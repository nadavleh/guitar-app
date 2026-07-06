# Audio Engine Overhaul — Design (mixer / voice-engine)

**Date:** 2026-07-06
**Status:** Approved design, ready for implementation plan.
**Target version:** v1.22.0 (single minor feature: "audio engine overhaul").
**Platforms:** Android (`audio` module: `AudioTrackEngine`, `PluckedSynth`) + mirrored `chorect-web` (`src/audio/*`, WebAudio).

## Context & decomposition

This is **Step 1 of two**. Nadav asked for (a) higher-quality sampled guitar sounds and (b) a "vast mixer engine improvement" because the current sound "doesn't decay well, doesn't sustain naturally, or bloom," and mixes simultaneous strings poorly.

Decision (locked with Nadav): **do the mixer/voice-engine overhaul first**, because it is foundational — it fixes clipping, decay tails, declicking, headroom, panning, and adds a master bus (limiter + reverb) that benefits **both** the current synth **and** the future sampled instruments. The **sampled-instrument engine is a separate spec** (Step 2) built on this foundation.

Locked decisions:
- **Sequencing:** mixer first, then samples.
- **Tone target:** *roomy / subtle bloom* — smooth natural decay, a touch of body/space, still intimate. Master bus = soft limiter + a **gentle** reverb send.
- **Output:** **stereo** (currently mono), with per-voice pan.
- **Voice model:** **Approach A** — a real-time per-voice pull model (industry-standard sampler/synth voice architecture), not the current pre-rendered-array sum.
- **APK size impact of this spec:** ~0 MB. Pure DSP code only; reverb is **algorithmic** (no impulse-response asset). MB growth is deferred to Step 2 (samples).

## Problem analysis (root causes in current code)

Verified against the current engine:
- **Hard clipping.** `AudioTrackEngine.kt:130` clamps the summed sample to ±1; WebAudio clips at `destination`. When strings/chords/drums sum past 1.0 → harsh intermodulation distortion (the opposite of "bloom"). **This is the single worst offender for "mixing different strings."**
- **Fixed-length pre-rendered buffers.** Notes are whole `FloatArray`s (`PluckedSynth.synthesize`); they end at buffer exhaustion, and `stop()` does `voices.clear()` while voice-overflow drops the oldest instantly (`addVoice` → `removeAt(0)`). All three cause abrupt cuts / clicks and prevent a natural release.
- **Uniform Karplus-Strong damping.** A single `damping` constant can't model a real string (fast high-harmonic decay + long fundamental tail + body resonance) → "doesn't decay/sustain naturally."
- **Mono, no pan, no master bus.** No stereo separation, no limiter/compressor, no reverb → strings pile up at one point; no bloom.

## Architecture

Replace "pre-render whole notes → hard-sum arrays" with a **real-time voice graph**: the mixer *pulls* audio from voices in blocks, wraps each voice in an amp envelope + pan, sums into a **stereo bus**, and runs the bus through a master chain (reverb send + soft limiter). Every existing sound source (Karplus-Strong notes, chords, drum one-shots) becomes "just a voice source," so nothing breaks — all gain the new bus for free. Step 2's sampled instruments implement the same voice interface.

### Signal chain

```
                 ┌────────── per voice ──────────┐
 VoiceSource ──► render(block) ──► amp envelope ──► constant-power pan ──┐
 (KS buffer,                        (attack+release)   (L/R gains)       │
  drum sample,                                                          ▼
  sample instr. [Step 2])                                          ┌─► STEREO SUM (dry L/R)
        ⋮  (N voices)                                              │        │
                                                                   │        ├─► reverb send ─► Freeverb (stereo) ─► wet
                                                                   │        │                                        │
                                                                   └────────┴────────────  dry + wet  ◄─────────────┘
                                                                                    │
                                                                          soft peak limiter (ceiling ≈ -0.5 dBFS)
                                                                                    │
                                                                interleave → AudioTrack (STEREO)  /  → destination (web)
```

### The Voice abstraction (keystone)

```kotlin
interface VoiceSource {
    /** Pull up to `count` mono samples into out[0 until count]; return how many were
     *  produced (< count when the source runs dry). Block-wise so the virtual-call cost
     *  is amortized across ~64–256 frames. */
    fun render(out: FloatArray, count: Int): Int
    val isFinished: Boolean          // true once fully drained
}
```

Sources shipping in this spec:
- **`BufferSource(samples)`** — reads a pre-rendered `FloatArray`. Wraps **both** current Karplus-Strong output **and** drum one-shots → existing behavior preserved with zero synth changes.
- (Step 2's `SampleInstrumentSource` implements the same interface later — this is how samples "plug in as just another voice.")

Around each source the mixer keeps **voice state**: an `AmpEnvelope` (fast attack to declick start; `release` ramp ~15–30 ms on stop/steal so notes taper), a `pan` position, and a gain. **The source owns the note's natural decay**; the envelope only handles start/stop edges and graceful stealing. Voice-stealing picks the **quietest** voice and **releases** it (not a hard remove).

How this maps to the complaints: clip-distortion → soft limiter; abrupt stop → release envelope; bloom → stereo reverb send; strings pile up → per-voice pan + headroom + limiter. The KS source additionally gets a modest decay improvement (M6); the dramatic realism arrives with Step-2 samples on this same chain.

## Components

### Android — `AudioTrackEngine` rewrite (pull-model mixer)

Output thread runs a block mixdown:

```
per output block (chunkFrames ≈ 128):
  zero accL[chunk], accR[chunk]
  under voicesLock, for each voice v:
      n = v.source.render(mono, chunk)
      v.envelope.applyInPlace(mono, n)          // attack/release ramp over the block
      (gL, gR) = panGains(v.pan); g = v.gain
      for i in 0..n:  accL[i] += mono[i]*g*gL ;  accR[i] += mono[i]*g*gR
      if v.source.isFinished && v.envelope.isSilent: remove
  reverb.process(accL, accR)                     // dry + wet, in place
  limiter.process(accL, accR)                    // soft peak limit → ceiling
  interleave accL/accR → ShortArray[chunk*2]; track.write(...)
```

Changes:
- `AudioTrack` → `CHANNEL_OUT_STEREO`, interleaved 16-bit; buffer size recomputed from the stereo min.
- `Voice` gains `source: VoiceSource`, `envelope`, `pan`; keeps the scheduling-delay (`pos < 0` counts down, gating `render`).
- **Idle-park corrected:** may only stop writing when there are **no voices AND** the reverb tail has decayed (`reverb.isRingingOut()`), so the bloom is never truncated.
- `MAX_VOICES` (16) overflow → **release the quietest**, not instant drop.
- `stop()` → set all voices to **release** (~20 ms) and let them ring out (no `voices.clear()`).

### Web — WebAudio mapping (idiomatic, sonic parity)

WebAudio mixes natively and has high-quality nodes, so build the graph rather than a per-sample loop:

```
voice:  AudioBufferSourceNode ─► GainNode (env via linearRampToValueAtTime) ─► StereoPannerNode ─┬─► masterDry (GainNode)
                                                                                                 └─► reverbSend (GainNode) ─► ConvolverNode ─┐
 masterDry ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
                                                                                                                                            ▼
                                                                                                        DynamicsCompressorNode (as limiter) ─► ctx.destination
```

- **Reverb:** `ConvolverNode` fed a **synthetically-generated stereo IR** (exponentially-decaying noise, ~1–1.5 s). No asset; matches the Freeverb *sound*.
- **Limiter:** `DynamicsCompressorNode` set brickwall-ish (high ratio, low threshold, fast attack) — web-native equivalent of the Kotlin soft limiter.
- **Release/declick:** per-voice `GainNode` ramp; `stop()` ramps all voice gains to 0 over ~20 ms then stops sources.
- Drums already route through a per-voice `GainNode`; they gain a `StereoPanner` + a low/zero reverb send.

**Parity stance:** the two platforms are already not bit-identical (different synth RNG, different drum onset handling). Target **sonic parity** — same chain topology and perceived result — implementing each stage idiomatically per platform. Pure math that is cheap to share (constant-power pan law, limiter curve constants, envelope timings) is specified once and mirrored in both languages.

### Master-bus DSP components (pure, testable — no Android/DOM deps)

- **`panGains(pan) → (l, r)`** — constant-power: `θ=(pan+1)·π/4`, `l=cos θ`, `r=sin θ` ⇒ `l²+r²≈1` (no center loudness bump).
- **`AmpEnvelope`** — Attack → Sustain(pass-through = 1.0) → Release; block `applyInPlace(buf, n)`; `release()` starts the down-ramp; `isSilent` when released to 0. Attack ≈ 3 ms, release ≈ 15–30 ms.
- **`SoftLimiter`** (Android) — peak-following gain reduction with a soft knee toward a ceiling (≈ −0.5 dBFS) + short release; guarantees `|out| ≤ ceiling`, no hard clip, no NaN. (Web uses `DynamicsCompressor`.)
- **`Freeverb`** (Android) — public-domain algorithmic reverb: 8 parallel comb + 4 series allpass per channel with standard stereo spread; `roomSize`/`damp`/`wet` params (`wet` small for subtle bloom); `isRingingOut()` for idle detection. (Web uses `ConvolverNode`.)

These live in the pure/testable layer alongside `PluckedSynth`/`PercussionSynth`.

## API & compatibility

Public `AudioEngine` interface is **unchanged** — `playNote / playFrequency / playChord / playSamples / playSamplesAt / stop / close` keep signatures; all callers untouched. Internals now create voices:

- **`playNote` / `playFrequency`** → synth renders `FloatArray` → `BufferSource` voice + envelope + pan (pan derived from pitch: low centered, high nudged out within ±~0.3, subtle).
- **`playChord`** → **improved**: one voice per note with the strum offset as each voice's start-delay, so each string gets its own pan (real strum spread) and release. `synthesizeChord` is retired; strum + `1/√N` scaling move into per-voice gain.
- **`playSamples` / `playSamplesAt`** (drums, metronome) → wrap array in `BufferSource`; `delayFrames` → voice start-delay. Drums default **center pan, low/zero reverb send** (dry, punchy), now pass through the limiter.
- **`stop`** → release all voices (~20 ms), not instant clear.
- **`Timbre`** gains optional fields with defaults (existing usages compile unchanged):
  ```kotlin
  data class Timbre(
      val damping: Double = 0.997,
      val amplitude: Double = 0.6,
      val pan: Double = 0.0,          // -1..1; default center
      val reverbSend: Double = 0.18,  // 0..1; subtle bloom; drums override ~0
      val releaseMs: Int = 20,
  )
  ```
  `Timbre.Clarity`/`Timbre.Guitar` get sensible reverb sends; drum playback passes `reverbSend ≈ 0`.

## Testing

Pure DSP unit-tested on **both** JVM (`audio` module) and TS; existing 17 audio tests stay green. Refactor so the mixdown math is callable without Android (`AudioTrack` is a thin sink), mirroring the already-pure `PluckedSynth`/`PercussionSynth`.

- **`panGains`:** `l²+r²≈1` across a sweep; center ⇒ `l≈r≈0.707`.
- **`AmpEnvelope`:** attack ramps 0→1 monotonically; after `release()`, reaches exactly 0 and stays; block boundaries continuous (no click).
- **`SoftLimiter`:** `|out| ≤ ceiling` for any input; recovers after a transient; no NaN/Inf; sub-ceiling signal ~unchanged.
- **`Freeverb`:** impulse-response energy decays to silence (stable, no runaway); `isRingingOut()` eventually true; wet output bounded.
- **Voice-steal:** with `MAX_VOICES+1` voices, the quietest is chosen and released (tail continues), not hard-cut.
- **Mixer integration** (JVM, headless — mix to buffer, no `AudioTrack`): several full-scale voices stay within ceiling; after `stop()` the last N samples approach 0 monotonically (declick); reverb tail keeps producing output after voices finish.

## Milestones (each independently testable & shippable)

1. **M1 — Voice abstraction + pull model (behavior-preserving).** `VoiceSource`/`BufferSource`; mixer pulls blocks. Still mono, no bus. Goal: *identical* sound to today (safe refactor). Existing tests pass.
2. **M2 — Amp envelope + graceful stop/steal.** Attack declick, release ramp; `stop()` and overflow use release. (Kills clicks.)
3. **M3 — Stereo bus + per-voice pan.** `AudioTrack` → stereo; `StereoPanner` on web; chord path → per-note voices with spread.
4. **M4 — Soft limiter / master bus.** Fixes hard-clip distortion on summed strings/drums.
5. **M5 — Reverb send (Freeverb / Convolver).** Roomy/subtle bloom; idle-park corrected for the tail.
6. **M6 — KS decay improvement + `Timbre` extensions + drum reverb-send wiring.** Dual-rate damping (fast HF decay, longer fundamental tail); finalize defaults.

Ship as **v1.22.0** at the end of M6 (or incrementally, Nadav's call). Web mirrored throughout; CI (tsc/vite/deploy) + `assembleDebug` + audio tests green before ship.

## Out of scope (future)

- **Step 2 — sampled-instrument engine** (multisample + resample player; acoustic/classical/electric). Separate spec; rides this voice/bus chain.
- **Parametric EQ** — its own feature (post-mix biquad chain; web `BiquadFilterNode`). Deliberately not bolted onto `Timbre`.
- **Convolution body-IR / sympathetic-resonance** bloom upgrades — optional later, on top of the algorithmic reverb.
- **Velocity layers / round-robins** — belong to Step 2.

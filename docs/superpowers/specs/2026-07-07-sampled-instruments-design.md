# Sampled Instruments — Design (Step 2 of the audio work)

**Date:** 2026-07-07
**Status:** Approved (user waived the review gate; proceed to plan + implementation).
**Target version:** v1.23.0 (minor — new feature: sampled guitar instruments).
**Builds on:** the v1.22.0 audio-engine overhaul (`docs/superpowers/specs/2026-07-06-audio-engine-overhaul-design.md`). Platforms: Android (`audio` module + app) + `chorect-web` (WebAudio), sonic parity.

## Context

The v1.22.0 overhaul replaced the mixer with a real-time voice graph. A sampled voice is just another `VoiceSource` (Android) / modern voice (web), so real recorded guitars now slot into the existing engine (envelope, pan, reverb send, soft limiter all apply). This spec adds **sampled acoustic-steel, nylon/classical, and electric guitars**, selectable via a new **Sound** picker, alongside the existing synth.

### Locked decisions
- **Source:** CC0 multisample sets (Versilian **VSCO2** for steel + nylon; a CC0 electric — VSCO2 electric, else FreePats/Philharmonia-CC0). Fully distributable. Exact files + licenses documented with the banks. If no acceptable CC0 electric is found, flag it and ship acoustic+nylon for v1 (do not silently substitute).
- **Instruments (v1):** all three — acoustic steel, nylon/classical, electric.
- **Playback:** Approach A — on-the-fly nearest-sample **resample** (SFZ-style). Android: linear-interp resampler in a `SampleSource : VoiceSource`. Web: `AudioBufferSourceNode.playbackRate`.
- **Format/density:** **WAV, mono, 44.1 kHz, 16-bit**, ~1 sample every **2 semitones** across **MIDI 40 (E2) → 84 (C6)** (~22 pitches/instrument), tails ~2.5 s with a short fade-out. ~66 files, **~18–24 MB bundled** (within the 15–30 MB target). WAV reuses `WavDecoder` (Android) + `decodeSample`/`decodeAudioData` (web) — no new decoder. Density is a build-pipeline parameter; OGG (+ a small Android OGG decoder) is the fallback only if we later want every-semitone density beyond the WAV budget.
- **No velocity layers, no sustain loops** — the app triggers notes programmatically with no velocity/expression input; guitars decay naturally (one-shot to decay). YAGNI.
- **Selection UX:** a new **Sound** dropdown (`Synth / Acoustic / Nylon / Electric`) in the **🎚 Audio** control on both platforms. Sampled sounds run on the **modern engine only**; the legacy/modern **A/B engine toggle stays separate and unchanged** (A/B compares engines; Sound compares sources).

## Architecture

### Data model (Android, `audio` module — pure Kotlin, JVM-testable)
```kotlin
class GuitarSample(val rootMidi: Int, val samples: FloatArray)      // one decoded recorded pitch (mono)

class SampleInstrument(val id: String, samples: List<GuitarSample>) {
    // samples sorted by rootMidi
    fun nearest(midi: Int): GuitarSample   // recorded pitch closest to `midi` (ties → lower root)
}

class SampleSource(inst: SampleInstrument, targetMidi: Int) : VoiceSource {
    // root = inst.nearest(targetMidi); rate = 2^((targetMidi - root.rootMidi)/12)
    // render(out, count): read root.samples at `rate` with linear interpolation over a
    //   fractional read position; return produced count (< count when past end); isFinished then.
}
```
`SampleSource` implements the existing `VoiceSource` interface → drops into the v1.22.0 mixer unchanged (envelope declick/release, constant-power pan, reverb send, soft limiter). Chords = N `SampleSource` voices (one per note), exactly like the synth path.

### Engine integration
`AudioTrackEngine` (the modern engine) gains `var voiceInstrument: SampleInstrument? = null`. In modern-mode `playNote`/`playFrequency`/`playChord`:
- `voiceInstrument == null` → synth path (Karplus-Strong `BufferSource` — today's behavior).
- `voiceInstrument != null` → `SampleSource` voices from the bank (pan via `Panner.forMidi`, strum via start-delay, gain `1/√N`, `Timbre` reverbSend/releaseMs as now).

The **legacy** engine is untouched (always the old KS synth). Sampled sounds are therefore a modern-engine feature; the A/B toggle stays orthogonal.

### Sound-selection state (app)
```kotlin
enum class GuitarSound { Synth, Acoustic, Nylon, Electric }
// AppState:
var sound: GuitarSound by mutableStateOf(GuitarSound.Synth)   // persisted
fun setSound(s: GuitarSound)   // Synth → engine.voiceInstrument = null;
                               // else lazily load+cache that bank (off-thread), then set engine.voiceInstrument
```
Loading is lazy and cached: first selection of a bank loads+decodes its WAVs once, keeps it in memory; re-selecting is instant. While loading, playback falls back to synth and the option shows a brief "loading…" state — never blocks the UI. Web mirrors this (`setInstrument(bank|null)` on the modern chain).

## Sample banks + build pipeline

### `tools/build_guitar_samples.py` (mirrors `build_drum_samples.py`)
For each instrument, from the CC0 source files:
1. Select target pitches (every 2 semitones, MIDI 40–84), each mapped to the nearest available recorded note.
2. Per-instrument normalize (consistent loudness across all three), trim leading silence, trim to ~2.5 s, short fade-out (declick).
3. Write `app/src/main/assets/guitar/<inst>_<midi>.wav` AND `chorect-web/public/guitar/<inst>_<midi>.wav` (`<inst>` ∈ `acoustic`/`nylon`/`electric`).
4. Emit a manifest per instrument (e.g. `guitar/<inst>.json` = list of `rootMidi`s present) so loaders fetch/decode exactly the right files — no missing-file guessing.

Banks are committed as build artifacts (like the drum WAVs). CC0 source URLs + licenses recorded in `app/src/main/assets/guitar/LICENSES.txt` (and mirrored to web). The tool is re-runnable to re-tune density/trim.

## Per-platform playback & loading

### Android
- `SampleSource.render`: linear-interp read at `rate`, fractional position, no per-call allocation.
- `GuitarBankLoader` (mirrors `drumSampleLoader`): read manifest → `assets.open("guitar/<inst>_<midi>.wav")` → `WavDecoder.decode()` → `GuitarSample` → `SampleInstrument`. Off-thread (coroutine on `scope`), cached per instrument. `setSound()` loads then sets `engine.voiceInstrument`.

### Web (parity)
- Sampled voice = `AudioBufferSourceNode` with `buffer` = nearest recorded pitch, `playbackRate = 2^((target−root)/12)`, feeding the existing modern voice graph (env → panner → dry + reverb send → limiter). Chords = one per note.
- Loader: `fetch("guitar/<inst>_<midi>.wav")` → `decodeSample` → `AudioBuffer`, cached `Map<rootMidi, AudioBuffer>` per instrument. `setInstrument(bank|null)` on the modern chain; Sound dropdown drives it.
- Parity: identical nearest-pitch + rate logic; resampler differs only in impl (hand-rolled linear vs `playbackRate`).

## Sound dropdown UI
- **🎚 Audio** control, both platforms: a **Sound** picker `Synth / Acoustic / Nylon / Electric` bound to `AppState.sound`/`setSound` (Android `DropdownMenu`; web `select`/`switchRow`-style). Loading state shown briefly; synth fallback until ready. The A/B engine toggle stays put, separate.
- `sound` persisted (Android DataStore, web localStorage), like other audio settings.

## Testing
- **Pure (JVM; mirror cheap bits in web `verify.ts`):**
  - `SampleInstrument.nearest(midi)` → closest root, ties → lower.
  - rate = `2^((t−r)/12)`: unison → 1.0, +12 → 2.0, −12 → 0.5.
  - resampler: ramp read at rate 1.0 = identity; rate 2.0 = half length; interpolation midpoint correct; `render` partial-final-block + `isFinished`; bounded output for bounded input.
  - manifest coverage: every listed `rootMidi` has a decodable file; sampled range covers the app note range.
- Existing 57 audio tests stay green; sampled voice rides the already-tested mixer/bus.

## Milestones
1. **Build pipeline + banks** — source/verify CC0 (flag if no CC0 electric), `build_guitar_samples.py`, produce WAV banks + manifest + `LICENSES.txt` in assets/public.
2. **`SampleInstrument` + `SampleSource` + resampler** — pure Kotlin, TDD.
3. **Android engine integration + `GuitarBankLoader`** — `voiceInstrument`, sampled note/chord paths, lazy cached off-thread loading.
4. **Sound state + 🎚 Audio "Sound" dropdown** (Android) + persistence.
5. **Web mirror** — bank fetch/decode + `playbackRate` sampled voices + Sound dropdown + persistence.
6. **Ship v1.23.0** — bundle-size check, version bump (app+web), APK archive/build, tests, commit, push, web CI. A/B toggle kept.

## Out of scope (future)
- Velocity layers / round-robins / sustain loops (no velocity input).
- Parametric EQ / per-instrument tone params (separate future feature).
- Non-guitar sampled instruments; per-note pan overrides via `Timbre.pan` (still unused — decide later).
- OGG encoding + Android OGG decoder (only if we later want denser-than-WAV-budget banks).

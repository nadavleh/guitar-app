# Sampled Instruments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add sampled acoustic-steel, nylon/classical, and electric guitars — selectable via a new "Sound" picker — playing through the v1.22.0 voice engine, on Android and chorect-web.

**Architecture:** Approach A (SFZ-style). Multisample banks (WAV, one recorded pitch every ~2 semitones) are bundled in assets/public. A `SampleInstrument` holds the decoded bank; a `SampleSource : VoiceSource` picks the nearest recorded pitch and pitch-shifts it by playback rate (linear interpolation on Android; `AudioBufferSourceNode.playbackRate` on web), feeding the existing modern voice graph (envelope/pan/reverb/limiter). The modern `AudioTrackEngine` gains `voiceInstrument`; a `GuitarSound` selector switches synth ↔ sampled. Legacy engine untouched; A/B toggle unaffected.

**Tech Stack:** Kotlin (`audio` module, JUnit5 + `kotlin.test`), TypeScript (`chorect-web`, WebAudio), Python 3 (build tool, like `tools/build_drum_samples.py`), Gradle, Vite.

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-07-07-sampled-instruments-design.md` (authoritative).
- **Builds on v1.22.0 voice engine:** `VoiceSource { fun render(out: FloatArray, count: Int): Int; val isFinished: Boolean }`; `MixVoice(source: VoiceSource, gain=1f, delayFrames=0, envelope=AmpEnvelope(...), reverbSend=0f)` with `var pan`; `VoiceMixer.addAndCap(v, max)`. Do NOT modify the mixer/envelope/limiter/reverb — sampled voices reuse them as-is.
- **Sample source:** CC0 only (Versilian VSCO2 for steel+nylon; a CC0 electric — VSCO2 electric else FreePats). Document exact files + licenses in `LICENSES.txt`. If no acceptable CC0 electric, STOP and flag — ship acoustic+nylon for v1, don't substitute silently.
- **Instruments:** `acoustic`, `nylon`, `electric`.
- **Format:** WAV, mono, 44.1 kHz, 16-bit. ~1 pitch every **2 semitones**, MIDI **40–84**, tails ~2.5 s + short fade-out. Target **~18–24 MB** total (≤30 MB). Reuse `WavDecoder` (Android) + `decodeSample` (web) — no new decoder.
- **No velocity layers, no sustain loops** (one-shot to natural decay).
- **Resample rate:** `rate = 2^((targetMidi − rootMidi) / 12)`. Nearest-pitch tie → lower root.
- **Sampled sounds = modern engine only.** Legacy engine and the A/B toggle are untouched.
- **Sound picker:** `Synth / Acoustic / Nylon / Electric` in the 🎚 Audio control, persisted (Android DataStore, web localStorage).
- **Asset paths:** Android `app/src/main/assets/guitar/<inst>_<midi>.wav`; web `chorect-web/public/guitar/<inst>_<midi>.wav`; manifest `guitar/<inst>.json` (JSON array of rootMidi ints).
- **Versioning:** ship v1.23.0 (`versionCode = 12300`, `versionName = "1.23.0"`; web `package.json` "1.23.0"). Archive prior APK to `releases/` before `assembleDebug`.
- **Commands:** Kotlin tests `.\gradlew.bat :audio:test` (Windows — `.\gradlew.bat`, never `./gradlew`). Web has NO local Node — verify via CI (tsc + vite build); `test/verify.ts` is not run by CI. Web deploy: `gh workflow run "Deploy web to GitHub Pages" --ref main`.
- **Commit** after each task. Do not push until a milestone completes and Nadav approves.

---

## File Structure

**Build tool:** `tools/build_guitar_samples.py` (new) — carve/normalize/trim CC0 sources → per-pitch WAVs + manifest into both asset dirs.

**Android (`audio/src/main/kotlin/app/guitar/audio/`, package `app.guitar.audio`):**
- Create `SampleInstrument.kt` — `GuitarSample`, `SampleInstrument` (nearest-pitch), `SampleSource : VoiceSource` (resampler).
- Modify `AudioTrackEngine.kt` — `var voiceInstrument: SampleInstrument?`; `addVoiceSource(...)` helper; branch note/chord paths on `voiceInstrument`.
- Test `audio/src/test/kotlin/app/guitar/audio/SampleInstrumentTest.kt`.

**Android app (`app/src/main/kotlin/app/guitar/app/`):**
- Create `GuitarBankLoader.kt` — load a bank from assets (manifest → WAVs → `SampleInstrument`).
- Modify `AppState.kt` — `GuitarSound` enum, `sound` state + `setSound`, persistence hooks.
- Modify `MainActivity.kt` — provide the asset-reading bank loader to AppState.
- Modify `AudioQuick.kt` — the "Sound" dropdown.
- Modify `TuningRepository.kt` — persist the selected sound.

**Web (`chorect-web/src/audio/` + `src/app/`):**
- Create `src/audio/sampleInstrument.ts` — `nearestRoot`, `pitchRate` (mirror), bank type.
- Modify `src/audio/engine.ts` — modern chain gains a current bank + `setInstrument`; sampled voices via `playbackRate`.
- Modify `src/app/appState.ts` — sound state + persistence.
- Modify `src/app/ui.ts` — Sound picker in the 🎚 Audio popup.

---

## Task 1: Build pipeline + CC0 banks

**Files:**
- Create: `tools/build_guitar_samples.py`
- Create (artifacts): `app/src/main/assets/guitar/<inst>_<midi>.wav`, `app/src/main/assets/guitar/<inst>.json`, `app/src/main/assets/guitar/LICENSES.txt`; mirror to `chorect-web/public/guitar/`.

**Interfaces:**
- Produces: WAV banks + per-instrument manifest JSON (array of rootMidi ints, ascending) + LICENSES.txt. Instruments `acoustic`, `nylon`, `electric`; pitches every 2 semitones over MIDI 40–84.

> **Controller note:** this task needs network + judgment (download CC0 packs, audition electric). It may be run by the controller rather than a headless subagent. If no acceptable CC0 electric is found, STOP and flag — build acoustic + nylon only for v1.

- [ ] **Step 1: Acquire CC0 sources**

Download Versilian VSCO2 (CE/CC0 community edition) acoustic-steel + nylon/classical guitar samples, and a CC0 electric (VSCO2 electric, else FreePats). Place raw sources under `tools/_guitar_src/<inst>/`. Record each pack's URL + license.

- [ ] **Step 2: Write `tools/build_guitar_samples.py`**

Mirror `tools/build_drum_samples.py` conventions. For each instrument dir, for each target MIDI in `range(40, 85, 2)`: pick the source recording whose pitch is nearest the target; load (soundfile), mono-mix, resample to 44.1 kHz if needed, normalize to a per-instrument peak (e.g. −1 dBFS), trim leading silence, trim to 2.5 s, apply a 30 ms fade-out; write `<inst>_<midi>.wav` (16-bit PCM) into BOTH `app/src/main/assets/guitar/` and `chorect-web/public/guitar/`. Emit `<inst>.json` = sorted list of the rootMidis actually written. Append source/license lines to `LICENSES.txt` in both dirs.

```python
# tools/build_guitar_samples.py  (skeleton — fill instrument source maps)
import json, os, numpy as np, soundfile as sf
from scipy.signal import resample_poly

SR = 44100
TARGETS = list(range(40, 85, 2))          # E2..C6 every 2 semitones
OUT = ["app/src/main/assets/guitar", "chorect-web/public/guitar"]

def load_mono(path):
    x, sr = sf.read(path, always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        from math import gcd
        g = gcd(int(sr), SR); x = resample_poly(x, SR // g, int(sr) // g)
    return x.astype(np.float32)

def process(x):
    # trim leading silence
    thr = 0.01 * np.max(np.abs(x)) if x.size else 0
    nz = np.where(np.abs(x) > thr)[0]
    if nz.size: x = x[nz[0]:]
    x = x[: int(2.5 * SR)]                 # trim tail
    peak = np.max(np.abs(x)) or 1.0
    x = x * (10 ** (-1/20) / peak)         # normalize to -1 dBFS
    f = min(int(0.03 * SR), x.size)        # 30 ms fade-out
    if f: x[-f:] *= np.linspace(1, 0, f)
    return x.astype(np.float32)

def build(inst, src_for_midi):             # src_for_midi: dict target_midi -> source wav path
    roots = []
    for m in TARGETS:
        src = src_for_midi.get(m)
        if src is None: continue
        y = process(load_mono(src))
        for d in OUT:
            os.makedirs(d, exist_ok=True)
            sf.write(os.path.join(d, f"{inst}_{m}.wav"), y, SR, subtype="PCM_16")
        roots.append(m)
    for d in OUT:
        json.dump(roots, open(os.path.join(d, f"{inst}.json"), "w"))
    print(f"{inst}: wrote {len(roots)} pitches")

# TODO per instrument: map each TARGET midi to the nearest available source recording,
# then: build("acoustic", ...); build("nylon", ...); build("electric", ...)
```

- [ ] **Step 3: Run it and verify size + coverage**

Run: `python tools/build_guitar_samples.py`
Expected: each instrument prints its pitch count (~22). Then check total size:
`du -sh app/src/main/assets/guitar` → expect ~18–24 MB (≤30). Confirm `<inst>.json` exists for each instrument and lists the written rootMidis.

- [ ] **Step 4: Commit**

```bash
git add tools/build_guitar_samples.py app/src/main/assets/guitar chorect-web/public/guitar
git commit -m "feat(audio): CC0 guitar multisample banks + build_guitar_samples.py (M1)"
```

---

## Task 2: `SampleInstrument` + `SampleSource` (pure Kotlin, TDD)

**Files:**
- Create: `audio/src/main/kotlin/app/guitar/audio/SampleInstrument.kt`
- Test: `audio/src/test/kotlin/app/guitar/audio/SampleInstrumentTest.kt`

**Interfaces:**
- Consumes: `VoiceSource` (existing).
- Produces:
  - `class GuitarSample(val rootMidi: Int, val samples: FloatArray)`
  - `class SampleInstrument(val id: String, samples: List<GuitarSample>)` with `fun nearest(midi: Int): GuitarSample`.
  - `class SampleSource(inst: SampleInstrument, targetMidi: Int) : VoiceSource`.
  - `companion object { fun pitchRate(target: Int, root: Int): Double = Math.pow(2.0, (target - root) / 12.0) }` on `SampleSource`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.guitar.audio

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class SampleInstrumentTest {
    private fun ramp(n: Int) = FloatArray(n) { it / n.toFloat() }   // 0..~1 ramp

    @Test fun `nearest picks closest root, ties go lower`() {
        val inst = SampleInstrument("t", listOf(
            GuitarSample(40, floatArrayOf(1f)), GuitarSample(44, floatArrayOf(2f)), GuitarSample(48, floatArrayOf(3f))))
        assertEquals(40, inst.nearest(41).rootMidi)
        assertEquals(44, inst.nearest(45).rootMidi)
        assertEquals(40, inst.nearest(42).rootMidi)   // tie 40/44 -> lower
        assertEquals(48, inst.nearest(100).rootMidi)   // clamp to top
        assertEquals(40, inst.nearest(0).rootMidi)     // clamp to bottom
    }

    @Test fun `pitchRate — unison 1, octave up 2, octave down half`() {
        assertEquals(1.0, SampleSource.pitchRate(60, 60), 1e-9)
        assertEquals(2.0, SampleSource.pitchRate(72, 60), 1e-9)
        assertEquals(0.5, SampleSource.pitchRate(48, 60), 1e-9)
    }

    @Test fun `at unison the source reproduces the sample then finishes`() {
        val inst = SampleInstrument("t", listOf(GuitarSample(60, ramp(100))))
        val src = SampleSource(inst, 60)             // rate 1.0
        val out = FloatArray(64)
        val n1 = src.render(out, 64); assertEquals(64, n1)
        assertEquals(ramp(100)[0], out[0], 1e-6f)
        assertEquals(ramp(100)[63], out[63], 1e-6f)
        assertFalse(src.isFinished)
        val n2 = src.render(out, 64); assertEquals(36, n2)   // 100 - 64
        assertTrue(src.isFinished)
        assertEquals(0, src.render(out, 64))
    }

    @Test fun `octave up consumes the sample about twice as fast`() {
        val inst = SampleInstrument("t", listOf(GuitarSample(60, ramp(200))))
        val src = SampleSource(inst, 72)             // rate 2.0 -> ~100 output frames
        val out = FloatArray(256)
        val n = src.render(out, 256)
        assertTrue(n in 99..101, "expected ~100 output frames, got $n")
        assertTrue(src.isFinished)
    }

    @Test fun `output stays within the sample's amplitude bound`() {
        val inst = SampleInstrument("t", listOf(GuitarSample(60, FloatArray(500) { 0.8f })))
        val src = SampleSource(inst, 67)             // rate 2^(7/12) ~1.498
        val out = FloatArray(512)
        val n = src.render(out, 512)
        for (i in 0 until n) assertTrue(abs(out[i]) <= 0.8001f, "out[$i]=${out[i]}")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.SampleInstrumentTest"`
Expected: FAIL — unresolved `SampleInstrument`/`SampleSource`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.guitar.audio

/** One recorded pitch of a sampled instrument (mono float PCM at the engine rate). */
class GuitarSample(val rootMidi: Int, val samples: FloatArray)

/** A loaded multisample bank. [samples] are sorted by rootMidi at construction. */
class SampleInstrument(val id: String, samples: List<GuitarSample>) {
    private val sorted = samples.sortedBy { it.rootMidi }
    init { require(sorted.isNotEmpty()) { "instrument $id has no samples" } }

    /** Recorded pitch whose root is closest to [midi]; ties resolve to the lower root. */
    fun nearest(midi: Int): GuitarSample {
        var best = sorted[0]
        var bestDist = kotlin.math.abs(midi - best.rootMidi)
        for (i in 1 until sorted.size) {
            val d = kotlin.math.abs(midi - sorted[i].rootMidi)
            if (d < bestDist) { best = sorted[i]; bestDist = d }   // strict < keeps the lower root on ties
        }
        return best
    }
}

/** Pitch-shifts the nearest recorded pitch to [targetMidi] by playback-rate resampling
 *  (linear interpolation), pulled block-wise by the mixer. Mono. */
class SampleSource(inst: SampleInstrument, targetMidi: Int) : VoiceSource {
    private val root = inst.nearest(targetMidi)
    private val buf = root.samples
    private val rate = pitchRate(targetMidi, root.rootMidi)
    private var pos = 0.0

    override fun render(out: FloatArray, count: Int): Int {
        var produced = 0
        while (produced < count) {
            val i = pos.toInt()
            if (i >= buf.size - 1) {
                // last sample (no next to interpolate) then done
                if (i == buf.size - 1) { out[produced++] = buf[i]; pos += rate }
                break
            }
            val frac = (pos - i).toFloat()
            out[produced++] = buf[i] + (buf[i + 1] - buf[i]) * frac
            pos += rate
        }
        return produced
    }

    override val isFinished: Boolean get() = pos.toInt() >= buf.size

    companion object {
        fun pitchRate(target: Int, root: Int): Double = Math.pow(2.0, (target - root) / 12.0)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.SampleInstrumentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/SampleInstrument.kt audio/src/test/kotlin/app/guitar/audio/SampleInstrumentTest.kt
git commit -m "feat(audio): SampleInstrument + SampleSource resampler (M2)"
```

---

## Task 3: Android engine integration + `GuitarBankLoader`

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt`
- Create: `app/src/main/kotlin/app/guitar/app/GuitarBankLoader.kt`

**Interfaces:**
- Consumes: `SampleInstrument`, `SampleSource` (Task 2); `WavDecoder.decode` (existing); the engine's `mixer`, `MixVoice`, `AmpEnvelope`, `Panner`, `MAX_VOICES`, `GUITAR_BRIGHTNESS_DECAY`.
- Produces: `AudioTrackEngine.voiceInstrument: SampleInstrument?` (public `var`, default null); `GuitarBankLoader.load(assetOpener, inst): SampleInstrument?`.

- [ ] **Step 1: Add `voiceInstrument` + `addVoiceSource` and branch the play paths**

In `AudioTrackEngine.kt`: add field `@Volatile var voiceInstrument: SampleInstrument? = null`. Extract the existing single-note add into a source-taking helper and branch:

```kotlin
/** Add any VoiceSource as a modern voice (envelope + pan + reverb send). */
private fun addVoiceSource(source: VoiceSource, gain: Float = 1f, delayFrames: Int = 0,
                           pan: Double = 0.0, reverbSend: Float = 0f, releaseMs: Int = 20) {
    mixer.addAndCap(
        MixVoice(source, gain, delayFrames, AmpEnvelope(sampleRate, 3.0, releaseMs.toDouble()),
            reverbSend = reverbSend).also { it.pan = pan },
        MAX_VOICES,
    )
}
```

`playNote` — inside `synthesizer.execute { }`, before synthesizing, branch on the instrument:
```kotlin
val inst = voiceInstrument
if (inst != null) {
    addVoiceSource(SampleSource(inst, midiNote), pan = Panner.forMidi(midiNote),
        reverbSend = timbre.reverbSend.toFloat(), releaseMs = timbre.releaseMs)
    return@execute
}
// else: existing synth.synthesize(...) + addVoice(...) unchanged
```

`playChord` — inside the `notes.forEachIndexed` loop:
```kotlin
val inst = voiceInstrument
val source: VoiceSource = if (inst != null) SampleSource(inst, midi)
    else BufferSource(synth.synthesize(midi, sustainMillis / 1000.0, System.nanoTime() + i,
        timbre.damping, timbre.amplitude, brightnessDecay = GUITAR_BRIGHTNESS_DECAY))
mixer.addAndCap(MixVoice(source, gain, strumFrames * i,
    AmpEnvelope(sampleRate, 3.0, timbre.releaseMs.toDouble()), reverbSend = timbre.reverbSend.toFloat())
    .also { it.pan = Panner.forMidi(midi) }, MAX_VOICES)
```

`playFrequency` — sampled path via nearest midi:
```kotlin
val inst = voiceInstrument
if (inst != null) {
    val midi = Math.round(69 + 12 * (Math.log(freqHz.toDouble() / 440.0) / Math.log(2.0))).toInt().coerceIn(0, 127)
    addVoiceSource(SampleSource(inst, midi), reverbSend = timbre.reverbSend.toFloat(), releaseMs = timbre.releaseMs)
    return@execute
}
// else: existing synth path unchanged
```
Leave `playSamples`/`playSamplesAt` (drums) unchanged. Leave `addVoice(samples, …)` as-is (it can delegate to `addVoiceSource(BufferSource(samples), …)` if you like, but not required).

- [ ] **Step 2: Write `GuitarBankLoader.kt`**

```kotlin
package app.guitar.app

import app.guitar.audio.GuitarSample
import app.guitar.audio.SampleInstrument
import app.guitar.audio.WavDecoder
import org.json.JSONArray

/** Loads a bundled guitar bank from assets/guitar/<inst>.json + <inst>_<midi>.wav.
 *  [openAsset] reads an asset path to bytes (supplied by the Activity). Returns null
 *  if the manifest or all samples are missing. Pure of Android APIs beyond the passed opener. */
object GuitarBankLoader {
    fun load(inst: String, openAsset: (String) -> ByteArray?): SampleInstrument? {
        val manifest = openAsset("guitar/$inst.json") ?: return null
        val roots = JSONArray(String(manifest))
        val samples = ArrayList<GuitarSample>()
        for (k in 0 until roots.length()) {
            val midi = roots.getInt(k)
            val wav = openAsset("guitar/${inst}_$midi.wav") ?: continue
            samples.add(GuitarSample(midi, WavDecoder.decode(wav)))
        }
        return if (samples.isEmpty()) null else SampleInstrument(inst, samples)
    }
}
```
(`org.json` is available on Android. If the `audio` module can't see `org.json`, keep `GuitarBankLoader` in the `app` module as written here — it is.)

- [ ] **Step 3: Build + test**

Run: `.\gradlew.bat :audio:test` (all pass — no behavior change when `voiceInstrument` is null) then `.\gradlew.bat :app:assembleDebug` (BUILD SUCCESSFUL).

- [ ] **Step 4: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt app/src/main/kotlin/app/guitar/app/GuitarBankLoader.kt
git commit -m "feat(audio): engine voiceInstrument + sampled note/chord paths + GuitarBankLoader (M3)"
```

---

## Task 4: Sound state + 🎚 Audio "Sound" dropdown (Android)

**Files:**
- Modify: `app/src/main/kotlin/app/guitar/app/AppState.kt`
- Modify: `app/src/main/kotlin/app/guitar/app/MainActivity.kt`
- Modify: `app/src/main/kotlin/app/guitar/app/AudioQuick.kt`
- Modify: `app/src/main/kotlin/app/guitar/app/TuningRepository.kt`

**Interfaces:**
- Consumes: `GuitarBankLoader.load`, `AudioTrackEngine.voiceInstrument`, `SwitchableAudioEngine` (the `audio` is a `SwitchableAudioEngine` wrapping the modern `AudioTrackEngine`).
- Produces: `enum class GuitarSound { Synth, Acoustic, Nylon, Electric }`; `AppState.sound` + `setSound`.

- [ ] **Step 1: Add sound state + loader wiring to `AppState`**

Add a bank-loader dependency (like `drumSampleLoader`) and state:
```kotlin
enum class GuitarSound { Synth, Acoustic, Nylon, Electric }

// constructor param (after drumSampleLoader):
private val guitarBankLoader: (String) -> app.guitar.audio.SampleInstrument? = { null },

var sound by mutableStateOf(GuitarSound.Synth)
    private set
var soundLoading by mutableStateOf(false)
    private set
private val bankCache = HashMap<String, app.guitar.audio.SampleInstrument>()

@JvmName("applySound")
fun setSound(s: GuitarSound) {
    sound = s
    scope.launch { repo.setGuitarSound(s.name) }
    val modern = (audio as? app.guitar.audio.SwitchableAudioEngine)?.modernEngine
        ?: return
    if (s == GuitarSound.Synth) { modern.voiceInstrument = null; return }
    val id = s.name.lowercase()
    bankCache[id]?.let { modern.voiceInstrument = it; return }
    soundLoading = true
    scope.launch(kotlinx.coroutines.Dispatchers.Default) {
        val bank = guitarBankLoader(id)
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (bank != null) { bankCache[id] = bank; if (sound == s) modern.voiceInstrument = bank }
            soundLoading = false
        }
    }
}
```
Expose the modern engine: add `val modernEngine: AudioTrackEngine` to `SwitchableAudioEngine` (it already holds `modern`; add a public getter `val modernEngine get() = modern as? AudioTrackEngine` — or type `modern` as `AudioTrackEngine`). Adjust the cast above to match. If `audio` isn't switchable (tests), `setSound` no-ops on the engine but still tracks state.

- [ ] **Step 2: Persist in `TuningRepository`**

Add a `guitar_sound` string preference mirroring an existing string pref (e.g. how the tuning/theme are stored): `val guitarSound: Flow<String>` and `suspend fun setGuitarSound(v: String)`. On startup, `AppState` reads it and calls `setSound(GuitarSound.valueOf(...))` (guard invalid values → Synth).

- [ ] **Step 3: Provide the asset bank loader in `MainActivity`**

```kotlin
val guitarBankLoader: (String) -> app.guitar.audio.SampleInstrument? = { inst ->
    app.guitar.app.GuitarBankLoader.load(inst) { path ->
        runCatching { context.applicationContext.assets.open(path).use { it.readBytes() } }.getOrNull()
    }
}
// pass into AppState(repo, scope, audio, drumSampleLoader, guitarBankLoader)
```

- [ ] **Step 4: "Sound" dropdown in `AudioQuick.kt`**

In `AudioQuickSliders`, above or below the A/B switch, add a compact picker:
```kotlin
Spacer(Modifier.height(8.dp))
Text("Sound" + if (state.soundLoading) " (loading…)" else "", style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    GuitarSound.entries.forEach { gs ->
        val sel = state.sound == gs
        androidx.compose.material3.FilterChip(selected = sel, onClick = { state.setSound(gs) },
            label = { Text(gs.name) })
    }
}
```
(`Arrangement` import already present in the file; add `FilterChip` import or fully-qualify as above.)

- [ ] **Step 5: Build + smoke**

Run: `.\gradlew.bat :app:assembleDebug`. Manual: 🎚 Audio → pick Acoustic/Nylon/Electric, play a chord — sampled tone; Synth returns to Karplus-Strong; selection persists across restart.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/app/guitar/app/AppState.kt app/src/main/kotlin/app/guitar/app/MainActivity.kt app/src/main/kotlin/app/guitar/app/AudioQuick.kt app/src/main/kotlin/app/guitar/app/TuningRepository.kt audio/src/main/kotlin/app/guitar/audio/SwitchableAudioEngine.kt
git commit -m "feat(audio): GuitarSound selector + 🎚 Audio Sound dropdown + persistence (M4)"
```

---

## Task 5: Web mirror

**Files:**
- Create: `chorect-web/src/audio/sampleInstrument.ts`
- Modify: `chorect-web/src/audio/engine.ts`
- Modify: `chorect-web/src/app/appState.ts`
- Modify: `chorect-web/src/app/ui.ts`
- Modify: `chorect-web/test/verify.ts`

**Interfaces:**
- Consumes: `WebAudioEngine` modern chain (`playModernVoice`, `modernMaster`, `reverbBus`), `decodeSample`, `panForMidi`.
- Produces: `nearestRoot(roots, midi)`, `pitchRate(target, root)`, a `SampleBank` type; `WebAudioEngine.setInstrument(bank | null)`; `AppState.sound`/`setSound`.

> Web has NO local Node — write tsc-valid TS; verify via CI (tsc + vite build) at ship.

- [ ] **Step 1: `sampleInstrument.ts` (pure, mirrors Kotlin)**

```typescript
export interface SampleBank { id: string; roots: number[]; buffers: Map<number, AudioBuffer>; }

/** Recorded root closest to midi; ties -> lower (mirror Kotlin SampleInstrument.nearest). */
export function nearestRoot(roots: number[], midi: number): number {
  let best = roots[0], bestD = Math.abs(midi - best);
  for (let i = 1; i < roots.length; i++) {
    const d = Math.abs(midi - roots[i]);
    if (d < bestD) { best = roots[i]; bestD = d; }
  }
  return best;
}
export function pitchRate(target: number, root: number): number { return Math.pow(2, (target - root) / 12); }
```
Export from `src/audio/index.ts`.

- [ ] **Step 2: Bank loading + sampled voices in `engine.ts`**

Add a private `currentBank: SampleBank | null = null` and `setInstrument(b: SampleBank | null) { this.currentBank = b; }`. Add an async loader:
```typescript
async loadBank(inst: string): Promise<SampleBank> {
  const roots: number[] = await (await fetch(`guitar/${inst}.json`)).json();
  const buffers = new Map<number, AudioBuffer>();
  const ctx = this.ensure();
  await Promise.all(roots.map(async (m) => {
    const bytes = await (await fetch(`guitar/${inst}_${m}.wav`)).arrayBuffer();
    buffers.set(m, await ctx.decodeAudioData(bytes));
  }));
  return { id: inst, roots, buffers };
}
```
In the MODERN note/chord paths, when `currentBank` is set, build the voice from a sample buffer at `playbackRate` instead of a synth-rendered buffer:
```typescript
// modern playNote (currentBank set):
const root = nearestRoot(this.currentBank.roots, midiNote);
this.playModernSampleVoice(this.currentBank.buffers.get(root)!, pitchRate(midiNote, root),
   panForMidi(midiNote), timbre.reverbSend, 1.0, timbre.releaseMs);
```
Add `playModernSampleVoice(buffer, rate, pan, reverbSend, level, releaseMs, startAt?)` — identical graph to `playModernVoice` (env → panner → dry + reverb send) but the source is `ctx.createBufferSource(); src.buffer = buffer; src.playbackRate.value = rate;` (no synth buffer). Chord = one per note (pan `panForMidi(midi)`, level `1/√N`, strum via `startAt`). `playFrequency` sampled: nearest midi = `Math.round(69 + 12*Math.log2(freq/440))`. Legacy path and drums unchanged.

- [ ] **Step 3: Sound state in `appState.ts` + Sound picker in `ui.ts`**

`appState.ts`: `sound: "Synth"|"Acoustic"|"Nylon"|"Electric"` (persisted to localStorage like other settings), `setSound(s)` → `if Synth: audio.setInstrument(null)` else `audio.loadBank(s.toLowerCase())` (cache per id) then `audio.setInstrument(bank)`; a `soundLoading` flag. `ui.ts`: in the 🎚 Audio popup, a row of buttons/`segmented` for Synth/Acoustic/Nylon/Electric bound to `setSound`, showing "(loading…)" while fetching. A/B toggle stays.

- [ ] **Step 4: verify.ts parity checks**

Add `check("nearestRoot ties lower", nearestRoot([40,44,48],42)===40)`, `check("pitchRate octave", pitchRate(72,60)===2)`.

- [ ] **Step 5: Commit**

```bash
git add chorect-web/src/audio/sampleInstrument.ts chorect-web/src/audio/index.ts chorect-web/src/audio/engine.ts chorect-web/src/app/appState.ts chorect-web/src/app/ui.ts chorect-web/test/verify.ts
git commit -m "feat(ear-web): sampled guitar banks + playbackRate voices + Sound picker (M5)"
```

---

## Task 6: Ship v1.23.0

- [ ] **Step 1: Bump versions** — `app/build.gradle.kts` `versionCode = 12300`, `versionName = "1.23.0"`; `chorect-web/package.json` "1.23.0".
- [ ] **Step 2: Size + build** — `du -sh app/src/main/assets/guitar` (confirm ≤30 MB); archive prior APK: `cp app/build/outputs/apk/debug/Chorect_beta_V1.22.0.apk releases/ 2>/dev/null || true`; run `.\gradlew.bat :audio:test :app:assembleDebug` (green; `Chorect_beta_V1.23.0.apk` present).
- [ ] **Step 3: Commit + push + web CI**
```bash
git add -A && git commit -m "chore: release v1.23.0 (sampled guitar instruments)"
git push origin main
gh workflow run "Deploy web to GitHub Pages" --ref main
```
Watch: `gh run watch <id> --exit-status` — expect tsc/vite/deploy green.
- [ ] **Step 4: Update project-state memory** with the v1.23.0 sampled-instruments summary.

---

## Self-Review

**Spec coverage:** CC0 banks + build tool → Task 1. `SampleInstrument`/`SampleSource`/resample (Approach A) → Task 2. Engine `voiceInstrument` + sampled note/chord/freq paths + loader → Task 3. `GuitarSound` selector + Sound dropdown + persistence (Android) → Task 4. Web mirror (bank fetch, `playbackRate` voices, picker) → Task 5. Ship v1.23.0 → Task 6. Format/density/paths, nearest-tie-lower, rate formula, modern-only, A/B untouched → Global Constraints + Tasks 2/3/5. ✓

**Placeholder scan:** Task 1's Python has a genuine `# TODO per instrument` for the source→pitch mapping — unavoidable (depends on the actual CC0 files sourced at build time) and flagged as controller work, not a code placeholder in shipped app code. All app/engine code steps carry complete code.

**Type consistency:** `SampleInstrument.nearest`, `SampleSource(inst, targetMidi)` + `pitchRate(target, root)`, `GuitarSample(rootMidi, samples)`, `voiceInstrument`, `GuitarBankLoader.load(inst, openAsset)`, `GuitarSound{Synth,Acoustic,Nylon,Electric}`, web `nearestRoot(roots, midi)`/`pitchRate`/`SampleBank`/`setInstrument`/`loadBank` — consistent across tasks and Kotlin↔TS mirrors.

**Cross-task note:** Task 4 requires `SwitchableAudioEngine` to expose the modern `AudioTrackEngine` (add `modernEngine`). Called out in Task 4 Step 1 so it isn't a surprise.

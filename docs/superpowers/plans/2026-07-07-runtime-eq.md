# Runtime EQ Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A per-instrument 3-band (bass/mid/treble) runtime EQ on the modern audio bus, adjustable live in the 🎚 Audio control, replacing the baked-in nylon mid-cut.

**Architecture:** One `ThreeBandEq` (RBJ biquads) on the modern bus, before the limiter. Its gains are swapped to the currently-selected sound's stored `EqSettings` (only one sound plays at a time). Per-sound EQ lives in AppState, persisted; the active sound's EQ is pushed to the engine on select and on slider edit. Nylon is rebuilt flat and its default EQ carries mid = −4 dB. Web mirrors with 3 `BiquadFilterNode`s.

**Tech Stack:** Kotlin (`audio` module, JUnit5 + `kotlin.test`), TypeScript (`chorect-web`, WebAudio), Python (build tool), Gradle, Vite.

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-07-07-runtime-eq-design.md` (authoritative).
- **Bands:** low **shelf @120 Hz**, mid **peaking @700 Hz (Q≈0.9)**, high **shelf @3500 Hz**; each gain **±12 dB**; **0 dB = flat = bypass** (exact passthrough).
- **Placement:** modern engine bus, **before the limiter**. Android: in `VoiceMixer.mixBlock` after reverb-add, before `limiter.process`. Web: `modernMaster → eqLow → eqMid → eqHigh → modernLimiter → destination`. **Modern engine only** (legacy engine untouched).
- **One bus EQ, gains swapped per active sound.** Per-sound `EqSettings(bassDb, midDb, trebleDb)` in AppState.
- **Sounds:** `GuitarSound { Synth, Acoustic, Nylon, Electric }` (existing). Defaults: all flat EXCEPT **Nylon mid = −4 dB**.
- **Baked nylon cut removed:** drop nylon from `tools/build_guitar_samples.py` `EQ` dict and rebuild nylon flat (the runtime default replaces it).
- **Persistence:** per-sound EQ persisted (Android DataStore key `guitar_eq`; web localStorage), restored on startup, active sound's EQ pushed to engine after restore.
- **UI:** Bass/Mid/Treble sliders (−12…+12 dB, value shown) + a **Flat** reset in the 🎚 Audio control, under the Sound picker, acting on the current sound.
- **Versioning:** ship v1.24.0 (`versionCode = 12400`, `versionName = "1.24.0"`; web `package.json` "1.24.0"). Archive prior APK to `releases/`.
- **Commands:** Kotlin tests `.\gradlew.bat :audio:test` (Windows — `.\gradlew.bat`). Web has NO local Node — verify via CI (tsc + vite). Web deploy: `gh workflow run "Deploy web to GitHub Pages" --ref main`.
- **Commit** after each task; push only at ship.

---

## File Structure

**Android (`audio/src/main/kotlin/app/guitar/audio/`, package `app.guitar.audio`):**
- Create `ThreeBandEq.kt` — `EqSettings`, `Biquad`, `ThreeBandEq`.
- Modify `VoiceMixer.kt` — own a `ThreeBandEq`, apply before limiter; `setEq(...)`.
- Modify `AudioTrackEngine.kt` — `fun setEq(bassDb, midDb, trebleDb)` → mixer.
- Test `audio/src/test/kotlin/app/guitar/audio/ThreeBandEqTest.kt`.

**Android app (`app/src/main/kotlin/app/guitar/app/`):**
- Modify `AppState.kt` — per-sound EQ map + `setEqBand`, push on setSound/edit/startup.
- Modify `TuningRepository.kt` — persist `guitar_eq`.
- Modify `AudioQuick.kt` — Bass/Mid/Treble sliders + Flat.

**Web (`chorect-web/src/`):**
- Modify `audio/engine.ts` — biquad chain + `setEq`.
- Modify `app/appState.ts` — per-sound EQ map + persistence + push.
- Modify `app/ui.ts` — EQ sliders in the audio popup.

**Build tool:** `tools/build_guitar_samples.py` — drop nylon from `EQ`; rebuild nylon flat.

---

## Task 1: `ThreeBandEq` DSP (pure Kotlin, TDD)

**Files:**
- Create: `audio/src/main/kotlin/app/guitar/audio/ThreeBandEq.kt`
- Test: `audio/src/test/kotlin/app/guitar/audio/ThreeBandEqTest.kt`

**Interfaces:**
- Produces:
  - `data class EqSettings(val bassDb: Float = 0f, val midDb: Float = 0f, val trebleDb: Float = 0f)`
  - `class ThreeBandEq(sampleRate: Int)` with `fun setGainsDb(bass: Float, mid: Float, treble: Float)` and `fun process(l: FloatArray, r: FloatArray, count: Int)`. Bypasses (exact passthrough) when all three gains are 0.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.guitar.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ThreeBandEqTest {
    private val SR = 44100
    private fun tone(freq: Double, n: Int) = FloatArray(n) { (0.5 * sin(2 * PI * freq * it / SR)).toFloat() }
    // RMS of the second half (after filter settling)
    private fun rms(x: FloatArray): Double {
        var s = 0.0; val from = x.size / 2
        for (i in from until x.size) s += x[i].toDouble() * x[i]
        return sqrt(s / (x.size - from))
    }
    private fun runEq(eq: ThreeBandEq, freq: Double): Double {
        val l = tone(freq, SR); val r = tone(freq, SR)
        eq.process(l, r, l.size)
        return rms(l)
    }

    @Test fun `flat is exact passthrough`() {
        val eq = ThreeBandEq(SR); eq.setGainsDb(0f, 0f, 0f)
        val l = tone(440.0, 1000); val r = tone(440.0, 1000)
        val lin = l.copyOf()
        eq.process(l, r, l.size)
        for (i in l.indices) assertEquals(lin[i], l[i], 1e-6f)
    }

    @Test fun `bass boost raises lows, leaves highs about unchanged`() {
        val ref = rms(tone(80.0, SR))                 // input level of an 80 Hz tone
        val boost = ThreeBandEq(SR).also { it.setGainsDb(9f, 0f, 0f) }
        assertTrue(runEq(boost, 80.0) > ref * 1.5, "80 Hz should be boosted")
        val flatHi = ThreeBandEq(SR).also { it.setGainsDb(9f, 0f, 0f) }
        val hiRef = rms(tone(6000.0, SR))
        assertTrue(runEq(flatHi, 6000.0) in (hiRef * 0.8)..(hiRef * 1.2), "6 kHz ~unchanged by bass")
    }

    @Test fun `treble boost raises highs`() {
        val hiRef = rms(tone(8000.0, SR))
        val eq = ThreeBandEq(SR).also { it.setGainsDb(0f, 0f, 9f) }
        assertTrue(runEq(eq, 8000.0) > hiRef * 1.5, "8 kHz should be boosted")
    }

    @Test fun `mid cut at 700Hz reduces a 700Hz tone`() {
        val ref = rms(tone(700.0, SR))
        val eq = ThreeBandEq(SR).also { it.setGainsDb(0f, -9f, 0f) }
        assertTrue(runEq(eq, 700.0) < ref * 0.6, "700 Hz should be cut")
    }

    @Test fun `stable and bounded for full-scale input`() {
        val eq = ThreeBandEq(SR).also { it.setGainsDb(12f, 12f, 12f) }
        val l = FloatArray(SR) { 1f }; val r = FloatArray(SR) { 1f }
        eq.process(l, r, l.size)
        for (v in l) assertTrue(v.isFinite() && kotlin.math.abs(v) < 20f, "bounded: $v")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.ThreeBandEqTest"`
Expected: FAIL — unresolved `ThreeBandEq`/`EqSettings`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.guitar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Per-sound EQ gains in dB, each in [-12, 12]. 0 = flat. */
data class EqSettings(val bassDb: Float = 0f, val midDb: Float = 0f, val trebleDb: Float = 0f)

/** One RBJ biquad (Direct Form I), mono state. Coeffs are stored already divided by a0. */
private class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = xd; y2 = y1; y1 = y
        return y.toFloat()
    }

    private fun set(b0n: Double, b1n: Double, b2n: Double, a0: Double, a1n: Double, a2n: Double) {
        b0 = b0n / a0; b1 = b1n / a0; b2 = b2n / a0; a1 = a1n / a0; a2 = a2n / a0
    }

    fun lowShelf(fc: Double, sr: Int, gainDb: Double) {
        val A = 10.0.pow(gainDb / 40.0); val w0 = 2 * PI * fc / sr
        val cw = cos(w0); val alpha = sin(w0) / 2.0 * sqrt(2.0); val ta = 2 * sqrt(A) * alpha
        set(
            A * ((A + 1) - (A - 1) * cw + ta),
            2 * A * ((A - 1) - (A + 1) * cw),
            A * ((A + 1) - (A - 1) * cw - ta),
            (A + 1) + (A - 1) * cw + ta,
            -2 * ((A - 1) + (A + 1) * cw),
            (A + 1) + (A - 1) * cw - ta,
        )
    }

    fun highShelf(fc: Double, sr: Int, gainDb: Double) {
        val A = 10.0.pow(gainDb / 40.0); val w0 = 2 * PI * fc / sr
        val cw = cos(w0); val alpha = sin(w0) / 2.0 * sqrt(2.0); val ta = 2 * sqrt(A) * alpha
        set(
            A * ((A + 1) + (A - 1) * cw + ta),
            -2 * A * ((A - 1) + (A + 1) * cw),
            A * ((A + 1) + (A - 1) * cw - ta),
            (A + 1) - (A - 1) * cw + ta,
            2 * ((A - 1) - (A + 1) * cw),
            (A + 1) - (A - 1) * cw - ta,
        )
    }

    fun peaking(fc: Double, sr: Int, q: Double, gainDb: Double) {
        val A = 10.0.pow(gainDb / 40.0); val w0 = 2 * PI * fc / sr
        val cw = cos(w0); val alpha = sin(w0) / (2 * q)
        set(1 + alpha * A, -2 * cw, 1 - alpha * A, 1 + alpha / A, -2 * cw, 1 - alpha / A)
    }
}

/**
 * Stereo 3-band tone EQ: low shelf @120 Hz, mid peak @700 Hz (Q≈0.9), high shelf @3500 Hz.
 * Gains in dB (±12). When all three gains are 0 it bypasses — [process] leaves the input
 * untouched (exact passthrough), and no filter state accumulates. Pure Kotlin.
 */
class ThreeBandEq(private val sampleRate: Int) {
    private companion object { const val BASS_HZ = 120.0; const val MID_HZ = 700.0; const val MID_Q = 0.9; const val TREBLE_HZ = 3500.0 }
    private val chainL = arrayOf(Biquad(), Biquad(), Biquad())   // bass, mid, treble
    private val chainR = arrayOf(Biquad(), Biquad(), Biquad())
    private var active = false

    fun setGainsDb(bass: Float, mid: Float, treble: Float) {
        val wasActive = active
        active = bass != 0f || mid != 0f || treble != 0f
        if (!active) return
        for (c in listOf(chainL, chainR)) {
            c[0].lowShelf(BASS_HZ, sampleRate, bass.toDouble())
            c[1].peaking(MID_HZ, sampleRate, MID_Q, mid.toDouble())
            c[2].highShelf(TREBLE_HZ, sampleRate, treble.toDouble())
        }
        if (!wasActive) { chainL.forEach { it.reset() }; chainR.forEach { it.reset() } }
    }

    fun process(l: FloatArray, r: FloatArray, count: Int) {
        if (!active) return
        for (i in 0 until count) {
            var vl = l[i]; var vr = r[i]
            for (b in 0..2) { vl = chainL[b].process(vl); vr = chainR[b].process(vr) }
            l[i] = vl; r[i] = vr
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.ThreeBandEqTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/ThreeBandEq.kt audio/src/test/kotlin/app/guitar/audio/ThreeBandEqTest.kt
git commit -m "feat(audio): ThreeBandEq RBJ tone EQ (M1)"
```

---

## Task 2: Android engine wiring

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt`
- Modify: `audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt`

**Interfaces:**
- Consumes: `ThreeBandEq` (Task 1).
- Produces: `VoiceMixer.setEq(bassDb, midDb, trebleDb)`; `AudioTrackEngine.setEq(bassDb, midDb, trebleDb)`.

- [ ] **Step 1: Add the EQ to `VoiceMixer`**

In `VoiceMixer.kt`, add a field and apply it after the reverb-add and BEFORE the limiter. Find the tail of `mixBlock`:
```kotlin
        freeverb.process(sendL, sendR, count)
        for (i in 0 until count) { outL[i] += sendL[i]; outR[i] += sendR[i] }
        limiter.process(outL, outR, count)
```
Change to:
```kotlin
        freeverb.process(sendL, sendR, count)
        for (i in 0 until count) { outL[i] += sendL[i]; outR[i] += sendR[i] }
        eq.process(outL, outR, count)
        limiter.process(outL, outR, count)
```
Add the field near the other bus effects (`limiter`, `freeverb`):
```kotlin
    private val eq = ThreeBandEq(sampleRate)
    @Synchronized fun setEq(bassDb: Float, midDb: Float, trebleDb: Float) = eq.setGainsDb(bassDb, midDb, trebleDb)
```

- [ ] **Step 2: Expose `setEq` on the engine**

In `AudioTrackEngine.kt`, add:
```kotlin
    /** Set the modern-bus tone EQ gains (dB). */
    fun setEq(bassDb: Float, midDb: Float, trebleDb: Float) = mixer.setEq(bassDb, midDb, trebleDb)
```

- [ ] **Step 3: Build + test**

Run: `.\gradlew.bat :audio:test` (all pass — EQ defaults inactive, no behavior change) then `.\gradlew.bat :app:assembleDebug` (BUILD SUCCESSFUL).

- [ ] **Step 4: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt
git commit -m "feat(audio): wire ThreeBandEq onto the modern bus before the limiter (M2)"
```

---

## Task 3: Android AppState EQ state + persistence + UI + nylon rebuild

**Files:**
- Modify: `app/src/main/kotlin/app/guitar/app/AppState.kt`
- Modify: `app/src/main/kotlin/app/guitar/app/TuningRepository.kt`
- Modify: `app/src/main/kotlin/app/guitar/app/AudioQuick.kt`
- Modify: `tools/build_guitar_samples.py` (drop nylon EQ) + rebuild nylon flat.

**Interfaces:**
- Consumes: `AudioTrackEngine.setEq` (Task 2), `EqSettings` (Task 1), `SwitchableAudioEngine.modernEngine` (existing).
- Produces: `AppState.eqFor(sound): EqSettings`, `AppState.setEqBand(sound, band, db)`, `enum Band { Bass, Mid, Treble }`.

- [ ] **Step 1: EQ state + push logic in `AppState`**

Add:
```kotlin
enum class Band { Bass, Mid, Treble }

// default: all flat except Nylon mid = -4 (migrated from the baked cut)
private val eq = java.util.EnumMap<GuitarSound, app.guitar.audio.EqSettings>(GuitarSound::class.java).apply {
    GuitarSound.entries.forEach { put(it, app.guitar.audio.EqSettings()) }
    put(GuitarSound.Nylon, app.guitar.audio.EqSettings(midDb = -4f))
}
var eqVersion by mutableStateOf(0)   // bump to trigger recompose of the sliders
    private set

fun eqFor(s: GuitarSound): app.guitar.audio.EqSettings = eq[s] ?: app.guitar.audio.EqSettings()

private fun pushEq(s: GuitarSound) {
    if (s != sound) return
    val e = eqFor(s)
    (audio as? app.guitar.audio.SwitchableAudioEngine)?.modernEngine?.setEq(e.bassDb, e.midDb, e.trebleDb)
}

fun setEqBand(s: GuitarSound, band: Band, db: Float) {
    val e = eqFor(s)
    eq[s] = when (band) {
        Band.Bass -> e.copy(bassDb = db); Band.Mid -> e.copy(midDb = db); Band.Treble -> e.copy(trebleDb = db)
    }
    eqVersion++
    pushEq(s)
    scope.launch { repo.setGuitarEq(encodeEq()) }
}

fun resetEq(s: GuitarSound) {
    eq[s] = app.guitar.audio.EqSettings(); eqVersion++; pushEq(s)
    scope.launch { repo.setGuitarEq(encodeEq()) }
}

private fun encodeEq(): String = GuitarSound.entries.joinToString(";") {
    val e = eqFor(it); "${it.name},${e.bassDb},${e.midDb},${e.trebleDb}"
}
private fun decodeEq(s: String) {
    s.split(";").forEach { row ->
        val p = row.split(","); if (p.size == 4) runCatching {
            eq[GuitarSound.valueOf(p[0])] = app.guitar.audio.EqSettings(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
        }
    }
}
```
In `setSound(s)` (after it sets `modern.voiceInstrument`), add `pushEq(s)` so the new sound's EQ is applied. On startup (where `guitar_sound` is restored), also read `repo.guitarEq.first()`, `decodeEq(...)` (guard blank), then `pushEq(sound)`.

- [ ] **Step 2: Persist in `TuningRepository`**

Add a `guitar_eq` string preference mirroring `guitar_sound`: `val guitarEq: Flow<String>` (default "") + `suspend fun setGuitarEq(v: String)`.

- [ ] **Step 3: EQ sliders + Flat in `AudioQuick.kt`**

In `AudioQuickSliders`, below the Sound picker, read `state.eqVersion` (to recompose) and `val e = state.eqFor(state.sound)`, then three sliders:
```kotlin
Spacer(Modifier.height(8.dp))
Text("Tone — ${state.sound.name}", style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
@Composable fun band(label: String, value: Float, b: Band) {
    Text("$label: ${value.toInt()} dB", style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = { state.setEqBand(state.sound, b, it) }, valueRange = -12f..12f)
}
band("Bass", e.bassDb, Band.Bass); band("Mid", e.midDb, Band.Mid); band("Treble", e.trebleDb, Band.Treble)
TextButton(onClick = { state.resetEq(state.sound) }) { Text("Flat") }
```
(Reference `state.eqVersion` once in the composable scope so slider edits recompose.)

- [ ] **Step 4: Rebuild nylon flat**

Edit `tools/build_guitar_samples.py`: remove the `"nylon"` entry from the `EQ` dict (leave the EQ mechanism for future use). Then rebuild just nylon's WAVs:
```bash
rm -f app/src/main/assets/guitar/nylon_*.wav chorect-web/public/guitar/nylon_*.wav
python tools/build_guitar_samples.py
```
(The tool rebuilds all three; acoustic/electric outputs are unchanged byte-wise. Nylon is now flat; the runtime default mid −4 replaces the baked cut.)

- [ ] **Step 5: Build + smoke**

Run: `.\gradlew.bat :app:assembleDebug`. Manual: 🎚 Audio → Nylon shows Mid at −4; moving Bass/Mid/Treble changes tone live; Flat resets; switching Sound repoints sliders; persists across restart.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/app/guitar/app/AppState.kt app/src/main/kotlin/app/guitar/app/TuningRepository.kt app/src/main/kotlin/app/guitar/app/AudioQuick.kt tools/build_guitar_samples.py app/src/main/assets/guitar chorect-web/public/guitar
git commit -m "feat(audio): per-sound EQ state + persistence + 🎚 sliders; nylon rebuilt flat (M3)"
```

---

## Task 4: Web mirror

**Files:**
- Modify: `chorect-web/src/audio/engine.ts`
- Modify: `chorect-web/src/app/appState.ts`
- Modify: `chorect-web/src/app/ui.ts`

**Interfaces:**
- Consumes: `WebAudioEngine` modern chain (`modernMaster`, `modernLimiter`), the `sound` state.
- Produces: `WebAudioEngine.setEq(bass, mid, treble)`; `appState` per-sound EQ map + `setEqBand`.

> Web has NO local Node — write tsc-valid TS; verify via CI.

- [ ] **Step 1: Biquad chain in `engine.ts`**

In `ensure()`, replace the `modernMaster → modernLimiter` connection with a 3-node EQ chain:
```typescript
this.eqLow = this.ctx.createBiquadFilter(); this.eqLow.type = "lowshelf"; this.eqLow.frequency.value = 120;
this.eqMid = this.ctx.createBiquadFilter(); this.eqMid.type = "peaking"; this.eqMid.frequency.value = 700; this.eqMid.Q.value = 0.9;
this.eqHigh = this.ctx.createBiquadFilter(); this.eqHigh.type = "highshelf"; this.eqHigh.frequency.value = 3500;
this.modernMaster.connect(this.eqLow); this.eqLow.connect(this.eqMid); this.eqMid.connect(this.eqHigh);
this.eqHigh.connect(this.modernLimiter);
```
(Declare `eqLow/eqMid/eqHigh: BiquadFilterNode | null`.) Add:
```typescript
setEq(bass: number, mid: number, treble: number): void {
  if (!this.eqLow) this.ensure();
  this.eqLow!.gain.value = bass; this.eqMid!.gain.value = mid; this.eqHigh!.gain.value = treble;
}
```
(Reverb still connects to `modernMaster`, so reverb goes through the EQ too — same as Android where EQ is after the reverb-add.)

- [ ] **Step 2: Per-sound EQ + persistence in `appState.ts`**

Add `eq: Record<SoundName, {bass:number;mid:number;treble:number}>` (default all 0 except Nylon mid −4), persisted to localStorage alongside `sound`. `setEqBand(sound, band, db)`: update, persist, and if `sound === this.sound` call `audio.setEq(...)`. `resetEq(sound)`: zero it. In `applySound(s)`, after setting the instrument, call `this.audio.setEq(eq[s].bass, eq[s].mid, eq[s].treble)`. Restore on init and push the active sound's EQ.

- [ ] **Step 3: EQ sliders in `ui.ts`**

In the 🎚 Audio popup, under the Sound picker, three `slider(-12, 12, value, cb)` for Bass/Mid/Treble of the current sound + a Flat button, mirroring Android. Label with the current sound name and dB values.

- [ ] **Step 4: Commit**

```bash
git add chorect-web/src/audio/engine.ts chorect-web/src/app/appState.ts chorect-web/src/app/ui.ts
git commit -m "feat(ear-web): per-sound EQ biquad chain + sliders + persistence (M4)"
```

---

## Task 5: Ship v1.24.0

- [ ] **Step 1: Bump versions** — `app/build.gradle.kts` `versionCode = 12400`, `versionName = "1.24.0"`; `chorect-web/package.json` "1.24.0".
- [ ] **Step 2: Build + archive** — `cp app/build/outputs/apk/debug/Chorect_beta_V1.23.1.apk releases/ 2>/dev/null || true`; `.\gradlew.bat :audio:test :app:assembleDebug` (green; `Chorect_beta_V1.24.0.apk`); `du -sh app/src/main/assets/guitar` (≤30 MB).
- [ ] **Step 3: Commit + push + web CI**
```bash
git add -A && git commit -m "chore: release v1.24.0 (per-instrument runtime EQ)"
git push origin main
gh workflow run "Deploy web to GitHub Pages" --ref main
```
Watch: `gh run watch <id> --exit-status` — expect tsc/vite/deploy green.
- [ ] **Step 4: Update project-state memory** with the v1.24.0 EQ summary.

---

## Self-Review

**Spec coverage:** `ThreeBandEq` + bands/freqs/±12/bypass → Task 1. Bus placement before limiter (Android) → Task 2. Per-sound state + defaults (Nylon mid −4) + persistence + push-on-select/edit + UI + nylon-rebuild-flat → Task 3. Web mirror (biquad chain + state + sliders) → Task 4. Ship v1.24.0 → Task 5. Modern-engine-only / legacy untouched → EQ lives on the modern `VoiceMixer`/`modernMaster` only. ✓

**Placeholder scan:** All code steps carry complete code; the nylon rebuild is a concrete command sequence. No TBD/TODO.

**Type consistency:** `EqSettings(bassDb, midDb, trebleDb)`, `ThreeBandEq.setGainsDb`/`process`, `VoiceMixer.setEq`/`AudioTrackEngine.setEq`, `AppState.eqFor`/`setEqBand`/`resetEq`/`Band{Bass,Mid,Treble}`/`eqVersion`, `TuningRepository.guitarEq`/`setGuitarEq`, web `WebAudioEngine.setEq`/`eqLow/eqMid/eqHigh` — consistent across tasks and Kotlin↔TS.

**Note:** Task 3 references `SwitchableAudioEngine.modernEngine` and `GuitarSound`/`setSound`/`sound` — all exist from v1.23.x. The EQ push on `setSound` is an addition to the existing method.

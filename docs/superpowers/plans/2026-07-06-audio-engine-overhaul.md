# Audio Engine Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the pre-rendered-array mixer with a real-time voice-graph engine (per-voice pull + amp envelope + constant-power pan → stereo bus → soft limiter + gentle reverb), fixing clip-distortion, abrupt cuts, unnatural decay, and poor multi-string mixing on both Android and web.

**Architecture:** Approach A from the spec. Each sound (Karplus-Strong note, chord note, drum one-shot) becomes a `VoiceSource` the mixer pulls in blocks. The mixer applies an amp envelope (attack declick + release ramp) and constant-power pan per voice, sums into a stereo bus, then runs a master chain (gentle algorithmic reverb + soft peak limiter). Pure DSP is extracted into headless, JVM-unit-tested classes; `AudioTrack`/WebAudio are thin sinks. Web mirrors the topology idiomatically (WebAudio nodes) for *sonic* parity.

**Tech Stack:** Kotlin (`audio` module, JUnit5 + `kotlin.test`), TypeScript (`chorect-web`, WebAudio API), Gradle, Vite.

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-07-06-audio-engine-overhaul-design.md` (authoritative).
- **Public `AudioEngine` interface is unchanged** — `playNote / playFrequency / playChord / playSamples / playSamplesAt / stop / close` keep exact signatures. Callers must not need edits.
- **Tone target:** roomy / **subtle** bloom — reverb `wet` is gentle; default per-note `reverbSend ≈ 0.18`; drums `reverbSend ≈ 0`.
- **Output:** stereo. Android `AudioTrack` → `CHANNEL_OUT_STEREO`, interleaved 16-bit PCM. Web → 2-channel via `StereoPannerNode`.
- **Parity:** *sonic*, not bit-exact. Share pure math constants (pan law, envelope ms, limiter ceiling) verbatim across languages; implement each stage idiomatically per platform.
- **Limiter ceiling:** −0.5 dBFS ≈ linear `0.944`.
- **Envelope timings:** attack ≈ 3 ms, release ≈ 20 ms (from `Timbre.releaseMs`, default 20).
- **Block size:** mixer processes in blocks of `CHUNK = 128` frames.
- **`MAX_VOICES = 16`**; overflow releases the *quietest* voice (never a hard drop).
- **Versioning:** ship at end of M6 as `versionCode = 12200`, `versionName = "1.22.0"`, `chorect-web/package.json` `"1.22.0"`. Archive the prior debug APK to `releases/` before `assembleDebug`.
- **Kotlin tests:** `./gradlew.bat :audio:test` (Windows: `.\gradlew.bat`, no `./gradlew`).
- **Web verify:** add `check(name, cond)` assertions to `chorect-web/test/verify.ts`; runs via `npm run verify` on CI (no local Node). Web deploy is `workflow_dispatch` only: `gh workflow run "Deploy web to GitHub Pages" --ref main`.
- **Commit** after each task. **Do not push** until a milestone is complete and Nadav approves.

---

## File Structure

**Android (`audio/src/main/kotlin/app/guitar/audio/`, package `app.guitar.audio`):**
- Create `VoiceSource.kt` — `VoiceSource` interface + `BufferSource` (wraps a `FloatArray`).
- Create `AmpEnvelope.kt` — attack/sustain/release gate, block `applyInPlace`.
- Create `Panner.kt` — `panGains(pan)` constant-power law + `panForMidi(midi)`.
- Create `SoftLimiter.kt` — peak-following soft limiter to a ceiling.
- Create `Freeverb.kt` — algorithmic reverb (comb+allpass), stereo, `isRingingOut()`.
- Create `VoiceMixer.kt` — headless mixdown: list of `MixVoice` → stereo L/R blocks + reverb + limiter. The testable core.
- Modify `Timbre.kt` — add `pan`, `reverbSend`, `releaseMs`.
- Modify `AudioTrackEngine.kt` — stereo `AudioTrack`, delegates mixing to `VoiceMixer`, voice add/release/steal, corrected idle-park.
- Modify `PluckedSynth.kt` (M6) — dual-rate damping.

**Android tests (`audio/src/test/kotlin/app/guitar/audio/`):** `VoiceSourceTest.kt`, `AmpEnvelopeTest.kt`, `PannerTest.kt`, `SoftLimiterTest.kt`, `FreeverbTest.kt`, `VoiceMixerTest.kt`.

**Web (`chorect-web/src/audio/`):**
- Create `panner.ts` — `panGains(pan)` + `panForMidi(midi)` (mirror).
- Create `reverbIR.ts` — `buildReverbIR(ctx)` → `AudioBuffer` for `ConvolverNode`.
- Modify `timbre.ts` — add `pan`, `reverbSend`, `releaseMs`.
- Modify `engine.ts` — stereo voice graph (env `GainNode` + `StereoPannerNode` + reverb send + `ConvolverNode` + master `DynamicsCompressorNode`), chord→per-note voices, `stop()` ramp, drum panning.
- Modify `pluckedSynth.ts` (M6) — dual-rate damping.
- Modify `test/verify.ts` — parity `check()`s for `panGains`.

---

## MILESTONE M1 — Voice abstraction + pull-model mixer (behavior-preserving, mono)

Goal: introduce `VoiceSource`/`BufferSource` and a headless `VoiceMixer` that sums voices, and route `AudioTrackEngine` through them. Still **mono, no envelope, no bus** — output must sound identical to today. Proves the refactor is safe.

### Task 1: `VoiceSource` interface + `BufferSource`

**Files:**
- Create: `audio/src/main/kotlin/app/guitar/audio/VoiceSource.kt`
- Test: `audio/src/test/kotlin/app/guitar/audio/VoiceSourceTest.kt`

**Interfaces:**
- Produces: `interface VoiceSource { fun render(out: FloatArray, count: Int): Int; val isFinished: Boolean }` and `class BufferSource(private val samples: FloatArray) : VoiceSource`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.guitar.audio

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class VoiceSourceTest {
    @Test
    fun `buffer source yields all samples then finishes`() {
        val src = BufferSource(floatArrayOf(1f, 2f, 3f, 4f, 5f))
        val out = FloatArray(3)
        assertEquals(3, src.render(out, 3))
        assertEquals(listOf(1f, 2f, 3f), out.toList())
        assertFalse(src.isFinished)
        assertEquals(2, src.render(out, 3))   // only 2 left
        assertEquals(listOf(4f, 5f), out.toList().take(2))
        assertTrue(src.isFinished)
        assertEquals(0, src.render(out, 3))    // drained
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.VoiceSourceTest"`
Expected: FAIL — `VoiceSource`/`BufferSource` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.guitar.audio

/** A pullable mono audio source. The mixer calls [render] block-wise. */
interface VoiceSource {
    /** Fill out[0 until count] with up to [count] mono samples; return the number
     *  actually produced (< count when the source runs dry). */
    fun render(out: FloatArray, count: Int): Int
    /** True once fully drained. */
    val isFinished: Boolean
}

/** A [VoiceSource] that replays a pre-rendered [samples] buffer once. Wraps both
 *  Karplus-Strong note output and cached percussion one-shots. */
class BufferSource(private val samples: FloatArray) : VoiceSource {
    private var pos = 0
    override fun render(out: FloatArray, count: Int): Int {
        val n = minOf(count, samples.size - pos)
        if (n <= 0) return 0
        System.arraycopy(samples, pos, out, 0, n)
        pos += n
        return n
    }
    override val isFinished: Boolean get() = pos >= samples.size
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.VoiceSourceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/VoiceSource.kt audio/src/test/kotlin/app/guitar/audio/VoiceSourceTest.kt
git commit -m "feat(audio): VoiceSource interface + BufferSource (M1)"
```

### Task 2: Headless `VoiceMixer` — mono sum (behavior-preserving)

**Files:**
- Create: `audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt`
- Test: `audio/src/test/kotlin/app/guitar/audio/VoiceMixerTest.kt`

**Interfaces:**
- Consumes: `VoiceSource`, `BufferSource` (Task 1).
- Produces:
  - `class MixVoice(val source: VoiceSource, var gain: Float = 1f, var delayFrames: Int = 0)` with internal `envelope`/`pan` added later (M2/M3).
  - `class VoiceMixer(val sampleRate: Int)` with `fun add(v: MixVoice)`, `fun mixBlock(outL: FloatArray, outR: FloatArray, count: Int)`, `fun releaseAll()` (M2), `fun clear()`, `val activeCount: Int`. In M1, `outL`/`outR` receive the identical mono sum (L == R) so behavior matches the current mono engine.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.guitar.audio

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class VoiceMixerTest {
    @Test
    fun `sums two voices sample-accurately (mono, L equals R in M1)`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(floatArrayOf(0.2f, 0.2f, 0.2f))))
        m.add(MixVoice(BufferSource(floatArrayOf(0.1f, 0.1f))))
        val l = FloatArray(3); val r = FloatArray(3)
        m.mixBlock(l, r, 3)
        assertEquals(0.3f, l[0], 1e-6f)
        assertEquals(0.3f, l[1], 1e-6f)
        assertEquals(0.2f, l[2], 1e-6f)   // 2nd voice drained
        assertEquals(l.toList(), r.toList())
    }

    @Test
    fun `delayFrames postpones a voice on the mixer clock`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(floatArrayOf(1f, 1f)), delayFrames = 2))
        val l = FloatArray(4); val r = FloatArray(4)
        m.mixBlock(l, r, 4)
        assertEquals(listOf(0f, 0f, 1f, 1f), l.toList())
    }

    @Test
    fun `finished voices are removed`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(floatArrayOf(0.5f))))
        val l = FloatArray(2); val r = FloatArray(2)
        m.mixBlock(l, r, 2)
        assertEquals(0, m.activeCount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.VoiceMixerTest"`
Expected: FAIL — `VoiceMixer`/`MixVoice` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.guitar.audio

/** One active voice in the mixer: a pull [source] plus per-voice controls. In M1
 *  only [gain] and [delayFrames] are used; [pan]/[envelope] arrive in M2/M3. */
class MixVoice(
    val source: VoiceSource,
    var gain: Float = 1f,
    delayFrames: Int = 0,
) {
    /** Frames still to wait before this voice starts sounding (mixer clock). */
    var remainingDelay: Int = delayFrames.coerceAtLeast(0)
}

/** Headless real-time mixer: sums active voices into stereo L/R blocks. No Android
 *  API — unit-testable on the JVM. In M1 it produces a plain mono sum (L == R).
 *  Master bus (pan/reverb/limiter) is layered on in M3–M5. */
class VoiceMixer(val sampleRate: Int) {
    private val voices = ArrayList<MixVoice>()
    private val scratch = FloatArray(4096)

    val activeCount: Int get() = voices.size

    @Synchronized fun add(v: MixVoice) { voices.add(v) }
    @Synchronized fun clear() { voices.clear() }

    /** Mix [count] frames. outL/outR must be >= count. */
    @Synchronized fun mixBlock(outL: FloatArray, outR: FloatArray, count: Int) {
        for (i in 0 until count) { outL[i] = 0f; outR[i] = 0f }
        val it = voices.iterator()
        while (it.hasNext()) {
            val v = it.next()
            var i = 0
            // Consume any scheduling delay first.
            if (v.remainingDelay > 0) {
                val d = minOf(v.remainingDelay, count)
                v.remainingDelay -= d
                i = d
            }
            while (i < count) {
                val want = minOf(count - i, scratch.size)
                val n = v.source.render(scratch, want)
                if (n <= 0) break
                for (j in 0 until n) {
                    val s = scratch[j] * v.gain
                    outL[i + j] += s
                    outR[i + j] += s
                }
                i += n
                if (n < want) break
            }
            if (v.source.isFinished && v.remainingDelay == 0) it.remove()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.VoiceMixerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt audio/src/test/kotlin/app/guitar/audio/VoiceMixerTest.kt
git commit -m "feat(audio): headless VoiceMixer, mono sum + delay + voice removal (M1)"
```

### Task 3: Route `AudioTrackEngine` through `VoiceMixer` (still mono, behavior-preserving)

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt`

**Interfaces:**
- Consumes: `VoiceMixer`, `MixVoice`, `BufferSource` (Tasks 1–2).
- Produces: unchanged `AudioEngine` behavior. `addVoice(samples, gain, delayFrames)` now builds a `MixVoice(BufferSource(samples), gain, delayFrames)` and calls `mixer.add`.

- [ ] **Step 1: Replace the voice storage + output loop**

In `AudioTrackEngine.kt`, delete the private `Voice` class, the `voicesLock`/`voices` fields, and the per-sample mixing inside `runOutputLoop`. Add a mixer and a stereo output loop. Keep `CHANNEL_OUT_MONO` for M1 (stereo comes in M3) by writing the L channel only:

```kotlin
private val mixer = VoiceMixer(sampleRate)

// in runOutputLoop(): replace the mix body with —
val chunkFrames = 128
val l = FloatArray(chunkFrames)
val r = FloatArray(chunkFrames)
val chunk = ShortArray(chunkFrames)
while (running.get() && !Thread.currentThread().isInterrupted) {
    if (mixer.activeCount == 0) { try { Thread.sleep(3) } catch (_: InterruptedException) { return }; continue }
    mixer.mixBlock(l, r, chunkFrames)
    for (i in 0 until chunkFrames) {
        val s = l[i]                       // M1: mono — L only
        val c = if (s > 1f) 1f else if (s < -1f) -1f else s
        chunk[i] = (c * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }
    try {
        if (track.write(chunk, 0, chunkFrames, AudioTrack.WRITE_BLOCKING) < 0) break
    } catch (e: Exception) { if (running.get()) Log.e(TAG, "output write threw", e); break }
}
```

- [ ] **Step 2: Replace `addVoice` and `stop`**

```kotlin
private fun addVoice(samples: FloatArray, gain: Float = 1f, delayFrames: Int = 0) {
    if (samples.isEmpty()) return
    mixer.add(MixVoice(BufferSource(samples), gain, delayFrames))
    // MAX_VOICES enforcement moves into the mixer in M2 (release-quietest); M1 keeps
    // the simple cap to preserve behavior:
    mixer.capVoices(MAX_VOICES)
}

override fun stop() { mixer.clear(); }
```

Add to `VoiceMixer` (mirrors current oldest-drop for M1; replaced in M2):

```kotlin
@Synchronized fun capVoices(max: Int) { while (voices.size > max) voices.removeAt(0) }
```

- [ ] **Step 3: Build + run the full audio suite**

Run: `.\gradlew.bat :audio:test`
Expected: PASS (existing 17 + new). No behavioral assertion changed.

- [ ] **Step 4: Build the app to confirm it compiles**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual smoke check (device/emulator)**

Launch, play a chord + a drum loop. Expected: sounds **identical** to pre-refactor (this milestone changes structure only).

- [ ] **Step 6: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt
git commit -m "refactor(audio): AudioTrackEngine mixes via VoiceMixer (M1, behavior-preserving)"
```

---

## MILESTONE M2 — Amp envelope + graceful stop/steal (declick)

Goal: per-voice attack (declick start) + release ramp; `stop()` and voice-overflow trigger a release instead of a hard cut.

### Task 4: `AmpEnvelope`

**Files:**
- Create: `audio/src/main/kotlin/app/guitar/audio/AmpEnvelope.kt`
- Test: `audio/src/test/kotlin/app/guitar/audio/AmpEnvelopeTest.kt`

**Interfaces:**
- Produces: `class AmpEnvelope(sampleRate: Int, attackMs: Double = 3.0, releaseMs: Double = 20.0)` with `fun applyInPlace(buf: FloatArray, count: Int)`, `fun release()`, `val isSilent: Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.guitar.audio

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class AmpEnvelopeTest {
    @Test
    fun `attack ramps from 0 up to 1 monotonically`() {
        val env = AmpEnvelope(sampleRate = 48000, attackMs = 1.0, releaseMs = 20.0)
        val buf = FloatArray(48) { 1f }      // 1 ms = 48 frames
        env.applyInPlace(buf, 48)
        assertTrue(buf[0] < 0.05f, "starts near 0 (declick), was ${buf[0]}")
        for (i in 1 until 48) assertTrue(buf[i] >= buf[i - 1] - 1e-6f, "non-monotonic at $i")
        assertEquals(1f, buf[47], 0.05f)     // reached sustain
    }

    @Test
    fun `after release output reaches 0 and stays, then isSilent`() {
        val env = AmpEnvelope(sampleRate = 48000, attackMs = 0.0, releaseMs = 1.0)
        val warm = FloatArray(48) { 1f }
        env.applyInPlace(warm, 48)           // pass attack → sustain=1
        env.release()
        val buf = FloatArray(96) { 1f }
        env.applyInPlace(buf, 96)
        assertEquals(0f, buf[95], 1e-4f)
        assertTrue(env.isSilent)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.AmpEnvelopeTest"`
Expected: FAIL — `AmpEnvelope` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.guitar.audio

/** Per-voice amplitude gate: a short attack to declick the start, a unity sustain
 *  (the source owns the note's decay), and a release ramp triggered on stop/steal.
 *  Pure — no Android API. */
class AmpEnvelope(
    sampleRate: Int,
    attackMs: Double = 3.0,
    releaseMs: Double = 20.0,
) {
    private enum class Stage { ATTACK, SUSTAIN, RELEASE, DONE }
    private var stage = if (attackMs <= 0.0) Stage.SUSTAIN else Stage.ATTACK
    private val attackStep = if (attackMs <= 0.0) 1f else (1.0 / (sampleRate * attackMs / 1000.0)).toFloat()
    private val releaseStep = if (releaseMs <= 0.0) 1f else (1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()
    private var level = if (attackMs <= 0.0) 1f else 0f

    val isSilent: Boolean get() = stage == Stage.DONE

    fun release() { if (stage != Stage.DONE) stage = Stage.RELEASE }

    fun applyInPlace(buf: FloatArray, count: Int) {
        for (i in 0 until count) {
            when (stage) {
                Stage.ATTACK -> { level += attackStep; if (level >= 1f) { level = 1f; stage = Stage.SUSTAIN } }
                Stage.SUSTAIN -> { }
                Stage.RELEASE -> { level -= releaseStep; if (level <= 0f) { level = 0f; stage = Stage.DONE } }
                Stage.DONE -> level = 0f
            }
            buf[i] *= level
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.AmpEnvelopeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/AmpEnvelope.kt audio/src/test/kotlin/app/guitar/audio/AmpEnvelopeTest.kt
git commit -m "feat(audio): AmpEnvelope attack/release gate (M2)"
```

### Task 5: Wire envelope + release-based stop/steal into `VoiceMixer`

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt`
- Modify: `audio/src/test/kotlin/app/guitar/audio/VoiceMixerTest.kt`

**Interfaces:**
- Consumes: `AmpEnvelope` (Task 4).
- Produces: `MixVoice` gains `val envelope: AmpEnvelope`. `VoiceMixer.releaseAll()` (release every voice). `capVoices(max)` now **releases the quietest** rather than dropping. A voice is removed only when `source.isFinished || envelope.isSilent` **and** `envelope.isSilent` for released ones — precisely: remove when `envelope.isSilent`, or when `source.isFinished` while not mid-release.

- [ ] **Step 1: Write the failing test (release fades, not cut; steal picks quietest)**

```kotlin
    @Test
    fun `releaseAll fades voices instead of cutting`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(FloatArray(48000) { 1f }), envelope = AmpEnvelope(48000, 0.0, 1.0)))
        val l = FloatArray(48); val r = FloatArray(48)
        m.mixBlock(l, r, 48)                 // sustain ~1
        m.releaseAll()
        val l2 = FloatArray(96); val r2 = FloatArray(96)
        m.mixBlock(l2, r2, 96)
        assertTrue(l2[0] > 0f, "release should fade, not instantly zero")
        assertEquals(0f, l2[95], 1e-4f)
        assertEquals(0, m.activeCount, "silent voice removed")
    }
```

(Add `import` for `AmpEnvelope` is same package — none needed.)

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.VoiceMixerTest"`
Expected: FAIL — `envelope` param + `releaseAll` unresolved.

- [ ] **Step 3: Implement**

Update `MixVoice` to carry an envelope (default a no-attack/no-release passthrough so M1 tests still hold):

```kotlin
class MixVoice(
    val source: VoiceSource,
    var gain: Float = 1f,
    delayFrames: Int = 0,
    val envelope: AmpEnvelope = AmpEnvelope(48000, attackMs = 0.0, releaseMs = 0.0),
) {
    var remainingDelay: Int = delayFrames.coerceAtLeast(0)
    /** Running peak of the last block, for quietest-voice stealing. */
    var lastPeak: Float = 0f
}
```

In `mixBlock`, apply the envelope to each rendered sub-block before summing, track `lastPeak`, and change removal:

```kotlin
// inside the per-voice while(i<count) loop, after render into scratch (n samples):
env0@ run {
    v.envelope.applyInPlace(scratch, n)     // scratch now enveloped
}
var peak = v.lastPeak
for (j in 0 until n) {
    val s = scratch[j] * v.gain
    if (kotlin.math.abs(s) > peak) peak = kotlin.math.abs(s)
    outL[i + j] += s; outR[i + j] += s
}
v.lastPeak = peak
// ...
// removal condition (replace the old one):
if (v.envelope.isSilent || (v.source.isFinished && v.remainingDelay == 0)) it.remove()
```

Add:

```kotlin
@Synchronized fun releaseAll() { for (v in voices) v.envelope.release() }

@Synchronized fun capVoices(max: Int) {
    while (voices.size > max) {
        // Release the quietest (lowest recent peak) rather than hard-dropping.
        val quietest = voices.minByOrNull { it.lastPeak } ?: break
        quietest.envelope.release()
        // If still over cap because releases haven't finished, hard-remove the quietest
        // fully-released one; otherwise break to avoid dropping audible voices.
        val doneIdx = voices.indexOfFirst { it.envelope.isSilent }
        if (doneIdx >= 0) voices.removeAt(doneIdx) else break
    }
}
```

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.VoiceMixerTest"`
Expected: PASS (M1 tests still green — default envelope is passthrough).

- [ ] **Step 5: Use envelope + release in the engine**

In `AudioTrackEngine.kt`: build voices with a real envelope from `Timbre.releaseMs` (Timbre gains the field in Task 12; until then hardcode 3.0/20.0), and make `stop()` release + let the loop drain:

```kotlin
private fun addVoice(samples: FloatArray, gain: Float = 1f, delayFrames: Int = 0,
                     attackMs: Double = 3.0, releaseMs: Double = 20.0) {
    if (samples.isEmpty()) return
    mixer.add(MixVoice(BufferSource(samples), gain, delayFrames,
        AmpEnvelope(sampleRate, attackMs, releaseMs)))
    mixer.capVoices(MAX_VOICES)
}

override fun stop() { mixer.releaseAll() }   // fade out; voices self-remove when silent
```

The idle-park `activeCount == 0` check now naturally waits for releases to finish (a releasing voice is still active), so tails aren't cut.

- [ ] **Step 6: Build + test + manual**

Run: `.\gradlew.bat :audio:test` then `.\gradlew.bat :app:assembleDebug`. Manual: tap Stop mid-chord — expect a smooth fade, no click/pop.

- [ ] **Step 7: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt audio/src/test/kotlin/app/guitar/audio/VoiceMixerTest.kt
git commit -m "feat(audio): per-voice envelope + release-based stop/steal (M2)"
```

---

## MILESTONE M3 — Stereo bus + per-voice pan

Goal: stereo output; each voice panned (constant-power); chord path becomes per-note voices with a subtle spread.

### Task 6: `Panner` (pan law + pitch→pan)

**Files:**
- Create: `audio/src/main/kotlin/app/guitar/audio/Panner.kt`
- Test: `audio/src/test/kotlin/app/guitar/audio/PannerTest.kt`

**Interfaces:**
- Produces: `object Panner { fun gains(pan: Double): Pair<Float, Float>; fun forMidi(midi: Int, spread: Double = 0.3): Double }`. `gains` returns `(left, right)` with `l²+r²≈1`. `forMidi` maps MIDI 40..88 linearly to `[-spread, +spread]`, clamped.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.guitar.audio

import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PannerTest {
    @Test
    fun `constant power - center is equal and ~0point707`() {
        val (l, r) = Panner.gains(0.0)
        assertEquals(0.7071f, l, 1e-3f)
        assertEquals(0.7071f, r, 1e-3f)
    }

    @Test
    fun `power is preserved across the sweep`() {
        for (p in listOf(-1.0, -0.5, 0.0, 0.5, 1.0)) {
            val (l, r) = Panner.gains(p)
            assertEquals(1f, l * l + r * r, 1e-4f, "power at pan=$p")
        }
    }

    @Test
    fun `hard left and right`() {
        val (ll, lr) = Panner.gains(-1.0); assertTrue(ll > 0.99f && lr < 0.01f)
        val (rl, rr) = Panner.gains(1.0);  assertTrue(rr > 0.99f && rl < 0.01f)
    }

    @Test
    fun `pitch maps low-to-left, high-to-right, clamped`() {
        assertTrue(Panner.forMidi(40) < 0.0)
        assertTrue(Panner.forMidi(88) > 0.0)
        assertTrue(Panner.forMidi(200) <= 0.3 + 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.PannerTest"`
Expected: FAIL — `Panner` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.guitar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Constant-power stereo panning. pan ∈ [-1, 1]; -1 = hard left, 0 = center, 1 = right. */
object Panner {
    fun gains(pan: Double): Pair<Float, Float> {
        val p = pan.coerceIn(-1.0, 1.0)
        val theta = (p + 1.0) * (PI / 4.0)     // 0..π/2
        return cos(theta).toFloat() to sin(theta).toFloat()
    }

    /** Subtle pan by pitch: MIDI 40..88 → [-spread, +spread], clamped. */
    fun forMidi(midi: Int, spread: Double = 0.3): Double {
        val t = ((midi - 40).toDouble() / (88 - 40)).coerceIn(0.0, 1.0)  // 0..1
        return ((t * 2.0 - 1.0) * spread).coerceIn(-spread, spread)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.PannerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/Panner.kt audio/src/test/kotlin/app/guitar/audio/PannerTest.kt
git commit -m "feat(audio): constant-power Panner + pitch->pan (M3)"
```

### Task 7: Apply pan in `VoiceMixer` + stereo `AudioTrack`

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt`
- Modify: `audio/src/test/kotlin/app/guitar/audio/VoiceMixerTest.kt`
- Modify: `audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt`

**Interfaces:**
- Consumes: `Panner` (Task 6).
- Produces: `MixVoice` gains `var pan: Double = 0.0`. `mixBlock` applies `Panner.gains(pan)` to split into L/R. `AudioTrackEngine` uses `CHANNEL_OUT_STEREO` and interleaves L/R.

- [ ] **Step 1: Write the failing test (pan splits L/R)**

```kotlin
    @Test
    fun `hard-left pan routes signal to L only`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(floatArrayOf(0.8f)), pan = -1.0))
        val l = FloatArray(1); val r = FloatArray(1)
        m.mixBlock(l, r, 1)
        assertTrue(l[0] > 0.79f && r[0] < 0.01f, "L=${l[0]} R=${r[0]}")
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.VoiceMixerTest"`
Expected: FAIL — `pan` param unresolved.

- [ ] **Step 3: Implement pan in `MixVoice`/`mixBlock`**

Add `var pan: Double = 0.0` to `MixVoice`. In `mixBlock`, compute `val (gL, gR) = Panner.gains(v.pan)` once per voice per block, then:

```kotlin
for (j in 0 until n) {
    val s = scratch[j] * v.gain
    val a = kotlin.math.abs(s); if (a > peak) peak = a
    outL[i + j] += s * gL
    outR[i + j] += s * gR
}
```

Note: this changes the M1 "L == R" test. **Update** that earlier test's default expectation: a default `MixVoice` has `pan = 0.0` → `gL = gR = 0.7071`, so L == R still holds but scaled by 0.7071. Adjust `sums two voices` expected values to `0.3f * 0.7071f` etc., or set those test voices to `pan` via a center helper. Simplest: multiply expected by `0.70710678f` and keep `assertEquals(l, r)`.

- [ ] **Step 4: Stereo `AudioTrack` + interleave**

In `AudioTrackEngine.kt`: change channel mask to `CHANNEL_OUT_STEREO`; recompute `getMinBufferSize(..., CHANNEL_OUT_STEREO, ...)`. Output loop writes interleaved:

```kotlin
val chunk = ShortArray(chunkFrames * 2)
// after mixer.mixBlock(l, r, chunkFrames):
for (i in 0 until chunkFrames) {
    val sl = l[i].coerceIn(-1f, 1f); val sr = r[i].coerceIn(-1f, 1f)
    chunk[2 * i]     = (sl * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    chunk[2 * i + 1] = (sr * 32767f).toInt().coerceIn(-32768, 32767).toShort()
}
track.write(chunk, 0, chunkFrames * 2, AudioTrack.WRITE_BLOCKING)
```

- [ ] **Step 5: Assign pan when creating voices**

`playNote`/`playFrequency`: `pan = Panner.forMidi(midiNote)` (for `playFrequency`, pan = 0.0). `playSamples`/drums: `pan = 0.0`. Chord handled in Task 8.

- [ ] **Step 6: Build + test + manual (headphones)**

Run: `.\gradlew.bat :audio:test` then `assembleDebug`. Manual on headphones: single high vs low notes lean slightly right vs left.

- [ ] **Step 7: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt audio/src/test/kotlin/app/guitar/audio/VoiceMixerTest.kt
git commit -m "feat(audio): stereo bus + per-voice constant-power pan (M3)"
```

### Task 8: Chord path → per-note voices with spread

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt`

**Interfaces:**
- Consumes: `PluckedSynth.synthesize` (single note), `Panner.forMidi`.
- Produces: `playChord` adds one `MixVoice` per note (pan per note, strum via `delayFrames`, gain `1/√N`), replacing `synth.synthesizeChord`.

- [ ] **Step 1: Rewrite `playChord`**

```kotlin
override fun playChord(midiNotes: List<Int>, strumDelayMillis: Int, sustainMillis: Int, timbre: Timbre) {
    if (midiNotes.isEmpty() || sustainMillis <= 0 || !running.get()) return
    val notes = midiNotes.filter { it in 0..127 }
    if (notes.isEmpty()) return
    val strumFrames = (sampleRate * strumDelayMillis / 1000).coerceAtLeast(0)
    val gain = (1.0 / kotlin.math.sqrt(notes.size.toDouble())).toFloat()
    synthesizer.execute {
        notes.forEachIndexed { i, midi ->
            val samples = synth.synthesize(midi, sustainMillis / 1000.0, System.nanoTime() + i,
                timbre.damping, timbre.amplitude)
            mixer.add(MixVoice(BufferSource(samples), gain, strumFrames * i,
                AmpEnvelope(sampleRate, 3.0, timbreReleaseMs(timbre))).also { it.pan = Panner.forMidi(midi) })
        }
        mixer.capVoices(MAX_VOICES)
    }
}
```

(Use `timbreReleaseMs(timbre)` = `timbre.releaseMs.toDouble()` after Task 12; until then `20.0`.)

- [ ] **Step 2: Delete `PluckedSynth.synthesizeChord` + its callers/tests**

Remove `synthesizeChord` from `PluckedSynth.kt` and its test(s) in `PluckedSynthTest.kt` (search `synthesizeChord`). The chord is now composed in the engine.

- [ ] **Step 3: Build + test + manual**

Run: `.\gradlew.bat :audio:test` then `assembleDebug`. Manual: a strummed chord spreads across the stereo field; each string rings independently.

- [ ] **Step 4: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt audio/src/main/kotlin/app/guitar/audio/PluckedSynth.kt audio/src/test/kotlin/app/guitar/audio/PluckedSynthTest.kt
git commit -m "feat(audio): chord = per-note voices with stereo spread (M3)"
```

---

## MILESTONE M4 — Soft limiter (fix hard-clip distortion)

### Task 9: `SoftLimiter`

**Files:**
- Create: `audio/src/main/kotlin/app/guitar/audio/SoftLimiter.kt`
- Test: `audio/src/test/kotlin/app/guitar/audio/SoftLimiterTest.kt`

**Interfaces:**
- Produces: `class SoftLimiter(sampleRate: Int, ceiling: Float = 0.944f, releaseMs: Double = 80.0)` with `fun process(l: FloatArray, r: FloatArray, count: Int)`. Guarantees `|out| ≤ ceiling`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.guitar.audio

import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SoftLimiterTest {
    @Test
    fun `output never exceeds ceiling for a hot signal`() {
        val lim = SoftLimiter(sampleRate = 48000, ceiling = 0.944f)
        val l = FloatArray(1000) { 3f }        // way over
        val r = FloatArray(1000) { -3f }
        lim.process(l, r, 1000)
        for (i in l.indices) {
            assertTrue(l[i] in -0.9441f..0.9441f, "L[$i]=${l[i]}")
            assertTrue(r[i] in -0.9441f..0.9441f, "R[$i]=${r[i]}")
            assertTrue(!l[i].isNaN() && !r[i].isNaN())
        }
    }

    @Test
    fun `signal below ceiling passes essentially unchanged`() {
        val lim = SoftLimiter(sampleRate = 48000, ceiling = 0.944f)
        val l = FloatArray(500) { 0.2f }; val r = FloatArray(500) { 0.2f }
        lim.process(l, r, 500)
        assertTrue(l[499] in 0.19f..0.201f, "was ${l[499]}")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.SoftLimiterTest"`
Expected: FAIL — `SoftLimiter` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.guitar.audio

import kotlin.math.abs
import kotlin.math.exp

/** Peak-following soft limiter on the stereo bus. Tracks the max |sample| across L/R
 *  and pulls a smoothed gain down so the output never exceeds [ceiling]. A short
 *  release lets the gain recover after a transient. Guarantees |out| <= ceiling by a
 *  final safety clamp. Pure — no Android API. */
class SoftLimiter(
    sampleRate: Int,
    private val ceiling: Float = 0.944f,   // -0.5 dBFS
    releaseMs: Double = 80.0,
) {
    private var gain = 1f
    private val releaseCoef = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()

    fun process(l: FloatArray, r: FloatArray, count: Int) {
        for (i in 0 until count) {
            val peak = maxOf(abs(l[i]), abs(r[i]))
            // Target gain that would bring this peak to the ceiling (<=1).
            val target = if (peak > ceiling) ceiling / peak else 1f
            // Attack instantly (clamp down now), release slowly (recover).
            gain = if (target < gain) target else gain * releaseCoef + target * (1 - releaseCoef)
            var sl = l[i] * gain
            var sr = r[i] * gain
            // Safety clamp (guarantees the invariant even during release lag).
            if (sl > ceiling) sl = ceiling else if (sl < -ceiling) sl = -ceiling
            if (sr > ceiling) sr = ceiling else if (sr < -ceiling) sr = -ceiling
            l[i] = sl; r[i] = sr
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.SoftLimiterTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/SoftLimiter.kt audio/src/test/kotlin/app/guitar/audio/SoftLimiterTest.kt
git commit -m "feat(audio): SoftLimiter peak-following bus limiter (M4)"
```

### Task 10: Insert `SoftLimiter` on the mixer bus + drop hard clip

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt`
- Modify: `audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt`

**Interfaces:**
- Consumes: `SoftLimiter` (Task 9).
- Produces: `VoiceMixer` owns a `SoftLimiter` and applies it at the end of `mixBlock`. `AudioTrackEngine` no longer soft-clamps before quantizing (limiter guarantees bound).

- [ ] **Step 1: Add limiter to `VoiceMixer.mixBlock`**

```kotlin
private val limiter = SoftLimiter(sampleRate)
// at the END of mixBlock, after summing all voices:
limiter.process(outL, outR, count)
```

- [ ] **Step 2: Simplify the engine quantize (limiter already bounded to ceiling)**

In `AudioTrackEngine` output loop, drop the `coerceIn(-1f,1f)` guard — keep only the short conversion (still `coerceIn(-32768,32767)` for integer safety).

- [ ] **Step 3: Build + test + manual (the key payoff check)**

Run: `.\gradlew.bat :audio:test` then `assembleDebug`. Manual: play a **dense chord + loud drum loop simultaneously** — previously harsh/crackly on peaks; now smooth, no clip-distortion.

- [ ] **Step 4: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt
git commit -m "feat(audio): soft-limit the mixer bus, remove hard clip (M4)"
```

---

## MILESTONE M5 — Reverb send (roomy / subtle bloom)

### Task 11: `Freeverb`

**Files:**
- Create: `audio/src/main/kotlin/app/guitar/audio/Freeverb.kt`
- Test: `audio/src/test/kotlin/app/guitar/audio/FreeverbTest.kt`

**Interfaces:**
- Produces: `class Freeverb(sampleRate: Int, roomSize: Float = 0.5f, damp: Float = 0.5f, wet: Float = 0.18f)` with `fun process(l: FloatArray, r: FloatArray, count: Int)` (adds wet in place: `out = dry + wet·reverb`) and `fun isRingingOut(threshold: Float = 1e-4f): Boolean`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.guitar.audio

import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class FreeverbTest {
    @Test
    fun `impulse produces a decaying tail that rings out`() {
        val rv = Freeverb(sampleRate = 48000, wet = 0.5f)
        val l = FloatArray(48000); val r = FloatArray(48000)
        l[0] = 1f; r[0] = 1f                       // impulse
        rv.process(l, r, 48000)
        // Tail exists shortly after the impulse...
        var energyEarly = 0f; for (i in 100 until 2000) energyEarly += abs(l[i])
        assertTrue(energyEarly > 0f, "expected a reverb tail")
        // ...and has decayed to near-silence by the end.
        var energyLate = 0f; for (i in 40000 until 48000) energyLate += abs(l[i])
        assertTrue(energyLate < energyEarly, "tail should decay")
        // Feed silence a bit more, then it reports rung-out.
        val s = FloatArray(48000); val s2 = FloatArray(48000)
        rv.process(s, s2, 48000)
        assertTrue(rv.isRingingOut(), "should report rung out after long silence")
    }

    @Test
    fun `stays bounded (no runaway feedback)`() {
        val rv = Freeverb(sampleRate = 48000, wet = 0.5f)
        val l = FloatArray(48000) { 0.5f }; val r = FloatArray(48000) { 0.5f }
        rv.process(l, r, 48000)
        for (i in l.indices) assertTrue(abs(l[i]) < 4f && !l[i].isNaN(), "L[$i]=${l[i]}")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.FreeverbTest"`
Expected: FAIL — `Freeverb` unresolved.

- [ ] **Step 3: Write minimal implementation (public-domain Freeverb constants)**

```kotlin
package app.guitar.audio

import kotlin.math.abs

/** Public-domain "Freeverb" algorithmic reverb (Schroeder/Moorer): 8 parallel comb
 *  filters + 4 series allpass per channel, with a stereo spread. Adds `wet`·reverb to
 *  the dry signal in place. Pure — no Android API.
 *
 *  Tunings are the canonical Freeverb values, scaled from the original 44.1 kHz. */
class Freeverb(
    sampleRate: Int,
    roomSize: Float = 0.5f,
    damp: Float = 0.5f,
    private val wet: Float = 0.18f,
) {
    private val combTuning = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allpassTuning = intArrayOf(556, 441, 341, 225)
    private val stereoSpread = 23
    private val feedback = roomSize * 0.28f + 0.7f
    private val damp1 = damp * 0.4f
    private val damp2 = 1f - damp1

    private val scale = sampleRate / 44100f
    private fun s(x: Int) = (x * scale).toInt().coerceAtLeast(1)

    private inner class Comb(size: Int) {
        private val buf = FloatArray(size); private var idx = 0; private var filt = 0f
        fun tick(input: Float): Float {
            val out = buf[idx]
            filt = out * damp2 + filt * damp1
            buf[idx] = input + filt * feedback
            if (++idx >= buf.size) idx = 0
            return out
        }
    }
    private inner class Allpass(size: Int) {
        private val buf = FloatArray(size); private var idx = 0
        fun tick(input: Float): Float {
            val bufout = buf[idx]
            val out = -input + bufout
            buf[idx] = input + bufout * 0.5f
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    private val combL = Array(8) { Comb(s(combTuning[it])) }
    private val combR = Array(8) { Comb(s(combTuning[it] + stereoSpread)) }
    private val apL = Array(4) { Allpass(s(allpassTuning[it])) }
    private val apR = Array(4) { Allpass(s(allpassTuning[it] + stereoSpread)) }

    private var lastTail = 1f

    fun process(l: FloatArray, r: FloatArray, count: Int) {
        var tail = 0f
        for (i in 0 until count) {
            val input = (l[i] + r[i]) * 0.015f      // gain into the reverb
            var wl = 0f; var wr = 0f
            for (c in 0 until 8) { wl += combL[c].tick(input); wr += combR[c].tick(input) }
            for (a in 0 until 4) { wl = apL[a].tick(wl); wr = apR[a].tick(wr) }
            l[i] += wl * wet
            r[i] += wr * wet
            val e = abs(wl) + abs(wr); if (e > tail) tail = e
        }
        lastTail = tail
    }

    fun isRingingOut(threshold: Float = 1e-4f): Boolean = lastTail < threshold
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.FreeverbTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/Freeverb.kt audio/src/test/kotlin/app/guitar/audio/FreeverbTest.kt
git commit -m "feat(audio): Freeverb algorithmic stereo reverb (M5)"
```

### Task 12: Wire reverb send into the mixer + `Timbre` fields + corrected idle-park

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/Timbre.kt`
- Modify: `audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt`
- Modify: `audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt`

**Interfaces:**
- Consumes: `Freeverb` (Task 11).
- Produces: `Timbre` gains `pan`, `reverbSend`, `releaseMs`. `MixVoice` gains `var reverbSend: Float`. `VoiceMixer` accumulates a wet-send bus, runs `Freeverb` on it, sums into L/R before the limiter, and exposes `isRingingOut()`.

- [ ] **Step 1: Extend `Timbre`**

```kotlin
data class Timbre(
    val damping: Double = 0.997,
    val amplitude: Double = 0.6,
    val pan: Double = 0.0,
    val reverbSend: Double = 0.18,
    val releaseMs: Int = 20,
) {
    companion object {
        val Guitar = Timbre(damping = 0.997, amplitude = 0.6)
        val Cavaquinho = Timbre(damping = 0.989, amplitude = 0.55, reverbSend = 0.12)
        val Clarity = Timbre(damping = 0.997, amplitude = 0.62, reverbSend = 0.15)
    }
}
```

- [ ] **Step 2: Reverb send in `VoiceMixer`**

Add `var reverbSend: Float = 0f` to `MixVoice`. Keep a `sendL/sendR` scratch accumulator sized like the block; per voice add `s*gL*reverbSend` / `s*gR*reverbSend` to the send bus; after all voices: `freeverb.process(sendL, sendR, count)` then add `sendL/sendR` into `outL/outR`, THEN `limiter.process`. Add `fun isRingingOut() = freeverb.isRingingOut() && voices.isEmpty()`.

```kotlin
private val freeverb = Freeverb(sampleRate)
private val sendL = FloatArray(4096)
private val sendR = FloatArray(4096)
// in mixBlock: zero sendL/sendR[0until count]; per sample:
//   sendL[i+j] += s*gL*v.reverbSend ; sendR[i+j] += s*gR*v.reverbSend
// after voice loop:
freeverb.process(sendL, sendR, count)
for (i in 0 until count) { outL[i] += sendL[i]; outR[i] += sendR[i] }
limiter.process(outL, outR, count)
```

- [ ] **Step 3: Corrected idle-park in the engine**

Replace `if (mixer.activeCount == 0)` park check with `if (mixer.activeCount == 0 && mixer.isRingingOut())` so the reverb tail is never truncated. (Because the tail must keep flowing, when `activeCount==0` but not rung out, still call `mixBlock` — it will process the decaying send bus.)

- [ ] **Step 4: Pass `reverbSend`/pan/release from `Timbre` when adding voices**

`playNote`/`playChord`: set `MixVoice.reverbSend = timbre.reverbSend.toFloat()`, envelope `releaseMs = timbre.releaseMs.toDouble()`. `playSamples` (drums): `reverbSend = 0f`.

- [ ] **Step 5: Build + test + manual**

Run: `.\gradlew.bat :audio:test` then `assembleDebug`. Manual: a held chord now "blooms" with a subtle tail; drums stay dry/punchy; after Stop the tail rings out and doesn't cut.

- [ ] **Step 6: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/Timbre.kt audio/src/main/kotlin/app/guitar/audio/VoiceMixer.kt audio/src/main/kotlin/app/guitar/audio/AudioTrackEngine.kt
git commit -m "feat(audio): reverb send + Timbre pan/reverbSend/release + tail-aware idle (M5)"
```

---

## MILESTONE M6 — Karplus-Strong decay improvement + finalize

### Task 13: Dual-rate damping in `PluckedSynth`

**Files:**
- Modify: `audio/src/main/kotlin/app/guitar/audio/PluckedSynth.kt`
- Modify: `audio/src/test/kotlin/app/guitar/audio/PluckedSynthTest.kt`

**Interfaces:**
- Produces: `synthesize`/`synthesizeFrequency` gain an optional `brightnessDecay: Double = 1.0` (1.0 = current behavior) that damps high harmonics faster than the fundamental by adding a per-loop extra lowpass whose strength decays over the note. Existing bounded/deterministic tests must still pass.

- [ ] **Step 1: Write the failing test (spectral centroid falls over the note)**

```kotlin
    @Test
    fun `high harmonics decay faster than the fundamental (brighter attack, warmer tail)`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val out = synth.synthesize(midiNote = 52, durationSec = 1.5, brightnessDecay = 0.6)
        // Zero-crossing rate is a cheap brightness proxy: compare first 100ms vs last 100ms.
        fun zcr(from: Int, to: Int): Int { var z = 0; for (i in from + 1 until to) if ((out[i] >= 0) != (out[i-1] >= 0)) z++; return z }
        val early = zcr(0, 4800)
        val late = zcr(out.size - 4800, out.size)
        assertTrue(late < early, "tail ($late) should be less bright than attack ($early)")
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `.\gradlew.bat :audio:test --tests "app.guitar.audio.PluckedSynthTest"`
Expected: FAIL — `brightnessDecay` param unresolved.

- [ ] **Step 3: Implement dual-rate damping**

Add `brightnessDecay: Double = 1.0` to both `synthesize` and `synthesizeFrequency`. In the KS loop, apply an extra one-pole lowpass whose coefficient starts letting highs through and increases smoothing over the note (so highs die faster). Keep amplitude bounds (the extra filter is a convex operation). Minimal change in the delay loop:

```kotlin
// before the loop:
var lp = 0.0
val bright = brightnessDecay.coerceIn(0.0, 1.0)
// per sample i, replace `ks[i] = cur` region with:
val cur = buf[idx]
val nxt = buf[(idx + 1) % n]
// progress 0..1 across the note; more smoothing toward the end
val prog = i.toDouble() / numSamples
val extra = bright + (1.0 - bright) * (1.0 - prog)   // 1.0 → less smoothing early, more late when bright<1
lp = extra * cur + (1.0 - extra) * lp
ks[i] = lp
buf[idx] = damping * 0.5 * (cur + nxt)
idx = (idx + 1) % n
```

Default `brightnessDecay = 1.0` ⇒ `extra = 1.0` ⇒ `lp = cur` ⇒ identical to today (existing tests hold). Wire `Timbre` → pass `brightnessDecay` (add field default 0.6 to `Guitar`/`Clarity` if desired, or keep synth-level default and pass a constant from the engine).

- [ ] **Step 4: Run + full suite**

Run: `.\gradlew.bat :audio:test`
Expected: PASS (new + existing, incl. bounded/deterministic).

- [ ] **Step 5: Commit**

```bash
git add audio/src/main/kotlin/app/guitar/audio/PluckedSynth.kt audio/src/test/kotlin/app/guitar/audio/PluckedSynthTest.kt
git commit -m "feat(audio): dual-rate KS damping for natural decay (M6)"
```

---

## WEB MIRROR (parity — after the Android milestones land)

The web engine mirrors the *topology* using WebAudio nodes. Do these after the Android side is stable; each maps to a milestone.

### Task 14: `panner.ts` + verify parity (mirrors Task 6)

**Files:**
- Create: `chorect-web/src/audio/panner.ts`
- Modify: `chorect-web/test/verify.ts`
- Modify: `chorect-web/src/audio/index.ts` (export panner)

**Interfaces:**
- Produces: `export function panGains(pan: number): [number, number]` and `export function panForMidi(midi: number, spread = 0.3): number` — identical math to Kotlin `Panner`.

- [ ] **Step 1: Implement `panner.ts`**

```typescript
/** Constant-power stereo pan. pan ∈ [-1,1]. Mirrors Kotlin Panner. */
export function panGains(pan: number): [number, number] {
  const p = Math.max(-1, Math.min(1, pan));
  const theta = (p + 1) * (Math.PI / 4);
  return [Math.cos(theta), Math.sin(theta)];
}

export function panForMidi(midi: number, spread = 0.3): number {
  const t = Math.max(0, Math.min(1, (midi - 40) / (88 - 40)));
  return Math.max(-spread, Math.min(spread, (t * 2 - 1) * spread));
}
```

- [ ] **Step 2: Export + add verify checks**

In `chorect-web/src/audio/index.ts` add `export * from "./panner";`. In `test/verify.ts`, import `panGains` from `../src/audio` and add:

```typescript
const [cl, cr] = panGains(0);
check("panGains center ~0.707", Math.abs(cl - 0.7071) < 1e-3 && Math.abs(cr - 0.7071) < 1e-3);
check("panGains constant power", Math.abs(cl*cl + cr*cr - 1) < 1e-4);
const [hl, hr] = panGains(-1);
check("panGains hard left", hl > 0.99 && hr < 0.01);
```

- [ ] **Step 3: Commit (CI verifies)**

```bash
git add chorect-web/src/audio/panner.ts chorect-web/src/audio/index.ts chorect-web/test/verify.ts
git commit -m "feat(ear-web): panGains/panForMidi parity + verify checks (M3 web)"
```

### Task 15: `reverbIR.ts` + stereo voice graph in `engine.ts` (mirrors M2–M5)

**Files:**
- Create: `chorect-web/src/audio/reverbIR.ts`
- Modify: `chorect-web/src/audio/engine.ts`
- Modify: `chorect-web/src/audio/timbre.ts`

**Interfaces:**
- Consumes: `panForMidi`, `panGains` (Task 14).
- Produces: `export function buildReverbIR(ctx: BaseAudioContext, seconds?: number): AudioBuffer`. `WebAudioEngine` builds `master → limiter(DynamicsCompressor) → destination`, a `convolver` fed a generated IR on a `reverbBus → convolver → master`, and each voice routes `bufferSource → envGain → panner → {master, reverbSend→reverbBus}`.

- [ ] **Step 1: `reverbIR.ts`**

```typescript
/** Synthetic exponentially-decaying stereo noise IR for a subtle "room" — mirrors
 *  the Freeverb sound without bundling an asset. */
export function buildReverbIR(ctx: BaseAudioContext, seconds = 1.3): AudioBuffer {
  const rate = ctx.sampleRate;
  const len = Math.floor(rate * seconds);
  const ir = ctx.createBuffer(2, len, rate);
  for (let ch = 0; ch < 2; ch++) {
    const d = ir.getChannelData(ch);
    for (let i = 0; i < len; i++) {
      const decay = Math.pow(1 - i / len, 2.5);
      d[i] = (Math.random() * 2 - 1) * decay;
    }
  }
  return ir;
}
```

- [ ] **Step 2: Extend `timbre.ts`**

```typescript
export interface Timbre { damping: number; amplitude: number; pan: number; reverbSend: number; releaseMs: number; }
export const Timbres = {
  Guitar: { damping: 0.997, amplitude: 0.6, pan: 0, reverbSend: 0.18, releaseMs: 20 },
  Cavaquinho: { damping: 0.989, amplitude: 0.55, pan: 0, reverbSend: 0.12, releaseMs: 20 },
  Clarity: { damping: 0.997, amplitude: 0.62, pan: 0, reverbSend: 0.15, releaseMs: 20 },
} as const;
```

(Update existing `Timbres` references if the object shape changed — search `Timbres.`.)

- [ ] **Step 3: Rebuild the graph in `engine.ts`**

In `ensure()`, after creating `master`, insert a limiter and reverb bus:

```typescript
this.limiter = this.ctx.createDynamicsCompressor();
this.limiter.threshold.value = -1; this.limiter.knee.value = 0;
this.limiter.ratio.value = 20; this.limiter.attack.value = 0.003; this.limiter.release.value = 0.08;
this.master.connect(this.limiter); this.limiter.connect(this.ctx.destination);
this.reverb = this.ctx.createConvolver(); this.reverb.buffer = buildReverbIR(this.ctx);
this.reverbBus = this.ctx.createGain(); this.reverbBus.gain.value = 1;
this.reverbBus.connect(this.reverb); this.reverb.connect(this.master);
```

Replace `play(samples)` / `playSamples` with a shared voice builder that takes `pan`, `reverbSend`, `releaseMs`, wiring `src → envGain → panner → master` and `panner → reverbSend(Gain) → reverbBus`. Attack via `envGain.gain.setValueAtTime(0,...).linearRampToValueAtTime(1, t+0.003)`. `playChord` fires one voice per note (pan = `panForMidi(midi)`, strum offset via `when`, gain `1/√N`) instead of `synthesizeChord`. `stop()` ramps each voice's `envGain` to 0 over `releaseMs` then `src.stop()`. Drums call the builder with `pan=0, reverbSend=0`.

- [ ] **Step 4: Typecheck locally if possible, else rely on CI**

Run (if Node available): `cd chorect-web && npm run build`. Otherwise the CI `tsc --noEmit` + `vite build` gate it on dispatch.

- [ ] **Step 5: Commit**

```bash
git add chorect-web/src/audio/reverbIR.ts chorect-web/src/audio/engine.ts chorect-web/src/audio/timbre.ts
git commit -m "feat(ear-web): stereo voice graph — env, pan, reverb send, limiter (M2-M5 web)"
```

### Task 16: `pluckedSynth.ts` dual-rate damping (mirrors Task 13)

**Files:**
- Modify: `chorect-web/src/audio/pluckedSynth.ts`
- Modify: `chorect-web/test/verify.ts`

- [ ] **Step 1: Mirror `brightnessDecay`** in `synthesize`/`synthesizeFrequency` exactly as the Kotlin change (default 1.0 = unchanged). Add a `check()` in verify.ts asserting the tail zero-cross rate < attack zero-cross rate for `brightnessDecay = 0.6`.

- [ ] **Step 2: Commit**

```bash
git add chorect-web/src/audio/pluckedSynth.ts chorect-web/test/verify.ts
git commit -m "feat(ear-web): dual-rate KS damping parity (M6 web)"
```

---

## SHIP (after M6 + web mirror)

### Task 17: Version bump, build, verify, release

- [ ] **Step 1: Bump versions**

`app/build.gradle.kts`: `versionCode = 12200`, `versionName = "1.22.0"`. `chorect-web/package.json`: `"1.22.0"`.

- [ ] **Step 2: Archive prior APK + build**

```bash
cp app/build/outputs/apk/debug/Chorect_beta_V1.21.0.apk releases/ 2>/dev/null || true
```
Run: `.\gradlew.bat :audio:test :theory:test :app:assembleDebug`
Expected: BUILD SUCCESSFUL; `Chorect_beta_V1.22.0.apk` present.

- [ ] **Step 3: Commit + push + dispatch web CI**

```bash
git add -A && git commit -m "chore: release v1.22.0 (audio engine overhaul)"
git push origin main
gh workflow run "Deploy web to GitHub Pages" --ref main
```
Watch: `gh run watch <id> --exit-status` — expect `tsc`/`vite`/deploy green.

- [ ] **Step 4: Update project-state memory** with the v1.22.0 audio-engine-overhaul summary and mark Step 2 (sampled instruments) as the next spec.

---

## Self-Review

**Spec coverage:**
- Root causes (hard clip, fixed buffers, uniform damping, mono/no-bus) → M4 (limiter), M2 (envelope/release), M6 (dual-rate), M3/M5 (stereo+pan, reverb). ✓
- `VoiceSource`/`BufferSource` → Task 1. ✓ Headless `VoiceMixer` → Tasks 2,5,7,10,12. ✓
- Android pull-model mixer rewrite → Tasks 3,7,10,12. ✓
- Web idiomatic mapping (env/pan/reverb/limiter, sonic parity) → Tasks 14–16. ✓
- Pure DSP: pan law (Task 6/14), envelope (Task 4), soft limiter (Task 9), Freeverb (Task 11). ✓
- API unchanged + `Timbre` extension → Tasks 3,8,12 (Kotlin), 15 (web). ✓
- Improved chord/strum path → Task 8 (Kotlin), 15 (web). ✓ Drums preserved + dry send → Tasks 3,12,15. ✓
- Test plan (pan power, envelope declick, limiter ceiling, reverb decay/stability, voice-steal, mixer integration) → Tasks 4,6,9,11 + VoiceMixerTest. ✓
- Milestones M1–M6 + ship v1.22.0 → Tasks 1–17. ✓

**Placeholder scan:** No TBD/TODO; each code step carries full code. Integration steps that edit large existing methods give the exact replacement blocks and search anchors.

**Type consistency:** `VoiceSource.render(out,count):Int`, `MixVoice(source,gain,delayFrames,envelope)` + `.pan`/`.reverbSend`/`.lastPeak`, `VoiceMixer.mixBlock(outL,outR,count)`/`add`/`releaseAll`/`capVoices`/`clear`/`isRingingOut`/`activeCount`, `AmpEnvelope(sampleRate,attackMs,releaseMs).applyInPlace/release/isSilent`, `Panner.gains/forMidi`, `SoftLimiter.process`, `Freeverb.process/isRingingOut`, `Timbre(pan,reverbSend,releaseMs)` — consistent across tasks and between Kotlin and TS mirrors.

**Note on M1 test adjustment:** Task 7 explicitly updates the Task 2 "L == R" expectations to account for the center-pan 0.7071 factor — called out so it isn't a surprise regression.

package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Offline render (the WAV export) — asserted with a one-sample IMPULSE per voice, so a
 * hit is exactly one non-zero sample and every assertion is about position and gain
 * rather than "does it sound about right".
 */
class PercussionRenderTest {

    private val sr = 44_100
    private val amp = 0.5f   // below the 0.999 ceiling even after a 1.4× accent

    /** Every voice is a single-sample impulse → out[i] != 0 iff a hit starts at i. */
    private val impulses = PercussionRender.VoiceBuffers { _, _ -> floatArrayOf(amp) }

    private fun patternOf(row: String, id: String = "surdo"): PercussionPattern =
        requireNotNull(PercussionPattern.decode("M:2,2,4,16;$id=$row"))

    /** Sample indices carrying a hit. */
    private fun onsets(out: FloatArray): List<Int> = out.indices.filter { out[it] != 0f }

    // ---- length ----

    @Test fun `one cycle is exactly its musical length`() {
        // 2 bars of 2/4 = 4 quarter-note beats. At 90 bpm that is 4 × (60/90) s.
        val p = patternOf("0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-")
        val r = PercussionRender.render(PercussionRender.Spec(p, bpm = 90, sampleRate = sr), impulses)
        assertEquals(Math.round(4 * (60.0 / 90) * sr).toInt(), r.samples.size)
        assertEquals(4 * (60.0 / 90), r.durationSec, 1e-6)
    }

    @Test fun `cycles multiply the length and repeat the hits`() {
        val p = patternOf("0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-")
        val one = PercussionRender.render(PercussionRender.Spec(p, bpm = 90, sampleRate = sr), impulses)
        val four = PercussionRender.render(PercussionRender.Spec(p, bpm = 90, cycles = 4, sampleRate = sr), impulses)
        assertEquals(one.samples.size * 4, four.samples.size)
        assertEquals(listOf(0, one.samples.size, one.samples.size * 2, one.samples.size * 3), onsets(four.samples))
        assertEquals(4, four.hits)
    }

    // ---- placement ----

    @Test fun `hits land on the sixteenth-note grid`() {
        // The default groove's surdo row — the one the CLI export renders.
        val p = patternOf("1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2")
        val r = PercussionRender.render(PercussionRender.Spec(p, bpm = 90, sampleRate = sr), impulses)
        val slotSec = 60.0 / 90 / 4
        val expected = listOf(0, 3, 4, 7, 8, 11, 12, 15).map { Math.round(it * slotSec * sr).toInt() }
        assertEquals(expected, onsets(r.samples))
        assertEquals(8, r.hits)
    }

    @Test fun `onlyTrack renders that track alone`() {
        val p = requireNotNull(PercussionPattern.decode(
            "M:2,2,4,16;surdo=0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-|tamborim=-,0,-,-,-,-,-,-,-,-,-,-,-,-,-,-",
        ))
        val full = PercussionRender.render(PercussionRender.Spec(p, bpm = 90, sampleRate = sr), impulses)
        val solo = PercussionRender.render(PercussionRender.Spec(p, bpm = 90, onlyTrack = "surdo", sampleRate = sr), impulses)
        assertEquals(2, full.hits)
        assertEquals(1, solo.hits)
        assertEquals(listOf(0), onsets(solo.samples))
    }

    @Test fun `includeTrack applies to the full kit but never to a single-track export`() {
        val p = requireNotNull(PercussionPattern.decode(
            "M:2,2,4,16;surdo=0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-|tamborim=-,0,-,-,-,-,-,-,-,-,-,-,-,-,-,-",
        ))
        val audible = { id: String -> id != "surdo" }   // surdo muted in the mixer
        val mix = PercussionRender.render(
            PercussionRender.Spec(p, bpm = 90, includeTrack = audible, sampleRate = sr), impulses,
        )
        assertEquals(1, mix.hits, "a muted track must not reach the full-kit render")
        // ...but asking for that exact stem still renders it.
        val stem = PercussionRender.render(
            PercussionRender.Spec(p, bpm = 90, onlyTrack = "surdo", includeTrack = audible, sampleRate = sr), impulses,
        )
        assertEquals(1, stem.hits)
        assertEquals(listOf(0), onsets(stem.samples))
    }

    // ---- gain ----

    @Test fun `accent dynamics and track volume scale the hit`() {
        val slot0 = { p: PercussionPattern ->
            PercussionRender.render(PercussionRender.Spec(p, bpm = 90, sampleRate = sr), impulses).samples[0]
        }
        val plain = slot0(patternOf("0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-"))
        assertEquals(amp, plain, 1e-6f)
        // raw = voice + 100·accent + 1000·dynLevel
        assertEquals(amp * PercussionRender.ACCENT_GAIN, slot0(patternOf("100,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-")), 1e-6f)
        assertEquals(amp * 0.5f, slot0(patternOf("2000,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-")), 1e-6f)
        val quiet = patternOf("0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-").withTrackVolume("surdo", 50)
        assertEquals(amp * 0.5f, slot0(quiet), 1e-6f)
    }

    @Test fun `a hot mix is scaled down instead of clipping the file`() {
        // Two loud tracks on the same slot sum past full scale.
        val loud = PercussionRender.VoiceBuffers { _, _ -> floatArrayOf(0.8f) }
        val p = requireNotNull(PercussionPattern.decode(
            "M:2,2,4,16;surdo=0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-|tamborim=0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-",
        ))
        val r = PercussionRender.render(PercussionRender.Spec(p, bpm = 90, sampleRate = sr), loud)
        assertEquals(1.6f, r.peak, 1e-5f)
        assertTrue(r.safetyGain < 1f, "expected safety scaling, got ${r.safetyGain}")
        assertTrue(r.samples.all { abs(it) <= 1f }, "render must not exceed full scale")
    }

    // ---- loop seam ----

    @Test fun `loopExact wraps a ring-out onto the start so the file loops`() {
        val p = patternOf("-,-,-,-,-,-,-,-,-,-,-,-,-,-,-,0")   // last slot only
        // A tail far longer than the remaining slot: it must reappear at the file start.
        val long = PercussionRender.VoiceBuffers { _, _ -> FloatArray(sr) { amp } }
        val wrapped = PercussionRender.render(
            PercussionRender.Spec(p, bpm = 90, loopExact = true, sampleRate = sr), long,
        )
        val oneShot = PercussionRender.render(
            PercussionRender.Spec(p, bpm = 90, loopExact = false, sampleRate = sr), long,
        )
        assertEquals(Math.round(4 * (60.0 / 90) * sr).toInt(), wrapped.samples.size)
        assertTrue(oneShot.samples.size > wrapped.samples.size, "one-shot keeps the tail after the loop end")
        assertTrue(wrapped.samples[0] != 0f, "the wrapped tail must sound at the loop start")
        assertEquals(0f, oneShot.samples[0], "a one-shot starts silent — nothing wraps")
    }

    // ---- voices with no sound ----

    @Test fun `a voice with no buffer is reported not silently dropped`() {
        val p = patternOf("0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-")
        val none = PercussionRender.VoiceBuffers { _, _ -> null }
        val r = PercussionRender.render(PercussionRender.Spec(p, bpm = 90, sampleRate = sr), none)
        assertEquals(0, r.hits)
        assertEquals(listOf("surdo:0"), r.missingVoices)
    }

    // ---- exact vs scheduler timing ----

    @Test fun `exact onsets fix the schedulers rounded-millisecond drift`() {
        val m = PercussionMeter.DEFAULT
        // The live grid truncates twice: at 90 bpm a 16th is 166 ms, not 166.667.
        assertEquals(166L, PercussionTiming.slotMs(90, 16))
        assertEquals(166.667, PercussionTiming.slotMsExact(90, 16), 1e-3)
        // Over one cycle that is ~10 ms — enough to drift against a DAW's bar line.
        val rounded = (0 until m.totalSlots).sumOf { PercussionTiming.swungSlotMs(it, 90, 0, m) }
        val exact = m.totalSlots * PercussionTiming.slotMsExact(90, m.division)
        assertEquals(2656L, rounded)
        assertEquals(2666.667, exact, 1e-3)
        // Straight (swing 0) exact onsets are plain multiples of the slot.
        for (k in 0..16) {
            assertEquals(k * PercussionTiming.slotMsExact(90, 16), PercussionTiming.swungOnsetMsExact(k, 90, 0, m), 1e-6)
        }
    }

    @Test fun `exact onsets carry the same swing feel as the live grid`() {
        val m = PercussionMeter.DEFAULT
        val base = PercussionTiming.slotMsExact(90, 16)
        val straight = (0..4).map { PercussionTiming.swungOnsetMsExact(it, 90, 0, m) }
        val swung = (0..4).map { PercussionTiming.swungOnsetMsExact(it, 90, 100, m) }
        // SwingModel.Default (see SWING_MODEL_FORMULA): 1st and 2nd 16ths are DELAYED
        // (offsets +d/2 and +d), the 3rd sits on the grid, the 4th is ANTICIPATED (−d/2).
        assertTrue(swung[1] > straight[1], "2nd 16th is delayed toward the triplet third")
        assertEquals(straight[2], swung[2], 1e-9, "3rd 16th stays on the grid")
        assertTrue(swung[3] < straight[3], "4th 16th is anticipated")
        // Beat length is preserved, so the loop keeps its total length at any swing.
        assertEquals(4 * base, swung[4] - swung[0], 1e-6)
        assertEquals(4 * base, straight[4] - straight[0], 1e-6)
        // Same model as the scheduler, just unquantised: at 120 bpm — where a 16th is a
        // whole 125 ms and the scheduler's truncation vanishes — the two grids agree.
        // (At 90 bpm they diverge purely by that rounding; see the drift test above.)
        // Both measured from the downbeat, which is where a loop starts either way.
        val zero = PercussionTiming.swungOnsetMsExact(0, 120, 100, m)
        var acc = 0L
        for (k in 0..7) {
            val exactAt = PercussionTiming.swungOnsetMsExact(k, 120, 100, m) - zero
            assertTrue(abs(acc - exactAt) <= 1.0, "slot $k: exact $exactAt vs scheduler $acc")
            acc += PercussionTiming.swungSlotMs(k, 120, 100, m)
        }
    }

    @Test fun `a swung render still puts the downbeat at sample zero`() {
        val p = patternOf("0,-,-,-,-,-,-,-,-,-,-,-,-,-,-,-")
        for (swing in listOf(0, 50, 100)) {
            val r = PercussionRender.render(
                PercussionRender.Spec(p, bpm = 90, swing = swing, sampleRate = sr), impulses,
            )
            assertEquals(listOf(0), onsets(r.samples), "swing $swing must start on sample 0")
        }
    }

    // ---- WAV container ----

    @Test fun `WAV round-trips through encode and decode`() {
        val src = FloatArray(1000) { (it % 100) / 100f - 0.5f }
        val bytes = WavFile.encodeMono16(src, 44_100)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        val back = requireNotNull(WavFile.decodeMono16(bytes))
        assertEquals(44_100, back.sampleRate)
        assertEquals(src.size, back.samples.size)
        for (i in src.indices) assertEquals(src[i], back.samples[i], 1e-4f)
    }

    @Test fun `WAV clamps instead of wrapping and rejects non-PCM bytes`() {
        val bytes = WavFile.encodeMono16(floatArrayOf(2f, -2f), 22_050)
        val back = requireNotNull(WavFile.decodeMono16(bytes))
        assertEquals(22_050, back.sampleRate)
        assertTrue(back.samples[0] > 0.99f && back.samples[1] < -0.99f, "clipping must clamp, not wrap")
        assertEquals(null, WavFile.decodeMono16(ByteArray(64)))
        assertEquals(null, WavFile.decodeMono16("not a wav file at all".toByteArray()))
    }

    @Test fun `the bundled surdo samples decode as 44_1k mono`() {
        val f = java.io.File("../app/src/main/assets/drums/surdo_0.wav")
        if (!f.isFile) return   // running outside a full checkout
        val d = requireNotNull(WavFile.decodeMono16(f.readBytes()))
        assertEquals(44_100, d.sampleRate)
        assertTrue(d.samples.isNotEmpty())
        assertTrue(d.samples.any { abs(it) > 0.1f }, "sample should not be silence")
    }
}

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

    @Test fun `octave down produces ~twice the length with no tail duplication`() {
        val inst = SampleInstrument("t", listOf(GuitarSample(60, ramp(100))))
        val src = SampleSource(inst, 48)   // rate 0.5 -> ~199 output frames
        val out = FloatArray(4096)
        var total = 0
        while (true) { val n = src.render(out, 4096); if (n == 0) break; total += n }
        assertTrue(total in 198..200, "expected ~199 output frames, got $total")
        assertTrue(src.isFinished)
    }
}

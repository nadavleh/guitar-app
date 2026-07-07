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

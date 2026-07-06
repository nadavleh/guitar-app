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

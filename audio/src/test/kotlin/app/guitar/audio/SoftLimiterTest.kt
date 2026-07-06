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

    @Test
    fun `non-finite input produces finite bounded output`() {
        val lim = SoftLimiter(sampleRate = 48000, ceiling = 0.944f)
        val l = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.5f, 2f)
        val r = floatArrayOf(Float.POSITIVE_INFINITY, Float.NaN, 0.3f, Float.NEGATIVE_INFINITY, -5f)
        lim.process(l, r, 5)
        for (i in 0 until 5) {
            assertTrue(l[i].isFinite() && l[i] in -0.9441f..0.9441f, "L[$i]=${l[i]}")
            assertTrue(r[i].isFinite() && r[i] in -0.9441f..0.9441f, "R[$i]=${r[i]}")
        }
    }

    @Test
    fun `gain recovers gradually after a loud transient`() {
        val lim = SoftLimiter(sampleRate = 48000, ceiling = 0.944f, releaseMs = 10.0)
        val hot = FloatArray(64) { 4f }; val hotR = FloatArray(64) { 4f }
        lim.process(hot, hotR, 64)                 // drives gain down toward ceiling/4
        val quiet = FloatArray(2000) { 0.5f }; val quietR = FloatArray(2000) { 0.5f }
        lim.process(quiet, quietR, 2000)
        // first quiet sample is still attenuated (gain hasn't recovered yet)...
        assertTrue(quiet[0] < 0.5f, "expected initial attenuation, got ${quiet[0]}")
        // ...and by the end the gain has recovered so output ~= input (0.5), and it's monotonic-ish up
        assertTrue(quiet[1999] > quiet[0], "release should recover upward")
        assertTrue(quiet[1999] in 0.49f..0.5001f, "should recover to ~unity, got ${quiet[1999]}")
    }
}

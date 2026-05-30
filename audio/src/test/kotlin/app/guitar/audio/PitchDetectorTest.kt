package app.guitar.audio

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PitchDetectorTest {

    private val sr = 44100
    private val pd = PitchDetector(sampleRate = sr)

    @Test fun `detects A4 within 2 cents`() {
        val freq = pd.detect(sine(440f, sr, 2048))!!
        assertWithinCents(freq, 440f, 2f)
    }

    @Test fun `detects low E2 within 5 cents`() {
        val freq = pd.detect(sine(82.41f, sr, 4096))!!
        assertWithinCents(freq, 82.41f, 5f)
    }

    @Test fun `detects high E4 within 2 cents`() {
        val freq = pd.detect(sine(329.63f, sr, 2048))!!
        assertWithinCents(freq, 329.63f, 2f)
    }

    @Test fun `detects D3 within 2 cents`() {
        val freq = pd.detect(sine(146.83f, sr, 2048))!!
        assertWithinCents(freq, 146.83f, 2f)
    }

    @Test fun `detects 442 Hz separately from 440 Hz`() {
        val f440 = pd.detect(sine(440f, sr, 2048))!!
        val f442 = pd.detect(sine(442f, sr, 2048))!!
        assertTrue(f442 > f440, "442 Hz signal should yield higher detected freq than 440 Hz ($f440 vs $f442)")
    }

    @Test fun `mixed sine + harmonic still picks the fundamental`() {
        // 220 Hz fundamental + 440 Hz second harmonic (half amplitude).
        // YIN must pick the fundamental, not the harmonic.
        val n = 4096
        val samples = FloatArray(n)
        val w1 = 2.0 * Math.PI * 220.0 / sr
        val w2 = 2.0 * Math.PI * 440.0 / sr
        for (i in 0 until n) {
            samples[i] = (0.6 * sin(w1 * i) + 0.4 * sin(w2 * i)).toFloat()
        }
        val freq = pd.detect(samples)!!
        assertWithinCents(freq, 220f, 5f)
    }

    @Test fun `pure noise returns null or low confidence`() {
        val rng = java.util.Random(42)
        val noise = FloatArray(2048) { rng.nextGaussian().toFloat() * 0.1f }
        val freq = pd.detect(noise)
        // We accept either null (rejected) or a freq, but if a freq comes back
        // it should at least be in the valid range. (YIN sometimes locks onto a
        // random low frequency for white noise.)
        if (freq != null) {
            assertTrue(freq >= 50f && freq <= 1500f)
        }
    }

    @Test fun `silence returns null`() {
        val freq = pd.detect(FloatArray(2048))
        assertNull(freq)
    }

    // ---------- helpers ----------

    private fun sine(freq: Float, sr: Int, n: Int, amp: Float = 0.5f): FloatArray {
        val out = FloatArray(n)
        val omega = 2.0 * Math.PI * freq / sr
        for (i in 0 until n) out[i] = (amp * sin(omega * i)).toFloat()
        return out
    }

    private fun assertWithinCents(detected: Float, expected: Float, tol: Float) {
        val cents = 1200.0 * (ln(detected / expected.toDouble()) / ln(2.0))
        assertTrue(
            abs(cents) <= tol,
            "expected $expected Hz, got $detected Hz (${"%.2f".format(cents)} cents off, tolerance $tol)"
        )
    }
}

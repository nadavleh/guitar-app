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

package app.guitar.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WavDecoderTest {

    /** Build a 16-bit PCM mono WAV in memory. */
    private fun wav16(samples: FloatArray, rate: Int): ByteArray {
        val dataLen = samples.size * 2
        val out = ByteArrayOutputStream()
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun le16(v: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
        str("RIFF"); le32(36 + dataLen); str("WAVE")
        str("fmt "); le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
        str("data"); le32(dataLen)
        for (s in samples) {
            le16((s.coerceIn(-1f, 1f) * 32767).toInt())
        }
        return out.toByteArray()
    }

    @Test fun decodesPcm16MonoRoundTrip() {
        val src = FloatArray(1000) { sin(2 * PI * 440 * it / 44100).toFloat() * 0.5f }
        val decoded = WavDecoder.decode(wav16(src, 44100), targetRate = 44100)
        assertNotNull(decoded)
        assertEquals(src.size, decoded.size)
        for (i in src.indices) assertTrue(kotlin.math.abs(src[i] - decoded[i]) < 1e-3f)
    }

    @Test fun resamplesToTargetRate() {
        val src = FloatArray(2205) { sin(2 * PI * 220 * it / 22050).toFloat() * 0.5f } // 0.1s @ 22050
        val decoded = WavDecoder.decode(wav16(src, 22050), targetRate = 44100)
        assertNotNull(decoded)
        // ~0.1s at 44100 ≈ 4410 samples (allow rounding slack).
        assertTrue(decoded.size in 4300..4500, "got ${decoded.size}")
    }

    @Test fun rejectsNonWav() {
        assertEquals(null, WavDecoder.decode(ByteArray(10), targetRate = 48000))
        assertEquals(null, WavDecoder.decode("not a wav file at all....".toByteArray(), targetRate = 48000))
    }

    /** The bundled drum/guitar assets are 44.1 kHz files; on a 48 kHz engine they must come
     *  back stretched to 48 kHz so they play at the right pitch and length. */
    @Test fun resamples44kAssetsUpTo48k() {
        val src = FloatArray(4410) { sin(2 * PI * 220 * it / 44100).toFloat() * 0.5f } // 0.1s @ 44100
        val decoded = WavDecoder.decode(wav16(src, 44100), targetRate = 48000)
        assertNotNull(decoded)
        // 0.1 s at 48 kHz = 4800 samples (allow rounding slack).
        assertTrue(decoded.size in 4700..4900, "got ${decoded.size}")
    }

    /** 44.1 -> 48 kHz is now the normal path for every bundled asset, so the interpolator
     *  has to track the true waveform closely — not just land in the right ballpark. */
    @Test fun resamplingStaysFaithfulToTheOriginalWaveform() {
        val hz = 1000.0
        val src = FloatArray(4410) { sin(2 * PI * hz * it / 44100).toFloat() * 0.5f }
        val decoded = WavDecoder.decode(wav16(src, 44100), targetRate = 48000)
        assertNotNull(decoded)
        // Compare against the analytic 48 kHz sine, skipping the clamped edges.
        var worst = 0f
        for (i in 4 until decoded.size - 4) {
            val expected = sin(2 * PI * hz * i / 48000).toFloat() * 0.5f
            val err = kotlin.math.abs(decoded[i] - expected)
            if (err > worst) worst = err
        }
        // Measured at this frequency: Catmull-Rom cubic errs by 2.3e-5, plain linear
        // interpolation by 1.3e-3 (54x worse). The bound sits between them, so dropping
        // back to linear interpolation fails this test rather than passing quietly.
        assertTrue(worst < 2e-4f, "resampler drifts from the true waveform: worst err $worst")
    }
}

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
}

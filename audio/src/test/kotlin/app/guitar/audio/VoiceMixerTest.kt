package app.guitar.audio

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class VoiceMixerTest {
    // Center pan (default 0.0) applies constant-power gL = gR = cos(pi/4) to every
    // voice (Task 7). Values below are the M1 raw sums scaled by this factor.
    private val centerGain = 0.70710678f

    @Test
    fun `sums two voices sample-accurately (mono, L equals R in M1)`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(floatArrayOf(0.2f, 0.2f, 0.2f))))
        m.add(MixVoice(BufferSource(floatArrayOf(0.1f, 0.1f))))
        val l = FloatArray(3); val r = FloatArray(3)
        m.mixBlock(l, r, 3)
        assertEquals(0.3f * centerGain, l[0], 1e-5f)
        assertEquals(0.3f * centerGain, l[1], 1e-5f)
        assertEquals(0.2f * centerGain, l[2], 1e-5f)   // 2nd voice drained
        assertEquals(l.toList(), r.toList())
    }

    @Test
    fun `delayFrames postpones a voice on the mixer clock`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(floatArrayOf(1f, 1f)), delayFrames = 2))
        val l = FloatArray(4); val r = FloatArray(4)
        m.mixBlock(l, r, 4)
        val expected = listOf(0f, 0f, centerGain, centerGain)
        for (idx in expected.indices) assertEquals(expected[idx], l[idx], 1e-5f)
    }

    @Test
    fun `hard-left pan routes signal to L only`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(floatArrayOf(0.8f)), pan = -1.0))
        val l = FloatArray(1); val r = FloatArray(1)
        m.mixBlock(l, r, 1)
        assertTrue(l[0] > 0.79f && r[0] < 0.01f, "L=${l[0]} R=${r[0]}")
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

    @Test
    fun `lastPeak is per-block and a fresh voice is not the steal target`() {
        val m = VoiceMixer(sampleRate = 48000)
        val loud = MixVoice(BufferSource(FloatArray(1000) { 0.9f }))
        val quiet = MixVoice(BufferSource(FloatArray(1000) { 0.02f }))
        m.add(loud); m.add(quiet)
        val l = FloatArray(128); val r = FloatArray(128)
        m.mixBlock(l, r, 128)
        // per-block peak reflects actual current loudness, not a historical max
        assertTrue(quiet.lastPeak < loud.lastPeak, "quiet=${quiet.lastPeak} loud=${loud.lastPeak}")
        assertTrue(quiet.lastPeak > 0f, "quiet voice did sound this block")
        // a freshly-added, not-yet-mixed voice stays at the high sentinel so it won't be stolen
        val fresh = MixVoice(BufferSource(FloatArray(1000) { 0.9f }))
        m.add(fresh)
        assertEquals(Float.MAX_VALUE, fresh.lastPeak)
        // therefore the quietest actually-sounding voice is the steal target, not the fresh one
        assertTrue(quiet.lastPeak < fresh.lastPeak)
    }

    @Test
    fun `reverb send produces a decaying tail after the source ends and isRingingOut flips`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(FloatArray(64) { 0.8f }), reverbSend = 0.5f))
        val l = FloatArray(64); val r = FloatArray(64)
        m.mixBlock(l, r, 64)                 // source plays; feeds reverb
        assertEquals(0, m.activeCount)       // dry source drained + removed

        // The comb delay lines (~1100+ samples, per FreeverbTest) haven't cycled yet at
        // this point, so isRingingOut() may still read true for a few more blocks before
        // the wet tail actually arrives at the output. Pump silence and track the same
        // develop-then-decay shape FreeverbTest verifies directly: the tail becomes
        // audible and isRingingOut() reads false while it rings, then flips back to
        // true once it has fully decayed.
        var heardTail = false
        var sawRinging = false
        var rangOut = false
        var iterations = 0
        while (iterations < 8000) {
            val bl = FloatArray(64); val br = FloatArray(64)
            m.mixBlock(bl, br, 64)
            if (bl.any { kotlin.math.abs(it) > 1e-4f }) heardTail = true
            if (!m.isRingingOut()) sawRinging = true
            if (sawRinging && m.isRingingOut()) { rangOut = true; break }
            iterations++
        }
        assertTrue(heardTail, "expected an audible reverb tail after the source ended")
        assertTrue(sawRinging, "expected isRingingOut() to read false while the tail was ringing")
        assertTrue(rangOut, "expected isRingingOut() to flip back to true once the tail decayed (ran $iterations blocks)")
    }
}

package app.guitar.audio

import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class FreeverbTest {
    @Test
    fun `impulse produces a tail that develops then rings out`() {
        val rv = Freeverb(sampleRate = 48000, wet = 0.5f)
        // Inject an impulse in one production-sized (128-frame) block.
        val l = FloatArray(128); val r = FloatArray(128)
        l[0] = 1f; r[0] = 1f
        rv.process(l, r, 128)
        var impulseEnergy = 0f; for (i in 0 until 128) impulseEnergy += abs(l[i])
        assertTrue(impulseEnergy > 0f, "impulse block should carry energy (at least the dry impulse)")

        // Feed silence in 128-frame blocks (as the mixer does). The comb delay lines take
        // ~1100+ samples to cycle, so the wet tail first develops (isRingingOut == false),
        // then decays back below threshold (isRingingOut == true).
        var sawTail = false
        var rangOut = false
        var iterations = 0
        while (iterations < 8000) {
            val s = FloatArray(128); val s2 = FloatArray(128)
            rv.process(s, s2, 128)
            if (!rv.isRingingOut()) sawTail = true
            if (sawTail && rv.isRingingOut()) { rangOut = true; break }
            iterations++
        }
        assertTrue(sawTail, "reverb tail should develop after the impulse")
        assertTrue(rangOut, "reverb should then decay to silence (ran $iterations blocks)")
    }

    @Test
    fun `stays bounded (no runaway feedback)`() {
        val rv = Freeverb(sampleRate = 48000, wet = 0.5f)
        val l = FloatArray(48000) { 0.5f }; val r = FloatArray(48000) { 0.5f }
        rv.process(l, r, 48000)
        for (i in l.indices) assertTrue(abs(l[i]) < 4f && !l[i].isNaN(), "L[$i]=${l[i]}")
    }
}

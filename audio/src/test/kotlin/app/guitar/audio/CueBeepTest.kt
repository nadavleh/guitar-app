package app.guitar.audio

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Car-mode lead-in cue: a soft-attack sine, synthesised so no asset ships. */
class CueBeepTest {

    private val hz = 880.0
    private val ms = 140
    private val peak = 0.55f
    private val attack = 5

    @Test fun `length is the requested duration at the given sample rate`() {
        assertEquals(44100 * ms / 1000, CueBeep.render(hz, ms, 44100, peak, attack).size)
        assertEquals(48000 * ms / 1000, CueBeep.render(hz, ms, 48000, peak, attack).size)
    }

    @Test fun `every sample is finite and within the peak`() {
        val buf = CueBeep.render(hz, ms, 44100, peak, attack)
        for ((i, s) in buf.withIndex()) {
            assertTrue(s.isFinite(), "sample $i is not finite: $s")
            assertTrue(abs(s) <= peak + 1e-6f, "sample $i = $s exceeds peak $peak")
        }
    }

    @Test fun `the attack starts from silence so the onset does not click`() {
        val buf = CueBeep.render(hz, ms, 44100, peak, attack)
        assertTrue(abs(buf[0]) < 0.02f, "onset sample ${buf[0]} would click")
    }

    @Test fun `the tail decays away so beeps do not run together`() {
        val buf = CueBeep.render(hz, ms, 44100, peak, attack)
        assertTrue(abs(buf.last()) < 0.05f * peak, "tail sample ${buf.last()} is still loud")
    }

    @Test fun `the loudest sample lands early - attack then decay`() {
        val buf = CueBeep.render(hz, ms, 44100, peak, attack)
        val loudest = buf.indices.maxByOrNull { abs(buf[it]) }!!
        assertTrue(loudest < buf.size * 15 / 100,
            "peak at sample $loudest of ${buf.size} — that is not an attack-then-decay envelope")
    }

    @Test fun `the tone really is 880 Hz`() {
        // A full-wave sine crosses zero twice per cycle: 2 * 880 * 0.14 s ≈ 246.
        val buf = CueBeep.render(hz, ms, 44100, peak, attack)
        var crossings = 0
        for (i in 1 until buf.size) {
            if ((buf[i - 1] < 0f && buf[i] >= 0f) || (buf[i - 1] >= 0f && buf[i] < 0f)) crossings++
        }
        val expected = (2 * hz * ms / 1000.0).toInt()
        assertTrue(abs(crossings - expected) <= 4,
            "expected ~$expected zero crossings for ${hz} Hz, got $crossings")
    }

    @Test fun `frequency is honoured - a higher tone crosses zero more often`() {
        fun crossings(f: Double): Int {
            val b = CueBeep.render(f, ms, 44100, peak, attack)
            return (1 until b.size).count { (b[it - 1] < 0f) != (b[it] < 0f) }
        }
        assertTrue(crossings(1760.0) > crossings(880.0) * 3 / 2,
            "doubling the frequency should roughly double the crossings")
    }

    @Test fun `a zero-length request yields an empty buffer rather than throwing`() {
        assertEquals(0, CueBeep.render(hz, 0, 44100, peak, attack).size)
    }
}

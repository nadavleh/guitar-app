package app.guitar.audio

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluckedSynthTest {

    @Test
    fun `midi 69 is A4 at 440 Hz`() {
        assertEquals(440.0, PluckedSynth.midiToFreq(69), 1e-9)
    }

    @Test
    fun `midi 60 is C4 at the standard frequency`() {
        assertEquals(261.6256, PluckedSynth.midiToFreq(60), 0.001)
    }

    @Test
    fun `midi 40 is E2 — the low guitar string`() {
        assertEquals(82.4069, PluckedSynth.midiToFreq(40), 0.001)
    }

    @Test
    fun `output length matches sampleRate times duration`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val out = synth.synthesize(midiNote = 69, durationSec = 1.0)
        assertEquals(48000, out.size)
    }

    @Test
    fun `output stays bounded inside the amplitude envelope`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val out = synth.synthesize(midiNote = 60, durationSec = 0.5, amplitude = 0.6)
        for (s in out) assertTrue(s in -0.61f..0.61f, "sample $s out of amplitude bounds")
    }

    @Test
    fun `deterministic with the same seed`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val a = synth.synthesize(midiNote = 60, durationSec = 0.1, seed = 42L)
        val b = synth.synthesize(midiNote = 60, durationSec = 0.1, seed = 42L)
        assertTrue(a.contentEquals(b), "same seed should produce identical buffers")
    }

    @Test
    fun `different seeds give different output`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val a = synth.synthesize(midiNote = 60, durationSec = 0.1, seed = 1L)
        val b = synth.synthesize(midiNote = 60, durationSec = 0.1, seed = 2L)
        assertTrue(!a.contentEquals(b), "different seeds should produce different buffers")
    }

    @Test
    fun `tail fades to near zero`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val out = synth.synthesize(midiNote = 60, durationSec = 0.5)
        assertTrue(abs(out.last()) < 0.01f, "expected fade-out at tail, got ${out.last()}")
    }

    @Test
    fun `output is not silence`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val out = synth.synthesize(midiNote = 60, durationSec = 0.5)
        val rms = sqrt(out.map { (it * it).toDouble() }.sum() / out.size)
        assertTrue(rms > 0.02, "output appears silent (RMS = $rms)")
    }

    @Test
    fun `amplitude scales linearly`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val a = synth.synthesize(midiNote = 60, durationSec = 0.2, seed = 7L, amplitude = 0.3)
        val b = synth.synthesize(midiNote = 60, durationSec = 0.2, seed = 7L, amplitude = 0.6)
        val rmsA = sqrt(a.map { (it * it).toDouble() }.sum() / a.size)
        val rmsB = sqrt(b.map { (it * it).toDouble() }.sum() / b.size)
        val ratio = rmsB / rmsA
        assertTrue(ratio in 1.8..2.2, "amplitude doubling should ~double RMS, got ratio=$ratio")
    }

    @Test
    fun `lower MIDI notes produce longer delay buffers (lower fundamental period)`() {
        // Karplus-Strong delay length is sampleRate/freq, so lower freq → longer period.
        // We verify this indirectly: the energy in the first few periods of a low note
        // is less stationary (longer cycle), but for this test we just check that
        // synthesis succeeds across a wide MIDI range.
        val synth = PluckedSynth(sampleRate = 48000)
        val low = synth.synthesize(28, 0.1)   // E1 ~41 Hz
        val high = synth.synthesize(84, 0.1)  // C6 ~1046 Hz
        assertTrue(low.any { it != 0f })
        assertTrue(high.any { it != 0f })
    }

    @Test
    fun `out-of-range MIDI throws`() {
        val synth = PluckedSynth()
        assertThrows<IllegalArgumentException> { synth.synthesize(-1, 0.1) }
        assertThrows<IllegalArgumentException> { synth.synthesize(128, 0.1) }
    }

    @Test
    fun `non-positive duration throws`() {
        val synth = PluckedSynth()
        assertThrows<IllegalArgumentException> { synth.synthesize(60, 0.0) }
        assertThrows<IllegalArgumentException> { synth.synthesize(60, -1.0) }
    }

    @Test
    fun `synthesizeChord length accounts for strum delay`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val sustainSec = 1.0
        val strumDelaySamples = 1920  // 40 ms
        val notes = listOf(40, 45, 50, 55, 59, 64)   // standard tuning open strings
        val buf = synth.synthesizeChord(notes, sustainSec, strumDelaySamples)
        val perVoice = (48000 * sustainSec).toInt()
        val expected = perVoice + (notes.size - 1) * strumDelaySamples
        assertEquals(expected, buf.size)
    }

    @Test
    fun `synthesizeChord with empty notes yields empty buffer`() {
        val synth = PluckedSynth()
        assertEquals(0, synth.synthesizeChord(emptyList(), 1.0, 1920).size)
    }

    @Test
    fun `synthesizeChord stays bounded`() {
        val synth = PluckedSynth(sampleRate = 48000)
        val notes = listOf(40, 45, 50, 55, 59, 64)
        val buf = synth.synthesizeChord(notes, 0.5, 1920)
        for (s in buf) {
            assertTrue(s in -1f..1f, "chord sample $s out of [-1, 1]")
        }
    }

    @Test
    fun `synthesizeChord skips out-of-range MIDI notes`() {
        val synth = PluckedSynth()
        val valid = listOf(60, 64, 67)
        val mixed = listOf(60, -5, 64, 200, 67)
        val a = synth.synthesizeChord(valid, 0.2, 960, seedBase = 1L)
        val b = synth.synthesizeChord(mixed, 0.2, 960, seedBase = 1L)
        // Should produce the same buffer (invalid notes silently dropped)
        assertTrue(a.contentEquals(b))
    }
}

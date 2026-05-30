package app.guitar.audio

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitchAnalysisTest {

    @Test fun `A4 = 440 Hz maps to MIDI 69 with 0 cents`() {
        val e = PitchAnalysis.analyze(440f)
        assertEquals(69, e.midi)
        assertTrue(abs(e.cents) < 0.001f, "cents=${e.cents}")
    }

    @Test fun `C4 = 261_63 Hz maps to MIDI 60 within a cent`() {
        val e = PitchAnalysis.analyze(261.63f)
        assertEquals(60, e.midi)
        assertTrue(abs(e.cents) < 1f, "cents=${e.cents}")
    }

    @Test fun `frequency just above A4 measures positive cents`() {
        val e = PitchAnalysis.analyze(441f)
        assertEquals(69, e.midi)
        assertTrue(e.cents > 0f && e.cents < 10f, "cents=${e.cents}")
    }

    @Test fun `frequency just below A4 measures negative cents`() {
        val e = PitchAnalysis.analyze(439f)
        assertEquals(69, e.midi)
        assertTrue(e.cents < 0f && e.cents > -10f, "cents=${e.cents}")
    }

    @Test fun `boundary - exactly between two notes rounds to one with ~50 cents off`() {
        // Halfway between A4 (440) and Bb4 (466.16): geometric mean = 452.89
        val midpoint = (440.0 * 466.16).let { Math.sqrt(it).toFloat() }
        val e = PitchAnalysis.analyze(midpoint)
        // Could round either way; cents magnitude must be close to 50.
        assertTrue(abs(abs(e.cents) - 50f) < 1f, "cents=${e.cents}")
    }

    @Test fun `custom A4 ref shifts the whole grid`() {
        // With A4 = 442, a 442 Hz signal is exactly A and 0 cents off.
        val e = PitchAnalysis.analyze(442f, a4Hz = 442f)
        assertEquals(69, e.midi)
        assertTrue(abs(e.cents) < 0.001f, "cents=${e.cents}")
    }

    @Test fun `midiToFreq inverts analyze for A4`() {
        val f = PitchAnalysis.midiToFreq(69, a4Hz = 440f)
        assertTrue(abs(f - 440f) < 0.001f, "got $f")
    }

    @Test fun `midiToFreq for low E (MIDI 40) is 82_4 Hz`() {
        val f = PitchAnalysis.midiToFreq(40, a4Hz = 440f)
        assertTrue(abs(f - 82.4068892f) < 0.001f, "got $f")
    }

    @Test fun `midiToFreq scales with custom A4`() {
        val f440 = PitchAnalysis.midiToFreq(60, a4Hz = 440f)
        val f445 = PitchAnalysis.midiToFreq(60, a4Hz = 445f)
        assertTrue(f445 > f440)
        // Ratio of A4 references equals ratio of any-note frequencies
        assertTrue(abs(f445 / f440 - 445f / 440f) < 0.001f)
    }
}

package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Note2ChordTest {

    @Test fun `chordSymbol for C major is C`() {
        val c = N2cChallenge(PitchClass.C, isMinor = false, testNoteOffsetSemitones = 2)
        assertEquals("C", c.chordSymbol)
    }

    @Test fun `chordSymbol for A minor is Am`() {
        val c = N2cChallenge(PitchClass.A, isMinor = true, testNoteOffsetSemitones = 2)
        assertEquals("Am", c.chordSymbol)
    }

    @Test fun `testNote of D over C is the 9`() {
        val c = N2cChallenge(PitchClass.C, isMinor = false, testNoteOffsetSemitones = 2)
        assertEquals(PitchClass.D, c.testNote)
        assertEquals("9 (2)", c.answerLabel)
    }

    @Test fun `testNote of B over C is maj7`() {
        val c = N2cChallenge(PitchClass.C, isMinor = false, testNoteOffsetSemitones = 11)
        assertEquals(PitchClass.B, c.testNote)
        assertEquals("maj7", c.answerLabel)
    }

    @Test fun `testNote of Bb over C minor is b7`() {
        val c = N2cChallenge(PitchClass.C, isMinor = true, testNoteOffsetSemitones = 10)
        assertEquals("b7", c.answerLabel)
    }

    @Test fun `testNote of Ab over C minor is b13 b6`() {
        val c = N2cChallenge(PitchClass.C, isMinor = true, testNoteOffsetSemitones = 8)
        assertEquals("b13 (b6)", c.answerLabel)
    }

    @Test fun `random returns offsets only from diatonic non-chord-tones`() {
        val rng = Random(123)
        repeat(200) {
            val c = N2cChallenge.random(rng)
            val pool = if (c.isMinor) N2cChallenge.MINOR_TEST_OFFSETS else N2cChallenge.MAJOR_TEST_OFFSETS
            assertTrue(
                c.testNoteOffsetSemitones in pool.toList(),
                "offset ${c.testNoteOffsetSemitones} not in pool for ${c.chordSymbol}"
            )
        }
    }

    @Test fun `random covers all 12 roots eventually`() {
        val rng = Random(0)
        val roots = mutableSetOf<Int>()
        for (i in 0 until 1000) roots += N2cChallenge.random(rng).chordRoot.value
        assertEquals((0..11).toSet(), roots)
    }
}

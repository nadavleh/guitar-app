package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EarTrainingTest {

    // ----- degreeRoot -----

    @Test fun `I in C major is C`() {
        assertEquals(PitchClass.C, EarTraining.degreeRoot(PitchClass.C, 1, TrainingMode.Major))
    }

    @Test fun `V in C major is G`() {
        assertEquals(PitchClass.G, EarTraining.degreeRoot(PitchClass.C, 5, TrainingMode.Major))
    }

    @Test fun `vi in C major is A`() {
        assertEquals(PitchClass.A, EarTraining.degreeRoot(PitchClass.C, 6, TrainingMode.Major))
    }

    @Test fun `i in A minor is A`() {
        assertEquals(PitchClass.A, EarTraining.degreeRoot(PitchClass.A, 1, TrainingMode.Minor))
    }

    @Test fun `VI in A minor is F`() {
        assertEquals(PitchClass.F, EarTraining.degreeRoot(PitchClass.A, 6, TrainingMode.Minor))
    }

    @Test fun `VII in A minor is G`() {
        assertEquals(PitchClass.G, EarTraining.degreeRoot(PitchClass.A, 7, TrainingMode.Minor))
    }

    // ----- resolve -----

    @Test fun `I in C major triad`() {
        val r = EarTraining.resolve(1, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Triads)
        assertEquals("C", r.symbol)
        assertEquals("I", r.romanLabel)
    }

    @Test fun `ii in C major triad`() {
        val r = EarTraining.resolve(2, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Triads)
        assertEquals("Dm", r.symbol)
        assertEquals("ii", r.romanLabel)
    }

    @Test fun `vii° in C major triad is Bdim`() {
        val r = EarTraining.resolve(7, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Triads)
        assertEquals("Bdim", r.symbol)
        assertEquals("vii°", r.romanLabel)
    }

    @Test fun `Imaj7 in C major`() {
        val r = EarTraining.resolve(1, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("Cmaj7", r.symbol)
        assertEquals("Imaj7", r.romanLabel)
    }

    @Test fun `V7 in C major`() {
        val r = EarTraining.resolve(5, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("G7", r.symbol)
        assertEquals("V7", r.romanLabel)
    }

    @Test fun `ii7 in C major strips redundant m`() {
        val r = EarTraining.resolve(2, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("Dm7", r.symbol)
        assertEquals("ii7", r.romanLabel)    // not "iim7"
    }

    @Test fun `vii°7 in C major is Bm7b5`() {
        val r = EarTraining.resolve(7, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Sevenths)
        assertEquals("Bm7b5", r.symbol)
        assertEquals("vii°7", r.romanLabel)
    }

    @Test fun `i in A minor triad is Am`() {
        val r = EarTraining.resolve(1, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Triads)
        assertEquals("Am", r.symbol)
        assertEquals("i", r.romanLabel)
    }

    @Test fun `V in A minor triad uses harmonic dominant`() {
        val r = EarTraining.resolve(5, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Triads)
        assertEquals("E", r.symbol)
        assertEquals("V", r.romanLabel)
    }

    @Test fun `V7 in A minor is E7`() {
        val r = EarTraining.resolve(5, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Sevenths)
        assertEquals("E7", r.symbol)
        assertEquals("V7", r.romanLabel)
    }

    @Test fun `ii°7 in A minor is Bm7b5`() {
        val r = EarTraining.resolve(2, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Sevenths)
        assertEquals("Bm7b5", r.symbol)
        assertEquals("ii°7", r.romanLabel)
    }

    @Test fun `IIImaj7 in A minor is Cmaj7`() {
        val r = EarTraining.resolve(3, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Sevenths)
        assertEquals("Cmaj7", r.symbol)
        assertEquals("IIImaj7", r.romanLabel)
    }

    @Test fun `Imaj9 in C major extended`() {
        val r = EarTraining.resolve(1, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Extended)
        assertEquals("Cmaj9", r.symbol)
        assertEquals("Imaj9", r.romanLabel)
    }

    @Test fun `V9 in C major extended`() {
        val r = EarTraining.resolve(5, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Extended)
        assertEquals("G9", r.symbol)
        assertEquals("V9", r.romanLabel)
    }

    @Test fun `ii9 in C major extended strips m`() {
        val r = EarTraining.resolve(2, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Extended)
        assertEquals("Dm9", r.symbol)
        assertEquals("ii9", r.romanLabel)
    }

    // ----- progression library -----

    @Test fun `all major progressions resolve to parseable chord symbols`() {
        for (p in EarTraining.MAJOR_PROGRESSIONS) {
            val chords = EarTraining.resolveProgression(p, PitchClass.C, ChordTypeLevel.Sevenths)
            assertEquals(4, chords.size)
            for (c in chords) {
                assertTrue(
                    ChordLibrary.parse(c.symbol) != null,
                    "ChordLibrary couldn't parse '${c.symbol}' from $p"
                )
            }
        }
    }

    @Test fun `all minor progressions resolve to parseable chord symbols`() {
        for (p in EarTraining.MINOR_PROGRESSIONS) {
            val chords = EarTraining.resolveProgression(p, PitchClass.A, ChordTypeLevel.Sevenths)
            for (c in chords) {
                assertTrue(
                    ChordLibrary.parse(c.symbol) != null,
                    "ChordLibrary couldn't parse '${c.symbol}' from $p"
                )
            }
        }
    }

    @Test fun `every degree resolves to a parseable triad`() {
        for (deg in 1..7) {
            val maj = EarTraining.resolve(deg, PitchClass.C, TrainingMode.Major, ChordTypeLevel.Triads)
            assertTrue(ChordLibrary.parse(maj.symbol) != null, "couldn't parse ${maj.symbol}")
            val min = EarTraining.resolve(deg, PitchClass.A, TrainingMode.Minor, ChordTypeLevel.Triads)
            assertTrue(ChordLibrary.parse(min.symbol) != null, "couldn't parse ${min.symbol}")
        }
    }

    @Test fun `randomProgression returns one of the library entries`() {
        val rng = kotlin.random.Random(42)
        val p = EarTraining.randomProgression(TrainingMode.Major, rng)
        assertTrue(p in EarTraining.MAJOR_PROGRESSIONS)
    }
}

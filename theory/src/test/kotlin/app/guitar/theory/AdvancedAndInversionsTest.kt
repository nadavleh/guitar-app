package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.random.Random

class AdvancedAndInversionsTest {

    // ----- Inversions -----

    @Test fun triadInversionsStackCorrectly() {
        val cmaj = ChordLibrary.qualities[""]!!
        // Root position from C4 (60): C E G
        assertEquals(listOf(60, 64, 67), Inversions.midis(60, cmaj, 0))
        // 1st inversion: E G C (C up an octave)
        assertEquals(listOf(64, 67, 72), Inversions.midis(60, cmaj, 1))
        // 2nd inversion: G C E
        assertEquals(listOf(67, 72, 76), Inversions.midis(60, cmaj, 2))
        assertEquals(3, Inversions.count(cmaj))
    }

    @Test fun seventhChordHasFourInversions() {
        val cmaj7 = ChordLibrary.qualities["maj7"]!!
        assertEquals(4, Inversions.count(cmaj7))
        // 3rd inversion puts the 7th (B) in the bass.
        val third = Inversions.midis(60, cmaj7, 3)
        assertEquals(71, third.first())   // B3 is the lowest note
    }

    @Test fun inversionNames() {
        assertEquals("Root position", Inversions.name(0))
        assertEquals("1st inversion", Inversions.name(1))
        assertEquals("3rd inversion", Inversions.name(3))
    }

    // ----- New chord qualities -----

    @Test fun newQualitiesParseAndAreCorrect() {
        assertEquals(
            listOf(Interval.P1, Interval.min3, Interval.P5, Interval.maj7),
            ChordLibrary.qualities["mMaj7"]!!.intervals,
        )
        // 7#5: dominant 7 with a raised 5th (#5 == min6 enharmonically).
        assertEquals(
            listOf(Interval.P1, Interval.maj3, Interval.min6, Interval.min7),
            ChordLibrary.qualities["7#5"]!!.intervals,
        )
        assertTrue(ChordLibrary.parse("CmMaj7") != null)
        assertTrue(ChordLibrary.parse("G7#5") != null)
        assertTrue(ChordLibrary.parse("Emaj7#5") != null)
    }

    // ----- Advanced progressions -----

    @Test fun advancedProgressionsAllResolveToParseableChords() {
        assertTrue(EarTraining.ADVANCED_PROGRESSIONS.isNotEmpty())
        for (np in EarTraining.ADVANCED_PROGRESSIONS) {
            assertTrue(np.chords.isNotEmpty(), "${np.name} has no chords")
            assertTrue(np.explanation.isNotBlank(), "${np.name} has no explanation")
            // Resolve in every key; every produced symbol must be parseable.
            for (k in 0..11) {
                for (rc in np.resolve(PitchClass(k))) {
                    assertTrue(
                        ChordLibrary.parse(rc.symbol) != null,
                        "unparseable symbol '${rc.symbol}' in ${np.name} (key $k)",
                    )
                }
            }
        }
    }

    @Test fun marioCadenceResolvesInC() {
        val mario = EarTraining.ADVANCED_PROGRESSIONS.first { it.name == "Mario Cadence" }
        val chords = mario.resolve(PitchClass.C)
        // bVI - bVII - I  ->  roots Ab(8) - Bb(10) - C(0) (spelling-agnostic)
        assertEquals(listOf("bVI", "bVII", "I"), chords.map { it.romanLabel })
        assertEquals(listOf(PitchClass(8), PitchClass(10), PitchClass(0)), chords.map { it.root })
    }

    @Test fun ragtimeCircleIsSecondaryDominants() {
        val rag = EarTraining.ADVANCED_PROGRESSIONS.first { it.name == "Ragtime Circle" }
        // I - VI7 - II7 - V7 in C -> C - A7 - D7 - G7
        assertEquals(listOf("C", "A7", "D7", "G7"), rag.resolve(PitchClass.C).map { it.symbol })
    }

    @Test fun extendedDiatonicOptionsAllParse() {
        for (map in listOf(EarTraining.MAJOR_DEGREES, EarTraining.MINOR_DEGREES)) {
            for ((_, info) in map) {
                for ((qual, _) in info.extendedOptions) {
                    assertTrue(ChordLibrary.parse("C$qual") != null, "ext quality '$qual' must parse")
                }
            }
        }
    }
}

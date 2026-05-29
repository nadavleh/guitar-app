package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScalePositionsTest {

    @Test
    fun `C major in standard tuning yields 7 positions`() {
        val major = ScaleLibrary.scales["major"]!!
        val positions = ScalePositions.forScale(
            root = PitchClass.C,
            scale = major,
            tuning = Tunings.standard,
            numFrets = 14,
        )
        assertEquals(7, positions.size, "expected 7 positions for a diatonic scale, got ${positions.size}")
    }

    @Test
    fun `A minor pentatonic in standard tuning yields 5 positions`() {
        val pent = ScaleLibrary.scales["minor pentatonic"]!!
        val positions = ScalePositions.forScale(
            root = PitchClass.A,
            scale = pent,
            tuning = Tunings.standard,
            numFrets = 14,
        )
        assertEquals(5, positions.size)
    }

    @Test
    fun `every position has span at most 5 frets (maxFretSpan + 1)`() {
        val major = ScaleLibrary.scales["major"]!!
        val positions = ScalePositions.forScale(
            root = PitchClass.C, scale = major,
            tuning = Tunings.standard, numFrets = 14,
        )
        for (p in positions) {
            val span = p.lastFret - p.firstFret
            assertTrue(span <= 4, "position ${p.index} spans frets ${p.firstFret}-${p.lastFret} (span=$span)")
        }
    }

    @Test
    fun `every position has at least 2 roots`() {
        val major = ScaleLibrary.scales["major"]!!
        val positions = ScalePositions.forScale(
            root = PitchClass.C, scale = major,
            tuning = Tunings.standard, numFrets = 14,
        )
        for (p in positions) {
            assertTrue(p.rootCount >= 2, "position ${p.index} has only ${p.rootCount} root(s)")
        }
    }

    @Test
    fun `position 1 of C major anchors on C at lowest fret on string 0`() {
        val major = ScaleLibrary.scales["major"]!!
        val positions = ScalePositions.forScale(
            root = PitchClass.C, scale = major,
            tuning = Tunings.standard, numFrets = 14,
        )
        val pos1 = positions[0]
        assertEquals(PitchClass.C, pos1.anchorPitchClass)
        // C on low E string is at fret 8 (E2 + 8 semitones = C3)
        assertEquals(8, pos1.firstFret)
        assertEquals(12, pos1.lastFret)
    }

    @Test
    fun `positions only contain pitches that are in the scale`() {
        val mixo = ScaleLibrary.scales["mixolydian"]!!
        val positions = ScalePositions.forScale(
            root = PitchClass.G, scale = mixo,
            tuning = Tunings.standard, numFrets = 14,
        )
        val scalePcs = mixo.notesFrom(PitchClass.G).toSet()
        for (p in positions) {
            for (fp in p.positions) {
                val pc = Fretboard.noteAt(Tunings.standard, fp).pitchClass
                assertTrue(pc in scalePcs, "position ${p.index} contains non-scale pitch $pc at $fp")
            }
        }
    }

    @Test
    fun `Drop D tuning produces different positions than Standard for the same scale`() {
        val major = ScaleLibrary.scales["major"]!!
        val standard = ScalePositions.forScale(PitchClass.C, major, Tunings.standard, numFrets = 14)
        val dropD = ScalePositions.forScale(PitchClass.C, major, Tunings.dropD, numFrets = 14)
        // Same scale → same pitch-class set; but the (string, fret) sets differ
        // because the low E string is now D.
        val standardPosSet = standard.flatMap { it.positions }.toSet()
        val dropDPosSet = dropD.flatMap { it.positions }.toSet()
        assertTrue(standardPosSet != dropDPosSet, "tuning change should alter position sets")
    }

    @Test
    fun `blues scale has 6 positions`() {
        val blues = ScaleLibrary.scales["blues"]!!
        val positions = ScalePositions.forScale(
            root = PitchClass.A, scale = blues,
            tuning = Tunings.standard, numFrets = 14,
        )
        assertEquals(6, positions.size)
    }
}

package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordDecompositionTest {

    /** Pitch classes (0–11) of a list of root-relative intervals. */
    private fun pcs(xs: List<Int>) = xs.map { ((it % 12) + 12) % 12 }.toSet()

    @Test fun `every entry has a root-bearing shell and a 3-note upper triad`() {
        for (dec in ChordDecompositions.ALL) {
            assertTrue(0 in dec.shell, "${dec.quality}: shell must contain the root")
            assertEquals(3, dec.upper.size, "${dec.quality}: upper must be a 3-note triad")
            // Upper intervals are authored in root-position ascending order, so the
            // two stacked thirds are consecutive differences.
            assertEquals(dec.upper.sorted(), dec.upper, "${dec.quality}: upper must be ascending")
            assertEquals(3, dec.upper.map { ((it % 12) + 12) % 12 }.toSet().size,
                "${dec.quality}: upper triad has duplicate pitch classes")
            val gap1 = dec.upper[1] - dec.upper[0]
            val gap2 = dec.upper[2] - dec.upper[1]
            assertTrue(gap1 in 3..4 && gap2 in 3..4,
                "${dec.quality}: upper isn't stacked thirds (gaps $gap1,$gap2)")
        }
    }

    @Test fun `triad-quality label matches the upper intervals`() {
        for (dec in ChordDecompositions.ALL) {
            val gap1 = dec.upper[1] - dec.upper[0]
            val gap2 = dec.upper[2] - dec.upper[1]
            val expected = when {
                gap1 == 4 && gap2 == 3 -> "major"
                gap1 == 3 && gap2 == 4 -> "minor"
                gap1 == 3 && gap2 == 3 -> "diminished"
                gap1 == 4 && gap2 == 4 -> "augmented"
                else -> "?"
            }
            assertEquals(expected, dec.upperTriad, "${dec.quality}: wrong upper-triad label")
        }
    }

    @Test fun `representative decompositions spell the right chord tones`() {
        // Cmaj7 = C E G B  ->  C + Em(E G B)
        val maj7 = ChordDecompositions.forQuality("maj7")!!
        assertEquals(setOf(0, 4, 7, 11), pcs(maj7.shell) + pcs(maj7.upper))
        assertEquals("minor", maj7.upperTriad)
        // C7 = C E G B♭  ->  C + E°(E G B♭)
        val dom7 = ChordDecompositions.forQuality("7")!!
        assertEquals(setOf(0, 4, 7, 10), pcs(dom7.shell) + pcs(dom7.upper))
        assertEquals("diminished", dom7.upperTriad)
        // Cm7 = C E♭ G B♭  ->  C + E♭(E♭ G B♭)
        val m7 = ChordDecompositions.forQuality("m7")!!
        assertEquals(setOf(0, 3, 7, 10), pcs(m7.shell) + pcs(m7.upper))
        assertEquals("major", m7.upperTriad)
        // C6 = C E A (no 5th)  ->  C + Am(A C E)
        val six = ChordDecompositions.forQuality("6")!!
        assertEquals(setOf(0, 4, 9), pcs(six.shell) + pcs(six.upper))
        // C13 ≈ C7 shell (C E B♭) + D major (D F♯ A) = 9 ♯11 13
        val thirteen = ChordDecompositions.forQuality("13")!!
        assertEquals(setOf(0, 4, 10, 2, 6, 9), pcs(thirteen.shell) + pcs(thirteen.upper))
        assertEquals("major", thirteen.upperTriad)
    }

    @Test fun `upperRootInterval is the lowest upper note's pitch class`() {
        // 13: upper D F♯ A starting at 14 -> pc 2
        assertEquals(2, ChordDecompositions.forQuality("13")!!.upperRootInterval)
        // maj7: upper E G B starting at 4
        assertEquals(4, ChordDecompositions.forQuality("maj7")!!.upperRootInterval)
    }
}

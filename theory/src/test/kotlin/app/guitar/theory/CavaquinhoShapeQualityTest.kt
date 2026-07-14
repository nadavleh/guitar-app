package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the cavaquinho (DGBD, the default tuning) shape-quality rules:
 *  1. no open string (fret 0) combined with a note above the 3rd fret;
 *  2. fretted span <= 4;
 *  3. at most one muted string;
 *  4. no unison (same note) on two physically adjacent strings;
 *  5. at most 5 canonical shapes per chord (CAGED-like set);
 *  6. the tonic is always present.
 */
class CavaquinhoShapeQualityTest {

    private val tuning = Tunings.cavaqDgbd
    private val gen = ChordShapeGenerator()
    private val triads = listOf("C", "G", "F", "D", "A", "E", "B", "Am", "Dm", "Em", "Gm", "Bm", "Cm", "F#m")
    private val sevenths = listOf("G7", "A7", "D7", "E7", "C7", "B7", "Dm7", "Am7", "Em7", "Cmaj7", "Fmaj7", "Bm7b5", "Cdim", "Am6", "C6")

    @Test fun `every DGBD voicing obeys the shape-quality rules`() {
        for (sym in triads + sevenths) {
            val (root, q) = ChordLibrary.parse(sym) ?: continue
            val shapes = gen.shapesFor(root, q, tuning, frets = 14)
            for (sh in shapes) {
                val frets = sh.frets
                val fretted = frets.filterNotNull().filter { it > 0 }
                val hasOpen = frets.any { it == 0 }
                if (hasOpen && fretted.isNotEmpty()) {
                    assertTrue(fretted.max() <= 3, "$sym $frets: open string with a note above fret 3")
                }
                if (fretted.size >= 2) {
                    assertTrue(fretted.max() - fretted.min() <= 4, "$sym $frets: fretted span exceeds 4")
                }
                assertTrue(sh.mutedCount <= 1, "$sym $frets: more than one muted string")
                // No unison on adjacent strings.
                val midis = sh.notes.map { it?.midi?.value }
                for (i in 0 until midis.size - 1) {
                    val a = midis[i]; val b = midis[i + 1]
                    if (a != null && b != null) assertTrue(a != b, "$sym $frets: unison on adjacent strings")
                }
                // Tonic always present.
                val pcs = sh.notes.filterNotNull().map { it.pitchClass }
                assertTrue(root in pcs, "$sym $frets: missing the tonic")
            }
        }
    }

    @Test fun `at most five canonical shapes per major and minor chord`() {
        for (sym in triads) {
            val (root, q) = ChordLibrary.parse(sym) ?: continue
            val shapes = gen.shapesFor(root, q, tuning, frets = 14)
            assertTrue(shapes.isNotEmpty(), "$sym produced no cavaquinho voicing")
            assertTrue(shapes.size <= 5, "$sym produced ${shapes.size} shapes (expected <= 5)")
            // Canonical shapes sit at distinct neck positions.
            assertTrue(shapes.map { it.position }.toSet().size == shapes.size,
                "$sym has duplicate-position shapes")
        }
    }

    @Test fun `seventh chords voice all four strings (no mutes)`() {
        for (sym in listOf("G7", "A7", "D7", "Dm7", "Am7", "Cmaj7")) {
            val (root, q) = ChordLibrary.parse(sym) ?: continue
            val shapes = gen.shapesFor(root, q, tuning, frets = 14)
            assertTrue(shapes.isNotEmpty(), "$sym produced no cavaquinho voicing")
            assertTrue(shapes.first().mutedCount == 0,
                "$sym best voicing ${shapes.first().frets} should sound all four strings")
        }
    }
}

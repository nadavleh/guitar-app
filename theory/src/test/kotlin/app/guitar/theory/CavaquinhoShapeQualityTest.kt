package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the cavaquinho (DGBD, the default tuning) shape-quality rules:
 *  1. a shape may not combine an open string (fret 0) with any note above the 3rd fret;
 *  2. the fretted span may not exceed 5;
 *  3. muted strings are rare — at most one per shape.
 */
class CavaquinhoShapeQualityTest {

    private val tuning = Tunings.cavaqDgbd
    private val gen = ChordShapeGenerator()
    private val symbols = listOf(
        "C", "G", "F", "D", "A", "E", "Am", "Dm", "Em", "Gm", "Bm",
        "G7", "A7", "D7", "E7", "C7", "B7", "Dm7", "Am7", "Cmaj7", "Fmaj7",
        "Bm7b5", "Cdim", "Am6", "C6",
    )

    @Test fun `every DGBD voicing obeys the shape-quality rules`() {
        for (sym in symbols) {
            val (root, q) = ChordLibrary.parse(sym) ?: continue
            val shapes = gen.shapesFor(root, q, tuning, frets = 14)
            for (sh in shapes) {
                val frets = sh.frets
                val fretted = frets.filterNotNull().filter { it > 0 }
                val hasOpen = frets.any { it == 0 }
                // Rule 1: no open string together with a note above the 3rd fret.
                if (hasOpen && fretted.isNotEmpty()) {
                    assertTrue(fretted.max() <= 3,
                        "$sym $frets: open string combined with a note above fret 3")
                }
                // Rule 2: fretted span <= 5.
                if (fretted.size >= 2) {
                    assertTrue(fretted.max() - fretted.min() <= 5,
                        "$sym $frets: fretted span exceeds 5")
                }
                // Rule 3: at most one muted string.
                assertTrue(sh.mutedCount <= 1, "$sym $frets: more than one muted string")
            }
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

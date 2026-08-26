package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [CagedShapeTable] against the sheet it was transcribed from. A typo in one
 * of the 34 shape strings shows up here as an out-of-scale note, a misplaced root
 * or a hole in the neck coverage — none of which the app itself would complain
 * about.
 */
class CagedShapeTableTest {

    private val std = Tunings.standard
    private val G = PitchClass.G   // low-E fret 3

    private fun pcsOf(mode: CagedMode, subset: ScaleSubset): Set<Int> {
        val degrees = when (mode) {
            CagedMode.Major -> when (subset) {
                ScaleSubset.FullScale -> listOf(0, 2, 4, 5, 7, 9, 11)
                ScaleSubset.Pentatonic -> listOf(0, 2, 4, 7, 9)
                ScaleSubset.Triad -> listOf(0, 4, 7)
            }
            CagedMode.Minor -> when (subset) {
                ScaleSubset.FullScale -> listOf(0, 2, 3, 5, 7, 8, 10)
                ScaleSubset.Pentatonic -> listOf(0, 3, 5, 7, 10)
                ScaleSubset.Triad -> listOf(0, 3, 7)
            }
        }
        return degrees.map { (7 + it) % 12 }.toSet()
    }

    @Test fun `the sheet's 34 diagrams are all present`() {
        assertEquals(34, CagedShapeTable.SHAPES.size)
        // A second fingering exists only for the SCALE of boxes 1 and 4.
        for (box in CagedBox.entries) for (mode in CagedMode.entries) for (subset in ScaleSubset.entries) {
            val expected = if (subset == ScaleSubset.FullScale && (box == CagedBox.POS1 || box == CagedBox.POS4)) 2 else 1
            assertEquals(expected, CagedShapeTable.patternCount(box, mode, subset), "$box $mode $subset")
        }
    }

    @Test fun `every dot in every shape is in the right scale, and roots are roots`() {
        for ((key, dots) in CagedShapeTable.SHAPES) {
            val allowed = pcsOf(key.mode, key.subset)
            for (d in dots) {
                val pc = Fretboard.noteAt(std, FretPosition(d.string, 3 + d.offset)).pitchClass.value
                assertTrue(pc in allowed, "$key: ${d.string}/${d.offset} is pc $pc, not in $allowed")
                assertEquals(pc == G.value, d.isRoot, "$key: root flag wrong at ${d.string}/${d.offset}")
            }
        }
    }

    @Test fun `every shape fits a 22-fret neck in all 12 keys`() {
        for (k in 0 until 12) {
            val key = PitchClass.of(k)
            for (mode in CagedMode.entries) for (subset in ScaleSubset.entries) for (box in CagedBox.entries) {
                for (p in 1..CagedShapeTable.patternCount(box, mode, subset)) {
                    val dots = CagedShapeTable.dots(box, mode, subset, p)
                    val w = CagedScales.window(key, box, std, mode, subset, p)
                    assertTrue(w.first >= 0 && w.last <= 22, "key $k $box $mode $subset p$p lands on frets $w")
                    // resolve() must not silently drop notes off the end of the neck.
                    assertEquals(dots.size, CagedScales.resolve(key, box, mode, subset, std, pattern = p).size,
                        "key $k $box $mode $subset p$p dropped notes")
                }
            }
        }
    }

    @Test fun `the boxes ascend the neck`() {
        for (mode in CagedMode.entries) for (subset in ScaleSubset.entries) {
            val los = CagedBox.entries.map { CagedScales.window(G, it, std, mode, subset).first }
            assertEquals(los.sorted(), los, "$mode $subset boxes are out of order: $los")
        }
    }

    @Test fun `the 5 boxes tile every scale tone between frets 2 and 12`() {
        for (mode in CagedMode.entries) {
            val allowed = pcsOf(mode, ScaleSubset.FullScale)
            val expected = buildSet {
                for (s in 0 until std.stringCount) for (f in 2..12) {
                    if (Fretboard.noteAt(std, FretPosition(s, f)).pitchClass.value in allowed) add(FretPosition(s, f))
                }
            }
            val union = buildSet {
                for (box in CagedBox.entries) for (p in 1..CagedShapeTable.patternCount(box, mode, ScaleSubset.FullScale)) {
                    addAll(CagedScales.resolve(G, box, mode, ScaleSubset.FullScale, std, pattern = p).map { it.position })
                }
            }
            assertTrue(expected.all { it in union }, "$mode leaves holes: ${(expected - union).sortedBy { it.fret }}")
        }
    }

    // ---- The four corrections applied to the sheet (see CagedShapeTable's header) ----

    @Test fun `correction 1 - minor scale box 1 pattern 1 is in G, not A minor`() {
        val notes = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, std, pattern = 1)
        val roots = notes.filter { it.isRoot }.map { it.position }
        assertTrue(FretPosition(0, 3) in roots, "root should sit on the low E at fret 3 (G), not 5 (A)")
        assertTrue(notes.none { Fretboard.noteAt(std, it.position).pitchClass.value == 11 }, "no B natural in G minor")
    }

    @Test fun `correction 2 - minor pentatonic box 1 has D on the A string, not D sharp`() {
        val aString = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.Pentatonic, std)
            .filter { it.position.stringIndex == 1 }.map { it.position.fret }.sorted()
        assertEquals(listOf(3, 5), aString)   // C, D — fret 6 would be D#
    }

    @Test fun `corrections 3 and 4 - minor box 4 is not a copy of box 3`() {
        for (subset in listOf(ScaleSubset.Pentatonic, ScaleSubset.Triad)) {
            val b3 = CagedScales.resolve(G, CagedBox.POS3, CagedMode.Minor, subset, std).map { it.position }.toSet()
            val b4 = CagedScales.resolve(G, CagedBox.POS4, CagedMode.Minor, subset, std).map { it.position }.toSet()
            assertTrue(b3 != b4, "minor $subset box 4 is still box 3's shape")
            assertTrue(b4.minOf { it.fret } > b3.minOf { it.fret }, "minor $subset box 4 should sit above box 3")
        }
        // The low E of the minor triad box 4 is D (fret 10), not C (fret 8).
        val lowE = CagedScales.resolve(G, CagedBox.POS4, CagedMode.Minor, ScaleSubset.Triad, std)
            .filter { it.position.stringIndex == 0 }.map { it.position.fret }
        assertEquals(listOf(10), lowE)
    }

    @Test fun `each triad shape is the triad subset of its own pentatonic shape`() {
        // The relationship every diagram on the sheet obeys, give or take the odd
        // extra reach-back note the sheet adds. Checked as containment of the
        // pentatonic's chord tones, not equality.
        for (box in CagedBox.entries) for (mode in CagedMode.entries) {
            val triadPcs = pcsOf(mode, ScaleSubset.Triad)
            val fromPent = CagedScales.resolve(G, box, mode, ScaleSubset.Pentatonic, std)
                .filter { Fretboard.noteAt(std, it.position).pitchClass.value in triadPcs }
                .map { it.position }.toSet()
            val triad = CagedScales.resolve(G, box, mode, ScaleSubset.Triad, std).map { it.position }.toSet()
            assertTrue(fromPent.all { it in triad }, "$box $mode triad is missing ${fromPent - triad}")
        }
    }
}

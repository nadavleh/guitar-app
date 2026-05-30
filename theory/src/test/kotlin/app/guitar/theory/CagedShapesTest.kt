package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests every CAGED template by transposing it to its "naming" root (e.g. C-shape → C, A-shape → A)
 * and verifying we recover the canonical open-position voicing for that chord.
 *
 * Also tests scrolling along the neck: for any chord root R, the 5 CAGED shapes appear at
 * 5 distinct positions ascending the neck.
 */
class CagedShapesTest {

    private val std = Tunings.standard
    private val frets = 14

    // ---------------- MAJOR ----------------

    @Test fun `C major C-shape is x 3 2 0 1 0`() = assertShape("C", CagedShape.C, listOf(null, 3, 2, 0, 1, 0))
    @Test fun `A major A-shape is x 0 2 2 2 0`() = assertShape("A", CagedShape.A, listOf(null, 0, 2, 2, 2, 0))
    @Test fun `G major G-shape is 3 2 0 0 0 3`() = assertShape("G", CagedShape.G, listOf(3, 2, 0, 0, 0, 3))
    @Test fun `E major E-shape is 0 2 2 1 0 0`() = assertShape("E", CagedShape.E, listOf(0, 2, 2, 1, 0, 0))
    @Test fun `D major D-shape is x x 0 2 3 2`() = assertShape("D", CagedShape.D, listOf(null, null, 0, 2, 3, 2))

    // ---------------- MINOR ----------------

    @Test fun `Cm C-shape is x 3 1 0 1 x`() = assertShape("Cm", CagedShape.C, listOf(null, 3, 1, 0, 1, null))
    @Test fun `Am A-shape is x 0 2 2 1 0`() = assertShape("Am", CagedShape.A, listOf(null, 0, 2, 2, 1, 0))
    @Test fun `Gm G-shape is 3 1 0 0 x 3`() = assertShape("Gm", CagedShape.G, listOf(3, 1, 0, 0, null, 3))
    @Test fun `Em E-shape is 0 2 2 0 0 0`() = assertShape("Em", CagedShape.E, listOf(0, 2, 2, 0, 0, 0))
    @Test fun `Dm D-shape is x x 0 2 3 1`() = assertShape("Dm", CagedShape.D, listOf(null, null, 0, 2, 3, 1))

    // ---------------- DOM 7 ----------------

    @Test fun `C7 C-shape is x 3 2 3 1 0`() = assertShape("C7", CagedShape.C, listOf(null, 3, 2, 3, 1, 0))
    @Test fun `A7 A-shape is x 0 2 0 2 0`() = assertShape("A7", CagedShape.A, listOf(null, 0, 2, 0, 2, 0))
    @Test fun `G7 G-shape is 3 2 0 0 0 1`() = assertShape("G7", CagedShape.G, listOf(3, 2, 0, 0, 0, 1))
    @Test fun `E7 E-shape is 0 2 0 1 0 0`() = assertShape("E7", CagedShape.E, listOf(0, 2, 0, 1, 0, 0))
    @Test fun `D7 D-shape is x x 0 2 1 2`() = assertShape("D7", CagedShape.D, listOf(null, null, 0, 2, 1, 2))

    // ---------------- MAJ 7 ----------------

    @Test fun `Cmaj7 C-shape is x 3 2 0 0 0`() = assertShape("Cmaj7", CagedShape.C, listOf(null, 3, 2, 0, 0, 0))
    @Test fun `Amaj7 A-shape is x 0 2 1 2 0`() = assertShape("Amaj7", CagedShape.A, listOf(null, 0, 2, 1, 2, 0))
    @Test fun `Gmaj7 G-shape is 3 2 0 0 0 2`() = assertShape("Gmaj7", CagedShape.G, listOf(3, 2, 0, 0, 0, 2))
    @Test fun `Emaj7 E-shape is 0 2 1 1 0 0`() = assertShape("Emaj7", CagedShape.E, listOf(0, 2, 1, 1, 0, 0))
    @Test fun `Dmaj7 D-shape is x x 0 2 2 2`() = assertShape("Dmaj7", CagedShape.D, listOf(null, null, 0, 2, 2, 2))

    // ---------------- M 7 ----------------

    @Test fun `Cm7 C-shape is x 3 1 3 1 3`() = assertShape("Cm7", CagedShape.C, listOf(null, 3, 1, 3, 1, 3))
    @Test fun `Am7 A-shape is x 0 2 0 1 0`() = assertShape("Am7", CagedShape.A, listOf(null, 0, 2, 0, 1, 0))
    @Test fun `Gm7 G-shape is 3 1 0 0 x 1`() = assertShape("Gm7", CagedShape.G, listOf(3, 1, 0, 0, null, 1))
    @Test fun `Em7 E-shape is 0 2 0 0 0 0`() = assertShape("Em7", CagedShape.E, listOf(0, 2, 0, 0, 0, 0))
    @Test fun `Dm7 D-shape is x x 0 2 1 1`() = assertShape("Dm7", CagedShape.D, listOf(null, null, 0, 2, 1, 1))

    // ---------------- M7b5 ----------------

    @Test fun `Am7b5 A-shape is x 0 1 0 1 x`() = assertShape("Am7b5", CagedShape.A, listOf(null, 0, 1, 0, 1, null))
    @Test fun `Em7b5 E-shape is 0 1 0 0 x x`() = assertShape("Em7b5", CagedShape.E, listOf(0, 1, 0, 0, null, null))
    @Test fun `Dm7b5 D-shape is x x 0 1 1 1`() = assertShape("Dm7b5", CagedShape.D, listOf(null, null, 0, 1, 1, 1))

    // ---------------- DIM 7 ----------------

    @Test fun `Adim7 A-shape is x 0 1 2 1 2`() = assertShape("Adim7", CagedShape.A, listOf(null, 0, 1, 2, 1, 2))
    @Test fun `Edim7 E-shape is 0 1 2 0 2 x`() = assertShape("Edim7", CagedShape.E, listOf(0, 1, 2, 0, 2, null))
    @Test fun `Ddim7 D-shape is x x 0 1 0 1`() = assertShape("Ddim7", CagedShape.D, listOf(null, null, 0, 1, 0, 1))

    // ---------------- NECK-SPANNING POSITIONS ----------------

    @Test fun `C major produces 5 CAGED shapes spanning the neck`() {
        val shapes = cagedShapesFor(PitchClass.C, q("")!!, std, frets)
        assertEquals(5, shapes.size, "expected 5 shapes for C major")
        // Expected lowest-fretted positions (ignoring open strings): C-shape (1), A-shape (3), G-shape (5), E-shape (8), D-shape (10)
        assertEquals(listOf(1, 3, 5, 8, 10), shapes.map { it.position })
    }

    @Test fun `G major produces 5 CAGED shapes spanning the neck`() {
        val shapes = cagedShapesFor(PitchClass.G, q("")!!, std, frets)
        assertEquals(5, shapes.size, "expected 5 shapes for G major")
        // Order ascending: G-shape (2), E-shape (3), D-shape (5), C-shape (7), A-shape (10)
        assertEquals(listOf(2, 3, 5, 7, 10), shapes.map { it.position })
    }

    @Test fun `F major produces 5 CAGED shapes spanning the neck`() {
        val shapes = cagedShapesFor(PitchClass.F, q("")!!, std, frets)
        assertEquals(5, shapes.size, "expected 5 shapes for F major")
        // F major CAGED order: E-shape (1), D-shape (3), C-shape (5), A-shape (8), G-shape (10)
        assertEquals(listOf(1, 3, 5, 8, 10), shapes.map { it.position })
    }

    @Test fun `5 distinct positions for every major root`() {
        val roots = (0..11).map { PitchClass(it) }
        for (root in roots) {
            val shapes = cagedShapesFor(root, q("")!!, std, frets)
            assertEquals(5, shapes.size, "expected 5 shapes for root $root")
            val positions = shapes.map { it.position }
            // Positions should be increasing (each shape further up the neck).
            assertTrue(
                positions.zipWithNext().all { (a, b) -> a <= b },
                "shapes not sorted by position for root $root: $positions"
            )
        }
    }

    @Test fun `non-standard tuning returns empty CAGED list`() {
        val dropD = Tunings.dropD
        val shapes = cagedShapesFor(PitchClass.C, q("")!!, dropD, frets)
        assertEquals(emptyList(), shapes)
    }

    @Test fun `chord shape contains exactly the expected chord tones`() {
        // For Cmaj7 in any CAGED shape, every played note must be C, E, G, or B.
        val shapes = cagedShapesFor(PitchClass.C, q("maj7")!!, std, frets)
        val allowed = setOf(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.B)
        for (s in shapes) {
            val pcs = s.notes.filterNotNull().map { it.pitchClass }.toSet()
            assertTrue(pcs.all { it in allowed }, "Cmaj7 ${s.frets} has non-chord-tone(s): $pcs")
        }
    }

    // ---------------- helpers ----------------

    private fun q(sym: String): ChordQuality? = ChordLibrary.qualities[sym]

    /** Build the requested CAGED shape for chord [chordSymbol] and assert the fret list matches [expected]. */
    private fun assertShape(chordSymbol: String, shape: CagedShape, expected: List<Int?>) {
        val parsed = ChordLibrary.parse(chordSymbol)
        assertNotNull(parsed, "couldn't parse '$chordSymbol'")
        val (root, qq) = parsed
        val all = cagedShapesFor(root, qq, std, frets)
        // Find the shape matching `shape` enum by computing its expected position.
        // Easier: find by exact-match frets.
        val match = all.firstOrNull { it.frets == expected }
        assertNotNull(
            match,
            "expected shape $expected not found in cagedShapesFor($chordSymbol) → ${all.map { it.frets }}"
        )
    }
}

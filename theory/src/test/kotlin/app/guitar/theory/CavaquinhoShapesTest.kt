package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every curated cavaquinho voicing must:
 *  (a) match the canonical voicing for a representative chord (sanity), and
 *  (b) contain only chord tones of that quality (correctness).
 */
class CavaquinhoShapesTest {

    private val dgbe = Tunings.cavaqDgbe
    private val frets = 14

    // ---- spot-checks for known voicings ----

    @Test fun `Cmaj7 root-pos in DGBe is 5 5 5 7`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("maj7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(5, 5, 5, 7))
    }

    @Test fun `Cmaj7 1st-inv in DGBe is 9 9 8 8`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("maj7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(9, 9, 8, 8))
    }

    @Test fun `Cm7 Freddie-Green in DGBe is 8 8 8 8`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("m7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(8, 8, 8, 8))
    }

    @Test fun `C7 root-pos in DGBe is 5 5 5 6`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(5, 5, 5, 6))
    }

    @Test fun `Cm7b5 root-pos in DGBe is 4 5 4 6`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("m7b5")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(4, 5, 4, 6))
    }

    @Test fun `Cdim7 in DGBe is 1 2 1 2`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("dim7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(1, 2, 1, 2))
    }

    @Test fun `C major A-shape in DGBe is 5 5 5 3`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(5, 5, 5, 3))
    }

    @Test fun `Cm A-shape in DGBe is 5 5 4 3`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("m")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(5, 5, 4, 3))
    }

    @Test fun `Cm E-shape in DGBe is 10 8 8 8`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("m")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(10, 8, 8, 8))
    }

    @Test fun `C6 root-pos in DGBe is 5 5 5 5`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("6")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(5, 5, 5, 5))
    }

    @Test fun `Cm6 root-pos in DGBe is 5 5 4 5`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("m6")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(5, 5, 4, 5))
    }

    @Test fun `Cmaj7 rootless in DGBe is 2 0 0 0`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("maj7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(2, 0, 0, 0))
    }

    @Test fun `Cm7 rootless in DGBe is 1 3 4 3`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("m7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(1, 3, 4, 3))
    }

    @Test fun `C7 rootless in DGBe is 2 3 5 3`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(2, 3, 5, 3))
    }

    @Test fun `Adim7 in DGBe includes the 5-fret-stretch D-rooted voicing 7 5 4 2`() {
        val s = cavaquinhoShapesFor(PitchClass.A, q("dim7")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(7, 5, 4, 2))
    }

    @Test fun `Am7b5 in DGBe includes the 5-fret-stretch D-rooted voicing 7 5 4 3`() {
        val s = cavaquinhoShapesFor(PitchClass.A, q("m7b5")!!, dgbe, frets)
        assertContains(s, listOf<Int?>(7, 5, 4, 3))
    }

    // ---- correctness ----

    @Test fun `every Cmaj7 cavaq voicing contains only chord tones`() {
        val shapes = cavaquinhoShapesFor(PitchClass.C, q("maj7")!!, dgbe, frets)
        val allowed = setOf(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.B)
        for (s in shapes) {
            val pcs = s.notes.filterNotNull().map { it.pitchClass }.toSet()
            assertTrue(pcs.all { it in allowed }, "shape ${s.frets} has non-chord-tone(s): $pcs")
        }
    }

    @Test fun `every C7 cavaq voicing contains only chord tones (C E G Bb)`() {
        val shapes = cavaquinhoShapesFor(PitchClass.C, q("7")!!, dgbe, frets)
        val allowed = setOf(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.As)
        for (s in shapes) {
            val pcs = s.notes.filterNotNull().map { it.pitchClass }.toSet()
            assertTrue(pcs.all { it in allowed }, "shape ${s.frets} has non-chord-tone(s): $pcs")
        }
    }

    // ---- non-curated tunings fall through ----

    @Test fun `cavaquinhoShapesFor returns empty for guitar tuning`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("maj7")!!, Tunings.standard, frets)
        assertEquals(emptyList(), s)
    }

    @Test fun `cavaquinhoShapesFor returns empty for DGBD (Phase 2 only curates DGBe)`() {
        val s = cavaquinhoShapesFor(PitchClass.C, q("maj7")!!, Tunings.cavaqDgbd, frets)
        assertEquals(emptyList(), s)
    }

    // ---- end-to-end: ChordShapeGenerator routes through this for cavaquinho ----

    @Test fun `ChordShapeGenerator on DGBe returns the curated maj7 set`() {
        val gen = ChordShapeGenerator(maxFretSpan = 5)
        val shapes = gen.shapesFor(PitchClass.C, q("maj7")!!, dgbe, frets)
        assertContains(shapes, listOf<Int?>(5, 5, 5, 7))
        assertContains(shapes, listOf<Int?>(9, 9, 8, 8))
    }

    @Test fun `ChordShapeGenerator on DGBD falls back to brute-force (still gets shapes)`() {
        val gen = ChordShapeGenerator(maxFretSpan = 5)
        val shapes = gen.shapesFor(PitchClass.C, q("maj7")!!, Tunings.cavaqDgbd, frets)
        assertTrue(shapes.isNotEmpty(), "no maj7 shapes generated for DGBD")
    }

    // ---- helpers ----

    private fun q(sym: String): ChordQuality? = ChordLibrary.qualities[sym]

    private fun assertContains(shapes: List<ChordShape>, expected: List<Int?>) {
        assertTrue(
            shapes.any { it.frets == expected },
            "expected $expected in ${shapes.map { it.frets }}"
        )
    }
}

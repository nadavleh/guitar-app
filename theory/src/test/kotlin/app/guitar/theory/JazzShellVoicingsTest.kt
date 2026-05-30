package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests the canonical jazz drop-2 voicings. Each chord quality must produce the
 * well-known voicings (cross-checked against jazzguitar.be's chord dictionary).
 */
class JazzShellVoicingsTest {

    private val std = Tunings.standard
    private val frets = 14

    // ---------- Cmaj7 ----------

    @Test fun `Cmaj7 includes drop-2 root-pos x x 5 5 5 7`() =
        assertContainsShape("Cmaj7", listOf(null, null, 5, 5, 5, 7))

    @Test fun `Cmaj7 includes drop-2 1st-inv x x 9 9 8 8`() =
        assertContainsShape("Cmaj7", listOf(null, null, 9, 9, 8, 8))

    @Test fun `Cmaj7 includes drop-2 2nd-inv x x 10 12 12 12`() =
        assertContainsShape("Cmaj7", listOf(null, null, 10, 12, 12, 12))

    @Test fun `Cmaj7 includes drop-2 3rd-inv x x 2 4 1 3`() =
        assertContainsShape("Cmaj7", listOf(null, null, 2, 4, 1, 3))

    @Test fun `Cmaj7 includes middle-4 x 3 5 4 5 x`() =
        assertContainsShape("Cmaj7", listOf(null, 3, 5, 4, 5, null))

    // ---------- Cm7 ----------

    @Test fun `Cm7 includes drop-2 root-pos x x 5 5 4 6`() =
        assertContainsShape("Cm7", listOf(null, null, 5, 5, 4, 6))

    @Test fun `Cm7 includes Freddie-Green x x 8 8 8 8`() =
        assertContainsShape("Cm7", listOf(null, null, 8, 8, 8, 8))

    @Test fun `Cm7 includes drop-2 2nd-inv x x 10 12 11 11`() =
        assertContainsShape("Cm7", listOf(null, null, 10, 12, 11, 11))

    @Test fun `Cm7 includes drop-2 3rd-inv x x 1 3 1 3`() =
        assertContainsShape("Cm7", listOf(null, null, 1, 3, 1, 3))

    @Test fun `Cm7 includes middle-4 x 3 5 3 4 x`() =
        assertContainsShape("Cm7", listOf(null, 3, 5, 3, 4, null))

    // ---------- C7 ----------

    @Test fun `C7 includes drop-2 root-pos x x 5 5 5 6`() =
        assertContainsShape("C7", listOf(null, null, 5, 5, 5, 6))

    @Test fun `C7 includes drop-2 1st-inv x x 8 9 8 8`() =
        assertContainsShape("C7", listOf(null, null, 8, 9, 8, 8))

    @Test fun `C7 includes drop-2 2nd-inv x x 10 12 11 12`() =
        assertContainsShape("C7", listOf(null, null, 10, 12, 11, 12))

    @Test fun `C7 includes drop-2 3rd-inv x x 2 3 1 3`() =
        assertContainsShape("C7", listOf(null, null, 2, 3, 1, 3))

    @Test fun `C7 includes middle-4 x 3 5 3 5 x`() =
        assertContainsShape("C7", listOf(null, 3, 5, 3, 5, null))

    // ---------- Cm7b5 ----------

    @Test fun `Cm7b5 includes drop-2 root-pos x x 4 5 4 6`() =
        assertContainsShape("Cm7b5", listOf(null, null, 4, 5, 4, 6))

    @Test fun `Cm7b5 includes drop-2 1st-inv x x 8 8 7 8`() =
        assertContainsShape("Cm7b5", listOf(null, null, 8, 8, 7, 8))

    @Test fun `Cm7b5 includes drop-2 2nd-inv x x 10 11 11 11`() =
        assertContainsShape("Cm7b5", listOf(null, null, 10, 11, 11, 11))

    @Test fun `Cm7b5 includes drop-2 3rd-inv x x 1 3 1 2`() =
        assertContainsShape("Cm7b5", listOf(null, null, 1, 3, 1, 2))

    // ---------- Cdim7 ----------

    @Test fun `Cdim7 includes top-4 x x 1 2 1 2`() =
        assertContainsShape("Cdim7", listOf(null, null, 1, 2, 1, 2))

    @Test fun `Cdim7 includes A-shape x 3 4 5 4 x`() =
        assertContainsShape("Cdim7", listOf(null, 3, 4, 5, 4, null))

    // ---------- Properties ----------

    @Test fun `every Cmaj7 jazz voicing contains only C E G B`() {
        val shapes = jazzShellVoicingsFor(PitchClass.C, ChordLibrary.qualities["maj7"]!!, std, frets)
        val allowed = setOf(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.B)
        for (s in shapes) {
            val pcs = s.notes.filterNotNull().map { it.pitchClass }.toSet()
            assertTrue(pcs.all { it in allowed }, "voicing ${s.frets} has non-chord-tone(s): $pcs")
        }
    }

    @Test fun `Gmaj7 includes drop-2 root-pos x x 0 0 0 2`() {
        // Same template as Cmaj7 root-pos (rootString=G), now naturally at the open position.
        assertContainsShape("Gmaj7", listOf(null, null, 0, 0, 0, 2))
    }

    @Test fun `Gm7 includes Freddie-Green x x 3 3 3 3`() {
        // The Freddie-Green m7 box transposes cleanly to any root.
        assertContainsShape("Gm7", listOf(null, null, 3, 3, 3, 3))
    }

    @Test fun `non-standard tuning returns empty jazz voicings`() {
        val shapes = jazzShellVoicingsFor(PitchClass.C, ChordLibrary.qualities["maj7"]!!, Tunings.dropD, frets)
        assertEquals(emptyList(), shapes)
    }

    @Test fun `unsupported quality returns empty`() {
        val shapes = jazzShellVoicingsFor(PitchClass.C, ChordLibrary.qualities["sus4"]!!, std, frets)
        assertEquals(emptyList(), shapes)
    }

    // ---------- helpers ----------

    private fun assertContainsShape(chordSymbol: String, expected: List<Int?>) {
        val parsed = ChordLibrary.parse(chordSymbol)
        assertNotNull(parsed, "couldn't parse '$chordSymbol'")
        val (root, q) = parsed
        val all = jazzShellVoicingsFor(root, q, std, frets)
        assertTrue(
            all.any { it.frets == expected },
            "expected shape $expected not found in jazzShellVoicingsFor($chordSymbol) → ${all.map { it.frets }}"
        )
    }
}

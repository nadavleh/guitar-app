package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * True jazz SHELL voicings (root + 3rd + 7th, no 5th) in two shapes — root on the 6th
 * string and root on the 5th string. Cross-checked against jazzguitar.be's shell-chords
 * lesson.
 */
class JazzShellVoicingsTest {

    private val std = Tunings.standard
    private val frets = 14

    // ---------- the three basic shells, 5th-string root (low, unambiguous) ----------

    @Test fun `Cmaj7 shell 5th-string root is x 3 x 4 5 x`() =
        assertContainsShape("Cmaj7", listOf(null, 3, null, 4, 5, null))

    @Test fun `C7 shell 5th-string root is x 3 x 3 5 x`() =
        assertContainsShape("C7", listOf(null, 3, null, 3, 5, null))

    @Test fun `Cm7 shell 5th-string root is x 3 x 3 4 x`() =
        assertContainsShape("Cm7", listOf(null, 3, null, 3, 4, null))

    // ---------- the three basic shells, 6th-string root ----------

    @Test fun `Gmaj7 shell 6th-string root is 3 x 4 4 x x`() =
        assertContainsShape("Gmaj7", listOf(3, null, 4, 4, null, null))

    @Test fun `G7 shell 6th-string root is 3 x 3 4 x x`() =
        assertContainsShape("G7", listOf(3, null, 3, 4, null, null))

    @Test fun `Gm7 shell 6th-string root is 3 x 3 3 x x`() =
        assertContainsShape("Gm7", listOf(3, null, 3, 3, null, null))

    // ---------- shells drop the 5th ----------

    @Test fun `Cmaj7 shell has no 5th - only root, 3rd, 7th`() {
        val shapes = jazzShellVoicingsFor(PitchClass.C, ChordLibrary.qualities["maj7"]!!, std, frets)
        assertTrue(shapes.isNotEmpty())
        val allowed = setOf(PitchClass.C, PitchClass.E, PitchClass.B)   // NO G (the 5th)
        for (s in shapes) {
            val pcs = s.notes.filterNotNull().map { it.pitchClass }.toSet()
            assertEquals(allowed, pcs, "shell ${s.frets} should be exactly root/3rd/7th")
        }
    }

    @Test fun `C7 shell is root, major 3rd, b7 and no 5th`() {
        val shapes = jazzShellVoicingsFor(PitchClass.C, ChordLibrary.qualities["7"]!!, std, frets)
        val allowed = setOf(PitchClass.C, PitchClass.E, PitchClass.of(10)) // C, E(3), Bb (b7) — no G
        for (s in shapes) {
            assertEquals(allowed, s.notes.filterNotNull().map { it.pitchClass }.toSet())
        }
    }

    @Test fun `each shell voicing sounds exactly three strings`() {
        for (sym in listOf("Cmaj7", "C7", "Cm7", "Gmaj7", "Am7", "Fmaj7", "Bb7")) {
            val (root, q) = ChordLibrary.parse(sym)!!
            for (s in jazzShellVoicingsFor(root, q, std, frets)) {
                assertEquals(3, s.frets.count { it != null }, "$sym shell ${s.frets} must be 3 notes")
            }
        }
    }

    // ---------- edge cases ----------

    @Test fun `triads have no shell voicing (fall through to CAGED)`() {
        val shapes = jazzShellVoicingsFor(PitchClass.C, ChordLibrary.qualities[""]!!, std, frets)
        assertEquals(emptyList(), shapes)
    }

    @Test fun `non-standard tuning returns empty shell voicings`() {
        val shapes = jazzShellVoicingsFor(PitchClass.C, ChordLibrary.qualities["maj7"]!!, Tunings.dropD, frets)
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
            "expected shell $expected not found in jazzShellVoicingsFor($chordSymbol) → ${all.map { it.frets }}"
        )
    }
}

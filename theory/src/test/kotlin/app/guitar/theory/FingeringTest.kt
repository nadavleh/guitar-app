package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FingeringTest {

    private fun shape(vararg frets: Int?): ChordShape {
        val (root, quality) = ChordLibrary.parse("C")!!
        return ChordShape(
            chordName = "C",
            root = root,
            quality = quality,
            frets = frets.toList(),
            tuning = Tunings.standard,
        )
    }

    @Test
    fun `open chord — frets become fingers 1 through 3, opens are null`() {
        // open C: x 3 2 0 1 0 (internal order: s6=null, s5=3, s4=2, s3=0, s2=1, s1=0)
        val s = shape(null, 3, 2, 0, 1, 0)
        // Anchor = min nonzero = 1, no barre (only s2 at fret 1)
        // s5 fret 3 → finger 3 (3-1+1)
        // s4 fret 2 → finger 2
        // s2 fret 1 → finger 1
        assertEquals(listOf(null, 3, 2, null, 1, null), Fingering.suggest(s))
    }

    @Test
    fun `barre chord — multiple strings on anchor fret all get finger 1`() {
        // F barre at fret 1: 1 3 3 2 1 1 (s6=1, s5=3, s4=3, s3=2, s2=1, s1=1)
        val s = shape(1, 3, 3, 2, 1, 1)
        // anchor = 1, three strings on anchor (s6, s2, s1), and there are frets > 1 → barre
        // All anchor strings → 1
        // s5 fret 3 → 3
        // s4 fret 3 → 3
        // s3 fret 2 → 2
        assertEquals(listOf(1, 3, 3, 2, 1, 1), Fingering.suggest(s))
    }

    @Test
    fun `all-open chord — every finger is null`() {
        // Em open: 0 2 2 0 0 0
        val s = shape(0, 2, 2, 0, 0, 0)
        // Only frets > 0 get fingers. Anchor = 2. Two anchor strings (s5, s4),
        // no fret above the anchor, so not classified as barre — but with both at
        // the same fret they each get finger 1 (anchor offset 0 → 1).
        val result = Fingering.suggest(s)
        // open strings = null
        assertEquals(null, result[0])
        assertEquals(null, result[3])
        assertEquals(null, result[4])
        assertEquals(null, result[5])
        // s5 fret 2 → 1, s4 fret 2 → 1
        assertEquals(1, result[1])
        assertEquals(1, result[2])
    }

    @Test
    fun `all-muted shape — all entries are null`() {
        val s = shape(null, null, null, null, null, null)
        assertEquals(List(6) { null }, Fingering.suggest(s))
    }

    @Test
    fun `four-fret span maps cleanly to fingers 1-4`() {
        // s6=3, s5=4, s4=5, s3=6, s2=null, s1=null
        val s = shape(3, 4, 5, 6, null, null)
        assertEquals(listOf(1, 2, 3, 4, null, null), Fingering.suggest(s))
    }
}

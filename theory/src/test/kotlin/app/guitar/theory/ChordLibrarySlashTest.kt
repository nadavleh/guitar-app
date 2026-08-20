package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Slash chords and chord-sheet shorthand.
 *
 * The captured transcriptions are full of "D/F#" and site shorthand like "A4".
 * Before this, [ChordLibrary.parse] returned null for 131 of the 272 distinct
 * symbols in that corpus, which would have rendered them as dead rows.
 *
 * The model: a slash chord is an INVERSION. The bass is a tone of the chord that
 * the symbol did not bother to spell, so it is folded in and the inversion index
 * always resolves — "C/D" is Cadd9 with the 9th in the bass, not a special case.
 */
class ChordLibrarySlashTest {

    @Test fun `slash bass parses and keeps the base chord`() {
        val c = ChordLibrary.parseFull("D/F#")
        assertNotNull(c)
        assertEquals(PitchClass.D, c.root)
        assertEquals("", c.quality.symbol)
        assertEquals(PitchClass.Fs, c.bass)
    }

    @Test fun `parse ignores the bass so existing callers are unaffected`() {
        // The fretboard/looper/ear-training callers want the chord, not the bass.
        assertEquals(ChordLibrary.parse("D"), ChordLibrary.parse("D/F#"))
        assertEquals(ChordLibrary.parse("Am7"), ChordLibrary.parse("Am7/G"))
    }

    @Test fun `a chord tone in the bass is an inversion, numbered by chord tone`() {
        // D major = D F# A, so F# is chord tone 1 and A is chord tone 2.
        assertEquals(1, ChordLibrary.parseFull("D/F#")!!.inversion)
        assertEquals(2, ChordLibrary.parseFull("D/A")!!.inversion)
        assertEquals(1, ChordLibrary.parseFull("C/E")!!.inversion)
        assertEquals(2, ChordLibrary.parseFull("C/G")!!.inversion)
        // The 7th in the bass of a 7th chord is the 3rd inversion.
        assertEquals(3, ChordLibrary.parseFull("Bb7/Ab")!!.inversion)
    }

    @Test fun `a 7th in the bass implies the 7th chord and inverts it`() {
        // Sheets write "Bb/Ab" for Bb7 with its own b7 in the bass — a 3rd inversion.
        val c = ChordLibrary.parseFull("Bb/Ab")
        assertNotNull(c)
        assertTrue(c.impliesTone)
        assertEquals("7", c.effectiveQuality.symbol)
        assertEquals(3, c.inversion)
        assertTrue(c.isInversion)
    }

    @Test fun `a major 7th in the bass implies maj7`() {
        val c = ChordLibrary.parseFull("Eb/D")!!
        assertEquals("maj7", c.effectiveQuality.symbol)
        assertEquals(3, c.inversion)
    }

    @Test fun `a minor triad with a 7th in the bass implies m7`() {
        assertEquals("m7", ChordLibrary.parseFull("Am/G")!!.effectiveQuality.symbol)
        assertEquals("mMaj7", ChordLibrary.parseFull("Am/G#")!!.effectiveQuality.symbol)
    }

    @Test fun `a 9th in the bass implies add9`() {
        // The bass is just a note of the chord: D over C is the 9th, so it is Cadd9
        // in 3rd inversion. No separate "pedal" concept is needed for this.
        val c = ChordLibrary.parseFull("C/D")!!
        assertTrue(c.impliesTone)
        assertEquals("add9", c.effectiveQuality.symbol)
        assertEquals(3, c.inversion)
        assertTrue(c.isInversion)
    }

    @Test fun `a 6th in the bass implies the 6 chord`() {
        // Dm/B — B is the 6th of Dm, so Dm6, which is a rootless Bm7b5.
        assertEquals("m6", ChordLibrary.parseFull("Dm/B")!!.effectiveQuality.symbol)
        assertEquals("6", ChordLibrary.parseFull("C/A")!!.effectiveQuality.symbol)
    }

    @Test fun `an 11th in the bass appends the tone`() {
        // F over C is the 11th. No stock quality is named for it, so the tone is
        // appended rather than the chord being silently renamed.
        val c = ChordLibrary.parseFull("C/F")!!
        assertEquals("add11", c.effectiveQuality.symbol)
        assertTrue(c.effectiveQuality.intervals.contains(Interval.P4))
    }

    @Test fun `a written 7th chord is never re-implied`() {
        // The bass is already a chord tone, so the quality must pass through intact.
        val c = ChordLibrary.parseFull("Bb7/Ab")!!
        assertEquals("7", c.effectiveQuality.symbol)
        assertEquals(3, c.inversion)
        assertFalse(c.impliesTone)
    }

    @Test fun `no slash means root position`() {
        val c = ChordLibrary.parseFull("Cmaj7")
        assertNotNull(c)
        assertNull(c.bass)
        assertEquals(0, c.inversion)
        assertFalse(c.isInversion)
        assertFalse(c.impliesTone)
    }

    @Test fun `an unreadable bass rejects the whole symbol`() {
        // Degrading "C/H" to plain C would hide bad data instead of surfacing it.
        assertNull(ChordLibrary.parseFull("C/H"))
        assertNull(ChordLibrary.parse("C/H"))
    }

    @Test fun `site shorthand maps onto the canonical qualities`() {
        assertEquals(ChordLibrary.parse("Asus4"), ChordLibrary.parse("A4"))
        assertEquals(ChordLibrary.parse("Dsus2"), ChordLibrary.parse("D2"))
        assertEquals(ChordLibrary.parse("Amaj7"), ChordLibrary.parse("AM7"))
        assertEquals(ChordLibrary.parse("Aaug"), ChordLibrary.parse("A+"))
    }

    @Test fun `capital M is major and lowercase m is minor`() {
        // The alias table is case-sensitive on purpose; a lowercase compare would
        // collapse these two into one chord.
        assertEquals("maj7", ChordLibrary.parse("AM7")!!.second.symbol)
        assertEquals("m7", ChordLibrary.parse("Am7")!!.second.symbol)
    }

    @Test fun `the power chord has no third`() {
        val q = ChordLibrary.parse("E5")!!.second
        assertEquals(listOf(Interval.P1, Interval.P5), q.intervals)
        assertFalse(q.intervals.contains(Interval.maj3))
        assertFalse(q.intervals.contains(Interval.min3))
    }

    @Test fun `every symbol in the captured corpus parses and inverts`() {
        // A representative slice of the 272 distinct symbols extracted from the
        // saved chord sheets — the shapes that used to fail.
        val corpus = listOf(
            "A/C#", "A/E", "Am/C", "Am7/D", "B7/F#", "Bb7/Ab", "C/E", "C/G", "Cm/G",
            "D/A", "D/F#", "D9/F#", "Dm7/C", "E/G#", "E7/B", "Eb/G", "Em/G", "F/A",
            "F/C", "F7/Eb", "Fm/Ab", "G/B", "G/D", "G7/B", "Gm/Bb", "Ebmmaj7/Gb",
            "A4", "B4", "D2", "E5", "AM7", "A+", "C7sus4", "D7b9", "Eb7b5", "Abm13",
            "F/G", "G/A", "C/D", "Dm/B", "C/F", "Gb/Ab",
        )
        for (s in corpus) {
            val c = ChordLibrary.parseFull(s)
            assertNotNull(c, "'$s' does not parse")
            // Every slash chord resolves to an inversion index inside the chord.
            assertTrue(c.inversion < c.effectiveQuality.intervals.size,
                "$s: inversion ${c.inversion} out of range")
        }
    }

    @Test fun `the bass is always a tone of the effective chord`() {
        for (s in listOf("D/F#", "C/G", "Bb/Ab", "C/D", "Dm/B", "C/F", "G/A")) {
            val c = ChordLibrary.parseFull(s)!!
            assertTrue(c.effectiveQuality.notesFrom(c.root).contains(c.bass),
                "$s: bass is not a tone of ${c.effectiveQuality.symbol}")
        }
    }
}

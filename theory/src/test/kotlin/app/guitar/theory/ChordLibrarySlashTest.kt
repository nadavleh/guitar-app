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

    @Test fun `a non-chord-tone bass is a pedal, not an inversion`() {
        // C major is C E G — D is not in it, so "C/D" is a pedal/added bass. The
        // engine must say "unknown inversion" rather than invent an index.
        val c = ChordLibrary.parseFull("C/D")
        assertNotNull(c)
        assertNull(c.inversion)
        assertFalse(c.isInversion)
    }

    @Test fun `no slash means root position`() {
        val c = ChordLibrary.parseFull("Cmaj7")
        assertNotNull(c)
        assertNull(c.bass)
        assertEquals(0, c.inversion)
        assertFalse(c.isInversion)
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
        val major = ChordLibrary.parse("AM7")!!
        val minor = ChordLibrary.parse("Am7")!!
        assertEquals("maj7", major.second.symbol)
        assertEquals("m7", minor.second.symbol)
    }

    @Test fun `the power chord has no third`() {
        val q = ChordLibrary.parse("E5")!!.second
        assertEquals(listOf(Interval.P1, Interval.P5), q.intervals)
        assertFalse(q.intervals.contains(Interval.maj3))
        assertFalse(q.intervals.contains(Interval.min3))
    }

    @Test fun `every symbol in the captured corpus parses`() {
        // A representative slice of the 272 distinct symbols extracted from the
        // saved chord sheets — the shapes that used to fail.
        val corpus = listOf(
            "A/C#", "A/E", "Am/C", "Am7/D", "B7/F#", "Bb7/Ab", "C/E", "C/G", "Cm/G",
            "D/A", "D/F#", "D9/F#", "Dm7/C", "E/G#", "E7/B", "Eb/G", "Em/G", "F/A",
            "F/C", "F7/Eb", "Fm/Ab", "G/B", "G/D", "G7/B", "Gm/Bb", "Ebmmaj7/Gb",
            "A4", "B4", "D2", "E5", "AM7", "A+", "C7sus4", "D7b9", "Eb7b5", "Abm13",
        )
        for (s in corpus) {
            assertNotNull(ChordLibrary.parse(s), "'$s' does not parse")
            assertNotNull(ChordLibrary.parseFull(s), "'$s' does not parse in full")
        }
    }

    @Test fun `inversion index never exceeds the chord tone count`() {
        for (s in listOf("D/F#", "C/G", "Bb7/Ab", "Dm7/C", "E7/B")) {
            val c = ChordLibrary.parseFull(s)!!
            val inv = c.inversion
            assertNotNull(inv)
            assertTrue(inv < c.quality.intervals.size, "$s: inversion $inv out of range")
        }
    }
}

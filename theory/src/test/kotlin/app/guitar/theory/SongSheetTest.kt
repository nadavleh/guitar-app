package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Transposition and degree labelling for the Songs tab.
 *
 * Mirrored by chorect-web/src/theory/songSheet.ts; verify.ts asserts the same
 * results so the two platforms cannot label the same song differently.
 */
class SongSheetTest {

    // ---------- keys ----------

    @Test fun `a key parses from a chord symbol`() {
        assertEquals(SongSheet.SongKey(PitchClass.G, false), SongSheet.parseKey("G"))
        assertEquals(SongSheet.SongKey(PitchClass.A, true), SongSheet.parseKey("Am"))
        assertEquals(SongSheet.SongKey(PitchClass.As, false), SongSheet.parseKey("Bb"))
        assertEquals(SongSheet.SongKey(PitchClass.Fs, true), SongSheet.parseKey("F#m"))
    }

    @Test fun `a major-seventh key is not mistaken for minor`() {
        // "maj" ends in no "m", but a naive endsWith("m") on "Cmaj" would trip.
        val k = SongSheet.parseKey("Cmaj")
        assertNotNull(k)
        assertFalse(k.minor)
    }

    @Test fun `nonsense is not a key`() {
        assertNull(SongSheet.parseKey(""))
        assertNull(SongSheet.parseKey("H"))
    }

    @Test fun `flat keys prefer flat spelling`() {
        assertTrue(SongSheet.prefersFlats(SongSheet.parseKey("F")))
        assertTrue(SongSheet.prefersFlats(SongSheet.parseKey("Bb")))
        assertFalse(SongSheet.prefersFlats(SongSheet.parseKey("G")))
        assertFalse(SongSheet.prefersFlats(null))
    }

    // ---------- transposition ----------

    @Test fun `transposing keeps the quality`() {
        assertEquals("D", SongSheet.transposeSymbol("C", 2))
        assertEquals("Dm7", SongSheet.transposeSymbol("Cm7", 2))
        assertEquals("Dmaj7", SongSheet.transposeSymbol("Cmaj7", 2))
    }

    @Test fun `transposing moves the slash bass with the chord`() {
        // The whole reason the parser keeps the bass: D/F# up two is E/G#, not E.
        assertEquals("E/G#", SongSheet.transposeSymbol("D/F#", 2))
        assertEquals("C/E", SongSheet.transposeSymbol("G/B", 5))
    }

    @Test fun `transposing can spell flat`() {
        assertEquals("Bb", SongSheet.transposeSymbol("A", 1, flats = true))
        assertEquals("A#", SongSheet.transposeSymbol("A", 1, flats = false))
        // F# up a semitone is G, and G has no flat spelling of its own.
        assertEquals("Eb/G", SongSheet.transposeSymbol("D/F#", 1, flats = true))
    }

    @Test fun `transposing wraps the octave and accepts negatives`() {
        assertEquals("C", SongSheet.transposeSymbol("C", 12))
        assertEquals("B", SongSheet.transposeSymbol("C", -1))
        assertEquals("B", SongSheet.transposeSymbol("C", 11))
    }

    @Test fun `an unparseable symbol transposes to itself`() {
        // A capture oddity must survive the sheet rather than disappearing from it.
        assertEquals("N.C.", SongSheet.transposeSymbol("N.C.", 3))
        assertEquals("%", SongSheet.transposeSymbol("%", 3))
    }

    @Test fun `the key transposes with the chords`() {
        val g = SongSheet.parseKey("G")!!
        assertEquals("A", SongSheet.transposeKey(g, 2))
        val am = SongSheet.parseKey("Am")!!
        assertEquals("Bm", SongSheet.transposeKey(am, 2))
        assertEquals("Bbm", SongSheet.transposeKey(am, 1, flats = true))
    }

    // ---------- degrees ----------

    @Test fun `diatonic chords get their Roman numerals in a major key`() {
        val c = SongSheet.parseKey("C")!!
        assertEquals("I", SongSheet.degreeLabel("C", c))
        assertEquals("ii", SongSheet.degreeLabel("Dm", c))
        assertEquals("iii", SongSheet.degreeLabel("Em", c))
        assertEquals("IV", SongSheet.degreeLabel("F", c))
        assertEquals("V", SongSheet.degreeLabel("G", c))
        assertEquals("vi", SongSheet.degreeLabel("Am", c))
    }

    @Test fun `the quality rides along with the numeral`() {
        val c = SongSheet.parseKey("C")!!
        assertEquals("V7", SongSheet.degreeLabel("G7", c))
        assertEquals("Imaj7", SongSheet.degreeLabel("Cmaj7", c))
        assertEquals("ii7", SongSheet.degreeLabel("Dm7", c))
        assertEquals("viiø7", SongSheet.degreeLabel("Bm7b5", c))
    }

    @Test fun `chromatic chords keep their accidental`() {
        val c = SongSheet.parseKey("C")!!
        assertEquals("bVII", SongSheet.degreeLabel("Bb", c))
        assertEquals("bIII", SongSheet.degreeLabel("Eb", c))
        // A secondary dominant is just a major chord on a non-diatonic degree.
        assertEquals("II7", SongSheet.degreeLabel("D7", c))
    }

    @Test fun `an inversion names the bass degree`() {
        val c = SongSheet.parseKey("C")!!
        // "C/E" is the tonic with its third in the bass.
        assertEquals("I/3", SongSheet.degreeLabel("C/E", c))
        assertEquals("I/5", SongSheet.degreeLabel("C/G", c))
        assertEquals("V/7", SongSheet.degreeLabel("G/B", c))
    }

    @Test fun `a minor key labels its own diatonic set`() {
        val am = SongSheet.parseKey("Am")!!
        assertEquals("i", SongSheet.degreeLabel("Am", am))
        assertEquals("iv", SongSheet.degreeLabel("Dm", am))
        assertEquals("v", SongSheet.degreeLabel("Em", am))
        // In natural minor the bVII is diatonic, so it carries no flat sign.
        assertEquals("VII", SongSheet.degreeLabel("G", am))
        assertEquals("III", SongSheet.degreeLabel("C", am))
    }

    @Test fun `degrees are invariant under transposition`() {
        // The point of the degrees view: the function does not change with the key.
        val c = SongSheet.parseKey("C")!!
        val d = SongSheet.parseKey("D")!!
        val progression = listOf("C", "Am", "F", "G7", "C/E")
        val inC = SongSheet.degreeLabels(progression, c)
        val inD = SongSheet.degreeLabels(progression.map { SongSheet.transposeSymbol(it, 2) }, d)
        assertEquals(inC, inD)
    }

    @Test fun `an unparseable symbol labels as itself`() {
        val c = SongSheet.parseKey("C")!!
        assertEquals("N.C.", SongSheet.degreeLabel("N.C.", c))
    }
}

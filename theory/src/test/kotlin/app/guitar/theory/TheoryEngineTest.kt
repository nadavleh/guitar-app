package app.guitar.theory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PitchClassTest {

    @Test fun `wraps additions past 12`() {
        assertEquals(PitchClass.D, PitchClass.B + 3)   // 11 + 3 â†’ 14 â†’ 2 (D)
        assertEquals(PitchClass.A, PitchClass.C + 9)
    }

    @Test fun `wraps negative additions`() {
        assertEquals(PitchClass.B, PitchClass.C + (-1))
        assertEquals(PitchClass.G, PitchClass.C + (-5))
    }

    @Test fun `subtraction yields the ascending interval`() {
        assertEquals(Interval.P5, PitchClass.G - PitchClass.C)
        assertEquals(Interval.P4, PitchClass.C - PitchClass.G)   // wraps: P5 â†’ P4 inversion
    }

    @Test fun `out-of-range raw constructor throws`() {
        assertThrows<IllegalArgumentException> { PitchClass(12) }
        assertThrows<IllegalArgumentException> { PitchClass(-1) }
    }
}

class NoteTest {

    @Test fun `parse natural notes`() {
        assertEquals(40, Note.parse("E2").midi.value)
        assertEquals(60, Note.parse("C4").midi.value)
        assertEquals(69, Note.parse("A4").midi.value)   // concert pitch
    }

    @Test fun `parse with sharp and flat`() {
        assertEquals(Note.parse("C#4").midi.value, Note.parse("Db4").midi.value)
        assertEquals(Note.parse("F#3").midi.value, Note.parse("Gb3").midi.value)
    }

    @Test fun `octave matches MIDI convention C4 = 60`() {
        assertEquals(4, Note.parse("C4").octave)
        assertEquals(2, Note.parse("E2").octave)
    }

    @Test fun `invalid format throws`() {
        assertThrows<IllegalArgumentException> { Note.parse("H4") }
        assertThrows<IllegalArgumentException> { Note.parse("C") }
    }
}

class NoteSpellerTest {

    @Test fun `sharp spelling default`() {
        assertEquals("C#", NoteSpeller.spell(PitchClass.Cs))
        assertEquals("F#", NoteSpeller.spell(PitchClass.Fs))
    }

    @Test fun `flat spelling when preferred`() {
        assertEquals("Db", NoteSpeller.spell(PitchClass.Cs, Accidental.FLAT))
        assertEquals("Bb", NoteSpeller.spell(PitchClass.As, Accidental.FLAT))
    }

    @Test fun `naturals spell the same way regardless of preference`() {
        assertEquals("C", NoteSpeller.spell(PitchClass.C, Accidental.SHARP))
        assertEquals("C", NoteSpeller.spell(PitchClass.C, Accidental.FLAT))
        assertEquals("E", NoteSpeller.spell(PitchClass.E, Accidental.SHARP))
        assertEquals("E", NoteSpeller.spell(PitchClass.E, Accidental.FLAT))
    }

    @Test fun `parse enharmonic equivalents`() {
        assertEquals(PitchClass.Cs, NoteSpeller.parsePitchClass("C#"))
        assertEquals(PitchClass.Cs, NoteSpeller.parsePitchClass("Db"))
        assertEquals(PitchClass.As, NoteSpeller.parsePitchClass("A#"))
        assertEquals(PitchClass.As, NoteSpeller.parsePitchClass("Bb"))
    }
}

class FretboardTest {

    @Test fun `open strings match tuning`() {
        for (s in 0 until Tunings.standard.stringCount) {
            val open = Fretboard.noteAt(Tunings.standard, FretPosition(s, 0))
            assertEquals(Tunings.standard.openStrings[s], open)
        }
    }

    @Test fun `12th fret is one octave up from open`() {
        for (s in 0 until Tunings.standard.stringCount) {
            val open = Fretboard.noteAt(Tunings.standard, FretPosition(s, 0))
            val twelfth = Fretboard.noteAt(Tunings.standard, FretPosition(s, 12))
            assertEquals(open.midi.value + 12, twelfth.midi.value)
            assertEquals(open.pitchClass, twelfth.pitchClass)
        }
    }

    @Test fun `out-of-range string index throws`() {
        assertThrows<IllegalArgumentException> {
            Fretboard.noteAt(Tunings.standard, FretPosition(stringIndex = 6, fret = 0))
        }
    }

    @Test fun `allPositions finds every occurrence of a pitch class`() {
        val gPositions = Fretboard.allPositions(Tunings.standard, frets = 12, of = PitchClass.G)
        // Open G3 string (index 3), low E + 3 (3rd fret of string 6), etc.
        assertTrue(gPositions.contains(FretPosition(stringIndex = 0, fret = 3)))   // G2 on low E
        assertTrue(gPositions.contains(FretPosition(stringIndex = 3, fret = 0)))   // G3 open G
        assertTrue(gPositions.contains(FretPosition(stringIndex = 5, fret = 3)))   // G4 on high E
    }
}

class ChordLibraryTest {

    @Test fun `parse simple major chord`() {
        val parsed = ChordLibrary.parse("D")
        assertNotNull(parsed)
        val (root, quality) = parsed
        assertEquals(PitchClass.D, root)
        assertEquals(listOf(PitchClass.D, PitchClass.Fs, PitchClass.A), quality.notesFrom(root))
    }

    @Test fun `parse Cmaj7`() {
        val parsed = ChordLibrary.parse("Cmaj7")
        assertNotNull(parsed)
        val (root, quality) = parsed
        assertEquals(PitchClass.C, root)
        assertEquals(listOf(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.B), quality.notesFrom(root))
    }

    @Test fun `parse sharp root`() {
        val parsed = ChordLibrary.parse("F#m7")
        assertNotNull(parsed)
        assertEquals(PitchClass.Fs, parsed.first)
        assertEquals(4, parsed.second.intervals.size)
    }

    @Test fun `parse flat root`() {
        val parsed = ChordLibrary.parse("Bbmaj7")
        assertNotNull(parsed)
        assertEquals(PitchClass.As, parsed.first)
    }

    @Test fun `unknown quality returns null`() {
        assertNull(ChordLibrary.parse("Cxyzzy"))
    }

    @Test fun `empty input returns null`() {
        assertNull(ChordLibrary.parse(""))
        assertNull(ChordLibrary.parse("   "))
    }

    @Test fun `m7b5 has flat-five`() {
        val (_, quality) = ChordLibrary.parse("Cm7b5")!!
        val notes = quality.notesFrom(PitchClass.C)
        assertEquals(listOf(PitchClass.C, PitchClass.Ds, PitchClass.Fs, PitchClass.As), notes)
    }
}

class ScaleLibraryTest {

    @Test fun `C major scale notes`() {
        val major = ScaleLibrary.scales["major"]!!
        val notes = major.notesFrom(PitchClass.C)
        assertEquals(
            listOf(PitchClass.C, PitchClass.D, PitchClass.E, PitchClass.F,
                   PitchClass.G, PitchClass.A, PitchClass.B),
            notes
        )
    }

    @Test fun `G mixolydian has b7`() {
        val mixo = ScaleLibrary.scales["mixolydian"]!!
        val notes = mixo.notesFrom(PitchClass.G)
        assertTrue(notes.contains(PitchClass.F))    // b7 of G is F natural
        assertTrue(!notes.contains(PitchClass.Fs))  // no F#
    }

    @Test fun `blues scale has the blue note`() {
        val blues = ScaleLibrary.scales["blues"]!!
        val notes = blues.notesFrom(PitchClass.A)
        // A C D D# E G
        assertEquals(6, notes.size)
        assertTrue(notes.contains(PitchClass.Ds))   // blue note
    }
}

class TuningChangeTest {

    @Test fun `changing to drop D affects only string 6 open note`() {
        val standard = Tunings.standard
        val dropD = Tunings.dropD
        // Strings 5-1 (indices 1-5) unchanged
        for (i in 1 until standard.stringCount) {
            assertEquals(standard.openStrings[i], dropD.openStrings[i])
        }
        // String 6 (index 0) dropped from E2 to D2
        assertEquals(Note.parse("E2"), standard.openStrings[0])
        assertEquals(Note.parse("D2"), dropD.openStrings[0])
    }

    @Test fun `fretboard notes update with tuning`() {
        val standardFret5 = Fretboard.noteAt(Tunings.standard, FretPosition(0, 5))
        val dropDFret5 = Fretboard.noteAt(Tunings.dropD, FretPosition(0, 5))
        // String 6 fret 5 in standard = A; in drop D = G
        assertEquals(PitchClass.A, standardFret5.pitchClass)
        assertEquals(PitchClass.G, dropDFret5.pitchClass)
    }
}

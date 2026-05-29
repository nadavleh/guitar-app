package app.guitar.theory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests that correspond 1:1 with requirements.md Â§13.
 * String numbering follows guitar convention: string 6 = lowest pitch (low E in standard).
 * In code, openStrings[0] = string 6.
 */
class RequirementsTest {

    @Test
    @DisplayName("Â§13.1 Standard tuning generates correct open-string notes")
    fun standardTuningOpenStrings() {
        val expected = listOf("E2", "A2", "D3", "G3", "B3", "E4")
        val actual = Tunings.standard.openStrings.map { NoteSpeller.spell(it) }
        assertEquals(expected, actual)
    }

    @Test
    @DisplayName("Â§13.2 6th string, 3rd fret in standard tuning is G")
    fun sixthStringThirdFretIsG() {
        val pos = FretPosition(stringIndex = 0, fret = 3)
        val note = Fretboard.noteAt(Tunings.standard, pos)
        assertEquals(PitchClass.G, note.pitchClass)
    }

    @Test
    @DisplayName("Â§13.3 5th string, 3rd fret in standard tuning is C")
    fun fifthStringThirdFretIsC() {
        val pos = FretPosition(stringIndex = 1, fret = 3)
        val note = Fretboard.noteAt(Tunings.standard, pos)
        assertEquals(PitchClass.C, note.pitchClass)
    }

    @Test
    @DisplayName("Â§13.4 C major chord returns C, E, G")
    fun cMajorChordNotes() {
        val parsed = ChordLibrary.parse("C")
        assertNotNull(parsed, "C should parse")
        val (root, quality) = parsed
        assertEquals(listOf(PitchClass.C, PitchClass.E, PitchClass.G), quality.notesFrom(root))
    }

    @Test
    @DisplayName("Â§13.5 A minor pentatonic returns A, C, D, E, G")
    fun aMinorPentatonicNotes() {
        val scale = ScaleLibrary.scales["minor pentatonic"]
        assertNotNull(scale, "minor pentatonic should exist")
        val notes = scale.notesFrom(PitchClass.A)
        assertEquals(listOf(PitchClass.A, PitchClass.C, PitchClass.D, PitchClass.E, PitchClass.G), notes)
    }

    @Test
    @DisplayName("Â§13.6 Drop D tuning 6th string open is D2")
    fun dropDLowestStringIsD2() {
        val pos = FretPosition(stringIndex = 0, fret = 0)
        val note = Fretboard.noteAt(Tunings.dropD, pos)
        assertEquals(PitchClass.D, note.pitchClass)
        assertEquals(2, note.octave)
    }

    @Test
    @DisplayName("Â§13.7 Chord shapes do not exceed the maximum fret span")
    fun chordShapesRespectMaxFretSpan() {
        val gen = ChordShapeGenerator(maxFretSpan = 4)
        val cases = listOf("C", "Am", "G", "D", "Cmaj7", "F#m7", "Bbdim7", "E7", "Dsus4", "Caug")
        for (symbol in cases) {
            val parsed = ChordLibrary.parse(symbol)
            assertNotNull(parsed, "expected $symbol to parse")
            val (root, quality) = parsed
            val shapes = gen.shapesFor(root, quality, Tunings.standard, frets = 14)
            for (s in shapes) {
                kotlin.test.assertTrue(
                    s.fretSpan <= gen.maxFretSpan,
                    "$symbol shape ${s.frets} has span ${s.fretSpan} > ${gen.maxFretSpan}"
                )
            }
        }
    }

    @Test
    @DisplayName("Â§13.8 Scale display updates correctly after tuning changes")
    fun scaleDisplayUpdatesWithTuning() {
        val major = ScaleLibrary.scales["major"]!!
        val standard = FretboardOverlay.scale(PitchClass.C, major, Tunings.standard, numFrets = 12)
        val dropD = FretboardOverlay.scale(PitchClass.C, major, Tunings.dropD, numFrets = 12)
        // Same scale + root → same pitch-class set, but different POSITIONS on the neck
        assertEquals(
            standard.values.map { it.pitchClass }.toSet(),
            dropD.values.map { it.pitchClass }.toSet()
        )
        kotlin.test.assertTrue(
            standard.keys != dropD.keys,
            "scale positions should change with tuning"
        )
    }
}

package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CavaquinhoTuningsTest {

    @Test fun `DGBe has 4 strings`() {
        assertEquals(4, Tunings.cavaqDgbe.stringCount)
    }

    @Test fun `DGBD has 4 strings`() {
        assertEquals(4, Tunings.cavaqDgbd.stringCount)
    }

    @Test fun `DGBe open pitches are D4 G4 B4 E5`() {
        val notes = Tunings.cavaqDgbe.openStrings
        // String 0 = lowest = D4
        assertEquals(Note.parse("D4"), notes[0])
        assertEquals(Note.parse("G4"), notes[1])
        assertEquals(Note.parse("B4"), notes[2])
        assertEquals(Note.parse("E5"), notes[3])
    }

    @Test fun `DGBD top string is one octave above the bottom`() {
        val notes = Tunings.cavaqDgbd.openStrings
        assertEquals(Note.parse("D4"), notes[0])
        assertEquals(Note.parse("D5"), notes[3])
        assertEquals(12, notes[3].midi.value - notes[0].midi.value)
    }

    @Test fun `presetsFor Guitar returns 6-string tunings only`() {
        val presets = Tunings.presetsFor(Instrument.Guitar)
        assertTrue(presets.isNotEmpty())
        for ((_, t) in presets) {
            assertEquals(6, t.stringCount)
        }
    }

    @Test fun `presetsFor Cavaquinho returns 4-string tunings only`() {
        val presets = Tunings.presetsFor(Instrument.Cavaquinho)
        assertTrue(presets.isNotEmpty())
        for ((_, t) in presets) {
            assertEquals(4, t.stringCount)
        }
    }

    @Test fun `defaultFor Cavaquinho is DGBe`() {
        assertEquals(Tunings.cavaqDgbe, Tunings.defaultFor(Instrument.Cavaquinho))
        assertEquals("DGBe", Tunings.defaultNameFor(Instrument.Cavaquinho))
    }

    @Test fun `chord shape generator works on cavaquinho tunings with span 5`() {
        // Adim7 in DGBe — the user's example
        val gen = ChordShapeGenerator(maxFretSpan = 5)
        val shapes = gen.shapesFor(
            root = PitchClass.A,
            quality = ChordLibrary.qualities["dim7"]!!,
            tuning = Tunings.cavaqDgbe,
            frets = 14,
        )
        assertTrue(shapes.isNotEmpty(), "no shapes generated for Adim7 in DGBe")
        // The chord should have at least one voicing with the root on the lowest D string
        val rootOnD = shapes.any { shape ->
            val f = shape.frets[0]
            f != null && ((Tunings.cavaqDgbe.openStrings[0].pitchClass.value + f) % 12) ==
                PitchClass.A.value
        }
        assertTrue(rootOnD, "no Adim7 voicing places the root on the cavaquinho's D string")
    }
}

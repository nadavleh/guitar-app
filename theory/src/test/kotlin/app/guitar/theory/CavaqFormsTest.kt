package app.guitar.theory

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("cavaq")
class CavaqFormsTest {
    private val dgbd = Tunings.cavaqDgbd
    private fun q(s: String) = ChordLibrary.qualities[s]!!
    private fun forms(root: Int, sym: String) = CavaqForms.formsFor(PitchClass(root), q(sym), dgbd)

    @Test fun `Dm has the three forms - b3, 5 and R in the bass`() {
        val f = forms(2, "m")
        assertEquals(listOf(3, 7, 0), f.map { it.bass })
        assertEquals(listOf(listOf(3, 2, 3, 3), listOf(7, 7, 6, 7), listOf(12, 10, 10, 12)), f.map { it.shape.frets })
    }

    @Test fun `every triad has 3 forms and every four-note chord 4, in every key`() {
        for (r in 0..11) {
            for (sym in listOf("", "m", "dim", "aug", "sus4"))
                assertEquals(3, CavaqForms.formsFor(PitchClass(r), q(sym), dgbd).size, "$r$sym")
            for (sym in listOf("7", "maj7", "m7", "m7b5", "dim7", "6", "m6", "9", "mMaj7"))
                assertEquals(4, CavaqForms.formsFor(PitchClass(r), q(sym), dgbd).size, "$r$sym")
            // R 3 ♭7 13 has no 5-fret grip for one bass in some keys.
            assertTrue(CavaqForms.formsFor(PitchClass(r), q("13"), dgbd).size >= 3, "${r}13")
        }
    }

    @Test fun `every form sounds exactly the form tones, bass as labelled`() {
        for (sym in CavaqForms.QUALITIES) for (r in 0..11) {
            val tones = CavaqForms.formTones(q(sym)).map { (it + r) % 12 }.toSet()
            for (f in CavaqForms.formsFor(PitchClass(r), q(sym), dgbd)) {
                val midis = f.shape.notes.map { it!!.midi.value }
                assertEquals(tones, midis.map { it % 12 }.toSet(), "$r$sym ${f.shape.frets}")
                assertEquals(f.bass, ((midis.min() % 12 - r) % 12 + 12) % 12)
            }
        }
    }

    @Test fun `open-string copy of a fretted grip is dropped`() {
        val dm = q("m"); val d = PitchClass(2)
        val a = ChordShape("Dm", d, dm, listOf(0, 2, 3, 3), dgbd)
        val b = ChordShape("Dm", d, dm, listOf(3, 2, 3, 3), dgbd)
        val c = ChordShape("Dm", d, dm, listOf(7, 7, 6, 7), dgbd)
        assertEquals(listOf(b, c), CavaqForms.dropOpenStringCopies(listOf(a, b, c)))
        assertTrue(CavaqForms.dropOpenStringCopies(listOf(a, c)).contains(a))
    }

    @Test fun `C major 5 5 5 5 branches out to 2 5 5 5 (3rd in the bass)`() {
        val main = forms(0, "").first { it.shape.frets == listOf(5, 5, 5, 5) }
        assertTrue(listOf(2, 5, 5, 5) in main.branches.map { it.frets }, "${main.branches.map { it.frets }}")
        assertEquals("3 in the bass", main.branches.first { it.frets == listOf(2, 5, 5, 5) }.templateName)
    }
}

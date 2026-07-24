package app.guitar.theory

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CavaqSequencesTest {

    @Test fun `quadradinho resolves to C A7 Dm G7 in C`() {
        val q = CavaqSequences.byId("quadradinho_maj")
        assertNotNull(q)
        val chords = q.prog.resolve(PitchClass.C).map { it.symbol }
        assertEquals(listOf("C", "A7", "Dm", "G7"), chords)
    }

    @Test fun `basic minor resolves to Cm C7 Fm G7 in C`() {
        val chords = CavaqSequences.byId("basic_min")!!.prog.resolve(PitchClass.C).map { it.symbol }
        assertEquals(listOf("Cm", "C7", "Fm", "G7"), chords)
    }

    @Test fun `medio major has 13 chords starting and ending on I`() {
        val medio = CavaqSequences.byId("medio_maj")!!
        val chords = medio.prog.resolve(PitchClass.C).map { it.symbol }
        assertEquals(13, chords.size)
        assertEquals("C", chords.first())
        assertEquals("C", chords.last())
    }

    @Test fun `medio minor resolves to the Betto Correa extended minor sequence in C`() {
        val chords = CavaqSequences.byId("medio_min")!!.prog.resolve(PitchClass.C).map { it.symbol }
        assertEquals(listOf("Cm", "C7", "Fm", "Bb7", "Eb", "Ab", "Dm7b5", "G7", "Cm"), chords)
    }

    @Test fun `II-V-I sequence was removed`() {
        assertEquals(null, CavaqSequences.byId("ii_v_i_maj"))
    }

    @Test fun `transposing quadradinho to D gives D B7 Em A7`() {
        val chords = CavaqSequences.byId("quadradinho_maj")!!.prog.resolve(PitchClass.D).map { it.symbol }
        assertEquals(listOf("D", "B7", "Em", "A7"), chords)
    }

    @Test fun `all sequences have unique ids and non-empty names`() {
        val ids = CavaqSequences.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(CavaqSequences.ALL.all { it.namePt.isNotBlank() && it.nameEn.isNotBlank() })
    }

    @Test fun `cavaquinho voicing pool offers rootless and shell dominant grips`() {
        val (root, q) = ChordLibrary.parse("G7")!!
        val pool = cavaquinhoVoicingPool(root, q, Tunings.cavaqDgbd, maxFret = 14)
        assertTrue(pool.any { it.templateName == "complete" }, "expected complete voicings")
        assertTrue(pool.any { it.templateName == "rootless" }, "expected rootless voicings")
        assertTrue(pool.any { it.templateName == "shell" }, "expected no-5th shell voicings")
        // A rootless dominant is the upper-structure dim triad on the 3rd (no root sounding).
        val rootless = pool.first { it.templateName == "rootless" }
        assertTrue(rootless.notes.none { it != null && it.pitchClass == root })
    }

    @Test fun `quadradinho VI7 voice-leads to a rootless grip on the cavaquinho`() {
        // Key G quadradinho I VI7: G [5,4,3,5] -> E7. The least-motion pick from the
        // enriched pool is the rootless E7 (G#dim upper structure) 6-4-3-6 — matching
        // Nadav's "Quadradinhos" sheet, which chose voicings by minimal finger motion.
        val cavaq = Tunings.cavaqDgbd
        val gMajor = ChordLibrary.parse("G")!!
        val gShape = ChordShape("G", gMajor.first, gMajor.second, listOf(5, 4, 3, 5), cavaq)
        val (e7root, e7q) = ChordLibrary.parse("E7")!!
        val pool = cavaquinhoVoicingPool(e7root, e7q, cavaq, maxFret = 14)
        val chosen = pool[VoiceLeading.pickMinMovement(gShape, pool)]
        assertEquals(listOf<Int?>(6, 4, 3, 6), chosen.frets)
        assertEquals("rootless", chosen.templateName)
    }
}

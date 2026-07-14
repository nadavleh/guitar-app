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

    @Test fun `transposing quadradinho to D gives D B7 Em A7`() {
        val chords = CavaqSequences.byId("quadradinho_maj")!!.prog.resolve(PitchClass.D).map { it.symbol }
        assertEquals(listOf("D", "B7", "Em", "A7"), chords)
    }

    @Test fun `all sequences have unique ids and non-empty names`() {
        val ids = CavaqSequences.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(CavaqSequences.ALL.all { it.namePt.isNotBlank() && it.nameEn.isNotBlank() })
    }
}

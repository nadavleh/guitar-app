package app.guitar.theory

import org.junit.jupiter.api.Tag
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

@Tag("cavaq")
class CavaqSequencesTest {

    @Test fun `quadradinho resolves to C A7 Dm G7 in C`() {
        val q = CavaqSequences.byId("quadradinho_maj")
        assertNotNull(q)
        val chords = q.prog.resolve(PitchClass.C).map { it.symbol }
        assertEquals(listOf("C", "A7", "Dm", "G7"), chords)
    }

    @Test fun `sou mais o samba A and B resolve in C`() {
        assertEquals(listOf("Dm", "G7", "C", "A7", "Dm", "G7", "C", "G7"),
            CavaqSequences.byId("sou_mais_o_samba_a")!!.prog.resolve(PitchClass.C).map { it.symbol })
        assertEquals(listOf("C", "Dm", "G7", "C"),
            CavaqSequences.byId("sou_mais_o_samba_b")!!.prog.resolve(PitchClass.C).map { it.symbol })
        assertEquals(listOf("quadradinho_maj", "sou_mais_o_samba_a", "sou_mais_o_samba_b"),
            CavaqSequences.ALL.take(3).map { it.id })
        assertEquals(PitchClass.C, CavaqSequences.byId("sou_mais_o_samba_a")!!.defaultKey)
        assertEquals(PitchClass.C, CavaqSequences.byId("sou_mais_o_samba_b")!!.defaultKey)
        assertEquals(null, CavaqSequences.byId("quadradinho_maj")!!.defaultKey)
    }

    @Test fun `sou mais o samba extended A and B resolve and voice on the cavaquinho`() {
        val a = CavaqSequences.byId("sou_mais_o_samba_a_ext")!!.prog.resolve(PitchClass.C).map { it.symbol }
        assertEquals(listOf("Dm9", "G13", "C6add9", "A7b9", "Dm9", "G7b9", "C6add9", "G7"), a)
        val b = CavaqSequences.byId("sou_mais_o_samba_b_ext")!!.prog.resolve(PitchClass.C).map { it.symbol }
        assertEquals(listOf("C6add9", "Dm9", "G13", "C6", "Cdim7", "Dm9"), b)
        val cavaq = Tunings.cavaqDgbd
        fun pool(sym: String): List<List<Int?>> {
            val (r, q) = ChordLibrary.parse(sym)!!
            return cavaquinhoVoicingPool(r, q, cavaq, maxFret = 14).map { it.frets }
        }
        for (sym in (a + b).toSet()) assertTrue(pool(sym).isNotEmpty(), "no cavaquinho grip for $sym")
        // The grips from the ChatGPT chart are all in the pool.
        assertTrue(listOf<Int?>(3, 2, 1, 2) in pool("Dm9"))   // F A C E, rootless
        assertTrue(listOf<Int?>(3, 2, 0, 2) in pool("G13"))   // F A B E, rootless
        assertTrue(listOf<Int?>(2, 2, 1, 0) in pool("C6add9")) // E A C D, no 5th
        assertTrue(listOf<Int?>(5, 3, 2, 2) in pool("A7b9"))  // G Bb C# E, rootless
        assertTrue(listOf<Int?>(3, 1, 0, 0) in pool("G7b9"))  // F Ab B D, rootless
        assertTrue(listOf<Int?>(2, 2, 1, 2) in pool("C6"))
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

package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceLeadingTest {

    private val gen = ChordShapeGenerator()
    private val std = Tunings.standard

    /** The user's example: from G major in the E-shape barre (3 5 5 4 3 3), the
     *  voicing of C picked by min-movement must be the A-shape barre (x 3 5 5 5 3),
     *  NOT the high E-shape barre (8 10 10 9 8 8) and NOT the open C (x 3 2 0 1 0). */
    @Test fun `G E-shape → C picks the A-shape barre (the user's example)`() {
        val g = gen.shapesFor(PitchClass.G, q("")!!, std, 14)
            .first { it.cagedShape == CagedShape.E }
        assertEquals(listOf(3, 5, 5, 4, 3, 3), g.frets)
        val cCands = gen.shapesFor(PitchClass.C, q("")!!, std, 14)
        val pick = cCands[VoiceLeading.pickMinMovement(g, cCands)]
        assertEquals(CagedShape.A, pick.cagedShape, "expected A-shape (x 3 5 5 5 3), got ${pick.frets}")
        assertEquals(listOf<Int?>(null, 3, 5, 5, 5, 3), pick.frets)
    }

    /** Round trip — from C in the A-shape (x 3 5 5 5 3), back to G should land on the
     *  E-shape G barre (3 5 5 4 3 3) — its closest neighbor by movement. */
    @Test fun `round trip — A-shape C → G picks E-shape G barre`() {
        val cs = gen.shapesFor(PitchClass.C, q("")!!, std, 14)
            .first { it.cagedShape == CagedShape.A }
        val gCands = gen.shapesFor(PitchClass.G, q("")!!, std, 14)
        val pick = gCands[VoiceLeading.pickMinMovement(cs, gCands)]
        assertEquals(CagedShape.E, pick.cagedShape)
        assertEquals(listOf<Int?>(3, 5, 5, 4, 3, 3), pick.frets)
    }

    /** A simple sanity check: zero cost when comparing a shape to itself. */
    @Test fun `cost of shape to itself is zero`() {
        val s = gen.shapesFor(PitchClass.D, q("m7")!!, std, 14).first()
        assertEquals(0, VoiceLeading.movementCost(s, s))
    }

    /** Mute-vs-played transitions count the penalty. */
    @Test fun `mute penalty is applied per transitioning string`() {
        // Two synthetic shapes: same chord (won't be used as a chord here, just as a vehicle).
        val open = ChordShape(
            chordName = "C", root = PitchClass.C, quality = q("")!!,
            frets = listOf(null, 3, 2, 0, 1, 0), tuning = std,
        )
        val muted = ChordShape(
            chordName = "C", root = PitchClass.C, quality = q("")!!,
            frets = listOf(null, null, null, 0, 1, 0), tuning = std,
        )
        // Strings 1 (3→muted) and 2 (2→muted) flip mute state; others identical.
        assertEquals(3 + 3, VoiceLeading.movementCost(open, muted, mutePenalty = 3))
    }

    /** When moving through a 4-chord progression, voice-leading should keep the
     *  total cost lower than just always picking the E-shape. */
    @Test fun `voice-leading total cost beats always-E-shape for I-vi-IV-V`() {
        val progression = listOf(
            PitchClass.C to "" to "I",
            PitchClass.A to "m" to "vi",
            PitchClass.F to "" to "IV",
            PitchClass.G to "" to "V",
        )

        // Path A: always E-shape (when available, else fallback to first)
        var totalE = 0
        var prevE: ChordShape? = null
        for ((rk, _) in progression) {
            val (root, qSym) = rk
            val candidates = gen.shapesFor(root, q(qSym)!!, std, 14)
            val pick = candidates.firstOrNull { it.cagedShape == CagedShape.E } ?: candidates.first()
            if (prevE != null) totalE += VoiceLeading.movementCost(prevE!!, pick)
            prevE = pick
        }

        // Path B: voice-leading
        var totalV = 0
        var prevV: ChordShape? = null
        for ((rk, _) in progression) {
            val (root, qSym) = rk
            val candidates = gen.shapesFor(root, q(qSym)!!, std, 14)
            val pick = if (prevV == null) {
                candidates.firstOrNull { it.cagedShape == CagedShape.E } ?: candidates.first()
            } else {
                candidates[VoiceLeading.pickMinMovement(prevV!!, candidates)]
            }
            if (prevV != null) totalV += VoiceLeading.movementCost(prevV!!, pick)
            prevV = pick
        }

        assertTrue(totalV <= totalE,
            "voice-leading total ($totalV) should be ≤ always-E-shape total ($totalE)")
    }

    private fun q(sym: String): ChordQuality? = ChordLibrary.qualities[sym]
}

package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The API around [CagedShapeTable]: transposition, the guided run's step order,
 * and the triad inversions. The shapes themselves are pinned by
 * [CagedShapeTableTest].
 */
class CagedScalesTest {

    private val std = Tunings.standard
    private val G = PitchClass.G   // 7

    @Test fun `box 1 in G major occupies frets 2 to 5, rooted on the low E at 3`() {
        assertEquals(2..5, CagedScales.window(G, CagedBox.POS1, std))
        // Low-E string notes of box 1 (G major) are F#(2) G(3) A(5) — Δ7, R, Δ2.
        val notes = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, std)
        assertEquals(listOf(2, 3, 5), notes.filter { it.position.stringIndex == 0 }.map { it.position.fret })
        assertTrue(notes.any { it.position == FretPosition(0, 3) && it.isRoot })
    }

    @Test fun `the boxes are named for their CAGED chord shape, E D C A G`() {
        assertEquals(listOf("E", "D", "C", "A", "G"), CagedBox.entries.map { it.cagedShape })
        assertEquals(listOf(1, 2, 3, 4, 5), CagedBox.entries.map { it.number })
    }

    @Test fun `transposing a box just slides it - shape and intervals are identical`() {
        val inG = CagedScales.resolve(G, CagedBox.POS2, CagedMode.Major, ScaleSubset.FullScale, std)
        val inA = CagedScales.resolve(PitchClass.A, CagedBox.POS2, CagedMode.Major, ScaleSubset.FullScale, std)
        assertEquals(inG.map { it.position.stringIndex }, inA.map { it.position.stringIndex })
        assertEquals(inG.map { it.position.fret + 2 }, inA.map { it.position.fret })
        assertEquals(inG.map { it.interval }, inA.map { it.interval })
    }

    @Test fun `every note of a box lies inside the window that box reports`() {
        for (box in CagedBox.entries) for (mode in CagedMode.entries) {
            val w = CagedScales.window(G, box, std, mode)
            val notes = CagedScales.resolve(G, box, mode, ScaleSubset.FullScale, std)
            assertTrue(notes.all { it.position.fret in w }, "$box $mode has a note outside window $w")
        }
    }

    @Test fun `triad inversions yield 12 per quality, top string group first`() {
        // Nadav's order: strings 1-2-3, then 2-3-4, 3-4-5, 4-5-6 (0 = low E here).
        assertEquals(listOf(listOf(3, 4, 5), listOf(2, 3, 4), listOf(1, 2, 3), listOf(0, 1, 2)), CagedScales.TRIAD_GROUPS)
        val maj = CagedScales.triadInversions(G, "maj", std)
        assertEquals(12, maj.size)
        assertEquals(CagedScales.TRIAD_GROUPS, maj.map { it.strings }.distinct())
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2), maj.map { it.inversion })
        for (t in maj) t.strings.indices.forEach { i ->
            val pc = Fretboard.noteAt(std, FretPosition(t.strings[i], t.frets[i])).pitchClass
            assertTrue(pc in setOf(PitchClass.G, PitchClass(11), PitchClass.D), "G major triad tone")
        }
        val min = CagedScales.triadInversions(G, "min", std)
        assertEquals(12, min.size)
        for (t in min) t.strings.indices.forEach { i ->
            val pc = Fretboard.noteAt(std, FretPosition(t.strings[i], t.frets[i])).pitchClass
            assertTrue(pc in setOf(PitchClass.G, PitchClass(10), PitchClass.D), "G minor triad tone")
        }
    }

    @Test fun `the triad run is all 24 - every major group then every minor group`() {
        val run = CagedScales.triadRun(G, std)
        assertEquals(24, run.size)
        assertEquals(List(12) { "maj" } + List(12) { "min" }, run.map { it.first })
        assertEquals(CagedScales.TRIAD_GROUPS, run.take(12).map { it.second.strings }.distinct())
        assertEquals(CagedScales.TRIAD_GROUPS, run.drop(12).map { it.second.strings }.distinct())
    }

    @Test fun `major pentatonic drops the 4th and 7th`() {
        val pcs = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Major, ScaleSubset.Pentatonic, std)
            .map { Fretboard.noteAt(std, it.position).pitchClass }.toSet()
        assertTrue(PitchClass.C !in pcs, "C (the 4th) should be dropped")   // C = 0
        assertTrue(PitchClass(6) !in pcs, "F# (the 7th) should be dropped")
        assertTrue(PitchClass.G in pcs && PitchClass.D in pcs)
    }

    @Test fun `major triad is G B D rooted on G`() {
        val notes = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Major, ScaleSubset.Triad, std)
        val pcs = notes.map { Fretboard.noteAt(std, it.position).pitchClass }.toSet()
        assertEquals(setOf(PitchClass.G, PitchClass(11), PitchClass.D), pcs)   // G B D
        assertTrue(notes.any { it.isRoot } && notes.filter { it.isRoot }.all { Fretboard.noteAt(std, it.position).pitchClass == PitchClass.G })
    }

    @Test fun `minor mode is the PARALLEL minor of the same tonic`() {
        assertEquals(G, CagedScales.rootOf(G, CagedMode.Minor))
        val triad = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.Triad, std)
            .map { Fretboard.noteAt(std, it.position).pitchClass }.toSet()
        assertEquals(setOf(PitchClass.G, PitchClass(10), PitchClass.D), triad)   // G Bb D
        val majPcs = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, std)
            .map { Fretboard.noteAt(std, it.position).pitchClass }.toSet()
        val minPcs = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, std)
            .map { Fretboard.noteAt(std, it.position).pitchClass }.toSet()
        assertTrue(PitchClass(10) in minPcs && PitchClass(10) !in majPcs)   // Bb (♭3) only in minor
        assertTrue(PitchClass(11) in majPcs && PitchClass(11) !in minPcs)   // B (natural 3) only in major
    }

    // ---- The guided run ----

    @Test fun `the guided run is one step per diagram - 34 in all`() {
        val run = CagedScales.PRACTICE_RUN
        assertEquals(34, run.size)
        assertEquals(CagedShapeTable.SHAPES.keys.size, run.size)
        // Every step names a shape that actually exists, and no shape is drilled twice.
        assertEquals(run.size, run.distinct().size)
        for (s in run) assertTrue(ShapeKey(s.box, s.mode, s.subset, s.pattern) in CagedShapeTable.SHAPES, "$s has no shape")
    }

    @Test fun `the run walks the boxes low to high and alternates the leading mode`() {
        val run = CagedScales.PRACTICE_RUN
        // Boxes come in order, contiguously.
        assertEquals(CagedBox.entries.toList(), run.map { it.box }.distinct())
        // Box 1 leads major, box 2 leads minor, box 3 major, … — and each box drills
        // BOTH modes (Nadav's "if pos == 0 major then minor, else swap" rule).
        for (box in CagedBox.entries) {
            val steps = run.filter { it.box == box }
            val expectedLead = if (box.ordinal % 2 == 0) CagedMode.Major else CagedMode.Minor
            assertEquals(expectedLead, steps.first().mode, "$box leads with the wrong mode")
            assertEquals(setOf(CagedMode.Major, CagedMode.Minor), steps.map { it.mode }.toSet(), "$box misses a mode")
            // One contiguous block per mode: the lead's steps all come first.
            assertEquals(steps.count { it.mode == expectedLead }, steps.takeWhile { it.mode == expectedLead }.size)
            // Chord tones first, then the scale, then the pentatonic.
            assertEquals(
                listOf(ScaleSubset.Triad, ScaleSubset.FullScale, ScaleSubset.Pentatonic),
                steps.filter { it.mode == expectedLead }.map { it.subset }.distinct(),
            )
        }
        // Boxes 1 and 4 drill both scale fingerings, so they run 8 steps; the rest 6.
        assertEquals(listOf(8, 6, 6, 8, 6), CagedBox.entries.map { b -> run.count { it.box == b } })
    }
}

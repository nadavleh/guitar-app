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
        // Neck order, not inversion order: each group's 3 shapes ascend the neck and
        // between them cover all 3 inversions (which order they land in depends on the key).
        for (g in CagedScales.TRIAD_GROUPS) {
            val inGroup = maj.filter { it.strings == g }
            assertEquals(3, inGroup.size)
            assertEquals(setOf(0, 1, 2), inGroup.map { it.inversion }.toSet(), "all 3 inversions in $g")
            val lows = inGroup.map { it.frets.min() }
            assertEquals(lows.sorted(), lows, "$g must ascend the neck")
        }
        assertTrue(maj.all { t -> t.frets.all { it >= 1 } }, "movable shapes only — no open strings")
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

    /**
     * Pinned to Nadav's triad sheet (`docs/caged-shapes-source.md` — the D-major
     * page of ~/Desktop/fretboard.pdf): 4 adjacent 3-string groups x 3 inversions,
     * low -> high the neck, no open strings. This is the reason [triadInversions]
     * skips open strings and incomplete (degree-doubling) close voicings.
     */
    // ---- Explore browser: the sheet's boxes, not fret windows ----

    /**
     * Explore used to show every scale tone inside a 5-fret window, which duplicated
     * a PITCH in six of the seven windows: the B string is a major 3rd above the G
     * string, so G-string fret n and B-string fret n−4 are the same note and both
     * fall inside one window. Nadav circled five of them. Reading the sheet's own
     * diagrams removes all of it — no box repeats a pitch.
     */
    @Test fun `no Explore box sounds the same pitch twice`() {
        // The two subsets Explore serves. A TRIAD diagram is a chord grip and may
        // legitimately double a pitch across strings, so it is not included.
        for (mode in CagedMode.entries) for (subset in listOf(ScaleSubset.FullScale, ScaleSubset.Pentatonic)) {
            for (tonic in 0..11) {
                for (p in CagedScales.explorePositions(PitchClass(tonic), mode, subset, std)) {
                    val midis = p.notes.map { Fretboard.noteAt(std, it.position).midi.value }
                    assertEquals(midis.size, midis.distinct().size, "$mode $subset box ${p.box} pattern ${p.pattern} in key $tonic repeats a pitch")
                }
            }
        }
    }

    @Test fun `Explore serves the sheet's diagrams, ascending the neck`() {
        for (mode in CagedMode.entries) {
            assertEquals(7, CagedScales.explorePositions(G, mode, ScaleSubset.FullScale, std).size)
            assertEquals(5, CagedScales.explorePositions(G, mode, ScaleSubset.Pentatonic, std).size)
            assertEquals(5, CagedScales.explorePositions(G, mode, ScaleSubset.Triad, std).size)
            for (subset in ScaleSubset.entries) {
                val ps = CagedScales.explorePositions(G, mode, subset, std)
                assertEquals((1..ps.size).toList(), ps.map { it.index })
                assertEquals(ps.map { it.firstFret }.sorted(), ps.map { it.firstFret }, "$mode $subset must ascend")
                // Every entry IS the table's shape for its own box/pattern.
                for (p in ps) {
                    assertEquals(
                        CagedScales.resolve(G, p.box, mode, subset, std, pattern = p.pattern),
                        p.notes, "$mode $subset box ${p.box} pattern ${p.pattern}",
                    )
                }
            }
        }
    }

    /**
     * The five windows Nadav circled, now served as the boxes that replaced them.
     * String indices: 3 = 3rd string (G), 4 = 2nd string (B).
     *
     * His fifth circle was on the old nut window (frets 0-4). The table has no
     * such box — that window was box 5 an octave down — so box 5 at frets 12-16
     * carries the same shape, minus the same duplicate.
     */
    @Test fun `Nadav's Explore removals - the duplicated pitch is gone from each box`() {
        val major = CagedScales.explorePositions(G, CagedMode.Major, ScaleSubset.FullScale, std)
        fun stringOf(box: CagedBox, pattern: Int, stringIndex: Int): List<Int> =
            major.first { it.box == box && it.pattern == pattern }.notes
                .filter { it.position.stringIndex == stringIndex }.map { it.position.fret }.sorted()
        assertEquals(listOf(5, 7), stringOf(CagedBox.POS2, 1, 3))      // was 5-9: the 6th (G string fret 9) is gone
        assertEquals(listOf(7, 9), stringOf(CagedBox.POS3, 1, 3))      // was 7-11: the 7th (G string fret 11) is gone
        assertEquals(listOf(10, 12), stringOf(CagedBox.POS4, 1, 4))    // was 8-12: the 1 (B string fret 8) is gone
        assertEquals(listOf(12, 13), stringOf(CagedBox.POS4, 2, 4))    // was 10-14: the 2 (B string fret 10) is gone
        assertEquals(listOf(12, 14), stringOf(CagedBox.POS5, 1, 3))    // was 0-4: the 3rd (G string fret 4 / 16) is gone
    }

    @Test fun `triads match Nadav's D major sheet exactly`() {
        val d = PitchClass.D
        val sheet = mapOf(
            listOf(3, 4, 5) to listOf(listOf(2, 3, 2), listOf(7, 7, 5), listOf(11, 10, 10)),
            listOf(2, 3, 4) to listOf(listOf(4, 2, 3), listOf(7, 7, 7), listOf(12, 11, 10)),
            listOf(1, 2, 3) to listOf(listOf(5, 4, 2), listOf(9, 7, 7), listOf(12, 12, 11)),
            listOf(0, 1, 2) to listOf(listOf(5, 5, 4), listOf(10, 9, 7), listOf(14, 12, 12)),
        )
        val got = CagedScales.triadInversions(d, "maj", std)
        assertEquals(12, got.size)
        for ((group, want) in sheet) {
            assertEquals(want, got.filter { it.strings == group }.map { it.frets }, "group $group")
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

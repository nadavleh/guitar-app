package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CagedScalesTest {

    private val std = Tunings.standard
    private val G = PitchClass.G   // 7

    @Test fun `POS1 in G major is the fret window 2 to 5`() {
        assertEquals(2..5, CagedScales.window(G, CagedBox.POS1, std))
        // Low-E string notes of POS1 (G major) are F#(2) G(3) A(5) — Δ7, R, Δ2.
        val lowE = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, std)
            .filter { it.position.stringIndex == 0 }
            .map { it.position.fret }
            .sorted()
        assertEquals(listOf(2, 3, 5), lowE)
        // The root G sits on the low E at fret 3.
        assertTrue(
            CagedScales.resolve(G, CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, std)
                .any { it.position == FretPosition(0, 3) && it.isRoot },
        )
    }

    @Test fun `every note of every box lies inside that box's fret window`() {
        for (box in CagedBox.entries) {
            val w = CagedScales.window(G, box, std)
            val notes = CagedScales.resolve(G, box, CagedMode.Major, ScaleSubset.FullScale, std)
            assertTrue(notes.all { it.position.fret in w }, "box $box has a note outside window $w")
        }
    }

    @Test fun `the 5 boxes tile every scale tone on the neck between frets 2 and 15`() {
        val scalePcs = CagedScales.MAJOR_SCALE.notesFrom(G).toSet()
        val expected = buildSet {
            for (s in 0 until std.stringCount) for (f in 2..15) {
                if (Fretboard.noteAt(std, FretPosition(s, f)).pitchClass in scalePcs) add(FretPosition(s, f))
            }
        }
        val union = CagedBox.entries.flatMap {
            CagedScales.resolve(G, it, CagedMode.Major, ScaleSubset.FullScale, std, numFrets = 15).map { n -> n.position }
        }.toSet()
        assertEquals(expected, union)
    }

    @Test fun `triad inversions yield 12 per quality across the 4 string groups`() {
        val maj = CagedScales.triadInversions(G, "maj", std)
        assertEquals(12, maj.size)
        assertEquals(CagedScales.TRIAD_GROUPS, maj.map { it.strings }.distinct())
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

    @Test fun `minor mode is the PARALLEL minor, root-anchored (no note below the root fret)`() {
        // Same root as major (G), NOT the relative minor.
        assertEquals(G, CagedScales.rootOf(G, CagedMode.Minor))
        // Minor POS1 is ROOT-ANCHORED: window [3,7] (root at fret 3, nothing below).
        assertEquals(3..7, CagedScales.window(G, CagedBox.POS1, std, CagedMode.Minor))
        val minNotes = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, std)
        assertTrue(minNotes.all { it.position.fret >= 3 }, "no note below the root fret (3)")
        // G minor triad = G B♭ D.
        val triad = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.Triad, std)
            .map { Fretboard.noteAt(std, it.position).pitchClass }.toSet()
        assertEquals(setOf(PitchClass.G, PitchClass(10), PitchClass.D), triad)   // G Bb D
        // Major POS1 differs (window [2,5]); the full-scale notes differ too (natural minor ♭3/♭6/♭7).
        val majPcs = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, std)
            .map { Fretboard.noteAt(std, it.position).pitchClass }.toSet()
        val minPcs = CagedScales.resolve(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, std)
            .map { Fretboard.noteAt(std, it.position).pitchClass }.toSet()
        assertTrue(PitchClass(10) in minPcs && PitchClass(10) !in majPcs)   // Bb (♭3) only in minor
        assertTrue(PitchClass(11) in majPcs && PitchClass(11) !in minPcs)   // B (natural 3) only in major
    }

    @Test fun `practice first box reaches down to the 3rd on the next string`() {
        val regions = CagedScales.practiceRegions(G, std)
        // G root on low E = fret 3, so the first box starts at fret 2: the major 3rd
        // (B) then sits on the A string at fret 2 — one string up, per the user's rule.
        assertEquals(2, regions.first().first)
        // The root-anchored [3,7] box is now the SECOND box.
        assertEquals(3..7, regions[1])
        // No duplicate windows after the prepend/dedupe.
        assertEquals(regions.size, regions.distinct().size)
    }
}

package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FretboardOverlayTest {

    @Test
    fun `C major chord lights up exactly C, E, G across the fretboard`() {
        val (root, q) = ChordLibrary.parse("C")!!
        val overlay = FretboardOverlay.chord(root, q, Tunings.standard, numFrets = 12)
        val playedPcs = overlay.values.map { it.pitchClass }.toSet()
        assertEquals(setOf(PitchClass.C, PitchClass.E, PitchClass.G), playedPcs)
    }

    @Test
    fun `every C in the C-major overlay is flagged as root`() {
        val (root, q) = ChordLibrary.parse("C")!!
        val overlay = FretboardOverlay.chord(root, q, Tunings.standard, numFrets = 12)
        for ((pos, h) in overlay) {
            if (h.pitchClass == PitchClass.C) {
                assertTrue(h.isRoot, "$pos should be marked root")
                assertEquals(0, h.interval.semitones, "root must have interval 0 / P1")
            } else {
                assertTrue(!h.isRoot, "$pos non-root should not be flagged root")
            }
        }
    }

    @Test
    fun `Cmaj7 overlay includes all four chord tones`() {
        val (root, q) = ChordLibrary.parse("Cmaj7")!!
        val overlay = FretboardOverlay.chord(root, q, Tunings.standard, numFrets = 12)
        val playedPcs = overlay.values.map { it.pitchClass }.toSet()
        assertEquals(setOf(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.B), playedPcs)
    }

    @Test
    fun `open positions of standard tuning that are chord tones get fret zero entries`() {
        val (root, q) = ChordLibrary.parse("Em")!!
        // Em = E, G, B → all three are open strings in standard tuning (E2/G3/B3/E4)
        val overlay = FretboardOverlay.chord(root, q, Tunings.standard, numFrets = 12)
        val openPositions = overlay.keys.filter { it.fret == 0 }.map { it.stringIndex }.toSet()
        // string 0 (low E)=E ✓, string 2 (D) open=D ✗, string 3 (G)=G ✓, string 4 (B)=B ✓, string 5 (high E)=E ✓
        assertTrue(openPositions.contains(0))
        assertTrue(openPositions.contains(3))
        assertTrue(openPositions.contains(4))
        assertTrue(openPositions.contains(5))
    }

    @Test
    fun `A minor pentatonic scale overlay positions match Fretboard_allPositions`() {
        val scale = ScaleLibrary.scales["minor pentatonic"]!!
        val overlay = FretboardOverlay.scale(PitchClass.A, scale, Tunings.standard, numFrets = 12)
        val overlayPositions = overlay.keys.toSet()

        val expectedPositions = scale.notesFrom(PitchClass.A)
            .flatMap { pc -> Fretboard.allPositions(Tunings.standard, frets = 12, of = pc) }
            .toSet()
        assertEquals(expectedPositions, overlayPositions)
    }

    @Test
    fun `§13_8 scale display updates correctly after tuning changes`() {
        val scale = ScaleLibrary.scales["major"]!!
        val standardOverlay = FretboardOverlay.scale(PitchClass.C, scale, Tunings.standard, numFrets = 12)
        val dropDOverlay = FretboardOverlay.scale(PitchClass.C, scale, Tunings.dropD, numFrets = 12)

        // The pitch-class set is identical (same scale, same root), but the positions differ
        // because the lowest string's tuning changed E2 → D2.
        assertEquals(
            standardOverlay.values.map { it.pitchClass }.toSet(),
            dropDOverlay.values.map { it.pitchClass }.toSet(),
            "scale's pitch-class set should be invariant under tuning change"
        )
        assertTrue(
            standardOverlay.keys != dropDOverlay.keys,
            "scale POSITIONS must change with tuning (E2 → D2 on string 6 alters chord-tone hits)"
        )

        // Specific check: drop-D string 6 fret 0 = D → in C major
        assertTrue(FretPosition(0, 0) in dropDOverlay.keys, "drop D's open low D should be in C major scale")
        // Standard tuning string 6 fret 0 = E → also in C major (E is the 3rd)
        assertTrue(FretPosition(0, 0) in standardOverlay.keys)
        // Difference: drop-D's fret 1 on string 6 is D# (not in C major), but standard's is F (in C major)
        assertTrue(FretPosition(0, 1) in standardOverlay.keys, "standard fret 1 on low E is F, in C major")
        assertTrue(FretPosition(0, 1) !in dropDOverlay.keys, "drop-D fret 1 on low D is D#, NOT in C major")
    }

    @Test
    fun `interval label matches actual chord-quality interval, not just semitones-mod-12`() {
        // For a Cmaj13 chord, the 9 is the M9 (14 semitones) and 13 is the M13 (21 semitones).
        // The overlay should report those exact intervals, not their reduced form.
        val (root, q) = ChordLibrary.parse("C13")!!
        val overlay = FretboardOverlay.chord(root, q, Tunings.standard, numFrets = 12)
        val intervalsByPc: Map<PitchClass, Interval> = overlay.values.associate { it.pitchClass to it.interval }
        // Cmaj9 (14 semitones) = D — D should be marked with semitones 14, not 2
        assertEquals(14, intervalsByPc[PitchClass.D]?.semitones, "9th should show as M9 (14), not M2 (2)")
        // C13 (21 semitones) = A — A should be marked with semitones 21
        assertEquals(21, intervalsByPc[PitchClass.A]?.semitones, "13th should show as M13 (21)")
    }
}

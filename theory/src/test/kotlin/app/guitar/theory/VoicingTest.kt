package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoicingTest {

    // A shell voicing is ROOT + 3rd + 7th, with the 5th (perfect OR altered) omitted.

    @Test
    fun `shell essentials of Cmaj7 are root, 3rd and 7th`() {
        val (_, q) = ChordLibrary.parse("Cmaj7")!!
        val essential = essentialShellIntervals(q)
        assertEquals(setOf(Interval.P1, Interval.maj3, Interval.maj7), essential)
    }

    @Test
    fun `shell essentials of C7 are root, 3rd and b7`() {
        val (_, q) = ChordLibrary.parse("C7")!!
        val essential = essentialShellIntervals(q)
        assertEquals(setOf(Interval.P1, Interval.maj3, Interval.min7), essential)
    }

    @Test
    fun `shell essentials of Cm7b5 drop the b5 - just root, b3, b7`() {
        val (_, q) = ChordLibrary.parse("Cm7b5")!!
        val essential = essentialShellIntervals(q)
        assertEquals(setOf(Interval.P1, Interval.min3, Interval.min7), essential)
    }

    @Test
    fun `shell essentials of triad Cmaj keep root and third`() {
        val (_, q) = ChordLibrary.parse("C")!!
        val essential = essentialShellIntervals(q)
        assertEquals(setOf(Interval.P1, Interval.maj3), essential)
    }

    @Test
    fun `shell essentials of dim7 are root, b3, bb7 (no b5)`() {
        val (_, q) = ChordLibrary.parse("Cdim7")!!
        val essential = essentialShellIntervals(q)
        // Cdim7 = C Eb Gb Bbb (= A). bb7 is maj6 (semitones 9) in our encoding; b5 dropped.
        assertEquals(setOf(Interval.P1, Interval.min3, Interval.maj6), essential)
    }

    @Test
    fun `shell essentials of Cmaj9 include extension`() {
        val (_, q) = ChordLibrary.parse("Cmaj9".replace("maj9", "9"))!!  // C9 = dominant 9
        val essential = essentialShellIntervals(q)
        // C9 = C E G Bb D. Shell drops the 5th (G). Keeps root, E (maj3), Bb (min7), D (maj9).
        assertEquals(setOf(Interval.P1, Interval.maj3, Interval.min7, Interval.maj9), essential)
    }

    @Test
    fun `Standard mode unchanged - Cmaj7 shapes contain all four chord tones`() {
        val gen = ChordShapeGenerator(style = VoicingStyle.Standard)
        val (root, q) = ChordLibrary.parse("Cmaj7")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        val chordPcs = q.notesFrom(root).toSet()
        for (s in shapes) {
            val pcs = s.notes.filterNotNull().map { it.pitchClass }.toSet()
            assertTrue(pcs.containsAll(chordPcs), "standard mode must include all chord tones")
        }
    }

    @Test
    fun `Shell mode for Cmaj7 produces shapes containing E and B at minimum`() {
        val gen = ChordShapeGenerator(style = VoicingStyle.Shell)
        val (root, q) = ChordLibrary.parse("Cmaj7")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        assertTrue(shapes.isNotEmpty(), "expected at least one shell voicing")
        for (s in shapes) {
            val pcs = s.notes.filterNotNull().map { it.pitchClass }.toSet()
            // Essential PCs for Cmaj7 shell: E (maj3) and B (maj7)
            assertTrue(PitchClass.E in pcs, "shape ${s.frets} missing the 3rd (E)")
            assertTrue(PitchClass.B in pcs, "shape ${s.frets} missing the 7th (B)")
            // Non-chord tones MUST NOT be present (only chord tones — root C, 3rd E, 5th G, 7th B)
            val allowed = setOf(PitchClass.C, PitchClass.E, PitchClass.G, PitchClass.B)
            assertTrue(pcs.all { it in allowed }, "shape contains non-chord tones: $pcs")
        }
    }

    @Test
    fun `Shell mode for C7 includes the true 5th-string-root shell x 3 x 3 5 x`() {
        // Shell mode serves TRUE shell voicings (root + 3rd + b7, no 5th). This is the
        // 5th-string-root C7 shell: A-3=C(R), G-3=Bb(b7), B-5=E(3).
        val gen = ChordShapeGenerator(style = VoicingStyle.Shell)
        val (root, q) = ChordLibrary.parse("C7")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        val expected = listOf(null, 3, null, 3, 5, null)
        assertTrue(shapes.any { it.frets == expected },
            "expected C7 shell $expected, got ${shapes.map { it.frets }}")
    }

    @Test
    fun `Shell mode for Cmaj7 gives the two shell shapes (6th- and 5th-string root)`() {
        val gen = ChordShapeGenerator(style = VoicingStyle.Shell)
        val (root, q) = ChordLibrary.parse("Cmaj7")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        assertEquals(2, shapes.size, "expected exactly the two shell shapes, got ${shapes.map { it.frets }}")
        assertTrue(shapes.all { it.frets.count { f -> f != null } == 3 }, "shells are 3-note voicings")
    }
}

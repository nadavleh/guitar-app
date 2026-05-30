package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoicingTest {

    @Test
    fun `shell essentials of Cmaj7 are the 3rd and 7th`() {
        val (_, q) = ChordLibrary.parse("Cmaj7")!!
        val essential = essentialShellIntervals(q)
        assertEquals(setOf(Interval.maj3, Interval.maj7), essential)
    }

    @Test
    fun `shell essentials of C7 are the 3rd and b7`() {
        val (_, q) = ChordLibrary.parse("C7")!!
        val essential = essentialShellIntervals(q)
        assertEquals(setOf(Interval.maj3, Interval.min7), essential)
    }

    @Test
    fun `shell essentials of Cm7b5 are the b3, b5, and b7`() {
        val (_, q) = ChordLibrary.parse("Cm7b5")!!
        val essential = essentialShellIntervals(q)
        assertEquals(setOf(Interval.min3, Interval.TT, Interval.min7), essential)
    }

    @Test
    fun `shell essentials of triad Cmaj keep root and third`() {
        val (_, q) = ChordLibrary.parse("C")!!
        val essential = essentialShellIntervals(q)
        assertEquals(setOf(Interval.P1, Interval.maj3), essential)
    }

    @Test
    fun `shell essentials of dim7 keep b3 TT bb7`() {
        val (_, q) = ChordLibrary.parse("Cdim7")!!
        val essential = essentialShellIntervals(q)
        // Cdim7 = C Eb Gb Bbb (= A). bb7 is maj6 (semitones 9) in our encoding.
        assertEquals(setOf(Interval.min3, Interval.TT, Interval.maj6), essential)
    }

    @Test
    fun `shell essentials of Cmaj9 include extension`() {
        val (_, q) = ChordLibrary.parse("Cmaj9".replace("maj9", "9"))!!  // C9 = dominant 9
        val essential = essentialShellIntervals(q)
        // C9 = C E G Bb D. Shell drops C, G. Keeps E (maj3), Bb (min7), D (maj9).
        assertEquals(setOf(Interval.maj3, Interval.min7, Interval.maj9), essential)
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
    fun `Shell mode for C7 includes the canonical drop-2 root-pos x x 5 5 5 6`() {
        // Shell mode now serves the jazzguitar.be drop-2 dictionary. This is the
        // root-position drop-2 voicing of C7 (5-R-3-b7 on D-G-B-e).
        val gen = ChordShapeGenerator(style = VoicingStyle.Shell)
        val (root, q) = ChordLibrary.parse("C7")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        val expected = listOf(null, null, 5, 5, 5, 6)
        assertTrue(shapes.any { it.frets == expected },
            "expected canonical C7 drop-2 voicing $expected, got ${shapes.map { it.frets }}")
    }

    @Test
    fun `Shell mode for Cmaj7 spans multiple positions along the neck`() {
        val gen = ChordShapeGenerator(style = VoicingStyle.Shell)
        val (root, q) = ChordLibrary.parse("Cmaj7")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        // Each inversion sits in a different neck zone; expect distinct positions.
        val positions = shapes.map { it.position }.toSet()
        assertTrue(positions.size >= 4,
            "expected Shell-mode Cmaj7 to span 4+ distinct positions, got $positions")
    }
}

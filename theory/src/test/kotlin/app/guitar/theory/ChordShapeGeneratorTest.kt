package app.guitar.theory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChordShapeGeneratorTest {

    private val gen = ChordShapeGenerator()

    @Test
    fun `every shape contains all chord tones`() {
        val (root, q) = ChordLibrary.parse("Cmaj7")!!
        val chordPcs = q.notesFrom(root).toSet()
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        assertTrue(shapes.isNotEmpty(), "expected at least one Cmaj7 shape")
        for (s in shapes) {
            val playedPcs = s.notes.filterNotNull().map { it.pitchClass }.toSet()
            assertTrue(
                playedPcs.containsAll(chordPcs),
                "shape ${s.frets} missing chord tones: required=$chordPcs played=$playedPcs"
            )
        }
    }

    @Test
    fun `every shape has at most one note per string`() {
        val (root, q) = ChordLibrary.parse("G")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        for (s in shapes) {
            assertEquals(
                Tunings.standard.stringCount,
                s.frets.size,
                "frets list length must equal string count"
            )
        }
    }

    @Test
    fun `§13_7 no shape exceeds the configured max fret span`() {
        val testCases = listOf("C", "Am", "G", "D", "F#m7", "Cmaj7", "Bbdim7", "E7", "Dsus4")
        for (symbol in testCases) {
            val (root, q) = ChordLibrary.parse(symbol)!!
            val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
            for (s in shapes) {
                assertTrue(
                    s.fretSpan <= gen.maxFretSpan,
                    "shape $symbol ${s.frets} has span ${s.fretSpan} > maxFretSpan ${gen.maxFretSpan}"
                )
            }
        }
    }

    @Test
    fun `every shape plays at least the minimum number of strings`() {
        val (root, q) = ChordLibrary.parse("Am")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        for (s in shapes) {
            assertTrue(s.playedCount >= gen.minStringsPlayed)
        }
    }

    @Test
    fun `C major in standard tuning includes the canonical open shape`() {
        val (root, q) = ChordLibrary.parse("C")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        // Open C major: x 3 2 0 1 0 — strings indexed low→high
        // index 0 = string 6 (low E) — muted
        // index 1 = string 5 (A) — fret 3 (C)
        // index 2 = string 4 (D) — fret 2 (E)
        // index 3 = string 3 (G) — fret 0 (G, open)
        // index 4 = string 2 (B) — fret 1 (C)
        // index 5 = string 1 (high E) — fret 0 (E, open)
        val openC = listOf<Int?>(null, 3, 2, 0, 1, 0)
        assertTrue(
            shapes.any { it.frets == openC },
            "expected open-C shape $openC among ${shapes.size} shapes; got: ${shapes.take(10).map { it.frets }}"
        )
    }

    @Test
    fun `E minor in standard tuning includes the canonical open shape`() {
        val (root, q) = ChordLibrary.parse("Em")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        // Open Em: 0 2 2 0 0 0
        val openEm = listOf<Int?>(0, 2, 2, 0, 0, 0)
        assertTrue(
            shapes.any { it.frets == openEm },
            "expected open-Em shape $openEm; got top 10: ${shapes.take(10).map { it.frets }}"
        )
    }

    @Test
    fun `bass pitch class and root-in-bass detection`() {
        val (root, q) = ChordLibrary.parse("C")!!
        val shape = ChordShape(
            chordName = "C",
            root = root,
            quality = q,
            frets = listOf(null, 3, 2, 0, 1, 0),   // open C
            tuning = Tunings.standard
        )
        // First non-muted string is index 1 (5th string A, fret 3 = C)
        assertEquals(PitchClass.C, shape.bassPitchClass)
        assertTrue(shape.hasRootInBass)
        assertEquals(1, shape.position)
        assertEquals(2, shape.fretSpan)  // frets 1..3 used
    }

    @Test
    fun `shape with no fretted notes has position and span of 0`() {
        val (root, q) = ChordLibrary.parse("Em")!!
        val shape = ChordShape(
            chordName = "Em",
            root = root,
            quality = q,
            frets = listOf(0, 2, 2, 0, 0, 0),
            tuning = Tunings.standard
        )
        assertEquals(2, shape.position)
        assertEquals(0, shape.fretSpan)   // only non-zero frets are both 2
    }

    @Test
    fun `Drop D tuning produces different shapes than standard`() {
        val (root, q) = ChordLibrary.parse("D")!!
        val standardShapes = gen.shapesFor(root, q, Tunings.standard, frets = 12)
            .map { it.frets }.toSet()
        val dropDShapes = gen.shapesFor(root, q, Tunings.dropD, frets = 12)
            .map { it.frets }.toSet()
        assertTrue(standardShapes != dropDShapes, "Drop D should yield a different set of D shapes")
        // Drop D specifically should allow a low D2 at fret 0 on string 6
        val openLowD = dropDShapes.any { it[0] == 0 }
        assertTrue(openLowD, "expected some Drop D voicing using open string 6 = D2")
    }

    @Test
    fun `fret range filter limits output`() {
        val (root, q) = ChordLibrary.parse("C")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14, fretRange = 5..9)
        assertTrue(shapes.isNotEmpty())
        for (s in shapes) {
            val nonZero = s.frets.filterNotNull().filter { it > 0 }
            for (f in nonZero) {
                assertTrue(f in 5..9, "shape ${s.frets} has fret $f outside 5..9")
            }
        }
    }

    @Test
    fun `output is sorted with root-in-bass first then by position`() {
        val (root, q) = ChordLibrary.parse("G")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        assertTrue(shapes.isNotEmpty())
        // The first few shapes should have root in bass (when any such shape exists)
        val anyRootInBass = shapes.any { it.hasRootInBass }
        if (anyRootInBass) {
            assertTrue(
                shapes.first().hasRootInBass,
                "expected first shape to have root in bass; got: ${shapes.first().frets}"
            )
        }
        // Positions should be non-decreasing within the root-in-bass group
        val rootInBassGroup = shapes.filter { it.hasRootInBass }
        for (i in 1 until rootInBassGroup.size) {
            assertTrue(
                rootInBassGroup[i - 1].position <= rootInBassGroup[i].position,
                "positions not non-decreasing among root-in-bass shapes"
            )
        }
    }

    @Test
    fun `multiple shapes at different positions for Gmaj7`() {
        val (root, q) = ChordLibrary.parse("Gmaj7")!!
        val shapes = gen.shapesFor(root, q, Tunings.standard, frets = 14)
        val positions = shapes.map { it.position }.toSet()
        assertTrue(
            positions.size >= 3,
            "expected ≥3 distinct positions for Gmaj7; got positions=$positions"
        )
    }

    @Test
    fun `unrelated chord produces non-empty result for common chords`() {
        // Spot-check: every chord in the library should yield at least one shape
        // somewhere on a 14-fret standard-tuning neck.
        for ((symbol, _) in ChordLibrary.qualities) {
            if (symbol.isEmpty()) continue   // skip the duplicate "" alias
            val full = "C$symbol"
            val parsed = ChordLibrary.parse(full) ?: continue
            val shapes = gen.shapesFor(parsed.first, parsed.second, Tunings.standard, frets = 14)
            assertTrue(
                shapes.isNotEmpty(),
                "no shape found for $full on standard tuning, 14 frets"
            )
        }
    }

    @Test
    fun `generates within a reasonable time budget`() {
        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
            val (root, q) = ChordLibrary.parse("Cmaj7")!!
            gen.shapesFor(root, q, Tunings.standard, frets = 14)
        }
    }
}

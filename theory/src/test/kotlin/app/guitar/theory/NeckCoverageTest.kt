package app.guitar.theory

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * The user's hard requirement: for every chord-quality × root, the chord shape
 * generator must return a set of voicings that
 *  (a) covers a real stretch of the neck (positions span ≥ 5 frets), and
 *  (b) includes at least one shape with the root note on each of the three
 *      lowest strings — guitar-6 (low E, idx 0), guitar-5 (A, idx 1), and
 *      guitar-4 (D, idx 2). That guarantees a low / mid / high voicing exists
 *      regardless of how the rest of the CAGED templates happen to land.
 *
 * G#dim was the user-reported regression: it had NO CAGED template at all
 * (the quality "dim" wasn't in the map), so cagedShapesFor returned an empty
 * list and the brute-force fallback clustered every voicing near fret 0-3.
 */
class NeckCoverageTest {

    private val std = Tunings.standard
    private val maxFrets = 14

    /** All chord-quality symbols we expose to the user in the Chord sheet. */
    private val USER_QUALITIES = listOf(
        "", "m", "7", "maj7", "m7", "dim", "aug", "sus4", "sus2",
        "6", "m6", "m7b5", "dim7", "9", "add9", "13",
    )

    /** This mirrors what the Chord sheet actually surfaces to the user.
     *  The UI takes the first 12 shapes the generator returns, so we test that
     *  subset — not the brute-force avalanche of 500+ enumerated shapes. */
    private val UI_SHAPE_LIMIT = 12

    private fun shapesAsUiSeesThem(
        root: PitchClass,
        quality: ChordQuality,
    ): List<ChordShape> =
        ChordShapeGenerator()
            .shapesFor(root, quality, std, frets = maxFrets)
            .take(UI_SHAPE_LIMIT)

    @TestFactory
    fun `every chord quality has a root on strings 6, 5, and 4`(): List<DynamicTest> =
        USER_QUALITIES.flatMap { qualSym ->
            (0..11).map { rootValue ->
                val root = PitchClass(rootValue)
                val quality = ChordLibrary.qualities[qualSym]
                    ?: error("unknown user quality '$qualSym'")
                val displayName = "${NoteSpeller.spell(root)}$qualSym"
                DynamicTest.dynamicTest("$displayName has roots on strings 6/5/4") {
                    val shapes = shapesAsUiSeesThem(root, quality)
                    assertTrue(shapes.isNotEmpty(), "no shapes generated for $displayName")
                    assertTrue(
                        shapes.any { rootOnString(it, stringIdx = 0, root) },
                        "$displayName: no shape places the root on the low-E string (6th). " +
                            "Shapes: ${shapes.map { it.frets }}"
                    )
                    assertTrue(
                        shapes.any { rootOnString(it, stringIdx = 1, root) },
                        "$displayName: no shape places the root on the A string (5th). " +
                            "Shapes: ${shapes.map { it.frets }}"
                    )
                    assertTrue(
                        shapes.any { rootOnString(it, stringIdx = 2, root) },
                        "$displayName: no shape places the root on the D string (4th). " +
                            "Shapes: ${shapes.map { it.frets }}"
                    )
                }
            }
        }

    @TestFactory
    fun `every chord quality spans at least 5 frets along the neck`(): List<DynamicTest> =
        USER_QUALITIES.flatMap { qualSym ->
            (0..11).map { rootValue ->
                val root = PitchClass(rootValue)
                val quality = ChordLibrary.qualities[qualSym]!!
                val displayName = "${NoteSpeller.spell(root)}$qualSym"
                DynamicTest.dynamicTest("$displayName spans ≥ 5 frets") {
                    val shapes = shapesAsUiSeesThem(root, quality)
                    assertTrue(shapes.size >= 3, "$displayName: only ${shapes.size} shape(s)")
                    val positions = shapes.map { it.position }
                    val span = (positions.max()) - (positions.min())
                    assertTrue(
                        span >= 5,
                        "$displayName: positions ${positions.sorted()} only span $span frets " +
                            "(want ≥ 5). Shapes: ${shapes.map { it.frets }}"
                    )
                }
            }
        }

    /** Sanity: every quality the user can pick yields at least one shape. */
    @Test
    fun `no user-facing chord quality produces an empty shape list`() {
        for (qualSym in USER_QUALITIES) {
            for (rootValue in 0..11) {
                val root = PitchClass(rootValue)
                val q = ChordLibrary.qualities[qualSym]!!
                val shapes = ChordShapeGenerator()
                    .shapesFor(root, q, std, frets = maxFrets)
                assertTrue(
                    shapes.isNotEmpty(),
                    "no shapes for ${NoteSpeller.spell(root)}$qualSym"
                )
            }
        }
    }

    /** Returns true when [shape] plays the chord's root note on [stringIdx]. */
    private fun rootOnString(shape: ChordShape, stringIdx: Int, root: PitchClass): Boolean {
        val fret = shape.frets.getOrNull(stringIdx) ?: return false
        val openPc = shape.tuning.openStrings[stringIdx].pitchClass.value
        val pcOnFret = (openPc + fret) % 12
        return pcOnFret == root.value
    }
}

package app.guitar.theory

/**
 * The 34 CAGED shapes of the "Scales & Triads" trainer, transcribed dot-for-dot
 * from Nadav's hand-drawn sheet (`~/Desktop/fretboard.pdf`, archived as
 * `docs/caged-shapes-source.md`).
 *
 * These REPLACE the old fret-window generator: a window like `[T-1, T+2]` sweeps
 * up every scale tone it contains, which is close to the real fingering but not
 * equal to it - the sheet's boxes reach back or forward a fret on individual
 * strings, and its pentatonic/triad shapes are hand-picked, not filtered. The
 * table is the source of truth; nothing is derived any more.
 *
 * Mirror of `chorect-web/src/theory/cagedShapeTable.ts`.
 *
 * ## Notation
 * One shape is a string of per-string groups, low-E first:
 * `"E:-1,0*,2 | A:-1,0,2 | ..."` - the letter is the open string (E A D G B e,
 * low to high), each number is a **fret offset from the key's low-E root fret**
 * (so G, whose root sits at low-E fret 3, reads directly off the sheet as
 * `fret - 3`), and `*` marks a root. A string with no dots is omitted.
 *
 * ## Corrections applied to the sheet
 * The sheet is drawn in G throughout and is self-consistent except for four
 * slips, each verified by rendering the diagram and re-deriving it:
 *  1. **Minor scale box 1 pattern 1** was drawn in A minor (roots at fret 5).
 *     Shifted down 2 frets so its roots land on G like every other diagram.
 *  2. **Minor pentatonic box 1** had the A string's 2nd dot at fret 6 (D#, not
 *     in G minor pentatonic). Moved to fret 5 (D).
 *  3. **Minor pentatonic box 4** was an exact copy of box 3. Rebuilt at frets
 *     10-13 - which is precisely the pentatonic subset of the sheet's own
 *     *minor scale box 4 pattern 2*, so the correction comes from the sheet.
 *  4. **Minor triad box 4** was box 3's shape with the low-E dot on fret 8 (C,
 *     not a G-minor chord tone). Rebuilt as the triad subset of the corrected
 *     minor pentatonic box 4 - the same relationship every other triad diagram
 *     on the sheet obeys (verified exact on major box 4 and minor box 3).
 *
 * `CagedShapeTableTest` re-checks all of this on every build.
 */

/** One dot of a transcribed shape. [string] 0 = low E; [offset] is relative to
 *  the key's low-E root fret; [isRoot] drives the red dots on the sheet. */
data class ShapeDot(val string: Int, val offset: Int, val isRoot: Boolean)

/** Identity of one diagram on the sheet. [pattern] is 1, or 2 for the second
 *  (3-notes-per-string) fingering that only boxes 1 and 4 carry. */
data class ShapeKey(
    val box: CagedBox,
    val mode: CagedMode,
    val subset: ScaleSubset,
    val pattern: Int = 1,
)

object CagedShapeTable {

    /** Open-string letters in the shape notation, low to high. */
    private val STRING_LETTERS = listOf("E", "A", "D", "G", "B", "e")

    private fun shape(
        box: CagedBox, mode: CagedMode, subset: ScaleSubset, pattern: Int, spec: String,
    ): Pair<ShapeKey, List<ShapeDot>> = ShapeKey(box, mode, subset, pattern) to parse(spec)

    /** `"E:-1,0*,2 | A:..."` -> dots. Throws on an unknown string letter so a
     *  typo fails the tests rather than silently dropping notes. */
    internal fun parse(spec: String): List<ShapeDot> = buildList {
        for (group in spec.split("|")) {
            val head = group.substringBefore(':').trim()
            val s = STRING_LETTERS.indexOf(head)
            require(s >= 0) { "unknown string '$head' in shape spec '$spec'" }
            for (tok in group.substringAfter(':').split(",")) {
                val t = tok.trim()
                val isRoot = t.endsWith("*")
                add(ShapeDot(s, (if (isRoot) t.dropLast(1) else t).toInt(), isRoot))
            }
        }
    }

    /** All 34 shapes, exactly as drawn (after the four corrections above). */
    val SHAPES: Map<ShapeKey, List<ShapeDot>> = mapOf(
        // ---- Box 1 - CAGED shape E ----
        shape(CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, 1,
            "E:-1,0*,2 | A:-1,0,2 | D:-1,1,2* | G:-1,1,2 | B:0,2 | e:-1,0*,2"),   // Major scale box 1 pattern 1
        shape(CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, 2,
            "E:0*,2,4 | A:0,2,4 | D:1,2*,4 | G:1,2,4 | B:0,2,4 | e:0*,2,4"),   // Major scale box 1 pattern 2
        shape(CagedBox.POS1, CagedMode.Major, ScaleSubset.Pentatonic, 1,
            "E:0*,2 | A:-1,2 | D:-1,2* | G:-1,1 | B:0,2 | e:0*,2"),   // Major pentatonic scale box 1
        shape(CagedBox.POS1, CagedMode.Major, ScaleSubset.Triad, 1,
            "E:0*,4 | A:-1,2 | D:2* | G:1 | B:0 | e:0*,4"),   // Major triad box 1
        shape(CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, 1,
            "E:-2,0*,2 | A:-2,0,2 | D:-2,0,2* | G:-1,0,2 | B:0,1 | e:-2,0*,2"),   // Minor scale box 1 pattern 1
        shape(CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, 2,
            "E:0*,2,3 | A:0,2,3 | D:0,2*,4 | G:0,2,4 | B:0,1,3 | e:0*,2,3"),   // Minor scale box 1 pattern 2
        shape(CagedBox.POS1, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
            "E:0*,3 | A:0,2 | D:0,2* | G:0,2 | B:0,3 | e:0*,3"),   // Minor pentatonic scale box 1
        shape(CagedBox.POS1, CagedMode.Minor, ScaleSubset.Triad, 1,
            "E:0*,3 | A:-2,2 | D:2* | G:0 | B:0 | e:0*,3"),   // Minor triad box 1

        // ---- Box 2 - CAGED shape D ----
        shape(CagedBox.POS2, CagedMode.Major, ScaleSubset.FullScale, 1,
            "E:2,4,5 | A:2,4,6 | D:2*,4,6 | G:2,4 | B:2,4,5* | e:2,4,5"),   // Major scale box 2 pattern 1
        shape(CagedBox.POS2, CagedMode.Major, ScaleSubset.Pentatonic, 1,
            "E:2,4 | A:2,4 | D:2*,4 | G:1,4 | B:2,5* | e:2,4"),   // Major pentatonic scale box 2
        shape(CagedBox.POS2, CagedMode.Major, ScaleSubset.Triad, 1,
            "E:4 | A:2 | D:2*,6 | G:1,4 | B:5* | e:4"),   // Major triad box 2
        shape(CagedBox.POS2, CagedMode.Minor, ScaleSubset.FullScale, 1,
            "E:2,3,5 | A:2,3,5 | D:2*,4,5 | G:2,4,5 | B:3,5* | e:2,3,5"),   // Minor scale box 2 pattern 1
        shape(CagedBox.POS2, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
            "E:3,5 | A:2,5 | D:2*,5 | G:2,4 | B:3,5* | e:3,5"),   // Minor pentatonic scale box 2
        shape(CagedBox.POS2, CagedMode.Minor, ScaleSubset.Triad, 1,
            "E:3 | A:2 | D:2*,5 | G:0,4 | B:5* | e:3"),   // Minor triad box 2

        // ---- Box 3 - CAGED shape C ----
        shape(CagedBox.POS3, CagedMode.Major, ScaleSubset.FullScale, 1,
            "E:4,5,7 | A:4,6,7* | D:4,6,7 | G:4,6 | B:4,5*,7 | e:4,5,7"),   // Major scale box 3 pattern 1
        shape(CagedBox.POS3, CagedMode.Major, ScaleSubset.Pentatonic, 1,
            "E:4,7 | A:4,7* | D:4,6 | G:4,6 | B:5*,7 | e:4,7"),   // Major pentatonic scale box 3
        shape(CagedBox.POS3, CagedMode.Major, ScaleSubset.Triad, 1,
            "E:4,7 | A:7* | D:6 | G:4 | B:5* | e:4,7"),   // Major triad box 3
        shape(CagedBox.POS3, CagedMode.Minor, ScaleSubset.FullScale, 1,
            "E:3,5,7 | A:3,5,7* | D:4,5,7 | G:4,5,7 | B:5*,7 | e:3,5,7"),   // Minor scale box 3 pattern 1
        shape(CagedBox.POS3, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
            "E:5,7 | A:5,7* | D:5,7 | G:4,7 | B:5*,8 | e:5,7"),   // Minor pentatonic scale box 3
        shape(CagedBox.POS3, CagedMode.Minor, ScaleSubset.Triad, 1,
            "E:7 | A:7* | D:5 | G:4 | B:5*,8 | e:7"),   // Minor triad box 3

        // ---- Box 4 - CAGED shape A ----
        shape(CagedBox.POS4, CagedMode.Major, ScaleSubset.FullScale, 1,
            "E:5,7,9 | A:6,7*,9 | D:6,7,9 | G:6,8,9* | B:7,9 | e:5,7,9"),   // Major scale box 4 pattern 1
        shape(CagedBox.POS4, CagedMode.Major, ScaleSubset.FullScale, 2,
            "E:7,9,11 | A:7*,9,11 | D:7,9,11 | G:8,9*,11 | B:9,10 | e:7,9,11"),   // Major scale box 4 pattern 2
        shape(CagedBox.POS4, CagedMode.Major, ScaleSubset.Pentatonic, 1,
            "E:7,9 | A:7*,9 | D:6,9 | G:6,9* | B:7,9 | e:7,9"),   // Major pentatonic scale box 4
        shape(CagedBox.POS4, CagedMode.Major, ScaleSubset.Triad, 1,
            "E:7 | A:7* | D:6,9 | G:9* | B:9 | e:7"),   // Major triad box 4
        shape(CagedBox.POS4, CagedMode.Minor, ScaleSubset.FullScale, 1,
            "E:5,7,8 | A:5,7*,9 | D:5,7,9 | G:5,7 | B:5*,7,8 | e:5,7,8"),   // Minor scale box 4 pattern 1
        shape(CagedBox.POS4, CagedMode.Minor, ScaleSubset.FullScale, 2,
            "E:7,8,10 | A:7*,9,10 | D:7,9,10 | G:7,9* | B:7,8,10 | e:7,8,10"),   // Minor scale box 4 pattern 2
        shape(CagedBox.POS4, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
            "E:7,10 | A:7*,10 | D:7,9 | G:7,9* | B:8,10 | e:7,10"),   // Minor pentatonic scale box 4
        shape(CagedBox.POS4, CagedMode.Minor, ScaleSubset.Triad, 1,
            "E:7 | A:7*,10 | D:9 | G:9* | B:8 | e:7"),   // Minor triad box 4

        // ---- Box 5 - CAGED shape G ----
        shape(CagedBox.POS5, CagedMode.Major, ScaleSubset.FullScale, 1,
            "E:9,11,12* | A:9,11,12 | D:9,11,13 | G:9*,11 | B:9,10,12 | e:9,11,12*"),   // Major scale box 5 pattern 1
        shape(CagedBox.POS5, CagedMode.Major, ScaleSubset.Pentatonic, 1,
            "E:9,12* | A:9,11 | D:9,11 | G:9*,11 | B:9,12 | e:9,12*"),   // Major pentatonic scale box 5
        shape(CagedBox.POS5, CagedMode.Major, ScaleSubset.Triad, 1,
            "E:12* | A:11 | D:9 | G:9* | B:9,12 | e:12*"),   // Major triad box 5
        shape(CagedBox.POS5, CagedMode.Minor, ScaleSubset.FullScale, 1,
            "E:8,10,12* | A:9,10,12 | D:9,10,12 | G:9*,11,12 | B:10,12 | e:8,10,12*"),   // Minor scale box 5 pattern 1
        shape(CagedBox.POS5, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
            "E:10,12* | A:10,12 | D:9,12 | G:9*,12 | B:10,12 | e:10,12*"),   // Minor pentatonic scale box 5
        shape(CagedBox.POS5, CagedMode.Minor, ScaleSubset.Triad, 1,
            "E:12* | A:10 | D:9 | G:9*,12 | B:12 | e:12*"),   // Minor triad box 5
    )

    /** How many fingerings the sheet draws for this diagram - 2 for the scale of
     *  boxes 1 and 4, otherwise 1. */
    fun patternCount(box: CagedBox, mode: CagedMode, subset: ScaleSubset): Int =
        if (SHAPES.containsKey(ShapeKey(box, mode, subset, 2))) 2 else 1

    fun dots(box: CagedBox, mode: CagedMode, subset: ScaleSubset, pattern: Int = 1): List<ShapeDot> =
        SHAPES[ShapeKey(box, mode, subset, pattern)]
            ?: SHAPES.getValue(ShapeKey(box, mode, subset, 1))

    /**
     * Place [dots] on the neck for key [tonic]: the anchor is the tonic's fret on
     * the low-E string, nudged by an octave when the shape would otherwise fall
     * off either end of a [numFrets] neck (box 5 of D# would need fret 24; box 1
     * of E would need fret -2).
     */
    fun anchorFor(tonic: PitchClass, dots: List<ShapeDot>, tuning: Tuning, numFrets: Int = 22): Int {
        val lowEpc = tuning.openStrings[0].pitchClass.value
        var base = ((tonic.value - lowEpc) % 12 + 12) % 12
        val lo = dots.minOf { it.offset }
        val hi = dots.maxOf { it.offset }
        if (base + hi > numFrets && base - 12 + lo >= 0) base -= 12
        if (base + lo < 0) base += 12
        return base
    }
}

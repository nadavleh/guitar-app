package app.guitar.theory

/**
 * The CAGED 5-position system for GUITAR (standard tuning), behind the
 * "Guitar practice" trainer. See
 * docs/superpowers/specs/2026-07-25-caged-scales-triads-design.md.
 *
 * Fret placement is NOT computed here any more. Every shape is read verbatim out
 * of [CagedShapeTable], which transcribes Nadav's hand-drawn sheet dot for dot.
 * The old fret-window generator (`[T+loOffset, T+hiOffset]`, sweep up every scale
 * tone inside) approximated those fingerings but never matched them: the real
 * boxes reach a fret back or forward on individual strings, and the pentatonic
 * and triad shapes are hand-picked rather than filtered from the scale.
 *
 * Mirror of chorect-web/src/theory/cagedScales.ts.
 */

/**
 * The 5 CAGED positions, low to high on the neck, each named for the open-chord
 * shape it contains. Placement comes from [CagedShapeTable]; the enum is only an
 * index and a label.
 */
enum class CagedBox(val cagedShape: String) {
    POS1("E"), POS2("D"), POS3("C"), POS4("A"), POS5("G");

    /** 1-based box number, as the sheet labels it. */
    val number: Int get() = ordinal + 1
}

/** Which note-subset of the box to show/play. */
enum class ScaleSubset { Triad, Pentatonic, FullScale }

/** Whether the box is drilled major or parallel-minor (same root, natural minor). */
enum class CagedMode { Major, Minor }

/** One triad voicing on a 3-string group: strings low→high, their frets, the
 *  bass interval above the triad root, and the inversion (0=root,1=1st,2=2nd). */
data class TriadShape(
    val strings: List<Int>,
    val frets: List<Int>,
    val bassInterval: Interval,
    val inversion: Int,
)

/** One sounding note of a resolved box: where on the neck, plus its role. */
data class CagedNote(
    val position: FretPosition,
    /** Interval above the active-mode root (0=root, 7=fifth, …). */
    val interval: Interval,
    val isRoot: Boolean,
)

/** One step of the guided Practice run. */
data class DrillStep(
    val box: CagedBox,
    val mode: CagedMode,
    val subset: ScaleSubset,
    val pattern: Int = 1,
)

object CagedScales {

    val MAJOR_SCALE = Scale(
        "Major",
        listOf(Interval.P1, Interval.maj2, Interval.maj3, Interval.P4, Interval.P5, Interval.maj6, Interval.maj7),
    )

    val BOXES: List<CagedBox> = CagedBox.entries.toList()

    /** Root of the active mode: the SAME [tonic] for both major and parallel minor. */
    fun rootOf(tonic: PitchClass, mode: CagedMode): PitchClass = tonic

    /**
     * The sheet's shape for [box] × [mode] × [subset] × [pattern], transposed to
     * key [tonic] on [tuning] and labelled against the active-mode root. Notes
     * that would fall off a [numFrets] neck are dropped (see
     * [CagedShapeTable.anchorFor], which first tries to avoid that by octave).
     */
    fun resolve(
        tonic: PitchClass,
        box: CagedBox,
        mode: CagedMode,
        subset: ScaleSubset,
        tuning: Tuning,
        numFrets: Int = 22,
        pattern: Int = 1,
    ): List<CagedNote> {
        val dots = CagedShapeTable.dots(box, mode, subset, pattern)
        val base = CagedShapeTable.anchorFor(tonic, dots, tuning, numFrets)
        val root = rootOf(tonic, mode)
        return dots.mapNotNull { d ->
            val f = base + d.offset
            if (f < 0 || f > numFrets || d.string >= tuning.stringCount) return@mapNotNull null
            val pc = Fretboard.noteAt(tuning, FretPosition(d.string, f)).pitchClass
            CagedNote(FretPosition(d.string, f), Interval(((pc.value - root.value) % 12 + 12) % 12), d.isRoot)
        }.sortedWith(compareBy({ it.position.stringIndex }, { it.position.fret }))
    }

    /** The fret span the shape actually occupies — the label under the neck. */
    fun window(
        tonic: PitchClass,
        box: CagedBox,
        tuning: Tuning,
        mode: CagedMode = CagedMode.Major,
        subset: ScaleSubset = ScaleSubset.FullScale,
        pattern: Int = 1,
        numFrets: Int = 22,
    ): IntRange {
        val dots = CagedShapeTable.dots(box, mode, subset, pattern)
        val base = CagedShapeTable.anchorFor(tonic, dots, tuning, numFrets)
        return (base + dots.minOf { it.offset })..(base + dots.maxOf { it.offset })
    }

    // ---- The guided Practice run ----

    /** Chord tones first, then the whole scale, then the pentatonic. */
    private val SUBSET_ORDER = listOf(ScaleSubset.Triad, ScaleSubset.FullScale, ScaleSubset.Pentatonic)

    /**
     * The steps drilled at one box: both qualities, the LEAD alternating by box
     * index — Nadav's rule "if pos == 0 play major then minor; else if the last
     * thing played was minor, play major, else minor". Where the sheet draws a
     * second fingering (the scale of boxes 1 and 4) both patterns are drilled,
     * pattern 1 first.
     */
    fun drillSteps(box: CagedBox): List<DrillStep> {
        val lead = if (box.ordinal % 2 == 0) CagedMode.Major else CagedMode.Minor
        val other = if (lead == CagedMode.Major) CagedMode.Minor else CagedMode.Major
        fun forMode(mode: CagedMode) = SUBSET_ORDER.flatMap { subset ->
            (1..CagedShapeTable.patternCount(box, mode, subset)).map { DrillStep(box, mode, subset, it) }
        }
        return forMode(lead) + forMode(other)
    }

    /** The whole run: 5 boxes low to high, [drillSteps] at each. */
    val PRACTICE_RUN: List<DrillStep> by lazy { BOXES.flatMap { drillSteps(it) } }

    // ---- Triads: 4 adjacent 3-string groups × 3 inversions × {maj,min} ----

    /**
     * The 4 adjacent 3-string groups, **top group first** — guitar strings
     * 1-2-3, then 2-3-4, 3-4-5, 4-5-6. (Indices here are 0 = low E, so the lists
     * read high→low.) This is the order Nadav drills them in.
     */
    val TRIAD_GROUPS: List<List<Int>> = listOf(
        listOf(3, 4, 5), listOf(2, 3, 4), listOf(1, 2, 3), listOf(0, 1, 2),
    )

    /** The 3 close-voiced inversions of a [quality] ("maj"/"min") triad on each of
     *  the 4 string groups, ascending the neck — 4 × 3 = 12 shapes.
     *
     *  Pinned to Nadav's triad sheet (`docs/caged-shapes-source.md`, D major) by
     *  three rules that are NOT free choices:
     *   - **no open strings** — these are movable shapes, so the search starts at
     *     fret 1; an open-string voicing is a different (unmovable) grip;
     *   - **complete triads only** — a close voicing that lands on root/3rd/3rd
     *     (which happens once the open string is off the table, e.g. E-A-D in D at
     *     fret 2) is not a triad and is skipped, pushing that inversion up the neck;
     *   - **neck order, not inversion order** — within a string group the three
     *     shapes come out low → high, the order they are drilled in. */
    fun triadInversions(
        keyTonic: PitchClass,
        quality: String,
        tuning: Tuning,
        numFrets: Int = 22,
    ): List<TriadShape> {
        val root = keyTonic.value % 12
        val third = ((root + (if (quality == "maj") 4 else 3)) % 12)
        val fifth = ((root + 7) % 12)
        val triadPcs = setOf(root, third, fifth)
        val out = ArrayList<TriadShape>()
        for (group in TRIAD_GROUPS) {
            val (a, b, c) = Triple(group[0], group[1], group[2])
            val found = ArrayList<TriadShape>()
            val seenBass = HashSet<Int>()
            var f0 = 1
            while (f0 <= numFrets && seenBass.size < 3) {
                val m0 = Fretboard.noteAt(tuning, FretPosition(a, f0)).midi.value
                if (m0 % 12 in triadPcs) {
                    val f1 = nextTone(tuning, b, m0, triadPcs, numFrets)
                    val m1 = if (f1 >= 0) Fretboard.noteAt(tuning, FretPosition(b, f1)).midi.value else 0
                    val f2 = if (f1 >= 0) nextTone(tuning, c, m1, triadPcs, numFrets) else -1
                    if (f1 >= 0 && f2 >= 0) {
                        val m2 = Fretboard.noteAt(tuning, FretPosition(c, f2)).midi.value
                        val span = listOf(f0, f1, f2).max() - listOf(f0, f1, f2).min()
                        val complete = setOf(m0 % 12, m1 % 12, m2 % 12).size == 3
                        val bassPc = m0 % 12
                        if (span <= 5 && complete && bassPc !in seenBass) {
                            seenBass.add(bassPc)
                            val inv = if (bassPc == root) 0 else if (bassPc == third) 1 else 2
                            found.add(TriadShape(group, listOf(f0, f1, f2), Interval(((bassPc - root) % 12 + 12) % 12), inv))
                        }
                    }
                }
                f0++
            }
            out.addAll(found)   // neck order — see triadInversions' contract
        }
        return out
    }

    /**
     * The Triads drill, in Nadav's order: the top 3-string group's 3 shapes low →
     * high the neck, then 2-3-4, 3-4-5, 4-5-6 — all **major**, then the whole run
     * again **minor**. 24 voicings.
     */
    fun triadRun(keyTonic: PitchClass, tuning: Tuning, numFrets: Int = 22): List<Pair<String, TriadShape>> =
        triadInversions(keyTonic, "maj", tuning, numFrets).map { "maj" to it } +
            triadInversions(keyTonic, "min", tuning, numFrets).map { "min" to it }

    /** Smallest FRETTED (>= 1) fret on [str] whose note is a triad tone strictly
     *  above [aboveMidi]. Open strings are excluded: see [triadInversions]. */
    private fun nextTone(tuning: Tuning, str: Int, aboveMidi: Int, triadPcs: Set<Int>, numFrets: Int): Int {
        for (f in 1..numFrets) {
            val m = Fretboard.noteAt(tuning, FretPosition(str, f)).midi.value
            if (m > aboveMidi && m % 12 in triadPcs) return f
        }
        return -1
    }

    // ---- Explore tab (free scale/position browser, not part of the drill) ----

    val EXPLORE_MAJOR = MAJOR_SCALE
    val EXPLORE_MINOR = Scale("natural minor", listOf(Interval.P1, Interval.maj2, Interval.min3, Interval.P4, Interval.P5, Interval.min6, Interval.min7))
    val EXPLORE_PENTATONIC = Scale("minor pentatonic", listOf(Interval.P1, Interval.min3, Interval.P4, Interval.P5, Interval.min7))

    /** Positions of an arbitrary scale for the Explore tab's scroller. */
    fun explorePositions(root: PitchClass, scale: Scale, tuning: Tuning, numFrets: Int = 22): List<ScalePosition> =
        ScalePositions.forScale(root, scale, tuning, numFrets)
}

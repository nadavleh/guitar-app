package app.guitar.theory

/**
 * The CAGED 5-position major-scale system for GUITAR (standard tuning), for the
 * "Scales & Triads" practice trainer. See
 * docs/superpowers/specs/2026-07-25-caged-scales-triads-design.md.
 *
 * Each position is simply **every major-scale tone inside a fixed fret window**;
 * the window is `[T+loOffset, T+hiOffset]` where `T` is the fret of the parent-
 * major tonic on the low‑E string (string 0), placed in the lowest octave that
 * keeps the window on the neck. A fixed window is exactly the user's "clean
 * positional window, no backward reach" fingering convention. Offsets were read
 * off the standard "5 connected positions" diagram and verified in G major.
 */
/**
 * Fret-window offsets (from the tonic's low-E fret) for each of the 5 positions.
 * MAJOR windows match the standard "5 major scale patterns" diagram. MINOR uses
 * separate, ROOT-ANCHORED windows (the root is the lowest note — no reach back
 * below it), per Nadav's fingering rule; audited complete for every key.
 */
enum class CagedBox(
    val loOffset: Int, val hiOffset: Int,
    val minLoOffset: Int, val minHiOffset: Int,
) {
    POS1(-1, 2, 0, 4),
    POS2(1, 5, 2, 6),
    POS3(4, 7, 4, 8),
    POS4(6, 10, 7, 11),
    POS5(8, 12, 9, 13);

    fun lo(mode: CagedMode): Int = if (mode == CagedMode.Major) loOffset else minLoOffset
    fun hi(mode: CagedMode): Int = if (mode == CagedMode.Major) hiOffset else minHiOffset
}

/** Which note-subset of the box to show/play. */
enum class ScaleSubset { Triad, Pentatonic, FullScale }

/** Whether the box is rooted on its parent-major tonic or its relative minor. */
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

object CagedScales {

    val MAJOR_SCALE = Scale(
        "Major",
        listOf(Interval.P1, Interval.maj2, Interval.maj3, Interval.P4, Interval.P5, Interval.maj6, Interval.maj7),
    )

    /** Pitch classes of the subset. Minor is the PARALLEL minor of [tonic] (same
     *  root, natural minor) — NOT the relative minor — so the box stays in the
     *  same neck position and only the notes change. */
    private fun subsetPcs(tonic: PitchClass, mode: CagedMode, subset: ScaleSubset): Set<PitchClass> {
        fun pc(semis: Int) = PitchClass((tonic.value + semis) % 12)
        val degrees = when (mode) {
            CagedMode.Major -> when (subset) {
                ScaleSubset.FullScale -> listOf(0, 2, 4, 5, 7, 9, 11)
                ScaleSubset.Pentatonic -> listOf(0, 2, 4, 7, 9)         // major pentatonic
                ScaleSubset.Triad -> listOf(0, 4, 7)                    // major triad
            }
            CagedMode.Minor -> when (subset) {
                ScaleSubset.FullScale -> listOf(0, 2, 3, 5, 7, 8, 10)   // natural minor
                ScaleSubset.Pentatonic -> listOf(0, 3, 5, 7, 10)        // minor pentatonic
                ScaleSubset.Triad -> listOf(0, 3, 7)                    // minor triad
            }
        }
        return degrees.map { pc(it) }.toSet()
    }

    /** Root of the active mode: the SAME [tonic] for both major and parallel minor. */
    fun rootOf(tonic: PitchClass, mode: CagedMode): PitchClass = tonic

    /**
     * Resolve [box] of the CAGED system for parent-major key [tonic] on [tuning]
     * (assumed 6-string standard, string 0 = low E), returning the [subset] notes
     * in the box window, each labelled relative to the active-mode root.
     */
    fun resolve(
        tonic: PitchClass,
        box: CagedBox,
        mode: CagedMode,
        subset: ScaleSubset,
        tuning: Tuning,
        numFrets: Int = 22,
    ): List<CagedNote> {
        val lowEpc = tuning.openStrings[0].pitchClass.value
        // T = tonic's lowest fret on the low-E string (0..11). The boxes then run
        // up the neck from there; a below-nut POS1 note is just clipped at fret 0.
        // (Do NOT shift the whole set up an octave — that pushes POS4/POS5 off the
        // neck and drops notes; the neck must simply be long enough, hence 22.)
        val t = ((tonic.value - lowEpc) % 12 + 12) % 12
        val lo = t + box.lo(mode)
        val hi = t + box.hi(mode)
        val pcs = subsetPcs(tonic, mode, subset)
        val root = rootOf(tonic, mode)
        val out = ArrayList<CagedNote>()
        for (s in 0 until tuning.stringCount) {
            for (f in maxOf(lo, 0)..minOf(hi, numFrets)) {
                val pc = Fretboard.noteAt(tuning, FretPosition(s, f)).pitchClass
                if (pc in pcs) {
                    val interval = Interval(((pc.value - root.value) % 12 + 12) % 12)
                    out.add(CagedNote(FretPosition(s, f), interval, pc == root))
                }
            }
        }
        return out
    }

    // ---- Triads: 4 adjacent 3-string groups × 3 inversions × {maj,min} ----

    /** The 4 adjacent 3-string groups, low→high (6-5-4, 5-4-3, 4-3-2, 3-2-1). */
    val TRIAD_GROUPS: List<List<Int>> = listOf(
        listOf(0, 1, 2), listOf(1, 2, 3), listOf(2, 3, 4), listOf(3, 4, 5),
    )

    /** The 3 close-voiced inversions of a [quality] ("maj"/"min") triad on each of
     *  the 4 string groups, ascending — 4 × 3 = 12 shapes. */
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
            var f0 = 0
            while (f0 <= numFrets && seenBass.size < 3) {
                val m0 = Fretboard.noteAt(tuning, FretPosition(a, f0)).midi.value
                if (m0 % 12 in triadPcs) {
                    val f1 = nextTone(tuning, b, m0, triadPcs, numFrets)
                    val f2 = if (f1 >= 0) nextTone(tuning, c, Fretboard.noteAt(tuning, FretPosition(b, f1)).midi.value, triadPcs, numFrets) else -1
                    if (f1 >= 0 && f2 >= 0) {
                        val fretted = listOf(f0, f1, f2).filter { it > 0 }
                        val span = if (fretted.isEmpty()) 0 else fretted.max() - fretted.min()
                        val bassPc = m0 % 12
                        if (span <= 5 && bassPc !in seenBass) {
                            seenBass.add(bassPc)
                            val inv = if (bassPc == root) 0 else if (bassPc == third) 1 else 2
                            found.add(TriadShape(group, listOf(f0, f1, f2), Interval(((bassPc - root) % 12 + 12) % 12), inv))
                        }
                    }
                }
                f0++
            }
            found.sortBy { it.inversion }
            out.addAll(found)
        }
        return out
    }

    /** Smallest fret on [str] whose note is a triad tone strictly above [aboveMidi]. */
    private fun nextTone(tuning: Tuning, str: Int, aboveMidi: Int, triadPcs: Set<Int>, numFrets: Int): Int {
        for (f in 0..numFrets) {
            val m = Fretboard.noteAt(tuning, FretPosition(str, f)).midi.value
            if (m > aboveMidi && m % 12 in triadPcs) return f
        }
        return -1
    }

    /** The window [firstFret, lastFret] a box occupies for [tonic] on [tuning]. */
    fun window(tonic: PitchClass, box: CagedBox, tuning: Tuning, mode: CagedMode = CagedMode.Major): IntRange {
        val lowEpc = tuning.openStrings[0].pitchClass.value
        val t = ((tonic.value - lowEpc) % 12 + 12) % 12
        return (t + box.lo(mode))..(t + box.hi(mode))
    }

    // ---- 7-position practice (mirrors the Fretboard "scales by position") ----

    val EXPLORE_MAJOR = MAJOR_SCALE
    val EXPLORE_MINOR = Scale("natural minor", listOf(Interval.P1, Interval.maj2, Interval.min3, Interval.P4, Interval.P5, Interval.min6, Interval.min7))
    val EXPLORE_PENTATONIC = Scale("minor pentatonic", listOf(Interval.P1, Interval.min3, Interval.P4, Interval.P5, Interval.min7))

    /** Fret windows of the key's MAJOR-scale positions (the same engine as the
     *  Fretboard "scales by position"), but STARTING one box lower than the root
     *  position: the first box reaches down so the major 3rd sits on the next-higher
     *  string (e.g. G major: B on the A string, fret 2). That box's scale is drilled
     *  first, then the root-anchored box, then the rest up the neck. */
    fun practiceRegions(tonic: PitchClass, tuning: Tuning, numFrets: Int = 22): List<IntRange> {
        val base = ScalePositions.forScale(tonic, MAJOR_SCALE, tuning, numFrets).map { it.firstFret..it.lastFret }
        if (base.isEmpty()) return base
        val lowEpc = tuning.openStrings[0].pitchClass.value
        val rootFret = ((tonic.value - lowEpc) % 12 + 12) % 12
        val lo = (rootFret - 1).coerceAtLeast(0)
        val firstBox = lo..(lo + ScalePositions.DEFAULT_MAX_FRET_SPAN).coerceAtMost(numFrets)
        // Prepend the 3rd-reaching box; drop any existing window identical to it (dedupe).
        return listOf(firstBox) + base.filter { it != firstBox }
    }

    /** [subset] notes of [mode] (parallel minor = same [tonic]) inside window [lo,hi]. */
    fun notesInWindow(tonic: PitchClass, lo: Int, hi: Int, mode: CagedMode, subset: ScaleSubset, tuning: Tuning, numFrets: Int = 22): List<CagedNote> {
        val pcs = subsetPcs(tonic, mode, subset)
        val out = ArrayList<CagedNote>()
        for (s in 0 until tuning.stringCount) {
            for (f in maxOf(lo, 0)..minOf(hi, numFrets)) {
                val pc = Fretboard.noteAt(tuning, FretPosition(s, f)).pitchClass
                if (pc in pcs) out.add(CagedNote(FretPosition(s, f), Interval(((pc.value - tonic.value) % 12 + 12) % 12), pc == tonic))
            }
        }
        return out
    }

    /** Positions of an arbitrary scale for the Explore tab's scroller. */
    fun explorePositions(root: PitchClass, scale: Scale, tuning: Tuning, numFrets: Int = 22): List<ScalePosition> =
        ScalePositions.forScale(root, scale, tuning, numFrets)
}

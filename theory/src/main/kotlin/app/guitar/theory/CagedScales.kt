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
enum class CagedBox(val loOffset: Int, val hiOffset: Int) {
    POS1(-1, 2),
    POS2(1, 5),
    POS3(4, 7),
    POS4(6, 10),
    POS5(8, 12);
}

/** Which note-subset of the box to show/play. */
enum class ScaleSubset { Triad, Pentatonic, FullScale }

/** Whether the box is rooted on its parent-major tonic or its relative minor. */
enum class CagedMode { Major, Minor }

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
        val lo = t + box.loOffset
        val hi = t + box.hiOffset
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

    /** The window [firstFret, lastFret] a box occupies for [tonic] on [tuning]. */
    fun window(tonic: PitchClass, box: CagedBox, tuning: Tuning): IntRange {
        val lowEpc = tuning.openStrings[0].pitchClass.value
        val t = ((tonic.value - lowEpc) % 12 + 12) % 12
        return (t + box.loOffset)..(t + box.hiOffset)
    }
}

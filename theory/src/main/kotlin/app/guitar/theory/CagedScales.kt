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

    /** Pitch classes of the subset, for a parent-major [tonic] and [mode].
     *  Full scale + pentatonic are mode-independent note sets (only the root
     *  differs); triad is the I triad (major) or the vi / relative-minor triad. */
    private fun subsetPcs(tonic: PitchClass, mode: CagedMode, subset: ScaleSubset): Set<PitchClass> {
        fun pc(semis: Int) = PitchClass((tonic.value + semis) % 12)
        return when (subset) {
            ScaleSubset.FullScale -> setOf(0, 2, 4, 5, 7, 9, 11).map { pc(it) }.toSet()
            // Major pentatonic of the tonic == minor pentatonic of the relative minor.
            ScaleSubset.Pentatonic -> setOf(0, 2, 4, 7, 9).map { pc(it) }.toSet()
            ScaleSubset.Triad -> when (mode) {
                CagedMode.Major -> setOf(0, 4, 7).map { pc(it) }.toSet()
                CagedMode.Minor -> setOf(9, 0, 4).map { pc(it) }.toSet()   // relative-minor triad
            }
        }
    }

    /** Root pitch class for the active mode of a box in parent-major key [tonic]. */
    fun rootOf(tonic: PitchClass, mode: CagedMode): PitchClass =
        if (mode == CagedMode.Major) tonic else PitchClass((tonic.value + 9) % 12)

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
        numFrets: Int = 17,
    ): List<CagedNote> {
        val lowEpc = tuning.openStrings[0].pitchClass.value
        // T = tonic fret on the low-E string, lowest octave keeping the window >= 0.
        var t = ((tonic.value - lowEpc) % 12 + 12) % 12
        while (t + box.loOffset < 0) t += 12
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
        var t = ((tonic.value - lowEpc) % 12 + 12) % 12
        while (t + box.loOffset < 0) t += 12
        return (t + box.loOffset)..(t + box.hiOffset)
    }
}

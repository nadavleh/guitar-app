package app.guitar.theory

/**
 * A playable "position" of a scale on the neck: a contiguous window of frets
 * (≤ [maxFretSpan]+1 frets wide) anchored on a specific scale-degree pitch class
 * on the lowest string, containing all the scale tones within that window and
 * at least [minRootInstances] root notes.
 *
 * For a 7-note diatonic scale, [ScalePositions.forScale] returns up to 7 positions
 * (one per scale degree). For pentatonic scales: up to 5. Generally: up to N positions
 * for an N-note scale.
 */
data class ScalePosition(
    /** 1-based index of this position within the parent scale's position list. */
    val index: Int,
    /** Which scale-degree pitch class anchors this position on the lowest string. */
    val anchorPitchClass: PitchClass,
    /** Lowest fret of the window (the fret on the lowest string where [anchorPitchClass] appears). */
    val firstFret: Int,
    /** Highest fret of the window (inclusive, ≤ firstFret + maxFretSpan). */
    val lastFret: Int,
    /** Every fret-position in this window that belongs to the scale. */
    val positions: List<FretPosition>,
    /** How many of [positions] are the scale's root. */
    val rootCount: Int,
)

object ScalePositions {

    const val DEFAULT_MAX_FRET_SPAN: Int = 4
    const val DEFAULT_MIN_ROOT_INSTANCES: Int = 2

    /**
     * Find all positions of [scale] (rooted on [root]) on [tuning] within [numFrets].
     *
     * Algorithm (matches the spec in `GUI_DESIGN.md §7`):
     *   for each scale-degree i:
     *     anchor = lowest fret on the lowest string (index 0) where
     *              `root + scale.intervals[i]` appears, in 0..numFrets
     *     window = [anchor, anchor + maxFretSpan] ∩ [0, numFrets]
     *     positions = every (string, fret) in window whose pc is in the scale
     *     if positions has ≥ minRootInstances roots → emit
     *     else skip (degenerate degree on this tuning)
     */
    fun forScale(
        root: PitchClass,
        scale: Scale,
        tuning: Tuning,
        numFrets: Int,
        maxFretSpan: Int = DEFAULT_MAX_FRET_SPAN,
        minRootInstances: Int = DEFAULT_MIN_ROOT_INSTANCES,
    ): List<ScalePosition> {
        val scalePcs: Set<PitchClass> = scale.notesFrom(root).toSet()
        val out = ArrayList<ScalePosition>(scale.intervals.size)

        scale.intervals.forEachIndexed { i, interval ->
            val anchorPc = root + interval
            val anchor = lowestFretOnString0(anchorPc, tuning, numFrets) ?: return@forEachIndexed
            val lastFret = (anchor + maxFretSpan).coerceAtMost(numFrets)
            val collected = ArrayList<FretPosition>()
            var rootCount = 0
            for (s in 0 until tuning.stringCount) {
                for (f in anchor..lastFret) {
                    val pc = Fretboard.noteAt(tuning, FretPosition(s, f)).pitchClass
                    if (pc in scalePcs) {
                        collected.add(FretPosition(s, f))
                        if (pc == root) rootCount++
                    }
                }
            }
            if (rootCount >= minRootInstances) {
                out.add(
                    ScalePosition(
                        index = out.size + 1,
                        anchorPitchClass = anchorPc,
                        firstFret = anchor,
                        lastFret = lastFret,
                        positions = collected,
                        rootCount = rootCount,
                    )
                )
            }
        }
        return out
    }

    private fun lowestFretOnString0(pc: PitchClass, tuning: Tuning, numFrets: Int): Int? {
        for (f in 0..numFrets) {
            if (Fretboard.noteAt(tuning, FretPosition(0, f)).pitchClass == pc) return f
        }
        return null
    }
}

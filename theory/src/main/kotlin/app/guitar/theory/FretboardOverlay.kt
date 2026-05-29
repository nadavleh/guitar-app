package app.guitar.theory

data class FretboardHighlight(
    val pitchClass: PitchClass,
    val interval: Interval,
    val isRoot: Boolean,
)

object FretboardOverlay {
    fun chord(
        root: PitchClass,
        quality: ChordQuality,
        tuning: Tuning,
        numFrets: Int,
    ): Map<FretPosition, FretboardHighlight> =
        scan(root, quality.intervals, tuning, numFrets)

    fun scale(
        root: PitchClass,
        scale: Scale,
        tuning: Tuning,
        numFrets: Int,
    ): Map<FretPosition, FretboardHighlight> =
        scan(root, scale.intervals, tuning, numFrets)

    private fun scan(
        root: PitchClass,
        intervals: List<Interval>,
        tuning: Tuning,
        numFrets: Int,
    ): Map<FretPosition, FretboardHighlight> {
        val intervalByPc: Map<PitchClass, Interval> = intervals.associateBy { root + it }
        val result = HashMap<FretPosition, FretboardHighlight>()
        for (s in 0 until tuning.stringCount) {
            for (f in 0..numFrets) {
                val pc = Fretboard.noteAt(tuning, FretPosition(s, f)).pitchClass
                val interval = intervalByPc[pc] ?: continue
                result[FretPosition(s, f)] = FretboardHighlight(
                    pitchClass = pc,
                    interval = interval,
                    isRoot = pc == root,
                )
            }
        }
        return result
    }
}

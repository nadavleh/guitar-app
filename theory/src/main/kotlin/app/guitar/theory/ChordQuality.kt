package app.guitar.theory

data class ChordQuality(val symbol: String, val intervals: List<Interval>) {
    fun notesFrom(root: PitchClass): List<PitchClass> = intervals.map { root + it }
}

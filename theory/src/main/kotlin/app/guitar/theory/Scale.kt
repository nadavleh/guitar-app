package app.guitar.theory

data class Scale(val name: String, val intervals: List<Interval>) {
    fun notesFrom(root: PitchClass): List<PitchClass> = intervals.map { root + it }
}

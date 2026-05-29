package app.guitar.theory

object ScaleLibrary {
    val scales: Map<String, Scale> = linkedMapOf(
        "major"            to Scale("major",            listOf(Interval.P1, Interval.maj2, Interval.maj3, Interval.P4, Interval.P5, Interval.maj6, Interval.maj7)),
        "natural minor"    to Scale("natural minor",    listOf(Interval.P1, Interval.maj2, Interval.min3, Interval.P4, Interval.P5, Interval.min6, Interval.min7)),
        "major pentatonic" to Scale("major pentatonic", listOf(Interval.P1, Interval.maj2, Interval.maj3, Interval.P5, Interval.maj6)),
        "minor pentatonic" to Scale("minor pentatonic", listOf(Interval.P1, Interval.min3, Interval.P4, Interval.P5, Interval.min7)),
        "blues"            to Scale("blues",            listOf(Interval.P1, Interval.min3, Interval.P4, Interval.TT, Interval.P5, Interval.min7)),
        "dorian"           to Scale("dorian",           listOf(Interval.P1, Interval.maj2, Interval.min3, Interval.P4, Interval.P5, Interval.maj6, Interval.min7)),
        "mixolydian"       to Scale("mixolydian",       listOf(Interval.P1, Interval.maj2, Interval.maj3, Interval.P4, Interval.P5, Interval.maj6, Interval.min7)),
    )

    fun notes(root: PitchClass, scale: Scale): List<PitchClass> = scale.notesFrom(root)
}

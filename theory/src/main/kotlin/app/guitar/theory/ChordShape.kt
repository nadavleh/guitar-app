package app.guitar.theory

data class ChordShape(
    val chordName: String,
    val root: PitchClass,
    val quality: ChordQuality,
    val frets: List<Int?>,
    val tuning: Tuning
) {
    init {
        require(frets.size == tuning.stringCount) {
            "frets list size ${frets.size} != tuning.stringCount ${tuning.stringCount}"
        }
        for (f in frets) {
            if (f != null) require(f >= 0) { "fret must be >= 0, got $f" }
        }
    }

    private val frettedNonZero: List<Int> get() = frets.filterNotNull().filter { it > 0 }
    private val played: List<Int> get() = frets.filterNotNull()

    val position: Int get() = frettedNonZero.minOrNull() ?: 0

    val fretSpan: Int get() {
        val f = frettedNonZero
        return if (f.isEmpty()) 0 else (f.max() - f.min())
    }

    val notes: List<Note?> get() = frets.mapIndexed { i, f ->
        if (f == null) null else Fretboard.noteAt(tuning, FretPosition(i, f))
    }

    val intervals: List<Interval?> get() = notes.map { n ->
        if (n == null) null else Interval(((n.pitchClass.value - root.value) % 12 + 12) % 12)
    }

    val bassPitchClass: PitchClass? get() = notes.firstOrNull { it != null }?.pitchClass

    val hasRootInBass: Boolean get() = bassPitchClass == root

    val mutedCount: Int get() = frets.count { it == null }

    val playedCount: Int get() = played.size
}

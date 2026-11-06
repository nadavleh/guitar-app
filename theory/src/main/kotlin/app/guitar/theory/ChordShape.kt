package app.guitar.theory

data class ChordShape(
    val chordName: String,
    val root: PitchClass,
    val quality: ChordQuality,
    val frets: List<Int?>,
    val tuning: Tuning,
    /** Which CAGED template this shape came from. Null for non-CAGED voicings (jazz drop-2, brute-force, etc.). */
    val cagedShape: CagedShape? = null,
    /** Free-form label for non-CAGED voicings (e.g. "drop-2 root-pos"). Null when [cagedShape] is non-null or for brute-force shapes. */
    val templateName: String? = null,
) {
    init {
        require(frets.size == tuning.stringCount) {
            "frets list size ${frets.size} != tuning.stringCount ${tuning.stringCount}"
        }
        for (f in frets) {
            if (f != null) require(f >= 0) { "fret must be >= 0, got $f" }
        }
    }

    // Derived views are computed once per shape: the generators' rankers and the UI read
    // them repeatedly (every comparator call used to rebuild `notes`).
    private val frettedNonZero: List<Int> by lazy(LazyThreadSafetyMode.PUBLICATION) { frets.filterNotNull().filter { it > 0 } }

    val position: Int get() = frettedNonZero.minOrNull() ?: 0

    val fretSpan: Int get() {
        val f = frettedNonZero
        return if (f.isEmpty()) 0 else (f.max() - f.min())
    }

    val notes: List<Note?> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        frets.mapIndexed { i, f -> if (f == null) null else Fretboard.noteAt(tuning, FretPosition(i, f)) }
    }

    /** MIDI numbers of the sounding strings, low string first. */
    val midis: List<Int> get() = notes.mapNotNull { it?.midi?.value }

    val intervals: List<Interval?> get() = notes.map { n ->
        if (n == null) null else Interval(((n.pitchClass.value - root.value) % 12 + 12) % 12)
    }

    val bassPitchClass: PitchClass? get() = notes.firstOrNull { it != null }?.pitchClass

    val hasRootInBass: Boolean get() = bassPitchClass == root

    val mutedCount: Int get() = frets.count { it == null }

    val playedCount: Int get() = frets.count { it != null }
}

/** The E-shape grip if there is one, else the first shape — the default "show me this
 *  chord" voicing (E-shape is the canonical movable barre). Null only when empty. */
fun List<ChordShape>.eShapeOrFirst(): ChordShape? = firstOrNull { it.cagedShape == CagedShape.E } ?: firstOrNull()

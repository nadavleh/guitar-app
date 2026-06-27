package app.guitar.theory

/**
 * How an extended/altered chord splits into a **shell** (root + guide tones, the
 * pianist's left hand) and an **upper-structure triad** (the right hand) — the
 * "shell + triad" way of voicing and learning extended chords on guitar (#5).
 *
 * All intervals are semitones from the chord root. Upper-structure intervals may
 * exceed 12 (they sit an octave up). The decomposition is intentionally a *voicing*
 * (the perfect 5th is often dropped), not the full literal chord — that's the point
 * of the technique: a compact shell plus a familiar triad on top.
 */
data class ChordDecomposition(
    val quality: String,        // chord-quality symbol the picker uses ("", "maj7", "13"…)
    val displayName: String,    // short label for the picker ("maj7", "13"…)
    val shell: List<Int>,       // bass group — semitone intervals from the root
    val upper: List<Int>,       // upper triad — semitone intervals from the root
    val upperTriad: String,     // "major" / "minor" / "diminished" / "augmented"
    /** Degree description of the upper triad, e.g. "5–♭7–9" or "6–1–3". */
    val upperDegrees: String,
) {
    /** Upper-triad root as a semitone interval from the chord root (lowest upper note, mod 12). */
    val upperRootInterval: Int get() = (upper.minOrNull() ?: 0) % 12
}

object ChordDecompositions {

    private fun d(
        quality: String, display: String,
        shell: List<Int>, upper: List<Int>, upperTriad: String, upperDegrees: String,
    ) = ChordDecomposition(quality, display, shell, upper, upperTriad, upperDegrees)

    /**
     * The supported chords, in picker order. Intervals: 9=14, ♯9=15, ♭9=13,
     * 11=17, ♯11=18, 13=21, ♭13=20. The upper group is always a 3-note triad.
     */
    val ALL: List<ChordDecomposition> = listOf(
        // 6 chords — drop the 5th; the upper triad sits on the 6th.
        d("6", "6", shell = listOf(0), upper = listOf(9, 12, 16), upperTriad = "minor", upperDegrees = "6–1–3"),
        d("m6", "m6", shell = listOf(0), upper = listOf(9, 12, 15), upperTriad = "diminished", upperDegrees = "6–1–♭3"),
        // 7th chords — upper triad built on the 3rd.
        d("maj7", "maj7", shell = listOf(0), upper = listOf(4, 7, 11), upperTriad = "minor", upperDegrees = "3–5–7"),
        d("7", "7", shell = listOf(0), upper = listOf(4, 7, 10), upperTriad = "diminished", upperDegrees = "3–5–♭7"),
        d("m7", "m7", shell = listOf(0), upper = listOf(3, 7, 10), upperTriad = "major", upperDegrees = "♭3–5–♭7"),
        d("m7b5", "m7♭5", shell = listOf(0), upper = listOf(3, 6, 10), upperTriad = "minor", upperDegrees = "♭3–♭5–♭7"),
        d("dim7", "°7", shell = listOf(0), upper = listOf(3, 6, 9), upperTriad = "diminished", upperDegrees = "♭3–♭5–6"),
        // 9ths — shell (1·3·♭7 / 1·3·7) + a triad spelling 5·7·9.
        d("9", "9", shell = listOf(0, 4, 10), upper = listOf(7, 10, 14), upperTriad = "minor", upperDegrees = "5–♭7–9"),
        d("maj9", "maj9", shell = listOf(0, 4, 11), upper = listOf(7, 11, 14), upperTriad = "major", upperDegrees = "5–7–9"),
        d("m9", "m9", shell = listOf(0, 3, 10), upper = listOf(7, 10, 14), upperTriad = "minor", upperDegrees = "5–♭7–9"),
        // 11ths — upper triad spells ♭7·9·11 (a major triad on the ♭7).
        d("11", "11", shell = listOf(0, 10), upper = listOf(10, 14, 17), upperTriad = "major", upperDegrees = "♭7–9–11"),
        d("m11", "m11", shell = listOf(0, 3, 10), upper = listOf(10, 14, 17), upperTriad = "major", upperDegrees = "♭7–9–11"),
        // 13ths — classic upper-structure: major triad on the 9th spells 9·♯11·13.
        d("13", "13", shell = listOf(0, 4, 10), upper = listOf(14, 18, 21), upperTriad = "major", upperDegrees = "9–♯11–13"),
        d("maj13", "maj13", shell = listOf(0, 4, 11), upper = listOf(14, 18, 21), upperTriad = "major", upperDegrees = "9–♯11–13"),
        // Altered dominants.
        d("7#9", "7♯9", shell = listOf(0, 4), upper = listOf(3, 7, 10), upperTriad = "major", upperDegrees = "♯9–5–♭7"),
        d("7b9", "7♭9", shell = listOf(0, 10), upper = listOf(1, 4, 7), upperTriad = "diminished", upperDegrees = "♭9–3–5"),
    )

    private val byQuality = ALL.associateBy { it.quality }

    fun forQuality(quality: String): ChordDecomposition? = byQuality[quality]
}

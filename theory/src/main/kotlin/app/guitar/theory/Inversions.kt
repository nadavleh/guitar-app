package app.guitar.theory

/**
 * Chord-inversion helpers for the ear-training "Inversions" trainer.
 *
 * An inversion stacks the same chord tones but puts a different tone in the bass:
 *   - inversion 0 = root position (root in bass)
 *   - inversion 1 = 1st inversion (3rd in bass)
 *   - inversion 2 = 2nd inversion (5th in bass)
 *   - inversion 3 = 3rd inversion (7th in bass, 7th chords only)
 */
object Inversions {

    /** Number of distinct inversions a [quality] has (= its tone count). */
    fun count(quality: ChordQuality): Int = quality.intervals.size

    /** Human label for inversion index [k]. */
    fun name(k: Int): String = when (k) {
        0 -> "Root position"
        1 -> "1st inversion"
        2 -> "2nd inversion"
        3 -> "3rd inversion"
        else -> "${k}th inversion"
    }

    /**
     * MIDI notes for [quality] rooted at [rootMidi], voiced in [inversion]. The
     * bottom [inversion] chord tones are lifted an octave so the desired tone sits
     * in the bass; the result is returned low→high.
     */
    fun midis(rootMidi: Int, quality: ChordQuality, inversion: Int): List<Int> {
        val notes = quality.intervals.map { rootMidi + it.semitones }.toMutableList()
        val k = inversion.coerceIn(0, notes.size - 1)
        repeat(k) {
            val low = notes.removeAt(0)
            notes.add(low + 12)
        }
        return notes.sorted()
    }

    /** True when every inversion of [quality] is a mere transposition of root
     *  position (aug, dim7): with the root unknown they sound identical, so the
     *  trainer can't ask which one it is. */
    fun isSymmetric(quality: ChordQuality): Boolean {
        fun shape(k: Int) = midis(0, quality, k).let { m -> m.map { it - m.first() } }
        val root = shape(0)
        return count(quality) > 1 && (1 until count(quality)).all { shape(it) == root }
    }

    /** Whether guessing inversion [guess] for a chord voiced in [actual] is right:
     *  exact match, or ANY inversion of a [isSymmetric] quality. */
    fun isCorrectGuess(quality: ChordQuality, guess: Int, actual: Int): Boolean =
        guess == actual || isSymmetric(quality)
}

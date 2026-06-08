package app.guitar.theory

import kotlin.random.Random

/**
 * "Note 2 Chord" ear-training challenge: a random major or minor triad is
 * played, then a single note from that chord's diatonic scale (non-chord-tone)
 * is played on top. The user identifies the extension/scale-degree label.
 */
data class N2cChallenge(
    /** Root of the underlying triad. */
    val chordRoot: PitchClass,
    /** Whether the triad is minor (true) or major (false). */
    val isMinor: Boolean,
    /** Semitone offset from [chordRoot] of the *test* note played on top. */
    val testNoteOffsetSemitones: Int,
) {
    /** Pitch class of the note played on top. */
    val testNote: PitchClass get() = PitchClass.of(chordRoot.value + testNoteOffsetSemitones)

    /** Chord symbol parseable by [ChordLibrary], e.g. "C" or "Cm". */
    val chordSymbol: String get() = NoteSpeller.spell(chordRoot) + if (isMinor) "m" else ""

    /** The label the user is trying to identify — what the test note IS relative to the chord. */
    val answerLabel: String get() = label(testNoteOffsetSemitones)

    /** Spelled name of the test note, e.g. "F#". */
    val testNoteName: String get() = NoteSpeller.spell(testNote)

    companion object {
        /** Degree label for a semitone offset above the chord root. */
        fun label(offset: Int): String = when (offset) {
            0  -> "1 (root)"
            2  -> "9 (2)"
            3  -> "b3"
            4  -> "3"
            5  -> "11 (4)"
            7  -> "5"
            8  -> "b13 (b6)"
            9  -> "13 (6)"
            10 -> "b7"
            11 -> "maj7"
            else -> "?"
        }

        /** Diatonic NON-chord-tones for ear-training over a major triad.
         *  We exclude root/3/5 since chord tones are trivial. */
        val MAJOR_TEST_OFFSETS = intArrayOf(2, 5, 9, 11)   // 9, 11, 13, maj7
        /** Diatonic NON-chord-tones for a minor triad (natural minor scale).
         *  Exclude root/b3/5. */
        val MINOR_TEST_OFFSETS = intArrayOf(2, 5, 8, 10)   // 9, 11, b13, b7

        /** Generate a uniformly-random challenge. */
        fun random(rng: Random = Random.Default): N2cChallenge {
            val root = PitchClass(rng.nextInt(12))
            val isMinor = rng.nextBoolean()
            val offsets = if (isMinor) MINOR_TEST_OFFSETS else MAJOR_TEST_OFFSETS
            val offset = offsets[rng.nextInt(offsets.size)]
            return N2cChallenge(root, isMinor, offset)
        }
    }
}

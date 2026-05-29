package app.guitar.theory

@JvmInline
value class Midi(val value: Int) {
    init { require(value in 0..127) { "MIDI must be 0..127, got $value" } }

    val pitchClass: PitchClass get() = PitchClass(value % 12)
    val octave: Int get() = (value / 12) - 1

    operator fun plus(semitones: Int): Midi = Midi(value + semitones)
    operator fun plus(interval: Interval): Midi = Midi(value + interval.semitones)
    operator fun minus(other: Midi): Interval = Interval(value - other.value)
}

data class Note(val midi: Midi) {
    val pitchClass: PitchClass get() = midi.pitchClass
    val octave: Int get() = midi.octave

    companion object {
        private val NOTE_REGEX = Regex("([A-G])([#b]?)(-?\\d+)")

        fun parse(text: String): Note {
            val match = NOTE_REGEX.matchEntire(text)
                ?: throw IllegalArgumentException("Invalid note: $text")
            val (letter, accidental, octaveStr) = match.destructured
            val basePc = letterToPitchClass(letter)
            val accOffset = when (accidental) {
                "#" -> 1; "b" -> -1; else -> 0
            }
            val octave = octaveStr.toInt()
            val midi = (octave + 1) * 12 + basePc + accOffset
            return Note(Midi(midi))
        }

        private fun letterToPitchClass(letter: String): Int = when (letter) {
            "C" -> 0; "D" -> 2; "E" -> 4; "F" -> 5
            "G" -> 7; "A" -> 9; "B" -> 11
            else -> throw IllegalArgumentException("Invalid letter: $letter")
        }
    }
}

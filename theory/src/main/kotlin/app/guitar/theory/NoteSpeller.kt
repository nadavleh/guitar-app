package app.guitar.theory

object NoteSpeller {
    private val sharpNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatNames  = arrayOf("C", "Db", "D",  "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    fun spell(pc: PitchClass, prefer: Accidental = Accidental.SHARP): String =
        if (prefer == Accidental.SHARP) sharpNames[pc.value] else flatNames[pc.value]

    fun spell(note: Note, prefer: Accidental = Accidental.SHARP): String =
        "${spell(note.pitchClass, prefer)}${note.octave}"

    private val PC_REGEX = Regex("([A-Ga-g])([#b]?)")

    fun parsePitchClass(text: String): PitchClass {
        val match = PC_REGEX.matchEntire(text.trim())
            ?: throw IllegalArgumentException("Invalid pitch class: $text")
        val (letter, accidental) = match.destructured
        val basePc = when (letter.uppercase()) {
            "C" -> 0; "D" -> 2; "E" -> 4; "F" -> 5
            "G" -> 7; "A" -> 9; "B" -> 11
            else -> throw IllegalArgumentException("Invalid letter: $letter")
        }
        val accOffset = when (accidental) { "#" -> 1; "b" -> -1; else -> 0 }
        return PitchClass.of(basePc + accOffset)
    }
}

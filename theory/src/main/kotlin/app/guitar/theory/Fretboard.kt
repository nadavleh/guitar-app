package app.guitar.theory

data class FretPosition(val stringIndex: Int, val fret: Int) {
    init {
        require(stringIndex >= 0) { "stringIndex must be >= 0, got $stringIndex" }
        require(fret >= 0) { "fret must be >= 0, got $fret" }
    }
}

object Fretboard {
    fun noteAt(tuning: Tuning, pos: FretPosition): Note {
        require(pos.stringIndex < tuning.stringCount) {
            "stringIndex ${pos.stringIndex} out of range for ${tuning.stringCount}-string tuning"
        }
        val open = tuning.openStrings[pos.stringIndex]
        return Note(open.midi + pos.fret)
    }

    fun allPositions(tuning: Tuning, frets: Int, of: PitchClass): List<FretPosition> {
        val result = mutableListOf<FretPosition>()
        for (s in 0 until tuning.stringCount) {
            for (f in 0..frets) {
                if (noteAt(tuning, FretPosition(s, f)).pitchClass == of) {
                    result += FretPosition(s, f)
                }
            }
        }
        return result
    }
}

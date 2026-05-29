package app.guitar.theory

object TuningCodec {
    private const val NAME_DELIM = '|'
    private const val NOTE_DELIM = ','
    private const val ENTRY_DELIM = ';'

    fun encode(tuning: Tuning): String =
        tuning.openStrings.joinToString(NOTE_DELIM.toString()) { NoteSpeller.spell(it) }

    fun decode(encoded: String): Tuning {
        val notes = encoded.split(NOTE_DELIM).map { Note.parse(it.trim()) }
        return Tuning(notes)
    }

    fun encodeNamed(name: String, tuning: Tuning): String {
        require(NAME_DELIM !in name && ENTRY_DELIM !in name) {
            "tuning name cannot contain '$NAME_DELIM' or '$ENTRY_DELIM'"
        }
        require(name.isNotBlank()) { "tuning name cannot be blank" }
        return "$name$NAME_DELIM${encode(tuning)}"
    }

    fun decodeNamed(encoded: String): Pair<String, Tuning> {
        val idx = encoded.indexOf(NAME_DELIM)
        require(idx > 0) { "invalid named tuning: $encoded" }
        val name = encoded.substring(0, idx)
        val notes = encoded.substring(idx + 1)
        return name to decode(notes)
    }

    fun encodeMap(tunings: Map<String, Tuning>): String =
        tunings.entries.joinToString(ENTRY_DELIM.toString()) { (n, t) -> encodeNamed(n, t) }

    fun decodeMap(encoded: String): LinkedHashMap<String, Tuning> {
        val result = LinkedHashMap<String, Tuning>()
        if (encoded.isBlank()) return result
        for (entry in encoded.split(ENTRY_DELIM)) {
            if (entry.isBlank()) continue
            val (name, tuning) = decodeNamed(entry)
            result[name] = tuning
        }
        return result
    }
}

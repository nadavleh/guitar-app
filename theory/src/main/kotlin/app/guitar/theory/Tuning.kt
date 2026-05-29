package app.guitar.theory

data class Tuning(val openStrings: List<Note>) {
    init { require(openStrings.isNotEmpty()) { "Tuning must have at least one string" } }

    val stringCount: Int get() = openStrings.size

    companion object {
        fun of(vararg noteNames: String): Tuning = Tuning(noteNames.map { Note.parse(it) })
    }
}

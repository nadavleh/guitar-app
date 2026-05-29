package app.guitar.theory

/**
 * Heuristic suggested fingering for a chord shape.
 *
 * Returns a list (string-indexed, same order as [ChordShape.frets]) of finger
 * numbers in 1..4 (index, middle, ring, pinky), `null` for muted or open
 * strings, and 1 for *all* strings that share the lowest fretted fret (i.e. a
 * barre is rendered as multiple 1s).
 */
object Fingering {

    fun suggest(shape: ChordShape): List<Int?> {
        val frets = shape.frets
        val nonZero = frets.mapIndexedNotNull { i, f -> if (f != null && f > 0) i to f else null }
        if (nonZero.isEmpty()) return List(frets.size) { null }
        val anchor = nonZero.minOf { it.second }
        // Detect barre: multiple strings share the anchor fret AND the lowest-index
        // (= lowest pitch) one is below at least one HIGHER fret on another string.
        val anchorStrings = nonZero.filter { it.second == anchor }.map { it.first }
        val isBarre = anchorStrings.size >= 2 &&
            nonZero.any { it.second > anchor && it.first > anchorStrings.min() }
        return frets.mapIndexed { i, f ->
            when {
                f == null -> null
                f == 0 -> null
                isBarre && f == anchor -> 1
                else -> (f - anchor + 1).coerceIn(1, 4)
            }
        }
    }
}

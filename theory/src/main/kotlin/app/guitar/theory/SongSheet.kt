package app.guitar.theory

/**
 * Song-sheet theory: transposing a chord symbol, and naming it by its function in
 * a key. Mirrored by chorect-web/src/theory/songSheet.ts — keep the two in step.
 *
 * This is what the Songs tab needs and all it needs: no voicings, no playback. The
 * sheet shows chords over lyrics, transposes them, and can relabel them by degree.
 */
object SongSheet {

    /** A key: its tonic, and whether the song is in a minor mode. */
    data class SongKey(val tonic: PitchClass, val minor: Boolean)

    /**
     * Parse a key written as a chord symbol — "G", "Am", "Bb", "F#m", "Cmaj7".
     *
     * Runs through [ChordLibrary.parseFull] rather than splitting the text by hand:
     * that already knows every quality and its shorthand, so "Cmaj" reads as major
     * and "Am7" as minor without this needing its own suffix rules.
     */
    fun parseKey(text: String): SongKey? {
        val c = ChordLibrary.parseFull(text.trim()) ?: return null
        val q = c.quality.symbol
        val minor = Regex("^(m|min)(?!aj)").containsMatchIn(q) || q == "dim" || q == "dim7"
        return SongKey(c.root, minor)
    }

    /** How a transposed symbol should be spelled. Sheets in flat keys read badly in
     *  sharps, so the key decides rather than a global preference. */
    fun prefersFlats(key: SongKey?): Boolean {
        if (key == null) return false
        // F, Bb, Eb, Ab, Db, Gb major and their relative minors are flat keys.
        val flatTonics = if (key.minor) listOf(5, 10, 3, 8, 1, 2, 7) else listOf(5, 10, 3, 8, 1, 6)
        return flatTonics.contains(key.tonic.value)
    }

    private fun accidental(flats: Boolean) = if (flats) Accidental.FLAT else Accidental.SHARP

    /**
     * Transpose one chord symbol by [semitones], preserving quality and slash bass.
     *
     * Returns the original text unchanged when the symbol does not parse, so an
     * oddity in a captured sheet transposes to itself rather than vanishing.
     */
    fun transposeSymbol(symbol: String, semitones: Int, flats: Boolean = false): String {
        val c = ChordLibrary.parseFull(symbol) ?: return symbol
        val shift = ((semitones % 12) + 12) % 12
        val root = NoteSpeller.spell(c.root + shift, accidental(flats))
        val bass = c.bass?.let { "/" + NoteSpeller.spell(it + shift, accidental(flats)) } ?: ""
        return root + c.quality.symbol + bass
    }

    /** Transpose a key the same way, so the header stays consistent with the chords. */
    fun transposeKey(key: SongKey, semitones: Int, flats: Boolean = false): String {
        val shift = ((semitones % 12) + 12) % 12
        return NoteSpeller.spell(key.tonic + shift, accidental(flats)) + if (key.minor) "m" else ""
    }

    // Semitone offset from the tonic → Roman numeral, spelled against the MAJOR
    // scale so chromatic chords read the way a musician writes them (bVII, #IV).
    private val MAJOR_ROMAN = listOf(
        "I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII")

    // In a minor key the b3/b6/b7 are diatonic, so they carry no flat sign; the
    // raised ones are the ones worth marking.
    private val MINOR_ROMAN = listOf(
        "I", "bII", "II", "III", "#III", "IV", "#IV", "V", "VI", "#VI", "VII", "#VII")

    private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII")

    /** Scale-degree number (1..7, with accidental) of a pitch class in a key — used
     *  to name the bass of an inversion. */
    fun bassDegree(key: SongKey, pc: PitchClass): String {
        val iv = pc - key.tonic
        val roman = (if (key.minor) MINOR_ROMAN else MAJOR_ROMAN)[iv.semitones]
        val acc = when {
            roman.startsWith("b") -> "b"
            roman.startsWith("#") -> "#"
            else -> ""
        }
        val bare = roman.trimStart('b', '#')
        return acc + (NUMERALS.indexOf(bare) + 1)
    }

    /**
     * The chord's function in the key: "IV", "V7", "vi", "bVII", "iiø7".
     *
     * Case carries the quality — upper for major/dominant, lower for minor and
     * diminished — which is the convention the ear-training side already uses. An
     * inversion is appended as "/<bass degree>", so "C/E" in C reads "I/3": the
     * tonic with its third in the bass. That is more legible at a glance than
     * figured bass, and it is the same information.
     */
    fun degreeLabel(symbol: String, key: SongKey): String {
        val c = ChordLibrary.parseFull(symbol) ?: return symbol
        val iv = c.root - key.tonic
        var roman = (if (key.minor) MINOR_ROMAN else MAJOR_ROMAN)[iv.semitones]
        val q = c.quality.symbol
        val minorish = Regex("^(m|min)(?!aj)").containsMatchIn(q) ||
            q == "dim" || q == "dim7" || q == "m7b5"
        if (minorish) {
            roman = roman.replace(Regex("[IV]+")) { it.value.lowercase() }
        }
        // The quality suffix, minus the minor marker already carried by the case.
        var suffix = q.replace(Regex("^(m|min)(?!aj)"), "")
        if (q == "dim" || q == "dim7") suffix = if (q == "dim7") "°7" else "°"
        if (q == "m7b5") suffix = "ø7"
        var label = roman + suffix
        val bass = c.bass
        if (bass != null && c.inversion > 0) {
            label += "/" + bassDegree(key, bass)
        }
        return label
    }

    /** Every chord in a section relabelled by function — the whole point of the
     *  degrees view, kept here so both platforms relabel identically. */
    fun degreeLabels(symbols: List<String>, key: SongKey): List<String> =
        symbols.map { degreeLabel(it, key) }
}

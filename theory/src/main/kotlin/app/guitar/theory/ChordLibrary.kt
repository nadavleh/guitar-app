package app.guitar.theory

object ChordLibrary {
    val qualities: Map<String, ChordQuality> = linkedMapOf(
        ""      to ChordQuality("",     listOf(Interval.P1, Interval.maj3, Interval.P5)),
        "maj"   to ChordQuality("maj",  listOf(Interval.P1, Interval.maj3, Interval.P5)),
        "m"     to ChordQuality("m",    listOf(Interval.P1, Interval.min3, Interval.P5)),
        "min"   to ChordQuality("min",  listOf(Interval.P1, Interval.min3, Interval.P5)),
        "dim"   to ChordQuality("dim",  listOf(Interval.P1, Interval.min3, Interval.TT)),
        "aug"   to ChordQuality("aug",  listOf(Interval.P1, Interval.maj3, Interval.min6)),
        "sus2"  to ChordQuality("sus2", listOf(Interval.P1, Interval.maj2, Interval.P5)),
        "sus4"  to ChordQuality("sus4", listOf(Interval.P1, Interval.P4, Interval.P5)),
        "7"     to ChordQuality("7",    listOf(Interval.P1, Interval.maj3, Interval.P5, Interval.min7)),
        "maj7"  to ChordQuality("maj7", listOf(Interval.P1, Interval.maj3, Interval.P5, Interval.maj7)),
        "m7"    to ChordQuality("m7",   listOf(Interval.P1, Interval.min3, Interval.P5, Interval.min7)),
        "min7"  to ChordQuality("min7", listOf(Interval.P1, Interval.min3, Interval.P5, Interval.min7)),
        "m7b5"  to ChordQuality("m7b5", listOf(Interval.P1, Interval.min3, Interval.TT, Interval.min7)),
        "dim7"  to ChordQuality("dim7", listOf(Interval.P1, Interval.min3, Interval.TT, Interval.maj6)),
        "6"     to ChordQuality("6",    listOf(Interval.P1, Interval.maj3, Interval.P5, Interval.maj6)),
        "m6"    to ChordQuality("m6",   listOf(Interval.P1, Interval.min3, Interval.P5, Interval.maj6)),
        "9"     to ChordQuality("9",    listOf(Interval.P1, Interval.maj3, Interval.P5, Interval.min7, Interval.maj9)),
        "add9"  to ChordQuality("add9", listOf(Interval.P1, Interval.maj3, Interval.P5, Interval.maj9)),
        "13"    to ChordQuality("13",   listOf(Interval.P1, Interval.maj3, Interval.P5, Interval.min7, Interval.maj9, Interval.maj13)),
        // Diatonic extensions used by ear training. The perfect 5th is omitted
        // (standard jazz practice) so each stays a tractable 4-note voicing.
        "maj9"    to ChordQuality("maj9",    listOf(Interval.P1, Interval.maj3, Interval.maj7, Interval.maj9)),
        "maj13"   to ChordQuality("maj13",   listOf(Interval.P1, Interval.maj3, Interval.maj7, Interval.maj13)),
        "maj7#11" to ChordQuality("maj7#11", listOf(Interval.P1, Interval.maj3, Interval.maj7, Interval.s11)),
        "m9"      to ChordQuality("m9",      listOf(Interval.P1, Interval.min3, Interval.min7, Interval.maj9)),
        "m11"     to ChordQuality("m11",     listOf(Interval.P1, Interval.min3, Interval.min7, Interval.P11)),
        // Dominant 11 omits the 3rd (it clashes with the 11) — the textbook voicing.
        "11"      to ChordQuality("11",      listOf(Interval.P1, Interval.P5, Interval.min7, Interval.P11)),
    )

    fun parse(symbol: String): Pair<PitchClass, ChordQuality>? {
        val trimmed = symbol.trim()
        if (trimmed.isEmpty()) return null
        for (rootLen in minOf(2, trimmed.length) downTo 1) {
            val rootText = trimmed.substring(0, rootLen)
            val rootPc = try { NoteSpeller.parsePitchClass(rootText) } catch (_: Exception) { null }
            if (rootPc != null) {
                val qualitySymbol = trimmed.substring(rootLen)
                val quality = qualities[qualitySymbol]
                if (quality != null) return rootPc to quality
            }
        }
        return null
    }
}

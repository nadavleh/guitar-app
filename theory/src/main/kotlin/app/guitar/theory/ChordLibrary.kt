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
        // Minor-major 7th — the "James Bond" chord; used by the minor line-cliché
        // progression (i – i(maj7) – i7 – i6).
        "mMaj7"   to ChordQuality("mMaj7",   listOf(Interval.P1, Interval.min3, Interval.P5, Interval.maj7)),
        // Augmented dominant 7th (7#5): a dominant chord with a raised 5th.
        "7#5"     to ChordQuality("7#5",     listOf(Interval.P1, Interval.maj3, Interval.min6, Interval.min7)),
        // Augmented major 7th (maj7#5): a maj7 with a raised 5th.
        "maj7#5"  to ChordQuality("maj7#5",  listOf(Interval.P1, Interval.maj3, Interval.min6, Interval.maj7)),
        // Power chord — root and 5th, deliberately NO third, so it is neither major
        // nor minor. Common in the transcriptions as "E5".
        "5"       to ChordQuality("5",       listOf(Interval.P1, Interval.P5)),
        // Suspended dominant: the 3rd is replaced by the 4th, the 7th stays.
        "7sus4"   to ChordQuality("7sus4",   listOf(Interval.P1, Interval.P4, Interval.P5, Interval.min7)),
        // Altered dominants that turn up in the jazz/bossa transcriptions.
        "7b5"     to ChordQuality("7b5",     listOf(Interval.P1, Interval.maj3, Interval.TT, Interval.min7)),
        "7b9"     to ChordQuality("7b9",     listOf(Interval.P1, Interval.maj3, Interval.P5, Interval.min7, Interval(13))),
        "6add9"   to ChordQuality("6add9",   listOf(Interval.P1, Interval.maj3, Interval.P5, Interval.maj6, Interval.maj9)),
        "m13"     to ChordQuality("m13",     listOf(Interval.P1, Interval.min3, Interval.min7, Interval.maj9, Interval.maj13)),
    )

    /**
     * Chord-sheet shorthand that means an existing quality. Transcription sites write
     * the same chord several ways: "A4" for Asus4, "D2" for Dsus2, "AM7" (capital M)
     * for Amaj7. These are notation variants, not new harmony, so they map onto the
     * canonical qualities rather than duplicating them.
     *
     * Case matters and is the reason this is an explicit table rather than a
     * lowercase compare: "m" is minor and "M" is major.
     */
    private val ALIASES: Map<String, String> = mapOf(
        "4" to "sus4",
        "2" to "sus2",
        "M7" to "maj7",
        "Maj7" to "maj7",
        "mmaj7" to "mMaj7",
        "mMAJ7" to "mMaj7",
        "minmaj7" to "mMaj7",
        "sus" to "sus4",
        "7sus" to "7sus4",
        "4add9" to "sus4",
        "M" to "",
        "+" to "aug",
        "aug7" to "7#5",
    )

    /**
     * A parsed chord symbol, including the slash bass when one was written.
     *
     * A slash chord is almost always an INVERSION — the same chord with a different
     * chord tone in the bass ("D/F#" is D major over its own 3rd). Occasionally the
     * bass is not a chord tone at all ("C/D", a pedal), which is why [bass] is kept
     * as a plain pitch class and the inversion index is derived, not assumed:
     * [inversion] returns null for the pedal case rather than inventing a number.
     */
    data class ParsedChord(
        val root: PitchClass,
        val quality: ChordQuality,
        /** The note written after the slash; null when the symbol had none. */
        val bass: PitchClass?,
    ) {
        /**
         * The quality once the bass note is accounted for.
         *
         * A slash chord is an inversion: the bass is a tone OF the chord, and the
         * symbol simply did not spell it. "Bb/Ab" is Bb7 with its b7 in the bass;
         * "C/D" is Cadd9 with its 9th in the bass. So whatever interval the bass
         * sits at, it is folded in as a chord tone — there is no separate category
         * of "bass note that isn't part of the chord".
         *
         * Where that produces a chord a musician already has a name for, the named
         * quality is used ([IMPLIED]); otherwise the tone is appended as "addN".
         */
        val effectiveQuality: ChordQuality
            get() {
                val b = bass ?: return quality
                if (quality.notesFrom(root).contains(b)) return quality
                val iv = b - root
                val named = IMPLIED[quality.symbol to iv]
                return qualities[named]
                    ?: ChordQuality(quality.symbol + "add" + degreeName(iv), quality.intervals + iv)
            }

        /** Chord-tone index of [bass]; 0 = root position. Always resolves, because
         *  [effectiveQuality] has already folded the bass in as a chord tone. */
        val inversion: Int
            get() {
                val b = bass ?: return 0
                return effectiveQuality.notesFrom(root).indexOf(b).coerceAtLeast(0)
            }

        /** True when the bass is some tone other than the root. */
        val isInversion: Boolean get() = inversion > 0

        /** True when the bass added a tone the written symbol did not spell. */
        val impliesTone: Boolean get() = effectiveQuality !== quality
    }

    /** Degree name for an interval above the root, as a musician would write it. */
    private fun degreeName(iv: Interval): String = when (iv.semitones) {
        1 -> "b9"; 2 -> "9"; 3 -> "#9"; 4 -> "3"; 5 -> "11"; 6 -> "#11"
        7 -> "5"; 8 -> "b13"; 9 -> "13"; 10 -> "7"; 11 -> "maj7"
        else -> "1"
    }

    /**
     * Canonical name for "<written quality> + this tone in the bass", so the implied
     * chord reports with the symbol a musician would write rather than a synthetic
     * one — "Bb/Ab" as Bb7, not as Bbadd7.
     */
    private val IMPLIED: Map<Pair<String, Interval>, String> = mapOf(
        ("" to Interval.min7) to "7",
        ("" to Interval.maj7) to "maj7",
        ("" to Interval.maj2) to "add9",
        ("" to Interval.maj6) to "6",
        ("maj" to Interval.min7) to "7",
        ("maj" to Interval.maj7) to "maj7",
        ("maj" to Interval.maj2) to "add9",
        ("maj" to Interval.maj6) to "6",
        ("m" to Interval.min7) to "m7",
        ("m" to Interval.maj7) to "mMaj7",
        ("m" to Interval.maj6) to "m6",
        ("min" to Interval.min7) to "min7",
        ("min" to Interval.maj7) to "mMaj7",
        ("min" to Interval.maj6) to "m6",
        ("sus4" to Interval.min7) to "7sus4",
        ("dim" to Interval.min7) to "m7b5",
        ("aug" to Interval.min7) to "7#5",
        ("aug" to Interval.maj7) to "maj7#5",
        ("5" to Interval.min7) to "7",
        ("5" to Interval.maj7) to "maj7",
        ("5" to Interval.maj3) to "",
        ("5" to Interval.min3) to "m",
        ("7" to Interval.maj9) to "9",
        ("m7" to Interval.maj9) to "m9",
        ("maj7" to Interval.maj9) to "maj9",
        ("6" to Interval.maj9) to "6add9",
    )

    /** Root + quality, ignoring any slash bass. Kept at this signature because the
     *  fretboard, looper and ear-training callers only ever want the chord itself. */
    fun parse(symbol: String): Pair<PitchClass, ChordQuality>? =
        parseFull(symbol)?.let { it.root to it.quality }

    /** Full parse, preserving the slash bass. */
    fun parseFull(symbol: String): ParsedChord? {
        val trimmed = symbol.trim()
        if (trimmed.isEmpty()) return null
        var core = trimmed
        var bass: PitchClass? = null
        val slash = trimmed.indexOf('/')
        if (slash > 0) {
            // A slash with an unreadable bass makes the whole symbol invalid rather
            // than silently degrading to the base chord — that would hide bad data.
            bass = try {
                NoteSpeller.parsePitchClass(trimmed.substring(slash + 1))
            } catch (_: Exception) { return null }
            core = trimmed.substring(0, slash).trim()
        }
        val base = parseCore(core) ?: return null
        return ParsedChord(base.first, base.second, bass)
    }

    private fun parseCore(text: String): Pair<PitchClass, ChordQuality>? {
        if (text.isEmpty()) return null
        for (rootLen in minOf(2, text.length) downTo 1) {
            val rootText = text.substring(0, rootLen)
            val rootPc = try { NoteSpeller.parsePitchClass(rootText) } catch (_: Exception) { null }
            if (rootPc != null) {
                val qualitySymbol = text.substring(rootLen)
                val quality = qualities[qualitySymbol]
                    ?: qualities[ALIASES[qualitySymbol]]
                if (quality != null) return rootPc to quality
            }
        }
        return null
    }
}

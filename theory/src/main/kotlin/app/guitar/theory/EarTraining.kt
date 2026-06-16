package app.guitar.theory

/**
 * Pure-Kotlin ear-training theory: Roman-numeral diatonic chord roles for major
 * and minor keys, a small library of common 4-bar progressions, and a resolver
 * that turns a (degree, key, mode, chord-type-level) into a concrete chord
 * symbol that [ChordLibrary] knows how to parse.
 *
 * Conventions:
 *  - For major keys: I, ii, iii, IV, V, vi, vii° (case = chord quality).
 *  - For minor keys: i, ii°, III, iv, V, VI, VII. We use the *harmonic-minor V*
 *    (a major dominant) by default because V7→i is the cadence beginners
 *    practice. Pure natural-minor v is rarely used in pop/rock progressions.
 */
enum class TrainingMode { Major, Minor }

enum class ChordTypeLevel(val displayName: String) {
    Triads("Triads"),
    Sevenths("7th chords"),
    Extended("Extended"),
}

/** Per-degree role: Roman label + the chord-quality symbol at each level. */
data class DegreeInfo(
    val roman: String,           // "I", "ii", "vii°", "V", etc.
    val triadQuality: String,    // [ChordQuality.symbol] for triad: "" / "m" / "dim"
    val seventhQuality: String,  // "maj7" / "m7" / "7" / "m7b5"
    val extendedQuality: String, // "maj9" / "m9" / "9" / "m7b5"
    /**
     * Allowed *diatonic* extensions for ear-training progression generation:
     * each pair is (chord-quality symbol parseable by [ChordLibrary], Roman-label
     * suffix). When non-empty, the Extended level picks one at random from this
     * set; when empty it falls back to [extendedQuality]. This restriction applies
     * ONLY to generated ear-training progressions — elsewhere all extensions are
     * permitted.
     */
    val extendedOptions: List<Pair<String, String>> = emptyList(),
)

/** A 4-bar progression by scale degree. */
data class Progression(
    val mode: TrainingMode,
    val degrees: List<Int>,   // length 4, each in 1..7
) {
    init {
        require(degrees.size == 4) { "progressions must be 4 bars, got ${degrees.size}" }
        require(degrees.all { it in 1..7 })
    }
}

/** A chord realised in a specific key — both the playable symbol and its Roman label. */
data class ResolvedChord(
    /** Chord symbol parseable by [ChordLibrary.parse], e.g. "Cmaj7", "Am", "Bm7b5". */
    val symbol: String,
    /** Roman-numeral display, e.g. "Imaj7", "ii7", "vii°7". */
    val romanLabel: String,
    /** The diatonic root pitch class. */
    val root: PitchClass,
)

object EarTraining {

    val MAJOR_DEGREES: Map<Int, DegreeInfo> = mapOf(
        // extendedOptions encode the diatonic extensions allowed per degree:
        //   I→9,13 · ii→9,11 · iii→11 · IV→9,#11,13 · V→9,11,13 · vi→9,11 · vii°→(11/b13, rarely written)
        1 to DegreeInfo("I",    "",    "maj7", "maj9", listOf("6" to "6", "add9" to "add9", "maj9" to "maj9", "maj13" to "maj13")),
        2 to DegreeInfo("ii",   "m",   "m7",   "m9",   listOf("m6" to "6", "m9" to "9", "m11" to "11")),
        3 to DegreeInfo("iii",  "m",   "m7",   "m7",   listOf("m11" to "11")),   // 11 is the only stable tension on iii
        4 to DegreeInfo("IV",   "",    "maj7", "maj9", listOf("6" to "6", "add9" to "add9", "maj9" to "maj9", "maj7#11" to "maj7#11", "maj13" to "maj13")),
        5 to DegreeInfo("V",    "",    "7",    "9",    listOf("6" to "6", "9" to "9", "11" to "11", "13" to "13")),
        6 to DegreeInfo("vi",   "m",   "m7",   "m9",   listOf("m9" to "9", "m11" to "11")),
        // Extended diminished extensions are rarely written; keep vii° at the ø7 sound.
        7 to DegreeInfo("vii°", "dim", "m7b5", "m7b5", listOf("m7b5" to "7")),
    )

    val MINOR_DEGREES: Map<Int, DegreeInfo> = mapOf(
        1 to DegreeInfo("i",    "m",   "m7",   "m9"),
        2 to DegreeInfo("ii°",  "dim", "m7b5", "m7b5"),
        3 to DegreeInfo("III",  "",    "maj7", "maj9"),
        4 to DegreeInfo("iv",   "m",   "m7",   "m9"),
        // Harmonic minor V — most common in cadences. Pure natural-minor v is m7.
        5 to DegreeInfo("V",    "",    "7",    "7"),
        6 to DegreeInfo("VI",   "",    "maj7", "maj9"),
        7 to DegreeInfo("VII",  "",    "7",    "7"),
    )

    private val MAJOR_SCALE_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val NATURAL_MINOR_SEMITONES = intArrayOf(0, 2, 3, 5, 7, 8, 10)

    /** Pitch class of the diatonic root for [degree] in [key] under [mode]. */
    fun degreeRoot(key: PitchClass, degree: Int, mode: TrainingMode): PitchClass {
        require(degree in 1..7) { "degree must be 1..7, got $degree" }
        val scale = if (mode == TrainingMode.Major) MAJOR_SCALE_SEMITONES else NATURAL_MINOR_SEMITONES
        return PitchClass.of(key.value + scale[degree - 1])
    }

    /** Build the displayed Roman label for a non-triad level: e.g. "ii"+"m7" → "ii7", "V"+"7" → "V7". */
    fun romanLabel(triadRoman: String, quality: String): String = when {
        // Diminished: "vii°" / "ii°" + m7b5 → "vii°7" / "ii°7"
        triadRoman.endsWith("°") -> if (quality == "m7b5") "${triadRoman}7" else triadRoman + quality
        // Lowercase (minor) Roman: the "m" prefix is redundant — strip it.
        // "ii" + "m7" → "ii7"; "vi" + "m9" → "vi9"
        triadRoman[0].isLowerCase() && quality.startsWith("m") && quality != "m7b5" ->
            triadRoman + quality.removePrefix("m")
        else -> triadRoman + quality
    }

    /** Resolve a Roman degree to a playable chord symbol + Roman label in the given key.
     *  At the Extended level, a degree with a non-empty [DegreeInfo.extendedOptions]
     *  picks one allowed diatonic extension at random using [rng]. */
    fun resolve(
        degree: Int,
        key: PitchClass,
        mode: TrainingMode,
        level: ChordTypeLevel,
        rng: kotlin.random.Random = kotlin.random.Random.Default,
    ): ResolvedChord {
        val info = (if (mode == TrainingMode.Major) MAJOR_DEGREES else MINOR_DEGREES)[degree]
            ?: error("invalid degree $degree")
        val root = degreeRoot(key, degree, mode)
        val rootName = NoteSpeller.spell(root)
        // Extended level with a diatonic allowed-extension set → choose one at random.
        if (level == ChordTypeLevel.Extended && info.extendedOptions.isNotEmpty()) {
            val (qual, romanSuffix) = info.extendedOptions[rng.nextInt(info.extendedOptions.size)]
            return ResolvedChord("$rootName$qual", info.roman + romanSuffix, root)
        }
        val quality = when (level) {
            ChordTypeLevel.Triads    -> info.triadQuality
            ChordTypeLevel.Sevenths  -> info.seventhQuality
            ChordTypeLevel.Extended  -> info.extendedQuality
        }
        val romanLabel = when (level) {
            ChordTypeLevel.Triads   -> info.roman
            ChordTypeLevel.Sevenths -> romanLabel(info.roman, info.seventhQuality)
            ChordTypeLevel.Extended -> romanLabel(info.roman, info.extendedQuality)
        }
        return ResolvedChord("$rootName$quality", romanLabel, root)
    }

    /** Resolve a full progression in the given key. Each bar's Extended extension is
     *  drawn independently from its degree's allowed diatonic set using [rng]. */
    fun resolveProgression(
        p: Progression,
        key: PitchClass,
        level: ChordTypeLevel,
        rng: kotlin.random.Random = kotlin.random.Random.Default,
    ): List<ResolvedChord> =
        p.degrees.map { resolve(it, key, p.mode, level, rng) }

    // ----- Common progressions ----------------------------------------------------------------

    val MAJOR_PROGRESSIONS: List<Progression> = listOf(
        Progression(TrainingMode.Major, listOf(1, 5, 6, 4)),   // I-V-vi-IV  ("pop")
        Progression(TrainingMode.Major, listOf(1, 4, 5, 1)),   // I-IV-V-I
        Progression(TrainingMode.Major, listOf(1, 6, 4, 5)),   // I-vi-IV-V  ("50s")
        Progression(TrainingMode.Major, listOf(6, 4, 1, 5)),   // vi-IV-I-V
        Progression(TrainingMode.Major, listOf(2, 5, 1, 1)),   // ii-V-I-I   (jazz)
        Progression(TrainingMode.Major, listOf(1, 6, 2, 5)),   // I-vi-ii-V  (jazz turnaround)
        Progression(TrainingMode.Major, listOf(1, 5, 1, 4)),   // I-V-I-IV
        Progression(TrainingMode.Major, listOf(1, 3, 4, 5)),   // I-iii-IV-V
        Progression(TrainingMode.Major, listOf(1, 5, 4, 1)),
    )

    val MINOR_PROGRESSIONS: List<Progression> = listOf(
        Progression(TrainingMode.Minor, listOf(1, 6, 3, 7)),   // i-VI-III-VII
        Progression(TrainingMode.Minor, listOf(1, 4, 5, 1)),   // i-iv-V-i
        Progression(TrainingMode.Minor, listOf(1, 6, 7, 1)),   // i-VI-VII-i
        Progression(TrainingMode.Minor, listOf(2, 5, 1, 1)),   // ii°-V-i-i
        Progression(TrainingMode.Minor, listOf(1, 7, 6, 5)),   // i-VII-VI-V
        Progression(TrainingMode.Minor, listOf(1, 4, 7, 3)),   // i-iv-VII-III
    )

    /** Pick a random progression for [mode], using [rng]. */
    fun randomProgression(mode: TrainingMode, rng: kotlin.random.Random): Progression {
        val pool = if (mode == TrainingMode.Major) MAJOR_PROGRESSIONS else MINOR_PROGRESSIONS
        return pool[rng.nextInt(pool.size)]
    }

    // ----- Advanced (non-diatonic) progressions ----------------------------------------------

    /**
     * One chord of an advanced progression, expressed RELATIVE to the key tonic so
     * it transposes to any key. Unlike [Progression] (diatonic degree 1..7), this
     * can name borrowed, secondary-dominant, and chromatic chords.
     *
     * @param semitone chord-root offset above the tonic, 0..11
     * @param quality  a [ChordLibrary] quality symbol ("", "m", "7", "maj7", "dim7", "m7b5", "6", "mMaj7", …)
     * @param roman    display label, e.g. "bVII", "III7", "V7", "i6", "#IV°7"
     */
    data class AdvChord(val semitone: Int, val quality: String, val roman: String)

    /** A named, possibly non-diatonic progression with a teaching note, for the
     *  "Advanced progressions" ear-training option. Variable length. */
    data class NamedProgression(
        val name: String,
        val explanation: String,
        /** Whether the key center is heard as major or minor (affects tonic spelling). */
        val tonicMode: TrainingMode,
        val chords: List<AdvChord>,
    ) {
        /** Roman-numeral line, e.g. "I – bVII – IV". */
        val romanLine: String get() = chords.joinToString("  –  ") { it.roman }

        /** Realise the progression in [key] as concrete, playable chords. */
        fun resolve(key: PitchClass): List<ResolvedChord> = chords.map { c ->
            val root = PitchClass.of(key.value + c.semitone)
            ResolvedChord(NoteSpeller.spell(root) + c.quality, c.roman, root)
        }
    }

    private fun adv(name: String, explanation: String, mode: TrainingMode, vararg chords: AdvChord) =
        NamedProgression(name, explanation, mode, chords.toList())

    /**
     * Curated non-diatonic / "special" progressions for advanced practice — borrowed
     * chords (modal interchange), secondary dominants, chromatic passing chords, and
     * jazz turnarounds. Each carries a short explanation shown while quizzing.
     */
    val ADVANCED_PROGRESSIONS: List<NamedProgression> = listOf(
        adv("Mixolydian Rocker", "Borrows bVII from the parallel Mixolydian mode for a driving, anthemic classic-rock sound.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(10, "", "bVII"), AdvChord(5, "", "IV")),
        adv("Bright Lift", "The major II is a borrowed/secondary-dominant chord (V of V) that gives a sudden, hopeful lift.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(2, "", "II"), AdvChord(5, "", "IV"), AdvChord(0, "", "I")),
        adv("Romantic Climax", "A bright major III then a borrowed minor iv — a dramatic rise melting into melancholy.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(4, "", "III"), AdvChord(5, "", "IV"), AdvChord(5, "m", "iv")),
        adv("Epic Backstep", "Borrowed bVII and bVI from the parallel minor give a cinematic, heroic backstep.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(10, "", "bVII"), AdvChord(8, "", "bVI"), AdvChord(10, "", "bVII")),
        adv("Andalusian Cadence", "The flamenco descending tetrachord; the major V (harmonic minor) adds dark, Spanish tension.",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(10, "", "bVII"), AdvChord(8, "", "bVI"), AdvChord(7, "", "V")),
        adv("Dark Roots", "Uses the natural-minor v (minor, not the usual major V) for a raw, modal folk/blues feel.",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(5, "m", "iv"), AdvChord(7, "m", "v")),
        adv("Neo-Soul Minor", "Moody natural-minor motion through a minor v, popular in modern R&B and lo-fi.",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(7, "m", "v"), AdvChord(8, "", "bVI"), AdvChord(10, "", "bVII")),
        adv("Ragtime Circle", "A chain of secondary dominants around the circle of fifths — the bouncing staple of ragtime and stride.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(9, "7", "VI7"), AdvChord(2, "7", "II7"), AdvChord(7, "7", "V7")),
        adv("Classic Ragtime Turnaround", "I becomes a dominant I7 to tonicise IV, then a borrowed minor iv adds a nostalgic, bluesy turn.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(0, "7", "I7"), AdvChord(5, "", "IV"), AdvChord(5, "m", "iv")),
        adv("Chromatic Passing Chord", "A #i diminished passing chord connects I to ii7 with a smooth chromatic walking bass.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(1, "dim7", "#I°7"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Traditional Rag Ending", "A syncopated Scott-Joplin ending: a secondary-dominant III7, a #IV°7 passing chord, then a I–V7–I cadence.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(4, "7", "III7"), AdvChord(5, "", "IV"), AdvChord(6, "dim7", "#IV°7"),
            AdvChord(0, "", "I/V"), AdvChord(7, "7", "V7"), AdvChord(0, "", "I")),
        adv("Melancholic Jazz-Rag", "A secondary-dominant III7 leads to a borrowed minor iv and a half-diminished ii — bittersweet and vintage.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(4, "7", "III7"), AdvChord(5, "m", "iv"), AdvChord(2, "m7b5", "ii7b5"), AdvChord(7, "7", "V7")),
        adv("Broadway Lift", "The secondary-dominant III7 brightens a major-key ii–V cadence — a classic show-tune lift.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(4, "7", "III7"), AdvChord(5, "", "IV"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Minor-Key Swing", "Starts dark, then a striking secondary-dominant III7 lifts before the ii–V cadence.",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(3, "7", "III7"), AdvChord(5, "m", "iv"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Extended Pop Ballad", "A secondary-dominant III7 tonicises vi, prolonging tension before the ii–V resolution.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(4, "7", "III7"), AdvChord(9, "m", "vi"), AdvChord(5, "", "IV"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Tritone Substitution", "The dominant V7 is replaced by bII7 a tritone away — a smooth chromatic slide into the tonic.",
            TrainingMode.Major,
            AdvChord(2, "m7", "ii7"), AdvChord(1, "7", "bII7"), AdvChord(0, "maj7", "Imaj7")),
        adv("Minor Line Cliché", "A stationary minor chord with one inner voice descending chromatically (root–7–b7–6).",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(0, "mMaj7", "i(maj7)"), AdvChord(0, "m7", "i7"), AdvChord(0, "m6", "i6")),
        adv("Romantic Plaintive", "A major line cliché: the top voice melts down (root–maj7–b7), pulling toward IV.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(0, "maj7", "Imaj7"), AdvChord(0, "7", "I7"), AdvChord(5, "", "IV")),
        adv("Church Cadence", "A gospel plagal feel with a bluesy bVII descent back to IV.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(5, "", "IV"), AdvChord(0, "", "I"), AdvChord(10, "", "bVII"), AdvChord(5, "", "IV")),
        adv("Gospel Walk-Up", "A bassline climbing the scale through a #IV°7 diminished chord — a driving gospel walk-up.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(0, "", "I/III"), AdvChord(5, "", "IV"), AdvChord(6, "dim7", "#IV°7"), AdvChord(7, "", "V")),
        adv("Mario Cadence", "Borrowed bVI and bVII resolve up to a triumphant major I — the classic heroic/video-game cadence.",
            TrainingMode.Major,
            AdvChord(8, "", "bVI"), AdvChord(10, "", "bVII"), AdvChord(0, "", "I")),
        adv("Royal Road", "The backbone of modern J-pop/anime: it loops without ever landing on the home chord.",
            TrainingMode.Major,
            AdvChord(5, "", "IV"), AdvChord(7, "", "V"), AdvChord(4, "m", "iii"), AdvChord(9, "m", "vi")),
        adv("Bird Blues Turnaround", "Charlie Parker's rapid descending turnaround, stacking a passing #IV°7 and a secondary-dominant VI7.",
            TrainingMode.Major,
            AdvChord(0, "maj7", "Imaj7"), AdvChord(6, "dim7", "#IV°7"), AdvChord(4, "m7", "iii7"),
            AdvChord(9, "7", "VI7"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Montgomery Turnaround", "A highly chromatic Wes-Montgomery turnaround that slides back to the tonic in tritone steps.",
            TrainingMode.Major,
            AdvChord(0, "maj7", "Imaj7"), AdvChord(3, "7", "bIII7"), AdvChord(8, "7", "bVI7"), AdvChord(1, "7", "bII7")),
    )

    /** Pick a random advanced progression. */
    fun randomAdvanced(rng: kotlin.random.Random): NamedProgression =
        ADVANCED_PROGRESSIONS[rng.nextInt(ADVANCED_PROGRESSIONS.size)]
}

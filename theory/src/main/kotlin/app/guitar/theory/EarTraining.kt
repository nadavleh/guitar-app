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

/** A 4-bar progression by scale degree.
 *
 *  [dominantBars] lists bar indices (0..3) that should sound as the HARMONIC-MINOR
 *  dominant — a major V / V7 instead of the natural-minor `v`. Only meaningful in a
 *  minor key and only on degree-5 bars (the raised leading tone that pulls to i).
 *  Empty for every natural-minor and major progression. */
data class Progression(
    val mode: TrainingMode,
    val degrees: List<Int>,   // length 4, each in 1..7
    val dominantBars: Set<Int> = emptySet(),
) {
    init {
        require(degrees.size == 4) { "progressions must be 4 bars, got ${degrees.size}" }
        require(degrees.all { it in 1..7 })
        require(dominantBars.all { it in degrees.indices }) { "dominantBars out of range" }
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
        // Roman numerals are named RELATIVE TO THE MAJOR SCALE: the lowered natural-minor
        // degrees carry a flat (bIII, bVI, bVII). Qualities are natural-minor diatonic.
        3 to DegreeInfo("bIII", "",    "maj7", "maj9"),
        4 to DegreeInfo("iv",   "m",   "m7",   "m9"),
        5 to DegreeInfo("v",    "m",   "m7",   "m9"),
        6 to DegreeInfo("bVI",  "",    "maj7", "maj9"),
        7 to DegreeInfo("bVII", "",    "7",    "7"),
    )

    /** The HARMONIC-MINOR dominant: degree 5 played as a MAJOR V (raised leading tone),
     *  the classic V→i cadence. Same root as the natural-minor `v` (the perfect fifth),
     *  but a major triad / dominant 7th. Used only for a progression's [Progression.dominantBars].
     *  No random extended set — its per-level suffixes ("", "7", "9") deliberately match the
     *  natural `v`'s, so the challenge scores a degree-5 answer identically for either. */
    val MINOR_DOMINANT = DegreeInfo("V", "", "7", "9")

    private val MAJOR_SCALE_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val NATURAL_MINOR_SEMITONES = intArrayOf(0, 2, 3, 5, 7, 8, 10)

    /** Pitch class of the diatonic root for [degree] in [key] under [mode]. */
    fun degreeRoot(key: PitchClass, degree: Int, mode: TrainingMode): PitchClass {
        require(degree in 1..7) { "degree must be 1..7, got $degree" }
        val scale = if (mode == TrainingMode.Major) MAJOR_SCALE_SEMITONES else NATURAL_MINOR_SEMITONES
        return PitchClass.of(key.value + scale[degree - 1])
    }

    /** MIDI note for a bare degree-reference tone, anchored to the tonic: degree 1
     *  always sounds at 52 + key (mid guitar register) and degrees 2..7 land in the
     *  octave ABOVE it, so 1..7 form one ascending scale in every key. (Mapping the
     *  pitch class straight to 52 + pc made the octave depend on where the key's
     *  degrees fell around the pc wrap point — e.g. G major dropped an octave at 4.) */
    fun degreeRefMidi(key: PitchClass, degree: Int, mode: TrainingMode): Int =
        52 + key.value + ((degreeRoot(key, degree, mode).value - key.value + 12) % 12)

    /** Build the displayed Roman label for a non-triad level: e.g. "ii"+"m7" → "ii7", "V"+"7" → "V7". */
    fun romanLabel(triadRoman: String, quality: String): String {
        // Ignore any leading accidental (b/#) when deciding major/minor case, so
        // "bIII"+"maj7" → "bIIImaj7" (major) and "v"+"m7" → "v7" (minor).
        val core = triadRoman.trimStart('b', '#')
        return when {
            // Diminished: "vii°" / "ii°" + m7b5 → "vii°7" / "ii°7"
            triadRoman.endsWith("°") -> if (quality == "m7b5") "${triadRoman}7" else triadRoman + quality
            // Lowercase (minor) Roman: the "m" prefix is redundant — strip it (but never
            // from "maj7"/"maj9"). "ii" + "m7" → "ii7"; "vi" + "m9" → "vi9".
            core.isNotEmpty() && core[0].isLowerCase() &&
                quality.startsWith("m") && !quality.startsWith("maj") && quality != "m7b5" ->
                triadRoman + quality.removePrefix("m")
            else -> triadRoman + quality
        }
    }

    /**
     * Map a scale degree in [mode] to its *relative-major* degree (1..7). A major
     * key and its relative minor share the same seven diatonic chords, just numbered
     * from a different tonic: the relative minor sits on the major key's 6th degree,
     * so minor 1↔major 6, minor 3↔major 1, minor 5↔major 3, etc. This is what makes
     * a major I–IV–V read as a minor III–VI–VII (the same three chords).
     */
    fun majorRelativeDegree(degree: Int, mode: TrainingMode): Int =
        if (mode == TrainingMode.Major) degree else ((degree + 4) % 7) + 1

    /** Inverse of [majorRelativeDegree]: a relative-major degree back into [mode]. */
    fun degreeFromMajorRelative(majorRelative: Int, mode: TrainingMode): Int =
        if (mode == TrainingMode.Major) majorRelative else ((majorRelative + 1) % 7) + 1

    /** Resolve a Roman degree to a playable chord symbol + Roman label in the given key.
     *  At the Extended level, a degree with a non-empty [DegreeInfo.extendedOptions]
     *  picks one allowed diatonic extension at random using [rng]. */
    fun resolve(
        degree: Int,
        key: PitchClass,
        mode: TrainingMode,
        level: ChordTypeLevel,
        rng: kotlin.random.Random = kotlin.random.Random.Default,
        asDominant: Boolean = false,
    ): ResolvedChord {
        // Harmonic-minor dominant: degree 5 sounded as a major V (see [MINOR_DOMINANT]).
        val info = if (asDominant && mode == TrainingMode.Minor) MINOR_DOMINANT
            else (if (mode == TrainingMode.Major) MAJOR_DEGREES else MINOR_DEGREES)[degree]
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
        p.degrees.mapIndexed { i, d -> resolve(d, key, p.mode, level, rng, asDominant = i in p.dominantBars) }

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
        Progression(TrainingMode.Major, listOf(1, 3, 6, 4)),   // I-iii-vi-IV (soft tonic family)
        Progression(TrainingMode.Major, listOf(6, 2, 5, 1)),   // vi-ii-V-I
        Progression(TrainingMode.Major, listOf(1, 2, 5, 1)),   // I-ii-V-I
        // Added from Nadav's "Top 96 progressions" list (all pure-diatonic 4-chord).
        Progression(TrainingMode.Major, listOf(1, 4, 2, 5)),   // I-IV-ii-V
        Progression(TrainingMode.Major, listOf(1, 4, 6, 5)),   // I-IV-vi-V
        Progression(TrainingMode.Major, listOf(1, 5, 4, 5)),   // I-V-IV-V
        Progression(TrainingMode.Major, listOf(6, 5, 4, 5)),   // vi-V-IV-V
        // Reclassified from Advanced — these are fully diatonic despite their names.
        Progression(TrainingMode.Major, listOf(1, 2, 5, 6)),   // I-ii-V-vi  ("Deceptive Cadence")
        Progression(TrainingMode.Major, listOf(4, 5, 3, 6)),   // IV-V-iii-vi ("Royal Road" J-pop)
    )

    val MINOR_PROGRESSIONS: List<Progression> = listOf(
        Progression(TrainingMode.Minor, listOf(1, 6, 3, 7)),   // i-VI-III-VII
        Progression(TrainingMode.Minor, listOf(1, 4, 5, 1)),   // i-iv-V-i
        Progression(TrainingMode.Minor, listOf(1, 6, 7, 1)),   // i-VI-VII-i
        Progression(TrainingMode.Minor, listOf(2, 5, 1, 1)),   // ii°-V-i-i
        Progression(TrainingMode.Minor, listOf(1, 7, 6, 5)),   // i-VII-VI-V
        Progression(TrainingMode.Minor, listOf(1, 4, 7, 3)),   // i-iv-VII-III
        // Added from Nadav's "Top 96 progressions" list (pure natural-minor 4-chord).
        Progression(TrainingMode.Minor, listOf(1, 5, 6, 7)),   // i-v-bVI-bVII
        Progression(TrainingMode.Minor, listOf(1, 3, 7, 4)),   // i-bIII-bVII-iv
    )

    /** Harmonic-minor progressions: the classic minor-key cadences that use a MAJOR V /
     *  V7 (raised leading tone) resolving to the minor tonic — the strong V→i pull that
     *  natural-minor `v` lacks. Each marks its degree-5 bar(s) as [Progression.dominantBars].
     *  Included in the minor generator pool + library only when the harmonic-minor toggle
     *  is on (default on). Kept separate from [MINOR_PROGRESSIONS] so the natural-minor
     *  set (e.g. i-v-bVI-bVII) still sounds its minor v. */
    val MINOR_HARMONIC_PROGRESSIONS: List<Progression> = listOf(
        Progression(TrainingMode.Minor, listOf(1, 4, 5, 1), dominantBars = setOf(2)),  // i-iv-V-i
        Progression(TrainingMode.Minor, listOf(1, 2, 5, 1), dominantBars = setOf(2)),  // i-ii°-V-i
        Progression(TrainingMode.Minor, listOf(2, 5, 1, 1), dominantBars = setOf(1)),  // ii°-V-i-i
        Progression(TrainingMode.Minor, listOf(1, 6, 2, 5), dominantBars = setOf(3)),  // i-bVI-ii°-V
        Progression(TrainingMode.Minor, listOf(1, 6, 4, 5), dominantBars = setOf(3)),  // i-bVI-iv-V
        Progression(TrainingMode.Minor, listOf(1, 4, 1, 5), dominantBars = setOf(3)),  // i-iv-i-V (half cadence)
        // Verified from Nadav's list (fact-checked — the mislabeled bVII "axis" ones excluded).
        Progression(TrainingMode.Minor, listOf(1, 6, 3, 5), dominantBars = setOf(3)),  // i-bVI-bIII-V
        Progression(TrainingMode.Minor, listOf(1, 3, 6, 5), dominantBars = setOf(3)),  // i-bIII-bVI-V
        Progression(TrainingMode.Minor, listOf(1, 4, 6, 5), dominantBars = setOf(3)),  // i-iv-bVI-V
    )

    /** Focused drill for hearing the I→iii move (the "soft" mediant, which shares
     *  two notes with the tonic and is easy to miss). Every entry opens with I–iii so
     *  the ear gets repeated exposure to that transition. Major-only (iii is the minor
     *  mediant of a major key). NOT part of [MAJOR_PROGRESSIONS] — it's a practice
     *  drill, not a library entry, so it needs no song examples. */
    val III_FOCUS_PROGRESSIONS: List<Progression> = listOf(
        Progression(TrainingMode.Major, listOf(1, 3, 4, 5)),   // I–iii–IV–V
        Progression(TrainingMode.Major, listOf(1, 3, 6, 4)),   // I–iii–vi–IV
        Progression(TrainingMode.Major, listOf(1, 3, 4, 1)),   // I–iii–IV–I
        Progression(TrainingMode.Major, listOf(1, 3, 2, 5)),   // I–iii–ii–V
        Progression(TrainingMode.Major, listOf(1, 3, 6, 5)),   // I–iii–vi–V
        Progression(TrainingMode.Major, listOf(1, 3, 1, 4)),   // I–iii–I–IV (back-and-forth)
    )

    /** Pick a random progression for [mode], using [rng]. When [focusIiii] is set,
     *  draw from the [III_FOCUS_PROGRESSIONS] drill (always major) instead. */
    fun randomProgression(
        mode: TrainingMode,
        rng: kotlin.random.Random,
        focusIiii: Boolean = false,
        includeHarmonicMinor: Boolean = true,
    ): Progression {
        if (focusIiii) return III_FOCUS_PROGRESSIONS[rng.nextInt(III_FOCUS_PROGRESSIONS.size)]
        val pool = when {
            mode == TrainingMode.Major -> MAJOR_PROGRESSIONS
            includeHarmonicMinor -> MINOR_PROGRESSIONS + MINOR_HARMONIC_PROGRESSIONS
            else -> MINOR_PROGRESSIONS
        }
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

        /** Realise the progression in [key] as concrete, playable chords. Spell the root
         *  to match the roman's accidental (a "b" roman like bVII → flat root Bb). */
        fun resolve(key: PitchClass): List<ResolvedChord> = chords.map { c ->
            val root = PitchClass.of(key.value + c.semitone)
            val prefer = if (c.roman.contains('#')) Accidental.SHARP
                         else if (c.roman.contains('b')) Accidental.FLAT else Accidental.SHARP
            ResolvedChord(NoteSpeller.spell(root, prefer) + c.quality, c.roman, root)
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
        adv("Bird Blues Turnaround", "Charlie Parker's rapid descending turnaround, stacking a passing #IV°7 and a secondary-dominant VI7.",
            TrainingMode.Major,
            AdvChord(0, "maj7", "Imaj7"), AdvChord(6, "dim7", "#IV°7"), AdvChord(4, "m7", "iii7"),
            AdvChord(9, "7", "VI7"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Montgomery Turnaround", "A highly chromatic Wes-Montgomery turnaround that slides back to the tonic in tritone steps.",
            TrainingMode.Major,
            AdvChord(0, "maj7", "Imaj7"), AdvChord(3, "7", "bIII7"), AdvChord(8, "7", "bVI7"), AdvChord(1, "7", "bII7")),
        adv("Applied V of V", "The major II is a secondary dominant (V of V): a dominant pointing at the dominant, not directly home.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(2, "7", "II7"), AdvChord(7, "", "V"), AdvChord(0, "", "I")),
        adv("Tonicized Relative", "III7 is a secondary dominant (V of vi) that pulls hard into the relative minor before returning home.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(4, "7", "III7"), AdvChord(9, "m", "vi"), AdvChord(0, "", "I")),
        adv("Applied V of ii", "VI7 is a secondary dominant (V of ii) tonicising the supertonic — a staple of jazz, standards, and Brazilian harmony.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(9, "7", "VI7"), AdvChord(2, "m", "ii"), AdvChord(7, "", "V"), AdvChord(0, "", "I")),
        adv("Long Applied Turnaround", "A chain of applied dominants (V/vi → vi → V/V → V → I) driving a long, propulsive turnaround.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(4, "7", "III7"), AdvChord(9, "m", "vi"), AdvChord(2, "7", "II7"), AdvChord(7, "", "V"), AdvChord(0, "", "I")),
        adv("Borrowed iv", "The borrowed minor iv from the parallel minor gives a bittersweet plagal turn back to I.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(5, "", "IV"), AdvChord(5, "m", "iv"), AdvChord(0, "", "I")),
        adv("Mixolydian Vamp", "A borrowed bVII lends a Mixolydian, rock-modal color between the V and IV.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(7, "", "V"), AdvChord(10, "", "bVII"), AdvChord(5, "", "IV")),
        adv("bVI-bVII Climb", "Borrowed bVI and bVII climb chromatically back up to a triumphant I — a dramatic modal resolution.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(8, "", "bVI"), AdvChord(10, "", "bVII"), AdvChord(0, "", "I")),
        adv("Flat-Six Color", "A borrowed bVI drops in unexpected color before the familiar IV–V.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(8, "", "bVI"), AdvChord(5, "", "IV"), AdvChord(7, "", "V")),
        adv("Flat-Three Borrowed", "The borrowed bIII from the parallel minor adds a bluesy, unexpected lift on the way to IV.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(3, "", "bIII"), AdvChord(5, "", "IV"), AdvChord(0, "", "I")),
        adv("Chromatic Descent", "iii → bIII → ii walks the bass down chromatically into a ii–V — a smooth descending passing motion.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(4, "m", "iii"), AdvChord(3, "", "bIII"), AdvChord(2, "m", "ii"), AdvChord(7, "", "V")),
        adv("Diminished to ii", "A #I° diminished passing chord connects I to ii with a chromatic walk-up — hear it as approach, not a \"weird\" chord.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(1, "dim", "#I°"), AdvChord(2, "m", "ii"), AdvChord(7, "", "V")),
        adv("Diminished to iii", "A #ii° diminished chord slides ii up into iii, then a secondary-dominant VI7 pushes onward.",
            TrainingMode.Major,
            AdvChord(2, "m", "ii"), AdvChord(3, "dim", "#ii°"), AdvChord(4, "m", "iii"), AdvChord(9, "7", "VI7")),
        adv("Minor #iv° to V", "In minor, a #iv° diminished chord approaches the (major) V for a dark, dramatic dominant setup.",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(6, "dim", "#iv°"), AdvChord(7, "", "V"), AdvChord(0, "m", "i")),
        adv("Minor Plagal Diminished", "iv slides up through a #iv° diminished passing chord back to the tonic — a brooding minor plagal move.",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(5, "m", "iv"), AdvChord(6, "dim", "#iv°"), AdvChord(0, "m", "i")),
        adv("iii-VI-ii-V Turnaround", "The descending jazz turnaround: iii7 and a secondary-dominant VI7 feed the ii–V, looping back to I.",
            TrainingMode.Major,
            AdvChord(4, "m7", "iii7"), AdvChord(9, "7", "VI7"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Rhythm-Changes Turnaround", "The \"rhythm changes\" turnaround — I–VI7–ii–V — the engine of bebop and countless standards.",
            TrainingMode.Major,
            AdvChord(0, "maj7", "Imaj7"), AdvChord(9, "7", "VI7"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Bossa Minor Diminished", "A bossa/jazz minor move: iv through a #iv° passing diminished into a dominant V7.",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(5, "m", "iv"), AdvChord(6, "dim", "#iv°"), AdvChord(7, "7", "V7")),
        adv("Ragtime Return", "I becomes a dominant I7 to tonicise IV, a borrowed minor iv adds nostalgia, then home — a ragtime staple.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(0, "7", "I7"), AdvChord(5, "", "IV"), AdvChord(5, "m", "iv"), AdvChord(0, "", "I")),
        adv("Bossa Chromatic", "A bossa-nova chromatic: a #I° diminished links Imaj7 to the ii7–V7, gliding on a chromatic bass.",
            TrainingMode.Major,
            AdvChord(0, "maj7", "Imaj7"), AdvChord(1, "dim", "#I°"), AdvChord(2, "m7", "ii7"), AdvChord(7, "7", "V7")),
        adv("Extended vi Turnaround", "The doo-wop I–vi–IV move, warmed by a borrowed minor iv before resolving home.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(9, "m", "vi"), AdvChord(5, "", "IV"), AdvChord(5, "m", "iv"), AdvChord(0, "", "I")),
        adv("Full Turnaround", "The complete I–vi–ii–V–I turnaround — the most common way to loop a tune back to its beginning.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(9, "m", "vi"), AdvChord(2, "m", "ii"), AdvChord(7, "", "V"), AdvChord(0, "", "I")),
        // Folded in from Nadav's "Top 96" list (non-diatonic triad progressions).
        adv("Pachelbel's Canon", "The endlessly-looping canon progression — I–V–vi–iii–IV–I–IV–V.",
            TrainingMode.Major,
            AdvChord(0, "", "I"), AdvChord(7, "", "V"), AdvChord(9, "m", "vi"), AdvChord(4, "m", "iii"),
            AdvChord(5, "", "IV"), AdvChord(0, "", "I"), AdvChord(5, "", "IV"), AdvChord(7, "", "V")),
        adv("Minor ii–V–i", "The minor-key ii–V–i: a half-diminished iiø into a dominant V7 resolving home.",
            TrainingMode.Minor,
            AdvChord(2, "m7b5", "iiø"), AdvChord(7, "7", "V7"), AdvChord(0, "m", "i")),
        adv("Neapolitan Cadence", "The bII (Neapolitan) — a dark half-step-above-tonic major chord — colours a minor iv–bII–bIII move.",
            TrainingMode.Minor,
            AdvChord(0, "m", "i"), AdvChord(5, "m", "iv"), AdvChord(1, "", "bII"), AdvChord(3, "", "bIII")),
    )

    /** Pick a random advanced progression. */
    fun randomAdvanced(rng: kotlin.random.Random): NamedProgression =
        ADVANCED_PROGRESSIONS[rng.nextInt(ADVANCED_PROGRESSIONS.size)]

    /** SUS category — progressions built on suspended (sus2/sus4) chords. Curated from
     *  Nadav's "Top 96" list; inversions are treated as the base chord. */
    val SUS_PROGRESSIONS: List<NamedProgression> = listOf(
        adv("Sus Resolution", "A suspended I that relaxes back to the plain I — the 4th falls to the 3rd.",
            TrainingMode.Major, AdvChord(0, "", "I"), AdvChord(0, "sus4", "Isus4"), AdvChord(0, "", "I")),
        adv("Suspended Lift", "A sus4 on the V adds tension before landing on vi.",
            TrainingMode.Major, AdvChord(0, "", "I"), AdvChord(7, "sus4", "Vsus4"), AdvChord(9, "m", "vi")),
        adv("Sus Bookends", "Sus2 colour on the tonic and a sus4 subdominant, framed by the plain I.",
            TrainingMode.Major, AdvChord(0, "", "I"), AdvChord(0, "sus2", "Isus2"), AdvChord(5, "sus4", "IVsus4"), AdvChord(0, "", "I")),
        adv("Dorian Sus Vamp", "A minor-key sus vamp with Dorian's bright major IV.",
            TrainingMode.Minor, AdvChord(0, "m", "i"), AdvChord(0, "sus4", "isus4"), AdvChord(7, "m", "v"), AdvChord(5, "", "IV")),
        adv("Mixolydian Sus", "A sus4 subdominant over a Mixolydian I–V feel.",
            TrainingMode.Major, AdvChord(5, "sus4", "IVsus4"), AdvChord(5, "", "IV"), AdvChord(0, "", "I"), AdvChord(7, "", "V")),
    )

    /** ADVANCED II category — richer colours: major-7th, minor-9th and MODAL progressions
     *  (Dorian / Mixolydian / Lydian / Phrygian). Curated from Nadav's "Top 96" list. */
    val ADVANCED2_PROGRESSIONS: List<NamedProgression> = listOf(
        adv("Maj7 Pop", "A dreamy maj7 on the tonic softens a I–IV–V.",
            TrainingMode.Major, AdvChord(0, "", "I"), AdvChord(0, "maj7", "Imaj7"), AdvChord(5, "", "IV"), AdvChord(7, "", "V")),
        adv("Maj7 Climb", "A lush IVmaj7 rising through V to vi.",
            TrainingMode.Major, AdvChord(0, "", "I"), AdvChord(5, "maj7", "IVmaj7"), AdvChord(7, "", "V"), AdvChord(9, "m", "vi")),
        adv("Backdoor Maj7", "IVmaj7 and a borrowed bVIImaj7 resolve to Imaj7 — the soul/backdoor sound.",
            TrainingMode.Major, AdvChord(5, "maj7", "IVmaj7"), AdvChord(10, "maj7", "bVIImaj7"), AdvChord(0, "maj7", "Imaj7")),
        adv("Minor-9 Vamp", "A wistful iim9 rocking against the tonic.",
            TrainingMode.Major, AdvChord(2, "m9", "iim9"), AdvChord(0, "", "I"), AdvChord(2, "m9", "iim9"), AdvChord(0, "", "I")),
        adv("Add9 Roots", "Open add9 shapes with a borrowed bVIImaj7 — the Bruce-Hornsby colour.",
            TrainingMode.Major, AdvChord(0, "add9", "Iadd9"), AdvChord(10, "maj7", "bVIImaj7"), AdvChord(5, "add9", "IVadd9")),
        adv("Dorian Vamp", "Minor tonic with Dorian's bright major IV (and bVII, bIII).",
            TrainingMode.Minor, AdvChord(0, "m", "i"), AdvChord(10, "", "bVII"), AdvChord(3, "", "bIII"), AdvChord(5, "", "IV")),
        adv("Mixolydian Two", "Major with a bVII and a Mixolydian II — bright and modal.",
            TrainingMode.Major, AdvChord(0, "", "I"), AdvChord(10, "", "bVII"), AdvChord(2, "", "II"), AdvChord(0, "", "I")),
        adv("Lydian Bright", "The floating Lydian sound: I rocking to a major II (from the raised 4th).",
            TrainingMode.Major, AdvChord(0, "", "I"), AdvChord(2, "", "II"), AdvChord(0, "", "I"), AdvChord(2, "", "II")),
        adv("Phrygian Dark", "Minor tonic sliding to a bII — the Spanish/metal Phrygian colour.",
            TrainingMode.Minor, AdvChord(0, "m", "i"), AdvChord(1, "", "bII")),
    )

    fun randomSus(rng: kotlin.random.Random): NamedProgression =
        SUS_PROGRESSIONS[rng.nextInt(SUS_PROGRESSIONS.size)]

    fun randomAdvanced2(rng: kotlin.random.Random): NamedProgression =
        ADVANCED2_PROGRESSIONS[rng.nextInt(ADVANCED2_PROGRESSIONS.size)]

    /** The seven diatonic chords of a major key arranged by DESCENDING fifths
     *  (each root a fifth below the previous / a fourth above): I–IV–vii°–iii–vi–ii–V,
     *  then back to I. This is the "circle of fifths" cycle. */
    val CIRCLE_OF_FIFTHS: List<AdvChord> = listOf(
        AdvChord(0,  "",    "I"),
        AdvChord(5,  "",    "IV"),
        AdvChord(11, "dim", "vii°"),
        AdvChord(4,  "m",   "iii"),
        AdvChord(9,  "m",   "vi"),
        AdvChord(2,  "m",   "ii"),
        AdvChord(7,  "",    "V"),
    )

    /**
     * Four adjacent chords of the diatonic [CIRCLE_OF_FIFTHS], starting at a random
     * point and moving along the cycle (roots falling by a fifth). Because each root
     * is a fifth above the next, sounding any non-final, non-diminished chord as a
     * dominant 7th turns it into a SECONDARY DOMINANT (V7) of the chord that follows.
     * This trains the ear on applied/secondary dominants "through the circle": each
     * eligible chord is domified with high probability, and at least one always is,
     * so every draw features a secondary dominant (often a whole applied-dominant
     * chain). Realised as a [NamedProgression] so it reuses the advanced play/reveal
     * flow.
     */
    fun randomCircleOfFifths(rng: kotlin.random.Random): NamedProgression {
        val n = CIRCLE_OF_FIFTHS.size
        val start = rng.nextInt(n)
        val window = (0 until 4).map { CIRCLE_OF_FIFTHS[(start + it) % n] }.toMutableList()
        // A chord is eligible to become a secondary dominant if it isn't the last one
        // (needs a target to resolve to) and isn't diminished (vii° can't be a V7).
        val eligible = (0 until 3).filter { window[it].quality != "dim" }
        var domCount = 0
        for (i in eligible) {
            if (rng.nextInt(100) < 75) {
                val c = window[i]
                window[i] = AdvChord(c.semitone, "7", c.roman.uppercase() + "7")
                domCount++
            }
        }
        // Guarantee at least one secondary dominant per draw so the drill always
        // delivers what it promises.
        if (domCount == 0 && eligible.isNotEmpty()) {
            val i = eligible[rng.nextInt(eligible.size)]
            val c = window[i]
            window[i] = AdvChord(c.semitone, "7", c.roman.uppercase() + "7")
            domCount = 1
        }
        val note = "Four chords along the circle of fifths (roots falling by a fifth). " +
            (if (domCount > 1) "Several chords are secondary dominants (V7 of the next), forming an applied-dominant chain that pulls hard toward the tonic."
             else "One chord is a secondary dominant (V7 of the next), intensifying the pull toward the tonic.")
        return NamedProgression("Circle of 5ths", note, TrainingMode.Major, window)
    }

    /** One draw-able 4-chord window of the diatonic circle of fifths, for the
     *  progression-library viewer. [id] ("W1".."W7") keys its song list. Carries
     *  its [chords] so the library's preview player can sound and voice it. */
    data class CircleWindow(val id: String, val romanLine: String, val chords: List<AdvChord>) {
        /** Realise the window in [key] as concrete, playable chords; spell the root to
         *  match the roman's accidental (bVII → Bb, #IV → F#). */
        fun resolve(key: PitchClass): List<ResolvedChord> = chords.map { c ->
            val root = PitchClass.of(key.value + c.semitone)
            val prefer = if (c.roman.contains('#')) Accidental.SHARP
                         else if (c.roman.contains('b')) Accidental.FLAT else Accidental.SHARP
            ResolvedChord(NoteSpeller.spell(root, prefer) + c.quality, c.roman, root)
        }
    }

    /** The seven 4-chord windows the [randomCircleOfFifths] trainer can draw, in
     *  cycle order starting from each diatonic chord. Used by the library viewer. */
    val CIRCLE_WINDOWS: List<CircleWindow> = run {
        val n = CIRCLE_OF_FIFTHS.size
        (0 until n).map { start ->
            val w = (0 until 4).map { CIRCLE_OF_FIFTHS[(start + it) % n] }
            CircleWindow("W${start + 1}", w.joinToString("  –  ") { it.roman }, w)
        }
    }

    /** Roman-numeral line for a diatonic [Progression], e.g. "I – V – vi – IV". Used
     *  by the progression-library viewer. */
    fun romanLineFor(prog: Progression): String {
        val map = if (prog.mode == TrainingMode.Major) MAJOR_DEGREES else MINOR_DEGREES
        return prog.degrees.mapIndexed { i, d ->
            if (i in prog.dominantBars && prog.mode == TrainingMode.Minor) MINOR_DOMINANT.roman
            else map[d]?.roman ?: d.toString()
        }.joinToString("  –  ")
    }

    /** Canonical id for a diatonic progression: "maj:1,5,6,4" or "min:1,4,5,1@2"
     *  (mode prefix + degrees, optional @-joined dominantBars to distinguish
     *  natural-minor from harmonic-minor variants that share degrees). Used to track
     *  which progressions the user misses and to reconstruct them in the drill tab. */
    fun progressionKey(prog: Progression): String {
        val prefix = if (prog.mode == TrainingMode.Major) "maj" else "min"
        val dom = prog.dominantBars.sorted()
        return "$prefix:${prog.degrees.joinToString(",")}" + if (dom.isNotEmpty()) "@${dom.joinToString(",")}" else ""
    }

    /** Inverse of [progressionKey]; null if [key] is not a valid diatonic key. */
    fun progressionFromKey(key: String): Progression? {
        val m = Regex("^(maj|min):(\\d+(?:,\\d+)*)(?:@(\\d+(?:,\\d+)*))?$").matchEntire(key) ?: return null
        val mode = if (m.groupValues[1] == "maj") TrainingMode.Major else TrainingMode.Minor
        val degrees = m.groupValues[2].split(",").mapNotNull { it.toIntOrNull() }
        if (degrees.size != 4 || degrees.any { it < 1 || it > 7 }) return null
        val dom = m.groupValues[3].takeIf { it.isNotEmpty() }?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        return runCatching { Progression(mode, degrees, dom) }.getOrNull()
    }

    /** A progression with no tonic (no I/i chord = scale-degree 1) has nothing
     *  anchoring the key, so it's harder to place by ear — the UI marks these as
     *  "difficult" in the library, the drill list and on reveal. */
    fun progressionLacksTonic(prog: Progression): Boolean = 1 !in prog.degrees
}

/** Direction an interval is played in the interval-ID trainer. */
enum class IntervalDirection { Ascending, Descending, Mixed }

/** One choosable interval: [semitones] above the tonic, with a short and long name. */
data class IntervalChoice(val semitones: Int, val shortName: String, val longName: String)

/**
 * Pure theory for the interval-identification ear trainer (#6). The 13 intervals
 * from unison to the octave, plus the arithmetic to turn a (tonic midi, interval,
 * direction) into the played target note. Direction-aware so a descending m3 plays
 * the tonic, then a note 3 semitones BELOW it.
 */
object IntervalTrainer {
    val INTERVALS: List<IntervalChoice> = listOf(
        IntervalChoice(0, "P1", "unison"),
        IntervalChoice(1, "m2", "minor 2nd"),
        IntervalChoice(2, "M2", "major 2nd"),
        IntervalChoice(3, "m3", "minor 3rd"),
        IntervalChoice(4, "M3", "major 3rd"),
        IntervalChoice(5, "P4", "perfect 4th"),
        IntervalChoice(6, "TT", "tritone"),
        IntervalChoice(7, "P5", "perfect 5th"),
        IntervalChoice(8, "m6", "minor 6th"),
        IntervalChoice(9, "M6", "major 6th"),
        IntervalChoice(10, "m7", "minor 7th"),
        IntervalChoice(11, "M7", "major 7th"),
        IntervalChoice(12, "P8", "octave"),
    )

    /** The MIDI note [semitones] from [tonicMidi] in [direction]. Mixed must be
     *  resolved to Ascending/Descending by the caller before calling this. */
    fun targetMidi(tonicMidi: Int, semitones: Int, ascending: Boolean): Int =
        if (ascending) tonicMidi + semitones else tonicMidi - semitones

    fun choiceFor(semitones: Int): IntervalChoice =
        INTERVALS.first { it.semitones == semitones }
}

package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.audio.Timbre
import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordShapeGenerator
import app.guitar.theory.ChordTypeLevel
import app.guitar.theory.EarTraining
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.Progression
import app.guitar.theory.ResolvedChord
import app.guitar.theory.TrainingMode
import app.guitar.theory.Tuning
import app.guitar.theory.VoicingStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * State + scheduler for the Ear-Training screen.
 *
 * Holds two independent sub-states:
 *   - Progression trainer: a 4-bar Roman-numeral progression that loops at BPM
 *     until the user requests "next". Per-slot reveal flags control whether
 *     each chord's label is shown to the user.
 *   - Note2Chord trainer: a single (triad, test-note) challenge. The chord
 *     plays as a block, then ~700 ms later the test note plays on top. The
 *     user reveals the answer label after attempting to identify it.
 */
@Stable
class EarTrainingState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
    /** Returns the current tuning — used to find a guitar-friendly voicing. */
    private val tuningProvider: () -> Tuning,
    /** Returns the current ring-sustain ms — used for the test-note duration. */
    private val sustainProvider: () -> Int,
    /** Returns the current strum spread in ms (0 = struck at once). */
    private val strumProvider: () -> Int = { 30 },
) {

    // ---- Voicing / variety options (apply to progression playback & generation) ----
    /** Use shell (jazz drop-2) voicings for ear-training chords. */
    var earShellVoicing by mutableStateOf(false)
    /** Mix everything: randomize chord-type level (triad/7th/extended) per bar AND
     *  randomize voicing (standard/shell) per chord. Overrides the single selections. */
    var earMixAll by mutableStateOf(false)

    /** Voicing style for the next chord, honoring shell / mix settings. */
    private fun earStyle(): VoicingStyle = when {
        earMixAll -> if (rng.nextBoolean()) VoicingStyle.Shell else VoicingStyle.Standard
        earShellVoicing -> VoicingStyle.Shell
        else -> VoicingStyle.Standard
    }
    // ---------- Progression trainer ----------

    var progSubMode by mutableStateOf(EarSubMode.Progression)

    /** Practice (free play) vs Challenge (scored rounds) — applies to the active tab. */
    var earMode by mutableStateOf(EarMode.Practice)

    /** Switch tabs: reset to Practice and stop any audio so modes don't bleed together. */
    fun switchTab(sub: EarSubMode) {
        progSubMode = sub
        earMode = EarMode.Practice
        stopLoop()
    }

    /** Whether the user wants Major mode in the rotation. */
    var includeMajor by mutableStateOf(true)
    /** Whether the user wants Minor mode in the rotation. */
    var includeMinor by mutableStateOf(true)
    /** Triads vs Sevenths vs Extended. */
    var chordTypeLevel by mutableStateOf(ChordTypeLevel.Sevenths)
    /** Null = random key each round. Non-null = always use this key. */
    var fixedKey by mutableStateOf<PitchClass?>(null)
    /** BPM for progression loop. */
    var progBpm by mutableStateOf(120)

    /** Current progression state. */
    var progKey by mutableStateOf(PitchClass.C)
    var progMode by mutableStateOf(TrainingMode.Major)
    var progProgression by mutableStateOf<Progression?>(null)
    var progResolved by mutableStateOf<List<ResolvedChord>>(emptyList())
    var progBarRevealed by mutableStateOf<Set<Int>>(emptySet())  // indices 0..3
    var keyRevealed by mutableStateOf(false)
    var modeRevealed by mutableStateOf(false)
    var isLooping by mutableStateOf(false)
    var currentBar by mutableStateOf(0)

    /** How many progressions have been generated in normal Progression training
     *  (excludes Challenge re-rolls). Shown as a small counter to the user. */
    var progressionCount by mutableStateOf(0)
        private set

    private var loopJob: Job? = null
    private val rng = Random.Default

    /** Tracks the last voicing played so the next chord can be picked by voice-leading.
     *  Persists across loop iterations so the wrap (bar 4 → bar 1) also flows. */
    private var prevPlayedShape: app.guitar.theory.ChordShape? = null

    /** Live broadcast: the shape currently being played by the progression looper.
     *  Compose can observe this to display the chord on the fretboard. */
    var currentPlayingShape by mutableStateOf<app.guitar.theory.ChordShape?>(null)
        private set

    /** The most-recently-sounded shape, persisted across stopLoop() so the optional
     *  fretboard panel keeps showing what was last heard. Updated by both the loop
     *  and per-bar play taps. */
    var lastShownShape by mutableStateOf<app.guitar.theory.ChordShape?>(null)
        private set

    /** Whether the fretboard panel under the progression cards is visible. */
    var showFretboard by mutableStateOf(false)

    /** True once the user has clicked "Generate progression" for the first time
     *  this session. We use this to gate the initial reveal cards behind an
     *  explicit user action so the very first progression honors the
     *  major/minor/sevenths settings the user has just chosen. */
    var hasGenerated by mutableStateOf(false)
        private set

    fun nextProgression() {
        // Pick a mode honoring the include flags
        val candidates = buildList {
            if (includeMajor) add(TrainingMode.Major)
            if (includeMinor) add(TrainingMode.Minor)
        }.ifEmpty { listOf(TrainingMode.Major) }
        val mode = candidates[rng.nextInt(candidates.size)]
        val key = fixedKey ?: PitchClass(rng.nextInt(12))
        val prog = EarTraining.randomProgression(mode, rng)
        progKey = key
        progMode = mode
        progProgression = prog
        progResolved = resolveCurrent(prog, key)
        // Hide all reveals for the new round
        progBarRevealed = emptySet()
        keyRevealed = false
        modeRevealed = false
        currentBar = 0
        hasGenerated = true
        // Count only progressions generated in normal Progression training.
        if (progSubMode == EarSubMode.Progression) progressionCount++
        // If we're currently playing, restart cleanly so the new progression begins immediately
        if (isLooping) {
            stopLoop()
            startLoop()
        }
    }

    /** Resolve the current progression's chords, honoring the mix-all setting
     *  (random chord-type level per bar) or the single [chordTypeLevel]. */
    private fun resolveCurrent(prog: Progression, key: PitchClass): List<ResolvedChord> =
        if (earMixAll) {
            prog.degrees.map { deg ->
                val lvl = ChordTypeLevel.entries[rng.nextInt(ChordTypeLevel.entries.size)]
                EarTraining.resolve(deg, key, prog.mode, lvl, rng)
            }
        } else {
            EarTraining.resolveProgression(prog, key, chordTypeLevel, rng)
        }

    /** Re-resolve the current progression in place (e.g. after changing level / mix). */
    fun reresolveCurrent() {
        val prog = progProgression ?: return
        progResolved = resolveCurrent(prog, progKey)
    }

    /** Resolve the [idx]-th chord of the current progression to a shape (using
     *  voice-leading from whatever last played in the loop, or E-shape if none),
     *  play it once, and update [lastShownShape] so the optional fretboard panel
     *  shows it. */
    fun playBarOnce(idx: Int) {
        val resolved = progResolved.getOrNull(idx) ?: return
        val parsed = ChordLibrary.parse(resolved.symbol) ?: return
        val (root, q) = parsed
        val tuning = tuningProvider()
        val shapes = ChordShapeGenerator(style = earStyle()).shapesFor(root, q, tuning, frets = DISPLAY_FRETS)
        if (shapes.isEmpty()) return
        val shape = if (prevPlayedShape == null) {
            shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E } ?: shapes.first()
        } else {
            shapes[app.guitar.theory.VoiceLeading.pickMinMovement(prevPlayedShape!!, shapes)]
        }
        prevPlayedShape = shape
        lastShownShape = shape
        currentBar = idx
        val midis = shape.notes.mapNotNull { it?.midi?.value }
        if (midis.isEmpty()) return
        // Strum spread is user-controlled (0 = struck at once); brighter timbre for clarity.
        audio.playChord(midis, strumDelayMillis = strumProvider(), sustainMillis = sustainProvider(),
            timbre = Timbre.Clarity)
    }

    private var cadenceJob: Job? = null

    /** Mode-aware cadence label for the progression key: "I–V–I" / "i–V–i". */
    fun progCadenceLabel(): String = if (progMode == TrainingMode.Major) "I–V–I" else "i–V–i"

    /** #1: play a I-V-I (major) / i-V-i (minor) cadence in the current progression
     *  key so the user can hear the tonic before identifying the progression. */
    fun playProgKeyCadence() {
        cadenceJob?.cancel()
        val map = if (progMode == TrainingMode.Major) EarTraining.MAJOR_DEGREES else EarTraining.MINOR_DEGREES
        cadenceJob = scope.launch {
            for (deg in listOf(1, 5, 1)) {
                val root = EarTraining.degreeRoot(progKey, deg, progMode)
                playSymbolOnce(NoteSpeller.spell(root) + (map[deg]?.triadQuality ?: ""), 600)
                delay(650)
            }
        }
    }

    fun toggleBarReveal(idx: Int) {
        progBarRevealed = if (idx in progBarRevealed) progBarRevealed - idx else progBarRevealed + idx
    }

    fun toggleKeyReveal() { keyRevealed = !keyRevealed }
    fun toggleModeReveal() { modeRevealed = !modeRevealed }

    /** Reveal/hide key and mode together — they share a single card in the UI. */
    fun toggleKeyModeReveal() { val v = !keyRevealed; keyRevealed = v; modeRevealed = v }

    fun startLoop() {
        if (isLooping) return
        if (progResolved.isEmpty()) nextProgression()
        prevPlayedShape = null   // reset voice-leading state so first chord = E-shape
        isLooping = true
        loopJob = scope.launch {
            val beatMs = (60_000L / progBpm.coerceAtLeast(20))
            // One chord per bar; 4 beats per bar.
            val barMs = beatMs * 4
            while (isLooping) {
                for (i in progResolved.indices) {
                    if (!isLooping) break
                    currentBar = i
                    val resolved = progResolved[i]
                    playChordOnce(resolved.symbol, barMs)
                    delay(barMs)
                }
            }
        }
    }

    fun stopLoop() {
        isLooping = false
        loopJob?.cancel()
        loopJob = null
        currentPlayingShape = null
        audio.stop()
    }

    /** Pick the next chord's voicing via voice-leading (first chord = E-shape), play it,
     *  and broadcast it on [currentPlayingShape] so any observer (e.g. the live fretboard)
     *  can show what's being heard. */
    private fun playChordOnce(symbol: String, barMs: Long) {
        val parsed = ChordLibrary.parse(symbol) ?: return
        val (root, q) = parsed
        val tuning = tuningProvider()
        val shapes = ChordShapeGenerator(style = earStyle()).shapesFor(root, q, tuning, frets = DISPLAY_FRETS)
        if (shapes.isEmpty()) return
        val shape = if (prevPlayedShape == null) {
            shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E } ?: shapes.first()
        } else {
            shapes[app.guitar.theory.VoiceLeading.pickMinMovement(prevPlayedShape!!, shapes)]
        }
        prevPlayedShape = shape
        currentPlayingShape = shape
        lastShownShape = shape
        val midis = shape.notes.mapNotNull { it?.midi?.value }
        if (midis.isEmpty()) return
        audio.playChord(midis,
            strumDelayMillis = strumProvider(),
            sustainMillis = (barMs * 0.9).toInt().coerceAtLeast(200),
            timbre = Timbre.Clarity)
    }

    // ---------- Note2Chord trainer ----------

    var n2cChallenge by mutableStateOf<app.guitar.theory.N2cChallenge?>(null)
    var n2cRevealed by mutableStateOf(false)
    var n2cPlaying by mutableStateOf(false)

    private var n2cJob: Job? = null

    fun nextN2cChallenge() {
        n2cChallenge = app.guitar.theory.N2cChallenge.random(rng)
        n2cRevealed = false
    }

    fun toggleN2cReveal() { n2cRevealed = !n2cRevealed }

    /** Plays the triad, waits, then plays the test note on top. */
    fun playN2c() {
        val c = n2cChallenge ?: run { nextN2cChallenge(); n2cChallenge!! }
        if (n2cPlaying) return
        n2cJob?.cancel()
        n2cPlaying = true
        n2cJob = scope.launch {
            try {
                // Find a guitar voicing for the triad in the current tuning
                val parsed = ChordLibrary.parse(c.chordSymbol) ?: return@launch
                val (root, q) = parsed
                val tuning = tuningProvider()
                val shapes = ChordShapeGenerator().shapesFor(root, q, tuning, frets = DISPLAY_FRETS)
                val shape = shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E }
                    ?: shapes.firstOrNull() ?: return@launch
                val midis = shape.notes.mapNotNull { it?.midi?.value }
                val sustain = sustainProvider()
                audio.playChord(midis, strumDelayMillis = 0, sustainMillis = sustain)
                delay(800)
                // Pick the test note in a useful octave: the closest tuning string fret
                // that produces the right pitch class, biased to the higher strings.
                val testMidi = nearestMidiAboveChord(c.testNote, midis)
                audio.playNote(testMidi, durationMillis = sustain)
            } finally {
                n2cPlaying = false
            }
        }
    }

    /** Midis of the current Note2Chord triad's E-shape (or first) voicing. */
    private fun n2cShapeMidis(): List<Int> {
        val c = n2cChallenge ?: return emptyList()
        val parsed = ChordLibrary.parse(c.chordSymbol) ?: return emptyList()
        val (root, q) = parsed
        val shapes = ChordShapeGenerator().shapesFor(root, q, tuningProvider(), frets = DISPLAY_FRETS)
        val shape = shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E }
            ?: shapes.firstOrNull() ?: return emptyList()
        return shape.notes.mapNotNull { it?.midi?.value }
    }

    /** #2: play just the triad (no test note). */
    fun playN2cChord() {
        val midis = n2cShapeMidis()
        if (midis.isNotEmpty()) audio.playChord(midis, strumDelayMillis = 0, sustainMillis = sustainProvider())
    }

    /** #2: play just the test note (placed above the triad's register). */
    fun playN2cNote() {
        val c = n2cChallenge ?: return
        val midis = n2cShapeMidis().ifEmpty { listOf(60) }
        audio.playNote(nearestMidiAboveChord(c.testNote, midis), durationMillis = sustainProvider())
    }

    /** Pick a MIDI note for the given pitch class that sits above the chord cluster
     *  (so the test note rings clearly on top). */
    private fun nearestMidiAboveChord(testPc: PitchClass, chordMidis: List<Int>): Int {
        val target = (chordMidis.maxOrNull() ?: 60) + 4    // a few semitones above the highest chord note
        // Find the MIDI within [target-6, target+6] whose pitchClass matches testPc.
        for (delta in 0..12) {
            for (sign in intArrayOf(+1, -1)) {
                val candidate = target + sign * delta
                if (candidate in 0..127 && ((candidate % 12) + 12) % 12 == testPc.value) return candidate
            }
        }
        return 60 + testPc.value   // last-resort C4-relative
    }

    // ---------- Note2Chord Challenge (scored rounds) ----------

    val n2cChallengeTotal: Int = 10
    var n2cChActive by mutableStateOf(false)
        private set
    var n2cChIndex by mutableStateOf(0)
        private set
    var n2cChScore by mutableStateOf(0)
        private set
    var n2cChGuess by mutableStateOf<String?>(null)
        private set

    /** Distinct answer-label options across major + minor diatonic test notes. */
    fun n2cAnswerOptions(): List<String> =
        (app.guitar.theory.N2cChallenge.MAJOR_TEST_OFFSETS + app.guitar.theory.N2cChallenge.MINOR_TEST_OFFSETS)
            .distinct().sorted().map { app.guitar.theory.N2cChallenge.label(it) }

    fun startN2cChallenge() {
        n2cChActive = true; n2cChIndex = 0; n2cChScore = 0; n2cChGuess = null
        nextN2cChallenge(); playN2c()
    }
    fun guessN2c(label: String) {
        if (!n2cChActive || n2cChGuess != null) return
        n2cChGuess = label
        if (label == n2cChallenge?.answerLabel) n2cChScore++
    }
    fun advanceN2cChallenge() {
        if (!n2cChActive) return
        if (n2cChIndex >= n2cChallengeTotal - 1) { n2cChIndex = n2cChallengeTotal; return }
        n2cChIndex++; n2cChGuess = null; nextN2cChallenge(); playN2c()
    }
    fun exitN2cChallenge() { n2cChActive = false; n2cChIndex = 0; n2cChGuess = null }

    // ---------- Progression Challenge (15-question quiz) ----------

    /** Length of one challenge session. */
    val challengeTotal: Int = 15

    /** Per-question answer state: null = not yet marked, true = right, false = wrong. */
    var challengeAnswers by mutableStateOf<List<Boolean?>>(emptyList())
        private set
    /** Per-question count of correctly-identified bars (0..4) — enables partial credit. */
    var challengeBarsCorrect by mutableStateOf<List<Int>>(emptyList())
        private set
    var challengeIndex by mutableStateOf(0)
        private set
    /** Whether a challenge session is currently in flight (vs. on the title/score screen). */
    var challengeActive by mutableStateOf(false)
        private set
    /** Whether the user has revealed the current question's answer.
     *  Right/Wrong buttons are only enabled after reveal. */
    var challengeRevealed by mutableStateOf(false)

    fun startChallenge() {
        challengeAnswers = List(challengeTotal) { null }
        challengeBarsCorrect = List(challengeTotal) { 0 }
        challengeIndex = 0
        challengeRevealed = false
        challengeActive = true
        resetChallengeGuesses()
        // Generate the first question's progression honoring current Major/Minor + chord-type settings.
        nextProgression()
    }

    fun markChallenge(correct: Boolean) {
        if (!challengeActive || challengeIndex >= challengeTotal) return
        challengeAnswers = challengeAnswers.toMutableList().also { it[challengeIndex] = correct }
    }

    /** Advance to the next question. If we were on the last one, exit to the score screen. */
    fun advanceChallenge() {
        if (!challengeActive) return
        if (challengeIndex >= challengeTotal - 1) {
            // Stay on `challengeActive = true` but `challengeIndex == total` signals "done".
            challengeIndex = challengeTotal
            stopLoop()
            return
        }
        challengeIndex++
        challengeRevealed = false
        resetChallengeGuesses()
        nextProgression()
    }

    fun exitChallenge() {
        challengeActive = false
        challengeRevealed = false
        challengeIndex = 0
        stopLoop()
    }

    /** Current score so far (number of questions with all bars correct). */
    val challengeScore: Int get() = challengeAnswers.count { it == true }
    /** Partial-credit score: total correctly-identified bars across all questions. */
    val challengeBarScore: Int get() = challengeBarsCorrect.sum()
    /** Maximum possible bar score (4 bars × every question). */
    val challengeBarTotal: Int get() = challengeTotal * 4

    // ---- #8/#9: gamified per-bar answering ----

    /** Per-bar degree guesses (1..7); null = unanswered. */
    var challengeGuessDegree by mutableStateOf<List<Int?>>(List(4) { null })
        private set
    /** Per-bar extension-label guesses; null = unanswered. */
    var challengeGuessExt by mutableStateOf<List<String?>>(List(4) { null })
        private set

    private fun resetChallengeGuesses() {
        challengeGuessDegree = List(4) { null }
        challengeGuessExt = List(4) { null }
    }

    /** Whether the challenge should ask for an extension (mix mode always does, since
     *  bars can be 7ths/extended; triad bars are answered with the "none" option). */
    val challengeNeedsExt: Boolean get() = earMixAll || chordTypeLevel != ChordTypeLevel.Triads

    private fun degreesMap() =
        if (progMode == TrainingMode.Major) EarTraining.MAJOR_DEGREES else EarTraining.MINOR_DEGREES

    /** Degree-button options for the current mode: (degree 1..7, Roman label). */
    fun challengeDegreeOptions(): List<Pair<Int, String>> =
        degreesMap().entries.sortedBy { it.key }.map { it.key to it.value.roman }

    /** Distinct extension-label options for the current mode + level. In mix mode the
     *  union spans triad ("") + 7th + extended suffixes so every possible bar is answerable. */
    fun challengeExtOptions(): List<String> {
        if (!challengeNeedsExt) return emptyList()
        val m = degreesMap()
        if (earMixAll) {
            val labels = linkedSetOf("")   // "" = triad / no extension
            for (info in m.values) {
                labels.add(EarTraining.romanLabel(info.roman, info.seventhQuality).removePrefix(info.roman))
                if (info.extendedOptions.isNotEmpty()) info.extendedOptions.forEach { labels.add(it.second) }
                else labels.add(EarTraining.romanLabel(info.roman, info.extendedQuality).removePrefix(info.roman))
            }
            return labels.sorted()
        }
        return m.values.flatMap { info ->
            when (chordTypeLevel) {
                ChordTypeLevel.Sevenths ->
                    listOf(EarTraining.romanLabel(info.roman, info.seventhQuality).removePrefix(info.roman))
                ChordTypeLevel.Extended ->
                    if (info.extendedOptions.isNotEmpty()) info.extendedOptions.map { it.second }
                    else listOf(EarTraining.romanLabel(info.roman, info.extendedQuality).removePrefix(info.roman))
                else -> emptyList()
            }
        }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    /** Correct extension label for bar [i] (suffix of its Roman label), or "" if none. */
    fun correctExtLabel(i: Int): String {
        val deg = progProgression?.degrees?.getOrNull(i) ?: return ""
        val info = degreesMap()[deg] ?: return ""
        return progResolved.getOrNull(i)?.romanLabel?.removePrefix(info.roman) ?: ""
    }

    /** null = bar not fully answered yet; true/false = correct/incorrect. */
    fun challengeBarCorrect(i: Int): Boolean? {
        val deg = progProgression?.degrees?.getOrNull(i) ?: return null
        val g = challengeGuessDegree.getOrNull(i) ?: return null
        if (challengeNeedsExt && challengeGuessExt.getOrNull(i) == null) return null
        val degOk = g == deg
        val extOk = !challengeNeedsExt || challengeGuessExt.getOrNull(i) == correctExtLabel(i)
        return degOk && extOk
    }

    fun guessChallengeDegree(bar: Int, degree: Int) {
        if (!challengeActive) return
        challengeGuessDegree = challengeGuessDegree.toMutableList().also { it[bar] = degree }
        maybeAutoMarkChallenge()
    }

    fun guessChallengeExt(bar: Int, ext: String) {
        if (!challengeActive) return
        challengeGuessExt = challengeGuessExt.toMutableList().also { it[bar] = ext }
        maybeAutoMarkChallenge()
    }

    /** Re-roll the current question's progression and clear its guesses. */
    fun rerollChallengeQuestion() {
        resetChallengeGuesses()
        nextProgression()
    }

    /** Once every bar is fully answered, auto-score the question (all bars right = a point). */
    private fun maybeAutoMarkChallenge() {
        if (!challengeActive || challengeIndex >= challengeTotal) return
        if (challengeAnswers.getOrNull(challengeIndex) != null) return
        val degrees = progProgression?.degrees ?: return
        for (i in degrees.indices) if (challengeBarCorrect(i) == null) return
        val correctCount = degrees.indices.count { challengeBarCorrect(it) == true }
        if (challengeIndex < challengeBarsCorrect.size) {
            challengeBarsCorrect = challengeBarsCorrect.toMutableList().also { it[challengeIndex] = correctCount }
        }
        markChallenge(correctCount == degrees.size)
    }

    // ---------- #5 Chord Flavor trainer ----------

    /** Palette of chord flavors the user may enable for the random pool. */
    val flavorPalette: List<String> = listOf(
        "", "m", "dim", "aug", "sus2", "sus4",
        "7", "maj7", "m7", "m7b5",
        "9", "m9", "maj9", "11", "13",
    )

    /** Flavors currently enabled (chord-quality symbols). */
    var flavorAllowed by mutableStateOf(setOf("", "m", "7", "maj7", "m7"))

    /** Which key-center modes may appear in the flavor trainer. */
    var flavorIncludeMajor by mutableStateOf(true)
    var flavorIncludeMinor by mutableStateOf(true)

    var flavorKey by mutableStateOf(PitchClass.C)
        private set
    /** Diatonic scale degree (1..7) the drawn chord's root sits on. */
    var flavorDegree by mutableStateOf(1)
        private set
    /** Quality of the drawn chord (a member of [flavorAllowed]). */
    var flavorQuality by mutableStateOf("")
        private set
    var flavorRevealed by mutableStateOf(false)
    var flavorGuessDegree by mutableStateOf<Int?>(null)
    var flavorGuessQuality by mutableStateOf<String?>(null)
    var flavorPlaying by mutableStateOf(false)
        private set
    /** True once the user has generated the first flavor chord this session. */
    var flavorStarted by mutableStateOf(false)
        private set

    private var flavorJob: Job? = null
    /** Each flavor challenge picks a major or minor key; the cadence is I-V-I (major)
     *  or i-V-i (minor, harmonic-minor major V). */
    var flavorMode by mutableStateOf(TrainingMode.Major)
        private set

    fun toggleFlavorAllowed(sym: String) {
        flavorAllowed = if (sym in flavorAllowed) flavorAllowed - sym else flavorAllowed + sym
    }

    private fun flavorRootPc(): PitchClass = EarTraining.degreeRoot(flavorKey, flavorDegree, flavorMode)
    fun flavorChordSymbol(): String = NoteSpeller.spell(flavorRootPc()) + flavorQuality
    private fun flavorDegreesMap() =
        if (flavorMode == TrainingMode.Major) EarTraining.MAJOR_DEGREES else EarTraining.MINOR_DEGREES

    /** Roman base for the drawn degree (e.g. "IV"/"iv"), for the reveal. */
    fun flavorDegreeRoman(): String = flavorDegreesMap()[flavorDegree]?.roman ?: "$flavorDegree"

    /** Mode-aware cadence label for the flavor key-setter: "I–V–I" / "i–V–i". */
    fun flavorCadenceLabel(): String = if (flavorMode == TrainingMode.Major) "I–V–I" else "i–V–i"

    /**
     * Diatonic (degree, quality) candidates for [mode]: for every degree, the
     * triad / 7th / extended qualities the diatonic scale actually produces.
     * When [allowed] is non-null, only candidates whose quality the user enabled
     * are kept — so we never play a non-diatonic flavor (e.g. v-m7 instead of V7).
     */
    private fun diatonicFlavorCandidates(
        mode: TrainingMode,
        allowed: Set<String>?,
    ): List<Pair<Int, String>> {
        val map = if (mode == TrainingMode.Major) EarTraining.MAJOR_DEGREES else EarTraining.MINOR_DEGREES
        val out = ArrayList<Pair<Int, String>>()
        for ((deg, info) in map) {
            val quals = linkedSetOf(info.triadQuality, info.seventhQuality)
            if (info.extendedOptions.isNotEmpty()) info.extendedOptions.forEach { quals.add(it.first) }
            else quals.add(info.extendedQuality)
            for (q in quals) if (allowed == null || q in allowed) out.add(deg to q)
        }
        return out
    }

    /** Draw a fresh challenge (new random key + mode, then a DIATONIC chord whose
     *  flavor the user enabled), play the cadence, then sound the chord. */
    fun newFlavorChallenge() {
        flavorKey = fixedKey ?: PitchClass(rng.nextInt(12))
        val modes = buildList {
            if (flavorIncludeMajor) add(TrainingMode.Major)
            if (flavorIncludeMinor) add(TrainingMode.Minor)
        }.ifEmpty { listOf(TrainingMode.Major) }
        flavorMode = modes[rng.nextInt(modes.size)]
        // Only diatonic chords: pick a (degree, diatonic-quality) the user allowed;
        // if their palette excludes every diatonic chord, fall back to all diatonic.
        val candidates = diatonicFlavorCandidates(flavorMode, flavorAllowed)
            .ifEmpty { diatonicFlavorCandidates(flavorMode, null) }
        val (deg, qual) = candidates[rng.nextInt(candidates.size)]
        flavorDegree = deg
        flavorQuality = qual
        flavorRevealed = false
        flavorGuessDegree = null
        flavorGuessQuality = null
        flavorStarted = true
        flavorJob?.cancel()
        flavorPlaying = true
        flavorJob = scope.launch {
            try {
                playCadenceInline()
                delay(400)
                playSymbolOnce(flavorChordSymbol(), sustainProvider())
            } finally { flavorPlaying = false }
        }
    }

    /** Replay just the I-V-I cadence (does not redraw the chord). */
    fun replayFlavorCadence() {
        if (flavorPlaying) return
        flavorJob?.cancel()
        flavorPlaying = true
        flavorJob = scope.launch { try { playCadenceInline() } finally { flavorPlaying = false } }
    }

    /** Replay the currently-drawn flavor chord. */
    fun playFlavorChord() {
        flavorJob?.cancel()
        flavorJob = scope.launch { playSymbolOnce(flavorChordSymbol(), sustainProvider()) }
    }

    fun toggleFlavorReveal() { flavorRevealed = !flavorRevealed }

    /** Audition degree [deg] at the currently-drawn flavor, in the current key —
     *  lets the user compare candidate degrees (e.g. ii vs iii vs vi) by ear when
     *  guessing. */
    fun auditionFlavorDegree(deg: Int) {
        playSymbolOnce(
            NoteSpeller.spell(EarTraining.degreeRoot(flavorKey, deg, flavorMode)) + flavorQuality,
            sustainProvider(),
        )
    }

    /** Audition the current degree at quality [qual] — compare flavors by ear. */
    fun auditionFlavorQuality(qual: String) {
        playSymbolOnce(NoteSpeller.spell(flavorRootPc()) + qual, sustainProvider())
    }

    /** Audition degree [deg]'s diatonic chord in the progression key (challenge
     *  per-bar guessing) so the user can compare candidates. */
    fun auditionProgDegree(deg: Int) {
        val level = if (earMixAll) ChordTypeLevel.Sevenths else chordTypeLevel
        playSymbolOnce(EarTraining.resolve(deg, progKey, progMode, level, rng).symbol, sustainProvider())
    }

    // ---- Flavor Challenge (scored rounds) ----
    val flavorChallengeTotal: Int = 10
    var flavorChActive by mutableStateOf(false)
        private set
    var flavorChIndex by mutableStateOf(0)
        private set
    var flavorChScore by mutableStateOf(0)
        private set
    var flavorChAnswered by mutableStateOf(false)
        private set

    fun startFlavorChallenge() {
        flavorChActive = true; flavorChIndex = 0; flavorChScore = 0; flavorChAnswered = false
        newFlavorChallenge()
    }
    /** Lock in the current degree+flavor guess and score it. */
    fun submitFlavorGuess() {
        if (!flavorChActive || flavorChAnswered) return
        if (flavorGuessDegree == null || flavorGuessQuality == null) return
        flavorChAnswered = true
        flavorRevealed = true
        if (flavorGuessDegree == flavorDegree && flavorGuessQuality == flavorQuality) flavorChScore++
    }
    fun advanceFlavorChallenge() {
        if (!flavorChActive) return
        if (flavorChIndex >= flavorChallengeTotal - 1) { flavorChIndex = flavorChallengeTotal; return }
        flavorChIndex++; flavorChAnswered = false; newFlavorChallenge()
    }
    fun exitFlavorChallenge() { flavorChActive = false; flavorChIndex = 0; flavorChAnswered = false }

    private suspend fun playCadenceInline() {
        val map = flavorDegreesMap()
        for (deg in listOf(1, 5, 1)) {
            val root = EarTraining.degreeRoot(flavorKey, deg, flavorMode)
            val symbol = NoteSpeller.spell(root) + (map[deg]?.triadQuality ?: "")
            playSymbolOnce(symbol, 600)
            delay(650)
        }
    }

    /** Voice [symbol] (E-shape preferred) and strum it once with the clarity timbre. */
    private fun playSymbolOnce(symbol: String, sustainMs: Int) {
        val parsed = ChordLibrary.parse(symbol) ?: return
        val (root, q) = parsed
        val tuning = tuningProvider()
        val shapes = ChordShapeGenerator().shapesFor(root, q, tuning, frets = DISPLAY_FRETS)
        val shape = shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E }
            ?: shapes.firstOrNull() ?: return
        val midis = shape.notes.mapNotNull { it?.midi?.value }
        if (midis.isEmpty()) return
        audio.playChord(midis, strumDelayMillis = strumProvider(), sustainMillis = sustainMs, timbre = Timbre.Clarity)
    }

    fun release() {
        stopLoop()
        n2cJob?.cancel()
        n2cJob = null
        flavorJob?.cancel()
        flavorJob = null
        cadenceJob?.cancel()
        cadenceJob = null
    }
}

enum class EarSubMode { Progression, Note2Chord, Flavor }

/** Within any tab: free Practice or scored Challenge rounds. */
enum class EarMode { Practice, Challenge }

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
    /** Called once when a progression challenge finishes, with the final bar score,
     *  the max possible bar score, and the wall-clock duration in ms. */
    private val onProgressionChallengeComplete: (score: Int, total: Int, durationMs: Long) -> Unit =
        { _, _, _ -> },
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
    /** Whether the user wants Minor mode in the rotation. Default off: the app
     *  opens in major-triads-only for the simplest starting point. */
    var includeMinor by mutableStateOf(false)
    /** Triads vs Sevenths vs Extended. Default Triads (simplest). */
    var chordTypeLevel by mutableStateOf(ChordTypeLevel.Triads)
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
        if (shapes.isEmpty()) {
            // Fallback for exotic chords with no playable guitar voicing (some
            // advanced-progression chords): sound the chord tones as a block.
            currentPlayingShape = null
            val rootMidi = 52 + root.value
            val midis = q.intervals.map { rootMidi + it.semitones }
            audio.playChord(midis, strumDelayMillis = strumProvider(),
                sustainMillis = (barMs * 0.9).toInt().coerceAtLeast(200), timbre = Timbre.Clarity)
            return
        }
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

    /** Play the [idx]-th chord of the current progression as a block built directly
     *  from its chord tones — guarantees any quality (incl. exotic advanced ones)
     *  sounds, regardless of guitar-voicing availability. */
    fun playProgChordDirect(idx: Int) {
        val rc = progResolved.getOrNull(idx) ?: return
        val (root, q) = ChordLibrary.parse(rc.symbol) ?: return
        val rootMidi = 52 + root.value
        val midis = q.intervals.map { rootMidi + it.semitones }
        scope.launch {
            audio.playChord(midis, strumDelayMillis = strumProvider(),
                sustainMillis = sustainProvider(), timbre = Timbre.Clarity)
        }
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

    /** Wall-clock start of the current challenge (for the high-score time tiebreak). */
    private var challengeStartMs = 0L
    /** Duration of the just-finished challenge in ms (valid on the score screen). */
    var challengeDurationMs by mutableStateOf(0L)
        private set

    fun startChallenge() {
        challengeAnswers = List(challengeTotal) { null }
        challengeBarsCorrect = List(challengeTotal) { 0 }
        challengeIndex = 0
        challengeRevealed = false
        challengeActive = true
        challengeStartMs = System.currentTimeMillis()
        challengeDurationMs = 0L
        // Fresh question history; generate the first question honoring current settings.
        challengeLog.clear()
        val q = freshChallengeQuestion()
        challengeLog.add(q)
        applyChallengeQuestion(q)
    }

    fun markChallenge(correct: Boolean) {
        if (!challengeActive || challengeIndex >= challengeTotal) return
        challengeAnswers = challengeAnswers.toMutableList().also { it[challengeIndex] = correct }
    }

    /**
     * Finalize the current question's score. Per the "skip = credit" rule: a bar
     * counts correct if it was answered correctly OR left completely unanswered
     * (no degree chosen). A bar with a wrong (or partial) guess counts incorrect.
     */
    private fun finalizeCurrentQuestion() {
        if (!challengeActive || challengeIndex >= challengeTotal) return
        val degrees = progProgression?.degrees ?: return
        val correctCount = degrees.indices.count { i ->
            challengeBarCorrect(i) == true || challengeGuessDegree.getOrNull(i) == null
        }
        if (challengeIndex < challengeBarsCorrect.size) {
            challengeBarsCorrect = challengeBarsCorrect.toMutableList().also { it[challengeIndex] = correctCount }
        }
        challengeAnswers = challengeAnswers.toMutableList().also { it[challengeIndex] = (correctCount == degrees.size) }
    }

    /** Advance to the next question. Always allowed — any unanswered bars in the
     *  current question are credited as correct. If we were on the last one, score
     *  the session and hand it to the high-score table, then show the score screen. */
    fun advanceChallenge() {
        if (!challengeActive) return
        saveChallengeGuesses()
        finalizeCurrentQuestion()
        if (challengeIndex >= challengeTotal - 1) {
            // Stay on `challengeActive = true` but `challengeIndex == total` signals "done".
            challengeIndex = challengeTotal
            challengeDurationMs = System.currentTimeMillis() - challengeStartMs
            stopLoop()
            onProgressionChallengeComplete(challengeBarScore, challengeBarTotal, challengeDurationMs)
            return
        }
        val next = challengeIndex + 1
        if (next < challengeLog.size) {
            // Revisiting a question we've already seen — restore it (and its answers).
            challengeIndex = next
            applyChallengeQuestion(challengeLog[next])
        } else {
            val q = freshChallengeQuestion()
            challengeLog.add(q)
            challengeIndex = next
            applyChallengeQuestion(q)
        }
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
    /** #6: per-bar display label of the user's keyboard answer (e.g. "V7", "iv"),
     *  in whatever Roman system they entered it; null = the bar's square is empty. */
    var challengeGuessLabel by mutableStateOf<List<String?>>(List(4) { null })
        private set

    /** #6: answer-keyboard "shift" state — false shows the MAJOR Roman row
     *  (I ii iii IV V vi vii°), true shows the MINOR row (i ii° III iv v VI VII).
     *  Both rows label the same seven shared diatonic chords; see
     *  [EarTraining.majorRelativeDegree]. */
    var keyboardMinor by mutableStateOf(false)

    private fun resetChallengeGuesses() {
        challengeGuessDegree = List(4) { null }
        challengeGuessExt = List(4) { null }
        challengeGuessLabel = List(4) { null }
    }

    // ---- #4/#5: question history so the user can step back and forward ----

    /** One challenge question: the generated progression + the user's saved guesses. */
    private class QState(
        val key: PitchClass,
        val mode: TrainingMode,
        val prog: Progression,
        val resolved: List<ResolvedChord>,
        var guessDeg: List<Int?>,
        var guessExt: List<String?>,
        var guessLabel: List<String?>,
    )

    private val challengeLog = ArrayList<QState>()

    private fun freshChallengeQuestion(): QState {
        val candidates = buildList {
            if (includeMajor) add(TrainingMode.Major)
            if (includeMinor) add(TrainingMode.Minor)
        }.ifEmpty { listOf(TrainingMode.Major) }
        val mode = candidates[rng.nextInt(candidates.size)]
        val key = fixedKey ?: PitchClass(rng.nextInt(12))
        val prog = EarTraining.randomProgression(mode, rng)
        return QState(key, mode, prog, resolveCurrent(prog, key),
            List(4) { null }, List(4) { null }, List(4) { null })
    }

    /** Make [q] the live question (prog* + guesses), resetting reveals. */
    private fun applyChallengeQuestion(q: QState) {
        progKey = q.key
        progMode = q.mode
        progProgression = q.prog
        progResolved = q.resolved
        challengeGuessDegree = q.guessDeg
        challengeGuessExt = q.guessExt
        challengeGuessLabel = q.guessLabel
        progBarRevealed = emptySet()
        keyRevealed = false
        modeRevealed = false
        currentBar = 0
        challengeRevealed = false
        if (isLooping) { stopLoop(); startLoop() }
    }

    /** Persist the live guesses back into the log for the current index. */
    private fun saveChallengeGuesses() {
        challengeLog.getOrNull(challengeIndex)?.let {
            it.guessDeg = challengeGuessDegree
            it.guessExt = challengeGuessExt
            it.guessLabel = challengeGuessLabel
        }
    }

    /** True when stepping back to an earlier question is possible. */
    val canGoPrevChallenge: Boolean get() = challengeActive && challengeIndex in 1 until challengeTotal

    /** #4/#5: step back to the previous question, restoring its saved answers. */
    fun previousChallengeQuestion() {
        if (!canGoPrevChallenge) return
        saveChallengeGuesses()
        finalizeCurrentQuestion()
        challengeIndex--
        applyChallengeQuestion(challengeLog[challengeIndex])
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

    /**
     * #3: When the level is fixed Sevenths (not mix), each scale degree has exactly
     * ONE diatonic 7th (ii→ii7, V→V7, etc.), so the user shouldn't pick degree and
     * extension separately — a single combined choice ("V7") encodes both. (Triads
     * have no extension; mix mode varies the level per bar, so both keep the
     * separate degree+extension pickers.)
     */
    val challengeCombinedMode: Boolean
        get() = !earMixAll && chordTypeLevel == ChordTypeLevel.Sevenths

    /** Combined diatonic-7th options for the current mode: (degree, "V7"-style label). */
    fun challengeCombinedOptions(): List<Pair<Int, String>> =
        degreesMap().entries.sortedBy { it.key }.map { (deg, info) ->
            deg to EarTraining.romanLabel(info.roman, info.seventhQuality)
        }

    /** Pick a combined diatonic-7th answer — sets both the degree and its (forced)
     *  diatonic extension for [bar], then auto-scores if the question is complete. */
    fun guessChallengeCombined(bar: Int, degree: Int) {
        if (!challengeActive) return
        val info = degreesMap()[degree] ?: return
        val ext = EarTraining.romanLabel(info.roman, info.seventhQuality).removePrefix(info.roman)
        challengeGuessDegree = challengeGuessDegree.toMutableList().also { it[bar] = degree }
        challengeGuessExt = challengeGuessExt.toMutableList().also { it[bar] = ext }
        maybeAutoMarkChallenge()
    }

    /** #3: labels for the dedicated "hear the degrees" reference palette — PLAIN
     *  Arabic numbers 1..7, never Roman numerals / qualities, so hearing a degree
     *  doesn't visually give away whether the key is major or minor (that's for the
     *  user to identify). Played, in the hidden key, via [auditionProgDegree]. */
    fun challengeReferenceLabels(): List<Pair<Int, String>> =
        degreesMap().keys.sorted().map { it to it.toString() }

    /** Re-roll the current question's progression and clear its guesses. */
    fun rerollChallengeQuestion() {
        if (!challengeActive) { resetChallengeGuesses(); nextProgression(); return }
        val q = freshChallengeQuestion()
        if (challengeIndex in challengeLog.indices) challengeLog[challengeIndex] = q
        else challengeLog.add(q)
        applyChallengeQuestion(q)
    }

    // ---- #6: degree-keyboard answering ----

    /** Roman labels for the 7 keyboard keys in the currently-shown system, paired
     *  with the relative-major degree (1..7) each key represents. The minor row's
     *  keys map to the SAME shared chords as the major row (see
     *  [EarTraining.majorRelativeDegree]); both are accepted as equivalent. */
    fun keyboardKeys(): List<Pair<Int, String>> {
        val map = if (keyboardMinor) EarTraining.MINOR_DEGREES else EarTraining.MAJOR_DEGREES
        val mode = if (keyboardMinor) TrainingMode.Minor else TrainingMode.Major
        return (1..7).map { pos ->
            EarTraining.majorRelativeDegree(pos, mode) to (map[pos]?.roman ?: pos.toString())
        }
    }

    fun toggleKeyboardShift() { keyboardMinor = !keyboardMinor }

    /**
     * Commit a keyboard answer for [bar]: [majorRelativeDegree] is the relative-major
     * degree the tapped key stands for (so a major-row and the equivalent minor-row
     * key produce the same answer). The degree is converted into the actual key's
     * mode for scoring, so identifying I–IV–V or its minor III–VI–VII both score.
     * [roman] is the tapped key's label in the user's chosen system (used to build
     * the square's display). [ext] is the chosen extension suffix when the level
     * needs one (ignored for triads; forced to the diatonic 7th in fixed-7ths mode).
     */
    fun guessChallengeKeyboard(bar: Int, majorRelativeDegree: Int, roman: String, ext: String?) {
        if (!challengeActive || bar !in 0..3) return
        val deg = EarTraining.degreeFromMajorRelative(majorRelativeDegree, progMode)
        challengeGuessDegree = challengeGuessDegree.toMutableList().also { it[bar] = deg }
        val extSuffix: String = when {
            challengeCombinedMode -> {
                val info = degreesMap()[deg]
                val e = if (info != null)
                    EarTraining.romanLabel(info.roman, info.seventhQuality).removePrefix(info.roman) else ""
                challengeGuessExt = challengeGuessExt.toMutableList().also { it[bar] = e }
                e
            }
            challengeNeedsExt -> {
                challengeGuessExt = challengeGuessExt.toMutableList().also { it[bar] = ext ?: "" }
                ext ?: ""
            }
            else -> ""
        }
        challengeGuessLabel = challengeGuessLabel.toMutableList().also { it[bar] = roman + extSuffix }
        maybeAutoMarkChallenge()
    }

    /** Clear bar [bar]'s keyboard answer (empties its square). */
    fun clearChallengeBar(bar: Int) {
        if (bar !in 0..3) return
        challengeGuessDegree = challengeGuessDegree.toMutableList().also { it[bar] = null }
        challengeGuessExt = challengeGuessExt.toMutableList().also { it[bar] = null }
        challengeGuessLabel = challengeGuessLabel.toMutableList().also { it[bar] = null }
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
        "6", "m6", "7", "maj7", "m7", "m7b5",
        "add9", "9", "m9", "maj9", "11", "13",
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

    // ---------- #2 Advanced (non-diatonic) progressions ----------

    /** Whether the Progression sub-mode is showing advanced named progressions. */
    var advancedMode by mutableStateOf(false)
    /** The currently-drawn advanced progression (null until generated). */
    var advProg by mutableStateOf<app.guitar.theory.EarTraining.NamedProgression?>(null)
        private set
    /** Whether the advanced answer (name + Roman line + chords) is revealed. */
    var advRevealed by mutableStateOf(false)

    /** Draw a fresh advanced progression (random named progression + key), load it
     *  into [progResolved] for the shared looper, and reset the reveal. */
    fun nextAdvancedProgression() {
        val np = EarTraining.randomAdvanced(rng)
        val key = fixedKey ?: PitchClass(rng.nextInt(12))
        advProg = np
        progKey = key
        progMode = np.tonicMode
        progProgression = null
        progResolved = np.resolve(key)
        advRevealed = false
        hasGenerated = true
        prevPlayedShape = null
        if (isLooping) { stopLoop(); startLoop() }
    }

    fun toggleAdvReveal() { advRevealed = !advRevealed }

    // Advanced challenge: self-marked (chromatic chords make multiple-choice impractical).
    val advChallengeTotal: Int = 10
    var advChActive by mutableStateOf(false)
        private set
    var advChIndex by mutableStateOf(0)
        private set
    var advChScore by mutableStateOf(0)
        private set
    var advChMarked by mutableStateOf(false)
        private set

    fun startAdvChallenge() {
        advChActive = true; advChIndex = 0; advChScore = 0; advChMarked = false
        nextAdvancedProgression()
        startLoop()
    }
    fun markAdv(correct: Boolean) {
        if (!advChActive || advChMarked) return
        advChMarked = true
        advRevealed = true
        if (correct) advChScore++
    }
    fun advanceAdvChallenge() {
        if (!advChActive) return
        if (advChIndex >= advChallengeTotal - 1) { advChIndex = advChallengeTotal; stopLoop(); return }
        advChIndex++; advChMarked = false
        nextAdvancedProgression()
    }
    fun exitAdvChallenge() { advChActive = false; advChIndex = 0; stopLoop() }

    // ---------- #3 Inversions trainer ----------

    /** Chord qualities selectable for the inversions trainer. */
    val invPalette: List<String> = listOf(
        "", "m", "sus2", "sus4", "aug", "dim",
        "7", "maj7", "m7", "m7b5", "dim7", "6", "m6", "9", "maj9", "m9",
    )
    var invAllowed by mutableStateOf(setOf("", "m", "7"))

    var invRoot by mutableStateOf(PitchClass.C)
        private set
    var invQuality by mutableStateOf("")
        private set
    var invInversion by mutableStateOf(0)
        private set
    var invRevealed by mutableStateOf(false)
    var invGuess by mutableStateOf<Int?>(null)
    var invStarted by mutableStateOf(false)
        private set
    var invPlaying by mutableStateOf(false)
        private set
    private var invJob: Job? = null

    fun toggleInvAllowed(sym: String) {
        invAllowed = if (sym in invAllowed) invAllowed - sym else invAllowed + sym
    }

    /** Number of inversions the current chord quality has (3 for triads, 4 for 7ths). */
    fun invCount(): Int {
        val q = ChordLibrary.qualities[invQuality] ?: return 3
        return app.guitar.theory.Inversions.count(q)
    }

    private fun invMidis(inversion: Int): List<Int> {
        val q = ChordLibrary.qualities[invQuality] ?: return emptyList()
        val rootMidi = 52 + invRoot.value   // E3-ish base register
        return app.guitar.theory.Inversions.midis(rootMidi, q, inversion)
    }

    /** Draw a new chord (random allowed quality, root, and inversion) and play it. */
    fun newInversion() {
        val pool = invAllowed.ifEmpty { setOf("") }.toList()
        invQuality = pool[rng.nextInt(pool.size)]
        invRoot = PitchClass(rng.nextInt(12))
        invInversion = rng.nextInt(invCount())
        invRevealed = false
        invGuess = null
        invStarted = true
        playInversion()
    }

    /** Replay the current chord in its (hidden) inversion. */
    fun playInversion() {
        val midis = invMidis(invInversion)
        if (midis.isEmpty()) return
        invJob?.cancel()
        invPlaying = true
        invJob = scope.launch {
            try {
                audio.playChord(midis, strumDelayMillis = strumProvider(),
                    sustainMillis = sustainProvider(), timbre = Timbre.Clarity)
            } finally { invPlaying = false }
        }
    }

    /** Audition inversion [k] of the current chord — lets the user compare by ear. */
    fun auditionInversion(k: Int) {
        val midis = invMidis(k)
        if (midis.isNotEmpty()) {
            scope.launch {
                audio.playChord(midis, strumDelayMillis = strumProvider(),
                    sustainMillis = sustainProvider(), timbre = Timbre.Clarity)
            }
        }
    }

    fun toggleInvReveal() { invRevealed = !invRevealed }

    // Inversions challenge (scored).
    val invChallengeTotal: Int = 10
    var invChActive by mutableStateOf(false)
        private set
    var invChIndex by mutableStateOf(0)
        private set
    var invChScore by mutableStateOf(0)
        private set
    var invChAnswered by mutableStateOf(false)
        private set

    fun startInvChallenge() {
        invChActive = true; invChIndex = 0; invChScore = 0; invChAnswered = false
        newInversion()
    }
    fun submitInvGuess() {
        if (!invChActive || invChAnswered) return
        val g = invGuess ?: return
        invChAnswered = true; invRevealed = true
        if (g == invInversion) invChScore++
    }
    fun advanceInvChallenge() {
        if (!invChActive) return
        if (invChIndex >= invChallengeTotal - 1) { invChIndex = invChallengeTotal; return }
        invChIndex++; invChAnswered = false; newInversion()
    }
    fun exitInvChallenge() { invChActive = false; invChIndex = 0 }

    // ---------- #4 Augmented vs Diminished trainer ----------

    /** Qualities selectable for the aug/dim trainer (triads + their 7th/extended forms). */
    val augDimPalette: List<String> = listOf("aug", "dim", "dim7", "m7b5", "7#5", "maj7#5")
    var augDimAllowed by mutableStateOf(setOf("aug", "dim"))

    var adRoot by mutableStateOf(PitchClass.C)
        private set
    var adQuality by mutableStateOf("aug")
        private set
    var adRevealed by mutableStateOf(false)
    var adGuess by mutableStateOf<String?>(null)
    var adStarted by mutableStateOf(false)
        private set
    private var adJob: Job? = null

    fun toggleAugDimAllowed(sym: String) {
        augDimAllowed = if (sym in augDimAllowed) augDimAllowed - sym else augDimAllowed + sym
    }

    /** True family ("Augmented" / "Diminished") of a quality symbol, for grouping/feedback. */
    fun augDimFamily(sym: String): String =
        if (sym.startsWith("aug") || sym == "7#5" || sym == "maj7#5") "Augmented" else "Diminished"

    private fun adMidis(quality: String): List<Int> {
        val q = ChordLibrary.qualities[quality] ?: return emptyList()
        val rootMidi = 52 + adRoot.value
        return q.intervals.map { rootMidi + it.semitones }
    }

    fun newAugDim() {
        val pool = augDimAllowed.ifEmpty { setOf("aug", "dim") }.toList()
        adQuality = pool[rng.nextInt(pool.size)]
        adRoot = PitchClass(rng.nextInt(12))
        adRevealed = false; adGuess = null; adStarted = true
        playAugDim()
    }
    fun playAugDim() {
        val midis = adMidis(adQuality)
        if (midis.isEmpty()) return
        adJob?.cancel()
        adJob = scope.launch {
            audio.playChord(midis, strumDelayMillis = strumProvider(),
                sustainMillis = sustainProvider(), timbre = Timbre.Clarity)
        }
    }
    /** Audition quality [sym] at the current root — compare aug vs dim sounds. */
    fun auditionAugDim(sym: String) {
        val midis = adMidis(sym)
        if (midis.isNotEmpty()) {
            scope.launch {
                audio.playChord(midis, strumDelayMillis = strumProvider(),
                    sustainMillis = sustainProvider(), timbre = Timbre.Clarity)
            }
        }
    }
    fun toggleAugDimReveal() { adRevealed = !adRevealed }

    // Aug/Dim challenge (scored).
    val augDimChallengeTotal: Int = 10
    var adChActive by mutableStateOf(false)
        private set
    var adChIndex by mutableStateOf(0)
        private set
    var adChScore by mutableStateOf(0)
        private set
    var adChAnswered by mutableStateOf(false)
        private set

    fun startAugDimChallenge() {
        adChActive = true; adChIndex = 0; adChScore = 0; adChAnswered = false
        newAugDim()
    }
    fun submitAugDimGuess() {
        if (!adChActive || adChAnswered) return
        val g = adGuess ?: return
        adChAnswered = true; adRevealed = true
        if (g == adQuality) adChScore++
    }
    fun advanceAugDimChallenge() {
        if (!adChActive) return
        if (adChIndex >= augDimChallengeTotal - 1) { adChIndex = augDimChallengeTotal; return }
        adChIndex++; adChAnswered = false; newAugDim()
    }
    fun exitAugDimChallenge() { adChActive = false; adChIndex = 0 }

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
        invJob?.cancel()
        invJob = null
        adJob?.cancel()
        adJob = null
    }
}

enum class EarSubMode { Progression, Note2Chord, Flavor, Inversions, AugDim }

/** Within any tab: free Practice or scored Challenge rounds. */
enum class EarMode { Practice, Challenge }

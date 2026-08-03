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
import app.guitar.theory.IntervalDirection
import app.guitar.theory.IntervalTrainer
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
    /** Called once when any OTHER challenge finishes (kind = "inversions" / "augdim" /
     *  "flavor" / "intervals" / "note2chord") — feeds the per-kind stats. */
    private val onChallengeComplete: (kind: String, score: Int, total: Int, durationMs: Long) -> Unit =
        { _, _, _, _ -> },
    /** Called once per wrongly-answered progression when a Progression Challenge ends. */
    private val onProgressionMistake: (progKey: String) -> Unit = { },
) {
    /** Per-kind challenge start time, for duration in the recorded stats. */
    private val kindChallengeStart = HashMap<String, Long>()
    private fun markChallengeStart(kind: String) { kindChallengeStart[kind] = System.currentTimeMillis() }
    private fun reportChallengeDone(kind: String, score: Int, total: Int) {
        val started = kindChallengeStart.remove(kind) ?: System.currentTimeMillis()
        onChallengeComplete(kind, score, total, System.currentTimeMillis() - started)
    }

    // ---- Voicing / variety options (apply to progression playback & generation) ----
    /** Use shell (jazz drop-2) voicings for ear-training chords. */
    var earShellVoicing by mutableStateOf(true)   // shell voicings (root+3rd+7th) default for now
    /** Mix everything: randomize chord-type level (triad/7th/extended) per bar AND
     *  randomize voicing (standard/shell) per chord. Overrides the single selections. */
    var earMixAll by mutableStateOf(false)

    /** Include the harmonic-minor progressions (major V / V7 → i cadences) in the minor
     *  generator pool + library. Default on. When off, minor uses only the natural-minor set. */
    var earHarmonicMinor by mutableStateOf(true)

    /** Voicing style for the next chord, honoring shell / mix settings. */
    private fun earStyle(): VoicingStyle = when {
        earMixAll -> if (rng.nextBoolean()) VoicingStyle.Shell else VoicingStyle.Standard
        earShellVoicing -> VoicingStyle.Shell
        else -> VoicingStyle.Standard
    }
    // ---------- Progression trainer ----------

    var progSubMode by mutableStateOf(EarSubMode.Progression)

    /** Practice (free play) vs Challenge (scored rounds) — applies to the active tab. */
    var earMode by mutableStateOf(EarMode.Challenge)   // opens in Progression Challenge by default

    /** Switch tabs: reset to Practice and stop any audio so modes don't bleed together. */
    fun switchTab(sub: EarSubMode) {
        progSubMode = sub
        earMode = EarMode.Practice
        stopLoop()
        stopDrill()
    }

    /** Whether the user wants Major mode in the rotation. */
    var includeMajor by mutableStateOf(true)
    /** Whether the user wants Minor mode in the rotation. Default off: the app
     *  opens in major-triads-only for the simplest starting point. */
    var includeMinor by mutableStateOf(true)
    /** Triads vs Sevenths vs Extended. Default Triads (simplest). */
    var chordTypeLevel by mutableStateOf(ChordTypeLevel.Sevenths)
    /** Null = random key each round. Non-null = always use this key. */
    var fixedKey by mutableStateOf<PitchClass?>(null)
    /** BPM for progression loop. */
    var progBpm by mutableStateOf(140)

    /** Current progression state. */
    var progKey by mutableStateOf(PitchClass.C)
    var progMode by mutableStateOf(TrainingMode.Major)
    /** Net semitones the current progression has been transposed from its generated
     *  key (0 = as generated). Reset when a fresh progression/question is drawn. */
    var progTranspose by mutableStateOf(0)
        private set
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

    /** Voice-led shapes for the CURRENT progression, one per bar — built once (per
     *  progression/voicing/tuning) so the loop AND every slot button sound the identical
     *  voicing. See [ensureProgShapes]. */
    private var progShapes: List<app.guitar.theory.ChordShape?> = emptyList()
    private var progShapesSig: String = ""

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

    /** Zoom/pan camera for the progression fretboard panel — hoisted here so the
     *  view keeps its zoom across the show/hide toggle (and screen re-entry). */
    val progFretboardCamera = FretboardCamera()

    /** Boost the ROOT of each ear-training chord: it is played separately and louder
     *  than the other voices, so the bass note stands out of the strum. */
    var earBoostTonic by mutableStateOf(false)

    // Always-on subtle bass-string emphasis for ear-training chords (independent of
    // the earBoostTonic root-emphasis toggle): the lowest note of a voicing is ~40%
    // louder, tapering to none at the top, so chords sit on a fuller low end.
    private val EAR_BASS_BOOST = 0.4f

    /** Thin a guitar voicing for EAR TRAINING playback: keep only the LOWEST occurrence
     *  of each pitch class, so a 6-string barre grip collapses to its 3–4 distinct chord
     *  tones instead of sounding all doubled strings at once (a cacophony by ear). */
    /** Ear-training voicing candidates: NEVER a full 6-string grip (its doubled low
     *  strings scramble into mud by ear), and prefer voicings whose bass sits at or
     *  above ~A2 (MIDI 45). Each filter falls back rather than emptying the list. */
    private fun earShapes(shapes: List<app.guitar.theory.ChordShape>): List<app.guitar.theory.ChordShape> {
        val compact = shapes.filter { sh -> sh.notes.count { it != null } <= 5 }.ifEmpty { shapes }
        return compact.filter { sh ->
            (sh.notes.mapNotNull { it?.midi?.value }.minOrNull() ?: 0) >= 45
        }.ifEmpty { compact }
    }

    private fun earMidis(shape: app.guitar.theory.ChordShape): List<Int> {
        val midis = shape.notes.mapNotNull { it?.midi?.value }.sorted()
        val seen = HashSet<Int>()
        return midis.filter { seen.add(((it % 12) + 12) % 12) }
    }

    /** Sound [midis] as an ear-training chord. When [earBoostTonic] is on, the lowest
     *  note matching [rootPc] is played separately and louder (and left out of the
     *  strummed chord) so the root is clearly audible above the other strings. */
    private fun playEarChord(midis: List<Int>, rootPc: Int, sustainMillis: Int) {
        if (midis.isEmpty()) return
        if (earBoostTonic) {
            val tonic = midis.filter { ((it % 12) + 12) % 12 == rootPc }.minOrNull()
            if (tonic != null) {
                audio.playNote(tonic, sustainMillis, Timbre.Clarity.copy(amplitude = 0.95))
                val rest = midis.filter { it != tonic }
                if (rest.isNotEmpty()) {
                    audio.playChord(rest, strumDelayMillis = strumProvider(),
                        sustainMillis = sustainMillis, timbre = Timbre.Clarity, bassBoost = EAR_BASS_BOOST)
                }
                return
            }
        }
        audio.playChord(midis, strumDelayMillis = strumProvider(),
            sustainMillis = sustainMillis, timbre = Timbre.Clarity, bassBoost = EAR_BASS_BOOST)
    }

    /** True once the user has clicked "Generate progression" for the first time
     *  this session. We use this to gate the initial reveal cards behind an
     *  explicit user action so the very first progression honors the
     *  major/minor/sevenths settings the user has just chosen. */
    var hasGenerated by mutableStateOf(false)
        private set

    // ---------- ← Previous history (practice only) ----------
    // Each "Next" pushes the outgoing progression, so ← Prev can bring it back
    // (reveals reset — it returns as a fresh quiz). Challenge questions are NOT
    // recorded: the challenge has its own per-question Prev.

    /** Snapshot of a generated diatonic practice progression. */
    private data class DiatonicSnapshot(
        val prog: Progression,
        val key: PitchClass,
        val mode: TrainingMode,
        val resolved: List<ResolvedChord>,
    )

    private val diatonicHistory = ArrayDeque<DiatonicSnapshot>()
    var canGoPrevProgression by mutableStateOf(false)
        private set

    /** Restore the previously generated diatonic practice progression. */
    fun previousProgression() {
        val snap = diatonicHistory.removeLastOrNull() ?: return
        canGoPrevProgression = diatonicHistory.isNotEmpty()
        progKey = snap.key
        progMode = snap.mode
        progProgression = snap.prog
        progResolved = snap.resolved
        progTranspose = 0
        progBarRevealed = emptySet()
        keyRevealed = false
        modeRevealed = false
        currentBar = 0
        if (isLooping) { stopLoop(); startLoop() }
    }

    fun nextProgression() {
        // Record the outgoing progression for ← Prev (practice only; cap the stack).
        if (earMode == EarMode.Practice) {
            progProgression?.let { cur ->
                diatonicHistory.addLast(DiatonicSnapshot(cur, progKey, progMode, progResolved))
                while (diatonicHistory.size > 20) diatonicHistory.removeFirst()
                canGoPrevProgression = true
            }
        }
        // Pick a mode honoring the include flags
        val candidates = buildList {
            if (includeMajor) add(TrainingMode.Major)
            if (includeMinor) add(TrainingMode.Minor)
        }.ifEmpty { listOf(TrainingMode.Major) }
        // The I→iii drill is major-only; otherwise honor the Major/Minor include flags.
        val mode = if (iiiFocusMode) TrainingMode.Major else candidates[rng.nextInt(candidates.size)]
        val key = fixedKey ?: PitchClass(rng.nextInt(12))
        val prog = EarTraining.randomProgression(mode, rng, focusIiii = iiiFocusMode, includeHarmonicMinor = earHarmonicMinor)
        progKey = key
        progMode = mode
        progProgression = prog
        progResolved = resolveCurrent(prog, key)
        progTranspose = 0
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

    /**
     * Transpose the current Progressions-practice progression by [n] semitones:
     * shift the key and every chord's root, keeping the SAME chords/qualities and
     * Roman degrees (so it isn't re-randomized). Works for the diatonic generator
     * and the advanced (non-diatonic) library alike, since both feed [progResolved].
     */
    fun transposeProgression(n: Int) {
        if (progResolved.isEmpty()) return
        progTranspose += n
        progKey = PitchClass.of(progKey.value + n)
        progResolved = progResolved.map { rc ->
            val parsed = ChordLibrary.parse(rc.symbol) ?: return@map rc
            val (root, q) = parsed
            val newRoot = PitchClass.of(root.value + n)
            ResolvedChord(NoteSpeller.spell(newRoot) + q.symbol, rc.romanLabel, newRoot)
        }
        if (isLooping) { stopLoop(); startLoop() }
    }

    /** Rebuild the per-bar voice-led shapes only when the progression / voicing / tuning
     *  changed, so the loop and slot buttons keep sounding the identical cached voicings. */
    private fun ensureProgShapes() {
        val t = tuningProvider()
        val sig = progResolved.joinToString(",") { it.symbol } +
            "|" + (if (earMixAll) "mix" else if (earShellVoicing) "shell" else "std") +
            "|" + t.openStrings.joinToString(",") { it.midi.value.toString() }
        if (sig != progShapesSig) { buildProgShapes(); progShapesSig = sig }
    }

    private fun buildProgShapes() {
        val style = earStyle()
        val tuning = tuningProvider()
        var prev: app.guitar.theory.ChordShape? = null
        progShapes = progResolved.map { rc ->
            val parsed = ChordLibrary.parse(rc.symbol) ?: return@map null
            val (root, q) = parsed
            val shapes = earShapes(ChordShapeGenerator(style = style).shapesFor(root, q, tuning, frets = DISPLAY_FRETS))
            if (shapes.isEmpty()) return@map null
            val shape = if (prev == null) shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E } ?: shapes.first()
                        else shapes[app.guitar.theory.VoiceLeading.pickMinMovement(prev!!, shapes)]
            prev = shape
            shape
        }
    }

    /** Sound bar [idx] using its precomputed voice-led shape (block-tone fallback), shared
     *  by the loop and every slot button so they always sound the same voicing. */
    private fun soundBar(idx: Int, sustain: Int) {
        val rc = progResolved.getOrNull(idx) ?: return
        val parsed = ChordLibrary.parse(rc.symbol)
        val rootPc = parsed?.first?.value ?: 0
        val shape = progShapes.getOrNull(idx)
        currentPlayingShape = shape
        if (shape != null) {
            lastShownShape = shape
            playEarChord(earMidis(shape), rootPc, sustain)
        } else if (parsed != null) {
            val rootMidi = 52 + rootPc
            playEarChord(parsed.second.intervals.map { rootMidi + it.semitones }, rootPc, sustain)
        }
    }

    /** Play the [idx]-th chord once — same voice-led shape the loop uses for that bar. */
    fun playBarOnce(idx: Int) {
        ensureProgShapes()
        currentBar = idx
        soundBar(idx, sustainProvider())
    }

    /** Famous songs built on the CURRENT progression (from the library data), for the
     *  "Songs" popup available in both Practice and Challenge. Best-effort across
     *  generators: diatonic by degrees, advanced by name, circle by matching window. */
    fun currentProgressionSongs(): List<app.guitar.theory.SongExample> = when {
        advancedMode -> advProg?.let { app.guitar.theory.ProgressionSongs.forAdvanced(it.name) } ?: emptyList()
        circleMode -> advProg?.let { np ->
            app.guitar.theory.EarTraining.CIRCLE_WINDOWS.firstOrNull { it.romanLine == np.romanLine }
                ?.let { app.guitar.theory.ProgressionSongs.forCircleWindow(it.id) }
        } ?: emptyList()
        else -> progProgression?.let {
            if (it.dominantBars.isNotEmpty()) app.guitar.theory.ProgressionSongs.forHarmonicMinor(it)
            else app.guitar.theory.ProgressionSongs.forDiatonic(it)
        } ?: emptyList()
    }

    /** PDF-imported EXTRA songs for the current progression, shown behind "Show more"
     *  in the Songs popup. Diatonic only (advanced/circle have no imported extras). */
    fun currentProgressionImportedSongs(): List<app.guitar.theory.SongExample> = when {
        advancedMode || circleMode -> emptyList()
        else -> progProgression?.let { app.guitar.theory.ProgressionSongs.importedForDiatonic(it) } ?: emptyList()
    }

    // ---------- Library preview player ----------
    // A SEPARATE, self-contained looper for the progression-library dialog. It never
    // touches the quiz looper's state (progResolved / currentBar / currentPlayingShape /

    /** Which library row is currently previewing (its key, e.g. "maj:1,5,6,4"), or null. */
    var libPlayingId by mutableStateOf<String?>(null)
        private set
    /** Index of the bar the library preview is currently sounding. */
    var libBar by mutableStateOf(0)
        private set
    /** The shape the library preview is currently sounding — drives the follow-along
     *  fretboard in the expanded library row. Null for block-voiced (unvoiceable) chords. */
    var libShape by mutableStateOf<app.guitar.theory.ChordShape?>(null)
        private set

    private var libLoopJob: Job? = null
    private var libPrevShape: app.guitar.theory.ChordShape? = null

    /** Loop [chords] as a library preview tagged [id] (fixed key baked into the passed-in
     *  chords). Voice-leads like the quiz looper, with a block-tone fallback for exotic
     *  chords, but on its own independent state. Loops until [libraryStop]. Calling this
     *  while another row plays cleanly switches to the new row. */
    fun libraryPlay(id: String, chords: List<ResolvedChord>) {
        libraryStop()
        if (chords.isEmpty()) return
        libPlayingId = id
        libPrevShape = null
        libBar = 0
        libLoopJob = scope.launch {
            val beatMs = (60_000L / progBpm.coerceAtLeast(10))
            val barMs = beatMs * 4
            while (libPlayingId == id) {
                for (i in chords.indices) {
                    if (libPlayingId != id) break
                    libBar = i
                    audio.cutReverb()   // clear the previous chord's reverb tail first
                    libPlayChordOnce(chords[i].symbol, barMs)
                    delay(barMs)
                }
            }
        }
    }

    /** Stop the library preview loop and silence its notes. */
    fun libraryStop() {
        libPlayingId = null
        libLoopJob?.cancel()
        libLoopJob = null
        libShape = null
        libPrevShape = null
        audio.stop()
    }

    private fun libPlayChordOnce(symbol: String, barMs: Long) {
        val parsed = ChordLibrary.parse(symbol) ?: return
        val (root, q) = parsed
        val tuning = tuningProvider()
        val shapes = earShapes(ChordShapeGenerator(style = earStyle()).shapesFor(root, q, tuning, frets = DISPLAY_FRETS))
        val sustain = (barMs * 0.9).toInt().coerceAtLeast(200)
        if (shapes.isEmpty()) {
            // Exotic chord with no playable guitar voicing: sound the chord tones as a block.
            libShape = null
            val rootMidi = 52 + root.value
            val midis = q.intervals.map { rootMidi + it.semitones }
            playEarChord(midis, root.value, sustain)
            return
        }
        val shape = if (libPrevShape == null) {
            shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E } ?: shapes.first()
        } else {
            shapes[app.guitar.theory.VoiceLeading.pickMinMovement(libPrevShape!!, shapes)]
        }
        libPrevShape = shape
        libShape = shape
        // Deduped voicing (never a full barre) + optional root boost.
        playEarChord(earMidis(shape), root.value, sustain)
    }

    private var cadenceJob: Job? = null

    /** Cadence label for the progression key. Plain DIGITS ("1–5–1") on purpose:
     *  the challenge is to identify major vs minor by ear, so the reference button
     *  must not reveal the mode (Roman case would).  */
    fun progCadenceLabel(): String = "1–5–1"

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
        ensureProgShapes()
        isLooping = true
        loopJob = scope.launch {
            val beatMs = (60_000L / progBpm.coerceAtLeast(10))
            // One chord per bar; 4 beats per bar.
            val barMs = beatMs * 4
            val sustain = (barMs * 0.9).toInt().coerceAtLeast(200)
            while (isLooping) {
                for (i in progResolved.indices) {
                    if (!isLooping) break
                    currentBar = i
                    audio.cutReverb()   // don't let the previous chord's reverb ring over this one
                    soundBar(i, sustain)
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

    /** Play the [idx]-th chord once — same voice-led shape the loop uses for that bar. */
    fun playProgChordDirect(idx: Int) {
        playBarOnce(idx)
    }

    // ---------- Mistake Drill ----------
    // A self-contained looper (never touches the quiz looper) repeating ONE missed
    // progression with per-bar voicing control. Default per bar is the voice-led
    // SHELL shape (identical to the quiz loop); an override forces a close-voiced
    // inversion so the 5th's position (above/below the root) is controllable —
    // shell drops the 5th, so the override switches to a fuller voicing.

    var drillKey by mutableStateOf<String?>(null)
        private set
    var drillProg by mutableStateOf<Progression?>(null)
        private set
    private var drillResolved: List<ResolvedChord> = emptyList()
    /** Per-bar inversion override: null = auto (voice-led shell), 0..n-1 = forced. */
    var drillInversions by mutableStateOf<List<Int?>>(emptyList())
        private set
    private var drillMidis: List<List<Int>> = emptyList()
    var drillBar by mutableStateOf(-1)
        private set
    private var drillJob: Job? = null

    val isDrilling: Boolean get() = drillKey != null

    /** Question indices already counted as a mistake this challenge (dedupe so
     *  stepping back/forward can't re-count the same progression). */
    private val challengeMistakesRecorded = HashSet<Int>()

    /** Count the CURRENT question as a miss the moment the user advances past it
     *  (press Next), if any bar was wrong — once per question. Accumulates through
     *  the challenge instead of all-at-the-end (early exits still count answered). */
    private fun recordCurrentMistakeIfWrong() {
        if (specialProgMode) return
        val i = challengeIndex
        if (i < 0 || i >= challengeTotal) return
        if (i in challengeMistakesRecorded) return
        if (challengeAnswers.getOrNull(i) == false) {
            challengeMistakesRecorded.add(i)
            challengeLog.getOrNull(i)?.let { onProgressionMistake(EarTraining.progressionKey(it.prog)) }
        }
    }

    /** Start (or restart) looping the missed progression identified by [progKey]. */
    fun startDrill(progKey: String) {
        val prog = EarTraining.progressionFromKey(progKey) ?: return
        stopLoop()
        stopDrill()
        val tonic = if (prog.mode == TrainingMode.Major) PitchClass.of(0) else PitchClass.of(9)
        drillKey = progKey
        drillProg = prog
        drillResolved = EarTraining.resolveProgression(prog, tonic, chordTypeLevel, rng)
        drillInversions = drillResolved.map { null }
        rebuildDrillVoicing()
        drillBar = 0
        drillJob = scope.launch {
            while (isDrilling && drillKey == progKey) {
                for (i in drillResolved.indices) {
                    if (!isDrilling || drillKey != progKey) break
                    val barMs = (60_000L / progBpm.coerceAtLeast(10)) * 4   // read live so BPM edits apply next bar
                    val sustain = (barMs * 0.9).toInt().coerceAtLeast(200)
                    drillBar = i
                    audio.cutReverb()
                    val rootPc = ChordLibrary.parse(drillResolved[i].symbol)?.first?.value ?: 0
                    val midis = drillMidis.getOrNull(i) ?: emptyList()
                    if (midis.isNotEmpty()) playEarChord(midis, rootPc, sustain)
                    delay(barMs)
                }
            }
        }
    }

    fun stopDrill() {
        if (drillKey == null) return
        drillKey = null
        drillBar = -1
        drillJob?.cancel()
        drillJob = null
        audio.stop()
    }

    /** Number of inversions of bar [i]'s chord (root..n-1); 0 if unresolvable. */
    fun drillInversionCount(i: Int): Int {
        val rc = drillResolved.getOrNull(i) ?: return 0
        return ChordLibrary.parse(rc.symbol)?.let { app.guitar.theory.Inversions.count(it.second) } ?: 0
    }

    /** Cycle bar [i]'s override: auto → root → 1st → … → last → auto. */
    fun cycleDrillInversion(i: Int) {
        if (i !in drillInversions.indices) return
        val n = drillInversionCount(i)
        val cur = drillInversions[i]
        val next = if (cur == null) 0 else if (cur + 1 >= n) null else cur + 1
        drillInversions = drillInversions.toMutableList().also { it[i] = next }
        rebuildDrillVoicing()
    }

    /** Force every bar to the same inversion (2 = 5th in bass), or null = auto. */
    fun setAllDrillInversions(inv: Int?) {
        drillInversions = drillInversions.indices.map { i ->
            if (inv == null) null else minOf(inv, maxOf(drillInversionCount(i) - 1, 0))
        }
        rebuildDrillVoicing()
    }

    private fun rebuildDrillVoicing() {
        val style = earStyle()
        val tuning = tuningProvider()
        var prev: app.guitar.theory.ChordShape? = null
        drillMidis = drillResolved.mapIndexed { i, rc ->
            val parsed = ChordLibrary.parse(rc.symbol) ?: return@mapIndexed emptyList()
            val (root, q) = parsed
            val inv = drillInversions.getOrNull(i)
            if (inv != null) return@mapIndexed app.guitar.theory.Inversions.midis(48 + root.value, q, inv)
            val shapes = earShapes(ChordShapeGenerator(style = style).shapesFor(root, q, tuning, frets = DISPLAY_FRETS))
            if (shapes.isEmpty()) { val rm = 52 + root.value; return@mapIndexed q.intervals.map { rm + it.semitones } }
            val shape = if (prev == null) shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E } ?: shapes.first()
                        else shapes[app.guitar.theory.VoiceLeading.pickMinMovement(prev!!, shapes)]
            prev = shape
            earMidis(shape)
        }
    }

    // ---------- Note2Chord trainer ----------

    var n2cChallenge by mutableStateOf<app.guitar.theory.N2cChallenge?>(null)
    var n2cRevealed by mutableStateOf(false)
    var n2cPlaying by mutableStateOf(false)
    /** Show the current Note2Chord triad on the fretboard (#3). */
    var n2cShowFretboard by mutableStateOf(false)

    private var n2cJob: Job? = null

    // Drawn-challenge history for Previous/Next (#3).
    private val n2cHistory = ArrayList<app.guitar.theory.N2cChallenge>()
    private var n2cHistIndex by mutableStateOf(-1)
    val n2cHasPrev: Boolean get() = n2cHistIndex > 0
    val n2cHasNext: Boolean get() = n2cHistIndex in 0 until n2cHistory.lastIndex

    fun nextN2cChallenge() {
        val c = app.guitar.theory.N2cChallenge.random(rng)
        n2cChallenge = c
        n2cRevealed = false
        if (n2cHistIndex < n2cHistory.lastIndex) {
            while (n2cHistory.lastIndex > n2cHistIndex) n2cHistory.removeAt(n2cHistory.lastIndex)
        }
        n2cHistory.add(c)
        if (n2cHistory.size > 32) n2cHistory.removeAt(0)
        n2cHistIndex = n2cHistory.lastIndex
    }

    fun n2cPrev() {
        if (!n2cHasPrev) return
        n2cHistIndex--
        n2cChallenge = n2cHistory[n2cHistIndex]; n2cRevealed = false
    }
    fun n2cNext() {
        if (!n2cHasNext) return
        n2cHistIndex++
        n2cChallenge = n2cHistory[n2cHistIndex]; n2cRevealed = false
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
        markChallengeStart("note2chord")
        nextN2cChallenge(); playN2c()
    }
    fun guessN2c(label: String) {
        if (!n2cChActive || n2cChGuess != null) return
        n2cChGuess = label
        if (label == n2cChallenge?.answerLabel) n2cChScore++
    }
    fun advanceN2cChallenge() {
        if (!n2cChActive) return
        if (n2cChIndex >= n2cChallengeTotal - 1) {
            n2cChIndex = n2cChallengeTotal
            reportChallengeDone("note2chord", n2cChScore, n2cChallengeTotal)
            return
        }
        n2cChIndex++; n2cChGuess = null; nextN2cChallenge(); playN2c()
    }
    fun exitN2cChallenge() { n2cChActive = false; n2cChIndex = 0; n2cChGuess = null }

    // ---------- Progression Challenge ([challengeTotal]-question quiz) ----------

    /** Length of one challenge session. */
    val challengeTotal: Int = 10

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
        challengeMistakesRecorded.clear()
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
        recordCurrentMistakeIfWrong()   // count THIS progression now (on Next)
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
    /** Per-bar flag: the user answered the HARMONIC-MINOR dominant (major V / V7) for
     *  this bar, not the natural-minor `v`. Only settable from the minor keyboard's
     *  dedicated "V7" key (shown when the harmonic-minor toggle is on). Scoring requires
     *  it to match whether the bar actually is a [Progression.dominantBars] bar. */
    var challengeGuessDominant by mutableStateOf<List<Boolean>>(List(4) { false })
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
        challengeGuessDominant = List(4) { false }
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
        var guessDom: List<Boolean>,
    )

    private val challengeLog = ArrayList<QState>()

    private fun freshChallengeQuestion(): QState {
        val candidates = buildList {
            if (includeMajor) add(TrainingMode.Major)
            if (includeMinor) add(TrainingMode.Minor)
        }.ifEmpty { listOf(TrainingMode.Major) }
        // The I→iii drill is major-only; otherwise honor the Major/Minor include flags.
        val mode = if (iiiFocusMode) TrainingMode.Major else candidates[rng.nextInt(candidates.size)]
        val key = fixedKey ?: PitchClass(rng.nextInt(12))
        val prog = EarTraining.randomProgression(mode, rng, focusIiii = iiiFocusMode, includeHarmonicMinor = earHarmonicMinor)
        return QState(key, mode, prog, resolveCurrent(prog, key),
            List(4) { null }, List(4) { null }, List(4) { null }, List(4) { false })
    }

    /** Make [q] the live question (prog* + guesses), resetting reveals. */
    private fun applyChallengeQuestion(q: QState) {
        progKey = q.key
        progMode = q.mode
        progProgression = q.prog
        progResolved = q.resolved
        progTranspose = 0
        challengeGuessDegree = q.guessDeg
        challengeGuessExt = q.guessExt
        challengeGuessLabel = q.guessLabel
        challengeGuessDominant = q.guessDom
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
            it.guessDom = challengeGuessDominant
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

    /** Correct extension label for bar [i] (suffix of its Roman label), or "" if none.
     *  A harmonic-minor dominant bar strips the "V" prefix (its suffix "" / "7" / "9"
     *  matches the natural `v`'s, so degree-5 answers score the same either way). */
    fun correctExtLabel(i: Int): String {
        val deg = progProgression?.degrees?.getOrNull(i) ?: return ""
        val dominant = progMode == TrainingMode.Minor && progProgression?.dominantBars?.contains(i) == true
        val info = if (dominant) EarTraining.MINOR_DOMINANT else degreesMap()[deg] ?: return ""
        return progResolved.getOrNull(i)?.romanLabel?.removePrefix(info.roman) ?: ""
    }

    /** null = bar not fully answered yet; true/false = correct/incorrect. */
    fun challengeBarCorrect(i: Int): Boolean? {
        val deg = progProgression?.degrees?.getOrNull(i) ?: return null
        val g = challengeGuessDegree.getOrNull(i) ?: return null
        if (challengeNeedsExt && challengeGuessExt.getOrNull(i) == null) return null
        // Degree must match AND the major-V/natural-v choice must match the bar: a
        // harmonic-dominant bar is only correct when answered with the "V7" key, and a
        // natural degree-5 bar only when answered with the plain "v" key.
        val barDominant = progMode == TrainingMode.Minor && progProgression?.dominantBars?.contains(i) == true
        val guessDominant = challengeGuessDominant.getOrNull(i) == true
        val degOk = g == deg && guessDominant == barDominant
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
            val info = map[pos]
            val roman = info?.roman ?: pos.toString()
            // In fixed-7ths mode the key IS the whole answer, so show the 7th on it
            // (ii7, V7, Imaj7, viiø7…). Extended/triad/mix keep the plain numeral.
            val label = if (challengeCombinedMode && info != null)
                EarTraining.romanLabel(info.roman, info.seventhQuality) else roman
            EarTraining.majorRelativeDegree(pos, mode) to label
        }
    }

    /**
     * Diatonic extension suffixes to offer for a keyboard key ([majorRelativeDegree])
     * in Extended mode, in the actual key's mode — so after picking a degree the
     * extension row shows only what's valid on THAT chord (e.g. IV→9/♯11/13,
     * ii→9/11, V→9/11/13). Mix mode keeps the full union (level varies per bar).
     */
    fun challengeExtOptionsForDegree(majorRelativeDegree: Int): List<String> {
        if (earMixAll || chordTypeLevel != ChordTypeLevel.Extended) return challengeExtOptions()
        val deg = EarTraining.degreeFromMajorRelative(majorRelativeDegree, progMode)
        val info = degreesMap()[deg] ?: return emptyList()
        return if (info.extendedOptions.isNotEmpty()) info.extendedOptions.map { it.second }.distinct()
        else listOf(EarTraining.romanLabel(info.roman, info.extendedQuality).removePrefix(info.roman))
    }

    fun toggleKeyboardShift() { keyboardMinor = !keyboardMinor }

    /** Show the dedicated harmonic-minor dominant ("V7") answer key: only on the minor
     *  keyboard row, and only when the harmonic-minor option is on. Lets the user mark a
     *  major V distinctly from the natural `v` (which the plain degree-5 key answers). */
    val harmonicDominantVisible: Boolean get() = keyboardMinor && earHarmonicMinor
    /** The relative-major degree the harmonic-dominant key answers as (minor degree 5). */
    val harmonicDominantMajDeg: Int get() = EarTraining.majorRelativeDegree(5, TrainingMode.Minor)
    /** Label for the harmonic-dominant answer key ("V7" in fixed-7ths mode, else "V"). */
    fun harmonicDominantLabel(): String = if (challengeCombinedMode) "V7" else "V"

    /**
     * Commit a keyboard answer for [bar]: [majorRelativeDegree] is the relative-major
     * degree the tapped key stands for (so a major-row and the equivalent minor-row
     * key produce the same answer). The degree is converted into the actual key's
     * mode for scoring, so identifying I–IV–V or its minor III–VI–VII both score.
     * [roman] is the tapped key's label in the user's chosen system (used to build
     * the square's display). [ext] is the chosen extension suffix when the level
     * needs one (ignored for triads; forced to the diatonic 7th in fixed-7ths mode).
     */
    fun guessChallengeKeyboard(bar: Int, majorRelativeDegree: Int, roman: String, ext: String?, dominant: Boolean = false) {
        if (!challengeActive || bar !in 0..3) return
        val deg = EarTraining.degreeFromMajorRelative(majorRelativeDegree, progMode)
        challengeGuessDegree = challengeGuessDegree.toMutableList().also { it[bar] = deg }
        challengeGuessDominant = challengeGuessDominant.toMutableList().also { it[bar] = dominant }
        // The harmonic dominant answers with MINOR_DOMINANT (major V) rather than the
        // natural `v`; its per-level suffix ("", "7", "9") is otherwise identical.
        val info = if (dominant) EarTraining.MINOR_DOMINANT else degreesMap()[deg]
        // label = what shows in the square; roman already carries the 7th in combined
        // mode (from keyboardKeys), so don't append the suffix again there.
        val label: String = when {
            challengeCombinedMode -> {
                val e = if (info != null)
                    EarTraining.romanLabel(info.roman, info.seventhQuality).removePrefix(info.roman) else ""
                challengeGuessExt = challengeGuessExt.toMutableList().also { it[bar] = e }
                roman
            }
            challengeNeedsExt -> {
                challengeGuessExt = challengeGuessExt.toMutableList().also { it[bar] = ext ?: "" }
                roman + (ext ?: "")
            }
            else -> roman
        }
        challengeGuessLabel = challengeGuessLabel.toMutableList().also { it[bar] = label }
        maybeAutoMarkChallenge()
    }

    /** Clear bar [bar]'s keyboard answer (empties its square). */
    fun clearChallengeBar(bar: Int) {
        if (bar !in 0..3) return
        challengeGuessDegree = challengeGuessDegree.toMutableList().also { it[bar] = null }
        challengeGuessExt = challengeGuessExt.toMutableList().also { it[bar] = null }
        challengeGuessLabel = challengeGuessLabel.toMutableList().also { it[bar] = null }
        challengeGuessDominant = challengeGuessDominant.toMutableList().also { it[bar] = false }
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
    /** Show the drawn flavor chord on the fretboard (#3). */
    var flavorShowFretboard by mutableStateOf(false)
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

    /**
     * Qualities to present as guess/audition options (#4): only flavors that are
     * diatonic in the current key/mode and enabled — never a flavor that doesn't fit
     * the key. If a degree is being guessed, narrow to the qualities diatonic for
     * THAT degree; otherwise show the union across the key. Preserves [flavorPalette]
     * order. Falls back to the full enabled set only if the diatonic set is empty.
     */
    fun flavorQualityOptions(forDegree: Int? = null): List<String> {
        val candidates = diatonicFlavorCandidates(flavorMode, flavorAllowed)
        val diatonic = (if (forDegree != null) candidates.filter { it.first == forDegree } else candidates)
            .map { it.second }.toSet()
        val ordered = flavorPalette.filter { it in diatonic }
        return ordered.ifEmpty { flavorPalette.filter { it in flavorAllowed } }
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
    /** Play a diatonic-degree reference for the challenge palette. Conforms to the
     *  progression's voicings: if [deg] appears in the current progression, play that
     *  bar's EXACT cached voice-led shape; otherwise voice the degree with the SAME
     *  ear-training style (shell/standard, earShapes/earMidis + bass boost) the
     *  progression uses — so a reference chord never sounds like a different voicing
     *  than the progression. Never mutates the fretboard (no answer reveal). */
    fun auditionProgDegree(deg: Int) {
        ensureProgShapes()
        val progIdx = progProgression?.degrees?.indexOf(deg) ?: -1
        val cached = if (progIdx >= 0) progShapes.getOrNull(progIdx) else null
        if (cached != null) {
            val rootPc = ChordLibrary.parse(progResolved[progIdx].symbol)?.first?.value ?: 0
            playEarChord(earMidis(cached), rootPc, sustainProvider())
            return
        }
        val level = if (earMixAll) ChordTypeLevel.Sevenths else chordTypeLevel
        val parsed = ChordLibrary.parse(EarTraining.resolve(deg, progKey, progMode, level, rng).symbol) ?: return
        val (root, q) = parsed
        val shapes = earShapes(ChordShapeGenerator(style = earStyle()).shapesFor(root, q, tuningProvider(), frets = DISPLAY_FRETS))
        val shape = shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E } ?: shapes.firstOrNull()
        if (shape != null) playEarChord(earMidis(shape), root.value, sustainProvider())
        else playEarChord(q.intervals.map { 52 + root.value + it.semitones }, root.value, sustainProvider())
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
        markChallengeStart("flavor")
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
        if (flavorChIndex >= flavorChallengeTotal - 1) {
            flavorChIndex = flavorChallengeTotal
            reportChallengeDone("flavor", flavorChScore, flavorChallengeTotal)
            return
        }
        flavorChIndex++; flavorChAnswered = false; newFlavorChallenge()
    }
    fun exitFlavorChallenge() { flavorChActive = false; flavorChIndex = 0; flavorChAnswered = false }

    // ---------- #2 Advanced (non-diatonic) progressions ----------

    /** Whether the Progression sub-mode is showing advanced named progressions. */
    var advancedMode by mutableStateOf(false)
    /** Whether the Progression sub-mode is drawing 4-chord circle-of-fifths windows.
     *  Reuses the advanced play/reveal flow; mutually exclusive with [advancedMode]. */
    var circleMode by mutableStateOf(false)
    /** Whether the Progression sub-mode is running the I→iii recognition drill. Unlike
     *  advanced/circle, this stays in the DIATONIC multiple-choice flow (best for ear
     *  recognition) — it just swaps the draw pool to [EarTraining.III_FOCUS_PROGRESSIONS]. */
    var iiiFocusMode by mutableStateOf(false)
    /** True when a "special" generator (advanced or circle) is active — both use the
     *  advanced-style self-marked views. The I→iii drill is NOT special (diatonic view). */
    val specialProgMode: Boolean get() = advancedMode || circleMode

    /** Which advanced pool [advancedMode] draws from: "advanced" (non-diatonic),
     *  "sus" (suspended chords), or "advanced2" (maj7 / min9 / modal colours). */
    var advCategory by mutableStateOf("advanced")
        private set

    /** Enter an advanced category (sets [advancedMode]); [cat] picks the draw pool. */
    fun chooseAdvancedCategory(cat: String) {
        advCategory = cat; advancedMode = true; circleMode = false; iiiFocusMode = false; stopLoop()
    }

    fun chooseAdvancedMode(on: Boolean) { advancedMode = on; if (on) { advCategory = "advanced"; circleMode = false; iiiFocusMode = false }; stopLoop() }
    fun chooseCircleMode(on: Boolean) { circleMode = on; if (on) { advancedMode = false; iiiFocusMode = false }; stopLoop() }
    /** Enter/leave the I→iii drill; clears the special generators and returns to the
     *  diatonic view. */
    fun chooseIiiFocusMode(on: Boolean) {
        iiiFocusMode = on
        if (on) { advancedMode = false; circleMode = false }
        stopLoop()
    }

    /** The currently-drawn advanced progression (null until generated). */
    var advProg by mutableStateOf<app.guitar.theory.EarTraining.NamedProgression?>(null)
        private set
    /** Whether the advanced answer (name + Roman line + chords) is revealed. */
    var advRevealed by mutableStateOf(false)

    /** Draw a fresh advanced/circle progression (random named progression + key),
     *  load it into [progResolved] for the shared looper, and reset the reveal. */
    /** Snapshot of a generated advanced/circle practice progression, for ← Prev. */
    private data class AdvSnapshot(
        val np: app.guitar.theory.EarTraining.NamedProgression,
        val key: PitchClass,
        val resolved: List<ResolvedChord>,
    )

    private val advHistory = ArrayDeque<AdvSnapshot>()
    var canGoPrevAdvanced by mutableStateOf(false)
        private set

    /** Restore the previously generated advanced/circle practice progression. */
    fun previousAdvancedProgression() {
        val snap = advHistory.removeLastOrNull() ?: return
        canGoPrevAdvanced = advHistory.isNotEmpty()
        advProg = snap.np
        progKey = snap.key
        progMode = snap.np.tonicMode
        progProgression = null
        progResolved = snap.resolved
        progTranspose = 0
        advRevealed = false
        if (isLooping) { stopLoop(); startLoop() }
    }

    fun nextAdvancedProgression() {
        // Record the outgoing progression for ← Prev (practice only; cap the stack).
        if (earMode == EarMode.Practice) {
            advProg?.let { cur ->
                advHistory.addLast(AdvSnapshot(cur, progKey, progResolved))
                while (advHistory.size > 20) advHistory.removeFirst()
                canGoPrevAdvanced = true
            }
        }
        val np = when {
            circleMode -> EarTraining.randomCircleOfFifths(rng)
            advCategory == "sus" -> EarTraining.randomSus(rng)
            advCategory == "advanced2" -> EarTraining.randomAdvanced2(rng)
            else -> EarTraining.randomAdvanced(rng)
        }
        val key = fixedKey ?: PitchClass(rng.nextInt(12))
        advProg = np
        progKey = key
        progMode = np.tonicMode
        progProgression = null
        progResolved = np.resolve(key)
        progTranspose = 0
        advRevealed = false
        hasGenerated = true
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
    /** Show the current inversion's chord on the fretboard (#3). */
    var invShowFretboard by mutableStateOf(false)
    private var invJob: Job? = null

    // Drawn-chord history (root, quality, inversion) for Previous/Next (#3).
    private val invHistory = ArrayList<Triple<PitchClass, String, Int>>()
    private var invHistIndex by mutableStateOf(-1)
    val invHasPrev: Boolean get() = invHistIndex > 0
    val invHasNext: Boolean get() = invHistIndex in 0 until invHistory.lastIndex

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
        if (invHistIndex < invHistory.lastIndex) {
            while (invHistory.lastIndex > invHistIndex) invHistory.removeAt(invHistory.lastIndex)
        }
        invHistory.add(Triple(invRoot, invQuality, invInversion))
        if (invHistory.size > 32) invHistory.removeAt(0)
        invHistIndex = invHistory.lastIndex
        playInversion()
    }

    fun inversionPrev() {
        if (!invHasPrev) return
        invHistIndex--
        val (r, q, inv) = invHistory[invHistIndex]
        invRoot = r; invQuality = q; invInversion = inv; invRevealed = false; invGuess = null
        playInversion()
    }
    fun inversionNext() {
        if (!invHasNext) return
        invHistIndex++
        val (r, q, inv) = invHistory[invHistIndex]
        invRoot = r; invQuality = q; invInversion = inv; invRevealed = false; invGuess = null
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
        markChallengeStart("inversions")
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
        if (invChIndex >= invChallengeTotal - 1) {
            invChIndex = invChallengeTotal
            reportChallengeDone("inversions", invChScore, invChallengeTotal)
            return
        }
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
    /** Show the current aug/dim chord on the fretboard (#2). */
    var adShowFretboard by mutableStateOf(false)
    private var adJob: Job? = null

    // Drawn-chord history so Previous/Next revisit chords without re-randomizing (#1).
    private val adHistory = ArrayList<Pair<PitchClass, String>>()
    private var adHistIndex by mutableStateOf(-1)
    val adHasPrev: Boolean get() = adHistIndex > 0
    val adHasNext: Boolean get() = adHistIndex in 0 until adHistory.lastIndex

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
        // Append to history (dropping any forward entries if we'd branched off).
        if (adHistIndex < adHistory.lastIndex) {
            while (adHistory.lastIndex > adHistIndex) adHistory.removeAt(adHistory.lastIndex)
        }
        adHistory.add(adRoot to adQuality)
        if (adHistory.size > 32) adHistory.removeAt(0)
        adHistIndex = adHistory.lastIndex
        playAugDim()
    }

    /** Revisit the previous drawn chord (no re-randomize). Resets the guess/reveal. */
    fun augDimPrev() {
        if (!adHasPrev) return
        adHistIndex--
        val (r, q) = adHistory[adHistIndex]
        adRoot = r; adQuality = q; adRevealed = false; adGuess = null
        playAugDim()
    }
    fun augDimNext() {
        if (!adHasNext) return
        adHistIndex++
        val (r, q) = adHistory[adHistIndex]
        adRoot = r; adQuality = q; adRevealed = false; adGuess = null
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
        markChallengeStart("augdim")
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
        if (adChIndex >= augDimChallengeTotal - 1) {
            adChIndex = augDimChallengeTotal
            reportChallengeDone("augdim", adChScore, augDimChallengeTotal)
            return
        }
        adChIndex++; adChAnswered = false; newAugDim()
    }
    fun exitAugDimChallenge() { adChActive = false; adChIndex = 0 }

    // ---------- #6 Interval identification (ascending/descending) ----------

    val intervalChallengeTotal: Int = 10
    /** Major key the I–V–I reference + tonic are built from (transposable). */
    var intervalKey by mutableStateOf(PitchClass.C)
        private set
    /** Net semitones the interval key has been transposed from C (0 = C). */
    var intervalTransposeSteps by mutableStateOf(0)
        private set
    var intervalDirection by mutableStateOf(IntervalDirection.Ascending)
    /** Harmonic mode: sound tonic + target TOGETHER (else melodic: one after the other). */
    var intervalHarmonic by mutableStateOf(false)
    var intervalChActive by mutableStateOf(false)
        private set
    var intervalChIndex by mutableStateOf(0)
        private set
    var intervalChScore by mutableStateOf(0)
        private set
    var intervalChAnswered by mutableStateOf(false)
        private set
    /** Current question: interval size (semitones) and the direction it's played. */
    var intervalSemitones by mutableStateOf(0)
        private set
    var intervalAscending by mutableStateOf(true)
        private set
    var intervalGuess by mutableStateOf<Int?>(null)
    var intervalPlaying by mutableStateOf(false)
        private set
    private var intervalJob: Job? = null

    /** Tonic MIDI for the current key, anchored near C4 so intervals stay audible
     *  in both directions. */
    private fun intervalTonicMidi(): Int = 60 + ((intervalKey.value + 6) % 12 - 6)

    fun intervalTranspose(n: Int) {
        intervalKey = PitchClass(((intervalKey.value + n) % 12 + 12) % 12)
        intervalTransposeSteps += n
        if (intervalChActive) playIntervalTonicCadence()
    }

    /** Play I–V–I in the current major key to anchor the tonic. */
    fun playIntervalTonicCadence() {
        intervalJob?.cancel()
        intervalPlaying = true
        intervalJob = scope.launch {
            try {
                for (deg in listOf(1, 5, 1)) {
                    val root = EarTraining.degreeRoot(intervalKey, deg, TrainingMode.Major)
                    val q = EarTraining.MAJOR_DEGREES[deg]?.triadQuality ?: ""
                    playSymbolOnce(NoteSpeller.spell(root) + q, 600)
                    delay(650)
                }
            } finally { intervalPlaying = false }
        }
    }

    // ---------- Interval REFERENCE playback (Theory tab / song-refs dialog) ----------
    // Independent of the challenge's key and harmonic settings: a fixed C4 base so every
    // row sounds alike. Plays the leap melodically, then both notes together, because the
    // stacked version is what makes the interval's colour unmistakable.

    /** Which reference row is currently sounding (drives the ▶/■ button), or null. */
    var intervalPreviewId by mutableStateOf<String?>(null)
        private set
    private var intervalPreviewJob: Job? = null

    fun playIntervalPreview(id: String, semitones: Int, ascending: Boolean) {
        intervalPreviewJob?.cancel()
        intervalPreviewId = id
        intervalPreviewJob = scope.launch {
            try {
                val base = 60                                   // C4
                val target = if (ascending) base + semitones else base - semitones
                audio.playNote(base, durationMillis = sustainProvider())
                delay(700)
                audio.playNote(target, durationMillis = sustainProvider())
                delay(700)
                audio.playChord(listOf(base, target), strumDelayMillis = 0,
                    sustainMillis = sustainProvider(), timbre = Timbre.Clarity)
                delay(900)
            } finally {
                if (intervalPreviewId == id) intervalPreviewId = null
            }
        }
    }

    fun stopIntervalPreview() {
        intervalPreviewJob?.cancel()
        intervalPreviewId = null
    }

    /** Re-sound just the tonic note (the reference). */
    fun playIntervalTonic() {
        intervalJob?.cancel()
        intervalJob = scope.launch { audio.playNote(intervalTonicMidi(), durationMillis = sustainProvider()) }
    }

    /** Play the current interval question: tonic then target (melodic), or both
     *  together (harmonic mode). */
    fun playIntervalQuestion() {
        if (intervalPlaying) return
        intervalJob?.cancel()
        intervalPlaying = true
        intervalJob = scope.launch {
            try {
                val tonic = intervalTonicMidi()
                val target = IntervalTrainer.targetMidi(tonic, intervalSemitones, intervalAscending)
                if (intervalHarmonic) {
                    audio.playChord(listOf(tonic, target), strumDelayMillis = 0,
                        sustainMillis = sustainProvider(), timbre = Timbre.Clarity)
                } else {
                    audio.playNote(tonic, durationMillis = sustainProvider())
                    delay(700)
                    audio.playNote(target, durationMillis = sustainProvider())
                }
            } finally { intervalPlaying = false }
        }
    }

    private fun drawIntervalQuestion() {
        intervalSemitones = rng.nextInt(IntervalTrainer.INTERVALS.size)   // 0..12
        intervalAscending = when (intervalDirection) {
            IntervalDirection.Ascending -> true
            IntervalDirection.Descending -> false
            IntervalDirection.Mixed -> rng.nextBoolean()
        }
        intervalGuess = null
        intervalChAnswered = false
    }

    fun startIntervalChallenge() {
        intervalChActive = true; intervalChIndex = 0; intervalChScore = 0
        markChallengeStart("intervals")
        drawIntervalQuestion()
        // Anchor the key, then sound the first interval after the cadence.
        intervalJob?.cancel()
        intervalPlaying = true
        intervalJob = scope.launch {
            try {
                for (deg in listOf(1, 5, 1)) {
                    val root = EarTraining.degreeRoot(intervalKey, deg, TrainingMode.Major)
                    val q = EarTraining.MAJOR_DEGREES[deg]?.triadQuality ?: ""
                    playSymbolOnce(NoteSpeller.spell(root) + q, 600)
                    delay(650)
                }
                delay(300)
                val tonic = intervalTonicMidi()
                val target = IntervalTrainer.targetMidi(tonic, intervalSemitones, intervalAscending)
                if (intervalHarmonic) {
                    audio.playChord(listOf(tonic, target), strumDelayMillis = 0,
                        sustainMillis = sustainProvider(), timbre = Timbre.Clarity)
                } else {
                    audio.playNote(tonic, durationMillis = sustainProvider())
                    delay(700)
                    audio.playNote(target, durationMillis = sustainProvider())
                }
            } finally { intervalPlaying = false }
        }
    }

    fun submitIntervalGuess() {
        if (!intervalChActive || intervalChAnswered) return
        val g = intervalGuess ?: return
        intervalChAnswered = true
        if (g == intervalSemitones) intervalChScore++
    }

    fun advanceIntervalChallenge() {
        if (!intervalChActive) return
        if (intervalChIndex >= intervalChallengeTotal - 1) {
            intervalChIndex = intervalChallengeTotal
            reportChallengeDone("intervals", intervalChScore, intervalChallengeTotal)
            return
        }
        intervalChIndex++
        drawIntervalQuestion()
        playIntervalQuestion()
    }

    fun exitIntervalChallenge() { intervalChActive = false; intervalChIndex = 0; intervalChAnswered = false }

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
        stopDrill()
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
        intervalJob?.cancel()
        intervalJob = null
    }
}

enum class EarSubMode { Progression, Note2Chord, Flavor, Inversions, AugDim, Intervals, Drill, Workout }

/** Within any tab: free Practice or scored Challenge rounds. */
enum class EarMode { Practice, Challenge }

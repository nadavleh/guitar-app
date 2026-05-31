package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
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
) {
    // ---------- Progression trainer ----------

    var progSubMode by mutableStateOf(EarSubMode.Progression)

    /** Whether the user wants Major mode in the rotation. */
    var includeMajor by mutableStateOf(true)
    /** Whether the user wants Minor mode in the rotation. */
    var includeMinor by mutableStateOf(true)
    /** Triads vs Sevenths vs Extended. */
    var chordTypeLevel by mutableStateOf(ChordTypeLevel.Sevenths)
    /** Null = random key each round. Non-null = always use this key. */
    var fixedKey by mutableStateOf<PitchClass?>(null)
    /** BPM for progression loop. */
    var progBpm by mutableStateOf(80)

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

    private var loopJob: Job? = null
    private val rng = Random.Default

    /** Tracks the last voicing played so the next chord can be picked by voice-leading.
     *  Persists across loop iterations so the wrap (bar 4 → bar 1) also flows. */
    private var prevPlayedShape: app.guitar.theory.ChordShape? = null

    /** Live broadcast: the shape currently being played by the progression looper.
     *  Compose can observe this to display the chord on the fretboard. */
    var currentPlayingShape by mutableStateOf<app.guitar.theory.ChordShape?>(null)
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
        progResolved = EarTraining.resolveProgression(prog, key, chordTypeLevel)
        // Hide all reveals for the new round
        progBarRevealed = emptySet()
        keyRevealed = false
        modeRevealed = false
        currentBar = 0
        // If we're currently playing, restart cleanly so the new progression begins immediately
        if (isLooping) {
            stopLoop()
            startLoop()
        }
    }

    fun toggleBarReveal(idx: Int) {
        progBarRevealed = if (idx in progBarRevealed) progBarRevealed - idx else progBarRevealed + idx
    }

    fun toggleKeyReveal() { keyRevealed = !keyRevealed }
    fun toggleModeReveal() { modeRevealed = !modeRevealed }

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
        val shapes = ChordShapeGenerator().shapesFor(root, q, tuning, frets = DISPLAY_FRETS)
        if (shapes.isEmpty()) return
        val shape = if (prevPlayedShape == null) {
            shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E } ?: shapes.first()
        } else {
            shapes[app.guitar.theory.VoiceLeading.pickMinMovement(prevPlayedShape!!, shapes)]
        }
        prevPlayedShape = shape
        currentPlayingShape = shape
        val midis = shape.notes.mapNotNull { it?.midi?.value }
        if (midis.isEmpty()) return
        audio.playChord(midis,
            strumDelayMillis = 25,
            sustainMillis = (barMs * 0.9).toInt().coerceAtLeast(200))
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

    fun release() {
        stopLoop()
        n2cJob?.cancel()
        n2cJob = null
    }
}

enum class EarSubMode { Progression, Note2Chord }

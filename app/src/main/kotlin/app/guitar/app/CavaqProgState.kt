package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.audio.Timbre
import app.guitar.theory.CavaqSequence
import app.guitar.theory.CavaqSequences
import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordShape
import app.guitar.theory.ChordShapeGenerator
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.ResolvedChord
import app.guitar.theory.Tuning
import app.guitar.theory.VoiceLeading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State + looper for the cavaquinho **Progressions** screen. Plays a functional
 * [CavaqSequence] (e.g. the quadradinho I VI7 ii V7) in a chosen key on a bar loop,
 * and exposes voice-led chord shapes across the neck: the FIRST chord's voicing is
 * picked by [positionIndex] (a neck-region scroller), and every later chord is the
 * least-motion voicing from the previous one ([VoiceLeading.pickMinMovement]) — so
 * scrolling the position moves the whole sequence up/down the neck coherently.
 */
@Stable
class CavaqProgState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
    private val tuningProvider: () -> Tuning,
    private val sustainProvider: () -> Int,
    private val strumProvider: () -> Int,
    private val timbreProvider: () -> Timbre,
) {
    var sequenceId by mutableStateOf(CavaqSequences.ALL.first().id)
        private set
    /** Tonic the sequence is transposed to (starts in C). */
    var key by mutableStateOf(PitchClass.C)
        private set
    /** Net semitones transposed from C (for the counter display). */
    var transpose by mutableStateOf(0)
        private set
    var bpm by mutableStateOf(100)
        private set
    /** Which starting voicing of the first chord (neck region) drives the voice-leading. */
    var positionIndex by mutableStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentBar by mutableStateOf(-1)
        private set
    /** The shape currently sounding (or previewed) — drives the follow-along fretboard. */
    var currentShape by mutableStateOf<ChordShape?>(null)
        private set

    private var loopJob: Job? = null
    private val gen = ChordShapeGenerator()

    val sequence: CavaqSequence
        get() = CavaqSequences.byId(sequenceId) ?: CavaqSequences.ALL.first()

    /** The sequence realised in the current key: (symbol, roman, root) per bar. */
    val resolved: List<ResolvedChord>
        get() = sequence.prog.resolve(key)

    private fun minFret(sh: ChordShape) = sh.frets.filterNotNull().minOrNull() ?: 0

    /** Candidate starting voicings for the first chord, sorted low → high on the neck. */
    private fun firstShapes(): List<ChordShape> {
        val first = resolved.firstOrNull() ?: return emptyList()
        val (root, q) = ChordLibrary.parse(first.symbol) ?: return emptyList()
        return gen.shapesFor(root, q, tuningProvider(), frets = DISPLAY_FRETS).sortedBy { minFret(it) }
    }

    /** How many neck-region positions the current sequence offers (>= 1 when playable). */
    val positionCount: Int get() = firstShapes().size.coerceAtLeast(1)

    /** Voice-led shapes for the whole sequence, from the chosen [positionIndex]. Entries
     *  are null for any (rare) chord with no playable cavaquinho voicing. */
    fun shapes(): List<ChordShape?> {
        val tuning = tuningProvider()
        val starts = firstShapes()
        var prev: ChordShape? = null
        return resolved.mapIndexed { i, rc ->
            val parsed = ChordLibrary.parse(rc.symbol) ?: return@mapIndexed null
            val (root, q) = parsed
            val chosen: ChordShape? = if (i == 0) {
                if (starts.isEmpty()) null else starts[positionIndex.coerceIn(0, starts.size - 1)]
            } else {
                val shs = gen.shapesFor(root, q, tuning, frets = DISPLAY_FRETS)
                when {
                    shs.isEmpty() -> null
                    prev == null -> shs.sortedBy { minFret(it) }.first()
                    else -> shs[VoiceLeading.pickMinMovement(prev!!, shs)]
                }
            }
            if (chosen != null) prev = chosen
            chosen
        }
    }

    fun setSequence(id: String) {
        if (id == sequenceId) return
        sequenceId = id
        positionIndex = 0
        resetPreview()
    }

    fun chooseKey(pc: PitchClass) {
        transpose += ((pc.value - key.value) % 12 + 12) % 12
        key = pc
        resetPreview()
    }

    fun shiftKey(semitones: Int) {
        key = PitchClass.of(key.value + semitones)
        transpose += semitones
        resetPreview()
    }

    fun setPosition(i: Int) {
        positionIndex = i.coerceIn(0, positionCount - 1)
        resetPreview()
    }

    fun nudgePosition(delta: Int) = setPosition(positionIndex + delta)

    fun changeBpm(v: Int) { bpm = v.coerceIn(30, 240) }

    /** Refresh the idle fretboard preview (first chord's chosen shape) when not looping. */
    private fun resetPreview() {
        if (!isPlaying) {
            currentBar = -1
            currentShape = shapes().firstOrNull()
        }
    }

    fun toggle() { if (isPlaying) stop() else play() }

    fun play() {
        if (isPlaying) return
        isPlaying = true
        loopJob = scope.launch {
            val beatMs = (60_000L / bpm.coerceAtLeast(10))
            val barMs = beatMs * 4
            val sustain = (barMs * 0.9).toInt().coerceAtLeast(200)
            while (isPlaying) {
                val shs = shapes()
                for (i in shs.indices) {
                    if (!isPlaying) break
                    currentBar = i
                    val sh = shs[i]
                    currentShape = sh
                    val midis = sh?.notes?.mapNotNull { it?.midi?.value } ?: emptyList()
                    if (midis.isNotEmpty()) {
                        audio.playChord(midis, strumDelayMillis = strumProvider(),
                            sustainMillis = sustain, timbre = timbreProvider())
                    }
                    delay(barMs)
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        loopJob?.cancel()
        loopJob = null
        currentBar = -1
        audio.stop()
        currentShape = shapes().firstOrNull()
    }

    /** Play a single bar's chord once (for tapping a chord to hear it). */
    fun playBar(i: Int) {
        val sh = shapes().getOrNull(i) ?: return
        currentBar = i
        currentShape = sh
        val midis = sh.notes.mapNotNull { it?.midi?.value }
        if (midis.isEmpty()) return
        scope.launch {
            audio.playChord(midis, strumDelayMillis = strumProvider(),
                sustainMillis = sustainProvider(), timbre = timbreProvider())
        }
    }

    /** Label like "C" / "A♭" for the current key. */
    fun keyLabel(): String = NoteSpeller.spell(key)
}

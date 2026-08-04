package app.guitar.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.theory.RhythmUnit
import app.guitar.theory.RhythmUnits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Playback for the Rhythmic Units screen: loops one selected one-beat unit,
 * clicking a synthesized woodblock-like tick on each note onset (the first onset
 * of every beat accented). BPM-controllable. Reuses the drum machine's
 * mixer-clock lookahead scheduling ([AudioEngine.playSamplesAt]) so timing is
 * sample-accurate and never drifts.
 */
class RhythmUnitState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
) {
    var selectedId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var bpm by mutableStateOf(30)
        private set

    private var job: Job? = null

    // Pre-rendered click buffers: a short exponentially-decaying tone. Accent (beat 1)
    // is a touch higher + louder so the downbeat is felt.
    private val click = synthClick(2000.0, 45)
    private val accentClick = synthClick(2800.0, 45)

    val selected: RhythmUnit? get() = selectedId?.let { RhythmUnits.byId(it) }

    /** Tap a unit card: switch to it and (re)start its loop; tapping the one that's
     *  already playing stops. */
    fun select(id: String) {
        if (selectedId == id && isPlaying) { stop(); return }
        selectedId = id
        restart()
    }

    fun toggle() {
        if (isPlaying) stop() else if (selectedId != null) start()
    }

    // Named changeBpm (not setBpm) to avoid clashing with the generated `bpm` setter.
    fun changeBpm(v: Int) { bpm = v.coerceIn(10, 300) }   // loop re-reads bpm each beat

    // Clear isPlaying before restarting so start()'s `if (isPlaying) return` guard
    // doesn't swallow an instant switch to another unit while one is playing.
    private fun restart() { cancelJob(); isPlaying = false; start() }

    fun start() {
        val u = selected ?: return
        if (isPlaying) return
        isPlaying = true
        job = scope.launch { loop(u) }
    }

    fun stop() {
        cancelJob()
        isPlaying = false
    }

    private fun cancelJob() { job?.cancel(); job = null }

    private suspend fun loop(u: RhythmUnit) {
        val sr = audio.sampleRate
        val fractions = u.clickFractions()   // rests produce no click
        var nextBeatNanos = System.nanoTime()
        while (isPlaying) {
            val beatMs = 60_000.0 / bpm
            val baseDelayMs = ((nextBeatNanos - System.nanoTime()) / 1_000_000.0).coerceAtLeast(0.0)
            for (f in fractions) {
                val onsetDelayMs = baseDelayMs + f * beatMs
                val delayFrames = (onsetDelayMs * sr / 1000.0).toInt().coerceAtLeast(0)
                // Accent the downbeat click (a note landing on the beat), not merely the
                // first click — so a unit that rests on beat 1 isn't falsely accented.
                if (f == 0.0) audio.playSamplesAt(accentClick, 1.0f, delayFrames)
                else audio.playSamplesAt(click, 0.72f, delayFrames)
            }
            nextBeatNanos += (beatMs * 1_000_000).toLong()
            delay((baseDelayMs + beatMs).toLong().coerceAtLeast(1))
        }
    }

    private fun synthClick(freqHz: Double, ms: Int, sr: Int = audio.sampleRate): FloatArray {
        val n = sr * ms / 1000
        val buf = FloatArray(n)
        val w = 2.0 * Math.PI * freqHz / sr
        for (i in 0 until n) {
            val env = Math.exp(-6.0 * i / n)   // fast percussive decay
            buf[i] = (Math.sin(w * i) * env * 0.7).toFloat()
        }
        return buf
    }
}

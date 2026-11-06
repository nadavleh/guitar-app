package app.guitar.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Standalone metronome: clicks [beatsPerBar] wood clicks per bar at [bpm], the
 * downbeat ("1") accented with a higher wood click. Mirror of chorect-web's
 * MetronomeState. Uses the mixer-clock lookahead ([AudioEngine.playSamplesAt])
 * like [RhythmUnitState] so timing is sample-accurate and never drifts.
 */
class MetronomeState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
) {
    var bpm by mutableStateOf(100)
        private set
    var beatsPerBar by mutableStateOf(4)
        private set
    var beatUnit by mutableStateOf(4)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    /** Beat currently sounding (0-based), or -1 when stopped. Drives the beat dots. */
    var currentBeat by mutableStateOf(-1)
        private set

    private var job: Job? = null
    private val click = synthClick(2000.0, 45)
    private val accentClick = synthClick(2800.0, 45)

    // Named changeBpm (not setBpm) to avoid clashing with the generated `bpm` setter.
    fun changeBpm(v: Int) { bpm = v.coerceIn(10, 300) }   // loop re-reads bpm each beat

    fun setTimeSignature(beats: Int, unit: Int) {
        beatsPerBar = beats.coerceIn(1, 12)
        beatUnit = unit
    }

    fun toggle() { if (isPlaying) stop() else start() }

    private val taps = TapTempo()
    fun tapTempo(nowMs: Long = System.currentTimeMillis()) { taps.tap(nowMs)?.let { bpm = it } }

    fun start() {
        if (isPlaying) return
        isPlaying = true
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel(); job = null
        isPlaying = false
        currentBeat = -1
    }

    fun release() = stop()

    /**
     * Lookahead scheduler: wake [LOOKAHEAD_MS] BEFORE each beat and queue its click at
     * the beat's absolute time on the mixer clock (delayFrames), so coroutine wake-up
     * jitter never moves a click. The beat dot updates at the onset itself.
     */
    private suspend fun loop() {
        val sr = audio.sampleRate
        var nextBeatNanos = System.nanoTime()
        var beat = 0
        while (isPlaying) {
            val beatNanos = 60e9 / bpm
            // Fell more than a beat behind (process paused): resync, don't burst.
            if (System.nanoTime() - nextBeatNanos > beatNanos) nextBeatNanos = System.nanoTime()
            val delayNanos = (nextBeatNanos - System.nanoTime()).coerceAtLeast(0)
            val delayFrames = (delayNanos * sr / 1e9).roundToInt()
            if (beat == 0) audio.playSamplesAt(accentClick, 1.0f, delayFrames)
            else audio.playSamplesAt(click, 0.72f, delayFrames)
            delay(delayNanos / 1_000_000)
            if (!isPlaying) break
            currentBeat = beat
            nextBeatNanos += beatNanos.roundToLong()
            beat = (beat + 1) % beatsPerBar
            val wakeNanos = nextBeatNanos - LOOKAHEAD_MS * 1_000_000L - System.nanoTime()
            if (wakeNanos > 0) delay(wakeNanos / 1_000_000)
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

    companion object {
        /** How far ahead of each beat its click is queued (≥ worst-case wake-up jitter). */
        private const val LOOKAHEAD_MS = 100L

        /** Time signatures offered (beatsPerBar to beatUnit). Cadence uses beatsPerBar. */
        val TIME_SIGNATURES = listOf(2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 8, 7 to 8, 3 to 8, 12 to 8, 2 to 2)
    }
}

/** Tap-tempo: average the intervals of the recent taps (2 s window, last 6). Shared by
 *  the metronome and the samba looper. */
class TapTempo {
    private val times = ArrayList<Long>()

    /** Record a tap; returns the tapped BPM (10..300) once two taps are in, else null. */
    fun tap(nowMs: Long): Int? {
        if (times.isNotEmpty() && nowMs - times.last() > 2000) times.clear()
        times.add(nowMs)
        while (times.size > 6) times.removeAt(0)
        if (times.size < 2) return null
        val avg = (times.last() - times.first()).toDouble() / (times.size - 1)
        return (60_000.0 / avg).roundToInt().coerceIn(10, 300)
    }
}

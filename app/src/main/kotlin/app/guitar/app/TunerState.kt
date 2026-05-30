package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.MicInput
import app.guitar.audio.PitchAnalysis
import app.guitar.audio.PitchDetector

/**
 * Tuner state. Owns the mic capture + pitch detector pipeline and exposes
 * the latest reading as Compose-observable state.
 *
 * Lifecycle: [start] hooks the mic; [stop] releases it. Both are idempotent.
 * The instance is single-use — create a new one if you re-enter the tuner.
 */
@Stable
class TunerState(
    /** Function returning the current A4 reference frequency. Read on every analysis. */
    private val a4Provider: () -> Float,
) {
    private val mic = MicInput()
    private val detector = PitchDetector()

    /** Last detected fundamental in Hz, or null when no pitch is being heard. */
    var freqHz by mutableStateOf<Float?>(null)
        private set
    /** Last detected nearest equal-tempered MIDI note (under current A4 reference). */
    var midi by mutableStateOf<Int?>(null)
        private set
    /** Last detected cents offset from the nearest note. (-50, +50]. */
    var cents by mutableStateOf<Float?>(null)
        private set
    /** True when the mic is capturing; false when the user denied permission or stopped. */
    var capturing by mutableStateOf(false)
        private set

    /** Smoothing buffer — last N detections averaged in pitch-class space for stability. */
    private val smoothing = ArrayDeque<Pair<Int, Float>>()
    private val SMOOTH_N = 4

    /** Lock the dial to a specific (midi, cents) until [lockedUntilMs] (uptime millis).
     *  Used when the user taps the displayed note: we want the dial to read "spot on"
     *  for the tone we're playing back. */
    var lockedMidi by mutableStateOf<Int?>(null)
        private set
    private var lockedUntilMs = 0L

    /** Start mic capture. Returns false if the mic could not be initialised (permission missing, etc.) */
    fun start(): Boolean {
        if (capturing) return true
        val ok = mic.start { samples -> onSamples(samples) }
        capturing = ok
        return ok
    }

    fun stop() {
        if (!capturing) return
        mic.stop()
        capturing = false
        freqHz = null
        midi = null
        cents = null
        smoothing.clear()
    }

    /** Force the dial to read "spot on" for [midi] for [holdMs] millis. */
    fun lockTo(midi: Int, holdMs: Long = 1500L) {
        lockedMidi = midi
        lockedUntilMs = System.currentTimeMillis() + holdMs
        this.midi = midi
        this.cents = 0f
        this.freqHz = PitchAnalysis.midiToFreq(midi, a4Provider())
    }

    private fun onSamples(samples: FloatArray) {
        // Energy gate — under a tiny RMS, treat as silence to avoid the dial wiggling on noise.
        var energy = 0.0
        for (s in samples) energy += (s * s).toDouble()
        val rms = Math.sqrt(energy / samples.size).toFloat()
        if (rms < 0.005f) {
            // If we're not in a locked state, clear the reading.
            if (System.currentTimeMillis() >= lockedUntilMs) {
                freqHz = null; midi = null; cents = null
                smoothing.clear()
            }
            return
        }

        // Honor an active lock — don't overwrite with mic readings while held.
        if (System.currentTimeMillis() < lockedUntilMs) return
        if (lockedMidi != null) lockedMidi = null

        val freq = detector.detect(samples) ?: return
        val est = PitchAnalysis.analyze(freq, a4Provider())

        // Smooth: keep the last N readings, drop those whose MIDI differs from the current
        // dominant note (cents per-note averaging is more stable than raw freq averaging).
        smoothing.addLast(est.midi to est.cents)
        if (smoothing.size > SMOOTH_N) smoothing.removeFirst()
        val dominant = smoothing.groupingBy { it.first }.eachCount().maxBy { it.value }.key
        val centsAvg = smoothing.filter { it.first == dominant }.map { it.second }.average().toFloat()

        freqHz = freq
        midi = dominant
        cents = centsAvg
    }
}

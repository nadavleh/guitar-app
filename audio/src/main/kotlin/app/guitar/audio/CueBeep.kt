package app.guitar.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * A short soft-attack sine cue, synthesised on the fly — the Car-mode lead-in beep.
 *
 * Nothing is loaded from disk: no asset ships for this. Mirrored by
 * chorect-web/src/audio/cueBeep.ts; keep the envelope identical so the two platforms
 * announce an exercise with the same sound.
 */
object CueBeep {

    /**
     * Render [ms] of a [freqHz] sine at [sr] Hz, with [attackMs] of linear fade-in
     * followed by an exponential decay, scaled so samples stay within ±[peak].
     *
     * The attack matters: a raw sine that starts at full amplitude produces an
     * audible click, which in a car reads as a glitch rather than a cue.
     */
    fun render(freqHz: Double, ms: Int, sr: Int, peak: Float, attackMs: Int): FloatArray {
        val n = sr * ms / 1000
        if (n <= 0) return FloatArray(0)
        val out = FloatArray(n)
        val attackSamples = min(sr * attackMs / 1000, n)
        val twoPiFOverSr = 2.0 * PI * freqHz / sr
        for (i in 0 until n) {
            // Linear attack, then exp(-6t) decay over the remaining length: at the
            // final sample that is e^-6 ≈ 0.0025 of peak, comfortably silent.
            val attack = if (attackSamples > 0) min(1.0, i.toDouble() / attackSamples) else 1.0
            val decay = exp(-6.0 * i / n)
            out[i] = (sin(twoPiFOverSr * i) * attack * decay * peak).toFloat()
        }
        return out
    }
}

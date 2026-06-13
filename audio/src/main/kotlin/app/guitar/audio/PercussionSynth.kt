package app.guitar.audio

import app.guitar.theory.PercussionInstrument
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Phase-1 synthesized samba percussion — no samples, all generated on the fly,
 * the same approach as [PluckedSynth]. Each (instrument, voiceIndex) returns a
 * one-shot mono FloatArray in [-1, 1]; the app caches these once and feeds them
 * to the mixer via [AudioEngine.playSamples].
 *
 * Pure Kotlin (deterministic noise via a seeded RNG) → unit-testable on the JVM.
 */
class PercussionSynth(val sampleRate: Int = 44100) {

    fun synthesize(instrument: PercussionInstrument, voiceIndex: Int): FloatArray = when (instrument) {
        PercussionInstrument.Surdo -> when (voiceIndex) {
            0 -> surdo(open = true)
            else -> surdo(open = false)
        }
        PercussionInstrument.Tamborim -> when (voiceIndex) {
            0 -> tamborim(muted = false)
            else -> tamborim(muted = true)
        }
        PercussionInstrument.Pandeiro -> when (voiceIndex) {
            0 -> noiseDrum(durSec = 0.10, decay = 45.0, lpAlpha = 0.06, hp = false, amp = 0.75) // low slap
            1 -> noiseDrum(durSec = 0.14, decay = 30.0, lpAlpha = 0.30, hp = false, amp = 0.65) // high open
            else -> noiseDrum(durSec = 0.04, decay = 120.0, lpAlpha = 0.20, hp = false, amp = 0.45) // mute tap
        }
        PercussionInstrument.Agogo -> when (voiceIndex) {
            0 -> bell(freq = 590.0)
            else -> bell(freq = 740.0)
        }
    }

    // ---- voices ----

    /** Low boom: fundamental sine + a noisy strike transient, exponential decay. */
    private fun surdo(open: Boolean): FloatArray {
        val durSec = if (open) 0.45 else 0.10
        val decay = if (open) 7.0 else 38.0
        val freq = 68.0
        val n = (sampleRate * durSec).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val rng = Random(101)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val e = exp(-decay * t)
            val body = sin(2 * PI * freq * t) + 0.4 * sin(2 * PI * freq * 2 * t)
            val transient = if (t < 0.006) (rng.nextDouble() * 2 - 1) * exp(-t * 600) else 0.0
            out[i] = ((body * 0.55 + transient * 0.6) * e * 0.85).toFloat()
        }
        return fadeOut(out)
    }

    /** Bright dry crack: high-passed noise burst. */
    private fun tamborim(muted: Boolean): FloatArray {
        val durSec = if (muted) 0.05 else 0.11
        val decay = if (muted) 110.0 else 55.0
        return noiseDrum(durSec, decay, lpAlpha = 0.45, hp = true, amp = if (muted) 0.5 else 0.7)
    }

    /** Two tuned bells: pure-ish sine with a soft envelope and a faint 2nd partial. */
    private fun bell(freq: Double): FloatArray {
        val durSec = 0.25
        val decay = 11.0
        val n = (sampleRate * durSec).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val e = exp(-decay * t)
            val s = sin(2 * PI * freq * t) + 0.25 * sin(2 * PI * freq * 2.76 * t)
            out[i] = (s * e * 0.6).toFloat()
        }
        return fadeOut(out)
    }

    /**
     * Generic membrane/skin hit: white noise through a one-pole filter with an
     * exponential amplitude decay.
     * @param lpAlpha one-pole coefficient (higher = brighter low-pass).
     * @param hp if true, output the high-pass complement (white - lowpass).
     */
    private fun noiseDrum(
        durSec: Double,
        decay: Double,
        lpAlpha: Double,
        hp: Boolean,
        amp: Double,
        seed: Long = 202L,
    ): FloatArray {
        val n = (sampleRate * durSec).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val rng = Random(seed)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val white = rng.nextDouble() * 2 - 1
            lp += lpAlpha * (white - lp)
            val sample = if (hp) (white - lp) else lp
            val e = exp(-decay * t)
            out[i] = (sample * e * amp).toFloat()
        }
        return fadeOut(out)
    }

    /** 3 ms linear fade-out so the buffer ends exactly at 0 (no click). */
    private fun fadeOut(buf: FloatArray): FloatArray {
        val fade = (sampleRate * 0.003).toInt().coerceAtMost(buf.size)
        for (i in 0 until fade) {
            val mult = i.toFloat() / fade
            buf[buf.size - 1 - i] *= mult
        }
        return buf
    }
}

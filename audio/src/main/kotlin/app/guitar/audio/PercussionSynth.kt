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
        // Surdo: 0 ringing bass, 1 muted bass, 2 light muted tap.
        PercussionInstrument.Surdo -> when (voiceIndex) {
            0 -> surdo(open = true)
            1 -> surdo(open = false)
            else -> tonedTap(freq = 165.0, durSec = 0.055, decay = 75.0, amp = 0.30)
        }
        // Tamborim: 0 high clack, 1 muted clack, 2 light tap.
        PercussionInstrument.Tamborim -> when (voiceIndex) {
            0 -> tamborimClack(muted = false)
            1 -> tamborimClack(muted = true)
            else -> noiseDrum(durSec = 0.05, decay = 95.0, lpAlpha = 0.30, hp = true, amp = 0.30)
        }
        // Pandeiro: 0 open bass, 1 muted bass (low-mid), 2 slap, 3 jingle.
        PercussionInstrument.Pandeiro -> when (voiceIndex) {
            0 -> tonedTap(freq = 150.0, durSec = 0.22, decay = 13.0, amp = 0.62) // open bass
            1 -> tonedTap(freq = 155.0, durSec = 0.08, decay = 42.0, amp = 0.55) // muted bass
            2 -> noiseDrum(durSec = 0.08, decay = 60.0, lpAlpha = 0.50, hp = true, amp = 0.72) // slap
            else -> jingle()
        }
        PercussionInstrument.Agogo -> when (voiceIndex) {
            0 -> bell(freq = 590.0)
            else -> bell(freq = 740.0)
        }
    }

    // ---- voices ----

    /** Low boom: fundamental sine + a noisy strike transient, exponential decay. */
    private fun surdo(open: Boolean): FloatArray {
        val durSec = if (open) 0.50 else 0.12
        val decay = if (open) 6.0 else 34.0
        val freq = if (open) 62.0 else 66.0
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

    /**
     * Pitched membrane hit (tom-like): a fundamental + 2nd partial with a short
     * noisy attack. Used for the low-mid pandeiro bass notes and the surdo tap.
     */
    private fun tonedTap(freq: Double, durSec: Double, decay: Double, amp: Double): FloatArray {
        val n = (sampleRate * durSec).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val rng = Random(404)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val e = exp(-decay * t)
            val body = sin(2 * PI * freq * t) + 0.5 * sin(2 * PI * freq * 2 * t)
            val transient = if (t < 0.005) (rng.nextDouble() * 2 - 1) * exp(-t * 500) else 0.0
            out[i] = ((body * 0.6 + transient * 0.5) * e * amp).toFloat()
        }
        return fadeOut(out)
    }

    /** High, fast-attack tamborim "clack": bright high-passed noise + a short high tone. */
    private fun tamborimClack(muted: Boolean): FloatArray {
        val durSec = if (muted) 0.045 else 0.09
        val decay = if (muted) 130.0 else 70.0
        val amp = if (muted) 0.5 else 0.78
        val n = (sampleRate * durSec).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val rng = Random(202)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val white = rng.nextDouble() * 2 - 1
            lp += 0.6 * (white - lp)
            val hpNoise = white - lp
            val tone = sin(2 * PI * 1500.0 * t) * exp(-t * 220)   // brief high "clak" pitch
            val e = exp(-decay * t)
            out[i] = ((hpNoise * 0.7 + tone * 0.28) * e * amp).toFloat()
        }
        return fadeOut(out)
    }

    /** Pandeiro jingle (platinelas): bright high-passed noise + inharmonic high
     *  partials that shimmer and ring. */
    private fun jingle(): FloatArray {
        val durSec = 0.20
        val decay = 20.0
        val base = 2700.0
        val n = (sampleRate * durSec).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val rng = Random(303)
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val white = rng.nextDouble() * 2 - 1
            lp += 0.7 * (white - lp)
            val hpNoise = white - lp
            val metal = sin(2 * PI * base * t) +
                0.6 * sin(2 * PI * base * 1.51 * t) +
                0.4 * sin(2 * PI * base * 2.31 * t)
            val e = exp(-decay * t)
            out[i] = ((hpNoise * 0.5 + metal * 0.16) * e * 0.5).toFloat()
        }
        return fadeOut(out)
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

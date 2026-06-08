package app.guitar.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Karplus-Strong plucked-string synthesis.
 *
 * Classical algorithm: initialize a delay-line buffer of length n = round(sampleRate/freq)
 * with white noise; on each sample, output the head value then replace it with a
 * lowpass-filtered (running 2-tap average × damping) version of itself and the next sample.
 * - n controls the fundamental frequency (longer delay → lower pitch).
 * - The 2-tap average is a 1st-order lowpass — it makes higher harmonics decay faster
 *   than the fundamental, giving the characteristic plucked-string sound.
 * - The damping factor (<1.0) makes the whole tone decay over time.
 *
 * Pure Kotlin / no Android API → unit-testable on the JVM.
 */
class PluckedSynth(val sampleRate: Int = 48000) {

    fun synthesize(
        midiNote: Int,
        durationSec: Double,
        seed: Long = 1L,
        damping: Double = 0.997,
        amplitude: Double = 0.6,
    ): FloatArray {
        require(midiNote in 0..127) { "MIDI note must be 0..127, got $midiNote" }
        return synthesizeFrequency(midiToFreq(midiNote), durationSec, seed, damping, amplitude)
    }

    /** Synthesize a plucked tone at an arbitrary frequency. Lets the tuner play a reference
     *  pitch under a custom A4 reference without going through MIDI rounding. */
    fun synthesizeFrequency(
        freqHz: Double,
        durationSec: Double,
        seed: Long = 1L,
        damping: Double = 0.997,
        amplitude: Double = 0.6,
    ): FloatArray {
        require(freqHz > 0.0) { "freq must be > 0, got $freqHz" }
        require(durationSec > 0.0) { "duration must be positive, got $durationSec" }
        require(damping in 0.0..1.0) { "damping must be in 0..1, got $damping" }

        // Round (not truncate) the delay length: integer truncation tunes notes sharp,
        // which makes chords beat and smear. Rounding halves the worst-case detuning so
        // simultaneously-sounding chord tones stay in tune and stay distinguishable.
        val n = (sampleRate / freqHz).roundToInt().coerceAtLeast(2)

        // --- Excitation: shaped noise for more realistic plucked harmonics ---
        val rng = Random(seed)
        val raw = DoubleArray(n) { rng.nextDouble() * 2.0 - 1.0 }
        // Pluck-position comb: a string plucked ~1/4 along its length has the 4th harmonic
        // (and its multiples) suppressed. Subtracting a delayed copy carves those notches,
        // giving a more natural harmonic balance than raw white noise.
        val pluck = (n / 4).coerceAtLeast(1)
        val buf = DoubleArray(n)
        var warm = 0.0
        val warmCoef = 0.55   // one-pole lowpass on the excitation → warmer, less fizzy attack
        var maxAbs = 1e-9
        for (i in 0 until n) {
            val combed = raw[i] - 0.9 * raw[(i - pluck + n) % n]
            warm = warmCoef * combed + (1.0 - warmCoef) * warm
            buf[i] = warm
            val a = abs(warm)
            if (a > maxAbs) maxAbs = a
        }
        // Normalize excitation to unit peak so output respects the ±amplitude bound exactly.
        val norm = 1.0 / maxAbs
        for (i in 0 until n) buf[i] *= norm

        // --- Karplus-Strong delay loop ---
        val numSamples = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val ks = DoubleArray(numSamples)
        var idx = 0
        for (i in 0 until numSamples) {
            val cur = buf[idx]
            val nxt = buf[(idx + 1) % n]
            ks[i] = cur
            buf[idx] = damping * 0.5 * (cur + nxt)
            idx = (idx + 1) % n
        }

        // --- Body: blend in a low-passed copy for more bottom end. A convex mix of two
        // signals each within [-1, 1] stays within [-1, 1], so the amplitude bound and
        // amplitude-linearity both hold (the shape is independent of `amplitude`). ---
        val output = FloatArray(numSamples)
        var body = 0.0
        val bodyCoef = 0.12   // one-pole lowpass cutoff for the body component (lower = deeper)
        val bodyMix = 0.32    // how much low-passed body to blend in
        for (i in 0 until numSamples) {
            body = bodyCoef * ks[i] + (1.0 - bodyCoef) * body
            val mix = (1.0 - bodyMix) * ks[i] + bodyMix * body
            output[i] = (amplitude * mix).toFloat()
        }

        // 50ms linear fade-out so the very last sample ends at 0 (no click).
        // For loop index i: 0 maps to the very last sample (mult = 0), fadeSamples-1 maps
        // to the first sample of the fade region (mult ≈ 1).
        val fadeSamples = (sampleRate * 0.05).toInt().coerceAtMost(numSamples)
        for (i in 0 until fadeSamples) {
            val mult = i.toFloat() / fadeSamples
            output[numSamples - 1 - i] *= mult
        }
        return output
    }

    /**
     * Synthesize a strummed chord: each note is offset by [strumDelaySamples]
     * relative to the previous one and mixed into a single buffer.
     * The mix is attenuated by 1/sqrt(N) to keep the peak amplitude bounded.
     */
    fun synthesizeChord(
        midiNotes: List<Int>,
        sustainSec: Double,
        strumDelaySamples: Int,
        seedBase: Long = 1L,
        damping: Double = 0.997,
        amplitude: Double = 0.6,
    ): FloatArray {
        require(sustainSec > 0.0)
        require(strumDelaySamples >= 0)
        val voices = midiNotes.filter { it in 0..127 }
        if (voices.isEmpty()) return FloatArray(0)
        val perVoiceLen = (sampleRate * sustainSec).toInt().coerceAtLeast(1)
        val totalLen = perVoiceLen + (voices.size - 1) * strumDelaySamples
        val mix = FloatArray(totalLen)
        val scale = (1.0 / kotlin.math.sqrt(voices.size.toDouble())).toFloat()
        voices.forEachIndexed { i, midi ->
            val voice = synthesize(
                midiNote = midi,
                durationSec = sustainSec,
                seed = seedBase + i,
                damping = damping,
                amplitude = amplitude,
            )
            val offset = i * strumDelaySamples
            val end = minOf(offset + voice.size, totalLen)
            for (j in 0 until (end - offset)) {
                mix[offset + j] += voice[j] * scale
            }
        }
        return mix
    }

    companion object {
        fun midiToFreq(midiNote: Int): Double = 440.0 * 2.0.pow((midiNote - 69) / 12.0)
    }
}

package app.guitar.audio

import kotlin.math.pow
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

        val n = (sampleRate / freqHz).toInt().coerceAtLeast(2)
        val buf = DoubleArray(n)
        val rng = Random(seed)
        for (i in 0 until n) buf[i] = rng.nextDouble() * 2.0 - 1.0

        val numSamples = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val output = FloatArray(numSamples)
        var idx = 0
        for (i in 0 until numSamples) {
            val cur = buf[idx]
            val nxt = buf[(idx + 1) % n]
            output[i] = (amplitude * cur).toFloat()
            buf[idx] = damping * 0.5 * (cur + nxt)
            idx = (idx + 1) % n
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

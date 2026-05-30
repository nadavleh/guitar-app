package app.guitar.audio

/**
 * YIN pitch detection (de Cheveigné & Kawahara, 2002).
 *
 * Pure-Kotlin, no Android dependencies, so it can be unit-tested. Given a
 * window of mono PCM samples normalized to [-1, 1], returns the fundamental
 * frequency in Hz, or null when no confident pitch is found.
 *
 * Tuned for monophonic guitar / voice in the 50-1500 Hz range. For low E2
 * (~82 Hz) at 44.1 kHz the window must contain at least one full period
 * (~535 samples) — 2048 samples is a comfortable default.
 *
 * @param sampleRate sample rate of the input buffer
 * @param threshold YIN absolute threshold. Below this value of the CMNDF a
 *   tau is considered a pitch candidate. 0.10-0.15 is typical for guitar.
 * @param minFreq lowest detectable frequency (caps maximum tau)
 * @param maxFreq highest detectable frequency (sets minimum tau)
 */
class PitchDetector(
    val sampleRate: Int = 44100,
    val threshold: Float = 0.15f,
    val minFreq: Float = 50f,
    val maxFreq: Float = 1500f,
) {
    /**
     * Returns the detected fundamental in Hz, or null if no confident pitch.
     *
     * The buffer is consumed read-only; safe to share between threads.
     */
    fun detect(samples: FloatArray): Float? {
        if (samples.size < 32) return null
        val tauMin = (sampleRate / maxFreq).toInt().coerceAtLeast(2)
        val tauMax = (sampleRate / minFreq).toInt().coerceAtMost(samples.size / 2 - 1)
        if (tauMin >= tauMax) return null

        // Step 1: difference function d(tau)
        // d(tau) = sum over j of (x[j] - x[j+tau])^2, with j in [0, W - tauMax)
        val diff = FloatArray(tauMax + 1)
        val W = samples.size - tauMax
        for (tau in 1..tauMax) {
            var sum = 0f
            var j = 0
            while (j < W) {
                val delta = samples[j] - samples[j + tau]
                sum += delta * delta
                j++
            }
            diff[tau] = sum
        }

        // Step 2: cumulative mean normalized difference: d'(tau) = d(tau) / ((1/tau) * sum_{i=1..tau} d(i))
        val cmnd = FloatArray(tauMax + 1)
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += diff[tau]
            cmnd[tau] = if (running == 0f) 1f else diff[tau] * tau / running
        }

        // Step 3: absolute threshold — find first tau where d'(tau) < threshold,
        // then walk down to the local minimum.
        var pitchTau = -1
        var tau = tauMin
        while (tau < tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                pitchTau = tau
                break
            }
            tau++
        }
        if (pitchTau == -1) {
            // No tau crossed the threshold — fall back to the global minimum.
            // Reject if the minimum CMNDF is still high (no clear periodicity).
            var minIdx = tauMin
            var minVal = cmnd[tauMin]
            for (t in tauMin + 1..tauMax) {
                if (cmnd[t] < minVal) {
                    minVal = cmnd[t]
                    minIdx = t
                }
            }
            if (minVal > 0.5f) return null
            pitchTau = minIdx
        }

        // Step 4: parabolic interpolation refines the integer tau using nearby CMNDF samples.
        val refined = parabolicInterpolation(cmnd, pitchTau)
        return sampleRate / refined
    }

    /** 3-point parabolic fit around index [t] in [d], returning the sub-sample minimum location. */
    private fun parabolicInterpolation(d: FloatArray, t: Int): Float {
        if (t < 1 || t >= d.size - 1) return t.toFloat()
        val s0 = d[t - 1]
        val s1 = d[t]
        val s2 = d[t + 1]
        val denom = 2f * s1 - s0 - s2
        return if (denom == 0f) t.toFloat()
        else t + 0.5f * (s2 - s0) / denom
    }
}

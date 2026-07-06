package app.guitar.audio

import kotlin.math.abs
import kotlin.math.exp

/** Peak-following soft limiter on the stereo bus. Tracks the max |sample| across L/R
 *  and pulls a smoothed gain down so the output never exceeds [ceiling]. A short
 *  release lets the gain recover after a transient. Guarantees |out| <= ceiling by a
 *  final safety clamp. Pure — no Android API. */
class SoftLimiter(
    sampleRate: Int,
    private val ceiling: Float = 0.944f,   // -0.5 dBFS
    releaseMs: Double = 80.0,
) {
    private var gain = 1f
    private val releaseCoef = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()

    fun process(l: FloatArray, r: FloatArray, count: Int) {
        for (i in 0 until count) {
            val peak = maxOf(abs(l[i]), abs(r[i]))
            // Target gain that would bring this peak to the ceiling (<=1).
            val target = if (peak > ceiling) ceiling / peak else 1f
            // Attack instantly (clamp down now), release slowly (recover).
            gain = if (target < gain) target else gain * releaseCoef + target * (1 - releaseCoef)
            var sl = l[i] * gain
            var sr = r[i] * gain
            // Safety clamp (guarantees the invariant even during release lag).
            if (sl > ceiling) sl = ceiling else if (sl < -ceiling) sl = -ceiling
            if (sr > ceiling) sr = ceiling else if (sr < -ceiling) sr = -ceiling
            l[i] = sl; r[i] = sr
        }
    }
}

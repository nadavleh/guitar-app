package app.guitar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Constant-power stereo panning. pan ∈ [-1, 1]; -1 = hard left, 0 = center, 1 = right. */
object Panner {
    fun gains(pan: Double): Pair<Float, Float> {
        val p = pan.coerceIn(-1.0, 1.0)
        val theta = (p + 1.0) * (PI / 4.0)     // 0..π/2
        return cos(theta).toFloat() to sin(theta).toFloat()
    }

    /** Subtle pan by pitch: MIDI 40..88 → [-spread, +spread], clamped. */
    fun forMidi(midi: Int, spread: Double = 0.3): Double {
        val t = ((midi - 40).toDouble() / (88 - 40)).coerceIn(0.0, 1.0)  // 0..1
        return ((t * 2.0 - 1.0) * spread).coerceIn(-spread, spread)
    }
}

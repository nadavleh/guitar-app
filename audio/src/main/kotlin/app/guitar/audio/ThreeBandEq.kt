package app.guitar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Per-sound EQ gains in dB, each in [-12, 12]. 0 = flat. */
data class EqSettings(val bassDb: Float = 0f, val midDb: Float = 0f, val trebleDb: Float = 0f)

/** One RBJ biquad (Direct Form I), mono state. Coeffs are stored already divided by a0. */
private class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = xd; y2 = y1; y1 = y
        return y.toFloat()
    }

    private fun set(b0n: Double, b1n: Double, b2n: Double, a0: Double, a1n: Double, a2n: Double) {
        b0 = b0n / a0; b1 = b1n / a0; b2 = b2n / a0; a1 = a1n / a0; a2 = a2n / a0
    }

    fun lowShelf(fc: Double, sr: Int, gainDb: Double) {
        val A = 10.0.pow(gainDb / 40.0); val w0 = 2 * PI * fc / sr
        val cw = cos(w0); val alpha = sin(w0) / 2.0 * sqrt(2.0); val ta = 2 * sqrt(A) * alpha
        set(
            A * ((A + 1) - (A - 1) * cw + ta),
            2 * A * ((A - 1) - (A + 1) * cw),
            A * ((A + 1) - (A - 1) * cw - ta),
            (A + 1) + (A - 1) * cw + ta,
            -2 * ((A - 1) + (A + 1) * cw),
            (A + 1) + (A - 1) * cw - ta,
        )
    }

    fun highShelf(fc: Double, sr: Int, gainDb: Double) {
        val A = 10.0.pow(gainDb / 40.0); val w0 = 2 * PI * fc / sr
        val cw = cos(w0); val alpha = sin(w0) / 2.0 * sqrt(2.0); val ta = 2 * sqrt(A) * alpha
        set(
            A * ((A + 1) + (A - 1) * cw + ta),
            -2 * A * ((A - 1) + (A + 1) * cw),
            A * ((A + 1) + (A - 1) * cw - ta),
            (A + 1) - (A - 1) * cw + ta,
            2 * ((A - 1) - (A + 1) * cw),
            (A + 1) - (A - 1) * cw - ta,
        )
    }

    fun peaking(fc: Double, sr: Int, q: Double, gainDb: Double) {
        val A = 10.0.pow(gainDb / 40.0); val w0 = 2 * PI * fc / sr
        val cw = cos(w0); val alpha = sin(w0) / (2 * q)
        set(1 + alpha * A, -2 * cw, 1 - alpha * A, 1 + alpha / A, -2 * cw, 1 - alpha / A)
    }
}

/**
 * Stereo 3-band tone EQ: low shelf @120 Hz, mid peak @700 Hz (Q≈0.9), high shelf @3500 Hz.
 * Gains in dB (±12). When all three gains are 0 it bypasses — [process] leaves the input
 * untouched (exact passthrough), and no filter state accumulates. Pure Kotlin.
 */
class ThreeBandEq(private val sampleRate: Int) {
    private companion object { const val BASS_HZ = 120.0; const val MID_HZ = 700.0; const val MID_Q = 0.9; const val TREBLE_HZ = 3500.0 }
    private val chainL = arrayOf(Biquad(), Biquad(), Biquad())   // bass, mid, treble
    private val chainR = arrayOf(Biquad(), Biquad(), Biquad())
    private var active = false

    fun setGainsDb(bass: Float, mid: Float, treble: Float) {
        val wasActive = active
        active = bass != 0f || mid != 0f || treble != 0f
        if (!active) return
        for (c in listOf(chainL, chainR)) {
            c[0].lowShelf(BASS_HZ, sampleRate, bass.toDouble())
            c[1].peaking(MID_HZ, sampleRate, MID_Q, mid.toDouble())
            c[2].highShelf(TREBLE_HZ, sampleRate, treble.toDouble())
        }
        if (!wasActive) { chainL.forEach { it.reset() }; chainR.forEach { it.reset() } }
    }

    fun process(l: FloatArray, r: FloatArray, count: Int) {
        if (!active) return
        for (i in 0 until count) {
            var vl = l[i]; var vr = r[i]
            for (b in 0..2) { vl = chainL[b].process(vl); vr = chainR[b].process(vr) }
            l[i] = vl; r[i] = vr
        }
    }
}

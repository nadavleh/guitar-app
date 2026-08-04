package app.guitar.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline EQ for pandeiro one-shots (mirror of chorect-web/src/app/pandeiroEq.ts):
 * a high-pass to clear the surdo's low band plus a gentle high-shelf lift.
 * RBJ biquad cookbook, direct-form-I. Applied once per buffer (cached upstream).
 */
private const val HP_HZ = 130.0
private const val HP_Q = 0.707
private const val SHELF_HZ = 3000.0
private const val SHELF_DB = 4.0

private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

private fun apply(x: FloatArray, c: Biquad): FloatArray {
    val y = FloatArray(x.size)
    var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
    for (i in x.indices) {
        val x0 = x[i].toDouble()
        val y0 = c.b0 * x0 + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
        y[i] = y0.toFloat()
        x2 = x1; x1 = x0; y2 = y1; y1 = y0
    }
    return y
}

private fun highpass(f0: Double, q: Double, fs: Double): Biquad {
    val w0 = 2 * PI * f0 / fs; val c = cos(w0); val s = sin(w0); val alpha = s / (2 * q)
    val a0 = 1 + alpha
    return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
}

private fun highshelf(f0: Double, dB: Double, fs: Double): Biquad {
    val a = 10.0.pow(dB / 40); val w0 = 2 * PI * f0 / fs; val c = cos(w0); val s = sin(w0)
    val alpha = s / 2 * sqrt(2.0)          // shelf slope S = 1
    val beta = 2 * sqrt(a) * alpha
    val a0 = (a + 1) - (a - 1) * c + beta
    return Biquad(
        a * ((a + 1) + (a - 1) * c + beta) / a0,
        -2 * a * ((a - 1) + (a + 1) * c) / a0,
        a * ((a + 1) + (a - 1) * c - beta) / a0,
        2 * ((a - 1) - (a + 1) * c) / a0,
        ((a + 1) - (a - 1) * c - beta) / a0,
    )
}

/** High-pass + high-shelf a pandeiro one-shot so it sits above the surdo. */
fun pandeiroEq(samples: FloatArray, sampleRate: Int): FloatArray =
    apply(apply(samples, highpass(HP_HZ, HP_Q, sampleRate.toDouble())), highshelf(SHELF_HZ, SHELF_DB, sampleRate.toDouble()))

package app.guitar.audio

import kotlin.math.abs

/** Public-domain "Freeverb" algorithmic reverb (Schroeder/Moorer): 8 parallel comb
 *  filters + 4 series allpass per channel, with a stereo spread. Adds `wet`·reverb to
 *  the dry signal in place. Pure — no Android API.
 *
 *  Tunings are the canonical Freeverb values, scaled from the original 44.1 kHz. */
class Freeverb(
    sampleRate: Int,
    roomSize: Float = 0.5f,
    damp: Float = 0.5f,
    private val wet: Float = 0.18f,
) {
    private val combTuning = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allpassTuning = intArrayOf(556, 441, 341, 225)
    private val stereoSpread = 23
    private val feedback = roomSize * 0.28f + 0.7f
    private val damp1 = damp * 0.4f
    private val damp2 = 1f - damp1

    private val scale = sampleRate / 44100f
    private fun s(x: Int) = (x * scale).toInt().coerceAtLeast(1)

    private inner class Comb(size: Int) {
        private val buf = FloatArray(size); private var idx = 0; private var filt = 0f
        fun tick(input: Float): Float {
            val out = buf[idx]
            filt = out * damp2 + filt * damp1
            buf[idx] = input + filt * feedback
            if (++idx >= buf.size) idx = 0
            return out
        }
        fun clear() { buf.fill(0f); idx = 0; filt = 0f }
    }
    private inner class Allpass(size: Int) {
        private val buf = FloatArray(size); private var idx = 0
        fun tick(input: Float): Float {
            val bufout = buf[idx]
            val out = -input + bufout
            buf[idx] = input + bufout * 0.5f
            if (++idx >= buf.size) idx = 0
            return out
        }
        fun clear() { buf.fill(0f); idx = 0 }
    }

    private val combL = Array(8) { Comb(s(combTuning[it])) }
    private val combR = Array(8) { Comb(s(combTuning[it] + stereoSpread)) }
    private val apL = Array(4) { Allpass(s(allpassTuning[it])) }
    private val apR = Array(4) { Allpass(s(allpassTuning[it] + stereoSpread)) }

    private var lastTail = 1f

    fun process(l: FloatArray, r: FloatArray, count: Int) {
        var tail = 0f
        for (i in 0 until count) {
            var input = (l[i] + r[i]) * 0.015f      // gain into the reverb
            if (!input.isFinite()) input = 0f       // never let NaN/Inf poison the feedback buffers
            var wl = 0f; var wr = 0f
            for (c in 0 until 8) { wl += combL[c].tick(input); wr += combR[c].tick(input) }
            for (a in 0 until 4) { wl = apL[a].tick(wl); wr = apR[a].tick(wr) }
            l[i] += wl * wet
            r[i] += wr * wet
            val e = abs(wl) + abs(wr); if (e > tail) tail = e
        }
        lastTail = tail
    }

    fun isRingingOut(threshold: Float = 1e-4f): Boolean = lastTail < threshold

    /** Flush all comb/allpass state so the current tail stops instantly — used to
     *  keep one chord's ambience from ringing over the next. */
    fun clear() {
        for (c in combL) c.clear(); for (c in combR) c.clear()
        for (a in apL) a.clear(); for (a in apR) a.clear()
        lastTail = 0f
    }
}

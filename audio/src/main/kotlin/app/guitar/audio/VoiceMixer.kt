package app.guitar.audio

/** One active voice in the mixer: a pull [source] plus per-voice controls. In M1
 *  only [gain] and [delayFrames] are used; [pan]/[envelope] arrive in M2/M3. */
class MixVoice(
    val source: VoiceSource,
    var gain: Float = 1f,
    delayFrames: Int = 0,
) {
    /** Frames still to wait before this voice starts sounding (mixer clock). */
    var remainingDelay: Int = delayFrames.coerceAtLeast(0)
}

/** Headless real-time mixer: sums active voices into stereo L/R blocks. No Android
 *  API — unit-testable on the JVM. In M1 it produces a plain mono sum (L == R).
 *  Master bus (pan/reverb/limiter) is layered on in M3–M5. */
class VoiceMixer(val sampleRate: Int) {
    private val voices = ArrayList<MixVoice>()
    private val scratch = FloatArray(4096)

    val activeCount: Int get() = voices.size

    @Synchronized fun add(v: MixVoice) { voices.add(v) }
    @Synchronized fun clear() { voices.clear() }

    /** Mix [count] frames. outL/outR must be >= count. */
    @Synchronized fun mixBlock(outL: FloatArray, outR: FloatArray, count: Int) {
        for (i in 0 until count) { outL[i] = 0f; outR[i] = 0f }
        val it = voices.iterator()
        while (it.hasNext()) {
            val v = it.next()
            var i = 0
            // Consume any scheduling delay first.
            if (v.remainingDelay > 0) {
                val d = minOf(v.remainingDelay, count)
                v.remainingDelay -= d
                i = d
            }
            while (i < count) {
                val want = minOf(count - i, scratch.size)
                val n = v.source.render(scratch, want)
                if (n <= 0) break
                for (j in 0 until n) {
                    val s = scratch[j] * v.gain
                    outL[i + j] += s
                    outR[i + j] += s
                }
                i += n
                if (n < want) break
            }
            if (v.source.isFinished && v.remainingDelay == 0) it.remove()
        }
    }
}

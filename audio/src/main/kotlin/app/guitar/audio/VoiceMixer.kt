package app.guitar.audio

/** One active voice in the mixer: a pull [source] plus per-voice controls. In M1
 *  only [gain] and [delayFrames] were used; M2 adds [envelope] (attack declick +
 *  release-based stop/steal). [pan] arrives in M3. */
class MixVoice(
    val source: VoiceSource,
    var gain: Float = 1f,
    delayFrames: Int = 0,
    val envelope: AmpEnvelope = AmpEnvelope(48000, attackMs = 0.0, releaseMs = 0.0),
) {
    /** Frames still to wait before this voice starts sounding (mixer clock). */
    var remainingDelay: Int = delayFrames.coerceAtLeast(0)
    /** Running peak of the last block, for quietest-voice stealing. */
    var lastPeak: Float = 0f
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

    /** Release every active voice (fade-out) instead of hard-cutting. Voices
     *  self-remove from [mixBlock] once their envelope reaches silence. */
    @Synchronized fun releaseAll() { for (v in voices) v.envelope.release() }

    @Synchronized fun capVoices(max: Int) {
        while (voices.size > max) {
            // Release the quietest (lowest recent peak) rather than hard-dropping.
            val quietest = voices.minByOrNull { it.lastPeak } ?: break
            quietest.envelope.release()
            // If still over cap because releases haven't finished, hard-remove the quietest
            // fully-released one; otherwise break to avoid dropping audible voices.
            val doneIdx = voices.indexOfFirst { it.envelope.isSilent }
            if (doneIdx >= 0) voices.removeAt(doneIdx) else break
        }
    }

    /** Add a voice and atomically trim to the cap in one synchronized block,
     *  preventing the output thread from briefly seeing voices over the max. */
    @Synchronized fun addAndCap(v: MixVoice, max: Int) {
        voices.add(v)
        capVoices(max)
    }

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
                v.envelope.applyInPlace(scratch, n)     // scratch now enveloped
                var peak = v.lastPeak
                for (j in 0 until n) {
                    val s = scratch[j] * v.gain
                    if (kotlin.math.abs(s) > peak) peak = kotlin.math.abs(s)
                    outL[i + j] += s
                    outR[i + j] += s
                }
                v.lastPeak = peak
                i += n
                if (n < want) break
            }
            if (v.envelope.isSilent || (v.source.isFinished && v.remainingDelay == 0)) it.remove()
        }
    }
}

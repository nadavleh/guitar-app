package app.guitar.audio

/** One active voice in the mixer: a pull [source] plus per-voice controls. In M1
 *  only [gain] and [delayFrames] were used; M2 adds [envelope] (attack declick +
 *  release-based stop/steal). [pan] arrives in M3: -1 = hard left, 0 = center,
 *  1 = hard right (constant-power via [Panner.gains]). */
class MixVoice(
    val source: VoiceSource,
    var gain: Float = 1f,
    delayFrames: Int = 0,
    val envelope: AmpEnvelope = AmpEnvelope(48000, attackMs = 0.0, releaseMs = 0.0),
    var pan: Double = 0.0,
    var reverbSend: Float = 0f,
) {
    /** Frames still to wait before this voice starts sounding (mixer clock). */
    var remainingDelay: Int = delayFrames.coerceAtLeast(0)
    /** Peak |post-gain sample| of the most recent block this voice contributed to,
     *  for quietest-voice stealing. Initialized high so a freshly-added voice (not
     *  yet mixed) is never chosen as the [VoiceMixer.capVoices] steal target. */
    var lastPeak: Float = Float.MAX_VALUE
}

/** Headless real-time mixer: sums active voices into stereo L/R blocks. No Android
 *  API — unit-testable on the JVM. Each voice is split into L/R via constant-power
 *  [Panner.gains] on [MixVoice.pan] (M3); center pan (0.0) still yields L == R.
 *  Remaining master-bus stages (reverb/limiter) are layered on in M4–M5. */
class VoiceMixer(val sampleRate: Int) {
    private val voices = ArrayList<MixVoice>()
    private val scratch = FloatArray(4096)
    private val limiter = SoftLimiter(sampleRate)
    private val freeverb = Freeverb(sampleRate)
    private val sendL = FloatArray(4096)
    private val sendR = FloatArray(4096)

    val activeCount: Int get() = voices.size

    /** True once every voice has finished AND the reverb send bus has decayed below
     *  its ringing-out threshold — used by the output loop to know it's safe to park
     *  without truncating a held-chord's reverb tail. */
    fun isRingingOut(): Boolean = voices.isEmpty() && freeverb.isRingingOut()

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
        for (i in 0 until count) { outL[i] = 0f; outR[i] = 0f; sendL[i] = 0f; sendR[i] = 0f }
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
            var produced = false
            var peak = 0f
            val (gL, gR) = Panner.gains(v.pan)
            while (i < count) {
                val want = minOf(count - i, scratch.size)
                val n = v.source.render(scratch, want)
                if (n <= 0) break
                produced = true
                v.envelope.applyInPlace(scratch, n)     // scratch now enveloped
                for (j in 0 until n) {
                    val s = scratch[j] * v.gain
                    val a = kotlin.math.abs(s); if (a > peak) peak = a
                    outL[i + j] += s * gL
                    outR[i + j] += s * gR
                    sendL[i + j] += s * gL * v.reverbSend
                    sendR[i + j] += s * gR * v.reverbSend
                }
                i += n
                if (n < want) break
            }
            if (produced) v.lastPeak = peak
            if (v.envelope.isSilent || (v.source.isFinished && v.remainingDelay == 0)) it.remove()
        }
        freeverb.process(sendL, sendR, count)
        for (i in 0 until count) { outL[i] += sendL[i]; outR[i] += sendR[i] }
        limiter.process(outL, outR, count)
    }
}

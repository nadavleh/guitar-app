package app.guitar.audio

/** One recorded pitch of a sampled instrument (mono float PCM at the engine rate). */
class GuitarSample(val rootMidi: Int, val samples: FloatArray)

/** A loaded multisample bank. [samples] are sorted by rootMidi at construction. */
class SampleInstrument(val id: String, samples: List<GuitarSample>) {
    private val sorted = samples.sortedBy { it.rootMidi }
    init { require(sorted.isNotEmpty()) { "instrument $id has no samples" } }

    /** Recorded pitch whose root is closest to [midi]; ties resolve to the lower root. */
    fun nearest(midi: Int): GuitarSample {
        var best = sorted[0]
        var bestDist = kotlin.math.abs(midi - best.rootMidi)
        for (i in 1 until sorted.size) {
            val d = kotlin.math.abs(midi - sorted[i].rootMidi)
            if (d < bestDist) { best = sorted[i]; bestDist = d }   // strict < keeps the lower root on ties
        }
        return best
    }
}

/** Pitch-shifts the nearest recorded pitch to [targetMidi] by playback-rate resampling
 *  (linear interpolation), pulled block-wise by the mixer. Mono. */
class SampleSource(inst: SampleInstrument, targetMidi: Int) : VoiceSource {
    private val root = inst.nearest(targetMidi)
    private val buf = root.samples
    private val rate = pitchRate(targetMidi, root.rootMidi)
    private var pos = 0.0

    override fun render(out: FloatArray, count: Int): Int {
        var produced = 0
        while (produced < count) {
            val i = pos.toInt()
            if (i >= buf.size - 1) {
                // Emit the final sample at most once, then finish. (Setting pos to the end
                // avoids re-emitting the tail on sub-unity rates, i.e. notes below the root.)
                if (i == buf.size - 1) out[produced++] = buf[buf.size - 1]
                pos = buf.size.toDouble()
                break
            }
            val frac = (pos - i).toFloat()
            out[produced++] = buf[i] + (buf[i + 1] - buf[i]) * frac
            pos += rate
        }
        return produced
    }

    override val isFinished: Boolean get() = pos.toInt() >= buf.size

    companion object {
        fun pitchRate(target: Int, root: Int): Double = Math.pow(2.0, (target - root) / 12.0)
    }
}

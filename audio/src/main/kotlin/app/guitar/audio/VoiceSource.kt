package app.guitar.audio

/** A pullable mono audio source. The mixer calls [render] block-wise. */
interface VoiceSource {
    /** Fill out[0 until count] with up to [count] mono samples; return the number
     *  actually produced (< count when the source runs dry). */
    fun render(out: FloatArray, count: Int): Int
    /** True once fully drained. */
    val isFinished: Boolean
}

/** A [VoiceSource] that replays a pre-rendered [samples] buffer once. Wraps both
 *  Karplus-Strong note output and cached percussion one-shots. */
class BufferSource(private val samples: FloatArray) : VoiceSource {
    private var pos = 0
    override fun render(out: FloatArray, count: Int): Int {
        val n = minOf(count, samples.size - pos)
        if (n <= 0) return 0
        System.arraycopy(samples, pos, out, 0, n)
        pos += n
        return n
    }
    override val isFinished: Boolean get() = pos >= samples.size
}

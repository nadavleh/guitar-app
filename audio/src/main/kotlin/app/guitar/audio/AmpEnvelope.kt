package app.guitar.audio

/** Per-voice amplitude gate: a short attack to declick the start, a unity sustain
 *  (the source owns the note's decay), and a release ramp triggered on stop/steal.
 *  Pure — no Android API. */
class AmpEnvelope(
    sampleRate: Int,
    attackMs: Double = 3.0,
    releaseMs: Double = 20.0,
) {
    private enum class Stage { ATTACK, SUSTAIN, RELEASE, DONE }
    private var stage = if (attackMs <= 0.0) Stage.SUSTAIN else Stage.ATTACK
    private val attackStep = if (attackMs <= 0.0) 1f else (1.0 / (sampleRate * attackMs / 1000.0)).toFloat()
    private val releaseStep = if (releaseMs <= 0.0) 1f else (1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()
    private var level = if (attackMs <= 0.0) 1f else 0f

    val isSilent: Boolean get() = stage == Stage.DONE

    fun release() { if (stage != Stage.DONE) stage = Stage.RELEASE }

    fun applyInPlace(buf: FloatArray, count: Int) {
        for (i in 0 until count) {
            when (stage) {
                Stage.ATTACK -> { level += attackStep; if (level >= 1f) { level = 1f; stage = Stage.SUSTAIN } }
                Stage.SUSTAIN -> { }
                Stage.RELEASE -> { level -= releaseStep; if (level <= 0f) { level = 0f; stage = Stage.DONE } }
                Stage.DONE -> level = 0f
            }
            buf[i] *= level
        }
    }
}

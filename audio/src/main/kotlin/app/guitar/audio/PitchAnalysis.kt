package app.guitar.audio

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

/**
 * Equal-tempered pitch math. The A4 reference is configurable (435-445 Hz is the
 * usual range) so the user can tune to anything from baroque pitch (A=415) up to
 * modern brilliant orchestras (A=445) without changing anything else.
 */
data class PitchEstimate(
    /** Detected fundamental in Hz (or the input freq). */
    val freqHz: Float,
    /** Nearest equal-tempered MIDI note, clamped to 0..127. */
    val midi: Int,
    /** Signed cents offset from the nearest note, in (-50, +50]. */
    val cents: Float,
)

object PitchAnalysis {

    /** Convert a frequency in Hz to a (nearest MIDI note, cents offset).
     *  cents is in (-50, +50]. */
    fun analyze(freqHz: Float, a4Hz: Float = 440f): PitchEstimate {
        require(freqHz > 0f) { "freq must be > 0, got $freqHz" }
        require(a4Hz > 0f) { "A4 reference must be > 0, got $a4Hz" }
        val midiFloat = 69f + 12f * (ln(freqHz / a4Hz) / LN2).toFloat()
        val midi = round(midiFloat).toInt()
        val cents = (midiFloat - midi) * 100f
        return PitchEstimate(freqHz, midi.coerceIn(0, 127), cents)
    }

    /** Inverse of [analyze]: equal-tempered frequency for the given MIDI note. */
    fun midiToFreq(midi: Int, a4Hz: Float = 440f): Float =
        a4Hz * 2f.pow((midi - 69) / 12f)

    private const val LN2 = 0.6931471805599453
}

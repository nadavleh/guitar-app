package app.guitar.audio

import app.guitar.theory.PercussionCatalog
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine now runs at the device's native output rate (48 kHz on modern hardware)
 * rather than a hardcoded 44.1 kHz, so every buffer producer must honour the rate it is
 * handed. A producer that ignores it yields buffers that are ~8.8% sharp and short when
 * the engine is at 48 kHz — which is exactly the class of bug this locks out.
 */
class SampleRateAgnosticTest {

    private val ratio = 48000.0 / 44100.0

    @Test
    fun `plucked notes keep their duration and pitch across engine rates`() {
        val durationSec = 0.5
        val at441 = PluckedSynth(44100).synthesize(midiNote = 69, durationSec = durationSec)
        val at480 = PluckedSynth(48000).synthesize(midiNote = 69, durationSec = durationSec)

        // Same musical length -> sample counts differ by exactly the rate ratio.
        assertEquals(44100 / 2, at441.size)
        assertEquals(48000 / 2, at480.size)
        assertTrue(abs(at480.size / at441.size.toDouble() - ratio) < 0.01)

        // Same musical pitch: A4 = 440 Hz at either rate. Karplus-Strong's delay line is
        // round(rate / freq), so the period in SAMPLES must scale with the rate.
        assertEquals(440.0, PitchDetector(sampleRate = 44100).detect(at441)?.toDouble() ?: 0.0, 4.0)
        assertEquals(440.0, PitchDetector(sampleRate = 48000).detect(at480)?.toDouble() ?: 0.0, 4.0)
    }

    @Test
    fun `percussion one-shots keep their duration across engine rates`() {
        val lo = PercussionSynth(44100)
        val hi = PercussionSynth(48000)
        for (inst in PercussionCatalog.ALL) {
            for (v in 0 until inst.voiceCount) {
                val a = lo.synthesize(inst, v).size
                val b = hi.synthesize(inst, v).size
                assertTrue(a > 0 && b > 0, "${inst.id} voice $v empty")
                // Allow a sample or two of rounding, but the ratio must track the rates —
                // an identical length would mean the rate was ignored.
                assertTrue(abs(b / a.toDouble() - ratio) < 0.02,
                    "${inst.id} voice $v ignored its sample rate ($a -> $b)")
            }
        }
    }

    @Test
    fun `the silent engine reports a usable rate so callers never divide by zero`() {
        assertTrue(AudioEngine.Silent.sampleRate >= 8000)
        assertEquals(48000, AudioRates.FALLBACK_RATE)
    }
}

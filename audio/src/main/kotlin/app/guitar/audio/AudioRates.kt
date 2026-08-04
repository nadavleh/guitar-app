package app.guitar.audio

import android.content.Context
import android.media.AudioManager

/**
 * The device's native audio-output parameters.
 *
 * Android only grants the low-latency ("FAST") output path to a track that needs no
 * resampling — i.e. one whose sample rate already matches the device's native output
 * rate, which is 48 kHz on essentially all modern hardware. Running the engine at
 * 44.1 kHz therefore had `PERFORMANCE_MODE_LOW_LATENCY` silently denied and pushed
 * every note through AudioFlinger's resampler on the normal mixer path, costing a
 * constant ~20-40 ms on top of whatever else was going on.
 *
 * Everything downstream (synthesis, sample decoding, sequencer frame math) derives its
 * rate from [AudioEngine.sampleRate], so the engine is the single source of truth and
 * these values only need to be read once, at construction.
 */
object AudioRates {

    /** Used when the device won't report its rate. 48 kHz is the modern norm. */
    const val FALLBACK_RATE = 48000

    /** Used when the device won't report its burst size. 192 frames = 4 ms at 48 kHz. */
    const val FALLBACK_FRAMES_PER_BUFFER = 192

    /** Native output sample rate in Hz. */
    fun outputRate(context: Context): Int =
        property(context, AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.takeIf { it in 8_000..192_000 } ?: FALLBACK_RATE

    /** Native output burst size in frames — the quantum the audio HAL consumes.
     *  Writing in this size keeps the output thread aligned with the HAL. */
    fun framesPerBuffer(context: Context): Int =
        property(context, AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.takeIf { it in 16..8_192 } ?: FALLBACK_FRAMES_PER_BUFFER

    private fun property(context: Context, key: String): Int? =
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            ?.getProperty(key)?.toIntOrNull()
}

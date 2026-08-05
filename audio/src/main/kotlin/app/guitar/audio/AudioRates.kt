package app.guitar.audio

import android.content.Context
import android.media.AudioDeviceInfo
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

    /**
     * Where audio is currently being routed, and whether that route is inherently
     * high-latency.
     *
     * This matters more than anything the engine does: Bluetooth A2DP buffers 150-400 ms
     * inside the receiving device, entirely downstream of the app, the HAL and the DSP. No
     * amount of engine tuning removes it — the only fix is wired output or the phone speaker.
     */
    fun outputRoute(context: Context): OutputRoute {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return OutputRoute("unknown", highLatency = false)
        // API 31+ can report the route actually selected for media playback.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val type = runCatching { am.getAudioDevicesForAttributes(attrs).firstOrNull()?.type }
                .getOrNull()
            if (type != null) return describe(type)
        }
        // Older devices: no "current route" API, so infer from what's switched on.
        @Suppress("DEPRECATION")
        return when {
            am.isBluetoothA2dpOn -> OutputRoute("Bluetooth (A2DP)", highLatency = true)
            am.isWiredHeadsetOn -> OutputRoute("wired headset", highLatency = false)
            else -> OutputRoute("phone speaker", highLatency = false)
        }
    }

    private fun describe(type: Int): OutputRoute = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputRoute("Bluetooth (A2DP)", highLatency = true)
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> OutputRoute("Bluetooth (SCO)", highLatency = true)
        AudioDeviceInfo.TYPE_HEARING_AID -> OutputRoute("hearing aid", highLatency = true)
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST ->
            OutputRoute("Bluetooth LE", highLatency = true)
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputRoute("phone speaker", highLatency = false)
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET ->
            OutputRoute("wired headphones", highLatency = false)
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE ->
            OutputRoute("USB audio", highLatency = false)
        else -> OutputRoute("output type $type", highLatency = false)
    }
}

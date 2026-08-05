package app.guitar.audio

/**
 * A snapshot of everything on the engine side that governs touch-to-sound delay.
 *
 * Exists so "it still feels late" can be answered with numbers. Note what is NOT here and
 * cannot be: the delay added by the audio HAL, the DSP, and the output device itself. A
 * Bluetooth speaker adds 150-400 ms downstream of everything below — see
 * [AudioRates.outputRoute].
 */
data class AudioLatencyReport(
    val sampleRate: Int,
    /** The HAL's burst size in frames — the hardware's own quantum. */
    val halBurstFrames: Int,
    /** Ring buffer actually allocated (the floor the platform would give us). */
    val allocatedBufferFrames: Int,
    /** Depth we actually queue to — this is the engine's steady output latency. */
    val effectiveBufferFrames: Int,
    /** Lifetime underrun count; > 0 means the queue had to grow, trading latency for glitch-free playback. */
    val underruns: Int,
    /** False = the stream is parked, so the next note additionally pays HAL warm-up. */
    val outputWarm: Boolean,
    /** How long the last note waited for the synthesis thread, in ms (-1 = none yet). */
    val lastNoteQueueMs: Long,
    /** How long the last note took to synthesize, in ms (-1 = none yet). */
    val lastNoteSynthMs: Long,
) {
    /** Steady engine-side output latency in ms: what the queue depth costs. */
    val bufferMs: Double get() = effectiveBufferFrames * 1000.0 / sampleRate

    /** One burst in ms — the theoretical floor for this device. */
    val burstMs: Double get() = halBurstFrames * 1000.0 / sampleRate
}

/** Where audio is currently going, and whether that route is inherently high-latency. */
data class OutputRoute(
    val label: String,
    /** True for routes that add delay no app-side change can remove (Bluetooth, hearing aids).
     *  A2DP alone is typically 150-400 ms. */
    val highLatency: Boolean,
)

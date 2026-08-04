package app.guitar.audio

/**
 * Produce the next block of interleaved stereo PCM for the output thread: the mixed
 * voices, or silence when nothing is sounding.
 *
 * Always fills `frames * 2` shorts of [chunk], so the output thread can keep feeding the
 * AudioTrack while idle — see the keep-warm note in AudioTrackEngine's output loop.
 * When idle it emits zeros directly instead of running the reverb / EQ / limiter chain
 * over silence, so staying warm costs almost no CPU.
 *
 * Returns true when the block carries audio (i.e. voices were mixed).
 *
 * Free function with no Android dependency so the pacing contract stays JVM-testable.
 */
internal fun nextOutputBlock(
    mixer: VoiceMixer,
    l: FloatArray,
    r: FloatArray,
    chunk: ShortArray,
    frames: Int,
): Boolean {
    if (mixer.activeCount == 0 && mixer.isRingingOut()) {
        java.util.Arrays.fill(chunk, 0, frames * 2, 0)
        return false
    }
    mixer.mixBlock(l, r, frames)
    for (i in 0 until frames) {
        chunk[2 * i] = (l[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        chunk[2 * i + 1] = (r[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }
    return true
}

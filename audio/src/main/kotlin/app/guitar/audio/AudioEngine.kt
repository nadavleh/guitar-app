package app.guitar.audio

interface AudioEngine {
    /**
     * Play a single MIDI note. Non-blocking; replaces any currently-playing note.
     * No-op if [midiNote] is outside 0..127.
     */
    fun playNote(midiNote: Int, durationMillis: Int = 1500, timbre: Timbre = Timbre.Guitar)

    /**
     * Play a single tone at an arbitrary frequency. Useful for the tuner where
     * the user's A4 reference may not be exactly 440 Hz. No-op if [freqHz] <= 0.
     */
    fun playFrequency(freqHz: Float, durationMillis: Int = 1500, timbre: Timbre = Timbre.Guitar)

    /**
     * Play a list of MIDI notes as a strummed chord. Each note is delayed by
     * [strumDelayMillis] from the previous; all notes sustain [sustainMillis].
     * The notes are pre-mixed into a single buffer so they ring polyphonically.
     */
    fun playChord(midiNotes: List<Int>, strumDelayMillis: Int = 40, sustainMillis: Int = 2000, timbre: Timbre = Timbre.Guitar)

    /**
     * Play a pre-synthesized one-shot mono buffer (samples in [-1, 1] at the
     * engine's sample rate). Mixed polyphonically with everything else, like a
     * pluck. Used by the percussion looper, which renders its voices once and
     * replays the cached buffers. No-op if [samples] is empty.
     *
     * [gain] scales the buffer's amplitude at mix time (1f = unchanged), so a
     * per-instrument volume can be applied without mutating the cached buffer.
     */
    fun playSamples(samples: FloatArray, gain: Float = 1f)

    /** Stop any currently-playing audio immediately. */
    fun stop()

    /** Release all audio resources. Must be called when the engine is no longer needed. */
    fun close()

    companion object {
        /** A no-op engine for previews and tests. */
        val Silent: AudioEngine = object : AudioEngine {
            override fun playNote(midiNote: Int, durationMillis: Int, timbre: Timbre) {}
            override fun playFrequency(freqHz: Float, durationMillis: Int, timbre: Timbre) {}
            override fun playChord(midiNotes: List<Int>, strumDelayMillis: Int, sustainMillis: Int, timbre: Timbre) {}
            override fun playSamples(samples: FloatArray, gain: Float) {}
            override fun stop() {}
            override fun close() {}
        }
    }
}

package app.guitar.audio

interface AudioEngine {
    /**
     * Play a single MIDI note. Non-blocking; replaces any currently-playing note.
     * No-op if [midiNote] is outside 0..127.
     */
    fun playNote(midiNote: Int, durationMillis: Int = 1500)

    /**
     * Play a single tone at an arbitrary frequency. Useful for the tuner where
     * the user's A4 reference may not be exactly 440 Hz. No-op if [freqHz] <= 0.
     */
    fun playFrequency(freqHz: Float, durationMillis: Int = 1500)

    /**
     * Play a list of MIDI notes as a strummed chord. Each note is delayed by
     * [strumDelayMillis] from the previous; all notes sustain [sustainMillis].
     * The notes are pre-mixed into a single buffer so they ring polyphonically.
     */
    fun playChord(midiNotes: List<Int>, strumDelayMillis: Int = 40, sustainMillis: Int = 2000)

    /** Stop any currently-playing audio immediately. */
    fun stop()

    /** Release all audio resources. Must be called when the engine is no longer needed. */
    fun close()

    companion object {
        /** A no-op engine for previews and tests. */
        val Silent: AudioEngine = object : AudioEngine {
            override fun playNote(midiNote: Int, durationMillis: Int) {}
            override fun playFrequency(freqHz: Float, durationMillis: Int) {}
            override fun playChord(midiNotes: List<Int>, strumDelayMillis: Int, sustainMillis: Int) {}
            override fun stop() {}
            override fun close() {}
        }
    }
}

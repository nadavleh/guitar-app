package app.guitar.audio

interface AudioEngine {
    /**
     * Frames per second this engine renders at — the device's native output rate, so
     * Android can grant the low-latency path instead of inserting a resampler.
     *
     * Everything that hands the engine a buffer or a frame count must derive it from
     * here rather than assuming a constant: buffers passed to [playSamples] are
     * interpreted at this rate, and [playSamplesAt]'s `delayFrames` is counted in it.
     * Assuming 44.1 kHz while the engine runs at 48 kHz makes samples play ~8.8% sharp
     * and sequencers run ~8.8% fast.
     */
    val sampleRate: Int

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
     *
     * [bassBoost] (0 = off) emphasizes the low strings: the lowest-pitched note is
     * scaled by (1 + bassBoost), tapering linearly to no boost at the highest note,
     * so a voicing's low end sits fuller without a separate root-emphasis pass.
     */
    fun playChord(midiNotes: List<Int>, strumDelayMillis: Int = 40, sustainMillis: Int = 2000, timbre: Timbre = Timbre.Guitar, bassBoost: Float = 0f)

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

    /**
     * Like [playSamples], but the buffer starts sounding [delayFrames] engine
     * frames after insertion, counted on the MIXER's own clock — sample-accurate
     * lookahead scheduling for sequencers. The pending voice keeps the output
     * loop running, so the countdown never pauses. Default: immediate.
     *
     * [chokeKey]: self-choke group — a new voice with the same key fades the
     * previous one out at its own onset (a pandeiro hand damps the old stroke
     * when the new one lands). Null = voices ring freely (default).
     */
    fun playSamplesAt(samples: FloatArray, gain: Float = 1f, delayFrames: Int = 0, chokeKey: String? = null) =
        playSamples(samples, gain)

    /** Stop any currently-playing audio immediately. */
    fun stop()

    /** Flush the reverb tail so a previous chord's ambience doesn't ring on top of
     *  the next one. Default no-op for engines without a reverb bus. */
    fun cutReverb() {}

    /** Release all audio resources. Must be called when the engine is no longer needed. */
    fun close()

    companion object {
        /** A no-op engine for previews and tests. */
        val Silent: AudioEngine = object : AudioEngine {
            override val sampleRate = AudioRates.FALLBACK_RATE
            override fun playNote(midiNote: Int, durationMillis: Int, timbre: Timbre) {}
            override fun playFrequency(freqHz: Float, durationMillis: Int, timbre: Timbre) {}
            override fun playChord(midiNotes: List<Int>, strumDelayMillis: Int, sustainMillis: Int, timbre: Timbre, bassBoost: Float) {}
            override fun playSamples(samples: FloatArray, gain: Float) {}
            override fun stop() {}
            override fun close() {}
        }
    }
}

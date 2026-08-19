package app.guitar.audio

/**
 * A/B test scaffolding: forwards every [AudioEngine] call to either the [modern]
 * voice-graph engine or the [legacy] pre-overhaul engine, chosen by [useModern] at
 * call time. Lets the app switch engines live so the two can be compared by ear.
 *
 * Both engines are constructed up front and kept alive; only one produces sound at a
 * time (the inactive one has no active voices). Switching stops BOTH so nothing from
 * the previous engine keeps ringing across the change.
 *
 * TEMPORARY — remove (along with [LegacyAudioTrackEngine]) before shipping the overhaul.
 */
class SwitchableAudioEngine(
    private val modern: AudioTrackEngine,
    private val legacy: AudioEngine,
) : AudioEngine {

    /** Both engines are built at the device's native rate, so cached sample buffers
     *  stay valid across an A/B switch. Reported from the modern engine. */
    override val sampleRate: Int get() = modern.sampleRate

    /** The modern voice-graph engine, exposed so callers can reach engine-specific
     *  knobs (e.g. [AudioTrackEngine.voiceInstrument]) that aren't part of the
     *  generic [AudioEngine] surface. */
    val modernEngine: AudioTrackEngine get() = modern

    @Volatile
    var useModern: Boolean = true
        private set

    /** Switch the active engine. No-op if unchanged; otherwise stops both first. */
    fun setUseModern(value: Boolean) {
        if (value == useModern) return
        modern.stop()
        legacy.stop()
        useModern = value
    }

    private val active: AudioEngine get() = if (useModern) modern else legacy

    override fun playNote(midiNote: Int, durationMillis: Int, timbre: Timbre) =
        active.playNote(midiNote, durationMillis, timbre)

    override fun playFrequency(freqHz: Float, durationMillis: Int, timbre: Timbre) =
        active.playFrequency(freqHz, durationMillis, timbre)

    override fun playChord(midiNotes: List<Int>, strumDelayMillis: Int, sustainMillis: Int, timbre: Timbre, bassBoost: Float) =
        active.playChord(midiNotes, strumDelayMillis, sustainMillis, timbre, bassBoost)

    override fun playSamples(samples: FloatArray, gain: Float) =
        active.playSamples(samples, gain)

    override fun playSamplesAt(samples: FloatArray, gain: Float, delayFrames: Int, chokeKey: String?) =
        active.playSamplesAt(samples, gain, delayFrames, chokeKey)

    /** Stop BOTH engines — safe regardless of which is active. */
    override fun stop() {
        modern.stop()
        legacy.stop()
    }

    override fun cutReverb() = active.cutReverb()

    /** Only the modern engine layers voices per note, so only it can leave a chord
     *  ringing under the next one; the legacy engine renders a whole chord as one
     *  buffer that already honours `sustainMillis`, so its no-op default is correct. */
    override fun chokeChords() = active.chokeChords()

    override fun close() {
        modern.close()
        legacy.close()
    }
}

package app.guitar.audio

/**
 * Tone shaping parameters for [PluckedSynth] — lets callers play guitar-like
 * notes or cavaquinho-like (brighter, shorter decay, slightly quieter) notes
 * without having to know the synth internals.
 */
data class Timbre(
    /** Karplus-Strong damping per sample. Closer to 1.0 = longer sustain.
     *  Below ~0.99 the tone gets noticeably brighter / quicker-decaying. */
    val damping: Double = 0.997,
    /** Peak amplitude in [0, 1]. */
    val amplitude: Double = 0.6,
    /** Stereo position: -1 = hard left, 0 = center, 1 = hard right. */
    val pan: Double = 0.0,
    /** Fraction of this voice's signal sent to the reverb bus, in [0, 1]. */
    val reverbSend: Double = 0.18,
    /** Envelope release time in milliseconds when the voice is stopped/stolen. */
    val releaseMs: Int = 20,
) {
    companion object {
        /** Default — bronze-wound + plain steel, long sustain. */
        val Guitar = Timbre(damping = 0.997, amplitude = 0.6, reverbSend = 0.18)
        /** Smaller body, nylon/steel strings, brighter ping with quicker decay. */
        val Cavaquinho = Timbre(damping = 0.989, amplitude = 0.55, reverbSend = 0.12)
        /** Ear-training chords: full sustain with a bit more level for body. Chord-tone
         *  clarity now comes from the improved synth (pluck-comb harmonics, rounded
         *  intonation, low-end blend) rather than from brightening, which thinned the bottom. */
        val Clarity = Timbre(damping = 0.997, amplitude = 0.62, reverbSend = 0.15)
    }
}

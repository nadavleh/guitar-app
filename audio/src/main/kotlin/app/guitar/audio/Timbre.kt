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
    val reverbSend: Double = 0.03,
    /** Envelope release time in milliseconds when the voice is stopped/stolen. */
    val releaseMs: Int = 20,
    /** Short and dry: the voice is released after its duration even when it is a sample
     *  (samples otherwise ring to their natural end), sends nothing to the reverb, and
     *  the synth's high harmonics die faster for a rounder, softer tone. */
    val gated: Boolean = false,
) {
    companion object {
        /** Default — bronze-wound + plain steel, long sustain. */
        val Guitar = Timbre(damping = 0.997, amplitude = 0.6, reverbSend = 0.03)
        /** Smaller body, nylon/steel strings, brighter ping with quicker decay. */
        val Cavaquinho = Timbre(damping = 0.989, amplitude = 0.55, reverbSend = 0.03)
        /** Ear-training chords: full sustain with a bit more level for body. Chord-tone
         *  clarity now comes from the improved synth (pluck-comb harmonics, rounded
         *  intonation, low-end blend) rather than from brightening, which thinned the bottom. */
        val Clarity = Timbre(damping = 0.997, amplitude = 0.62, reverbSend = 0.03)
        /** Interval trainer + song snippets: soft, short, no reverb — every note ends
         *  where the next begins, so a leap is heard as two clean pitches. */
        val Soft = Timbre(damping = 0.994, amplitude = 0.42, reverbSend = 0.0, releaseMs = 70, gated = true)
    }
}

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
) {
    companion object {
        /** Default — bronze-wound + plain steel, long sustain. */
        val Guitar = Timbre(damping = 0.997, amplitude = 0.6)
        /** Smaller body, nylon/steel strings, brighter ping with quicker decay. */
        val Cavaquinho = Timbre(damping = 0.989, amplitude = 0.55)
        /** Ear-training chords: full sustain with a bit more level for body. Chord-tone
         *  clarity now comes from the improved synth (pluck-comb harmonics, rounded
         *  intonation, low-end blend) rather than from brightening, which thinned the bottom. */
        val Clarity = Timbre(damping = 0.997, amplitude = 0.62)
    }
}

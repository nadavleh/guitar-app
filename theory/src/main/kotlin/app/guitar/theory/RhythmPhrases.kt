package app.guitar.theory

/** One click of a phrase: its global 16th-slot index and whether it's a bar-downbeat
 *  accent. */
data class PhraseOnset(val slot: Int, val accent: Boolean)

/**
 * A multi-bar rhythmic phrase: [beats] one-beat units (each subdivision 4) laid out
 * as [bars] bars of [beatsPerBar] beats (time signature beatsPerBar/4). Because every
 * beat is a 16th-grid unit, the whole phrase maps onto a clean 16th grid.
 */
data class RhythmPhrase(
    val bars: Int,
    val beatsPerBar: Int,
    val beats: List<RhythmUnit>,
) {
    init {
        require(beats.size == bars * beatsPerBar) { "phrase must have bars*beatsPerBar beats" }
        require(beats.all { it.subdivision == RhythmPhrases.SLOTS_PER_BEAT }) {
            "phrase beats must be 16th-grid units (subdivision ${RhythmPhrases.SLOTS_PER_BEAT})"
        }
    }

    val slotsPerBeat: Int get() = RhythmPhrases.SLOTS_PER_BEAT
    val totalSlots: Int get() = beats.size * slotsPerBeat
    val slotsPerBar: Int get() = beatsPerBar * slotsPerBeat

    /** Global 16th-slot of every click, accented on bar downbeats that carry an onset. */
    fun onsets(): List<PhraseOnset> {
        val out = ArrayList<PhraseOnset>()
        beats.forEachIndexed { b, unit ->
            val isBarStart = b % beatsPerBar == 0
            for (f in unit.clickFractions()) {
                val local = (f * slotsPerBeat).toInt()
                out.add(PhraseOnset(b * slotsPerBeat + local, accent = isBarStart && local == 0))
            }
        }
        return out
    }
}

object RhythmPhrases {
    const val SLOTS_PER_BEAT = 4   // sixteenth grid

    val MIN_BARS = 1
    val MAX_BARS = 4
    val TIME_SIGNATURES = listOf(2, 3, 4)   // N/4

    /** Units a phrase can be built from: the 16th-grid units (no triplet) + rests. */
    val POOL: List<RhythmUnit> = RhythmUnits.ALL.filter { it.subdivision == SLOTS_PER_BEAT } + RhythmUnits.RESTS

    fun generatePhrase(bars: Int, beatsPerBar: Int, rng: kotlin.random.Random): RhythmPhrase {
        val b = bars.coerceIn(MIN_BARS, MAX_BARS)
        val bpb = if (beatsPerBar in TIME_SIGNATURES) beatsPerBar else 2
        val beats = List(b * bpb) { POOL[rng.nextInt(POOL.size)] }
        return RhythmPhrase(b, bpb, beats)
    }
}

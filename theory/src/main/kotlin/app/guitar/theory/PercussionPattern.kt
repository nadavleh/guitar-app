package app.guitar.theory

/** 16 sixteenth-note slots = 2 bars of 2/4 (each beat = 4 sixteenths). */
const val PERCUSSION_SLOTS = 16

/**
 * A percussion loop grid. For each instrument there is a list of
 * [PERCUSSION_SLOTS] cells; a cell is either `null` (silent) or a 0-based voice
 * index for that instrument.
 *
 * Immutable — every mutation returns a new pattern (Compose-friendly).
 */
data class PercussionPattern(
    val grid: Map<PercussionInstrument, List<Int?>>,
) {
    init {
        require(grid.keys.containsAll(PercussionInstrument.entries.toSet())) {
            "pattern must cover every instrument; missing ${PercussionInstrument.entries - grid.keys}"
        }
        grid.forEach { (inst, row) ->
            require(row.size == PERCUSSION_SLOTS) {
                "$inst row must have $PERCUSSION_SLOTS slots, got ${row.size}"
            }
            row.forEach { v ->
                require(v == null || v in 0 until PercussionVoices.voiceCount(inst)) {
                    "$inst has out-of-range voice index $v"
                }
            }
        }
    }

    fun voiceAt(instrument: PercussionInstrument, slot: Int): Int? = grid.getValue(instrument)[slot]

    /**
     * Advance a cell one step in the cycle:
     * `null → 0 → 1 → … → (voiceCount-1) → null`.
     */
    fun cycled(instrument: PercussionInstrument, slot: Int): PercussionPattern {
        require(slot in 0 until PERCUSSION_SLOTS)
        val count = PercussionVoices.voiceCount(instrument)
        val cur = grid.getValue(instrument)[slot]
        val next = when {
            cur == null -> 0
            cur >= count - 1 -> null
            else -> cur + 1
        }
        return withCell(instrument, slot, next)
    }

    fun withCell(instrument: PercussionInstrument, slot: Int, voice: Int?): PercussionPattern {
        val newRow = grid.getValue(instrument).toMutableList().also { it[slot] = voice }
        return copy(grid = grid + (instrument to newRow))
    }

    fun clearedRow(instrument: PercussionInstrument): PercussionPattern =
        copy(grid = grid + (instrument to List(PERCUSSION_SLOTS) { null }))

    fun isEmpty(): Boolean = grid.values.all { row -> row.all { it == null } }

    companion object {
        fun empty(): PercussionPattern = PercussionPattern(
            PercussionInstrument.entries.associateWith { List(PERCUSSION_SLOTS) { null } }
        )

        /**
         * A basic samba groove over 2 bars of 2/4.
         *  - Surdo: muffled on each bar downbeat (0, 8), open accent on the "2" (4, 12).
         *  - Tamborim: the teleco-teco — a syncopated open ostinato.
         *  - Pandeiro: continuous sixteenths, low/high/mute/high repeating.
         *  - Agogô: low/high alternating bell figure.
         */
        val SAMBA: PercussionPattern = run {
            val surdo = arrayOfNulls<Int>(PERCUSSION_SLOTS).also {
                it[0] = 1; it[4] = 0; it[8] = 1; it[12] = 0
            }.toList()
            val tamborim = arrayOfNulls<Int>(PERCUSSION_SLOTS).also {
                // open hits with a couple of mutes for the choke feel
                it[0] = 0; it[3] = 0; it[4] = 1; it[6] = 0
                it[8] = 0; it[11] = 0; it[12] = 1; it[14] = 0
            }.toList()
            val pandeiro = (0 until PERCUSSION_SLOTS).map { i ->
                when (i % 4) {
                    0 -> 0   // low (slap)
                    2 -> 2   // mute (tap)
                    else -> 1 // high (open)
                }
            }
            val agogo = arrayOfNulls<Int>(PERCUSSION_SLOTS).also {
                it[0] = 0; it[2] = 1; it[3] = 1; it[6] = 0
                it[8] = 0; it[10] = 1; it[11] = 1; it[14] = 0
            }.toList()
            PercussionPattern(
                mapOf(
                    PercussionInstrument.Surdo to surdo,
                    PercussionInstrument.Tamborim to tamborim,
                    PercussionInstrument.Pandeiro to pandeiro,
                    PercussionInstrument.Agogo to agogo,
                )
            )
        }
    }
}

/** Loop timing helpers (kept pure so they're unit-testable on the JVM). */
object PercussionTiming {
    /** Milliseconds per sixteenth-note slot at [bpm] (a quarter-note = 4 sixteenths). */
    fun slotMs(bpm: Int): Long = (60_000L / bpm.coerceAtLeast(20)) / 4

    /** Total loop length in milliseconds. */
    fun loopMs(bpm: Int): Long = slotMs(bpm) * PERCUSSION_SLOTS
}

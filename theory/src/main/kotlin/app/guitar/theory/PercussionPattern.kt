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

    /**
     * Serialize to a compact string for persistence: one row per instrument (in
     * enum order), cells comma-separated, silent = "-", rows joined by "|".
     * e.g. "1,-,-,...|0,...|...|...". Round-trips via [decode].
     */
    fun encode(): String =
        PercussionInstrument.entries.joinToString("|") { inst ->
            grid.getValue(inst).joinToString(",") { it?.toString() ?: "-" }
        }

    companion object {
        fun empty(): PercussionPattern = PercussionPattern(
            PercussionInstrument.entries.associateWith { List(PERCUSSION_SLOTS) { null } }
        )

        /** Parse a string produced by [encode]; null if malformed or out of range. */
        fun decode(s: String): PercussionPattern? {
            val rows = s.split("|")
            if (rows.size != PercussionInstrument.entries.size) return null
            val grid = HashMap<PercussionInstrument, List<Int?>>()
            for ((idx, inst) in PercussionInstrument.entries.withIndex()) {
                val cells = rows[idx].split(",")
                if (cells.size != PERCUSSION_SLOTS) return null
                val row = cells.map { c -> if (c == "-") null else c.toIntOrNull() ?: return null }
                if (row.any { it != null && it !in 0 until PercussionVoices.voiceCount(inst) }) return null
                grid[inst] = row
            }
            return runCatching { PercussionPattern(grid) }.getOrNull()
        }

        /**
         * The built-in "stock samba" groove over 2 bars of 2/4.
         *  - Surdo: muted bass on each bar downbeat (0, 8), ringing accent on the "2" (4, 12).
         *  - Tamborim: the teleco-teco — a syncopated clack ostinato with choked mutes.
         *  - Pandeiro: continuous sixteenths — bass / jingle / slap / jingle-hi.
         *  - Agogô: low/high alternating bell figure.
         */
        val SAMBA: PercussionPattern = run {
            // Surdo voices: 0 open(ring), 1 muted bass, 2 tap.
            val surdo = arrayOfNulls<Int>(PERCUSSION_SLOTS).also {
                it[0] = 1; it[4] = 0; it[8] = 1; it[12] = 0
            }.toList()
            // Tamborim voices: 0 clack, 1 muted clack, 2 tap.
            val tamborim = arrayOfNulls<Int>(PERCUSSION_SLOTS).also {
                it[0] = 0; it[3] = 0; it[4] = 1; it[6] = 0
                it[8] = 0; it[11] = 0; it[12] = 1; it[14] = 0
            }.toList()
            // Pandeiro voices: 0 bass(open), 1 bass(muted), 2 slap, 3 jingle, 4 jingle hi.
            val pandeiro = (0 until PERCUSSION_SLOTS).map { i ->
                when (i % 4) {
                    0 -> 0   // bass (open) on the beat
                    1 -> 3   // jingle
                    2 -> 2   // slap
                    else -> 4 // jingle hi
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

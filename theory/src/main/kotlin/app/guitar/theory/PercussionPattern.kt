package app.guitar.theory

/** Slot count of the default meter (2 bars of 2/4 in sixteenths = 16). Kept as a
 *  named constant for the stock samba / empty patterns and the unit tests. */
const val PERCUSSION_SLOTS = 16

/**
 * Time grid of a percussion loop: [bars] of [beatsPerBar]/[beatUnit] (the time
 * signature), each beat subdivided into [division]-note slots.
 *
 *   slotsPerBeat = division / beatUnit   (e.g. 1/16 slots in 2/4 -> 16/4 = 4)
 *   slotsPerBar  = beatsPerBar * slotsPerBeat
 *   totalSlots   = bars * slotsPerBar
 *
 * [division] must be an integer multiple of [beatUnit] (you can't subdivide a
 * beat into a coarser value than the beat itself).
 */
data class PercussionMeter(
    val bars: Int = 2,
    val beatsPerBar: Int = 2,
    val beatUnit: Int = 4,
    val division: Int = 16,
) {
    init {
        require(bars in 1..8) { "bars must be 1..8, got $bars" }
        require(beatsPerBar in 1..12) { "beatsPerBar must be 1..12, got $beatsPerBar" }
        require(beatUnit in BEAT_UNITS) { "beatUnit must be one of $BEAT_UNITS, got $beatUnit" }
        require(division in DIVISIONS) { "division must be one of $DIVISIONS, got $division" }
        require(division % beatUnit == 0) { "division ($division) must be a multiple of beatUnit ($beatUnit)" }
    }

    val slotsPerBeat: Int get() = division / beatUnit
    val slotsPerBar: Int get() = beatsPerBar * slotsPerBeat
    val totalSlots: Int get() = bars * slotsPerBar

    /** "2 bars · 2/4 · 1/16" style summary for captions. */
    fun describe(): String = "$bars bar${if (bars == 1) "" else "s"} · $beatsPerBar/$beatUnit · 1/$division"

    companion object {
        val BEAT_UNITS = listOf(2, 4, 8)
        val DIVISIONS = listOf(4, 8, 16, 32)
        val DEFAULT = PercussionMeter()   // 2 bars of 2/4 in sixteenths → 16 slots
    }
}

/**
 * A percussion loop grid. For each instrument there is a list of cells (one per
 * slot of [meter]); a cell is either `null` (silent) or a 0-based voice index for
 * that instrument. The slot count is [PercussionMeter.totalSlots], so it varies
 * with the chosen bars / time signature / division.
 *
 * Immutable — every mutation returns a new pattern (Compose-friendly).
 */
data class PercussionPattern(
    val grid: Map<PercussionInstrument, List<Int?>>,
    val meter: PercussionMeter = PercussionMeter.DEFAULT,
) {
    /** Number of slots in this pattern (= meter.totalSlots). */
    val slots: Int get() = meter.totalSlots

    init {
        require(grid.keys.containsAll(PercussionInstrument.entries.toSet())) {
            "pattern must cover every instrument; missing ${PercussionInstrument.entries - grid.keys}"
        }
        grid.forEach { (inst, row) ->
            require(row.size == meter.totalSlots) {
                "$inst row must have ${meter.totalSlots} slots, got ${row.size}"
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
        require(slot in 0 until slots)
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
        copy(grid = grid + (instrument to List(slots) { null }))

    fun isEmpty(): Boolean = grid.values.all { row -> row.all { it == null } }

    /**
     * Shift every instrument's row by [n] slots with wrap-around (positive = later
     * in the loop / to the right). [n] is taken modulo [slots], so any integer is
     * valid. Used by the looper's translate control.
     */
    fun translated(n: Int): PercussionPattern {
        if (slots == 0) return this
        val shift = ((n % slots) + slots) % slots
        if (shift == 0) return this
        val newGrid = grid.mapValues { (_, row) ->
            List(slots) { i -> row[((i - shift) % slots + slots) % slots] }
        }
        return copy(grid = newGrid)
    }

    /**
     * Re-fit this pattern onto [newMeter], copying cells by slot index (cells past
     * the new slot count are dropped; new slots are silent). Out-of-range voice
     * indices can't occur because the instruments are unchanged.
     */
    fun withMeter(newMeter: PercussionMeter): PercussionPattern {
        if (newMeter == meter) return this
        val n = newMeter.totalSlots
        val newGrid = PercussionInstrument.entries.associateWith { inst ->
            val old = grid.getValue(inst)
            List(n) { i -> old.getOrNull(i) }
        }
        return PercussionPattern(newGrid, newMeter)
    }

    /**
     * Serialize to a compact string for persistence. New format carries the meter:
     *   "M:bars,beatsPerBar,beatUnit,division;row|row|…"
     * Each row is its cells comma-separated, silent = "-". Round-trips via [decode],
     * which also accepts the legacy meter-less 16-cell format.
     */
    fun encode(): String {
        val m = "M:${meter.bars},${meter.beatsPerBar},${meter.beatUnit},${meter.division};"
        val body = PercussionInstrument.entries.joinToString("|") { inst ->
            grid.getValue(inst).joinToString(",") { it?.toString() ?: "-" }
        }
        return m + body
    }

    companion object {
        fun empty(meter: PercussionMeter = PercussionMeter.DEFAULT): PercussionPattern =
            PercussionPattern(
                PercussionInstrument.entries.associateWith { List(meter.totalSlots) { null } },
                meter,
            )

        /** Parse a string produced by [encode]; null if malformed or out of range.
         *  Accepts the new "M:…;rows" form and the legacy meter-less 16-cell form. */
        fun decode(s: String): PercussionPattern? {
            var meter = PercussionMeter.DEFAULT
            var body = s
            if (s.startsWith("M:")) {
                val sep = s.indexOf(';')
                if (sep < 0) return null
                val parts = s.substring(2, sep).split(",")
                if (parts.size != 4) return null
                val ints = parts.map { it.toIntOrNull() ?: return null }
                meter = runCatching {
                    PercussionMeter(ints[0], ints[1], ints[2], ints[3])
                }.getOrNull() ?: return null
                body = s.substring(sep + 1)
            }
            val rows = body.split("|")
            if (rows.size != PercussionInstrument.entries.size) return null
            val grid = HashMap<PercussionInstrument, List<Int?>>()
            for ((idx, inst) in PercussionInstrument.entries.withIndex()) {
                val cells = rows[idx].split(",")
                if (cells.size != meter.totalSlots) return null
                val row = cells.map { c -> if (c == "-") null else c.toIntOrNull() ?: return null }
                if (row.any { it != null && it !in 0 until PercussionVoices.voiceCount(inst) }) return null
                grid[inst] = row
            }
            return runCatching { PercussionPattern(grid, meter) }.getOrNull()
        }

        /**
         * The built-in "stock samba" groove over 2 bars of 2/4 (default meter).
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
                ),
                PercussionMeter.DEFAULT,
            )
        }
    }
}

/** Loop timing helpers (kept pure so they're unit-testable on the JVM). */
object PercussionTiming {
    /** Milliseconds of one [division]-note slot at [bpm] (a quarter-note = 4 sixteenths,
     *  so a 1/[division] note = quarter × 4 / division). */
    fun slotMs(bpm: Int, division: Int = 16): Long = (60_000L / bpm.coerceAtLeast(20)) * 4 / division

    /** Total loop length in milliseconds for the default 16-slot meter. */
    fun loopMs(bpm: Int): Long = slotMs(bpm) * PERCUSSION_SLOTS

    /**
     * Duration (ms) to wait AFTER [slot] before the next slot, applying a Brazilian
     * subdivision swing. [swingPercent] 0 = straight; higher pushes the odd
     * subdivisions (the off-beats — e.g. the "e"/"a" of every beat in sixteenths)
     * progressively later, so each on→off pair stretches from 1:1 (straight) toward
     * a hemiola/triplet lilt (≈2:1 around 66%, up to 3:1 at 100%). The loop's total
     * length is unchanged — only the internal subdivision shifts.
     */
    fun swungSlotMs(slot: Int, bpm: Int, swingPercent: Int, division: Int = 16): Long {
        val base = slotMs(bpm, division).toDouble()
        val frac = (swingPercent.coerceIn(0, 100) / 100.0) * 0.5   // max: off-slots half a slot late
        fun delayOf(s: Int) = if (s % 2 == 1) base * frac else 0.0  // odd subdivisions arrive late
        return (base + delayOf(slot + 1) - delayOf(slot)).toLong().coerceAtLeast(1L)
    }
}

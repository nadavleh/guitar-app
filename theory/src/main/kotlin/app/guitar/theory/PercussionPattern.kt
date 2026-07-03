package app.guitar.theory

/** Slot count of the default meter (2 bars of 2/4 in sixteenths = 16). Kept as a
 *  named constant for the empty pattern and the unit tests. */
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

/** Accent flag folded into a cell's raw value: raw = voice + ACCENT when accented. */
const val PERCUSSION_ACCENT = 100

/**
 * A percussion loop grid. For each instrument there is a list of cells (one per
 * slot of [meter]); a cell is either `null` (silent) or a raw value encoding a
 * 0-based voice index plus an optional accent flag (raw = voice, or voice +
 * [PERCUSSION_ACCENT] when the hit is accented). Use [voiceAt] / [isAccented]
 * rather than reading the raw grid. The slot count is
 * [PercussionMeter.totalSlots], so it varies with bars / time signature / division.
 *
 * Immutable — every mutation returns a new pattern (Compose-friendly).
 */
data class PercussionPattern(
    val instruments: List<PercussionInstrument>,
    val grid: Map<String, List<Int?>>,
    val meter: PercussionMeter = PercussionMeter.DEFAULT,
) {
    /** Number of slots in this pattern (= meter.totalSlots). */
    val slots: Int get() = meter.totalSlots

    init {
        require(instruments.map { it.id }.toSet().size == instruments.size) {
            "kit has duplicate instruments: ${instruments.map { it.id }}"
        }
        require(grid.keys == instruments.map { it.id }.toSet()) {
            "grid keys ${grid.keys} must match the kit ${instruments.map { it.id }}"
        }
        instruments.forEach { inst ->
            val row = grid.getValue(inst.id)
            require(row.size == meter.totalSlots) {
                "${inst.id} row must have ${meter.totalSlots} slots, got ${row.size}"
            }
            row.forEach { v ->
                require(v == null || (v % PERCUSSION_ACCENT) in 0 until inst.voiceCount &&
                    v / PERCUSSION_ACCENT <= 1 && v >= 0) {
                    "${inst.id} has out-of-range cell value $v"
                }
            }
        }
    }

    fun voiceAt(instrument: PercussionInstrument, slot: Int): Int? =
        grid.getValue(instrument.id)[slot]?.let { it % PERCUSSION_ACCENT }

    /** Whether the (non-silent) cell is an accented hit. */
    fun isAccented(instrument: PercussionInstrument, slot: Int): Boolean =
        (grid.getValue(instrument.id)[slot] ?: 0) >= PERCUSSION_ACCENT

    /** Toggle the accent on a non-silent cell (no-op on silent cells). */
    fun accentToggled(instrument: PercussionInstrument, slot: Int): PercussionPattern {
        val raw = grid.getValue(instrument.id)[slot] ?: return this
        val next = if (raw >= PERCUSSION_ACCENT) raw - PERCUSSION_ACCENT else raw + PERCUSSION_ACCENT
        val newRow = grid.getValue(instrument.id).toMutableList().also { it[slot] = next }
        return copy(grid = grid + (instrument.id to newRow))
    }

    /** Whether [instrument] (by id) is part of this pattern's kit. */
    fun hasInstrument(instrument: PercussionInstrument): Boolean = grid.containsKey(instrument.id)

    /**
     * Advance a cell one step in the cycle:
     * `null → 0 → 1 → … → (voiceCount-1) → null`. An accent survives voice cycling.
     */
    fun cycled(instrument: PercussionInstrument, slot: Int): PercussionPattern {
        require(slot in 0 until slots)
        val count = instrument.voiceCount
        val cur = voiceAt(instrument, slot)
        val accent = isAccented(instrument, slot)
        val next = when {
            cur == null -> 0
            cur >= count - 1 -> null
            else -> cur + 1
        }
        return withCell(instrument, slot, next?.plus(if (accent) PERCUSSION_ACCENT else 0))
    }

    fun withCell(instrument: PercussionInstrument, slot: Int, voice: Int?): PercussionPattern {
        val newRow = grid.getValue(instrument.id).toMutableList().also { it[slot] = voice }
        return copy(grid = grid + (instrument.id to newRow))
    }

    fun clearedRow(instrument: PercussionInstrument): PercussionPattern =
        copy(grid = grid + (instrument.id to List(slots) { null }))

    /** Append [instrument] to the kit with a silent row. No-op if already present. */
    fun addInstrument(instrument: PercussionInstrument): PercussionPattern {
        if (hasInstrument(instrument)) return this
        return copy(
            instruments = instruments + instrument,
            grid = grid + (instrument.id to List(slots) { null }),
        )
    }

    /** Remove [instrument] (and its row) from the kit. No-op if absent. */
    fun removeInstrument(instrument: PercussionInstrument): PercussionPattern {
        if (!hasInstrument(instrument)) return this
        return copy(
            instruments = instruments.filter { it.id != instrument.id },
            grid = grid - instrument.id,
        )
    }

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
        val newGrid = instruments.associate { inst ->
            val old = grid.getValue(inst.id)
            inst.id to List(n) { i -> old.getOrNull(i) }
        }
        return PercussionPattern(instruments, newGrid, newMeter)
    }

    /**
     * Serialize to a compact string for persistence:
     *   "M:bars,beatsPerBar,beatUnit,division;id=cells|id=cells|…"
     * Each row is "instrumentId=" then its cells comma-separated (silent = "-").
     * Round-trips via [decode].
     */
    fun encode(): String {
        val m = "M:${meter.bars},${meter.beatsPerBar},${meter.beatUnit},${meter.division};"
        val body = instruments.joinToString("|") { inst ->
            inst.id + "=" + grid.getValue(inst.id).joinToString(",") { it?.toString() ?: "-" }
        }
        return m + body
    }

    companion object {
        fun empty(
            kit: List<PercussionInstrument> = PercussionCatalog.DEFAULT_KIT,
            meter: PercussionMeter = PercussionMeter.DEFAULT,
        ): PercussionPattern =
            PercussionPattern(
                kit,
                kit.associate { it.id to List(meter.totalSlots) { null } },
                meter,
            )

        /**
         * Parse a string produced by [encode]; null only on structural garbage.
         * Rows whose instrument id isn't in the catalog are skipped (forward/backward
         * compatibility), so a smaller-but-valid kit can result.
         */
        fun decode(s: String): PercussionPattern? {
            if (!s.startsWith("M:")) return null
            val sep = s.indexOf(';')
            if (sep < 0) return null
            val parts = s.substring(2, sep).split(",")
            if (parts.size != 4) return null
            val ints = parts.map { it.toIntOrNull() ?: return null }
            val meter = runCatching {
                PercussionMeter(ints[0], ints[1], ints[2], ints[3])
            }.getOrNull() ?: return null

            val rows = s.substring(sep + 1).split("|")
            val instruments = ArrayList<PercussionInstrument>()
            val grid = HashMap<String, List<Int?>>()
            for (rowStr in rows) {
                val eq = rowStr.indexOf('=')
                if (eq < 0) return null
                val id = rowStr.substring(0, eq)
                val inst = PercussionCatalog.byId(id) ?: continue   // skip unknown instruments
                if (grid.containsKey(id)) continue                  // ignore duplicate rows
                val cells = rowStr.substring(eq + 1).split(",")
                if (cells.size != meter.totalSlots) return null
                val row = cells.map { c -> if (c == "-") null else c.toIntOrNull() ?: return null }
                // Raw cell = voice or voice + PERCUSSION_ACCENT (accented hit).
                if (row.any { it != null &&
                        !(it >= 0 && it / PERCUSSION_ACCENT <= 1 && (it % PERCUSSION_ACCENT) in 0 until inst.voiceCount) }) return null
                instruments.add(inst)
                grid[id] = row
            }
            return runCatching { PercussionPattern(instruments, grid, meter) }.getOrNull()
        }
    }
}

/**
 * Built-in loadable grooves for the drum-machine Load… menu, defined via the
 * [PercussionPattern.encode] string form so they're compact and self-validating
 * through decode. Same set as chorect-web's BUILTIN_PATTERNS (keep in sync).
 */
object PercussionBuiltins {
    private fun builtin(encoded: String): PercussionPattern =
        requireNotNull(PercussionPattern.decode(encoded)) { "invalid built-in pattern: $encoded" }

    /** Teleco-teco — the two classic phrasings. Surdo + pandeiro are shared; the
     *  tamborim and agogô are phase-shifted between the two. */
    val TELECOTECO_1: PercussionPattern = builtin(
        "M:2,2,4,16;" +
            "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
            "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
            "pandeiro=0,3,2,0,0,3,2,0,0,3,2,0,0,3,2,0" + "|" +
            "agogo=-,0,-,1,-,-,0,-,0,-,0,-,1,-,-,0",
    )

    val TELECOTECO_2: PercussionPattern = builtin(
        "M:2,2,4,16;" +
            "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
            "tamborim=0,1,0,1,0,1,2,0,1,0,1,0,1,2,0,1" + "|" +
            "pandeiro=0,3,2,0,0,3,2,0,0,3,2,0,0,3,2,0" + "|" +
            "agogo=0,-,0,-,1,-,-,0,-,0,-,1,-,-,0,-",
    )

    /** Grooves offered in the Load… menu (before the user's saved beats). */
    val ALL: List<Pair<String, PercussionPattern>> = listOf(
        "teleco-teco 1" to TELECOTECO_1,
        "teleco-teco 2" to TELECOTECO_2,
    )
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
     * 16th-note swing.
     *
     * Swing only operates when a quarter-note beat is split into exactly four 16th
     * notes ([PercussionMeter.beatUnit] == 4 and [PercussionMeter.division] == 16);
     * any other meter plays straight. Within each beat the four 16ths sit at the
     * nominal positions 0, ¼, ½, ¾ of the beat. As [swingPercent] rises 0→100 the
     * 1st and 3rd 16ths stay anchored, the 2nd is delayed toward ⅓ of the beat
     * (+1/12 beat at 100 %), and the 4th is advanced (made early) toward ⅔ of the
     * beat (−1/12 beat at 100 %). Equivalently the per-beat slot durations scale by
     * [1+s/3, 1−s/3, 1−s/3, 1+s/3] where s = swingPercent/100, so each beat — and
     * thus the whole loop — keeps its total length; only the inner onsets move.
     */
    fun swungSlotMs(slot: Int, bpm: Int, swingPercent: Int, meter: PercussionMeter): Long {
        val base = slotMs(bpm, meter.division)
        // Swing is defined only for a quarter-note beat divided into four 16ths.
        if (meter.beatUnit != 4 || meter.division != 16) return base.coerceAtLeast(1L)
        val s = swingPercent.coerceIn(0, 100) / 100.0
        // Each 16th's onset, in ms from loop start, rounded independently — so the
        // anchors (beat start, half-beat, beat boundary) stay exactly on grid and the
        // rounding never accumulates. The slot's duration is the gap to the next onset.
        fun onsetMs(k: Int): Long {
            val offsetSlots = when (k % 4) {
                0 -> 0.0              // 1st 16th: anchored on the beat
                1 -> 1.0 + s / 3.0    // 2nd: delayed ¼→⅓ of the beat
                2 -> 2.0              // 3rd: anchored on the half-beat
                else -> 3.0 - s / 3.0 // 4th: advanced ¾→⅔ of the beat
            }
            return Math.round(((k / 4) * 4 + offsetSlots) * base)
        }
        return (onsetMs(slot + 1) - onsetMs(slot)).coerceAtLeast(1L)
    }
}

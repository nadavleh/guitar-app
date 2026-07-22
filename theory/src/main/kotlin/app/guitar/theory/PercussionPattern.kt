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

/** Per-slot dynamics folded into a cell's raw value: raw += 1000 × dynLevel.
 *  Level 0 = 100 % (default), 1 = 75 %, 2 = 50 %, 3 = 25 %. So the full cell
 *  encoding is raw = voice + 100·accent + 1000·dynLevel. Older app versions
 *  reject cells with a dyn level and skip the whole beat (the established
 *  forward-compat path). */
const val PERCUSSION_DYN = 1000
val PERCUSSION_DYN_FACTORS = listOf(1.0f, 0.75f, 0.5f, 0.25f)

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
                // raw = voice + 100·accent + 1000·dynLevel
                require(v == null || (v >= 0 && (v % PERCUSSION_ACCENT) in 0 until inst.voiceCount &&
                    (v / PERCUSSION_ACCENT) % 10 <= 1 && v / PERCUSSION_DYN <= 3)) {
                    "${inst.id} has out-of-range cell value $v"
                }
            }
        }
    }

    fun voiceAt(instrument: PercussionInstrument, slot: Int): Int? =
        grid.getValue(instrument.id)[slot]?.let { it % PERCUSSION_ACCENT }

    /** Whether the (non-silent) cell is an accented hit. */
    fun isAccented(instrument: PercussionInstrument, slot: Int): Boolean =
        ((grid.getValue(instrument.id)[slot] ?: 0) / PERCUSSION_ACCENT) % 10 == 1

    /** Toggle the accent on a non-silent cell (no-op on silent cells). */
    fun accentToggled(instrument: PercussionInstrument, slot: Int): PercussionPattern {
        val raw = grid.getValue(instrument.id)[slot] ?: return this
        val next = if (isAccented(instrument, slot)) raw - PERCUSSION_ACCENT else raw + PERCUSSION_ACCENT
        val newRow = grid.getValue(instrument.id).toMutableList().also { it[slot] = next }
        return copy(grid = grid + (instrument.id to newRow))
    }

    /** Per-slot dynamic level (0 = 100 %, 1 = 75 %, 2 = 50 %, 3 = 25 %). */
    fun dynLevelAt(instrument: PercussionInstrument, slot: Int): Int =
        (grid.getValue(instrument.id)[slot] ?: 0) / PERCUSSION_DYN

    /** Cycle a non-silent cell's dynamic level 100 → 75 → 50 → 25 → 100 (Dyn tool). */
    fun dynCycled(instrument: PercussionInstrument, slot: Int): PercussionPattern {
        val raw = grid.getValue(instrument.id)[slot] ?: return this
        val level = raw / PERCUSSION_DYN
        val next = raw - level * PERCUSSION_DYN + ((level + 1) % 4) * PERCUSSION_DYN
        val newRow = grid.getValue(instrument.id).toMutableList().also { it[slot] = next }
        return copy(grid = grid + (instrument.id to newRow))
    }

    /** Whether [instrument] (by id) is part of this pattern's kit. */
    fun hasInstrument(instrument: PercussionInstrument): Boolean = grid.containsKey(instrument.id)

    /**
     * Advance a cell one step in the cycle:
     * `null → 0 → 1 → … → (voiceCount-1) → null`. The accent AND the dynamic
     * level survive voice cycling.
     */
    fun cycled(instrument: PercussionInstrument, slot: Int): PercussionPattern {
        require(slot in 0 until slots)
        val count = instrument.voiceCount
        val cur = voiceAt(instrument, slot)
        val accent = isAccented(instrument, slot)
        val dyn = dynLevelAt(instrument, slot)
        val next = when {
            cur == null -> 0
            cur >= count - 1 -> null
            else -> cur + 1
        }
        return withCell(instrument, slot,
            next?.plus(if (accent) PERCUSSION_ACCENT else 0)?.plus(dyn * PERCUSSION_DYN))
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

    /** Reorder the kit: move the track at [from] to index [to]. The grid is unchanged
     *  (rows are keyed by id); only the display order of [instruments] changes. */
    fun movedInstrument(from: Int, to: Int): PercussionPattern {
        if (from !in instruments.indices || to !in instruments.indices || from == to) return this
        val list = instruments.toMutableList()
        list.add(to, list.removeAt(from))
        return copy(instruments = list)
    }

    /** Add a preset TRACK in one press: [base]'s row filled by tiling [template]
     *  (defined on the default 16-slot meter) across this pattern's slots. If the
     *  instrument is already in the kit, the preset lands on a fresh clone track
     *  ("Surdo 2") so the existing line is untouched. */
    fun withPresetTrack(base: PercussionInstrument, template: List<Int?>): PercussionPattern {
        val inst = if (!hasInstrument(base)) base else {
            var n = 2
            while (instruments.any { it.id == "${base.id}#$n" }) n++
            base.copy(id = "${base.id}#$n", displayName = "${base.displayName} $n")
        }
        val row = List(meter.totalSlots) { i -> template[i % template.size] }
        return copy(instruments = instruments + inst, grid = grid + (inst.id to row))
    }

    /** Duplicate [instrument]'s track: a CLONE instrument (same voices and sound,
     *  id "<base>#<n>", display name "Surdo 2") is inserted right below it with a
     *  copy of its row. No-op if [instrument] isn't in the kit. */
    fun duplicatedTrack(instrument: PercussionInstrument): PercussionPattern {
        val idx = instruments.indexOfFirst { it.id == instrument.id }
        if (idx < 0) return this
        val base = PercussionCatalog.baseId(instrument.id)
        var n = 2
        while (instruments.any { it.id == "$base#$n" }) n++
        val baseInst = PercussionCatalog.byId(base) ?: instrument
        val clone = baseInst.copy(id = "$base#$n", displayName = "${baseInst.displayName} $n")
        val list = instruments.toMutableList().apply { add(idx + 1, clone) }
        return PercussionPattern(list, grid + (clone.id to grid.getValue(instrument.id)), meter)
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
                // resolve() also reconstructs duplicated-track clones ("surdo#2");
                // truly unknown instruments are skipped (forward compatibility).
                val inst = PercussionCatalog.resolve(id) ?: continue
                if (grid.containsKey(id)) continue                  // ignore duplicate rows
                val cells = rowStr.substring(eq + 1).split(",")
                if (cells.size != meter.totalSlots) return null
                val row = cells.map { c -> if (c == "-") null else c.toIntOrNull() ?: return null }
                // Raw cell = voice + 100·accent + 1000·dynLevel.
                if (row.any { it != null &&
                        !(it >= 0 && (it / PERCUSSION_ACCENT) % 10 <= 1 && it / PERCUSSION_DYN <= 3 &&
                            (it % PERCUSSION_ACCENT) in 0 until inst.voiceCount) }) return null
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
            // Agogô (#12): low bell ▼ (voice 0) on steps 1,7,9,16; high bell ▲ (voice 1)
            // on steps 4,5,11,13,14 — 0-indexed slots below.
            "agogo=0,-,-,1,1,-,0,-,0,-,1,-,1,1,-,0",
    )

    val TELECOTECO_2: PercussionPattern = builtin(
        "M:2,2,4,16;" +
            "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
            "tamborim=0,1,0,1,0,1,2,0,1,0,1,0,1,2,0,1" + "|" +
            "pandeiro=0,3,2,0,0,3,2,0,0,3,2,0,0,3,2,0" + "|" +
            "agogo=0,-,0,-,1,-,-,0,-,0,-,1,-,-,0,-",
    )

    /** Batida do cavaco 1 — the default samba groove for the new default kit
     *  (surdo + tamborim + bongo): the teleco-teco surdo/tamborim with a steady
     *  hi/lo bongo comp on the off-beats. */
    val BATIDA_CAVACO_1: PercussionPattern = builtin(
        "M:2,2,4,16;" +
            "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
            "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
            "bongo=-,0,-,1,-,0,-,1,-,0,-,1,-,0,-,1",
    )

    // Northeastern-Brazilian grooves (xote / baião / forró / xaxado / arrasta-pé).
    // Each uses the shared teleco-teco surdo (muted-bass ◐ + tap · pulse) and a
    // tamborim tresillo (3+3+2) under a bongo comp transcribed from the user's beats.
    private const val SURDO_TELECO = "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2"
    private const val TAMB_TRESILLO = "tamborim=0,-,-,0,-,-,0,-,0,-,-,0,-,-,0,-"

    val XOTE: PercussionPattern = builtin(
        "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
            "bongo=0,-,2,1,0,-,0,-,0,-,2,1,0,-,0,-",
    )
    val BAIAO: PercussionPattern = builtin(
        "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
            "bongo=0,-,2,1,-,-,2,1,0,-,2,1,-,-,2,1",
    )
    val FORRO: PercussionPattern = builtin(
        "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
            "bongo=0,-,3,1,2,-,3,1,0,0,-,0,2,-,3,1",
    )
    val XAXADO: PercussionPattern = builtin(
        "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
            "bongo=0,2,3,0,-,-,1,-,0,2,3,0,-,2,1,-",
    )
    val ARRASTA_PE: PercussionPattern = builtin(
        "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
            "bongo=0,2,3,0,1,-,1,-,0,2,3,0,1,-,1,-",
    )

    /** A loadable groove for the Load… menu; [bpm] (when non-null) is applied on
     *  load; [opening] (when non-null) is a one-shot entrada played before the loop. */
    data class BuiltinPattern(
        val name: String,
        val pattern: PercussionPattern,
        val bpm: Int? = null,
        val opening: PercussionPattern? = null,
    )

    /** A one-press preset TRACK ("+ Add ▾" → presets, also the phrase "chunks"
     *  the Blocks feature sequences): a 16-slot row template tiled across the
     *  current loop on [instrument] (cloned if already present). [swing] is the
     *  chunk's own feel (0 = straight; used by Blocks playback); [note] is a
     *  playing rule/tip attached to the chunk. */
    data class PresetTrack(
        val label: String,
        val instrument: PercussionInstrument,
        val template: List<Int?>,
        val swing: Int = 0,
        val note: String = "",
        /** When true, whatever phrase FOLLOWS this one in a block gains a strong
         *  beat on 1 (its measure-2 downbeat stroke) — the partido-alto return rule. */
        val addsReturnDownbeat: Boolean = false,
    )

    /** Find a preset track by its label (block cells reference phrases by label). */
    fun presetByLabel(label: String): PresetTrack? = PRESET_TRACKS.firstOrNull { it.label == label }

    /** A single-line tamborim rhythm from onset slots ([accented] slots get the
     *  accent flag). Tamborim articulation: an onset directly followed by another
     *  onset is played as a MUTED clack (voice 1) leading into the open clack
     *  (voice 0) — i.e. the first stroke of every consecutive-16ths pair is muted.
     *  Used by the study patterns, transcribed from notation sheets. */
    private fun tamborimLine(onsets: Set<Int>, accented: Set<Int> = emptySet(), bars: Int = 2): PercussionPattern {
        val cells = (0 until bars * 8).joinToString(",") { i ->
            when {
                i !in onsets -> "-"
                i in accented -> "${PERCUSSION_ACCENT}"
                (i + 1) in onsets -> "1"    // muted pickup into the next stroke
                else -> "0"
            }
        }
        return builtin("M:$bars,2,4,16;tamborim=$cells")
    }

    // ---- Study rhythms. 2 bars of 2/4 on a 16th grid unless noted; single
    // tamborim line. An entrada is an OPENING played once before its loop.
    private val BOSSA_UP = tamborimLine(setOf(0, 3, 6, 10, 13))
    private val SAMBA_CLAP = tamborimLine(setOf(0, 3, 6), bars = 1)

    /** The teleco-teco tamborim loop the entradas fall into — the same line as
     *  the "Tamborim — Teleco-teco" track preset. */
    private val TELECO_LOOP: PercussionPattern =
        builtin("M:2,2,4,16;tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0")

    /** Study grooves (the "Study" section): comping rhythms plus Oded's two
     *  entradas — each entrada plays once, then falls into the teleco-teco loop. */
    val STUDY: List<BuiltinPattern> = listOf(
        BuiltinPattern("Bossa Nova Clave", BOSSA_UP, bpm = 70),
        BuiltinPattern("Samba Clap (Palma)", SAMBA_CLAP, bpm = 70),
        BuiltinPattern("Entrada 1 → Teleco-teco", TELECO_LOOP, bpm = 80,
            opening = builtin("M:2,2,4,16;tamborim=0,2,1,0,1,2,0,2,0,2,0,2,0,2,1,0")),
        BuiltinPattern("Entrada 2 → Teleco-teco", TELECO_LOOP, bpm = 70,
            opening = builtin("M:2,2,4,16;tamborim=0,2,0,2,0,2,0,2,0,2,0,2,0,2,1,0")),
    )

    /** Track presets — the single-instrument "chunks": added to the CURRENT beat
     *  in one press from "+ Add ▾", loadable as an opening entrada, and the
     *  phrases the Blocks feature sequences. Sources: the teleco-teco built-ins,
     *  Oded's entradas, and Nadav's variation exports. */
    val PRESET_TRACKS: List<PresetTrack> = listOf(
        PresetTrack(
            "Surdo — Marcação", PercussionCatalog.Surdo,
            listOf(1, null, null, 2, 0, null, null, 2, 1, null, null, 2, 0, null, null, 2),
        ),
        PresetTrack(
            "Tamborim — Teleco-teco", PercussionCatalog.Tamborim,
            listOf(1, 0, 1, 0, 1, 2, 0, 1, 0, 1, 0, 1, 0, 1, 2, 0),
        ),
        PresetTrack(
            "Tamborim — Telecoteco Var 1", PercussionCatalog.Tamborim,
            listOf(1, 0, 1, 0, 1, 2, 0, 1, 0, 0, 0, 0, 0, 1, 2, 0),
        ),
        PresetTrack(
            "Tamborim — Telecoteco Var 2", PercussionCatalog.Tamborim,
            listOf(1, 0, 1, 0, 1, 2, 0, 1, 2, 0, 0, 0, 0, 1, 2, 0),
        ),
        PresetTrack(
            "Tamborim — Telecoteco Var 3", PercussionCatalog.Tamborim,
            listOf(1, 0, 1, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0),
        ),
        // Corrected per Nadav's export: each beat = ACCENTED clack, muted clack at
        // 75 % (dyn level 1), tap, clack — with a light 10 % swing.
        PresetTrack(
            "Tamborim — Levada Reta", PercussionCatalog.Tamborim,
            listOf(100, 1001, 2, 0, 100, 1001, 2, 0, 100, 1001, 2, 0, 100, 1001, 2, 0),
            swing = 10,
        ),
        PresetTrack(
            "Tamborim — Chamada", PercussionCatalog.Tamborim,
            listOf(1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0),
            swing = 61,
            note = "Played with ~60% swing.",
        ),
        PresetTrack(
            "Tamborim — Entrada 1", PercussionCatalog.Tamborim,
            listOf(0, 2, 1, 0, 1, 2, 0, 2, 0, 2, 0, 2, 0, 2, 1, 0),
        ),
        PresetTrack(
            "Tamborim — Entrada 2", PercussionCatalog.Tamborim,
            listOf(0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 1, 0),
        ),
        PresetTrack(
            "Bongo — Partido Alto", PercussionCatalog.Bongo,
            listOf(null, 0, null, null, 1, null, 1, null, 1, null, 0, null, null, 1, null, 1),
        ),
        PresetTrack(
            "Bongo — Partido Alto Var 1", PercussionCatalog.Bongo,
            listOf(null, 0, null, 0, 1, null, 1, 1, 1, null, 1, 1, 1, null, 1, 1),
            note = "RULE: when returning to the regular partido alto after this variation, " +
                "the partido alto gets a strong beat on beat 1 of measure 1 — the same stroke " +
                "as its measure-2 downbeat (doesn't occur normally).",
            addsReturnDownbeat = true,
        ),
        PresetTrack(
            "Bongo — Partido Alto Var 2", PercussionCatalog.Bongo,
            listOf(null, 0, null, 0, 1, null, 0, null, 2, 1, null, 1, null, 1, null, 1),
        ),
    )

    // Partido-alto grooves (from Nadav's exported beats): the teleco-teco
    // surdo/tamborim under three bongo comps.
    val PARTIDO_ALTO_OFFICIAL: PercussionPattern = builtin(
        "M:2,2,4,16;" + SURDO_TELECO + "|" +
            "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
            "bongo=-,0,-,-,1,-,1,-,1,-,0,-,-,1,-,1",
    )
    val PARTIDO_ALTO_DEC: PercussionPattern = builtin(
        "M:2,2,4,16;" + SURDO_TELECO + "|" +
            "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
            "bongo=-,0,-,0,1,-,1,-,1,-,0,-,3,1,-,1",
    )
    val PARTIDO_ALTO_PLATINELAS: PercussionPattern = builtin(
        "M:2,2,4,16;" + SURDO_TELECO + "|" +
            "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
            "bongo=0,0,-,0,1,-,0,-,1,-,0,-,3,1,2,0",
    )

    /** Grooves offered in the Load… menu (before the user's saved beats). */
    val ALL: List<BuiltinPattern> = listOf(
        BuiltinPattern("Samba 1", TELECOTECO_1),
        BuiltinPattern("Partido Alto Groove (Official)", PARTIDO_ALTO_OFFICIAL, bpm = 70),
        BuiltinPattern("Partido Alto Groove (Dec)", PARTIDO_ALTO_DEC, bpm = 70),
        BuiltinPattern("Platinelas Pandeiro — Partido Alto Groove", PARTIDO_ALTO_PLATINELAS, bpm = 70),
        BuiltinPattern("Xote", XOTE, bpm = 90),
        BuiltinPattern("Baião", BAIAO, bpm = 90),
        BuiltinPattern("Forró", FORRO, bpm = 95),
        BuiltinPattern("Xaxado", XAXADO, bpm = 100),
        BuiltinPattern("Arrasta-pé", ARRASTA_PE, bpm = 100),
    )
}

/**
 * Beat file (export / import): a self-describing JSON envelope around a pattern
 * plus its name / tempo, so a beat can be saved to disk and loaded back (here or
 * in chorect-web — same shape). Kept dependency-free (hand-rolled emit; the app
 * layer parses with its platform JSON reader). Round-trips through [BeatFile].
 */
data class BeatFile(
    val name: String,
    val bpm: Int,
    val swing: Int,
    val pattern: PercussionPattern,
    /** Optional one-shot opening (entrada) played once before the loop. */
    val opening: PercussionPattern? = null,
    /** Optional free-text notes attached to the beat. */
    val notes: String = "",
) {
    /** Pretty-printed JSON envelope written to disk. */
    fun encode(): String {
        fun esc(s: String) = buildString {
            for (c in s) when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\")
                '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                else -> append(c)
            }
        }
        return buildString {
            append("{\n")
            append("  \"format\": \"chorect-beat\",\n")
            append("  \"version\": 1,\n")
            append("  \"name\": \"").append(esc(name)).append("\",\n")
            append("  \"bpm\": ").append(bpm).append(",\n")
            append("  \"swing\": ").append(swing).append(",\n")
            append("  \"pattern\": \"").append(esc(pattern.encode())).append("\"")
            if (opening != null) {
                append(",\n  \"opening\": \"").append(esc(opening.encode())).append("\"")
            }
            if (notes.isNotEmpty()) {
                append(",\n  \"notes\": \"").append(esc(notes)).append("\"")
            }
            append("\n}\n")
        }
    }

    companion object {
        const val FORMAT = "chorect-beat"

        /** Parse a beat file produced by [encode]; null on anything unrecognizable.
         *  Uses a focused flat-JSON reader (string/number values, any key order) so
         *  the module stays dependency-free and round-trips with web's JSON.stringify. */
        fun decode(text: String): BeatFile? {
            val fields = parseFlatJsonObject(text) ?: return null
            if (fields["format"] != FORMAT) return null
            val pattern = PercussionPattern.decode(fields["pattern"] ?: return null) ?: return null
            val opening = fields["opening"]?.let { PercussionPattern.decode(it) }
            val notes = fields["notes"] ?: ""
            val name = fields["name"]?.takeIf { it.isNotBlank() } ?: "beat"
            val bpm = fields["bpm"]?.toIntOrNull()?.coerceIn(10, 300) ?: 90
            val swing = fields["swing"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0
            return BeatFile(name, bpm, swing, pattern, opening, notes)
        }
    }
}

/**
 * A saved beat: the loop, an optional one-shot opening (entrada) played once
 * before it, and free-text [notes]. Persisted as "main", "main~opening", or
 * "main~opening~notes" (empty middle part when there's no opening) — '~' never
 * appears in [PercussionPattern.encode] output and is escaped out of the notes
 * (newlines too: the Android store is newline-delimited). Older app versions
 * fail to decode combined values and simply skip the beat rather than
 * mis-reading it. Mirrors chorect-web's encodeBeatPatterns/decodeBeatPatterns.
 */
data class SavedBeat(
    val main: PercussionPattern,
    val opening: PercussionPattern? = null,
    val notes: String = "",
) {
    fun encode(): String = buildString {
        append(main.encode())
        if (opening != null || notes.isNotEmpty()) {
            append(SEP).append(opening?.encode() ?: "")
        }
        if (notes.isNotEmpty()) append(SEP).append(escapeNotes(notes))
    }

    companion object {
        const val SEP = "~"

        private fun escapeNotes(s: String): String = s
            .replace("%", "%25").replace(SEP, "%7E")
            .replace("\r", "%0D").replace("\n", "%0A")

        private fun unescapeNotes(s: String): String = s
            .replace("%0A", "\n").replace("%0D", "\r")
            .replace("%7E", SEP).replace("%25", "%")

        /** Decode a persisted beat value; null if the main pattern is unreadable.
         *  A bad opening part is dropped, not fatal. */
        fun decode(s: String): SavedBeat? {
            val parts = s.split(SEP)
            val main = PercussionPattern.decode(parts[0]) ?: return null
            val opening = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { PercussionPattern.decode(it) }
            val notes = if (parts.size > 2) unescapeNotes(parts.drop(2).joinToString(SEP)) else ""
            return SavedBeat(main, opening, notes)
        }
    }
}

/**
 * Minimal reader for a FLAT JSON object (values are strings or numbers only — no
 * nesting), returning each key → its scalar value as a string (strings unescaped,
 * numbers as their digit text). Enough for [BeatFile]; not a general JSON parser.
 */
private fun parseFlatJsonObject(text: String): Map<String, String>? {
    val s = text.trim()
    var i = s.indexOf('{')
    val end = s.lastIndexOf('}')
    if (i < 0 || end <= i) return null
    i++
    val out = HashMap<String, String>()

    fun skipWs() { while (i < end && s[i].isWhitespace()) i++ }
    fun parseString(): String? {
        if (i >= end || s[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < end) {
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (i >= end) return null
                    when (val e = s[i++]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'b' -> sb.append('\b'); 'f' -> sb.append(12.toChar())
                        'u' -> { if (i + 4 > end) return null; sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> return null
                    }
                }
                else -> sb.append(c)
            }
        }
        return null
    }

    while (true) {
        skipWs()
        if (i >= end) break
        if (s[i] == ',') { i++; continue }
        val key = parseString() ?: return null
        skipWs()
        if (i >= end || s[i] != ':') return null
        i++
        skipWs()
        val value = if (i < end && s[i] == '"') {
            parseString() ?: return null
        } else {
            val start = i
            while (i < end && s[i] != ',' && !s[i].isWhitespace()) i++
            s.substring(start, i)
        }
        out[key] = value
    }
    return out
}

/** Loop timing helpers (kept pure so they're unit-testable on the JVM). */
object PercussionTiming {
    /** Milliseconds of one [division]-note slot at [bpm] (a quarter-note = 4 sixteenths,
     *  so a 1/[division] note = quarter × 4 / division). */
    fun slotMs(bpm: Int, division: Int = 16): Long = (60_000L / bpm.coerceAtLeast(10)) * 4 / division

    /** Total loop length in milliseconds for the default 16-slot meter. */
    fun loopMs(bpm: Int): Long = slotMs(bpm) * PERCUSSION_SLOTS

    /**
     * Duration (ms) to wait AFTER [slot] before the next slot, applying a Brazilian
     * 16th-note swing.
     *
     * Swing only operates when a quarter-note beat is split into exactly four 16th
     * notes ([PercussionMeter.beatUnit] == 4 and [PercussionMeter.division] == 16);
     * any other meter plays straight. Within each beat the four 16ths sit at the
     * nominal positions 0, ¼, ½, ¾ of the beat. Samba microtiming studies (Gerischer;
     * Naveda/Gouyon) show the played feel keeps the 1st AND 2nd 16ths on the grid and
     * ANTICIPATES the 3rd and (more so) the 4th — the propulsive "pushing" samba lilt.
     * As [swingPercent] rises 0→100 the 3rd 16th moves up to −0.25 slot (−1/16 beat)
     * early and the 4th up to −0.4 slot (−1/10 beat) early (≈2× the deviations
     * measured in performance, so the slider max is clearly audible). Beat boundaries
     * stay anchored, so each beat — and thus the whole loop — keeps its total length.
     *
     * (This replaces an earlier model that delayed the 2nd 16th toward a triplet ⅓
     * while advancing the 4th — at high percentages the middle notes bunched together
     * and the groove sounded lopsided rather than swung.)
     */
    fun swungSlotMs(slot: Int, bpm: Int, swingPercent: Int, meter: PercussionMeter): Long {
        val base = slotMs(bpm, meter.division)
        // Swing is defined only for a quarter-note beat divided into four 16ths.
        if (meter.beatUnit != 4 || meter.division != 16) return base.coerceAtLeast(1L)
        val s = swingPercent.coerceIn(0, 100) / 100.0
        // Each 16th's onset, in ms from loop start, rounded independently — so the
        // anchors (beat start, quarter-beat, beat boundary) stay exactly on grid and
        // the rounding never accumulates. The slot's duration is the gap to the next onset.
        fun onsetMs(k: Int): Long {
            val offsetSlots = when (k % 4) {
                0 -> 0.0              // 1st 16th: anchored on the beat
                1 -> 1.0              // 2nd: anchored on the grid
                2 -> 2.0 - s * 0.25   // 3rd: anticipated (early)
                else -> 3.0 - s * 0.4 // 4th: anticipated more
            }
            return Math.round(((k / 4) * 4 + offsetSlots) * base)
        }
        return (onsetMs(slot + 1) - onsetMs(slot)).coerceAtLeast(1L)
    }
}

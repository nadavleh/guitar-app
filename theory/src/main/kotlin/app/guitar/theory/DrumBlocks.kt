package app.guitar.theory

import app.guitar.theory.PercussionBuiltins.PresetTrack

/**
 * Blocks: phrase sequencing for the drum machine (see
 * docs/superpowers/specs/2026-07-22-drum-blocks-design.md).
 *
 * A block is a grid of tracks × phrase columns. Each row ([BlockTrack]) is one
 * instrument; each cell holds a phrase (a [PresetTrack] chunk — one instrument,
 * 16 slots of 2/4 in 16ths, its own swing) or null (silence for that column).
 * Playback loops column by column: all tracks sound their column-c phrase
 * simultaneously — each with ITS phrase's swing micro-timing, which preserves
 * beat anchors, so tracks never drift apart — then the block advances to
 * column c+1 on the straight clock.
 */
data class BlockTrack(
    val instrument: PercussionInstrument,
    /** One phrase per column; null = silent for that column. Size == phraseCount. */
    val cells: List<PresetTrack?>,
    /** Optional OPENING cell: on the block's FIRST pass this plays instead of
     *  cells[0]; every loop after plays cells[0] and skips the opening. Null =
     *  this track plays cells[0] even on the first pass. */
    val opening: PresetTrack? = null,
)

data class DrumBlock(
    val name: String,
    val tracks: List<BlockTrack>,
    val phraseCount: Int,
) {
    init {
        require(phraseCount in 1..MAX_PHRASES) { "phraseCount must be 1..$MAX_PHRASES, got $phraseCount" }
        tracks.forEach { require(it.cells.size == phraseCount) { "track cells must match phraseCount" } }
    }

    /** Resize the column count, keeping existing cells (new columns empty). */
    fun withPhraseCount(n: Int): DrumBlock {
        val c = n.coerceIn(1, MAX_PHRASES)
        return copy(phraseCount = c, tracks = tracks.map { t -> t.copy(cells = List(c) { i -> t.cells.getOrNull(i) }) })
    }

    /** Append an empty track for [instrument] (instruments may repeat). */
    fun withTrack(instrument: PercussionInstrument): DrumBlock =
        copy(tracks = tracks + BlockTrack(instrument, List(phraseCount) { null }))

    /** Set a track's OPENING cell (plays instead of its first phrase on pass 1). */
    fun withOpeningCell(track: Int, phrase: PresetTrack?): DrumBlock {
        if (track !in tracks.indices) return this
        val t = tracks[track]
        return copy(tracks = tracks.toMutableList().also { it[track] = t.copy(opening = phrase) })
    }

    fun withoutTrack(index: Int): DrumBlock =
        if (index in tracks.indices) copy(tracks = tracks.filterIndexed { i, _ -> i != index }) else this

    fun withCell(track: Int, col: Int, phrase: PresetTrack?): DrumBlock {
        if (track !in tracks.indices || col !in 0 until phraseCount) return this
        val t = tracks[track]
        val cells = t.cells.toMutableList().also { it[col] = phrase }
        return copy(tracks = tracks.toMutableList().also { it[track] = t.copy(cells = cells) })
    }

    /** Merge with [other]: union of the two blocks' tracks. Only blocks with the
     *  same phrase count merge (all phrases share the 16-slot length); null otherwise. */
    fun mergedWith(other: DrumBlock, newName: String = "$name + ${other.name}"): DrumBlock? =
        if (other.phraseCount != phraseCount) null
        else DrumBlock(newName, tracks + other.tracks, phraseCount)

    fun isEmpty(): Boolean = tracks.all { t -> t.cells.all { it == null } }

    /** Serialize: "name=instId:lbl,lbl,…|instId:…" — phrases referenced by label
     *  (empty cell = empty label). A cell whose swing was overridden away from
     *  its library default is written "label@swing". Labels contain none of
     *  '=', '|', ':', ',' (or a trailing "@<digits>"). [resolve] is the phrase
     *  library (built-ins by default; pass a merged lookup when custom phrases
     *  exist). */
    fun encode(resolve: (String) -> PresetTrack? = PercussionBuiltins::presetByLabel): String {
        fun cellStr(c: PresetTrack): String {
            val libSwing = resolve(c.label)?.swing ?: 0
            return if (c.swing != libSwing) "${c.label}@${c.swing}" else c.label
        }
        return name + "=" + tracks.joinToString("|") { t ->
            // A leading "^cell" is the track's OPENING (plays once, pass 1).
            val prefix = t.opening?.let { "^" + cellStr(it) + "," } ?: ""
            t.instrument.id + ":" + prefix + t.cells.joinToString(",") { c -> c?.let(::cellStr) ?: "" }
        }
    }

    companion object {
        const val MAX_PHRASES = 8

        fun empty(name: String = "Block 1", phraseCount: Int = 4): DrumBlock =
            DrumBlock(name, emptyList(), phraseCount)

        /** Parse a value produced by [encode]; null on structural garbage. Unknown
         *  phrase labels become empty cells (forward compatibility). [resolve] is
         *  the phrase library (built-ins by default; pass a merged lookup when
         *  custom phrases exist). */
        fun decode(
            s: String,
            resolve: (String) -> PresetTrack? = PercussionBuiltins::presetByLabel,
        ): DrumBlock? {
            val eq = s.indexOf('=')
            if (eq <= 0) return null
            val name = s.substring(0, eq)
            val body = s.substring(eq + 1)
            if (body.isEmpty()) return null
            val tracks = ArrayList<BlockTrack>()
            var phraseCount = -1
            for (trackStr in body.split("|")) {
                val colon = trackStr.indexOf(':')
                if (colon <= 0) return null
                val inst = PercussionCatalog.resolve(trackStr.substring(0, colon)) ?: continue
                fun parseCell(lbl: String): PresetTrack? {
                    if (lbl.isEmpty()) return null
                    // "label@swing" = a per-cell swing override on the library phrase.
                    val at = lbl.lastIndexOf('@')
                    val overridden = if (at > 0) lbl.substring(at + 1).toIntOrNull() else null
                    return if (overridden != null) {
                        resolve(lbl.substring(0, at))?.copy(swing = overridden.coerceIn(0, 100))
                    } else {
                        resolve(lbl)
                    }
                }
                var parts = trackStr.substring(colon + 1).split(",")
                // A leading "^cell" is the track's OPENING (plays once, pass 1).
                var opening: PresetTrack? = null
                if (parts.isNotEmpty() && parts[0].startsWith("^")) {
                    opening = parseCell(parts[0].drop(1))
                    parts = parts.drop(1)
                }
                val cells = parts.map(::parseCell)
                if (phraseCount == -1) phraseCount = cells.size
                if (cells.size != phraseCount) return null
                tracks.add(BlockTrack(inst, cells, opening))
            }
            if (phraseCount !in 1..MAX_PHRASES) return null
            return runCatching { DrumBlock(name, tracks, phraseCount) }.getOrNull()
        }
    }
}

/**
 * BUILT-IN blocks (encoded [DrumBlock] strings): offered in the Blocks Load…
 * list above the user's saved blocks, decoded against the CURRENT phrase
 * library so custom phrases with matching labels still substitute. Keep in
 * sync with chorect-web's BUILTIN_BLOCKS.
 */
val BUILTIN_BLOCKS: List<String> = listOf(
    // Nadav's tamborim study block: Entrada 1 opening, then teleco-teco
    // alternating with its three variations across 8 phrases.
    "Tamborim Block=tamborim:^Tamborim — Entrada 1,Tamborim — Teleco-teco," +
        "Tamborim — Telecoteco Var 1,Tamborim — Teleco-teco,Tamborim — Telecoteco Var 2," +
        "Tamborim — Teleco-teco,Tamborim — Telecoteco Var 3,Tamborim — Telecoteco Var 1," +
        "Tamborim — Telecoteco Var 2",
)

/**
 * Persistence codec for USER-DEFINED phrases (custom track presets): a track
 * built in the Beat editor, saved by name, joining the phrase library. A custom
 * phrase with a built-in's label REPLACES it everywhere (edit-and-resave).
 * Format: "label=instBaseId:swing:cells" (cells = raw values, "-" = silent).
 * Labels must not contain '=', ':', ',', '|', '@', '~', or newlines.
 */
fun encodePresetTrack(p: PresetTrack): String =
    p.label + "=" + PercussionCatalog.baseId(p.instrument.id) + ":" + p.swing + ":" +
        p.template.joinToString(",") { it?.toString() ?: "-" }

fun decodePresetTrack(s: String): PresetTrack? {
    val eq = s.indexOf('=')
    if (eq <= 0) return null
    val label = s.substring(0, eq)
    val parts = s.substring(eq + 1).split(":")
    if (parts.size != 3) return null
    val inst = PercussionCatalog.byId(parts[0]) ?: return null
    val swing = parts[1].toIntOrNull()?.coerceIn(0, 100) ?: return null
    val cells = parts[2].split(",").map { if (it == "-") null else it.toIntOrNull() ?: return null }
    if (cells.size != 16) return null
    return PresetTrack(label, inst, cells, swing = swing)
}

/** The phrase library: built-ins with [custom] phrases merged in — a custom
 *  phrase whose label matches a built-in REPLACES it; new labels append. */
fun mergedPresets(custom: Collection<PresetTrack>): List<PresetTrack> {
    val byLabel = LinkedHashMap<String, PresetTrack>()
    for (p in PercussionBuiltins.PRESET_TRACKS) byLabel[p.label] = p
    for (p in custom) byLabel[p.label] = p
    return byLabel.values.toList()
}

/**
 * The 16-slot template a block cell actually plays: applies the RETURN RULE —
 * when the PREVIOUS column's phrase (wrapping around the loop) declares
 * [PresetTrack.addsReturnDownbeat] and this phrase's slot 0 is empty, slot 0
 * gains this phrase's measure-2 downbeat stroke (slot 8), accented.
 */
fun materializedTemplate(phrase: PresetTrack?, prev: PresetTrack?): List<Int?>? {
    if (phrase == null) return null
    if (prev?.addsReturnDownbeat != true || phrase.template.getOrNull(0) != null) return phrase.template
    val m2 = phrase.template.getOrNull(8) ?: return phrase.template
    return phrase.template.toMutableList().also { it[0] = (m2 % PERCUSSION_ACCENT) + PERCUSSION_ACCENT }
}

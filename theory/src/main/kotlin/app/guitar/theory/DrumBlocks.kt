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
     *  '=', '|', ':', ',' (or a trailing "@<digits>"). */
    fun encode(): String =
        name + "=" + tracks.joinToString("|") { t ->
            t.instrument.id + ":" + t.cells.joinToString(",") { c ->
                if (c == null) ""
                else {
                    val libSwing = PercussionBuiltins.presetByLabel(c.label)?.swing ?: 0
                    if (c.swing != libSwing) "${c.label}@${c.swing}" else c.label
                }
            }
        }

    companion object {
        const val MAX_PHRASES = 8

        fun empty(name: String = "Block 1", phraseCount: Int = 4): DrumBlock =
            DrumBlock(name, emptyList(), phraseCount)

        /** Parse a value produced by [encode]; null on structural garbage. Unknown
         *  phrase labels become empty cells (forward compatibility). */
        fun decode(s: String): DrumBlock? {
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
                val cells = trackStr.substring(colon + 1).split(",").map { lbl ->
                    if (lbl.isEmpty()) null
                    else {
                        // "label@swing" = a per-cell swing override on the library phrase.
                        val at = lbl.lastIndexOf('@')
                        val overridden = if (at > 0) lbl.substring(at + 1).toIntOrNull() else null
                        if (overridden != null) {
                            PercussionBuiltins.presetByLabel(lbl.substring(0, at))
                                ?.copy(swing = overridden.coerceIn(0, 100))
                        } else {
                            PercussionBuiltins.presetByLabel(lbl)
                        }
                    }
                }
                if (phraseCount == -1) phraseCount = cells.size
                if (cells.size != phraseCount) return null
                tracks.add(BlockTrack(inst, cells))
            }
            if (phraseCount !in 1..MAX_PHRASES) return null
            return runCatching { DrumBlock(name, tracks, phraseCount) }.getOrNull()
        }
    }
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

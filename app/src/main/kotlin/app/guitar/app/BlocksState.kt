package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.audio.PercussionSynth
import app.guitar.theory.BlockFile
import app.guitar.theory.DrumBlock
import app.guitar.theory.PercussionBuiltins
import app.guitar.theory.PercussionBuiltins.PresetTrack
import app.guitar.theory.PercussionCatalog
import app.guitar.theory.PercussionInstrument
import app.guitar.theory.PercussionMeter
import app.guitar.theory.PercussionTiming
import app.guitar.theory.PERCUSSION_ACCENT
import app.guitar.theory.PERCUSSION_DYN
import app.guitar.theory.PERCUSSION_DYN_FACTORS
import app.guitar.theory.materializedTemplate
import app.guitar.theory.mergedPresets
import app.guitar.theory.encodePresetTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Blocks: phrase-sequencer state + scheduler (drum machine's Blocks view).
 * Mirror of chorect-web's BlocksState; design in
 * docs/superpowers/specs/2026-07-22-drum-blocks-design.md.
 *
 * Playback loops the block column by column. Each column is 16 STRAIGHT slots
 * long (2 bars of 2/4 in 16ths) for every track; within the column each track's
 * phrase plays with ITS OWN swing micro-timing (its own clock). Because swing
 * only moves onsets inside a beat and keeps the beat anchors exact, all tracks
 * re-align at every beat — a swung chamada over a straight teleco-teco never
 * drifts, and a swung phrase followed by a straight one snaps back naturally.
 */
@Stable
class BlocksState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
    private val repo: TuningRepository,
    private val sampleLoader: (PercussionInstrument, Int) -> FloatArray? = { _, _ -> null },
) {
    var block by mutableStateOf(DrumBlock.empty())
        private set
    var bpm by mutableStateOf(80)
    var isPlaying by mutableStateOf(false)
        private set

    /** Column currently sounding (0-based), or -1 when stopped. */
    var currentCol by mutableStateOf(-1)
        private set

    /** 16th-slot inside the current column (0-15, straight clock), or -1 when
     *  stopped — drives the playhead inside the mini phrase grids. */
    var currentSlot by mutableStateOf(-1)
        private set

    /** True while the block's very FIRST column plays (openings sound instead of
     *  each track's first phrase); false once the loop wraps. */
    var openingPass by mutableStateOf(false)
        private set

    /** Overlay a wood-click metronome (higher click on each bar's "1"). */
    var metronomeOn by mutableStateOf(false)
        private set
    fun toggleMetronome() { metronomeOn = !metronomeOn }
    /** Play a 2-beat count-in (16th ticks, downbeats accented) before the loop starts. */
    var countIn by mutableStateOf(false)
        private set
    fun toggleCountIn() { countIn = !countIn }
    private val mClick: FloatArray by lazy { synthWood(2000.0, 45) }
    private val mAccent: FloatArray by lazy { synthWood(2800.0, 45) }
    private fun synthWood(freqHz: Double, ms: Int, sr: Int = audio.sampleRate): FloatArray {
        val n = sr * ms / 1000
        val buf = FloatArray(n)
        val w = 2.0 * Math.PI * freqHz / sr
        for (i in 0 until n) buf[i] = (Math.sin(w * i) * Math.exp(-6.0 * i / n) * 0.7).toFloat()
        return buf
    }

    /** USER-DEFINED phrases (custom track presets) + raw saved-block lines,
     *  mirrored from the repo so the library resolves synchronously. */
    var customPresets by mutableStateOf<Map<String, PresetTrack>>(emptyMap())
        private set
    private var savedBlockLines by mutableStateOf<List<String>>(emptyList())

    init {
        scope.launch { repo.drumTrackPresets.collect { customPresets = it } }
        scope.launch { repo.drumBlockLines.collect { savedBlockLines = it } }
        // Open the Blocks view on the built-in Tamborim Block by default.
        builtinBlocks.firstOrNull()?.let { block = it }
    }

    /** Library lookup: a user phrase with a built-in's label REPLACES it. */
    fun resolvePreset(label: String): PresetTrack? =
        customPresets[label] ?: PercussionBuiltins.presetByLabel(label)

    /** All phrases (built-ins overridden/extended by the user's). */
    fun allPresets(): List<PresetTrack> = mergedPresets(customPresets.values)

    /** Store a complete phrase in the library as-is (its own swing included) —
     *  used by phrase-file import. Returns false on an empty/reserved-char label. */
    fun savePhrase(p: PresetTrack): Boolean {
        val clean = p.label.trim()
        if (clean.isEmpty() || clean.any { it in "=:,|@~^" || it == '\n' }) return false
        scope.launch { repo.saveDrumTrackPreset(encodePresetTrack(p.copy(label = clean))) }
        return true
    }

    /** Save a Beat-editor track as a named phrase (custom track preset). The row's
     *  accents + dynamics ride along in the raw values; clones save as their base
     *  instrument. Returns false when the label is empty/has reserved chars. */
    fun saveTrackAsPreset(inst: PercussionInstrument, row: List<Int?>, label: String): Boolean {
        val base = PercussionCatalog.byId(PercussionCatalog.baseId(inst.id)) ?: inst
        val template = List(16) { i -> row.getOrNull(i) }
        return savePhrase(PresetTrack(label, base, template, swing = 0))
    }

    fun deleteTrackPreset(label: String) { scope.launch { repo.deleteDrumTrackPreset(label) } }

    private var job: Job? = null
    private val synth = PercussionSynth(audio.sampleRate)
    private val cache = HashMap<Pair<PercussionInstrument, Int>, FloatArray>()

    private fun buffer(inst: PercussionInstrument, voice: Int): FloatArray =
        cache.getOrPut(inst to voice) { sampleLoader(inst, voice) ?: synth.synthesize(inst, voice) }

    // ---- editing ----

    fun rename(name: String) { block = block.copy(name = name.ifBlank { "Block" }) }
    fun addTrack(inst: PercussionInstrument) { block = block.withTrack(inst) }
    fun removeTrack(index: Int) { block = block.withoutTrack(index) }
    /** col == -1 targets the track's OPENING cell (plays once, pass 1). */
    fun setCell(track: Int, col: Int, phrase: PresetTrack?) {
        block = if (col == -1) block.withOpeningCell(track, phrase) else block.withCell(track, col, phrase)
    }

    /** Override one cell's swing (0–100): the phrase keeps its own clock, so a
     *  swung cell over straight tracks stays bar-aligned. Saved with the block.
     *  col == -1 targets the opening cell. */
    fun setCellSwing(track: Int, col: Int, swing: Int) {
        val t = block.tracks.getOrNull(track)
        val phrase = (if (col == -1) t?.opening else t?.cells?.getOrNull(col)) ?: return
        setCell(track, col, phrase.copy(swing = swing.coerceIn(0, 100)))
    }
    fun setPhraseCount(n: Int) { block = block.withPhraseCount(n) }
    /** Drag-reorder: move a track's phrase from [fromCol] to [toCol]. */
    fun moveCell(track: Int, fromCol: Int, toCol: Int) { block = block.movedCell(track, fromCol, toCol) }
    fun clear() { block = DrumBlock.empty(block.name, block.phraseCount) }

    /** Phrases available for a track's instrument (block cells are per-instrument). */
    fun phrasesFor(inst: PercussionInstrument): List<PresetTrack> {
        val base = PercussionCatalog.baseId(inst.id)
        return allPresets().filter { PercussionCatalog.baseId(it.instrument.id) == base }
    }

    fun mergeWith(other: DrumBlock) {
        block.mergedWith(other)?.let { block = it }
    }

    // ---- save / load ----

    /** Saved blocks decoded against the CURRENT phrase library (custom + built-in). */
    val savedBlocks: Map<String, DrumBlock>
        get() {
            val out = LinkedHashMap<String, DrumBlock>()
            for (line in savedBlockLines) {
                val b = DrumBlock.decode(line, ::resolvePreset) ?: continue
                out[b.name] = b
            }
            return out
        }

    /** BUILT-IN blocks, decoded against the current phrase library (custom
     *  phrases with matching labels substitute into them too). */
    val builtinBlocks: List<DrumBlock>
        get() = app.guitar.theory.BUILTIN_BLOCKS.mapNotNull { DrumBlock.decode(it, ::resolvePreset) }

    fun saveCurrent() {
        val encoded = block.encode(::resolvePreset)
        scope.launch { repo.saveDrumBlock(encoded) }
    }

    // ---- block files (export / import) ----

    /** Block file: the encoded block plus every USER-DEFINED phrase it references
     *  (cells + openings), so the block is portable to another device. */
    fun exportBlockFile(): String {
        val used = LinkedHashMap<String, PresetTrack>()
        for (t in block.tracks) {
            for (cell in t.cells + t.opening) {
                if (cell == null || used.containsKey(cell.label)) continue
                customPresets[cell.label]?.let { used[cell.label] = it }
            }
        }
        return BlockFile.encode(block.encode(::resolvePreset), used.values.toList())
    }

    /** Import a block file: restore its embedded phrases into the library, then
     *  decode the block (preferring the embedded phrases) and load it. */
    fun importBlockFile(text: String): Boolean {
        val (encodedBlock, phrases) = BlockFile.decode(text) ?: return false
        val byLabel = phrases.associateBy { it.label }
        for (p in phrases) savePhrase(p)
        val b = DrumBlock.decode(encodedBlock) { lbl -> byLabel[lbl] ?: resolvePreset(lbl) } ?: return false
        loadBlock(b)
        return true
    }
    fun loadBlock(b: DrumBlock) { block = b }
    fun deleteSaved(name: String) { scope.launch { repo.deleteDrumBlock(name) } }

    /** Instruments offered by "+ Track ▾" (catalog order; repeats allowed). */
    fun instrumentsToAdd(): List<PercussionInstrument> = PercussionCatalog.ALL

    // ---- playback ----

    fun toggle() { if (isPlaying) stop() else start() }

    fun start() {
        if (isPlaying || block.isEmpty()) return
        isPlaying = true
        job = scope.launch {
            val sr = audio.sampleRate
            val meter = PercussionMeter.DEFAULT   // phrases are 16 slots of 2/4 in 16ths
            var colStartNanos = System.nanoTime() + 60_000_000L
            // Count-in: two beats of 16th ticks (each beat's downbeat accented) before the loop.
            if (countIn) {
                val stepMs = PercussionTiming.slotMsExact(bpm, meter.division)
                val ticks = 2 * meter.slotsPerBeat
                for (i in 0 until ticks) {
                    val accent = i % meter.slotsPerBeat == 0
                    val delayMs = ((colStartNanos - System.nanoTime()) / 1_000_000 + Math.round(i * stepMs)).coerceAtLeast(0)
                    audio.playSamplesAt(if (accent) mAccent else mClick, if (accent) 0.9f else 0.55f, (delayMs * sr / 1000).toInt())
                }
                colStartNanos += Math.round(ticks * stepMs * 1_000_000)
            }
            var colIndex = 0
            while (isPlaying) {
                val snapshot = block            // re-read each column so edits apply next column
                if (snapshot.tracks.isEmpty()) { stop(); return@launch }
                val cols = snapshot.phraseCount
                val c = colIndex % cols
                currentCol = c
                openingPass = colIndex == 0
                // Schedule the whole column for every track: each phrase with ITS swing.
                // What a track plays at absolute column ci: its OPENING at ci 0 (if
                // set), its cells afterwards — so `prev` (the return rule's input)
                // tracks what actually sounded, and no return rule fires before
                // anything played.
                for ((ti, t) in snapshot.tracks.withIndex()) {
                    fun playedAt(ci: Int): PresetTrack? = when {
                        ci < 0 -> null
                        ci == 0 && t.opening != null -> t.opening
                        else -> t.cells[ci % cols]
                    }
                    val phrase = playedAt(colIndex)
                    val prev = playedAt(colIndex - 1)
                    val tmpl = materializedTemplate(phrase, prev) ?: continue
                    val swing = phrase?.swing ?: 0
                    var onsetMs = 0.0
                    for (slot in 0 until 16) {
                        val raw = tmpl.getOrNull(slot)
                        if (raw != null) {
                            val voice = raw % PERCUSSION_ACCENT
                            val accented = (raw / PERCUSSION_ACCENT) % 10 == 1
                            val dyn = raw / PERCUSSION_DYN
                            val gain = (if (accented) 1.4f else 1f) * PERCUSSION_DYN_FACTORS[dyn]
                            val delayMs = ((colStartNanos - System.nanoTime()) / 1_000_000 + Math.round(onsetMs)).coerceAtLeast(0)
                            // Self-choke per TRACK (blocks may repeat an instrument —
                            // two pandeiro players don't damp each other).
                            val chokeKey = if (t.instrument.selfChoke) "${t.instrument.id}@$ti" else null
                            audio.playSamplesAt(buffer(t.instrument, voice), gain, (delayMs * sr / 1000).toInt(), chokeKey)
                        }
                        onsetMs += PercussionTiming.swungSlotMsExact(slot, bpm, swing, meter)
                    }
                }
                // Metronome click track: one click per beat on the straight clock,
                // higher click on each bar's "1" (bars are 8 slots in 2/4 · 1/16).
                if (metronomeOn) {
                    val baseMs = PercussionTiming.slotMsExact(bpm, meter.division)
                    var slot = 0
                    while (slot < 16) {
                        val barDown = slot % meter.slotsPerBar == 0
                        val delayMs = ((colStartNanos - System.nanoTime()) / 1_000_000 + Math.round(slot * baseMs)).coerceAtLeast(0)
                        audio.playSamplesAt(if (barDown) mAccent else mClick, if (barDown) 0.9f else 0.6f, (delayMs * sr / 1000).toInt())
                        slot += meter.slotsPerBeat
                    }
                }
                // Columns advance on the STRAIGHT clock (16 × base slot) for all
                // tracks. Walk the 16 slots for the UI playhead (audio is already
                // queued); the last delay ends ~30 ms early so the next column
                // schedules in time.
                val slotDurMs = PercussionTiming.slotMsExact(bpm, meter.division)
                for (sl in 0 until 16) {
                    if (!isPlaying) break
                    currentSlot = sl
                    val targetNanos = colStartNanos + Math.round((sl + 1) * slotDurMs * 1_000_000)
                    val early = if (sl == 15) 30 else 0
                    delay(((targetNanos - System.nanoTime()) / 1_000_000 - early).coerceAtLeast(0))
                }
                colStartNanos += Math.round(slotDurMs * 16 * 1_000_000)
                colIndex++
            }
        }
    }

    fun stop() {
        isPlaying = false
        job?.cancel()
        job = null
        currentCol = -1
        currentSlot = -1
        openingPass = false
        audio.stop()
    }

    fun release() = stop()
}

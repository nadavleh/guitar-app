package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.audio.PercussionSynth
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

    /** Overlay a wood-click metronome (higher click on each bar's "1"). */
    var metronomeOn by mutableStateOf(false)
        private set
    fun toggleMetronome() { metronomeOn = !metronomeOn }
    private val mClick: FloatArray by lazy { synthWood(2000.0, 45) }
    private val mAccent: FloatArray by lazy { synthWood(2800.0, 45) }
    private fun synthWood(freqHz: Double, ms: Int, sr: Int = 44100): FloatArray {
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
        if (clean.isEmpty() || clean.any { it in "=:,|@~" || it == '\n' }) return false
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
    private val synth = PercussionSynth()
    private val cache = HashMap<Pair<PercussionInstrument, Int>, FloatArray>()

    private fun buffer(inst: PercussionInstrument, voice: Int): FloatArray =
        cache.getOrPut(inst to voice) { sampleLoader(inst, voice) ?: synth.synthesize(inst, voice) }

    // ---- editing ----

    fun rename(name: String) { block = block.copy(name = name.ifBlank { "Block" }) }
    fun addTrack(inst: PercussionInstrument) { block = block.withTrack(inst) }
    fun removeTrack(index: Int) { block = block.withoutTrack(index) }
    fun setCell(track: Int, col: Int, phrase: PresetTrack?) { block = block.withCell(track, col, phrase) }

    /** Override one cell's swing (0–100): the phrase keeps its own clock, so a
     *  swung cell over straight tracks stays bar-aligned. Saved with the block. */
    fun setCellSwing(track: Int, col: Int, swing: Int) {
        val phrase = block.tracks.getOrNull(track)?.cells?.getOrNull(col) ?: return
        setCell(track, col, phrase.copy(swing = swing.coerceIn(0, 100)))
    }
    fun setPhraseCount(n: Int) { block = block.withPhraseCount(n) }
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

    fun saveCurrent() {
        val encoded = block.encode(::resolvePreset)
        scope.launch { repo.saveDrumBlock(encoded) }
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
            val sr = 44100
            val meter = PercussionMeter.DEFAULT   // phrases are 16 slots of 2/4 in 16ths
            var colStartNanos = System.nanoTime() + 60_000_000L
            var colIndex = 0
            while (isPlaying) {
                val snapshot = block            // re-read each column so edits apply next column
                if (snapshot.tracks.isEmpty()) { stop(); return@launch }
                val cols = snapshot.phraseCount
                val c = colIndex % cols
                currentCol = c
                // Schedule the whole column for every track: each phrase with ITS swing.
                for (t in snapshot.tracks) {
                    val phrase = t.cells[c]
                    val prev = t.cells[(c - 1 + cols) % cols]
                    val tmpl = materializedTemplate(phrase, prev) ?: continue
                    val swing = phrase?.swing ?: 0
                    var onsetMs = 0L
                    for (slot in 0 until 16) {
                        val raw = tmpl.getOrNull(slot)
                        if (raw != null) {
                            val voice = raw % PERCUSSION_ACCENT
                            val accented = (raw / PERCUSSION_ACCENT) % 10 == 1
                            val dyn = raw / PERCUSSION_DYN
                            val gain = (if (accented) 1.4f else 1f) * PERCUSSION_DYN_FACTORS[dyn]
                            val delayMs = ((colStartNanos - System.nanoTime()) / 1_000_000 + onsetMs).coerceAtLeast(0)
                            audio.playSamplesAt(buffer(t.instrument, voice), gain, (delayMs * sr / 1000).toInt())
                        }
                        onsetMs += PercussionTiming.swungSlotMs(slot, bpm, swing, meter)
                    }
                }
                // Metronome click track: one click per beat on the straight clock,
                // higher click on each bar's "1" (bars are 8 slots in 2/4 · 1/16).
                if (metronomeOn) {
                    val baseMs = PercussionTiming.slotMs(bpm, meter.division)
                    var slot = 0
                    while (slot < 16) {
                        val barDown = slot % meter.slotsPerBar == 0
                        val delayMs = ((colStartNanos - System.nanoTime()) / 1_000_000 + slot * baseMs).coerceAtLeast(0)
                        audio.playSamplesAt(if (barDown) mAccent else mClick, if (barDown) 0.9f else 0.6f, (delayMs * sr / 1000).toInt())
                        slot += meter.slotsPerBeat
                    }
                }
                // Columns advance on the STRAIGHT clock (16 × base slot) for all tracks.
                val colDurMs = PercussionTiming.slotMs(bpm, meter.division) * 16
                colStartNanos += colDurMs * 1_000_000
                colIndex++
                delay(((colStartNanos - System.nanoTime()) / 1_000_000 - 30).coerceAtLeast(0))
            }
        }
    }

    fun stop() {
        isPlaying = false
        job?.cancel()
        job = null
        currentCol = -1
        audio.stop()
    }

    fun release() = stop()
}

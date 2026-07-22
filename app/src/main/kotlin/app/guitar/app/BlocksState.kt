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
    fun setPhraseCount(n: Int) { block = block.withPhraseCount(n) }
    fun clear() { block = DrumBlock.empty(block.name, block.phraseCount) }

    /** Phrases available for a track's instrument (block cells are per-instrument). */
    fun phrasesFor(inst: PercussionInstrument): List<PresetTrack> {
        val base = PercussionCatalog.baseId(inst.id)
        return PercussionBuiltins.PRESET_TRACKS.filter { PercussionCatalog.baseId(it.instrument.id) == base }
    }

    fun mergeWith(other: DrumBlock) {
        block.mergedWith(other)?.let { block = it }
    }

    // ---- save / load ----

    val savedBlocks get() = repo.drumBlocks

    fun saveCurrent() { val snapshot = block; scope.launch { repo.saveDrumBlock(snapshot) } }
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

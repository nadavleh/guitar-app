package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.audio.PercussionSynth
import app.guitar.theory.PERCUSSION_SLOTS
import app.guitar.theory.PercussionInstrument
import app.guitar.theory.PercussionPattern
import app.guitar.theory.PercussionTiming
import app.guitar.theory.PercussionVoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State + scheduler for the samba percussion looper (drum-machine tab).
 *
 * Holds the editable [pattern], transport ([bpm], [isPlaying], [currentSlot]),
 * and a synth-buffer cache. Voices are synthesized once on first use and replayed
 * from the cache, so the per-slot tick just pushes ready buffers into the mixer.
 *
 * App-lifetime (owned by AppState), so leaving the screen and coming back keeps
 * the pattern you built.
 */
@Stable
class SambaLooperState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
) {
    var pattern by mutableStateOf(PercussionPattern.SAMBA)
        private set
    var bpm by mutableStateOf(100)
    var isPlaying by mutableStateOf(false)
        private set
    /** Slot currently sounding (0..15), or -1 when stopped. Drives the playhead. */
    var currentSlot by mutableStateOf(-1)
        private set

    /** Tracks muted instruments and soloed instruments. Audible = not muted AND
     *  (no solo active OR this instrument is soloed). */
    var muted by mutableStateOf<Set<PercussionInstrument>>(emptySet())
        private set
    var soloed by mutableStateOf<Set<PercussionInstrument>>(emptySet())
        private set

    fun toggleMute(inst: PercussionInstrument) {
        muted = if (inst in muted) muted - inst else muted + inst
    }

    fun toggleSolo(inst: PercussionInstrument) {
        soloed = if (inst in soloed) soloed - inst else soloed + inst
    }

    fun isAudible(inst: PercussionInstrument): Boolean =
        inst !in muted && (soloed.isEmpty() || inst in soloed)

    private var job: Job? = null
    private val synth = PercussionSynth()
    private val cache = HashMap<Pair<PercussionInstrument, Int>, FloatArray>()

    private fun buffer(instrument: PercussionInstrument, voiceIndex: Int): FloatArray =
        cache.getOrPut(instrument to voiceIndex) { synth.synthesize(instrument, voiceIndex) }

    /** Cycle a cell's voice and, if it became audible, preview the new voice. */
    fun toggleSlot(instrument: PercussionInstrument, slot: Int) {
        pattern = pattern.cycled(instrument, slot)
        val v = pattern.voiceAt(instrument, slot)
        if (v != null && !isPlaying) audio.playSamples(buffer(instrument, v))
    }

    /** Audition a single voice (used by the row-label tap). */
    fun preview(instrument: PercussionInstrument, voiceIndex: Int) {
        audio.playSamples(buffer(instrument, voiceIndex))
    }

    /** Clear a single cell (long-press) without cycling through the voices. */
    fun clearCell(instrument: PercussionInstrument, slot: Int) {
        pattern = pattern.withCell(instrument, slot, null)
    }

    fun clearRow(instrument: PercussionInstrument) {
        pattern = pattern.clearedRow(instrument)
    }

    fun clearAll() {
        pattern = PercussionPattern.empty()
    }

    fun loadSamba() {
        pattern = PercussionPattern.SAMBA
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true
        job = scope.launch {
            while (isPlaying) {
                for (slot in 0 until PERCUSSION_SLOTS) {
                    if (!isPlaying) break
                    currentSlot = slot
                    for (inst in PercussionInstrument.entries) {
                        if (!isAudible(inst)) continue
                        val v = pattern.voiceAt(inst, slot) ?: continue
                        audio.playSamples(buffer(inst, v))
                    }
                    delay(PercussionTiming.slotMs(bpm))
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        job?.cancel()
        job = null
        currentSlot = -1
        audio.stop()
    }

    fun release() {
        stop()
    }
}

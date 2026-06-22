package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.audio.PercussionSynth
import app.guitar.theory.PercussionInstrument
import app.guitar.theory.PercussionMeter
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
 * and a per-voice buffer cache. Each voice is loaded once on first use — a bundled
 * one-shot sample via [sampleLoader], falling back to [PercussionSynth] — then
 * replayed from the cache, so the per-slot tick just pushes ready buffers into the mixer.
 *
 * App-lifetime (owned by AppState), so leaving the screen and coming back keeps
 * the pattern you built.
 */
@Stable
class SambaLooperState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
    private val repo: TuningRepository,
    /** Loads a bundled one-shot sample for (instrument, voice), or null to fall
     *  back to the built-in synth. Injected so the pure state stays Context-free. */
    private val sampleLoader: (PercussionInstrument, Int) -> FloatArray? = { _, _ -> null },
) {
    var pattern by mutableStateOf(PercussionPattern.SAMBA)
        private set
    var bpm by mutableStateOf(100)
    /** Brazilian 16th-note swing, 0..100 % (0 = straight). */
    var swing by mutableStateOf(0)
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

    /** Per-instrument playback volume, 0f..1f (1 = full). Applied as a gain at
     *  mix time so the cached one-shot buffers are never mutated. App-lifetime,
     *  so the mix you dial in survives leaving and returning to the screen. */
    var volumes by mutableStateOf(
        PercussionInstrument.entries.associateWith { 1f },
    )
        private set

    fun volumeOf(inst: PercussionInstrument): Float = volumes[inst] ?: 1f

    fun setVolume(inst: PercussionInstrument, value: Float) {
        volumes = volumes + (inst to value.coerceIn(0f, 1f))
    }

    private var job: Job? = null
    private val synth = PercussionSynth()
    private val cache = HashMap<Pair<PercussionInstrument, Int>, FloatArray>()

    private fun buffer(instrument: PercussionInstrument, voiceIndex: Int): FloatArray =
        cache.getOrPut(instrument to voiceIndex) {
            // Prefer a bundled sample; fall back to the on-device synth if absent.
            sampleLoader(instrument, voiceIndex) ?: synth.synthesize(instrument, voiceIndex)
        }

    /** Cycle a cell's voice and, if it became audible, preview the new voice. */
    fun toggleSlot(instrument: PercussionInstrument, slot: Int) {
        pattern = pattern.cycled(instrument, slot)
        val v = pattern.voiceAt(instrument, slot)
        if (v != null && !isPlaying) audio.playSamples(buffer(instrument, v), volumeOf(instrument))
    }

    /** Audition a single voice (used by the row-label tap). */
    fun preview(instrument: PercussionInstrument, voiceIndex: Int) {
        audio.playSamples(buffer(instrument, voiceIndex), volumeOf(instrument))
    }

    /** Clear a single cell (long-press) without cycling through the voices. */
    fun clearCell(instrument: PercussionInstrument, slot: Int) {
        pattern = pattern.withCell(instrument, slot, null)
    }

    fun clearRow(instrument: PercussionInstrument) {
        pattern = pattern.clearedRow(instrument)
    }

    fun clearAll() {
        pattern = PercussionPattern.empty(pattern.meter)
    }

    /** Load the built-in "stock samba" groove. */
    fun loadSamba() {
        pattern = PercussionPattern.SAMBA
    }

    // ---- Meter (bars / time signature / division) ----

    val meter get() = pattern.meter

    /** Re-fit the current pattern onto [newMeter] (cells preserved by slot index). */
    fun setMeter(newMeter: PercussionMeter) {
        pattern = pattern.withMeter(newMeter)
    }

    fun setBars(bars: Int) =
        setMeter(meter.copy(bars = bars.coerceIn(1, 8)))

    /** Set the time signature. If the new beat unit can't host the current
     *  division (division must be a multiple of beatUnit), bump the division up to
     *  the beat unit so the meter stays valid. */
    fun setTimeSignature(beatsPerBar: Int, beatUnit: Int) {
        val beats = beatsPerBar.coerceIn(1, 12)
        val unit = if (beatUnit in PercussionMeter.BEAT_UNITS) beatUnit else 4
        val div = if (meter.division % unit == 0) meter.division
                  else PercussionMeter.DIVISIONS.first { it % unit == 0 && it >= unit }
        setMeter(meter.copy(beatsPerBar = beats, beatUnit = unit, division = div))
    }

    fun setDivision(division: Int) {
        if (division !in PercussionMeter.DIVISIONS) return
        if (division % meter.beatUnit != 0) return
        setMeter(meter.copy(division = division))
    }

    /** Translate (rotate) the whole loop by [n] slots with wrap-around. */
    fun translate(n: Int) {
        pattern = pattern.translated(n)
    }

    // ---- Save / load user beats ----

    /** User-saved beats, by name (observe in the UI). */
    val savedPatterns get() = repo.drumPatterns

    /** Save the current pattern under [name]. */
    fun saveCurrent(name: String) {
        val snapshot = pattern
        scope.launch { repo.saveDrumPattern(name, snapshot) }
    }

    /** Replace the editable pattern with a saved/loaded one. */
    fun loadPattern(p: PercussionPattern) {
        pattern = p
    }

    fun deleteSaved(name: String) {
        scope.launch { repo.deleteDrumPattern(name) }
    }

    fun start() {
        if (isPlaying) return
        isPlaying = true
        job = scope.launch {
            while (isPlaying) {
                val snapshot = pattern        // re-read each bar so meter edits take effect
                val division = snapshot.meter.division
                for (slot in 0 until snapshot.slots) {
                    if (!isPlaying) break
                    currentSlot = slot
                    for (inst in PercussionInstrument.entries) {
                        if (!isAudible(inst)) continue
                        val v = snapshot.voiceAt(inst, slot) ?: continue
                        audio.playSamples(buffer(inst, v), volumeOf(inst))
                    }
                    delay(PercussionTiming.swungSlotMs(slot, bpm, swing, division))
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

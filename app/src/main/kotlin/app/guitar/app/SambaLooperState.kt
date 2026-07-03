package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.audio.PercussionSynth
import app.guitar.theory.PercussionCatalog
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
    var pattern by mutableStateOf(PercussionPattern.empty())
        private set
    var bpm by mutableStateOf(140)
    /** Brazilian 16th-note swing, 0..100 % (0 = straight). */
    var swing by mutableStateOf(0)
    /** Metronome click on each beat (accented on bar downbeats). */
    var metronome by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
        private set

    // Tap-tempo: average the intervals of the recent taps (2 s window, last 6).
    private val tapTimes = ArrayList<Long>()
    fun tapTempo(nowMs: Long = System.currentTimeMillis()) {
        if (tapTimes.isNotEmpty() && nowMs - tapTimes.last() > 2000) tapTimes.clear()
        tapTimes.add(nowMs)
        while (tapTimes.size > 6) tapTimes.removeAt(0)
        if (tapTimes.size >= 2) {
            val avg = (tapTimes.last() - tapTimes.first()).toDouble() / (tapTimes.size - 1)
            bpm = (60_000.0 / avg).toInt().coerceIn(60, 200)
        }
    }
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

    /** Per-instrument playback volume, 0f..1f (1 = full), keyed by instrument id.
     *  Applied as a gain at mix time so the cached one-shot buffers are never
     *  mutated. App-lifetime, so the mix you dial in survives leaving and returning
     *  to the screen; instruments not yet dialled default to full. */
    var volumes by mutableStateOf<Map<String, Float>>(emptyMap())
        private set

    fun volumeOf(inst: PercussionInstrument): Float = volumes[inst.id] ?: 1f

    fun setVolume(inst: PercussionInstrument, value: Float) {
        volumes = volumes + (inst.id to value.coerceIn(0f, 1f))
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

    /** Toggle the accent on a non-silent cell (Accent tool). */
    fun toggleAccent(instrument: PercussionInstrument, slot: Int) {
        pattern = pattern.accentToggled(instrument, slot)
    }

    /** Clear a single cell (long-press) without cycling through the voices. */
    fun clearCell(instrument: PercussionInstrument, slot: Int) {
        pattern = pattern.withCell(instrument, slot, null)
    }

    fun clearRow(instrument: PercussionInstrument) {
        pattern = pattern.clearedRow(instrument)
    }

    fun clearAll() {
        pattern = PercussionPattern.empty(pattern.instruments, pattern.meter)
    }

    // ---- Kit: add / remove instruments ----

    /** Catalog instruments not yet in the kit, in catalog order (for the picker). */
    fun instrumentsToAdd(): List<PercussionInstrument> =
        PercussionCatalog.ALL.filter { !pattern.hasInstrument(it) }

    /** Add [inst] to the kit (silent row) and audition its first voice. */
    fun addInstrument(inst: PercussionInstrument) {
        pattern = pattern.addInstrument(inst)
        if (!isPlaying) audio.playSamples(buffer(inst, 0), volumeOf(inst))
    }

    /** Remove [inst] from the kit, also clearing its mute/solo state. */
    fun removeInstrument(inst: PercussionInstrument) {
        pattern = pattern.removeInstrument(inst)
        muted = muted - inst
        soloed = soloed - inst
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

    // Metronome click buffers (lazy; accented downbeat vs plain beat).
    private val clickAccent by lazy { synth.metronomeClick(accent = true) }
    private val clickBeat by lazy { synth.metronomeClick(accent = false) }

    /** Frames until a buffer first reaches 90 % of its peak. Aligned hits sit at
     *  ~3 ms; crescendo articulations (shake rolls, long scrapes) bloom much later
     *  and are started early so their accent lands on the grid. */
    private val peakOffsetCache = HashMap<Pair<PercussionInstrument, Int>, Int>()
    private fun peakOffsetFrames(inst: PercussionInstrument, voiceIndex: Int, buf: FloatArray): Int =
        peakOffsetCache.getOrPut(inst to voiceIndex) {
            var peak = 0f
            for (s in buf) { val a = kotlin.math.abs(s); if (a > peak) peak = a }
            if (peak <= 0f) return@getOrPut 0
            val th = peak * 0.9f
            var i = 0
            while (i < buf.size && kotlin.math.abs(buf[i]) < th) i++
            i
        }

    /**
     * Lookahead sequencer. Each slot's audio is queued ONE SLOT AHEAD via
     * [AudioEngine.playSamplesAt] with a delay counted on the mixer's own
     * sample clock, computed from an absolute wall-clock schedule. So:
     *   - coroutine wake-up jitter no longer moves hits (it's absorbed by the
     *     delay), and timing errors never accumulate across the loop;
     *   - crescendo voices (peak later than ~20 ms) are started early — capped
     *     by the one-slot lookahead — so their peak lands on the beat.
     * The delay() loop itself only drives the UI playhead.
     */
    fun start() {
        if (isPlaying) return
        isPlaying = true
        job = scope.launch {
            val sr = 44100
            fun scheduleSlot(snapshot: PercussionPattern, slot: Int, delayMs: Long) {
                val baseFrames = (delayMs * sr / 1000).toInt()
                if (metronome && slot % snapshot.meter.slotsPerBeat == 0) {
                    val click = if (slot % snapshot.meter.slotsPerBar == 0) clickAccent else clickBeat
                    audio.playSamplesAt(click, 1f, baseFrames)
                }
                for (inst in snapshot.instruments) {
                    if (!isAudible(inst)) continue
                    val v = snapshot.voiceAt(inst, slot) ?: continue
                    val buf = buffer(inst, v)
                    val peak = peakOffsetFrames(inst, v, buf)
                    val advance = if (peak > sr / 50) minOf(peak, baseFrames) else 0
                    // Accented hits play ~1.4× louder (mixer clamps overall).
                    val gain = volumeOf(inst) * (if (snapshot.isAccented(inst, slot)) 1.4f else 1f)
                    audio.playSamplesAt(buf, gain, baseFrames - advance)
                }
            }
            var nextOnsetNanos = System.nanoTime()
            var first = true
            while (isPlaying) {
                val snapshot = pattern        // re-read each pass so meter edits take effect
                for (slot in 0 until snapshot.slots) {
                    if (!isPlaying) break
                    currentSlot = slot
                    if (first) { scheduleSlot(snapshot, slot, 0); first = false }
                    val slotMs = PercussionTiming.swungSlotMs(slot, bpm, swing, snapshot.meter)
                    nextOnsetNanos += slotMs * 1_000_000
                    val nextSlot = (slot + 1) % snapshot.slots
                    val nextSnapshot = if (nextSlot == 0) pattern else snapshot
                    val delayMs = ((nextOnsetNanos - System.nanoTime()) / 1_000_000).coerceAtLeast(0)
                    if (nextSlot < nextSnapshot.slots) scheduleSlot(nextSnapshot, nextSlot, delayMs)
                    delay(delayMs)
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

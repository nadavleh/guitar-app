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
import app.guitar.theory.PercussionBuiltins
import app.guitar.theory.PercussionPattern
import app.guitar.theory.PercussionTiming
import app.guitar.theory.PercussionVoices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    // Default-load the "batida do cavaco 1" groove (surdo + tamborim + bongo) so the
    // machine opens with a musical starting point on the default kit. The user can
    // Clear all or Load another beat from there.
    var pattern by mutableStateOf(PercussionBuiltins.BATIDA_CAVACO_1)
        private set

    // Undo stack of prior patterns (Ctrl-Z / Undo button). Every pattern edit pushes
    // the previous pattern here via [commit]; [undo] pops it back.
    private val undoStack = ArrayDeque<PercussionPattern>()
    var canUndo by mutableStateOf(false)
        private set

    /** Apply a pattern edit, recording the previous pattern for undo. */
    private fun commit(next: PercussionPattern) {
        if (next == pattern) return
        undoStack.addLast(pattern)
        while (undoStack.size > 50) undoStack.removeFirst()
        pattern = next
        canUndo = true
    }

    /** Undo the last pattern edit. */
    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        pattern = prev
        canUndo = undoStack.isNotEmpty()
    }

    /** Reorder the kit: move the track at [from] to index [to]. */
    fun reorderInstrument(from: Int, to: Int) { commit(pattern.movedInstrument(from, to)) }
    var bpm by mutableStateOf(70)
    /** Brazilian 16th-note swing, 0..100 % (0 = straight). */
    var swing by mutableStateOf(0)
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
            bpm = (60_000.0 / avg).toInt().coerceIn(10, 300)
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

    /** Mixer volumes, 0f..1f, keyed by "<instId>" (global instrument level) or
     *  "<instId>:<voiceIndex>" (a single voice). Applied as a gain at mix time so
     *  the cached one-shot buffers are never mutated. Effective gain of a hit is
     *  global × per-voice. Loaded from and written through to [repo] so the mix
     *  survives closing the app; unset keys fall back to [defaultVoiceVolume]. */
    var volumes by mutableStateOf<Map<String, Float>>(emptyMap())
        private set

    init {
        // Load the persisted mix once at startup (state is app-scoped, built once).
        scope.launch { runCatching { volumes = repo.drumVolumes.first() } }
    }

    private fun voiceKey(inst: PercussionInstrument, voiceIndex: Int) = "${inst.id}:$voiceIndex"

    /** Global level of an instrument (default full). */
    fun volumeOf(inst: PercussionInstrument): Float =
        volumes[inst.id] ?: if (inst.id == "agogo") 0.1f else 1f   // agogô defaults quiet (user: 10%)

    /** Level of one voice (default full, or 50% for the soft tamborim voices). */
    fun voiceVolumeOf(inst: PercussionInstrument, voiceIndex: Int): Float =
        volumes[voiceKey(inst, voiceIndex)] ?: defaultVoiceVolume(inst.id, voiceIndex)

    /** Combined gain a hit of [voiceIndex] actually plays at: global × per-voice. */
    fun effectiveGain(inst: PercussionInstrument, voiceIndex: Int): Float =
        volumeOf(inst) * voiceVolumeOf(inst, voiceIndex)

    fun setVolume(inst: PercussionInstrument, value: Float) {
        val v = value.coerceIn(0f, 1f)
        volumes = volumes + (inst.id to v)
        scope.launch { repo.setDrumVolume(inst.id, v) }
    }

    fun setVoiceVolume(inst: PercussionInstrument, voiceIndex: Int, value: Float) {
        val v = value.coerceIn(0f, 1f)
        val key = voiceKey(inst, voiceIndex)
        volumes = volumes + (key to v)
        scope.launch { repo.setDrumVolume(key, v) }
    }

    companion object {
        /** Voices that should start quieter than full. The tamborim "muted clack"
         *  (voice 1) and "tap" (voice 2) are much softer than its open clack, so
         *  they default to 50% until the user dials them in. */
        private val SOFT_VOICE_DEFAULTS = mapOf(
            "tamborim:1" to 0.5f,
            "tamborim:2" to 0.5f,
        )

        fun defaultVoiceVolume(instId: String, voiceIndex: Int): Float =
            SOFT_VOICE_DEFAULTS["$instId:$voiceIndex"] ?: 1f
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
        commit(pattern.cycled(instrument, slot))
        val v = pattern.voiceAt(instrument, slot)
        if (v != null && !isPlaying) audio.playSamples(buffer(instrument, v), effectiveGain(instrument, v))
    }

    /** Audition a single voice (used by the row-label tap). */
    fun preview(instrument: PercussionInstrument, voiceIndex: Int) {
        audio.playSamples(buffer(instrument, voiceIndex), effectiveGain(instrument, voiceIndex))
    }

    /** Toggle the accent on a non-silent cell (Accent tool). */
    fun toggleAccent(instrument: PercussionInstrument, slot: Int) {
        commit(pattern.accentToggled(instrument, slot))
    }

    /** Clear a single cell (long-press) without cycling through the voices. */
    fun clearCell(instrument: PercussionInstrument, slot: Int) {
        commit(pattern.withCell(instrument, slot, null))
    }

    fun clearRow(instrument: PercussionInstrument) {
        commit(pattern.clearedRow(instrument))
    }

    fun clearAll() {
        commit(PercussionPattern.empty(pattern.instruments, pattern.meter))
    }

    // ---- Kit: add / remove instruments ----

    /** Catalog instruments not yet in the kit, in catalog order (for the picker). */
    fun instrumentsToAdd(): List<PercussionInstrument> =
        PercussionCatalog.ALL.filter { !pattern.hasInstrument(it) }

    /** Add [inst] to the kit (silent row) and audition its first voice. */
    fun addInstrument(inst: PercussionInstrument) {
        commit(pattern.addInstrument(inst))
        if (!isPlaying) audio.playSamples(buffer(inst, 0), effectiveGain(inst, 0))
    }

    /** Remove [inst] from the kit, also clearing its mute/solo state. */
    fun removeInstrument(inst: PercussionInstrument) {
        commit(pattern.removeInstrument(inst))
        muted = muted - inst
        soloed = soloed - inst
    }

    // ---- Meter (bars / time signature / division) ----

    val meter get() = pattern.meter

    /** Re-fit the current pattern onto [newMeter] (cells preserved by slot index). */
    fun setMeter(newMeter: PercussionMeter) {
        commit(pattern.withMeter(newMeter))
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
        commit(pattern.translated(n))
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
        commit(p)
    }

    fun deleteSaved(name: String) {
        scope.launch { repo.deleteDrumPattern(name) }
    }

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
                for (inst in snapshot.instruments) {
                    if (!isAudible(inst)) continue
                    val v = snapshot.voiceAt(inst, slot) ?: continue
                    val buf = buffer(inst, v)
                    val peak = peakOffsetFrames(inst, v, buf)
                    val advance = if (peak > sr / 50) minOf(peak, baseFrames) else 0
                    // Accented hits play ~1.4× louder (mixer clamps overall).
                    val gain = effectiveGain(inst, v) * (if (snapshot.isAccented(inst, slot)) 1.4f else 1f)
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

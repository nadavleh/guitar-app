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
import app.guitar.theory.SavedBeat
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
    // Default-load the "Batida do Cavaco 1" groove (surdo + tamborim + bongo) so the
    // machine opens with a musical starting point on the default kit. The user can
    // Clear all or Load another beat from there.
    var pattern by mutableStateOf(PercussionBuiltins.BATIDA_CAVACO_1)
        private set

    /** Optional one-shot "opening" (entrada) played once before the loop starts. */
    var opening by mutableStateOf<PercussionPattern?>(null)
        private set

    /** Which pattern the grid is editing: the loop (false) or the opening (true). */
    var editingOpening by mutableStateOf(false)
        private set

    /** True while the scheduler is sounding the opening pass (drives the playhead). */
    var playingOpening by mutableStateOf(false)
        private set

    /** The pattern the grid is currently editing (loop or opening). */
    val editPattern: PercussionPattern
        get() = if (editingOpening) opening ?: pattern else pattern

    /** Create an empty opening (same kit + meter as the loop) and start editing it. */
    fun addOpening() {
        if (opening == null) {
            opening = PercussionPattern.empty(pattern.instruments, pattern.meter)
        }
        editingOpening = true
    }

    /** Create an opening pre-filled with a preset track (e.g. an entrada chunk). */
    fun addOpeningFromPreset(p: PercussionBuiltins.PresetTrack) {
        pushUndo()
        opening = PercussionPattern.empty(emptyList(), pattern.meter)
            .withPresetTrack(p.instrument, p.template)
        editingOpening = false
    }

    /** Delete the opening and return to editing the loop. */
    fun removeOpening() {
        if (opening == null) return
        pushUndo()
        opening = null
        editingOpening = false
    }

    /** Switch which section (loop or opening) edits target. Both grids are always
     *  visible; interacting with a section's rows calls this first, so header
     *  tools (meter, palette, add) follow the section you touched last. Clears
     *  the track selection on a switch so the brush can't leak across sections. */
    fun editOpening(on: Boolean) {
        if (on && opening == null) { addOpening(); return }
        val target = on && opening != null
        if (editingOpening == target) return
        editingOpening = target
        selectedTrackId = null
        brush = Brush.Cycle
    }

    /** Name of the most recently loaded/saved beat (for the header caption); null = unnamed. */
    var loadedName by mutableStateOf<String?>("Batida do Cavaco 1")
        private set

    /** Free-text notes attached to the current beat (saved + exported with it). */
    var beatNotes by mutableStateOf("")

    /** Overlay a wood-click metronome on the loop (higher click on each bar's "1"). */
    var metronomeOn by mutableStateOf(false)
        private set
    fun toggleMetronome() { metronomeOn = !metronomeOn }

    // ---- track selection + voice brush (the bottom palette) ----

    /** Id of the selected track (tap its name), or null. Selecting shows the voice
     *  palette; cells of the selected track follow [brush] instead of cycling. */
    var selectedTrackId by mutableStateOf<String?>(null)
        private set

    /** What a tap paints on the selected track (mirrors chorect-web's brush). */
    sealed interface Brush {
        /** Classic behavior: tap steps through the voices. The default. */
        object Cycle : Brush
        /** Tap clears the cell. */
        object Erase : Brush
        /** Tap places this voice; tapping a same-voice cell clears it (quick-clear). */
        data class Voice(val index: Int) : Brush
    }

    var brush by mutableStateOf<Brush>(Brush.Cycle)
        private set

    /** Toggle track selection; a fresh selection resets the brush to Cycle. */
    fun selectTrack(id: String) {
        if (selectedTrackId == id) selectedTrackId = null
        else { selectedTrackId = id; brush = Brush.Cycle }
    }

    // Named changeBrush (not setBrush) to avoid clashing with the generated `brush` setter.
    fun changeBrush(b: Brush) { brush = b }

    /** Apply the current brush to a cell of the selected track. */
    fun applyBrush(instrument: PercussionInstrument, slot: Int) {
        when (val b = brush) {
            is Brush.Cycle -> toggleSlot(instrument, slot)
            is Brush.Erase -> clearCell(instrument, slot)
            is Brush.Voice -> {
                if (editPattern.voiceAt(instrument, slot) == b.index) { clearCell(instrument, slot); return }
                commit(editPattern.withCell(instrument, slot, b.index))
                if (!isPlaying) audio.playSamples(buffer(instrument, b.index), effectiveGain(instrument, b.index))
            }
        }
    }
    private val mClick: FloatArray by lazy { synthClick(2000.0, 45) }
    private val mAccent: FloatArray by lazy { synthClick(2800.0, 45) }
    private fun synthClick(freqHz: Double, ms: Int, sr: Int = 44100): FloatArray {
        val n = sr * ms / 1000
        val buf = FloatArray(n)
        val w = 2.0 * Math.PI * freqHz / sr
        for (i in 0 until n) buf[i] = (Math.sin(w * i) * Math.exp(-6.0 * i / n) * 0.7).toFloat()
        return buf
    }

    // Undo stack (Ctrl-Z / Undo button). Every edit pushes a snapshot of BOTH
    // patterns via [commit], so undo restores loop + opening together.
    private val undoStack = ArrayDeque<Pair<PercussionPattern, PercussionPattern?>>()
    var canUndo by mutableStateOf(false)
        private set

    private fun pushUndo() {
        undoStack.addLast(pattern to opening)
        while (undoStack.size > 50) undoStack.removeFirst()
        canUndo = true
    }

    /** Apply an edit to whichever pattern the grid is editing (loop or opening),
     *  recording the previous state for undo. */
    private fun commit(next: PercussionPattern) {
        if (next == editPattern) return
        pushUndo()
        if (editingOpening && opening != null) opening = next else pattern = next
        loadedName = null   // an edit means it's no longer the named beat (load/save re-sets)
    }

    /** Undo the last edit (restores both loop and opening). */
    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        pattern = prev.first
        opening = prev.second
        if (opening == null) editingOpening = false
        canUndo = undoStack.isNotEmpty()
    }

    /** Reorder the kit: move the track at [from] to index [to]. */
    fun reorderInstrument(from: Int, to: Int) { commit(editPattern.movedInstrument(from, to)) }
    var bpm by mutableStateOf(80)
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

    /** Global level of an instrument (default full; clones default like their base). */
    fun volumeOf(inst: PercussionInstrument): Float =
        volumes[inst.id] ?: if (PercussionCatalog.baseId(inst.id) == "agogo") 0.1f else 1f   // agogô defaults quiet (user: 10%)

    /** Level of one voice (default full, or 50% for the soft tamborim voices). */
    fun voiceVolumeOf(inst: PercussionInstrument, voiceIndex: Int): Float =
        volumes[voiceKey(inst, voiceIndex)] ?: defaultVoiceVolume(PercussionCatalog.baseId(inst.id), voiceIndex)

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
        commit(editPattern.cycled(instrument, slot))
        val v = editPattern.voiceAt(instrument, slot)
        if (v != null && !isPlaying) audio.playSamples(buffer(instrument, v), effectiveGain(instrument, v))
    }

    /** Audition a single voice (used by the row-label tap). */
    fun preview(instrument: PercussionInstrument, voiceIndex: Int) {
        audio.playSamples(buffer(instrument, voiceIndex), effectiveGain(instrument, voiceIndex))
    }

    /** Toggle the accent on a non-silent cell (Accent tool). */
    fun toggleAccent(instrument: PercussionInstrument, slot: Int) {
        commit(editPattern.accentToggled(instrument, slot))
    }

    /** Cycle a hit's per-slot volume 100 → 75 → 50 → 25 % (Dyn tool). */
    fun dynCycle(instrument: PercussionInstrument, slot: Int) {
        commit(editPattern.dynCycled(instrument, slot))
    }

    /** Clear a single cell (long-press) without cycling through the voices. */
    fun clearCell(instrument: PercussionInstrument, slot: Int) {
        commit(editPattern.withCell(instrument, slot, null))
    }

    fun clearRow(instrument: PercussionInstrument) {
        commit(editPattern.clearedRow(instrument))
    }

    fun clearAll() {
        commit(PercussionPattern.empty(editPattern.instruments, editPattern.meter))
    }

    // ---- Kit: add / remove instruments ----

    /** Catalog instruments not yet in the kit, in catalog order (for the picker). */
    fun instrumentsToAdd(): List<PercussionInstrument> =
        PercussionCatalog.ALL.filter { !editPattern.hasInstrument(it) }

    /** Add [inst] to the kit (silent row) and audition its first voice. */
    fun addInstrument(inst: PercussionInstrument) {
        commit(editPattern.addInstrument(inst))
        if (!isPlaying) audio.playSamples(buffer(inst, 0), effectiveGain(inst, 0))
    }

    /** Duplicate [inst]'s track — same sound + a copy of its row, no re-picking the
     *  instrument or re-painting (the new track is a clone, e.g. "Surdo 2"). */
    fun duplicateTrack(inst: PercussionInstrument) {
        commit(editPattern.duplicatedTrack(inst))
    }

    /** One-press preset track (marcação surdo / teleco-teco tamborim): adds the
     *  instrument (cloned if present) with its row pre-filled, and auditions it. */
    fun addPresetTrack(p: PercussionBuiltins.PresetTrack) {
        commit(editPattern.withPresetTrack(p.instrument, p.template))
        if (!isPlaying) audio.playSamples(buffer(p.instrument, 0), effectiveGain(p.instrument, 0))
    }

    /** Remove [inst] from the kit, also clearing its mute/solo/selection state. */
    fun removeInstrument(inst: PercussionInstrument) {
        commit(editPattern.removeInstrument(inst))
        muted = muted - inst
        soloed = soloed - inst
        if (selectedTrackId == inst.id) { selectedTrackId = null; brush = Brush.Cycle }
    }

    // ---- Meter (bars / time signature / division) ----

    /** Meter of the pattern the grid is editing (opening can differ from the loop). */
    val meter get() = editPattern.meter

    /** Re-fit the edited pattern onto [newMeter] (cells preserved by slot index). */
    fun setMeter(newMeter: PercussionMeter) {
        commit(editPattern.withMeter(newMeter))
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

    /** Translate (rotate) the edited pattern by [n] slots with wrap-around. */
    fun translate(n: Int) {
        commit(editPattern.translated(n))
    }

    // ---- Save / load user beats ----

    /** User-saved beats (loop + optional opening), by name (observe in the UI). */
    val savedPatterns get() = repo.drumPatterns

    /** Save the current beat (loop + opening + notes) under [name]. */
    fun saveCurrent(name: String) {
        val snapshot = SavedBeat(pattern, opening, beatNotes)
        scope.launch { repo.saveDrumPattern(name, snapshot) }
        loadedName = name
    }

    /** Replace the editable beat with a saved/loaded one (loop + optional opening
     *  + notes), optionally naming it (caption) and setting its tempo / swing.
     *  Editing returns to the loop grid. */
    fun loadPattern(
        p: PercussionPattern, name: String? = null,
        bpm: Int? = null, swing: Int? = null,
        opening: PercussionPattern? = null,
        notes: String = "",
    ) {
        pushUndo()
        pattern = p
        this.opening = opening
        editingOpening = false
        loadedName = name
        beatNotes = notes
        if (bpm != null) this.bpm = bpm.coerceIn(10, 300)
        if (swing != null) this.swing = swing.coerceIn(0, 100)
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
                // Metronome click track: one click per beat, higher click on each bar's "1".
                if (metronomeOn) {
                    val m = snapshot.meter
                    if (slot % m.slotsPerBeat == 0) {
                        val barDown = slot % m.slotsPerBar == 0
                        audio.playSamplesAt(if (barDown) mAccent else mClick, if (barDown) 0.9f else 0.6f, baseFrames)
                    }
                }
                for (inst in snapshot.instruments) {
                    if (!isAudible(inst)) continue
                    val v = snapshot.voiceAt(inst, slot) ?: continue
                    val buf = buffer(inst, v)
                    val peak = peakOffsetFrames(inst, v, buf)
                    val advance = if (peak > sr / 50) minOf(peak, baseFrames) else 0
                    // Accented hits play ~1.4× louder (mixer clamps overall); per-slot
                    // dynamics scale the hit down (100/75/50/25 %).
                    val gain = effectiveGain(inst, v) *
                        (if (snapshot.isAccented(inst, slot)) 1.4f else 1f) *
                        app.guitar.theory.PERCUSSION_DYN_FACTORS[snapshot.dynLevelAt(inst, slot)]
                    audio.playSamplesAt(buf, gain, baseFrames - advance)
                }
            }
            var nextOnsetNanos = System.nanoTime()
            var first = true

            // ---- Opening: one pass of the (non-empty) opening pattern, then the loop.
            val op = opening
            if (op != null && !op.isEmpty()) {
                playingOpening = true
                for (slot in 0 until op.slots) {
                    if (!isPlaying) break
                    currentSlot = slot
                    if (first) { scheduleSlot(op, slot, 0); first = false }
                    val slotMs = PercussionTiming.swungSlotMs(slot, bpm, swing, op.meter)
                    nextOnsetNanos += slotMs * 1_000_000
                    val delayMs = ((nextOnsetNanos - System.nanoTime()) / 1_000_000).coerceAtLeast(0)
                    // Next up: the opening's next slot, or the loop's downbeat when it ends.
                    if (slot + 1 < op.slots) scheduleSlot(op, slot + 1, delayMs)
                    else scheduleSlot(pattern, 0, delayMs)
                    delay(delayMs)
                }
                playingOpening = false
            }

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
        playingOpening = false
        audio.stop()
    }

    fun release() {
        stop()
    }
}

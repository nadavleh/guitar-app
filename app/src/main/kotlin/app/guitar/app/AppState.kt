package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.theory.CagedShape
import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordShape
import app.guitar.theory.ChordShapeGenerator
import app.guitar.theory.FretPosition
import app.guitar.theory.Fretboard
import app.guitar.theory.Midi
import app.guitar.theory.Note
import app.guitar.theory.Tuning
import app.guitar.theory.Tunings
import app.guitar.theory.VoicingStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MIDI_MIN = 28   // E1
const val MIDI_MAX = 84   // C6
const val DISPLAY_FRETS = 14

enum class Highlight { Chord, Scale, None }
enum class LabelMode { Notes, Intervals, Empty }

/** What the fretboard is currently lighting up. */
enum class DisplayMode { None, Chord, Scale, Pick }

/** Which bottom sheet (or full-screen route) is currently open (null = none).
 *  Loop and Tuner are full-screen; the others are bottom sheets. */
enum class Sheet { Chord, Scale, Pick, Options, Loop, Tuner }

/** All-notes vs single-position view, for chord & scale display. */
enum class ChordScaleView { AllNotes, Positions }

@Stable
class AppState(
    private val repo: TuningRepository,
    private val scope: CoroutineScope,
    private val audio: AudioEngine,
) {
    var tuningName by mutableStateOf("Standard")
    var liveTuning by mutableStateOf<Tuning>(Tunings.standard)
    var isEditedTuning by mutableStateOf(false)

    var chordInput by mutableStateOf("Cmaj7")
    var chordFretRange by mutableStateOf(0..DISPLAY_FRETS)
    var selectedShapeIndex by mutableStateOf<Int?>(null)

    var scaleRoot by mutableStateOf("A")
    var scaleType by mutableStateOf("minor pentatonic")
    var scaleFretRange by mutableStateOf(0..DISPLAY_FRETS)

    var highlight by mutableStateOf(Highlight.Chord)
    var labelMode by mutableStateOf(LabelMode.Notes)
    var selectedPosition by mutableStateOf<FretPosition?>(null)
    var leftHanded by mutableStateOf(false)

    // v1 GUI state
    var displayMode by mutableStateOf(DisplayMode.Chord)
    var currentSheet by mutableStateOf<Sheet?>(null)
    /** The last sheet the user explicitly opened. Used by the bottom drag-up affordance
     *  so the user can re-open the same sheet without going through the menu again. */
    var lastSheet by mutableStateOf<Sheet?>(null)
    var chordView by mutableStateOf(ChordScaleView.AllNotes)
    var scaleView by mutableStateOf(ChordScaleView.AllNotes)
    var chordPositionIndex by mutableStateOf(0)
    var scalePositionIndex by mutableStateOf(0)
    var pickedPositions by mutableStateOf<Set<FretPosition>>(emptySet())

    // Jazz/shell voicing toggle — affects ChordShapeGenerator calls everywhere
    var voicingStyle by mutableStateOf(VoicingStyle.Standard)

    // Tuner / audio configuration
    var a4Hz by mutableStateOf(440f)
    var ringSustainMs by mutableStateOf(1500)

    @JvmName("applyA4Hz")
    fun setA4Hz(value: Float) {
        val clamped = value.coerceIn(435f, 445f)
        a4Hz = clamped
        scope.launch { repo.setA4Hz(clamped) }
    }

    @JvmName("applyRingSustainMs")
    fun setRingSustainMs(value: Int) {
        val clamped = value.coerceIn(200, 5000)
        ringSustainMs = clamped
        scope.launch { repo.setRingSustainMs(clamped) }
    }

    fun toggleVoicingStyle(shell: Boolean) {
        voicingStyle = if (shell) VoicingStyle.Shell else VoicingStyle.Standard
        scope.launch { repo.setVoicingShell(shell) }
    }

    /** JVM-name avoids clash with `var labelMode`'s synthetic setter. */
    @JvmName("applyLabelMode")
    fun setLabelMode(mode: LabelMode) {
        labelMode = mode
        scope.launch { repo.setLabelMode(mode.name) }
    }

    // Loop builder state.
    // The progression is a List<List<LoopSlot>>: each inner list is one bar; each slot is one beat.
    // slotsPerBar lets the user split a bar into 1 (whole-note), 2 (half-note), or 4 (quarter-note).
    var bpm by mutableStateOf(80)
    var slotsPerBar by mutableStateOf(1)   // start with one chord per bar (whole-bar slots)
    var loopProgression by mutableStateOf(DEFAULT_PROGRESSION)
    var isLooping by mutableStateOf(false)
    var loopCurrentBar by mutableStateOf(0)
    var loopCurrentSlot by mutableStateOf(0)
    /** Currently-edited (barIdx, slotIdx) for the slot-edit panel, or null. */
    var loopEditingSlot by mutableStateOf<Pair<Int, Int>?>(null)
    private var loopJob: Job? = null
    private val loopShapeGen get() = ChordShapeGenerator(style = voicingStyle)

    val customTunings get() = repo.customTunings
    val savedSelectedName get() = repo.selectedTuningName

    fun selectTuning(name: String, tuning: Tuning) {
        tuningName = name
        liveTuning = tuning
        isEditedTuning = false
        selectedShapeIndex = null
        scope.launch { repo.setSelected(name) }
    }

    fun adjustString(stringIdx: Int, delta: Int) {
        val current = liveTuning.openStrings[stringIdx]
        val newMidi = (current.midi.value + delta).coerceIn(MIDI_MIN, MIDI_MAX)
        if (newMidi == current.midi.value) return
        val newOpenStrings = liveTuning.openStrings.toMutableList().apply {
            this[stringIdx] = Note(Midi(newMidi))
        }
        liveTuning = Tuning(newOpenStrings)
        isEditedTuning = true
    }

    fun saveCustomTuning(name: String) {
        val clean = name.trim()
        if (clean.isBlank() || '|' in clean || ';' in clean) return
        scope.launch {
            repo.saveTuning(clean, liveTuning)
            repo.setSelected(clean)
        }
        tuningName = clean
        isEditedTuning = false
    }

    fun deleteCustomTuning(name: String) {
        scope.launch {
            repo.deleteTuning(name)
            if (tuningName == name) repo.setSelected("Standard")
        }
        if (tuningName == name) {
            tuningName = "Standard"
            liveTuning = Tunings.standard
            isEditedTuning = false
        }
    }

    fun resetTuningToSaved(customTunings: Map<String, Tuning>) {
        liveTuning = Tunings.all[tuningName]
            ?: customTunings[tuningName]
            ?: Tunings.standard
        isEditedTuning = false
    }

    /**
     * Select a position on the fretboard AND play the corresponding note.
     * No-op if [pos] is out of range for the current tuning.
     */
    fun tapPosition(pos: FretPosition) {
        if (pos.stringIndex < 0 || pos.stringIndex >= liveTuning.stringCount) return
        selectedPosition = pos
        val note = Fretboard.noteAt(liveTuning, pos)
        audio.playNote(note.midi.value, durationMillis = ringSustainMs)
    }

    /** Play all (non-muted) notes of a chord shape as a strummed arpeggio low → high. */
    fun playShape(shape: ChordShape) {
        val midis = shape.notes.mapNotNull { it?.midi?.value }
        if (midis.isNotEmpty()) {
            audio.playChord(midis, strumDelayMillis = 40, sustainMillis = ringSustainMs)
        }
    }

    /** Tuner-specific: play a guitar pluck at a specific MIDI note (under the user's A4 ref). */
    fun playReferencePitch(midi: Int) {
        val freq = (a4Hz.toDouble() * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
        audio.playFrequency(freq, durationMillis = ringSustainMs)
    }


    // ---------- Pick mode ----------

    fun togglePick(pos: FretPosition) {
        if (pos.stringIndex < 0 || pos.stringIndex >= liveTuning.stringCount) return
        pickedPositions = if (pos in pickedPositions) pickedPositions - pos else pickedPositions + pos
    }

    fun clearPicked() {
        pickedPositions = emptySet()
    }

    fun strumPicked(arpeggio: Boolean = false) {
        if (pickedPositions.isEmpty()) return
        val midis = pickedPositions
            .filter { it.stringIndex < liveTuning.stringCount }
            .sortedWith(compareBy({ it.stringIndex }, { it.fret }))
            .map { Fretboard.noteAt(liveTuning, it).midi.value }
        if (midis.isNotEmpty()) {
            audio.playChord(
                midis,
                strumDelayMillis = if (arpeggio) 120 else 35,
                sustainMillis = ringSustainMs,
            )
        }
    }

    fun toggleLeftHanded(value: Boolean) {
        leftHanded = value
        scope.launch { repo.setLeftHanded(value) }
    }

    // ---------- Sheet / display-mode interactions ----------

    fun openSheet(sheet: Sheet) {
        currentSheet = sheet
        lastSheet = sheet
        when (sheet) {
            Sheet.Chord -> displayMode = DisplayMode.Chord
            Sheet.Scale -> displayMode = DisplayMode.Scale
            Sheet.Pick -> displayMode = DisplayMode.Pick
            Sheet.Options -> {} // tunings/options doesn't change what's lit
            Sheet.Loop -> {}    // loop sheet plays its own audio; fretboard view unchanged
            Sheet.Tuner -> {}   // tuner reads the mic; fretboard view unchanged
        }
    }

    fun reopenLastSheet() {
        lastSheet?.let { openSheet(it) }
    }

    fun closeSheet() { currentSheet = null }

    fun resetChordPosition() { chordPositionIndex = 0 }
    fun resetScalePosition() { scalePositionIndex = 0 }

    fun stepChordPosition(delta: Int, count: Int) {
        if (count <= 0) return
        chordPositionIndex = ((chordPositionIndex + delta) % count + count) % count
    }
    fun stepScalePosition(delta: Int, count: Int) {
        if (count <= 0) return
        scalePositionIndex = ((scalePositionIndex + delta) % count + count) % count
    }

    // ---------- Loop transport ----------

    /** Replace one slot in the progression. */
    fun setLoopSlot(barIdx: Int, slotIdx: Int, slot: LoopSlot) {
        if (barIdx !in loopProgression.indices) return
        val bar = loopProgression[barIdx]
        if (slotIdx !in bar.indices) return
        loopProgression = loopProgression.toMutableList().also { bars ->
            bars[barIdx] = bar.toMutableList().also { it[slotIdx] = slot }
        }
    }

    /** Update only the chord-symbol field of a slot (keep voicing/strum, default to fresh slot if empty). */
    fun setLoopSlotChord(barIdx: Int, slotIdx: Int, chordSymbol: String?) {
        val current = loopProgression.getOrNull(barIdx)?.getOrNull(slotIdx) ?: return
        val cleaned = chordSymbol?.ifBlank { null }
        val newSlot =
            if (cleaned != current.chordSymbol)
                current.copy(chordSymbol = cleaned, voicingIndex = defaultVoicingIndexFor(cleaned))
            else current.copy(chordSymbol = cleaned)
        setLoopSlot(barIdx, slotIdx, newSlot)
    }

    /** Pick the index of the E-shape voicing for [chordSymbol], or 0 if none exists.
     *  E-shape is the canonical movable barre — typically the easiest "default" voicing. */
    fun defaultVoicingIndexFor(chordSymbol: String?): Int {
        val parsed = chordSymbol?.let { ChordLibrary.parse(it) } ?: return 0
        val (r, q) = parsed
        val shapes = loopShapeGen.shapesFor(r, q, liveTuning, frets = DISPLAY_FRETS)
        val idx = shapes.indexOfFirst { it.cagedShape == CagedShape.E }
        return if (idx >= 0) idx else 0
    }

    /** Tracks whether we've already normalized the initial progression's voicings to the
     *  E-shape default for the active tuning/voicing style. */
    var loopNormalized by mutableStateOf(false)

    /** Update every slot's voicingIndex to the E-shape default. Idempotent — safe to call repeatedly. */
    fun normalizeLoopVoicings() {
        loopProgression = loopProgression.map { bar ->
            bar.map { slot ->
                if (slot.chordSymbol != null)
                    slot.copy(voicingIndex = defaultVoicingIndexFor(slot.chordSymbol))
                else slot
            }
        }
        loopNormalized = true
    }

    /** Change how many slots make up a bar (1 = whole, 2 = half, 4 = quarter). Preserves existing chords. */
    @JvmName("applySlotsPerBar")
    fun setSlotsPerBar(n: Int) {
        val clamped = n.coerceIn(1, 4)
        if (clamped == slotsPerBar) return
        loopProgression = loopProgression.map { bar ->
            // Stretch or compress: keep slot 0; fill the rest with sustain (chord=null) so we don't
            // accidentally duplicate the same chord on every beat.
            val first = bar.firstOrNull() ?: LoopSlot()
            buildList {
                add(first)
                repeat(clamped - 1) { add(LoopSlot()) }
            }
        }
        slotsPerBar = clamped
    }

    fun setBarCount(count: Int) {
        val clamped = count.coerceIn(1, 16)
        val empty = List(slotsPerBar) { LoopSlot() }
        loopProgression = (0 until clamped).map { loopProgression.getOrNull(it) ?: empty }
    }

    fun startLoop() {
        if (isLooping) return
        isLooping = true
        loopJob = scope.launch {
            while (isLooping) {
                for (barIdx in loopProgression.indices) {
                    if (!isLooping) break
                    loopCurrentBar = barIdx
                    playBar(loopProgression[barIdx])
                }
            }
        }
    }

    fun stopLoop() {
        isLooping = false
        loopJob?.cancel()
        loopJob = null
        audio.stop()
    }

    private suspend fun playBar(bar: List<LoopSlot>) {
        val beatMs = (60_000L / bpm.coerceAtLeast(20))
        // Each slot is one (whole bar / slotsPerBar) division.
        val slotMs = beatMs * 4 / bar.size.coerceAtLeast(1)
        for (slotIdx in bar.indices) {
            if (!isLooping) return
            loopCurrentSlot = slotIdx
            val slot = bar[slotIdx]
            val parsed = slot.chordSymbol?.let { ChordLibrary.parse(it) }
            if (parsed != null && slot.strum != StrumPattern.Sustain) {
                val (root, q) = parsed
                val shapes = loopShapeGen.shapesFor(root, q, liveTuning, frets = DISPLAY_FRETS)
                val shape = shapes.getOrNull(slot.voicingIndex) ?: shapes.firstOrNull()
                if (shape != null) {
                    val midis = shape.notes.mapNotNull { it?.midi?.value }
                    val ordered = if (slot.strum == StrumPattern.Up) midis.asReversed() else midis
                    val strumDelay = when (slot.strum) {
                        StrumPattern.Down -> 25
                        StrumPattern.Up -> 25
                        StrumPattern.Arpeggio -> 100
                        StrumPattern.Sustain -> 0
                    }
                    audio.playChord(ordered, strumDelayMillis = strumDelay,
                        sustainMillis = (slotMs * 0.9).toInt().coerceAtLeast(150))
                }
            }
            delay(slotMs)
        }
    }
}

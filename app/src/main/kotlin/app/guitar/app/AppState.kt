package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
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

/** Which bottom sheet is currently open (null = none). */
enum class Sheet { Chord, Scale, Pick, Options, Loop }

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
    var chordView by mutableStateOf(ChordScaleView.AllNotes)
    var scaleView by mutableStateOf(ChordScaleView.AllNotes)
    var chordPositionIndex by mutableStateOf(0)
    var scalePositionIndex by mutableStateOf(0)
    var pickedPositions by mutableStateOf<Set<FretPosition>>(emptySet())

    // Jazz/shell voicing toggle — affects ChordShapeGenerator calls everywhere
    var voicingStyle by mutableStateOf(VoicingStyle.Standard)

    // Loop builder state
    var bpm by mutableStateOf(80)
    var loopBars by mutableStateOf(listOf<String?>("C", "Am", "F", "G"))
    var isLooping by mutableStateOf(false)
    var loopCurrentBar by mutableStateOf(0)
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
        audio.playNote(note.midi.value, durationMillis = 1500)
    }

    /** Play all (non-muted) notes of a chord shape as a strummed arpeggio low → high. */
    fun playShape(shape: ChordShape) {
        val midis = shape.notes.mapNotNull { it?.midi?.value }
        if (midis.isNotEmpty()) {
            audio.playChord(midis, strumDelayMillis = 40, sustainMillis = 2000)
        }
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
                sustainMillis = 2000
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
        when (sheet) {
            Sheet.Chord -> displayMode = DisplayMode.Chord
            Sheet.Scale -> displayMode = DisplayMode.Scale
            Sheet.Pick -> displayMode = DisplayMode.Pick
            Sheet.Options -> {} // tunings/options doesn't change what's lit
            Sheet.Loop -> {}    // loop sheet plays its own audio; fretboard view unchanged
        }
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

    fun setLoopBar(index: Int, chordSymbol: String?) {
        if (index !in loopBars.indices) return
        loopBars = loopBars.toMutableList().also { it[index] = chordSymbol?.ifBlank { null } }
    }

    fun setBarCount(count: Int) {
        val clamped = count.coerceIn(1, 16)
        loopBars = (0 until clamped).map { loopBars.getOrNull(it) }
    }

    fun startLoop() {
        if (isLooping) return
        isLooping = true
        loopJob = scope.launch {
            while (isLooping) {
                for (i in loopBars.indices) {
                    if (!isLooping) break
                    loopCurrentBar = i
                    playBar(loopBars[i])
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

    private suspend fun playBar(chordSymbol: String?) {
        val beatMs = (60_000L / bpm.coerceAtLeast(20))
        val parsed = chordSymbol?.let { ChordLibrary.parse(it) }
        if (parsed == null) { delay(beatMs * 4); return }
        val (root, q) = parsed
        val shapes = loopShapeGen.shapesFor(root, q, liveTuning, frets = DISPLAY_FRETS)
        val shape = shapes.firstOrNull()
        if (shape == null) { delay(beatMs * 4); return }
        val midis = shape.notes.mapNotNull { it?.midi?.value }
        if (midis.isEmpty()) { delay(beatMs * 4); return }
        repeat(4) {                              // 4 quarter-note strums per bar (v1: 4/4 only)
            if (!isLooping) return
            audio.playChord(midis, strumDelayMillis = 30, sustainMillis = (beatMs * 0.9).toInt().coerceAtLeast(150))
            delay(beatMs)
        }
    }
}

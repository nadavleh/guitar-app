package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.theory.ChordShape
import app.guitar.theory.FretPosition
import app.guitar.theory.Fretboard
import app.guitar.theory.Midi
import app.guitar.theory.Note
import app.guitar.theory.Tuning
import app.guitar.theory.Tunings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val MIDI_MIN = 28   // E1
const val MIDI_MAX = 84   // C6
const val DISPLAY_FRETS = 14

enum class Highlight { Chord, Scale, None }
enum class LabelMode { Notes, Intervals, Empty }
enum class Mode { Tuning, Chord, Scale, Pick }
enum class DisplayMode { AllNotes, Positions }

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
    var mode by mutableStateOf(Mode.Chord)
    var chordDisplay by mutableStateOf(DisplayMode.AllNotes)
    var scaleDisplay by mutableStateOf(DisplayMode.AllNotes)
    var pickedPositions by mutableStateOf<Set<FretPosition>>(emptySet())

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
}

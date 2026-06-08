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
import app.guitar.theory.ChordTypeLevel
import app.guitar.theory.EarTraining
import app.guitar.theory.FretPosition
import app.guitar.theory.Fretboard
import app.guitar.theory.Instrument
import app.guitar.theory.Midi
import app.guitar.theory.Note
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.TrainingMode
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
enum class Sheet { Chord, Scale, Pick, Options, Loop, Tuner, EarTraining }

/** All-notes vs single-position view, for chord & scale display. */
enum class ChordScaleView { AllNotes, Positions }

@Stable
class AppState(
    private val repo: TuningRepository,
    val scope: CoroutineScope,
    val audio: AudioEngine,
) {
    var instrument by mutableStateOf(Instrument.Guitar)
    var tuningName by mutableStateOf("Standard")
    var liveTuning by mutableStateOf<Tuning>(Tunings.standard)
    var isEditedTuning by mutableStateOf(false)

    /** Switch the active instrument. Resets the tuning to the new instrument's
     *  default preset (e.g. Guitar→Standard, Cavaquinho→DGBe) and persists. */
    @JvmName("applyInstrument")
    fun setInstrument(value: Instrument) {
        if (instrument == value) return
        instrument = value
        val defaultName = Tunings.defaultNameFor(value)
        val defaultTuning = Tunings.defaultFor(value)
        tuningName = defaultName
        liveTuning = defaultTuning
        isEditedTuning = false
        scope.launch {
            repo.setInstrument(value.name)
            repo.setSelected(defaultName)
        }
    }

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

    /** Strum/arpeggio spread in ms between consecutive chord notes. 0 = struck at once. */
    var strumMs by mutableStateOf(30)

    /**
     * #7: Ear-training state is owned by AppState (app-lifetime) rather than by the
     * EarTrainingScreen composable, so navigating away (e.g. to check yourself on the
     * fretboard) and back preserves the current progression, reveals, and counters
     * instead of regenerating a fresh one. Created lazily on first entry.
     */
    val earTraining: EarTrainingState by lazy {
        EarTrainingState(
            audio = audio,
            scope = scope,
            tuningProvider = { liveTuning },
            sustainProvider = { ringSustainMs },
            strumProvider = { strumMs },
        )
    }

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
    /** The chord shape currently being played by the looper. Observers (the main
     *  fretboard) can use this to display the sounding chord live. Null when the
     *  loop is stopped or the current slot is empty / sustaining. */
    var loopPlayingShape by mutableStateOf<app.guitar.theory.ChordShape?>(null)
        private set
    /** Currently-edited (barIdx, slotIdx) for the slot-edit panel, or null. */
    var loopEditingSlot by mutableStateOf<Pair<Int, Int>?>(null)

    // ---- "Build by degree" panel state ----
    var loopBuildExpanded by mutableStateOf(false)
    var loopBuildKey by mutableStateOf(PitchClass.C)
    var loopBuildMode by mutableStateOf(TrainingMode.Major)
    var loopBuildLevel by mutableStateOf(ChordTypeLevel.Sevenths)
    /** If non-null, this quality symbol overrides the diatonic quality for the
     *  next tapped degree. Lets the user say "add a 13 chord on the V" by
     *  selecting "13" then tapping V. Null = use the diatonic quality at the
     *  current level. */
    var loopBuildOverride by mutableStateOf<String?>(null)
    /** Cursor that advances every time the user taps a degree button without
     *  having an editing slot open. Wraps around the progression. */
    var loopBuildCursor by mutableStateOf(0)

    fun setLoopBuildKeyRandom() {
        loopBuildKey = PitchClass(kotlin.random.Random.nextInt(12))
    }

    /** Compute the diatonic chord symbol for a Roman degree under the panel's
     *  current key/mode/level, then write it into the editing slot (if any) or
     *  into the bar at [loopBuildCursor] (advancing the cursor with wrap). */
    fun applyLoopDegree(degree: Int) {
        require(degree in 1..7)
        val rootPc = EarTraining.degreeRoot(loopBuildKey, degree, loopBuildMode)
        val rootName = NoteSpeller.spell(rootPc)
        val quality: String = loopBuildOverride ?: run {
            val info = (if (loopBuildMode == TrainingMode.Major) EarTraining.MAJOR_DEGREES
                        else EarTraining.MINOR_DEGREES)[degree] ?: return
            when (loopBuildLevel) {
                ChordTypeLevel.Triads   -> info.triadQuality
                ChordTypeLevel.Sevenths -> info.seventhQuality
                ChordTypeLevel.Extended -> info.extendedQuality
            }
        }
        val symbol = "$rootName$quality"
        val target = loopEditingSlot
        if (target != null) {
            setLoopSlotChord(target.first, target.second, symbol)
        } else {
            val barIdx = loopBuildCursor.coerceIn(0, loopProgression.size - 1)
            setLoopSlotChord(barIdx, 0, symbol)
            loopBuildCursor = (barIdx + 1) % loopProgression.size.coerceAtLeast(1)
        }
    }

    fun resetLoopBuildCursor() { loopBuildCursor = 0 }
    private var loopJob: Job? = null
    private val loopShapeGen get() = ChordShapeGenerator(
        style = voicingStyle,
        maxFretSpan = instrument.maxFretSpan,
    )

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
    /** Per-instrument audio timbre. Cavaquinho = brighter, quicker decay. */
    private val timbre: app.guitar.audio.Timbre get() = when (instrument) {
        Instrument.Guitar     -> app.guitar.audio.Timbre.Guitar
        Instrument.Cavaquinho -> app.guitar.audio.Timbre.Cavaquinho
    }

    fun tapPosition(pos: FretPosition) {
        if (pos.stringIndex < 0 || pos.stringIndex >= liveTuning.stringCount) return
        selectedPosition = pos
        val note = Fretboard.noteAt(liveTuning, pos)
        audio.playNote(note.midi.value, durationMillis = ringSustainMs, timbre = timbre)
    }

    /** Play all (non-muted) notes of a chord shape as a strummed arpeggio low → high. */
    fun playShape(shape: ChordShape) {
        val midis = shape.notes.mapNotNull { it?.midi?.value }
        if (midis.isNotEmpty()) {
            audio.playChord(midis, strumDelayMillis = strumMs, sustainMillis = ringSustainMs, timbre = timbre)
        }
    }

    /** Tuner-specific: play a pluck at a specific MIDI note under the user's A4 ref,
     *  with the active instrument's timbre. */
    fun playReferencePitch(midi: Int) {
        val freq = (a4Hz.toDouble() * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
        audio.playFrequency(freq, durationMillis = ringSustainMs, timbre = timbre)
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
                timbre = timbre,
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
            Sheet.EarTraining -> {} // ear training plays its own audio
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

    /** #2: Replace the loop with the chords currently shown in the ear-training
     *  Progression trainer (one chord per bar), normalize voicings, and jump to the
     *  Looper so the user can keep working with them there. */
    fun loadProgressionIntoLoop(chordSymbols: List<String>) {
        val symbols = chordSymbols.filter { it.isNotBlank() }
        if (symbols.isEmpty()) return
        loopProgression = symbols.map { listOf(LoopSlot(it)) }
        loopCurrentBar = 0
        loopCurrentSlot = 0
        loopBuildCursor = 0
        normalizeLoopVoicings()
        openSheet(Sheet.Loop)
    }

    /** Update only the chord-symbol field of a slot, then re-normalize the whole
     *  progression so voice-leading flows naturally into and out of the change. */
    fun setLoopSlotChord(barIdx: Int, slotIdx: Int, chordSymbol: String?) {
        val current = loopProgression.getOrNull(barIdx)?.getOrNull(slotIdx) ?: return
        val cleaned = chordSymbol?.ifBlank { null }
        if (cleaned == current.chordSymbol) return
        setLoopSlot(barIdx, slotIdx, current.copy(chordSymbol = cleaned, voicingIndex = 0))
        // Re-normalize so the new chord and its neighbors get min-movement voicings.
        normalizeLoopVoicings()
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

    /**
     * Pick a voicing for every slot in the progression so the sequence flows
     * smoothly: first chord prefers the E-shape (most common movable barre);
     * every subsequent chord is picked to minimize finger movement from the
     * previously-chosen voicing (the way a human player chooses voicings).
     *
     * Idempotent: re-running is safe — the chosen voicings only depend on the
     * chord-symbol sequence, the current tuning, and the current voicing style.
     */
    fun normalizeLoopVoicings() {
        val newBars = ArrayList<List<LoopSlot>>(loopProgression.size)
        var prevShape: app.guitar.theory.ChordShape? = null
        for (bar in loopProgression) {
            val newBar = ArrayList<LoopSlot>(bar.size)
            for (slot in bar) {
                val sym = slot.chordSymbol
                if (sym == null) {
                    // Sustain / empty — prevShape carries through.
                    newBar.add(slot)
                    continue
                }
                val parsed = ChordLibrary.parse(sym)
                if (parsed == null) {
                    newBar.add(slot)
                    continue
                }
                val (root, q) = parsed
                val shapes = loopShapeGen.shapesFor(root, q, liveTuning, frets = DISPLAY_FRETS)
                if (shapes.isEmpty()) {
                    newBar.add(slot)
                    continue
                }
                val pickedIdx = if (prevShape == null) {
                    val eIdx = shapes.indexOfFirst { it.cagedShape == CagedShape.E }
                    if (eIdx >= 0) eIdx else 0
                } else {
                    app.guitar.theory.VoiceLeading.pickMinMovement(prevShape!!, shapes)
                }
                newBar.add(slot.copy(voicingIndex = pickedIdx))
                prevShape = shapes[pickedIdx]
            }
            newBars.add(newBar)
        }
        loopProgression = newBars
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
        loopPlayingShape = null
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
                    loopPlayingShape = shape
                    val midis = shape.notes.mapNotNull { it?.midi?.value }
                    val ordered = if (slot.strum == StrumPattern.Up) midis.asReversed() else midis
                    val strumDelay = when (slot.strum) {
                        StrumPattern.Down -> 25
                        StrumPattern.Up -> 25
                        StrumPattern.Arpeggio -> 100
                        StrumPattern.Sustain -> 0
                    }
                    audio.playChord(ordered, strumDelayMillis = strumDelay,
                        sustainMillis = (slotMs * 0.9).toInt().coerceAtLeast(150),
                        timbre = timbre)
                }
            }
            delay(slotMs)
        }
    }
}

package app.guitar.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.theory.CagedBox
import app.guitar.theory.CagedMode
import app.guitar.theory.CagedNote
import app.guitar.theory.CagedScales
import app.guitar.theory.CagedShapeTable
import app.guitar.theory.DrillStep
import app.guitar.theory.ExplorePosition
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.PitchClass
import app.guitar.theory.ScaleSubset
import app.guitar.theory.TriadShape
import app.guitar.theory.Tunings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * State + play loop for the guitar "Guitar practice" trainer (Android).
 * Mirror of chorect-web/src/app/cagedTrainerState.ts.
 *
 * Two sections, chosen from the dropdown at the top of the screen:
 *  - **Scales** — the CAGED boxes, with tabs Practice (the guided 34-step run),
 *    Challenge (random unscored prompts) and Explore (free position browser).
 *  - **Triads** — the 24 close-voiced triad inversions, top string group first.
 */
enum class TrainerSection { Scales, Triads }
enum class TrainerTab { Practice, Challenge, Explore }
enum class ExploreScale { Major, Minor, Pentatonic }

data class ChallengePrompt(
    val key: PitchClass,
    val box: CagedBox,
    val mode: CagedMode,
    val subset: ScaleSubset,
    val pattern: Int,
)

@Stable
class CagedTrainerState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
) {
    val tuning = Tunings.standard

    var section by mutableStateOf(TrainerSection.Scales); private set
    var tab by mutableStateOf(TrainerTab.Practice); private set
    var key by mutableStateOf(PitchClass.G); private set
    var bpm by mutableStateOf(80); private set
    var audioDemo by mutableStateOf(true); private set
    var reveal by mutableStateOf(false); private set
    var isPlaying by mutableStateOf(false); private set
    var stepIndex by mutableStateOf(0); private set
    var challenge by mutableStateOf<ChallengePrompt?>(null); private set
    /** Note currently sounding in a Practice sweep (drives the fretboard highlight). */
    var activeNote by mutableStateOf<FretPosition?>(null); private set
    /** Index 0..23 of the triad currently selected/sounding, or -1. */
    var activeTriad by mutableStateOf(-1); private set
    /** Explore tab: which scale + which position is shown. */
    var exploreScale by mutableStateOf(ExploreScale.Major); private set
    var explorePos by mutableStateOf(0); private set

    private var job: Job? = null

    // ---- Practice derivations (the 5 CAGED boxes, shapes straight off the sheet) ----

    /** The guided run — 34 steps, one per diagram on the sheet. */
    val run: List<DrillStep> get() = CagedScales.PRACTICE_RUN
    val stepCount: Int get() = run.size
    val step: DrillStep get() = run[stepIndex.coerceIn(0, stepCount - 1)]
    val box: CagedBox get() = step.box
    /** 0-based index of the current box, for the "Box 3 of 5" readout. */
    val boxIndex: Int get() = step.box.ordinal
    /** Position of the current step within its own box, and that box's length. */
    val drillIndex: Int get() = stepIndex - run.indexOfFirst { it.box == step.box }
    val drillCount: Int get() = run.count { it.box == step.box }

    fun practiceNotes(): List<CagedNote> =
        CagedScales.resolve(key, step.box, step.mode, step.subset, tuning, pattern = step.pattern)

    /** The fret span the current shape occupies, for the label under the neck. */
    fun practiceWindow(): IntRange =
        CagedScales.window(key, step.box, tuning, step.mode, step.subset, step.pattern)

    fun triadSequence(): List<Pair<String, TriadShape>> = CagedScales.triadRun(key, tuning)

    fun selectSection(s: TrainerSection) { if (s == section) return; stop(); section = s }
    fun selectTab(t: TrainerTab) { if (t == tab) return; stop(); tab = t }
    fun chooseKey(pc: PitchClass) { key = PitchClass.of(pc.value); resetPlayback() }
    fun randomKey() { chooseKey(PitchClass.of(Random.nextInt(12))) }
    fun changeBpm(v: Int) { bpm = v.coerceIn(30, 240) }
    fun toggleAudioDemo() { audioDemo = !audioDemo }
    fun toggleReveal() { reveal = !reveal }
    fun setStep(i: Int) { stepIndex = i.coerceIn(0, stepCount - 1); resetPlayback() }
    fun nudgeStep(d: Int) = setStep(stepIndex + d)
    /** Jump to the first step of a box (the box scroller). */
    fun jumpToBox(box: CagedBox) {
        val i = run.indexOfFirst { it.box == box }
        if (i >= 0) setStep(i)
    }

    fun nextChallenge() {
        val box = CagedBox.entries[Random.nextInt(CagedBox.entries.size)]
        val mode = if (Random.nextBoolean()) CagedMode.Major else CagedMode.Minor
        val subset = if (Random.nextBoolean()) ScaleSubset.FullScale else ScaleSubset.Pentatonic
        challenge = ChallengePrompt(
            key = PitchClass.of(Random.nextInt(12)),
            box = box,
            mode = mode,
            subset = subset,
            pattern = Random.nextInt(CagedShapeTable.patternCount(box, mode, subset)) + 1,
        )
        reveal = false
    }

    private fun resetPlayback() { activeNote = null; activeTriad = -1 }

    /** Manually step a triad (Triads scroller): stop any loop, select, and pluck it. */
    fun setTriad(i: Int) {
        if (isPlaying) stop()
        val seq = triadSequence()
        if (seq.isEmpty()) return
        activeTriad = ((i % seq.size) + seq.size) % seq.size
        pluckTriad(seq[activeTriad].second)
    }
    fun nudgeTriad(d: Int) = setTriad((if (activeTriad < 0) 0 else activeTriad) + d)

    // ---- Explore (browse the SHEET's boxes, the same shapes the Guided run drills) ----
    /** Pentatonic browses the MINOR pentatonic boxes — what the button showed before. */
    private fun exploreModeSubset(): Pair<CagedMode, ScaleSubset> = when (exploreScale) {
        ExploreScale.Major -> CagedMode.Major to ScaleSubset.FullScale
        ExploreScale.Minor -> CagedMode.Minor to ScaleSubset.FullScale
        ExploreScale.Pentatonic -> CagedMode.Minor to ScaleSubset.Pentatonic
    }
    fun explorePositionsList(): List<ExplorePosition> {
        val (mode, subset) = exploreModeSubset()
        return CagedScales.explorePositions(key, mode, subset, tuning)
    }
    fun selectExploreScale(s: ExploreScale) { exploreScale = s; explorePos = 0 }
    fun setExploreIndex(i: Int) {
        val n = explorePositionsList().size
        explorePos = if (n > 0) ((i % n) + n) % n else 0
    }
    fun nudgeExplorePos(d: Int) = setExploreIndex(explorePos + d)

    private fun pluckTriad(shape: TriadShape) {
        val midis = shape.strings.mapIndexed { k, s -> Fretboard.noteAt(tuning, FretPosition(s, shape.frets[k])).midi.value }
        audio.playChord(midis, strumDelayMillis = 18, sustainMillis = 800)
    }

    fun toggle() { if (isPlaying) stop() else play() }

    fun stop() {
        isPlaying = false
        job?.cancel(); job = null
        resetPlayback()
        audio.stop()
    }

    fun play() {
        if (isPlaying) return
        isPlaying = true
        job = scope.launch {
            while (isPlaying) {
                val beat = (60_000L / bpm.coerceAtLeast(20))
                if (section == TrainerSection.Triads) {
                    val seq = triadSequence()
                    for (i in seq.indices) {
                        if (!isPlaying) break
                        activeTriad = i
                        pluckTriad(seq[i].second)
                        delay(beat)
                    }
                    if (isPlaying) { stop(); break }
                    continue
                }
                when (tab) {
                    TrainerTab.Practice -> {
                        // Play the current drill (up+down once), then advance to the next
                        // step — triad → scale → pentatonic of the leading mode, then the
                        // other mode, across every box — and stop after the last.
                        if (audioDemo) {
                            val notes = practiceNotes().sortedBy { Fretboard.noteAt(tuning, it.position).midi.value }
                            val sweep = notes + notes.dropLast(1).reversed()
                            for (n in sweep) {
                                if (!isPlaying) break
                                activeNote = n.position
                                audio.chokeChords()   // a guided-run sweep is one note at a time: damp the previous pluck
                                audio.playNote(Fretboard.noteAt(tuning, n.position).midi.value, durationMillis = (beat * 0.95).toInt().coerceAtLeast(150))
                                delay(beat)
                            }
                        } else {
                            for (b in 0 until 4) {
                                if (!isPlaying) break
                                audio.playNote(if (b == 0) 96 else 91, durationMillis = 30)
                                delay(beat)
                            }
                        }
                        if (!isPlaying) break
                        if (stepIndex >= stepCount - 1) { stop(); break }
                        stepIndex += 1
                        activeNote = null
                    }
                    TrainerTab.Challenge -> { isPlaying = false }
                    TrainerTab.Explore -> { isPlaying = false }
                }
            }
        }
    }
}

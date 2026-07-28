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
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.PitchClass
import app.guitar.theory.Scale
import app.guitar.theory.ScalePosition
import app.guitar.theory.ScaleSubset
import app.guitar.theory.TriadShape
import app.guitar.theory.Tunings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * State + play loop for the guitar "Scales & Triads" CAGED trainer (Android).
 * Mirror of chorect-web/src/app/cagedTrainerState.ts. Three tabs: Practice
 * (guided box/drill run), Challenge (random unscored prompts), Triads (24 triad
 * inversions). Standard tuning, guitar only.
 */
enum class TrainerTab { Practice, Challenge, Triads, Explore }
enum class ExploreScale { Major, Minor, Pentatonic }

data class DrillStep(val mode: CagedMode, val subset: ScaleSubset)
data class ChallengePrompt(val key: PitchClass, val box: CagedBox, val mode: CagedMode, val subset: ScaleSubset)

@Stable
class CagedTrainerState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
) {
    val tuning = Tunings.standard

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

    private val subsetOrder = listOf(ScaleSubset.Triad, ScaleSubset.FullScale, ScaleSubset.Pentatonic)

    // ---- Practice derivations (over the 7 major-scale POSITIONS, like Fretboard mode) ----
    /** Fret windows of the key's positions (7 for a diatonic key), low→high. */
    fun regions(): List<IntRange> {
        val r = CagedScales.practiceRegions(key, tuning)
        return r.ifEmpty { listOf(0..4) }
    }
    val regionCount: Int get() = regions().size
    val stepCount: Int get() = regionCount * 6
    val boxIndex: Int get() = minOf(stepIndex / 6, regionCount - 1)
    val drillIndex: Int get() = stepIndex % 6

    /** 6 drill steps: [triad,scale,pentatonic] of the leading mode then the other;
     *  the leading mode alternates each position. */
    fun drillSteps(boxIndex: Int): List<DrillStep> {
        val lead = if (boxIndex % 2 == 0) CagedMode.Major else CagedMode.Minor
        val other = if (lead == CagedMode.Major) CagedMode.Minor else CagedMode.Major
        return subsetOrder.map { DrillStep(lead, it) } + subsetOrder.map { DrillStep(other, it) }
    }

    val step: DrillStep get() = drillSteps(boxIndex)[drillIndex]

    fun practiceNotes(): List<CagedNote> {
        val w = regions()[boxIndex]
        return CagedScales.notesInWindow(key, w.first, w.last, step.mode, step.subset, tuning)
    }

    fun triadSequence(): List<Pair<String, TriadShape>> =
        CagedScales.triadInversions(key, "maj", tuning).map { "maj" to it } +
            CagedScales.triadInversions(key, "min", tuning).map { "min" to it }

    fun selectTab(t: TrainerTab) { if (t == tab) return; stop(); tab = t }
    fun chooseKey(pc: PitchClass) { key = PitchClass.of(pc.value); resetPlayback() }
    fun randomKey() { chooseKey(PitchClass.of(Random.nextInt(12))) }
    fun changeBpm(v: Int) { bpm = v.coerceIn(30, 240) }
    fun toggleAudioDemo() { audioDemo = !audioDemo }
    fun toggleReveal() { reveal = !reveal }
    fun setStep(i: Int) { stepIndex = i.coerceIn(0, stepCount - 1); resetPlayback() }
    fun nudgeStep(d: Int) = setStep(stepIndex + d)

    fun nextChallenge() {
        challenge = ChallengePrompt(
            key = PitchClass.of(Random.nextInt(12)),
            box = CagedBox.entries[Random.nextInt(CagedBox.entries.size)],
            mode = if (Random.nextBoolean()) CagedMode.Major else CagedMode.Minor,
            subset = if (Random.nextBoolean()) ScaleSubset.FullScale else ScaleSubset.Pentatonic,
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

    // ---- Explore (scroll positions like Fretboard mode) ----
    private fun exploreScaleObj(): Scale = when (exploreScale) {
        ExploreScale.Major -> CagedScales.EXPLORE_MAJOR
        ExploreScale.Minor -> CagedScales.EXPLORE_MINOR
        ExploreScale.Pentatonic -> CagedScales.EXPLORE_PENTATONIC
    }
    fun explorePositionsList(): List<ScalePosition> =
        CagedScales.explorePositions(key, exploreScaleObj(), tuning)
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
                when (tab) {
                    TrainerTab.Triads -> {
                        val seq = triadSequence()
                        for (i in seq.indices) {
                            if (!isPlaying) break
                            activeTriad = i
                            pluckTriad(seq[i].second)
                            delay(beat)
                        }
                    }
                    TrainerTab.Practice -> {
                        // Play the current drill (up+down once), then advance to the next
                        // drill step — arp → scale → pentatonic across every box — stop after the last.
                        if (audioDemo) {
                            val notes = practiceNotes().sortedBy { Fretboard.noteAt(tuning, it.position).midi.value }
                            val sweep = notes + notes.dropLast(1).reversed()
                            for (n in sweep) {
                                if (!isPlaying) break
                                activeNote = n.position
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

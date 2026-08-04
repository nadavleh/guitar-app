package app.guitar.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.guitar.audio.AudioEngine
import app.guitar.theory.RhythmPhrase
import app.guitar.theory.RhythmPhrases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Generates & loops a multi-bar rhythmic phrase (see [RhythmPhrases]). Plays a
 * synthesized woodblock click on each onset (bar downbeats accented) and publishes
 * [currentSlot] to drive the notation + drum-grid playheads. Same mixer-clock
 * lookahead scheduling as the drum machine, so timing never drifts.
 */
class RhythmPhraseState(
    private val audio: AudioEngine,
    private val scope: CoroutineScope,
) {
    var bars by mutableStateOf(2)
        private set
    var beatsPerBar by mutableStateOf(2)   // time signature N/4
        private set
    var bpm by mutableStateOf(30)
        private set
    var phrase by mutableStateOf<RhythmPhrase?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentSlot by mutableStateOf(-1)   // playhead; -1 when stopped
        private set

    private var job: Job? = null
    private var onsetAccent: Map<Int, Boolean> = emptyMap()

    private val click = synthClick(2000.0, 45)
    private val accentClick = synthClick(2800.0, 45)
    private val mClick = synthClick(1000.0, 45)
    private val mAccent = synthClick(1400.0, 45)

    /** Background metronome: soft, LOWER-pitched clicks on the beats (higher of
     *  the two on bar downbeats) so it reads under the phrase's woodblock. */
    var metronomeOn by mutableStateOf(false)
        private set
    fun toggleMetronome() { metronomeOn = !metronomeOn }

    fun generate() {
        stop()
        val p = RhythmPhrases.generatePhrase(bars, beatsPerBar, kotlin.random.Random.Default)
        phrase = p
        onsetAccent = p.onsets().associate { it.slot to it.accent }
    }

    // Named change* (not set*) to avoid clashing with the generated property setters.
    fun changeBars(v: Int) { bars = v.coerceIn(RhythmPhrases.MIN_BARS, RhythmPhrases.MAX_BARS); generate() }
    fun changeBeatsPerBar(v: Int) { if (v in RhythmPhrases.TIME_SIGNATURES) { beatsPerBar = v; generate() } }
    fun changeBpm(v: Int) { bpm = v.coerceIn(10, 300) }   // loop re-reads bpm each slot

    fun toggle() { if (isPlaying) stop() else start() }

    fun start() {
        val p = phrase ?: run { generate(); phrase } ?: return
        if (isPlaying) return
        isPlaying = true
        job = scope.launch { loop(p) }
    }

    fun stop() {
        job?.cancel(); job = null
        isPlaying = false
        currentSlot = -1
    }

    private fun scheduleClick(slot: Int, delayMs: Double, sr: Int) {
        val frames = (delayMs * sr / 1000.0).toInt().coerceAtLeast(0)
        val accent = onsetAccent[slot]
        if (accent != null) {
            if (accent) audio.playSamplesAt(accentClick, 1.0f, frames)
            else audio.playSamplesAt(click, 0.72f, frames)
        }
        // Background metronome on the beats (soft; bar downbeats slightly higher).
        val p = phrase
        if (metronomeOn && p != null && slot % RhythmPhrases.SLOTS_PER_BEAT == 0) {
            val bar = slot % (p.beatsPerBar * RhythmPhrases.SLOTS_PER_BEAT) == 0
            audio.playSamplesAt(if (bar) mAccent else mClick, if (bar) 0.55f else 0.4f, frames)
        }
    }

    private suspend fun loop(p: RhythmPhrase) {
        val sr = audio.sampleRate
        val total = p.totalSlots
        var nextOnsetNanos = System.nanoTime()
        var first = true
        while (isPlaying) {
            for (slot in 0 until total) {
                if (!isPlaying) break
                currentSlot = slot
                if (first) { scheduleClick(slot, 0.0, sr); first = false }
                val slotMs = (60_000.0 / bpm) / RhythmPhrases.SLOTS_PER_BEAT
                nextOnsetNanos += (slotMs * 1_000_000).toLong()
                val nextSlot = (slot + 1) % total
                val delayMs = ((nextOnsetNanos - System.nanoTime()) / 1_000_000.0).coerceAtLeast(0.0)
                scheduleClick(nextSlot, delayMs, sr)
                delay(delayMs.toLong().coerceAtLeast(0))
            }
        }
    }

    private fun synthClick(freqHz: Double, ms: Int, sr: Int = audio.sampleRate): FloatArray {
        val n = sr * ms / 1000
        val buf = FloatArray(n)
        val w = 2.0 * Math.PI * freqHz / sr
        for (i in 0 until n) {
            val env = Math.exp(-6.0 * i / n)
            buf[i] = (Math.sin(w * i) * env * 0.7).toFloat()
        }
        return buf
    }
}

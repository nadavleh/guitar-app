package app.guitar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Continuous-output audio engine.
 *
 * Architecture:
 *   - One persistent `AudioTrack` in MODE_STREAM, in PLAYING state for the
 *     engine's lifetime.
 *   - One dedicated high-priority **output thread** that ALWAYS writes — either
 *     mixed voices or silence. The track never stops, never flushes, never
 *     re-creates. The audio system sees a single continuous stream.
 *   - One **synthesis worker** (single-thread executor) that turns playNote /
 *     playChord requests into FloatArray voices and pushes them into the mixer.
 *     Decouples synthesis cost from UI taps.
 *   - **Voice storage and mixing** are managed by [VoiceMixer]. The output thread
 *     pulls fixed-size blocks (~128 frames per iteration) via [mixer.mixBlock],
 *     which sums active [VoiceSource]s (e.g. [BufferSource]) and removes exhausted voices.
 *   - **Polyphonic**: tapping a new note doesn't cut the previous — they ring
 *     together. Polyphony and [MAX_VOICES] cap are enforced by the mixer.
 *
 * This eliminates the per-tap pause/flush/play cycle that was causing glitches.
 */
class AudioTrackEngine(
    /** Must be the device's native output rate ([AudioRates.outputRate]) or Android
     *  denies the low-latency path and resamples every note. */
    override val sampleRate: Int = AudioRates.FALLBACK_RATE,
    /** The HAL's burst size ([AudioRates.framesPerBuffer]); the output thread writes
     *  in this quantum so its writes line up with what the HAL consumes. */
    private val framesPerBuffer: Int = AudioRates.FALLBACK_FRAMES_PER_BUFFER,
) : AudioEngine {

    private val synth = PluckedSynth(sampleRate)
    private val running = AtomicBoolean(true)

    private val systemMinBufferBytes: Int = AudioTrack.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)

    // Allocate the system minimum — it is the size guaranteed to initialize. The size that
    // actually governs latency is the EFFECTIVE size set below via setBufferSizeInFrames;
    // requesting a tiny buffer here instead risks the track failing to build at all.
    private val bufferSizeBytes: Int = systemMinBufferBytes

    /** The HAL's burst: the quantum the hardware consumes per wake-up. */
    private val burstFrames: Int = framesPerBuffer.coerceIn(32, 2048)

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        )
        .setBufferSizeInBytes(bufferSizeBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()
        .also {
            if (it.state == AudioTrack.STATE_INITIALIZED) {
                try { it.play() } catch (e: Exception) { Log.e(TAG, "initial play() failed", e) }
            } else {
                Log.e(TAG, "AudioTrack not initialized after build: state=${it.state}")
            }
        }

    private val mixer = VoiceMixer(sampleRate)

    /** How long after the last sound the output thread keeps feeding the track before it
     *  parks. Feeding it silence costs one (minimum-size) buffer of steady latency;
     *  NOT feeding it is far worse — the track underruns, AudioFlinger drops it from the
     *  active mix, and the audio HAL enters standby, so the next note pays codec warm-up
     *  (tens to hundreds of ms). That warm-up is what makes play-on-touch-down feel late,
     *  because every fretboard tap follows a silence. A practising user therefore keeps the
     *  stream warm continuously, while a backgrounded or abandoned app still powers down. */
    private val keepWarmNanos = 30_000_000_000L

    /** Deadline until which the output thread keeps feeding the track. Started warm so the
     *  very first tap after launch doesn't pay warm-up either. */
    @Volatile private var warmDeadline = System.nanoTime() + keepWarmNanos

    /** True while the output thread is feeding the track, so a new note starts immediately;
     *  false while parked, meaning the next note pays HAL warm-up. Logged per note. */
    @Volatile private var outputWarm = true

    /**
     * Effective (NOT allocated) ring-buffer depth in frames — the engine's steady output
     * latency, and the number worth minimizing.
     *
     * Because the output thread keeps the track continuously fed, a new note is only heard
     * after the already-queued audio drains, so queued depth == delay. `getMinBufferSize()`
     * is computed for the NORMAL mixer path and is typically several bursts deep, so leaving
     * the buffer at that size hands back much of what the low-latency path was meant to save.
     *
     * Starts at two HAL bursts (the documented low-latency size, and what Oboe uses) and
     * grows one burst at a time only if this particular device actually underruns — low
     * latency by default, self-healing on hardware that can't sustain it.
     */
    @Volatile private var effectiveBufferFrames = 0
        private set

    private var lastUnderruns = 0

    /** Last per-note timings, surfaced in [latencyReport] for the in-app readout. */
    @Volatile private var lastQueueMs = -1L
    @Volatile private var lastSynthMs = -1L

    /** When set, [playNote]/[playFrequency]/[playChord] play sampled voices from this
     *  bank instead of Karplus-Strong synthesis (M3). Drums ([playSamples]/[playSamplesAt])
     *  and the legacy engine are unaffected. Null = existing synth behavior (default). */
    @Volatile var voiceInstrument: SampleInstrument? = null

    private val synthesizer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GuitarAudio-synth").apply { isDaemon = true }
    }

    private val outputThread = Thread({
        runOutputLoop()
    }, "GuitarAudio-out").apply {
        priority = Thread.MAX_PRIORITY
        isDaemon = true
        start()
    }

    init {
        // Shrink the queue to the low-latency depth. Done after build (not via
        // setBufferSizeInBytes) so a device that refuses the small size still gets a
        // working track at its own minimum.
        applyEffectiveBuffer(burstFrames * 2)
        Log.i(
            TAG,
            "engine init: sampleRate=$sampleRate " +
                "framesPerBuffer=$framesPerBuffer " +
                "minBufBytes=$systemMinBufferBytes (${systemMinBufferBytes * 1000.0 / (sampleRate * 2)} ms) " +
                "trackBufFrames=${track.bufferSizeInFrames} (${track.bufferSizeInFrames * 1000.0 / sampleRate} ms) " +
                "perfMode=LOW_LATENCY"
        )
    }

    /** Set the effective queue depth, clamped to 1..8 bursts. Returns nothing; the granted
     *  size (the device has the final say) is recorded in [effectiveBufferFrames]. */
    private fun applyEffectiveBuffer(frames: Int) {
        val want = frames.coerceIn(burstFrames, burstFrames * 8)
        val got = try { track.setBufferSizeInFrames(want) } catch (e: Exception) {
            Log.w(TAG, "setBufferSizeInFrames($want) threw", e); -1
        }
        effectiveBufferFrames = if (got > 0) got else track.bufferSizeInFrames
        Log.i(
            TAG,
            "effective buffer = $effectiveBufferFrames frames " +
                "(${"%.1f".format(effectiveBufferFrames * 1000.0 / sampleRate)} ms), " +
                "requested $want, allocated ${track.bufferSizeInFrames}"
        )
    }

    /** What actually governs touch-to-sound delay right now — for the in-app readout, so a
     *  "still feels late" report can be answered with numbers instead of guesses. */
    fun latencyReport(): AudioLatencyReport = AudioLatencyReport(
        sampleRate = sampleRate,
        halBurstFrames = burstFrames,
        allocatedBufferFrames = track.bufferSizeInFrames,
        effectiveBufferFrames = effectiveBufferFrames,
        underruns = runCatching { track.underrunCount }.getOrDefault(-1),
        outputWarm = outputWarm,
        lastNoteQueueMs = lastQueueMs,
        lastNoteSynthMs = lastSynthMs,
    )

    private fun runOutputLoop() {
        // Keep the stream alive while idle by writing silence, rather than parking and
        // letting the track drain: an unfed AudioTrack underruns, gets dropped from
        // AudioFlinger's active mix, and lets the audio HAL fall into standby — so the
        // next note waits for the codec to warm up before a single sample is heard.
        // WRITE_BLOCKING paces the loop for us (it returns only when there's room), so
        // no sleep is needed while warm. After [keepWarmNanos] with nothing sounding we
        // do park, so an app left in the background stops drawing power.
        val chunkFrames = framesPerBuffer.coerceIn(32, 2048)
        val l = FloatArray(chunkFrames)
        val r = FloatArray(chunkFrames)
        val chunk = ShortArray(chunkFrames * 2)
        var blocksSinceUnderrunCheck = 0
        while (running.get() && !Thread.currentThread().isInterrupted) {
            // Every ~50 ms, let a device that genuinely can't sustain a 2-burst queue grow it
            // a burst at a time. Underruns are audible glitches, so we trade the latency back
            // only on hardware that demonstrably needs it — but check often enough that such
            // a device converges in a fraction of a second instead of crackling for seconds.
            if (++blocksSinceUnderrunCheck * chunkFrames >= sampleRate / 20) {
                blocksSinceUnderrunCheck = 0
                val u = runCatching { track.underrunCount }.getOrDefault(lastUnderruns)
                if (u > lastUnderruns) {
                    lastUnderruns = u
                    if (effectiveBufferFrames < burstFrames * 8) {
                        Log.i(TAG, "underruns=$u — growing the queue by one burst")
                        applyEffectiveBuffer(effectiveBufferFrames + burstFrames)
                    }
                }
            }
            if (nextOutputBlock(mixer, l, r, chunk, chunkFrames)) {
                warmDeadline = System.nanoTime() + keepWarmNanos
            } else if (System.nanoTime() - warmDeadline > 0) {
                if (outputWarm) {
                    outputWarm = false
                    Log.i(TAG, "output parked after ${keepWarmNanos / 1_000_000_000} s idle — next note pays HAL warm-up")
                }
                try { Thread.sleep(3) } catch (_: InterruptedException) { return }
                continue
            }
            if (!outputWarm) {
                outputWarm = true
                Log.i(TAG, "output resumed — stream warm again")
            }
            try {
                if (track.write(chunk, 0, chunkFrames * 2, AudioTrack.WRITE_BLOCKING) < 0) break
            } catch (e: Exception) { if (running.get()) Log.e(TAG, "output write threw", e); break }
        }
    }

    override fun playNote(midiNote: Int, durationMillis: Int, timbre: Timbre) {
        if (midiNote !in 0..127 || durationMillis <= 0) return
        if (!running.get()) return
        val tCall = System.nanoTime()
        synthesizer.execute {
            val inst = voiceInstrument
            if (inst != null) {
                addVoiceSource(
                    SampleSource(inst, midiNote),
                    // Samples ignore the synth's amplitude param, so map it to voice
                    // gain (0.6 = the Timbre default = unity) — keeps per-timbre level
                    // differences (e.g. the ear-training root boost) audible on samples.
                    gain = (timbre.amplitude / 0.6).toFloat(),
                    pan = Panner.forMidi(midiNote),
                    reverbSend = voiceReverbSend,
                    releaseMs = timbre.releaseMs,
                )
                return@execute
            }
            val tStart = System.nanoTime()
            val samples = synth.synthesize(
                midiNote = midiNote,
                durationSec = durationMillis / 1000.0,
                seed = System.nanoTime(),
                damping = timbre.damping,
                amplitude = timbre.amplitude,
                brightnessDecay = GUITAR_BRIGHTNESS_DECAY,
            )
            val tEnd = System.nanoTime()
            addVoice(
                samples,
                pan = Panner.forMidi(midiNote),
                reverbSend = voiceReverbSend,
                releaseMs = timbre.releaseMs,
            )
            val tAdded = System.nanoTime()
            lastQueueMs = (tStart - tCall) / 1_000_000
            lastSynthMs = (tEnd - tStart) / 1_000_000
            Log.i(
                TAG,
                "midi=$midiNote " +
                    "queue=${(tStart - tCall) / 1_000_000} ms " +
                    "synth=${(tEnd - tStart) / 1_000_000} ms " +
                    "add=${(tAdded - tEnd) / 1_000_000} ms " +
                    "warm=$outputWarm " +
                    "bufFrames=${track.bufferSizeInFrames} " +
                    "head=${track.playbackHeadPosition}"
            )
        }
    }

    override fun playFrequency(freqHz: Float, durationMillis: Int, timbre: Timbre) {
        if (freqHz <= 0f || durationMillis <= 0) return
        if (!running.get()) return
        synthesizer.execute {
            val inst = voiceInstrument
            if (inst != null) {
                val midi = Math.round(69 + 12 * (Math.log(freqHz.toDouble() / 440.0) / Math.log(2.0))).toInt().coerceIn(0, 127)
                addVoiceSource(SampleSource(inst, midi), gain = (timbre.amplitude / 0.6).toFloat(),
                    reverbSend = voiceReverbSend, releaseMs = timbre.releaseMs)
                return@execute
            }
            val samples = synth.synthesizeFrequency(
                freqHz = freqHz.toDouble(),
                durationSec = durationMillis / 1000.0,
                seed = System.nanoTime(),
                damping = timbre.damping,
                amplitude = timbre.amplitude,
                brightnessDecay = GUITAR_BRIGHTNESS_DECAY,
            )
            addVoice(samples, reverbSend = voiceReverbSend, releaseMs = timbre.releaseMs)
        }
    }

    override fun playChord(midiNotes: List<Int>, strumDelayMillis: Int, sustainMillis: Int, timbre: Timbre, bassBoost: Float) {
        if (midiNotes.isEmpty() || sustainMillis <= 0 || !running.get()) return
        val notes = midiNotes.filter { it in 0..127 }
        if (notes.isEmpty()) return
        val strumFrames = (sampleRate * strumDelayMillis / 1000).coerceAtLeast(0)
        val gain = (1.0 / kotlin.math.sqrt(notes.size.toDouble())).toFloat()
        // Bass emphasis: lowest note ×(1+bassBoost), tapering to ×1 at the top note.
        val minMidi = notes.min(); val maxMidi = notes.max()
        val span = (maxMidi - minMidi).coerceAtLeast(1)
        synthesizer.execute {
            val inst = voiceInstrument
            // Samples ignore the synth's amplitude param, so fold it into the voice
            // gain (0.6 = the Timbre default = unity); synth buffers already bake it in.
            val voiceGain = if (inst != null) (gain * timbre.amplitude / 0.6).toFloat() else gain
            notes.forEachIndexed { i, midi ->
                val boost = 1f + bassBoost * ((maxMidi - midi).toFloat() / span)
                val source: VoiceSource = if (inst != null) SampleSource(inst, midi)
                    else BufferSource(synth.synthesize(
                        midi, sustainMillis / 1000.0, System.nanoTime() + i,
                        timbre.damping, timbre.amplitude,
                        brightnessDecay = GUITAR_BRIGHTNESS_DECAY,
                    ))
                mixer.addAndCap(
                    MixVoice(
                        source,
                        voiceGain * boost,
                        strumFrames * i,
                        AmpEnvelope(sampleRate, 3.0, timbre.releaseMs.toDouble()),
                        reverbSend = voiceReverbSend,
                    ).also { it.pan = Panner.forMidi(midi) },
                    MAX_VOICES,
                )
            }
        }
    }

    override fun playSamples(samples: FloatArray, gain: Float) {
        if (!running.get() || samples.isEmpty() || gain <= 0f) return
        addVoice(samples, gain)
    }

    override fun playSamplesAt(samples: FloatArray, gain: Float, delayFrames: Int, chokeKey: String?) {
        if (!running.get() || samples.isEmpty() || gain <= 0f) return
        addVoice(samples, gain, delayFrames.coerceAtLeast(0), chokeKey = chokeKey)
    }

    /** Add any VoiceSource as a modern voice (envelope + pan + reverb send). */
    private fun addVoiceSource(
        source: VoiceSource,
        gain: Float = 1f,
        delayFrames: Int = 0,
        pan: Double = 0.0,
        reverbSend: Float = 0f,
        releaseMs: Int = 20,
    ) {
        mixer.addAndCap(
            MixVoice(source, gain, delayFrames, AmpEnvelope(sampleRate, 3.0, releaseMs.toDouble()),
                reverbSend = reverbSend).also { it.pan = pan },
            MAX_VOICES,
        )
    }

    private fun addVoice(
        samples: FloatArray,
        gain: Float = 1f,
        delayFrames: Int = 0,
        attackMs: Double = 3.0,
        releaseMs: Int = 20,
        pan: Double = 0.0,
        reverbSend: Float = 0f,
        chokeKey: String? = null,
    ) {
        if (samples.isEmpty()) return
        mixer.addAndCap(
            MixVoice(
                BufferSource(samples),
                gain,
                delayFrames,
                AmpEnvelope(sampleRate, attackMs, releaseMs.toDouble()),
                pan,
                reverbSend,
                chokeKey,
            ),
            MAX_VOICES,
        )
    }

    // Fade out rather than hard-cut: releaseAll() ramps every voice's envelope to
    // silence; mixBlock removes each once its envelope reports isSilent, so the
    // `activeCount == 0` check in nextOutputBlock naturally waits for the release
    // tail to finish before the output falls back to writing silence.
    override fun stop() { mixer.releaseAll() }

    override fun cutReverb() { mixer.clearReverb() }

    /** Set the modern-bus tone EQ gains (dB). */
    fun setEq(bassDb: Float, midDb: Float, trebleDb: Float) = mixer.setEq(bassDb, midDb, trebleDb)

    /** Per-voice reverb send (0..1) for guitar note/chord voices — set per selected
     *  Sound by the app. Drums (playSamples) stay dry regardless. */
    @Volatile var voiceReverbSend: Float = 0.03f
        private set

    fun setReverbSend(amount: Float) { voiceReverbSend = amount.coerceIn(0f, 1f) }

    override fun close() {
        running.set(false)
        synthesizer.shutdownNow()
        outputThread.interrupt()
        try { outputThread.join(500) } catch (_: InterruptedException) {}
        try {
            track.stop()
            track.release()
        } catch (e: Exception) {
            Log.e(TAG, "close() error", e)
        }
    }

    companion object {
        private const val TAG = "GuitarAudio"
        // Headroom for a 4-instrument percussion loop ringing over guitar plucks.
        private const val MAX_VOICES = 16
        // Dual-rate KS damping (M6): < 1.0 makes high harmonics decay faster than the
        // fundamental over the note — bright attack, warmer tail. 1.0 = uniform decay
        // (PluckedSynth's own default, used by tests that need reproducible behavior).
        private const val GUITAR_BRIGHTNESS_DECAY = 0.6
    }
}

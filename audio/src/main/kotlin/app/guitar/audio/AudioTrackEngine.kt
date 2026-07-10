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
    private val sampleRate: Int = 44100,
) : AudioEngine {

    private val synth = PluckedSynth(sampleRate)
    private val running = AtomicBoolean(true)

    private val systemMinBufferBytes: Int = AudioTrack.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)

    // Use exactly the system min — anything bigger just adds latency in idle-skip mode.
    private val bufferSizeBytes: Int = systemMinBufferBytes

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
        Log.i(
            TAG,
            "engine init: sampleRate=$sampleRate " +
                "minBufBytes=$systemMinBufferBytes (${systemMinBufferBytes * 1000.0 / (sampleRate * 2)} ms) " +
                "trackBufFrames=${track.bufferSizeInFrames} (${track.bufferSizeInFrames * 1000.0 / sampleRate} ms) " +
                "perfMode=LOW_LATENCY"
        )
    }

    private fun runOutputLoop() {
        // Idle: don't write silence — that would keep the AudioTrack ring buffer full
        // of nothing, adding ~buffer_size of latency to the next note. Park briefly;
        // when a voice is added we'll fall through and write immediately into a
        // mostly-empty buffer.
        val chunkFrames = 128
        val l = FloatArray(chunkFrames)
        val r = FloatArray(chunkFrames)
        val chunk = ShortArray(chunkFrames * 2)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            if (mixer.activeCount == 0 && mixer.isRingingOut()) { try { Thread.sleep(3) } catch (_: InterruptedException) { return }; continue }
            mixer.mixBlock(l, r, chunkFrames)
            for (i in 0 until chunkFrames) {
                chunk[2 * i] = (l[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                chunk[2 * i + 1] = (r[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
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
            Log.i(
                TAG,
                "midi=$midiNote " +
                    "queue=${(tStart - tCall) / 1_000_000} ms " +
                    "synth=${(tEnd - tStart) / 1_000_000} ms " +
                    "add=${(tAdded - tEnd) / 1_000_000} ms " +
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

    override fun playChord(midiNotes: List<Int>, strumDelayMillis: Int, sustainMillis: Int, timbre: Timbre) {
        if (midiNotes.isEmpty() || sustainMillis <= 0 || !running.get()) return
        val notes = midiNotes.filter { it in 0..127 }
        if (notes.isEmpty()) return
        val strumFrames = (sampleRate * strumDelayMillis / 1000).coerceAtLeast(0)
        val gain = (1.0 / kotlin.math.sqrt(notes.size.toDouble())).toFloat()
        synthesizer.execute {
            val inst = voiceInstrument
            // Samples ignore the synth's amplitude param, so fold it into the voice
            // gain (0.6 = the Timbre default = unity); synth buffers already bake it in.
            val voiceGain = if (inst != null) (gain * timbre.amplitude / 0.6).toFloat() else gain
            notes.forEachIndexed { i, midi ->
                val source: VoiceSource = if (inst != null) SampleSource(inst, midi)
                    else BufferSource(synth.synthesize(
                        midi, sustainMillis / 1000.0, System.nanoTime() + i,
                        timbre.damping, timbre.amplitude,
                        brightnessDecay = GUITAR_BRIGHTNESS_DECAY,
                    ))
                mixer.addAndCap(
                    MixVoice(
                        source,
                        voiceGain,
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

    override fun playSamplesAt(samples: FloatArray, gain: Float, delayFrames: Int) {
        if (!running.get() || samples.isEmpty() || gain <= 0f) return
        addVoice(samples, gain, delayFrames.coerceAtLeast(0))
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
            ),
            MAX_VOICES,
        )
    }

    // Fade out rather than hard-cut: releaseAll() ramps every voice's envelope to
    // silence; mixBlock removes each once its envelope reports isSilent, so the
    // idle-park `activeCount == 0` check in runOutputLoop naturally waits for the
    // release tail to finish before the output thread stops writing.
    override fun stop() { mixer.releaseAll() }

    /** Set the modern-bus tone EQ gains (dB). */
    fun setEq(bassDb: Float, midDb: Float, trebleDb: Float) = mixer.setEq(bassDb, midDb, trebleDb)

    /** Per-voice reverb send (0..1) for guitar note/chord voices — set per selected
     *  Sound by the app. Drums (playSamples) stay dry regardless. */
    @Volatile var voiceReverbSend: Float = 0.18f
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

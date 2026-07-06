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
 *   - `voices`: list of active voices, each a FloatArray + read position.
 *     The output thread mixes them sample-by-sample, removing exhausted voices.
 *   - **Polyphonic**: tapping a new note doesn't cut the previous — they ring
 *     together. Cap at MAX_VOICES to prevent overload.
 *
 * This eliminates the per-tap pause/flush/play cycle that was causing glitches.
 */
class AudioTrackEngine(
    private val sampleRate: Int = 44100,
) : AudioEngine {

    private val synth = PluckedSynth(sampleRate)
    private val running = AtomicBoolean(true)

    private val systemMinBufferBytes: Int = AudioTrack.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
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
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
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
        val chunk = ShortArray(chunkFrames)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            if (mixer.activeCount == 0) { try { Thread.sleep(3) } catch (_: InterruptedException) { return }; continue }
            mixer.mixBlock(l, r, chunkFrames)
            for (i in 0 until chunkFrames) {
                val s = l[i]                       // M1: mono — L only
                val c = if (s > 1f) 1f else if (s < -1f) -1f else s
                chunk[i] = (c * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
            try {
                if (track.write(chunk, 0, chunkFrames, AudioTrack.WRITE_BLOCKING) < 0) break
            } catch (e: Exception) { if (running.get()) Log.e(TAG, "output write threw", e); break }
        }
    }

    override fun playNote(midiNote: Int, durationMillis: Int, timbre: Timbre) {
        if (midiNote !in 0..127 || durationMillis <= 0) return
        if (!running.get()) return
        val tCall = System.nanoTime()
        synthesizer.execute {
            val tStart = System.nanoTime()
            val samples = synth.synthesize(
                midiNote = midiNote,
                durationSec = durationMillis / 1000.0,
                seed = System.nanoTime(),
                damping = timbre.damping,
                amplitude = timbre.amplitude,
            )
            val tEnd = System.nanoTime()
            addVoice(samples)
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
            val samples = synth.synthesizeFrequency(
                freqHz = freqHz.toDouble(),
                durationSec = durationMillis / 1000.0,
                seed = System.nanoTime(),
                damping = timbre.damping,
                amplitude = timbre.amplitude,
            )
            addVoice(samples)
        }
    }

    override fun playChord(midiNotes: List<Int>, strumDelayMillis: Int, sustainMillis: Int, timbre: Timbre) {
        if (midiNotes.isEmpty() || sustainMillis <= 0) return
        if (!running.get()) return
        synthesizer.execute {
            val mix = synth.synthesizeChord(
                midiNotes = midiNotes,
                sustainSec = sustainMillis / 1000.0,
                strumDelaySamples = (sampleRate * strumDelayMillis / 1000).coerceAtLeast(0),
                seedBase = System.nanoTime(),
                damping = timbre.damping,
                amplitude = timbre.amplitude,
            )
            if (mix.isNotEmpty()) addVoice(mix)
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

    private fun addVoice(samples: FloatArray, gain: Float = 1f, delayFrames: Int = 0) {
        if (samples.isEmpty()) return
        mixer.add(MixVoice(BufferSource(samples), gain, delayFrames))
        // MAX_VOICES enforcement moves into the mixer in M2 (release-quietest); M1 keeps
        // the simple cap to preserve behavior:
        mixer.capVoices(MAX_VOICES)
    }

    override fun stop() { mixer.clear() }

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
    }
}

package app.guitar.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Microphone capture for the tuner. Streams mono PCM-float windows of [bufferSize]
 * samples to a callback via a dedicated reader thread.
 *
 * The caller must hold the `RECORD_AUDIO` permission before calling [start].
 * [stop] is idempotent.
 *
 * Buffer choice: 2048 samples at 44.1 kHz is ~46 ms — short enough that the
 * dial feels responsive, long enough to cover one full period of low E2.
 */
class MicInput(
    val sampleRate: Int = 44100,
    val bufferSize: Int = 2048,
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    /**
     * Begin capture on a background thread. Each window of samples is delivered
     * as normalized floats in [-1, 1] to [onSamples], called from the reader thread.
     *
     * @return true if recording started, false if AudioRecord failed to initialise
     *   (e.g. permission denied, mic in use).
     */
    fun start(onSamples: (FloatArray) -> Unit): Boolean {
        if (running) return true
        val minSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minSize <= 0) return false
        // We allocate ~4x the minimum + at least 2 windows so a brief delay in the
        // reader thread doesn't drop samples.
        val recordBuffer = maxOf(minSize * 4, bufferSize * 2 /* shorts */ * 2)
        val ar = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recordBuffer,
            )
        } catch (_: Exception) {
            return false
        }
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            return false
        }
        record = ar
        running = true
        ar.startRecording()
        thread = Thread {
            val shorts = ShortArray(bufferSize)
            while (running) {
                val n = try {
                    ar.read(shorts, 0, bufferSize)
                } catch (_: Exception) {
                    break
                }
                if (n > 0) {
                    val out = FloatArray(n)
                    for (i in 0 until n) out[i] = shorts[i] / 32768f
                    try { onSamples(out) } catch (_: Throwable) { /* swallow UI errors */ }
                } else if (n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_BAD_VALUE) {
                    break
                }
            }
        }.apply {
            name = "fretboard-mic-reader"
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        try { thread?.join(200) } catch (_: InterruptedException) {}
        thread = null
    }
}

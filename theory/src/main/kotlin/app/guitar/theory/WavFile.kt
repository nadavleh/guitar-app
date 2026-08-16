package app.guitar.theory

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Minimal 16-bit PCM WAV writer — the export format for rendered percussion.
 *
 * WAV (not MP3) on purpose: a canonical RIFF header plus raw samples is ~30 lines and
 * needs no encoder dependency on either platform, it is lossless, and every DAW and OS
 * player opens it. An MP3 export would mean shipping an encoder (LAME/ffmpeg on
 * Android, a WASM encoder on the web) for a file that is a few seconds long.
 *
 * Pure Kotlin, no Android → shared by the app's export and the CLI render task.
 */
object WavFile {

    /** RIFF header size in bytes — the audio data starts here. */
    private const val HEADER_BYTES = 44

    /**
     * Encode mono [samples] (nominally in [-1, 1]) as a 16-bit PCM WAV file.
     * Values outside the range are clamped rather than allowed to wrap, so a hot mix
     * distorts gracefully instead of producing the loud crackle of integer overflow.
     */
    fun encodeMono16(samples: FloatArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val dataBytes = samples.size * channels * bytesPerSample
        val out = ByteArray(HEADER_BYTES + dataBytes)
        var p = 0

        fun ascii(s: String) { for (c in s) out[p++] = c.code.toByte() }
        fun le32(v: Int) {
            out[p++] = (v and 0xFF).toByte()
            out[p++] = ((v ushr 8) and 0xFF).toByte()
            out[p++] = ((v ushr 16) and 0xFF).toByte()
            out[p++] = ((v ushr 24) and 0xFF).toByte()
        }
        fun le16(v: Int) {
            out[p++] = (v and 0xFF).toByte()
            out[p++] = ((v ushr 8) and 0xFF).toByte()
        }

        ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); le32(16)
        le16(1)                                             // PCM, uncompressed
        le16(channels)
        le32(sampleRate)
        le32(sampleRate * channels * bytesPerSample)        // byte rate
        le16(channels * bytesPerSample)                     // block align
        le16(bitsPerSample)
        ascii("data"); le32(dataBytes)

        for (s in samples) {
            val clamped = min(max(s, -1f), 1f)
            // 32767 (not 32768) so +1.0 and -1.0 are both representable without wrapping.
            le16((clamped * 32767f).roundToInt() and 0xFFFF)
        }
        return out
    }

    /** A decoded WAV: mono float samples plus the rate they were recorded at. */
    data class Decoded(val samples: FloatArray, val sampleRate: Int)

    /**
     * Decode a 16-bit PCM RIFF/WAVE file to mono floats (multi-channel input is
     * averaged down). Returns null for anything else — a compressed or 24/32-bit
     * file is a caller problem, not something to guess at.
     *
     * Walks the chunk list rather than assuming a 44-byte header, so a file carrying
     * a "LIST"/"fact" chunk before "data" still decodes.
     */
    fun decodeMono16(bytes: ByteArray): Decoded? {
        if (bytes.size < 12) return null
        fun u8(i: Int) = bytes[i].toInt() and 0xFF
        fun le16At(i: Int) = u8(i) or (u8(i + 1) shl 8)
        fun le32At(i: Int) = u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16) or (u8(i + 3) shl 24)
        fun tag(i: Int) = String(bytes, i, 4, Charsets.US_ASCII)

        if (tag(0) != "RIFF" || tag(8) != "WAVE") return null
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var p = 12
        while (p + 8 <= bytes.size) {
            val id = tag(p)
            val size = le32At(p + 4)
            val body = p + 8
            if (size < 0 || body + size > bytes.size) {
                // Truncated final chunk: still usable if it's the data we want.
                if (id != "data") return null
            }
            when (id) {
                "fmt " -> {
                    if (le16At(body) != 1) return null       // not uncompressed PCM
                    channels = le16At(body + 2)
                    sampleRate = le32At(body + 4)
                    bits = le16At(body + 14)
                }
                "data" -> {
                    if (bits != 16 || channels < 1 || sampleRate <= 0) return null
                    val avail = min(size, bytes.size - body)
                    val frames = avail / (2 * channels)
                    val out = FloatArray(frames)
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) {
                            val raw = le16At(body + (f * channels + c) * 2)
                            sum += (if (raw >= 0x8000) raw - 0x10000 else raw) / 32768f
                        }
                        out[f] = sum / channels
                    }
                    return Decoded(out, sampleRate)
                }
            }
            p = body + size + (size and 1)                   // chunks are word-aligned
        }
        return null
    }
}

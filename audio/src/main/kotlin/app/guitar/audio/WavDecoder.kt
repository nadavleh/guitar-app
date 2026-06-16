package app.guitar.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal RIFF/WAVE decoder → mono FloatArray at [targetRate] (default 44.1 kHz),
 * for loading bundled one-shot drum samples. Pure Kotlin (no Android), so it's
 * unit-testable on the JVM.
 *
 * Supports PCM 8/16/24/32-bit and IEEE-float 32-bit, mono or multi-channel
 * (channels are averaged to mono). Sample rates other than [targetRate] are
 * linearly resampled.
 */
object WavDecoder {

    fun decode(bytes: ByteArray, targetRate: Int = 44100): FloatArray? {
        if (bytes.size < 44) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes[0].toInt().toChar() != 'R' || bytes[1].toInt().toChar() != 'I' ||
            bytes[2].toInt().toChar() != 'F' || bytes[3].toInt().toChar() != 'F') return null
        // "WAVE" at 8
        if (String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null

        var pos = 12
        var audioFormat = 1
        var channels = 1
        var sampleRate = targetRate
        var bits = 16
        var dataOff = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > bytes.size + 1) break
            when (id) {
                "fmt " -> {
                    audioFormat = bb.getShort(body).toInt() and 0xFFFF
                    channels = (bb.getShort(body + 2).toInt() and 0xFFFF).coerceAtLeast(1)
                    sampleRate = bb.getInt(body + 4)
                    bits = bb.getShort(body + 14).toInt() and 0xFFFF
                }
                "data" -> { dataOff = body; dataLen = minOf(size, bytes.size - body) }
            }
            pos = body + size + (size and 1)   // chunks are word-aligned
        }
        if (dataOff < 0 || bits == 0) return null

        val bytesPerSample = bits / 8
        val frameSize = bytesPerSample * channels
        if (frameSize == 0) return null
        val frames = dataLen / frameSize
        val mono = FloatArray(frames)
        val isFloat = audioFormat == 3
        for (f in 0 until frames) {
            var acc = 0.0
            for (c in 0 until channels) {
                val o = dataOff + f * frameSize + c * bytesPerSample
                acc += when {
                    isFloat && bits == 32 -> bb.getFloat(o).toDouble()
                    bits == 16 -> bb.getShort(o) / 32768.0
                    bits == 8 -> ((bytes[o].toInt() and 0xFF) - 128) / 128.0
                    bits == 24 -> {
                        val v = (bytes[o].toInt() and 0xFF) or
                            ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                            ((bytes[o + 2].toInt() and 0xFF) shl 16)
                        val signed = if (v and 0x800000 != 0) v or -0x1000000 else v
                        signed / 8388608.0
                    }
                    bits == 32 -> bb.getInt(o) / 2147483648.0
                    else -> 0.0
                }
            }
            mono[f] = (acc / channels).toFloat()
        }
        return if (sampleRate == targetRate) mono else resample(mono, sampleRate, targetRate)
    }

    /** Simple linear resampler. */
    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (input.isEmpty() || from <= 0) return input
        val ratio = to.toDouble() / from
        val outLen = (input.size * ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val src = i / ratio
            val i0 = src.toInt()
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (src - i0).toFloat()
            out[i] = input[i0] * (1 - frac) + input[i1] * frac
        }
        return out
    }
}

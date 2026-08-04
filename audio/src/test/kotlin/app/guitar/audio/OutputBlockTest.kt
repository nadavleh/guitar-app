package app.guitar.audio

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The output thread must be able to feed the AudioTrack on EVERY iteration, idle or not:
 * a track that stops being fed underruns, AudioFlinger drops it from the active mix, and
 * the audio HAL enters standby — so the next note pays codec warm-up and play-on-touch-down
 * feels late. [nextOutputBlock] is therefore total: it always yields a full, well-formed
 * block, and only reports whether that block carries audio.
 */
class OutputBlockTest {

    /** A freshly-built Freeverb reports "still ringing" until it has processed one block
     *  (lastTail starts at 1f), so the engine mixes one silent block at startup before the
     *  cheap idle path takes over. Settle that so the assertions below cover steady state. */
    private fun settled(m: VoiceMixer, frames: Int) {
        nextOutputBlock(m, FloatArray(frames), FloatArray(frames), ShortArray(frames * 2), frames)
    }

    @Test
    fun `an idle mixer still yields a full block of silence`() {
        val m = VoiceMixer(sampleRate = 48000)
        val frames = 8
        settled(m, frames)
        val l = FloatArray(frames); val r = FloatArray(frames)
        // Pre-dirty the chunk: the idle path must overwrite every short, not leave the
        // previous block's audio behind (that would loop the last note as a buzz).
        val chunk = ShortArray(frames * 2) { 999 }

        val carriesAudio = nextOutputBlock(m, l, r, chunk, frames)

        assertFalse(carriesAudio, "no voices -> block carries no audio")
        for (i in 0 until frames * 2) assertEquals(0, chunk[i], "short $i must be silence")
    }

    @Test
    fun `an active voice yields a block that carries audio`() {
        val m = VoiceMixer(sampleRate = 48000)
        m.add(MixVoice(BufferSource(FloatArray(64) { 0.5f })))
        val frames = 8
        val l = FloatArray(frames); val r = FloatArray(frames)
        val chunk = ShortArray(frames * 2)

        val carriesAudio = nextOutputBlock(m, l, r, chunk, frames)

        assertTrue(carriesAudio, "a sounding voice -> block carries audio")
        assertTrue(chunk.any { it != 0.toShort() }, "mixed block must not be silent")
    }

    @Test
    fun `writes exactly frames times two shorts and nothing past them`() {
        val m = VoiceMixer(sampleRate = 48000)
        val frames = 4
        settled(m, frames)
        val l = FloatArray(frames); val r = FloatArray(frames)
        // Oversized chunk with a sentinel tail — the block must not run past its length.
        val chunk = ShortArray(frames * 2 + 3) { 777 }

        nextOutputBlock(m, l, r, chunk, frames)

        for (i in 0 until frames * 2) assertEquals(0, chunk[i], "short $i in block")
        for (i in frames * 2 until chunk.size) assertEquals(777, chunk[i], "short $i past block")
    }
}

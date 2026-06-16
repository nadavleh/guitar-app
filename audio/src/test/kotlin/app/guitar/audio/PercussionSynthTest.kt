package app.guitar.audio

import app.guitar.theory.PercussionInstrument
import app.guitar.theory.PercussionVoices
import kotlin.test.Test
import kotlin.test.assertTrue

class PercussionSynthTest {

    @Test fun everyVoiceSynthesizesToAValidBuffer() {
        val synth = PercussionSynth()
        for (inst in PercussionInstrument.entries) {
            for (v in 0 until PercussionVoices.voiceCount(inst)) {
                val buf = synth.synthesize(inst, v)
                assertTrue(buf.isNotEmpty(), "$inst voice $v produced an empty buffer")
                assertTrue(buf.all { it.isFinite() }, "$inst voice $v has non-finite samples")
                assertTrue(buf.all { it in -1.05f..1.05f }, "$inst voice $v clips outside [-1,1]")
                // Ends at silence (fade-out) so consecutive hits don't click.
                assertTrue(kotlin.math.abs(buf.last()) < 1e-3f, "$inst voice $v doesn't fade to zero")
            }
        }
    }

    @Test fun voiceCountsMatchTheNewLayout() {
        assertTrue(PercussionVoices.voiceCount(PercussionInstrument.Surdo) == 3)
        assertTrue(PercussionVoices.voiceCount(PercussionInstrument.Tamborim) == 3)
        assertTrue(PercussionVoices.voiceCount(PercussionInstrument.Pandeiro) == 5)
        assertTrue(PercussionVoices.voiceCount(PercussionInstrument.Agogo) == 2)
    }
}

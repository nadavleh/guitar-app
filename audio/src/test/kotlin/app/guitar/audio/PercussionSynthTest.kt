package app.guitar.audio

import app.guitar.theory.PercussionCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

class PercussionSynthTest {

    @Test fun everyVoiceSynthesizesToAValidBuffer() {
        val synth = PercussionSynth()
        // Cover the WHOLE catalog (incl. the added instruments' fallback synthesis),
        // not just the default kit.
        for (inst in PercussionCatalog.ALL) {
            for (v in 0 until inst.voiceCount) {
                val buf = synth.synthesize(inst, v)
                assertTrue(buf.isNotEmpty(), "${inst.id} voice $v produced an empty buffer")
                assertTrue(buf.all { it.isFinite() }, "${inst.id} voice $v has non-finite samples")
                assertTrue(buf.all { it in -1.05f..1.05f }, "${inst.id} voice $v clips outside [-1,1]")
                // Ends at silence (fade-out) so consecutive hits don't click.
                assertTrue(kotlin.math.abs(buf.last()) < 1e-3f, "${inst.id} voice $v doesn't fade to zero")
            }
        }
    }

    @Test fun defaultKitVoiceCountsMatchTheBundledSamples() {
        assertTrue(PercussionCatalog.Surdo.voiceCount == 3)
        assertTrue(PercussionCatalog.Tamborim.voiceCount == 3)
        assertTrue(PercussionCatalog.Pandeiro.voiceCount == 4)
        assertTrue(PercussionCatalog.Agogo.voiceCount == 2)
    }
}

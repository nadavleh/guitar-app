package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PercussionPatternTest {

    @Test fun `empty pattern has all silent cells`() {
        val p = PercussionPattern.empty()
        assertTrue(p.isEmpty())
        for (inst in PercussionInstrument.entries) {
            for (s in 0 until PERCUSSION_SLOTS) assertNull(p.voiceAt(inst, s))
        }
    }

    @Test fun `cycling a 2-voice instrument goes null to 0 to 1 to null`() {
        var p = PercussionPattern.empty()
        val inst = PercussionInstrument.Agogo  // 2 voices
        assertNull(p.voiceAt(inst, 0))
        p = p.cycled(inst, 0); assertEquals(0, p.voiceAt(inst, 0))
        p = p.cycled(inst, 0); assertEquals(1, p.voiceAt(inst, 0))
        p = p.cycled(inst, 0); assertNull(p.voiceAt(inst, 0))
    }

    @Test fun `cycling a 3-voice instrument goes null to 0 to 1 to 2 to null`() {
        var p = PercussionPattern.empty()
        val inst = PercussionInstrument.Surdo  // 3 voices
        p = p.cycled(inst, 5); assertEquals(0, p.voiceAt(inst, 5))
        p = p.cycled(inst, 5); assertEquals(1, p.voiceAt(inst, 5))
        p = p.cycled(inst, 5); assertEquals(2, p.voiceAt(inst, 5))
        p = p.cycled(inst, 5); assertNull(p.voiceAt(inst, 5))
    }

    @Test fun `cycling the 5-voice pandeiro wraps after voice 4`() {
        var p = PercussionPattern.empty()
        val inst = PercussionInstrument.Pandeiro  // 5 voices
        for (expected in 0..4) {
            p = p.cycled(inst, 9); assertEquals(expected, p.voiceAt(inst, 9))
        }
        p = p.cycled(inst, 9); assertNull(p.voiceAt(inst, 9))
    }

    @Test fun `pattern encodes and decodes round-trip`() {
        val p = PercussionPattern.SAMBA
        val decoded = PercussionPattern.decode(p.encode())
        assertEquals(p, decoded)
        // Empty round-trips too.
        assertEquals(PercussionPattern.empty(), PercussionPattern.decode(PercussionPattern.empty().encode()))
    }

    @Test fun `decode rejects malformed or out-of-range input`() {
        assertNull(PercussionPattern.decode("garbage"))
        assertNull(PercussionPattern.decode(""))
        // A surdo cell of 9 is out of range (surdo has 3 voices).
        val bad = PercussionPattern.SAMBA.encode().replaceFirst("1", "9")
        assertNull(PercussionPattern.decode(bad))
    }

    @Test fun `cycling one cell does not disturb the others`() {
        var p = PercussionPattern.empty().cycled(PercussionInstrument.Tamborim, 2)
        p = p.cycled(PercussionInstrument.Tamborim, 7)
        assertEquals(0, p.voiceAt(PercussionInstrument.Tamborim, 2))
        assertEquals(0, p.voiceAt(PercussionInstrument.Tamborim, 7))
        assertNull(p.voiceAt(PercussionInstrument.Tamborim, 3))
    }

    @Test fun `clearedRow wipes only that instrument`() {
        var p = PercussionPattern.SAMBA
        p = p.clearedRow(PercussionInstrument.Pandeiro)
        assertTrue((0 until PERCUSSION_SLOTS).all { p.voiceAt(PercussionInstrument.Pandeiro, it) == null })
        // Surdo still has its downbeat hits
        assertTrue((0 until PERCUSSION_SLOTS).any { p.voiceAt(PercussionInstrument.Surdo, it) != null })
    }

    @Test fun `samba preset hits the downbeat of each bar on the surdo`() {
        val p = PercussionPattern.SAMBA
        // slot 0 = bar 1 downbeat, slot 8 = bar 2 downbeat
        assertTrue(p.voiceAt(PercussionInstrument.Surdo, 0) != null)
        assertTrue(p.voiceAt(PercussionInstrument.Surdo, 8) != null)
    }

    @Test fun `samba pandeiro plays every sixteenth`() {
        val p = PercussionPattern.SAMBA
        for (s in 0 until PERCUSSION_SLOTS) {
            assertTrue(p.voiceAt(PercussionInstrument.Pandeiro, s) != null, "pandeiro silent at slot $s")
        }
    }

    @Test fun `slot and loop timing at 120 bpm`() {
        // quarter = 500 ms, sixteenth = 125 ms, loop = 16 * 125 = 2000 ms
        assertEquals(125L, PercussionTiming.slotMs(120))
        assertEquals(2000L, PercussionTiming.loopMs(120))
    }
}

package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrumBlocksTest {

    private val teleco = PercussionBuiltins.presetByLabel("Tamborim — teleco-teco")!!
    private val paVar1 = PercussionBuiltins.presetByLabel("Bongo — partido alto var 1")!!
    private val pa = PercussionBuiltins.presetByLabel("Bongo — partido alto")!!

    @Test fun `block edits, encode and decode round-trip`() {
        var b = DrumBlock.empty("My block", 4)
            .withTrack(PercussionCatalog.Tamborim)
            .withTrack(PercussionCatalog.Bongo)
        b = b.withCell(0, 0, teleco).withCell(1, 1, paVar1).withCell(1, 2, pa)
        assertEquals(2, b.tracks.size)
        assertEquals(teleco, b.tracks[0].cells[0])
        assertEquals(pa, b.tracks[1].cells[2])
        assertEquals(b, DrumBlock.decode(b.encode()))
        // Resizing keeps cells; growing adds empty columns.
        assertEquals(pa, b.withPhraseCount(6).tracks[1].cells[2])
        assertNull(b.withPhraseCount(2).tracks[1].cells.getOrNull(2))
    }

    @Test fun `blocks merge only when phrase counts match`() {
        val a = DrumBlock.empty("A", 4).withTrack(PercussionCatalog.Tamborim).withCell(0, 0, teleco)
        val c = DrumBlock.empty("C", 4).withTrack(PercussionCatalog.Bongo).withCell(0, 0, pa)
        val merged = a.mergedWith(c)!!
        assertEquals(2, merged.tracks.size)
        assertEquals(teleco, merged.tracks[0].cells[0])
        assertEquals(pa, merged.tracks[1].cells[0])
        assertNull(a.mergedWith(DrumBlock.empty("D", 3)))
    }

    @Test fun `return rule adds an accented measure-2 stroke on beat 1`() {
        // Partido alto's slot 0 is empty and slot 8 (measure-2 downbeat) is voice 1;
        // following variation 1 it gains an ACCENTED voice-1 hit on slot 0.
        val mat = materializedTemplate(pa, prev = paVar1)!!
        assertEquals(1 + PERCUSSION_ACCENT, mat[0])
        assertEquals(pa.template.drop(1), mat.drop(1))
        // No rule → template unchanged; rule but occupied slot 0 → unchanged.
        assertEquals(pa.template, materializedTemplate(pa, prev = teleco))
        assertEquals(teleco.template, materializedTemplate(teleco, prev = paVar1))
        assertNull(materializedTemplate(null, prev = paVar1))
    }

    @Test fun `per-slot dynamics cycle, survive voice cycling, and round-trip`() {
        val tamb = PercussionCatalog.Tamborim
        var p = PercussionPattern.empty().cycled(tamb, 0)           // voice 0, full
        assertEquals(0, p.dynLevelAt(tamb, 0))
        p = p.dynCycled(tamb, 0)                                     // 75 %
        assertEquals(1, p.dynLevelAt(tamb, 0))
        assertEquals(0, p.voiceAt(tamb, 0))
        // Accent + dyn coexist; voice cycling keeps both.
        p = p.accentToggled(tamb, 0)
        assertTrue(p.isAccented(tamb, 0))
        p = p.cycled(tamb, 0)                                        // voice 1
        assertEquals(1, p.voiceAt(tamb, 0))
        assertTrue(p.isAccented(tamb, 0))
        assertEquals(1, p.dynLevelAt(tamb, 0))
        // Encode/decode round-trips the combined raw value (1000 + 100 + 1).
        assertEquals(p, PercussionPattern.decode(p.encode()))
        // Dyn cycles back to full after level 3.
        p = p.dynCycled(tamb, 0).dynCycled(tamb, 0).dynCycled(tamb, 0)
        assertEquals(0, p.dynLevelAt(tamb, 0))
    }
}

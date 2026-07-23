package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrumBlocksTest {

    private val teleco = PercussionBuiltins.presetByLabel("Tamborim — Teleco-teco")!!
    private val paVar1 = PercussionBuiltins.presetByLabel("Bongo — Partido Alto Var 1")!!
    private val pa = PercussionBuiltins.presetByLabel("Bongo — Partido Alto")!!

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

    @Test fun `phrase file round-trips a custom phrase incl accents, dynamics and swing`() {
        val custom = PercussionBuiltins.PresetTrack(
            "My Groove Phrase", PercussionCatalog.Tamborim,
            listOf(100, 1001, 2, 0, null, 1, null, 0, 100, 1001, 2, 0, null, 1, null, 0),
            swing = 35,
        )
        val decoded = PhraseFile.decode(PhraseFile.encode(custom))
        assertEquals(custom, decoded)
        // The preset codec itself round-trips too (persistence path).
        assertEquals(custom, decodePresetTrack(encodePresetTrack(custom)))
        // Garbage / wrong format is rejected.
        assertNull(PhraseFile.decode("""{"format":"chorect-beat","pattern":"x"}"""))
        assertNull(PhraseFile.decode("nonsense"))
    }

    @Test fun `per-cell swing override round-trips and default swing stays plain`() {
        var b = DrumBlock.empty("Swing", 2).withTrack(PercussionCatalog.Tamborim)
        b = b.withCell(0, 0, teleco.copy(swing = 55)).withCell(0, 1, teleco)
        val decoded = DrumBlock.decode(b.encode())!!
        assertEquals(55, decoded.tracks[0].cells[0]?.swing)
        assertEquals(teleco, decoded.tracks[0].cells[1])   // untouched cell = library phrase
        assertEquals(b, decoded)
        // The encoding only annotates the overridden cell.
        assertTrue(b.encode().contains("@55"))
        assertEquals(1, Regex("@\\d+").findAll(b.encode()).count())
    }

    @Test fun `opening cell encodes with a caret prefix and round-trips`() {
        var b = DrumBlock.empty("Entrada block", 2)
            .withTrack(PercussionCatalog.Tamborim)
            .withTrack(PercussionCatalog.Bongo)
        b = b.withCell(0, 0, teleco).withCell(0, 1, teleco).withCell(1, 0, pa)
            .withOpeningCell(0, paVar1)
        assertEquals(paVar1, b.tracks[0].opening)
        assertNull(b.tracks[1].opening)
        val enc = b.encode()
        assertTrue(enc.contains(":^"))                 // opening rides as a "^cell" prefix
        val decoded = DrumBlock.decode(enc)!!
        assertEquals(b, decoded)
        assertEquals(paVar1, decoded.tracks[0].opening)
        // Clearing the opening drops the prefix; per-cell swing survives on openings.
        assertEquals(b.withOpeningCell(0, null), DrumBlock.decode(b.withOpeningCell(0, null).encode()))
        val swung = b.withOpeningCell(0, paVar1.copy(swing = 40))
        assertEquals(40, DrumBlock.decode(swung.encode())!!.tracks[0].opening?.swing)
    }

    @Test fun `block file embeds custom phrases and round-trips`() {
        val custom = PercussionBuiltins.PresetTrack(
            "My Entrada", PercussionCatalog.Tamborim,
            listOf(100, 2, 1, 0, null, 1, null, 0, 100, 2, 1, 0, null, 1, null, 0),
            swing = 15,
        )
        val resolve = { lbl: String -> if (lbl == custom.label) custom else PercussionBuiltins.presetByLabel(lbl) }
        var b = DrumBlock.empty("Portable", 2).withTrack(PercussionCatalog.Tamborim)
        b = b.withCell(0, 0, teleco).withCell(0, 1, teleco).withOpeningCell(0, custom)
        val file = BlockFile.encode(b.encode(resolve), listOf(custom))
        val (encodedBlock, phrases) = BlockFile.decode(file)!!
        assertEquals(listOf(custom), phrases)
        val restored = DrumBlock.decode(encodedBlock) { lbl -> phrases.firstOrNull { it.label == lbl } ?: PercussionBuiltins.presetByLabel(lbl) }
        assertEquals(b, restored)
        // Wrong format / garbage rejected.
        assertNull(BlockFile.decode("""{"format":"chorect-beat","pattern":"x"}"""))
        assertNull(BlockFile.decode("nonsense"))
    }

    @Test fun `built-in blocks decode fully against the built-in phrase library`() {
        for (enc in BUILTIN_BLOCKS) {
            val b = DrumBlock.decode(enc)
            assertTrue(b != null && !b.isEmpty(), "built-in block failed to decode: $enc")
        }
        val tb = DrumBlock.decode(BUILTIN_BLOCKS.first())!!
        assertEquals("Tamborim Block", tb.name)
        assertEquals(8, tb.phraseCount)
        assertEquals("Tamborim — Entrada 1", tb.tracks[0].opening?.label)
        assertTrue(tb.tracks[0].cells.all { it != null })   // every phrase label resolved
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

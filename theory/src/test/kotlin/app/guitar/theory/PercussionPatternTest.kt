package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PercussionPatternTest {

    /** A non-trivial hand-built pattern (Surdo on both bar downbeats + assorted hits)
     *  for the structural tests, now that there is no built-in preset. */
    private fun samplePattern(): PercussionPattern =
        PercussionPattern.empty()
            .withCell(PercussionCatalog.Surdo, 0, 1)
            .withCell(PercussionCatalog.Surdo, 8, 1)
            .withCell(PercussionCatalog.Tamborim, 3, 0)
            .withCell(PercussionCatalog.Bongo, 5, 2)

    @Test fun `empty pattern has all silent cells`() {
        val p = PercussionPattern.empty()
        assertTrue(p.isEmpty())
        assertEquals(PercussionCatalog.DEFAULT_KIT, p.instruments)   // default kit = surdo, tamborim, bongo
        for (inst in p.instruments) {
            for (s in 0 until PERCUSSION_SLOTS) assertNull(p.voiceAt(inst, s))
        }
    }

    @Test fun `cycling a 2-voice instrument goes null to 0 to 1 to null`() {
        val inst = PercussionCatalog.Agogo  // 2 voices (not in the default kit — add it)
        var p = PercussionPattern.empty().addInstrument(inst)
        assertNull(p.voiceAt(inst, 0))
        p = p.cycled(inst, 0); assertEquals(0, p.voiceAt(inst, 0))
        p = p.cycled(inst, 0); assertEquals(1, p.voiceAt(inst, 0))
        p = p.cycled(inst, 0); assertNull(p.voiceAt(inst, 0))
    }

    @Test fun `cycling a 3-voice instrument goes null to 0 to 1 to 2 to null`() {
        var p = PercussionPattern.empty()
        val inst = PercussionCatalog.Surdo  // 3 voices
        p = p.cycled(inst, 5); assertEquals(0, p.voiceAt(inst, 5))
        p = p.cycled(inst, 5); assertEquals(1, p.voiceAt(inst, 5))
        p = p.cycled(inst, 5); assertEquals(2, p.voiceAt(inst, 5))
        p = p.cycled(inst, 5); assertNull(p.voiceAt(inst, 5))
    }

    @Test fun `cycling the 4-voice bongo wraps after voice 3`() {
        var p = PercussionPattern.empty()
        val inst = PercussionCatalog.Bongo  // 4 voices
        for (expected in 0..3) {
            p = p.cycled(inst, 9); assertEquals(expected, p.voiceAt(inst, 9))
        }
        p = p.cycled(inst, 9); assertNull(p.voiceAt(inst, 9))
    }

    @Test fun `pattern encodes and decodes round-trip`() {
        val p = samplePattern()
        val decoded = PercussionPattern.decode(p.encode())
        assertEquals(p, decoded)
        // Empty round-trips too.
        assertEquals(PercussionPattern.empty(), PercussionPattern.decode(PercussionPattern.empty().encode()))
    }

    @Test fun `non-default meter round-trips through encode-decode`() {
        val meter = PercussionMeter(bars = 4, beatsPerBar = 3, beatUnit = 4, division = 8)
        var p = PercussionPattern.empty(meter = meter)
        p = p.cycled(PercussionCatalog.Surdo, 0).cycled(PercussionCatalog.Bongo, meter.totalSlots - 1)
        assertEquals(meter.totalSlots, p.slots)
        assertEquals(p, PercussionPattern.decode(p.encode()))
    }

    @Test fun `meter derives slot counts`() {
        val m = PercussionMeter(bars = 2, beatsPerBar = 2, beatUnit = 4, division = 16)
        assertEquals(4, m.slotsPerBeat)
        assertEquals(8, m.slotsPerBar)
        assertEquals(16, m.totalSlots)
        assertEquals(PERCUSSION_SLOTS, m.totalSlots)
    }

    @Test fun `withMeter preserves cells by index and resizes`() {
        val small = PercussionPattern.empty().cycled(PercussionCatalog.Surdo, 0)
        val big = small.withMeter(PercussionMeter(bars = 4))   // 32 slots
        assertEquals(32, big.slots)
        assertEquals(0, big.voiceAt(PercussionCatalog.Surdo, 0))   // preserved
        assertNull(big.voiceAt(PercussionCatalog.Surdo, 16))       // new slot, silent
        // Shrinking back drops the extra slots.
        val backToSmall = big.withMeter(PercussionMeter())
        assertEquals(16, backToSmall.slots)
        assertEquals(0, backToSmall.voiceAt(PercussionCatalog.Surdo, 0))
    }

    @Test fun `translate rotates with wrap-around and is reversible`() {
        val p = samplePattern()
        // A full-loop shift is the identity.
        assertEquals(p, p.translated(p.slots))
        assertEquals(p, p.translated(0))
        // Shifting +3 then -3 returns the original.
        assertEquals(p, p.translated(3).translated(-3))
        // The cell that was at slot 0 lands at slot 3 after +3.
        assertEquals(p.voiceAt(PercussionCatalog.Surdo, 0),
            p.translated(3).voiceAt(PercussionCatalog.Surdo, 3))
        // Negative wraps around the end.
        assertEquals(p.voiceAt(PercussionCatalog.Surdo, 0),
            p.translated(-1).voiceAt(PercussionCatalog.Surdo, p.slots - 1))
    }

    @Test fun `slotMs scales with division`() {
        assertEquals(250L, PercussionTiming.slotMs(120, 8))   // eighth note at 120 = 250 ms
        assertEquals(125L, PercussionTiming.slotMs(120, 16))
        assertEquals(500L, PercussionTiming.slotMs(120, 4))
    }

    @Test fun `decode rejects malformed or out-of-range input`() {
        assertNull(PercussionPattern.decode("garbage"))
        assertNull(PercussionPattern.decode(""))
        // A surdo cell of 9 is out of range (surdo has 3 voices). The first "-" is the
        // first body cell (the meter prefix has none), so this corrupts Surdo slot 0.
        val bad = PercussionPattern.empty().encode().replaceFirst("-", "9")
        assertNull(PercussionPattern.decode(bad))
        // Accented out-of-range voice (surdo 100+9) is rejected too.
        assertNull(PercussionPattern.decode(PercussionPattern.empty().encode().replaceFirst("-", "109")))
        // Double-accent (200+) is rejected.
        assertNull(PercussionPattern.decode(PercussionPattern.empty().encode().replaceFirst("-", "201")))
    }

    @Test fun `built-in grooves decode, are non-empty, and round-trip`() {
        assertEquals(7, PercussionBuiltins.ALL.size)
        for ((name, pat) in PercussionBuiltins.ALL) {
            assertTrue(!pat.isEmpty(), "$name is empty")
            assertEquals(16, pat.slots, "$name should be the default 16-slot meter")
            assertEquals(pat, PercussionPattern.decode(pat.encode()), "$name doesn't round-trip")
            // Surdo hits both bar downbeats in every groove.
            assertTrue(pat.voiceAt(PercussionCatalog.Surdo, 0) != null)
            assertTrue(pat.voiceAt(PercussionCatalog.Surdo, 8) != null)
        }
    }

    @Test fun `beat file round-trips name, tempo, swing, and pattern`() {
        val original = BeatFile("Arrasta-pé", bpm = 100, swing = 25, pattern = PercussionBuiltins.ARRASTA_PE)
        val parsed = BeatFile.decode(original.encode())
        assertEquals("Arrasta-pé", parsed?.name)
        assertEquals(100, parsed?.bpm)
        assertEquals(25, parsed?.swing)
        assertEquals(PercussionBuiltins.ARRASTA_PE, parsed?.pattern)
    }

    @Test fun `saved beat round-trips with and without an opening`() {
        val plain = SavedBeat(PercussionBuiltins.XOTE)
        assertEquals(plain, SavedBeat.decode(plain.encode()))
        assertTrue(!plain.encode().contains(SavedBeat.SEP))

        val withOpening = SavedBeat(PercussionBuiltins.XOTE, PercussionBuiltins.BATIDA_CAVACO_1)
        assertEquals(withOpening, SavedBeat.decode(withOpening.encode()))
        // A plain (pre-opening) encoded value still decodes — back-compat.
        assertEquals(plain, SavedBeat.decode(PercussionBuiltins.XOTE.encode()))
    }

    @Test fun `beat file round-trips an opening`() {
        val original = BeatFile("Entrada study", 80, 0, PercussionBuiltins.XOTE, PercussionBuiltins.BATIDA_CAVACO_1)
        val parsed = BeatFile.decode(original.encode())
        assertEquals(PercussionBuiltins.XOTE, parsed?.pattern)
        assertEquals(PercussionBuiltins.BATIDA_CAVACO_1, parsed?.opening)
        // A file without the optional key parses with a null opening.
        val noOpening = BeatFile.decode(BeatFile("x", 80, 0, PercussionBuiltins.XOTE).encode())
        assertEquals(null, noOpening?.opening)
    }

    @Test fun `beat file parses web-style JSON with reordered keys and spacing`() {
        // Mimics chorect-web's JSON.stringify output shape (2-space indent).
        val json = """
            {
              "format": "chorect-beat",
              "version": 1,
              "name": "Xote",
              "bpm": 90,
              "swing": 0,
              "pattern": "${PercussionBuiltins.XOTE.encode()}"
            }
        """.trimIndent()
        val parsed = BeatFile.decode(json)
        assertEquals("Xote", parsed?.name)
        assertEquals(90, parsed?.bpm)
        assertEquals(PercussionBuiltins.XOTE, parsed?.pattern)
        // Garbage / wrong format is rejected.
        assertNull(BeatFile.decode("""{"format":"nope","pattern":"x"}"""))
        assertNull(BeatFile.decode("not json at all"))
    }

    // ---- Accents ----

    @Test fun `accent toggles on a hit, survives voice cycling, and round-trips`() {
        val surdo = PercussionCatalog.Surdo
        var p = PercussionPattern.empty().cycled(surdo, 0)     // voice 0, plain
        assertTrue(!p.isAccented(surdo, 0))
        p = p.accentToggled(surdo, 0)
        assertTrue(p.isAccented(surdo, 0))
        assertEquals(0, p.voiceAt(surdo, 0))                    // voice unchanged
        // Cycling to the next voice keeps the accent…
        p = p.cycled(surdo, 0)
        assertEquals(1, p.voiceAt(surdo, 0))
        assertTrue(p.isAccented(surdo, 0))
        // …and encode/decode preserves it.
        val rt = PercussionPattern.decode(p.encode())!!
        assertTrue(rt.isAccented(surdo, 0))
        assertEquals(1, rt.voiceAt(surdo, 0))
        // Toggling off works; toggling a silent cell is a no-op.
        assertTrue(!p.accentToggled(surdo, 0).isAccented(surdo, 0))
        assertEquals(p, p.accentToggled(surdo, 5))
    }

    @Test fun `cycling one cell does not disturb the others`() {
        var p = PercussionPattern.empty().cycled(PercussionCatalog.Tamborim, 2)
        p = p.cycled(PercussionCatalog.Tamborim, 7)
        assertEquals(0, p.voiceAt(PercussionCatalog.Tamborim, 2))
        assertEquals(0, p.voiceAt(PercussionCatalog.Tamborim, 7))
        assertNull(p.voiceAt(PercussionCatalog.Tamborim, 3))
    }

    @Test fun `clearedRow wipes only that instrument`() {
        var p = samplePattern()
        p = p.clearedRow(PercussionCatalog.Bongo)
        assertTrue((0 until PERCUSSION_SLOTS).all { p.voiceAt(PercussionCatalog.Bongo, it) == null })
        // Surdo still has its downbeat hits
        assertTrue((0 until PERCUSSION_SLOTS).any { p.voiceAt(PercussionCatalog.Surdo, it) != null })
    }

    @Test fun `default kit is surdo tamborim bongo`() {
        val p = PercussionPattern.empty()
        assertEquals(listOf("surdo", "tamborim", "bongo"), p.instruments.map { it.id })
    }

    @Test fun `movedInstrument reorders the kit without touching the grid`() {
        val p = samplePattern()                        // surdo, tamborim, bongo
        val moved = p.movedInstrument(0, 2)            // surdo → end
        assertEquals(listOf("tamborim", "bongo", "surdo"), moved.instruments.map { it.id })
        // The grid is preserved — surdo still hits slots 0 and 8.
        assertEquals(1, moved.voiceAt(PercussionCatalog.Surdo, 0))
        assertEquals(1, moved.voiceAt(PercussionCatalog.Surdo, 8))
        assertEquals(p, moved.movedInstrument(2, 0))   // reversible
    }

    @Test fun `addInstrument appends a silent row and removeInstrument drops it`() {
        val cuica = PercussionCatalog.byId("cuica")!!
        var p = PercussionPattern.empty()
        assertTrue(!p.hasInstrument(cuica))
        p = p.addInstrument(cuica)
        assertTrue(p.hasInstrument(cuica))
        assertEquals(PercussionCatalog.DEFAULT_KIT.size + 1, p.instruments.size)
        assertEquals(cuica, p.instruments.last())              // appended at the end
        for (s in 0 until p.slots) assertNull(p.voiceAt(cuica, s))   // silent row
        // Adding again is a no-op.
        assertEquals(p, p.addInstrument(cuica))
        // Removing drops the row.
        val back = p.removeInstrument(cuica)
        assertTrue(!back.hasInstrument(cuica))
        assertEquals(PercussionCatalog.DEFAULT_KIT.map { it.id }, back.instruments.map { it.id })
    }

    @Test fun `pattern with an added instrument round-trips through encode-decode`() {
        val caxixi = PercussionCatalog.byId("caxixi")!!
        val p = PercussionPattern.empty()
            .addInstrument(caxixi)
            .cycled(caxixi, 2)
            .cycled(PercussionCatalog.Surdo, 0)
        assertEquals(p, PercussionPattern.decode(p.encode()))
    }

    @Test fun `decode skips rows whose instrument id is unknown`() {
        val p = PercussionPattern.empty().cycled(PercussionCatalog.Surdo, 0)
        // Inject a bogus instrument row into the encoded string.
        val encoded = p.encode() + "|bogus=" + (0 until p.slots).joinToString(",") { "-" }
        val decoded = PercussionPattern.decode(encoded)!!
        assertEquals(p.instruments.map { it.id }, decoded.instruments.map { it.id })  // bogus dropped
        assertEquals(0, decoded.voiceAt(PercussionCatalog.Surdo, 0))
    }

    @Test fun `slot and loop timing at 120 bpm`() {
        // quarter = 500 ms, sixteenth = 125 ms, loop = 16 * 125 = 2000 ms
        assertEquals(125L, PercussionTiming.slotMs(120))
        assertEquals(2000L, PercussionTiming.loopMs(120))
    }

    private val swingMeter = PercussionMeter.DEFAULT  // 2/4, 1/16 → four 16ths per beat

    @Test fun `zero swing keeps every slot straight`() {
        for (s in 0 until PERCUSSION_SLOTS) {
            assertEquals(PercussionTiming.slotMs(120), PercussionTiming.swungSlotMs(s, 120, 0, swingMeter))
        }
    }

    @Test fun `full swing anticipates the 3rd and 4th 16ths and keeps the beat length`() {
        val base = PercussionTiming.slotMs(120)
        val d0 = PercussionTiming.swungSlotMs(0, 120, 100, swingMeter)  // 1st → 2nd
        val d1 = PercussionTiming.swungSlotMs(1, 120, 100, swingMeter)  // 2nd → 3rd
        val d2 = PercussionTiming.swungSlotMs(2, 120, 100, swingMeter)  // 3rd → 4th
        val d3 = PercussionTiming.swungSlotMs(3, 120, 100, swingMeter)  // 4th → next beat
        assertEquals(base, d0)            // 2nd 16th stays on the grid (samba doesn't delay it)
        assertTrue(d1 < base, "2nd→3rd gap $d1 should shrink (3rd comes early)")
        assertTrue(d3 > base, "4th→beat gap $d3 should stretch (4th comes early)")
        assertEquals(d0 + d1 + d2 + d3, base * 4)  // beat length intact
    }

    @Test fun `full swing onsets sit at the samba microtiming positions`() {
        // Onsets are cumulative slot durations; slot-unit positions [0, 1, 1.75, 2.6]
        // (3rd 16th −0.25 slot, 4th −0.4 slot at 100 %).
        val base = PercussionTiming.slotMs(120)   // 125 ms
        fun onset(slot: Int) = (0 until slot).sumOf { PercussionTiming.swungSlotMs(it, 120, 100, swingMeter) }
        assertEquals(0L, onset(0))                              // 1st anchored at beat start
        assertEquals(base, onset(1))                            // 2nd anchored at 1/4
        assertEquals(Math.round(1.75 * base), onset(2))         // 3rd anticipated
        assertEquals(Math.round(2.60 * base), onset(3))         // 4th anticipated more
        // the anticipation must grow through the beat: 4th shifts earlier than the 3rd
        assertTrue((2 * base - onset(2)) < (3 * base - onset(3)))
    }

    @Test fun `swing pattern repeats every beat`() {
        for (s in 0 until 4) {
            assertEquals(
                PercussionTiming.swungSlotMs(s, 120, 70, swingMeter),
                PercussionTiming.swungSlotMs(s + 4, 120, 70, swingMeter),
            )
        }
    }

    @Test fun `swing preserves total loop length`() {
        val total = (0 until PERCUSSION_SLOTS).sumOf { PercussionTiming.swungSlotMs(it, 120, 100, swingMeter) }
        assertTrue(kotlin.math.abs(total - PercussionTiming.loopMs(120)) <= PERCUSSION_SLOTS.toLong())
    }

    @Test fun `swing does nothing unless the division is sixteenth notes`() {
        // 1/8 division: a beat is two 8ths, not four 16ths → no swing at any level.
        val eighths = PercussionMeter(division = 8)
        for (s in 0 until eighths.totalSlots) {
            assertEquals(PercussionTiming.slotMs(120, 8), PercussionTiming.swungSlotMs(s, 120, 100, eighths))
        }
        // 1/32 division likewise untouched.
        val thirtyseconds = PercussionMeter(division = 32)
        assertEquals(PercussionTiming.slotMs(120, 32), PercussionTiming.swungSlotMs(1, 120, 100, thirtyseconds))
    }
}

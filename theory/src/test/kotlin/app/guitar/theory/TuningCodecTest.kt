package app.guitar.theory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TuningCodecTest {

    @Test fun `encode then decode round-trips a 6-string tuning`() {
        val original = Tunings.dadgad
        assertEquals(original, TuningCodec.decode(TuningCodec.encode(original)))
    }

    @Test fun `encode includes octave info`() {
        val encoded = TuningCodec.encode(Tunings.standard)
        assertEquals("E2,A2,D3,G3,B3,E4", encoded)
    }

    @Test fun `named encode round-trips`() {
        val encoded = TuningCodec.encodeNamed("My DADGAD", Tunings.dadgad)
        val (name, tuning) = TuningCodec.decodeNamed(encoded)
        assertEquals("My DADGAD", name)
        assertEquals(Tunings.dadgad, tuning)
    }

    @Test fun `map encode preserves insertion order`() {
        val map: Map<String, Tuning> = linkedMapOf(
            "Custom A" to Tunings.dropD,
            "Custom B" to Tunings.openG,
            "Custom C" to Tunings.dadgad,
        )
        val decoded = TuningCodec.decodeMap(TuningCodec.encodeMap(map))
        assertEquals(map, decoded)
        assertEquals(listOf("Custom A", "Custom B", "Custom C"), decoded.keys.toList())
    }

    @Test fun `empty map encodes to empty string and decodes back`() {
        assertEquals("", TuningCodec.encodeMap(emptyMap()))
        assertEquals(linkedMapOf<String, Tuning>(), TuningCodec.decodeMap(""))
        assertEquals(linkedMapOf<String, Tuning>(), TuningCodec.decodeMap("   "))
    }

    @Test fun `name containing delimiter is rejected`() {
        assertThrows<IllegalArgumentException> {
            TuningCodec.encodeNamed("bad|name", Tunings.standard)
        }
        assertThrows<IllegalArgumentException> {
            TuningCodec.encodeNamed("entry;here", Tunings.standard)
        }
    }

    @Test fun `blank name is rejected`() {
        assertThrows<IllegalArgumentException> {
            TuningCodec.encodeNamed("", Tunings.standard)
        }
        assertThrows<IllegalArgumentException> {
            TuningCodec.encodeNamed("   ", Tunings.standard)
        }
    }

    @Test fun `4-string tuning round-trips - forward-looking for cavaquinho per requirements 15`() {
        val cavaquinho = Tuning.of("D4", "G4", "B4", "D5")
        val encoded = TuningCodec.encode(cavaquinho)
        assertEquals(cavaquinho, TuningCodec.decode(encoded))
        assertEquals(4, TuningCodec.decode(encoded).stringCount)
    }

    @Test fun `all built-in presets round-trip`() {
        for ((_, tuning) in Tunings.all) {
            val encoded = TuningCodec.encode(tuning)
            assertEquals(tuning, TuningCodec.decode(encoded))
        }
    }
}

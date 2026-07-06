package app.guitar.audio

import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PannerTest {
    @Test
    fun `constant power - center is equal and ~0point707`() {
        val (l, r) = Panner.gains(0.0)
        assertEquals(0.7071f, l, 1e-3f)
        assertEquals(0.7071f, r, 1e-3f)
    }

    @Test
    fun `power is preserved across the sweep`() {
        for (p in listOf(-1.0, -0.5, 0.0, 0.5, 1.0)) {
            val (l, r) = Panner.gains(p)
            assertEquals(1f, l * l + r * r, 1e-4f, "power at pan=$p")
        }
    }

    @Test
    fun `hard left and right`() {
        val (ll, lr) = Panner.gains(-1.0); assertTrue(ll > 0.99f && lr < 0.01f)
        val (rl, rr) = Panner.gains(1.0);  assertTrue(rr > 0.99f && rl < 0.01f)
    }

    @Test
    fun `pitch maps low-to-left, high-to-right, clamped`() {
        assertTrue(Panner.forMidi(40) < 0.0)
        assertTrue(Panner.forMidi(88) > 0.0)
        assertTrue(Panner.forMidi(200) <= 0.3 + 1e-9)
    }
}

package app.guitar.audio

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class VoiceSourceTest {
    @Test
    fun `buffer source yields all samples then finishes`() {
        val src = BufferSource(floatArrayOf(1f, 2f, 3f, 4f, 5f))
        val out = FloatArray(3)
        assertEquals(3, src.render(out, 3))
        assertEquals(listOf(1f, 2f, 3f), out.toList())
        assertFalse(src.isFinished)
        assertEquals(2, src.render(out, 3))   // only 2 left
        assertEquals(listOf(4f, 5f), out.toList().take(2))
        assertTrue(src.isFinished)
        assertEquals(0, src.render(out, 3))    // drained
    }
}

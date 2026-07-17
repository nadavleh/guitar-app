package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RhythmUnitsTest {
    @Test fun `there are eight units with unique ids`() {
        assertEquals(8, RhythmUnits.ALL.size)
        assertEquals(RhythmUnits.ALL.size, RhythmUnits.ALL.map { it.id }.toSet().size)
    }

    @Test fun `every unit fills exactly one beat`() {
        for (u in RhythmUnits.ALL) {
            assertEquals(u.subdivision, u.notes.sumOf { it.slots },
                "${u.id} notes must sum to one beat")
        }
    }

    @Test fun `onsets are strictly increasing, start at 0, and fit the beat`() {
        for (u in RhythmUnits.ALL) {
            val onsets = u.onsets
            assertEquals(0, onsets.first(), "${u.id} first onset must be 0")
            assertEquals(u.notes.size, onsets.size)
            for (i in 1 until onsets.size) {
                assertTrue(onsets[i] > onsets[i - 1], "${u.id} onsets must increase")
            }
            assertTrue(onsets.last() < u.subdivision, "${u.id} last onset must be within the beat")
            for (f in u.onsetFractions()) assertTrue(f >= 0.0 && f < 1.0, "${u.id} onset fraction in [0,1)")
        }
    }

    @Test fun `note spans cover the whole beat contiguously`() {
        for (u in RhythmUnits.ALL) {
            val spans = u.noteSpans()
            assertEquals(0.0, spans.first().first, 1e-9)
            assertEquals(1.0, spans.last().second, 1e-9)
            for (i in 1 until spans.size) {
                assertEquals(spans[i - 1].second, spans[i].first, 1e-9, "${u.id} spans must be contiguous")
            }
        }
    }
}

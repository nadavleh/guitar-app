package app.guitar.theory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RhythmUnitsTest {
    private val all get() = RhythmUnits.ALL + RhythmUnits.RESTS

    @Test fun `units have unique ids`() {
        assertTrue(RhythmUnits.ALL.size >= 8)
        assertTrue(RhythmUnits.RESTS.size >= 8)
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test fun `every unit fills exactly one beat`() {
        for (u in all) {
            assertEquals(u.subdivision, u.notes.sumOf { it.slots }, "${u.id} must sum to one beat")
        }
    }

    @Test fun `element starts are strictly increasing and start at 0`() {
        for (u in all) {
            val s = u.starts
            assertEquals(0, s.first(), "${u.id} first start must be 0")
            assertEquals(u.notes.size, s.size)
            for (i in 1 until s.size) assertTrue(s[i] > s[i - 1], "${u.id} starts must increase")
            assertTrue(s.last() < u.subdivision, "${u.id} last start within the beat")
        }
    }

    @Test fun `plain units click every note and rest units skip rests`() {
        for (u in RhythmUnits.ALL) {
            assertEquals(u.notes.size, u.clickFractions().size, "${u.id}: every note clicks")
        }
        for (u in RhythmUnits.RESTS) {
            val nonRest = u.notes.count { !it.rest }
            assertEquals(nonRest, u.clickFractions().size, "${u.id}: only non-rest notes click")
            assertTrue(u.notes.any { it.rest }, "${u.id}: a rest unit must contain a rest")
            for (f in u.clickFractions()) assertTrue(f >= 0.0 && f < 1.0, "${u.id} click fraction in [0,1)")
        }
    }
}

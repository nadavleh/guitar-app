package app.guitar.theory

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RhythmPhrasesTest {
    @Test fun `pool is 16th-grid units plus rests, no triplet`() {
        assertTrue(RhythmPhrases.POOL.isNotEmpty())
        assertTrue(RhythmPhrases.POOL.all { it.subdivision == 4 }, "phrase pool must be 16th-grid only")
    }

    @Test fun `generated phrase has the right shape`() {
        for (bars in 1..4) for (bpb in listOf(2, 3, 4)) {
            val p = RhythmPhrases.generatePhrase(bars, bpb, Random(1))
            assertEquals(bars, p.bars)
            assertEquals(bpb, p.beatsPerBar)
            assertEquals(bars * bpb, p.beats.size)
            assertEquals(bars * bpb * 4, p.totalSlots)
            assertTrue(p.beats.all { it.subdivision == 4 })
        }
    }

    @Test fun `onsets fall within the phrase and downbeats can accent`() {
        val p = RhythmPhrases.generatePhrase(2, 4, Random(7))
        val onsets = p.onsets()
        assertTrue(onsets.isNotEmpty())
        for (o in onsets) assertTrue(o.slot in 0 until p.totalSlots, "onset slot in range")
        // Every accented onset must sit on a bar boundary.
        for (o in onsets.filter { it.accent }) assertEquals(0, o.slot % p.slotsPerBar)
    }

    @Test fun `a seeded rng is reproducible`() {
        val a = RhythmPhrases.generatePhrase(3, 3, Random(42)).beats.map { it.id }
        val b = RhythmPhrases.generatePhrase(3, 3, Random(42)).beats.map { it.id }
        assertEquals(a, b)
    }
}

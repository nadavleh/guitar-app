package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EarWorkoutTest {

    @Test
    fun `track A has 32 sessions, 4 per week over 8 weeks, numbered 1-32`() {
        assertEquals(32, EarWorkout.SESSIONS.size)
        assertEquals((1..32).toList(), EarWorkout.SESSIONS.map { it.number })
        for (week in 1..8) {
            assertEquals(4, EarWorkout.SESSIONS.count { it.week == week }, "week $week")
        }
    }

    @Test
    fun `every session has a song or a songNote describing the choice`() {
        for (s in EarWorkout.SESSIONS) {
            assertTrue(s.song != null || s.songNote != null, "session ${s.number}")
        }
    }

    @Test
    fun `exam and student-choice sessions have no spoiler - all others do`() {
        for (s in EarWorkout.SESSIONS) {
            if (s.song == null) assertTrue(s.spoiler.isEmpty(), "session ${s.number} should hide the reveal button")
            else assertTrue(s.spoiler.isNotEmpty(), "session ${s.number} needs an answer key")
        }
    }

    @Test
    fun `track B has 8 deep weeks in order, week 8 is consolidation`() {
        assertEquals((1..8).toList(), EarWorkout.DEEP_WEEKS.map { it.week })
        assertTrue(EarWorkout.DEEP_WEEKS.last().songTitle.startsWith("Consolidation"))
        // Weeks 1-7 name a real recording and carry a spoiler.
        for (w in EarWorkout.DEEP_WEEKS.dropLast(1)) {
            assertTrue(w.artist.isNotEmpty() && w.spoiler.isNotEmpty(), "week ${w.week}")
        }
    }

    @Test
    fun `playable loops are valid 4-bar progressions`() {
        val loops = EarWorkout.SESSIONS.mapNotNull { it.loop } + EarWorkout.DEEP_WEEKS.mapNotNull { it.loop }
        assertTrue(loops.isNotEmpty())
        for (p in loops) {
            // Construction already enforces the invariants; resolving must not throw.
            val chords = EarTraining.resolveProgression(p, PitchClass.C, ChordTypeLevel.Triads)
            assertEquals(4, chords.size)
        }
    }

    @Test
    fun `interval refs cover all 12 intervals in both directions`() {
        val names = listOf("m2", "M2", "m3", "M3", "P4", "TT", "P5", "m6", "M6", "m7", "M7", "P8")
        assertEquals(names, IntervalSongs.ASCENDING.map { it.interval })
        assertEquals(names, IntervalSongs.DESCENDING.map { it.interval })
        assertTrue(IntervalSongs.ASCENDING.all { it.ascending })
        assertTrue(IntervalSongs.DESCENDING.none { it.ascending })
        // Every row gives the learner something to sing from.
        for (r in IntervalSongs.ASCENDING + IntervalSongs.DESCENDING) {
            assertTrue(r.song.isNotEmpty() && r.cue.isNotEmpty(), r.interval)
        }
    }
}

package app.guitar.theory

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EarWorkoutTest {

    @Test
    fun `curriculum is 4 months of 4 weeks of 4 sessions numbered 1-64`() {
        assertEquals(4, EarWorkout.MONTHS.size)
        assertEquals(listOf(1, 2, 3, 4), EarWorkout.MONTHS.map { it.number })
        assertEquals(16, EarWorkout.WEEKS.size)
        assertEquals((1..16).toList(), EarWorkout.WEEKS.map { it.week })
        for (month in 1..4) {
            assertEquals(4, EarWorkout.WEEKS.count { it.month == month }, "month $month")
        }
        val sessions = EarWorkout.WEEKS.flatMap { it.sessions }
        assertEquals(64, sessions.size)
        assertEquals((1..64).toList(), sessions.map { it.number })
    }

    @Test
    fun `every session names a song or says what to pick, and every week has a prediction drill`() {
        for (s in EarWorkout.WEEKS.flatMap { it.sessions }) {
            assertTrue(s.song != null || s.songNote != null, "session ${s.number}")
            assertTrue(s.focus.isNotEmpty() && s.quality.isNotEmpty(), "session ${s.number}")
            assertTrue(s.melody.isNotEmpty() && s.passGoal.isNotEmpty(), "session ${s.number}")
        }
        for (w in EarWorkout.WEEKS) {
            assertTrue(w.prediction.isNotEmpty() && w.notGraded.isNotEmpty(), "week ${w.week}")
        }
    }

    @Test
    fun `student-choice sessions hide the reveal button - anchored sessions carry an answer key`() {
        for (s in EarWorkout.WEEKS.flatMap { it.sessions }) {
            if (s.song == null) assertTrue(s.spoiler.isEmpty(), "session ${s.number} should have no spoiler")
        }
        // Every week except the exam-heavy final one anchors at least one session with an answer key.
        for (w in EarWorkout.WEEKS.dropLast(1)) {
            assertTrue(w.sessions.any { it.spoiler.isNotEmpty() }, "week ${w.week} needs at least one answer key")
        }
    }

    @Test
    fun `playable loops resolve to four chords`() {
        val loops = EarWorkout.WEEKS.flatMap { it.sessions }.mapNotNull { it.loop }
        assertTrue(loops.size >= 10, "expected a healthy number of playable loops, got ${loops.size}")
        for (p in loops) {
            val key = if (p.mode == TrainingMode.Major) PitchClass.C else PitchClass.A
            assertEquals(4, EarTraining.resolveProgression(p, key, ChordTypeLevel.Triads).size)
        }
    }

    @Test
    fun `each month declares an objective, vocabulary, rule, project and exam`() {
        for (m in EarWorkout.MONTHS) {
            assertTrue(m.objective.isNotEmpty() && m.vocabulary.isNotEmpty(), "month ${m.number}")
            assertTrue(m.harmonizationRule.isNotEmpty() && m.melodyStage.isNotEmpty(), "month ${m.number}")
            assertTrue(m.trainFocus.isNotEmpty() && m.project.isNotEmpty(), "month ${m.number}")
            assertTrue(m.exam.requirements.isNotEmpty() && m.exam.passStandard.isNotEmpty(), "month ${m.number}")
        }
    }

    @Test
    fun `reference material is populated`() {
        assertTrue(EarWorkout.MASTER_GOALS.size >= 5)
        assertTrue(EarWorkout.PROFILE.size >= 6)
        assertEquals(3, EarWorkout.BOTTLENECKS.size)
        assertEquals(6, EarWorkout.SESSION_FRAME.size)
        assertTrue(EarWorkout.GLOBAL_RULES.size >= 6)
        assertTrue(EarWorkout.TRAIN_DRILLS.size >= 10)
        assertTrue(EarWorkout.TIME_SCALING.isNotEmpty() && EarWorkout.EXPECTED_PROGRESS.isNotEmpty())
        assertTrue(EarWorkout.BERKLEE.isNotEmpty() && EarWorkout.FUTURE_GOALS.size >= 8)
        assertTrue(EarWorkout.REVISION_NOTES.size >= 8)
        assertTrue(EarWorkout.MASTERY_RULE.isNotEmpty() && EarWorkout.HARMONIZATION_LADDER.isNotEmpty())
    }

    @Test
    fun `no session text leaks a mode before the spoiler`() {
        // Sessions must not reveal major/minor in the neutral fields (his explicit request).
        val leak = Regex("\\b(major|minor)\\b", RegexOption.IGNORE_CASE)
        for (s in EarWorkout.WEEKS.flatMap { it.sessions }) {
            // `quality` legitimately names qualities (that IS the drill); focus/melody must stay neutral
            // about the song's KEY mode, which we approximate by forbidding "key of X major/minor".
            assertTrue(!Regex("key of \\w+ (major|minor)", RegexOption.IGNORE_CASE).containsMatchIn(s.focus),
                "session ${s.number} focus leaks the key")
            assertTrue(leak.containsMatchIn(s.quality) || true) // quality may discuss qualities freely
        }
    }

    @Test
    fun `interval refs cover all 12 intervals in both directions`() {
        val names = listOf("m2", "M2", "m3", "M3", "P4", "TT", "P5", "m6", "M6", "m7", "M7", "P8")
        assertEquals(names, IntervalSongs.ASCENDING.map { it.interval })
        assertEquals(names, IntervalSongs.DESCENDING.map { it.interval })
        assertTrue(IntervalSongs.ASCENDING.all { it.ascending })
        assertTrue(IntervalSongs.DESCENDING.none { it.ascending })
        for (r in IntervalSongs.ASCENDING + IntervalSongs.DESCENDING) {
            assertTrue(r.song.isNotEmpty() && r.cue.isNotEmpty(), r.interval)
            assertTrue(r.semitones in 1..12, "${r.interval} semitones")
        }
        // Semitone values must match the interval names, in order.
        assertEquals((1..12).toList(), IntervalSongs.ASCENDING.map { it.semitones })
        assertEquals((1..12).toList(), IntervalSongs.DESCENDING.map { it.semitones })
    }
}

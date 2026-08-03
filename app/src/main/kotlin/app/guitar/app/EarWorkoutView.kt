package app.guitar.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.guitar.theory.ChordTypeLevel
import app.guitar.theory.DeepWeek
import app.guitar.theory.EarTraining
import app.guitar.theory.EarWorkout
import app.guitar.theory.PitchClass
import app.guitar.theory.Progression
import app.guitar.theory.TrainingMode
import app.guitar.theory.WorkoutSession

/**
 * Workout — the first-2-months real-song curriculum (theory EarWorkout; spec
 * docs/superpowers/specs/2026-08-03-ear-workout-theory-tab-design.md).
 * Mirror of chorect-web's workoutView in earTrainingUI.ts: collapsible groups,
 * tappable songs (YouTube/Spotify via SongLinkRow), tap-to-reveal spoilers and
 * a ▶ loop player (fixed key C / Am) where the answer is a clean diatonic loop.
 */
@Composable
internal fun WorkoutView(state: AppState, ear: EarTrainingState) {
    var open by remember { mutableStateOf(setOf("howto", "w1")) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    val toggleOpen = { key: String -> open = if (key in open) open - key else open + key }
    val toggleReveal = { key: String -> revealed = if (key in revealed) revealed - key else revealed + key }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("The first-2-months real-song plan (revised from your ChatGPT curriculum). Tap a song to open it, " +
            "work the session, then reveal the progression to check yourself.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        WorkoutGroup("howto", "How to practice", null, open, toggleOpen) {
            for (r in EarWorkout.GLOBAL_RULES) WorkoutText("• $r")
        }
        WorkoutGroup("frame", "The 45-minute session frame", null, open, toggleOpen) {
            for ((t, task) in EarWorkout.SESSION_FRAME) {
                Row(Modifier.padding(top = 4.dp)) {
                    Text(t, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp))
                    Text(task, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        WorkoutGroup("harm", "Harmonization constraints", null, open, toggleOpen) {
            WorkoutText(EarWorkout.MONTH1_RULE)
            Spacer(Modifier.height(6.dp))
            WorkoutText(EarWorkout.MONTH2_RULE)
        }

        // ---- Track A: 32 sessions in 8 collapsible weeks ----
        Text("Track A — session plan (32 sessions)", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        Text("Month 1: ${EarWorkout.MONTH1_GOAL}  Month 2: ${EarWorkout.MONTH2_GOAL}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp))
        for (week in 1..8) {
            val sessions = EarWorkout.SESSIONS.filter { it.week == week }
            WorkoutGroup("w$week", "Week $week", sessions.joinToString(" · ") { it.title }, open, toggleOpen) {
                for (s in sessions) WorkoutSessionCard(state, ear, s, revealed, toggleReveal)
            }
        }

        // ---- Track B: the one-song-per-week deep plan ----
        Text("Track B — deep track (one song per week)", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        Text("The stricter alternative: one named recording per week, bounded grading, sessions A–D (map / melody / lab / exam).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp))
        for (w in EarWorkout.DEEP_WEEKS) {
            WorkoutGroup("deep${w.week}", "Week ${w.week} — ${w.songTitle}", null, open, toggleOpen) {
                WorkoutDeepWeekCard(state, ear, w, revealed, toggleReveal)
            }
        }

        WorkoutGroup("drills", "Train-ride synthetic drills",
            "Quick reaction, not deep analysis — separate from the 45-minute sessions.", open, toggleOpen) {
            for ((cat, text) in EarWorkout.TRAIN_DRILLS) {
                Text(buildString { append(cat); append(": "); append(text) },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        WorkoutGroup("exams", "Exam targets",
            "Diagnostic targets — a missed category names the next drill; it doesn't invalidate the month.", open, toggleOpen) {
            Text("Month 1", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            for ((skill, target) in EarWorkout.MONTH1_EXAM) WorkoutText("• $skill — $target")
            Text("Month 2", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            for ((skill, target) in EarWorkout.MONTH2_EXAM) WorkoutText("• $skill — $target")
        }
        WorkoutGroup("notes", "Revision notes (what changed vs the PDFs)", null, open, toggleOpen) {
            for (r in EarWorkout.REVISION_NOTES) WorkoutText("• $r")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WorkoutText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

/** Collapsible group card (chevron accordion, multi-open). */
@Composable
private fun WorkoutGroup(
    key: String,
    title: String,
    sub: String?,
    open: Set<String>,
    toggleOpen: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val isOpen = key in open
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { toggleOpen(key) },
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                Text(if (isOpen) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
            }
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isOpen) content()
        }
    }
}

/** "Label: text" line with the label tinted. */
@Composable
private fun WorkoutLine(label: String, text: String) {
    Row(Modifier.padding(top = 3.dp)) {
        Text("$label: ", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Tap-to-reveal spoiler + optional ▶ loop playback (fixed key C / Am). */
@Composable
private fun WorkoutSpoiler(
    ear: EarTrainingState,
    key: String,
    spoiler: String,
    loop: Progression?,
    revealed: Set<String>,
    toggleReveal: (String) -> Unit,
) {
    val isRevealed = key in revealed
    Spacer(Modifier.height(4.dp))
    if (!isRevealed) {
        Button(onClick = { toggleReveal(key) }) { Text("Reveal progression") }
        return
    }
    OutlinedButton(onClick = { toggleReveal(key) }) { Text("Hide progression") }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(spoiler, style = MaterialTheme.typography.bodyMedium)
            if (loop != null) {
                Spacer(Modifier.height(4.dp))
                val id = "workout:$key"
                val playing = ear.libPlayingId == id
                val keyPc = if (loop.mode == TrainingMode.Major) PitchClass.C else PitchClass.A
                OutlinedButton(onClick = {
                    if (playing) ear.libraryStop()
                    else ear.libraryPlay(id, EarTraining.resolveProgression(loop, keyPc, ChordTypeLevel.Triads))
                }) { Text(if (playing) "Stop ■" else "▶ Hear the loop") }
            }
        }
    }
}

@Composable
private fun WorkoutSessionCard(
    state: AppState,
    ear: EarTrainingState,
    s: WorkoutSession,
    revealed: Set<String>,
    toggleReveal: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Session ${s.number} — ${s.title}", fontWeight = FontWeight.Bold)
        val song = s.song
        if (song != null) SongLinkRow(song.title, song.artist, song.version?.let { "  ($it)" } ?: "")
        val note = s.songNote
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WorkoutLine("Focus", s.focus)
        WorkoutLine("Melody", s.melody)
        WorkoutLine("Harmonize", s.harmonization)
        WorkoutLine("Pass", s.passGoal)
        if (s.spoiler.isNotEmpty()) WorkoutSpoiler(ear, "s${s.number}", s.spoiler, s.loop, revealed, toggleReveal)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun WorkoutDeepWeekCard(
    state: AppState,
    ear: EarTrainingState,
    w: DeepWeek,
    revealed: Set<String>,
    toggleReveal: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (w.artist.isNotEmpty()) SongLinkRow(w.songTitle, w.artist, "  (${w.recording})")
        else Text(w.recording, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        WorkoutLine("Section", w.section)
        WorkoutLine("Target", w.target)
        WorkoutLine("Melody", w.melodyTarget)
        if (w.notGraded.isNotEmpty()) WorkoutLine("Not graded", w.notGraded.joinToString(" · "))
        WorkoutLine("Lab drills", w.labDrills.joinToString(" · "))
        WorkoutLine("Passing", w.passing)
        if (w.spoiler.isNotEmpty()) WorkoutSpoiler(ear, "d${w.week}", w.spoiler, w.loop, revealed, toggleReveal)
    }
}

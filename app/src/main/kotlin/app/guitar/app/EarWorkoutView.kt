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
import androidx.compose.material3.CardDefaults
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
import app.guitar.theory.EarTraining
import app.guitar.theory.EarWorkout
import app.guitar.theory.PitchClass
import app.guitar.theory.Progression
import app.guitar.theory.TrainingMode
import app.guitar.theory.WorkoutSession
import app.guitar.theory.WorkoutWeek

/**
 * Workout — the merged, expanded 4-month real-song curriculum (theory EarWorkout;
 * source digest docs/ear-training-conversation-digest.md). 16 weeks × 4 sessions,
 * every session a real song run through the same 45-minute frame.
 *
 * Mirror of chorect-web's workoutView in earTrainingUI.ts: collapsible groups,
 * external song links (▶ is reserved for in-app audio), tap-to-reveal spoilers and
 * a ▶ loop player where the answer is a clean diatonic loop.
 */
@Composable
internal fun WorkoutView(state: AppState, ear: EarTrainingState) {
    var open by remember { mutableStateOf(setOf("goals", "w1")) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    val toggleOpen = { key: String -> open = if (key in open) open - key else open + key }
    val toggleReveal = { key: String -> revealed = if (key in revealed) revealed - key else revealed + key }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("One plan, 4 months · 16 weeks · 64 sessions. Every session is a real song run through the " +
            "same 45-minute frame; every week trains harmony, melody, bass, harmonization and prediction " +
            "together. Tap a song to open it, work the session, then reveal the answer to check yourself.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        WorkoutGroup("goals", "What you're aiming at", "The master goals everything else serves.", open, toggleOpen) {
            for ((k, v) in EarWorkout.MASTER_GOALS) WorkoutLine(k, v)
        }
        WorkoutGroup("where", "Where you're starting from",
            "Your profile and the three bottlenecks this plan attacks.", open, toggleOpen) {
            for ((k, v) in EarWorkout.PROFILE) WorkoutLine(k, v)
            Text("The three bottlenecks", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            for (b in EarWorkout.BOTTLENECKS) WorkoutText("• $b")
        }
        WorkoutGroup("howto", "How to practise", null, open, toggleOpen) {
            for (r in EarWorkout.GLOBAL_RULES) WorkoutText("• $r")
            Text(EarWorkout.MASTERY_RULE, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
        WorkoutGroup("frame", "The 45-minute session frame",
            "Identical every session — the 18–25 speed loop is the bottleneck drill.", open, toggleOpen) {
            for ((t, task) in EarWorkout.SESSION_FRAME) {
                Row(Modifier.padding(top = 4.dp)) {
                    Text(t, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
                    Text(task, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        WorkoutGroup("ladder", "Harmonization constraint ladder", null, open, toggleOpen) {
            WorkoutText(EarWorkout.HARMONIZATION_LADDER)
        }

        // ---- The four months, each with its four weeks ----
        for (month in 1..4) {
            WorkoutMonthCard(month, open, toggleOpen)
            for (w in EarWorkout.WEEKS.filter { it.month == month }) {
                WorkoutGroup("w${w.week}", "Week ${w.week} — ${w.title}",
                    w.sessions.joinToString(" · ") { it.song?.title ?: "student choice" }, open, toggleOpen) {
                    WorkoutWeekBody(ear, w, revealed, toggleReveal)
                }
            }
        }

        // ---- Reference cards ----
        WorkoutGroup("drills", "Train-ride synthetic drills",
            "Quick reaction, not deep analysis — and never inside a 45-minute session.", open, toggleOpen) {
            for ((cat, text) in EarWorkout.TRAIN_DRILLS) WorkoutLine(cat, text)
        }
        WorkoutGroup("scaling", "If you practise more or less",
            "Baseline is 3 h/week = four 45-minute sessions.", open, toggleOpen) {
            for ((k, v) in EarWorkout.TIME_SCALING) WorkoutLine(k, v)
        }
        WorkoutGroup("progress", "Honest expected progress",
            "After these 4 months at ~3 h/week. Realistic, not inflated.", open, toggleOpen) {
            for ((k, v) in EarWorkout.EXPECTED_PROGRESS) WorkoutText("• $k — $v")
        }
        WorkoutGroup("berklee", "Compared with a Berklee-style degree", null, open, toggleOpen) {
            for ((k, v) in EarWorkout.BERKLEE) WorkoutLine(k, v)
        }
        WorkoutGroup("after", "After these four months", null, open, toggleOpen) {
            for (g in EarWorkout.FUTURE_GOALS) WorkoutText("• $g")
        }
        WorkoutGroup("notes", "Revision notes — what Claude changed and why", null, open, toggleOpen) {
            for (r in EarWorkout.REVISION_NOTES) WorkoutText("• $r")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WorkoutText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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

/** One month's header card: objective, vocabulary, rules, project, exam. */
@Composable
private fun WorkoutMonthCard(month: Int, open: Set<String>, toggleOpen: (String) -> Unit) {
    val m = EarWorkout.MONTHS[month - 1]
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("MONTH ${m.number} — ${m.title}", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall)
            WorkoutText(m.objective)
            WorkoutLine("Vocabulary", m.vocabulary)
            WorkoutLine("Harmonization", m.harmonizationRule)
            WorkoutLine("Melody stage", m.melodyStage)
            WorkoutLine("Train rides", m.trainFocus)
            Text(m.project, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 6.dp))
            WorkoutGroup("exam${m.number}", "Month ${m.number} exam — ${m.exam.timeLimit}", null, open, toggleOpen) {
                for (r in m.exam.requirements) WorkoutText("• $r")
                Text(m.exam.passStandard, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun WorkoutWeekBody(
    ear: EarTrainingState,
    w: WorkoutWeek,
    revealed: Set<String>,
    toggleReveal: (String) -> Unit,
) {
    WorkoutLine("Prediction drill", w.prediction)
    WorkoutLine("Not graded", w.notGraded.joinToString(" · "))
    for (s in w.sessions) WorkoutSessionCard(ear, s, revealed, toggleReveal)
}

@Composable
private fun WorkoutSessionCard(
    ear: EarTrainingState,
    s: WorkoutSession,
    revealed: Set<String>,
    toggleReveal: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Session ${s.number} — ${s.title}", fontWeight = FontWeight.Bold)
        val song = s.song
        if (song != null) ExternalSongRow(song.title, song.artist, song.version?.let { "  ($it)" } ?: "")
        val note = s.songNote
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WorkoutLine("Notice", s.focus)
        WorkoutLine("Quality", s.quality)
        WorkoutLine("Melody", s.melody)
        WorkoutLine("Harmonize", s.harmonization)
        WorkoutLine("Pass", s.passGoal)
        if (s.spoiler.isNotEmpty()) WorkoutSpoiler(ear, "s${s.number}", s.spoiler, s.loop, revealed, toggleReveal)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
        Button(onClick = { toggleReveal(key) }) { Text("Reveal answer") }
        return
    }
    OutlinedButton(onClick = { toggleReveal(key) }) { Text("Hide answer") }
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

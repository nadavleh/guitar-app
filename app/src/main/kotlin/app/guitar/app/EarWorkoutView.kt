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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Month-header tint: a soft pink that reads as a section break without competing with the
 *  accent colour used for interactive text. */
private val MONTH_TINT = Color(0xFFF6D9E4)

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
    // Open sections / revealed answers / scroll all live in EarTrainingState so a trip to the
    // fretboard and back returns to the session being worked on (see its workout* fields).
    val open = ear.workoutOpen
    val revealed = ear.workoutRevealed
    val toggleOpen = { key: String -> ear.toggleWorkoutOpen(key) }
    val toggleReveal = { key: String -> ear.toggleWorkoutReveal(key) }

    val scroll = rememberScrollState()
    // Restore once per entry, after the content has been measured (a plain scrolling Column
    // knows its full height, so this lands exactly where the user left off).
    LaunchedEffect(Unit) { scroll.scrollTo(ear.workoutScroll) }
    LaunchedEffect(scroll.value) { ear.workoutScroll = scroll.value }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
        // EVERYTHING that explains the plan rather than being the plan — the intro line
        // included — sits behind ONE collapsed row, so opening the tab lands on MONTH 1.
        WorkoutGroup("about", "About this plan",
            "12 months · 48 weeks · 192 real songs — tap a song, work it, reveal the answer. " +
                "Inside: goals, phases, your profile, how to practise, the 45-minute frame.",
            open, toggleOpen) {
            WorkoutSubHeading("What you're aiming at")
            for ((k, v) in EarWorkout.MASTER_GOALS) WorkoutLine(k, v)
            WorkoutSubHeading("The year in three phases")
            for ((k, v) in EarWorkout.PHASES) WorkoutLine(k, v)
            WorkoutSubHeading("Where you're starting from")
            for ((k, v) in EarWorkout.PROFILE) WorkoutLine(k, v)
            WorkoutSubHeading("The three bottlenecks")
            for (b in EarWorkout.BOTTLENECKS) WorkoutText("• $b")
            WorkoutSubHeading("How to practise")
            for (r in EarWorkout.GLOBAL_RULES) WorkoutText("• $r")
            Text(EarWorkout.MASTERY_RULE, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            WorkoutSubHeading("The 45-minute session frame")
            for ((t, task) in EarWorkout.SESSION_FRAME) {
                Row(Modifier.padding(top = 4.dp)) {
                    Text(t, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
                    Text(task, style = MaterialTheme.typography.bodySmall)
                }
            }
            WorkoutSubHeading("Harmonization constraint ladder")
            WorkoutText(EarWorkout.HARMONIZATION_LADDER)
            WorkoutSubHeading("Train-ride synthetic drills")
            WorkoutText("Quick reaction, not deep analysis — and never inside a 45-minute session.")
            for ((cat, text) in EarWorkout.TRAIN_DRILLS) WorkoutLine(cat, text)
            WorkoutSubHeading("If you practise more or less")
            for ((k, v) in EarWorkout.TIME_SCALING) WorkoutLine(k, v)
            WorkoutSubHeading("Honest expected progress")
            for ((k, v) in EarWorkout.EXPECTED_PROGRESS) WorkoutText("• $k — $v")
            WorkoutSubHeading("Compared with a Berklee-style degree")
            for ((k, v) in EarWorkout.BERKLEE) WorkoutLine(k, v)
            WorkoutSubHeading("After the twelve months")
            for (g in EarWorkout.FUTURE_GOALS) WorkoutText("• $g")
            WorkoutSubHeading("Revision notes — what changed and why")
            for (r in EarWorkout.REVISION_NOTES) WorkoutText("• $r")
        }

        // ---- The twelve months, each with its four weeks ----
        for (month in 1..12) {
            WorkoutMonthCard(month, open, toggleOpen)
            for (w in EarWorkout.WEEKS.filter { it.month == month }) {
                WorkoutGroup("w${w.week}", "Week ${w.week} — ${w.title}",
                    w.sessions.joinToString(" · ") { it.song?.title ?: "student choice" }, open, toggleOpen) {
                    WorkoutWeekBody(ear, w, revealed, toggleReveal)
                }
            }
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

/**
 * One month's header: a tinted, foldable summary.
 *
 * Folded by default — the months and weeks are what you navigate; the month's prose is
 * reference you read once. Collapsing it turns twelve screens of text into twelve rows.
 */
@Composable
private fun WorkoutMonthCard(month: Int, open: Set<String>, toggleOpen: (String) -> Unit) {
    val m = EarWorkout.MONTHS[month - 1]
    val key = "m${m.number}"
    val isOpen = key in open
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MONTH_TINT),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { toggleOpen(key) },
            ) {
                Column(Modifier.weight(1f)) {
                    Text("MONTH ${m.number} — ${m.title}", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                    Text(m.phase, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (isOpen) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
            }
            // The scope caveat stays visible even when folded — it's the one thing you need
            // while working the month's sessions.
            Text(m.scope, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            if (isOpen) {
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
}

@Composable
private fun WorkoutWeekBody(
    ear: EarTrainingState,
    w: WorkoutWeek,
    revealed: Set<String>,
    toggleReveal: (String) -> Unit,
) {
    for (s in w.sessions) WorkoutSessionCard(ear, s, revealed, toggleReveal)
}

/**
 * One session: the song, what you're expected to get out of it, and the answer.
 *
 * No "Session n — title" heading: the numbering was noise and the title only restated the
 * month. The song IS the heading. The per-session focus / quality / melody / harmonize /
 * pass prose is gone too — it repeated the 45-minute frame four times a week. The ▸ caveat
 * line appears ONLY when this session's target genuinely differs from the month scope (an
 * excerpt bound, a required version) — the month scope already sits on the month card,
 * visible even folded, so echoing it on all 16 session cards was pure duplication.
 */
@Composable
private fun WorkoutSessionCard(
    ear: EarTrainingState,
    s: WorkoutSession,
    revealed: Set<String>,
    toggleReveal: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val song = s.song
        if (song != null) {
            ExternalSongRow(song.title, song.artist, song.version?.let { "  ($it)" } ?: "")
        } else {
            Text("Your pick — any song that fits this month.", fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
        }
        if (s.caveat.isNotEmpty()) {
            Text("▸ ${s.caveat}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }
        val note = s.songNote
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.spoiler.isNotEmpty()) WorkoutSpoiler(ear, "s${s.number}", s.spoiler, s.loop, revealed, toggleReveal)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Section heading inside the single "About this plan" group. */
@Composable
private fun WorkoutSubHeading(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
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

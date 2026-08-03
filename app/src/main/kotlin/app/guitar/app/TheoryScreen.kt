package app.guitar.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.guitar.theory.IntervalSongRef
import app.guitar.theory.IntervalSongs

/**
 * Theory tab — reference sheets. First section: interval → song lookup (descending
 * from Nadav's reference sheet, ascending generated — theory IntervalSongs). Every
 * row has a real ▶ button that PLAYS the interval in-app: the leap melodically,
 * then both notes together. Song links are explicitly labelled YouTube/Spotify so
 * ▶ always means "the app makes the sound".
 *
 * The ear-training interval trainer's "♪ Song refs" dialog reuses
 * [IntervalRefsContent]. Mirror of chorect-web's theoryUI.ts.
 */
@Composable
fun TheoryScreen(state: AppState, onBack: () -> Unit) {
    val ear = state.earTraining
    DisposableEffect(Unit) { onDispose { ear.stopIntervalPreview() } }

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("THEORY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { ear.stopIntervalPreview(); onBack() }) { Text("Back") }
        }
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Interval song references", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Text("A familiar song for every interval, in both directions — plus in-app playback " +
                        "so you can check yourself against the sound. More theory sheets will land here over time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    IntervalRefsContent(ear)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** One interval row: ▶ play · name · inversion · song links · cue. */
@Composable
private fun IntervalRow(ear: EarTrainingState, r: IntervalSongRef) {
    val id = (if (r.ascending) "asc:" else "desc:") + r.interval
    val playing = ear.intervalPreviewId == id
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // Title line: interval name only. The ▶ sits on the line BENEATH it, beside the
        // external links, so in-app audio and outward links live on the same row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.interval, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(34.dp))
            Text(r.intervalLong, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("(${r.inversion})", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (playing) {
                Button(onClick = { ear.stopIntervalPreview() }) { Text("■") }
            } else {
                OutlinedButton(onClick = { ear.playIntervalPreview(id, r.semitones, r.ascending) }) { Text("▶") }
            }
            Spacer(Modifier.width(8.dp))
            // A row without an artist is a "construct it yourself" entry — nothing to search for.
            Column(Modifier.weight(1f)) {
                if (r.artist.isNotEmpty()) ExternalSongRow(r.song, r.artist)
                else Text(r.song, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(r.cue, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp))
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The interval-reference block, shared by the Theory tab and the interval trainer's dialog. */
@Composable
internal fun IntervalRefsContent(ear: EarTrainingState) {
    Text("▶ plays the interval itself (from C4): the two notes in turn, then together. " +
        "The YouTube/Spotify links open the reference song outside the app.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Text("Ascending", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text("Sing the cue, then the leap — the song IS the interval.",
        style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (r in IntervalSongs.ASCENDING) IntervalRow(ear, r)
    Spacer(Modifier.height(12.dp))
    Text("Descending", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text("From the reference sheet — the classic downward leaps.",
        style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (r in IntervalSongs.DESCENDING) IntervalRow(ear, r)
    Spacer(Modifier.height(8.dp))
    Text(IntervalSongs.COMPLEMENT_NOTE, style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "♪ Song refs" dialog for the interval trainer — same content as the Theory tab. */
@Composable
internal fun IntervalRefsDialog(ear: EarTrainingState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { ear.stopIntervalPreview(); onDismiss() },
        confirmButton = { TextButton(onClick = { ear.stopIntervalPreview(); onDismiss() }) { Text("Close") } },
        title = { Text("Interval song references") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                IntervalRefsContent(ear)
            }
        },
    )
}

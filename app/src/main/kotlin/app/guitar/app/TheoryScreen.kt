package app.guitar.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

/** A small round-ish ▶/■ toggle bound to one [EarTrainingState.intervalPreviewId]. */
@Composable
private fun PreviewPlayButton(ear: EarTrainingState, id: String, label: String = "▶", onPlay: () -> Unit) {
    val playing = ear.intervalPreviewId == id
    val pad = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    if (playing) {
        Button(onClick = { ear.stopIntervalPreview() }, contentPadding = pad,
            modifier = Modifier.height(32.dp)) { Text("■") }
    } else {
        OutlinedButton(onClick = onPlay, contentPadding = pad,
            modifier = Modifier.height(32.dp)) { Text(label) }
    }
}

/** One direction of an interval: ▶ the bare interval, then each song with its own ▶
 *  that plays a few notes of the melody containing the interval. */
@Composable
private fun IntervalDirectionBlock(ear: EarTrainingState, r: IntervalSongRef) {
    val dirId = (if (r.ascending) "asc:" else "desc:") + r.interval
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        PreviewPlayButton(ear, dirId, if (r.ascending) "▶ ↑" else "▶ ↓") {
            ear.playIntervalPreview(dirId, r.semitones, r.ascending)
        }
        Spacer(Modifier.width(8.dp))
        Text(if (r.ascending) "Ascending" else "Descending", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text("(${r.inversion})", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (r.songs.isEmpty()) {
        Text(r.note, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
    }
    for ((i, song) in r.songs.withIndex()) {
        val songId = "$dirId:song$i"
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 2.dp)) {
            PreviewPlayButton(ear, songId) { ear.playMelodySnippet(songId, song.melody) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                ExternalSongRow(song.title, song.artist)
                Text(song.cue, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One interval, both directions: name, then ↑ and ↓ blocks ([firstAscending] picks
 *  which comes first — the trainer puts the direction you just heard on top). */
@Composable
private fun IntervalCard(ear: EarTrainingState, semitones: Int, firstAscending: Boolean = true) {
    val first = IntervalSongs.refFor(semitones, firstAscending) ?: return
    val second = IntervalSongs.refFor(semitones, !firstAscending) ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(first.interval, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(34.dp))
            Text(first.intervalLong, fontWeight = FontWeight.Bold)
        }
        IntervalDirectionBlock(ear, first)
        IntervalDirectionBlock(ear, second)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The interval-reference block, shared by the Theory tab and the interval trainer's dialog. */
@Composable
internal fun IntervalRefsContent(ear: EarTrainingState) {
    Text("▶ ↑ / ▶ ↓ play the bare interval (from C4): the two notes in turn, then together. " +
        "The ▶ beside a song plays a few notes of its melody that contain the interval; " +
        "YouTube/Spotify open the real recording.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    for (st in 1..12) IntervalCard(ear, st)
    Spacer(Modifier.height(8.dp))
    Text(IntervalSongs.COMPLEMENT_NOTE, style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The trainer's post-answer "♪ Songs for …" dialog: one interval, heard direction first. */
@Composable
internal fun IntervalSongsDialog(ear: EarTrainingState, semitones: Int, ascending: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { ear.stopIntervalPreview(); onDismiss() },
        confirmButton = { TextButton(onClick = { ear.stopIntervalPreview(); onDismiss() }) { Text("Close") } },
        title = { Text("Songs: ${IntervalSongs.refFor(semitones, ascending)?.intervalLong ?: ""}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                IntervalCard(ear, semitones, firstAscending = ascending)
            }
        },
    )
}

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

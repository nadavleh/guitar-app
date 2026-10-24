package app.guitar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
private fun IntervalDirectionBlock(ear: EarTrainingState, r: IntervalSongRef, anchorMidi: Int?) {
    val dirId = (if (r.ascending) "asc:" else "desc:") + r.interval
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        PreviewPlayButton(ear, dirId, if (r.ascending) "▶ ↑" else "▶ ↓") {
            ear.playIntervalPreview(dirId, r.semitones, r.ascending, baseMidi = anchorMidi ?: 60)
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
            PreviewPlayButton(ear, songId) {
                ear.playMelodySnippet(songId, song.melody, r.semitones, r.ascending, anchorMidi)
            }
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
private fun IntervalCard(ear: EarTrainingState, semitones: Int, firstAscending: Boolean = true,
                         anchorMidi: Int? = null) {
    val first = IntervalSongs.refFor(semitones, firstAscending) ?: return
    val second = IntervalSongs.refFor(semitones, !firstAscending) ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(first.interval, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(34.dp))
            Text(first.intervalLong, fontWeight = FontWeight.Bold)
        }
        IntervalDirectionBlock(ear, first, anchorMidi)
        IntervalDirectionBlock(ear, second, anchorMidi)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The interval-reference block, shared by the Theory tab and the interval trainer's dialog.
 *  Master–detail instead of one long list: a 12-chip interval picker plus an ↑/↓ toggle
 *  select ONE interval in ONE direction, so any interval is a single tap away and the
 *  songs below never push the others off-screen. ‹ › step to the neighbouring interval.
 *  With [anchorMidi] (the trainer's tonic) everything plays from that note instead of C4,
 *  and each song's leap starts on it; [initialSemitones]/[initialAscending] preselect
 *  (the trainer opens on the question just asked). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntervalRefsContent(ear: EarTrainingState, anchorMidi: Int? = null,
                                 initialSemitones: Int = 1, initialAscending: Boolean = true) {
    var sel by rememberSaveable { mutableIntStateOf(initialSemitones.coerceIn(1, 12)) }
    var up by rememberSaveable { mutableStateOf(initialAscending) }
    fun pick(st: Int, asc: Boolean) { ear.stopIntervalPreview(); sel = st; up = asc }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (st in 1..12) {
            val r = IntervalSongs.refFor(st, up) ?: continue
            FilterChip(selected = st == sel, onClick = { pick(st, up) },
                label = { Text(r.interval, fontWeight = FontWeight.Bold) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (asc in listOf(true, false)) {
            val n = IntervalSongs.refFor(sel, asc)?.songs?.size ?: 0
            FilterChip(selected = up == asc, onClick = { pick(sel, asc) },
                label = { Text((if (asc) "↑ Ascending" else "↓ Descending") + " · $n") })
        }
    }
    val r = IntervalSongs.refFor(sel, up) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        TextButton(onClick = { pick(if (sel == 1) 12 else sel - 1, up) }) { Text("‹") }
        Text(r.interval, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(34.dp))
        Text(r.intervalLong, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${r.semitones} st", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { pick(if (sel == 12) 1 else sel + 1, up) }) { Text("›") }
    }
    IntervalDirectionBlock(ear, r, anchorMidi)
    Spacer(Modifier.height(10.dp))
    Text("▶ ↑ / ▶ ↓ play the bare interval (from " +
        (if (anchorMidi == null) "C4" else "this question's tonic") +
        "): the two notes in turn, then together. " +
        "The ▶ beside a song plays a few notes of its melody that contain the interval; " +
        "YouTube/Spotify open the real recording.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(IntervalSongs.COMPLEMENT_NOTE, style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The trainer's post-answer "♪ Songs for …" dialog: one interval, heard direction first. */
@Composable
internal fun IntervalSongsDialog(ear: EarTrainingState, semitones: Int, ascending: Boolean,
                                 anchorMidi: Int? = null, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { ear.stopIntervalPreview(); onDismiss() },
        confirmButton = { TextButton(onClick = { ear.stopIntervalPreview(); onDismiss() }) { Text("Close") } },
        title = { Text("Songs: ${IntervalSongs.refFor(semitones, ascending)?.intervalLong ?: ""}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (anchorMidi != null) {
                    Text("Played from this question's tonic, so each song's leap is the one you just heard.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IntervalCard(ear, semitones, firstAscending = ascending, anchorMidi = anchorMidi)
            }
        },
    )
}

@Composable
internal fun IntervalRefsDialog(ear: EarTrainingState, anchorMidi: Int? = null,
                                initialSemitones: Int = 1, initialAscending: Boolean = true,
                                onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { ear.stopIntervalPreview(); onDismiss() },
        confirmButton = { TextButton(onClick = { ear.stopIntervalPreview(); onDismiss() }) { Text("Close") } },
        title = { Text("Interval song references") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                IntervalRefsContent(ear, anchorMidi, initialSemitones, initialAscending)
            }
        },
    )
}

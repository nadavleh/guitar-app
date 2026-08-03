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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.guitar.theory.IntervalSongRef
import app.guitar.theory.IntervalSongs

/**
 * Theory tab — reference sheets. First section: interval → song lookup
 * (descending from Nadav's PDF, ascending generated — theory IntervalSongs).
 * The ear-training interval trainer's "♪ Song refs" dialog reuses
 * [IntervalRefsContent]. Mirror of chorect-web's theoryUI.ts. Built to grow —
 * more sections land here later.
 */
@Composable
fun TheoryScreen(state: AppState, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("THEORY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Interval song references", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Text("A familiar song for every interval, both directions. Tap a song to hear it " +
                        "(YouTube ▶ / Spotify ♫). More theory sheets will land in this tab over time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    IntervalRefsContent()
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** One interval row: name + tappable song + cue + inversion note. */
@Composable
private fun IntervalRow(r: IntervalSongRef) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(r.interval, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(38.dp))
            Text(r.intervalLong, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("(${r.inversion})", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A row without an artist is a "construct it yourself" entry — no search link.
        if (r.artist.isNotEmpty()) SongLinkRow(r.song, r.artist)
        else Text(r.song, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(r.cue, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp))
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The interval-reference block, shared by the Theory tab and the ear-training
 *  interval trainer's dialog. */
@Composable
internal fun IntervalRefsContent() {
    Text("Ascending", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text("Sing the cue, then the leap — the song IS the interval.",
        style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (r in IntervalSongs.ASCENDING) IntervalRow(r)
    Spacer(Modifier.height(12.dp))
    Text("Descending", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Text("From the reference PDF — the classic downward leaps.",
        style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    for (r in IntervalSongs.DESCENDING) IntervalRow(r)
    Spacer(Modifier.height(8.dp))
    Text(IntervalSongs.COMPLEMENT_NOTE, style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "♪ Song refs" dialog for the interval trainer — same content as the Theory tab. */
@Composable
internal fun IntervalRefsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Interval song references") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                IntervalRefsContent()
            }
        },
    )
}

package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.guitar.theory.CavaqSequences
import app.guitar.theory.Fretboard
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CavaqProgressionsScreen(state: AppState, onBack: () -> Unit) {
    val cp = state.cavaqProg
    // Stop the loop when NAVIGATING AWAY; keep the picked sequence/key/position.
    // Guard on currentSheet so a rotation (portrait/landscape layout swap disposes+
    // recreates this composable) doesn't stop playback — only a real navigation does.
    DisposableEffect(Unit) { onDispose { if (state.currentSheet != Sheet.CavaqProgressions) cp.stop() } }
    // Seed the idle fretboard preview (first chord's shape) on open.
    LaunchedEffect(Unit) { if (!cp.isPlaying) cp.setPosition(cp.positionIndex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "PROGRESSIONS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            CavaqSongsButton(cp.sequenceId)
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { cp.stop(); onBack() }) { Text("Back") }
        }
        Spacer(Modifier.height(8.dp))

        // ---- Sequence picker ----
        SequenceDropdown(cp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))

        // ---- Key + transpose ----
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Key: ${cp.keyLabel()}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(96.dp))
            OutlinedButton(onClick = { cp.shiftKey(-1) }) { Text("−") }
            OutlinedButton(onClick = { cp.shiftKey(+1) }) { Text("+") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { cp.chooseKey(PitchClass.G) }) { Text("Reset to G") }
        }
        Spacer(Modifier.height(8.dp))

        // ---- Tempo ----
        NumericValueText("Tempo: ${cp.bpm} BPM", value = cp.bpm.toFloat(), min = 40f, max = 200f,
            onSet = { cp.changeBpm(it.toInt()) },
            style = MaterialTheme.typography.bodySmall)
        Slider(value = cp.bpm.toFloat(), onValueChange = { cp.changeBpm(it.toInt()) }, valueRange = 40f..200f)

        // ---- Play + position scroller ----
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { cp.toggle() }) { Text(if (cp.isPlaying) "Stop ■" else "Play ▶") }
            Spacer(Modifier.weight(1f))
            Text("Position", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { cp.nudgePosition(-1) }, enabled = cp.positionIndex > 0) { Text("◀") }
            Text("${cp.positionIndex + 1}/${cp.positionCount}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { cp.nudgePosition(+1) }, enabled = cp.positionIndex < cp.positionCount - 1) { Text("▶") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Position moves the whole sequence up/down the neck — each chord is the least-motion voicing from the previous.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        // ---- Chord chips (roman + symbol); tap to hear; current bar highlighted ----
        val resolved = cp.resolved
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            resolved.forEachIndexed { i, rc ->
                val isCurrent = cp.currentBar == i
                Button(
                    onClick = { cp.playBar(i) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(rc.romanLabel, style = MaterialTheme.typography.labelSmall)
                        Text(rc.symbol, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- Fretboard (follows playback / shows the current voicing) ----
        val shape = cp.currentShape
        val marks = remember(shape, state.labelMode) {
            shape?.let { shapeMarks(it, state.labelMode) } ?: emptyMap()
        }
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            FretboardView(
                tuning = cp.tuning,
                marks = marks,
                selectedPosition = null,
                onTap = { pos ->
                    val midi = Fretboard.noteAt(cp.tuning, pos).midi.value
                    state.audio.playNote(midi, durationMillis = state.ringSustainMs)
                },
                numFrets = DISPLAY_FRETS,
                leftHanded = state.leftHanded,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** "Songs ♪" button → popup of curated samba songs matching the current sequence's family. */
@Composable
private fun CavaqSongsButton(sequenceId: String) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("Songs ♪") }
    if (open) {
        val songs = app.guitar.theory.CavaqSongs.forSequence(sequenceId)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Close") }
            },
            title = { Text("Samba songs with this progression") },
            text = {
                if (songs.isEmpty()) {
                    Text("No curated songs match this sequence yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        songs.forEach { SongLinkRow(it.title, it.artist, "  (${it.keyLabel})") }
                    }
                }
            },
        )
    }
}

/** Sequence picker dropdown (English name). */
@Composable
private fun SequenceDropdown(cp: CavaqProgState, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Sequence: ${cp.sequence.nameEn}  ▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CavaqSequences.ALL.forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.nameEn}   ·   ${s.namePt}") },
                    onClick = { cp.setSequence(s.id); open = false },
                )
            }
        }
    }
}

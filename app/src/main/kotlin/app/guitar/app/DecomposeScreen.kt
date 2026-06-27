package app.guitar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.guitar.theory.ChordDecomposition
import app.guitar.theory.ChordDecompositions
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.audio.Timbre
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Decompose 🧩" tool (#5). A non-quiz reference that shows, on the fretboard, how
 * an extended/altered chord splits into a **shell** (root + guide tones, one colour)
 * and an **upper-structure triad** (another colour) — the pianist's "left-hand
 * shell / right-hand triad" idea applied to guitar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecomposeScreen(state: AppState, onBack: () -> Unit) {
    var root by remember { mutableStateOf(PitchClass.C) }
    var quality by remember { mutableStateOf(ChordDecompositions.ALL.first().quality) }
    val scope = rememberCoroutineScope()

    val dec = ChordDecompositions.forQuality(quality) ?: ChordDecompositions.ALL.first()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("DECOMPOSE", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            AudioQuickButton(state, compact = true)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(6.dp))
        Text("See how an extended chord = a shell (root + guide tones) plus a triad on top — " +
            "the way pianists voice extensions with two hands.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))

        // Root + chord-type pickers
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Root", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
            RootPicker(root) { root = it }
        }
        Spacer(Modifier.height(8.dp))
        Text("Chord", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in ChordDecompositions.ALL) {
                FilterChip(
                    selected = c.quality == quality,
                    onClick = { quality = c.quality },
                    label = { Text(NoteSpeller.spell(root) + c.displayName) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Summary card
        val shellNotes = dec.shell.map { NoteSpeller.spell(PitchClass(((root.value + it) % 12 + 12) % 12)) }
        val upperRootPc = PitchClass(((root.value + dec.upperRootInterval) % 12 + 12) % 12)
        val upperLabel = "${NoteSpeller.spell(upperRootPc)} ${dec.upperTriad}"
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text("${NoteSpeller.spell(root)}${dec.displayName}  ≈  shell + triad",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Shell (bass): ${shellNotes.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text("Upper triad: $upperLabel   (${dec.upperDegrees})",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                scope.launch {
                    val base = 48 + root.value          // C3-ish so the upper triad sits up top
                    val shellMidis = dec.shell.map { base + it }
                    val upperMidis = dec.upper.map { base + it }
                    state.audio.playChord(shellMidis, strumDelayMillis = 26,
                        sustainMillis = 1100, timbre = Timbre.Clarity)
                    delay(700)
                    state.audio.playChord(upperMidis, strumDelayMillis = 26,
                        sustainMillis = 1100, timbre = Timbre.Clarity)
                }
            }) { Text("Play shell → triad ▶") }
            Text("● shell   ● triad", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(10.dp))
        // Fretboard: shell tones (Chord colour) + upper-triad tones (Scale colour).
        val marks = remember(root, quality, state.labelMode, state.liveTuning) {
            decomposeMarks(state, root, dec)
        }
        Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
            FretboardView(
                tuning = state.liveTuning,
                marks = marks,
                selectedPosition = null,
                onTap = { pos ->
                    val midi = Fretboard.noteAt(state.liveTuning, pos).midi.value
                    state.audio.playNote(midi, durationMillis = state.ringSustainMs)
                },
                numFrets = DISPLAY_FRETS,
                leftHanded = state.leftHanded,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RootPicker(root: PitchClass, onPick: (PitchClass) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(NoteSpeller.spell(root) + " ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (i in 0..11) {
                val pc = PitchClass(i)
                DropdownMenuItem(text = { Text(NoteSpeller.spell(pc)) },
                    onClick = { onPick(pc); open = false })
            }
        }
    }
}

/** Mark every fret position whose pitch class is a shell tone (Chord kind, root
 *  highlighted) or an upper-triad tone (Scale kind). */
private fun decomposeMarks(
    state: AppState,
    root: PitchClass,
    dec: ChordDecomposition,
): Map<FretPosition, FretMark> {
    val rootPc = root.value
    val shellPcs = dec.shell.map { ((rootPc + it) % 12 + 12) % 12 }.toSet()
    val upperPcs = dec.upper.map { ((rootPc + it) % 12 + 12) % 12 }.toSet()
    val tuning = state.liveTuning
    val out = HashMap<FretPosition, FretMark>()
    for (s in tuning.openStrings.indices) {
        for (f in 0..DISPLAY_FRETS) {
            val pos = FretPosition(s, f)
            val pc = Fretboard.noteAt(tuning, pos).pitchClass
            val label = when (state.labelMode) {
                LabelMode.Notes -> NoteSpeller.spell(pc)
                else -> ""
            }
            when {
                pc.value in shellPcs ->
                    out[pos] = FretMark(label = label, isRoot = pc.value == rootPc, kind = MarkKind.Chord)
                // Don't double-mark a pitch class that's already a shell tone.
                pc.value in upperPcs ->
                    out[pos] = FretMark(label = label, isRoot = false, kind = MarkKind.Scale)
            }
        }
    }
    return out
}

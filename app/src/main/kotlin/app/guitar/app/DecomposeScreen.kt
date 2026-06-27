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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var showGuide by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val dec = ChordDecompositions.forQuality(quality) ?: ChordDecompositions.ALL.first()

    if (showGuide) DecomposeGuideDialog(onDismiss = { showGuide = false })

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("DECOMPOSE", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { showGuide = true }) { Text("Guide") }
            Spacer(Modifier.width(8.dp))
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
        }
        Spacer(Modifier.height(6.dp))
        // Legend: the circle colours + a note that the labels are interval degrees.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LegendDot(GuitarColors.rootTone, "root (1)")
            LegendDot(GuitarColors.chordTone, "shell")
            LegendDot(GuitarColors.scaleTone, "upper triad")
            Text("numbers = interval degree", style = MaterialTheme.typography.labelSmall,
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
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("●", color = color, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val DECOMPOSE_GUIDE =
    "Pianists voice extensions as a shell in the left hand (root + the 3rd & 7th that " +
    "define the quality) and a triad in the right hand. On guitar it's the same idea — " +
    "and every circle here is labelled with its interval degree.\n\n" +
    "6th chords — root + a triad on the 6th:\n" +
    "• C6 = C + A minor (the 6th carries a minor triad)\n" +
    "• Cm6 = C + A°\n" +
    "(C6 shares its notes with Am7 — the relative-minor 7th.)\n\n" +
    "7th chords — root + a triad on the 3rd:\n" +
    "• Cmaj7 = C + E minor (3·5·7)\n" +
    "• C7 = C + E° (3·5·♭7)\n" +
    "• Cm7 = C + E♭ major (♭3·5·♭7)\n" +
    "• Cm7♭5 = C + E♭ minor;   C°7 = C + E♭°\n\n" +
    "Extensions — shell (1·3·♭7) + an upper-structure triad:\n" +
    "• C9 = C7 shell + G minor (5·♭7·9)\n" +
    "• Cmaj9 = Cmaj7 shell + G major (5·7·9)\n" +
    "• C11 = C7 shell + B♭ major (♭7·9·11)\n" +
    "• C13 = C7 shell + D major (9·♯11·13)\n" +
    "• C7♯9 = C + E♭ major (♯9·5·♭7);   C7♭9 = C + D♭° (♭9·3·5)"

@Composable
private fun DecomposeGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text("How chords decompose") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(DECOMPOSE_GUIDE, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
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

/** Degree label for a chord-relative interval (incl. compound 9/11/13), so the
 *  circles read "1 / 3 / ♭7 / 9 / ♯11 / 13" rather than note names. */
private fun decomposeDegree(interval: Int): String = when (interval) {
    0, 12 -> "1"
    1 -> "♭9"; 13 -> "♭9"
    2, 14 -> "9"
    3 -> "♭3"; 15 -> "♯9"
    4, 16 -> "3"
    5, 17 -> "11"
    6 -> "♭5"; 18 -> "♯11"
    7 -> "5"
    8 -> "♯5"; 20 -> "♭13"
    9 -> "6"; 21 -> "13"
    10 -> "♭7"
    11 -> "7"
    else -> "$interval"
}

/**
 * Mark fret positions, labelling each circle with its INTERVAL degree (always —
 * independent of the global note/interval setting). Shell tones use the Chord
 * colour (root highlighted); upper-triad tones use the Scale colour.
 */
private fun decomposeMarks(
    state: AppState,
    root: PitchClass,
    dec: ChordDecomposition,
): Map<FretPosition, FretMark> {
    val rootPc = root.value
    // pitch class -> (degree label, isUpper). Shell wins if a pc appears in both.
    val pcInfo = HashMap<Int, Pair<String, Boolean>>()
    for (iv in dec.upper) pcInfo[((rootPc + iv) % 12 + 12) % 12] = decomposeDegree(iv) to true
    for (iv in dec.shell) pcInfo[((rootPc + iv) % 12 + 12) % 12] = decomposeDegree(iv) to false
    val tuning = state.liveTuning
    val out = HashMap<FretPosition, FretMark>()
    for (s in tuning.openStrings.indices) {
        for (f in 0..DISPLAY_FRETS) {
            val pos = FretPosition(s, f)
            val pc = Fretboard.noteAt(tuning, pos).pitchClass.value
            val info = pcInfo[pc] ?: continue
            out[pos] = FretMark(
                label = info.first,
                isRoot = pc == rootPc,
                kind = if (info.second) MarkKind.Scale else MarkKind.Chord,
            )
        }
    }
    return out
}

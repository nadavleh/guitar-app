package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordShape
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.ScaleLibrary
import app.guitar.theory.ScalePosition

/**
 * Concept-A control dock (milestone 2). A fixed strip docked under the neck that
 * replaces the old modal bottom sheets for Chord / Scale / Pick. Controls are
 * laid out as compact `LABEL → horizontally-scrollable chip strip` rows plus
 * segmented toggles, so everything stays on one screen — no modal, no page
 * scroll. The dock's content follows the fretboard's current [DisplayMode].
 */

private val DOCK_PITCH_CLASSES = listOf(
    PitchClass.C, PitchClass.Cs, PitchClass.D, PitchClass.Ds,
    PitchClass.E, PitchClass.F, PitchClass.Fs, PitchClass.G,
    PitchClass.Gs, PitchClass.A, PitchClass.As, PitchClass.B,
)

private val DOCK_QUALITIES = listOf(
    "", "m", "7", "maj7", "m7", "dim", "aug", "sus4", "sus2",
    "6", "m6", "m7b5", "dim7", "9", "add9", "13",
)

@Composable
fun ControlDock(
    state: AppState,
    chordShapes: List<ChordShape>,
    scalePositions: List<ScalePosition>,
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when (state.displayMode) {
            DisplayMode.Chord -> ChordDock(state, chordShapes)
            DisplayMode.Scale -> ScaleDock(state, scalePositions)
            DisplayMode.Pick -> PickDock(state)
            DisplayMode.None -> Text(
                "Pick a tool from the rail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- shared atoms ----------

@Composable
private fun DockLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(44.dp),
    )
}

/** A label + horizontally-scrollable chip strip on one row. */
@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DockLabel(label)
        // weight + horizontalScroll are valid here: we're inside the outer Row's
        // RowScope content lambda.
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

// ---------- Chord ----------

@Composable
private fun ChordDock(state: AppState, chordShapes: List<ChordShape>) {
    val parsed = ChordLibrary.parse(state.chordInput)
    val currentRoot = parsed?.first
    val currentQuality = parsed?.second?.symbol

    ChipRow("Root") {
        DOCK_PITCH_CLASSES.forEach { pc ->
            FilterChip(
                selected = pc == currentRoot,
                onClick = {
                    state.chordInput = NoteSpeller.spell(pc) + (currentQuality ?: "")
                    state.resetChordPosition()
                },
                label = { Text(NoteSpeller.spell(pc)) },
            )
        }
    }
    ChipRow("Qual") {
        DOCK_QUALITIES.forEach { sym ->
            FilterChip(
                selected = sym == currentQuality,
                onClick = {
                    state.chordInput = NoteSpeller.spell(currentRoot ?: PitchClass.C) + sym
                    state.resetChordPosition()
                },
                label = { Text(if (sym.isEmpty()) "maj" else sym) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ViewToggle(
            isPositions = state.chordView == ChordScaleView.Positions,
            onAll = { state.chordView = ChordScaleView.AllNotes },
            onPositions = { state.chordView = ChordScaleView.Positions },
        )
        Spacer(Modifier.width(8.dp))
        LabelToggle(state)
    }
    if (state.chordView == ChordScaleView.Positions && chordShapes.isNotEmpty()) {
        val sh = chordShapes.getOrNull(state.chordPositionIndex)
        val played = sh?.frets?.filterNotNull().orEmpty()
        val fretText = when {
            played.isEmpty() -> ""
            played.min() == played.max() -> "fret ${played.min()}"
            else -> "frets ${played.min()}–${played.max()}"
        }
        PositionStepper(
            label = "${sh?.chordName ?: ""}  ·  $fretText  ·  ${state.chordPositionIndex + 1}/${chordShapes.size}",
            onPrev = { state.stepChordPosition(-1, chordShapes.size) },
            onNext = { state.stepChordPosition(+1, chordShapes.size) },
        )
    }
}

// ---------- Scale ----------

@Composable
private fun ScaleDock(state: AppState, scalePositions: List<ScalePosition>) {
    ChipRow("Root") {
        DOCK_PITCH_CLASSES.forEach { pc ->
            val name = NoteSpeller.spell(pc)
            FilterChip(
                selected = name == state.scaleRoot,
                onClick = { state.scaleRoot = name; state.resetScalePosition() },
                label = { Text(name) },
            )
        }
    }
    ChipRow("Scale") {
        ScaleLibrary.scales.keys.forEach { name ->
            FilterChip(
                selected = name == state.scaleType,
                onClick = { state.scaleType = name; state.resetScalePosition() },
                label = { Text(name) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ViewToggle(
            isPositions = state.scaleView == ChordScaleView.Positions,
            onAll = { state.scaleView = ChordScaleView.AllNotes },
            onPositions = { state.scaleView = ChordScaleView.Positions },
        )
        Spacer(Modifier.width(8.dp))
        LabelToggle(state)
    }
    if (state.scaleView == ChordScaleView.Positions && scalePositions.isNotEmpty()) {
        val sp = scalePositions.getOrNull(state.scalePositionIndex)
        val anchor = sp?.let {
            "anchor ${NoteSpeller.spell(it.anchorPitchClass)} · frets ${it.firstFret}–${it.lastFret}"
        } ?: ""
        PositionStepper(
            label = "${state.scaleRoot} ${state.scaleType}  ·  $anchor  ·  ${state.scalePositionIndex + 1}/${scalePositions.size}",
            onPrev = { state.stepScalePosition(-1, scalePositions.size) },
            onNext = { state.stepScalePosition(+1, scalePositions.size) },
        )
    }
}

// ---------- Pick ----------

@Composable
private fun PickDock(state: AppState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Picked: ${state.pickedPositions.size}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { state.strumPicked(false) }, enabled = state.pickedPositions.isNotEmpty()) { Text("Strum") }
        OutlinedButton(onClick = { state.strumPicked(true) }, enabled = state.pickedPositions.isNotEmpty()) { Text("Arp") }
        OutlinedButton(onClick = { state.clearPicked() }, enabled = state.pickedPositions.isNotEmpty()) { Text("Clear") }
    }
}

// ---------- toggles / stepper ----------

@Composable
private fun ViewToggle(isPositions: Boolean, onAll: () -> Unit, onPositions: () -> Unit) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = !isPositions,
            onClick = onAll,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("All notes") },
        )
        SegmentedButton(
            selected = isPositions,
            onClick = onPositions,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("Positions") },
        )
    }
}

@Composable
private fun LabelToggle(state: AppState) {
    SingleChoiceSegmentedButtonRow {
        LabelMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = m == state.labelMode,
                onClick = { state.setLabelMode(m) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = LabelMode.entries.size),
                label = { Text(when (m) { LabelMode.Notes -> "Notes"; LabelMode.Intervals -> "Int"; LabelMode.Empty -> "—" }) },
            )
        }
    }
}

@Composable
private fun PositionStepper(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(onClick = onPrev) { Text("◀", style = MaterialTheme.typography.titleMedium) }
        Text(
            label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onNext) { Text("▶", style = MaterialTheme.typography.titleMedium) }
    }
}

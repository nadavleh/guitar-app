package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordShapeGenerator
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.ScaleLibrary
import app.guitar.theory.Tuning
import app.guitar.theory.Tunings
import app.guitar.theory.VoicingStyle

private val PITCH_CLASS_ROW = listOf(
    PitchClass.C, PitchClass.Cs, PitchClass.D, PitchClass.Ds,
    PitchClass.E, PitchClass.F, PitchClass.Fs, PitchClass.G,
    PitchClass.Gs, PitchClass.A, PitchClass.As, PitchClass.B,
)

private val COMMON_QUALITY_SYMBOLS = listOf(
    "", "m", "7", "maj7", "m7", "dim", "aug", "sus4", "sus2",
    "6", "m6", "m7b5", "dim7", "9", "add9", "13",
)

private fun qualityLabel(symbol: String): String =
    if (symbol.isEmpty()) "major" else symbol

// ---------- CHORD SHEET ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordSheet(state: AppState) {
    val parsed = ChordLibrary.parse(state.chordInput)
    val currentRoot = parsed?.first
    val currentQualitySymbol = parsed?.second?.symbol

    SheetBody {
        SheetHeader("Chord")

        Text("Root", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PITCH_CLASS_ROW.forEach { pc ->
                FilterChip(
                    selected = pc == currentRoot,
                    onClick = {
                        state.chordInput = NoteSpeller.spell(pc) + (currentQualitySymbol ?: "")
                        state.resetChordPosition()
                    },
                    label = { Text(NoteSpeller.spell(pc)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Quality", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COMMON_QUALITY_SYMBOLS.forEach { sym ->
                FilterChip(
                    selected = sym == currentQualitySymbol,
                    onClick = {
                        state.chordInput = NoteSpeller.spell(currentRoot ?: PitchClass.C) + sym
                        state.resetChordPosition()
                    },
                    label = { Text(qualityLabel(sym)) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Display", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChordScaleView.entries.forEachIndexed { i, v ->
                SegmentedButton(
                    selected = state.chordView == v,
                    onClick = { state.chordView = v },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ChordScaleView.entries.size),
                    label = { Text(if (v == ChordScaleView.AllNotes) "All notes" else "Positions") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Labels", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LabelMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == state.labelMode,
                    onClick = { state.setLabelMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = LabelMode.entries.size),
                    label = { Text(m.name) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (parsed != null) {
            val (root, q) = parsed
            val notes = q.notesFrom(root).joinToString(" ") { NoteSpeller.spell(it) }
            val intervalsLine = q.intervals.joinToString(" ") { intervalName(it) }
            Text("${NoteSpeller.spell(root)}${q.symbol}:  $notes", style = MaterialTheme.typography.bodyMedium)
            Text(
                "intervals:  $intervalsLine",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("(chord not recognized)", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { state.closeSheet() }) { Text("Done") }
        }
    }
}

// ---------- SCALE SHEET ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleSheet(state: AppState) {
    val scalePc = try { NoteSpeller.parsePitchClass(state.scaleRoot) } catch (_: Exception) { null }
    val scale = ScaleLibrary.scales[state.scaleType]

    SheetBody {
        SheetHeader("Scale")

        Text("Root", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PITCH_CLASS_ROW.forEach { pc ->
                val pcName = NoteSpeller.spell(pc)
                FilterChip(
                    selected = pcName == state.scaleRoot,
                    onClick = { state.scaleRoot = pcName; state.resetScalePosition() },
                    label = { Text(pcName) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Scale", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScaleLibrary.scales.keys.forEach { name ->
                FilterChip(
                    selected = name == state.scaleType,
                    onClick = { state.scaleType = name; state.resetScalePosition() },
                    label = { Text(name) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Display", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChordScaleView.entries.forEachIndexed { i, v ->
                SegmentedButton(
                    selected = state.scaleView == v,
                    onClick = { state.scaleView = v },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ChordScaleView.entries.size),
                    label = { Text(if (v == ChordScaleView.AllNotes) "All notes" else "Positions") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Labels", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LabelMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == state.labelMode,
                    onClick = { state.setLabelMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = LabelMode.entries.size),
                    label = { Text(m.name) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (scalePc != null && scale != null) {
            val notes = scale.notesFrom(scalePc).joinToString(" ") { NoteSpeller.spell(it) }
            val formula = scale.intervals.joinToString(" ") { intervalName(it) }
            Text("${state.scaleRoot} ${scale.name}", style = MaterialTheme.typography.bodyMedium)
            Text("notes    $notes",   fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text("formula  $formula", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("(invalid root or scale)", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { state.closeSheet() }) { Text("Done") }
        }
    }
}

// ---------- PICK SHEET ----------

@Composable
fun PickSheet(state: AppState) {
    SheetBody {
        SheetHeader("Pick & strum")
        Text(
            "Tap any fret on the neck to add or remove it from your selection. " +
                "Use the bottom strip to strum or clear.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text("Picked: ${state.pickedPositions.size}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.strumPicked(arpeggio = false) }, enabled = state.pickedPositions.isNotEmpty()) { Text("Strum") }
            OutlinedButton(onClick = { state.strumPicked(arpeggio = true) }, enabled = state.pickedPositions.isNotEmpty()) { Text("Arpeggio") }
            OutlinedButton(onClick = { state.clearPicked() }, enabled = state.pickedPositions.isNotEmpty()) { Text("Clear") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { state.closeSheet() }) { Text("Done") }
        }
    }
}

// ---------- OPTIONS SHEET ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionsSheet(state: AppState, customTunings: Map<String, Tuning>) {
    var editorOpen by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    SheetBody {
        SheetHeader("Options")

        Text("Tuning", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tunings.all.forEach { (name, t) ->
                FilterChip(
                    selected = name == state.tuningName && !state.isEditedTuning,
                    onClick = { state.selectTuning(name, t) },
                    label = { Text(name) }
                )
            }
        }
        if (customTunings.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("My tunings", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                customTunings.forEach { (name, t) ->
                    FilterChip(
                        selected = name == state.tuningName && !state.isEditedTuning,
                        onClick = { state.selectTuning(name, t) },
                        label = { Text(name) },
                        trailingIcon = {
                            TextButton(onClick = { state.deleteCustomTuning(name) }) { Text("✕") }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Open strings (low → high):  " +
                state.liveTuning.openStrings.joinToString(" ") { NoteSpeller.spell(it) },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.isEditedTuning) {
            Text("(unsaved edits)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { editorOpen = !editorOpen }) { Text(if (editorOpen) "Close editor" else "Customize…") }
            if (state.isEditedTuning) {
                TextButton(onClick = { state.resetTuningToSaved(customTunings) }) { Text("Discard edits") }
            }
        }
        if (editorOpen) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    for (s in (state.liveTuning.stringCount - 1) downTo 0) {
                        val n = state.liveTuning.stringCount - s
                        val note = state.liveTuning.openStrings[s]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("S$n  ${NoteSpeller.spell(note).padEnd(4)}", fontFamily = FontFamily.Monospace, modifier = Modifier.width(88.dp))
                            OutlinedButton(onClick = { state.adjustString(s, -1) }) { Text("−") }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton(onClick = { state.adjustString(s, +1) }) { Text("+") }
                            Spacer(Modifier.width(10.dp))
                            OutlinedButton(onClick = { state.adjustString(s, -12) }) { Text("−oct") }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton(onClick = { state.adjustString(s, +12) }) { Text("+oct") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        OutlinedTextField(
                            value = saveName,
                            onValueChange = { saveName = it },
                            label = { Text("Save as…") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { state.saveCustomTuning(saveName); saveName = "" },
                            enabled = saveName.trim().isNotEmpty() && '|' !in saveName && ';' !in saveName
                        ) { Text("Save") }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        Text("Display", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text("Labels on dots", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LabelMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == state.labelMode,
                    onClick = { state.setLabelMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = LabelMode.entries.size),
                    label = { Text(m.name) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Left-handed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.leftHanded, onCheckedChange = { state.toggleLeftHanded(it) })
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Jazz / shell voicings", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Drop the 5th (and root for 7+ chords); favor 2-4 note voicings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.voicingStyle == VoicingStyle.Shell,
                onCheckedChange = {
                    state.toggleVoicingStyle(it)
                    state.resetChordPosition()
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // ----- Tuner / audio configuration -----
        Text("Tuner & audio", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text("A4 reference: ${state.a4Hz.toInt()} Hz",
            style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = state.a4Hz,
            onValueChange = { state.setA4Hz(it) },
            valueRange = 435f..445f,
            steps = 9,  // 1 Hz increments
        )
        Spacer(Modifier.height(8.dp))
        Text("Ring sustain: ${"%.1f".format(state.ringSustainMs / 1000f)} s",
            style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = state.ringSustainMs.toFloat(),
            onValueChange = { state.setRingSustainMs(it.toInt()) },
            valueRange = 300f..4000f,
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { state.closeSheet() }) { Text("Done") }
        }
    }
}

// ---------- LOOP SCREEN (full-screen, not a sheet) ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoopScreen(state: AppState) {
    androidx.compose.runtime.LaunchedEffect(state.voicingStyle, state.liveTuning) {
        // Re-normalize whenever the user changes voicing style or tuning, so the
        // default chord voicing matches the current display mode.
        state.normalizeLoopVoicings()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ----- Top: title + transport + back -----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("PROGRESSION LOOP",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f))
            if (state.isLooping) {
                Button(onClick = { state.stopLoop() }) { Text("Stop ⏹") }
            } else {
                Button(onClick = { state.startLoop() },
                    enabled = state.loopProgression.any { bar -> bar.any { it.chordSymbol != null } }) {
                    Text("Play ▶")
                }
            }
            Spacer(Modifier.width(8.dp))
            // Don't stop the loop when navigating away — the user wants to watch
            // the chords sound on the main fretboard live. The Stop button above
            // is the explicit way to halt playback.
            OutlinedButton(onClick = { state.closeSheet() }) {
                Text(if (state.isLooping) "Watch on neck ▶" else "Back")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ----- Controls row: BPM, slots/bar, bars +/- -----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("BPM: ${state.bpm}", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Slider(
                    value = state.bpm.toFloat(),
                    onValueChange = { state.bpm = it.toInt() },
                    valueRange = 40f..200f,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Slots/bar", style = MaterialTheme.typography.labelSmall)
                SingleChoiceSegmentedButtonRow {
                    listOf(1, 2, 4).forEachIndexed { i, n ->
                        SegmentedButton(
                            selected = state.slotsPerBar == n,
                            onClick = { state.setSlotsPerBar(n) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                            label = { Text("$n") },
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Bars: ${state.loopProgression.size}", style = MaterialTheme.typography.labelSmall)
                Row {
                    OutlinedButton(
                        onClick = { state.setBarCount(state.loopProgression.size - 1) },
                        enabled = state.loopProgression.size > 1,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text("−") }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { state.setBarCount(state.loopProgression.size + 1) },
                        enabled = state.loopProgression.size < 16,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text("+") }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(6.dp))

        // ----- Main area: bars grid (left) + slot editor (right, when open) -----
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Bars grid
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 2 bars per row when each bar has 4 slots, otherwise 4 per row.
                val barsPerRow = if (state.slotsPerBar >= 4) 2 else 4
                val rows = state.loopProgression.withIndex().chunked(barsPerRow)
                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for ((barIdx, bar) in row) {
                            BarCard(
                                barIdx = barIdx,
                                bar = bar,
                                isCurrentBar = state.isLooping && state.loopCurrentBar == barIdx,
                                currentSlot = state.loopCurrentSlot,
                                isLooping = state.isLooping,
                                onSlotTap = { slotIdx -> state.loopEditingSlot = barIdx to slotIdx },
                                editingSlot = state.loopEditingSlot,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Pad the row to keep widths equal
                        repeat(barsPerRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            // Slot editor sits to the right of the bars grid when a slot is selected.
            state.loopEditingSlot?.let { (barIdx, slotIdx) ->
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SlotEditor(state, barIdx, slotIdx)
                }
            }
        }
    }
}

@Composable
private fun BarCard(
    barIdx: Int,
    bar: List<LoopSlot>,
    isCurrentBar: Boolean,
    currentSlot: Int,
    isLooping: Boolean,
    onSlotTap: (Int) -> Unit,
    editingSlot: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = if (isCurrentBar)
            androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else androidx.compose.material3.CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text("Bar ${barIdx + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for ((slotIdx, slot) in bar.withIndex()) {
                    val isEditing = editingSlot == (barIdx to slotIdx)
                    val isPlaying = isLooping && isCurrentBar && currentSlot == slotIdx
                    val bg = when {
                        isEditing -> MaterialTheme.colorScheme.tertiaryContainer
                        isPlaying -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .clickable { onSlotTap(slotIdx) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                slot.chordSymbol ?: "·",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (slot.chordSymbol == null)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                slot.strum.glyph,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotEditor(state: AppState, barIdx: Int, slotIdx: Int) {
    val slot = state.loopProgression.getOrNull(barIdx)?.getOrNull(slotIdx) ?: return
    val parsed = slot.chordSymbol?.let { app.guitar.theory.ChordLibrary.parse(it) }
    val shapes: List<app.guitar.theory.ChordShape> = remember(parsed, state.liveTuning, state.voicingStyle) {
        if (parsed == null) emptyList()
        else {
            val (r, q) = parsed
            app.guitar.theory.ChordShapeGenerator(style = state.voicingStyle)
                .shapesFor(r, q, state.liveTuning, frets = DISPLAY_FRETS)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Edit · Bar ${barIdx + 1} / slot ${slotIdx + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { state.loopEditingSlot = null }) { Text("Close") }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = slot.chordSymbol ?: "",
                    onValueChange = { state.setLoopSlotChord(barIdx, slotIdx, it) },
                    label = { Text("Chord") },
                    placeholder = { Text("e.g. Cmaj7, Dm7, G7") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    state.setLoopSlot(barIdx, slotIdx, slot.copy(chordSymbol = null))
                }) { Text("Clear") }
            }

            // ----- Voicing picker -----
            if (slot.chordSymbol != null) {
                Spacer(Modifier.height(8.dp))
                Text("Voicing", style = MaterialTheme.typography.labelMedium)
                if (shapes.isEmpty()) {
                    Text("(chord not recognized)",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        shapes.forEachIndexed { i, sh ->
                            val label = sh.cagedShape?.displayName
                                ?: sh.templateName?.substringBefore(" (")
                                ?: "shape ${i + 1}"
                            val played = sh.frets.filterNotNull().filter { it > 0 }
                            val fretText = if (played.isEmpty()) "open"
                                else if (played.min() == played.max()) "fret ${played.min()}"
                                else "frets ${played.min()}–${played.max()}"
                            FilterChip(
                                selected = i == slot.voicingIndex,
                                onClick = {
                                    state.setLoopSlot(barIdx, slotIdx, slot.copy(voicingIndex = i))
                                },
                                label = { Text("$label · $fretText") },
                            )
                        }
                    }
                }
            }

            // ----- Strum picker -----
            Spacer(Modifier.height(8.dp))
            Text("Strum", style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                StrumPattern.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = s == slot.strum,
                        onClick = { state.setLoopSlot(barIdx, slotIdx, slot.copy(strum = s)) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = StrumPattern.entries.size),
                        label = { Text("${s.glyph} ${s.displayName}") },
                    )
                }
            }
        }
    }
}

// ---------- helpers ----------

@Composable
private fun SheetBody(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
}

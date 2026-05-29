package app.guitar.app

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
import androidx.compose.material3.RangeSlider
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
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.ScaleLibrary
import app.guitar.theory.Tuning
import app.guitar.theory.Tunings

private val shapeGen = ChordShapeGenerator()

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FretboardScreen(state: AppState, modifier: Modifier = Modifier) {
    val parsedChord = ChordLibrary.parse(state.chordInput)
    val scalePc = try { NoteSpeller.parsePitchClass(state.scaleRoot) } catch (_: Exception) { null }
    val scale = ScaleLibrary.scales[state.scaleType]

    val marks: Map<FretPosition, FretMark> = remember(
        state.highlight, parsedChord, scalePc, scale, state.liveTuning, state.labelMode
    ) {
        when (state.highlight) {
            Highlight.Chord -> parsedChord?.let { (r, q) ->
                chordMarks(r, q, state.liveTuning, DISPLAY_FRETS, state.labelMode)
            }
            Highlight.Scale -> if (scalePc != null && scale != null)
                scaleMarks(scalePc, scale, state.liveTuning, DISPLAY_FRETS, state.labelMode)
                else null
            Highlight.None -> null
        } ?: emptyMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Fretboard", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        FretboardView(
            tuning = state.liveTuning,
            marks = marks,
            selectedPosition = state.selectedPosition,
            onTap = { state.tapPosition(it) },
            numFrets = DISPLAY_FRETS,
            leftHanded = state.leftHanded,
        )
        Spacer(Modifier.height(6.dp))
        SelectedPositionInfo(state.liveTuning, state.selectedPosition, parsedChord)

        Spacer(Modifier.height(12.dp))
        Text("Show on fretboard", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Highlight.entries.forEachIndexed { i, h ->
                SegmentedButton(
                    selected = h == state.highlight,
                    onClick = { state.highlight = h },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = Highlight.entries.size),
                    label = { Text(h.name) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Labels", style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LabelMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == state.labelMode,
                    onClick = { state.labelMode = m },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = LabelMode.entries.size),
                    label = { Text(m.name) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Tuning:  ${state.tuningName}${if (state.isEditedTuning) " (edited)" else ""}", style = MaterialTheme.typography.bodyMedium)
        val openStringsText = state.liveTuning.openStrings.joinToString(" ") { NoteSpeller.spell(it) }
        Text("Open strings (low → high):  $openStringsText", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordScreen(state: AppState, modifier: Modifier = Modifier) {
    val parsedChord = ChordLibrary.parse(state.chordInput)

    val shapes = remember(parsedChord, state.liveTuning, state.chordFretRange) {
        if (parsedChord == null) emptyList()
        else {
            val (root, q) = parsedChord
            shapeGen.shapesFor(root, q, state.liveTuning, frets = DISPLAY_FRETS, fretRange = state.chordFretRange).take(10)
        }
    }

    // Clear selection if shapes changed and index invalid
    val selectedShape = state.selectedShapeIndex?.let { i -> shapes.getOrNull(i) }

    val marks: Map<FretPosition, FretMark> = remember(selectedShape, parsedChord, state.liveTuning, state.labelMode, state.chordFretRange) {
        when {
            selectedShape != null -> shapeMarks(selectedShape, state.labelMode)
            parsedChord != null -> {
                val (r, q) = parsedChord
                chordMarks(r, q, state.liveTuning, DISPLAY_FRETS, state.labelMode, state.chordFretRange)
            }
            else -> emptyMap()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Chord", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        FretboardView(
            tuning = state.liveTuning,
            marks = marks,
            selectedPosition = state.selectedPosition,
            onTap = { state.tapPosition(it) },
            numFrets = DISPLAY_FRETS,
            leftHanded = state.leftHanded,
        )

        Spacer(Modifier.height(12.dp))
        val currentRoot = parsedChord?.first
        val currentQualitySymbol = parsedChord?.second?.symbol

        fun setChord(root: PitchClass, qualitySymbol: String) {
            state.chordInput = NoteSpeller.spell(root) + qualitySymbol
            state.selectedShapeIndex = null
        }

        Text("Root", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PITCH_CLASS_ROW.forEach { pc ->
                FilterChip(
                    selected = pc == currentRoot,
                    onClick = { setChord(pc, currentQualitySymbol ?: "") },
                    label = { Text(NoteSpeller.spell(pc)) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text("Quality", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COMMON_QUALITY_SYMBOLS.forEach { sym ->
                FilterChip(
                    selected = sym == currentQualitySymbol,
                    onClick = { setChord(currentRoot ?: PitchClass.C, sym) },
                    label = { Text(qualityLabel(sym)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.chordInput,
            onValueChange = { state.chordInput = it; state.selectedShapeIndex = null },
            label = { Text("Chord symbol — chips above edit this") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        if (parsedChord != null) {
            val (root, quality) = parsedChord
            val notes = quality.notesFrom(root).joinToString(" ") { NoteSpeller.spell(it) }
            val intervalsLine = quality.intervals.joinToString(" ") { intervalName(it) }
            Text("${NoteSpeller.spell(root)}${quality.symbol}:  $notes")
            Text("intervals:  $intervalsLine", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("(chord not recognized)", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
        FretRangeSlider(
            label = "Fret range",
            range = state.chordFretRange,
            onRangeChange = { state.chordFretRange = it; state.selectedShapeIndex = null }
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Playable shapes (${shapes.size})", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            if (selectedShape != null) {
                TextButton(onClick = { state.selectedShapeIndex = null }) { Text("Show all positions") }
            }
        }
        Spacer(Modifier.height(4.dp))
        shapes.forEachIndexed { i, shape ->
            ShapeCard(
                shape = shape,
                selected = state.selectedShapeIndex == i,
                onClick = {
                    state.selectedShapeIndex = if (state.selectedShapeIndex == i) null else i
                    state.playShape(shape)
                },
            )
            Spacer(Modifier.height(6.dp))
        }
        if (shapes.isEmpty() && parsedChord != null) {
            Text("(no shapes in this fret range)", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleScreen(state: AppState, modifier: Modifier = Modifier) {
    val scalePc = try { NoteSpeller.parsePitchClass(state.scaleRoot) } catch (_: Exception) { null }
    val scale = ScaleLibrary.scales[state.scaleType]

    val marks: Map<FretPosition, FretMark> = remember(scalePc, scale, state.liveTuning, state.labelMode, state.scaleFretRange) {
        if (scalePc != null && scale != null) {
            scaleMarks(scalePc, scale, state.liveTuning, DISPLAY_FRETS, state.labelMode, state.scaleFretRange)
        } else emptyMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Scale", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        FretboardView(
            tuning = state.liveTuning,
            marks = marks,
            selectedPosition = state.selectedPosition,
            onTap = { state.tapPosition(it) },
            numFrets = DISPLAY_FRETS,
            leftHanded = state.leftHanded,
        )

        Spacer(Modifier.height(12.dp))
        Text("Root", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PITCH_CLASS_ROW.forEach { pc ->
                val pcName = NoteSpeller.spell(pc)
                FilterChip(
                    selected = pcName == state.scaleRoot,
                    onClick = { state.scaleRoot = pcName },
                    label = { Text(pcName) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state.scaleRoot,
            onValueChange = { state.scaleRoot = it },
            label = { Text("Root — chips above edit this") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text("Scale", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScaleLibrary.scales.keys.forEach { name ->
                FilterChip(
                    selected = name == state.scaleType,
                    onClick = { state.scaleType = name },
                    label = { Text(name) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (scalePc != null && scale != null) {
            val notes = scale.notesFrom(scalePc).joinToString(" ") { NoteSpeller.spell(it) }
            val formula = scale.intervals.joinToString(" ") { intervalName(it) }
            Text("${state.scaleRoot} ${scale.name}:")
            Text("notes:    $notes", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Text("formula:  $formula", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("(invalid root or scale)", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
        FretRangeSlider(
            label = "Fret range",
            range = state.scaleFretRange,
            onRangeChange = { state.scaleFretRange = it }
        )

        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: AppState,
    customTunings: Map<String, Tuning>,
    modifier: Modifier = Modifier,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Left-handed", style = MaterialTheme.typography.titleMedium)
                Text("Mirror the fretboard horizontally", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = state.leftHanded,
                onCheckedChange = { state.toggleLeftHanded(it) },
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tuning", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            if (state.isEditedTuning) Text("(unsaved edits)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { editorOpen = !editorOpen }) {
                    Text(if (editorOpen) "Close editor" else "Customize…")
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tunings.all.forEach { (name, tuning) ->
                FilterChip(
                    selected = name == state.tuningName && !state.isEditedTuning,
                    onClick = { state.selectTuning(name, tuning) },
                    label = { Text(name) }
                )
            }
            customTunings.forEach { (name, tuning) ->
                FilterChip(
                    selected = name == state.tuningName && !state.isEditedTuning,
                    onClick = { state.selectTuning(name, tuning) },
                    label = { Text(name) },
                    trailingIcon = {
                        TextButton(
                            onClick = { state.deleteCustomTuning(name) },
                            modifier = Modifier.padding(start = 4.dp)
                        ) { Text("✕", style = MaterialTheme.typography.labelLarge) }
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        val openStringsText = state.liveTuning.openStrings.joinToString(" ") { NoteSpeller.spell(it) }
        Text("Open strings (low → high):  $openStringsText", fontFamily = FontFamily.Monospace)

        if (editorOpen) {
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Adjust each string", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    for (s in (state.liveTuning.stringCount - 1) downTo 0) {
                        val stringNumber = state.liveTuning.stringCount - s
                        val note = state.liveTuning.openStrings[s]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "S$stringNumber  ${NoteSpeller.spell(note).padEnd(4)}",
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(96.dp)
                            )
                            OutlinedButton(onClick = { state.adjustString(s, -1) }) { Text("−") }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { state.adjustString(s, +1) }) { Text("+") }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(onClick = { state.adjustString(s, -12) }) { Text("−oct") }
                            Spacer(Modifier.width(6.dp))
                            OutlinedButton(onClick = { state.adjustString(s, +12) }) { Text("+oct") }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = saveName,
                            onValueChange = { saveName = it },
                            label = { Text("Save as…") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { state.saveCustomTuning(saveName); saveName = "" },
                            enabled = saveName.trim().isNotEmpty() && '|' !in saveName && ';' !in saveName
                        ) { Text("Save") }
                    }
                    if (state.isEditedTuning) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { state.resetTuningToSaved(customTunings) }) { Text("Discard edits") }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FretRangeSlider(
    label: String,
    range: IntRange,
    onRangeChange: (IntRange) -> Unit,
) {
    var sliderRange by remember(range) { mutableStateOf(range.first.toFloat()..range.last.toFloat()) }
    Column {
        Text("$label:  ${sliderRange.start.toInt()}–${sliderRange.endInclusive.toInt()}", style = MaterialTheme.typography.bodyMedium)
        RangeSlider(
            value = sliderRange,
            onValueChange = { sliderRange = it },
            onValueChangeFinished = {
                onRangeChange(sliderRange.start.toInt()..sliderRange.endInclusive.toInt())
            },
            valueRange = 0f..DISPLAY_FRETS.toFloat(),
            steps = DISPLAY_FRETS - 1,
        )
    }
}

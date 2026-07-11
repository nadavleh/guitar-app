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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordShapeGenerator
import app.guitar.theory.ChordTypeLevel
import app.guitar.theory.EarTraining
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.ScaleLibrary
import app.guitar.theory.TrainingMode
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

// ---------- FRETBOARD SHEET (Chord / Scale / Strum in one) ----------

/**
 * Single "Fretboard" tool sheet. A segmented selector at the top chooses what the
 * neck displays — Chord, Scale, or Strum (pick) — and the body shows that mode's
 * controls. Replaces the former three separate Chord / Scale / Pick entries.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FretboardSheet(state: AppState) {
    SheetBody {
        SheetHeader("Fretboard", state)

        // "None" first: the board can be (and now starts) unlit — pick it to clear.
        val modes = listOf(DisplayMode.None, DisplayMode.Chord, DisplayMode.Scale, DisplayMode.Pick)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = state.displayMode == m,
                    onClick = { state.displayMode = m },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                    label = {
                        Text(when (m) {
                            DisplayMode.None -> "None"
                            DisplayMode.Scale -> "Scale"
                            DisplayMode.Pick -> "Strum"
                            else -> "Chord"
                        })
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when (state.displayMode) {
            DisplayMode.None -> Text(
                "Nothing lit — pick Chord, Scale or Strum to light the board.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DisplayMode.Scale -> ScaleControls(state)
            DisplayMode.Pick -> PickControls(state)
            else -> ChordControls(state)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { state.closeSheet() }) { Text("Done") }
        }
    }
}

// ---------- CHORD CONTROLS ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordControls(state: AppState) {
    val parsed = ChordLibrary.parse(state.chordInput)
    val currentRoot = parsed?.first
    val currentQualitySymbol = parsed?.second?.symbol

    Column {
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
    }
}

// ---------- SCALE CONTROLS ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleControls(state: AppState) {
    val scalePc = try { NoteSpeller.parsePitchClass(state.scaleRoot) } catch (_: Exception) { null }
    val scale = ScaleLibrary.scales[state.scaleType]

    Column {
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
    }
}

// ---------- STRUM (PICK) CONTROLS ----------

@Composable
private fun PickControls(state: AppState) {
    Column {
        Text(
            "Tap any fret on the neck to add or remove it from your selection, " +
                "mute whole strings below, then strum or arpeggiate the set.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text("Picked: ${state.pickedPositions.size}" +
            (if (state.mutedStrings.isNotEmpty()) "   ·   muted: ${state.mutedStrings.size}" else ""),
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("Mute strings", style = MaterialTheme.typography.labelMedium)
        StringMuteRow(state)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val canStrum = state.pickedPositions.any { it.stringIndex !in state.mutedStrings }
            Button(onClick = { state.strumPicked(arpeggio = false) }, enabled = canStrum) { Text("Strum") }
            OutlinedButton(onClick = { state.strumPicked(arpeggio = true) }, enabled = canStrum) { Text("Arpeggio") }
            OutlinedButton(onClick = { state.clearPicked() }, enabled = state.pickedPositions.isNotEmpty() || state.mutedStrings.isNotEmpty()) { Text("Clear") }
        }
    }
}

// ---------- SETTINGS SHEET (Sheet.Options) ----------

/** Settings → Personalize: the 5 ACT-accent swatches. Each circle is the
 *  [Accent]'s dark-theme color (the palette always shows dark swatches — same
 *  approach as a paint-chip picker — even though the live app may be in light
 *  theme); the selected swatch gets a ring so selection isn't color-only.
 *  Applies immediately via [AppState.setAccent] (no "Done" step). */
@Composable
private fun AccentRow(state: AppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Accent.entries.forEach { a ->
            val selected = a == state.accent
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { state.setAccent(a) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    )
                }
                Box(
                    Modifier
                        .size(if (selected) 34.dp else 40.dp)
                        .clip(CircleShape)
                        .background(a.dark)
                )
            }
        }
    }
}

private fun <T> List<T>.swapped(i: Int, j: Int): List<T> =
    toMutableList().also { val tmp = it[i]; it[i] = it[j]; it[j] = tmp }

/** Settings → Personalize: pick exactly 4 of the 6 [TabDest] candidates for the
 *  bottom tab bar/rail, and reorder the picked ones with up/down arrows.
 *
 * Editing happens on a *local* pending list, only committed to [AppState.setTabOrder]
 * (and thus persisted) once it holds exactly 4 entries — unchecking one of the 4
 * picked tabs is allowed (drops the local list to 3, "freeing a slot" per the
 * design) without ever pushing an invalid <4 set to the live tab bar; checking a
 * 5th candidate is simply disabled. `remember(state.tabOrder)` re-seeds the
 * pending list from the real order whenever it changes elsewhere (or on first
 * open), and quietly drops any transient 3-item edit if the sheet is dismissed
 * before a 4th is picked. */
@Composable
private fun TabOrderEditor(state: AppState) {
    var pending by remember(state.tabOrder) { mutableStateOf(state.tabOrder) }
    fun commit(next: List<TabDest>) {
        pending = next
        if (next.size == 4) state.setTabOrder(next)
    }

    Column {
        pending.forEachIndexed { idx, dest ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = true, onCheckedChange = { commit(pending - dest) })
                Icon(dest.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(dest.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { commit(pending.swapped(idx, idx - 1)) }, enabled = idx > 0,
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move ${dest.label} up")
                }
                IconButton(onClick = { commit(pending.swapped(idx, idx + 1)) }, enabled = idx < pending.size - 1,
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move ${dest.label} down")
                }
            }
        }
        val unpicked = TabDest.entries.filter { it !in pending }
        if (unpicked.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            unpicked.forEach { dest ->
                val canAdd = pending.size < 4
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = false, enabled = canAdd, onCheckedChange = { if (canAdd) commit(pending + dest) })
                    Icon(dest.icon, contentDescription = null,
                        tint = if (canAdd) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        dest.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (canAdd) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionsSheet(state: AppState, customTunings: Map<String, Tuning>) {
    var editorOpen by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    SheetBody {
        SheetHeader("Settings", state)

        // ----- Personalize -----
        SectionLabel("Personalize")
        Spacer(Modifier.height(8.dp))

        Text("Theme", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        SegmentedRow(
            options = ThemeMode.entries,
            selected = state.themeMode,
            onSelect = { state.setThemeMode(it) },
            label = { it.name },
        )

        Spacer(Modifier.height(14.dp))
        Text("Accent", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        AccentRow(state)

        Spacer(Modifier.height(14.dp))
        Text("Tabs & order", style = MaterialTheme.typography.labelMedium)
        Text(
            "Pick 4 tabs; everything else lives in More",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TabOrderEditor(state)

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Left-handed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.leftHanded, onCheckedChange = { state.toggleLeftHanded(it) })
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // ----- Instrument (unchanged) -----
        SectionLabel("Instrument")
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            app.guitar.theory.Instrument.entries.forEachIndexed { i, inst ->
                SegmentedButton(
                    selected = state.instrument == inst,
                    onClick = { state.setInstrument(inst) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = i,
                        count = app.guitar.theory.Instrument.entries.size,
                    ),
                    label = { Text(inst.displayName) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Tuning", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Show only the preset tunings appropriate to the current instrument
            Tunings.presetsFor(state.instrument).forEach { (name, t) ->
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

        SectionLabel("Behavior")
        Spacer(Modifier.height(8.dp))
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
            Column(modifier = Modifier.weight(1f)) {
                Text("Play note on touch-down", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Off (default): notes play on tap-release, so swiping the neck to " +
                        "see more frets won't sound a note. On: notes fire the instant you touch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.tapOnTouchDown,
                onCheckedChange = { state.setTapOnTouchDown(it) },
            )
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

        // ----- Tuner (A4 reference; unchanged) -----
        SectionLabel("Tuner")
        Spacer(Modifier.height(8.dp))
        Text("A4 reference: ${state.a4Hz.toInt()} Hz",
            style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = state.a4Hz,
            onValueChange = { state.setA4Hz(it) },
            valueRange = 435f..445f,
            steps = 9,  // 1 Hz increments
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
        // ----- Top: title + back / watch-on-neck -----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("LOOP",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f))
            // "Watch on neck": start playback (if not already) and jump to the main
            // fretboard so the sounding chords light up live. Don't stop the loop when
            // navigating away — the Stop button on the TransportDock below is the
            // explicit way to halt it.
            OutlinedButton(onClick = {
                if (!state.isLooping && state.loopHasChords) state.startLoop()
                state.closeSheet()
            }) {
                Text(if (state.isLooping || state.loopHasChords) "Watch on neck" else "Back")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ----- "Now playing / next" banner (purely derived from existing loop
        // state — no new fields) — shown only while the loop is actually running. -----
        if (state.isLooping) {
            val (currentChord, nextChord) = currentAndNextLoopChord(state)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    currentChord ?: "·",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (nextChord != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "next: $nextChord",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ----- Controls: slots/bar + bars (wrap). Tempo now lives in the TransportDock. -----
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

        // ----- "Build by degree" panel (collapsed by default) -----
        BuildByDegreePanel(state)

        Spacer(Modifier.height(6.dp))

        // ----- Main area: bar lane (left) + slot editor (right, when open) -----
        // Signal redesign: bars render as a horizontally-scrolling lane of chip/
        // card tiles (rather than a wrapped grid) with a dashed "+" tile at the end
        // that adds a bar via the exact same setBarCount() call the old "+" stepper
        // used. Per-bar/per-slot editing (tap a slot → SlotEditor) is unchanged.
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val barWidth = (state.slotsPerBar.coerceAtLeast(1) * 46 + 20).dp
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                for ((barIdx, bar) in state.loopProgression.withIndex()) {
                    BarCard(
                        barIdx = barIdx,
                        bar = bar,
                        isCurrentBar = state.isLooping && state.loopCurrentBar == barIdx,
                        currentSlot = state.loopCurrentSlot,
                        isLooping = state.isLooping,
                        onSlotTap = { slotIdx -> state.loopEditingSlot = barIdx to slotIdx },
                        editingSlot = state.loopEditingSlot,
                        modifier = Modifier.width(barWidth),
                    )
                }
                AddBarTile(
                    enabled = state.loopProgression.size < 16,
                    onClick = { state.setBarCount(state.loopProgression.size + 1) },
                    modifier = Modifier.width(barWidth),
                )
            }
            // Slot editor sits to the right of the bar lane when a slot is selected.
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

        Spacer(Modifier.height(8.dp))
        var toneSheetOpen by remember { mutableStateOf(false) }
        TransportDock(
            playing = state.isLooping,
            onPlayStop = {
                if (state.isLooping) state.stopLoop()
                // Preserve the old Play button's guard: don't start an empty loop.
                else if (state.loopHasChords) state.startLoop()
            },
            bpm = state.bpm,
            // state.bpm is re-read live every bar (see AppState.playBar()), so no
            // restart is needed here.
            onBpm = { state.bpm = it },
            toneLabel = state.sound.name,
            onTone = { toneSheetOpen = true },
        )
        if (toneSheetOpen) ToneSheet(state, onDismiss = { toneSheetOpen = false })
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
        // Playhead bar: act-bordered (per Signal spec), on top of the existing tint.
        border = if (isCurrentBar)
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null,
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

/** Dashed "+" tile at the end of the bar lane — adds a bar via the exact same
 *  [AppState.setBarCount] call the old "+" bars-stepper button used. */
@Composable
private fun AddBarTile(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (enabled) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add bar", tint = color)
    }
}

/** Current + next chord symbol for the "now playing" banner — a pure read of
 *  [AppState.loopProgression]/[AppState.loopCurrentBar]/[AppState.loopCurrentSlot];
 *  adds no new state. */
private fun currentAndNextLoopChord(state: AppState): Pair<String?, String?> {
    val bars = state.loopProgression
    if (bars.isEmpty()) return null to null
    val barIdx = state.loopCurrentBar.coerceIn(0, bars.lastIndex)
    val bar = bars[barIdx]
    if (bar.isEmpty()) return null to null
    val slotIdx = state.loopCurrentSlot.coerceIn(0, bar.lastIndex)
    val current = bar[slotIdx].chordSymbol
    val next = bar.getOrNull(slotIdx + 1)?.chordSymbol
        ?: bars[(barIdx + 1) % bars.size].firstOrNull()?.chordSymbol
    return current to next
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

/**
 * Compact panel that lets the user build a progression by Roman-numeral degree
 * rather than typing chord symbols. Key + Mode + Level + an optional quality
 * override drive the resolution; tapping a degree button writes the resolved
 * chord into the currently-editing slot (if any) or into the cursor-advancing
 * next bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildByDegreePanel(state: AppState) {
    val expanded = state.loopBuildExpanded
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Build by degree",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { state.loopBuildExpanded = !expanded }) {
                    Text(if (expanded) "Collapse ▲" else "Expand ▼")
                }
            }
            if (!expanded) return@Column

            Spacer(Modifier.height(4.dp))

            // ---- Key dropdown + Mode (decluttered from a 12-chip row) ----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Key", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                var keyOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { keyOpen = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text(NoteSpeller.spell(state.loopBuildKey) + " ▾") }
                    DropdownMenu(expanded = keyOpen, onDismissRequest = { keyOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Random") },
                            onClick = { state.setLoopBuildKeyRandom(); keyOpen = false },
                        )
                        for (i in 0..11) {
                            val pc = PitchClass(i)
                            DropdownMenuItem(
                                text = { Text(NoteSpeller.spell(pc)) },
                                onClick = { state.loopBuildKey = pc; keyOpen = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text("Mode", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                SingleChoiceSegmentedButtonRow {
                    listOf(TrainingMode.Major, TrainingMode.Minor).forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = state.loopBuildMode == m,
                            onClick = { state.loopBuildMode = m },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                            label = { Text(if (m == TrainingMode.Major) "Major" else "Minor") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ---- Diatonic level + quality override ----
            Text("Diatonic level", style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ChordTypeLevel.entries.forEachIndexed { i, lvl ->
                    SegmentedButton(
                        selected = state.loopBuildLevel == lvl && state.loopBuildOverride == null,
                        onClick = {
                            state.loopBuildLevel = lvl
                            state.loopBuildOverride = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ChordTypeLevel.entries.size),
                        label = { Text(lvl.displayName, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Override quality (replaces diatonic)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Most-useful chord-quality overrides for building a progression.
                val overrides = listOf("", "m", "aug", "7", "maj7", "m7", "m7b5", "dim7", "6", "m6", "9", "13", "sus4", "add9")
                for (sym in overrides) {
                    FilterChip(
                        selected = state.loopBuildOverride == sym,
                        onClick = {
                            state.loopBuildOverride = if (state.loopBuildOverride == sym) null else sym
                        },
                        label = { Text(if (sym.isEmpty()) "maj" else sym) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Cursor row + 7 degree buttons ----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val cursorLabel = if (state.loopEditingSlot != null)
                    "→ writes to selected slot"
                else
                    "→ writes to bar ${state.loopBuildCursor + 1} (auto-advances)"
                Text(cursorLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                if (state.loopEditingSlot == null) {
                    TextButton(onClick = { state.resetLoopBuildCursor() }) { Text("Reset to bar 1") }
                }
            }
            Spacer(Modifier.height(4.dp))
            val degreeInfo = if (state.loopBuildMode == TrainingMode.Major) EarTraining.MAJOR_DEGREES
                             else EarTraining.MINOR_DEGREES
            // Each row of buttons stacks Roman label + resolved chord preview.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in 1..7) {
                    val info = degreeInfo[d]
                    val roman = info?.roman ?: "?"
                    val rootPc = EarTraining.degreeRoot(state.loopBuildKey, d, state.loopBuildMode)
                    val q = state.loopBuildOverride ?: when (state.loopBuildLevel) {
                        ChordTypeLevel.Triads   -> info?.triadQuality ?: ""
                        ChordTypeLevel.Sevenths -> info?.seventhQuality ?: ""
                        ChordTypeLevel.Extended -> info?.extendedQuality ?: ""
                    }
                    val preview = NoteSpeller.spell(rootPc) + q
                    OutlinedButton(
                        onClick = { state.applyLoopDegree(d) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp, vertical = 6.dp,
                        ),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(roman, style = MaterialTheme.typography.titleSmall)
                            Text(preview,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
private fun SheetHeader(title: String, state: AppState) {
    var toneSheetOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // Sound/EQ/reverb settings, reachable everywhere — opens the shared ToneSheet.
        IconButton(onClick = { toneSheetOpen = true }) {
            Icon(Icons.Outlined.Tune, contentDescription = "Tone")
        }
    }
    Spacer(Modifier.height(8.dp))
    if (toneSheetOpen) ToneSheet(state, onDismiss = { toneSheetOpen = false })
}

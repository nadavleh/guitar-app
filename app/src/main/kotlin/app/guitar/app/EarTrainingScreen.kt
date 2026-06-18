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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.ChordTypeLevel
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.TrainingMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarTrainingScreen(state: AppState, onBack: () -> Unit) {
    // #7: use the app-lifetime instance so leaving and returning preserves state.
    val ear = state.earTraining
    // Stop audio/looping when leaving the screen, but keep all state (progression,
    // reveals, counters) so returning shows exactly what you left.
    DisposableEffect(Unit) { onDispose { ear.stopLoop() } }
    LaunchedEffect(Unit) {
        // NB: deliberately do NOT auto-generate a progression here. The user
        // wants the first progression to honor settings they pick beforehand,
        // so we show a "Generate progression" button instead. Note2Chord still
        // pre-generates because it has no settings to honor.
        if (ear.n2cChallenge == null) ear.nextN2cChallenge()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
    ) {
        // Title row: title + audio + back. Tabs get their own full-width row below
        // so nothing is squeezed in a narrow (portrait) column.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "EAR TRAINING",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            AudioQuickButton(state, compact = true)
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { ear.release(); onBack() }) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))
        // Compact selectors (tasks #1/#2): the sub-mode and the Practice/Challenge
        // mode are side-by-side dropdowns instead of a wrapping 5-chip grid plus a
        // full-width segmented bar, so the fixed header no longer eats the screen
        // and leaves the scrollable body the room it needs.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            SubModeDropdown(ear, modifier = Modifier.weight(1f))
            ModeDropdown(ear, modifier = Modifier.weight(1f))
        }

        // Progression sub-mode gets an "Advanced (non-diatonic)" toggle that swaps
        // the diatonic generator for the curated special-progression library.
        if (ear.progSubMode == EarSubMode.Progression) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Advanced (non-diatonic) progressions",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Borrowed chords, secondary dominants & jazz turnarounds, each with a note.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ear.advancedMode, onCheckedChange = {
                    ear.advancedMode = it
                    ear.stopLoop()
                })
            }
        }

        when (ear.progSubMode) {
            EarSubMode.Progression ->
                if (ear.advancedMode) {
                    if (ear.earMode == EarMode.Challenge) AdvancedChallengeView(ear)
                    else AdvancedProgressionView(ear)
                } else {
                    if (ear.earMode == EarMode.Challenge) ProgressionChallengeView(state, ear)
                    else ProgressionView(state, ear)
                }
            EarSubMode.Note2Chord ->
                if (ear.earMode == EarMode.Challenge) Note2ChordChallengeView(ear)
                else Note2ChordView(ear)
            EarSubMode.Flavor ->
                if (ear.earMode == EarMode.Challenge) FlavorChallengeView(ear)
                else FlavorView(ear)
            EarSubMode.Inversions ->
                if (ear.earMode == EarMode.Challenge) InversionsChallengeView(ear)
                else InversionsView(ear)
            EarSubMode.AugDim ->
                if (ear.earMode == EarMode.Challenge) AugDimChallengeView(ear)
                else AugDimView(ear)
        }
    }
}

private fun subModeLabel(s: EarSubMode): String = when (s) {
    EarSubMode.Progression -> "Progressions"
    EarSubMode.Note2Chord  -> "Note→Chord"
    EarSubMode.Flavor      -> "Flavor"
    EarSubMode.Inversions  -> "Inversions"
    EarSubMode.AugDim      -> "Aug / Dim"
}

/** Compact sub-mode picker (task #1/#2) — replaces the 5-chip wrapping grid. */
@Composable
private fun SubModeDropdown(ear: EarTrainingState, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(subModeLabel(ear.progSubMode) + "  ▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (s in EarSubMode.entries) {
                DropdownMenuItem(
                    text = { Text(subModeLabel(s)) },
                    onClick = { ear.switchTab(s); open = false },
                )
            }
        }
    }
}

/** Compact Practice/Challenge picker (task #1) — replaces the full-width segmented bar. */
@Composable
private fun ModeDropdown(ear: EarTrainingState, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text((if (ear.earMode == EarMode.Practice) "Practice" else "Challenge") + "  ▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (m in EarMode.entries) {
                DropdownMenuItem(
                    text = { Text(if (m == EarMode.Practice) "Practice" else "Challenge") },
                    onClick = { ear.earMode = m; open = false },
                )
            }
        }
    }
}

// -------- Progression view --------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionView(state: AppState, ear: EarTrainingState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ProgressionSettings(ear)

        Spacer(Modifier.height(12.dp))

        // Tempo + strum — full-width sliders so labels never get squeezed.
        TempoStrumSliders(state, ear)

        Spacer(Modifier.height(10.dp))

        if (!ear.hasGenerated) {
            // Initial state: prominent CTA. The user adjusts settings above, then
            // taps this to produce the first progression that honors them.
            Button(
                onClick = { ear.nextProgression() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Generate progression ▶", style = MaterialTheme.typography.titleMedium) }
            return@Column
        }

        // Transport + actions — wrap so nothing gets clipped in a narrow column.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ear.isLooping) {
                Button(onClick = { ear.stopLoop() }) { Text("Stop ⏹") }
            } else {
                Button(onClick = { ear.startLoop() }) { Text("Play ▶") }
            }
            OutlinedButton(onClick = { ear.nextProgression() }) { Text("Next →") }
            // #1: hear the tonic — plays I-V-I (or i-V-i) in the current key.
            OutlinedButton(onClick = { ear.playProgKeyCadence() }) { Text("Hear ${ear.progCadenceLabel()}") }
            // #2: push the current progression's chords into the Looper.
            OutlinedButton(onClick = {
                state.loadProgressionIntoLoop(ear.progResolved.map { it.symbol })
            }) { Text("→ Looper") }
        }

        Spacer(Modifier.height(16.dp))

        // KEY + MODE combined reveal — deliberately small / low-emphasis (the chord
        // labels are the focus; key+mode is a secondary hint).
        RevealCard(
            label = "Key & Mode",
            hidden = !ear.keyRevealed,
            content = NoteSpeller.spell(ear.progKey) + "  " +
                if (ear.progMode == TrainingMode.Major) "Major" else "Minor",
            onToggle = { ear.toggleKeyModeReveal() },
            modifier = Modifier.width(150.dp),
            contentSizeSp = 15,
        )

        Spacer(Modifier.height(12.dp))

        // Four chord-slot reveal cards (each with its own play button)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 4) {
                val resolved = ear.progResolved.getOrNull(i)
                val isCurrent = ear.isLooping && ear.currentBar == i
                ChordSlotCard(
                    barNumber = i + 1,
                    label = resolved?.romanLabel ?: "—",
                    hidden = i !in ear.progBarRevealed,
                    onToggle = { ear.toggleBarReveal(i) },
                    onPlay = { ear.playBarOnce(i) },
                    isPlaying = isCurrent,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- Show-on-fretboard switch + optional FretboardView ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Show chord on fretboard",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            Switch(checked = ear.showFretboard, onCheckedChange = { ear.showFretboard = it })
        }
        if (ear.showFretboard) {
            val shape = ear.currentPlayingShape ?: ear.lastShownShape
            val marks = remember(shape, state.labelMode) {
                shape?.let { shapeMarks(it, state.labelMode) } ?: emptyMap()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(vertical = 4.dp),
            ) {
                FretboardView(
                    tuning = state.liveTuning,
                    marks = marks,
                    selectedPosition = null,
                    onTap = { pos ->
                        // #1: tapping a fret plays that note so the user can
                        // check themselves against the progression.
                        val midi = Fretboard.noteAt(state.liveTuning, pos).midi.value
                        state.audio.playNote(midi, durationMillis = state.ringSustainMs)
                    },
                    numFrets = DISPLAY_FRETS,
                    leftHanded = state.leftHanded,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** "Random ▾" key picker that collapses the 12 fixed keys into a dropdown. */
@Composable
private fun KeyDropdown(ear: EarTrainingState) {
    var keyMenu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { keyMenu = true }) {
            Text((ear.fixedKey?.let { NoteSpeller.spell(it) } ?: "Random") + " ▾")
        }
        DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
            DropdownMenuItem(text = { Text("Random") }, onClick = { ear.fixedKey = null; keyMenu = false })
            for (i in 0..11) {
                val pc = PitchClass(i)
                DropdownMenuItem(
                    text = { Text("Fixed: " + NoteSpeller.spell(pc)) },
                    onClick = { ear.fixedKey = pc; keyMenu = false },
                )
            }
        }
    }
}

/** Full-width BPM + strum sliders, shared by the progression trainer & challenge. */
@Composable
private fun TempoStrumSliders(state: AppState, ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Tempo: ${ear.progBpm} BPM", style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = ear.progBpm.toFloat(),
            onValueChange = { ear.progBpm = it.toInt() },
            valueRange = 40f..200f,
        )
        Text(
            if (state.strumMs == 0) "Strum: struck at once" else "Strum: ${state.strumMs} ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Slider(
            value = state.strumMs.toFloat(),
            onValueChange = { state.setStrumMs(it.toInt()) },
            valueRange = 0f..150f,
        )
    }
}

/**
 * Vertically-stacked, full-width settings so nothing gets squeezed in a narrow
 * (portrait) column — each control owns a full row and segmented controls span
 * the width instead of wrapping their labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionSettings(ear: EarTrainingState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Key + which modes are in the pool.
        Text("Key & modes", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyDropdown(ear)
            FilterChip(
                selected = ear.includeMajor,
                onClick = { ear.includeMajor = !ear.includeMajor },
                label = { Text("Major") },
            )
            FilterChip(
                selected = ear.includeMinor,
                onClick = { ear.includeMinor = !ear.includeMinor },
                label = { Text("Minor") },
            )
        }

        // Chord type — full-width segmented control.
        Column {
            Text("Chord type", style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ChordTypeLevel.entries.forEachIndexed { i, lvl ->
                    SegmentedButton(
                        selected = ear.chordTypeLevel == lvl && !ear.earMixAll,
                        onClick = { ear.chordTypeLevel = lvl; ear.reresolveCurrent() },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ChordTypeLevel.entries.size),
                        label = { Text(lvl.displayName, maxLines = 1) },
                    )
                }
            }
        }

        // Voicing style + mix-and-match.
        Column {
            Text("Voicing", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !ear.earShellVoicing && !ear.earMixAll,
                    onClick = { ear.earShellVoicing = false; ear.earMixAll = false },
                    label = { Text("Standard") },
                )
                FilterChip(
                    selected = ear.earShellVoicing && !ear.earMixAll,
                    onClick = { ear.earShellVoicing = true; ear.earMixAll = false },
                    label = { Text("Shell") },
                )
                FilterChip(
                    selected = ear.earMixAll,
                    onClick = { ear.earMixAll = !ear.earMixAll; ear.reresolveCurrent() },
                    label = { Text("Mix all") },
                )
            }
        }
    }
}

@Composable
private fun RevealCard(
    label: String,
    hidden: Boolean,
    content: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentSizeSp: Int = 32,
) {
    val bg = if (hidden) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
             else MaterialTheme.colorScheme.tertiaryContainer
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (hidden) "tap to reveal" else content,
                fontSize = if (hidden) 14.sp else contentSizeSp.sp,
                fontWeight = if (hidden) FontWeight.Normal else FontWeight.SemiBold,
                color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun ChordSlotCard(
    barNumber: Int,
    label: String,
    hidden: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        hidden -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Bar $barNumber",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            // Single line: short "tap" placeholder when hidden so the narrow card
            // (4-across in portrait) never wraps the text into a column.
            Text(
                if (hidden) "tap" else label,
                fontSize = if (hidden) 13.sp else 26.sp,
                fontWeight = if (hidden) FontWeight.Normal else FontWeight.SemiBold,
                color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onPlay,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp, vertical = 2.dp),
            ) { Text("▶") }
        }
    }
}

// -------- Note2Chord view --------

@Composable
private fun Note2ChordView(ear: EarTrainingState) {
    val c = ear.n2cChallenge
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "A triad plays, then a single note from its diatonic scale sounds above. " +
                "Identify the test note's degree relative to the chord (e.g. 9, b7, maj7).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { ear.playN2c() },
                enabled = !ear.n2cPlaying,
            ) { Text(if (ear.n2cPlaying) "Playing…" else "Play both ▶") }
            OutlinedButton(onClick = {
                ear.nextN2cChallenge()
                ear.playN2c()
            }) { Text("Next →") }
        }
        Spacer(Modifier.height(8.dp))
        // #2: audition the chord and the test note independently.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { ear.playN2cChord() }) { Text("♪ Chord") }
            OutlinedButton(onClick = { ear.playN2cNote() }) { Text("• Note") }
        }

        Spacer(Modifier.height(14.dp))

        // Compact reveal card: ~half the previous height, half-width.
        Card(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { ear.toggleN2cReveal() },
            colors = CardDefaults.cardColors(
                containerColor = if (ear.n2cRevealed) MaterialTheme.colorScheme.tertiaryContainer
                                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Answer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                if (c == null) {
                    Text("(no challenge yet)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!ear.n2cRevealed) {
                    Text(
                        "tap to reveal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        c.answerLabel,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        "${c.chordSymbol}  ·  test note: ${c.testNoteName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        // Bottom breathing room so the card never abuts the system gesture bar.
        Spacer(Modifier.height(20.dp))
    }
}

// -------- Chord Flavor view (#5) --------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlavorView(ear: EarTrainingState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
    ) {
        Text(
            "Pick which flavors can appear. Tap \"New chord\" — a cadence (I–V–I in major, " +
                "i–V–i in minor) plays to set the key, then a random diatonic chord sounds. " +
                "Identify its scale degree and flavor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Text("Allowed flavors", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (sym in ear.flavorPalette) {
                FilterChip(
                    selected = sym in ear.flavorAllowed,
                    onClick = { ear.toggleFlavorAllowed(sym) },
                    label = { Text(if (sym.isEmpty()) "maj" else sym) },
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        // Mode selection: which key-center modes may appear.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Modes", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(12.dp))
            Text("Major", style = MaterialTheme.typography.bodySmall)
            Switch(checked = ear.flavorIncludeMajor, onCheckedChange = { ear.flavorIncludeMajor = it })
            Spacer(Modifier.width(8.dp))
            Text("Minor", style = MaterialTheme.typography.bodySmall)
            Switch(checked = ear.flavorIncludeMinor, onCheckedChange = { ear.flavorIncludeMinor = it })
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { ear.newFlavorChallenge() }, enabled = !ear.flavorPlaying) {
                Text(if (ear.flavorPlaying) "Playing…" else "New chord ▶")
            }
            OutlinedButton(
                onClick = { ear.replayFlavorCadence() },
                enabled = ear.flavorStarted && !ear.flavorPlaying,
            ) { Text("Replay ${ear.flavorCadenceLabel()}") }
            OutlinedButton(onClick = { ear.playFlavorChord() }, enabled = ear.flavorStarted) {
                Text("Play chord")
            }
        }

        if (!ear.flavorStarted) return@Column

        Spacer(Modifier.height(14.dp))

        Text("Degree  (tap to hear & compare)", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (deg in 1..7) {
                FilterChip(
                    selected = ear.flavorGuessDegree == deg,
                    onClick = { ear.flavorGuessDegree = deg; ear.auditionFlavorDegree(deg) },
                    label = { Text("$deg") },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Flavor  (tap to hear)", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (sym in ear.flavorAllowed.toList()) {
                FilterChip(
                    selected = ear.flavorGuessQuality == sym,
                    onClick = { ear.flavorGuessQuality = sym; ear.auditionFlavorQuality(sym) },
                    label = { Text(if (sym.isEmpty()) "maj" else sym) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val degOk = ear.flavorGuessDegree == ear.flavorDegree
        val qualOk = ear.flavorGuessQuality == ear.flavorQuality
        Card(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { ear.toggleFlavorReveal() },
            colors = CardDefaults.cardColors(
                containerColor = if (ear.flavorRevealed) MaterialTheme.colorScheme.tertiaryContainer
                                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Answer", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                if (!ear.flavorRevealed) {
                    Text("tap to reveal", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "Degree ${ear.flavorDegree} (${ear.flavorDegreeRoman()})  ·  " +
                            (if (ear.flavorQuality.isEmpty()) "maj" else ear.flavorQuality),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(ear.flavorChordSymbol(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        "in ${NoteSpeller.spell(ear.flavorKey)} " +
                            if (ear.flavorMode == TrainingMode.Major) "major" else "minor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    if (ear.flavorGuessDegree != null || ear.flavorGuessQuality != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "you: degree ${if (degOk) "✔" else "✘"}  ·  flavor ${if (qualOk) "✔" else "✘"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// -------- Per-tab Challenge views (#3) --------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Note2ChordChallengeView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        if (!ear.n2cChActive) {
            Text("Identify the test note's degree over the chord. ${ear.n2cChallengeTotal} rounds, scored.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { ear.startN2cChallenge() }) { Text("Start challenge ▶") }
            }
            return@Column
        }
        if (ear.n2cChIndex >= ear.n2cChallengeTotal) {
            SimpleDoneCard(ear.n2cChScore, ear.n2cChallengeTotal,
                onRestart = { ear.startN2cChallenge() }, onExit = { ear.exitN2cChallenge() })
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Question ${ear.n2cChIndex + 1} / ${ear.n2cChallengeTotal}",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Score: ${ear.n2cChScore}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { ear.startN2cChallenge() }) { Text("Restart") }
            TextButton(onClick = { ear.exitN2cChallenge() }) { Text("Quit") }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { ear.playN2c() }, enabled = !ear.n2cPlaying) {
                Text(if (ear.n2cPlaying) "Playing…" else "Replay both ▶")
            }
            OutlinedButton(onClick = { ear.playN2cChord() }) { Text("♪ Chord") }
            OutlinedButton(onClick = { ear.playN2cNote() }) { Text("• Note") }
        }
        Spacer(Modifier.height(12.dp))
        val guess = ear.n2cChGuess
        val correct = ear.n2cChallenge?.answerLabel
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (opt in ear.n2cAnswerOptions()) {
                FilterChip(
                    selected = guess == opt || (guess != null && opt == correct),
                    enabled = guess == null,
                    onClick = { ear.guessN2c(opt) },
                    label = { Text(opt) },
                )
            }
        }
        if (guess != null) {
            Spacer(Modifier.height(8.dp))
            Text(if (guess == correct) "✔ correct" else "✘ answer: $correct",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ear.advanceN2cChallenge() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ear.n2cChIndex == ear.n2cChallengeTotal - 1) "See score →" else "Next →")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlavorChallengeView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        if (!ear.flavorChActive) {
            Text("${ear.flavorChallengeTotal} rounds. A cadence sets the key, then a random chord " +
                "plays — identify its degree and flavor.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Allowed flavors", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (sym in ear.flavorPalette) {
                    FilterChip(selected = sym in ear.flavorAllowed,
                        onClick = { ear.toggleFlavorAllowed(sym) },
                        label = { Text(if (sym.isEmpty()) "maj" else sym) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Modes", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(12.dp))
                Text("Major", style = MaterialTheme.typography.bodySmall)
                Switch(checked = ear.flavorIncludeMajor, onCheckedChange = { ear.flavorIncludeMajor = it })
                Spacer(Modifier.width(8.dp))
                Text("Minor", style = MaterialTheme.typography.bodySmall)
                Switch(checked = ear.flavorIncludeMinor, onCheckedChange = { ear.flavorIncludeMinor = it })
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { ear.startFlavorChallenge() }) { Text("Start challenge ▶") }
            }
            return@Column
        }
        if (ear.flavorChIndex >= ear.flavorChallengeTotal) {
            SimpleDoneCard(ear.flavorChScore, ear.flavorChallengeTotal,
                onRestart = { ear.startFlavorChallenge() }, onExit = { ear.exitFlavorChallenge() })
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Round ${ear.flavorChIndex + 1} / ${ear.flavorChallengeTotal}",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Score: ${ear.flavorChScore}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { ear.startFlavorChallenge() }) { Text("Restart") }
            TextButton(onClick = { ear.exitFlavorChallenge() }) { Text("Quit") }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { ear.replayFlavorCadence() }, enabled = !ear.flavorPlaying) {
                Text("Replay ${ear.flavorCadenceLabel()}")
            }
            OutlinedButton(onClick = { ear.playFlavorChord() }) { Text("Play chord") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Degree  (tap to hear & compare)", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (deg in 1..7) {
                FilterChip(selected = ear.flavorGuessDegree == deg, enabled = !ear.flavorChAnswered,
                    onClick = { ear.flavorGuessDegree = deg; ear.auditionFlavorDegree(deg) }, label = { Text("$deg") })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Flavor  (tap to hear)", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (sym in ear.flavorAllowed.toList()) {
                FilterChip(selected = ear.flavorGuessQuality == sym, enabled = !ear.flavorChAnswered,
                    onClick = { ear.flavorGuessQuality = sym; ear.auditionFlavorQuality(sym) },
                    label = { Text(if (sym.isEmpty()) "maj" else sym) })
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!ear.flavorChAnswered) {
            Button(onClick = { ear.submitFlavorGuess() },
                enabled = ear.flavorGuessDegree != null && ear.flavorGuessQuality != null,
                modifier = Modifier.fillMaxWidth()) { Text("Submit") }
        } else {
            val degOk = ear.flavorGuessDegree == ear.flavorDegree
            val qualOk = ear.flavorGuessQuality == ear.flavorQuality
            Text(
                "Answer: degree ${ear.flavorDegree} (${ear.flavorDegreeRoman()}) · " +
                    (if (ear.flavorQuality.isEmpty()) "maj" else ear.flavorQuality) +
                    "  [${ear.flavorChordSymbol()}, ${if (ear.flavorMode == TrainingMode.Major) "major" else "minor"}]",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("you: degree ${if (degOk) "✔" else "✘"} · flavor ${if (qualOk) "✔" else "✘"}",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ear.advanceFlavorChallenge() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ear.flavorChIndex == ear.flavorChallengeTotal - 1) "See score →" else "Next →")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Generic score screen for the single-answer challenges (Note2Chord, Flavor). */
@Composable
private fun SimpleDoneCard(score: Int, total: Int, onRestart: () -> Unit, onExit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Challenge complete!", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text("$score / $total", fontSize = 64.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestart) { Text("Restart") }
                OutlinedButton(onClick = onExit) { Text("Exit") }
            }
        }
    }
}

// -------- Progression Challenge view --------

/**
 * Auto-scored 15-question quiz. Each question is a fresh random progression
 * generated under the same settings as the Progressions sub-mode (Major/Minor
 * include flags + Triads / 7ths / Extended). For each bar the user taps the
 * correct Roman numeral (and extension, when the level has one); the question
 * scores a point only if all four bars are right. After 15 questions a final
 * score screen is shown with a Restart button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionChallengeView(state: AppState, ear: EarTrainingState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!ear.challengeActive) {
            // ---- title / config screen ----
            Text(
                "A challenge is 15 progressions in a row. Listen, then tap the correct " +
                    "Roman numeral for each bar (and its extension when shown). Each " +
                    "question auto-scores; your total appears at the end.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            ProgressionSettings(ear)

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Button(onClick = { ear.startChallenge() }) {
                    Text("Start challenge ▶", style = MaterialTheme.typography.titleMedium)
                }
            }
            return@Column
        }

        // ---- done screen (after Q15 advance) ----
        if (ear.challengeIndex >= ear.challengeTotal) {
            val highScores by state.challengeScores.collectAsState(initial = emptyList())
            ChallengeDoneCard(
                score = ear.challengeBarScore,
                total = ear.challengeBarTotal,
                durationMs = ear.challengeDurationMs,
                answers = ear.challengeAnswers,
                highScores = highScores,
                onRestart = { ear.startChallenge() },
                onExit = { ear.exitChallenge() },
            )
            return@Column
        }

        // ---- in-flight question screen ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Question ${ear.challengeIndex + 1} / ${ear.challengeTotal}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Score: ${ear.challengeBarScore} bars",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { ear.startChallenge() }) { Text("Restart") }
            TextButton(onClick = { ear.exitChallenge() }) { Text("Quit") }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ear.isLooping) {
                Button(onClick = { ear.stopLoop() }) { Text("Stop ⏹") }
            } else {
                Button(onClick = { ear.startLoop() }) { Text("Play ▶") }
            }
            OutlinedButton(onClick = { ear.playProgKeyCadence() }) { Text("Hear ${ear.progCadenceLabel()}") }
            OutlinedButton(onClick = { ear.rerollChallengeQuestion() }) { Text("Re-roll") }
        }

        Spacer(Modifier.height(8.dp))

        // #1: BPM control inside the challenge (mirrors the Practice transport).
        Column {
            Text("BPM: ${ear.progBpm}", style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.Slider(
                value = ear.progBpm.toFloat(),
                onValueChange = { ear.progBpm = it.toInt() },
                valueRange = 40f..200f,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Small optional key/mode hint (same low-emphasis chip as the trainer).
        RevealCard(
            label = "Key & Mode (hint)",
            hidden = !ear.keyRevealed,
            content = NoteSpeller.spell(ear.progKey) + "  " +
                if (ear.progMode == TrainingMode.Major) "Major" else "Minor",
            onToggle = { ear.toggleKeyModeReveal() },
            modifier = Modifier.width(170.dp),
            contentSizeSp = 15,
        )

        Spacer(Modifier.height(10.dp))

        // #2: dedicated reference palette — these (and the per-bar ▶ Play) are the
        // ONLY things that make sound. The answer chips below just select, so you
        // compare candidates here rather than accidentally hearing your guess.
        Text("Hear the degrees  (reference — plays in the hidden key)",
            style = MaterialTheme.typography.labelMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((deg, label) in ear.challengeReferenceLabels()) {
                OutlinedButton(
                    onClick = { ear.auditionProgDegree(deg) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp, vertical = 4.dp),
                ) { Text("▶ $label") }
            }
        }

        Spacer(Modifier.height(10.dp))

        // #9/#3: per-bar answering. In fixed-7ths mode each degree maps to one
        // diatonic 7th, so a single combined choice ("V7") encodes both degree and
        // extension; otherwise pick the Roman numeral (plus extension when the
        // level has one). Each bar auto-scores; selecting never plays a chord.
        val combined = ear.challengeCombinedMode
        val degreeOptions = ear.challengeDegreeOptions()
        val combinedOptions = ear.challengeCombinedOptions()
        val extOptions = ear.challengeExtOptions()
        for (i in 0 until 4) {
            val verdict = ear.challengeBarCorrect(i)   // null / true / false
            val barBg = when (verdict) {
                true  -> MaterialTheme.colorScheme.primaryContainer
                false -> MaterialTheme.colorScheme.errorContainer
                null  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(containerColor = barBg),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bar ${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { ear.playBarOnce(i) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp, vertical = 2.dp),
                        ) { Text("▶ Play") }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (combined) {
                        // Single combined diatonic-7th choice (sets degree + extension).
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((deg, label) in combinedOptions) {
                                FilterChip(
                                    selected = ear.challengeGuessDegree.getOrNull(i) == deg,
                                    onClick = { ear.guessChallengeCombined(i, deg) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((deg, roman) in degreeOptions) {
                                FilterChip(
                                    selected = ear.challengeGuessDegree.getOrNull(i) == deg,
                                    // Selecting no longer plays — use the reference palette above to compare.
                                    onClick = { ear.guessChallengeDegree(i, deg) },
                                    label = { Text(roman) },
                                )
                            }
                        }
                        if (ear.challengeNeedsExt && extOptions.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("extension",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (ext in extOptions) {
                                    FilterChip(
                                        selected = ear.challengeGuessExt.getOrNull(i) == ext,
                                        onClick = { ear.guessChallengeExt(i, ext) },
                                        label = { Text(if (ext.isEmpty()) "none" else ext) },
                                    )
                                }
                            }
                        }
                    }
                    if (verdict != null) {
                        Spacer(Modifier.height(4.dp))
                        val answer = ear.progResolved.getOrNull(i)?.romanLabel ?: ""
                        Text(
                            if (verdict) "✔ $answer" else "✘ answer: $answer",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // #4: always allowed — any bars you haven't answered are credited as correct.
        Button(
            onClick = { ear.advanceChallenge() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ear.challengeIndex == ear.challengeTotal - 1) "See score →" else "Next question →")
        }
        Text(
            "Unanswered bars count as correct.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(12.dp))

        // Optional fretboard: re-uses the same toggle as Progressions sub-mode.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Show chord on fretboard",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            Switch(checked = ear.showFretboard, onCheckedChange = { ear.showFretboard = it })
        }
        if (ear.showFretboard) {
            val shape = ear.currentPlayingShape ?: ear.lastShownShape
            val marks = remember(shape, state.labelMode) {
                shape?.let { shapeMarks(it, state.labelMode) } ?: emptyMap()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(vertical = 4.dp),
            ) {
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
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeDoneCard(
    score: Int,
    total: Int,
    durationMs: Long,
    answers: List<Boolean?>,
    highScores: List<app.guitar.app.ChallengeScore>,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Challenge complete!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(
                "$score / $total",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "bars correct  ·  ${formatDuration(durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            // Per-question dot strip (wraps so all 15 fit on a narrow screen)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for ((i, a) in answers.withIndex()) {
                    val color = when (a) {
                        true  -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null  -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(18.dp)
                            .background(color, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // ---- High-score table (best first; ties broken by faster time) ----
            // The persisted write is async, so the flow may not include this run on
            // the first frame — merge it in locally so it always shows immediately.
            val shown = remember(highScores, score, durationMs) {
                if (highScores.any { it.score == score && it.durationMs == durationMs }) highScores
                else (highScores + ChallengeScore(score, total, durationMs, System.currentTimeMillis()))
                    .sortedWith(CHALLENGE_SCORE_ORDER)
            }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                Text("High scores",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                // Mark the row that matches this run (same score + duration) as "you".
                var highlighted = false
                shown.take(5).forEachIndexed { rank, hs ->
                    val isThisRun = !highlighted && hs.score == score && hs.durationMs == durationMs
                    if (isThisRun) highlighted = true
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${rank + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isThisRun) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.width(24.dp))
                        Text("${hs.score}/${hs.total}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isThisRun) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.width(56.dp))
                        Text(formatDuration(hs.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isThisRun) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.width(56.dp))
                        Text(formatScoreDate(hs.dateMillis) + if (isThisRun) "  ← you" else "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isThisRun) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestart) { Text("Restart") }
                OutlinedButton(onClick = onExit) { Text("Exit") }
            }
        }
    }
}

/** "m:ss" wall-clock duration. */
private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** Short local date+time for a high-score row, e.g. "Jun 16, 14:32". */
private fun formatScoreDate(millis: Long): String =
    java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

// ======================================================================================
// #2  Advanced (non-diatonic) progressions
// ======================================================================================

/** Shared body: per-chord play buttons, a reveal card (name + Roman + chords), and
 *  the always-visible teaching explanation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedProgressionBody(ear: EarTrainingState) {
    val np = ear.advProg ?: return
    Text("Chords  (tap ▶ to hear each)", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in ear.progResolved.indices) {
            OutlinedButton(
                onClick = { ear.playProgChordDirect(i) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) { Text(if (ear.advRevealed) "▶ ${ear.progResolved[i].romanLabel}" else "▶ ${i + 1}") }
        }
    }
    Spacer(Modifier.height(10.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { ear.toggleAdvReveal() },
        colors = CardDefaults.cardColors(
            containerColor = if (ear.advRevealed) MaterialTheme.colorScheme.tertiaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Answer", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            if (!ear.advRevealed) {
                Text("tap to reveal", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(np.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(np.romanLine, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(ear.progResolved.joinToString("   ") { it.symbol },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("in ${NoteSpeller.spell(ear.progKey)} " +
                    if (ear.progMode == TrainingMode.Major) "major" else "minor",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    // Teaching note — always visible (the user wants the explanation shown while quizzing).
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("About this progression", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(2.dp))
            Text(np.explanation, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedProgressionView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Borrowed chords, secondary dominants and chromatic moves. Pick a key, generate one, " +
            "try to identify it, then reveal the name, Roman numerals and chords.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Key", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            KeyDropdown(ear)
        }
        Spacer(Modifier.height(10.dp))
        if (ear.advProg == null) {
            Button(onClick = { ear.nextAdvancedProgression() }, modifier = Modifier.fillMaxWidth()) {
                Text("Generate progression ▶", style = MaterialTheme.typography.titleMedium)
            }
            return@Column
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ear.isLooping) Button(onClick = { ear.stopLoop() }) { Text("Stop ⏹") }
            else Button(onClick = { ear.startLoop() }) { Text("Play ▶") }
            OutlinedButton(onClick = { ear.nextAdvancedProgression() }) { Text("Next →") }
        }
        Spacer(Modifier.height(12.dp))
        AdvancedProgressionBody(ear)
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedChallengeView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!ear.advChActive) {
            Text("${ear.advChallengeTotal} advanced progressions in a row. Listen, try to identify each, " +
                "then reveal and mark yourself. A teaching note is shown for every one.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Key", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp)); KeyDropdown(ear)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { ear.startAdvChallenge() }) { Text("Start challenge ▶") }
            }
            return@Column
        }
        if (ear.advChIndex >= ear.advChallengeTotal) {
            SimpleDoneCard(ear.advChScore, ear.advChallengeTotal,
                onRestart = { ear.startAdvChallenge() }, onExit = { ear.exitAdvChallenge() })
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Progression ${ear.advChIndex + 1} / ${ear.advChallengeTotal}",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Score: ${ear.advChScore}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { ear.startAdvChallenge() }) { Text("Restart") }
            TextButton(onClick = { ear.exitAdvChallenge() }) { Text("Quit") }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ear.isLooping) Button(onClick = { ear.stopLoop() }) { Text("Stop ⏹") }
            else Button(onClick = { ear.startLoop() }) { Text("Play ▶") }
        }
        Spacer(Modifier.height(12.dp))
        AdvancedProgressionBody(ear)
        Spacer(Modifier.height(12.dp))
        if (!ear.advChMarked) {
            Text("Reveal the answer, then mark yourself:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ear.markAdv(true) }, enabled = ear.advRevealed) { Text("✔ I got it") }
                OutlinedButton(onClick = { ear.markAdv(false) }, enabled = ear.advRevealed) { Text("✘ Missed") }
            }
        } else {
            Button(onClick = { ear.advanceAdvChallenge() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ear.advChIndex == ear.advChallengeTotal - 1) "See score →" else "Next →")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ======================================================================================
// #3  Inversions trainer
// ======================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InversionQualityPalette(ear: EarTrainingState) {
    Text("Chord types", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (sym in ear.invPalette) {
            FilterChip(
                selected = sym in ear.invAllowed,
                onClick = { ear.toggleInvAllowed(sym) },
                label = { Text(if (sym.isEmpty()) "maj" else sym) },
            )
        }
    }
}

/** Inversion guess chips (root / 1st / 2nd / 3rd …). Tapping auditions that inversion. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InversionGuessChips(ear: EarTrainingState, enabled: Boolean) {
    Text("Which inversion?  (tap to hear & compare)", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (k in 0 until ear.invCount()) {
            FilterChip(
                selected = ear.invGuess == k,
                enabled = enabled,
                onClick = { ear.invGuess = k; ear.auditionInversion(k) },
                label = { Text(app.guitar.theory.Inversions.name(k)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InversionsView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("A chord plays in some inversion (which chord tone is in the bass). Identify it. " +
            "Pick which chord types can appear below.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        InversionQualityPalette(ear)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ear.newInversion() }, enabled = !ear.invPlaying) {
                Text(if (ear.invPlaying) "Playing…" else "New chord ▶")
            }
            OutlinedButton(onClick = { ear.playInversion() }, enabled = ear.invStarted) { Text("Replay") }
        }
        if (!ear.invStarted) return@Column
        Spacer(Modifier.height(14.dp))
        InversionGuessChips(ear, enabled = true)
        Spacer(Modifier.height(12.dp))
        RevealCard(
            label = "Answer",
            hidden = !ear.invRevealed,
            content = app.guitar.theory.Inversions.name(ear.invInversion) + "  ·  " +
                NoteSpeller.spell(ear.invRoot) + (if (ear.invQuality.isEmpty()) "" else ear.invQuality),
            onToggle = { ear.toggleInvReveal() },
            modifier = Modifier.fillMaxWidth(),
            contentSizeSp = 20,
        )
        if (ear.invRevealed && ear.invGuess != null) {
            Spacer(Modifier.height(6.dp))
            Text(if (ear.invGuess == ear.invInversion) "✔ correct" else "✘ that was the ${app.guitar.theory.Inversions.name(ear.invGuess!!).lowercase()}",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InversionsChallengeView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!ear.invChActive) {
            Text("${ear.invChallengeTotal} rounds. A chord plays in an inversion — identify which. " +
                "Choose which chord types can appear:",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            InversionQualityPalette(ear)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { ear.startInvChallenge() }) { Text("Start challenge ▶") }
            }
            return@Column
        }
        if (ear.invChIndex >= ear.invChallengeTotal) {
            SimpleDoneCard(ear.invChScore, ear.invChallengeTotal,
                onRestart = { ear.startInvChallenge() }, onExit = { ear.exitInvChallenge() })
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Round ${ear.invChIndex + 1} / ${ear.invChallengeTotal}",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Score: ${ear.invChScore}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { ear.startInvChallenge() }) { Text("Restart") }
            TextButton(onClick = { ear.exitInvChallenge() }) { Text("Quit") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { ear.playInversion() }) { Text("Replay ▶") }
        Spacer(Modifier.height(12.dp))
        InversionGuessChips(ear, enabled = !ear.invChAnswered)
        Spacer(Modifier.height(10.dp))
        if (!ear.invChAnswered) {
            Button(onClick = { ear.submitInvGuess() }, enabled = ear.invGuess != null,
                modifier = Modifier.fillMaxWidth()) { Text("Submit") }
        } else {
            val ok = ear.invGuess == ear.invInversion
            Text((if (ok) "✔ correct" else "✘ answer: ${app.guitar.theory.Inversions.name(ear.invInversion)}") +
                "   (${NoteSpeller.spell(ear.invRoot)}${if (ear.invQuality.isEmpty()) "" else ear.invQuality})",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ear.advanceInvChallenge() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ear.invChIndex == ear.invChallengeTotal - 1) "See score →" else "Next →")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ======================================================================================
// #4  Augmented vs Diminished trainer
// ======================================================================================

private fun augDimLabel(sym: String): String = when (sym) {
    "aug" -> "Augmented (+)"
    "dim" -> "Diminished (°)"
    "dim7" -> "dim7 (°7)"
    "m7b5" -> "m7♭5 (half-dim ø)"
    "7#5" -> "7♯5 (aug 7th)"
    "maj7#5" -> "maj7♯5"
    else -> sym
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugDimPalette(ear: EarTrainingState) {
    Text("Chord types", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (sym in ear.augDimPalette) {
            FilterChip(
                selected = sym in ear.augDimAllowed,
                onClick = { ear.toggleAugDimAllowed(sym) },
                label = { Text(augDimLabel(sym)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugDimGuessChips(ear: EarTrainingState, enabled: Boolean) {
    Text("Which chord?  (tap to hear & compare)", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (sym in ear.augDimPalette.filter { it in ear.augDimAllowed }) {
            FilterChip(
                selected = ear.adGuess == sym,
                enabled = enabled,
                onClick = { ear.adGuess = sym; ear.auditionAugDim(sym) },
                label = { Text(augDimLabel(sym)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugDimView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Tell augmented from diminished by ear. Enable the qualities you want to drill " +
            "(add 7th/extended forms below), then identify each chord.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        AugDimPalette(ear)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ear.newAugDim() }) { Text("New chord ▶") }
            OutlinedButton(onClick = { ear.playAugDim() }, enabled = ear.adStarted) { Text("Replay") }
        }
        if (!ear.adStarted) return@Column
        Spacer(Modifier.height(14.dp))
        AugDimGuessChips(ear, enabled = true)
        Spacer(Modifier.height(12.dp))
        RevealCard(
            label = "Answer",
            hidden = !ear.adRevealed,
            content = NoteSpeller.spell(ear.adRoot) + ear.adQuality + "  ·  " + ear.augDimFamily(ear.adQuality),
            onToggle = { ear.toggleAugDimReveal() },
            modifier = Modifier.fillMaxWidth(),
            contentSizeSp = 20,
        )
        if (ear.adRevealed && ear.adGuess != null) {
            Spacer(Modifier.height(6.dp))
            Text(if (ear.adGuess == ear.adQuality) "✔ correct"
                 else "✘ it was ${augDimLabel(ear.adQuality)}",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugDimChallengeView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!ear.adChActive) {
            Text("${ear.augDimChallengeTotal} rounds. Identify each augmented/diminished chord. " +
                "Choose which qualities can appear:",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            AugDimPalette(ear)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { ear.startAugDimChallenge() },
                    enabled = ear.augDimAllowed.isNotEmpty()) { Text("Start challenge ▶") }
            }
            return@Column
        }
        if (ear.adChIndex >= ear.augDimChallengeTotal) {
            SimpleDoneCard(ear.adChScore, ear.augDimChallengeTotal,
                onRestart = { ear.startAugDimChallenge() }, onExit = { ear.exitAugDimChallenge() })
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Round ${ear.adChIndex + 1} / ${ear.augDimChallengeTotal}",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Score: ${ear.adChScore}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { ear.startAugDimChallenge() }) { Text("Restart") }
            TextButton(onClick = { ear.exitAugDimChallenge() }) { Text("Quit") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { ear.playAugDim() }) { Text("Replay ▶") }
        Spacer(Modifier.height(12.dp))
        AugDimGuessChips(ear, enabled = !ear.adChAnswered)
        Spacer(Modifier.height(10.dp))
        if (!ear.adChAnswered) {
            Button(onClick = { ear.submitAugDimGuess() }, enabled = ear.adGuess != null,
                modifier = Modifier.fillMaxWidth()) { Text("Submit") }
        } else {
            val ok = ear.adGuess == ear.adQuality
            Text((if (ok) "✔ correct" else "✘ answer: ${augDimLabel(ear.adQuality)}") +
                "   (${NoteSpeller.spell(ear.adRoot)}${ear.adQuality})",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ear.advanceAugDimChallenge() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ear.adChIndex == ear.augDimChallengeTotal - 1) "See score →" else "Next →")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

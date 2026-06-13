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
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "EAR TRAINING",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SingleChoiceSegmentedButtonRow {
                EarSubMode.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = s == ear.progSubMode,
                        onClick = { ear.switchTab(s) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = EarSubMode.entries.size),
                        label = {
                            Text(
                                when (s) {
                                    EarSubMode.Progression -> "Progressions"
                                    EarSubMode.Note2Chord  -> "Note2Chord"
                                    EarSubMode.Flavor      -> "Flavor"
                                }
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            AudioQuickButton(state, compact = true)
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { ear.release(); onBack() }) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // Practice / Challenge toggle — every tab has both (note #3).
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(bottom = 8.dp)) {
            EarMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == ear.earMode,
                    onClick = { ear.earMode = m },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = EarMode.entries.size),
                    label = { Text(if (m == EarMode.Practice) "Practice" else "Challenge") },
                )
            }
        }

        when (ear.progSubMode) {
            EarSubMode.Progression ->
                if (ear.earMode == EarMode.Challenge) ProgressionChallengeView(state, ear)
                else ProgressionView(state, ear)
            EarSubMode.Note2Chord ->
                if (ear.earMode == EarMode.Challenge) Note2ChordChallengeView(ear)
                else Note2ChordView(ear)
            EarSubMode.Flavor ->
                if (ear.earMode == EarMode.Challenge) FlavorChallengeView(ear)
                else FlavorView(ear)
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

        // BPM + transport + Generate / Next
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("BPM: ${ear.progBpm}", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Slider(
                    value = ear.progBpm.toFloat(),
                    onValueChange = { ear.progBpm = it.toInt() },
                    valueRange = 40f..200f,
                )
                Text(
                    if (state.strumMs == 0) "Strum: struck at once" else "Strum: ${state.strumMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                )
                androidx.compose.material3.Slider(
                    value = state.strumMs.toFloat(),
                    onValueChange = { state.setStrumMs(it.toInt()) },
                    valueRange = 0f..150f,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (ear.hasGenerated) {
                if (ear.isLooping) {
                    Button(onClick = { ear.stopLoop() }) { Text("Stop ⏹") }
                } else {
                    Button(onClick = { ear.startLoop() }) { Text("Play ▶") }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { ear.nextProgression() }) { Text("Next progression →") }
                Spacer(Modifier.width(8.dp))
                // #1: hear the tonic — plays I-V-I (or i-V-i) in the current key.
                OutlinedButton(onClick = { ear.playProgKeyCadence() }) {
                    Text("Hear key ${ear.progCadenceLabel()}")
                }
                Spacer(Modifier.width(8.dp))
                // #2: push the current progression's chords into the Looper.
                OutlinedButton(onClick = {
                    state.loadProgressionIntoLoop(ear.progResolved.map { it.symbol })
                }) { Text("→ Looper") }
                Spacer(Modifier.width(12.dp))
                // #11: small counter of how many progressions generated this session.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${ear.progressionCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!ear.hasGenerated) {
            // Initial state: prominent CTA. The user adjusts settings above, then
            // taps this to produce the first progression that honors them.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(onClick = { ear.nextProgression() }) {
                    Text("Generate progression ▶", style = MaterialTheme.typography.titleMedium)
                }
            }
            return@Column
        }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionSettings(ear: EarTrainingState) {
    // Settings row: key (random / fixed), mode toggles, chord-type level
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Key", style = MaterialTheme.typography.labelMedium)
            // Random is the common case, so collapse the 12 fixed keys into a popup.
            var keyMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { keyMenu = true }) {
                    Text((ear.fixedKey?.let { NoteSpeller.spell(it) } ?: "Random") + " ▾")
                }
                DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Random") },
                        onClick = { ear.fixedKey = null; keyMenu = false },
                    )
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
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text("Modes", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Major", style = MaterialTheme.typography.bodySmall)
                Switch(checked = ear.includeMajor, onCheckedChange = { ear.includeMajor = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Minor", style = MaterialTheme.typography.bodySmall)
                Switch(checked = ear.includeMinor, onCheckedChange = { ear.includeMinor = it })
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text("Chord type", style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow {
                ChordTypeLevel.entries.forEachIndexed { i, lvl ->
                    SegmentedButton(
                        selected = ear.chordTypeLevel == lvl && !ear.earMixAll,
                        onClick = { ear.chordTypeLevel = lvl; ear.reresolveCurrent() },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ChordTypeLevel.entries.size),
                        label = { Text(lvl.displayName) },
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        // Voicing style + mix-and-match (notes: shell-only / mix everything).
        Column(horizontalAlignment = Alignment.Start) {
            Text("Voicing", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !ear.earShellVoicing && !ear.earMixAll,
                    onClick = { ear.earShellVoicing = false },
                    label = { Text("Standard") },
                )
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = ear.earShellVoicing && !ear.earMixAll,
                    onClick = { ear.earShellVoicing = true },
                    label = { Text("Shell") },
                )
            }
            Spacer(Modifier.height(4.dp))
            FilterChip(
                selected = ear.earMixAll,
                onClick = { ear.earMixAll = !ear.earMixAll; ear.reresolveCurrent() },
                label = { Text("Mix all") },
            )
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Bar $barNumber",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (hidden) "tap to reveal" else label,
                fontSize = if (hidden) 14.sp else 30.sp,
                fontWeight = if (hidden) FontWeight.Normal else FontWeight.SemiBold,
                color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onPlay,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 10.dp, vertical = 2.dp),
            ) { Text("▶ Play") }
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { ear.newFlavorChallenge() }, enabled = !ear.flavorPlaying) {
                Text(if (ear.flavorPlaying) "Playing…" else "New chord ▶")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { ear.replayFlavorCadence() },
                enabled = ear.flavorStarted && !ear.flavorPlaying,
            ) { Text("Replay ${ear.flavorCadenceLabel()}") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { ear.playFlavorChord() }, enabled = ear.flavorStarted) {
                Text("Play chord")
            }
        }

        if (!ear.flavorStarted) return@Column

        Spacer(Modifier.height(14.dp))

        Text("Degree", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (deg in 1..7) {
                FilterChip(
                    selected = ear.flavorGuessDegree == deg,
                    onClick = { ear.flavorGuessDegree = deg },
                    label = { Text("$deg") },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Flavor", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (sym in ear.flavorAllowed.toList()) {
                FilterChip(
                    selected = ear.flavorGuessQuality == sym,
                    onClick = { ear.flavorGuessQuality = sym },
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
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { ear.exitN2cChallenge() }) { Text("Quit") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { ear.exitFlavorChallenge() }) { Text("Quit") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { ear.replayFlavorCadence() }, enabled = !ear.flavorPlaying) {
                Text("Replay ${ear.flavorCadenceLabel()}")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { ear.playFlavorChord() }) { Text("Play chord") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Degree", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (deg in 1..7) {
                FilterChip(selected = ear.flavorGuessDegree == deg, enabled = !ear.flavorChAnswered,
                    onClick = { ear.flavorGuessDegree = deg }, label = { Text("$deg") })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Flavor", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (sym in ear.flavorAllowed.toList()) {
                FilterChip(selected = ear.flavorGuessQuality == sym, enabled = !ear.flavorChAnswered,
                    onClick = { ear.flavorGuessQuality = sym },
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
            ChallengeDoneCard(
                score = ear.challengeBarScore,
                total = ear.challengeBarTotal,
                answers = ear.challengeAnswers,
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
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { ear.exitChallenge() }) { Text("Quit") }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (ear.isLooping) {
                Button(onClick = { ear.stopLoop() }) { Text("Stop ⏹") }
            } else {
                Button(onClick = { ear.startLoop() }) { Text("Play progression ▶") }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { ear.playProgKeyCadence() }) { Text("Hear key ${ear.progCadenceLabel()}") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { ear.rerollChallengeQuestion() }) { Text("Re-roll progression") }
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

        // #9: per-bar answering — tap the correct Roman numeral (and the extension
        // when the level has one). Each bar auto-scores; the question is a point if
        // all four bars are right.
        val degreeOptions = ear.challengeDegreeOptions()
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((deg, roman) in degreeOptions) {
                            FilterChip(
                                selected = ear.challengeGuessDegree.getOrNull(i) == deg,
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

        val marked = ear.challengeAnswers.getOrNull(ear.challengeIndex)
        Button(
            onClick = { ear.advanceChallenge() },
            enabled = marked != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ear.challengeIndex == ear.challengeTotal - 1) "See score →" else "Next question →")
        }

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

@Composable
private fun ChallengeDoneCard(
    score: Int,
    total: Int,
    answers: List<Boolean?>,
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
                "bars correct",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            // Per-question dot strip
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestart) { Text("Restart") }
                OutlinedButton(onClick = onExit) { Text("Exit") }
            }
        }
    }
}

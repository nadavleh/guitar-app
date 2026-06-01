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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.ChordTypeLevel
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.TrainingMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarTrainingScreen(state: AppState, onBack: () -> Unit) {
    val ear = remember(state) {
        EarTrainingState(
            audio = state.audio,
            scope = state.scope,
            tuningProvider = { state.liveTuning },
            sustainProvider = { state.ringSustainMs },
        )
    }
    DisposableEffect(Unit) { onDispose { ear.release() } }
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
                        onClick = { ear.progSubMode = s; ear.stopLoop() },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = EarSubMode.entries.size),
                        label = {
                            Text(
                                when (s) {
                                    EarSubMode.Progression -> "Progressions"
                                    EarSubMode.Note2Chord  -> "Note2Chord"
                                    EarSubMode.Challenge   -> "Challenge"
                                }
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { ear.release(); onBack() }) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        when (ear.progSubMode) {
            EarSubMode.Progression -> ProgressionView(state, ear)
            EarSubMode.Note2Chord  -> Note2ChordView(ear)
            EarSubMode.Challenge   -> ChallengeView(state, ear)
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

        // KEY + MODE reveal row
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RevealCard(
                label = "Key",
                hidden = !ear.keyRevealed,
                content = NoteSpeller.spell(ear.progKey),
                onToggle = { ear.toggleKeyReveal() },
                modifier = Modifier.weight(1f),
                contentSizeSp = 56,
            )
            Spacer(Modifier.width(8.dp))
            RevealCard(
                label = "Mode",
                hidden = !ear.modeRevealed,
                content = if (ear.progMode == TrainingMode.Major) "Major" else "Minor",
                onToggle = { ear.toggleModeReveal() },
                modifier = Modifier.weight(1f),
                contentSizeSp = 28,
            )
        }

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
                    onTap = { /* read-only inside ear training */ },
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
        Column(modifier = Modifier.weight(1f)) {
            Text("Key", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = ear.fixedKey == null,
                    onClick = { ear.fixedKey = null },
                    label = { Text("Random") },
                )
                Spacer(Modifier.width(8.dp))
                Text("Fixed:", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0..11) {
                    val pc = PitchClass(i)
                    FilterChip(
                        selected = ear.fixedKey == pc,
                        onClick = { ear.fixedKey = pc },
                        label = { Text(NoteSpeller.spell(pc)) },
                    )
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
                        selected = ear.chordTypeLevel == lvl,
                        onClick = {
                            ear.chordTypeLevel = lvl
                            // Re-resolve the current progression at the new level (if any).
                            val prog = ear.progProgression
                            if (prog != null) {
                                ear.progResolved = app.guitar.theory.EarTraining
                                    .resolveProgression(prog, ear.progKey, lvl)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ChordTypeLevel.entries.size),
                        label = { Text(lvl.displayName) },
                    )
                }
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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { ear.playN2c() },
                enabled = !ear.n2cPlaying,
            ) { Text(if (ear.n2cPlaying) "Playing…" else "Play challenge ▶") }
            OutlinedButton(onClick = {
                ear.nextN2cChallenge()
                ear.playN2c()
            }) { Text("Next →") }
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

// -------- Challenge view --------

/**
 * Self-marked 15-question quiz. Each question is a fresh random progression
 * generated under the same settings as the Progressions sub-mode (Major/Minor
 * include flags + Triads / 7ths / Extended). The user plays it, reveals the
 * answer, and presses "✔ Got it" or "✘ Missed it". After 15 questions a final
 * score screen is shown with a Restart button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeView(state: AppState, ear: EarTrainingState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!ear.challengeActive) {
            // ---- title / config screen ----
            Text(
                "A challenge is 15 progressions in a row. Listen, reveal the answer, " +
                    "and self-mark whether you identified each chord correctly. Your " +
                    "score appears at the end.",
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
                score = ear.challengeScore,
                total = ear.challengeTotal,
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
                "Score: ${ear.challengeScore}",
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
            OutlinedButton(onClick = { ear.nextProgression() }) { Text("Re-roll progression") }
        }

        Spacer(Modifier.height(12.dp))

        // Reveal panel: hidden by default; shows key/mode/4 chord labels at once.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { ear.challengeRevealed = !ear.challengeRevealed },
            colors = CardDefaults.cardColors(
                containerColor = if (ear.challengeRevealed) MaterialTheme.colorScheme.tertiaryContainer
                                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Answer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                if (!ear.challengeRevealed) {
                    Text(
                        "tap to reveal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val key = NoteSpeller.spell(ear.progKey)
                    val mode = if (ear.progMode == TrainingMode.Major) "Major" else "Minor"
                    Text(
                        "$key  ·  $mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (rc in ear.progResolved) {
                            Text(
                                rc.romanLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (rc in ear.progResolved) {
                            Text(
                                rc.symbol,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Right / Wrong (only enabled once revealed)
        val marked = ear.challengeAnswers.getOrNull(ear.challengeIndex)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ear.markChallenge(true) },
                enabled = ear.challengeRevealed && marked == null,
                modifier = Modifier.weight(1f),
            ) { Text("✔ Got it") }
            Button(
                onClick = { ear.markChallenge(false) },
                enabled = ear.challengeRevealed && marked == null,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) { Text("✘ Missed it") }
        }

        Spacer(Modifier.height(10.dp))

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
                    onTap = { },
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

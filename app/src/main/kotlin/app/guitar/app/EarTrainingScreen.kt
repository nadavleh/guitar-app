package app.guitar.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.ChordTypeLevel
import app.guitar.theory.EarTraining
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.ProgressionSongs
import app.guitar.theory.ResolvedChord
import app.guitar.theory.SongExample
import app.guitar.theory.TrainingMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarTrainingScreen(state: AppState, onBack: () -> Unit) {
    // #7: use the app-lifetime instance so leaving and returning preserves state.
    val ear = state.earTraining
    // Stop audio/looping when leaving the screen, but keep all state (progression,
    // reveals, counters) so returning shows exactly what you left.
    DisposableEffect(Unit) { onDispose { ear.stopLoop(); ear.libraryStop() } }
    LaunchedEffect(Unit) {
        // NB: deliberately do NOT auto-generate a progression here. The user
        // wants the first progression to honor settings they pick beforehand,
        // so we show a "Generate progression" button instead. Note2Chord still
        // pre-generates because it has no settings to honor.
        if (ear.n2cChallenge == null) ear.nextN2cChallenge()
    }

    // One shared Tone sheet toggle: opened either from the header's Tune icon
    // (needed by every sub-mode, including the ones with no transport dock) or
    // from the Progression dock's tone chip (Signal move #2/#3).
    var toneSheetOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
    ) {
        // Title row: title + (while a Progression challenge is in flight) pinned
        // Restart/Quit icons + Stats + Tune + Back.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "EAR TRAINING",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            val progChallengeInFlight = ear.progSubMode == EarSubMode.Progression &&
                ear.earMode == EarMode.Challenge &&
                if (ear.specialProgMode) ear.advChActive && ear.advChIndex < ear.advChallengeTotal
                else ear.challengeActive && ear.challengeIndex < ear.challengeTotal
            if (progChallengeInFlight) {
                IconButton(onClick = {
                    if (ear.specialProgMode) ear.startAdvChallenge() else ear.startChallenge()
                }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Restart challenge")
                }
                IconButton(onClick = {
                    if (ear.specialProgMode) ear.exitAdvChallenge() else ear.exitChallenge()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Quit challenge")
                }
            }
            var statsOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { statsOpen = true }) {
                Icon(Icons.Outlined.BarChart, contentDescription = "Stats")
            }
            if (statsOpen) EarStatsDialog(state, onDismiss = { statsOpen = false })
            IconButton(onClick = { toneSheetOpen = true }) {
                Icon(Icons.Outlined.Tune, contentDescription = "Tone")
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { ear.release(); onBack() }) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))
        // Practice/Challenge as a full-width segmented row (Signal move — replaces
        // the compact ModeDropdown) and the sub-modes as a chip row with a "More ▾"
        // overflow (replaces SubModeDropdown). ear.switchTab / ear.earMode calls
        // are identical to before — only the picker chrome changed.
        SegmentedRow(
            options = EarMode.entries,
            selected = ear.earMode,
            onSelect = { ear.earMode = it },
            label = { if (it == EarMode.Practice) "Practice" else "Challenge" },
        )
        Spacer(Modifier.height(8.dp))
        SubModeChipRow(ear)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (ear.progSubMode) {
                EarSubMode.Progression ->
                    if (ear.specialProgMode) {
                        if (ear.earMode == EarMode.Challenge) AdvancedChallengeView(state, ear)
                        else AdvancedProgressionView(state, ear)
                    } else {
                        if (ear.earMode == EarMode.Challenge) ProgressionChallengeView(state, ear)
                        else ProgressionView(state, ear)
                    }
                EarSubMode.Note2Chord ->
                    if (ear.earMode == EarMode.Challenge) Note2ChordChallengeView(ear)
                    else Note2ChordView(state, ear)
                EarSubMode.Flavor ->
                    if (ear.earMode == EarMode.Challenge) FlavorChallengeView(ear)
                    else FlavorView(state, ear)
                EarSubMode.Inversions ->
                    if (ear.earMode == EarMode.Challenge) InversionsChallengeView(state, ear)
                    else InversionsView(state, ear)
                EarSubMode.AugDim ->
                    if (ear.earMode == EarMode.Challenge) AugDimChallengeView(state, ear)
                    else AugDimView(state, ear)
                // Intervals is challenge-only (#6) — same view in either mode.
                EarSubMode.Intervals -> IntervalsView(ear)
            }
        }

        // Transport dock (Signal move #2): replaces the per-view Play ▶/Stop ⏹
        // buttons for every Progression generator (diatonic/advanced/circle/iii-focus)
        // in both Practice and Challenge. progBpm is captured once when startLoop()
        // launches its coroutine, so a live BPM edit restarts the loop to take effect.
        if (ear.progSubMode == EarSubMode.Progression) {
            Spacer(Modifier.height(8.dp))
            TransportDock(
                playing = ear.isLooping,
                onPlayStop = { if (ear.isLooping) ear.stopLoop() else ear.startLoop() },
                bpm = ear.progBpm,
                onBpm = { newBpm ->
                    ear.progBpm = newBpm
                    if (ear.isLooping) { ear.stopLoop(); ear.startLoop() }
                },
                toneLabel = state.sound.name,
                onTone = { toneSheetOpen = true },
            )
        }
    }
    if (toneSheetOpen) ToneSheet(state, onDismiss = { toneSheetOpen = false })
}

private fun subModeLabel(s: EarSubMode): String = when (s) {
    EarSubMode.Progression -> "Progressions"
    EarSubMode.Note2Chord  -> "Note→Chord"
    EarSubMode.Flavor      -> "Flavor"
    EarSubMode.Inversions  -> "Inversions"
    EarSubMode.AugDim      -> "Aug / Dim"
    EarSubMode.Intervals   -> "Intervals"
}

/** Sub-mode chip row (Signal move — replaces the SubModeDropdown): Progressions,
 *  Intervals and Note→Chord are always-visible chips; Flavor/Inversions/AugDim live
 *  behind a "More ▾" overflow chip (which shows the current sub-mode's name when
 *  the selection IS one of the overflowed ones, so the active mode is never hidden). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubModeChipRow(ear: EarTrainingState) {
    val primaryChips = listOf(EarSubMode.Progression, EarSubMode.Intervals, EarSubMode.Note2Chord)
    val overflowChips = listOf(EarSubMode.Flavor, EarSubMode.Inversions, EarSubMode.AugDim)
    var moreOpen by remember { mutableStateOf(false) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        for (s in primaryChips) {
            FilterChip(
                selected = ear.progSubMode == s,
                onClick = { ear.switchTab(s) },
                label = { Text(subModeLabel(s)) },
            )
        }
        Box {
            val inOverflow = ear.progSubMode in overflowChips
            FilterChip(
                selected = inOverflow,
                onClick = { moreOpen = true },
                label = { Text((if (inOverflow) subModeLabel(ear.progSubMode) else "More") + "  ▾") },
            )
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                for (s in overflowChips) {
                    DropdownMenuItem(
                        text = { Text(subModeLabel(s)) },
                        onClick = { ear.switchTab(s); moreOpen = false },
                    )
                }
            }
        }
    }
}

/** Short label for the current progression generator. */
private fun generatorLabel(ear: EarTrainingState): String = when {
    ear.advancedMode -> "Advanced"
    ear.circleMode -> "Circle of 5ths"
    ear.iiiFocusMode -> "I → iii focus"
    else -> "Diatonic"
}

/** One-line teaching caption for the current progression generator. */
private fun generatorCaption(ear: EarTrainingState): String = when {
    ear.advancedMode -> "Borrowed chords, secondary dominants & jazz turnarounds, each with a note."
    ear.circleMode -> "Circle-of-fifths windows built around secondary dominants (V7 of the next chord)."
    ear.iiiFocusMode -> "Drill for hearing the I→iii move — every progression opens with I then iii (major)."
    else -> "Standard diatonic progressions in the chosen key & mode."
}

/** Compact generator picker — collapses the former Advanced / Circle-of-fifths toggle
 *  rows into one dropdown (Diatonic / Advanced / Circle) so the fixed header stays small. */
@Composable
private fun GeneratorDropdown(ear: EarTrainingState, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Generator: ${generatorLabel(ear)}  ▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Diatonic") },
                onClick = { ear.chooseAdvancedMode(false); ear.chooseCircleMode(false); ear.chooseIiiFocusMode(false); open = false })
            DropdownMenuItem(text = { Text("I → iii focus") },
                onClick = { ear.chooseIiiFocusMode(true); open = false })
            DropdownMenuItem(text = { Text("Advanced (non-diatonic)") },
                onClick = { ear.chooseAdvancedMode(true); open = false })
            DropdownMenuItem(text = { Text("Circle of fifths — secondary dominants") },
                onClick = { ear.chooseCircleMode(true); open = false })
        }
    }
}

/** Short label for the current chord-type/level pool ("Mix" when Mix-all is on). */
private fun levelLabel(ear: EarTrainingState): String =
    if (ear.earMixAll) "Mix" else ear.chordTypeLevel.displayName

/**
 * One-line "‹Generator› · ‹key› · ‹level› — tap to configure" summary card (Signal
 * move): replaces the always-expanded [ProgressionSettings]/[GeneratorDropdown]/
 * Library row with a single tappable card that opens [GeneratorSettingsSheet]
 * hosting all of it. Shown by every Progression sub-mode view (diatonic + the
 * advanced/circle generators), both Practice and Challenge.
 */
@Composable
private fun GeneratorSummaryCard(ear: EarTrainingState, onClick: () -> Unit) {
    val keyLabel = ear.fixedKey?.let { NoteSpeller.spell(it) } ?: "Random key"
    val summary = generatorLabel(ear) + "  ·  " + keyLabel +
        if (!ear.specialProgMode) "  ·  " + levelLabel(ear) else ""
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(summary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("tap to configure", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.Tune, contentDescription = "Configure",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Settings sheet opened by [GeneratorSummaryCard]: hosts the generator dropdown +
 * caption + Library button (always), plus — for the diatonic generator — the full
 * [ProgressionSettings] (key/modes/level/voicing), or — for the advanced/circle
 * generators, which don't use those pools — just the Key picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratorSettingsSheet(state: AppState, ear: EarTrainingState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var libOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text("Progression settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GeneratorDropdown(ear, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { libOpen = true }) { Text("Library") }
            }
            Text(generatorCaption(ear), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            if (ear.specialProgMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Key", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    KeyDropdown(ear)
                }
            } else {
                ProgressionSettings(ear)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (libOpen) ProgressionLibraryDialog(state, onDismiss = { libOpen = false })
}

// -------- Progression view --------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionView(state: AppState, ear: EarTrainingState) {
    var settingsOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!ear.hasGenerated) {
            // Initial state: prominent CTA. The user adjusts settings via the
            // summary card below, then taps this to produce the first progression
            // that honors them.
            GeneratorSummaryCard(ear, onClick = { settingsOpen = true })
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { ear.nextProgression() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Generate progression ▶", style = MaterialTheme.typography.titleMedium) }
            if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
            return@Column
        }

        // ---- Reveal cards first (Signal order): Key&Mode hint, then the 4 bars ----
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

        // Four chord-slot reveal cards (each with its own play button); the
        // currently-sounding bar gets an act border (Signal restyle — see
        // ChordSlotCard's `isPlaying` prop).
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

        // ---- Action strip directly under the cards: compact outlined buttons.
        // Play/Stop lives in the TransportDock (Signal move #2), pinned below.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // #7: Prev/Next get transparent red/green tints so they're clearly distinct
            // from the accent Play button in the transport dock (users misclicked Next
            // for Play). ← Prev restores the previously generated progression.
            Button(
                onClick = { ear.previousProgression() },
                enabled = ear.canGoPrevProgression,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = COMPACT_BUTTON_PADDING,
            ) { Text("← Prev progression") }
            Button(
                onClick = { ear.nextProgression() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32).copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = COMPACT_BUTTON_PADDING,
            ) { Text("Next progression →") }
            // #1: hear the tonic — plays I-V-I (or i-V-i) in the current key.
            OutlinedButton(onClick = { ear.playProgKeyCadence() }, contentPadding = COMPACT_BUTTON_PADDING) {
                Text("Hear ${ear.progCadenceLabel()}")
            }
            // #2: push the current progression's chords into the Looper.
            OutlinedButton(
                onClick = { state.loadProgressionIntoLoop(ear.progResolved.map { it.symbol }) },
                contentPadding = COMPACT_BUTTON_PADDING,
            ) { Text("→ Looper") }
            ProgressionSongsButton(ear)
        }

        Spacer(Modifier.height(10.dp))
        TransposeClicker(ear)

        Spacer(Modifier.height(12.dp))
        GeneratorSummaryCard(ear, onClick = { settingsOpen = true })

        Spacer(Modifier.height(12.dp))

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
                    // Hoisted camera: keeps the zoom when the panel is toggled off/on.
                    camera = ear.progFretboardCamera,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
    if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
}

/** Compact content padding shared by the Progression action-strip buttons
 *  (Signal restyle — a tighter, "chip-like" footprint for a 4-button FlowRow). */
private val COMPACT_BUTTON_PADDING = PaddingValues(horizontal = 10.dp, vertical = 6.dp)

/** "Songs ♪" button + popup listing famous songs built on the CURRENT progression
 *  (from the library data). Available in Practice and Challenge, all generators. */
@Composable
private fun ProgressionSongsButton(ear: EarTrainingState) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, contentPadding = COMPACT_BUTTON_PADDING) { Text("Songs ♪") }
    if (open) {
        // Curated hits first; PDF-imported extras fold behind a "Show more" expander.
        val songs = ear.currentProgressionSongs()
        val extra = ear.currentProgressionImportedSongs()
        var showExtra by remember(songs, extra) { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
            title = { Text("Songs with this progression") },
            text = {
                if (songs.isEmpty() && extra.isEmpty()) {
                    Text("No songs are listed for this progression yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        songs.forEach { SongLinkRow(it.title, it.artist) }
                        if (extra.isNotEmpty()) {
                            if (!showExtra) {
                                TextButton(onClick = { showExtra = true }, contentPadding = COMPACT_BUTTON_PADDING) {
                                    Text("Show ${extra.size} more from the songbook ▾")
                                }
                            } else {
                                if (songs.isNotEmpty()) {
                                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    Text("More from the songbook",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp))
                                }
                                extra.forEach { SongLinkRow(it.title, it.artist) }
                            }
                        }
                    }
                }
            },
        )
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

/**
 * Reusable "show this chord on the fretboard" block (#2/#3). Given a chord [symbol]
 * (e.g. "C7", "Eaug"), renders a Switch and, when on, a FretboardView marking the
 * preferred (E-shape) voicing. Tapping a fret auditions that note.
 */
@Composable
private fun ChordOnFretboard(
    state: AppState,
    symbol: String,
    show: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Show chord on fretboard", style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        Switch(checked = show, onCheckedChange = onToggle)
    }
    if (!show) return
    val marks = remember(symbol, state.labelMode, state.liveTuning) {
        val parsed = app.guitar.theory.ChordLibrary.parse(symbol)
        if (parsed == null) emptyMap()
        else {
            val (root, q) = parsed
            val shapes = app.guitar.theory.ChordShapeGenerator()
                .shapesFor(root, q, state.liveTuning, frets = DISPLAY_FRETS)
            val shape = shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E }
                ?: shapes.firstOrNull()
            // Show ONLY the chord's own tones: some CAGED grips (e.g. the "dim" shape is
            // really a dim7 voicing) carry an extra note that would otherwise render a
            // phantom extension on the neck for a plain triad.
            if (shape == null) emptyMap()
            else {
                val chordPcs = q.intervals.map { ((root.value + it.semitones) % 12 + 12) % 12 }.toSet()
                shapeMarks(shape, state.labelMode).filterKeys { pos ->
                    Fretboard.noteAt(state.liveTuning, pos).pitchClass.value in chordPcs
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(220.dp).padding(vertical = 4.dp)) {
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

/** ±1-semitone transpose clicker for the Progressions practice views. Shifts the
 *  whole progression (key + chords) while keeping the same degrees. */
@Composable
private fun TransposeClicker(ear: EarTrainingState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Transpose", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = { ear.transposeProgression(-1) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        ) { Text("−") }
        // Running net offset from the generated key (e.g. "+3 semitones").
        Text("  ${transposeLabel(ear.progTranspose)}  ", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { ear.transposeProgression(1) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        ) { Text("+") }
    }
}

/** "0 semitones" / "+3 semitones" / "−2 semitones" for the transpose counters. */
private fun transposeLabel(n: Int): String {
    val unit = if (n == 1 || n == -1) "semitone" else "semitones"
    val num = when {
        n > 0 -> "+$n"
        n < 0 -> "−${-n}"   // U+2212 minus to match the − button
        else -> "0"
    }
    return "$num $unit"
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
        // Signal restyle: the currently-sounding bar gets a solid act border so it
        // reads clearly even at a glance (not just the subtler container tint).
        border = if (isPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
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
@OptIn(ExperimentalLayoutApi::class)
private fun Note2ChordView(state: AppState, ear: EarTrainingState) {
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

        // Replay is primary; Prev/Next walk history; New + draws a fresh challenge (#1/#3).
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ear.playN2c() },
                enabled = !ear.n2cPlaying,
            ) { Text(if (ear.n2cPlaying) "Playing…" else "Replay both ▶") }
            OutlinedButton(onClick = { ear.n2cPrev(); ear.playN2c() }, enabled = ear.n2cHasPrev) { Text("◀ Prev") }
            OutlinedButton(onClick = { ear.n2cNext(); ear.playN2c() }, enabled = ear.n2cHasNext) { Text("Next ▶") }
            OutlinedButton(onClick = { ear.nextN2cChallenge(); ear.playN2c() }) { Text("New +") }
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
        if (c != null) {
            Spacer(Modifier.height(12.dp))
            ChordOnFretboard(state, c.chordSymbol, ear.n2cShowFretboard) { ear.n2cShowFretboard = it }
        }
        // Bottom breathing room so the card never abuts the system gesture bar.
        Spacer(Modifier.height(20.dp))
    }
}

// -------- Chord Flavor view (#5) --------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlavorView(state: AppState, ear: EarTrainingState) {
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
        Text("Flavor  (only diatonic flavors for this key)", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // #4: present only flavors that are diatonic in the current key/mode
            // (narrowed to the guessed degree once one is chosen).
            for (sym in ear.flavorQualityOptions(ear.flavorGuessDegree)) {
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
        Spacer(Modifier.height(12.dp))
        ChordOnFretboard(state, ear.flavorChordSymbol(), ear.flavorShowFretboard) { ear.flavorShowFretboard = it }
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
            IconButton(onClick = { ear.startN2cChallenge() }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = "Restart challenge")
            }
            IconButton(onClick = { ear.exitN2cChallenge() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Quit challenge")
            }
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
            IconButton(onClick = { ear.startFlavorChallenge() }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = "Restart challenge")
            }
            IconButton(onClick = { ear.exitFlavorChallenge() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Quit challenge")
            }
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
 * Auto-scored quiz of [EarTrainingState.challengeTotal] questions. Each question is a
 * fresh random progression generated under the same settings as the Progressions
 * sub-mode (Major/Minor include flags + Triads / 7ths / Extended). For each bar the
 * user taps the correct Roman numeral (and extension, when the level has one); the
 * question scores a point only if all four bars are right. After the last question a
 * final score screen is shown with a Restart button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionChallengeView(state: AppState, ear: EarTrainingState) {
    var settingsOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!ear.challengeActive) {
            // ---- title / config screen ----
            Text(
                "A challenge is ${ear.challengeTotal} progressions in a row. Listen, then tap the correct " +
                    "Roman numeral for each bar (and its extension when shown). Each " +
                    "question auto-scores; your total appears at the end.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            GeneratorSummaryCard(ear, onClick = { settingsOpen = true })

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Button(onClick = { ear.startChallenge() }) {
                    Text("Start challenge ▶", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
            return@Column
        }

        // ---- done screen (after the last question advances) ----
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
        // Progress ring + per-question dot strip (Signal move — replaces the old
        // "Question n/N · Score · Restart · Quit" row; Restart/Quit are now pinned
        // icon buttons in the screen header, and "Q n/N" lives inside the ring).
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ChallengeProgressRing(index = ear.challengeIndex, total = ear.challengeTotal)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Score: ${ear.challengeBarScore} / ${ear.challengeBarTotal} bars",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                ChallengeDotStrip(ear)
            }
        }

        Spacer(Modifier.height(10.dp))

        // #4/#5: question navigation pinned up top, so an accidental "Next" can be
        // undone (← Prev restores that question's saved answers) and you can advance
        // without scrolling to the bottom button.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { ear.previousChallengeQuestion() },
                enabled = ear.canGoPrevChallenge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC0392B), contentColor = Color.White,
                    disabledContainerColor = Color(0xFFC0392B).copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.weight(1f),
            ) { Text("← Prev") }
            Button(
                onClick = { ear.advanceChallenge() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E9E4F), contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f),
            ) { Text(if (ear.challengeIndex == ear.challengeTotal - 1) "See score →" else "Next →") }
        }

        Spacer(Modifier.height(8.dp))

        // Tools row: Hear the cadence · Re-roll · Transpose (Signal move — one row).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { ear.playProgKeyCadence() }) { Text("Hear ${ear.progCadenceLabel()}") }
            OutlinedButton(onClick = { ear.rerollChallengeQuestion() }) { Text("Re-roll") }
            ProgressionSongsButton(ear)
            // Transpose works here too — it shifts the key/chords but not the
            // degrees, so it never gives away the answer.
            TransposeClicker(ear)
        }

        Spacer(Modifier.height(8.dp))
        // BPM + strum now live in the "Playback ▾" dropdown in the section header
        // (shared by all generators & modes — tasks #4/#10).

        Spacer(Modifier.height(4.dp))

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

        // #6/Signal: fixed answer pad — tap a bar square to select it, then answer
        // it from the always-visible pad below (replaces the old popup keyboard;
        // the per-bar ▶ Play and reference palette above are the only things that
        // sound — selecting a bar / a key is silent).
        Text("Fill each bar  (tap a square to select it, then tap its chord below)",
            style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        var selectedBar by remember { mutableStateOf(0) }
        // Land back on bar 1 whenever a fresh question starts.
        LaunchedEffect(ear.challengeIndex) { selectedBar = 0 }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (i in 0 until 4) {
                val verdict = ear.challengeBarCorrect(i)
                BarSquare(
                    barNumber = i + 1,
                    label = ear.challengeGuessLabel.getOrNull(i),
                    verdict = verdict,
                    answer = if (verdict != null) ear.progResolved.getOrNull(i)?.romanLabel else null,
                    selected = selectedBar == i,
                    playhead = ear.isLooping && ear.currentBar == i,   // playing "head"
                    onTap = { selectedBar = i },
                    // Playing a bar also selects it, so it's the target of the answer pad.
                    onPlay = { selectedBar = i; ear.playBarOnce(i) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ChallengeAnswerPad(ear, bar = selectedBar)

        Spacer(Modifier.height(10.dp))

        // Bottom nav: reddish Prev + greenish Next question (matches the top nav) so Prev
        // is present and visible at the bottom too. #4: advancing is always allowed — any
        // bars you haven't answered are credited as correct.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { ear.previousChallengeQuestion() },
                enabled = ear.canGoPrevChallenge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC0392B), contentColor = Color.White,
                    disabledContainerColor = Color(0xFFC0392B).copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
                modifier = Modifier.weight(1f),
            ) { Text("← Prev question") }
            Button(
                onClick = { ear.advanceChallenge() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E9E4F), contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f),
            ) { Text(if (ear.challengeIndex == ear.challengeTotal - 1) "See score →" else "Next question →") }
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
                    // Hoisted camera: keeps the zoom when the panel is toggled off/on.
                    camera = ear.progFretboardCamera,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
}

/** #6: one bar's answer square — a tappable tile that targets the fixed answer
 *  pad below it, showing the chosen chord label (or "?" when empty) plus a ▶ to
 *  hear the bar. [selected] marks the bar the pad currently answers for. */
@Composable
private fun BarSquare(
    barNumber: Int,
    label: String?,
    verdict: Boolean?,
    answer: String?,
    selected: Boolean = false,
    playhead: Boolean = false,
    onTap: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = when {
        playhead -> MaterialTheme.colorScheme.primary
        verdict == true  -> MaterialTheme.colorScheme.primary
        verdict == false -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Bar $barNumber", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        playhead -> MaterialTheme.colorScheme.primaryContainer
                        label == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .border(if (playhead || (selected && verdict == null)) 3.dp else 2.dp, border, RoundedCornerShape(8.dp))
                .clickable { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label ?: "?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = if (label == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(2.dp))
        OutlinedButton(
            onClick = onPlay,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        ) { Text("▶") }
        if (verdict != null) {
            Text(
                if (verdict) "✔" else "✘ ${answer ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = if (verdict) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * #6/Signal: the fixed answer pad for one bar — degree keys (4 per row) with a
 * Major/Minor shift that relabels the same shared chords, and, when the level
 * uses them, an expandable "7th ▾" extensions row. Always visible (replaces the
 * old [AlertDialog]-based popup keyboard); it targets whichever bar is currently
 * selected via the [BarSquare] row above it. Triads / fixed-7ths commit on the
 * degree tap; extended/mix mode waits for an extension tap to commit. Reuses the
 * exact same [EarTrainingState.guessChallengeKeyboard]/[EarTrainingState.clearChallengeBar]
 * commit logic the popup used — only the placement changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeAnswerPad(ear: EarTrainingState, bar: Int) {
    var pickedDeg by remember(bar) { mutableStateOf<Int?>(null) }     // relative-major degree
    var pickedRoman by remember(bar) { mutableStateOf<String?>(null) }
    var pickedExt by remember(bar) { mutableStateOf<String?>(null) }
    var extOpen by remember(bar) { mutableStateOf(false) }
    val needsExt = ear.challengeNeedsExt && !ear.challengeCombinedMode
    // Extension options depend on the picked degree (only its diatonic extensions).
    val extOptions = pickedDeg?.let { ear.challengeExtOptionsForDegree(it) } ?: emptyList()

    fun reset() { pickedDeg = null; pickedRoman = null; pickedExt = null; extOpen = false }
    fun commit(ext: String?) {
        val deg = pickedDeg ?: return
        ear.guessChallengeKeyboard(bar, deg, pickedRoman ?: deg.toString(), ext)
        reset()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            // Major/Minor shift sits on the LEFT; the bar label fills the rest, right-aligned.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = !ear.keyboardMinor,
                    onClick = { if (ear.keyboardMinor) ear.toggleKeyboardShift() },
                    label = { Text("Major") },
                )
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = ear.keyboardMinor,
                    onClick = { if (!ear.keyboardMinor) ear.toggleKeyboardShift() },
                    label = { Text("⇧ Minor") },
                )
                Text("Bar ${bar + 1} answer", style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            // Degree grid — 4 columns (I ii iii IV / V vi vii° / …), plus the
            // extensions key when this level needs one.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 4,
            ) {
                for ((majDeg, roman) in ear.keyboardKeys()) {
                    FilterChip(
                        selected = pickedDeg == majDeg,
                        onClick = {
                            // Changing the degree invalidates the chosen extension.
                            if (pickedDeg != majDeg) { pickedExt = null; extOpen = false }
                            pickedDeg = majDeg; pickedRoman = roman
                            if (!needsExt) commit(null)
                        },
                        label = { Text(roman) },
                    )
                }
                if (needsExt) {
                    FilterChip(
                        selected = extOpen,
                        onClick = { extOpen = !extOpen },
                        label = { Text("7th ▾") },
                    )
                }
            }
            if (needsExt && extOpen) {
                Spacer(Modifier.height(8.dp))
                if (pickedDeg == null) {
                    Text("Pick a degree first — its valid extensions appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (ext in extOptions) {
                            FilterChip(
                                selected = pickedExt == ext,
                                onClick = { pickedExt = ext; commit(ext) },
                                label = { Text(if (ext.isEmpty()) "triad" else ext) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { ear.clearChallengeBar(bar); reset() }) { Text("Clear bar ${bar + 1}") }
        }
    }
}

/** 72dp progress ring for the Progression Challenge: a muted (outline) track with
 *  an act-colored arc sweeping to the answered fraction, and "Q n/N" centered. */
@Composable
private fun ChallengeProgressRing(index: Int, total: Int) {
    val trackColor = MaterialTheme.colorScheme.outline
    val actColor = MaterialTheme.colorScheme.primary
    val fraction = (index.toFloat() / total).coerceIn(0f, 1f)
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            if (fraction > 0f) {
                drawArc(color = actColor, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, style = stroke)
            }
        }
        Text(
            "Q ${index + 1}/$total",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Per-question dot strip for the Progression Challenge: teal = fully right,
 *  coral (error) = wrong, act-filled with a ring = the current question, muted
 *  (outline) = upcoming/unanswered. Derived from [EarTrainingState.challengeAnswers]
 *  and [EarTrainingState.challengeIndex]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeDotStrip(ear: EarTrainingState) {
    val palette = LocalSignal.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until ear.challengeTotal) {
            val isCurrent = i == ear.challengeIndex
            val answer = ear.challengeAnswers.getOrNull(i)
            val dotColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                answer == true -> palette.feedback
                answer == false -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(
                        if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier,
                    ),
            )
        }
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
            // Per-question dot strip (wraps so all the questions fit on a narrow screen)
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
            ) {
                // #3: always a plain number — never reveal quality (major/minor/7th/♯)
                // on the play button; the reveal card below shows the answer.
                Text("▶ ${i + 1}")
            }
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
private fun AdvancedProgressionView(state: AppState, ear: EarTrainingState) {
    var settingsOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Borrowed chords, secondary dominants and chromatic moves. Pick a key, generate one, " +
            "try to identify it, then reveal the name, Roman numerals and chords.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        // Key picker + generator choice + Library now live behind the summary card
        // (Signal move — same treatment as the diatonic Progressions view).
        GeneratorSummaryCard(ear, onClick = { settingsOpen = true })
        Spacer(Modifier.height(10.dp))
        if (ear.advProg == null) {
            Button(onClick = { ear.nextAdvancedProgression() }, modifier = Modifier.fillMaxWidth()) {
                Text("Generate progression ▶", style = MaterialTheme.typography.titleMedium)
            }
            if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
            return@Column
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // #7: ← Prev restores the previously generated progression (reveal reset).
            OutlinedButton(
                onClick = { ear.previousAdvancedProgression() },
                enabled = ear.canGoPrevAdvanced,
            ) { Text("← Prev") }
            OutlinedButton(onClick = { ear.nextAdvancedProgression() }) { Text("Next →") }
            ProgressionSongsButton(ear)
        }
        Spacer(Modifier.height(10.dp))
        TransposeClicker(ear)
        Spacer(Modifier.height(12.dp))
        AdvancedProgressionBody(ear)
        Spacer(Modifier.height(20.dp))
    }
    if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedChallengeView(state: AppState, ear: EarTrainingState) {
    var settingsOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!ear.advChActive) {
            Text("${ear.advChallengeTotal} advanced progressions in a row. Listen, try to identify each, " +
                "then reveal and mark yourself. A teaching note is shown for every one.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            GeneratorSummaryCard(ear, onClick = { settingsOpen = true })
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { ear.startAdvChallenge() }) { Text("Start challenge ▶") }
            }
            if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
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
        Spacer(Modifier.height(8.dp))
        ProgressionSongsButton(ear)
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
private fun InversionsView(state: AppState, ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("A chord plays in some inversion (which chord tone is in the bass). Identify it. " +
            "Pick which chord types can appear below.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        InversionQualityPalette(ear)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ear.playInversion() }, enabled = ear.invStarted && !ear.invPlaying) {
                Text(if (ear.invPlaying) "Playing…" else "Replay ▶")
            }
            OutlinedButton(onClick = { ear.inversionPrev() }, enabled = ear.invHasPrev) { Text("◀ Prev") }
            OutlinedButton(onClick = { ear.inversionNext() }, enabled = ear.invHasNext) { Text("Next ▶") }
            OutlinedButton(onClick = { ear.newInversion() }, enabled = !ear.invPlaying) { Text("New chord +") }
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
        Spacer(Modifier.height(12.dp))
        ChordOnFretboard(state, NoteSpeller.spell(ear.invRoot) + ear.invQuality,
            ear.invShowFretboard) { ear.invShowFretboard = it }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InversionsChallengeView(state: AppState, ear: EarTrainingState) {
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
            IconButton(onClick = { ear.startInvChallenge() }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = "Restart challenge")
            }
            IconButton(onClick = { ear.exitInvChallenge() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Quit challenge")
            }
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
            Spacer(Modifier.height(10.dp))
            // Post-answer only: showing the chord earlier would leak the answer.
            ChordOnFretboard(state, NoteSpeller.spell(ear.invRoot) + ear.invQuality,
                ear.invShowFretboard) { ear.invShowFretboard = it }
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
private fun AugDimView(state: AppState, ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Tell augmented from diminished by ear. Enable the qualities you want to drill " +
            "(add 7th/extended forms below), then identify each chord.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        AugDimPalette(ear)
        Spacer(Modifier.height(10.dp))
        // Replay is the primary (filled) action so it isn't confused with the
        // chord-advancing buttons (#1). New/Previous/Next are secondary.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ear.playAugDim() }, enabled = ear.adStarted) { Text("Replay ▶") }
            OutlinedButton(onClick = { ear.augDimPrev() }, enabled = ear.adHasPrev) { Text("◀ Prev") }
            OutlinedButton(onClick = { ear.augDimNext() }, enabled = ear.adHasNext) { Text("Next ▶") }
            OutlinedButton(onClick = { ear.newAugDim() }) { Text("New chord +") }
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
        Spacer(Modifier.height(12.dp))
        ChordOnFretboard(state, NoteSpeller.spell(ear.adRoot) + ear.adQuality,
            ear.adShowFretboard) { ear.adShowFretboard = it }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AugDimChallengeView(state: AppState, ear: EarTrainingState) {
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
            IconButton(onClick = { ear.startAugDimChallenge() }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = "Restart challenge")
            }
            IconButton(onClick = { ear.exitAugDimChallenge() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Quit challenge")
            }
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
            Spacer(Modifier.height(10.dp))
            // Post-answer only: showing the chord earlier would leak the answer.
            ChordOnFretboard(state, NoteSpeller.spell(ear.adRoot) + ear.adQuality,
                ear.adShowFretboard) { ear.adShowFretboard = it }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ======================================================================================
// #6  Interval identification (ascending / descending)
// ======================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalGuessChips(ear: EarTrainingState, enabled: Boolean) {
    Text("Which interval?", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (iv in app.guitar.theory.IntervalTrainer.INTERVALS) {
            FilterChip(
                selected = ear.intervalGuess == iv.semitones,
                enabled = enabled,
                onClick = { ear.intervalGuess = iv.semitones },
                label = { Text(iv.shortName) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalsView(ear: EarTrainingState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!ear.intervalChActive) {
            Text("${ear.intervalChallengeTotal} questions. A I–V–I cadence sets the key, then the " +
                "tonic and a note sound — identify the interval. Choose a direction first; you can " +
                "always replay the tonic, and transpose if the key is uncomfortable.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text("Direction", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (dir in app.guitar.theory.IntervalDirection.entries) {
                    FilterChip(
                        selected = ear.intervalDirection == dir,
                        onClick = { ear.intervalDirection = dir },
                        label = { Text(dir.name) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Playback", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !ear.intervalHarmonic,
                    onClick = { ear.intervalHarmonic = false },
                    label = { Text("Melodic (one after the other)") })
                FilterChip(selected = ear.intervalHarmonic,
                    onClick = { ear.intervalHarmonic = true },
                    label = { Text("Harmonic (together)") })
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Key: ${NoteSpeller.spell(ear.intervalKey)} major",
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(150.dp))
                OutlinedButton(onClick = { ear.intervalTranspose(-1) }) { Text("♭") }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = { ear.intervalTranspose(1) }) { Text("♯") }
                Spacer(Modifier.width(8.dp))
                Text(transposeLabel(ear.intervalTransposeSteps), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { ear.startIntervalChallenge() }) { Text("Start challenge ▶") }
            }
            return@Column
        }
        if (ear.intervalChIndex >= ear.intervalChallengeTotal) {
            SimpleDoneCard(ear.intervalChScore, ear.intervalChallengeTotal,
                onRestart = { ear.startIntervalChallenge() }, onExit = { ear.exitIntervalChallenge() })
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Q ${ear.intervalChIndex + 1} / ${ear.intervalChallengeTotal}",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Score: ${ear.intervalChScore}", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { ear.startIntervalChallenge() }) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = "Restart challenge")
            }
            IconButton(onClick = { ear.exitIntervalChallenge() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Quit challenge")
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ear.playIntervalQuestion() }, enabled = !ear.intervalPlaying) { Text("Replay ▶") }
            OutlinedButton(onClick = { ear.playIntervalTonic() }) { Text("♪ Tonic") }
            OutlinedButton(onClick = { ear.playIntervalTonicCadence() }) { Text("Hear I–V–I") }
            OutlinedButton(onClick = { ear.intervalTranspose(-1) }) { Text("♭") }
            OutlinedButton(onClick = { ear.intervalTranspose(1) }) { Text("♯") }
            Text(transposeLabel(ear.intervalTransposeSteps), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(12.dp))
        IntervalGuessChips(ear, enabled = !ear.intervalChAnswered)
        Spacer(Modifier.height(10.dp))
        if (!ear.intervalChAnswered) {
            Button(onClick = { ear.submitIntervalGuess() }, enabled = ear.intervalGuess != null,
                modifier = Modifier.fillMaxWidth()) { Text("Submit") }
        } else {
            val ok = ear.intervalGuess == ear.intervalSemitones
            val dir = if (ear.intervalAscending) "ascending" else "descending"
            Text((if (ok) "✔ correct" else "✘ answer: ${app.guitar.theory.IntervalTrainer.choiceFor(ear.intervalSemitones).longName}") +
                "  ($dir)",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { ear.advanceIntervalChallenge() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (ear.intervalChIndex == ear.intervalChallengeTotal - 1) "See score →" else "Next →")
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ======================================================================================
// Stats — per-kind challenge history (recorded on every completed challenge)
// ======================================================================================

private fun statsKindLabel(kind: String): String = when (kind) {
    "progression" -> "Progressions"
    "note2chord"  -> "Note→Chord"
    "flavor"      -> "Flavor"
    "inversions"  -> "Inversions"
    "augdim"      -> "Aug / Dim"
    "intervals"   -> "Intervals"
    else -> kind
}

/** Internal (not private): reused by [MoreScreen] (Shell.kt) so "Challenge stats"
 *  opens the exact same dialog from the More screen as it does from this header. */
@Composable
internal fun EarStatsDialog(state: AppState, onDismiss: () -> Unit) {
    val scores by state.challengeScores.collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Challenge stats") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (scores.isEmpty()) {
                    Text("No completed challenges yet — finish any 10-question " +
                        "challenge and it lands here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }
                val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                for ((kind, rows) in scores.groupBy { it.kind }) {
                    val best = rows.first()   // repo stores rows best-first per kind
                    val avg = rows.sumOf { it.score * 100.0 / it.total } / rows.size
                    val last = rows.maxByOrNull { it.dateMillis }
                    Text(statsKindLabel(kind), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(
                        "best ${best.score}/${best.total}  ·  avg ${avg.toInt()}%  ·  " +
                            "${rows.size} run${if (rows.size == 1) "" else "s"}" +
                            (last?.let { "  ·  last ${fmt.format(java.util.Date(it.dateMillis))}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
    )
}

// ======================================================================================
// Progression library — the pools the trainer draws from (major / minor / advanced / circle)
// ======================================================================================

@Composable
private fun ProgressionLibraryDialog(state: AppState, onDismiss: () -> Unit) {
    val ear = state.earTraining
    // Single-open accordion: at most one row's drop-down is open at a time. Opening a
    // new row closes the previously-open one.
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val toggle: (String) -> Unit = { key ->
        // Any change of which row is open stops whatever the preview player was sounding.
        ear.libraryStop()
        expandedKey = if (expandedKey == key) null else key
    }
    val dismiss = { ear.libraryStop(); onDismiss() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = { TextButton(onClick = dismiss) { Text("Close") } },
        title = { Text("Progression library") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            ) {
                LibrarySection("Major (diatonic)", "Tap a progression for songs + to hear it (fixed key C major).") {
                    EarTraining.MAJOR_PROGRESSIONS.forEach { p ->
                        LibraryRow(state, "maj:${p.degrees}", EarTraining.romanLineFor(p),
                            ProgressionSongs.forDiatonic(p),
                            EarTraining.resolveProgression(p, PitchClass.C, ChordTypeLevel.Triads),
                            expandedKey, toggle)
                    }
                }
                LibrarySection("Minor (diatonic)", "Fixed key A minor.") {
                    EarTraining.MINOR_PROGRESSIONS.forEach { p ->
                        LibraryRow(state, "min:${p.degrees}", EarTraining.romanLineFor(p),
                            ProgressionSongs.forDiatonic(p),
                            EarTraining.resolveProgression(p, PitchClass.A, ChordTypeLevel.Triads),
                            expandedKey, toggle)
                    }
                }
                LibrarySection("Advanced (non-diatonic)",
                    "Characteristic examples — the signature harmonic move, not always note-for-note.") {
                    EarTraining.ADVANCED_PROGRESSIONS.forEach { np ->
                        val key = if (np.tonicMode == TrainingMode.Major) PitchClass.C else PitchClass.A
                        LibraryRow(state, "adv:${np.name}", "${np.name}:  ${np.romanLine}",
                            ProgressionSongs.forAdvanced(np.name), np.resolve(key),
                            expandedKey, toggle)
                    }
                }
                LibrarySection("Circle of fifths",
                    "Draws 4 adjacent chords; the 2nd may become a dominant 7th (except vii°). Characteristic examples.") {
                    EarTraining.CIRCLE_WINDOWS.forEach { w ->
                        LibraryRow(state, "cof:${w.id}", w.romanLine,
                            ProgressionSongs.forCircleWindow(w.id), w.resolve(PitchClass.C),
                            expandedKey, toggle)
                    }
                }
            }
        },
    )
}

@Composable
private fun LibrarySection(title: String, caption: String?, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
    if (caption != null) {
        Text(caption, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(2.dp))
    content()
    Spacer(Modifier.height(10.dp))
}

/** Best-effort E-shape (or first) voicing for a chord symbol, for the idle fretboard
 *  preview before playback starts. Null for unvoiceable/exotic chords. */
private fun previewShape(state: AppState, symbol: String): app.guitar.theory.ChordShape? {
    val parsed = app.guitar.theory.ChordLibrary.parse(symbol) ?: return null
    val (root, q) = parsed
    val shapes = app.guitar.theory.ChordShapeGenerator()
        .shapesFor(root, q, state.liveTuning, frets = DISPLAY_FRETS)
    return shapes.firstOrNull { it.cagedShape == app.guitar.theory.CagedShape.E } ?: shapes.firstOrNull()
}

/** A progression row in the library. Always clickable (▸/▾ chevron): tapping expands an
 *  indented panel with a Play/Stop button (loops the progression in a fixed key via the
 *  preview player), an optional follow-along fretboard, and the "Title — Artist" list. */
@Composable
private fun LibraryRow(
    state: AppState,
    key: String,
    label: String,
    songs: List<SongExample>,
    chords: List<ResolvedChord>,
    expandedKey: String?,
    onToggle: (String) -> Unit,
) {
    val ear = state.earTraining
    val isOpen = expandedKey == key
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(key) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(if (isOpen) "▾" else "▸", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
    }
    if (isOpen) {
        var showFb by remember { mutableStateOf(false) }
        val isPlaying = ear.libPlayingId == key
        Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (isPlaying) {
                    Button(onClick = { ear.libraryStop() }) { Text("Stop ■") }
                } else {
                    Button(onClick = { ear.libraryPlay(key, chords) }) { Text("Play ▶") }
                }
                Spacer(Modifier.weight(1f))
                Text("Fretboard", style = MaterialTheme.typography.labelMedium)
                Switch(checked = showFb, onCheckedChange = { showFb = it })
            }
            if (showFb) {
                // While playing, the board follows the preview player's live shape; when
                // idle it shows the progression's first chord as a static preview.
                val idleShape = remember(chords, state.liveTuning) {
                    chords.firstOrNull()?.let { previewShape(state, it.symbol) }
                }
                val shape = if (isPlaying) ear.libShape else idleShape
                val marks = remember(shape, state.labelMode) {
                    shape?.let { shapeMarks(it, state.labelMode) } ?: emptyMap()
                }
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 4.dp)) {
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
            if (songs.isNotEmpty()) {
                for (song in songs) {
                    Text("•  ${song.title} — ${song.artist}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Text("No song examples for this one.", style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

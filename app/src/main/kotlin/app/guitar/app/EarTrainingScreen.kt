package app.guitar.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsCar
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.ChordTypeLevel
import app.guitar.theory.CarMode
import app.guitar.theory.EarTraining
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.Progression
import app.guitar.theory.ProgressionSongs
import app.guitar.theory.ResolvedChord
import app.guitar.theory.SongExample
import app.guitar.theory.TrainingMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarTrainingScreen(state: AppState, onBack: () -> Unit) {
    // #7: use the app-lifetime instance so leaving and returning preserves state.
    val ear = state.earTraining
    // Stop audio/looping when NAVIGATING AWAY from the screen, but keep all state
    // (progression, reveals, counters) so returning shows exactly what you left.
    // Guard on currentSheet so a mere rotation (which disposes+recreates this
    // composable when the portrait/landscape layout swaps, without changing the
    // route) does NOT stop playback — only a real navigation away does.
    DisposableEffect(Unit) {
        onDispose { if (state.currentSheet != Sheet.EarTraining) { ear.stopLoop(); ear.libraryStop() } }
    }
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
            .padding(8.dp),
    ) {
        // Car mode owns the whole content column: no sub-mode chips, no Practice/
        // Challenge picker, no answer pad, no transport dock — just huge glanceable
        // slots. Returning here is what guarantees the dispatch `when` below and the
        // TransportDock never run in Car mode (they would fall into their else/Practice
        // branches). Only Progressions has a car mode for now.
        if (ear.earMode == EarMode.Car && ear.progSubMode == EarSubMode.Progression) {
            CarModeView(state, ear)
            return@Column
        }

        // Title row: title + (while a Progression challenge is in flight) pinned
        // Restart/Quit icons + Stats + Tune + Back.
        val progChallengeInFlight = ear.progSubMode == EarSubMode.Progression &&
            ear.earMode == EarMode.Challenge &&
            if (ear.specialProgMode) ear.advChActive && ear.advChIndex < ear.advChallengeTotal
            else ear.challengeActive && ear.challengeIndex < ear.challengeTotal
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "EAR TRAINING",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
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
        // Drill and Workout have no Practice/Challenge split, so their control is hidden.
        // While a Progression challenge is in flight both rows fold into ONE compact
        // dropdown (v2.64) — they cost two rows of prime space while answering.
        if (progChallengeInFlight) {
            ChallengeModeFold(state, ear)
            Spacer(Modifier.height(8.dp))
        } else {
            if (ear.progSubMode != EarSubMode.Drill && ear.progSubMode != EarSubMode.Workout) {
                SegmentedRow(
                    // NOT EarMode.entries — Car is entered from the challenge config
                    // screen, never from this picker (it would read as "Challenge").
                    options = listOf(EarMode.Practice, EarMode.Challenge),
                    selected = ear.earMode,
                    onSelect = { ear.earMode = it },
                    label = { if (it == EarMode.Practice) "Practice" else "Challenge" },
                )
                Spacer(Modifier.height(6.dp))
            }
            SubModeChipRow(ear)
        }

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
                EarSubMode.Drill -> DrillView(state, ear)
                EarSubMode.Workout -> WorkoutView(state, ear)
            }
        }

        // Transport dock (Signal move #2): replaces the per-view Play ▶/Stop ⏹
        // buttons for every Progression generator (diatonic/advanced/circle/iii-focus)
        // in both Practice and Challenge. progBpm is captured once when startLoop()
        // launches its coroutine, so a live BPM edit restarts the loop to take effect.
        if (ear.progSubMode == EarSubMode.Progression) {
            Spacer(Modifier.height(6.dp))
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
    EarSubMode.Drill       -> "Drill"
    EarSubMode.Workout     -> "Workout"
}

/** Height of one sub-mode chip. They wrap to two rows on a phone, so the stock
 *  32dp chip cost two rows' worth of slack above every single ear sub-mode. */
private val SUBMODE_CHIP_H = 30.dp

/** Sub-mode chip row (Signal move — replaces the SubModeDropdown): Progressions,
 *  Intervals and Note→Chord are always-visible chips; Flavor/Inversions/AugDim live
 *  behind a "More ▾" overflow chip (which shows the current sub-mode's name when
 *  the selection IS one of the overflowed ones, so the active mode is never hidden). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubModeChipRow(ear: EarTrainingState) {
    // Workout sits directly after Progressions, in the always-visible row — it's a daily
    // destination, not something to go hunting for behind "More".
    val primaryChips = listOf(EarSubMode.Progression, EarSubMode.Workout, EarSubMode.Intervals, EarSubMode.Note2Chord)
    val overflowChips = listOf(EarSubMode.Flavor, EarSubMode.Inversions, EarSubMode.AugDim, EarSubMode.Drill)
    var moreOpen by remember { mutableStateOf(false) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        for (s in primaryChips) {
            FilterChip(
                selected = ear.progSubMode == s,
                modifier = Modifier.height(SUBMODE_CHIP_H),
                onClick = { ear.switchTab(s) },
                label = { Text(subModeLabel(s)) },
            )
        }
        Box {
            val inOverflow = ear.progSubMode in overflowChips
            FilterChip(
                selected = inOverflow,
                modifier = Modifier.height(SUBMODE_CHIP_H),
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

/** The in-flight challenge header fold (v2.64): one compact chip replacing the
 *  Practice/Challenge switch + sub-mode chips while a Progression challenge runs.
 *  Expands to a dropdown holding those pickers plus the seldom-used tools that
 *  used to live in the body's "More tools" card: 1–5–1 cadence, re-roll, songs,
 *  the ♪ chords/notes reference toggle, drawn-from (source + generator —
 *  changeable mid-challenge, applies from the next question), transpose, and
 *  the key & mode reveal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeModeFold(state: AppState, ear: EarTrainingState) {
    var open by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = open,
            onClick = { open = !open },
            label = { Text("${subModeLabel(ear.progSubMode)} · Challenge · More tools  " + if (open) "▴" else "▾") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.width(300.dp).padding(horizontal = 10.dp, vertical = 4.dp)) {
                SegmentedRow(
                    options = listOf(EarMode.Practice, EarMode.Challenge),
                    selected = ear.earMode,
                    onSelect = { ear.earMode = it },
                    label = { if (it == EarMode.Practice) "Practice" else "Challenge" },
                )
                Spacer(Modifier.height(8.dp))
                SubModeChipRow(ear)
                if (ear.progSubMode == EarSubMode.Progression) {
                    // The second way into car mode: behind a deliberate fold tap, so it
                    // stays unreachable by a stray jab at the answer pad. Leaves the
                    // challenge untouched — car mode is never graded.
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { open = false; ear.enterCarMode() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.DirectionsCar, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Car mode — hands-free")
                    }
                }
                if (!ear.specialProgMode && ear.challengeActive) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { ear.playProgKeyCadence() }) { Text("Hear ${ear.progCadenceLabel()}") }
                        OutlinedButton(onClick = { ear.rerollChallengeQuestion() }) { Text("Re-roll") }
                        ProgressionSongsButton(ear)
                        // What the challenge screen's degree buttons play (moved here
                        // from the play row): the full diatonic chord, or the bare root.
                        FilterChip(
                            selected = true,
                            onClick = { ear.degreeRefChords = !ear.degreeRefChords },
                            label = { Text(if (ear.degreeRefChords) "♪ chords" else "♪ notes") },
                        )
                        // Answer pad walks with the playhead while the loop runs. Tapping a
                        // square by hand still wins for the rest of that question.
                        FilterChip(
                            selected = ear.challengeFollowPlayhead,
                            onClick = { ear.challengeFollowPlayhead = !ear.challengeFollowPlayhead },
                            label = { Text("Keys follow playhead") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Drawn from  (tap to change — applies to the next question)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                    ChallengeSourceRow(ear)
                    Spacer(Modifier.height(4.dp))
                    GeneratorSummaryCard(ear, onClick = { settingsOpen = true })
                    Spacer(Modifier.height(8.dp))
                    // Transpose shifts the key/chords but not the degrees — challenge-safe.
                    TransposeClicker(ear)
                    Spacer(Modifier.height(8.dp))
                    RevealCard(
                        label = "Key & Mode (hint)",
                        hidden = !ear.keyRevealed,
                        content = NoteSpeller.spell(ear.progKey) + "  " +
                            if (ear.progMode == TrainingMode.Major) "Major" else "Minor",
                        onToggle = { ear.toggleKeyModeReveal() },
                        modifier = Modifier.width(170.dp),
                        contentSizeSp = 15,
                    )
                }
            }
        }
    }
    if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
}

/** Short label for the current progression generator. */
private fun generatorLabel(ear: EarTrainingState): String = when {
    ear.advancedMode -> when (ear.advCategory) {
        "sus" -> "Sus chords"; "advanced2" -> "Advanced II"; else -> "Advanced"
    }
    ear.circleMode -> "Circle of 5ths"
    ear.iiiFocusMode -> "I → iii focus"
    ear.third6FocusMode -> "3rd vs 6th focus"
    else -> "Diatonic"
}

/** One-line teaching caption for the current progression generator. */
private fun generatorCaption(ear: EarTrainingState): String = when {
    ear.advancedMode && ear.advCategory == "sus" -> "Progressions built on suspended (sus2/sus4) chords."
    ear.advancedMode && ear.advCategory == "advanced2" -> "Richer colours: major-7th, minor-9th and modal (Dorian/Mixolydian/Lydian/Phrygian)."
    ear.advancedMode -> "Borrowed chords, secondary dominants & jazz turnarounds, each with a note."
    ear.circleMode -> "Circle-of-fifths windows built around secondary dominants (V7 of the next chord)."
    ear.iiiFocusMode -> "Drill for hearing the I→iii move — every progression opens with I then iii (major)."
    ear.third6FocusMode -> "Drill for telling the 3rd from the 6th — progressions with iii/bIII, mixed with ~30% I→vi (i→bVI) foils."
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
                onClick = { ear.chooseAdvancedMode(false); ear.chooseCircleMode(false); ear.chooseIiiFocusMode(false); ear.chooseThird6FocusMode(false); open = false })
            DropdownMenuItem(text = { Text("I → iii focus") },
                onClick = { ear.chooseIiiFocusMode(true); open = false })
            DropdownMenuItem(text = { Text("3rd vs 6th focus") },
                onClick = { ear.chooseThird6FocusMode(true); open = false })
            DropdownMenuItem(text = { Text("Advanced (non-diatonic)") },
                onClick = { ear.chooseAdvancedMode(true); open = false })
            DropdownMenuItem(text = { Text("Advanced II (maj7 / min9 / modal)") },
                onClick = { ear.chooseAdvancedCategory("advanced2"); open = false })
            DropdownMenuItem(text = { Text("Sus chords") },
                onClick = { ear.chooseAdvancedCategory("sus"); open = false })
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

        NoTonicBanner(ear, showRelativeLine = true)

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

        // Harmonic-minor progressions: add the major-V / V7 → i cadences to the minor
        // generator pool + library (default on). Only affects minor keys.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Harmonic minor (V7)", style = MaterialTheme.typography.labelMedium)
                Text("Add major-V → i cadences to the minor set",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = ear.earHarmonicMinor, onCheckedChange = { ear.earHarmonicMinor = it })
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
    // The playing bar uses the FEEDBACK (teal) hue, kept distinct from the coral
    // ACT/primary used for user selection elsewhere (see BarSquare).
    val playheadColor = LocalSignal.current.feedback
    val bg = when {
        isPlaying -> playheadColor.copy(alpha = 0.28f)
        hidden -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = bg),
        // Signal restyle: the currently-sounding bar gets a solid teal border so it
        // reads clearly even at a glance (not just the subtler container tint).
        border = if (isPlaying) BorderStroke(2.dp, playheadColor) else null,
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

// Compact density for the three blocks touched on EVERY question (v2.72.1). Stock
// Material sizes (40dp buttons, 32dp chips, 54dp squares) pushed the answer pad below
// the fold on a phone, so answering meant scrolling between the squares and the
// keyboard on every single bar. These sit deliberately below the 48dp touch-target
// guidance: they form a dense grid you are looking at while you tap, and scrolling
// mid-question costs far more accuracy than a slightly smaller target does.
private val CHALLENGE_REF_H = 30.dp          // the play + degree-reference row
private val CHALLENGE_SQUARE_H = 44.dp       // the bar square itself
private val CHALLENGE_SQUARE_PLAY_H = 26.dp  // its own play button
private val CHALLENGE_CHIP_H = 28.dp         // one degree/extension key
private val CHALLENGE_SHIFT_CHIP_H = 30.dp   // the Major/Minor shift, tapped far less


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
                "${ear.challengeTotal} progressions in a row. Listen, then tap the Roman numeral for " +
                    "each bar (and its extension when shown) — every question auto-scores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))

            Text(
                "Draw questions from",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            ChallengeSourceRow(ear)
            Spacer(Modifier.height(6.dp))

            GeneratorSummaryCard(ear, onClick = { settingsOpen = true })

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Button(onClick = { ear.startChallenge() }) {
                    Text("Start challenge ▶", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Hands-free car mode. Deliberately HERE and not in the transport dock or
            // the mode picker: this is the screen you sit on before driving, it honours
            // the generator settings shown just above, and it cannot be mis-tapped
            // while you are jabbing at Roman numerals mid-question.
            OutlinedButton(
                onClick = { ear.enterCarMode() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.DirectionsCar, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Car mode — hands-free")
            }
            Text(
                "5 plays per progression, revealing one more chord each play. Not graded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
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
        // Plain one-line counter (v2.64): the progress ring + per-question dot
        // strip cost ~80dp of height for information a text row carries fine.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Q ${ear.challengeIndex + 1}/${ear.challengeTotal}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Score: ${ear.challengeBarScore} / ${ear.challengeBarTotal} bars",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---- Compact core (v2.63, tightened v2.64): everything touched on every
        // question fits one screen-high stack — play + degree references, the four
        // answer squares, the answer pad, one nav row. Seldom-used tools fold into
        // the header dropdown (see ChallengeModeFold). ----

        var selectedBar by remember { mutableStateOf(0) }
        // Land back on bar 1 whenever a fresh question starts.
        LaunchedEffect(ear.challengeIndex) { selectedBar = 0 }
        // Follow-the-playhead: while the progression loops, the answer pad walks to the
        // bar being played, so you can answer in time instead of tapping a square first.
        // Disarmed for the rest of the question by any manual square tap.
        LaunchedEffect(ear.currentBar, ear.challengeFollowingPlayhead) {
            if (ear.challengeFollowingPlayhead) selectedBar = ear.currentBar
        }

        // ▶ Play + the 7 degree references share ONE compact row (v2.64). The
        // palette plays in the hidden key; its ♪ chords/notes toggle lives in the
        // header dropdown. These are the ONLY things that sound — the answer pad
        // below just selects.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { if (ear.isLooping) ear.stopLoop() else ear.startLoop() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(CHALLENGE_REF_H),
            ) { Text(if (ear.isLooping) "■ Stop" else "▶ Play", maxLines = 1) }
            for ((deg, label) in ear.challengeReferenceLabels()) {
                OutlinedButton(
                    onClick = { ear.auditionProgDegree(deg) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).height(CHALLENGE_REF_H),
                ) { Text(label, maxLines = 1) }
            }
        }

        Spacer(Modifier.height(8.dp))

        // The four bar squares: tap one to target it, answer from the pad below.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0 until 4) {
                val verdict = ear.challengeBarCorrect(i)
                BarSquare(
                    barNumber = i + 1,
                    label = ear.challengeGuessLabel.getOrNull(i),
                    // "(minor)" when the answer was the harmonic-minor dominant — a bare
                    // "V7" there is indistinguishable from the major key's V7.
                    labelTag = ear.challengeGuessTag(i),
                    verdict = verdict,
                    answer = if (verdict != null) ear.challengeAnswerLabel(i) else null,
                    selected = selectedBar == i,
                    playhead = ear.isLooping && ear.currentBar == i,   // playing "head"
                    onTap = { selectedBar = i; ear.disarmChallengeFollow() },
                    // Playing a bar also selects it, so it's the target of the answer pad.
                    onPlay = { selectedBar = i; ear.disarmChallengeFollow(); ear.playBarOnce(i) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // The no-tonic warning sits directly under the squares being filled — on top it
        // scrolled away from the answering area, which is where it matters.
        NoTonicBanner(ear, showRelativeLine = ear.challengeAllBarsAnswered)

        // Optional fretboard (v2.65: moved up from the bottom of the screen, where
        // checking it meant scrolling down and back up to hit ▶ on the next bar).
        // It answers the bar squares' ▶ buttons, so it belongs right under them.
        // Re-uses the same toggle as the Progressions sub-mode.
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

        Spacer(Modifier.height(6.dp))
        ChallengeAnswerPad(ear, bar = selectedBar)

        Spacer(Modifier.height(6.dp))

        // Single nav row — the old duplicated top+bottom nav cost a screen of height.
        // #4: advancing is always allowed — unanswered bars are credited as correct.
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
            ) { Text("← Prev") }
            Button(
                onClick = { ear.advanceChallenge() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E9E4F), contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f),
            ) { Text(if (ear.challengeIndex == ear.challengeTotal - 1) "See score →" else "Next →") }
        }
        Text(
            "Unanswered bars count as correct.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(12.dp))
    }
    if (settingsOpen) GeneratorSettingsSheet(state, ear, onDismiss = { settingsOpen = false })
}

/** Generator vs Drill-list question-source chips (start screen + "More tools").
 *  The Drill chip is disabled while nothing is tracked. */
@Composable
private fun ChallengeSourceRow(ear: EarTrainingState) {
    val n = ear.drillPoolSize
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = ear.challengeSource == ChallengeSource.Generator,
            onClick = { ear.challengeSource = ChallengeSource.Generator },
            label = { Text("Generator") },
        )
        FilterChip(
            selected = ear.challengeSource == ChallengeSource.DrillList,
            onClick = { ear.challengeSource = ChallengeSource.DrillList },
            enabled = n > 0,
            label = { Text(if (n > 0) "Drill list ($n)" else "Drill list (empty)") },
        )
    }
}

/** #6: one bar's answer square — a tappable tile that targets the fixed answer
 *  pad below it, showing the chosen chord label (or "?" when empty) plus a ▶ to
 *  hear the bar. [selected] marks the bar the pad currently answers for. */
@Composable
private fun BarSquare(
    barNumber: Int,
    label: String?,
    /** Small marker under [label] (e.g. "(minor)") when the numeral alone is ambiguous. */
    labelTag: String? = null,
    verdict: Boolean?,
    answer: String?,
    selected: Boolean = false,
    playhead: Boolean = false,
    onTap: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The playhead uses the distinct FEEDBACK (teal) hue so it never reads the
    // same as an ACT/primary-coloured user selection (coral). A selected bar the
    // playhead is on shows the teal fill+ring AND its coral selection border.
    val playheadColor = LocalSignal.current.feedback
    val border = when {
        selected && verdict == null -> MaterialTheme.colorScheme.primary
        playhead -> playheadColor
        verdict == true  -> MaterialTheme.colorScheme.primary
        verdict == false -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Bar $barNumber", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHALLENGE_SQUARE_H)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        playhead -> playheadColor.copy(alpha = 0.28f)
                        label == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .border(if (playhead || (selected && verdict == null)) 3.dp else 2.dp, border, RoundedCornerShape(8.dp))
                .clickable { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    label ?: "?",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = if (label == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                )
                // "(minor)" etc. — too long to sit inline next to a 22sp numeral in a
                // quarter-width square, so it gets its own small line.
                if (labelTag != null) {
                    Text(
                        labelTag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        OutlinedButton(
            onClick = onPlay,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.height(CHALLENGE_SQUARE_PLAY_H),
        ) { Text("▶", fontSize = 13.sp, maxLines = 1) }
        if (verdict != null) {
            Text(
                if (verdict) "✔" else "✘ ${answer ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                // 2 lines: a disambiguated answer ("V7 (minor)") doesn't fit a
                // quarter-width column on one line.
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    var pickedDominant by remember(bar) { mutableStateOf(false) }     // harmonic-minor V7 key
    var extOpen by remember(bar) { mutableStateOf(false) }
    val needsExt = ear.challengeNeedsExt && !ear.challengeCombinedMode
    // Extension options depend on the picked degree (only its diatonic extensions).
    val extOptions = pickedDeg?.let { ear.challengeExtOptionsForDegree(it) } ?: emptyList()

    fun reset() { pickedDeg = null; pickedRoman = null; pickedExt = null; pickedDominant = false; extOpen = false }
    fun commit(ext: String?) {
        val deg = pickedDeg ?: return
        ear.guessChallengeKeyboard(bar, deg, pickedRoman ?: deg.toString(), ext, pickedDominant)
        reset()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            // Major/Minor shift sits on the LEFT; the bar label + Clear fill the rest,
            // right-aligned (v2.64: Clear moved up here — its own bottom row cost a
            // full row of height). The chosen side fills SOLID with the primary color —
            // the stock FilterChip tint was too subtle to tell which keyboard is active.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = !ear.keyboardMinor,
                    onClick = { if (ear.keyboardMinor) ear.toggleKeyboardShift() },
                    modifier = Modifier.height(CHALLENGE_SHIFT_CHIP_H),
                    label = { Text("Major", fontWeight = if (!ear.keyboardMinor) FontWeight.Bold else FontWeight.Normal) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = ear.keyboardMinor,
                    onClick = { if (!ear.keyboardMinor) ear.toggleKeyboardShift() },
                    modifier = Modifier.height(CHALLENGE_SHIFT_CHIP_H),
                    label = { Text("Minor", fontWeight = if (ear.keyboardMinor) FontWeight.Bold else FontWeight.Normal) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                Text("Bar ${bar + 1}", style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { ear.clearChallengeBar(bar); reset() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) { Text("Clear") }
            }
            Spacer(Modifier.height(4.dp))
            // Degree grid — 4 columns (I ii iii IV / V vi vii° / …), plus the
            // extensions key when this level needs one.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                maxItemsInEachRow = 4,
            ) {
                for ((majDeg, roman) in ear.keyboardKeys()) {
                    FilterChip(
                        selected = pickedDeg == majDeg && !pickedDominant,
                        modifier = Modifier.height(CHALLENGE_CHIP_H),
                        onClick = {
                            // Changing the degree invalidates the chosen extension.
                            if (pickedDeg != majDeg || pickedDominant) { pickedExt = null; extOpen = false }
                            pickedDeg = majDeg; pickedRoman = roman; pickedDominant = false
                            if (!needsExt) commit(null)
                        },
                        label = { Text(roman) },
                    )
                }
                // Harmonic-minor dominant: a dedicated "V7"/"V" key (minor row + harmonic
                // toggle on) so a major V is marked distinctly from the natural `v`.
                if (ear.harmonicDominantVisible) {
                    val domMajDeg = ear.harmonicDominantMajDeg
                    val domLabel = ear.harmonicDominantLabel()
                    FilterChip(
                        selected = pickedDeg == domMajDeg && pickedDominant,
                        modifier = Modifier.height(CHALLENGE_CHIP_H),
                        onClick = {
                            if (pickedDeg != domMajDeg || !pickedDominant) { pickedExt = null; extOpen = false }
                            pickedDeg = domMajDeg; pickedRoman = domLabel; pickedDominant = true
                            if (!needsExt) commit(null)
                        },
                        label = { Text(domLabel) },
                    )
                }
                if (needsExt) {
                    FilterChip(
                        selected = extOpen,
                        modifier = Modifier.height(CHALLENGE_CHIP_H),
                        onClick = { extOpen = !extOpen },
                        label = { Text("7th ▾") },
                    )
                }
            }
            if (needsExt && extOpen) {
                Spacer(Modifier.height(6.dp))
                if (pickedDeg == null) {
                    Text("Pick a degree first — its valid extensions appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (ext in extOptions) {
                            FilterChip(
                                selected = pickedExt == ext,
                                modifier = Modifier.height(CHALLENGE_CHIP_H),
                                onClick = { pickedExt = ext; commit(ext) },
                                label = { Text(if (ext.isEmpty()) "triad" else ext) },
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
            // The bar the loop is currently sounding gets a filled playing-"head"
            // highlight in the FEEDBACK (teal) hue (mirrors the diatonic slot /
            // bar-square treatment; kept distinct from the coral selection colour).
            val playhead = ear.isLooping && ear.currentBar == i
            val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            // #3: always a plain number — never reveal quality (major/minor/7th/♯)
            // on the play button; the reveal card below shows the answer.
            if (playhead) {
                val teal = LocalSignal.current.feedback
                Button(
                    onClick = { ear.playProgChordDirect(i) },
                    contentPadding = pad,
                    colors = ButtonDefaults.buttonColors(containerColor = teal, contentColor = Color.White),
                ) { Text("▶ ${i + 1}") }
            } else {
                OutlinedButton(onClick = { ear.playProgChordDirect(i) }, contentPadding = pad) { Text("▶ ${i + 1}") }
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
    // "♪ Song refs" — the interval→song reference sheet (same content as the Theory tab).
    var refsOpen by remember { mutableStateOf(false) }
    if (refsOpen) IntervalRefsDialog(ear, onDismiss = { refsOpen = false })
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = { ear.startIntervalChallenge() }) { Text("Start challenge ▶") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { refsOpen = true }) { Text("♪ Song refs") }
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
            OutlinedButton(onClick = { refsOpen = true }) { Text("♪ Song refs") }
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
// Drill — repeat progressions the user misses most, with voicing control
// ======================================================================================

@Composable
private fun DrillView(state: AppState, ear: EarTrainingState) {
    val mistakes by state.progressionMistakes.collectAsState(initial = emptyMap())
    val entries = mistakes.entries
        .mapNotNull { e -> app.guitar.theory.EarTraining.progressionFromKey(e.key)?.let { Triple(e.key, e.value, it) } }
        .sortedByDescending { it.second }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Progressions you've missed in the Progression Challenge, most-missed first. Loop one to " +
            "drill it by ear — adjust each chord's voicing to isolate the sound you keep missing.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        if (entries.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("No mistakes tracked yet.", fontWeight = FontWeight.Bold)
                    Text("Finish a Progression Challenge; any progression you get wrong lands here to drill.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            return@Column
        }
        for ((key, count, prog) in entries) {
            val drilling = ear.isDrilling && ear.drillKey == key
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(app.guitar.theory.EarTraining.romanLineFor(prog) + tonicMark(prog), fontWeight = FontWeight.Bold)
                            Text("${if (prog.mode == app.guitar.theory.TrainingMode.Major) "Major" else "Minor"} · missed ${count}×",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (drilling) Button(onClick = { ear.stopDrill() }) { Text("■ Stop") }
                        else OutlinedButton(onClick = { ear.startDrill(key) }) { Text("▶ Loop") }
                        TextButton(onClick = { if (drilling) ear.stopDrill(); state.clearProgressionMistake(key) }) { Text("✕") }
                    }
                    if (drilling) DrillControls(ear)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Voicing + tempo panel shown under the row currently being drilled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrillControls(ear: EarTrainingState) {
    Spacer(Modifier.height(10.dp))
    Text("Tempo: ${ear.progBpm} BPM", style = MaterialTheme.typography.labelMedium)
    Slider(value = ear.progBpm.toFloat(), onValueChange = { ear.progBpm = it.toInt() }, valueRange = 40f..220f)
    Spacer(Modifier.height(4.dp))
    Text("Voicing per chord (tap to cycle)", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ear.drillInversions.forEachIndexed { i, inv ->
            FilterChip(
                selected = ear.drillBar == i,
                onClick = { ear.cycleDrillInversion(i) },
                label = { Text(drillBarLabel(ear, i, inv)) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { ear.setAllDrillInversions(2) }) { Text("5th in bass (all)") }
        OutlinedButton(onClick = { ear.setAllDrillInversions(0) }) { Text("Root (all)") }
        OutlinedButton(onClick = { ear.setAllDrillInversions(null) }) { Text("Auto (voice-led shell)") }
    }
    Text("Auto uses the app's voice-led shell voicing. Forcing an inversion plays a full close " +
        "voicing so the 5th is present and you control whether it sits above or below the root.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun drillBarLabel(ear: EarTrainingState, i: Int, inv: Int?): String {
    val parts = ear.drillProg?.let { app.guitar.theory.EarTraining.romanLineFor(it).split("  –  ") } ?: emptyList()
    val roman = parts.getOrNull(i) ?: (i + 1).toString()
    val invText = when (inv) { null -> "auto"; 0 -> "root"; 1 -> "3rd bass"; 2 -> "5th bass"; else -> "7th bass" }
    return "$roman · $invText"
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
                    .heightIn(max = 460.dp)
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
                    Text(statsKindLabel(kind), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(
                        "best ${best.score}/${best.total}  ·  avg ${avg.toInt()}%  ·  " +
                            "${rows.size} run${if (rows.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    for (r in rows) {
                        val pct = (r.score * 100.0 / r.total).toInt()
                        Text(
                            "${r.score}/${r.total} ($pct%)  ·  ${"%.1f".format(r.durationMs / 1000.0)}s  ·  " +
                                fmt.format(java.util.Date(r.dateMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // Read-only on purpose: a stray thumb next to a score you just set should
                // not be able to erase your history. Deleting lives in Settings → Data,
                // behind a confirm.
                Text(
                    "To delete runs, open Settings → Data.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
}

// ======================================================================================
// Progression library — the pools the trainer draws from (major / minor / advanced / circle)
// ======================================================================================

/** Marker appended to a progression's Roman line when it has no tonic (no I/i): nothing
 *  anchors the key, so it's harder to place by ear — flagged as "difficult". A
 *  progression that carries its RELATIVE tonic instead (IV–V–iii–vi ends on vi, vi–V–IV–V
 *  opens on it — both are the relative minor's i) is not one of those: it has a home, just
 *  in the other key, so it gets the calmer marker. */
private fun tonicMark(p: Progression): String {
    if (!EarTraining.progressionLacksTonic(p)) return ""
    val rel = EarTraining.progressionRelativeTonicMode(p) ?: return "   ◆ no-tonic (hard)"
    return if (rel == TrainingMode.Minor) "   ◆ relative minor" else "   ◆ relative major"
}

/**
 * Prominent note about where a progression's home is, when it isn't where the Roman
 * numbering says.
 *
 * A progression with no I of its own still has a home whenever it CONTAINS the relative
 * tonic — IV–V–iii–vi finishes on vi and vi–V–IV–V opens on it, and both vi ARE the
 * relative minor's i. It is simply a minor-key progression wearing major numerals, so the
 * card states the minor reading; it only adds whether the progression also ends there
 * (IV–V–iii–vi cadences, vi–V–IV–V hangs on bVII). Calling that second one "no tonic" was
 * a bug — it opens on its tonic. The hard warning is now reserved for a progression that
 * holds neither tonic: with no home to measure the other functions against, a wrong key
 * guess stays wrong for all four bars. Shown in Practice and in Challenge, where you meet
 * the progression, not only in the library.
 */
@Composable
private fun NoTonicBanner(ear: EarTrainingState, showRelativeLine: Boolean) {
    val prog = ear.progProgression ?: return
    if (!EarTraining.progressionLacksTonic(prog)) return
    val relativeMode = EarTraining.progressionRelativeTonicMode(prog)
    val relative = relativeMode != null
    val relWord = if (relativeMode == TrainingMode.Minor) "minor" else "major"
    val homeWord = if (relativeMode == TrainingMode.Minor) "major" else "minor"
    // The relative-tonic reading is information, not a warning — it uses the neutral
    // surface so it can't read as "you got something wrong".
    Surface(
        color = if (relative) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        val fg = if (relative) MaterialTheme.colorScheme.onSurfaceVariant
                 else MaterialTheme.colorScheme.onErrorContainer
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                if (relative) "◆  READS IN THE RELATIVE ${relWord.uppercase()}  ◆" else "◆  NO TONIC  ◆",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = fg,
            )
            if (relative) {
                val endsHome = EarTraining.progressionEndsOnRelativeTonic(prog)
                val bar = EarTraining.progressionRelativeTonicBar(prog)
                Text(
                    (if (endsHome) "No I in this key, but it lands on the relative tonic"
                     else "No I in this key, but bar $bar IS the relative tonic — it just " +
                         "doesn't end there") +
                        " — so hear it from the $relWord, not the $homeWord." +
                        if (showRelativeLine) " Relative to the $relWord scale it is:" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = fg,
                )
                // The relative Roman line names every bar, so it is the ANSWER: printed in
                // Practice and the library, never on an unanswered challenge question.
                if (showRelativeLine) {
                    Text(
                        EarTraining.relativeRomanLineFor(prog),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    "This progression holds no tonic at all, in either key — one of the " +
                        "hard ones. Don't wait to hear home; judge each chord by its pull instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = fg,
                )
            }
        }
    }
}

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
                        LibraryRow(state, "maj:${p.degrees}", EarTraining.romanLineFor(p) + tonicMark(p),
                            ProgressionSongs.forDiatonic(p),
                            EarTraining.resolveProgression(p, PitchClass.C, ChordTypeLevel.Triads),
                            expandedKey, toggle)
                    }
                }
                LibrarySection("Minor (diatonic)", "Fixed key A minor.") {
                    EarTraining.MINOR_PROGRESSIONS.forEach { p ->
                        LibraryRow(state, "min:${p.degrees}", EarTraining.romanLineFor(p) + tonicMark(p),
                            ProgressionSongs.forDiatonic(p),
                            EarTraining.resolveProgression(p, PitchClass.A, ChordTypeLevel.Triads),
                            expandedKey, toggle)
                    }
                }
                if (ear.earHarmonicMinor) {
                    LibrarySection("Minor — harmonic (V7 → i)",
                        "Major-V cadences (raised leading tone). Toggle off in the generator settings.") {
                        EarTraining.MINOR_HARMONIC_PROGRESSIONS.forEach { p ->
                            LibraryRow(state, "minH:${p.degrees}${p.dominantBars}", EarTraining.romanLineFor(p) + tonicMark(p),
                                ProgressionSongs.forHarmonicMinor(p),
                                EarTraining.resolveProgression(p, PitchClass.A, ChordTypeLevel.Triads),
                                expandedKey, toggle)
                        }
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
                LibrarySection("Advanced II (maj7 / min9 / modal)",
                    "Extended and modal colours — seventh chords and modal vamps.") {
                    EarTraining.ADVANCED2_PROGRESSIONS.forEach { np ->
                        val key = if (np.tonicMode == TrainingMode.Major) PitchClass.C else PitchClass.A
                        LibraryRow(state, "adv2:${np.name}", "${np.name}:  ${np.romanLine}",
                            ProgressionSongs.forAdvanced(np.name), np.resolve(key),
                            expandedKey, toggle)
                    }
                }
                LibrarySection("Suspended (sus2 / sus4)",
                    "The tension-and-release of suspended chords.") {
                    EarTraining.SUS_PROGRESSIONS.forEach { np ->
                        val key = if (np.tonicMode == TrainingMode.Major) PitchClass.C else PitchClass.A
                        LibraryRow(state, "sus:${np.name}", "${np.name}:  ${np.romanLine}",
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
                for (song in songs) SongLinkRow(song.title, song.artist)
            } else {
                Text("No song examples for this one.", style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


// ---------------------------------------------------------------------------------
// Car mode - hands-free progression drill
// ---------------------------------------------------------------------------------

/**
 * The whole content column while [EarMode.Car] is active: a read-only generator line,
 * one huge row of chord slots, round dots, and three thumb-sized buttons.
 *
 * Everything here is sized to be read at a glance from a dash mount, and NOTHING here
 * is graded - the reveals are the feedback. The labels are Roman-numeral functions,
 * never chord symbols, and the key is never shown (see the ear-training digest: work
 * directly in function, start each exercise guitarless).
 */
@Composable
private fun CarModeView(state: AppState, ear: EarTrainingState) {
    // Never let the screen sleep mid-exercise. keepScreenOn needs no permission (that
    // is only for PowerManager wake locks), and the DisposableEffect releases it on
    // exit - including the dispose+recreate a rotation causes, which re-applies it.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- top bar: what/where + Exit ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "CAR MODE",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            val where = if (ear.carExerciseCount == 0) {
                ""
            } else {
                val play = if (ear.carRound > 0) " - play " + ear.carRound + "/" + CarMode.ROUNDS else ""
                "Exercise " + ear.carExerciseCount + play
            }
            Text(
                where,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { ear.exitCarMode() }) { Text("Exit") }
        }

        // Read-only: car mode honours whatever the challenge config screen was set to,
        // but nothing here opens a sheet - no settings while driving.
        Text(
            generatorLabel(ear) + "  -  " + levelLabel(ear),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        if (ear.carPhase == CarPhase.Idle && ear.carRound == 0) {
            // ---- idle: one big Start ----
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { ear.startCarExercise() },
                        modifier = Modifier.height(72.dp).fillMaxWidth(0.7f),
                    ) { Text("Start", style = MaterialTheme.typography.headlineSmall) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "about " + ear.carExerciseSeconds + " s per exercise  -  " +
                            CarMode.ROUNDS + " plays  -  one more chord revealed each play" +
                            "\ntap a slot to peek at it early",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            // ---- the slots: the only thing you should need to see while driving ----
            val slots = ear.progResolved.size.coerceAtLeast(1)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Size the label so the LONGEST label in this progression fits the slot
                // width (a bold glyph is ~0.62 em wide), capped by the slot height. Both
                // orientations are handled by one layout: landscape just raises the
                // height cap. Sizing off the longest label (not each one) keeps the type
                // from jumping as reveals come in, and stops "Imaj13" being clipped.
                val perSlot = maxWidth / slots
                val chars = ear.carLongestLabel.coerceAtLeast(1)
                val byWidth = perSlot.value * 0.80f / (0.62f * chars)
                val byHeight = maxHeight.value * 0.5f
                // No lower clamp: byWidth is already the largest size that FITS, and
                // coercing it UP re-introduced clipping on the 6-8 chord advanced
                // progressions (7 slots, a 5-glyph "#IV°7" wants ~13sp in a ~44dp slot).
                val labelSp = minOf(byWidth, byHeight).coerceAtMost(132f).coerceAtLeast(8f).sp
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (i in 0 until slots) {
                        val sounding = ear.currentBar == i && ear.carPhase == CarPhase.Playing
                        val revealed = ear.carSlotRevealed(i)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (sounding) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                // Tap to peek: the whole slot is the target, because at
                                // arm's length in a car nothing smaller is hittable.
                                .clickable { ear.toggleCarSlot(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    ear.carSlotLabel(i),
                                    fontSize = labelSp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = if (revealed) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    },
                                )
                                // "(minor)" / "(major)" only for a V that reads the same
                                // in both keys — a small second line, so it can never
                                // crowd the number you are actually glancing at.
                                val tag = ear.carSlotTag(i)
                                if (tag.isNotEmpty()) {
                                    Text(
                                        tag,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- round dots: the only progress affordance ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                for (r in 1..CarMode.ROUNDS) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (r <= ear.carRound) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- the three thumb-sized actions ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { ear.replayCarExercise() },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Replay " + CarMode.ROUNDS + "x") }
                Button(
                    onClick = { ear.startCarExercise() },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Next") }
                OutlinedButton(
                    onClick = { ear.stopCarExercise() },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Stop") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Switch(checked = ear.carAutoAdvance, onCheckedChange = { ear.chooseCarAutoAdvance(it) })
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ear.carAutoAdvance) {
                        "Auto-advance (" + (CarMode.GAP_MS / 1000) + " s gap)"
                    } else {
                        "Auto-advance off"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // The chord voice. Reads only what the screen already shows, so it never
            // gives away a slot the schedule is still holding back.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Switch(checked = ear.carSpeakChords, onCheckedChange = { ear.chooseCarSpeakChords(it) })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (ear.carSpeakChords) "Speak each chord as it appears" else "Chord voice off",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ear.carSpeakChords) {
                        Text(
                            "Over the music — \"4 minor\" for iv, \"4 major\" for IV.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Level slider. Releasing it speaks a sample so it can be set by ear without
            // waiting for the next chord to come round.
            if (ear.carSpeakChords) {
                Text(
                    "Voice level: ${(ear.carSpeechVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = ear.carSpeechVolume,
                    onValueChange = { ear.chooseCarSpeechVolume(it) },
                    onValueChangeFinished = { ear.previewCarSpeech() },
                    valueRange = CarMode.SPEECH_VOLUME_MIN..CarMode.SPEECH_VOLUME_MAX,
                )
                Text(
                    "100% is as loud as the voice goes — past that, raise the device volume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

package app.guitar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.guitar.audio.AudioEngine
import app.guitar.audio.AudioTrackEngine
import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordShape
import app.guitar.theory.ChordShapeGenerator
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.ScaleLibrary
import app.guitar.theory.ScalePosition
import app.guitar.theory.ScalePositions
import app.guitar.theory.Tunings

class MainActivity : ComponentActivity() {
    private val audioEngine: AudioEngine = AudioTrackEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuitarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(audioEngine)
                }
            }
        }
    }

    override fun onDestroy() {
        audioEngine.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(audio: AudioEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { TuningRepository(context.applicationContext) }
    val state = remember { AppState(repo, scope, audio) }

    val customTunings by state.customTunings.collectAsState(initial = emptyMap())
    val savedSelected by state.savedSelectedName.collectAsState(initial = "Standard")
    val persistedLeftHanded by repo.leftHanded.collectAsState(initial = false)
    val persistedVoicingShell by repo.voicingShell.collectAsState(initial = false)
    val persistedLabelMode by repo.labelMode.collectAsState(initial = LabelMode.Intervals.name)
    val persistedA4 by repo.a4Hz.collectAsState(initial = 440f)
    val persistedSustain by repo.ringSustainMs.collectAsState(initial = 1500)
    val persistedStrum by repo.strumMs.collectAsState(initial = 30)
    val persistedTapOnTouchDown by repo.tapOnTouchDown.collectAsState(initial = false)
    val persistedInstrument by repo.instrument.collectAsState(initial = app.guitar.theory.Instrument.Guitar.name)

    LaunchedEffect(savedSelected, customTunings) {
        if (!state.isEditedTuning) {
            state.tuningName = savedSelected
            state.liveTuning = Tunings.all[savedSelected]
                ?: customTunings[savedSelected]
                ?: Tunings.standard
        }
    }
    LaunchedEffect(persistedLeftHanded) { state.leftHanded = persistedLeftHanded }
    LaunchedEffect(persistedVoicingShell) {
        state.voicingStyle =
            if (persistedVoicingShell) app.guitar.theory.VoicingStyle.Shell
            else app.guitar.theory.VoicingStyle.Standard
    }
    LaunchedEffect(persistedLabelMode) {
        state.labelMode = runCatching { LabelMode.valueOf(persistedLabelMode) }.getOrDefault(LabelMode.Notes)
    }
    LaunchedEffect(persistedA4) { state.a4Hz = persistedA4 }
    LaunchedEffect(persistedSustain) { state.ringSustainMs = persistedSustain }
    LaunchedEffect(persistedStrum) { state.strumMs = persistedStrum }
    LaunchedEffect(persistedTapOnTouchDown) { state.tapOnTouchDown = persistedTapOnTouchDown }
    LaunchedEffect(persistedInstrument) {
        state.instrument = runCatching { app.guitar.theory.Instrument.valueOf(persistedInstrument) }
            .getOrDefault(app.guitar.theory.Instrument.Guitar)
    }
    DisposableEffect(Unit) { onDispose { audio.stop() } }

    // ---------- RECORD_AUDIO runtime permission ----------
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    // When the Tuner is requested and we don't yet have permission, request it.
    LaunchedEffect(state.currentSheet, micGranted) {
        if (state.currentSheet == Sheet.Tuner && !micGranted) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val parsedChord = ChordLibrary.parse(state.chordInput)
    val scalePc = try { NoteSpeller.parsePitchClass(state.scaleRoot) } catch (_: Exception) { null }
    val scale = ScaleLibrary.scales[state.scaleType]

    val chordShapes: List<ChordShape> = remember(parsedChord, state.liveTuning, state.voicingStyle, state.instrument) {
        if (parsedChord == null) emptyList()
        else {
            val (r, q) = parsedChord
            // No .take() cap — for Standard mode this is 5 CAGED shapes; for Shell it's
            // 4-5 drop-2 inversions. For qualities without canonical templates (e.g. 9, 13),
            // the brute-force generator still applies and the list could be longer.
            // Cavaquinho gets a wider fret-span allowance via state.instrument.maxFretSpan.
            ChordShapeGenerator(
                style = state.voicingStyle,
                maxFretSpan = state.instrument.maxFretSpan,
            ).shapesFor(r, q, state.liveTuning, frets = DISPLAY_FRETS).take(12)
        }
    }
    val scalePositions: List<ScalePosition> = remember(scalePc, scale, state.liveTuning) {
        if (scalePc != null && scale != null) {
            ScalePositions.forScale(scalePc, scale, state.liveTuning, numFrets = DISPLAY_FRETS)
        } else emptyList()
    }
    // Keep indices in range
    LaunchedEffect(chordShapes.size) { if (state.chordPositionIndex >= chordShapes.size) state.resetChordPosition() }
    LaunchedEffect(scalePositions.size) { if (state.scalePositionIndex >= scalePositions.size) state.resetScalePosition() }

    val marks: Map<FretPosition, FretMark> = remember(
        state.displayMode, state.chordView, state.scaleView,
        state.chordPositionIndex, state.scalePositionIndex,
        state.chordInput, state.scaleRoot, state.scaleType,
        state.liveTuning, state.labelMode, state.pickedPositions,
        parsedChord, scalePc, scale, chordShapes, scalePositions,
        state.isLooping, state.loopPlayingShape,
    ) {
        // When the loop is playing AND we have a current shape, override whatever
        // the user has set so the fretboard shows the chord that's sounding now.
        val loopShape = state.loopPlayingShape
        if (state.isLooping && loopShape != null) {
            return@remember shapeMarks(loopShape, state.labelMode)
        }
        when (state.displayMode) {
            DisplayMode.Chord -> {
                if (parsedChord == null) emptyMap()
                else if (state.chordView == ChordScaleView.AllNotes)
                    chordMarks(parsedChord.first, parsedChord.second, state.liveTuning, DISPLAY_FRETS, state.labelMode)
                else chordShapes.getOrNull(state.chordPositionIndex)?.let { shapeMarks(it, state.labelMode) } ?: emptyMap()
            }
            DisplayMode.Scale -> {
                if (scalePc == null || scale == null) emptyMap()
                else if (state.scaleView == ChordScaleView.AllNotes)
                    scaleMarks(scalePc, scale, state.liveTuning, DISPLAY_FRETS, state.labelMode)
                else scalePositions.getOrNull(state.scalePositionIndex)?.let {
                    scalePositionMarks(it, scalePc, state.liveTuning, state.labelMode)
                } ?: emptyMap()
            }
            DisplayMode.Pick -> pickedMarks(state)
            DisplayMode.None -> emptyMap()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()   // keep content clear of status bar + nav gesture
    ) {
        // Concept-A persistent navigation rail (milestone 1). Always visible so
        // the user can jump between tools without the menu.
        NavRail(state)
        HorizontalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (state.currentSheet == Sheet.Loop) {
                // Loop takes over the content area — it has its own controls and Back button.
                LoopScreen(state)
            } else if (state.currentSheet == Sheet.Tuner) {
                TunerScreen(state, onBack = { state.closeSheet() })
            } else if (state.currentSheet == Sheet.EarTraining) {
                EarTrainingScreen(state, onBack = { state.closeSheet() })
            } else if (state.currentSheet == Sheet.SambaLooper) {
                SambaLooperScreen(state, onBack = { state.closeSheet() })
            } else {
                StatusBar(state)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                // Fretboard fills all remaining vertical space (landscape-locked, so this
                // is always wider than tall — renders as a horizontal neck).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    FretboardView(
                        tuning = state.liveTuning,
                        marks = marks,
                        selectedPosition = state.selectedPosition,
                        onTap = { pos ->
                            if (state.displayMode == DisplayMode.Pick) state.togglePick(pos)
                            else state.tapPosition(pos)
                        },
                        numFrets = DISPLAY_FRETS,
                        leftHanded = state.leftHanded,
                        playOnTouchDown = state.tapOnTouchDown,
                        mutedStrings = if (state.displayMode == DisplayMode.Pick) state.mutedStrings else emptySet(),
                    )
                }
                SelectedPositionInfo(state.liveTuning, state.selectedPosition, parsedChord)
                // Tool controls live in the draggable bottom sheets (opened from the
                // rail or the menu), so the neck keeps its full height here.
                ContextBar(state, chordShapes, scalePositions)
            }
        }
    }

    // ---------- Tool bottom sheets (drag up from the bottom; scrollable) ----------
    // Chord / Scale / Pick / Options open as draggable bottom sheets so the neck
    // keeps its full height. Loop / Tuner / Ear / Drums are full-screen routes.
    val sheet = state.currentSheet
    if (sheet == Sheet.Fretboard || sheet == Sheet.Options) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { state.closeSheet() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            when (sheet) {
                Sheet.Fretboard -> FretboardSheet(state)
                Sheet.Options   -> OptionsSheet(state, customTunings)
                else -> {}
            }
        }
    }
}

private fun sheetLabel(s: Sheet): String = when (s) {
    Sheet.Fretboard -> "Fretboard"
    Sheet.Loop -> "Loop"
    Sheet.Options -> "Options"
    Sheet.Tuner -> "Tuner"
    Sheet.EarTraining -> "Ear"
    Sheet.SambaLooper -> "Drums"
}

@Composable
private fun StatusBar(state: AppState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        // #10: "Chorect" wordmark. The 'c' carries a strong negative kerning so the
        // following 't' tucks into it, making the "ct" read like a single 'd' glyph.
        val wordmark = buildAnnotatedString {
            append("Chore")
            withStyle(SpanStyle(letterSpacing = (-0.28).em)) { append("c") }
            append("t")
        }
        Text(
            wordmark,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        val summary = "${state.tuningName}${if (state.isEditedTuning) "*" else ""}  ·  " +
            state.liveTuning.openStrings.joinToString(" ") { NoteSpeller.spell(it.pitchClass) }
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Re-open the last-used sheet without going through the menu.
        state.lastSheet?.let { sh ->
            if (state.currentSheet == null) {
                TextButton(
                    onClick = { state.reopenLastSheet() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "↑ ${sheetLabel(sh)}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
        }
        // While the loop is playing, surface a stop control here so the user can
        // stop it without going back into the loop screen.
        if (state.isLooping) {
            TextButton(
                onClick = { state.stopLoop() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("⏹ Stop", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(2.dp))
        }
        // App-wide quick audio controls (strum spread + ring sustain). All tool
        // navigation now lives in the always-visible left rail, so the old
        // Ear / Tuner / Menu buttons here were redundant and have been removed.
        AudioQuickButton(state, compact = true)
    }
}

@Composable
private fun ContextBar(
    state: AppState,
    chordShapes: List<ChordShape>,
    scalePositions: List<ScalePosition>,
) {
    when (state.displayMode) {
        DisplayMode.Chord -> if (state.chordView == ChordScaleView.Positions && chordShapes.isNotEmpty()) {
            PositionScroller(
                label = run {
                    val sh = chordShapes.getOrNull(state.chordPositionIndex)
                    val fretsLabel = sh?.let {
                        val played = it.frets.filterNotNull()
                        if (played.isEmpty()) ""
                        else {
                            val lo = played.min()
                            val hi = played.max()
                            if (lo == hi) "fret $lo" else "frets $lo–$hi"
                        }
                    } ?: ""
                    "${sh?.chordName ?: ""}  ·  $fretsLabel  ·  ${state.chordPositionIndex + 1} / ${chordShapes.size}"
                },
                onPrev = { state.stepChordPosition(-1, chordShapes.size) },
                onNext = { state.stepChordPosition(+1, chordShapes.size) },
            )
        } else NoContextBar(state.displayMode)

        DisplayMode.Scale -> if (state.scaleView == ChordScaleView.Positions && scalePositions.isNotEmpty()) {
            PositionScroller(
                label = run {
                    val sp = scalePositions.getOrNull(state.scalePositionIndex)
                    val anchor = sp?.let {
                        "anchor ${NoteSpeller.spell(it.anchorPitchClass)} · frets ${it.firstFret}–${it.lastFret}"
                    } ?: ""
                    "${state.scaleRoot} ${state.scaleType}  ·  $anchor  ·  ${state.scalePositionIndex + 1} / ${scalePositions.size}"
                },
                onPrev = { state.stepScalePosition(-1, scalePositions.size) },
                onNext = { state.stepScalePosition(+1, scalePositions.size) },
            )
        } else NoContextBar(state.displayMode)

        DisplayMode.Pick -> PickActionBar(state)
        DisplayMode.None -> {} // nothing
    }
}

@Composable
private fun NoContextBar(@Suppress("UNUSED_PARAMETER") dm: DisplayMode) {
    // Empty placeholder. Kept as a function in case we add per-mode info later.
}

@Composable
private fun PositionScroller(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        TextButton(onClick = onPrev) { Text("◀", style = MaterialTheme.typography.titleLarge) }
        Text(
            label,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onNext) { Text("▶", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun PickActionBar(state: AppState) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Per-string mute toggles (red ✕ at the nut), then the strum transport.
        StringMuteRow(state)
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val canStrum = state.pickedPositions.any { it.stringIndex !in state.mutedStrings }
            Text(
                "Picked: ${state.pickedPositions.size}" +
                    (if (state.mutedStrings.isNotEmpty()) "  ·  muted: ${state.mutedStrings.size}" else ""),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { state.strumPicked(false) }, enabled = canStrum) { Text("Strum") }
            OutlinedButton(onClick = { state.strumPicked(true) }, enabled = canStrum) { Text("Arp") }
            OutlinedButton(onClick = { state.clearPicked() }, enabled = state.pickedPositions.isNotEmpty() || state.mutedStrings.isNotEmpty()) { Text("Clear") }
        }
    }
}

// Persistent bottom action bar was removed (the menu now lives in the top-right
// dropdown in [StatusBar]). [ActionBarItem] is still defined in ModeBar.kt in
// case we want to expose the menu items somewhere else later.

package app.guitar.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.guitar.audio.AudioEngine
import app.guitar.audio.AudioTrackEngine
import app.guitar.audio.LegacyAudioTrackEngine
import app.guitar.audio.SwitchableAudioEngine
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
    // A/B scaffolding: run the new voice-graph engine and the legacy engine side by
    // side so the in-app toggle can compare them. Remove the legacy/switchable wrapper
    // (revert to plain `AudioTrackEngine()`) before shipping the overhaul.
    private val audioEngine: AudioEngine = SwitchableAudioEngine(
        modern = AudioTrackEngine(),
        legacy = LegacyAudioTrackEngine(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Theme flag read straight from the repository so the theme wraps the
            // whole app (AppState is created inside App()).
            val repo = androidx.compose.runtime.remember { TuningRepository(applicationContext) }
            // Theme resolution: theme_mode is the source of truth ("dark"/"light"/
            // "auto"); repo.themeMode itself falls back to the old dark-only boolean
            // pref for installs that predate this setting (see TuningRepository).
            val themeMode by repo.themeMode.collectAsState(initial = "light")
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (themeMode) {
                "light" -> false
                "auto" -> systemDark
                else -> true // "dark" (or an unrecognized value) — explicit dark
            }
            val accentName by repo.accent.collectAsState(initial = Accent.Coral.name)
            val accent = androidx.compose.runtime.remember(accentName) {
                runCatching { Accent.valueOf(accentName) }.getOrDefault(Accent.Coral)
            }
            GuitarTheme(dark = dark, accent = accent) {
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
    // Loads bundled drum one-shots from assets/drums/<instrument>_<voice>.wav,
    // decoded to mono 44.1 kHz; null → SambaLooperState falls back to the synth.
    val drumSampleLoader = remember(context) {
        loader@{ inst: app.guitar.theory.PercussionInstrument, voice: Int ->
            val name = "drums/${inst.id}_$voice.wav"
            runCatching {
                context.applicationContext.assets.open(name).use { it.readBytes() }
            }.getOrNull()?.let { app.guitar.audio.WavDecoder.decode(it) }
        }
    }
    // Loads a bundled guitar sample bank (assets/guitar/<inst>.json + wavs) for the
    // Sound picker; null → GuitarBankLoader falls back and AppState keeps the synth.
    val guitarBankLoader = remember(context) {
        loader@{ inst: String ->
            GuitarBankLoader.load(inst) { path ->
                runCatching { context.applicationContext.assets.open(path).use { it.readBytes() } }.getOrNull()
            }
        }
    }
    val state = remember { AppState(repo, scope, audio, drumSampleLoader, guitarBankLoader) }

    val customTunings by state.customTunings.collectAsState(initial = emptyMap())
    val savedSelected by state.savedSelectedName.collectAsState(initial = "Standard")
    val persistedLeftHanded by repo.leftHanded.collectAsState(initial = false)
    val persistedVoicingShell by repo.voicingShell.collectAsState(initial = false)
    val persistedLabelMode by repo.labelMode.collectAsState(initial = LabelMode.Intervals.name)
    val persistedA4 by repo.a4Hz.collectAsState(initial = 440f)
    val persistedSustain by repo.ringSustainMs.collectAsState(initial = 1500)
    val persistedStrum by repo.strumMs.collectAsState(initial = 30)
    val persistedTapOnTouchDown by repo.tapOnTouchDown.collectAsState(initial = true)
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

    // Content area is identical regardless of orientation — only the chrome
    // (bottom tab bar vs. left rail) around it differs. Captured as a
    // ColumnScope-receiver lambda so `Modifier.weight(1f)` below (on the
    // FretboardView Box) keeps resolving against whichever Column hosts it.
    val content: @Composable ColumnScope.() -> Unit = {
        if (state.currentSheet == Sheet.Loop) {
            // Loop takes over the content area — it has its own controls and Back button.
            LoopScreen(state)
        } else if (state.currentSheet == Sheet.Tuner) {
            TunerScreen(state, onBack = { state.closeSheet() })
        } else if (state.currentSheet == Sheet.EarTraining) {
            EarTrainingScreen(state, onBack = { state.closeSheet() })
        } else if (state.currentSheet == Sheet.SambaLooper) {
            SambaLooperScreen(state, onBack = { state.closeSheet() })
        } else if (state.currentSheet == Sheet.Decompose) {
            DecomposeScreen(state, onBack = { state.closeSheet() })
        } else if (state.currentSheet == Sheet.CavaqProgressions) {
            CavaqProgressionsScreen(state, onBack = { state.closeSheet() })
        } else {
            StatusBar(state)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // Fretboard fills all remaining vertical space (this screen renders as a
            // horizontal neck in both portrait and landscape).
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
                    // Play mode: sweep across the strings to strum the current grip.
                    strumMode = state.displayMode == DisplayMode.Pick,
                    onStrumPluck = { s -> state.pluckString(s) },
                )
            }
            SelectedPositionInfo(state.liveTuning, state.selectedPosition, parsedChord)
            // Tool controls live in the draggable bottom sheets (opened from the
            // tab bar/rail or More), so the neck keeps its full height here.
            ContextBar(state, chordShapes, scalePositions)
        }
    }

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    if (isPortrait) {
        // Signal bottom-tab shell (M3): content above, tab bar pinned to the
        // bottom. safeDrawingPadding on this Column already keeps the bar clear
        // of the gesture/nav-bar inset.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)
            SignalTabBar(state)
        }
    } else {
        // Landscape: same 5 items as a compact left rail (existing landscape
        // support preserved) — content area is untouched.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            SignalTabRail(state)
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), content = content)
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

    // "More" overlay (Shell.kt): lists every destination not currently tabbed,
    // plus Challenge Stats and Settings. Not a Sheet — AppState.moreOpen is a
    // transient flag toggled by the tab bar/rail's fixed 5th item.
    if (state.moreOpen) {
        val moreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { state.closeMore() },
            sheetState = moreSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            MoreScreen(state)
        }
    }
}

private fun sheetLabel(s: Sheet): String = when (s) {
    Sheet.Fretboard -> "Fretboard"
    Sheet.Loop -> "Loop"
    Sheet.Options -> "Settings"
    Sheet.CavaqProgressions -> "Progressions"
    Sheet.Tuner -> "Tuner"
    Sheet.EarTraining -> "Ear Training"
    Sheet.SambaLooper -> "Drums"
    Sheet.Decompose -> "Decompose"
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
        Spacer(Modifier.width(6.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        // Byline: the handle is tappable and opens Nadav's Instagram.
        val context = LocalContext.current
        Text(
            "made by ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            "@nadavileh",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.instagram.com/nadavileh"),
                        )
                    )
                }
            },
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
                Icon(
                    Icons.Outlined.Stop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Stop", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(2.dp))
        }
        // Quick light/dark toggle (the full Dark/Light/Auto control lives in
        // Settings -> Personalize). Shows the mode you'd switch TO.
        val goingDark = state.themeMode != ThemeMode.Dark
        IconButton(onClick = {
            state.setThemeMode(if (goingDark) ThemeMode.Dark else ThemeMode.Light)
        }) {
            Icon(
                if (goingDark) Icons.Outlined.DarkMode
                else Icons.Outlined.LightMode,
                contentDescription = if (goingDark) "Switch to dark theme" else "Switch to light theme",
            )
        }
        // Sound/EQ/reverb settings, reachable everywhere — opens the shared
        // ToneSheet (replaces the old audio-quick dropdown button).
        var toneSheetOpen by remember { mutableStateOf(false) }
        IconButton(onClick = { toneSheetOpen = true }) {
            Icon(Icons.Outlined.Tune, contentDescription = "Tone")
        }
        if (toneSheetOpen) ToneSheet(state, onDismiss = { toneSheetOpen = false })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickActionBar(state: AppState) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Quick chords: one tap lights that chord's grip on the board — then sweep
        // the neck to strum it. The ✎ chip flips to edit mode, where tapping a
        // chip reassigns its chord symbol instead of applying it.
        var editSlots by remember { mutableStateOf(false) }
        var editingSlot by remember { mutableStateOf(-1) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            state.chordSlots.forEachIndexed { i, sym ->
                FilterChip(
                    selected = state.activeChordSlot == i,
                    onClick = { if (editSlots) editingSlot = i else state.applyChordSlot(i) },
                    label = { Text(if (editSlots) "✎ $sym" else sym) },
                )
            }
            FilterChip(
                selected = editSlots,
                onClick = { editSlots = !editSlots },
                label = { Text("✎") },
            )
        }
        if (editingSlot >= 0) {
            var text by remember(editingSlot) { mutableStateOf(state.chordSlots[editingSlot]) }
            val parses = ChordLibrary.parse(text.trim()) != null
            AlertDialog(
                onDismissRequest = { editingSlot = -1 },
                title = { Text("Quick chord ${editingSlot + 1}") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Chord symbol (e.g. Am7)") },
                            singleLine = true,
                        )
                        if (!parses) {
                            Text(
                                "Not a chord symbol I can parse yet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = parses,
                        onClick = { state.setChordSlot(editingSlot, text); editingSlot = -1 },
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editingSlot = -1 }) { Text("Cancel") } },
            )
        }
        Spacer(Modifier.height(4.dp))
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

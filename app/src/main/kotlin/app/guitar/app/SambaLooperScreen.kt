package app.guitar.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.BeatFile
import app.guitar.theory.PercussionInstrument
import app.guitar.theory.PercussionMeter
import app.guitar.theory.PercussionVoices

/**
 * Drum-machine / samba-looper screen ("Rhythm" tab). Pattern-only: the 4-row ×
 * 16-cell step grid itself (2 bars of 2/4 in sixteenths by default). Tapping a
 * cell cycles its voice (silent → voice 1 → … → silent); long-press clears the
 * cell. A tinted column tracks the playhead while looping. Tapping an
 * instrument's row label opens a voice popup (overall + per-voice volume,
 * preview, and Remove); a "+ Add ▾" control in the header adds instruments from
 * the catalog. (Restores the pre-Signal tap-to-mix interaction; see the reverted
 * Signal Mixer/Kit segments this replaces.)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SambaLooperScreen(state: AppState, onBack: () -> Unit) {
    val samba = state.sambaLooper
    val blocks = state.drumBlocks
    // Guard on currentSheet so a rotation (which disposes+recreates this composable
    // when the portrait/landscape layout swaps) doesn't stop playback — only a real
    // navigation away does.
    DisposableEffect(Unit) {
        onDispose { if (state.currentSheet != Sheet.SambaLooper) { samba.stop(); blocks.stop() } }
    }

    // [Beat | Blocks]: the step-grid editor vs. the phrase sequencer.
    var blocksMode by remember { mutableStateOf(false) }
    // Eraser tool: when on, tapping a cell clears it instead of cycling its voice.
    var eraseMode by remember { mutableStateOf(false) }
    // Accent tool: when on, tapping a non-silent cell toggles its accent (louder hit).
    var accentMode by remember { mutableStateOf(false) }
    // Dyn tool: tap a hit to cycle its per-slot volume 100 → 75 → 50 → 25 %.
    var dynMode by remember { mutableStateOf(false) }
    // Free-transform state for the drum-loop grid (#6). scaleX/scaleY are INDEPENDENT so
    // you can stretch just the width (widen narrow cells) or just the height. Two-finger
    // pinch zooms + pans; single-finger drag pans once zoomed. Pure render-layer effect
    // (graphicsLayer) over a grid whose 16 cells fit the width at 1× by default.
    // NOTE: kept as MutableFloatState objects (not `by`-delegated local vars) and
    // threaded through to PatternSection by reference — the pinch-zoom gesture
    // below lives inside a `pointerInput(Unit)` block, whose coroutine is launched
    // once and never restarted; it must keep reading/writing the SAME state object
    // across recompositions (and across every event within one continuous gesture),
    // which only holds if the object reference — not a plain Float snapshot — is
    // what gets passed down.
    val scaleX = remember { mutableFloatStateOf(1f) }
    val scaleY = remember { mutableFloatStateOf(1f) }
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }

    var toneSheetOpen by remember { mutableStateOf(false) }
    // Which track's mixer popup (volumes/remove) is open — triggered from the
    // palette's Mixer chip, anchored at that track's row label.
    var mixerFor by remember { mutableStateOf<String?>(null) }
    // Track whose row is being saved as a named phrase (palette 💾 chip).
    var phraseSaveFor by remember { mutableStateOf<PercussionInstrument?>(null) }
    var phraseName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
    ) {
        // ----- Header -----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "DRUMS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { samba.stop(); blocks.stop(); onBack() }) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))

        // [Beat | Blocks] toggle.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !blocksMode,
                onClick = { blocksMode = false; blocks.stop() }, label = { Text("Beat") })
            FilterChip(selected = blocksMode,
                onClick = { blocksMode = true; samba.stop() }, label = { Text("Blocks") })
        }
        Spacer(Modifier.height(8.dp))

        // ----- Scrollable body: the pattern grid + its controls. (On the phone a
        // permanent side panel squeezes the grid, so beats load from a scrolling
        // popup — with remembered scroll position — unlike web's side panel.) -----
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (blocksMode) {
                BlocksSection(blocks)
            } else {
                PatternSection(
                    samba = samba,
                    blocks = blocks,
                    eraseMode = eraseMode,
                    onEraseMode = { eraseMode = it },
                    accentMode = accentMode,
                    onAccentMode = { accentMode = it },
                    dynMode = dynMode,
                    onDynMode = { dynMode = it },
                    scaleX = scaleX, scaleY = scaleY, offsetX = offsetX, offsetY = offsetY,
                    mixerFor = mixerFor,
                    onMixerDismiss = { mixerFor = null },
                    onBlockImported = { blocksMode = true; samba.stop() },
                )
            }
        }

        // Voice palette for the selected track — pinned above the transport dock so
        // it stays visible while the grid scrolls (mirrors chorect-web).
        val selInst = samba.editPattern.instruments.firstOrNull { it.id == samba.selectedTrackId }
        if (selInst != null) {
            Spacer(Modifier.height(6.dp))
            PaletteBar(
                samba, selInst,
                onMixer = { mixerFor = selInst.id },
                onSavePhrase = { phraseSaveFor = selInst },
            )
        }

        Spacer(Modifier.height(8.dp))
        TransportDock(
            playing = if (blocksMode) blocks.isPlaying else samba.isPlaying,
            onPlayStop = {
                if (blocksMode) blocks.toggle()
                else if (samba.isPlaying) samba.stop() else samba.start()
            },
            bpm = if (blocksMode) blocks.bpm else samba.bpm,
            // both playback loops re-read `bpm` live, so no restart is needed here.
            onBpm = { if (blocksMode) blocks.bpm = it else samba.bpm = it },
            toneLabel = state.sound.name,
            onTone = { toneSheetOpen = true },
            inlineBpm = true,
        )
        if (toneSheetOpen) ToneSheet(state, onDismiss = { toneSheetOpen = false })

        // Save-as-phrase dialog: names the selected track's row into the phrase
        // library (accents + dynamics included). Same name as a preset replaces it.
        phraseSaveFor?.let { inst ->
            AlertDialog(
                onDismissRequest = { phraseSaveFor = null },
                title = { Text("Save track as phrase") },
                text = {
                    OutlinedTextField(
                        value = phraseName,
                        onValueChange = { phraseName = it },
                        label = { Text("Phrase name (same name replaces a preset)") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = phraseName.isNotBlank(),
                        onClick = {
                            val row = samba.editPattern.grid[inst.id] ?: emptyList()
                            if (blocks.saveTrackAsPreset(inst, row, phraseName)) {
                                phraseSaveFor = null
                                phraseName = ""
                            }
                        },
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { phraseSaveFor = null }) { Text("Cancel") } },
            )
        }
    }
}

// ---------- PATTERN section ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternSection(
    samba: SambaLooperState,
    blocks: BlocksState,
    eraseMode: Boolean,
    onEraseMode: (Boolean) -> Unit,
    accentMode: Boolean,
    onAccentMode: (Boolean) -> Unit,
    dynMode: Boolean = false,
    onDynMode: (Boolean) -> Unit = {},
    scaleX: androidx.compose.runtime.MutableFloatState,
    scaleY: androidx.compose.runtime.MutableFloatState,
    offsetX: androidx.compose.runtime.MutableFloatState,
    offsetY: androidx.compose.runtime.MutableFloatState,
    mixerFor: String? = null,
    onMixerDismiss: () -> Unit = {},
    onBlockImported: () -> Unit = {},
) {
    // ----- Section header: Save / Clear / Erase / Accent / Notes -----
    var saveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var notesOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Export the current beat to a JSON file (Storage Access Framework "create
    // document"); Import picks one back and loads it. Same JSON shape as chorect-web.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(BeatFile(samba.loadedName ?: "beat", samba.bpm, samba.swing, samba.pattern, samba.opening, samba.beatNotes).encode().toByteArray())
            }
        }
    }
    var phraseToExport by remember { mutableStateOf<app.guitar.theory.PercussionBuiltins.PresetTrack?>(null) }
    val phraseExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val p = phraseToExport
        if (uri != null && p != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(app.guitar.theory.PhraseFile.encode(p).toByteArray())
            }
        }
        phraseToExport = null
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@runCatching
            val beat = BeatFile.decode(text)
            if (beat != null) {
                samba.loadPattern(beat.pattern, beat.name, beat.bpm, beat.swing, beat.opening, beat.notes)
            } else if (blocks.importBlockFile(text)) {
                // A block file loads into the Blocks view.
                onBlockImported()
            } else {
                // A phrase file joins the track-preset library instead.
                app.guitar.theory.PhraseFile.decode(text)?.let { blocks.savePhrase(it) }
            }
        }
    }

    // ----- Beat header: current beat name + tempo (set by Load / Save / Import) -----
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                samba.loadedName ?: "Untitled beat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${samba.bpm} BPM",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            // The opening (when present) renders as its own grid section above the
            // loop; "＋ Opening ▾" starts one empty or pre-filled with a preset chunk.
            if (samba.opening == null) {
                var openingMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { openingMenu = true }, contentPadding = STEP_PAD) { Text("＋ Opening ▾") }
                    DropdownMenu(expanded = openingMenu, onDismissRequest = { openingMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("(empty opening)") },
                            onClick = { openingMenu = false; samba.addOpening() },
                        )
                        DropdownMenuItem(text = { Text("From a preset track",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }, enabled = false, onClick = {})
                        for (p in blocks.allPresets()) {
                            DropdownMenuItem(
                                text = { Text("★ ${p.label}") },
                                onClick = { openingMenu = false; samba.addOpeningFromPreset(p) },
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    // ----- Notes: free text saved + exported with the beat; auto-shown when the
    // loaded beat carries notes. -----
    if (notesOpen || samba.beatNotes.isNotEmpty()) {
        OutlinedTextField(
            value = samba.beatNotes,
            onValueChange = { samba.beatNotes = it },
            label = { Text("Notes for this beat — saved and exported with it") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Spacer(Modifier.height(8.dp))
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Eraser: when on, tapping a cell clears it (no need to cycle every voice).
        if (eraseMode) {
            Button(onClick = { onEraseMode(false) }) { Text("Erase ✓") }
        } else {
            OutlinedButton(onClick = { onEraseMode(true); onAccentMode(false); onDynMode(false) }) { Text("Erase") }
        }
        // Accent: when on, tapping a hit toggles its accent (played louder).
        if (accentMode) {
            Button(onClick = { onAccentMode(false) }) { Text("Accent ✓") }
        } else {
            OutlinedButton(onClick = { onAccentMode(true); onEraseMode(false); onDynMode(false) }) { Text("Accent") }
        }
        // Dyn: tap a hit to cycle its per-slot volume 100 → 75 → 50 → 25 %.
        if (dynMode) {
            Button(onClick = { onDynMode(false) }) { Text("Dyn ✓") }
        } else {
            OutlinedButton(onClick = { onDynMode(true); onEraseMode(false); onAccentMode(false) }) { Text("Dyn") }
        }
        OutlinedButton(onClick = { saveName = samba.loadedName ?: ""; saveDialog = true }) { Text("Save…") }
        LoadBeatsControl(samba, blocks, onExportPhrase = { p ->
            phraseToExport = p
            phraseExportLauncher.launch(p.label.replace(Regex("[^\\w-]+"), "_") + ".chorect-phrase.json")
        })
        OutlinedButton(onClick = { samba.clearAll() }) { Text("Clear all") }
        // Remove ALL tracks (clean slate; Undo restores them).
        OutlinedButton(onClick = { samba.removeAllTracks() }) { Text("✕ Tracks") }
        OutlinedButton(onClick = { samba.undo() }, enabled = samba.canUndo) { Text("↶ Undo") }
        // Notes toggle (the editor shows under the beat header).
        if (samba.beatNotes.isNotEmpty() || notesOpen) {
            Button(onClick = { notesOpen = !notesOpen }) { Text("📝 Notes") }
        } else {
            OutlinedButton(onClick = { notesOpen = true }) { Text("📝 Notes") }
        }
        OutlinedButton(onClick = {
            exportLauncher.launch("${(samba.loadedName ?: "beat").replace(Regex("[^\\w-]+"), "_")}.chorect.json")
        }) { Text("Export") }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }

        // Add an instrument to the kit, sourced from the catalog.
        var addMenu by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { addMenu = true }) { Text("+ Add ▾") }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                // One-press preset tracks first (instrument + a filled row in one go).
                DropdownMenuItem(text = { Text("Track presets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }, enabled = false, onClick = {})
                for (p in blocks.allPresets()) {
                    DropdownMenuItem(
                        text = { Text("★ ${p.label}") },
                        onClick = { samba.addPresetTrack(p); addMenu = false },
                    )
                }
                HorizontalDivider()
                val toAdd = samba.instrumentsToAdd()
                if (toAdd.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("(all instruments added)") },
                        enabled = false, onClick = {},
                    )
                }
                for (inst in toAdd) {
                    DropdownMenuItem(
                        text = { Text(inst.displayName) },
                        onClick = { samba.addInstrument(inst); addMenu = false },
                    )
                }
            }
        }
    }

    if (saveDialog) {
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("Save beat") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("Beat name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = saveName.trim().isNotEmpty() && saveName.none { it in "=;|," },
                    onClick = { samba.saveCurrent(saveName.trim()); saveDialog = false },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Cancel") } },
        )
    }

    Spacer(Modifier.height(8.dp))

    // ----- Gesture legend banner (dismiss kept in-memory only, per spec) -----
    var legendDismissed by remember { mutableStateOf(false) }
    if (!legendDismissed) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    @Composable
                    fun legendLine(head: String, rest: String) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(head) }
                                append(rest)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    legendLine("Grid:  ", "tap a cell = cycle its voice · long-press = clear it")
                    legendLine("Accent tool:  ", "turn it on, then tap a hit → the hit plays louder (teal ring)")
                    legendLine("Dyn tool:  ", "turn it on, then tap a hit → its volume cycles 100 → 75 → 50 → 25 % (faded)")
                    legendLine("Erase tool:  ", "turn it on, then tap any cell → cleared")
                }
                IconButton(onClick = { legendDismissed = true }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // ----- Loop setup: bars / time signature / division + translate (#1, #2) -----
    LoopSetupControls(samba)

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    // ----- Opening section header (the opening grid renders above the loop) -----
    val op = samba.opening
    if (op != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "OPENING — plays once ▶¹",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = LocalSignal.current.feedback,
            )
            Spacer(Modifier.width(12.dp))
            Text("Bars", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { samba.editOpening(true); samba.setBars(op.meter.bars - 1) }, contentPadding = STEP_PAD) { Text("−") }
            Text("  ${op.meter.bars}  ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { samba.editOpening(true); samba.setBars(op.meter.bars + 1) }, contentPadding = STEP_PAD) { Text("+") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { samba.removeOpening() }, contentPadding = STEP_PAD) { Text("✕ Remove") }
        }
        Spacer(Modifier.height(4.dp))
    }

    // ----- Grid -----
    // All 16 (× bars) step cells fit the width at 1× (fill-width, no horizontal
    // scroll) so the whole cycle is visible at a glance in landscape. Pinch to
    // zoom in (independent X/Y) and pan for a closer look; a subtle beat-group
    // tint + wider bar gaps make the quarter-note grouping easy to read (#6/#7).
    // When an opening exists, its grid stacks ON TOP of the loop's, separated by
    // a bold rule, and both live inside the same pinch-zoom container.
    val loopKit = samba.pattern.instruments
    val opKit = op?.instruments ?: emptyList()
    fun sectionHeight(rows: Int): Int =
        COUNT_ROW_DP + rows * ROW_HEIGHT_DP + (rows - 1).coerceAtLeast(0) * 6 + CAPTION_DP
    val loopRows = loopKit.size.coerceAtLeast(1)
    val gridHeight = (
        (if (op != null) sectionHeight(opKit.size) + OPENING_DIVIDER_DP else 0) +
            sectionHeight(loopRows)
        ).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(gridHeight)
            .clipToBounds()
            // Gestures on the CONTAINER (never on a cell, so cell taps still hit):
            //  • 2 fingers → independent X/Y zoom (aspect ratio) + pan.
            //  • 1 finger, when zoomed → drag-pan the grid.
            //  • 1 finger, when NOT zoomed → not consumed, so it falls through to
            //    the page scroll and to the per-cell tap/long-press handlers.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dragging = false
                    var totalDrag = 0f
                    val slop = viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val a = pressed[0]; val b = pressed[1]
                            val curDx = kotlin.math.abs(a.position.x - b.position.x)
                            val curDy = kotlin.math.abs(a.position.y - b.position.y)
                            val preDx = kotlin.math.abs(a.previousPosition.x - b.previousPosition.x)
                            val preDy = kotlin.math.abs(a.previousPosition.y - b.previousPosition.y)
                            val zx = if (preDx > 10f) curDx / preDx else 1f
                            val zy = if (preDy > 10f) curDy / preDy else 1f
                            val cenX = (a.position.x + b.position.x) / 2f
                            val cenY = (a.position.y + b.position.y) / 2f
                            val panX = cenX - (a.previousPosition.x + b.previousPosition.x) / 2f
                            val panY = cenY - (a.previousPosition.y + b.previousPosition.y) / 2f
                            val oldSx = scaleX.floatValue; val oldSy = scaleY.floatValue
                            scaleX.floatValue = (scaleX.floatValue * zx).coerceIn(0.4f, 4f)
                            scaleY.floatValue = (scaleY.floatValue * zy).coerceIn(0.4f, 4f)
                            val mx = kotlin.math.max(0f, size.width * (scaleX.floatValue - 1f) / 2f)
                            val my = kotlin.math.max(0f, size.height * (scaleY.floatValue - 1f) / 2f)
                            offsetX.floatValue = (offsetX.floatValue + panX + (cenX - size.width / 2f) * (oldSx - scaleX.floatValue)).coerceIn(-mx, mx)
                            offsetY.floatValue = (offsetY.floatValue + panY + (cenY - size.height / 2f) * (oldSy - scaleY.floatValue)).coerceIn(-my, my)
                            event.changes.forEach { it.consume() }
                            dragging = true
                        } else if (pressed.size == 1 && (scaleX.floatValue > 1.001f || scaleY.floatValue > 1.001f)) {
                            val ch = pressed[0]
                            val dx = ch.position.x - ch.previousPosition.x
                            val dy = ch.position.y - ch.previousPosition.y
                            totalDrag += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                            if (dragging || totalDrag > slop) {
                                dragging = true
                                val mx = kotlin.math.max(0f, size.width * (scaleX.floatValue - 1f) / 2f)
                                val my = kotlin.math.max(0f, size.height * (scaleY.floatValue - 1f) / 2f)
                                offsetX.floatValue = (offsetX.floatValue + dx).coerceIn(-mx, mx)
                                offsetY.floatValue = (offsetY.floatValue + dy).coerceIn(-my, my)
                                ch.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.scaleX = scaleX.floatValue
                    this.scaleY = scaleY.floatValue
                    translationX = offsetX.floatValue
                    translationY = offsetY.floatValue
                },
        ) {
            @Composable
            fun caption(text: String) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(ROW_LABEL_DP.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            @Composable
            fun section(pat: app.guitar.theory.PercussionPattern, kit: List<PercussionInstrument>, inOpening: Boolean) {
                CountRow(pat)
                for ((i, inst) in kit.withIndex()) {
                    InstrumentRow(
                        samba = samba,
                        instrument = inst,
                        index = i,
                        kitSize = kit.size,
                        eraseMode = eraseMode,
                        accentMode = accentMode,
                        dynMode = dynMode,
                        pat = pat,
                        inOpening = inOpening,
                        mixerOpen = mixerFor == inst.id && samba.editingOpening == inOpening,
                        onMixerDismiss = onMixerDismiss,
                        modifier = Modifier.height(ROW_HEIGHT_DP.dp).fillMaxWidth(),
                    )
                    if (i != kit.lastIndex) {
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (kit.isEmpty() && !inOpening) {
                    Text(
                        "Clean slate — add a track with ＋ Add ▾, or load a groove / track preset from Load…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                caption(pat.meter.describe())
            }

            if (op != null) {
                section(op, opKit, inOpening = true)
                // Bold rule between the opening and the loop.
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)),
                )
                Spacer(Modifier.height(3.dp))
                Text("LOOP", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            section(samba.pattern, loopKit, inOpening = false)
        }
    }

    Spacer(Modifier.height(8.dp))

    // ----- Compact card: swing / tap-tempo / zoom -----
    val swingActive = samba.meter.beatUnit == 4 && samba.meter.division == 16
    val zoomed = scaleX.floatValue > 1.001f || scaleY.floatValue > 1.001f ||
        offsetX.floatValue != 0f || offsetY.floatValue != 0f
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Tap-tempo: tap along; BPM follows the average tap interval.
                OutlinedButton(onClick = { samba.tapTempo() }) { Text("Tap tempo") }
                Spacer(Modifier.width(8.dp))
                // Metronome: overlay a wood click on the loop (higher click on beat 1).
                if (samba.metronomeOn) {
                    Button(onClick = { samba.toggleMetronome() }) { Text("Metronome ✓") }
                } else {
                    OutlinedButton(onClick = { samba.toggleMetronome() }) { Text("Metronome") }
                }
                Spacer(Modifier.weight(1f))
                if (zoomed) {
                    TextButton(onClick = {
                        scaleX.floatValue = 1f; scaleY.floatValue = 1f
                        offsetX.floatValue = 0f; offsetY.floatValue = 0f
                    }) { Text("Reset zoom") }
                }
            }
            Spacer(Modifier.height(4.dp))
            // ----- Swing (Brazilian 16th-note swing; 0 = straight) -----
            // Only meaningful on a 1/16 grid (a quarter-note beat split into four 16ths):
            // it keeps the 1st & 2nd 16ths on the grid and pulls the 3rd and (more so)
            // the 4th early — the samba anticipation feel. On any other division it does
            // nothing, so the slider is disabled and the label says why.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        !swingActive -> "Swing: 1/16 grid only"
                        samba.swing == 0 -> "Swing: straight"
                        else -> "Swing: ${samba.swing}% (16ths)"
                    },
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(140.dp))
                Slider(
                    value = samba.swing.toFloat(),
                    onValueChange = { samba.swing = it.toInt() },
                    valueRange = 0f..100f,
                    enabled = swingActive,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val ROW_LABEL_DP = 128
private const val ROW_HEIGHT_DP = 70   // per-instrument row: name + ▾ + M/S all fit
private const val CAPTION_DP = 18      // bar/beat caption strip below the rows
private const val COUNT_ROW_DP = 16    // "1 e & a…" count strip above each grid
private const val OPENING_DIVIDER_DP = 30  // bold rule + LOOP label between sections
private const val LONG_PRESS_CLEAR_MS = 1500L  // hold this long on a cell to clear it

private val STEP_PAD = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)

/** Bars / time-signature / division pickers plus the loop-translate control. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoopSetupControls(samba: SambaLooperState) {
    val meter = samba.meter
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Bars stepper
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bars", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { samba.setBars(meter.bars - 1) }, enabled = meter.bars > 1,
                contentPadding = STEP_PAD) { Text("−") }
            Text(" ${meter.bars} ", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { samba.setBars(meter.bars + 1) }, enabled = meter.bars < 8,
                contentPadding = STEP_PAD) { Text("+") }
        }
        // Time signature
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Time", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            var open by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { open = true }, contentPadding = STEP_PAD) {
                    Text("${meter.beatsPerBar}/${meter.beatUnit}  ▾")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for ((b, u) in TIME_SIGNATURES) {
                        DropdownMenuItem(text = { Text("$b/$u") },
                            onClick = { samba.setTimeSignature(b, u); open = false })
                    }
                }
            }
        }
        // Division
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Note", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            var open by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { open = true }, contentPadding = STEP_PAD) {
                    Text("1/${meter.division}  ▾")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    for (d in PercussionMeter.DIVISIONS.filter { it % meter.beatUnit == 0 }) {
                        DropdownMenuItem(text = { Text("1/$d") },
                            onClick = { samba.setDivision(d); open = false })
                    }
                }
            }
        }
        // Translate (rotate) the loop by ±n slots, wrap-around.
        TranslateControl(samba)
    }
}

private val TIME_SIGNATURES = listOf(
    2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 8, 3 to 8, 12 to 8, 2 to 2,
)

/** "Shift" the whole loop left/right by ±1, or by a typed amount (wrap-around). */
@Composable
private fun TranslateControl(samba: SambaLooperState) {
    var n by remember { mutableStateOf("1") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Shift", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        OutlinedButton(onClick = { samba.translate(-1) }, contentPadding = STEP_PAD) { Text("◀") }
        Spacer(Modifier.width(2.dp))
        OutlinedButton(onClick = { samba.translate(1) }, contentPadding = STEP_PAD) { Text("▶") }
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = n,
            onValueChange = { s -> n = s.filter { it.isDigit() || it == '-' }.take(3) },
            singleLine = true,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.width(4.dp))
        OutlinedButton(
            onClick = { (n.toIntOrNull())?.let { samba.translate(it) } },
            contentPadding = STEP_PAD,
        ) { Text("Go") }
    }
}

/** Blocks: the phrase sequencer (mirrors chorect-web's blocksBody). Header
 *  (name / phrase-count / save / load / merge / clear / + track), the tracks ×
 *  phrase-columns grid (tap a cell, pick its phrase from the palette below),
 *  the playing column highlighted, and the used phrases' rule notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlocksSection(blocks: BlocksState) {
    val blk = blocks.block
    val saved = blocks.savedBlocks
    // Picked cell: (track, col); col == -1 is the track's OPENING cell.
    var pick by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val context = LocalContext.current

    // Export the block to a JSON file (embeds the custom phrases it references,
    // so it's portable); Import reads one back. Same shape as chorect-web.
    val exportBlockLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(blocks.exportBlockFile().toByteArray())
            }
        }
    }
    val importBlockLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@runCatching
            blocks.importBlockFile(text)
        }
    }

    // Header: name + phrase-count stepper.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = blk.name,
            onValueChange = { blocks.rename(it) },
            label = { Text("Block name") },
            singleLine = true,
            modifier = Modifier.width(170.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text("Phrases", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        OutlinedButton(onClick = { blocks.setPhraseCount(blk.phraseCount - 1) },
            enabled = blk.phraseCount > 1, contentPadding = STEP_PAD) { Text("−") }
        Text("  ${blk.phraseCount}  ", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { blocks.setPhraseCount(blk.phraseCount + 1) },
            enabled = blk.phraseCount < 8, contentPadding = STEP_PAD) { Text("+") }
    }
    Spacer(Modifier.height(6.dp))

    // Actions.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { blocks.saveCurrent() }) { Text("Save block") }
        var loadOpen by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { loadOpen = true }) { Text("Load…") }
            DropdownMenu(expanded = loadOpen, onDismissRequest = { loadOpen = false }) {
                for ((name, b) in saved) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, modifier = Modifier.weight(1f))
                                TextButton(onClick = { blocks.deleteSaved(name) }) { Text("✕") }
                            }
                        },
                        onClick = { blocks.loadBlock(b); loadOpen = false },
                    )
                }
                if (saved.isEmpty()) {
                    DropdownMenuItem(text = { Text("(no saved blocks yet)") }, enabled = false, onClick = {})
                }
            }
        }
        var mergeOpen by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { mergeOpen = true }) { Text("Merge with…") }
            DropdownMenu(expanded = mergeOpen, onDismissRequest = { mergeOpen = false }) {
                val candidates = saved.filter { it.value.phraseCount == blk.phraseCount && it.key != blk.name }
                for ((name, b) in candidates) {
                    DropdownMenuItem(text = { Text(name) }, onClick = { blocks.mergeWith(b); mergeOpen = false })
                }
                if (candidates.isEmpty()) {
                    DropdownMenuItem(text = { Text("(no saved blocks with ${blk.phraseCount} phrases)") }, enabled = false, onClick = {})
                }
            }
        }
        OutlinedButton(onClick = {
            exportBlockLauncher.launch(blk.name.replace(Regex("[^\\w-]+"), "_") + ".chorect-block.json")
        }) { Text("Export") }
        OutlinedButton(onClick = { importBlockLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }
        OutlinedButton(onClick = { pick = null; blocks.clear() }) { Text("Clear") }
        // Metronome: overlay a wood click on the block (higher on each bar's "1").
        if (blocks.metronomeOn) {
            Button(onClick = { blocks.toggleMetronome() }) { Text("Metronome ✓") }
        } else {
            OutlinedButton(onClick = { blocks.toggleMetronome() }) { Text("Metronome") }
        }
        var addOpen by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { addOpen = true }) { Text("+ Track ▾") }
            DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                for (inst in blocks.instrumentsToAdd()) {
                    DropdownMenuItem(text = { Text(inst.displayName) },
                        onClick = { blocks.addTrack(inst); addOpen = false })
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    if (blk.tracks.isEmpty()) {
        Text(
            "A block sequences phrases: add a track (instrument), then tap its cells to " +
                "place phrases — e.g. entrada → variation → teleco-teco → variation. " +
                "Each phrase plays with its own swing; the block loops.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // Grid: one COLUMN per track (instrument), phrases stacked VERTICALLY —
    // time flows downward. Row ▶¹ = the opening (plays instead of phrase 1 on
    // the block's first pass only); rows 1..N = the looped phrases.
    val teal = LocalSignal.current.feedback
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(26.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            blk.tracks.forEachIndexed { ti, t ->
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t.instrument.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text("✕", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { pick = null; blocks.removeTrack(ti) }
                            .padding(4.dp))
                }
            }
        }
    }
    for (c in -1 until blk.phraseCount) {
        val isOpening = c == -1
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (isOpening) "▶¹" else "${c + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                blk.tracks.forEachIndexed { ti, t ->
                    val phrase = if (isOpening) t.opening else t.cells[c]
                    val active = blocks.isPlaying && if (isOpening) {
                        blocks.openingPass && blocks.currentCol == 0 && t.opening != null
                    } else {
                        blocks.currentCol == c && !(c == 0 && blocks.openingPass && t.opening != null)
                    }
                    val picking = pick == (ti to c)
                    val border = when {
                        active -> teal
                        picking -> MaterialTheme.colorScheme.primary
                        isOpening -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val label = phrase?.let {
                        val i = it.label.indexOf("— ")
                        (if (i < 0) it.label else it.label.substring(i + 2)) +
                            (if (it.swing > 0) " ~${it.swing}%" else "") +
                            (if (it.note.isNotEmpty()) " ※" else "")
                    } ?: (if (isOpening) "▶¹" else "＋")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (active) teal.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            )
                            .border(if (active || picking) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
                            .clickable { pick = if (picking) null else (ti to c) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (phrase != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Visual gap between the one-shot opening row and the looped rows.
        if (isOpening) Spacer(Modifier.height(8.dp))
    }

    // Phrase palette for the picked cell (stays open so the swing can be tuned).
    pick?.let { (ti, c) ->
        if (ti < blk.tracks.size) {
            val track = blk.tracks[ti]
            val current = if (c == -1) track.opening else track.cells[c]
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${track.instrument.displayName} · ${if (c == -1) "opening ▶¹" else "phrase ${c + 1}"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { pick = null }, contentPadding = STEP_PAD) { Text("✕") }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PalChip("(empty)", selected = current == null) { blocks.setCell(ti, c, null) }
                for (p in blocks.phrasesFor(track.instrument)) {
                    val i = p.label.indexOf("— ")
                    val short = (if (i < 0) p.label else p.label.substring(i + 2)) +
                        (if (p.swing > 0) " ~${p.swing}%" else "")
                    PalChip(short, selected = current?.label == p.label) { blocks.setCell(ti, c, p) }
                }
            }
            // Per-cell swing override: THIS phrase's own clock (0 = straight).
            if (current != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Swing of this phrase: ${current.swing}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = current.swing.toFloat(),
                    onValueChange = { blocks.setCellSwing(ti, c, it.toInt()) },
                    valueRange = 0f..100f,
                )
            }
        }
    }

    // Rule/notes of the phrases in use, shown under the grid (selectable → copyable).
    androidx.compose.foundation.text.selection.SelectionContainer {
        Column {
            val noted = LinkedHashSet<String>()
            for (t in blk.tracks) for (p in t.cells + t.opening) {
                if (p != null && p.note.isNotEmpty() && noted.add(p.label)) {
                    Spacer(Modifier.height(4.dp))
                    Text("※ ${p.label}: ${p.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** Load… control: a scrolling beats popup (Grooves + Saved) whose scroll
 *  position is REMEMBERED across openings — the phone keeps the grid full-width
 *  (web uses a constantly-open side panel with the same content). */
@Composable
private fun LoadBeatsControl(
    samba: SambaLooperState,
    blocks: BlocksState,
    onExportPhrase: (app.guitar.theory.PercussionBuiltins.PresetTrack) -> Unit = {},
) {
    val saved by samba.savedPatterns.collectAsState(initial = emptyMap())
    var open by remember { mutableStateOf(false) }
    // Hoisted above the menu so closing/reopening keeps the scroll position.
    val listScroll = rememberScrollState()
    Box {
        OutlinedButton(onClick = { open = true }) { Text("Load…") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BeatList(samba, blocks, saved, listScroll, onLoaded = { open = false }, onExportPhrase = onExportPhrase)
        }
    }
}

@Composable
private fun BeatList(
    samba: SambaLooperState,
    blocks: BlocksState,
    saved: Map<String, app.guitar.theory.SavedBeat>,
    listScroll: androidx.compose.foundation.ScrollState,
    onLoaded: () -> Unit,
    onExportPhrase: (app.guitar.theory.PercussionBuiltins.PresetTrack) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .heightIn(max = 420.dp)
            .padding(6.dp)
            .verticalScroll(listScroll),
    ) {
        @Composable
        fun header(t: String) {
            Text(
                t.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
            )
            HorizontalDivider()
        }

        @Composable
        fun beatRow(label: String, selected: Boolean, trailing: (@Composable () -> Unit)? = null, onTap: () -> Unit) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(onClick = onTap)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
        }

        header("Grooves")
        for (b in app.guitar.theory.PercussionBuiltins.ALL + app.guitar.theory.PercussionBuiltins.STUDY) {
            beatRow(b.name + if (b.opening != null) " ▶¹" else "", samba.loadedName == b.name) {
                samba.loadPattern(b.pattern, b.name, b.bpm, opening = b.opening)
                onLoaded()
            }
        }
        // Track presets: tap to ADD the chunk as a track to the current beat.
        // User-defined phrases (👤) can be deleted; save one via the track palette's 💾.
        header("Track presets")
        val customs = blocks.customPresets.keys
        for (p in blocks.allPresets()) {
            val isCustom = p.label in customs
            beatRow(
                "★ ${p.label}" + (if (p.swing > 0) " ~${p.swing}%" else "") + (if (isCustom) " 👤" else ""),
                selected = false,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // ◎ loops the phrase ALONE: replaces the whole beat with just
                        // this track (Undo brings the previous beat back).
                        Text(
                            "◎",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { samba.loadPresetAsBeat(p) }
                                .padding(4.dp),
                        )
                        if (isCustom) {
                            // ⤓ exports the phrase as a .chorect-phrase.json (Import reads it back).
                            Text(
                                "⤓",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onExportPhrase(p) }
                                    .padding(4.dp),
                            )
                            Text(
                                "✕",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { blocks.deleteTrackPreset(p.label) }
                                    .padding(4.dp),
                            )
                        }
                    }
                },
            ) {
                samba.addPresetTrack(p)
            }
        }
        header("Saved")
        for ((name, beat) in saved) {
            beatRow(
                name + (if (beat.opening != null) " ▶¹" else "") + (if (beat.notes.isNotEmpty()) " 📝" else ""),
                samba.loadedName == name,
                trailing = {
                    Text(
                        "✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { samba.deleteSaved(name) }
                            .padding(4.dp),
                    )
                },
            ) {
                samba.loadPattern(beat.main, name, opening = beat.opening, notes = beat.notes)
                onLoaded()
            }
        }
        if (saved.isEmpty()) {
            Text(
                "(no saved beats yet)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Count strip above a grid: "1 e & a  2 e & a …" aligned with the step cells. */
@Composable
private fun CountRow(pat: app.guitar.theory.PercussionPattern, modifier: Modifier = Modifier) {
    val perBeat = pat.meter.slotsPerBeat.coerceAtLeast(1)
    val slotsPerBar = pat.meter.slotsPerBar.coerceAtLeast(1)
    val sub16 = listOf("", "e", "&", "a")
    Row(modifier = modifier.fillMaxWidth().height(COUNT_ROW_DP.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(ROW_LABEL_DP.dp + 6.dp))
        Row(Modifier.weight(1f)) {
            for (slot in 0 until pat.slots) {
                val pos = slot % perBeat
                val label = when {
                    pos == 0 -> "${(slot / perBeat) % pat.meter.beatsPerBar + 1}"
                    perBeat == 4 -> sub16[pos]
                    perBeat == 2 -> "&"
                    else -> "·"
                }
                Box(Modifier.weight(1f).padding(horizontal = 1.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = if (pos == 0) 11.sp else 9.sp,
                        fontWeight = if (pos == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (pos == 0) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if ((slot + 1) % perBeat == 0 && slot != pat.slots - 1) {
                    val isBar = (slot + 1) % slotsPerBar == 0
                    Spacer(Modifier.width(if (isBar) 8.dp else 5.dp))
                }
            }
        }
    }
}

/** Pattern-tab row of one section (loop or opening): instrument name label
 *  (tap → select track / voice palette) + Mute/Solo toggles + its step cells.
 *  Any interaction first makes its section the edit target. */
@Composable
private fun InstrumentRow(
    samba: SambaLooperState,
    instrument: PercussionInstrument,
    index: Int,
    kitSize: Int,
    eraseMode: Boolean,
    accentMode: Boolean,
    pat: app.guitar.theory.PercussionPattern,
    inOpening: Boolean,
    dynMode: Boolean = false,
    mixerOpen: Boolean = false,
    onMixerDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val voices = PercussionVoices.voicesFor(instrument)
    val audible = samba.isAudible(instrument)
    val selected = samba.selectedTrackId == instrument.id && samba.editingOpening == inOpening
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // ---- Row label: instrument name (tap → select track / voice palette) + Mute / Solo ----
        Column(
            modifier = Modifier.width(ROW_LABEL_DP.dp).fillMaxHeight().padding(end = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box {
                // Tap the name to select the track — opens the voice palette at the
                // bottom (the mixer popup now opens from the palette's Mixer chip).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .pointerInput(instrument) { detectTapGestures(onTap = { samba.editOpening(inOpening); samba.selectTrack(instrument.id) }) }
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        instrument.displayName + (if (selected) " ✓" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            audible -> MaterialTheme.colorScheme.onBackground
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    // A track with its own swing shows a ~N% badge (dim while the
                    // global swing overrides it).
                    val tSwing = pat.trackSwing[instrument.id] ?: 0
                    if (tSwing > 0) {
                        Text(
                            " ~$tSwing%",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (samba.swing > 0) 0.35f else 0.85f,
                            ),
                        )
                    }
                }
                // Mixer menu: overall + per-voice volume, plus Remove. Opened from the
                // palette's Mixer chip; tap outside to dismiss; stays open across
                // slider drags so levels can be compared.
                DropdownMenu(expanded = mixerOpen, onDismissRequest = onMixerDismiss) {
                    // Overall instrument volume. Lives in the voice popup so the dense
                    // step-grid stays uncluttered.
                    Column(modifier = Modifier.width(260.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        val vol = samba.volumeOf(instrument)
                        Text(
                            "Overall volume: ${(vol * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Slider(
                            value = vol,
                            onValueChange = { samba.setVolume(instrument, it) },
                            valueRange = 0f..1f,
                        )
                        // Per-TRACK swing: this track's own clock. Only heard while
                        // the beat's global swing is 0 (a nonzero global overrides).
                        val tSwing = samba.editPattern.trackSwing[instrument.id] ?: 0
                        Text(
                            "Track swing: $tSwing%" + (if (samba.swing > 0) " (overridden by global swing)" else ""),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Slider(
                            value = tSwing.toFloat(),
                            onValueChange = { samba.setTrackSwing(instrument, it.toInt()) },
                            valueRange = 0f..100f,
                        )
                    }
                    HorizontalDivider()
                    Text(
                        "  Per-voice volume (tap name to audition)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    // Each voice: its own level slider; the label auditions the voice
                    // at its current effective gain so tuning is immediate.
                    voices.forEachIndexed { idx, v ->
                        val vvol = samba.voiceVolumeOf(instrument, idx)
                        Column(modifier = Modifier.width(260.dp).padding(horizontal = 12.dp, vertical = 2.dp)) {
                            Text(
                                "${v.glyph}   ${v.displayName}   ·   ${(vvol * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.pointerInput(instrument, idx) {
                                    detectTapGestures(onTap = { samba.preview(instrument, idx) })
                                },
                            )
                            Slider(
                                value = vvol,
                                onValueChange = { samba.setVoiceVolume(instrument, idx, it) },
                                valueRange = 0f..1f,
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("⧉ Duplicate ${instrument.displayName}") },
                        onClick = { onMixerDismiss(); samba.editOpening(inOpening); samba.duplicateTrack(instrument) },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove ${instrument.displayName}") },
                        onClick = { onMixerDismiss(); samba.removeInstrument(instrument) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ToggleTag("M", on = instrument in samba.muted,
                    onColor = MaterialTheme.colorScheme.error) { samba.toggleMute(instrument) }
                ToggleTag("S", on = instrument in samba.soloed,
                    onColor = MaterialTheme.colorScheme.primary) { samba.toggleSolo(instrument) }
                // Reorder this track up / down (raise / lower to sit two tracks together).
                Text("▲", style = MaterialTheme.typography.bodySmall,
                    color = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = index > 0) { samba.editOpening(inOpening); samba.reorderInstrument(index, index - 1) }
                        .padding(horizontal = 2.dp))
                Text("▼", style = MaterialTheme.typography.bodySmall,
                    color = if (index < kitSize - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = index < kitSize - 1) { samba.editOpening(inOpening); samba.reorderInstrument(index, index + 1) }
                        .padding(horizontal = 2.dp))
            }
        }
        // ---- step cells (dimmed when the track isn't audible) ----
        // Fill-width (weight per cell) so the whole cycle fits at 1×; pinch-zoom the
        // container to enlarge. Beat-group index drives an alternating tint so the
        // quarter-note groups (4 sixteenths each) are easy to count.
        val slots = pat.slots
        val slotsPerBeat = pat.meter.slotsPerBeat.coerceAtLeast(1)
        val slotsPerBar = pat.meter.slotsPerBar.coerceAtLeast(1)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .alpha(if (audible) 1f else 0.4f),
        ) {
            for (slot in 0 until slots) {
                Cell(
                    samba = samba,
                    instrument = instrument,
                    slot = slot,
                    beatIndex = slot / slotsPerBeat,
                    isBeatStart = slot % slotsPerBeat == 0,
                    eraseMode = eraseMode,
                    accentMode = accentMode,
                    dynMode = dynMode,
                    pat = pat,
                    inOpening = inOpening,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp),
                )
                // Beat separators: a visible vertical rule after each beat (each group
                // of four 16ths), heavier at bar lines, so the quarter-note divisions
                // read clearly — not just a gap.
                if ((slot + 1) % slotsPerBeat == 0 && slot != slots - 1) {
                    val isBar = (slot + 1) % slotsPerBar == 0
                    Box(
                        modifier = Modifier.width(if (isBar) 8.dp else 5.dp).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(if (isBar) 3.5.dp else 2.dp)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.onBackground.copy(
                                        alpha = if (isBar) 0.9f else 0.6f
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

/** Bottom voice palette for the selected track (mirrors chorect-web): pick the
 *  "brush" a cell tap paints — Cycle (default, classic behavior), one chip per
 *  voice (tapping also previews the sound), or Erase. Mixer opens the volume
 *  popup at the track's row; ✕ deselects. */
@Composable
private fun PaletteBar(
    samba: SambaLooperState,
    inst: PercussionInstrument,
    onMixer: () -> Unit,
    onSavePhrase: () -> Unit = {},
) {
    val brush = samba.brush
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            inst.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PalChip("↻ Cycle", brush is SambaLooperState.Brush.Cycle) { samba.changeBrush(SambaLooperState.Brush.Cycle) }
            PercussionVoices.voicesFor(inst).forEachIndexed { idx, v ->
                PalChip("${v.glyph} ${v.displayName}", brush == SambaLooperState.Brush.Voice(idx)) {
                    samba.changeBrush(SambaLooperState.Brush.Voice(idx))
                    samba.preview(inst, idx)   // hear what you're about to paint
                }
            }
            PalChip("⌫ Erase", brush is SambaLooperState.Brush.Erase) { samba.changeBrush(SambaLooperState.Brush.Erase) }
        }
        Spacer(Modifier.width(8.dp))
        PalChip("Mixer", selected = false, tool = true, onTap = onMixer)
        Spacer(Modifier.width(6.dp))
        PalChip("⧉ Dup", selected = false, tool = true) { samba.duplicateTrack(inst) }
        Spacer(Modifier.width(6.dp))
        // Save this track's row as a named PHRASE (custom preset): joins the
        // library everywhere; a built-in's name REPLACES it (edit-and-resave).
        PalChip("💾", selected = false, tool = true, onTap = onSavePhrase)
        Spacer(Modifier.width(6.dp))
        PalChip("✕", selected = false, tool = true) { samba.selectTrack(inst.id) }
    }
}

/** One palette chip: pill outline; filled with the act color when selected;
 *  teal (feedback) outline for the tool chips (Mixer / ✕). */
@Composable
private fun PalChip(label: String, selected: Boolean, tool: Boolean = false, onTap: () -> Unit) {
    val palette = LocalSignal.current
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        tool -> palette.feedback
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimary
                tool -> palette.feedback
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** Small square toggle used for Mute (M) and Solo (S). Outlined when off (so the
 *  letter stays legible), filled with [onColor] when on. */
@Composable
private fun ToggleTag(label: String, on: Boolean, onColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) onColor else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (on) onColor else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp),
            )
            .pointerInput(on) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (on) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Cell(
    samba: SambaLooperState,
    instrument: PercussionInstrument,
    slot: Int,
    beatIndex: Int,
    isBeatStart: Boolean,
    eraseMode: Boolean,
    accentMode: Boolean,
    pat: app.guitar.theory.PercussionPattern,
    inOpening: Boolean,
    dynMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSignal.current
    val voice = pat.voiceAt(instrument, slot)
    val accented = pat.isAccented(instrument, slot)
    // Per-slot dynamics: quieter hits render faded (Dyn tool cycles the level).
    val dynLevel = if (voice == null) 0 else pat.dynLevelAt(instrument, slot)
    // Playhead lights the section that's actually sounding: the opening rows
    // during the opening pass, the loop rows afterwards. Tracks with their own
    // swing carry their OWN playhead (they anticipate the master clock).
    val isPlayhead = samba.isPlaying && samba.playheadSlotFor(instrument) == slot && samba.playingOpening == inOpening
    val base = MaterialTheme.colorScheme.surfaceVariant
    // Empty cells are brightened (were near-invisible on the black background) and
    // tinted per beat-group so the quarter-note grouping reads at a glance: the first
    // 16th of each beat is brightest, and alternating beats step between two shades.
    val emptyFill = when {
        isBeatStart -> base.copy(alpha = 0.95f)
        beatIndex % 2 == 0 -> base.copy(alpha = 0.75f)
        else -> base.copy(alpha = 0.6f)
    }
    // Hit cells = act (per the Signal palette: hits are the primary/act color,
    // regardless of which voice — the printed glyph already distinguishes voices).
    // Multicolor voices restored (user: "I miss the multicolors of the instruments
    // voices") — voice 1 = act, voice 2 = blue, voice 3+ = teal, as pre-Signal.
    val fillBase = when (voice) {
        null -> emptyFill
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val fill = if (dynLevel > 0) fillBase.copy(alpha = fillBase.alpha * (1f - 0.22f * dynLevel)) else fillBase
    // Border precedence: playhead > accent ring (feedback teal) > none.
    val borderWidth = if (isPlayhead) 2.dp else if (accented) 2.dp else 0.dp
    val borderColor = when {
        isPlayhead -> MaterialTheme.colorScheme.onBackground
        accented -> palette.feedback
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .pointerInput(instrument, slot, eraseMode, accentMode, dynMode) {
                // Tap = cycle/erase/accent. A DELIBERATE long press (≥1.5 s) clears the
                // cell — long enough that it won't fire by accident while tapping.
                awaitEachGesture {
                    awaitFirstDown()
                    var heldLongEnough = false
                    val up = try {
                        withTimeout(LONG_PRESS_CLEAR_MS) { waitForUpOrCancellation() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        heldLongEnough = true
                        null
                    }
                    if (heldLongEnough) {
                        samba.editOpening(inOpening)
                        samba.clearCell(instrument, slot)
                        waitForUpOrCancellation()   // swallow the eventual release
                    } else if (up != null) {
                        samba.editOpening(inOpening)   // edits target this cell's section
                        when {
                            eraseMode -> samba.clearCell(instrument, slot)
                            accentMode -> samba.toggleAccent(instrument, slot)
                            dynMode -> samba.dynCycle(instrument, slot)
                            // Selected track follows the palette brush; others keep cycling.
                            samba.selectedTrackId == instrument.id -> samba.applyBrush(instrument, slot)
                            else -> samba.toggleSlot(instrument, slot)
                        }
                    }
                    // up == null && !heldLongEnough → gesture cancelled; do nothing.
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (voice != null) {
            Text(
                PercussionVoices.voice(instrument, voice).glyph,
                fontSize = if (accented) 18.sp else 16.sp,
                fontWeight = if (accented) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

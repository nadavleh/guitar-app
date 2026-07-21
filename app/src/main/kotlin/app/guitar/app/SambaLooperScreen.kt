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
import androidx.compose.ui.text.font.FontWeight
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
    // Guard on currentSheet so a rotation (which disposes+recreates this composable
    // when the portrait/landscape layout swaps) doesn't stop playback — only a real
    // navigation away does.
    DisposableEffect(Unit) { onDispose { if (state.currentSheet != Sheet.SambaLooper) samba.stop() } }

    // Eraser tool: when on, tapping a cell clears it instead of cycling its voice.
    var eraseMode by remember { mutableStateOf(false) }
    // Accent tool: when on, tapping a non-silent cell toggles its accent (louder hit).
    var accentMode by remember { mutableStateOf(false) }
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
            OutlinedButton(onClick = { samba.stop(); onBack() }) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))

        // ----- Scrollable body: the pattern grid + its controls -----
        // Wrapped so the TransportDock below stays pinned and visible instead of
        // scrolling away with the (potentially long) instrument grid + footer.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            PatternSection(
                samba = samba,
                eraseMode = eraseMode,
                onEraseMode = { eraseMode = it },
                accentMode = accentMode,
                onAccentMode = { accentMode = it },
                scaleX = scaleX, scaleY = scaleY, offsetX = offsetX, offsetY = offsetY,
                mixerFor = mixerFor,
                onMixerDismiss = { mixerFor = null },
            )
        }

        // Voice palette for the selected track — pinned above the transport dock so
        // it stays visible while the grid scrolls (mirrors chorect-web).
        val selInst = samba.editPattern.instruments.firstOrNull { it.id == samba.selectedTrackId }
        if (selInst != null) {
            Spacer(Modifier.height(6.dp))
            PaletteBar(samba, selInst, onMixer = { mixerFor = selInst.id })
        }

        Spacer(Modifier.height(8.dp))
        TransportDock(
            playing = samba.isPlaying,
            onPlayStop = { if (samba.isPlaying) samba.stop() else samba.start() },
            bpm = samba.bpm,
            // samba's playback loop re-reads `bpm` live every slot (see
            // SambaLooperState.start()), so no restart is needed here.
            onBpm = { samba.bpm = it },
            toneLabel = state.sound.name,
            onTone = { toneSheetOpen = true },
            inlineBpm = true,
        )
        if (toneSheetOpen) ToneSheet(state, onDismiss = { toneSheetOpen = false })
    }
}

// ---------- PATTERN section ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternSection(
    samba: SambaLooperState,
    eraseMode: Boolean,
    onEraseMode: (Boolean) -> Unit,
    accentMode: Boolean,
    onAccentMode: (Boolean) -> Unit,
    scaleX: androidx.compose.runtime.MutableFloatState,
    scaleY: androidx.compose.runtime.MutableFloatState,
    offsetX: androidx.compose.runtime.MutableFloatState,
    offsetY: androidx.compose.runtime.MutableFloatState,
    mixerFor: String? = null,
    onMixerDismiss: () -> Unit = {},
) {
    // ----- Section header: Save / Load / Clear / Erase / Accent -----
    val saved by samba.savedPatterns.collectAsState(initial = emptyMap())
    var saveDialog by remember { mutableStateOf(false) }
    var loadMenu by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Export the current beat to a JSON file (Storage Access Framework "create
    // document"); Import picks one back and loads it. Same JSON shape as chorect-web.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(BeatFile(samba.loadedName ?: "beat", samba.bpm, samba.swing, samba.pattern, samba.opening).encode().toByteArray())
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@runCatching
            BeatFile.decode(text)?.let { samba.loadPattern(it.pattern, it.name, it.bpm, it.swing, it.opening) }
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
                (samba.loadedName ?: "Untitled beat") + if (samba.editingOpening) " — opening" else "",
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
            // Loop/Opening edit toggle (the opening plays once before the loop).
            if (samba.opening != null) {
                if (samba.editingOpening) {
                    OutlinedButton(onClick = { samba.editOpening(false) }, contentPadding = STEP_PAD) { Text("Loop") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { samba.editOpening(true) }, contentPadding = STEP_PAD) { Text("Opening ▶¹") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { samba.removeOpening() }, contentPadding = STEP_PAD) { Text("✕") }
                } else {
                    Button(onClick = { samba.editOpening(false) }, contentPadding = STEP_PAD) { Text("Loop") }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = { samba.editOpening(true) }, contentPadding = STEP_PAD) { Text("Opening ▶¹") }
                }
            } else {
                OutlinedButton(onClick = { samba.addOpening() }, contentPadding = STEP_PAD) { Text("＋ Opening") }
            }
        }
    }
    Spacer(Modifier.height(8.dp))

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Eraser: when on, tapping a cell clears it (no need to cycle every voice).
        if (eraseMode) {
            Button(onClick = { onEraseMode(false) }) { Text("Erase ✓") }
        } else {
            OutlinedButton(onClick = { onEraseMode(true); onAccentMode(false) }) { Text("Erase") }
        }
        // Accent: when on, tapping a hit toggles its accent (played louder).
        if (accentMode) {
            Button(onClick = { onAccentMode(false) }) { Text("Accent ✓") }
        } else {
            OutlinedButton(onClick = { onAccentMode(true); onEraseMode(false) }) { Text("Accent") }
        }
        OutlinedButton(onClick = { saveName = ""; saveDialog = true }) { Text("Save…") }
        Box {
            OutlinedButton(onClick = { loadMenu = true }) { Text("Load…") }
            DropdownMenu(expanded = loadMenu, onDismissRequest = { loadMenu = false }) {
                // Built-in grooves first (same set as the web), then saved beats.
                for (b in app.guitar.theory.PercussionBuiltins.ALL) {
                    DropdownMenuItem(
                        text = { Text(b.name) },
                        onClick = { samba.loadPattern(b.pattern, b.name, b.bpm, opening = b.opening); loadMenu = false },
                    )
                }
                if (saved.isNotEmpty()) HorizontalDivider()
                for ((name, beat) in saved) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name + if (beat.opening != null) " ▶¹" else "", modifier = Modifier.weight(1f))
                                TextButton(onClick = { samba.deleteSaved(name) }) { Text("✕") }
                            }
                        },
                        onClick = { samba.loadPattern(beat.main, name, opening = beat.opening); loadMenu = false },
                    )
                }
                if (saved.isEmpty()) {
                    DropdownMenuItem(text = { Text("(no saved beats yet)") }, enabled = false, onClick = {})
                }
            }
        }
        OutlinedButton(onClick = { samba.clearAll() }) { Text("Clear all") }
        OutlinedButton(onClick = { samba.undo() }, enabled = samba.canUndo) { Text("↶ Undo") }
        OutlinedButton(onClick = {
            exportLauncher.launch("${(samba.loadedName ?: "beat").replace(Regex("[^\\w-]+"), "_")}.chorect.json")
        }) { Text("Export") }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Import") }

        // Add an instrument to the kit, sourced from the catalog.
        var addMenu by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { addMenu = true }) { Text("+ Add ▾") }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
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
                Text(
                    "Tap = toggle · hold = accent · long-press = erase",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
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

    // ----- Grid -----
    // All 16 (× bars) step cells fit the width at 1× (fill-width, no horizontal
    // scroll) so the whole cycle is visible at a glance in landscape. Pinch to
    // zoom in (independent X/Y) and pan for a closer look; a subtle beat-group
    // tint + wider bar gaps make the quarter-note grouping easy to read (#6/#7).
    val kit = samba.editPattern.instruments
    val rowCount = kit.size.coerceAtLeast(1)
    val gridHeight = (rowCount * ROW_HEIGHT_DP + (rowCount - 1) * 6 + CAPTION_DP).dp
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
            for ((i, inst) in kit.withIndex()) {
                InstrumentRow(
                    samba = samba,
                    instrument = inst,
                    index = i,
                    kitSize = kit.size,
                    eraseMode = eraseMode,
                    accentMode = accentMode,
                    mixerOpen = mixerFor == inst.id,
                    onMixerDismiss = onMixerDismiss,
                    modifier = Modifier.height(ROW_HEIGHT_DP.dp).fillMaxWidth(),
                )
                if (i != kit.lastIndex) {
                    Spacer(Modifier.height(6.dp))
                }
            }
            // Beat / bar caption
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(ROW_LABEL_DP.dp))
                Text(
                    samba.meter.describe(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
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

/** Pattern-tab row: instrument name label (tap → voice popup with overall + per-voice
 *  volume and Remove) + Mute/Solo toggles + its step cells. */
@Composable
private fun InstrumentRow(
    samba: SambaLooperState,
    instrument: PercussionInstrument,
    index: Int,
    kitSize: Int,
    eraseMode: Boolean,
    accentMode: Boolean,
    mixerOpen: Boolean = false,
    onMixerDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val voices = PercussionVoices.voicesFor(instrument)
    val audible = samba.isAudible(instrument)
    val selected = samba.selectedTrackId == instrument.id
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
                        .pointerInput(instrument) { detectTapGestures(onTap = { samba.selectTrack(instrument.id) }) }
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        instrument.displayName + if (selected) " ✓" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        color = when {
                            selected -> MaterialTheme.colorScheme.primary
                            audible -> MaterialTheme.colorScheme.onBackground
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
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
                        .clickable(enabled = index > 0) { samba.reorderInstrument(index, index - 1) }
                        .padding(horizontal = 2.dp))
                Text("▼", style = MaterialTheme.typography.bodySmall,
                    color = if (index < kitSize - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = index < kitSize - 1) { samba.reorderInstrument(index, index + 1) }
                        .padding(horizontal = 2.dp))
            }
        }
        // ---- step cells (dimmed when the track isn't audible) ----
        // Fill-width (weight per cell) so the whole cycle fits at 1×; pinch-zoom the
        // container to enlarge. Beat-group index drives an alternating tint so the
        // quarter-note groups (4 sixteenths each) are easy to count.
        val slots = samba.editPattern.slots
        val slotsPerBeat = samba.meter.slotsPerBeat.coerceAtLeast(1)
        val slotsPerBar = samba.meter.slotsPerBar.coerceAtLeast(1)
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
                                .width(if (isBar) 2.5.dp else 1.5.dp)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (isBar) 0.8f else 0.5f
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
private fun PaletteBar(samba: SambaLooperState, inst: PercussionInstrument, onMixer: () -> Unit) {
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
    modifier: Modifier = Modifier,
) {
    val palette = LocalSignal.current
    val voice = samba.editPattern.voiceAt(instrument, slot)
    val accented = samba.editPattern.isAccented(instrument, slot)
    // Playhead shows only when the displayed grid is what's sounding (the loop
    // grid during the loop, the opening grid during the opening pass).
    val isPlayhead = samba.currentSlot == slot && samba.playingOpening == samba.editingOpening
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
    val fill = when (voice) {
        null -> emptyFill
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
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
            .pointerInput(instrument, slot, eraseMode, accentMode) {
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
                        samba.clearCell(instrument, slot)
                        waitForUpOrCancellation()   // swallow the eventual release
                    } else if (up != null) {
                        when {
                            eraseMode -> samba.clearCell(instrument, slot)
                            accentMode -> samba.toggleAccent(instrument, slot)
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

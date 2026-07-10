package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.PercussionInstrument
import app.guitar.theory.PercussionMeter
import app.guitar.theory.PercussionVoices

/**
 * Drum-machine / samba-looper screen. A 4-row × 16-cell step grid (2 bars of
 * 2/4 in sixteenths). Tapping a cell cycles its voice
 * (silent → voice 1 → … → silent); long-press clears the cell. A tinted column
 * tracks the playhead while looping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SambaLooperScreen(state: AppState, onBack: () -> Unit) {
    val samba = state.sambaLooper
    DisposableEffect(Unit) { onDispose { samba.stop() } }

    // Eraser tool: when on, tapping a cell clears it instead of cycling its voice.
    var eraseMode by remember { mutableStateOf(false) }
    // Accent tool: when on, tapping a non-silent cell toggles its accent (louder hit).
    var accentMode by remember { mutableStateOf(false) }
    // Free-transform state for the drum-loop grid (#6). scaleX/scaleY are INDEPENDENT so
    // you can stretch just the width (widen narrow cells) or just the height. Two-finger
    // pinch zooms + pans; single-finger drag pans once zoomed. Pure render-layer effect
    // (graphicsLayer) over a grid whose 16 cells fit the width at 1× by default.
    var scaleX by remember { mutableFloatStateOf(1f) }
    var scaleY by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var toneSheetOpen by remember { mutableStateOf(false) }

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

        // ----- Tap tempo + metronome (BPM itself now lives in the TransportDock) -----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // Tap-tempo: tap along; BPM follows the average tap interval.
            OutlinedButton(onClick = { samba.tapTempo() }) { Text("Tap tempo") }
            Spacer(Modifier.width(6.dp))
            // Metronome click on each beat (accented downbeats).
            FilterChip(
                selected = samba.metronome,
                onClick = { samba.metronome = !samba.metronome },
                label = { Text("Metro") },
            )
        }

        // ----- Scrollable body: swing, loop setup, grid, footer actions -----
        // Wrapped so the TransportDock below stays pinned and visible instead of
        // scrolling away with the (potentially long) instrument grid + footer.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
        // ----- Swing (Brazilian 16th-note swing; 0 = straight) -----
        // Only meaningful on a 1/16 grid (a quarter-note beat split into four 16ths):
        // it holds the 1st & 3rd 16ths in place, delays the 2nd, and pulls the 4th
        // early — straight → triplet lilt. On any other division it does nothing, so
        // the slider is disabled and the label says why.
        val swingActive = samba.meter.beatUnit == 4 && samba.meter.division == 16
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

        Spacer(Modifier.height(8.dp))
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
        val kit = samba.pattern.instruments
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
                                val oldSx = scaleX; val oldSy = scaleY
                                scaleX = (scaleX * zx).coerceIn(0.4f, 4f)
                                scaleY = (scaleY * zy).coerceIn(0.4f, 4f)
                                val mx = kotlin.math.max(0f, size.width * (scaleX - 1f) / 2f)
                                val my = kotlin.math.max(0f, size.height * (scaleY - 1f) / 2f)
                                offsetX = (offsetX + panX + (cenX - size.width / 2f) * (oldSx - scaleX)).coerceIn(-mx, mx)
                                offsetY = (offsetY + panY + (cenY - size.height / 2f) * (oldSy - scaleY)).coerceIn(-my, my)
                                event.changes.forEach { it.consume() }
                                dragging = true
                            } else if (pressed.size == 1 && (scaleX > 1.001f || scaleY > 1.001f)) {
                                val ch = pressed[0]
                                val dx = ch.position.x - ch.previousPosition.x
                                val dy = ch.position.y - ch.previousPosition.y
                                totalDrag += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                                if (dragging || totalDrag > slop) {
                                    dragging = true
                                    val mx = kotlin.math.max(0f, size.width * (scaleX - 1f) / 2f)
                                    val my = kotlin.math.max(0f, size.height * (scaleY - 1f) / 2f)
                                    offsetX = (offsetX + dx).coerceIn(-mx, mx)
                                    offsetY = (offsetY + dy).coerceIn(-my, my)
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
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        translationX = offsetX
                        translationY = offsetY
                    },
            ) {
                for ((i, inst) in kit.withIndex()) {
                    InstrumentRow(
                        samba = samba,
                        instrument = inst,
                        eraseMode = eraseMode,
                        accentMode = accentMode,
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
        if (scaleX > 1.001f || scaleY > 1.001f || offsetX != 0f || offsetY != 0f) {
            TextButton(onClick = { scaleX = 1f; scaleY = 1f; offsetX = 0f; offsetY = 0f }) {
                Text("Reset zoom")
            }
        }

        Spacer(Modifier.height(8.dp))

        // ----- Footer actions: Save / Load / Clear -----
        val saved by samba.savedPatterns.collectAsState(initial = emptyMap())
        var saveDialog by remember { mutableStateOf(false) }
        var loadMenu by remember { mutableStateOf(false) }
        var saveName by remember { mutableStateOf("") }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Eraser: when on, tapping a cell clears it (no need to cycle every voice).
            if (eraseMode) {
                Button(onClick = { eraseMode = false }) { Text("Erase ✓") }
            } else {
                OutlinedButton(onClick = { eraseMode = true; accentMode = false }) { Text("Erase") }
            }
            // Accent: when on, tapping a hit toggles its accent (played louder).
            if (accentMode) {
                Button(onClick = { accentMode = false }) { Text("Accent ✓") }
            } else {
                OutlinedButton(onClick = { accentMode = true; eraseMode = false }) { Text("Accent") }
            }
            OutlinedButton(onClick = { saveName = ""; saveDialog = true }) { Text("Save…") }
            Box {
                OutlinedButton(onClick = { loadMenu = true }) { Text("Load…") }
                DropdownMenu(expanded = loadMenu, onDismissRequest = { loadMenu = false }) {
                    // Built-in grooves first (same set as the web), then saved beats.
                    for ((name, pat) in app.guitar.theory.PercussionBuiltins.ALL) {
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { samba.loadPattern(pat); loadMenu = false },
                        )
                    }
                    if (saved.isNotEmpty()) HorizontalDivider()
                    for ((name, pat) in saved) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { samba.deleteSaved(name) }) { Text("✕") }
                                }
                            },
                            onClick = { samba.loadPattern(pat); loadMenu = false },
                        )
                    }
                    if (saved.isEmpty()) {
                        DropdownMenuItem(text = { Text("(no saved beats yet)") }, enabled = false, onClick = {})
                    }
                }
            }
            OutlinedButton(onClick = { samba.clearAll() }) { Text("Clear all") }

            // Add an instrument to the kit, sourced from the catalog.
            var addMenu by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { addMenu = true }) { Text("+ Add instrument") }
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
        } // end scrollable body

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
        )
        if (toneSheetOpen) ToneSheet(state, onDismiss = { toneSheetOpen = false })
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

@Composable
private fun InstrumentRow(
    samba: SambaLooperState,
    instrument: PercussionInstrument,
    eraseMode: Boolean,
    accentMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val voices = PercussionVoices.voicesFor(instrument)
    val audible = samba.isAudible(instrument)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // ---- Row label: instrument name (tap → voice popup) + Mute / Solo ----
        Column(
            modifier = Modifier.width(ROW_LABEL_DP.dp).fillMaxHeight().padding(end = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            var voiceMenu by remember { mutableStateOf(false) }
            Box {
                // Name + ▾ hints the voice popup; the whole row is the tap target.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .pointerInput(instrument) { detectTapGestures(onTap = { voiceMenu = true }) }
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        instrument.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = if (audible) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(" ▾", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Voice menu: a button per voice; tap to audition (stays open to
                // compare). Tap outside to dismiss.
                DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                    // Overall instrument volume (task #4). Lives in the voice popup
                    // so the dense step-grid stays uncluttered.
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
                        onClick = { voiceMenu = false; samba.removeInstrument(instrument) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleTag("M", on = instrument in samba.muted,
                    onColor = MaterialTheme.colorScheme.error) { samba.toggleMute(instrument) }
                ToggleTag("S", on = instrument in samba.soloed,
                    onColor = MaterialTheme.colorScheme.primary) { samba.toggleSolo(instrument) }
            }
        }
        // ---- step cells (dimmed when the track isn't audible) ----
        // Fill-width (weight per cell) so the whole cycle fits at 1×; pinch-zoom the
        // container to enlarge. Beat-group index drives an alternating tint so the
        // quarter-note groups (4 sixteenths each) are easy to count.
        val slots = samba.pattern.slots
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
                // Beat separators: a gap after each beat; a wider gap at each bar line.
                if ((slot + 1) % slotsPerBeat == 0 && slot != slots - 1) {
                    val w = if ((slot + 1) % slotsPerBar == 0) 6.dp else 3.dp
                    Spacer(Modifier.width(w))
                }
            }
        }
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
    val voice = samba.pattern.voiceAt(instrument, slot)
    val accented = samba.pattern.isAccented(instrument, slot)
    val isPlayhead = samba.currentSlot == slot
    val base = MaterialTheme.colorScheme.surfaceVariant
    // Empty cells are brightened (were near-invisible on the black background) and
    // tinted per beat-group so the quarter-note grouping reads at a glance: the first
    // 16th of each beat is brightest, and alternating beats step between two shades.
    val emptyFill = when {
        isBeatStart -> base.copy(alpha = 0.95f)
        beatIndex % 2 == 0 -> base.copy(alpha = 0.75f)
        else -> base.copy(alpha = 0.6f)
    }
    val fill = when (voice) {
        null -> emptyFill
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    // Border precedence: playhead > accent ring > none.
    val borderWidth = if (isPlayhead) 2.dp else if (accented) 2.dp else 0.dp
    val borderColor = when {
        isPlayhead -> MaterialTheme.colorScheme.onBackground
        accented -> Color.White.copy(alpha = 0.85f)
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

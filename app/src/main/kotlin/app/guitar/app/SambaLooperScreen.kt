package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.PercussionInstrument
import app.guitar.theory.PercussionMeter
import app.guitar.theory.PercussionVoices
import kotlin.math.max

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

    // Free-transform state for the drum-loop grid. scaleX/scaleY are INDEPENDENT so
    // a 2-finger pinch can change the aspect ratio (stretch wider or taller) — handy
    // in portrait where the default grid looks squished. Single-finger drag pans
    // once zoomed. The transform is a pure render-layer effect (graphicsLayer).
    var scaleX by remember { mutableFloatStateOf(1f) }
    var scaleY by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // Eraser tool: when on, tapping a cell clears it instead of cycling its voice.
    var eraseMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
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
            if (samba.isPlaying) {
                Button(onClick = { samba.stop() }) { Text("Stop ⏹") }
            } else {
                Button(onClick = { samba.start() }) { Text("Play ▶") }
            }
            Spacer(Modifier.width(8.dp))
            AudioQuickButton(state, compact = true)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { samba.stop(); onBack() }) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))

        // ----- BPM -----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("BPM: ${samba.bpm}", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(96.dp))
            Slider(
                value = samba.bpm.toFloat(),
                onValueChange = { samba.bpm = it.toInt() },
                valueRange = 60f..200f,
                modifier = Modifier.weight(1f),
            )
        }

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
        // The grid has a FIXED height (not weight) so each of the 4 instrument
        // rows always has room for its name, the ▾ voice popup, and the M/S
        // toggles — nothing gets clipped in short-height (landscape) layouts.
        // 4 rows × ROW_HEIGHT_DP + 3 inter-row spacers + caption row.
        val gridHeight =
            (PercussionInstrument.entries.size * ROW_HEIGHT_DP +
                (PercussionInstrument.entries.size - 1) * 6 + CAPTION_DP).dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .clipToBounds()
                // Gestures on the CONTAINER (never on a cell, so cell taps still hit):
                //  • 2 fingers → independent X/Y zoom (changes the aspect ratio) + pan.
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
                                val mx = max(0f, size.width * (scaleX - 1f) / 2f)
                                val my = max(0f, size.height * (scaleY - 1f) / 2f)
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
                                    val mx = max(0f, size.width * (scaleX - 1f) / 2f)
                                    val my = max(0f, size.height * (scaleY - 1f) / 2f)
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
                for ((i, inst) in PercussionInstrument.entries.withIndex()) {
                    InstrumentRow(
                        samba = samba,
                        instrument = inst,
                        eraseMode = eraseMode,
                        modifier = Modifier.height(ROW_HEIGHT_DP.dp).fillMaxWidth(),
                    )
                    if (i != PercussionInstrument.entries.lastIndex) {
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
                OutlinedButton(onClick = { eraseMode = true }) { Text("Erase") }
            }
            OutlinedButton(onClick = { saveName = ""; saveDialog = true }) { Text("Save…") }
            Box {
                OutlinedButton(onClick = { loadMenu = true }) { Text("Load…") }
                DropdownMenu(expanded = loadMenu, onDismissRequest = { loadMenu = false }) {
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
    }
}

private const val ROW_LABEL_DP = 128
private const val ROW_HEIGHT_DP = 70   // per-instrument row: name + ▾ + M/S all fit
private const val CAPTION_DP = 18      // bar/beat caption strip below the rows

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
                    // Per-instrument volume (task #4). Lives in the voice popup so
                    // the dense step-grid stays uncluttered.
                    Column(modifier = Modifier.width(240.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        val vol = samba.volumeOf(instrument)
                        Text(
                            "Volume: ${(vol * 100).toInt()}%",
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
                        "  ${instrument.displayName} voices",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    for (v in voices) {
                        DropdownMenuItem(
                            text = { Text("${v.glyph}   ${v.displayName}") },
                            onClick = { samba.preview(instrument, v.index) },
                        )
                    }
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
        val slots = samba.pattern.slots
        val slotsPerBeat = samba.meter.slotsPerBeat
        val slotsPerBar = samba.meter.slotsPerBar
        Row(modifier = Modifier.weight(1f).fillMaxHeight().alpha(if (audible) 1f else 0.4f)) {
            for (slot in 0 until slots) {
                Cell(
                    samba = samba,
                    instrument = instrument,
                    slot = slot,
                    eraseMode = eraseMode,
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
    eraseMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val voice = samba.pattern.voiceAt(instrument, slot)
    val isPlayhead = samba.currentSlot == slot
    val base = MaterialTheme.colorScheme.surfaceVariant
    val fill = when (voice) {
        null -> base.copy(alpha = 0.4f)
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val border = if (isPlayhead) MaterialTheme.colorScheme.onBackground else Color.Transparent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .border(if (isPlayhead) 2.dp else 0.dp, border, RoundedCornerShape(4.dp))
            .pointerInput(instrument, slot, eraseMode) {
                detectTapGestures(
                    onTap = {
                        if (eraseMode) samba.clearCell(instrument, slot)
                        else samba.toggleSlot(instrument, slot)
                    },
                    onLongPress = { samba.clearCell(instrument, slot) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (voice != null) {
            Text(
                PercussionVoices.voice(instrument, voice).glyph,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.PERCUSSION_SLOTS
import app.guitar.theory.PercussionInstrument
import app.guitar.theory.PercussionVoices

/**
 * Drum-machine / samba-looper screen. A 4-row × 16-cell step grid (2 bars of
 * 2/4 in sixteenths). Tapping a cell cycles its voice
 * (silent → voice 1 → … → silent); long-press clears the cell. A tinted column
 * tracks the playhead while looping.
 */
@Composable
fun SambaLooperScreen(state: AppState, onBack: () -> Unit) {
    val samba = state.sambaLooper
    DisposableEffect(Unit) { onDispose { samba.stop() } }

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

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // ----- Grid -----
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            for (inst in PercussionInstrument.entries) {
                InstrumentRow(
                    samba = samba,
                    instrument = inst,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }
            // Beat / bar caption
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(ROW_LABEL_DP.dp))
                Text(
                    "bar 1  ·  bar 2   (2/4, sixteenths)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ----- Footer actions -----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { samba.loadSamba() }) { Text("Load samba") }
            OutlinedButton(onClick = { samba.clearAll() }) { Text("Clear all") }
        }
    }
}

private const val ROW_LABEL_DP = 118

@Composable
private fun InstrumentRow(
    samba: SambaLooperState,
    instrument: PercussionInstrument,
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
        // ---- 16 cells (dimmed when the track isn't audible) ----
        Row(modifier = Modifier.weight(1f).fillMaxHeight().alpha(if (audible) 1f else 0.4f)) {
            for (slot in 0 until PERCUSSION_SLOTS) {
                Cell(
                    samba = samba,
                    instrument = instrument,
                    slot = slot,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp),
                )
                // Beat separators: thicker gap after every 4th cell; bar gap after 8th.
                if (slot % 4 == 3 && slot != PERCUSSION_SLOTS - 1) {
                    val w = if (slot == 7) 6.dp else 3.dp
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
            .pointerInput(instrument, slot) {
                detectTapGestures(
                    onTap = { samba.toggleSlot(instrument, slot) },
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

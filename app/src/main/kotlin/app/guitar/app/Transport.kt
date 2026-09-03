package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared transport chrome for the Ear / Rhythm / Loop screens (Signal redesign,
 * move #2 — see docs/superpowers/specs/2026-07-10-signal-gui-redesign-design.md).
 * Consolidates what used to be scattered per-screen Play/Stop buttons, BPM
 * sliders, and the old "Audio" quick-access dropdown into one persistent pill
 * plus one shared settings sheet ([ToneSheet]), both driven purely by the same
 * state each screen already exposed (no logic changes — see each call site).
 */

// ---------- Transport dock ----------

/**
 * Persistent pill: round act-colored Play/Stop button, an optional BPM readout
 * (tap opens a slider popover; the whole BPM block is omitted when [bpm] is
 * null, e.g. for a screen with no tempo concept), a spacer, and a teal-outlined
 * tone chip that opens [ToneSheet]. Each caller wires [onPlayStop]/[onBpm] to
 * its own loop state — this composable holds no playback logic itself.
 */
@Composable
fun TransportDock(
    playing: Boolean,
    onPlayStop: () -> Unit,
    bpm: Int?,
    onBpm: ((Int) -> Unit)?,
    toneLabel: String,
    onTone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Show BPM as an always-visible readout + inline slider (no popover) — drum machine. */
    inlineBpm: Boolean = false,
    /** Master output level 0..1. Pass both to add an always-visible 🔊 fader to the
     *  dock (drum machine only — every other screen leaves them null). */
    volume: Float? = null,
    onVolume: ((Float) -> Unit)? = null,
) {
    val palette = LocalSignal.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Round act-filled Play/Stop button — the dock's one primary action.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPlayStop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Stop" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }

        if (bpm != null && onBpm != null) {
            Spacer(Modifier.width(14.dp))
            if (inlineBpm) {
                // Always-visible readout + inline slider with −/+ fine steppers;
                // double-tap the number to type an exact tempo.
                NumericValueText(
                    "$bpm", value = bpm.toFloat(), min = 10f, max = 300f,
                    onSet = { onBpm(it.toInt()) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(2.dp))
                Text("BPM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                // Both ±5 steppers next to the caption; the slider follows.
                BpmStep("−") { onBpm((bpm - 5).coerceAtLeast(10)) }
                Spacer(Modifier.width(4.dp))
                BpmStep("+") { onBpm((bpm + 5).coerceAtMost(300)) }
                Slider(
                    value = bpm.toFloat(),
                    onValueChange = { onBpm(it.toInt()) },
                    valueRange = 10f..300f,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
            } else {
                var open by remember { mutableStateOf(false) }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { open = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "$bpm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        Column(modifier = Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // Double-tap the label to type an exact tempo.
                            NumericValueText(
                                "Tempo: $bpm BPM", value = bpm.toFloat(), min = 10f, max = 300f,
                                onSet = { onBpm(it.toInt()) },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BpmStep("−") { onBpm((bpm - 5).coerceAtLeast(10)) }
                                Spacer(Modifier.width(6.dp))
                                BpmStep("+") { onBpm((bpm + 5).coerceAtMost(300)) }
                                Slider(
                                    value = bpm.toFloat(),
                                    onValueChange = { onBpm(it.toInt()) },
                                    valueRange = 10f..300f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Master output fader (drum machine): always visible next to the tempo, since
        // it is the control you reach for while the loop is running.
        if (volume != null && onVolume != null) {
            Spacer(Modifier.width(8.dp))
            Text("🔊", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = volume,
                onValueChange = onVolume,
                valueRange = 0f..1f,
                modifier = Modifier.width(84.dp).padding(horizontal = 4.dp),
            )
            Text(
                "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }

        // Tone chip: teal (feedback) outline + text, per the "current tone" role.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, palette.feedback, RoundedCornerShape(999.dp))
                .clickable(onClick = onTone)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                toneLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.feedback,
            )
        }
    }
}

// ---------- Shared small helpers ----------

/** Compact circular −/+ tempo stepper: a 30dp tap target with a subtle outline,
 *  for one-BPM nudges beside the tempo slider (finer than dragging). */
@Composable
private fun BpmStep(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
    }
}

/** Small-caps section label used above a group of controls in sheets. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Full-width single-choice segmented row. Selected segment is filled with the
 *  act color and uses onAct-style (dark-on-act) text via `onPrimary` — never
 *  the M3 `secondary`/`onSecondary` pair, which is too low-contrast for text
 *  (see the T1 ledger note in .superpowers/sdd/progress-signal.md). */
@Composable
fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { i, opt ->
            SegmentedButton(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                ),
                label = { Text(label(opt), maxLines = 1) },
            )
        }
    }
}

/** Icon + label/value row with a slider directly beneath — used for the plain
 *  single-slider Tone rows (Reverb, Strum spread, Ring sustain). */
@Composable
private fun SliderRow(
    icon: ImageVector,
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** Icon + label/sub row with a trailing switch — used for Boost root note and
 *  the New audio engine A/B toggle. */
@Composable
private fun SwitchRow(
    icon: ImageVector,
    label: String,
    sub: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheck)
    }
}

/** Expandable EQ row: collapsed it's just an icon + summary line; expanded it
 *  hosts the existing 3-band sliders (Task 3) + a "Flat" reset. */
@Composable
private fun EqRow(state: AppState) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.Equalizer, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("EQ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Bass / Mid / Treble — ${state.sound.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            // Reading eqVersion keys this block to every EQ change so the sliders
            // (and their labels) recompose live.
            state.eqVersion
            val e = state.eqFor(state.sound)
            Column(modifier = Modifier.padding(start = 30.dp, bottom = 4.dp)) {
                EqBandSlider(state, "Bass", e.bassDb, Band.Bass)
                EqBandSlider(state, "Mid", e.midDb, Band.Mid)
                EqBandSlider(state, "Treble", e.trebleDb, Band.Treble)
                TextButton(onClick = { state.resetEq(state.sound) }) { Text("Flat") }
            }
        }
    }
}

@Composable
private fun EqBandSlider(state: AppState, label: String, value: Float, band: Band) {
    Text("$label: ${value.toInt()} dB", style = MaterialTheme.typography.bodySmall)
    Slider(
        value = value,
        onValueChange = { state.setEqBand(state.sound, band, it) },
        valueRange = -12f..12f,
    )
}

// ---------- Tone sheet ----------

/**
 * The one Tone settings sheet, opened identically from every screen's dock (or,
 * on screens with no transport dock, from a small Tune icon button). Replaces
 * the old "Audio" quick-access dropdown entirely: Sound picker, EQ, Reverb,
 * Strum spread, Ring sustain, Boost root note, and the audio-engine A/B toggle
 * all live here now, reading/writing the exact same [AppState] members the old
 * dropdown did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToneSheet(state: AppState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
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
            Text("Tone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            SectionLabel("Sound" + if (state.soundLoading) " (loading…)" else "")
            Spacer(Modifier.height(8.dp))
            SegmentedRow(
                options = GuitarSound.entries,
                selected = state.sound,
                onSelect = { state.setSound(it) },
                label = { it.name },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            EqRow(state)

            HorizontalDivider()
            run {
                state.reverbVersion
                val rev = state.reverbFor(state.sound)
                SliderRow(
                    icon = Icons.Outlined.Waves,
                    label = "Reverb",
                    valueLabel = "${(rev * 100).toInt()}%",
                    value = rev,
                    range = 0f..1f,
                    onChange = { state.setReverb(state.sound, it) },
                )
            }

            HorizontalDivider()
            SliderRow(
                icon = Icons.AutoMirrored.Outlined.CompareArrows,
                label = "Strum spread",
                valueLabel = if (state.strumMs == 0) "at once" else "${state.strumMs} ms",
                value = state.strumMs.toFloat(),
                range = 0f..150f,
                onChange = { state.setStrumMs(it.toInt()) },
            )

            HorizontalDivider()
            SliderRow(
                icon = Icons.Outlined.Timer,
                label = "Ring sustain",
                valueLabel = "${"%.1f".format(state.ringSustainMs / 1000f)} s",
                value = state.ringSustainMs.toFloat(),
                range = 300f..4000f,
                onChange = { state.setRingSustainMs(it.toInt()) },
            )

            HorizontalDivider()
            SwitchRow(
                icon = Icons.Outlined.MusicNote,
                label = "Boost root note",
                sub = "Play each chord's root louder so it cuts through",
                checked = state.earTraining.earBoostTonic,
                onCheck = { state.earTraining.earBoostTonic = it },
            )

            // The audio engine is always the modern chain now (A/B toggle removed);
            // the Synth SOUND option remains in the Sound picker above.

            Spacer(Modifier.height(20.dp))
        }
    }
}

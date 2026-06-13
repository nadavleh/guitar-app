package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.res.Configuration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import app.guitar.theory.NoteSpeller
import app.guitar.theory.Tunings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Tuner screen.
 *
 * Layout (landscape):
 *   - Top: title + back button + A4-ref readout
 *   - Center: large quarter-ring dial spanning ±50 cents with cent ticks and a
 *     pivoting needle. Big detected note label in the middle (tappable to play
 *     the equal-tempered reference tone).
 *   - Bottom: row of buttons for each open-string note of the current tuning.
 *     Tap to hear the reference tone for that string.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TunerScreen(state: AppState, onBack: () -> Unit) {
    val tuner = remember(state) { TunerState(a4Provider = { state.a4Hz }) }
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val customTunings by state.customTunings.collectAsState(initial = emptyMap())

    LaunchedEffect(Unit) {
        // Try to start the mic; if it fails (permission missing) capturing stays false
        // and the UI will show the explanation.
        tuner.start()
    }
    DisposableEffect(Unit) { onDispose { tuner.stop() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        // -------- Top bar --------
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("TUNER",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f))
            Text("A4 = ${state.a4Hz.toInt()} Hz",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            AudioQuickButton(state, compact = true)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        Spacer(Modifier.height(8.dp))
        // -------- Change tuning on the fly (no need to open Options) --------
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tunings.presetsFor(state.instrument).forEach { (name, t) ->
                FilterChip(
                    selected = name == state.tuningName && !state.isEditedTuning,
                    onClick = { state.selectTuning(name, t) },
                    label = { Text(name, maxLines = 1) },
                )
            }
            // The user's saved custom tunings, so they can switch to those here too.
            customTunings.forEach { (name, t) ->
                FilterChip(
                    selected = name == state.tuningName && !state.isEditedTuning,
                    onClick = { state.selectTuning(name, t) },
                    label = { Text(name, maxLines = 1) },
                )
            }
        }

        // -------- Dial + note label --------
        // In portrait the available column is very tall; constrain the dial to a
        // compact aspect and center it so it isn't a tiny ring floating in space.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = if (portrait) Modifier.fillMaxWidth().aspectRatio(1.5f)
                           else Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (!tuner.capturing) {
                    MicPermissionPanel(state, tuner)
                } else {
                    TunerDial(tuner = tuner, state = state)
                }
            }
        }

        // -------- Tuning reference row --------
        TuningRefRow(state, tuner)
    }
}

// -------- the dial --------

@Composable
private fun TunerDial(tuner: TunerState, state: AppState) {
    val midi = tuner.midi
    val cents = tuner.cents
    val inTune = cents != null && abs(cents) <= 10f
    val tunedColor = Color(0xFF66BB6A)   // bright green; theme tertiary varies
    val noteColor = if (inTune) tunedColor else MaterialTheme.colorScheme.onSurface
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
    val needleColor = if (inTune) tunedColor else MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Quarter-ring dial drawn on a Canvas. Apex at the top (north), spanning
        // -50¢ on the upper-left to +50¢ on the upper-right (90° total sweep).
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDial(cents = cents, ringColor = ringColor, needleColor = needleColor, tunedColor = tunedColor)
        }
        // Centered overlay: detected note + cents readout + tap-to-play hint.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val (noteText, octText) = if (midi != null) noteLabel(midi) else "—" to ""
            // Make the note label tappable: tap → lock dial to spot-on + play reference.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = midi != null) {
                        if (midi != null) {
                            state.playReferencePitch(midi)
                            // Lock the dial to "spot on" while the reference tone rings.
                            tuner.lockTo(midi, holdMs = state.ringSustainMs.toLong())
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        noteText,
                        color = noteColor,
                        fontSize = 96.sp,
                        style = MaterialTheme.typography.displayLarge,
                    )
                    if (octText.isNotEmpty()) {
                        Text(
                            octText,
                            color = noteColor.copy(alpha = 0.7f),
                            fontSize = 28.sp,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                }
            }
            val centsText = cents?.let { "${if (it >= 0) "+" else ""}${"%.0f".format(it)} ¢" } ?: ""
            Text(
                centsText,
                color = if (inTune) tunedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
            if (inTune) {
                Text(
                    "IN TUNE",
                    color = tunedColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else if (midi != null) {
                Text(
                    "tap note to hear reference",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun DrawScope.drawDial(cents: Float?, ringColor: Color, needleColor: Color, tunedColor: Color) {
    // Place the arc center BELOW the visible area so the quarter ring fills the top.
    val cx = size.width / 2f
    val cy = size.height * 0.74f
    val radius = minOf(size.width * 0.46f, size.height * 0.62f)

    // Quarter-ring arc: 90° sweep centered on north (270° in Canvas conventions).
    drawArc(
        color = ringColor,
        startAngle = 225f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = 4f),
    )

    // Cent ticks: every 1¢ is a 1° spacing (90° / 100 cents = 0.9° per cent — we'll use 0.9°).
    // Major ticks every 10¢, minor ticks every 5¢, micro ticks every 1¢.
    for (c in -50..50) {
        val theta = (c / 50.0) * 45.0   // degrees from north, + = right
        val (sx, sy) = polar(cx, cy, radius - tickLengthFor(c), theta)
        val (ex, ey) = polar(cx, cy, radius + 2f, theta)
        val color = when {
            c == 0 -> tunedColor
            abs(c) <= 10 -> tunedColor.copy(alpha = 0.5f)
            else -> ringColor
        }
        val w = when {
            c == 0 -> 4f
            c % 10 == 0 -> 3f
            c % 5 == 0 -> 1.8f
            else -> 1f
        }
        drawLine(color = color, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = w)
    }

    // Tick labels at -50, -25, 0, +25, +50
    // (Drawing text with Canvas drawText needs a TextMeasurer; skipping for simplicity —
    // the dial's symmetry is enough to communicate the scale.)

    // Needle: small pivot at (cx, cy), pointing at the current cents.
    val c = cents
    if (c != null) {
        val theta = (c.coerceIn(-50f, 50f) / 50.0) * 45.0
        val (nx, ny) = polar(cx, cy, radius - 8f, theta)
        drawLine(
            color = needleColor,
            start = Offset(cx, cy),
            end = Offset(nx, ny),
            strokeWidth = 6f,
        )
        // Pivot circle at the center
        drawCircle(
            color = needleColor,
            radius = 10f,
            center = Offset(cx, cy),
        )
    }
}

private fun tickLengthFor(c: Int): Float = when {
    c == 0 -> 26f
    c % 10 == 0 -> 22f
    c % 5 == 0 -> 14f
    else -> 8f
}

/** Convert "polar from north, + = right" (theta in degrees) to a Cartesian endpoint. */
private fun polar(cx: Float, cy: Float, radius: Float, thetaDegFromNorth: Double): Pair<Float, Float> {
    val rad = (thetaDegFromNorth * PI / 180.0)
    val x = cx + (radius * sin(rad)).toFloat()
    val y = cy - (radius * cos(rad)).toFloat()
    return x to y
}

// -------- bottom row: tuning reference --------

@Composable
private fun TuningRefRow(state: AppState, tuner: TunerState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Reference",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Each open string of the current tuning, low → high (left → right).
        // Tapping a string plays its equal-tempered reference AND locks the dial to
        // "spot on" for that note so the user can confirm their ear matches the display.
        for ((i, note) in state.liveTuning.openStrings.withIndex()) {
            val pcName = NoteSpeller.spell(note.pitchClass)
            OutlinedButton(
                onClick = {
                    state.playReferencePitch(note.midi.value)
                    tuner.lockTo(note.midi.value, holdMs = state.ringSustainMs.toLong())
                },
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("S${state.liveTuning.stringCount - i}", style = MaterialTheme.typography.labelSmall)
                    Text("$pcName${noteOctave(note.midi.value)}",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// -------- mic-permission explainer --------

@Composable
private fun MicPermissionPanel(state: AppState, tuner: TunerState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            "🎤 Microphone access required",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The tuner listens to your guitar through the mic to detect the pitch. " +
                "Grant the RECORD_AUDIO permission in your phone settings, then come back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { tuner.start() }) { Text("Retry") }
    }
}

// -------- helpers --------

private fun noteLabel(midi: Int): Pair<String, String> {
    val pc = ((midi % 12) + 12) % 12
    val octave = midi / 12 - 1
    val name = NoteSpeller.spell(app.guitar.theory.PitchClass(pc))
    return name to "$octave"
}

private fun noteOctave(midi: Int): Int = midi / 12 - 1

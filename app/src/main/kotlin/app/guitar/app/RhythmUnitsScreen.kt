package app.guitar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitar.theory.RhythmNoteType
import app.guitar.theory.RhythmUnit
import app.guitar.theory.RhythmUnits

/**
 * Rhythmic Units — a single-section screen to learn & train the 8 basic one-beat
 * rhythmic units. Each unit is a card with a music-notation thumbnail; tapping it
 * loops the unit's click pattern at the transport BPM (the playing card lights up).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RhythmUnitsScreen(state: AppState, onBack: () -> Unit) {
    val ru = state.rhythmUnits
    // Stop the loop when NAVIGATING AWAY, but survive a rotation (which disposes+
    // recreates this composable without changing the route) — same guard as the other
    // looping screens (see v2.14.1).
    DisposableEffect(Unit) { onDispose { if (state.currentSheet != Sheet.RhythmUnits) ru.stop() } }

    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("RHYTHM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { ru.stop(); onBack() }) { Text("Back") }
        }
        Text("Tap a unit to loop it. Each is one beat; the downbeat is accented.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(10.dp))

        // Transport: Play/Stop + BPM.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { ru.toggle() }, enabled = ru.selectedId != null) {
                Text(if (ru.isPlaying) "Stop ■" else "Play ▶")
            }
            Spacer(Modifier.weight(1f))
            Text("${ru.bpm} BPM", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = ru.bpm.toFloat(),
            onValueChange = { ru.changeBpm(it.toInt()) },
            valueRange = 10f..300f,
        )

        Spacer(Modifier.height(6.dp))
        Text("Rhythmic units", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            for (unit in RhythmUnits.ALL) {
                val playing = ru.selectedId == unit.id && ru.isPlaying
                RhythmUnitCard(unit, playing, onTap = { ru.select(unit.id) }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RhythmUnitCard(unit: RhythmUnit, playing: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val teal = LocalSignal.current.feedback
    val border = if (playing) teal else MaterialTheme.colorScheme.outline
    val bg = if (playing) teal.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val noteColor = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (playing) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onTap() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RhythmNotation(unit, noteColor, modifier = Modifier.fillMaxWidth().height(58.dp))
        Spacer(Modifier.height(4.dp))
        Text(unit.count, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(unit.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Draws [unit] as simple music notation: noteheads at their beat positions, stems,
 *  a primary beam across sub-quarter notes, secondary (16th) beams/stubs, a dot for
 *  the dotted eighth, and a "3" for the triplet. Placement follows onset fractions. */
@Composable
private fun RhythmNotation(unit: RhythmUnit, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padL = w * 0.12f
        val padR = w * 0.12f
        val usable = w - padL - padR
        val baseline = h * 0.72f
        val beamY = h * 0.20f
        val headRx = (h * 0.11f).coerceAtMost(w * 0.05f)
        val headRy = headRx * 0.78f
        val stemW = (h * 0.035f).coerceAtLeast(1.5f)
        val beamThick = h * 0.10f

        val fractions = unit.onsetFractions()
        // A note is beamable if it is shorter than a quarter (everything except a lone quarter).
        val beamable = unit.notes.map { it.type != RhythmNoteType.Quarter }
        // Note x-centers and stem x (stem on the right edge of the notehead).
        val cx = fractions.map { padL + (it.toFloat()) * usable + headRx }
        val stemX = cx.map { it + headRx * 0.9f }

        // Noteheads + stems.
        for (i in unit.notes.indices) {
            drawOval(
                color = color,
                topLeft = Offset(cx[i] - headRx, baseline - headRy),
                size = androidx.compose.ui.geometry.Size(headRx * 2, headRy * 2),
            )
            if (beamable[i] || unit.notes.size == 1) {
                drawLine(color, Offset(stemX[i], baseline - headRy * 0.4f), Offset(stemX[i], beamY), stemW)
            }
        }

        // Dotted-eighth augmentation dot (after its notehead).
        unit.notes.forEachIndexed { i, n ->
            if (n.type == RhythmNoteType.DottedEighth) {
                drawCircle(color, radius = headRy * 0.42f, center = Offset(cx[i] + headRx * 1.7f, baseline))
            }
        }

        // Primary beam across all beamable notes (they all sit in one beat).
        val beamIdx = unit.notes.indices.filter { beamable[it] }
        if (beamIdx.size >= 2) {
            drawLine(color, Offset(stemX[beamIdx.first()], beamY), Offset(stemX[beamIdx.last()], beamY),
                beamThick, cap = StrokeCap.Butt)
            // Secondary (16th) beam: full segment between adjacent sixteenths, else a stub.
            val secY = beamY + beamThick * 1.5f
            val is16 = unit.notes.map { it.type == RhythmNoteType.Sixteenth }
            val stubLen = (usable / unit.notes.size) * 0.4f
            var i = 0
            while (i < unit.notes.size) {
                if (is16[i]) {
                    if (i + 1 < unit.notes.size && is16[i + 1]) {
                        drawLine(color, Offset(stemX[i], secY), Offset(stemX[i + 1], secY), beamThick, cap = StrokeCap.Butt)
                        i += 2
                        continue
                    } else {
                        // isolated sixteenth → stub toward the beat interior
                        val dir = if (i == 0) 1f else -1f
                        drawLine(color, Offset(stemX[i], secY), Offset(stemX[i] + dir * stubLen, secY), beamThick, cap = StrokeCap.Butt)
                    }
                }
                i++
            }
        }

        // Triplet "3" above the beam.
        if (unit.notes.firstOrNull()?.type == RhythmNoteType.TripletEighth) {
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(
                        (color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                    textSize = h * 0.22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText("3", (stemX.first() + stemX.last()) / 2f, beamY - h * 0.04f, paint)
            }
        }
    }
}

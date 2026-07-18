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
        RhythmSection("Rhythmic units", RhythmUnits.ALL, ru)
        Spacer(Modifier.height(14.dp))
        RhythmSection("With rests", RhythmUnits.RESTS, ru)
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RhythmSection(title: String, units: List<RhythmUnit>, ru: RhythmUnitState) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        for (unit in units) {
            val playing = ru.selectedId == unit.id && ru.isPlaying
            RhythmUnitCard(unit, playing, onTap = { ru.select(unit.id) }, modifier = Modifier.weight(1f))
        }
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

/** Draws [unit] as simple music notation: noteheads at their beat positions with stems,
 *  rest glyphs for rests, a primary beam across sub-quarter notes (spanning over any
 *  rests), secondary (16th) beams/stubs, a flag for a lone beamed note, a dot for the
 *  dotted eighth, and a "3" for the triplet. Placement follows the element start slots. */
@Composable
private fun RhythmNotation(unit: RhythmUnit, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padL = w * 0.12f
        val usable = w - padL * 2
        val baseline = h * 0.72f
        val beamY = h * 0.20f
        val headRx = (h * 0.11f).coerceAtMost(w * 0.05f)
        val headRy = headRx * 0.78f
        val stemW = (h * 0.035f).coerceAtLeast(1.5f)
        val beamThick = h * 0.10f
        val sub = unit.subdivision

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, wd: Float) =
            drawLine(color, Offset(x1, y1), Offset(x2, y2), wd, cap = StrokeCap.Butt)

        val startFrac = unit.starts.map { it.toFloat() / sub }
        val noteCx = startFrac.map { padL + it * usable + headRx }
        val stemX = noteCx.map { it + headRx * 0.9f }
        val is16 = unit.notes.map { !it.rest && it.type == RhythmNoteType.Sixteenth }
        val beamable = unit.notes.indices.filter { !unit.notes[it].rest && unit.notes[it].type != RhythmNoteType.Quarter }

        // Elements: noteheads + stems (notes) or rest glyphs (rests).
        unit.notes.forEachIndexed { i, n ->
            if (n.rest) {
                val cxR = padL + (startFrac[i] + (n.slots.toFloat() / 2f) / sub) * usable
                val midY = (beamY + baseline) / 2f
                val h2 = (baseline - beamY) * 0.40f
                val dotR = headRy * 0.55f
                val dotX = cxR - headRx * 0.25f
                val dotTop = midY - h2 + dotR
                drawCircle(color, dotR, Offset(dotX, dotTop))
                line(dotX + dotR * 0.6f, dotTop - dotR * 0.2f, cxR + headRx * 0.5f, midY + h2, stemW)
                if (n.type == RhythmNoteType.Sixteenth) {
                    drawCircle(color, dotR, Offset(dotX - dotR * 0.3f, dotTop + dotR * 1.8f))
                }
            } else {
                drawOval(color, Offset(noteCx[i] - headRx, baseline - headRy),
                    androidx.compose.ui.geometry.Size(headRx * 2, headRy * 2))
                line(stemX[i], baseline - headRy * 0.4f, stemX[i], beamY, stemW)
                if (n.type == RhythmNoteType.DottedEighth) {
                    drawCircle(color, headRy * 0.42f, Offset(noteCx[i] + headRx * 1.7f, baseline))
                }
            }
        }

        if (beamable.size >= 2) {
            // Primary beam spans the first→last beamed note (over any rests between).
            line(stemX[beamable.first()], beamY, stemX[beamable.last()], beamY, beamThick)
            val secY = beamY + beamThick * 1.5f
            val stubLen = (usable / unit.notes.size) * 0.4f
            for (i in unit.notes.indices) {
                if (!is16[i]) continue
                val hasNext16 = i + 1 < unit.notes.size && is16[i + 1]
                val hasPrev16 = i - 1 >= 0 && is16[i - 1]
                if (hasNext16) line(stemX[i], secY, stemX[i + 1], secY, beamThick)
                else if (!hasPrev16) {
                    val dir = if (i == beamable.first()) 1f else -1f
                    line(stemX[i], secY, stemX[i] + dir * stubLen, secY, beamThick)
                }
            }
        } else if (beamable.size == 1) {
            // Lone beamed note → a flag (eighth = 1, sixteenth = 2) instead of a beam.
            val j = beamable.first()
            val flags = if (unit.notes[j].type == RhythmNoteType.Sixteenth) 2 else 1
            val flagLen = headRx * 1.6f
            val flagDrop = (baseline - beamY) * 0.32f
            for (k in 0 until flags) {
                val y = beamY + k * flagDrop * 0.6f
                line(stemX[j], y, stemX[j] + flagLen, y + flagDrop, stemW * 1.1f)
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

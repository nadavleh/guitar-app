package app.guitar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.guitar.theory.FretPosition
import app.guitar.theory.Tuning

data class FretMark(
    val label: String,
    val isRoot: Boolean = false,
)

private const val OPEN_COL_FRAC = 0.06f
private const val NUT_FRAC = 0.020f
private const val STRING_DP = 36

@Composable
fun FretboardView(
    tuning: Tuning,
    marks: Map<FretPosition, FretMark>,
    onTap: (FretPosition) -> Unit,
    modifier: Modifier = Modifier,
    numFrets: Int = 12,
    selectedPosition: FretPosition? = null,
    leftHanded: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val colorBg = Color(0xFFFAEFCB)
    val colorString = Color(0xFF555555)
    val colorFret = Color(0xFFB0B0B0)
    val colorNut = Color(0xFF101010)
    val colorMarker = Color(0xFFA0A0A0)
    val colorRoot = Color(0xFFC62828)
    val colorTone = Color(0xFF1565C0)
    val colorSelectedRing = Color(0xFFFFA000)
    val colorLabel = Color.White

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((tuning.stringCount * STRING_DP).dp)
            .pointerInput(tuning, numFrets, leftHanded) {
                detectTapGestures { off ->
                    val pos = pixelToPosition(off, size.width.toFloat(), size.height.toFloat(), tuning.stringCount, numFrets, leftHanded)
                    if (pos != null) onTap(pos)
                }
            }
    ) {
        drawRect(color = colorBg, size = size)

        val w = size.width
        val h = size.height
        val openWidth = w * OPEN_COL_FRAC
        val nutWidth = w * NUT_FRAC
        val fretAreaWidth = w - openWidth - nutWidth
        val fretSpacing = fretAreaWidth / numFrets
        val stringSpacing = h / tuning.stringCount
        val firstStringY = stringSpacing / 2

        // Helper: mirror an x coordinate horizontally if left-handed.
        fun mx(x: Float) = if (leftHanded) w - x else x

        // Nut
        val nutLeft = mx(if (leftHanded) openWidth + nutWidth else openWidth)
        drawRect(
            color = colorNut,
            topLeft = Offset(nutLeft, 0f),
            size = Size(nutWidth, h)
        )
        // Fret lines
        for (f in 1..numFrets) {
            val x = mx(openWidth + nutWidth + f * fretSpacing)
            drawLine(colorFret, Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
        }
        // String lines (low E at bottom; idx 0 = lowest pitch)
        for (s in 0 until tuning.stringCount) {
            val y = firstStringY + (tuning.stringCount - 1 - s) * stringSpacing
            drawLine(
                colorString,
                Offset(0f, y),
                Offset(w, y),
                strokeWidth = 1.5f + s * 0.35f
            )
        }
        // Inlays
        val singleDots = listOf(3, 5, 7, 9, 15, 17, 19, 21)
        val doubleDots = listOf(12, 24)
        for (f in singleDots) if (f <= numFrets) {
            val x = mx(openWidth + nutWidth + (f - 0.5f) * fretSpacing)
            drawCircle(colorMarker, radius = 4f, center = Offset(x, h / 2))
        }
        for (f in doubleDots) if (f <= numFrets) {
            val x = mx(openWidth + nutWidth + (f - 0.5f) * fretSpacing)
            drawCircle(colorMarker, radius = 4f, center = Offset(x, h * 0.30f))
            drawCircle(colorMarker, radius = 4f, center = Offset(x, h * 0.70f))
        }
        // Marks (chord/scale)
        val dotR = stringSpacing * 0.42f
        val labelSp = (stringSpacing * 0.40f).toSp()
        for ((pos, mark) in marks) {
            if (pos.fret > numFrets) continue
            if (pos.stringIndex >= tuning.stringCount) continue
            val (cx, cy) = positionToPixel(pos, w, h, tuning.stringCount, numFrets, leftHanded)
            val color = if (mark.isRoot) colorRoot else colorTone
            drawCircle(color, radius = dotR, center = Offset(cx, cy))
            if (mark.label.isNotEmpty()) {
                val style = TextStyle(
                    color = colorLabel,
                    fontSize = labelSp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                val measured = measurer.measure(text = mark.label, style = style)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        cx - measured.size.width / 2f,
                        cy - measured.size.height / 2f,
                    )
                )
            }
        }
        // Selected ring
        if (selectedPosition != null) {
            val (cx, cy) = positionToPixel(selectedPosition, w, h, tuning.stringCount, numFrets, leftHanded)
            drawCircle(
                color = colorSelectedRing,
                radius = stringSpacing * 0.5f,
                center = Offset(cx, cy),
                style = Stroke(width = 4f)
            )
        }
    }
}

private fun positionToPixel(
    pos: FretPosition,
    w: Float, h: Float,
    stringCount: Int,
    numFrets: Int,
    leftHanded: Boolean,
): Pair<Float, Float> {
    val openWidth = w * OPEN_COL_FRAC
    val nutWidth = w * NUT_FRAC
    val fretAreaWidth = w - openWidth - nutWidth
    val fretSpacing = fretAreaWidth / numFrets
    val stringSpacing = h / stringCount
    val firstStringY = stringSpacing / 2
    val cxRight = if (pos.fret == 0) openWidth / 2f
                  else openWidth + nutWidth + (pos.fret - 0.5f) * fretSpacing
    val cx = if (leftHanded) w - cxRight else cxRight
    val cy = firstStringY + (stringCount - 1 - pos.stringIndex) * stringSpacing
    return cx to cy
}

private fun pixelToPosition(
    p: Offset,
    w: Float, h: Float,
    stringCount: Int,
    numFrets: Int,
    leftHanded: Boolean,
): FretPosition? {
    val openWidth = w * OPEN_COL_FRAC
    val nutWidth = w * NUT_FRAC
    val fretAreaWidth = w - openWidth - nutWidth
    val fretSpacing = fretAreaWidth / numFrets
    val stringSpacing = h / stringCount
    val rowFromTop = (p.y / stringSpacing).toInt().coerceIn(0, stringCount - 1)
    val s = stringCount - 1 - rowFromTop
    // Mirror tap x for left-handed so the same hit zones apply on the mirrored layout
    val px = if (leftHanded) w - p.x else p.x
    val f = when {
        px < openWidth -> 0
        px < openWidth + nutWidth -> return null
        else -> {
            val n = ((px - openWidth - nutWidth) / fretSpacing).toInt() + 1
            if (n in 1..numFrets) n else return null
        }
    }
    return FretPosition(s, f)
}

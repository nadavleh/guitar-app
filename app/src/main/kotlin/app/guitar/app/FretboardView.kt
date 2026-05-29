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
import androidx.compose.ui.graphics.PathEffect
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
import kotlin.math.max

enum class MarkKind { Chord, Scale, Pick }

data class FretMark(
    val label: String,
    val isRoot: Boolean = false,
    val kind: MarkKind = MarkKind.Chord,
)

private const val OPEN_COL_FRAC = 0.06f
private const val NUT_FRAC = 0.022f
private const val STRING_DP = 42
private const val FRET_NUMBER_DP = 18   // extra height below for fret-number row

/**
 * Realistic horizontal fretboard.
 *
 * Layout:
 *   • Wood background (dark walnut + grain stripes)
 *   • Nut on the left (or right in left-handed mode)
 *   • 12 frets visible by default
 *   • Bottom 3 strings (low E/A/D) = wound — thicker bronze with a dashed overlay
 *   • Top 3 strings (G/B/high E) = plain — thinner bright steel
 *   • Fret-number row at the bottom
 *   • Dots colored by [MarkKind] + isRoot:
 *       - Root          → crimson (rootTone) with pearl inner ring
 *       - Chord tone    → teal    (chordTone)
 *       - Scale tone    → lavender(scaleTone)
 *       - Pick selection→ amber outline ring (no fill)
 *
 * Tap behaviour: in non-pick modes, tap = play & inspect; in pick mode, tap toggles selection.
 * The selected-position amber ring still appears in all modes for the most recently tapped cell.
 */
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
    // Fixed total height = strings * STRING_DP + fret-number strip. This guarantees
    // the fretboard always renders in a horizontal guitar-neck aspect, regardless of
    // how much vertical space the parent gives us (the spec calls for the fretboard
    // to look horizontal in both portrait and landscape).
    val totalHeightDp = (tuning.stringCount * STRING_DP + FRET_NUMBER_DP).dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp)
            .pointerInput(tuning, numFrets, leftHanded) {
                detectTapGestures { off ->
                    val pos = pixelToPosition(
                        off, size.width.toFloat(), size.height.toFloat(),
                        tuning.stringCount, numFrets, leftHanded
                    )
                    if (pos != null) onTap(pos)
                }
            }
    ) {
        val w = size.width
        val totalH = size.height
        // Reserve the bottom strip for fret numbers
        val numberStripH = totalH * (FRET_NUMBER_DP.toFloat() / (tuning.stringCount * STRING_DP + FRET_NUMBER_DP))
        val h = totalH - numberStripH

        val openWidth = w * OPEN_COL_FRAC
        val nutWidth = w * NUT_FRAC
        val fretAreaWidth = w - openWidth - nutWidth
        val fretSpacing = fretAreaWidth / numFrets
        val stringSpacing = h / tuning.stringCount
        val firstStringY = stringSpacing / 2

        fun mx(x: Float) = if (leftHanded) w - x else x

        // ---------- Wood + grain ----------
        drawRect(color = GuitarColors.wood, size = Size(w, h))
        // Subtle horizontal grain — a few low-alpha streaks at varying y, varying alpha
        val grainColor = GuitarColors.woodGrain
        val grainBands = listOf(
            0.07f to 0.10f, 0.18f to 0.06f, 0.27f to 0.08f, 0.38f to 0.05f,
            0.49f to 0.09f, 0.61f to 0.06f, 0.73f to 0.08f, 0.84f to 0.05f, 0.92f to 0.07f
        )
        for ((yFrac, alpha) in grainBands) {
            drawLine(
                color = grainColor.copy(alpha = alpha),
                start = Offset(0f, h * yFrac),
                end = Offset(w, h * yFrac),
                strokeWidth = 1.2f
            )
        }

        // Open-string band separator (between open column and the nut)
        val openSepX = mx(openWidth)
        drawLine(
            color = GuitarColors.fretWire.copy(alpha = 0.5f),
            start = Offset(openSepX, 0f),
            end = Offset(openSepX, h),
            strokeWidth = 1f
        )

        // ---------- Nut ----------
        val nutLeft = mx(if (leftHanded) openWidth + nutWidth else openWidth)
        drawRect(
            color = GuitarColors.nut,
            topLeft = Offset(nutLeft, 0f),
            size = Size(nutWidth, h)
        )

        // ---------- Fret wires ----------
        for (f in 1..numFrets) {
            val x = mx(openWidth + nutWidth + f * fretSpacing)
            drawLine(
                color = GuitarColors.fretWire,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 2.2f
            )
        }

        // ---------- Inlays (between frets, vertically centered) ----------
        val singleDots = listOf(3, 5, 7, 9, 15, 17, 19, 21)
        val doubleDots = listOf(12, 24)
        val inlayR = max(3f, stringSpacing * 0.12f)
        for (f in singleDots) if (f <= numFrets) {
            val x = mx(openWidth + nutWidth + (f - 0.5f) * fretSpacing)
            drawCircle(GuitarColors.inlay.copy(alpha = 0.6f), radius = inlayR, center = Offset(x, h / 2))
        }
        for (f in doubleDots) if (f <= numFrets) {
            val x = mx(openWidth + nutWidth + (f - 0.5f) * fretSpacing)
            drawCircle(GuitarColors.inlay.copy(alpha = 0.6f), radius = inlayR, center = Offset(x, h * 0.32f))
            drawCircle(GuitarColors.inlay.copy(alpha = 0.6f), radius = inlayR, center = Offset(x, h * 0.68f))
        }

        // ---------- Strings ----------
        // stringIndex 0 = lowest pitch = bottom of the screen
        // Bottom 3 (indices 0,1,2) = wound; top 3 (indices 3,4,5) = plain
        // For n-string instruments other than 6, scale the threshold linearly.
        val woundCutoff = (tuning.stringCount + 1) / 2   // bottom half wound
        val plainHatch = PathEffect.dashPathEffect(floatArrayOf(2.5f, 1.5f), 0f)
        for (s in 0 until tuning.stringCount) {
            val y = firstStringY + (tuning.stringCount - 1 - s) * stringSpacing
            val isWound = s < woundCutoff
            if (isWound) {
                val thickness = 4.0f - (s * 0.5f)            // 4.0, 3.5, 3.0 for s=0,1,2
                // Base bronze line
                drawLine(
                    color = GuitarColors.stringWound,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = thickness
                )
                // Winding hatches (slightly darker bronze, dashed)
                drawLine(
                    color = GuitarColors.stringWound.copy(red = 0.6f, green = 0.45f, blue = 0.25f).copy(alpha = 0.8f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = thickness * 0.85f,
                    pathEffect = plainHatch
                )
                // Highlight (subtle bright stripe just above center)
                drawLine(
                    color = Color(0xFFE9D6A3).copy(alpha = 0.55f),
                    start = Offset(0f, y - thickness * 0.35f),
                    end = Offset(w, y - thickness * 0.35f),
                    strokeWidth = 0.7f
                )
            } else {
                val plainIdx = s - woundCutoff               // 0,1,2 for top three
                val thickness = 2.1f - (plainIdx * 0.3f)     // 2.1, 1.8, 1.5
                drawLine(
                    color = GuitarColors.stringPlain,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = thickness
                )
                // Specular highlight stripe
                drawLine(
                    color = Color(0xFFF3E9CC),
                    start = Offset(0f, y - thickness * 0.3f),
                    end = Offset(w, y - thickness * 0.3f),
                    strokeWidth = 0.6f
                )
            }
        }

        // ---------- Marks ----------
        val dotR = stringSpacing * 0.40f
        val labelSp = (stringSpacing * 0.36f).toSp()
        for ((pos, mark) in marks) {
            if (pos.fret > numFrets) continue
            if (pos.stringIndex >= tuning.stringCount) continue
            val (cx, cy) = positionToPixel(pos, w, h, tuning.stringCount, numFrets, leftHanded)

            when (mark.kind) {
                MarkKind.Pick -> {
                    // Amber outline ring, no fill
                    drawCircle(
                        color = GuitarColors.pickSelect,
                        radius = dotR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3f)
                    )
                }
                else -> {
                    val fillColor = if (mark.isRoot) {
                        GuitarColors.rootTone
                    } else when (mark.kind) {
                        MarkKind.Chord -> GuitarColors.chordTone
                        MarkKind.Scale -> GuitarColors.scaleTone
                        MarkKind.Pick  -> GuitarColors.pickSelect
                    }
                    drawCircle(fillColor, radius = dotR, center = Offset(cx, cy))
                    if (mark.isRoot) {
                        // Pearl inner ring for the root, makes it pop
                        drawCircle(
                            color = GuitarColors.inlay,
                            radius = dotR * 0.78f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5f)
                        )
                    }
                    if (mark.label.isNotEmpty()) {
                        val style = TextStyle(
                            color = GuitarColors.textPrimary,
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
            }
        }

        // ---------- Selected (tap pulse / inspect) ring ----------
        if (selectedPosition != null) {
            val (cx, cy) = positionToPixel(selectedPosition, w, h, tuning.stringCount, numFrets, leftHanded)
            drawCircle(
                color = GuitarColors.primary,
                radius = stringSpacing * 0.48f,
                center = Offset(cx, cy),
                style = Stroke(width = 3f)
            )
        }

        // ---------- Fret-number row (below the wood) ----------
        drawRect(
            color = GuitarColors.background,
            topLeft = Offset(0f, h),
            size = Size(w, numberStripH)
        )
        val numStyle = TextStyle(
            color = GuitarColors.textSecondary,
            fontSize = (numberStripH * 0.55f).toSp(),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        for (f in 1..numFrets) {
            val x = mx(openWidth + nutWidth + (f - 0.5f) * fretSpacing)
            val measured = measurer.measure(text = f.toString(), style = numStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x - measured.size.width / 2f,
                    h + (numberStripH - measured.size.height) / 2f
                )
            )
        }
        // "0" above the open column
        val openX = mx(openWidth / 2f)
        val openLabel = measurer.measure("0", numStyle)
        drawText(
            textLayoutResult = openLabel,
            topLeft = Offset(
                openX - openLabel.size.width / 2f,
                h + (numberStripH - openLabel.size.height) / 2f
            )
        )
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
    w: Float, totalH: Float,
    stringCount: Int,
    numFrets: Int,
    leftHanded: Boolean,
): FretPosition? {
    // Tap below the fretboard area (in the fret-number strip) → ignore.
    val numberStripH = totalH * (FRET_NUMBER_DP.toFloat() / (stringCount * STRING_DP + FRET_NUMBER_DP))
    val h = totalH - numberStripH
    if (p.y > h) return null

    val openWidth = w * OPEN_COL_FRAC
    val nutWidth = w * NUT_FRAC
    val fretAreaWidth = w - openWidth - nutWidth
    val fretSpacing = fretAreaWidth / numFrets
    val stringSpacing = h / stringCount
    val rowFromTop = (p.y / stringSpacing).toInt().coerceIn(0, stringCount - 1)
    val s = stringCount - 1 - rowFromTop
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

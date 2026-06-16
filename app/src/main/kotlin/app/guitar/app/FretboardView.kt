package app.guitar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.Tuning
import kotlin.math.max

enum class MarkKind { Chord, Scale, Pick }

data class FretMark(
    val label: String,
    val isRoot: Boolean = false,
    val kind: MarkKind = MarkKind.Chord,
)

private const val OPEN_COL_FRAC = 0.08f
/** Where, inside the open column, to center the fret-0 chord-tone circles.
 *  0.5 = centered (overlaps the string label on the left); 0.7 = pushed toward
 *  the nut so the label has clear room. */
private const val OPEN_MARK_FRAC = 0.7f
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
 *
 * Aspect ratio: the neck is ALWAYS drawn at a fixed long-horizontal /
 * short-vertical aspect ratio ([neckAspect], from frets × strings), centered in
 * (and letterboxed within) whatever box the caller gives it. The viewport's own
 * shape no longer distorts the neck — a tall portrait box just leaves empty
 * space above/below the short neck; pinch/drag zoom & pan within that frame.
 *
 * Zoom/pan: at scale 1 the whole fretboard fits the viewport. A pinch gesture
 * scales it (uniformly, preserving the aspect ratio) between [minScale]=0.5
 * (whole neck shrinks to half the viewport) and [maxScale]=stringCount/2 (zoom
 * in until ~2 strings fill the height). A pinch zooms around the focal point;
 * a drag pans the zoomed neck, clamped so it can't be pulled off-screen. The
 * transform is purely a render-layer effect (graphicsLayer), so tap hit-testing
 * still uses the Canvas's un-transformed coordinate space — Compose maps pointer
 * coordinates back through the layer for us, and [pixelToPosition] is unchanged.
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
    /** When true, a note fires on touch-DOWN (immediate, but also fires at the
     *  start of a horizontal swipe). When false (default), the note fires only on
     *  a clean tap-release, so swiping the neck to scroll never sounds a note. */
    playOnTouchDown: Boolean = false,
    /** String indices (0 = lowest pitch) marked as muted (X). Drawn as a red ✕ at
     *  the nut, like a chord diagram, and excluded from any strum. */
    mutedStrings: Set<Int> = emptySet(),
) {
    val measurer = rememberTextMeasurer()

    // Zoom/pan state. scale 1 = whole neck fills the viewport. The neck is drawn
    // into a fixed-size Canvas and only the render layer is scaled/translated, so
    // the layout never changes size — the user pinches/drags within a fixed frame.
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val minScale = 0.5f                                   // zoom out: neck → half the viewport
    val maxScale = (tuning.stringCount / 2f).coerceAtLeast(1.5f)  // zoom in: ~2 strings tall

    val tapModifier = Modifier.pointerInput(tuning, numFrets, leftHanded, playOnTouchDown) {
        val handler: (Offset) -> Unit = { off ->
            val pos = pixelToPosition(
                off, size.width.toFloat(), size.height.toFloat(),
                tuning.stringCount, numFrets, leftHanded
            )
            if (pos != null) onTap(pos)
        }
        if (playOnTouchDown) {
            detectTapGestures(onPress = { off -> handler(off) })
        } else {
            // onTap fires only when the gesture stayed within touch-slop (a real
            // tap). A drag is claimed by the transform gesture below and never
            // becomes a tap, so panning the neck won't play anything.
            detectTapGestures(onTap = handler)
        }
    }

    val zoomModifier = Modifier.pointerInput(minScale, maxScale) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            val oldScale = scale
            val newScale = (oldScale * zoom).coerceIn(minScale, maxScale)
            // Focal-point zoom: keep the content under the pinch centroid fixed
            // (then apply the finger pan on top). graphicsLayer's origin is the
            // viewport center c; a content-local point q maps to screen as
            //   c + (q - c)*scale + offset
            // Holding q's screen position across the scale change gives:
            //   newOffset = oldOffset + (q - c)*(oldScale - newScale)
            // The centroid is reported in the same un-transformed local space as
            // taps, so it plays the role of q directly.
            val cx = size.width / 2f
            val cy = size.height / 2f
            scale = newScale
            // graphicsLayer center origin: content can travel ±(size*(scale-1)/2)
            // before an edge reaches the viewport edge. Below scale 1 the neck is
            // smaller than the viewport, so it stays centered (range collapses).
            val maxX = max(0f, size.width * (newScale - 1f) / 2f)
            val maxY = max(0f, size.height * (newScale - 1f) / 2f)
            offsetX = (offsetX + pan.x + (centroid.x - cx) * (oldScale - newScale)).coerceIn(-maxX, maxX)
            offsetY = (offsetY + pan.y + (centroid.y - cy) * (oldScale - newScale)).coerceIn(-maxY, maxY)
        }
    }

    // Fixed neck proportions: long horizontal, short vertical. Width units =
    // per-fret(72) × frets + open column + nut(≈100); height units = strings × 42
    // + the fret-number strip(18). The neck is letterboxed at this ratio inside
    // the viewport so the box shape never stretches it.
    val neckAspect = (numFrets * 72f + 100f) / (tuning.stringCount * STRING_DP + FRET_NUMBER_DP).toFloat()

    Box(modifier = modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .aspectRatio(neckAspect)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .then(zoomModifier)
                .then(tapModifier)
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
        // Dots/labels are sized by the SMALLER of string- and fret-spacing so they
        // stay round and reasonable in any aspect ratio (e.g. a tall portrait neck
        // has huge string spacing — without this cap the dots would balloon).
        val unit = minOf(stringSpacing, fretSpacing)

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
        val inlayR = max(3f, unit * 0.12f)
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
        // stringIndex 0 = lowest pitch = bottom of the screen.
        // For guitar (6-string), the bottom half (E, A, D) are wound bronze and
        // the top half (G, B, e) are plain. For cavaquinho (4-string, much
        // shorter scale + nylon/steel of similar gauge), render every string as
        // plain — no wound bronze.
        val woundCutoff = if (tuning.stringCount == 4) 0    // all plain
                          else (tuning.stringCount + 1) / 2
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
        val dotR = unit * 0.40f
        val labelSp = (unit * 0.36f).toSp()
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

        // ---------- Open-string labels (left of the nut) ----------
        // Note letter for each open string. Convention: uppercase for the lowest-
        // octave occurrence of each letter, lowercase for higher-octave duplicates.
        // Standard tuning reads "E A D G B e".
        // The labels sit at the LEFT EDGE of the open column so they don't overlap
        // the chord-tone / interval circles drawn at fret 0 (which are centered on
        // openWidth/2). Font is small for the same reason.
        val labelStyle = TextStyle(
            color = GuitarColors.primary,
            fontSize = (stringSpacing * 0.32f).toSp(),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
        )
        for (s in 0 until tuning.stringCount) {
            val y = firstStringY + (tuning.stringCount - 1 - s) * stringSpacing
            val pc = tuning.openStrings[s].pitchClass
            val letter = NoteSpeller.spell(pc)
            // Convention: the HIGHEST string always reads lowercase regardless of
            // whether the letter appears elsewhere. So standard tuning is
            // "E A D G B e", DGBe is "D G B e", DGBD is "D G B d".
            val isHighest = s == tuning.stringCount - 1
            val label = if (isHighest) letter.lowercase() else letter
            val measured = measurer.measure(text = label, style = labelStyle)
            // Pin to the leftmost ~3px in left-handed view, or to ~3px from the
            // left edge in right-handed view. Either way, well clear of the
            // open-column center where the fret-0 marks live.
            val labelLeftX = if (leftHanded) w - measured.size.width - 4f else 4f
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    labelLeftX,
                    y - measured.size.height / 2f
                )
            )
        }

        // ---------- Muted-string X (chord-diagram style, over the open column) ----------
        if (mutedStrings.isNotEmpty()) {
            val xStyle = TextStyle(
                color = GuitarColors.rootTone,            // crimson — reads as "don't play"
                fontSize = (stringSpacing * 0.5f).toSp(),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            val mutedX = mx(openWidth * OPEN_MARK_FRAC)    // same column as the fret-0 marks / "0"
            for (s in mutedStrings) {
                if (s < 0 || s >= tuning.stringCount) continue
                val y = firstStringY + (tuning.stringCount - 1 - s) * stringSpacing
                val measured = measurer.measure(text = "✕", style = xStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(mutedX - measured.size.width / 2f, y - measured.size.height / 2f),
                )
            }
        }

        // ---------- Selected (tap pulse / inspect) ring ----------
        if (selectedPosition != null) {
            val (cx, cy) = positionToPixel(selectedPosition, w, h, tuning.stringCount, numFrets, leftHanded)
            drawCircle(
                color = GuitarColors.primary,
                radius = unit * 0.48f,
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
        // "0" below the open column — aligned with the open-mark circles, not the
        // string labels.
        val openX = mx(openWidth * OPEN_MARK_FRAC)
        val openLabel = measurer.measure("0", numStyle)
        drawText(
            textLayoutResult = openLabel,
            topLeft = Offset(
                openX - openLabel.size.width / 2f,
                h + (numberStripH - openLabel.size.height) / 2f
            )
        )
        }  // Canvas
    }      // viewport Box (fixed; clips the zoomed/panned neck)
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
    val cxRight = if (pos.fret == 0) openWidth * OPEN_MARK_FRAC
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

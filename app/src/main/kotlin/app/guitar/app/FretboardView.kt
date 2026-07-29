package app.guitar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.Tuning
import kotlin.math.abs
import kotlin.math.max

enum class MarkKind { Chord, Scale, Pick }

data class FretMark(
    val label: String,
    val isRoot: Boolean = false,
    val kind: MarkKind = MarkKind.Chord,
)

/** Lifetime of a pluck's ripple/glow feedback, seconds (Fretboard v3 concept). */
private const val PLUCK_LIFE_S = 1.4f

/** One pluck-feedback event: where it lit up and when (ms, frame-clock epoch). */
private class Pluck(val pos: FretPosition, val t0: Long)

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
 *   • Dots ranked by [MarkKind] + isRoot (Fretboard v3):
 *       - Root          → crimson (rootTone), larger, with pearl inner ring
 *       - Chord tone    → teal    (chordTone), filled
 *       - Scale tone    → lavender(scaleTone), hollow ring
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
/**
 * Hoistable zoom/pan state for [FretboardView]. Hold one per logical fretboard (e.g.
 * in a screen-level state object) and pass it in, so hiding/showing the view — which
 * removes it from composition — no longer resets the user's zoom. [initializedFor]
 * records the orientation the camera was last framed for; the view re-frames it when
 * the orientation changes (replacing the old `remember(portrait)` reset).
 */
@Stable
class FretboardCamera {
    var initializedFor: Boolean? = null
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
}

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
    /** Optional hoisted zoom/pan state. Pass a caller-owned [FretboardCamera] to keep
     *  the zoom across this view leaving composition (e.g. a show/hide toggle); when
     *  null the camera lives (and dies) with the composable, as before. */
    camera: FretboardCamera? = null,
    /** Play-mode sweep (v2.2): when true, a single-finger drag across the strings
     *  plucks each string it crosses instead of panning (two-finger pinch still
     *  zooms; a clean tap still fires [onTap]). */
    strumMode: Boolean = false,
    /** Resolves what sounds when a sweep crosses [stringIndex]: play it and return
     *  the fret that sounded (drives the ripple), or null for silence (muted). */
    onStrumPluck: (stringIndex: Int) -> Int? = { null },
) {
    val measurer = rememberTextMeasurer()
    // Board art follows the theme: dark walnut (original) vs cream maple (light).
    val board = if (LocalSignal.current.isDark) BoardColors.Dark else BoardColors.Light
    // Pluck feedback color = the live accent ("act").
    val act = MaterialTheme.colorScheme.primary

    // Age-driven pluck feedback (ripple + glow + string shimmer). While any pluck is
    // alive, a frame clock advances `frameNow` — which the draw pass reads, so the
    // Canvas repaints every frame — and dead plucks are pruned. Idle cost is zero:
    // the effect coroutine ends as soon as the list drains.
    val plucks = remember { mutableStateListOf<Pluck>() }
    var frameNow by remember { mutableLongStateOf(0L) }
    var pluckTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(pluckTrigger) {
        while (plucks.isNotEmpty()) {
            withFrameMillis { frameNow = it }
            val cutoff = frameNow - (PLUCK_LIFE_S * 1000).toLong()
            plucks.removeAll { it.t0 < cutoff }
        }
    }
    fun addPluck(pos: FretPosition) {
        plucks.add(Pluck(pos, System.nanoTime() / 1_000_000))
        pluckTrigger++
    }

    val minScale = 0.5f                                   // zoom out: neck → half the viewport
    val maxScale = (tuning.stringCount / 2f).coerceAtLeast(1.5f)  // zoom in: ~2 strings tall

    // Tap-to-play / inspect. Stays on the Canvas so hit-testing uses the neck's
    // un-transformed coordinate space (Compose maps pointer coords back through
    // the graphicsLayer for us). A drag is claimed by the transform gesture and
    // never becomes a tap, so panning the neck won't sound a note.
    val tapModifier = Modifier.pointerInput(tuning, numFrets, leftHanded, playOnTouchDown) {
        val handler: (Offset) -> Unit = { off ->
            val pos = pixelToPosition(
                off, size.width.toFloat(), size.height.toFloat(),
                tuning.stringCount, numFrets, leftHanded
            )
            if (pos != null) {
                onTap(pos)
                addPluck(pos)
            }
        }
        if (playOnTouchDown) {
            // Fire on the DOWN itself — awaitFirstDown is reliable for "play on
            // touch-down"; detectTapGestures(onPress=…) could defer/cancel.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                handler(down.position)
            }
        } else {
            detectTapGestures(onTap = handler)
        }
    }

    // Play ("strum") mode gesture: replaces tapModifier on the Canvas. A sweep
    // plucks every string the pointer crosses; a clean tap (within touch slop)
    // still fires onTap so grips stay editable. Consuming the sweep's changes
    // keeps the outer pan/zoom from also dragging the neck; a second finger
    // bails out so pinch-zoom keeps working. Lives on the Canvas so positions
    // arrive in neck-local space (Compose maps them back through graphicsLayer).
    val strumModifier = Modifier.pointerInput(tuning, numFrets, leftHanded, strumMode) {
        if (!strumMode) return@pointerInput
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown()
            val totalH = size.height.toFloat()
            val stripH = totalH * (FRET_NUMBER_DP.toFloat() / (tuning.stringCount * STRING_DP + FRET_NUMBER_DP))
            val h = totalH - stripH
            val stringSpacing = h / tuning.stringCount
            val firstStringY = stringSpacing / 2
            fun stringY(s: Int) = firstStringY + (tuning.stringCount - 1 - s) * stringSpacing
            var prevY = down.position.y
            var moved = 0f
            var strummed = false
            var aborted = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) { aborted = true; break }   // pinch → zoom gesture's turn
                if (pressed.isEmpty()) break                        // released
                val ch = pressed[0]
                val y = ch.position.y
                moved += abs(ch.position.x - ch.previousPosition.x) + abs(y - ch.previousPosition.y)
                if (moved > slop) {
                    for (s in 0 until tuning.stringCount) {
                        val ys = stringY(s)
                        if ((prevY - ys) * (y - ys) < 0f) {          // crossed this string
                            strummed = true
                            val fret = onStrumPluck(s)
                            if (fret != null) addPluck(FretPosition(s, fret))
                        }
                    }
                    ch.consume()
                }
                prevY = y
            }
            if (!aborted && !strummed && moved <= slop) {
                val pos = pixelToPosition(
                    down.position, size.width.toFloat(), totalH,
                    tuning.stringCount, numFrets, leftHanded,
                )
                if (pos != null) onTap(pos)
            }
        }
    }

    // Fixed neck proportions: long horizontal, short vertical. Width units =
    // per-fret(72) × frets + open column + nut(≈100); height units = strings × 42
    // + the fret-number strip(18). The neck is letterboxed at this ratio inside
    // the viewport so the box shape never stretches it.
    val neckAspect = (numFrets * 72f + 100f) / (tuning.stringCount * STRING_DP + FRET_NUMBER_DP).toFloat()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val boxWpx = with(density) { maxWidth.toPx() }
        val boxHpx = with(density) { maxHeight.toPx() }
        // The neck Canvas is letterboxed to neckAspect inside the box; compute its
        // actual pixel size so pan-clamping uses the NECK's extent, not the box's.
        val canvasWpx: Float
        val canvasHpx: Float
        if (boxWpx / boxHpx > neckAspect) {
            canvasHpx = boxHpx; canvasWpx = boxHpx * neckAspect   // height-constrained (landscape)
        } else {
            canvasWpx = boxWpx; canvasHpx = boxWpx / neckAspect   // width-constrained (portrait)
        }
        // Portrait = taller than wide. There the wide neck letterboxes down to a
        // thin sliver, so start zoomed in on the first frets (task #6) rather than
        // tiny. offsetX = +maxX left-aligns it (nut + low frets in view).
        val portrait = boxHpx > boxWpx
        val initialScale = if (portrait) minOf(maxScale, 2.2f) else 1f

        // Zoom/pan state — hoistable via [camera] so callers can keep it across
        // hide/show. Re-framed when the orientation flips (or on first use), which
        // replaces the old `remember(portrait)` keying.
        val cam = camera ?: remember { FretboardCamera() }
        if (cam.initializedFor != portrait) {
            cam.scale = initialScale
            cam.offsetX = if (portrait) canvasWpx * (initialScale - 1f) / 2f else 0f
            cam.offsetY = 0f
            cam.initializedFor = portrait
        }

        // Pinch/drag over the WHOLE allotted area — including the empty letterbox
        // margins above/below the short neck — so the user need not pinch precisely
        // on the thin neck (task #6). This Box fills the area and is the gesture
        // parent of the centered Canvas; graphicsLayer's origin is the box center,
        // which coincides with the centered neck's center, so the focal math and
        // pixel translations line up even though the gesture sees the larger box.
        val zoomModifier = Modifier.pointerInput(minScale, maxScale, canvasWpx, canvasHpx) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val oldScale = cam.scale
                val newScale = (oldScale * zoom).coerceIn(minScale, maxScale)
                val cx = size.width / 2f
                val cy = size.height / 2f
                cam.scale = newScale
                val maxX = max(0f, canvasWpx * (newScale - 1f) / 2f)
                val maxY = max(0f, canvasHpx * (newScale - 1f) / 2f)
                cam.offsetX = (cam.offsetX + pan.x + (centroid.x - cx) * (oldScale - newScale)).coerceIn(-maxX, maxX)
                cam.offsetY = (cam.offsetY + pan.y + (centroid.y - cy) * (oldScale - newScale)).coerceIn(-maxY, maxY)
            }
        }

      Box(
        modifier = Modifier.matchParentSize().then(zoomModifier),
        contentAlignment = Alignment.Center,
      ) {
        Canvas(
            modifier = Modifier
                .aspectRatio(neckAspect)
                .graphicsLayer {
                    scaleX = cam.scale
                    scaleY = cam.scale
                    translationX = cam.offsetX
                    translationY = cam.offsetY
                }
                .then(if (strumMode) strumModifier else tapModifier)
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

        // ---------- Wood + grain (v3: vertical light gradient — material, not flat fill) ----------
        drawRect(
            brush = Brush.verticalGradient(
                0f to board.woodA, 0.5f to board.woodB, 1f to board.woodA,
                startY = 0f, endY = h,
            ),
            size = Size(w, h),
        )
        // Subtle horizontal grain — a few low-alpha streaks at varying y, varying alpha
        val grainColor = board.woodGrain
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
            color = board.fretWire.copy(alpha = 0.5f),
            start = Offset(openSepX, 0f),
            end = Offset(openSepX, h),
            strokeWidth = 1f
        )

        // ---------- Nut ----------
        val nutLeft = mx(if (leftHanded) openWidth + nutWidth else openWidth)
        drawRect(
            color = board.nut,
            topLeft = Offset(nutLeft, 0f),
            size = Size(nutWidth, h)
        )

        // ---------- Fret wires (v3: two-tone metal — dark body, bright leading edge) ----------
        for (f in 1..numFrets) {
            val x = mx(openWidth + nutWidth + f * fretSpacing)
            drawRect(board.fretWireDark, topLeft = Offset(x - 1.6f, 0f), size = Size(3.2f, h))
            drawRect(board.fretWire, topLeft = Offset(x - 1.6f, 0f), size = Size(1.4f, h))
        }

        // ---------- Inlays (between frets, vertically centered) ----------
        val singleDots = listOf(3, 5, 7, 9, 15, 17, 19, 21)
        val doubleDots = listOf(12, 24)
        val inlayR = max(3f, unit * 0.12f)
        for (f in singleDots) if (f <= numFrets) {
            val x = mx(openWidth + nutWidth + (f - 0.5f) * fretSpacing)
            drawCircle(board.inlay.copy(alpha = 0.6f), radius = inlayR, center = Offset(x, h / 2))
        }
        for (f in doubleDots) if (f <= numFrets) {
            val x = mx(openWidth + nutWidth + (f - 0.5f) * fretSpacing)
            drawCircle(board.inlay.copy(alpha = 0.6f), radius = inlayR, center = Offset(x, h * 0.32f))
            drawCircle(board.inlay.copy(alpha = 0.6f), radius = inlayR, center = Offset(x, h * 0.68f))
        }

        // ---------- Fret numbers (marker frets, bottom edge) ----------
        // Position is hard to tell in the zoomed portrait view, so number the
        // marker frets in the strip below the lowest string.
        val fretNumStyle = TextStyle(
            color = board.inlay.copy(alpha = 0.85f),
            fontSize = (stringSpacing * 0.28f).toSp(),
            fontWeight = FontWeight.SemiBold,
        )
        for (f in singleDots + doubleDots) if (f <= numFrets) {
            val x = mx(openWidth + nutWidth + (f - 0.5f) * fretSpacing)
            val measured = measurer.measure(text = "$f", style = fretNumStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(x - measured.size.width / 2f, h - measured.size.height - 1f),
            )
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
                    color = board.stringWound,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = thickness
                )
                // Winding hatches (slightly darker bronze, dashed)
                drawLine(
                    color = board.stringWound.copy(red = 0.6f, green = 0.45f, blue = 0.25f).copy(alpha = 0.8f),
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
                    color = board.stringPlain,
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

        // ---------- Marks (v3 ranking: roots larger w/ pearl ring; chord tones filled;
        // scale tones hollow rings so the hierarchy reads at a glance) ----------
        val dotR = unit * 0.40f
        val rootR = unit * 0.46f
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
                    val hollow = mark.kind == MarkKind.Scale && !mark.isRoot
                    val r = if (mark.isRoot) rootR else dotR
                    if (hollow) {
                        // Hollow scale tone: translucent knock-back + colored ring.
                        drawCircle(board.scaleFill, radius = r - 1.2f, center = Offset(cx, cy))
                        drawCircle(
                            color = GuitarColors.scaleTone,
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.4f)
                        )
                    } else {
                        val fillColor = if (mark.isRoot) GuitarColors.rootTone else GuitarColors.chordTone
                        drawCircle(fillColor, radius = r, center = Offset(cx, cy))
                        if (mark.isRoot) {
                            // Pearl inner ring for the root, makes it pop
                            drawCircle(
                                color = board.inlay,
                                radius = r * 0.78f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.5f)
                            )
                        }
                    }
                    if (mark.label.isNotEmpty()) {
                        val style = TextStyle(
                            color = if (hollow) GuitarColors.scaleTone else GuitarColors.textPrimary,
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
            // Larger + bolder so the per-string note letters stay legible in the
            // zoomed-in portrait (vertical phone) view.
            fontSize = (stringSpacing * 0.42f).toSp(),
            fontWeight = FontWeight.Bold,
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

        // ---------- Pluck ripples + glow + string shimmer (age-driven, v3) ----------
        // Reading frameNow here re-executes this draw block every frame while any
        // pluck is alive (see the LaunchedEffect above); zero cost when idle.
        val nowMs = frameNow
        for (p in plucks) {
            val age = (nowMs - p.t0) / 1000f
            if (age < 0f || age > PLUCK_LIFE_S) continue
            if (p.pos.fret > numFrets || p.pos.stringIndex >= tuning.stringCount) continue
            val (px, py) = positionToPixel(p.pos, w, h, tuning.stringCount, numFrets, leftHanded)
            val c = Offset(px, py)
            // expanding ripple ring
            drawCircle(
                color = act.copy(alpha = (0.5f - age * 0.45f).coerceAtLeast(0f)),
                radius = unit * (0.5f + age * 1.6f),
                center = c,
                style = Stroke(width = 2f),
            )
            // glow bloom that decays like the ring-out
            drawCircle(
                brush = Brush.radialGradient(listOf(act, Color.Transparent), center = c, radius = unit * 0.95f),
                radius = unit * 0.95f,
                center = c,
                alpha = (0.55f - age * 0.4f).coerceAtLeast(0f),
            )
            // shimmer traveling outward along the string
            val spread = unit * (2f + age * 8f)
            drawLine(
                color = act.copy(alpha = (0.5f - age * 0.5f).coerceAtLeast(0f)),
                start = Offset(px - spread, py),
                end = Offset(px + spread, py),
                strokeWidth = 2.2f,
            )
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
      }    // gesture Box (full area; catches pinch/drag over the letterbox too)
    }      // viewport BoxWithConstraints (fixed; clips the zoomed/panned neck)
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

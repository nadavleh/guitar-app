package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.QueueMusic
import app.guitar.theory.Instrument
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Custom nav glyphs (task: "ear training symbol is a speaker… I need a small ear;
 * drum machine should be a drum; fretboard needs a more resembling symbol").
 * Stroke-drawn 24×24 [ImageVector]s in the Material-outlined weight; `Icon`'s tint
 * paints over the path color, so they recolor like any stock icon.
 */
object ShellIcons {
    private fun outlined(name: String, build: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply(build).build()

    private val stroke = SolidColor(Color.Black)

    /** A small ear: outer helix, lobe, and an inner-canal curve. */
    val Ear: ImageVector by lazy {
        outlined("ShellEar") {
            path(stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Outer helix down to the lobe.
                moveTo(17.5f, 10f)
                curveTo(17.5f, 6.4f, 15f, 4f, 12f, 4f)
                curveTo(9f, 4f, 6.5f, 6.4f, 6.5f, 10f)
                curveTo(6.5f, 12.2f, 7.6f, 13.2f, 8.4f, 14.6f)
                curveTo(9.1f, 15.8f, 9.2f, 17.3f, 10.3f, 18.4f)
                curveTo(11.6f, 19.7f, 13.9f, 19.6f, 15f, 18.2f)
            }
            path(stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Inner curve (antihelix + canal).
                moveTo(14.5f, 10f)
                curveTo(14.5f, 8.1f, 13.4f, 7f, 12f, 7f)
                curveTo(10.6f, 7f, 9.5f, 8.1f, 9.5f, 10f)
                curveTo(9.5f, 11.4f, 10.5f, 11.9f, 11.2f, 13f)
            }
        }
    }

    /** A drum: elliptical head, tapered shell, and two crossed sticks above. */
    val Drum: ImageVector by lazy {
        outlined("ShellDrum") {
            path(stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Head (ellipse) — two arcs.
                moveTo(4.5f, 11.5f)
                curveTo(4.5f, 9.8f, 7.9f, 8.5f, 12f, 8.5f)
                curveTo(16.1f, 8.5f, 19.5f, 9.8f, 19.5f, 11.5f)
                curveTo(19.5f, 13.2f, 16.1f, 14.5f, 12f, 14.5f)
                curveTo(7.9f, 14.5f, 4.5f, 13.2f, 4.5f, 11.5f)
                close()
            }
            path(stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Shell sides + bottom curve.
                moveTo(4.5f, 11.5f)
                lineTo(4.5f, 16.5f)
                curveTo(4.5f, 18.2f, 7.9f, 19.5f, 12f, 19.5f)
                curveTo(16.1f, 19.5f, 19.5f, 18.2f, 19.5f, 16.5f)
                lineTo(19.5f, 11.5f)
            }
            path(stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Crossed sticks.
                moveTo(8.5f, 10.5f); lineTo(16.5f, 4.5f)
                moveTo(15.5f, 10.5f); lineTo(7.5f, 4.5f)
            }
        }
    }

    /** A fretboard: nut, two frets, three strings, and two inlay dots. */
    val Fretboard: ImageVector by lazy {
        outlined("ShellFretboard") {
            path(stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Board outline.
                moveTo(3.5f, 6.5f)
                lineTo(20.5f, 6.5f)
                lineTo(20.5f, 17.5f)
                lineTo(3.5f, 17.5f)
                close()
                // Nut (thicker feel: doubled at the left edge).
                moveTo(6f, 6.5f); lineTo(6f, 17.5f)
                // Frets.
                moveTo(11f, 6.5f); lineTo(11f, 17.5f)
                moveTo(16f, 6.5f); lineTo(16f, 17.5f)
            }
            path(stroke = stroke, strokeLineWidth = 1.2f, strokeLineCap = StrokeCap.Round) {
                // Strings.
                moveTo(3.5f, 9.5f); lineTo(20.5f, 9.5f)
                moveTo(3.5f, 12f); lineTo(20.5f, 12f)
                moveTo(3.5f, 14.5f); lineTo(20.5f, 14.5f)
            }
            path(fill = stroke) {
                // Inlay dots (filled).
                moveTo(8.5f, 12f)
                curveTo(8.5f, 11.4f, 9f, 10.9f, 9.6f, 10.9f)
                curveTo(10.2f, 10.9f, 10.7f, 11.4f, 10.7f, 12f)
                curveTo(10.7f, 12.6f, 10.2f, 13.1f, 9.6f, 13.1f)
                curveTo(9f, 13.1f, 8.5f, 12.6f, 8.5f, 12f)
                close()
                moveTo(13.5f, 12f)
                curveTo(13.5f, 11.4f, 14f, 10.9f, 14.6f, 10.9f)
                curveTo(15.2f, 10.9f, 15.7f, 11.4f, 15.7f, 12f)
                curveTo(15.7f, 12.6f, 15.2f, 13.1f, 14.6f, 13.1f)
                curveTo(14f, 13.1f, 13.5f, 12.6f, 13.5f, 12f)
                close()
            }
        }
    }

    /** A beamed sixteenth-note group ("1 e a"): three noteheads + stems under a
     *  double beam — the Rhythm-units tab glyph. */
    val RhythmNotes: ImageVector by lazy {
        outlined("ShellRhythmNotes") {
            path(stroke = stroke, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
                // Stems.
                moveTo(7.8f, 17f); lineTo(7.8f, 6f)
                moveTo(13f, 17f); lineTo(13f, 6f)
                moveTo(18.2f, 17f); lineTo(18.2f, 6f)
            }
            path(stroke = stroke, strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round) {
                // Double beam.
                moveTo(7.4f, 6.4f); lineTo(18.6f, 6.4f)
                moveTo(7.4f, 9.4f); lineTo(18.6f, 9.4f)
            }
            path(fill = stroke) {
                // Three noteheads (filled circles, r≈2.3).
                moveTo(3.8f, 17.2f)
                curveTo(3.8f, 15.93f, 4.83f, 14.9f, 6.1f, 14.9f)
                curveTo(7.37f, 14.9f, 8.4f, 15.93f, 8.4f, 17.2f)
                curveTo(8.4f, 18.47f, 7.37f, 19.5f, 6.1f, 19.5f)
                curveTo(4.83f, 19.5f, 3.8f, 18.47f, 3.8f, 17.2f)
                close()
                moveTo(9.0f, 17.2f)
                curveTo(9.0f, 15.93f, 10.03f, 14.9f, 11.3f, 14.9f)
                curveTo(12.57f, 14.9f, 13.6f, 15.93f, 13.6f, 17.2f)
                curveTo(13.6f, 18.47f, 12.57f, 19.5f, 11.3f, 19.5f)
                curveTo(10.03f, 19.5f, 9.0f, 18.47f, 9.0f, 17.2f)
                close()
                moveTo(14.2f, 17.2f)
                curveTo(14.2f, 15.93f, 15.23f, 14.9f, 16.5f, 14.9f)
                curveTo(17.77f, 14.9f, 18.8f, 15.93f, 18.8f, 17.2f)
                curveTo(18.8f, 18.47f, 17.77f, 19.5f, 16.5f, 19.5f)
                curveTo(15.23f, 19.5f, 14.2f, 18.47f, 14.2f, 17.2f)
                close()
            }
        }
    }

    /** A clock face + two hands — the Metronome tab glyph. */
    val Clock: ImageVector by lazy {
        outlined("ShellClock") {
            path(stroke = stroke, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                // Face.
                moveTo(4f, 12f)
                curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                close()
                // Hands.
                moveTo(12f, 12f); lineTo(12f, 7f)
                moveTo(12f, 12f); lineTo(15.5f, 13.5f)
            }
        }
    }
}

/**
 * Signal bottom-tab shell (M3): 4 user-configurable tabs + a fixed "More" item,
 * replacing the milestone-1 [NavRail] (deleted with this task; its file
 * `AppShell.kt` is removed — everything it did lives here now).
 *
 * Portrait renders [SignalTabBar] as a bottom bar; landscape renders
 * [SignalTabRail], a compact left rail with the same 5 items — both share the
 * single [TabBarItem] composable so the two variants can never drift visually.
 * Chrome only: routing still goes through [AppState.openSheet]/[Sheet] exactly
 * as before.
 */

/** One user-configurable tab destination. Maps onto the existing [Sheet] enum —
 *  navigation logic (openSheet/closeSheet/currentSheet) is untouched; this is
 *  purely a chrome-layer view over it. */
enum class TabDest(val sheet: Sheet, val label: String, val icon: ImageVector) {
    // NOTE: enum NAMES are persisted in the tab_order pref — never rename them;
    // only the display labels are user-facing.
    Neck(Sheet.Fretboard, "Fretboard", ShellIcons.Fretboard),
    Ear(Sheet.EarTraining, "Ear", ShellIcons.Ear),
    Rhythm(Sheet.SambaLooper, "DrumLoop", ShellIcons.Drum),
    Loop(Sheet.Loop, "Loop", Icons.Outlined.Repeat),
    Tuner(Sheet.Tuner, "Tuner", Icons.Outlined.Speed),
    Decompose(Sheet.Decompose, "Decompose", Icons.Outlined.Extension),
    RhythmUnits(Sheet.RhythmUnits, "Rhythm", ShellIcons.RhythmNotes),
    Metronome(Sheet.Metronome, "Metronome", ShellIcons.Clock),
    // Cavaquinho-only (filtered in the tab editor + More by instrument).
    CavaqProgressions(Sheet.CavaqProgressions, "Progressions", Icons.Outlined.QueueMusic),
}

/** Destinations that only make sense for a specific instrument — hidden from the tab
 *  editor and the More overlay unless that instrument is active. */
fun TabDest.availableFor(state: AppState): Boolean = when (this) {
    TabDest.CavaqProgressions -> state.instrument == Instrument.Cavaquinho
    else -> true
}

/** Default tab set/order for a fresh install — matches [TuningRepository]'s
 *  persisted default ("Neck,Ear,Rhythm,Tuner") so a never-configured install and
 *  a freshly-reset one look identical. Loop lives in More by default. */
val DEFAULT_TAB_ORDER: List<TabDest> = listOf(TabDest.Neck, TabDest.Ear, TabDest.Rhythm, TabDest.Tuner)

/** One tab is "selected" when it's the open sheet; the bare Fretboard screen
 *  (currentSheet == null) counts as the Neck tab being selected — whether an
 *  overlay (chord/scale/strum) is lit on the neck or not, since Neck is the
 *  app's implicit home screen (fresh launch included). */
private fun isTabSelected(state: AppState, dest: TabDest): Boolean {
    val sheet = state.currentSheet
    return if (sheet != null) sheet == dest.sheet
    else dest.sheet == Sheet.Fretboard
}

/** "More" is selected whenever the open sheet isn't one of the current 4 tabs
 *  (e.g. Tuner/Decompose when not tabbed, or Options/Settings). */
private fun isMoreSelected(state: AppState): Boolean {
    val sheet = state.currentSheet ?: return false
    return state.tabOrder.none { it.sheet == sheet }
}

/** Bottom tab bar (portrait). Self-contained: draws its own top hairline and
 *  background so callers just place it at the bottom of the layout. */
@Composable
fun SignalTabBar(state: AppState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .background(MaterialTheme.colorScheme.background),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.tabOrder.forEach { dest ->
                TabBarItem(
                    icon = dest.icon,
                    label = dest.label,
                    selected = isTabSelected(state, dest),
                    onClick = { state.openSheet(dest.sheet) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            TabBarItem(
                icon = Icons.Outlined.MoreHoriz,
                label = "More",
                selected = isMoreSelected(state),
                onClick = { state.openMore() },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** Compact left rail (landscape) — the same 5 items as [SignalTabBar], just laid
 *  out as a column instead of a row. Self-contained: draws its own trailing
 *  hairline so callers just place it at the start of the layout. */
@Composable
fun SignalTabRail(state: AppState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            state.tabOrder.forEach { dest ->
                TabBarItem(
                    icon = dest.icon,
                    label = dest.label,
                    selected = isTabSelected(state, dest),
                    onClick = { state.openSheet(dest.sheet) },
                    modifier = Modifier.width(56.dp),
                )
            }
            TabBarItem(
                icon = Icons.Outlined.MoreHoriz,
                label = "More",
                selected = isMoreSelected(state),
                onClick = { state.openMore() },
                modifier = Modifier.width(56.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxHeight().width(1.dp),
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Single tab item shared by both [SignalTabBar] and [SignalTabRail]: a 22dp
 *  icon over a 10sp label. Selected = act color (MaterialTheme.primary) + bold
 *  label; unselected = muted (onSurfaceVariant). State is never color-only —
 *  the label and its weight change too. */
@Composable
private fun TabBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        Text(
            label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** One-line description shown under each destination's title in [MoreScreen]. */
private fun destSubtitle(dest: TabDest): String = when (dest) {
    TabDest.Neck -> "Fretboard — chords, scales & pick mode"
    TabDest.Ear -> "Progression, interval & chord ear training"
    TabDest.Rhythm -> "Samba percussion drum-machine looper"
    TabDest.Loop -> "Chord-progression looper"
    TabDest.CavaqProgressions -> "Cavaquinho functional sequences — looper + neck"
    TabDest.Tuner -> "Chromatic tuner with cents needle"
    TabDest.Decompose -> "Chord-tone breakdown reference"
    TabDest.RhythmUnits -> "Learn & train basic rhythmic units"
    TabDest.Metronome -> "Click track with selectable time signatures"
}

/**
 * "More" screen: every [TabDest] not currently in [AppState.tabOrder], plus the
 * two fixed rows Challenge Stats and Settings. Hosted in a [androidx.compose.material3.ModalBottomSheet]
 * from [App] (see MainActivity.kt) — [AppState.moreOpen] is the transient,
 * unpersisted flag that controls whether it's showing.
 */
@Composable
fun MoreScreen(state: AppState) {
    val extra = remember(state.tabOrder, state.instrument) {
        TabDest.entries.filter { it !in state.tabOrder && it.availableFor(state) }
    }
    var statsOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "MORE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        extra.forEach { dest ->
            MoreRow(
                icon = dest.icon,
                title = dest.label,
                sub = destSubtitle(dest),
                onClick = { state.openSheet(dest.sheet); state.closeMore() },
            )
        }
        MoreRow(
            icon = Icons.Outlined.BarChart,
            title = "Challenge stats",
            sub = "Best scores across every ear-training challenge",
            onClick = { statsOpen = true },
        )
        MoreRow(
            icon = Icons.Outlined.Settings,
            title = "Settings",
            sub = "Theme, accent, tabs & order, tuning, instrument",
            onClick = { state.openSheet(Sheet.Options); state.closeMore() },
        )
    }

    if (statsOpen) EarStatsDialog(state, onDismiss = { statsOpen = false })
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp))
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

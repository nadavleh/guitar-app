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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Neck(Sheet.Fretboard, "Neck", Icons.Outlined.GridView),
    Ear(Sheet.EarTraining, "Ear", Icons.Outlined.Hearing),
    Rhythm(Sheet.SambaLooper, "Rhythm", Icons.Outlined.GraphicEq),
    Loop(Sheet.Loop, "Loop", Icons.Outlined.Repeat),
    Tuner(Sheet.Tuner, "Tuner", Icons.Outlined.Speed),
    Decompose(Sheet.Decompose, "Decompose", Icons.Outlined.Extension),
}

/** Default tab set/order for a fresh install — matches [TuningRepository]'s
 *  persisted default ("Neck,Ear,Rhythm,Loop") so a never-configured install and
 *  a freshly-reset one look identical. */
val DEFAULT_TAB_ORDER: List<TabDest> = listOf(TabDest.Neck, TabDest.Ear, TabDest.Rhythm, TabDest.Loop)

/** One tab is "selected" when it's the open sheet; the bare Fretboard screen
 *  (currentSheet == null but the neck is lit) counts as the Neck tab being
 *  selected, mirroring the milestone-1 NavRail's behavior. */
private fun isTabSelected(state: AppState, dest: TabDest): Boolean {
    val sheet = state.currentSheet
    return if (sheet != null) sheet == dest.sheet
    else dest.sheet == Sheet.Fretboard && state.displayMode != DisplayMode.None
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
    TabDest.Tuner -> "Chromatic tuner with cents needle"
    TabDest.Decompose -> "Chord-tone breakdown reference"
}

/**
 * "More" screen: every [TabDest] not currently in [AppState.tabOrder], plus the
 * two fixed rows Challenge Stats and Settings. Hosted in a [androidx.compose.material3.ModalBottomSheet]
 * from [App] (see MainActivity.kt) — [AppState.moreOpen] is the transient,
 * unpersisted flag that controls whether it's showing.
 */
@Composable
fun MoreScreen(state: AppState) {
    val extra = remember(state.tabOrder) { TabDest.entries.filter { it !in state.tabOrder } }
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

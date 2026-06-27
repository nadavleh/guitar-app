package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Concept-A navigation rail (landscape). A slim, always-visible column of tool
 * icons down the left edge. Tapping an item routes to that tool — Chord / Scale
 * / Pick / Options open as bottom sheets over the neck (unchanged), while Loop /
 * Ear / Drums / Tuner take over the content area to the right of the rail.
 *
 * This is milestone 1 of the redesign: the persistent-navigation backbone. The
 * control dock that replaces the modal sheets lands in a later milestone.
 */

private data class RailItem(val sheet: Sheet, val icon: ImageVector, val label: String)

// White outlined (line) icons — no emoji. Tuner uses a gauge/needle (Speed) to
// echo the tuner's dial; Decompose uses the puzzle-piece outline (Extension).
private val RAIL_ITEMS = listOf(
    RailItem(Sheet.Fretboard, Icons.Outlined.GridView, "Fretboard"),
    RailItem(Sheet.Loop, Icons.Outlined.Loop, "Loop"),
    RailItem(Sheet.EarTraining, Icons.Outlined.Hearing, "Ear Training"),
    RailItem(Sheet.Decompose, Icons.Outlined.Extension, "Decompose"),
    RailItem(Sheet.SambaLooper, Icons.Outlined.GraphicEq, "Drums"),
    RailItem(Sheet.Tuner, Icons.Outlined.Speed, "Tuner"),
    RailItem(Sheet.Options, Icons.Outlined.Settings, "Options"),
)

private fun isRailActive(state: AppState, sheet: Sheet): Boolean =
    if (state.currentSheet != null) state.currentSheet == sheet
    // On the bare fretboard screen, the Fretboard tool is the active context.
    else sheet == Sheet.Fretboard && state.displayMode != DisplayMode.None

@Composable
fun NavRail(state: AppState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(58.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        RAIL_ITEMS.forEach { item ->
            RailButton(
                icon = item.icon,
                label = item.label,
                active = isRailActive(state, item.sheet),
                onClick = { state.openSheet(item.sheet) },
            )
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val fg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer
             else androidx.compose.ui.graphics.Color.Transparent
    Column(
        modifier = Modifier
            .width(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = fg,
            modifier = Modifier.size(22.dp))
        Text(
            label,
            color = fg,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

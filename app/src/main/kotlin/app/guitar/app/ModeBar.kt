package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One button in the bottom action bar. Tapping it opens the corresponding [Sheet]
 * and, for Chord/Scale/Pick, also sets the fretboard's displayMode so the
 * selection is reflected immediately.
 *
 * Active state = the sheet for this item is currently open.
 */
@Composable
fun ActionBarItem(label: String, glyph: String, sheet: Sheet, state: AppState) {
    val isActive = state.currentSheet == sheet
    // Indicate the fretboard's *current* display mode by tinting the matching button
    // even when no sheet is open. Options has no fretboard-display counterpart.
    val isReflected = when (sheet) {
        Sheet.Chord -> state.displayMode == DisplayMode.Chord
        Sheet.Scale -> state.displayMode == DisplayMode.Scale
        Sheet.Pick -> state.displayMode == DisplayMode.Pick
        Sheet.Options -> false
        Sheet.Loop -> false
        Sheet.Tuner -> false
        Sheet.EarTraining -> false
        Sheet.SambaLooper -> false
    }
    val fg = when {
        isActive -> MaterialTheme.colorScheme.primary
        isReflected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pillBg = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { state.openSheet(sheet) }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(pillBg)
                .size(width = 44.dp, height = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = fg, fontSize = 20.sp)
        }
        Text(
            label,
            color = fg,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

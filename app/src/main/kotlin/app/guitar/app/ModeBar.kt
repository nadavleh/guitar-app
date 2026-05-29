package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModeBar(active: Mode, onChange: (Mode) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        for (m in Mode.entries) {
            ModeBarItem(
                label = m.name,
                glyph = m.glyph(),
                selected = m == active,
                onClick = { onChange(m) },
            )
        }
    }
}

private fun Mode.glyph(): String = when (this) {
    Mode.Tuning -> "≡"
    Mode.Chord  -> "♪"
    Mode.Scale  -> "♫"
    Mode.Pick   -> "✋"
}

@Composable
private fun ModeBarItem(
    label: String,
    glyph: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val pillBg = if (selected) MaterialTheme.colorScheme.primaryContainer
                 else androidx.compose.ui.graphics.Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(pillBg)
                .size(width = 44.dp, height = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = fg, fontSize = 22.sp)
        }
        Text(
            label,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Compact, app-wide quick-access control for the two global audio knobs:
 * strum spread (ms between consecutive chord notes) and ring sustain (how long
 * a plucked note rings). Rendered as a small "🎚 Audio" button that opens a
 * dropdown with two sliders. Both write through the persisting setters so the
 * change sticks across the whole app and survives restarts.
 *
 * Drop one of these into every screen's header so the user can tweak feel
 * without diving into Options.
 */
@Composable
fun AudioQuickButton(state: AppState, compact: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { open = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 10.dp, vertical = 6.dp,
            ),
        ) {
            Text(
                if (compact) "🎚" else "🎚 Audio",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            AudioQuickSliders(state, modifier = Modifier.width(280.dp).padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

/** The two sliders, reusable inline (e.g. inside Options) or in the dropdown. */
@Composable
fun AudioQuickSliders(state: AppState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("Audio feel", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            if (state.strumMs == 0) "Strum spread: struck at once"
            else "Strum spread: ${state.strumMs} ms",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = state.strumMs.toFloat(),
            onValueChange = { state.setStrumMs(it.toInt()) },
            valueRange = 0f..150f,
        )
        Text("Ring sustain: ${"%.1f".format(state.ringSustainMs / 1000f)} s",
            style = MaterialTheme.typography.bodySmall)
        Slider(
            value = state.ringSustainMs.toFloat(),
            onValueChange = { state.setRingSustainMs(it.toInt()) },
            valueRange = 300f..4000f,
        )
    }
}

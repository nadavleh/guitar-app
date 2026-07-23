package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Standalone Metronome screen: Play/Stop + BPM (slider & tap tempo) + a selectable
 * time signature, with a row of beat dots that light on each click (the "1"
 * accented, using the higher wood click). Mirror of chorect-web's MetronomeUI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomeScreen(state: AppState, onBack: () -> Unit) {
    val m = state.metronome
    // Stop when navigating away, survive rotation (same guard as other loop screens).
    DisposableEffect(Unit) { onDispose { if (state.currentSheet != Sheet.Metronome) m.stop() } }

    val teal = LocalSignal.current.feedback

    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState()),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("METRONOME", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { m.stop(); onBack() }) { Text("Back") }
        }
        Spacer(Modifier.height(12.dp))

        // Beat dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(34.dp),
        ) {
            for (b in 0 until m.beatsPerBar) {
                val on = b == m.currentBeat
                val accent = b == 0
                val size = if (accent) 26.dp else 20.dp
                val fill = when {
                    on && accent -> teal
                    on -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val borderColor = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(fill, CircleShape)
                        .border(2.dp, if (on) fill else borderColor, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Transport
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { m.toggle() }) { Text(if (m.isPlaying) "Stop ■" else "Play ▶") }
            Spacer(Modifier.weight(1f))
            NumericValueText("${m.bpm} BPM", value = m.bpm.toFloat(), min = 10f, max = 300f,
                onSet = { m.changeBpm(it.toInt()) },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value = m.bpm.toFloat(), onValueChange = { m.changeBpm(it.toInt()) }, valueRange = 10f..300f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { m.changeBpm(m.bpm - 1) }) { Text("−") }
            OutlinedButton(onClick = { m.changeBpm(m.bpm + 1) }) { Text("+") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { m.tapTempo() }) { Text("Tap tempo") }
        }
        Spacer(Modifier.height(14.dp))

        // Time signature
        Text("Time signature", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((n, d) in MetronomeState.TIME_SIGNATURES) {
                val sel = m.beatsPerBar == n && m.beatUnit == d
                FilterChip(selected = sel, onClick = { m.setTimeSignature(n, d) }, label = { Text("$n/$d") })
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Two wood clicks — the higher one marks beat 1 of each bar.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

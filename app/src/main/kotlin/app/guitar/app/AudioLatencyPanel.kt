package app.guitar.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.guitar.audio.AudioRates
import app.guitar.audio.AudioTrackEngine
import app.guitar.audio.SwitchableAudioEngine

/**
 * Measured touch-to-sound budget, on screen.
 *
 * Perceived latency is notoriously hard to estimate by ear — and the single biggest
 * contributor is usually the OUTPUT ROUTE, not the app: Bluetooth buffers 150-400 ms inside
 * the receiving device, downstream of everything the app controls. This panel shows what the
 * engine actually negotiated with the platform plus the current route, so a report of
 * "still late" can be diagnosed instead of guessed at.
 */
@Composable
fun AudioLatencyPanel(state: AppState) {
    val context = LocalContext.current
    val engine = (state.audio as? SwitchableAudioEngine)?.modernEngine as? AudioTrackEngine
    var tick by remember { mutableStateOf(0) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Audio latency", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))

            if (engine == null) {
                Text(
                    "Unavailable (the legacy A/B engine is selected).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            // `tick` is read so pressing Refresh recomposes with fresh numbers.
            @Suppress("UNUSED_EXPRESSION") tick
            val r = engine.latencyReport()
            val route = remember(tick) { AudioRates.outputRoute(context) }

            if (route.highLatency) {
                Text(
                    "⚠ Output is ${route.label}. Bluetooth adds roughly 150-400 ms of its own " +
                        "delay, inside the receiving device — nothing the app does can remove it. " +
                        "Use the phone speaker or wired headphones to judge touch response.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
            }

            LatencyRow("Output route", route.label)
            LatencyRow("Engine rate", "${r.sampleRate} Hz")
            LatencyRow("Device burst", "${r.halBurstFrames} frames (${"%.1f".format(r.burstMs)} ms)")
            LatencyRow(
                "Output queue",
                "${r.effectiveBufferFrames} frames (${"%.1f".format(r.bufferMs)} ms)" +
                    if (r.allocatedBufferFrames != r.effectiveBufferFrames)
                        " of ${r.allocatedBufferFrames} allocated" else "",
            )
            LatencyRow(
                "Touch → app",
                if (InputLatencyProbe.lastDispatchMs < 0) "— (tap the neck first)"
                else "${InputLatencyProbe.lastDispatchMs} ms (worst ${InputLatencyProbe.worstDispatchMs} ms)",
            )
            LatencyRow("Underruns", if (r.underruns < 0) "n/a" else r.underruns.toString())
            LatencyRow("Stream", if (r.outputWarm) "warm (ready)" else "parked (next note wakes it)")
            LatencyRow(
                "Last note",
                if (r.lastNoteSynthMs < 0) "—"
                else "queued ${r.lastNoteQueueMs} ms + synth ${r.lastNoteSynthMs} ms",
            )

            Spacer(Modifier.height(4.dp))
            Text(
                "Engine-side total ≈ ${"%.1f".format(r.bufferMs)} ms of queue plus the last " +
                    "note's synth time. The phone's own hardware output adds ~10-40 ms on top " +
                    "(more on Bluetooth) and is not measurable from inside the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = { tick++ }) { Text("Refresh") }
        }
    }
}

@Composable
private fun LatencyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

package app.guitar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.guitar.audio.AudioEngine
import app.guitar.audio.AudioTrackEngine
import app.guitar.theory.ChordLibrary
import app.guitar.theory.FretPosition
import app.guitar.theory.NoteSpeller
import app.guitar.theory.ScaleLibrary
import app.guitar.theory.Tuning
import app.guitar.theory.Tunings

class MainActivity : ComponentActivity() {
    private val audioEngine: AudioEngine = AudioTrackEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuitarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App(audioEngine)
                }
            }
        }
    }

    override fun onDestroy() {
        audioEngine.close()
        super.onDestroy()
    }
}

@Composable
fun App(audio: AudioEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { TuningRepository(context.applicationContext) }
    val state = remember { AppState(repo, scope, audio) }

    val customTunings by state.customTunings.collectAsState(initial = emptyMap())
    val savedSelected by state.savedSelectedName.collectAsState(initial = "Standard")
    val persistedLeftHanded by repo.leftHanded.collectAsState(initial = false)

    LaunchedEffect(savedSelected, customTunings) {
        if (!state.isEditedTuning) {
            state.tuningName = savedSelected
            state.liveTuning = Tunings.all[savedSelected]
                ?: customTunings[savedSelected]
                ?: Tunings.standard
        }
    }
    LaunchedEffect(persistedLeftHanded) { state.leftHanded = persistedLeftHanded }
    DisposableEffect(Unit) { onDispose { audio.stop() } }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopStatusBar(state)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        FretboardSection(state)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ModePanel(state = state, customTunings = customTunings)
        }
        ModeBar(active = state.mode, onChange = { state.mode = it })
    }
}

@Composable
private fun TopStatusBar(state: AppState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("GUITAR", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.width(12.dp))
        val tuningSummary = "${state.tuningName}${if (state.isEditedTuning) "*" else ""}" +
            "  ·  " + state.liveTuning.openStrings.joinToString(" ") { NoteSpeller.spell(it.pitchClass) }
        Text(
            tuningSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FretboardSection(state: AppState) {
    val parsedChord = ChordLibrary.parse(state.chordInput)
    val scalePc = try { NoteSpeller.parsePitchClass(state.scaleRoot) } catch (_: Exception) { null }
    val scale = ScaleLibrary.scales[state.scaleType]

    val marks: Map<FretPosition, FretMark> = remember(
        state.mode, state.chordInput, state.scaleRoot, state.scaleType,
        state.liveTuning, state.labelMode, state.pickedPositions, parsedChord, scalePc, scale
    ) {
        when (state.mode) {
            Mode.Chord -> parsedChord?.let { (r, q) ->
                chordMarks(r, q, state.liveTuning, DISPLAY_FRETS, state.labelMode)
            } ?: emptyMap()
            Mode.Scale -> if (scalePc != null && scale != null)
                scaleMarks(scalePc, scale, state.liveTuning, DISPLAY_FRETS, state.labelMode) else emptyMap()
            Mode.Pick -> pickedMarks(state)
            Mode.Tuning -> emptyMap()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        FretboardView(
            tuning = state.liveTuning,
            marks = marks,
            selectedPosition = state.selectedPosition,
            onTap = { pos ->
                if (state.mode == Mode.Pick) state.togglePick(pos) else state.tapPosition(pos)
            },
            numFrets = DISPLAY_FRETS,
            leftHanded = state.leftHanded,
        )
    }
    SelectedPositionInfo(state.liveTuning, state.selectedPosition, parsedChord)
}

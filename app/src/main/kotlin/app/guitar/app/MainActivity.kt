package app.guitar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import app.guitar.audio.AudioEngine
import app.guitar.audio.AudioTrackEngine
import app.guitar.theory.Tunings

class MainActivity : ComponentActivity() {
    private val audioEngine: AudioEngine = AudioTrackEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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

enum class Screen(val label: String, val icon: String) {
    Fretboard("Fretboard", "≡"),
    Chord("Chord", "♪"),
    Scale("Scale", "♫"),
    Settings("Settings", "⚙"),
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

    LaunchedEffect(persistedLeftHanded) {
        state.leftHanded = persistedLeftHanded
    }

    DisposableEffect(Unit) {
        onDispose { audio.stop() }
    }

    var screen by remember { mutableStateOf(Screen.Fretboard) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = screen == s,
                        onClick = { screen = s },
                        icon = { Text(s.icon, fontSize = 22.sp) },
                        label = { Text(s.label) }
                    )
                }
            }
        }
    ) { padding ->
        val innerModifier = Modifier.padding(padding)
        when (screen) {
            Screen.Fretboard -> FretboardScreen(state, innerModifier)
            Screen.Chord -> ChordScreen(state, innerModifier)
            Screen.Scale -> ScaleScreen(state, innerModifier)
            Screen.Settings -> SettingsScreen(state, customTunings, innerModifier)
        }
    }
}

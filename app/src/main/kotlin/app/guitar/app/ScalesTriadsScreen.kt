package app.guitar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.guitar.theory.CagedMode
import app.guitar.theory.CagedScales
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.Interval
import app.guitar.theory.NoteSpeller
import app.guitar.theory.ScaleSubset

private const val TRAINER_FRETS = 22
private val STRING_NAMES = listOf("6 (low E)", "5 (A)", "4 (D)", "3 (G)", "2 (B)", "1 (high E)")

@Composable
fun ScalesTriadsScreen(state: AppState, onBack: () -> Unit) {
    val t = state.cagedTrainer
    DisposableEffect(Unit) { onDispose { if (state.currentSheet != Sheet.ScalesTriads) t.stop() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Scales & Triads", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // Tabs
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TabButton("Practice", t.tab == TrainerTab.Practice) { t.selectTab(TrainerTab.Practice) }
            TabButton("Challenge", t.tab == TrainerTab.Challenge) { t.selectTab(TrainerTab.Challenge) }
            TabButton("Triads", t.tab == TrainerTab.Triads) { t.selectTab(TrainerTab.Triads) }
        }

        // Key + tempo (shared)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Key: ${NoteSpeller.spell(t.key)}", fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
            OutlinedButton(onClick = { t.chooseKey(app.guitar.theory.PitchClass.of(t.key.value + 11)) }) { Text("−") }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = { t.chooseKey(app.guitar.theory.PitchClass.of(t.key.value + 1)) }) { Text("+") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { t.randomKey() }) { Text("🎲 Random") }
        }
        Text("Tempo: ${t.bpm} BPM", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        Slider(value = t.bpm.toFloat(), onValueChange = { t.changeBpm(it.toInt()) }, valueRange = 30f..200f)

        when (t.tab) {
            TrainerTab.Practice -> PracticeControls(t)
            TrainerTab.Challenge -> ChallengeControls(t)
            TrainerTab.Triads -> TriadControls(t)
        }

        // Shared fretboard
        val marks = trainerMarks(state)
        Box(modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 12.dp)) {
            FretboardView(
                tuning = t.tuning,
                marks = marks,
                selectedPosition = if (t.tab == TrainerTab.Practice) t.activeNote else null,
                onTap = { pos -> state.audio.playNote(Fretboard.noteAt(t.tuning, pos).midi.value, durationMillis = state.ringSustainMs) },
                numFrets = TRAINER_FRETS,
                leftHanded = state.leftHanded,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun PracticeControls(t: CagedTrainerState) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { t.toggle() }) { Text(if (t.isPlaying) "Stop ■" else "Play ▶") }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { t.nudgeStep(-1) }, enabled = t.stepIndex > 0) { Text("◀") }
        Spacer(Modifier.width(6.dp))
        Text("${t.stepIndex + 1}/30")
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { t.nudgeStep(1) }, enabled = t.stepIndex < 29) { Text("▶") }
    }
    val modeName = if (t.step.mode == CagedMode.Major) "Major" else "Minor"
    val subName = when (t.step.subset) {
        ScaleSubset.Triad -> "triad"; ScaleSubset.FullScale -> "scale"; ScaleSubset.Pentatonic -> "pentatonic"
    }
    Text("Box ${t.boxIndex + 1} · $modeName $subName", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = t.audioDemo, onCheckedChange = { t.toggleAudioDemo() })
        Spacer(Modifier.width(8.dp))
        Text("Audio demo (play the notes) — off = metronome only")
    }
}

@Composable
private fun ChallengeControls(t: CagedTrainerState) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { t.nextChallenge() }) { Text("Next") }
        OutlinedButton(onClick = { t.toggleReveal() }, enabled = t.challenge != null) {
            Text(if (t.reveal) "Hide neck" else "Reveal on neck")
        }
    }
    val c = t.challenge
    if (c == null) {
        Text("Tap Next for a prompt, play it yourself, then Reveal to check.", modifier = Modifier.padding(top = 12.dp))
    } else {
        val rootStr = primaryRootString(t, c.key, c.box, c.mode)
        Column(Modifier.padding(top = 12.dp)) {
            Text(
                "${NoteSpeller.spell(c.key)} ${if (c.mode == CagedMode.Major) "major" else "minor"}",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            Text(if (c.subset == ScaleSubset.Pentatonic) "Pentatonic" else "Diatonic (full scale)")
            Text("Box ${c.box.ordinal + 1} — root on string $rootStr")
        }
    }
}

@Composable
private fun TriadControls(t: CagedTrainerState) {
    val seq = t.triadSequence()
    val idx = if (t.activeTriad >= 0) t.activeTriad else 0
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { t.toggle() }) { Text(if (t.isPlaying) "Stop ■" else "Play ▶") }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { t.nudgeTriad(-1) }) { Text("◀") }
        Spacer(Modifier.width(6.dp))
        Text("${idx + 1}/${seq.size}")
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { t.nudgeTriad(1) }) { Text("▶") }
    }
    val cur = seq.getOrNull(idx)
    val groups = listOf("strings 6-5-4", "strings 5-4-3", "strings 4-3-2", "strings 3-2-1")
    val invs = listOf("root position", "1st inversion", "2nd inversion")
    val label = if (cur != null)
        "${NoteSpeller.spell(t.key)}${if (cur.first == "min") "m" else ""} · ${groups[(idx % 12) / 3]} · ${invs[cur.second.inversion]}"
    else "All major triads then all minor — 4 groups × 3 inversions."
    Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    Text("◀ ▶ to step all 24; Play runs them one per beat.", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
}

/** Marks for the current tab. */
private fun trainerMarks(state: AppState): Map<FretPosition, FretMark> {
    val t = state.cagedTrainer
    return when (t.tab) {
        TrainerTab.Practice -> notesToMarks(t.practiceNotes())
        TrainerTab.Challenge -> {
            val c = t.challenge
            if (c != null && t.reveal) notesToMarks(CagedScales.resolve(c.key, c.box, c.mode, c.subset, t.tuning))
            else emptyMap()
        }
        TrainerTab.Triads -> {
            val seq = t.triadSequence()
            val cur = seq.getOrNull(if (t.activeTriad >= 0) t.activeTriad else 0) ?: return emptyMap()
            val root = t.key.value
            val out = HashMap<FretPosition, FretMark>()
            cur.second.strings.forEachIndexed { k, s ->
                val pos = FretPosition(s, cur.second.frets[k])
                val pc = Fretboard.noteAt(t.tuning, pos).pitchClass
                out[pos] = FretMark(intervalName(Interval(((pc.value - root) % 12 + 12) % 12)), pc.value == root, MarkKind.Chord)
            }
            out
        }
    }
}

private fun notesToMarks(notes: List<app.guitar.theory.CagedNote>): Map<FretPosition, FretMark> {
    val out = HashMap<FretPosition, FretMark>()
    for (n in notes) out[n.position] = FretMark(intervalName(n.interval), n.isRoot, MarkKind.Scale)
    return out
}

/** Guitar string number of the lowest string carrying the mode root in this box. */
private fun primaryRootString(t: CagedTrainerState, key: app.guitar.theory.PitchClass, box: app.guitar.theory.CagedBox, mode: CagedMode): String {
    val notes = CagedScales.resolve(key, box, mode, ScaleSubset.FullScale, t.tuning)
    val root = CagedScales.rootOf(key, mode)
    var lowest = 6
    for (n in notes) {
        val pc = Fretboard.noteAt(t.tuning, n.position).pitchClass
        if (pc == root && n.position.stringIndex < lowest) lowest = n.position.stringIndex
    }
    return STRING_NAMES[lowest.coerceIn(0, 5)]
}

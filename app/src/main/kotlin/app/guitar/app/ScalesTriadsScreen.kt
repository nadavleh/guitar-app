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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.guitar.theory.CagedBox
import app.guitar.theory.CagedMode
import app.guitar.theory.CagedScales
import app.guitar.theory.Fretboard
import app.guitar.theory.FretPosition
import app.guitar.theory.Interval
import app.guitar.theory.NoteSpeller
import app.guitar.theory.ScaleSubset

private const val TRAINER_FRETS = 22
private val STRING_NAMES = listOf("6 (low E)", "5 (A)", "4 (D)", "3 (G)", "2 (B)", "1 (high E)")

/** How the Triads drill names each 3-string group, in the drill's own order. */
private val TRIAD_GROUP_NAMES = listOf("strings 1-2-3", "strings 2-3-4", "strings 3-4-5", "strings 4-5-6")
private val INVERSION_NAMES = listOf("root position", "1st inversion", "2nd inversion")

private fun sectionLabel(s: TrainerSection) = when (s) {
    TrainerSection.Scales -> "Scales"
    TrainerSection.Triads -> "Triads"
}

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
        // Title + section dropdown. The dropdown is the top-level split; each
        // section brings its own tab row (or none).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Guitar practice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            SectionDropdown(t)
        }

        // Tabs (Scales only — the Triads drill is a single view)
        if (t.section == TrainerSection.Scales) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TabButton("Guided run", t.tab == TrainerTab.Practice) { t.selectTab(TrainerTab.Practice) }
                TabButton("Challenge", t.tab == TrainerTab.Challenge) { t.selectTab(TrainerTab.Challenge) }
                TabButton("Explore", t.tab == TrainerTab.Explore) { t.selectTab(TrainerTab.Explore) }
            }
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

        if (t.section == TrainerSection.Triads) TriadControls(t)
        else when (t.tab) {
            TrainerTab.Practice -> PracticeControls(t)
            TrainerTab.Challenge -> ChallengeControls(t)
            TrainerTab.Explore -> ExploreControls(t)
        }

        // Shared fretboard
        val marks = trainerMarks(state)
        Box(modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 12.dp)) {
            FretboardView(
                tuning = t.tuning,
                marks = marks,
                selectedPosition = if (t.section == TrainerSection.Scales && t.tab == TrainerTab.Practice) t.activeNote else null,
                onTap = { pos -> state.audio.playNote(Fretboard.noteAt(t.tuning, pos).midi.value, durationMillis = state.ringSustainMs) },
                numFrets = TRAINER_FRETS,
                leftHanded = state.leftHanded,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionDropdown(t: CagedTrainerState) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("${sectionLabel(t.section)} ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (s in TrainerSection.entries) {
                DropdownMenuItem(
                    text = { Text(sectionLabel(s)) },
                    onClick = { t.selectSection(s); open = false },
                )
            }
        }
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
        Text("${t.stepIndex + 1}/${t.stepCount}")
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { t.nudgeStep(1) }, enabled = t.stepIndex < t.stepCount - 1) { Text("▶") }
    }
    val step = t.step
    val modeName = if (step.mode == CagedMode.Major) "Major" else "Minor"
    val subName = when (step.subset) {
        ScaleSubset.Triad -> "triad"; ScaleSubset.FullScale -> "scale"; ScaleSubset.Pentatonic -> "pentatonic"
    }
    val patName = if (hasTwoPatterns(step.box, step.mode, step.subset)) " pattern ${step.pattern}" else ""
    val w = t.practiceWindow()
    Text(
        "Box ${step.box.number}/5 (${step.box.cagedShape} shape) · $modeName $subName$patName",
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        "Frets ${maxOf(w.first, 0)}–${w.last} · step ${t.drillIndex + 1} of ${t.drillCount} in this box",
        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp),
    )
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in CagedBox.entries) {
            TabButton("${b.number}", b == step.box) { t.jumpToBox(b) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = t.audioDemo, onCheckedChange = { t.toggleAudioDemo() })
        Spacer(Modifier.width(8.dp))
        Text("Audio demo (play the notes) — off = metronome only")
    }
}

/** True when the sheet draws two fingerings for this diagram (scale of boxes 1 and 4). */
private fun hasTwoPatterns(box: CagedBox, mode: CagedMode, subset: ScaleSubset): Boolean =
    app.guitar.theory.CagedShapeTable.patternCount(box, mode, subset) > 1

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
            Text("Box ${c.box.number} (${c.box.cagedShape} shape) — root on string $rootStr")
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
    val label = if (cur != null)
        "${NoteSpeller.spell(t.key)}${if (cur.first == "min") "m" else ""} · ${TRIAD_GROUP_NAMES[(idx % 12) / 3]} · ${INVERSION_NAMES[cur.second.inversion]}"
    else "All 3 inversions on strings 1-2-3, then 2-3-4, 3-4-5, 4-5-6 — major, then the same again minor."
    Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    Text(
        "◀ ▶ to step all ${seq.size}; Play runs them one per beat.",
        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ExploreControls(t: CagedTrainerState) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TabButton("Major", t.exploreScale == ExploreScale.Major) { t.selectExploreScale(ExploreScale.Major) }
        TabButton("Minor", t.exploreScale == ExploreScale.Minor) { t.selectExploreScale(ExploreScale.Minor) }
        TabButton("Pentatonic", t.exploreScale == ExploreScale.Pentatonic) { t.selectExploreScale(ExploreScale.Pentatonic) }
    }
    val positions = t.explorePositionsList()
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Position", modifier = Modifier.width(76.dp))
        OutlinedButton(onClick = { t.nudgeExplorePos(-1) }, enabled = positions.size > 1) { Text("◀") }
        Spacer(Modifier.width(6.dp))
        Text("${if (positions.isNotEmpty()) t.explorePos + 1 else 0}/${positions.size}")
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { t.nudgeExplorePos(1) }, enabled = positions.size > 1) { Text("▶") }
    }
    val cur = positions.getOrNull(t.explorePos)
    if (cur != null) {
        val patName = if (hasTwoPatterns(cur.box, cur.mode, cur.subset)) " pattern ${cur.pattern}" else ""
        Text(
            "Box ${cur.box.number}/5 (${cur.box.cagedShape} shape)$patName · frets ${cur.firstFret}–${cur.lastFret}",
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp),
        )
    }
    Text(
        "The same boxes the Guided run drills, low to high the neck. Tap a note to hear it.",
        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp),
    )
}

/** Marks for the current section/tab. */
private fun trainerMarks(state: AppState): Map<FretPosition, FretMark> {
    val t = state.cagedTrainer
    if (t.section == TrainerSection.Triads) {
        val seq = t.triadSequence()
        val cur = seq.getOrNull(if (t.activeTriad >= 0) t.activeTriad else 0) ?: return emptyMap()
        val root = t.key.value
        val out = HashMap<FretPosition, FretMark>()
        cur.second.strings.forEachIndexed { k, s ->
            val pos = FretPosition(s, cur.second.frets[k])
            val pc = Fretboard.noteAt(t.tuning, pos).pitchClass
            out[pos] = FretMark(intervalName(Interval(((pc.value - root) % 12 + 12) % 12)), pc.value == root, MarkKind.Chord)
        }
        return out
    }
    return when (t.tab) {
        TrainerTab.Practice -> notesToMarks(t.practiceNotes())
        TrainerTab.Challenge -> {
            val c = t.challenge
            if (c != null && t.reveal)
                notesToMarks(CagedScales.resolve(c.key, c.box, c.mode, c.subset, t.tuning, pattern = c.pattern))
            else emptyMap()
        }
        TrainerTab.Explore -> {
            val pos = t.explorePositionsList().getOrNull(t.explorePos) ?: return emptyMap()
            notesToMarks(pos.notes)
        }
    }
}

private fun notesToMarks(notes: List<app.guitar.theory.CagedNote>): Map<FretPosition, FretMark> {
    val out = HashMap<FretPosition, FretMark>()
    for (n in notes) out[n.position] = FretMark(intervalName(n.interval), n.isRoot, MarkKind.Scale)
    return out
}

/** Guitar string number of the lowest string carrying the mode root in this box. */
private fun primaryRootString(t: CagedTrainerState, key: app.guitar.theory.PitchClass, box: CagedBox, mode: CagedMode): String {
    val notes = CagedScales.resolve(key, box, mode, ScaleSubset.FullScale, t.tuning)
    val root = CagedScales.rootOf(key, mode)
    var lowest = 6
    for (n in notes) {
        val pc = Fretboard.noteAt(t.tuning, n.position).pitchClass
        if (pc == root && n.position.stringIndex < lowest) lowest = n.position.stringIndex
    }
    return STRING_NAMES[lowest.coerceIn(0, 5)]
}

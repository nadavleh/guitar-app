package app.guitar.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.guitar.theory.ChordQuality
import app.guitar.theory.ChordShape
import app.guitar.theory.Fingering
import app.guitar.theory.FretPosition
import app.guitar.theory.Fretboard
import app.guitar.theory.FretboardOverlay
import app.guitar.theory.Interval
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.Scale
import app.guitar.theory.ScalePosition
import app.guitar.theory.Tuning

fun intervalName(iv: Interval): String = when (iv.semitones % 12) {
    0 -> "1"; 1 -> "b2"; 2 -> "2"; 3 -> "b3"; 4 -> "3"; 5 -> "4"
    6 -> "b5"; 7 -> "5"; 8 -> "b6"; 9 -> "6"; 10 -> "b7"; 11 -> "7"
    else -> "?"
}

fun chordMarks(
    root: PitchClass,
    quality: ChordQuality,
    tuning: Tuning,
    numFrets: Int,
    labelMode: LabelMode,
    fretRange: IntRange? = null,
): Map<FretPosition, FretMark> {
    val overlay = FretboardOverlay.chord(root, quality, tuning, numFrets)
    val filtered = if (fretRange == null) overlay else overlay.filterKeys { it.fret in fretRange }
    return filtered.mapValues { (_, h) ->
        FretMark(
            label = when (labelMode) {
                LabelMode.Notes -> NoteSpeller.spell(h.pitchClass)
                LabelMode.Intervals -> intervalName(h.interval)
                LabelMode.Empty -> ""
            },
            isRoot = h.isRoot,
            kind = MarkKind.Chord,
        )
    }
}

fun scaleMarks(
    root: PitchClass,
    scale: Scale,
    tuning: Tuning,
    numFrets: Int,
    labelMode: LabelMode,
    fretRange: IntRange? = null,
): Map<FretPosition, FretMark> {
    val overlay = FretboardOverlay.scale(root, scale, tuning, numFrets)
    val filtered = if (fretRange == null) overlay else overlay.filterKeys { it.fret in fretRange }
    return filtered.mapValues { (_, h) ->
        FretMark(
            label = when (labelMode) {
                LabelMode.Notes -> NoteSpeller.spell(h.pitchClass)
                LabelMode.Intervals -> intervalName(h.interval)
                LabelMode.Empty -> ""
            },
            isRoot = h.isRoot,
            kind = MarkKind.Scale,
        )
    }
}

fun scalePositionMarks(
    position: ScalePosition,
    root: PitchClass,
    tuning: Tuning,
    labelMode: LabelMode,
): Map<FretPosition, FretMark> {
    val result = HashMap<FretPosition, FretMark>(position.positions.size)
    for (pos in position.positions) {
        if (pos.stringIndex >= tuning.stringCount) continue
        val note = Fretboard.noteAt(tuning, pos)
        val isRoot = note.pitchClass == root
        val interval = Interval(((note.pitchClass.value - root.value) % 12 + 12) % 12)
        result[pos] = FretMark(
            label = when (labelMode) {
                LabelMode.Notes -> NoteSpeller.spell(note.pitchClass)
                LabelMode.Intervals -> intervalName(interval)
                LabelMode.Empty -> ""
            },
            isRoot = isRoot,
            kind = MarkKind.Scale,
        )
    }
    return result
}

fun pickedMarks(state: AppState): Map<FretPosition, FretMark> {
    val result = HashMap<FretPosition, FretMark>()
    for (pos in state.pickedPositions) {
        if (pos.stringIndex >= state.liveTuning.stringCount) continue
        val note = Fretboard.noteAt(state.liveTuning, pos)
        result[pos] = FretMark(
            label = when (state.labelMode) {
                LabelMode.Notes -> NoteSpeller.spell(note.pitchClass)
                LabelMode.Intervals, LabelMode.Empty -> ""
            },
            isRoot = false,
            kind = MarkKind.Pick,
        )
    }
    return result
}

/**
 * Per-string mute toggles for Strum (pick) mode. Each chip is one string, labeled
 * by its open-note name (high → low, as seen looking at the neck); tapping toggles
 * a red ✕ mute that excludes the string from the strum. Shared by the Strum sheet
 * and the on-screen strum action bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StringMuteRow(state: AppState) {
    val tuning = state.liveTuning
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (s in tuning.stringCount - 1 downTo 0) {
            val name = NoteSpeller.spell(tuning.openStrings[s].pitchClass)
            val muted = s in state.mutedStrings
            FilterChip(
                selected = muted,
                onClick = { state.toggleMutedString(s) },
                label = { Text(if (muted) "✕ $name" else name) },
            )
        }
    }
}

fun shapeMarks(
    shape: ChordShape,
    labelMode: LabelMode,
): Map<FretPosition, FretMark> {
    val result = HashMap<FretPosition, FretMark>()
    for (s in shape.frets.indices) {
        val f = shape.frets[s] ?: continue
        val note = Fretboard.noteAt(shape.tuning, FretPosition(s, f))
        val interval = Interval(((note.pitchClass.value - shape.root.value) % 12 + 12) % 12)
        result[FretPosition(s, f)] = FretMark(
            label = when (labelMode) {
                LabelMode.Notes -> NoteSpeller.spell(note.pitchClass)
                LabelMode.Intervals -> intervalName(interval)
                LabelMode.Empty -> ""
            },
            isRoot = note.pitchClass == shape.root,
        )
    }
    return result
}

@Composable
fun ShapeCard(
    shape: ChordShape,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reversedFrets = shape.frets.reversed()
    val reversedNotes = shape.notes.reversed()
    val reversedIntervals = shape.intervals.reversed()
    val reversedFingering = Fingering.suggest(shape).reversed()

    val fretsLine = reversedFrets.joinToString(" ") { f -> if (f == null) " x" else f.toString().padStart(2, ' ') }
    val notesLine = reversedNotes.joinToString(" ") { n -> if (n == null) " x" else NoteSpeller.spell(n.pitchClass).padStart(2, ' ') }
    val intervalsLine = reversedIntervals.joinToString(" ") { iv -> if (iv == null) " x" else intervalName(iv).padStart(2, ' ') }
    val fingersLine = reversedFingering.joinToString(" ") { f -> if (f == null) " ·" else f.toString().padStart(2, ' ') }

    val rootTag = if (shape.hasRootInBass) " · root in bass" else ""
    val positionLabel = if (shape.position == 0) "open position" else "position ${shape.position}"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (selected) androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                 else androidx.compose.material3.CardDefaults.cardColors(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${shape.chordName}  ·  $positionLabel  ·  span ${shape.fretSpan}$rootTag",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))
            Text("frets     $fretsLine", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text("notes     $notesLine", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text("intervals $intervalsLine", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text("fingers   $fingersLine", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SelectedPositionInfo(
    tuning: Tuning,
    selected: FretPosition?,
    parsedChord: Pair<PitchClass, ChordQuality>?,
) {
    if (selected == null) {
        // Show the current tuning here too (handy in portrait, where the top status
        // bar is far from the neck): low → high open-string note names.
        val tuningNotes = tuning.openStrings.joinToString(" ") { NoteSpeller.spell(it.pitchClass) }
        Text(
            "Tuning:  $tuningNotes    ·    tap any spot to inspect",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (selected.stringIndex >= tuning.stringCount) return
    val note = Fretboard.noteAt(tuning, selected)
    val noteName = NoteSpeller.spell(note)
    val stringNum = tuning.stringCount - selected.stringIndex
    val openOrFret = if (selected.fret == 0) "open" else "fret ${selected.fret}"
    val tail = parsedChord?.let { (root, _) ->
        val interval = Interval(((note.pitchClass.value - root.value) % 12 + 12) % 12)
        "  ·  ${intervalName(interval)} relative to ${NoteSpeller.spell(root)}"
    } ?: ""
    Text("string $stringNum · $openOrFret · $noteName$tail", style = MaterialTheme.typography.bodySmall)
}

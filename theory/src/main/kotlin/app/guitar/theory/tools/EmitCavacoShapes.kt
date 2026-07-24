package app.guitar.theory.tools

import app.guitar.theory.Accidental
import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordQuality
import app.guitar.theory.FretPosition
import app.guitar.theory.Fretboard
import app.guitar.theory.Interval
import app.guitar.theory.NoteSpeller
import app.guitar.theory.PitchClass
import app.guitar.theory.Tuning
import app.guitar.theory.Tunings
import java.io.File

/**
 * Emits cavaquinho (DGBD) G-chord shapes as JSON for the chord-sheet PDF
 * generator (tools/build_cavaco_chord_pdf.py).
 *
 * Unlike the app's 5-shape picker (which drops the 5th on 7ths and caps at 5),
 * this tool enumerates a COMPREHENSIVE reference set, all using four strings:
 *
 *   - COMPLETE voicings: every chord tone present. Since all four strings
 *     sound, the low-D string (string 0) is always the bass, so the voicing's
 *     INVERSION is exactly which chord tone sits on the lowest string (root =
 *     root position, 3rd = 1st inversion, 5th = 2nd, 7th = 3rd). This matches
 *     the cavaquinho-CAGED idea of "the note on the lowest string picks the
 *     shape". Deduped by interval-per-string signature (an octave-up grip
 *     collapses to its lowest form), kept 2 per inversion.
 *   - ROOTLESS voicings (7th chords): the root dropped, leaving the 3rd, 5th
 *     and 7th — an upper-structure triad built on the 3rd (for a dominant 7
 *     that triad is DIMINISHED, e.g. G7 -> Bdim). Staple comping shapes.
 *   - SHELL voicings (7th chords): the 5th dropped (root + 3rd + 7th), e.g. the
 *     G7 grip 5-4-6-5 = G B F G.
 *
 * Complete voicings come first (grouped by inversion), then rootless, then
 * shells. Each shape is tagged with `kind` ("complete"/"rootless"/"shell"),
 * `inversion` (0..3) and a `label` (the upper-structure name for rootless,
 * else "") so the PDF can caption it directly.
 *
 * Run:  .\gradlew :theory:emitCavacoShapes
 * Output (default): tools/cavaco_g_shapes.json — or pass a path as arg 0.
 */

private const val MAX_FRET = 15
private const val MAX_SPAN = 3              // max highest-minus-lowest fretted distance; 3 = fits a 4-fret window (drops any 5-fret-or-wider stretch)
private const val MAX_PER_INVERSION = 2     // complete voicings kept per inversion
private const val MAX_ROOTLESS = 3          // rootless voicings kept per quality
private const val MAX_SHELLS = 2            // no-5th shells kept per quality

// Qualities that get rootless + shell extras (4-note chords with a real root to drop / 5th to drop).
private val EXTRA_SYMBOLS = setOf("7", "m7", "maj7")

private data class Shape(
    val frets: List<Int>,        // 4 entries, all strings sound (no mutes)
    val intervals: List<Int>,    // interval semitone (0..11) per string, relative to root
    val bass: Int,               // interval on the lowest string (string 0)
    val position: Int,           // lowest fretted non-zero fret, else 0
    val inversion: Int,          // 0..3 = index of the bass interval in the sorted chord tones
    val kind: String,            // "complete" | "rootless" | "shell"
    val label: String,           // upper-structure name for rootless (e.g. "Bdim"), else ""
)

fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "tools/cavaco_g_shapes.json" })
    val tuning = Tunings.cavaqDgbd
    val rootG = PitchClass(7)

    // The 8 pages of the original "All_G_chord_shapes" sheet, in order.
    val pages = listOf(
        "G major" to "",
        "G minor" to "m",
        "G7" to "7",
        "Gm7" to "m7",
        "Gmaj7" to "maj7",
        "G dim" to "dim",
        "G b5bb7 (full dim7)" to "dim7",
        "G half-dim7 (m7b5)" to "m7b5",
        "G6/9" to "69",
    )

    // 6/9 has five tones (R 3 5 6 9) — one too many for four strings, so the
    // cavaquinho voicing drops the 5th: the chord is R 3 6 9. Built locally
    // (not in ChordLibrary) to keep this a tool-only addition.
    val sixNine = ChordQuality("69", listOf(Interval.P1, Interval.maj3, Interval.maj6, Interval.maj9))

    val sb = StringBuilder()
    sb.append("{\n  \"tuning\": \"DGBD\",\n  \"pages\": [\n")
    pages.forEachIndexed { pi, (header, symbol) ->
        val quality = if (symbol == "69") sixNine else ChordLibrary.qualities.getValue(symbol)
        val shapes = shapesFor(rootG, quality, symbol, tuning)
        val by = shapes.groupingBy { it.kind }.eachCount()
        System.err.println("$header: ${by["complete"] ?: 0} complete, ${by["rootless"] ?: 0} rootless, ${by["shell"] ?: 0} shell")
        sb.append("    {\n")
        sb.append("      \"header\": \"").append(header).append("\",\n")
        sb.append("      \"symbol\": \"").append(symbol).append("\",\n")
        sb.append("      \"shapes\": [\n")
        shapes.forEachIndexed { si, sh ->
            sb.append("        {\"frets\": [").append(sh.frets.joinToString(","))
                .append("], \"intervals\": [").append(sh.intervals.joinToString(","))
                .append("], \"bass\": ").append(sh.bass)
                .append(", \"position\": ").append(sh.position)
                .append(", \"inversion\": ").append(sh.inversion)
                .append(", \"kind\": \"").append(sh.kind).append("\"")
                .append(", \"label\": \"").append(sh.label).append("\"")
                .append("}")
            sb.append(if (si == shapes.lastIndex) "\n" else ",\n")
        }
        sb.append("      ]\n")
        sb.append("    }").append(if (pi == pages.lastIndex) "\n" else ",\n")
    }
    sb.append("  ]\n}\n")
    out.parentFile?.mkdirs()
    out.writeText(sb.toString())
    println("wrote ${out.absolutePath}")
}

/** Enumerate the reference voicings for [root] [quality] (symbol [symbol]) on [tuning]. */
private fun shapesFor(root: PitchClass, quality: ChordQuality, symbol: String, tuning: Tuning): List<Shape> {
    val chordPcs = quality.notesFrom(root).toSet()
    val sortedIntervals = chordPcs
        .map { ((it.value - root.value) % 12 + 12) % 12 }
        .distinct().sorted()
    val fifth = PitchClass((root.value + 7) % 12)          // perfect 5th (may or may not be a chord tone)
    val wantExtras = symbol in EXTRA_SYMBOLS && chordPcs.size >= 4
    val hasPerfectFifth = fifth in chordPcs
    val rootlessLabel = if (wantExtras) upperStructureLabel(root, chordPcs, symbol) else ""

    // Per-string fret candidates: every fret whose pitch class is a chord tone.
    val candidates: List<List<Int>> = (0 until tuning.stringCount).map { s ->
        (0..MAX_FRET).filter { f ->
            Fretboard.noteAt(tuning, FretPosition(s, f)).pitchClass in chordPcs
        }
    }
    if (candidates.any { it.isEmpty() }) return emptyList()

    val complete = HashMap<List<Int>, Shape>()   // signature -> lowest-position shape
    val rootless = HashMap<List<Int>, Shape>()
    val shells = HashMap<List<Int>, Shape>()

    fun record(frets: List<Int>) {
        val notes = frets.mapIndexed { s, f -> Fretboard.noteAt(tuning, FretPosition(s, f)) }
        // No unison doubling on physically adjacent strings.
        for (i in 0 until notes.size - 1) {
            if (notes[i].midi.value == notes[i + 1].midi.value) return
        }
        val fretted = frets.filter { it > 0 }
        val maxFretted = fretted.maxOrNull() ?: 0
        // An open string only makes sense in first position (no note fretted above the 3rd).
        if (frets.any { it == 0 } && maxFretted > 3) return
        if (fretted.isNotEmpty() && (maxFretted - fretted.min()) > MAX_SPAN) return

        val playedPcs = notes.map { it.pitchClass }.toSet()
        val intervals = notes.map { ((it.pitchClass.value - root.value) % 12 + 12) % 12 }
        val bass = intervals[0]
        val position = fretted.minOrNull() ?: 0
        val inversion = sortedIntervals.indexOf(bass).coerceAtLeast(0)

        val isComplete = playedPcs.containsAll(chordPcs)
        val isRootless = wantExtras && root !in playedPcs && playedPcs.containsAll(chordPcs - root)
        val isShell = wantExtras && hasPerfectFifth && root in playedPcs &&
            fifth !in playedPcs && playedPcs.containsAll(chordPcs - fifth)

        val (bucket, kind, label) = when {
            isComplete -> Triple(complete, "complete", "")
            isRootless -> Triple(rootless, "rootless", rootlessLabel)
            isShell    -> Triple(shells, "shell", "")
            else       -> return
        }
        val shape = Shape(frets, intervals, bass, position, inversion, kind, label)
        val prev = bucket[intervals]
        if (prev == null || position < prev.position) bucket[intervals] = shape
    }

    // Odometer over the four strings' candidate frets.
    val idx = IntArray(tuning.stringCount)
    val cur = IntArray(tuning.stringCount)
    outer@ while (true) {
        for (s in candidates.indices) cur[s] = candidates[s][idx[s]]
        record(cur.toList())
        var i = candidates.size - 1
        while (i >= 0) {
            idx[i]++
            if (idx[i] < candidates[i].size) break
            idx[i] = 0; i--
        }
        if (i < 0) break@outer
    }

    // Complete voicings: keep MAX_PER_INVERSION lowest per inversion, ordered by inversion then position.
    val completeOut = complete.values
        .groupBy { it.inversion }
        .toSortedMap()
        .flatMap { (_, group) -> group.sortedBy { it.position }.take(MAX_PER_INVERSION) }
        .sortedWith(compareBy({ it.inversion }, { it.position }))

    // Rootless (upper-structure triads): surface the most useful — the compact,
    // low-neck grips first (avoiding the root pushes these off the open strings).
    val rootlessOut = rootless.values
        .sortedWith(compareBy({ it.position }, { it.inversion }))
        .take(MAX_ROOTLESS)

    // No-5th shells: prefer the classic root-position shell (root + 3rd + ♭7), then position.
    val shellsOut = shells.values
        .sortedWith(compareBy({ it.inversion }, { it.position }))
        .take(MAX_SHELLS)

    return completeOut + rootlessOut + shellsOut
}

/** Name the upper-structure triad left when the root is dropped from a 4-note chord:
 *  the triad built on the 3rd of the chord (dominant 7 -> diminished, maj7 -> minor,
 *  m7 -> major). Returns e.g. "Bdim", "Bm", "B♭maj". */
private fun upperStructureLabel(root: PitchClass, chordPcs: Set<PitchClass>, symbol: String): String {
    val third = chordPcs.firstOrNull {
        val iv = ((it.value - root.value) % 12 + 12) % 12
        iv == 3 || iv == 4
    } ?: return "rootless"
    val ivs = (chordPcs - root).map { ((it.value - third.value) % 12 + 12) % 12 }.toSortedSet()
    val type = when {
        ivs.containsAll(listOf(0, 3, 6)) -> "dim"
        ivs.containsAll(listOf(0, 3, 7)) -> "m"
        ivs.containsAll(listOf(0, 4, 7)) -> ""      // major triad: bare letter
        ivs.containsAll(listOf(0, 4, 8)) -> "aug"
        else -> ""
    }
    val minorFamily = symbol.startsWith("m") && symbol != "maj7"
    val name = NoteSpeller.spell(third, if (minorFamily) Accidental.FLAT else Accidental.SHARP)
    return name + type
}

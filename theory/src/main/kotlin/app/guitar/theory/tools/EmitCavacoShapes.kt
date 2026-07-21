package app.guitar.theory.tools

import app.guitar.theory.ChordLibrary
import app.guitar.theory.ChordShapeGenerator
import app.guitar.theory.PitchClass
import app.guitar.theory.Tunings
import java.io.File

/**
 * Emits every cavaquinho (DGBD) G-chord shape as JSON, one entry per chord
 * quality, for the chord-sheet PDF generator (tools/build_cavaco_chord_pdf.py).
 * Uses the exact same voicing engine as the app (ChordShapeGenerator → curated
 * cavaquinho voicings), so the printed sheet always matches what the app shows.
 *
 * Run:  .\gradlew :theory:emitCavacoShapes
 * Output (default): tools/cavaco_g_shapes.json — or pass a path as arg 0.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "tools/cavaco_g_shapes.json" })
    val tuning = Tunings.cavaqDgbd
    val gen = ChordShapeGenerator()
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
    )

    val sb = StringBuilder()
    sb.append("{\n  \"tuning\": \"DGBD\",\n  \"pages\": [\n")
    pages.forEachIndexed { pi, (header, symbol) ->
        val quality = ChordLibrary.qualities.getValue(symbol)
        val shapes = gen.shapesFor(rootG, quality, tuning, frets = 15)
        sb.append("    {\n")
        sb.append("      \"header\": \"").append(header).append("\",\n")
        sb.append("      \"symbol\": \"").append(symbol).append("\",\n")
        sb.append("      \"shapes\": [\n")
        shapes.forEachIndexed { si, sh ->
            val frets = sh.frets.joinToString(",") { it?.toString() ?: "null" }
            val intervals = sh.intervals.joinToString(",") { it?.semitones?.toString() ?: "null" }
            // Bass = the lowest played string (string 0 is the low D on DGBD).
            val bass = sh.intervals.firstOrNull { it != null }?.semitones ?: 0
            sb.append("        {\"frets\": [").append(frets)
                .append("], \"intervals\": [").append(intervals)
                .append("], \"bass\": ").append(bass)
                .append(", \"position\": ").append(sh.position).append("}")
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

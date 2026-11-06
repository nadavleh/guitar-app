package app.guitar.theory.tools

import app.guitar.theory.PercussionBuiltins
import app.guitar.theory.PercussionCatalog
import app.guitar.theory.PercussionPattern
import app.guitar.theory.PercussionRender
import app.guitar.theory.SwingModel
import app.guitar.theory.WavFile
import java.io.File

/**
 * CLI front-end for [PercussionRender] — renders a drum-machine pattern to a WAV file
 * without a device or a browser:
 *
 *   .\gradlew :theory:renderPercussion --args="--bpm 90 --track surdo --out C:\...\surdo.wav"
 *
 * Same renderer the in-app "Export WAV" uses, so a file produced here is
 * sample-for-sample what the app exports at the same settings. Handy for checking a
 * groove in a DAW, and for regression-listening after a timing change.
 *
 * Options (all optional except --out):
 *   --out <path>       destination .wav                                (required)
 *   --pattern <name>   builtin groove name or an encoded pattern string (default: the
 *                      first builtin, i.e. what the drum machine loads on open)
 *   --bpm <n>          tempo                          (default: the builtin's own bpm)
 *   --track <id|all>   single instrument id, or "all" for the full kit    (default all)
 *   --cycles <n>       how many times the loop repeats                     (default 1)
 *   --swing <0..100>   global swing                                        (default 0)
 *   --tail             append the final ring-out instead of wrapping it around, i.e.
 *                      a one-shot ending rather than a seamless loop
 *   --samples <dir>    drum WAV folder     (default: app/src/main/assets/drums)
 */
object RenderPercussion {

    @JvmStatic
    fun main(args: Array<String>) {
        val opts = parseArgs(args)
        val out = opts["out"] ?: return fail("--out <path> is required")

        val (pattern, builtinBpm, patternName) = resolvePattern(opts["pattern"])
            ?: return fail("unknown --pattern '${opts["pattern"]}' (builtins: ${builtinNames()})")

        val bpm = opts["bpm"]?.toIntOrNull() ?: builtinBpm ?: 80
        val track = opts["track"]?.takeIf { it != "all" }
        if (track != null && pattern.instruments.none { it.id == track }) {
            return fail("pattern '$patternName' has no track '$track' (has: ${pattern.instruments.joinToString { it.id }})")
        }
        val samplesDir = File(opts["samples"] ?: defaultSamplesDir())
        if (!samplesDir.isDirectory) return fail("sample folder not found: $samplesDir")

        val spec = PercussionRender.Spec(
            pattern = pattern,
            bpm = bpm,
            swing = opts["swing"]?.toIntOrNull() ?: 0,
            swingModel = SwingModel.Default,
            onlyTrack = track,
            cycles = opts["cycles"]?.toIntOrNull() ?: 1,
            loopExact = !opts.containsKey("tail"),
        )
        val result = PercussionRender.render(spec, FolderVoiceBuffers(samplesDir, spec.sampleRate))

        val file = File(out).absoluteFile
        file.parentFile?.mkdirs()
        file.writeBytes(WavFile.encodeMono16(result.samples, result.sampleRate))

        println("wrote $file")
        println("  pattern : $patternName${track?.let { "  ·  track '$it' only" } ?: "  ·  full kit"}")
        println("  tempo   : $bpm bpm · swing ${spec.swing}% · ${spec.cycles} cycle(s) · ${pattern.meter.describe()}")
        println("  audio   : %.3f s · %d Hz · %d hit(s)".format(result.durationSec, result.sampleRate, result.hits))
        println("  ending  : " + if (spec.loopExact) "seamless loop (ring-out wraps to the start)" else "one-shot (ring-out appended)")
        if (result.safetyGain < 1f) {
            println("  NOTE    : mix peaked at %.2f — scaled by %.2f to fit the file".format(result.peak, result.safetyGain))
        }
        if (result.missingVoices.isNotEmpty()) {
            println("  MISSING : no sample for ${result.missingVoices.joinToString()} (those hits were skipped)")
        }
    }

    /** Loads `<dir>/<baseId>_<voice>.wav` on demand, mirroring the app's asset loader.
     *  Duplicated tracks ("surdo#2") share their base instrument's samples. */
    private class FolderVoiceBuffers(val dir: File, val sampleRate: Int) : PercussionRender.VoiceBuffers {
        private val cache = HashMap<String, FloatArray?>()
        override fun bufferFor(instrumentId: String, voice: Int): FloatArray? =
            cache.getOrPut("${PercussionCatalog.baseId(instrumentId)}_$voice") {
                val f = File(dir, "${PercussionCatalog.baseId(instrumentId)}_$voice.wav")
                if (!f.isFile) return@getOrPut null
                val decoded = WavFile.decodeMono16(f.readBytes()) ?: return@getOrPut null
                if (decoded.sampleRate == sampleRate) decoded.samples
                else resample(decoded.samples, decoded.sampleRate, sampleRate)
            }

        /** Linear resample — the bundled kit is already 44.1 kHz, so this is a guard
         *  for someone pointing --samples at a folder of their own recordings. */
        private fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
            val n = (src.size.toLong() * to / from).toInt().coerceAtLeast(1)
            val out = FloatArray(n)
            val step = from.toDouble() / to
            for (i in 0 until n) {
                val x = i * step
                val i0 = x.toInt().coerceAtMost(src.size - 1)
                val i1 = (i0 + 1).coerceAtMost(src.size - 1)
                val frac = (x - i0).toFloat()
                out[i] = src[i0] * (1 - frac) + src[i1] * frac
            }
            return out
        }
    }

    /** A builtin groove by name (case/space-insensitive), or a raw encoded pattern. */
    private fun resolvePattern(arg: String?): Triple<PercussionPattern, Int?, String>? {
        val builtins = PercussionBuiltins.ALL
        if (arg == null) return builtins[0].let { Triple(it.pattern, it.bpm, it.name) }
        builtins.firstOrNull { it.name.equals(arg, ignoreCase = true) }
            ?.let { return Triple(it.pattern, it.bpm, it.name) }
        PercussionPattern.decode(arg)?.let { return Triple(it, null, "(encoded)") }
        return null
    }

    private fun builtinNames() = PercussionBuiltins.ALL.joinToString(" | ") { "\"${it.name}\"" }

    /** Repo-relative default so the task works straight out of a clone. */
    private fun defaultSamplesDir(): String =
        File(System.getProperty("user.dir")).resolve("app/src/main/assets/drums").path

    /** `--key value` and bare `--flag` pairs; anything else is ignored. */
    private fun parseArgs(args: Array<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("--")) {
                val key = a.removePrefix("--")
                val next = args.getOrNull(i + 1)
                if (next != null && !next.startsWith("--")) { out[key] = next; i++ } else out[key] = ""
            }
            i++
        }
        return out
    }

    private fun fail(message: String) {
        System.err.println("render-percussion: $message")
        kotlin.system.exitProcess(1)
    }
}

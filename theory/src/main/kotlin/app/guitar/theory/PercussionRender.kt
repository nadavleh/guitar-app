package app.guitar.theory

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * OFFLINE mixdown of a percussion loop — the render behind "Export WAV".
 *
 * This deliberately re-implements what the live scheduler does per slot
 * (SambaLooperState.scheduleSlot + AudioEngine.playSamples) as a pure function of
 * time, so an export is not a recording of playback but a deterministic render:
 * no AudioContext, no threads, no dropouts, and identical output on both platforms.
 * Everything it needs — slot timing ([PercussionTiming.swungSlotMs]), accents,
 * per-slot dynamics, per-track volume and swing — already lives in this module.
 *
 * The one thing it CAN'T know is what a voice sounds like (bundled WAV vs. synth
 * fallback), so the caller supplies that through [VoiceBuffers].
 *
 * Pure Kotlin, no Android → unit-testable on the JVM and usable from the
 * `:theory:renderPercussion` CLI task.
 */
object PercussionRender {

    /** Accented hits play this much louder (mirrors the live scheduler). */
    const val ACCENT_GAIN = 1.4f

    /** A voice whose peak lands later than this is treated as a crescendo and started
     *  early, so its PEAK — not its onset — sits on the beat (mirrors the scheduler). */
    private const val CRESCENDO_PEAK_SEC = 0.02

    /** Self-choke fade: a new strike ramps the previous one on the same track to
     *  silence over this long, starting at the new hit's onset (engine.playSamples). */
    private const val CHOKE_FADE_SEC = 0.012

    /** One mono one-shot per (instrument id, voice index), already at the render rate.
     *  Return null for "no sound available" — that voice is then skipped. */
    fun interface VoiceBuffers {
        fun bufferFor(instrumentId: String, voice: Int): FloatArray?
    }

    /**
     * @param onlyTrack null renders the whole kit ("full cycle"); an instrument id
     *   renders that track ALONE (its own volume and swing still apply, so a solo
     *   stem sits at the same level it does in the mix).
     * @param loopExact true = the file is EXACTLY [cycles] cycles long and a hit's
     *   ring-out past the end wraps around to the start, which is precisely what you
     *   hear when the loop repeats — so the file loops seamlessly in a DAW. false =
     *   the final ring-out is appended instead, giving a clean one-shot ending.
     * @param voiceGain extra per-voice gain from the live mixer (voice volume
     *   sliders); 1f leaves the pattern as authored.
     * @param includeTrack the live mixer's mute/solo state, applied to a FULL-kit
     *   render only. A single-track export ignores it on purpose: you asked for that
     *   stem, so a muted track still renders rather than producing a silent file.
     */
    data class Spec(
        val pattern: PercussionPattern,
        val bpm: Int,
        val swing: Int = 0,
        val swingModel: SwingModel = SwingModel.Default,
        val onlyTrack: String? = null,
        val includeTrack: (String) -> Boolean = { true },
        val cycles: Int = 1,
        val loopExact: Boolean = true,
        val sampleRate: Int = 44_100,
        val voiceGain: (String, Int) -> Float = { _, _ -> 1f },
    ) {
        init {
            require(bpm in 10..300) { "bpm must be 10..300, got $bpm" }
            require(cycles >= 1) { "cycles must be >= 1, got $cycles" }
            require(sampleRate >= 8_000) { "sampleRate must be >= 8000, got $sampleRate" }
        }
    }

    /** What a render produced, for the caller to report to the user. */
    data class Result(
        val samples: FloatArray,
        val sampleRate: Int,
        /** Hits actually mixed in (a voice with no buffer is skipped, not counted). */
        val hits: Int,
        /** Voices that had no buffer at all — surfaced instead of silently dropped. */
        val missingVoices: List<String>,
        /** Peak BEFORE any safety scaling; > 1 means the mix was scaled down to fit. */
        val peak: Float,
    ) {
        val durationSec: Double get() = samples.size.toDouble() / sampleRate
        /** Safety gain applied to keep the file from clipping (1 = untouched). */
        val safetyGain: Float get() = if (peak > CEILING) CEILING / peak else 1f
    }

    /** Ceiling the mix is scaled to when it would otherwise clip the 16-bit file. */
    private const val CEILING = 0.999f

    /** One scheduled strike, resolved to sample positions. */
    private class Hit(
        val start: Int,
        val buffer: FloatArray,
        val gain: Float,
        /** Sample index at which the next strike on this track chokes it (or -1). */
        var chokeAt: Int = -1,
    )

    fun render(spec: Spec, buffers: VoiceBuffers): Result {
        val pattern = spec.pattern
        val meter = pattern.meter
        val slots = pattern.slots
        val sr = spec.sampleRate

        // Onset (ms from cycle start) of every slot, and the cycle's own length. Uses
        // the EXACT timing rather than the scheduler's rounded-to-whole-ms grid, so a
        // cycle is precisely its musical length and the file loops on a DAW's bar line
        // (see PercussionTiming.slotMsExact). The difference is under a millisecond per
        // hit — the swing feel is identical.
        // Anchored so slot 0 sits at sample 0: the swing model gives the downbeat its own
        // offset, and the live scheduler starts the loop AT slot 0 rather than at that
        // offset. A file whose first hit was ~20 ms in would never line up on a bar line.
        val slotZeroMs = PercussionTiming.swungOnsetMsExact(0, spec.bpm, spec.swing, meter, spec.swingModel)
        val slotOnsetMs = DoubleArray(slots) {
            PercussionTiming.swungOnsetMsExact(it, spec.bpm, spec.swing, meter, spec.swingModel) - slotZeroMs
        }
        val cycleMs = slots * PercussionTiming.slotMsExact(spec.bpm, meter.division)

        val tracks = pattern.instruments.filter {
            if (spec.onlyTrack != null) it.id == spec.onlyTrack else spec.includeTrack(it.id)
        }
        val missing = LinkedHashSet<String>()
        val perTrackHits = LinkedHashMap<String, MutableList<Hit>>()
        var hitCount = 0

        for (inst in tracks) {
            val hits = ArrayList<Hit>()
            val trackDeltaMs = trackOnsetDeltas(pattern, inst.id, spec)
            val trackVol = pattern.trackVolumeOf(inst.id) / 100f
            for (cycle in 0 until spec.cycles) {
                for (slot in 0 until slots) {
                    val voice = pattern.voiceAt(inst, slot) ?: continue
                    val buf = buffers.bufferFor(inst.id, voice)
                    if (buf == null || buf.isEmpty()) {
                        missing += "${inst.id}:$voice"
                        continue
                    }
                    val gain = trackVol *
                        spec.voiceGain(inst.id, voice) *
                        (if (pattern.isAccented(inst, slot)) ACCENT_GAIN else 1f) *
                        PERCUSSION_DYN_FACTORS[pattern.dynLevelAt(inst, slot)]
                    val onsetMs = cycle * cycleMs + slotOnsetMs[slot] + trackDeltaMs[slot]
                    var start = msToSamples(onsetMs, sr)
                    // Crescendo voices start early so their PEAK lands on the beat.
                    val peakOffset = peakOffsetSamples(buf)
                    if (peakOffset > CRESCENDO_PEAK_SEC * sr) start -= min(peakOffset, start)
                    hits += Hit(start, buf, gain)
                    hitCount++
                }
            }
            hits.sortBy { it.start }
            // A self-choking track (pandeiro) damps its previous stroke at each new hit.
            if (inst.selfChoke) {
                for (i in 0 until hits.size - 1) hits[i].chokeAt = hits[i + 1].start
            }
            perTrackHits[inst.id] = hits
        }

        val cycleSamples = msToSamples(cycleMs * spec.cycles, sr)
        val tailSamples = perTrackHits.values.flatten()
            .maxOfOrNull { it.start + it.buffer.size }?.minus(cycleSamples)?.coerceAtLeast(0) ?: 0
        val length = if (spec.loopExact) cycleSamples else cycleSamples + tailSamples
        val out = FloatArray(max(length, 1))

        for (hits in perTrackHits.values) {
            for (hit in hits) writeHit(out, hit, sr, wrap = spec.loopExact)
        }

        var peak = 0f
        for (v in out) peak = max(peak, abs(v))
        if (peak > CEILING) {
            val g = CEILING / peak
            for (i in out.indices) out[i] *= g
        }
        return Result(out, sr, hitCount, missing.toList(), peak)
    }

    /**
     * Per-TRACK swing offsets (ms) for every slot. A track with its own swing walks its
     * own micro-timing clock, but ONLY while the beat's global swing is 0 (a nonzero
     * global swing overrides all track values) — the same rule the live scheduler uses.
     */
    private fun trackOnsetDeltas(pattern: PercussionPattern, id: String, spec: Spec): DoubleArray {
        val slots = pattern.slots
        val deltas = DoubleArray(slots)
        val trackSwing = pattern.trackSwing[id] ?: return deltas
        if (spec.swing != 0 || trackSwing == 0) return deltas
        // Both clocks are measured from their OWN slot 0, so the two grids start together
        // and the track's hits drift from the master only by its swing (mirrors the live
        // scheduler, which accumulates per-slot differences from a shared downbeat).
        fun onset(k: Int, swing: Int) =
            PercussionTiming.swungOnsetMsExact(k, spec.bpm, swing, pattern.meter, spec.swingModel) -
                PercussionTiming.swungOnsetMsExact(0, spec.bpm, swing, pattern.meter, spec.swingModel)
        for (k in 0 until slots) deltas[k] = onset(k, trackSwing) - onset(k, spec.swing)
        return deltas
    }

    /** Mix one strike into [out], applying its choke fade and (optionally) wrapping
     *  any ring-out past the end back to the start so the file loops seamlessly. */
    private fun writeHit(out: FloatArray, hit: Hit, sampleRate: Int, wrap: Boolean) {
        val fade = max((CHOKE_FADE_SEC * sampleRate).roundToInt(), 1)
        for (i in hit.buffer.indices) {
            val at = hit.start + i
            if (!wrap && at >= out.size) break
            var env = hit.gain
            if (hit.chokeAt >= 0) {
                val since = at - hit.chokeAt
                if (since >= fade) break                       // fully damped — nothing left to write
                if (since >= 0) env *= 1f - since.toFloat() / fade
            }
            val idx = if (wrap) ((at % out.size) + out.size) % out.size else at
            out[idx] += hit.buffer[i] * env
        }
    }

    /** Sample offset of the buffer's first near-peak sample (crescendo detection);
     *  mirrors SambaLooperState.peakOffsetSec. */
    private fun peakOffsetSamples(buf: FloatArray): Int {
        var peak = 0f
        for (v in buf) peak = max(peak, abs(v))
        if (peak <= 0f) return 0
        val threshold = peak * 0.9f
        var i = 0
        while (i < buf.size && abs(buf[i]) < threshold) i++
        return i
    }

    private fun msToSamples(ms: Double, sampleRate: Int): Int =
        Math.round(ms * sampleRate / 1000.0).toInt()
}

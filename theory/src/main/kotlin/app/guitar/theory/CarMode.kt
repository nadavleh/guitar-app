package app.guitar.theory

/**
 * Timing + reveal schedule for hands-free "Car mode" ear training.
 *
 * Pure data: no audio, no coroutines, no UI — the platform state layers drive it
 * (Android `EarTrainingState.carDriver`, web `earTrainingState.ts`). It lives in the
 * theory module because that is where the unit tests are; everything about car mode
 * that can be *derived* rather than *timed* belongs here, so both platforms compute
 * the same schedule from the same numbers.
 *
 * One exercise: [BEEPS] lead-in beeps [BEEP_GAP_MS] apart, then the progression
 * sounds [ROUNDS] times, revealing one more chord per round from the 2nd onward.
 * Never graded — the driver self-assesses during [GAP_MS].
 */
object CarMode {
    /** Progression passes per exercise. */
    const val ROUNDS = 5

    /** Lead-in beeps announcing that a new exercise has begun. */
    const val BEEPS = 3

    /** Beep onset-to-onset spacing, and also beep-3 → chord-1, giving "3-2-1-go". */
    const val BEEP_GAP_MS = 500

    /** Silence from the first beep to the first chord. */
    const val LEAD_IN_MS = BEEPS * BEEP_GAP_MS

    /** Silent self-assessment gap after the last round, before auto-advancing. */
    const val GAP_MS = 4000

    /** A5 — deliberately ABOVE the progression voicings (MIDI 45-70 ≈ 110-490 Hz)
     *  and above the sub-500 Hz road-noise hump, so the cue cuts through a car
     *  cabin without being shrill. */
    const val BEEP_HZ = 880.0
    const val BEEP_MS = 140
    const val BEEP_PEAK = 0.55f

    /** A raw sine onset clicks; this much linear attack removes it. */
    const val BEEP_ATTACK_MS = 5

    /**
     * How many chord slots are revealed while [round] (1-based) is sounding.
     * Round 1 reveals nothing (guess blind); for the canonical 4-chord progression each
     * later round reveals exactly one more, and the last round always shows everything.
     * Round 0 — idle, nothing started — reveals nothing.
     *
     * This is the ONLY source of the reveal count: the state layers expose it as a
     * derived getter rather than storing a set, so there is no "forgot to clear it"
     * bug to have.
     */
    fun revealedSlots(round: Int, slotCount: Int): Int {
        if (round <= 1 || slotCount <= 0) return 0
        if (round >= ROUNDS) return slotCount
        // One more slot per round for the canonical 4-bar progression. Spread instead of
        // stepping by 1 so a LONGER progression still reaches a full reveal by the last
        // round: the advanced library has 6-, 7- and 8-chord entries, and stepping by one
        // left Pachelbel's Canon showing only 4 of its 8 chords when the exercise ended.
        return minOf(slotCount, Math.ceil((round - 1).toDouble() * slotCount / (ROUNDS - 1)).toInt())
    }

    /**
     * How many leading slots are revealed at this instant, given the playhead is on
     * [playheadSlot] (0-based; negative during the lead-in) of [round].
     *
     * [revealedSlots] says how many slots round [round] is ALLOWED to give away; this
     * says how many it has given away SO FAR. A round's newly-earned slots appear one
     * at a time, as the playhead reaches them — hearing the chord and reading its
     * function at the same instant is the whole point, and dumping the new slot at the
     * top of the round let you read ahead of the sound. Slots earned in EARLIER rounds
     * stay up (the `held` floor), so nothing ever un-reveals mid-exercise.
     *
     * Still derived, never stored: round + playhead are the only inputs.
     */
    fun revealedSlotsAt(round: Int, playheadSlot: Int, slotCount: Int): Int {
        if (round <= 1 || slotCount <= 0) return 0
        val target = revealedSlots(round, slotCount)
        val held = revealedSlots(round - 1, slotCount)   // already given away, stays up
        val reached = (playheadSlot + 1).coerceIn(0, slotCount)
        return minOf(target, maxOf(held, reached))
    }

    /**
     * Wall-clock ms of one exercise at [bpm] over [slotCount] bars, excluding the
     * trailing [GAP_MS]. Drives the "≈40 s per exercise" caption; [bpm] is clamped
     * so a nonsense tempo can't divide by zero.
     */
    fun exerciseMs(bpm: Int, slotCount: Int): Long =
        LEAD_IN_MS + ROUNDS.toLong() * slotCount * (60_000L / bpm.coerceAtLeast(10)) * 4

    /**
     * Default playback volume of the spoken chord label, 0..1 — the starting point of a
     * user-facing slider, not a fixed level.
     *
     * The voice is an overdub ON TOP of the looper, not a replacement for it: neither
     * platform ducks the music, because the point of the drill is to hear the chord.
     * It started at 0.35 on that reasoning and was simply inaudible in a moving car, so
     * the default now sits high and the slider is what trades intelligibility against
     * masking. Both platforms clamp to the same range, and 1.0 is a hard ceiling on both
     * (Android KEY_PARAM_VOLUME and SpeechSynthesisUtterance.volume are each capped
     * there) — a louder voice than this needs the device volume, not a bigger number.
     */
    const val SPEECH_VOLUME = 0.9f

    /** Slider bounds for the spoken label. The floor is audible-but-quiet rather than 0:
     *  silencing the voice is what the toggle is for. */
    const val SPEECH_VOLUME_MIN = 0.1f
    const val SPEECH_VOLUME_MAX = 1.0f

    /** [v] clamped into the slider's range. Shared so a persisted or hand-edited value
     *  can never hand the platform TTS an out-of-range volume. */
    fun clampSpeechVolume(v: Float): Float = v.coerceIn(SPEECH_VOLUME_MIN, SPEECH_VOLUME_MAX)

    /** Roman numerals 1..7, longest-first so "VII" wins over "V" / "VI". */
    private val NUMERALS = listOf("VII" to 7, "VI" to 6, "IV" to 4, "V" to 5, "III" to 3, "II" to 2, "I" to 1)

    /** Chord-suffix numbers that make an UPPERCASE numeral a dominant rather than a plain
     *  major: V7, V9, V11, V13. "I6" / "Iadd9" stay major — a 6th or an add9 is colour,
     *  not a dominant. */
    private val DOMINANT_SUFFIXES = setOf("7", "9", "11", "13")

    /**
     * Spoken form of a Roman-numeral FUNCTION label, for the car-mode voice.
     *
     * The numeral becomes a spoken degree number and the case becomes a spoken quality,
     * because "four minor" is unambiguous over road noise where "iv" and "IV" sound
     * identical — that ambiguity is the whole reason this exists:
     *
     *   IV -> "4 major"      iv       -> "4 minor"       vii deg -> "7 diminished"
     *   i7 -> "1 minor 7"    bVImaj7  -> "flat 6 major 7"
     *   V7 -> "5 dominant 7" #IV(deg)7 -> "sharp 4 diminished 7"
     *
     * Pure string work with no TTS dependency, so both platforms speak the same words
     * and the mapping is unit-testable. Returns "" for a label it cannot parse (the
     * caller then says nothing rather than reading gibberish aloud).
     */
    fun speechFor(roman: String): String {
        var rest = roman.trim()
        if (rest.isEmpty()) return ""
        val out = StringBuilder()

        // Leading accidental — bVI, #IV.
        when (rest.firstOrNull()) {
            'b' -> { out.append("flat "); rest = rest.substring(1) }
            '#' -> { out.append("sharp "); rest = rest.substring(1) }
        }

        val numeral = NUMERALS.firstOrNull { rest.startsWith(it.first, ignoreCase = true) } ?: return ""
        val upper = rest[0].isUpperCase()
        rest = rest.substring(numeral.first.length)
        out.append(numeral.second)

        // Quality: from the case, unless the suffix declares one of its own.
        var quality = if (upper) "major" else "minor"
        when {
            rest.startsWith("°") || rest.startsWith("dim") ->
                { quality = "diminished"; rest = rest.removePrefix("°").removePrefix("dim") }
            rest.startsWith("ø") -> { quality = "half diminished"; rest = rest.substring(1) }
            rest.startsWith("+") || rest.startsWith("aug") ->
                { quality = "augmented"; rest = rest.removePrefix("+").removePrefix("aug") }
            rest.startsWith("maj") -> { quality = "major"; rest = rest.substring(3) }
            rest.startsWith("sus") -> { quality = "suspended"; rest = rest.substring(3) }
            upper && rest in DOMINANT_SUFFIXES -> quality = "dominant"
        }
        out.append(' ').append(quality)

        // Whatever is left is colour: numbers, accidentals, "add".
        var i = 0
        while (i < rest.length) {
            val c = rest[i]
            when {
                c.isDigit() -> {
                    var j = i
                    while (j < rest.length && rest[j].isDigit()) j++
                    out.append(' ').append(rest, i, j); i = j
                }
                c == 'b' -> { out.append(" flat"); i++ }
                c == '#' -> { out.append(" sharp"); i++ }
                rest.startsWith("add", i) -> { out.append(" add"); i += 3 }
                rest.startsWith("sus", i) -> { out.append(" suspended"); i += 3 }
                c == '°' -> { out.append(" diminished"); i++ }
                else -> i++   // punctuation / anything unspeakable
            }
        }
        return out.toString()
    }
}

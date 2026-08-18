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
     * Round 1 reveals nothing (guess blind), each later round reveals one more,
     * capped at [slotCount]. Round 0 — idle, nothing started — reveals nothing.
     *
     * This is the ONLY source of the reveal count: the state layers expose it as a
     * derived getter rather than storing a set, so there is no "forgot to clear it"
     * bug to have.
     */
    fun revealedSlots(round: Int, slotCount: Int): Int =
        (round - 1).coerceIn(0, slotCount)

    /**
     * Wall-clock ms of one exercise at [bpm] over [slotCount] bars, excluding the
     * trailing [GAP_MS]. Drives the "≈40 s per exercise" caption; [bpm] is clamped
     * so a nonsense tempo can't divide by zero.
     */
    fun exerciseMs(bpm: Int, slotCount: Int): Long =
        LEAD_IN_MS + ROUNDS.toLong() * slotCount * (60_000L / bpm.coerceAtLeast(10)) * 4
}

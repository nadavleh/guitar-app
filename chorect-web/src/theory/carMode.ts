// Timing + reveal schedule for hands-free "Car mode" ear training.
// Mirrors theory/src/main/kotlin/app/guitar/theory/CarMode.kt — keep the numbers
// identical; chorect-web/test/verify.ts pins them against the Kotlin values.
//
// Pure data: no audio, no timers, no DOM — earTrainingState.ts drives it. One
// exercise: BEEPS lead-in beeps BEEP_GAP_MS apart, then the progression sounds
// ROUNDS times, revealing one more chord per round from the 2nd onward. Never graded.

/** Progression passes per exercise. */
export const CAR_ROUNDS = 5;

/** Lead-in beeps announcing that a new exercise has begun. */
export const CAR_BEEPS = 3;

/** Beep onset-to-onset spacing, and also beep-3 → chord-1, giving "3-2-1-go". */
export const CAR_BEEP_GAP_MS = 500;

export const CarMode = {
  ROUNDS: CAR_ROUNDS,
  BEEPS: CAR_BEEPS,
  BEEP_GAP_MS: CAR_BEEP_GAP_MS,
  /** Silence from the first beep to the first chord. */
  LEAD_IN_MS: CAR_BEEPS * CAR_BEEP_GAP_MS,
  /** Silent self-assessment gap after the last round, before auto-advancing. */
  GAP_MS: 4000,
  /** A5 — deliberately ABOVE the progression voicings (MIDI 45-70 ≈ 110-490 Hz)
   *  and above the sub-500 Hz road-noise hump, so the cue cuts through a car
   *  cabin without being shrill. */
  BEEP_HZ: 880,
  BEEP_MS: 140,
  BEEP_PEAK: 0.55,
  /** A raw sine onset clicks; this much linear attack removes it. */
  BEEP_ATTACK_MS: 5,

  /**
   * How many chord slots are revealed while `round` (1-based) is sounding.
   * Round 1 reveals nothing (guess blind); for the canonical 4-chord progression each
   * later round reveals exactly one more, and the last round always shows everything.
   * Round 0 — idle, nothing started — reveals nothing.
   *
   * The ONLY source of the reveal count: the state layer exposes it as a derived
   * getter rather than storing a set, so there is no "forgot to clear it" bug.
   */
  revealedSlots(round: number, slotCount: number): number {
    if (round <= 1 || slotCount <= 0) return 0;
    if (round >= CAR_ROUNDS) return slotCount;
    // One more slot per round for the canonical 4-bar progression. Spread instead of
    // stepping by 1 so a LONGER progression still reaches a full reveal by the last
    // round: the advanced library has 6-, 7- and 8-chord entries, and stepping by one
    // left Pachelbel's Canon showing only 4 of its 8 chords when the exercise ended.
    return Math.min(slotCount, Math.ceil((round - 1) * slotCount / (CAR_ROUNDS - 1)));
  },

  /**
   * Wall-clock ms of one exercise at `bpm` over `slotCount` bars, excluding the
   * trailing GAP_MS. Drives the "≈40 s per exercise" caption; `bpm` is clamped so
   * a nonsense tempo can't divide by zero. Integer-truncating division matches
   * Kotlin's `60_000L / Int` exactly.
   */
  exerciseMs(bpm: number, slotCount: number): number {
    const barQuarter = Math.trunc(60_000 / Math.max(bpm, 10));
    return CarMode.LEAD_IN_MS + CAR_ROUNDS * slotCount * barQuarter * 4;
  },
} as const;

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

/** Roman numerals 1..7, longest-first so "VII" wins over "V" / "VI". */
const CAR_NUMERALS: ReadonlyArray<readonly [string, number]> = [
  ["VII", 7], ["VI", 6], ["IV", 4], ["V", 5], ["III", 3], ["II", 2], ["I", 1],
];

/** Chord-suffix numbers that make an UPPERCASE numeral a dominant rather than a plain
 *  major: V7, V9, V11, V13. "I6" / "Iadd9" stay major — a 6th or an add9 is colour,
 *  not a dominant. */
const CAR_DOMINANT_SUFFIXES = new Set(["7", "9", "11", "13"]);

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
   * How many leading slots are revealed at this instant, given the playhead is on
   * `playheadSlot` (0-based; negative during the lead-in) of `round`.
   *
   * `revealedSlots` says how many slots this round is ALLOWED to give away; this says
   * how many it has given away SO FAR. A round's newly-earned slots appear one at a
   * time, as the playhead reaches them — hearing the chord and reading its function at
   * the same instant is the whole point, and dumping the new slot at the top of the
   * round let you read ahead of the sound. Slots earned in EARLIER rounds stay up (the
   * `held` floor), so nothing ever un-reveals mid-exercise. Still derived, never stored.
   */
  revealedSlotsAt(round: number, playheadSlot: number, slotCount: number): number {
    if (round <= 1 || slotCount <= 0) return 0;
    const target = CarMode.revealedSlots(round, slotCount);
    const held = CarMode.revealedSlots(round - 1, slotCount);   // already given away
    const reached = Math.min(Math.max(playheadSlot + 1, 0), slotCount);
    return Math.min(target, Math.max(held, reached));
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
  SPEECH_VOLUME: 0.9,

  /** Slider bounds for the spoken label. The floor is audible-but-quiet rather than 0:
   *  silencing the voice is what the toggle is for. */
  SPEECH_VOLUME_MIN: 0.1,
  SPEECH_VOLUME_MAX: 1.0,

  /** `v` clamped into the slider's range. Shared so a persisted or hand-edited value can
   *  never hand the platform TTS an out-of-range volume. */
  clampSpeechVolume(v: number): number {
    return Math.min(Math.max(v, CarMode.SPEECH_VOLUME_MIN), CarMode.SPEECH_VOLUME_MAX);
  },

  /**
   * Spoken form of a Roman-numeral FUNCTION label, for the car-mode voice.
   *
   * The numeral becomes a spoken degree number and the case becomes a spoken quality,
   * because "four minor" is unambiguous over road noise where "iv" and "IV" sound
   * identical — that ambiguity is the whole reason this exists:
   *
   *   IV → "4 major"      iv      → "4 minor"       vii°  → "7 diminished"
   *   i7 → "1 minor 7"    bVImaj7 → "flat 6 major 7"
   *   V7 → "5 dominant 7" #IV°7   → "sharp 4 diminished 7"
   *
   * Pure string work with no speechSynthesis dependency, so both platforms speak the
   * same words and the mapping is unit-testable. Returns "" for a label it cannot
   * parse (the caller then says nothing rather than reading gibberish aloud).
   */
  speechFor(roman: string): string {
    let rest = roman.trim();
    if (!rest) return "";
    let out = "";

    // Leading accidental — bVI, #IV.
    if (rest[0] === "b") { out += "flat "; rest = rest.slice(1); }
    else if (rest[0] === "#") { out += "sharp "; rest = rest.slice(1); }

    const numeral = CAR_NUMERALS.find((n) => rest.toUpperCase().startsWith(n[0]));
    if (!numeral) return "";
    const upper = rest[0] === rest[0].toUpperCase();
    rest = rest.slice(numeral[0].length);
    out += String(numeral[1]);

    // Quality: from the case, unless the suffix declares one of its own.
    let quality = upper ? "major" : "minor";
    if (rest.startsWith("°") || rest.startsWith("dim")) {
      quality = "diminished";
      rest = rest.startsWith("°") ? rest.slice(1) : rest.slice(3);
    } else if (rest.startsWith("ø")) { quality = "half diminished"; rest = rest.slice(1); }
    else if (rest.startsWith("+")) { quality = "augmented"; rest = rest.slice(1); }
    else if (rest.startsWith("aug")) { quality = "augmented"; rest = rest.slice(3); }
    else if (rest.startsWith("maj")) { quality = "major"; rest = rest.slice(3); }
    else if (rest.startsWith("sus")) { quality = "suspended"; rest = rest.slice(3); }
    else if (upper && CAR_DOMINANT_SUFFIXES.has(rest)) quality = "dominant";
    out += " " + quality;

    // Whatever is left is colour: numbers, accidentals, "add".
    let i = 0;
    while (i < rest.length) {
      const c = rest[i];
      if (c >= "0" && c <= "9") {
        let j = i;
        while (j < rest.length && rest[j] >= "0" && rest[j] <= "9") j++;
        out += " " + rest.slice(i, j); i = j;
      } else if (c === "b") { out += " flat"; i++; }
      else if (c === "#") { out += " sharp"; i++; }
      else if (rest.startsWith("add", i)) { out += " add"; i += 3; }
      else if (rest.startsWith("sus", i)) { out += " suspended"; i += 3; }
      else if (c === "°") { out += " diminished"; i++; }
      else i++;   // punctuation / anything unspeakable
    }
    return out;
  },
} as const;

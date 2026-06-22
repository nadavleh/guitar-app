// "Note 2 Chord" ear-training challenge, ported from theory/.../Note2Chord.kt.

import { PitchClass, pcOf, spellPc } from "./core";
import { Rng, defaultRng } from "./random";

export interface N2cChallenge {
  chordRoot: PitchClass;
  isMinor: boolean;
  testNoteOffsetSemitones: number;
}

/** Degree label for a semitone offset above the chord root. */
export function n2cLabel(offset: number): string {
  switch (offset) {
    case 0: return "1 (root)";
    case 2: return "9 (2)";
    case 3: return "b3";
    case 4: return "3";
    case 5: return "11 (4)";
    case 7: return "5";
    case 8: return "b13 (b6)";
    case 9: return "13 (6)";
    case 10: return "b7";
    case 11: return "maj7";
    default: return "?";
  }
}

export const N2C_MAJOR_TEST_OFFSETS = [2, 5, 9, 11];
export const N2C_MINOR_TEST_OFFSETS = [2, 5, 8, 10];

export function n2cTestNote(c: N2cChallenge): PitchClass {
  return pcOf(c.chordRoot + c.testNoteOffsetSemitones);
}
export function n2cChordSymbol(c: N2cChallenge): string {
  return spellPc(c.chordRoot) + (c.isMinor ? "m" : "");
}
export function n2cAnswerLabel(c: N2cChallenge): string {
  return n2cLabel(c.testNoteOffsetSemitones);
}
export function n2cTestNoteName(c: N2cChallenge): string {
  return spellPc(n2cTestNote(c));
}

export function randomN2c(rng: Rng = defaultRng): N2cChallenge {
  const chordRoot = rng.int(12);
  const isMinor = rng.bool();
  const offsets = isMinor ? N2C_MINOR_TEST_OFFSETS : N2C_MAJOR_TEST_OFFSETS;
  return { chordRoot, isMinor, testNoteOffsetSemitones: offsets[rng.int(offsets.length)] };
}

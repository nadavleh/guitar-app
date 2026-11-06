// Chord-inversion helpers for the ear-training Inversions trainer, ported from
// theory/.../Inversions.kt.

import { ChordQuality } from "./chords";

export function inversionCount(quality: ChordQuality): number {
  return quality.intervals.length;
}

export function inversionName(k: number): string {
  switch (k) {
    case 0: return "Root position";
    case 1: return "1st inversion";
    case 2: return "2nd inversion";
    case 3: return "3rd inversion";
    default: return `${k}th inversion`;
  }
}

/** MIDI notes for [quality] rooted at [rootMidi], voiced in [inversion], low→high. */
export function inversionMidis(rootMidi: number, quality: ChordQuality, inversion: number): number[] {
  const notes = quality.intervals.map((iv) => rootMidi + iv);
  const k = Math.min(Math.max(inversion, 0), notes.length - 1);
  for (let i = 0; i < k; i++) {
    const low = notes.shift()!;
    notes.push(low + 12);
  }
  return notes.slice().sort((a, b) => a - b);
}

/** True when every inversion of `quality` is a mere transposition of root position
 *  (aug, dim7): with the root unknown they sound identical, so the trainer can't ask
 *  which one it is. Mirrors Inversions.isSymmetric. */
export function inversionIsSymmetric(quality: ChordQuality): boolean {
  const shape = (k: number) => { const m = inversionMidis(0, quality, k); return m.map((x) => x - m[0]).join(","); };
  const n = inversionCount(quality);
  if (n <= 1) return false;
  const root = shape(0);
  for (let k = 1; k < n; k++) if (shape(k) !== root) return false;
  return true;
}

/** Whether guessing inversion `guess` for a chord voiced in `actual` is right: exact
 *  match, or ANY inversion of a symmetric quality. Mirrors Inversions.isCorrectGuess. */
export function inversionGuessCorrect(quality: ChordQuality, guess: number, actual: number): boolean {
  return guess === actual || inversionIsSymmetric(quality);
}

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

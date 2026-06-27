// How an extended/altered chord splits into a shell (root + guide tones) and an
// upper-structure triad — the "shell + triad" way of voicing extensions (#5).
// Ported from theory/.../ChordDecomposition.kt. Intervals are semitones from the
// chord root; upper-structure intervals may exceed 12 (they sit an octave up).

export interface ChordDecomposition {
  quality: string;       // picker symbol ("", "maj7", "13"…)
  displayName: string;   // short picker label
  shell: number[];       // bass group — semitone intervals from the root
  upper: number[];       // upper triad — semitone intervals from the root (ascending)
  upperTriad: string;    // "major" | "minor" | "diminished" | "augmented"
  upperDegrees: string;  // e.g. "5–♭7–9"
}

function d(
  quality: string, displayName: string,
  shell: number[], upper: number[], upperTriad: string, upperDegrees: string,
): ChordDecomposition {
  return { quality, displayName, shell, upper, upperTriad, upperDegrees };
}

/** Supported chords, in picker order. 9=14, ♯9=15, ♭9=13, 11=17, ♯11=18, 13=21. */
export const CHORD_DECOMPOSITIONS: ChordDecomposition[] = [
  d("6", "6", [0], [9, 12, 16], "minor", "6–1–3"),
  d("m6", "m6", [0], [9, 12, 15], "diminished", "6–1–♭3"),
  d("maj7", "maj7", [0], [4, 7, 11], "minor", "3–5–7"),
  d("7", "7", [0], [4, 7, 10], "diminished", "3–5–♭7"),
  d("m7", "m7", [0], [3, 7, 10], "major", "♭3–5–♭7"),
  d("m7b5", "m7♭5", [0], [3, 6, 10], "minor", "♭3–♭5–♭7"),
  d("dim7", "°7", [0], [3, 6, 9], "diminished", "♭3–♭5–6"),
  d("9", "9", [0, 4, 10], [7, 10, 14], "minor", "5–♭7–9"),
  d("maj9", "maj9", [0, 4, 11], [7, 11, 14], "major", "5–7–9"),
  d("m9", "m9", [0, 3, 10], [7, 10, 14], "minor", "5–♭7–9"),
  d("11", "11", [0, 10], [10, 14, 17], "major", "♭7–9–11"),
  d("m11", "m11", [0, 3, 10], [10, 14, 17], "major", "♭7–9–11"),
  d("13", "13", [0, 4, 10], [14, 18, 21], "major", "9–♯11–13"),
  d("maj13", "maj13", [0, 4, 11], [14, 18, 21], "major", "9–♯11–13"),
  d("7#9", "7♯9", [0, 4], [3, 7, 10], "major", "♯9–5–♭7"),
  d("7b9", "7♭9", [0, 10], [1, 4, 7], "diminished", "♭9–3–5"),
];

const byQuality = new Map(CHORD_DECOMPOSITIONS.map((c) => [c.quality, c]));

export function decompositionFor(quality: string): ChordDecomposition | undefined {
  return byQuality.get(quality);
}

/** Upper-triad root as a pitch-class interval from the chord root (lowest upper note). */
export function upperRootInterval(dec: ChordDecomposition): number {
  return Math.min(...dec.upper) % 12;
}

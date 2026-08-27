// The 34 CAGED shapes of the "Scales & Triads" trainer, transcribed dot-for-dot
// from Nadav's hand-drawn sheet (archived as docs/caged-shapes-source.md).
// Mirror of theory/src/main/kotlin/app/guitar/theory/CagedShapeTable.kt - see
// that file's header for the notation and for the four corrections applied to
// the sheet (A-minor box 1, D# in minor pentatonic box 1, minor pentatonic box 4
// duplicating box 3, minor triad box 4's low-E note).
//
// These REPLACE the old fret-window generator: a window like [T-1, T+2] sweeps up
// every scale tone it contains, which is close to the real fingering but not equal
// to it. The table is the source of truth; nothing is derived any more.

import { PitchClass, Tuning, midiPitchClass } from "./core";

// The three enums live HERE rather than in cagedScales.ts purely to break an
// import cycle: the table is built at module load and needs their values, while
// cagedScales needs the table. cagedScales re-exports all three, so every other
// module still imports them from there.

/** The 5 CAGED positions, low to high, each named for the open-chord shape it
 *  contains (box 1 = E shape, 2 = D, 3 = C, 4 = A, 5 = G). */
export enum CagedBox { POS1 = "POS1", POS2 = "POS2", POS3 = "POS3", POS4 = "POS4", POS5 = "POS5" }

/** Which note-subset of the box to show/play. */
export enum ScaleSubset { Triad = "Triad", Pentatonic = "Pentatonic", FullScale = "FullScale" }

/** Major, or PARALLEL minor (same root, natural minor) — not the relative minor. */
export enum CagedMode { Major = "Major", Minor = "Minor" }

export const CAGED_BOXES: CagedBox[] = [CagedBox.POS1, CagedBox.POS2, CagedBox.POS3, CagedBox.POS4, CagedBox.POS5];

/** The open-chord shape each box is named for, and its 1-based number. */
export const CAGED_SHAPE_LETTER: Record<CagedBox, string> = {
  [CagedBox.POS1]: "E", [CagedBox.POS2]: "D", [CagedBox.POS3]: "C",
  [CagedBox.POS4]: "A", [CagedBox.POS5]: "G",
};
export function boxNumber(box: CagedBox): number { return CAGED_BOXES.indexOf(box) + 1; }

/** One dot: string 0 = low E, offset relative to the key's low-E root fret. */
export interface ShapeDot { string: number; offset: number; isRoot: boolean }

/** Open-string letters in the shape notation, low to high. */
const STRING_LETTERS = ["E", "A", "D", "G", "B", "e"];

/** `"E:-1,0*,2 | A:..."` -> dots. Throws on an unknown string letter so a typo
 *  fails `npm run verify` rather than silently dropping notes. */
export function parseShape(spec: string): ShapeDot[] {
  const out: ShapeDot[] = [];
  for (const group of spec.split("|")) {
    const i = group.indexOf(":");
    const letter = group.slice(0, i).trim();
    const s = STRING_LETTERS.indexOf(letter);
    if (s < 0) throw new Error(`unknown string '${letter}' in shape spec '${spec}'`);
    for (const tok of group.slice(i + 1).split(",")) {
      const t = tok.trim();
      const isRoot = t.endsWith("*");
      out.push({ string: s, offset: parseInt(isRoot ? t.slice(0, -1) : t, 10), isRoot });
    }
  }
  return out;
}

/** Key of one diagram; pattern is 1, or 2 for the second (3-notes-per-string)
 *  fingering that only boxes 1 and 4 carry. */
export function shapeKey(box: CagedBox, mode: CagedMode, subset: ScaleSubset, pattern = 1): string {
  return `${box}|${mode}|${subset}|${pattern}`;
}

function shape(box: CagedBox, mode: CagedMode, subset: ScaleSubset, pattern: number, spec: string): [string, ShapeDot[]] {
  return [shapeKey(box, mode, subset, pattern), parseShape(spec)];
}

/** All 34 shapes, exactly as drawn (after the four corrections). */
export const CAGED_SHAPES: Map<string, ShapeDot[]> = new Map([
  // ---- Box 1 - CAGED shape E ----
  shape(CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, 1,
    "E:-1,0*,2 | A:-1,0,2 | D:-1,1,2* | G:-1,1,2 | B:0,2 | e:-1,0*,2"),   // Major scale box 1 pattern 1
  shape(CagedBox.POS1, CagedMode.Major, ScaleSubset.FullScale, 2,
    "E:0*,2,4 | A:0,2,4 | D:1,2*,4 | G:1,2,4 | B:2,4 | e:0*,2,4"),   // Major scale box 1 pattern 2
  shape(CagedBox.POS1, CagedMode.Major, ScaleSubset.Pentatonic, 1,
    "E:0*,2 | A:-1,2 | D:-1,2* | G:-1,1 | B:0,2 | e:0*,2"),   // Major pentatonic scale box 1
  shape(CagedBox.POS1, CagedMode.Major, ScaleSubset.Triad, 1,
    "E:0*,4 | A:-1,2 | D:2* | G:1 | B:0 | e:0*,4"),   // Major triad box 1
  shape(CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, 1,
    "E:-2,0*,2 | A:-2,0,2 | D:-2,0,2* | G:-1,0,2 | B:0,1 | e:-2,0*,2"),   // Minor scale box 1 pattern 1
  shape(CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, 2,
    "E:0*,2,3 | A:0,2,3 | D:0,2*,4 | G:0,2 | B:0,1,3 | e:0*,2,3"),   // Minor scale box 1 pattern 2
  shape(CagedBox.POS1, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
    "E:0*,3 | A:0,2 | D:0,2* | G:0,2 | B:0,3 | e:0*,3"),   // Minor pentatonic scale box 1
  shape(CagedBox.POS1, CagedMode.Minor, ScaleSubset.Triad, 1,
    "E:0*,3 | A:-2,2 | D:2* | G:0 | B:0 | e:0*,3"),   // Minor triad box 1

  // ---- Box 2 - CAGED shape D ----
  shape(CagedBox.POS2, CagedMode.Major, ScaleSubset.FullScale, 1,
    "E:2,4,5 | A:2,4,6 | D:2*,4,6 | G:2,4 | B:2,4,5* | e:2,4,5"),   // Major scale box 2 pattern 1
  shape(CagedBox.POS2, CagedMode.Major, ScaleSubset.Pentatonic, 1,
    "E:2,4 | A:2,4 | D:2*,4 | G:1,4 | B:2,5* | e:2,4"),   // Major pentatonic scale box 2
  shape(CagedBox.POS2, CagedMode.Major, ScaleSubset.Triad, 1,
    "E:4 | A:2 | D:2*,6 | G:1,4 | B:5* | e:4"),   // Major triad box 2
  shape(CagedBox.POS2, CagedMode.Minor, ScaleSubset.FullScale, 1,
    "E:2,3,5 | A:2,3,5 | D:2*,4,5 | G:2,4,5 | B:3,5* | e:2,3,5"),   // Minor scale box 2 pattern 1
  shape(CagedBox.POS2, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
    "E:3,5 | A:2,5 | D:2*,5 | G:2,4 | B:3,5* | e:3,5"),   // Minor pentatonic scale box 2
  shape(CagedBox.POS2, CagedMode.Minor, ScaleSubset.Triad, 1,
    "E:3 | A:2 | D:2*,5 | G:0,4 | B:5* | e:3"),   // Minor triad box 2

  // ---- Box 3 - CAGED shape C ----
  shape(CagedBox.POS3, CagedMode.Major, ScaleSubset.FullScale, 1,
    "E:4,5,7 | A:4,6,7* | D:4,6,7 | G:4,6 | B:4,5*,7 | e:4,5,7"),   // Major scale box 3 pattern 1
  shape(CagedBox.POS3, CagedMode.Major, ScaleSubset.Pentatonic, 1,
    "E:4,7 | A:4,7* | D:4,6 | G:4,6 | B:5*,7 | e:4,7"),   // Major pentatonic scale box 3
  shape(CagedBox.POS3, CagedMode.Major, ScaleSubset.Triad, 1,
    "E:4,7 | A:7* | D:6 | G:4 | B:5* | e:4,7"),   // Major triad box 3
  shape(CagedBox.POS3, CagedMode.Minor, ScaleSubset.FullScale, 1,
    "E:3,5,7 | A:3,5,7* | D:4,5,7 | G:4,5,7 | B:5*,7 | e:3,5,7"),   // Minor scale box 3 pattern 1
  shape(CagedBox.POS3, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
    "E:5,7 | A:5,7* | D:5,7 | G:4,7 | B:5*,8 | e:5,7"),   // Minor pentatonic scale box 3
  shape(CagedBox.POS3, CagedMode.Minor, ScaleSubset.Triad, 1,
    "E:7 | A:7* | D:5 | G:4 | B:5*,8 | e:7"),   // Minor triad box 3

  // ---- Box 4 - CAGED shape A ----
  shape(CagedBox.POS4, CagedMode.Major, ScaleSubset.FullScale, 1,
    "E:5,7,9 | A:6,7*,9 | D:6,7,9 | G:6,8,9* | B:7,9 | e:5,7,9"),   // Major scale box 4 pattern 1
  shape(CagedBox.POS4, CagedMode.Major, ScaleSubset.FullScale, 2,
    "E:7,9,11 | A:7*,9,11 | D:7,9,11 | G:8,9*,11 | B:9,10 | e:7,9,11"),   // Major scale box 4 pattern 2
  shape(CagedBox.POS4, CagedMode.Major, ScaleSubset.Pentatonic, 1,
    "E:7,9 | A:7*,9 | D:6,9 | G:6,9* | B:7,9 | e:7,9"),   // Major pentatonic scale box 4
  shape(CagedBox.POS4, CagedMode.Major, ScaleSubset.Triad, 1,
    "E:7 | A:7* | D:6,9 | G:9* | B:9 | e:7"),   // Major triad box 4
  shape(CagedBox.POS4, CagedMode.Minor, ScaleSubset.FullScale, 1,
    "E:5,7,8 | A:5,7*,9 | D:5,7,9 | G:5,7 | B:5*,7,8 | e:5,7,8"),   // Minor scale box 4 pattern 1
  shape(CagedBox.POS4, CagedMode.Minor, ScaleSubset.FullScale, 2,
    "E:7,8,10 | A:7*,9,10 | D:7,9,10 | G:7,9* | B:7,8,10 | e:7,8,10"),   // Minor scale box 4 pattern 2
  shape(CagedBox.POS4, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
    "E:7,10 | A:7*,10 | D:7,9 | G:7,9* | B:8,10 | e:7,10"),   // Minor pentatonic scale box 4
  shape(CagedBox.POS4, CagedMode.Minor, ScaleSubset.Triad, 1,
    "E:7 | A:7*,10 | D:9 | G:9* | B:8 | e:7"),   // Minor triad box 4

  // ---- Box 5 - CAGED shape G ----
  shape(CagedBox.POS5, CagedMode.Major, ScaleSubset.FullScale, 1,
    "E:9,11,12* | A:9,11,12 | D:9,11,13 | G:9*,11 | B:9,10,12 | e:9,11,12*"),   // Major scale box 5 pattern 1
  shape(CagedBox.POS5, CagedMode.Major, ScaleSubset.Pentatonic, 1,
    "E:9,12* | A:9,11 | D:9,11 | G:9*,11 | B:9,12 | e:9,12*"),   // Major pentatonic scale box 5
  shape(CagedBox.POS5, CagedMode.Major, ScaleSubset.Triad, 1,
    "E:12* | A:11 | D:9 | G:9* | B:9,12 | e:12*"),   // Major triad box 5
  shape(CagedBox.POS5, CagedMode.Minor, ScaleSubset.FullScale, 1,
    "E:8,10,12* | A:9,10,12 | D:9,10,12 | G:9*,11,12 | B:10,12 | e:8,10,12*"),   // Minor scale box 5 pattern 1
  shape(CagedBox.POS5, CagedMode.Minor, ScaleSubset.Pentatonic, 1,
    "E:10,12* | A:10,12 | D:9,12 | G:9*,12 | B:10,12 | e:10,12*"),   // Minor pentatonic scale box 5
  shape(CagedBox.POS5, CagedMode.Minor, ScaleSubset.Triad, 1,
    "E:12* | A:10 | D:9 | G:9*,12 | B:12 | e:12*"),   // Minor triad box 5
]);

/** How many fingerings the sheet draws - 2 for the scale of boxes 1 and 4. */
export function patternCount(box: CagedBox, mode: CagedMode, subset: ScaleSubset): number {
  return CAGED_SHAPES.has(shapeKey(box, mode, subset, 2)) ? 2 : 1;
}

export function shapeDots(box: CagedBox, mode: CagedMode, subset: ScaleSubset, pattern = 1): ShapeDot[] {
  return CAGED_SHAPES.get(shapeKey(box, mode, subset, pattern))
    ?? CAGED_SHAPES.get(shapeKey(box, mode, subset, 1))!;
}

/** The tonic's fret on the low-E string, nudged by an octave when the shape would
 *  otherwise fall off either end of a numFrets neck. */
export function anchorFor(tonic: PitchClass, dots: ShapeDot[], tuning: Tuning, numFrets = 22): number {
  const lowEpc = midiPitchClass(tuning.openStrings[0].midi);
  let base = (((tonic - lowEpc) % 12) + 12) % 12;
  const lo = Math.min(...dots.map((d) => d.offset));
  const hi = Math.max(...dots.map((d) => d.offset));
  if (base + hi > numFrets && base - 12 + lo >= 0) base -= 12;
  if (base + lo < 0) base += 12;
  return base;
}

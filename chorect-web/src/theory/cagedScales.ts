// The CAGED 5-position major-scale system for GUITAR (standard tuning), for the
// "Scales & Triads" trainer. Mirror of theory/.../CagedScales.kt. See
// docs/superpowers/specs/2026-07-25-caged-scales-triads-design.md.
//
// Each position = every major-scale tone inside a fixed fret window
// [T+loOffset, T+hiOffset], T = the parent-major tonic's fret on the low-E
// string (lowest octave that keeps the window on the neck). A fixed window IS
// the "clean position, no backward reach" fingering convention.

import { PitchClass, Interval, Tuning, FretPosition, fp, noteAt, midiPitchClass, stringCount } from "./core";

export enum CagedBox { POS1 = "POS1", POS2 = "POS2", POS3 = "POS3", POS4 = "POS4", POS5 = "POS5" }

const BOX_OFFSETS: Record<CagedBox, [number, number]> = {
  [CagedBox.POS1]: [-1, 2],
  [CagedBox.POS2]: [1, 5],
  [CagedBox.POS3]: [4, 7],
  [CagedBox.POS4]: [6, 10],
  [CagedBox.POS5]: [8, 12],
};

export const CAGED_BOXES: CagedBox[] = [CagedBox.POS1, CagedBox.POS2, CagedBox.POS3, CagedBox.POS4, CagedBox.POS5];

export enum ScaleSubset { Triad = "Triad", Pentatonic = "Pentatonic", FullScale = "FullScale" }
export enum CagedMode { Major = "Major", Minor = "Minor" }

export interface CagedNote {
  position: FretPosition;
  interval: Interval;   // above the active-mode root
  isRoot: boolean;
}

// Minor is the PARALLEL minor of tonic (same root, natural minor) — NOT the
// relative minor — so the box stays in the same position; only the notes change.
function subsetPcs(tonic: PitchClass, mode: CagedMode, subset: ScaleSubset): Set<PitchClass> {
  const pc = (semis: number): PitchClass => (((tonic + semis) % 12) + 12) % 12;
  const degrees = mode === CagedMode.Major
    ? { [ScaleSubset.FullScale]: [0, 2, 4, 5, 7, 9, 11], [ScaleSubset.Pentatonic]: [0, 2, 4, 7, 9], [ScaleSubset.Triad]: [0, 4, 7] }
    : { [ScaleSubset.FullScale]: [0, 2, 3, 5, 7, 8, 10], [ScaleSubset.Pentatonic]: [0, 3, 5, 7, 10], [ScaleSubset.Triad]: [0, 3, 7] };
  return new Set(degrees[subset].map(pc));
}

export function rootOf(tonic: PitchClass, _mode: CagedMode): PitchClass {
  return tonic;   // same root for major and parallel minor
}

// Tonic's lowest fret on the low-E string (0..11). Boxes run up the neck from
// there; a below-nut POS1 note is clipped at fret 0. Do NOT shift the whole set
// up an octave — that pushes POS4/POS5 off the neck and drops notes (the neck
// must simply be long enough — see the 22-fret default).
function anchorFret(tonic: PitchClass, _box: CagedBox, tuning: Tuning): number {
  const lowEpc = midiPitchClass(tuning.openStrings[0].midi);
  return (((tonic - lowEpc) % 12) + 12) % 12;
}

export function boxWindow(tonic: PitchClass, box: CagedBox, tuning: Tuning): [number, number] {
  const t = anchorFret(tonic, box, tuning);
  return [t + BOX_OFFSETS[box][0], t + BOX_OFFSETS[box][1]];
}

export function resolveBox(
  tonic: PitchClass,
  box: CagedBox,
  mode: CagedMode,
  subset: ScaleSubset,
  tuning: Tuning,
  numFrets = 22,
): CagedNote[] {
  const [lo, hi] = boxWindow(tonic, box, tuning);
  const pcs = subsetPcs(tonic, mode, subset);
  const root = rootOf(tonic, mode);
  const out: CagedNote[] = [];
  for (let s = 0; s < stringCount(tuning); s++) {
    for (let f = Math.max(lo, 0); f <= Math.min(hi, numFrets); f++) {
      const pc = midiPitchClass(noteAt(tuning, fp(s, f)).midi);
      if (pcs.has(pc)) {
        out.push({ position: fp(s, f), interval: (((pc - root) % 12) + 12) % 12, isRoot: pc === root });
      }
    }
  }
  return out;
}

// ---------- Triads: 4 adjacent 3-string groups × 3 inversions × {maj,min} ----------

export interface TriadShape {
  strings: [number, number, number];  // low→high string indices
  frets: [number, number, number];
  bassInterval: Interval;             // interval of the lowest note above the triad root
  inversion: number;                  // 0=root pos, 1=1st, 2=2nd
}

export const TRIAD_GROUPS: [number, number, number][] = [
  [0, 1, 2], [1, 2, 3], [2, 3, 4], [3, 4, 5],
];

/** The 3 close-voiced inversions of a major/minor triad on each 3-string group,
 *  ascending the neck. 4 groups × 3 = 12 shapes. */
export function triadInversions(
  keyTonic: PitchClass,
  quality: "maj" | "min",
  tuning: Tuning,
  numFrets = 22,
): TriadShape[] {
  const rootPc = keyTonic;
  const thirdPc = (((rootPc + (quality === "maj" ? 4 : 3)) % 12) + 12) % 12;
  const fifthPc = (((rootPc + 7) % 12) + 12) % 12;
  const triadPcs = new Set([rootPc, thirdPc, fifthPc]);
  const out: TriadShape[] = [];
  for (const group of TRIAD_GROUPS) {
    const [a, b, c] = group;
    const found: TriadShape[] = [];
    const seenBass = new Set<number>();
    for (let f0 = 0; f0 <= numFrets; f0++) {
      const m0 = noteAt(tuning, fp(a, f0)).midi;
      if (!triadPcs.has(midiPitchClass(m0))) continue;
      const f1 = nextTone(tuning, b, m0, triadPcs, numFrets);
      if (f1 < 0) continue;
      const m1 = noteAt(tuning, fp(b, f1)).midi;
      const f2 = nextTone(tuning, c, m1, triadPcs, numFrets);
      if (f2 < 0) continue;
      const frets: [number, number, number] = [f0, f1, f2];
      const fretted = frets.filter((f) => f > 0);
      const span = fretted.length ? Math.max(...fretted) - Math.min(...fretted) : 0;
      if (span > 5) continue;
      const bassPc = midiPitchClass(m0);
      if (seenBass.has(bassPc)) continue;        // one voicing per inversion (lowest)
      seenBass.add(bassPc);
      const bassInterval = (((bassPc - rootPc) % 12) + 12) % 12;
      const inversion = bassPc === rootPc ? 0 : bassPc === thirdPc ? 1 : 2;
      found.push({ strings: group, frets, bassInterval, inversion });
      if (seenBass.size === 3) break;
    }
    found.sort((x, y) => x.inversion - y.inversion);
    out.push(...found);
  }
  return out;
}

/** Smallest fret on [str] whose note is a triad tone with midi strictly above [aboveMidi]. */
function nextTone(tuning: Tuning, str: number, aboveMidi: number, triadPcs: Set<PitchClass>, numFrets: number): number {
  for (let f = 0; f <= numFrets; f++) {
    const n = noteAt(tuning, fp(str, f)).midi;
    if (n > aboveMidi && triadPcs.has(midiPitchClass(n))) return f;
  }
  return -1;
}

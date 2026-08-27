// The CAGED 5-position system for GUITAR (standard tuning), behind the
// "Guitar practice" trainer. Mirror of theory/.../CagedScales.kt. See
// docs/superpowers/specs/2026-07-25-caged-scales-triads-design.md.
//
// Fret placement is NOT computed here any more. Every shape is read verbatim out
// of cagedShapeTable.ts, which transcribes Nadav's hand-drawn sheet dot for dot.
// The old fret-window generator ([T+lo, T+hi], sweep up every scale tone inside)
// approximated those fingerings but never matched them: the real boxes reach a
// fret back or forward on individual strings, and the pentatonic and triad shapes
// are hand-picked rather than filtered from the scale.

import { PitchClass, Interval, Tuning, FretPosition, fp, noteAt, midiPitchClass } from "./core";
import {
  CagedBox, CagedMode, ScaleSubset, CAGED_BOXES,
  shapeDots, patternCount, anchorFor,
} from "./cagedShapeTable";

// CagedBox / CagedMode / ScaleSubset live in cagedShapeTable.ts (to break an
// import cycle) and reach consumers through theory/index.ts, which re-exports
// both modules.

export interface CagedNote {
  position: FretPosition;
  interval: Interval;   // above the active-mode root
  isRoot: boolean;
}

/** One step of the guided Practice run. */
export interface DrillStep {
  box: CagedBox;
  mode: CagedMode;
  subset: ScaleSubset;
  pattern: number;
}

/** Root of the active mode: the SAME tonic for both major and parallel minor. */
export function rootOf(tonic: PitchClass, _mode: CagedMode): PitchClass {
  return tonic;
}

/**
 * The sheet's shape for box × mode × subset × pattern, transposed to `tonic` and
 * labelled against the active-mode root. Notes that would fall off a `numFrets`
 * neck are dropped (anchorFor first tries to avoid that by octave).
 */
export function resolveBox(
  tonic: PitchClass,
  box: CagedBox,
  mode: CagedMode,
  subset: ScaleSubset,
  tuning: Tuning,
  numFrets = 22,
  pattern = 1,
): CagedNote[] {
  const dots = shapeDots(box, mode, subset, pattern);
  const base = anchorFor(tonic, dots, tuning, numFrets);
  const root = rootOf(tonic, mode);
  const out: CagedNote[] = [];
  for (const d of dots) {
    const f = base + d.offset;
    if (f < 0 || f > numFrets || d.string >= tuning.openStrings.length) continue;
    const pc = midiPitchClass(noteAt(tuning, fp(d.string, f)).midi);
    out.push({ position: fp(d.string, f), interval: (((pc - root) % 12) + 12) % 12, isRoot: d.isRoot });
  }
  out.sort((a, b) => a.position.stringIndex - b.position.stringIndex || a.position.fret - b.position.fret);
  return out;
}

/** The fret span the shape actually occupies — the label under the neck. */
export function boxWindow(
  tonic: PitchClass,
  box: CagedBox,
  tuning: Tuning,
  mode: CagedMode = CagedMode.Major,
  subset: ScaleSubset = ScaleSubset.FullScale,
  pattern = 1,
  numFrets = 22,
): [number, number] {
  const dots = shapeDots(box, mode, subset, pattern);
  const base = anchorFor(tonic, dots, tuning, numFrets);
  const offs = dots.map((d) => d.offset);
  return [base + Math.min(...offs), base + Math.max(...offs)];
}

// ---------- The guided Practice run ----------

/** Chord tones first, then the whole scale, then the pentatonic. */
const SUBSET_ORDER: ScaleSubset[] = [ScaleSubset.Triad, ScaleSubset.FullScale, ScaleSubset.Pentatonic];

/**
 * The steps drilled at one box: both qualities, the LEAD alternating by box index
 * — Nadav's rule "if pos == 0 play major then minor; else if the last thing
 * played was minor, play major, else minor". Where the sheet draws a second
 * fingering (the scale of boxes 1 and 4) both patterns are drilled, pattern 1
 * first.
 */
export function drillSteps(box: CagedBox): DrillStep[] {
  const i = CAGED_BOXES.indexOf(box);
  const lead = i % 2 === 0 ? CagedMode.Major : CagedMode.Minor;
  const other = lead === CagedMode.Major ? CagedMode.Minor : CagedMode.Major;
  const forMode = (mode: CagedMode): DrillStep[] => {
    const steps: DrillStep[] = [];
    for (const subset of SUBSET_ORDER) {
      for (let p = 1; p <= patternCount(box, mode, subset); p++) steps.push({ box, mode, subset, pattern: p });
    }
    return steps;
  };
  return [...forMode(lead), ...forMode(other)];
}

/** The whole run: 5 boxes low to high, drillSteps at each. */
export const PRACTICE_RUN: DrillStep[] = CAGED_BOXES.flatMap(drillSteps);

// ---------- Explore tab (free position browser, not part of the drill) ----------

/** One entry of the Explore browser: a diagram from the sheet, with the box and
 *  pattern it came from so the caption can name it the way the Guided run does. */
export interface ExplorePosition {
  /** 1-based index within the browser's list, ascending the neck. */
  index: number;
  box: CagedBox;
  mode: CagedMode;
  subset: ScaleSubset;
  pattern: number;
  firstFret: number;
  lastFret: number;
  notes: CagedNote[];
}

/**
 * Positions for the Explore scroller: THE SHEET'S OWN DIAGRAMS, the same
 * fingerings the Guided run drills, ordered low → high the neck.
 *
 * It used to call scalePositionsFor(), which takes every scale tone inside a
 * 5-fret window. That is not a fingering: because the B string sits a major 3rd
 * (not a 4th) above the G string, one pitch lands twice inside such a window —
 * G-string fret n and B-string fret n−4 are the same note — so six of the seven
 * windows carried a duplicate the real box does not, and the pentatonic windows
 * were not the pentatonic boxes at all. Nadav flagged both. Reading the table
 * instead fixes every case at once and keeps Explore and the Guided run showing
 * the same shapes.
 *
 * Counts follow the sheet: 7 per mode for FullScale (5 boxes, with a second
 * fingering on boxes 1 and 4), 5 for the other subsets. (Fretboard mode still
 * browses arbitrary scales via scalePositionsFor.)
 */
export function explorePositions(
  tonic: PitchClass, mode: CagedMode, subset: ScaleSubset, tuning: Tuning, numFrets = 22,
): ExplorePosition[] {
  const out: ExplorePosition[] = [];
  for (const box of CAGED_BOXES) {
    for (let pattern = 1; pattern <= patternCount(box, mode, subset); pattern++) {
      const notes = resolveBox(tonic, box, mode, subset, tuning, numFrets, pattern);
      if (!notes.length) continue;
      const frets = notes.map((n) => n.position.fret);
      out.push({
        index: 0, box, mode, subset, pattern,
        firstFret: Math.min(...frets), lastFret: Math.max(...frets), notes,
      });
    }
  }
  out.sort((a, b) => (a.firstFret - b.firstFret) || (a.lastFret - b.lastFret));
  return out.map((p, i) => ({ ...p, index: i + 1 }));
}

// ---------- Triads: 4 adjacent 3-string groups × 3 inversions × {maj,min} ----------

export interface TriadShape {
  strings: [number, number, number];  // low→high string indices
  frets: [number, number, number];
  bassInterval: Interval;             // interval of the lowest note above the triad root
  inversion: number;                  // 0=root pos, 1=1st, 2=2nd
}

/**
 * The 4 adjacent 3-string groups, **top group first** — guitar strings 1-2-3,
 * then 2-3-4, 3-4-5, 4-5-6. (Indices are 0 = low E, so the lists read high→low.)
 * This is the order Nadav drills them in.
 */
export const TRIAD_GROUPS: [number, number, number][] = [
  [3, 4, 5], [2, 3, 4], [1, 2, 3], [0, 1, 2],
];

/** The 3 close-voiced inversions of a major/minor triad on each 3-string group,
 *  ascending the neck. 4 groups × 3 = 12 shapes.
 *
 *  Pinned to Nadav's triad sheet (docs/caged-shapes-source.md, D major) by three
 *  rules that are NOT free choices:
 *   - **no open strings** — these are movable shapes, so the search starts at
 *     fret 1; an open-string voicing is a different (unmovable) grip;
 *   - **complete triads only** — a close voicing that lands on root/3rd/3rd
 *     (which happens once the open string is off the table, e.g. E-A-D in D at
 *     fret 2) is not a triad and is skipped, pushing that inversion up the neck;
 *   - **neck order, not inversion order** — within a string group the three shapes
 *     come out low → high, the order they are drilled in. */
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
    for (let f0 = 1; f0 <= numFrets; f0++) {
      const m0 = noteAt(tuning, fp(a, f0)).midi;
      if (!triadPcs.has(midiPitchClass(m0))) continue;
      const f1 = nextTone(tuning, b, m0, triadPcs, numFrets);
      if (f1 < 0) continue;
      const m1 = noteAt(tuning, fp(b, f1)).midi;
      const f2 = nextTone(tuning, c, m1, triadPcs, numFrets);
      if (f2 < 0) continue;
      const m2 = noteAt(tuning, fp(c, f2)).midi;
      const frets: [number, number, number] = [f0, f1, f2];
      const span = Math.max(...frets) - Math.min(...frets);
      if (span > 5) continue;
      // A close voicing can double a degree and drop another — not a triad.
      const degrees = new Set([midiPitchClass(m0), midiPitchClass(m1), midiPitchClass(m2)]);
      if (degrees.size !== 3) continue;
      const bassPc = midiPitchClass(m0);
      if (seenBass.has(bassPc)) continue;        // one voicing per inversion (lowest)
      seenBass.add(bassPc);
      const bassInterval = (((bassPc - rootPc) % 12) + 12) % 12;
      const inversion = bassPc === rootPc ? 0 : bassPc === thirdPc ? 1 : 2;
      found.push({ strings: group, frets, bassInterval, inversion });
      if (seenBass.size === 3) break;
    }
    out.push(...found);   // neck order — see triadInversions' contract
  }
  return out;
}

/**
 * The Triads drill, in Nadav's order: the top 3-string group's 3 shapes low → high
 * the neck, then 2-3-4, 3-4-5, 4-5-6 — all **major**, then the whole run again
 * **minor**. 24 voicings.
 */
export function triadRun(
  keyTonic: PitchClass, tuning: Tuning, numFrets = 22,
): { quality: "maj" | "min"; shape: TriadShape }[] {
  return [
    ...triadInversions(keyTonic, "maj", tuning, numFrets).map((shape) => ({ quality: "maj" as const, shape })),
    ...triadInversions(keyTonic, "min", tuning, numFrets).map((shape) => ({ quality: "min" as const, shape })),
  ];
}

/** Smallest FRETTED (>= 1) fret on [str] whose note is a triad tone strictly above
 *  [aboveMidi]. Open strings are excluded — see triadInversions. */
function nextTone(tuning: Tuning, str: number, aboveMidi: number, triadPcs: Set<PitchClass>, numFrets: number): number {
  for (let f = 1; f <= numFrets; f++) {
    const n = noteAt(tuning, fp(str, f)).midi;
    if (n > aboveMidi && triadPcs.has(midiPitchClass(n))) return f;
  }
  return -1;
}

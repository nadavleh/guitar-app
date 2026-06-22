// Scale theory, ported from theory/.../{Scale,ScaleLibrary,ScalePosition}.kt.

import {
  Interval, IV, PitchClass, Tuning, FretPosition, fp, noteAt, midiPitchClass,
  pcPlus, stringCount,
} from "./core";

export interface Scale {
  readonly name: string;
  readonly intervals: ReadonlyArray<Interval>;
}

export function scaleNotesFrom(scale: Scale, root: PitchClass): PitchClass[] {
  return scale.intervals.map((iv) => pcPlus(root, iv));
}

function s(name: string, intervals: Interval[]): Scale {
  return { name, intervals };
}

export const SCALES: ReadonlyMap<string, Scale> = new Map([
  ["major", s("major", [IV.P1, IV.maj2, IV.maj3, IV.P4, IV.P5, IV.maj6, IV.maj7])],
  ["natural minor", s("natural minor", [IV.P1, IV.maj2, IV.min3, IV.P4, IV.P5, IV.min6, IV.min7])],
  ["major pentatonic", s("major pentatonic", [IV.P1, IV.maj2, IV.maj3, IV.P5, IV.maj6])],
  ["minor pentatonic", s("minor pentatonic", [IV.P1, IV.min3, IV.P4, IV.P5, IV.min7])],
  ["blues", s("blues", [IV.P1, IV.min3, IV.P4, IV.TT, IV.P5, IV.min7])],
  ["dorian", s("dorian", [IV.P1, IV.maj2, IV.min3, IV.P4, IV.P5, IV.maj6, IV.min7])],
  ["mixolydian", s("mixolydian", [IV.P1, IV.maj2, IV.maj3, IV.P4, IV.P5, IV.maj6, IV.min7])],
]);

// ---------- Scale positions ----------

export interface ScalePosition {
  readonly index: number;
  readonly anchorPitchClass: PitchClass;
  readonly firstFret: number;
  readonly lastFret: number;
  readonly positions: ReadonlyArray<FretPosition>;
  readonly rootCount: number;
}

const DEFAULT_MAX_FRET_SPAN = 4;
const DEFAULT_MIN_ROOT_INSTANCES = 2;

function lowestFretOnString0(pc: PitchClass, tuning: Tuning, numFrets: number): number | null {
  for (let f = 0; f <= numFrets; f++) {
    if (midiPitchClass(noteAt(tuning, fp(0, f)).midi) === pc) return f;
  }
  return null;
}

export function scalePositionsFor(
  root: PitchClass,
  scale: Scale,
  tuning: Tuning,
  numFrets: number,
  maxFretSpan = DEFAULT_MAX_FRET_SPAN,
  minRootInstances = DEFAULT_MIN_ROOT_INSTANCES,
): ScalePosition[] {
  const scalePcs = new Set<PitchClass>(scaleNotesFrom(scale, root));
  const out: ScalePosition[] = [];

  scale.intervals.forEach((interval) => {
    const anchorPc = pcPlus(root, interval);
    const anchor = lowestFretOnString0(anchorPc, tuning, numFrets);
    if (anchor === null) return;
    const lastFret = Math.min(anchor + maxFretSpan, numFrets);
    const collected: FretPosition[] = [];
    let rootCount = 0;
    for (let s2 = 0; s2 < stringCount(tuning); s2++) {
      for (let f = anchor; f <= lastFret; f++) {
        const pc = midiPitchClass(noteAt(tuning, fp(s2, f)).midi);
        if (scalePcs.has(pc)) {
          collected.push(fp(s2, f));
          if (pc === root) rootCount++;
        }
      }
    }
    if (rootCount >= minRootInstances) {
      out.push({
        index: out.length + 1,
        anchorPitchClass: anchorPc,
        firstFret: anchor,
        lastFret,
        positions: collected,
        rootCount,
      });
    }
  });
  return out;
}

// Fretboard highlight scan, ported from theory/.../FretboardOverlay.kt.

import {
  Interval, PitchClass, Tuning, FretPosition, fp, fpKey, noteAt, midiPitchClass,
  pcPlus, stringCount,
} from "./core";
import { ChordQuality } from "./chords";
import { Scale } from "./scales";

export interface FretboardHighlight {
  readonly pos: FretPosition;
  readonly pitchClass: PitchClass;
  readonly interval: Interval;
  readonly isRoot: boolean;
}

function scan(root: PitchClass, intervals: ReadonlyArray<Interval>, tuning: Tuning, numFrets: number): Map<string, FretboardHighlight> {
  const intervalByPc = new Map<PitchClass, Interval>();
  for (const iv of intervals) intervalByPc.set(pcPlus(root, iv), iv);
  const result = new Map<string, FretboardHighlight>();
  for (let s = 0; s < stringCount(tuning); s++) {
    for (let f = 0; f <= numFrets; f++) {
      const pos = fp(s, f);
      const pc = midiPitchClass(noteAt(tuning, pos).midi);
      const interval = intervalByPc.get(pc);
      if (interval === undefined) continue;
      result.set(fpKey(pos), { pos, pitchClass: pc, interval, isRoot: pc === root });
    }
  }
  return result;
}

export function chordOverlay(root: PitchClass, quality: ChordQuality, tuning: Tuning, numFrets: number): Map<string, FretboardHighlight> {
  return scan(root, quality.intervals, tuning, numFrets);
}

export function scaleOverlay(root: PitchClass, scale: Scale, tuning: Tuning, numFrets: number): Map<string, FretboardHighlight> {
  return scan(root, scale.intervals, tuning, numFrets);
}

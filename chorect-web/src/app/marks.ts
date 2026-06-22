// Fretboard mark computation, ported from app/.../Common.kt + the marks block in
// MainActivity.kt. Produces the Map<fpKey, FretMark> the canvas renders.

import {
  Interval, PitchClass, Tuning, ChordQuality, Scale, ScalePosition, ChordShape,
  fp, fpKey, noteAt, midiPitchClass, pcInterval, spellPc, stringCount,
  chordOverlay, scaleOverlay,
} from "../theory";
import { AppState, DisplayMode, LabelMode, ChordScaleView } from "./appState";

export enum MarkKind { Chord = "Chord", Scale = "Scale", Pick = "Pick" }

export interface FretMark {
  readonly label: string;
  readonly isRoot: boolean;
  readonly kind: MarkKind;
}

export function intervalName(iv: Interval): string {
  switch (((iv % 12) + 12) % 12) {
    case 0: return "1";
    case 1: return "b2";
    case 2: return "2";
    case 3: return "b3";
    case 4: return "3";
    case 5: return "4";
    case 6: return "b5";
    case 7: return "5";
    case 8: return "b6";
    case 9: return "6";
    case 10: return "b7";
    case 11: return "7";
    default: return "?";
  }
}

function labelFor(mode: LabelMode, pc: PitchClass, interval: Interval): string {
  switch (mode) {
    case LabelMode.Notes: return spellPc(pc);
    case LabelMode.Intervals: return intervalName(interval);
    case LabelMode.Empty: return "";
  }
}

export function chordMarks(root: PitchClass, quality: ChordQuality, tuning: Tuning, numFrets: number, labelMode: LabelMode): Map<string, FretMark> {
  const overlay = chordOverlay(root, quality, tuning, numFrets);
  const out = new Map<string, FretMark>();
  for (const [key, h] of overlay) {
    out.set(key, { label: labelFor(labelMode, h.pitchClass, h.interval), isRoot: h.isRoot, kind: MarkKind.Chord });
  }
  return out;
}

export function scaleMarks(root: PitchClass, scale: Scale, tuning: Tuning, numFrets: number, labelMode: LabelMode): Map<string, FretMark> {
  const overlay = scaleOverlay(root, scale, tuning, numFrets);
  const out = new Map<string, FretMark>();
  for (const [key, h] of overlay) {
    out.set(key, { label: labelFor(labelMode, h.pitchClass, h.interval), isRoot: h.isRoot, kind: MarkKind.Scale });
  }
  return out;
}

export function scalePositionMarks(position: ScalePosition, root: PitchClass, tuning: Tuning, labelMode: LabelMode): Map<string, FretMark> {
  const out = new Map<string, FretMark>();
  for (const pos of position.positions) {
    if (pos.stringIndex >= stringCount(tuning)) continue;
    const n = noteAt(tuning, pos);
    const pc = midiPitchClass(n.midi);
    const interval = pcInterval(pc, root);
    out.set(fpKey(pos), { label: labelFor(labelMode, pc, interval), isRoot: pc === root, kind: MarkKind.Scale });
  }
  return out;
}

export function shapeMarks(shape: ChordShape, labelMode: LabelMode): Map<string, FretMark> {
  const out = new Map<string, FretMark>();
  shape.frets.forEach((f, s) => {
    if (f === null) return;
    const n = noteAt(shape.tuning, fp(s, f));
    const pc = midiPitchClass(n.midi);
    const interval = pcInterval(pc, shape.root);
    out.set(fpKey(fp(s, f)), { label: labelFor(labelMode, pc, interval), isRoot: pc === shape.root, kind: MarkKind.Chord });
  });
  return out;
}

export function pickedMarks(state: AppState): Map<string, FretMark> {
  const out = new Map<string, FretMark>();
  for (const key of state.pickedPositions) {
    const [s, f] = key.split(",").map((x) => parseInt(x, 10));
    if (s >= stringCount(state.liveTuning)) continue;
    const n = noteAt(state.liveTuning, fp(s, f));
    out.set(key, {
      label: state.labelMode === LabelMode.Notes ? spellPc(midiPitchClass(n.midi)) : "",
      isRoot: false,
      kind: MarkKind.Pick,
    });
  }
  return out;
}

/** The full mark set for the current state, given the derived chord shapes & scale positions. */
export function computeMarks(state: AppState, chordShapes: ChordShape[], scalePositions: ScalePosition[], numFrets: number): Map<string, FretMark> {
  const parsed = parseChordSafe(state.chordInput);
  switch (state.displayMode) {
    case DisplayMode.Chord: {
      if (!parsed) return new Map();
      if (state.chordView === ChordScaleView.AllNotes) {
        return chordMarks(parsed[0], parsed[1], state.liveTuning, numFrets, state.labelMode);
      }
      const shape = chordShapes[state.chordPositionIndex];
      return shape ? shapeMarks(shape, state.labelMode) : new Map();
    }
    case DisplayMode.Scale: {
      const sc = scaleInfo(state);
      if (!sc) return new Map();
      if (state.scaleView === ChordScaleView.AllNotes) {
        return scaleMarks(sc.root, sc.scale, state.liveTuning, numFrets, state.labelMode);
      }
      const pos = scalePositions[state.scalePositionIndex];
      return pos ? scalePositionMarks(pos, sc.root, state.liveTuning, state.labelMode) : new Map();
    }
    case DisplayMode.Pick:
      return pickedMarks(state);
    case DisplayMode.None:
      return new Map();
  }
}

// Local helpers that the UI also needs; kept here so marks + UI agree.

import { parseChord } from "../theory";
import { SCALES, parsePitchClass } from "../theory";

export function parseChordSafe(symbol: string): [PitchClass, ChordQuality] | null {
  return parseChord(symbol);
}

export function scaleInfo(state: AppState): { root: PitchClass; scale: Scale } | null {
  let root: PitchClass;
  try {
    root = parsePitchClass(state.scaleRoot);
  } catch {
    return null;
  }
  const scale = SCALES.get(state.scaleType);
  if (!scale) return null;
  return { root, scale };
}

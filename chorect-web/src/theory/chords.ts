// Chord theory, ported from theory/.../{ChordQuality,ChordLibrary,ChordShape,
// CagedShapes,CavaquinhoShapes,JazzShellVoicings,Voicing,ChordShapeGenerator,
// Fingering}.kt.

import {
  Interval, IV, PitchClass, Tuning, Note, fp, noteAt,
  midiPitchClass, pcPlus, pcInterval, spellPc, parsePitchClass, stringCount, tuningsEqual,
} from "./core";
import { standard, cavaqDgbe } from "./tunings";

// ---------- ChordQuality ----------

export interface ChordQuality {
  readonly symbol: string;
  readonly intervals: ReadonlyArray<Interval>;
}

export function notesFrom(quality: ChordQuality, root: PitchClass): PitchClass[] {
  return quality.intervals.map((iv) => pcPlus(root, iv));
}

function q(symbol: string, intervals: Interval[]): ChordQuality {
  return { symbol, intervals };
}

// ---------- ChordLibrary ----------

export const QUALITIES: ReadonlyMap<string, ChordQuality> = new Map([
  ["", q("", [IV.P1, IV.maj3, IV.P5])],
  ["maj", q("maj", [IV.P1, IV.maj3, IV.P5])],
  ["m", q("m", [IV.P1, IV.min3, IV.P5])],
  ["min", q("min", [IV.P1, IV.min3, IV.P5])],
  ["dim", q("dim", [IV.P1, IV.min3, IV.TT])],
  ["aug", q("aug", [IV.P1, IV.maj3, IV.min6])],
  ["sus2", q("sus2", [IV.P1, IV.maj2, IV.P5])],
  ["sus4", q("sus4", [IV.P1, IV.P4, IV.P5])],
  ["7", q("7", [IV.P1, IV.maj3, IV.P5, IV.min7])],
  ["maj7", q("maj7", [IV.P1, IV.maj3, IV.P5, IV.maj7])],
  ["m7", q("m7", [IV.P1, IV.min3, IV.P5, IV.min7])],
  ["min7", q("min7", [IV.P1, IV.min3, IV.P5, IV.min7])],
  ["m7b5", q("m7b5", [IV.P1, IV.min3, IV.TT, IV.min7])],
  ["dim7", q("dim7", [IV.P1, IV.min3, IV.TT, IV.maj6])],
  ["6", q("6", [IV.P1, IV.maj3, IV.P5, IV.maj6])],
  ["m6", q("m6", [IV.P1, IV.min3, IV.P5, IV.maj6])],
  ["9", q("9", [IV.P1, IV.maj3, IV.P5, IV.min7, IV.maj9])],
  ["add9", q("add9", [IV.P1, IV.maj3, IV.P5, IV.maj9])],
  ["13", q("13", [IV.P1, IV.maj3, IV.P5, IV.min7, IV.maj9, IV.maj13])],
  ["maj9", q("maj9", [IV.P1, IV.maj3, IV.maj7, IV.maj9])],
  ["maj13", q("maj13", [IV.P1, IV.maj3, IV.maj7, IV.maj13])],
  ["maj7#11", q("maj7#11", [IV.P1, IV.maj3, IV.maj7, IV.s11])],
  ["m9", q("m9", [IV.P1, IV.min3, IV.min7, IV.maj9])],
  ["m11", q("m11", [IV.P1, IV.min3, IV.min7, IV.P11])],
  ["11", q("11", [IV.P1, IV.P5, IV.min7, IV.P11])],
  ["mMaj7", q("mMaj7", [IV.P1, IV.min3, IV.P5, IV.maj7])],
  ["7#5", q("7#5", [IV.P1, IV.maj3, IV.min6, IV.min7])],
  ["maj7#5", q("maj7#5", [IV.P1, IV.maj3, IV.min6, IV.maj7])],
]);

/** Parse a chord symbol like "Cmaj7" → [root, quality], or null. */
export function parseChord(symbol: string): [PitchClass, ChordQuality] | null {
  const trimmed = symbol.trim();
  if (trimmed.length === 0) return null;
  for (let rootLen = Math.min(2, trimmed.length); rootLen >= 1; rootLen--) {
    const rootText = trimmed.substring(0, rootLen);
    let rootPc: PitchClass | null;
    try {
      rootPc = parsePitchClass(rootText);
    } catch {
      rootPc = null;
    }
    if (rootPc !== null) {
      const qualitySymbol = trimmed.substring(rootLen);
      const quality = QUALITIES.get(qualitySymbol);
      if (quality) return [rootPc, quality];
    }
  }
  return null;
}

// ---------- ChordShape ----------

export enum CagedShape {
  C = "C", A = "A", G = "G", E = "E", D = "D",
}

export const CagedShapeInfo: Record<CagedShape, { displayName: string; rootString: number }> = {
  [CagedShape.C]: { displayName: "C-shape", rootString: 1 },
  [CagedShape.A]: { displayName: "A-shape", rootString: 1 },
  [CagedShape.G]: { displayName: "G-shape", rootString: 0 },
  [CagedShape.E]: { displayName: "E-shape", rootString: 0 },
  [CagedShape.D]: { displayName: "D-shape", rootString: 2 },
};

export class ChordShape {
  readonly chordName: string;
  readonly root: PitchClass;
  readonly quality: ChordQuality;
  readonly frets: ReadonlyArray<number | null>;
  readonly tuning: Tuning;
  readonly cagedShape: CagedShape | null;
  readonly templateName: string | null;

  constructor(args: {
    chordName: string;
    root: PitchClass;
    quality: ChordQuality;
    frets: ReadonlyArray<number | null>;
    tuning: Tuning;
    cagedShape?: CagedShape | null;
    templateName?: string | null;
  }) {
    this.chordName = args.chordName;
    this.root = args.root;
    this.quality = args.quality;
    this.frets = args.frets;
    this.tuning = args.tuning;
    this.cagedShape = args.cagedShape ?? null;
    this.templateName = args.templateName ?? null;
  }

  private get frettedNonZero(): number[] {
    return this.frets.filter((f): f is number => f !== null && f > 0);
  }
  private get played(): number[] {
    return this.frets.filter((f): f is number => f !== null);
  }

  get position(): number {
    const f = this.frettedNonZero;
    return f.length ? Math.min(...f) : 0;
  }

  get fretSpan(): number {
    const f = this.frettedNonZero;
    return f.length === 0 ? 0 : Math.max(...f) - Math.min(...f);
  }

  get notes(): (Note | null)[] {
    return this.frets.map((f, i) => (f === null ? null : noteAt(this.tuning, fp(i, f))));
  }

  get intervals(): (Interval | null)[] {
    return this.notes.map((n) => (n === null ? null : pcInterval(midiPitchClass(n.midi), this.root)));
  }

  get bassPitchClass(): PitchClass | null {
    const first = this.notes.find((n) => n !== null);
    return first ? midiPitchClass(first.midi) : null;
  }

  get hasRootInBass(): boolean {
    return this.bassPitchClass === this.root;
  }

  get mutedCount(): number {
    return this.frets.filter((f) => f === null).length;
  }

  get playedCount(): number {
    return this.played.length;
  }
}

// ---------- CAGED templates ----------

type Tmpl = ReadonlyArray<number | null>;

const CAGED: Record<string, Partial<Record<CagedShape, Tmpl>>> = {
  major: {
    C: [null, 0, -1, -3, -2, -3], A: [null, 0, 2, 2, 2, 0], G: [0, -1, -3, -3, -3, 0],
    E: [0, 2, 2, 1, 0, 0], D: [null, null, 0, 2, 3, 2],
  },
  minor: {
    C: [null, 0, -2, -3, -2, null], A: [null, 0, 2, 2, 1, 0], G: [0, -2, -3, -3, null, 0],
    E: [0, 2, 2, 0, 0, 0], D: [null, null, 0, 2, 3, 1],
  },
  dom7: {
    C: [null, 0, -1, 0, -2, -3], A: [null, 0, 2, 0, 2, 0], G: [0, -1, -3, -3, -3, -2],
    E: [0, 2, 0, 1, 0, 0], D: [null, null, 0, 2, 1, 2],
  },
  maj7: {
    C: [null, 0, -1, -3, -3, -3], A: [null, 0, 2, 1, 2, 0], G: [0, -1, -3, -3, -3, -1],
    E: [0, 2, 1, 1, 0, 0], D: [null, null, 0, 2, 2, 2],
  },
  m7: {
    C: [null, 0, -2, 0, -2, 0], A: [null, 0, 2, 0, 1, 0], G: [0, -2, -3, -3, null, -2],
    E: [0, 2, 0, 0, 0, 0], D: [null, null, 0, 2, 1, 1],
  },
  m7b5: {
    C: [null, 0, -2, 0, 1, -1], A: [null, 0, 1, 0, 1, null], G: [0, -2, 0, 0, -1, 0],
    E: [0, 1, 0, 0, null, null], D: [null, null, 0, 1, 1, 1],
  },
  dim7: {
    C: [null, 0, -2, -1, -2, -1], A: [null, 0, 1, 2, 1, 2], G: [0, -2, -1, 0, -1, 0],
    E: [0, 1, 2, 0, 2, null], D: [null, null, 0, 1, 0, 1],
  },
  dim: {
    C: [null, 0, -2, -4, -2, null], A: [null, 0, 1, 2, 1, null], G: [0, -2, -4, -3, -4, 0],
    E: [0, 1, 2, 0, 2, null], D: [null, null, 0, 1, null, 1],
  },
  aug: {
    C: [null, 0, -1, -2, -2, -3], A: [null, 0, 3, 2, 2, 1], G: [0, -1, -2, -3, null, 0],
    E: [0, 3, 2, 1, 1, 0], D: [null, null, 0, 3, 3, 2],
  },
  ninth: {
    C: [null, 0, -1, 0, 0, -3], A: [null, 0, -1, 0, 0, 0], G: [0, -1, -3, -1, -3, -2],
    E: [0, 2, 0, 1, 0, 2], D: [null, null, 0, -1, 1, 0],
  },
  thirteen: {
    C: [null, 0, -1, 0, 2, 2], A: [null, 0, 2, 0, 2, 2], G: [0, -1, 0, -3, -3, -3],
    E: [0, 2, 0, 1, 2, 2], D: [null, 2, 0, 2, 1, 2],
  },
  sus2: {
    C: [null, 0, -3, -3, -2, null], A: [null, 0, 2, 2, 0, 0], G: [0, -3, -3, -3, null, 0],
    E: [0, 2, 4, 4, 0, 0], D: [null, null, 0, 2, 3, 0],
  },
  sus4: {
    C: [null, 0, 0, -3, -2, -2], A: [null, 0, 2, 2, 3, 0], G: [0, 0, -3, -3, -2, 0],
    E: [0, 2, 2, 2, 0, 0], D: [null, null, 0, 2, 3, 3],
  },
  sixth: {
    C: [null, 0, -1, -1, -2, -3], A: [null, 0, 2, 2, 2, 2], G: [0, -1, -3, -3, -3, -3],
    E: [0, 2, 2, 1, 2, 0], D: [null, null, 0, 2, 0, 2],
  },
  minor6: {
    C: [null, 0, -2, -1, -2, 0], A: [null, 0, 2, 2, 1, 2], G: [0, -2, -1, -3, null, -3],
    E: [0, 2, 2, 0, 2, 0], D: [null, null, 0, 2, 0, 1],
  },
  add9: {
    C: [null, 0, -1, -3, 0, -3], A: [null, 0, 2, 4, 2, 0], G: [0, -1, -3, -1, -3, 0],
    E: [0, 2, 4, 1, 0, 0], D: [null, null, 0, 2, 3, 0],
  },
};

function cagedTemplatesFor(symbol: string): Partial<Record<CagedShape, Tmpl>> | null {
  switch (symbol) {
    case "": case "maj": return CAGED.major;
    case "m": case "min": return CAGED.minor;
    case "7": return CAGED.dom7;
    case "maj7": return CAGED.maj7;
    case "m7": case "min7": return CAGED.m7;
    case "m7b5": return CAGED.m7b5;
    case "dim7": return CAGED.dim7;
    case "dim": return CAGED.dim;
    case "aug": return CAGED.aug;
    case "sus2": return CAGED.sus2;
    case "sus4": return CAGED.sus4;
    case "6": return CAGED.sixth;
    case "m6": return CAGED.minor6;
    case "9": return CAGED.ninth;
    case "add9": return CAGED.add9;
    case "13": return CAGED.thirteen;
    default: return null;
  }
}

function buildOffsetShape(args: {
  offsets: Tmpl;
  rootString: number;
  root: PitchClass;
  quality: ChordQuality;
  tuning: Tuning;
  maxFrets: number;
  cagedShape?: CagedShape | null;
  templateName?: string | null;
}): ChordShape | null {
  const { offsets, rootString, root, quality, tuning, maxFrets } = args;
  if (offsets.length !== stringCount(tuning)) return null;
  const openPc = midiPitchClass(tuning.openStrings[rootString].midi);
  const xBase = (((root - openPc) % 12) + 12) % 12;
  const nonNull = offsets.filter((o): o is number => o !== null);
  const minNeg = nonNull.length ? Math.min(...nonNull) : 0;
  const minX = minNeg < 0 ? -minNeg : 0;
  let x = xBase;
  while (x < minX) x += 12;
  if (x > maxFrets) return null;
  const frets = offsets.map((off) => (off === null ? null : x + off));
  if (frets.some((f) => f !== null && (f < 0 || f > maxFrets))) return null;
  return new ChordShape({
    chordName: `${spellPc(root)}${quality.symbol}`,
    root, quality, frets, tuning,
    cagedShape: args.cagedShape ?? null,
    templateName: args.templateName ?? null,
  });
}

function sortByPosition(shapes: ChordShape[]): ChordShape[] {
  return shapes.slice().sort((a, b) => {
    if (a.position !== b.position) return a.position - b.position;
    const am = Math.max(0, ...a.frets.filter((f): f is number => f !== null));
    const bm = Math.max(0, ...b.frets.filter((f): f is number => f !== null));
    return am - bm;
  });
}

export function cagedShapesFor(root: PitchClass, quality: ChordQuality, tuning: Tuning, maxFrets: number): ChordShape[] {
  if (!tuningsEqual(tuning, standard)) return [];
  const templates = cagedTemplatesFor(quality.symbol);
  if (!templates) return [];
  const results: ChordShape[] = [];
  for (const shape of [CagedShape.C, CagedShape.A, CagedShape.G, CagedShape.E, CagedShape.D]) {
    const tmpl = templates[shape];
    if (!tmpl) continue;
    const s = buildOffsetShape({
      offsets: tmpl, rootString: CagedShapeInfo[shape].rootString,
      root, quality, tuning, maxFrets, cagedShape: shape,
    });
    if (s) results.push(s);
  }
  return sortByPosition(results);
}

// ---------- Cavaquinho DGBe voicings ----------

interface OffsetVoicing { name: string; rootString: number; offsets: Tmpl; }

const CAVAQ_DGBE: Record<string, OffsetVoicing[]> = {
  major: [
    { name: "major (C-shape, 3-5-R-3)", rootString: 2, offsets: [1, -1, 0, -1] },
    { name: "major (A-shape, 5-R-3-5)", rootString: 1, offsets: [0, 0, 0, -2] },
    { name: "major (D-shape, R-3-5-R)", rootString: 0, offsets: [0, -1, -2, -2] },
  ],
  minor: [
    { name: "minor (A-shape, 5-R-b3-5)", rootString: 1, offsets: [0, 0, -1, -2] },
    { name: "minor (E-shape, R-b3-5-R)", rootString: 0, offsets: [0, -2, -2, -2] },
  ],
  dom7: [
    { name: "7 root-pos (5-R-3-b7)", rootString: 1, offsets: [0, 0, 0, 1] },
    { name: "7 1st-inv (b7-3-5-R)", rootString: 3, offsets: [0, 1, 0, 0] },
    { name: "7 2nd-inv (R-5-b7-3)", rootString: 0, offsets: [0, 2, 1, 2] },
    { name: "7 3rd-inv (3-b7-R-5)", rootString: 2, offsets: [1, 2, 0, 2] },
    { name: "7 rootless (3-b7-3-5)", rootString: 1, offsets: [-3, -2, 0, -2] },
  ],
  maj7: [
    { name: "maj7 root-pos (5-R-3-7)", rootString: 1, offsets: [0, 0, 0, 2] },
    { name: "maj7 1st-inv (7-3-5-R)", rootString: 3, offsets: [1, 1, 0, 0] },
    { name: "maj7 2nd-inv (R-5-7-3)", rootString: 0, offsets: [0, 2, 2, 2] },
    { name: "maj7 3rd-inv (3-7-R-5)", rootString: 2, offsets: [1, 3, 0, 2] },
    { name: "maj7 rootless (3-5-7-3)", rootString: 1, offsets: [-3, -5, -5, -5] },
  ],
  m7: [
    { name: "m7 root-pos (5-R-b3-b7)", rootString: 1, offsets: [0, 0, -1, 1] },
    { name: "m7 Freddie-Green (b7-b3-5-R)", rootString: 3, offsets: [0, 0, 0, 0] },
    { name: "m7 2nd-inv (R-5-b7-b3)", rootString: 0, offsets: [0, 2, 1, 1] },
    { name: "m7 3rd-inv (b3-b7-R-5)", rootString: 2, offsets: [0, 2, 0, 2] },
    { name: "m7 rootless (b3-b7-b3-5)", rootString: 1, offsets: [-4, -2, -1, -2] },
  ],
  m7b5: [
    { name: "m7b5 root-pos (b5-R-b3-b7)", rootString: 1, offsets: [-1, 0, -1, 1] },
    { name: "m7b5 1st-inv (b7-b3-b5-R)", rootString: 3, offsets: [0, 0, -1, 0] },
    { name: "m7b5 2nd-inv (R-b5-b7-b3)", rootString: 0, offsets: [0, 1, 1, 1] },
    { name: "m7b5 3rd-inv (b3-b7-R-b5)", rootString: 2, offsets: [0, 2, 0, 1] },
    { name: "m7b5 (5-fret stretch, R-b3-b5-b7)", rootString: 0, offsets: [0, -2, -3, -4] },
  ],
  dim7: [
    { name: "dim7 (R on B-string)", rootString: 2, offsets: [0, 1, 0, 1] },
    { name: "dim7 (5-fret stretch, R-b3-b5-bb7)", rootString: 0, offsets: [0, -2, -3, -5] },
  ],
  sixth: [
    { name: "6 root-pos (5-R-3-6)", rootString: 1, offsets: [0, 0, 0, 0] },
    { name: "6 1st-inv (6-3-5-R)", rootString: 3, offsets: [-1, 1, 0, 0] },
    { name: "6 2nd-inv (R-5-6-3)", rootString: 0, offsets: [0, 2, 0, 2] },
    { name: "6 3rd-inv (3-6-R-5)", rootString: 2, offsets: [1, 1, 0, 2] },
  ],
  minor6: [
    { name: "m6 root-pos (5-R-b3-6)", rootString: 1, offsets: [0, 0, -1, 0] },
    { name: "m6 1st-inv (6-b3-5-R)", rootString: 3, offsets: [-1, 0, 0, 0] },
    { name: "m6 2nd-inv (R-5-6-b3)", rootString: 0, offsets: [0, 2, 0, 1] },
    { name: "m6 3rd-inv (b3-6-R-5)", rootString: 2, offsets: [0, 1, 0, 2] },
  ],
};

function cavaqTableFor(symbol: string): OffsetVoicing[] | null {
  switch (symbol) {
    case "": case "maj": return CAVAQ_DGBE.major;
    case "m": case "min": return CAVAQ_DGBE.minor;
    case "7": return CAVAQ_DGBE.dom7;
    case "maj7": return CAVAQ_DGBE.maj7;
    case "m7": case "min7": return CAVAQ_DGBE.m7;
    case "m7b5": return CAVAQ_DGBE.m7b5;
    case "dim7": return CAVAQ_DGBE.dim7;
    case "6": return CAVAQ_DGBE.sixth;
    case "m6": return CAVAQ_DGBE.minor6;
    default: return null;
  }
}

export function cavaquinhoShapesFor(root: PitchClass, quality: ChordQuality, tuning: Tuning, maxFrets: number): ChordShape[] {
  if (!tuningsEqual(tuning, cavaqDgbe)) return [];
  const table = cavaqTableFor(quality.symbol);
  if (!table) return [];
  const out: ChordShape[] = [];
  for (const v of table) {
    const s = buildOffsetShape({
      offsets: v.offsets, rootString: v.rootString,
      root, quality, tuning, maxFrets, templateName: v.name,
    });
    if (s) out.push(s);
  }
  return sortByPosition(out);
}

// ---------- Jazz drop-2 shell voicings ----------

const JAZZ_TABLE: Record<string, OffsetVoicing[]> = {
  maj7: [
    { name: "maj7 drop-2 root-pos (5-R-3-7)", rootString: 3, offsets: [null, null, 0, 0, 0, 2] },
    { name: "maj7 drop-2 1st-inv (7-3-5-R)", rootString: 5, offsets: [null, null, 1, 1, 0, 0] },
    { name: "maj7 drop-2 2nd-inv (R-5-7-3)", rootString: 2, offsets: [null, null, 0, 2, 2, 2] },
    { name: "maj7 drop-2 3rd-inv (3-7-R-5)", rootString: 4, offsets: [null, null, 1, 3, 0, 2] },
    { name: "maj7 drop-2 middle-4 (R-5-7-3)", rootString: 1, offsets: [null, 0, 2, 1, 2, null] },
  ],
  m7: [
    { name: "m7 drop-2 root-pos (5-R-b3-b7)", rootString: 3, offsets: [null, null, 0, 0, -1, 1] },
    { name: "m7 drop-2 1st-inv (b7-b3-5-R)", rootString: 5, offsets: [null, null, 0, 0, 0, 0] },
    { name: "m7 drop-2 2nd-inv (R-5-b7-b3)", rootString: 2, offsets: [null, null, 0, 2, 1, 1] },
    { name: "m7 drop-2 3rd-inv (b3-b7-R-5)", rootString: 4, offsets: [null, null, 0, 2, 0, 2] },
    { name: "m7 drop-2 middle-4 (R-5-b7-b3)", rootString: 1, offsets: [null, 0, 2, 0, 1, null] },
  ],
  dom7: [
    { name: "7 drop-2 root-pos (5-R-3-b7)", rootString: 3, offsets: [null, null, 0, 0, 0, 1] },
    { name: "7 drop-2 1st-inv (b7-3-5-R)", rootString: 5, offsets: [null, null, 0, 1, 0, 0] },
    { name: "7 drop-2 2nd-inv (R-5-b7-3)", rootString: 2, offsets: [null, null, 0, 2, 1, 2] },
    { name: "7 drop-2 3rd-inv (3-b7-R-5)", rootString: 4, offsets: [null, null, 1, 2, 0, 2] },
    { name: "7 drop-2 middle-4 (R-5-b7-3)", rootString: 1, offsets: [null, 0, 2, 0, 2, null] },
  ],
  m7b5: [
    { name: "m7b5 drop-2 root-pos (b5-R-b3-b7)", rootString: 3, offsets: [null, null, -1, 0, -1, 1] },
    { name: "m7b5 drop-2 1st-inv (b7-b3-b5-R)", rootString: 5, offsets: [null, null, 0, 0, -1, 0] },
    { name: "m7b5 drop-2 2nd-inv (R-b5-b7-b3)", rootString: 2, offsets: [null, null, 0, 1, 1, 1] },
    { name: "m7b5 drop-2 3rd-inv (b3-b7-R-b5)", rootString: 4, offsets: [null, null, 0, 2, 0, 1] },
  ],
  dim7: [
    { name: "dim7 drop-2 (b3-bb7-R-b5)", rootString: 4, offsets: [null, null, 0, 1, 0, 1] },
    { name: "dim7 A-shape (R-b5-bb7-b3)", rootString: 1, offsets: [null, 0, 1, 2, 1, null] },
  ],
  sixth: [
    { name: "6 drop-2 root-pos (5-R-3-6)", rootString: 3, offsets: [null, null, 0, 0, 0, 0] },
    { name: "6 drop-2 1st-inv (6-3-5-R)", rootString: 5, offsets: [null, null, -1, 1, 0, 0] },
    { name: "6 drop-2 2nd-inv (R-5-6-3)", rootString: 2, offsets: [null, null, 0, 2, 0, 2] },
    { name: "6 drop-2 3rd-inv (3-6-R-5)", rootString: 4, offsets: [null, null, 1, 1, 0, 2] },
  ],
  minor6: [
    { name: "m6 drop-2 root-pos (5-R-b3-6)", rootString: 3, offsets: [null, null, 0, 0, -1, 0] },
    { name: "m6 drop-2 1st-inv (6-b3-5-R)", rootString: 5, offsets: [null, null, -1, 0, 0, 0] },
    { name: "m6 drop-2 2nd-inv (R-5-6-b3)", rootString: 2, offsets: [null, null, 0, 2, 0, 1] },
    { name: "m6 drop-2 3rd-inv (b3-6-R-5)", rootString: 4, offsets: [null, null, 0, 1, 0, 2] },
  ],
  ninth: [
    { name: "9 standard (R-3-b7-9-5)", rootString: 1, offsets: [null, 0, -1, 0, 0, 0] },
  ],
};

function jazzTableFor(symbol: string): OffsetVoicing[] | null {
  switch (symbol) {
    case "maj7": return JAZZ_TABLE.maj7;
    case "m7": case "min7": return JAZZ_TABLE.m7;
    case "7": return JAZZ_TABLE.dom7;
    case "m7b5": return JAZZ_TABLE.m7b5;
    case "dim7": return JAZZ_TABLE.dim7;
    case "6": return JAZZ_TABLE.sixth;
    case "m6": return JAZZ_TABLE.minor6;
    case "9": return JAZZ_TABLE.ninth;
    default: return null;
  }
}

export function jazzShellVoicingsFor(root: PitchClass, quality: ChordQuality, tuning: Tuning, maxFrets: number): ChordShape[] {
  if (!tuningsEqual(tuning, standard)) return [];
  const table = jazzTableFor(quality.symbol);
  if (!table) return [];
  const out: ChordShape[] = [];
  for (const v of table) {
    const s = buildOffsetShape({
      offsets: v.offsets, rootString: v.rootString,
      root, quality, tuning, maxFrets, templateName: v.name,
    });
    if (s) out.push(s);
  }
  return sortByPosition(out);
}

// ---------- Voicing style + shell essentials ----------

export enum VoicingStyle { Standard = "Standard", Shell = "Shell" }

export function essentialShellIntervals(quality: ChordQuality): Set<Interval> {
  const ints = new Set(quality.intervals);
  const essential = new Set<Interval>();
  if (ints.has(IV.maj3)) essential.add(IV.maj3);
  if (ints.has(IV.min3)) essential.add(IV.min3);
  if (ints.has(IV.maj2)) essential.add(IV.maj2);
  if (ints.has(IV.P4)) essential.add(IV.P4);
  if (ints.has(IV.maj7)) essential.add(IV.maj7);
  if (ints.has(IV.min7)) essential.add(IV.min7);
  const sym = quality.symbol;
  if ((sym === "dim" || sym === "dim7" || sym === "m7b5") && ints.has(IV.TT)) essential.add(IV.TT);
  if (sym === "dim7" && ints.has(IV.maj6)) essential.add(IV.maj6);
  if (sym === "aug" && ints.has(IV.min6)) essential.add(IV.min6);
  if (ints.has(IV.b9)) essential.add(IV.b9);
  if (ints.has(IV.maj9)) essential.add(IV.maj9);
  if (ints.has(IV.P11)) essential.add(IV.P11);
  if (ints.has(IV.s11)) essential.add(IV.s11);
  if (ints.has(IV.maj13)) essential.add(IV.maj13);
  if (quality.intervals.length <= 3 && ints.has(IV.P1)) essential.add(IV.P1);
  return essential;
}

// ---------- ChordShapeGenerator ----------

export class ChordShapeGenerator {
  constructor(
    public readonly maxFretSpan = 4,
    public readonly requireAllChordTones = true,
    public readonly minStringsPlayed = 3,
    public readonly style: VoicingStyle = VoicingStyle.Standard,
  ) {}

  shapesFor(root: PitchClass, quality: ChordQuality, tuning: Tuning, frets: number, fretRange?: [number, number]): ChordShape[] {
    if (!fretRange) {
      if (stringCount(tuning) === 4) {
        const cavaq = cavaquinhoShapesFor(root, quality, tuning, frets);
        if (cavaq.length) return cavaq;
      }
      let canonical: ChordShape[];
      if (this.style === VoicingStyle.Standard) {
        canonical = cagedShapesFor(root, quality, tuning, frets);
      } else {
        const jazz = jazzShellVoicingsFor(root, quality, tuning, frets);
        canonical = jazz.length ? jazz : cagedShapesFor(root, quality, tuning, frets);
      }
      if (canonical.length) return canonical;
    }

    const chordPcs = new Set<PitchClass>(notesFrom(quality, root));
    const essentialPcs: Set<PitchClass> =
      this.style === VoicingStyle.Standard
        ? chordPcs
        : new Set([...essentialShellIntervals(quality)].map((iv) => pcPlus(root, iv)));

    const firstFret = Math.max(fretRange?.[0] ?? 0, 0);
    const lastFret = Math.min(fretRange?.[1] ?? frets, frets);
    if (firstFret > lastFret) return [];

    const seen = new Set<string>();
    const results: ChordShape[] = [];
    const chordName = `${spellPc(root)}${quality.symbol}`;

    const maxAnchor = Math.max(lastFret - this.maxFretSpan, 0);
    const anchorStart = firstFret === 0 ? 0 : firstFret;
    for (let anchor = anchorStart; anchor <= maxAnchor; anchor++) {
      const windowLo = Math.max(anchor, 1, firstFret);
      const windowHi = Math.min(anchor + this.maxFretSpan, lastFret);

      const candidates: (number | null)[][] = [];
      for (let s = 0; s < stringCount(tuning); s++) {
        const perString: (number | null)[] = [null];
        if (firstFret === 0) {
          const openPc = midiPitchClass(tuning.openStrings[s].midi);
          if (chordPcs.has(openPc)) perString.push(0);
        }
        for (let f = windowLo; f <= windowHi; f++) {
          const pc = midiPitchClass(noteAt(tuning, fp(s, f)).midi);
          if (chordPcs.has(pc)) perString.push(f);
        }
        candidates.push(perString);
      }

      this.enumerate(candidates, (shapeFrets) => {
        if (!this.isValid(shapeFrets, chordPcs, essentialPcs, tuning)) return;
        const key = shapeFrets.map((f) => (f === null ? "x" : f)).join(",");
        if (seen.has(key)) return;
        seen.add(key);
        results.push(new ChordShape({ chordName, root, quality, frets: shapeFrets.slice(), tuning }));
      });
    }

    return results.sort((a, b) => {
      if (a.hasRootInBass !== b.hasRootInBass) return a.hasRootInBass ? -1 : 1;
      if (a.position !== b.position) return a.position - b.position;
      if (a.mutedCount !== b.mutedCount) return a.mutedCount - b.mutedCount;
      return a.fretSpan - b.fretSpan;
    });
  }

  private isValid(shapeFrets: (number | null)[], chordPcs: Set<PitchClass>, essentialPcs: Set<PitchClass>, tuning: Tuning): boolean {
    let played = 0;
    let minFretted = Number.MAX_SAFE_INTEGER;
    let maxFretted = Number.MIN_SAFE_INTEGER;
    const playedPcs = new Set<PitchClass>();
    for (let i = 0; i < shapeFrets.length; i++) {
      const f = shapeFrets[i];
      if (f === null) continue;
      played++;
      if (f > 0) {
        if (f < minFretted) minFretted = f;
        if (f > maxFretted) maxFretted = f;
      }
      playedPcs.add(midiPitchClass(noteAt(tuning, fp(i, f)).midi));
    }
    const minStrings = this.style === VoicingStyle.Shell ? 2 : this.minStringsPlayed;
    if (played < minStrings) return false;
    if (minFretted !== Number.MAX_SAFE_INTEGER) {
      if (maxFretted - minFretted > this.maxFretSpan) return false;
    }
    if (this.style === VoicingStyle.Standard && this.requireAllChordTones && !containsAll(playedPcs, chordPcs)) return false;
    if (!containsAll(playedPcs, essentialPcs)) return false;
    return true;
  }

  private enumerate(candidates: (number | null)[][], action: (shape: (number | null)[]) => void): void {
    const n = candidates.length;
    const indices = new Array<number>(n).fill(0);
    const current = new Array<number | null>(n).fill(null);
    for (;;) {
      for (let i = 0; i < n; i++) current[i] = candidates[i][indices[i]];
      action(current);
      let i = n - 1;
      while (i >= 0) {
        indices[i]++;
        if (indices[i] < candidates[i].length) break;
        indices[i] = 0;
        i--;
      }
      if (i < 0) break;
    }
  }
}

function containsAll(set: Set<number>, subset: Set<number>): boolean {
  for (const v of subset) if (!set.has(v)) return false;
  return true;
}

// ---------- Fingering ----------

export function suggestFingering(shape: ChordShape): (number | null)[] {
  const frets = shape.frets;
  const nonZero: [number, number][] = [];
  frets.forEach((f, i) => {
    if (f !== null && f > 0) nonZero.push([i, f]);
  });
  if (nonZero.length === 0) return frets.map(() => null);
  const anchor = Math.min(...nonZero.map((x) => x[1]));
  const anchorStrings = nonZero.filter((x) => x[1] === anchor).map((x) => x[0]);
  const minAnchorString = Math.min(...anchorStrings);
  const isBarre = anchorStrings.length >= 2 && nonZero.some((x) => x[1] > anchor && x[0] > minAnchorString);
  return frets.map((f) => {
    if (f === null || f === 0) return null;
    if (isBarre && f === anchor) return 1;
    return Math.min(Math.max(f - anchor + 1, 1), 4);
  });
}

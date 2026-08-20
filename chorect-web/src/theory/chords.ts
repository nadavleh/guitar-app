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
  // Power chord — root and 5th, deliberately NO third, so it is neither major nor
  // minor. Common in the transcriptions as "E5".
  ["5", q("5", [IV.P1, IV.P5])],
  // Suspended dominant: the 3rd is replaced by the 4th, the 7th stays.
  ["7sus4", q("7sus4", [IV.P1, IV.P4, IV.P5, IV.min7])],
  // Altered dominants that turn up in the jazz/bossa transcriptions.
  ["7b5", q("7b5", [IV.P1, IV.maj3, IV.TT, IV.min7])],
  ["7b9", q("7b9", [IV.P1, IV.maj3, IV.P5, IV.min7, IV.b9])],
  ["6add9", q("6add9", [IV.P1, IV.maj3, IV.P5, IV.maj6, IV.maj9])],
  ["m13", q("m13", [IV.P1, IV.min3, IV.min7, IV.maj9, IV.maj13])],
]);

/**
 * Chord-sheet shorthand that means an existing quality. Transcription sites write
 * the same chord several ways: "A4" for Asus4, "D2" for Dsus2, "AM7" (capital M)
 * for Amaj7. These are notation variants, not new harmony, so they map onto the
 * canonical qualities rather than duplicating them.
 *
 * Case matters and is the reason this is an explicit table rather than a lowercase
 * compare: "m" is minor and "M" is major.
 */
const ALIASES: ReadonlyMap<string, string> = new Map([
  ["4", "sus4"],
  ["2", "sus2"],
  ["M7", "maj7"],
  ["Maj7", "maj7"],
  ["mmaj7", "mMaj7"],
  ["mMAJ7", "mMaj7"],
  ["minmaj7", "mMaj7"],
  ["sus", "sus4"],
  ["7sus", "7sus4"],
  ["4add9", "sus4"],
  ["M", ""],
  ["+", "aug"],
  ["aug7", "7#5"],
]);

/** Parse a chord symbol like "Cmaj7" → [root, quality], or null. */
export function parseChord(symbol: string): [PitchClass, ChordQuality] | null {
  const full = parseChordFull(symbol);
  return full === null ? null : [full.root, full.quality];
}

/**
 * A parsed chord symbol, including the slash bass when one was written.
 *
 * A slash chord is almost always an INVERSION — the same chord with a different
 * chord tone in the bass ("D/F#" is D major over its own 3rd). Occasionally the
 * bass is not a chord tone at all ("C/D", a pedal), which is why `bass` is kept as
 * a plain pitch class and the inversion index is derived, not assumed:
 * `inversionOf` returns null for the pedal case rather than inventing a number.
 */
export interface ParsedChord {
  readonly root: PitchClass;
  readonly quality: ChordQuality;
  /** The note written after the slash; null when the symbol had none. */
  readonly bass: PitchClass | null;
}

/** Canonical name for "<triad> with a 7th in the bass", so the implied chord is
 *  reported with the symbol a musician would write rather than a synthetic one. */
const SEVENTH_OF: ReadonlyMap<string, string> = new Map([
  ["|10", "7"], ["|11", "maj7"],
  ["maj|10", "7"], ["maj|11", "maj7"],
  ["m|10", "m7"], ["m|11", "mMaj7"],
  ["min|10", "min7"], ["min|11", "mMaj7"],
  ["sus4|10", "7sus4"],
  ["dim|10", "m7b5"],
  ["aug|10", "7#5"], ["aug|11", "maj7#5"],
  ["5|10", "7"], ["5|11", "maj7"],
]);

/**
 * The quality once a 7th in the bass is accounted for.
 *
 * Chord sheets routinely write "Bb/Ab" for what is really Bb7 with its own b7 in
 * the bass — a valid 3rd inversion, not a pedal. When the written quality carries
 * no 7th and the bass sits a 7th above the root, the 7th is implied and folded in
 * here, so `inversionOf` can resolve it properly.
 */
export function effectiveQuality(chord: ParsedChord): ChordQuality {
  const b = chord.bass;
  if (b === null) return chord.quality;
  if (notesFrom(chord.quality, chord.root).includes(b)) return chord.quality;
  const iv = pcInterval(chord.root, b);
  if (iv !== IV.min7 && iv !== IV.maj7) return chord.quality;
  if (chord.quality.intervals.includes(IV.min7) ||
      chord.quality.intervals.includes(IV.maj7)) return chord.quality;
  const named = SEVENTH_OF.get(`${chord.quality.symbol}|${iv}`);
  const canonical = named !== undefined ? QUALITIES.get(named) : undefined;
  return canonical ?? q(chord.quality.symbol + (iv === IV.maj7 ? "maj7" : "7"),
                        [...chord.quality.intervals, iv]);
}

/** True when the 7th was inferred from the bass rather than written. */
export function impliesSeventh(chord: ParsedChord): boolean {
  return effectiveQuality(chord) !== chord.quality;
}

/** Chord-tone index of the bass (0 = root position), or null when the bass is not
 *  a chord tone at all — a true pedal/added bass, e.g. "C/D". */
export function inversionOf(chord: ParsedChord): number | null {
  if (chord.bass === null) return 0;
  const idx = notesFrom(effectiveQuality(chord), chord.root).indexOf(chord.bass);
  return idx >= 0 ? idx : null;
}

/** True when the bass is a genuine chord tone below the root. */
export function isInversion(chord: ParsedChord): boolean {
  return (inversionOf(chord) ?? 0) > 0;
}

/** Full parse, preserving the slash bass. */
export function parseChordFull(symbol: string): ParsedChord | null {
  const trimmed = symbol.trim();
  if (trimmed.length === 0) return null;
  let core = trimmed;
  let bass: PitchClass | null = null;
  const slash = trimmed.indexOf("/");
  if (slash > 0) {
    // A slash with an unreadable bass makes the whole symbol invalid rather than
    // silently degrading to the base chord — that would hide bad data.
    try {
      bass = parsePitchClass(trimmed.substring(slash + 1));
    } catch {
      return null;
    }
    core = trimmed.substring(0, slash).trim();
  }
  const base = parseCore(core);
  if (base === null) return null;
  return { root: base[0], quality: base[1], bass };
}

function parseCore(text: string): [PitchClass, ChordQuality] | null {
  if (text.length === 0) return null;
  for (let rootLen = Math.min(2, text.length); rootLen >= 1; rootLen--) {
    const rootText = text.substring(0, rootLen);
    let rootPc: PitchClass | null;
    try {
      rootPc = parsePitchClass(rootText);
    } catch {
      rootPc = null;
    }
    if (rootPc !== null) {
      const qualitySymbol = text.substring(rootLen);
      const alias = ALIASES.get(qualitySymbol);
      const quality = QUALITIES.get(qualitySymbol) ?? (alias !== undefined ? QUALITIES.get(alias) : undefined);
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
    // E-shape drops the B string: at +2 it sounds the maj6 (a dim7 tone, not a dim-triad tone).
    E: [0, 1, 2, 0, null, null], D: [null, null, 0, 1, null, 1],
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

// ---------- Jazz shell voicings (root + 3rd + 7th, no 5th) ----------

/** True shell voicings on standard guitar (EADGBE): the root on a bass string plus the
 *  3rd and 7th (guide tones) above it, with the 5th OMITTED. Two shapes — root on the 6th
 *  string, root on the 5th string. Derived from the chord's 3rd and 7th (or 6th for 6/dim7),
 *  so it covers maj7/7/m7/mMaj7/m7b5/dim7/6/m6. Triads (no 7th/6th) have no shell and fall
 *  through to CAGED. Standard tuning only. (jazzguitar.be shell-chords lesson.) */
export function jazzShellVoicingsFor(root: PitchClass, quality: ChordQuality, tuning: Tuning, maxFrets: number): ChordShape[] {
  if (!tuningsEqual(tuning, standard)) return [];
  const ints = new Set(quality.intervals);
  const third = ints.has(IV.maj3) ? 4 : ints.has(IV.min3) ? 3 : null;
  if (third === null) return [];
  const seventh = ints.has(IV.maj7) ? 11 : ints.has(IV.min7) ? 10 : ints.has(IV.maj6) ? 9 : null;
  if (seventh === null) return [];
  // Fret offset (relative to the root fret) of a note `iv` semitones above the root, on a
  // string tuned `openGap` semitones above the root string, kept small (-5..+6).
  const off = (openGap: number, iv: number): number => {
    let o = ((iv - openGap) % 12 + 12) % 12;
    if (o > 6) o -= 12;
    return o;
  };
  // EADGBE gaps: 6th-root uses E(root)/D(7th,+10)/G(3rd,+15); 5th-root uses A/G(7th,+10)/B(3rd,+14).
  const shapes: OffsetVoicing[] = [
    { name: "shell 6th-string root (R-7-3)", rootString: 0, offsets: [0, null, off(10, seventh), off(15, third), null, null] },
    { name: "shell 5th-string root (R-7-3)", rootString: 1, offsets: [null, 0, null, off(10, seventh), off(14, third), null] },
  ];
  const out: ChordShape[] = [];
  for (const v of shapes) {
    const s = buildOffsetShape({ offsets: v.offsets, rootString: v.rootString, root, quality, tuning, maxFrets, templateName: v.name });
    if (s) out.push(s);
  }
  return sortByPosition(out);
}

// ---------- Voicing style + shell essentials ----------

export enum VoicingStyle { Standard = "Standard", Shell = "Shell" }

/** Shell essentials = ROOT + 3rd + 7th (or 6th), with the (perfect or altered) 5th dropped. */
export function essentialShellIntervals(quality: ChordQuality): Set<Interval> {
  const ints = new Set(quality.intervals);
  const essential = new Set<Interval>();
  essential.add(IV.P1);                                   // root (in the bass)
  if (ints.has(IV.maj3)) essential.add(IV.maj3);
  if (ints.has(IV.min3)) essential.add(IV.min3);
  if (ints.has(IV.maj2)) essential.add(IV.maj2);          // sus2
  if (ints.has(IV.P4)) essential.add(IV.P4);              // sus4
  if (ints.has(IV.maj7)) essential.add(IV.maj7);
  if (ints.has(IV.min7)) essential.add(IV.min7);
  if (!ints.has(IV.maj7) && !ints.has(IV.min7) && ints.has(IV.maj6)) essential.add(IV.maj6);
  if (quality.symbol === "aug" && ints.has(IV.min6)) essential.add(IV.min6);
  if (ints.has(IV.b9)) essential.add(IV.b9);
  if (ints.has(IV.maj9)) essential.add(IV.maj9);
  if (ints.has(IV.P11)) essential.add(IV.P11);
  if (ints.has(IV.s11)) essential.add(IV.s11);
  if (ints.has(IV.maj13)) essential.add(IV.maj13);
  return essential;
}

// ---------- cavaquinho voicing pool (voice-leading) ----------

/**
 * Comprehensive four-string cavaquinho voicing pool for the Progressions screen's
 * voice-leading. Mirror of theory/.../CavaquinhoShapes.kt#cavaquinhoVoicingPool.
 * Every grip sounding all four strings within `maxSpan` frets, classified as
 * COMPLETE (all chord tones), ROOTLESS (root dropped — 4-note chords; upper-
 * structure triad on the 3rd) or no-5th SHELL (root + 3rd + 7th), deduped by
 * interval-per-string signature (lowest position kept). templateName = the kind.
 * Feeding these to pickMinMovement lets the least-motion rule choose the smooth
 * rootless/shell voicings a player uses (e.g. the quadradinho).
 */
export function cavaquinhoVoicingPool(
  root: PitchClass,
  quality: ChordQuality,
  tuning: Tuning,
  maxFret = 15,
  maxSpan = 3,
): ChordShape[] {
  const chordPcs = new Set(notesFrom(quality, root));
  const fifth = pcPlus(root, 7);
  const fourNote = chordPcs.size >= 4;
  const hasP5 = chordPcs.has(fifth);
  const name = `${spellPc(root)}${quality.symbol}`;
  const n = stringCount(tuning);

  const candidates: number[][] = [];
  for (let s = 0; s < n; s++) {
    const list: number[] = [];
    for (let f = 0; f <= maxFret; f++) {
      if (chordPcs.has(midiPitchClass(noteAt(tuning, fp(s, f)).midi))) list.push(f);
    }
    if (list.length === 0) return [];
    candidates.push(list);
  }

  const complete = new Map<string, ChordShape>();
  const rootless = new Map<string, ChordShape>();
  const shell = new Map<string, ChordShape>();

  const consider = (frets: number[]) => {
    const midis = frets.map((f, s) => noteAt(tuning, fp(s, f)).midi);
    for (let i = 0; i < n - 1; i++) if (midis[i] === midis[i + 1]) return;
    const fretted = frets.filter((f) => f > 0);
    const maxF = fretted.length ? Math.max(...fretted) : 0;
    if (frets.some((f) => f === 0) && maxF > 3) return;
    if (fretted.length && maxF - Math.min(...fretted) > maxSpan) return;
    const playedPcs = new Set(midis.map((m) => midiPitchClass(m)));
    const intervals = midis.map((m) => (((midiPitchClass(m) - root) % 12) + 12) % 12);
    const pos = fretted.length ? Math.min(...fretted) : 0;
    const chordArr = [...chordPcs];
    const isComplete = chordArr.every((pc) => playedPcs.has(pc));
    const isRootless = fourNote && !playedPcs.has(root) && chordArr.every((pc) => pc === root || playedPcs.has(pc));
    const isShell = fourNote && hasP5 && playedPcs.has(root) && !playedPcs.has(fifth) &&
      chordArr.every((pc) => pc === fifth || playedPcs.has(pc));
    let bucket: Map<string, ChordShape>;
    let kind: string;
    if (isComplete) { bucket = complete; kind = "complete"; }
    else if (isRootless) { bucket = rootless; kind = "rootless"; }
    else if (isShell) { bucket = shell; kind = "shell"; }
    else return;
    const key = intervals.join(",");
    const prev = bucket.get(key);
    if (!prev || pos < prev.position) {
      bucket.set(key, new ChordShape({ chordName: name, root, quality, frets: frets.slice(), tuning, templateName: kind }));
    }
  };

  const idx = new Array<number>(n).fill(0);
  for (;;) {
    consider(candidates.map((c, s) => c[idx[s]]));
    let i = n - 1;
    for (; i >= 0; i--) {
      idx[i]++;
      if (idx[i] < candidates[i].length) break;
      idx[i] = 0;
    }
    if (i < 0) break;
  }
  const byPos = (a: ChordShape, b: ChordShape) => a.position - b.position;
  return [
    ...[...complete.values()].sort(byPos),
    ...[...rootless.values()].sort(byPos).slice(0, 3),
    ...[...shell.values()].sort(byPos).slice(0, 2),
  ];
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
    // Standard: every chord tone EXCEPT the perfect 5th, which is optional once the chord
    // has 4+ tones (so 7ths form compact closed voicings). Triads keep all three.
    const fifthPc = (((root + 7) % 12) as PitchClass);
    const essentialPcs: Set<PitchClass> =
      this.style === VoicingStyle.Standard
        ? ((chordPcs.size >= 4 && chordPcs.has(fifthPc))
            ? new Set([...chordPcs].filter((pc) => pc !== fifthPc))
            : chordPcs)
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
        if (!this.isValid(shapeFrets, chordPcs, essentialPcs, tuning, root)) return;
        const key = shapeFrets.map((f) => (f === null ? "x" : f)).join(",");
        if (seen.has(key)) return;
        seen.add(key);
        results.push(new ChordShape({ chordName, root, quality, frets: shapeFrets.slice(), tuning }));
      });
    }

    const ranked = results.sort((a, b) => {
      if (a.hasRootInBass !== b.hasRootInBass) return a.hasRootInBass ? -1 : 1;
      if (a.position !== b.position) return a.position - b.position;
      if (a.mutedCount !== b.mutedCount) return a.mutedCount - b.mutedCount;
      return a.fretSpan - b.fretSpan;
    });
    // 4-string instruments (cavaquinho): keep up to 5 DISTINCT voicings, preferring the
    // most compact (fewest-fret-span) ones — then lower neck position, then root-in-bass.
    // Distinct inversions that share a starting fret are all kept (different shapes).
    // Prefer full (no-mute) voicings; tolerate ≤1 mute only if no full voicing exists.
    if (stringCount(tuning) !== 4) return ranked;
    const full = ranked.filter((sh) => sh.mutedCount === 0);
    const base = full.length ? full : ranked.filter((sh) => sh.mutedCount <= 1);
    // Dedupe by VOICING SHAPE (interval pattern per string): octave copies collapse, but
    // distinct inversions are all kept. `base` is low→high so the kept instance is lowest.
    const seenSig = new Set<string>();
    const uniq: ChordShape[] = [];
    for (const sh of base) {
      const sig = sh.notes.map((n) => (n === null ? "x" : (((midiPitchClass(n.midi) - root) % 12 + 12) % 12))).join(",");
      if (!seenSig.has(sig)) { seenSig.add(sig); uniq.push(sh); }
    }
    uniq.sort((a, b) =>
      (a.fretSpan - b.fretSpan) || (a.position - b.position) ||
      (a.hasRootInBass === b.hasRootInBass ? 0 : a.hasRootInBass ? -1 : 1));
    return uniq.slice(0, 5);
  }

  private isValid(shapeFrets: (number | null)[], chordPcs: Set<PitchClass>, essentialPcs: Set<PitchClass>, tuning: Tuning, root: PitchClass): boolean {
    let played = 0;
    let minFretted = Number.MAX_SAFE_INTEGER;
    let maxFretted = Number.MIN_SAFE_INTEGER;
    let hasOpen = false;
    const playedPcs = new Set<PitchClass>();
    const midis: (number | null)[] = new Array(shapeFrets.length).fill(null);
    for (let i = 0; i < shapeFrets.length; i++) {
      const f = shapeFrets[i];
      if (f === null) continue;
      played++;
      if (f === 0) hasOpen = true;
      if (f > 0) {
        if (f < minFretted) minFretted = f;
        if (f > maxFretted) maxFretted = f;
      }
      const m = noteAt(tuning, fp(i, f)).midi;
      midis[i] = m;
      playedPcs.add(midiPitchClass(m));
    }
    const minStrings = this.style === VoicingStyle.Shell ? 2 : this.minStringsPlayed;
    if (played < minStrings) return false;
    // Don't double the SAME note (unison) on two physically adjacent strings.
    for (let i = 0; i < shapeFrets.length - 1; i++) {
      if (midis[i] !== null && midis[i + 1] !== null && midis[i] === midis[i + 1]) return false;
    }
    // An open string only makes sense in first position: a shape may NOT combine an
    // open string (fret 0) with any note fretted above the 3rd fret.
    if (hasOpen && maxFretted !== Number.MIN_SAFE_INTEGER && maxFretted > 3) return false;
    if (minFretted !== Number.MAX_SAFE_INTEGER) {
      const span = maxFretted - minFretted;
      // Cap the fretted span at maxFretSpan; hard cap 5 (guitar) / 4 (cavaquinho, 4-string).
      const hardCap = stringCount(tuning) === 4 ? 4 : 5;
      if (span > this.maxFretSpan || span > hardCap) return false;
    }
    // All-chord-tones (Standard). Tonic mandatory; the PERFECT 5th is optional whenever
    // the chord has 4+ tones and contains one — lets 7ths/6ths/extensions form compact
    // closed voicings (many drop the 5th) instead of wide grips. Triads keep all three;
    // diminished / m7b5 keep their flatted 5th (a defining tone).
    if (this.style === VoicingStyle.Standard && this.requireAllChordTones) {
      const fifth = (((root + 7) % 12) as PitchClass);
      const need = (chordPcs.size >= 4 && chordPcs.has(fifth))
        ? new Set([...chordPcs].filter((pc) => pc !== fifth))
        : chordPcs;
      if (!containsAll(playedPcs, need)) return false;
    }
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

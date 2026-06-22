// Core music-theory value types, ported from theory/.../{PitchClass,Interval,Note,
// Fretboard,Tuning,Instrument,NoteSpeller,Accidental}.kt.
//
// Kotlin uses @JvmInline value classes for PitchClass / Interval / Midi. We model
// them as plain `number` aliases plus free functions: this keeps Set<PitchClass>
// membership (used heavily in the chord generator) working by value, which a
// class-based type would break under JS reference equality.

/** A pitch class: 0..11 (C..B). */
export type PitchClass = number;
/** An interval in semitones. */
export type Interval = number;
/** An absolute MIDI note number, 0..127. */
export type Midi = number;

// ---------- PitchClass ----------

export const PC = {
  C: 0, Cs: 1, D: 2, Ds: 3, E: 4, F: 5,
  Fs: 6, G: 7, Gs: 8, A: 9, As: 10, B: 11,
} as const;

/** Wrap any integer into 0..11. */
export function pcOf(value: number): PitchClass {
  return ((value % 12) + 12) % 12;
}

/** PitchClass + semitones (or + an interval — same thing in semitones). */
export function pcPlus(pc: PitchClass, semitones: number): PitchClass {
  return pcOf(pc + semitones);
}

/** The ascending interval from `from` up to `to`, in 0..11. */
export function pcInterval(to: PitchClass, from: PitchClass): Interval {
  return (((to - from) % 12) + 12) % 12;
}

// ---------- Interval ----------

export const IV = {
  P1: 0, min2: 1, maj2: 2, min3: 3, maj3: 4, P4: 5, TT: 6, P5: 7,
  min6: 8, maj6: 9, min7: 10, maj7: 11, P8: 12, b9: 13, maj9: 14,
  P11: 17, s11: 18, maj13: 21,
} as const;

// ---------- Midi / Note ----------

export function midiPitchClass(midi: Midi): PitchClass {
  return ((midi % 12) + 12) % 12;
}

export function midiOctave(midi: Midi): number {
  return Math.floor(midi / 12) - 1;
}

export interface Note {
  readonly midi: Midi;
}

export function note(midi: Midi): Note {
  return { midi };
}

const NOTE_REGEX = /^([A-G])([#b]?)(-?\d+)$/;

function letterToPitchClass(letter: string): number {
  switch (letter) {
    case "C": return 0;
    case "D": return 2;
    case "E": return 4;
    case "F": return 5;
    case "G": return 7;
    case "A": return 9;
    case "B": return 11;
    default: throw new Error(`Invalid letter: ${letter}`);
  }
}

export function parseNote(text: string): Note {
  const m = NOTE_REGEX.exec(text);
  if (!m) throw new Error(`Invalid note: ${text}`);
  const [, letter, accidental, octaveStr] = m;
  const basePc = letterToPitchClass(letter);
  const accOffset = accidental === "#" ? 1 : accidental === "b" ? -1 : 0;
  const octave = parseInt(octaveStr, 10);
  return note((octave + 1) * 12 + basePc + accOffset);
}

// ---------- NoteSpeller ----------

export type Accidental = "sharp" | "flat";

const SHARP_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
const FLAT_NAMES = ["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"];

export function spellPc(pc: PitchClass, prefer: Accidental = "sharp"): string {
  return prefer === "sharp" ? SHARP_NAMES[pc] : FLAT_NAMES[pc];
}

export function spellNote(n: Note, prefer: Accidental = "sharp"): string {
  return `${spellPc(midiPitchClass(n.midi), prefer)}${midiOctave(n.midi)}`;
}

const PC_REGEX = /^([A-Ga-g])([#b]?)$/;

export function parsePitchClass(text: string): PitchClass {
  const m = PC_REGEX.exec(text.trim());
  if (!m) throw new Error(`Invalid pitch class: ${text}`);
  const [, letter, accidental] = m;
  const basePc = letterToPitchClass(letter.toUpperCase());
  const accOffset = accidental === "#" ? 1 : accidental === "b" ? -1 : 0;
  return pcOf(basePc + accOffset);
}

// ---------- Instrument ----------

export enum Instrument {
  Guitar = "Guitar",
  Cavaquinho = "Cavaquinho",
}

export const InstrumentInfo: Record<Instrument, { displayName: string; maxFretSpan: number }> = {
  [Instrument.Guitar]: { displayName: "Guitar", maxFretSpan: 4 },
  [Instrument.Cavaquinho]: { displayName: "Cavaquinho", maxFretSpan: 5 },
};

// ---------- Tuning ----------

export interface Tuning {
  readonly openStrings: ReadonlyArray<Note>;
}

export function tuningOf(...noteNames: string[]): Tuning {
  return { openStrings: noteNames.map(parseNote) };
}

export function stringCount(t: Tuning): number {
  return t.openStrings.length;
}

/** Structural equality for tunings (Kotlin data-class equality). */
export function tuningsEqual(a: Tuning, b: Tuning): boolean {
  if (a.openStrings.length !== b.openStrings.length) return false;
  for (let i = 0; i < a.openStrings.length; i++) {
    if (a.openStrings[i].midi !== b.openStrings[i].midi) return false;
  }
  return true;
}

// ---------- FretPosition + Fretboard ----------

export interface FretPosition {
  readonly stringIndex: number;
  readonly fret: number;
}

export function fp(stringIndex: number, fret: number): FretPosition {
  return { stringIndex, fret };
}

/** Stable string key for using FretPositions as Map keys. */
export function fpKey(p: FretPosition): string {
  return `${p.stringIndex},${p.fret}`;
}

export function fpFromKey(key: string): FretPosition {
  const [s, f] = key.split(",");
  return fp(parseInt(s, 10), parseInt(f, 10));
}

export function noteAt(tuning: Tuning, pos: FretPosition): Note {
  const open = tuning.openStrings[pos.stringIndex];
  return note(open.midi + pos.fret);
}

export function allPositions(tuning: Tuning, frets: number, of: PitchClass): FretPosition[] {
  const result: FretPosition[] = [];
  for (let s = 0; s < stringCount(tuning); s++) {
    for (let f = 0; f <= frets; f++) {
      if (midiPitchClass(noteAt(tuning, fp(s, f)).midi) === of) result.push(fp(s, f));
    }
  }
  return result;
}

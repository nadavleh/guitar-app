// Song-sheet theory: transposing a chord symbol, and naming it by its function in
// a key. Ported to/from theory/.../SongSheet.kt — keep the two in step.
//
// This is what the Songs tab needs and all it needs: no voicings, no playback. The
// sheet shows chords over lyrics, transposes them, and can relabel them by degree.

import { PitchClass, spellPc, pcPlus, pcInterval } from "./core";
import { parseChordFull, inversionOf } from "./chords";

/** A key: its tonic, and whether the song is in a minor mode. */
export interface SongKey {
  readonly tonic: PitchClass;
  readonly minor: boolean;
}

/**
 * Parse a key written as a chord symbol — "G", "Am", "Bb", "F#m", "Cmaj7".
 *
 * Runs through `parseChordFull` rather than splitting the text by hand: that
 * already knows every quality and its shorthand, so "Cmaj" reads as major and
 * "Am7" as minor without this needing its own suffix rules.
 */
export function parseKey(text: string): SongKey | null {
  const c = parseChordFull(text.trim());
  if (c === null) return null;
  const q = c.quality.symbol;
  const minor = /^(m|min)(?!aj)/.test(q) || q === "dim" || q === "dim7";
  return { tonic: c.root, minor };
}

/** How a transposed symbol should be spelled. Sheets in flat keys read badly in
 *  sharps, so the key decides rather than a global preference. */
export function prefersFlats(key: SongKey | null): boolean {
  if (key === null) return false;
  // F, Bb, Eb, Ab, Db, Gb major and their relative minors are flat keys.
  const flatTonics = key.minor ? [5, 10, 3, 8, 1, 2, 7] : [5, 10, 3, 8, 1, 6];
  return flatTonics.includes(key.tonic);
}

/**
 * Transpose one chord symbol by [semitones], preserving quality and slash bass.
 *
 * Returns the original text unchanged when the symbol does not parse, so an
 * oddity in a captured sheet transposes to itself rather than vanishing.
 */
export function transposeSymbol(symbol: string, semitones: number, flats = false): string {
  const c = parseChordFull(symbol);
  if (c === null) return symbol;
  const root = spellPc(pcPlus(c.root, ((semitones % 12) + 12) % 12), flats ? "flat" : "sharp");
  const bass = c.bass === null
    ? ""
    : "/" + spellPc(pcPlus(c.bass, ((semitones % 12) + 12) % 12), flats ? "flat" : "sharp");
  return root + c.quality.symbol + bass;
}

/** Transpose a key the same way, so the header stays consistent with the chords. */
export function transposeKey(key: SongKey, semitones: number, flats = false): string {
  return spellPc(pcPlus(key.tonic, ((semitones % 12) + 12) % 12), flats ? "flat" : "sharp") +
    (key.minor ? "m" : "");
}

// Semitone offset from the tonic → Roman numeral, spelled against the MAJOR scale
// so chromatic chords read the way a musician writes them (bVII, bIII, #IV).
const MAJOR_ROMAN = ["I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII"];
// In a minor key the b3/b6/b7 are diatonic, so they carry no flat sign; the raised
// ones are the ones worth marking.
const MINOR_ROMAN = ["I", "bII", "II", "III", "#III", "IV", "#IV", "V", "VI", "#VI", "VII", "#VII"];

/** Scale-degree number (1..7, with accidental) of a pitch class in a key — used to
 *  name the bass of an inversion. */
export function bassDegree(key: SongKey, pc: PitchClass): string {
  const iv = pcInterval(pc, key.tonic);
  const roman = (key.minor ? MINOR_ROMAN : MAJOR_ROMAN)[iv];
  const accidental = roman.startsWith("b") ? "b" : roman.startsWith("#") ? "#" : "";
  const numerals = ["I", "II", "III", "IV", "V", "VI", "VII"];
  const bare = roman.replace(/^[b#]/, "");
  return accidental + String(numerals.indexOf(bare) + 1);
}

/**
 * The chord's function in the key: "IV", "V7", "vi", "bVII", "ii7b5".
 *
 * Case carries the quality — upper for major/dominant, lower for minor/diminished
 * — which is the convention the ear-training side already uses. An inversion is
 * appended as "/<bass degree>", so "C/E" in C reads "I/3": the tonic with its
 * third in the bass. That is more legible at a glance than figured bass, and it is
 * the same information.
 */
export function degreeLabel(symbol: string, key: SongKey): string {
  const c = parseChordFull(symbol);
  if (c === null) return symbol;
  const iv = pcInterval(c.root, key.tonic);
  let roman = (key.minor ? MINOR_ROMAN : MAJOR_ROMAN)[iv];
  const q = c.quality.symbol;
  const isMinorish = /^(m|min)(?!aj)/.test(q) || q === "dim" || q === "dim7" || q === "m7b5";
  if (isMinorish) roman = roman.replace(/[IV]+/, (s) => s.toLowerCase());
  // The quality suffix, minus the minor marker already carried by the lower case.
  let suffix = q.replace(/^(m|min)(?!aj)/, "");
  if (q === "dim" || q === "dim7") suffix = q === "dim7" ? "°7" : "°";
  if (q === "m7b5") suffix = "ø7";
  let label = roman + suffix;
  if (c.bass !== null && inversionOf(c) > 0) {
    label += "/" + bassDegree(key, c.bass);
  }
  return label;
}

/** Every chord in a section relabelled by function — the whole point of the
 *  degrees view, kept here so both platforms relabel identically. */
export function degreeLabels(symbols: ReadonlyArray<string>, key: SongKey): string[] {
  return symbols.map((s) => degreeLabel(s, key));
}

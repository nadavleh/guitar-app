// Ear-training theory, ported from theory/.../EarTraining.kt.

import { PitchClass, pcOf, spellPc } from "./core";
import { Rng, defaultRng } from "./random";

export enum TrainingMode { Major = "Major", Minor = "Minor" }

export enum ChordTypeLevel { Triads = "Triads", Sevenths = "Sevenths", Extended = "Extended" }

export const ChordTypeLevelName: Record<ChordTypeLevel, string> = {
  [ChordTypeLevel.Triads]: "Triads",
  [ChordTypeLevel.Sevenths]: "7th chords",
  [ChordTypeLevel.Extended]: "Extended",
};

export interface DegreeInfo {
  roman: string;
  triadQuality: string;
  seventhQuality: string;
  extendedQuality: string;
  /** (chord-quality symbol, Roman-label suffix) diatonic extension options. */
  extendedOptions: [string, string][];
}

function di(roman: string, triad: string, seventh: string, extended: string, options: [string, string][] = []): DegreeInfo {
  return { roman, triadQuality: triad, seventhQuality: seventh, extendedQuality: extended, extendedOptions: options };
}

export interface ResolvedChord {
  /** Chord symbol parseable by parseChord, e.g. "Cmaj7". */
  symbol: string;
  /** Roman-numeral display, e.g. "Imaj7", "ii7". */
  romanLabel: string;
  root: PitchClass;
}

export interface Progression {
  mode: TrainingMode;
  degrees: number[]; // length 4, each 1..7
}

export const MAJOR_DEGREES: Map<number, DegreeInfo> = new Map([
  [1, di("I", "", "maj7", "maj9", [["6", "6"], ["add9", "add9"], ["maj9", "maj9"], ["maj13", "maj13"]])],
  [2, di("ii", "m", "m7", "m9", [["m6", "6"], ["m9", "9"], ["m11", "11"]])],
  [3, di("iii", "m", "m7", "m7", [["m11", "11"]])],
  [4, di("IV", "", "maj7", "maj9", [["6", "6"], ["add9", "add9"], ["maj9", "maj9"], ["maj7#11", "maj7#11"], ["maj13", "maj13"]])],
  [5, di("V", "", "7", "9", [["6", "6"], ["9", "9"], ["11", "11"], ["13", "13"]])],
  [6, di("vi", "m", "m7", "m9", [["m9", "9"], ["m11", "11"]])],
  [7, di("vii°", "dim", "m7b5", "m7b5", [["m7b5", "7"]])],
]);

export const MINOR_DEGREES: Map<number, DegreeInfo> = new Map([
  [1, di("i", "m", "m7", "m9")],
  [2, di("ii°", "dim", "m7b5", "m7b5")],
  [3, di("III", "", "maj7", "maj9")],
  [4, di("iv", "m", "m7", "m9")],
  [5, di("V", "", "7", "7")],
  [6, di("VI", "", "maj7", "maj9")],
  [7, di("VII", "", "7", "7")],
]);

const MAJOR_SCALE_SEMITONES = [0, 2, 4, 5, 7, 9, 11];
const NATURAL_MINOR_SEMITONES = [0, 2, 3, 5, 7, 8, 10];

export function degreeRoot(key: PitchClass, degree: number, mode: TrainingMode): PitchClass {
  const scale = mode === TrainingMode.Major ? MAJOR_SCALE_SEMITONES : NATURAL_MINOR_SEMITONES;
  return pcOf(key + scale[degree - 1]);
}

/** Build the displayed Roman label for a non-triad level. */
export function romanLabel(triadRoman: string, quality: string): string {
  if (triadRoman.endsWith("°")) {
    return quality === "m7b5" ? `${triadRoman}7` : triadRoman + quality;
  }
  const firstIsLower = triadRoman[0] === triadRoman[0].toLowerCase() && triadRoman[0] !== triadRoman[0].toUpperCase();
  if (firstIsLower && quality.startsWith("m") && quality !== "m7b5") {
    return triadRoman + quality.replace(/^m/, "");
  }
  return triadRoman + quality;
}

export function degreesMapFor(mode: TrainingMode): Map<number, DegreeInfo> {
  return mode === TrainingMode.Major ? MAJOR_DEGREES : MINOR_DEGREES;
}

/**
 * Map a scale degree in [mode] to its relative-major degree (1..7). A major key
 * and its relative minor share the same seven diatonic chords; minor 1 = major 6,
 * so a major I–IV–V reads as a minor III–VI–VII. Major mode is the identity.
 */
export function majorRelativeDegree(degree: number, mode: TrainingMode): number {
  return mode === TrainingMode.Major ? degree : ((degree + 4) % 7) + 1;
}

/** Inverse of [majorRelativeDegree]: a relative-major degree back into [mode]. */
export function degreeFromMajorRelative(majorRelative: number, mode: TrainingMode): number {
  return mode === TrainingMode.Major ? majorRelative : ((majorRelative + 1) % 7) + 1;
}

/** Alias used by the app layer. */
export const EarTrainingDegrees = degreesMapFor;

export function resolve(degree: number, key: PitchClass, mode: TrainingMode, level: ChordTypeLevel, rng: Rng = defaultRng): ResolvedChord {
  const info = degreesMapFor(mode).get(degree)!;
  const root = degreeRoot(key, degree, mode);
  const rootName = spellPc(root);
  if (level === ChordTypeLevel.Extended && info.extendedOptions.length > 0) {
    const [qual, romanSuffix] = info.extendedOptions[rng.int(info.extendedOptions.length)];
    return { symbol: `${rootName}${qual}`, romanLabel: info.roman + romanSuffix, root };
  }
  const quality =
    level === ChordTypeLevel.Triads ? info.triadQuality :
    level === ChordTypeLevel.Sevenths ? info.seventhQuality : info.extendedQuality;
  const label =
    level === ChordTypeLevel.Triads ? info.roman :
    level === ChordTypeLevel.Sevenths ? romanLabel(info.roman, info.seventhQuality) :
    romanLabel(info.roman, info.extendedQuality);
  return { symbol: `${rootName}${quality}`, romanLabel: label, root };
}

export function resolveProgression(p: Progression, key: PitchClass, level: ChordTypeLevel, rng: Rng = defaultRng): ResolvedChord[] {
  return p.degrees.map((d) => resolve(d, key, p.mode, level, rng));
}

export const MAJOR_PROGRESSIONS: Progression[] = [
  { mode: TrainingMode.Major, degrees: [1, 5, 6, 4] },
  { mode: TrainingMode.Major, degrees: [1, 4, 5, 1] },
  { mode: TrainingMode.Major, degrees: [1, 6, 4, 5] },
  { mode: TrainingMode.Major, degrees: [6, 4, 1, 5] },
  { mode: TrainingMode.Major, degrees: [2, 5, 1, 1] },
  { mode: TrainingMode.Major, degrees: [1, 6, 2, 5] },
  { mode: TrainingMode.Major, degrees: [1, 5, 1, 4] },
  { mode: TrainingMode.Major, degrees: [1, 3, 4, 5] },
  { mode: TrainingMode.Major, degrees: [1, 5, 4, 1] },
];

export const MINOR_PROGRESSIONS: Progression[] = [
  { mode: TrainingMode.Minor, degrees: [1, 6, 3, 7] },
  { mode: TrainingMode.Minor, degrees: [1, 4, 5, 1] },
  { mode: TrainingMode.Minor, degrees: [1, 6, 7, 1] },
  { mode: TrainingMode.Minor, degrees: [2, 5, 1, 1] },
  { mode: TrainingMode.Minor, degrees: [1, 7, 6, 5] },
  { mode: TrainingMode.Minor, degrees: [1, 4, 7, 3] },
];

export function randomProgression(mode: TrainingMode, rng: Rng = defaultRng): Progression {
  const pool = mode === TrainingMode.Major ? MAJOR_PROGRESSIONS : MINOR_PROGRESSIONS;
  return pool[rng.int(pool.length)];
}

// ----- Advanced (non-diatonic) progressions -----

export interface AdvChord { semitone: number; quality: string; roman: string; }

export interface NamedProgression {
  name: string;
  explanation: string;
  tonicMode: TrainingMode;
  chords: AdvChord[];
}

export function namedRomanLine(np: NamedProgression): string {
  return np.chords.map((c) => c.roman).join("  –  ");
}

export function resolveNamed(np: NamedProgression, key: PitchClass): ResolvedChord[] {
  return np.chords.map((c) => {
    const root = pcOf(key + c.semitone);
    return { symbol: spellPc(root) + c.quality, romanLabel: c.roman, root };
  });
}

function adv(name: string, explanation: string, mode: TrainingMode, chords: AdvChord[]): NamedProgression {
  return { name, explanation, tonicMode: mode, chords };
}
const c = (semitone: number, quality: string, roman: string): AdvChord => ({ semitone, quality, roman });

export const ADVANCED_PROGRESSIONS: NamedProgression[] = [
  adv("Mixolydian Rocker", "Borrows bVII from the parallel Mixolydian mode for a driving, anthemic classic-rock sound.", TrainingMode.Major,
    [c(0, "", "I"), c(10, "", "bVII"), c(5, "", "IV")]),
  adv("Bright Lift", "The major II is a borrowed/secondary-dominant chord (V of V) that gives a sudden, hopeful lift.", TrainingMode.Major,
    [c(0, "", "I"), c(2, "", "II"), c(5, "", "IV"), c(0, "", "I")]),
  adv("Romantic Climax", "A bright major III then a borrowed minor iv — a dramatic rise melting into melancholy.", TrainingMode.Major,
    [c(0, "", "I"), c(4, "", "III"), c(5, "", "IV"), c(5, "m", "iv")]),
  adv("Epic Backstep", "Borrowed bVII and bVI from the parallel minor give a cinematic, heroic backstep.", TrainingMode.Major,
    [c(0, "", "I"), c(10, "", "bVII"), c(8, "", "bVI"), c(10, "", "bVII")]),
  adv("Andalusian Cadence", "The flamenco descending tetrachord; the major V (harmonic minor) adds dark, Spanish tension.", TrainingMode.Minor,
    [c(0, "m", "i"), c(10, "", "bVII"), c(8, "", "bVI"), c(7, "", "V")]),
  adv("Dark Roots", "Uses the natural-minor v (minor, not the usual major V) for a raw, modal folk/blues feel.", TrainingMode.Minor,
    [c(0, "m", "i"), c(5, "m", "iv"), c(7, "m", "v")]),
  adv("Neo-Soul Minor", "Moody natural-minor motion through a minor v, popular in modern R&B and lo-fi.", TrainingMode.Minor,
    [c(0, "m", "i"), c(7, "m", "v"), c(8, "", "bVI"), c(10, "", "bVII")]),
  adv("Ragtime Circle", "A chain of secondary dominants around the circle of fifths — the bouncing staple of ragtime and stride.", TrainingMode.Major,
    [c(0, "", "I"), c(9, "7", "VI7"), c(2, "7", "II7"), c(7, "7", "V7")]),
  adv("Classic Ragtime Turnaround", "I becomes a dominant I7 to tonicise IV, then a borrowed minor iv adds a nostalgic, bluesy turn.", TrainingMode.Major,
    [c(0, "", "I"), c(0, "7", "I7"), c(5, "", "IV"), c(5, "m", "iv")]),
  adv("Chromatic Passing Chord", "A #i diminished passing chord connects I to ii7 with a smooth chromatic walking bass.", TrainingMode.Major,
    [c(0, "", "I"), c(1, "dim7", "#I°7"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Traditional Rag Ending", "A syncopated Scott-Joplin ending: a secondary-dominant III7, a #IV°7 passing chord, then a I–V7–I cadence.", TrainingMode.Major,
    [c(0, "", "I"), c(4, "7", "III7"), c(5, "", "IV"), c(6, "dim7", "#IV°7"), c(0, "", "I/V"), c(7, "7", "V7"), c(0, "", "I")]),
  adv("Melancholic Jazz-Rag", "A secondary-dominant III7 leads to a borrowed minor iv and a half-diminished ii — bittersweet and vintage.", TrainingMode.Major,
    [c(0, "", "I"), c(4, "7", "III7"), c(5, "m", "iv"), c(2, "m7b5", "ii7b5"), c(7, "7", "V7")]),
  adv("Broadway Lift", "The secondary-dominant III7 brightens a major-key ii–V cadence — a classic show-tune lift.", TrainingMode.Major,
    [c(0, "", "I"), c(4, "7", "III7"), c(5, "", "IV"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Minor-Key Swing", "Starts dark, then a striking secondary-dominant III7 lifts before the ii–V cadence.", TrainingMode.Minor,
    [c(0, "m", "i"), c(3, "7", "III7"), c(5, "m", "iv"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Extended Pop Ballad", "A secondary-dominant III7 tonicises vi, prolonging tension before the ii–V resolution.", TrainingMode.Major,
    [c(0, "", "I"), c(4, "7", "III7"), c(9, "m", "vi"), c(5, "", "IV"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Tritone Substitution", "The dominant V7 is replaced by bII7 a tritone away — a smooth chromatic slide into the tonic.", TrainingMode.Major,
    [c(2, "m7", "ii7"), c(1, "7", "bII7"), c(0, "maj7", "Imaj7")]),
  adv("Minor Line Cliché", "A stationary minor chord with one inner voice descending chromatically (root–7–b7–6).", TrainingMode.Minor,
    [c(0, "m", "i"), c(0, "mMaj7", "i(maj7)"), c(0, "m7", "i7"), c(0, "m6", "i6")]),
  adv("Romantic Plaintive", "A major line cliché: the top voice melts down (root–maj7–b7), pulling toward IV.", TrainingMode.Major,
    [c(0, "", "I"), c(0, "maj7", "Imaj7"), c(0, "7", "I7"), c(5, "", "IV")]),
  adv("Church Cadence", "A gospel plagal feel with a bluesy bVII descent back to IV.", TrainingMode.Major,
    [c(0, "", "I"), c(5, "", "IV"), c(0, "", "I"), c(10, "", "bVII"), c(5, "", "IV")]),
  adv("Gospel Walk-Up", "A bassline climbing the scale through a #IV°7 diminished chord — a driving gospel walk-up.", TrainingMode.Major,
    [c(0, "", "I"), c(0, "", "I/III"), c(5, "", "IV"), c(6, "dim7", "#IV°7"), c(7, "", "V")]),
  adv("Mario Cadence", "Borrowed bVI and bVII resolve up to a triumphant major I — the classic heroic/video-game cadence.", TrainingMode.Major,
    [c(8, "", "bVI"), c(10, "", "bVII"), c(0, "", "I")]),
  adv("Royal Road", "The backbone of modern J-pop/anime: it loops without ever landing on the home chord.", TrainingMode.Major,
    [c(5, "", "IV"), c(7, "", "V"), c(4, "m", "iii"), c(9, "m", "vi")]),
  adv("Bird Blues Turnaround", "Charlie Parker's rapid descending turnaround, stacking a passing #IV°7 and a secondary-dominant VI7.", TrainingMode.Major,
    [c(0, "maj7", "Imaj7"), c(6, "dim7", "#IV°7"), c(4, "m7", "iii7"), c(9, "7", "VI7"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Montgomery Turnaround", "A highly chromatic Wes-Montgomery turnaround that slides back to the tonic in tritone steps.", TrainingMode.Major,
    [c(0, "maj7", "Imaj7"), c(3, "7", "bIII7"), c(8, "7", "bVI7"), c(1, "7", "bII7")]),
];

export function randomAdvanced(rng: Rng = defaultRng): NamedProgression {
  return ADVANCED_PROGRESSIONS[rng.int(ADVANCED_PROGRESSIONS.length)];
}

// ---- Interval-identification trainer (#6) ----

export enum IntervalDirection { Ascending = "Ascending", Descending = "Descending", Mixed = "Mixed" }

export interface IntervalChoice { semitones: number; shortName: string; longName: string; }

/** The 13 intervals from unison to octave, with the arithmetic to place a target
 *  note above/below a tonic. Mirrors theory/EarTraining.kt's IntervalTrainer. */
export const INTERVAL_CHOICES: IntervalChoice[] = [
  { semitones: 0, shortName: "P1", longName: "unison" },
  { semitones: 1, shortName: "m2", longName: "minor 2nd" },
  { semitones: 2, shortName: "M2", longName: "major 2nd" },
  { semitones: 3, shortName: "m3", longName: "minor 3rd" },
  { semitones: 4, shortName: "M3", longName: "major 3rd" },
  { semitones: 5, shortName: "P4", longName: "perfect 4th" },
  { semitones: 6, shortName: "TT", longName: "tritone" },
  { semitones: 7, shortName: "P5", longName: "perfect 5th" },
  { semitones: 8, shortName: "m6", longName: "minor 6th" },
  { semitones: 9, shortName: "M6", longName: "major 6th" },
  { semitones: 10, shortName: "m7", longName: "minor 7th" },
  { semitones: 11, shortName: "M7", longName: "major 7th" },
  { semitones: 12, shortName: "P8", longName: "octave" },
];

export function intervalTargetMidi(tonicMidi: number, semitones: number, ascending: boolean): number {
  return ascending ? tonicMidi + semitones : tonicMidi - semitones;
}

export function intervalChoiceFor(semitones: number): IntervalChoice {
  return INTERVAL_CHOICES.find((i) => i.semitones === semitones)!;
}

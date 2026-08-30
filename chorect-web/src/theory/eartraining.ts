// Ear-training theory, ported from theory/.../EarTraining.kt.

import { PitchClass, pcOf, spellPc, Accidental } from "./core";
import { Rng, defaultRng } from "./random";

export enum TrainingMode { Major = "Major", Minor = "Minor" }

/** Which pool the progression generator draws from. `None` is the standard diatonic
 *  library; the others are focused recognition drills that stay in the SAME diatonic
 *  multiple-choice flow and only swap the draw pool. Mirrors Kotlin ProgFocus. */
export enum ProgFocus { None = "None", Iiii = "Iiii", ThirdVsSixth = "ThirdVsSixth" }

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
  /** Bar indices (0..3) that sound as the HARMONIC-MINOR dominant — a major V / V7
   *  instead of the natural-minor `v`. Minor-key, degree-5 bars only. Absent/empty
   *  for every natural-minor and major progression. */
  dominantBars?: number[];
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
  // Roman numerals named RELATIVE TO THE MAJOR SCALE: lowered natural-minor degrees carry
  // a flat (bIII, bVI, bVII). Qualities are natural-minor diatonic (v is minor).
  [3, di("bIII", "", "maj7", "maj9")],
  [4, di("iv", "m", "m7", "m9")],
  [5, di("v", "m", "m7", "m9")],
  [6, di("bVI", "", "maj7", "maj9")],
  [7, di("bVII", "", "7", "7")],
]);

/** The HARMONIC-MINOR dominant: degree 5 played as a MAJOR V (raised leading tone),
 *  the classic V→i cadence. Same root as the natural-minor `v`, but major / dominant 7th.
 *  Per-level suffixes ("", "7", "9") deliberately match the natural `v`'s so the
 *  challenge scores a degree-5 answer identically for either. */
export const MINOR_DOMINANT: DegreeInfo = di("V", "", "7", "9");

/** Markers appended to a Roman that prints the same in both keys, saying which one it is
 *  read in. BOTH readings are marked — leaving the major one bare made the answer
 *  "✘ V7" look like it needed no explanation, which is the confusion the marker exists
 *  to remove. See [romanIsModeAmbiguous]. */
export const MINOR_ROMAN_TAG = "(minor)";
export const MAJOR_ROMAN_TAG = "(major)";

/** The tag for a Roman read in the minor key (`minorReading`) or the major key. */
export function romanModeTag(minorReading: boolean): string {
  return minorReading ? MINOR_ROMAN_TAG : MAJOR_ROMAN_TAG;
}

/**
 * True when `roman` reads the same in a major and in a minor key, so showing it on its
 * own is ambiguous. The dominant V-family ("V", "V7", "V9", "V13"…) is the only such
 * case: it is both the major key's degree 5 and the harmonic-minor degree 5
 * ([MINOR_DOMINANT]) — two *different* answers on the challenge pad that print the same.
 * Every other degree is separated by case or an accidental (IV vs iv, iii vs bIII,
 * vii° vs bVII), so it needs no marking.
 */
export function romanIsModeAmbiguous(roman: string): boolean {
  return /^V\d*$/.test(roman);
}

const MAJOR_SCALE_SEMITONES = [0, 2, 4, 5, 7, 9, 11];
const NATURAL_MINOR_SEMITONES = [0, 2, 3, 5, 7, 8, 10];

export function degreeRoot(key: PitchClass, degree: number, mode: TrainingMode): PitchClass {
  const scale = mode === TrainingMode.Major ? MAJOR_SCALE_SEMITONES : NATURAL_MINOR_SEMITONES;
  return pcOf(key + scale[degree - 1]);
}

/** MIDI note for a bare degree-reference tone, anchored to the tonic: degree 1
 *  always sounds at 52 + key (mid guitar register) and degrees 2..7 land in the
 *  octave ABOVE it, so 1..7 form one ascending scale in every key. (Mapping the
 *  pitch class straight to 52 + pc made the octave depend on where the key's
 *  degrees fell around the pc wrap point — e.g. G major dropped an octave at 4.)
 *  Mirrors Kotlin EarTraining.degreeRefMidi. */
export function degreeRefMidi(key: PitchClass, degree: number, mode: TrainingMode): number {
  return 52 + key + ((degreeRoot(key, degree, mode) - key + 12) % 12);
}

/** Build the displayed Roman label for a non-triad level. */
export function romanLabel(triadRoman: string, quality: string): string {
  if (triadRoman.endsWith("°")) {
    return quality === "m7b5" ? `${triadRoman}7` : triadRoman + quality;
  }
  // Ignore any leading accidental (b/#) when deciding major/minor case, so
  // "bIII"+"maj7" → "bIIImaj7" (major) and "v"+"m7" → "v7" (minor).
  const core = triadRoman.replace(/^[b#]+/, "");
  const firstIsLower = core.length > 0 && core[0] === core[0].toLowerCase() && core[0] !== core[0].toUpperCase();
  if (firstIsLower && quality.startsWith("m") && !quality.startsWith("maj") && quality !== "m7b5") {
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

export function resolve(degree: number, key: PitchClass, mode: TrainingMode, level: ChordTypeLevel, rng: Rng = defaultRng, asDominant = false): ResolvedChord {
  // Harmonic-minor dominant: degree 5 sounded as a major V (see MINOR_DOMINANT).
  const info = (asDominant && mode === TrainingMode.Minor) ? MINOR_DOMINANT : degreesMapFor(mode).get(degree)!;
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
  const dom = p.dominantBars ?? [];
  return p.degrees.map((d, i) => resolve(d, key, p.mode, level, rng, dom.includes(i)));
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
  { mode: TrainingMode.Major, degrees: [1, 3, 6, 4] },   // I-iii-vi-IV (soft tonic family)
  { mode: TrainingMode.Major, degrees: [6, 2, 5, 1] },   // vi-ii-V-I
  { mode: TrainingMode.Major, degrees: [1, 2, 5, 1] },   // I-ii-V-I
  // Added from Nadav's "Top 96 progressions" list (all pure-diatonic 4-chord).
  { mode: TrainingMode.Major, degrees: [1, 4, 2, 5] },   // I-IV-ii-V
  { mode: TrainingMode.Major, degrees: [1, 4, 6, 5] },   // I-IV-vi-V
  { mode: TrainingMode.Major, degrees: [1, 5, 4, 5] },   // I-V-IV-V
  { mode: TrainingMode.Major, degrees: [6, 5, 4, 5] },   // vi-V-IV-V
  // Reclassified from Advanced — these are fully diatonic despite their names.
  { mode: TrainingMode.Major, degrees: [1, 2, 5, 6] },   // I-ii-V-vi  ("Deceptive Cadence")
  { mode: TrainingMode.Major, degrees: [4, 5, 3, 6] },   // IV-V-iii-vi ("Royal Road" J-pop)
];

export const MINOR_PROGRESSIONS: Progression[] = [
  { mode: TrainingMode.Minor, degrees: [1, 6, 3, 7] },
  { mode: TrainingMode.Minor, degrees: [1, 4, 5, 1] },
  { mode: TrainingMode.Minor, degrees: [1, 6, 7, 1] },
  { mode: TrainingMode.Minor, degrees: [2, 5, 1, 1] },
  { mode: TrainingMode.Minor, degrees: [1, 7, 6, 5] },
  { mode: TrainingMode.Minor, degrees: [1, 4, 7, 3] },
  { mode: TrainingMode.Minor, degrees: [1, 5, 6, 7] },   // i-v-bVI-bVII
  { mode: TrainingMode.Minor, degrees: [1, 3, 7, 4] },   // i-bIII-bVII-iv
];

/** Harmonic-minor progressions: classic minor-key cadences using a MAJOR V / V7 (raised
 *  leading tone) → i. Each marks its degree-5 bar(s) as dominantBars. Included in the
 *  minor generator pool + library only when the harmonic-minor toggle is on (default on).
 *  Kept separate from MINOR_PROGRESSIONS so the natural-minor set keeps its minor v. */
export const MINOR_HARMONIC_PROGRESSIONS: Progression[] = [
  { mode: TrainingMode.Minor, degrees: [1, 4, 5, 1], dominantBars: [2] },  // i-iv-V-i
  { mode: TrainingMode.Minor, degrees: [1, 2, 5, 1], dominantBars: [2] },  // i-ii°-V-i
  { mode: TrainingMode.Minor, degrees: [2, 5, 1, 1], dominantBars: [1] },  // ii°-V-i-i
  { mode: TrainingMode.Minor, degrees: [1, 6, 2, 5], dominantBars: [3] },  // i-bVI-ii°-V
  { mode: TrainingMode.Minor, degrees: [1, 6, 4, 5], dominantBars: [3] },  // i-bVI-iv-V
  { mode: TrainingMode.Minor, degrees: [1, 4, 1, 5], dominantBars: [3] },  // i-iv-i-V (half cadence)
  // Verified from Nadav's list (fact-checked — the mislabeled bVII "axis" ones excluded).
  { mode: TrainingMode.Minor, degrees: [1, 6, 3, 5], dominantBars: [3] },  // i-bVI-bIII-V
  { mode: TrainingMode.Minor, degrees: [1, 3, 6, 5], dominantBars: [3] },  // i-bIII-bVI-V
  { mode: TrainingMode.Minor, degrees: [1, 4, 6, 5], dominantBars: [3] },  // i-iv-bVI-V
];

/** Focused drill for hearing the I→iii move (the "soft" mediant that shares two
 *  notes with the tonic). Every entry opens with I–iii. Major-only, and NOT part of
 *  MAJOR_PROGRESSIONS (it's a drill, not a library entry, so needs no song list). */
export const III_FOCUS_PROGRESSIONS: Progression[] = [
  { mode: TrainingMode.Major, degrees: [1, 3, 4, 5] },   // I–iii–IV–V
  { mode: TrainingMode.Major, degrees: [1, 3, 6, 4] },   // I–iii–vi–IV
  { mode: TrainingMode.Major, degrees: [1, 3, 4, 1] },   // I–iii–IV–I
  { mode: TrainingMode.Major, degrees: [1, 3, 2, 5] },   // I–iii–ii–V
  { mode: TrainingMode.Major, degrees: [1, 3, 6, 5] },   // I–iii–vi–V
  { mode: TrainingMode.Major, degrees: [1, 3, 1, 4] },   // I–iii–I–IV
];

/** Drill-only progressions for telling the 3rd degree from the 6th: every entry puts
 *  degree 3 and degree 6 on ADJACENT bars (iii↔vi in major, bIII↔bVI in minor) so the
 *  two get compared back-to-back in a single hearing. They share the tonic's 3rd
 *  (iii = 3-5-7, vi = 6-1-3), which is exactly why they blur together. NOT part of
 *  MAJOR_PROGRESSIONS / MINOR_PROGRESSIONS — a drill needs no song examples. */
export const THIRD_SIXTH_DRILL_PROGRESSIONS: Progression[] = [
  { mode: TrainingMode.Major, degrees: [1, 3, 6, 4] },   // I–iii–vi–IV
  { mode: TrainingMode.Major, degrees: [1, 6, 3, 4] },   // I–vi–iii–IV
  { mode: TrainingMode.Major, degrees: [1, 3, 6, 1] },   // I–iii–vi–I
  { mode: TrainingMode.Major, degrees: [1, 6, 3, 5] },   // I–vi–iii–V
  { mode: TrainingMode.Major, degrees: [4, 3, 6, 1] },   // IV–iii–vi–I
  { mode: TrainingMode.Major, degrees: [1, 3, 6, 5] },   // I–iii–vi–V
  { mode: TrainingMode.Minor, degrees: [1, 3, 6, 4] },   // i–bIII–bVI–iv
  { mode: TrainingMode.Minor, degrees: [1, 6, 3, 5] },   // i–bVI–bIII–v
  { mode: TrainingMode.Minor, degrees: [1, 3, 6, 7] },   // i–bIII–bVI–bVII
  { mode: TrainingMode.Minor, degrees: [1, 6, 3, 4] },   // i–bVI–bIII–iv
];

/** Drill-only CONTRAST progressions for the 3rd-vs-6th drill: a 1↔6 move (I→vi, i→bVI,
 *  or the reverse) and NO degree 3 — the "was that the 6th or the 3rd?" foil. The library
 *  alone left this pool degenerate: with harmonic minor off there was exactly ONE minor
 *  entry, so 30 % of questions repeated the same progression. Drill-only, like
 *  THIRD_SIXTH_DRILL_PROGRESSIONS — no song lists needed. */
export const THIRD_SIXTH_CONTRAST_DRILL: Progression[] = [
  { mode: TrainingMode.Major, degrees: [1, 6, 4, 1] },   // I–vi–IV–I
  { mode: TrainingMode.Major, degrees: [1, 6, 5, 4] },   // I–vi–V–IV
  { mode: TrainingMode.Major, degrees: [4, 5, 1, 6] },   // IV–V–I–vi (wraps vi→I)
  { mode: TrainingMode.Minor, degrees: [1, 6, 4, 5] },   // i–bVI–iv–v (natural v)
  { mode: TrainingMode.Minor, degrees: [1, 6, 7, 5] },   // i–bVI–bVII–v
  { mode: TrainingMode.Minor, degrees: [1, 6, 4, 1] },   // i–bVI–iv–i
];

/** Percent of ProgFocus.ThirdVsSixth draws taken from the CONTRAST pool (a 1↔6 move
 *  and no degree 3) rather than the degree-3 pool. Integer percent, not a float, so
 *  the draw uses `rng.int` and matches Kotlin bit-for-bit on a shared seed. */
export const THIRD_SIXTH_CONTRAST_PERCENT = 30;

/** Every diatonic progression `mode` can draw, drill entries included. */
function diatonicUniverse(mode: TrainingMode, includeHarmonicMinor: boolean): Progression[] {
  if (mode === TrainingMode.Major) return [...MAJOR_PROGRESSIONS, ...III_FOCUS_PROGRESSIONS];
  return includeHarmonicMinor ? [...MINOR_PROGRESSIONS, ...MINOR_HARMONIC_PROGRESSIONS] : MINOR_PROGRESSIONS;
}

/** True when `degrees` steps between degree 1 and degree 6 in either direction on
 *  consecutive bars. The last→first bar counts: the progression loops, so that step
 *  is heard just as often as the interior ones. */
export function hasOneSixStep(degrees: number[]): boolean {
  return degrees.some((a, i) => {
    const b = degrees[(i + 1) % degrees.length];
    return (a === 1 && b === 6) || (a === 6 && b === 1);
  });
}

function dedupeProgressions(ps: Progression[]): Progression[] {
  const seen = new Map<string, Progression>();
  for (const p of ps) {
    const key = `${p.mode}|${p.degrees.join(",")}|${[...(p.dominantBars ?? [])].sort((a, b) => a - b).join(",")}`;
    if (!seen.has(key)) seen.set(key, p);
  }
  return [...seen.values()];
}

/** PRIMARY pool of the 3rd-vs-6th drill: everything in `mode` containing degree 3
 *  (iii / bIII), with the adjacent-3↔6 drill entries prepended. */
export function thirdSixthPrimaryPool(mode: TrainingMode, includeHarmonicMinor = true): Progression[] {
  return dedupeProgressions([
    ...THIRD_SIXTH_DRILL_PROGRESSIONS.filter((p) => p.mode === mode),
    ...diatonicUniverse(mode, includeHarmonicMinor).filter((p) => p.degrees.includes(3)),
  ]);
}

/** CONTRAST pool of the 3rd-vs-6th drill: library progressions that make the I↔vi
 *  (i↔bVI) move and contain NO degree 3 — the "is that the 6th or the 3rd?" foil. */
export function thirdSixthContrastPool(mode: TrainingMode, includeHarmonicMinor = true): Progression[] {
  return dedupeProgressions([
    ...THIRD_SIXTH_CONTRAST_DRILL.filter((p) => p.mode === mode),
    ...diatonicUniverse(mode, includeHarmonicMinor)
      .filter((p) => !p.degrees.includes(3) && hasOneSixStep(p.degrees)),
  ]);
}

/** Pick a random progression for `mode`, using `rng`. `focus` swaps the draw pool:
 *  ProgFocus.Iiii draws the I→iii drill (always major); ProgFocus.ThirdVsSixth draws
 *  degree-3 progressions with a THIRD_SIXTH_CONTRAST_PERCENT slice of 1↔6 foils mixed in. */
export function randomProgression(mode: TrainingMode, rng: Rng = defaultRng, focus: ProgFocus = ProgFocus.None, includeHarmonicMinor = true): Progression {
  if (focus === ProgFocus.Iiii) return III_FOCUS_PROGRESSIONS[rng.int(III_FOCUS_PROGRESSIONS.length)];
  if (focus === ProgFocus.ThirdVsSixth) {
    const contrast = thirdSixthContrastPool(mode, includeHarmonicMinor);
    const pool = contrast.length > 0 && rng.int(100) < THIRD_SIXTH_CONTRAST_PERCENT
      ? contrast : thirdSixthPrimaryPool(mode, includeHarmonicMinor);
    return pool[rng.int(pool.length)];
  }
  const pool =
    mode === TrainingMode.Major ? MAJOR_PROGRESSIONS :
    includeHarmonicMinor ? [...MINOR_PROGRESSIONS, ...MINOR_HARMONIC_PROGRESSIONS] : MINOR_PROGRESSIONS;
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
    // Spell the root to match the roman's accidental (bVII → Bb, #IV → F#).
    const prefer: Accidental = c.roman.includes("#") ? "sharp" : c.roman.includes("b") ? "flat" : "sharp";
    return { symbol: spellPc(root, prefer) + c.quality, romanLabel: c.roman, root };
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
  adv("Bird Blues Turnaround", "Charlie Parker's rapid descending turnaround, stacking a passing #IV°7 and a secondary-dominant VI7.", TrainingMode.Major,
    [c(0, "maj7", "Imaj7"), c(6, "dim7", "#IV°7"), c(4, "m7", "iii7"), c(9, "7", "VI7"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Montgomery Turnaround", "A highly chromatic Wes-Montgomery turnaround that slides back to the tonic in tritone steps.", TrainingMode.Major,
    [c(0, "maj7", "Imaj7"), c(3, "7", "bIII7"), c(8, "7", "bVI7"), c(1, "7", "bII7")]),
  adv("Applied V of V", "The major II is a secondary dominant (V of V): a dominant pointing at the dominant, not directly home.", TrainingMode.Major,
    [c(0, "", "I"), c(2, "7", "II7"), c(7, "", "V"), c(0, "", "I")]),
  adv("Tonicized Relative", "III7 is a secondary dominant (V of vi) that pulls hard into the relative minor before returning home.", TrainingMode.Major,
    [c(0, "", "I"), c(4, "7", "III7"), c(9, "m", "vi"), c(0, "", "I")]),
  adv("Applied V of ii", "VI7 is a secondary dominant (V of ii) tonicising the supertonic — a staple of jazz, standards, and Brazilian harmony.", TrainingMode.Major,
    [c(0, "", "I"), c(9, "7", "VI7"), c(2, "m", "ii"), c(7, "", "V"), c(0, "", "I")]),
  adv("Long Applied Turnaround", "A chain of applied dominants (V/vi → vi → V/V → V → I) driving a long, propulsive turnaround.", TrainingMode.Major,
    [c(0, "", "I"), c(4, "7", "III7"), c(9, "m", "vi"), c(2, "7", "II7"), c(7, "", "V"), c(0, "", "I")]),
  adv("Borrowed iv", "The borrowed minor iv from the parallel minor gives a bittersweet plagal turn back to I.", TrainingMode.Major,
    [c(0, "", "I"), c(5, "", "IV"), c(5, "m", "iv"), c(0, "", "I")]),
  adv("Mixolydian Vamp", "A borrowed bVII lends a Mixolydian, rock-modal color between the V and IV.", TrainingMode.Major,
    [c(0, "", "I"), c(7, "", "V"), c(10, "", "bVII"), c(5, "", "IV")]),
  adv("bVI-bVII Climb", "Borrowed bVI and bVII climb chromatically back up to a triumphant I — a dramatic modal resolution.", TrainingMode.Major,
    [c(0, "", "I"), c(8, "", "bVI"), c(10, "", "bVII"), c(0, "", "I")]),
  adv("Flat-Six Color", "A borrowed bVI drops in unexpected color before the familiar IV–V.", TrainingMode.Major,
    [c(0, "", "I"), c(8, "", "bVI"), c(5, "", "IV"), c(7, "", "V")]),
  adv("Flat-Three Borrowed", "The borrowed bIII from the parallel minor adds a bluesy, unexpected lift on the way to IV.", TrainingMode.Major,
    [c(0, "", "I"), c(3, "", "bIII"), c(5, "", "IV"), c(0, "", "I")]),
  adv("Chromatic Descent", "iii → bIII → ii walks the bass down chromatically into a ii–V — a smooth descending passing motion.", TrainingMode.Major,
    [c(0, "", "I"), c(4, "m", "iii"), c(3, "", "bIII"), c(2, "m", "ii"), c(7, "", "V")]),
  adv("Diminished to ii", "A #I° diminished passing chord connects I to ii with a chromatic walk-up — hear it as approach, not a \"weird\" chord.", TrainingMode.Major,
    [c(0, "", "I"), c(1, "dim", "#I°"), c(2, "m", "ii"), c(7, "", "V")]),
  adv("Diminished to iii", "A #ii° diminished chord slides ii up into iii, then a secondary-dominant VI7 pushes onward.", TrainingMode.Major,
    [c(2, "m", "ii"), c(3, "dim", "#ii°"), c(4, "m", "iii"), c(9, "7", "VI7")]),
  adv("Minor #iv° to V", "In minor, a #iv° diminished chord approaches the (major) V for a dark, dramatic dominant setup.", TrainingMode.Minor,
    [c(0, "m", "i"), c(6, "dim", "#iv°"), c(7, "", "V"), c(0, "m", "i")]),
  adv("Minor Plagal Diminished", "iv slides up through a #iv° diminished passing chord back to the tonic — a brooding minor plagal move.", TrainingMode.Minor,
    [c(0, "m", "i"), c(5, "m", "iv"), c(6, "dim", "#iv°"), c(0, "m", "i")]),
  adv("iii-VI-ii-V Turnaround", "The descending jazz turnaround: iii7 and a secondary-dominant VI7 feed the ii–V, looping back to I.", TrainingMode.Major,
    [c(4, "m7", "iii7"), c(9, "7", "VI7"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Rhythm-Changes Turnaround", "The \"rhythm changes\" turnaround — I–VI7–ii–V — the engine of bebop and countless standards.", TrainingMode.Major,
    [c(0, "maj7", "Imaj7"), c(9, "7", "VI7"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Bossa Minor Diminished", "A bossa/jazz minor move: iv through a #iv° passing diminished into a dominant V7.", TrainingMode.Minor,
    [c(0, "m", "i"), c(5, "m", "iv"), c(6, "dim", "#iv°"), c(7, "7", "V7")]),
  adv("Ragtime Return", "I becomes a dominant I7 to tonicise IV, a borrowed minor iv adds nostalgia, then home — a ragtime staple.", TrainingMode.Major,
    [c(0, "", "I"), c(0, "7", "I7"), c(5, "", "IV"), c(5, "m", "iv"), c(0, "", "I")]),
  adv("Bossa Chromatic", "A bossa-nova chromatic: a #I° diminished links Imaj7 to the ii7–V7, gliding on a chromatic bass.", TrainingMode.Major,
    [c(0, "maj7", "Imaj7"), c(1, "dim", "#I°"), c(2, "m7", "ii7"), c(7, "7", "V7")]),
  adv("Extended vi Turnaround", "The doo-wop I–vi–IV move, warmed by a borrowed minor iv before resolving home.", TrainingMode.Major,
    [c(0, "", "I"), c(9, "m", "vi"), c(5, "", "IV"), c(5, "m", "iv"), c(0, "", "I")]),
  adv("Full Turnaround", "The complete I–vi–ii–V–I turnaround — the most common way to loop a tune back to its beginning.", TrainingMode.Major,
    [c(0, "", "I"), c(9, "m", "vi"), c(2, "m", "ii"), c(7, "", "V"), c(0, "", "I")]),
  // Folded in from Nadav's "Top 96" list (non-diatonic triad progressions).
  adv("Pachelbel's Canon", "The endlessly-looping canon progression — I–V–vi–iii–IV–I–IV–V.", TrainingMode.Major,
    [c(0, "", "I"), c(7, "", "V"), c(9, "m", "vi"), c(4, "m", "iii"), c(5, "", "IV"), c(0, "", "I"), c(5, "", "IV"), c(7, "", "V")]),
  adv("Minor ii–V–i", "The minor-key ii–V–i: a half-diminished iiø into a dominant V7 resolving home.", TrainingMode.Minor,
    [c(2, "m7b5", "iiø"), c(7, "7", "V7"), c(0, "m", "i")]),
  adv("Neapolitan Cadence", "The bII (Neapolitan) — a dark half-step-above-tonic major chord — colours a minor iv–bII–bIII move.", TrainingMode.Minor,
    [c(0, "m", "i"), c(5, "m", "iv"), c(1, "", "bII"), c(3, "", "bIII")]),
];

export function randomAdvanced(rng: Rng = defaultRng): NamedProgression {
  return ADVANCED_PROGRESSIONS[rng.int(ADVANCED_PROGRESSIONS.length)];
}

/** SUS category — progressions built on suspended (sus2/sus4) chords. */
export const SUS_PROGRESSIONS: NamedProgression[] = [
  adv("Sus Resolution", "A suspended I that relaxes back to the plain I — the 4th falls to the 3rd.",
    TrainingMode.Major, [c(0, "", "I"), c(0, "sus4", "Isus4"), c(0, "", "I")]),
  adv("Suspended Lift", "A sus4 on the V adds tension before landing on vi.",
    TrainingMode.Major, [c(0, "", "I"), c(7, "sus4", "Vsus4"), c(9, "m", "vi")]),
  adv("Sus Bookends", "Sus2 colour on the tonic and a sus4 subdominant, framed by the plain I.",
    TrainingMode.Major, [c(0, "", "I"), c(0, "sus2", "Isus2"), c(5, "sus4", "IVsus4"), c(0, "", "I")]),
  adv("Dorian Sus Vamp", "A minor-key sus vamp with Dorian's bright major IV.",
    TrainingMode.Minor, [c(0, "m", "i"), c(0, "sus4", "isus4"), c(7, "m", "v"), c(5, "", "IV")]),
  adv("Mixolydian Sus", "A sus4 subdominant over a Mixolydian I–V feel.",
    TrainingMode.Major, [c(5, "sus4", "IVsus4"), c(5, "", "IV"), c(0, "", "I"), c(7, "", "V")]),
];

/** ADVANCED II category — maj7 / min9 / modal (Dorian, Mixolydian, Lydian, Phrygian) colours. */
export const ADVANCED2_PROGRESSIONS: NamedProgression[] = [
  adv("Maj7 Pop", "A dreamy maj7 on the tonic softens a I–IV–V.",
    TrainingMode.Major, [c(0, "", "I"), c(0, "maj7", "Imaj7"), c(5, "", "IV"), c(7, "", "V")]),
  adv("Maj7 Climb", "A lush IVmaj7 rising through V to vi.",
    TrainingMode.Major, [c(0, "", "I"), c(5, "maj7", "IVmaj7"), c(7, "", "V"), c(9, "m", "vi")]),
  adv("Backdoor Maj7", "IVmaj7 and a borrowed bVIImaj7 resolve to Imaj7 — the soul/backdoor sound.",
    TrainingMode.Major, [c(5, "maj7", "IVmaj7"), c(10, "maj7", "bVIImaj7"), c(0, "maj7", "Imaj7")]),
  adv("Minor-9 Vamp", "A wistful iim9 rocking against the tonic.",
    TrainingMode.Major, [c(2, "m9", "iim9"), c(0, "", "I"), c(2, "m9", "iim9"), c(0, "", "I")]),
  adv("Add9 Roots", "Open add9 shapes with a borrowed bVIImaj7 — the Bruce-Hornsby colour.",
    TrainingMode.Major, [c(0, "add9", "Iadd9"), c(10, "maj7", "bVIImaj7"), c(5, "add9", "IVadd9")]),
  adv("Dorian Vamp", "Minor tonic with Dorian's bright major IV (and bVII, bIII).",
    TrainingMode.Minor, [c(0, "m", "i"), c(10, "", "bVII"), c(3, "", "bIII"), c(5, "", "IV")]),
  adv("Mixolydian Two", "Major with a bVII and a Mixolydian II — bright and modal.",
    TrainingMode.Major, [c(0, "", "I"), c(10, "", "bVII"), c(2, "", "II"), c(0, "", "I")]),
  adv("Lydian Bright", "The floating Lydian sound: I rocking to a major II (from the raised 4th).",
    TrainingMode.Major, [c(0, "", "I"), c(2, "", "II"), c(0, "", "I"), c(2, "", "II")]),
  adv("Phrygian Dark", "Minor tonic sliding to a bII — the Spanish/metal Phrygian colour.",
    TrainingMode.Minor, [c(0, "m", "i"), c(1, "", "bII")]),
];

export function randomSus(rng: Rng = defaultRng): NamedProgression {
  return SUS_PROGRESSIONS[rng.int(SUS_PROGRESSIONS.length)];
}
export function randomAdvanced2(rng: Rng = defaultRng): NamedProgression {
  return ADVANCED2_PROGRESSIONS[rng.int(ADVANCED2_PROGRESSIONS.length)];
}

/** Diatonic chords of a major key by DESCENDING fifths: I–IV–vii°–iii–vi–ii–V, then
 *  back to I (the "circle of fifths"). */
export const CIRCLE_OF_FIFTHS: AdvChord[] = [
  c(0, "", "I"), c(5, "", "IV"), c(11, "dim", "vii°"), c(4, "m", "iii"),
  c(9, "m", "vi"), c(2, "m", "ii"), c(7, "", "V"),
];

/** Four adjacent chords of CIRCLE_OF_FIFTHS from a random start (roots falling by a
 *  fifth). Because each root is a fifth above the next, sounding any non-final,
 *  non-diminished chord as a dominant 7th makes it a SECONDARY DOMINANT (V7) of the
 *  following chord. Each eligible chord is domified with high probability and at least
 *  one always is, so every draw trains secondary dominants "through the circle". */
export function randomCircleOfFifths(rng: Rng = defaultRng): NamedProgression {
  const n = CIRCLE_OF_FIFTHS.length;
  const start = rng.int(n);
  const window = Array.from({ length: 4 }, (_, i) => ({ ...CIRCLE_OF_FIFTHS[(start + i) % n] }));
  // Eligible: not the last chord (needs a target) and not diminished (vii° can't be V7).
  const eligible = [0, 1, 2].filter((i) => window[i].quality !== "dim");
  let domCount = 0;
  for (const i of eligible) {
    if (rng.int(100) < 75) { window[i] = c(window[i].semitone, "7", window[i].roman.toUpperCase() + "7"); domCount++; }
  }
  if (domCount === 0 && eligible.length > 0) {
    const i = eligible[rng.int(eligible.length)];
    window[i] = c(window[i].semitone, "7", window[i].roman.toUpperCase() + "7");
    domCount = 1;
  }
  const note = "Four chords along the circle of fifths (roots falling by a fifth). "
    + (domCount > 1
      ? "Several chords are secondary dominants (V7 of the next), forming an applied-dominant chain that pulls hard toward the tonic."
      : "One chord is a secondary dominant (V7 of the next), intensifying the pull toward the tonic.");
  return { name: "Circle of 5ths", explanation: note, tonicMode: TrainingMode.Major, chords: window };
}

/** One draw-able 4-chord window of the diatonic circle of fifths, for the
 *  progression-library viewer. `id` ("W1".."W7") keys its song list. Carries its
 *  `chords` so the library's preview player can sound and voice it. */
export interface CircleWindow { id: string; romanLine: string; chords: AdvChord[]; }

/** The seven 4-chord windows randomCircleOfFifths can draw, in cycle order. */
export const CIRCLE_WINDOWS: CircleWindow[] = (() => {
  const n = CIRCLE_OF_FIFTHS.length;
  return Array.from({ length: n }, (_, start) => {
    const w = Array.from({ length: 4 }, (_, i) => CIRCLE_OF_FIFTHS[(start + i) % n]);
    return { id: `W${start + 1}`, romanLine: w.map((ch) => ch.roman).join("  –  "), chords: w };
  });
})();

/** Realise a circle window in `key` as concrete, playable chords (major-key spelling). */
export function resolveCircleWindow(win: CircleWindow, key: PitchClass): ResolvedChord[] {
  return win.chords.map((ch) => {
    const root = pcOf(key + ch.semitone);
    const prefer: Accidental = ch.roman.includes("#") ? "sharp" : ch.roman.includes("b") ? "flat" : "sharp";
    return { symbol: spellPc(root, prefer) + ch.quality, romanLabel: ch.roman, root };
  });
}

/** Roman-numeral line for a diatonic progression, e.g. "I – V – vi – IV". A
 *  harmonic-minor dominant bar shows "V" instead of the natural "v". */
export function romanLineFor(prog: Progression): string {
  const map = degreesMapFor(prog.mode);
  const dom = prog.dominantBars ?? [];
  return prog.degrees.map((d, i) =>
    (dom.includes(i) && prog.mode === TrainingMode.Minor) ? MINOR_DOMINANT.roman : (map.get(d)?.roman ?? String(d)),
  ).join("  –  ");
}

/** Canonical id for a diatonic progression: "maj:1,5,6,4" or "min:1,4,5,1@2"
 *  (mode prefix + degrees, optional @-joined dominantBars to distinguish
 *  natural-minor from harmonic-minor variants that share degrees). Used to track
 *  which progressions the user misses and to reconstruct them in the drill tab. */
export function progressionKey(prog: Progression): string {
  const prefix = prog.mode === TrainingMode.Major ? "maj" : "min";
  const dom = (prog.dominantBars ?? []).slice().sort((a, b) => a - b);
  return `${prefix}:${prog.degrees.join(",")}` + (dom.length ? `@${dom.join(",")}` : "");
}

/** A progression with NO tonic (no I/i chord = scale-degree 1) is harder to place
 *  by ear (nothing anchors the key), so the UI marks it as a difficult one. */
export function progressionLacksTonic(p: Progression): boolean {
  return !p.degrees.includes(1);
}

/** Scale degree of the RELATIVE key's tonic: a major key's relative minor has its i on
 *  degree 6, a minor key's relative major has its I on degree 3. */
function relativeTonicDegree(mode: TrainingMode): number {
  return mode === TrainingMode.Major ? 6 : 3;
}

/**
 * The RELATIVE key a tonic-less progression is actually heard in, or null when it has no
 * tonic at all.
 *
 * A major key and its relative minor share all seven chords, so a progression with no I
 * of its own may still OWN a tonic once renumbered from the other tonic: IV–V–iii–vi is
 * bVI–bVII–v–i, and vi–V–IV–V is i–bVII–bVI–bVII — a minor vamp that opens on its own i.
 * Both have a minor tonic; the only thing worth saying about the difference is WHERE it
 * sits (see [progressionRelativeTonicBar]) — and when it is bar 1 there is nothing to say
 * at all, the progression starts at home like any other. Only a progression holding
 * neither degree 1 nor the relative tonic is genuinely tonic-less.
 *
 * Major → the relative minor's tonic is degree 6; minor → the relative major's is
 * degree 3 (see [majorRelativeDegree]).
 */
export function progressionRelativeTonicMode(p: Progression): TrainingMode | null {
  if (!progressionLacksTonic(p)) return null;
  if (!p.degrees.includes(relativeTonicDegree(p.mode))) return null;
  return p.mode === TrainingMode.Major ? TrainingMode.Minor : TrainingMode.Major;
}

/** 1-based bar holding the first relative-tonic chord, or 0 when there is none. */
export function progressionRelativeTonicBar(p: Progression): number {
  if (progressionRelativeTonicMode(p) === null) return 0;
  return p.degrees.indexOf(relativeTonicDegree(p.mode)) + 1;
}

/** True when nothing needs flagging about [p]'s home: it has its own I, or it OPENS on the
 *  relative tonic (vi–V–IV–V is i–bVII–bVI–bVII — bar 1 is the minor i, so it starts at
 *  home and the ear has its anchor from the first chord). Only a tonic that arrives LATER
 *  (IV–V–iii–vi reaches i in bar 4) is worth a word, and only a progression with no tonic
 *  in either key is a hard one. */
export function progressionHomeIsObvious(p: Progression): boolean {
  return !progressionLacksTonic(p) || progressionRelativeTonicBar(p) === 1;
}

/** [p] renumbered from its relative tonic, or null when it has no such reading.
 *  `dominantBars` are dropped: they name a HARMONIC-minor V, which has no meaning once
 *  the same chords are read from the other tonic. */
export function progressionInRelativeKey(p: Progression): Progression | null {
  const mode = progressionRelativeTonicMode(p);
  if (mode === null) return null;
  return { mode, degrees: p.degrees.map((d) => degreeFromMajorRelative(majorRelativeDegree(d, p.mode), mode)) };
}

/** Roman line of [p] read from its relative tonic, e.g. "bVI  –  bVII  –  v  –  i" for a
 *  major IV–V–iii–vi. Empty when it has no relative-tonic reading. */
export function relativeRomanLineFor(p: Progression): string {
  const rel = progressionInRelativeKey(p);
  return rel ? romanLineFor(rel) : "";
}

/** Inverse of [progressionKey]; null if [key] is not a valid diatonic key. */
export function progressionFromKey(key: string): Progression | null {
  const m = /^(maj|min):(\d+(?:,\d+)*)(?:@(\d+(?:,\d+)*))?$/.exec(key);
  if (!m) return null;
  const mode = m[1] === "maj" ? TrainingMode.Major : TrainingMode.Minor;
  const degrees = m[2].split(",").map((s) => parseInt(s, 10));
  if (degrees.length !== 4 || degrees.some((d) => d < 1 || d > 7)) return null;
  const dominantBars = m[3] ? m[3].split(",").map((s) => parseInt(s, 10)) : undefined;
  return { mode, degrees, dominantBars };
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

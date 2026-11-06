// Area "chords" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- chords

import { parseChord, parseChordFull, inversionOf, isInversion, effectiveQuality, impliesTone, ChordShapeGenerator, cagedShapesFor, VoicingStyle, notesFrom, parsePitchClass, midiPitchClass, QUALITIES, inversionMidis, inversionIsSymmetric, inversionGuessCorrect, randomN2c, n2cAnswerLabel, movementCost, pickMinMovement, CHORD_DECOMPOSITIONS, decompositionFor, upperRootInterval } from "../../src/theory";
import { standard } from "../../src/theory/tunings";
import { check } from "./harness";

export function run(): void {

  // --- Chord parsing ---
  const cm7 = parseChord("Cmaj7")!;
  check("parse Cmaj7 root = C", cm7[0] === 0);
  check("Cmaj7 intervals = 1 3 5 7", JSON.stringify(cm7[1].intervals) === JSON.stringify([0, 4, 7, 11]));

  // --- CAGED: every Cmaj7 shape contains all 4 chord tones ---
  const gen = new ChordShapeGenerator(4, true, 3, VoicingStyle.Standard);
  const cmaj7Shapes = gen.shapesFor(cm7[0], cm7[1], standard, 14);
  const chordPcs = new Set(notesFrom(cm7[1], cm7[0]));
  check("Cmaj7 yields >= 5 shapes", cmaj7Shapes.length >= 5);
  let allContain = true;
  for (const s of cmaj7Shapes) {
    const pcs = new Set(s.notes.filter((n) => n !== null).map((n) => midiPitchClass(n!.midi)));
    for (const t of chordPcs) if (!pcs.has(t)) allContain = false;
  }
  check("every Cmaj7 shape contains all chord tones", allContain);

  // --- Inversions: 1st inversion of C major puts the 3rd (E) in the bass ---
  const cTriad = QUALITIES.get("")!;
  const firstInv = inversionMidis(60, cTriad, 1); // root C4=60
  check("C major 1st inversion bass note is E (pc 4)", firstInv[0] % 12 === 4);
  const rootPos = inversionMidis(60, cTriad, 0);
  check("C major root position bass note is C (pc 0)", rootPos[0] % 12 === 0);
  // Symmetric qualities (aug, dim7): every inversion is a transposition of root position,
  // so any guess is right (mirrors Kotlin symmetricChordsAcceptAnyInversion).
  check("only aug and dim7 are inversion-symmetric",
    [...QUALITIES.entries()].every(([sym, q]) => inversionIsSymmetric(q) === (sym === "aug" || sym === "dim7")));
  check("any inversion guess is right for aug", inversionGuessCorrect(QUALITIES.get("aug")!, 0, 2));
  check("a wrong inversion guess is wrong for a major triad", !inversionGuessCorrect(cTriad, 0, 2) && inversionGuessCorrect(cTriad, 1, 1));

  // --- Note2Chord: random challenge has a valid label ---
  const n2c = randomN2c();
  check("random N2C produces a known label", n2cAnswerLabel(n2c) !== "?");

  // --- Looper voice-leading: pickMinMovement returns the lowest-cost C voicing from a G shape ---
  const gShapes = cagedShapesFor(parsePitchClass("G"), parseChord("G")![1], standard, 14);
  const cShapes = cagedShapesFor(parsePitchClass("C"), parseChord("C")![1], standard, 14);
  const prev = gShapes[0];
  const idx = pickMinMovement(prev, cShapes);
  const chosenCost = movementCost(prev, cShapes[idx]);
  const isMin = cShapes.every((sh) => chosenCost <= movementCost(prev, sh));
  check("pickMinMovement returns the lowest-cost voicing", isMin && idx >= 0 && idx < cShapes.length);

  // --- Chord decomposition (#5): shell + a valid 3-note upper triad ---
  let decOk = CHORD_DECOMPOSITIONS.length > 0;
  for (const dec of CHORD_DECOMPOSITIONS) {
    if (!dec.shell.includes(0)) decOk = false;
    if (dec.upper.length !== 3) decOk = false;
    const g1 = dec.upper[1] - dec.upper[0], g2 = dec.upper[2] - dec.upper[1];
    if (!(g1 >= 3 && g1 <= 4 && g2 >= 3 && g2 <= 4)) decOk = false;
  }
  check("every decomposition has a root shell + stacked-thirds upper triad", decOk);
  // Cmaj7 shell+upper = {0,4,7,11}; upper root = E (pc 4)
  const maj7 = decompositionFor("maj7")!;
  const maj7pcs = new Set([...maj7.shell, ...maj7.upper].map((x) => ((x % 12) + 12) % 12));
  check("Cmaj7 decomposes to C + Em", [0, 4, 7, 11].every((p) => maj7pcs.has(p)) && upperRootInterval(maj7) === 4);

  // --- Slash chords and chord-sheet shorthand ---
  // Mirrors ChordLibrarySlashTest.kt. These pin the TS port against the Kotlin one:
  // a slash chord is an INVERSION when the bass is a chord tone, and a pedal when it
  // is not — the ports must agree on which, or the degree display would disagree
  // across platforms for the same song.
  check("slash bass parses and keeps the base chord", (() => {
    const c = parseChordFull("D/F#");
    return c !== null && c.root === 2 && c.quality.symbol === "" && c.bass === 6;
  })());
  check("parseChord ignores the bass so existing callers are unaffected",
    JSON.stringify(parseChord("D")) === JSON.stringify(parseChord("D/F#")) &&
    JSON.stringify(parseChord("Am7")) === JSON.stringify(parseChord("Am7/G")));
  check("a chord tone in the bass is an inversion numbered by chord tone",
    inversionOf(parseChordFull("D/F#")!) === 1 &&
    inversionOf(parseChordFull("D/A")!) === 2 &&
    inversionOf(parseChordFull("C/E")!) === 1 &&
    inversionOf(parseChordFull("C/G")!) === 2 &&
    inversionOf(parseChordFull("Bb7/Ab")!) === 3);
  // The bass is a tone OF the chord that the symbol did not spell. Both ports must
  // agree on which tone, or the degree display would differ per device.
  check("a 7th in the bass implies the 7th chord and inverts it", (() => {
    const c = parseChordFull("Bb/Ab")!;
    return impliesTone(c) && effectiveQuality(c).symbol === "7" &&
      inversionOf(c) === 3 && isInversion(c);
  })());
  check("a major 7th in the bass implies maj7", (() => {
    const c = parseChordFull("Eb/D")!;
    return effectiveQuality(c).symbol === "maj7" && inversionOf(c) === 3;
  })());
  check("a minor triad with a 7th in the bass implies m7",
    effectiveQuality(parseChordFull("Am/G")!).symbol === "m7" &&
    effectiveQuality(parseChordFull("Am/G#")!).symbol === "mMaj7");
  check("a 9th in the bass implies add9", (() => {
    const c = parseChordFull("C/D")!;
    return impliesTone(c) && effectiveQuality(c).symbol === "add9" &&
      inversionOf(c) === 3 && isInversion(c);
  })());
  check("a 6th in the bass implies the 6 chord",
    effectiveQuality(parseChordFull("Dm/B")!).symbol === "m6" &&
    effectiveQuality(parseChordFull("C/A")!).symbol === "6");
  check("an 11th in the bass appends the tone", (() => {
    const c = parseChordFull("C/F")!;
    return effectiveQuality(c).symbol === "add11" && effectiveQuality(c).intervals.includes(5);
  })());
  check("a written 7th chord is never re-implied", (() => {
    const c = parseChordFull("Bb7/Ab")!;
    return effectiveQuality(c).symbol === "7" && inversionOf(c) === 3 && !impliesTone(c);
  })());
  check("the bass is always a tone of the effective chord",
    ["D/F#", "C/G", "Bb/Ab", "C/D", "Dm/B", "C/F", "G/A"].every((s) => {
      const c = parseChordFull(s)!;
      return notesFrom(effectiveQuality(c), c.root).includes(c.bass!);
    }));
  check("no slash means root position", (() => {
    const c = parseChordFull("Cmaj7")!;
    return c.bass === null && inversionOf(c) === 0 && !isInversion(c);
  })());
  check("an unreadable bass rejects the whole symbol",
    parseChordFull("C/H") === null && parseChord("C/H") === null);
  check("site shorthand maps onto the canonical qualities",
    JSON.stringify(parseChord("Asus4")) === JSON.stringify(parseChord("A4")) &&
    JSON.stringify(parseChord("Dsus2")) === JSON.stringify(parseChord("D2")) &&
    JSON.stringify(parseChord("Amaj7")) === JSON.stringify(parseChord("AM7")) &&
    JSON.stringify(parseChord("Aaug")) === JSON.stringify(parseChord("A+")));
  check("capital M is major and lowercase m is minor",
    parseChord("AM7")![1].symbol === "maj7" && parseChord("Am7")![1].symbol === "m7");
  check("the power chord has no third", (() => {
    const q = parseChord("E5")![1];
    return JSON.stringify(q.intervals) === JSON.stringify([0, 7]);
  })());
  check("every symbol in the captured corpus parses", [
    "A/C#", "A/E", "Am/C", "Am7/D", "B7/F#", "Bb7/Ab", "C/E", "C/G", "Cm/G",
    "D/A", "D/F#", "D9/F#", "Dm7/C", "E/G#", "E7/B", "Eb/G", "Em/G", "F/A",
    "F/C", "F7/Eb", "Fm/Ab", "G/B", "G/D", "G7/B", "Gm/Bb", "Ebmmaj7/Gb",
    "A4", "B4", "D2", "E5", "AM7", "A+", "C7sus4", "D7b9", "Eb7b5", "Abm13",
  ].every((s) => parseChord(s) !== null && parseChordFull(s) !== null));
}

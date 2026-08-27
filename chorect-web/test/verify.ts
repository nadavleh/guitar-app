// Runtime sanity checks for the ported theory + audio engines. Built as an SSR
// bundle (`vite build --ssr`) and run under Node, so we exercise the real modules
// the browser uses. Mirrors a handful of the Kotlin JUnit assertions.

import {
  parseChord, parseChordFull, inversionOf, isInversion, effectiveQuality, impliesTone,
  parseKey, prefersFlats, transposeSymbol, transposeKey, degreeLabel, degreeLabels,
  ChordShapeGenerator, cagedShapesFor, VoicingStyle, notesFrom,
  scalePositionsFor, scaleNotesFrom, SCALES, parsePitchClass, midiPitchClass,
  TrainingMode, ChordTypeLevel, resolve as resolveDegree, degreeRoot, romanLabel,
  ADVANCED_PROGRESSIONS, resolveNamed, QUALITIES, inversionMidis, randomN2c, n2cAnswerLabel,
  romanIsModeAmbiguous, MAJOR_DEGREES, MINOR_DEGREES,
  ProgFocus, randomProgression, hasOneSixStep, thirdSixthPrimaryPool, thirdSixthContrastPool,
  THIRD_SIXTH_DRILL_PROGRESSIONS, THIRD_SIXTH_CONTRAST_DRILL, THIRD_SIXTH_CONTRAST_PERCENT, Progression, CarMode,
  progressionLacksTonic, progressionRelativeTonicMode, relativeRomanLineFor, romanLineFor,
  CAGED_SHAPES, CAGED_BOXES, CagedBox, CagedMode, ScaleSubset, patternCount, boxNumber,
  resolveBox, boxWindow, PRACTICE_RUN, TRIAD_GROUPS, triadRun, triadInversions, noteAt, fp, fpKey,
} from "../src/theory";
import { standard } from "../src/theory/tunings";
import {
  SONGS, SONGS_WITH_CHORDS, SONG_ARTISTS, SONG_LIBRARY_DIGEST, Song,
  songHasChords, songChordVocabulary, searchSongs,
} from "../src/theory/songLibrary";
import { DrumBlock, BUILTIN_BLOCKS, PresetTrack } from "../src/theory";
import { synthClick, clickAt } from "../src/app/woodClick";
import {
  PercussionCatalog, PercussionPattern, PercussionMeter, swungSlotMs, slotMs, voiceCount, SwingModel,
  movementCost, pickMinMovement, BUILTIN_PATTERNS,
  INTERVAL_CHOICES, intervalTargetMidi, CHORD_DECOMPOSITIONS, decompositionFor, upperRootInterval,
} from "../src/theory";
import { readFileSync } from "node:fs";
import { PluckedSynth, PitchDetector, analyzePitch, PercussionSynth, panGains, nearestRoot, pitchRate, renderCueBeep } from "../src/audio";

let passed = 0;
let failed = 0;
function check(name: string, cond: boolean): void {
  if (cond) { passed++; }
  else { failed++; console.error(`  ✗ ${name}`); }
}

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

// --- CAGED template purity: templates must not ADD foreign tones (e.g. the old
// dim E-shape sneaked in the dim7's maj6) ---
let templatesPure = true;
for (const sym of ["", "m", "7", "maj7", "m7", "m7b5", "dim7", "dim", "aug", "sus2", "sus4", "6", "m6"]) {
  const q = QUALITIES.get(sym)!;
  for (let r = 0; r < 12; r++) {
    const allowed = new Set(notesFrom(q, r));
    for (const s of cagedShapesFor(r, q, standard, 14)) {
      for (const n of s.notes) if (n !== null && !allowed.has(midiPitchClass(n.midi))) templatesPure = false;
    }
  }
}
check("every CAGED template sounds only chord tones", templatesPure);

// --- CAGED templates: C major → 5 ascending shapes ---
const cMajCaged = cagedShapesFor(0, parseChord("C")![1], standard, 14);
check("C major has 5 CAGED shapes", cMajCaged.length === 5);
let ascending = true;
for (let i = 1; i < cMajCaged.length; i++) if (cMajCaged[i].position < cMajCaged[i - 1].position) ascending = false;
check("CAGED shapes sorted ascending by position", ascending);

// --- Scales: A minor pentatonic = A C D E G ---
const aRoot = parsePitchClass("A");
const minPent = SCALES.get("minor pentatonic")!;
const notes = new Set(scaleNotesFrom(minPent, aRoot));
check("A minor pentatonic = {A,C,D,E,G}", [9, 0, 2, 4, 7].every((p) => notes.has(p)) && notes.size === 5);
const aPositions = scalePositionsFor(aRoot, minPent, standard, 14);
check("A minor pentatonic yields positions", aPositions.length >= 3);

// --- Pitch math: A4 @ 440 → MIDI 69, 0 cents ---
const est = analyzePitch(440, 440);
check("analyze(440) → midi 69", est.midi === 69);
check("analyze(440) → ~0 cents", Math.abs(est.cents) < 0.01);

// --- Karplus-Strong: bounded, finite output ---
const synth = new PluckedSynth(44100);
const buf = synth.synthesize(69, 0.5, 1, 0.997, 0.6);
let bounded = true;
for (const v of buf) if (!Number.isFinite(v) || Math.abs(v) > 0.61) bounded = false;
check("synth output is finite & within amplitude", bounded && buf.length === 22050);

// --- brightnessDecay default (1.0) is a provable no-op vs. the pre-existing 5-arg call ---
const bufDefault = synth.synthesize(69, 0.5, 1, 0.997, 0.6, 1.0);
check("brightnessDecay=1.0 is identical to omitting it", buf.length === bufDefault.length && buf.every((v, i) => v === bufDefault[i]));

// --- Panner: constant-power stereo gains ---
const [centerL, centerR] = panGains(0);
check("panGains(0) center ≈ (0.7071, 0.7071)", Math.abs(centerL - 0.70710678) < 1e-6 && Math.abs(centerR - 0.70710678) < 1e-6);
for (const p of [-1, -0.5, 0, 0.3, 1]) {
  const [l, r] = panGains(p);
  if (Math.abs(l * l + r * r - 1) >= 1e-9) { check(`panGains(${p}) constant power`, false); }
  else { check(`panGains(${p}) constant power`, true); }
}
const [hardLeftL, hardLeftR] = panGains(-1);
check("panGains(-1) hard-left = (1, 0)", Math.abs(hardLeftL - 1) < 1e-9 && Math.abs(hardLeftR - 0) < 1e-9);

// --- Sampled guitar voices: nearest-root selection + playback-rate math ---
check("nearestRoot ties lower", nearestRoot([40, 44, 48], 42) === 40);
check("pitchRate octave", pitchRate(72, 60) === 2);

// --- YIN: detect a synthetic 220 Hz sine ---
const sr = 44100;
const sine = new Float32Array(2048);
for (let i = 0; i < sine.length; i++) sine[i] = 0.8 * Math.sin((2 * Math.PI * 220 * i) / sr);
const detected = new PitchDetector(sr).detect(sine);
check("YIN detects 220 Hz sine within 2 Hz", detected !== null && Math.abs(detected - 220) < 2);

// --- Ear training: diatonic resolve ---
const vChord = resolveDegree(5, parsePitchClass("C"), TrainingMode.Major, ChordTypeLevel.Sevenths);
check("V7 in C major = G7", vChord.symbol === "G7" && vChord.romanLabel === "V7");
const iiChord = resolveDegree(2, parsePitchClass("C"), TrainingMode.Major, ChordTypeLevel.Sevenths);
check("ii7 in C major = Dm7, labelled ii7 (not iim7)", iiChord.symbol === "Dm7" && iiChord.romanLabel === "ii7");
check("degreeRoot vi in C major = A", degreeRoot(parsePitchClass("C"), 6, TrainingMode.Major) === 9);
check("romanLabel vii°+m7b5 = vii°7", romanLabel("vii°", "m7b5") === "vii°7");

// --- Major/minor-ambiguous Romans (challenge answer disambiguation) ---
// The harmonic-minor dominant prints exactly like the major key's V7 but is a
// completely different chord, so only that V-family gets the "(minor)" marker.
const minorV7 = resolveDegree(5, parsePitchClass("A"), TrainingMode.Minor, ChordTypeLevel.Sevenths, undefined, true);
check("harmonic-minor V7 in A minor = E7, labelled V7 like the major key's",
  minorV7.symbol === "E7" && minorV7.romanLabel === "V7" && vChord.romanLabel === minorV7.romanLabel);
check("V-family Romans are mode-ambiguous",
  ["V", "V6", "V7", "V9", "V11", "V13"].every(romanIsModeAmbiguous));
check("every other degree of both rows is unambiguous",
  [...MAJOR_DEGREES.values(), ...MINOR_DEGREES.values()].map((d) => d.roman)
    .filter((r) => r !== "V").every((r) => !romanIsModeAmbiguous(r)) &&
  !romanIsModeAmbiguous("VI7") && !romanIsModeAmbiguous("v7") && !romanIsModeAmbiguous(""));

// --- Ear training: every advanced progression resolves to parseable chords in any key ---
let advOk = true;
for (const np of ADVANCED_PROGRESSIONS) {
  for (let key = 0; key < 12; key++) {
    for (const rc of resolveNamed(np, key)) {
      if (parseChord(rc.symbol) === null) advOk = false;
    }
  }
}
check("all advanced progressions resolve to parseable chords in all keys", advOk);

// --- Inversions: 1st inversion of C major puts the 3rd (E) in the bass ---
const cTriad = QUALITIES.get("")!;
const firstInv = inversionMidis(60, cTriad, 1); // root C4=60
check("C major 1st inversion bass note is E (pc 4)", firstInv[0] % 12 === 4);
const rootPos = inversionMidis(60, cTriad, 0);
check("C major root position bass note is C (pc 0)", rootPos[0] % 12 === 0);

// --- Note2Chord: random challenge has a valid label ---
const n2c = randomN2c();
check("random N2C produces a known label", n2cAnswerLabel(n2c) !== "?");

// --- Percussion: dynamic kit, add/remove, pattern round-trip ---
const M = PercussionMeter.DEFAULT;
const empty = PercussionPattern.empty();
check("default kit is surdo, tamborim, bongo",
  empty.instruments.map((i) => i.id).join(",") === "surdo,tamborim,bongo" &&
  empty.instruments.map((i) => i.id).join(",") === PercussionCatalog.DEFAULT_KIT.map((i) => i.id).join(","));
// cycle: null → 0 → 1 → 2 → null for Surdo (3 voices)
let p = empty;
const cy: (number | null)[] = [];
for (let i = 0; i < 4; i++) { p = p.cycled(PercussionCatalog.Surdo, 0); cy.push(p.voiceAt(PercussionCatalog.Surdo, 0)); }
check("Surdo cell cycles 0,1,2,null", cy[0] === 0 && cy[1] === 1 && cy[2] === 2 && cy[3] === null);
check("Surdo has 3 voices, Pandeiro 8 (recorded articulations)", voiceCount(PercussionCatalog.Surdo) === 3 && voiceCount(PercussionCatalog.Pandeiro) === 8);
// add an instrument → silent row appended; round-trips through encode/decode
const cuica = PercussionCatalog.byId("cuica")!;
const withCuica = empty.addInstrument(cuica).cycled(cuica, 2).cycled(PercussionCatalog.Surdo, 0);
check("addInstrument appends a row", withCuica.hasInstrument(cuica) &&
  withCuica.instruments.length === PercussionCatalog.DEFAULT_KIT.length + 1);
const rt2 = PercussionPattern.decode(withCuica.encode());
check("pattern with added instrument round-trips", rt2 !== null && rt2.encode() === withCuica.encode());
check("removeInstrument drops the row", !withCuica.removeInstrument(cuica).hasInstrument(cuica));
// decode skips unknown instrument ids
const withBogus = withCuica.encode() + "|bogus=" + Array.from({ length: withCuica.slots }, () => "-").join(",");
const rtBogus = PercussionPattern.decode(withBogus);
check("decode skips unknown instrument ids", rtBogus !== null && rtBogus.instruments.every((i) => i.id !== "bogus"));
// per-track swing: encoded as an "@N" id suffix, round-trips, drops with the track
const swung = withCuica.withTrackSwing("cuica", 33);
check("per-track swing encodes as id@33 and round-trips",
  swung.encode().includes("cuica@33=") &&
  PercussionPattern.decode(swung.encode())?.trackSwingOf("cuica") === 33 &&
  swung.withTrackSwing("cuica", 0).encode().includes("cuica@") === false &&
  swung.removeInstrument(cuica).trackSwing.size === 0);
// per-track volume: "%N" suffix, combines with swing on one head, round-trips
const quiet = swung.withTrackVolume("cuica", 20);
check("per-track volume encodes as id@33%20 and round-trips",
  quiet.encode().includes("cuica@33%20=") &&
  PercussionPattern.decode(quiet.encode())?.trackVolumeOf("cuica") === 20 &&
  quiet.withTrackVolume("cuica", 100).encode().includes("%") === false);

// --- Swing (samba microtiming): anchors 1st/2nd, anticipates 3rd & 4th; preserves loop length; 1/16 only ---
const straightSum = Array.from({ length: 16 }, (_, i) => swungSlotMs(i, 100, 0, M)).reduce((a, b) => a + b, 0);
const swungSum = Array.from({ length: 16 }, (_, i) => swungSlotMs(i, 100, 60, M)).reduce((a, b) => a + b, 0);
check("straight slot = base slotMs", Math.abs(swungSlotMs(0, 100, 0, M) - slotMs(100)) < 1.5);
check("swing preserves total loop length (±a few ms rounding)", Math.abs(straightSum - swungSum) <= 16);
// At 100 % the feel is HEMIOLA-based (SwingModel), not the retired "2nd anchored"
// model: the 2nd 16th is DELAYED toward 1/3 of the beat, the 3rd stays at 1/2, and the
// 4th is pulled back to 2/3. Mirrors the two live Kotlin tests in PercussionPatternTest
// (`full Hemiola swing delays the 2nd...` and `...onsets sit at the hemiola positions`).
// bpm 120 makes the base slot exactly 125 ms, so the onsets are checkable to the ms.
{
  const base = slotMs(120);          // 125 ms
  const hem = SwingModel.Hemiola;
  const d = [0, 1, 2, 3].map((i) => swungSlotMs(i, 120, 100, M, hem));
  check("hemiola swing: 1st→2nd stretches, 2nd→3rd shrinks, 4th→beat stretches",
    d[0] > base && d[1] < base && d[3] > base);
  check("hemiola swing keeps the beat length intact", d[0] + d[1] + d[2] + d[3] === base * 4);
  // Absolute onsets, in slot units: [0, 4/3, 2, 8/3] of the beat.
  const onset = (slot: number) => d.slice(0, slot).reduce((a, b) => a + b, 0);
  check("hemiola onsets sit at 0, 1/3, 1/2, 2/3 of the beat",
    onset(0) === 0 && onset(1) === Math.round((4 / 3) * base) &&
    onset(2) === 2 * base && onset(3) === Math.round((8 / 3) * base));
  // The DEFAULT model differs only in that it also nudges the 1st 16th (+d/2) and
  // pulls the 4th half as far — so onset(0) is NOT on the beat there.
  const dd = [0, 1, 2, 3].map((i) => swungSlotMs(i, 120, 100, M, SwingModel.Default));
  check("default model also preserves the beat length", dd[0] + dd[1] + dd[2] + dd[3] === base * 4);
  check("default model nudges the 1st 16th, hemiola does not",
    swungSlotMs(0, 120, 100, M, SwingModel.Default) !== swungSlotMs(0, 120, 100, M, hem));
}
// non-1/16 grids ignore swing entirely
const eighths = new PercussionMeter(2, 2, 4, 8);
check("swing does nothing off a 1/16 grid", swungSlotMs(1, 100, 100, eighths) === slotMs(100, 8));

// --- Percussion synth: every catalog voice renders bounded, finite audio ---
const psynth = new PercussionSynth(44100);
let psOk = true;
for (const inst of PercussionCatalog.ALL) {
  for (let v = 0; v < voiceCount(inst); v++) {
    const buf = psynth.synthesize(inst, v);
    if (buf.length === 0) psOk = false;
    for (const x of buf) if (!Number.isFinite(x) || Math.abs(x) > 1.001) psOk = false;
  }
}
check("every percussion voice renders finite, bounded audio", psOk);

// --- Looper voice-leading: pickMinMovement returns the lowest-cost C voicing from a G shape ---
const gShapes = cagedShapesFor(parsePitchClass("G"), parseChord("G")![1], standard, 14);
const cShapes = cagedShapesFor(parsePitchClass("C"), parseChord("C")![1], standard, 14);
const prev = gShapes[0];
const idx = pickMinMovement(prev, cShapes);
const chosenCost = movementCost(prev, cShapes[idx]);
const isMin = cShapes.every((sh) => chosenCost <= movementCost(prev, sh));
check("pickMinMovement returns the lowest-cost voicing", isMin && idx >= 0 && idx < cShapes.length);

// --- Built-in grooves are valid (16 slots, in-range voice indices) ---
let builtinsOk = BUILTIN_PATTERNS.length >= 5;
for (const b of BUILTIN_PATTERNS) {
  const rt = PercussionPattern.decode(b.pattern.encode());
  if (!rt || rt.encode() !== b.pattern.encode()) builtinsOk = false;
}
check("built-in grooves are valid & round-trip", builtinsOk);
// Pins the twin of PercussionBuiltins.CASA_2_LEVADA — his Casa 2 export, the one
// groove with no surdo (two tamborims, the first at 97 %).
check("the Casa 2 cavaco levada groove is in the list, exactly as exported", (() => {
  const b = BUILTIN_PATTERNS.find((x) => x.name === "Casa 2 — Cavaco Levada");
  if (!b || b.bpm !== 92) return false;
  return b.pattern.encode() === "M:2,2,4,16;tamborim%97=0,-,0,1,2,0,-,0,1,2,0,1,0,-,0,1|tamborim#2=0,-,1,0,1,0,-,0,1,0,-,0,1,0,0,1";
})());

// --- Drum accents: toggle, survive voice cycling, round-trip encode/decode ---
{
  const surdo = PercussionCatalog.Surdo;
  let ap = PercussionPattern.empty().cycled(surdo, 0).accentToggled(surdo, 0);
  check("accent toggles on and keeps the voice", ap.isAccented(surdo, 0) && ap.voiceAt(surdo, 0) === 0);
  ap = ap.cycled(surdo, 0);
  check("accent survives voice cycling", ap.isAccented(surdo, 0) && ap.voiceAt(surdo, 0) === 1);
  const art = PercussionPattern.decode(ap.encode());
  check("accent round-trips encode/decode", art !== null && art.isAccented(surdo, 0) && art.voiceAt(surdo, 0) === 1);
  check("accent toggles off / silent no-op", !ap.accentToggled(surdo, 0).isAccented(surdo, 0) && ap.accentToggled(surdo, 5).encode() === ap.encode());
  check("decode rejects accented out-of-range voice", PercussionPattern.decode(PercussionPattern.empty().encode().replace("-", "109")) === null);
}

// --- Interval trainer (#6) ---
check("13 intervals from unison to octave", INTERVAL_CHOICES.length === 13 &&
  INTERVAL_CHOICES[0].longName === "unison" && INTERVAL_CHOICES[12].longName === "octave");
check("intervalTargetMidi ascends/descends", intervalTargetMidi(60, 7, true) === 67 && intervalTargetMidi(60, 7, false) === 53);

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

// --- 3rd-vs-6th drill pools: must match the Kotlin pools EXACTLY (same entries,
//     same order) or the two platforms drill different progressions. The expected
//     lists below were dumped from EarTraining.thirdSixthPrimaryPool/ContrastPool. ---
const poolKey = (p: Progression): string =>
  `${p.degrees.join(",")}|${[...(p.dominantBars ?? [])].sort((a, b) => a - b).join(",")}`;
const poolKeys = (ps: Progression[]): string => ps.map(poolKey).join(" / ");

check("3rd-vs-6th primary pool (major) matches Kotlin", poolKeys(thirdSixthPrimaryPool(TrainingMode.Major)) ===
  "1,3,6,4| / 1,6,3,4| / 1,3,6,1| / 1,6,3,5| / 4,3,6,1| / 1,3,6,5| / 1,3,4,5| / 4,5,3,6| / 1,3,4,1| / 1,3,2,5| / 1,3,1,4|");
check("3rd-vs-6th contrast pool (major) matches Kotlin", poolKeys(thirdSixthContrastPool(TrainingMode.Major)) ===
  "1,6,4,1| / 1,6,5,4| / 4,5,1,6| / 1,6,4,5| / 1,6,2,5| / 6,2,5,1| / 1,2,5,6|");
check("3rd-vs-6th primary pool (minor, harmonic on) matches Kotlin", poolKeys(thirdSixthPrimaryPool(TrainingMode.Minor)) ===
  "1,3,6,4| / 1,6,3,5| / 1,3,6,7| / 1,6,3,4| / 1,6,3,7| / 1,4,7,3| / 1,3,7,4| / 1,6,3,5|3 / 1,3,6,5|3");
check("3rd-vs-6th contrast pool (minor, harmonic on) matches Kotlin", poolKeys(thirdSixthContrastPool(TrainingMode.Minor)) ===
  "1,6,4,5| / 1,6,7,5| / 1,6,4,1| / 1,6,7,1| / 1,6,2,5|3 / 1,6,4,5|3");
check("3rd-vs-6th primary pool (minor, harmonic off) matches Kotlin", poolKeys(thirdSixthPrimaryPool(TrainingMode.Minor, false)) ===
  "1,3,6,4| / 1,6,3,5| / 1,3,6,7| / 1,6,3,4| / 1,6,3,7| / 1,4,7,3| / 1,3,7,4|");
check("3rd-vs-6th contrast pool (minor, harmonic off) matches Kotlin", poolKeys(thirdSixthContrastPool(TrainingMode.Minor, false)) ===
  "1,6,4,5| / 1,6,7,5| / 1,6,4,1| / 1,6,7,1|");

check("1<->6 step counts the loop wrap", hasOneSixStep([1, 6, 4, 5]) && hasOneSixStep([1, 2, 5, 6]) &&
  hasOneSixStep([6, 2, 5, 1]) && !hasOneSixStep([1, 5, 6, 4]) && !hasOneSixStep([1, 4, 6, 5]));
check("every drill entry has an adjacent 3<->6 pair", THIRD_SIXTH_DRILL_PROGRESSIONS.every((p) =>
  p.degrees.some((a, i) => { const b = p.degrees[(i + 1) % p.degrees.length]; return (a === 3 && b === 6) || (a === 6 && b === 3); })));
check("primary pools are all degree-3, contrast pools none", [TrainingMode.Major, TrainingMode.Minor].every((m) =>
  thirdSixthPrimaryPool(m).every((p) => p.degrees.includes(3)) &&
  thirdSixthContrastPool(m).every((p) => !p.degrees.includes(3) && hasOneSixStep(p.degrees))));

// The weighted draw: ~THIRD_SIXTH_CONTRAST_PERCENT % of questions are 1<->6 foils.
for (const m of [TrainingMode.Major, TrainingMode.Minor]) {
  let seed = 20260818;
  const rng = { int: (b: number) => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed % b; }, bool: () => false };
  const n = 4000;
  let foils = 0;
  let inAPool = true;
  const contrastKeys = new Set(thirdSixthContrastPool(m).map(poolKey));
  for (let i = 0; i < n; i++) {
    const p = randomProgression(m, rng, ProgFocus.ThirdVsSixth);
    if (!p.degrees.includes(3) && !contrastKeys.has(poolKey(p))) inAPool = false;
    if (!p.degrees.includes(3)) foils++;
  }
  const share = (foils / n) * 100;
  check(`3rd-vs-6th draw (${m}) stays inside the two pools`, inAPool);
  check(`3rd-vs-6th draw (${m}) is ~${THIRD_SIXTH_CONTRAST_PERCENT}% foils (got ${share.toFixed(1)}%)`,
    share > THIRD_SIXTH_CONTRAST_PERCENT - 4 && share < THIRD_SIXTH_CONTRAST_PERCENT + 4);
}

// The I->iii drill must be untouched by the focus refactor.
{
  let seed = 7;
  const rng = { int: (b: number) => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed % b; }, bool: () => false };
  let ok = true;
  for (let i = 0; i < 200; i++) {
    const p = randomProgression(TrainingMode.Minor, rng, ProgFocus.Iiii);
    if (p.mode !== TrainingMode.Major || p.degrees[0] !== 1 || p.degrees[1] !== 3) ok = false;
  }
  check("I->iii drill still draws major I-iii openers", ok);
}


// --- Car mode: the reveal/timing schedule must match Kotlin CarMode exactly ---
check("CarMode constants match Kotlin", CarMode.ROUNDS === 5 && CarMode.BEEPS === 3 &&
  CarMode.BEEP_GAP_MS === 500 && CarMode.LEAD_IN_MS === 1500 && CarMode.GAP_MS === 4000 &&
  CarMode.BEEP_HZ === 880 && CarMode.BEEP_MS === 140 && CarMode.BEEP_PEAK === 0.55 &&
  CarMode.BEEP_ATTACK_MS === 5);
check("round 1 reveals nothing, round 5 reveals every slot",
  CarMode.revealedSlots(1, 4) === 0 && CarMode.revealedSlots(5, 4) === 4);
// Indexed by round 0..5 — pinned as a literal so nobody "optimises" the ramp away.
// This is the shape the spec asks for: one more chord per play, on a 4-chord progression.
check("reveal ramp is exactly [0,0,1,2,3,4] over rounds 0..5",
  [0, 1, 2, 3, 4, 5].map((r) => CarMode.revealedSlots(r, 4)).join(",") === "0,0,1,2,3,4");
// The advanced library has 6-, 7- and 8-chord entries; stepping by one left Pachelbel's
// Canon showing 4 of 8 when the exercise ended, so the answer was never given.
check("the last round always shows the whole progression",
  [1, 2, 3, 4, 5, 6, 7, 8].every((n) => CarMode.revealedSlots(CarMode.ROUNDS, n) === n));
check("a long progression reveals more than one slot per round",
  [1, 2, 3, 4, 5].map((r) => CarMode.revealedSlots(r, 8)).join(",") === "0,2,4,6,8" &&
  [1, 2, 3, 4, 5].map((r) => CarMode.revealedSlots(r, 3)).join(",") === "0,1,2,3,3");
check("the reveal ramp never goes backwards", [1, 2, 3, 4, 5, 6, 7, 8].every((n) => {
  let prev = 0;
  for (let r = 1; r <= CarMode.ROUNDS; r++) {
    const v = CarMode.revealedSlots(r, n);
    if (v < prev || v > n) return false;
    prev = v;
  }
  return true;
}));
check("reveal count clamps to the slot count and never goes negative",
  CarMode.revealedSlots(4, 3) === 3 && CarMode.revealedSlots(5, 3) === 3 &&
  CarMode.revealedSlots(0, 4) === 0 && CarMode.revealedSlots(-1, 4) === 0);
// A round's newly-earned slot must wait for the playhead: reading the answer before
// hearing the chord defeats the drill. Mirrors CarModeTest's playhead cases exactly.
check("a round's new slot appears only when the playhead reaches it",
  CarMode.revealedSlots(3, 4) === 2 &&
  CarMode.revealedSlotsAt(3, 0, 4) === 1 &&
  CarMode.revealedSlotsAt(3, 1, 4) === 2 &&
  CarMode.revealedSlotsAt(3, 3, 4) === 2);

// --- Car-mode chord voice: the spoken labels must match CarModeTest word for word ---
check("speechFor turns case into a spoken quality",
  CarMode.speechFor("IV") === "4 major" && CarMode.speechFor("iv") === "4 minor" &&
  CarMode.speechFor("I") === "1 major" && CarMode.speechFor("i") === "1 minor");
check("speechFor maps every numeral to its degree number",
  ["I", "II", "III", "IV", "V", "VI", "VII"].map((r) => CarMode.speechFor(r)).join("|") ===
    "1 major|2 major|3 major|4 major|5 major|6 major|7 major");
check("speechFor speaks accidentals before the degree",
  CarMode.speechFor("bVI") === "flat 6 major" && CarMode.speechFor("bVII") === "flat 7 major" &&
  CarMode.speechFor("#IV") === "sharp 4 major");
check("speechFor lets a suffix quality override the case",
  CarMode.speechFor("vii°") === "7 diminished" && CarMode.speechFor("ii°") === "2 diminished" &&
  CarMode.speechFor("vii°7") === "7 diminished 7" && CarMode.speechFor("#IV°7") === "sharp 4 diminished 7" &&
  CarMode.speechFor("V+") === "5 augmented");
check("speechFor calls an uppercase bare 7th a dominant, but not a 6th or add9",
  CarMode.speechFor("V7") === "5 dominant 7" && CarMode.speechFor("bVII7") === "flat 7 dominant 7" &&
  CarMode.speechFor("V13") === "5 dominant 13" && CarMode.speechFor("I6") === "1 major 6" &&
  CarMode.speechFor("IVadd9") === "4 major add 9");
check("speechFor says maj once, not doubled by the uppercase case",
  CarMode.speechFor("Imaj7") === "1 major 7" && CarMode.speechFor("bVImaj7") === "flat 6 major 7" &&
  CarMode.speechFor("IVmaj7#11") === "4 major 7 sharp 11");
check("speechFor keeps a lowercase 7th minor",
  CarMode.speechFor("i7") === "1 minor 7" && CarMode.speechFor("ii7") === "2 minor 7" &&
  CarMode.speechFor("vi9") === "6 minor 9");
check("speechFor is silent on an unparseable label",
  CarMode.speechFor("") === "" && CarMode.speechFor("—") === "" && CarMode.speechFor("?") === "");
check("speechFor speaks every Roman the diatonic library can print",
  [...Object.values(MAJOR_DEGREES), ...Object.values(MINOR_DEGREES)].every((info) =>
    [info.roman, romanLabel(info.roman, info.seventhQuality), romanLabel(info.roman, info.extendedQuality),
      ...info.extendedOptions.map(([, suffix]) => info.roman + suffix)]
      .every((r) => CarMode.speechFor(r).length > 0)));

check("the spoken level defaults high and clamps into the slider range",
  CarMode.SPEECH_VOLUME >= 0.8 && CarMode.SPEECH_VOLUME_MAX === 1 &&
  CarMode.SPEECH_VOLUME >= CarMode.SPEECH_VOLUME_MIN && CarMode.SPEECH_VOLUME <= CarMode.SPEECH_VOLUME_MAX &&
  CarMode.clampSpeechVolume(4) === CarMode.SPEECH_VOLUME_MAX &&
  CarMode.clampSpeechVolume(-1) === CarMode.SPEECH_VOLUME_MIN &&
  CarMode.clampSpeechVolume(0.5) === 0.5);

// --- Relative-tonic reading: a "no tonic" progression that resolves in the other key ---
const royalRoad: Progression = { mode: TrainingMode.Major, degrees: [4, 5, 3, 6] };
check("IV-V-iii-vi resolves in the relative minor as bVI-bVII-v-i",
  progressionLacksTonic(royalRoad) &&
  progressionRelativeTonicMode(royalRoad) === TrainingMode.Minor &&
  relativeRomanLineFor(royalRoad) === "bVI  –  bVII  –  v  –  i");
const hanging: Progression = { mode: TrainingMode.Major, degrees: [6, 5, 4, 5] };
check("a progression ending away from the relative tonic stays unresolved",
  progressionLacksTonic(hanging) && progressionRelativeTonicMode(hanging) === null &&
  relativeRomanLineFor(hanging) === "");
check("a progression with its own tonic has no relative reading",
  progressionRelativeTonicMode({ mode: TrainingMode.Major, degrees: [1, 5, 6, 4] }) === null);
check("a minor progression ending on bIII reads in the relative major",
  progressionRelativeTonicMode({ mode: TrainingMode.Minor, degrees: [4, 5, 6, 3] }) === TrainingMode.Major &&
  relativeRomanLineFor({ mode: TrainingMode.Minor, degrees: [4, 5, 6, 3] }) === "ii  –  iii  –  IV  –  I");
check("the lead-in and round 1 reveal nothing wherever the playhead is",
  [-1, 0, 1, 2, 3].every((p) => CarMode.revealedSlotsAt(0, p, 4) === 0 && CarMode.revealedSlotsAt(1, p, 4) === 0) &&
  CarMode.revealedSlotsAt(2, -5, 4) === 0 &&
  CarMode.revealedSlotsAt(CarMode.ROUNDS, 99, 4) === 4);
check("playhead-gated reveals never un-reveal and never outrun the schedule", (() => {
  let prev = 0;
  for (let r = 1; r <= CarMode.ROUNDS; r++) {
    for (let p = 0; p < 4; p++) {
      const now = CarMode.revealedSlotsAt(r, p, 4);
      if (now < prev || now > CarMode.revealedSlots(r, 4)) return false;
      prev = now;
    }
  }
  return prev === 4;
})());
check("a long progression still reaches a full reveal on its last bar",
  [1, 2, 3, 4, 5, 6, 7, 8].every((n) => CarMode.revealedSlotsAt(CarMode.ROUNDS, n - 1, n) === n));
check("exercise at 140bpm over 4 bars is ~36s",
  CarMode.exerciseMs(140, 4) >= 35_000 && CarMode.exerciseMs(140, 4) <= 37_000);
check("exerciseMs matches Kotlin integer division exactly", CarMode.exerciseMs(140, 4) === 35740);
check("exerciseMs doubles with the bars and clamps a nonsense bpm",
  (CarMode.exerciseMs(140, 8) - CarMode.LEAD_IN_MS) === 2 * (CarMode.exerciseMs(140, 4) - CarMode.LEAD_IN_MS) &&
  CarMode.exerciseMs(0, 4) > 0);

// --- Car-mode cue beep: same envelope as Kotlin CueBeep ---
{
  const beep = renderCueBeep(CarMode.BEEP_HZ, CarMode.BEEP_MS, 44100, CarMode.BEEP_PEAK, CarMode.BEEP_ATTACK_MS);
  check("cue beep length is the requested duration", beep.length === Math.trunc(44100 * 140 / 1000));
  check("cue beep stays finite and within peak",
    beep.every((s) => Number.isFinite(s) && Math.abs(s) <= CarMode.BEEP_PEAK + 1e-6));
  check("cue beep attacks from silence (no onset click)", Math.abs(beep[0]) < 0.02);
  check("cue beep decays away", Math.abs(beep[beep.length - 1]) < 0.05 * CarMode.BEEP_PEAK);
  let loudest = 0;
  for (let i = 1; i < beep.length; i++) if (Math.abs(beep[i]) > Math.abs(beep[loudest])) loudest = i;
  check("cue beep peaks early (attack then decay)", loudest < beep.length * 0.15);
  let crossings = 0;
  for (let i = 1; i < beep.length; i++) if ((beep[i - 1] < 0) !== (beep[i] < 0)) crossings++;
  const expected = Math.trunc(2 * CarMode.BEEP_HZ * CarMode.BEEP_MS / 1000);
  check(`cue beep really is ${CarMode.BEEP_HZ}Hz (${crossings} crossings, expected ~${expected})`,
    Math.abs(crossings - expected) <= 4);
  check("cue beep of zero length is empty, not a throw",
    renderCueBeep(CarMode.BEEP_HZ, 0, 44100, CarMode.BEEP_PEAK, CarMode.BEEP_ATTACK_MS).length === 0);
}


// The bare Roman "V" reads identically in a major key and in harmonic minor, so car
// mode (which never shows the key) has to mark which one it is - same rule the
// challenge answer line uses. Everything else is separated by case or an accidental.
check("only the V family needs a mode tag in car mode",
  romanIsModeAmbiguous("V") && romanIsModeAmbiguous("V7") && romanIsModeAmbiguous("V9") &&
  !romanIsModeAmbiguous("v") && !romanIsModeAmbiguous("iii") && !romanIsModeAmbiguous("bIII") &&
  !romanIsModeAmbiguous("IV") && !romanIsModeAmbiguous("iv") && !romanIsModeAmbiguous("bVII"));

// No mode/toggle combination may go degenerate: the library alone left the minor
// contrast pool with ONE entry once harmonic minor was off, so 30 % of questions
// repeated the same progression. Mirrors the Kotlin guard.
for (const m of [TrainingMode.Major, TrainingMode.Minor]) {
  for (const hm of [true, false]) {
    check(`3rd-vs-6th pools stay varied (${m}, harmonic=${hm})`,
      thirdSixthPrimaryPool(m, hm).length >= 6 && thirdSixthContrastPool(m, hm).length >= 4);
  }
}
check("every contrast drill entry is a 1<->6 move with no degree 3",
  THIRD_SIXTH_CONTRAST_DRILL.every((p) => hasOneSixStep(p.degrees) && !p.degrees.includes(3) &&
    (p.dominantBars ?? []).length === 0));

// --- Version numbers must not drift ---
// package.json had silently fallen 33 minor versions behind APP_VERSION (2.38.9 vs
// 2.71.3) despite the "keep in sync" comment on the constant. Read the file rather than
// importing it: resolveJsonModule is off in tsconfig, and verify runs under Node.
{
  const read = (rel: string) => readFileSync(new URL(rel, import.meta.url), "utf8");
  const pkgVersion = (JSON.parse(read("../package.json")) as { version: string }).version;
  // Read appState.ts as TEXT rather than importing it: this script runs under plain Node,
  // and importing the app-state module would execute its browser-facing module scope.
  const appVersion = /APP_VERSION\s*=\s*"([^"]+)"/.exec(read("../src/app/appState.ts"))?.[1];
  check(`package.json ${pkgVersion} === APP_VERSION ${appVersion}`, !!appVersion && pkgVersion === appVersion);
}

// --- Drum blocks: the `opening` (entrada) must survive every edit ---
// This module had NO parity assertions, which is how two data-loss bugs lived here: both
// withCell and withPhraseCount rebuilt BlockTrack as a fresh object literal without
// spreading `...t`, so editing any cell (or the column count) silently deleted the
// track's opening — from the grid, from playback and from the saved block. Kotlin uses
// t.copy(...) and was unaffected.
{
  const opening: PresetTrack = { label: "Op", encoded: "x" } as unknown as PresetTrack;
  const cellA: PresetTrack = { label: "A", encoded: "y" } as unknown as PresetTrack;
  let blk = new DrumBlock("t", [{ instrument: "tamborim", cells: [null, null], opening }], 2);
  check("withCell keeps the track's opening", blk.withCell(0, 0, cellA).tracks[0].opening === opening);
  check("withCell still sets the cell", blk.withCell(0, 1, cellA).tracks[0].cells[1] === cellA);
  check("withPhraseCount keeps the opening", blk.withPhraseCount(4).tracks[0].opening === opening);
  check("withPhraseCount resizes", blk.withPhraseCount(4).tracks[0].cells.length === 4);
  // ...and the shipped built-in that exercises it round-trips through an edit.
  const builtin = BUILTIN_BLOCKS.find((b) => b.startsWith("Tamborim Block="));
  check("a built-in block with an entrada exists", !!builtin);
}

// --- Clicks must be synthesised at the ENGINE's rate, not a hard-coded 44100 ---
// Web built every click at 44100 and then declared the buffer at ctx.sampleRate, so on a
// 48 kHz device each click played ~147 cents sharp and 8.8 % short. Kotlin always threaded
// audio.sampleRate through. `sr` is now a required parameter; these pin that.
check("synthClick length follows the sample rate",
  synthClick(2000, 45, 48000).length === Math.floor(48000 * 45 / 1000) &&
  synthClick(2000, 45, 44100).length === Math.floor(44100 * 45 / 1000));
check("clickAt memoises per (freq, ms, rate)",
  clickAt(2000, 45, 48000) === clickAt(2000, 45, 48000) &&
  clickAt(2000, 45, 48000) !== clickAt(2000, 45, 44100));
check("PercussionSynth defaults to Kotlin's FALLBACK_RATE, not 44100",
  new PercussionSynth().sampleRate === 48000);

// --- Songs library: one generator writes both ports, so they must agree exactly ---
// Digest and counts are copied from the generated Kotlin SongLibrary; regenerate
// with tools/build_song_library.py after changing either JSON input.
check("song library digest matches Kotlin", SONG_LIBRARY_DIGEST === "d3e6ba15f25d8d7c");
check("song count matches Kotlin", SONGS.length === 229);
check("songs with chord data matches Kotlin", SONGS_WITH_CHORDS.length === 15);
check("every song row is usable (title, http url, sane capo)",
  SONGS.every((s) => s.title.trim().length > 0 && s.url.startsWith("http") &&
    s.site.trim().length > 0 && s.capo >= 0 && s.capo <= 12));
check("no song appears twice", (() => {
  const keys = SONGS.map((s) => (s.artist + "|" + s.title).toLowerCase());
  return new Set(keys).size === keys.length;
})());
// Compare the two keys SEPARATELY, the way the generator (Python tuple sort) and
// SongLibraryTest (compareBy) both do. Joining them with "|" is NOT equivalent:
// 0x7C sorts after space, so "Bob|x" > "Bob Dylan|y" while ("bob","x") < ("bob dylan","y").
check("songs are sorted by artist then title", (() => {
  for (let i = 1; i < SONGS.length; i++) {
    const a = SONGS[i - 1], b = SONGS[i];
    const aa = a.artist.toLowerCase(), ba = b.artist.toLowerCase();
    if (aa > ba) return false;
    if (aa === ba && a.title.toLowerCase() > b.title.toLowerCase()) return false;
  }
  return true;
})());
// Storing SOUNDING symbols only pays off if the engine can resolve them - an
// unparseable symbol would render as a dead row in the tab.
check("every chord symbol in the library parses", SONGS_WITH_CHORDS.every((s) =>
  s.sections.every((sec) => sec.chords.length > 0 && sec.chords.every((c) => parseChord(c) !== null))));
check("every song with chords names a key that parses",
  SONGS_WITH_CHORDS.every((s) => s.key !== null && parseChord(s.key) !== null));
check("a song without chords is still a valid row", (() => {
  const bare = SONGS.filter((s) => !songHasChords(s));
  return bare.length > 0 && bare.every((s) => s.sections.length === 0 && s.key === null &&
    songChordVocabulary(s).length === 0);
})());
check("chord vocabulary de-dupes in first-seen order", (() => {
  const s = SONGS_WITH_CHORDS.find((x) => x.sections.reduce((n, sec) => n + sec.chords.length, 0) > 3)!;
  const v = songChordVocabulary(s);
  return new Set(v).size === v.length && v[0] === s.sections[0].chords[0];
})());
check("search matches title and artist; blank returns everything",
  searchSongs("   ").length === SONGS.length &&
  searchSongs("beatles").length > 0 &&
  searchSongs("beatles").every((s) => s.artist.toLowerCase().includes("beatles")) &&
  searchSongs("zzzznotasong").length === 0);
check("artists are distinct and never blank",
  new Set(SONG_ARTISTS).size === SONG_ARTISTS.length && SONG_ARTISTS.every((a) => a.trim().length > 0));
// Seeds come from common musical knowledge, not from Nadav's own sheets; the tab
// marks them so nothing poses as his transcription.
check("the shipped library is seeds only", SONGS_WITH_CHORDS.every((s) => s.seeded));

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


// --- Song sheet: transposition and degrees ---
// Mirrors SongSheetTest.kt. If these two drift, the same song shows different
// degree labels on the phone and on the web.
check("a key parses from a chord symbol", (() => {
  const g = parseKey("G"), am = parseKey("Am"), fsm = parseKey("F#m");
  return g?.tonic === 7 && !g.minor && am?.tonic === 9 && am.minor &&
    fsm?.tonic === 6 && fsm.minor;
})());
check("a major-seventh key is not mistaken for minor", parseKey("Cmaj")?.minor === false);
check("nonsense is not a key", parseKey("") === null && parseKey("H") === null);
check("flat keys prefer flat spelling",
  prefersFlats(parseKey("F")) && prefersFlats(parseKey("Bb")) &&
  !prefersFlats(parseKey("G")) && !prefersFlats(null));
check("transposing keeps the quality",
  transposeSymbol("C", 2) === "D" && transposeSymbol("Cm7", 2) === "Dm7" &&
  transposeSymbol("Cmaj7", 2) === "Dmaj7");
check("transposing moves the slash bass with the chord",
  transposeSymbol("D/F#", 2) === "E/G#" && transposeSymbol("G/B", 5) === "C/E");
check("transposing can spell flat",
  transposeSymbol("A", 1, true) === "Bb" && transposeSymbol("A", 1, false) === "A#" &&
  transposeSymbol("D/F#", 1, true) === "Eb/G");
check("transposing wraps the octave and accepts negatives",
  transposeSymbol("C", 12) === "C" && transposeSymbol("C", -1) === "B" &&
  transposeSymbol("C", 11) === "B");
check("an unparseable symbol transposes to itself",
  transposeSymbol("N.C.", 3) === "N.C." && transposeSymbol("%", 3) === "%");
check("the key transposes with the chords",
  transposeKey(parseKey("G")!, 2) === "A" && transposeKey(parseKey("Am")!, 2) === "Bm" &&
  transposeKey(parseKey("Am")!, 1, true) === "Bbm");
check("diatonic chords get their Roman numerals in a major key", (() => {
  const c = parseKey("C")!;
  return degreeLabel("C", c) === "I" && degreeLabel("Dm", c) === "ii" &&
    degreeLabel("Em", c) === "iii" && degreeLabel("F", c) === "IV" &&
    degreeLabel("G", c) === "V" && degreeLabel("Am", c) === "vi";
})());
check("the quality rides along with the numeral", (() => {
  const c = parseKey("C")!;
  return degreeLabel("G7", c) === "V7" && degreeLabel("Cmaj7", c) === "Imaj7" &&
    degreeLabel("Dm7", c) === "ii7" && degreeLabel("Bm7b5", c) === "viiø7";
})());
check("chromatic chords keep their accidental", (() => {
  const c = parseKey("C")!;
  return degreeLabel("Bb", c) === "bVII" && degreeLabel("Eb", c) === "bIII" &&
    degreeLabel("D7", c) === "II7";
})());
check("an inversion names the bass degree", (() => {
  const c = parseKey("C")!;
  return degreeLabel("C/E", c) === "I/3" && degreeLabel("C/G", c) === "I/5" &&
    degreeLabel("G/B", c) === "V/7";
})());
check("a minor key labels its own diatonic set", (() => {
  const am = parseKey("Am")!;
  return degreeLabel("Am", am) === "i" && degreeLabel("Dm", am) === "iv" &&
    degreeLabel("Em", am) === "v" && degreeLabel("G", am) === "VII" &&
    degreeLabel("C", am) === "III";
})());
check("degrees are invariant under transposition", (() => {
  const prog = ["C", "Am", "F", "G7", "C/E"];
  const inC = degreeLabels(prog, parseKey("C")!);
  const inD = degreeLabels(prog.map((s) => transposeSymbol(s, 2)), parseKey("D")!);
  return JSON.stringify(inC) === JSON.stringify(inD);
})());


// --- CAGED shape table (mirrors theory/.../CagedShapeTableTest.kt + CagedScalesTest.kt) ---
// The 34 shapes are transcribed from Nadav's sheet; these pin them so a typo in
// one shape string, or a one-sided edit to either twin, fails CI.
{
  const G = 7;
  const pcsOf = (mode: CagedMode, subset: ScaleSubset): Set<number> => {
    const deg = mode === CagedMode.Major
      ? { [ScaleSubset.FullScale]: [0, 2, 4, 5, 7, 9, 11], [ScaleSubset.Pentatonic]: [0, 2, 4, 7, 9], [ScaleSubset.Triad]: [0, 4, 7] }
      : { [ScaleSubset.FullScale]: [0, 2, 3, 5, 7, 8, 10], [ScaleSubset.Pentatonic]: [0, 3, 5, 7, 10], [ScaleSubset.Triad]: [0, 3, 7] };
    return new Set(deg[subset].map((i) => (G + i) % 12));
  };
  const pcAt = (stringIndex: number, fret: number) => midiPitchClass(noteAt(standard, fp(stringIndex, fret)).midi);
  const MODES = [CagedMode.Major, CagedMode.Minor];
  const SUBSETS = [ScaleSubset.Triad, ScaleSubset.Pentatonic, ScaleSubset.FullScale];

  check("the sheet's 34 CAGED diagrams are all present", CAGED_SHAPES.size === 34);
  check("a 2nd fingering exists only for the scale of boxes 1 and 4", (() => {
    for (const box of CAGED_BOXES) for (const mode of MODES) for (const subset of SUBSETS) {
      const want = subset === ScaleSubset.FullScale && (box === CagedBox.POS1 || box === CagedBox.POS4) ? 2 : 1;
      if (patternCount(box, mode, subset) !== want) return false;
    }
    return true;
  })());
  check("every dot of every CAGED shape is in the right scale, and roots are roots", (() => {
    for (const [key, dots] of CAGED_SHAPES) {
      const parts = key.split("|");
      const mode = parts[1] as CagedMode;
      const subset = parts[2] as ScaleSubset;
      const allowed = pcsOf(mode, subset);
      for (const d of dots) {
        const pc = pcAt(d.string, 3 + d.offset);
        if (!allowed.has(pc)) return false;
        if (d.isRoot !== (pc === G)) return false;
      }
    }
    return true;
  })());
  check("every CAGED shape fits a 22-fret neck in all 12 keys, dropping no notes", (() => {
    for (let k = 0; k < 12; k++) {
      for (const box of CAGED_BOXES) for (const mode of MODES) for (const subset of SUBSETS) {
        for (let pat = 1; pat <= patternCount(box, mode, subset); pat++) {
          const [lo, hi] = boxWindow(k, box, standard, mode, subset, pat);
          if (lo < 0 || hi > 22) return false;
          const want = CAGED_SHAPES.get(`${box}|${mode}|${subset}|${pat}`)!.length;
          if (resolveBox(k, box, mode, subset, standard, 22, pat).length !== want) return false;
        }
      }
    }
    return true;
  })());
  check("the CAGED boxes ascend the neck", (() => {
    for (const mode of MODES) for (const subset of SUBSETS) {
      const los = CAGED_BOXES.map((b) => boxWindow(G, b, standard, mode, subset)[0]);
      for (let i = 1; i < los.length; i++) if (los[i] < los[i - 1]) return false;
    }
    return true;
  })());
  check("the 5 boxes tile every scale tone between frets 2 and 12", (() => {
    for (const mode of MODES) {
      const allowed = pcsOf(mode, ScaleSubset.FullScale);
      const union = new Set<string>();
      for (const box of CAGED_BOXES) for (let pat = 1; pat <= patternCount(box, mode, ScaleSubset.FullScale); pat++) {
        for (const n of resolveBox(G, box, mode, ScaleSubset.FullScale, standard, 22, pat)) union.add(fpKey(n.position));
      }
      for (let st = 0; st < 6; st++) for (let f = 2; f <= 12; f++) {
        if (allowed.has(pcAt(st, f)) && !union.has(fpKey(fp(st, f)))) return false;
      }
    }
    return true;
  })());
  // The four corrections applied to the sheet (see cagedShapeTable.ts's header).
  check("correction 1 - minor scale box 1 pattern 1 is in G, not A minor", (() => {
    const notes = resolveBox(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, standard, 22, 1);
    return notes.some((n) => n.isRoot && n.position.stringIndex === 0 && n.position.fret === 3) &&
      !notes.some((n) => pcAt(n.position.stringIndex, n.position.fret) === 11);
  })());
  check("correction 2 - minor pentatonic box 1 has D on the A string, not D#", (() => {
    const a = resolveBox(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.Pentatonic, standard)
      .filter((n) => n.position.stringIndex === 1).map((n) => n.position.fret).sort((x, y) => x - y);
    return JSON.stringify(a) === JSON.stringify([3, 5]);
  })());
  check("corrections 3 and 4 - minor box 4 is not a copy of box 3", (() => {
    for (const subset of [ScaleSubset.Pentatonic, ScaleSubset.Triad]) {
      const b3 = resolveBox(G, CagedBox.POS3, CagedMode.Minor, subset, standard).map((n) => n.position.fret);
      const b4 = resolveBox(G, CagedBox.POS4, CagedMode.Minor, subset, standard).map((n) => n.position.fret);
      if (JSON.stringify(b3) === JSON.stringify(b4)) return false;
      if (Math.min(...b4) <= Math.min(...b3)) return false;
    }
    const lowE = resolveBox(G, CagedBox.POS4, CagedMode.Minor, ScaleSubset.Triad, standard)
      .filter((n) => n.position.stringIndex === 0).map((n) => n.position.fret);
    return JSON.stringify(lowE) === JSON.stringify([10]);
  })());
  check("each triad shape contains its own pentatonic shape's chord tones", (() => {
    for (const box of CAGED_BOXES) for (const mode of MODES) {
      const triadPcs = pcsOf(mode, ScaleSubset.Triad);
      const triad = new Set(resolveBox(G, box, mode, ScaleSubset.Triad, standard).map((n) => fpKey(n.position)));
      for (const n of resolveBox(G, box, mode, ScaleSubset.Pentatonic, standard)) {
        if (triadPcs.has(pcAt(n.position.stringIndex, n.position.fret)) && !triad.has(fpKey(n.position))) return false;
      }
    }
    return true;
  })());

  // --- The guided run + the triad drill ---
  check("the guided run is one step per diagram - 34 in all, none repeated", (() => {
    const seen = new Set(PRACTICE_RUN.map((s2) => `${s2.box}|${s2.mode}|${s2.subset}|${s2.pattern}`));
    return PRACTICE_RUN.length === 34 && seen.size === 34 &&
      [...seen].every((k) => CAGED_SHAPES.has(k));
  })());
  check("the run walks the boxes low to high, alternating the leading quality", (() => {
    const order = [...new Set(PRACTICE_RUN.map((s2) => s2.box))];
    if (JSON.stringify(order) !== JSON.stringify(CAGED_BOXES)) return false;
    for (const box of CAGED_BOXES) {
      const steps = PRACTICE_RUN.filter((s2) => s2.box === box);
      const lead = (boxNumber(box) - 1) % 2 === 0 ? CagedMode.Major : CagedMode.Minor;
      if (steps[0].mode !== lead) return false;
      if (new Set(steps.map((s2) => s2.mode)).size !== 2) return false;
      // one contiguous block per quality, chord tones first inside each
      const leadSteps = steps.filter((s2) => s2.mode === lead);
      if (steps.filter((s2, i) => i < leadSteps.length && s2.mode === lead).length !== leadSteps.length) return false;
      const subs = [...new Set(leadSteps.map((s2) => s2.subset))];
      if (JSON.stringify(subs) !== JSON.stringify([ScaleSubset.Triad, ScaleSubset.FullScale, ScaleSubset.Pentatonic])) return false;
    }
    const perBox = CAGED_BOXES.map((b) => PRACTICE_RUN.filter((s2) => s2.box === b).length);
    return JSON.stringify(perBox) === JSON.stringify([8, 6, 6, 8, 6]);
  })());
  check("triad groups run top-down: strings 1-2-3, 2-3-4, 3-4-5, 4-5-6",
    JSON.stringify(TRIAD_GROUPS) === JSON.stringify([[3, 4, 5], [2, 3, 4], [1, 2, 3], [0, 1, 2]]));
  check("the triad run is all 24 - 12 major then 12 minor", (() => {
    const run = triadRun(G, standard);
    if (run.length !== 24) return false;
    if (!run.slice(0, 12).every((r) => r.quality === "maj")) return false;
    if (!run.slice(12).every((r) => r.quality === "min")) return false;
    const groups = [...new Set(run.slice(0, 12).map((r) => JSON.stringify(r.shape.strings)))];
    return JSON.stringify(groups) === JSON.stringify(TRIAD_GROUPS.map((g) => JSON.stringify(g)));
  })());
  // Pinned to Nadav's triad sheet (docs/caged-shapes-source.md — the D-major page of
  // ~/Desktop/fretboard.pdf). This is why triadInversions skips open strings and
  // incomplete (degree-doubling) close voicings, and orders by neck position.
  check("triads match Nadav's D major sheet exactly", (() => {
    const sheet: [number[], number[][]][] = [
      [[3, 4, 5], [[2, 3, 2], [7, 7, 5], [11, 10, 10]]],
      [[2, 3, 4], [[4, 2, 3], [7, 7, 7], [12, 11, 10]]],
      [[1, 2, 3], [[5, 4, 2], [9, 7, 7], [12, 12, 11]]],
      [[0, 1, 2], [[5, 5, 4], [10, 9, 7], [14, 12, 12]]],
    ];
    const got = triadInversions(parsePitchClass("D"), "maj", standard);
    if (got.length !== 12) return false;
    for (const [group, want] of sheet) {
      const mine = got.filter((t) => JSON.stringify(t.strings) === JSON.stringify(group)).map((t) => t.frets);
      if (JSON.stringify(mine) !== JSON.stringify(want)) return false;
    }
    return true;
  })());
  check("triad shapes are movable and ascend the neck (no open strings)", (() => {
    for (const key of [0, 2, 5, 7, 9]) for (const q of ["maj", "min"] as const) {
      const got = triadInversions(key, q, standard);
      if (got.length !== 12) return false;
      for (const g of TRIAD_GROUPS) {
        const inGroup = got.filter((t) => JSON.stringify(t.strings) === JSON.stringify(g));
        if (inGroup.length !== 3) return false;
        if (new Set(inGroup.map((t) => t.inversion)).size !== 3) return false;
        const lows = inGroup.map((t) => Math.min(...t.frets));
        if (JSON.stringify(lows) !== JSON.stringify([...lows].sort((x, y) => x - y))) return false;
        for (const t of inGroup) {
          if (t.frets.some((f) => f < 1)) return false;
          if (new Set(t.frets.map((f, i) => pcAt(t.strings[i], f))).size !== 3) return false;
        }
      }
    }
    return true;
  })());
  check("every triad-run voicing is a chord tone of the key", (() => {
    for (const { quality, shape } of triadRun(G, standard)) {
      const want = new Set([G, (G + (quality === "maj" ? 4 : 3)) % 12, (G + 7) % 12]);
      for (let i = 0; i < 3; i++) if (!want.has(pcAt(shape.strings[i], shape.frets[i]))) return false;
    }
    return true;
  })());
}


console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);

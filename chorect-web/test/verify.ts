// Runtime sanity checks for the ported theory + audio engines. Built as an SSR
// bundle (`vite build --ssr`) and run under Node, so we exercise the real modules
// the browser uses. Mirrors a handful of the Kotlin JUnit assertions.

import {
  parseChord, ChordShapeGenerator, cagedShapesFor, VoicingStyle, notesFrom,
  scalePositionsFor, scaleNotesFrom, SCALES, parsePitchClass, midiPitchClass,
  TrainingMode, ChordTypeLevel, resolve as resolveDegree, degreeRoot, romanLabel,
  ADVANCED_PROGRESSIONS, resolveNamed, QUALITIES, inversionMidis, randomN2c, n2cAnswerLabel,
  romanIsModeAmbiguous, MAJOR_DEGREES, MINOR_DEGREES,
  ProgFocus, randomProgression, hasOneSixStep, thirdSixthPrimaryPool, thirdSixthContrastPool,
  THIRD_SIXTH_DRILL_PROGRESSIONS, THIRD_SIXTH_CONTRAST_DRILL, THIRD_SIXTH_CONTRAST_PERCENT, Progression, CarMode,
} from "../src/theory";
import { standard } from "../src/theory/tunings";
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

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);

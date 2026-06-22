// Runtime sanity checks for the ported theory + audio engines. Built as an SSR
// bundle (`vite build --ssr`) and run under Node, so we exercise the real modules
// the browser uses. Mirrors a handful of the Kotlin JUnit assertions.

import {
  parseChord, ChordShapeGenerator, cagedShapesFor, VoicingStyle, notesFrom,
  scalePositionsFor, scaleNotesFrom, SCALES, parsePitchClass, midiPitchClass,
  TrainingMode, ChordTypeLevel, resolve as resolveDegree, degreeRoot, romanLabel,
  ADVANCED_PROGRESSIONS, resolveNamed, QUALITIES, inversionMidis, randomN2c, n2cAnswerLabel,
} from "../src/theory";
import { standard } from "../src/theory/tunings";
import {
  PercussionInstrument, PercussionPattern, SAMBA, swungSlotMs, slotMs, voiceCount,
  movementCost, pickMinMovement, BUILTIN_PATTERNS,
} from "../src/theory";
import { PluckedSynth, PitchDetector, analyzePitch, PercussionSynth } from "../src/audio";

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

// --- Percussion: SAMBA groove + pattern round-trip ---
check("SAMBA groove is non-empty", !SAMBA.isEmpty());
const roundTrip = PercussionPattern.decode(SAMBA.encode());
check("PercussionPattern encode→decode round-trips", roundTrip !== null && roundTrip.encode() === SAMBA.encode());
// cycle: null → 0 → 1 → 2 → null for Surdo (3 voices)
let p = PercussionPattern.empty();
const cy: (number | null)[] = [];
for (let i = 0; i < 4; i++) { p = p.cycled(PercussionInstrument.Surdo, 0); cy.push(p.voiceAt(PercussionInstrument.Surdo, 0)); }
check("Surdo cell cycles 0,1,2,null", cy[0] === 0 && cy[1] === 1 && cy[2] === 2 && cy[3] === null);
check("Surdo has 3 voices, Pandeiro 5", voiceCount(PercussionInstrument.Surdo) === 3 && voiceCount(PercussionInstrument.Pandeiro) === 5);

// --- Swing: straight = even slots; swung delays the off-16ths but preserves loop length ---
const straightSum = Array.from({ length: 16 }, (_, i) => swungSlotMs(i, 100, 0)).reduce((a, b) => a + b, 0);
const swungSum = Array.from({ length: 16 }, (_, i) => swungSlotMs(i, 100, 60)).reduce((a, b) => a + b, 0);
check("straight slot = base slotMs", Math.abs(swungSlotMs(0, 100, 0) - slotMs(100)) < 1.5);
check("swing preserves total loop length (±a few ms rounding)", Math.abs(straightSum - swungSum) <= 16);

// --- Percussion synth: every voice renders bounded, finite audio ---
const psynth = new PercussionSynth(44100);
let psOk = true;
for (const inst of [PercussionInstrument.Surdo, PercussionInstrument.Tamborim, PercussionInstrument.Pandeiro, PercussionInstrument.Agogo]) {
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
let builtinsOk = BUILTIN_PATTERNS.length === 3;
for (const b of BUILTIN_PATTERNS) {
  const rt = PercussionPattern.decode(b.pattern.encode());
  if (!rt || rt.encode() !== b.pattern.encode()) builtinsOk = false;
}
check("built-in grooves (stock samba + teleco-teco 1/2) are valid & round-trip", builtinsOk);

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);

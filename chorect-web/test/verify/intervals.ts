// Area "intervals" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- intervals

import { TrainingMode, INTERVAL_CHOICES, intervalTargetMidi, INTERVAL_DEGREES, intervalDegreeSemitones, intervalDegreeLabel, diatonicIntervalSemitones, diatonicIntervalAnswerSet, intervalRefFor, intervalBoxSteps, intervalStepLabel, intervalStepSemitones, intervalStepAnswerSet, intervalStepMode, intervalChoiceFor, IntervalDirection, randomOtherKey, drawAvoiding, melodyNotes, melodyContains, melodyNotesAnchored, INTERVAL_SONGS_ASCENDING, INTERVAL_SONGS_DESCENDING,
  IntervalMiss, encodeIntervalMisses, decodeIntervalMisses, appendIntervalMiss, intervalMissStats, INTERVAL_MISSES_MAX_KEPT } from "../../src/theory";
import { check } from "./harness";

export function run(): void {

  // --- Interval trainer (#6) ---
  check("13 intervals from unison to octave", INTERVAL_CHOICES.length === 13 &&
    INTERVAL_CHOICES[0].longName === "unison" && INTERVAL_CHOICES[12].longName === "octave");
  check("intervalTargetMidi ascends/descends", intervalTargetMidi(60, 7, true) === 67 && intervalTargetMidi(60, 7, false) === 53);
  // Pinned to theory EarTrainingTest (diatonic interval source).
  check("diatonic degree steps (major/minor)",
    INTERVAL_DEGREES.map((d) => intervalDegreeSemitones(TrainingMode.Major, d)).join() === "0,2,4,5,7,9,11,12" &&
    INTERVAL_DEGREES.map((d) => intervalDegreeSemitones(TrainingMode.Minor, d)).join() === "0,2,3,5,7,8,10,12");
  check("diatonic degree labels (minor)",
    INTERVAL_DEGREES.map((d) => intervalDegreeLabel(TrainingMode.Minor, d)).join() === "1,2,♭3,4,5,♭6,♭7,8");
  check("descending diatonic = same degree an octave lower",
    diatonicIntervalSemitones(TrainingMode.Major, 3, true) === 4 && diatonicIntervalSemitones(TrainingMode.Major, 3, false) === 8 &&
    diatonicIntervalSemitones(TrainingMode.Major, 5, false) === 5 && diatonicIntervalSemitones(TrainingMode.Major, 1, false) === 0 &&
    diatonicIntervalSemitones(TrainingMode.Major, 8, false) === 12);
  check("unison/octave names", intervalChoiceFor(0).shortName === "Unison" && intervalChoiceFor(12).shortName === "Octave");
  {
    let prev: number | null = null, ok = true;
    for (let i = 0; i < 300; i++) {
      const st = drawAvoiding(prev, (x: number) => x, () => Math.floor(Math.random() * 3));
      if (st === prev) ok = false;
      prev = st;
    }
    ok = ok && drawAvoiding(4, (x: number) => x, () => 4) === 4;
    const keys = new Set<number>();
    for (let i = 0; i < 300; i++) { const k = randomOtherKey(0); if (k === 0) ok = false; keys.add(k); }
    check("no repeated interval; random key always changes", ok && keys.size === 11);
  }
  check("interval step boxes: major/minor/both + tritone",
    intervalBoxSteps(TrainingMode.Major, false).join() === "0,2,4,5,6,7,9,11,12" &&
    intervalBoxSteps(TrainingMode.Minor, false).join() === "0,2,3,5,6,7,8,10,12" &&
    intervalBoxSteps(TrainingMode.Major, true).join() === "0,2,3,4,5,6,7,8,9,10,11,12" &&
    intervalBoxSteps(TrainingMode.Major, true).map(intervalStepLabel).join(" ") === "1 2 ♭3 3 4 ♭5 5 ♭6 6 ♭7 7 8" &&
    intervalStepSemitones(4, false) === 8 && intervalStepSemitones(6, false) === 6 && intervalStepSemitones(12, false) === 12 &&
    intervalStepAnswerSet(new Set([4, 6]), IntervalDirection.Mixed).join() === "4,6,8" &&
    intervalStepMode(3) === TrainingMode.Minor && intervalStepMode(11) === TrainingMode.Major && intervalStepMode(6) === null);
  check("diatonic answer set",
    diatonicIntervalAnswerSet(TrainingMode.Major, new Set([3, 5]), IntervalDirection.Mixed).join() === "4,5,7,8" &&
    diatonicIntervalAnswerSet(TrainingMode.Major, new Set(INTERVAL_DEGREES), IntervalDirection.Ascending).join() === "0,2,4,5,7,9,11,12");
  {
    let ok = true;
    for (let st = 1; st <= 12; st++) for (const up of [true, false]) if (intervalRefFor(st, up)?.semitones !== st) ok = false;
    for (const m of [TrainingMode.Major, TrainingMode.Minor]) for (const d of INTERVAL_DEGREES) for (const up of [true, false])
      if (!intervalChoiceFor(diatonicIntervalSemitones(m, d, up))) ok = false;
    check("interval song lookup", ok);
    const allRefs = [...INTERVAL_SONGS_ASCENDING, ...INTERVAL_SONGS_DESCENDING];
    // Pinned to Kotlin EarTrainingTest: same song count, every snippet sounds its interval.
    check("interval song count = 64", allRefs.reduce((n, r) => n + r.songs.length, 0) === 64);
    let melOk = true;
    for (const r of allRefs) for (const sg of r.songs) {
      const notes = melodyNotes(sg.melody);
      if (notes.length < 6 || notes.length > 12 || !notes.every((n) => n.midi >= 54 && n.midi <= 80)) melOk = false;
      if (!melodyContains(sg.melody, r.semitones, r.ascending)) melOk = false;
    }
    check("every song snippet contains its interval", melOk);
    check("melody parse centres + keeps lengths",
      melodyNotes("0/2 5/1.5 5/0.5", 66).map((n) => n.midi).join() === "64,69,69" &&
      melodyNotes("0/2 5/1.5 5/0.5", 66).map((n) => n.eighths).join() === "2,1.5,0.5");
    check("anchored melody puts the leap on the question's tonic (= Kotlin)",
      melodyNotesAnchored("0/2 5/1.5 5/0.5", 5, true, 62).map((n) => n.midi).join() === "62,67,67" &&
      melodyNotesAnchored("0/4 12/4 11/2", 11, true, 60).map((n) => n.midi).join() === "60,72,71" &&
      melodyNotesAnchored("0/4 12/4 11/2 7/1", 1, false, 60).map((n) => n.midi).join() === "48,60,59,55");
  }

  // --- Interval misses (pinned to Kotlin EarTrainingTest) ---
  {
    const ms: IntervalMiss[] = [
      { asked: 4, ascending: false, guess: 5, tonicMidi: 62, dateMillis: 1000 }, { asked: 4, ascending: false, guess: 5, tonicMidi: 57, dateMillis: 2000 },
      { asked: 4, ascending: false, guess: 3, tonicMidi: 60, dateMillis: 3000 }, { asked: 7, ascending: true, guess: 5, tonicMidi: 64, dateMillis: 4000 },
      { asked: 3, ascending: false, guess: 4, tonicMidi: 61, dateMillis: 5000 }, { asked: 3, ascending: true, guess: 4, tonicMidi: 61, dateMillis: 6000 },
    ];
    const raw = encodeIntervalMisses(ms);
    check("interval misses encode (= Kotlin)", raw === "4,0,5,62,1000;4,0,5,57,2000;4,0,3,60,3000;7,1,5,64,4000;3,0,4,61,5000;3,1,4,61,6000");
    check("interval misses round-trip", JSON.stringify(decodeIntervalMisses(raw)) === JSON.stringify(ms));
    check("interval misses skip junk rows",
      JSON.stringify(decodeIntervalMisses("x;13,1,2,60,1;2,1,1,60,7;2,2,1,60,1;")) ===
      JSON.stringify([{ asked: 2, ascending: true, guess: 1, tonicMidi: 60, dateMillis: 7 }]));
    check("interval miss stats (= Kotlin)", intervalMissStats(ms).map((r) => `${r.asked}${r.ascending ? "u" : "d"}${r.count}/${r.topGuess}x${r.topGuessCount}`).join() ===
      "4d3/5x2,3u1/4x1,3d1/4x1,7u1/5x1");
    let list: IntervalMiss[] = [];
    for (let i = 0; i < INTERVAL_MISSES_MAX_KEPT + 3; i++) list = appendIntervalMiss(list, { asked: 1, ascending: true, guess: 2, tonicMidi: 60, dateMillis: i });
    check("interval misses keep only the newest", list.length === INTERVAL_MISSES_MAX_KEPT && list[0].dateMillis === 3);
  }
}

// Area "ear" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- ear

import { parseChord, parsePitchClass, TrainingMode, ChordTypeLevel, resolve as resolveDegree, degreeRoot, romanLabel, ADVANCED_PROGRESSIONS, resolveNamed, romanIsModeAmbiguous, MAJOR_DEGREES, MINOR_DEGREES, ProgFocus, randomProgression, hasOneSixStep, thirdSixthPrimaryPool, thirdSixthContrastPool, THIRD_SIXTH_DRILL_PROGRESSIONS, THIRD_SIXTH_CONTRAST_DRILL, THIRD_SIXTH_CONTRAST_PERCENT, Progression, CarMode, progressionLacksTonic, progressionRelativeTonicMode, progressionRelativeTonicBar, progressionHomeIsObvious, relativeRomanLineFor, openingRelativeMode, openingRelativeLabel, circleRun, circleRunBars, MissedTake, encodeMissedTake, decodeMissedTake } from "../../src/theory";
import { check } from "./harness";

export function run(): void {

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

  // --- Missed TAKES: the rendition saved when a progression is missed (mirrors the three
  // Kotlin tests `MissedTake round-trips…` / `…refuses to encode…` / `…decode rejects junk`) ---
  {
    // A minor i–bVII–bVI–v whose last two bars sit an OCTAVE below the bVII — exactly the
    // case a regenerated drill voicing would silently "fix".
    const take: MissedTake = {
      keyPc: 9,
      bars: [
        { symbol: "Am", midis: [57, 60, 64] },
        { symbol: "G", midis: [67, 71, 74] },
        { symbol: "F", midis: [53, 57, 60] },
        { symbol: "Em", midis: [52, 55, 59] },
      ],
    };
    const enc = encodeMissedTake(take);
    check("MissedTake encodes flat as <key>|<sym>:<midis>",
      enc === "9|Am:57.60.64|G:67.71.74|F:53.57.60|Em:52.55.59");
    const back = enc ? decodeMissedTake(enc) : null;
    check("MissedTake round-trips the key and every bar's pitches (octaves included)",
      back !== null && back.keyPc === 9 && back.bars.length === 4 &&
      back.bars[1].midis[0] === 67 && back.bars[2].midis[0] === 53 &&
      back.bars.every((b, i) => b.symbol === take.bars[i].symbol &&
        b.midis.join(".") === take.bars[i].midis.join(".")));
    const bars = [{ symbol: "Am", midis: [57, 60, 64] }];
    check("MissedTake refuses to encode anything a flat store can't read back",
      encodeMissedTake({ keyPc: 9, bars: [{ symbol: "A=m", midis: [57] }] }) === null &&
      encodeMissedTake({ keyPc: 9, bars: [{ symbol: "A;m", midis: [57] }] }) === null &&
      encodeMissedTake({ keyPc: 9, bars: [{ symbol: "A|m", midis: [57] }] }) === null &&
      encodeMissedTake({ keyPc: 9, bars: [{ symbol: "Am", midis: [] }] }) === null &&
      encodeMissedTake({ keyPc: 12, bars }) === null &&
      encodeMissedTake({ keyPc: 9, bars: [] }) === null &&
      encodeMissedTake({ keyPc: 9, bars: [{ symbol: "Am", midis: [200] }] }) === null);
    check("MissedTake decode rejects junk instead of half-reading it",
      decodeMissedTake("") === null && decodeMissedTake("9") === null &&
      decodeMissedTake("9|Am") === null && decodeMissedTake("9|:57.60") === null &&
      decodeMissedTake("13|Am:57") === null && decodeMissedTake("x|Am:57") === null);
  }

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
    progressionRelativeTonicBar(royalRoad) === 4 &&
    !progressionHomeIsObvious(royalRoad) &&
    relativeRomanLineFor(royalRoad) === "bVI  –  bVII  –  v  –  i");
  const hanging: Progression = { mode: TrainingMode.Major, degrees: [6, 5, 4, 5] };
  check("vi-V-IV-V OPENS on the relative tonic - so nothing is flagged",
    progressionLacksTonic(hanging) &&
    progressionRelativeTonicMode(hanging) === TrainingMode.Minor &&
    progressionRelativeTonicBar(hanging) === 1 &&
    progressionHomeIsObvious(hanging) &&
    relativeRomanLineFor(hanging) === "i  –  bVII  –  bVI  –  bVII");
  check("a progression with its own I is never flagged",
    progressionHomeIsObvious({ mode: TrainingMode.Major, degrees: [1, 5, 6, 4] }));
  const noTonic: Progression = { mode: TrainingMode.Major, degrees: [2, 5, 4, 5] };
  check("only a progression holding neither tonic is truly tonic-less",
    progressionLacksTonic(noTonic) && progressionRelativeTonicMode(noTonic) === null &&
    progressionRelativeTonicBar(noTonic) === 0 &&
    !progressionHomeIsObvious(noTonic) && relativeRomanLineFor(noTonic) === "");
  check("a progression with its own tonic has no relative reading",
    progressionRelativeTonicMode({ mode: TrainingMode.Major, degrees: [1, 5, 6, 4] }) === null);
  check("a minor progression ending on bIII reads in the relative major",
    progressionRelativeTonicMode({ mode: TrainingMode.Minor, degrees: [4, 5, 6, 3] }) === TrainingMode.Major &&
    relativeRomanLineFor({ mode: TrainingMode.Minor, degrees: [4, 5, 6, 3] }) === "ii  –  iii  –  IV  –  I");

  // --- Answers named from the relative tonic when bar 1 IS that tonic (mirrors EarTrainingTest) ---
  const viOpener: Progression = { mode: TrainingMode.Major, degrees: [6, 4, 1, 5] };
  check("a major progression opening on vi7 is answered in minor numerals",
    openingRelativeMode(viOpener) === TrainingMode.Minor &&
    openingRelativeLabel(viOpener, 0, "vi7") === "i7" &&
    openingRelativeLabel(viOpener, 1, "IVmaj7") === "bVImaj7" &&
    openingRelativeLabel(viOpener, 2, "Imaj7") === "bIIImaj7" &&
    openingRelativeLabel(viOpener, 3, "V7") === "bVII7" &&
    openingRelativeLabel(viOpener, 2, "I") === "bIII");
  const bIIIOpener: Progression = { mode: TrainingMode.Minor, degrees: [3, 2, 5, 1], dominantBars: [2] };
  check("a minor progression opening on bIII is answered in major numerals",
    openingRelativeMode(bIIIOpener) === TrainingMode.Major &&
    openingRelativeLabel(bIIIOpener, 0, "bIIImaj7") === "Imaj7" &&
    openingRelativeLabel(bIIIOpener, 1, "ii°7") === "vii°7" &&
    openingRelativeLabel(bIIIOpener, 2, "V7") === "III7" &&
    openingRelativeLabel(bIIIOpener, 3, "i7") === "vi7");
  check("a progression opening anywhere else keeps its own numerals",
    openingRelativeMode({ mode: TrainingMode.Major, degrees: [1, 6, 4, 5] }) === null &&
    openingRelativeLabel({ mode: TrainingMode.Major, degrees: [1, 6, 4, 5] }, 1, "vi7") === "vi7");

  // --- Circle-of-fifths runs ---
  check("ii-V-I is a falling-fifths run of three", (() => {
    const r = circleRun([9, 2, 7, 0]);
    const iiVI = circleRun([0, 2, 7, 0]);
    return !!r && r.fallingFifths && r.length === 4 && circleRunBars(r, 4).join() === "0,1,2,3" &&
      !!iiVI && circleRunBars(iiVI, 4).join() === "1,2,3";
  })());
  check("circle runs wrap across the loop and read both directions", (() => {
    const wrap = circleRun([0, 5, 4, 7]);
    const up = circleRun([0, 7, 2, 9]);
    return !!wrap && circleRunBars(wrap, 4).join() === "3,0,1" && !!up && !up.fallingFifths && up.length === 4;
  })());
  check("no circle run in I-vi-IV-V or with only two linked chords",
    circleRun([0, 9, 5, 7]) === null && circleRun([0, 5, 0, 5]) === null &&
    circleRunBars(circleRun([5, 11, 4, 9])!, 4).join() === "1,2,3");
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
}

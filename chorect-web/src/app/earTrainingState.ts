// State + scheduler for Ear Training, ported from app/.../EarTrainingState.kt.
//
// Kotlin coroutines + delay() become async functions guarded by per-job tokens: a
// running sequence bails as soon as its token is superseded (a new job started) or
// stopped, which replaces structured-concurrency cancellation.

import {
  Tuning, PitchClass, ChordShape, ChordShapeGenerator, VoicingStyle, CagedShape,
  parseChord, QUALITIES, spellPc,
  TrainingMode, ChordTypeLevel, Progression, ResolvedChord, NamedProgression,
  EarTrainingDegrees, degreeRoot, resolve as resolveDegree, resolveProgression,
  randomProgression, romanLabel, randomAdvanced, resolveNamed,
  majorRelativeDegree, degreeFromMajorRelative,
  N2cChallenge, randomN2c, n2cAnswerLabel, n2cChordSymbol, n2cTestNote, n2cLabel,
  N2C_MAJOR_TEST_OFFSETS, N2C_MINOR_TEST_OFFSETS,
  inversionCount, inversionMidis,
  pickMinMovement, defaultRng,
  IntervalDirection, INTERVAL_CHOICES, intervalTargetMidi,
} from "../theory";
import { WebAudioEngine, Timbres } from "../audio";

const DISPLAY_FRETS = 14;
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export enum EarSubMode { Progression = "Progression", Note2Chord = "Note2Chord", Flavor = "Flavor", Inversions = "Inversions", AugDim = "AugDim", Intervals = "Intervals" }
export enum EarMode { Practice = "Practice", Challenge = "Challenge" }

export interface EarDeps {
  audio: WebAudioEngine;
  tuningProvider: () => Tuning;
  sustainProvider: () => number;
  strumProvider: () => number;
  onChange: () => void;
  onProgressionChallengeComplete: (score: number, total: number, durationMs: number) => void;
}

/** One challenge question: the generated progression + the user's saved guesses. */
interface QState {
  key: PitchClass;
  mode: TrainingMode;
  prog: Progression;
  resolved: ResolvedChord[];
  guessDeg: (number | null)[];
  guessExt: (string | null)[];
  guessLabel: (string | null)[];
}

export class EarTrainingState {
  private rng = defaultRng;

  // voicing / variety
  earShellVoicing = false;
  earMixAll = false;

  progSubMode = EarSubMode.Progression;
  earMode = EarMode.Practice;

  includeMajor = true;
  includeMinor = false;
  chordTypeLevel = ChordTypeLevel.Sevenths;
  fixedKey: PitchClass | null = null;
  progBpm = 140;

  progKey: PitchClass = 0;
  progMode = TrainingMode.Major;
  progProgression: Progression | null = null;
  progResolved: ResolvedChord[] = [];
  progBarRevealed = new Set<number>();
  keyRevealed = false;
  modeRevealed = false;
  isLooping = false;
  currentBar = 0;
  progressionCount = 0;
  hasGenerated = false;

  showFretboard = false;
  currentPlayingShape: ChordShape | null = null;
  lastShownShape: ChordShape | null = null;

  private prevPlayedShape: ChordShape | null = null;
  private loopToken = 0;
  private cadenceToken = 0;

  constructor(private deps: EarDeps) {
    // Pre-seed a Note2Chord challenge so the UI never has to mutate during render.
    this.n2cChallenge = randomN2c(this.rng);
  }

  private notify() { this.deps.onChange(); }

  private earStyle(): VoicingStyle {
    if (this.earMixAll) return this.rng.bool() ? VoicingStyle.Shell : VoicingStyle.Standard;
    return this.earShellVoicing ? VoicingStyle.Shell : VoicingStyle.Standard;
  }

  private gen(style: VoicingStyle): ChordShapeGenerator {
    return new ChordShapeGenerator(4, true, 3, style);
  }

  switchTab(sub: EarSubMode) {
    this.progSubMode = sub;
    this.earMode = EarMode.Practice;
    this.stopLoop();
    this.notify();
  }

  setEarMode(m: EarMode) { this.earMode = m; this.notify(); }

  // ---------- Progression trainer ----------

  nextProgression() {
    const candidates: TrainingMode[] = [];
    if (this.includeMajor) candidates.push(TrainingMode.Major);
    if (this.includeMinor) candidates.push(TrainingMode.Minor);
    if (candidates.length === 0) candidates.push(TrainingMode.Major);
    const mode = candidates[this.rng.int(candidates.length)];
    const key = this.fixedKey ?? this.rng.int(12);
    const prog = randomProgression(mode, this.rng);
    this.progKey = key;
    this.progMode = mode;
    this.progProgression = prog;
    this.progResolved = this.resolveCurrent(prog, key);
    this.progBarRevealed = new Set();
    this.keyRevealed = false;
    this.modeRevealed = false;
    this.currentBar = 0;
    this.hasGenerated = true;
    if (this.progSubMode === EarSubMode.Progression) this.progressionCount++;
    if (this.isLooping) { this.stopLoop(); this.startLoop(); }
    this.notify();
  }

  private resolveCurrent(prog: Progression, key: PitchClass): ResolvedChord[] {
    if (this.earMixAll) {
      const levels = [ChordTypeLevel.Triads, ChordTypeLevel.Sevenths, ChordTypeLevel.Extended];
      return prog.degrees.map((deg) => resolveDegree(deg, key, prog.mode, levels[this.rng.int(levels.length)], this.rng));
    }
    return resolveProgression(prog, key, this.chordTypeLevel, this.rng);
  }

  reresolveCurrent() {
    if (!this.progProgression) return;
    this.progResolved = this.resolveCurrent(this.progProgression, this.progKey);
    this.notify();
  }

  /** Transpose the current Progressions-practice progression by n semitones:
   *  shift the key and every chord's root, keeping the same chords/qualities and
   *  Roman degrees (no re-randomization). Works for diatonic and advanced alike. */
  transposeProgression(n: number) {
    if (this.progResolved.length === 0) return;
    const pc = (v: number) => (((v + n) % 12) + 12) % 12;
    this.progKey = pc(this.progKey);
    this.progResolved = this.progResolved.map((rc) => {
      const parsed = parseChord(rc.symbol);
      if (!parsed) return rc;
      const [root, q] = parsed;
      const newRoot = pc(root);
      return { symbol: spellPc(newRoot) + q.symbol, romanLabel: rc.romanLabel, root: newRoot };
    });
    this.prevPlayedShape = null;
    if (this.isLooping) { this.stopLoop(); this.startLoop(); }
    this.notify();
  }

  playBarOnce(idx: number) {
    const resolved = this.progResolved[idx];
    if (!resolved) return;
    const parsed = parseChord(resolved.symbol);
    if (!parsed) return;
    const [root, q] = parsed;
    const tuning = this.deps.tuningProvider();
    const shapes = this.gen(this.earStyle()).shapesFor(root, q, tuning, DISPLAY_FRETS);
    if (shapes.length === 0) return;
    const shape = this.prevPlayedShape == null
      ? (shapes.find((s) => s.cagedShape === CagedShape.E) ?? shapes[0])
      : shapes[pickMinMovement(this.prevPlayedShape, shapes)];
    this.prevPlayedShape = shape;
    this.lastShownShape = shape;
    this.currentBar = idx;
    const midis = shape.notes.filter((n) => n !== null).map((n) => n!.midi);
    if (midis.length === 0) return;
    this.deps.audio.playChord(midis, this.deps.strumProvider(), this.deps.sustainProvider(), Timbres.Clarity);
    this.notify();
  }

  progCadenceLabel(): string { return this.progMode === TrainingMode.Major ? "I–V–I" : "i–V–i"; }

  playProgKeyCadence() {
    this.cadenceToken++;
    const token = this.cadenceToken;
    const map = EarTrainingDegrees(this.progMode);
    void (async () => {
      for (const deg of [1, 5, 1]) {
        if (token !== this.cadenceToken) return;
        const root = degreeRoot(this.progKey, deg, this.progMode);
        this.playSymbolOnce(spellPc(root) + (map.get(deg)?.triadQuality ?? ""), 600);
        await sleep(650);
      }
    })();
  }

  toggleBarReveal(idx: number) {
    if (this.progBarRevealed.has(idx)) this.progBarRevealed.delete(idx);
    else this.progBarRevealed.add(idx);
    this.notify();
  }
  toggleKeyModeReveal() { const v = !this.keyRevealed; this.keyRevealed = v; this.modeRevealed = v; this.notify(); }

  startLoop() {
    if (this.isLooping) return;
    if (this.progResolved.length === 0) this.nextProgression();
    this.prevPlayedShape = null;
    this.isLooping = true;
    this.loopToken++;
    const token = this.loopToken;
    this.notify();
    void (async () => {
      const beatMs = 60000 / Math.max(this.progBpm, 20);
      const barMs = beatMs * 4;
      while (this.isLooping && token === this.loopToken) {
        for (let i = 0; i < this.progResolved.length; i++) {
          if (!this.isLooping || token !== this.loopToken) break;
          this.currentBar = i;
          this.notify();
          this.playChordOnce(this.progResolved[i].symbol, barMs);
          await sleep(barMs);
        }
      }
    })();
  }

  stopLoop() {
    this.isLooping = false;
    this.loopToken++;
    this.currentPlayingShape = null;
    this.deps.audio.stop();
    this.notify();
  }

  private playChordOnce(symbol: string, barMs: number) {
    const parsed = parseChord(symbol);
    if (!parsed) return;
    const [root, q] = parsed;
    const tuning = this.deps.tuningProvider();
    const shapes = this.gen(this.earStyle()).shapesFor(root, q, tuning, DISPLAY_FRETS);
    const sustain = Math.max(Math.floor(barMs * 0.9), 200);
    if (shapes.length === 0) {
      this.currentPlayingShape = null;
      const rootMidi = 52 + root;
      const midis = q.intervals.map((iv) => rootMidi + iv);
      this.deps.audio.playChord(midis, this.deps.strumProvider(), sustain, Timbres.Clarity);
      return;
    }
    const shape = this.prevPlayedShape == null
      ? (shapes.find((s) => s.cagedShape === CagedShape.E) ?? shapes[0])
      : shapes[pickMinMovement(this.prevPlayedShape, shapes)];
    this.prevPlayedShape = shape;
    this.currentPlayingShape = shape;
    this.lastShownShape = shape;
    const midis = shape.notes.filter((n) => n !== null).map((n) => n!.midi);
    if (midis.length === 0) return;
    this.deps.audio.playChord(midis, this.deps.strumProvider(), sustain, Timbres.Clarity);
  }

  playProgChordDirect(idx: number) {
    const rc = this.progResolved[idx];
    if (!rc) return;
    const parsed = parseChord(rc.symbol);
    if (!parsed) return;
    const [root, q] = parsed;
    const rootMidi = 52 + root;
    const midis = q.intervals.map((iv) => rootMidi + iv);
    this.deps.audio.playChord(midis, this.deps.strumProvider(), this.deps.sustainProvider(), Timbres.Clarity);
  }

  // ---------- Note2Chord ----------

  n2cChallenge: N2cChallenge | null = null;
  n2cRevealed = false;
  n2cPlaying = false;
  n2cShowFretboard = false;
  private n2cToken = 0;
  private n2cHistory: N2cChallenge[] = [];
  private n2cHistIndex = -1;
  get n2cHasPrev(): boolean { return this.n2cHistIndex > 0; }
  get n2cHasNext(): boolean { return this.n2cHistIndex >= 0 && this.n2cHistIndex < this.n2cHistory.length - 1; }

  nextN2cChallenge() {
    const c = randomN2c(this.rng);
    this.n2cChallenge = c; this.n2cRevealed = false;
    if (this.n2cHistIndex < this.n2cHistory.length - 1) this.n2cHistory.length = this.n2cHistIndex + 1;
    this.n2cHistory.push(c);
    if (this.n2cHistory.length > 32) this.n2cHistory.shift();
    this.n2cHistIndex = this.n2cHistory.length - 1;
    this.notify();
  }
  n2cPrev() {
    if (!this.n2cHasPrev) return;
    this.n2cHistIndex--; this.n2cChallenge = this.n2cHistory[this.n2cHistIndex]; this.n2cRevealed = false; this.notify();
  }
  n2cNext() {
    if (!this.n2cHasNext) return;
    this.n2cHistIndex++; this.n2cChallenge = this.n2cHistory[this.n2cHistIndex]; this.n2cRevealed = false; this.notify();
  }
  setN2cShowFretboard(v: boolean) { this.n2cShowFretboard = v; this.notify(); }
  toggleN2cReveal() { this.n2cRevealed = !this.n2cRevealed; this.notify(); }

  playN2c() {
    if (!this.n2cChallenge) this.nextN2cChallenge();
    const c = this.n2cChallenge!;
    if (this.n2cPlaying) return;
    this.n2cToken++;
    const token = this.n2cToken;
    this.n2cPlaying = true;
    this.notify();
    void (async () => {
      try {
        const midis = this.n2cShapeMidis();
        const sustain = this.deps.sustainProvider();
        if (midis.length) this.deps.audio.playChord(midis, 0, sustain, Timbres.Clarity);
        await sleep(800);
        if (token !== this.n2cToken) return;
        const testMidi = this.nearestMidiAboveChord(n2cTestNote(c), midis.length ? midis : [60]);
        this.deps.audio.playNote(testMidi, sustain);
      } finally {
        this.n2cPlaying = false;
        this.notify();
      }
    })();
  }

  private n2cShapeMidis(): number[] {
    const c = this.n2cChallenge;
    if (!c) return [];
    const parsed = parseChord(n2cChordSymbol(c));
    if (!parsed) return [];
    const [root, q] = parsed;
    const shapes = this.gen(VoicingStyle.Standard).shapesFor(root, q, this.deps.tuningProvider(), DISPLAY_FRETS);
    const shape = shapes.find((s) => s.cagedShape === CagedShape.E) ?? shapes[0];
    if (!shape) return [];
    return shape.notes.filter((n) => n !== null).map((n) => n!.midi);
  }

  playN2cChord() {
    const midis = this.n2cShapeMidis();
    if (midis.length) this.deps.audio.playChord(midis, 0, this.deps.sustainProvider(), Timbres.Clarity);
  }
  playN2cNote() {
    const c = this.n2cChallenge;
    if (!c) return;
    const midis = this.n2cShapeMidis();
    this.deps.audio.playNote(this.nearestMidiAboveChord(n2cTestNote(c), midis.length ? midis : [60]), this.deps.sustainProvider());
  }

  private nearestMidiAboveChord(testPc: PitchClass, chordMidis: number[]): number {
    const target = (chordMidis.length ? Math.max(...chordMidis) : 60) + 4;
    for (let delta = 0; delta <= 12; delta++) {
      for (const sign of [1, -1]) {
        const cand = target + sign * delta;
        if (cand >= 0 && cand <= 127 && (((cand % 12) + 12) % 12) === testPc) return cand;
      }
    }
    return 60 + testPc;
  }

  // ---------- Note2Chord Challenge ----------

  n2cChallengeTotal = 10;
  n2cChActive = false;
  n2cChIndex = 0;
  n2cChScore = 0;
  n2cChGuess: string | null = null;

  n2cAnswerOptions(): string[] {
    const set = new Set<number>([...N2C_MAJOR_TEST_OFFSETS, ...N2C_MINOR_TEST_OFFSETS]);
    return [...set].sort((a, b) => a - b).map((o) => n2cLabel(o));
  }
  startN2cChallenge() { this.n2cChActive = true; this.n2cChIndex = 0; this.n2cChScore = 0; this.n2cChGuess = null; this.nextN2cChallenge(); this.playN2c(); }
  guessN2c(label: string) {
    if (!this.n2cChActive || this.n2cChGuess !== null) return;
    this.n2cChGuess = label;
    if (this.n2cChallenge && label === n2cAnswerLabel(this.n2cChallenge)) this.n2cChScore++;
    this.notify();
  }
  advanceN2cChallenge() {
    if (!this.n2cChActive) return;
    if (this.n2cChIndex >= this.n2cChallengeTotal - 1) { this.n2cChIndex = this.n2cChallengeTotal; this.notify(); return; }
    this.n2cChIndex++; this.n2cChGuess = null; this.nextN2cChallenge(); this.playN2c();
  }
  exitN2cChallenge() { this.n2cChActive = false; this.n2cChIndex = 0; this.n2cChGuess = null; this.notify(); }

  // ---------- Progression Challenge ----------

  challengeTotal = 15;
  challengeAnswers: (boolean | null)[] = [];
  challengeBarsCorrect: number[] = [];
  challengeIndex = 0;
  challengeActive = false;
  challengeRevealed = false;
  private challengeStartMs = 0;
  challengeDurationMs = 0;

  challengeGuessDegree: (number | null)[] = [null, null, null, null];
  challengeGuessExt: (string | null)[] = [null, null, null, null];
  /** Per-bar display label of the user's keyboard answer (e.g. "V7", "iv");
   *  null = the bar's square is empty. */
  challengeGuessLabel: (string | null)[] = [null, null, null, null];

  /** Answer-keyboard "shift": false shows the MAJOR Roman row, true the MINOR row.
   *  Both label the same seven shared diatonic chords. */
  keyboardMinor = false;

  startChallenge() {
    this.challengeAnswers = Array(this.challengeTotal).fill(null);
    this.challengeBarsCorrect = Array(this.challengeTotal).fill(0);
    this.challengeIndex = 0;
    this.challengeRevealed = false;
    this.challengeActive = true;
    this.challengeStartMs = Date.now();
    this.challengeDurationMs = 0;
    // Fresh question history; generate the first question honoring current settings.
    this.challengeLog = [];
    const q = this.freshChallengeQuestion();
    this.challengeLog.push(q);
    this.applyChallengeQuestion(q);
  }

  private finalizeCurrentQuestion() {
    if (!this.challengeActive || this.challengeIndex >= this.challengeTotal) return;
    const degrees = this.progProgression?.degrees;
    if (!degrees) return;
    let correctCount = 0;
    degrees.forEach((_, i) => {
      if (this.challengeBarCorrect(i) === true || this.challengeGuessDegree[i] == null) correctCount++;
    });
    if (this.challengeIndex < this.challengeBarsCorrect.length) this.challengeBarsCorrect[this.challengeIndex] = correctCount;
    this.challengeAnswers[this.challengeIndex] = correctCount === degrees.length;
  }

  advanceChallenge() {
    if (!this.challengeActive) return;
    this.saveChallengeGuesses();
    this.finalizeCurrentQuestion();
    if (this.challengeIndex >= this.challengeTotal - 1) {
      this.challengeIndex = this.challengeTotal;
      this.challengeDurationMs = Date.now() - this.challengeStartMs;
      this.stopLoop();
      this.deps.onProgressionChallengeComplete(this.challengeBarScore(), this.challengeBarTotal(), this.challengeDurationMs);
      this.notify();
      return;
    }
    const next = this.challengeIndex + 1;
    if (next < this.challengeLog.length) {
      // Revisiting a question we've already seen — restore it (and its answers).
      this.challengeIndex = next;
      this.applyChallengeQuestion(this.challengeLog[next]);
    } else {
      const q = this.freshChallengeQuestion();
      this.challengeLog.push(q);
      this.challengeIndex = next;
      this.applyChallengeQuestion(q);
    }
  }

  exitChallenge() { this.challengeActive = false; this.challengeRevealed = false; this.challengeIndex = 0; this.stopLoop(); this.notify(); }

  challengeScore(): number { return this.challengeAnswers.filter((a) => a === true).length; }
  challengeBarScore(): number { return this.challengeBarsCorrect.reduce((a, b) => a + b, 0); }
  challengeBarTotal(): number { return this.challengeTotal * 4; }

  private resetChallengeGuesses() {
    this.challengeGuessDegree = [null, null, null, null];
    this.challengeGuessExt = [null, null, null, null];
    this.challengeGuessLabel = [null, null, null, null];
  }

  // ---- question history so the user can step back and forward ----

  private challengeLog: QState[] = [];

  private freshChallengeQuestion(): QState {
    const candidates: TrainingMode[] = [];
    if (this.includeMajor) candidates.push(TrainingMode.Major);
    if (this.includeMinor) candidates.push(TrainingMode.Minor);
    if (candidates.length === 0) candidates.push(TrainingMode.Major);
    const mode = candidates[this.rng.int(candidates.length)];
    const key = this.fixedKey ?? this.rng.int(12);
    const prog = randomProgression(mode, this.rng);
    return {
      key, mode, prog, resolved: this.resolveCurrent(prog, key),
      guessDeg: [null, null, null, null],
      guessExt: [null, null, null, null],
      guessLabel: [null, null, null, null],
    };
  }

  /** Make [q] the live question (prog* + guesses), resetting reveals. */
  private applyChallengeQuestion(q: QState) {
    this.progKey = q.key;
    this.progMode = q.mode;
    this.progProgression = q.prog;
    this.progResolved = q.resolved;
    this.challengeGuessDegree = q.guessDeg;
    this.challengeGuessExt = q.guessExt;
    this.challengeGuessLabel = q.guessLabel;
    this.progBarRevealed = new Set();
    this.keyRevealed = false;
    this.modeRevealed = false;
    this.currentBar = 0;
    this.challengeRevealed = false;
    if (this.isLooping) { this.stopLoop(); this.startLoop(); }
    this.notify();
  }

  /** Persist the live guesses back into the log for the current index. */
  private saveChallengeGuesses() {
    const q = this.challengeLog[this.challengeIndex];
    if (!q) return;
    q.guessDeg = this.challengeGuessDegree;
    q.guessExt = this.challengeGuessExt;
    q.guessLabel = this.challengeGuessLabel;
  }

  /** True when stepping back to an earlier question is possible. */
  get canGoPrevChallenge(): boolean {
    return this.challengeActive && this.challengeIndex >= 1 && this.challengeIndex < this.challengeTotal;
  }

  /** Step back to the previous question, restoring its saved answers. */
  previousChallengeQuestion() {
    if (!this.canGoPrevChallenge) return;
    this.saveChallengeGuesses();
    this.finalizeCurrentQuestion();
    this.challengeIndex--;
    this.applyChallengeQuestion(this.challengeLog[this.challengeIndex]);
  }

  get challengeNeedsExt(): boolean { return this.earMixAll || this.chordTypeLevel !== ChordTypeLevel.Triads; }

  challengeDegreeOptions(): [number, string][] {
    return [...EarTrainingDegrees(this.progMode).entries()].sort((a, b) => a[0] - b[0]).map(([deg, info]) => [deg, info.roman]);
  }

  challengeExtOptions(): string[] {
    if (!this.challengeNeedsExt) return [];
    const m = EarTrainingDegrees(this.progMode);
    if (this.earMixAll) {
      const labels = new Set<string>([""]);
      for (const info of m.values()) {
        labels.add(romanLabel(info.roman, info.seventhQuality).replace(info.roman, ""));
        if (info.extendedOptions.length) info.extendedOptions.forEach(([, suffix]) => labels.add(suffix));
        else labels.add(romanLabel(info.roman, info.extendedQuality).replace(info.roman, ""));
      }
      return [...labels].sort();
    }
    const out: string[] = [];
    for (const info of m.values()) {
      if (this.chordTypeLevel === ChordTypeLevel.Sevenths) {
        out.push(romanLabel(info.roman, info.seventhQuality).replace(info.roman, ""));
      } else if (this.chordTypeLevel === ChordTypeLevel.Extended) {
        if (info.extendedOptions.length) info.extendedOptions.forEach(([, suffix]) => out.push(suffix));
        else out.push(romanLabel(info.roman, info.extendedQuality).replace(info.roman, ""));
      }
    }
    return [...new Set(out.filter((x) => x.length))].sort();
  }

  correctExtLabel(i: number): string {
    const deg = this.progProgression?.degrees[i];
    if (deg == null) return "";
    const info = EarTrainingDegrees(this.progMode).get(deg);
    if (!info) return "";
    return this.progResolved[i]?.romanLabel.replace(info.roman, "") ?? "";
  }

  challengeBarCorrect(i: number): boolean | null {
    const deg = this.progProgression?.degrees[i];
    if (deg == null) return null;
    const g = this.challengeGuessDegree[i];
    if (g == null) return null;
    if (this.challengeNeedsExt && this.challengeGuessExt[i] == null) return null;
    const degOk = g === deg;
    const extOk = !this.challengeNeedsExt || this.challengeGuessExt[i] === this.correctExtLabel(i);
    return degOk && extOk;
  }

  guessChallengeDegree(bar: number, degree: number) {
    if (!this.challengeActive) return;
    this.challengeGuessDegree[bar] = degree;
    this.maybeAutoMark();
    this.notify();
  }
  guessChallengeExt(bar: number, ext: string) {
    if (!this.challengeActive) return;
    this.challengeGuessExt[bar] = ext;
    this.maybeAutoMark();
    this.notify();
  }

  get challengeCombinedMode(): boolean { return !this.earMixAll && this.chordTypeLevel === ChordTypeLevel.Sevenths; }

  challengeCombinedOptions(): [number, string][] {
    return [...EarTrainingDegrees(this.progMode).entries()].sort((a, b) => a[0] - b[0])
      .map(([deg, info]) => [deg, romanLabel(info.roman, info.seventhQuality)] as [number, string]);
  }

  guessChallengeCombined(bar: number, degree: number) {
    if (!this.challengeActive) return;
    const info = EarTrainingDegrees(this.progMode).get(degree);
    if (!info) return;
    const ext = romanLabel(info.roman, info.seventhQuality).replace(info.roman, "");
    this.challengeGuessDegree[bar] = degree;
    this.challengeGuessExt[bar] = ext;
    this.maybeAutoMark();
    this.notify();
  }

  /** Labels for the "hear the degrees" reference palette — PLAIN Arabic numbers
   *  1..7, never Roman numerals / qualities, so hearing a degree doesn't give away
   *  whether the key is major or minor. */
  challengeReferenceLabels(): [number, string][] {
    return [...EarTrainingDegrees(this.progMode).keys()].sort((a, b) => a - b).map((deg) => [deg, String(deg)]);
  }

  rerollChallengeQuestion() {
    if (!this.challengeActive) { this.resetChallengeGuesses(); this.nextProgression(); return; }
    const q = this.freshChallengeQuestion();
    if (this.challengeIndex >= 0 && this.challengeIndex < this.challengeLog.length) this.challengeLog[this.challengeIndex] = q;
    else this.challengeLog.push(q);
    this.applyChallengeQuestion(q);
  }

  // ---- degree-keyboard answering ----

  /** Roman labels for the 7 keyboard keys in the currently-shown system, paired
   *  with the relative-major degree (1..7) each key represents. */
  keyboardKeys(): [number, string][] {
    const map = this.keyboardMinor ? EarTrainingDegrees(TrainingMode.Minor) : EarTrainingDegrees(TrainingMode.Major);
    const mode = this.keyboardMinor ? TrainingMode.Minor : TrainingMode.Major;
    const out: [number, string][] = [];
    for (let pos = 1; pos <= 7; pos++) out.push([majorRelativeDegree(pos, mode), map.get(pos)?.roman ?? String(pos)]);
    return out;
  }

  toggleKeyboardShift() { this.keyboardMinor = !this.keyboardMinor; this.notify(); }

  /**
   * Commit a keyboard answer for [bar]. [majorRel] is the relative-major degree the
   * tapped key stands for (so a major-row and the equivalent minor-row key produce
   * the same answer); it is converted into the actual key's mode for scoring. [roman]
   * is the tapped key's label; [ext] is the chosen extension suffix when the level
   * needs one (ignored for triads; forced to the diatonic 7th in fixed-7ths mode).
   */
  guessChallengeKeyboard(bar: number, majorRel: number, roman: string, ext: string | null) {
    if (!this.challengeActive || bar < 0 || bar > 3) return;
    const deg = degreeFromMajorRelative(majorRel, this.progMode);
    this.challengeGuessDegree[bar] = deg;
    let extSuffix = "";
    if (this.challengeCombinedMode) {
      const info = EarTrainingDegrees(this.progMode).get(deg);
      extSuffix = info ? romanLabel(info.roman, info.seventhQuality).replace(info.roman, "") : "";
      this.challengeGuessExt[bar] = extSuffix;
    } else if (this.challengeNeedsExt) {
      extSuffix = ext ?? "";
      this.challengeGuessExt[bar] = extSuffix;
    }
    this.challengeGuessLabel[bar] = roman + extSuffix;
    this.maybeAutoMark();
    this.notify();
  }

  /** Clear bar [bar]'s keyboard answer (empties its square). */
  clearChallengeBar(bar: number) {
    if (bar < 0 || bar > 3) return;
    this.challengeGuessDegree[bar] = null;
    this.challengeGuessExt[bar] = null;
    this.challengeGuessLabel[bar] = null;
    this.notify();
  }

  private maybeAutoMark() {
    if (!this.challengeActive || this.challengeIndex >= this.challengeTotal) return;
    if (this.challengeAnswers[this.challengeIndex] != null) return;
    const degrees = this.progProgression?.degrees;
    if (!degrees) return;
    for (let i = 0; i < degrees.length; i++) if (this.challengeBarCorrect(i) === null) return;
    let correctCount = 0;
    degrees.forEach((_, i) => { if (this.challengeBarCorrect(i) === true) correctCount++; });
    if (this.challengeIndex < this.challengeBarsCorrect.length) this.challengeBarsCorrect[this.challengeIndex] = correctCount;
    this.challengeAnswers[this.challengeIndex] = correctCount === degrees.length;
  }

  // ---------- Chord Flavor ----------

  flavorPalette = ["", "m", "dim", "aug", "sus2", "sus4", "6", "m6", "7", "maj7", "m7", "m7b5", "add9", "9", "m9", "maj9", "11", "13"];
  flavorAllowed = new Set<string>(["", "m", "7", "maj7", "m7"]);
  flavorIncludeMajor = true;
  flavorIncludeMinor = true;
  flavorKey: PitchClass = 0;
  flavorDegree = 1;
  flavorQuality = "";
  flavorRevealed = false;
  flavorGuessDegree: number | null = null;
  flavorGuessQuality: string | null = null;
  flavorPlaying = false;
  flavorStarted = false;
  flavorShowFretboard = false;
  flavorMode = TrainingMode.Major;
  private flavorToken = 0;
  setFlavorShowFretboard(v: boolean) { this.flavorShowFretboard = v; this.notify(); }

  /**
   * Flavors to present as guess/audition options (#4): only those diatonic in the
   * current key/mode and enabled. If a degree is being guessed, narrow to flavors
   * diatonic for THAT degree. Falls back to the full enabled set if empty.
   */
  flavorQualityOptions(forDegree: number | null = null): string[] {
    const candidates = this.diatonicFlavorCandidates(this.flavorMode, this.flavorAllowed);
    const diatonic = new Set(
      (forDegree != null ? candidates.filter(([d]) => d === forDegree) : candidates).map(([, q]) => q),
    );
    const ordered = this.flavorPalette.filter((q) => diatonic.has(q));
    return ordered.length ? ordered : this.flavorPalette.filter((q) => this.flavorAllowed.has(q));
  }

  toggleFlavorAllowed(sym: string) {
    if (this.flavorAllowed.has(sym)) this.flavorAllowed.delete(sym); else this.flavorAllowed.add(sym);
    this.notify();
  }
  private flavorRootPc(): PitchClass { return degreeRoot(this.flavorKey, this.flavorDegree, this.flavorMode); }
  flavorChordSymbol(): string { return spellPc(this.flavorRootPc()) + this.flavorQuality; }
  flavorDegreeRoman(): string { return EarTrainingDegrees(this.flavorMode).get(this.flavorDegree)?.roman ?? `${this.flavorDegree}`; }
  flavorCadenceLabel(): string { return this.flavorMode === TrainingMode.Major ? "I–V–I" : "i–V–i"; }

  private diatonicFlavorCandidates(mode: TrainingMode, allowed: Set<string> | null): [number, string][] {
    const map = EarTrainingDegrees(mode);
    const out: [number, string][] = [];
    for (const [deg, info] of map) {
      const quals = new Set<string>([info.triadQuality, info.seventhQuality]);
      if (info.extendedOptions.length) info.extendedOptions.forEach(([qual]) => quals.add(qual));
      else quals.add(info.extendedQuality);
      for (const q of quals) if (allowed === null || allowed.has(q)) out.push([deg, q]);
    }
    return out;
  }

  newFlavorChallenge() {
    this.flavorKey = this.fixedKey ?? this.rng.int(12);
    const modes: TrainingMode[] = [];
    if (this.flavorIncludeMajor) modes.push(TrainingMode.Major);
    if (this.flavorIncludeMinor) modes.push(TrainingMode.Minor);
    if (modes.length === 0) modes.push(TrainingMode.Major);
    this.flavorMode = modes[this.rng.int(modes.length)];
    let candidates = this.diatonicFlavorCandidates(this.flavorMode, this.flavorAllowed);
    if (candidates.length === 0) candidates = this.diatonicFlavorCandidates(this.flavorMode, null);
    const [deg, qual] = candidates[this.rng.int(candidates.length)];
    this.flavorDegree = deg;
    this.flavorQuality = qual;
    this.flavorRevealed = false;
    this.flavorGuessDegree = null;
    this.flavorGuessQuality = null;
    this.flavorStarted = true;
    this.flavorToken++;
    const token = this.flavorToken;
    this.flavorPlaying = true;
    this.notify();
    void (async () => {
      try {
        await this.playCadenceInline(token, this.flavorMode, this.flavorKey);
        await sleep(400);
        if (token === this.flavorToken) this.playSymbolOnce(this.flavorChordSymbol(), this.deps.sustainProvider());
      } finally { this.flavorPlaying = false; this.notify(); }
    })();
  }

  replayFlavorCadence() {
    if (this.flavorPlaying) return;
    this.flavorToken++;
    const token = this.flavorToken;
    this.flavorPlaying = true;
    this.notify();
    void (async () => {
      try { await this.playCadenceInline(token, this.flavorMode, this.flavorKey); }
      finally { this.flavorPlaying = false; this.notify(); }
    })();
  }

  playFlavorChord() { this.playSymbolOnce(this.flavorChordSymbol(), this.deps.sustainProvider()); }
  toggleFlavorReveal() { this.flavorRevealed = !this.flavorRevealed; this.notify(); }
  setFlavorGuessDegree(deg: number) { this.flavorGuessDegree = deg; this.auditionFlavorDegree(deg); this.notify(); }
  setFlavorGuessQuality(q: string) { this.flavorGuessQuality = q; this.auditionFlavorQuality(q); this.notify(); }

  auditionFlavorDegree(deg: number) {
    this.playSymbolOnce(spellPc(degreeRoot(this.flavorKey, deg, this.flavorMode)) + this.flavorQuality, this.deps.sustainProvider());
  }
  auditionFlavorQuality(qual: string) { this.playSymbolOnce(spellPc(this.flavorRootPc()) + qual, this.deps.sustainProvider()); }
  auditionProgDegree(deg: number) {
    const level = this.earMixAll ? ChordTypeLevel.Sevenths : this.chordTypeLevel;
    this.playSymbolOnce(resolveDegree(deg, this.progKey, this.progMode, level, this.rng).symbol, this.deps.sustainProvider());
  }

  // Flavor Challenge
  flavorChallengeTotal = 10;
  flavorChActive = false;
  flavorChIndex = 0;
  flavorChScore = 0;
  flavorChAnswered = false;

  startFlavorChallenge() { this.flavorChActive = true; this.flavorChIndex = 0; this.flavorChScore = 0; this.flavorChAnswered = false; this.newFlavorChallenge(); }
  submitFlavorGuess() {
    if (!this.flavorChActive || this.flavorChAnswered) return;
    if (this.flavorGuessDegree == null || this.flavorGuessQuality == null) return;
    this.flavorChAnswered = true;
    this.flavorRevealed = true;
    if (this.flavorGuessDegree === this.flavorDegree && this.flavorGuessQuality === this.flavorQuality) this.flavorChScore++;
    this.notify();
  }
  advanceFlavorChallenge() {
    if (!this.flavorChActive) return;
    if (this.flavorChIndex >= this.flavorChallengeTotal - 1) { this.flavorChIndex = this.flavorChallengeTotal; this.notify(); return; }
    this.flavorChIndex++; this.flavorChAnswered = false; this.newFlavorChallenge();
  }
  exitFlavorChallenge() { this.flavorChActive = false; this.flavorChIndex = 0; this.flavorChAnswered = false; this.notify(); }

  // ---------- Advanced progressions ----------

  advancedMode = false;
  advProg: NamedProgression | null = null;
  advRevealed = false;

  setAdvancedMode(v: boolean) { this.advancedMode = v; this.stopLoop(); this.notify(); }

  nextAdvancedProgression() {
    const np = randomAdvanced(this.rng);
    const key = this.fixedKey ?? this.rng.int(12);
    this.advProg = np;
    this.progKey = key;
    this.progMode = np.tonicMode;
    this.progProgression = null;
    this.progResolved = resolveNamed(np, key);
    this.advRevealed = false;
    this.hasGenerated = true;
    this.prevPlayedShape = null;
    if (this.isLooping) { this.stopLoop(); this.startLoop(); }
    this.notify();
  }
  toggleAdvReveal() { this.advRevealed = !this.advRevealed; this.notify(); }

  advChallengeTotal = 10;
  advChActive = false;
  advChIndex = 0;
  advChScore = 0;
  advChMarked = false;

  startAdvChallenge() { this.advChActive = true; this.advChIndex = 0; this.advChScore = 0; this.advChMarked = false; this.nextAdvancedProgression(); this.startLoop(); }
  markAdv(correct: boolean) {
    if (!this.advChActive || this.advChMarked) return;
    this.advChMarked = true; this.advRevealed = true;
    if (correct) this.advChScore++;
    this.notify();
  }
  advanceAdvChallenge() {
    if (!this.advChActive) return;
    if (this.advChIndex >= this.advChallengeTotal - 1) { this.advChIndex = this.advChallengeTotal; this.stopLoop(); this.notify(); return; }
    this.advChIndex++; this.advChMarked = false; this.nextAdvancedProgression();
  }
  exitAdvChallenge() { this.advChActive = false; this.advChIndex = 0; this.stopLoop(); this.notify(); }

  // ---------- Inversions ----------

  invPalette = ["", "m", "sus2", "sus4", "aug", "dim", "7", "maj7", "m7", "m7b5", "dim7", "6", "m6", "9", "maj9", "m9"];
  invAllowed = new Set<string>(["", "m", "7"]);
  invRoot: PitchClass = 0;
  invQuality = "";
  invInversion = 0;
  invRevealed = false;
  invGuess: number | null = null;
  invStarted = false;
  invPlaying = false;
  invShowFretboard = false;
  private invToken = 0;
  private invHistory: [PitchClass, string, number][] = [];
  private invHistIndex = -1;
  get invHasPrev(): boolean { return this.invHistIndex > 0; }
  get invHasNext(): boolean { return this.invHistIndex >= 0 && this.invHistIndex < this.invHistory.length - 1; }

  toggleInvAllowed(sym: string) {
    if (this.invAllowed.has(sym)) this.invAllowed.delete(sym); else this.invAllowed.add(sym);
    this.notify();
  }
  invCount(): number {
    const q = QUALITIES.get(this.invQuality);
    return q ? inversionCount(q) : 3;
  }
  private invMidis(inversion: number): number[] {
    const q = QUALITIES.get(this.invQuality);
    if (!q) return [];
    return inversionMidis(52 + this.invRoot, q, inversion);
  }
  newInversion() {
    const pool = (this.invAllowed.size ? [...this.invAllowed] : [""]);
    this.invQuality = pool[this.rng.int(pool.length)];
    this.invRoot = this.rng.int(12);
    this.invInversion = this.rng.int(this.invCount());
    this.invRevealed = false; this.invGuess = null; this.invStarted = true;
    if (this.invHistIndex < this.invHistory.length - 1) this.invHistory.length = this.invHistIndex + 1;
    this.invHistory.push([this.invRoot, this.invQuality, this.invInversion]);
    if (this.invHistory.length > 32) this.invHistory.shift();
    this.invHistIndex = this.invHistory.length - 1;
    this.playInversion();
  }
  inversionPrev() {
    if (!this.invHasPrev) return;
    this.invHistIndex--;
    [this.invRoot, this.invQuality, this.invInversion] = this.invHistory[this.invHistIndex];
    this.invRevealed = false; this.invGuess = null;
    this.playInversion(); this.notify();
  }
  inversionNext() {
    if (!this.invHasNext) return;
    this.invHistIndex++;
    [this.invRoot, this.invQuality, this.invInversion] = this.invHistory[this.invHistIndex];
    this.invRevealed = false; this.invGuess = null;
    this.playInversion(); this.notify();
  }
  setInvShowFretboard(v: boolean) { this.invShowFretboard = v; this.notify(); }
  playInversion() {
    const midis = this.invMidis(this.invInversion);
    if (midis.length === 0) return;
    this.invToken++;
    this.invPlaying = true;
    this.notify();
    void (async () => {
      try { this.deps.audio.playChord(midis, this.deps.strumProvider(), this.deps.sustainProvider(), Timbres.Clarity); }
      finally { this.invPlaying = false; this.notify(); }
    })();
  }
  auditionInversion(k: number) {
    const midis = this.invMidis(k);
    if (midis.length) this.deps.audio.playChord(midis, this.deps.strumProvider(), this.deps.sustainProvider(), Timbres.Clarity);
  }
  setInvGuess(k: number) { this.invGuess = k; this.auditionInversion(k); this.notify(); }
  toggleInvReveal() { this.invRevealed = !this.invRevealed; this.notify(); }

  invChallengeTotal = 10;
  invChActive = false;
  invChIndex = 0;
  invChScore = 0;
  invChAnswered = false;

  startInvChallenge() { this.invChActive = true; this.invChIndex = 0; this.invChScore = 0; this.invChAnswered = false; this.newInversion(); }
  submitInvGuess() {
    if (!this.invChActive || this.invChAnswered) return;
    if (this.invGuess == null) return;
    this.invChAnswered = true; this.invRevealed = true;
    if (this.invGuess === this.invInversion) this.invChScore++;
    this.notify();
  }
  advanceInvChallenge() {
    if (!this.invChActive) return;
    if (this.invChIndex >= this.invChallengeTotal - 1) { this.invChIndex = this.invChallengeTotal; this.notify(); return; }
    this.invChIndex++; this.invChAnswered = false; this.newInversion();
  }
  exitInvChallenge() { this.invChActive = false; this.invChIndex = 0; this.notify(); }

  // ---------- Aug vs Dim ----------

  augDimPalette = ["aug", "dim", "dim7", "m7b5", "7#5", "maj7#5"];
  augDimAllowed = new Set<string>(["aug", "dim"]);
  adRoot: PitchClass = 0;
  adQuality = "aug";
  adRevealed = false;
  adGuess: string | null = null;
  adStarted = false;
  adShowFretboard = false;
  private adToken = 0;
  // Drawn-chord history so Prev/Next revisit chords without re-randomizing (#1).
  private adHistory: [PitchClass, string][] = [];
  private adHistIndex = -1;
  get adHasPrev(): boolean { return this.adHistIndex > 0; }
  get adHasNext(): boolean { return this.adHistIndex >= 0 && this.adHistIndex < this.adHistory.length - 1; }

  toggleAugDimAllowed(sym: string) {
    if (this.augDimAllowed.has(sym)) this.augDimAllowed.delete(sym); else this.augDimAllowed.add(sym);
    this.notify();
  }
  augDimFamily(sym: string): string {
    return sym.startsWith("aug") || sym === "7#5" || sym === "maj7#5" ? "Augmented" : "Diminished";
  }
  private adMidis(quality: string): number[] {
    const q = QUALITIES.get(quality);
    if (!q) return [];
    return q.intervals.map((iv) => 52 + this.adRoot + iv);
  }
  newAugDim() {
    const pool = (this.augDimAllowed.size ? [...this.augDimAllowed] : ["aug", "dim"]);
    this.adQuality = pool[this.rng.int(pool.length)];
    this.adRoot = this.rng.int(12);
    this.adRevealed = false; this.adGuess = null; this.adStarted = true;
    if (this.adHistIndex < this.adHistory.length - 1) this.adHistory.length = this.adHistIndex + 1;
    this.adHistory.push([this.adRoot, this.adQuality]);
    if (this.adHistory.length > 32) this.adHistory.shift();
    this.adHistIndex = this.adHistory.length - 1;
    this.playAugDim();
    this.notify();   // re-render so the reveal card + fretboard refresh to the new chord
  }
  augDimPrev() {
    if (!this.adHasPrev) return;
    this.adHistIndex--;
    [this.adRoot, this.adQuality] = this.adHistory[this.adHistIndex];
    this.adRevealed = false; this.adGuess = null;
    this.playAugDim(); this.notify();
  }
  augDimNext() {
    if (!this.adHasNext) return;
    this.adHistIndex++;
    [this.adRoot, this.adQuality] = this.adHistory[this.adHistIndex];
    this.adRevealed = false; this.adGuess = null;
    this.playAugDim(); this.notify();
  }
  setAdShowFretboard(v: boolean) { this.adShowFretboard = v; this.notify(); }
  playAugDim() {
    const midis = this.adMidis(this.adQuality);
    if (midis.length === 0) return;
    this.adToken++;
    this.deps.audio.playChord(midis, this.deps.strumProvider(), this.deps.sustainProvider(), Timbres.Clarity);
  }
  auditionAugDim(sym: string) {
    const midis = this.adMidis(sym);
    if (midis.length) this.deps.audio.playChord(midis, this.deps.strumProvider(), this.deps.sustainProvider(), Timbres.Clarity);
  }
  setAdGuess(sym: string) { this.adGuess = sym; this.auditionAugDim(sym); this.notify(); }
  toggleAugDimReveal() { this.adRevealed = !this.adRevealed; this.notify(); }

  augDimChallengeTotal = 10;
  adChActive = false;
  adChIndex = 0;
  adChScore = 0;
  adChAnswered = false;

  startAugDimChallenge() { this.adChActive = true; this.adChIndex = 0; this.adChScore = 0; this.adChAnswered = false; this.newAugDim(); }
  submitAugDimGuess() {
    if (!this.adChActive || this.adChAnswered) return;
    if (this.adGuess == null) return;
    this.adChAnswered = true; this.adRevealed = true;
    if (this.adGuess === this.adQuality) this.adChScore++;
    this.notify();
  }
  advanceAugDimChallenge() {
    if (!this.adChActive) return;
    if (this.adChIndex >= this.augDimChallengeTotal - 1) { this.adChIndex = this.augDimChallengeTotal; this.notify(); return; }
    this.adChIndex++; this.adChAnswered = false; this.newAugDim();
  }
  exitAugDimChallenge() { this.adChActive = false; this.adChIndex = 0; this.notify(); }

  // ---------- #6 Interval identification ----------

  intervalChallengeTotal = 10;
  intervalKey: PitchClass = 0;
  intervalDirection = IntervalDirection.Ascending;
  intervalChActive = false;
  intervalChIndex = 0;
  intervalChScore = 0;
  intervalChAnswered = false;
  intervalSemitones = 0;
  intervalAscending = true;
  intervalGuess: number | null = null;
  intervalPlaying = false;
  private intervalToken = 0;

  private intervalTonicMidi(): number { return 60 + (((this.intervalKey + 6) % 12) - 6); }

  setIntervalDirection(d: IntervalDirection) { this.intervalDirection = d; this.notify(); }
  setIntervalGuess(s: number) { this.intervalGuess = s; this.notify(); }

  intervalTranspose(n: number) {
    this.intervalKey = ((this.intervalKey + n) % 12 + 12) % 12;
    this.notify();
    if (this.intervalChActive) this.playIntervalTonicCadence();
  }

  playIntervalTonicCadence() {
    this.intervalToken++;
    const token = this.intervalToken;
    this.intervalPlaying = true;
    this.notify();
    void (async () => {
      try {
        for (const deg of [1, 5, 1]) {
          if (token !== this.intervalToken) return;
          const root = degreeRoot(this.intervalKey, deg, TrainingMode.Major);
          const q = EarTrainingDegrees(TrainingMode.Major).get(deg)?.triadQuality ?? "";
          this.playSymbolOnce(spellPc(root) + q, 600);
          await sleep(650);
        }
      } finally { this.intervalPlaying = false; this.notify(); }
    })();
  }

  playIntervalTonic() {
    this.deps.audio.playNote(this.intervalTonicMidi(), this.deps.sustainProvider());
  }

  playIntervalQuestion() {
    if (this.intervalPlaying) return;
    this.intervalToken++;
    const token = this.intervalToken;
    this.intervalPlaying = true;
    this.notify();
    void (async () => {
      try {
        const tonic = this.intervalTonicMidi();
        this.deps.audio.playNote(tonic, this.deps.sustainProvider());
        await sleep(700);
        if (token !== this.intervalToken) return;
        this.deps.audio.playNote(
          intervalTargetMidi(tonic, this.intervalSemitones, this.intervalAscending),
          this.deps.sustainProvider());
      } finally { this.intervalPlaying = false; this.notify(); }
    })();
  }

  private drawIntervalQuestion() {
    this.intervalSemitones = this.rng.int(INTERVAL_CHOICES.length);   // 0..12
    this.intervalAscending = this.intervalDirection === IntervalDirection.Ascending ? true
      : this.intervalDirection === IntervalDirection.Descending ? false
      : this.rng.int(2) === 0;
    this.intervalGuess = null;
    this.intervalChAnswered = false;
  }

  startIntervalChallenge() {
    this.intervalChActive = true; this.intervalChIndex = 0; this.intervalChScore = 0;
    this.drawIntervalQuestion();
    this.intervalToken++;
    const token = this.intervalToken;
    this.intervalPlaying = true;
    this.notify();
    void (async () => {
      try {
        for (const deg of [1, 5, 1]) {
          if (token !== this.intervalToken) return;
          const root = degreeRoot(this.intervalKey, deg, TrainingMode.Major);
          const q = EarTrainingDegrees(TrainingMode.Major).get(deg)?.triadQuality ?? "";
          this.playSymbolOnce(spellPc(root) + q, 600);
          await sleep(650);
        }
        await sleep(300);
        if (token !== this.intervalToken) return;
        const tonic = this.intervalTonicMidi();
        this.deps.audio.playNote(tonic, this.deps.sustainProvider());
        await sleep(700);
        if (token !== this.intervalToken) return;
        this.deps.audio.playNote(
          intervalTargetMidi(tonic, this.intervalSemitones, this.intervalAscending),
          this.deps.sustainProvider());
      } finally { this.intervalPlaying = false; this.notify(); }
    })();
  }

  submitIntervalGuess() {
    if (!this.intervalChActive || this.intervalChAnswered) return;
    if (this.intervalGuess == null) return;
    this.intervalChAnswered = true;
    if (this.intervalGuess === this.intervalSemitones) this.intervalChScore++;
    this.notify();
  }

  advanceIntervalChallenge() {
    if (!this.intervalChActive) return;
    if (this.intervalChIndex >= this.intervalChallengeTotal - 1) { this.intervalChIndex = this.intervalChallengeTotal; this.notify(); return; }
    this.intervalChIndex++;
    this.drawIntervalQuestion();
    this.playIntervalQuestion();
  }

  exitIntervalChallenge() { this.intervalChActive = false; this.intervalChIndex = 0; this.intervalChAnswered = false; this.notify(); }

  // ---------- shared playback helpers ----------

  private async playCadenceInline(token: number, mode: TrainingMode, key: PitchClass): Promise<void> {
    const map = EarTrainingDegrees(mode);
    for (const deg of [1, 5, 1]) {
      if (token !== this.flavorToken) return;
      const root = degreeRoot(key, deg, mode);
      this.playSymbolOnce(spellPc(root) + (map.get(deg)?.triadQuality ?? ""), 600);
      await sleep(650);
    }
  }

  private playSymbolOnce(symbol: string, sustainMs: number) {
    const parsed = parseChord(symbol);
    if (!parsed) return;
    const [root, q] = parsed;
    const tuning = this.deps.tuningProvider();
    const shapes = this.gen(VoicingStyle.Standard).shapesFor(root, q, tuning, DISPLAY_FRETS);
    const shape = shapes.find((s) => s.cagedShape === CagedShape.E) ?? shapes[0];
    if (!shape) return;
    const midis = shape.notes.filter((n) => n !== null).map((n) => n!.midi);
    if (midis.length === 0) return;
    this.deps.audio.playChord(midis, this.deps.strumProvider(), sustainMs, Timbres.Clarity);
  }

  release() {
    this.stopLoop();
    this.cadenceToken++; this.flavorToken++; this.n2cToken++; this.invToken++; this.adToken++;
    this.intervalToken++;
  }
}

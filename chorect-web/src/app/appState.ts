// Reactive app state, ported from app/.../AppState.kt (Phase 1 subset: Fretboard,
// Tuner, Options). Persistence uses localStorage in place of Android DataStore.
//
// State is a plain observable: mutating methods change fields then call notify(),
// and the UI subscribes via subscribe() to re-render. (No framework — the Kotlin
// app's Compose recomposition is replaced by an explicit render pass.)

import {
  Instrument, InstrumentInfo, Tuning, Note, note, FretPosition, fp, fpKey,
  noteAt, stringCount, parseNote,
  ChordShape, VoicingStyle, parseChord, ChordShapeGenerator,
} from "../theory";
import * as Tunings from "../theory/tunings";
import { WebAudioEngine, Timbre, Timbres, midiToFreqA4 } from "../audio";

export const DISPLAY_FRETS = 14;
const MIDI_MIN = 28; // E1
const MIDI_MAX = 84; // C6

export interface ChallengeScore {
  score: number;
  total: number;
  durationMs: number;
  dateMillis: number;
}

/** Higher score first; ties broken by faster (smaller) completion time. */
export const CHALLENGE_SCORE_ORDER = (a: ChallengeScore, b: ChallengeScore): number =>
  b.score - a.score || a.durationMs - b.durationMs;

export enum DisplayMode { None = "None", Chord = "Chord", Scale = "Scale", Pick = "Pick" }
export enum LabelMode { Notes = "Notes", Intervals = "Intervals", Empty = "Empty" }
export enum Sheet { Fretboard = "Fretboard", Options = "Options", Tuner = "Tuner", Loop = "Loop", EarTraining = "EarTraining", SambaLooper = "SambaLooper", Decompose = "Decompose" }
export enum ChordScaleView { AllNotes = "AllNotes", Positions = "Positions" }

const LS_KEY = "chorect-web.v1";

interface Persisted {
  instrument: string;
  tuningName: string;
  labelMode: string;
  leftHanded: boolean;
  voicingShell: boolean;
  a4Hz: number;
  ringSustainMs: number;
  strumMs: number;
  tapOnTouchDown: boolean;
  customTunings: Record<string, number[]>;
  challengeScores: ChallengeScore[];
  drumPatterns: Record<string, string>;
}

export class AppState {
  instrument = Instrument.Guitar;
  tuningName = "Standard";
  liveTuning: Tuning = Tunings.standard;
  isEditedTuning = false;

  chordInput = "Cmaj7";
  scaleRoot = "A";
  scaleType = "minor pentatonic";

  labelMode = LabelMode.Intervals;
  selectedPosition: FretPosition | null = null;
  leftHanded = false;

  displayMode = DisplayMode.None;
  currentSheet: Sheet | null = null;
  lastSheet: Sheet | null = null;
  chordView = ChordScaleView.AllNotes;
  scaleView = ChordScaleView.AllNotes;
  chordPositionIndex = 0;
  scalePositionIndex = 0;

  pickedPositions = new Set<string>(); // fpKey strings
  mutedStrings = new Set<number>();

  voicingStyle = VoicingStyle.Standard;

  a4Hz = 440;
  ringSustainMs = 1500;
  strumMs = 30;
  tapOnTouchDown = false;

  customTunings = new Map<string, Tuning>();
  challengeScores: ChallengeScore[] = [];
  /** Saved drum beats: name → encoded PercussionPattern string (insertion order). */
  drumPatterns = new Map<string, string>();

  private listeners = new Set<() => void>();

  constructor(public readonly audio: WebAudioEngine) {
    this.load();
  }

  // ---------- reactivity ----------

  subscribe(fn: () => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  private notify(): void {
    for (const fn of this.listeners) fn();
  }

  // ---------- persistence ----------

  private load(): void {
    const raw = localStorage.getItem(LS_KEY);
    if (!raw) return;
    try {
      const p = JSON.parse(raw) as Partial<Persisted>;
      if (p.instrument && p.instrument in InstrumentInfo) this.instrument = p.instrument as Instrument;
      if (p.labelMode && p.labelMode in LabelMode) this.labelMode = p.labelMode as LabelMode;
      if (typeof p.leftHanded === "boolean") this.leftHanded = p.leftHanded;
      if (typeof p.voicingShell === "boolean") this.voicingStyle = p.voicingShell ? VoicingStyle.Shell : VoicingStyle.Standard;
      if (typeof p.a4Hz === "number") this.a4Hz = p.a4Hz;
      if (typeof p.ringSustainMs === "number") this.ringSustainMs = p.ringSustainMs;
      if (typeof p.strumMs === "number") this.strumMs = p.strumMs;
      if (typeof p.tapOnTouchDown === "boolean") this.tapOnTouchDown = p.tapOnTouchDown;
      if (p.customTunings) {
        for (const [name, midis] of Object.entries(p.customTunings)) {
          this.customTunings.set(name, { openStrings: midis.map((m) => note(m)) });
        }
      }
      if (Array.isArray(p.challengeScores)) this.challengeScores = p.challengeScores.slice();
      if (p.drumPatterns) for (const [name, enc] of Object.entries(p.drumPatterns)) this.drumPatterns.set(name, enc);
      // Resolve the saved tuning name against presets + customs for the current instrument.
      const name = p.tuningName ?? Tunings.defaultNameFor(this.instrument);
      const resolved = Tunings.allPresets.get(name) ?? this.customTunings.get(name) ?? Tunings.defaultFor(this.instrument);
      this.tuningName = name;
      this.liveTuning = resolved;
    } catch {
      /* ignore corrupt storage */
    }
  }

  private save(): void {
    const customTunings: Record<string, number[]> = {};
    for (const [name, t] of this.customTunings) customTunings[name] = t.openStrings.map((n) => n.midi);
    const p: Persisted = {
      instrument: this.instrument,
      tuningName: this.tuningName,
      labelMode: this.labelMode,
      leftHanded: this.leftHanded,
      voicingShell: this.voicingStyle === VoicingStyle.Shell,
      a4Hz: this.a4Hz,
      ringSustainMs: this.ringSustainMs,
      strumMs: this.strumMs,
      tapOnTouchDown: this.tapOnTouchDown,
      customTunings,
      challengeScores: this.challengeScores,
      drumPatterns: Object.fromEntries(this.drumPatterns),
    };
    localStorage.setItem(LS_KEY, JSON.stringify(p));
  }

  /** Record a finished progression-challenge result (best first, keep top 10). */
  recordChallengeScore(score: number, total: number, durationMs: number): void {
    this.commit(() => {
      this.challengeScores = [...this.challengeScores, { score, total, durationMs, dateMillis: Date.now() }]
        .sort(CHALLENGE_SCORE_ORDER)
        .slice(0, 10);
    });
  }

  saveDrumPattern(name: string, encoded: string): void {
    this.commit(() => { this.drumPatterns.set(name, encoded); });
  }
  deleteDrumPattern(name: string): void {
    this.commit(() => { this.drumPatterns.delete(name); });
  }

  /** Mutate + persist + re-render in one shot. */
  private commit(mutate: () => void): void {
    mutate();
    this.save();
    this.notify();
  }

  // ---------- timbre ----------

  private get timbre(): Timbre {
    return this.instrument === Instrument.Guitar ? Timbres.Guitar : Timbres.Cavaquinho;
  }

  // ---------- instrument / tuning ----------

  setInstrument(value: Instrument): void {
    if (this.instrument === value) return;
    this.commit(() => {
      this.instrument = value;
      this.tuningName = Tunings.defaultNameFor(value);
      this.liveTuning = Tunings.defaultFor(value);
      this.isEditedTuning = false;
      this.chordPositionIndex = 0;
    });
  }

  selectTuning(name: string, tuning: Tuning): void {
    this.commit(() => {
      this.tuningName = name;
      this.liveTuning = tuning;
      this.isEditedTuning = false;
      this.chordPositionIndex = 0;
    });
  }

  adjustString(stringIdx: number, delta: number): void {
    const current = this.liveTuning.openStrings[stringIdx];
    const newMidi = Math.min(Math.max(current.midi + delta, MIDI_MIN), MIDI_MAX);
    if (newMidi === current.midi) return;
    this.commit(() => {
      const open = this.liveTuning.openStrings.slice();
      open[stringIdx] = note(newMidi);
      this.liveTuning = { openStrings: open };
      this.isEditedTuning = true;
    });
  }

  saveCustomTuning(name: string): void {
    const clean = name.trim();
    if (clean.length === 0 || clean.includes("|") || clean.includes(";")) return;
    this.commit(() => {
      this.customTunings.set(clean, { openStrings: this.liveTuning.openStrings.slice() });
      this.tuningName = clean;
      this.isEditedTuning = false;
    });
  }

  deleteCustomTuning(name: string): void {
    this.commit(() => {
      this.customTunings.delete(name);
      if (this.tuningName === name) {
        this.tuningName = "Standard";
        this.liveTuning = Tunings.standard;
        this.isEditedTuning = false;
      }
    });
  }

  resetTuningToSaved(): void {
    this.commit(() => {
      this.liveTuning =
        Tunings.allPresets.get(this.tuningName) ?? this.customTunings.get(this.tuningName) ?? Tunings.standard;
      this.isEditedTuning = false;
    });
  }

  // ---------- simple setters ----------

  setChordInput(symbol: string): void { this.commit(() => { this.chordInput = symbol; this.chordPositionIndex = 0; }); }
  setScaleRoot(name: string): void { this.commit(() => { this.scaleRoot = name; this.scalePositionIndex = 0; }); }
  setScaleType(name: string): void { this.commit(() => { this.scaleType = name; this.scalePositionIndex = 0; }); }
  setDisplayMode(m: DisplayMode): void { this.commit(() => { this.displayMode = m; }); }
  setChordView(v: ChordScaleView): void { this.commit(() => { this.chordView = v; }); }
  setScaleView(v: ChordScaleView): void { this.commit(() => { this.scaleView = v; }); }
  setLabelMode(m: LabelMode): void { this.commit(() => { this.labelMode = m; }); }
  toggleLeftHanded(v: boolean): void { this.commit(() => { this.leftHanded = v; }); }
  setTapOnTouchDown(v: boolean): void { this.commit(() => { this.tapOnTouchDown = v; }); }
  setA4Hz(v: number): void { this.commit(() => { this.a4Hz = Math.min(Math.max(Math.round(v), 435), 445); }); }
  setRingSustainMs(v: number): void { this.commit(() => { this.ringSustainMs = Math.min(Math.max(Math.round(v), 300), 4000); }); }
  setStrumMs(v: number): void { this.commit(() => { this.strumMs = Math.min(Math.max(Math.round(v), 0), 150); }); }
  toggleVoicingStyle(shell: boolean): void { this.commit(() => { this.voicingStyle = shell ? VoicingStyle.Shell : VoicingStyle.Standard; this.chordPositionIndex = 0; }); }

  // ---------- position scroller ----------

  resetChordPosition(): void { this.chordPositionIndex = 0; }
  resetScalePosition(): void { this.scalePositionIndex = 0; }
  stepChordPosition(delta: number, count: number): void {
    if (count <= 0) return;
    this.commit(() => { this.chordPositionIndex = (((this.chordPositionIndex + delta) % count) + count) % count; });
  }
  stepScalePosition(delta: number, count: number): void {
    if (count <= 0) return;
    this.commit(() => { this.scalePositionIndex = (((this.scalePositionIndex + delta) % count) + count) % count; });
  }

  // ---------- sheets ----------

  openSheet(sheet: Sheet): void {
    this.commit(() => {
      this.currentSheet = sheet;
      this.lastSheet = sheet;
      if (sheet === Sheet.Fretboard && this.displayMode === DisplayMode.None) this.displayMode = DisplayMode.Chord;
    });
  }
  closeSheet(): void { this.commit(() => { this.currentSheet = null; }); }
  reopenLastSheet(): void { if (this.lastSheet) this.openSheet(this.lastSheet); }

  // ---------- audio actions ----------

  tapPosition(pos: FretPosition): void {
    if (pos.stringIndex < 0 || pos.stringIndex >= stringCount(this.liveTuning)) return;
    this.selectedPosition = pos;
    const n = noteAt(this.liveTuning, pos);
    this.audio.playNote(n.midi, this.ringSustainMs, this.timbre);
    this.notify();
  }

  playShape(shape: ChordShape): void {
    const midis = shape.notes.filter((n): n is Note => n !== null).map((n) => n.midi);
    if (midis.length) this.audio.playChord(midis, this.strumMs, this.ringSustainMs, this.timbre);
  }

  playReferencePitch(midi: number): void {
    const freq = midiToFreqA4(midi, this.a4Hz);
    this.audio.playFrequency(freq, this.ringSustainMs, this.timbre);
  }

  // ---------- pick mode ----------

  togglePick(pos: FretPosition): void {
    if (pos.stringIndex < 0 || pos.stringIndex >= stringCount(this.liveTuning)) return;
    this.commit(() => {
      if (this.mutedStrings.has(pos.stringIndex)) this.mutedStrings.delete(pos.stringIndex);
      const key = fpKey(pos);
      if (this.pickedPositions.has(key)) this.pickedPositions.delete(key);
      else this.pickedPositions.add(key);
    });
  }

  toggleMutedString(stringIdx: number): void {
    if (stringIdx < 0 || stringIdx >= stringCount(this.liveTuning)) return;
    this.commit(() => {
      if (this.mutedStrings.has(stringIdx)) {
        this.mutedStrings.delete(stringIdx);
      } else {
        for (const key of [...this.pickedPositions]) {
          if (parseInt(key.split(",")[0], 10) === stringIdx) this.pickedPositions.delete(key);
        }
        this.mutedStrings.add(stringIdx);
      }
    });
  }

  clearPicked(): void {
    this.commit(() => { this.pickedPositions.clear(); this.mutedStrings.clear(); });
  }

  strumPicked(arpeggio = false): void {
    const positions = [...this.pickedPositions]
      .map((k) => fp(parseInt(k.split(",")[0], 10), parseInt(k.split(",")[1], 10)))
      .filter((p) => p.stringIndex < stringCount(this.liveTuning) && !this.mutedStrings.has(p.stringIndex))
      .sort((a, b) => (a.stringIndex - b.stringIndex) || (a.fret - b.fret));
    const midis = positions.map((p) => noteAt(this.liveTuning, p).midi);
    if (midis.length) {
      this.audio.playChord(midis, arpeggio ? Math.max(this.strumMs * 4, 100) : this.strumMs, this.ringSustainMs, this.timbre);
    }
  }

  // ---------- derived ----------

  chordGenerator(): ChordShapeGenerator {
    return new ChordShapeGenerator(InstrumentInfo[this.instrument].maxFretSpan, true, 3, this.voicingStyle);
  }
}

// Re-export parseChord for the UI without a separate import line.
export { parseChord, parseNote };

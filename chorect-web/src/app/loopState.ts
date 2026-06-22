// Progression-looper state + scheduler, ported from the loop section of
// app/.../AppState.kt + Loop.kt. Separate class (like the ear/drum states); the App
// owns one instance and the main fretboard reads playingShape for "watch on neck".

import {
  Tuning, PitchClass, PC, ChordShape, ChordShapeGenerator, VoicingStyle, CagedShape,
  parseChord, spellPc, TrainingMode, ChordTypeLevel, degreeRoot, degreesMapFor, pickMinMovement,
} from "../theory";
import { WebAudioEngine, Timbre } from "../audio";

const DISPLAY_FRETS = 14;
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export enum StrumPattern { Down = "Down", Up = "Up", Arpeggio = "Arpeggio", Sustain = "Sustain" }
export const StrumGlyph: Record<StrumPattern, string> = {
  [StrumPattern.Down]: "↓", [StrumPattern.Up]: "↑", [StrumPattern.Arpeggio]: "≋", [StrumPattern.Sustain]: "·",
};
export const StrumName: Record<StrumPattern, string> = {
  [StrumPattern.Down]: "Down", [StrumPattern.Up]: "Up", [StrumPattern.Arpeggio]: "Arp", [StrumPattern.Sustain]: "Sustain",
};

export interface LoopSlot {
  chordSymbol: string | null;
  voicingIndex: number;
  strum: StrumPattern;
}
function slot(chordSymbol: string | null = null, voicingIndex = 0, strum = StrumPattern.Down): LoopSlot {
  return { chordSymbol, voicingIndex, strum };
}

/** ii–V–I in C, the default fresh progression. */
function defaultProgression(): LoopSlot[][] {
  return [[slot("Dm7")], [slot("G7")], [slot("Cmaj7")], [slot("Cmaj7")]];
}

export interface LoopDeps {
  audio: WebAudioEngine;
  tuningProvider: () => Tuning;
  voicingStyleProvider: () => VoicingStyle;
  maxFretSpanProvider: () => number;
  strumProvider: () => number;
  sustainProvider: () => number;
  timbreProvider: () => Timbre;
  onChange: () => void;
}

export class LoopState {
  bpm = 80;
  slotsPerBar = 1;
  progression: LoopSlot[][] = defaultProgression();
  isLooping = false;
  currentBar = 0;
  currentSlot = 0;
  playingShape: ChordShape | null = null;
  editingSlot: [number, number] | null = null;

  buildExpanded = false;
  buildKey: PitchClass = PC.C;
  buildMode = TrainingMode.Major;
  buildLevel = ChordTypeLevel.Sevenths;
  buildOverride: string | null = null;
  buildCursor = 0;
  normalized = false;

  private token = 0;

  constructor(private deps: LoopDeps) {}

  private notify() { this.deps.onChange(); }

  private gen(): ChordShapeGenerator {
    return new ChordShapeGenerator(this.deps.maxFretSpanProvider(), true, 3, this.deps.voicingStyleProvider());
  }

  // ---- editing ----

  setLoopSlot(barIdx: number, slotIdx: number, s: LoopSlot) {
    const bar = this.progression[barIdx];
    if (!bar || slotIdx < 0 || slotIdx >= bar.length) return;
    const bars = this.progression.map((b) => b.slice());
    bars[barIdx][slotIdx] = s;
    this.progression = bars;
    this.notify();
  }

  setLoopSlotChord(barIdx: number, slotIdx: number, chordSymbol: string | null) {
    const current = this.progression[barIdx]?.[slotIdx];
    if (!current) return;
    const cleaned = chordSymbol && chordSymbol.trim() ? chordSymbol : null;
    if (cleaned === current.chordSymbol) return;
    const bars = this.progression.map((b) => b.slice());
    bars[barIdx][slotIdx] = { ...current, chordSymbol: cleaned, voicingIndex: 0 };
    this.progression = bars;
    this.normalizeLoopVoicings();
    this.notify();
  }

  loadProgressionIntoLoop(chordSymbols: string[]) {
    const symbols = chordSymbols.filter((s) => s.trim());
    if (symbols.length === 0) return;
    this.progression = symbols.map((s) => [slot(s)]);
    this.currentBar = 0;
    this.currentSlot = 0;
    this.buildCursor = 0;
    this.slotsPerBar = 1;
    this.normalizeLoopVoicings();
    this.notify();
  }

  /** Idempotent voice-leading pass: first chord prefers E-shape, then min-movement. */
  normalizeLoopVoicings() {
    const newBars: LoopSlot[][] = [];
    let prevShape: ChordShape | null = null;
    for (const bar of this.progression) {
      const newBar: LoopSlot[] = [];
      for (const s of bar) {
        const sym = s.chordSymbol;
        if (sym == null) { newBar.push(s); continue; }
        const parsed = parseChord(sym);
        if (!parsed) { newBar.push(s); continue; }
        const shapes = this.gen().shapesFor(parsed[0], parsed[1], this.deps.tuningProvider(), DISPLAY_FRETS);
        if (shapes.length === 0) { newBar.push(s); continue; }
        let pickedIdx: number;
        if (prevShape == null) {
          const eIdx = shapes.findIndex((sh) => sh.cagedShape === CagedShape.E);
          pickedIdx = eIdx >= 0 ? eIdx : 0;
        } else {
          pickedIdx = pickMinMovement(prevShape, shapes);
        }
        newBar.push({ ...s, voicingIndex: pickedIdx });
        prevShape = shapes[pickedIdx];
      }
      newBars.push(newBar);
    }
    this.progression = newBars;
    this.normalized = true;
  }

  /** Normalize once on entry if not already done. */
  ensureNormalized() {
    if (!this.normalized) this.normalizeLoopVoicings();
  }

  setSlotsPerBar(n: number) {
    const clamped = Math.min(Math.max(n, 1), 4);
    if (clamped === this.slotsPerBar) return;
    this.progression = this.progression.map((bar) => {
      const first = bar[0] ?? slot();
      const out = [first];
      for (let i = 0; i < clamped - 1; i++) out.push(slot());
      return out;
    });
    this.slotsPerBar = clamped;
    this.notify();
  }

  setBarCount(count: number) {
    const clamped = Math.min(Math.max(count, 1), 16);
    const empty = () => Array.from({ length: this.slotsPerBar }, () => slot());
    this.progression = Array.from({ length: clamped }, (_, i) => this.progression[i] ?? empty());
    this.notify();
  }

  setBpm(v: number) { this.bpm = Math.round(v); this.notify(); }
  setEditingSlot(s: [number, number] | null) { this.editingSlot = s; this.notify(); }

  // ---- transport ----

  startLoop() {
    if (this.isLooping) return;
    this.ensureNormalized();
    this.isLooping = true;
    this.token++;
    const token = this.token;
    this.notify();
    void (async () => {
      while (this.isLooping && token === this.token) {
        for (let barIdx = 0; barIdx < this.progression.length; barIdx++) {
          if (!this.isLooping || token !== this.token) break;
          this.currentBar = barIdx;
          await this.playBar(this.progression[barIdx], token);
        }
      }
    })();
  }

  stopLoop() {
    this.isLooping = false;
    this.token++;
    this.playingShape = null;
    this.deps.audio.stop();
    this.notify();
  }

  private async playBar(bar: LoopSlot[], token: number) {
    const beatMs = 60000 / Math.max(this.bpm, 20);
    const slotMs = (beatMs * 4) / Math.max(bar.length, 1);
    for (let slotIdx = 0; slotIdx < bar.length; slotIdx++) {
      if (!this.isLooping || token !== this.token) return;
      this.currentSlot = slotIdx;
      const s = bar[slotIdx];
      const parsed = s.chordSymbol ? parseChord(s.chordSymbol) : null;
      if (parsed && s.strum !== StrumPattern.Sustain) {
        const shapes = this.gen().shapesFor(parsed[0], parsed[1], this.deps.tuningProvider(), DISPLAY_FRETS);
        const shape = shapes[s.voicingIndex] ?? shapes[0];
        if (shape) {
          this.playingShape = shape;
          const midis = shape.notes.filter((n) => n !== null).map((n) => n!.midi);
          const ordered = s.strum === StrumPattern.Up ? midis.slice().reverse() : midis;
          // Sustain is already excluded by the guard above; only Down/Up/Arpeggio reach here.
          const strumDelay = s.strum === StrumPattern.Arpeggio ? Math.max(this.deps.strumProvider() * 4, 100) : this.deps.strumProvider();
          this.deps.audio.playChord(ordered, strumDelay, Math.max(Math.round(slotMs * 0.9), 150), this.deps.timbreProvider());
        }
      }
      this.notify();
      await sleep(slotMs);
    }
  }

  // ---- build by degree ----

  setBuildKeyRandom() { this.buildKey = Math.floor(Math.random() * 12); this.notify(); }
  resetBuildCursor() { this.buildCursor = 0; this.notify(); }

  applyLoopDegree(degree: number) {
    const rootPc = degreeRoot(this.buildKey, degree, this.buildMode);
    const rootName = spellPc(rootPc);
    let quality: string;
    if (this.buildOverride != null) {
      quality = this.buildOverride;
    } else {
      const info = degreesMapFor(this.buildMode).get(degree);
      if (!info) return;
      quality = this.buildLevel === ChordTypeLevel.Triads ? info.triadQuality
        : this.buildLevel === ChordTypeLevel.Sevenths ? info.seventhQuality : info.extendedQuality;
    }
    const symbol = `${rootName}${quality}`;
    if (this.editingSlot) {
      this.setLoopSlotChord(this.editingSlot[0], this.editingSlot[1], symbol);
    } else {
      const barIdx = Math.min(Math.max(this.buildCursor, 0), this.progression.length - 1);
      this.setLoopSlotChord(barIdx, 0, symbol);
      this.buildCursor = (barIdx + 1) % Math.max(this.progression.length, 1);
      this.notify();
    }
  }

  /** Voicings list for the slot editor (chord shapes of the slot's chord). */
  shapesForSlot(barIdx: number, slotIdx: number): ChordShape[] {
    const s = this.progression[barIdx]?.[slotIdx];
    if (!s?.chordSymbol) return [];
    const parsed = parseChord(s.chordSymbol);
    if (!parsed) return [];
    return this.gen().shapesFor(parsed[0], parsed[1], this.deps.tuningProvider(), DISPLAY_FRETS);
  }

  hasAnyChord(): boolean {
    return this.progression.some((bar) => bar.some((s) => s.chordSymbol != null));
  }
}

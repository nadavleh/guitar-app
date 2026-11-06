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
  bpm = 150;
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

  /** First playable chord shape in the progression, or null if empty. Lights the
   *  neck immediately on "Watch on neck" without waiting for the first sounding slot. */
  private firstLoopShape(): ChordShape | null {
    for (const bar of this.progression) {
      for (const s of bar) {
        const parsed = s.chordSymbol ? parseChord(s.chordSymbol) : null;
        if (!parsed) continue;
        const shapes = this.gen().shapesFor(parsed[0], parsed[1], this.deps.tuningProvider(), DISPLAY_FRETS);
        if (shapes.length === 0) continue;
        return shapes[s.voicingIndex] ?? shapes[0];
      }
    }
    return null;
  }

  startLoop() {
    if (this.isLooping) return;
    this.ensureNormalized();
    this.isLooping = true;
    // Seed the neck with the first chord's shape so "Watch on neck" shows notes
    // immediately, instead of a blank neck until the first slot plays.
    this.playingShape = this.firstLoopShape() ?? this.playingShape;
    this.token++;
    const token = this.token;
    this.notify();
    void (async () => {
      // Absolute onset clock (fractional ms): each slot is scheduled at nextOnsetMs,
      // so per-slot work never accumulates as drift.
      let nextOnsetMs = performance.now();
      while (this.isLooping && token === this.token) {
        for (let barIdx = 0; barIdx < this.progression.length; barIdx++) {
          if (!this.isLooping || token !== this.token) break;
          // The progression may shrink mid-pass (bar removed, shorter one loaded).
          const bar = this.progression[barIdx];
          if (!bar) break;
          this.currentBar = barIdx;
          nextOnsetMs = await this.playBar(bar, token, nextOnsetMs);
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

  /** Shapes cached per (symbol, tuning, style, span) so a slot doesn't rebuild the
   *  shape generator on every pass. Cleared whenever the tuning/style/span changes. */
  private shapeCache = new Map<string, ChordShape[]>();
  private shapeCacheTuning: Tuning | null = null;
  private shapeCacheStyle: VoicingStyle | null = null;
  private shapeCacheSpan = -1;

  private slotShape(s: LoopSlot): ChordShape | null {
    if (!s.chordSymbol) return null;
    const tuning = this.deps.tuningProvider();
    const style = this.deps.voicingStyleProvider();
    const span = this.deps.maxFretSpanProvider();
    if (tuning !== this.shapeCacheTuning || style !== this.shapeCacheStyle || span !== this.shapeCacheSpan || this.shapeCache.size > 256) {
      this.shapeCache.clear();
      this.shapeCacheTuning = tuning;
      this.shapeCacheStyle = style;
      this.shapeCacheSpan = span;
    }
    let shapes = this.shapeCache.get(s.chordSymbol);
    if (!shapes) {
      const parsed = parseChord(s.chordSymbol);
      shapes = parsed ? this.gen().shapesFor(parsed[0], parsed[1], tuning, DISPLAY_FRETS) : [];
      this.shapeCache.set(s.chordSymbol, shapes);
    }
    return shapes[s.voicingIndex] ?? shapes[0] ?? null;
  }

  /** Plays one bar whose first slot is due at startOnsetMs (performance.now ms);
   *  resolves to the onset of the slot after the bar. */
  private async playBar(bar: LoopSlot[], token: number, startOnsetMs: number): Promise<number> {
    let nextOnsetMs = startOnsetMs;
    for (let slotIdx = 0; slotIdx < bar.length; slotIdx++) {
      if (!this.isLooping || token !== this.token) return nextOnsetMs;
      // Each slot is one (whole bar / slotsPerBar) division; read live so tempo edits apply.
      const slotMs = 240000 / Math.max(this.bpm, 10) / Math.max(bar.length, 1);
      // Fell far behind (e.g. tab hidden): resync instead of firing a burst.
      const now = performance.now();
      if (now - nextOnsetMs > slotMs) nextOnsetMs = now;
      const wait = nextOnsetMs - now;
      if (wait > 0) await sleep(wait);
      if (!this.isLooping || token !== this.token) return nextOnsetMs;
      this.currentSlot = slotIdx;
      const s = bar[slotIdx];
      if (s.strum !== StrumPattern.Sustain) {
        const shape = this.slotShape(s);
        if (shape) {
          this.playingShape = shape;
          const midis = shape.midis;
          const ordered = s.strum === StrumPattern.Up ? midis.slice().reverse() : midis;
          // Sustain is already excluded by the guard above; only Down/Up/Arpeggio reach here.
          const strumDelay = s.strum === StrumPattern.Arpeggio ? Math.max(this.deps.strumProvider() * 4, 100) : this.deps.strumProvider();
          // Damp the previous slot's chord before striking this one: a sampled
          // instrument ignores the sustain argument and would ring straight through.
          // Only struck slots get here — a Sustain slot deliberately lets the chord
          // before it ring on.
          this.deps.audio.chokeChords();
          this.deps.audio.playChord(ordered, strumDelay, Math.max(Math.round(slotMs * 0.9), 150), this.deps.timbreProvider());
        }
      }
      this.notify();
      nextOnsetMs += slotMs;
    }
    return nextOnsetMs;
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

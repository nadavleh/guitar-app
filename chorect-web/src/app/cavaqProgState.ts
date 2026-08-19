// State + looper for the cavaquinho Progressions screen. Mirror of
// app/.../CavaqProgState.kt. Plays a functional CavaqSequence (e.g. the
// quadradinho I VI7 ii V7) in a chosen key on a bar loop, and exposes voice-led
// chord shapes across the neck: the FIRST chord's voicing is picked by
// positionIndex (a neck-region scroller), every later chord is the least-motion
// voicing from the previous one (pickMinMovement) — so scrolling the position
// moves the whole sequence up/down the neck coherently.
//
// Coroutine + delay() become an async loop guarded by a per-run token, matching
// earTrainingState's looper.

import {
  Tuning, PitchClass, ChordShape, ChordShapeGenerator, ChordQuality,
  ResolvedChord, resolveNamed, parseChord, spellPc, stringCount, cavaquinhoVoicingPool,
  CavaqSequence, CAVAQ_SEQUENCES, cavaqSequenceById, pickMinMovement,
} from "../theory";
import { WebAudioEngine, Timbre } from "../audio";

const DISPLAY_FRETS = 14;
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface CavaqProgDeps {
  audio: WebAudioEngine;
  tuningProvider: () => Tuning;
  sustainProvider: () => number;
  strumProvider: () => number;
  timbreProvider: () => Timbre;
  onChange: () => void;
}

export class CavaqProgState {
  sequenceId = CAVAQ_SEQUENCES[0].id;
  /** Tonic the sequence is transposed to (starts in G — the common cavaquinho key). */
  key: PitchClass = 7;
  /** Net semitones transposed from C (for the counter display). */
  transpose = 0;
  bpm = 100;
  /** Which starting voicing of the first chord (neck region) drives voice-leading. */
  positionIndex = 0;
  isPlaying = false;
  currentBar = -1;
  /** The shape currently sounding (or previewed) — drives the follow-along fretboard. */
  currentShape: ChordShape | null = null;

  private loopToken = 0;
  private gen = new ChordShapeGenerator();

  constructor(private deps: CavaqProgDeps) {
    this.resetPreview();
  }

  private notify() { this.deps.onChange(); }

  get sequence(): CavaqSequence {
    return cavaqSequenceById(this.sequenceId) ?? CAVAQ_SEQUENCES[0];
  }

  /** The sequence realised in the current key. */
  get resolved(): ResolvedChord[] {
    return resolveNamed(this.sequence.prog, this.key);
  }

  private minFret(sh: ChordShape): number {
    const fs = sh.frets.filter((f): f is number => f !== null);
    return fs.length ? Math.min(...fs) : 0;
  }

  /** All candidate voicings for a chord. On the 4-string cavaquinho this is the
   *  comprehensive pool (complete + rootless + no-5th shell) so the voice-leader
   *  can pick smooth rootless/shell grips; other tunings use the CAGED generator. */
  private voicings(root: PitchClass, q: ChordQuality, tuning: Tuning): ChordShape[] {
    return stringCount(tuning) === 4
      ? cavaquinhoVoicingPool(root, q, tuning, DISPLAY_FRETS)
      : this.gen.shapesFor(root, q, tuning, DISPLAY_FRETS);
  }

  /** Candidate starting voicings for the first chord, sorted low → high. */
  private firstShapes(): ChordShape[] {
    const first = this.resolved[0];
    if (!first) return [];
    const parsed = parseChord(first.symbol);
    if (!parsed) return [];
    const [root, q] = parsed;
    return this.voicings(root, q, this.deps.tuningProvider())
      .slice()
      .sort((a, b) => this.minFret(a) - this.minFret(b));
  }

  /** How many neck-region positions the current sequence offers (>= 1 when playable). */
  get positionCount(): number {
    return Math.max(this.firstShapes().length, 1);
  }

  /** Voice-led shapes for the whole sequence, from the chosen positionIndex. Entries
   *  are null for any (rare) chord with no playable cavaquinho voicing. */
  shapes(): (ChordShape | null)[] {
    const tuning = this.deps.tuningProvider();
    const starts = this.firstShapes();
    let prev: ChordShape | null = null;
    return this.resolved.map((rc, i) => {
      const parsed = parseChord(rc.symbol);
      if (!parsed) return null;
      const [root, q] = parsed;
      let chosen: ChordShape | null;
      if (i === 0) {
        chosen = starts.length === 0 ? null : starts[Math.min(Math.max(this.positionIndex, 0), starts.length - 1)];
      } else {
        const shs = this.voicings(root, q, tuning);
        if (shs.length === 0) chosen = null;
        else if (prev === null) chosen = shs.slice().sort((a, b) => this.minFret(a) - this.minFret(b))[0];
        else chosen = shs[pickMinMovement(prev, shs)];
      }
      if (chosen !== null) prev = chosen;
      return chosen;
    });
  }

  setSequence(id: string) {
    if (id === this.sequenceId) return;
    this.sequenceId = id;
    this.positionIndex = 0;
    this.resetPreview();
    this.notify();
  }

  chooseKey(pc: PitchClass) {
    this.transpose += (((pc - this.key) % 12) + 12) % 12;
    this.key = pc;
    this.resetPreview();
    this.notify();
  }

  shiftKey(semitones: number) {
    this.key = ((((this.key + semitones) % 12) + 12) % 12) as PitchClass;
    this.transpose += semitones;
    this.resetPreview();
    this.notify();
  }

  setPosition(i: number) {
    this.positionIndex = Math.min(Math.max(i, 0), this.positionCount - 1);
    this.resetPreview();
    this.notify();
  }

  nudgePosition(delta: number) { this.setPosition(this.positionIndex + delta); }

  changeBpm(v: number) { this.bpm = Math.min(Math.max(Math.round(v), 30), 240); this.notify(); }

  /** Refresh the idle fretboard preview (first chord's chosen shape) when not looping. */
  private resetPreview() {
    if (!this.isPlaying) {
      this.currentBar = -1;
      this.currentShape = this.shapes()[0] ?? null;
    }
  }

  toggle() { if (this.isPlaying) this.stop(); else this.play(); }

  play() {
    if (this.isPlaying) return;
    this.isPlaying = true;
    this.loopToken++;
    const token = this.loopToken;
    this.notify();
    void (async () => {
      const beatMs = 60000 / Math.max(this.bpm, 10);
      const barMs = beatMs * 4;
      const sustain = Math.max(Math.floor(barMs * 0.9), 200);
      while (this.isPlaying && token === this.loopToken) {
        const shs = this.shapes();
        for (let i = 0; i < shs.length; i++) {
          if (!this.isPlaying || token !== this.loopToken) break;
          this.currentBar = i;
          const sh = shs[i];
          this.currentShape = sh;
          const midis = sh ? sh.notes.filter((n) => n !== null).map((n) => n!.midi) : [];
          if (midis.length) {
            this.deps.audio.chokeChords();   // one bar's chord must not ring into the next
            this.deps.audio.playChord(midis, this.deps.strumProvider(), sustain, this.deps.timbreProvider());
          }
          this.notify();
          await sleep(barMs);
        }
      }
    })();
  }

  stop() {
    this.isPlaying = false;
    this.loopToken++;
    this.currentBar = -1;
    this.deps.audio.stop();
    this.currentShape = this.shapes()[0] ?? null;
    this.notify();
  }

  /** Play a single bar's chord once (tapping a chord chip to hear it). */
  playBar(i: number) {
    const sh = this.shapes()[i];
    if (!sh) return;
    this.currentBar = i;
    this.currentShape = sh;
    const midis = sh.notes.filter((n) => n !== null).map((n) => n!.midi);
    if (midis.length) {
      this.deps.audio.chokeChords();
      this.deps.audio.playChord(midis, this.deps.strumProvider(), this.deps.sustainProvider(), this.deps.timbreProvider());
    }
    this.notify();
  }

  /** Label like "C" / "Ab" for the current key. */
  keyLabel(): string { return spellPc(this.key); }
}

// Playback for the Rhythmic Units screen — mirror of app/.../RhythmUnitState.kt.
// Loops one selected one-beat unit, clicking a synthesized woodblock-like tick on
// each note onset (downbeat accented), at a controllable BPM. Schedules on the
// AudioContext clock (playSamples(…, whenSec)) so timing never drifts; a token
// counter replaces Kotlin's Job cancellation.

import { WebAudioEngine } from "../audio";
import { RhythmUnit, rhythmUnitById, clickFractions } from "../theory";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface RhythmUnitDeps {
  audio: WebAudioEngine;
  onChange: () => void;
}

export class RhythmUnitState {
  selectedId: string | null = null;
  isPlaying = false;
  bpm = 80;

  private token = 0;
  private readonly click: Float32Array;
  private readonly accentClick: Float32Array;

  constructor(private deps: RhythmUnitDeps) {
    this.click = synthClick(2000, 45);
    this.accentClick = synthClick(2800, 45);
  }

  get selected(): RhythmUnit | undefined {
    return this.selectedId ? rhythmUnitById(this.selectedId) : undefined;
  }

  /** Tap a card: switch to it and (re)start; tapping the playing one stops. */
  select(id: string): void {
    if (this.selectedId === id && this.isPlaying) { this.stop(); return; }
    this.selectedId = id;
    this.stopLoop();
    this.start();
  }

  toggle(): void {
    if (this.isPlaying) this.stop();
    else if (this.selectedId) this.start();
  }

  setBpm(v: number): void {
    this.bpm = Math.round(Math.min(Math.max(v, 10), 300));   // loop re-reads bpm each beat
    this.deps.onChange();
  }

  start(): void {
    const u = this.selected;
    if (!u || this.isPlaying) return;
    this.isPlaying = true;
    this.deps.onChange();
    void this.loop(u, ++this.token);
  }

  stop(): void {
    this.stopLoop();
    this.isPlaying = false;
    this.deps.onChange();
  }

  private stopLoop(): void { this.token++; }

  private async loop(u: RhythmUnit, token: number): Promise<void> {
    const fractions = clickFractions(u);   // rests produce no click
    let nextBeat = this.deps.audio.now();
    while (this.isPlaying && token === this.token) {
      const beatSec = 60 / this.bpm;
      const base = Math.max(nextBeat - this.deps.audio.now(), 0);
      fractions.forEach((f) => {
        const whenSec = this.deps.audio.now() + base + f * beatSec;
        // Accent the downbeat click (a note on the beat), not merely the first click.
        if (f === 0) this.deps.audio.playSamples(this.accentClick, 1.0, whenSec);
        else this.deps.audio.playSamples(this.click, 0.72, whenSec);
      });
      nextBeat += beatSec;
      await sleep((base + beatSec) * 1000);
    }
  }
}

function synthClick(freqHz: number, ms: number): Float32Array {
  const sr = 44100;
  const n = Math.floor((sr * ms) / 1000);
  const buf = new Float32Array(n);
  const w = (2 * Math.PI * freqHz) / sr;
  for (let i = 0; i < n; i++) {
    const env = Math.exp((-6 * i) / n);   // fast percussive decay
    buf[i] = Math.sin(w * i) * env * 0.7;
  }
  return buf;
}

// Standalone metronome: clicks [beatsPerBar] wood clicks per bar at [bpm], the
// downbeat ("1") accented with the higher wood click. Mirror of MetronomeState.kt.
// Scheduling is driven off the AudioContext clock so it never drifts; a token
// counter cancels the async loop (same pattern as RhythmUnitState).

import { WebAudioEngine } from "../audio";
import { synthClick, ACCENT_CLICK_HZ, BEAT_CLICK_HZ } from "./woodClick";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface MetronomeDeps {
  audio: WebAudioEngine;
  onChange: () => void;
}

/** Time signatures offered in the metronome (beatsPerBar / beatUnit). The click
 *  cadence uses beatsPerBar; beatUnit is shown for reading (e.g. 6/8). */
export const METRONOME_TIME_SIGNATURES: [number, number][] = [
  [2, 4], [3, 4], [4, 4], [5, 4], [6, 8], [7, 8], [3, 8], [12, 8], [2, 2],
];

export class MetronomeState {
  bpm = 100;
  beatsPerBar = 4;
  beatUnit = 4;
  isPlaying = false;
  /** Beat currently sounding (0-based), or -1 when stopped. Drives the beat dots. */
  currentBeat = -1;

  private token = 0;
  private readonly click: Float32Array;
  private readonly accent: Float32Array;

  constructor(private deps: MetronomeDeps) {
    this.click = synthClick(BEAT_CLICK_HZ, 45);
    this.accent = synthClick(ACCENT_CLICK_HZ, 45);
  }

  toggle(): void { if (this.isPlaying) this.stop(); else this.start(); }

  setBpm(v: number): void {
    this.bpm = Math.round(Math.min(Math.max(v, 10), 300));   // loop re-reads bpm each beat
    this.deps.onChange();
  }

  setTimeSignature(beatsPerBar: number, beatUnit: number): void {
    this.beatsPerBar = Math.min(Math.max(beatsPerBar, 1), 12);
    this.beatUnit = beatUnit;
    this.deps.onChange();
  }

  // Tap-tempo: average the intervals of the recent taps (2 s window, last 6).
  private tapTimes: number[] = [];
  tapTempo(nowMs: number = Date.now()): void {
    if (this.tapTimes.length && nowMs - this.tapTimes[this.tapTimes.length - 1] > 2000) this.tapTimes = [];
    this.tapTimes.push(nowMs);
    while (this.tapTimes.length > 6) this.tapTimes.shift();
    if (this.tapTimes.length >= 2) {
      const avg = (this.tapTimes[this.tapTimes.length - 1] - this.tapTimes[0]) / (this.tapTimes.length - 1);
      this.bpm = Math.min(Math.max(Math.round(60000 / avg), 10), 300);
    }
    this.deps.onChange();
  }

  start(): void {
    if (this.isPlaying) return;
    this.isPlaying = true;
    this.deps.onChange();
    void this.loop(++this.token);
  }

  stop(): void {
    this.token++;
    this.isPlaying = false;
    this.currentBeat = -1;
    this.deps.onChange();
  }

  release(): void { this.stop(); }

  private async loop(token: number): Promise<void> {
    let nextBeat = this.deps.audio.now();
    let beat = 0;
    while (this.isPlaying && token === this.token) {
      const wait = Math.max(nextBeat - this.deps.audio.now(), 0);
      await sleep(wait * 1000);
      if (!this.isPlaying || token !== this.token) break;
      this.currentBeat = beat;
      const accented = beat === 0;
      this.deps.audio.playSamples(accented ? this.accent : this.click, accented ? 1.0 : 0.72, this.deps.audio.now());
      this.deps.onChange();
      nextBeat += 60 / this.bpm;
      beat = (beat + 1) % this.beatsPerBar;
    }
  }
}

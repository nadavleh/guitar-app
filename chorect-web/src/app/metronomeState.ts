// Standalone metronome: clicks [beatsPerBar] wood clicks per bar at [bpm], the
// downbeat ("1") accented with the higher wood click. Mirror of MetronomeState.kt.
// Scheduling is driven off the AudioContext clock so it never drifts; a token
// counter cancels the async loop (same pattern as RhythmUnitState).

import { WebAudioEngine } from "../audio";
import { clickAt, ACCENT_CLICK_HZ, BEAT_CLICK_HZ } from "./woodClick";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
/** How far ahead of each beat its click is queued (≥ worst-case timer jitter). */
const LOOKAHEAD_SEC = 0.1;

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

  constructor(private deps: MetronomeDeps) {}

  /** Clicks are built lazily at the ENGINE's rate — see clickAt(). */
  private get click(): Float32Array { return clickAt(BEAT_CLICK_HZ, 45, this.deps.audio.sampleRate); }
  private get accent(): Float32Array { return clickAt(ACCENT_CLICK_HZ, 45, this.deps.audio.sampleRate); }

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

  private taps = new TapTempo();
  tapTempo(nowMs: number = Date.now()): void {
    const bpm = this.taps.tap(nowMs);
    if (bpm !== null) this.bpm = bpm;
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

  /** Lookahead scheduler: wake LOOKAHEAD_SEC BEFORE each beat and queue its click at
   *  the beat's absolute AudioContext time, so setTimeout jitter never moves a click.
   *  The beat dot updates at the onset itself. */
  private async loop(token: number): Promise<void> {
    const audio = this.deps.audio;
    const live = () => this.isPlaying && token === this.token;
    let nextBeat = audio.now();
    let beat = 0;
    while (live()) {
      const beatSec = 60 / this.bpm;
      // Fell more than a beat behind (tab throttled): resync, don't burst.
      if (audio.now() - nextBeat > beatSec) nextBeat = audio.now();
      const accented = beat === 0;
      audio.playSamples(accented ? this.accent : this.click, accented ? 1.0 : 0.72, nextBeat);
      await sleep(Math.max(nextBeat - audio.now(), 0) * 1000);
      if (!live()) break;
      this.currentBeat = beat;
      this.deps.onChange();
      nextBeat += beatSec;
      beat = (beat + 1) % this.beatsPerBar;
      await sleep(Math.max(nextBeat - LOOKAHEAD_SEC - audio.now(), 0) * 1000);
    }
  }
}

/** Tap-tempo: average the intervals of the recent taps (2 s window, last 6). Shared by
 *  the metronome and the samba looper (mirrors MetronomeState.kt TapTempo). */
export class TapTempo {
  private times: number[] = [];

  /** Record a tap; returns the tapped BPM (10..300) once two taps are in, else null. */
  tap(nowMs: number): number | null {
    if (this.times.length && nowMs - this.times[this.times.length - 1] > 2000) this.times = [];
    this.times.push(nowMs);
    while (this.times.length > 6) this.times.shift();
    if (this.times.length < 2) return null;
    const avg = (this.times[this.times.length - 1] - this.times[0]) / (this.times.length - 1);
    return Math.min(Math.max(Math.round(60000 / avg), 10), 300);
  }
}

// Multi-bar rhythmic phrase generator + looper. Mirror of app/.../RhythmPhraseState.kt.
// Loops a generated phrase, clicking a synthesized woodblock on each onset (bar
// downbeats accented), and publishes currentSlot for the notation + grid playheads.

import { WebAudioEngine } from "../audio";
import {
  RhythmPhrase, generatePhrase, phraseOnsets, phraseTotalSlots, SLOTS_PER_BEAT,
  PHRASE_MIN_BARS, PHRASE_MAX_BARS, PHRASE_TIME_SIGNATURES,
} from "../theory";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface RhythmPhraseDeps {
  audio: WebAudioEngine;
  onChange: () => void;
}

export class RhythmPhraseState {
  bars = 2;
  beatsPerBar = 2;
  bpm = 30;
  phrase: RhythmPhrase | null = null;
  isPlaying = false;
  currentSlot = -1;

  /** Background metronome: soft, LOWER-pitched clicks on the beats (higher of
   *  the two on bar downbeats) so it reads under the phrase's woodblock. */
  metronomeOn = false;
  toggleMetronome(): void { this.metronomeOn = !this.metronomeOn; this.deps.onChange(); }

  private token = 0;
  private onsetAccent = new Map<number, boolean>();
  private readonly click: Float32Array;
  private readonly accentClick: Float32Array;
  private readonly mClick: Float32Array;
  private readonly mAccent: Float32Array;

  constructor(private deps: RhythmPhraseDeps) {
    this.click = synthClick(2000, 45);
    this.accentClick = synthClick(2800, 45);
    this.mClick = synthClick(1000, 45);
    this.mAccent = synthClick(1400, 45);
  }

  generate(): void {
    this.stop();
    const p = generatePhrase(this.bars, this.beatsPerBar);
    this.phrase = p;
    this.onsetAccent = new Map(phraseOnsets(p).map((o) => [o.slot, o.accent]));
    this.deps.onChange();
  }

  changeBars(v: number): void {
    this.bars = Math.min(Math.max(v, PHRASE_MIN_BARS), PHRASE_MAX_BARS);
    this.generate();
  }
  changeBeatsPerBar(v: number): void {
    if (PHRASE_TIME_SIGNATURES.includes(v)) { this.beatsPerBar = v; this.generate(); }
  }
  setBpm(v: number): void { this.bpm = Math.round(Math.min(Math.max(v, 10), 300)); this.deps.onChange(); }

  toggle(): void { if (this.isPlaying) this.stop(); else this.start(); }

  start(): void {
    let p = this.phrase;
    if (!p) { this.generate(); p = this.phrase; }
    if (!p || this.isPlaying) return;
    this.isPlaying = true;
    this.deps.onChange();
    void this.loop(p, ++this.token);
  }

  stop(): void {
    this.token++;
    this.isPlaying = false;
    this.currentSlot = -1;
    this.deps.onChange();
  }

  private scheduleClickAt(slot: number, whenSec: number): void {
    const accent = this.onsetAccent.get(slot);
    if (accent !== undefined) {
      this.deps.audio.playSamples(accent ? this.accentClick : this.click, accent ? 1.0 : 0.72, whenSec);
    }
    // Background metronome on the beats (soft; bar downbeats slightly higher).
    if (this.metronomeOn && this.phrase && slot % SLOTS_PER_BEAT === 0) {
      const bar = slot % (this.phrase.beatsPerBar * SLOTS_PER_BEAT) === 0;
      this.deps.audio.playSamples(bar ? this.mAccent : this.mClick, bar ? 0.55 : 0.4, whenSec);
    }
  }

  private async loop(p: RhythmPhrase, token: number): Promise<void> {
    const total = phraseTotalSlots(p);
    let nextOnset = this.deps.audio.now();
    let first = true;
    while (this.isPlaying && token === this.token) {
      for (let slot = 0; slot < total; slot++) {
        if (!this.isPlaying || token !== this.token) break;
        this.currentSlot = slot;
        this.deps.onChange();
        if (first) { this.scheduleClickAt(slot, this.deps.audio.now()); first = false; }
        const slotSec = (60 / this.bpm) / SLOTS_PER_BEAT;
        nextOnset += slotSec;
        this.scheduleClickAt((slot + 1) % total, nextOnset);
        await sleep(Math.max((nextOnset - this.deps.audio.now()) * 1000, 0));
      }
    }
  }
}

function synthClick(freqHz: number, ms: number): Float32Array {
  const sr = 44100;
  const n = Math.floor((sr * ms) / 1000);
  const buf = new Float32Array(n);
  const w = (2 * Math.PI * freqHz) / sr;
  for (let i = 0; i < n; i++) {
    const env = Math.exp((-6 * i) / n);
    buf[i] = Math.sin(w * i) * env * 0.7;
  }
  return buf;
}

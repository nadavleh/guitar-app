// Tuner pipeline state, ported from app/.../TunerState.kt. Owns mic capture +
// YIN detector + smoothing + the tap-to-lock behaviour, and calls onUpdate when
// the reading changes so the dial can redraw.

import { MicInput, PitchDetector, analyzePitch, midiToFreqA4 } from "../audio";

const SMOOTH_N = 4;

export class TunerState {
  freqHz: number | null = null;
  midi: number | null = null;
  cents: number | null = null;
  capturing = false;
  lockedMidi: number | null = null;

  private mic: MicInput;
  private detector = new PitchDetector(this.ctx.sampleRate);
  private smoothing: [number, number][] = [];
  private lockedUntilMs = 0;

  constructor(private ctx: AudioContext, private a4Provider: () => number, private onUpdate: () => void) {
    this.mic = new MicInput(ctx);
  }

  async start(): Promise<boolean> {
    if (this.capturing) return true;
    const ok = await this.mic.start((samples) => this.onSamples(samples));
    this.capturing = ok;
    this.onUpdate();
    return ok;
  }

  stop(): void {
    if (!this.capturing) return;
    this.mic.stop();
    this.capturing = false;
    this.freqHz = null;
    this.midi = null;
    this.cents = null;
    this.smoothing = [];
    this.onUpdate();
  }

  /** Force the dial to read "spot on" for [midi] for [holdMs] millis. */
  lockTo(midi: number, holdMs = 1500): void {
    this.lockedMidi = midi;
    this.lockedUntilMs = Date.now() + holdMs;
    this.midi = midi;
    this.cents = 0;
    this.freqHz = midiToFreqA4(midi, this.a4Provider());
    this.onUpdate();
  }

  private onSamples(samples: Float32Array): void {
    let energy = 0;
    for (const s of samples) energy += s * s;
    const rms = Math.sqrt(energy / samples.length);
    if (rms < 0.005) {
      if (Date.now() >= this.lockedUntilMs) {
        this.freqHz = null;
        this.midi = null;
        this.cents = null;
        this.smoothing = [];
        this.onUpdate();
      }
      return;
    }
    if (Date.now() < this.lockedUntilMs) return;
    if (this.lockedMidi !== null) this.lockedMidi = null;

    const freq = this.detector.detect(samples);
    if (freq === null) return;
    const est = analyzePitch(freq, this.a4Provider());

    this.smoothing.push([est.midi, est.cents]);
    if (this.smoothing.length > SMOOTH_N) this.smoothing.shift();
    const counts = new Map<number, number>();
    for (const [m] of this.smoothing) counts.set(m, (counts.get(m) ?? 0) + 1);
    let dominant = est.midi;
    let best = 0;
    for (const [m, c] of counts) if (c > best) { best = c; dominant = m; }
    const matching = this.smoothing.filter(([m]) => m === dominant).map(([, c]) => c);
    const centsAvg = matching.reduce((a, b) => a + b, 0) / matching.length;

    this.freqHz = freq;
    this.midi = dominant;
    this.cents = centsAvg;
    this.onUpdate();
  }
}

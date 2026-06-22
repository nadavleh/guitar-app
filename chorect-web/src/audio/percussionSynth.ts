// Synthesized samba percussion voices, ported from audio/.../PercussionSynth.kt.
// Each (instrument, voiceIndex) renders a one-shot mono Float32Array in [-1, 1].
// Deterministic noise via a seeded RNG (mulberry32), mirroring Kotlin's Random(seed).

import { PercussionInstrument } from "../theory";

function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const TAU = Math.PI * 2;

export class PercussionSynth {
  constructor(public readonly sampleRate = 44100) {}

  synthesize(instrument: PercussionInstrument, voiceIndex: number): Float32Array {
    switch (instrument) {
      case PercussionInstrument.Surdo:
        if (voiceIndex === 0) return this.surdo(true);
        if (voiceIndex === 1) return this.surdo(false);
        return this.tonedTap(165, 0.055, 75, 0.30);
      case PercussionInstrument.Tamborim:
        if (voiceIndex === 0) return this.tamborimClack(false);
        if (voiceIndex === 1) return this.tamborimClack(true);
        return this.noiseDrum(0.05, 95, 0.30, true, 0.30);
      case PercussionInstrument.Pandeiro:
        if (voiceIndex === 0) return this.tonedTap(150, 0.22, 13, 0.62);
        if (voiceIndex === 1) return this.tonedTap(155, 0.08, 42, 0.55);
        if (voiceIndex === 2) return this.noiseDrum(0.08, 60, 0.50, true, 0.72);
        if (voiceIndex === 3) return this.jingle(false);
        return this.jingle(true);
      case PercussionInstrument.Agogo:
        return voiceIndex === 0 ? this.bell(590) : this.bell(740);
    }
  }

  private surdo(open: boolean): Float32Array {
    const durSec = open ? 0.50 : 0.12;
    const decay = open ? 6.0 : 34.0;
    const freq = open ? 62.0 : 66.0;
    const n = Math.max(Math.floor(this.sampleRate * durSec), 1);
    const out = new Float32Array(n);
    const rng = mulberry32(101);
    for (let i = 0; i < n; i++) {
      const t = i / this.sampleRate;
      const e = Math.exp(-decay * t);
      const body = Math.sin(TAU * freq * t) + 0.4 * Math.sin(TAU * freq * 2 * t);
      const transient = t < 0.006 ? (rng() * 2 - 1) * Math.exp(-t * 600) : 0;
      out[i] = (body * 0.55 + transient * 0.6) * e * 0.85;
    }
    return this.fadeOut(out);
  }

  private tonedTap(freq: number, durSec: number, decay: number, amp: number): Float32Array {
    const n = Math.max(Math.floor(this.sampleRate * durSec), 1);
    const out = new Float32Array(n);
    const rng = mulberry32(404);
    for (let i = 0; i < n; i++) {
      const t = i / this.sampleRate;
      const e = Math.exp(-decay * t);
      const body = Math.sin(TAU * freq * t) + 0.5 * Math.sin(TAU * freq * 2 * t);
      const transient = t < 0.005 ? (rng() * 2 - 1) * Math.exp(-t * 500) : 0;
      out[i] = (body * 0.6 + transient * 0.5) * e * amp;
    }
    return this.fadeOut(out);
  }

  private tamborimClack(muted: boolean): Float32Array {
    const durSec = muted ? 0.045 : 0.09;
    const decay = muted ? 130.0 : 70.0;
    const amp = muted ? 0.5 : 0.78;
    const n = Math.max(Math.floor(this.sampleRate * durSec), 1);
    const out = new Float32Array(n);
    const rng = mulberry32(202);
    let lp = 0;
    for (let i = 0; i < n; i++) {
      const t = i / this.sampleRate;
      const white = rng() * 2 - 1;
      lp += 0.6 * (white - lp);
      const hpNoise = white - lp;
      const tone = Math.sin(TAU * 1500 * t) * Math.exp(-t * 220);
      const e = Math.exp(-decay * t);
      out[i] = (hpNoise * 0.7 + tone * 0.28) * e * amp;
    }
    return this.fadeOut(out);
  }

  private jingle(hi: boolean): Float32Array {
    const durSec = hi ? 0.16 : 0.20;
    const decay = hi ? 26.0 : 20.0;
    const base = hi ? 3400.0 : 2700.0;
    const n = Math.max(Math.floor(this.sampleRate * durSec), 1);
    const out = new Float32Array(n);
    const rng = mulberry32(303);
    let lp = 0;
    for (let i = 0; i < n; i++) {
      const t = i / this.sampleRate;
      const white = rng() * 2 - 1;
      lp += 0.7 * (white - lp);
      const hpNoise = white - lp;
      const metal = Math.sin(TAU * base * t) + 0.6 * Math.sin(TAU * base * 1.51 * t) + 0.4 * Math.sin(TAU * base * 2.31 * t);
      const e = Math.exp(-decay * t);
      out[i] = (hpNoise * 0.5 + metal * 0.16) * e * 0.5;
    }
    return this.fadeOut(out);
  }

  private bell(freq: number): Float32Array {
    const durSec = 0.25, decay = 11.0;
    const n = Math.max(Math.floor(this.sampleRate * durSec), 1);
    const out = new Float32Array(n);
    for (let i = 0; i < n; i++) {
      const t = i / this.sampleRate;
      const e = Math.exp(-decay * t);
      const s = Math.sin(TAU * freq * t) + 0.25 * Math.sin(TAU * freq * 2.76 * t);
      out[i] = s * e * 0.6;
    }
    return this.fadeOut(out);
  }

  private noiseDrum(durSec: number, decay: number, lpAlpha: number, hp: boolean, amp: number, seed = 202): Float32Array {
    const n = Math.max(Math.floor(this.sampleRate * durSec), 1);
    const out = new Float32Array(n);
    const rng = mulberry32(seed);
    let lp = 0;
    for (let i = 0; i < n; i++) {
      const t = i / this.sampleRate;
      const white = rng() * 2 - 1;
      lp += lpAlpha * (white - lp);
      const sample = hp ? white - lp : lp;
      const e = Math.exp(-decay * t);
      out[i] = sample * e * amp;
    }
    return this.fadeOut(out);
  }

  private fadeOut(buf: Float32Array): Float32Array {
    const fade = Math.min(Math.floor(this.sampleRate * 0.003), buf.length);
    for (let i = 0; i < fade; i++) buf[buf.length - 1 - i] *= i / fade;
    return buf;
  }
}

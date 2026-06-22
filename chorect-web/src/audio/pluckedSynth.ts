// Karplus-Strong plucked-string synthesis, ported from audio/.../PluckedSynth.kt.
//
// Pure math, no Web Audio dependency — it just renders a Float32Array of samples.
// The Kotlin version seeds kotlin.random.Random for reproducible per-voice noise;
// we use a small seeded mulberry32 RNG to keep chord voices decorrelated the same way.

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

export function midiToFreq(midiNote: number): number {
  return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
}

export class PluckedSynth {
  constructor(public readonly sampleRate = 48000) {}

  synthesize(midiNote: number, durationSec: number, seed = 1, damping = 0.997, amplitude = 0.6): Float32Array {
    return this.synthesizeFrequency(midiToFreq(midiNote), durationSec, seed, damping, amplitude);
  }

  synthesizeFrequency(freqHz: number, durationSec: number, seed = 1, damping = 0.997, amplitude = 0.6): Float32Array {
    if (freqHz <= 0) throw new Error(`freq must be > 0, got ${freqHz}`);
    if (durationSec <= 0) throw new Error(`duration must be positive, got ${durationSec}`);

    // Round (not truncate) the delay length so chord tones stay in tune.
    const n = Math.max(Math.round(this.sampleRate / freqHz), 2);

    // Excitation: shaped noise + pluck-position comb + one-pole lowpass.
    const rng = mulberry32(seed);
    const raw = new Float64Array(n);
    for (let i = 0; i < n; i++) raw[i] = rng() * 2.0 - 1.0;
    const pluck = Math.max(Math.floor(n / 4), 1);
    const buf = new Float64Array(n);
    let warm = 0.0;
    const warmCoef = 0.55;
    let maxAbs = 1e-9;
    for (let i = 0; i < n; i++) {
      const combed = raw[i] - 0.9 * raw[(i - pluck + n) % n];
      warm = warmCoef * combed + (1.0 - warmCoef) * warm;
      buf[i] = warm;
      const a = Math.abs(warm);
      if (a > maxAbs) maxAbs = a;
    }
    const norm = 1.0 / maxAbs;
    for (let i = 0; i < n; i++) buf[i] *= norm;

    // Karplus-Strong delay loop.
    const numSamples = Math.max(Math.floor(this.sampleRate * durationSec), 1);
    const ks = new Float64Array(numSamples);
    let idx = 0;
    for (let i = 0; i < numSamples; i++) {
      const cur = buf[idx];
      const nxt = buf[(idx + 1) % n];
      ks[i] = cur;
      buf[idx] = damping * 0.5 * (cur + nxt);
      idx = (idx + 1) % n;
    }

    // Body: blend in a low-passed copy for more bottom end.
    const output = new Float32Array(numSamples);
    let body = 0.0;
    const bodyCoef = 0.12;
    const bodyMix = 0.32;
    for (let i = 0; i < numSamples; i++) {
      body = bodyCoef * ks[i] + (1.0 - bodyCoef) * body;
      const mix = (1.0 - bodyMix) * ks[i] + bodyMix * body;
      output[i] = amplitude * mix;
    }

    // 50ms linear fade-out so the last sample ends at 0 (no click).
    const fadeSamples = Math.min(Math.floor(this.sampleRate * 0.05), numSamples);
    for (let i = 0; i < fadeSamples; i++) {
      const mult = i / fadeSamples;
      output[numSamples - 1 - i] *= mult;
    }
    return output;
  }

  synthesizeChord(midiNotes: number[], sustainSec: number, strumDelaySamples: number, seedBase = 1, damping = 0.997, amplitude = 0.6): Float32Array {
    const voices = midiNotes.filter((m) => m >= 0 && m <= 127);
    if (voices.length === 0) return new Float32Array(0);
    const perVoiceLen = Math.max(Math.floor(this.sampleRate * sustainSec), 1);
    const totalLen = perVoiceLen + (voices.length - 1) * strumDelaySamples;
    const mix = new Float32Array(totalLen);
    const scale = 1.0 / Math.sqrt(voices.length);
    voices.forEach((midi, i) => {
      const voice = this.synthesize(midi, sustainSec, seedBase + i, damping, amplitude);
      const offset = i * strumDelaySamples;
      const end = Math.min(offset + voice.length, totalLen);
      for (let j = 0; j < end - offset; j++) mix[offset + j] += voice[j] * scale;
    });
    return mix;
  }
}

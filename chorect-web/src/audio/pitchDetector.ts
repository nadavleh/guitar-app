// YIN pitch detection (de Cheveigné & Kawahara, 2002), ported from
// audio/.../PitchDetector.kt. Returns the fundamental in Hz, or null.

export class PitchDetector {
  constructor(
    public readonly sampleRate = 44100,
    public readonly threshold = 0.15,
    public readonly minFreq = 50,
    public readonly maxFreq = 1500,
  ) {}

  detect(samples: Float32Array): number | null {
    if (samples.length < 32) return null;
    const tauMin = Math.max(Math.floor(this.sampleRate / this.maxFreq), 2);
    const tauMax = Math.min(Math.floor(this.sampleRate / this.minFreq), Math.floor(samples.length / 2) - 1);
    if (tauMin >= tauMax) return null;

    // Step 1: difference function.
    const diff = new Float32Array(tauMax + 1);
    const W = samples.length - tauMax;
    for (let tau = 1; tau <= tauMax; tau++) {
      let sum = 0;
      for (let j = 0; j < W; j++) {
        const delta = samples[j] - samples[j + tau];
        sum += delta * delta;
      }
      diff[tau] = sum;
    }

    // Step 2: cumulative mean normalized difference.
    const cmnd = new Float32Array(tauMax + 1);
    cmnd[0] = 1;
    let running = 0;
    for (let tau = 1; tau <= tauMax; tau++) {
      running += diff[tau];
      cmnd[tau] = running === 0 ? 1 : (diff[tau] * tau) / running;
    }

    // Step 3: absolute threshold, then walk down to the local minimum.
    let pitchTau = -1;
    let tau = tauMin;
    while (tau < tauMax) {
      if (cmnd[tau] < this.threshold) {
        while (tau + 1 < tauMax && cmnd[tau + 1] < cmnd[tau]) tau++;
        pitchTau = tau;
        break;
      }
      tau++;
    }
    if (pitchTau === -1) {
      let minIdx = tauMin;
      let minVal = cmnd[tauMin];
      for (let t = tauMin + 1; t <= tauMax; t++) {
        if (cmnd[t] < minVal) {
          minVal = cmnd[t];
          minIdx = t;
        }
      }
      if (minVal > 0.5) return null;
      pitchTau = minIdx;
    }

    // Step 4: parabolic interpolation.
    const refined = this.parabolicInterpolation(cmnd, pitchTau);
    return this.sampleRate / refined;
  }

  private parabolicInterpolation(d: Float32Array, t: number): number {
    if (t < 1 || t >= d.length - 1) return t;
    const s0 = d[t - 1];
    const s1 = d[t];
    const s2 = d[t + 1];
    const denom = 2 * s1 - s0 - s2;
    return denom === 0 ? t : t + (0.5 * (s2 - s0)) / denom;
  }
}

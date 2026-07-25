// Offline EQ for pandeiro one-shots: a high-pass to clear the surdo's low end
// (so the surdo–pandeiro pair stops fighting for the same low band) plus a
// gentle high-shelf lift to brighten. Applied once per buffer and cached.
// RBJ biquad cookbook coefficients, direct-form-I.

const HP_HZ = 160;      // high-pass corner — keeps more pandeiro bass, still above the surdo's fundamental
const HP_Q = 0.707;     // Butterworth (no resonant bump at the corner)
const SHELF_HZ = 3000;  // brighten everything above ~3 kHz
const SHELF_DB = 4;     // gentle high lift

interface Biquad { b0: number; b1: number; b2: number; a1: number; a2: number; }

function apply(x: Float32Array, c: Biquad): Float32Array {
  const y = new Float32Array(x.length);
  let x1 = 0, x2 = 0, y1 = 0, y2 = 0;
  for (let i = 0; i < x.length; i++) {
    const x0 = x[i];
    const y0 = c.b0 * x0 + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2;
    y[i] = y0;
    x2 = x1; x1 = x0; y2 = y1; y1 = y0;
  }
  return y;
}

function highpass(f0: number, q: number, fs: number): Biquad {
  const w0 = (2 * Math.PI * f0) / fs, c = Math.cos(w0), s = Math.sin(w0), alpha = s / (2 * q);
  const a0 = 1 + alpha;
  return { b0: ((1 + c) / 2) / a0, b1: -(1 + c) / a0, b2: ((1 + c) / 2) / a0, a1: (-2 * c) / a0, a2: (1 - alpha) / a0 };
}

function highshelf(f0: number, dB: number, fs: number): Biquad {
  const A = Math.pow(10, dB / 40);
  const w0 = (2 * Math.PI * f0) / fs, c = Math.cos(w0), s = Math.sin(w0);
  const alpha = (s / 2) * Math.SQRT2;            // shelf slope S = 1
  const beta = 2 * Math.sqrt(A) * alpha;
  const a0 = (A + 1) - (A - 1) * c + beta;
  return {
    b0: (A * ((A + 1) + (A - 1) * c + beta)) / a0,
    b1: (-2 * A * ((A - 1) + (A + 1) * c)) / a0,
    b2: (A * ((A + 1) + (A - 1) * c - beta)) / a0,
    a1: (2 * ((A - 1) - (A + 1) * c)) / a0,
    a2: ((A + 1) - (A - 1) * c - beta) / a0,
  };
}

/** High-pass + high-shelf a pandeiro one-shot so it sits above the surdo. */
export function pandeiroEq(samples: Float32Array, sampleRate: number): Float32Array {
  const hp = apply(samples, highpass(HP_HZ, HP_Q, sampleRate));
  return apply(hp, highshelf(SHELF_HZ, SHELF_DB, sampleRate));
}

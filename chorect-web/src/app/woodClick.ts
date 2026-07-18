// Shared woodblock-like click synth for the rhythm tools (Rhythm units, phrases,
// the drum-machine click track, and the standalone Metronome). A short decaying
// sine burst — a higher pitch is used for the accented "1" of each bar.

/** A percussive wood click: [freqHz] sine with a fast exponential decay, [ms] long. */
export function synthClick(freqHz: number, ms: number): Float32Array {
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

/** The two standard rhythm-tool clicks: accent (the "1" count) + regular beat. */
export const ACCENT_CLICK_HZ = 2800;
export const BEAT_CLICK_HZ = 2000;

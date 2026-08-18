// Shared woodblock-like click synth for the rhythm tools (Rhythm units, phrases,
// the drum-machine click track, and the standalone Metronome). A short decaying
// sine burst — a higher pitch is used for the accented "1" of each bar.

/** A percussive wood click: [freqHz] sine with a fast exponential decay, [ms] long,
 *  rendered at `sr`. `sr` is REQUIRED: it used to default to 44100 while the buffer was
 *  declared at the AudioContext rate, so every click played ~147 cents sharp and 8.8 %
 *  short on a 48 kHz device. Mirrors Kotlin's `synthClick(..., sr = audio.sampleRate)`. */
export function synthClick(freqHz: number, ms: number, sr: number): Float32Array {
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

/** Rate-keyed cache for [synthClick]. Clicks are deterministic and few, so one shared
 *  memo beats a per-state-class cache — and beats building them in a constructor, which
 *  would have to read (and therefore create) the AudioContext before a user gesture. */
const CLICK_CACHE = new Map<string, Float32Array>();

/** Memoised [synthClick]. Call it at PLAY time, passing `audio.sampleRate`. */
export function clickAt(freqHz: number, ms: number, sr: number): Float32Array {
  const key = `${freqHz}|${ms}|${sr}`;
  let buf = CLICK_CACHE.get(key);
  if (!buf) { buf = synthClick(freqHz, ms, sr); CLICK_CACHE.set(key, buf); }
  return buf;
}

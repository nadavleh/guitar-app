// The Car-mode lead-in beep: a short soft-attack sine, synthesised on the fly.
// Mirrors audio/src/main/kotlin/app/guitar/audio/CueBeep.kt — keep the envelope
// identical so both platforms announce an exercise with the same sound. No asset
// ships for this.

/**
 * Render `ms` of a `freqHz` sine at `sr` Hz, with `attackMs` of linear fade-in
 * followed by an exponential decay, scaled so samples stay within ±`peak`.
 *
 * The attack matters: a raw sine that starts at full amplitude produces an audible
 * click, which in a car reads as a glitch rather than a cue.
 */
export function renderCueBeep(
  freqHz: number, ms: number, sr: number, peak: number, attackMs: number,
): Float32Array {
  const n = Math.trunc(sr * ms / 1000);
  if (n <= 0) return new Float32Array(0);
  const out = new Float32Array(n);
  const attackSamples = Math.min(Math.trunc(sr * attackMs / 1000), n);
  const twoPiFOverSr = 2 * Math.PI * freqHz / sr;
  for (let i = 0; i < n; i++) {
    // Linear attack, then exp(-6t) decay over the remaining length: at the final
    // sample that is e^-6 ≈ 0.0025 of peak, comfortably silent.
    const attack = attackSamples > 0 ? Math.min(1, i / attackSamples) : 1;
    const decay = Math.exp(-6 * i / n);
    out[i] = Math.sin(twoPiFOverSr * i) * attack * decay * peak;
  }
  return out;
}

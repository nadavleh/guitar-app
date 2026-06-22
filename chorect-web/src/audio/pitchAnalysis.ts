// Equal-tempered pitch math with configurable A4, ported from audio/.../PitchAnalysis.kt.

export interface PitchEstimate {
  /** Detected fundamental in Hz. */
  readonly freqHz: number;
  /** Nearest equal-tempered MIDI note, clamped to 0..127. */
  readonly midi: number;
  /** Signed cents offset from the nearest note, in (-50, +50]. */
  readonly cents: number;
}

const LN2 = 0.6931471805599453;

export function analyzePitch(freqHz: number, a4Hz = 440): PitchEstimate {
  if (freqHz <= 0) throw new Error(`freq must be > 0, got ${freqHz}`);
  if (a4Hz <= 0) throw new Error(`A4 reference must be > 0, got ${a4Hz}`);
  const midiFloat = 69 + (12 * Math.log(freqHz / a4Hz)) / LN2;
  const midi = Math.round(midiFloat);
  const cents = (midiFloat - midi) * 100;
  return { freqHz, midi: Math.min(Math.max(midi, 0), 127), cents };
}

export function midiToFreqA4(midi: number, a4Hz = 440): number {
  return a4Hz * Math.pow(2, (midi - 69) / 12);
}

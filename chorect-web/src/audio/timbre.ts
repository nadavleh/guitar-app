// Tone-shaping parameters for PluckedSynth, ported from audio/.../Timbre.kt.

export interface Timbre {
  /** Karplus-Strong damping per sample. Closer to 1.0 = longer sustain. */
  readonly damping: number;
  /** Peak amplitude in [0, 1]. */
  readonly amplitude: number;
}

export const Timbres = {
  Guitar: { damping: 0.997, amplitude: 0.6 } as Timbre,
  Cavaquinho: { damping: 0.989, amplitude: 0.55 } as Timbre,
  Clarity: { damping: 0.997, amplitude: 0.62 } as Timbre,
};

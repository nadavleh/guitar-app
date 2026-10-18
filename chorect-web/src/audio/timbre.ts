// Tone-shaping parameters for PluckedSynth, ported from audio/.../Timbre.kt.

export interface Timbre {
  /** Karplus-Strong damping per sample. Closer to 1.0 = longer sustain. */
  readonly damping: number;
  /** Peak amplitude in [0, 1]. */
  readonly amplitude: number;
  /** Stereo position: -1 = hard left, 0 = center, 1 = hard right. Used by the
   *  MODERN (overhaul) output chain only — the legacy chain is mono/center. */
  readonly pan: number;
  /** Fraction of this voice's signal sent to the reverb bus, in [0, 1].
   *  MODERN chain only. */
  readonly reverbSend: number;
  /** Envelope release time in milliseconds when a voice is stopped. MODERN
   *  chain only — the legacy chain hard-stops. */
  readonly releaseMs: number;
  /** Short and dry: released after its duration even when it is a sample, no reverb
   *  send, and a darker synth tone. Mirrors Kotlin Timbre.gated. */
  readonly gated?: boolean;
}

export const Timbres = {
  Guitar: { damping: 0.997, amplitude: 0.6, pan: 0, reverbSend: 0.03, releaseMs: 20 } as Timbre,
  Cavaquinho: { damping: 0.989, amplitude: 0.55, pan: 0, reverbSend: 0.03, releaseMs: 20 } as Timbre,
  Clarity: { damping: 0.997, amplitude: 0.62, pan: 0, reverbSend: 0.03, releaseMs: 20 } as Timbre,
  /** Interval trainer + song snippets: soft, short, no reverb. */
  Soft: { damping: 0.994, amplitude: 0.42, pan: 0, reverbSend: 0, releaseMs: 70, gated: true } as Timbre,
};

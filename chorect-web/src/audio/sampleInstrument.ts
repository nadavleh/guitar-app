// Sampled guitar voices, ported from audio/.../SampleInstrument.kt. A "bank" is
// a fixed set of recorded root notes for one instrument sound (e.g. "acoustic");
// playback picks the closest recorded root and pitch-shifts it via playbackRate
// to reach any requested MIDI note.

/** A loaded sample bank: recorded root MIDI notes + their decoded buffers. */
export interface SampleBank {
  id: string;
  roots: number[];
  buffers: Map<number, AudioBuffer>;
}

/** Recorded root closest to midi; ties -> lower (mirrors Kotlin SampleInstrument.nearest). */
export function nearestRoot(roots: number[], midi: number): number {
  let best = roots[0];
  let bestD = Math.abs(midi - best);
  for (let i = 1; i < roots.length; i++) {
    const d = Math.abs(midi - roots[i]);
    if (d < bestD) {
      best = roots[i];
      bestD = d;
    }
  }
  return best;
}

/** Playback rate to retune a sample recorded at [root] up/down to [target]. */
export function pitchRate(target: number, root: number): number {
  return Math.pow(2, (target - root) / 12);
}

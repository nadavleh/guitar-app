// Constant-power stereo panning, ported from audio/.../Panner.kt.
//
// pan ∈ [-1, 1]; -1 = hard left, 0 = center, 1 = right.

/** Constant-power pan gains: theta = (clamp(pan,-1,1)+1) * PI/4 -> [cos(theta), sin(theta)]. */
export function panGains(pan: number): [number, number] {
  const p = Math.min(1, Math.max(-1, pan));
  const theta = (p + 1.0) * (Math.PI / 4.0); // 0..pi/2
  return [Math.cos(theta), Math.sin(theta)];
}

/** Subtle pan by pitch: MIDI 40..88 -> [-spread, +spread], clamped. */
export function panForMidi(midi: number, spread = 0.3): number {
  const t = Math.min(1, Math.max(0, (midi - 40) / (88 - 40))); // 0..1
  const v = (t * 2.0 - 1.0) * spread;
  return Math.min(spread, Math.max(-spread, v));
}

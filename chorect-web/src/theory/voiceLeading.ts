// Voice-leading voicing picker, ported from theory/.../VoiceLeading.kt.

import { ChordShape } from "./chords";

/** Sum of per-string fret displacement + a fixed penalty per muted↔played transition. */
export function movementCost(prev: ChordShape, next: ChordShape, mutePenalty = 3): number {
  const n = Math.min(prev.frets.length, next.frets.length);
  let cost = 0;
  for (let s = 0; s < n; s++) {
    const a = prev.frets[s];
    const b = next.frets[s];
    if (a === null && b === null) cost += 0;
    else if (a === null || b === null) cost += mutePenalty;
    else cost += Math.abs(a - b);
  }
  return cost;
}

/** Index of the candidate voicing closest to [prev] by movement cost. */
export function pickMinMovement(prev: ChordShape, candidates: ChordShape[]): number {
  if (candidates.length === 0) return 0;
  let best = 0;
  let bestCost = movementCost(prev, candidates[0]);
  for (let i = 1; i < candidates.length; i++) {
    const cst = movementCost(prev, candidates[i]);
    if (cst < bestCost) { bestCost = cst; best = i; }
  }
  return best;
}

// Area "theory" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- theory

import { scalePositionsFor, scaleNotesFrom, SCALES, parsePitchClass } from "../../src/theory";
import { standard } from "../../src/theory/tunings";
import { check } from "./harness";

export function run(): void {

  // --- Scales: A minor pentatonic = A C D E G ---
  const aRoot = parsePitchClass("A");
  const minPent = SCALES.get("minor pentatonic")!;
  const notes = new Set(scaleNotesFrom(minPent, aRoot));
  check("A minor pentatonic = {A,C,D,E,G}", [9, 0, 2, 4, 7].every((p) => notes.has(p)) && notes.size === 5);
  const aPositions = scalePositionsFor(aRoot, minPent, standard, 14);
  check("A minor pentatonic yields positions", aPositions.length >= 3);
}

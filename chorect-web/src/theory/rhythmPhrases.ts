// Multi-bar rhythmic phrases. Mirror of theory/.../RhythmPhrases.kt.

import { RhythmUnit, RHYTHM_UNITS, RHYTHM_UNITS_RESTS, clickFractions } from "./rhythmUnits";
import { Rng, defaultRng } from "./random";

export const SLOTS_PER_BEAT = 4;   // sixteenth grid
export const PHRASE_MIN_BARS = 1;
export const PHRASE_MAX_BARS = 4;
export const PHRASE_TIME_SIGNATURES = [2, 3, 4];   // N/4

/** Units a phrase can be built from: the 16th-grid units (no triplet) + rests. */
export const PHRASE_POOL: RhythmUnit[] =
  [...RHYTHM_UNITS.filter((u) => u.subdivision === SLOTS_PER_BEAT), ...RHYTHM_UNITS_RESTS];

export interface PhraseOnset { slot: number; accent: boolean; }

export interface RhythmPhrase {
  bars: number;
  beatsPerBar: number;
  beats: RhythmUnit[];   // length = bars*beatsPerBar, each subdivision 4
}

export function phraseTotalSlots(p: RhythmPhrase): number { return p.beats.length * SLOTS_PER_BEAT; }
export function phraseSlotsPerBar(p: RhythmPhrase): number { return p.beatsPerBar * SLOTS_PER_BEAT; }

/** Global 16th-slot of every click, accented on bar downbeats that carry an onset. */
export function phraseOnsets(p: RhythmPhrase): PhraseOnset[] {
  const out: PhraseOnset[] = [];
  p.beats.forEach((unit, b) => {
    const isBarStart = b % p.beatsPerBar === 0;
    for (const f of clickFractions(unit)) {
      const local = Math.round(f * SLOTS_PER_BEAT);
      out.push({ slot: b * SLOTS_PER_BEAT + local, accent: isBarStart && local === 0 });
    }
  });
  return out;
}

export function generatePhrase(bars: number, beatsPerBar: number, rng: Rng = defaultRng): RhythmPhrase {
  const b = Math.min(Math.max(bars, PHRASE_MIN_BARS), PHRASE_MAX_BARS);
  const bpb = PHRASE_TIME_SIGNATURES.includes(beatsPerBar) ? beatsPerBar : 2;
  const beats = Array.from({ length: b * bpb }, () => PHRASE_POOL[rng.int(PHRASE_POOL.length)]);
  return { bars: b, beatsPerBar: bpb, beats };
}

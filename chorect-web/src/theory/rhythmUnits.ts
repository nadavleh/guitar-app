// Basic one-beat rhythmic units. Mirror of theory/.../RhythmUnits.kt — keep in sync.

export enum RhythmNoteType {
  Quarter = "Quarter",
  DottedEighth = "DottedEighth",
  Eighth = "Eighth",
  Sixteenth = "Sixteenth",
  TripletEighth = "TripletEighth",
}

/** One element: duration in grid slots, notation type, and whether it's a rest
 *  (silent — no click, drawn as a rest glyph). */
export interface RNote { slots: number; type: RhythmNoteType; rest: boolean; }

export interface RhythmUnit {
  id: string;
  name: string;
  count: string;
  subdivision: number;   // 4 = sixteenth grid, 3 = triplet
  notes: RNote[];
}

/** Slot index (0-based) where each element begins (notes AND rests) — for drawing. */
export function starts(u: RhythmUnit): number[] {
  const out: number[] = [];
  let acc = 0;
  for (const n of u.notes) { out.push(acc); acc += n.slots; }
  return out;
}

/** Fraction (0..1) within the beat where each PLAYED note starts — the clicks. */
export function clickFractions(u: RhythmUnit): number[] {
  const out: number[] = [];
  let acc = 0;
  for (const n of u.notes) {
    if (!n.rest) out.push(acc / u.subdivision);
    acc += n.slots;
  }
  return out;
}

function typeOf(slots: number, sub: number): RhythmNoteType {
  return sub === 3 ? RhythmNoteType.TripletEighth :
    slots >= 4 ? RhythmNoteType.Quarter :
    slots === 3 ? RhythmNoteType.DottedEighth :
    slots === 2 ? RhythmNoteType.Eighth : RhythmNoteType.Sixteenth;
}

/** Build a unit from signed slot counts: a NEGATIVE value is a rest of that many slots. */
function unit(id: string, name: string, count: string, sub: number, ...slots: number[]): RhythmUnit {
  const notes = slots.map((s) => {
    const abs = Math.abs(s);
    return { slots: abs, type: typeOf(abs, sub), rest: s < 0 };
  });
  return { id, name, count, subdivision: sub, notes };
}

/** Plain (no-rest) one-beat units. */
export const RHYTHM_UNITS: RhythmUnit[] = [
  unit("quarter", "Quarter", "1", 4, 4),
  unit("two-eighths", "Two eighths", "1  &", 4, 2, 2),
  unit("four-sixteenths", "Four sixteenths", "1 e & a", 4, 1, 1, 1, 1),
  unit("eighth-two-sixteenths", "Eighth + two sixteenths", "1  & a", 4, 2, 1, 1),
  unit("two-sixteenths-eighth", "Two sixteenths + eighth", "1 e &", 4, 1, 1, 2),
  unit("sixteenth-eighth-sixteenth", "Sixteenth–eighth–sixteenth", "1 e   a", 4, 1, 2, 1),
  unit("dotted-eighth-sixteenth", "Dotted eighth + sixteenth", "1     a", 4, 3, 1),
  unit("eighth-triplet", "Eighth-note triplet", "1 trip let", 3, 1, 1, 1),
];

/** One-beat units that include rests (a negative slot = a rest). */
export const RHYTHM_UNITS_RESTS: RhythmUnit[] = [
  unit("rest-eighth-eighthrest", "Eighth + eighth rest", "1", 4, 2, -2),
  unit("rest-eighthrest-eighth", "Eighth rest + eighth", "&", 4, -2, 2),
  unit("rest-two16-eighthrest", "Two sixteenths + eighth rest", "1 e", 4, 1, 1, -2),
  unit("rest-eighthrest-two16", "Eighth rest + two sixteenths", "& a", 4, -2, 1, 1),
  unit("rest-eighth-16-16rest", "Eighth, sixteenth, sixteenth rest", "1 &", 4, 2, 1, -1),
  unit("rest-eighth-16rest-16", "Eighth, sixteenth rest, sixteenth", "1 a", 4, 2, -1, 1),
  unit("rest-16-16rest-eighth", "Sixteenth, sixteenth rest, eighth", "1 &", 4, 1, -1, 2),
  unit("rest-16rest-16-eighth", "Sixteenth rest, sixteenth, eighth", "e &", 4, -1, 1, 2),
  unit("rest-three16-16rest", "Three sixteenths + sixteenth rest", "1 e &", 4, 1, 1, 1, -1),
  unit("rest-offbeat-16s", "Off-beat sixteenths", "e a", 4, -1, 1, -1, 1),
];

export function rhythmUnitById(id: string): RhythmUnit | undefined {
  return [...RHYTHM_UNITS, ...RHYTHM_UNITS_RESTS].find((u) => u.id === id);
}

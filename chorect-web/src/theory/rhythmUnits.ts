// Basic one-beat rhythmic units. Mirror of theory/.../RhythmUnits.kt — keep in sync.

export enum RhythmNoteType {
  Quarter = "Quarter",
  DottedEighth = "DottedEighth",
  Eighth = "Eighth",
  Sixteenth = "Sixteenth",
  TripletEighth = "TripletEighth",
}

export interface RNote { slots: number; type: RhythmNoteType; }

export interface RhythmUnit {
  id: string;
  name: string;
  count: string;
  subdivision: number;   // 4 = sixteenth grid, 3 = triplet
  notes: RNote[];
}

/** Slot index (0-based) where each note begins — the click onsets. */
export function onsets(u: RhythmUnit): number[] {
  const out: number[] = [];
  let acc = 0;
  for (const n of u.notes) { out.push(acc); acc += n.slots; }
  return out;
}

/** Fraction (0..1) within the beat of each onset. */
export function onsetFractions(u: RhythmUnit): number[] {
  return onsets(u).map((s) => s / u.subdivision);
}

function rn(slots: number, sub: number): RNote {
  const type =
    sub === 3 ? RhythmNoteType.TripletEighth :
    slots >= 4 ? RhythmNoteType.Quarter :
    slots === 3 ? RhythmNoteType.DottedEighth :
    slots === 2 ? RhythmNoteType.Eighth : RhythmNoteType.Sixteenth;
  return { slots, type };
}

function unit(id: string, name: string, count: string, sub: number, ...slots: number[]): RhythmUnit {
  return { id, name, count, subdivision: sub, notes: slots.map((s) => rn(s, sub)) };
}

/** The 8 basic one-beat rhythmic units, in teaching order. */
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

export function rhythmUnitById(id: string): RhythmUnit | undefined {
  return RHYTHM_UNITS.find((u) => u.id === id);
}

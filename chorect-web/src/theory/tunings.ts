// Preset & default tunings, ported from theory/.../Tunings.kt.

import { Instrument, Tuning, tuningOf } from "./core";

// ---- Guitar (6-string) ----
export const standard = tuningOf("E2", "A2", "D3", "G3", "B3", "E4");
export const dropD = tuningOf("D2", "A2", "D3", "G3", "B3", "E4");
export const dadgad = tuningOf("D2", "A2", "D3", "G3", "A3", "D4");
export const openG = tuningOf("D2", "G2", "D3", "G3", "B3", "D4");
export const openD = tuningOf("D2", "A2", "D3", "F#3", "A3", "D4");
export const halfStepDown = tuningOf("D#2", "G#2", "C#3", "F#3", "A#3", "D#4");
export const wholeStepDown = tuningOf("D2", "G2", "C3", "F3", "A3", "D4");

export const guitarPresets: ReadonlyMap<string, Tuning> = new Map([
  ["Standard", standard],
  ["Drop D", dropD],
  ["DADGAD", dadgad],
  ["Open G", openG],
  ["Open D", openD],
  ["Half-step down", halfStepDown],
  ["Whole-step down", wholeStepDown],
]);

// ---- Cavaquinho (4-string) ----
export const cavaqDgbe = tuningOf("D4", "G4", "B4", "E5");
export const cavaqDgbd = tuningOf("D4", "G4", "B4", "D5");

export const cavaquinhoPresets: ReadonlyMap<string, Tuning> = new Map([
  ["DGBe", cavaqDgbe],
  ["DGBD", cavaqDgbd],
]);

export function presetsFor(instrument: Instrument): ReadonlyMap<string, Tuning> {
  return instrument === Instrument.Guitar ? guitarPresets : cavaquinhoPresets;
}

export function defaultFor(instrument: Instrument): Tuning {
  return instrument === Instrument.Guitar ? standard : cavaqDgbe;
}

export function defaultNameFor(instrument: Instrument): string {
  return instrument === Instrument.Guitar ? "Standard" : "DGBe";
}

/** Union of all presets across instruments, for name → Tuning lookups. */
export const allPresets: ReadonlyMap<string, Tuning> = new Map([
  ...guitarPresets,
  ...cavaquinhoPresets,
]);

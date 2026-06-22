// Brand palette — ported verbatim from app/.../Theme.kt (GuitarColors).
// The Kotlin app is always dark; we keep that here.

export const Colors = {
  background: "#0E1014",
  surface: "#181B22",
  surfaceElev: "#20242E",
  divider: "#262A33",

  textPrimary: "#F5F0E6",
  textSecondary: "#9098A6",
  textDisabled: "#4A5060",

  primary: "#F2A93B", // amber
  onPrimary: "#1A1206",

  rootTone: "#D34D52", // crimson
  chordTone: "#3FB8AF", // teal
  scaleTone: "#9B7BF7", // lavender
  pickSelect: "#F2A93B", // amber

  wood: "#3D2817",
  woodGrain: "#2C1C10",
  nut: "#0A0A0B",
  fretWire: "#6F6F75",
  inlay: "#E8E4D9",
  stringWound: "#C9A876", // bronze base for low strings
  stringPlain: "#DCC698", // bright steel for high strings

  tuned: "#66BB6A", // tuner "in tune" green
} as const;

/** Hex color with an alpha applied, returned as rgba(). */
export function withAlpha(hex: string, alpha: number): string {
  const h = hex.replace("#", "");
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

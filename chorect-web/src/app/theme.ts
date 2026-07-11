// Brand palette — ported verbatim from app/.../Theme.kt (SignalColors /
// GuitarColors, DEFAULT combination: dark theme, Coral accent). This feeds
// canvases (fretboard marks etc.), which stay fixed-dark and non-accent-
// reactive for now, same as before ("the Kotlin app is always dark" — now:
// the Signal dark tokens at their default coral/teal/blue combo).

export const Colors = {
  background: "#10141E",
  surface: "#191F2E",
  surfaceElev: "#20283C",
  divider: "#273049",

  textPrimary: "#EAEEF7",
  textSecondary: "#7C86A2",
  textDisabled: "#454E64",

  primary: "#FF5C57", // coral (act, default accent)
  onPrimary: "#2A0A09",

  rootTone: "#FF5C57", // root marks: act (coral)
  chordTone: "#3DDCC8", // chord-tone marks: feedback (teal)
  scaleTone: "#8AA3FF", // scale-tone marks: blue
  pickSelect: "#FF5C57", // coral (act, default accent)

  wood: "#3D2817",
  woodGrain: "#2C1C10",
  nut: "#0A0A0B",
  fretWire: "#6F6F75",
  inlay: "#E8E4D9",
  stringWound: "#C9A876", // bronze base for low strings
  stringPlain: "#DCC698", // bright steel for high strings

  tuned: "#3DDCC8", // tuner "in tune": feedback (teal)
} as const;

/** Hex color with an alpha applied, returned as rgba(). */
export function withAlpha(hex: string, alpha: number): string {
  const h = hex.replace("#", "");
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** Fretboard "wood" art palette — the neck's non-token colors, ported verbatim
 *  from Theme.kt's `BoardColors` companion (Dark/Light). Fretboard-v3 material
 *  pass: the wood is a vertical woodA→woodB→woodA gradient, frets are two-tone
 *  metal (fretWire bright edge over fretWireDark), the dark-theme nut is bone
 *  (real nuts aren't black), and hollow scale-tone dots knock the wood back
 *  with scaleFill. */
export interface BoardPalette {
  woodA: string;
  woodB: string;
  woodGrain: string;
  nut: string;
  fretWire: string;
  fretWireDark: string;
  inlay: string;
  stringWound: string;
  stringPlain: string;
  /** Translucent backing inside hollow (scale-tone) dots so labels stay legible. */
  scaleFill: string;
  bg: string;
}

export const BoardDark: BoardPalette = {
  woodA: "#4A3320",
  woodB: "#33200F",
  woodGrain: "#2C1C10",
  nut: "#EDE6D6",
  fretWire: "#9B9BA3",
  fretWireDark: "#5A5A61",
  inlay: "#E8E4D9",
  stringWound: "#C9A876",
  stringPlain: "#DCC698",
  scaleFill: "rgba(16,20,30,0.72)",
  bg: "#10141E",
};

export const BoardLight: BoardPalette = {
  woodA: "#F0E2C0",
  woodB: "#DFC99C",
  woodGrain: "#D8C49B",
  nut: "#4A4136",
  fretWire: "#A9A9AF",
  fretWireDark: "#6E6E74",
  inlay: "#6B5B44",
  stringWound: "#8A6F45",
  stringPlain: "#6E6046",
  scaleFill: "rgba(251,247,236,0.78)",
  bg: "#F3EDDF",
};

/** Resolves the active board palette from the current theme (`:root.light`),
 *  same source of truth ui.ts's render() already toggles on document root. */
export function boardColors(): BoardPalette {
  return document.documentElement.classList.contains("light") ? BoardLight : BoardDark;
}

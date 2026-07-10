// Signal nav icon set — inline SVG, no emoji (design spec §"No emoji icons
// anywhere"). One icon() factory used everywhere a glyph is needed: the nav
// rail/tab bar (ui.ts renderNav()) and the More sheet's rows. Path data is
// ported 1:1 from the pre-M2 NAV_ICONS map that lived in ui.ts (now deleted
// there — this module is its only home); "more" and "stats" are new, added
// for the 4+More shell (Task 4 / M2).

export type IconName =
  | "neck"
  | "ear"
  | "rhythm"
  | "loop"
  | "tuner"
  | "decompose"
  | "more"
  | "stats"
  | "settings";

const PATH: Record<IconName, string> = {
  // Fretboard grid (was NAV_ICONS.fretboard).
  neck: '<rect x="3" y="4" width="18" height="16" rx="1"/><line x1="3" y1="9.3" x2="21" y2="9.3"/><line x1="3" y1="14.6" x2="21" y2="14.6"/><line x1="9" y1="4" x2="9" y2="20"/><line x1="15" y1="4" x2="15" y2="20"/>',
  // Ear / listening glyph (unchanged).
  ear: '<path d="M4 10v4h3l5 4V6L7 10H4z"/><path d="M16 8.5a4 4 0 0 1 0 7"/><path d="M18.5 6a7.5 7.5 0 0 1 0 12"/>',
  // Drum bars (was NAV_ICONS.drums — Rhythm tab = the samba/drum looper).
  rhythm: '<line x1="5" y1="20" x2="5" y2="11"/><line x1="10" y1="20" x2="10" y2="4"/><line x1="15" y1="20" x2="15" y2="13"/><line x1="20" y1="20" x2="20" y2="8"/>',
  // Loop arrows (unchanged).
  loop: '<polyline points="17 2 21 6 17 10"/><path d="M3 12V10a4 4 0 0 1 4-4h14"/><polyline points="7 22 3 18 7 14"/><path d="M21 12v2a4 4 0 0 1-4 4H3"/>',
  // Tuner dial + needle (unchanged).
  tuner: '<path d="M4 18a8 8 0 1 1 16 0"/><line x1="12" y1="18" x2="15.5" y2="11.5"/><circle cx="12" cy="18" r="1.3" fill="currentColor" stroke="none"/>',
  // Puzzle piece (unchanged).
  decompose: '<path d="M9 4.5a2 2 0 1 1 4 0H16a1 1 0 0 1 1 1v3a2 2 0 1 1 0 4v3a1 1 0 0 1-1 1h-3a2 2 0 1 0-4 0H6a1 1 0 0 1-1-1v-3a2 2 0 1 1 0-4V5.5a1 1 0 0 1 1-1h3z"/>',
  // Overflow ellipsis — new, for the "More" tab.
  more: '<circle cx="5" cy="12" r="1.7" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.7" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1.7" fill="currentColor" stroke="none"/>',
  // Ascending bars on a baseline — new, for Challenge stats.
  stats: '<line x1="3" y1="21" x2="21" y2="21"/><rect x="5" y="13" width="4" height="7" rx="0.6"/><rect x="11" y="8" width="4" height="12" rx="0.6"/><rect x="17" y="4" width="4" height="16" rx="0.6"/>',
  // Sliders (was NAV_ICONS.options — Settings row in the More sheet).
  settings: '<line x1="4" y1="8" x2="20" y2="8"/><circle cx="9" cy="8" r="2.1"/><line x1="4" y1="16" x2="20" y2="16"/><circle cx="15" cy="16" r="2.1"/>',
};

/**
 * One SVG glyph, stroke=currentColor (so it inherits whatever `color` the
 * caller sets — the rail/tab-bar item's selected/unselected tint, or the
 * More sheet row's accent tint), wrapped in a `<span class="glyph">` so it
 * drops straight into the existing `.rail-btn`/`.more-row` layouts (same DOM
 * shape the old inline-SVG rail items used). `size` is the rendered box in
 * CSS px; the nav rail/tab bar uses the ~20px default, More-sheet rows pass
 * a slightly larger size.
 */
export function icon(name: IconName, size = 20): HTMLElement {
  const span = document.createElement("span");
  span.className = "glyph";
  span.innerHTML =
    `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" ` +
    `stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">${PATH[name]}</svg>`;
  return span;
}

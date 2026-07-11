// Signal nav icon set — inline SVG, no emoji (design spec §"No emoji icons
// anywhere"). One icon() factory used everywhere a glyph is needed: the nav
// rail/tab bar (ui.ts renderNav()) and the More sheet's rows. Path data is
// ported 1:1 from the pre-M2 NAV_ICONS map that lived in ui.ts (now deleted
// there — this module is its only home); "more" and "stats" are new, added
// for the 4+More shell (Task 4 / M2). "play"/"stop"/"tune"/"eq"/"chevronDown"/
// "waves"/"spread"/"timer"/"note"/"flask" are new for the transport dock +
// Tone sheet (Task 6 / M3, see transport.ts) — replacing the old ▶/⏹ text
// glyphs and the per-screen 🎚 popup with SVG throughout.

export type IconName =
  | "neck"
  | "ear"
  | "rhythm"
  | "loop"
  | "tuner"
  | "decompose"
  | "more"
  | "stats"
  | "settings"
  | "play"
  | "stop"
  | "tune"
  | "eq"
  | "chevronDown"
  | "chevronUp"
  | "waves"
  | "spread"
  | "timer"
  | "note"
  | "flask"
  | "restart"
  | "close"
  | "add"
  | "mic"
  | "sun"
  | "moon";

const PATH: Record<IconName, string> = {
  // Fretboard board — mirrors Android ShellIcons.Fretboard (board outline +
  // nut + 2 frets + 3 strings + 2 inlay dots).
  neck: '<path d="M3.5 6.5 L20.5 6.5 L20.5 17.5 L3.5 17.5 Z M6 6.5 L6 17.5 M11 6.5 L11 17.5 M16 6.5 L16 17.5"/><path d="M3.5 9.5 L20.5 9.5 M3.5 12 L20.5 12 M3.5 14.5 L20.5 14.5" stroke-width="1.2"/><circle cx="9.6" cy="12" r="1.1" fill="currentColor" stroke="none"/><circle cx="14.6" cy="12" r="1.1" fill="currentColor" stroke="none"/>',
  // Ear outline — mirrors Android ShellIcons.Ear (outer helix + lobe, inner
  // antihelix/canal curve).
  ear: '<path d="M17.5 10 C17.5 6.4 15 4 12 4 C9 4 6.5 6.4 6.5 10 C6.5 12.2 7.6 13.2 8.4 14.6 C9.1 15.8 9.2 17.3 10.3 18.4 C11.6 19.7 13.9 19.6 15 18.2"/><path d="M14.5 10 C14.5 8.1 13.4 7 12 7 C10.6 7 9.5 8.1 9.5 10 C9.5 11.4 10.5 11.9 11.2 13"/>',
  // Drum — mirrors Android ShellIcons.Drum (elliptical head, tapered shell,
  // crossed sticks above).
  rhythm: '<path d="M4.5 11.5 C4.5 9.8 7.9 8.5 12 8.5 C16.1 8.5 19.5 9.8 19.5 11.5 C19.5 13.2 16.1 14.5 12 14.5 C7.9 14.5 4.5 13.2 4.5 11.5 Z"/><path d="M4.5 11.5 L4.5 16.5 C4.5 18.2 7.9 19.5 12 19.5 C16.1 19.5 19.5 18.2 19.5 16.5 L19.5 11.5"/><path d="M8.5 10.5 L16.5 4.5 M15.5 10.5 L7.5 4.5"/>',
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
  // Transport dock: act-filled Play/Stop button.
  play: '<polygon points="7 4 20 12 7 20 7 4"/>',
  stop: '<rect x="6" y="6" width="12" height="12" rx="1.5"/>',
  // Tone chip / small tune buttons (Fretboard, Tuner, Decompose, Options).
  tune: '<line x1="6" y1="21" x2="6" y2="3"/><circle cx="6" cy="14" r="2"/><line x1="12" y1="21" x2="12" y2="3"/><circle cx="12" cy="8" r="2"/><line x1="18" y1="21" x2="18" y2="3"/><circle cx="18" cy="17" r="2"/>',
  // Tone sheet row icons.
  eq: '<line x1="5" y1="21" x2="5" y2="10"/><line x1="12" y1="21" x2="12" y2="4"/><line x1="19" y1="21" x2="19" y2="14"/><circle cx="5" cy="7" r="1.6"/><circle cx="12" cy="14" r="1.6"/><circle cx="19" cy="10" r="1.6"/>',
  chevronDown: '<polyline points="6 9 12 15 18 9"/>',
  // Tabs & order editor's reorder buttons (Signal T12): pairs with chevronDown.
  chevronUp: '<polyline points="6 15 12 9 18 15"/>',
  waves: '<path d="M2 13c2.2-4 4.4-4 6.6 0s4.4 4 6.6 0 4.4-4 6.6 0"/><path d="M2 18c2.2-4 4.4-4 6.6 0s4.4 4 6.6 0 4.4-4 6.6 0"/>',
  spread: '<line x1="4" y1="12" x2="20" y2="12"/><polyline points="8 6 4 12 8 18"/><polyline points="16 6 20 12 16 18"/>',
  timer: '<circle cx="12" cy="13" r="8"/><line x1="12" y1="13" x2="12" y2="9"/><line x1="12" y1="13" x2="15" y2="15"/><line x1="9" y1="3" x2="15" y2="3"/><line x1="12" y1="3" x2="12" y2="5"/>',
  note: '<path d="M9 18V5l11-2v13"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="17.5" cy="16" r="2.5"/>',
  flask: '<path d="M9 2h6"/><path d="M10 2v6.5l-6 10.5a1.5 1.5 0 0 0 1.3 2.2h13.4a1.5 1.5 0 0 0 1.3-2.2l-6-10.5V2"/><line x1="7.5" y1="15" x2="16.5" y2="15"/>',
  // Ear Training Signal restructure (T9): pinned header Restart/Quit icons for
  // an in-flight Progression/Advanced challenge (replaces inline "Restart"/
  // "Quit" text buttons — mirrors Android's Icons.Rounded.RestartAlt/Close).
  restart: '<polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>',
  close: '<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>',
  // Loop screen's dashed "+" add-bar tile (Signal T10 web mirror of Android's
  // AddBarTile, Icons.Rounded.Add).
  add: '<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>',
  // Tuner mic-permission panel (Signal T10): replaces the "🎤" emoji glyph.
  mic: '<path d="M12 15a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3z"/><path d="M6 11a6 6 0 0 0 12 0"/><line x1="12" y1="17" x2="12" y2="21"/><line x1="8" y1="21" x2="16" y2="21"/>',
  // Header quick theme toggle (v2.1.0): sun shown when dark (tap -> Light),
  // moon shown when light/auto (tap -> Dark) — mirrors Android's
  // Icons.Outlined.LightMode/DarkMode quick toggle in MainActivity.
  sun: '<circle cx="12" cy="12" r="4"/><line x1="12" y1="2" x2="12" y2="4"/><line x1="12" y1="20" x2="12" y2="22"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="2" y1="12" x2="4" y2="12"/><line x1="20" y1="12" x2="22" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>',
  moon: '<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>',
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

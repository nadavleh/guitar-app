# "Signal" GUI Redesign — Design (v2.0.0)

**Date:** 2026-07-10
**Status:** Approved by Nadav (direction, tabs, rollout confirmed; see amendments). Proceed to plan + implementation.
**Target version:** v2.0.0 (major — breaking redesign per versioning rules).
**Platforms:** Android (Compose) + chorect-web, 1:1 mirror. Big-bang rollout: build complete on a branch, ship once.
**Pitch:** https://claude.ai/code/artifact/f66cdff4-6448-4ee7-ada2-dae81d83bdcc (design source of truth for look/layout).

## Nadav's decisions (locked)

- **Direction: Signal.** Indigo ground, dual accents with fixed semantics: **coral = ACT** (play, primary buttons, destructive), **teal = FEEDBACK** (correct, in-tune, current tone, drum accents). Dark-first; a matching light theme ships too (theme dark/light/auto).
- **Tabs approved:** `Neck · Ear · Rhythm · Loop · ⋯More` — user-configurable set and order.
- **Rollout: big bang** — one release; no incremental restyles on main.
- **KEEP the current neck/fretboard rendering** (`FretboardView`/`FretboardCanvas` drawing stays as-is; palette recolor only). The pitch's full-height-neck/voicing-strip Home concept is dropped; the Neck screen keeps its current content with new chrome.
- **No emoji icons anywhere.** Android: Material icons (`material-icons-extended` is already a dependency). Web: inline SVG icons. Replaces all current emoji glyphs (🎚, 💾, ▶, ⏹, 🧩, etc.). Unicode arrows/musical text in *labels* (e.g. "I–V–vi–IV", "←") are fine; pictographic emoji are not.

## Design tokens (both platforms)

Dark (default):
| token | value | use |
|---|---|---|
| bg | `#10141E` | screen ground |
| surface | `#191F2E` | cards |
| surface2 | `#20283C` | inset cards, answer keys, transport |
| text | `#EAEEF7` | primary text |
| muted | `#7C86A2` | secondary text, labels |
| line | `#273049` | hairlines, borders |
| act | `#FF5C57` (coral, default accent) | play, primary actions, selection |
| onAct | `#2A0A09` | text on act |
| feedback | `#3DDCC8` (teal) | correct/in-tune/accents/tone chip |

Light: ground `#F4F6FB`, surface `#FFFFFF`, surface2 `#E9EDF6`, text `#1C2233`, muted `#5D6782`, line `#D8DEED`; act/feedback darkened for contrast (`#E03E39`, `#159C8B`). All text ≥ 4.5:1 in both themes.

**Accent picker:** `act` is user-swappable among 5 swatches — coral `#FF5C57` (default), amber `#FFB454`, teal `#3DDCC8`, blue `#8AA3FF`, purple `#C98ADF` (dark theme values; light theme uses darkened pairs). `feedback` stays teal unless act=teal, in which case feedback falls back to blue `#8AA3FF`. Persisted.

Type: system font; screen titles bold ~22sp tight tracking; uppercase mono-feel section labels (small, letter-spaced) for group headers; tabular numerals for BPM/cents.

## The four system moves

1. **Bottom tab bar (4 + More)** replaces the 7-item NavRail. Defaults: Neck (Fretboard screen), Ear (EarTraining), Rhythm (SambaLooper), Loop, More. `More` opens a screen hosting: Tuner, Decompose, Challenge Stats, Settings. Tab set + order configurable in Settings (pick any 4 of {Neck, Ear, Rhythm, Loop, Tuner, Decompose}; More is fixed). Persisted (DataStore / localStorage). Landscape: the bar becomes a compact left rail with the same 5 items (existing landscape support preserved).
2. **Transport dock** — persistent pill above the tab bar on Ear, Rhythm, Loop: round coral Play/Stop button, BPM value (tap → BPM slider popover), spacer, **tone chip** (teal outline, shows current Sound, e.g. "Nylon") opening the Tone sheet. Each screen wires the dock to its own loop state (ear.startLoop/stopLoop + progBpm; samba.start/stop + BPM; loop.startLoop/stopLoop + BPM). Web keeps spacebar parity.
3. **Tone sheet** — one modal bottom sheet, identical from every dock: Sound segmented control (Synth/Acoustic/Nylon/Electric), then rows: EQ (opens existing EQ controls), Reverb (existing reverb amount), Strum spread (slider), Ring sustain (slider), Boost root note (switch), Engine A/B (switch, labeled "New engine"). Replaces the 🎚 AudioQuick dropdown, the Playback ▾ sound-ish parts, and Options' audio rows. (Tempo stays on the dock, not in Tone.)
4. **Progressive disclosure** — every screen opens on its primary action; configuration folds behind summary rows/sheets. Specifics per screen below.

## Screens (Android composables; web mirrors)

- **Neck** (current Fretboard screen): keep the neck component and its current behaviors (zoom persistence, tap-to-play, left-handed). New chrome only: Signal-styled chip bar for chord/scale entry + label mode; controls that currently sit in other bars restyle in place. Neck board colors: chord/root dots move to act/feedback palette variants chosen for contrast on the wood-free indigo ground; keep mark shapes as today.
- **Ear**: top segmented Practice/Challenge; sub-modes (Progressions, Intervals, Note→Chord, Flavor, Inversions, AugDim, Decompose-link) as a chip row with overflow "More ▾". Practice: reveal cards first (current card behavior, restyled: current bar gets act border), action strip (← Prev · Next → · Hear 1–5–1 · → Loop) directly under the cards, then a **generator summary card** ("Diatonic · G Major? · Sevenths — tap to configure") opening a settings sheet holding everything currently in ProgressionSettings + generator dropdown + drill toggle. Fretboard panel unchanged (keep component; show/hide switch stays). Transport dock replaces inline Play/Stop.
  - **Challenge**: progress ring (act arc on line track, "Q n/10" center) + per-question dot strip (teal=right, coral=wrong, act-filled=current); Restart/Quit as icon buttons pinned in the screen header (never overflow); fixed Roman answer pad (grid of keys I…vii°, extension key "7th ▾" opens the extension row) replacing the popup keyboard; re-roll/cadence/transpose in one tools row. Keep all scoring/flow logic identical — this is a re-skin of interaction placement, not logic.
- **Rhythm**: header segments Pattern / Mixer / Kit. Pattern = the step grid full-width (hits = act, accents = feedback, playhead column highlighted, beat lines as today); one dismissible gesture-legend banner (tap=toggle · hold=accent · long-press/right-click=erase). Mixer = per-instrument + per-voice volume sliders (current mixer content). Kit = add/remove instruments (current catalog UI). Swing/metronome/zoom controls in a compact card under the grid. Transport dock drives start/stop + BPM.
- **Loop**: progression as a horizontal bar lane (chips with chord names, playhead bar act-bordered, "+" dashed bar to add via the chord picker); watch-on-neck panel default ON (keep current neck component); "now playing / next" banner; arpeggio/×2 toggles as chips. Transport dock.
- **Tuner** (under More): re-skin only — big note letter + Hz, needle arc (needle coral, turns teal within ±5¢), cents scale, string buttons (E A D G B e) that audition targets with the current tone; A4 reference control kept.
- **Decompose** (under More): Signal restyle of existing screen; no structural change.
- **More**: list screen — Tuner, Decompose, Challenge Stats, Settings, with icons.
- **Settings** (replaces Options): grouped list. **Personalize** first: Theme (dark/light/auto), Accent (5 swatches), Tabs & order (editor: pick 4 + drag order), Left-handed. Then **Instrument** (existing tunings/instrument content), then remaining existing options. Audio rows that moved to Tone get removed here (single source). Deferred (NOT in v2.0.0): density setting, settings search.

## Configurability summary (must ship)
Theme dark/light/auto · accent (5) · tab set + order · left-handed (existing) — all persisted on both platforms.

## Interaction/accessibility baseline
48dp min targets; long-press = secondary consistently (drum accent as today, tap-tempo deferred); one motion signature (sheets spring up; playhead pulse) honoring reduced-motion (web `prefers-reduced-motion`, Android animation scale); state never color-only (shape+label too); web keyboard parity kept (space; 1–7 answer keys where the answer pad is visible).

## Architecture notes

- **Android:** all colors flow through a `SignalTheme` (MaterialTheme wrapper): extend the existing `GuitarTheme`/color scheme with the token set + act/feedback + accent selection; kill ad-hoc hex in screens as they're touched. New shared composables: `SignalTabBar`, `TransportDock(playing, onPlayStop, bpm, onBpm, toneLabel, onTone)`, `ToneSheet(state)`, `SegmentedRow`, `SectionLabel`, `SummaryCard`. Navigation stays the existing `Sheet` enum + `openSheet` state machine — only its chrome changes (rail → tabs + More).
- **Web:** tokens as CSS custom properties on `:root` (dark/light via class, as today); new `tabbar`, `transport`, `tone-sheet` components in ui.ts; SVG icon set in one `icons.ts` (path constants). Screens keep their state classes untouched; only render functions restyle.
- **State/logic untouched:** this is chrome. Theory, audio, loopers, trainers, persistence keys all stay. New prefs: `theme_mode` (exists), `accent`, `tab_order`.
- **A/B engine toggle stays** (Tone sheet row).

## Out of scope (deferred)
Density setting; settings search; tap-tempo long-press; voicing-strip Home concept (rejected with the neck redesign); any theory/audio changes.

## Acceptance
Both platforms build green (tests, assembleDebug, web tsc/vite); every current feature reachable in ≤2 taps from its old location (map in plan); no emoji glyph remains in UI strings; light + dark verified; ship as v2.0.0 (versionCode 20000).

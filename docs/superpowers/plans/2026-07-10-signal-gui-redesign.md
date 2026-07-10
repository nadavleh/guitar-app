# Signal GUI Redesign Implementation Plan (v2.0.0)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the whole app (Android + web) into the approved "Signal" design — bottom tabs, transport dock, Tone sheet, Signal tokens — shipping once as v2.0.0.

**Architecture:** Chrome-only makeover: all state/logic/persistence classes stay; screens re-skin around them. A token layer (`SignalColors` + Compose `LocalSignal` / CSS custom properties) carries the palette incl. a user-selectable accent; shared shell components (`SignalTabBar`, `TransportDock`, `ToneSheet` and web equivalents) are built first, then each screen converts. Big-bang branch `signal-redesign`, merged once green.

**Tech Stack:** Kotlin + Compose M3 (BOM 2024.09.03, `material-icons-extended` available), TypeScript + hand-rolled DOM (chorect-web), DataStore/localStorage prefs.

## Global Constraints

- **Spec (authoritative, READ FIRST):** `docs/superpowers/specs/2026-07-10-signal-gui-redesign-design.md` — tokens table, screen specs, semantics (coral act / teal feedback), accent list, deferred items.
- **KEEP the current neck/fretboard rendering** (`FretboardView.kt` drawing, `fretboardCanvas.ts`): geometry/shapes/behavior untouched; only colors may move to Signal tokens.
- **NO EMOJI in UI.** Android: `androidx.compose.material.icons.Icons` (Outlined/Rounded). Web: inline SVG from `src/app/icons.ts`. Text glyphs like "←", "▾", "–" allowed; pictographs (🎚💾▶⏹🎸🧩📊 etc.) are not — including inside button labels ("Play ▶" → icon + "Play").
- **Logic untouched:** do not modify `EarTrainingState`, `SambaLooperState`, `LoopState`, audio engines, theory, or persistence semantics (new prefs `accent`, `tab_order` are additive).
- **Every existing feature stays reachable** (≤2 taps from old spot). When a task moves a control, its old rendering is REMOVED (no duplicates).
- Tests: `.\gradlew.bat :theory:test :audio:test :app:assembleDebug` green per task (Windows: `.\gradlew.bat`). Web: no local node — strict tsc-valid TS; CI verifies at ship.
- Versioning at ship: `versionCode = 20000`, `versionName = "2.0.0"`, web `"2.0.0"`. Archive prior APK to `releases/` first.
- Commit per task on branch `signal-redesign`; do not push until M7.

---

## File Structure

**Android (`app/src/main/kotlin/app/guitar/app/`):**
- Modify `Theme.kt` → Signal tokens, dark+light schemes, `Accent` enum, `LocalSignal`.
- Create `Shell.kt` → `SignalTabBar`, `MoreScreen`, tab model/order helpers.
- Create `Transport.kt` → `TransportDock`, `ToneSheet`, `SectionLabel`, `SummaryCard`, `SegmentedRow`.
- Modify `MainActivity.kt` (scaffold: rail→tabs), `Screens.kt`, `EarTrainingScreen.kt`, `SambaLooperScreen.kt` (or LooperScreen file names as found), `TunerScreen.kt`, `DecomposeScreen.kt`, options/settings composables, `AudioQuick.kt` (dissolved into ToneSheet), `TuningRepository.kt` (+`accent`, `tab_order`), `AppState.kt` (accent/tabs state).
- `FretboardView.kt` / `Common.kt` (`shapeMarks` colors): color hookup only.

**Web (`chorect-web/src/`):**
- Modify `style.css` (tokens), `app/theme.ts`.
- Create `app/icons.ts` (SVG path set: play, stop, neck, ear, rhythm, loop, more, tuner, decompose, stats, settings, save, restart, close, chevron, eq, reverb, metronome).
- Modify `app/ui.ts` (tab bar, More, transport, tone sheet, settings), `app/earTrainingUI.ts`, `app/sambaLooperUI.ts`, `app/loopUI.ts`, `app/decomposeUI.ts`, tuner UI, `app/appState.ts` (accent/tab prefs).

---

## MILESTONE M1 — Token foundation

### Task 1: Android Signal theme (Theme.kt + fretboard colors + prefs)
**Produces:** `object SignalColors` (all spec tokens, dark+light), `enum class Accent(val dark: Color, val light: Color, val label: String) { Coral, Amber, Teal, Blue, Purple }`, `data class SignalPalette(bg, surface, surface2, text, muted, line, act, onAct, feedback)`, `val LocalSignal = staticCompositionLocalOf<SignalPalette>`, `GuitarTheme(dark: Boolean, accent: Accent, content)` building the M3 scheme from tokens (primary=act, background=bg, surface=surface, etc.) AND providing `LocalSignal`. Feedback fallback: if accent==Teal → feedback=Blue. `TuningRepository`: `accent: Flow<String>`/`setAccent`, `tabOrder: Flow<String>`/`setTabOrder` (comma list). `AppState`: `accent` mutableState + setter (persisted), read at startup; MainActivity passes accent into `GuitarTheme`.
Recolor `GuitarColors` consumers minimally: map old roles onto Signal (primary→act, chordTone→feedback, scaleTone→blue `#8AA3FF`, background/surface per tokens) so ALL screens shift ground immediately even before their restructure tasks. Keep `GuitarColors` object as aliases to Signal values (so untouched call sites compile and inherit the palette).
Steps: implement → `.\gradlew.bat :app:assembleDebug` green → manual: app launches in Signal dark; light toggle OK → commit `feat(gui): Signal token theme + accent system (M1)`.

### Task 2: Web Signal tokens
Rewrite `style.css` `:root` (dark) + `:root.light` to the spec tokens; add accent override classes `:root[data-accent="amber"]` … setting `--primary`/`--act` pairs (default coral needs no attr). Update `theme.ts` `Colors` to the dark Signal values (it's read by canvases). `appState.ts`: `accent` persisted field + `setAccent` applying `document.documentElement.dataset.accent`. Keep every existing var name working (alias old names to new values) so untouched components inherit. tsc-valid; commit `feat(gui-web): Signal tokens + accent (M1)`.

## MILESTONE M2 — Shell: tabs + More

### Task 3: Android `Shell.kt` + MainActivity swap
`SignalTabBar(state)`: bottom bar, 5 slots = 4 user tabs + More; each = Material icon + 9sp label; selected tinted act. Icon map: Neck=`Icons.Outlined.GridOn` (or similar neck-like), Ear=`Icons.Outlined.Hearing`, Rhythm=`Icons.Outlined.GraphicEq`, Loop=`Icons.Outlined.Repeat`, Tuner=`Icons.Outlined.Speed`, Decompose=`Icons.Outlined.Extension`, More=`Icons.Outlined.MoreHoriz` — final picks may adjust to what exists in icons-extended; NO emoji. Tab model: `enum TabDest { Neck, Ear, Rhythm, Loop, Tuner, Decompose }` mapping onto the existing `Sheet` enum; order from `AppState.tabOrder` (default "Neck,Ear,Rhythm,Loop"). `MoreScreen(state)`: rows (icon+title+sub) → Tuner, Decompose, Challenge Stats (existing stats dialog/host), Settings. MainActivity: replace `NavRail(state)` with bottom `SignalTabBar` (portrait) / compact rail variant with the same 5 items (landscape) — reuse one item composable. Old NavRail removed. Verify every screen reachable. Build green; commit.

### Task 4: Web tab bar + More + icons.ts
`icons.ts`: `export function icon(name: IconName, size = 18): SVGElement` inline paths (stroke=currentColor). Replace the rail in `ui.ts` with a bottom tab bar (fixed, 5 items, act tint on active) on narrow viewports and keep a slim rail on wide viewports — SAME 4+More model, both built from one item factory. More = sheet listing Tuner/Decompose/Stats/Settings. Remove NAV emoji icons. tsc-valid; commit.

## MILESTONE M3 — Transport dock + Tone sheet

### Task 5: Android `Transport.kt` + wiring
`TransportDock(playing: Boolean, onPlayStop: () -> Unit, bpm: Int?, onBpm: ((Int) -> Unit)?, toneLabel: String, onTone: () -> Unit)` — pill: 40dp act circle with `Icons.Rounded.PlayArrow`/`Stop`, bpm text (tap → slider popover 10..300) hidden when bpm==null, spacer, tone chip (teal outline, label). `ToneSheet(state, onDismiss)` — ModalBottomSheet: Sound `SegmentedRow` (state.sound/setSound + loading), rows: EQ (hosts existing EQ sliders), Reverb (existing amount slider), Strum spread, Ring sustain, Boost root (switch, ear.earBoostTonic), Engine A/B (switch, state.useModernAudio). Contents MOVE from `AudioQuick.kt`/Options/Playback▾: after this task `AudioQuickButton` is deleted and its call sites (all screen headers) drop it; Ear's Playback▾ keeps ONLY Tempo (until Task 7 removes it for the dock) — simpler: Task 5 already wires the dock into Ear, Rhythm, Loop screens (bottom placement above tab bar) with each screen's play/stop+bpm, and deletes the per-screen Play/Stop+BPM+🎚 controls it replaces. Build green; manual: play/stop + BPM + tone sheet on all three screens; commit.

### Task 6: Web transport + tone sheet
Mirror Task 5 in `ui.ts` (+ per-screen UIs): shared `transportDock(opts)` element factory rendered by Ear/Rhythm/Loop views above the tab bar; `toneSheet()` bottom sheet with same rows bound to existing web state (sound, EQ, reverb, strum, sustain, boost, A/B). Spacebar keeps working (it calls the same state methods). Remove the 🎚 popup + per-screen play rows that moved. tsc-valid; commit.

## MILESTONE M4 — Screen restructures (Android then web)

### Task 7: Android Ear screen (practice + challenge)
Per spec: Practice/Challenge `SegmentedRow`; sub-mode chip row w/ "More ▾"; practice = reveal cards (act border on current bar) → action strip (← Prev · Next → · Hear 1–5–1 · → Loop as compact outlined buttons) → generator `SummaryCard` ("Diatonic · G Major? · Sevenths", tap → settings ModalBottomSheet hosting ALL of ProgressionSettings + generator dropdown + drill + level chips) → fretboard switch+panel (unchanged component). Challenge = progress ring (Canvas arc: act on line track, center "Q n/10") + dot strip (feedback/act/current) + answer pad grid (keys I..vii°, "7th ▾" expands extension row; replaces popup keyboard placement but reuses the same answer-commit logic) + tools row (Re-roll, Hear 1–5–1, Transpose) + header icon buttons Restart(`Icons.Rounded.RestartAlt`)/Quit(`Close`). Delete replaced inline controls. Keep ALL state calls identical. Build; manual practice+challenge flows; commit.

### Task 8: Android Rhythm + Loop + Tuner + Decompose + Neck chrome
Rhythm: Pattern/Mixer/Kit `SegmentedRow` swapping content sections (grid | mixer sliders | kit catalog); grid cell colors act(hit)/feedback(accent); legend banner (dismiss persisted in-memory); swing/metronome/zoom compact card. Loop: bar-lane chips (act border on playhead, dashed "+"), watch-on-neck default ON, now/next banner, arpeggio/×2 chips. Tuner: big note + Hz, needle arc act→feedback within ±5¢, cents row, string buttons, A4 control. Decompose + Neck: Signal chrome pass (chips, cards, icons; neck component untouched). Delete replaced controls. Build; commit (may be 2 commits: rhythm+loop, tuner+misc).

### Task 9: Web Ear screen — mirror Task 7 (same structure, CSS ring via conic-gradient, answer pad grid, keyboard 1–7 kept). Commit.

### Task 10: Web Rhythm + Loop + Tuner + Decompose + Neck chrome — mirror Task 8. Commit.

## MILESTONE M5 — Settings / personalization

### Task 11: Android Settings screen
Replace Options content with grouped list: **Personalize** (Theme dark/light/auto — extend existing dark pref with "auto" following system; Accent 5-swatch row; Tabs & order editor: 6 candidates, pick exactly 4, up/down reorder — writes `tab_order`; Left-handed), **Instrument** (existing tuning/instrument content), **everything else** that remains from old Options minus rows that moved to Tone. Reachable from More. Build; commit.

### Task 12: Web settings — mirror (accent swatches apply `data-accent`; tabs editor; theme select). Commit.

## MILESTONE M6 — Sweep + parity audit

### Task 13: Emoji sweep + reachability audit (both platforms)
`grep -rnP "[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}]" app/src/main/kotlin chorect-web/src` → replace every UI-string hit with icons/text (allowed: ← → ▾ – ♪? NO: ♪ is a text dingbat — spec allows musical text glyphs in labels; keep ♪ only in tone chip if it renders consistently, else drop). Verify feature map: every v1.29 control has a home (checklist in spec acceptance). Fix stragglers. Build both; commit.

## MILESTONE M7 — Ship v2.0.0

### Task 14: Release
Bump `versionCode = 20000` / `"2.0.0"` + web `"2.0.0"`; archive `Chorect_beta_V1.29.0.apk` to `releases/`; `.\gradlew.bat :theory:test :audio:test :app:assembleDebug`; merge `signal-redesign` → main (ff or merge), push, `gh workflow run "Deploy web to GitHub Pages" --ref main` + watch; update project-state + gui_redesign memories.

---

## Self-Review
Spec coverage: tokens/accent→T1/T2; tabs+More→T3/T4; dock+Tone→T5/T6; Ear/Challenge→T7/T9; Rhythm/Loop/Tuner/Decompose/Neck-chrome→T8/T10; Personalize/settings→T11/T12; no-emoji + reachability→T13 (and per-task); ship→T14. Neck-keep + logic-untouched are global constraints. Deferred items excluded. Interfaces named once (SignalPalette/LocalSignal, TabDest, TransportDock, ToneSheet, icons.ts) and referenced consistently. Screen tasks specify structure + deletions + acceptance rather than full code by design — implementers read the spec + existing screen code (this is a restyle of existing render code, not new logic).

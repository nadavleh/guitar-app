// The app UI, ported from app/.../{MainActivity,AppShell,Screens,TunerScreen,
// AudioQuick}.kt. Vanilla DOM, re-rendered on each state change. The fretboard
// <canvas> element is persistent across renders so its zoom/pan survives.

import {
  AppState, DisplayMode, LabelMode, Sheet, ChordScaleView, DISPLAY_FRETS, APP_VERSION, TabDestName, ALL_TAB_DESTS,
  ThemeMode, ALL_THEME_MODES, AccentName, ALL_ACCENTS,
} from "./appState";
import { icon, IconName } from "./icons";
import { renderChallengeStatsOverlay } from "./statsOverlay";
import { speak } from "./speech";
import { FretboardCanvas, FretboardData } from "./fretboardCanvas";
import { inputDispatchReport } from "./inputLatencyProbe";
import { computeMarks, scaleInfo, intervalName, shapeMarks } from "./marks";
import { TunerState } from "./tunerState";
import { EarTrainingState, EarSubMode, EarMode } from "./earTrainingState";
import { EarTrainingUI } from "./earTrainingUI";
import { SambaLooperState } from "./sambaLooperState";
import { BlocksState } from "./blocksState";
import { SambaLooperUI } from "./sambaLooperUI";
import { DecomposeUI } from "./decomposeUI";
import { LoopState } from "./loopState";
import { LoopUI } from "./loopUI";
import { CavaqProgState } from "./cavaqProgState";
import { CavaqProgUI } from "./cavaqProgUI";
import { CagedTrainerState } from "./cagedTrainerState";
import { CagedTrainerUI } from "./cagedTrainerUI";
import { RhythmUnitState } from "./rhythmUnitState";
import { RhythmPhraseState } from "./rhythmPhraseState";
import { RhythmUnitsUI } from "./rhythmUnitsUI";
import { MetronomeState } from "./metronomeState";
import { MetronomeUI } from "./metronomeUI";
import { TheoryUI } from "./theoryUI";
import { SongsUI } from "./songsUI";
import { loadDrumSample } from "./drumSamples";
import { Timbres } from "../audio";
import { Colors, withAlpha } from "./theme";
import { el, clear, btn, segmented, chipRow, valueSlider, switchRow, labelSm } from "./dom";
import { toneSheet } from "./transport";
import {
  PC, Instrument, InstrumentInfo, ChordShape, ScalePosition, VoicingStyle,
  spellPc, spellNote, midiPitchClass, midiOctave, noteAt, stringCount, suggestFingering,
  parseChord, scaleNotesFrom, notesFrom, SCALES, parsePitchClass, scalePositionsFor,
} from "../theory";
import * as Tunings from "../theory/tunings";

const PITCH_CLASS_ROW = [PC.C, PC.Cs, PC.D, PC.Ds, PC.E, PC.F, PC.Fs, PC.G, PC.Gs, PC.A, PC.As, PC.B];
const COMMON_QUALITY_SYMBOLS = ["", "m", "7", "maj7", "m7", "dim", "aug", "sus4", "sus2", "6", "m6", "m7b5", "dim7", "9", "add9", "13"];
const qualityLabel = (sym: string) => (sym === "" ? "major" : sym);

// 4+More shell (Signal M2/T4): the bottom tab bar (narrow) and left rail (wide)
// render the SAME 4 user-configurable tabs + a fixed "More" item from one item
// factory (see App.navItem/renderNav below) — mirrors Android's TabDest enum +
// SignalTabBar/SignalTabRail (Shell.kt) exactly, just as chrome over the same
// Sheet-based routing.
const TAB_SHEET: Record<TabDestName, Sheet> = {
  Neck: Sheet.Fretboard,
  Ear: Sheet.EarTraining,
  Rhythm: Sheet.SambaLooper,
  Loop: Sheet.Loop,
  Tuner: Sheet.Tuner,
  Decompose: Sheet.Decompose,
  CavaqProgressions: Sheet.CavaqProgressions,
  RhythmUnits: Sheet.RhythmUnits,
  Metronome: Sheet.Metronome,
  ScalesTriads: Sheet.ScalesTriads,
  Theory: Sheet.Theory,
  Songs: Sheet.Songs,
};
const TAB_ICON: Record<TabDestName, IconName> = {
  Neck: "neck", Ear: "ear", Rhythm: "rhythm", Loop: "loop", Tuner: "tuner", Decompose: "decompose", CavaqProgressions: "note", RhythmUnits: "rhythmNotes", Metronome: "timer", ScalesTriads: "neck", Theory: "book", Songs: "note",
};
const TAB_LABEL: Record<TabDestName, string> = {
  Neck: "Fretboard", Ear: "Ear", Rhythm: "DrumLoop", Loop: "Loop", Tuner: "Tuner", Decompose: "Decompose", CavaqProgressions: "Progressions", RhythmUnits: "Rhythm", Metronome: "Metronome", ScalesTriads: "Practice", Theory: "Theory", Songs: "Songs",
};
/** One-line description shown under each destination's title in the More sheet. */
const TAB_SUBTITLE: Record<TabDestName, string> = {
  Neck: "Fretboard — chords, scales & pick mode",
  Ear: "Progression, interval & chord ear training",
  Rhythm: "Samba percussion drum-machine looper",
  Loop: "Chord-progression looper",
  Tuner: "Chromatic tuner with cents needle",
  Decompose: "Chord-tone breakdown reference",
  CavaqProgressions: "Cavaquinho functional sequences — looper + neck",
  RhythmUnits: "Learn & train basic rhythmic units",
  Metronome: "Click track with selectable time signatures",
  ScalesTriads: "Guitar practice - CAGED scale boxes & triad inversions",
  Theory: "Interval song references & reference sheets — expanding",
  Songs: "Your chord sheets — view, transpose, show degrees",
};

/** Whether a tab destination is available for the current instrument (mirrors
 *  Android's TabDest.availableFor).
 *
 *  CavaqProgressions is deliberately NOT gated: the screen's value is the functional
 *  progressions themselves, and its voicings follow `liveTuning` (4-string tunings get
 *  the cavaquinho pool, anything else the CAGED generator), so it reads fine on guitar.
 *  Nadav works mostly in guitar mode and wants to glance at those progressions without
 *  switching instrument and back. */
function availableFor(dest: TabDestName, instrument: Instrument): boolean {
  if (dest === "ScalesTriads") return instrument === Instrument.Guitar;
  return true;
}

// Settings → Personalize (Signal T12): Theme Auto resolution + accent swatches.

/** Live OS/browser theme-preference query, polled once via `.matches` and
 *  subscribed to below (App constructor) so an "Auto" theme mode re-renders
 *  immediately when the system flips light/dark — no reload needed. */
const prefersDarkMQL = window.matchMedia("(prefers-color-scheme: dark)");

/** Resolve a persisted Theme mode to the boolean `render()` needs for its
 *  `.light` class toggle: Light is always light, Dark is always dark, Auto
 *  follows `prefersDarkMQL` live (mirrors Android's MainActivity theme
 *  resolution: auto → system, light → false, dark → true). */
function isLightFor(mode: ThemeMode): boolean {
  if (mode === "Light") return true;
  if (mode === "Dark") return false;
  return !prefersDarkMQL.matches;
}

/** Settings → Personalize's 5 accent swatches: each [AccentName]'s dark-theme
 *  hex (style.css `[data-accent]` overrides' `--act` values) — the palette
 *  always shows dark swatches, paint-chip style, even in light theme. */
const ACCENT_SWATCH_HEX: Record<AccentName, string> = {
  coral: "#FF5C57",
  amber: "#FFB454",
  teal: "#3DDCC8",
  blue: "#8AA3FF",
  purple: "#C98ADF",
};

export class App {
  private railEl = el("div", { class: "nav-rail" });
  private tabbarEl = el("div", { class: "tabbar" });
  private contentEl = el("div", { class: "content" });
  private sheetLayer = el("div", {});

  /** Transient (unpersisted) "More" overlay state — mirrors Android's
   *  AppState.moreOpen: purely a chrome flag, independent of currentSheet
   *  routing, so More can be reached from any screen. */
  private moreOpen = false;
  /** Challenge-stats popup stacked on top of the More sheet. */
  private moreStatsOpen = false;

  private fretCanvasEl = el("canvas", { class: "fretboard" });
  private fretboard: FretboardCanvas;

  /** Play-mode quick-chord chips: edit toggle + which slot's rename popup is open. */
  private editChordSlots = false;
  private editingChordSlot = -1;

  private tuner: TunerState | null = null;
  private tunerDialCanvas: HTMLCanvasElement | null = null;
  private tunerNoteEl: HTMLElement | null = null;
  private tunerHzEl: HTMLElement | null = null;
  private tunerCentsEl: HTMLElement | null = null;
  private tunerHintEl: HTMLElement | null = null;
  private tunerRefBtns: HTMLButtonElement[] = [];

  private toneSheetOpen = false;

  /** Settings → Personalize's Tabs & order editor: the local pending pick,
   *  distinct from the committed `state.tabOrder` so unchecking one of the 4
   *  can "free a slot" (3 items) without ever pushing an invalid <4 set to the
   *  live tab bar — mirrors Android's TabOrderEditor `remember(state.tabOrder)`
   *  local state. `null` means "mirror state.tabOrder directly" (no pending
   *  edit); reset to `null` whenever the Settings sheet isn't showing, so a
   *  transient 3-item edit is dropped on dismiss, same as Android. */
  private tabOrderPending: TabDestName[] | null = null;

  private ear: EarTrainingState;
  private earUI: EarTrainingUI;
  private samba: SambaLooperState;
  private drumBlocks: BlocksState;
  private sambaUI: SambaLooperUI;
  private decomposeUI: DecomposeUI;
  private loop: LoopState;
  private loopUI: LoopUI;
  private cavaq: CavaqProgState;
  private cavaqUI: CavaqProgUI;
  private caged: CagedTrainerState;
  private cagedUI: CagedTrainerUI;
  private rhythmUnits: RhythmUnitState;
  private rhythmPhrase: RhythmPhraseState;
  private rhythmUnitsUI: RhythmUnitsUI;
  private metronome: MetronomeState;
  private metronomeUI: MetronomeUI;
  private theoryUI: TheoryUI;
  private songsUI: SongsUI;

  constructor(private state: AppState, root: HTMLElement) {
    this.fretboard = new FretboardCanvas(this.fretCanvasEl);
    this.ear = new EarTrainingState({
      audio: state.audio,
      // Ear training is ALWAYS a guitar exercise: use the live tuning only on guitar,
      // else fall back to standard guitar tuning so the cavaquinho's 4-string DGBD
      // never drives ear-training voicings. Cavaco support may come later.
      tuningProvider: () => (state.instrument === Instrument.Guitar ? state.liveTuning : Tunings.standard),
      sustainProvider: () => state.ringSustainMs,
      strumProvider: () => state.strumMs,
      onChange: () => this.scheduleRender(),
      onProgressionChallengeComplete: (s, t, d) => state.recordChallengeScore(s, t, d),
      onChallengeComplete: (kind, s, t, d) => state.recordChallengeScore(s, t, d, kind),
      onProgressionMistake: (k) => state.recordProgressionMistake(k),
      progressionMistakesProvider: () => state.progressionMistakes,
      speak,
    });
    this.loop = new LoopState({
      audio: state.audio,
      tuningProvider: () => state.liveTuning,
      voicingStyleProvider: () => state.voicingStyle,
      maxFretSpanProvider: () => InstrumentInfo[state.instrument].maxFretSpan,
      strumProvider: () => state.strumMs,
      sustainProvider: () => state.ringSustainMs,
      timbreProvider: () => (state.instrument === Instrument.Cavaquinho ? Timbres.Cavaquinho : Timbres.Guitar),
      onChange: () => this.scheduleRender(),
    });
    this.loopUI = new LoopUI(this.loop, state, this.ear, () => state.closeSheet());
    this.earUI = new EarTrainingUI(this.ear, state, () => state.closeSheet(), (symbols) => {
      this.loop.loadProgressionIntoLoop(symbols);
      state.openSheet(Sheet.Loop);
    });
    this.samba = new SambaLooperState({
      audio: state.audio,
      onChange: () => this.scheduleRender(),
      // Per-tick: just repaint the playhead classes — NO full DOM rebuild — so
      // mouse-wheel / touch scrolling stays smooth while the loop plays.
      onPlayhead: () => this.sambaUI?.paintPlayhead(),
      getSaved: () => state.drumPatterns,
      save: (name, enc) => state.saveDrumPattern(name, enc),
      del: (name) => state.deleteDrumPattern(name),
      loadSample: (inst, voice) => loadDrumSample(state.audio, inst, voice),
      getVolumes: () => state.drumVolumes,
      saveVolume: (key, value) => state.setDrumVolume(key, value),
    });
    this.drumBlocks = new BlocksState({
      audio: state.audio,
      onChange: () => this.scheduleRender(),
      getSaved: () => state.drumBlocks,
      save: (name, enc) => state.saveDrumBlock(name, enc),
      del: (name) => state.deleteDrumBlock(name),
      getTrackPresets: () => state.drumTrackPresets,
      saveTrackPreset: (name, enc) => state.saveDrumTrackPreset(name, enc),
      delTrackPreset: (name) => state.deleteDrumTrackPreset(name),
      loadSample: (inst, voice) => loadDrumSample(state.audio, inst, voice),
    });
    this.sambaUI = new SambaLooperUI(this.samba, this.drumBlocks, state, this.ear, () => state.closeSheet());
    this.cavaq = new CavaqProgState({
      audio: state.audio,
      tuningProvider: () => state.liveTuning,
      sustainProvider: () => state.ringSustainMs,
      strumProvider: () => state.strumMs,
      timbreProvider: () => (state.instrument === Instrument.Cavaquinho ? Timbres.Cavaquinho : Timbres.Guitar),
      onChange: () => this.scheduleRender(),
    });
    this.cavaqUI = new CavaqProgUI(state, this.cavaq);
    this.caged = new CagedTrainerState(state.audio, () => this.scheduleRender());
    this.cagedUI = new CagedTrainerUI(state, this.caged);
    this.rhythmUnits = new RhythmUnitState({ audio: state.audio, onChange: () => this.scheduleRender() });
    this.rhythmPhrase = new RhythmPhraseState({ audio: state.audio, onChange: () => this.scheduleRender() });
    this.rhythmUnitsUI = new RhythmUnitsUI(this.rhythmUnits, this.rhythmPhrase, () => state.closeSheet(), () => this.scheduleRender());
    this.metronome = new MetronomeState({ audio: state.audio, onChange: () => this.scheduleRender() });
    this.metronomeUI = new MetronomeUI(this.metronome, () => state.closeSheet(), () => this.scheduleRender());
    this.theoryUI = new TheoryUI(this.ear, () => state.closeSheet(), () => this.scheduleRender());
    this.songsUI = new SongsUI();
    this.decomposeUI = new DecomposeUI(state, this.ear, () => state.closeSheet(), (symbols) => {
      this.loop.loadProgressionIntoLoop(symbols);
      state.openSheet(Sheet.Loop);
    });
    // Quick light/dark toggle (the full Dark/Light/Auto control lives in
    // Settings -> Personalize). Shows the mode you'd switch TO — mirrors
    // MainActivity's IconButton exactly: sun while dark (tap -> Light), moon
    // while light/auto (tap -> Dark). Self-contained: it swaps its own icon on
    // click rather than waiting for a render() pass, since the header is built
    // once here and isn't touched by the state-driven re-render loop.
    const themeToggleBtn = el("button", {
      class: "btn icon",
      "aria-label": "Toggle theme",
      title: "Toggle theme",
    });
    const refreshThemeToggleIcon = () => {
      clear(themeToggleBtn);
      themeToggleBtn.appendChild(icon(state.themeMode === "Dark" ? "sun" : "moon", 18));
    };
    refreshThemeToggleIcon();
    themeToggleBtn.addEventListener("click", () => {
      state.setThemeMode(state.themeMode === "Dark" ? "Light" : "Dark");
      refreshThemeToggleIcon();
    });
    const shortcutsBtn = el("button", {
      title: "Keyboard shortcuts (?)",
      "aria-label": "Keyboard shortcuts",
      style: "background:transparent;border:none;color:var(--text-primary);font-size:18px;cursor:pointer;padding:4px 8px",
    }, ["⌨"]);
    shortcutsBtn.addEventListener("click", () => this.toggleShortcuts());
    const header = el("div", { class: "app-header" }, [
      el("span", { class: "app-brand" }, ["chorect"]),
      el("span", { style: "font-size:11px;opacity:0.6;margin-left:6px;align-self:flex-end;padding-bottom:2px" }, [`v${APP_VERSION}`]),
      el("span", { class: "app-byline" }, [
        "made by ",
        el("a", {
          class: "app-byline-link",
          href: "https://www.instagram.com/nadavileh",
          target: "_blank",
          rel: "noopener",
        }, ["@nadavileh"]),
      ]),
      el("span", { class: "spacer" }),
      shortcutsBtn,
      themeToggleBtn,
    ]);
    const appRoot = el("div", { class: "app-root" }, [this.railEl, this.contentEl]);
    const shell = el("div", { class: "app-shell" }, [header, appRoot]);
    root.appendChild(shell);
    root.appendChild(this.tabbarEl);
    root.appendChild(this.sheetLayer);
    this.setupPressGuard();
    this.setupMarquee();
    this.setupSpacebarShortcut();
    state.subscribe(() => this.scheduleRender());
    // Theme mode "Auto" tracks the system live (see isLightFor/render()).
    prefersDarkMQL.addEventListener("change", () => {
      if (this.state.themeMode === "Auto") this.scheduleRender();
    });
    this.render();
    // Deep link: #EarTraining / #Tuner / #Options / … opens that tool on load.
    const hash = location.hash.replace("#", "");
    if (hash && (Object.values(Sheet) as string[]).includes(hash)) state.openSheet(hash as Sheet);
  }

  // ---------- derived ----------

  private chordShapes(): ChordShape[] {
    const parsed = parseChord(this.state.chordInput);
    if (this.state.displayMode !== DisplayMode.Chord || !parsed) return [];
    return this.state.chordGenerator().shapesFor(parsed[0], parsed[1], this.state.liveTuning, DISPLAY_FRETS).slice(0, 12);
  }

  private scalePositions(): ScalePosition[] {
    if (this.state.displayMode !== DisplayMode.Scale) return [];
    const sc = scaleInfo(this.state);
    if (!sc) return [];
    return scalePositionsFor(sc.root, sc.scale, this.state.liveTuning, DISPLAY_FRETS);
  }

  // ---------- render ----------

  private rendering = false;
  // Press guard + render coalescing: never rebuild the DOM while a pointer is down
  // (so the element being pressed survives until release and its click completes),
  // and batch rapid state changes — e.g. a playing loop's per-tick updates — into at
  // most one render per animation frame.
  private pressActive = false;
  private pendingRender = false;
  private rafScheduled = false;

  private setupPressGuard(): void {
    // Capture phase so these run before any element's own handler.
    window.addEventListener("pointerdown", (e) => {
      this.pressActive = true;
      // Any open <details> popover (transport BPM / EQ) closes when you tap
      // outside it — the general "click elsewhere dismisses the popup" rule.
      const t = e.target as Node;
      document.querySelectorAll<HTMLDetailsElement>("details.transport-bpm-wrap[open], details.tone-eq-wrap[open]")
        .forEach((d) => { if (!d.contains(t)) d.open = false; });   // toggle event syncs the persisted flag
    }, true);
    const release = () => {
      if (!this.pressActive) return;
      this.pressActive = false;
      // Defer to the next frame so a button's click (which fires right after pointerup)
      // lands on the still-live element before any rebuild replaces it.
      if (this.pendingRender) this.scheduleRender();
    };
    window.addEventListener("pointerup", release, true);
    window.addEventListener("pointercancel", release, true);
  }

  /** App-wide rectangle selection (Windows-desktop style): RIGHT-drag anywhere
   *  on the screen draws a translucent rubber band; elements that tag themselves
   *  as selectable (currently the drum grid's cells, via data-sect/track/slot)
   *  get marked as it passes over them. A plain right-click (no drag) keeps its
   *  usual meaning — clearing a grid cell, or the browser's own menu elsewhere;
   *  text fields are exempt entirely so their native menus keep working. More
   *  selectable element kinds can join later by tagging themselves the same way. */
  private setupMarquee(): void {
    let startX = 0, startY = 0;
    let active = false, dragging = false;
    let startedOnCell: HTMLElement | null = null;
    let marquee: HTMLElement | null = null;
    let suppressMenu = false;

    document.addEventListener("pointerdown", (e) => {
      if (e.button !== 2) return;
      const t = e.target as HTMLElement;
      if (t.closest("input, textarea, [contenteditable]")) return;
      active = true;
      dragging = false;
      startX = e.clientX;
      startY = e.clientY;
      startedOnCell = t.closest<HTMLElement>(".drum-cell[data-slot]");
    }, true);

    document.addEventListener("pointermove", (e) => {
      if (!active) return;
      if (!dragging && Math.abs(e.clientX - startX) + Math.abs(e.clientY - startY) < 5) return;
      dragging = true;
      if (!marquee) {
        marquee = document.createElement("div");
        marquee.className = "drum-marquee";
        document.body.appendChild(marquee);
      }
      const left = Math.min(startX, e.clientX), top = Math.min(startY, e.clientY);
      const width = Math.abs(e.clientX - startX), height = Math.abs(e.clientY - startY);
      marquee.style.left = `${left}px`;
      marquee.style.top = `${top}px`;
      marquee.style.width = `${width}px`;
      marquee.style.height = `${height}px`;
      this.sambaUI.marqueeSelect({ left, top, right: left + width, bottom: top + height });
    }, true);

    document.addEventListener("pointerup", (e) => {
      if (e.button !== 2 || !active) return;
      active = false;
      marquee?.remove();
      marquee = null;
      if (dragging) suppressMenu = true;
      else if (startedOnCell && this.sambaUI.rightClickCellOrMenu(startedOnCell, e.clientX, e.clientY)) suppressMenu = true;
      startedOnCell = null;
    }, true);

    // Only swallow the browser context menu when the right button actually did
    // something (a drag-select, or a cell clear).
    document.addEventListener("contextmenu", (e) => {
      if (suppressMenu) { e.preventDefault(); suppressMenu = false; }
    }, true);

    // Safety net: a left-drag of a cell selection (item 2) that is released off
    // the grid never hits a cell's pointerup — finalize it here (bubble phase, so
    // a release over a cell is handled by that cell first and this is a no-op).
    document.addEventListener("pointerup", (e) => {
      if (e.button === 0) this.sambaUI.finishMoveDrag();
    });
  }

  /** Physical-keyboard shortcuts (web). Space toggles play/stop; arrows/number keys
   *  navigate the active screen (positions, progression, quick-chord slots); "?" opens
   *  the shortcuts help. Ignored while typing in an input. The ear-training CHALLENGE
   *  answer pad has its own always-attached handler (EarTrainingUI.attachChallengeKeys),
   *  so we skip Ear-progression arrows while a challenge is in flight to avoid clashes. */
  private setupSpacebarShortcut(): void {
    document.addEventListener("keydown", (e) => {
      if (e.repeat) return;
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.tagName === "SELECT" || t.isContentEditable)) return;

      // "?" (Shift+/) toggles the shortcuts help from anywhere.
      if (e.key === "?") { e.preventDefault(); this.toggleShortcuts(); return; }

      const sheet = this.state.currentSheet;
      const digit = e.key >= "1" && e.key <= "9" ? Number(e.key) - 1 : -1;

      // Ctrl/Cmd-Z undoes the last drum-machine edit.
      if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key.toLowerCase() === "z" && sheet === Sheet.SambaLooper) {
        e.preventDefault();
        this.samba.undo();
        this.scheduleRender();
        return;
      }
      // Ctrl/Cmd-C copies a right-drag cell selection (falls through to the
      // native text copy when no cells are selected); Ctrl/Cmd-V pastes the
      // copied strikes at the hovered cell.
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "c" && sheet === Sheet.SambaLooper) {
        if (this.sambaUI.copySelection()) { e.preventDefault(); return; }
      }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "v" && sheet === Sheet.SambaLooper) {
        if (this.sambaUI.pasteAtHover()) { e.preventDefault(); return; }
      }
      // Ctrl/Cmd-X cuts the selection (copy + blank the region).
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "x" && sheet === Sheet.SambaLooper) {
        if (this.sambaUI.cutSelection()) { e.preventDefault(); return; }
      }

      if (e.code === "Space") {
        if (sheet === Sheet.SambaLooper) { e.preventDefault(); this.sambaUI.togglePlay(); }
        else if (sheet === Sheet.Loop) { e.preventDefault(); if (this.loop.isLooping) this.loop.stopLoop(); else this.loop.startLoop(); }
        // Car mode excluded: it drives its own clock, and startLoop() here would run the
        // endless practice looper alongside it (two clocks, doubled chords).
        else if (sheet === Sheet.EarTraining && this.ear.progSubMode === EarSubMode.Progression && this.ear.earMode !== EarMode.Car) { e.preventDefault(); if (this.ear.isLooping) this.ear.stopLoop(); else this.ear.startLoop(); }
        else if (sheet === Sheet.CavaqProgressions) { e.preventDefault(); this.cavaq.toggle(); }
        else if (sheet === Sheet.RhythmUnits) { e.preventDefault(); this.rhythmUnits.toggle(); }
        else if (sheet === Sheet.Metronome) { e.preventDefault(); this.metronome.toggle(); }
        else if (sheet === Sheet.ScalesTriads) { e.preventDefault(); if (this.caged.section === "scales" && this.caged.tab === "challenge") this.caged.nextChallenge(); else if (this.caged.section === "triads" || this.caged.tab !== "explore") this.caged.toggle(); }
        return;
      }

      // Cavaquinho Progressions: ←/→ neck position, ↑/↓ transpose, digits play a chord.
      if (sheet === Sheet.CavaqProgressions) {
        if (e.key === "ArrowLeft") { e.preventDefault(); this.cavaq.nudgePosition(-1); }
        else if (e.key === "ArrowRight") { e.preventDefault(); this.cavaq.nudgePosition(1); }
        else if (e.key === "ArrowUp") { e.preventDefault(); this.cavaq.shiftKey(1); }
        else if (e.key === "ArrowDown") { e.preventDefault(); this.cavaq.shiftKey(-1); }
        else if (digit >= 0 && digit < this.cavaq.resolved.length) { e.preventDefault(); this.cavaq.playBar(digit); }
        return;
      }

      // Ear-training PRACTICE progression: ←/→ prev/next, digits play a bar.
      // (Challenge bar navigation lives in EarTrainingUI's own handler.)
      if (sheet === Sheet.EarTraining && this.ear.progSubMode === EarSubMode.Progression &&
          !this.ear.challengeActive && this.ear.earMode !== EarMode.Car) {
        const adv = this.ear.specialProgMode;
        if (e.key === "ArrowLeft") { e.preventDefault(); if (adv) this.ear.previousAdvancedProgression(); else this.ear.previousProgression(); }
        else if (e.key === "ArrowRight") { e.preventDefault(); if (adv) this.ear.nextAdvancedProgression(); else this.ear.nextProgression(); }
        else if (digit >= 0 && digit < this.ear.progResolved.length) { e.preventDefault(); this.ear.playBarOnce(digit); }
        return;
      }

      // Play mode (Neck screen, Pick display): ←/→ cycle quick-chord slots, digits apply.
      if ((sheet === null || sheet === Sheet.Fretboard) && this.state.displayMode === DisplayMode.Pick) {
        const n = this.state.chordSlots.length;
        if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
          e.preventDefault();
          const cur = this.state.activeChordSlot < 0 ? 0 : this.state.activeChordSlot;
          this.state.applyChordSlot(((cur + (e.key === "ArrowRight" ? 1 : -1)) % n + n) % n);
        } else if (digit >= 0 && digit < n) { e.preventDefault(); this.state.applyChordSlot(digit); }
        return;
      }
    });
  }

  /** Toggle the keyboard-shortcuts help popup. Lists the bindings for the active screen. */
  private shortcutsScrim: HTMLElement | null = null;
  private toggleShortcuts(): void {
    if (this.shortcutsScrim) { this.shortcutsScrim.remove(); this.shortcutsScrim = null; return; }
    const sheet = this.state.currentSheet;
    const rows: [string, string][] = [["?", "Show / hide this help"]];
    if (sheet === Sheet.CavaqProgressions) {
      rows.push(["Space", "Play / stop"], ["← →", "Previous / next neck position"],
        ["↑ ↓", "Transpose up / down a semitone"], ["1–9", "Play that chord"]);
    } else if (sheet === Sheet.EarTraining && this.ear.progSubMode === EarSubMode.Progression) {
      rows.push(["Space", "Play / stop the progression"]);
      if (this.ear.challengeActive) rows.push(["← →", "Select previous / next bar (and play it)"], ["1–7", "Answer the selected bar"], ["Enter", "Commit (extension mode)"], ["Esc", "Cancel pending answer"]);
      else rows.push(["← →", "Previous / next progression"], ["1–4", "Play that bar"]);
    } else if (sheet === Sheet.SambaLooper || sheet === Sheet.Loop) {
      rows.push(["Space", "Play / stop the loop"]);
    } else if (sheet === null || sheet === Sheet.Fretboard) {
      rows.push(["← →", "Previous / next quick-chord slot (Play mode)"], ["1–8", "Apply that quick-chord slot"]);
    }
    const body = el("div", {}, rows.map(([k, d]) => el("div", { style: "display:flex;gap:12px;padding:3px 0;font-size:14px" }, [
      el("kbd", { style: "min-width:56px;font-weight:700;font-family:monospace" }, [k]),
      el("span", { style: "flex:1;color:var(--text-secondary)" }, [d]),
    ])));
    const close = btn("Close", () => this.toggleShortcuts(), "btn primary");
    const card = el("div", { class: "et-card", style: "max-width:420px;margin:auto;background:var(--surface-elev);color:var(--text-primary)" }, [
      el("div", { style: "font-weight:700;font-size:16px;margin-bottom:8px" }, ["⌨  Keyboard shortcuts"]),
      body,
      el("div", { style: "text-align:right;margin-top:10px" }, [close]),
    ]);
    card.addEventListener("click", (e) => e.stopPropagation());
    const scrim = el("div", { style: "position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;padding:16px;z-index:70" }, [card]);
    scrim.addEventListener("click", () => this.toggleShortcuts());
    document.body.appendChild(scrim);
    this.shortcutsScrim = scrim;
  }

  /** Coalesced, press-aware render request. Prefer this over render() for state changes. */
  private scheduleRender(): void {
    if (this.pressActive) { this.pendingRender = true; return; }
    if (this.rafScheduled) return;
    this.rafScheduled = true;
    requestAnimationFrame(() => {
      this.rafScheduled = false;
      this.pendingRender = false;
      this.render();
    });
  }

  private render(): void {
    // Guard against re-entrant renders (a state mutation firing onChange mid-render).
    if (this.rendering) return;
    this.rendering = true;
    // Theme: the light palette is a :root.light override of the CSS variables.
    // themeMode Dark/Light is static; Auto resolves against the live system
    // preference (isLightFor / prefersDarkMQL, listened for above).
    document.documentElement.classList.toggle("light", isLightFor(this.state.themeMode));
    // ACT accent: style.css `[data-accent="..."]` overrides; coral (default) has none.
    if (this.state.accent === "coral") delete document.documentElement.dataset.accent;
    else document.documentElement.dataset.accent = this.state.accent;
    try {
      this.renderInner();
    } finally {
      this.rendering = false;
    }
  }

  private renderInner(): void {
    const route = this.state.currentSheet;
    const isTuner = route === Sheet.Tuner;
    if (!isTuner && this.tuner) {
      this.tuner.stop();
      this.tuner = null;
    }
    // Leaving the Ear screen halts its looper but keeps all state (Kotlin DisposableEffect).
    // NOT gated on isLooping: the car driver clears that flag before its silent
    // auto-advance gap, so a gated check let the chain survive a tab change and then draw
    // a new progression while another tool was on screen. Android's onDispose is
    // unconditional for the same reason.
    if (route !== Sheet.EarTraining) {
      if (this.ear.earMode === EarMode.Car) this.earUI.leaveCarMode();
      else if (this.ear.isLooping) this.ear.stopLoop();
    }
    if (route !== Sheet.SambaLooper && this.samba.isPlaying) this.samba.stop();
    if (route !== Sheet.SambaLooper && this.drumBlocks.isPlaying) this.drumBlocks.stop();
    if (route !== Sheet.CavaqProgressions && this.cavaq.isPlaying) this.cavaq.stop();
    if (route !== Sheet.RhythmUnits && this.rhythmUnits.isPlaying) this.rhythmUnits.stop();
    if (route !== Sheet.RhythmUnits && this.rhythmPhrase.isPlaying) this.rhythmPhrase.stop();
    if (route !== Sheet.Metronome && this.metronome.isPlaying) this.metronome.stop();
    if (route !== Sheet.ScalesTriads && this.caged.isPlaying) this.caged.stop();

    this.renderNav();
    // Preserve the scroll position of any long scrollable pane across full rebuilds.
    const prevScroll = this.contentEl.querySelector(".et-scroll")?.scrollTop ?? 0;
    clear(this.contentEl);

    if (route === Sheet.Tuner) this.renderTuner();
    else if (route === Sheet.Loop) this.loopUI.render(this.contentEl);
    else if (route === Sheet.EarTraining) this.earUI.render(this.contentEl);
    else if (route === Sheet.SambaLooper) this.sambaUI.render(this.contentEl);
    else if (route === Sheet.Decompose) this.decomposeUI.render(this.contentEl);
    else if (route === Sheet.CavaqProgressions) this.cavaqUI.render(this.contentEl);
    else if (route === Sheet.RhythmUnits) this.rhythmUnitsUI.render(this.contentEl);
    else if (route === Sheet.Metronome) this.metronomeUI.render(this.contentEl);
    else if (route === Sheet.ScalesTriads) this.cagedUI.render(this.contentEl);
    else if (route === Sheet.Theory) this.theoryUI.render(this.contentEl);
    else if (route === Sheet.Songs) this.songsUI.render(this.contentEl);
    else this.renderFretboardView();

    const newScroll = this.contentEl.querySelector(".et-scroll");
    if (newScroll) newScroll.scrollTop = prevScroll;

    this.renderOverlays(route);
  }

  // ---------- nav: 4 tabs + More, shared by the wide rail and narrow tab bar ----------

  /** One tab is "selected" when it's the open sheet; the bare Fretboard screen
   *  (currentSheet == null but the neck is lit) counts as the Neck tab being
   *  selected — mirrors Android's Shell.kt isTabSelected. */
  private isTabSelected(dest: TabDestName): boolean {
    const sheet = this.state.currentSheet;
    if (sheet !== null) return sheet === TAB_SHEET[dest];
    return dest === "Neck";
  }

  /** One rail/tab-bar item. A fresh element every call — the same item is
   *  independently built for the rail and the tab bar (a DOM node can only
   *  live in one parent), so renderNav() calls this twice per destination. */
  private navItem(iconName: IconName, label: string, active: boolean, onClick: () => void): HTMLElement {
    const b = el("button", { class: active ? "rail-btn active" : "rail-btn" }, [
      icon(iconName),
      el("span", { class: "label" }, [label]),
    ]);
    b.addEventListener("click", onClick);
    return b;
  }

  private renderNav(): void {
    clear(this.railEl);
    clear(this.tabbarEl);
    // Show the ENTIRE menu directly (no "More" overflow on web): the user's tab order
    // first, then every remaining destination available for the current instrument,
    // and finally Stats + Settings (which used to live inside More).
    const ordered = [...this.state.tabOrder, ...ALL_TAB_DESTS.filter((d) => !this.state.tabOrder.includes(d))]
      .filter((d) => availableFor(d, this.state.instrument));
    const add = (icon: IconName, label: string, active: boolean, onClick: () => void) => {
      this.railEl.appendChild(this.navItem(icon, label, active, onClick));
      this.tabbarEl.appendChild(this.navItem(icon, label, active, onClick));
    };
    for (const dest of ordered) {
      add(TAB_ICON[dest], TAB_LABEL[dest], this.isTabSelected(dest), () => {
        if (dest === "Songs") this.songsUI.showList();
        this.state.openSheet(TAB_SHEET[dest]);
      });
    }
    add("stats", "Stats", false, () => { this.moreStatsOpen = true; this.render(); });
    add("settings", "Settings", this.state.currentSheet === Sheet.Options, () => this.state.openSheet(Sheet.Options));
  }

  private closeMore(): void { this.moreOpen = false; this.moreStatsOpen = false; this.render(); }

  // ---------- fretboard view ----------

  private renderFretboardView(): void {
    // status bar
    const wordmark = el("div", { class: "wordmark" }, [
      "Chore", el("span", { class: "ct" }, ["c"]), "t",
    ]);
    const summary = `${this.state.tuningName}${this.state.isEditedTuning ? "*" : ""}  ·  ` +
      this.state.liveTuning.openStrings.map((n) => spellPc(midiPitchClass(n.midi))).join(" ");
    const statusRight = el("div", { class: "row" });
    if (this.state.currentSheet === null && this.state.lastSheet) {
      statusRight.appendChild(btn(`↑ ${this.sheetLabel(this.state.lastSheet)}`, () => this.state.reopenLastSheet(), "btn text"));
    }
    // While the loop plays, surface a Stop here (the loop keeps running across screens).
    if (this.loop.isLooping) {
      const stop = el("button", { class: "btn text" }, [
        icon("stop", 16), el("span", {}, [" Stop"]),
      ]);
      stop.addEventListener("click", () => this.loop.stopLoop());
      stop.style.color = "var(--root-tone)";
      statusRight.appendChild(stop);
    }
    statusRight.appendChild(this.tuneButton());
    const statusBar = el("div", { class: "status-bar" }, [
      wordmark,
      el("div", { class: "status-summary" }, [summary]),
      statusRight,
    ]);
    this.contentEl.appendChild(statusBar);

    // fretboard canvas (persistent element re-attached here)
    const wrap = el("div", { class: "fretboard-wrap" });
    wrap.appendChild(this.fretCanvasEl);
    this.contentEl.appendChild(wrap);

    // clamp indices, compute shapes/marks
    const chordShapes = this.chordShapes();
    const scalePositions = this.scalePositions();
    if (this.state.chordPositionIndex >= chordShapes.length) this.state.chordPositionIndex = 0;
    if (this.state.scalePositionIndex >= scalePositions.length) this.state.scalePositionIndex = 0;
    // "Watch on neck": while the loop plays, mirror the sounding chord on the main fretboard.
    const marks = (this.loop.isLooping && this.loop.playingShape)
      ? shapeMarks(this.loop.playingShape, this.state.labelMode)
      : computeMarks(this.state, chordShapes, scalePositions, DISPLAY_FRETS);

    const data: FretboardData = {
      tuning: this.state.liveTuning,
      marks,
      selectedPosition: this.state.selectedPosition,
      leftHanded: this.state.leftHanded,
      numFrets: DISPLAY_FRETS,
      playOnTouchDown: this.state.tapOnTouchDown,
      mutedStrings: this.state.displayMode === DisplayMode.Pick ? this.state.mutedStrings : new Set<number>(),
      onTap: (pos) => {
        if (this.state.displayMode === DisplayMode.Pick) this.state.togglePick(pos);
        else this.state.tapPosition(pos);
      },
      // Play mode: sweep across the strings to strum the current grip.
      strumMode: this.state.displayMode === DisplayMode.Pick,
      onStrumPluck: (s) => this.state.pluckString(s),
    };
    this.fretboard.setData(data);

    // selected-position info
    this.contentEl.appendChild(this.selectedInfo());

    // context bar
    const ctxBar = this.contextBar(chordShapes, scalePositions);
    if (ctxBar) this.contentEl.appendChild(ctxBar);
  }

  private selectedInfo(): HTMLElement {
    const sel = this.state.selectedPosition;
    const tuning = this.state.liveTuning;
    if (!sel || sel.stringIndex >= stringCount(tuning)) {
      const tuningNotes = tuning.openStrings.map((n) => spellPc(midiPitchClass(n.midi))).join(" ");
      return el("div", { class: "selected-info" }, [`Tuning:  ${tuningNotes}    ·    tap any spot to inspect`]);
    }
    const n = noteAt(tuning, sel);
    const noteName = spellNote(n);
    const stringNum = stringCount(tuning) - sel.stringIndex;
    const openOrFret = sel.fret === 0 ? "open" : `fret ${sel.fret}`;
    let tail = "";
    const parsed = parseChord(this.state.chordInput);
    if (parsed) {
      const iv = (((midiPitchClass(n.midi) - parsed[0]) % 12) + 12) % 12;
      tail = `  ·  ${intervalName(iv)} relative to ${spellPc(parsed[0])}`;
    }
    return el("div", { class: "selected-info" }, [`string ${stringNum} · ${openOrFret} · ${noteName}${tail}`]);
  }

  private contextBar(chordShapes: ChordShape[], scalePositions: ScalePosition[]): HTMLElement | null {
    const dm = this.state.displayMode;
    if (dm === DisplayMode.Chord && this.state.chordView === ChordScaleView.Positions && chordShapes.length) {
      const sh = chordShapes[this.state.chordPositionIndex];
      const played = sh.frets.filter((f): f is number => f !== null);
      const fretsLabel = played.length === 0 ? "" : (() => {
        const lo = Math.min(...played), hi = Math.max(...played);
        return lo === hi ? `fret ${lo}` : `frets ${lo}–${hi}`;
      })();
      const label = `${sh.chordName}  ·  ${fretsLabel}  ·  ${this.state.chordPositionIndex + 1} / ${chordShapes.length}`;
      return this.positionScroller(label, () => this.state.stepChordPosition(-1, chordShapes.length), () => this.state.stepChordPosition(1, chordShapes.length));
    }
    if (dm === DisplayMode.Scale && this.state.scaleView === ChordScaleView.Positions && scalePositions.length) {
      const sp = scalePositions[this.state.scalePositionIndex];
      const anchor = `anchor ${spellPc(sp.anchorPitchClass)} · frets ${sp.firstFret}–${sp.lastFret}`;
      const label = `${this.state.scaleRoot} ${this.state.scaleType}  ·  ${anchor}  ·  ${this.state.scalePositionIndex + 1} / ${scalePositions.length}`;
      return this.positionScroller(label, () => this.state.stepScalePosition(-1, scalePositions.length), () => this.state.stepScalePosition(1, scalePositions.length));
    }
    if (dm === DisplayMode.Pick) return this.pickActionBar();
    return null;
  }

  private positionScroller(label: string, onPrev: () => void, onNext: () => void): HTMLElement {
    return el("div", { class: "context-bar" }, [
      el("div", { class: "position-scroller" }, [
        btn("◀", onPrev, "btn icon"),
        el("div", { class: "label" }, [label]),
        btn("▶", onNext, "btn icon"),
      ]),
    ]);
  }

  private pickActionBar(): HTMLElement {
    const canStrum = [...this.state.pickedPositions].some((k) => !this.state.mutedStrings.has(parseInt(k.split(",")[0], 10)));
    const counts = `Picked: ${this.state.pickedPositions.size}` + (this.state.mutedStrings.size ? `  ·  muted: ${this.state.mutedStrings.size}` : "");
    const strumDownBtn = btn("Strum ↓", () => this.state.strumPicked(false, false), "btn primary");
    const strumUpBtn = btn("Strum ↑", () => this.state.strumPicked(true, false), "btn primary");
    const arpBtn = btn("Arp", () => this.state.strumPicked(false, true));
    const clearBtn = btn("Clear", () => this.state.clearPicked());
    (strumDownBtn as HTMLButtonElement).disabled = !canStrum;
    (strumUpBtn as HTMLButtonElement).disabled = !canStrum;
    (arpBtn as HTMLButtonElement).disabled = !canStrum;
    (clearBtn as HTMLButtonElement).disabled = this.state.pickedPositions.size === 0 && this.state.mutedStrings.size === 0;
    return el("div", { class: "context-bar" }, [
      this.quickChordRow(),
      el("div", { class: "v-gap-8" }),
      this.muteRow(),
      el("div", { class: "v-gap-8" }),
      el("div", { class: "row" }, [el("div", { class: "spacer", style: "" }, [counts]), strumDownBtn, strumUpBtn, arpBtn, clearBtn]),
    ]);
  }

  /** Play-mode quick chords: one tap lights that chord's grip on the board —
   *  then sweep the neck to strum it. The ✎ chip flips to edit mode, where
   *  tapping a chip reassigns its chord symbol instead of applying it. */
  private quickChordRow(): HTMLElement {
    const row = el("div", { class: "chip-row", style: "position:relative" });
    this.state.chordSlots.forEach((sym, i) => {
      const active = this.state.activeChordSlot === i;
      const chip = el("button", { class: active ? "chip selected" : "chip" }, [
        this.editChordSlots ? `✎ ${sym}` : sym,
      ]);
      chip.addEventListener("click", () => {
        if (this.editChordSlots) { this.editingChordSlot = i; this.render(); }
        else this.state.applyChordSlot(i);
      });
      row.appendChild(chip);
    });
    const edit = el("button", { class: this.editChordSlots ? "chip selected" : "chip" }, ["✎"]);
    edit.addEventListener("click", () => {
      this.editChordSlots = !this.editChordSlots;
      this.editingChordSlot = -1;
      this.render();
    });
    row.appendChild(edit);
    if (this.editingChordSlot >= 0) {
      const idx = this.editingChordSlot;
      const input = el("input", { type: "text", style: "width:110px" }) as HTMLInputElement;
      input.value = this.state.chordSlots[idx];
      const save = btn("Save", () => {
        this.state.setChordSlot(idx, input.value);   // no-op if it doesn't parse
        this.editingChordSlot = -1;
        this.editChordSlots = false;
        this.render();
      }, "btn primary");
      const cancel = btn("✕", () => { this.editingChordSlot = -1; this.render(); });
      row.appendChild(el("div", { class: "drum-load-pop", style: "display:flex;gap:6px;padding:8px;position:absolute;right:0;top:100%;z-index:5" }, [
        el("span", { style: "align-self:center;font-size:12px;color:var(--text-secondary)" }, [`Slot ${idx + 1}:`]),
        input, save, cancel,
      ]));
    }
    return row;
  }

  private muteRow(): HTMLElement {
    const tuning = this.state.liveTuning;
    const row = el("div", { class: "chip-row" });
    for (let s = stringCount(tuning) - 1; s >= 0; s--) {
      const name = spellPc(midiPitchClass(tuning.openStrings[s].midi));
      const muted = this.state.mutedStrings.has(s);
      const chip = el("button", { class: muted ? "chip selected" : "chip" }, [muted ? `✕ ${name}` : name]);
      chip.addEventListener("click", () => this.state.toggleMutedString(s));
      row.appendChild(chip);
    }
    return row;
  }

  // ---------- Tone sheet access ----------

  /** Small tune-icon button — the Fretboard/Options status bar and the Tuner
   *  topbar's way into the shared Tone sheet (Signal move #3). Ear/Rhythm/Loop
   *  reach it through their transport dock's tone chip instead. */
  private tuneButton(): HTMLElement {
    const b = el("button", { class: "tune-btn" }, [icon("tune", 18)]);
    b.setAttribute("aria-label", "Tone");
    b.addEventListener("click", () => { this.toneSheetOpen = true; this.render(); });
    return b;
  }

  // ---------- More sheet ----------

  /** More = a bottom sheet listing every TabDest NOT currently in the 4-tab
   *  order, plus the two fixed rows Challenge Stats and Settings — mirrors
   *  Android's MoreScreen (Shell.kt) exactly. Independent of the route-keyed
   *  config sheet below: it can open over any screen. */
  private moreSheet(): HTMLElement {
    const sheet = el("div", { class: "sheet" });
    sheet.appendChild(el("div", { class: "sheet-grabber" }));
    sheet.appendChild(el("div", { class: "sheet-header" }, [
      el("h2", {}, ["More"]),
      btn("✕", () => this.closeMore(), "btn text"),
    ]));
    const extra = ALL_TAB_DESTS.filter((d) => !this.state.tabOrder.includes(d) && availableFor(d, this.state.instrument));
    for (const dest of extra) {
      sheet.appendChild(this.moreRow(TAB_ICON[dest], TAB_LABEL[dest], TAB_SUBTITLE[dest], () => {
        this.state.openSheet(TAB_SHEET[dest]);
        this.closeMore();
      }));
    }
    sheet.appendChild(this.moreRow("stats", "Challenge stats", "Best scores across every ear-training challenge", () => {
      this.moreStatsOpen = true;
      this.render();
    }));
    sheet.appendChild(this.moreRow("settings", "Settings", "Theme, accent, tabs & order, tuning, instrument", () => {
      this.state.openSheet(Sheet.Options);
      this.closeMore();
    }));
    const scrim = el("div", { class: "sheet-scrim" }, [sheet]);
    scrim.addEventListener("click", (e) => { if (e.target === scrim) this.closeMore(); });
    return scrim;
  }

  private moreRow(iconName: IconName, title: string, sub: string, onClick: () => void): HTMLElement {
    const row = el("div", { class: "more-row" }, [
      icon(iconName, 24),
      el("div", { class: "more-row-text" }, [
        el("div", { class: "more-row-title" }, [title]),
        el("div", { class: "more-row-sub" }, [sub]),
      ]),
    ]);
    row.addEventListener("click", onClick);
    return row;
  }

  // ---------- control sheets ----------

  /** Renders whichever overlay is active into the single sheet layer: the
   *  More sheet (+ its stacked stats popup) takes priority over the
   *  route-keyed Fretboard/Options config sheet, since More isn't tied to
   *  currentSheet at all. */
  private renderOverlays(route: Sheet | null): void {
    clear(this.sheetLayer);
    // The Tabs & order editor's pending pick is local UI state, not part of
    // AppState — drop any in-flight (<4) edit once the Settings sheet isn't
    // showing, same as Android's remember-scoped TabOrderEditor being
    // disposed on dismiss.
    if (route !== Sheet.Options) this.tabOrderPending = null;
    if (this.moreOpen) {
      this.sheetLayer.appendChild(this.moreSheet());
      if (this.moreStatsOpen) {
        this.sheetLayer.appendChild(renderChallengeStatsOverlay(this.state, () => { this.moreStatsOpen = false; this.render(); }));
      }
      return;
    }
    // Challenge stats can be opened straight from the nav (the full menu is visible now,
    // so there's no "More" sheet to nest it in).
    if (this.moreStatsOpen) {
      this.sheetLayer.appendChild(renderChallengeStatsOverlay(this.state, () => { this.moreStatsOpen = false; this.render(); }));
      return;
    }
    if (route === Sheet.Fretboard || route === Sheet.Options) {
      const sheet = el("div", { class: "sheet" });
      sheet.appendChild(el("div", { class: "sheet-grabber" }));
      const header = el("div", { class: "sheet-header" }, [
        el("h2", {}, [route === Sheet.Fretboard ? "Fretboard" : "Settings"]),
        btn("✕", () => this.state.closeSheet(), "btn text"),
      ]);
      sheet.appendChild(header);
      if (route === Sheet.Fretboard) this.fillFretboardSheet(sheet);
      else this.fillOptionsSheet(sheet);
      sheet.appendChild(el("div", { class: "row end" }, [btn("Done", () => this.state.closeSheet(), "btn text")]));

      const scrim = el("div", { class: "sheet-scrim" }, [sheet]);
      scrim.addEventListener("click", (e) => { if (e.target === scrim) this.state.closeSheet(); });
      this.sheetLayer.appendChild(scrim);
    }
    // Tone sheet (Signal move #3): stacked on top of whatever else is showing —
    // reachable from the Fretboard/Options status bar and the Tuner topbar's
    // tune button (Ear/Rhythm/Loop reach it through their own transport dock).
    if (this.toneSheetOpen) {
      this.sheetLayer.appendChild(toneSheet(this.state, this.ear, () => { this.toneSheetOpen = false; this.render(); }));
    }
  }

  private fillFretboardSheet(sheet: HTMLElement): void {
    sheet.appendChild(segmented<DisplayMode>(
      [{ value: DisplayMode.None, label: "None" }, { value: DisplayMode.Chord, label: "Chord" }, { value: DisplayMode.Scale, label: "Scale" }, { value: DisplayMode.Pick, label: "Play" }],
      this.state.displayMode,
      (v) => this.state.setDisplayMode(v),
    ));
    sheet.appendChild(el("div", { class: "v-gap-12" }));
    if (this.state.displayMode === DisplayMode.Scale) this.fillScaleControls(sheet);
    else if (this.state.displayMode === DisplayMode.Pick) this.fillPickControls(sheet);
    else this.fillChordControls(sheet);
  }

  private fillChordControls(sheet: HTMLElement): void {
    const parsed = parseChord(this.state.chordInput);
    const currentRoot = parsed?.[0] ?? null;
    const currentQ = parsed?.[1].symbol ?? null;

    sheet.appendChild(labelSm("Root"));
    sheet.appendChild(chipRow(
      PITCH_CLASS_ROW.map((pc) => ({ value: pc, label: spellPc(pc) })),
      (pc) => pc === currentRoot,
      (pc) => this.state.setChordInput(spellPc(pc) + (currentQ ?? "")),
    ));
    sheet.appendChild(labelSm("Quality"));
    sheet.appendChild(chipRow(
      COMMON_QUALITY_SYMBOLS.map((sym) => ({ value: sym, label: qualityLabel(sym) })),
      (sym) => sym === currentQ,
      (sym) => this.state.setChordInput(spellPc(currentRoot ?? PC.C) + sym),
    ));
    sheet.appendChild(labelSm("Display"));
    sheet.appendChild(segmented<ChordScaleView>(
      [{ value: ChordScaleView.AllNotes, label: "All notes" }, { value: ChordScaleView.Positions, label: "Positions" }],
      this.state.chordView, (v) => this.state.setChordView(v),
    ));
    sheet.appendChild(labelSm("Labels"));
    sheet.appendChild(this.labelModeSeg());

    sheet.appendChild(el("div", { class: "v-gap-12" }));
    if (parsed) {
      const [root, q] = parsed;
      const notes = notesFrom(q, root).map((pc) => spellPc(pc)).join(" ");
      const intervals = q.intervals.map((iv) => intervalName(iv)).join(" ");
      sheet.appendChild(el("div", {}, [`${spellPc(root)}${q.symbol}:  ${notes}`]));
      sheet.appendChild(el("div", { class: "mono", style: `color:var(--muted)` }, [`intervals:  ${intervals}`]));
      // selected position's voicing card
      if (this.state.chordView === ChordScaleView.Positions) {
        const shapes = this.chordShapes();
        const sh = shapes[this.state.chordPositionIndex];
        if (sh) sheet.appendChild(this.shapeCard(sh));
      }
    } else {
      sheet.appendChild(el("div", { style: `color:var(--root-tone)` }, ["(chord not recognized)"]));
    }
  }

  private fillScaleControls(sheet: HTMLElement): void {
    let scalePc: number | null;
    try { scalePc = parsePitchClass(this.state.scaleRoot); } catch { scalePc = null; }
    const scale = SCALES.get(this.state.scaleType);

    sheet.appendChild(labelSm("Root"));
    sheet.appendChild(chipRow(
      PITCH_CLASS_ROW.map((pc) => ({ value: spellPc(pc), label: spellPc(pc) })),
      (name) => name === this.state.scaleRoot,
      (name) => this.state.setScaleRoot(name),
    ));
    sheet.appendChild(labelSm("Scale"));
    sheet.appendChild(chipRow(
      [...SCALES.keys()].map((name) => ({ value: name, label: name })),
      (name) => name === this.state.scaleType,
      (name) => this.state.setScaleType(name),
    ));
    sheet.appendChild(labelSm("Display"));
    sheet.appendChild(segmented<ChordScaleView>(
      [{ value: ChordScaleView.AllNotes, label: "All notes" }, { value: ChordScaleView.Positions, label: "Positions" }],
      this.state.scaleView, (v) => this.state.setScaleView(v),
    ));
    sheet.appendChild(labelSm("Labels"));
    sheet.appendChild(this.labelModeSeg());

    sheet.appendChild(el("div", { class: "v-gap-12" }));
    if (scalePc !== null && scale) {
      const notes = scaleNotesFrom(scale, scalePc).map((pc) => spellPc(pc)).join(" ");
      const formula = scale.intervals.map((iv) => intervalName(iv)).join(" ");
      sheet.appendChild(el("div", {}, [`${this.state.scaleRoot} ${scale.name}`]));
      sheet.appendChild(el("div", { class: "mono" }, [`notes    ${notes}`]));
      sheet.appendChild(el("div", { class: "mono" }, [`formula  ${formula}`]));
    } else {
      sheet.appendChild(el("div", { style: `color:var(--root-tone)` }, ["(invalid root or scale)"]));
    }
  }

  private fillPickControls(sheet: HTMLElement): void {
    sheet.appendChild(el("div", { style: `color:var(--muted)` }, [
      "Tap any fret to add or remove it from your grip (or tap a quick-chord button below the neck), " +
      "mute whole strings, then SWEEP across the strings to strum — each string plucks as the pointer " +
      "crosses it. Unpicked strings ring open.",
    ]));
    sheet.appendChild(el("div", { class: "v-gap-8" }));
    sheet.appendChild(el("div", {}, [`Picked: ${this.state.pickedPositions.size}` + (this.state.mutedStrings.size ? `   ·   muted: ${this.state.mutedStrings.size}` : "")]));
    sheet.appendChild(labelSm("Mute strings"));
    sheet.appendChild(this.muteRow());
    sheet.appendChild(el("div", { class: "v-gap-12" }));
    const canStrum = [...this.state.pickedPositions].some((k) => !this.state.mutedStrings.has(parseInt(k.split(",")[0], 10)));
    const strumBtn = btn("Strum", () => this.state.strumPicked(false), "btn primary");
    const arpBtn = btn("Arpeggio", () => this.state.strumPicked(true));
    const clearBtn = btn("Clear", () => this.state.clearPicked());
    (strumBtn as HTMLButtonElement).disabled = !canStrum;
    (arpBtn as HTMLButtonElement).disabled = !canStrum;
    sheet.appendChild(el("div", { class: "row" }, [strumBtn, arpBtn, clearBtn]));
  }

  private labelModeSeg(): HTMLElement {
    return segmented<LabelMode>(
      [{ value: LabelMode.Notes, label: "Notes" }, { value: LabelMode.Intervals, label: "Intervals" }, { value: LabelMode.Empty, label: "Empty" }],
      this.state.labelMode, (v) => this.state.setLabelMode(v),
    );
  }

  private shapeCard(shape: ChordShape): HTMLElement {
    const rev = <T>(a: ReadonlyArray<T>) => a.slice().reverse();
    const pad = (s: string) => s.padStart(2, " ");
    const fretsLine = rev(shape.frets).map((f) => (f === null ? " x" : pad(String(f)))).join(" ");
    const notesLine = rev(shape.notes).map((n) => (n === null ? " x" : pad(spellPc(midiPitchClass(n.midi))))).join(" ");
    const ivLine = rev(shape.intervals).map((iv) => (iv === null ? " x" : pad(intervalName(iv)))).join(" ");
    const fingersLine = rev(suggestFingering(shape)).map((f) => (f === null ? " ·" : pad(String(f)))).join(" ");
    const positionLabel = shape.position === 0 ? "open position" : `position ${shape.position}`;
    const rootTag = shape.hasRootInBass ? " · root in bass" : "";
    return el("div", { style: `margin-top:10px;background:var(--surface2);border-radius:10px;padding:12px` }, [
      el("div", {}, [`${shape.chordName}  ·  ${positionLabel}  ·  span ${shape.fretSpan}${rootTag}`]),
      el("div", { class: "mono" }, [`frets     ${fretsLine}`]),
      el("div", { class: "mono" }, [`notes     ${notesLine}`]),
      el("div", { class: "mono" }, [`intervals ${ivLine}`]),
      el("div", { class: "mono" }, [`fingers   ${fingersLine}`]),
    ]);
  }

  /** Settings sheet content (Signal T12, mirrors Android's Screens.kt
   *  OptionsSheet grouping exactly): Personalize (left-handed) → Instrument
   *  (tuning, unchanged) → Behavior (labels/touch/voicing, unchanged) → Tuner
   *  (A4 only — ring sustain/strum spread live in the Tone sheet, see
   *  transport.ts, so aren't duplicated here) → "Look & tabs" fold
   *  (theme/accent/tabs & order, collapsed, last). */
  private fillOptionsSheet(sheet: HTMLElement): void {
    const s = this.state;

    // ----- Personalize (theme / accent / tabs & order moved into the collapsed
    // "Look & tabs" fold at the bottom — set-once options shouldn't push the
    // settings you actually revisit off-screen) -----
    sheet.appendChild(this.sectionLabel("Personalize"));

    sheet.appendChild(switchRow("Left-handed", null, s.leftHanded, (v) => s.toggleLeftHanded(v)));

    sheet.appendChild(el("div", { class: "divider-line" }));

    // ----- Instrument (unchanged) -----
    sheet.appendChild(this.sectionLabel("Instrument"));
    sheet.appendChild(el("div", { class: "v-gap-8" }));
    sheet.appendChild(segmented<Instrument>(
      [Instrument.Guitar, Instrument.Cavaquinho].map((i) => ({ value: i, label: InstrumentInfo[i].displayName })),
      s.instrument, (v) => s.setInstrument(v),
    ));

    sheet.appendChild(labelSm("Tuning"));
    const presets = [...Tunings.presetsFor(s.instrument).entries()];
    sheet.appendChild(chipRow(
      presets.map(([name]) => ({ value: name, label: name })),
      (name) => name === s.tuningName && !s.isEditedTuning,
      (name) => { const t = Tunings.presetsFor(s.instrument).get(name)!; s.selectTuning(name, t); },
    ));
    if (s.customTunings.size) {
      sheet.appendChild(labelSm("My tunings"));
      const myRow = el("div", { class: "chip-row" });
      for (const [name, t] of s.customTunings) {
        const selected = name === s.tuningName && !s.isEditedTuning;
        const chip = el("button", { class: selected ? "chip selected" : "chip" }, [name + "  "]);
        chip.addEventListener("click", () => s.selectTuning(name, t));
        const x = el("span", { style: `margin-left:6px;color:var(--root-tone)` }, ["✕"]);
        x.addEventListener("click", (e) => { e.stopPropagation(); s.deleteCustomTuning(name); });
        chip.appendChild(x);
        myRow.appendChild(chip);
      }
      sheet.appendChild(myRow);
    }
    sheet.appendChild(el("div", { class: "mono", style: "margin-top:6px" }, [
      "Open strings (low → high):  " + s.liveTuning.openStrings.map((n) => spellNote(n)).join(" "),
    ]));
    if (s.isEditedTuning) sheet.appendChild(el("div", { style: `color:var(--root-tone);font-size:12px` }, ["(unsaved edits)"]));
    sheet.appendChild(this.tuningEditor());

    sheet.appendChild(el("div", { class: "divider-line" }));

    // ----- Behavior (unchanged, minus the dark-theme switch — moved to Personalize) -----
    sheet.appendChild(this.sectionLabel("Behavior"));
    sheet.appendChild(labelSm("Labels on dots"));
    sheet.appendChild(this.labelModeSeg());
    sheet.appendChild(switchRow(
      "Play note on touch-down",
      "Off (default): notes play on tap-release, so swiping the neck won't sound a note. On: notes fire the instant you touch.",
      s.tapOnTouchDown, (v) => s.setTapOnTouchDown(v),
    ));
    sheet.appendChild(this.audioLatencyPanel());
    sheet.appendChild(switchRow(
      "Jazz / shell voicings",
      "Drop the 5th (and root for 7+ chords); favor 2-4 note voicings.",
      s.voicingStyle === VoicingStyle.Shell, (v) => s.toggleVoicingStyle(v),
    ));

    sheet.appendChild(el("div", { class: "divider-line" }));

    // ----- Instrument volume: balances the played instrument against the drum machine,
    // which has its own per-voice volumes. Stored per instrument (guitar vs cavaquinho are
    // voiced differently), so the label names whichever one is selected. -----
    sheet.appendChild(this.sectionLabel("Sound level"));
    const volName = s.instrument === Instrument.Cavaquinho ? "Cavaquinho" : "Guitar";
    const volVS = valueSlider((v) => `${volName} volume: ${Math.round(v)}%`, 0, 100,
      s.instrumentVolumePct, (v) => s.setInstrumentVolumePct(v));
    sheet.appendChild(el("div", { style: "margin-top:6px" }, [volVS.label]));
    sheet.appendChild(volVS.input);
    sheet.appendChild(el("div", { class: "settings-hint" }, [
      "Level of the played notes and chords. The drum machine has its own per-voice volumes, " +
      "so this sets the balance between the two.",
    ]));

    sheet.appendChild(el("div", { class: "divider-line" }));

    // ----- Tuner (A4 reference only; ring sustain/strum spread moved to Tone sheet in T6) -----
    sheet.appendChild(this.sectionLabel("Tuner"));
    const a4VS = valueSlider((v) => `A4 reference: ${Math.round(v)} Hz`, 435, 445, s.a4Hz, (v) => s.setA4Hz(v));
    sheet.appendChild(el("div", { style: "margin-top:6px" }, [a4VS.label]));
    sheet.appendChild(a4VS.input);

    sheet.appendChild(el("div", { class: "divider-line" }));

    // ----- Data (the ONLY place recorded practice history can be deleted) -----
    this.dataSection(sheet);

    sheet.appendChild(el("div", { class: "divider-line" }));

    // ----- Look & tabs (collapsed by default; last section on purpose) -----
    sheet.appendChild(this.appearanceFold());
  }

  /**
   * Settings → Data: the one place recorded practice history can be erased.
   *
   * Deliberately NOT reachable from the stats popup any more. Those buttons sat a
   * thumb-width from a score you had just set, and hitting one wiped a history that took
   * weeks to build. Here they are behind a settings sheet AND a confirm.
   *
   * The two lists are independent and stay that way: the challenge-score log is a record
   * of what you did, the Drill list is a curated set of what you still get wrong. Clearing
   * your scores must not silently throw away the drill queue you've been building — so
   * each has its own button, and neither touches the other. Mirrors Android's
   * DataSection (Screens.kt).
   */
  private dataSection(sheet: HTMLElement): void {
    const s = this.state;
    const scores = s.challengeScores;
    const mistakes = Object.keys(s.progressionMistakes);

    sheet.appendChild(this.sectionLabel("Data"));
    sheet.appendChild(el("div", { style: "margin-top:6px" },
      [`Challenge stats — ${scores.length} recorded run${scores.length === 1 ? "" : "s"}`]));
    sheet.appendChild(el("div", { class: "et-muted", style: "font-size:12px" }, [
      "Every finished ear-training challenge. Deleting these does NOT change your Drill list.",
    ]));

    const runsWrap = el("div", { style: "margin-top:6px" });
    const listBtn = btn("Delete single runs…", () => {
      if (runsWrap.childElementCount) { runsWrap.replaceChildren(); listBtn.textContent = "Delete single runs…"; return; }
      listBtn.textContent = "Hide runs";
      for (const r of scores) {
        const row = el("div", { style: "display:flex;align-items:center;gap:8px;padding:2px 0" }, [
          el("div", { style: "flex:1;font-size:13px" }, [
            `${r.kind ?? "progression"}  ·  ${r.score}/${r.total}  ·  ${new Date(r.dateMillis).toLocaleDateString()}`,
          ]),
          // A single row is small enough to delete without a confirm.
          btn("✕", () => { s.deleteChallengeScore(r); row.remove(); }, "btn text"),
        ]);
        runsWrap.appendChild(row);
      }
    }, "btn");
    (listBtn as HTMLButtonElement).disabled = scores.length === 0;

    const clearAllBtn = btn("Delete all", () => {
      if (!confirm(`Delete all ${scores.length} recorded runs, every kind, permanently?

` +
        "Your Drill list is not affected.")) return;
      s.clearChallengeScores();
      this.scheduleRender();
    }, "btn");
    (clearAllBtn as HTMLButtonElement).disabled = scores.length === 0;

    sheet.appendChild(el("div", { class: "et-row-gap", style: "margin-top:6px" }, [listBtn, clearAllBtn]));
    sheet.appendChild(runsWrap);

    sheet.appendChild(el("div", { style: "margin-top:12px" },
      [`Drill list — ${mistakes.length} progression${mistakes.length === 1 ? "" : "s"} tracked`]));
    sheet.appendChild(el("div", { class: "et-muted", style: "font-size:12px" }, [
      "The progressions you keep missing, which the Drill-list challenge source draws from. " +
      "Separate from your stats — clearing one leaves the other alone.",
    ]));
    const clearDrill = btn("Clear drill list", () => {
      if (!confirm(`Clear all ${mistakes.length} tracked progressions and their miss counts?

` +
        "Your challenge stats are not affected.")) return;
      s.clearProgressionMistakes();
      this.scheduleRender();
    }, "btn");
    (clearDrill as HTMLButtonElement).disabled = mistakes.length === 0;
    sheet.appendChild(el("div", { style: "margin-top:6px" }, [clearDrill]));
  }

  /** Settings → "Look & tabs": theme mode, accent swatches and the tab picker,
   *  all behind one fold at the bottom of the sheet. Mirrors Android's
   *  AppearanceSection, including the persisted open flag — kept in AppState
   *  rather than a `<details open>` because picking an accent rerenders (and so
   *  rebuilds) the whole sheet, which would drop native element state. */
  private appearanceFold(): HTMLElement {
    const s = this.state;
    const open = s.appearanceExpanded;
    const head = el("div", { class: "settings-fold-head" }, [
      el("div", { style: "flex:1" }, [
        el("div", {}, ["Look & tabs"]),
        el("div", { class: "settings-hint", style: "margin:0" }, ["Theme, accent, which 4 tabs show"]),
      ]),
      el("span", { class: "settings-fold-chevron" }, [open ? "▴" : "▾"]),
    ]);
    head.addEventListener("click", () => s.setAppearanceExpanded(!open));

    const wrap = el("div", {}, [head]);
    if (!open) return wrap;

    const body = el("div", { class: "settings-fold-body" });
    body.appendChild(labelSm("Theme"));
    body.appendChild(segmented<ThemeMode>(
      ALL_THEME_MODES.map((m) => ({ value: m, label: m })),
      s.themeMode, (v) => s.setThemeMode(v),
    ));

    body.appendChild(labelSm("Accent"));
    body.appendChild(this.accentRow());

    body.appendChild(labelSm("Tabs & order"));
    body.appendChild(el("div", { class: "settings-hint" }, ["Pick 4 tabs; everything else lives in More"]));
    body.appendChild(this.tabOrderEditor());
    wrap.appendChild(body);
    return wrap;
  }

  private sectionLabel(text: string): HTMLElement {
    return el("div", { class: "section-label" }, [text]);
  }

  /**
   * Measured touch-to-sound budget (mirror of Android's AudioLatencyPanel).
   *
   * Perceived latency is hard to estimate by ear, and the biggest contributor is usually the
   * output device rather than the app — Bluetooth buffers 150-400 ms downstream of the
   * browser. `outputLatency` is the browser's own estimate including the OS and device, so
   * it is the figure that corresponds to what you actually hear.
   */
  private audioLatencyPanel(): HTMLElement {
    const card = el("div", { class: "latency-card" });
    const render = () => {
      card.textContent = "";
      const r = this.state.audio.latencyReport();
      card.appendChild(el("div", { class: "latency-title" }, ["Audio latency"]));
      const row = (label: string, value: string) =>
        card.appendChild(el("div", { class: "latency-row" }, [
          el("span", { class: "latency-label" }, [label]),
          el("span", {}, [value]),
        ]));
      row("Context rate", `${r.sampleRate} Hz`);
      row("Graph latency", r.baseMs > 0 ? `${r.baseMs.toFixed(1)} ms` : "not reported");
      row("Output latency", r.outputMs > 0 ? `${r.outputMs.toFixed(1)} ms` : "not reported");
      row("Context state", r.state);
      const d = inputDispatchReport();
      row(
        "Touch → app",
        d.lastMs < 0 ? "— (tap the neck first)"
          : `${d.lastMs.toFixed(1)} ms (worst ${d.worstMs.toFixed(1)} ms)`,
      );
      card.appendChild(el("div", { class: "latency-note" }, [
        r.outputMs > 120
          ? "That output latency is high for touch response. It is almost always a Bluetooth " +
            "speaker or headphones buffering downstream of the browser — try wired output or " +
            "the built-in speakers."
          : "Output latency is the browser's own estimate, including the OS and the output " +
            "device. Bluetooth adds 150-400 ms that no code change can remove.",
      ]));
      const btn = el("button", { class: "btn small" }, ["Refresh"]);
      btn.addEventListener("click", render);
      card.appendChild(btn);
    };
    render();
    return card;
  }

  /** Settings → Personalize's 5 ACT-accent swatches: each circle is
   *  [ACCENT_SWATCH_HEX]'s dark color; the selected swatch gets a ring.
   *  Applies immediately via `AppState.setAccent` (no "Done" step) — mirrors
   *  Android's AccentRow exactly. */
  private accentRow(): HTMLElement {
    const s = this.state;
    const row = el("div", { class: "accent-row" });
    for (const a of ALL_ACCENTS) {
      const swatch = el("button", {
        class: a === s.accent ? "accent-swatch selected" : "accent-swatch",
        style: `background-color:${ACCENT_SWATCH_HEX[a]}`,
        "aria-label": `${a} accent`,
        "aria-pressed": a === s.accent ? "true" : "false",
      });
      swatch.addEventListener("click", () => s.setAccent(a));
      row.appendChild(swatch);
    }
    return row;
  }

  /** Settings → Personalize's Tabs & order editor: pick exactly 4 of the 6
   *  [ALL_TAB_DESTS] candidates for the bottom tab bar/rail, reorder the
   *  picked ones with up/down buttons. See `tabOrderPending`'s doc comment
   *  for the local-pending-list rationale; mirrors Android's TabOrderEditor. */
  private tabOrderEditor(): HTMLElement {
    const s = this.state;
    const pending = this.tabOrderPending ?? s.tabOrder;
    const commit = (next: TabDestName[]): void => {
      if (next.length === 4) {
        this.tabOrderPending = null;
        s.setTabOrder(next); // persists + notifies (schedules its own render)
      } else {
        this.tabOrderPending = next;
        this.scheduleRender();
      }
    };
    const swapped = (i: number, j: number): TabDestName[] => {
      const next = pending.slice();
      [next[i], next[j]] = [next[j], next[i]];
      return next;
    };

    const wrap = el("div", { class: "tab-order-editor" });
    pending.forEach((dest, idx) => {
      const cb = el("input", { type: "checkbox" });
      cb.checked = true;
      cb.addEventListener("change", () => commit(pending.filter((d) => d !== dest)));

      const up = el("button", { class: "btn tab-order-btn", "aria-label": `Move ${TAB_LABEL[dest]} up` }, [icon("chevronUp", 18)]);
      up.disabled = idx === 0;
      up.addEventListener("click", () => commit(swapped(idx, idx - 1)));

      const down = el("button", { class: "btn tab-order-btn", "aria-label": `Move ${TAB_LABEL[dest]} down` }, [icon("chevronDown", 18)]);
      down.disabled = idx === pending.length - 1;
      down.addEventListener("click", () => commit(swapped(idx, idx + 1)));

      wrap.appendChild(el("div", { class: "tab-order-row" }, [
        cb, icon(TAB_ICON[dest], 18), el("span", { class: "tab-order-label" }, [TAB_LABEL[dest]]), up, down,
      ]));
    });

    const unpicked = ALL_TAB_DESTS.filter((d) => !pending.includes(d) && availableFor(d, s.instrument));
    for (const dest of unpicked) {
      const canAdd = pending.length < 4;
      const cb = el("input", { type: "checkbox" });
      cb.checked = false;
      cb.disabled = !canAdd;
      cb.addEventListener("change", () => { if (canAdd) commit([...pending, dest]); });
      wrap.appendChild(el("div", { class: canAdd ? "tab-order-row" : "tab-order-row disabled" }, [
        cb, icon(TAB_ICON[dest], 18), el("span", { class: "tab-order-label" }, [TAB_LABEL[dest]]),
      ]));
    }
    return wrap;
  }

  private tuningEditor(): HTMLElement {
    const s = this.state;
    const card = el("div", { style: `margin-top:8px;background:var(--surface2);border-radius:10px;padding:10px` });
    for (let str = stringCount(s.liveTuning) - 1; str >= 0; str--) {
      const n = stringCount(s.liveTuning) - str;
      const note0 = s.liveTuning.openStrings[str];
      const row = el("div", { class: "row", style: "margin:3px 0" }, [
        el("span", { class: "mono", style: "width:74px;display:inline-block" }, [`S${n}  ${spellNote(note0)}`]),
        btn("−", () => s.adjustString(str, -1)),
        btn("+", () => s.adjustString(str, 1)),
        btn("−oct", () => s.adjustString(str, -12)),
        btn("+oct", () => s.adjustString(str, 12)),
      ]);
      card.appendChild(row);
    }
    const input = el("input", { type: "text", placeholder: "Save as…", style: "flex:1" }) as HTMLInputElement;
    const saveBtn = btn("Save", () => { s.saveCustomTuning(input.value); }, "btn primary");
    card.appendChild(el("div", { class: "row", style: "margin-top:8px" }, [input, saveBtn]));
    if (s.isEditedTuning) card.appendChild(el("div", { class: "row", style: "margin-top:6px" }, [btn("Discard edits", () => s.resetTuningToSaved(), "btn text")]));
    return card;
  }

  // ---------- tuner screen ----------

  private renderTuner(): void {
    const s = this.state;
    const screen = el("div", { class: "tool-screen" });

    // top bar
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("div", { class: "tool-title" }, ["TUNER"]),
      el("div", { style: `font-size:12px;color:var(--muted)` }, [`A4 = ${s.a4Hz} Hz`]),
      this.tuneButton(),
      btn("Back", () => s.closeSheet()),
    ]));

    // on-the-fly tuning chips
    const presets = [...Tunings.presetsFor(s.instrument).entries()];
    screen.appendChild(el("div", { style: "margin-top:8px" }, [chipRow(
      presets.map(([name]) => ({ value: name, label: name })),
      (name) => name === s.tuningName && !s.isEditedTuning,
      (name) => { const t = Tunings.presetsFor(s.instrument).get(name)!; s.selectTuning(name, t); },
    )]));

    // dial area
    const dialWrap = el("div", { class: "tuner-dial-wrap" });
    const dialCanvas = el("canvas", { class: "tuner-dial" });
    this.tunerDialCanvas = dialCanvas;
    const noteEl = el("div", { class: "tuner-note" }, ["—"]);
    const hzEl = el("div", { class: "tuner-hz" }, [""]);
    const centsEl = el("div", { class: "tuner-cents" }, [""]);
    const hintEl = el("div", { class: "tuner-hint" }, [""]);
    this.tunerNoteEl = noteEl;
    this.tunerHzEl = hzEl;
    this.tunerCentsEl = centsEl;
    this.tunerHintEl = hintEl;
    noteEl.addEventListener("click", () => {
      if (this.tuner?.midi != null) {
        s.playReferencePitch(this.tuner.midi);
        this.tuner.lockTo(this.tuner.midi, s.ringSustainMs);
      }
    });
    const readout = el("div", { class: "tuner-readout" }, [noteEl, hzEl, centsEl, hintEl]);
    dialWrap.appendChild(dialCanvas);
    dialWrap.appendChild(readout);
    screen.appendChild(dialWrap);

    // reference row — the string nearest the currently detected pitch (by
    // pitch class first, tie-broken by absolute MIDI distance) is act-bordered,
    // purely a display computation over the existing tuner reading (mirrors
    // Android TunerScreen's `nearestIdx`; adds no new state). Buttons are kept
    // in `tunerRefBtns` so `redrawTuner()` can update the highlight live —
    // `renderTuner()` itself only reruns on a full app rerender, same reason
    // note/cents/hint are mutated directly rather than rebuilt every frame.
    const strings = s.liveTuning.openStrings;
    const refRow = el("div", { class: "tuner-ref-row" }, [el("span", { style: `font-size:11px;color:var(--muted)` }, ["Reference"])]);
    this.tunerRefBtns = [];
    strings.forEach((n, i) => {
      const b = el("button", { class: "btn ref-btn" }, [
        el("span", { class: "s" }, [`S${stringCount(s.liveTuning) - i}`]),
        el("span", {}, [`${spellPc(midiPitchClass(n.midi))}${midiOctave(n.midi)}`]),
      ]);
      b.addEventListener("click", () => { s.playReferencePitch(n.midi); this.tuner?.lockTo(n.midi, s.ringSustainMs); });
      this.tunerRefBtns.push(b);
      refRow.appendChild(b);
    });
    screen.appendChild(refRow);

    this.contentEl.appendChild(screen);

    // start mic pipeline
    if (!this.tuner) {
      this.tuner = new TunerState(s.audio.context(), () => s.a4Hz, () => this.redrawTuner());
      void this.tuner.start().then(() => this.redrawTuner());
    }
    requestAnimationFrame(() => this.redrawTuner());
  }

  private redrawTuner(): void {
    const canvas = this.tunerDialCanvas;
    if (!canvas || !this.tuner) return;
    if (!this.tuner.capturing) {
      // No emoji glyphs anywhere (Signal spec) — a Mic icon replaces the old
      // "🎤" text in the big note slot; A4/mic capture logic is untouched.
      if (this.tunerNoteEl) { this.tunerNoteEl.innerHTML = ""; this.tunerNoteEl.appendChild(icon("mic", 40)); }
      if (this.tunerHzEl) this.tunerHzEl.textContent = "";
      if (this.tunerCentsEl) this.tunerCentsEl.textContent = "Allow microphone access to tune";
      if (this.tunerHintEl) this.tunerHintEl.textContent = "";
    }
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (w === 0 || h === 0) return;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    const ctx = canvas.getContext("2d")!;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    const cents = this.tuner.cents;
    const midi = this.tuner.midi;
    // Signal spec: the needle/note turn FEEDBACK (teal) once within ±5 cents
    // (was ±10) — a display-threshold change only, not detector logic.
    const inTune = cents !== null && Math.abs(cents) <= 5;
    drawTunerDial(ctx, w, h, cents, inTune);

    if (this.tunerNoteEl && this.tuner.capturing) {
      if (midi !== null) {
        const name = spellPc(midiPitchClass(midi));
        const oct = midiOctave(midi);
        this.tunerNoteEl.innerHTML = "";
        this.tunerNoteEl.appendChild(document.createTextNode(name));
        this.tunerNoteEl.appendChild(el("span", { class: "oct" }, [String(oct)]));
        this.tunerNoteEl.style.color = inTune ? "var(--tuned)" : "var(--text-primary)";
      } else {
        this.tunerNoteEl.textContent = "—";
        this.tunerNoteEl.style.color = "var(--text-primary)";
      }
    }
    if (this.tunerHzEl && this.tuner.capturing) {
      this.tunerHzEl.textContent = this.tuner.freqHz != null ? `${this.tuner.freqHz.toFixed(1)} Hz` : "";
    }
    if (this.tunerCentsEl && this.tuner.capturing) {
      this.tunerCentsEl.textContent = cents !== null ? `${cents >= 0 ? "+" : ""}${cents.toFixed(0)} ¢` : "";
      this.tunerCentsEl.style.color = inTune ? "var(--tuned)" : "var(--muted)";
    }
    if (this.tunerHintEl && this.tuner.capturing) {
      this.tunerHintEl.textContent = inTune ? "IN TUNE" : midi !== null ? "tap note to hear reference" : "";
      this.tunerHintEl.style.color = inTune ? "var(--tuned)" : "var(--muted)";
    }
    if (this.tunerRefBtns.length) {
      const strings = this.state.liveTuning.openStrings;
      let nearestIdx: number | null = null;
      if (midi != null && strings.length) {
        const pc = ((midi % 12) + 12) % 12;
        const samePc = strings.map((_, i) => i).filter((i) => ((strings[i].midi % 12) + 12) % 12 === pc);
        const candidates = samePc.length ? samePc : strings.map((_, i) => i);
        nearestIdx = candidates.reduce((best, i) =>
          Math.abs(strings[i].midi - midi) < Math.abs(strings[best].midi - midi) ? i : best);
      }
      this.tunerRefBtns.forEach((b, i) => { b.className = i === nearestIdx ? "btn ref-btn selected" : "btn ref-btn"; });
    }
  }

  private sheetLabel(s: Sheet): string {
    switch (s) {
      case Sheet.Fretboard: return "Fretboard";
      case Sheet.Loop: return "Loop";
      case Sheet.Options: return "Settings";
      case Sheet.Tuner: return "Tuner";
      case Sheet.EarTraining: return "Ear";
      case Sheet.SambaLooper: return "Drums";
      case Sheet.Decompose: return "Decompose";
      case Sheet.CavaqProgressions: return "Progressions";
      case Sheet.RhythmUnits: return "Rhythm";
      case Sheet.Metronome: return "Metronome";
      case Sheet.ScalesTriads: return "Guitar practice";
      case Sheet.Theory: return "Theory";
      case Sheet.Songs: return "Songs";
    }
  }
}

// ---------- tuner dial drawing (ported from TunerScreen.drawDial) ----------

function drawTunerDial(ctx: CanvasRenderingContext2D, w: number, h: number, cents: number | null, inTune: boolean): void {
  const cx = w / 2;
  const cy = h * 0.74;
  const radius = Math.min(w * 0.46, h * 0.62);
  const ring = Colors.textSecondary;
  const tuned = Colors.tuned;
  const needle = inTune ? tuned : Colors.primary;

  // quarter-ring arc (225°..315°)
  ctx.strokeStyle = ring;
  ctx.lineWidth = 4;
  ctx.beginPath();
  ctx.arc(cx, cy, radius, (225 * Math.PI) / 180, (315 * Math.PI) / 180);
  ctx.stroke();

  const polar = (r: number, thetaDegFromNorth: number): [number, number] => {
    const rad = (thetaDegFromNorth * Math.PI) / 180;
    return [cx + r * Math.sin(rad), cy - r * Math.cos(rad)];
  };
  const tickLen = (c: number) => (c === 0 ? 26 : c % 10 === 0 ? 22 : c % 5 === 0 ? 14 : 8);
  for (let c = -50; c <= 50; c++) {
    const theta = (c / 50) * 45;
    const [sx, sy] = polar(radius - tickLen(c), theta);
    const [ex, ey] = polar(radius + 2, theta);
    ctx.strokeStyle = c === 0 ? tuned : Math.abs(c) <= 10 ? withAlpha(tuned, 0.5) : ring;
    ctx.lineWidth = c === 0 ? 4 : c % 10 === 0 ? 3 : c % 5 === 0 ? 1.8 : 1;
    ctx.beginPath();
    ctx.moveTo(sx, sy);
    ctx.lineTo(ex, ey);
    ctx.stroke();
  }

  if (cents !== null) {
    const theta = (Math.min(Math.max(cents, -50), 50) / 50) * 45;
    const [nx, ny] = polar(radius - 8, theta);
    ctx.strokeStyle = needle;
    ctx.lineWidth = 6;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(nx, ny);
    ctx.stroke();
    ctx.fillStyle = needle;
    ctx.beginPath();
    ctx.arc(cx, cy, 10, 0, Math.PI * 2);
    ctx.fill();
  }
}

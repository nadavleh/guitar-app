// The app UI, ported from app/.../{MainActivity,AppShell,Screens,TunerScreen,
// AudioQuick}.kt. Vanilla DOM, re-rendered on each state change. The fretboard
// <canvas> element is persistent across renders so its zoom/pan survives.

import { AppState, DisplayMode, LabelMode, Sheet, ChordScaleView, DISPLAY_FRETS, TabDestName, ALL_TAB_DESTS } from "./appState";
import { icon, IconName } from "./icons";
import { renderChallengeStatsOverlay } from "./statsOverlay";
import { FretboardCanvas, FretboardData } from "./fretboardCanvas";
import { computeMarks, scaleInfo, intervalName, shapeMarks } from "./marks";
import { TunerState } from "./tunerState";
import { EarTrainingState, EarSubMode } from "./earTrainingState";
import { EarTrainingUI } from "./earTrainingUI";
import { SambaLooperState } from "./sambaLooperState";
import { SambaLooperUI } from "./sambaLooperUI";
import { DecomposeUI } from "./decomposeUI";
import { LoopState } from "./loopState";
import { LoopUI } from "./loopUI";
import { loadDrumSample } from "./drumSamples";
import { Timbres } from "../audio";
import { Colors, withAlpha } from "./theme";
import { el, clear, btn, segmented, chipRow, slider, switchRow, labelSm } from "./dom";
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
};
const TAB_ICON: Record<TabDestName, IconName> = {
  Neck: "neck", Ear: "ear", Rhythm: "rhythm", Loop: "loop", Tuner: "tuner", Decompose: "decompose",
};
const TAB_LABEL: Record<TabDestName, string> = {
  Neck: "Neck", Ear: "Ear", Rhythm: "Rhythm", Loop: "Loop", Tuner: "Tuner", Decompose: "Decompose",
};
/** One-line description shown under each destination's title in the More sheet. */
const TAB_SUBTITLE: Record<TabDestName, string> = {
  Neck: "Fretboard — chords, scales & pick mode",
  Ear: "Progression, interval & chord ear training",
  Rhythm: "Samba percussion drum-machine looper",
  Loop: "Chord-progression looper",
  Tuner: "Chromatic tuner with cents needle",
  Decompose: "Chord-tone breakdown reference",
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

  private tuner: TunerState | null = null;
  private tunerDialCanvas: HTMLCanvasElement | null = null;
  private tunerNoteEl: HTMLElement | null = null;
  private tunerCentsEl: HTMLElement | null = null;
  private tunerHintEl: HTMLElement | null = null;

  private toneSheetOpen = false;

  private ear: EarTrainingState;
  private earUI: EarTrainingUI;
  private samba: SambaLooperState;
  private sambaUI: SambaLooperUI;
  private decomposeUI: DecomposeUI;
  private loop: LoopState;
  private loopUI: LoopUI;

  constructor(private state: AppState, root: HTMLElement) {
    this.fretboard = new FretboardCanvas(this.fretCanvasEl);
    this.ear = new EarTrainingState({
      audio: state.audio,
      tuningProvider: () => state.liveTuning,
      sustainProvider: () => state.ringSustainMs,
      strumProvider: () => state.strumMs,
      onChange: () => this.scheduleRender(),
      onProgressionChallengeComplete: (s, t, d) => state.recordChallengeScore(s, t, d),
      onChallengeComplete: (kind, s, t, d) => state.recordChallengeScore(s, t, d, kind),
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
      getSaved: () => state.drumPatterns,
      save: (name, enc) => state.saveDrumPattern(name, enc),
      del: (name) => state.deleteDrumPattern(name),
      loadSample: (inst, voice) => loadDrumSample(state.audio, inst, voice),
      getVolumes: () => state.drumVolumes,
      saveVolume: (key, value) => state.setDrumVolume(key, value),
    });
    this.sambaUI = new SambaLooperUI(this.samba, state, this.ear, () => state.closeSheet());
    this.decomposeUI = new DecomposeUI(state, this.ear, () => state.closeSheet(), (symbols) => {
      this.loop.loadProgressionIntoLoop(symbols);
      state.openSheet(Sheet.Loop);
    });
    const header = el("div", { class: "app-header" }, [
      el("span", { class: "app-brand" }, ["chorect"]),
      el("span", { class: "app-byline" }, ["made by Nadavileh"]),
    ]);
    const appRoot = el("div", { class: "app-root" }, [this.railEl, this.contentEl]);
    const shell = el("div", { class: "app-shell" }, [header, appRoot]);
    root.appendChild(shell);
    root.appendChild(this.tabbarEl);
    root.appendChild(this.sheetLayer);
    this.setupPressGuard();
    this.setupSpacebarShortcut();
    state.subscribe(() => this.scheduleRender());
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
    window.addEventListener("pointerdown", () => { this.pressActive = true; }, true);
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

  /** #8: spacebar toggles play/stop of the active screen's loop — the drum looper,
   *  the chord Loop, and the ear-training progression loop. Ignored while typing in
   *  an input. preventDefault also stops the page scrolling and stops a still-focused
   *  Play button from re-activating (which would double-toggle). */
  private setupSpacebarShortcut(): void {
    document.addEventListener("keydown", (e) => {
      if (e.code !== "Space" || e.repeat) return;
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.tagName === "SELECT" || t.isContentEditable)) return;
      const sheet = this.state.currentSheet;
      if (sheet === Sheet.SambaLooper) {
        e.preventDefault();
        if (this.samba.isPlaying) this.samba.stop(); else this.samba.start();
      } else if (sheet === Sheet.Loop) {
        e.preventDefault();
        if (this.loop.isLooping) this.loop.stopLoop(); else this.loop.startLoop();
      } else if (sheet === Sheet.EarTraining && this.ear.progSubMode === EarSubMode.Progression) {
        e.preventDefault();
        if (this.ear.isLooping) this.ear.stopLoop(); else this.ear.startLoop();
      }
    });
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
    document.documentElement.classList.toggle("light", !this.state.darkTheme);
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
    if (route !== Sheet.EarTraining && this.ear.isLooping) this.ear.stopLoop();
    if (route !== Sheet.SambaLooper && this.samba.isPlaying) this.samba.stop();

    this.renderNav();
    // Preserve the scroll position of any long scrollable pane across full rebuilds.
    const prevScroll = this.contentEl.querySelector(".et-scroll")?.scrollTop ?? 0;
    clear(this.contentEl);

    if (route === Sheet.Tuner) this.renderTuner();
    else if (route === Sheet.Loop) this.loopUI.render(this.contentEl);
    else if (route === Sheet.EarTraining) this.earUI.render(this.contentEl);
    else if (route === Sheet.SambaLooper) this.sambaUI.render(this.contentEl);
    else if (route === Sheet.Decompose) this.decomposeUI.render(this.contentEl);
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
    return dest === "Neck" && this.state.displayMode !== DisplayMode.None;
  }

  /** "More" is selected whenever the open sheet isn't one of the current 4
   *  tabs (e.g. Tuner/Decompose when not tabbed, or Options). */
  private isMoreSelected(): boolean {
    const sheet = this.state.currentSheet;
    if (sheet === null) return false;
    return !this.state.tabOrder.some((d) => TAB_SHEET[d] === sheet);
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
    for (const dest of this.state.tabOrder) {
      const active = this.isTabSelected(dest);
      const onClick = () => this.state.openSheet(TAB_SHEET[dest]);
      this.railEl.appendChild(this.navItem(TAB_ICON[dest], TAB_LABEL[dest], active, onClick));
      this.tabbarEl.appendChild(this.navItem(TAB_ICON[dest], TAB_LABEL[dest], active, onClick));
    }
    const moreActive = this.isMoreSelected();
    this.railEl.appendChild(this.navItem("more", "More", moreActive, () => this.openMore()));
    this.tabbarEl.appendChild(this.navItem("more", "More", moreActive, () => this.openMore()));
  }

  private openMore(): void { this.moreOpen = true; this.render(); }
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
      const stop = btn("⏹ Stop", () => this.loop.stopLoop(), "btn text");
      stop.style.color = Colors.rootTone;
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
    const strumBtn = btn("Strum", () => this.state.strumPicked(false), "btn primary");
    const arpBtn = btn("Arp", () => this.state.strumPicked(true));
    const clearBtn = btn("Clear", () => this.state.clearPicked());
    (strumBtn as HTMLButtonElement).disabled = !canStrum;
    (arpBtn as HTMLButtonElement).disabled = !canStrum;
    (clearBtn as HTMLButtonElement).disabled = this.state.pickedPositions.size === 0 && this.state.mutedStrings.size === 0;
    return el("div", { class: "context-bar" }, [
      this.muteRow(),
      el("div", { class: "v-gap-8" }),
      el("div", { class: "row" }, [el("div", { class: "spacer", style: "" }, [counts]), strumBtn, arpBtn, clearBtn]),
    ]);
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
    const extra = ALL_TAB_DESTS.filter((d) => !this.state.tabOrder.includes(d));
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
    if (this.moreOpen) {
      this.sheetLayer.appendChild(this.moreSheet());
      if (this.moreStatsOpen) {
        this.sheetLayer.appendChild(renderChallengeStatsOverlay(this.state, () => { this.moreStatsOpen = false; this.render(); }));
      }
      return;
    }
    if (route === Sheet.Fretboard || route === Sheet.Options) {
      const sheet = el("div", { class: "sheet" });
      sheet.appendChild(el("div", { class: "sheet-grabber" }));
      const header = el("div", { class: "sheet-header" }, [
        el("h2", {}, [route === Sheet.Fretboard ? "Fretboard" : "Options"]),
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
      [{ value: DisplayMode.Chord, label: "Chord" }, { value: DisplayMode.Scale, label: "Scale" }, { value: DisplayMode.Pick, label: "Strum" }],
      this.state.displayMode === DisplayMode.None ? DisplayMode.Chord : this.state.displayMode,
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
      sheet.appendChild(el("div", { class: "mono", style: `color:${Colors.textSecondary}` }, [`intervals:  ${intervals}`]));
      // selected position's voicing card
      if (this.state.chordView === ChordScaleView.Positions) {
        const shapes = this.chordShapes();
        const sh = shapes[this.state.chordPositionIndex];
        if (sh) sheet.appendChild(this.shapeCard(sh));
      }
    } else {
      sheet.appendChild(el("div", { style: `color:${Colors.rootTone}` }, ["(chord not recognized)"]));
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
      sheet.appendChild(el("div", { style: `color:${Colors.rootTone}` }, ["(invalid root or scale)"]));
    }
  }

  private fillPickControls(sheet: HTMLElement): void {
    sheet.appendChild(el("div", { style: `color:${Colors.textSecondary}` }, [
      "Tap any fret on the neck to add or remove it from your selection, mute whole strings below, then strum or arpeggiate the set.",
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
    return el("div", { style: `margin-top:10px;background:${Colors.surfaceElev};border-radius:10px;padding:12px` }, [
      el("div", {}, [`${shape.chordName}  ·  ${positionLabel}  ·  span ${shape.fretSpan}${rootTag}`]),
      el("div", { class: "mono" }, [`frets     ${fretsLine}`]),
      el("div", { class: "mono" }, [`notes     ${notesLine}`]),
      el("div", { class: "mono" }, [`intervals ${ivLine}`]),
      el("div", { class: "mono" }, [`fingers   ${fingersLine}`]),
    ]);
  }

  private fillOptionsSheet(sheet: HTMLElement): void {
    const s = this.state;
    // Instrument
    sheet.appendChild(el("div", { style: "font-weight:600" }, ["Instrument"]));
    sheet.appendChild(el("div", { class: "v-gap-8" }));
    sheet.appendChild(segmented<Instrument>(
      [Instrument.Guitar, Instrument.Cavaquinho].map((i) => ({ value: i, label: InstrumentInfo[i].displayName })),
      s.instrument, (v) => s.setInstrument(v),
    ));

    // Tuning
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
        const x = el("span", { style: `margin-left:6px;color:${Colors.rootTone}` }, ["✕"]);
        x.addEventListener("click", (e) => { e.stopPropagation(); s.deleteCustomTuning(name); });
        chip.appendChild(x);
        myRow.appendChild(chip);
      }
      sheet.appendChild(myRow);
    }
    sheet.appendChild(el("div", { class: "mono", style: "margin-top:6px" }, [
      "Open strings (low → high):  " + s.liveTuning.openStrings.map((n) => spellNote(n)).join(" "),
    ]));
    if (s.isEditedTuning) sheet.appendChild(el("div", { style: `color:${Colors.rootTone};font-size:12px` }, ["(unsaved edits)"]));
    sheet.appendChild(this.tuningEditor());

    sheet.appendChild(el("div", { class: "divider-line" }));

    // Display
    sheet.appendChild(el("div", { style: "font-weight:600" }, ["Display"]));
    sheet.appendChild(labelSm("Labels on dots"));
    sheet.appendChild(this.labelModeSeg());
    sheet.appendChild(switchRow("Left-handed", null, s.leftHanded, (v) => s.toggleLeftHanded(v)));
    sheet.appendChild(switchRow("Dark theme", null, s.darkTheme, (v) => s.toggleDarkTheme(v)));
    sheet.appendChild(switchRow(
      "Play note on touch-down",
      "Off (default): notes play on tap-release, so swiping the neck won't sound a note. On: notes fire the instant you touch.",
      s.tapOnTouchDown, (v) => s.setTapOnTouchDown(v),
    ));
    sheet.appendChild(switchRow(
      "Jazz / shell voicings",
      "Drop the 5th (and root for 7+ chords); favor 2-4 note voicings.",
      s.voicingStyle === VoicingStyle.Shell, (v) => s.toggleVoicingStyle(v),
    ));

    sheet.appendChild(el("div", { class: "divider-line" }));

    // Tuner & audio
    sheet.appendChild(el("div", { style: "font-weight:600" }, ["Tuner & audio"]));
    sheet.appendChild(el("div", { style: "margin-top:6px" }, [`A4 reference: ${s.a4Hz} Hz`]));
    sheet.appendChild(slider(435, 445, s.a4Hz, (v) => s.setA4Hz(v)));
    sheet.appendChild(el("div", {}, [`Ring sustain: ${(s.ringSustainMs / 1000).toFixed(1)} s`]));
    sheet.appendChild(slider(300, 4000, s.ringSustainMs, (v) => s.setRingSustainMs(v)));
    sheet.appendChild(el("div", {}, [s.strumMs === 0 ? "Strum spread: struck at once" : `Strum spread: ${s.strumMs} ms`]));
    sheet.appendChild(slider(0, 150, s.strumMs, (v) => s.setStrumMs(v)));
  }

  private tuningEditor(): HTMLElement {
    const s = this.state;
    const card = el("div", { style: `margin-top:8px;background:${Colors.surfaceElev};border-radius:10px;padding:10px` });
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
      el("div", { style: `font-size:12px;color:${Colors.textSecondary}` }, [`A4 = ${s.a4Hz} Hz`]),
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
    const centsEl = el("div", { class: "tuner-cents" }, [""]);
    const hintEl = el("div", { class: "tuner-hint" }, [""]);
    this.tunerNoteEl = noteEl;
    this.tunerCentsEl = centsEl;
    this.tunerHintEl = hintEl;
    noteEl.addEventListener("click", () => {
      if (this.tuner?.midi != null) {
        s.playReferencePitch(this.tuner.midi);
        this.tuner.lockTo(this.tuner.midi, s.ringSustainMs);
      }
    });
    const readout = el("div", { class: "tuner-readout" }, [noteEl, centsEl, hintEl]);
    dialWrap.appendChild(dialCanvas);
    dialWrap.appendChild(readout);
    screen.appendChild(dialWrap);

    // reference row
    const refRow = el("div", { class: "tuner-ref-row" }, [el("span", { style: `font-size:11px;color:${Colors.textSecondary}` }, ["Reference"])]);
    s.liveTuning.openStrings.forEach((n, i) => {
      const b = el("button", { class: "btn ref-btn" }, [
        el("span", { class: "s" }, [`S${stringCount(s.liveTuning) - i}`]),
        el("span", {}, [`${spellPc(midiPitchClass(n.midi))}${midiOctave(n.midi)}`]),
      ]);
      b.addEventListener("click", () => { s.playReferencePitch(n.midi); this.tuner?.lockTo(n.midi, s.ringSustainMs); });
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
      if (this.tunerNoteEl) this.tunerNoteEl.textContent = "🎤";
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
    const inTune = cents !== null && Math.abs(cents) <= 10;
    drawTunerDial(ctx, w, h, cents, inTune);

    if (this.tunerNoteEl && this.tuner.capturing) {
      if (midi !== null) {
        const name = spellPc(midiPitchClass(midi));
        const oct = midiOctave(midi);
        this.tunerNoteEl.innerHTML = "";
        this.tunerNoteEl.appendChild(document.createTextNode(name));
        this.tunerNoteEl.appendChild(el("span", { class: "oct" }, [String(oct)]));
        this.tunerNoteEl.style.color = inTune ? Colors.tuned : Colors.textPrimary;
      } else {
        this.tunerNoteEl.textContent = "—";
        this.tunerNoteEl.style.color = Colors.textPrimary;
      }
    }
    if (this.tunerCentsEl && this.tuner.capturing) {
      this.tunerCentsEl.textContent = cents !== null ? `${cents >= 0 ? "+" : ""}${cents.toFixed(0)} ¢` : "";
      this.tunerCentsEl.style.color = inTune ? Colors.tuned : Colors.textSecondary;
    }
    if (this.tunerHintEl && this.tuner.capturing) {
      this.tunerHintEl.textContent = inTune ? "IN TUNE" : midi !== null ? "tap note to hear reference" : "";
      this.tunerHintEl.style.color = inTune ? Colors.tuned : Colors.textSecondary;
    }
  }

  private sheetLabel(s: Sheet): string {
    switch (s) {
      case Sheet.Fretboard: return "Fretboard";
      case Sheet.Loop: return "Loop";
      case Sheet.Options: return "Options";
      case Sheet.Tuner: return "Tuner";
      case Sheet.EarTraining: return "Ear";
      case Sheet.SambaLooper: return "Drums";
      case Sheet.Decompose: return "Decompose";
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

// Drum-machine screen, ported from app/.../SambaLooperScreen.kt. Pattern-only
// (v2.1.0 restores the pre-Signal interaction, dropping the Signal redesign's
// Mixer/Kit segments — see commit b02d227 on Android): the step grid itself
// (tap a cell to cycle its voice, or clear in Erase mode; long-press/right-
// click clears), a dismissible gesture-legend banner, and a compact
// swing/tap-tempo card. Tapping an instrument's row label opens a voice
// popup (overall + per-voice volume, tap-to-preview, Remove); a "+ Add ▾"
// control in the header row adds instruments from the catalog. Save…/Load…/
// Clear all/Erase/Accent stay in the same header row.

import { SambaLooperState } from "./sambaLooperState";
import { BlocksState } from "./blocksState";
import { EarTrainingState } from "./earTrainingState";
import { Colors } from "./theme";
import { el, btn, slider, segmented } from "./dom";
import { icon } from "./icons";
import { transportDock, toneSheet } from "./transport";
import { AppState } from "./appState";
import {
  PercussionInstrument, PercussionPattern, voicesFor, voiceOf, BUILTIN_PATTERNS, STUDY_PATTERNS,
  PresetTrack, DIVISIONS, encodeBeatFile, decodeBeatFile, BuiltinPattern,
  encodePhraseFile, decodePhraseFile,
} from "../theory";

/** Time signatures offered in the Time dropdown (beatsPerBar / beatUnit). */
const TIME_SIGNATURES: [number, number][] = [
  [2, 4], [3, 4], [4, 4], [5, 4], [6, 8], [3, 8], [12, 8], [2, 2],
];

const LONG_PRESS_MS = 450;

/** Whether the gesture-legend banner has been dismissed. Module-level (not an
 *  instance field) — mirrors Android's `remember { mutableStateOf(false) }`
 *  inside PatternSection, which resets every time that composable enters
 *  composition (i.e. every screen visit); `SambaLooperUI` itself is a single
 *  long-lived instance (see ui.ts), so a plain module-level flag is the
 *  equivalent "resets on reload, not on every rerender" behavior (same
 *  pattern as transport.ts's bpmExpanded/eqExpanded). */
let legendDismissed = false;

export class SambaLooperUI {
  private eraseMode = false;
  private accentMode = false;
  /** Instrument id whose voice popup (overall + per-voice volume, Remove) is open. */
  private openVoiceMenu: string | null = null;
  private addMenuOpen = false;
  private saveOpen = false;
  /** Whether the "＋ Opening ▾" picker (empty / from a preset track) is open. */
  private openingMenuOpen = false;
  private saveName = "";
  /** Shared lane scroll position, preserved across the full re-renders. */
  private laneScrollLeft = 0;
  /** Beats side-panel scroll position, preserved across the full re-renders
   *  (playback rebuilds the DOM every slot — without this the panel snaps to top). */
  private sideScrollTop = 0;
  /** The single active outside-tap popup closer (never stacked). */
  private outsideCloser: ((e: Event) => void) | null = null;
  private toneSheetOpen = false;
  /** Track index currently being drag-reordered, or null. */
  private dragIndex: number | null = null;
  /** Which section the drag started in (drops only land in the same section). */
  private dragFromOpening = false;
  /** Whether the notes editor under the beat header is open. */
  private notesOpen = false;
  /** Caret position + last-edit time of the notes editor (focus survives rebuilds). */
  private notesCaret = 0;
  private lastNotesEditMs = 0;

  /** Which view the screen shows: the step-grid Beat editor or the phrase Blocks. */
  private viewMode: "beat" | "blocks" = "beat";
  /** Blocks: cell whose phrase palette is open (tap a block cell to pick). */
  private blockPick: { track: number; col: number } | null = null;
  private blockLoadOpen = false;
  private blockMergeOpen = false;
  private blockAddOpen = false;
  /** Blocks: render each cell's phrase as a mini 16-step grid (Grid toggle). */
  private blockMiniGrid = false;
  /** Dyn tool (per-slot dynamics): tap a hit to cycle 100→75→50→25 %. */
  private dynMode = false;

  // ---- rectangle select + copy/paste of strikes (web only) ----
  // The rubber-band itself is app-level (ui.ts setupMarquee): it draws anywhere
  // on the screen and reports its rect here; only grid cells (elements tagged
  // with data-sect/data-track/data-slot) are actually selected. More selectable
  // element kinds can join later by tagging themselves the same way.
  /** The selected rectangle (inclusive), or null. */
  private selRect: { inOpening: boolean; t0: number; t1: number; s0: number; s1: number } | null = null;
  /** Cell the mouse is currently over — the Ctrl+V paste anchor. */
  private hoverCell: { track: number; slot: number; inOpening: boolean } | null = null;
  /** Copied region (rows × slots of raw cell values). */
  private cellClipboard: (number | null)[][] | null = null;

  /** Live marquee update from ui.ts: select the grid cells intersecting the
   *  viewport rect. If the rect spans both grids, the section holding most of
   *  the hits wins (a selection is rectangular within ONE section). */
  marqueeSelect(rect: { left: number; top: number; right: number; bottom: number }): void {
    const hits = Array.from(document.querySelectorAll<HTMLElement>(".drum-cell[data-slot]"))
      .map((elm) => ({ elm, r: elm.getBoundingClientRect() }))
      .filter(({ r }) => r.right > rect.left && r.left < rect.right && r.bottom > rect.top && r.top < rect.bottom)
      .map(({ elm }) => ({ sect: elm.dataset.sect!, track: Number(elm.dataset.track), slot: Number(elm.dataset.slot) }));
    if (hits.length === 0) {
      if (this.selRect) { this.selRect = null; this.rerender(); }
      return;
    }
    const openingHits = hits.filter((h) => h.sect === "o").length;
    const inOpening = openingHits > hits.length / 2;
    const sel = hits.filter((h) => (h.sect === "o") === inOpening);
    const next = {
      inOpening,
      t0: Math.min(...sel.map((h) => h.track)), t1: Math.max(...sel.map((h) => h.track)),
      s0: Math.min(...sel.map((h) => h.slot)), s1: Math.max(...sel.map((h) => h.slot)),
    };
    const cur = this.selRect;
    if (!cur || cur.inOpening !== next.inOpening || cur.t0 !== next.t0 || cur.t1 !== next.t1 || cur.s0 !== next.s0 || cur.s1 !== next.s1) {
      this.selRect = next;
      this.rerender();
    }
  }

  /** A plain right-click (no drag) on a grid cell clears it (classic behavior).
   *  Returns false when the element isn't a grid cell (native menu proceeds). */
  rightClickCell(elm: HTMLElement): boolean {
    const { sect, track, slot } = elm.dataset;
    if (sect === undefined || track === undefined || slot === undefined) return false;
    const inOpening = sect === "o";
    const pat = inOpening ? this.samba.opening : this.samba.pattern;
    const inst = pat?.instruments[Number(track)];
    if (!pat || !inst) return false;
    this.selRect = null;
    this.samba.editOpening(inOpening);
    this.samba.clearCell(inst, Number(slot));
    return true;
  }

  /** Ctrl+C: copy the selected rectangle. False = no selection (let native copy run). */
  copySelection(): boolean {
    const r = this.selRect;
    if (!r) return false;
    const pat = r.inOpening ? this.samba.opening : this.samba.pattern;
    if (!pat) return false;
    const rows: (number | null)[][] = [];
    for (let t = r.t0; t <= Math.min(r.t1, pat.instruments.length - 1); t++) {
      const inst = pat.instruments[t];
      const row: (number | null)[] = [];
      for (let s = r.s0; s <= Math.min(r.s1, pat.slots - 1); s++) row.push(pat.grid.get(inst.id)![s]);
      rows.push(row);
    }
    this.cellClipboard = rows;
    return rows.length > 0;
  }

  /** Ctrl+V: paste the copied region anchored at the hovered cell. */
  pasteAtHover(): boolean {
    const h = this.hoverCell, cb = this.cellClipboard;
    if (!h || !cb) return false;
    this.samba.editOpening(h.inOpening);
    this.samba.pasteCells(h.track, h.slot, cb);
    return true;
  }

  constructor(
    private samba: SambaLooperState,
    private blocks: BlocksState,
    private state: AppState,
    private ear: EarTrainingState,
    private onBack: () => void,
  ) {}

  render(container: HTMLElement): void {
    const s = this.samba;
    const screen = el("div", { class: "tool-screen" });

    // header
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("div", { class: "tool-title" }, ["DRUMS"]),
      btn("Back", () => { s.stop(); this.blocks.stop(); this.onBack(); }),
    ]));

    screen.appendChild(el("div", { class: "v-gap-8" }));

    // [Beat | Blocks] — the step-grid editor vs. the phrase sequencer.
    screen.appendChild(el("div", { style: "margin-bottom:6px" }, [
      segmented<"beat" | "blocks">(
        [{ value: "beat", label: "Beat" }, { value: "blocks", label: "Blocks" }],
        this.viewMode,
        (v) => {
          this.viewMode = v;
          if (v === "beat") this.blocks.stop(); else this.samba.stop();
          this.rerender();
        },
      ),
    ]));

    if (this.viewMode === "blocks") {
      const body = el("div", { class: "et-scroll" });
      body.appendChild(this.blocksBody());
      screen.appendChild(el("div", { class: "drum-main" }, [body]));
      screen.appendChild(transportDock({
        playing: this.blocks.isPlaying,
        onPlayStop: () => this.blocks.toggle(),
        bpm: this.blocks.bpm,
        onBpm: (v) => this.blocks.setBpm(v),
        inlineBpm: true,
        toneLabel: this.state.sound,
        onTone: () => { this.toneSheetOpen = true; this.rerender(); },
      }));
      container.appendChild(screen);
      if (this.toneSheetOpen) container.appendChild(toneSheet(this.state, this.ear, () => { this.toneSheetOpen = false; this.rerender(); }));
      return;
    }

    // Main row: the scrolling pattern editor + a constantly-open, scrollable
    // beats side panel (grooves / study / saved — replaces the Load… popup).
    const body = el("div", { class: "et-scroll" });
    body.appendChild(this.patternSection());
    screen.appendChild(el("div", { class: "drum-main" }, [body, this.beatSidebar()]));

    // Voice palette for the selected track — pinned above the transport dock so
    // it stays visible while the grid scrolls.
    const selInst = s.editPattern.instruments.find((i) => i.id === s.selectedTrackId);
    if (selInst) screen.appendChild(this.paletteBar(selInst));

    // Transport dock (Signal move #2): BPM lives here now — samba reads `bpm`
    // live every slot (see SambaLooperState.start()), so no restart is needed
    // when it changes, unlike the Ear progression loop.
    screen.appendChild(transportDock({
      playing: s.isPlaying,
      onPlayStop: () => { if (s.isPlaying) s.stop(); else s.start(); },
      bpm: s.bpm,
      onBpm: (v) => s.setBpm(v),
      inlineBpm: true,
      toneLabel: this.state.sound,
      onTone: () => { this.toneSheetOpen = true; this.rerender(); },
    }));

    container.appendChild(screen);
    if (this.toneSheetOpen) container.appendChild(toneSheet(this.state, this.ear, () => { this.toneSheetOpen = false; this.rerender(); }));

    // Restore the side panel's scroll position (rebuilt fresh on every rerender).
    const side = screen.querySelector<HTMLElement>(".drum-side");
    if (side) side.scrollTop = this.sideScrollTop;

    // Keep all step lanes scrolled together (fixed-size cells scroll horizontally
    // so they stay a consistent size regardless of viewport width).
    const lanes = Array.from(screen.querySelectorAll<HTMLElement>(".drum-cells"));
    for (const lane of lanes) {
      lane.scrollLeft = this.laneScrollLeft;
      lane.addEventListener("scroll", () => {
        this.laneScrollLeft = lane.scrollLeft;
        for (const other of lanes) if (other !== lane && other.scrollLeft !== lane.scrollLeft) other.scrollLeft = lane.scrollLeft;
      });
    }
    // Auto-follow the playhead while playing: center the sounding column.
    if (s.isPlaying && lanes.length) {
      const ph = lanes[0].querySelector<HTMLElement>(".drum-cell.playhead");
      if (ph) {
        const target = Math.max(ph.offsetLeft - lanes[0].clientWidth / 2, 0);
        this.laneScrollLeft = target;
        for (const lane of lanes) lane.scrollLeft = target;
      }
    }

    // Close any open popup when the next tap lands outside a popup (so you don't
    // have to tap the same trigger again). A SINGLE tracked listener — re-renders
    // while a popup is open must not stack additional listeners.
    if (this.outsideCloser) {
      document.removeEventListener("pointerdown", this.outsideCloser, true);
      this.outsideCloser = null;
    }
    if (this.openVoiceMenu !== null || this.addMenuOpen || this.saveOpen || this.openingMenuOpen) {
      const onDoc = (e: Event) => {
        if (!(e.target as HTMLElement).closest(".drum-voice-pop, .drum-load-pop")) {
          document.removeEventListener("pointerdown", onDoc, true);
          if (this.outsideCloser === onDoc) this.outsideCloser = null;
          this.openVoiceMenu = null; this.addMenuOpen = false; this.saveOpen = false; this.openingMenuOpen = false;
          this.rerender();
        }
      };
      this.outsideCloser = onDoc;
      setTimeout(() => { if (this.outsideCloser === onDoc) document.addEventListener("pointerdown", onDoc, true); }, 0);
    }
  }

  private rerender(): void { (this.samba as unknown as { deps: { onChange: () => void } }).deps.onChange(); }

  // ---------- PATTERN section ----------

  private patternSection(): HTMLElement {
    const s = this.samba;
    const wrap = el("div", {});

    // Header row: Erase / Accent / Save… / Load… / Clear all / + Add ▾.
    const erase = this.eraseMode
      ? btn("Erase ✓", () => { this.eraseMode = false; this.rerender(); }, "btn primary")
      : btn("Erase", () => { this.eraseMode = true; this.accentMode = false; this.dynMode = false; this.rerender(); });
    const accent = this.accentMode
      ? btn("Accent ✓", () => { this.accentMode = false; this.rerender(); }, "btn primary")
      : btn("Accent", () => { this.accentMode = true; this.eraseMode = false; this.dynMode = false; this.rerender(); });
    // Dyn: tap a hit to cycle its per-slot volume 100 → 75 → 50 → 25 %.
    const dyn = this.dynMode
      ? btn("Dyn ✓", () => { this.dynMode = false; this.rerender(); }, "btn primary")
      : btn("Dyn", () => { this.dynMode = true; this.eraseMode = false; this.accentMode = false; this.rerender(); });
    const undoBtn = btn("↶ Undo", () => { s.undo(); this.rerender(); });
    if (!s.canUndo) undoBtn.disabled = true;

    // Beat header: current beat name + tempo (set by Load / Save / Import). The
    // opening (when present) renders as its own grid section above the loop;
    // "＋ Opening ▾" starts one empty or pre-filled with a preset chunk (entrada).
    const editToggle = el("span", { class: "drum-edit-toggle", style: "position:relative" });
    if (!s.opening) {
      editToggle.appendChild(btn(this.openingMenuOpen ? "＋ Opening ✕" : "＋ Opening ▾", () => {
        this.openingMenuOpen = !this.openingMenuOpen; this.addMenuOpen = false; this.saveOpen = false; this.rerender();
      }));
      if (this.openingMenuOpen) {
        const pop = el("div", { class: "drum-load-pop", style: "right:0" });
        const emptyRow = el("div", { class: "lrow" }, ["(empty opening)"]);
        emptyRow.addEventListener("click", () => { this.openingMenuOpen = false; s.addOpening(); });
        pop.appendChild(emptyRow);
        pop.appendChild(el("div", { class: "lrow", style: "color:var(--text-secondary);cursor:default;font-size:11px" }, ["From a preset track"]));
        for (const p of this.blocks.allPresets()) {
          const row = el("div", { class: "lrow" }, [`★ ${p.label}`]);
          row.addEventListener("click", () => { this.openingMenuOpen = false; s.addOpeningFromPreset(p); });
          pop.appendChild(row);
        }
        editToggle.appendChild(pop);
      }
    }
    wrap.appendChild(el("div", { class: "drum-beat-header" }, [
      el("span", { class: "drum-beat-name" }, [s.loadedName ?? "Untitled beat"]),
      el("span", { class: "drum-beat-bpm" }, [`${s.bpm} BPM`]),
      el("span", { style: "flex:1" }),
      editToggle,
    ]));

    const notesBtn = btn(s.beatNotes || this.notesOpen ? "📝 Notes ✓" : "📝 Notes",
      () => { this.notesOpen = !this.notesOpen; this.rerender(); }, s.beatNotes ? "btn primary" : "btn");
    wrap.appendChild(el("div", { class: "et-row-gap" }, [
      erase, accent, dyn, this.saveControl(), btn("Clear all", () => s.clearAll()),
      btn("✕ Tracks", () => s.removeAllTracks(), "btn", "remove ALL tracks (Undo restores them)"),
      undoBtn, notesBtn, btn("Export", () => this.exportBeat()), btn("Import", () => this.importBeat()),
      this.addInstrumentControl(),
    ]));

    // Notes editor: free text saved + exported with the beat, auto-shown when the
    // loaded beat carries notes. Typing writes state silently (no rebuild), and a
    // rebuild mid-typing (e.g. the playhead) restores focus + caret.
    if (this.notesOpen || s.beatNotes) {
      const ta = el("textarea", { class: "drum-notes", placeholder: "Notes for this beat — saved and exported with it", rows: "3" }) as HTMLTextAreaElement;
      ta.value = s.beatNotes;
      ta.addEventListener("input", () => {
        s.beatNotes = ta.value;
        this.notesCaret = ta.selectionStart;
        this.lastNotesEditMs = Date.now();
      });
      wrap.appendChild(ta);
      if (Date.now() - this.lastNotesEditMs < 3000) {
        setTimeout(() => {
          if (document.activeElement === document.body && ta.isConnected) {
            ta.focus();
            ta.setSelectionRange(this.notesCaret, this.notesCaret);
          }
        }, 0);
      }
    }

    // Dismissible gesture-legend banner (module-level flag — see the
    // `legendDismissed` doc above).
    if (!legendDismissed) {
      const closeBtn = el("button", { class: "tune-btn", "aria-label": "Dismiss" }, [icon("close", 16)]);
      closeBtn.addEventListener("click", () => { legendDismissed = true; this.rerender(); });
      const legendLine = (head: string, rest: string) => el("div", { class: "et-muted", style: "line-height:1.5" }, [
        el("b", { style: "color:var(--text-primary)" }, [head]), rest,
      ]);
      wrap.appendChild(el("div", { class: "et-card drum-legend", style: `background:var(--surface2)` }, [
        el("div", { class: "row", style: "align-items:flex-start" }, [
          el("div", { style: "flex:1" }, [
            legendLine("Grid:  ", "tap a cell = cycle its voice · long-press (or right-click) = clear it"),
            legendLine("Accent tool:  ", "turn it on, then tap a hit → the hit plays louder (teal ring)"),
            legendLine("Dyn tool:  ", "turn it on, then tap a hit → its volume cycles 100 → 75 → 50 → 25 % (shown faded)"),
            legendLine("Erase tool:  ", "turn it on, then tap any cell → cleared"),
            legendLine("Copy strikes:  ", "right-click + drag to select a rectangle → Ctrl+C copies · hover a target cell → Ctrl+V pastes"),
          ]),
          closeBtn,
        ]),
      ]));
    }

    // loop setup: bars / time signature / division + translate
    wrap.appendChild(this.loopSetupControls());
    wrap.appendChild(el("div", { class: "divider-line" }));

    // ---- grids: the opening (when present) sits ON TOP, separated from the
    // loop by a bold rule, so it reads as "played once, then the loop".
    const op = s.opening;
    if (op) {
      const removeBtn = btn("✕", () => s.removeOpening(), "btn text");
      removeBtn.title = "Remove opening";
      const minusB = btn("−", () => { s.editOpening(true); s.setBars(op.meter.bars - 1); });
      const plusB = btn("+", () => { s.editOpening(true); s.setBars(op.meter.bars + 1); });
      wrap.appendChild(el("div", { class: "drum-section-head opening" }, [
        el("span", { class: "drum-section-title" }, ["OPENING — plays once ▶¹"]),
        el("span", { class: "drum-setup-label", style: "margin-left:12px" }, ["Bars"]),
        minusB, el("span", { class: "drum-setup-val" }, [String(op.meter.bars)]), plusB,
        el("span", { style: "flex:1" }),
        removeBtn,
      ]));
      wrap.appendChild(this.countRow(op));
      op.instruments.forEach((inst, i) => wrap.appendChild(this.instrumentRow(inst, i, op, true)));
      wrap.appendChild(el("div", { class: "drum-caption" }, [op.meter.describe()]));
      // Bold divider between the opening and the loop.
      wrap.appendChild(el("div", { class: "drum-opening-divider" }));
      wrap.appendChild(el("div", { class: "drum-section-head" }, [
        el("span", { class: "drum-section-title" }, ["LOOP"]),
      ]));
    }
    const loop = s.pattern;
    wrap.appendChild(this.countRow(loop));
    loop.instruments.forEach((inst, i) => wrap.appendChild(this.instrumentRow(inst, i, loop, false)));
    if (loop.instruments.length === 0) {
      wrap.appendChild(el("div", { class: "et-muted", style: "margin:10px 0" }, [
        "Clean slate — add a track with ＋ Add ▾, or tap a groove / track preset in the side panel.",
      ]));
    }
    wrap.appendChild(el("div", { class: "drum-caption" }, [loop.meter.describe()]));

    // Compact card: tap-tempo + swing.
    const swingActive = s.meter.beatUnit === 4 && s.meter.division === 16;
    const swingSlider = slider(0, 100, s.swing, (v) => s.setSwing(v));
    swingSlider.disabled = !swingActive;
    const metroBtn = btn(s.metronomeOn ? "Metronome ✓" : "Metronome", () => { s.toggleMetronome(); this.rerender(); }, s.metronomeOn ? "btn primary" : "btn");
    wrap.appendChild(el("div", { class: "et-card", style: `background:var(--surface2);margin-top:8px` }, [
      el("div", { class: "row", style: "gap:8px" }, [
        btn("Tap tempo", () => s.tapTempo()), metroBtn,
      ]),
      el("div", { class: "label-sm" }, [
        !swingActive ? "Swing: 1/16 grid only" : s.swing === 0 ? "Swing: straight" : `Swing: ${s.swing}% (16ths)`,
      ]),
      swingSlider,
    ]));

    return wrap;
  }

  /** Bars stepper + time-signature / division dropdowns + loop-translate control. */
  private loopSetupControls(): HTMLElement {
    const s = this.samba;
    const m = s.meter;

    // Bars −/+ stepper
    const minus = btn("−", () => s.setBars(m.bars - 1));
    if (m.bars <= 1) minus.disabled = true;
    const plus = btn("+", () => s.setBars(m.bars + 1));
    if (m.bars >= 8) plus.disabled = true;
    const bars = el("div", { class: "drum-setup-item" }, [
      el("span", { class: "drum-setup-label" }, ["Bars"]),
      minus, el("span", { class: "drum-setup-val" }, [String(m.bars)]), plus,
    ]);

    // Time-signature dropdown
    const timeSel = el("select", { class: "drum-select" }) as HTMLSelectElement;
    for (const [b, u] of TIME_SIGNATURES) {
      const opt = el("option", { value: `${b}/${u}` }, [`${b}/${u}`]) as HTMLOptionElement;
      if (b === m.beatsPerBar && u === m.beatUnit) opt.selected = true;
      timeSel.appendChild(opt);
    }
    timeSel.addEventListener("change", () => {
      const [b, u] = timeSel.value.split("/").map((x) => parseInt(x, 10));
      s.setTimeSignature(b, u);
    });
    const time = el("div", { class: "drum-setup-item" }, [
      el("span", { class: "drum-setup-label" }, ["Time"]), timeSel,
    ]);

    // Division dropdown (only divisions that are multiples of the current beat unit)
    const divSel = el("select", { class: "drum-select" }) as HTMLSelectElement;
    for (const d of DIVISIONS.filter((x) => x % m.beatUnit === 0)) {
      const opt = el("option", { value: String(d) }, [`1/${d}`]) as HTMLOptionElement;
      if (d === m.division) opt.selected = true;
      divSel.appendChild(opt);
    }
    divSel.addEventListener("change", () => s.setDivision(parseInt(divSel.value, 10)));
    const division = el("div", { class: "drum-setup-item" }, [
      el("span", { class: "drum-setup-label" }, ["Note"]), divSel,
    ]);

    // Shift (translate) control: ◀ / ▶ / numeric + Go
    const shiftInput = el("input", { type: "text", value: "1", class: "drum-shift-input" }) as HTMLInputElement;
    shiftInput.addEventListener("input", () => {
      shiftInput.value = shiftInput.value.replace(/[^0-9-]/g, "").slice(0, 3);
    });
    const shift = el("div", { class: "drum-setup-item" }, [
      el("span", { class: "drum-setup-label" }, ["Shift"]),
      btn("◀", () => s.translate(-1)),
      btn("▶", () => s.translate(1)),
      shiftInput,
      btn("Go", () => { const n = parseInt(shiftInput.value, 10); if (!Number.isNaN(n)) s.translate(n); }),
    ]);

    return el("div", { class: "drum-setup-row" }, [bars, time, division, shift]);
  }

  /** Count row above a grid: "1 e & a  2 e & a …" aligned with the step cells
   *  (rendered as a non-interactive cells lane so it scroll-syncs with them). */
  private countRow(pat: PercussionPattern): HTMLElement {
    const { slotsPerBeat, slotsPerBar, beatsPerBar } = pat.meter;
    const perBeat = Math.max(slotsPerBeat, 1);
    const sub16 = ["", "e", "&", "a"];
    const lane = el("div", { class: "drum-cells count-lane" });
    for (let slot = 0; slot < pat.slots; slot++) {
      const pos = slot % perBeat;
      const label = pos === 0
        ? String((Math.floor(slot / perBeat) % beatsPerBar) + 1)
        : perBeat === 4 ? sub16[pos]
        : perBeat === 2 ? "&"
        : "·";
      lane.appendChild(el("div", { class: pos === 0 ? "drum-cell count beat" : "drum-cell count" }, [label]));
      if ((slot + 1) % slotsPerBeat === 0 && slot !== pat.slots - 1) {
        const isBar = (slot + 1) % slotsPerBar === 0;
        lane.appendChild(el("div", { class: isBar ? "drum-div bar" : "drum-div", style: "opacity:0" }));
      }
    }
    return el("div", { class: "drum-row count-row" }, [el("div", { class: "drum-label" }), lane]);
  }

  /** Instrument row of the given pattern section (loop or opening): name label
   *  (tap → select track / voice palette) + Mute/Solo + its step cells. Any
   *  interaction first makes its section the edit target. */
  private instrumentRow(inst: PercussionInstrument, index: number, pat: PercussionPattern, inOpening: boolean): HTMLElement {
    const s = this.samba;
    const audible = s.isAudible(inst);

    const selected = s.selectedTrackId === inst.id && s.editingOpening === inOpening;
    // A track with its own swing shows a ~N% badge (dim while global swing
    // overrides it — global > 0 wins).
    const tSwing = pat.trackSwingOf(inst.id);
    const swingBadge = tSwing > 0 ? ` ~${tSwing}%` : "";
    const name = el("span", { class: selected ? "name track-sel" : "name", title: "tap to select · drag to reorder · right-click to remove" },
      [inst.displayName + (selected ? " ✓" : "")]);
    if (swingBadge) {
      name.appendChild(el("span", {
        style: `font-size:10px;opacity:${s.swing > 0 ? 0.35 : 0.8}`,
        title: s.swing > 0 ? "track swing (overridden by the global swing)" : "track swing",
      }, [swingBadge]));
    }
    // Tap the name to select the track — opens the voice palette at the bottom.
    name.addEventListener("click", () => { this.openVoiceMenu = null; s.editOpening(inOpening); s.selectTrack(inst.id); });
    // Right-click the track name to remove it.
    name.addEventListener("contextmenu", (e) => { e.preventDefault(); this.openVoiceMenu = null; s.editOpening(inOpening); s.removeInstrument(inst); this.rerender(); });
    const labelInner = el("div", {}, [name]);
    if (this.openVoiceMenu === inst.id && s.editingOpening === inOpening) labelInner.appendChild(this.voicePopup(inst));

    const mTag = el("button", { class: s.muted.has(inst.id) ? "ms-tag on-m" : "ms-tag" }, ["M"]);
    mTag.addEventListener("click", () => s.toggleMute(inst));
    const sTag = el("button", { class: s.soloed.has(inst.id) ? "ms-tag on-s" : "ms-tag" }, ["S"]);
    sTag.addEventListener("click", () => s.toggleSolo(inst));
    const label = el("div", { class: audible ? "drum-label" : "drum-label dim", style: "position:relative;cursor:grab", draggable: "true" }, [
      labelInner,
      el("div", { class: "drum-ms" }, [mTag, sTag]),
    ]);
    // Drag the label to reorder the track within its section. A press starting
    // inside the mixer popup (its sliders live in this draggable label) must
    // NOT become an HTML5 drag — that hijacked every mixer-slider drag.
    label.addEventListener("pointerdown", (e) => {
      label.draggable = !(e.target as HTMLElement).closest(".drum-voice-pop");
    });
    label.addEventListener("dragstart", (e) => { this.dragIndex = index; this.dragFromOpening = inOpening; e.dataTransfer?.setData("text/plain", String(index)); });
    label.addEventListener("dragend", () => { this.dragIndex = null; });

    // cells
    const slots = pat.slots;
    const { slotsPerBeat, slotsPerBar } = pat.meter;
    const cells = el("div", { class: audible ? "drum-cells" : "drum-cells dim" });
    const perBeat = Math.max(slotsPerBeat, 1);
    for (let slot = 0; slot < slots; slot++) {
      cells.appendChild(this.cell(inst, slot, Math.floor(slot / perBeat), slot % perBeat === 0, pat, inOpening, index));
      // Beat separators: a visible vertical rule after each beat (each group of four
      // 16ths), heavier at bar lines, so the quarter-note divisions read clearly.
      if ((slot + 1) % slotsPerBeat === 0 && slot !== slots - 1) {
        const isBar = (slot + 1) % slotsPerBar === 0;
        cells.appendChild(el("div", { class: isBar ? "drum-div bar" : "drum-div" }));
      }
    }

    const row = el("div", { class: "drum-row" }, [label, cells]);
    // Drop target: reorder the dragged track to this row's position (same section only).
    row.addEventListener("dragover", (e) => { if (this.dragIndex !== null && this.dragFromOpening === inOpening) e.preventDefault(); });
    row.addEventListener("drop", (e) => {
      e.preventDefault();
      if (this.dragIndex !== null && this.dragFromOpening === inOpening && this.dragIndex !== index) {
        s.editOpening(inOpening);
        s.reorderInstrument(this.dragIndex, index);
      }
      this.dragIndex = null;
    });
    return row;
  }

  /** UNTOUCHED gesture logic (tap = cycle/erase/accent; long-press/right-click
   *  = clear) — per-voice multicolor fill restored (voice 0/1/else ->
   *  primary/scaleTone/chordTone), the pre-Signal mapping. */
  private cell(inst: PercussionInstrument, slot: number, beatIndex: number, isBeatStart: boolean, pat: PercussionPattern, inOpening: boolean, trackIndex: number): HTMLElement {
    const s = this.samba;
    const voice = pat.voiceAt(inst, slot);
    const accented = pat.isAccented(inst, slot);
    // Playhead lights the section that's actually sounding: the opening rows
    // during the opening pass, the loop rows afterwards. Tracks with their own
    // swing carry their OWN playhead (they anticipate the master clock).
    const isPlayhead = s.isPlaying && s.playheadSlotFor(inst) === slot && s.playingOpening === inOpening;
    // Empty cells brightened (were near-invisible on black) and tinted per beat-group
    // so the quarter-note grouping reads at a glance: first 16th of each beat is
    // brightest; alternating beats step between two shades.
    const emptyFill = isBeatStart ? "rgba(120,128,144,0.55)"
      : beatIndex % 2 === 0 ? "rgba(120,128,144,0.42)"
      : "rgba(120,128,144,0.30)";
    const fill = voice === null ? emptyFill
      : voice === 0 ? Colors.primary
      : voice === 1 ? Colors.scaleTone
      : Colors.chordTone;
    // Per-slot dynamics: quieter hits render faded (Dyn tool cycles the level).
    const dynLevel = voice === null ? 0 : pat.dynLevelAt(inst, slot);
    const dynStyle = dynLevel > 0 ? `;opacity:${(1 - 0.22 * dynLevel).toFixed(2)}` : "";
    const r = this.selRect;
    const inSel = r !== null && r.inOpening === inOpening &&
      trackIndex >= r.t0 && trackIndex <= r.t1 && slot >= r.s0 && slot <= r.s1;
    const cls = "drum-cell" + (isPlayhead ? " playhead" : "") + (accented ? " accent" : "") + (inSel ? " sel" : "");
    const c = el("div", {
      class: cls,
      style: `background:${fill}${dynStyle}`,
      title: dynLevel > 0 ? `${100 - dynLevel * 25}%` : "",
      // Marquee-selectable element tags (see ui.ts setupMarquee).
      "data-sect": inOpening ? "o" : "l",
      "data-track": String(trackIndex),
      "data-slot": String(slot),
    }, [voice !== null ? voiceOf(inst, voice).glyph : ""]);

    // tap = cycle/erase/accent; long-press = clear. The RIGHT button is handled
    // app-wide (ui.ts setupMarquee): drag = rectangle selection, plain click on
    // a cell = clear. Any edit first targets this cell's section.
    let longPressed = false;
    let timer: number | undefined;
    c.addEventListener("pointerdown", (e) => {
      if ((e as PointerEvent).button !== 0) return;
      // A left-click anywhere dismisses an existing selection.
      if (this.selRect) { this.selRect = null; this.rerender(); }
      longPressed = false;
      timer = window.setTimeout(() => { longPressed = true; s.editOpening(inOpening); s.clearCell(inst, slot); }, LONG_PRESS_MS);
    });
    const cancel = () => { if (timer) { clearTimeout(timer); timer = undefined; } };
    c.addEventListener("pointerenter", () => {
      this.hoverCell = { track: trackIndex, slot, inOpening };
    });
    c.addEventListener("pointerup", (e) => {
      if ((e as PointerEvent).button !== 0) return;
      cancel();
      if (longPressed) return;
      s.editOpening(inOpening);
      if (this.eraseMode) s.clearCell(inst, slot);
      else if (this.accentMode) s.toggleAccent(inst, slot);
      else if (this.dynMode) s.dynCycle(inst, slot);
      // Selected track follows the palette brush; other tracks keep cycling.
      else if (s.selectedTrackId === inst.id) s.applyBrush(inst, slot);
      else s.toggleSlot(inst, slot);
    });
    c.addEventListener("pointerleave", cancel);
    c.addEventListener("pointercancel", cancel);
    return c;
  }

  // ---------- BLOCKS view ----------

  /** Short chip label for a phrase: drop the "Instrument — " prefix. */
  private phraseShort(p: PresetTrack): string {
    const i = p.label.indexOf("— ");
    return i < 0 ? p.label : p.label.substring(i + 2);
  }

  /** The Blocks phrase sequencer: block header (name / phrases / save / load /
   *  merge / + track), the tracks × phrase-columns grid (tap a cell to pick its
   *  phrase from the palette below), and the playing column highlight. */
  private blocksBody(): HTMLElement {
    const b = this.blocks;
    const blk = b.block;
    const wrap = el("div", {});

    // Header: name + phrase-count stepper.
    const nameInput = el("input", { type: "text", value: blk.name, class: "block-name" }) as HTMLInputElement;
    nameInput.addEventListener("change", () => b.rename(nameInput.value.trim() || "Block"));
    const minus = btn("−", () => b.setPhraseCount(blk.phraseCount - 1));
    if (blk.phraseCount <= 1) minus.disabled = true;
    const plus = btn("+", () => b.setPhraseCount(blk.phraseCount + 1));
    if (blk.phraseCount >= 8) plus.disabled = true;
    wrap.appendChild(el("div", { class: "et-row-gap" }, [
      nameInput,
      el("span", { class: "drum-setup-label" }, ["Phrases"]),
      minus, el("span", { class: "drum-setup-val" }, [String(blk.phraseCount)]), plus,
    ]));

    // Actions: save / load / merge / clear / + track.
    const loadWrap = el("div", { style: "position:relative" });
    loadWrap.appendChild(btn(this.blockLoadOpen ? "Load ✕" : "Load…", () => { this.blockLoadOpen = !this.blockLoadOpen; this.blockMergeOpen = false; this.blockAddOpen = false; this.rerender(); }));
    if (this.blockLoadOpen) {
      const pop = el("div", { class: "drum-load-pop" });
      const saved = b.savedBlocks();
      for (const [name, blkSaved] of saved) {
        const del = el("button", { class: "btn text" }, ["✕"]);
        del.addEventListener("click", (e) => { e.stopPropagation(); b.deleteSaved(name); this.rerender(); });
        const row = el("div", { class: "lrow" }, [el("span", { style: "flex:1" }, [name]), del]);
        row.addEventListener("click", () => { b.loadBlock(blkSaved); this.blockLoadOpen = false; this.rerender(); });
        pop.appendChild(row);
      }
      if (saved.size === 0) pop.appendChild(el("div", { class: "lrow", style: "color:var(--text-secondary)" }, ["(no saved blocks yet)"]));
      loadWrap.appendChild(pop);
    }
    const mergeWrap = el("div", { style: "position:relative" });
    mergeWrap.appendChild(btn(this.blockMergeOpen ? "Merge ✕" : "Merge with…", () => { this.blockMergeOpen = !this.blockMergeOpen; this.blockLoadOpen = false; this.blockAddOpen = false; this.rerender(); }));
    if (this.blockMergeOpen) {
      const pop = el("div", { class: "drum-load-pop" });
      const candidates = b.mergeCandidates();
      for (const cand of candidates) {
        const row = el("div", { class: "lrow" }, [cand.name]);
        row.addEventListener("click", () => { b.mergeWith(cand.block); this.blockMergeOpen = false; this.rerender(); });
        pop.appendChild(row);
      }
      if (candidates.length === 0) pop.appendChild(el("div", { class: "lrow", style: "color:var(--text-secondary)" }, [`(no saved blocks with ${blk.phraseCount} phrases)`]));
      mergeWrap.appendChild(pop);
    }
    const addWrap = el("div", { style: "position:relative" });
    addWrap.appendChild(btn(this.blockAddOpen ? "+ Track ✕" : "+ Track ▾", () => { this.blockAddOpen = !this.blockAddOpen; this.blockLoadOpen = false; this.blockMergeOpen = false; this.rerender(); }, "btn primary"));
    if (this.blockAddOpen) {
      const pop = el("div", { class: "drum-load-pop" });
      for (const inst of b.instrumentsToAdd()) {
        const row = el("div", { class: "lrow" }, [inst.displayName]);
        row.addEventListener("click", () => { b.addTrack(inst); this.blockAddOpen = false; this.rerender(); });
        pop.appendChild(row);
      }
      addWrap.appendChild(pop);
    }
    const metro = btn(b.metronomeOn ? "Metronome ✓" : "Metronome", () => { b.toggleMetronome(); this.rerender(); }, b.metronomeOn ? "btn primary" : "btn");
    const gridToggle = btn(this.blockMiniGrid ? "Grid ✓" : "Grid",
      () => { this.blockMiniGrid = !this.blockMiniGrid; this.rerender(); },
      this.blockMiniGrid ? "btn primary" : "btn",
      "show each phrase as a mini 16-step grid");
    wrap.appendChild(el("div", { class: "et-row-gap" }, [
      btn("Save block", () => b.saveCurrent()), loadWrap, mergeWrap,
      btn("Export", () => this.exportBlock()), btn("Import", () => this.importBeat()),
      btn("Clear", () => b.clear()), metro, gridToggle, addWrap,
    ]));

    if (blk.tracks.length === 0) {
      wrap.appendChild(el("div", { class: "et-muted", style: "margin-top:14px" }, [
        "A block sequences phrases: add a track (instrument), then tap its cells to place phrases — e.g. entrada → variation → teleco-teco → variation. Each phrase plays with its own swing; the block loops.",
      ]));
      return wrap;
    }

    // Grid: one COLUMN per track (instrument), phrases stacked VERTICALLY —
    // time flows downward. Row ▶¹ = the opening (plays instead of phrase 1 on
    // the block's first pass only); rows 1..N = the looped phrases.
    const cellBtn = (ti: number, c: number): HTMLElement => {
      const t = blk.tracks[ti];
      const isOpening = c === -1;
      const phrase = isOpening ? (t.opening ?? null) : t.cells[c];
      const active = b.isPlaying && (isOpening
        ? b.openingPass && b.currentCol === 0 && !!t.opening
        : b.currentCol === c && !(c === 0 && b.openingPass && t.opening));
      const picking = this.blockPick?.track === ti && this.blockPick?.col === c;
      const cls = "block-cell" + (isOpening ? " opening" : "") + (active ? " playing" : "") + (picking ? " picking" : "") + (phrase ? "" : " empty");
      const badges = phrase?.swing ? ` ~${phrase.swing}%` : "";
      const text = phrase ? this.phraseShort(phrase) + badges + (phrase.note ? " ※" : "") : (isOpening ? "▶¹" : "＋");
      const content: (HTMLElement | string)[] = phrase && this.blockMiniGrid
        ? [el("div", { class: "mini-name" }, [text]), this.miniPhraseGrid(phrase)]
        : [text];
      const cell = el("button", {
        class: cls,
        title: isOpening ? "Opening: plays instead of phrase 1 on the first pass only" : phrase?.note ?? "",
      }, content);
      cell.addEventListener("click", () => {
        this.blockPick = picking ? null : { track: ti, col: c };
        this.rerender();
      });
      return cell;
    };
    const headRow = el("div", { class: "block-grid-row" }, [el("div", { class: "block-rowlabel" }, [""])]);
    blk.tracks.forEach((t, ti) => {
      const rm = el("button", { class: "btn text", title: "Remove track" }, ["✕"]);
      rm.addEventListener("click", () => { this.blockPick = null; b.removeTrack(ti); });
      headRow.appendChild(el("div", { class: "block-col-head" }, [
        el("span", { class: "block-track-label" }, [t.instrument.displayName]), rm,
      ]));
    });
    wrap.appendChild(headRow);
    for (let c = -1; c < blk.phraseCount; c++) {
      const row = el("div", { class: c === -1 ? "block-grid-row opening-row" : "block-grid-row" }, [
        el("div", { class: "block-rowlabel" }, [c === -1 ? "▶¹" : String(c + 1)]),
      ]);
      blk.tracks.forEach((_, ti) => row.appendChild(cellBtn(ti, c)));
      wrap.appendChild(row);
    }

    // Phrase palette for the picked cell (stays open so the swing can be tuned).
    const pick = this.blockPick;
    if (pick && pick.track < blk.tracks.length) {
      const track = blk.tracks[pick.track];
      const current = pick.col === -1 ? (track.opening ?? null) : track.cells[pick.col];
      const chips = el("div", { class: "pal-chips" });
      const noneChip = el("button", { class: current ? "pal-chip" : "pal-chip on" }, ["(empty)"]);
      noneChip.addEventListener("click", () => { b.setCell(pick.track, pick.col, null); this.rerender(); });
      chips.appendChild(noneChip);
      for (const p of b.phrasesFor(track.instrument)) {
        const sel = current?.label === p.label;
        const chip = el("button", { class: sel ? "pal-chip on" : "pal-chip", title: p.note ?? "" }, [this.phraseShort(p) + (p.swing ? ` ~${p.swing}%` : "")]);
        chip.addEventListener("click", () => { b.setCell(pick.track, pick.col, p); this.rerender(); });
        chips.appendChild(chip);
      }
      const close = el("button", { class: "pal-chip pal-tool", "aria-label": "Close" }, ["✕"]);
      close.addEventListener("click", () => { this.blockPick = null; this.rerender(); });
      wrap.appendChild(el("div", { class: "drum-palette", style: "margin-top:10px" }, [
        el("span", { class: "pal-name" }, [`${track.instrument.displayName} · ${pick.col === -1 ? "opening ▶¹" : `phrase ${pick.col + 1}`}`]),
        chips, close,
      ]));
      // Per-cell swing override: THIS phrase's own clock (0 = straight).
      if (current) {
        wrap.appendChild(el("div", { class: "row", style: "margin-top:6px;gap:10px;align-items:center" }, [
          el("span", { class: "drum-setup-label", style: "flex:0 0 auto" }, [`Swing of this phrase: ${current.swing ?? 0}%`]),
          slider(0, 100, current.swing ?? 0, (v) => b.setCellSwing(pick.track, pick.col, v)),
        ]));
      }
    }

    // Rule/notes of the phrases in use, shown under the grid.
    const noted = new Set<string>();
    for (const t of blk.tracks) for (const p of [...t.cells, t.opening ?? null]) {
      if (p?.note && !noted.has(p.label)) {
        noted.add(p.label);
        wrap.appendChild(el("div", { class: "et-muted", style: "font-size:12px;margin-top:6px" }, [`※ ${p.label}: ${p.note}`]));
      }
    }
    return wrap;
  }

  /** Bottom voice palette for the selected track: pick the "brush" a cell tap
   *  paints — Cycle (default, classic behavior), one chip per voice (tap also
   *  previews the sound), or Erase. Mixer opens the volume popup; ✕ deselects. */
  private paletteBar(inst: PercussionInstrument): HTMLElement {
    const s = this.samba;
    const chip = (label: string, active: boolean, onTap: () => void, title = ""): HTMLElement => {
      const b = el("button", { class: active ? "pal-chip on" : "pal-chip", title }, [label]);
      b.addEventListener("click", onTap);
      return b;
    };
    const chips = el("div", { class: "pal-chips" });
    chips.appendChild(chip("↻ Cycle", s.brush === "cycle", () => s.setBrush("cycle"), "tap steps through the voices (classic)"));
    voicesFor(inst).forEach((v, idx) => {
      chips.appendChild(chip(`${v.glyph} ${v.displayName}`, s.brush === idx, () => {
        s.setBrush(idx);
        s.preview(inst, idx);   // hear what you're about to paint
      }, "tap a cell to place this voice · tap a same-voice cell to clear"));
    });
    chips.appendChild(chip("⌫ Erase", s.brush === "erase", () => s.setBrush("erase"), "tap cells to clear them"));

    const mixer = el("button", { class: "pal-chip pal-tool" }, ["Mixer"]);
    mixer.addEventListener("click", () => { this.openVoiceMenu = this.openVoiceMenu === inst.id ? null : inst.id; this.rerender(); });
    const dup = el("button", { class: "pal-chip pal-tool", title: "Duplicate this track (same sound + pattern)" }, ["⧉ Dup"]);
    dup.addEventListener("click", () => s.duplicateTrack(inst));
    // Save this track's row as a named PHRASE (custom preset): joins the library
    // everywhere (+ Add, opening picker, beats list, Blocks palette). Using a
    // built-in's name REPLACES it (edit-and-resave of presaved tracks).
    const savePhrase = el("button", { class: "pal-chip pal-tool", title: "Save this track as a phrase / preset" }, ["💾 Phrase"]);
    savePhrase.addEventListener("click", () => {
      const row = s.editPattern.grid.get(inst.id);
      if (!row) return;
      const suggested = `${inst.displayName.split(" ")[0]} — `;
      const name = window.prompt("Phrase name (same name as a preset replaces it):", suggested);
      if (name === null) return;
      if (!this.blocks.saveTrackAsPreset(inst, [...row], name)) {
        window.alert("Name can't be empty or contain = : , | @ ~ ^");
      }
    });
    const close = el("button", { class: "pal-chip pal-tool", "aria-label": "Deselect track" }, ["✕"]);
    close.addEventListener("click", () => s.selectTrack(inst.id));

    return el("div", { class: "drum-palette" }, [
      el("span", { class: "pal-name" }, [inst.displayName]),
      chips, mixer, dup, savePhrase, close,
    ]);
  }

  /** Voice popup: overall instrument volume, per-voice volume (tap the label
   *  to audition), and a Remove action — opened from the palette's Mixer chip. */
  private voicePopup(inst: PercussionInstrument): HTMLElement {
    const s = this.samba;
    const vol = s.volumeOf(inst);
    const tSwing = s.editPattern.trackSwingOf(inst.id);
    const pop = el("div", { class: "drum-voice-pop" }, [
      el("div", { style: "font-weight:600;font-size:13px" }, [`Overall volume: ${Math.round(vol * 100)}%`]),
      slider(0, 1, vol, (v) => s.setVolume(inst, v), 0.01),
      el("div", { class: "divider-line" }),
      // Per-TRACK swing: this track's own clock. Only heard while the beat's
      // global swing is 0 — a nonzero global swing overrides every track.
      el("div", { style: "font-weight:600;font-size:13px" }, [
        `Track swing: ${tSwing}%` + (s.swing > 0 ? " (overridden by global swing)" : ""),
      ]),
      slider(0, 100, tSwing, (v) => s.setTrackSwing(inst, v), 1),
      el("div", { class: "divider-line" }),
      el("div", { class: "ans-label" }, ["Per-voice volume (tap name to audition)"]),
    ]);
    voicesFor(inst).forEach((v, idx) => {
      const src = s.usesSample(inst, idx) ? "sample" : "synth";
      const vvol = s.voiceVolumeOf(inst, idx);
      const label = el("span", { style: "flex:1" }, [`${v.glyph}   ${v.displayName}   ·   ${Math.round(vvol * 100)}%`]);
      const row = el("div", { class: "vrow", style: "display:flex;align-items:center;gap:8px" }, [
        label,
        el("span", { style: `font-size:10px;color:${s.usesSample(inst, idx) ? Colors.primary : Colors.textSecondary}` }, [src]),
      ]);
      label.addEventListener("click", (e) => { e.stopPropagation(); s.preview(inst, idx); });
      pop.appendChild(row);
      pop.appendChild(slider(0, 1, vvol, (val) => s.setVoiceVolume(inst, idx, val), 0.01));
    });
    // Duplicate / Remove this track.
    pop.appendChild(el("div", { class: "divider-line" }));
    const dup = el("div", { class: "vrow" }, [`⧉ Duplicate ${inst.displayName}`]);
    dup.addEventListener("click", (e) => { e.stopPropagation(); this.openVoiceMenu = null; s.duplicateTrack(inst); this.rerender(); });
    pop.appendChild(dup);
    const remove = el("div", { class: "vrow", style: `color:${Colors.textSecondary}` }, [`Remove ${inst.displayName}`]);
    remove.addEventListener("click", (e) => { e.stopPropagation(); this.openVoiceMenu = null; s.removeInstrument(inst); this.rerender(); });
    pop.appendChild(remove);
    return pop;
  }

  /** "+ Add ▾" button + dropdown of catalog instruments not yet in the kit. */
  private addInstrumentControl(): HTMLElement {
    const s = this.samba;
    const wrap = el("div", { style: "position:relative" });
    wrap.appendChild(btn(this.addMenuOpen ? "+ Add ✕" : "+ Add ▾", () => {
      this.addMenuOpen = !this.addMenuOpen; this.saveOpen = false; this.rerender();
    }, "btn primary"));
    if (this.addMenuOpen) {
      const pop = el("div", { class: "drum-load-pop" });
      // One-press preset tracks first (instrument + a filled row in one go).
      pop.appendChild(el("div", { class: "lrow", style: "color:var(--text-secondary);cursor:default;font-size:11px" }, ["Track presets"]));
      for (const p of this.blocks.allPresets()) {
        const row = el("div", { class: "lrow" }, [`★ ${p.label}`]);
        row.addEventListener("click", () => { s.addPresetTrack(p); this.addMenuOpen = false; this.rerender(); });
        pop.appendChild(row);
      }
      pop.appendChild(el("div", { class: "divider-line", style: "margin:4px 0" }));
      const toAdd = s.instrumentsToAdd();
      if (toAdd.length === 0) {
        pop.appendChild(el("div", { class: "lrow", style: "color:var(--text-secondary)" }, ["(all instruments added)"]));
      }
      for (const inst of toAdd) {
        const row = el("div", { class: "lrow" }, [inst.displayName]);
        row.addEventListener("click", () => { s.addInstrument(inst); this.addMenuOpen = false; this.rerender(); });
        pop.appendChild(row);
      }
      wrap.appendChild(pop);
    }
    return wrap;
  }

  private saveControl(): HTMLElement {
    const s = this.samba;
    const wrap = el("div", { style: "position:relative" });
    wrap.appendChild(btn(this.saveOpen ? "Save ✕" : "Save…", () => {
      this.saveOpen = !this.saveOpen;
      if (this.saveOpen && !this.saveName) this.saveName = this.samba.loadedName ?? "";
      this.addMenuOpen = false; this.rerender();
    }));
    if (this.saveOpen) {
      const input = el("input", { type: "text", placeholder: "Beat name", style: "width:150px" }) as HTMLInputElement;
      input.value = this.saveName;
      input.addEventListener("input", () => { this.saveName = input.value; });
      const saveBtn = btn("Save", () => {
        const n = this.saveName.trim();
        if (n && ![..."=;|,"].some((ch) => n.includes(ch))) { s.saveCurrent(n); this.saveOpen = false; this.saveName = ""; this.rerender(); }
      }, "btn primary");
      wrap.appendChild(el("div", { class: "drum-load-pop", style: "display:flex;gap:6px;padding:10px" }, [input, saveBtn]));
    }
    return wrap;
  }

  /** Constantly-open, scrollable beats side panel: Grooves / Study / Saved.
   *  Replaces the old Load… popup; the loaded beat's row is highlighted. */
  private beatSidebar(): HTMLElement {
    const s = this.samba;
    const side = el("div", { class: "drum-side" });
    side.addEventListener("scroll", () => { this.sideScrollTop = side.scrollTop; });
    const header = (t: string) => side.appendChild(el("div", { class: "drum-side-head" }, [t]));
    const beatRow = (b: BuiltinPattern): void => {
      const cls = s.loadedName === b.name ? "lrow sel" : "lrow";
      const row = el("div", { class: cls }, [b.name + (b.opening ? " ▶¹" : "")]);
      row.addEventListener("click", () => { s.loadPattern(b.pattern, b.name, b.bpm ?? null, null, b.opening ?? null); this.rerender(); });
      side.appendChild(row);
    };
    header("Grooves");
    for (const b of [...BUILTIN_PATTERNS, ...STUDY_PATTERNS]) beatRow(b);
    // Track presets: tap to ADD the chunk as a track to the current beat.
    // User-defined phrases (👤) can be deleted; save one via the track palette's 💾.
    header("Track presets");
    const customs = this.blocks.customLabels();
    for (const p of this.blocks.allPresets()) {
      const isCustom = customs.has(p.label);
      const label = `★ ${p.label}` + (p.swing ? ` ~${p.swing}%` : "") + (isCustom ? " 👤" : "");
      const row = el("div", { class: "lrow", title: p.note ?? "tap to add this track to the current beat" },
        [el("span", { style: "flex:1" }, [label])]);
      // ◎ loops the phrase ALONE: replaces the whole beat with just this track
      // (Undo brings the previous beat back).
      const solo = el("button", { class: "btn text", title: "Loop this phrase alone (replaces the current beat; Undo restores it)" }, ["◎"]);
      solo.addEventListener("click", (e) => { e.stopPropagation(); s.loadPresetAsBeat(p); this.rerender(); });
      row.appendChild(solo);
      if (isCustom) {
        // ⤓ exports the phrase as a .chorect-phrase.json (Import reads it back).
        const exp = el("button", { class: "btn text", title: "Export this phrase" }, ["⤓"]);
        exp.addEventListener("click", (e) => { e.stopPropagation(); this.exportPhrase(p); });
        row.appendChild(exp);
        const del = el("button", { class: "btn text" }, ["✕"]);
        del.addEventListener("click", (e) => { e.stopPropagation(); this.blocks.deleteTrackPreset(p.label); this.rerender(); });
        row.appendChild(del);
      }
      row.addEventListener("click", () => { s.addPresetTrack(p); this.rerender(); });
      side.appendChild(row);
    }
    header("Saved");
    const saved = s.savedPatterns();
    for (const [name, beat] of saved) {
      const del = el("button", { class: "btn text" }, ["✕"]);
      del.addEventListener("click", (e) => { e.stopPropagation(); s.deleteSaved(name); this.rerender(); });
      const cls = s.loadedName === name ? "lrow sel" : "lrow";
      const row = el("div", { class: cls }, [
        el("span", { style: "flex:1" }, [name + (beat.opening ? " ▶¹" : "") + (beat.notes ? " 📝" : "")]),
        del,
      ]);
      row.addEventListener("click", () => { s.loadPattern(beat.main, name, null, null, beat.opening, beat.notes); this.rerender(); });
      side.appendChild(row);
    }
    if (saved.size === 0) side.appendChild(el("div", { class: "lrow", style: "color:var(--text-secondary);cursor:default" }, ["(no saved beats yet)"]));
    return side;
  }

  /** Download the current beat as a Chorect beat file (JSON: name + bpm + swing + pattern). */
  private exportBeat(): void {
    const s = this.samba;
    const name = (s.loadedName ?? "beat").trim() || "beat";
    const json = encodeBeatFile(name, s.bpm, s.swing, s.pattern, s.opening, s.beatNotes);
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = el("a", { href: url, download: `${name.replace(/[^\w-]+/g, "_")}.chorect.json` }) as HTMLAnchorElement;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  /** Mini 16-step strip of a phrase's template (the Blocks "Grid" toggle):
   *  a filled tick per onset — accents taller/teal, dyn levels dimmer, a small
   *  gap at each quarter. */
  private miniPhraseGrid(p: PresetTrack): HTMLElement {
    const g = el("div", { class: "mini-grid" });
    p.template.forEach((raw, i) => {
      const on = raw !== null && raw !== undefined;
      const acc = on && Math.floor((raw as number) / 100) % 10 === 1;
      const dyn = on ? Math.floor((raw as number) / 1000) : 0;
      const c = el("div", { class: "mini-cell" + (on ? " on" : "") + (acc ? " acc" : "") + (i % 4 === 0 && i > 0 ? " beat" : "") });
      if (on && dyn > 0) c.style.opacity = String(1 - 0.25 * dyn);
      g.appendChild(c);
    });
    return g;
  }

  /** Download the current block as a Chorect block file (embeds the custom
   *  phrases it references, so it's portable to another device). */
  private exportBlock(): void {
    const name = this.blocks.block.name.trim() || "block";
    const json = this.blocks.exportBlockFile();
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = el("a", { href: url, download: `${name.replace(/[^\w-]+/g, "_")}.chorect-block.json` }) as HTMLAnchorElement;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  /** Download a phrase as a Chorect phrase file (Import reads it back). */
  private exportPhrase(p: PresetTrack): void {
    const json = encodePhraseFile(p);
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = el("a", { href: url, download: `${p.label.replace(/[^\w-]+/g, "_")}.chorect-phrase.json` }) as HTMLAnchorElement;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  /** Pick a Chorect file and load it: a BEAT file loads into the editor; a
   *  BLOCK file loads into the Blocks view (its embedded phrases join the
   *  library); a PHRASE file joins the track-preset library (dispatch on
   *  "format"). */
  private importBeat(): void {
    const input = el("input", { type: "file", accept: ".json,application/json", style: "display:none" }) as HTMLInputElement;
    input.addEventListener("change", () => {
      const file = input.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        const text = String(reader.result ?? "");
        const beat = decodeBeatFile(text);
        if (beat) {
          this.samba.loadPattern(beat.pattern, beat.name, beat.bpm, beat.swing, beat.opening, beat.notes);
          this.rerender();
          return;
        }
        if (this.blocks.importBlockFile(text)) {
          this.viewMode = "blocks";
          this.rerender();
          return;
        }
        const phrase = decodePhraseFile(text);
        if (phrase && this.blocks.savePhrase(phrase)) {
          window.alert(`Phrase "${phrase.label}" imported into Track presets.`);
          this.rerender();
          return;
        }
        window.alert("Not a valid Chorect beat, block, or phrase file.");
      };
      reader.readAsText(file);
    });
    input.click();
  }
}

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
import { EarTrainingState } from "./earTrainingState";
import { Colors } from "./theme";
import { el, btn, slider } from "./dom";
import { icon } from "./icons";
import { transportDock, toneSheet } from "./transport";
import { AppState } from "./appState";
import {
  PercussionInstrument, voicesFor, voiceOf, PercussionPattern, BUILTIN_PATTERNS,
  DIVISIONS,
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
  private loadMenuOpen = false;
  private addMenuOpen = false;
  private saveOpen = false;
  private saveName = "";
  /** Shared lane scroll position, preserved across the full re-renders. */
  private laneScrollLeft = 0;
  /** The single active outside-tap popup closer (never stacked). */
  private outsideCloser: ((e: Event) => void) | null = null;
  private toneSheetOpen = false;
  /** Track index currently being drag-reordered, or null. */
  private dragIndex: number | null = null;

  constructor(
    private samba: SambaLooperState,
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
      btn("Back", () => { s.stop(); this.onBack(); }),
    ]));

    screen.appendChild(el("div", { class: "v-gap-8" }));

    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);
    body.appendChild(this.patternSection());

    // Transport dock (Signal move #2): BPM lives here now — samba reads `bpm`
    // live every slot (see SambaLooperState.start()), so no restart is needed
    // when it changes, unlike the Ear progression loop.
    screen.appendChild(transportDock({
      playing: s.isPlaying,
      onPlayStop: () => { if (s.isPlaying) s.stop(); else s.start(); },
      bpm: s.bpm,
      onBpm: (v) => s.setBpm(v),
      toneLabel: this.state.sound,
      onTone: () => { this.toneSheetOpen = true; this.rerender(); },
    }));

    container.appendChild(screen);
    if (this.toneSheetOpen) container.appendChild(toneSheet(this.state, this.ear, () => { this.toneSheetOpen = false; this.rerender(); }));

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
    if (this.openVoiceMenu !== null || this.loadMenuOpen || this.addMenuOpen || this.saveOpen) {
      const onDoc = (e: Event) => {
        if (!(e.target as HTMLElement).closest(".drum-voice-pop, .drum-load-pop")) {
          document.removeEventListener("pointerdown", onDoc, true);
          if (this.outsideCloser === onDoc) this.outsideCloser = null;
          this.openVoiceMenu = null; this.loadMenuOpen = false; this.addMenuOpen = false; this.saveOpen = false;
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
      : btn("Erase", () => { this.eraseMode = true; this.accentMode = false; this.rerender(); });
    const accent = this.accentMode
      ? btn("Accent ✓", () => { this.accentMode = false; this.rerender(); }, "btn primary")
      : btn("Accent", () => { this.accentMode = true; this.eraseMode = false; this.rerender(); });
    const undoBtn = btn("↶ Undo", () => { s.undo(); this.rerender(); });
    if (!s.canUndo) undoBtn.disabled = true;
    wrap.appendChild(el("div", { class: "et-row-gap" }, [
      erase, accent, this.saveControl(), this.loadControl(), btn("Clear all", () => s.clearAll()),
      undoBtn, this.addInstrumentControl(),
    ]));

    // Dismissible gesture-legend banner (module-level flag — see the
    // `legendDismissed` doc above).
    if (!legendDismissed) {
      const closeBtn = el("button", { class: "tune-btn", "aria-label": "Dismiss" }, [icon("close", 16)]);
      closeBtn.addEventListener("click", () => { legendDismissed = true; this.rerender(); });
      wrap.appendChild(el("div", { class: "et-card drum-legend", style: `background:var(--surface2)` }, [
        el("div", { class: "row" }, [
          el("div", { class: "et-muted", style: "flex:1" }, ["Tap = toggle · hold = accent · long-press = erase"]),
          closeBtn,
        ]),
      ]));
    }

    // loop setup: bars / time signature / division + translate
    wrap.appendChild(this.loopSetupControls());
    wrap.appendChild(el("div", { class: "divider-line" }));

    // grid — dynamic kit
    s.pattern.instruments.forEach((inst, i) => wrap.appendChild(this.instrumentRow(inst, i)));
    wrap.appendChild(el("div", { class: "drum-caption" }, [s.meter.describe()]));

    // Compact card: tap-tempo + swing.
    const swingActive = s.meter.beatUnit === 4 && s.meter.division === 16;
    const swingSlider = slider(0, 100, s.swing, (v) => s.setSwing(v));
    swingSlider.disabled = !swingActive;
    wrap.appendChild(el("div", { class: "et-card", style: `background:var(--surface2);margin-top:8px` }, [
      el("div", { class: "row" }, [
        btn("Tap tempo", () => s.tapTempo()),
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

  /** Instrument row: name label (tap → voice popup with overall + per-voice
   *  volume, tap-to-preview, and Remove) + Mute/Solo toggles + its step cells. */
  private instrumentRow(inst: PercussionInstrument, index: number): HTMLElement {
    const s = this.samba;
    const audible = s.isAudible(inst);

    const name = el("span", { class: "name", title: "drag to reorder · right-click to remove" }, [inst.displayName + " ▾"]);
    name.addEventListener("click", () => { this.openVoiceMenu = this.openVoiceMenu === inst.id ? null : inst.id; this.rerender(); });
    // Right-click the track name to remove it.
    name.addEventListener("contextmenu", (e) => { e.preventDefault(); this.openVoiceMenu = null; s.removeInstrument(inst); this.rerender(); });
    const labelInner = el("div", {}, [name]);
    if (this.openVoiceMenu === inst.id) labelInner.appendChild(this.voicePopup(inst));

    const mTag = el("button", { class: s.muted.has(inst.id) ? "ms-tag on-m" : "ms-tag" }, ["M"]);
    mTag.addEventListener("click", () => s.toggleMute(inst));
    const sTag = el("button", { class: s.soloed.has(inst.id) ? "ms-tag on-s" : "ms-tag" }, ["S"]);
    sTag.addEventListener("click", () => s.toggleSolo(inst));
    const label = el("div", { class: audible ? "drum-label" : "drum-label dim", style: "position:relative;cursor:grab", draggable: "true" }, [
      labelInner,
      el("div", { class: "drum-ms" }, [mTag, sTag]),
    ]);
    // Drag the label to reorder the track (raise/lower). The row is the drop target.
    label.addEventListener("dragstart", (e) => { this.dragIndex = index; e.dataTransfer?.setData("text/plain", String(index)); });
    label.addEventListener("dragend", () => { this.dragIndex = null; });

    // cells
    const slots = s.pattern.slots;
    const { slotsPerBeat, slotsPerBar } = s.meter;
    const cells = el("div", { class: audible ? "drum-cells" : "drum-cells dim" });
    const perBeat = Math.max(slotsPerBeat, 1);
    for (let slot = 0; slot < slots; slot++) {
      cells.appendChild(this.cell(inst, slot, Math.floor(slot / perBeat), slot % perBeat === 0));
      // Beat separators: a visible vertical rule after each beat (each group of four
      // 16ths), heavier at bar lines, so the quarter-note divisions read clearly.
      if ((slot + 1) % slotsPerBeat === 0 && slot !== slots - 1) {
        const isBar = (slot + 1) % slotsPerBar === 0;
        cells.appendChild(el("div", { class: isBar ? "drum-div bar" : "drum-div" }));
      }
    }

    const row = el("div", { class: "drum-row" }, [label, cells]);
    // Drop target: reorder the dragged track to this row's position.
    row.addEventListener("dragover", (e) => { if (this.dragIndex !== null) e.preventDefault(); });
    row.addEventListener("drop", (e) => {
      e.preventDefault();
      if (this.dragIndex !== null && this.dragIndex !== index) s.reorderInstrument(this.dragIndex, index);
      this.dragIndex = null;
    });
    return row;
  }

  /** UNTOUCHED gesture logic (tap = cycle/erase/accent; long-press/right-click
   *  = clear) — per-voice multicolor fill restored (voice 0/1/else ->
   *  primary/scaleTone/chordTone), the pre-Signal mapping. */
  private cell(inst: PercussionInstrument, slot: number, beatIndex: number, isBeatStart: boolean): HTMLElement {
    const s = this.samba;
    const voice = s.pattern.voiceAt(inst, slot);
    const accented = s.pattern.isAccented(inst, slot);
    const isPlayhead = s.currentSlot === slot;
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
    const cls = "drum-cell" + (isPlayhead ? " playhead" : "") + (accented ? " accent" : "");
    const c = el("div", { class: cls, style: `background:${fill}` },
      [voice !== null ? voiceOf(inst, voice).glyph : ""]);

    // tap = cycle/erase/accent; long-press = clear
    let longPressed = false;
    let timer: number | undefined;
    c.addEventListener("pointerdown", () => {
      longPressed = false;
      timer = window.setTimeout(() => { longPressed = true; s.clearCell(inst, slot); }, LONG_PRESS_MS);
    });
    const cancel = () => { if (timer) { clearTimeout(timer); timer = undefined; } };
    c.addEventListener("pointerup", () => {
      cancel();
      if (longPressed) return;
      if (this.eraseMode) s.clearCell(inst, slot);
      else if (this.accentMode) s.toggleAccent(inst, slot);
      else s.toggleSlot(inst, slot);
    });
    c.addEventListener("pointerleave", cancel);
    c.addEventListener("pointercancel", cancel);
    // Right-click clears the slot (desktop convenience; mirrors the long-press).
    c.addEventListener("contextmenu", (e) => {
      e.preventDefault();
      cancel();
      s.clearCell(inst, slot);
    });
    return c;
  }

  /** Voice popup: overall instrument volume, per-voice volume (tap the label
   *  to audition), and a Remove action — opened by tapping the row's name. */
  private voicePopup(inst: PercussionInstrument): HTMLElement {
    const s = this.samba;
    const vol = s.volumeOf(inst);
    const pop = el("div", { class: "drum-voice-pop" }, [
      el("div", { style: "font-weight:600;font-size:13px" }, [`Overall volume: ${Math.round(vol * 100)}%`]),
      slider(0, 1, vol, (v) => s.setVolume(inst, v), 0.01),
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
    // Remove this instrument from the kit.
    pop.appendChild(el("div", { class: "divider-line" }));
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
      this.addMenuOpen = !this.addMenuOpen; this.loadMenuOpen = false; this.saveOpen = false; this.rerender();
    }, "btn primary"));
    if (this.addMenuOpen) {
      const pop = el("div", { class: "drum-load-pop" });
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
    wrap.appendChild(btn(this.saveOpen ? "Save ✕" : "Save…", () => { this.saveOpen = !this.saveOpen; this.loadMenuOpen = false; this.addMenuOpen = false; this.rerender(); }));
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

  private loadControl(): HTMLElement {
    const s = this.samba;
    const wrap = el("div", { style: "position:relative" });
    wrap.appendChild(btn(this.loadMenuOpen ? "Load ✕" : "Load…", () => { this.loadMenuOpen = !this.loadMenuOpen; this.saveOpen = false; this.addMenuOpen = false; this.rerender(); }));
    if (this.loadMenuOpen) {
      const pop = el("div", { class: "drum-load-pop" });
      for (const b of BUILTIN_PATTERNS) {
        const row = el("div", { class: "lrow" }, [b.name]);
        row.addEventListener("click", () => { s.loadPattern(b.pattern); this.loadMenuOpen = false; this.rerender(); });
        pop.appendChild(row);
      }
      const saved = s.savedPatterns();
      if (saved.size) pop.appendChild(el("div", { class: "divider-line", style: "margin:4px 0" }));
      for (const [name, pat] of saved) {
        const del = el("button", { class: "btn text" }, ["✕"]);
        del.addEventListener("click", (e) => { e.stopPropagation(); s.deleteSaved(name); this.rerender(); });
        const row = el("div", { class: "lrow" }, [el("span", { style: "flex:1" }, [name]), del]);
        row.addEventListener("click", () => { s.loadPattern(pat as PercussionPattern); this.loadMenuOpen = false; this.rerender(); });
        pop.appendChild(row);
      }
      if (saved.size === 0) pop.appendChild(el("div", { class: "lrow", style: "color:var(--text-secondary)" }, ["(no saved beats yet)"]));
      wrap.appendChild(pop);
    }
    return wrap;
  }
}

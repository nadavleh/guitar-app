// Drum-machine screen, ported from app/.../SambaLooperScreen.kt (Signal redesign,
// see docs/superpowers/specs/2026-07-10-signal-gui-redesign-design.md
// §Screens → Rhythm). A header `segmented()` control swaps the body between
// three sections:
//  - **Pattern** — the step grid itself (tap a cell to cycle its voice, or
//    clear in Erase mode; long-press/right-click clears), a dismissible
//    gesture-legend banner, and a compact swing/metronome card. Save…/Load…/
//    Clear all/Erase/Accent stay reachable in this section's header.
//  - **Mixer** — per-instrument + per-voice volume (previously tucked behind a
//    per-row popup opened by tapping the instrument name).
//  - **Kit** — add/remove instruments (previously the footer's dropdown).
// None of the grid's gesture handlers or SambaLooperState calls changed — this
// is a chrome-only regrouping of the same controls (mirrors Android T8).

import { SambaLooperState } from "./sambaLooperState";
import { EarTrainingState } from "./earTrainingState";
import { Colors } from "./theme";
import { el, btn, slider, segmented, labelSm } from "./dom";
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

type RhythmSection = "pattern" | "mixer" | "kit";
const RHYTHM_SECTIONS: { value: RhythmSection; label: string }[] = [
  { value: "pattern", label: "Pattern" },
  { value: "mixer", label: "Mixer" },
  { value: "kit", label: "Kit" },
];

/** Whether the Pattern section's gesture-legend banner has been dismissed.
 *  Module-level (not an instance field) — mirrors Android's
 *  `remember { mutableStateOf(false) }` inside `PatternSection`, which resets
 *  every time that composable enters composition (i.e. every screen visit);
 *  `SambaLooperUI` itself is a single long-lived instance (see ui.ts), so a
 *  plain module-level flag is the equivalent "resets on reload, not on every
 *  rerender" behavior (same pattern as transport.ts's bpmExpanded/eqExpanded). */
let legendDismissed = false;

export class SambaLooperUI {
  private section: RhythmSection = "pattern";
  private eraseMode = false;
  private accentMode = false;
  private loadMenuOpen = false;
  private saveOpen = false;
  private saveName = "";
  /** Shared lane scroll position, preserved across the full re-renders. */
  private laneScrollLeft = 0;
  /** The single active outside-tap popup closer (never stacked). */
  private outsideCloser: ((e: Event) => void) | null = null;
  private toneSheetOpen = false;

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
    screen.appendChild(segmented<RhythmSection>(
      RHYTHM_SECTIONS, this.section, (v) => { this.section = v; this.rerender(); },
    ));

    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);

    if (this.section === "pattern") body.appendChild(this.patternSection());
    else if (this.section === "mixer") body.appendChild(this.mixerSection());
    else body.appendChild(this.kitSection());

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
    // so they stay a consistent size regardless of viewport width). Only present
    // in the Pattern section; a no-op elsewhere.
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
    if (this.loadMenuOpen || this.saveOpen) {
      const onDoc = (e: Event) => {
        if (!(e.target as HTMLElement).closest(".drum-load-pop")) {
          document.removeEventListener("pointerdown", onDoc, true);
          if (this.outsideCloser === onDoc) this.outsideCloser = null;
          this.loadMenuOpen = false; this.saveOpen = false;
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

    // Section header: Erase / Accent / Save… / Load… / Clear all (same calls
    // the old always-visible footer used — just relocated here per spec).
    const erase = this.eraseMode
      ? btn("Erase ✓", () => { this.eraseMode = false; this.rerender(); }, "btn primary")
      : btn("Erase", () => { this.eraseMode = true; this.accentMode = false; this.rerender(); });
    const accent = this.accentMode
      ? btn("Accent ✓", () => { this.accentMode = false; this.rerender(); }, "btn primary")
      : btn("Accent", () => { this.accentMode = true; this.eraseMode = false; this.rerender(); });
    wrap.appendChild(el("div", { class: "et-row-gap" }, [
      erase, accent, this.saveControl(), this.loadControl(), btn("Clear all", () => s.clearAll()),
    ]));

    // Dismissible gesture-legend banner (per spec: "one dismissible gesture-
    // legend banner"), module-level flag — see the `legendDismissed` doc above.
    if (!legendDismissed) {
      const closeBtn = el("button", { class: "tune-btn", "aria-label": "Dismiss" }, [icon("close", 16)]);
      closeBtn.addEventListener("click", () => { legendDismissed = true; this.rerender(); });
      wrap.appendChild(el("div", { class: "et-card drum-legend", style: `background:${Colors.surfaceElev}` }, [
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
    for (const inst of s.pattern.instruments) wrap.appendChild(this.instrumentRow(inst));
    wrap.appendChild(el("div", { class: "drum-caption" }, [s.meter.describe()]));

    // Compact card: tap-tempo + metronome + swing (Pattern-only tools, moved
    // out of the always-visible header/footer per spec).
    const swingActive = s.meter.beatUnit === 4 && s.meter.division === 16;
    const swingSlider = slider(0, 100, s.swing, (v) => s.setSwing(v));
    swingSlider.disabled = !swingActive;
    wrap.appendChild(el("div", { class: "et-card", style: `background:${Colors.surfaceElev};margin-top:8px` }, [
      el("div", { class: "row" }, [
        btn("Tap tempo", () => s.tapTempo()),
        btn(s.metronome ? "Metro ✓" : "Metro", () => s.toggleMetronome(), s.metronome ? "btn primary" : "btn"),
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

  /** Pattern-tab row: instrument name label + Mute/Solo toggles + its step cells.
   *  (Per-voice volume and Remove now live in the Mixer / Kit sections respectively.) */
  private instrumentRow(inst: PercussionInstrument): HTMLElement {
    const s = this.samba;
    const audible = s.isAudible(inst);

    const mTag = el("button", { class: s.muted.has(inst.id) ? "ms-tag on-m" : "ms-tag" }, ["M"]);
    mTag.addEventListener("click", () => s.toggleMute(inst));
    const sTag = el("button", { class: s.soloed.has(inst.id) ? "ms-tag on-s" : "ms-tag" }, ["S"]);
    sTag.addEventListener("click", () => s.toggleSolo(inst));
    const label = el("div", { class: audible ? "drum-label" : "drum-label dim" }, [
      el("div", { class: "name" }, [inst.displayName]),
      el("div", { class: "drum-ms" }, [mTag, sTag]),
    ]);

    // cells
    const slots = s.pattern.slots;
    const { slotsPerBeat, slotsPerBar } = s.meter;
    const cells = el("div", { class: audible ? "drum-cells" : "drum-cells dim" });
    const perBeat = Math.max(slotsPerBeat, 1);
    for (let slot = 0; slot < slots; slot++) {
      cells.appendChild(this.cell(inst, slot, Math.floor(slot / perBeat), slot % perBeat === 0));
      // Beat separators: a gap after each beat; a wider gap at each bar line.
      if ((slot + 1) % slotsPerBeat === 0 && slot !== slots - 1) {
        const w = (slot + 1) % slotsPerBar === 0 ? 6 : 3;
        cells.appendChild(el("div", { class: "drum-gap", style: `flex:0 0 ${w}px` }));
      }
    }

    return el("div", { class: "drum-row" }, [label, cells]);
  }

  /** UNTOUCHED gesture logic (tap = cycle/erase/accent; long-press/right-click
   *  = clear) — only the fill/border coloring changed (Signal recolor: hit
   *  cells are always the act color regardless of voice, since the printed
   *  glyph already distinguishes voices; the accent ring is the feedback color). */
  private cell(inst: PercussionInstrument, slot: number, beatIndex: number, isBeatStart: boolean): HTMLElement {
    const s = this.samba;
    const voice = s.pattern.voiceAt(inst, slot);
    const accented = s.pattern.isAccented(inst, slot);
    const isPlayhead = s.currentSlot === slot;
    // Empty cells brightened (were near-invisible on black) and tinted per beat-group
    // so the quarter-note grouping reads at a glance (#7): first 16th of each beat is
    // brightest; alternating beats step between two shades.
    const emptyFill = isBeatStart ? "rgba(120,128,144,0.55)"
      : beatIndex % 2 === 0 ? "rgba(120,128,144,0.42)"
      : "rgba(120,128,144,0.30)";
    const fill = voice === null ? emptyFill : Colors.primary;
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

  private saveControl(): HTMLElement {
    const s = this.samba;
    const wrap = el("div", { style: "position:relative" });
    wrap.appendChild(btn(this.saveOpen ? "Save ✕" : "Save…", () => { this.saveOpen = !this.saveOpen; this.loadMenuOpen = false; this.rerender(); }));
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
    wrap.appendChild(btn(this.loadMenuOpen ? "Load ✕" : "Load…", () => { this.loadMenuOpen = !this.loadMenuOpen; this.saveOpen = false; this.rerender(); }));
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

  // ---------- MIXER section ----------

  private mixerSection(): HTMLElement {
    const s = this.samba;
    const wrap = el("div", {});
    wrap.appendChild(labelSm("Volume — per instrument & voice"));
    if (s.pattern.instruments.length === 0) {
      wrap.appendChild(el("div", { class: "et-muted" }, ["No instruments in the kit yet — add some from the Kit section."]));
      return wrap;
    }
    for (const inst of s.pattern.instruments) wrap.appendChild(this.mixerCard(inst));
    return wrap;
  }

  private mixerCard(inst: PercussionInstrument): HTMLElement {
    const s = this.samba;
    const vol = s.volumeOf(inst);
    const card = el("div", { class: "et-card", style: `background:${Colors.surfaceElev}` }, [
      el("div", { style: "font-weight:600" }, [inst.displayName]),
      el("div", { class: "label-sm" }, [`Overall volume: ${Math.round(vol * 100)}%`]),
      slider(0, 1, vol, (v) => s.setVolume(inst, v), 0.01),
      el("div", { class: "divider-line" }),
      el("div", { class: "ans-label" }, ["Per-voice volume (tap name to audition)"]),
    ]);
    voicesFor(inst).forEach((v, idx) => {
      const src = s.usesSample(inst, idx) ? "sample" : "synth";
      const vvol = s.voiceVolumeOf(inst, idx);
      const label = el("span", { style: "flex:1;cursor:pointer" }, [`${v.glyph}   ${v.displayName}   ·   ${Math.round(vvol * 100)}%`]);
      label.addEventListener("click", () => s.preview(inst, idx));
      const row = el("div", { class: "row", style: "margin-top:6px" }, [
        label,
        el("span", { style: `font-size:10px;color:${s.usesSample(inst, idx) ? Colors.primary : Colors.textSecondary}` }, [src]),
      ]);
      card.appendChild(row);
      card.appendChild(slider(0, 1, vvol, (val) => s.setVoiceVolume(inst, idx, val), 0.01));
    });
    return card;
  }

  // ---------- KIT section ----------

  private kitSection(): HTMLElement {
    const s = this.samba;
    const wrap = el("div", {});
    wrap.appendChild(labelSm("Current kit"));
    if (s.pattern.instruments.length === 0) {
      wrap.appendChild(el("div", { class: "et-muted" }, ["(kit is empty)"]));
    } else {
      for (const inst of s.pattern.instruments) {
        wrap.appendChild(el("div", { class: "row", style: "margin:4px 0" }, [
          el("span", { style: "flex:1" }, [inst.displayName]),
          btn("Remove", () => s.removeInstrument(inst)),
        ]));
      }
    }

    wrap.appendChild(el("div", { class: "divider-line" }));
    wrap.appendChild(labelSm("Add instrument"));
    const toAdd = s.instrumentsToAdd();
    if (toAdd.length === 0) {
      wrap.appendChild(el("div", { class: "et-muted" }, ["(all instruments added)"]));
    } else {
      wrap.appendChild(el("div", { class: "chip-row" }, toAdd.map((inst) => {
        const b = el("button", { class: "chip" }, [inst.displayName]);
        b.addEventListener("click", () => s.addInstrument(inst));
        return b;
      })));
    }
    return wrap;
  }
}

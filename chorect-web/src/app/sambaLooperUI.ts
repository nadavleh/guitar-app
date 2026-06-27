// Drum-machine screen, ported from app/.../SambaLooperScreen.kt. A 4-row × 16-cell
// step grid: tap a cell to cycle its voice (or clear in Erase mode), long-press to
// clear; a tinted column tracks the playhead. The Compose 2-finger aspect-zoom is
// omitted — the web grid sizes responsively to the viewport width instead.

import { SambaLooperState } from "./sambaLooperState";
import { Colors } from "./theme";
import { el, btn, slider } from "./dom";
import {
  PercussionInstrument, voicesFor, voiceOf, PercussionPattern, BUILTIN_PATTERNS,
  DIVISIONS,
} from "../theory";

/** Time signatures offered in the Time dropdown (beatsPerBar / beatUnit). */
const TIME_SIGNATURES: [number, number][] = [
  [2, 4], [3, 4], [4, 4], [5, 4], [6, 8], [3, 8], [12, 8], [2, 2],
];

const LONG_PRESS_MS = 450;

export class SambaLooperUI {
  private eraseMode = false;
  private openVoiceMenu: string | null = null;   // instrument id whose voice popup is open
  private loadMenuOpen = false;
  private addMenuOpen = false;
  private saveOpen = false;
  private saveName = "";

  constructor(private samba: SambaLooperState, private onBack: () => void) {}

  render(container: HTMLElement): void {
    const s = this.samba;
    const screen = el("div", { class: "tool-screen" });

    // header
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("div", { class: "tool-title" }, ["DRUMS"]),
      s.isPlaying ? btn("Stop ⏹", () => s.stop(), "btn primary") : btn("Play ▶", () => s.start(), "btn primary"),
      btn("Back", () => { s.stop(); this.onBack(); }),
    ]));

    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);

    // BPM + swing
    body.appendChild(el("div", {}, [`BPM: ${s.bpm}`]));
    body.appendChild(slider(60, 200, s.bpm, (v) => s.setBpm(v)));
    // Swing only acts on a 1/16 grid (a quarter-note split into four 16ths): it
    // holds the 1st & 3rd 16ths, delays the 2nd, and pulls the 4th early. On any
    // other division it does nothing, so the slider is disabled and says why.
    const swingActive = s.meter.beatUnit === 4 && s.meter.division === 16;
    body.appendChild(el("div", {}, [
      !swingActive ? "Swing: 1/16 grid only" : s.swing === 0 ? "Swing: straight" : `Swing: ${s.swing}% (16ths)`,
    ]));
    const swingSlider = slider(0, 100, s.swing, (v) => s.setSwing(v));
    swingSlider.disabled = !swingActive;
    body.appendChild(swingSlider);

    // loop setup: bars / time signature / division + translate
    body.appendChild(this.loopSetupControls());

    body.appendChild(el("div", { class: "divider-line" }));

    // grid — dynamic kit
    for (const inst of s.pattern.instruments) body.appendChild(this.instrumentRow(inst));
    body.appendChild(el("div", { class: "drum-caption" }, [s.meter.describe()]));

    body.appendChild(el("div", { class: "v-gap-12" }));

    // footer actions
    const erase = this.eraseMode
      ? btn("Erase ✓", () => { this.eraseMode = false; this.rerender(); }, "btn primary")
      : btn("Erase", () => { this.eraseMode = true; this.rerender(); });
    body.appendChild(el("div", { class: "et-row-gap" }, [
      erase,
      this.saveControl(),
      this.loadControl(),
      btn("Clear all", () => s.clearAll()),
      this.addInstrumentControl(),
    ]));

    container.appendChild(screen);
  }

  private rerender(): void { (this.samba as unknown as { deps: { onChange: () => void } }).deps.onChange(); }

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

  private instrumentRow(inst: PercussionInstrument): HTMLElement {
    const s = this.samba;
    const audible = s.isAudible(inst);

    // label + M/S + voice popup
    const name = el("span", { class: "name" }, [inst.displayName + " ▾"]);
    name.addEventListener("click", () => { this.openVoiceMenu = this.openVoiceMenu === inst.id ? null : inst.id; this.rerender(); });
    const labelInner = el("div", {}, [name]);
    if (this.openVoiceMenu === inst.id) labelInner.appendChild(this.voicePopup(inst));
    const mTag = el("button", { class: s.muted.has(inst.id) ? "ms-tag on-m" : "ms-tag" }, ["M"]);
    mTag.addEventListener("click", () => s.toggleMute(inst));
    const sTag = el("button", { class: s.soloed.has(inst.id) ? "ms-tag on-s" : "ms-tag" }, ["S"]);
    sTag.addEventListener("click", () => s.toggleSolo(inst));
    const label = el("div", { class: audible ? "drum-label" : "drum-label dim", style: "position:relative" }, [
      labelInner,
      el("div", { class: "drum-ms" }, [mTag, sTag]),
    ]);

    // cells
    const slots = s.pattern.slots;
    const { slotsPerBeat, slotsPerBar } = s.meter;
    const cells = el("div", { class: audible ? "drum-cells" : "drum-cells dim" });
    for (let slot = 0; slot < slots; slot++) {
      cells.appendChild(this.cell(inst, slot));
      // Beat separators: a gap after each beat; a wider gap at each bar line.
      if ((slot + 1) % slotsPerBeat === 0 && slot !== slots - 1) {
        const w = (slot + 1) % slotsPerBar === 0 ? 6 : 3;
        cells.appendChild(el("div", { style: `flex:0 0 ${w}px` }));
      }
    }

    return el("div", { class: "drum-row" }, [label, cells]);
  }

  private cell(inst: PercussionInstrument, slot: number): HTMLElement {
    const s = this.samba;
    const voice = s.pattern.voiceAt(inst, slot);
    const isPlayhead = s.currentSlot === slot;
    const fill = voice === null ? "rgba(120,128,144,0.25)"
      : voice === 0 ? Colors.primary
      : voice === 1 ? Colors.scaleTone
      : Colors.chordTone;
    const c = el("div", { class: isPlayhead ? "drum-cell playhead" : "drum-cell", style: `background:${fill}` },
      [voice !== null ? voiceOf(inst, voice).glyph : ""]);

    // tap = cycle/erase; long-press = clear
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
      else s.toggleSlot(inst, slot);
    });
    c.addEventListener("pointerleave", cancel);
    c.addEventListener("pointercancel", cancel);
    return c;
  }

  private voicePopup(inst: PercussionInstrument): HTMLElement {
    const s = this.samba;
    const vol = s.volumeOf(inst);
    const pop = el("div", { class: "drum-voice-pop" }, [
      el("div", { style: "font-weight:600;font-size:13px" }, [`Volume: ${Math.round(vol * 100)}%`]),
      slider(0, 1, vol, (v) => s.setVolume(inst, v), 0.01),
      el("div", { class: "divider-line" }),
      el("div", { class: "ans-label" }, [`${inst.displayName} voices`]),
    ]);
    voicesFor(inst).forEach((v, idx) => {
      const src = s.usesSample(inst, idx) ? "sample" : "synth";
      const row = el("div", { class: "vrow", style: "display:flex;align-items:center;gap:8px" }, [
        el("span", { style: "flex:1" }, [`${v.glyph}   ${v.displayName}`]),
        el("span", { style: `font-size:10px;color:${s.usesSample(inst, idx) ? Colors.primary : Colors.textSecondary}` }, [src]),
      ]);
      row.addEventListener("click", (e) => { e.stopPropagation(); s.preview(inst, idx); });
      pop.appendChild(row);
    });
    // Remove this instrument from the kit.
    pop.appendChild(el("div", { class: "divider-line" }));
    const remove = el("div", { class: "vrow", style: `color:${Colors.textSecondary}` }, [`Remove ${inst.displayName}`]);
    remove.addEventListener("click", (e) => { e.stopPropagation(); this.openVoiceMenu = null; s.removeInstrument(inst); this.rerender(); });
    pop.appendChild(remove);
    return pop;
  }

  /** "+ Add instrument" button + dropdown of catalog instruments not yet in the kit. */
  private addInstrumentControl(): HTMLElement {
    const s = this.samba;
    const wrap = el("div", { style: "position:relative" });
    wrap.appendChild(btn(this.addMenuOpen ? "+ Add ✕" : "+ Add instrument", () => {
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

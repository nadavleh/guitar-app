// Cavaquinho Progressions screen UI. Mirror of app/.../CavaqProgressionsScreen.kt.
// A view class owning its own fretboard canvas, re-rendered on each state change.

import { AppState } from "./appState";
import { CavaqProgState } from "./cavaqProgState";
import { FretboardCanvas } from "./fretboardCanvas";
import { shapeMarks } from "./marks";
import { el, btn, slider, labelSm } from "./dom";
import { CAVAQ_SEQUENCES, noteAt } from "../theory";

const DISPLAY_FRETS = 14;

export class CavaqProgUI {
  private fbCanvasEl: HTMLCanvasElement | null = null;
  private fb: FretboardCanvas | null = null;

  // Re-render is driven by CavaqProgState's onChange dep (→ ui.ts scheduleRender),
  // so this view holds no rerender callback of its own.
  constructor(
    private state: AppState,
    private cp: CavaqProgState,
  ) {}

  render(parent: HTMLElement): void {
    const cp = this.cp, s = this.state;
    const screen = el("div", { class: "tool-screen" });

    // ---- Header ----
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("h2", {}, ["Progressions"]),
    ]));

    // ---- Sequence picker ----
    const sel = el("select", { class: "btn", style: "width:100%;margin-top:6px" });
    for (const seq of CAVAQ_SEQUENCES) {
      const opt = el("option", { value: seq.id }, [`${seq.nameEn}  ·  ${seq.namePt}`]);
      if (seq.id === cp.sequenceId) opt.setAttribute("selected", "");
      sel.appendChild(opt);
    }
    sel.addEventListener("change", () => { cp.setSequence(sel.value); });
    screen.appendChild(el("div", { style: "margin-top:8px" }, [labelSm("Sequence"), sel]));

    // ---- Key + transpose ----
    screen.appendChild(el("div", { class: "row", style: "margin-top:10px;align-items:center;gap:8px" }, [
      el("span", { class: "mono", style: "min-width:88px" }, [`Key: ${cp.keyLabel()}`]),
      btn("−", () => cp.shiftKey(-1), "btn"),
      btn("+", () => cp.shiftKey(+1), "btn"),
      el("span", { style: "flex:1" }),
      btn("Reset to C", () => cp.chooseKey(0), "btn text"),
    ]));

    // ---- Tempo ----
    screen.appendChild(el("div", { style: "margin-top:8px" }, [
      labelSm(`Tempo: ${cp.bpm} BPM`),
      slider(40, 200, cp.bpm, (v) => cp.changeBpm(v)),
    ]));

    // ---- Play + position scroller ----
    screen.appendChild(el("div", { class: "row", style: "margin-top:8px;align-items:center;gap:8px" }, [
      btn(cp.isPlaying ? "Stop ■" : "Play ▶", () => cp.toggle(), "btn primary"),
      el("span", { style: "flex:1" }),
      labelSm("Position"),
      this.navBtn("◀", () => cp.nudgePosition(-1), cp.positionIndex <= 0),
      el("span", { class: "mono" }, [`${cp.positionIndex + 1}/${cp.positionCount}`]),
      this.navBtn("▶", () => cp.nudgePosition(+1), cp.positionIndex >= cp.positionCount - 1),
    ]));
    screen.appendChild(el("div", { style: "margin-top:4px" }, [
      labelSm("Position moves the whole sequence up/down the neck — each chord is the least-motion voicing from the previous."),
    ]));

    // ---- Chord chips (roman + symbol); tap to hear; current bar highlighted ----
    const chips = el("div", { class: "row", style: "flex-wrap:wrap;gap:6px;margin-top:10px" });
    cp.resolved.forEach((rc, i) => {
      const isCurrent = cp.currentBar === i;
      const chip = el("button", { class: isCurrent ? "btn primary" : "btn", style: "flex-direction:column;padding:6px 10px;line-height:1.2" }, [
        el("span", { style: "font-size:11px;opacity:.8" }, [rc.romanLabel]),
        el("span", { style: "font-weight:700" }, [rc.symbol]),
      ]);
      chip.addEventListener("click", () => cp.playBar(i));
      chips.appendChild(chip);
    });
    screen.appendChild(chips);

    // ---- Fretboard (follows playback / shows current voicing) ----
    if (!this.fbCanvasEl) {
      this.fbCanvasEl = el("canvas", { class: "fretboard" });
      this.fb = new FretboardCanvas(this.fbCanvasEl);
    }
    const wrap = el("div", { style: "height:220px;position:relative;margin:12px 0" });
    wrap.appendChild(this.fbCanvasEl);
    screen.appendChild(wrap);

    parent.appendChild(screen);

    const shape = cp.currentShape;
    const marks = shape ? shapeMarks(shape, s.labelMode) : new Map();
    this.fb!.setData({
      tuning: s.liveTuning, marks, selectedPosition: null, leftHanded: s.leftHanded,
      numFrets: DISPLAY_FRETS, playOnTouchDown: false, mutedStrings: new Set<number>(),
      onTap: (pos) => s.audio.playNote(noteAt(s.liveTuning, pos).midi, s.ringSustainMs),
    });
  }

  private navBtn(text: string, onClick: () => void, disabled: boolean): HTMLButtonElement {
    const b = btn(text, onClick, "btn");
    b.disabled = disabled;
    return b;
  }
}

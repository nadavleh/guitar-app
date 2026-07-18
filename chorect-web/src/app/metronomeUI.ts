// Standalone Metronome screen. Mirror of app/.../MetronomeScreen.kt.
// Play/Stop + BPM (slider & tap tempo) + selectable time signature, with a row
// of beat dots that light up on each click (the "1" accented).

import { MetronomeState, METRONOME_TIME_SIGNATURES } from "./metronomeState";
import { el, btn, slider } from "./dom";

export class MetronomeUI {
  constructor(
    private m: MetronomeState,
    private onBack: () => void,
    private rerender: () => void = () => {},
  ) {}

  render(parent: HTMLElement): void {
    const m = this.m;
    const screen = el("div", { class: "tool-screen" });

    // Header (fixed)
    const topbar = el("div", { class: "tool-topbar" }, [el("h2", {}, ["Metronome"])]);
    topbar.appendChild(el("span", { style: "flex:1" }));
    topbar.appendChild(btn("Back", () => { m.stop(); this.onBack(); }));
    screen.appendChild(topbar);

    const body = el("div", { class: "et-scroll" });

    // Beat dots — one per beat, the current one lit (accent = larger/act colour).
    const dots = el("div", { style: "display:flex;gap:10px;justify-content:center;align-items:center;margin:18px 0;min-height:34px;flex-wrap:wrap" });
    for (let b = 0; b < m.beatsPerBar; b++) {
      const on = b === m.currentBeat;
      const accent = b === 0;
      const size = accent ? 26 : 20;
      const color = on ? (accent ? "var(--feedback)" : "var(--act)") : "var(--surface2)";
      const border = accent ? "var(--act)" : "var(--line)";
      dots.appendChild(el("div", {
        style: `width:${size}px;height:${size}px;border-radius:50%;background:${color};` +
          `border:2px solid ${on ? color : border};transition:background 60ms linear`,
      }));
    }
    body.appendChild(dots);

    // Transport: Play/Stop + BPM readout.
    const playBtn = btn(m.isPlaying ? "Stop ■" : "Play ▶", () => { m.toggle(); this.rerender(); }, "btn primary");
    body.appendChild(el("div", { class: "row", style: "align-items:center;gap:10px" }, [
      playBtn, el("span", { style: "flex:1" }),
      el("span", { class: "mono", style: "font-weight:700;font-size:16px" }, [`${m.bpm} BPM`]),
    ]));

    // BPM slider + tap tempo.
    body.appendChild(el("div", { style: "margin-top:8px" }, [slider(10, 300, m.bpm, (v) => m.setBpm(v))]));
    body.appendChild(el("div", { class: "row", style: "margin-top:6px;gap:8px;align-items:center" }, [
      btn("−", () => m.setBpm(m.bpm - 1)),
      btn("+", () => m.setBpm(m.bpm + 1)),
      el("span", { style: "flex:1" }),
      btn("Tap tempo", () => { m.tapTempo(); this.rerender(); }),
    ]));

    // Time-signature chips.
    body.appendChild(el("div", { style: "margin-top:14px;font-weight:700;color:var(--act)" }, ["Time signature"]));
    const chips = el("div", { class: "row", style: "gap:6px;flex-wrap:wrap;margin-top:6px" },
      METRONOME_TIME_SIGNATURES.map(([n, d]) => {
        const sel = m.beatsPerBar === n && m.beatUnit === d;
        return btn(`${n}/${d}`, () => { m.setTimeSignature(n, d); this.rerender(); }, sel ? "btn primary" : "btn");
      }));
    body.appendChild(chips);

    body.appendChild(el("div", { class: "et-muted", style: "font-size:13px;margin-top:14px" }, [
      "Two wood clicks — the higher one marks beat 1 of each bar.",
    ]));

    screen.appendChild(body);
    parent.appendChild(screen);
  }
}

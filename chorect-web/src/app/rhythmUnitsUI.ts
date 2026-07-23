// Rhythm screen UI. Mirror of app/.../RhythmUnitsScreen.kt.
// Two sub-modes: "Units" (loop a one-beat unit) and "Phrases" (generate a multi-bar
// rhythmic phrase, shown as notation + a drum-machine-style grid with a playhead).

import { RhythmUnitState } from "./rhythmUnitState";
import { RhythmPhraseState } from "./rhythmPhraseState";
import { el, btn, valueSlider, segmented } from "./dom";
import {
  RHYTHM_UNITS, RHYTHM_UNITS_RESTS, RhythmUnit, RhythmNoteType, starts,
  RhythmPhrase, phraseOnsets, phraseTotalSlots, PHRASE_TIME_SIGNATURES,
} from "../theory";

type SubMode = "units" | "phrases";

// ---- One beat of notation as pure geometric inline SVG (no webfont) ----
// Rendered from primitives — filled slanted noteheads (ellipse), stems + beams
// (rect), flags + rests (path) — so it looks identical everywhere and can never
// fall back to tofu boxes (an earlier Bravura-webfont version did). Scales freely.
function beatSvg(unit: RhythmUnit): string {
  const W = 100, H = 104, sub = unit.subdivision;   // content sits ≈ y 20–92
  const padL = 14, usable = 72;
  const baseline = 84;                 // notehead centre y
  const rx = 8.5, ry = 6.3, tilt = 20; // notehead radii + slant (deg)
  const stemW = 2.3, beamY = 28, beamThick = 7, secGap = 11, stemTopQuarter = 36;

  const startFrac = starts(unit).map((s) => s / sub);
  const cx = startFrac.map((f) => padL + f * usable + rx);
  const stemX = cx.map((x) => x + rx * 0.82);           // stem rides the notehead's right edge
  const is16 = unit.notes.map((n) => !n.rest && n.type === RhythmNoteType.Sixteenth);
  const beamable = unit.notes.map((_, i) => i).filter((i) => !unit.notes[i].rest && unit.notes[i].type !== RhythmNoteType.Quarter);
  const beamed = beamable.length >= 2;

  const p: string[] = [];
  const f2 = (n: number) => n.toFixed(2);
  const notehead = (x: number) =>
    p.push(`<ellipse cx="${f2(x)}" cy="${baseline}" rx="${rx}" ry="${ry}" transform="rotate(-${tilt} ${f2(x)} ${baseline})" fill="currentColor"/>`);
  const rect = (x: number, y: number, w: number, h: number) =>
    p.push(`<rect x="${f2(x)}" y="${f2(y)}" width="${f2(Math.max(w, 0))}" height="${f2(h)}" rx="0.7" fill="currentColor"/>`);
  const stem = (x: number, top: number) => rect(x - stemW / 2, top, stemW, baseline - ry * 0.2 - top);
  const dot = (x: number) => p.push(`<circle cx="${f2(x)}" cy="${baseline}" r="2.4" fill="currentColor"/>`);
  const flag = (sx: number, off: number) =>
    p.push(`<path d="M ${f2(sx)} ${f2(beamY + off)} C ${f2(sx + 13)} ${f2(beamY + off + 4)} ${f2(sx + 12)} ${f2(beamY + off + 14)} ${f2(sx + 3)} ${f2(beamY + off + 21)} C ${f2(sx + 10)} ${f2(beamY + off + 13)} ${f2(sx + 9)} ${f2(beamY + off + 7)} ${f2(sx)} ${f2(beamY + off + 8)} Z" fill="currentColor"/>`);
  const rest = (rcx: number, sixteenth: boolean) => {
    const top = baseline - 26;
    p.push(`<path d="M ${f2(rcx + 7)} ${f2(top)} L ${f2(rcx + 9)} ${f2(top)} L ${f2(rcx - 6)} ${f2(baseline + 8)} L ${f2(rcx - 8)} ${f2(baseline + 8)} Z" fill="currentColor"/>`);
    p.push(`<circle cx="${f2(rcx + 5)}" cy="${f2(top + 3)}" r="4.2" fill="currentColor"/>`);
    if (sixteenth) p.push(`<circle cx="${f2(rcx + 1)}" cy="${f2(top + 13)}" r="4.2" fill="currentColor"/>`);
  };

  unit.notes.forEach((n, i) => {
    if (n.rest) {
      rest(padL + (startFrac[i] + (n.slots / 2) / sub) * usable, n.type === RhythmNoteType.Sixteenth);
      return;
    }
    notehead(cx[i]);
    if (n.type === RhythmNoteType.Quarter) {
      stem(stemX[i], stemTopQuarter);
    } else if (!beamed) {                     // lone note: stem + flag(s)
      stem(stemX[i], beamY);
      flag(stemX[i] - stemW / 2, 0);
      if (n.type === RhythmNoteType.Sixteenth) flag(stemX[i] - stemW / 2, 9);
      if (n.type === RhythmNoteType.DottedEighth) dot(cx[i] + rx + 3);
    } else {                                  // part of a beamed group
      stem(stemX[i], beamY);
      if (n.type === RhythmNoteType.DottedEighth) dot(cx[i] + rx + 3);
    }
  });

  if (beamed) {
    rect(stemX[beamable[0]] - stemW / 2, beamY, stemX[beamable[beamable.length - 1]] - stemX[beamable[0]] + stemW, beamThick);
    const stubLen = (usable / unit.notes.length) * 0.45;
    for (let i = 0; i < unit.notes.length; i++) {
      if (!is16[i]) continue;
      const hasNext16 = i + 1 < unit.notes.length && is16[i + 1];
      const hasPrev16 = i - 1 >= 0 && is16[i - 1];
      const y = beamY + secGap;
      if (hasNext16) rect(stemX[i] - stemW / 2, y, stemX[i + 1] - stemX[i] + stemW, beamThick * 0.85);
      else if (!hasPrev16) { const dir = i === beamable[0] ? 1 : -1; rect(dir > 0 ? stemX[i] - stemW / 2 : stemX[i] - stubLen, y, stubLen + stemW / 2, beamThick * 0.85); }
    }
    if (unit.notes[0]?.type === RhythmNoteType.TripletEighth) {
      p.push(`<text x="${f2((stemX[beamable[0]] + stemX[beamable[beamable.length - 1]]) / 2)}" y="${f2(beamY - 5)}" font-family="sans-serif" font-size="15" font-weight="700" text-anchor="middle" fill="currentColor">3</text>`);
    }
  }

  return `<svg viewBox="0 0 ${W} ${H}" width="100%" height="100%" preserveAspectRatio="xMidYMid meet" style="display:block;color:var(--text-primary)" xmlns="http://www.w3.org/2000/svg">${p.join("")}</svg>`;
}

export class RhythmUnitsUI {
  private subMode: SubMode = "units";
  private phraseScale = 1;   // user-resizable size of the phrase notation + grid

  constructor(
    private ru: RhythmUnitState,
    private rp: RhythmPhraseState,
    private onBack: () => void,
    private rerender: () => void = () => {},
  ) {}

  render(parent: HTMLElement): void {
    const screen = el("div", { class: "tool-screen" });

    // Header (fixed)
    const topbar = el("div", { class: "tool-topbar" }, [el("h2", {}, ["Rhythm"])]);
    topbar.appendChild(el("span", { style: "flex:1" }));
    topbar.appendChild(btn("Back", () => { this.ru.stop(); this.rp.stop(); this.onBack(); }));
    screen.appendChild(topbar);

    // Sub-mode toggle (fixed)
    screen.appendChild(el("div", { style: "margin-top:8px" }, [
      segmented<SubMode>([{ value: "units", label: "Units" }, { value: "phrases", label: "Phrases" }], this.subMode, (v) => {
        this.subMode = v;
        if (v === "units") this.rp.stop();
        else { this.ru.stop(); if (!this.rp.phrase) this.rp.generate(); }
        this.rerender();
      }),
    ]));

    // Scrollable body
    const body = el("div", { class: "et-scroll" });
    if (this.subMode === "units") this.unitsBody(body);
    else this.phrasesBody(body);
    screen.appendChild(body);

    parent.appendChild(screen);
  }

  // ---------- Units ----------

  private unitsBody(body: HTMLElement): void {
    const ru = this.ru;
    body.appendChild(el("div", { class: "et-muted", style: "font-size:13px" },
      ["Tap a unit to loop it. Each is one beat; the downbeat is accented."]));
    const playBtn = btn(ru.isPlaying ? "Stop ■" : "Play ▶", () => ru.toggle(), "btn primary");
    if (!ru.selectedId) playBtn.disabled = true;
    const bpmVS = valueSlider((v) => `${Math.round(v)} BPM`, 10, 300, ru.bpm, (v) => ru.setBpm(v));
    bpmVS.label.className = "mono";
    bpmVS.label.style.fontWeight = "600";
    body.appendChild(el("div", { class: "row", style: "margin-top:8px;align-items:center;gap:10px" }, [
      playBtn, el("span", { style: "flex:1" }), bpmVS.label,
    ]));
    body.appendChild(el("div", { style: "margin-top:6px" }, [bpmVS.input]));
    this.unitSection(body, "Rhythmic units", RHYTHM_UNITS);
    this.unitSection(body, "With rests", RHYTHM_UNITS_RESTS);
  }

  private unitSection(parent: HTMLElement, title: string, units: RhythmUnit[]): void {
    parent.appendChild(el("div", { style: "margin-top:10px;font-weight:700;color:var(--act)" }, [title]));
    const grid = el("div", { style: "display:grid;grid-template-columns:repeat(auto-fill,minmax(112px,1fr));gap:8px;margin-top:6px" });
    for (const unit of units) grid.appendChild(this.unitCard(unit));
    parent.appendChild(grid);
  }

  private unitCard(unit: RhythmUnit): HTMLElement {
    const ru = this.ru;
    const playing = ru.selectedId === unit.id && ru.isPlaying;
    const card = el("div", {
      title: unit.name,
      style:
        "display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;padding:8px 6px;" +
        "border-radius:10px;cursor:pointer;height:100%;box-sizing:border-box;" +
        `background:${playing ? "color-mix(in srgb, var(--feedback) 16%, transparent)" : "var(--surface2)"};` +
        `border:${playing ? "2px" : "1px"} solid ${playing ? "var(--feedback)" : "var(--line)"}`,
    });
    // Geometric inline-SVG notation — crisp at any size, no webfont dependency.
    const svgBox = el("div", { style: "width:100%;height:92px;display:block" });
    svgBox.innerHTML = beatSvg(unit);
    card.appendChild(svgBox);
    card.appendChild(el("div", { class: "mono", style: "font-size:12px;line-height:1.15;color:var(--text-secondary);font-weight:600" }, [unit.count || " "]));
    card.addEventListener("click", () => ru.select(unit.id));
    return card;
  }

  // ---------- Phrases ----------

  private phrasesBody(body: HTMLElement): void {
    const rp = this.rp;
    body.appendChild(el("div", { class: "et-muted", style: "font-size:13px" },
      ["Generate a phrase, then read & play it. The playhead marks the current beat."]));

    // Config: Bars stepper + Time + Generate
    const stepper = el("div", { class: "row", style: "gap:4px;align-items:center" }, [
      btn("−", () => rp.changeBars(rp.bars - 1)),
      el("span", { class: "mono", style: "min-width:16px;text-align:center;font-weight:600" }, [String(rp.bars)]),
      btn("+", () => rp.changeBars(rp.bars + 1)),
    ]);
    const timeChips = el("div", { class: "row", style: "gap:4px" },
      PHRASE_TIME_SIGNATURES.map((n) => btn(`${n}/4`, () => rp.changeBeatsPerBar(n), rp.beatsPerBar === n ? "btn primary" : "btn")));
    body.appendChild(el("div", { class: "row", style: "margin-top:8px;align-items:center;gap:12px;flex-wrap:wrap" }, [
      el("span", { style: "font-size:13px" }, ["Bars"]), stepper,
      el("span", { style: "font-size:13px" }, ["Time"]), timeChips,
    ]));

    // Transport: Generate + Play/Stop + Metronome + BPM
    const pBpmVS = valueSlider((v) => `${Math.round(v)} BPM`, 10, 300, rp.bpm, (v) => rp.setBpm(v));
    pBpmVS.label.className = "mono";
    pBpmVS.label.style.fontWeight = "600";
    body.appendChild(el("div", { class: "row", style: "margin-top:8px;align-items:center;gap:8px" }, [
      btn("Generate ↻", () => rp.generate()),
      btn(rp.isPlaying ? "Stop ■" : "Play ▶", () => rp.toggle(), "btn primary"),
      btn(rp.metronomeOn ? "Metronome ✓" : "Metronome", () => { rp.toggleMetronome(); this.rerender(); }, rp.metronomeOn ? "btn primary" : "btn"),
      el("span", { style: "flex:1" }),
      pBpmVS.label,
    ]));
    body.appendChild(el("div", { style: "margin-top:6px" }, [pBpmVS.input]));

    // Resize control for the notation + grid (web).
    const sizeVS = valueSlider((v) => `Size ${Math.round(v)}%`, 70, 200,
      Math.round(this.phraseScale * 100), (v) => { this.phraseScale = v / 100; this.rerender(); });
    sizeVS.label.style.fontSize = "13px";
    body.appendChild(el("div", { class: "row", style: "margin-top:6px;align-items:center;gap:8px" }, [
      sizeVS.label, sizeVS.input,
    ]));

    const phrase = rp.phrase;
    if (phrase) {
      body.appendChild(this.phraseNotation(phrase));
      body.appendChild(el("div", { style: "margin-top:12px;font-weight:700;color:var(--act)" }, ["Grid"]));
      body.appendChild(this.phraseGrid(phrase));
    }
  }

  private phraseNotation(phrase: RhythmPhrase): HTMLElement {
    const s = this.phraseScale;
    const boxW = Math.round(78 * s), boxH = Math.round(100 * s);
    const currentBeat = this.rp.currentSlot >= 0 ? Math.floor(this.rp.currentSlot / 4) : -1;
    const wrap = el("div", { style: `display:flex;flex-wrap:wrap;gap:${Math.round(6 * s)}px;align-items:flex-end;margin-top:10px` });
    for (let bar = 0; bar < phrase.bars; bar++) {
      const group = el("div", { style: "display:flex;align-items:flex-end" });
      for (let b = 0; b < phrase.beatsPerBar; b++) {
        const gi = bar * phrase.beatsPerBar + b;
        const playing = gi === currentBeat;
        // Geometric inline SVG — scales perfectly at any resolution.
        const box = el("div", {
          style: `width:${boxW}px;height:${boxH}px;border-radius:8px;display:flex;align-items:center;justify-content:center;` +
            (playing ? "background:color-mix(in srgb, var(--feedback) 20%, transparent);" : ""),
        });
        box.innerHTML = beatSvg(phrase.beats[gi]);
        group.appendChild(box);
      }
      group.appendChild(el("div", { style: `width:2px;height:${Math.round(76 * s)}px;background:var(--line);margin:0 ${Math.round(8 * s)}px` }));
      wrap.appendChild(group);
    }
    return wrap;
  }

  private phraseGrid(phrase: RhythmPhrase): HTMLElement {
    const s = this.phraseScale;
    const cellW = Math.round(26 * s), cellH = Math.round(42 * s);
    const onsetAccent = new Map(phraseOnsets(phrase).map((o) => [o.slot, o.accent] as [number, boolean]));
    const total = phraseTotalSlots(phrase);
    const slotsPerBar = phrase.beatsPerBar * 4;
    const row = el("div", { style: "display:flex;align-items:center;overflow-x:auto;margin-top:6px;padding-bottom:4px" });
    for (let slot = 0; slot < total; slot++) {
      if (slot > 0 && slot % slotsPerBar === 0) row.appendChild(el("div", { style: `width:3px;height:${Math.round(cellH * 1.1)}px;background:var(--line);margin:0 3px;flex:0 0 auto` }));
      else if (slot > 0 && slot % 4 === 0) row.appendChild(el("div", { style: "width:6px;flex:0 0 auto" }));
      else if (slot > 0) row.appendChild(el("div", { style: "width:2px;flex:0 0 auto" }));
      const accent = onsetAccent.get(slot);
      const playhead = slot === this.rp.currentSlot;
      const bg = playhead ? "var(--feedback)"
        : accent === true ? "var(--act)"
        : accent === false ? "color-mix(in srgb, var(--act) 55%, transparent)"
        : "var(--surface2)";
      const border = playhead ? "var(--feedback)" : "color-mix(in srgb, var(--line) 40%, transparent)";
      row.appendChild(el("div", { style: `width:${cellW}px;height:${cellH}px;border-radius:4px;flex:0 0 auto;background:${bg};border:1px solid ${border}` }));
    }
    return row;
  }
}


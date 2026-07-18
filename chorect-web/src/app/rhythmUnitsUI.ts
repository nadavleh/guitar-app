// Rhythm screen UI. Mirror of app/.../RhythmUnitsScreen.kt.
// Two sub-modes: "Units" (loop a one-beat unit) and "Phrases" (generate a multi-bar
// rhythmic phrase, shown as notation + a drum-machine-style grid with a playhead).

import { RhythmUnitState } from "./rhythmUnitState";
import { RhythmPhraseState } from "./rhythmPhraseState";
import { el, btn, slider, segmented } from "./dom";
import {
  RHYTHM_UNITS, RHYTHM_UNITS_RESTS, RhythmUnit, RhythmNoteType, starts,
  RhythmPhrase, phraseOnsets, phraseTotalSlots, PHRASE_TIME_SIGNATURES,
} from "../theory";

type SubMode = "units" | "phrases";

// ---- Phrase notation via the Bravura SMuFL music font, rendered as inline SVG ----
// SVG <text> with the Bravura webfont auto-reflows when the font loads (no manual
// redraw → no render loop) and scales to any resolution. Codepoints via
// String.fromCodePoint keep the source ASCII.
const SM = {
  head: String.fromCodePoint(0xE0A4),   // noteheadBlack
  quarter: String.fromCodePoint(0xE1D5),
  n8: String.fromCodePoint(0xE1D7),     // note8thUp (with flag)
  n16: String.fromCodePoint(0xE1D9),    // note16thUp (with flags)
  rest8: String.fromCodePoint(0xE4E6),
  rest16: String.fromCodePoint(0xE4E7),
  dot: String.fromCodePoint(0xE1E7),    // augmentationDot
};

let fontPreloaded = false;
function preloadMusicFont(onLoaded: () => void): void {
  if (fontPreloaded || typeof document === "undefined") return;
  fontPreloaded = true;   // fire exactly once → no render loop
  // Base-aware URL (the site is served under /<repo>/ on project Pages).
  const face = new FontFace("Bravura", `url(${import.meta.env.BASE_URL}fonts/Bravura.woff2)`);
  face.load().then((f) => { document.fonts.add(f); onLoaded(); }).catch(() => {});
}

/** One beat of [unit] as an inline-SVG string: Bravura glyphs for noteheads /
 *  rests / flagged notes, plus <rect> stems + beams. Scales to any size. */
function beatSvg(unit: RhythmUnit): string {
  const W = 100, H = 130, sub = unit.subdivision;
  const padL = 15, usable = W - padL * 2;
  const baseline = 86, fs = 62;
  const noteHalfW = fs * 0.14, stemDx = fs * 0.125, stemW = 2.4;
  const beamThick = 8, beamY = baseline - fs * 0.82, secGap = beamThick * 1.5;

  const startFrac = starts(unit).map((s) => s / sub);
  const noteCx = startFrac.map((f) => padL + f * usable + noteHalfW);
  const stemX = noteCx.map((x) => x + stemDx);
  const is16 = unit.notes.map((n) => !n.rest && n.type === RhythmNoteType.Sixteenth);
  const beamable = unit.notes.map((_, i) => i).filter((i) => !unit.notes[i].rest && unit.notes[i].type !== RhythmNoteType.Quarter);
  const beamed = beamable.length >= 2;

  const parts: string[] = [];
  const text = (g: string, x: number, y: number, anchor = "middle") =>
    parts.push(`<text x="${x.toFixed(2)}" y="${y.toFixed(2)}" font-family="Bravura" font-size="${fs}" text-anchor="${anchor}" fill="currentColor">${g}</text>`);
  const rect = (x: number, y: number, w: number, h: number) =>
    parts.push(`<rect x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${Math.max(w, 0).toFixed(2)}" height="${h.toFixed(2)}" fill="currentColor"/>`);

  unit.notes.forEach((n, i) => {
    if (n.rest) {
      const cxR = padL + (startFrac[i] + (n.slots / 2) / sub) * usable;
      text(n.type === RhythmNoteType.Sixteenth ? SM.rest16 : SM.rest8, cxR, baseline);
    } else if (n.type === RhythmNoteType.Quarter) {
      text(SM.quarter, noteCx[i] - noteHalfW, baseline, "start");
    } else if (!beamed) {
      text(n.type === RhythmNoteType.Sixteenth ? SM.n16 : SM.n8, noteCx[i] - noteHalfW, baseline, "start");
      if (n.type === RhythmNoteType.DottedEighth) text(SM.dot, noteCx[i] + noteHalfW * 1.6, baseline, "start");
    } else {
      text(SM.head, noteCx[i], baseline);
      if (n.type === RhythmNoteType.DottedEighth) text(SM.dot, noteCx[i] + noteHalfW * 1.7, baseline, "start");
    }
  });

  if (beamed) {
    for (const i of beamable) rect(stemX[i] - stemW / 2, beamY, stemW, baseline - beamY);
    rect(stemX[beamable[0]] - stemW / 2, beamY, stemX[beamable[beamable.length - 1]] - stemX[beamable[0]] + stemW, beamThick);
    const stubLen = (usable / unit.notes.length) * 0.4;
    for (let i = 0; i < unit.notes.length; i++) {
      if (!is16[i]) continue;
      const hasNext16 = i + 1 < unit.notes.length && is16[i + 1];
      const hasPrev16 = i - 1 >= 0 && is16[i - 1];
      const y = beamY + secGap;
      if (hasNext16) rect(stemX[i] - stemW / 2, y, stemX[i + 1] - stemX[i] + stemW, beamThick);
      else if (!hasPrev16) { const dir = i === beamable[0] ? 1 : -1; rect(dir > 0 ? stemX[i] - stemW / 2 : stemX[i] - stubLen, y, stubLen + stemW / 2, beamThick); }
    }
    if (unit.notes[0]?.type === RhythmNoteType.TripletEighth) {
      parts.push(`<text x="${((stemX[beamable[0]] + stemX[beamable[beamable.length - 1]]) / 2).toFixed(2)}" y="${(beamY - 4).toFixed(2)}" font-family="sans-serif" font-size="16" font-weight="700" text-anchor="middle" fill="currentColor">3</text>`);
    }
  }

  return `<svg viewBox="0 0 ${W} ${H}" width="100%" height="100%" preserveAspectRatio="xMidYMid meet" style="display:block;color:var(--text-primary)" xmlns="http://www.w3.org/2000/svg">${parts.join("")}</svg>`;
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
    preloadMusicFont(() => this.rerender());   // once; redraws the SVG notation when Bravura loads
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
    body.appendChild(el("div", { class: "row", style: "margin-top:8px;align-items:center;gap:10px" }, [
      playBtn, el("span", { style: "flex:1" }), el("span", { class: "mono", style: "font-weight:600" }, [`${ru.bpm} BPM`]),
    ]));
    body.appendChild(el("div", { style: "margin-top:6px" }, [slider(10, 300, ru.bpm, (v) => ru.setBpm(v))]));
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
    // Bravura SMuFL notation (inline SVG) — crisp at any size, reflows on font-load.
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

    // Transport: Generate + Play/Stop + BPM
    body.appendChild(el("div", { class: "row", style: "margin-top:8px;align-items:center;gap:8px" }, [
      btn("Generate ↻", () => rp.generate()),
      btn(rp.isPlaying ? "Stop ■" : "Play ▶", () => rp.toggle(), "btn primary"),
      el("span", { style: "flex:1" }),
      el("span", { class: "mono", style: "font-weight:600" }, [`${rp.bpm} BPM`]),
    ]));
    body.appendChild(el("div", { style: "margin-top:6px" }, [slider(10, 300, rp.bpm, (v) => rp.setBpm(v))]));

    // Resize control for the notation + grid (web).
    body.appendChild(el("div", { class: "row", style: "margin-top:6px;align-items:center;gap:8px" }, [
      el("span", { style: "font-size:13px" }, ["Size"]),
      slider(70, 200, Math.round(this.phraseScale * 100), (v) => { this.phraseScale = v / 100; this.rerender(); }),
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
        // Inline SVG (Bravura glyphs) — scales perfectly and reflows on font-load.
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


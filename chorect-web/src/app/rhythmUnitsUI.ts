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

export class RhythmUnitsUI {
  private subMode: SubMode = "units";

  constructor(private ru: RhythmUnitState, private rp: RhythmPhraseState, private onBack: () => void) {}

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
        // Both branches call a state stop()/generate(), which fires onChange →
        // scheduleRender, so the screen re-renders in the new sub-mode.
        if (v === "units") this.rp.stop();
        else { this.ru.stop(); if (!this.rp.phrase) this.rp.generate(); }
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
    const grid = el("div", { style: "display:grid;grid-template-columns:repeat(auto-fill,minmax(76px,1fr));gap:6px;margin-top:6px" });
    for (const unit of units) grid.appendChild(this.unitCard(unit));
    parent.appendChild(grid);
  }

  private unitCard(unit: RhythmUnit): HTMLElement {
    const ru = this.ru;
    const playing = ru.selectedId === unit.id && ru.isPlaying;
    const card = el("div", {
      title: unit.name,
      style:
        "display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;padding:5px 4px;" +
        "border-radius:8px;cursor:pointer;height:100%;box-sizing:border-box;" +
        `background:${playing ? "color-mix(in srgb, var(--feedback) 16%, transparent)" : "var(--surface2)"};` +
        `border:${playing ? "2px" : "1px"} solid ${playing ? "var(--feedback)" : "var(--line)"}`,
    });
    const cv = el("canvas", { style: "width:100%;height:auto;display:block" }) as HTMLCanvasElement;
    cv.width = 360; cv.height = 120;
    drawNotation(cv, unit);
    card.appendChild(cv);
    card.appendChild(el("div", { class: "mono", style: "font-size:10px;line-height:1.1;color:var(--text-secondary)" }, [unit.count || " "]));
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

    const phrase = rp.phrase;
    if (phrase) {
      body.appendChild(this.phraseNotation(phrase));
      body.appendChild(el("div", { style: "margin-top:12px;font-weight:700;color:var(--act)" }, ["Grid"]));
      body.appendChild(this.phraseGrid(phrase));
    }
  }

  private phraseNotation(phrase: RhythmPhrase): HTMLElement {
    const currentBeat = this.rp.currentSlot >= 0 ? Math.floor(this.rp.currentSlot / 4) : -1;
    const wrap = el("div", { style: "display:flex;flex-wrap:wrap;gap:8px;align-items:flex-end;margin-top:10px" });
    for (let bar = 0; bar < phrase.bars; bar++) {
      const group = el("div", { style: "display:flex;align-items:flex-end" });
      for (let b = 0; b < phrase.beatsPerBar; b++) {
        const gi = bar * phrase.beatsPerBar + b;
        const playing = gi === currentBeat;
        const box = el("div", {
          style: "width:74px;height:80px;border-radius:8px;display:flex;align-items:center;justify-content:center;" +
            (playing ? "background:color-mix(in srgb, var(--feedback) 20%, transparent);" : ""),
        });
        const cv = el("canvas", { style: "width:100%;height:auto;display:block" }) as HTMLCanvasElement;
        cv.width = 120; cv.height = 120;
        drawNotation(cv, phrase.beats[gi]);
        box.appendChild(cv);
        group.appendChild(box);
      }
      group.appendChild(el("div", { style: "width:2px;height:60px;background:var(--line);margin:0 8px" }));
      wrap.appendChild(group);
    }
    return wrap;
  }

  private phraseGrid(phrase: RhythmPhrase): HTMLElement {
    const onsetAccent = new Map(phraseOnsets(phrase).map((o) => [o.slot, o.accent] as [number, boolean]));
    const total = phraseTotalSlots(phrase);
    const slotsPerBar = phrase.beatsPerBar * 4;
    const row = el("div", { style: "display:flex;align-items:center;overflow-x:auto;margin-top:6px;padding-bottom:4px" });
    for (let slot = 0; slot < total; slot++) {
      if (slot > 0 && slot % slotsPerBar === 0) row.appendChild(el("div", { style: "width:3px;height:46px;background:var(--line);margin:0 3px;flex:0 0 auto" }));
      else if (slot > 0 && slot % 4 === 0) row.appendChild(el("div", { style: "width:6px;flex:0 0 auto" }));
      else if (slot > 0) row.appendChild(el("div", { style: "width:2px;flex:0 0 auto" }));
      const accent = onsetAccent.get(slot);
      const playhead = slot === this.rp.currentSlot;
      const bg = playhead ? "var(--feedback)"
        : accent === true ? "var(--act)"
        : accent === false ? "color-mix(in srgb, var(--act) 55%, transparent)"
        : "var(--surface2)";
      const border = playhead ? "var(--feedback)" : "color-mix(in srgb, var(--line) 40%, transparent)";
      row.appendChild(el("div", { style: `width:26px;height:42px;border-radius:4px;flex:0 0 auto;background:${bg};border:1px solid ${border}` }));
    }
    return row;
  }
}

/** Draw [unit] as simple notation on [cv] — mirror of RhythmNotation (Compose). */
function drawNotation(cv: HTMLCanvasElement, unit: RhythmUnit): void {
  const ctx = cv.getContext("2d");
  if (!ctx) return;
  const w = cv.width, h = cv.height;
  ctx.clearRect(0, 0, w, h);
  const color = getComputedStyle(document.documentElement).getPropertyValue("--text-primary").trim() || "#111";
  ctx.fillStyle = color;
  ctx.strokeStyle = color;

  const padL = w * 0.12, usable = w - padL * 2;
  const baseline = h * 0.72, beamY = h * 0.20;
  const headRx = Math.min(h * 0.11, w * 0.05), headRy = headRx * 0.78;
  const stemW = Math.max(h * 0.035, 2), beamThick = h * 0.10;
  const sub = unit.subdivision;

  const line = (x1: number, y1: number, x2: number, y2: number, width: number) => {
    ctx.lineWidth = width;
    ctx.lineCap = "butt";
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.stroke();
  };
  const circle = (x: number, y: number, r: number) => {
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  };

  const startFrac = starts(unit).map((s) => s / sub);
  const noteCx = startFrac.map((f) => padL + f * usable + headRx);
  const stemX = noteCx.map((x) => x + headRx * 0.9);
  const is16 = unit.notes.map((n) => !n.rest && n.type === RhythmNoteType.Sixteenth);
  const beamable = unit.notes.map((_, i) => i).filter((i) => !unit.notes[i].rest && unit.notes[i].type !== RhythmNoteType.Quarter);

  unit.notes.forEach((n, i) => {
    if (n.rest) {
      const cxR = padL + (startFrac[i] + (n.slots / 2) / sub) * usable;
      const midY = (beamY + baseline) / 2;
      const h2 = (baseline - beamY) * 0.40;
      const dotR = headRy * 0.55;
      const dotX = cxR - headRx * 0.25;
      const dotTop = midY - h2 + dotR;
      circle(dotX, dotTop, dotR);
      line(dotX + dotR * 0.6, dotTop - dotR * 0.2, cxR + headRx * 0.5, midY + h2, stemW);
      if (n.type === RhythmNoteType.Sixteenth) circle(dotX - dotR * 0.3, dotTop + dotR * 1.8, dotR);
    } else {
      ctx.beginPath();
      ctx.ellipse(noteCx[i], baseline, headRx, headRy, 0, 0, Math.PI * 2);
      ctx.fill();
      line(stemX[i], baseline - headRy * 0.4, stemX[i], beamY, stemW);
      if (n.type === RhythmNoteType.DottedEighth) circle(noteCx[i] + headRx * 1.7, baseline, headRy * 0.42);
    }
  });

  if (beamable.length >= 2) {
    line(stemX[beamable[0]], beamY, stemX[beamable[beamable.length - 1]], beamY, beamThick);
    const secY = beamY + beamThick * 1.5;
    const stubLen = (usable / unit.notes.length) * 0.4;
    for (let i = 0; i < unit.notes.length; i++) {
      if (!is16[i]) continue;
      const hasNext16 = i + 1 < unit.notes.length && is16[i + 1];
      const hasPrev16 = i - 1 >= 0 && is16[i - 1];
      if (hasNext16) line(stemX[i], secY, stemX[i + 1], secY, beamThick);
      else if (!hasPrev16) {
        const dir = i === beamable[0] ? 1 : -1;
        line(stemX[i], secY, stemX[i] + dir * stubLen, secY, beamThick);
      }
    }
  } else if (beamable.length === 1) {
    const j = beamable[0];
    const flags = unit.notes[j].type === RhythmNoteType.Sixteenth ? 2 : 1;
    const flagLen = headRx * 1.6, flagDrop = (baseline - beamY) * 0.32;
    for (let k = 0; k < flags; k++) {
      const y = beamY + k * flagDrop * 0.6;
      line(stemX[j], y, stemX[j] + flagLen, y + flagDrop, stemW * 1.1);
    }
  }

  if (unit.notes[0]?.type === RhythmNoteType.TripletEighth) {
    ctx.font = `bold ${Math.round(h * 0.22)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "alphabetic";
    ctx.fillText("3", (stemX[0] + stemX[stemX.length - 1]) / 2, beamY - h * 0.04);
  }
}

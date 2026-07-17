// Rhythmic Units screen UI. Mirror of app/.../RhythmUnitsScreen.kt.
// One section: a grid of unit cards, each with a music-notation thumbnail canvas.
// Tapping a card loops the unit at the transport BPM (playing card highlights).

import { RhythmUnitState } from "./rhythmUnitState";
import { el, btn, slider } from "./dom";
import { RHYTHM_UNITS, RhythmUnit, RhythmNoteType, onsetFractions } from "../theory";

export class RhythmUnitsUI {
  constructor(private ru: RhythmUnitState, private onBack: () => void) {}

  render(parent: HTMLElement): void {
    const ru = this.ru;
    const screen = el("div", { class: "tool-screen" });

    // Header
    const topbar = el("div", { class: "tool-topbar" }, [el("h2", {}, ["Rhythm"])]);
    topbar.appendChild(el("span", { style: "flex:1" }));
    topbar.appendChild(btn("Back", () => { ru.stop(); this.onBack(); }));
    screen.appendChild(topbar);
    screen.appendChild(el("div", { class: "et-muted", style: "font-size:13px;margin-top:2px" },
      ["Tap a unit to loop it. Each is one beat; the downbeat is accented."]));

    // Transport: Play/Stop + BPM
    const playBtn = btn(ru.isPlaying ? "Stop ■" : "Play ▶", () => ru.toggle(), "btn primary");
    if (!ru.selectedId) playBtn.disabled = true;
    screen.appendChild(el("div", { class: "row", style: "margin-top:10px;align-items:center;gap:10px" }, [
      playBtn,
      el("span", { style: "flex:1" }),
      el("span", { class: "mono", style: "font-weight:600" }, [`${ru.bpm} BPM`]),
    ]));
    screen.appendChild(el("div", { style: "margin-top:6px" }, [slider(10, 300, ru.bpm, (v) => ru.setBpm(v))]));

    // Section
    screen.appendChild(el("div", { style: "margin-top:10px;font-weight:700;color:var(--act)" }, ["Rhythmic units"]));

    const grid = el("div", { style: "display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:8px" });
    for (const unit of RHYTHM_UNITS) {
      grid.appendChild(this.unitCard(unit));
    }
    screen.appendChild(grid);

    parent.appendChild(screen);
  }

  private unitCard(unit: RhythmUnit): HTMLElement {
    const ru = this.ru;
    const playing = ru.selectedId === unit.id && ru.isPlaying;
    const card = el("div", {
      style:
        "display:flex;flex-direction:column;align-items:center;gap:4px;padding:10px;border-radius:10px;cursor:pointer;" +
        `background:${playing ? "color-mix(in srgb, var(--feedback) 14%, transparent)" : "var(--surface2)"};` +
        `border:${playing ? "2px" : "1px"} solid ${playing ? "var(--feedback)" : "var(--line)"}`,
    });
    const cv = el("canvas", { style: "width:100%;height:58px" }) as HTMLCanvasElement;
    cv.width = 300;
    cv.height = 116;
    drawNotation(cv, unit);
    card.appendChild(cv);
    card.appendChild(el("div", { class: "mono", style: "font-size:13px;color:var(--text-secondary)" }, [unit.count]));
    card.appendChild(el("div", { style: "font-size:13px;font-weight:600" }, [unit.name]));
    card.addEventListener("click", () => ru.select(unit.id));
    return card;
  }
}

/** Draw [unit] as simple notation on [cv]: noteheads at their beat positions, stems,
 *  a primary beam across sub-quarter notes, secondary (16th) beams/stubs, a dot for
 *  the dotted eighth, a "3" for the triplet. Placement follows onset fractions. */
function drawNotation(cv: HTMLCanvasElement, unit: RhythmUnit): void {
  const ctx = cv.getContext("2d");
  if (!ctx) return;
  const w = cv.width, h = cv.height;
  ctx.clearRect(0, 0, w, h);
  const color = getComputedStyle(document.documentElement).getPropertyValue("--text-primary").trim() || "#111";
  ctx.fillStyle = color;
  ctx.strokeStyle = color;

  const padL = w * 0.12, padR = w * 0.12, usable = w - padL - padR;
  const baseline = h * 0.72, beamY = h * 0.20;
  const headRx = Math.min(h * 0.11, w * 0.05), headRy = headRx * 0.78;
  const stemW = Math.max(h * 0.035, 2), beamThick = h * 0.10;

  const fr = onsetFractions(unit);
  const beamable = unit.notes.map((n) => n.type !== RhythmNoteType.Quarter);
  const is16 = unit.notes.map((n) => n.type === RhythmNoteType.Sixteenth);
  const cx = fr.map((f) => padL + f * usable + headRx);
  const stemX = cx.map((x) => x + headRx * 0.9);

  const line = (x1: number, y1: number, x2: number, y2: number, width: number) => {
    ctx.lineWidth = width;
    ctx.lineCap = "butt";
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.stroke();
  };

  // Noteheads + stems
  for (let i = 0; i < unit.notes.length; i++) {
    ctx.beginPath();
    ctx.ellipse(cx[i], baseline, headRx, headRy, 0, 0, Math.PI * 2);
    ctx.fill();
    if (beamable[i] || unit.notes.length === 1) {
      line(stemX[i], baseline - headRy * 0.4, stemX[i], beamY, stemW);
    }
  }

  // Dotted-eighth augmentation dot
  unit.notes.forEach((n, i) => {
    if (n.type === RhythmNoteType.DottedEighth) {
      ctx.beginPath();
      ctx.arc(cx[i] + headRx * 1.7, baseline, headRy * 0.42, 0, Math.PI * 2);
      ctx.fill();
    }
  });

  // Primary beam across all beamable notes
  const beamIdx = unit.notes.map((_, i) => i).filter((i) => beamable[i]);
  if (beamIdx.length >= 2) {
    line(stemX[beamIdx[0]], beamY, stemX[beamIdx[beamIdx.length - 1]], beamY, beamThick);
    const secY = beamY + beamThick * 1.5;
    const stubLen = (usable / unit.notes.length) * 0.4;
    let i = 0;
    while (i < unit.notes.length) {
      if (is16[i]) {
        if (i + 1 < unit.notes.length && is16[i + 1]) {
          line(stemX[i], secY, stemX[i + 1], secY, beamThick);
          i += 2;
          continue;
        }
        const dir = i === 0 ? 1 : -1;
        line(stemX[i], secY, stemX[i] + dir * stubLen, secY, beamThick);
      }
      i++;
    }
  }

  // Triplet "3"
  if (unit.notes[0]?.type === RhythmNoteType.TripletEighth) {
    ctx.font = `bold ${Math.round(h * 0.22)}px sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "alphabetic";
    ctx.fillText("3", (stemX[0] + stemX[stemX.length - 1]) / 2, beamY - h * 0.04);
  }
}

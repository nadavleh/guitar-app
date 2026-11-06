// Cavaquinho Progressions screen UI. Mirror of app/.../CavaqProgressionsScreen.kt.
// A view class owning its own fretboard canvas, re-rendered on each state change.

import { AppState } from "./appState";
import { CavaqProgState } from "./cavaqProgState";
import { FretboardCanvas } from "./fretboardCanvas";
import { shapeMarks } from "./marks";
import { el, btn, valueSlider, labelSm, songLinkRow } from "./dom";
import { CAVAQ_SEQUENCES, noteAt, cavaqSongsForSequence, CAVAQ_FORM_QUALITIES, cavaqBassLabel, spellPc, ChordShape } from "../theory";

const DISPLAY_FRETS = 14;

export class CavaqProgUI {
  private fbCanvasEl: HTMLCanvasElement | null = null;
  private fb: FretboardCanvas | null = null;
  /** One canvas per form on the Shapes tab, reused across renders. */
  private formCanvases: { el: HTMLCanvasElement; fb: FretboardCanvas }[] = [];

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
    const songsBtn = btn("Songs ♪", () => this.showSongsPopup());
    const topbar = el("div", { class: "tool-topbar" }, [el("h2", {}, ["Progressions"])]);
    topbar.appendChild(el("span", { style: "flex:1" }));
    topbar.appendChild(songsBtn);
    screen.appendChild(topbar);

    // ---- Tabs: the sequence looper, or the chord-forms ("cavaquinho CAGED") view ----
    const tab = (label: string, on: boolean, f: () => void) => btn(label, f, on ? "btn primary" : "btn");
    screen.appendChild(el("div", { class: "row", style: "gap:6px;margin-top:6px" }, [
      tab("Sequences", !cp.showShapes, () => cp.setShowShapes(false)),
      tab("Shapes", cp.showShapes, () => cp.setShowShapes(true)),
    ]));
    if (cp.showShapes) {
      const after = this.renderShapes(screen);
      parent.appendChild(screen);
      after.forEach((f) => f());   // canvases get their data once they are in the DOM
      return;
    }

    // ---- Sequence picker ----
    const sel = el("select", { class: "btn", style: "flex:1;min-width:0" });
    for (const seq of CAVAQ_SEQUENCES) {
      const label = seq.nameEn === seq.namePt ? seq.nameEn : `${seq.nameEn}  ·  ${seq.namePt}`;
      const opt = el("option", { value: seq.id }, [label]);
      if (seq.id === cp.sequenceId) opt.setAttribute("selected", "");
      sel.appendChild(opt);
    }
    sel.addEventListener("change", () => { cp.setSequence(sel.value); });
    const seqRow = el("div", { style: "display:flex;gap:6px;align-items:center;margin-top:6px" }, [
      btn("◀", () => cp.stepSequence(-1), "btn"), sel, btn("▶", () => cp.stepSequence(+1), "btn"),
    ]);
    screen.appendChild(el("div", { style: "margin-top:8px" }, [labelSm("Sequence"), seqRow]));

    // ---- Key + transpose ----
    screen.appendChild(el("div", { class: "row", style: "margin-top:10px;align-items:center;gap:8px" }, [
      el("span", { class: "mono", style: "min-width:88px" }, [`Key: ${cp.keyLabel()}`]),
      btn("−", () => cp.shiftKey(-1), "btn"),
      btn("+", () => cp.shiftKey(+1), "btn"),
      el("span", { style: "flex:1" }),
      btn("Reset to G", () => cp.chooseKey(7), "btn text"),
    ]));

    // ---- Tempo (live readout; double-click to type) ----
    const bpmVS = valueSlider((v) => `Tempo: ${Math.round(v)} BPM`, 40, 200, cp.bpm, (v) => cp.changeBpm(v));
    bpmVS.label.className = "label-sm";
    screen.appendChild(el("div", { style: "margin-top:8px" }, [
      bpmVS.label,
      bpmVS.input,
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
      tuning: this.cp.tuning, marks, selectedPosition: null, leftHanded: s.leftHanded,
      numFrets: DISPLAY_FRETS, playOnTouchDown: false, mutedStrings: new Set<number>(),
      onTap: (pos) => s.audio.playNote(noteAt(this.cp.tuning, pos).midi, s.ringSustainMs),
    });
  }

  /** Shapes tab: pick a chord (root + type), see its movable forms — one per chord tone
   *  in the bass, up the neck — each with its branch-outs (one string moved). */
  private renderShapes(screen: HTMLElement): (() => void)[] {
    const after: (() => void)[] = [];
    const cp = this.cp, s = this.state;
    const sel = el("select", { class: "btn", style: "flex:1;min-width:0" });
    for (const q of CAVAQ_FORM_QUALITIES) {
      const opt = el("option", { value: q }, [q === "" ? "major" : q]);
      if (q === cp.formQuality) opt.setAttribute("selected", "");
      sel.appendChild(opt);
    }
    sel.addEventListener("change", () => cp.chooseFormQuality(sel.value));
    screen.appendChild(el("div", { class: "row", style: "gap:6px;align-items:center;margin-top:10px" }, [
      btn("◀", () => cp.shiftFormRoot(-1), "btn"),
      el("span", { style: "font-weight:700;font-size:18px;min-width:36px;text-align:center" }, [spellPc(cp.formRoot)]),
      btn("▶", () => cp.shiftFormRoot(+1), "btn"),
      sel,
    ]));
    const forms = cp.forms();
    const name = spellPc(cp.formRoot) + cp.formQuality;
    screen.appendChild(el("div", { class: "row", style: "gap:8px;align-items:center;margin-top:8px" }, [
      el("span", { style: "font-weight:600;flex:1" }, [`${name} — ${forms.length} forms`]),
      btn("Play all ▶", () => cp.playAllForms(), "btn primary"),
    ]));
    screen.appendChild(labelSm("One form per chord tone in the bass, low → high on the neck. Branch-outs move one string to another chord tone."));
    if (forms.length === 0) { screen.appendChild(el("div", {}, ["No four-string form for this chord."])); return after; }
    const fretsTxt = (sh: ChordShape) => sh.frets.map((f) => (f === null ? "x" : String(f))).join(" ");
    forms.forEach((form, i) => {
      const pick = cp.formPick.get(i) ?? 0;
      const shape = cp.formShape(i)!;
      screen.appendChild(el("div", {
        style: `font-weight:700;margin-top:14px;${cp.formPlaying === i ? "color:var(--act)" : ""}`,
      }, [`Form ${i + 1} · ${cavaqBassLabel(form.bass)} in the bass`]));
      const chips = el("div", { class: "row", style: "flex-wrap:wrap;gap:6px;margin-top:4px" });
      chips.appendChild(btn(`Main  ${fretsTxt(form.shape)}`, () => cp.pickForm(i, 0), pick === 0 ? "btn primary" : "btn"));
      form.branches.forEach((b, k) => {
        const bass = (b.templateName ?? "").replace(" in the bass", "");
        chips.appendChild(btn(`${bass} bass  ${fretsTxt(b)}`, () => cp.pickForm(i, k + 1), pick === k + 1 ? "btn primary" : "btn"));
      });
      screen.appendChild(chips);
      if (!this.formCanvases[i]) {
        const c = el("canvas", { class: "fretboard" }) as HTMLCanvasElement;
        this.formCanvases[i] = { el: c, fb: new FretboardCanvas(c) };
      }
      const { el: canvas, fb } = this.formCanvases[i];
      const wrap = el("div", { style: "height:150px;position:relative;margin:6px 0" });
      wrap.appendChild(canvas);
      screen.appendChild(wrap);
      after.push(() => fb.setData({
        tuning: cp.tuning, marks: shapeMarks(shape, s.labelMode), selectedPosition: null, leftHanded: s.leftHanded,
        numFrets: DISPLAY_FRETS, playOnTouchDown: false, mutedStrings: new Set<number>(),
        onTap: (pos) => s.audio.playNote(noteAt(cp.tuning, pos).midi, s.ringSustainMs),
      }));
    });
    return after;
  }

  /** Popup of curated samba songs whose functional family matches the current sequence. */
  private showSongsPopup(): void {
    const songs = cavaqSongsForSequence(this.cp.sequenceId);
    const body = songs.length
      ? el("div", {}, songs.map((sg) => songLinkRow(sg.title, sg.artist, `  (${sg.keyLabel})`)))
      : el("div", { class: "et-muted" }, ["No curated songs match this sequence yet."]);
    const closeBtn = btn("Close", () => scrim.remove(), "btn primary");
    const card = el("div", {
      class: "et-card",
      style: "max-width:480px;max-height:75vh;overflow:auto;margin:auto;background:var(--surface-elev);color:var(--text-primary)",
    }, [
      el("div", { style: "font-weight:700;font-size:16px;margin-bottom:8px" }, [`Samba songs — ${this.cp.sequence.nameEn}`]),
      body,
      el("div", { style: "text-align:right;margin-top:10px" }, [closeBtn]),
    ]);
    card.addEventListener("click", (e) => e.stopPropagation());
    const scrim = el("div", { style: "position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;padding:16px;z-index:60" }, [card]);
    scrim.addEventListener("click", () => scrim.remove());
    document.body.appendChild(scrim);
  }

  private navBtn(text: string, onClick: () => void, disabled: boolean): HTMLButtonElement {
    const b = btn(text, onClick, "btn");
    b.disabled = disabled;
    return b;
  }
}

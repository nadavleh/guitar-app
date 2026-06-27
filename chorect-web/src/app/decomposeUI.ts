// "Decompose 🧩" tool (#5), ported from app/.../DecomposeScreen.kt. A non-quiz
// reference showing how an extended chord splits into a shell (root + guide tones)
// and an upper-structure triad, drawn on the fretboard in two colours.

import { AppState, LabelMode } from "./appState";
import { FretboardCanvas } from "./fretboardCanvas";
import { FretMark, MarkKind } from "./marks";
import { Colors } from "./theme";
import { el, btn, clear } from "./dom";
import {
  PitchClass, spellPc, noteAt, midiPitchClass, fp, fpKey,
  ChordDecomposition, CHORD_DECOMPOSITIONS, decompositionFor, upperRootInterval,
} from "../theory";
import { Timbres } from "../audio";

const DISPLAY_FRETS = 14;

export class DecomposeUI {
  private root: PitchClass = 0;
  private quality = CHORD_DECOMPOSITIONS[0].quality;
  private parent: HTMLElement | null = null;
  private fbCanvasEl: HTMLCanvasElement | null = null;
  private fb: FretboardCanvas | null = null;

  constructor(private state: AppState, private onBack: () => void) {}

  private rerender(): void { if (this.parent) this.render(this.parent); }

  render(parent: HTMLElement): void {
    this.parent = parent;
    clear(parent);
    const s = this.state;
    const dec = decompositionFor(this.quality) ?? CHORD_DECOMPOSITIONS[0];

    const screen = el("div", { class: "tool-screen" });
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("div", { class: "tool-title" }, ["DECOMPOSE"]),
      btn("Back", () => this.onBack()),
    ]));

    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);

    body.appendChild(el("div", { class: "et-muted" }, [
      "See how an extended chord = a shell (root + guide tones) plus a triad on top — the way pianists voice extensions with two hands.",
    ]));

    // Root picker (native select) + chord-type chips.
    const rootSel = el("select", { class: "drum-select" }) as HTMLSelectElement;
    for (let i = 0; i < 12; i++) {
      const opt = el("option", { value: String(i) }, [spellPc(i)]) as HTMLOptionElement;
      if (i === this.root) opt.selected = true;
      rootSel.appendChild(opt);
    }
    rootSel.addEventListener("change", () => { this.root = parseInt(rootSel.value, 10); this.rerender(); });
    body.appendChild(el("div", { class: "et-row-gap", style: "margin-top:6px" }, [
      el("span", { class: "ans-label" }, ["Root"]), rootSel,
    ]));

    const chips = CHORD_DECOMPOSITIONS.map((c) => {
      const b = el("button", { class: c.quality === this.quality ? "chip selected" : "chip" }, [spellPc(this.root) + c.displayName]);
      b.addEventListener("click", () => { this.quality = c.quality; this.rerender(); });
      return b;
    });
    body.appendChild(el("div", { class: "chip-row", style: "margin-top:6px" }, chips));

    // Summary card.
    const shellNotes = dec.shell.map((iv) => spellPc(((this.root + iv) % 12 + 12) % 12));
    const upperPc = ((this.root + upperRootInterval(dec)) % 12 + 12) % 12;
    const card = el("div", { class: "et-card", style: "margin-top:10px" }, [
      el("div", { style: "font-weight:700;font-size:16px" }, [`${spellPc(this.root)}${dec.displayName}  ≈  shell + triad`]),
      el("div", { style: `color:${Colors.primary}` }, [`Shell (bass): ${shellNotes.join(" · ")}`]),
      el("div", { style: `color:${Colors.scaleTone}` }, [`Upper triad: ${spellPc(upperPc)} ${dec.upperTriad}   (${dec.upperDegrees})`]),
    ]);
    body.appendChild(card);

    body.appendChild(el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      btn("Play shell → triad ▶", () => this.play(dec), "btn primary"),
      el("span", { class: "et-muted" }, ["● shell   ● triad"]),
    ]));

    // Fretboard.
    if (!this.fbCanvasEl) {
      this.fbCanvasEl = el("canvas", { class: "fretboard" });
      this.fb = new FretboardCanvas(this.fbCanvasEl);
    }
    const wrap = el("div", { style: "height:240px;position:relative;margin:8px 0" });
    wrap.appendChild(this.fbCanvasEl);
    body.appendChild(wrap);
    this.fb!.setData({
      tuning: s.liveTuning, marks: this.marks(dec), selectedPosition: null, leftHanded: s.leftHanded,
      numFrets: DISPLAY_FRETS, playOnTouchDown: false, mutedStrings: new Set<number>(),
      onTap: (pos) => s.audio.playNote(noteAt(s.liveTuning, pos).midi, s.ringSustainMs),
    });

    parent.appendChild(screen);
  }

  private play(dec: ChordDecomposition): void {
    const base = 48 + this.root;
    const shell = dec.shell.map((iv) => base + iv);
    const upper = dec.upper.map((iv) => base + iv);
    this.state.audio.playChord(shell, 26, 1100, Timbres.Clarity);
    setTimeout(() => this.state.audio.playChord(upper, 26, 1100, Timbres.Clarity), 700);
  }

  private marks(dec: ChordDecomposition): Map<string, FretMark> {
    const rootPc = this.root;
    const shellPcs = new Set(dec.shell.map((iv) => ((rootPc + iv) % 12 + 12) % 12));
    const upperPcs = new Set(dec.upper.map((iv) => ((rootPc + iv) % 12 + 12) % 12));
    const tuning = this.state.liveTuning;
    const out = new Map<string, FretMark>();
    for (let str = 0; str < tuning.openStrings.length; str++) {
      for (let f = 0; f <= DISPLAY_FRETS; f++) {
        const pos = fp(str, f);
        const pc = midiPitchClass(noteAt(tuning, pos).midi);
        const label = this.state.labelMode === LabelMode.Notes ? spellPc(pc) : "";
        if (shellPcs.has(pc)) {
          out.set(fpKey(pos), { label, isRoot: pc === rootPc, kind: MarkKind.Chord });
        } else if (upperPcs.has(pc)) {
          out.set(fpKey(pos), { label, isRoot: false, kind: MarkKind.Scale });
        }
      }
    }
    return out;
  }
}

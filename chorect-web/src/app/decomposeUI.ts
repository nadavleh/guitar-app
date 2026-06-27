// "Decompose 🧩" tool (#5), ported from app/.../DecomposeScreen.kt. A non-quiz
// reference showing how an extended chord splits into a shell (root + guide tones)
// and an upper-structure triad, drawn on the fretboard in two colours.

import { AppState } from "./appState";
import { FretboardCanvas } from "./fretboardCanvas";
import { FretMark, MarkKind } from "./marks";
import { Colors } from "./theme";
import { el, btn, clear } from "./dom";

/** Degree label for a chord-relative interval (incl. compound 9/11/13). */
function decomposeDegree(iv: number): string {
  switch (iv) {
    case 0: case 12: return "1";
    case 1: case 13: return "♭9";
    case 2: case 14: return "9";
    case 3: return "♭3"; case 15: return "♯9";
    case 4: case 16: return "3";
    case 5: case 17: return "11";
    case 6: return "♭5"; case 18: return "♯11";
    case 7: return "5";
    case 8: return "♯5"; case 20: return "♭13";
    case 9: return "6"; case 21: return "13";
    case 10: return "♭7";
    case 11: return "7";
    default: return String(iv);
  }
}

const DECOMPOSE_GUIDE = [
  "Pianists voice extensions as a shell in the left hand (root + the 3rd & 7th that define the quality) and a triad in the right hand. On guitar it's the same idea — and every circle here is labelled with its interval degree.",
  "",
  "6th chords — root + a triad on the 6th:",
  "• C6 = C + A minor (the 6th carries a minor triad)",
  "• Cm6 = C + A°    (C6 shares its notes with Am7)",
  "",
  "7th chords — root + a triad on the 3rd:",
  "• Cmaj7 = C + E minor (3·5·7)",
  "• C7 = C + E° (3·5·♭7)",
  "• Cm7 = C + E♭ major (♭3·5·♭7)",
  "• Cm7♭5 = C + E♭ minor;   C°7 = C + E♭°",
  "",
  "Extensions — shell (1·3·♭7) + an upper-structure triad:",
  "• C9 = C7 shell + G minor (5·♭7·9)",
  "• Cmaj9 = Cmaj7 shell + G major (5·7·9)",
  "• C11 = C7 shell + B♭ major (♭7·9·11)",
  "• C13 = C7 shell + D major (9·♯11·13)",
  "• C7♯9 = C + E♭ major (♯9·5·♭7);   C7♭9 = C + D♭° (♭9·3·5)",
].join("\n");
import {
  PitchClass, spellPc, noteAt, midiPitchClass, fp, fpKey,
  ChordDecomposition, CHORD_DECOMPOSITIONS, decompositionFor, upperRootInterval,
} from "../theory";
import { Timbres } from "../audio";

const DISPLAY_FRETS = 14;

export class DecomposeUI {
  private root: PitchClass = 0;
  private quality = CHORD_DECOMPOSITIONS[0].quality;
  private showGuide = false;
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
      btn("Guide", () => { this.showGuide = true; this.rerender(); }),
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
    ]));
    // Legend: circle colours + that the numbers are interval degrees.
    body.appendChild(el("div", { class: "et-row-gap", style: "margin-top:4px;font-size:12px;flex-wrap:wrap;gap:12px" }, [
      el("span", {}, [el("span", { style: `color:${Colors.rootTone}` }, ["●"]), " root (1)"]),
      el("span", {}, [el("span", { style: `color:${Colors.chordTone}` }, ["●"]), " shell"]),
      el("span", {}, [el("span", { style: `color:${Colors.scaleTone}` }, ["●"]), " upper triad"]),
      el("span", { class: "et-muted" }, ["numbers = interval degree"]),
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

    if (this.showGuide) {
      // Modal guide; tap the scrim or "Got it" to close.
      const close = () => { this.showGuide = false; this.rerender(); };
      const dialog = el("div", { class: "et-card", style: "max-width:520px;max-height:75vh;overflow:auto;margin:auto" }, [
        el("div", { style: "font-weight:700;font-size:16px;margin-bottom:6px" }, ["How chords decompose"]),
        el("div", { style: "white-space:pre-wrap;font-size:13px;line-height:1.5" }, [DECOMPOSE_GUIDE]),
        el("div", { style: "margin-top:10px;text-align:right" }, [btn("Got it", close, "btn primary")]),
      ]);
      dialog.addEventListener("click", (e) => e.stopPropagation());
      const scrim = el("div", {
        style: "position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;padding:16px;z-index:50",
      }, [dialog]);
      scrim.addEventListener("click", close);
      parent.appendChild(scrim);
    }
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
    // pitch class -> { degree label, isUpper }. Shell wins if a pc is in both.
    const pcInfo = new Map<number, { label: string; upper: boolean }>();
    for (const iv of dec.upper) pcInfo.set(((rootPc + iv) % 12 + 12) % 12, { label: decomposeDegree(iv), upper: true });
    for (const iv of dec.shell) pcInfo.set(((rootPc + iv) % 12 + 12) % 12, { label: decomposeDegree(iv), upper: false });
    const tuning = this.state.liveTuning;
    const out = new Map<string, FretMark>();
    for (let str = 0; str < tuning.openStrings.length; str++) {
      for (let f = 0; f <= DISPLAY_FRETS; f++) {
        const pos = fp(str, f);
        const pc = midiPitchClass(noteAt(tuning, pos).midi);
        const info = pcInfo.get(pc);
        if (!info) continue;
        out.set(fpKey(pos), { label: info.label, isRoot: pc === rootPc, kind: info.upper ? MarkKind.Scale : MarkKind.Chord });
      }
    }
    return out;
  }
}

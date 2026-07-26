// "Scales & Triads" CAGED trainer screen (web, guitar only). Practice / Challenge
// / Triads tabs sharing one fretboard. Mirror to Android later.

import { AppState } from "./appState";
import { CagedTrainerState, TrainerTab } from "./cagedTrainerState";
import { FretboardCanvas } from "./fretboardCanvas";
import { FretMark, MarkKind, intervalName } from "./marks";
import { el, btn, valueSlider, labelSm } from "./dom";
import {
  spellPc, noteAt, fpKey, fpFromKey, midiPitchClass,
  CagedBox, CagedMode, ScaleSubset, resolveBox, rootOf,
} from "../theory";

const NUM_FRETS = 22;
const STRING_NAMES = ["6 (low E)", "5 (A)", "4 (D)", "3 (G)", "2 (B)", "1 (high E)"];
const BOX_LABEL: Record<CagedBox, string> = {
  [CagedBox.POS1]: "Box 1", [CagedBox.POS2]: "Box 2", [CagedBox.POS3]: "Box 3",
  [CagedBox.POS4]: "Box 4", [CagedBox.POS5]: "Box 5",
};
const SUBSET_LABEL: Record<ScaleSubset, string> = {
  [ScaleSubset.Triad]: "triad", [ScaleSubset.FullScale]: "scale", [ScaleSubset.Pentatonic]: "pentatonic",
};

export class CagedTrainerUI {
  private fbCanvasEl: HTMLCanvasElement | null = null;
  private fb: FretboardCanvas | null = null;

  constructor(private state: AppState, private t: CagedTrainerState) {}

  render(parent: HTMLElement): void {
    const t = this.t;
    const screen = el("div", { class: "tool-screen" });

    // Title + tabs
    screen.appendChild(el("div", { class: "tool-topbar" }, [el("h2", {}, ["Scales & Triads"])]));
    const tabs = el("div", { class: "row", style: "gap:6px;margin-top:6px" });
    const tabBtn = (id: TrainerTab, label: string) =>
      btn(label, () => t.setTab(id), t.tab === id ? "btn primary" : "btn");
    tabs.appendChild(tabBtn("practice", "Practice"));
    tabs.appendChild(tabBtn("challenge", "Challenge"));
    tabs.appendChild(tabBtn("triads", "Triads"));
    screen.appendChild(tabs);

    // Key + tempo (shared)
    screen.appendChild(el("div", { class: "row", style: "margin-top:10px;align-items:center;gap:8px" }, [
      el("span", { class: "mono", style: "min-width:70px" }, [`Key: ${spellPc(t.key)}`]),
      btn("−", () => t.setKey((t.key + 11) as number), "btn"),
      btn("+", () => t.setKey((t.key + 1) as number), "btn"),
      btn("🎲 Random", () => t.randomKey(), "btn text"),
    ]));
    const bpmVS = valueSlider((v) => `Tempo: ${Math.round(v)} BPM`, 30, 200, t.bpm, (v) => t.setBpm(v));
    bpmVS.label.className = "label-sm";
    screen.appendChild(el("div", { style: "margin-top:8px" }, [bpmVS.label, bpmVS.input]));

    if (t.tab === "practice") this.renderPractice(screen);
    else if (t.tab === "challenge") this.renderChallenge(screen);
    else this.renderTriads(screen);

    // Shared fretboard
    if (!this.fbCanvasEl) {
      this.fbCanvasEl = el("canvas", { class: "fretboard" });
      this.fb = new FretboardCanvas(this.fbCanvasEl);
    }
    const wrap = el("div", { style: "height:240px;position:relative;margin:12px 0" });
    wrap.appendChild(this.fbCanvasEl);
    screen.appendChild(wrap);

    parent.appendChild(screen);
    this.paintFretboard();
  }

  // ---- Practice ----
  private renderPractice(screen: HTMLElement): void {
    const t = this.t;
    const st = t.step;
    screen.appendChild(el("div", { class: "row", style: "margin-top:10px;align-items:center;gap:8px" }, [
      btn(t.isPlaying ? "Stop ■" : "Play ▶", () => t.toggle(), "btn primary"),
      el("span", { style: "flex:1" }),
      labelSm("Step"),
      this.navBtn("◀", () => t.nudgeStep(-1), t.stepIndex <= 0),
      el("span", { class: "mono" }, [`${t.stepIndex + 1}/${t.stepCount}`]),
      this.navBtn("▶", () => t.nudgeStep(+1), t.stepIndex >= t.stepCount - 1),
    ]));
    screen.appendChild(el("div", { style: "margin-top:8px;font-weight:700" }, [
      `Position ${t.boxIndex + 1}/${t.regionCount} · ${t.step.mode === CagedMode.Major ? "Major" : "Minor"} ${SUBSET_LABEL[st.subset]}`,
    ]));
    screen.appendChild(el("div", { style: "margin-top:2px" }, [
      labelSm("Run box 1→5; each box: triad · scale · pentatonic for the leading mode, then the other (leading mode alternates per box). Roots are ringed."),
    ]));
    const demo = el("label", { class: "row", style: "gap:6px;margin-top:8px;align-items:center;cursor:pointer" }, [
      (() => { const c = el("input", { type: "checkbox" }) as HTMLInputElement; c.checked = t.audioDemo; c.addEventListener("change", () => t.toggleAudioDemo()); return c; })(),
      el("span", {}, ["Audio demo (play the notes) — off = metronome only"]),
    ]);
    screen.appendChild(demo);
  }

  // ---- Challenge ----
  private renderChallenge(screen: HTMLElement): void {
    const t = this.t;
    const c = t.challenge;
    screen.appendChild(el("div", { class: "row", style: "margin-top:10px;gap:8px" }, [
      btn("Next  (Space)", () => t.nextChallenge(), "btn primary"),
      (() => { const b = btn(t.reveal ? "Hide neck" : "Reveal on neck", () => t.toggleReveal(), "btn"); b.disabled = !c; return b; })(),
    ]));
    if (!c) {
      screen.appendChild(el("div", { style: "margin-top:12px" }, [labelSm("Press Next (or the space bar) for a prompt, play it yourself, then Reveal to check.")]));
      return;
    }
    const rootStr = this.primaryRootString(c.key, c.box, c.mode);
    const card = el("div", { class: "et-card", style: "margin-top:12px;padding:12px;line-height:1.7" }, [
      el("div", { style: "font-size:22px;font-weight:800" }, [`${spellPc(c.key)} ${c.mode === CagedMode.Major ? "major" : "minor"}`]),
      el("div", {}, [`${c.subset === ScaleSubset.Pentatonic ? "Pentatonic" : "Diatonic (full scale)"}`]),
      el("div", {}, [`${BOX_LABEL[c.box]} — root on string ${rootStr}`]),
    ]);
    screen.appendChild(card);
  }

  // ---- Triads ----
  private renderTriads(screen: HTMLElement): void {
    const t = this.t;
    const seq = t.triadSequence();
    const idx = t.activeTriad >= 0 ? t.activeTriad : 0;
    const cur = seq[idx];
    screen.appendChild(el("div", { class: "row", style: "margin-top:10px;align-items:center;gap:8px" }, [
      btn(t.isPlaying ? "Stop ■" : "Play ▶", () => t.toggle(), "btn primary"),
      el("span", { style: "flex:1" }),
      labelSm("Triad"),
      this.navBtn("◀", () => t.nudgeTriad(-1), false),
      el("span", { class: "mono" }, [`${idx + 1}/${seq.length}`]),
      this.navBtn("▶", () => t.nudgeTriad(+1), false),
    ]));
    const groupNames = ["strings 6-5-4", "strings 5-4-3", "strings 4-3-2", "strings 3-2-1"];
    const invNames = ["root position", "1st inversion", "2nd inversion"];
    const label = cur
      ? `${spellPc(t.key)}${cur.quality === "min" ? "m" : ""} · ${groupNames[Math.floor((idx % 12) / 3)]} · ${invNames[cur.shape.inversion]}`
      : "All major triads (4 string-groups × 3 inversions), then all minor.";
    screen.appendChild(el("div", { style: "margin-top:8px;font-weight:700" }, [label]));
    screen.appendChild(el("div", { style: "margin-top:2px" }, [
      labelSm("◀ ▶ to step through all 24 (12 major, then 12 minor); Play runs them one per beat."),
    ]));
  }

  // ---- Fretboard painting ----
  private paintFretboard(): void {
    const t = this.t;
    let marks = new Map<string, FretMark>();
    let selected: string | null = null;

    if (t.tab === "practice") {
      marks = this.notesToMarks(t.practiceNotes());
      selected = t.activeKey;
    } else if (t.tab === "challenge") {
      const c = t.challenge;
      if (c && t.reveal) marks = this.notesToMarks(resolveBox(c.key, c.box, c.mode, c.subset, t.tuning));
    } else {
      const seq = t.triadSequence();
      const cur = t.activeTriad >= 0 ? seq[t.activeTriad] : seq[0];
      if (cur) {
        const root = t.key;
        cur.shape.strings.forEach((s, k) => {
          const pos = { stringIndex: s, fret: cur.shape.frets[k] };
          const pc = midiPitchClass(noteAt(t.tuning, pos).midi);
          marks.set(fpKey(pos), { label: intervalName(((pc - root) % 12 + 12) % 12), isRoot: pc === root, kind: MarkKind.Chord });
        });
      }
    }

    this.fb!.setData({
      tuning: t.tuning,
      marks,
      selectedPosition: selected ? fpFromKey(selected) : null,
      leftHanded: this.state.leftHanded,
      numFrets: NUM_FRETS,
      playOnTouchDown: false,
      mutedStrings: new Set<number>(),
      onTap: (pos) => this.state.audio.playNote(noteAt(t.tuning, pos).midi, this.state.ringSustainMs),
    });
  }

  private notesToMarks(notes: { position: { stringIndex: number; fret: number }; interval: number; isRoot: boolean }[]): Map<string, FretMark> {
    const out = new Map<string, FretMark>();
    for (const n of notes) {
      out.set(fpKey(n.position), { label: intervalName(n.interval), isRoot: n.isRoot, kind: MarkKind.Scale });
    }
    return out;
  }

  /** Guitar string number (as in STRING_NAMES) of the lowest string carrying the
   *  active-mode root in this box — identifies the CAGED position. */
  private primaryRootString(key: number, box: CagedBox, mode: CagedMode): string {
    const notes = resolveBox(key, box, mode, ScaleSubset.FullScale, this.t.tuning);
    const root = rootOf(key, mode);
    let lowest = 6;
    for (const n of notes) {
      const pc = midiPitchClass(noteAt(this.t.tuning, n.position).midi);
      if (pc === root && n.position.stringIndex < lowest) lowest = n.position.stringIndex;
    }
    return STRING_NAMES[Math.min(lowest, 5)];
  }

  private navBtn(text: string, onClick: () => void, disabled: boolean): HTMLButtonElement {
    const b = btn(text, onClick, "btn");
    b.disabled = disabled;
    return b;
  }
}

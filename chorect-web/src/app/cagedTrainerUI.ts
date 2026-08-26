// "Guitar practice" screen (web, guitar only). A section dropdown picks Scales
// (Guided run / Challenge / Explore tabs over the CAGED boxes) or Triads (the 24
// inversions); both share one fretboard. Mirrored on Android
// (ScalesTriadsScreen.kt).

import { AppState } from "./appState";
import { CagedTrainerState, TrainerSection, TrainerTab, ExploreScale } from "./cagedTrainerState";
import { FretboardCanvas } from "./fretboardCanvas";
import { FretMark, MarkKind, intervalName } from "./marks";
import { el, btn, valueSlider, labelSm } from "./dom";
import {
  spellPc, noteAt, fpKey, fpFromKey, midiPitchClass,
  CagedBox, CAGED_BOXES, CAGED_SHAPE_LETTER, boxNumber, CagedMode, ScaleSubset,
  resolveBox, rootOf, patternCount,
} from "../theory";

const NUM_FRETS = 22;
const STRING_NAMES = ["6 (low E)", "5 (A)", "4 (D)", "3 (G)", "2 (B)", "1 (high E)"];
const SECTIONS: [TrainerSection, string][] = [["scales", "Scales"], ["triads", "Triads"]];
const SUBSET_LABEL: Record<ScaleSubset, string> = {
  [ScaleSubset.Triad]: "triad", [ScaleSubset.FullScale]: "scale", [ScaleSubset.Pentatonic]: "pentatonic",
};
/** How the Triads drill names each 3-string group, in the drill's own order. */
const TRIAD_GROUP_NAMES = ["strings 1-2-3", "strings 2-3-4", "strings 3-4-5", "strings 4-5-6"];
const INVERSION_NAMES = ["root position", "1st inversion", "2nd inversion"];

const boxLabel = (b: CagedBox) => `Box ${boxNumber(b)} (${CAGED_SHAPE_LETTER[b]} shape)`;

export class CagedTrainerUI {
  private fbCanvasEl: HTMLCanvasElement | null = null;
  private fb: FretboardCanvas | null = null;

  constructor(private state: AppState, private t: CagedTrainerState) {}

  render(parent: HTMLElement): void {
    const t = this.t;
    const screen = el("div", { class: "tool-screen" });

    // Title + section dropdown. The dropdown is the top-level split; each section
    // brings its own tab row (or none).
    const sectionSel = el("select", { class: "btn" }) as HTMLSelectElement;
    for (const [id, label] of SECTIONS) {
      const o = el("option", { value: id }, [label]) as HTMLOptionElement;
      if (t.section === id) o.selected = true;
      sectionSel.appendChild(o);
    }
    sectionSel.addEventListener("change", () => t.setSection(sectionSel.value as TrainerSection));
    screen.appendChild(el("div", { class: "tool-topbar" }, [el("h2", {}, ["Guitar practice"]), sectionSel]));

    if (t.section === "scales") {
      const tabs = el("div", { class: "row", style: "gap:6px;margin-top:6px" });
      const tabBtn = (id: TrainerTab, label: string) =>
        btn(label, () => t.setTab(id), t.tab === id ? "btn primary" : "btn");
      tabs.appendChild(tabBtn("practice", "Guided run"));
      tabs.appendChild(tabBtn("challenge", "Challenge"));
      tabs.appendChild(tabBtn("explore", "Explore"));
      screen.appendChild(tabs);
    }

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

    if (t.section === "triads") this.renderTriads(screen);
    else if (t.tab === "practice") this.renderPractice(screen);
    else if (t.tab === "challenge") this.renderChallenge(screen);
    else this.renderExplore(screen);

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
    const patName = patternCount(st.box, st.mode, st.subset) > 1 ? ` pattern ${st.pattern}` : "";
    const [wLo, wHi] = t.practiceWindow();
    screen.appendChild(el("div", { style: "margin-top:8px;font-weight:700" }, [
      `${boxLabel(st.box)} · ${st.mode === CagedMode.Major ? "Major" : "Minor"} ${SUBSET_LABEL[st.subset]}${patName}`,
    ]));
    screen.appendChild(el("div", { style: "margin-top:2px" }, [
      labelSm(`Frets ${Math.max(wLo, 0)}–${wHi} · step ${t.drillIndex + 1} of ${t.drillCount} in this box`),
    ]));
    const boxRow = el("div", { class: "row", style: "margin-top:8px;gap:6px" });
    for (const b of CAGED_BOXES) {
      boxRow.appendChild(btn(String(boxNumber(b)), () => t.jumpToBox(b), b === st.box ? "btn primary" : "btn"));
    }
    screen.appendChild(boxRow);
    screen.appendChild(el("div", { style: "margin-top:2px" }, [
      labelSm("Boxes 1→5; each box drills triad · scale · pentatonic in one quality then the other, the leading quality alternating per box. Roots are ringed."),
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
      el("div", {}, [`${boxLabel(c.box)} — root on string ${rootStr}`]),
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
    const label = cur
      ? `${spellPc(t.key)}${cur.quality === "min" ? "m" : ""} · ${TRIAD_GROUP_NAMES[Math.floor((idx % 12) / 3)]} · ${INVERSION_NAMES[cur.shape.inversion]}`
      : "All 3 inversions on strings 1-2-3, then 2-3-4, 3-4-5, 4-5-6 — major, then the same again minor.";
    screen.appendChild(el("div", { style: "margin-top:8px;font-weight:700" }, [label]));
    screen.appendChild(el("div", { style: "margin-top:2px" }, [
      labelSm(`◀ ▶ to step through all ${seq.length} (12 major, then 12 minor); Play runs them one per beat.`),
    ]));
  }

  // ---- Explore (scroll scale positions, like Fretboard mode) ----
  private renderExplore(screen: HTMLElement): void {
    const t = this.t;
    const scales: [ExploreScale, string][] = [["major", "Major"], ["minor", "Minor"], ["pentatonic", "Pentatonic"]];
    const row = el("div", { class: "row", style: "margin-top:10px;gap:6px" });
    for (const [id, lbl] of scales) {
      row.appendChild(btn(lbl, () => t.setExploreScale(id), t.exploreScale === id ? "btn primary" : "btn"));
    }
    screen.appendChild(row);
    const positions = t.explorePositionsList();
    screen.appendChild(el("div", { class: "row", style: "margin-top:10px;align-items:center;gap:8px" }, [
      labelSm("Position"),
      this.navBtn("◀", () => t.nudgeExplorePos(-1), false),
      el("span", { class: "mono" }, [`${positions.length ? t.explorePos + 1 : 0}/${positions.length}`]),
      this.navBtn("▶", () => t.nudgeExplorePos(+1), false),
    ]));
    screen.appendChild(el("div", { style: "margin-top:2px" }, [
      labelSm("Scroll the scale's positions across the neck (like Fretboard mode). Tap a note to hear it."),
    ]));
  }

  // ---- Fretboard painting ----
  private paintFretboard(): void {
    const t = this.t;
    let marks = new Map<string, FretMark>();
    let selected: string | null = null;

    if (t.section === "triads") {
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
    } else if (t.tab === "practice") {
      marks = this.notesToMarks(t.practiceNotes());
      selected = t.activeKey;
    } else if (t.tab === "challenge") {
      const c = t.challenge;
      if (c && t.reveal) marks = this.notesToMarks(resolveBox(c.key, c.box, c.mode, c.subset, t.tuning, NUM_FRETS, c.pattern));
    } else {
      // explore: the current position of the selected scale
      const pos = t.explorePositionsList()[t.explorePos];
      if (pos) {
        const root = t.key;
        for (const p of pos.positions) {
          const pc = midiPitchClass(noteAt(t.tuning, p).midi);
          marks.set(fpKey(p), { label: intervalName(((pc - root) % 12 + 12) % 12), isRoot: pc === root, kind: MarkKind.Scale });
        }
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

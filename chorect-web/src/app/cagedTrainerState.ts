// State + play loop for the guitar "Guitar practice" trainer (web).
// Two sections, chosen from the dropdown at the top of the screen:
//   Scales — the CAGED boxes, with tabs Practice (the guided 34-step run),
//            Challenge (random unscored prompts) and Explore (position browser).
//   Triads — the 24 close-voiced triad inversions, top string group first.
// Mirrored on Android (CagedTrainerState.kt). Standard tuning, guitar only.
// Spec: docs/superpowers/specs/2026-07-25-*.

import {
  PitchClass, fpKey, noteAt, standard,
  CagedBox, CAGED_BOXES, CagedMode, ScaleSubset, CagedNote,
  triadRun, TriadShape, resolveBox, boxWindow, DrillStep, PRACTICE_RUN, patternCount,
  explorePositions, EXPLORE_MAJOR, EXPLORE_MINOR, EXPLORE_PENTATONIC,
  ScalePosition,
} from "../theory";
import { WebAudioEngine } from "../audio";

export type TrainerSection = "scales" | "triads";
export type TrainerTab = "practice" | "challenge" | "explore";
export type ExploreScale = "major" | "minor" | "pentatonic";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface ChallengePrompt {
  key: PitchClass;
  box: CagedBox;
  mode: CagedMode;
  subset: ScaleSubset;
  pattern: number;
}

export class CagedTrainerState {
  section: TrainerSection = "scales";
  tab: TrainerTab = "practice";
  key: PitchClass = 7;          // G
  bpm = 80;
  audioDemo = true;             // Practice: play notes vs metronome-only
  reveal = false;               // Challenge: overlay the scale on the neck
  isPlaying = false;
  stepIndex = 0;                // Practice: 0..33, one per diagram on the sheet
  challenge: ChallengePrompt | null = null;
  /** fpKey of the note currently sounding in a Practice sweep (or null). */
  activeKey: string | null = null;
  /** Index 0..23 of the triad currently sounding, or -1. */
  activeTriad = -1;
  /** Explore tab: which scale + which position is shown. */
  exploreScale: ExploreScale = "major";
  explorePos = 0;

  readonly tuning = standard;
  private token = 0;

  constructor(private audio: WebAudioEngine, private onChange: () => void) {}

  private notify() { this.onChange(); }

  // ---- Practice derivations (the 5 CAGED boxes, shapes straight off the sheet) ----

  /** The guided run — 34 steps, one per diagram on the sheet. */
  get run(): DrillStep[] { return PRACTICE_RUN; }
  get stepCount(): number { return this.run.length; }
  get step(): DrillStep { return this.run[Math.min(Math.max(this.stepIndex, 0), this.stepCount - 1)]; }
  get box(): CagedBox { return this.step.box; }
  /** 0-based index of the current box, for the "Box 3 of 5" readout. */
  get boxIndex(): number { return CAGED_BOXES.indexOf(this.step.box); }
  /** Position of the current step within its own box, and that box's length. */
  get drillIndex(): number { return this.stepIndex - this.run.findIndex((s) => s.box === this.step.box); }
  get drillCount(): number { return this.run.filter((s) => s.box === this.step.box).length; }

  practiceNotes(): CagedNote[] {
    const st = this.step;
    return resolveBox(this.key, st.box, st.mode, st.subset, this.tuning, 22, st.pattern);
  }

  /** The fret span the current shape occupies, for the label under the neck. */
  practiceWindow(): [number, number] {
    const st = this.step;
    return boxWindow(this.key, st.box, this.tuning, st.mode, st.subset, st.pattern);
  }

  triadSequence(): { quality: "maj" | "min"; shape: TriadShape }[] {
    return triadRun(this.key, this.tuning);
  }

  // ---- Setters ----
  setSection(sec: TrainerSection) { if (sec === this.section) return; this.stop(); this.section = sec; this.notify(); }
  setTab(t: TrainerTab) { if (t === this.tab) return; this.stop(); this.tab = t; this.notify(); }
  setKey(pc: PitchClass) { this.key = (((pc % 12) + 12) % 12) as PitchClass; this.resetPlayback(); this.notify(); }
  randomKey() { this.setKey(Math.floor(Math.random() * 12) as PitchClass); }
  setBpm(v: number) { this.bpm = Math.min(Math.max(Math.round(v), 30), 240); this.notify(); }
  toggleAudioDemo() { this.audioDemo = !this.audioDemo; this.notify(); }
  toggleReveal() { this.reveal = !this.reveal; this.notify(); }
  setStep(i: number) { this.stepIndex = Math.min(Math.max(i, 0), this.stepCount - 1); this.resetPlayback(); this.notify(); }
  nudgeStep(d: number) { this.setStep(this.stepIndex + d); }
  /** Jump to the first step of a box (the box scroller). */
  jumpToBox(box: CagedBox) {
    const i = this.run.findIndex((s) => s.box === box);
    if (i >= 0) this.setStep(i);
  }

  nextChallenge() {
    const boxes = CAGED_BOXES;
    const box = boxes[Math.floor(Math.random() * boxes.length)];
    const mode = Math.random() < 0.5 ? CagedMode.Major : CagedMode.Minor;
    const subset = Math.random() < 0.5 ? ScaleSubset.FullScale : ScaleSubset.Pentatonic;
    this.challenge = {
      key: Math.floor(Math.random() * 12) as PitchClass,
      box, mode, subset,
      pattern: Math.floor(Math.random() * patternCount(box, mode, subset)) + 1,
    };
    this.reveal = false;
    this.notify();
  }

  private resetPlayback() { this.activeKey = null; this.activeTriad = -1; }

  /** Manually step to a triad (Triads tab scroller): stops any loop, selects it,
   *  and plucks it once. */
  setTriad(i: number) {
    if (this.isPlaying) this.stop();
    const seq = this.triadSequence();
    if (seq.length === 0) return;
    this.activeTriad = ((i % seq.length) + seq.length) % seq.length;
    const { shape } = seq[this.activeTriad];
    const midis = shape.strings.map((s, k) => noteAt(this.tuning, { stringIndex: s, fret: shape.frets[k] }).midi);
    this.audio.playChord(midis, 18, 800);
    this.notify();
  }
  nudgeTriad(d: number) { this.setTriad((this.activeTriad < 0 ? 0 : this.activeTriad) + d); }

  // ---- Explore (scroll positions like Fretboard mode) ----
  private exploreScaleObj() {
    return this.exploreScale === "major" ? EXPLORE_MAJOR : this.exploreScale === "minor" ? EXPLORE_MINOR : EXPLORE_PENTATONIC;
  }
  explorePositionsList(): ScalePosition[] {
    return explorePositions(this.key, this.exploreScaleObj(), this.tuning);
  }
  setExploreScale(s: ExploreScale) { this.exploreScale = s; this.explorePos = 0; this.notify(); }
  setExplorePos(i: number) {
    const n = this.explorePositionsList().length;
    this.explorePos = n ? (((i % n) + n) % n) : 0;
    this.notify();
  }
  nudgeExplorePos(d: number) { this.setExplorePos(this.explorePos + d); }

  toggle() { if (this.isPlaying) this.stop(); else this.play(); }

  stop() {
    this.isPlaying = false;
    this.token++;
    this.resetPlayback();
    this.audio.stop();
    this.notify();
  }

  play() {
    if (this.isPlaying) return;
    this.isPlaying = true;
    this.token++;
    const myToken = this.token;
    this.notify();
    void (async () => {
      while (this.isPlaying && myToken === this.token) {
        const beat = 60000 / Math.max(this.bpm, 20);
        if (this.section === "triads") {
          const seq = this.triadSequence();
          for (let i = 0; i < seq.length; i++) {
            if (!this.isPlaying || myToken !== this.token) return;
            this.activeTriad = i;
            const { shape } = seq[i];
            const midis = shape.strings.map((s, k) => noteAt(this.tuning, { stringIndex: s, fret: shape.frets[k] }).midi);
            this.audio.playChord(midis, 18, Math.max(beat * 0.9, 200));
            this.notify();
            await sleep(beat);
          }
          if (this.isPlaying && myToken === this.token) { this.stop(); return; }
        } else if (this.tab === "practice") {
          // Play the CURRENT drill (up + down once), then advance to the next step
          // — triad → scale → pentatonic of the leading mode, then the other mode,
          // across every box — and stop after the last.
          if (this.audioDemo) {
            const notes = this.practiceNotes().slice().sort((a, b) =>
              noteAt(this.tuning, a.position).midi - noteAt(this.tuning, b.position).midi);
            const sweep = [...notes, ...notes.slice(0, -1).reverse()];
            for (const n of sweep) {
              if (!this.isPlaying || myToken !== this.token) return;
              this.activeKey = fpKey(n.position);
              this.audio.chokeChords();   // a guided-run sweep is one note at a time: damp the previous pluck
              this.audio.playNote(noteAt(this.tuning, n.position).midi, Math.max(beat * 0.95, 150));
              this.notify();
              await sleep(beat);
            }
          } else {
            // metronome only: 1 bar of ticks, pattern shown statically
            for (let b = 0; b < 4; b++) {
              if (!this.isPlaying || myToken !== this.token) return;
              this.audio.playNote(b === 0 ? 96 : 91, 30);   // high short "click"
              this.notify();
              await sleep(beat);
            }
          }
          if (!this.isPlaying || myToken !== this.token) return;
          if (this.stepIndex >= this.stepCount - 1) { this.stop(); return; }
          this.stepIndex += 1;
          this.activeKey = null;
          this.notify();
        } else {
          return;
        }
      }
    })();
  }
}

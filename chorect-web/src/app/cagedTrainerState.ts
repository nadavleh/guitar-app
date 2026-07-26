// State + play loop for the guitar "Scales & Triads" CAGED trainer (web).
// Three tabs: Practice (guided box/drill run), Challenge (random unscored
// prompts), Triads (24 triad inversions). Standard tuning, guitar only.
// Android version to follow. Spec: docs/superpowers/specs/2026-07-25-*.

import {
  PitchClass, fpKey, noteAt, standard,
  CagedBox, CAGED_BOXES, CagedMode, ScaleSubset, CagedNote,
  triadInversions, TriadShape, practiceRegions, notesInWindow,
} from "../theory";
import { WebAudioEngine } from "../audio";

export type TrainerTab = "practice" | "challenge" | "triads";

export interface DrillStep { mode: CagedMode; subset: ScaleSubset; }

const SUBSET_ORDER: ScaleSubset[] = [ScaleSubset.Triad, ScaleSubset.FullScale, ScaleSubset.Pentatonic];
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface ChallengePrompt {
  key: PitchClass;
  box: CagedBox;
  mode: CagedMode;
  subset: ScaleSubset;
}

export class CagedTrainerState {
  tab: TrainerTab = "practice";
  key: PitchClass = 7;          // G
  bpm = 80;
  audioDemo = true;             // Practice: play notes vs metronome-only
  reveal = false;               // Challenge: overlay the scale on the neck
  isPlaying = false;
  stepIndex = 0;                // Practice: 0..29 (5 boxes × 6 drill steps)
  challenge: ChallengePrompt | null = null;
  /** fpKey of the note currently sounding in a Practice sweep (or null). */
  activeKey: string | null = null;
  /** Index 0..23 of the triad currently sounding, or -1. */
  activeTriad = -1;

  readonly tuning = standard;
  private token = 0;

  constructor(private audio: WebAudioEngine, private onChange: () => void) {}

  private notify() { this.onChange(); }

  // ---- Practice derivations (over the 7 major-scale POSITIONS, like Fretboard mode) ----
  /** Fret windows of the key's positions (7 for a diatonic key), low→high. */
  regions(): [number, number][] {
    const r = practiceRegions(this.key, this.tuning);
    return r.length ? r : [[0, 4]];
  }
  get regionCount(): number { return this.regions().length; }
  get stepCount(): number { return this.regionCount * 6; }
  get boxIndex(): number { return Math.min(Math.floor(this.stepIndex / 6), this.regionCount - 1); }
  get drillIndex(): number { return this.stepIndex % 6; }

  /** The 6 drill steps for the current position: [triad,scale,pent] of the
   *  leading mode then the other; the leading mode alternates each position. */
  drillSteps(boxIndex: number): DrillStep[] {
    const lead = boxIndex % 2 === 0 ? CagedMode.Major : CagedMode.Minor;
    const other = lead === CagedMode.Major ? CagedMode.Minor : CagedMode.Major;
    return [
      ...SUBSET_ORDER.map((s) => ({ mode: lead, subset: s })),
      ...SUBSET_ORDER.map((s) => ({ mode: other, subset: s })),
    ];
  }

  get step(): DrillStep { return this.drillSteps(this.boxIndex)[this.drillIndex]; }

  practiceNotes(): CagedNote[] {
    const st = this.step;
    const [lo, hi] = this.regions()[this.boxIndex] ?? [0, 4];
    return notesInWindow(this.key, lo, hi, st.mode, st.subset, this.tuning);
  }

  triadSequence(): { quality: "maj" | "min"; shape: TriadShape }[] {
    return [
      ...triadInversions(this.key, "maj", this.tuning).map((shape) => ({ quality: "maj" as const, shape })),
      ...triadInversions(this.key, "min", this.tuning).map((shape) => ({ quality: "min" as const, shape })),
    ];
  }

  // ---- Setters ----
  setTab(t: TrainerTab) { if (t === this.tab) return; this.stop(); this.tab = t; this.notify(); }
  setKey(pc: PitchClass) { this.key = (((pc % 12) + 12) % 12) as PitchClass; this.resetPlayback(); this.notify(); }
  randomKey() { this.setKey(Math.floor(Math.random() * 12) as PitchClass); }
  setBpm(v: number) { this.bpm = Math.min(Math.max(Math.round(v), 30), 240); this.notify(); }
  toggleAudioDemo() { this.audioDemo = !this.audioDemo; this.notify(); }
  toggleReveal() { this.reveal = !this.reveal; this.notify(); }
  setStep(i: number) { this.stepIndex = Math.min(Math.max(i, 0), this.stepCount - 1); this.resetPlayback(); this.notify(); }
  nudgeStep(d: number) { this.setStep(this.stepIndex + d); }

  nextChallenge() {
    const boxes = CAGED_BOXES;
    this.challenge = {
      key: Math.floor(Math.random() * 12) as PitchClass,
      box: boxes[Math.floor(Math.random() * boxes.length)],
      mode: Math.random() < 0.5 ? CagedMode.Major : CagedMode.Minor,
      subset: Math.random() < 0.5 ? ScaleSubset.FullScale : ScaleSubset.Pentatonic,
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
        if (this.tab === "triads") {
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
        } else if (this.tab === "practice") {
          // Play the CURRENT drill (up + down once), then advance to the next drill
          // step — arp → scale → pentatonic across every box — and stop after the last.
          if (this.audioDemo) {
            const notes = this.practiceNotes().slice().sort((a, b) =>
              noteAt(this.tuning, a.position).midi - noteAt(this.tuning, b.position).midi);
            const sweep = [...notes, ...notes.slice(0, -1).reverse()];
            for (const n of sweep) {
              if (!this.isPlaying || myToken !== this.token) return;
              this.activeKey = fpKey(n.position);
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

// Ear Training screen, ported from app/.../EarTrainingScreen.kt. A self-contained
// view class owning its own (optional) fretboard panel canvas, re-rendered on each
// state change. Native <select> replaces the Compose dropdowns.

import { AppState, ChallengeScore, CHALLENGE_SCORE_ORDER } from "./appState";
import { EarTrainingState, EarSubMode, EarMode } from "./earTrainingState";
import { FretboardCanvas } from "./fretboardCanvas";
import { shapeMarks } from "./marks";
import { Colors, withAlpha } from "./theme";
import { el, btn, slider, switchRow, labelSm } from "./dom";
import {
  spellPc, noteAt, TrainingMode, ChordTypeLevel, ChordTypeLevelName,
  namedRomanLine, inversionName, n2cAnswerLabel, n2cChordSymbol, n2cTestNoteName,
} from "../theory";

const DISPLAY_FRETS = 14;

// card backgrounds (Compose container colors → tints of our palette)
const BG_HIDDEN = Colors.surfaceElev;
const BG_REVEAL = withAlpha(Colors.scaleTone, 0.20);
const BG_PRIMARY = withAlpha(Colors.primary, 0.20);
const BG_TEACH = withAlpha(Colors.chordTone, 0.15);

function select(options: { value: string; label: string }[], value: string, onChange: (v: string) => void): HTMLSelectElement {
  const s = el("select", { class: "et-select" }) as HTMLSelectElement;
  for (const o of options) {
    const opt = el("option", { value: o.value }, [o.label]);
    if (o.value === value) opt.selected = true;
    s.appendChild(opt);
  }
  s.addEventListener("change", () => onChange(s.value));
  return s;
}

function chip(label: string, selected: boolean, onClick: () => void, enabled = true): HTMLButtonElement {
  const b = el("button", { class: selected ? "chip selected" : "chip" }, [label]);
  if (!enabled) b.disabled = true;
  else b.addEventListener("click", onClick);
  return b;
}

function chipsRow(children: HTMLElement[]): HTMLElement {
  return el("div", { class: "chip-row" }, children);
}

function formatDuration(ms: number): string {
  const total = Math.max(Math.floor(ms / 1000), 0);
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}
function formatScoreDate(ms: number): string {
  const d = new Date(ms);
  return d.toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export class EarTrainingUI {
  private fbCanvasEl: HTMLCanvasElement | null = null;
  private fb: FretboardCanvas | null = null;
  /** Which bar's degree-keyboard popup is open (null = none). */
  private keyboardBar: number | null = null;
  /** Pending picks inside the open keyboard popup (extended/mix mode needs OK). */
  private kbPickedDeg: number | null = null;
  private kbPickedRoman: string | null = null;
  private kbPickedExt: string | null = null;
  /** Physical-keyboard handler active while the degree popup is open (1..7 pick a
   *  degree, Enter commits in extension mode, Esc closes). */
  private kbKeyHandler: ((e: KeyboardEvent) => void) | null = null;

  constructor(private ear: EarTrainingState, private state: AppState, private onBack: () => void, private onToLooper: (symbols: string[]) => void) {}

  render(container: HTMLElement): void {
    const ear = this.ear;
    const screen = el("div", { class: "tool-screen" });

    // header
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("div", { class: "tool-title" }, ["EAR TRAINING"]),
      btn("Back", () => { ear.release(); this.onBack(); }),
    ]));

    // sub-mode + mode selectors
    screen.appendChild(el("div", { class: "row", style: "gap:8px;margin-top:8px" }, [
      select(
        [
          { value: EarSubMode.Progression, label: "Progressions" },
          { value: EarSubMode.Note2Chord, label: "Note→Chord" },
          { value: EarSubMode.Flavor, label: "Flavor" },
          { value: EarSubMode.Inversions, label: "Inversions" },
          { value: EarSubMode.AugDim, label: "Aug / Dim" },
        ],
        ear.progSubMode,
        (v) => ear.switchTab(v as EarSubMode),
      ),
      select(
        [{ value: EarMode.Practice, label: "Practice" }, { value: EarMode.Challenge, label: "Challenge" }],
        ear.earMode,
        (v) => ear.setEarMode(v as EarMode),
      ),
    ]));

    if (ear.progSubMode === EarSubMode.Progression) {
      screen.appendChild(switchRow(
        "Advanced (non-diatonic) progressions",
        "Borrowed chords, secondary dominants & jazz turnarounds, each with a note.",
        ear.advancedMode, (v) => ear.setAdvancedMode(v),
      ));
    }

    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);

    switch (ear.progSubMode) {
      case EarSubMode.Progression:
        if (ear.advancedMode) ear.earMode === EarMode.Challenge ? this.advancedChallenge(body) : this.advancedView(body);
        else ear.earMode === EarMode.Challenge ? this.progressionChallenge(body) : this.progressionView(body);
        break;
      case EarSubMode.Note2Chord:
        ear.earMode === EarMode.Challenge ? this.n2cChallenge(body) : this.n2cView(body);
        break;
      case EarSubMode.Flavor:
        ear.earMode === EarMode.Challenge ? this.flavorChallenge(body) : this.flavorView(body);
        break;
      case EarSubMode.Inversions:
        ear.earMode === EarMode.Challenge ? this.invChallenge(body) : this.invView(body);
        break;
      case EarSubMode.AugDim:
        ear.earMode === EarMode.Challenge ? this.augDimChallenge(body) : this.augDimView(body);
        break;
    }

    container.appendChild(screen);
  }

  // ---------- shared widgets ----------

  private revealCard(label: string, hidden: boolean, content: string, onToggle: () => void, big = true): HTMLElement {
    const c = el("div", { class: "et-card et-reveal", style: `background:${hidden ? BG_HIDDEN : BG_REVEAL}` }, [
      el("div", { class: "ans-label" }, [label]),
      el("div", { style: `font-weight:600;${hidden ? "color:var(--text-secondary);font-size:14px" : `font-size:${big ? 22 : 15}px`}` }, [hidden ? "tap to reveal" : content]),
    ]);
    c.addEventListener("click", onToggle);
    return c;
  }

  private rerender(): void { (this.ear as unknown as { deps: { onChange: () => void } }).deps.onChange(); }

  private progressionSettings(parent: HTMLElement): void {
    const ear = this.ear;
    parent.appendChild(labelSm("Key & modes"));
    parent.appendChild(el("div", { class: "et-row-gap" }, [
      this.keySelectInline(),
      chip("Major", ear.includeMajor, () => { ear.includeMajor = !ear.includeMajor; this.rerender(); }),
      chip("Minor", ear.includeMinor, () => { ear.includeMinor = !ear.includeMinor; this.rerender(); }),
    ]));
    parent.appendChild(labelSm("Chord type"));
    parent.appendChild(el("div", { class: "seg full" }, [ChordTypeLevel.Triads, ChordTypeLevel.Sevenths, ChordTypeLevel.Extended].map((lvl) => {
      const sel = ear.chordTypeLevel === lvl && !ear.earMixAll;
      const b = el("button", { class: sel ? "selected" : "" }, [ChordTypeLevelName[lvl]]);
      b.addEventListener("click", () => { ear.chordTypeLevel = lvl; ear.earMixAll = false; ear.reresolveCurrent(); });
      return b;
    })));
    parent.appendChild(labelSm("Voicing"));
    parent.appendChild(chipsRow([
      chip("Standard", !ear.earShellVoicing && !ear.earMixAll, () => { ear.earShellVoicing = false; ear.earMixAll = false; this.rerender(); }),
      chip("Shell", ear.earShellVoicing && !ear.earMixAll, () => { ear.earShellVoicing = true; ear.earMixAll = false; this.rerender(); }),
      chip("Mix all", ear.earMixAll, () => { ear.earMixAll = !ear.earMixAll; ear.reresolveCurrent(); }),
    ]));
  }

  private keySelectInline(): HTMLElement {
    const ear = this.ear;
    const opts = [{ value: "random", label: "Random key" }];
    for (let i = 0; i < 12; i++) opts.push({ value: String(i), label: "Fixed: " + spellPc(i) });
    const wrap = el("div", { style: "min-width:130px" }, [
      select(opts, ear.fixedKey == null ? "random" : String(ear.fixedKey), (v) => { ear.fixedKey = v === "random" ? null : parseInt(v, 10); this.rerender(); }),
    ]);
    return wrap;
  }

  private tempoStrumSliders(parent: HTMLElement): void {
    const ear = this.ear, s = this.state;
    parent.appendChild(el("div", { style: "margin-top:8px" }, [`Tempo: ${ear.progBpm} BPM`]));
    parent.appendChild(slider(40, 200, ear.progBpm, (v) => { ear.progBpm = Math.round(v); this.rerender(); }));
    parent.appendChild(el("div", { class: "et-muted" }, [s.strumMs === 0 ? "Strum: struck at once" : `Strum: ${s.strumMs} ms`]));
    parent.appendChild(slider(0, 150, s.strumMs, (v) => s.setStrumMs(v)));
  }

  private fretboardPanel(parent: HTMLElement): void {
    const ear = this.ear, s = this.state;
    parent.appendChild(switchRow("Show chord on fretboard", null, ear.showFretboard, (v) => { ear.showFretboard = v; this.rerender(); }));
    if (!ear.showFretboard) return;
    if (!this.fbCanvasEl) {
      this.fbCanvasEl = el("canvas", { class: "fretboard" });
      this.fb = new FretboardCanvas(this.fbCanvasEl);
    }
    const wrap = el("div", { style: "height:220px;position:relative;margin:6px 0" });
    wrap.appendChild(this.fbCanvasEl);
    parent.appendChild(wrap);
    const shape = ear.currentPlayingShape ?? ear.lastShownShape;
    const marks = shape ? shapeMarks(shape, s.labelMode) : new Map();
    this.fb!.setData({
      tuning: s.liveTuning, marks, selectedPosition: null, leftHanded: s.leftHanded,
      numFrets: DISPLAY_FRETS, playOnTouchDown: false, mutedStrings: new Set<number>(),
      onTap: (pos) => s.audio.playNote(noteAt(s.liveTuning, pos).midi, s.ringSustainMs),
    });
  }

  private challengeHeader(parent: HTMLElement, label: string, score: string, onRestart: () => void, onQuit: () => void): void {
    parent.appendChild(el("div", { class: "row" }, [
      el("div", { style: "flex:1;font-weight:600" }, [label]),
      el("div", { style: `color:${Colors.primary};font-weight:600` }, [score]),
      btn("Restart", onRestart, "btn text"),
      btn("Quit", onQuit, "btn text"),
    ]));
  }

  private simpleDone(parent: HTMLElement, score: number, total: number, onRestart: () => void, onExit: () => void): void {
    parent.appendChild(el("div", { class: "et-card", style: `background:${BG_PRIMARY};text-align:center;padding:20px` }, [
      el("div", { style: "font-weight:600" }, ["Challenge complete!"]),
      el("div", { class: "et-score-big", style: "margin:8px 0" }, [`${score} / ${total}`]),
      el("div", { class: "row", style: "justify-content:center;gap:8px" }, [
        btn("Restart", onRestart, "btn primary"), btn("Exit", onExit),
      ]),
    ]));
  }

  // ---------- Progression (practice) ----------

  /** ±1-semitone transpose clicker for the Progressions practice views. */
  private transposeRow(): HTMLElement {
    const ear = this.ear;
    return el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      el("span", { class: "ans-label" }, ["Transpose"]),
      btn("−", () => ear.transposeProgression(-1)),
      el("span", { class: "et-muted" }, ["semitone"]),
      btn("+", () => ear.transposeProgression(1)),
    ]);
  }

  private progressionView(parent: HTMLElement): void {
    const ear = this.ear, s = this.state;
    this.progressionSettings(parent);
    this.tempoStrumSliders(parent);
    parent.appendChild(el("div", { class: "v-gap-12" }));

    if (!ear.hasGenerated) {
      parent.appendChild(btn("Generate progression ▶", () => ear.nextProgression(), "btn primary"));
      return;
    }

    parent.appendChild(el("div", { class: "et-row-gap" }, [
      ear.isLooping ? btn("Stop ⏹", () => ear.stopLoop(), "btn primary") : btn("Play ▶", () => ear.startLoop(), "btn primary"),
      btn("Next →", () => ear.nextProgression()),
      btn(`Hear ${ear.progCadenceLabel()}`, () => ear.playProgKeyCadence()),
      btn("→ Looper", () => this.onToLooper(ear.progResolved.map((rc) => rc.symbol))),
    ]));

    parent.appendChild(this.transposeRow());

    parent.appendChild(el("div", { class: "v-gap-12" }));
    parent.appendChild(this.revealCard("Key & Mode", !ear.keyRevealed,
      spellPc(ear.progKey) + "  " + (ear.progMode === TrainingMode.Major ? "Major" : "Minor"),
      () => ear.toggleKeyModeReveal(), false));

    parent.appendChild(el("div", { class: "v-gap-8" }));
    parent.appendChild(this.chordSlots());
    parent.appendChild(el("div", { class: "v-gap-8" }));
    this.fretboardPanel(parent);
    void s;
  }

  private chordSlots(): HTMLElement {
    const ear = this.ear;
    const row = el("div", { class: "et-slot-row" });
    for (let i = 0; i < 4; i++) {
      const resolved = ear.progResolved[i];
      const isCurrent = ear.isLooping && ear.currentBar === i;
      const hidden = !ear.progBarRevealed.has(i);
      const bg = isCurrent ? BG_PRIMARY : hidden ? BG_HIDDEN : BG_REVEAL;
      const slot = el("div", { class: "et-slot", style: `background:${bg}` }, [
        el("div", { class: "ans-label" }, [`Bar ${i + 1}`]),
        el("div", { style: `margin:6px 0;font-weight:600;${hidden ? "font-size:13px;color:var(--text-secondary)" : "font-size:24px"}` }, [hidden ? "tap" : (resolved?.romanLabel ?? "—")]),
        btn("▶", () => ear.playBarOnce(i)),
      ]);
      slot.querySelector(".ans-label")!.addEventListener("click", () => ear.toggleBarReveal(i));
      (slot.childNodes[1] as HTMLElement).addEventListener("click", () => ear.toggleBarReveal(i));
      row.appendChild(slot);
    }
    return row;
  }

  // ---------- Progression Challenge ----------

  private progressionChallenge(parent: HTMLElement): void {
    const ear = this.ear, s = this.state;
    if (!ear.challengeActive) {
      parent.appendChild(el("div", { class: "et-muted" }, ["A challenge is 15 progressions in a row. Listen, then tap the correct Roman numeral for each bar (and its extension when shown). Each question auto-scores; your total appears at the end."]));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      this.progressionSettings(parent);
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Start challenge ▶", () => ear.startChallenge(), "btn primary"));
      return;
    }
    if (ear.challengeIndex >= ear.challengeTotal) {
      this.challengeDone(parent);
      return;
    }
    this.challengeHeader(parent, `Question ${ear.challengeIndex + 1} / ${ear.challengeTotal}`, `Score: ${ear.challengeBarScore()} bars`,
      () => ear.startChallenge(), () => ear.exitChallenge());

    // Question navigation pinned up top: an accidental "Next" can be undone
    // (← Prev restores that question's saved answers) without scrolling down.
    const prevBtn = btn("← Prev", () => ear.previousChallengeQuestion(), "btn");
    prevBtn.style.flex = "1";
    if (!ear.canGoPrevChallenge) prevBtn.disabled = true;
    const nextTopBtn = btn(ear.challengeIndex === ear.challengeTotal - 1 ? "See score →" : "Next →", () => ear.advanceChallenge(), "btn primary");
    nextTopBtn.style.flex = "1";
    parent.appendChild(el("div", { class: "row", style: "gap:8px" }, [prevBtn, nextTopBtn]));

    parent.appendChild(el("div", { class: "et-row-gap" }, [
      ear.isLooping ? btn("Stop ⏹", () => ear.stopLoop(), "btn primary") : btn("Play ▶", () => ear.startLoop(), "btn primary"),
      btn(`Hear ${ear.progCadenceLabel()}`, () => ear.playProgKeyCadence()),
      btn("Re-roll", () => ear.rerollChallengeQuestion()),
    ]));
    parent.appendChild(el("div", { style: "margin-top:8px" }, [`BPM: ${ear.progBpm}`]));
    parent.appendChild(slider(40, 200, ear.progBpm, (v) => { ear.progBpm = Math.round(v); this.rerender(); }));
    parent.appendChild(this.revealCard("Key & Mode (hint)", !ear.keyRevealed,
      spellPc(ear.progKey) + "  " + (ear.progMode === TrainingMode.Major ? "Major" : "Minor"),
      () => ear.toggleKeyModeReveal(), false));

    parent.appendChild(labelSm("Hear the degrees  (reference — plays in the hidden key)"));
    parent.appendChild(el("div", { class: "et-row-gap" }, ear.challengeReferenceLabels().map(([deg, label]) => btn(`▶ ${label}`, () => ear.auditionProgDegree(deg)))));

    // Degree-keyboard answering: each bar is a square the user fills by tapping it
    // and choosing from a degree "keyboard" (Major/Minor shift, plus an extensions
    // row when the level uses them). The per-bar ▶ and the reference palette above
    // are the only things that sound; selecting is silent.
    parent.appendChild(labelSm("Fill each bar  (tap a square to choose its chord)"));
    const sqRow = el("div", { class: "et-slot-row" });
    for (let i = 0; i < 4; i++) {
      sqRow.appendChild(this.barSquare(i));
    }
    parent.appendChild(sqRow);
    if (this.keyboardBar !== null) parent.appendChild(this.degreeKeyboardDialog(this.keyboardBar));

    parent.appendChild(el("div", { class: "v-gap-8" }));
    parent.appendChild(btn(ear.challengeIndex === ear.challengeTotal - 1 ? "See score →" : "Next question →", () => ear.advanceChallenge(), "btn primary"));
    parent.appendChild(el("div", { class: "et-muted", style: "margin-top:2px" }, ["Unanswered bars count as correct."]));
    parent.appendChild(el("div", { class: "v-gap-12" }));
    this.fretboardPanel(parent);
    void s;
  }

  /** One bar's answer square: a tappable tile showing the chosen label (or "?"),
   *  a ▶ to hear the bar, and a ✔/✘+answer once scored. */
  private barSquare(i: number): HTMLElement {
    const ear = this.ear;
    const verdict = ear.challengeBarCorrect(i);
    const label = ear.challengeGuessLabel[i];
    const border = verdict === true ? Colors.primary : verdict === false ? Colors.rootTone : Colors.divider;
    const box = el("div", {
      class: "et-barsq",
      style: `border-color:${border};background:${label == null ? BG_HIDDEN : Colors.surfaceElev}`,
    }, [label ?? "?"]);
    box.addEventListener("click", () => { this.keyboardBar = i; this.resetKbPicks(); this.rerender(); });
    const col = el("div", { class: "et-slot" }, [
      el("div", { class: "ans-label" }, [`Bar ${i + 1}`]),
      box,
      btn("▶", () => ear.playBarOnce(i)),
    ]);
    if (verdict !== null) {
      const answer = ear.progResolved[i]?.romanLabel ?? "";
      col.appendChild(el("div", {
        style: `font-size:11px;font-weight:600;margin-top:2px;color:${verdict ? Colors.primary : Colors.rootTone}`,
      }, [verdict ? "✔" : `✘ ${answer}`]));
    }
    return col;
  }

  private resetKbPicks(): void {
    this.kbPickedDeg = null; this.kbPickedRoman = null; this.kbPickedExt = null;
  }

  /** Item #2: let the physical number keys 1..7 pick a degree while the popup is
   *  open (Enter commits in extension mode; Esc closes). Attached on open only. */
  private attachKbKeys(): void {
    if (this.kbKeyHandler) return;
    const handler = (e: KeyboardEvent) => {
      const bar = this.keyboardBar;
      if (bar === null) return;
      const ear = this.ear;
      const needsExt = ear.challengeNeedsExt && !ear.challengeCombinedMode;
      const finish = () => { this.keyboardBar = null; this.resetKbPicks(); this.detachKbKeys(); this.rerender(); };
      if (e.key >= "1" && e.key <= "7") {
        const idx = parseInt(e.key, 10) - 1;
        const keys = ear.keyboardKeys();
        if (idx >= keys.length) return;
        e.preventDefault();
        const [majDeg, roman] = keys[idx];
        this.kbPickedDeg = majDeg; this.kbPickedRoman = roman;
        if (!needsExt) { ear.guessChallengeKeyboard(bar, majDeg, roman, null); finish(); }
        else this.rerender();
      } else if (e.key === "Escape") {
        e.preventDefault(); finish();
      } else if (e.key === "Enter" && needsExt && this.kbPickedDeg != null) {
        e.preventDefault();
        ear.guessChallengeKeyboard(bar, this.kbPickedDeg, this.kbPickedRoman ?? String(this.kbPickedDeg), this.kbPickedExt);
        finish();
      }
    };
    this.kbKeyHandler = handler;
    document.addEventListener("keydown", handler);
  }

  private detachKbKeys(): void {
    if (this.kbKeyHandler) {
      document.removeEventListener("keydown", this.kbKeyHandler);
      this.kbKeyHandler = null;
    }
  }

  /** The answer "keyboard" popup for one bar: a Major/Minor shift, a row of 7 degree
   *  keys, and (when the level uses them, and not fixed-7ths mode) an extensions row.
   *  Triads / fixed-7ths commit on the degree tap; extended/mix wait for OK. */
  private degreeKeyboardDialog(bar: number): HTMLElement {
    const ear = this.ear;
    const needsExt = ear.challengeNeedsExt && !ear.challengeCombinedMode;
    const extOptions = ear.challengeExtOptions();

    // Physical 1..7 keys select degrees while this popup is open.
    this.attachKbKeys();

    const commit = () => {
      if (this.kbPickedDeg == null) return;
      ear.guessChallengeKeyboard(bar, this.kbPickedDeg, this.kbPickedRoman ?? String(this.kbPickedDeg), needsExt ? this.kbPickedExt : null);
      this.keyboardBar = null;
      this.resetKbPicks();
      this.detachKbKeys();
      this.rerender();
    };
    const close = () => { this.keyboardBar = null; this.resetKbPicks(); this.detachKbKeys(); this.rerender(); };

    const body = el("div", {});
    // Major / Minor shift
    body.appendChild(el("div", { class: "et-row-gap" }, [
      el("span", { class: "ans-label" }, ["Numerals"]),
      chip("Major", !ear.keyboardMinor, () => { if (ear.keyboardMinor) ear.toggleKeyboardShift(); }),
      chip("⇧ Minor", ear.keyboardMinor, () => { if (!ear.keyboardMinor) ear.toggleKeyboardShift(); }),
    ]));
    body.appendChild(labelSm("Degree"));
    body.appendChild(chipsRow(ear.keyboardKeys().map(([majDeg, roman]) =>
      chip(roman, this.kbPickedDeg === majDeg, () => {
        this.kbPickedDeg = majDeg; this.kbPickedRoman = roman;
        if (!needsExt) commit(); else this.rerender();
      }))));
    if (needsExt && extOptions.length) {
      body.appendChild(labelSm("Extension"));
      body.appendChild(chipsRow(extOptions.map((ext) =>
        chip(ext === "" ? "triad" : ext, this.kbPickedExt === ext, () => { this.kbPickedExt = ext; this.rerender(); }))));
    }

    const footer = el("div", { class: "row", style: "gap:8px;justify-content:flex-end;margin-top:12px" });
    footer.appendChild(btn("Clear", () => { ear.clearChallengeBar(bar); close(); }));
    if (needsExt) {
      const ok = btn("OK", commit, "btn primary");
      if (this.kbPickedDeg == null) ok.disabled = true;
      footer.appendChild(ok);
    } else {
      footer.appendChild(btn("Close", close));
    }

    const card = el("div", { class: "et-kb-card" }, [
      el("div", { style: "font-weight:600;margin-bottom:8px" }, [`Bar ${bar + 1}`]),
      body, footer,
    ]);
    const backdrop = el("div", { class: "et-kb-backdrop" }, [card]);
    backdrop.addEventListener("click", (e) => { if (e.target === backdrop) close(); });
    return backdrop;
  }

  private challengeDone(parent: HTMLElement): void {
    const ear = this.ear, s = this.state;
    const score = ear.challengeBarScore(), total = ear.challengeBarTotal(), dur = ear.challengeDurationMs;
    const card = el("div", { class: "et-card", style: `background:${BG_PRIMARY};padding:20px;text-align:center` });
    card.appendChild(el("div", { style: "font-weight:600" }, ["Challenge complete!"]));
    card.appendChild(el("div", { class: "et-score-big", style: "margin:6px 0" }, [`${score} / ${total}`]));
    card.appendChild(el("div", { class: "et-muted" }, [`bars correct  ·  ${formatDuration(dur)}`]));
    // per-question dots
    const dots = el("div", { class: "chip-row", style: "justify-content:center;margin:12px 0" });
    ear.challengeAnswers.forEach((a, i) => {
      const color = a === true ? Colors.primary : a === false ? Colors.rootTone : Colors.divider;
      dots.appendChild(el("div", { class: "et-dot", style: `background:${color}` }, [String(i + 1)]));
    });
    card.appendChild(dots);

    // high-score table (merge this run in case persistence hasn't flushed)
    const merged: ChallengeScore[] = s.challengeScores.some((h) => h.score === score && h.durationMs === dur)
      ? s.challengeScores.slice()
      : [...s.challengeScores, { score, total, durationMs: dur, dateMillis: Date.now() }].sort(CHALLENGE_SCORE_ORDER);
    if (merged.length) {
      card.appendChild(el("div", { class: "divider-line" }));
      card.appendChild(el("div", { style: "font-weight:600" }, ["High scores"]));
      let highlighted = false;
      merged.slice(0, 5).forEach((hs, rank) => {
        const isThis = !highlighted && hs.score === score && hs.durationMs === dur;
        if (isThis) highlighted = true;
        card.appendChild(el("div", { class: "et-hs-row", style: isThis ? "font-weight:700" : "" }, [
          el("span", { style: "width:24px" }, [`${rank + 1}.`]),
          el("span", { style: "width:56px" }, [`${hs.score}/${hs.total}`]),
          el("span", { style: "width:48px" }, [formatDuration(hs.durationMs)]),
          el("span", { style: "flex:1;text-align:left" }, [formatScoreDate(hs.dateMillis) + (isThis ? "  ← you" : "")]),
        ]));
      });
    }
    card.appendChild(el("div", { class: "row", style: "justify-content:center;gap:8px;margin-top:12px" }, [
      btn("Restart", () => ear.startChallenge(), "btn primary"), btn("Exit", () => ear.exitChallenge()),
    ]));
    parent.appendChild(card);
  }

  // ---------- Advanced progressions ----------

  private advancedBody(parent: HTMLElement): void {
    const ear = this.ear;
    const np = ear.advProg;
    if (!np) return;
    parent.appendChild(labelSm("Chords  (tap ▶ to hear each)"));
    // Always a plain positional number — never reveal quality on the play button;
    // the reveal card below shows the full answer.
    parent.appendChild(el("div", { class: "et-row-gap" }, ear.progResolved.map((_, i) =>
      btn(`▶ ${i + 1}`, () => ear.playProgChordDirect(i)))));
    parent.appendChild(this.revealCardNode(ear.advRevealed, () => ear.toggleAdvReveal(), [
      el("div", { style: "font-weight:700;font-size:17px" }, [np.name]),
      el("div", { style: "font-weight:600" }, [namedRomanLine(np)]),
      el("div", {}, [ear.progResolved.map((rc) => rc.symbol).join("   ")]),
      el("div", { class: "ans-label" }, ["in " + spellPc(ear.progKey) + " " + (ear.progMode === TrainingMode.Major ? "major" : "minor")]),
    ]));
    parent.appendChild(el("div", { class: "et-card", style: `background:${BG_TEACH}` }, [
      el("div", { class: "ans-label" }, ["About this progression"]),
      el("div", { class: "et-muted", style: "margin-top:2px" }, [np.explanation]),
    ]));
  }

  private revealCardNode(revealed: boolean, onToggle: () => void, revealedChildren: HTMLElement[]): HTMLElement {
    const c = el("div", { class: "et-card et-reveal", style: `background:${revealed ? BG_REVEAL : BG_HIDDEN};text-align:left` }, [
      el("div", { class: "ans-label" }, ["Answer"]),
      revealed ? el("div", {}, revealedChildren) : el("div", { class: "et-muted" }, ["tap to reveal"]),
    ]);
    c.addEventListener("click", onToggle);
    return c;
  }

  private advancedView(parent: HTMLElement): void {
    const ear = this.ear;
    parent.appendChild(el("div", { class: "et-muted" }, ["Borrowed chords, secondary dominants and chromatic moves. Pick a key, generate one, try to identify it, then reveal the name, Roman numerals and chords."]));
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:8px" }, [el("span", { class: "ans-label" }, ["Key"]), this.keySelectInline()]));
    if (!ear.advProg) {
      parent.appendChild(el("div", { class: "v-gap-8" }));
      parent.appendChild(btn("Generate progression ▶", () => ear.nextAdvancedProgression(), "btn primary"));
      return;
    }
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:8px" }, [
      ear.isLooping ? btn("Stop ⏹", () => ear.stopLoop(), "btn primary") : btn("Play ▶", () => ear.startLoop(), "btn primary"),
      btn("Next →", () => ear.nextAdvancedProgression()),
    ]));
    parent.appendChild(this.transposeRow());
    parent.appendChild(el("div", { class: "v-gap-8" }));
    this.advancedBody(parent);
  }

  private advancedChallenge(parent: HTMLElement): void {
    const ear = this.ear;
    if (!ear.advChActive) {
      parent.appendChild(el("div", { class: "et-muted" }, [`${ear.advChallengeTotal} advanced progressions in a row. Listen, try to identify each, then reveal and mark yourself. A teaching note is shown for every one.`]));
      parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:8px" }, [el("span", { class: "ans-label" }, ["Key"]), this.keySelectInline()]));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Start challenge ▶", () => ear.startAdvChallenge(), "btn primary"));
      return;
    }
    if (ear.advChIndex >= ear.advChallengeTotal) {
      this.simpleDone(parent, ear.advChScore, ear.advChallengeTotal, () => ear.startAdvChallenge(), () => ear.exitAdvChallenge());
      return;
    }
    this.challengeHeader(parent, `Progression ${ear.advChIndex + 1} / ${ear.advChallengeTotal}`, `Score: ${ear.advChScore}`,
      () => ear.startAdvChallenge(), () => ear.exitAdvChallenge());
    parent.appendChild(el("div", { class: "et-row-gap" }, [
      ear.isLooping ? btn("Stop ⏹", () => ear.stopLoop(), "btn primary") : btn("Play ▶", () => ear.startLoop(), "btn primary"),
    ]));
    parent.appendChild(el("div", { class: "v-gap-8" }));
    this.advancedBody(parent);
    parent.appendChild(el("div", { class: "v-gap-8" }));
    if (!ear.advChMarked) {
      parent.appendChild(labelSm("Reveal the answer, then mark yourself:"));
      const got = btn("✔ I got it", () => ear.markAdv(true), "btn primary");
      const missed = btn("✘ Missed", () => ear.markAdv(false));
      if (!ear.advRevealed) { got.disabled = true; missed.disabled = true; }
      parent.appendChild(el("div", { class: "row" }, [got, missed]));
    } else {
      parent.appendChild(btn(ear.advChIndex === ear.advChallengeTotal - 1 ? "See score →" : "Next →", () => ear.advanceAdvChallenge(), "btn primary"));
    }
  }

  // ---------- Note2Chord ----------

  private n2cView(parent: HTMLElement): void {
    const ear = this.ear;
    const c = ear.n2cChallenge;
    parent.appendChild(el("div", { class: "et-muted" }, ["A triad plays, then a single note from its diatonic scale sounds above. Identify the test note's degree relative to the chord (e.g. 9, b7, maj7)."]));
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      btn(ear.n2cPlaying ? "Playing…" : "Play both ▶", () => ear.playN2c(), "btn primary"),
      btn("Next →", () => { ear.nextN2cChallenge(); ear.playN2c(); }),
    ]));
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:8px" }, [
      btn("♪ Chord", () => ear.playN2cChord()), btn("• Note", () => ear.playN2cNote()),
    ]));
    parent.appendChild(el("div", { class: "v-gap-12" }));
    const revealed = ear.n2cRevealed;
    const content = !c ? "(no challenge yet)" : !revealed ? "tap to reveal" : "";
    const card = el("div", { class: "et-card et-reveal", style: `background:${revealed ? BG_REVEAL : BG_HIDDEN};max-width:340px` }, [
      el("div", { class: "ans-label" }, ["Answer"]),
    ]);
    if (c && revealed) {
      card.appendChild(el("div", { style: "font-size:26px;font-weight:700" }, [n2cAnswerLabel(c)]));
      card.appendChild(el("div", { class: "ans-label" }, [`${n2cChordSymbol(c)}  ·  test note: ${n2cTestNoteName(c)}`]));
    } else {
      card.appendChild(el("div", { class: "et-muted" }, [content]));
    }
    card.addEventListener("click", () => ear.toggleN2cReveal());
    parent.appendChild(card);
  }

  private n2cChallenge(parent: HTMLElement): void {
    const ear = this.ear;
    if (!ear.n2cChActive) {
      parent.appendChild(el("div", { class: "et-muted" }, [`Identify the test note's degree over the chord. ${ear.n2cChallengeTotal} rounds, scored.`]));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Start challenge ▶", () => ear.startN2cChallenge(), "btn primary"));
      return;
    }
    if (ear.n2cChIndex >= ear.n2cChallengeTotal) {
      this.simpleDone(parent, ear.n2cChScore, ear.n2cChallengeTotal, () => ear.startN2cChallenge(), () => ear.exitN2cChallenge());
      return;
    }
    this.challengeHeader(parent, `Question ${ear.n2cChIndex + 1} / ${ear.n2cChallengeTotal}`, `Score: ${ear.n2cChScore}`,
      () => ear.startN2cChallenge(), () => ear.exitN2cChallenge());
    parent.appendChild(el("div", { class: "et-row-gap" }, [
      btn(ear.n2cPlaying ? "Playing…" : "Replay both ▶", () => ear.playN2c(), "btn primary"),
      btn("♪ Chord", () => ear.playN2cChord()), btn("• Note", () => ear.playN2cNote()),
    ]));
    parent.appendChild(el("div", { class: "v-gap-8" }));
    const guess = ear.n2cChGuess;
    const correct = ear.n2cChallenge ? n2cAnswerLabel(ear.n2cChallenge) : null;
    parent.appendChild(chipsRow(ear.n2cAnswerOptions().map((opt) =>
      chip(opt, guess === opt || (guess !== null && opt === correct), () => ear.guessN2c(opt), guess === null))));
    if (guess !== null) {
      parent.appendChild(el("div", { style: "font-weight:600;margin-top:8px" }, [guess === correct ? "✔ correct" : `✘ answer: ${correct}`]));
      parent.appendChild(btn(ear.n2cChIndex === ear.n2cChallengeTotal - 1 ? "See score →" : "Next →", () => ear.advanceN2cChallenge(), "btn primary"));
    }
  }

  // ---------- Flavor ----------

  private flavorView(parent: HTMLElement): void {
    const ear = this.ear;
    parent.appendChild(el("div", { class: "et-muted" }, ['Pick which flavors can appear. Tap "New chord" — a cadence plays to set the key, then a random diatonic chord sounds. Identify its scale degree and flavor.']));
    parent.appendChild(labelSm("Allowed flavors"));
    parent.appendChild(chipsRow(ear.flavorPalette.map((sym) =>
      chip(sym === "" ? "maj" : sym, ear.flavorAllowed.has(sym), () => ear.toggleFlavorAllowed(sym)))));
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:6px" }, [
      el("span", { class: "ans-label" }, ["Modes"]),
      chip("Major", ear.flavorIncludeMajor, () => { ear.flavorIncludeMajor = !ear.flavorIncludeMajor; this.rerender(); }),
      chip("Minor", ear.flavorIncludeMinor, () => { ear.flavorIncludeMinor = !ear.flavorIncludeMinor; this.rerender(); }),
    ]));
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      btn(ear.flavorPlaying ? "Playing…" : "New chord ▶", () => ear.newFlavorChallenge(), "btn primary"),
      btn(`Replay ${ear.flavorCadenceLabel()}`, () => ear.replayFlavorCadence()),
      btn("Play chord", () => ear.playFlavorChord()),
    ]));
    if (!ear.flavorStarted) return;
    parent.appendChild(labelSm("Degree  (tap to hear & compare)"));
    parent.appendChild(chipsRow([1, 2, 3, 4, 5, 6, 7].map((deg) =>
      chip(String(deg), ear.flavorGuessDegree === deg, () => ear.setFlavorGuessDegree(deg)))));
    parent.appendChild(labelSm("Flavor  (tap to hear)"));
    parent.appendChild(chipsRow([...ear.flavorAllowed].map((sym) =>
      chip(sym === "" ? "maj" : sym, ear.flavorGuessQuality === sym, () => ear.setFlavorGuessQuality(sym)))));
    parent.appendChild(el("div", { class: "v-gap-8" }));
    const revealed = ear.flavorRevealed;
    const card = el("div", { class: "et-card et-reveal", style: `background:${revealed ? BG_REVEAL : BG_HIDDEN};max-width:420px` }, [el("div", { class: "ans-label" }, ["Answer"])]);
    if (revealed) {
      const degOk = ear.flavorGuessDegree === ear.flavorDegree;
      const qualOk = ear.flavorGuessQuality === ear.flavorQuality;
      card.appendChild(el("div", { style: "font-weight:700" }, [`Degree ${ear.flavorDegree} (${ear.flavorDegreeRoman()})  ·  ${ear.flavorQuality === "" ? "maj" : ear.flavorQuality}`]));
      card.appendChild(el("div", { class: "ans-label" }, [`${ear.flavorChordSymbol()} in ${spellPc(ear.flavorKey)} ${ear.flavorMode === TrainingMode.Major ? "major" : "minor"}`]));
      if (ear.flavorGuessDegree !== null || ear.flavorGuessQuality !== null) {
        card.appendChild(el("div", { style: "font-weight:600;margin-top:4px" }, [`you: degree ${degOk ? "✔" : "✘"}  ·  flavor ${qualOk ? "✔" : "✘"}`]));
      }
    } else {
      card.appendChild(el("div", { class: "et-muted" }, ["tap to reveal"]));
    }
    card.addEventListener("click", () => ear.toggleFlavorReveal());
    parent.appendChild(card);
  }

  private flavorChallenge(parent: HTMLElement): void {
    const ear = this.ear;
    if (!ear.flavorChActive) {
      parent.appendChild(el("div", { class: "et-muted" }, [`${ear.flavorChallengeTotal} rounds. A cadence sets the key, then a random chord plays — identify its degree and flavor.`]));
      parent.appendChild(labelSm("Allowed flavors"));
      parent.appendChild(chipsRow(ear.flavorPalette.map((sym) =>
        chip(sym === "" ? "maj" : sym, ear.flavorAllowed.has(sym), () => ear.toggleFlavorAllowed(sym)))));
      parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:6px" }, [
        el("span", { class: "ans-label" }, ["Modes"]),
        chip("Major", ear.flavorIncludeMajor, () => { ear.flavorIncludeMajor = !ear.flavorIncludeMajor; this.rerender(); }),
        chip("Minor", ear.flavorIncludeMinor, () => { ear.flavorIncludeMinor = !ear.flavorIncludeMinor; this.rerender(); }),
      ]));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Start challenge ▶", () => ear.startFlavorChallenge(), "btn primary"));
      return;
    }
    if (ear.flavorChIndex >= ear.flavorChallengeTotal) {
      this.simpleDone(parent, ear.flavorChScore, ear.flavorChallengeTotal, () => ear.startFlavorChallenge(), () => ear.exitFlavorChallenge());
      return;
    }
    this.challengeHeader(parent, `Round ${ear.flavorChIndex + 1} / ${ear.flavorChallengeTotal}`, `Score: ${ear.flavorChScore}`,
      () => ear.startFlavorChallenge(), () => ear.exitFlavorChallenge());
    parent.appendChild(el("div", { class: "et-row-gap" }, [
      btn(`Replay ${ear.flavorCadenceLabel()}`, () => ear.replayFlavorCadence()),
      btn("Play chord", () => ear.playFlavorChord()),
    ]));
    parent.appendChild(labelSm("Degree  (tap to hear & compare)"));
    parent.appendChild(chipsRow([1, 2, 3, 4, 5, 6, 7].map((deg) =>
      chip(String(deg), ear.flavorGuessDegree === deg, () => ear.setFlavorGuessDegree(deg), !ear.flavorChAnswered))));
    parent.appendChild(labelSm("Flavor  (tap to hear)"));
    parent.appendChild(chipsRow([...ear.flavorAllowed].map((sym) =>
      chip(sym === "" ? "maj" : sym, ear.flavorGuessQuality === sym, () => ear.setFlavorGuessQuality(sym), !ear.flavorChAnswered))));
    parent.appendChild(el("div", { class: "v-gap-8" }));
    if (!ear.flavorChAnswered) {
      const b = btn("Submit", () => ear.submitFlavorGuess(), "btn primary");
      if (ear.flavorGuessDegree == null || ear.flavorGuessQuality == null) b.disabled = true;
      parent.appendChild(b);
    } else {
      const degOk = ear.flavorGuessDegree === ear.flavorDegree;
      const qualOk = ear.flavorGuessQuality === ear.flavorQuality;
      parent.appendChild(el("div", { style: `font-weight:700;color:${Colors.primary}` }, [`Answer: degree ${ear.flavorDegree} (${ear.flavorDegreeRoman()}) · ${ear.flavorQuality === "" ? "maj" : ear.flavorQuality}  [${ear.flavorChordSymbol()}, ${ear.flavorMode === TrainingMode.Major ? "major" : "minor"}]`]));
      parent.appendChild(el("div", { style: "font-weight:600" }, [`you: degree ${degOk ? "✔" : "✘"} · flavor ${qualOk ? "✔" : "✘"}`]));
      parent.appendChild(btn(ear.flavorChIndex === ear.flavorChallengeTotal - 1 ? "See score →" : "Next →", () => ear.advanceFlavorChallenge(), "btn primary"));
    }
  }

  // ---------- Inversions ----------

  private invPalette(parent: HTMLElement): void {
    const ear = this.ear;
    parent.appendChild(labelSm("Chord types"));
    parent.appendChild(chipsRow(ear.invPalette.map((sym) =>
      chip(sym === "" ? "maj" : sym, ear.invAllowed.has(sym), () => ear.toggleInvAllowed(sym)))));
  }

  private invGuessChips(parent: HTMLElement, enabled: boolean): void {
    const ear = this.ear;
    parent.appendChild(labelSm("Which inversion?  (tap to hear & compare)"));
    const chips: HTMLElement[] = [];
    for (let k = 0; k < ear.invCount(); k++) chips.push(chip(inversionName(k), ear.invGuess === k, () => ear.setInvGuess(k), enabled));
    parent.appendChild(chipsRow(chips));
  }

  private invView(parent: HTMLElement): void {
    const ear = this.ear;
    parent.appendChild(el("div", { class: "et-muted" }, ["A chord plays in some inversion (which chord tone is in the bass). Identify it. Pick which chord types can appear below."]));
    this.invPalette(parent);
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      btn(ear.invPlaying ? "Playing…" : "New chord ▶", () => ear.newInversion(), "btn primary"),
      btn("Replay", () => ear.playInversion()),
    ]));
    if (!ear.invStarted) return;
    this.invGuessChips(parent, true);
    parent.appendChild(this.revealCard("Answer", !ear.invRevealed,
      inversionName(ear.invInversion) + "  ·  " + spellPc(ear.invRoot) + ear.invQuality,
      () => ear.toggleInvReveal(), false));
    if (ear.invRevealed && ear.invGuess !== null) {
      parent.appendChild(el("div", { style: "font-weight:600;margin-top:6px" }, [ear.invGuess === ear.invInversion ? "✔ correct" : `✘ that was the ${inversionName(ear.invGuess).toLowerCase()}`]));
    }
  }

  private invChallenge(parent: HTMLElement): void {
    const ear = this.ear;
    if (!ear.invChActive) {
      parent.appendChild(el("div", { class: "et-muted" }, [`${ear.invChallengeTotal} rounds. A chord plays in an inversion — identify which. Choose which chord types can appear:`]));
      this.invPalette(parent);
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Start challenge ▶", () => ear.startInvChallenge(), "btn primary"));
      return;
    }
    if (ear.invChIndex >= ear.invChallengeTotal) {
      this.simpleDone(parent, ear.invChScore, ear.invChallengeTotal, () => ear.startInvChallenge(), () => ear.exitInvChallenge());
      return;
    }
    this.challengeHeader(parent, `Round ${ear.invChIndex + 1} / ${ear.invChallengeTotal}`, `Score: ${ear.invChScore}`,
      () => ear.startInvChallenge(), () => ear.exitInvChallenge());
    parent.appendChild(btn("Replay ▶", () => ear.playInversion()));
    this.invGuessChips(parent, !ear.invChAnswered);
    parent.appendChild(el("div", { class: "v-gap-8" }));
    if (!ear.invChAnswered) {
      const b = btn("Submit", () => ear.submitInvGuess(), "btn primary");
      if (ear.invGuess == null) b.disabled = true;
      parent.appendChild(b);
    } else {
      const ok = ear.invGuess === ear.invInversion;
      parent.appendChild(el("div", { style: `font-weight:700;color:${Colors.primary}` }, [`${ok ? "✔ correct" : `✘ answer: ${inversionName(ear.invInversion)}`}   (${spellPc(ear.invRoot)}${ear.invQuality})`]));
      parent.appendChild(btn(ear.invChIndex === ear.invChallengeTotal - 1 ? "See score →" : "Next →", () => ear.advanceInvChallenge(), "btn primary"));
    }
  }

  // ---------- Aug / Dim ----------

  private augDimLabel(sym: string): string {
    switch (sym) {
      case "aug": return "Augmented (+)";
      case "dim": return "Diminished (°)";
      case "dim7": return "dim7 (°7)";
      case "m7b5": return "m7♭5 (half-dim ø)";
      case "7#5": return "7♯5 (aug 7th)";
      case "maj7#5": return "maj7♯5";
      default: return sym;
    }
  }

  private augDimPalette(parent: HTMLElement): void {
    const ear = this.ear;
    parent.appendChild(labelSm("Chord types"));
    parent.appendChild(chipsRow(ear.augDimPalette.map((sym) =>
      chip(this.augDimLabel(sym), ear.augDimAllowed.has(sym), () => ear.toggleAugDimAllowed(sym)))));
  }

  private augDimGuessChips(parent: HTMLElement, enabled: boolean): void {
    const ear = this.ear;
    parent.appendChild(labelSm("Which chord?  (tap to hear & compare)"));
    parent.appendChild(chipsRow(ear.augDimPalette.filter((s) => ear.augDimAllowed.has(s)).map((sym) =>
      chip(this.augDimLabel(sym), ear.adGuess === sym, () => ear.setAdGuess(sym), enabled))));
  }

  private augDimView(parent: HTMLElement): void {
    const ear = this.ear;
    parent.appendChild(el("div", { class: "et-muted" }, ["Tell augmented from diminished by ear. Enable the qualities you want to drill (add 7th/extended forms), then identify each chord."]));
    this.augDimPalette(parent);
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      btn("New chord ▶", () => ear.newAugDim(), "btn primary"),
      btn("Replay", () => ear.playAugDim()),
    ]));
    if (!ear.adStarted) return;
    this.augDimGuessChips(parent, true);
    parent.appendChild(this.revealCard("Answer", !ear.adRevealed,
      spellPc(ear.adRoot) + ear.adQuality + "  ·  " + ear.augDimFamily(ear.adQuality),
      () => ear.toggleAugDimReveal(), false));
    if (ear.adRevealed && ear.adGuess !== null) {
      parent.appendChild(el("div", { style: "font-weight:600;margin-top:6px" }, [ear.adGuess === ear.adQuality ? "✔ correct" : `✘ it was ${this.augDimLabel(ear.adQuality)}`]));
    }
  }

  private augDimChallenge(parent: HTMLElement): void {
    const ear = this.ear;
    if (!ear.adChActive) {
      parent.appendChild(el("div", { class: "et-muted" }, [`${ear.augDimChallengeTotal} rounds. Identify each augmented/diminished chord. Choose which qualities can appear:`]));
      this.augDimPalette(parent);
      parent.appendChild(el("div", { class: "v-gap-12" }));
      const b = btn("Start challenge ▶", () => ear.startAugDimChallenge(), "btn primary");
      if (ear.augDimAllowed.size === 0) b.disabled = true;
      parent.appendChild(b);
      return;
    }
    if (ear.adChIndex >= ear.augDimChallengeTotal) {
      this.simpleDone(parent, ear.adChScore, ear.augDimChallengeTotal, () => ear.startAugDimChallenge(), () => ear.exitAugDimChallenge());
      return;
    }
    this.challengeHeader(parent, `Round ${ear.adChIndex + 1} / ${ear.augDimChallengeTotal}`, `Score: ${ear.adChScore}`,
      () => ear.startAugDimChallenge(), () => ear.exitAugDimChallenge());
    parent.appendChild(btn("Replay ▶", () => ear.playAugDim()));
    this.augDimGuessChips(parent, !ear.adChAnswered);
    parent.appendChild(el("div", { class: "v-gap-8" }));
    if (!ear.adChAnswered) {
      const b = btn("Submit", () => ear.submitAugDimGuess(), "btn primary");
      if (ear.adGuess == null) b.disabled = true;
      parent.appendChild(b);
    } else {
      const ok = ear.adGuess === ear.adQuality;
      parent.appendChild(el("div", { style: `font-weight:700;color:${Colors.primary}` }, [`${ok ? "✔ correct" : `✘ answer: ${this.augDimLabel(ear.adQuality)}`}   (${spellPc(ear.adRoot)}${ear.adQuality})`]));
      parent.appendChild(btn(ear.adChIndex === ear.augDimChallengeTotal - 1 ? "See score →" : "Next →", () => ear.advanceAugDimChallenge(), "btn primary"));
    }
  }
}

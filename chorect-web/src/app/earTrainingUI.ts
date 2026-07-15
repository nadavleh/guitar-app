// Ear Training screen, ported from app/.../EarTrainingScreen.kt. A self-contained
// view class owning its own (optional) fretboard panel canvas, re-rendered on each
// state change. Native <select> replaces the Compose dropdowns.

import { AppState, ChallengeScore, CHALLENGE_SCORE_ORDER } from "./appState";
import { EarTrainingState, EarSubMode, EarMode } from "./earTrainingState";
import { FretboardCanvas } from "./fretboardCanvas";
import { shapeMarks } from "./marks";
import { el, btn, segmented, switchRow, labelSm, songLinkRow } from "./dom";
import { transportDock, toneSheet } from "./transport";
import { icon } from "./icons";
import { renderChallengeStatsOverlay } from "./statsOverlay";
import {
  spellPc, noteAt, TrainingMode, ChordTypeLevel, ChordTypeLevelName,
  namedRomanLine, inversionName, n2cAnswerLabel, n2cChordSymbol, n2cTestNoteName,
  parseChord, ChordShapeGenerator, CagedShape, notesFrom, midiPitchClass, fp, fpKey,
  IntervalDirection, INTERVAL_CHOICES, intervalChoiceFor,
  MAJOR_PROGRESSIONS, MINOR_PROGRESSIONS, ADVANCED_PROGRESSIONS, ADVANCED2_PROGRESSIONS,
  SUS_PROGRESSIONS, CIRCLE_WINDOWS, romanLineFor,
  SongExample, songsForDiatonic, songsForAdvanced, songsForCircleWindow,
  ResolvedChord, ChordShape, resolveProgression, resolveNamed, resolveCircleWindow,
} from "../theory";

const DISPLAY_FRETS = 14;

// card backgrounds (Compose container colors → tints of our palette). CSS
// custom properties, not static hexes, so these stay readable in light theme.
const BG_HIDDEN = "var(--surface2)";
const BG_REVEAL = "color-mix(in srgb, var(--scale-tone) 20%, transparent)";
const BG_PRIMARY = "color-mix(in srgb, var(--act) 20%, transparent)";
// The playing-head uses the distinct FEEDBACK (teal) hue so it never reads the
// same as an ACT-coloured user selection (which stays coral/--act).
const BG_PLAYHEAD = "color-mix(in srgb, var(--feedback) 30%, transparent)";
const BG_TEACH = "color-mix(in srgb, var(--chord-tone) 15%, transparent)";

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

/** "0 semitones" / "+3 semitones" / "−2 semitones" for the transpose counters. */
function transposeLabel(n: number): string {
  const unit = Math.abs(n) === 1 ? "semitone" : "semitones";
  const num = n > 0 ? `+${n}` : n < 0 ? `−${-n}` : "0";
  return `${num} ${unit}`;
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

  /** Signal restructure (T9): which bar the fixed Challenge answer pad currently
   *  targets (replaces the old per-bar popup keyboard — see [challengeAnswerPad]). */
  private challengeSelectedBar = 0;
  private lastChallengeIndexForPad = -1;
  /** Pending picks for [challengeSelectedBar] (extended/mix mode needs an
   *  extension tap before the guess commits) — reset whenever the target bar
   *  changes, mirroring Android's `remember(bar)` keying. */
  private padPickedBar: number | null = null;
  private padPickedDeg: number | null = null;
  private padPickedRoman: string | null = null;
  private padPickedExt: string | null = null;
  private padExtOpen = false;
  /** Physical-keyboard handler for the fixed answer pad (1..7 pick a degree,
   *  Enter commits in extension mode, Esc cancels the pending pick) — attached
   *  once for the screen's lifetime and self-guards on whether a Progression
   *  challenge question is actually in flight. */
  private challengeKeyHandler: ((e: KeyboardEvent) => void) | null = null;

  /** Sub-mode chip row's "More ▾" overflow (Flavor/Inversions/AugDim). */
  private subModeMoreOpen = false;
  private subModeOutsideCloser: ((e: Event) => void) | null = null;

  /** One shared bottom sheet for the Progression generator/key/level settings
   *  (Signal move — replaces the always-expanded settings block with a
   *  GeneratorSummaryCard → sheet, same content, mirrors GeneratorSettingsSheet). */
  private settingsSheetOpen = false;

  private statsOpen = false;
  private libraryOpen = false;
  private toneSheetOpen = false;
  /** Which progression-library row is expanded (single-open accordion), or null. */
  private libExpandedKey: string | null = null;
  /** Whether the expanded row's follow-along fretboard is shown. */
  private libShowFb = false;
  /** Preserved scrollTop of the library overlay across rerenders (fixes the jump-to-top). */
  private libScrollTop = 0;
  /** Dedicated fretboard canvas for the library overlay (kept separate from the main
   *  view's shared canvas, which stays mounted behind the scrim). */
  private libFbEl: HTMLCanvasElement | null = null;
  private libFb: FretboardCanvas | null = null;

  constructor(private ear: EarTrainingState, private state: AppState, private onBack: () => void, private onToLooper: (symbols: string[]) => void) {
    this.attachChallengeKeys();
  }

  /** Per-kind challenge stats popup (scrim closes it) — shared with the More
   *  sheet's "Challenge stats" row; see statsOverlay.ts. */
  private statsOverlay(): HTMLElement {
    return renderChallengeStatsOverlay(this.state, () => { this.statsOpen = false; this.rerender(); });
  }

  // ---------- Signal restructure (T9): sub-mode chips, generator summary/sheet,
  // Challenge progress ring + dot strip + fixed answer pad ----------

  private static readonly SUBMODE_LABEL: Record<EarSubMode, string> = {
    [EarSubMode.Progression]: "Progressions",
    [EarSubMode.Note2Chord]: "Note→Chord",
    [EarSubMode.Flavor]: "Flavor",
    [EarSubMode.Inversions]: "Inversions",
    [EarSubMode.AugDim]: "Aug / Dim",
    [EarSubMode.Intervals]: "Intervals",
  };

  /** Sub-mode chip row (Signal move — replaces the sub-mode <select>):
   *  Progressions/Intervals/Note→Chord are always-visible chips; Flavor/
   *  Inversions/AugDim live behind a "More ▾" overflow chip which shows the
   *  active sub-mode's name when it IS one of the overflowed ones, so the
   *  current mode is never hidden — mirrors Android's SubModeChipRow. */
  private subModeChipRow(): HTMLElement {
    const ear = this.ear;
    const label = (s: EarSubMode) => EarTrainingUI.SUBMODE_LABEL[s];
    const primary = [EarSubMode.Progression, EarSubMode.Intervals, EarSubMode.Note2Chord];
    const overflow = [EarSubMode.Flavor, EarSubMode.Inversions, EarSubMode.AugDim];
    const row = el("div", { class: "chip-row" });
    for (const s of primary) row.appendChild(chip(label(s), ear.progSubMode === s, () => ear.switchTab(s)));

    const inOverflow = overflow.includes(ear.progSubMode);
    const wrap = el("div", { class: "et-submode-wrap", style: "position:relative" });
    wrap.appendChild(chip((inOverflow ? label(ear.progSubMode) : "More") + "  ▾", inOverflow, () => {
      this.subModeMoreOpen = !this.subModeMoreOpen;
      this.rerender();
    }));
    if (this.subModeMoreOpen) {
      const pop = el("div", { class: "et-submenu-pop" });
      for (const s of overflow) {
        const item = el("div", { class: "et-submenu-item" }, [label(s)]);
        item.addEventListener("click", () => { this.subModeMoreOpen = false; ear.switchTab(s); });
        pop.appendChild(item);
      }
      wrap.appendChild(pop);
    }
    row.appendChild(wrap);
    return row;
  }

  /** Short label for the current progression generator. */
  private generatorLabel(): string {
    const ear = this.ear;
    if (ear.advancedMode) return ear.advCategory === "sus" ? "Sus chords" : ear.advCategory === "advanced2" ? "Advanced II" : "Advanced";
    return ear.circleMode ? "Circle of 5ths" : ear.iiiFocusMode ? "I → iii focus" : "Diatonic";
  }

  /** One-line teaching caption for the current progression generator. */
  private generatorCaption(): string {
    const ear = this.ear;
    if (ear.advancedMode && ear.advCategory === "sus") return "Progressions built on suspended (sus2/sus4) chords.";
    if (ear.advancedMode && ear.advCategory === "advanced2") return "Richer colours: major-7th, minor-9th and modal (Dorian/Mixolydian/Lydian/Phrygian).";
    return ear.advancedMode
      ? "Borrowed chords, secondary dominants & jazz turnarounds, each with a note."
      : ear.circleMode
      ? "Circle-of-fifths windows built around secondary dominants (V7 of the next chord)."
      : ear.iiiFocusMode
      ? "Drill for hearing the I→iii move — every progression opens with I then iii (major)."
      : "Standard diatonic progressions in the chosen key & mode.";
  }

  private generatorSelect(): HTMLSelectElement {
    const ear = this.ear;
    const gen = ear.advancedMode
      ? (ear.advCategory === "sus" ? "sus" : ear.advCategory === "advanced2" ? "advanced2" : "advanced")
      : ear.circleMode ? "circle" : ear.iiiFocusMode ? "iiifocus" : "diatonic";
    return select(
      [
        { value: "diatonic", label: "Generator: Diatonic" },
        { value: "iiifocus", label: "Generator: I → iii focus" },
        { value: "advanced", label: "Generator: Advanced (non-diatonic)" },
        { value: "advanced2", label: "Generator: Advanced II (maj7 / min9 / modal)" },
        { value: "sus", label: "Generator: Sus chords" },
        { value: "circle", label: "Generator: Circle — secondary dominants" },
      ],
      gen,
      (v) => {
        if (v === "advanced") ear.setAdvancedMode(true);
        else if (v === "advanced2") ear.setAdvancedCategory("advanced2");
        else if (v === "sus") ear.setAdvancedCategory("sus");
        else if (v === "circle") ear.setCircleMode(true);
        else if (v === "iiifocus") ear.setIiiFocusMode(true);
        else { ear.setAdvancedMode(false); ear.setCircleMode(false); ear.setIiiFocusMode(false); }
      },
    );
  }

  /** Short label for the current chord-type/level pool ("Mix" when Mix-all is on). */
  private levelLabel(): string {
    const ear = this.ear;
    return ear.earMixAll ? "Mix" : ChordTypeLevelName[ear.chordTypeLevel];
  }

  /** One-line "‹Generator› · ‹key/Random› · ‹level› — tap to configure" summary
   *  card (Signal move): replaces the always-expanded generator/key/level block
   *  with a single tappable card opening [generatorSettingsSheet]. Shown by
   *  every Progression sub-mode view (diatonic + advanced/circle), both
   *  Practice and Challenge — mirrors Android's GeneratorSummaryCard. */
  private generatorSummaryCard(onClick: () => void): HTMLElement {
    const ear = this.ear;
    const keyLabel = ear.fixedKey == null ? "Random key" : spellPc(ear.fixedKey);
    const summary = this.generatorLabel() + "  ·  " + keyLabel + (!ear.specialProgMode ? "  ·  " + this.levelLabel() : "");
    const card = el("div", { class: "et-card et-summary-card", style: `background:var(--surface2)` }, [
      el("div", { style: "flex:1;min-width:0" }, [
        el("div", { style: "font-weight:600" }, [summary]),
        el("div", { class: "ans-label" }, ["tap to configure"]),
      ]),
      icon("tune", 20),
    ]);
    card.addEventListener("click", onClick);
    return card;
  }

  /** Settings sheet opened by [generatorSummaryCard]: hosts the generator
   *  select + caption + Library button (always), plus — for the diatonic
   *  generator — the full [progressionSettings] (key/modes/level/voicing), or
   *  — for the advanced/circle generators, which don't use those pools — just
   *  the key picker. Mirrors Android's GeneratorSettingsSheet. */
  private generatorSettingsSheet(onClose: () => void): HTMLElement {
    const ear = this.ear;
    const sheet = el("div", { class: "sheet" });
    sheet.appendChild(el("div", { class: "sheet-grabber" }));
    sheet.appendChild(el("div", { class: "sheet-header" }, [el("h2", {}, ["Progression settings"]), btn("Done", onClose, "btn text")]));
    sheet.appendChild(el("div", { style: "display:flex;gap:8px;align-items:center;margin:4px 0" }, [
      el("div", { style: "flex:1" }, [this.generatorSelect()]),
      btn("Library", () => { this.libraryOpen = true; this.rerender(); }),
    ]));
    sheet.appendChild(el("div", { class: "et-muted", style: "font-size:12px;font-style:italic;margin-bottom:10px" }, [this.generatorCaption()]));
    if (ear.specialProgMode) {
      sheet.appendChild(el("div", { class: "et-row-gap" }, [el("span", { class: "ans-label" }, ["Key"]), this.keySelectInline()]));
    } else {
      this.progressionSettings(sheet);
    }
    const scrim = el("div", { class: "sheet-scrim" }, [sheet]);
    scrim.addEventListener("click", (e) => { if (e.target === scrim) onClose(); });
    return scrim;
  }

  private openSettingsSheet(): void { this.settingsSheetOpen = true; this.rerender(); }

  /** Plain "‹label› · Score: ‹score›" row for the Progression/Advanced
   *  Challenge in-flight screens — Restart/Quit are pinned in the screen
   *  header instead (see render()'s progChallengeInFlight). Other challenge
   *  sub-modes (Note→Chord/Flavor/Inversions/AugDim) keep the inline
   *  [challengeHeader] with its own Restart/Quit buttons. */
  private challengeScoreRow(label: string, score: string): HTMLElement {
    return el("div", { class: "row" }, [
      el("div", { style: "flex:1;font-weight:600" }, [label]),
      el("div", { style: `color:var(--act);font-weight:600` }, [score]),
    ]);
  }

  /** 64px progress ring for the Progression Challenge: a muted track with an
   *  act-colored arc swept to the answered fraction (CSS conic-gradient donut —
   *  an inner surface-colored circle masks the middle so only a ring shows),
   *  "Q n/N" centered. Mirrors Android's ChallengeProgressRing (Canvas arcs). */
  private challengeProgressRing(index: number, total: number): HTMLElement {
    const fraction = Math.min(Math.max(total > 0 ? index / total : 0, 0), 1);
    const deg = Math.round(fraction * 360);
    const ring = el("div", {
      class: "et-ring",
      style: `background: conic-gradient(var(--act) ${deg}deg, var(--divider) ${deg}deg)`,
    }, [el("div", { class: "et-ring-inner" }, [`Q ${index + 1}/${total}`])]);
    return ring;
  }

  /** Per-question dot strip for the Progression Challenge: feedback(teal) =
   *  right, act(coral/error) = wrong, act-filled+ring = current, muted =
   *  upcoming/unanswered. Mirrors Android's ChallengeDotStrip. */
  private challengeDotStrip(): HTMLElement {
    const ear = this.ear;
    const row = el("div", { class: "chip-row" });
    for (let i = 0; i < ear.challengeTotal; i++) {
      const isCurrent = i === ear.challengeIndex;
      const answer = ear.challengeAnswers[i];
      const cls = isCurrent ? "et-dotc current" : answer === true ? "et-dotc right" : answer === false ? "et-dotc wrong" : "et-dotc muted";
      row.appendChild(el("div", { class: cls }));
    }
    return row;
  }

  private resetPad(): void {
    this.padPickedDeg = null; this.padPickedRoman = null; this.padPickedExt = null; this.padExtOpen = false;
  }

  /** Physical 1..7 keys pick a degree in [challengeSelectedBar]'s pad (Enter
   *  commits in extension mode; Esc cancels the pending pick). Attached once
   *  for the UI's lifetime — self-guards on whether a Progression challenge
   *  question is actually in flight, so it's a no-op the rest of the time
   *  (mirrors the always-attached spacebar shortcut in ui.ts). Rewired from
   *  the old popup keyboard's handler onto the same [guessChallengeKeyboard]/
   *  [clearChallengeBar] commit calls the fixed answer pad now uses. */
  private attachChallengeKeys(): void {
    if (this.challengeKeyHandler) return;
    const handler = (e: KeyboardEvent) => {
      const ear = this.ear;
      const active = ear.progSubMode === EarSubMode.Progression && ear.earMode === EarMode.Challenge &&
        !ear.specialProgMode && ear.challengeActive && ear.challengeIndex < ear.challengeTotal;
      if (!active) return;
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable)) return;
      const bar = this.challengeSelectedBar;
      if (this.padPickedBar !== bar) { this.padPickedBar = bar; this.resetPad(); }
      const needsExt = ear.challengeNeedsExt && !ear.challengeCombinedMode;
      if (e.key >= "1" && e.key <= "7") {
        const idx = parseInt(e.key, 10) - 1;
        const keys = ear.keyboardKeys();
        if (idx >= keys.length) return;
        e.preventDefault();
        const [majDeg, roman] = keys[idx];
        if (this.padPickedDeg !== majDeg) { this.padPickedExt = null; this.padExtOpen = false; }
        this.padPickedDeg = majDeg; this.padPickedRoman = roman;
        if (!needsExt) { ear.guessChallengeKeyboard(bar, majDeg, roman, null); this.resetPad(); }
        this.rerender();
      } else if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
        // Move the selected answer bar (and play it) with the arrow keys.
        e.preventDefault();
        const d = e.key === "ArrowLeft" ? -1 : 1;
        this.challengeSelectedBar = Math.min(Math.max(this.challengeSelectedBar + d, 0), 3);
        ear.playBarOnce(this.challengeSelectedBar);
        this.rerender();
      } else if (e.key === "Escape") {
        e.preventDefault();
        this.resetPad();
        this.rerender();
      } else if (e.key === "Enter" && needsExt && this.padPickedDeg != null) {
        e.preventDefault();
        ear.guessChallengeKeyboard(bar, this.padPickedDeg, this.padPickedRoman ?? String(this.padPickedDeg), this.padPickedExt);
        this.resetPad();
        this.rerender();
      }
    };
    this.challengeKeyHandler = handler;
    document.addEventListener("keydown", handler);
  }

  /** The fixed Challenge answer pad for [bar] (Signal move — replaces the old
   *  popup keyboard): a Major/Minor shift, a grid of 7 degree keys (I ii iii
   *  IV V vi vii°), and — when the level uses them, and not fixed-7ths
   *  (combined) mode — a "7th ▾" expander revealing that degree's diatonic
   *  extensions. Triads/fixed-7ths commit on the degree tap; extended/mix
   *  waits for an extension tap. Reuses the exact same
   *  [EarTrainingState.guessChallengeKeyboard]/[EarTrainingState.clearChallengeBar]
   *  commit logic the popup used — only the placement changed. */
  private challengeAnswerPad(bar: number): HTMLElement {
    const ear = this.ear;
    if (this.padPickedBar !== bar) { this.padPickedBar = bar; this.resetPad(); }
    const needsExt = ear.challengeNeedsExt && !ear.challengeCombinedMode;
    const extOptions = this.padPickedDeg != null ? ear.challengeExtOptionsForDegree(this.padPickedDeg) : [];

    const commit = (ext: string | null) => {
      if (this.padPickedDeg == null) return;
      ear.guessChallengeKeyboard(bar, this.padPickedDeg, this.padPickedRoman ?? String(this.padPickedDeg), ext);
      this.resetPad();
      this.rerender();
    };

    // Major/Minor shift sits on the LEFT; the bar label fills the rest on the right.
    const header = el("div", { class: "row" }, [
      chip("Major", !ear.keyboardMinor, () => { if (ear.keyboardMinor) ear.toggleKeyboardShift(); }),
      chip("⇧ Minor", ear.keyboardMinor, () => { if (!ear.keyboardMinor) ear.toggleKeyboardShift(); }),
      el("span", { class: "ans-label", style: "flex:1;text-align:right" }, [`Bar ${bar + 1} answer`]),
    ]);

    const grid = el("div", { class: "et-pad-grid" }, ear.keyboardKeys().map(([majDeg, roman]) =>
      chip(roman, this.padPickedDeg === majDeg, () => {
        if (this.padPickedDeg !== majDeg) { this.padPickedExt = null; this.padExtOpen = false; }
        this.padPickedDeg = majDeg; this.padPickedRoman = roman;
        if (!needsExt) commit(null); else this.rerender();
      })));
    if (needsExt) grid.appendChild(chip("7th ▾", this.padExtOpen, () => { this.padExtOpen = !this.padExtOpen; this.rerender(); }));

    const children: HTMLElement[] = [header, el("div", { class: "v-gap-8" }), grid];
    if (needsExt && this.padExtOpen) {
      if (this.padPickedDeg == null) {
        children.push(el("div", { class: "et-muted", style: "margin-top:8px" }, ["Pick a degree first — its valid extensions appear here."]));
      } else {
        children.push(el("div", { class: "chip-row", style: "margin-top:8px" }, extOptions.map((ext) =>
          chip(ext === "" ? "triad" : ext, this.padPickedExt === ext, () => { this.padPickedExt = ext; commit(ext); }))));
      }
    }
    children.push(el("div", { style: "margin-top:6px" }, [
      btn(`Clear bar ${bar + 1}`, () => { ear.clearChallengeBar(bar); this.resetPad(); this.rerender(); }, "btn text"),
    ]));

    return el("div", { class: "et-card", style: `background:color-mix(in srgb, var(--surface2) 70%, transparent)` }, children);
  }

  render(container: HTMLElement): void {
    const ear = this.ear;
    const screen = el("div", { class: "tool-screen" });

    // header: title + (while a Progression/Advanced challenge is in flight)
    // pinned Restart/Quit icons + Stats + Tune + Back — mirrors Android's
    // header Row in EarTrainingScreen() exactly (Signal restructure T9).
    const progChallengeInFlight = ear.progSubMode === EarSubMode.Progression && ear.earMode === EarMode.Challenge &&
      (ear.specialProgMode
        ? ear.advChActive && ear.advChIndex < ear.advChallengeTotal
        : ear.challengeActive && ear.challengeIndex < ear.challengeTotal);
    const topbarChildren: HTMLElement[] = [el("div", { class: "tool-title" }, ["EAR TRAINING"])];
    if (progChallengeInFlight) {
      const restartBtn = el("button", { class: "tune-btn", "aria-label": "Restart challenge" }, [icon("restart", 18)]);
      restartBtn.addEventListener("click", () => { if (ear.specialProgMode) ear.startAdvChallenge(); else ear.startChallenge(); });
      const quitBtn = el("button", { class: "tune-btn", "aria-label": "Quit challenge" }, [icon("close", 18)]);
      quitBtn.addEventListener("click", () => { if (ear.specialProgMode) ear.exitAdvChallenge(); else ear.exitChallenge(); });
      topbarChildren.push(restartBtn, quitBtn);
    }
    topbarChildren.push(
      btn("Stats", () => { this.statsOpen = true; this.rerender(); }),
      (() => {
        const b = el("button", { class: "tune-btn", "aria-label": "Tone" }, [icon("tune", 18)]);
        b.addEventListener("click", () => { this.toneSheetOpen = true; this.rerender(); });
        return b;
      })(),
      btn("Back", () => { ear.release(); this.onBack(); }),
    );
    screen.appendChild(el("div", { class: "tool-topbar" }, topbarChildren));

    // Practice/Challenge segmented control (Signal move — replaces the mode
    // <select>) + sub-mode chip row (replaces the sub-mode <select>).
    screen.appendChild(el("div", { style: "margin-top:8px" }, [
      segmented(
        [{ value: EarMode.Practice, label: "Practice" }, { value: EarMode.Challenge, label: "Challenge" }],
        ear.earMode,
        (v) => ear.setEarMode(v),
      ),
    ]));
    screen.appendChild(el("div", { style: "margin:8px 0" }, [this.subModeChipRow()]));

    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);

    switch (ear.progSubMode) {
      case EarSubMode.Progression:
        if (ear.specialProgMode) ear.earMode === EarMode.Challenge ? this.advancedChallenge(body) : this.advancedView(body);
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
      case EarSubMode.Intervals:   // challenge-only (#6)
        this.intervalsView(body);
        break;
    }

    // Transport dock (Signal move #2): replaces the per-view Play ▶/Stop ⏹
    // buttons for every Progression generator (diatonic/advanced/circle/iii-focus)
    // in both Practice and Challenge. progBpm is read once when startLoop()
    // launches its loop, so a live BPM edit restarts it to take effect (mirrors
    // Android's TransportDock wiring in EarTrainingScreen.kt).
    if (ear.progSubMode === EarSubMode.Progression) {
      screen.appendChild(transportDock({
        playing: ear.isLooping,
        onPlayStop: () => { if (ear.isLooping) ear.stopLoop(); else ear.startLoop(); },
        bpm: ear.progBpm,
        onBpm: (v) => {
          ear.progBpm = Math.round(v);
          if (ear.isLooping) { ear.stopLoop(); ear.startLoop(); }
          this.rerender();
        },
        toneLabel: this.state.sound,
        onTone: () => { this.toneSheetOpen = true; this.rerender(); },
      }));
    }

    container.appendChild(screen);
    if (this.statsOpen) container.appendChild(this.statsOverlay());
    if (this.libraryOpen) container.appendChild(this.libraryOverlay());
    if (this.settingsSheetOpen) container.appendChild(this.generatorSettingsSheet(() => { this.settingsSheetOpen = false; this.rerender(); }));
    if (this.toneSheetOpen) container.appendChild(toneSheet(this.state, this.ear, () => { this.toneSheetOpen = false; this.rerender(); }));

    // Close the sub-mode "More ▾" overflow when the next tap lands outside it
    // (same single-tracked-listener pattern as SambaLooperUI's popups).
    if (this.subModeOutsideCloser) {
      document.removeEventListener("pointerdown", this.subModeOutsideCloser, true);
      this.subModeOutsideCloser = null;
    }
    if (this.subModeMoreOpen) {
      const onDoc = (e: Event) => {
        if (!(e.target as HTMLElement).closest(".et-submode-wrap")) {
          document.removeEventListener("pointerdown", onDoc, true);
          if (this.subModeOutsideCloser === onDoc) this.subModeOutsideCloser = null;
          this.subModeMoreOpen = false;
          this.rerender();
        }
      };
      this.subModeOutsideCloser = onDoc;
      setTimeout(() => { if (this.subModeOutsideCloser === onDoc) document.addEventListener("pointerdown", onDoc, true); }, 0);
    }
  }

  /** Progression-library popup: the pools the trainer draws from. Every row is clickable
   *  (▸/▾, single-open accordion) and expands to a Play/Stop button that loops the
   *  progression in a fixed key via the preview player, an optional follow-along
   *  fretboard, and the "Title — Artist" list. Scroll position is preserved across the
   *  full-subtree rerender (see [libScrollTop]). */
  private libraryOverlay(): HTMLElement {
    const ear = this.ear;
    // Cleared each build; re-armed by libFretboard() only when a board is actually shown,
    // so the per-bar hook never fires against a hidden/stale canvas.
    ear.libOnBar = null;
    // The scrollable card — captured so handlers can save its scrollTop before a rerender.
    const body = el("div", { class: "et-card", style: "max-width:520px;max-height:75vh;overflow:auto;margin:auto" }, [
      el("div", { style: "font-weight:700;font-size:16px;margin-bottom:8px" }, ["Progression library"]),
    ]);
    const close = () => { this.libScrollTop = 0; ear.libraryStop(); this.libraryOpen = false; this.rerender(); };
    const saveScroll = () => { this.libScrollTop = body.scrollTop; };

    // Expanded detail: play/stop, fretboard toggle + follow-along board, then songs.
    const detail = (key: string, songs: SongExample[], chords: ResolvedChord[]): HTMLElement => {
      const playing = ear.libPlayingId === key;
      const playBtn = btn(playing ? "Stop ■" : "Play ▶", () => {
        saveScroll();
        if (playing) ear.libraryStop(); else ear.libraryPlay(key, chords);
        this.rerender();
      }, "btn primary");
      const children: HTMLElement[] = [
        el("div", { style: "margin:4px 0" }, [playBtn]),
        switchRow("Show fretboard", null, this.libShowFb, (v) => { saveScroll(); this.libShowFb = v; this.rerender(); }),
      ];
      if (this.libShowFb) children.push(this.libFretboard(key, this.previewShapeWeb(chords[0]?.symbol)));
      if (songs.length) {
        children.push(el("div", { style: "padding:2px 0 6px 8px" },
          songs.map((sg) => songLinkRow(sg.title, sg.artist))));
      } else {
        children.push(el("div", { class: "et-muted", style: "font-size:13px;font-style:italic;padding:2px 0 6px 14px" },
          ["No song examples for this one."]));
      }
      return el("div", { style: "padding-left:8px" }, children);
    };

    // One progression row: label + chevron; tap toggles expansion (single-open).
    const row = (key: string, label: string, songs: SongExample[], chords: ResolvedChord[]): HTMLElement => {
      const open = this.libExpandedKey === key;
      const head = el("div", {
        class: "et-muted",
        style: `font-size:13px;display:flex;gap:8px;align-items:baseline;cursor:pointer`,
      }, [
        el("span", { style: "flex:1" }, [label]),
        el("span", { style: `color:var(--act)` }, [open ? "▾" : "▸"]),
      ]);
      head.addEventListener("click", () => {
        saveScroll();
        // Changing which row is open stops any preview and resets the fretboard toggle.
        ear.libraryStop();
        this.libExpandedKey = open ? null : key;
        this.libShowFb = false;
        this.rerender();
      });
      return el("div", {}, open ? [head, detail(key, songs, chords)] : [head]);
    };

    const section = (title: string, caption: string | null, rows: HTMLElement[]): HTMLElement =>
      el("div", { style: "margin-bottom:10px" }, [
        el("div", { style: `font-weight:700;color:var(--act)` }, [title]),
        ...(caption ? [el("div", { class: "et-muted", style: "font-size:12px;font-style:italic" }, [caption])] : []),
        ...rows,
      ]);

    body.appendChild(section("Major (diatonic)", "Tap a progression for songs + to hear it (fixed key C major).",
      MAJOR_PROGRESSIONS.map((p) => row(`maj:${p.degrees.join(",")}`, romanLineFor(p), songsForDiatonic(p),
        resolveProgression(p, 0, ChordTypeLevel.Triads)))));
    body.appendChild(section("Minor (diatonic)", "Fixed key A minor.",
      MINOR_PROGRESSIONS.map((p) => row(`min:${p.degrees.join(",")}`, romanLineFor(p), songsForDiatonic(p),
        resolveProgression(p, 9, ChordTypeLevel.Triads)))));
    body.appendChild(section("Advanced (non-diatonic)", "Characteristic examples — the signature harmonic move, not always note-for-note.",
      ADVANCED_PROGRESSIONS.map((p) => row(`adv:${p.name}`, `${p.name}:  ${namedRomanLine(p)}`, songsForAdvanced(p.name),
        resolveNamed(p, p.tonicMode === TrainingMode.Major ? 0 : 9)))));
    body.appendChild(section("Advanced II (maj7 / min9 / modal)", "Extended and modal colours — seventh chords and modal vamps.",
      ADVANCED2_PROGRESSIONS.map((p) => row(`adv2:${p.name}`, `${p.name}:  ${namedRomanLine(p)}`, songsForAdvanced(p.name),
        resolveNamed(p, p.tonicMode === TrainingMode.Major ? 0 : 9)))));
    body.appendChild(section("Suspended (sus2 / sus4)", "The tension-and-release of suspended chords.",
      SUS_PROGRESSIONS.map((p) => row(`sus:${p.name}`, `${p.name}:  ${namedRomanLine(p)}`, songsForAdvanced(p.name),
        resolveNamed(p, p.tonicMode === TrainingMode.Major ? 0 : 9)))));
    body.appendChild(section("Circle of fifths", "Draws 4 adjacent chords; the 2nd may become a dominant 7th (except vii°). Characteristic examples.",
      CIRCLE_WINDOWS.map((w) => row(`cof:${w.id}`, w.romanLine, songsForCircleWindow(w.id),
        resolveCircleWindow(w, 0)))));
    body.appendChild(el("div", { style: "text-align:right;margin-top:8px" }, [btn("Close", close, "btn primary")]));

    body.addEventListener("click", (e) => e.stopPropagation());
    // Restore the preserved scroll position after this freshly-built card is mounted.
    requestAnimationFrame(() => { body.scrollTop = this.libScrollTop; });
    const scrim = el("div", { style: "position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;padding:16px;z-index:50" }, [body]);
    scrim.addEventListener("click", close);
    return scrim;
  }

  /** Best-effort E-shape (or first) voicing for a chord symbol, for the idle fretboard
   *  preview before playback starts. Null for unvoiceable/exotic chords or empty input. */
  private previewShapeWeb(symbol: string | undefined): ChordShape | null {
    if (!symbol) return null;
    const parsed = parseChord(symbol);
    if (!parsed) return null;
    const [root, q] = parsed;
    const shapes = new ChordShapeGenerator().shapesFor(root, q, this.state.liveTuning, DISPLAY_FRETS);
    return shapes.find((s) => s.cagedShape === CagedShape.E) ?? shapes[0] ?? null;
  }

  /** The library overlay's own follow-along fretboard (separate canvas from the main
   *  view's). While row [key] is playing it tracks `ear.libShape` live; otherwise it shows
   *  [idleShape]. The draw runs imperatively via `ear.libOnBar` on each preview bar, so the
   *  board follows playback WITHOUT a full-screen rerender (which would flash the overlay). */
  private libFretboard(key: string, idleShape: ChordShape | null): HTMLElement {
    const s = this.state, ear = this.ear;
    if (!this.libFbEl) {
      this.libFbEl = el("canvas", { class: "fretboard" });
      this.libFb = new FretboardCanvas(this.libFbEl);
    }
    const wrap = el("div", { style: "height:200px;position:relative;margin:6px 0" });
    wrap.appendChild(this.libFbEl);
    const draw = () => {
      const shape = ear.libPlayingId === key ? ear.libShape : idleShape;
      const marks = shape ? shapeMarks(shape, s.labelMode) : new Map();
      this.libFb!.setData({
        tuning: s.liveTuning, marks, selectedPosition: null, leftHanded: s.leftHanded,
        numFrets: DISPLAY_FRETS, playOnTouchDown: false, mutedStrings: new Set<number>(),
        onTap: (pos) => s.audio.playNote(noteAt(s.liveTuning, pos).midi, s.ringSustainMs),
      });
    };
    draw();
    ear.libOnBar = draw;  // re-armed here (cleared at the top of libraryOverlay each build)
    return wrap;
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

  /** "Show chord on fretboard" panel for a given chord [symbol] (#2/#3). */
  private chordFretboardPanel(parent: HTMLElement, symbol: string, show: boolean, onToggle: (v: boolean) => void): void {
    const s = this.state;
    parent.appendChild(switchRow("Show chord on fretboard", null, show, onToggle));
    if (!show) return;
    if (!this.fbCanvasEl) {
      this.fbCanvasEl = el("canvas", { class: "fretboard" });
      this.fb = new FretboardCanvas(this.fbCanvasEl);
    }
    const wrap = el("div", { style: "height:220px;position:relative;margin:6px 0" });
    wrap.appendChild(this.fbCanvasEl);
    parent.appendChild(wrap);
    let marks = new Map();
    const parsed = parseChord(symbol);
    if (parsed) {
      const [root, q] = parsed;
      const shapes = new ChordShapeGenerator().shapesFor(root, q, s.liveTuning, DISPLAY_FRETS);
      const shape = shapes.find((sh) => sh.cagedShape === CagedShape.E) ?? shapes[0];
      if (shape) {
        // Show ONLY the chord's own tones: some CAGED templates (e.g. the "dim" grip
        // is really a dim7 voicing) carry an extra note, which would otherwise render
        // a phantom extension on the neck for a plain triad.
        const chordPcs = new Set(notesFrom(q, root));
        const full = shapeMarks(shape, s.labelMode);
        shape.frets.forEach((f, str) => {
          if (f == null) return;
          const key = fpKey(fp(str, f));
          const mark = full.get(key);
          if (mark && chordPcs.has(midiPitchClass(noteAt(s.liveTuning, fp(str, f)).midi))) marks.set(key, mark);
        });
      }
    }
    this.fb!.setData({
      tuning: s.liveTuning, marks, selectedPosition: null, leftHanded: s.leftHanded,
      numFrets: DISPLAY_FRETS, playOnTouchDown: false, mutedStrings: new Set<number>(),
      onTap: (pos) => s.audio.playNote(noteAt(s.liveTuning, pos).midi, s.ringSustainMs),
    });
  }

  private challengeHeader(parent: HTMLElement, label: string, score: string, onRestart: () => void, onQuit: () => void): void {
    parent.appendChild(el("div", { class: "row" }, [
      el("div", { style: "flex:1;font-weight:600" }, [label]),
      el("div", { style: `color:var(--act);font-weight:600` }, [score]),
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
      btn("+", () => ear.transposeProgression(1)),
      el("span", { class: "et-muted" }, [transposeLabel(ear.progTranspose)]),
    ]);
  }

  /** Signal move: reveal cards first, then the action strip, then the
   *  generator summary card (tap to configure) — mirrors Android's
   *  ProgressionView ordering exactly. */
  /** "Songs ♪" button that pops a modal listing famous songs built on the CURRENT
   *  progression (library data). Used in Practice and Challenge, all generators. */
  private songsButton(): HTMLElement {
    return btn("Songs ♪", () => this.showSongsPopup());
  }

  private showSongsPopup(): void {
    // Curated hits first; PDF-imported extras fold behind a "Show more" button.
    const songs = this.ear.currentProgressionSongs();
    const extra = this.ear.currentProgressionImportedSongs();
    const songRow = (sg: SongExample) => songLinkRow(sg.title, sg.artist);
    const body = el("div", {});
    if (!songs.length && !extra.length) {
      body.appendChild(el("div", { class: "et-muted" }, ["No songs are listed for this progression yet."]));
    } else {
      songs.forEach((sg) => body.appendChild(songRow(sg)));
      if (extra.length) {
        const moreWrap = el("div", {});
        const moreBtn = btn(`Show ${extra.length} more from the songbook ▾`, () => {
          moreWrap.remove();
          if (songs.length) {
            body.appendChild(el("hr", { style: "border:none;border-top:1px solid var(--divider);margin:6px 0" }));
            body.appendChild(el("div", { class: "et-muted", style: "font-size:12px;padding:2px 0" }, ["More from the songbook"]));
          }
          extra.forEach((sg) => body.appendChild(songRow(sg)));
        }, "btn");
        moreWrap.appendChild(el("div", { style: "margin-top:6px" }, [moreBtn]));
        body.appendChild(moreWrap);
      }
    }
    const closeBtn = btn("Close", () => scrim.remove(), "btn primary");
    const card = el("div", { class: "et-card", style: "max-width:480px;max-height:75vh;overflow:auto;margin:auto;background:var(--surface-elev);color:var(--text-primary)" }, [
      el("div", { style: "font-weight:700;font-size:16px;margin-bottom:8px" }, ["Songs with this progression"]),
      body,
      el("div", { style: "text-align:right;margin-top:10px" }, [closeBtn]),
    ]);
    card.addEventListener("click", (e) => e.stopPropagation());
    const scrim = el("div", { style: "position:fixed;inset:0;background:rgba(0,0,0,0.6);display:flex;padding:16px;z-index:60" }, [card]);
    scrim.addEventListener("click", () => scrim.remove());
    document.body.appendChild(scrim);
  }

  private progressionView(parent: HTMLElement): void {
    const ear = this.ear;
    if (!ear.hasGenerated) {
      // Initial state: the summary card lets you dial in settings before the
      // first progression is generated (so it honors them from the start).
      parent.appendChild(this.generatorSummaryCard(() => this.openSettingsSheet()));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Generate progression ▶", () => ear.nextProgression(), "btn primary"));
      return;
    }

    // ---- Reveal cards first: Key & Mode hint, then the 4 bars ----
    parent.appendChild(this.revealCard("Key & Mode", !ear.keyRevealed,
      spellPc(ear.progKey) + "  " + (ear.progMode === TrainingMode.Major ? "Major" : "Minor"),
      () => ear.toggleKeyModeReveal(), false));
    parent.appendChild(el("div", { class: "v-gap-12" }));
    parent.appendChild(this.chordSlots());
    parent.appendChild(el("div", { class: "v-gap-8" }));

    // ---- Action strip directly under the cards ----
    // #7: Prev/Next get transparent red/green tints so they're clearly distinct from
    // the accent Play button (users misclicked Next for Play). ← Prev restores the
    // previously generated progression.
    const prevBtn = btn("← Prev progression", () => ear.previousProgression());
    prevBtn.disabled = !ear.canGoPrevProgression;
    prevBtn.style.background = "rgba(211,47,47,0.16)";
    prevBtn.style.borderColor = "rgba(211,47,47,0.5)";
    const nextBtn = btn("Next progression →", () => ear.nextProgression());
    nextBtn.style.background = "rgba(46,125,50,0.18)";
    nextBtn.style.borderColor = "rgba(46,125,50,0.5)";
    parent.appendChild(el("div", { class: "et-row-gap" }, [
      prevBtn,
      nextBtn,
      btn(`Hear ${ear.progCadenceLabel()}`, () => ear.playProgKeyCadence()),
      btn("→ Looper", () => this.onToLooper(ear.progResolved.map((rc) => rc.symbol))),
      this.songsButton(),
    ]));

    parent.appendChild(this.transposeRow());

    parent.appendChild(el("div", { class: "v-gap-12" }));
    parent.appendChild(this.generatorSummaryCard(() => this.openSettingsSheet()));

    parent.appendChild(el("div", { class: "v-gap-12" }));
    this.fretboardPanel(parent);
  }

  private chordSlots(): HTMLElement {
    const ear = this.ear;
    const row = el("div", { class: "et-slot-row" });
    for (let i = 0; i < 4; i++) {
      const resolved = ear.progResolved[i];
      const isCurrent = ear.isLooping && ear.currentBar === i;
      const hidden = !ear.progBarRevealed.has(i);
      const bg = isCurrent ? BG_PLAYHEAD : hidden ? BG_HIDDEN : BG_REVEAL;
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
    const ear = this.ear;
    if (!ear.challengeActive) {
      parent.appendChild(el("div", { class: "et-muted" }, [`A challenge is ${ear.challengeTotal} progressions in a row. Listen, then tap the correct Roman numeral for each bar (and its extension when shown). Each question auto-scores; your total appears at the end.`]));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(this.generatorSummaryCard(() => this.openSettingsSheet()));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Start challenge ▶", () => ear.startChallenge(), "btn primary"));
      return;
    }
    if (ear.challengeIndex >= ear.challengeTotal) {
      this.challengeDone(parent);
      return;
    }

    // A fresh question lands the fixed answer pad back on bar 1.
    if (this.lastChallengeIndexForPad !== ear.challengeIndex) {
      this.lastChallengeIndexForPad = ear.challengeIndex;
      this.challengeSelectedBar = 0;
      this.padPickedBar = null;
    }

    // ---- Progress ring + per-question dot strip (Signal move — replaces the
    // old "Question n/N · Score · Restart · Quit" row; Restart/Quit are now
    // pinned icon buttons in the screen header, and "Q n/N" lives in the ring). ----
    parent.appendChild(el("div", { class: "row", style: "align-items:center;gap:14px" }, [
      this.challengeProgressRing(ear.challengeIndex, ear.challengeTotal),
      el("div", { style: "flex:1;min-width:0" }, [
        el("div", { style: `font-weight:600;color:var(--act)` }, [`Score: ${ear.challengeBarScore()} / ${ear.challengeBarTotal()} bars`]),
        el("div", { class: "v-gap-8" }),
        this.challengeDotStrip(),
      ]),
    ]));

    parent.appendChild(el("div", { class: "v-gap-8" }));

    // Question navigation: Prev = reddish pill, Next = greenish pill (filled like the
    // Challenge segment button, tinted) so Next is never mistaken for it and Prev is
    // clearly visible (the old plain-dark ← Prev was nearly invisible). Pinned up top
    // AND repeated at the bottom (below the answer pad) so Prev is reachable there too.
    const tintRed = (b: HTMLElement) => { b.style.background = "#c0392b"; b.style.color = "#fff"; b.style.border = "none"; };
    const tintGreen = (b: HTMLElement) => { b.style.background = "#2e9e4f"; b.style.color = "#fff"; b.style.border = "none"; };
    const lastQ = ear.challengeIndex === ear.challengeTotal - 1;
    const prevBtn = btn("← Prev", () => ear.previousChallengeQuestion(), "btn");
    prevBtn.style.flex = "1"; tintRed(prevBtn);
    if (!ear.canGoPrevChallenge) prevBtn.disabled = true;
    const nextTopBtn = btn(lastQ ? "See score →" : "Next →", () => ear.advanceChallenge(), "btn");
    nextTopBtn.style.flex = "1"; tintGreen(nextTopBtn);
    parent.appendChild(el("div", { class: "row", style: "gap:8px" }, [prevBtn, nextTopBtn]));

    // Tools row: Hear the cadence · Re-roll · Transpose (Signal move — one row).
    parent.appendChild(el("div", { class: "et-row-gap" }, [
      btn(`Hear ${ear.progCadenceLabel()}`, () => ear.playProgKeyCadence()),
      btn("Re-roll", () => ear.rerollChallengeQuestion()),
      this.songsButton(),
    ]));
    // Transpose shifts the key/chords but not the degrees, so it's safe in the challenge.
    parent.appendChild(this.transposeRow());
    parent.appendChild(this.revealCard("Key & Mode (hint)", !ear.keyRevealed,
      spellPc(ear.progKey) + "  " + (ear.progMode === TrainingMode.Major ? "Major" : "Minor"),
      () => ear.toggleKeyModeReveal(), false));

    parent.appendChild(labelSm("Hear the degrees  (reference — plays in the hidden key)"));
    parent.appendChild(el("div", { class: "et-row-gap" }, ear.challengeReferenceLabels().map(([deg, label]) => btn(`▶ ${label}`, () => ear.auditionProgDegree(deg)))));

    // #6/Signal: fixed answer pad — tap a bar square to target it, then answer
    // it from the always-visible pad below (replaces the old popup keyboard;
    // the per-bar ▶ Play and reference palette above are the only things that
    // sound — selecting a bar / a key is silent).
    parent.appendChild(labelSm("Fill each bar  (tap a square to select it, then tap its chord below)"));
    const sqRow = el("div", { class: "et-slot-row" });
    for (let i = 0; i < 4; i++) sqRow.appendChild(this.barSquare(i, this.challengeSelectedBar, () => { this.challengeSelectedBar = i; this.rerender(); }));
    parent.appendChild(sqRow);
    parent.appendChild(el("div", { class: "v-gap-8" }));
    parent.appendChild(this.challengeAnswerPad(this.challengeSelectedBar));

    parent.appendChild(el("div", { class: "v-gap-8" }));
    // Bottom nav: reddish Prev + greenish Next question (same tinted-pill styling as the
    // top nav) so Prev is present and visible at the bottom of the page too.
    const prevBottom = btn("← Prev question", () => ear.previousChallengeQuestion(), "btn");
    prevBottom.style.flex = "1"; tintRed(prevBottom);
    if (!ear.canGoPrevChallenge) prevBottom.disabled = true;
    const nextBottom = btn(lastQ ? "See score →" : "Next question →", () => ear.advanceChallenge(), "btn");
    nextBottom.style.flex = "1"; tintGreen(nextBottom);
    parent.appendChild(el("div", { class: "row", style: "gap:8px" }, [prevBottom, nextBottom]));
    parent.appendChild(el("div", { class: "et-muted", style: "margin-top:2px" }, ["Unanswered bars count as correct."]));
    parent.appendChild(el("div", { class: "v-gap-12" }));
    this.fretboardPanel(parent);
  }

  /** One bar's answer square: a tappable tile targeting the fixed answer pad
   *  below it, showing the chosen chord label (or "?") plus a ▶ to hear the
   *  bar. [selected] marks the bar the pad currently answers for. */
  private barSquare(i: number, selectedBar: number, onSelect: () => void): HTMLElement {
    const ear = this.ear;
    const verdict = ear.challengeBarCorrect(i);
    const selected = selectedBar === i;
    const playhead = ear.isLooping && ear.currentBar === i;   // playing "head" highlight
    const label = ear.challengeGuessLabel[i];
    const border = verdict === true ? "var(--act)" : verdict === false ? "var(--root-tone)" : selected ? "var(--act)" : "var(--line)";
    // Playhead (teal bg + teal ring) is deliberately a different hue from the
    // coral selection border, so a selected bar the playhead is on shows BOTH.
    const bg = playhead ? BG_PLAYHEAD : label == null ? BG_HIDDEN : "var(--surface2)";
    const box = el("div", {
      class: "et-barsq",
      style: `border-color:${border};border-width:${selected && verdict === null ? "3px" : "2px"};background:${bg}${playhead ? ";box-shadow:0 0 0 3px var(--feedback)" : ""}`,
    }, [label ?? "?"]);
    box.addEventListener("click", onSelect);
    const col = el("div", { class: "et-slot" }, [
      el("div", { class: "ans-label" }, [`Bar ${i + 1}`]),
      box,
      // Playing a bar also selects it, so it becomes the target of the answer keyboard.
      btn("▶", () => { onSelect(); ear.playBarOnce(i); }),
    ]);
    if (verdict !== null) {
      const answer = ear.progResolved[i]?.romanLabel ?? "";
      col.appendChild(el("div", {
        style: `font-size:11px;font-weight:600;margin-top:2px;color:${verdict ? "var(--act)" : "var(--root-tone)"}`,
      }, [verdict ? "✔" : `✘ ${answer}`]));
    }
    return col;
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
      const color = a === true ? "var(--act)" : a === false ? "var(--root-tone)" : "var(--line)";
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
    // the reveal card below shows the full answer. The button for the bar the
    // loop is currently sounding gets a playing-"head" highlight (mirrors the
    // diatonic chordSlots / barSquare treatment).
    parent.appendChild(el("div", { class: "et-row-gap" }, ear.progResolved.map((_, i) => {
      const b = btn(`▶ ${i + 1}`, () => ear.playProgChordDirect(i));
      if (ear.isLooping && ear.currentBar === i) {
        b.style.background = BG_PLAYHEAD;
        b.style.boxShadow = "0 0 0 3px var(--feedback)";
        b.style.fontWeight = "700";
      }
      return b;
    })));
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
    // Key picker + generator choice + Library now live behind the summary card
    // (Signal move — same treatment as the diatonic Progressions view).
    parent.appendChild(el("div", { class: "v-gap-8" }));
    parent.appendChild(this.generatorSummaryCard(() => this.openSettingsSheet()));
    if (!ear.advProg) {
      parent.appendChild(el("div", { class: "v-gap-8" }));
      parent.appendChild(btn("Generate progression ▶", () => ear.nextAdvancedProgression(), "btn primary"));
      return;
    }
    // #7: ← Prev (left of Next) restores the previously generated progression.
    const advPrevBtn = btn("← Prev", () => ear.previousAdvancedProgression());
    advPrevBtn.disabled = !ear.canGoPrevAdvanced;
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:8px" }, [
      advPrevBtn,
      btn("Next →", () => ear.nextAdvancedProgression()),
      this.songsButton(),
    ]));
    parent.appendChild(this.transposeRow());
    parent.appendChild(el("div", { class: "v-gap-8" }));
    this.advancedBody(parent);
  }

  private advancedChallenge(parent: HTMLElement): void {
    const ear = this.ear;
    if (!ear.advChActive) {
      parent.appendChild(el("div", { class: "et-muted" }, [`${ear.advChallengeTotal} advanced progressions in a row. Listen, try to identify each, then reveal and mark yourself. A teaching note is shown for every one.`]));
      parent.appendChild(el("div", { class: "v-gap-8" }));
      parent.appendChild(this.generatorSummaryCard(() => this.openSettingsSheet()));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Start challenge ▶", () => ear.startAdvChallenge(), "btn primary"));
      return;
    }
    if (ear.advChIndex >= ear.advChallengeTotal) {
      this.simpleDone(parent, ear.advChScore, ear.advChallengeTotal, () => ear.startAdvChallenge(), () => ear.exitAdvChallenge());
      return;
    }
    // Restart/Quit are pinned in the screen header while this challenge is in
    // flight (see render()'s progChallengeInFlight) — this row is just the label + score.
    parent.appendChild(this.challengeScoreRow(`Progression ${ear.advChIndex + 1} / ${ear.advChallengeTotal}`, `Score: ${ear.advChScore}`));
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
      parent.appendChild(this.songsButton());
    }
  }

  // ---------- Note2Chord ----------

  private n2cView(parent: HTMLElement): void {
    const ear = this.ear;
    const c = ear.n2cChallenge;
    parent.appendChild(el("div", { class: "et-muted" }, ["A triad plays, then a single note from its diatonic scale sounds above. Identify the test note's degree relative to the chord (e.g. 9, b7, maj7)."]));
    const replayN2c = btn(ear.n2cPlaying ? "Playing…" : "Replay both ▶", () => ear.playN2c(), "btn primary");
    const prevN2c = btn("◀ Prev", () => { ear.n2cPrev(); ear.playN2c(); }); if (!ear.n2cHasPrev) prevN2c.disabled = true;
    const nextN2c = btn("Next ▶", () => { ear.n2cNext(); ear.playN2c(); }); if (!ear.n2cHasNext) nextN2c.disabled = true;
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      replayN2c, prevN2c, nextN2c, btn("New +", () => { ear.nextN2cChallenge(); ear.playN2c(); }),
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
    if (c) {
      parent.appendChild(el("div", { class: "v-gap-12" }));
      this.chordFretboardPanel(parent, n2cChordSymbol(c), ear.n2cShowFretboard, (v) => ear.setN2cShowFretboard(v));
    }
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
    parent.appendChild(labelSm("Flavor  (only diatonic flavors for this key)"));
    // #4: only flavors diatonic to the current key/mode (narrowed to the guessed degree).
    parent.appendChild(chipsRow(ear.flavorQualityOptions(ear.flavorGuessDegree).map((sym) =>
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
    parent.appendChild(el("div", { class: "v-gap-12" }));
    this.chordFretboardPanel(parent, ear.flavorChordSymbol(), ear.flavorShowFretboard, (v) => ear.setFlavorShowFretboard(v));
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
    parent.appendChild(labelSm("Flavor  (only diatonic flavors for this key)"));
    parent.appendChild(chipsRow(ear.flavorQualityOptions(ear.flavorGuessDegree).map((sym) =>
      chip(sym === "" ? "maj" : sym, ear.flavorGuessQuality === sym, () => ear.setFlavorGuessQuality(sym), !ear.flavorChAnswered))));
    parent.appendChild(el("div", { class: "v-gap-8" }));
    if (!ear.flavorChAnswered) {
      const b = btn("Submit", () => ear.submitFlavorGuess(), "btn primary");
      if (ear.flavorGuessDegree == null || ear.flavorGuessQuality == null) b.disabled = true;
      parent.appendChild(b);
    } else {
      const degOk = ear.flavorGuessDegree === ear.flavorDegree;
      const qualOk = ear.flavorGuessQuality === ear.flavorQuality;
      parent.appendChild(el("div", { style: `font-weight:700;color:var(--act)` }, [`Answer: degree ${ear.flavorDegree} (${ear.flavorDegreeRoman()}) · ${ear.flavorQuality === "" ? "maj" : ear.flavorQuality}  [${ear.flavorChordSymbol()}, ${ear.flavorMode === TrainingMode.Major ? "major" : "minor"}]`]));
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
    const replay = btn(ear.invPlaying ? "Playing…" : "Replay ▶", () => ear.playInversion(), "btn primary");
    if (!ear.invStarted || ear.invPlaying) replay.disabled = true;
    const prev = btn("◀ Prev", () => ear.inversionPrev()); if (!ear.invHasPrev) prev.disabled = true;
    const next = btn("Next ▶", () => ear.inversionNext()); if (!ear.invHasNext) next.disabled = true;
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      replay, prev, next, btn("New chord +", () => ear.newInversion()),
    ]));
    if (!ear.invStarted) return;
    this.invGuessChips(parent, true);
    parent.appendChild(this.revealCard("Answer", !ear.invRevealed,
      inversionName(ear.invInversion) + "  ·  " + spellPc(ear.invRoot) + ear.invQuality,
      () => ear.toggleInvReveal(), false));
    if (ear.invRevealed && ear.invGuess !== null) {
      parent.appendChild(el("div", { style: "font-weight:600;margin-top:6px" }, [ear.invGuess === ear.invInversion ? "✔ correct" : `✘ that was the ${inversionName(ear.invGuess).toLowerCase()}`]));
    }
    parent.appendChild(el("div", { class: "v-gap-12" }));
    this.chordFretboardPanel(parent, spellPc(ear.invRoot) + ear.invQuality, ear.invShowFretboard,
      (v) => ear.setInvShowFretboard(v));
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
      parent.appendChild(el("div", { style: `font-weight:700;color:var(--act)` }, [`${ok ? "✔ correct" : `✘ answer: ${inversionName(ear.invInversion)}`}   (${spellPc(ear.invRoot)}${ear.invQuality})`]));
      parent.appendChild(btn(ear.invChIndex === ear.invChallengeTotal - 1 ? "See score →" : "Next →", () => ear.advanceInvChallenge(), "btn primary"));
      // Post-answer only: showing the chord earlier would leak the answer.
      parent.appendChild(el("div", { class: "v-gap-8" }));
      this.chordFretboardPanel(parent, spellPc(ear.invRoot) + ear.invQuality, ear.invShowFretboard,
        (v) => ear.setInvShowFretboard(v));
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
    // Replay is primary so it isn't confused with the chord-advancing buttons (#1).
    const replay = btn("Replay ▶", () => ear.playAugDim(), "btn primary");
    if (!ear.adStarted) replay.disabled = true;
    const prev = btn("◀ Prev", () => ear.augDimPrev()); if (!ear.adHasPrev) prev.disabled = true;
    const next = btn("Next ▶", () => ear.augDimNext()); if (!ear.adHasNext) next.disabled = true;
    parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:10px" }, [
      replay, prev, next, btn("New chord +", () => ear.newAugDim()),
    ]));
    if (!ear.adStarted) return;
    this.augDimGuessChips(parent, true);
    parent.appendChild(this.revealCard("Answer", !ear.adRevealed,
      spellPc(ear.adRoot) + ear.adQuality + "  ·  " + ear.augDimFamily(ear.adQuality),
      () => ear.toggleAugDimReveal(), false));
    if (ear.adRevealed && ear.adGuess !== null) {
      parent.appendChild(el("div", { style: "font-weight:600;margin-top:6px" }, [ear.adGuess === ear.adQuality ? "✔ correct" : `✘ it was ${this.augDimLabel(ear.adQuality)}`]));
    }
    parent.appendChild(el("div", { class: "v-gap-12" }));
    this.chordFretboardPanel(parent, spellPc(ear.adRoot) + ear.adQuality, ear.adShowFretboard,
      (v) => ear.setAdShowFretboard(v));
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
      parent.appendChild(el("div", { style: `font-weight:700;color:var(--act)` }, [`${ok ? "✔ correct" : `✘ answer: ${this.augDimLabel(ear.adQuality)}`}   (${spellPc(ear.adRoot)}${ear.adQuality})`]));
      parent.appendChild(btn(ear.adChIndex === ear.augDimChallengeTotal - 1 ? "See score →" : "Next →", () => ear.advanceAugDimChallenge(), "btn primary"));
      // Post-answer only: showing the chord earlier would leak the answer.
      parent.appendChild(el("div", { class: "v-gap-8" }));
      this.chordFretboardPanel(parent, spellPc(ear.adRoot) + ear.adQuality, ear.adShowFretboard,
        (v) => ear.setAdShowFretboard(v));
    }
  }

  // ---------- #6 Interval identification ----------

  private intervalsView(parent: HTMLElement): void {
    const ear = this.ear;
    if (!ear.intervalChActive) {
      parent.appendChild(el("div", { class: "et-muted" }, [
        `${ear.intervalChallengeTotal} questions. A I–V–I cadence sets the key, then the tonic and a note sound — identify the interval. Choose a direction first; you can replay the tonic and transpose anytime.`,
      ]));
      parent.appendChild(labelSm("Direction"));
      parent.appendChild(chipsRow([IntervalDirection.Ascending, IntervalDirection.Descending, IntervalDirection.Mixed].map((d) =>
        chip(d, ear.intervalDirection === d, () => ear.setIntervalDirection(d)))));
      parent.appendChild(labelSm("Playback"));
      parent.appendChild(chipsRow([
        chip("Melodic (one after the other)", !ear.intervalHarmonic, () => ear.setIntervalHarmonic(false)),
        chip("Harmonic (together)", ear.intervalHarmonic, () => ear.setIntervalHarmonic(true)),
      ]));
      parent.appendChild(el("div", { class: "et-row-gap", style: "margin-top:8px" }, [
        el("span", { class: "ans-label" }, [`Key: ${spellPc(ear.intervalKey)} major`]),
        btn("♭", () => ear.intervalTranspose(-1)),
        btn("♯", () => ear.intervalTranspose(1)),
        el("span", { class: "et-muted" }, [transposeLabel(ear.intervalTransposeSteps)]),
      ]));
      parent.appendChild(el("div", { class: "v-gap-12" }));
      parent.appendChild(btn("Start challenge ▶", () => ear.startIntervalChallenge(), "btn primary"));
      return;
    }
    if (ear.intervalChIndex >= ear.intervalChallengeTotal) {
      this.simpleDone(parent, ear.intervalChScore, ear.intervalChallengeTotal,
        () => ear.startIntervalChallenge(), () => ear.exitIntervalChallenge());
      return;
    }
    this.challengeHeader(parent, `Q ${ear.intervalChIndex + 1} / ${ear.intervalChallengeTotal}`, `Score: ${ear.intervalChScore}`,
      () => ear.startIntervalChallenge(), () => ear.exitIntervalChallenge());
    const replay = btn("Replay ▶", () => ear.playIntervalQuestion(), "btn primary");
    if (ear.intervalPlaying) replay.disabled = true;
    parent.appendChild(el("div", { class: "et-row-gap" }, [
      replay,
      btn("♪ Tonic", () => ear.playIntervalTonic()),
      btn("Hear I–V–I", () => ear.playIntervalTonicCadence()),
      btn("♭", () => ear.intervalTranspose(-1)),
      btn("♯", () => ear.intervalTranspose(1)),
      el("span", { class: "et-muted" }, [transposeLabel(ear.intervalTransposeSteps)]),
    ]));
    parent.appendChild(el("div", { class: "v-gap-8" }));
    parent.appendChild(labelSm("Which interval?"));
    parent.appendChild(chipsRow(INTERVAL_CHOICES.map((iv) =>
      chip(iv.shortName, ear.intervalGuess === iv.semitones, () => ear.setIntervalGuess(iv.semitones), !ear.intervalChAnswered))));
    parent.appendChild(el("div", { class: "v-gap-8" }));
    if (!ear.intervalChAnswered) {
      const b = btn("Submit", () => ear.submitIntervalGuess(), "btn primary");
      if (ear.intervalGuess == null) b.disabled = true;
      parent.appendChild(b);
    } else {
      const ok = ear.intervalGuess === ear.intervalSemitones;
      const dir = ear.intervalAscending ? "ascending" : "descending";
      parent.appendChild(el("div", { style: `font-weight:700;color:var(--act)` }, [
        `${ok ? "✔ correct" : `✘ answer: ${intervalChoiceFor(ear.intervalSemitones).longName}`}  (${dir})`,
      ]));
      parent.appendChild(btn(ear.intervalChIndex === ear.intervalChallengeTotal - 1 ? "See score →" : "Next →",
        () => ear.advanceIntervalChallenge(), "btn primary"));
    }
  }
}

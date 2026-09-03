// Transport dock + Tone sheet (Signal moves #2/#3) — shared chrome for the
// Ear / Rhythm / Loop screens, mirroring app/.../Transport.kt (TransportDock +
// ToneSheet) exactly. See docs/superpowers/specs/2026-07-10-signal-gui-redesign-design.md
// §moves 2+3. Consolidates what used to be scattered per-screen Play/Stop
// buttons, BPM sliders, and the old "🎚 Sound" popup (audioControl.ts, now
// deleted) into one persistent pill (transportDock) plus one shared bottom
// sheet (toneSheet), both driven purely by the AppState/EarTrainingState each
// caller already owns — no playback logic lives here.

import { AppState, EqBand, SoundName } from "./appState";
import { EarTrainingState } from "./earTrainingState";
import { el, slider, valueSlider, switchRow } from "./dom";
import { icon, IconName } from "./icons";

export interface TransportDockOpts {
  playing: boolean;
  onPlayStop: () => void;
  /** Omit both bpm and onBpm for a screen with no tempo concept — the whole
   *  BPM block (readout + slider popover) is left out. */
  bpm?: number;
  onBpm?: (v: number) => void;
  /** Show the BPM as an always-visible inline readout + slider (no popover) —
   *  used by the drum machine so tempo needs no extra tap. */
  inlineBpm?: boolean;
  /** Master output level 0..1. Pass both to add an always-visible 🔊 fader to
   *  the dock (drum machine only — every other screen omits it). */
  volume?: number;
  onVolume?: (v: number) => void;
  toneLabel: string;
  onTone: () => void;
}

/**
 * Persistent pill: a round act-filled Play/Stop button (the dock's one
 * primary action), an optional BPM readout that opens a slider popover on tap
 * (a native <details>/<summary> — the app's press-guard, see ui.ts
 * setupPressGuard, defers any rebuild while the slider is being dragged, so
 * the popover survives the whole gesture and simply closes on the next full
 * rerender after release), a spacer, and a teal-outlined tone chip opening
 * [toneSheet]. Holds no playback logic of its own — each caller wires
 * onPlayStop/onBpm to its own loop state.
 */
export function transportDock(opts: TransportDockOpts): HTMLElement {
  const playBtn = el("button", { class: "transport-play" }, [icon(opts.playing ? "stop" : "play", 20)]);
  playBtn.setAttribute("aria-label", opts.playing ? "Stop" : "Play");
  playBtn.addEventListener("click", opts.onPlayStop);

  const children: HTMLElement[] = [playBtn];

  if (opts.bpm !== undefined && opts.onBpm) {
    const bpm = opts.bpm, onBpm = opts.onBpm;
    // Compact circular −/+ tempo stepper (±5 BPM per press).
    const bpmStep = (input: HTMLInputElement, label: HTMLElement, sym: string, delta: number) => {
      const b = el("button", {
        class: "bpm-step",
        "aria-label": delta < 0 ? "slower" : "faster",
        style: "min-width:30px;height:30px;border-radius:999px;border:1px solid var(--outline);background:transparent;color:var(--act);font-weight:700;font-size:17px;line-height:1;cursor:pointer;flex:0 0 auto",
      }, [sym]);
      b.addEventListener("click", () => {
        const v = Math.min(Math.max(Math.round(parseFloat(input.value)) + delta, 10), 300);
        input.value = String(v);
        label.textContent = String(v);
        onBpm(v);
      });
      return b;
    };
    if (opts.inlineBpm) {
      // Always-visible readout + slider, no popover (drum machine). The readout
      // updates live during the drag; double-click it to type a tempo.
      const vs = valueSlider((v) => String(Math.round(v)), 10, 300, bpm, (v) => onBpm(Math.round(v)));
      vs.label.className = "transport-bpm-val";
      const readout = el("div", { class: "transport-bpm transport-bpm-inline" }, [
        vs.label,
        el("span", { class: "transport-bpm-unit" }, ["BPM"]),
      ]);
      vs.input.classList.add("transport-bpm-slider");
      // Both steppers sit next to the BPM caption; the slider follows.
      children.push(readout, bpmStep(vs.input, vs.label, "−", -5), bpmStep(vs.input, vs.label, "+", +5), vs.input);
    } else {
      // Both the summary readout and the popover label follow the drag live;
      // double-click either to type a tempo.
      const val = el("span", { class: "transport-bpm-val", title: "double-click to type a value" }, [String(bpm)]);
      const lab = el("div", { class: "label-sm", style: "margin-top:0", title: "double-click to type a value" }, [`Tempo: ${bpm} BPM`]);
      const s = slider(10, 300, bpm, (v) => onBpm(Math.round(v)), 1, (v) => {
        val.textContent = String(Math.round(v));
        lab.textContent = `Tempo: ${Math.round(v)} BPM`;
      });
      const typeIn = () => {
        const raw = window.prompt("Tempo (BPM):", s.value);
        if (raw === null) return;
        const n = Math.round(parseFloat(raw.replace(",", ".")));
        if (Number.isNaN(n)) return;
        const v = Math.min(Math.max(n, 10), 300);
        s.value = String(v);
        val.textContent = String(v);
        lab.textContent = `Tempo: ${v} BPM`;
        onBpm(v);
      };
      lab.addEventListener("dblclick", typeIn);
      val.addEventListener("dblclick", typeIn);
      const summary = el("summary", { class: "transport-bpm" }, [
        val,
        el("span", { class: "transport-bpm-unit" }, ["BPM"]),
      ]);
      const popStep = (sym: string, delta: number) => {
        const b = el("button", {
          class: "bpm-step",
          "aria-label": delta < 0 ? "slower" : "faster",
          style: "min-width:30px;height:30px;border-radius:999px;border:1px solid var(--outline);background:transparent;color:var(--act);font-weight:700;font-size:17px;line-height:1;cursor:pointer;flex:0 0 auto",
        }, [sym]);
        b.addEventListener("click", () => {
          const v = Math.min(Math.max(Math.round(parseFloat(s.value)) + delta, 10), 300);
          s.value = String(v); val.textContent = String(v); lab.textContent = `Tempo: ${v} BPM`; onBpm(v);
        });
        return b;
      };
      s.style.flex = "1";
      // Both steppers next to the tempo caption; the slider on its own row below.
      const labRow = el("div", { style: "display:flex;align-items:center;gap:8px" }, [lab, popStep("−", -5), popStep("+", +5)]);
      const pop = el("div", { class: "transport-bpm-pop" }, [labRow, el("div", { style: "margin-top:6px;display:flex" }, [s])]);
      const details = el("details", { class: "transport-bpm-wrap" }, [summary, pop]);
      details.open = bpmExpanded;
      details.addEventListener("toggle", () => { bpmExpanded = details.open; });
      children.push(details);
    }
  }

  // Master output fader (drum machine): always visible next to the tempo, since
  // it is the control you reach for while the loop is running.
  if (opts.volume !== undefined && opts.onVolume) {
    const onVolume = opts.onVolume;
    const vvs = valueSlider((v) => `${Math.round(v)}%`, 0, 100, Math.round(opts.volume * 100),
      (v) => onVolume(Math.round(v) / 100));
    vvs.label.className = "transport-vol-val";
    vvs.input.classList.add("transport-vol-slider");
    children.push(el("div", { class: "transport-vol" }, [
      el("span", { class: "transport-vol-icon", title: "Master volume" }, ["🔊"]),
      vvs.input,
      vvs.label,
    ]));
  }

  children.push(el("div", { class: "spacer" }));

  const toneChip = el("button", { class: "tone-chip" }, [opts.toneLabel]);
  toneChip.addEventListener("click", opts.onTone);
  children.push(toneChip);

  return el("div", { class: "transport" }, children);
}

// ---------- Tone sheet ----------

const SOUND_OPTIONS: SoundName[] = ["Synth", "Acoustic", "Nylon", "Electric"];

/** Whether the Transport BPM popover is expanded. Module-level (not a class
 *  field) because only one dock is ever visible at a time — a single persisted
 *  flag survives the frequent full-app rerenders that happen while playback
 *  rebuilds the DOM. */
let bpmExpanded = false;

/** Whether the Tone sheet's EQ row is expanded. Module-level (not a class
 *  field) because only one Tone sheet is ever open at a time across every
 *  screen that can open it — a single persisted flag survives the frequent
 *  full-app rerenders that happen while a screen's loop plays behind the
 *  sheet (mirrors the `this.playbackOpen` pattern in earTrainingUI.ts, just
 *  at module scope since toneSheet() is a plain function, not a class). */
let eqExpanded = false;

/** Sound picker: a full-width 4-way row where the selected sound is filled
 *  solid with the act color + on-act (dark-on-act) text — deliberately NOT
 *  the shared `.seg`/`chip` styling used elsewhere, which is a lighter tint +
 *  act-colored text (see the T1 ledger note in .superpowers/sdd/progress-signal.md
 *  re: onAct-style text contrast). */
function soundSegmented(state: AppState): HTMLElement {
  const row = el("div", { class: "tone-sound-row" });
  for (const name of SOUND_OPTIONS) {
    const selected = state.sound === name;
    const b = el("button", { class: selected ? "tone-sound-btn selected" : "tone-sound-btn" }, [name]);
    b.addEventListener("click", () => state.setSound(name));
    row.appendChild(b);
  }
  return row;
}

function toneRow(iconName: IconName, content: HTMLElement[]): HTMLElement {
  return el("div", { class: "tone-sheet-row" }, [icon(iconName, 20), el("div", { class: "tone-row-body" }, content)]);
}

/** Icon + label/value row with a slider directly beneath — Reverb, Strum
 *  spread, Ring sustain. The value follows the drag live; double-click it to
 *  type a number. */
function sliderRow(
  iconName: IconName, label: string, fmt: (v: number) => string, value: number,
  min: number, max: number, onChange: (v: number) => void,
): HTMLElement {
  const vs = valueSlider(fmt, min, max, value, onChange);
  vs.label.className = "tone-row-val";
  return toneRow(iconName, [
    el("div", { class: "row" }, [
      el("div", { style: "flex:1" }, [label]),
      vs.label,
    ]),
    vs.input,
  ]);
}

/** Expandable EQ row: collapsed it's an icon + one-line summary; expanded it
 *  hosts the existing 3-band ±12dB sliders + a "Flat" reset (Task 3). */
function eqRow(state: AppState): HTMLElement {
  const e = state.eqFor(state.sound);
  const fmt = (db: number) => `${db > 0 ? "+" : ""}${Math.round(db)} dB`;
  const summary = el("summary", { class: "tone-eq-summary" }, [
    icon("eq", 20),
    el("div", { class: "tone-row-body" }, [
      el("div", {}, ["EQ"]),
      el("div", { class: "tone-row-val" }, [`Bass / Mid / Treble — ${state.sound}`]),
    ]),
    el("span", { class: "tone-eq-chevron" }, [icon("chevronDown", 18)]),
  ]);
  const bandSlider = (label: string, band: EqBand, value: number) => {
    const vs = valueSlider((v) => `${label}: ${fmt(v)}`, -12, 12, value, (v) => state.setEqBand(state.sound, band, v));
    return el("div", { style: "margin-top:6px" }, [
      el("div", { class: "label-sm", style: "margin:6px 0 2px" }, [vs.label]),
      vs.input,
    ]);
  };
  const resetBtn = el("button", { class: "btn text" }, ["Flat"]);
  resetBtn.addEventListener("click", () => state.resetEq(state.sound));
  const body = el("div", { class: "tone-eq-body" }, [
    bandSlider("Bass", "bass", e.bass),
    bandSlider("Mid", "mid", e.mid),
    bandSlider("Treble", "treble", e.treble),
    el("div", { class: "row end" }, [resetBtn]),
  ]);
  const details = el("details", { class: "tone-eq-wrap" }, [summary, body]);
  details.open = eqExpanded;
  details.addEventListener("toggle", () => { eqExpanded = details.open; });
  return details;
}

/**
 * The one Tone settings sheet, opened identically from every screen's dock
 * (or, on screens with no transport dock — Fretboard/Options, Tuner,
 * Decompose — a small tune-icon button). Replaces the old "🎚 Sound" popup
 * (audioControl.ts) entirely: Sound picker, EQ, Reverb, Strum spread, Ring
 * sustain, Boost root note, and the audio-engine A/B toggle all live here,
 * reading/writing the exact same AppState/EarTrainingState members the popup
 * did.
 */
export function toneSheet(state: AppState, ear: EarTrainingState, onClose: () => void): HTMLElement {
  const sheet = el("div", { class: "sheet tone-sheet" });
  sheet.appendChild(el("div", { class: "sheet-grabber" }));
  const closeBtn = el("button", { class: "btn text" }, ["✕"]);
  closeBtn.addEventListener("click", onClose);
  sheet.appendChild(el("div", { class: "sheet-header" }, [el("h2", {}, ["TONE"]), closeBtn]));

  sheet.appendChild(el("div", { class: "label-sm", style: "margin-top:0" }, ["Sound" + (state.soundLoading ? " (loading…)" : "")]));
  sheet.appendChild(soundSegmented(state));

  sheet.appendChild(el("div", { class: "divider-line" }));
  sheet.appendChild(eqRow(state));

  sheet.appendChild(el("div", { class: "divider-line" }));
  const reverbPct = Math.round(state.reverbFor(state.sound) * 100);
  sheet.appendChild(sliderRow("waves", "Reverb", (v) => `${Math.round(v)}%`, reverbPct, 0, 100,
    (v) => state.setReverb(state.sound, v / 100)));

  sheet.appendChild(el("div", { class: "divider-line" }));
  sheet.appendChild(sliderRow("spread", "Strum spread", (v) => (Math.round(v) === 0 ? "at once" : `${Math.round(v)} ms`),
    state.strumMs, 0, 150, (v) => state.setStrumMs(v)));

  sheet.appendChild(el("div", { class: "divider-line" }));
  sheet.appendChild(sliderRow("timer", "Ring sustain", (v) => `${(v / 1000).toFixed(1)} s`,
    state.ringSustainMs, 300, 4000, (v) => state.setRingSustainMs(v)));

  // These two switches mutate a plain field/the audio engine directly rather
  // than going through a `commit()`/notify() setter — the same as the old
  // audioControl.ts popup did. No explicit rerender is needed: the native
  // checkbox already reflects the click instantly, nothing else in the sheet
  // depends on either value, and the next full rerender (triggered by
  // whatever the user does next) will read the already-mutated object.
  sheet.appendChild(el("div", { class: "divider-line" }));
  sheet.appendChild(toneRow("note", [switchRow(
    "Boost root note", "Play each chord's root louder so it cuts through",
    ear.earBoostTonic, (v) => { ear.earBoostTonic = v; },
  )]));
  // The audio engine is always the modern chain now (toggle removed); the Synth
  // SOUND option remains in the sound picker.

  const scrim = el("div", { class: "sheet-scrim" }, [sheet]);
  scrim.addEventListener("click", (e) => { if (e.target === scrim) onClose(); });
  return scrim;
}

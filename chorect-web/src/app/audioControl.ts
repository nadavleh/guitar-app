// Shared "🎚 Sound" popup control — the guitar Sound picker (Synth / Acoustic /
// Nylon / Electric) plus per-sound tone EQ, reverb, strum/sustain feel, and the
// A/B engine toggle. Rendered in the main view AND the ear-training header so the
// sound menu is reachable from every section (mirrors Android's AudioQuickButton,
// which is already present app-wide).

import { AppState, SoundName } from "./appState";
import { el, btn, slider, labelSm, chipRow, switchRow } from "./dom";
import { Colors } from "./theme";

/** Bass/Mid/Treble sliders (±12 dB) for the selected Sound + Flat reset + reverb. */
function eqControls(s: AppState): HTMLElement {
  const e = s.eqFor(s.sound);
  const fmt = (db: number) => `${db > 0 ? "+" : ""}${db} dB`;
  return el("div", {}, [
    labelSm(`EQ — ${s.sound}`),
    el("div", {}, [`Bass: ${fmt(e.bass)}`]),
    slider(-12, 12, e.bass, (v) => s.setEqBand(s.sound, "bass", v)),
    el("div", {}, [`Mid: ${fmt(e.mid)}`]),
    slider(-12, 12, e.mid, (v) => s.setEqBand(s.sound, "mid", v)),
    el("div", {}, [`Treble: ${fmt(e.treble)}`]),
    slider(-12, 12, e.treble, (v) => s.setEqBand(s.sound, "treble", v)),
    el("div", { class: "row end" }, [btn("Flat", () => s.resetEq(s.sound))]),
    el("div", {}, [`Reverb: ${Math.round(s.reverbFor(s.sound) * 100)}%`]),
    slider(0, 100, Math.round(s.reverbFor(s.sound) * 100), (v) => s.setReverb(s.sound, v / 100)),
  ]);
}

function audioPanel(state: AppState, rerender: () => void): HTMLElement {
  return el("div", {
    style: "position:absolute;right:0;top:36px;z-index:20;background:" + Colors.surface +
      ";border:1px solid " + Colors.divider + ";border-radius:12px;padding:12px;width:260px;box-shadow:0 8px 30px rgba(0,0,0,0.5)",
  }, [
    labelSm("Audio feel"),
    el("div", {}, [state.strumMs === 0 ? "Strum spread: struck at once" : `Strum spread: ${state.strumMs} ms`]),
    slider(0, 150, state.strumMs, (v) => state.setStrumMs(v)),
    el("div", {}, [`Ring sustain: ${(state.ringSustainMs / 1000).toFixed(1)} s`]),
    slider(300, 4000, state.ringSustainMs, (v) => state.setRingSustainMs(v)),
    labelSm(state.soundLoading ? "Sound (loading…)" : "Sound"),
    chipRow<SoundName>(
      [
        { value: "Synth", label: "Synth" },
        { value: "Acoustic", label: "Acoustic" },
        { value: "Nylon", label: "Nylon" },
        { value: "Electric", label: "Electric" },
      ],
      (v) => v === state.sound,
      (v) => state.setSound(v),
    ),
    eqControls(state),
    // A/B engine toggle (temporary scaffolding, kept through the audio overhaul).
    switchRow(
      "Audio engine (A/B)",
      state.audio.useModern ? "New — voice graph + stereo bus + reverb" : "Old — legacy engine",
      state.audio.useModern,
      (v) => { state.audio.setUseModern(v); rerender(); },
    ),
  ]);
}

/**
 * The "🎚 Sound ▾" button plus its popup (when [isOpen]). [toggle] flips the
 * caller's open flag and rerenders; [rerender] repaints after the A/B engine
 * switch (which doesn't itself notify subscribers).
 */
export function audioControlButton(
  state: AppState,
  isOpen: boolean,
  toggle: () => void,
  rerender: () => void,
): HTMLElement {
  const wrap = el("div", { style: "position:relative" });
  wrap.appendChild(btn("🎚 Sound ▾", toggle, "btn text"));
  if (isOpen) wrap.appendChild(audioPanel(state, rerender));
  return wrap;
}

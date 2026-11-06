// Area "audio" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- audio

import { CarMode, PercussionCatalog, voiceCount } from "../../src/theory";
import { synthClick, clickAt } from "../../src/app/woodClick";
import { PluckedSynth, PitchDetector, analyzePitch, PercussionSynth, panGains, nearestRoot, pitchRate, renderCueBeep } from "../../src/audio";
import { check } from "./harness";

export function run(): void {

  // --- Pitch math: A4 @ 440 → MIDI 69, 0 cents ---
  const est = analyzePitch(440, 440);
  check("analyze(440) → midi 69", est.midi === 69);
  check("analyze(440) → ~0 cents", Math.abs(est.cents) < 0.01);

  // --- Karplus-Strong: bounded, finite output ---
  const synth = new PluckedSynth(44100);
  const buf = synth.synthesize(69, 0.5, 1, 0.997, 0.6);
  let bounded = true;
  for (const v of buf) if (!Number.isFinite(v) || Math.abs(v) > 0.61) bounded = false;
  check("synth output is finite & within amplitude", bounded && buf.length === 22050);

  // --- brightnessDecay default (1.0) is a provable no-op vs. the pre-existing 5-arg call ---
  const bufDefault = synth.synthesize(69, 0.5, 1, 0.997, 0.6, 1.0);
  check("brightnessDecay=1.0 is identical to omitting it", buf.length === bufDefault.length && buf.every((v, i) => v === bufDefault[i]));

  // --- Panner: constant-power stereo gains ---
  const [centerL, centerR] = panGains(0);
  check("panGains(0) center ≈ (0.7071, 0.7071)", Math.abs(centerL - 0.70710678) < 1e-6 && Math.abs(centerR - 0.70710678) < 1e-6);
  for (const p of [-1, -0.5, 0, 0.3, 1]) {
    const [l, r] = panGains(p);
    if (Math.abs(l * l + r * r - 1) >= 1e-9) { check(`panGains(${p}) constant power`, false); }
    else { check(`panGains(${p}) constant power`, true); }
  }
  const [hardLeftL, hardLeftR] = panGains(-1);
  check("panGains(-1) hard-left = (1, 0)", Math.abs(hardLeftL - 1) < 1e-9 && Math.abs(hardLeftR - 0) < 1e-9);

  // --- Sampled guitar voices: nearest-root selection + playback-rate math ---
  check("nearestRoot ties lower", nearestRoot([40, 44, 48], 42) === 40);
  check("pitchRate octave", pitchRate(72, 60) === 2);

  // --- YIN: detect a synthetic 220 Hz sine ---
  const sr = 44100;
  const sine = new Float32Array(2048);
  for (let i = 0; i < sine.length; i++) sine[i] = 0.8 * Math.sin((2 * Math.PI * 220 * i) / sr);
  const detected = new PitchDetector(sr).detect(sine);
  check("YIN detects 220 Hz sine within 2 Hz", detected !== null && Math.abs(detected - 220) < 2);

  // --- Percussion synth: every catalog voice renders bounded, finite audio ---
  const psynth = new PercussionSynth(44100);
  let psOk = true;
  for (const inst of PercussionCatalog.ALL) {
    for (let v = 0; v < voiceCount(inst); v++) {
      const buf = psynth.synthesize(inst, v);
      if (buf.length === 0) psOk = false;
      for (const x of buf) if (!Number.isFinite(x) || Math.abs(x) > 1.001) psOk = false;
    }
  }
  check("every percussion voice renders finite, bounded audio", psOk);

  // --- Car-mode cue beep: same envelope as Kotlin CueBeep ---
  {
    const beep = renderCueBeep(CarMode.BEEP_HZ, CarMode.BEEP_MS, 44100, CarMode.BEEP_PEAK, CarMode.BEEP_ATTACK_MS);
    check("cue beep length is the requested duration", beep.length === Math.trunc(44100 * 140 / 1000));
    check("cue beep stays finite and within peak",
      beep.every((s) => Number.isFinite(s) && Math.abs(s) <= CarMode.BEEP_PEAK + 1e-6));
    check("cue beep attacks from silence (no onset click)", Math.abs(beep[0]) < 0.02);
    check("cue beep decays away", Math.abs(beep[beep.length - 1]) < 0.05 * CarMode.BEEP_PEAK);
    let loudest = 0;
    for (let i = 1; i < beep.length; i++) if (Math.abs(beep[i]) > Math.abs(beep[loudest])) loudest = i;
    check("cue beep peaks early (attack then decay)", loudest < beep.length * 0.15);
    let crossings = 0;
    for (let i = 1; i < beep.length; i++) if ((beep[i - 1] < 0) !== (beep[i] < 0)) crossings++;
    const expected = Math.trunc(2 * CarMode.BEEP_HZ * CarMode.BEEP_MS / 1000);
    check(`cue beep really is ${CarMode.BEEP_HZ}Hz (${crossings} crossings, expected ~${expected})`,
      Math.abs(crossings - expected) <= 4);
    check("cue beep of zero length is empty, not a throw",
      renderCueBeep(CarMode.BEEP_HZ, 0, 44100, CarMode.BEEP_PEAK, CarMode.BEEP_ATTACK_MS).length === 0);
  }

  // --- Clicks must be synthesised at the ENGINE's rate, not a hard-coded 44100 ---
  // Web built every click at 44100 and then declared the buffer at ctx.sampleRate, so on a
  // 48 kHz device each click played ~147 cents sharp and 8.8 % short. Kotlin always threaded
  // audio.sampleRate through. `sr` is now a required parameter; these pin that.
  check("synthClick length follows the sample rate",
    synthClick(2000, 45, 48000).length === Math.floor(48000 * 45 / 1000) &&
    synthClick(2000, 45, 44100).length === Math.floor(44100 * 45 / 1000));
  check("clickAt memoises per (freq, ms, rate)",
    clickAt(2000, 45, 48000) === clickAt(2000, 45, 48000) &&
    clickAt(2000, 45, 48000) !== clickAt(2000, 45, 44100));
  check("PercussionSynth defaults to Kotlin's FALLBACK_RATE, not 44100",
    new PercussionSynth().sampleRate === 48000);
}

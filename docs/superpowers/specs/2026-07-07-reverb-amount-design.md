# Reverb Amount Control — Design

**Date:** 2026-07-07
**Status:** Approved (design settled; proceed to plan + implementation, review gate waived).
**Target version:** v1.26.0 (minor — new control).
**Builds on:** the v1.22.0 voice engine (per-voice `reverbSend` → Freeverb/Convolver) + the v1.24.0 per-instrument EQ (whose state/persistence/UI pattern this mirrors). Android + `chorect-web` parity.

## Locked decisions
- **Per-instrument** reverb amount (Synth / Acoustic / Nylon / Electric), like the EQ; follows the Sound picker.
- **What it scales:** the **per-voice reverb send** for the active sound. Guitar voices (note/chord/frequency) currently send `timbre.reverbSend` (≈0.18) to the reverb; this becomes a user-set per-sound value. Drums stay dry (send 0), unchanged.
- **Range/default:** slider **0–100%** → `reverbSend` 0.0–1.0, **default 18%** (matches current behavior). 0% = fully dry.
- **UI:** a single **Reverb** slider in the 🎚 Audio control, directly under the EQ sliders, acting on the currently-selected sound.
- **Persistence:** per-sound, Android DataStore (`guitar_reverb`), web localStorage; restored on startup and pushed to the engine.
- **Parity:** same behavior both platforms; modern engine only (legacy engine/A-B unaffected, like the EQ/reverb).

## Architecture (mirrors the EQ)
### Engine
- Android `AudioTrackEngine`: `@Volatile var voiceReverbSend: Float = 0.18f`; `fun setReverbSend(amount: Float)` sets it. `playNote`/`playFrequency`/`playChord` use `voiceReverbSend` for their voices' `reverbSend` in place of `timbre.reverbSend.toFloat()`. `playSamples`/`playSamplesAt` (drums) unchanged (send 0).
- Web `WebAudioEngine`: `private voiceReverbSend = 0.18`; `setReverbSend(amount)`; `playModernVoice`/`playModernSampleVoice` use `voiceReverbSend` for the per-voice reverb-send gain instead of `timbre.reverbSend`.

### State (`AppState`, mirroring EQ)
- Android: `EnumMap<GuitarSound, Float>` `reverb` (default **0.18** for all sounds), `reverbFor(s)`, `setReverb(s, amount)` (update map, bump `reverbVersion`, push if `s == sound`, persist `guitar_reverb`), `pushReverb(s)` guarded `s == sound` → `modernEngine.setReverbSend(...)`. Push on `setSound` (alongside `pushEq`), on slider edit, and on startup after restore. Encode/decode `Name,amount;...`.
- Web: `reverb: Record<SoundName, number>` (default 0.18, persisted), `setReverb`/`reverbFor`; `applySound(s)` also calls `audio.setReverbSend(reverb[s])`; startup restore + push.

### UI
- Android `AudioQuick.kt`: below the EQ sliders, a **Reverb** `Slider` (0f..1f, shown as `${(v*100).toInt()}%`) → `state.setReverb(state.sound, it)`; references `reverbVersion` to recompose.
- Web `ui.ts`: a `slider(0, 1, value, cb)` (or 0–100 scaled) labeled "Reverb <pct>%" under the EQ controls → `setReverb(sound, v)`.

## Testing
- Wiring only — no new DSP. Verify existing audio tests stay green (default 0.18 preserves current behavior). Confirm `assembleDebug` + web tsc/vite.
- Optional micro-check: `AudioTrackEngine.setReverbSend` changes the value used by subsequently-created voices (covered by build + on-device listen).

## Milestones
1. **Engine** — `voiceReverbSend` + `setReverbSend` (Android `AudioTrackEngine`, web `WebAudioEngine`); route voice reverb-send through it.
2. **State + persistence + UI (Android)** — per-sound `reverb` map, `setReverb`/`reverbFor`/`reverbVersion`, `pushReverb`, `guitar_reverb` persistence, Reverb slider in 🎚 Audio.
3. **Web mirror** — `setReverbSend`, per-sound reverb map + localStorage + slider.
4. **Ship v1.26.0** — tests, assembleDebug, commit, push, web CI. A/B toggle, Sound picker, EQ unchanged.

## Out of scope
- Global master-reverb wet/roomsize knob; reverb type/decay controls; reverb on the legacy engine.

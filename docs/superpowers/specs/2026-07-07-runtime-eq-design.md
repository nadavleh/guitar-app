# Runtime EQ — Design (per-instrument 3-band tone control)

**Date:** 2026-07-07
**Status:** Approved (user waived the review gate; proceed to plan + implementation).
**Target version:** v1.24.0 (minor — new feature).
**Builds on:** v1.22.0 voice engine + v1.23.x sampled instruments. Platforms: Android (`audio` module + app) + `chorect-web` (WebAudio), sonic parity.

## Context

A per-instrument runtime EQ so each guitar sound can be tone-shaped live, replacing the build-time baked nylon mid-cut with an adjustable one. Motivated by nylon reading "a bit muffled" — the user wants live tone control per sound.

### Locked decisions
- **Scope:** **per-instrument** EQ — each `GuitarSound` (Synth / Acoustic / Nylon / Electric) has its own EQ settings; the active sound's settings drive the bus EQ.
- **Bands:** **3-band** — low shelf @120 Hz, mid peak @700 Hz (Q≈0.9), high shelf @3500 Hz; each gain **±12 dB**, default 0 (flat = bypass).
- **Placement:** on the **modern** engine bus, **before the limiter** (after voices+reverb are summed), so boosts are caught by the limiter, not clipped. Modern engine only (matches sampled sounds / reverb); legacy A/B engine has no EQ.
- **One bus EQ, gains swapped per active sound** (only one sound plays at a time — no need for 4 EQ instances).
- **Baked nylon cut → live default:** rebuild nylon **flat** (drop it from the build tool's EQ) and ship Nylon's default `EqSettings` with **mid = −4 dB**, so it sounds identical out of the box but is now adjustable (single source of tone truth). All other sounds default flat.
- **UI:** Bass / Mid / Treble sliders in the **🎚 Audio** control, under the Sound picker, acting on the currently-selected sound; a **Flat** reset. Switching Sound repoints the sliders.
- **Persistence:** per-sound EQ persisted (Android DataStore, web localStorage); restored on startup and pushed to the engine.
- **No preset library** (YAGNI) — just per-sound persisted settings + defaults + Flat.

## Architecture

### DSP (Android `audio` module — pure Kotlin, JVM-testable)
```kotlin
data class EqSettings(val bassDb: Float = 0f, val midDb: Float = 0f, val trebleDb: Float = 0f)  // each in [-12, 12]

class ThreeBandEq(sampleRate: Int) {
    // RBJ biquads: low shelf @120Hz, mid peaking @700Hz (Q≈0.9), high shelf @3500Hz.
    // Stereo (independent per-channel filter state). setGainsDb recomputes coeffs.
    // When all gains == 0f it bypasses (output == input, no allocation, no CPU).
    fun setGainsDb(bass: Float, mid: Float, treble: Float)
    fun process(l: FloatArray, r: FloatArray, count: Int)
}
```
Web mirrors with three `BiquadFilterNode`s (`lowshelf` / `peaking` / `highshelf`) at the same frequencies, setting `.gain.value` per band (and `.Q.value` for the mid).

### Signal chain
- **Android** `VoiceMixer.mixBlock`: `sum voices + reverb wet → ThreeBandEq.process → SoftLimiter → AudioTrack`. `VoiceMixer` owns one `ThreeBandEq`.
- **Web** `engine.ts`: `modernMaster → eqLow → eqMid → eqHigh → modernLimiter → destination`.

### State & control flow
```kotlin
enum GuitarSound { Synth, Acoustic, Nylon, Electric }   // existing
// AppState:
val eq: EnumMap<GuitarSound, EqSettings>   // persisted; Nylon default mid=-4, rest flat
fun setEqBand(sound: GuitarSound, band: Band, db: Float)  // update map, persist, and if sound==current push to engine
// setSound(s) ALSO pushes eq[s] to modernEngine.setEq(...)
```
- `AudioTrackEngine.setEq(bassDb, midDb, trebleDb)` → forwards to the mixer's `ThreeBandEq.setGainsDb`. `SwitchableAudioEngine.modernEngine` already exposed.
- On `setSound(s)`: push `eq[s]` to the engine. On a slider change for the active sound: push live. On startup: restore the map, push the current sound's EQ.
- Web: `WebAudioEngine.setEq(bass, mid, treble)` sets the three node gains; same push logic in `appState.ts`.

## UI
🎚 Audio control, under the Sound picker (both platforms): three sliders **Bass / Mid / Treble**, each −12…+12 dB with the dB value shown, acting on the currently-selected sound; a **Flat** button resets the current sound to 0/0/0. Switching Sound repoints the sliders to that sound's stored values. Android: `Slider`s in `AudioQuickSliders`; web: `slider()`s in the audio popup.

## Testing
- **Pure (JVM; mirror cheap bits):** `ThreeBandEq` — flat (0/0/0) is exact passthrough (out == in, no NaN); a **bass boost** raises low-frequency energy while a high tone stays ~unchanged (and symmetrically for treble); a **mid cut** at 700 Hz reduces a 700 Hz sine's amplitude; full-scale input stays bounded/stable (no runaway); RBJ coeff sanity at known points.
- Existing 57 audio tests stay green (EQ bypasses when flat → non-EQ paths unchanged).

## Milestones
1. **`ThreeBandEq` DSP** — pure Kotlin, TDD.
2. **Android engine + AppState wiring** — bus EQ in `VoiceMixer`, `AudioTrackEngine.setEq`, per-sound `EqSettings` map + DataStore persistence + push-on-select/edit.
3. **Android UI** — Bass/Mid/Treble sliders + Flat in 🎚 Audio; **drop the build-tool nylon EQ, rebuild nylon flat**, set Nylon default mid −4.
4. **Web mirror** — biquad chain in `engine.ts`, appState map + localStorage, sliders in the audio popup.
5. **Ship v1.24.0** — banks unchanged size-wise (nylon rebuilt flat), APK build/archive, tests, commit, push, web CI. A/B toggle + Sound picker unchanged.

## Out of scope (future)
- Preset library / shareable presets; parametric (freq/Q) or graphic EQ; EQ on the legacy engine; master (global) EQ in addition to per-instrument.

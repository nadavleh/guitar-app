# Chorect Web

A browser port of **Chorect** — the interactive guitar/cavaquinho fretboard companion. This
is a faithful TypeScript + Web Audio reimplementation of the native Android (Kotlin/Jetpack
Compose) app that lives in `../guitar-app-main`.

> **Complete:** all six tools are ported and working — Fretboard (Chord / Scale / Strum), the
> chromatic Tuner, the full Ear Training suite, the Drum Machine, the Progression Looper, and
> all instrument/tuning/audio Options.

## Quick launch (Windows)

Double-click **`launch-web.bat`**. On the first run it installs dependencies (once), then
starts the dev server and opens your browser at `http://localhost:5317`. Keep the console
window open while you use the app; press `Ctrl+C` there to stop it.

> The port is pinned to **5317** (not Vite's default 5173) so it never collides with another
> local dev server. If 5317 is ever busy, the console says so — free it or change `server.port`
> in `vite.config.ts`.

> Requires [Node.js](https://nodejs.org/) (LTS). The `.bat` checks for it and tells you if it's
> missing. A true standalone `.exe` would mean wrapping this in Electron (a bundled Chromium
> desktop shell) — easy to add later if you want a windowed app instead of a browser tab.

## Manual commands

```sh
npm install     # once
npm run dev      # dev server with hot reload, opens the browser
npm run build    # type-check + production bundle into dist/
npm run preview  # serve the production build
npm run verify   # run the theory/audio engine sanity checks (see below)
```

## What works

- **Fretboard** — tappable neck with real Karplus-Strong string synthesis (Web Audio); pinch
  / drag (or mouse-drag + wheel) zoom and pan; left-handed mode; note / interval / empty dot
  labels; wound-vs-plain string rendering; fixed-aspect letterboxed neck.
- **Chord** — pick root + quality; All-notes vs Positions; CAGED voicings stepped with the
  position scroller; a per-voicing card with frets / notes / intervals / suggested fingering;
  optional jazz drop-2 (shell) voicings.
- **Scale** — 7 scales × 12 roots; All-notes vs Positions; notes + interval formula.
- **Strum** — tap frets to build a selection, mute whole strings, then Strum / Arpeggio / Clear.
- **Tuner** — live YIN pitch detection from the microphone, ±50 ¢ quarter-ring dial, tap-the-
  note reference tone, per-string reference row, on-the-fly tuning change. (Grant mic access
  when the browser asks.)
- **Instruments / tuning / audio** — Guitar & Cavaquinho, preset + editable custom tunings,
  A4 reference, ring-sustain and strum-spread sliders. Settings persist in `localStorage`.
- **Ear Training** — all five sub-modes (Progressions incl. ~24 curated advanced/non-diatonic
  progressions, Note→Chord, Flavor, Inversions, Aug/Dim), each with Practice + Challenge, the
  looping progression trainer with voice-leading, and a persisted progression-challenge
  high-score table.

- **Drum Machine** — a 4×16 samba step-sequencer (Surdo / Tamborim / Pandeiro / Agogô) with the
  built-in "stock samba" groove, per-track mute/solo + volume, tap-to-cycle voices, Brazilian
  16th-note swing, an erase tool, and save/load of custom beats. Voices are synthesized in Web
  Audio by default; drop your own/licensed one-shot WAVs into `public/drums/` (e.g.
  `pandeiro_3.wav`) to override any voice — see `public/drums/README.md`. Each voice falls back
  to the synth if no file is present, and the voice popup shows **sample** vs **synth**.

- **Progression Looper** — a multi-bar chord sequencer: 1/2/4 chords per bar, per-slot voicing
  picker + strum pattern (↓ ↑ ≋ ·), automatic voice-leading (each chord picks the
  smallest-movement voicing from the previous), a build-by-degree panel (Roman numerals →
  chords), tempo + bar count, and live "watch on neck" — the sounding chord mirrors onto the
  main fretboard while the loop plays.

A deep link opens a tool directly: `…/#EarTraining`, `#SambaLooper`, `#Loop`, `#Tuner`, `#Options`, `#Fretboard`.

## How the port maps to the Kotlin source

The original's clean three-module split carries straight over:

| Kotlin module | Web equivalent | Notes |
|---|---|---|
| `theory/` (pure Kotlin) | `src/theory/` | Direct 1:1 port — pitch classes, intervals, tunings, chord library, CAGED + cavaquinho + jazz-shell voicings, the constraint-based shape generator, scales, scale positions, fretboard overlay, fingering. |
| `audio/` (AudioTrack + DSP) | `src/audio/` | `PluckedSynth` (Karplus-Strong) and `PitchDetector` (YIN) are pure math and port directly; `WebAudioEngine` replaces the AudioTrack mixer with Web Audio buffer sources (the browser mixes voices for us), and `MicInput` uses `getUserMedia`. |
| `app/` (Jetpack Compose) | `src/app/` | `FretboardView`'s `Canvas` → an HTML `<canvas>` 2D renderer with the same geometry and zoom/pan math; Compose recomposition → an explicit re-render on an observable `AppState`; DataStore → `localStorage`. |

## Verification

`npm run verify` builds the real theory + audio modules and runs runtime assertions (chord
parsing, CAGED completeness, scale-position generation, equal-tempered pitch math, bounded
Karplus-Strong output, and YIN detecting a synthetic sine) — mirroring a slice of the Kotlin
JUnit suite.

## Tech

TypeScript, [Vite](https://vitejs.dev/) (dev server + bundler), the Web Audio API, and the
Canvas 2D API. No UI framework — the app is canvas- and state-driven.

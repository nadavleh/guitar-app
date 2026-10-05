<div align="center">
  <h1>🎸 Chorect</h1>
  <p>
    <strong>The fretboard <em>is</em> the app — an interactive guitar &amp; cavaquinho practice companion: chords, CAGED scales and triads, a progression looper, a deep ear-training suite, a samba drum machine, rhythm trainers, a chord-sheet reader, and a chromatic tuner.</strong>
  </p>
  <p>
    Tap any spot to hear the note. See every CAGED voicing across the neck. Loop voice-led progressions. Identify progressions, intervals, chord flavors and inversions by ear — even hands-free in the car. Build samba grooves from real percussion phrases.<br/>
    Native <strong>Android</strong> app + a feature-parity <strong>web</strong> port. Offline, no accounts, nothing leaves the device.
  </p>
  <p>
    <a href="https://nadavleh.github.io/guitar-app/"><strong>▶ Try the web app</strong></a>
  </p>
  <p>
    <img alt="Platform: Android" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
    <img alt="Platform: Web" src="https://img.shields.io/badge/platform-Web-F7DF1E?logo=javascript&logoColor=black">
    <img alt="Language: Kotlin" src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
    <img alt="Language: TypeScript" src="https://img.shields.io/badge/language-TypeScript-3178C6?logo=typescript&logoColor=white">
    <img alt="UI: Jetpack Compose" src="https://img.shields.io/badge/ui-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
    <img alt="minSdk: 26" src="https://img.shields.io/badge/minSdk-26-blue">
    <img alt="Version: 2.82.0" src="https://img.shields.io/badge/version-2.82.0-blue">
    <img alt="Tests: 900+" src="https://img.shields.io/badge/tests-900%2B-brightgreen">
    <img alt="License: TBD" src="https://img.shields.io/badge/license-TBD-lightgrey">
  </p>
</div>

---

## Table of Contents

- [About](#about)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Project structure](#project-structure)
- [Testing and CI](#testing-and-ci)
- [Architecture notes](#architecture-notes)
- [Contributing](#contributing)
- [License](#license)

---

## About

**Chorect** treats the guitar neck as the primary interface and layers everything you'd want to practise on top of it. It supports two instruments: **guitar** (6-string) and **cavaquinho** (4-string, default DGBD).

It is built **Android-first** in native Kotlin so the music-theory engine stays pure JVM (no Android dependencies, fast JUnit tests) and audio can use the low-latency `AudioTrack` path. A **web port** (`chorect-web/`, TypeScript + Web Audio, hand-rolled DOM with no UI framework) mirrors the theory engine, audio and every screen. A Kotlin Multiplatform iOS port is a long-term idea only.

> **Lockstep rule.** Every feature exists twice — Kotlin and TypeScript — and changes in the same commit. The web build is pinned against the Kotlin engine by runtime parity checks (`chorect-web/test/verify.ts`).

**Versioning** is `major.minor.patch` (minor = new feature, patch = bug fix). Android builds are named `Chorect_beta_V<version>.apk` and archived in [`releases/`](releases/) — old builds are never deleted. The current release is **2.82.0**.

---

## Features

The app has four user-chosen tabs (default: Fretboard / Ear / DrumLoop / Tuner; reorderable in Settings → *Look & tabs*) plus a **More** slot holding every other screen. Bottom tab bar in portrait, left rail in landscape; light / dark / auto themes with an accent picker.

### 🎸 Fretboard (home)

- **Live, tappable neck** computed from the current tuning; tap-on-release by default (swiping to pan never sounds a note), optional play-on-touch-down. Fixed-ratio neck in portrait and landscape, pinch-zoom + drag-pan, left-handed mode, labels as note names / intervals / none.
- One **Fretboard** sheet with **None / Chord / Scale / Play** modes.
- **Chords:** the 5 CAGED shapes per chord stepped with a position scroller, an all-notes view, and optional true **jazz shell voicings** (drop-2 dictionary). Cavaquinho gets its own compact voicings.
- **Scales:** major, natural minor, major/minor pentatonic, blues, dorian, mixolydian × 12 roots, with a formula display and positions.
- **Play mode:** free-form fret selection, per-string mutes, sweep-strumming, strum / arpeggio / clear, and 8 editable quick-chord slots.

### 🧩 Practice (guitar only)

A **Guitar practice** screen with a *Scales* / *Triads* section split, a shared key / tempo row and a 22-fret neck. *Scales* has **Guided run · Challenge · Explore** tabs over CAGED scale boxes; *Triads* drills triad inversions. See `GUI_DESIGN.md` §13.

### 🔁 Loop

A chord-progression looper: 1/2/4 chords per bar, 1–16 bars, BPM 40–200, per-slot strum (↓ ↑ arpeggio sustain), per-slot voicing chips with automatic min-movement **voice-leading**, and a build-by-Roman-numeral-degree panel. The sounding shape is mirrored live on the neck.

### 👂 Ear training

Sub-modes: **Progressions**, **Note→Chord**, **Flavor**, **Inversions**, **Aug/Dim**, **Intervals**, **Drill**, and **Workout**. Each trainer has **Practice** and scored **Challenge** modes. Ear training always uses guitar tuning and defaults to shell voicings.

- **Progressions:** diatonic major/minor plus **harmonic-minor** (V7 → i), a circle-of-fifths generator, "I→iii" and "3rd vs 6th" focus generators, and a large curated **Advanced / Advanced II / Suspended** library of named non-diatonic progressions. Challenges are answered on a **degree keyboard** (Major/Minor shift, extensions row, Prev/Next history) with a persistent high-score table. Banners flag a progression with no tonic, one that reads in the relative key, and — once every slot is filled — 3+ chords moving round the circle of fifths.
- **Library:** browse every progression the trainer can generate, hear it, see it on the neck, and open famous-song examples via YouTube / Spotify search links.
- **Car mode:** a hands-free, ungraded variant of the Progression challenge — spoken/beeped lead-in, five plays per progression revealing one more chord each time, then auto-advance; ← Prev goes back, tap a slot to hear its chord, double-tap to peek. Synthesised cue beep, configurable voice level. (`GUI_DESIGN.md` §10.4)
- **Drill:** every progression you miss is saved with the exact **rendition** heard (key + per-bar pitches) and replayed verbatim; re-tests can be drawn from the drill list.
- **Workout:** a real-song curriculum (spiral, 12-month timeline of sessions) that works directly in scale-degree function.

### 🥁 DrumLoop (samba drum machine)

- Step sequencer with **Surdo, Tamborim, Pandeiro and Agogô** tracks, per-track mute/solo, per-instrument voice audition and volume, tap-to-cycle voices, erase tool, and a master fader.
- Configurable meter (bars, time signature, division), two **swing models** (Default / Hemiola), loop-shift (a selected track rotates alone), 2-finger X/Y zoom + pan.
- **Blocks:** a phrase-based arranger — a grid of tracks × real percussion phrases (including partido-alto and teleco variants), per-track swing clocks, drag-reorder, merge, and a count-in. Several built-in grooves ship, and you can save/load your own beats.
- **Export** the loop to WAV (full mix or one track at a time).
- Drum voices are bundled recorded one-shots, with a built-in synth fallback. **The bundled samples are placeholders** to be replaced with properly licensed ones before any public distribution.

### 🎵 Rhythm and Metronome

- **Rhythm (units):** learn and train the basic one-beat rhythmic units (with and without rests) as notation cards that loop at an adjustable BPM; rhythmic phrases build on them.
- **Metronome:** click track with selectable time signatures.

### 🎻 Cavaquinho Progressions

Functional samba sequences (quadradinho I–VI7–ii–V7, minor and extended "médio" sequences, …) transposable to any key, looped over a voice-led neck, with a samba **Songs ♪** list per harmonic family. Works on guitar too (it follows the live tuning).

### 📖 Songs and Theory

- **Songs:** a chord-sheet reader over a **sideloaded song pack** (a folder you provide — never shipped in the app, the repo or the site). Search, transpose, and toggle chords ↔ scale degrees; monospace sheets, Hebrew RTL support. Nothing sounds — it is a reader. (`GUI_DESIGN.md` §12)
- **Theory:** interval song references and reference sheets.
- **Decompose:** chord-tone breakdown of any chord.

### 🎚️ Tuner

YIN pitch detection (±2 ¢ accuracy), a ±50 ¢ quarter-ring dial, tappable note label that plays the reference tone, per-string reference buttons, on-the-fly preset/custom tunings, and a configurable A4 (435–445 Hz).

### 🎛️ Instruments, tuning and audio

- **Guitar and cavaquinho**; preset tunings (Standard, Drop D, DADGAD, Open G/D, half/whole step down; cavaquinho DGBD, DGBe) and saved **custom tunings**.
- **Sounds:** Karplus-Strong synth plus sampled Acoustic, Nylon and Electric banks, per-sound 3-band EQ and reverb, strum-spread and ring-sustain sliders, per-instrument volume.
- **Audio latency:** engine runs at the device's native rate with a shallow output queue, plus an in-app latency measurement panel.

---

## Tech stack

**Android**

| Layer | Choice |
|---|---|
| Language / UI | **Kotlin 2.1**, **Jetpack Compose** (Material 3) |
| Audio out | `AudioTrack` low-latency mixer + Karplus-Strong DSP + multisamples |
| Audio in | `AudioRecord` + pure-Kotlin YIN |
| Persistence | DataStore Preferences |
| Build / tests | **Gradle** (Kotlin DSL, version catalog), JUnit 5 + `kotlin.test` |
| SDK | minSdk 26 / targetSdk 34 |

**Web** (`chorect-web/`)

| Layer | Choice |
|---|---|
| Language | **TypeScript** (strict) |
| UI | Hand-rolled DOM + Canvas, no framework |
| Audio | **Web Audio API** (buffer sources, biquad EQ, convolver reverb, compressor) |
| Persistence | `localStorage` |
| Build / host | **Vite** + `tsc --noEmit`, deployed to **GitHub Pages** |

---

## Getting started

### Android

Prerequisites: JDK 17 or 21, Android SDK with API 34, and an emulator or a device with USB debugging. Windows setup walkthrough: [ANDROID_SETUP.md](ANDROID_SETUP.md).

The repo ships **only `gradlew.bat`** (no POSIX wrapper); on Windows:

```sh
./gradlew.bat :app:assembleDebug     # APK -> app/build/outputs/apk/debug/Chorect_beta_V<version>.apk
./gradlew.bat :app:installDebug      # build + install on the connected device/emulator
./launch-app.bat                     # emulator (low-latency audio) + install + launch, in one step
```

The Tuner needs a real microphone (the emulator's is silent); for honest latency numbers also test on a real device. Prebuilt APKs are in [`releases/`](releases/).

### Web

Live: **https://nadavleh.github.io/guitar-app/**

Requires Node.js 20+:

```sh
cd chorect-web
npm ci
npm run dev        # Vite dev server
npm run build      # tsc --noEmit + vite build
npm run verify     # runtime parity checks against the Kotlin engine
```

---

## Project structure

```
Chorect/
├── theory/            Pure-JVM theory engine (no Android deps): notes, tunings, chords, CAGED,
│                      scales, ear-training generators + Workout, car mode, drum model/blocks,
│                      rhythm units/phrases, cavaquinho sequences, song sheets
├── audio/             Android library: AudioTrack engine, voice mixer, synths, sample player,
│                      EQ/reverb, YIN pitch detector, WAV decoder, cue beep
├── app/               Compose UI + state: Shell (tabs), Screens (fretboard), per-screen
│                      *Screen.kt / *State.kt files; app/src/main/assets holds samples
├── chorect-web/       TypeScript port
│   ├── src/theory/    mirror of theory/ (file names are NOT 1:1 — e.g. chords.ts, core.ts)
│   ├── src/audio/     mirror of audio/ on Web Audio
│   ├── src/app/       *State.ts + *UI.ts per screen, ui.ts shell, fretboardCanvas.ts
│   └── test/verify.ts runtime parity checks
├── docs/              design records: superpowers/specs + plans, progression library,
│                      ear-training digest, cavaquinho references
├── tools/             Python/Kotlin generators (drum + guitar samples, song library, launcher icon)
├── releases/          archived Chorect_beta_V<version>.apk builds
├── .github/workflows/ kotlin-tests.yml, deploy-web.yml
├── GUI_DESIGN.md      single source of truth for look-and-feel and screen behaviour
├── CLAUDE.md          contributor/agent working rules (lockstep, version bump, build notes)
├── requirements.md    original v1.x spec (directional only; inventory is out of date)
└── launch-app.bat     Windows emulator + install + launch
```

Generated, do not hand-edit: `chorect-web/dist*/`, `*/build/`, `tools/cavaco_g_shapes.json` (`./gradlew.bat :theory:emitCavacoShapes`), and the drum WAVs (built by `tools/build_drum_samples.py`; the Android and web copies must match).

---

## Testing and CI

```sh
./gradlew.bat test                  # all unit tests (~940, about a minute)
./gradlew.bat :theory:test          # theory engine only
./gradlew.bat :audio:testDebugUnitTest
```

Tests live only in `theory/src/test/` and `audio/src/test/`; the `app/` Compose layer is verified by building and running it. Coverage includes chord/CAGED shape correctness, voice-leading, progression resolvers and generators, Car-mode timing, drill renditions, drum/swing timing and WAV codec, YIN accuracy, and song-sheet transposition.

GitHub Actions:

- **`kotlin-tests.yml`** — runs the `theory` and `audio` Gradle tests on pushes/PRs touching those modules.
- **`deploy-web.yml`** — on pushes touching `chorect-web/`: `npm ci` → `tsc --noEmit` → `npm run verify` → `vite build` → deploy to GitHub Pages. (A red deploy step does not always mean a stale site; Pages can time out after publishing.)

---

## Architecture notes

- **Module boundaries.** `theory` has zero Android dependencies and stays unit-testable without any UI; `audio` and `app` sit on top. The web port re-creates the same layering in TypeScript, with an explicit render pass in place of Compose recomposition and a Web Audio engine in place of `AudioTrack`.
- **Chord shapes.** `ChordShapeGenerator` returns canonical CAGED templates (relative fret offsets, so transposition is one integer add) or the jazz shell dictionary, and falls back to a constrained brute-force search with a per-instrument max fret span.
- **Ear training.** Progressions are stored as Roman-degree / semitone-offset triples, so each transposes to any key with one add per chord; `EarTraining.resolve()` maps degree + key + mode + chord level to a chord symbol and Roman label.
- **Audio out.** One long-lived `AudioTrack` in streaming low-latency mode with a high-priority mixing thread; synthesis runs off the UI thread, and the loop skips writing when no voices are active so a fresh tap lands within a few ms of buffer.
- **Audio in.** 44.1 kHz mono, 2048-sample windows through YIN (difference function, cumulative-mean normalisation, threshold, parabolic interpolation), converted to note + cents against a configurable A4.

---

## Contributing

This is a personal project. If you'd like to contribute, open an issue first so we can agree on direction. Read `CLAUDE.md` for the lockstep and version-bump conventions.

---

## License

Not yet decided. Treat the code as **all rights reserved** until a `LICENSE` file is added. If you want to use a piece of it, ask.

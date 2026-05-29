<div align="center">
  <h1>🎸 Guitar Practice</h1>
  <p>
    <strong>An interactive guitar fretboard for learning chords, scales, and ear-training — built with Kotlin and Jetpack Compose.</strong>
  </p>
  <p>
    The fretboard is the app. Tap any spot to hear the note. Pick a chord, see every voicing across the neck. Pick a scale, see every position. Change the tuning and watch everything update live.
  </p>
  <p>
    <img alt="Platform: Android" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
    <img alt="Language: Kotlin" src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
    <img alt="UI: Jetpack Compose" src="https://img.shields.io/badge/ui-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
    <img alt="minSdk: 26" src="https://img.shields.io/badge/minSdk-26-blue">
    <img alt="Tests: 88 passing" src="https://img.shields.io/badge/tests-88%20passing-brightgreen">
  </p>
</div>

---

## Table of Contents

- [About](#about)
- [Features](#features)
- [Screenshots](#screenshots)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Project structure](#project-structure)
- [Running tests](#running-tests)
- [Roadmap](#roadmap)
- [Architecture notes](#architecture-notes)
- [Contributing](#contributing)
- [License](#license)

---

## About

**Guitar Practice** is a music-theory-aware fretboard companion for guitarists. It treats the fretboard as the primary interface and layers everything you'd want to study — chord voicings, scale positions, tunings, audio playback — on top of it. The app is offline, has no accounts, and stores nothing on a server.

It's built **Android-first** in native Kotlin so the music-theory engine can stay pure-Kotlin (zero Android dependencies, fast JUnit tests) and the audio path can use AAudio directly for low latency. A Kotlin Multiplatform port for iOS is planned.

> The music-theory engine and the audio engine are split into their own Gradle modules, fully unit-testable in isolation. The `app` module is just a Compose UI on top.

---

## Features

### Implemented

- **Live, tappable fretboard** — every position is computed from the current tuning. Tap any fret, hear the note, see its interval relative to the current chord or scale root.
- **Chord browser** — 16 chord qualities × 12 roots = 192 chords. For each, the [`ChordShapeGenerator`](theory/src/main/kotlin/app/guitar/theory/ChordShapeGenerator.kt) enumerates every playable voicing on the neck under the §6.3 constraints (≤ 4-fret span, ≤ 1 note per string, all chord tones present), ranks them, and surfaces the top shapes with finger suggestions.
- **Scale display** — 7 scales × 12 roots = 84 combinations, with formula display in monospace (`1 b3 4 5 b7` for A minor pentatonic, etc.).
- **Tunings** — seven presets (Standard, Drop D, DADGAD, Open G, Open D, Half-step down, Whole-step down). Custom tunings can be edited per-string and saved by name. Everything persists across app restarts.
- **Audio** — every tap and every chord-shape selection plays through a Karplus-Strong plucked-string synth. The audio engine is a continuous-output polyphonic mixer that idles when there's nothing to play and starts the moment a note arrives.
- **Pick mode** — select arbitrary positions across the neck and strum them as a chord or arpeggio.
- **Left-handed mode** — mirrors the fretboard and tap mapping; persisted.
- **Interval and note labels** — toggle between note names, interval numbers, or no labels inside the dots.
- **Fret-range filter** — narrow chord/scale results to a specific section of the neck.

### On the roadmap

See the [Roadmap](#roadmap) section.

---

## Screenshots

Coming soon. The current build runs on the Android Emulator and on real Pixel-class devices; a redesign pass (`GUI_DESIGN.md`) is in progress.

---

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin 2.1** | JVM-native, modern, no JS toolchain to learn |
| UI | **Jetpack Compose** (Material 3) | Declarative, theme-friendly, smooth animations |
| Audio | **AudioTrack** (AAudio path) + custom Karplus-Strong | Sub-50 ms tap-to-sound on real hardware |
| Persistence | **DataStore Preferences** | Modern replacement for SharedPreferences; coroutine-friendly |
| Build | **Gradle 8.11** with the Kotlin DSL + version catalog | Standard for new Android projects |
| Tests | **JUnit 5** + `kotlin.test` | Fast, modern, parameterizable |
| Min Android | **API 26 (Android 8.0)** | Required for the low-latency AAudio path; covers ~98% of active devices |
| Target Android | **API 34 (Android 14)** | Current stable |

The repo is a multi-module Gradle build:

```
guitar_app/
├── theory/   ← pure Kotlin, zero Android deps — KMP-ready for iOS later
├── audio/    ← Android library wrapping AudioTrack; depends on theory
└── app/      ← Compose UI; depends on theory + audio
```

---

## Getting started

### Prerequisites

- **Windows / macOS / Linux** development host
- **JDK 17 or 21** (Android Studio bundles a compatible JBR)
- **Android SDK** with **API 34** platform installed
- An Android emulator (a Pixel-class AVD with `x86_64` system image) **or** a real device with USB debugging enabled

For Windows setup specifically, see [ANDROID_SETUP.md](ANDROID_SETUP.md) — it walks through hardware-virtualization, SDK install, `JAVA_HOME` / `ANDROID_HOME`, and PATH cleanup.

### Build and run

Clone, then from the project root:

```sh
./gradlew :app:installDebug                              # build + push to the connected device/emulator
adb shell am start -n app.guitar/app.guitar.app.MainActivity   # launch
```

**On Windows** you can also just double-click `launch-app.bat` — it starts the emulator (with audio) if needed, builds, installs, and launches in one shot.

### A note on emulator audio

The Android Emulator on Windows often defaults to an audio backend that adds 100–300 ms of latency on top of the system's minimum buffer. The `launch-app.bat` script passes `-audio winaudio` to force the lower-latency Windows audio path; if you launch the emulator yourself, do the same:

```sh
emulator -avd Pixel_7 -audio winaudio
```

For an accurate feel for the audio latency, test on a real device. On a Pixel-class phone, tap-to-sound is ~20–40 ms.

---

## Project structure

```
guitar_app/
├── app/                                     # Android application (Compose UI)
│   └── src/main/kotlin/app/guitar/app/
│       ├── MainActivity.kt                  # entry point, single-screen layout
│       ├── AppState.kt                      # reactive UI state holder
│       ├── Theme.kt                         # dark "studio" color scheme + typography
│       ├── FretboardView.kt                 # Canvas-drawn fretboard composable
│       ├── ModeBar.kt                       # bottom mode selector (Tuning/Chord/Scale/Pick)
│       ├── Screens.kt                       # mode panels (above the ModeBar)
│       ├── Common.kt                        # ShapeCard, intervalName, mark helpers
│       └── TuningRepository.kt              # DataStore-backed tuning persistence
│
├── audio/                                   # Android library
│   └── src/main/kotlin/app/guitar/audio/
│       ├── AudioEngine.kt                   # interface + silent no-op
│       ├── AudioTrackEngine.kt              # MODE_STREAM continuous-output mixer
│       └── PluckedSynth.kt                  # pure-Kotlin Karplus-Strong DSP
│
├── theory/                                  # Pure-Kotlin music theory (KMP-ready)
│   └── src/main/kotlin/app/guitar/theory/
│       ├── PitchClass.kt   Interval.kt   Note.kt        # core types
│       ├── Tuning.kt       Tunings.kt    Fretboard.kt   # instrument model
│       ├── ChordLibrary.kt ChordQuality.kt ChordShape.kt
│       ├── ChordShapeGenerator.kt                       # shape enumeration
│       ├── ScaleLibrary.kt Scale.kt FretboardOverlay.kt
│       ├── Fingering.kt    NoteSpeller.kt               # display helpers
│       └── TuningCodec.kt                               # serialization
│
├── GUI_DESIGN.md       # the design source-of-truth (in flux during the redesign)
├── ANDROID_SETUP.md    # Windows setup walkthrough
├── requirements.md     # the original product spec
└── launch-app.bat      # double-click launcher for Windows
```

---

## Running tests

```sh
./gradlew test                          # all module tests
./gradlew :theory:test                  # theory engine only (fast, pure JVM)
./gradlew :audio:testDebugUnitTest      # audio DSP only
```

The current test count is **88** (theory: 71, audio: 17), all green. Tests are JUnit 5; the theory module is pure Kotlin so its tests run in milliseconds.

Notable coverage:

- All 8 spec tests from `requirements.md` §13 are mirrored 1-to-1 in `RequirementsTest.kt`.
- Chord shape generator is verified for max-fret-span constraint, root-in-bass detection, every chord quality in the library, and Drop-D-vs-Standard tuning shape divergence.
- Audio synth is unit-tested for amplitude bounds, deterministic seeding, fundamental frequency accuracy, and chord-mixing.

---

## Roadmap

| Status | Item | Notes |
|---|---|---|
| ✅ | Core theory engine (M1) | Notes, intervals, tunings, chord/scale libraries |
| ✅ | Chord shape generator (M2) | §6.3 constraints satisfied |
| ✅ | Compose fretboard view (M3) | Canvas-based, tappable |
| ✅ | Tuning persistence (M4) | DataStore + custom tunings |
| ✅ | Chord & scale screen polish (M5, M6) | Chips, formula display |
| ✅ | Audio (M7) | Karplus-Strong + AudioTrack continuous mixer |
| ✅ | Left-handed mode + chord playback (M8) | Persisted; mirrors entire fretboard |
| 🚧 | **GUI redesign (v1)** | Single-screen, fretboard-centric, mode-bar architecture — in progress; see `GUI_DESIGN.md` |
| 🚧 | Realistic strings (wound vs plain) | Phase C of redesign |
| 🚧 | Scale position algorithm | Phase B — CAGED-like positions per scale degree |
| 📅 | Position scroller for chords/scales | Phase D |
| 📅 | **Jazz / shell voicings toggle** | A toggle in **Options** that re-ranks chord shapes to favor jazz voicings: drop the 5th (it's the most expendable chord tone), sometimes also drop the root (relying on the bass to provide it). Reference: `guitar lesson chord shapes.pdf`. Should affect both the shape list in the Chord sheet and the position scroller. |
| 📅 | **Chord-progression loop builder** | A new menu option ("Loop" or "Progression") to lay out a repeating progression and hear it: <ul><li>Define a loop of *N* bars with meter *Y* (e.g. 4/4, 3/4, 6/8) and BPM</li><li>Each bar can hold one chord (full bar) or two chords (half bars each). Each chord plays as quarter-note strums for the duration of its bar slice.</li><li>Per slot, pick the voicing from the generated shape list OR draw an arbitrary chord by tapping positions on the fretboard (carried over from Pick mode).</li><li>Transport: play / stop / loop indefinitely. Visual indicator of the currently-sounding bar.</li></ul> Phase 2 extensions (not for v1): swing feel, finer subdivision than quarter notes, comping rhythms, sound-on-sound recording for practice. |
| 📅 | Custom-chord favorites | §10.5 from requirements |
| 📅 | Practice prompts (random challenges) | §10.6 |
| 📅 | Cavaquinho / non-6-string instruments | §15 — engine already n-string ready |
| 📅 | iOS port via Kotlin Multiplatform | Theory engine first; SwiftUI for shell |

---

## Architecture notes

### Module boundaries

The three-module split (`theory` / `audio` / `app`) is enforced by Gradle. The `theory` module has **zero Android dependencies** — it compiles to plain JVM bytecode and is fully unit-testable without an emulator. This makes both testing and a future Kotlin Multiplatform port straightforward; only the `app` layer (Compose) and `audio` layer (AudioTrack) would need iOS-specific replacements.

### Audio engine

The audio engine went through three iterations before settling on a continuous-output mixer:

1. **Per-tap AudioTrack creation (M7 v1)** — caused glitches and resource exhaustion under rapid tapping.
2. **Persistent AudioTrack with pause/flush/play per note (M7 v2)** — eliminated resource leak but introduced an audible click at every interrupt.
3. **Continuous-output mixer (current)** — one `AudioTrack` in `MODE_STREAM` + `PERFORMANCE_MODE_LOW_LATENCY`, kept in `PLAYING` state for the engine's lifetime, with a dedicated high-priority output thread that mixes voices in real time. Synthesis runs on a separate executor so the UI thread never blocks. The output loop intentionally **skips writing when there are no active voices** so the ring buffer stays empty and a fresh tap plays within ~6 ms of buffer latency.

### Theory engine

Built around two value classes (`PitchClass` for the 12-tone scale degree, `Midi` for absolute pitch), plus the data classes `Note`, `Interval`, `Tuning`, `FretPosition`. Every algorithm is a pure function on these types. The `ChordShapeGenerator` does an exhaustive search over anchor positions with constraint filtering and ranks results by a multi-key comparator (root in bass first, then position, then mute count, then fret span). The same approach will drive the upcoming scale-position algorithm in Phase B.

---

## Contributing

This is a personal project at the moment. If you're interested in contributing, open an issue first so we can sync on direction.

---

## License

Not yet decided. Treat the code as **all rights reserved** until a `LICENSE` file is added. If you want to use a piece of it, ask.

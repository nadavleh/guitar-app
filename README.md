<div align="center">
  <h1>🎸 Chorect</h1>
  <p>
    <strong>The fretboard <em>is</em> the app — an interactive guitar &amp; cavaquinho companion for chords, scales, progressions, ear training, cavaquinho functional sequences, rhythm training, a samba drum machine, and a built-in chromatic tuner.</strong>
  </p>
  <p>
    Tap any spot to hear the note. Pick a chord, see every CAGED voicing across the neck. Pick a scale, see every position. Loop a progression with voice-led voicings. Identify progressions (diatonic, harmonic-minor, and non-diatonic), chord flavors, inversions, intervals, and notes-over-chords by ear. Drill the basic units of rhythm. Tune to within a cent.<br/>
    Native <strong>Android</strong> app + a parity <strong>web</strong> port. Offline, no accounts, nothing leaves the device.
  </p>
  <p>
    <img alt="Platform: Android" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
    <img alt="Platform: Web" src="https://img.shields.io/badge/platform-Web-F7DF1E?logo=javascript&logoColor=black">
    <img alt="Language: Kotlin" src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
    <img alt="Language: TypeScript" src="https://img.shields.io/badge/language-TypeScript-3178C6?logo=typescript&logoColor=white">
    <img alt="UI: Jetpack Compose" src="https://img.shields.io/badge/ui-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
    <img alt="minSdk: 26" src="https://img.shields.io/badge/minSdk-26-blue">
    <img alt="Version: 2.16.0" src="https://img.shields.io/badge/version-2.16.0-blue">
    <img alt="Tests: 700+ passing" src="https://img.shields.io/badge/tests-700%2B%20passing-brightgreen">
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
- [Running tests](#running-tests)
- [Roadmap](#roadmap)
- [Architecture notes](#architecture-notes)
- [Contributing](#contributing)
- [License](#license)

---

## About

**Chorect** is a music-theory-aware fretboard companion. It treats the neck as the primary interface and layers everything you'd want to study — chord voicings, scale positions, tunings, progressions, ear training, a percussion looper, and a chromatic tuner — on top of it.

It's built **Android-first** in native Kotlin so the music-theory engine can stay pure-Kotlin (zero Android dependencies, fast JUnit tests) and the audio path can use the low-latency AAudio route directly. A **web port** (`chorect-web`, TypeScript + WebAudio) mirrors the same theory engine and UI so the app runs in any browser too; the two platforms are kept in strict feature parity. A Kotlin Multiplatform port for iOS is planned — the theory engine ports as-is, only the UI and audio drivers need replacing.

> Android: three Gradle modules — `theory` (pure JVM), `audio` (Android lib), `app` (Compose UI). The theory engine carries the bulk of the suite's 700+ tests that run in milliseconds, with zero emulator dependency. Web: `chorect-web/` is a Vite/TypeScript app whose `src/theory` is a hand-kept mirror of the Kotlin theory module, deployed to GitHub Pages.

The current release is **version 2.16.0** (versionCode 21600). Since the 1.x line the app has grown well beyond a fretboard tool:

- **v2.0 "Signal" redesign** — a whole-app makeover: bottom **tab bar** (portrait) / **left rail** (landscape) with a user-configurable pick-4 + reorder, a persistent transport dock, a unified tone sheet, light/dark/auto theming with a 5-colour accent picker.
- **Web port** — full parity in the browser (`chorect-web`), deployed to GitHub Pages.
- **Cavaquinho** — a first-class 4-string instrument with a dedicated **Progressions** screen (functional samba sequences: looper + voice-led neck), a curated song library, and DGBD default tuning.
- **Ear-training expansion** — diatonic **plus** a large curated **Advanced / Advanced II / Suspended** library, a **harmonic-minor** toggle (major V7 → i cadences, default on), a circle-of-fifths generator, a browsable progression **Library** with per-progression **song examples** (YouTube + Spotify links), and true jazz **shell voicings** by default.
- **Better audio** — sampled acoustic/nylon/electric guitar banks (with a Karplus-Strong synth fallback), per-sound **3-band EQ** and **reverb**, a real-time voice-graph engine (envelopes, panning, soft limiter) with an A/B toggle against the legacy engine.
- **v2.15–2.16 Rhythmic Units** — a new **Rhythm** menu entry to learn & train the basic one-beat rhythmic units (with and without rests), each shown as a music-notation thumbnail that loops at a controllable BPM.

Versioning is **major.minor.patch**: bump the **minor** for new features, the **patch** for bug fixes, and the **major** for breaking redesigns. Each Android build is emitted as `Chorect_beta_V<version>.apk` (e.g. `Chorect_beta_V2.16.0.apk`), and previous releases are kept in a `releases/` folder rather than overwritten.

---

## Features

### 🎸 Fretboard

- **Live, tappable fretboard.** Every position is computed from the current tuning. Tap any fret to hear the note (~20-40 ms tap-to-sound on real hardware) and see its interval relative to the current chord or scale root.
- **Starts empty.** On launch the neck shows **nothing lit** — no chord, scale, or notes — until you open the Fretboard tool and pick something.
- **Fixed-proportion neck, any orientation.** The neck is always drawn at a fixed long-horizontal / short-vertical aspect ratio, centered and letterboxed inside its viewport — the box shape never stretches the neck. The app runs in **both portrait and landscape**; a tall portrait box just leaves empty space above and below the short neck. In **portrait** the neck now **starts zoomed in on the first frets** at a larger size, rather than letterboxing down to a tiny sliver.
- **Pinch-to-zoom + drag-to-pan.** Pinch (focal-point) to scale the neck between 0.5× (whole neck shrinks to half the viewport) and ~stringCount/2 (zoom in until ~2 strings fill the height); drag to pan the zoomed neck, clamped so it can't be pulled off-screen. Pinch and drag work across the **whole area allotted to the fretboard** — including the empty letterbox margins above and below the neck — not just on the thin neck itself.
- **Tap-on-release by default.** A clean tap plays the note on release, so swiping the neck to pan never sounds a note. An Options toggle switches to play-on-touch-down if you prefer the immediate response.
- **Realistic strings.** On guitar, wound bass strings get a thicker bronze stroke; plain treble strings render lighter. Cavaquinho renders all four strings plain.
- **Left-handed mode.** Mirrors the whole fretboard and the tap mapping.
- **Labels.** Switch dots between note names, interval numbers, or empty — persists across launches. The default is interval labels.

The neck's display modes are unified under a single **Fretboard** tool with a **None / Chord / Scale / Play** segmented selector (not separate menu entries). **Play** mode adds single-finger sweep-strumming across the strings and **8 quick-chord slots** (editable chips) that drop a chord's grip onto the neck to strum up/down.

### 🎼 Chords

- **CAGED-canonical voicings.** For each chord, the [`ChordShapeGenerator`](theory/src/main/kotlin/app/guitar/theory/ChordShapeGenerator.kt) returns the 5 CAGED shapes (C / A / G / E / D) spread along the neck rather than crowding the low frets. Step through with the bottom position scroller — each shape labelled with its template and fret span.
- **Jazz / shell-voicings mode.** Toggle in Options. Replaces CAGED with the canonical drop-2 dictionary from jazzguitar.be — 4 inversions per `maj7`, `m7`, `7`, `m7b5`, `dim7`, `6`, `m6`, plus the standard A-rooted `9` shape and middle-4-string voicings.
- **All-notes vs Positions view** — light up every chord tone across the neck, or step through one voicing at a time.
- **Strummed playback** for every shape — Karplus-Strong plucked-string synthesis through the continuous-output mixer.
- **Broad quality library.** Beyond the common triads/7ths/extensions, the chord library now also knows `mMaj7` (minor-major 7th), `7#5` (augmented dominant), and `maj7#5` (augmented major 7th) — used by the line-cliché advanced progression and the Aug/Dim ear trainer.

### 🎵 Scales

- **7 scales** (major, natural minor, major pentatonic, minor pentatonic, blues, dorian, mixolydian) × 12 roots.
- **All-notes vs Positions view** — see the whole scale, or step through positions one at a time along the neck.
- **Formula display** — monospace notes + intervals (e.g. `1 b3 4 5 b7` for A minor pentatonic).

### ✊ Play (pick) mode

- **Build an arbitrary selection.** Tap any frets across the neck to add or remove them from the selection.
- **Per-string mutes.** Toggle whole strings off — drawn as a red ✕ at the nut, chord-diagram style. Muted strings are excluded from the Strum / Arpeggio (and picking a fret un-mutes its string).
- **Strum, Arpeggio, Clear** actions strum the selection together, roll it as an arpeggio, or reset both picks and mutes.

### 🎚️ Tuner

- **YIN pitch detection** (pure Kotlin, unit-tested). Locks within ~2 cents from low E2 (82 Hz) to high E4 (330 Hz).
- **Quarter-ring dial** spanning ±50 cents with cent ticks (major every 10 ¢, minor every 5 ¢, micro every 1 ¢) and a green-tinted ±10 ¢ "in tune" band. Enlarged dial in portrait.
- **Big tappable note label** — tap to play the equal-tempered reference tone and lock the dial to "spot on" for the sustain duration.
- **Reference row** — one button per open string of the current tuning. Tap to hear the target tone (so you can match by ear before plucking).
- **Change tuning on the fly** — preset and custom-tuning chips live right on the tuner, so you can switch tuning without opening Options.
- **Configurable A4** — 435 to 445 Hz, persisted.

### 🔁 Progression looper

- **Multi-chord per bar** — 1 slot (whole), 2 (half), or 4 (quarter).
- **Per-slot voicing picker** — chips for every CAGED shape with fret range. The whole progression is auto-voiced for smooth voice-leading (first chord = E-shape; each subsequent chord picks the min-movement voicing).
- **Per-slot strum** — `↓` Down, `↑` Up, `≋` Arp, `·` Sustain.
- **Build by degree** — an optional panel resolves Roman-numeral degrees (key / mode / diatonic level + an optional quality override) straight into the bars.
- **Bar count** 1-16, BPM 40-200.
- **Live on the neck** — bar/slot highlights in real time, and the sounding chord shape is mirrored on the main fretboard while the loop plays (you can leave the loop running and "watch on neck").

### 👂 Ear training

**Six** sub-modes (Progressions / Note→Chord / Flavor / Inversions / Aug-Dim / Intervals), each with a **Practice** and a **Challenge** mode. Every **Challenge** has a **Restart** action in its in-flight header (next to Quit). Ear training is always a **guitar** exercise (it uses standard guitar tuning even when the cavaquinho is the selected instrument), defaults to true jazz **shell voicings**, and gives the low strings a gentle bass boost so chords sit fuller.

- **Progression.** Random 4-bar progressions in any key/mode with reveal-on-tap chord labels and a "Hear I–V–I" key cadence. Practice loops a progression and lets you reveal each bar's Roman label; you can push the current chords into the Looper. The **Challenge** is 15 questions, answered with a tappable **degree keyboard**: each bar is a square you tap to open a row of scale-degree keys with a Major/Minor **shift** that relabels the same seven shared diatonic chords — so relative-major/minor answers (e.g. major I–IV–V vs minor III–VI–VII) are accepted as equivalent — plus an extensions row shown only when the level uses 7ths/extensions (triads get a simplified keyboard). The "Hear the degrees" reference palette auditions each diatonic degree in the (hidden) key labelled with **plain numbers** (1..7) rather than Roman numerals, so auditioning a degree doesn't reveal whether the key is major or minor; the answer keys themselves stay silent. **Prev / Next** are pinned at the top of the challenge with a per-question history, so "← Prev" returns to an earlier question and restores its saved answers (an accidental "Next" is recoverable). You can advance without answering every bar (unanswered bars are credited correct); a persistent **high-score table** keeps the top results with date and completion time, ranked by score then fastest time.
  - **Generator categories.** A dropdown swaps the diatonic generator for other pools: **Harmonic minor** (an on-by-default toggle adds the classic minor-key **major V / V7 → i** cadences — i–iv–V–i, i–ii°–V–i, i–bVI–ii°–V, etc. — answerable with a dedicated **V7** key on the minor keyboard); **I→iii focus** drill; **Circle of fifths** (adjacent windows with secondary dominants); and a large curated **Advanced / Advanced II (maj7·min9·modal) / Suspended (sus2·sus4)** library of ~60 named non-diatonic progressions — borrowed chords, secondary dominants, chromatic passing chords, jazz turnarounds, modal vamps (e.g. Mixolydian Rocker, Andalusian Cadence, Tritone Substitution, Bird/Montgomery turnarounds, Mario Cadence, line clichés). Each transposes to any key with a short teaching note and a reveal of name + Roman numerals + chords.
  - **Progression Library + songs.** A browsable **Library** lists every progression the trainer can generate — Major / Minor (diatonic), Minor — harmonic (V7 → i), Advanced / Advanced II / Suspended, and the circle-of-fifths windows — each expandable to hear it, see it on the neck, and browse **famous-song examples** with one-tap **YouTube** (default) and **Spotify** (alternative) search links. A playing-head highlights the current bar during playback across every progression view.
- **Note2Chord.** A random major or minor triad plays as a block, then a single diatonic non-chord-tone sounds on top ~800 ms later. Identify the test note's extension label (`9`, `11`, `13`, `maj7`, `b7`, …). Chord and test note can be auditioned independently; the Challenge scores a fixed number of rounds.
- **Flavor.** A cadence (I–V–I / i–V–i) sets the key, then a random diatonic chord sounds; identify its scale degree and flavor. Degree and flavor chips audition for ear comparison; the Challenge scores rounds. The flavor palette now includes **6th** and **add9** alongside the previously-covered 9th and 11th extensions.
- **Inversions.** Plays a chosen chord type — maj / min / 7th / extended / sus / dim / aug, or a mix you select from the palette — in a random inversion. Identify whether you're hearing **root position, 1st, 2nd, or 3rd inversion** (3rd only exists for 7th chords). Tap any inversion to audition and compare by ear; the Challenge scores rounds.
- **Aug/Dim.** Drills **augmented vs diminished** by ear, with optional 7th / extended forms (`dim7`, `m7b5`, `7#5`, `maj7#5`). Audition each candidate quality at the current root to compare; the Challenge scores rounds.
- **Intervals.** Plays a reference tone then a target note a chosen interval away (ascending / descending / mixed); identify the interval from unison to the octave.

The diatonic Extended / "Mix all" pool likewise reaches **6th** and **add9** (the 9th and 11th were already present).

### 🎻 Cavaquinho Progressions

A cavaquinho-only screen for practising the functional chord **sequences** of samba (from a Portuguese cavaquinho course): quadradinho (I VI7 ii V7), the basic minor and extended "médio" sequences, and more. Pick a sequence, transpose to any key (starts in **G**), and loop it with a **voice-led fretboard** — a neck-position scroller steps through up to five CAGED-like voicings per chord. A **Songs ♪** button lists real samba songs (curated from the CifraClub top-30) that use the selected sequence's harmonic family, with tap-to-search links. Available only when the cavaquinho is the selected instrument.

### 🥁 Rhythmic Units

A **Rhythm** menu entry to learn & train the basic one-beat units of rhythm. Two sections — plain units (quarter; two eighths; four sixteenths; eighth + two sixteenths; two sixteenths + eighth; sixteenth-eighth-sixteenth; dotted-eighth + sixteenth; eighth triplet) and units **with rests** (eighth + eighth-rest, off-beat sixteenths, and more). Each unit is a card with a small **music-notation thumbnail** (rendered on a Canvas: noteheads, stems, beams, rests, flags, dots, triplet bracket); tap a card to loop that one-beat cell at a **controllable BPM**, clicked on a synthesized woodblock-like tick with the downbeat accented.

### 🥁 Drum machine

A samba/percussion looper tab (step sequencer) with **Surdo, Tamborim, Pandeiro, and Agogô** tracks. Per-track mute/solo, tap-to-cycle per-cell voices, and per-instrument voice auditioning. Each instrument also has its own **volume slider** (0–100 %), inside that instrument's voice popup (tap the instrument name to open it) and applied as a per-voice gain at mix time. Designed to play alongside the chord-progression looper as a backing track.

**Configurable meter.** The loop's **number of bars** (a stepper), **time signature** (2/4, 3/4, 4/4, 6/8, …), and **division** (1/4, 1/8, 1/16, 1/32) are user-selectable via the bars stepper and two dropdowns. The default is **2 bars of 2/4 in sixteenths (16 slots)**. The step grid, the beat/bar separators, and the caption all adapt to the chosen meter, and playback timing is **division-aware**.

**Loop translate / shift.** A control rotates the entire loop by ±n slots with wrap-around — **◀ / ▶** shift by one, or type an amount and press **Go** to shift by that many slots.

As of **1.3.1**, the drum voices play **bundled one-shot WAV samples** — real recorded Latin-percussion hits shipped in `app/src/main/assets/drums/<instrument>_<voice>.wav` and decoded to mono 44.1 kHz on first use by a small [`WavDecoder`](audio/src/main/kotlin/app/guitar/audio/WavDecoder.kt) (it handles PCM 8/16/24/32-bit and 32-bit float, any channel count, and linearly resamples off-rate files). As of **1.4.0** the samples are re-trimmed to a tight onset so each hit lands squarely on the beat (fixing a slight perceived lateness). If a sample file is missing, that voice **falls back to the built-in synth** ([`PercussionSynth`](audio/src/main/kotlin/app/guitar/audio/PercussionSynth.kt)) so the drum machine always sounds. The per-instrument voice layout (also reflected by the synth fallback) is:

- **Surdo** (3 voices): an open ringing bass boom, a muted (damped) bass, and a light muted tap.
- **Tamborim** (3 voices): a high, fast-attack "clack", a muted (choked) clack, and a light tap.
- **Pandeiro** (5 voices): two low-mid bass notes (open + muted), a slap, and two jingle/platinela shimmers (one slightly higher).
- **Agogô** (2 voices): a low bell and a high bell.

> ⚠️ The bundled sample files are **placeholders** and must be swapped for properly-licensed samples before any public release.

**Swing.** A **Swing** slider (0–100 %, 0 = straight) applies a Brazilian 16th-note swing that progressively delays the off-16ths (the "e"/"a" of every beat — the odd slots) toward a triplet/hemiola lilt (≈2:1 around 66 %, up to 3:1 at 100 %). The loop's total length is preserved — only the internal subdivision shifts (see [`PercussionTiming.swungSlotMs`](theory/src/main/kotlin/app/guitar/theory/PercussionPattern.kt)).

**Erase tool.** An **Erase** toggle makes tapping a cell clear it (instead of cycling its voice) — handy for wiping a busy row without cycling every cell through all its voices. Long-press-to-clear still works regardless.

**Save / load custom beats.** Name and save the beat you've built, then reload it later from the **Load…** menu; the built-in groove is always available as **"stock samba"**. Saved beats persist across launches via DataStore.

The page **scrolls vertically**, and each track's name + Mute/Solo (M/S) toggles sit in a fixed-height row so they stay fully visible in both portrait and landscape (nothing gets clipped on short screens). A **2-finger pinch zooms and pans the loop grid** with **independent X/Y scaling (aspect-ratio change)** — stretch it wider or taller, which is handy in portrait where the default grid looks squished (each axis 0.4×–4×, clamped pan). A single finger **drag-pans the grid once it's zoomed**; when not zoomed, a single finger still scrolls the page and taps cells, because the zoom transform is a pure render-layer effect that doesn't disturb per-cell hit-testing.

### 🎛️ Instruments, tuning + audio

- **Two instruments:** Guitar (6-string) and Cavaquinho (4-string, default **DGBD**). Switching instruments resets to that instrument's default tuning and adjusts chord-shape fret-span comfort.
- **Preset tunings** — Guitar: Standard, Drop D, DADGAD, Open G, Open D, Half-step down, Whole-step down. Cavaquinho: DGBD, DGBe.
- **Custom tunings** — edit each string ±1 semitone or ±1 octave; save by name; persists.
- **Selectable guitar sounds** — a Karplus-Strong **synth** plus **sampled** Acoustic (steel), Nylon (classical), and Electric banks (CC0 / GPL-with-audio-exception sources), loaded on demand with a nearest-sample pitch-shift resampler; the synth is always the fallback.
- **Per-sound tone** — a real-time **3-band EQ** (bass/mid/treble shelves + peak) and a **reverb** amount (default a subtle 3 %), each stored per sound. An **A/B engine toggle** compares the modern voice-graph engine (envelopes, constant-power panning, soft limiter, per-voice reverb send) against the legacy engine.
- **Strum spread** slider — the gap (0-150 ms) between consecutive chord notes, shared by single strums, the loop, and ear training.
- **Ring sustain** slider — how long every note rings, from 0.3 s (staccato) to 4 s (drone).
- **First-open defaults** — the app opens on a **light** theme, ear training starts on **Major triads only** with shell voicings, and the neck shows **interval labels**.

---

## Tech stack

**Android** (primary):

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin 2.1** | JVM-native, modern, no JS toolchain |
| UI | **Jetpack Compose** (Material 3) | Declarative, theme-friendly, smooth animations |
| Audio out | **AudioTrack** (AAudio path) + Karplus-Strong DSP + samples | Sub-50 ms tap-to-sound on hardware |
| Audio in | **AudioRecord** + custom YIN | ~46 ms windows, ±2 ¢ accuracy |
| Persistence | **DataStore Preferences** | Coroutine-friendly modern replacement for SharedPreferences |
| Build | **Gradle 8.11** with the Kotlin DSL + version catalog | Standard for new Android projects |
| Tests | **JUnit 5** + `kotlin.test` | Fast, modern, parameterizable |
| Min / Target | **API 26 (Android 8.0)** / **API 34 (Android 14)** | Low-latency AAudio + adaptive icons; current stable |

**Web** (`chorect-web`, parity port):

| Layer | Choice |
|---|---|
| Language | **TypeScript** (strict) |
| UI | Hand-rolled DOM + Canvas (no framework), explicit render pass in place of Compose recomposition |
| Audio | **Web Audio API** (buffer sources, biquad EQ, convolver reverb, dynamics compressor) |
| Persistence | `localStorage` (mirrors the DataStore keys) |
| Build / host | **Vite** + `tsc --noEmit`, deployed to **GitHub Pages** via Actions |

The repo is a multi-module Gradle build plus the web port:

```
Chorect/
├── theory/       ← pure Kotlin, zero Android deps — KMP-ready for iOS later
├── audio/        ← Android library: AudioTrack engine, Karplus-Strong + sample player, YIN, MicInput
├── app/          ← Compose UI: fretboard, tuner, loop, ear training, cavaq progressions, rhythm units, drums
└── chorect-web/  ← TypeScript/WebAudio parity port; src/theory mirrors the Kotlin theory module
```

---

## Getting started

### Prerequisites

- **Windows / macOS / Linux** development host
- **JDK 17 or 21** (Android Studio bundles a compatible JBR)
- **Android SDK** with **API 34** platform installed
- An Android emulator (a Pixel-class AVD with `x86_64` system image) **or** a real device with USB debugging enabled

For Windows setup specifically, see [ANDROID_SETUP.md](ANDROID_SETUP.md) — it walks through hardware virtualization, SDK install, `JAVA_HOME` / `ANDROID_HOME`, and PATH cleanup.

### Build and run

From the project root:

```sh
./gradlew :app:installDebug                                    # build + push to the connected device/emulator
adb shell am start -n app.guitar/app.guitar.app.MainActivity   # launch
```

The debug APK is named after the version, e.g. `Chorect_beta_V1.6.0.apk`, and prior releases are archived under `releases/`.

**On Windows** you can also just double-click `launch-app.bat` — it starts the emulator (with audio) if needed, builds, installs, and launches in one shot.

### Web version

The parity web port lives in `chorect-web/`:

```sh
cd chorect-web
npm install
npm run dev      # Vite dev server
npm run build    # tsc --noEmit + vite build (the CI gate)
```

It deploys to GitHub Pages from `origin/main` via the "Deploy web to GitHub Pages" Actions workflow. The theory logic under `chorect-web/src/theory` is a hand-kept mirror of the Kotlin `theory` module — changes to the music engine are applied to both.

### A note on emulator audio

The Android Emulator on Windows often defaults to an audio backend that adds 100-300 ms of latency on top of the system's minimum buffer. The `launch-app.bat` script passes `-audio winaudio` to force the lower-latency Windows audio path; if you launch the emulator yourself, do the same:

```sh
emulator -avd Pixel_7 -audio winaudio
```

The Tuner requires the **microphone**. The emulator's mic is silent, so to test the YIN pitch detector live use a real device. (The algorithm itself is verified by unit tests against synthetic sine waves at known frequencies.)

For an accurate feel for the audio latency, test on a real device. On a Pixel-class phone, tap-to-sound is ~20-40 ms.

---

## Project structure

```
Chorect/
├── app/                                       # Android application (Compose UI)
│   └── src/main/kotlin/app/guitar/app/
│       ├── MainActivity.kt                    # entry point; single-Activity, orientation-aware layout
│       ├── Shell.kt                           # "Signal" bottom tab bar / landscape rail (configurable pick-4)
│       ├── AppState.kt                        # reactive Compose state holder
│       ├── Theme.kt                           # light/dark/auto colour schemes + 5-accent picker
│       ├── FretboardView.kt                   # Canvas-drawn fretboard (fixed-ratio, pinch-zoom + pan)
│       ├── Screens.kt                         # Fretboard (Chord/Scale/Play) + Options sheets, Loop screen
│       ├── Transport.kt                       # persistent transport dock (play / BPM / tone chip)
│       ├── TunerScreen.kt + TunerState.kt     # mic-driven quarter-ring tuner with on-the-fly tuning
│       ├── LoopScreen.kt + Loop.kt            # progression looper with per-slot voicing/strum
│       ├── EarTrainingScreen.kt + EarTrainingState.kt   # 6 ear-training sub-modes, Practice + Challenge, Library
│       ├── CavaqProgressionsScreen.kt + CavaqProgState.kt  # cavaquinho functional-sequence looper + neck
│       ├── RhythmUnitsScreen.kt + RhythmUnitState.kt   # rhythmic-units trainer (notation cards + click loop)
│       ├── DecomposeScreen.kt                 # chord-tone breakdown reference
│       ├── SambaLooperScreen.kt + SambaLooperState.kt   # percussion step-sequencer (drum machine)
│       └── TuningRepository.kt                # DataStore-backed persistence (incl. challenge high scores)
│
├── audio/                                     # Android library
│   └── src/main/kotlin/app/guitar/audio/
│       ├── AudioEngine.kt                     # interface + no-op
│       ├── AudioTrackEngine.kt                # modern voice-graph engine (envelope/pan/reverb/limiter)
│       ├── LegacyAudioTrackEngine.kt + SwitchableAudioEngine.kt  # A/B engine toggle
│       ├── VoiceMixer.kt Freeverb.kt Panner.kt ThreeBandEq.kt    # mixer bus + effects
│       ├── PluckedSynth.kt                    # pure-Kotlin Karplus-Strong DSP
│       ├── SampleInstrument.kt + GuitarBankLoader.kt  # multisample player + bundled guitar banks
│       ├── PercussionSynth.kt                 # pure-Kotlin percussion voices (synth fallback)
│       ├── WavDecoder.kt                       # RIFF/WAVE → mono 44.1 kHz, for bundled samples
│       ├── PitchDetector.kt PitchAnalysis.kt  # pure-Kotlin YIN + Hz↔MIDI↔cents (configurable A4)
│       └── MicInput.kt                        # AudioRecord wrapper
│
├── theory/                                    # Pure-Kotlin music theory (KMP-ready)
│   └── src/main/kotlin/app/guitar/theory/
│       ├── PitchClass.kt Interval.kt Note.kt  # core types
│       ├── Instrument.kt                       # Guitar / Cavaquinho
│       ├── Tuning.kt Tunings.kt Fretboard.kt  # instrument model + preset/custom tunings
│       ├── ChordLibrary.kt ChordQuality.kt ChordShape.kt
│       ├── ChordShapeGenerator.kt             # CAGED + brute-force fallback
│       ├── CagedShapes.kt                     # 5 canonical CAGED templates × qualities
│       ├── JazzShellVoicings.kt               # drop-2 dictionary
│       ├── VoiceLeading.kt                    # min-movement voicing picker
│       ├── ScaleLibrary.kt Scale.kt ScalePosition.kt FretboardOverlay.kt
│       ├── EarTraining.kt                     # Roman-numeral diatonic + harmonic-minor + curated advanced progressions, resolve()
│       ├── ProgressionSongs.kt                # famous-song examples per progression (library)
│       ├── CavaqSequences.kt CavaqSongs.kt    # cavaquinho functional sequences + song families
│       ├── RhythmUnits.kt                     # basic one-beat rhythmic units (with/without rests)
│       ├── Inversions.kt Note2Chord.kt IntervalTrainer (in EarTraining.kt)  # more ear-training generators
│       ├── Percussion.kt PercussionPattern.kt # drum-machine model
│       ├── Fingering.kt NoteSpeller.kt        # display helpers
│       └── TuningCodec.kt                     # serialization
│
├── chorect-web/                               # TypeScript/WebAudio parity port (Vite → GitHub Pages)
│   └── src/
│       ├── theory/                            # hand-kept mirror of the Kotlin theory module
│       └── app/                               # DOM+Canvas UI, WebAudioEngine, per-screen state/UI
│
├── docs/superpowers/specs/                    # design specs (e.g. rhythmic-units, cavaquinho, Signal)
├── releases/                                  # archived Chorect_beta_V<version>.apk builds
├── ANDROID_SETUP.md                           # Windows setup walkthrough
├── requirements.md                            # original product spec
└── launch-app.bat                             # double-click launcher for Windows
```

---

## Running tests

```sh
./gradlew test                          # all module tests
./gradlew :theory:test                  # theory engine only (pure JVM, fast)
./gradlew :audio:testDebugUnitTest      # audio DSP + YIN + cents
```

**700+ tests passing**, zero failures (most live in the pure-JVM `theory` module; ~313 static `@Test` methods plus a couple of `@TestFactory` methods that fan out into many generated cases, so the executed count runs well past the static `@Test` count). Highlights:

- **CAGED templates** — every shape verified against its canonical open-position voicing; every chord root produces 5 distinct ascending positions.
- **Jazz drop-2 dictionary** — every inversion of `maj7` / `m7` / `7` / `m7b5` / `dim7` / `6` / `m6` confirmed to contain only the correct chord tones.
- **YIN pitch detector** — locks within ±2 ¢ on A4, low E2, high E4, D3; rejects pure noise and silence; picks the fundamental over a harmonic.
- **Cents math** — A4=440 maps to MIDI 69 with 0 ¢; custom A4 references shift the grid as expected.
- **Ear-training progression resolver** — every Roman degree in major and minor resolves to a parseable chord symbol; "ii"+"m7" displays as `ii7` (not `iim7`); minor is natural (`v`) with the harmonic-minor dominant available as a marked-bar option; every curated advanced (non-diatonic) and harmonic-minor progression resolves to playable chords in any key.
- **Chord inversions** — `Inversions.midis()` voices each inversion (root / 1st / 2nd / 3rd) with the correct bass tone, low→high.
- **Note2Chord** — labels for every diatonic non-chord-tone in both major and minor; random sampling covers all 12 roots.
- **Percussion** — pattern cycling / voice selection for the drum machine, the synthesized percussion voices (`PercussionSynth`), the bundled-sample `WavDecoder` (PCM 8/16/24/32-bit + float, multi-channel averaging, resampling), and the save/load pattern codec round-trip.
- **Requirements** spec mirror — the tests from `requirements.md` §13 pass.

---

## Roadmap

| Status | Item |
|---|---|
| ✅ | Core theory engine — notes, intervals, tunings, chord/scale libraries |
| ✅ | CAGED chord shape generator with 5 canonical positions per chord |
| ✅ | Jazz drop-2 voicings dictionary |
| ✅ | Compose fretboard with realistic-string rendering |
| ✅ | Fixed-ratio neck with pinch-to-zoom + drag-to-pan, portrait + landscape |
| ✅ | Persistent navigation rail (Fretboard / Loop / Ear / Drums / Tuner / Options) |
| ✅ | Tuning persistence + custom tunings (editable on the fly from the tuner) |
| ✅ | Karplus-Strong audio engine + continuous-output mixer |
| ✅ | Left-handed mode |
| ✅ | Strum (pick) mode + arpeggio + per-string mutes |
| ✅ | Loop screen — multi-chord-per-bar, per-slot voicing, per-slot strum, build-by-degree |
| ✅ | Chromatic tuner — YIN, ±50 ¢ quarter-ring dial, A4 reference |
| ✅ | Ear training — Progression / Note2Chord / Flavor / Inversions / Aug-Dim, each with Practice + Challenge |
| ✅ | Advanced (non-diatonic) progression library — ~24 curated borrowed/secondary-dominant/turnaround progressions with teaching notes |
| ✅ | Progression challenge with high-score table (score + completion time) |
| ✅ | Voice-leading auto-voicing in Loop + Ear training |
| ✅ | Live chord on the fretboard while the loop plays |
| ✅ | Adaptive launcher icon (rosewood fretboard slice) |
| ✅ | Cavaquinho instrument — instrument toggle, DGBe/DGBD tunings, 4-string fretboard, per-instrument timbre |
| ✅ | Samba drum machine — percussion step-sequencer (Surdo / Tamborim / Pandeiro / Agogô) with bundled one-shot WAV samples (synth fallback), save/load custom beats (+ "stock samba"), always-visible mute/solo, vertical scroll, Brazilian 16th-note swing, erase tool, per-instrument volume slider, and 2-finger aspect-ratio zoom + drag-pan |
| ✅ | Ear-training degree-keyboard answering (1.6.0) — Major/Minor shift, plain-number references, challenge Prev/Next history |
| ✅ | Sampled guitar instruments (1.23) + per-sound 3-band EQ (1.24) + reverb (1.26) + modern voice-graph audio engine with A/B toggle (1.22) |
| ✅ | Progression Library + famous-song examples (1.25+) with YouTube/Spotify search links |
| ✅ | "Signal" whole-app GUI redesign (2.0) — bottom tab bar / landscape rail, configurable tabs, transport dock, unified tone sheet, light/dark/auto + accent picker |
| ✅ | **Web port** (`chorect-web`) — TypeScript/WebAudio parity port deployed to GitHub Pages |
| ✅ | Cavaquinho Progressions (2.5) — functional samba sequences, voice-led neck, song library; DGBD default tuning |
| ✅ | True jazz shell voicings (2.10) + compact cavaquinho voicings |
| ✅ | Ear-training harmonic-minor progressions (2.14) — major V7 → i cadences with a dedicated V7 answer key; Advanced II / Suspended categories |
| ✅ | Rhythmic Units (2.15–2.16) — learn/train the basic one-beat units (with & without rests), notation thumbnails + BPM click loop |
| 📅 | Cavaquinho curated chord library |
| 📅 | Custom-chord favorites |
| 📅 | iOS port via Kotlin Multiplatform |

---

## Architecture notes

### Module boundaries

The three-module split (`theory` / `audio` / `app`) is enforced by Gradle. The `theory` module has **zero Android dependencies** — it compiles to plain JVM bytecode and is fully unit-testable without an emulator. This makes both testing and a future Kotlin Multiplatform port straightforward; only the `app` layer (Compose) and `audio` layer (AudioTrack + AudioRecord) would need iOS-specific replacements.

The **web port** (`chorect-web`) re-implements the same design in TypeScript: `src/theory` is a line-by-line mirror of the Kotlin theory engine (same algorithms, same data), and `src/app` re-creates each screen's state + UI over a plain DOM/Canvas render pass in place of Compose recomposition, with a `WebAudioEngine` standing in for `AudioTrackEngine`. Keeping the two theory copies in lock-step is a deliberate constraint — every music-engine change lands in both, and the web build's `tsc --noEmit` (run in CI) is the type gate since there's no local Node toolchain.

### Audio out

The output engine went through three iterations before settling on a continuous-output mixer:

1. **Per-tap `AudioTrack` creation** — caused glitches and resource exhaustion under rapid tapping.
2. **Persistent track with pause / flush / play per note** — eliminated the leak but introduced an audible click at every interrupt.
3. **Continuous-output mixer (current)** — one `AudioTrack` in `MODE_STREAM` + `PERFORMANCE_MODE_LOW_LATENCY`, kept in `PLAYING` state for the engine's lifetime, with a dedicated high-priority output thread mixing voices in real time. Synthesis runs on a separate executor so the UI thread never blocks. The output loop intentionally **skips writing when there are no active voices** so the ring buffer stays empty and a fresh tap plays within ~6 ms of buffer latency.

### Audio in (Tuner)

`AudioRecord` at 44.1 kHz mono PCM, 2048-sample windows (~46 ms). Each window is fed to the YIN detector (de Cheveigné & Kawahara, 2002): difference function → cumulative-mean-normalized difference → absolute-threshold local minimum → parabolic interpolation for sub-sample tau refinement. The output frequency is converted to (nearest equal-tempered MIDI note, signed cents) via the configurable-A4 [`PitchAnalysis`](audio/src/main/kotlin/app/guitar/audio/PitchAnalysis.kt). A per-note smoothing buffer + an RMS energy gate keeps the dial from twitching on silence.

### Theory engine

Built around two value classes (`PitchClass` for the 12-tone scale degree, `Midi` for absolute pitch), plus the data classes `Note`, `Interval`, `Tuning`, `FretPosition`. Every algorithm is a pure function on these types.

For **chord shape generation**, the `ChordShapeGenerator` short-circuits to a canonical voicing dictionary when one exists for the (style, quality, tuning) tuple — `CagedShapes` for Standard mode, `JazzShellVoicings` for Shell mode — and falls back to a brute-force constraint-filtered enumeration otherwise (with a per-instrument max fret span). CAGED templates are encoded as relative fret offsets from the root fret on the shape's primary string, so transposing to any chord root is just a single integer add.

For **ear training**, `EarTraining.resolve()` maps a `(degree, key, mode, chord-type-level)` tuple to a `(chordSymbol, romanLabel)` pair. Diatonic-role tables for major and minor encode the triad / seventh / extended quality at each scale degree; minor uses the natural `v` by default, and a `MINOR_DOMINANT` role supplies the harmonic-minor major V for progressions that mark a bar as a dominant (the V7→i cadence family, on by default). Beyond the diatonic generator, `ADVANCED_PROGRESSIONS` is a curated list of named non-diatonic progressions — each chord is stored as a `(semitone-above-tonic, quality, Roman-label)` triple so the whole progression transposes to any key with a single add per chord, and `Inversions.midis()` lifts the bottom *k* chord tones an octave to voice the *k*-th inversion for the inversions trainer.

---

## Contributing

This is a personal project at the moment. If you're interested in contributing, open an issue first so we can sync on direction.

---

## License

Not yet decided. Treat the code as **all rights reserved** until a `LICENSE` file is added. If you want to use a piece of it, ask.

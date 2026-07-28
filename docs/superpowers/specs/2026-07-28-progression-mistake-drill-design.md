# Progression Mistake-Drill — Design

Date: 2026-07-28
Status: approved, building

## Goal

Add a new **Drill** tab to Ear Training that tracks which chord progressions the
user gets wrong most often in the Progression Challenge, ranks them by mistake
count, and lets the user loop any one of them — with voicing control — to drill
it into memory by repetition.

Both platforms (web `chorect-web` + Android Kotlin) get the identical feature.

## 1. Mistake tracking

- **Metric (user choice): raw lifetime wrong-count.** Rank progressions by total
  number of times answered wrong, highest first.
- **Progression identity.** Diatonic progressions have no stable id today. Add a
  canonical key: `progressionKey(prog)` → `"maj:1,5,6,4"` / `"min:1,4,5,1@2"`
  (mode prefix `maj`/`min`, degrees comma-joined, optional `@`-joined
  `dominantBars` to distinguish natural-minor vs harmonic-minor variants that
  share degrees). Inverse `progressionFromKey(key)` reconstructs the Progression.
  Shared theory helpers in `eartraining.ts` / `EarTraining.kt`.
- **Where counted.** On Progression-Challenge completion (`advanceChallenge`'s
  final branch, after `finalizeCurrentQuestion`), iterate the 10 logged questions
  and, for each whose `challengeAnswers[i] === false` (not all bars correct),
  record one miss for that question's progression. Counting once at completion
  avoids the double-count that a per-question hook would hit when the user steps
  back/forward through questions.

## 2. Persistence

- New map `progressionMistakes: key → misses (int)`.
- Web: field on `Persisted` (localStorage blob), read in `load()`, written in
  `save()`, mutated via `commit()`. Methods: `recordProgressionMistake(key)`,
  `clearProgressionMistake(key)`, `clearProgressionMistakes()`.
- Android: DataStore string key `progression_mistakes`, encoded `key=count;...`.
  Flow reader + suspend setters mirroring the three web methods; AppState
  wrappers launch them.

## 3. Drill tab

- New `EarSubMode.Drill`, added to the sub-mode chip row (overflow group) and the
  render dispatch. Drill ignores the Practice/Challenge toggle (that toggle is
  hidden for Drill).
- Lists every progression with ≥1 recorded miss, **ordered by miss count desc**.
  Each row: roman-numeral line (`romanLineFor` on the reconstructed progression),
  "missed N×", a **Loop ▶/■** button, and a **✕** to drop it (resets its count).
- Empty state: a hint that missed progressions from the Progression Challenge
  land here.

## 4. Drill loop + voicing

Self-contained looper (like the library-preview looper) so it can't corrupt a
quiz. Fixed key per mode (major→C, minor→A) for consistent repetition.

- **Default voicing = voice-led shell**, exactly like the quiz loop: E-shape on
  bar 1 then `pickMinMovement`, generator in shell style (`earShellVoicing`
  drives `earStyle()`), thinned by `earMidis`. This is the "initial voicing"
  requirement.
- **Per-bar override.** `drillInversions: (int|null)[]`, null = auto (the shell
  voice-led shape above). When set, that bar plays a close-voiced full chord via
  `inversionMidis(rootMidi, quality, inversion)` so the 5th is present and its
  position (bass vs above the root) is controlled — the stated pain point. Shell
  drops the 5th, so the override deliberately switches to the fuller voicing.
- **Controls (both models, unified):** one chip per bar; tapping cycles the
  inversion (Root/1st/2nd[/3rd]) and the chip shows BOTH the inversion name and
  the resulting bass note ("1st inv · 3rd in bass"). A global **"5th in bass"**
  quick-toggle snaps all bars to 2nd inversion at once. A **"Auto"** reset clears
  overrides back to voice-led shell.
- Reuses `progBpm` + `playEarChord`.

## Non-goals (v1)

- Advanced / circle-of-fifths progressions (different data type, not part of the
  scored Progression Challenge).
- Recency weighting / miss-rate (user chose raw count).
- Persisting the drill's chosen voicings (they reset to Auto each open).

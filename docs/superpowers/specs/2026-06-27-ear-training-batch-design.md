# Ear-training batch + chord-decomposition tool — design

**Date:** 2026-06-27
**Scope:** six requests (notes #1–#6). Implemented in BOTH the Android app (Kotlin)
and `chorect-web` (TypeScript), kept at parity. Version bump: **v1.9.0** (new features).

Existing structure (for reference):
- Top-level nav (`Sheet`): Fretboard / Ear / Drums / Tuner.
- Ear-training sub-modes (`EarSubMode`): Progression, Note2Chord, Flavor, Inversions, AugDim.
  Each runs in Practice or Challenge (`EarMode`).

---

## #1 — Aug/Dim practice: emphasize Replay, add Previous, keep history

**Problem:** the prominent (filled) button is "New chord ▶", which users hit by
mistake (losing the current chord); Replay is the de-emphasized outlined button.

**Fix (Aug/Dim Practice view):**
- Make **Replay** the filled/primary button; make **New chord** secondary (outlined).
- Add a **◀ Previous** button that steps back through previously-drawn chords.
- Maintain a bounded history (the drawn (root, quality) tuples, cap ~32) per
  section so Previous/Next navigate without re-randomizing. "New chord" still
  appends a fresh random draw to the history and moves to it.

State change: replace the single "current chord" fields with a `history: List<…>`
+ `historyIndex`. `playAugDim()` plays `history[historyIndex]`. New helpers
`augDimPrev()` / `augDimNext()` (Next only enabled when not at the end).

## #2 — Aug/Dim: optional "show on fretboard"

Add a **"Show chord on fretboard"** Switch + `FretboardView` to the Aug/Dim
practice view, mirroring the existing ProgressionView block (lines 287–312): when
on, render the drawn chord's notes on the neck (overlay = the chord tones). Uses
the same `showFretboard`-style flag and `FretboardView` already in the screen.

## #3 — Same treatment for the other sections

Apply #1 (Replay primary + Previous/Next history) and #2 (fretboard toggle) to:
- **Inversions** practice (same New/Replay button pair; chord drawn from root+quality+inversion).
- **Note2Chord** practice (already has New + Next; make Replay-both primary, add
  Previous via history; add fretboard toggle showing the triad).
- **Flavor** practice already has its own Replay-cadence / Play-chord buttons and a
  reveal; add a fretboard toggle (show the drawn chord) and a Previous/Next over
  drawn chords. (Progression already has a fretboard + Next; leave as is.)

A single shared history pattern is added per section's state (small, local).

## #4 — Flavor: only present diatonic flavors that fit the key

**Problem:** the "Flavor (tap to hear)" guess chips iterate **all enabled
qualities** (`flavorAllowed`), and `auditionFlavorQuality` plays the current degree
with any of them — so in a minor key you can pick/hear a quality that isn't
diatonic anywhere ("dominant" on a degree that's diatonically minor), which
"doesn't make sense in that key."

**Fix:** filter the presented quality chips to the **diatonic quality set for the
current key/mode** — the qualities produced by `diatonicFlavorCandidates(flavorMode,
flavorAllowed)` (union over degrees), intersected with the enabled palette. The
drawn quality is always in this set (it's already diatonic). When a degree is
selected as a guess, narrow further to the qualities diatonic **for that degree**.
The drawn chord generation is unchanged (already diatonic).

---

## #5 — NEW top-level tool: "Decompose" 🧩 (chord → shell + upper triad)

A new nav-rail entry (`Sheet.Decompose`). **Not a quiz** — a visual reference that
shows, on the fretboard, how an extended chord splits into a **shell (root + guide
tones)** in the bass and a **triad** stacked on top — the pianist's left-hand
shell / right-hand upper-structure idea, applied to guitar.

**Controls:** root picker (12), and a chord-type picker covering the full set:
- 6 / m6
- maj7 / 7 / m7 / m7♭5 / dim7
- 9 / maj9 / m9, 11 / m11, 13 / maj13 (and a few common alts: 7♯9, 7♭9, 7♯11, 7♭13)

**Decomposition model** (`theory` module, pure + unit-tested):
`decompose(root, quality) -> { shell: List<Interval-from-root>, upper: { triad
quality, rootDegree } , notes }`. Rules (semitones from root):
- **6 chords:** bass = root (+ optionally 3rd); upper triad = the major/minor triad
  whose root is the **6th** (i.e. a triad starting a M6/9 semitones up) — e.g. C6 =
  C in bass + Am-ish (A–C–E) → the "triad starting 1½–2 steps above the bass" you
  described. Concretely C6 ↔ Am/C: upper = Am triad.
- **7th chords:** bass = root (the shell root+3+7), upper triad starts on the **3rd
  degree** of the chord and is major / minor / diminished depending on quality —
  e.g. Cmaj7 = C + Em (E–G–B); C7 = C + Edim/E… (E–G–B♭ → Edim); Cm7 = C + E♭maj
  (E♭–G–B♭); Cm7♭5 = C + E♭m. (This is your "triad from the 3rd degree" rule.)
- **9/11/13:** bass = shell (root–3–7); **upper triad = a major/minor/dim/aug triad
  built on the 3rd, 5th, or 7th** that spells the extension — the classic
  upper-structure triads (e.g. D/C7 gives 9♯11; the table encodes one canonical
  upper triad per quality).

**Display:** the FretboardView shows the full chord with two visual groups —
**bass/shell notes** in one colour, **upper-triad notes** in another — plus a text
line: "C13 ≈ C7 shell (C E B♭) + D major triad (9–♯11–13)". A "play" button
arpeggiates shell then triad. No scoring, no key context needed.

(Reuses existing `Fretboard`, `ChordShapeGenerator`/overlay, and audio symbol
playback. The new theory is a `ChordDecomposition` object + table.)

## #6 — NEW ear-training sub-mode: "Intervals" (ascending/descending ID)

A 6th `EarSubMode` (`Intervals`), **challenge-only** (10 questions), matching your spec.

**Flow:**
1. Before start: choose **direction** = Ascending / Descending / Mixed; optional
   **transpose** (the reference key, if the default is uncomfortable).
2. On start: play **I–V–I** in a major key (the tonic reference).
3. 10 questions: each plays the **tonic then a target note** an interval away
   (direction per the chosen setting). The user picks the interval from **13
   buttons**: unison, m2, M2, m3, M3, P4, TT, P5, m6, M6, m7, M7, octave.
4. A **"Play tonic"** button is always available to re-anchor; a **Replay** button
   replays the current interval. Transpose ± semitone available during the run.
5. Score out of 10, with the standard done-card (restart / quit), and the score is
   recorded like the other challenges.

**Theory:** intervals are pure semitone offsets (0–12); generation picks a random
interval (optionally constrained), a random base note near the tonic register, and
a direction. Reuses single-note playback from the existing audio path.

---

## Files touched

**App (Kotlin):**
- `theory/`: new `ChordDecomposition.kt` (+ test); `EarTraining.kt` interval helpers
  (+ test); a diatonic-quality helper for #4.
- `app/`: `EarTrainingState.kt` (history for #1/#3, fretboard flags for #2/#3,
  flavor filtering #4, new Intervals sub-mode #6), `EarTrainingScreen.kt`
  (button emphasis, Previous, fretboard toggles, Intervals view, filtered flavor
  chips), new `DecomposeScreen.kt` (#5), `AppShell.kt` + `Sheet` enum (new rail
  entry), `EarSubMode` gains `Intervals`.

**Web (TypeScript), mirrored:**
- `src/theory/`: `chordDecomposition.ts`, interval helpers in `eartraining.ts`.
- `src/app/`: `earTrainingUI.ts` (same view changes + Intervals), new
  `decomposeUI.ts`, nav wiring, `test/verify.ts` cases.
- Version → 1.9.0 in `package.json`; CI deploy as before.

## Testing
- Theory unit tests: decomposition tables (shell+upper notes for representative
  chords), interval semitone math, diatonic-quality filtering for #4.
- Web `verify.ts`: parity checks for the same.
- Manual: each section's Replay/Previous/fretboard; Flavor chips in a minor key
  show only diatonic flavors; Decompose renders two-colour groups; Intervals
  challenge runs 10 questions asc/desc/mixed with tonic + transpose.

## Sequencing
Given the size, implement and commit in this order (each independently testable):
**(A)** #1–#3 nav/fretboard + #4 flavor filter (refinements to existing sections);
**(B)** #6 Intervals sub-mode; **(C)** #5 Decompose tool. Port each to web right
after the app side, then one version bump + deploy at the end.

# Cavaquinho mode — design plan

> **Status:** plan only. Implementation deferred until the user signs off.

The cavaquinho is a 4-string Portuguese / Brazilian instrument tuned in the same
fourth-third pattern as the top 4 strings of a guitar, but an octave higher and
on a much smaller body. The smaller body means:

- the **strings are physically closer** (string spacing is tighter)
- the **frets are physically smaller**, so a 5-fret stretch is comfortable where
  a guitarist would only attempt 4
- typical chord voicings span the whole neck, including unusual stretches like
  the user's example **Adim7 = 7 5 4 3**

The goal: make FretPal render and play the cavaquinho on its own neck, with
chord voicings curated for the instrument, switchable from Options.

---

## 1. Tunings to support

The two tunings the user named, both standard:

| Tuning | String 4 (low) | String 3 | String 2 | String 1 (high) | Notes |
|---|---|---|---|---|---|
| **DGBe** | D4 | G4 | B4 | E5 | Portuguese / Madeira standard. Same intervals as guitar strings 4-1. |
| **DGBD** | D4 | G4 | B4 | D5 | Brazilian standard. Re-entrant — the highest string drops back down to D. |

Both have the **same low three strings** (D-G-B). Only the top string differs.

A custom tuning editor (we already have one for guitar) handles the rest.

---

## 2. Chord shapes — what's different from guitar

### 2a. Smaller neck → wider fret span

Our current `ChordShapeGenerator` enforces `maxFretSpan = 4`. On cavaquinho the
classical voicings often stretch to 5 frets — sometimes more.

User example, Adim7 in DGBe:

```
string 4 (D):  fret 7  →  A     (root)
string 3 (G):  fret 5  →  C     (♭3)
string 2 (B):  fret 4  →  D♯/E♭ (♭5)
string 1 (e):  fret 3  →  G     (♭7)        ← this is m7♭5 not dim7
```

(Note: `7 5 4 3` produces R-♭3-♭5-♭7 which is **half-diminished (m7♭5)**, not
the doubly-diminished 7th (`Adim7`). When we wire this up, the canonical
template entry should be filed under `m7b5`. Reach out if you intended `dim7`
proper — that'd need `7 5 4 2` instead, because the ♭♭7 of A is F♯, which is
e-2.)

Either way, the span is **3-7 = 5 frets**, so the algorithm needs
`maxFretSpan = 5` for cavaquinho.

### 2b. No CAGED system to lean on

CAGED was built around the five open chord shapes of guitar (C, A, G, E, D).
Cavaquinho has its own canon: GDAE, AmDmG, etc. don't apply. So we need a
**fresh shape library**, not a translation.

There are two practical approaches:

#### Option A — brute-force, span = 5, on 4 strings
Run the existing constraint-based enumerator with the cavaquinho tuning and a
relaxed span. Pros: zero new theory; works for any chord quality. Cons: the
results will be unranked / unfiltered — many similar voicings clustered near
fret 0, and no canonical "go-to" shape per chord.

#### Option B — hand-curated voicings per quality
Build a table analogous to `CagedShapes.kt`: 3-5 canonical voicings per
chord quality, per tuning. Pros: matches what real cavaquinho players play;
position scroller covers the neck cleanly. Cons: 20 qualities × 2 tunings =
40 tables to author and verify.

#### **Recommendation: hybrid (B for common chords, A as fallback)**

- Hand-curate the 8 most-played qualities (`maj`, `m`, `7`, `maj7`, `m7`,
  `m7b5`, `dim7`, `6`) in both tunings — that's 16 tables of 3-5 voicings each.
- For everything else, the brute-force generator with relaxed span fills in.

The hand-curated voicings can be sourced from established cavaquinho chord
dictionaries (e.g. *Caderno de Acordes de Cavaquinho*, public Brazilian songbook
appendices). I'll lift them with attribution rather than guess.

---

## 3. Code changes

### theory module

| File | Change |
|---|---|
| `Tunings.kt` | Add `cavaqDgbe = Tuning.of("D4", "G4", "B4", "E5")` and `cavaqDgbd = Tuning.of("D4", "G4", "B4", "D5")`. Add an `Instrument` enum (Guitar, Cavaquinho) and group tunings under it. |
| `Tuning.kt` | Already supports n-string lists. Verify nothing assumes `stringCount == 6`. |
| `ChordShapeGenerator.kt` | Promote `maxFretSpan` to a per-call argument (default 4 for guitar). Cavaquinho callers pass 5. |
| `CagedShapes.kt` | Keep as-is — guitar-only. |
| `CavaquinhoShapes.kt` (new) | Mirror of `CagedShapes.kt` but for cavaquinho voicings, keyed by `(quality, tuning)`. |
| `ChordShapeGenerator.shapesFor()` | When the tuning matches a cavaquinho preset, route through `CavaquinhoShapes` first, fall through to brute-force with span=5. |

### audio module

No changes. The synth is pitch-agnostic — it'll happily play cavaquinho midi notes.
(The Karplus-Strong filter coefficients are tuned for guitar-like attack/sustain;
cavaquinho has a brighter ping and shorter decay. A second-tier polish item:
expose an `instrumentTimbre` parameter on `PluckedSynth`.)

### app module

| File | Change |
|---|---|
| `AppState.kt` | Add `var instrument: Instrument` persisted to DataStore. When it changes, swap the default tuning + clear the live tuning override. |
| `TuningRepository.kt` | New key `instrument`. Tuning persistence stays per-instrument (so the user's custom guitar tunings don't leak into cavaquinho mode). |
| `FretboardView.kt` | Already takes a `Tuning` with an arbitrary string count. The wound-vs-plain string heuristic (`woundCutoff = (n+1)/2`) still works; for cavaquinho all 4 strings are nylon/steel of similar gauge — we'll likely render them all as "plain" (lighter strokes). |
| `Screens.kt` (Options sheet) | New segmented row at the top: `Guitar (6-string)` / `Cavaquinho (4-string)`. The tuning preset list below filters to the chosen instrument. |
| `OpenStringLabels` (in FretboardView) | Already case-aware. For DGBD it'll render `D G B d`. |

### docs / launcher

- Mention cavaquinho mode in the README features list.
- Screenshot of the cavaquinho neck.

---

## 4. UI flow

1. Open **Options** → top row "Instrument": `Guitar` / `Cavaquinho`. Switch to
   Cavaquinho.
2. Tuning preset list updates to show only cavaquinho tunings (`DGBe`, `DGBD`,
   `Custom`).
3. Fretboard re-renders with 4 strings.
4. Chord sheet, scale sheet, loop, ear-training all continue to work — they
   pull voicings from `ChordShapeGenerator` which dispatches per instrument.
5. Persisted: instrument, per-instrument tuning, per-instrument custom tunings.

---

## 5. Tests

### Theory
- `CavaquinhoTuningsTest`: open strings for DGBe and DGBD match the expected pitches.
- `CavaquinhoShapesTest`: every hand-curated voicing produces exactly the
  expected chord tones (mirror of the existing `CagedShapesTest` discipline).
- `NeckCoverageTest` extended: for every (quality × root × cavaquinho tuning),
  voicings include at least one with the root on each of the 4 strings.

### App
- Switching instrument from Options resets the live tuning and re-renders.
- The fretboard tap maps correctly with 4 strings (test the math, not the UI).

---

## 6. Phased rollout

1. **Phase 1 — plumbing only.** Add the `Instrument` enum, cavaquinho tunings,
   instrument toggle in Options, persistence. Brute-force chord generator with
   span=5 supplies voicings. Ship a usable-but-unpolished cavaquinho mode.
2. **Phase 2 — curated shapes.** Hand-author the 16 voicing tables for the 8
   common qualities × 2 tunings. The position scroller now shows neat,
   neck-spanning shapes for the chords cavaquinho players actually use.
3. **Phase 3 — polish.** Lighter-gauge string rendering, optional timbre tweak
   in the synth, README screenshot, real cavaquinho user testing.

---

## 7. Open questions for the user

- **Adim7 vs Am7b5**: confirm whether your example `7 5 4 3` should be filed
  under `dim7` or `m7b5`. (The notes spell m7b5, so by default I'll file it
  there.)
- **Body / fret cosmetics**: do you want the cavaquinho neck visually
  distinguishable from the guitar neck (e.g. lighter wood, narrower nut),
  or is just "4 strings" enough?
- **Re-entrant tuning UX**: in `DGBD` the high D is *lower* than the B below
  it in MIDI. Should chord shapes account for re-entrant pitch ordering, or
  treat each string by its string-index regardless of pitch? (Default would
  be the latter — string index is what the player's finger sees.)

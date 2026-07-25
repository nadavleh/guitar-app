# Scales & Triads — guitar CAGED practice trainer

Date: 2026-07-25 · Status: approved (design Q&A) · Platforms: Android + web · Guitar only

A new top-level menu item, **"Scales & Triads"**, with three tabs, to drill the
CAGED major/minor scale system and triad inversions. Reuses the existing
fretboard renderers (`FretboardView.kt` / `fretboardCanvas.ts`),
`FretboardOverlay`, `Scale`, and `AudioEngine`.

## Theory foundation — `CagedScales` (Kotlin `theory/`, TS mirror)

Standard guitar tuning only (E A D G B E, string index 0 = low E … 5 = high e).

- **5 CAGED major-scale boxes** (Positions 1–5), matching the user's image
  ("5-major-scale-patterns-positions-connected"). Each box is identified by the
  string its root sits on (5 unique roots: strings 6/5/4/3/2 — string 1 repeats
  string 6). A box is a per-string set of `(fret, scaleDegree)` in a compact
  window, connected up the neck (Position 1 lowest → 5 highest).
- **Fingering rule (user's convention):** no note is played on the next-higher
  string one fret *below* the root's fret; that pitch is kept on the lower
  string 4 frets up instead — i.e. `(string i-1, fret j-1)` → `(string i, fret
  j+4)`, a unison relocation onto the root's string (works because adjacent
  4th-tuned strings put the same pitch at `+5`/`+4` frets). Result: each box is a
  clean ~4-fret ascending window with no backward reach. Verified visually
  against the image before locking.
- **Derivations from each box** (same shape, different note subsets/labels):
  - major scale (all 7 degrees), major pentatonic (drop 4 & 7), major triad (R 3 5)
  - relative-minor relabel: minor root = the major 6th degree; natural-minor
    scale, minor pentatonic (drop 2 & 6 rel. minor), minor triad. Major & minor
    are the SAME box shapes, 3 semitones apart, differing only in which notes are
    roots — so a "box" is stored once and relabeled.
- **Triad inversions by string group** (Triads tab): for the 4 adjacent 3-string
  groups (6-5-4, 5-4-3, 4-3-2, 3-2-1), the 3 close-voiced inversions of the
  major and minor triad in the chosen key, as fret positions along the neck.
- API sketch: `majorBox(index): CagedBox`, `box.resolve(key, mode, subset):
  List<FretPosition+degree>`, `triadInversions(key, quality): List<group×inv shape>`.
  Unit-tested (degree membership, root count, span ≤ window, no-backward-reach
  invariant, 5 boxes tile the neck, 24 triads enumerated).

### Decoded box model (verified against the image, G major)

Each position = **all major-scale tones inside a fixed fret window**; the window
is `[T+lo, T+hi]` where `T` = fret of the parent-major tonic on the low‑E string
(placed in the lowest octave that keeps the window on the neck). Offsets, read
off the image:

| Box | lo | hi |
|-----|----|----|
| POS1 | -1 | +2 |
| POS2 | +1 | +5 |
| POS3 | +4 | +7 |
| POS4 | +6 | +10 |
| POS5 | +8 | +12 |

Consecutive windows overlap; POS5's top = POS1 + 12 (octave), so they cycle. A
fixed window IS the "no backward reach / +4" rule — a box never contains a note a
fret behind on an adjacent string outside the window.

**Mode = the box's two roots.** For a chosen (major) key K the boxes are K's; the
"minor" pass uses the **relative minor** (root = K's 6th degree = K+9). Subsets:
- FullScale — all 7 major pcs (same notes for both modes; minor = natural minor).
- Pentatonic — the SAME 5 pcs for both (major pent of K = minor pent of K+9);
  only the highlighted root differs.
- Triad — Major: {K, K+4, K+7}; Minor: {K+9, K, K+4} (the relative-minor / vi triad).
Interval labels + `isRoot` are computed relative to the active mode's root.

## Tab 1 — Practice (guided)

- Pick key, or **Random key**. BPM slider. **Audio-demo toggle** (ON = app sounds
  + highlights each note ascending then descending in time; OFF = metronome +
  static box shown, user plays).
- Steps box 1 → 5 up the neck. Per box a 6-step drill: **[triad → scale →
  pentatonic]** for the leading mode, then the same three for the other mode.
  Leading mode **alternates each box** (box 1 major-first, box 2 minor-first, …).
- Current box + drill step shown on the fretboard, roots emphasized; Prev / Next
  / Play-Pause; metronome click.

## Tab 2 — Challenge (unscored)

- **Space** → a random prompt card: {key center · which string the root is on
  (→ box) · major | minor · diatonic | pentatonic}.
- **Reveal** toggle overlays the resolved scale/box on the fretboard (off by
  default — the user plays it themselves, then checks).

## Tab 3 — Triads

- Pick key. Runs **all 12 major triads then all 12 minor** = 24: 4 groups
  (6-5-4 → 3-2-1), 3 inversions each low→high. Each triad plucked together, one
  per beat at a set BPM, highlighted along the neck in order. Play/Stop + BPM.

## Milestones

1. `CagedScales` theory + tests (both platforms), boxes verified vs the image.
2. Fretboard practice player + Practice tab.
3. Triads tab.
4. Challenge tab.
5. Both-platform parity + polish; nav wiring (Sheet/TabDest, guitar-only gate).

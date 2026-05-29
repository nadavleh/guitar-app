# Guitar App — GUI Design

This document is the **single source of truth** for the app's look-and-feel and primary interactions. The Compose code in `app/` should match these specs. Update this doc before changing visual code so we don't drift.

Status: **v1** — Nadav redirected the architecture on 2026-05-29 from a 4-tab layout to a fretboard-centric single screen with a bottom mode bar. This doc reflects v1.

---

## 1. Vision

> An instrument, not a textbook.

**The fretboard is the app.** It is always visible and always the focus. Everything else — tuning choice, chord lookup, scale display, free-pick mode, settings — happens in subsidiary surfaces below or above it. The user should never feel like they've left the instrument.

**Mood**: warm, tactile, focused. Dark by default. Strings look like real strings; wood looks like real wood; lit-up notes feel like the instrument is responding to you. Bright accents are the chord/scale tones lighting up under your fingers.

---

## 2. Design tokens

### 2.1 Colors (hex)

```
Background ───── #0E1014   matte near-black, faint cool tint
Surface ──────── #181B22   lifted card / panel
Surface elev ─── #20242E   highest-level sheets, dialogs
Divider ──────── #262A33

Text primary ─── #F5F0E6   warm off-white
Text secondary ─ #9098A6   muted slate
Text disabled ── #4A5060

Primary accent ─ #F2A93B   warm amber — selection / active mode
On primary ──── #1A1206

Root tone ───── #D34D52   crimson, for chord/scale root dots
Chord tone ──── #3FB8AF   teal
Scale tone ──── #9B7BF7   lavender
Pick mode ──── #F2A93B   amber outline on selected fret cells

Fretboard wood ─ #3D2817   warm dark walnut
Wood grain ──── #2C1C10   subtle horizontal striping over wood
Nut ─────────── #0A0A0B
Fret wire ──── #6F6F75   nickel
Inlay ──────── #E8E4D9   pearl

String – wound  see §3.2 — bronze base + winding stripes
String – plain  see §3.2 — bright steel gradient
```

### 2.2 Typography

System fonts only in v1.

```
Display    32 / 28 / 22 sp   Bold     (titles, big numbers)
Title      18 sp             SemiBold (section headers)
Body       14 sp             Regular  (most UI)
Caption    12 sp             Regular  (hints, fret numbers, position labels)
Mono       13 sp             Medium   (note/interval rows on chord cards)
```

### 2.3 Spacing scale

`4 · 8 · 12 · 16 · 24 · 32 · 48` dp. Screen edge padding = 16dp. Inter-panel gap = 12dp.

### 2.4 Radii

```
Pill / chip ── 999 dp
Card ──────── 16 dp
Bottom sheet  24 dp top-corners-only
Dot ────────  circle
```

### 2.5 Motion

- **Tap on a fret** → dot scales 1.0 → 1.25 → 1.0 in 220 ms (spring), brief amber halo fades 400 ms.
- **Tuning change** → strings re-render and dots crossfade (180 ms, staggered per string × 25 ms — sweeps low-to-high across the neck).
- **Mode change** (bottom bar) → mode panel slides up from below (200 ms).
- **Position scroll** → fretboard dots crossfade (200 ms); new dots scale-in 0.6 → 1.0.
- **Strum button** → connected notes light sequentially low-to-high with a 35 ms stagger as they sound.

---

## 3. Component library

### 3.1 `Fretboard` — the centerpiece

Always at the top of the screen. Horizontal orientation.

- **Frets 0–12 visible by default** at full screen width. Frets 13–24 reached via horizontal scroll right. Open string column ("fret 0") permanently pinned on the left, doesn't scroll with the rest.
- **Strings ordered low-to-high from bottom to top**:
  - Row 0 (bottom)  = string 6 = lowest pitch
  - Row 5 (top)     = string 1 = highest pitch
- **Wood background**: dark walnut (`#3D2817`) with low-alpha horizontal grain stripes overlaid (4-5 stripes drawn at slightly varying y, 6% alpha).
- **Nut** is a thick black bar at the left edge of the scrollable area (fret 0 boundary).
- **Fret wires**: nickel-colored vertical lines, drawn after the wood.
- **Inlays**: pearl ovals at frets 3, 5, 7, 9, 15, 17, 19, 21; double ovals at 12 and 24.
- **Fret numbers below**: caption-color digits centered between each pair of fret wires.

#### Strings — realistic rendering

The bottom 3 strings (E2, A2, D3 in standard tuning — indices 0, 1, 2) are **wound bass strings**. The top 3 (G3, B3, E4 — indices 3, 4, 5) are **plain steel**.

```
Wound string visualization (bottom 3):
   ─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─/─
   ↑ tilted 1-pixel hatches every ~3 dp, alternating slightly lighter/darker
   ↑ underlying string is a wider stroke (~3.5 px on bottom, tapering to 3.0)

Plain string visualization (top 3):
   ──────────────────────────────────────────────────────────────────
   ↑ smooth gradient line (1.2-1.8 px), bright steel highlight
   ↑ slight specular gradient running its full length
```

String thickness gradient (bottom to top):
```
string 6 (low E)  ─── 3.5 px, wound, bronze base
string 5 (A)      ─── 3.2 px, wound, bronze base
string 4 (D)      ─── 2.8 px, wound, bronze base
string 3 (G)      ─── 2.0 px, plain, bright steel
string 2 (B)      ─── 1.6 px, plain, bright steel
string 1 (high E) ─── 1.4 px, plain, bright steel
```

This matches a standard light gauge (.010-.046) acoustic/electric set. We don't model "string color affected by tuning" — strings keep their thickness regardless of what they're tuned to.

#### Dots

- **Root** = crimson filled circle with a 2-px inner pearl ring (extra highlight).
- **Chord tone** (non-root) = teal filled circle.
- **Scale tone** (non-root) = lavender filled circle.
- **Pick-mode selected** = amber outlined ring around the cell, no fill (so the underlying note label remains readable).
- **Tap pulse** = transient amber halo, see §2.5.
- Label inside dot (when LabelMode is Notes or Intervals): bold display font, on-primary color (cream).

#### Tap behavior

- Tap a fret cell → that position is **selected & sounded**, regardless of mode.
- In pick mode, tap → toggles inclusion in the strum set.

### 3.2 `ModeBar` — bottom strip, persistent

```
┌────────────────────────────────────────────────────────────┐
│   🎚         🎵         🎼          ✋                     │
│  Tuning     Chord      Scale       Pick                    │
└────────────────────────────────────────────────────────────┘
```

64 dp tall. One mode is **always active**. Tapping an inactive item activates it. Tapping the active item *expands* or *collapses* its inline panel above the mode bar (see §3.3–§3.6).

- Active mode: amber icon, amber-tinted pill behind it, text in primary color.
- Inactive: secondary-text icons.

Persistent across all states. There is no top-bar navigation.

### 3.3 `TuningPanel` — slides up from ModeBar when Tuning is active

```
┌────────────────────────────────────────────────────────────┐
│  TUNING                                  [ Customize ▾ ]   │
│                                                            │
│  [ Standard ]  [ Drop D ]  [ DADGAD ]  [ Open G ]          │
│  [ Open D ]    [ Half-step ]  [ Whole-step ]               │
│                                                            │
│  ── My tunings ──                                          │
│  [ My_DropC ]  [ Gypsy_pluck ]  ...                        │
│                                                            │
│  E2  A2  D3  G3  B3  E4                                    │
└────────────────────────────────────────────────────────────┘
```

- Pill chips for presets + saved custom tunings (with × to delete on long-press or trailing edge).
- "Customize" expands an inline editor: per-string -1/+1/-oct/+oct buttons + a "Save as…" field.
- Open-strings line at the bottom is a constant read-out.

### 3.4 `ChordPanel`

```
┌────────────────────────────────────────────────────────────┐
│  CHORD                          [ All notes / Positions ]  │
│                                                            │
│  Root                                                      │
│  [ C ] [ C# ] [ D ] [ D# ] [ E ] [ F ] [ F# ] [ G ] ...    │
│                                                            │
│  Quality                                                   │
│  [ major ] [ m ] [ 7 ] [ maj7 ] [ m7 ] [ dim ] ...         │
│                                                            │
│  ── when "Positions" selected ──                           │
│  ◀  Cmaj7 — position 2 of 6   ▶                            │
│  fret 3 · span 1 · root in bass                            │
│                                                            │
│  ── when "All notes" selected ──                           │
│  Cmaj7  ·  C  E  G  B   (12 dots across the neck)          │
└────────────────────────────────────────────────────────────┘
```

- Root + quality pickers identical to current M5 work.
- Display toggle: **All notes** (every chord-tone instance across all visible frets, color-coded by interval) vs **Positions** (one chord shape at a time, scrolled with ◀/▶).
- Position scroller shows current position label + metadata. Uses existing `ChordShapeGenerator` output.

### 3.5 `ScalePanel`

```
┌────────────────────────────────────────────────────────────┐
│  SCALE                          [ All notes / Positions ]  │
│                                                            │
│  Root                                                      │
│  [ C ] [ C# ] [ D ] [ D# ] [ E ] [ F ] ...                 │
│                                                            │
│  Scale                                                     │
│  [ major ] [ natural minor ] [ major pentatonic ] ...      │
│                                                            │
│  ── when "Positions" selected ──                           │
│  ◀  A minor — position 3 of 7   ▶                          │
│  anchor: D on low E (fret 10)  · spans frets 10–14         │
│                                                            │
│  ── when "All notes" selected ──                           │
│  A minor  ·  A  B  C  D  E  F  G                           │
│  formula  ·  1  2  b3  4  5  b6  b7                        │
└────────────────────────────────────────────────────────────┘
```

- "Positions" uses the new `ScalePosition` algorithm (§7).
- Each position anchored on a different scale degree on the lowest string.
- For a 7-note diatonic scale: 7 positions. For pentatonic: 5. For blues: 6. Scale-degree count = position count.

### 3.6 `PickPanel` — free pick & strum

```
┌────────────────────────────────────────────────────────────┐
│  PICK                                                      │
│                                                            │
│  Tap any fret on the neck to add/remove a note.            │
│                                                            │
│  Selected: 4 notes ·  C E G B                              │
│                                                            │
│  [ Clear ]    [ Strum ▶ ]    [ Strum arpeggio ◐ ]          │
└────────────────────────────────────────────────────────────┘
```

- Fretboard above respects pick mode: tap toggles selection. Selected cells show amber ring.
- Selected count + sorted note-class list updates live.
- **Strum** plays all selected notes simultaneously (or with a 35 ms low-to-high stagger).
- **Strum arpeggio** plays them one at a time, low-to-high, 120 ms between notes.
- **Clear** unselects all.

---

## 4. Screen layout (single screen)

```
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ↻ Standard — E A D G B E             ⚙                  │ │  ← thin status row (32dp)
│ ├─────────────────────────────────────────────────────────┤ │
│ │                                                         │ │
│ │           ▣ FRETBOARD — frets 0 to 12 (scroll →)        │ │  ← takes 40-50% of viewport
│ │                                                         │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │                                                         │ │
│ │           [active mode's panel]                         │ │  ← expands/contracts
│ │                                                         │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🎚 Tuning   🎵 Chord    🎼 Scale    ✋ Pick              │ │  ← always visible
│ └─────────────────────────────────────────────────────────┘ │
```

- **Status row (top)**: current tuning summary + settings gear. Tapping the tuning text is a shortcut to the Tuning panel.
- **Fretboard**: ~40% of viewport height by default. Horizontally scrollable for >12 frets. Always visible, always responsive to taps.
- **Mode panel**: shows whatever the bottom bar's active mode dictates. Collapsible; when collapsed shows only a 1-line summary of current state.
- **ModeBar**: 4 modes, always visible. One is always active.

No tabs. No navigation drawer. No screen transitions.

---

## 5. Implementation phases

Each phase is independently shippable; we'll screenshot and react after each.

### Phase A — restructure
A1. Remove tab navigation (`Screen` enum, `NavigationBar`, four screens collapse into one).
A2. Add `Mode` enum + `ModeBar` component.
A3. New top status row.
A4. Compose the single-screen layout per §4.
A5. Wire the existing chord/scale/tuning state into mode panels.

### Phase B — theory engine additions
B1. `ScalePosition` data class (`anchorFret`, `endFret`, `positions: List<FretPosition>`, `rootCount`).
B2. `Scale.positions(root, scale, tuning, numFrets): List<ScalePosition>` per §7.
B3. Unit tests covering the 7 positions of C major, the 5 positions of A minor pentatonic, the constraint checks (≤5 fret span, ≥2 roots).
B4. Chord positions = wrap existing `ChordShapeGenerator` output in a position-friendly view model.

### Phase C — visual fretboard upgrade
C1. Theme + tokens (§2 colors and typography).
C2. Wound vs plain strings — Canvas draw routines per §3.2 strings.
C3. Color-coded dots (root crimson / chord teal / scale lavender).
C4. Wood grain background layer.
C5. Fret numbers below the neck.
C6. Tap-pulse animation.

### Phase D — modes
D1. ChordPanel with All / Positions toggle + position scroller.
D2. ScalePanel with All / Positions toggle + position scroller.
D3. TuningPanel as a panel (was a screen).
D4. PickPanel + selection state.

### Phase E — strum
E1. `AudioEngine.strum(positions: List<FretPosition>, tuning, mode: Simultaneous|Arpeggio)`.
E2. Wire into PickPanel buttons.

### Phase F — polish
F1. Motion (tap pulse, tuning sweep, mode-panel slide).
F2. Custom tuning save flow inside TuningPanel.
F3. Horizontal scroll past fret 12.
F4. Inlay glow + nut polish.

---

## 6. Decisions left implicit

(I'll commit unless you object.)

- **Single dark theme only** — no light mode in v1.
- **Standard 6-string only at first** — the theory engine is already n-string ready, but UI assumptions in this doc (e.g., "bottom 3 wound") hardcode 6 strings. Cavaquinho support (§15) is a v2 concern.
- **Audio strum** uses the existing continuous-mixer engine — no new audio architecture, just a new entry point that submits multiple voices.
- **Settings gear** (top right) opens a sheet with: left-handed toggle, label-mode (notes/intervals/empty), sound on/off, about.

---

## 7. `ScalePosition` algorithm

Pure-Kotlin function in the `theory` module.

```kotlin
data class ScalePosition(
    val anchorFret: Int,        // fret on the lowest string where the position is anchored
    val anchorPitchClass: PitchClass,   // which scale degree this position is built around
    val firstFret: Int,         // lowest fret in the position
    val lastFret: Int,          // highest fret in the position (≤ firstFret + 4)
    val positions: List<FretPosition>,   // every (string, fret) in this position that belongs to the scale
    val rootCount: Int,
)

fun Scale.positionsFor(
    root: PitchClass,
    tuning: Tuning,
    numFrets: Int,
    maxFretSpan: Int = 4,        // 4-fret-gap = 5-fret span (frets N to N+4 inclusive)
    minRootInstances: Int = 2,
): List<ScalePosition>
```

**Algorithm** for each scale degree `i` (anchors the position):

1. Compute `anchorPitchClass = root + scale.intervals[i]`.
2. Find the lowest fret on the bottom string (index 0) where `anchorPitchClass` occurs in `0..numFrets`. Call it `anchor`.
3. Define window `[anchor .. anchor + maxFretSpan]` (5 frets total).
4. Collect every `(s, f)` in the window where `Fretboard.noteAt(tuning, FretPosition(s, f)).pitchClass` is in the scale's PC set.
5. Count root instances among collected positions.
6. If `rootCount >= minRootInstances`, emit `ScalePosition`. Otherwise skip (rare for diatonic scales; can happen for degenerate cases).

The number of emitted positions equals the number of scale degrees that satisfy the root-count constraint. For:
- 7-note diatonic ⇒ 7 positions
- 5-note pentatonic ⇒ 5 positions
- 6-note blues ⇒ 6 positions

**Edge cases**:
- Anchor pitch class not present on string 0 (e.g., capo above the highest occurrence) → skip that degree.
- Window extends past `numFrets` → truncate.
- < `minRootInstances` roots in window → skip.

Tests will assert (for C major, standard tuning):
- exactly 7 positions returned
- each has span ≤ 5 frets
- each has ≥ 2 roots
- position 1 anchor = C on low E string fret 8

---

## 8. Open redirect points

If you want to redirect again before we cut code, here are the easy pivots:

- **Mode bar position**: bottom (current spec) vs floating-pill vs left rail.
- **Default mode**: "Chord" (most common starting point), "Scale" (theory study), or "None" (passive fretboard with no overlay).
- **Pick mode interaction**: tap to toggle (current spec) vs drag a multi-select rectangle vs long-press to enter selection mode.
- **Strum visualization**: live highlight of each note as it sounds vs no visual change.
- **Inlay style**: pearl ovals (current spec) vs the more modern flat colored dots vs no inlays at all.

Anything else, just say.

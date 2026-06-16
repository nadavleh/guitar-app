# Chorect — GUI Design

This document is the **single source of truth** for the app's look-and-feel and primary interactions. The Compose code in `app/` should match these specs. Update this doc before changing visual code so we don't drift.

Status: **v2 — "Studio"** (app version **1.3.0**) — the architecture is a persistent left **navigation rail** plus a content area that is, by default, the full-height fretboard. Tools open as draggable bottom sheets (Fretboard, Options) or full-screen routes (Loop, Tuner, Ear, Drums). This superseded the earlier v1 "single screen + bottom mode bar" concept; this doc now reflects the as-built v2.

---

## 1. Vision

> An instrument, not a textbook.

**The fretboard is the home screen.** With no tool open, the content area is a full-height neck the user can play, zoom, and pan. The navigation rail on the left is always present so the user can reach any tool in one tap and come straight back to the instrument. Tools layer over or beside the neck rather than replacing the app's identity.

**Mood**: warm, tactile, focused. Dark by default. Strings look like real strings; wood looks like real wood; lit-up notes feel like the instrument is responding to you. Bright accents are the chord/scale tones lighting up under your fingers.

The brand wordmark is **Chorect**: the trailing `c`+`t` are kerned so tightly (negative letter-spacing on the `c`) that "ct" reads as a single `d`-like glyph — i.e. "Chore**ct**" winks at "chord". It is drawn in the amber primary color in the top status bar.

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
Pill / chip ── 999 dp  (Material FilterChip / SegmentedButton)
Card ──────── 12–16 dp
Bottom sheet  Material 3 default top-corner radius
Rail button ─ 12 dp
Dot ────────  circle
```

### 2.5 Motion

Motion is currently deliberately minimal — most state changes redraw instantly. What exists today:

- **Bottom sheets** (Fretboard, Options) use the standard Material 3 `ModalBottomSheet` slide-up / drag-to-dismiss animation.
- **Tap on a fret** → the tapped cell gets a static amber selection ring (no spring/halo animation yet).
- **Audio strum spread** → notes sound low-to-high with a per-note delay set by the *Strum spread* slider (0–150 ms; 0 = struck at once). This is an audio-timing effect, not a visual one.

Aspirational (not yet implemented): tap-pulse spring, staggered tuning-change crossfade, and live note-lighting during a strum. Keep these out of the doc's "as-built" claims until they ship.

---

## 3. Component library

### 3.1 `FretboardView` — the centerpiece

Fills the content area to the right of the rail (on the bare fretboard screen, the whole area below the status bar). Horizontal orientation. Implemented as a single `Canvas` draw, `FretboardView.kt`.

- **`DISPLAY_FRETS` = 14 frets** drawn (frets 1–14, plus the open-string column). There is **no horizontal scroll** and no 24-fret extension — the whole 14-fret neck is always present; the user reaches detail by zooming/panning instead.
- **Fixed aspect ratio, letterboxed.** The neck is *always* drawn at a fixed long-horizontal / short-vertical aspect ratio (`neckAspect`, computed from `numFrets × 72 + 100` width units over `stringCount × 42 + 18` height units). It is centered and letterboxed inside whatever box the caller gives it, so the viewport's shape never stretches it. In a tall portrait viewport the short neck simply has empty space above and below it; in landscape it fills more of the width. This holds in **both orientations** (the app runs in `fullSensor`, not locked to landscape).
- **Pinch-to-zoom + drag-pan**, within that fixed frame, via `detectTransformGestures` on a render-only `graphicsLayer` (layout size never changes):
  - **scale 1** = the whole neck exactly fits the viewport.
  - **minScale 0.5** = neck shrinks to half the viewport (zoom out).
  - **maxScale ≈ stringCount / 2** (≥ 1.5) = zoom in until ~2 strings fill the height.
  - Pinch is **focal-point** (content under the centroid stays put); drag pans, clamped so the neck can't be pulled off-screen.
  - Because the transform is render-only, tap hit-testing still uses the un-transformed Canvas coordinates — Compose maps pointer coords back through the layer.
- **Strings ordered low-to-high from bottom to top**:
  - Row 0 (bottom)  = string 6 = lowest pitch (string index 0)
  - Row 5 (top)     = string 1 = highest pitch
- **Wood background**: dark walnut (`#3D2817`) with low-alpha horizontal grain stripes (9 streaks at varying y, 5–10% alpha).
- **Open-string column** on the left (~8% of width) holds the open-note letters and fret-0 marks; a faint separator line, then the **nut** (thin black bar). In **left-handed** mode the whole neck is mirrored horizontally (nut on the right, frets ascend right-to-left).
- **Fret wires**: nickel-colored vertical lines, drawn after the wood.
- **Inlays**: pearl dots at frets 3, 5, 7, 9 (single) and 12 (double); the 15/17/19/21/24 inlays only appear if `numFrets` reaches them (it doesn't at 14).
- **Open-string letters**: drawn at the left edge of the open column, amber, SemiBold. Convention: the **highest** string is lowercase, the rest uppercase — standard tuning reads `E A D G B e`.
- **Fret numbers below**: a dark strip under the wood with caption-color digits centered under each fret, plus a `0` under the open column.

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

- **Root** = crimson filled circle with a pearl inner ring (extra highlight).
- **Chord tone** (non-root) = teal filled circle.
- **Scale tone** (non-root) = lavender filled circle.
- **Pick / Strum selected** = amber outlined ring around the cell, no fill (so the underlying note label remains readable).
- **Muted string** (Strum mode) = a crimson `✕` drawn at the nut in the open column, chord-diagram style (see §3.4 Strum).
- **Selected/inspected cell** = a static amber ring on the most recently tapped position (all modes).
- Label inside a filled dot follows the global **LabelMode** (Notes / Intervals / Empty): bold, cream text. Pick/Strum rings show the note name only when LabelMode = Notes, otherwise no label.

#### Tap behavior

- **Default (`tapOnTouchDown` off):** a note fires on **tap-release** only, so a drag is claimed by the pan/zoom gesture and **dragging the neck never sounds a note**. Toggleable in Options.
- **`tapOnTouchDown` on:** a note fires the instant you touch down (more immediate, but a swipe-start also sounds).
- Tap a fret cell → that position is **selected & sounded** (and inspected in the info line below the neck) in Chord/Scale modes.
- In **Strum (Pick)** mode, tap → toggles that position's inclusion in the strum set.

### 3.2 `NavRail` — persistent left navigation (`AppShell.kt`)

```
┌────┐
│ 🎸 │  Fretboard   ← Chord / Scale / Strum sheet over the neck
│ ⟲ │  Loop        ← full-screen route
│ 👂 │  Ear         ← full-screen route
│ 🥁 │  Drums       ← full-screen route
│ 🎛 │  Tuner       ← full-screen route
│ ⚙ │  Options     ← bottom sheet
└────┘
```

A slim **58 dp** column down the left edge, **always visible in both portrait and landscape**, separated from the content by a 1-dp divider. Each item is a glyph + 9-sp single-line label in a 50-dp rounded button.

- **Active item**: amber glyph/label on an amber-tinted (`primaryContainer`) pill. The Fretboard item reads as active whenever a chord/scale/strum overlay is showing on the bare neck; otherwise the active item is whatever sheet/route is open.
- Tapping an item calls `openSheet(...)`. **Fretboard** and **Options** open as draggable bottom sheets over the neck; **Loop / Ear / Drums / Tuner** take over the content area to the right of the rail as full-screen routes (each with its own Back button).
- The rail itself scrolls vertically if the device is too short to fit all six items.

There is **no bottom mode bar and no top tab/drawer** — the rail is the only chrome.

### 3.3 `StatusBar` + `ContextBar` — around the neck

The bare fretboard screen stacks: **StatusBar** (top) → neck → **selected-position info line** → **ContextBar** (bottom).

**StatusBar** (`MainActivity.kt`): the **Chorect** wordmark (amber) + a one-line tuning summary (`name* · E A D G B e`, `*` = unsaved edits, ellipsized). Trailing controls, right-aligned: an `↑ <LastTool>` shortcut to re-open the last sheet, a `⏹ Stop` shown only while a loop is playing, and the compact **AudioQuick** button (strum-spread + ring-sustain quick popup).

**Selected-position info line**: when a cell is tapped, shows `string N · fret F · note` (plus the interval relative to the current chord root when a chord is set). With nothing selected it instead shows the current tuning notes as a hint.

**ContextBar** (the action bar under the neck) is mode-dependent:
- **Chord / Scale, Positions view** → a `◀  label  ▶` **PositionScroller**. Chord label = `Cmaj7 · frets 3–5 · 2 / 6`; scale label = `A minor · anchor B · frets 10–14 · 3 / 7`.
- **Chord / Scale, All-notes view** → no bar.
- **Strum (Pick)** → the **strum action bar**: per-string mute toggles (`StringMuteRow`, red `✕ <note>` chips, high→low) above a row of `Picked: N · muted: M` + **Strum** / **Arp** / **Clear** buttons. Strum/Arp are disabled if every picked note's string is muted.

### 3.4 `FretboardSheet` — unified Chord / Scale / Strum (`Screens.kt`)

The three former separate Chord / Scale / Pick entries are now **one Fretboard tool** opened from the rail, with a full-width **segmented selector** at the top — **Chord · Scale · Strum** — that drives `DisplayMode`. Opens as a draggable bottom sheet (`ModalBottomSheet`, scrollable, capped ~600 dp). Header is the small `FRETBOARD` caption + AudioQuick button; footer is a `Done` button.

**Chord** body: a `Root` `FilterChip` row (FlowRow, C…B) + a `Quality` FlowRow (`major, m, 7, maj7, m7, dim, aug, sus4, sus2, 6, m6, m7b5, dim7, 9, add9, 13`), then a full-width **Display** segmented control (`All notes` / `Positions`) and a **Labels** segmented control (`Notes` / `Intervals` / `Empty`). A live read-out shows the chord's notes and interval formula, or `(chord not recognized)`.

**Scale** body: `Root` chips + a `Scale` FlowRow (all `ScaleLibrary` names), the same **Display** and **Labels** segmented controls, and a monospace `notes` / `formula` read-out.

**Strum** body: instructions + `Picked: N · muted: M`, the **Mute strings** `StringMuteRow`, and `Strum` / `Arpeggio` / `Clear` buttons. (This is the same selection/strum state surfaced by the ContextBar's strum action bar, so the user can strum without re-opening the sheet.)

- **All notes** = every chord/scale-tone instance across the 14 frets, color-coded by interval. **Positions** = one shape at a time, stepped with the ContextBar `◀/▶`.
- Chord positions come from `ChordShapeGenerator` (CAGED in Standard voicing, drop-2 inversions in Shell voicing). Scale positions use the `ScalePosition` algorithm (§7).
- Portrait was tightened so nothing wraps oddly: full-width segmented controls, FlowRow chip groups, single-line labels.

### 3.5 `OptionsSheet` — settings (`Screens.kt`)

Draggable bottom sheet opened from the rail. Sections, top to bottom:

- **Instrument** — segmented (`Guitar` / `Cavaquinho`); changes preset tunings and chord fret-span allowance.
- **Tuning** — preset `FilterChip`s for the current instrument, a `My tunings` group (each with a trailing `✕` to delete), the live open-strings read-out (`low → high`), an `(unsaved edits)` warning, and a `Customize… / Close editor` toggle. The editor is a card with per-string `− / + / −oct / +oct` buttons and a `Save as…` field + `Save`.
- **Display** — `Labels on dots` segmented (`Notes / Intervals / Empty`), `Left-handed` switch, `Play note on touch-down` switch (with explanation), `Jazz / shell voicings` switch.
- **Tuner & audio** — `A4 reference` slider (435–445 Hz), `Ring sustain` slider (0.3–4.0 s), `Strum spread` slider (0–150 ms; 0 = struck at once).

### 3.6 Full-screen tool routes

Loop, Tuner, Ear, and Drums replace the content area (right of the rail) rather than opening as sheets; each has its own Back/Watch control. See §4.2.

---

## 4. Screen layout

### 4.1 Home (bare fretboard)

```
┌────┬──────────────────────────────────────────────────────┐
│ 🎸 │ Chorect   Standard · E A D G B e        ↑Fretboard ♪ │ ← StatusBar
│ ⟲ ├──────────────────────────────────────────────────────┤
│ 👂 │                                                      │
│ 🥁 │        ▣ FRETBOARD — 14 frets, pinch/pan,            │ ← fills all
│ 🎛 │           fixed aspect, centered + letterboxed       │   remaining
│ ⚙ │                                                      │   height
│    ├──────────────────────────────────────────────────────┤
│    │ string 3 · fret 5 · A          (selected-pos info)   │
│    ├──────────────────────────────────────────────────────┤
│    │ ◀  Cmaj7 · frets 3–5 · 2 / 6  ▶   (ContextBar)        │
└────┴──────────────────────────────────────────────────────┘
```

- **NavRail (left)**: always visible, both orientations (§3.2).
- **Content column (right)**: StatusBar → neck (`weight(1f)`, all remaining height) → selected-position info line → ContextBar.
- The neck never scrolls; the user zooms/pans within it (§3.1).
- The whole thing sits inside `safeDrawingPadding()` so it clears the status bar and gesture-nav insets.
- **Bottom sheets** (Fretboard, Options) slide up *over* this layout; the neck stays full-height behind them.

This is the same layout in portrait and landscape — only the neck's letterbox proportions change.

### 4.2 Full-screen tool routes

When Loop / Tuner / Ear / Drums is active, the content column to the right of the rail is replaced entirely by that tool (the rail stays). Each manages its own header with a Back button (Loop's reads `Watch on neck ▶` while a loop is playing, so the user can return to the live neck without stopping playback).

- **Loop** — chord-progression looper. Transport (Play/Stop) + AudioQuick + Back; full-width Tempo slider; `Slots/bar` (1/2/4) and `Bars` (±, 1–16) controls; a collapsible **Build by degree** panel (Key chips + Random, Major/Minor mode, diatonic Level, quality override, 7 Roman-numeral degree buttons that write into the cursor/selected slot); then a bars grid of `BarCard`s (slots show chord symbol + strum glyph, the playing bar/slot is tinted) with a `SlotEditor` panel to the right when a slot is selected (chord field, voicing chips, strum-pattern segmented control). While a loop plays, the **main neck** lights up the sounding chord and the StatusBar shows a Stop control.
- **Tuner** — see §9.
- **Ear training** — see §10.
- **Drums** — see §11.

---

## 5. Wound vs plain strings (Canvas draw detail)

The neck draws realistic strings; the wound/plain split depends on string count, not on tuning.

- **Guitar (6 strings):** the bottom half (low E/A/D, indices 0–2) are **wound** — a bronze base line with a dashed darker-bronze hatch overlay and a thin bright highlight; thickness `4.0 − 0.5·s` px. The top half (G/B/e, indices 3–5) are **plain** — a single bright-steel line with a specular highlight; thickness `2.1 − 0.3·plainIdx` px.
- **Cavaquinho (4 strings):** all strings render **plain** (no wound bronze), reflecting its short scale and similar-gauge nylon/steel set.

Dot and label sizes are scaled by the *smaller* of string-spacing and fret-spacing, so they stay round and sensible at any letterboxed aspect (a tall portrait neck has huge string spacing but the dots don't balloon).

---

## 6. Decisions in force

- **Single dark theme only** — `GuitarTheme` ignores the system light/dark setting and is always dark.
- **6-string guitar + 4-string cavaquinho** — selectable in Options. The "bottom half wound" rule is computed from string count, so both render correctly.
- **Audio** uses the continuous-mixer `AudioTrackEngine`; strum submits multiple voices spread by the *Strum spread* slider; the Tuner uses the mic (`RECORD_AUDIO`, requested on first Tuner open).
- **Settings live in the Options bottom sheet** (§3.5), reached from the rail — there is no separate gear icon.
- **Persistence** (`TuningRepository`, DataStore): selected/custom tunings, left-handed, voicing style, label mode, A4, ring sustain, strum spread, tap-on-touch-down, instrument, and ear-training challenge high scores.

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

## 8. Note on the rail-vs-modebar pivot

The §3.2 NavRail replaced the v1 bottom `ModeBar`. The old `ActionBarItem` helper still lives in the code in case rail items ever need to be surfaced elsewhere, but the rail is the single navigation surface today. "Pick" mode is now labeled **Strum**; tap-to-toggle selection is unchanged.

---

## 9. Tuner screen (`TunerScreen.kt`)

A full-screen route. Layout (column inside the content area):

- **Top bar**: `TUNER` title, `A4 = NNN Hz` read-out, AudioQuick, `Back`.
- **On-the-fly tuning chips**: a FlowRow of the current instrument's preset tunings + the user's saved custom tunings, so the tuning can be changed without opening Options. The active tuning is selected.
- **Center dial**: a quarter-ring (90° sweep, apex at north) spanning ±50 cents, with major/minor/micro cent ticks and a pivoting needle. The big detected-note label sits in the middle and turns **green + "IN TUNE"** within ±10¢. The note label is **tappable** → plays the equal-tempered reference tone and locks the dial to "spot on" for the ring-sustain duration. In **portrait** the dial is constrained to a compact 1.5:1 box and centered (so it isn't a tiny ring floating in a tall column); in landscape it fills the space. If the mic permission is missing, a `MicPermissionPanel` with a Retry button shows instead of the dial.
- **Reference row** (bottom): one `OutlinedButton` per open string of the current tuning (low→high), each showing `S<n>` + note+octave; tapping plays that string's reference tone and locks the dial.

---

## 10. Ear training screen (`EarTrainingScreen.kt`)

A full-screen route. Header: `EAR TRAINING` + AudioQuick + `Back`. Below it:

1. **Sub-mode selector** — a **wrapping chip row** (`FlowRow` of `FilterChip`s, not a segmented button), because there are now **five** sub-modes that don't fit a single segmented row in portrait: `Progressions` · `Note→Chord` · `Flavor` · `Inversions` · `Aug / Dim`.
2. **Practice / Challenge** toggle — a full-width segmented control; every sub-mode has both.

The body switches on (sub-mode × mode). The **Progressions** sub-mode also shows an **"Advanced (non-diatonic) progressions"** `Switch` above the body (§10.1).

### 10.0 Progressions (diatonic)

Highlights of the **Progression Challenge** (the most elaborate view):

- A challenge is **15 progressions**. Config screen explains the rules + a `Start challenge ▶` button (with the shared `ProgressionSettings`).
- In-flight: `Question k / 15`, running `Score: N bars`, `Quit`; a transport row (Play/Stop, `Hear <cadence>`, `Re-roll`); a BPM slider; an optional low-emphasis `Key & Mode (hint)` reveal chip.
- **"Hear the degrees"** reference palette — a FlowRow of `▶ <label>` buttons that audition each diatonic degree *in the hidden key*. These plus the per-bar `▶ Play` are the **only** things that make sound; selecting an answer never plays a chord (so you compare candidates deliberately).
- **Per-bar answer cards** (4 bars): each card has a `▶ Play`, then the answer chips. In **fixed-7ths (combined) mode** a single combined diatonic-7th choice (e.g. `V7`) encodes both degree and extension; otherwise you pick the Roman numeral plus, when the level has one, a separate `extension` chip row. Each bar **auto-scores** on selection — the card turns green/red and reveals `✔/✘ answer`.
- **"Next question → / See score →"** is **always enabled**; any bar left unanswered is **credited as correct** (caption: "Unanswered bars count as correct.").
- Optional `Show chord on fretboard` switch renders a 220-dp `FretboardView` of the current/last shape.
- **Score screen** (`ChallengeDoneCard`): big `score / total` bars-correct, duration, a **wrapping per-question dot strip** (15 numbered squares, green/red/outline), and a **High-scores table** — best first, ties broken by faster completion time (`CHALLENGE_SCORE_ORDER`), each row showing rank, `score/total`, time, and date; the current run is **bold + "← you"**. The just-finished run is merged in locally so it shows even before the async DataStore write lands. `Restart` / `Exit`.

Practice (Progression) mode mirrors the transport and settings but reveals answers via `RevealCard`s and adds a `→ Looper` button that pushes the current progression into the Loop tool. Note→Chord and Flavor sub-modes follow the same Practice/Challenge pattern with their own question types.

### 10.1 Progressions — Advanced (non-diatonic)

When the **"Advanced (non-diatonic) progressions"** `Switch` is on (only shown for the Progressions sub-mode), the diatonic generator is swapped for a curated library of borrowed chords, secondary dominants and jazz turnarounds. Both Practice and Challenge share one body (`AdvancedProgressionBody`):

- A **key picker** (`KeyDropdown` — Random or a fixed key) and **Play ▶ / Stop ⏹** + **Next →** transport.
- A **per-chord ▶ row** — one `OutlinedButton` per chord (labelled `▶ 1`, `▶ 2`, … while hidden, switching to `▶ <Roman>` once revealed).
- A **reveal card** that, when tapped open, shows the progression **name**, the **Roman-numeral line**, the **chord symbols**, and the **key** (e.g. "in C major").
- An **always-visible "About this progression"** teaching-note card (secondary-container tint) explaining the harmonic device — shown even while quizzing.

The **Advanced Challenge** runs a fixed number of progressions and is **self-marked**: you reveal, then tap **✔ I got it / ✘ Missed** (both enabled only after revealing), then **Next → / See score →**. It ends on the shared `SimpleDoneCard`.

### 10.2 Inversions

Practice (`InversionsView`): a **"Chord types"** palette (`FilterChip`s over `invPalette` — maj, m, sus2/4, aug, dim, 7, maj7, m7, m7♭5, dim7, 6, m6, 9, …) to enable which qualities can appear; **New chord ▶ / Replay**; a **"Which inversion?"** guess-chip row (`Root position / 1st inversion / 2nd … / 3rd …`, count depends on the chord) where **tapping a chip auditions that inversion** so you can compare; then a **reveal card** (inversion name + root+quality) with a `✔/✘` line once revealed.

Challenge (`InversionsChallengeView`): same palette on the config screen, then scored rounds — `Round k / N`, running `Score`, `Quit`; **Replay ▶**, the same guess chips (disabled after answering), a **Submit** button, the correct/answer line, and **Next → / See score →**. Ends on `SimpleDoneCard`.

### 10.3 Aug / Dim

Practice (`AugDimView`): a **"Chord types"** palette over `augDimPalette` — `Augmented (+)`, `Diminished (°)`, `dim7 (°7)`, `m7♭5 (half-dim ø)`, `7♯5 (aug 7th)`, `maj7♯5` — to enable qualities (default: Augmented + Diminished); **New chord ▶ / Replay**; a **"Which chord?"** guess-chip row (only the *enabled* qualities) where **tapping a chip auditions it**; then a **reveal card** (root + quality + family) with a `✔/✘` line.

Challenge (`AugDimChallengeView`): same palette on the config screen (Start is disabled until at least one quality is enabled), then scored rounds with **Replay ▶**, the guess chips, **Submit**, the answer line, and **Next → / See score →**. Ends on `SimpleDoneCard`.

---

## 11. Drums screen (`SambaLooperScreen.kt`)

A full-screen route — a step-sequencer drum machine. The **whole page is vertically scrollable** (`verticalScroll`) so it fits short-height (landscape) layouts.

- **Header (compact, single row for portrait)**: `DRUMS` title, Play/Stop, AudioQuick, `Back`.
- **BPM** row: label + slider (60–200).
- **Grid**: one row per `PercussionInstrument`, each row a 16-cell step grid (2 bars of 2/4 in sixteenths), with thicker gaps after every 4th cell and a wider bar gap after the 8th. The grid uses **fixed-height rows** (`ROW_HEIGHT_DP`, not weight) so each track's name, `▾` voice popup, and **M/S** toggles always have room and never get clipped in either orientation.
  - **Gestures**: a **2-finger pinch zooms/pans** the grid (render-only `graphicsLayer` on the grid container, clamped, focal-point — mirrors `FretboardView`; `minScale 0.5`, `maxScale 3`). A **single-finger drag falls through to the page's vertical scroll** (so the page still scrolls over the grid), and **cell taps still toggle steps** — the pinch consumes its events so it doesn't trigger scroll or taps, and because the transform is on the container (never a cell) tap hit-testing maps back through the layer.
  - Tapping a cell **cycles through all of that instrument's voices** (silent → v1 → v2 → … → silent); **long-press clears** the cell. Filled cells show the voice glyph; a tinted/bordered column tracks the playhead while looping. Voice counts differ per instrument: **Surdo 3** (open ring `●`, muted bass `◐`, tap `·`), **Tamborim 3** (clack `●`, muted clack `◐`, tap `·`), **Pandeiro 5** (bass open `●`, bass muted `◐`, slap `✦`, jingle `○`, jingle hi `◌`), **Agogô 2** (low bell `▼`, high bell `▲`).
- **Per-track controls** (left of each row, `ROW_LABEL_DP` wide): the instrument name with a `▾` — **tapping it opens a voice-audition popup** (`DropdownMenu`) listing **each of that instrument's voices** (glyph + name); tapping a voice previews it (popup stays open to compare). Below the name, **M (mute)** and **S (solo)** toggle tags (outlined off, filled on — error-red for mute, amber for solo). Rows that aren't audible (muted, or not soloed when something else is) are dimmed to 40%.
- **Footer**: `Save…` / `Load…` / `Clear all`.
  - **Save…** opens a name dialog (a beat name; names containing `= ; | ,` are rejected) and stores the current grid as a user-saved beat.
  - **Load…** opens a dropdown listing **stock samba** (the built-in preset) followed by every user-saved beat; each saved entry has a trailing `✕` delete affordance. Saved beats **persist** across sessions.
  - **Clear all** empties the grid.

---

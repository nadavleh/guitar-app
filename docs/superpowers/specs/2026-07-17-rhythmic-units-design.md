# Rhythmic Units — design

A new menu entry to learn & train the basic one-beat rhythmic units. One screen,
one section ("Rhythmic units"): a grid of unit cards, each with a music-notation
thumbnail; tapping a card loops that unit's click pattern at a controllable BPM.

Reference: the standard one-beat rhythmic vocabulary (per the YouTube video Nadav
linked, qz-ywUM6hFk — the universal set of one-beat subdivisions).

## Navigation / scope

- New full-screen route mirroring `Decompose` / `CavaqProgressions`:
  `TabDest.RhythmUnits` → `Sheet.RhythmUnits`, label **"Rhythm"**, subtitle
  "Learn & train basic rhythmic units". Available for all instruments.
- The screen contains ONLY the "Rhythmic units" section (no other content).

## The 8 units (one beat each)

Grid = `subdivision` slots per beat. A note's duration in slots implies its
notation type. Note starts = click onsets.

| # | name                     | subdiv | notes (slots) | onsets (slots) | count      |
|---|--------------------------|--------|---------------|----------------|------------|
| 1 | Quarter                  | 4      | [4]           | 0              | "1"        |
| 2 | Two eighths              | 4      | [2,2]         | 0,2            | "1 &"      |
| 3 | Four sixteenths          | 4      | [1,1,1,1]     | 0,1,2,3        | "1 e & a"  |
| 4 | Eighth + two sixteenths  | 4      | [2,1,1]       | 0,2,3          | "1 & a"    |
| 5 | Two sixteenths + eighth  | 4      | [1,1,2]       | 0,1,2          | "1 e &"    |
| 6 | Sixteenth–eighth–sixteenth | 4    | [1,2,1]       | 0,1,3          | "1 e a"    |
| 7 | Dotted eighth + sixteenth | 4     | [3,1]         | 0,3            | "1 a"      |
| 8 | Eighth-note triplet      | 3      | [1,1,1]       | 0,1,2          | "1 trip let" |

Invariant (unit-tested): each unit's note-slot durations sum to `subdivision`
(one full beat).

## Data model (theory module, pure Kotlin + TS mirror)

```
enum RhythmNoteType { Quarter, DottedEighth, Eighth, Sixteenth, TripletEighth }

data class RNote(val slots: Int, val type: RhythmNoteType)
data class RhythmUnit(
    val id: String,
    val name: String,
    val count: String,      // e.g. "1 e & a"
    val subdivision: Int,   // 4 (sixteenth grid) or 3 (triplet)
    val notes: List<RNote>,
) {
    /** Slot index of each note onset (cumulative durations). */
    val onsets: List<Int>
    /** Fraction (0..1) within the beat of each onset — for scheduling + drawing. */
    fun onsetFractions(): List<Double>
}

object RhythmUnits { val ALL: List<RhythmUnit> = listOf(/* the 8 above */) }
```

`type` is derived from `(subdivision, slots)` at construction so the list is
declared compactly; it drives notation (stems/beams/flags/dots) and nothing else.

## Playback (`RhythmUnitState`, app-lifetime — like `CavaqProgState`)

- Reuses the drum machine's mixer-clock scheduler (`AudioEngine.playSamplesAt`
  with a small look-ahead), so timing is sample-accurate and doesn't drift.
- `selectedId`, `isPlaying`, `bpm` (10–300, default 80). `select(id)` sets the
  unit and starts the loop; `stop()`; `setBpm()`.
- The selected unit's ONE beat loops continuously. `beatMs = 60000/bpm`;
  `slotMs = beatMs / subdivision`; a click fires at each onset's `slot*slotMs`.
  The first onset of every repetition is **accented** (louder + higher click).
- Click is synthesized once per state into two `FloatArray` buffers (normal ≈2 kHz,
  accent ≈2.8 kHz, ~45 ms, exponential decay) — self-contained, no drum-sample
  dependency. Web synthesizes the same via an offline/one-shot buffer.
- `currentOnset` (index) is published for the playing-card highlight; the UI reads
  it to pulse the active unit (teal playhead, matching the app's Signal palette).

## Notation thumbnail

`RhythmNotationView` (Compose Canvas) / `rhythmNotationCanvas` (web) — same custom
draw approach as the fretboard. Given a `RhythmUnit`:

- One horizontal baseline; a filled notehead per note at its x (x = onset fraction).
- Stems up from each notehead.
- Beaming: notes shorter than a quarter (eighth/sixteenth/dotted-eighth/triplet)
  in the beat are beamed under one **primary** beam across their stem-tops.
  **Secondary** beam segments (the 16th beam) are drawn for sixteenth-duration
  notes: a full segment between two adjacent sixteenths, else a short right/left
  stub (covers [2,1,1], [1,1,2], [1,2,1], and the dotted-eighth+sixteenth [3,1]
  stub).
- A quarter (unit 1) draws a single stem, no beam.
- Dotted eighth (unit 7) gets an augmentation **dot** after its notehead.
- Triplet (unit 8) draws a "3" with a bracket above the beam.
- Below the drawing: the `count` string in monospace.

Kept minimal but recognizable at thumbnail size; the goal is note PLACEMENT, so
exact x-positions follow the onset fractions.

## UI (`RhythmUnitsScreen` / `rhythmUnitsUI`)

- Header: title "RHYTHM" + Back (matches other screens).
- Transport row: Play/Stop + BPM control (slider or ± with live value), mirroring
  the existing transport styling. Tapping a card also starts its loop.
- Section "Rhythmic units": a responsive grid of 8 cards. Each card = the notation
  thumbnail + name + count. The playing card highlights (teal) and shows a subtle
  onset pulse. Tapping a non-playing card switches the loop to it; tapping the
  playing card stops.
- Leaving the screen stops the loop (guarded on `currentSheet` so rotation doesn't
  stop it — same pattern as the other looping screens, v2.14.1).

## Files

- theory: `theory/.../RhythmUnits.kt`, `chorect-web/src/theory/rhythmUnits.ts`
  (+ re-export from the theory index).
- app: `RhythmUnitState.kt`, `RhythmUnitsScreen.kt` (incl. `RhythmNotationView`);
  `Shell.kt` (TabDest + availableFor + subtitle), `MainActivity.kt` (route),
  `AppState.kt` (lazy `rhythmUnits` state), `Sheet` enum.
- web: `rhythmUnitState.ts`, `rhythmUnitsUI.ts` (incl. `rhythmNotationCanvas`),
  `ui.ts` route + Sheet/TabDest wiring, `appState.ts` Sheet + tab-dest enums.

## Testing

- theory unit test: every `RhythmUnit`'s note durations sum to `subdivision`;
  `onsets` are strictly increasing and start at 0; `onsetFractions` in [0,1).
- Build: `:theory:test` + `:app:assembleDebug` green; web tsc via Pages deploy.

## Out of scope (YAGNI)

No rests, no multi-bar patterns, no time-signature selection, no note-value ladder
(whole/half), no user-editable units — just the 8 fixed one-beat cells.

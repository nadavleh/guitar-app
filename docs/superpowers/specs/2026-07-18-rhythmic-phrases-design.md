# Rhythmic Phrases — design

A second mode on the Rhythm screen: generate a multi-bar rhythmic **phrase** built
from the one-beat units, show it as notation + a drum-machine-style grid with a
sweeping playhead, and loop it at a controllable BPM to practice reading/playing.

References: rhythmrandomizer.com, musicca.com/exercise/720, and the video
p9qynMmBz30 (notation with a highlighted playing beat).

## Navigation

The Rhythm screen gains a segmented **Units | Phrases** toggle at the top. "Units"
= the existing one-beat trainer; "Phrases" = this new mode. A new `RhythmPhraseState`
(theory generator stays pure) drives it; both states live on AppState / the web UI.

## Model (theory — `RhythmPhrases`, Kotlin + TS mirror)

- Pool = the 16th-grid units (`RhythmUnits.ALL` where `subdivision == 4`, i.e. all
  plain units except the triplet) **plus** `RhythmUnits.RESTS`.
- `RhythmPhrase(bars, beatsPerBar, beats: List<RhythmUnit>)` where
  `beats.size == bars * beatsPerBar` and every beat is a subdivision-4 unit, so the
  whole phrase maps onto a clean 16th grid (`slotsPerBeat = 4`,
  `totalSlots = beats.size * 4`).
- `generatePhrase(bars, beatsPerBar, rng)` fills each beat with a random pool unit.
- `PhraseOnset(slot, accent)` — the global 16th-slot of every click; `accent` is true
  on a bar downbeat (beat 0 of a bar) when that slot actually has an onset.
- Config ranges: `bars` 1–4, `beatsPerBar` ∈ {2,3,4} (time sig N/4), BPM 10–300.
  Defaults: 2 bars of 2/4, BPM 30.

Unit-tested: generated phrase length; every beat subdivision 4; onsets in
`[0, totalSlots)`; a seeded rng is reproducible.

## Playback (`RhythmPhraseState`)

Mirrors the drum machine's slot loop: iterate `currentSlot` 0..totalSlots-1 at
`slotMs = (60000/bpm)/4`, publish `currentSlot` (drives both playheads), and on an
onset slot schedule the synthesized woodblock click (accent buffer on bar downbeats),
using the mixer/AudioContext-clock lookahead so timing never drifts. Loops until
stopped. `currentBeat = currentSlot / 4` drives the notation highlight.

State: `bars`, `beatsPerBar`, `bpm`, `phrase`, `isPlaying`, `currentSlot`.
`generate()` rebuilds the phrase (and resets the playhead), `play/stop/toggle`,
`setBars/setBeatsPerBar/changeBpm`. Changing config regenerates.

## Views (both shown; both track the playhead)

1. **Notation staff.** The phrase laid out as notation: each beat rendered by the
   existing one-beat notation renderer (`RhythmNotation` Compose / `drawNotation`
   web) in its own box, beats grouped into bars with a bar-line between bars, wrapping
   to new lines as needed. The **currently-playing beat's box is highlighted** (teal
   tint) — like the reference image's blue highlight.
2. **Drum grid** (opened below, like ear-training's fretboard). One row of
   `totalSlots` cells styled like the drum machine: onset cells filled (accent cells
   brighter), thin **beat** dividers every 4 slots and thick **bar** dividers every
   `beatsPerBar*4` slots, and a **playhead** highlight on `currentSlot` that sweeps
   during playback.

## UI (Rhythm screen, Phrases mode)

Header (fixed): the Units/Phrases segmented toggle. Config row: **Bars** stepper,
**Time** dropdown (2/4·3/4·4/4), **Generate ↻**, **Play/Stop**, BPM slider + value.
Scrollable body: the notation staff, then the drum grid. Leaving the screen /
switching sub-mode stops playback (rotation-safe guard on Android).

## Files

- theory: `RhythmPhrases.kt` + `rhythmPhrases.ts` (re-exported from the theory index)
  + `RhythmPhrasesTest.kt`.
- Android: `RhythmPhraseState.kt`; extend `RhythmUnitsScreen.kt` with the sub-mode
  toggle + phrase view (notation row + grid); `AppState` lazy `rhythmPhrase`.
- Web: `rhythmPhraseState.ts`; extend `rhythmUnitsUI.ts` with the sub-mode + phrase
  view; wire the new state in `ui.ts`.

## Out of scope (YAGNI)

No 6/8/compound meter, no triplet beats (excluded from the pool for a clean grid),
no per-beat editing, no export, no scoring/challenge — just generate + read + loop.

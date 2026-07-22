# Drum-machine Blocks — design (v2.27.0)

Approved by Nadav 2026-07-22 ("Yes, build that"). Items 2/8/9/11 of his request.

## Concepts

- **Phrase** = a `PresetTrack` chunk (theory `PercussionBuiltins.PRESET_TRACKS` /
  web `PRESET_TRACKS`): one instrument, a 16-slot (2 bars of 2/4, 1/16) template,
  its own `swing` (0 = straight; chamada = 61) and `note`. Already shipped in
  v2.26.0.
- **Block** = a grid of *tracks × phrase slots*. Each row (track) is one
  instrument; each cell holds a phrase for that instrument or is empty (silence
  for that phrase's duration). A block loops column by column: column 0's
  phrases play on all tracks simultaneously (16 straight slots long), then
  column 1, …, then wraps.
- **Per-track swing clocks (item 11)**: each cell plays with **its phrase's own
  swing**. Swing micro-timing preserves beat anchors (see `swungSlotMs`), so a
  swung track and a straight track stay bar-aligned; columns advance on the
  straight clock (16 × base slot). A swung phrase followed by a straight phrase
  returns to normal timing naturally.
- **Return rule (item 8)**: `PresetTrack.addsReturnDownbeat = true` on
  "Bongo — partido alto var 1". At schedule time, if track T plays a phrase P at
  column c and the *previous* column's phrase (wrapping around the loop) has
  `addsReturnDownbeat`, and P's slot 0 is empty, then P gains a strong beat at
  slot 0 = P's slot-8 stroke (its measure-2 downbeat) + accent. Generic: any
  phrase can declare the flag. The rule is also written in the phrase's `note`.
- **Merge (item 2)**: two blocks with the same phrase count merge into a new
  block whose tracks are the union (A's tracks then B's). All phrases are
  16-slot chunks, so lengths always match.

## Per-slot dynamics (item 9) — extended raw cell values

No new pattern fields. A cell's raw value becomes:

    raw = voice + 100·accent + 1000·dynLevel      (dynLevel ∈ 0..3)

    dynLevel 0 = 100% (default), 1 = 75%, 2 = 50%, 3 = 25%
    voiceAt   = raw % 100
    isAccented = (raw / 100) % 10 == 1
    dynAt      = raw / 1000  → gain factor 1.0 / 0.75 / 0.5 / 0.25

- Validation in both `PercussionPattern` decoders/inits loosens to allow
  raw / 1000 ≤ 3 (older app versions reject such cells and skip the beat —
  the established forward-compat path).
- Scheduler gain ×= dynFactor. Cell UI renders reduced-dyn cells at reduced
  opacity with a small `75/50/25` corner tag.
- New **Dyn** tool next to Erase/Accent: tapping a non-silent cell cycles
  100 → 75 → 50 → 25 → 100.
- `cycled()` and `accentToggled()` preserve the dyn level (like accent survives
  voice cycling).

## Blocks UI (both platforms, identical design)

Drum screen gains a segmented top-level toggle: **[Beat | Blocks]**.

Blocks view:
- **Block header**: name field, [＋ Track ▾] (choose instrument), phrase-count
  stepper (− N +, 1..8), [Merge with… ▾] (blocks with same phrase count),
  [Save block], [Load block ▾ (saved blocks; ✕ delete)], [Clear].
- **Grid**: rows = tracks (label = instrument, ✕ remove); columns = phrase
  slots. Each cell shows the assigned phrase's short label (or "＋"); tapping a
  cell opens the phrase picker filtered to that row's instrument (plus
  "(empty)"). Cells show a small `~N%` swing badge when the phrase is swung and
  a `※` badge when it has a note; the note text shows under the grid for the
  last-tapped cell.
- **Playback**: transport dock (Play/Stop + inline BPM slider reused). The
  playing column is highlighted across all rows.
- Persistence: saved blocks in localStorage (web `chorect-web.v1` key
  `drumBlocks`) / DataStore (`drum_blocks`), encoded as
  `name=instId:phraseLabel,phraseLabel,…|instId:…` per line (phrase referenced
  by label; unknown labels = empty cell).

## Scheduling (mirrors the beat scheduler's lookahead style)

Per play: token++, startOnset = now. For each track independently (own async
loop / coroutine): for column c in 0..∞ (mod phraseCount): phrase = cells[c]
(after return-rule materialization); for slot 0..15: onset = columnStart +
swungOnset(slot, phrase.swing); schedule one slot ahead. columnStart advances by
16 × base every column on the straight clock for ALL tracks (shared), so tracks
never drift. UI playhead = current column (+ straight slot for the column
highlight).

## Plan

1. theory (kt+ts): PresetTrack.addsReturnDownbeat; Block/BlockTrack model +
   encode/decode + `materializedCell(phrase, prev)` return-rule helper + tests.
2. theory (kt+ts): dynamics — validation loosening, dynAt/withDynCycled
   helpers + tests.
3. web: BlocksState (edit + scheduler) + blocksUI + appState persistence +
   [Beat | Blocks] toggle; Dyn tool in the beat editor; dyn-aware gain in
   SambaLooperState scheduler.
4. Android mirror: BlocksState.kt + BlocksSection in SambaLooperScreen +
   TuningRepository blocks store; Dyn tool; dyn-aware gain.
5. v2.27.0, tests, ship both.

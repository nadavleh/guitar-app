# Samba percussion looper — design plan

> **Status:** plan only. Implementation deferred.

A step-sequencer style looper for samba percussion patterns, modelled after
[musicca.com's drum machine](https://www.musicca.com/drum-machine).
Each instrument has its own row of 16 cells (= 2 bars of 2/4 in sixteenth
notes); tapping a cell schedules a hit at that point in the loop. Some
instruments have multiple voices — tapping a populated cell cycles through
them (empty → voice 1 → voice 2 → … → empty).

## Time grid

```
| beat 1 | beat 2 || beat 1 | beat 2 |
| . . . .| . . . .|| . . . .| . . . .|
   bar 1               bar 2
```

- **2 bars of 2/4** = 4 quarter-note beats total
- **Each quarter divided into 4 sixteenths** → 16 sixteenth-note slots per row
- At BPM *N*, one sixteenth = `(60_000 / N) / 4` ms (e.g. 120 BPM → 125 ms)
- Loop wraps after slot 16 back to slot 0

## Instruments (initial set)

| Instrument | Voices | Sound character |
|---|---|---|
| **Surdo** | 2 — open / muffled | Low boom + tail (open). Palm-mute = thump with no sustain. Carries the pulse on beats 1 & 3. |
| **Tamborim** | 2 — open / muted | Bright, dry crack (open). Mute = quick choke. The 16th-note ostinato. |
| **Pandeiro** | 3 — low (slap) / high (open) / mute (skin tap, no jingles) | Thumb on the low rim, fingers on the open zone, palm mute for the muted hits. Frequent sub-divisions. |
| **Agogô** | 2 — low bell / high bell | Two tuned pitches, no decay tail; the alternating bell pattern. |

Tapping cycles through `null → voice 1 → voice 2 → … → null`. Long-press to
clear without cycling (nice-to-have).

## Audio

### Phase 1: synthesized percussion (no samples)
- **Surdo open**: low sine (~70 Hz) with a short attack + exponential decay,
  layered with filtered noise for the strike transient.
- **Surdo muted**: same but very short envelope (~80 ms).
- **Tamborim open**: short noise burst, high-pass filter ~3 kHz, ~120 ms.
- **Tamborim muted**: same, half the duration.
- **Pandeiro low**: noise burst, low-pass filter ~1 kHz, ~100 ms.
- **Pandeiro high**: noise burst, low-pass ~3 kHz, ~150 ms.
- **Pandeiro mute**: noise tick, no sustain, ~40 ms.
- **Agogô low**: pure sine ~600 Hz, soft envelope, ~250 ms.
- **Agogô high**: pure sine ~750 Hz, ~250 ms.

All synthesised on the fly in the audio module — no asset bundling, identical
to how `PluckedSynth` works today.

### Phase 2: sampled audio
Bundle royalty-free or self-recorded samples (~30 KB each, mono 16-bit WAV).
Audio module loads them once, mixes them into the existing continuous output.

## UI

Full-screen route, like Loop / Tuner / EarTraining.

```
┌─────────────────────────────────────────────────────────────────┐
│ SAMBA LOOPER          [Play ▶]   BPM ──────────[ 110 ]   [Back] │
├─────────────────────────────────────────────────────────────────┤
│ Surdo     │ ▮ . . . │ . . . . │ ▮ . . . │ . . . . │              │
│ Tamborim  │ . ▮ ▮ ▮ │ ▮ . . . │ . ▮ ▮ ▮ │ ▮ . . . │              │
│ Pandeiro  │ ▮ ▮ ◤ ▮ │ ▮ ◤ ▮ ▮ │ ▮ ▮ ◤ ▮ │ ▮ ◤ ▮ ▮ │              │
│ Agogô     │ . . . ◢ │ ◣ . . . │ . . . ◢ │ ◣ . . . │              │
└─────────────────────────────────────────────────────────────────┘
            beat 1   beat 2     beat 1   beat 2
              bar 1                bar 2
```

- **Cells** are touch-targets; tap to cycle voice.
- **Cell glyph** indicates the chosen voice (▮ for voice 1, ◤ or ◢ for voice 2,
  ▣ for voice 3 — TBD; can also just color-code by voice index).
- **Playhead column** is tinted while playing — same primary-tinted highlight
  as the Loop screen uses for the currently-playing bar.
- **Beat boundaries**: a 2-px vertical line every 4 cells (quarter beats),
  3-px every 8 cells (bar boundaries).
- **Header**: title, Play / Stop, BPM slider (60–200), Back.
- **Footer** (optional): instrument-volume sliders, "clear row" buttons,
  preset patterns (samba, partido alto, marcha, …).

## Code structure

### theory module (rhythm types, pure Kotlin)
- `Percussion.kt`
  - `enum class PercussionInstrument { Surdo, Tamborim, Pandeiro, Agogo }`
  - `data class PercussionVoice(val instrument: PercussionInstrument, val index: Int, val displayName: String)`
  - per-instrument voice catalog (number of voices, display names)
- `PercussionPattern.kt`
  - `data class PercussionSlot(val voice: PercussionVoice?)` — null = silent
  - `data class PercussionPattern(val grid: Map<PercussionInstrument, List<PercussionSlot>>, val slotCount: Int = 16)`
  - Default samba patterns: `samba`, `partidoAlto`, `marchaRanchada`, etc. — for the "preset" buttons.

### audio module (new file)
- `PercussionSynth.kt`
  - `fun synthesizeVoice(voice: PercussionVoice, sampleRate: Int): FloatArray`
  - Returns a one-shot buffer per voice (synthesized once at sample-rate then cached).
- `AudioEngine.playPercussion(voice: PercussionVoice)` — new method that pushes the cached buffer into the existing mixer (same `voices` list that already handles guitar plucks).

### app module
- `SambaLooperState.kt`
  - Compose-observable: `pattern`, `bpm`, `isPlaying`, `currentSlot`
  - `toggleSlot(instrument, slotIdx)` — cycles the voice
  - `startLoop() / stopLoop()` — coroutine that ticks every sixteenth
- `SambaLooperScreen.kt` — the grid UI described above.
- `AppState.kt` — add `Sheet.SambaLooper`, full-screen route.
- `MainActivity.kt` — top-bar "Samba" button (or include under Menu ☰ to
  avoid further crowding the status row — TBD).

### Persistence
The current pattern stays in memory by default; the user can save preset
patterns to disk in a follow-up. (Phase 2.)

## Tests

- `PercussionPatternTest`: cycle semantics — toggling a cell goes
  `null → v1 → v2 → null` (or however many voices the instrument has).
- `PercussionSchedulerTest`: time math — at BPM 120 and slotCount 16, total
  loop length = 2000 ms; each slot = 125 ms.
- `SambaPatternsTest`: the bundled samba pattern hits the canonical samba
  pulses (surdo on beats 1 & 3 of each bar; pandeiro on every sixteenth
  with the choreographed low/high/mute alternation, etc.).

## Phased rollout

1. **Phase 1 — engine + UI plumbing.** Synthesized percussion voices,
   step-grid UI, BPM control, Play/Stop, one preset (basic samba).
   Hand-tweak the sound parameters until they're acceptable.
2. **Phase 2 — sampled audio.** Replace the synth with actual recorded
   samples for each voice. Free royalty samples sourced from
   freesound.org / archive.org.
3. **Phase 3 — pattern library + persistence.** Save / load patterns,
   bundle common samba / partido-alto / marcha presets, share preset URLs.
4. **Phase 4 — pair with guitar progression.** Run the samba looper
   simultaneously with the chord-progression looper so the user can practice
   with a full backing groove.

## Open questions

- **Time signature**: stuck at 2/4 = 8 sixteenths per bar × 2 bars = 16 slots?
  Or expose meter-and-bars sliders like the existing Loop screen does?
  (Default: hardcode 2/4 × 2 bars for v1; add flexibility in v2.)
- **Tap-to-set vs. cycle**: confirmed cycling is the right interaction. Long-press
  to clear?
- **Visual encoding** for multi-voice cells: color, glyph, or both?
- **Synced playback with the chord progression looper**: should the user be able
  to lock the two transports? Same BPM, simultaneous start?

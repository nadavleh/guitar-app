# Progression Library Expansion — Design

**Date:** 2026-07-07
**Status:** Approved (design questions answered; proceed to plan + implementation, review gate waived).
**Target version:** v1.25.0 (minor — content expansion).
**Scope:** add new chord progressions from Nadav's curated list (via a ChatGPT conversation) to the ear-training progression library — diatonic 4-chord ones to Major/Minor, everything else (secondary dominants, borrowed chords, diminished approach chords, extensions, non-4-length) to the Advanced section — plus researched famous-song examples for each new advanced entry. Android (`theory` module) + `chorect-web` parity.

## Rules (from Nadav)
- 4-chord, purely-diatonic-triad progressions → `MAJOR_PROGRESSIONS` / `MINOR_PROGRESSIONS` (degree lists).
- Everything else → `ADVANCED_PROGRESSIONS` (`NamedProgression`, transposable `AdvChord`s) + song examples in `ProgressionSongs`.
- **Pad progressions shorter than 4 chords to 4**: insert a `ii` before the `V` (e.g. Deceptive `I–V–vi` → `I–ii–V–vi`); if there's no `V`, anchor on the tonic (append/prepend I/i).
- Dedupe against the existing library; don't re-add anything already present.
- Research real famous-song examples per new advanced progression: **titles/artists only** (avoids the content-filter trip seen in the earlier song-research work).

## Already present (skip)
`I–V–vi–IV`, `I–iii–IV–V`, `I–vi–ii–V` (Major); `ii–V–I` (= `ii-V-I-I` Major); `i–iv–V` (= `i-iv-V-i` Minor); `i–♭VII–♭VI–V` (Andalusian); plus family representatives already in Advanced (Mixolydian Rocker ♭VII, Mario ♭VI-♭VII, Broadway Lift III7, Chromatic Passing #I°7, Gospel Walk-Up #IV°7, Ragtime Circle VI7-II7-V7, Classic Ragtime I-I7-IV-iv, etc.).

## NEW — Major diatonic (append to `MAJOR_PROGRESSIONS`)
| Name | Degrees |
|---|---|
| I–iii–vi–IV | `[1,3,6,4]` |
| vi–ii–V–I | `[6,2,5,1]` |
| I–ii–V–I | `[1,2,5,1]` |

## NEW — Advanced (append to `ADVANCED_PROGRESSIONS`)
`AdvChord(semitone, quality, roman)`. Major-key offsets: I 0, ii 2m, iii 4m, IV 5, V 7, vi 9m, ♭III 3, ♭VI 8, ♭VII 10, iv 5m; secondary doms II7 2·"7", III7 4·"7", VI7 9·"7"; dim #I° 1·"dim", #ii° 3·"dim", #iv° 6·"dim". Minor uses i 0m, iv 5m, V 7·"", #iv° 6·"dim".

| # | Name | Mode | Chords (roman) | Encoding |
|---|---|---|---|---|
| 1 | Deceptive Cadence | Major | I–ii–V–vi | 0"" 2"m" 7"" 9"m" |
| 2 | Applied V of V | Major | I–II7–V–I | 0"" 2"7" 7"" 0"" |
| 3 | Tonicized Relative | Major | I–III7–vi–I | 0"" 4"7" 9"m" 0"" |
| 4 | Applied V of ii | Major | I–VI7–ii–V–I | 0"" 9"7" 2"m" 7"" 0"" |
| 5 | Long Applied Turnaround | Major | I–III7–vi–II7–V–I | 0"" 4"7" 9"m" 2"7" 7"" 0"" |
| 6 | Borrowed iv | Major | I–IV–iv–I | 0"" 5"" 5"m" 0"" |
| 7 | Mixolydian Vamp | Major | I–V–♭VII–IV | 0"" 7"" 10"" 5"" |
| 8 | ♭VI–♭VII Climb | Major | I–♭VI–♭VII–I | 0"" 8"" 10"" 0"" |
| 9 | Flat-Six Color | Major | I–♭VI–IV–V | 0"" 8"" 5"" 7"" |
| 10 | Flat-Three Borrowed | Major | I–♭III–IV–I | 0"" 3"" 5"" 0"" |
| 11 | Chromatic Descent | Major | I–iii–♭III–ii–V | 0"" 4"m" 3"" 2"m" 7"" |
| 12 | Diminished to ii | Major | I–#I°–ii–V | 0"" 1"dim" 2"m" 7"" |
| 13 | Diminished to iii | Major | ii–#ii°–iii–VI7 | 2"m" 3"dim" 4"m" 9"7" |
| 14 | Minor #iv° to V | Minor | i–#iv°–V–i | 0"m" 6"dim" 7"" 0"m" |
| 15 | Minor Plagal Diminished | Minor | i–iv–#iv°–i | 0"m" 5"m" 6"dim" 0"m" |
| 16 | iii–VI–ii–V Turnaround | Major | iii7–VI7–ii7–V7 | 4"m7" 9"7" 2"m7" 7"7" |
| 17 | Rhythm-Changes Turnaround | Major | Imaj7–VI7–ii7–V7 | 0"maj7" 9"7" 2"m7" 7"7" |
| 18 | Bossa Minor Diminished | Minor | i–iv–#iv°–V7 | 0"m" 5"m" 6"dim" 7"7" |
| 19 | Ragtime Return | Major | I–I7–IV–iv–I | 0"" 0"7" 5"" 5"m" 0"" |
| 20 | Bossa Chromatic | Major | Imaj7–#I°–ii7–V7 | 0"maj7" 1"dim" 2"m7" 7"7" |
| 21 | Extended vi Turnaround | Major | I–vi–IV–iv–I | 0"" 9"m" 5"" 5"m" 0"" |
| 22 | Full Turnaround | Major | I–vi–ii–V–I | 0"" 9"m" 2"m" 7"" 0"" |

Each gets a one-sentence teaching `explanation` in the style of the existing entries. Padding decisions (#1 ii-before-V per Nadav's example; #3/#14/#15 anchored on tonic) are baked into the encodings above.

## Song examples (`ProgressionSongs`)
- **Major diatonic new ones** → `ProgressionSongs.major` (keyed by degree `List<Int>`), via `forDiatonic`.
- **Advanced new ones** → `ProgressionSongs.advanced` (keyed by name), via `forAdvanced`.
- Research 3–6 famous songs per new advanced progression (titles/artists only). Curate to reduce repetition across the jazz-turnaround family (they share standards). Label them "characteristic examples" like the existing advanced entries.

## Parity
Mirror every addition into `chorect-web/src/theory/eartraining.ts` (`MAJOR_PROGRESSIONS`, `ADVANCED_PROGRESSIONS` — same `adv(...)`/degree shape) and `chorect-web/src/theory/progressionSongs.ts` (`major`/`advanced` maps). The progression-library dialog + play/fretboard already render whatever's in these lists (no UI change needed).

## Testing
- `ProgressionSongsTest` (Kotlin) coverage/hygiene: every new advanced name has a song list; diatonic new ones resolve; no empty/malformed entries.
- Existing theory + audio tests stay green.
- Web `verify.ts`: extend the existing progression checks to the new counts.

## Milestones
1. **Theory data (Kotlin)** — append 3 Major + 22 Advanced (`NamedProgression`s) with encodings above + explanations.
2. **Song research + `ProgressionSongs` (Kotlin)** — research per new advanced, add `major`/`advanced` entries.
3. **Web mirror** — `eartraining.ts` + `progressionSongs.ts` parity.
4. **Tests + docs** — `ProgressionSongsTest`, update the playlist `docs/progression_songs.md`.
5. **Ship v1.25.0** — tests, assembleDebug, commit, push, web CI.

## Out of scope
- The section-12 "reharmonization tools" (they're substitution *moves*, not fixed progressions; their concrete examples are covered above or already present).
- Any change to the quiz/generator logic or the library UI (data-only expansion).

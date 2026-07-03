# Progression Song Examples — Design

**Date:** 2026-07-03
**Status:** Approved design, pending spec review

## Goal

Make every entry in the progression library clickable. Tapping a progression reveals a
list of famous songs built on that progression. Also produce a Markdown file listing the
songs per progression, structured so it can later be turned into a Spotify playlist.

## Requirements

1. Each progression in `ProgressionLibraryDialog` (Android) and its web equivalent becomes
   clickable; tapping expands an inline list of songs (`Title — Artist`), tapping again collapses.
2. Songs must be famous / non-obscure (well above ~100 listings on YouTube/Spotify). Prefer
   many examples per progression.
3. Cover all four sections: Major (diatonic), Minor (diatonic), Advanced (named), Circle of fifths.
4. Produce `docs/progression_songs.md` — one section per progression, songs as `- Title — Artist`.

## Non-goals

- No audio playback of the songs, no external links/embeds, no Spotify API integration.
- No change to the ear-training generation logic itself.

## Data model (theory module — pure, unit-testable)

New file `theory/src/main/kotlin/app/guitar/theory/ProgressionSongs.kt`:

```kotlin
data class SongExample(val title: String, val artist: String)

object ProgressionSongs {
    val major: Map<List<Int>, List<SongExample>>   // keyed by Progression.degrees, e.g. [1,5,6,4]
    val minor: Map<List<Int>, List<SongExample>>
    val advanced: Map<String, List<SongExample>>     // keyed by NamedProgression.name
    val circle: List<SongExample>

    fun forDiatonic(p: Progression): List<SongExample>   // major/minor by mode + degrees
    fun forAdvanced(name: String): List<SongExample>
}
```

Rationale: lives with the theory engine (requirements §12 separation), testable without UI,
KMP-shareable for the planned iOS port. Keys are the existing stable identifiers already used
by the library dialog (degree list for diatonic, `NamedProgression.name` for advanced).

## Accuracy / sourcing

- **Diatonic (major + minor):** compiled from established, unambiguous examples; these
  progressions are ubiquitous, so many famous hits qualify.
- **Advanced (25 named) + circle:** fewer clean matches. Famous *characteristic* examples,
  web-verified for the contested ones before listing. UI labels these sections' examples as
  "characteristic examples," not guaranteed note-for-note.

## UI — Android

In `ProgressionLibraryDialog` / `LibrarySection` (`EarTrainingScreen.kt`):

- Replace static `Text` lines with clickable rows (`Modifier.clickable`), each with a
  trailing expand/collapse chevron when songs exist.
- Local `remember` set tracks which rows are expanded (multiple can be open).
- Expanded row shows songs indented below, `Title — Artist`, `bodySmall`.
- Rows with no song list are rendered as before (non-clickable).
- Advanced + circle sections show a one-line "characteristic examples" caption.

## UI — web mirror (chorect-web)

Mirror the same clickable-expand behavior in the TS progression-library view
(`earTrainingUI.ts` / related). Song data duplicated in a TS module (Kotlin can't be imported).
No local Node; verify the web build via the gh CI workflow.

## Markdown export

`docs/progression_songs.md`:

- Grouped by section (Major / Minor / Advanced / Circle).
- Each progression: a `###` heading with roman-numeral line (and name for advanced) + one-line
  description, followed by `- Title — Artist` bullets.
- A short intro noting the file is meant for building a Spotify playlist.

## Testing

- Unit test in `theory` module: `ProgressionSongs.forDiatonic` / `forAdvanced` return non-empty
  lists for every progression in `MAJOR_PROGRESSIONS`, `MINOR_PROGRESSIONS`, `ADVANCED_PROGRESSIONS`
  (guards against a progression being added later with no songs, and against key typos).
- Existing theory tests must stay green.

## Versioning / release

- Feature → minor bump (v1.19.0). Android APK built via `assembleDebug` at batch end; debug
  folder keeps only the newest APK. Old APKs preserved in `releases/`.

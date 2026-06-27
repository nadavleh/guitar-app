# Add-instrument slot (Brazilian + Latin percussion) — design

**Date:** 2026-06-27
**Note:** drum-machine notes batch (#3). Notes #1/#2/#4 already done in the same session.

## Goal

In the drum machine, let the user add percussion instruments beyond the default
four by tapping an "**+ Add instrument**" tile and picking from a catalog of
Brazilian/Latin instruments. Samples are sourced from the Ableton *Latin
Percussion* factory pack.

## Decisions (confirmed with Nadav)

- **Default view stays exactly as-is:** Surdo, Tamborim, Pandeiro, Agogô, with
  their existing bundled WAVs. No regeneration of those four.
- **Picker offers the full Brazilian + Latin set** (~20 new, 24 total).
- Samples come from the pack's per-hit preview Oggs
  (`…/Previews/Drums/Drum Hits/<cat>/<name>.adg.ogg`), decoded to **mono 44.1 kHz
  PCM_16 WAV** — identical to the format the app already loads. The full-quality
  `.aif` files are Ableton-proprietary-encoded (FORM/AIFC/`able` codec) and not
  decodable; the previews are the same source the existing four came from.

## Data-model change (the core of this work)

Today `PercussionInstrument` is a fixed 4-value **enum**, and every
`PercussionPattern` must carry a row for all four. That makes "add an instrument"
impossible. Replace it with a data-driven catalog + a per-pattern kit.

### `theory/Percussion.kt`
```kotlin
data class PercussionVoice(val displayName: String, val glyph: String)

data class PercussionInstrument(
    val id: String,                 // stable key for assets + persistence (e.g. "cuica")
    val displayName: String,
    val voices: List<PercussionVoice>,
)

object PercussionCatalog {
    val DEFAULT_KIT: List<PercussionInstrument>   // surdo, tamborim, pandeiro, agogo
    val ALL: List<PercussionInstrument>           // DEFAULT_KIT + ~20 more, display order
    fun byId(id: String): PercussionInstrument?
}
```
`PercussionVoices` helper object stays as a thin facade (`voicesFor`, `voiceCount`,
`voice`) delegating to `instrument.voices`, so call sites change minimally.

### `theory/PercussionPattern.kt`
```kotlin
data class PercussionPattern(
    val instruments: List<PercussionInstrument>,  // ordered kit (rows top→bottom)
    val grid: Map<String, List<Int?>>,            // instrument id -> cells
    val meter: PercussionMeter = PercussionMeter.DEFAULT,
)
```
- `init` validates only the kit's rows (length == totalSlots, voice indices in
  range) — no longer "must cover every catalog instrument".
- New: `addInstrument(inst)` (append silent row), `removeInstrument(inst)`.
- `empty(kit = DEFAULT_KIT, meter)`, `voiceAt`, `cycled`, `withCell`, `clearedRow`,
  `translated`, `withMeter`, `isEmpty` keep their signatures (take a
  `PercussionInstrument`, resolve `.id` internally).
- **Encode bumps format** to carry ids: `M:…;id=cells|id=cells|…`. `decode`
  parses ids, looks each up in the catalog, **skips unknown ids gracefully**
  (returns a valid pattern over the recognized kit; null only on structural
  garbage). Legacy enum-ordered beats won't load — acceptable mid-development.

### `audio/PercussionSynth.kt`
`synthesize(instrument, voiceIndex)` switches on `instrument.id`. The existing
four keep their handcrafted synth voices. New instruments rely on bundled
samples; the synth fallback for an unsampled new voice is a generic short click
(safety only — every catalog voice ships a WAV).

### `app/MainActivity.kt`
Sample path becomes `drums/${inst.id}_$voice.wav` (was `inst.name.lowercase()` —
same strings for the existing four, since their ids equal their lowercased names).

### `app/SambaLooperState.kt`
- Default `pattern = PercussionPattern.empty()` (already empty after #4) over the
  default kit.
- Volumes map keyed by instrument id, lazily defaulting to 1f for added rows.
- `addInstrument(inst)` / `removeInstrument(inst)` delegate to the pattern.
- The play loop iterates `pattern.instruments` (not the enum).

### `app/SambaLooperScreen.kt`
- Grid iterates `samba.pattern.instruments`; height derives from that count.
- Each row label gets a small **✕ remove**.
- Below the grid: an "**+ Add instrument**" tile → a dropdown/dialog listing
  `PercussionCatalog.ALL` minus instruments already in the kit → tap to add.

## Catalog (24 instruments)

Existing 4 (unchanged): **surdo** (open/muted/tap), **tamborim**
(clack/muted/tap), **pandeiro** (open/muted/slap/jingle), **agogo** (low/high).

New 20, each with a curated voice set sourced from the named preview Oggs
(all "A"/"1" variant unless noted):

| id | display | voices (← source hit) |
|----|---------|----------------------|
| cuica | Cuíca | low ←Cuica 1 Low · high ←Cuica 1 High |
| caxixi | Caxixi | open ←Caxixi 1 Open · hand ←Caxixi 1 Hand · fx ←Caxixi 1 FX |
| shaker | Shaker (Ganzá) | 1 ←Shaker 1 · 2 ←Shaker 2 |
| guiro | Guiro (Reco-reco) | down ←Guiro A Down · up ←Guiro A Up · long ←Guiro A Long |
| claves | Claves | 1 ←Claves A 1 · 2 ←Claves A 2 |
| cowbell | Cowbell | 1 ←Cowbell A 1 · 2 ←Cowbell A 2 |
| triangle | Triangle | open ←Triangle A Open · mute ←Triangle A Mute |
| apito | Apito (whistle) | low ←Samba Whstl 1 L · mid ←Samba Whstl 1 M · high ←Samba Whstl 1 H |
| cabasa | Cabasa | short ←Cabasa 1 Short · long ←Cabasa 1 Long · fx ←Cabasa 1 FX |
| conga | Conga | open ←Conga A Open · mute ←Conga A Mute · slap ←Conga A Slap Open · tip ←Conga A Tip |
| quinto | Quinto | open ←Quinto A Open · mute ←Quinto A Mute · slap ←Quinto A Slap Open |
| tumba | Tumba | open ←Tumba A Open · mute ←Tumba A Mute · slap ←Tumba A Slap |
| bongo | Bongo | hi ←Bongo A H Open 1F · lo ←Bongo A L Open 1F · rim ←Bongo A H Rim · slap ←Bongo A L Slap |
| timbales | Timbales | hi ←Timbales A H Open · lo ←Timbales A L Open · cascara ←Timb A H Cascara · rim ←Timbales A H Rim |
| maracas | Maracas | hit ←Maracas · fx ←Maracas FX |
| vibraslap | Vibraslap | hit ←Vibraslap A · pan ←Vibraslap A Pan |
| castanet | Castanet | single ←Castanet A · roll ←Castanet A Roll |
| woodblock | Wood Block | hi ←Wood Block B Hi · mid ←Wood Block B Mid · low ←Wood Block B Low |
| cymbal | Cymbal | bell ←Cymbal A Bell · open ←Cymbal A Stick Opn |
| gong | Gong | hit ←Gong A 1 |

## Sample pipeline (one-time, offline)

`tools/build_drum_samples.py` (committed): a table mapping
`(id, voiceIndex) → preview-ogg path`. Uses `soundfile` to decode each ogg,
downmix to mono, normalize lightly, trim trailing silence, and write
`app/src/main/assets/drums/<id>_<voice>.wav` (44.1 kHz PCM_16). Run once; the
WAVs are committed so the build needs no Python.

## Testing

- Theory (TDD): catalog ids unique; pattern add/remove instrument; empty default
  kit = 4 rows; encode/decode round-trips an arbitrary kit incl. an added
  instrument; decode skips unknown ids; voice counts per catalog entry.
- Audio: `synthesize` returns non-empty for every (instrument, voice) in the
  catalog (fallback safety).
- Manual: build APK, add/remove instruments, audition voices, play a loop with a
  mixed kit, confirm swing still right on a 1/16 grid.

## Version

Feature → minor bump: **v1.8.0**. Keep prior APKs in `releases/`.

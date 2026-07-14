# Cavaquinho: samba song library, Songs button, shape-quality & minor-Roman fixes

Design + spec for the batch Nadav approved on 2026-07-14. Ship as **v2.6.0** (new
features → minor bump). Android + `chorect-web` parity throughout. Theory changes are
JVM-unit-tested; web mirrors and is verified by the Pages-deploy `tsc`+`vite` build.

## 1. Cavaquinho chord-shape quality (theory generator)

**Problem.** The default cavaquinho tuning is Brazilian **DGBD** (re-entrant D4 G4 B4 D5).
`cavaquinhoShapesFor` only has curated templates for **DGBe**; DGBD falls through to the
brute-force `ChordShapeGenerator`, which (a) treats open strings as candidates in *every*
anchor window (so it emits shapes mixing an open string with notes high up the neck),
(b) always allows muted strings, and (c) can span wide. Result: many DGBD voicings have
muted strings, open+high-fret combinations, or big spans — not real playable shapes.

**Rules to enforce (from Nadav):**
1. **No open + high fret.** If a shape uses any open string (fret 0), *every* fretted note
   must be ≤ fret 3. (An open string only makes sense in first position.)
2. **Span ≤ 5.** The fretted span (max − min over the non-open fretted strings) must be ≤ 5.
3. **Mutes are rare.** Prefer voicings where all 4 strings sound. Allow at most **1** muted
   string in a shape, and only when no full (0-mute) voicing exists. Across a *progression*,
   at most one chord may use a muted shape.

**Design.**
- **Generator validity (both platforms), in `ChordShapeGenerator.isPlayable` / web equiv):**
  - Reject if `hasOpen && maxFrettedFret > 3`.
  - Reject if `frettedSpan > 5` (absolute cap, independent of `maxFretSpan`).
  - Two-pass string-count requirement for 4-string instruments: first require **all 4**
    strings sound (`minStringsPlayed = stringCount`); if that yields nothing for a
    (root, quality), relax to the existing `minStringsPlayed`. Keep the existing ranking
    (`mutedCount` then `fretSpan`) so 0-mute low voicings win.
- **Progression selection (`CavaqProgState.shapes()` / `cavaqProgState.ts`):** when choosing
  the first shape and each voice-led next shape, prefer candidates with `mutedCount == 0`;
  fall back to muted only if none exist; track how many muted shapes the progression has and
  bias later picks toward 0-mute so the whole loop has ≤ 1 muted shape where achievable.
- **Curated DGBD templates (nice-to-have, include if low-risk):** add
  `CavaquinhoTemplates.DGBD` first-position voicings for the common qualities
  (major, minor, 7, m7, maj7, 6, m6, dim, m7♭5) so the *default* tuning shows idiomatic,
  mute-free shapes instead of brute-force output; wire into `cavaquinhoShapesFor` for
  `Tunings.cavaqDgbd`. If a template can't be verified quickly, omit it — the generator
  constraints above already guarantee legal shapes.

**Tests.** New `CavaquinhoShapeQualityTest` (theory): for DGBD, every generated shape for a
representative chord set satisfies rules 1–2 and mostly satisfies rule 3 (≤1 mute).

## 2. Minor-mode Roman-numeral labeling standard

**Problem.** `MINOR_DEGREES` labels the ♭3/♭6/♭7 degrees as `III`, `VI`, `VII` (no flats),
and the 5th as a **dominant `V`**. Nadav's standard: label everything relative to the MAJOR
scale, so lowered degrees carry a ♭.

**Target minor diatonic table:** `i` (m), `ii°` (dim), **`♭III`** (maj), `iv` (m),
**`v`** (m), **`♭VI`** (maj), **`♭VII`** (maj).

**Design.** Update `MINOR_DEGREES` (Kotlin `EarTraining.kt`) and the web mirror
(`eartraining.ts`) roman strings to `i, ii°, ♭III, iv, v, ♭VI, ♭VII`, and set degree-5 to a
**minor** quality (`m`/`m7`/`m9`) — natural minor. This is a *behavioral* change for
minor-mode ear-training (the diatonic 5th becomes `v`, not `V7`). Dominant cadences that are
genuinely wanted stay explicit and correct: the cavaquinho `basic_min` sequence already spells
its own romans (`i, I7, iv, V7` via AdvChord) and is unaffected; named/advanced/circle
progressions spell their own romans too. Use a real ♭ glyph ("♭"), matching existing labels.

**Scope of the ♭ change:** anywhere minor scale degrees are displayed — ear-training reveal
labels, the challenge answer keyboard's minor row (`keyboardKeys`/`EarTrainingDegrees`), and
generated `romanLabel`s. Major mode is already correct (relative to itself).

**Tests.** Update `EarTrainingTest` expectations (minor romans now flatted, degree-5 minor);
update any assertion that expected `V`/`III`/`VI`/`VII` in minor.

## 3. UI fixes (both platforms)

1. **Major/Minor toggle position.** The minor/major keyboard-shift toggle in the challenge
   answer pad currently sits far right. Move it to the **left** of its row (web confirmed;
   verify + match on Android).
2. **Version next to the title.** Show the app version beside the "Chorect" header title.
   - Android: `BuildConfig.VERSION_NAME` in the StatusBar/header.
   - Web: a `APP_VERSION` constant (kept in sync with `package.json`; simplest is a single
     exported const bumped on release) rendered in the app-header, styled small/dimmed.
3. **Songs popup invisible in light mode.** The Songs popup card (`showSongsPopup`, and the
   new cavaquinho one) uses a background that disappears on the light theme. Give the card a
   solid themed surface (`var(--surface-elev)`/`--surface2`) + `--text-primary`, so it reads
   in both themes. Verify the Android `AlertDialog` is fine (it uses theme colors already).

## 4. Samba song library (top 30) + Songs button on Progressions

**Curation.** Expand `docs/cavaquinho-samba-songs.md` to the **top 30** most-accessed samba
songs (ranks 1–30 already retrieved from CifraClub). Each row: title, artist, key, functional
Roman progression, family tag, source path. Reductions simplify extended/slash/dim voicings to
their harmonic role (reviewable — Nadav corrects before it's canon).

**Data model.** New pure dataset `CavaqSongs` (theory, both platforms), keyed by
**functional family** rather than exact chords (samba charts vary): e.g.
`ii-V-I`, `I-vi-ii-V`, `circle-of-fifths`, `IV-iv`, `minor-cadence`, `I-IV-vamp`. Each
`CavaqSequence` id maps to the family(ies) it teaches, and `CavaqSongs.forSequence(id)`
returns the curated songs in those families. Songs whose family no taught sequence covers
simply don't appear yet (acceptable — Nadav: "the link may have songs with different
progressions").

Mapping (initial):
- `quadradinho_maj` (I VI7 ii V7) → families {`ii-V-I`, `I-vi-ii-V`}
- `ii_v_i_maj` (ii V I) → family {`ii-V-I`}
- `basic_min` (i I7 iv V7) → family {`minor-cadence`}
- `medio_maj` (13-chord) → family {`ii-V-I`, `circle-of-fifths`}
- `campo_maj` (harmonic field) → {} (diatonic scale, no song match needed) or {`I-vi-ii-V`}

**UI.** A **"Songs ♪"** button on the cavaquinho **Progressions** screen (Android
`CavaqProgressionsScreen`, web `cavaqProgUI`) opens a popup listing
`CavaqSongs.forSequence(currentSequenceId)` as `Title — Artist (key)`. Empty → a friendly
"No curated songs match this sequence yet." Reuse the light-mode-fixed popup styling from §3.3.

## 5. Delivery

Order: (2) minor-Roman labels + tests → (1) shape-quality generator + tests → (3) UI fixes →
(4) curation + CavaqSongs + Songs button. Build `:theory:test` + `:app:assembleDebug` green,
bump to **v2.6.0** (versionCode 20600), archive APK, merge/push, dispatch + watch the web
deploy (verifies web `tsc`). Update the project-state memory.

**Non-goals / deferred:** full DGBD template dictionary for every exotic quality; perfect
harmonic analysis of every samba chart (curation is reviewable); auto-matching songs by exact
chord sequence (family-tag matching is the MVP).

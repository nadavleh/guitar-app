# Chorect — Requirements

**App name:** Chorect &nbsp;·&nbsp; **Version:** 1.2.0 (semantic `major.minor.patch`).

**Versioning policy:**

* **MINOR** bumps for new features; **PATCH** bumps for bug fixes of existing features (MAJOR is reserved for milestone releases).
* Prior releases are **never deleted** — every shipped build is kept under `releases/`.
* The debug APK is named `Chorect_beta_V<version>.apk` (e.g. `Chorect_beta_V1.2.0.apk`), tracking `versionName`.
* `versionCode = major×10000 + minor×100 + patch` (e.g. 1.2.0 → `10200`).

## 1. Goal

Build a mobile app for practicing fretted string instruments.

The app supports two instruments: a 6-string **Guitar** and a 4-string **Cavaquinho**. The theory engine is instrument-generic, so further instruments (ukulele, mandolin, bass, etc.) can be added without rewriting the core logic.

The app should help the user:

* Visualize the fretboard as a realistic horizontal neck.
* Tap/play any note on the neck, with pinch-to-zoom and drag-pan.
* Search for chords and see possible shapes across the neck.
* Build and audition strummed/arpeggiated selections across strings.
* Change the tuning of each string (presets + custom, on the fly).
* Display chords and scales according to the current tuning.
* Practice by ear (progression, note-over-chord, chord-flavor, inversion, and augmented-vs-diminished trainers, with scored challenges).
* Tune the instrument by ear via a mic-driven tuner.
* Drive practice with a percussion (samba) loop and a chord looper.

The app should prioritize correctness, clarity, and musical usefulness over flashy graphics.

---

## 2. Target Platform

The app is designed for **mobile phones**.

Target:

* Android first (built with native Kotlin + Jetpack Compose; multi-module `theory` / `audio` / `app`)
* iOS later if possible (the `theory` engine is pure Kotlin so it can be reused via KMP)

The UI works in both portrait and landscape (`screenOrientation="fullSensor"`). The neck is always drawn as a **horizontal guitar display** regardless of device orientation — see §5.1.

---

## 3. Scope

The app includes:

1. Horizontal interactive fretboard with pinch-zoom and drag-pan
2. Note playback (per-instrument timbre)
3. Chord search and chord-shape display (CAGED + shell/jazz voicings)
4. Custom tuning per string, plus preset tunings (on-the-fly switching)
5. Scale display (all-notes and per-position views)
6. Theory labels: notes, intervals, chord tones
7. A unified **Strum (pick)** mode: select frets across strings, mute strings, then strum/arpeggiate
8. **Ear training**: five trainers — progression (diatonic + advanced non-diatonic), note-over-chord, chord-flavor, inversions, and augmented-vs-diminished — each with Practice and Challenge modes; a persistent progression-challenge high-score table
9. **Tuner**: real-time mic pitch detection with adjustable A4 reference and on-the-fly tuning changes
10. **Chord looper**: a bar/beat progression sequencer with voice-leading and "build by degree"
11. **Drum machine**: a samba percussion looper with per-track mute/solo
12. Two instruments: Guitar (6-string) and Cavaquinho (4-string)

The app does **not** include (and these remain out of scope):

* Account system
* Cloud sync
* Social features
* Full song library
* Audio recording / export
* Backing tracks (beyond the built-in chord/percussion loopers)

---

## 4. Instrument Model

The app ships two instruments, selectable in Options:

* **Guitar** — 6 strings, chord-shape comfort fret-span 4.
* **Cavaquinho** — 4 strings, chord-shape comfort fret-span 5 (smaller scale length).

Switching instruments resets the tuning to that instrument's default preset (Guitar→Standard, Cavaquinho→DGBe) and persists the choice. The theory engine is generic over string count, so neither 6 nor 4 strings is hard-coded.

### 4.1 Guitar Defaults

The default instrument is a 6-string guitar in standard tuning:

```text
String 6: E2
String 5: A2
String 4: D3
String 3: G3
String 2: B3
String 1: E4
```

The neck displays **14 frets** (fret 0 = open string). Pitch handling spans MIDI E1–C6, so re-tuned/transposed strings stay representable.

The neck renders the bottom three strings (E/A/D) as wound bronze and the top three (G/B/e) as plain steel; open-string letters appear left of the nut as `E A D G B e`.

### 4.2 Custom Tuning

The user should be able to change the tuning of each string manually.

Examples:

```text
Standard: E A D G B E
Drop D:   D A D G B E
DADGAD:   D A D G A D
Open G:   D G D G B D
```

Changing the tuning should immediately affect:

* Note names on the fretboard
* Chord shapes
* Scale shapes
* Interval labels

### 4.3 Tuning Presets

The Options sheet and the Tuner both show only the presets appropriate to the current instrument; switching is immediate (on the fly).

**Guitar presets:**

* Standard (E A D G B E)
* Drop D (D A D G B E)
* DADGAD (D A D G A D)
* Open G (D G D G B D)
* Open D (D A D F# A D)
* Half-step down (Eb Ab Db Gb Bb Eb)
* Whole-step down (D G C F A D)

**Cavaquinho presets:**

* DGBe (D4 G4 B4 E5 — Portuguese/Madeira standard)
* DGBD (D4 G4 B4 D5 — Brazilian, re-entrant top string)

The user can also save a custom tuning (named; saved tunings appear under "My tunings" and can be deleted). Tuning name, selection, and instrument persist across launches.

---

## 5. Fretboard View

### 5.1 Layout

The neck is drawn as a realistic horizontal fretboard (wood texture, nut, fret wires, position inlays, fret-number strip).

Orientation:

* Nut on the left (right in left-handed mode)
* Higher frets toward the right
* Lowest-pitch string at the bottom, highest at the top

**Fixed aspect ratio + letterboxing.** The neck is always rendered at a fixed long-horizontal / short-vertical aspect ratio (derived from fret count × string count), centered and letterboxed within its viewport in **both** portrait and landscape. The viewport's own shape never distorts the neck — a tall portrait viewport simply leaves empty space above and below the short horizontal neck.

**Zoom and pan.** Within that fixed frame the user can pinch-to-zoom (focal-point: the content under the pinch centroid stays fixed) and drag to pan, clamped so the neck can't be dragged off-screen:

* Minimum scale 0.5× — the whole neck shrinks to half the viewport.
* Maximum scale ≈ stringCount/2 — zoom in until roughly two strings fill the height.

The transform is a pure render-layer effect, so tap hit-testing is unaffected by zoom/pan.

### 5.2 Interactivity

The user can tap any fret/string position.

By default a note fires on **tap-release** (so a horizontal swipe pans the neck without sounding anything); an Options toggle switches this to fire on **touch-down**. In Strum (pick) mode, a tap toggles that position's selection instead of playing it.

When a position is tapped (non-pick modes):

* The note is highlighted with a ring.
* The note plays through audio (with the current instrument's timbre, at the current ring-sustain).
* The app shows the string number, fret number, note name, and — when a chord is loaded — its interval relative to the chord root.

Example:

```text
String: 2
Fret: 3
Note: D
Interval: optional, depending on current chord/scale context
```

### 5.3 What the neck shows

Two orthogonal selections drive the neck. **Display mode** (a Chord / Scale / Strum selector in the Fretboard tool) picks what is lit up; **label mode** picks the text on each dot.

#### Label modes

* **Intervals** *(default on first open)* — interval names relative to the root: `1, b2, 2, b3, 3, 4, #4/b5, 5, b6, 6, b7, 7`.
* **Notes** — note names (`C, C#, D, …`).
* **Empty** — dots only, no text.

Root dots are drawn distinctly (crimson with a pearl ring); other chord tones, scale tones, and pick selections each have their own color.

#### Display modes

* **Chord** — highlights the searched chord, either across the whole neck (All notes) or one playable shape at a time (Positions).
* **Scale** — highlights the searched scale, All notes or per-position.
* **Strum (pick)** — see §5.4.

### 5.4 Strum (pick) mode

A single mode for building an arbitrary voicing by hand:

* Tap frets across strings to add/removing them from the selection (shown as amber outline rings).
* Mute individual whole strings — drawn as a red ✕ at the nut, chord-diagram style. Muting a string clears any picks on it; picking a fret un-mutes that string (the two are mutually exclusive). Muted strings are excluded from the strum.
* **Strum** (low → high, at the global strum spread) or **Arpeggio** (wider spread) the selection, or **Clear** it.

---

## 6. Chord Search

### 6.1 Chord Input

The user requests a chord by tapping a **root** chip and a **quality** chip in the Fretboard tool's Chord controls (no free-text parsing required, though the loop's slot editor does accept typed symbols like `Cmaj7`, `Dm7`, `G7`). The quality chips cover at least: major, m, 7, maj7, m7, dim, aug, sus4, sus2, 6, m6, m7b5, dim7, 9, add9, 13.

Examples of supported chords:

```text
C
Cm
C7
Cmaj7
Cm7
C6
Cm6
C9
Cadd9
Csus2
Csus4
Cdim
Cdim7
Cm7b5
Caug
C13
```

The app should support at least these root notes:

```text
C, C#, Db, D, D#, Eb, E, F, F#, Gb, G, G#, Ab, A, A#, Bb, B
```

### 6.2 Chord Shape Generation

For a requested chord, the app shows multiple playable shapes across the neck, generated by a `ChordShapeGenerator` that honors the current tuning, instrument fret-span, and voicing style:

* **Standard** voicings — the five CAGED shapes where canonical templates exist, plus brute-force voicings for qualities without templates (9, 13, …).
* **Shell / jazz** voicings — drop-2 inversions that drop the 5th (and the root for 7+ chords), favoring 2–4 note voicings. Toggled in Options.

Each shape shows:

* Fretted notes
* Muted strings
* Open strings
* Root note
* Chord tones / intervals
* The CAGED/template shape name and its fret span (used as the shape's label)

### 6.3 Chord Shape Constraints

Chord shapes should be practical for human hands.

The app should avoid shapes that are technically correct but unrealistic.

Recommended MVP constraints:

* Maximum fret span: 4 or 5 frets
* Maximum one note per string
* Allow muted strings
* Allow open strings
* Prefer voicings with the root present
* Prefer lower-fret voicings first
* Avoid impossible stretches

### 6.4 Chord Shape Display

When the user selects a chord, the fretboard highlights the chord tones. The Display selector chooses between:

* **All notes** — every occurrence of the chord tones across the 14 frets.
* **Positions** — one playable shape at a time. A position scroller below the neck steps through the shapes with ◀ / ▶ and shows the shape's chord name, its fret range, and the `index / count` (e.g. `Cmaj7 · frets 2–3 · 2 / 5`).

The selected shape is highlighted directly on the full fretboard.

---

## 7. Scale Search

### 7.1 Scale Input

The user should be able to choose a root and scale type.

Minimum MVP scales:

* Major scale
* Natural minor
* Major pentatonic
* Minor pentatonic
* Blues scale
* Dorian
* Mixolydian

Examples:

```text
A minor pentatonic
G major
D mixolydian
E blues
```

### 7.2 Scale Display

The user picks a root chip and a scale chip. The Display selector chooses between:

* **All notes** — every scale tone across the 14 frets.
* **Positions** — one box/position at a time, generated by `ScalePositions`. A scroller steps through positions with ◀ / ▶, showing the position's anchor note and fret range (e.g. `A minor pentatonic · anchor A · frets 5–8 · 1 / 5`).

The neck shows:

* Root notes distinctly
* Other scale tones
* Interval or note labels per the current label mode

The scale controls also print the scale's notes and interval formula for reference.

---

## 8. Audio Playback

### 8.1 Note Playback

When the user taps a note, the app plays that pitch through a native `AudioTrackEngine` (synthesized plucked tone). Each instrument has its own timbre (Cavaquinho is brighter with quicker decay). The pitch matches the selected tuning and fret, and notes ring for the user-set **ring sustain** (§10.7).

The tuner can also play an equal-tempered **reference pitch** for any detected note or open string, computed from the user's A4 reference (§9, Tuner).

### 8.2 Chord / Strum Playback

The user can hear a chord shape or a hand-built strum selection played:

* Struck together, or spread low → high by the global **strum spread** setting (0 ms = struck at once).
* Arpeggiated (a wider spread).

Strum spread and ring sustain are global and shared by single strums, the chord looper, and ear-training playback.

### 8.3 Percussion Playback

A `PercussionSynth` synthesizes samba percussion voices (cached per voice) for the drum machine (§10.8).

---

## 9. Music Theory Engine

The app has an internal `theory` module — pure Kotlin, UI-independent, and unit-tested. It supports:

* Note names and enharmonic equivalents (`NoteSpeller`)
* MIDI note numbers / pitch-class representation
* Intervals
* Chord formulas (`ChordLibrary`) and shape generation (`ChordShapeGenerator`, CAGED + shell), with voice-leading (`VoiceLeading.pickMinMovement`) used by the loop and ear trainers
* Scale formulas (`ScaleLibrary`) and on-neck positions (`ScalePositions`)
* Tuning calculation (`Tunings`, `Tuning`) and fretboard note calculation (`Fretboard`)
* Ear-training models: diatonic degree resolution, random diatonic progressions, a curated library of advanced (non-diatonic) named progressions, note-over-chord and chord-flavor challenges, and chord-inversion voicing (`EarTraining`, `N2cChallenge`, `Inversions`)
* Percussion patterns and voices (`PercussionPattern`, `PercussionVoices`)

Beyond the minimum chord formulas below, the library also defines extensions and altered qualities used by ear training: diatonic extensions (maj9, maj13, maj7#11, m9, m11, 11), the minor-major 7th (mMaj7, the line-cliché chord), and the augmented-family 7ths (7#5, maj7#5) used by the augmented-vs-diminished trainer. The `Inversions` helper voices any quality's chord tones in a chosen inversion (root / 1st / 2nd / 3rd) and reports the inversion count for a quality — it is pure Kotlin and unit-tested like the rest of the engine.

### 9.1 Note Representation

Internally, use pitch classes:

```text
C  = 0
C# = 1
D  = 2
D# = 3
E  = 4
F  = 5
F# = 6
G  = 7
G# = 8
A  = 9
A# = 10
B  = 11
```

Flats should map to the same pitch classes:

```text
Db = C#
Eb = D#
Gb = F#
Ab = G#
Bb = A#
```

### 9.2 Chord Formulas

Minimum chord formulas:

```text
maj:     1 3 5
min:     1 b3 5
dim:     1 b3 b5
aug:     1 3 #5
sus2:    1 2 5
sus4:    1 4 5
7:       1 3 5 b7
maj7:    1 3 5 7
min7:    1 b3 5 b7
m7b5:    1 b3 b5 b7
dim7:    1 b3 b5 bb7
6:       1 3 5 6
m6:      1 b3 5 6
9:       1 3 5 b7 9
add9:    1 3 5 9
13:      1 3 5 b7 9 13
```

### 9.3 Scale Formulas

Minimum scale formulas:

```text
major:             1 2 3 4 5 6 7
natural minor:     1 2 b3 4 5 b6 b7
major pentatonic:  1 2 3 5 6
minor pentatonic:  1 b3 4 5 b7
blues:             1 b3 4 b5 5 b7
dorian:            1 2 b3 4 5 6 b7
mixolydian:        1 2 3 4 5 6 b7
```

---

## 10. Additional Features

These extend the core fretboard tool and are part of the shipped app.

### 10.1 Root Highlighting

When displaying a chord or scale, root notes are drawn distinctly (crimson with a pearl inner ring); other chord/scale tones use softer mode-specific colors.

### 10.2 Left-Handed Mode

The fretboard orientation can be flipped for left-handed players (Options toggle; persisted). The nut moves to the right and everything mirrors.

### 10.3 Note Labels

The label mode (§5.3) toggles dot labels between Notes, Intervals, and Empty (dots only). The default on first open is **Intervals**. The setting persists.

### 10.4 Position View

Rather than a numeric fret-range filter, scales and chords offer a **Positions** view that steps through individual playable shapes/boxes one at a time (§6.4, §7.2), each annotated with its fret range.

### 10.5 Saved Tunings

The user can save and delete named custom tunings locally (persisted via DataStore). The selected tuning and instrument also persist across launches.

### 10.6 Ear Training

A full ear-training tool with **five** sub-modes, each offering **Practice** (free play) and **Challenge** (scored rounds):

* **Progression** — a 4-bar diatonic Roman-numeral progression loops at a chosen BPM; the user identifies the key/mode and each bar's chord. A I–V–I (or i–V–i) cadence can be auditioned to establish the tonic. Options control which modes (major/minor) and chord-type level (triads / sevenths / extended) appear, a fixed or random key, and standard vs shell voicings (or a "mix all" that randomizes per bar/chord). An **Advanced (non-diatonic)** toggle swaps the diatonic generator for a curated library of ~24 named progressions (see below).
* **Note2Chord** — a triad plays, then a test note sounds on top; the user names the test note's relationship to the chord.
* **Flavor** — after a cadence sets the key, one diatonic chord plays; the user identifies its scale degree and quality.
* **Inversions** — a chord of a chosen quality (maj / min / 7th / extended / sus / dim / aug, or a user-selected mix from a quality palette) plays in a random inversion; the user identifies whether it is root position or 1st / 2nd / 3rd inversion. Tap-to-compare auditioning plays each candidate inversion so the user can A/B them by ear. The number of available inversions follows the quality (3 for triads, 4 for 7th chords).
* **Aug/Dim** — augmented-vs-diminished discrimination. The base palette is the augmented and diminished triads, with optional 7th / extended forms (dim7, m7♭5, 7♯5, maj7♯5); the user picks which qualities are in the pool, hears a random one, and identifies it (tap-to-compare auditioning of each candidate quality at the current root).

#### Advanced (non-diatonic) progressions

The Progression sub-mode's **Advanced** toggle replaces the diatonic generator with a curated library of ~24 named progressions covering borrowed/modal-interchange chords, secondary dominants, chromatic passing chords, and jazz turnarounds (e.g. *Mixolydian Rocker*, *Andalusian Cadence*, *Tritone Substitution*, *Royal Road*, *Bird Blues Turnaround*). Each progression is modeled **relative to the tonic** (a list of semitone-offset + quality + Roman-label chords) so it transposes to any key, is of variable length, and carries a short **teaching explanation** shown during quizzing. The reveal shows the progression's name, its Roman-numeral line, and the concrete chords in the drawn key. Advanced practice loops the progression in the shared looper; the Advanced **Challenge** is **self-marked** (the chromatic chords make multiple-choice impractical), with the user marking each round right/wrong after revealing.

**Progression Challenge** specifics:

* 15 questions per session.
* A dedicated "Hear the degrees" reference palette lets the user audition the diatonic degrees in the (hidden) key; the answer chips themselves are silent.
* In fixed-Sevenths mode the user gives one combined diatonic-7th answer per bar (e.g. "V7") rather than picking degree and extension separately. Triad and mix modes keep separate pickers.
* Advancing without answering every bar credits the unanswered bars as correct.
* A persistent **high-score table** keeps the top results, each with its date and completion time, ranked by score then by fastest time (time breaks ties).

The diatonic **Extended** level and the "mix all" pool include **6th** and **add9** chords alongside the 9th/11th extensions already present, and the same 6/add9 (and 9th/11th) flavors appear in the Flavor trainer's quality palette.

Ear-training state is app-lifetime, so the user can leave to check themselves on the fretboard and return to the same progression, reveals, and counters.

### 10.7 Global Audio Settings

Available app-wide (Options, and a quick-access button on the tool screens):

* **Strum spread** — ms between consecutive chord notes (0 = struck at once), shared by single strums, the loop, and ear training.
* **Ring sustain** — how long tapped/strummed notes ring.
* **A4 reference** — 435–445 Hz, used by the tuner and reference tones.
* **Play on touch-down** vs tap-release (§5.2).
* **Jazz / shell voicings** toggle.

### 10.8 Drum Machine

A samba percussion looper (drum-machine tab): an editable 16-slot pattern with per-track **mute** and **solo**, per-instrument **voice auditioning** (tap a row label or cell to preview), and an adjustable BPM. The pattern persists across leaving and returning to the screen.

Layout: the screen is a **vertically scrollable page**, and the track grid uses **fixed-height rows** (one per percussion instrument) rather than weight-distributed rows, so each track's name, voice picker, and Mute/Solo toggles are always fully visible — nothing is clipped in short (landscape) viewports. The loop grid itself supports **2-finger pinch-zoom and pan** (a pure render-layer transform, so per-cell tap hit-testing is unaffected); single-finger gestures scroll the page and tap/edit cells.

### 10.9 Chord Looper

A bar/beat progression sequencer ("Loop"):

* Set tempo, bar count (1–16), and slots-per-bar (1 = whole, 2 = half, 4 = quarter).
* Edit each slot's chord (typed symbol or via a **Build by degree** panel using key/mode/diatonic-level + optional quality override), its voicing, and its strum pattern (down / up / arpeggio / sustain).
* Voicings auto-normalize for smooth voice-leading (first chord prefers the E-shape; each subsequent chord minimizes finger movement).
* While playing, the main fretboard mirrors the sounding chord live, and a Stop control is surfaced in the status bar.
* An ear-training progression can be loaded straight into the looper.

### 10.10 Tuner

A mic-driven chromatic tuner:

* Detects pitch in real time and shows it on an enlarged quarter-ring dial (±50 ¢) with a pivoting needle and a large note label; turns green when in tune.
* The A4 reference is adjustable (§10.7).
* Tuning can be changed on the fly here (presets + custom) without opening Options.
* Tapping the note label or an open-string reference button plays the equal-tempered reference tone and locks the dial to "spot on" while it rings.
* Requires the `RECORD_AUDIO` runtime permission, requested on entry; an explainer panel is shown until it is granted.

---

## 11. Data Model

### 11.1 Instrument

The instrument is modeled as an enum carrying its string count and chord-span comfort; the active tuning is stored separately. The neck displays 14 frets.

```json
{
  "name": "Guitar",
  "strings": 6,
  "maxFretSpan": 4,
  "displayFrets": 14,
  "tuning": ["E2", "A2", "D3", "G3", "B3", "E4"]
}
```

```json
{
  "name": "Cavaquinho",
  "strings": 4,
  "maxFretSpan": 5,
  "displayFrets": 14,
  "tuning": ["D4", "G4", "B4", "E5"]
}
```

### 11.2 Fret Position

```json
{
  "string": 6,
  "fret": 3,
  "note": "G",
  "midi": 43,
  "pitchClass": 7
}
```

### 11.3 Chord Shape

```json
{
  "name": "Cmaj7",
  "frets": [-1, 3, 2, 0, 0, 0],
  "notes": [null, "C", "E", "G", "B", "E"],
  "intervals": [null, "1", "3", "5", "7", "3"],
  "position": 0
}
```

Use `-1` for muted strings.

---

## 12. Architecture Requirements

The app separates musical logic from the UI via three Gradle modules. The `theory` module is pure Kotlin with no Android or Compose dependencies, so it is unit-testable in isolation and reusable on other platforms (KMP).

```text
:theory   (pure Kotlin)
  Note / Interval / PitchClass / Midi / NoteSpeller
  ChordLibrary / ChordQuality / ChordShapeGenerator / CagedShape / VoiceLeading
  ScaleLibrary / Scale / ScalePositions
  Tuning / Tunings / TuningCodec
  Instrument / Fretboard / FretPosition
  EarTraining / N2cChallenge / Inversions   (ear-training models)
  PercussionPattern / PercussionVoices / PercussionInstrument

:audio    (Android audio engine)
  AudioEngine / AudioTrackEngine     (note, chord/strum, frequency, sample playback)
  Timbre / PercussionSynth

:app      (Jetpack Compose UI)
  MainActivity / App                 (nav rail + content area + bottom sheets)
  AppState                           (shared app-lifetime state)
  FretboardView                      (Canvas neck: fixed aspect, zoom/pan)
  Screens (Fretboard/Options sheets, Loop) / EarTrainingScreen / TunerScreen / SambaLooperScreen
  EarTrainingState / TunerState / SambaLooperState
  TuningRepository                   (DataStore persistence: tunings, settings, high scores)
```

The theory engine is testable independently from the UI.

---

## 13. Testing Requirements

Add tests for the theory logic.

Minimum tests:

1. Standard tuning generates correct open-string notes.
2. 6th string, 3rd fret in standard tuning is G.
3. 5th string, 3rd fret in standard tuning is C.
4. C major chord returns C, E, G.
5. A minor pentatonic returns A, C, D, E, G.
6. Drop D tuning changes 6th string open note to D.
7. Chord shapes do not exceed the maximum fret span.
8. Scale display updates correctly after tuning changes.

---

## 14. Acceptance Criteria

The app satisfies its goals when the user can:

1. Open the app and see a horizontal fretboard (in either orientation), and pinch-zoom / drag-pan it.
2. Tap any fret/string and hear the correct note.
3. Switch instrument (Guitar / Cavaquinho) and change the tuning of any string (preset or custom).
4. Pick a chord such as `Cmaj7` from the root/quality chips.
5. See multiple playable Cmaj7 shapes across the neck (standard or shell voicings), stepping through positions.
6. Pick a scale such as `A minor pentatonic`.
7. See the scale displayed correctly on the current tuning.
8. Switch between Notes / Intervals / Empty dot labels (default: Intervals).
9. Select frets, mute strings, and strum/arpeggiate a hand-built voicing.
10. Save at least one custom tuning locally.
11. Run an ear-training challenge and see a persistent high score.
12. Tune by ear with the mic tuner and play reference tones.
13. Build and play a chord loop and a percussion loop.

---

## 15. Cavaquinho Support (implemented)

Cavaquinho support is built. It demonstrates the generic instrument model: the theory engine never assumes 6 strings.

Implemented:

* 4-string instrument layout (no wound-bronze strings — all rendered plain).
* Cavaquinho preset tunings: DGBe (Portuguese) and DGBD (Brazilian, re-entrant).
* Wider chord-span comfort (fret span 5) than guitar (4).
* A brighter, quicker-decay timbre.

Further instruments can be added the same way: extend the `Instrument` enum and add presets to `Tunings`.

---

## 16. Implementation Priority

Phases 1–6 (the original MVP) are complete; phase 7 captures the tools built on top.

### Phase 1: Core Theory Engine

* Notes
* Intervals
* Tunings
* Fretboard note calculation
* Chord formulas
* Scale formulas

### Phase 2: Fretboard UI

* Horizontal fretboard
* Tap notes
* Highlight notes
* Display note names

### Phase 3: Tuning Editor

* Change string tuning
* Tuning presets
* Save custom tuning

### Phase 4: Chords

* Chord search (root/quality chips)
* Chord tones
* Chord shapes (CAGED + shell voicings)
* Position scroller

### Phase 5: Scales

* Scale search
* Scale highlighting
* Position view

### Phase 6: Audio

* Tap-to-play note
* Play chord shape
* Arpeggio / strum spread

### Phase 7: Practice Tools (built on the MVP)

* Strum (pick) mode with per-string muting
* Ear training (progression incl. advanced non-diatonic / note2chord / flavor / inversions / aug-dim; practice + challenge; high scores)
* Mic tuner with on-the-fly tuning and reference tones
* Chord looper (build-by-degree, voice-leading, strum patterns)
* Samba drum machine (mute/solo, voice auditioning)
* Second instrument (Cavaquinho)
* Pinch-zoom / drag-pan neck; fixed-aspect letterboxed rendering

---

## 17. Development Process

The original plan (tech stack, architecture, data model, theory-engine API, UI hierarchy, milestones) was proposed and confirmed before implementation, then built in small, independently testable steps with the theory engine unit-tested in isolation. New work continues to follow this process: confirm the approach, then implement incrementally.

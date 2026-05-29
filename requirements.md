# Guitar Practice App — MVP Requirements

## 1. Goal

Build a mobile app for practicing fretted string instruments.

The MVP should support **guitar only**. Later versions may support cavaquinho, ukulele, mandolin, bass, and other string instruments.

The app should help the user:

* Visualize the guitar fretboard horizontally.
* Tap/play any note on the neck.
* Search for chords and see possible shapes across the neck.
* Change the tuning of each string.
* Display chords and scales according to the current tuning.

The app should prioritize correctness, clarity, and musical usefulness over flashy graphics.

---

## 2. Target Platform

The app should be designed first for **mobile phones**.

Initial target:

* Android first
* iOS later if possible

The UI should work in both portrait and landscape, but the main fretboard view should be optimized for **horizontal guitar display**.

---

## 3. MVP Scope

The MVP should include:

1. Horizontal interactive guitar fretboard
2. Note playback
3. Chord search and chord-shape display
4. Custom tuning per string
5. Scale display
6. Basic theory labels: notes, intervals, chord tones
7. Preset tunings

The MVP should **not** initially include:

* Account system
* Cloud sync
* Social features
* Full song library
* Audio recording
* Real-time pitch detection
* Advanced ear training
* Backing tracks

These can be added later.

---

## 4. Instrument Model

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

The guitar should support at least frets 0–24.

Fret 0 means open string.

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

The app should include common tuning presets:

* Standard tuning
* Drop D
* DADGAD
* Open G
* Open D
* Half-step down
* Whole-step down

The user should also be able to save a custom tuning.

---

## 5. Fretboard View

### 5.1 Layout

The app should display the guitar neck horizontally.

Preferred orientation:

* Nut on the left
* Higher frets toward the right
* Low E string at the bottom
* High E string at the top

The user should be able to scroll or zoom if the whole neck does not fit on the screen.

### 5.2 Interactivity

The user should be able to tap any fret/string position.

When a position is tapped:

* The note should be highlighted.
* The note name should be shown.
* The note should play through audio.
* The app should show the string number and fret number.

Example:

```text
String: 2
Fret: 3
Note: D
Interval: optional, depending on current chord/scale context
```

### 5.3 Display Modes

The fretboard should support several display modes:

#### Note Name Mode

Shows note names:

```text
C, C#, D, D#, E, F, F#, G, G#, A, A#, B
```

The app should eventually support flats too:

```text
Db, Eb, Gb, Ab, Bb
```

#### Interval Mode

Shows interval names relative to a selected root:

```text
1, b2, 2, b3, 3, 4, #4/b5, 5, b6, 6, b7, 7
```

#### Empty Mode

Shows a clean fretboard with only highlighted chord or scale tones.

---

## 6. Chord Search

### 6.1 Chord Input

The user should be able to request any common chord.

Examples:

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

For a requested chord, the app should show multiple possible shapes across the neck.

Example request:

```text
Chord: Gmaj7
```

Expected output:

* Shape near open position
* Shape around 3rd fret
* Shape around 7th fret
* Shape around 10th/12th fret if available

Each shape should show:

* Fretted notes
* Muted strings
* Open strings
* Root note
* Chord tones / intervals
* Suggested fingering if possible, optional for MVP

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

When the user selects a chord, the fretboard should highlight the chord tones.

The app should also list chord-shape cards.

Each card should show:

```text
Chord: Cmaj7
Position: 3rd fret
Frets: x 3 2 0 0 0
Notes: x C E G B E
Intervals: x 1 3 5 7 3
```

The user should be able to tap a shape and see it highlighted on the full fretboard.

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

The app should highlight all notes from the selected scale on the fretboard.

The user should be able to choose a position or fret range.

Example:

```text
Root: A
Scale: Minor pentatonic
Fret range: 5–8
```

The app should show:

* Root notes distinctly
* Other scale tones
* Optional interval labels

---

## 8. Audio Playback

### 8.1 Note Playback

When the user taps a note, the app should play that pitch.

For MVP, synthetic audio is acceptable.

Possible implementations:

* Built-in sampler
* Simple synthesized sine/plucked sound
* WebAudio-style oscillator if using web technology
* Native mobile audio engine if using native app framework

The pitch must match the selected tuning and fret.

### 8.2 Chord Playback

Nice-to-have for MVP:

The user can tap a chord shape and hear all notes played:

* Simultaneously as a chord
* Or arpeggiated from low string to high string

---

## 9. Music Theory Engine

The app needs an internal theory engine.

It should support:

* Note names
* Enharmonic equivalents
* MIDI note numbers or equivalent pitch representation
* Intervals
* Chord formulas
* Scale formulas
* Tuning calculation
* Fretboard note calculation

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

## 10. Suggested Extra MVP Features

These are recommended because they make the app more useful without making the MVP too large.

### 10.1 Root Highlighting

When displaying a chord or scale, root notes should be visually different from other notes.

Example:

* Root = strong highlight
* Other chord/scale tones = softer highlight

### 10.2 Left-Handed Mode

Allow the fretboard orientation to be flipped for left-handed players.

### 10.3 Show/Hide Note Labels

User can toggle between:

* Show note names
* Show intervals
* Show both
* Show nothing except dots

### 10.4 Fret Range Filter

For scales and chords, allow the user to limit results to a fret range.

Examples:

```text
Frets 0–5
Frets 5–9
Frets 7–12
```

### 10.5 Favorite Chords and Tunings

User can save:

* Favorite chord shapes
* Favorite custom tunings

This can be local-only for MVP.

### 10.6 Practice Prompt Mode

A simple practice mode can randomly ask:

```text
Find all C notes
Play an A minor chord
Find G major pentatonic in 5th position
Find all b7 intervals over D7
```

This should be optional for MVP, but the app architecture should allow it later.

---

## 11. Data Model

### 11.1 Instrument

```json
{
  "name": "Guitar",
  "strings": 6,
  "frets": 24,
  "tuning": ["E2", "A2", "D3", "G3", "B3", "E4"]
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

The app should separate musical logic from the UI.

Recommended structure:

```text
src/
  theory/
    notes
    intervals
    chords
    scales
    tuning
    fretboard
    chordShapeGenerator

  components/
    Fretboard
    String
    Fret
    ChordSearch
    ScaleSearch
    TuningEditor
    ShapeCard

  screens/
    FretboardScreen
    ChordScreen
    ScaleScreen
    SettingsScreen

  audio/
    notePlayback
    chordPlayback

  storage/
    savedTunings
    favorites
```

The theory engine should be testable independently from the UI.

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

The MVP is successful when the user can:

1. Open the app and see a horizontal guitar fretboard.
2. Tap any fret/string and hear the correct note.
3. Change the tuning of any string.
4. Search for a chord such as `Cmaj7`.
5. See multiple playable Cmaj7 shapes across the neck.
6. Search for a scale such as `A minor pentatonic`.
7. See the scale displayed correctly on the current tuning.
8. Switch between note-name and interval display.
9. Save at least one custom tuning locally.

---

## 15. Future Expansion: Cavaquinho

The app should be designed so that cavaquinho support can be added later without rewriting the core logic.

Cavaquinho support will require:

* 4-string instrument layout
* Common cavaquinho tunings
* Different default string names
* Chord shapes adapted to 4 strings
* Possibly smaller fretboard range
* Brazilian chord vocabulary if desired

The theory engine should therefore not assume 6 strings permanently.

Instead, it should support a generic fretted instrument model:

```json
{
  "name": "Custom Instrument",
  "strings": 4,
  "frets": 19,
  "tuning": ["D4", "G4", "B4", "D5"]
}
```

---

## 16. Implementation Priority

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

* Chord search
* Chord tones
* Chord shapes
* Shape cards

### Phase 5: Scales

* Scale search
* Scale highlighting
* Fret range filtering

### Phase 6: Audio

* Tap-to-play note
* Play chord shape
* Optional arpeggio mode

---

## 17. Development Instruction for Claude

Before implementation, Claude should first produce:

1. A proposed technical stack
2. A simple architecture plan
3. The data model
4. The theory engine API
5. The UI component hierarchy
6. A list of implementation milestones

Claude should not start coding the full app immediately.

Claude should first confirm the plan and then implement the MVP in small, testable steps.

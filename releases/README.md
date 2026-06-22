# Releases

Archived release builds of **Chorect**. Previous versions are kept here and never
deleted, so there is a traceable release history.

Versioning is **major.minor.patch**:
- **minor** bump → new feature implementation (e.g. 1.2.0 → 1.3.0)
- **patch** bump → bug fix of an existing feature (e.g. 1.2.0 → 1.2.1)
- **major** bump → breaking redesign

The build emits the versioned name automatically (`Chorect_beta_V<version>.apk`)
from `versionName` in `app/build.gradle.kts`; copy each new build here.

| Version | Notes |
|---------|-------|
| 1.6.0   | Drum machine: configurable **meter** (bars / time signature / division; default 2 bars of 2/4 in 1/16) and a loop **translate/shift** (±n slots, wrap-around). Ear training: the "Hear the degrees" reference and advanced-progression play buttons now show **plain numbers** (no Roman numerals — they no longer give away major/minor); challenge gets **← Prev / Next →** at the top so a mis-tapped "Next" is recoverable; and the chord-progression challenge is answered with a new **degree-keyboard** tool — tap a bar's square, pick its chord from a degree keyboard with a Major/Minor **shift** (relative major/minor answers are accepted as equivalent) plus an extensions row when the level uses them. |
| 1.5.0   | UI polish: Ear-training Practice/Challenge and sub-mode are now compact dropdowns (frees scroll space) with a mid-challenge **Restart**; drum machine gains **per-instrument volume** sliders (in each voice popup); the fretboard now **starts empty** and, in portrait, **opens zoomed to the first frets** with pinch/drag working over the whole neck area (not just the thin neck). |
| 1.4.0   | Drum machine: Brazilian 16th-note swing slider, an Erase tool (tap-to-clear), 2-finger aspect-ratio (independent X/Y) zoom + drag-pan on the grid, and tighter on-the-beat sample onsets. |
| 1.3.1   | Drum machine now plays bundled recorded one-shot samples (real Latin-percussion hits) instead of pure synthesis, with synth fallback. Samples are placeholders pending licensing. |
| 1.3.0   | Drum machine: redesigned/expanded voices (Surdo 3, Tamborim 3, Pandeiro 5, Agogô 2) + save/load custom beats ("stock samba" is the built-in default). |
| 1.2.0   | Ear training: Inversions & Aug/Dim trainers, advanced (non-diatonic) progression library with explanations, 6th/add9 extensions. Drum machine: scrollable page + 2-finger pinch-zoom, always-visible per-track mute/solo. |

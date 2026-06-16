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
| 1.3.0   | Drum machine: redesigned/expanded voices (Surdo 3, Tamborim 3, Pandeiro 5, Agogô 2) + save/load custom beats ("stock samba" is the built-in default). |
| 1.2.0   | Ear training: Inversions & Aug/Dim trainers, advanced (non-diatonic) progression library with explanations, 6th/add9 extensions. Drum machine: scrollable page + 2-finger pinch-zoom, always-visible per-track mute/solo. |

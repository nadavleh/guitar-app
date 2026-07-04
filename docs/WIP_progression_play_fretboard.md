# WIP Handoff — Progression Library: Play + Fretboard (Phase 2)

**Status:** IN PROGRESS. Phase 1 (clickable progression → song examples) shipped as **v1.19.0**.
Phase 2 adds playback + fretboard to the progression-library dialog. Building on BOTH
Android and chorect-web. Target version bump: **v1.20.0** (new feature = minor bump).

## Locked decisions (from the user)
- **Play chord button per progression** — plays the progression's chords via the looper's audio engine.
- **Fixed key:** C major / A minor (by mode). **Loop until stopped.**
- **Fretboard toggle:** a *single* fretboard that *follows playback* (highlights the currently-playing chord, steps through as it plays).
- **Single-open accordion:** opening one progression row closes the previously-open one.
- **Fix scroll-jump bug** (web): the toggle's `rerender()` rebuilds the DOM and resets the scroll container's `scrollTop` to 0. Capture/restore `scrollTop` across rerender.
- **No approval gate** — implement directly on both platforms, build, ship.

## Done so far
- **theory (Kotlin)** `EarTraining.kt`: added `CircleWindow(id, romanLine)` data class + `CIRCLE_WINDOWS` constant (7 windows). **STILL NEEDS:** add `chords: List<AdvChord>` so circle windows are resolvable/playable.
- **theory (Kotlin)** `ProgressionSongs.kt` + test — DONE (4 tests pass).
- **web** `eartraining.ts`: mirrored `CircleWindow` + `CIRCLE_WINDOWS`. `progressionSongs.ts` + `index.ts` export — DONE.
- **Android** `EarTrainingScreen.kt`: `LibrarySection` / `LibraryRow` with ▸/▾ chevrons — DONE but **multi-open** (needs single-open) and dialog takes **no AppState** (needs threading).
- **web** `earTrainingUI.ts`: `libraryOverlay()` rewritten with clickable rows, `libExpanded` Set — DONE but multi-open + rerender scroll-jump unfixed.

## Remaining work
### theory (both)
- Add `chords: List<AdvChord>` / `chords: AdvChord[]` to `CircleWindow`; update `CIRCLE_WINDOWS` construction; add a resolve-to-key path (e.g. `CircleWindow.resolve(key)` or wrap as `NamedProgression`).

### Android — `EarTrainingState.kt`
- Add an **independent library preview player** (do NOT reuse quiz `startLoop`/`playChordOnce`/`progResolved`/`currentBar` — those mutate quiz state).
  - New fields: e.g. `libPlayKey: String?`, `libBar: Int`, `libShape` (current shape for fretboard), `libLoopJob`.
  - New methods: `libraryPlay(chords, mode)` / `libraryStop()`.
  - Reuse the voice-leading pattern from `playChordOnce` (lines ~311-340): parse symbol → `ChordShapeGenerator(style=earStyle()).shapesFor(...)` → first=E-shape else `VoiceLeading.pickMinMovement` → `shape.notes.mapNotNull{it?.midi?.value}` → `audio.playChord(midis, ..., timbre=Timbre.Clarity)`.
  - **Block fallback** for exotic chords with no guitar voicing: `52 + root.value` + `q.intervals` (see lines 319-324).
  - Fixed key C major / A minor by mode. Loop until stopped.

### Android — `EarTrainingScreen.kt`
- Thread `state: AppState` into `ProgressionLibraryDialog` / `LibraryRow`.
- Convert to **single-open accordion** (`var expandedKey by remember { mutableStateOf<String?>(null) }`).
- On expand: stop any playing preview.
- Add ▶Play/⏹Stop button + fretboard toggle + `FretboardView` driven by `libShape` in the expanded row.
- Keep scroll put (`rememberScrollState` won't reset to 0; add `bringIntoView` on the clicked header if reflow shifts it).
- Reuse: `ChordOnFretboard(state, symbol, show, onToggle)` at EarTrainingScreen.kt:378-425; `FretboardView` at FretboardView.kt:87; `shapeMarks` at Common.kt:152.

### web — `earTrainingUI.ts` / `earTrainingState.ts` (Explore findings, exact APIs)
- **Resolve rows:** `resolveProgression(p, key, level, rng)` (diatonic, eartraining.ts L121); `resolveNamed(np, key)` (advanced, L166). There is **no** `NamedProgression.resolve` — it's a plain interface + free function. `ResolvedChord = {symbol, romanLabel, root}`.
- **Circle rows:** `CIRCLE_WINDOWS` has no chords — rebuild the 4-chord window from exported `CIRCLE_OF_FIFTHS: AdvChord[]` (eartraining.ts L235) by window index, wrap as `{...tonicMode:Major, chords:window}`, call `resolveNamed`. `randomCircleOfFifths(rng)` L242 shows the window-building.
- **Play:** `deps.audio.playChord(midis, strum, sustain, Timbres.Clarity)` (engine.ts L80). `midis` from `parseChord(symbol)` + `ChordShapeGenerator().shapesFor(root,q,tuning,DISPLAY_FRETS).notes.filter(...).map(n=>n!.midi)` (voiced), or block fallback `52+root` + `q.intervals`. `audio.stop()` (L115) kills ringing notes.
- **Independent loop:** DO NOT call `startLoop`/`playChordOnce` (earTrainingState.ts L227/L258 — they mutate `progResolved`/`currentBar`/`prevPlayedShape`/`currentPlayingShape`/`lastShownShape`). Write a NEW token-guarded async loop over the passed-in `ResolvedChord[]`: `barMs=(60000/max(bpm,10))*4`, `playChord`, `await sleep(barMs)`, repeat while own token valid. Stop = bump token + `audio.stop()`.
- **Fretboard:** reuse the `chordFretboardPanel(parent, symbol, show, onToggle)` pattern (earTrainingUI.ts L331) — or `shapeMarks(shape, labelMode)` (marks.ts L75) → `fb.setData(FretboardData)` (fretboardCanvas.ts L61). `FretboardData` = `{tuning, marks:Map<string,FretMark>, selectedPosition, leftHanded, numFrets, playOnTouchDown, mutedStrings, onTap}`. Toggle field like existing `showFretboard` (state L80). CAVEAT: `this.fbCanvasEl`/`this.fb` is a SINGLE shared instance — if the library needs its own persistent fretboard alongside the main one, make a 2nd canvas/instance or last `setData` wins.
- **Scroll fix:** `rerender()` (L267) fires `onChange` → `ui.ts renderInner()` (L200) does `clear(contentEl)` then rebuilds the whole subtree, INCLUDING re-creating the library overlay (L197) — so the `et-card` (`overflow:auto`, libraryOverlay L238) loses `scrollTop`. Fix: capture the card's `scrollTop` into an `EarTrainingUI` instance field before `rerender()`, restore after append (mirror the `.et-scroll` preservation in ui.ts L212-224; rAF-restore as fallback). The `fbCanvasEl`/`fb` instance fields survive rerender (re-appended, not recreated).

### Ship
- Bump `app/build.gradle.kts` → versionCode 12000, versionName "1.20.0"; `chorect-web/package.json` → "1.20.0".
- Archive current APK into `releases/` before assembleDebug (build enforces only-newest in debug folder).
- `.\gradlew.bat :app:assembleDebug` + theory tests; commit; push; `gh workflow run "Deploy web to GitHub Pages" --ref main` then `gh run watch <id>`.

## Key gotchas
- Windows: use `.\gradlew.bat` (no `./gradlew`).
- Web deploy is **workflow_dispatch only** — push does NOT auto-trigger.
- Debug APK folder must hold only the newest APK (Gradle `doLast` deletes stale ones) — archive to `releases/` first.

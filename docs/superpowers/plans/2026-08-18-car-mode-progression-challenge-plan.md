# Car mode — hands-free progression ear training

**Target version: 2.71.0 / versionCode 27100** (minor = new feature; current 2.70.0 / 27000)
Scope: Ear Training → Progressions sub-mode only. Designed so a second sub-mode can adopt it later; only progressions are built.

Everything below was verified by reading: `CLAUDE.md`, `GUI_DESIGN.md`, `docs/ear-training-conversation-digest.md`, `chorect-web/src/app/earTrainingState.ts`, `chorect-web/src/app/earTrainingUI.ts`, `app/src/main/kotlin/app/guitar/app/EarTrainingState.kt`, `app/src/main/kotlin/app/guitar/app/EarTrainingScreen.kt`, `chorect-web/src/theory/eartraining.ts`, `theory/src/main/kotlin/app/guitar/theory/EarTraining.kt`, `chorect-web/src/audio/engine.ts`, `audio/src/main/kotlin/app/guitar/audio/AudioEngine.kt`, `chorect-web/src/app/woodClick.ts`, `app/src/main/kotlin/app/guitar/app/MetronomeState.kt`, `Shell.kt`, `Transport.kt`, `chorect-web/src/app/ui.ts`, `chorect-web/src/app/icons.ts`, `chorect-web/src/style.css`, `chorect-web/test/verify.ts`, `.github/workflows/deploy-web.yml`, `app/build.gradle.kts`.

---

## 0. Decisions & open questions — recommendations (all vetoable)

| # | Question | Recommendation | Why |
|---|---|---|---|
| **D1** | Third `EarMode` or a toggle on top of `EarMode.Challenge`? | **Third enum value `EarMode.Car`** (`earTrainingState.ts:33`, `EarTrainingState.kt:2197`). | `EarMode` already *is* the "how am I working this sub-mode" axis (Practice = free reveal, Challenge = graded). A parallel boolean would force `&& !carMode` into every one of the ~10 `earMode == Challenge` sites — easy to forget one. **Caveat you must budget for:** existing code is written `if (earMode == Challenge) ChallengeView else PracticeView`, so a new enum value silently falls into the Practice branch. Phase 2 converts every such site to an explicit 3-way branch — the audit list is in §4. |
| **D2** | Where does the Car-mode button live, and what does it look like? | **On the Progression Challenge *config* screen**, as a secondary full-width button directly under `Start challenge ▶` (`earTrainingUI.ts:1022`, `EarTrainingScreen.kt:1472`): `[car icon] Car mode — hands-free`. **Plus** a duplicate row inside the in-flight `ChallengeModeFold` "More tools" dropdown (`earTrainingUI.ts:1104`, `EarTrainingScreen.kt:290`). | (a) That config screen is literally where you sit *before* driving — the generator summary card and "Draw questions from" are the two rows above it, and car mode honours both, so the setup context is already correct. (b) It is **unreachable during answering** without deliberately opening the fold → cannot be mis-tapped while jabbing at Roman numerals. (c) **Zero permanent chrome**: no new tab (`Shell.kt` `TabDest`), no new dock button, no third segment in the Practice/Challenge control — so `GUI_DESIGN.md` §3.2 (tab bar portrait / tab rail landscape) and the fixed control dock are untouched in both orientations. (d) One-line mirror per platform. **Rejected alternatives:** a 3rd segment in the Practice/Challenge segmented control (one accidental tap away mid-challenge, and 3 segments crowd portrait); a button in the transport dock (highest-traffic surface in the app); a new tab (car mode is a *mode*, not a tool, and the 4-tab budget is user-owned via `AppState.tabOrder`). **No emoji** — `icons.ts` header states "no emoji icons anywhere", so this is a new SVG `car` icon web-side and `Icons.Outlined.DirectionsCar` on Android (`material.icons.extended` is already a dependency, `app/build.gradle.kts:80`). |
| **D3** | Do the generator flags still apply? | **Yes, unchanged.** | Car mode draws through the existing `nextProgression()` / `nextAdvancedProgression()`, which already read `progFocus` (`iiiFocusMode`/`third6FocusMode`), `advancedMode`+`advCategory`, `circleMode`, `fixedKey`, `includeMajor/Minor`, `chordTypeLevel`, `earMixAll`, `earHarmonicMinor`. The car screen shows a **read-only** one-line generator summary (not the tappable `generatorSummaryCard` — no sheets while driving). |
| **D4** | "Replay 5 more times": continue or restart the reveal cycle? | **Restart from round 1** on the *same* progression (beeps again, reveal ramp 0→1→2→3→4). | The entire didactic content is the graded-reveal ramp; continuing at full reveal is a different exercise (play-along), and you'd get 5 passes of an already-spoiled answer. If you want the play-along, it should be its own button later ("Play along ∞"), not an overload of Replay. |
| **D5** | Advance to the next exercise: automatic or big Next tap? | **Auto-advance ON by default**, after a **4 s silent self-assessment gap**; plus a big `Next →` to skip early and a big `Stop`. A `switchRow`/`Switch` "Auto-advance" toggle lets you turn it off (session-only, not persisted). | The three-beep announcement *only makes sense in an auto-advancing chain* — if every exercise needed a tap, you'd already know a new one had begun. Hands-free is the whole premise. The 4 s gap is what makes it usable: silence to say "iii, not vi" to yourself before the next lead-in. |
| **D6** | Beep spec, no new asset. | **880 Hz sine (A5), 140 ms, 5 ms linear attack, exponential decay (`exp(-6i/n)`), peak 0.55**, played at gain 0.9. Onsets at **t=0, 500, 1000 ms**; first chord at **t=1500 ms** (one more 500 ms gap → a clean "3-2-1-go"). Three identical beeps. | 880 Hz sits *above* the ear-training chord register (voicings are MIDI 45–70 ≈ 110–490 Hz) and above the <500 Hz road-noise hump, so it cuts through a car cabin without being shrill; car/phone speakers reproduce it cleanly. The 5 ms attack kills the click that a raw sine onset produces. Generated in code exactly like the existing `synthClick` (`woodClick.ts:6`, `MetronomeState.kt:91`) — **no asset shipped**. *Vetoable variant:* make beep 3 higher (e.g. 1174 Hz) as a "here it comes" marker. |
| **D7** | Screen wake + rotation + backgrounding. | **Android:** `LocalView.current.keepScreenOn = true` in a `DisposableEffect` inside the car view (no manifest permission — `WAKE_LOCK` is only for `PowerManager`; the manifest stays as-is). **Web:** `navigator.wakeLock.request("screen")` on the Start gesture, re-acquired on `visibilitychange`→visible, released on exit. **Rotation:** must *not* cancel — see D8. **Backgrounding:** Android keeps playing (matches the rest of the app — no lifecycle audio-stop exists today); **web stops the exercise when `document.hidden`** because background tabs clamp `setTimeout` to ≥1 s, which would smear the bar grid. | Screen Wake Lock is HTTPS-only — the site is on GitHub Pages (https), fine. iOS Safari <16.4 and some browsers lack it: the request is wrapped in try/catch and car mode still works, just without wake-lock. TS 5.6's `lib.dom` may not declare `wakeLock`, so use the codebase's existing cast idiom (`engine.ts:459` does `ctx as AudioContext & { outputLatency?: number }`). |
| **D8** | Rotation / navigating away. | Rotation **does not** cancel. Navigating away **does**. | Android `MainActivity` declares `configChanges="orientation|screenSize|..."` so the Activity isn't recreated; `AppState.scope` (`MainActivity.kt:141` `rememberCoroutineScope()`, passed at `:165`) survives, and the car driver job lives on it — not on the screen. The existing guard at `EarTrainingScreen.kt:92-94` (`onDispose { if (state.currentSheet != Sheet.EarTraining) { ear.stopLoop(); ... } }`) is copied exactly: since `stopLoop()` will also cancel the car driver (§4), navigating away kills it for free and rotation doesn't. `keepScreenOn` is re-applied by the recreated composable's own `DisposableEffect`. |
| **D9** | Grading. | **Never graded.** Car mode never calls `finalizeCurrentQuestion`, `maybeAutoMark`, `recordCurrentMistakeIfWrong`, `advanceChallenge`, `reportChallengeDone`, `onProgressionChallengeComplete`, `onProgressionMistake`. `challengeActive` stays whatever it was; `challengeLog`, `challengeAnswers`, `challengeBarsCorrect`, `challengeGuess*` are **never touched** — car mode does **not** call `applyChallengeQuestion()` (which would wipe the guess arrays) or `freshChallengeQuestion()` (which would… actually be harmless, but it isn't needed). It draws with `nextProgression()`/`nextAdvancedProgression()`. | So you can leave a half-finished challenge, do a drive, and come back to it intact. Also keeps the drill-mistake list uncontaminated by un-answered car exercises. |
| **D10** | How is the reveal count tracked/reset? | **Derived, never stored.** `carRevealedSlots = CarMode.revealedSlots(carRound, progResolved.size)`. Resetting an exercise = `carRound = 0`. | One source of truth ⇒ no "forgot to clear the set" bug class. Deliberately **not** reused: `progBarRevealed` (a user-toggled `Set<Int>` with its own semantics) and `challengeRevealed`. |
| **D11** | Extensibility to other sub-modes. | The round/reveal/beep **schedule lives in the theory module** (`CarMode`), the driver in the app-state layer, and the car screen is gated by `ear.progSubMode == EarSubMode.Progression`. A later sub-mode reuses `CarMode` unchanged and adds its own `carDriver` body. | Keeps the only *derivable* part shared and unit-tested; keeps timing/audio/UI where it belongs. |

### Interaction with the digest's hard constraints (`docs/ear-training-conversation-digest.md`)

- "**Synthetic progressions are train-ride work**" (line 48) — car mode is exactly the sanctioned home for the synthetic generator; it does not touch the Workout curriculum.
- "**Work directly in FUNCTION**" (line 51) — the revealed slots show `ResolvedChord.romanLabel` (`I`, `V7`, `iv`), **never** chord symbols and **never** the key/mode. Do not add a "key: C major" line to the car screen.
- "**Always start guitarless — form an internal guess first**" — round 1 reveals nothing. Matches the spec exactly.

---

## 1. Layering, and the exact theory-layer API

**Rule applied:** the *driver* is timing + audio + UI, so it stays in the app-state layer. But `CLAUDE.md`/`requirements.md` §12 requires logic to be unit-testable with no UI, and **Kotlin tests exist only for the `theory` and `audio` modules** — there is no `app/src/test` directory. So the small pure part is lifted into `theory`, and nothing else:

### New file: `theory/src/main/kotlin/app/guitar/theory/CarMode.kt`

```kotlin
package app.guitar.theory

/** Timing + reveal schedule for hands-free "Car mode" ear training. Pure data:
 *  no audio, no coroutines, no UI — the platform state layers drive it. */
object CarMode {
    const val ROUNDS = 5              // progression passes per exercise
    const val BEEPS = 3               // lead-in beeps announcing a new exercise
    const val BEEP_GAP_MS = 500       // onset-to-onset, and beep-3 → chord-1
    const val LEAD_IN_MS = BEEPS * BEEP_GAP_MS   // 1500
    const val GAP_MS = 4000           // silent self-assessment gap before auto-advance
    const val BEEP_HZ = 880.0         // A5 — above the chord register & road noise
    const val BEEP_MS = 140
    const val BEEP_PEAK = 0.55f
    const val BEEP_ATTACK_MS = 5

    /** Slots revealed while [round] (1-based) is sounding. Round 1 reveals
     *  nothing; each later round reveals one more, capped at [slotCount].
     *  Round 0 (idle) reveals nothing. */
    fun revealedSlots(round: Int, slotCount: Int): Int =
        (round - 1).coerceIn(0, slotCount)

    /** Total wall-clock ms of one exercise at [bpm] over [slotCount] bars,
     *  excluding [GAP_MS]. Used by tests and the on-screen estimate. */
    fun exerciseMs(bpm: Int, slotCount: Int): Long =
        LEAD_IN_MS + ROUNDS.toLong() * slotCount * (60_000L / bpm.coerceAtLeast(10)) * 4
}
```

### New file: `chorect-web/src/theory/carMode.ts` — line-for-line mirror

```ts
export const CarMode = {
  ROUNDS: 5, BEEPS: 3, BEEP_GAP_MS: 500, LEAD_IN_MS: 1500, GAP_MS: 4000,
  BEEP_HZ: 880, BEEP_MS: 140, BEEP_PEAK: 0.55, BEEP_ATTACK_MS: 5,
  revealedSlots(round: number, slotCount: number): number { /* clamp(round-1, 0, slotCount) */ },
  exerciseMs(bpm: number, slotCount: number): number { /* … */ },
} as const;
```

Exported by adding `export * from "./carMode";` to `chorect-web/src/theory/index.ts`.

**Nothing else changes in the theory layer** — `Progression`, `ResolvedChord`, `resolveProgression`, `ProgFocus`, `randomProgression`, the pools: all untouched. Car mode consumes `ResolvedChord.romanLabel` and `progResolved.length` only.

### New file: beep DSP in the **audio** module (mirrored, testable)

`audio/src/main/kotlin/app/guitar/audio/CueBeep.kt`:

```kotlin
object CueBeep {
    /** A soft-attack sine cue: [ms] long at [freqHz], [attackMs] linear attack then
     *  an exponential decay. Samples in [-peak, peak] at [sr]. */
    fun render(freqHz: Double, ms: Int, sr: Int, peak: Float, attackMs: Int): FloatArray
}
```

`chorect-web/src/audio/cueBeep.ts`: `export function renderCueBeep(freqHz, ms, sr, peak, attackMs): Float32Array` — same 8 lines; exported from `chorect-web/src/audio/index.ts`.

*Rejected alternative:* copy `synthClick` privately into `EarTrainingState` (as `MetronomeState.kt:91`, `RhythmUnitState.kt:94`, `SambaLooperState.kt:158` all do). Rejected because the audio module already has a JUnit test dir (`audio/src/test/kotlin/app/guitar/audio/`), so putting it there is the only way to get a Kotlin assertion on the waveform, and the private-copy pattern is the codebase's known duplication wart.

---

## 2. The state machine for one car-mode exercise

New app-state fields (identical names both platforms; `CarPhase` is a new enum next to `EarMode`):

```
enum CarPhase { Idle, Beeps, Playing, Between }

carPhase: CarPhase = Idle     // Idle = a progression may be loaded, nothing running
carRound: Int = 0             // 0 = not started; 1..CarMode.ROUNDS while running
carExerciseCount: Int = 0     // how many exercises this car session (header readout)
carAutoAdvance: Boolean = true
carRevealedSlots: Int         // GETTER = CarMode.revealedSlots(carRound, progResolved.size)
private carToken: Int = 0     // web cancellation token
private carJob: Job? = null   // Android
private carBeep: FloatArray?  // lazily rendered per sample rate
```

### Public entry points

```
enterCarMode()          // from the button: earMode = Car; carPhase = Idle; carRound = 0
                        // carExerciseCount = 0; stopLoop(); notify()
exitCarMode()           // stopCarExercise(); earMode = Challenge; notify()
startCarExercise()      // = beginCarExercise(draw = true,  cancelExisting = true)
replayCarExercise()     // = beginCarExercise(draw = false, cancelExisting = true)
stopCarExercise()       // stopLoop() (which cancels the driver) + carPhase = Idle
setCarAutoAdvance(v)
carSlotLabel(i): String // i < carRevealedSlots ? progResolved[i].romanLabel : "?"  ("—" if unresolvable)
```

### The driver (identical logic; `sleep()` web ↔ `delay()` Kotlin)

```
private beginCarExercise(draw, cancelExisting):
    if cancelExisting: stopLoop()          # cancels any looper AND any live car driver
    else:              audio.stop()        # auto-advance chain: never self-cancel (see §4)
    if draw:
        if specialProgMode: nextAdvancedProgression()   # advanced / circle / sus / advanced2
        else:               nextProgression()           # diatonic / iii-focus / 3rd-vs-6th
        carExerciseCount += 1
    carRound = 0
    carPhase  = Beeps
    isLooping = true            # keeps playhead + "is playing" chrome truthful
    notify()
    launch driver(token = ++carToken)      # Kotlin: carJob = scope.launch { … }

private driver(token):
    ensureProgShapes()
    slots = progResolved.size ; if slots == 0 { carPhase = Idle; isLooping = false; notify(); return }

    # ---- lead-in: 3 beeps, sample-accurate on the audio clock ----
    beep = cachedBeep(audio.sampleRate)
    for k in 0 until CarMode.BEEPS:
        web:     audio.playSamples(beep, 0.9, audio.now() + k * 0.5)
        Android: audio.playSamplesAt(beep, 0.9f, delayFrames = k * CarMode.BEEP_GAP_MS * sr / 1000)
    sleep(CarMode.LEAD_IN_MS)               # 1500 ms → chord 1 lands 500 ms after beep 3
    if cancelled(token) return

    # ---- 5 rounds; reveal count updates BEFORE the round sounds ----
    for round in 1..CarMode.ROUNDS:
        carRound = round                    # round 1 → 0 revealed … round 5 → 4 revealed
        carPhase = Playing
        notify()
        barMs   = (60000 / max(progBpm,10)) * 4      # re-read per round: BPM edits apply next round
        sustain = max(barMs * 0.9, 200)
        for i in 0 until slots:
            if cancelled(token) return
            currentBar = i
            audio.cutReverb()
            soundBar(i, sustain)            # the EXISTING shared voicing path
            notify()
            sleep(barMs)

    # ---- done ----
    carRound = CarMode.ROUNDS               # keep the full reveal on screen
    carPhase = Between
    isLooping = false ; currentPlayingShape = null
    notify()
    if !carAutoAdvance: return
    sleep(CarMode.GAP_MS)
    if cancelled(token) return
    beginCarExercise(draw = true, cancelExisting = false)   # chain
```

`cancelled(token)` — web: `token !== this.carToken || this.earMode !== EarMode.Car`. Kotlin: coroutine cancellation already unwinds at every `delay()`; add `if (earMode != EarMode.Car) return@launch` after each `delay` as belt-and-braces.

### Reveal schedule, explicitly (4-slot progression)

| round | slots revealed | shown |
|---|---|---|
| 1 | 0 | `? ? ? ?` |
| 2 | 1 | `I ? ? ?` |
| 3 | 2 | `I V ? ?` |
| 4 | 3 | `I V vi ?` |
| 5 | 4 | `I V vi IV` (full) |
| Between | 4 | full, frozen |

3-slot progressions (some advanced/circle entries) clamp: round 4 → 3 (full), round 5 → 3.

### Timeline at the default 140 BPM, 4 bars

lead-in 1.5 s + 5 × 4 × 1.714 s ≈ **35.8 s** per exercise, + 4 s gap ⇒ ~40 s cadence. `CarMode.exerciseMs` computes this; show it as a caption on the config button ("≈40 s per exercise").

---

## 3. Cancellation — how Stop / navigate-away / rotation behave

`stopLoop()` becomes the **single cancellation point** for car mode too. This is the key robustness decision: `stopLoop()` is already called from `switchTab()`, `release()`, `applyChallengeQuestion()`, `exitChallenge()`, `setAdvancedMode()`/`setCircleMode()`/`setIiiFocusMode()`/`setThird6FocusMode()`, the transport dock's Stop, and the Android `onDispose` guard — so every one of those cancels car mode for free, with no new call sites to remember.

**`stopLoop()` additions** (`earTrainingState.ts:345-351`, `EarTrainingState.kt:533-539`):

```
stopLoop():
    isLooping = false
    loopToken++            /  loopJob?.cancel(); loopJob = null
    carToken++             /  carJob?.cancel();  carJob = null        # NEW
    if carPhase == Beeps || carPhase == Playing: carPhase = Idle       # NEW
    currentPlayingShape = null
    audio.stop()
    notify()
```

Consequences, stated so they aren't rediscovered:

- **Stop mid-exercise** → driver dies at its next check, audio silences, `carPhase = Idle`, `carRound` is **preserved**, so the screen freezes the reveals at whatever round it reached and offers `Replay 5×` / `Next →`. It does **not** spoil the remaining slots. (Nothing is graded, so nothing is lost.)
- **Self-cancel hazard**: the auto-advance chain calls `beginCarExercise(cancelExisting = false)` from *inside* the driver, precisely so it doesn't `carJob?.cancel()` itself (which in Kotlin would cancel the running coroutine at its next suspension point) or bump the token out from under a still-executing frame.
- **`startLoop()` can't hijack**: it early-returns on `isLooping` (`earTrainingState.ts:321`, `EarTrainingState.kt:512`), and the driver sets `isLooping = true` for its whole run. The car screen doesn't render the transport dock anyway (§5).
- **`release()`** (`earTrainingState.ts:1815`, `EarTrainingState.kt:2172`) already begins with `stopLoop()` → covered. Add `carPhase = Idle` there for tidiness.
- **Rotation** does not route through any of the above (see D8).
- **Web `visibilitychange` → hidden** calls `stopCarExercise()` (D7).

---

## 4. The `EarMode.Car` branch audit (must-fix list)

Every site that today reads `earMode == Challenge` with an implicit else. Add `EarMode.Car` handling at each — this list is exhaustive as of 9fc5a02:

**Web `chorect-web/src/app/earTrainingState.ts`**

1. `:33` enum — add `Car = "Car"`.
2. `:141-147` `switchTab()` — sets `earMode = Practice`; correct as-is (leaving Progressions exits car mode); it also calls `stopLoop()` → driver cancelled. No change needed, but assert it in review.
3. `:178` `nextProgression()` — `if (this.earMode === EarMode.Practice)` history push: Car is excluded already. ✔ no change.
4. `:1388` `nextAdvancedProgression()` history push: same. ✔
5. **`:1407-1408`** `if (this.earMode === EarMode.Challenge) this.stopLoop(); else if (this.isLooping) { stopLoop(); startLoop(); }` — **must change**: Car must take the `stopLoop()` branch, otherwise drawing a new advanced progression restarts the *infinite* looper mid-car-exercise. → `if (earMode !== EarMode.Practice) this.stopLoop(); else if …`.
6. `:345` `stopLoop()` — the additions in §3.

**Web `chorect-web/src/app/earTrainingUI.ts`**

7. `:330-331` `attachChallengeKeys()` `active` guard — already requires `earMode === Challenge`, so the 1–7 physical-key handler is inert in car mode. ✔
8. `:445-448` `progChallengeInFlight` — requires `Challenge`. ✔ (car mode gets its own header)
9. `:478-486` the Practice/Challenge `segmented(...)` — enumerates only those two values explicitly, so `Car` never appears. ✔
10. **`:493-497`** dispatch — **must change** to a 3-way: `earMode === Car ? this.carModeView(body) : earMode === Challenge ? … : …`, for both the `specialProgMode` and diatonic paths.
11. **`:531-544`** transport-dock gate — **must change** to `if (progSubMode === Progression && earMode !== EarMode.Car)`.
12. `:1116-1120` the fold's segmented control — two explicit values. ✔

**Android `app/src/main/kotlin/app/guitar/app/EarTrainingState.kt`**

13. `:2197` enum — `enum class EarMode { Practice, Challenge, Car }`.
14. **`:1670`** the advanced twin of #5 — `if (earMode == EarMode.Challenge) stopLoop() else if (isLooping) {…}` → `if (earMode != EarMode.Practice) stopLoop() else if …`.
15. `:533` `stopLoop()` — §3 additions.
16. `:99-104` `switchTab`, `:256` / `:1645` history pushes — ✔ as-is.

**Android `app/src/main/kotlin/app/guitar/app/EarTrainingScreen.kt`**

17. **`:116-119`** `progChallengeInFlight` — requires `Challenge`. ✔
18. **`:165-170`** `SegmentedRow(options = EarMode.entries, …)` — **must change** to `options = listOf(EarMode.Practice, EarMode.Challenge)`, else a "Car" segment appears in the picker.
19. **`:301-306`** the same `EarMode.entries` inside `ChallengeModeFold` — **must change** identically.
20. **`:178-185`** dispatch `when` — **must change** to a 3-way for both the special and diatonic paths.
21. **`:209-222`** `TransportDock` gate — **must change** to also require `earMode != EarMode.Car`.
22. `:92-94` `DisposableEffect` guard — unchanged (D8).

---

## 5. The car screen — layout spec (both platforms)

Fits `GUI_DESIGN.md` §3.2/§4.2: the tab bar (portrait) / tab rail (landscape) **stays** (navigation is always present — that's the Studio invariant), the *content column* becomes the car screen. Everything else in the ear screen's chrome is gone: no sub-mode chips, no Practice/Challenge control, no answer pad, no degree-reference row, no fretboard, no transport dock, no generator card.

```
┌───────────────────────────────────────────────────────────────┐
│ CAR MODE          Exercise 7 · round 3/5              Exit    │  32-40dp, caption-size
├───────────────────────────────────────────────────────────────┤
│ Diatonic · Random key · 7th chords                            │  read-only, 12sp, muted
│                                                               │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐          │
│  │         │  │         │  │         │  │         │          │  slots: equal flex,
│  │    I    │  │   V7    │  │    ?    │  │    ?    │          │  fill remaining height,
│  │         │  │         │  │         │  │         │          │  label 15-20% of the
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘          │  shorter viewport edge
│   ●●●●●○○○  (round dots)                                      │
├───────────────────────────────────────────────────────────────┤
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐        │
│  │   Replay 5×   │ │    Next →     │ │     Stop      │        │  56dp tall, equal flex
│  └───────────────┘ └───────────────┘ └───────────────┘        │
│  [x] Auto-advance                                             │
└───────────────────────────────────────────────────────────────┘
```

- **Slot sizing:** the four slots are a `flex: 1` row that consumes all remaining height; the Roman label is sized off the smaller viewport edge (`clamp(48px, 14vmin, 140px)` web; `with(LocalDensity)`/`BoxWithConstraints` → `min(maxWidth/4, maxHeight) * 0.42` sp on Android), bold, tabular. Hidden slots show `?` at 45% opacity. **Landscape** is the same row, just taller slots — no separate layout.
- **Playhead:** the sounding slot gets the existing `BG_PLAYHEAD` teal fill + ring (`earTrainingUI.ts:39`), so you can see *where* in the bar cycle you are at a glance.
- **Round dots:** 5 dots, filled up to `carRound` — the only progress affordance.
- **Idle state** (`carPhase == Idle`, `carRound == 0`, nothing drawn yet): a single centred `Start ▶` button plus the "≈40 s per exercise · 5 plays · reveals one chord per play" caption.
- **`Between` state:** the three action buttons; if auto-advance is on, the Next button's label becomes `Next → (auto in 4s)` — a static label, not a live countdown (a per-second rerender of a 4 s gap is noise; flag if you want the countdown).
- **Colours:** existing tokens only (`--surface2`, `--act`, `--feedback`, `BG_REVEAL`) — no new palette entries, so both themes work.
- New CSS in `chorect-web/src/style.css`, appended after the `/* ---------- Ear Training ---------- */` block: `.car-screen`, `.car-topbar`, `.car-slot-row`, `.car-slot`, `.car-slot .lab`, `.car-dots`, `.car-actions`.

---

## 6. Implementation phases

Each phase compiles, is independently verifiable, and leaves the app shippable.

---

### Phase 1 — theory: the pure schedule

**Goal:** `CarMode` exists and is asserted on both platforms; zero behaviour change.

**Files**

- new `theory/src/main/kotlin/app/guitar/theory/CarMode.kt`
- new `theory/src/test/kotlin/app/guitar/theory/CarModeTest.kt`
- new `chorect-web/src/theory/carMode.ts`
- `chorect-web/src/theory/index.ts` (+1 export line)
- `chorect-web/test/verify.ts` (+import, +checks)

**Kotlin ↔ web:** as written in §1 — object + `const val` ↔ `export const CarMode = {…} as const`; `coerceIn` ↔ `Math.min/Math.max`.

**Tests**

- `theory/src/test/kotlin/app/guitar/theory/CarModeTest.kt` (JUnit 5 + `kotlin.test`, matching `EarTrainingTest.kt`'s style):
  - `` `round 1 reveals nothing and round 5 reveals every slot` `` → `revealedSlots(1,4)==0`, `(5,4)==4`.
  - `` `each round from the 2nd reveals exactly one more slot` `` → loop `2..5`: `revealedSlots(r,4) - revealedSlots(r-1,4) == 1`.
  - `` `reveal count clamps to the slot count and never goes negative` `` → `(5,3)==3`, `(4,3)==3`, `(0,4)==0`, `(-1,4)==0`.
  - `` `lead-in is three beeps half a second apart` `` → `BEEPS==3 && BEEP_GAP_MS==500 && LEAD_IN_MS==1500`.
  - `` `exercise length at 140 bpm over 4 bars is about 36 seconds` `` → `exerciseMs(140,4)` in `35_000..37_000`.
- `chorect-web/test/verify.ts` — the same five as `check(...)` calls, plus one cross-platform pin: `check("CarMode constants match Kotlin", CarMode.ROUNDS===5 && CarMode.BEEPS===3 && CarMode.BEEP_GAP_MS===500 && CarMode.GAP_MS===4000 && CarMode.BEEP_HZ===880)`.

**Manual verification:** `./gradlew :theory:test` → green. Web: `npx tsc --noEmit` (locally impossible — no node; push and read the CI job, per `.github/workflows/deploy-web.yml:53`). Note `npm run verify` is deliberately **not** in CI (workflow comment lines 55-60: 3 pre-existing percussion failures), so the new verify checks are a bonus, not the gate.

---

### Phase 2 — `EarMode.Car`, entry button, and an inert car screen

**Goal:** you can reach a car screen from the challenge config screen and get back. No audio, no driver. This is where the whole §4 branch audit lands, so the risky part is validated before any timing code exists.

**Files**

- `app/src/main/kotlin/app/guitar/app/EarTrainingState.kt` — enum `EarMode` +`Car`; new `enum class CarPhase { Idle, Beeps, Playing, Between }` beside it; fields `carPhase`, `carRound`, `carExerciseCount`, `carAutoAdvance`, getter `carRevealedSlots`, `carSlotLabel(i)`; `enterCarMode()`, `exitCarMode()`; audit items 13, 14, 16.
- `chorect-web/src/app/earTrainingState.ts` — same, audit items 1, 5.
- `app/src/main/kotlin/app/guitar/app/EarTrainingScreen.kt` — new `@Composable private fun CarModeView(state: AppState, ear: EarTrainingState)`; the entry `OutlinedButton` under `:1472`'s `Button`; a duplicate row in `ChallengeModeFold` (`:290`); audit items 18, 19, 20, 21.
- `chorect-web/src/app/earTrainingUI.ts` — new `private carModeView(parent: HTMLElement)`; entry `btn(...)` after `:1022`; a row in `challengeModeFold()` (`:1104`); audit items 10, 11.
- `chorect-web/src/app/icons.ts` — add `"car"` to the `IconName` union and a stroke-path entry to `PATH` (body + roof + two wheels, 1.6 stroke, matching the file's existing hand-drawn style).
- `chorect-web/src/style.css` — the `.car-*` classes.

**Kotlin ↔ web, side by side**

| Kotlin | Web |
|---|---|
| `var carPhase by mutableStateOf(CarPhase.Idle)` `private set` | `carPhase: CarPhase = CarPhase.Idle` |
| `val carRevealedSlots: Int get() = CarMode.revealedSlots(carRound, progResolved.size)` | `get carRevealedSlots(): number { return CarMode.revealedSlots(this.carRound, this.progResolved.length); }` |
| `fun enterCarMode() { earMode = EarMode.Car; carPhase = CarPhase.Idle; carRound = 0; carExerciseCount = 0; stopLoop() }` | same + `this.notify()` |
| entry: `OutlinedButton(onClick = { ear.enterCarMode() }) { Icon(Icons.Outlined.DirectionsCar, null); Spacer(4.dp); Text("Car mode — hands-free") }` | `const b = btn("Car mode — hands-free", () => ear.enterCarMode()); b.prepend(icon("car", 20));` |
| dispatch: `when (ear.earMode) { EarMode.Car -> CarModeView(state, ear); EarMode.Challenge -> …; EarMode.Practice -> … }` | `ear.earMode === EarMode.Car ? this.carModeView(body) : ear.earMode === EarMode.Challenge ? … : …` |

**Tests:** no new automated tests (this is pure wiring/UI). The compiler is the test: making the Android dispatch an exhaustive `when (ear.earMode)` **as a statement over an enum with a new entry** is what surfaces missed branches — write it as `when (ear.earMode) { … }` with all three arms explicitly, no `else`.

**Manual verification**

1. `./gradlew :app:installDebug` → Ear → Progressions → Challenge → the config screen shows `Start challenge ▶` and, under it, `Car mode — hands-free`.
2. Tap it → the car screen appears: 4 slots all `?`, `Start ▶`, `Exit`. The tab bar (portrait) / rail (landscape) is still there. Rotate → layout swaps, nothing crashes.
3. `Exit` → back on the challenge config screen; the generator summary is unchanged.
4. Start a real 10-question challenge, answer 2 questions, open the fold → `Car mode`, enter, exit → the challenge is still on question 3 with both prior answers intact (**proves D9**).
5. Confirm the Practice/Challenge segmented control still shows exactly **two** segments (audit 18/19).
6. Web: same walkthrough on the deployed Pages build after CI goes green.

---

### Phase 3 — the beep and the lead-in

**Goal:** `Start ▶` plays exactly three beeps, 0.5 s apart, then stops. Nothing else.

**Files**

- new `audio/src/main/kotlin/app/guitar/audio/CueBeep.kt`
- new `audio/src/test/kotlin/app/guitar/audio/CueBeepTest.kt`
- new `chorect-web/src/audio/cueBeep.ts`; `chorect-web/src/audio/index.ts` (+export)
- `chorect-web/test/verify.ts` (+3 checks)
- both `EarTrainingState` files — `private cachedBeep(sr)` + `beginCarExercise` with only the lead-in, then `carPhase = Between`.

**Kotlin ↔ web**

| Kotlin | Web |
|---|---|
| `CueBeep.render(CarMode.BEEP_HZ, CarMode.BEEP_MS, audio.sampleRate, CarMode.BEEP_PEAK, CarMode.BEEP_ATTACK_MS): FloatArray` | `renderCueBeep(CarMode.BEEP_HZ, CarMode.BEEP_MS, audio.sampleRate, …): Float32Array` |
| `audio.playSamplesAt(beep, 0.9f, delayFrames = k * CarMode.BEEP_GAP_MS * sr / 1000)` (`AudioEngine.kt:60`) | `audio.playSamples(beep, 0.9, audio.now() + k * CarMode.BEEP_GAP_MS / 1000)` (`engine.ts:473`, `now()` at `:435`) |
| `carJob = scope.launch { …; delay(CarMode.LEAD_IN_MS.toLong()) }` | `void (async () => { …; await sleep(CarMode.LEAD_IN_MS) })()` with the `token` guard |

Beeps are scheduled on the **audio clock**, not with three `sleep`s, so the 500 ms spacing is sample-accurate and immune to render jitter — same technique as `MetronomeState.kt:80-84`.

**Tests**

- `CueBeepTest.kt`: length `== sr*ms/1000` at 44100 and 48000; every sample finite and `|s| <= peak`; `buf[0]` ≈ 0 (attack, `< 0.02`); the last sample `< 0.05 * peak` (decay); the peak occurs inside the first 15% (attack then decay); a 44.1k render zero-crosses ~`2*880*0.14 ≈ 246` times ±4 (proves the frequency).
- `verify.ts`: the length, bound, and zero-crossing checks (imports `renderCueBeep` from `../src/audio`, which verify.ts already imports from).

**Manual verification:** enter car mode, tap `Start ▶` in a quiet room → exactly three identical short beeps, evenly spaced, no click at onset, clearly audible over a car-radio-level background. Repeat with a Bluetooth speaker (latency shifts everything equally; spacing must stay even). Web: same on the phone browser.

---

### Phase 4 — the 5-round driver and the reveal ramp

**Goal:** a full exercise: beeps → 5 passes → reveals 0,1,2,3,4. Stop works. Not graded.

**Files:** both `EarTrainingState` files (the full `driver` from §2 + the `stopLoop()` additions from §3), both UI files (slot rendering off `carRevealedSlots`/`carSlotLabel`, round dots, playhead, `Stop` button).

**Kotlin ↔ web:** `for (i in progResolved.indices) { currentBar = i; audio.cutReverb(); soundBar(i, sustain); delay(barMs) }` ↔ the same with `this.notify()` after `currentBar` and `await sleep(barMs)`; cancellation via coroutine `delay` vs. the `token !== this.carToken` check. `soundBar` (private, `earTrainingState.ts:272`, `EarTrainingState.kt:358`) and `ensureProgShapes` are reused **verbatim** — car mode must sound bit-identical to the challenge looper.

**Tests**

- No new theory tests (Phase 1 already pins the schedule); the driver is timing, deliberately untested.
- **Add a regression check to `verify.ts`** that `CarMode.revealedSlots` is the *only* reveal source, by asserting the table again against a hand-written expected array `[0,0,1,2,3,4]` indexed by round 0..5 — cheap insurance against someone "optimising" the clamp.

**Manual verification (the core acceptance test)**

1. Car mode → `Start ▶`. Count: 3 beeps, then 5 identical passes.
2. Pass 1: all four slots `?`. Pass 2: slot 1 shows its Roman *from the first bar of that pass*. Pass 3: slots 1-2. Pass 4: 1-2-3. Pass 5: all four.
3. The teal playhead moves 1→2→3→4 each pass, in time with the chords.
4. Slot text is legible **at arm's length** — hold the phone where a dash mount would be. If not, that's a Phase 7 sizing bug, note the required size.
5. Verify the labels are Roman numerals (`I`, `V7`, `iv`) — **not** chord symbols, and no key/mode anywhere on screen (digest constraint).
6. Tap `Stop` mid-pass-3 → audio silences within one bar, reveals freeze at 2 slots, `Replay 5×`/`Next →` appear. Tap `Stop` during the beeps → silence, back to `Start ▶`.
7. Set the generator to `Advanced` (via the challenge config screen), enter car mode, run one exercise → it draws advanced progressions and, if one has 3 chords, only 3 slots render and round 4 already shows all 3 (**proves D3 + the clamp**).
8. Press the phone's back/nav to another tab mid-exercise → audio stops (via the `onDispose` guard → `stopLoop()`).
9. Rotate mid-exercise → **audio keeps playing**, reveals unchanged, layout re-lays out (**proves D8**).

---

### Phase 5 — Replay, Next, auto-advance, the gap

**Goal:** the hands-free chain.

**Files:** both `EarTrainingState` files (`replayCarExercise`, `setCarAutoAdvance`, the `Between`-state gap + chain), both UI files (the three big buttons + the auto-advance switch).

**Kotlin ↔ web:** `Switch`/`switchRow("Auto-advance", …)`; buttons are `Button(modifier = Modifier.weight(1f).height(56.dp))` ↔ `btn(...)` with `style="flex:1;height:56px;font-size:18px"`.

**Tests:** none automated (timing). Add a comment in both driver bodies citing `CarMode.GAP_MS` so the two can't drift silently.

**Manual verification**

1. Auto-advance ON (default): let an exercise finish → ~4 s of silence → 3 beeps → a **new** progression, reveals back to none, "Exercise 2". Let it run 3 exercises without touching the phone (**the actual feature**).
2. `Replay 5×` after finishing → the **same** progression (check the revealed Romans match), beeps again, reveals restart at none (**proves D4**).
3. `Next →` mid-exercise → immediately abandons and draws a new one; the counter increments once, not twice.
4. Auto-advance OFF → after round 5 it stays on the finished exercise indefinitely; audio silent; `Next →` still works.
5. Rapid-fire `Next → Next → Next` 5×: no overlapping audio, no double-speed loop, counter increments by exactly 5 (**proves the token/job cancellation**).

---

### Phase 6 — screen wake, backgrounding

**Goal:** the screen never sleeps mid-exercise; backgrounding is defined.

**Files**

- `app/src/main/kotlin/app/guitar/app/EarTrainingScreen.kt` — inside `CarModeView`:

  ```kotlin
  val view = LocalView.current
  val keepAwake = ear.earMode == EarMode.Car
  DisposableEffect(keepAwake) { view.keepScreenOn = keepAwake; onDispose { view.keepScreenOn = false } }
  ```

  (+`import androidx.compose.ui.platform.LocalView`). **No manifest change** — `app/src/main/AndroidManifest.xml` stays as-is; `WAKE_LOCK` is not needed for `keepScreenOn`.
- `chorect-web/src/app/earTrainingUI.ts` (or a 25-line new `chorect-web/src/app/wakeLock.ts` — recommended, so the Looper/Drums can reuse it):

  ```ts
  type Sentinel = { release(): Promise<void>; addEventListener(t: string, f: () => void): void };
  type NavWL = Navigator & { wakeLock?: { request(t: "screen"): Promise<Sentinel> } };
  export async function acquireWakeLock(): Promise<Sentinel | null> { try { return (await (navigator as NavWL).wakeLock?.request("screen")) ?? null; } catch { return null; } }
  ```

  Acquired in the `Start ▶`/`enterCarMode` click handler (must be a user gesture); re-acquired on `visibilitychange` when `!document.hidden` and still in car mode; released in `exitCarMode` and on `stopCarExercise`.
- Web: the same `visibilitychange` listener calls `ear.stopCarExercise()` when `document.hidden` (D7). Register once, remove on exit — follow the single-tracked-listener pattern already used for `subModeOutsideCloser` (`earTrainingUI.ts:555-570`).

**Tests:** none automated (platform APIs).

**Manual verification**

1. Android: set Display timeout to 15 s, run car mode, don't touch the phone for 2 minutes → screen stays on. Exit car mode → screen sleeps normally after 15 s (**proves the lock is released**).
2. Android: background the app mid-exercise → audio continues (documented behaviour); return → still running, screen stays awake.
3. Web on the phone: run car mode, don't touch for 2 min → screen stays on (Chrome Android / Safari 16.4+). Switch tabs → exercise stops; come back → `Start ▶`/`Replay` available and the wake lock is re-acquired on the next start.
4. Web on a browser without the API (or with it disabled): car mode still runs; only the wake lock is missing, no exception in the console.

---

### Phase 7 — glanceability polish + design doc

**Goal:** readable from a dash mount, portrait and landscape, both platforms; the doc matches (`GUI_DESIGN.md` is "single source of truth… update this doc before changing visual code").

**Files:** `chorect-web/src/style.css`, `chorect-web/src/app/earTrainingUI.ts`, `app/src/main/kotlin/app/guitar/app/EarTrainingScreen.kt`, **`GUI_DESIGN.md`** (new §10.4 "Progressions — Car mode (hands-free)" documenting the entry point, the beeps→5-rounds→reveal schedule, the simplified layout in both orientations, wake behaviour, and the not-graded rule; plus a one-line cross-reference from §10.0).

**Manual verification:** phone in a dash mount / at 60–70 cm, portrait and landscape, screen at 40% brightness: the Roman numerals and the playhead must be readable **without leaning in**; the three action buttons must be hittable without looking (≥56 dp, full-width thirds). Repeat on the web build. Compare against `GUI_DESIGN.md` §2.2 type scale — the slot label is a Display-scale number, which is consistent with the tuner's 96 px note (`.tuner-note`).

---

### Phase 8 — version bump, build, ship

**Files**

- `app/build.gradle.kts:20-21` → `versionCode = 27100`, `versionName = "2.71.0"`.
- `chorect-web/src/app/appState.ts:18` → `export const APP_VERSION = "2.71.0";`.
- `README.md` — one line in the feature list (optional but conventional).
- Archive the previous APK: copy the current `Chorect_beta_V2.70.0.apk` into `releases/` **before** building (the Gradle `doLast` at `app/build.gradle.kts:38-46` deletes stale APKs from the output dir; `releases/` is never deleted — see the comment at `:17`).

**Verification / build steps, in order**

```sh
./gradlew :theory:test                 # Phase 1 assertions
./gradlew :audio:testDebugUnitTest     # Phase 3 assertions
./gradlew test                         # everything, no regressions
./gradlew :app:installDebug            # builds Chorect_beta_V2.71.0.apk + installs
adb shell am start -n app.guitar/app.guitar.app.MainActivity
```

Then re-run the Phase 4 and Phase 5 manual acceptance walkthroughs on the device.

Web: commit + push → the `Deploy web to GitHub Pages` workflow runs `npm ci`, `npx tsc --noEmit` (**the type gate — there is no local node on this machine**), then `vite build --base=/<repo>/`. Confirm the run is green, then load `https://nadavleh.github.io/guitar-app/` on the phone and repeat the Phase 4/5/6 web verifications. If the deploy job goes red, check the asset hash before retrying — see the note at `.github/workflows/deploy-web.yml:76-88`.

Suggested commit message: `feat(ear): hands-free Car mode for the progression challenge - v2.71.0`

---

## 7. Risks & watch-items

1. **The implicit-else trap (highest risk).** `EarMode.Car` silently rendering the Practice view. Mitigation: §4's numbered audit; write the Android dispatch as an explicit 3-arm `when` with **no `else`**; grep `earMode ==`/`earMode ===` after Phase 2 and confirm every hit is on the list.
2. **`nextAdvancedProgression`'s loop-restart branch** (audit items 5 and 14) — the one place where a missed branch would restart the *infinite* looper on top of a running car exercise. Verify explicitly with Phase 4 step 7.
3. **Web timer throttling.** Bar timing uses `setTimeout` (parity with the existing looper). In a hidden tab it degrades; D7 stops the exercise instead. If you ever want background-safe car mode on the web, chord onsets would need to be scheduled on the AudioContext clock, which `playChord` doesn't currently support (`engine.ts:395` has no `when` parameter) — that's a separate change.
4. **Auto-advance runaway.** A chained driver that fails to check its token could double-schedule. Phase 5 step 5 (rapid Next ×5) is the test for it; `beginCarExercise(cancelExisting = false)` from inside the driver is the mechanism that avoids self-cancellation on both platforms.
5. **Bluetooth latency** shifts beeps and chords equally, so the *musical* result is fine; only the absolute "3-2-1-go" delay grows. No mitigation needed.
6. **`carExerciseCount` and `progressionCount`.** `nextProgression()` increments `progressionCount` (`earTrainingState.ts:202`, `EarTrainingState.kt:285`) — car mode exercises will therefore count toward that practice counter. Harmless, but call it out in review in case you'd rather they didn't.

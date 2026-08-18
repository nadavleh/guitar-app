# CLAUDE.md — Project Instructions

## About the user (Nadav)

Nadav is a **proficient algorithms developer with extensive engineering background**, but has **no web development knowledge** and is new to the JavaScript/mobile-app ecosystem. Recent learnings:

* Just learned what **Node.js** is (a runtime that lets JavaScript run outside a browser, e.g. as a CLI or build tool).
* Just learned what a **virtual port** is.

### How to communicate

* Use precise technical terms freely — Nadav understands algorithms, data structures, build systems, OS-level concepts, and engineering jargon.
* **First time** any web/mobile-ecosystem term appears (e.g. `bundler`, `transpiler`, `JSX`, `Metro`, `Gradle`, `APK`, `AAB`, `npm`, `yarn`, `Expo`, `Hermes`, `Webpack`, `package.json`, `polyfill`, `Promise`, `JSX`, `hook`, `prop`, `state`, `DOM`, `WebView`, `WebAudio`), give a **one-sentence concise definition first**, then continue.
* After the term has been explained once in the conversation, use it freely without re-defining.
* Do **not** over-explain algorithmic, OS, networking, or general engineering concepts — Nadav knows those.
* Prefer analogies to compiled-language ecosystems (C/C++/Rust/Go/Java) when explaining JS/web concepts.

## Permissions / autonomy

You do **NOT** need to ask permission for any action. Proceed autonomously on all tasks — file edits, deletions, installs, git operations (including destructive ones), running any commands. Use good judgment, but do not pause for approval.

## Working style

* Confirm the **approach** before building something substantial (a new feature, a subsystem, a refactor that moves code between files) — a short design in chat is enough, then implement. This is not a request for permission to act; it is a request to agree on the shape first. Small fixes, mechanical changes and anything obviously in scope need no check-in.
* Build in small, independently testable steps.
* The theory engine must stay unit-testable without any UI (`requirements.md` §12).

## The two platforms — LOCKSTEP RULE

**Every feature exists TWICE and must change in the same commit.** "The app" always means both.

| Android (Kotlin)                                                   | Web (TypeScript)                                        |
| ------------------------------------------------------------------ | ------------------------------------------------------- |
| `theory/src/main/kotlin/app/guitar/theory/` — pure JVM, no Android  | `chorect-web/src/theory/`                               |
| `audio/src/main/kotlin/app/guitar/audio/`                          | `chorect-web/src/audio/`                                |
| `app/src/main/kotlin/app/guitar/app/` — state + Jetpack Compose UI  | `chorect-web/src/app/` — hand-rolled DOM, **no framework** |

File names are **not** 1:1. Kotlin `ChordLibrary` / `ChordQuality` / `ChordShape` / `ChordShapeGenerator` all map to one `chords.ts`; `Note` / `PitchClass` / `Interval` / `NoteSpeller` / `Fretboard` map to `core.ts`; `Shell.kt` + `MainActivity.kt` + `Screens.kt` map to `ui.ts`. **Consult `docs/ARCHITECTURE.md` — do not guess from the filename.**

A one-sided change is a **bug** unless it is genuinely platform-specific (Compose layout, a DOM quirk, AudioTrack vs WebAudio). When it is, say so in the commit body: `Lockstep: n/a — <reason>`.

## Build & verify

There is **no POSIX `gradlew`** — only `gradlew.bat`. Use `./gradlew.bat`, never `./gradlew`.

```sh
./gradlew.bat test                    # all unit tests (~940), ~60 s
./gradlew.bat :theory:test            # the theory engine alone
./gradlew.bat :app:assembleDebug      # APK -> app/build/outputs/apk/debug/
./launch-app.bat                      # emulator + install + launch
```

Tests live **only** in `theory/src/test/` and `audio/src/test/`. `app/` has no test source set — Compose code is verified by building, installing and screenshotting.

**There is no Node on this machine.** The web port cannot be type-checked or run locally. Push, then read CI:

```sh
gh run list --limit 3
gh run view <id> --log-failed
```

`.github/workflows/deploy-web.yml` runs `tsc --noEmit`, then `npm run verify` (`chorect-web/test/verify.ts` — runtime checks that pin the TS ports against the Kotlin ones), then deploys to Pages. `.github/workflows/kotlin-tests.yml` runs the Gradle tests. A red *deploy* job does not by itself mean the site is stale — check the asset hash before retrying.

## Version-bump ritual (every user-visible change)

Bump all **three**, together, or the numbers drift:

1. `app/build.gradle.kts` — `versionCode` (major × 10000 + minor × 100 + patch) **and** `versionName`
2. `chorect-web/src/app/appState.ts` — `APP_VERSION`
3. `chorect-web/package.json` — `"version"`

`minor` = new feature, `patch` = bugfix. Copy the built APK into `releases/` — **never delete an old one**. The debug output folder keeps only the newest APK (enforced by a `doLast` in `app/build.gradle.kts`).

## Generated / do-not-hand-edit

* `chorect-web/dist/`, `chorect-web/dist-test/`, `*/build/` — build output.
* `tools/cavaco_g_shapes.json` — regenerate with `./gradlew.bat :theory:emitCavacoShapes`.
* `app/src/main/assets/drums/*.wav` and `chorect-web/public/drums/*.wav` — built by `tools/build_drum_samples.py`; both trees must hold the same files.
* `docs/progression-library.md` is **hand-maintained**, despite what its header used to say.

## Big files — read the map first

A dozen files are 1000–3300 lines. `docs/ARCHITECTURE.md` lists each one's internal section markers with line ranges, so you can `sed -n 'A,Bp'` the region you need instead of reading the whole file. Both twins carry the **same section markers in the same order** — use them to find the mirror edit. Physical layout is *not* always symmetric (car mode sits at the end of `EarTrainingScreen.kt` but near the top of `earTrainingUI.ts`).

## Docs — which to trust

* `docs/ARCHITECTURE.md` — file-pair map, big-file section index, domain glossary. **Start here.**
* `GUI_DESIGN.md` — single source of truth for look-and-feel. Update it *before* changing visual code (its own rule).
* `requirements.md` — a frozen v1.6.0 spec. Directionally right, inventory stale.
* `README.md` — **stale** (badge and file lists lag well behind). Do not trust its file map; use `docs/ARCHITECTURE.md`.
* `docs/superpowers/specs/` and `docs/superpowers/plans/` — per-feature design records.

## Reference digests (read these instead of the source PDFs)

* `docs/ear-training-conversation-digest.md` — condensed from Nadav's 203-page ChatGPT ear-training export (`C:\Users\Nadav\Desktop\Ear Training corriculum.pdf`) plus the two curriculum PDFs. Holds his diagnosed skill profile, his three bottlenecks, the master goals, every hard preference/correction he made (spiral curriculum, synthetic drills are train-ride only, work directly in function, no rhythmic dictation/solfège, spoilers in an appendix), the harmonization constraint ladder, the songs-by-harmonic-concept ladder, his logged Girl / All-of-Me attempts and scores, time-scaling and expected-progress tables. **Consult it before changing anything in the ear-training Workout curriculum** (`theory/.../EarWorkout.kt` + `chorect-web/src/theory/earWorkout.ts`).

## Project goal

Build a mobile guitar-practice app per `requirements.md`. Stack is settled: **native Kotlin + Jetpack Compose** (Android-first), with the pure theory engine mirrored in TypeScript for the web port. Prioritize correctness of the music-theory engine and clean separation of theory logic from UI (`requirements.md` §12).

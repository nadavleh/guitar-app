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

## Project goal

Build a mobile guitar-practice app per `requirements.md`. Initial target: **Android first**, iOS later. Prioritize correctness of the music-theory engine and clean separation of theory logic from UI (see `requirements.md` §12).

## Reference digests (read these instead of the source PDFs)

* `docs/ear-training-conversation-digest.md` — condensed from Nadav's 203-page ChatGPT ear-training export (`C:\Users\Nadav\Desktop\Ear Training corriculum.pdf`) plus the two curriculum PDFs. Holds his diagnosed skill profile, his three bottlenecks, the master goals, every hard preference/correction he made (spiral curriculum, synthetic drills are train-ride only, work directly in function, no rhythmic dictation/solfège, spoilers in an appendix), the harmonization constraint ladder, the songs-by-harmonic-concept ladder, his logged Girl / All-of-Me attempts and scores, time-scaling and expected-progress tables. **Consult it before changing anything in the ear-training Workout curriculum** (`theory/.../EarWorkout.kt` + `chorect-web/src/theory/earWorkout.ts`).

## Working style

* Per `requirements.md` §17: **do not start full implementation immediately**. First propose tech stack, architecture, data model, theory-engine API, UI hierarchy, and milestones. Confirm with Nadav before building.
* Build in small, independently testable steps.
* Theory engine must be unit-testable without any UI.
* When suggesting a stack, briefly justify the choice and list the trade-offs (build complexity, native-audio quality, Android tooling required, etc.) so Nadav can make an informed call — he hasn't picked between React Native / Flutter / native Kotlin yet.

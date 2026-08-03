# Ear-Training Workout tab + Theory tab — design

Date: 2026-08-03 · Both platforms (Android + chorect-web), lockstep v2.57.0

## Goal

Nadav asked ChatGPT for a real-song ear-training plan ("synthetic exercises can
take me only so far"). Two PDFs resulted:

1. `ear_training_curriculum_first_2_months_v4_revised.pdf` — 32 sessions
   (8 weeks × 4), one song per session, 45-min frame, spoiler appendix A,
   train-ride drills appendix B.
2. `month_ear_training_curriculum.pdf` — stricter revision: 8 weeks, ONE song
   per week, fixed A–D session structure, "what is not graded" boundaries,
   verified performer/recording + spoiler appendix, exam tables.

Task: review/revise the plan, put it in the app as a new ear-training menu
entry — songs tappable (YouTube/Spotify), buttons to reveal progressions.
Plus: integrate `descending_interval_song_references.pdf`, generate an
ascending-interval list, and add a new "Theory" menu entry to host them.

## Curriculum revision (Claude)

The two documents are competing iterations, not month-1/month-2. Decision:
**merge into one plan with two tracks** rather than pick a winner:

- **Track A — Session plan**: the 32 sessions of the v4 PDF (more variety,
  4 sessions/week, one song each). Content kept, condensed for cards.
- **Track B — Deep track**: the 8-week one-song-per-week plan (stricter
  grading, named recordings, verified spoilers). Kept whole, including week 8
  consolidation (no new song) and both exam tables.
- The deep track's **evaluation rules become global** for both tracks:
  guitarless first pass; one hypothesis at a time on guitar; function before
  spelling (right function + uncertain inversion = partial success, not a
  miss); arrangement details are bounded; exact performer/version matters.
- **Session 8 (You Are My Sunshine)** flagged clearly: the V7/IV lab only
  works with a version containing I7→IV; otherwise treat as plain I–IV–V.
- Spoiler key spot-checked (Stand by Me I–vi–IV–V; Let It Be; All of Me
  C E7 A7 Dm E7 Am D7 Dm G7 C; NWNC C G/B Am F; Use Me Em7–A7; Hallelujah
  E7→Am = V7/vi; etc.) — all defensible normalized approximations; kept,
  labeled approximate.
- No other content changes; both PDFs are musically sound.

## Feature 1 — "Workout" sub-mode in Ear Training

New `EarSubMode.Workout` chip (overflow row). View renders:

1. "How to practice" card — merged global rules (7 bullets).
2. "45-minute session frame" card — the v4 time table.
3. Harmonization constraints card (Month-1 rule/vocab, Month-2 rule/vocab).
4. **Track A**: Month 1 (weeks 1–4) + Month 2 (weeks 5–8) — collapsible
   weeks; each session card: number+title, song row (tap → YouTube search,
   Spotify alt — existing songLinkRow), focus / melody / harmonization /
   pass-goal lines, **"Reveal progression"** tap-to-reveal spoiler, and a
   **▶ Hear the loop** button where the spoiler is a clean 4-bar diatonic
   loop (played via the existing library player, fixed key C/Am).
5. **Track B**: 8 deep weeks — performer/recording, assigned section, target,
   melody target, "not graded" list, lab drills, passing standard, spoiler.
6. Train-ride drills card (appendix B) + Month 1/2 exam-target tables.
7. "Revision notes" card — the bullet list above, so the delta vs the PDFs
   is visible in-app.

## Feature 2 — "Theory" top-level menu entry

New `Sheet.Theory` + `TabDest.Theory` (lives in More by default; label
"Theory", subtitle "Interval song references & reference sheets — expanding").
First section: **Interval song references** —

- **Descending** (from the PDF, verbatim): 12 rows, m2→P8, each with the
  ascending-inversion note, reference song (tappable), cue text.
- **Ascending** (generated, canonical picks): Jaws (m2), Happy Birthday (M2),
  Greensleeves (m3), When the Saints (M3), Here Comes the Bride (P4),
  The Simpsons (TT), Twinkle Twinkle (P5), Manhã de Carnaval (m6),
  My Bonnie (M6), Somewhere/West Side Story (m7), Take On Me (M7, plus
  construct: octave up then m2 down), Somewhere Over the Rainbow (P8).
- Footer note: octave-complement rule (maj↔min switch, perfect stays
  perfect, tritone self-inverts).

Integration into ear training: the **Intervals** trainer gets a
"♪ Song refs" button opening the same reference as an overlay/dialog.

## Data model (theory module, mirrored Kotlin/TS)

- `EarWorkout` (`EarWorkout.kt` / `earWorkout.ts`): `WorkoutSong`,
  `WorkoutSession(number, week, title, song?, songNote?, focus, melody,
  harmonization, passGoal, spoiler, loop: Progression?)`,
  `DeepWeek(week, songTitle, artist, recording, section, target,
  melodyTarget, notGraded[], labDrills[], passing, spoiler, loop?)`,
  plus GLOBAL_RULES / SESSION_FRAME / HARMONIZATION / TRAIN_DRILLS /
  MONTH1_EXAM / MONTH2_EXAM / REVISION_NOTES.
- `IntervalSongs` (`IntervalSongs.kt` / `intervalSongs.ts`):
  `IntervalSongRef(ascending, interval, inversion, song, artist?, cue)` +
  `DESCENDING`/`ASCENDING` lists (12 each).
- Unit test: 32 sessions, 4 per week × 8; 8 deep weeks; all loops valid
  Progressions; 12+12 interval refs covering all 12 interval names.

## Out of scope (later)

- Progress tracking / checkboxes per session.
- Playing non-diatonic spoilers (secondary-dominant chains) — the library
  player only takes diatonic `Progression`s today.
- More Theory-tab sections (the tab is built to expand).

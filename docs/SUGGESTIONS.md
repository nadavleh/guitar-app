# Chorect — improvement backlog (GUI / UX / functionality)

Written 2026-07-02 after a whole-app review pass (v1.9.6). Grounded in the current
code; roughly priority-ordered within each section.

## Timing & audio (highest impact)

1. **Audio-clock sequencer scheduling.** Both loopers advance with
   `delay()` (app) / `setTimeout` (web), so every slot inherits scheduler jitter
   (±several ms, worse under load). The fix is lookahead scheduling: compute each
   hit's exact sample position in the mixer and enqueue buffers slightly ahead of
   time (the mixer already mixes sample-accurately). This also unlocks #2.
2. **Pre-beat scheduling for crescendo voices.** The onset-alignment pass (v1.9.6)
   puts 90 % of peak ~3 ms after the trigger for every *hit* sample. The five
   genuine crescendo articulations (maracas/caxixi/cabasa FX shakes, vibraslap pan,
   guiro long scrape) still build after the trigger — musically a player starts
   those *before* the beat so the accent lands on it. With lookahead scheduling,
   store a per-voice `peakOffsetMs` (the pipeline already measures it) and start
   those buffers early so their peak lands on the grid line.
3. **Metronome / count-in** for the drum machine and ear-training loops.

## Drum machine

4. **Per-cell velocity/accent** (e.g. second tap level or long-press slider) —
   samba grooves live on accents; all hits are equal volume today.
5. **Pattern chaining (A/B) and song mode** — chain saved beats into a sequence.
6. **Tap tempo** next to the BPM slider.
7. **Haptic feedback** on cell taps (app).
8. **Humanize control** — a few ms of random timing/velocity jitter.

## Ear training

9. **Progress/stats screen.** Challenge scores are already persisted
   (`ChallengeScore` in the repository) but only partially surfaced — a simple
   history view (per sub-mode, over time) would close the loop.
10. **Intervals: melodic vs harmonic mode** (play both notes together as an option),
    and per-interval stats to focus drilling on weak intervals.
11. **Aug/Dim & Inversions: challenge-mode fretboard toggle** (practice has it now;
    challenge views don't).
12. **Adjustable answer time / auto-advance** in challenges.

## Decompose

13. **Audition buttons per group** — play shell only / triad only / full chord from
    the summary card (today it's one fixed shell→triad sequence).
14. **Upper-triad inversions** — show the 3 inversions of the upper triad on the
    neck (that's how comping voicings actually get grabbed).
15. **Link to Loop** — "send this voicing to the looper" like ear-training has.

## Fretboard / general UI

16. **Fret-number strip** under the neck (portrait zoom makes position hard to tell).
17. **Left-hand fingering overlay toggle** on chord shapes in Decompose (ShapeCard
    already computes fingerings elsewhere).
18. **Dark/light theme toggle** (web already has CSS variables; app is dark-only).
19. **Reduce full-screen re-renders on the web** (each state change rebuilds the
    whole tool DOM; fine today, but sliders/drags fight it — an element-level
    patch for hot paths like BPM/volume sliders would feel smoother).

## Known trade-offs / debt

- The `dim` CAGED grip is really a dim7 voicing; ear-training panels filter the
  extra tone at display time, but the main Fretboard chord tool still shows it.
  Fix the template (verify all 5 CAGED shapes for dim) when convenient.
- Drum-machine legacy saved beats from before v1.8.0 (enum-ordered format) no
  longer decode (accepted during development).
- Emoji sweep: transport ▶/⏹ and nav are line icons now; typographic glyphs
  (♭ ♯ ♪ ◀ ▶ steppers) remain by design.

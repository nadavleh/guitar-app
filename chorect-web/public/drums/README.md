# Drum voice samples (optional)

Drop one-shot `.wav` files here to replace the synthesized percussion voices. Each file is
loaded once and resampled to the audio engine's rate; any voice **without** a file falls back
to the built-in synth, so you only need to provide the ones you want to override.

**Use your own recordings or properly-licensed royalty-free samples** (e.g. freesound.org
clips under a permissive license). Don't use audio ripped from videos/recordings you don't have
the rights to.

## File names — `<instrument>_<voice>.wav`

| Instrument | File | Voice |
|---|---|---|
| Surdo | `surdo_0.wav` | open (ring) |
| | `surdo_1.wav` | muted bass |
| | `surdo_2.wav` | tap |
| Tamborim | `tamborim_0.wav` | clack |
| | `tamborim_1.wav` | muted clack |
| | `tamborim_2.wav` | tap |
| Pandeiro | `pandeiro_0.wav` | bass (open) |
| | `pandeiro_1.wav` | bass (muted) |
| | `pandeiro_2.wav` | slap |
| | `pandeiro_3.wav` | **jingle (platinela)** |
| | `pandeiro_4.wav` | **jingle hi** |
| Agogô | `agogo_0.wav` | low bell |
| | `agogo_1.wav` | high bell |

For just the pandeiro shimmer, drop in `pandeiro_3.wav` and `pandeiro_4.wav` — short one-shot
jingle hits work best (the looper retriggers them per sixteenth). Keep them trimmed to a tight
onset so each hit lands on the beat.

After adding files, reload the app and open a Drums voice popup: each voice shows **sample** or
**synth** so you can confirm the file loaded.

## Cutting samples from a recording

With the dev server running (`launch-web.bat`), open **http://localhost:5317/tool.html** — an
in-browser editor where you load an audio file, drag across the waveform to select a hit, loop
it to audition, and **Download** the selection as a trimmed one-shot WAV under the right name.
Save the download into this folder. (Use audio you have the rights to.)

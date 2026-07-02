#!/usr/bin/env python3
"""Align drum one-shot onsets to the grid (fixes the 'bloom' lateness).

Problem: some samples take tens of ms to reach their peak, so at 0 swing they
sound late. Rule (per Nadav): the point where the signal first reaches ~90% of
its peak should land on the beat — i.e. at (almost) the start of the buffer,
since the sequencer triggers buffers exactly on the grid.

Two-tier treatment:
  * "Bloomy hits" (reach 90% of peak within ONSET_CAP_MS): trim everything
    before (t90 - PREROLL_MS) and fade the first PREROLL_MS in linearly, so
    90%-of-peak lands ~PREROLL_MS after the trigger (inaudibly close).
  * Crescendo articulations (shake rolls / long scrapes, t90 > ONSET_CAP_MS):
    the build IS the sound — only leading near-silence (< SILENCE_FRAC of peak)
    is trimmed; the roll is kept intact. (Placing a roll's PEAK on the beat
    would require pre-beat scheduling — see the suggestions doc.)

Idempotent: re-running trims ~nothing. Writes in place (mono 44.1k PCM_16) to
app/src/main/assets/drums; mirror to chorect-web/public/drums afterwards.
"""
import os
import numpy as np
import soundfile as sf

DRUMS = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "drums")
SR = 44100
ONSET_CAP_MS = 150.0    # above this, treat as a crescendo articulation
PREROLL_MS = 3.0        # attack kept before the 90% point (fade-in region)
SILENCE_FRAC = 0.02     # leading "silence" threshold for crescendo trims
PEAK_FRAC = 0.9         # the user's rule: 90% of peak on the beat


def envelope(x: np.ndarray, sr: int, win_ms: float = 2.0) -> np.ndarray:
    n = max(int(sr * win_ms / 1000), 1)
    a = np.abs(x)
    pad = np.concatenate([a, np.zeros(n)])
    return np.array([pad[i:i + n].max() for i in range(len(a))])


def align(x: np.ndarray, sr: int) -> tuple[np.ndarray, str]:
    env = envelope(x, sr)
    peak = env.max()
    if peak <= 0:
        return x, "silent?"
    t90_idx = int(np.argmax(env >= PEAK_FRAC * peak))
    pre = int(sr * PREROLL_MS / 1000)
    if t90_idx / sr * 1000 <= ONSET_CAP_MS:
        cut = max(t90_idx - pre, 0)
        mode = f"hit  (cut {cut / sr * 1000:5.1f} ms)"
    else:
        sil_idx = int(np.argmax(env >= SILENCE_FRAC * peak))
        cut = max(sil_idx - pre, 0)
        mode = f"roll (cut {cut / sr * 1000:5.1f} ms silence)"
    y = x[cut:].copy()
    fade = min(pre, len(y))
    if cut > 0 and fade > 0:
        y[:fade] *= np.linspace(0.0, 1.0, fade)
    return y, mode


def main():
    changed = 0
    for f in sorted(os.listdir(DRUMS)):
        if not f.endswith(".wav"):
            continue
        path = os.path.join(DRUMS, f)
        x, sr = sf.read(path)
        if x.ndim > 1:
            x = x.mean(axis=1)
        y, mode = align(np.asarray(x, dtype=np.float64), sr)
        if len(y) != len(x):
            sf.write(path, y.astype(np.float32), sr, subtype="PCM_16")
            changed += 1
            print(f"{f:<18} {mode}")
    print(f"\naligned {changed} files (others already tight)")


if __name__ == "__main__":
    main()

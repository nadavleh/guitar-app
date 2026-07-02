#!/usr/bin/env python3
"""Measure the 'bloom' of every bundled drum one-shot: how many ms into the file
the signal reaches 90% of its peak amplitude (envelope-wise). A sample whose
90%-of-peak point is late sounds behind the beat even at 0 swing."""
import os
import sys
import numpy as np
import soundfile as sf

DRUMS = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "drums")


def envelope(x: np.ndarray, sr: int, win_ms: float = 2.0) -> np.ndarray:
    """Rectified moving-max envelope (robust to single-sample spikes)."""
    n = max(int(sr * win_ms / 1000), 1)
    a = np.abs(x)
    # moving max via strided cummax windows (simple, files are short)
    pad = np.concatenate([a, np.zeros(n)])
    return np.array([pad[i:i + n].max() for i in range(len(a))])


def onset_ms(path: str, frac: float = 0.9) -> tuple[float, float, float]:
    x, sr = sf.read(path)
    if x.ndim > 1:
        x = x.mean(axis=1)
    env = envelope(x, sr)
    peak = env.max()
    if peak <= 0:
        return (0.0, 0.0, 0.0)
    i90 = int(np.argmax(env >= frac * peak))
    ipk = int(np.argmax(env))
    return (i90 / sr * 1000, ipk / sr * 1000, len(x) / sr * 1000)


def main():
    rows = []
    for f in sorted(os.listdir(DRUMS)):
        if not f.endswith(".wav"):
            continue
        t90, tpk, dur = onset_ms(os.path.join(DRUMS, f))
        rows.append((f, t90, tpk, dur))
    rows.sort(key=lambda r: -r[1])
    print(f"{'file':<18} {'t(90% peak) ms':>14} {'t(peak) ms':>11} {'dur ms':>8}")
    late = 0
    for f, t90, tpk, dur in rows:
        flag = "  <-- LATE" if t90 > 10 else ""
        if t90 > 10:
            late += 1
        print(f"{f:<18} {t90:>14.1f} {tpk:>11.1f} {dur:>8.0f}{flag}")
    print(f"\n{late} of {len(rows)} samples reach 90% of peak later than 10 ms")
    sys.exit(0)


if __name__ == "__main__":
    main()

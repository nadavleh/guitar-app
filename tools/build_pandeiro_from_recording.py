#!/usr/bin/env python3
"""Build the PANDEIRO one-shot WAVs from NADAV'S OWN RECORDING (replaces the
freesound set of v2.30.0 — fully license-free, one instrument, one session).

Source: tools/recordings/pandeiro_articulations.wav — 4 consecutive strokes of
each articulation, ~750 ms apart, in this order:
    bass open x4, bass closed x4, finger open x4, finger closed x4,
    heel open x4, heel closed x4, slap x4
(One weak flam occurs inside the finger-open group; hits with a follower within
0.4 s or a peak far below the group's are skipped when picking takes.)

Voice map (PercussionCatalog's 8-voice Pandeiro):
    pandeiro_0  bass (open)      pandeiro_4  finger (open)
    pandeiro_1  bass (closed)    pandeiro_5  finger (closed)
    pandeiro_2  slap             pandeiro_6  heel (open)
    pandeiro_3  jingle (KEPT — freesound platinela, not in this recording)
    pandeiro_7  heel (closed)

Per group the best take = highest (decay x peak-closeness-to-group-max) with no
contamination. Same finishing as the other sample pipelines: mono 44.1 kHz,
trim trailing silence, normalize to -1 dBFS, onset-align, PCM_16, written to
BOTH the Android assets and chorect-web/public. Requires: numpy, soundfile.
"""
import os
import numpy as np
import soundfile as sf

from align_drum_onsets import align

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "recordings", "pandeiro_articulations.wav")
OUTS = [
    os.path.join(HERE, "..", "app", "src", "main", "assets", "drums"),
    os.path.join(HERE, "..", "chorect-web", "public", "drums"),
]
SR = 44100

GROUPS = ["bass_open", "bass_closed", "finger_open", "finger_closed",
          "heel_open", "heel_closed", "slap"]
VOICE_OF = {"bass_open": 0, "bass_closed": 1, "slap": 2,
            "finger_open": 4, "finger_closed": 5, "heel_open": 6, "heel_closed": 7}


def load_mono(path: str) -> np.ndarray:
    data, sr = sf.read(path, always_2d=True)
    mono = data.mean(axis=1).astype(np.float64)
    if sr != SR:
        n = int(round(len(mono) * SR / sr))
        mono = np.interp(np.linspace(0, len(mono), n, endpoint=False),
                         np.arange(len(mono)), mono)
    return mono


def find_onsets(mono: np.ndarray) -> list:
    """Hysteresis threshold crossing on a 5 ms envelope (tails don't retrigger)."""
    win = int(0.005 * SR)
    env = np.convolve(np.abs(mono), np.ones(win) / win, mode="same")
    high, low = 0.10 * env.max(), 0.05 * env.max()
    onsets, armed, last = [], True, -SR
    for i in range(len(env)):
        if armed and env[i] > high and i - last >= int(0.10 * SR):
            onsets.append(i)
            last = i
            armed = False
        elif not armed and env[i] < low:
            armed = True
    return onsets


def finish(mono: np.ndarray) -> np.ndarray:
    thr = 10 ** (-60 / 20)
    nz = np.where(np.abs(mono) > thr)[0]
    if len(nz):
        mono = mono[: min(len(mono), nz[-1] + int(0.01 * SR))]
    peak = float(np.max(np.abs(mono))) if len(mono) else 0.0
    if peak > 1e-6:
        mono = mono * (10 ** (-1 / 20) / peak)
    mono, _ = align(mono, SR)
    return mono.astype(np.float32)


def main():
    mono = load_mono(SRC)
    onsets = find_onsets(mono)
    peaks = [float(np.max(np.abs(mono[o:o + int(0.2 * SR)]))) for o in onsets]

    # Take = (onset, clean_len) where clean_len runs to the NEXT onset. A weak
    # flam (peak < 35% of the recording median) is dropped from the take list,
    # but still truncates its predecessor via clean_len.
    med = float(np.median(peaks))
    takes = []
    for k, o in enumerate(onsets):
        nxt = onsets[k + 1] if k + 1 < len(onsets) else len(mono)
        if peaks[k] < 0.35 * med:
            continue
        takes.append((o, nxt - o))
    if len(takes) != len(GROUPS) * 4:
        raise SystemExit(f"expected {len(GROUPS) * 4} real hits, found {len(takes)} "
                         f"({len(onsets)} raw onsets) — check the recording/threshold")

    for g, name in enumerate(GROUPS):
        group = takes[g * 4:(g + 1) * 4]
        gpeak = max(float(np.max(np.abs(mono[o:o + n]))) for o, n in group)
        # Best take: longest uncontaminated ring among hits near the group's level.
        def score(t):
            o, n = t
            p = float(np.max(np.abs(mono[o:o + n])))
            if p < 0.7 * gpeak or n < int(0.4 * SR):
                return -1
            return n
        best = max(group, key=score)
        o, n = best
        cut = mono[max(0, o - int(0.005 * SR)): o + n]
        buf = finish(cut)
        v = VOICE_OF[name]
        for out in OUTS:
            os.makedirs(out, exist_ok=True)
            dst = os.path.normpath(os.path.join(out, f"pandeiro_{v}.wav"))
            sf.write(dst, buf, SR, subtype="PCM_16")
        print(f"pandeiro_{v}.wav  {name:14s} take@{o/SR:6.2f}s  {len(buf)/SR:.2f}s")
    print("Done (pandeiro_3 jingle left untouched).")


if __name__ == "__main__":
    main()

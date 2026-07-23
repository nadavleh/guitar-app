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
RETA = os.path.join(HERE, "recordings", "pandeiro_reta_bars.wav")
OUTS = [
    os.path.join(HERE, "..", "app", "src", "main", "assets", "drums"),
    os.path.join(HERE, "..", "chorect-web", "public", "drums"),
]
SR = 44100

# Takes adopted from the IN-CONTEXT reta recording after A/B listening
# (build_pandeiro_ab_page.py): voice → onset time in RETA, seconds. These
# override the isolated-recording group picks.
RETA_OVERRIDES = {
    4: 23.59,   # finger (open) — Nadav's pick: brighter (7.6 kHz) than the isolated take
}

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


def extract_reta(t_sec: float) -> np.ndarray:
    """Cut the reta-recording hit whose onset is nearest `t_sec` (as labeled on
    the A/B page), ending at the next onset (where the next stroke choked it),
    with a 10 ms fade so the truncation doesn't click."""
    mono = load_mono(RETA)
    win = int(0.004 * SR)
    env = np.convolve(np.abs(mono), np.ones(win) / win, mode="same")
    high, low = 0.035 * env.max(), 0.5 * 0.035 * env.max()
    onsets, armed, last = [], True, -SR
    for i in range(len(env)):
        if armed and env[i] > high and i - last >= int(0.08 * SR):
            onsets.append(i)
            last = i
            armed = False
        elif not armed and env[i] < low:
            armed = True
    k = min(range(len(onsets)), key=lambda j: abs(onsets[j] / SR - t_sec))
    if abs(onsets[k] / SR - t_sec) > 0.06:
        raise SystemExit(f"no reta onset near {t_sec}s (nearest {onsets[k]/SR:.2f}s)")
    o = onsets[k]
    nxt = onsets[k + 1] if k + 1 < len(onsets) else len(mono)
    seg = mono[max(0, o - int(0.003 * SR)):nxt].copy()
    f = min(int(0.010 * SR), len(seg))
    seg[-f:] *= np.linspace(1, 0, f)
    return seg


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
        v = VOICE_OF[name]
        if v in RETA_OVERRIDES:
            buf = finish(extract_reta(RETA_OVERRIDES[v]))
            src_note = f"RETA@{RETA_OVERRIDES[v]}s"
        else:
            best = max(group, key=score)
            o, n = best
            buf = finish(mono[max(0, o - int(0.005 * SR)): o + n])
            src_note = f"take@{o/SR:.2f}s"
        for out in OUTS:
            os.makedirs(out, exist_ok=True)
            dst = os.path.normpath(os.path.join(out, f"pandeiro_{v}.wav"))
            sf.write(dst, buf, SR, subtype="PCM_16")
        print(f"pandeiro_{v}.wav  {name:14s} {src_note}  {len(buf)/SR:.2f}s")
    print("Done (pandeiro_3 jingle left untouched).")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Slice CANDIDATE pandeiro one-shots from the in-context reta recording and
build a local A/B-test HTML page (candidates vs the currently bundled WAVs).

Source: tools/recordings/pandeiro_reta_bars.wav — 24 bars of 2/4 at 80 BPM,
one hit per 16th, repeating 8-slot cycle:
    bass closed, finger closed, heel closed, slap,
    bass open, finger open, heel open, finger open
(some bars straight, some swung ~50% — slot assignment walks onset gaps, so
swing and slow tempo drift don't break it).

For each articulation the page offers the top takes by ring length (gap to the
next onset) with a sane peak; *open* taps are ranked by spectral centroid
(brightness) first — Nadav found the isolated finger-open take too low-pitched.
Output: pandeiro_ab_test.html (self-contained, WAVs embedded as data URIs).
Candidates are labeled by their onset time in the recording; to adopt one, its
onset time goes into build_pandeiro_from_recording-style extraction.
"""
import base64
import io
import os
import sys
import numpy as np
import soundfile as sf

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "recordings", "pandeiro_reta_bars.wav")
BUNDLED = os.path.join(HERE, "..", "app", "src", "main", "assets", "drums")
OUT_HTML = os.path.join(os.path.expanduser("~"), "Desktop", "pandeiro_ab_test.html")
SR = 44100

CYCLE = ["bass_closed", "finger_closed", "heel_closed", "slap",
         "bass_open", "finger_open", "heel_open", "finger_open"]
VOICE_OF = {"bass_open": 0, "bass_closed": 1, "slap": 2,
            "finger_open": 4, "finger_closed": 5, "heel_open": 6, "heel_closed": 7}
BRIGHT_FIRST = {"finger_open", "heel_open"}   # rank by brightness, not ring
N_CANDIDATES = 4


def load_mono(path):
    data, sr = sf.read(path, always_2d=True)
    mono = data.mean(axis=1).astype(np.float64)
    if sr != SR:
        n = int(round(len(mono) * SR / sr))
        mono = np.interp(np.linspace(0, len(mono), n, endpoint=False),
                         np.arange(len(mono)), mono)
    return mono


def find_onsets(mono, high_frac=0.035, low_frac=0.5, min_sep_s=0.08):
    win = int(0.004 * SR)
    env = np.convolve(np.abs(mono), np.ones(win) / win, mode="same")
    high = high_frac * env.max()
    low = low_frac * high
    onsets, armed, last = [], True, -SR
    for i in range(len(env)):
        if armed and env[i] > high and i - last >= int(min_sep_s * SR):
            onsets.append(i)
            last = i
            armed = False
        elif not armed and env[i] < low:
            armed = True
    return onsets


def centroid_hz(seg):
    n = min(len(seg), 4096)
    if n < 256:
        return 0.0
    spec = np.abs(np.fft.rfft(seg[:n] * np.hanning(n)))
    freqs = np.fft.rfftfreq(n, 1 / SR)
    return float((spec * freqs).sum() / max(spec.sum(), 1e-9))


def wav_data_uri(seg):
    peak = float(np.max(np.abs(seg))) if len(seg) else 0.0
    if peak > 1e-6:
        seg = seg * (10 ** (-1 / 20) / peak)
    # 10 ms fade-out so grid-truncated cuts don't click when auditioned.
    f = min(int(0.010 * SR), len(seg))
    if f > 0:
        seg = seg.copy()
        seg[-f:] *= np.linspace(1, 0, f)
    bio = io.BytesIO()
    sf.write(bio, seg.astype(np.float32), SR, subtype="PCM_16", format="WAV")
    return "data:audio/wav;base64," + base64.b64encode(bio.getvalue()).decode()


def main():
    mono = load_mono(SRC)
    onsets = find_onsets(mono)
    print(f"{len(onsets)} onsets detected", file=sys.stderr)

    # Sequential slot assignment: expected 16th = median small gap; a long gap
    # advances by however many slots it spans (missed soft hits stay missing).
    gaps = np.diff([o / SR for o in onsets])
    med16 = float(np.median(gaps[(gaps > 0.1) & (gaps < 0.3)]))
    slot_of = {0: 0}
    slot = 0
    for k in range(1, len(onsets)):
        slot += max(1, int(round(gaps[k - 1] / med16)))
        slot_of[k] = slot

    # Collect takes per articulation: (onset_idx, gap_s, peak, centroid).
    takes = {a: [] for a in set(CYCLE)}
    for k, o in enumerate(onsets):
        nxt = onsets[k + 1] if k + 1 < len(onsets) else len(mono)
        seg = mono[o:nxt]
        art = CYCLE[slot_of[k] % 8]
        takes[art].append((k, (nxt - o) / SR, float(np.max(np.abs(seg))), centroid_hz(seg)))

    rows = []
    for art in ["bass_open", "bass_closed", "slap", "finger_open", "finger_closed",
                "heel_open", "heel_closed"]:
        cands = takes[art]
        peaks = sorted(c[2] for c in cands)
        p75 = peaks[int(0.75 * (len(peaks) - 1))] if peaks else 0
        ok = [c for c in cands if c[2] >= 0.55 * p75]
        key = (lambda c: (c[3], c[1])) if art in BRIGHT_FIRST else (lambda c: (c[1], c[3]))
        best = sorted(ok, key=key, reverse=True)[:N_CANDIDATES]
        cells = []
        cur = os.path.join(BUNDLED, f"pandeiro_{VOICE_OF[art]}.wav")
        cells.append(("CURRENT (bundled)", wav_data_uri(load_mono(cur))))
        for k, gap, peak, cen in best:
            o = onsets[k]
            nxt = onsets[k + 1] if k + 1 < len(onsets) else len(mono)
            seg = mono[max(0, o - int(0.003 * SR)):nxt]
            cells.append((f"@{o/SR:.2f}s · ring {gap*1000:.0f}ms · {cen:.0f}Hz",
                          wav_data_uri(seg)))
        rows.append((art, cells))
        print(f"{art}: {len(cands)} takes, offering {len(best)}", file=sys.stderr)

    html = ["<!doctype html><meta charset='utf-8'><title>Pandeiro A/B</title>",
            "<style>body{font-family:sans-serif;max-width:900px;margin:20px auto}"
            "h2{margin:18px 0 6px}button{margin:3px;padding:8px 12px;border-radius:8px;"
            "border:1px solid #999;cursor:pointer;background:#f4f4f4}"
            "button.cur{background:#cde7e4;font-weight:bold}</style>",
            "<h1>Pandeiro one-shot A/B</h1>",
            "<p>Tap to play. Candidates come from the reta recording (in-context strokes"
            " — tails end where the next stroke choked them). To adopt one, tell Claude"
            " the articulation + the @time label.</p><script>let a=new Audio();"
            "function p(u){a.pause();a=new Audio(u);a.play()}</script>"]
    for art, cells in rows:
        html.append(f"<h2>{art.replace('_', ' ')}</h2>")
        for i, (label, uri) in enumerate(cells):
            cls = " class='cur'" if i == 0 else ""
            html.append(f"<button{cls} onclick=\"p('{uri}')\">{label}</button>")
    with open(OUT_HTML, "w", encoding="utf-8") as f:
        f.write("\n".join(html))
    print(OUT_HTML)


if __name__ == "__main__":
    main()

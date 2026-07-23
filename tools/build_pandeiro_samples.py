#!/usr/bin/env python3
"""Build the PANDEIRO one-shot WAVs from freely-licensed recordings of a REAL
goat-leather pandeiro (replacing the Ableton Latin Percussion previews, which
sounded thin for this instrument).

Sources (direct, no-auth Freesound preview MP3s, verified 2026-07-23):
  * Paulo Goncalves' "Tambourine" pack (freesound.org/people/Paulo_Gon%C3%A7alves/
    packs/11555/) - real pandeiro: low/bass hit, platinela (jingle) hits, trill.
    License: CC-BY 4.0 -> attribution "Paulo_Goncalves via freesound.org".
  * katusm's pandeiro slap (freesound.org/people/katusm/sounds/527967/).
    License: CC0.

Voice map (must match PercussionCatalog's Pandeiro in Percussion.kt):
  pandeiro_0  bass (open)   <- 182567 pandeiro_low
  pandeiro_1  bass (muted)  <- 182567 with a fast post-onset decay envelope
                               (the pack has no muted bass; a damped copy of the
                               SAME drum keeps the tone consistent)
  pandeiro_2  slap          <- 527967 katusm slap
  pandeiro_3  jingle        <- 182566 platinela 01

Same pipeline as build_drum_samples.py: mono, 44.1 kHz, trim trailing silence,
peak-normalize to -1 dBFS, onset-align; PCM_16 WAVs written to BOTH the Android
assets and chorect-web/public. The WAVs are committed - re-run only to change
sources. Requires: numpy, soundfile (>=0.12, for MP3 decode), internet.
"""
import io
import os
import urllib.request
import numpy as np
import soundfile as sf

from align_drum_onsets import align

HERE = os.path.dirname(__file__)
OUTS = [
    os.path.join(HERE, "..", "app", "src", "main", "assets", "drums"),
    os.path.join(HERE, "..", "chorect-web", "public", "drums"),
]
SR = 44100

CDN = "https://cdn.freesound.org/previews"
SOURCES = {
    "low":  f"{CDN}/182/182567_1446493-hq.mp3",   # CC-BY Paulo_Goncalves
    "slap": f"{CDN}/527/527967_9719882-hq.mp3",   # CC0 katusm
    "plat": f"{CDN}/182/182566_1446493-hq.mp3",   # CC-BY Paulo_Goncalves
}


def fetch(url: str) -> np.ndarray:
    req = urllib.request.Request(url, headers={"User-Agent": "chorect-sample-build"})
    with urllib.request.urlopen(req, timeout=60) as r:
        raw = r.read()
    data, sr = sf.read(io.BytesIO(raw), always_2d=True)
    mono = data.mean(axis=1).astype(np.float64)
    if sr != SR:
        n = int(round(len(mono) * SR / sr))
        mono = np.interp(np.linspace(0, len(mono), n, endpoint=False),
                         np.arange(len(mono)), mono)
    return mono


def finish(mono: np.ndarray) -> np.ndarray:
    """Trim trailing silence, normalize to -1 dBFS, onset-align (same as the
    Latin Percussion pipeline so all drums trigger identically)."""
    thr = 10 ** (-60 / 20)
    nz = np.where(np.abs(mono) > thr)[0]
    if len(nz):
        mono = mono[: min(len(mono), nz[-1] + int(0.01 * SR))]
    peak = float(np.max(np.abs(mono))) if len(mono) else 0.0
    if peak > 1e-6:
        mono = mono * (10 ** (-1 / 20) / peak)
    mono, _ = align(mono, SR)
    return mono.astype(np.float32)


def muted(mono: np.ndarray) -> np.ndarray:
    """Damped copy: keep the attack (~25 ms), then decay fast (tau 30 ms) - a
    hand-muted head stops ringing almost immediately."""
    out = mono.copy()
    onset = int(np.argmax(np.abs(out) > 0.5 * np.max(np.abs(out))))
    hold = onset + int(0.025 * SR)
    t = np.arange(len(out) - hold, dtype=np.float64)
    out[hold:] *= np.exp(-t / (0.030 * SR))
    return out[: hold + int(0.150 * SR)]


def main():
    low = fetch(SOURCES["low"])
    voices = {
        0: finish(low),
        1: finish(muted(low)),
        2: finish(fetch(SOURCES["slap"])),
        3: finish(fetch(SOURCES["plat"])),
    }
    for out in OUTS:
        os.makedirs(out, exist_ok=True)
        for v, buf in voices.items():
            dst = os.path.normpath(os.path.join(out, f"pandeiro_{v}.wav"))
            sf.write(dst, buf, SR, subtype="PCM_16")
            print(f"  {dst}  ({len(buf)/SR:.2f}s)")
    print("Done. Attribution: pandeiro low/jingle by Paulo_Goncalves via "
          "freesound.org (CC-BY 4.0); slap by katusm via freesound.org (CC0).")


if __name__ == "__main__":
    main()

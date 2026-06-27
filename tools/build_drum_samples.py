#!/usr/bin/env python3
"""Build the drum-machine one-shot WAVs from the Ableton *Latin Percussion* pack.

The full-quality `.aif` files in the pack are Ableton-proprietary-encoded
(FORM/AIFC/`able` codec) and not decodable by normal tools. The per-hit *preview*
Oggs under `…/Previews/Drums/Drum Hits/<category>/<name>.adg.ogg` are clean
one-shots in standard Ogg Vorbis, and are the same source the original four
instruments' WAVs came from.

This script maps each (instrument id, voice index) -> preview ogg, decodes it
(soundfile / libsndfile), downmixes to mono, trims trailing silence, peak-
normalizes lightly, and writes `app/src/main/assets/drums/<id>_<voice>.wav` as
mono 44.1 kHz PCM_16 — the exact format the app's WavDecoder already loads.

The generated WAVs are committed, so the normal build needs no Python. Re-run
this only when changing the instrument catalog. Requires: numpy, soundfile.
"""
import os
import sys
import numpy as np
import soundfile as sf

PACK = r"C:\Users\Nadav\Documents\Ableton\Factory Packs\Latin Percussion"
HITS = os.path.join(PACK, "Ableton Folder Info", "Previews", "Drums", "Drum Hits")
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "drums")
SR = 44100

# instrument id -> list of preview-ogg basenames (category is resolved by search),
# in the SAME voice order as PercussionCatalog in Percussion.kt. The existing four
# (surdo/tamborim/pandeiro/agogo) are intentionally NOT regenerated here.
VOICES = {
    "cuica":     ["Cuica 1 Low", "Cuica 1 High"],
    "caxixi":    ["Caxixi 1 Open", "Caxixi 1 Hand", "Caxixi 1 FX"],
    "shaker":    ["Shaker 1", "Shaker 2"],
    "guiro":     ["Guiro A Down", "Guiro A Up", "Guiro A Long"],
    "claves":    ["Claves A 1", "Claves A 2"],
    "cowbell":   ["Cowbell A 1", "Cowbell A 2"],
    "triangle":  ["Triangle A Open", "Triangle A Mute"],
    "apito":     ["Samba Whstl 1 L", "Samba Whstl 1 M", "Samba Whstl 1 H"],
    "cabasa":    ["Cabasa 1 Short", "Cabasa 1 Long", "Cabasa 1 FX"],
    "conga":     ["Conga A Open", "Conga A Mute", "Conga A Slap Open", "Conga A Tip"],
    "quinto":    ["Quinto A Open", "Quinto A Mute", "Quinto A Slap Open"],
    "tumba":     ["Tumba A Open", "Tumba A Mute", "Tumba A Slap"],
    "bongo":     ["Bongo A H Open 1F", "Bongo A L Open 1F", "Bongo A H Rim", "Bongo A L Slap"],
    "timbales":  ["Timbales A H Open", "Timbales A L Open", "Timb A H Cascara", "Timbales A H Rim"],
    "maracas":   ["Maracas", "Maracas FX"],
    "vibraslap": ["Vibraslap A", "Vibraslap A Pan"],
    "castanet":  ["Castanet A", "Castanet A Roll"],
    "woodblock": ["Wood Block B Hi", "Wood Block B Mid", "Wood Block B Low"],
    "cymbal":    ["Cymbal A Bell", "Cymbal A Stick Opn"],
    "gong":      ["Gong A 1"],
}


def index_oggs():
    """basename (no .adg.ogg) -> full path, across all Drum Hits categories."""
    idx = {}
    for root, _dirs, files in os.walk(HITS):
        for f in files:
            if f.endswith(".adg.ogg"):
                idx[f[: -len(".adg.ogg")]] = os.path.join(root, f)
    return idx


def process(path):
    data, sr = sf.read(path, always_2d=True)
    mono = data.mean(axis=1).astype(np.float64)
    if sr != SR:  # previews are 44.1k, but be safe with a simple linear resample
        n = int(round(len(mono) * SR / sr))
        mono = np.interp(np.linspace(0, len(mono), n, endpoint=False),
                         np.arange(len(mono)), mono)
    # Trim trailing silence below -60 dBFS, keep a tiny tail.
    thr = 10 ** (-60 / 20)
    nz = np.where(np.abs(mono) > thr)[0]
    if len(nz):
        mono = mono[: min(len(mono), nz[-1] + int(0.01 * SR))]
    # Light peak normalize to -1 dBFS (only attenuate-or-boost toward target).
    peak = float(np.max(np.abs(mono))) if len(mono) else 0.0
    if peak > 1e-6:
        mono = mono * (10 ** (-1 / 20) / peak)
    return mono.astype(np.float32)


def main():
    idx = index_oggs()
    os.makedirs(OUT, exist_ok=True)
    missing, written = [], 0
    for inst_id, names in VOICES.items():
        for v, name in enumerate(names):
            src = idx.get(name)
            if not src:
                missing.append(f"{inst_id}[{v}] <- {name!r}")
                continue
            buf = process(src)
            dst = os.path.normpath(os.path.join(OUT, f"{inst_id}_{v}.wav"))
            sf.write(dst, buf, SR, subtype="PCM_16")
            written += 1
            print(f"  {inst_id}_{v}.wav  <-  {name}  ({len(buf)/SR:.2f}s)")
    print(f"\nWrote {written} WAVs to {os.path.normpath(OUT)}")
    if missing:
        print("MISSING (no matching preview ogg):", file=sys.stderr)
        for m in missing:
            print("  " + m, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

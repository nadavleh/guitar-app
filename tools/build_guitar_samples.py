#!/usr/bin/env python3
"""Build sampled-guitar banks for the app from FreePats SFZ sources.

Reads each instrument's .sfz (the authoritative pitch->sample mapping), takes ONE
sample per recorded root pitch (pitch_keycenter, else key), and writes a normalized,
trimmed mono 44.1 kHz 16-bit WAV named <inst>_<midi>.wav into BOTH the Android assets
dir and the web public dir, plus a per-instrument manifest (<inst>.json = sorted list
of the recorded root MIDI notes) and a LICENSES.txt. The app's runtime resampler
(SampleSource / playbackRate) pitch-shifts the nearest recorded root to any target
note, so we keep the packs' native roots rather than pre-gridding.

Sources (downloaded/extracted under tools/_guitar_src/_dl/):
  acoustic (steel) : FreePats FS Seagull Steel-String  -- GPLv3 + audio exception
  nylon (classical): FreePats Spanish Classical Guitar  -- CC0-1.0
  electric (clean) : FreePats FSBS Clean Electric Guitar -- CC0-1.0

Run: python tools/build_guitar_samples.py
"""
import glob
import json
import os
import re
import sys

import math

import numpy as np
import soundfile as sf
from scipy.signal import filtfilt, resample_poly

SR = 44100
TAIL_SEC = 2.5
FADE_SEC = 0.03
PEAK_DBFS = -1.0

# Per-instrument tone shaping (baked into the samples — the app has no runtime EQ yet).
# Each entry is a list of RBJ peaking filters (center_hz, Q, gain_dB).
# nylon reads a touch muffled/boxy, so cut the low-mids to open it up.
EQ = {
    "nylon": [(600.0, 0.8, -4.0)],
}


def peaking(fc, q, gain_db):
    """RBJ peaking-EQ biquad coefficients (b, a)."""
    a_ = 10 ** (gain_db / 40.0)
    w0 = 2 * math.pi * fc / SR
    alpha = math.sin(w0) / (2 * q)
    cw = math.cos(w0)
    b = [1 + alpha * a_, -2 * cw, 1 - alpha * a_]
    a = [1 + alpha / a_, -2 * cw, 1 - alpha / a_]
    return b, a

HERE = os.path.dirname(os.path.abspath(__file__))
DL = os.path.join(HERE, "_guitar_src", "_dl")
OUT_DIRS = [
    os.path.join(HERE, "..", "app", "src", "main", "assets", "guitar"),
    os.path.join(HERE, "..", "chorect-web", "public", "guitar"),
]

# inst id -> (glob for its .sfz, license line)
INSTRUMENTS = {
    "acoustic": (
        os.path.join(DL, "steel_full_x", "**", "*.sfz"),
        "acoustic (steel-string): FreePats FS Seagull Steel-String Guitar (FlameStudios) "
        "-- GPLv3+ with the FreePats instrument exception -- "
        "https://freepats.zenvoid.org/Guitar/steel-acoustic-guitar.html",
    ),
    "nylon": (
        os.path.join(DL, "nylon_x", "**", "*.sfz"),
        "nylon (classical): FreePats Spanish Classical Guitar -- CC0-1.0 -- "
        "https://freepats.zenvoid.org/Guitar/acoustic-guitar.html",
    ),
    "electric": (
        os.path.join(DL, "electric_x", "**", "*.sfz"),
        "electric (clean): FreePats FSBS Clean Electric Guitar -- CC0-1.0 -- "
        "https://freepats.zenvoid.org/ElectricGuitar/clean-electric-guitar.html",
    ),
}

TOKEN = re.compile(r"(<\w+>)|(\w+)=([^\s]+)")


def parse_sfz(sfz_path):
    """Return {rootMidi: absolute_sample_path}, one entry per recorded root (first wins)."""
    base = os.path.dirname(sfz_path)
    text = open(sfz_path, encoding="utf-8", errors="ignore").read()
    # Strip // and <...> style comments minimally (FreePats sfz uses // comments).
    text = re.sub(r"//[^\n]*", "", text)
    roots = {}
    cur = {}

    def flush(region):
        smp = region.get("sample")
        if not smp:
            return
        root = region.get("pitch_keycenter", region.get("key", region.get("lokey")))
        if root is None:
            return
        try:
            root = int(root)
        except ValueError:
            return  # note-name keys not expected in these packs
        if root not in roots:
            roots[root] = os.path.normpath(os.path.join(base, smp.replace("\\", "/")))

    for m in TOKEN.finditer(text):
        header, op, val = m.group(1), m.group(2), m.group(3)
        if header:
            if header in ("<region>", "<group>", "<global>", "<control>", "<master>"):
                if header == "<region>":
                    flush(cur)
                    cur = {}
        elif op:
            cur[op] = val
    flush(cur)
    return roots


def process(x, sr, eq=()):
    if x.ndim > 1:
        x = x.mean(axis=1)
    x = x.astype(np.float64)
    if sr != SR:
        from math import gcd
        g = gcd(int(sr), SR)
        x = resample_poly(x, SR // g, int(sr) // g)
    # trim leading silence
    peak = np.max(np.abs(x)) if x.size else 0.0
    if peak > 0:
        nz = np.where(np.abs(x) > 0.01 * peak)[0]
        if nz.size:
            x = x[nz[0]:]
    x = x[: int(TAIL_SEC * SR)]
    # tone shaping (zero-phase, so no transient smearing)
    for fc, q, gain_db in eq:
        if x.size > 27:
            b, a = peaking(fc, q, gain_db)
            x = filtfilt(b, a, x)
    peak = np.max(np.abs(x)) or 1.0
    x = x * (10 ** (PEAK_DBFS / 20) / peak)          # normalize
    f = min(int(FADE_SEC * SR), x.size)
    if f:
        x[-f:] *= np.linspace(1.0, 0.0, f)
    return x.astype(np.float32)


def main():
    for d in OUT_DIRS:
        os.makedirs(d, exist_ok=True)
    license_lines = ["Guitar sample banks — sources and licenses:", ""]
    total_bytes = 0
    for inst, (sfz_glob, lic) in INSTRUMENTS.items():
        matches = glob.glob(sfz_glob, recursive=True)
        if not matches:
            print(f"!! {inst}: no SFZ found at {sfz_glob}", file=sys.stderr)
            sys.exit(1)
        roots = parse_sfz(matches[0])
        written = []
        for root, path in sorted(roots.items()):
            if not os.path.exists(path):
                print(f"   {inst}: missing sample {path} (skip)", file=sys.stderr)
                continue
            data, sr = sf.read(path, always_2d=False)
            y = process(data, sr, EQ.get(inst, ()))
            for d in OUT_DIRS:
                fp = os.path.join(d, f"{inst}_{root}.wav")
                sf.write(fp, y, SR, subtype="PCM_16")
                total_bytes += os.path.getsize(fp)
            written.append(root)
        written.sort()
        for d in OUT_DIRS:
            json.dump(written, open(os.path.join(d, f"{inst}.json"), "w"))
        license_lines.append(lic)
        print(f"{inst}: {len(written)} pitches (roots {written[0]}..{written[-1]})")
    for d in OUT_DIRS:
        open(os.path.join(d, "LICENSES.txt"), "w").write("\n".join(license_lines) + "\n")
    # total_bytes counts both output dirs; per-platform size is half.
    print(f"per-platform bank size: {total_bytes / 2 / 1e6:.1f} MB")


if __name__ == "__main__":
    main()

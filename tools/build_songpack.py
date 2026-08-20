#!/usr/bin/env python3
"""Build the sideloadable song pack - the chewed-down chords+lyrics the app reads.

    docs/songs/captured-chords.json  (chords, in the repo)
    docs/songs/captured-lyrics.json  (lyric text, gitignored)
        ->  <outdir>/index.json      manifest: one light row per song
        ->  <outdir>/songs/<id>.json one file per song, chords already aligned

The pack is written OUTSIDE the repo on purpose. It carries lyric text, and the
repo is public: nothing here may ever land in a commit, on GitHub Pages, or in the
APK. The app loads the pack at runtime from wherever the device owner puts it.

"Chewed down" means the app does no parsing work at load time. Every line already
carries its chords with their column offsets, so the viewer only has to render.

Run:  python tools/build_songpack.py "<outdir>"
"""

import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHORDS = os.path.join(ROOT, "docs", "songs", "captured-chords.json")
LYRICS = os.path.join(ROOT, "docs", "songs", "captured-lyrics.json")

# Bumped whenever the on-disk shape changes, so the app can tell a stale cache from
# a current one without re-reading every file.
PACK_FORMAT = 1


def slug(text):
    """A stable, filesystem-safe id. Non-Latin titles (Hebrew) keep their own
    characters where possible and fall back to a hash-free transliteration of the
    codepoints, so the id stays stable across runs."""
    s = text.strip().lower()
    s = re.sub(r"[\\/:*?\"<>|]", "", s)
    s = re.sub(r"\s+", "-", s)
    s = re.sub(r"-{2,}", "-", s)
    return s.strip("-") or "untitled"


PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
SHARP = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
# Scale degrees of the major and natural-minor scales, in semitones.
MAJOR = [0, 2, 4, 5, 7, 9, 11]
MINOR = [0, 2, 3, 5, 7, 8, 10]
# Diatonic triad quality per degree — a chord matching these is strong evidence.
MAJOR_TRIADS = ["", "m", "m", "", "", "m", "dim"]
MINOR_TRIADS = ["m", "dim", "", "m", "m", "", ""]


def root_pc(sym):
    """Pitch class of a chord symbol's root, or None."""
    m = re.match(r"^([A-G])([#b]?)", sym.strip())
    if not m:
        return None
    pc = PC[m.group(1)]
    if m.group(2) == "#":
        pc += 1
    elif m.group(2) == "b":
        pc -= 1
    return pc % 12


def is_minor_chord(sym):
    """True when the symbol is a minor-ish triad (m, m7, m6...) but not maj/dim."""
    body = re.sub(r"^[A-G][#b]?", "", sym.split("/")[0])
    return bool(re.match(r"^(m|min)(?!aj)", body))


def detect_key(symbols):
    """The key whose diatonic set best explains the chords.

    Scores every major and minor key by how many chord OCCURRENCES fall inside it,
    with a bonus when the chord's own major/minor quality matches the quality that
    degree should have, and a further bonus for the tonic opening or closing the
    song — which is what actually distinguishes a key from its relative."""
    roots = [(root_pc(s), is_minor_chord(s)) for s in symbols]
    roots = [(r, m) for r, m in roots if r is not None]
    if not roots:
        return None
    first, last = roots[0][0], roots[-1][0]
    best, best_score = None, -1
    for tonic in range(12):
        for scale, triads, minor_key in ((MAJOR, MAJOR_TRIADS, False),
                                         (MINOR, MINOR_TRIADS, True)):
            degrees = {(tonic + d) % 12: i for i, d in enumerate(scale)}
            score = 0
            for r, is_min in roots:
                if r not in degrees:
                    continue
                score += 2
                want = triads[degrees[r]]
                if (want == "m") == is_min:
                    score += 1
            # The tonic at either end is the strongest single cue.
            if last == tonic:
                score += 6
            if first == tonic:
                score += 3
            if score > best_score:
                best_score = score
                best = SHARP[tonic] + ("m" if minor_key else "")
    return best


def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else None
    if not outdir:
        raise SystemExit("usage: build_songpack.py <outdir>")
    if os.path.abspath(outdir).startswith(os.path.abspath(ROOT) + os.sep):
        raise SystemExit(
            "refusing to write the pack inside the repo (%s): it holds lyric text and\n"
            "this repo is public. Choose a directory outside the working tree." % ROOT)

    chords = json.load(io.open(CHORDS, encoding="utf-8"))
    lyrics = {}
    if os.path.exists(LYRICS):
        lyrics = json.load(io.open(LYRICS, encoding="utf-8"))

    songsdir = os.path.join(outdir, "songs")
    if not os.path.isdir(songsdir):
        os.makedirs(songsdir)

    index, seen = [], {}
    for key, c in sorted(chords.items(), key=lambda kv: (kv[1]["artist"].lower(),
                                                         kv[1]["title"].lower())):
        base = slug((c["artist"] + "-" + c["title"]) if c["artist"] else c["title"])
        sid = base
        n = 2
        while sid in seen:
            sid = "%s-%d" % (base, n)
            n += 1
        seen[sid] = True

        lyr = lyrics.get(key)
        rtl = bool(lyr and lyr.get("rtl"))
        sections = []
        if lyr:
            # Chords already sit on their columns; the app just renders them.
            for s in lyr["sections"]:
                sections.append({
                    "label": s["label"],
                    "lines": [{"chords": [[col, sym] for col, sym in ln["chords"]],
                               "lyric": ln["lyric"]} for ln in s["lines"]],
                })
        else:
            for s in c["sections"]:
                sections.append({
                    "label": s["label"],
                    "lines": [{"chords": [[0, sym] for sym in s["chords"]], "lyric": ""}],
                })

        allsyms = [x for s in c["sections"] for x in s["chords"]]
        detected = detect_key(allsyms)

        song = {
            "id": sid,
            "title": c["title"],
            "artist": c["artist"],
            "key": detected,
            "capo": c.get("capo", 0),
            "rtl": rtl,
            "url": c.get("url", ""),
            "site": c.get("site", ""),
            "sections": sections,
        }
        io.open(os.path.join(songsdir, sid + ".json"), "w", encoding="utf-8").write(
            json.dumps(song, ensure_ascii=False, separators=(",", ":")))

        index.append({
            "id": sid, "title": c["title"], "artist": c["artist"],
            "key": detected, "capo": c.get("capo", 0), "rtl": rtl,
            "chords": sum(len(s["chords"]) for s in c["sections"]),
            "lyrics": sum(1 for s in sections for ln in s["lines"] if ln["lyric"]),
        })

    manifest = {
        "format": PACK_FORMAT,
        "count": len(index),
        # A cheap change-detector: the app compares this against its cached copy and
        # only re-reads the directory when it differs.
        "digest": "%d-%d" % (len(index), sum(r["chords"] + r["lyrics"] for r in index)),
        "songs": index,
    }
    io.open(os.path.join(outdir, "index.json"), "w", encoding="utf-8").write(
        json.dumps(manifest, ensure_ascii=False, indent=1))

    withlyr = sum(1 for r in index if r["lyrics"] > 0)
    print("pack written to %s" % outdir)
    print("  %d songs, %d with lyric lines, digest %s" % (len(index), withlyr, manifest["digest"]))
    print("  index.json + songs/*.json")


if __name__ == "__main__":
    main()

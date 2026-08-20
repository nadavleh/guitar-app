#!/usr/bin/env python3
"""Extract chord data (and lyrics) from the saved chord-site page captures.

    <captures>/text/*.txt   ->  docs/songs/captured-chords.json   (chords, per song)
                            ->  docs/songs/captured-lyrics.json   (lyric text, per song)

The two outputs are written SEPARATELY on purpose: chords are facts about the
harmony and belong in the repo, lyrics are the rights-encumbered part. Which of
the two the generator consumes is one switch in build_song_library.py.

Three parser branches, picked by the capture's Source URL:

  ug      tabs.ultimate-guitar.com - "[Verse]" labels, chord line over lyric line,
          left-to-right. ~64 captures. Some older tabs carry no bracket labels;
          those fall back to blank-line-delimited blocks.
  tab4u   www.tab4u.com - RIGHT-TO-LEFT Hebrew. Same chord-over-lyric idea, but
          the chord line is space-padded to exactly the lyric line's width, and
          section labels are Hebrew words. ~34 captures.
  generic cifraclub / nagnu / guitartabsexplorer / bossanovaguitar - chord-over-
          lyric with no reliable section labels. ~8 captures.

Pages that are not songs at all (tuner sites, YouTube, backing-track and e-book
sites) are skipped by domain - they are already bare rows in bookmarks.json.

Run:  python tools/import_song_captures.py "<captures dir>"
"""

import io
import json
import os
import re
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_CHORDS = os.path.join(ROOT, "docs", "songs", "captured-chords.json")
OUT_LYRICS = os.path.join(ROOT, "docs", "songs", "captured-lyrics.json")

# Domains that are tools//video/reference pages, not songs.
SKIP_DOMAINS = {
    "www.youtube.com", "www.fbbts.com", "music-ebooks.ru", "archive.org",
    "tonesavvy.com", "www.tabinator.com", "www.oldtimejam.com",
    "tuner-online.com", "www.onlinepianist.com", "www.get-tuned.com",
    "chordify.net", "www.musixmatch.com", "genius.com", "mojim.com",
}
BOOKMARKS_BY_URL = {}
UG_DOMAINS = {"tabs.ultimate-guitar.com", "es.ultimate-guitar.com"}
TAB4U_DOMAINS = {"www.tab4u.com"}

# A chord symbol as ChordLibrary parses them: root, optional accidental, quality,
# extension, optional slash bass. Kept deliberately tight - a loose pattern turns
# ordinary lyric words ("A", "Am", "Be") into phantom chords.
CHORD = re.compile(
    r"^[A-G](?:#|b)?"
    r"(?:maj|min|m|M|dim|aug|sus|add|°|\+)?"
    r"(?:[0-9]{1,2})?"
    r"(?:(?:sus|add|maj|b|#)[0-9]{1,2})*"
    r"(?:/[A-G](?:#|b)?)?$"
)
HEBREW = re.compile(r"[\u0590-\u05FF]")
# Bracketed section label, e.g. "[Verse 2]", "[Chorus]", "[Instrumental]".
BRACKET = re.compile(r"^\s*\[([^\]]{1,40})\]\s*$")
# tab4u section labels.
HEB_LABELS = {
    "פזמון": "Chorus", "בית": "Verse", "מעבר": "Bridge", "סיום": "Outro",
    "פתיחה": "Intro", "מבוא": "Intro", "גשר": "Bridge", "סולו": "Solo",
}


def chord_tokens(line):
    """The chord symbols on a line, or None if the line is not a chord line.

    A chord line is one whose every token parses as a chord. Blank lines and
    lines holding any non-chord word are rejected."""
    toks = line.split()
    if not toks:
        return None
    if not all(CHORD.match(t) for t in toks):
        return None
    return toks


def chord_columns(line):
    """(column, symbol) for each chord, so the over-the-lyric alignment survives."""
    out, col = [], 0
    for m in re.finditer(r"\S+", line):
        out.append((m.start(), m.group()))
    return out


def read_capture(path):
    """The 6-line capture header plus the body lines."""
    txt = io.open(path, encoding="utf-8", errors="replace").read()
    lines = txt.split("\n")
    head = {}
    for l in lines[:8]:
        m = re.match(r"^(Bookmark title|Source URL|Final URL|Page title|HTTP status|Captured):\s*(.*)$", l)
        if m:
            head[m.group(1)] = m.group(2).strip()
    return head, lines


def domain_of(url):
    return re.sub(r"^https?://", "", url or "").split("/")[0]


def sheet_span(lines, pair_rtl=False):
    """The line range holding the actual chord sheet.

    Chord lines are clustered: the sheet is the densest run of them. Footers and
    nav chrome contain the odd stray chord-looking token ("E", "A") but never a
    dense cluster, so this drops them without a site-specific footer list."""
    idx = [i for i, l in enumerate(lines) if chord_tokens(l)]
    if not idx:
        return None
    blocks, cur = [], [idx[0]]
    for a, b in zip(idx, idx[1:]):
        if b - a <= 8:
            cur.append(b)
        else:
            blocks.append(cur)
            cur = [b]
    blocks.append(cur)
    best = max(blocks, key=len)
    return best[0], best[-1]


def lyric_after(lines, i):
    """The lyric belonging to the chord line at [i].

    The first capture put the lyric on the very next line; the refetched pages keep
    a blank line between the two. So skip a single blank, but no more - two blanks
    mean the chord line stands alone (an intro or a turnaround) and inventing a
    lyric for it would pull in the next section's first line."""
    for j in (i + 1, i + 2):
        if j >= len(lines):
            return ""
        nxt = lines[j]
        if nxt.strip() == "":
            continue
        if chord_tokens(nxt) or BRACKET.match(nxt):
            return ""
        return nxt.rstrip()
    return ""


def parse_ug(lines):
    """Ultimate-Guitar: '[Label]' headers, chord line directly above its lyric line."""
    span = sheet_span(lines)
    if not span:
        return []
    lo, hi = span
    # A bracket label often sits a line or two above the first chord line.
    lo = max(0, lo - 3)
    sections, cur = [], {"label": "Verse", "chords": [], "lines": []}
    for i in range(lo, min(hi + 2, len(lines))):
        l = lines[i]
        m = BRACKET.match(l)
        if m:
            if cur["chords"]:
                sections.append(cur)
            cur = {"label": m.group(1).strip(), "chords": [], "lines": []}
            continue
        ct = chord_tokens(l)
        if ct:
            lyric = lyric_after(lines, i)
            cur["chords"].extend(ct)
            cur["lines"].append({"chords": chord_columns(l), "lyric": lyric})
    if cur["chords"]:
        sections.append(cur)
    return sections


def parse_tab4u(lines):
    """tab4u: RTL. The chord line is padded to the EXACT width of its lyric line.

    That width match is the reliable signal - it is what separates a real chord
    line from a stray chord-looking token in the page chrome. Chord ORDER is kept
    as it appears in the capture; see the rtl note in the report."""
    span = sheet_span(lines)
    if not span:
        return []
    lo, hi = span
    sections, cur = [], {"label": "Verse", "chords": [], "lines": []}
    i = lo
    while i <= min(hi + 1, len(lines) - 1):
        l = lines[i]
        stripped = l.strip().rstrip(":")
        if stripped in HEB_LABELS:
            if cur["chords"]:
                sections.append(cur)
            cur = {"label": HEB_LABELS[stripped], "chords": [], "lines": []}
            i += 1
            continue
        ct = chord_tokens(l)
        if ct:
            nxt = lines[i + 1] if i + 1 < len(lines) else ""
            paired = len(nxt) == len(l) and HEBREW.search(nxt)
            cur["chords"].extend(ct)
            cur["lines"].append({
                "chords": chord_columns(l),
                "lyric": nxt.rstrip() if paired else "",
                "rtl": True,
            })
            i += 2 if paired else 1
            continue
        i += 1
    if cur["chords"]:
        sections.append(cur)
    return sections


def parse_generic(lines):
    """cifraclub / nagnu / guitartabsexplorer / bossanovaguitar - no section labels."""
    span = sheet_span(lines)
    if not span:
        return []
    lo, hi = span
    cur = {"label": "Song", "chords": [], "lines": []}
    for i in range(lo, min(hi + 2, len(lines))):
        ct = chord_tokens(lines[i])
        if not ct:
            continue
        lyric = lyric_after(lines, i)
        cur["chords"].extend(ct)
        cur["lines"].append({"chords": chord_columns(lines[i]), "lyric": lyric})
    return [cur] if cur["chords"] else []


def meta_ug(lines):
    """UG prints 'Capo:2nd fret' / 'Capo:No capo' and 'Tuning:' in its header block."""
    capo, tuning = 0, ""
    for l in lines[:80]:
        s = l.strip()
        m = re.match(r"^Capo:\s*(.*)$", s)
        if m:
            d = re.search(r"(\d+)", m.group(1))
            capo = int(d.group(1)) if d else 0
        m = re.match(r"^Tuning:\s*(.*)$", s)
        if m:
            tuning = m.group(1).strip()
    return capo, tuning


def title_artist(head, fname):
    """Artist/title. The UG page-title form first, then tab4u's Hebrew breadcrumb,
    then Cifra Club's. Falls back to the bookmark title with no artist."""
    pt = head.get("Page title", "") or head.get("Bookmark title", "")
    m = re.match(r"^(.*?)\s+(?:CHORDS|TAB)\b.*?\bby\s+(.*?)\s*(?:@|for guitar|$)", pt, re.I)
    if m:
        return m.group(1).strip().title(), m.group(2).strip()
    m = re.match(r"^אקורדים לשיר\s+(.*?)\s+-\s+(.*?)\s*\|", pt)
    if m:
        return m.group(1).strip(), m.group(2).strip()
    m = re.match(r"^(.*?)\s+-\s+(.*?)\s+-\s+Cifra Club", pt)
    if m:
        return m.group(1).strip(), m.group(2).strip()
    return (head.get("Bookmark title", "") or fname).strip(), ""


def guess_key(chords):
    """Weak heuristic: the closing chord when it is also the most frequent, else the
    most frequent. Emitted as 'key_guess' ONLY - never as an authoritative key."""
    if not chords:
        return None
    last = chords[-1]
    common = Counter(chords).most_common(1)[0][0]
    return last if last == common else common


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else None
    if not src or not os.path.isdir(os.path.join(src, "text")):
        raise SystemExit("usage: import_song_captures.py <captures dir containing text/>")
    tdir = os.path.join(src, "text")
    global BOOKMARKS_BY_URL
    bm = json.load(io.open(os.path.join(ROOT, "docs", "songs", "bookmarks.json"),
                           encoding="utf-8"))
    BOOKMARKS_BY_URL = {}
    for s in bm:
        BOOKMARKS_BY_URL.setdefault(s["url"], s)
    chords_out, lyrics_out, report = {}, {}, []
    for fname in sorted(os.listdir(tdir)):
        if not fname.endswith(".txt"):
            continue
        head, lines = read_capture(os.path.join(tdir, fname))
        url = head.get("Source URL", "")
        dom = domain_of(url)
        if dom in SKIP_DOMAINS:
            report.append({"file": fname, "domain": dom, "status": "skipped-not-a-song"})
            continue
        if dom in UG_DOMAINS:
            branch, sections = "ug", parse_ug(lines)
            capo, tuning = meta_ug(lines)
        elif dom in TAB4U_DOMAINS:
            branch, sections = "tab4u", parse_tab4u(lines)
            capo, tuning = 0, ""
        else:
            branch, sections = "generic", parse_generic(lines)
            capo, tuning = 0, ""
        title, artist = title_artist(head, fname)
        allch = [c for s in sections for c in s["chords"]]
        # Join to the bookmark by URL, not by title text: the URL is exact, whereas
        # matching "Artist|Title" would require reproducing each bookmark's own
        # casing and spelling. Emit under the BOOKMARK's key so the merge in
        # build_song_library.py (which hard-fails on an unknown key) always lands.
        bm = BOOKMARKS_BY_URL.get(url) or BOOKMARKS_BY_URL.get(head.get("Final URL", ""))
        if bm:
            key = bm["artist"] + "|" + bm["title"]
            title, artist = bm["title"], bm["artist"]
        else:
            key = artist + "|" + title
            report.append({"file": fname, "domain": dom, "branch": branch,
                           "status": "no-bookmark-match", "key": key})
        if not sections:
            report.append({"file": fname, "domain": dom, "branch": branch,
                           "status": "no-chords-found"})
            continue
        chords_out[key] = {
            "title": title, "artist": artist, "url": url, "site": dom,
            "branch": branch, "capo": capo, "tuning": tuning,
            "key_guess": guess_key(allch), "source": "capture",
            "sections": [{"label": s["label"], "chords": s["chords"]} for s in sections],
        }
        lyrics_out[key] = {
            "title": title, "artist": artist, "rtl": branch == "tab4u",
            "sections": [{"label": s["label"], "lines": s["lines"]} for s in sections],
        }
        report.append({
            "file": fname, "domain": dom, "branch": branch, "status": "ok",
            "sections": len(sections), "chords": len(allch),
            "distinct": len(set(allch)), "capo": capo,
            "key_guess": guess_key(allch),
            "lyric_lines": sum(1 for s in sections for l in s["lines"] if l["lyric"]),
        })
    io.open(OUT_CHORDS, "w", encoding="utf-8").write(
        json.dumps(chords_out, ensure_ascii=False, indent=2))
    io.open(OUT_LYRICS, "w", encoding="utf-8").write(
        json.dumps(lyrics_out, ensure_ascii=False, indent=2))
    io.open(os.path.join(ROOT, "docs", "songs", "capture-report.json"), "w",
            encoding="utf-8").write(json.dumps(report, ensure_ascii=False, indent=2))
    ok = [r for r in report if r["status"] == "ok"]
    print("songs with chords: %d   skipped: %d   no-chords: %d" % (
        len(ok),
        sum(1 for r in report if r["status"] == "skipped-not-a-song"),
        sum(1 for r in report if r["status"] == "no-chords-found")))
    for b in ("ug", "tab4u", "generic"):
        sel = [r for r in ok if r.get("branch") == b]
        if sel:
            print("  %-8s %3d songs %6d chords %5d lyric lines" % (
                b, len(sel), sum(r["chords"] for r in sel),
                sum(r["lyric_lines"] for r in sel)))


if __name__ == "__main__":
    main()

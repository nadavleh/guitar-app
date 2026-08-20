#!/usr/bin/env python3
"""Re-fetch the bookmarked chord pages the first crawl failed to capture.

The original capture lost 101 pages to HTTP 429 (rate limiting), not to
client-side rendering as was first assumed: the sheet is present in the served
HTML, inside the page's embedded JSON store, marked up as [ch]Am[/ch]. So a plain
polite fetch is enough - no browser, no OCR of the screenshots (which only show
the throttle page anyway).

Writes capture files in EXACTLY the format tools/import_song_captures.py already
reads, so the recovered songs go through the same parser as the first batch.

    python tools/refetch_missing.py <outdir> [--limit N] [--delay SECONDS]

Politeness is the whole point of this script: one request at a time, a real
delay between them, and exponential backoff that gives up rather than hammering
when the server says 429. Rate limiting is what broke the first run.
"""

import html
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BOOKMARKS = os.path.join(ROOT, "docs", "songs", "bookmarks.json")
CAPTURED = os.path.join(ROOT, "docs", "songs", "captured-chords.json")

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

# Sites whose pages are not songs; skipped rather than fetched.
SKIP_HOSTS = (
    "youtube.com", "fbbts.com", "music-ebooks.ru", "archive.org", "tonesavvy.com",
    "tabinator.com", "oldtimejam.com", "tuner-online.com", "onlinepianist.com",
    "get-tuned.com", "chordify.net", "musixmatch.com", "genius.com", "mojim.com",
)


def fetch(url, timeout=30):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml",
        "Accept-Language": "en-US,en;q=0.9",
    })
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.getcode(), r.read().decode("utf-8", "replace"), r.geturl()


def extract_ug_sheet(page):
    """The chord sheet out of an Ultimate-Guitar page.

    The sheet lives in a JSON blob in a data-content attribute, HTML-escaped, with
    chords wrapped in [ch]..[/ch]. Unescape, pull the wiki_tab content, then strip
    the chord tags - which leaves precisely the chord-over-lyric plain text the
    first capture produced, so the existing parser needs no new branch."""
    m = re.search(r'data-content="(.*?)"\s*></div>', page, re.S)
    blob = html.unescape(m.group(1)) if m else None
    content = None
    if blob:
        try:
            data = json.loads(blob)
            content = (data.get("store", {}).get("page", {}).get("data", {})
                       .get("tab_view", {}).get("wiki_tab", {}).get("content"))
        except Exception:
            content = None
    if not content:
        # Fall back to the raw markup if the JSON shape ever moves.
        m = re.search(r'\[tab\](.*?)\[/tab\]', page, re.S)
        content = m.group(1) if m else None
    if not content:
        return None
    text = content.replace("\\r\\n", "\n").replace("\\n", "\n")
    text = re.sub(r"\[/?ch\]", "", text)
    text = re.sub(r"\[/?tab\]", "", text)
    return html.unescape(text)


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: refetch_missing.py <outdir> [--limit N] [--delay S]")
    outdir = sys.argv[1]
    limit = None
    delay = 7.0
    for i, a in enumerate(sys.argv):
        if a == "--limit":
            limit = int(sys.argv[i + 1])
        if a == "--delay":
            delay = float(sys.argv[i + 1])

    bookmarks = json.load(io.open(BOOKMARKS, encoding="utf-8"))
    have = set()
    if os.path.exists(CAPTURED):
        have = {v["url"] for v in json.load(io.open(CAPTURED, encoding="utf-8")).values()}

    todo = [s for s in bookmarks
            if s["url"] not in have and not any(h in s["site"] for h in SKIP_HOSTS)]
    # Ultimate-Guitar first: that is where the 102 real losses are, and its sheet
    # extraction is the one that works. Other hosts each need their own reader.
    todo.sort(key=lambda s: 0 if "ultimate-guitar.com" in s["site"] else 1)
    if limit:
        todo = todo[:limit]

    tdir = os.path.join(outdir, "text")
    if not os.path.isdir(tdir):
        os.makedirs(tdir)

    ok = failed = empty = 0
    for n, s in enumerate(todo, 1):
        url = s["url"]
        backoff = delay
        code = body = final = None
        for attempt in range(4):
            try:
                code, body, final = fetch(url)
                break
            except urllib.error.HTTPError as e:
                code = e.code
                if code == 429:
                    # Back off hard: this is exactly what broke the first crawl.
                    time.sleep(backoff)
                    backoff *= 2
                    continue
                break
            except Exception:
                time.sleep(backoff)
                backoff *= 2
        if code != 200 or not body:
            failed += 1
            print("  [%3d/%d] HTTP %s  %s" % (n, len(todo), code, s["title"][:48]))
            time.sleep(delay)
            continue

        sheet = extract_ug_sheet(body) if "ultimate-guitar.com" in s["site"] else None
        if not sheet:
            empty += 1
            print("  [%3d/%d] no sheet found  %s" % (n, len(todo), s["title"][:48]))
            time.sleep(delay)
            continue

        # Same 6-line header the first capture wrote, so import_song_captures.py
        # reads these files without a new code path.
        safe = re.sub(r'[\\/:*?"<>|]', "", s["title"])[:80] or "untitled"
        header = (
            "Bookmark title: %s\nSource URL: %s\nFinal URL: %s\nPage title: %s\n"
            "HTTP status: 200\nCaptured: refetch\n\n" % (
                s["title"], url, final or url,
                "%s CHORDS by %s @ Ultimate-Guitar.Com" % (s["title"], s["artist"]))
        )
        io.open(os.path.join(tdir, "R%03d - %s.txt" % (n, safe)), "w",
                encoding="utf-8").write(header + sheet)
        ok += 1
        print("  [%3d/%d] ok  %s" % (n, len(todo), s["title"][:48]))
        time.sleep(delay)

    print("\nrefetched %d, no-sheet %d, failed %d, of %d attempted" %
          (ok, empty, failed, len(todo)))


if __name__ == "__main__":
    main()

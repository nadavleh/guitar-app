#!/usr/bin/env python3
"""Import a Chrome bookmarks folder into docs/songs/bookmarks.json.

    python tools/import_bookmarks.py [--folder-id 15] [--profile Default]

Reads Chrome's local `Bookmarks` JSON (nothing network-bound, nothing uploaded),
pulls one folder, normalises each entry to {title, artist, url, site}, de-dupes and
sorts. Run tools/build_song_library.py afterwards to regenerate the two ports.

Only titles, artists and URLs are taken - this never opens the bookmarked pages.

Title/artist recovery, in order:
  1. Ultimate-Guitar bookmark titles: "SONG CHORDS (ver 2) by ARTIST @ Ultimate-Guitar.Com"
  2. the URL slug, e.g. /adoniran-barbosa/prova-de-carinho/ -> Adoniran Barbosa / Prova De Carinho
  3. the raw bookmark name, trimmed at the first separator
Sites that shout their titles (UG) get title-cased; a leading "(1) " is dropped.
"""

import argparse
import io
import json
import os
import re
from urllib.parse import urlparse, unquote

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "songs", "bookmarks.json")
DEFAULT_CHROME = os.path.join(
    os.path.expanduser("~"), "AppData", "Local", "Google", "Chrome", "User Data")

# Slugs that are the site's own words, not the artist.
SLUG_NOISE = {"tab", "tabs", "chords", "cifra", "print", "en", "es", "pt"}


def clean(s):
    if not s:
        return ""
    s = re.sub(r"^\(\d+\)\s*", "", s).strip()
    if s.isupper() or s.islower():
        s = " ".join(w.capitalize() if len(w) > 2 or w.lower() in ("a", "i") else w.lower()
                     for w in s.split())
    return s


def from_slug(url):
    """(artist, title) from the URL path, or ('', '')."""
    parts = [unquote(p) for p in urlparse(url).path.strip("/").split("/") if p]
    parts = [p for p in parts if p.lower() not in SLUG_NOISE]
    if not parts:
        return "", ""
    def words(p):
        p = re.sub(r"-(chords|tabs)(-\d+)?$", "", p)
        p = re.sub(r"\.(html?|php)$", "", p)
        return " ".join(w.capitalize() for w in re.split(r"[-_]+", p) if w)
    if len(parts) >= 2:
        return words(parts[-2]), words(parts[-1])
    return "", words(parts[-1])


def parse_entry(name, url):
    t = (name or "").strip()
    m = re.match(r"^(.*?)\s+(CHORDS|TABS|BASS TABS|UKULELE CHORDS|PRO|POWER TAB)\b.*?\bby\s+(.*?)\s*@", t, re.I)
    if m:
        return clean(m.group(3)), clean(m.group(1))
    a, s = from_slug(url)
    if s:
        return clean(a), clean(s)
    return "", clean(re.sub(r"\s*[-|@].*$", "", t)) or clean(t)


def find_folder(node, target):
    if str(node.get("id")) == str(target):
        return node
    for c in node.get("children", []):
        r = find_folder(c, target)
        if r:
            return r
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--folder-id", default="15", help="Chrome folder id (see chrome://bookmarks/?id=N)")
    ap.add_argument("--profile", default="Default")
    ap.add_argument("--chrome", default=DEFAULT_CHROME)
    args = ap.parse_args()

    path = os.path.join(args.chrome, args.profile, "Bookmarks")
    data = json.load(io.open(path, encoding="utf-8"))
    folder = None
    for root in data["roots"].values():
        if isinstance(root, dict):
            folder = folder or find_folder(root, args.folder_id)
    if folder is None:
        raise SystemExit(f"folder id {args.folder_id} not found in {path}")

    rows = []
    def collect(n):
        for c in n.get("children", []):
            if c.get("type") == "url":
                artist, title = parse_entry(c.get("name", ""), c.get("url", ""))
                rows.append({"title": title, "artist": artist, "url": c.get("url", ""),
                             "site": urlparse(c.get("url", "")).netloc.replace("www.", "")})
            elif c.get("type") == "folder":
                collect(c)
    collect(folder)

    seen, out = set(), []
    for r in rows:
        if not r["title"]:
            raise SystemExit(f"could not name a title for {r['url']}")
        k = (r["artist"].lower(), r["title"].lower())
        if k in seen:
            continue
        seen.add(k)
        out.append(r)
    out.sort(key=lambda r: (r["artist"].lower(), r["title"].lower()))

    json.dump(out, io.open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"folder '{folder.get('name')}' ({args.folder_id}): {len(rows)} bookmarks -> {len(out)} unique songs")
    print(f"  named from a page title: {sum(1 for r in out if r['artist'])}, from the URL alone: {sum(1 for r in out if not r['artist'])}")
    print(f"  -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Create three Spotify playlists from docs/progression_songs.md.

    earTraining#1  — Major + Minor (diatonic) progressions
    earTraining#2  — Advanced (non-diatonic) progressions
    earTraining#3  — Circle-of-fifths progressions

Songs are parsed live from ../docs/progression_songs.md (the same list shown in the
app's progression library), deduplicated per playlist, searched on Spotify, and added.

--------------------------------------------------------------------------------
ONE-TIME SETUP
--------------------------------------------------------------------------------
1. Create a Spotify app:  https://developer.spotify.com/dashboard  -> "Create app".
   Copy the Client ID and Client Secret.
2. In that app's settings, add this exact Redirect URI (Spotify now requires the
   127.0.0.1 loopback, NOT "localhost"):
        http://127.0.0.1:8888/callback
3. Install the client library:
        pip install spotipy
4. Provide your credentials. Either edit the CONFIG block below, or set env vars
   (spotipy reads these three names automatically):
        SPOTIPY_CLIENT_ID       = <your client id>
        SPOTIPY_CLIENT_SECRET   = <your client secret>
        SPOTIPY_REDIRECT_URI    = http://127.0.0.1:8888/callback

--------------------------------------------------------------------------------
RUN
--------------------------------------------------------------------------------
        python tools/build_spotify_playlists.py

A browser window opens once for you to authorize the app; the script then creates
the playlists in your account. Tracks it can't confidently match are printed and
written to tools/spotify_unmatched.txt so you can add them by hand.

Re-running creates a fresh set of playlists each time (Spotify allows duplicate
names); delete old ones manually if you don't want them.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

# Windows consoles default to cp1252, which can't encode many matched track names
# (Greek, accented, CJK…). Force UTF-8 so a print() never crashes the run.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

# --------------------------------------------------------------------------- #
# CONFIG — fill these in here, OR leave blank and use the SPOTIPY_* env vars.
# --------------------------------------------------------------------------- #
CLIENT_ID = ""       # e.g. "a1b2c3..."   (overrides SPOTIPY_CLIENT_ID if set)
CLIENT_SECRET = ""   # e.g. "d4e5f6..."   (overrides SPOTIPY_CLIENT_SECRET if set)
REDIRECT_URI = "http://127.0.0.1:8888/callback"

# Make private playlists (True) or public (False).
PLAYLISTS_PUBLIC = False

# Map of playlist name -> which parsed sections feed it.
PLAYLIST_SECTIONS = {
    "earTraining#1": ("major", "minor"),
    "earTraining#2": ("advanced",),
    "earTraining#3": ("circle",),
}
PLAYLIST_DESC = {
    "earTraining#1": "Diatonic (major + minor) progression songs from the Chorect ear-trainer.",
    "earTraining#2": "Advanced / non-diatonic progression songs (characteristic examples).",
    "earTraining#3": "Circle-of-fifths progression songs (characteristic examples).",
}

# Manual overrides: source (title, artist) -> exact Spotify track URI. Fuzzy search
# occasionally picks the wrong track (a cover/karaoke, a medley, the wrong artist, or
# something unrelated); pin those here so a re-run stays correct.
TRACK_OVERRIDES = {
    ("Baby", "Justin Bieber"): "spotify:track:0GUOuy3DhaFdr7YgAxslOE",             # was "GO BABY"
    ("Boulevard of Broken Dreams", "Green Day"): "spotify:track:1hwJKpe0BPUsq6UUrwBWTw",  # was a "Holiday / …" medley
    ("Love the Way You Lie", "Eminem ft. Rihanna"): "spotify:track:15JINEqzVMv3SvJTAXAKED",  # was a karaoke cover
    ("Summertime", "Miles Davis"): "spotify:track:25H0Wd1ugPRXGM2LlpjVXM",         # was listed under "George Gershwin"
    ("Cocaine", "Eric Clapton"): "spotify:track:2udGjDmpK1dH9VGyw7nrei",           # was J.J. Cale's version
    ("Salty Dog Blues", "Rev. Gary Davis"): "spotify:track:2OWLg4tyw9J50G49pS9hsa",  # was an unrelated song; Mississippi John Hurt (live)
    ("Super Mario Bros. (Level Complete)", "Koji Kondo"): "spotify:track:03fijJ2GEzZ92Zl5fuTxI7",  # was "Tabula Rasa"; Main Theme (fanfare isn't a track)
}

# read-private lets us find & remove same-named playlists from a previous run so
# re-running replaces them instead of piling up duplicates.
SCOPE = "playlist-modify-public playlist-modify-private playlist-read-private"

# Song lines look like:  "- Autumn Leaves — Nat King Cole"  (em dash separator).
SONG_RE = re.compile(r"^-\s+(.+?)\s+—\s+(.+?)\s*$")

REPO_ROOT = Path(__file__).resolve().parent.parent
SONGS_MD = REPO_ROOT / "docs" / "progression_songs.md"
UNMATCHED_OUT = Path(__file__).resolve().parent / "spotify_unmatched.txt"


def section_key(header: str) -> str | None:
    """Classify a top-level '## ...' header into a parse bucket."""
    h = header.lower()
    if "major" in h:
        return "major"
    if "minor" in h:
        return "minor"
    if "advanced" in h:
        return "advanced"
    if "circle" in h:
        return "circle"
    return None


def parse_songs(md_path: Path) -> dict[str, list[tuple[str, str]]]:
    """Return {section_key: [(title, artist), ...]} deduped within each section,
    preserving first-seen order."""
    buckets: dict[str, list[tuple[str, str]]] = {
        "major": [], "minor": [], "advanced": [], "circle": [],
    }
    seen: dict[str, set[tuple[str, str]]] = {k: set() for k in buckets}
    current: str | None = None

    for raw in md_path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip()
        if line.startswith("## "):          # top-level section
            current = section_key(line[3:])
            continue
        if current is None:
            continue
        m = SONG_RE.match(line)
        if not m:
            continue
        title, artist = m.group(1).strip(), m.group(2).strip()
        key = (title.lower(), artist.lower())
        if key in seen[current]:
            continue
        seen[current].add(key)
        buckets[current].append((title, artist))
    return buckets


def find_track_uri(sp, title: str, artist: str):
    """Search Spotify for the best match. Returns (uri, display) or (None, None)."""
    ov = TRACK_OVERRIDES.get((title, artist))
    if ov:
        return ov, "(manual override)"

    def esc(s: str) -> str:
        return s.replace('"', "")

    queries: list[str] = []
    if artist and artist.lower() != "traditional":
        queries.append(f'track:"{esc(title)}" artist:"{esc(artist)}"')
        queries.append(f"{title} {artist}")
    queries.append(title)  # title-only fallback (traditional / classical)

    for q in queries:
        try:
            res = sp.search(q=q, type="track", limit=1)
        except Exception as e:  # network / rate-limit — surface and move on
            print(f"    ! search error for {title!r}: {e}")
            continue
        items = res.get("tracks", {}).get("items", [])
        if items:
            it = items[0]
            disp = f"{it['name']} — {it['artists'][0]['name']}"
            return it["uri"], disp
    return None, None


def chunked(seq, n):
    for i in range(0, len(seq), n):
        yield seq[i:i + n]


def unfollow_existing(sp, user_id: str, names: set[str]) -> None:
    """Remove (unfollow) any of the user's own playlists whose name is in `names`,
    so a re-run replaces prior runs instead of leaving duplicates/empties behind."""
    to_remove: list[tuple[str, str]] = []
    results = sp.current_user_playlists(limit=50)
    while results:
        for pl in results.get("items", []):
            if pl and pl["owner"]["id"] == user_id and pl["name"] in names:
                to_remove.append((pl["name"], pl["id"]))
        results = sp.next(results) if results.get("next") else None
    for nm, pid in to_remove:
        sp.current_user_unfollow_playlist(pid)
        print(f"   (removed a previous '{nm}')")


def main() -> int:
    try:
        import spotipy
        from spotipy.oauth2 import SpotifyOAuth
    except ImportError:
        print("spotipy is not installed. Run:  pip install spotipy")
        return 1

    if not SONGS_MD.exists():
        print(f"Cannot find song source: {SONGS_MD}")
        return 1

    # Let inline CONFIG override env vars, but fall back to env if left blank.
    if CLIENT_ID:
        os.environ["SPOTIPY_CLIENT_ID"] = CLIENT_ID
    if CLIENT_SECRET:
        os.environ["SPOTIPY_CLIENT_SECRET"] = CLIENT_SECRET
    os.environ.setdefault("SPOTIPY_REDIRECT_URI", REDIRECT_URI)

    if not os.environ.get("SPOTIPY_CLIENT_ID") or not os.environ.get("SPOTIPY_CLIENT_SECRET"):
        print("Missing credentials. Set CLIENT_ID/CLIENT_SECRET in the script or the "
              "SPOTIPY_CLIENT_ID / SPOTIPY_CLIENT_SECRET env vars. See the setup notes "
              "at the top of this file.")
        return 1

    buckets = parse_songs(SONGS_MD)
    print("Parsed from progression_songs.md:")
    for k, v in buckets.items():
        print(f"  {k:9s}: {len(v)} songs")

    sp = spotipy.Spotify(auth_manager=SpotifyOAuth(scope=SCOPE, open_browser=True))
    me = sp.me()
    user_id = me["id"]
    print(f"\nAuthorized as: {me.get('display_name') or user_id}\n")

    print("Removing any same-named playlists from a previous run...")
    unfollow_existing(sp, user_id, set(PLAYLIST_SECTIONS))

    unmatched_report: list[str] = []

    for name, section_keys in PLAYLIST_SECTIONS.items():
        # Merge the sections for this playlist, deduping across them too.
        songs: list[tuple[str, str]] = []
        seen: set[tuple[str, str]] = set()
        for sk in section_keys:
            for title, artist in buckets.get(sk, []):
                key = (title.lower(), artist.lower())
                if key not in seen:
                    seen.add(key)
                    songs.append((title, artist))

        print(f"== {name}  ({'+'.join(section_keys)}): {len(songs)} songs ==")
        # Spotify's Feb-2026 Web API migration retired POST /users/{id}/playlists
        # (what spotipy's user_playlist_create() calls) — it now 403s for everyone in
        # Development mode. The replacement is POST /me/playlists. spotipy has no wrapper
        # yet, so call the endpoint directly via its internal _post().
        pl = sp._post("me/playlists", payload={
            "name": name,
            "public": PLAYLISTS_PUBLIC,
            "description": PLAYLIST_DESC.get(name, ""),
        })
        uris: list[str] = []
        for title, artist in songs:
            uri, disp = find_track_uri(sp, title, artist)
            if uri:
                uris.append(uri)
                print(f"   OK  {title} — {artist}   ->   {disp}")
            else:
                print(f"   ??  NO MATCH: {title} — {artist}")
                unmatched_report.append(f"[{name}] {title} — {artist}")

        for batch in chunked(uris, 100):
            sp.playlist_add_items(pl["id"], batch)

        print(f"   added {len(uris)}/{len(songs)} tracks to {name}\n")

    if unmatched_report:
        UNMATCHED_OUT.write_text("\n".join(unmatched_report) + "\n", encoding="utf-8")
        print(f"{len(unmatched_report)} unmatched track(s) written to {UNMATCHED_OUT}")
    else:
        print("All tracks matched.")

    print("\nDone. Check your Spotify library for earTraining#1 / #2 / #3.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

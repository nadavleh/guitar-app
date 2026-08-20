import { el, btn, clear } from "./dom";
import {
  SongPack, PackSong, PackIndexRow, canPickDirectory, loadCachedPack, pickAndLoadPack,
  refreshFromRememberedDirectory, clearPack,
} from "./songPack";
import { parseKey, prefersFlats, transposeSymbol, transposeKey, degreeLabel, SongKey } from "../theory";

/** Songs tab — the sideloaded chord sheets.
 *
 * The pack lives on local disk, never on the server: the deployed site ships the
 * chord library only, and the lyric text comes from a directory the owner keeps.
 * Once read, the whole pack sits in IndexedDB, so the app still works after the
 * directory is moved or deleted — see songPack.ts.
 *
 * Three things the sheet can do, and deliberately no more: show the chords over
 * the lyrics, transpose them, and relabel them by degree. No playback, no shapes. */
export class SongsUI {
  private pack: SongPack | null = null;
  private query = "";
  private selected: string | null = null;
  /** Semitones to shift the displayed chords by; the pack itself is never edited. */
  private shift = 0;
  private degrees = false;
  private status = "";
  private host: HTMLElement | null = null;
  /** The cache read is kicked off once, on first paint. */
  private loaded = false;

  /** Load whatever is cached. Safe to call on every open: it touches IndexedDB
   *  only, so it needs no permission and cannot prompt. */
  async init(): Promise<void> {
    this.pack = await loadCachedPack();
    if (this.pack !== null) {
      this.status = `${this.pack.count} songs · loaded ${new Date(this.pack.loadedAt).toLocaleDateString()}`;
    }
    this.rerender();
  }

  /** Paint into [outer], matching the other sheets' render(host) contract. */
  render(outer: HTMLElement): void {
    clear(outer);
    const scroll = el("div", { class: "et-scroll", style: "overflow:auto;height:100%" });
    const host = el("div", { style: "display:flex;flex-direction:column;gap:10px;padding:10px" });
    scroll.appendChild(host);
    outer.appendChild(scroll);
    this.host = host;
    this.paint();
    if (!this.loaded) {
      this.loaded = true;
      void this.init();
    }
  }

  private rerender(): void {
    if (this.host !== null) this.paint();
  }

  private paint(): void {
    const host = this.host;
    if (host === null) return;
    clear(host);
    host.appendChild(this.packBar());
    if (this.pack === null) {
      host.appendChild(this.emptyState());
      return;
    }
    if (this.selected !== null) {
      const song = this.pack.bodies[this.selected];
      if (song !== undefined) {
        host.appendChild(this.songView(song));
        return;
      }
      this.selected = null;
    }
    host.appendChild(this.searchBar());
    host.appendChild(this.list());
  }

  // ---------- pack controls ----------

  private packBar(): HTMLElement {
    const kids: HTMLElement[] = [];
    if (canPickDirectory()) {
      kids.push(btn(this.pack === null ? "Open song folder…" : "Change folder…", () => {
        this.status = "reading…";
        this.rerender();
        pickAndLoadPack().then((p) => {
          this.pack = p;
          const read = Object.keys(p.bodies).length;
          this.status = read === p.count
            ? `${p.count} songs loaded and cached`
            : `${read} of ${p.count} songs loaded (some files missing)`;
          this.rerender();
        }).catch((e: Error) => {
          // An aborted picker is a normal cancel, not an error worth shouting about.
          this.status = /abort/i.test(e.message) ? "" : `could not read the folder: ${e.message}`;
          this.rerender();
        });
      }, "btn primary"));
    }
    if (this.pack !== null) {
      kids.push(btn("Refresh", () => {
        this.status = "refreshing…";
        this.rerender();
        refreshFromRememberedDirectory().then((p) => {
          if (p !== null) {
            this.pack = p;
            this.status = `${p.count} songs · refreshed`;
          } else {
            this.status = "the folder is not reachable — showing the cached copy";
          }
          this.rerender();
        });
      }));
      kids.push(btn("Forget", () => {
        clearPack().then(() => {
          this.pack = null;
          this.selected = null;
          this.status = "cache cleared";
          this.rerender();
        });
      }));
    }
    const bar = el("div", { style: "display:flex;gap:8px;align-items:center;flex-wrap:wrap" }, kids);
    if (this.status.length > 0) {
      bar.appendChild(el("span", { class: "et-muted", style: "font-size:12px" }, [this.status]));
    }
    return bar;
  }

  private emptyState(): HTMLElement {
    const lines = canPickDirectory()
      ? [
        "No song folder loaded on this computer yet.",
        "Open the folder built by tools/build_songpack.py — it holds index.json and songs/.",
        "The songs are then cached in this browser, so they stay available even if you move or delete the folder.",
      ]
      : [
        "This browser cannot open a local folder.",
        "The song pack needs Chrome or Edge on the desktop; the chord library still works everywhere.",
      ];
    return el("div", { class: "et-muted", style: "font-size:13px;line-height:1.6;padding:8px 0" },
      lines.map((t) => el("div", {}, [t])));
  }

  // ---------- list ----------

  private searchBar(): HTMLElement {
    const input = el("input", {
      type: "text", placeholder: "Search title or artist",
      style: "flex:1;padding:8px;border-radius:8px;border:1px solid var(--surface2);background:var(--surface);color:inherit",
    }) as HTMLInputElement;
    input.value = this.query;
    input.oninput = () => {
      this.query = input.value;
      const list = this.host?.querySelector("[data-songlist]");
      if (list instanceof HTMLElement) {
        clear(list);
        for (const row of this.rows()) list.appendChild(this.row(row));
      }
    };
    return el("div", { style: "display:flex;gap:8px" }, [input]);
  }

  private rows(): PackIndexRow[] {
    const pack = this.pack;
    if (pack === null) return [];
    const q = this.query.trim().toLowerCase();
    if (q.length === 0) return [...pack.songs];
    return pack.songs.filter((s) =>
      s.title.toLowerCase().includes(q) || s.artist.toLowerCase().includes(q));
  }

  private row(r: PackIndexRow): HTMLElement {
    const meta: string[] = [];
    if (r.key !== null) meta.push(r.key);
    if (r.capo > 0) meta.push(`capo ${r.capo}`);
    if (r.lyrics === 0) meta.push("chords only");
    const node = el("div", {
      style: "padding:8px 4px;border-bottom:1px solid var(--surface2);cursor:pointer",
    }, [
      el("div", { style: "font-weight:600" }, [r.title]),
      el("div", { class: "et-muted", style: "font-size:12px" }, [
        r.artist + (meta.length > 0 ? " · " + meta.join(" · ") : ""),
      ]),
    ]);
    node.onclick = () => {
      this.selected = r.id;
      this.shift = 0;
      this.rerender();
    };
    return node;
  }

  private list(): HTMLElement {
    const list = el("div", { "data-songlist": "1" }, this.rows().map((r) => this.row(r)));
    return list;
  }

  // ---------- one song ----------

  private songView(song: PackSong): HTMLElement {
    const key = song.key !== null ? parseKey(song.key) : null;
    const flats = prefersFlats(key);
    const shownKey = key !== null ? transposeKey(key, this.shift, flats) : "—";

    const back = btn("← Songs", () => { this.selected = null; this.rerender(); });
    const head = el("div", { style: "display:flex;gap:8px;align-items:center;flex-wrap:wrap" }, [
      back,
      el("span", { style: "font-weight:700" }, [song.title]),
      el("span", { class: "et-muted", style: "font-size:12px" }, [song.artist]),
    ]);

    const keyLine = el("div", { class: "et-muted", style: "font-size:12px" }, [
      `Key ${shownKey}${song.capo > 0 ? ` · capo ${song.capo}` : ""}${this.shift !== 0 ? ` · transposed ${this.shift > 0 ? "+" : ""}${this.shift}` : ""}`,
    ]);

    const controls = el("div", { style: "display:flex;gap:6px;align-items:center;flex-wrap:wrap" }, [
      btn("−", () => { this.shift = (this.shift + 11) % 12; this.rerender(); }),
      btn("+", () => { this.shift = (this.shift + 1) % 12; this.rerender(); }),
      btn("Reset", () => { this.shift = 0; this.rerender(); }),
      btn(this.degrees ? "Chords" : "Degrees", () => {
        this.degrees = !this.degrees;
        this.rerender();
      }, this.degrees ? "btn primary" : "btn"),
    ]);
    if (this.degrees && key === null) {
      controls.appendChild(el("span", { class: "et-muted", style: "font-size:12px" },
        ["no key detected — degrees unavailable"]));
    }

    const body = el("div", {
      style: "font-family:ui-monospace,Menlo,Consolas,monospace;font-size:13px;line-height:1.35;" +
        "white-space:pre;overflow-x:auto;" + (song.rtl ? "direction:rtl;text-align:right" : ""),
    });
    for (const sec of song.sections) {
      body.appendChild(el("div", {
        style: "font-weight:700;color:var(--act);margin-top:10px;font-family:inherit",
      }, [sec.label]));
      for (const line of sec.lines) {
        const chordText = this.chordLine(line.chords, key, flats);
        if (chordText.trim().length > 0) {
          body.appendChild(el("div", { style: "color:var(--act)" }, [chordText]));
        }
        if (line.lyric.length > 0) body.appendChild(el("div", {}, [line.lyric]));
      }
    }

    return el("div", { style: "display:flex;flex-direction:column;gap:8px" },
      [head, keyLine, controls, body]);
  }

  /**
   * Rebuild a chord line at its original columns.
   *
   * Transposing and relabelling change how wide each symbol is, so the columns are
   * re-laid rather than reused verbatim: each chord starts at its recorded column
   * when there is room, and is pushed right by a single space when the previous one
   * would otherwise run into it. Without that, "C" becoming "C#m7" would silently
   * swallow the next chord.
   */
  private chordLine(chords: ReadonlyArray<[number, string]>, key: SongKey | null,
                    flats: boolean): string {
    let out = "";
    for (const [col, symRaw] of chords) {
      const sym = this.degrees && key !== null
        ? degreeLabel(transposeSymbol(symRaw, 0), key)
        : transposeSymbol(symRaw, this.shift, flats);
      if (out.length < col) out += " ".repeat(col - out.length);
      else if (out.length > 0) out += " ";
      out += sym;
    }
    return out;
  }
}

import { el, btn, songLinkRow } from "./dom";
import {
  IntervalSongRef, INTERVAL_SONGS_ASCENDING, INTERVAL_SONGS_DESCENDING, INTERVAL_COMPLEMENT_NOTE,
} from "../theory";

/** Theory tab — reference sheets. First section: interval → song lookup
 *  (descending from Nadav's PDF, ascending generated). The interval trainer's
 *  "♪ Song refs" overlay reuses [intervalRefsContent]. Mirrors Android's
 *  TheoryScreen.kt. Built to grow — more sections land here later. */

/** One interval row: name + tappable song + cue + inversion note. */
function intervalRow(r: IntervalSongRef): HTMLElement {
  const children: HTMLElement[] = [
    el("div", { style: "display:flex;gap:8px;align-items:baseline" }, [
      el("span", { style: "font-weight:700;min-width:34px;color:var(--act)" }, [r.interval]),
      el("span", { style: "font-weight:600" }, [r.intervalLong]),
      el("span", { class: "et-muted", style: "font-size:12px" }, [`(${r.inversion})`]),
    ]),
  ];
  // A row without an artist is a "construct it yourself" entry — no search link.
  children.push(r.artist
    ? songLinkRow(r.song, r.artist)
    : el("div", { class: "et-muted", style: "font-size:14px;padding:2px 0" }, [r.song]));
  children.push(el("div", { class: "et-muted", style: "font-size:13px;padding-left:8px" }, [r.cue]));
  return el("div", { style: "padding:6px 0;border-bottom:1px solid var(--surface2)" }, children);
}

/** The interval-reference block, shared by the Theory tab and the ear-training
 *  interval trainer's overlay. */
export function intervalRefsContent(): HTMLElement {
  const section = (title: string, sub: string, rows: IntervalSongRef[]): HTMLElement =>
    el("div", { style: "margin-bottom:14px" }, [
      el("div", { style: "font-weight:700;color:var(--act);margin-bottom:2px" }, [title]),
      el("div", { class: "et-muted", style: "font-size:12px;font-style:italic;margin-bottom:4px" }, [sub]),
      ...rows.map(intervalRow),
    ]);
  return el("div", {}, [
    section("Ascending", "Sing the cue, then the leap — the song IS the interval.", INTERVAL_SONGS_ASCENDING),
    section("Descending", "From the reference PDF — the classic downward leaps.", INTERVAL_SONGS_DESCENDING),
    el("div", { class: "et-muted", style: "font-size:12px;font-style:italic" }, [INTERVAL_COMPLEMENT_NOTE]),
  ]);
}

export class TheoryUI {
  constructor(private onBack: () => void) {}

  render(container: HTMLElement): void {
    const screen = el("div", { class: "tool-screen" });
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("div", { class: "tool-title" }, ["THEORY"]),
      btn("Back", () => this.onBack()),
    ]));
    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);

    body.appendChild(el("div", { class: "et-card", style: "max-width:560px" }, [
      el("div", { style: "font-weight:700;font-size:16px;margin-bottom:2px" }, ["Interval song references"]),
      el("div", { class: "et-muted", style: "font-size:13px;margin-bottom:8px" }, [
        "A familiar song for every interval, both directions. Tap a song to hear it (YouTube ▶ / Spotify ♫). More theory sheets will land in this tab over time.",
      ]),
      intervalRefsContent(),
    ]));

    container.appendChild(screen);
  }
}

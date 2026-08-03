import { el, btn, externalSongRow } from "./dom";
import { EarTrainingState } from "./earTrainingState";
import {
  IntervalSongRef, INTERVAL_SONGS_ASCENDING, INTERVAL_SONGS_DESCENDING, INTERVAL_COMPLEMENT_NOTE,
} from "../theory";

/** Theory tab — reference sheets. First section: interval → song lookup
 *  (descending from Nadav's PDF, ascending generated). Every row has a real ▶
 *  button that PLAYS the interval in-app: the leap melodically, then both notes
 *  together. Song links are explicitly labelled YouTube/Spotify so ▶ always
 *  means "the app makes the sound". Mirrors Android's TheoryScreen.kt. */

/** One interval row: ▶ play · name · inversion · song links · cue. */
function intervalRow(ear: EarTrainingState, rerender: () => void, r: IntervalSongRef): HTMLElement {
  const id = `${r.ascending ? "asc" : "desc"}:${r.interval}`;
  const playing = ear.intervalPreviewId === id;
  const play = btn(playing ? "■" : "▶", () => {
    if (playing) ear.stopIntervalPreview();
    else ear.playIntervalPreview(id, r.semitones, r.ascending);
    rerender();
  }, playing ? "btn primary" : "btn");
  play.title = `Play the ${r.intervalLong} ${r.ascending ? "ascending" : "descending"} (from C4)`;
  play.style.minWidth = "36px";

  // Title line: interval name only. The ▶ sits on the line BENEATH it, beside the
  // external links, so in-app audio and outward links live on the same row.
  const head = el("div", { style: "display:flex;gap:8px;align-items:baseline;flex-wrap:wrap" }, [
    el("span", { style: "font-weight:700;min-width:32px;color:var(--act)" }, [r.interval]),
    el("span", { style: "font-weight:600" }, [r.intervalLong]),
    el("span", { class: "et-muted", style: "font-size:12px" }, [`(${r.inversion})`]),
  ]);
  // A row without an artist is a "construct it yourself" entry — nothing to search for.
  const songPart = r.artist
    ? externalSongRow(r.song, r.artist)
    : el("div", { class: "et-muted", style: "flex:1;font-size:14px" }, [r.song]);
  songPart.style.flex = "1";
  const children: HTMLElement[] = [
    head,
    el("div", { style: "display:flex;gap:8px;align-items:center" }, [play, songPart]),
    el("div", { class: "et-muted", style: "font-size:13px;padding-left:8px" }, [r.cue]),
  ];
  return el("div", { style: "padding:6px 0;border-bottom:1px solid var(--surface2)" }, children);
}

/** The interval-reference block, shared by the Theory tab and the ear-training
 *  interval trainer's overlay. */
export function intervalRefsContent(ear: EarTrainingState, rerender: () => void): HTMLElement {
  const section = (title: string, sub: string, rows: IntervalSongRef[]): HTMLElement =>
    el("div", { style: "margin-bottom:14px" }, [
      el("div", { style: "font-weight:700;color:var(--act);margin-bottom:2px" }, [title]),
      el("div", { class: "et-muted", style: "font-size:12px;font-style:italic;margin-bottom:4px" }, [sub]),
      ...rows.map((r) => intervalRow(ear, rerender, r)),
    ]);
  return el("div", {}, [
    el("div", { class: "et-muted", style: "font-size:12px;margin-bottom:8px" }, [
      "▶ plays the interval itself (from C4): the two notes in turn, then together. The YouTube/Spotify links open the reference song outside the app.",
    ]),
    section("Ascending", "Sing the cue, then the leap — the song IS the interval.", INTERVAL_SONGS_ASCENDING),
    section("Descending", "From the reference sheet — the classic downward leaps.", INTERVAL_SONGS_DESCENDING),
    el("div", { class: "et-muted", style: "font-size:12px;font-style:italic" }, [INTERVAL_COMPLEMENT_NOTE]),
  ]);
}

export class TheoryUI {
  constructor(
    private ear: EarTrainingState,
    private onBack: () => void,
    private rerender: () => void,
  ) {}

  render(container: HTMLElement): void {
    const screen = el("div", { class: "tool-screen" });
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("div", { class: "tool-title" }, ["THEORY"]),
      btn("Back", () => { this.ear.stopIntervalPreview(); this.onBack(); }),
    ]));
    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);

    body.appendChild(el("div", { class: "et-card", style: "max-width:600px" }, [
      el("div", { style: "font-weight:700;font-size:16px;margin-bottom:2px" }, ["Interval song references"]),
      el("div", { class: "et-muted", style: "font-size:13px;margin-bottom:8px" }, [
        "A familiar song for every interval, in both directions — plus in-app playback so you can check yourself against the sound. More theory sheets will land in this tab over time.",
      ]),
      intervalRefsContent(this.ear, this.rerender),
    ]));

    container.appendChild(screen);
  }
}

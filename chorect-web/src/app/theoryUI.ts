import { el, btn, externalSongRow } from "./dom";
import { EarTrainingState } from "./earTrainingState";
import {
  IntervalSongRef, INTERVAL_COMPLEMENT_NOTE, intervalRefFor,
} from "../theory";

/** Theory tab — reference sheets. First section: interval → song lookup
 *  (descending from Nadav's PDF, ascending generated). Every row has a real ▶
 *  button that PLAYS the interval in-app: the leap melodically, then both notes
 *  together. Song links are explicitly labelled YouTube/Spotify so ▶ always
 *  means "the app makes the sound". Mirrors Android's TheoryScreen.kt. */

/** A small ▶/■ toggle bound to one `ear.intervalPreviewId`. */
function previewButton(ear: EarTrainingState, rerender: () => void, id: string, label: string,
                       title: string, onPlay: () => void): HTMLElement {
  const playing = ear.intervalPreviewId === id;
  const b = btn(playing ? "■" : label, () => {
    if (playing) ear.stopIntervalPreview(); else onPlay();
    rerender();
  }, playing ? "btn primary" : "btn");
  b.title = title;
  b.style.minWidth = "36px";
  return b;
}

/** One direction of an interval: ▶ the bare interval, then each song with its own ▶
 *  that plays a few notes of the melody containing the interval. */
function directionBlock(ear: EarTrainingState, rerender: () => void, r: IntervalSongRef): HTMLElement {
  const dirId = `${r.ascending ? "asc" : "desc"}:${r.interval}`;
  const dirWord = r.ascending ? "ascending" : "descending";
  const children: HTMLElement[] = [
    el("div", { style: "display:flex;gap:8px;align-items:baseline;margin-top:4px" }, [
      previewButton(ear, rerender, dirId, r.ascending ? "▶ ↑" : "▶ ↓",
        `Play the ${r.intervalLong} ${dirWord} (from C4)`,
        () => ear.playIntervalPreview(dirId, r.semitones, r.ascending)),
      el("span", { style: "font-weight:600" }, [r.ascending ? "Ascending" : "Descending"]),
      el("span", { class: "et-muted", style: "font-size:12px" }, [`(${r.inversion})`]),
    ]),
  ];
  if (r.songs.length === 0 && r.note) {
    children.push(el("div", { class: "et-muted", style: "font-size:13px;padding-left:12px" }, [r.note]));
  }
  r.songs.forEach((song, i) => {
    const songId = `${dirId}:song${i}`;
    const links = externalSongRow(song.title, song.artist);
    links.style.flex = "1";
    children.push(el("div", { style: "display:flex;gap:8px;align-items:flex-start;padding-left:12px;margin-top:2px" }, [
      previewButton(ear, rerender, songId, "▶", `Play a few notes of ${song.title}`,
        () => ear.playMelodySnippet(songId, song.melody)),
      el("div", { style: "flex:1" }, [
        links,
        el("div", { class: "et-muted", style: "font-size:12px" }, [song.cue]),
      ]),
    ]));
  });
  return el("div", {}, children);
}

/** One interval, both directions; `firstAscending` picks which comes first (the
 *  trainer puts the direction you just heard on top). */
function intervalCard(ear: EarTrainingState, rerender: () => void, semitones: number, firstAscending = true): HTMLElement {
  const first = intervalRefFor(semitones, firstAscending);
  const second = intervalRefFor(semitones, !firstAscending);
  if (!first || !second) return el("div");
  return el("div", { style: "padding:6px 0 8px;border-bottom:1px solid var(--surface2)" }, [
    el("div", { style: "display:flex;gap:8px;align-items:baseline" }, [
      el("span", { style: "font-weight:700;min-width:32px;color:var(--act)" }, [first.interval]),
      el("span", { style: "font-weight:700" }, [first.intervalLong]),
    ]),
    directionBlock(ear, rerender, first),
    directionBlock(ear, rerender, second),
  ]);
}

/** Just one interval — opened by the trainer's post-answer "♪ Songs for …" button. */
export function intervalSongsContent(ear: EarTrainingState, rerender: () => void,
                                     semitones: number, ascending: boolean): HTMLElement {
  return intervalCard(ear, rerender, semitones, ascending);
}

/** The interval-reference block, shared by the Theory tab and the ear-training
 *  interval trainer's overlay. */
export function intervalRefsContent(ear: EarTrainingState, rerender: () => void): HTMLElement {
  const cards: HTMLElement[] = [];
  for (let st = 1; st <= 12; st++) cards.push(intervalCard(ear, rerender, st));
  return el("div", {}, [
    el("div", { class: "et-muted", style: "font-size:12px;margin-bottom:4px" }, [
      "▶ ↑ / ▶ ↓ play the bare interval (from C4): the two notes in turn, then together. The ▶ beside a song plays a few notes of its melody that contain the interval; YouTube/Spotify open the real recording.",
    ]),
    ...cards,
    el("div", { class: "et-muted", style: "font-size:12px;font-style:italic;margin-top:8px" }, [INTERVAL_COMPLEMENT_NOTE]),
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

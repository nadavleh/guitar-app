import { el, btn, externalSongRow } from "./dom";
import { EarTrainingState } from "./earTrainingState";
import {
  IntervalSongRef, intervalRefFor,
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
function directionBlock(ear: EarTrainingState, rerender: () => void, r: IntervalSongRef, anchorMidi: number | null): HTMLElement {
  const dirId = `${r.ascending ? "asc" : "desc"}:${r.interval}`;
  const dirWord = r.ascending ? "ascending" : "descending";
  const children: HTMLElement[] = [
    el("div", { style: "display:flex;gap:8px;align-items:baseline;margin-top:4px" }, [
      previewButton(ear, rerender, dirId, r.ascending ? "▶ ↑" : "▶ ↓",
        `Play the ${r.intervalLong} ${dirWord} (from ${anchorMidi === null ? "C4" : "this question's tonic"})`,
        () => ear.playIntervalPreview(dirId, r.semitones, r.ascending, anchorMidi ?? 60)),
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
        () => ear.playMelodySnippet(songId, song.melody, r.semitones, r.ascending, anchorMidi)),
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
function intervalCard(ear: EarTrainingState, rerender: () => void, semitones: number, firstAscending = true,
                      anchorMidi: number | null = null): HTMLElement {
  const first = intervalRefFor(semitones, firstAscending);
  const second = intervalRefFor(semitones, !firstAscending);
  if (!first || !second) return el("div");
  return el("div", { style: "padding:6px 0 8px;border-bottom:1px solid var(--surface2)" }, [
    el("div", { style: "display:flex;gap:8px;align-items:baseline" }, [
      el("span", { style: "font-weight:700;min-width:32px;color:var(--act)" }, [first.interval]),
      el("span", { style: "font-weight:700" }, [first.intervalLong]),
    ]),
    directionBlock(ear, rerender, first, anchorMidi),
    directionBlock(ear, rerender, second, anchorMidi),
  ]);
}

/** Just one interval — opened by the trainer's post-answer "♪ Songs for …" button.
 *  With `anchorMidi` (the question's tonic) each song's leap starts on that note. */
export function intervalSongsContent(ear: EarTrainingState, rerender: () => void,
                                     semitones: number, ascending: boolean, anchorMidi: number | null = null): HTMLElement {
  const card = intervalCard(ear, rerender, semitones, ascending, anchorMidi);
  if (anchorMidi === null) return card;
  return el("div", {}, [
    el("div", { class: "et-muted", style: "font-size:12px" }, [
      "Played from this question's tonic, so each song's leap is the one you just heard."]),
    card,
  ]);
}

/** Which interval/direction the reference block shows; shared by the Theory tab and
 *  the trainer overlay, kept across rerenders (Compose: rememberSaveable). */
let refSel = 1;
let refUp = true;

/** Preselect an interval (the trainer opens the overlay on the question just asked). */
export function selectIntervalRef(semitones: number, ascending: boolean): void {
  refSel = Math.min(12, Math.max(1, semitones));
  refUp = ascending;
}

/** The interval-reference block, shared by the Theory tab and the ear-training
 *  interval trainer's overlay. Master–detail instead of one long list: a 12-chip
 *  interval picker plus an ↑/↓ toggle select ONE interval in ONE direction, so any
 *  interval is a single tap away. ‹ › step to the neighbouring interval.
 *  Mirrors Android IntervalRefsContent. */
export function intervalRefsContent(ear: EarTrainingState, rerender: () => void, anchorMidi: number | null = null): HTMLElement {
  const pick = (st: number, up: boolean) => { ear.stopIntervalPreview(); refSel = st; refUp = up; rerender(); };
  const chips: HTMLElement[] = [];
  for (let st = 1; st <= 12; st++) {
    const r = intervalRefFor(st, refUp);
    if (!r) continue;
    const b = el("button", { class: st === refSel ? "chip selected" : "chip", style: "font-weight:700;min-width:44px" }, [r.interval]);
    b.title = r.intervalLong;
    b.addEventListener("click", () => pick(st, refUp));
    chips.push(b);
  }
  const dirChips = [true, false].map((up) => {
    const b = el("button", { class: up === refUp ? "chip selected" : "chip" }, [up ? "↑ Ascending" : "↓ Descending"]);
    b.addEventListener("click", () => pick(refSel, up));
    return b;
  });
  const r = intervalRefFor(refSel, refUp);
  const children: HTMLElement[] = [
    el("div", { class: "chip-row" }, chips),
    el("div", { class: "chip-row", style: "margin-top:6px" }, dirChips),
  ];
  if (r) {
    children.push(
      el("div", { style: "display:flex;gap:8px;align-items:center;margin-top:8px" }, [
        btn("‹", () => pick(refSel === 1 ? 12 : refSel - 1, refUp), "btn text"),
        el("span", { style: "font-weight:700;min-width:32px;color:var(--act)" }, [r.interval]),
        el("span", { style: "font-weight:700;flex:1" }, [r.intervalLong]),
        el("span", { class: "et-muted", style: "font-size:12px" }, [`${r.semitones} st`]),
        btn("›", () => pick(refSel === 12 ? 1 : refSel + 1, refUp), "btn text"),
      ]),
      directionBlock(ear, rerender, r, anchorMidi),
    );
  }
  return el("div", {}, children);
}

export class TheoryUI {
  /** Scroll position of the reference list, kept across rerenders. */
  private scroll = 0;

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
    // Every ▶ rebuilds the page; put the list back where it was instead of at the top.
    body.scrollTop = this.scroll;
    body.addEventListener("scroll", () => { this.scroll = body.scrollTop; }, { passive: true });
  }
}

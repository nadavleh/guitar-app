// Progression-looper screen, ported from the LoopScreen + BarCard + SlotEditor +
// BuildByDegreePanel section of app/.../Screens.kt.

import { LoopState, StrumPattern, StrumGlyph, StrumName } from "./loopState";
import { Colors } from "./theme";
import { el, btn, slider, segmented, labelSm } from "./dom";
import {
  spellPc, TrainingMode, ChordTypeLevel, ChordTypeLevelName,
  degreeRoot, degreesMapFor, CagedShapeInfo,
} from "../theory";

const OVERRIDE_QUALITIES = ["", "m", "aug", "7", "maj7", "m7", "m7b5", "dim7", "6", "m6", "9", "13", "sus4", "add9"];

export class LoopUI {
  constructor(private loop: LoopState, private onBack: () => void) {}

  render(container: HTMLElement): void {
    const L = this.loop;
    L.ensureNormalized();
    const screen = el("div", { class: "tool-screen" });

    // header
    screen.appendChild(el("div", { class: "tool-topbar" }, [
      el("div", { class: "tool-title" }, ["LOOP"]),
      L.isLooping ? btn("Stop ⏹", () => L.stopLoop(), "btn primary")
        : (() => { const b = btn("Play ▶", () => L.startLoop(), "btn primary"); if (!L.hasAnyChord()) b.disabled = true; return b; })(),
      btn(L.isLooping ? "Watch on neck ▶" : "Back", () => this.onBack()),
    ]));

    const body = el("div", { class: "et-scroll" });
    screen.appendChild(body);

    // tempo
    body.appendChild(el("div", {}, [`Tempo: ${L.bpm} BPM`]));
    body.appendChild(slider(40, 200, L.bpm, (v) => L.setBpm(v)));

    // slots/bar + bars
    body.appendChild(el("div", { class: "et-row-gap", style: "margin-top:6px" }, [
      el("div", {}, [
        labelSm("Slots/bar"),
        segmented<string>([{ value: "1", label: "1" }, { value: "2", label: "2" }, { value: "4", label: "4" }],
          String(L.slotsPerBar), (v) => L.setSlotsPerBar(parseInt(v, 10)), false),
      ]),
      el("div", {}, [
        labelSm(`Bars: ${L.progression.length}`),
        el("div", { class: "row" }, [
          (() => { const b = btn("−", () => L.setBarCount(L.progression.length - 1)); if (L.progression.length <= 1) b.disabled = true; return b; })(),
          (() => { const b = btn("+", () => L.setBarCount(L.progression.length + 1)); if (L.progression.length >= 16) b.disabled = true; return b; })(),
        ]),
      ]),
    ]));

    body.appendChild(el("div", { class: "divider-line" }));

    // build-by-degree
    body.appendChild(this.buildPanel());

    // bars grid
    body.appendChild(el("div", { class: "v-gap-8" }));
    body.appendChild(this.barsGrid());

    // slot editor
    if (L.editingSlot) {
      body.appendChild(el("div", { class: "v-gap-8" }));
      body.appendChild(this.slotEditor(L.editingSlot[0], L.editingSlot[1]));
    }

    container.appendChild(screen);
  }

  private barsGrid(): HTMLElement {
    const L = this.loop;
    const grid = el("div", { class: "loop-bars" });
    L.progression.forEach((bar, barIdx) => {
      const isCurrent = L.isLooping && L.currentBar === barIdx;
      const card = el("div", { class: isCurrent ? "bar-card current" : "bar-card" }, [
        el("div", { class: "bar-num" }, [`Bar ${barIdx + 1}`]),
      ]);
      const slots = el("div", { class: "bar-slots" });
      bar.forEach((s, slotIdx) => {
        const isEditing = L.editingSlot && L.editingSlot[0] === barIdx && L.editingSlot[1] === slotIdx;
        const isPlaying = L.isLooping && isCurrent && L.currentSlot === slotIdx;
        const cls = isEditing ? "slot-box editing" : isPlaying ? "slot-box playing" : "slot-box";
        const box = el("div", { class: cls }, [
          el("div", { class: "sym", style: s.chordSymbol ? "" : `color:${Colors.textSecondary}` }, [s.chordSymbol ?? "·"]),
          el("div", { class: "strum" }, [StrumGlyph[s.strum]]),
        ]);
        box.addEventListener("click", () => L.setEditingSlot([barIdx, slotIdx]));
        slots.appendChild(box);
      });
      card.appendChild(slots);
      grid.appendChild(card);
    });
    return grid;
  }

  private slotEditor(barIdx: number, slotIdx: number): HTMLElement {
    const L = this.loop;
    const s = L.progression[barIdx]?.[slotIdx];
    if (!s) return el("div", {});
    const card = el("div", { class: "et-card", style: `background:${Colors.surfaceElev}` });
    card.appendChild(el("div", { class: "row" }, [
      el("div", { style: "flex:1;font-weight:600" }, [`Edit · Bar ${barIdx + 1} / slot ${slotIdx + 1}`]),
      btn("Close", () => L.setEditingSlot(null), "btn text"),
    ]));

    // chord input — commit on change (blur/Enter) so typing doesn't lose focus on re-render
    const input = el("input", { type: "text", placeholder: "e.g. Cmaj7, Dm7, G7", style: "flex:1" }) as HTMLInputElement;
    input.value = s.chordSymbol ?? "";
    input.addEventListener("change", () => L.setLoopSlotChord(barIdx, slotIdx, input.value));
    input.addEventListener("keydown", (e) => { if ((e as KeyboardEvent).key === "Enter") input.blur(); });
    card.appendChild(el("div", { class: "row", style: "margin-top:6px" }, [
      input,
      btn("Clear", () => L.setLoopSlot(barIdx, slotIdx, { ...s, chordSymbol: null })),
    ]));

    // voicing picker
    if (s.chordSymbol != null) {
      card.appendChild(labelSm("Voicing"));
      const shapes = L.shapesForSlot(barIdx, slotIdx);
      if (shapes.length === 0) {
        card.appendChild(el("div", { style: `color:${Colors.rootTone};font-size:13px` }, ["(chord not recognized)"]));
      } else {
        const row = el("div", { class: "chip-row" });
        shapes.forEach((sh, i) => {
          const baseLabel = sh.cagedShape ? CagedShapeInfo[sh.cagedShape].displayName
            : sh.templateName ? sh.templateName.split(" (")[0] : `shape ${i + 1}`;
          const played = sh.frets.filter((f): f is number => f !== null && f > 0);
          const fretText = played.length === 0 ? "open"
            : Math.min(...played) === Math.max(...played) ? `fret ${Math.min(...played)}`
            : `frets ${Math.min(...played)}–${Math.max(...played)}`;
          const chip = el("button", { class: i === s.voicingIndex ? "chip selected" : "chip" }, [`${baseLabel} · ${fretText}`]);
          chip.addEventListener("click", () => L.setLoopSlot(barIdx, slotIdx, { ...s, voicingIndex: i }));
          row.appendChild(chip);
        });
        card.appendChild(row);
      }
    }

    // strum picker
    card.appendChild(labelSm("Strum"));
    card.appendChild(segmented<StrumPattern>(
      [StrumPattern.Down, StrumPattern.Up, StrumPattern.Arpeggio, StrumPattern.Sustain].map((p) => ({ value: p, label: `${StrumGlyph[p]} ${StrumName[p]}` })),
      s.strum, (p) => L.setLoopSlot(barIdx, slotIdx, { ...s, strum: p }),
    ));
    return card;
  }

  private buildPanel(): HTMLElement {
    const L = this.loop;
    const panel = el("div", { class: "build-panel" });
    panel.appendChild(el("div", { class: "row" }, [
      el("div", { style: "flex:1;font-weight:600" }, ["Build by degree"]),
      btn(L.buildExpanded ? "Collapse ▲" : "Expand ▼", () => { L.buildExpanded = !L.buildExpanded; (L as unknown as { notify?: () => void }); this.rerender(); }, "btn text"),
    ]));
    if (!L.buildExpanded) return panel;

    // key + mode
    panel.appendChild(el("div", { class: "et-row-gap", style: "margin-top:6px" }, [
      labelSm("Key"),
      btn("Random", () => L.setBuildKeyRandom()),
      labelSm("Mode"),
      segmented<TrainingMode>([{ value: TrainingMode.Major, label: "Major" }, { value: TrainingMode.Minor, label: "Minor" }],
        L.buildMode, (m) => { L.buildMode = m; this.rerender(); }, false),
    ]));
    const keyRow = el("div", { class: "chip-row" });
    for (let i = 0; i < 12; i++) {
      const chip = el("button", { class: i === L.buildKey ? "chip selected" : "chip" }, [spellPc(i)]);
      chip.addEventListener("click", () => { L.buildKey = i; this.rerender(); });
      keyRow.appendChild(chip);
    }
    panel.appendChild(keyRow);

    // level
    panel.appendChild(labelSm("Diatonic level"));
    panel.appendChild(segmented<ChordTypeLevel>(
      [ChordTypeLevel.Triads, ChordTypeLevel.Sevenths, ChordTypeLevel.Extended].map((lvl) => ({ value: lvl, label: ChordTypeLevelName[lvl] })),
      L.buildOverride == null ? L.buildLevel : ("__none__" as ChordTypeLevel),
      (lvl) => { L.buildLevel = lvl; L.buildOverride = null; this.rerender(); },
    ));

    // override quality
    panel.appendChild(labelSm("Override quality (replaces diatonic)"));
    const ovRow = el("div", { class: "chip-row" });
    for (const sym of OVERRIDE_QUALITIES) {
      const chip = el("button", { class: L.buildOverride === sym ? "chip selected" : "chip" }, [sym === "" ? "maj" : sym]);
      chip.addEventListener("click", () => { L.buildOverride = L.buildOverride === sym ? null : sym; this.rerender(); });
      ovRow.appendChild(chip);
    }
    panel.appendChild(ovRow);

    // cursor + degree buttons
    const cursorLabel = L.editingSlot ? "→ writes to selected slot" : `→ writes to bar ${L.buildCursor + 1} (auto-advances)`;
    panel.appendChild(el("div", { class: "row", style: "margin-top:6px" }, [
      el("div", { class: "et-muted", style: "flex:1" }, [cursorLabel]),
      !L.editingSlot ? btn("Reset to bar 1", () => L.resetBuildCursor(), "btn text") : null,
    ].filter(Boolean) as HTMLElement[]));

    const degRow = el("div", { class: "chip-row" });
    const info = degreesMapFor(L.buildMode);
    for (let d = 1; d <= 7; d++) {
      const di = info.get(d);
      const roman = di?.roman ?? "?";
      const rootPc = degreeRoot(L.buildKey, d, L.buildMode);
      const q = L.buildOverride ?? (L.buildLevel === ChordTypeLevel.Triads ? di?.triadQuality ?? ""
        : L.buildLevel === ChordTypeLevel.Sevenths ? di?.seventhQuality ?? "" : di?.extendedQuality ?? "");
      const b = el("button", { class: "degree-btn" }, [
        el("span", { class: "roman" }, [roman]),
        el("span", { class: "prev" }, [spellPc(rootPc) + q]),
      ]);
      b.addEventListener("click", () => L.applyLoopDegree(d));
      degRow.appendChild(b);
    }
    panel.appendChild(degRow);
    return panel;
  }

  private rerender(): void { (this.loop as unknown as { deps: { onChange: () => void } }).deps.onChange(); }
}

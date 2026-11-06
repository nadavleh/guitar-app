// Area "caged" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- caged

import { parseChord, cagedShapesFor, notesFrom, parsePitchClass, midiPitchClass, QUALITIES, CAGED_SHAPES, CAGED_BOXES, CagedBox, CagedMode, ScaleSubset, patternCount, boxNumber, resolveBox, boxWindow, PRACTICE_RUN, TRIAD_GROUPS, triadRun, triadInversions, explorePositions, noteAt, fp, fpKey } from "../../src/theory";
import { standard } from "../../src/theory/tunings";
import { check } from "./harness";

export function run(): void {

  // --- CAGED template purity: templates must not ADD foreign tones (e.g. the old
  // dim E-shape sneaked in the dim7's maj6) ---
  let templatesPure = true;
  for (const sym of ["", "m", "7", "maj7", "m7", "m7b5", "dim7", "dim", "aug", "sus2", "sus4", "6", "m6",
      "maj9", "maj13", "maj7#11", "m9", "m11", "11", "7sus4", "mMaj7"]) {
    const q = QUALITIES.get(sym)!;
    for (let r = 0; r < 12; r++) {
      const allowed = new Set(notesFrom(q, r));
      for (const s of cagedShapesFor(r, q, standard, 14)) {
        for (const n of s.notes) if (n !== null && !allowed.has(midiPitchClass(n.midi))) templatesPure = false;
      }
    }
  }
  check("every CAGED template sounds only chord tones", templatesPure);
  // Every ear-training extension has real CAGED grips (no brute-force voicing), in every key.
  {
    let ok = true;
    for (const sym of ["maj9", "maj13", "maj7#11", "m9", "m11", "11", "7sus4", "mMaj7"])
      for (let r = 0; r < 12; r++) if (cagedShapesFor(r, QUALITIES.get(sym)!, standard, 14).length < 2) ok = false;
    check("ear-training extensions have CAGED grips in every key", ok);
  }

  // --- CAGED templates: C major → 5 ascending shapes ---
  const cMajCaged = cagedShapesFor(0, parseChord("C")![1], standard, 14);
  check("C major has 5 CAGED shapes", cMajCaged.length === 5);
  let ascending = true;
  for (let i = 1; i < cMajCaged.length; i++) if (cMajCaged[i].position < cMajCaged[i - 1].position) ascending = false;
  check("CAGED shapes sorted ascending by position", ascending);

  // --- CAGED shape table (mirrors theory/.../CagedShapeTableTest.kt + CagedScalesTest.kt) ---
  // The 34 shapes are transcribed from Nadav's sheet; these pin them so a typo in
  // one shape string, or a one-sided edit to either twin, fails CI.
  {
    const G = 7;
    const pcsOf = (mode: CagedMode, subset: ScaleSubset): Set<number> => {
      const deg = mode === CagedMode.Major
        ? { [ScaleSubset.FullScale]: [0, 2, 4, 5, 7, 9, 11], [ScaleSubset.Pentatonic]: [0, 2, 4, 7, 9], [ScaleSubset.Triad]: [0, 4, 7] }
        : { [ScaleSubset.FullScale]: [0, 2, 3, 5, 7, 8, 10], [ScaleSubset.Pentatonic]: [0, 3, 5, 7, 10], [ScaleSubset.Triad]: [0, 3, 7] };
      return new Set(deg[subset].map((i) => (G + i) % 12));
    };
    const pcAt = (stringIndex: number, fret: number) => midiPitchClass(noteAt(standard, fp(stringIndex, fret)).midi);
    const MODES = [CagedMode.Major, CagedMode.Minor];
    const SUBSETS = [ScaleSubset.Triad, ScaleSubset.Pentatonic, ScaleSubset.FullScale];

    check("the sheet's 34 CAGED diagrams are all present", CAGED_SHAPES.size === 34);
    check("a 2nd fingering exists only for the scale of boxes 1 and 4", (() => {
      for (const box of CAGED_BOXES) for (const mode of MODES) for (const subset of SUBSETS) {
        const want = subset === ScaleSubset.FullScale && (box === CagedBox.POS1 || box === CagedBox.POS4) ? 2 : 1;
        if (patternCount(box, mode, subset) !== want) return false;
      }
      return true;
    })());
    check("every dot of every CAGED shape is in the right scale, and roots are roots", (() => {
      for (const [key, dots] of CAGED_SHAPES) {
        const parts = key.split("|");
        const mode = parts[1] as CagedMode;
        const subset = parts[2] as ScaleSubset;
        const allowed = pcsOf(mode, subset);
        for (const d of dots) {
          const pc = pcAt(d.string, 3 + d.offset);
          if (!allowed.has(pc)) return false;
          if (d.isRoot !== (pc === G)) return false;
        }
      }
      return true;
    })());
    check("every CAGED shape fits a 22-fret neck in all 12 keys, dropping no notes", (() => {
      for (let k = 0; k < 12; k++) {
        for (const box of CAGED_BOXES) for (const mode of MODES) for (const subset of SUBSETS) {
          for (let pat = 1; pat <= patternCount(box, mode, subset); pat++) {
            const [lo, hi] = boxWindow(k, box, standard, mode, subset, pat);
            if (lo < 0 || hi > 22) return false;
            const want = CAGED_SHAPES.get(`${box}|${mode}|${subset}|${pat}`)!.length;
            if (resolveBox(k, box, mode, subset, standard, 22, pat).length !== want) return false;
          }
        }
      }
      return true;
    })());
    check("the CAGED boxes ascend the neck", (() => {
      for (const mode of MODES) for (const subset of SUBSETS) {
        const los = CAGED_BOXES.map((b) => boxWindow(G, b, standard, mode, subset)[0]);
        for (let i = 1; i < los.length; i++) if (los[i] < los[i - 1]) return false;
      }
      return true;
    })());
    check("the 5 boxes tile every scale tone between frets 2 and 12", (() => {
      for (const mode of MODES) {
        const allowed = pcsOf(mode, ScaleSubset.FullScale);
        const union = new Set<string>();
        for (const box of CAGED_BOXES) for (let pat = 1; pat <= patternCount(box, mode, ScaleSubset.FullScale); pat++) {
          for (const n of resolveBox(G, box, mode, ScaleSubset.FullScale, standard, 22, pat)) union.add(fpKey(n.position));
        }
        for (let st = 0; st < 6; st++) for (let f = 2; f <= 12; f++) {
          if (allowed.has(pcAt(st, f)) && !union.has(fpKey(fp(st, f)))) return false;
        }
      }
      return true;
    })());
    // The four corrections applied to the sheet (see cagedShapeTable.ts's header).
    check("correction 1 - minor scale box 1 pattern 1 is in G, not A minor", (() => {
      const notes = resolveBox(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.FullScale, standard, 22, 1);
      return notes.some((n) => n.isRoot && n.position.stringIndex === 0 && n.position.fret === 3) &&
        !notes.some((n) => pcAt(n.position.stringIndex, n.position.fret) === 11);
    })());
    check("correction 2 - minor pentatonic box 1 has D on the A string, not D#", (() => {
      const a = resolveBox(G, CagedBox.POS1, CagedMode.Minor, ScaleSubset.Pentatonic, standard)
        .filter((n) => n.position.stringIndex === 1).map((n) => n.position.fret).sort((x, y) => x - y);
      return JSON.stringify(a) === JSON.stringify([3, 5]);
    })());
    check("corrections 3 and 4 - minor box 4 is not a copy of box 3", (() => {
      for (const subset of [ScaleSubset.Pentatonic, ScaleSubset.Triad]) {
        const b3 = resolveBox(G, CagedBox.POS3, CagedMode.Minor, subset, standard).map((n) => n.position.fret);
        const b4 = resolveBox(G, CagedBox.POS4, CagedMode.Minor, subset, standard).map((n) => n.position.fret);
        if (JSON.stringify(b3) === JSON.stringify(b4)) return false;
        if (Math.min(...b4) <= Math.min(...b3)) return false;
      }
      const lowE = resolveBox(G, CagedBox.POS4, CagedMode.Minor, ScaleSubset.Triad, standard)
        .filter((n) => n.position.stringIndex === 0).map((n) => n.position.fret);
      return JSON.stringify(lowE) === JSON.stringify([10]);
    })());
    // Nadav's later edits (not sheet slips): both box-1 pattern-2 scale fingerings
    // drop one 5th. The tiling check above proves neither leaves a hole.
    check("box 1 pattern 2 drops the 5th on the 3rd string (minor) / 2nd string (major)", (() => {
      const fifth = (G + 7) % 12;
      const onString = (mode: CagedMode, si: number) =>
        resolveBox(G, CagedBox.POS1, mode, ScaleSubset.FullScale, standard, 22, 2)
          .filter((n) => n.position.stringIndex === si);
      const minorG = onString(CagedMode.Minor, 3);
      if (JSON.stringify(minorG.map((n) => n.position.fret).sort((a, b) => a - b)) !== JSON.stringify([3, 5])) return false;
      if (minorG.some((n) => pcAt(n.position.stringIndex, n.position.fret) === fifth)) return false;
      const majorB = onString(CagedMode.Major, 4);
      if (JSON.stringify(majorB.map((n) => n.position.fret).sort((a, b) => a - b)) !== JSON.stringify([5, 7])) return false;
      return !majorB.some((n) => pcAt(n.position.stringIndex, n.position.fret) === fifth);
    })());
    check("each triad shape contains its own pentatonic shape's chord tones", (() => {
      for (const box of CAGED_BOXES) for (const mode of MODES) {
        const triadPcs = pcsOf(mode, ScaleSubset.Triad);
        const triad = new Set(resolveBox(G, box, mode, ScaleSubset.Triad, standard).map((n) => fpKey(n.position)));
        for (const n of resolveBox(G, box, mode, ScaleSubset.Pentatonic, standard)) {
          if (triadPcs.has(pcAt(n.position.stringIndex, n.position.fret)) && !triad.has(fpKey(n.position))) return false;
        }
      }
      return true;
    })());

    // --- The guided run + the triad drill ---
    check("the guided run is one step per diagram - 34 in all, none repeated", (() => {
      const seen = new Set(PRACTICE_RUN.map((s2) => `${s2.box}|${s2.mode}|${s2.subset}|${s2.pattern}`));
      return PRACTICE_RUN.length === 34 && seen.size === 34 &&
        [...seen].every((k) => CAGED_SHAPES.has(k));
    })());
    check("the run walks the boxes low to high, alternating the leading quality", (() => {
      const order = [...new Set(PRACTICE_RUN.map((s2) => s2.box))];
      if (JSON.stringify(order) !== JSON.stringify(CAGED_BOXES)) return false;
      for (const box of CAGED_BOXES) {
        const steps = PRACTICE_RUN.filter((s2) => s2.box === box);
        const lead = (boxNumber(box) - 1) % 2 === 0 ? CagedMode.Major : CagedMode.Minor;
        if (steps[0].mode !== lead) return false;
        if (new Set(steps.map((s2) => s2.mode)).size !== 2) return false;
        // one contiguous block per quality, chord tones first inside each
        const leadSteps = steps.filter((s2) => s2.mode === lead);
        if (steps.filter((s2, i) => i < leadSteps.length && s2.mode === lead).length !== leadSteps.length) return false;
        const subs = [...new Set(leadSteps.map((s2) => s2.subset))];
        if (JSON.stringify(subs) !== JSON.stringify([ScaleSubset.Triad, ScaleSubset.FullScale, ScaleSubset.Pentatonic])) return false;
      }
      const perBox = CAGED_BOXES.map((b) => PRACTICE_RUN.filter((s2) => s2.box === b).length);
      return JSON.stringify(perBox) === JSON.stringify([8, 6, 6, 8, 6]);
    })());
    check("triad groups run top-down: strings 1-2-3, 2-3-4, 3-4-5, 4-5-6",
      JSON.stringify(TRIAD_GROUPS) === JSON.stringify([[3, 4, 5], [2, 3, 4], [1, 2, 3], [0, 1, 2]]));
    check("the triad run is all 24 - 12 major then 12 minor", (() => {
      const run = triadRun(G, standard);
      if (run.length !== 24) return false;
      if (!run.slice(0, 12).every((r) => r.quality === "maj")) return false;
      if (!run.slice(12).every((r) => r.quality === "min")) return false;
      const groups = [...new Set(run.slice(0, 12).map((r) => JSON.stringify(r.shape.strings)))];
      return JSON.stringify(groups) === JSON.stringify(TRIAD_GROUPS.map((g) => JSON.stringify(g)));
    })());
    // Pinned to Nadav's triad sheet (docs/caged-shapes-source.md — the D-major page of
    // ~/Desktop/fretboard.pdf). This is why triadInversions skips open strings and
    // incomplete (degree-doubling) close voicings, and orders by neck position.
    // --- Explore browser: the sheet's boxes, not fret windows ---
    // Explore used to show every scale tone inside a 5-fret window, which duplicated a
    // PITCH in six of the seven windows (the B string is a major 3rd above the G string,
    // so G-string fret n and B-string fret n-4 are the same note and both fall inside one
    // window). Nadav circled five. Serving the sheet's diagrams removes all of it.
    check("no Explore box sounds the same pitch twice", (() => {
      for (const mode of [CagedMode.Major, CagedMode.Minor]) {
        // Triads are chord grips and may double a pitch, so only what Explore serves.
        for (const subset of [ScaleSubset.FullScale, ScaleSubset.Pentatonic]) {
          for (let tonic = 0; tonic < 12; tonic++) {
            for (const p of explorePositions(tonic, mode, subset, standard)) {
              const midis = p.notes.map((n) => noteAt(standard, n.position).midi);
              if (new Set(midis).size !== midis.length) return false;
            }
          }
        }
      }
      return true;
    })());
    check("Explore serves the sheet's diagrams, ascending the neck", (() => {
      for (const mode of [CagedMode.Major, CagedMode.Minor]) {
        if (explorePositions(G, mode, ScaleSubset.FullScale, standard).length !== 7) return false;
        if (explorePositions(G, mode, ScaleSubset.Pentatonic, standard).length !== 5) return false;
        for (const subset of [ScaleSubset.FullScale, ScaleSubset.Pentatonic, ScaleSubset.Triad]) {
          const ps = explorePositions(G, mode, subset, standard);
          if (ps.some((p, i) => p.index !== i + 1)) return false;
          const firsts = ps.map((p) => p.firstFret);
          if (JSON.stringify(firsts) !== JSON.stringify([...firsts].sort((a, b) => a - b))) return false;
          for (const p of ps) {
            const want = resolveBox(G, p.box, mode, subset, standard, 22, p.pattern);
            if (JSON.stringify(want) !== JSON.stringify(p.notes)) return false;
          }
        }
      }
      return true;
    })());
    // The five windows he circled, now served as the boxes that replaced them.
    // String index 3 = 3rd string (G), 4 = 2nd string (B). His fifth circle was the old
    // nut window (frets 0-4), which was box 5 an octave down — box 5 at 12-16 carries
    // the same shape minus the same duplicate.
    check("Nadav's Explore removals - the duplicated pitch is gone from each box", (() => {
      const major = explorePositions(G, CagedMode.Major, ScaleSubset.FullScale, standard);
      const stringOf = (box: CagedBox, pattern: number, si: number) => {
        const p = major.find((x) => x.box === box && x.pattern === pattern);
        if (!p) return null;
        return p.notes.filter((n) => n.position.stringIndex === si).map((n) => n.position.fret).sort((a, b) => a - b);
      };
      const want: [CagedBox, number, number, number[]][] = [
        [CagedBox.POS2, 1, 3, [5, 7]],       // was 5-9: the 6th (G string fret 9) is gone
        [CagedBox.POS3, 1, 3, [7, 9]],       // was 7-11: the 7th (G string fret 11) is gone
        [CagedBox.POS4, 1, 4, [10, 12]],     // was 8-12: the 1 (B string fret 8) is gone
        [CagedBox.POS4, 2, 4, [12, 13]],     // was 10-14: the 2 (B string fret 10) is gone
        [CagedBox.POS5, 1, 3, [12, 14]],     // was 0-4: the 3rd (G string fret 4 / 16) is gone
      ];
      return want.every(([b, pat, si, frets]) => JSON.stringify(stringOf(b, pat, si)) === JSON.stringify(frets));
    })());
    check("triads match Nadav's D major sheet exactly", (() => {
      const sheet: [number[], number[][]][] = [
        [[3, 4, 5], [[2, 3, 2], [7, 7, 5], [11, 10, 10]]],
        [[2, 3, 4], [[4, 2, 3], [7, 7, 7], [12, 11, 10]]],
        [[1, 2, 3], [[5, 4, 2], [9, 7, 7], [12, 12, 11]]],
        [[0, 1, 2], [[5, 5, 4], [10, 9, 7], [14, 12, 12]]],
      ];
      const got = triadInversions(parsePitchClass("D"), "maj", standard);
      if (got.length !== 12) return false;
      for (const [group, want] of sheet) {
        const mine = got.filter((t) => JSON.stringify(t.strings) === JSON.stringify(group)).map((t) => t.frets);
        if (JSON.stringify(mine) !== JSON.stringify(want)) return false;
      }
      return true;
    })());
    check("triad shapes are movable and ascend the neck (no open strings)", (() => {
      for (const key of [0, 2, 5, 7, 9]) for (const q of ["maj", "min"] as const) {
        const got = triadInversions(key, q, standard);
        if (got.length !== 12) return false;
        for (const g of TRIAD_GROUPS) {
          const inGroup = got.filter((t) => JSON.stringify(t.strings) === JSON.stringify(g));
          if (inGroup.length !== 3) return false;
          if (new Set(inGroup.map((t) => t.inversion)).size !== 3) return false;
          const lows = inGroup.map((t) => Math.min(...t.frets));
          if (JSON.stringify(lows) !== JSON.stringify([...lows].sort((x, y) => x - y))) return false;
          for (const t of inGroup) {
            if (t.frets.some((f) => f < 1)) return false;
            if (new Set(t.frets.map((f, i) => pcAt(t.strings[i], f))).size !== 3) return false;
          }
        }
      }
      return true;
    })());
    check("every triad-run voicing is a chord tone of the key", (() => {
      for (const { quality, shape } of triadRun(G, standard)) {
        const want = new Set([G, (G + (quality === "maj" ? 4 : 3)) % 12, (G + 7) % 12]);
        for (let i = 0; i < 3; i++) if (!want.has(pcAt(shape.strings[i], shape.frets[i]))) return false;
      }
      return true;
    })());
  }
}

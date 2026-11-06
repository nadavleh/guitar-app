// Area "drums" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- drums

import { DrumBlock, BUILTIN_BLOCKS, PresetTrack, PercussionCatalog, PercussionPattern, PercussionMeter, swungSlotMs, slotMs, voiceCount, SwingModel, BUILTIN_PATTERNS, STUDY_PATTERNS, PRESET_TRACKS } from "../../src/theory";
import { check } from "./harness";

export function run(): void {

  // --- Percussion: dynamic kit, add/remove, pattern round-trip ---
  const M = PercussionMeter.DEFAULT;
  const empty = PercussionPattern.empty();
  check("default kit is surdo, tamborim, bongo",
    empty.instruments.map((i) => i.id).join(",") === "surdo,tamborim,bongo" &&
    empty.instruments.map((i) => i.id).join(",") === PercussionCatalog.DEFAULT_KIT.map((i) => i.id).join(","));
  // cycle: null → 0 → 1 → 2 → null for Surdo (3 voices)
  let p = empty;
  const cy: (number | null)[] = [];
  for (let i = 0; i < 4; i++) { p = p.cycled(PercussionCatalog.Surdo, 0); cy.push(p.voiceAt(PercussionCatalog.Surdo, 0)); }
  check("Surdo cell cycles 0,1,2,null", cy[0] === 0 && cy[1] === 1 && cy[2] === 2 && cy[3] === null);
  check("Surdo has 3 voices, Pandeiro 8 (recorded articulations)", voiceCount(PercussionCatalog.Surdo) === 3 && voiceCount(PercussionCatalog.Pandeiro) === 8);
  // add an instrument → silent row appended; round-trips through encode/decode
  const cuica = PercussionCatalog.byId("cuica")!;
  const withCuica = empty.addInstrument(cuica).cycled(cuica, 2).cycled(PercussionCatalog.Surdo, 0);
  check("addInstrument appends a row", withCuica.hasInstrument(cuica) &&
    withCuica.instruments.length === PercussionCatalog.DEFAULT_KIT.length + 1);
  const rt2 = PercussionPattern.decode(withCuica.encode());
  check("pattern with added instrument round-trips", rt2 !== null && rt2.encode() === withCuica.encode());
  check("removeInstrument drops the row", !withCuica.removeInstrument(cuica).hasInstrument(cuica));
  // decode skips unknown instrument ids
  const withBogus = withCuica.encode() + "|bogus=" + Array.from({ length: withCuica.slots }, () => "-").join(",");
  const rtBogus = PercussionPattern.decode(withBogus);
  check("decode skips unknown instrument ids", rtBogus !== null && rtBogus.instruments.every((i) => i.id !== "bogus"));
  // per-track swing: encoded as an "@N" id suffix, round-trips, drops with the track
  const swung = withCuica.withTrackSwing("cuica", 33);
  check("per-track swing encodes as id@33 and round-trips",
    swung.encode().includes("cuica@33=") &&
    PercussionPattern.decode(swung.encode())?.trackSwingOf("cuica") === 33 &&
    swung.withTrackSwing("cuica", 0).encode().includes("cuica@") === false &&
    swung.removeInstrument(cuica).trackSwing.size === 0);
  // per-track volume: "%N" suffix, combines with swing on one head, round-trips
  const quiet = swung.withTrackVolume("cuica", 20);
  check("per-track volume encodes as id@33%20 and round-trips",
    quiet.encode().includes("cuica@33%20=") &&
    PercussionPattern.decode(quiet.encode())?.trackVolumeOf("cuica") === 20 &&
    quiet.withTrackVolume("cuica", 100).encode().includes("%") === false);

  // --- translatedRow: the Shift control with one track selected (mirrors the Kotlin
  // test `translatedRow rotates one track and leaves the rest alone`) ---
  {
    const two = empty.cycled(PercussionCatalog.Surdo, 0).cycled(PercussionCatalog.Tamborim, 1);
    const moved = two.translatedRow(PercussionCatalog.Surdo, 3);
    check("translatedRow moves only the named track",
      moved.voiceAt(PercussionCatalog.Surdo, 0) === null &&
      moved.voiceAt(PercussionCatalog.Surdo, 3) === two.voiceAt(PercussionCatalog.Surdo, 0) &&
      moved.voiceAt(PercussionCatalog.Tamborim, 1) === two.voiceAt(PercussionCatalog.Tamborim, 1));
    check("translatedRow wraps around and is reversible",
      two.translatedRow(PercussionCatalog.Surdo, -1).voiceAt(PercussionCatalog.Surdo, two.slots - 1) === two.voiceAt(PercussionCatalog.Surdo, 0) &&
      two.translatedRow(PercussionCatalog.Surdo, 3).translatedRow(PercussionCatalog.Surdo, -3).encode() === two.encode() &&
      two.translatedRow(PercussionCatalog.Surdo, two.slots).encode() === two.encode());
    const all = two.instruments.reduce((acc, inst) => acc.translatedRow(inst, 3), two);
    check("translatedRow on every track === whole-pattern translate", all.encode() === two.translated(3).encode());
  }

  // --- Swing (samba microtiming): anchors 1st/2nd, anticipates 3rd & 4th; preserves loop length; 1/16 only ---
  const straightSum = Array.from({ length: 16 }, (_, i) => swungSlotMs(i, 100, 0, M)).reduce((a, b) => a + b, 0);
  const swungSum = Array.from({ length: 16 }, (_, i) => swungSlotMs(i, 100, 60, M)).reduce((a, b) => a + b, 0);
  check("straight slot = base slotMs", Math.abs(swungSlotMs(0, 100, 0, M) - slotMs(100)) < 1.5);
  check("swing preserves total loop length (±a few ms rounding)", Math.abs(straightSum - swungSum) <= 16);
  // At 100 % the feel is HEMIOLA-based (SwingModel), not the retired "2nd anchored"
  // model: the 2nd 16th is DELAYED toward 1/3 of the beat, the 3rd stays at 1/2, and the
  // 4th is pulled back to 2/3. Mirrors the two live Kotlin tests in PercussionPatternTest
  // (`full Hemiola swing delays the 2nd...` and `...onsets sit at the hemiola positions`).
  // bpm 120 makes the base slot exactly 125 ms, so the onsets are checkable to the ms.
  {
    const base = slotMs(120);          // 125 ms
    const hem = SwingModel.Hemiola;
    const d = [0, 1, 2, 3].map((i) => swungSlotMs(i, 120, 100, M, hem));
    check("hemiola swing: 1st→2nd stretches, 2nd→3rd shrinks, 4th→beat stretches",
      d[0] > base && d[1] < base && d[3] > base);
    check("hemiola swing keeps the beat length intact", d[0] + d[1] + d[2] + d[3] === base * 4);
    // Absolute onsets, in slot units: [0, 4/3, 2, 8/3] of the beat.
    const onset = (slot: number) => d.slice(0, slot).reduce((a, b) => a + b, 0);
    check("hemiola onsets sit at 0, 1/3, 1/2, 2/3 of the beat",
      onset(0) === 0 && onset(1) === Math.round((4 / 3) * base) &&
      onset(2) === 2 * base && onset(3) === Math.round((8 / 3) * base));
    // The DEFAULT model differs only in that it also nudges the 1st 16th (+d/2) and
    // pulls the 4th half as far — so onset(0) is NOT on the beat there.
    const dd = [0, 1, 2, 3].map((i) => swungSlotMs(i, 120, 100, M, SwingModel.Default));
    check("default model also preserves the beat length", dd[0] + dd[1] + dd[2] + dd[3] === base * 4);
    check("default model nudges the 1st 16th, hemiola does not",
      swungSlotMs(0, 120, 100, M, SwingModel.Default) !== swungSlotMs(0, 120, 100, M, hem));
  }
  // non-1/16 grids ignore swing entirely
  const eighths = new PercussionMeter(2, 2, 4, 8);
  check("swing does nothing off a 1/16 grid", swungSlotMs(1, 100, 100, eighths) === slotMs(100, 8));

  // --- Built-in grooves are valid (16 slots, in-range voice indices) ---
  // Nadav's tan-tan study, pinned exactly as his saved beat exported it; NL + downbeat
  // were removed (mirrors the Kotlin test `only the Tantan teleco offbeat groove...`).
  check("Tantan NL + downbeat grooves are gone",
    !BUILTIN_PATTERNS.some((x) => x.name === "Tantan Teleco NL" || x.name === "Tantan Telco downbeat"));
  for (const [name, encoded] of [
    ["Tantan Telecoteco", "M:2,2,4,16;surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2|tamborim=-,0,-,0,-,-,0,3002,0,-,0,-,0,-,-,0"],
  ] as [string, string][]) {
    const b = BUILTIN_PATTERNS.find((x) => x.name === name);
    check(`built-in "${name}" is pinned as exported (67 BPM)`,
      b !== undefined && b.bpm === 67 && b.pattern.encode() === encoded);
  }
  check("the offbeat variant keeps its 25 % tamborim tap on the 'a' of beat 2",
    (() => {
      const b = BUILTIN_PATTERNS.find((x) => x.name === "Tantan Telecoteco")!;
      return b.pattern.voiceAt(PercussionCatalog.Tamborim, 7) === 2 &&
        b.pattern.dynLevelAt(PercussionCatalog.Tamborim, 7) === 3;
    })());

  check("bongo grooves name their tamborim track Tamborim-Tresillo (round-trips, = Kotlin)",
    BUILTIN_PATTERNS.filter((b) => b.name.endsWith("(Bongo)")).every((b) => {
      const rt = PercussionPattern.decode(b.pattern.encode());
      return b.pattern.instruments.find((i) => i.id === "tamborim")?.displayName === "Tamborim-Tresillo" &&
        rt?.instruments.find((i) => i.id === "tamborim")?.displayName === "Tamborim-Tresillo" &&
        rt.encode() === b.pattern.encode();
    }) && !PercussionPattern.empty().encode().includes("!"));
  check("Samba Clap became the two-bar Tresillo track preset (= Kotlin)",
    !STUDY_PATTERNS.some((b) => b.name.startsWith("Samba Clap")) &&
    PRESET_TRACKS.find((p) => p.label === "Tresillo")?.template.map((v, i) => (v === null ? -1 : i)).filter((i) => i >= 0).join() === "0,3,6,8,11,14");
  let builtinsOk = BUILTIN_PATTERNS.length >= 5;
  for (const b of BUILTIN_PATTERNS) {
    const rt = PercussionPattern.decode(b.pattern.encode());
    if (!rt || rt.encode() !== b.pattern.encode()) builtinsOk = false;
  }
  check("built-in grooves are valid & round-trip", builtinsOk);
  check("Son v1 (2:3) matches the Kotlin twin, clave on 2, 3 | 1, 2&, 4", (() => {
    const b = BUILTIN_PATTERNS.find((x) => x.name === "Son v1 (2:3)");
    return b !== undefined && b.bpm === 140 && b.pattern.encode() ===
      "M:2,4,4,8;claves%60=-,-,0,-,0,-,-,-,0,-,-,0,-,-,0,-|timbales%60=102,-,102,-,2,102,-,102,102,-,2,102,-,102,-,2|conga%60=1,3,2,3,1,3,0,0,1,3,2,-,-,3,0,0|tumba%60=-,-,-,-,-,-,-,-,-,-,-,0,0,-,-,-|cowbell%60!Campana=0,-,1,-,0,-,1,1,0,-,1,1,0,-,1,1";
  })());
  check("Rumba v1 (2:3) matches the Kotlin twin, clave on 2, 3 | 1, 2&, 4&", (() => {
    const b = BUILTIN_PATTERNS.find((x) => x.name === "Rumba v1 (2:3)");
    return b !== undefined && b.bpm === 140 && b.pattern.encode() ===
      "M:2,4,4,8;claves%60=-,-,0,-,0,-,-,-,0,-,-,0,-,-,-,0|timbales%60=102,-,102,-,2,102,-,102,102,-,2,102,-,102,-,102|conga%60=1,3,2,3,1,3,0,0,1,3,2,-,-,3,0,0|tumba%60=-,-,-,-,-,-,-,-,-,-,-,0,0,-,-,-|cowbell%60!Campana=0,-,1,-,0,-,1,1,0,-,1,1,0,-,1,1";
  })());
  check("Son v2 (3:2) matches the Kotlin twin, clave on 1, 2&, 4 | 2, 3", (() => {
    const b = BUILTIN_PATTERNS.find((x) => x.name === "Son v2 (3:2)");
    return b !== undefined && b.bpm === 140 && b.pattern.encode() ===
      "M:2,4,4,8;claves%60=0,-,-,0,-,-,0,-,-,-,0,-,0,-,-,-|timbales%60=2,-,2,2,-,2,2,-,2,-,2,2,-,2,-,2|" +
      "conga%60=1,3,2,3,1,-,0,0,1,3,2,3,1,3,0,0|tumba%60=-,-,-,-,-,0,-,-,-,-,-,-,-,-,-,-|bongo%60=0,2,3,2,0,2,1,2,0,2,3,2,0,2,1,2|cowbell%60!Campana=0,-,1,1,0,-,1,1,0,-,1,-,0,-,1,1";
  })());
  // Pins the twin of PercussionBuiltins.CASA_2_LEVADA — his Casa 2 export, the one
  // groove with no surdo (two tamborims, the first at 97 %).
  check("the Casa 2 cavaco levada groove is in the list, exactly as exported", (() => {
    const b = BUILTIN_PATTERNS.find((x) => x.name === "Casa 2 — Cavaco Levada");
    if (!b || b.bpm !== 92) return false;
    return b.pattern.encode() === "M:2,2,4,16;tamborim%97=0,-,0,1,2,0,-,0,1,2,0,1,0,-,0,1|tamborim#2=0,-,1,0,1,0,-,0,1,0,-,0,1,0,0,1";
  })());

  // --- Drum accents: toggle, survive voice cycling, round-trip encode/decode ---
  {
    const surdo = PercussionCatalog.Surdo;
    let ap = PercussionPattern.empty().cycled(surdo, 0).accentToggled(surdo, 0);
    check("accent toggles on and keeps the voice", ap.isAccented(surdo, 0) && ap.voiceAt(surdo, 0) === 0);
    ap = ap.cycled(surdo, 0);
    check("accent survives voice cycling", ap.isAccented(surdo, 0) && ap.voiceAt(surdo, 0) === 1);
    const art = PercussionPattern.decode(ap.encode());
    check("accent round-trips encode/decode", art !== null && art.isAccented(surdo, 0) && art.voiceAt(surdo, 0) === 1);
    check("accent toggles off / silent no-op", !ap.accentToggled(surdo, 0).isAccented(surdo, 0) && ap.accentToggled(surdo, 5).encode() === ap.encode());
    check("decode rejects accented out-of-range voice", PercussionPattern.decode(PercussionPattern.empty().encode().replace("-", "109")) === null);
  }

  // --- Drum blocks: the `opening` (entrada) must survive every edit ---
  // This module had NO parity assertions, which is how two data-loss bugs lived here: both
  // withCell and withPhraseCount rebuilt BlockTrack as a fresh object literal without
  // spreading `...t`, so editing any cell (or the column count) silently deleted the
  // track's opening — from the grid, from playback and from the saved block. Kotlin uses
  // t.copy(...) and was unaffected.
  {
    const opening: PresetTrack = { label: "Op", encoded: "x" } as unknown as PresetTrack;
    const cellA: PresetTrack = { label: "A", encoded: "y" } as unknown as PresetTrack;
    let blk = new DrumBlock("t", [{ instrument: "tamborim", cells: [null, null], opening }], 2);
    check("withCell keeps the track's opening", blk.withCell(0, 0, cellA).tracks[0].opening === opening);
    check("withCell still sets the cell", blk.withCell(0, 1, cellA).tracks[0].cells[1] === cellA);
    check("withPhraseCount keeps the opening", blk.withPhraseCount(4).tracks[0].opening === opening);
    check("withPhraseCount resizes", blk.withPhraseCount(4).tracks[0].cells.length === 4);
    // ...and the shipped built-in that exercises it round-trips through an edit.
    const builtin = BUILTIN_BLOCKS.find((b) => b.startsWith("Tamborim Block="));
    check("a built-in block with an entrada exists", !!builtin);
  }
}

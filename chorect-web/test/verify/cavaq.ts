// Area "cavaq" of the web parity checks (split out of the old monolithic verify.ts).
// Run alone: npm run verify -- cavaq

import { QUALITIES, cavaqFormsFor, cavaqDropOpenStringCopies, cavaqDgbd, ChordShape as CShape } from "../../src/theory";
import { check } from "./harness";
import { cavaqSequenceById } from "../../src/theory/cavaqSequences";
import { resolveNamed, parseChord, cavaquinhoVoicingPool } from "../../src/theory";

export function run(): void {

  // --- Sou Mais o Samba extended A / B (mirror of CavaqSequencesTest) ---
  {
    const syms = (id: string) => resolveNamed(cavaqSequenceById(id)!.prog, 0).map((c) => c.symbol);
    const a = syms("sou_mais_o_samba_a_ext");
    const b = syms("sou_mais_o_samba_b_ext");
    check("cavaq: SoMiSo A extended resolves in C", a.join() === "Dm9,G13,C6add9,A7b9,Dm9,G7b9,C6add9");
    check("cavaq: SoMiSo B extended resolves in C", b.join() === "C6add9,Dm9,G13,C6,Cdim7,Dm9");
    const pool = (sym: string) => {
      const [r, q] = parseChord(sym)!;
      return cavaquinhoVoicingPool(r, q, cavaqDgbd, 14).map((s) => s.frets.join());
    };
    check("cavaq: every SoMiSo extended chord has a grip", [...a, ...b].every((s) => pool(s).length > 0));
    check("cavaq: ChatGPT chart grips are in the pool",
      pool("Dm9").includes("3,2,1,2") && pool("G13").includes("3,2,0,2") &&
      pool("C6add9").includes("2,2,1,0") && pool("A7b9").includes("5,3,2,2") &&
      pool("G7b9").includes("3,1,0,0") && pool("C6").includes("2,2,1,2"));
  }

  // --- Cavaquinho forms (mirror of CavaqFormsTest) ---
  {
    const dm = cavaqFormsFor(2, QUALITIES.get("m")!, cavaqDgbd);
    check("cavaq Dm forms: b3, 5, R in the bass",
      dm.map((f) => f.bass).join() === "3,7,0" &&
      dm.map((f) => f.shape.frets.join(" ")).join("|") === "3 2 3 3|7 7 6 7|12 10 10 12");
    const c = cavaqFormsFor(0, QUALITIES.get("")!, cavaqDgbd).find((f) => f.shape.frets.join() === "5,5,5,5");
    check("cavaq C 5555 branches to 2555", !!c && c.branches.some((b) => b.frets.join() === "2,5,5,5"));
    let counts = true;
    for (let r = 0; r < 12; r++) {
      for (const sym of ["", "m", "dim", "aug", "sus4"]) if (cavaqFormsFor(r, QUALITIES.get(sym)!, cavaqDgbd).length !== 3) counts = false;
      for (const sym of ["7", "maj7", "m7", "m7b5", "dim7", "6", "m6", "9", "mMaj7"]) if (cavaqFormsFor(r, QUALITIES.get(sym)!, cavaqDgbd).length !== 4) counts = false;
    }
    check("cavaq: 3 forms per triad, 4 per four-note chord, every key", counts);
    const mk = (fr: number[]) => new CShape({ chordName: "Dm", root: 2, quality: QUALITIES.get("m")!, frets: fr, tuning: cavaqDgbd });
    const a0 = mk([0, 2, 3, 3]), b0 = mk([3, 2, 3, 3]), c0 = mk([7, 7, 6, 7]);
    check("cavaq: open-string copy dropped", cavaqDropOpenStringCopies([a0, b0, c0]).length === 2 &&
      cavaqDropOpenStringCopies([a0, c0]).length === 2);
  }
}

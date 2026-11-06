// Mirror of theory/.../CavaqForms.kt — the cavaquinho's movable chord forms.
import { PitchClass, Tuning, spellPc, stringCount } from "./core";
import { ChordQuality, ChordShape } from "./chords";

/** One movable cavaquinho form of a chord. `bass` = semitones (0..11) of the LOWEST
 *  sounding note above the root ("3 in the bass"). `branches` = the form's branch-outs:
 *  the same grip with ONE string moved to another chord tone within reach. */
export interface CavaqForm { bass: number; shape: ChordShape; branches: ChordShape[] }

/** The chord types offered in the Shapes picker, in display order. */
export const CAVAQ_FORM_QUALITIES: string[] = [
  "", "m", "7", "maj7", "m7", "6", "m6", "9", "maj9", "m9", "13", "add9",
  "sus4", "sus2", "7sus4", "mMaj7", "dim", "dim7", "m7b5", "aug", "7#5", "7b9",
];

/** Name of a chord tone by its semitones above the root ("R", "♭3", "5", "♭7"…). */
export function cavaqBassLabel(semis: number): string {
  return ["R", "♭9", "9", "♭3", "3", "4", "♭5", "5", "♯5", "6", "♭7", "7"][((semis % 12) + 12) % 12];
}

/** The (≤ 4) tones the forms sound: drop the 5th, then 9th, then 11th until four remain. */
export function cavaqFormTones(quality: ChordQuality): Set<number> {
  let tones = new Set(quality.intervals.map((i) => ((i % 12) + 12) % 12));
  for (const drop of [7, 2, 5]) if (tones.size > 4 && tones.has(drop)) { tones = new Set(tones); tones.delete(drop); }
  return tones;
}

const mod12 = (x: number) => ((x % 12) + 12) % 12;

/** One form per chord tone in the bass: the most compact all-fretted (movable) grip,
 *  span ≤ 3, widening to 4 then 5 only for a bass with no tighter grip. A form wholly
 *  at fret 12+ shows an octave lower. Sorted up the neck. */
export function cavaqFormsFor(root: PitchClass, quality: ChordQuality, tuning: Tuning, maxFret = 15): CavaqForm[] {
  const tones = cavaqFormTones(quality);
  const pcs = new Set([...tones].map((t) => (root + t) % 12));
  const n = stringCount(tuning);
  const opens = tuning.openStrings.map((o) => o.midi);
  const name = `${spellPc(root)}${quality.symbol}`;
  const sounds = (fr: number[]) => fr.map((f, s) => opens[s] + f);
  const sameSet = (a: Set<number>, b: Set<number>) => a.size === b.size && [...a].every((x) => b.has(x));
  const found = new Map<number, number[]>();
  for (const maxSpan of [3, 4, 5]) {
    const best = new Map<number, { span: number; lo: number; fr: number[] }>();
    const frets = new Array<number>(n).fill(1);
    for (;;) {
      const lo = Math.min(...frets), hi = Math.max(...frets);
      if (hi - lo <= maxSpan) {
        const m = sounds(frets);
        let unison = false;
        for (let i = 0; i < n - 1; i++) if (m[i] === m[i + 1]) unison = true;
        if (!unison && sameSet(new Set(m.map((x) => x % 12)), pcs)) {
          const bass = mod12(Math.min(...m) % 12 - root);
          const cur = best.get(bass);
          const span = hi - lo;
          if (!found.has(bass) && (!cur || span < cur.span || (span === cur.span && lo < cur.lo)))
            best.set(bass, { span, lo, fr: frets.slice() });
        }
      }
      let i = n - 1;
      for (; i >= 0; i--) { frets[i]++; if (frets[i] <= maxFret) break; frets[i] = 1; }
      if (i < 0) break;
    }
    for (const [b, v] of best) found.set(b, v.fr);
    if (found.size === tones.size) break;
  }
  const mains = new Map<number, number[]>();
  for (const [b, fr] of found) mains.set(b, Math.min(...fr) >= 12 ? fr.map((f) => f - 12) : fr);
  const mainKeys = new Set([...mains.values()].map((f) => f.join(",")));
  const bassOf = (fr: number[]) => mod12(Math.min(...sounds(fr)) % 12 - root);
  const shape = (fr: number[]) => new ChordShape({ chordName: name, root, quality, frets: fr, tuning,
    templateName: `${cavaqBassLabel(bassOf(fr))} in the bass` });
  const out: CavaqForm[] = [];
  for (const [bass, fr] of mains) {
    const minFret = Math.min(...fr) === 0 ? 0 : 1;
    const branches: number[][] = [];
    const seen = new Set<string>();
    for (let s = 0; s < n; s++) for (let f = minFret; f <= maxFret; f++) {
      if (f === fr[s] || !pcs.has((opens[s] + f) % 12)) continue;
      const alt = fr.slice(); alt[s] = f;
      const fretted = alt.filter((x) => x > 0);
      if (fretted.length && Math.max(...fretted) - Math.min(...fretted) > 4) continue;
      const m = sounds(alt);
      let unison = false;
      for (let i = 0; i < n - 1; i++) if (m[i] === m[i + 1]) unison = true;
      const k = alt.join(",");
      if (unison || !sameSet(new Set(m.map((x) => x % 12)), pcs) || mainKeys.has(k) || seen.has(k)) continue;
      seen.add(k); branches.push(alt);
    }
    out.push({ bass, shape: shape(fr), branches: branches.map(shape) });
  }
  const low = (f: CavaqForm) => Math.min(...f.shape.frets.filter((x): x is number => x !== null));
  return out.sort((a, b) => low(a) - low(b) || a.bass - b.bass);
}

/** Drop a grip that only adds open strings under ANOTHER grip's fretted notes (Dm's
 *  0 2 3 3 is 3 2 3 3 with the open D in the bass) — the same form, not a new one. */
export function cavaqDropOpenStringCopies(shapes: ChordShape[]): ChordShape[] {
  const opensOf = (s: ChordShape) => s.frets.filter((f) => f === 0).length;
  return shapes.filter((a) => {
    const o = opensOf(a);
    return !(o > 0 && shapes.some((b) => opensOf(b) < o &&
      a.frets.every((f, s) => f === null || f === 0 || b.frets[s] === f)));
  });
}

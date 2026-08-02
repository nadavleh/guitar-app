// Blocks: phrase sequencing for the drum machine. Mirror of theory/.../DrumBlocks.kt
// (see docs/superpowers/specs/2026-07-22-drum-blocks-design.md).
//
// A block is a grid of tracks × phrase columns. Each row (BlockTrack) is one
// instrument; each cell holds a phrase (a PresetTrack chunk — one instrument,
// 16 slots of 2/4 in 16ths, its own swing) or null (silence for that column).
// Playback loops column by column: all tracks sound their column-c phrase
// simultaneously — each with ITS phrase's swing micro-timing, which preserves
// beat anchors, so tracks never drift apart — then the block advances to
// column c+1 on the straight clock.

import {
  PercussionInstrument, PercussionCatalog, PRESET_TRACKS,
  PresetTrack, presetByLabel, basePercussionId,
} from "./percussion";

/** The phrase library lookup used by block encode/decode. */
export type PresetResolver = (label: string) => PresetTrack | null | undefined;

export const MAX_BLOCK_PHRASES = 8;

export interface BlockTrack {
  instrument: PercussionInstrument;
  /** One phrase per column; null = silent for that column. Size == phraseCount. */
  cells: (PresetTrack | null)[];
  /** Optional OPENING cell: on the block's FIRST pass this plays instead of
   *  cells[0]; every loop after plays cells[0] and skips the opening. Null/absent
   *  = this track plays cells[0] even on the first pass. */
  opening?: PresetTrack | null;
}

export class DrumBlock {
  constructor(
    readonly name: string,
    readonly tracks: ReadonlyArray<BlockTrack>,
    readonly phraseCount: number,
  ) {}

  static empty(name = "Block 1", phraseCount = 4): DrumBlock {
    return new DrumBlock(name, [], phraseCount);
  }

  /** Resize the column count, keeping existing cells (new columns empty). */
  withPhraseCount(n: number): DrumBlock {
    const c = Math.min(Math.max(n, 1), MAX_BLOCK_PHRASES);
    return new DrumBlock(this.name, this.tracks.map((t) => ({
      instrument: t.instrument,
      cells: Array.from({ length: c }, (_, i) => t.cells[i] ?? null),
    })), c);
  }

  /** Append an empty track for `instrument` (instruments may repeat). */
  withTrack(instrument: PercussionInstrument): DrumBlock {
    return new DrumBlock(this.name, [...this.tracks, { instrument, cells: Array(this.phraseCount).fill(null) }], this.phraseCount);
  }

  /** Set a track's OPENING cell (plays instead of its first phrase on pass 1). */
  withOpeningCell(track: number, phrase: PresetTrack | null): DrumBlock {
    if (track < 0 || track >= this.tracks.length) return this;
    const tracks = this.tracks.map((t, i) => (i !== track ? t : { ...t, opening: phrase }));
    return new DrumBlock(this.name, tracks, this.phraseCount);
  }

  withoutTrack(index: number): DrumBlock {
    if (index < 0 || index >= this.tracks.length) return this;
    return new DrumBlock(this.name, this.tracks.filter((_, i) => i !== index), this.phraseCount);
  }

  withCell(track: number, col: number, phrase: PresetTrack | null): DrumBlock {
    if (track < 0 || track >= this.tracks.length || col < 0 || col >= this.phraseCount) return this;
    const tracks = this.tracks.map((t, i) => i !== track ? t : {
      instrument: t.instrument,
      cells: t.cells.map((c, j) => (j === col ? phrase : c)),
    });
    return new DrumBlock(this.name, tracks, this.phraseCount);
  }

  withName(name: string): DrumBlock { return new DrumBlock(name, this.tracks, this.phraseCount); }

  /** Merge with `other`: union of the two blocks' tracks. Only blocks with the
   *  same phrase count merge (all phrases share the 16-slot length); null otherwise. */
  mergedWith(other: DrumBlock, newName = `${this.name} + ${other.name}`): DrumBlock | null {
    if (other.phraseCount !== this.phraseCount) return null;
    return new DrumBlock(newName, [...this.tracks, ...other.tracks], this.phraseCount);
  }

  isEmpty(): boolean { return this.tracks.every((t) => t.cells.every((c) => c === null)); }

  /** Serialize: "name=instId:lbl,lbl,…|instId:…" — phrases referenced by label
   *  (empty cell = empty label). A cell whose swing was overridden away from its
   *  library default is written "label@swing". Labels contain none of '=', '|',
   *  ':', ',' (or a trailing "@<digits>"). */
  encode(resolve: PresetResolver = presetByLabel): string {
    const cellStr = (c: PresetTrack): string => {
      const libSwing = resolve(c.label)?.swing ?? 0;
      return (c.swing ?? 0) !== libSwing ? `${c.label}@${c.swing ?? 0}` : c.label;
    };
    return this.name + "=" + this.tracks.map((t) => {
      // A leading "^cell" is the track's OPENING (plays once, pass 1).
      const prefix = t.opening ? "^" + cellStr(t.opening) + "," : "";
      return t.instrument.id + ":" + prefix + t.cells.map((c) => (c ? cellStr(c) : "")).join(",");
    }).join("|");
  }

  /** Parse a value produced by `encode`; null on structural garbage. Unknown
   *  phrase labels become empty cells (forward compatibility). */
  static decode(s: string, resolve: PresetResolver = presetByLabel): DrumBlock | null {
    const eq = s.indexOf("=");
    if (eq <= 0) return null;
    const name = s.substring(0, eq);
    const body = s.substring(eq + 1);
    if (!body) return null;
    const tracks: BlockTrack[] = [];
    let phraseCount = -1;
    for (const trackStr of body.split("|")) {
      const colon = trackStr.indexOf(":");
      if (colon <= 0) return null;
      const inst = PercussionCatalog.resolve(trackStr.substring(0, colon));
      if (!inst) continue;
      const parseCell = (lbl: string): PresetTrack | null => {
        if (!lbl) return null;
        // "label@swing" = a per-cell swing override on the library phrase.
        const at = lbl.lastIndexOf("@");
        const overridden = at > 0 ? parseInt(lbl.substring(at + 1), 10) : NaN;
        if (!Number.isNaN(overridden)) {
          const base = resolve(lbl.substring(0, at));
          return base ? { ...base, swing: Math.min(Math.max(overridden, 0), 100) } : null;
        }
        return resolve(lbl) ?? null;
      };
      let parts = trackStr.substring(colon + 1).split(",");
      // A leading "^cell" is the track's OPENING (plays once, pass 1).
      let opening: PresetTrack | null = null;
      if (parts.length > 0 && parts[0].startsWith("^")) {
        opening = parseCell(parts[0].substring(1));
        parts = parts.slice(1);
      }
      const cells = parts.map(parseCell);
      if (phraseCount === -1) phraseCount = cells.length;
      if (cells.length !== phraseCount) return null;
      tracks.push({ instrument: inst, cells, opening });
    }
    if (phraseCount < 1 || phraseCount > MAX_BLOCK_PHRASES) return null;
    return new DrumBlock(name, tracks, phraseCount);
  }
}

/**
 * Persistence codec for USER-DEFINED phrases (custom track presets): a track
 * built in the Beat editor, saved by name, joining the phrase library. A custom
 * phrase with a built-in's label REPLACES it everywhere (edit-and-resave).
 * Format: "label=instBaseId:swing:cells" (cells = raw values, "-" = silent).
 * Labels must not contain '=', ':', ',', '|', '@', '~', or newlines.
 */
export function encodePresetTrack(p: PresetTrack): string {
  return p.label + "=" + basePercussionId(p.instrument.id) + ":" + (p.swing ?? 0) + ":" +
    p.template.map((c) => (c === null ? "-" : String(c))).join(",");
}

export function decodePresetTrack(s: string): PresetTrack | null {
  const eq = s.indexOf("=");
  if (eq <= 0) return null;
  const label = s.substring(0, eq);
  const parts = s.substring(eq + 1).split(":");
  if (parts.length !== 3) return null;
  const inst = PercussionCatalog.byId(parts[0]);
  if (!inst) return null;
  const swing = parseInt(parts[1], 10);
  if (Number.isNaN(swing)) return null;
  const cells: (number | null)[] = [];
  for (const c of parts[2].split(",")) {
    if (c === "-") { cells.push(null); continue; }
    const n = parseInt(c, 10);
    if (Number.isNaN(n)) return null;
    cells.push(n);
  }
  if (cells.length !== 16) return null;
  return { label, instrument: inst, template: cells, swing: Math.min(Math.max(swing, 0), 100) };
}

/** BUILT-IN blocks (encoded DrumBlock strings): offered in the Blocks Load…
 *  list above the user's saved blocks, decoded against the CURRENT phrase
 *  library so custom phrases with matching labels still substitute. Keep in
 *  sync with theory/DrumBlocks.kt's BUILTIN_BLOCKS. */
export const BUILTIN_BLOCKS: string[] = [
  // Nadav's tamborim study block: Entrada 1 opening, then teleco-teco
  // alternating with its three variations across 8 phrases.
  "Tamborim Block=tamborim:^Tamborim — Entrada 1,Tamborim — Teleco-teco," +
    "Tamborim — Telecoteco Var 1,Tamborim — Teleco-teco,Tamborim — Telecoteco Var 2," +
    "Tamborim — Teleco-teco,Tamborim — Telecoteco Var 3,Tamborim — Telecoteco Var 1," +
    "Tamborim — Telecoteco Var 2",
];

/** Block file (export / import): a JSON envelope around one block PLUS the
 *  user-defined phrases it references, so a block is portable to another device
 *  (the phrases are restored into the library on import). */
export function encodeBlockFile(blockEncoded: string, customPhrases: PresetTrack[]): string {
  return JSON.stringify({
    format: "chorect-block",
    version: 1,
    block: blockEncoded,
    phrases: customPhrases.map((p) => encodePresetTrack(p)).join("\n"),
  }, null, 2);
}

export function decodeBlockFile(text: string): { block: string; phrases: PresetTrack[] } | null {
  let obj: unknown;
  try { obj = JSON.parse(text); } catch { return null; }
  if (!obj || typeof obj !== "object") return null;
  const o = obj as Record<string, unknown>;
  if (o.format !== "chorect-block" || typeof o.block !== "string") return null;
  const phrases = typeof o.phrases === "string"
    ? o.phrases.split("\n").map(decodePresetTrack).filter((p): p is PresetTrack => p !== null)
    : [];
  return { block: o.block, phrases };
}

/** Phrase file (export / import): a JSON envelope around ONE user-defined
 *  phrase, so phrases can be shared between devices like beats. The Import
 *  button accepts both file kinds and dispatches on "format". */
export function encodePhraseFile(p: PresetTrack): string {
  return JSON.stringify({ format: "chorect-phrase", version: 1, phrase: encodePresetTrack(p) }, null, 2);
}

export function decodePhraseFile(text: string): PresetTrack | null {
  let obj: unknown;
  try { obj = JSON.parse(text); } catch { return null; }
  if (!obj || typeof obj !== "object") return null;
  const o = obj as Record<string, unknown>;
  if (o.format !== "chorect-phrase" || typeof o.phrase !== "string") return null;
  return decodePresetTrack(o.phrase);
}

/** The phrase library: built-ins with `custom` phrases merged in — a custom
 *  phrase whose label matches a built-in REPLACES it; new labels append. */
export function mergedPresets(custom: Iterable<PresetTrack>): PresetTrack[] {
  const byLabel = new Map<string, PresetTrack>();
  for (const p of PRESET_TRACKS) byLabel.set(p.label, p);
  for (const p of custom) byLabel.set(p.label, p);
  return [...byLabel.values()];
}

/** The stroke the return rule writes on beat 1: an open bass note (voice 0). */
const RETURN_DOWNBEAT_VOICE = 0;

/**
 * The 16-slot template a block cell actually plays: applies the RETURN RULE —
 * when the PREVIOUS column's phrase in this track (wrapping around the loop)
 * declares `addsReturnDownbeat`, slot 0 (beat 1) is forced to an OPEN BASS note
 * (voice 0) for THIS instance only, no matter which phrase follows. The library
 * phrase is never mutated — play it after any other phrase and it's unaltered.
 */
export function materializedTemplate(phrase: PresetTrack | null, prev: PresetTrack | null): (number | null)[] | null {
  if (!phrase) return null;
  if (!prev?.addsReturnDownbeat) return phrase.template;
  const out = phrase.template.slice();
  out[0] = RETURN_DOWNBEAT_VOICE;
  return out;
}

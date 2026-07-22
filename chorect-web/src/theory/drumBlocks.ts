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
  PercussionInstrument, PercussionCatalog, PERCUSSION_ACCENT, PRESET_TRACKS,
  PresetTrack, presetByLabel, basePercussionId,
} from "./percussion";

/** The phrase library lookup used by block encode/decode. */
export type PresetResolver = (label: string) => PresetTrack | null | undefined;

export const MAX_BLOCK_PHRASES = 8;

export interface BlockTrack {
  instrument: PercussionInstrument;
  /** One phrase per column; null = silent for that column. Size == phraseCount. */
  cells: (PresetTrack | null)[];
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
    return this.name + "=" + this.tracks.map((t) =>
      t.instrument.id + ":" + t.cells.map((c) => {
        if (!c) return "";
        const libSwing = resolve(c.label)?.swing ?? 0;
        return (c.swing ?? 0) !== libSwing ? `${c.label}@${c.swing ?? 0}` : c.label;
      }).join(",")).join("|");
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
      const cells = trackStr.substring(colon + 1).split(",").map((lbl): PresetTrack | null => {
        if (!lbl) return null;
        // "label@swing" = a per-cell swing override on the library phrase.
        const at = lbl.lastIndexOf("@");
        const overridden = at > 0 ? parseInt(lbl.substring(at + 1), 10) : NaN;
        if (!Number.isNaN(overridden)) {
          const base = resolve(lbl.substring(0, at));
          return base ? { ...base, swing: Math.min(Math.max(overridden, 0), 100) } : null;
        }
        return resolve(lbl) ?? null;
      });
      if (phraseCount === -1) phraseCount = cells.length;
      if (cells.length !== phraseCount) return null;
      tracks.push({ instrument: inst, cells });
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

/** The phrase library: built-ins with `custom` phrases merged in — a custom
 *  phrase whose label matches a built-in REPLACES it; new labels append. */
export function mergedPresets(custom: Iterable<PresetTrack>): PresetTrack[] {
  const byLabel = new Map<string, PresetTrack>();
  for (const p of PRESET_TRACKS) byLabel.set(p.label, p);
  for (const p of custom) byLabel.set(p.label, p);
  return [...byLabel.values()];
}

/**
 * The 16-slot template a block cell actually plays: applies the RETURN RULE —
 * when the PREVIOUS column's phrase (wrapping around the loop) declares
 * `addsReturnDownbeat` and this phrase's slot 0 is empty, slot 0 gains this
 * phrase's measure-2 downbeat stroke (slot 8), accented.
 */
export function materializedTemplate(phrase: PresetTrack | null, prev: PresetTrack | null): (number | null)[] | null {
  if (!phrase) return null;
  if (!prev?.addsReturnDownbeat || phrase.template[0] != null) return phrase.template;
  const m2 = phrase.template[8];
  if (m2 == null) return phrase.template;
  const out = phrase.template.slice();
  out[0] = (m2 % PERCUSSION_ACCENT) + PERCUSSION_ACCENT;
  return out;
}

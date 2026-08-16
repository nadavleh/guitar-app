// Samba percussion model, ported from theory/.../{Percussion,PercussionPattern}.kt.
//
// An instrument is identified by a stable `id` (used for sample asset names and
// pattern persistence) and carries an ordered list of voices. PercussionCatalog
// holds the full set plus the default kit a fresh loop starts with. A pattern
// holds a dynamic, ordered kit — instruments can be added/removed at runtime.

export interface PercussionVoice {
  displayName: string;
  glyph: string;
}

export interface PercussionInstrument {
  id: string;
  displayName: string;
  voices: PercussionVoice[];
  /** When true, each new strike CHOKES the previous one on the same track —
   *  the hand stays on the head (pandeiro), so nothing rings past the next
   *  hit. The audio engine fades the previous voice at the new hit's onset. */
  selfChoke?: boolean;
}

function inst(id: string, displayName: string, voices: [string, string][]): PercussionInstrument {
  return { id, displayName, voices: voices.map(([glyph, vn]) => ({ displayName: vn, glyph })) };
}

// ---- The original four (default kit) — voices match the bundled WAVs. ----
const Surdo = inst("surdo", "Surdo", [["●", "open (ring)"], ["◐", "muted bass"], ["·", "tap"]]);
const Tamborim = inst("tamborim", "Tamborim", [["●", "clack"], ["◐", "muted clack"], ["·", "tap"]]);
const Bongo = inst("bongo", "Bongo", [["▲", "hi"], ["▼", "lo"], ["◇", "rim"], ["✦", "slap"]]);
// Voices 0-2 + 4-7 are Nadav's own recording (tools/build_pandeiro_from_recording.py);
// selfChoke: a real pandeiro hand damps the previous stroke at every new hit.
const Pandeiro: PercussionInstrument = {
  ...inst("pandeiro", "Pandeiro", [
    ["●", "bass (open)"], ["◐", "bass (closed)"], ["✦", "slap"], ["○", "jingle"],
    ["△", "finger (open)"], ["▲", "finger (closed)"],
    ["▽", "heel (open)"], ["▼", "heel (closed)"],
  ]),
  selfChoke: true,
};
const Agogo = inst("agogo", "Agogô", [["▼", "low bell"], ["▲", "high bell"]]);

const DEFAULT_KIT: PercussionInstrument[] = [Surdo, Tamborim, Bongo];

// ---- Brazilian + Latin additions (sourced from the Latin Percussion pack). ----
const ADDITIONS: PercussionInstrument[] = [
  Pandeiro, Agogo,
  inst("cuica", "Cuíca", [["▼", "low"], ["▲", "high"]]),
  inst("caxixi", "Caxixi", [["○", "open"], ["◌", "hand"], ["✺", "fx"]]),
  inst("shaker", "Shaker (Ganzá)", [["○", "shaker 1"], ["◌", "shaker 2"]]),
  inst("guiro", "Guiro (Reco-reco)", [["▶", "down"], ["◀", "up"], ["▬", "long"]]),
  inst("claves", "Claves", [["●", "clave 1"], ["◐", "clave 2"]]),
  inst("cowbell", "Cowbell", [["◉", "cowbell 1"], ["◎", "cowbell 2"]]),
  inst("triangle", "Triangle", [["△", "open"], ["▲", "mute"]]),
  inst("apito", "Apito (whistle)", [["▼", "low"], ["◆", "mid"], ["▲", "high"]]),
  inst("cabasa", "Cabasa", [["·", "short"], ["▬", "long"], ["✺", "fx"]]),
  inst("conga", "Conga", [["●", "open"], ["◐", "mute"], ["✦", "slap"], ["·", "tip"]]),
  inst("quinto", "Quinto", [["●", "open"], ["◐", "mute"], ["✦", "slap"]]),
  inst("tumba", "Tumba", [["●", "open"], ["◐", "mute"], ["✦", "slap"]]),
  inst("timbales", "Timbales", [["▲", "hi"], ["▼", "lo"], ["▬", "cascara"], ["◇", "rim"]]),
  inst("maracas", "Maracas", [["○", "hit"], ["✺", "fx"]]),
  inst("vibraslap", "Vibraslap", [["✹", "hit"], ["✺", "pan"]]),
  inst("castanet", "Castanet", [["·", "single"], ["▬", "roll"]]),
  inst("woodblock", "Wood Block", [["▲", "hi"], ["◆", "mid"], ["▼", "low"]]),
  inst("cymbal", "Cymbal", [["◉", "bell"], ["○", "open"]]),
  inst("gong", "Gong", [["◯", "hit"]]),
];

const ALL: PercussionInstrument[] = [...DEFAULT_KIT, ...ADDITIONS];
const BY_ID = new Map<string, PercussionInstrument>(ALL.map((i) => [i.id, i]));

/** Base catalog id of `id`: duplicated tracks are clones with ids "<base>#<n>"
 *  (e.g. "surdo#2"); samples/synthesis always key off the base id. */
export function basePercussionId(id: string): string {
  const h = id.indexOf("#");
  return h < 0 ? id : id.substring(0, h);
}

/** Catalog of every available instrument plus the default kit. */
export const PercussionCatalog = {
  Surdo, Tamborim, Bongo, Pandeiro, Agogo,
  DEFAULT_KIT,
  ALL,
  byId(id: string): PercussionInstrument | undefined { return BY_ID.get(id); },
  /** Resolve `id` to an instrument: a catalog instrument, or — for a duplicated
   *  track's clone id like "surdo#2" — a copy of its base instrument with the
   *  clone id and a numbered display name ("Surdo 2"). Undefined if unknown. */
  resolve(id: string): PercussionInstrument | undefined {
    const direct = BY_ID.get(id);
    if (direct) return direct;
    const base = BY_ID.get(basePercussionId(id));
    if (!base) return undefined;
    const h = id.indexOf("#");
    const n = h < 0 ? NaN : parseInt(id.substring(h + 1), 10);
    if (Number.isNaN(n)) return undefined;
    return { ...base, id, displayName: `${base.displayName} ${n}` };
  },
};

export function voicesFor(instrument: PercussionInstrument): PercussionVoice[] { return instrument.voices; }
export function voiceCount(instrument: PercussionInstrument): number { return instrument.voices.length; }
export function voiceOf(instrument: PercussionInstrument, index: number): PercussionVoice { return instrument.voices[index]; }

/** Slot count of the default meter (2 bars of 2/4 in sixteenths = 16). */
export const PERCUSSION_SLOTS = 16;

/** Accent flag folded into a cell's raw value: raw = voice + PERCUSSION_ACCENT. */
export const PERCUSSION_ACCENT = 100;

/** Per-slot dynamics folded into a cell's raw value: raw += 1000 × dynLevel.
 *  Level 0 = 100 % (default), 1 = 75 %, 2 = 50 %, 3 = 25 %. Full cell encoding:
 *  raw = voice + 100·accent + 1000·dynLevel. Older app versions reject cells
 *  with a dyn level and skip the whole beat (the forward-compat path). */
export const PERCUSSION_DYN = 1000;
export const PERCUSSION_DYN_FACTORS = [1.0, 0.75, 0.5, 0.25] as const;

/** Allowed beat units (the lower number of the time signature). */
export const BEAT_UNITS = [2, 4, 8] as const;
/** Allowed subdivision values (the "1/N" note each beat is split into). */
export const DIVISIONS = [4, 8, 16, 32] as const;

/**
 * Time grid of a percussion loop: `bars` of `beatsPerBar`/`beatUnit` (the time
 * signature), each beat subdivided into `division`-note slots.
 *
 *   slotsPerBeat = division / beatUnit   (e.g. 1/16 slots in 2/4 -> 16/4 = 4)
 *   slotsPerBar  = beatsPerBar * slotsPerBeat
 *   totalSlots   = bars * slotsPerBar
 *
 * `division` must be an integer multiple of `beatUnit`. Immutable.
 */
export class PercussionMeter {
  constructor(
    readonly bars = 2,
    readonly beatsPerBar = 2,
    readonly beatUnit = 4,
    readonly division = 16,
  ) {}

  get slotsPerBeat(): number { return this.division / this.beatUnit; }
  get slotsPerBar(): number { return this.beatsPerBar * this.slotsPerBeat; }
  get totalSlots(): number { return this.bars * this.slotsPerBar; }

  /** "2 bars · 2/4 · 1/16" style summary for captions. */
  describe(): string {
    return `${this.bars} bar${this.bars === 1 ? "" : "s"} · ${this.beatsPerBar}/${this.beatUnit} · 1/${this.division}`;
  }

  /** True if the fields form a valid meter (mirrors the Kotlin `init` requires). */
  isValid(): boolean {
    return this.bars >= 1 && this.bars <= 8 &&
      this.beatsPerBar >= 1 && this.beatsPerBar <= 12 &&
      (BEAT_UNITS as readonly number[]).includes(this.beatUnit) &&
      (DIVISIONS as readonly number[]).includes(this.division) &&
      this.division % this.beatUnit === 0;
  }

  /** Return a copy with the given fields overridden. */
  copy(fields: Partial<{ bars: number; beatsPerBar: number; beatUnit: number; division: number }>): PercussionMeter {
    return new PercussionMeter(
      fields.bars ?? this.bars,
      fields.beatsPerBar ?? this.beatsPerBar,
      fields.beatUnit ?? this.beatUnit,
      fields.division ?? this.division,
    );
  }

  equals(o: PercussionMeter): boolean {
    return this.bars === o.bars && this.beatsPerBar === o.beatsPerBar &&
      this.beatUnit === o.beatUnit && this.division === o.division;
  }

  /** 2 bars of 2/4 in sixteenths → 16 slots. */
  static readonly DEFAULT = new PercussionMeter();
}

/**
 * Immutable percussion loop grid over an ordered, dynamic kit of `instruments`.
 * `grid` maps an instrument id → its row of cells (null = silent, else 0-based
 * voice index). Every mutation returns a new pattern.
 */
export class PercussionPattern {
  constructor(
    readonly instruments: ReadonlyArray<PercussionInstrument>,
    readonly grid: ReadonlyMap<string, ReadonlyArray<number | null>>,
    readonly meter: PercussionMeter = PercussionMeter.DEFAULT,
    /** Per-TRACK swing (instrument id → 1..100). A track plays with its own
     *  micro-timing clock when the beat's GLOBAL swing is 0 — a nonzero global
     *  swing OVERRIDES all track values. Anchors (each beat and its 2nd 16th)
     *  are swing-invariant, so differently-swung tracks re-align every beat.
     *  Missing id = straight. */
    readonly trackSwing: ReadonlyMap<string, number> = new Map(),
    /** Per-TRACK volume percent (instrument id → 1..100; missing = 100). The
     *  mixer's Overall-volume slider edits this, so track balance travels WITH
     *  the beat (saved/exported/undone). Per-slot dynamics stay reserved for
     *  articulation contrast WITHIN a track. */
    readonly trackVolume: ReadonlyMap<string, number> = new Map(),
  ) {}

  /** One track's swing (0 = straight / no entry). */
  trackSwingOf(id: string): number { return this.trackSwing.get(id) ?? 0; }

  /** Set one track's swing (0 clears the entry — straight). */
  withTrackSwing(id: string, swing: number): PercussionPattern {
    const s = Math.min(Math.max(Math.round(swing), 0), 100);
    const m = new Map(this.trackSwing);
    if (s === 0) m.delete(id); else m.set(id, s);
    return new PercussionPattern(this.instruments, this.grid, this.meter, m, this.trackVolume);
  }

  /** One track's volume percent (100 when unset). */
  trackVolumeOf(id: string): number { return this.trackVolume.get(id) ?? 100; }

  /** Set one track's volume percent (100 clears the entry). */
  withTrackVolume(id: string, percent: number): PercussionPattern {
    const v = Math.min(Math.max(Math.round(percent), 0), 100);
    const m = new Map(this.trackVolume);
    if (v >= 100) m.delete(id); else m.set(id, v);
    return new PercussionPattern(this.instruments, this.grid, this.meter, this.trackSwing, m);
  }

  /** Number of slots in this pattern (= meter.totalSlots). */
  get slots(): number { return this.meter.totalSlots; }

  hasInstrument(instrument: PercussionInstrument): boolean { return this.grid.has(instrument.id); }

  /** Voice index (accent flag stripped), or null when silent. */
  voiceAt(instrument: PercussionInstrument, slot: number): number | null {
    const raw = this.grid.get(instrument.id)![slot];
    return raw === null ? null : raw % PERCUSSION_ACCENT;
  }

  /** Whether the (non-silent) cell is an accented hit. */
  isAccented(instrument: PercussionInstrument, slot: number): boolean {
    return Math.floor(((this.grid.get(instrument.id)![slot] ?? 0) / PERCUSSION_ACCENT)) % 10 === 1;
  }

  /** Toggle the accent on a non-silent cell (no-op on silent cells). */
  accentToggled(instrument: PercussionInstrument, slot: number): PercussionPattern {
    const raw = this.grid.get(instrument.id)![slot];
    if (raw === null) return this;
    return this.withCell(instrument, slot, this.isAccented(instrument, slot) ? raw - PERCUSSION_ACCENT : raw + PERCUSSION_ACCENT);
  }

  /** Per-slot dynamic level (0 = 100 %, 1 = 75 %, 2 = 50 %, 3 = 25 %). */
  dynLevelAt(instrument: PercussionInstrument, slot: number): number {
    return Math.floor((this.grid.get(instrument.id)![slot] ?? 0) / PERCUSSION_DYN);
  }

  /** Cycle a non-silent cell's dynamic level 100 → 75 → 50 → 25 → 100 (Dyn tool). */
  dynCycled(instrument: PercussionInstrument, slot: number): PercussionPattern {
    const raw = this.grid.get(instrument.id)![slot];
    if (raw === null) return this;
    const level = Math.floor(raw / PERCUSSION_DYN);
    return this.withCell(instrument, slot, raw - level * PERCUSSION_DYN + ((level + 1) % 4) * PERCUSSION_DYN);
  }

  /** Cycle the voice `null → 0 → … → last → null`; the accent AND the dynamic
   *  level survive cycling. */
  cycled(instrument: PercussionInstrument, slot: number): PercussionPattern {
    const count = instrument.voices.length;
    const cur = this.voiceAt(instrument, slot);
    const accent = this.isAccented(instrument, slot);
    const dyn = this.dynLevelAt(instrument, slot);
    const next = cur === null ? 0 : cur >= count - 1 ? null : cur + 1;
    return this.withCell(instrument, slot, next === null ? null : next + (accent ? PERCUSSION_ACCENT : 0) + dyn * PERCUSSION_DYN);
  }

  withCell(instrument: PercussionInstrument, slot: number, voice: number | null): PercussionPattern {
    const row = this.grid.get(instrument.id)!.slice();
    row[slot] = voice;
    const g = new Map(this.grid);
    g.set(instrument.id, row);
    return new PercussionPattern(this.instruments, g, this.meter, this.trackSwing, this.trackVolume);
  }

  clearedRow(instrument: PercussionInstrument): PercussionPattern {
    const g = new Map(this.grid);
    g.set(instrument.id, Array<number | null>(this.slots).fill(null));
    return new PercussionPattern(this.instruments, g, this.meter, this.trackSwing, this.trackVolume);
  }

  /** Append `instrument` to the kit with a silent row. No-op if already present. */
  addInstrument(instrument: PercussionInstrument): PercussionPattern {
    if (this.hasInstrument(instrument)) return this;
    const g = new Map(this.grid);
    g.set(instrument.id, Array<number | null>(this.slots).fill(null));
    return new PercussionPattern([...this.instruments, instrument], g, this.meter, this.trackSwing, this.trackVolume);
  }

  /** Remove `instrument` (and its row) from the kit. No-op if absent. */
  removeInstrument(instrument: PercussionInstrument): PercussionPattern {
    if (!this.hasInstrument(instrument)) return this;
    const g = new Map(this.grid);
    g.delete(instrument.id);
    const sw = new Map(this.trackSwing);
    sw.delete(instrument.id);
    const tv = new Map(this.trackVolume);
    tv.delete(instrument.id);
    return new PercussionPattern(this.instruments.filter((i) => i.id !== instrument.id), g, this.meter, sw, tv);
  }

  /** Reorder the kit: move the track at `from` to index `to` (grid unchanged). */
  movedInstrument(from: number, to: number): PercussionPattern {
    if (from < 0 || from >= this.instruments.length || to < 0 || to >= this.instruments.length || from === to) return this;
    const list = this.instruments.slice();
    const [item] = list.splice(from, 1);
    list.splice(to, 0, item);
    return new PercussionPattern(list, this.grid, this.meter, this.trackSwing, this.trackVolume);
  }

  /** Add a preset TRACK in one press: `base`'s row filled by tiling `template`
   *  (defined on the default 16-slot meter) across this pattern's slots. If the
   *  instrument is already in the kit, the preset lands on a fresh clone track
   *  ("Surdo 2") so the existing line is untouched. */
  withPresetTrack(base: PercussionInstrument, template: (number | null)[], swing = 0): PercussionPattern {
    let inst = base;
    if (this.hasInstrument(base)) {
      let n = 2;
      while (this.instruments.some((i) => i.id === `${base.id}#${n}`)) n++;
      inst = { ...base, id: `${base.id}#${n}`, displayName: `${base.displayName} ${n}` };
    }
    const row: (number | null)[] = Array.from({ length: this.meter.totalSlots }, (_, i) => template[i % template.length]);
    const g = new Map(this.grid);
    g.set(inst.id, row);
    return new PercussionPattern([...this.instruments, inst], g, this.meter, this.trackSwing, this.trackVolume)
      .withTrackSwing(inst.id, swing);
  }

  /** Duplicate `instrument`'s track: a CLONE instrument (same voices and sound,
   *  id "<base>#<n>", display name "Surdo 2") is inserted right below it with a
   *  copy of its row. No-op if `instrument` isn't in the kit. */
  duplicatedTrack(instrument: PercussionInstrument): PercussionPattern {
    const idx = this.instruments.findIndex((i) => i.id === instrument.id);
    if (idx < 0) return this;
    const base = basePercussionId(instrument.id);
    let n = 2;
    while (this.instruments.some((i) => i.id === `${base}#${n}`)) n++;
    const baseInst = PercussionCatalog.byId(base) ?? instrument;
    const clone: PercussionInstrument = { ...baseInst, id: `${base}#${n}`, displayName: `${baseInst.displayName} ${n}` };
    const list = this.instruments.slice();
    list.splice(idx + 1, 0, clone);
    const g = new Map(this.grid);
    g.set(clone.id, this.grid.get(instrument.id)!.slice());
    const sw = new Map(this.trackSwing);
    const s = this.trackSwing.get(instrument.id);
    if (s !== undefined) sw.set(clone.id, s);
    const tv = new Map(this.trackVolume);
    const v = this.trackVolume.get(instrument.id);
    if (v !== undefined) tv.set(clone.id, v);
    return new PercussionPattern(list, g, this.meter, sw, tv);
  }

  isEmpty(): boolean {
    for (const row of this.grid.values()) if (row.some((v) => v !== null)) return false;
    return true;
  }

  /**
   * Shift every instrument's row by [n] slots with wrap-around (positive = later
   * in the loop / to the right). [n] is taken modulo [slots], so any integer is valid.
   */
  translated(n: number): PercussionPattern {
    const slots = this.slots;
    if (slots === 0) return this;
    const shift = ((n % slots) + slots) % slots;
    if (shift === 0) return this;
    const g = new Map<string, (number | null)[]>();
    for (const [id, row] of this.grid) {
      const out: (number | null)[] = new Array(slots);
      for (let i = 0; i < slots; i++) out[i] = row[((i - shift) % slots + slots) % slots];
      g.set(id, out);
    }
    return new PercussionPattern(this.instruments, g, this.meter, this.trackSwing, this.trackVolume);
  }

  /**
   * Re-fit this pattern onto [newMeter], copying cells by slot index (cells past
   * the new slot count are dropped; new slots are silent).
   */
  withMeter(newMeter: PercussionMeter): PercussionPattern {
    if (newMeter.equals(this.meter)) return this;
    const n = newMeter.totalSlots;
    const g = new Map<string, (number | null)[]>();
    for (const i of this.instruments) {
      const old = this.grid.get(i.id)!;
      const out: (number | null)[] = new Array(n);
      for (let k = 0; k < n; k++) out[k] = k < old.length ? old[k] : null;
      g.set(i.id, out);
    }
    return new PercussionPattern(this.instruments, g, newMeter, this.trackSwing, this.trackVolume);
  }

  /**
   * Serialize for persistence:
   *   "M:bars,beatsPerBar,beatUnit,division;id=cells|id=cells|…"
   * Each row is "instrumentId=" then its cells comma-separated (silent = "-").
   */
  encode(): string {
    const m = `M:${this.meter.bars},${this.meter.beatsPerBar},${this.meter.beatUnit},${this.meter.division};`;
    const body = this.instruments
      .map((i) => {
        // Per-track swing ("@N") and volume ("%N") ride as id suffixes; old
        // decoders fail to resolve "pandeiro@33%20" and skip the row rather
        // than mis-reading it.
        const sw = this.trackSwing.get(i.id) ?? 0;
        const vol = this.trackVolume.get(i.id) ?? 100;
        const head = i.id + (sw !== 0 ? `@${sw}` : "") + (vol < 100 ? `%${vol}` : "");
        return `${head}=` + this.grid.get(i.id)!.map((v) => (v === null ? "-" : String(v))).join(",");
      })
      .join("|");
    return m + body;
  }

  static empty(kit: ReadonlyArray<PercussionInstrument> = DEFAULT_KIT, meter: PercussionMeter = PercussionMeter.DEFAULT): PercussionPattern {
    const g = new Map<string, (number | null)[]>();
    for (const i of kit) g.set(i.id, Array<number | null>(meter.totalSlots).fill(null));
    return new PercussionPattern([...kit], g, meter);
  }

  /**
   * Parse a string produced by [encode]; null only on structural garbage. Rows
   * whose instrument id isn't in the catalog are skipped (forward/backward
   * compatibility), so a smaller-but-valid kit can result.
   */
  static decode(s: string): PercussionPattern | null {
    if (!s.startsWith("M:")) return null;
    const sep = s.indexOf(";");
    if (sep < 0) return null;
    const parts = s.substring(2, sep).split(",");
    if (parts.length !== 4) return null;
    const ints = parts.map((p) => parseInt(p, 10));
    if (ints.some((n) => Number.isNaN(n))) return null;
    const meter = new PercussionMeter(ints[0], ints[1], ints[2], ints[3]);
    if (!meter.isValid()) return null;

    const rows = s.substring(sep + 1).split("|");
    const instruments: PercussionInstrument[] = [];
    const g = new Map<string, (number | null)[]>();
    const trackSwing = new Map<string, number>();
    const trackVolume = new Map<string, number>();
    for (const rowStr of rows) {
      const eq = rowStr.indexOf("=");
      if (eq < 0) return null;
      let id = rowStr.substring(0, eq);
      // "id[@swing][%vol]" — per-track swing/volume suffixes (see encode).
      const pct = id.lastIndexOf("%");
      const volume = pct > 0 ? parseInt(id.substring(pct + 1), 10) : NaN;
      if (!Number.isNaN(volume)) id = id.substring(0, pct);
      const at = id.lastIndexOf("@");
      const swing = at > 0 ? parseInt(id.substring(at + 1), 10) : NaN;
      if (!Number.isNaN(swing)) id = id.substring(0, at);
      // resolve() also reconstructs duplicated-track clones ("surdo#2"); truly
      // unknown instruments are skipped (forward compatibility).
      const instrument = PercussionCatalog.resolve(id);
      if (!instrument) continue;
      if (g.has(id)) continue;            // ignore duplicate rows
      const cells = rowStr.substring(eq + 1).split(",");
      if (cells.length !== meter.totalSlots) return null;
      const row: (number | null)[] = [];
      for (const c of cells) {
        if (c === "-") { row.push(null); continue; }
        const n = parseInt(c, 10);
        // Raw cell = voice + 100·accent + 1000·dynLevel.
        if (Number.isNaN(n) || n < 0 || Math.floor(n / PERCUSSION_ACCENT) % 10 > 1 ||
            Math.floor(n / PERCUSSION_DYN) > 3 ||
            (n % PERCUSSION_ACCENT) >= instrument.voices.length) return null;
        row.push(n);
      }
      instruments.push(instrument);
      g.set(id, row);
      if (!Number.isNaN(swing)) {
        const s2 = Math.min(Math.max(swing, 0), 100);
        if (s2 !== 0) trackSwing.set(id, s2);
      }
      if (!Number.isNaN(volume)) {
        const v2 = Math.min(Math.max(volume, 0), 100);
        if (v2 < 100) trackVolume.set(id, v2);
      }
    }
    return new PercussionPattern(instruments, g, meter, trackSwing, trackVolume);
  }
}

// Built-in loadable grooves, transcribed from the app's own step grid. Defined via the
// encode() string form so they're compact and self-validating through decode().
// (The "stock samba" auto-load preset was removed — the looper now starts empty.)
function builtin(encoded: string): PercussionPattern {
  const p = PercussionPattern.decode(encoded);
  if (!p) throw new Error(`invalid built-in pattern: ${encoded}`);
  return p;
}

// Teleco-teco — the two classic phrasings. Surdo + pandeiro are shared; the tamborim
// and agogô are phase-shifted between the two. (Rows: surdo|tamborim|pandeiro|agogo.)
export const TELECOTECO_1 = builtin(
  "M:2,2,4,16;" +
  "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
  "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
  "pandeiro=0,3,2,0,0,3,2,0,0,3,2,0,0,3,2,0" + "|" +
  // Agogô (#12): low bell ▼ (voice 0) on steps 1,7,9,16; high bell ▲ (voice 1)
  // on steps 4,5,11,13,14 — 0-indexed slots below.
  "agogo=0,-,-,1,1,-,0,-,0,-,1,-,1,1,-,0",
);

export const TELECOTECO_2 = builtin(
  "M:2,2,4,16;" +
  "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
  "tamborim=0,1,0,1,0,1,2,0,1,0,1,0,1,2,0,1" + "|" +
  "pandeiro=0,3,2,0,0,3,2,0,0,3,2,0,0,3,2,0" + "|" +
  "agogo=0,-,0,-,1,-,-,0,-,0,-,1,-,-,0,-",
);

// Batida do cavaco 1 — default groove for the new kit (surdo + tamborim + bongo).
export const BATIDA_CAVACO_1 = builtin(
  "M:2,2,4,16;" +
  "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
  "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
  "bongo=-,0,-,1,-,0,-,1,-,0,-,1,-,0,-,1",
);

// ---- Northeastern-Brazilian grooves (xote / baião / forró / xaxado / arrasta-pé). ----
// Each uses the shared teleco-teco surdo (muted-bass ◐ + tap · pulse) and a tamborim
// tresillo (3+3+2) under a bongo comp transcribed from the user's saved beats.
const SURDO_TELECO = "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2";
const TAMB_TRESILLO = "tamborim=0,-,-,0,-,-,0,-,0,-,-,0,-,-,0,-";

export const XOTE = builtin(
  "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
  "bongo=0,-,2,1,0,-,0,-,0,-,2,1,0,-,0,-",
);
export const BAIAO = builtin(
  "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
  "bongo=0,-,2,1,-,-,2,1,0,-,2,1,-,-,2,1",
);
export const FORRO = builtin(
  "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
  "bongo=0,-,3,1,2,-,3,1,0,0,-,0,2,-,3,1",
);
export const XAXADO = builtin(
  "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
  "bongo=0,2,3,0,-,-,1,-,0,2,3,0,-,2,1,-",
);
export const ARRASTA_PE = builtin(
  "M:2,2,4,16;" + SURDO_TELECO + "|" + TAMB_TRESILLO + "|" +
  "bongo=0,2,3,0,1,-,1,-,0,2,3,0,1,-,1,-",
);

/** A loadable groove for the Load… menu; `bpm` (when set) is applied on load;
 *  `opening` (when set) is a one-shot entrada played once before the loop. */
export interface BuiltinPattern { name: string; pattern: PercussionPattern; bpm?: number; opening?: PercussionPattern; }

// Partido-alto grooves (from Nadav's exported beats): the teleco-teco
// surdo/tamborim under three bongo comps.
export const PARTIDO_ALTO_OFFICIAL = builtin(
  "M:2,2,4,16;" + SURDO_TELECO + "|" +
  "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
  "pandeiro=-,2,-,-,0,-,0,-,0,-,2,-,-,0,-,0",   // "Pandeiro — Partido Alto" phrase
);
export const PARTIDO_ALTO_DEC = builtin(
  "M:2,2,4,16;" + SURDO_TELECO + "|" +
  "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
  "bongo=-,0,-,0,1,-,1,-,1,-,0,-,3,1,-,1",
);
export const PARTIDO_ALTO_PLATINELAS = builtin(
  "M:2,2,4,16;" + SURDO_TELECO + "|" +
  "tamborim=1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
  "bongo=0,0,-,0,1,-,0,-,1,-,0,-,3,1,2,0",
);

// Nadav's samba groove (from his exported beat): reta pandeiro (track swing 33)
// + marcação surdo + three tamborims — teleco-teco, levada reta (track swing 10)
// and palmas. (Samba 1's agogô line lives in the phrase library as
// "Agogô — Samba" — add it on demand; replaced the plain "Samba 1" groove.)
export const TRES_TAMBORINS = builtin(
  "M:2,2,4,16;" +
  "pandeiro@50=101,2005,2007,2,0,2004,2006,4,101,2005,2007,2,0,4,6,4" + "|" +
  "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
  "tamborim=3002,0,3002,0,2001,3002,0,3002,0,3002,0,3002,0,2001,3002,0" + "|" +
  "tamborim#2@50=100,1001,2,0,100,1001,2,0,100,1001,2,0,100,1001,2,0",
);

/** Grooves offered in the Drum-machine Load… menu (before the user's saved beats). */
export const BUILTIN_PATTERNS: BuiltinPattern[] = [
  { name: "2 Tamborims, Pandeiro & Surdo", pattern: TRES_TAMBORINS, bpm: 80 },
  { name: "Partido Alto Groove", pattern: PARTIDO_ALTO_OFFICIAL, bpm: 70 },
  { name: "Platinelas Pandeiro — Partido Alto Groove", pattern: PARTIDO_ALTO_PLATINELAS, bpm: 70 },
  { name: "Xote (Bongo)", pattern: XOTE, bpm: 90 },
  { name: "Baião (Bongo)", pattern: BAIAO, bpm: 90 },
  { name: "Forró (Bongo)", pattern: FORRO, bpm: 95 },
  { name: "Xaxado (Bongo)", pattern: XAXADO, bpm: 100 },
  { name: "Arrasta-pé (Bongo)", pattern: ARRASTA_PE, bpm: 100 },
];

/** A one-press preset TRACK ("+ Add ▾" → presets, also the phrase "chunks" the
 *  Blocks feature sequences): a 16-slot row template tiled across the current
 *  loop on `instrument` (cloned if already present). `swing` is the chunk's own
 *  feel (0 = straight; used by Blocks playback); `note` is a playing rule/tip. */
export interface PresetTrack {
  label: string;
  instrument: PercussionInstrument;
  template: (number | null)[];
  swing?: number;
  note?: string;
  /** When true, whatever phrase FOLLOWS this one in a block gains a strong beat
   *  on 1 (its measure-2 downbeat stroke) — the partido-alto return rule. */
  addsReturnDownbeat?: boolean;
}

/** Find a preset track by its label (block cells reference phrases by label). */
export function presetByLabel(label: string): PresetTrack | undefined {
  return PRESET_TRACKS.find((p) => p.label === label);
}

/** Track presets — the single-instrument "chunks": added to the CURRENT beat in
 *  one press from "+ Add ▾", loadable as an opening entrada, and the phrases the
 *  Blocks feature sequences. Sources: the teleco-teco built-ins, Oded's
 *  entradas, and Nadav's variation exports. */
export const PRESET_TRACKS: PresetTrack[] = [
  { label: "Surdo — Marcação", instrument: Surdo,
    template: [1, null, null, 2, 0, null, null, 2, 1, null, null, 2, 0, null, null, 2] },
  { label: "Tamborim — Teleco-teco", instrument: Tamborim,
    // tap (3002) then muted clack (2001) at each syncopation — the clack/tap were
    // switched at the "2"/"e" 16ths; corrected to match the Var phrases.
    template: [3002, 0, 3002, 0, 3002, 2001, 0, 3002, 0, 3002, 0, 3002, 0, 3002, 2001, 0] },
  { label: "Tamborim — Telecoteco Var 1", instrument: Tamborim,
    template: [3002, 0, 3002, 0, 3002, 2001, 0, 3002, 0, 0, 0, 0, 0, 3002, 2001, 0] },
  { label: "Tamborim — Telecoteco Var 2", instrument: Tamborim,
    template: [3002, 0, 3002, 0, 3002, 2001, 0, 3002, 2001, 0, 0, 0, 0, 3002, 2001, 0] },
  { label: "Tamborim — Telecoteco Var 3", instrument: Tamborim,
    template: [3002, 0, 3002, 0, 3002, 2001, 0, 3002, 2001, 0, 3002, 2001, 0, 3002, 2001, 0] },
  // Corrected per Nadav's export: each beat = ACCENTED clack, muted clack at
  // 75 % (dyn level 1), tap, clack — with a light 10 % swing.
  { label: "Tamborim — Levada Reta", instrument: Tamborim,
    template: [100, 3002, 2001, 0, 100, 3002, 2001, 0, 100, 3002, 2001, 0, 100, 3002, 2001, 0],
    swing: 50 },
  { label: "Tamborim — Chamada", instrument: Tamborim,
    template: [3002, 0, 3002, 0, 0, 0, 3002, 0, 0, 0, 3002, 0, 0, 0, 3002, 0],
    swing: 50, note: "Played with 50% swing." },
  // From Nadav's export: accented clacks, taps, and 50 %-dyn clacks (2000s).
  { label: "Tamborim — Palmas", instrument: Tamborim,
    template: [100, 2, 2000, 100, 2, 2000, 100, 2, 100, 2, 2000, 100, 2, 2000, 100, 2] },
  { label: "Tamborim — Entrada 1", instrument: Tamborim,
    template: [0, 3002, 2001, 0, 3002, 2001, 0, 3002, 0, 3002, 0, 3002, 0, 3002, 2001, 0] },
  { label: "Tamborim — Entrada 2", instrument: Tamborim,
    template: [0, 3002, 0, 3002, 0, 3002, 0, 3002, 0, 3002, 0, 3002, 0, 3002, 2001, 0] },
  // From Nadav's own recording + his authored dynamics (pandeiro.chorect.json):
  // bass closed (accented), finger closed @50%, heel closed @50%, slap |
  // bass open, finger open, heel open, finger open — bar 2's taps at full.
  { label: "Pandeiro — Reta", instrument: Pandeiro,
    template: [101, 2005, 2007, 2, 0, 2004, 2006, 4, 101, 2005, 2007, 2, 0, 4, 6, 4],
    swing: 50,
    note: "Nadav's reta — bass accented, closed taps at 50%." },
  // Pandeiro partido-alto phrases from Nadav's exports.
  { label: "Pandeiro — Partido Alto", instrument: Pandeiro,
    template: [null, 2, null, null, 0, null, 0, null, 0, null, 2, null, null, 0, null, 0] },
  { label: "Pandeiro — Partido Alto Dec 1", instrument: Pandeiro,
    template: [null, 102, null, 2, 0, null, 0, null, 0, null, 102, null, 2004, 0, null, 0] },
  { label: "Pandeiro — Partido Alto Dec 2", instrument: Pandeiro,
    template: [null, 102, null, 2, 0, 2004, 0, 2004, 1, null, 102, null, 2002, 0, 2004, 1] },
  // From Nadav's export pandeiro_partido_alto_2_4.
  { label: "Pandeiro — Partido Alto Dec 3", instrument: Pandeiro,
    template: [null, 102, 7, 2, 0, null, 0, null, 1, null, 102, 7, 2004, 0, null, 1] },
  // From Nadav's export pandeiro_partido_alto_2_5.
  { label: "Pandeiro — Partido Alto Dec 4", instrument: Pandeiro,
    template: [null, 102, 2007, 2, 0, 2004, 0, 2004, 0, null, 102, 7, 1002, 0, 2004, 0] },
  // Was "Dec 3" — the fully-decorated variant with opening bass strokes (renumbered).
  { label: "Pandeiro — Partido Alto Dec 5", instrument: Pandeiro,
    template: [2005, 102, 2007, 2, 0, 2004, 0, 2004, 0, 2005, 102, 7, 1002, 0, 2004, 0] },
  // From Nadav's export pandeiro_partido_alto_var_1.
  { label: "Pandeiro — Partido Alto Var 1", instrument: Pandeiro,
    template: [null, 102, null, 2, 0, 0, null, 0, 0, 0, null, 0, 0, 0, null, 0],
    swing: 50,
    note: "RULE: whatever phrase FOLLOWS this one in a block gets an open bass note on " +
      "beat 1 — the return to the regular partido alto. Applies to that instance only; " +
      "the following phrase is otherwise unchanged.",
    addsReturnDownbeat: true },
  // From Nadav's export pandeiro_partido_alto_var_2.
  { label: "Pandeiro — Partido Alto Var 2", instrument: Pandeiro,
    template: [null, 102, null, 2, 0, null, 2, null, 4, 0, null, 0, null, 0, null, 0] },
  // A copy of the regular "Pandeiro — Partido Alto" with the first slap moved one
  // 16th right — from the "e of 1" (slot 1) to the "& of 1" (slot 2) — an entrada.
  { label: "Pandeiro — Partido Alto Entrada", instrument: Pandeiro,
    template: [null, null, 2, null, 0, null, 0, null, 0, null, 2, null, null, 0, null, 0],
    note: "Opening figure — the regular partido alto with the first slap on the & of 1." },
  // Samba 1's agogô bells (low ▼ / high ▲) — usually mixed quiet (track volume).
  { label: "Agogô — Samba", instrument: Agogo,
    template: [0, null, null, 1, 1, null, 0, null, 0, null, 1, null, 1, 1, null, 0],
    note: "The classic samba bell line — usually mixed quiet (lower the track volume)." },
];

/** A single-line tamborim rhythm from onset slots (`accented` slots get the
 *  accent flag). Tamborim articulation: an onset directly followed by another
 *  onset is played as a MUTED clack (voice 1) leading into the open clack
 *  (voice 0) — i.e. the first stroke of every consecutive-16ths pair is muted.
 *  Used by the study patterns, transcribed from notation sheets. */
function tamborimLine(onsets: number[], accented: number[] = [], bars = 2): PercussionPattern {
  const on = new Set(onsets), acc = new Set(accented);
  const cells = Array.from({ length: bars * 8 }, (_, i) =>
    !on.has(i) ? "-"
    : acc.has(i) ? String(PERCUSSION_ACCENT)
    : on.has(i + 1) ? "1"    // muted pickup into the next stroke
    : "0").join(",");
  return builtin(`M:${bars},2,4,16;tamborim=${cells}`);
}

// ---- Study rhythms. 2 bars of 2/4 on a 16th grid unless noted; single
// tamborim line. An entrada is an OPENING played once before its loop.
const BOSSA_UP = tamborimLine([0, 3, 6, 10, 13]);
const SAMBA_CLAP = tamborimLine([0, 3, 6], [], 1);

/** Study grooves (the "Study" section). (The entrada → teleco-teco combos were
 *  removed: build them by adding an entrada preset as the opening.) */
export const STUDY_PATTERNS: BuiltinPattern[] = [
  { name: "Bossa Nova Clave", pattern: BOSSA_UP, bpm: 70 },
  { name: "Samba Clap (Palma)", pattern: SAMBA_CLAP, bpm: 70 },
];

// ---- Beat file (export / import) ----
// A self-describing JSON envelope around a pattern plus its name/tempo, so a beat
// can be saved to disk and loaded back (here or on Android — same shape).

export interface BeatFile {
  format: "chorect-beat";
  version: 1;
  name: string;
  bpm: number;
  swing: number;
  pattern: string;    // PercussionPattern.encode()
  opening?: string;   // optional one-shot entrada, PercussionPattern.encode()
  notes?: string;     // optional free-text notes attached to the beat
}

/** Serialize a beat to the pretty-printed JSON envelope written to disk. */
export function encodeBeatFile(name: string, bpm: number, swing: number, pattern: PercussionPattern, opening: PercussionPattern | null = null, notes = ""): string {
  const obj: BeatFile = { format: "chorect-beat", version: 1, name, bpm: Math.round(bpm), swing: Math.round(swing), pattern: pattern.encode() };
  if (opening) obj.opening = opening.encode();
  if (notes) obj.notes = notes;
  return JSON.stringify(obj, null, 2);
}

/** Parse a beat file produced by [encodeBeatFile]; null on anything unrecognizable. */
export function decodeBeatFile(text: string): { name: string; bpm: number; swing: number; pattern: PercussionPattern; opening: PercussionPattern | null; notes: string } | null {
  let obj: unknown;
  try { obj = JSON.parse(text); } catch { return null; }
  if (!obj || typeof obj !== "object") return null;
  const o = obj as Record<string, unknown>;
  if (o.format !== "chorect-beat") return null;
  const pattern = PercussionPattern.decode(String(o.pattern ?? ""));
  if (!pattern) return null;
  const opening = typeof o.opening === "string" ? PercussionPattern.decode(o.opening) : null;
  const notes = typeof o.notes === "string" ? o.notes : "";
  const name = typeof o.name === "string" && o.name.trim() ? o.name : "beat";
  const bpm = typeof o.bpm === "number" && Number.isFinite(o.bpm) ? Math.round(o.bpm) : 90;
  const swing = typeof o.swing === "number" && Number.isFinite(o.swing) ? Math.round(o.swing) : 0;
  return { name, bpm: Math.min(Math.max(bpm, 10), 300), swing: Math.min(Math.max(swing, 0), 100), pattern, opening, notes };
}

// ---- Timing ----

/** Exact milliseconds of one [division]-note slot at [bpm] (a quarter-note = 4
 *  sixteenths, so a 1/[division] note = quarter × 4 / division). This is the one true
 *  value; [slotMs] is its rounded form. Mirror of Kotlin PercussionTiming.slotMsExact. */
export function slotMsExact(bpm: number, division = 16): number {
  return (60000 / Math.max(bpm, 10)) * 4 / division;
}

/**
 * [slotMsExact] rounded to whole milliseconds, for callers that need an integer.
 *
 * This used to FLOOR — mirroring Kotlin's old integer arithmetic — which made the app
 * play measurably fast: at 90 bpm a 16th came out 166 ms instead of 166.667, so a cycle
 * ran 2656 ms instead of 2666.667 (0.4 % sharp). Anything driving playback should
 * accumulate [swungSlotMsExact] rather than whole milliseconds at all.
 */
export function slotMs(bpm: number, division = 16): number {
  return Math.round(slotMsExact(bpm, division));
}
export function loopMs(bpm: number): number {
  return slotMs(bpm) * PERCUSSION_SLOTS;
}

/**
 * Exact onset of [slot], in ms from the loop start, with the same swing feel
 * [swungSlotMs] applies but no per-onset rounding. Offline renders use this so one
 * cycle comes out exactly `totalSlots × slotMsExact` long; see [slotMsExact].
 * Mirror of Kotlin PercussionTiming.swungOnsetMsExact.
 */
export function swungOnsetMsExact(
  slot: number, bpm: number, swingPercent: number, meter: PercussionMeter,
  model: SwingModel = SwingModel.Default,
): number {
  const base = slotMsExact(bpm, meter.division);
  // Swing is defined only for a quarter-note beat divided into four 16ths.
  if (meter.beatUnit !== 4 || meter.division !== 16) return slot * base;
  const sw = Math.min(Math.max(swingPercent, 0), 100) / 100;
  const pos = ((slot % 4) + 4) % 4;
  return (Math.floor(slot / 4) * 4 + pos + swingOffset(pos, sw, model)) * base;
}

/**
 * Wait (ms) after [slot] before the next slot, applying a Brazilian 16th-note swing.
 *
 * Swing only operates when a quarter-note beat is split into exactly four 16th notes
 * (beatUnit === 4 and division === 16); any other meter plays straight. Within each
 * beat the four 16ths sit at 0, ¼, ½, ¾ of the beat. Samba microtiming studies
 * (Gerischer; Naveda/Gouyon) show the played feel keeps the 1st AND 2nd 16ths on the
 * grid and ANTICIPATES the 3rd and (more so) the 4th — the propulsive samba lilt.
 * As `swingPercent` rises 0→100 the 3rd 16th moves up to −0.25 slot early and the 4th
 * up to −0.4 slot early. Onsets are rounded independently so the anchors stay on-grid
 * and the loop length is preserved. (Replaces the earlier delayed-2nd/advanced-4th
 * model, whose bunched mid-beat notes sounded lopsided at high percentages.)
 */
/**
 * 16th-note swing feel — HEMIOLA-BASED. Mirror of Kotlin SwingModel /
 * PercussionTiming.swingOffset. Spec: docs/superpowers/specs/2026-07-29-swing-models.md.
 * Quarter-note beat = unit length; straight 16ths at [0,1/4,1/2,3/4]; full hemiola
 * (p=100 %) at [0,1/3,1/2,2/3]; q = swingPercent/100 interpolates linearly. Positions:
 *  • Default: [q·(1/3−1/4)/2, 1/4+q·(1/3−1/4), 1/2, 3/4−q·(3/4−2/3)/2]
 *  • Hemiola: [0, 1/4+q·(1/3−1/4), 1/2, 3/4−q·(3/4−2/3)]
 * swingOffset returns SLOT-unit offsets; the 2nd-16th shift d = q/3 slot (1/12 beat).
 * Default = Default.
 */
export enum SwingModel { Default = "default", Hemiola = "hemiola" }

/** Per-16th onset offset (slot units) added to nominal position [pos] (0..3), given s∈[0,1]. */
export function swingOffset(pos: number, s: number, model: SwingModel): number {
  const d = s / 3;
  switch (model) {
    case SwingModel.Hemiola: return pos === 1 ? d : pos === 3 ? -d : 0;
    default:                 return pos === 0 ? d / 2 : pos === 1 ? d : pos === 3 ? -d / 2 : 0;   // Default
  }
}

/** Human-readable per-16th position formulas (p = swing%/100). The one place to
 *  read "what are the swing models" without inferring from code; kept in lock-step
 *  with [swingOffset] and docs/superpowers/specs/2026-07-29-swing-models.md. */
export const SWING_MODEL_FORMULA: Record<SwingModel, string> = {
  [SwingModel.Default]: "[p(⅓−¼)/2,  ¼+p(⅓−¼),  ½,  ¾−p(¾−⅔)/2]",
  [SwingModel.Hemiola]: "[0,  ¼+p(⅓−¼),  ½,  ¾−p(¾−⅔)]",
};

/** Exact duration of [slot] in ms — [swungSlotMs] with no rounding at all. Playback
 *  schedulers accumulate THIS so the loop runs at the true tempo; they round only once,
 *  when handing a time to the audio clock. Mirror of Kotlin swungSlotMsExact. */
export function swungSlotMsExact(
  slot: number, bpm: number, swingPercent: number, meter: PercussionMeter,
  model: SwingModel = SwingModel.Default,
): number {
  return swungOnsetMsExact(slot + 1, bpm, swingPercent, meter, model) -
    swungOnsetMsExact(slot, bpm, swingPercent, meter, model);
}

export function swungSlotMs(
  slot: number, bpm: number, swingPercent: number, meter: PercussionMeter,
  model: SwingModel = SwingModel.Default,
): number {
  // The EXACT slot length, so the rounding below happens once on absolute onsets
  // rather than compounding on top of an already-floored base.
  const base = slotMsExact(bpm, meter.division);
  if (meter.beatUnit !== 4 || meter.division !== 16) return Math.max(Math.round(base), 1);
  const sw = Math.min(Math.max(swingPercent, 0), 100) / 100;
  const onsetMs = (k: number): number => {
    const pos = ((k % 4) + 4) % 4;
    const offsetSlots = pos + swingOffset(pos, sw, model);
    return Math.round((Math.floor(k / 4) * 4 + offsetSlots) * base);
  };
  return Math.max(onsetMs(slot + 1) - onsetMs(slot), 1);
}

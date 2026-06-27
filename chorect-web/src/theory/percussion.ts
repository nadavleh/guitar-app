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
}

function inst(id: string, displayName: string, voices: [string, string][]): PercussionInstrument {
  return { id, displayName, voices: voices.map(([glyph, vn]) => ({ displayName: vn, glyph })) };
}

// ---- The original four (default kit) — voices match the bundled WAVs. ----
const Surdo = inst("surdo", "Surdo", [["●", "open (ring)"], ["◐", "muted bass"], ["·", "tap"]]);
const Tamborim = inst("tamborim", "Tamborim", [["●", "clack"], ["◐", "muted clack"], ["·", "tap"]]);
const Pandeiro = inst("pandeiro", "Pandeiro", [["●", "bass (open)"], ["◐", "bass (muted)"], ["✦", "slap"], ["○", "jingle"]]);
const Agogo = inst("agogo", "Agogô", [["▼", "low bell"], ["▲", "high bell"]]);

const DEFAULT_KIT: PercussionInstrument[] = [Surdo, Tamborim, Pandeiro, Agogo];

// ---- Brazilian + Latin additions (sourced from the Latin Percussion pack). ----
const ADDITIONS: PercussionInstrument[] = [
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
  inst("bongo", "Bongo", [["▲", "hi"], ["▼", "lo"], ["◇", "rim"], ["✦", "slap"]]),
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

/** Catalog of every available instrument plus the default kit. */
export const PercussionCatalog = {
  Surdo, Tamborim, Pandeiro, Agogo,
  DEFAULT_KIT,
  ALL,
  byId(id: string): PercussionInstrument | undefined { return BY_ID.get(id); },
};

export function voicesFor(instrument: PercussionInstrument): PercussionVoice[] { return instrument.voices; }
export function voiceCount(instrument: PercussionInstrument): number { return instrument.voices.length; }
export function voiceOf(instrument: PercussionInstrument, index: number): PercussionVoice { return instrument.voices[index]; }

/** Slot count of the default meter (2 bars of 2/4 in sixteenths = 16). */
export const PERCUSSION_SLOTS = 16;

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
  ) {}

  /** Number of slots in this pattern (= meter.totalSlots). */
  get slots(): number { return this.meter.totalSlots; }

  hasInstrument(instrument: PercussionInstrument): boolean { return this.grid.has(instrument.id); }

  voiceAt(instrument: PercussionInstrument, slot: number): number | null {
    return this.grid.get(instrument.id)![slot];
  }

  cycled(instrument: PercussionInstrument, slot: number): PercussionPattern {
    const count = instrument.voices.length;
    const cur = this.grid.get(instrument.id)![slot];
    const next = cur === null ? 0 : cur >= count - 1 ? null : cur + 1;
    return this.withCell(instrument, slot, next);
  }

  withCell(instrument: PercussionInstrument, slot: number, voice: number | null): PercussionPattern {
    const row = this.grid.get(instrument.id)!.slice();
    row[slot] = voice;
    const g = new Map(this.grid);
    g.set(instrument.id, row);
    return new PercussionPattern(this.instruments, g, this.meter);
  }

  clearedRow(instrument: PercussionInstrument): PercussionPattern {
    const g = new Map(this.grid);
    g.set(instrument.id, Array<number | null>(this.slots).fill(null));
    return new PercussionPattern(this.instruments, g, this.meter);
  }

  /** Append `instrument` to the kit with a silent row. No-op if already present. */
  addInstrument(instrument: PercussionInstrument): PercussionPattern {
    if (this.hasInstrument(instrument)) return this;
    const g = new Map(this.grid);
    g.set(instrument.id, Array<number | null>(this.slots).fill(null));
    return new PercussionPattern([...this.instruments, instrument], g, this.meter);
  }

  /** Remove `instrument` (and its row) from the kit. No-op if absent. */
  removeInstrument(instrument: PercussionInstrument): PercussionPattern {
    if (!this.hasInstrument(instrument)) return this;
    const g = new Map(this.grid);
    g.delete(instrument.id);
    return new PercussionPattern(this.instruments.filter((i) => i.id !== instrument.id), g, this.meter);
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
    return new PercussionPattern(this.instruments, g, this.meter);
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
    return new PercussionPattern(this.instruments, g, newMeter);
  }

  /**
   * Serialize for persistence:
   *   "M:bars,beatsPerBar,beatUnit,division;id=cells|id=cells|…"
   * Each row is "instrumentId=" then its cells comma-separated (silent = "-").
   */
  encode(): string {
    const m = `M:${this.meter.bars},${this.meter.beatsPerBar},${this.meter.beatUnit},${this.meter.division};`;
    const body = this.instruments
      .map((i) => `${i.id}=` + this.grid.get(i.id)!.map((v) => (v === null ? "-" : String(v))).join(","))
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
    for (const rowStr of rows) {
      const eq = rowStr.indexOf("=");
      if (eq < 0) return null;
      const id = rowStr.substring(0, eq);
      const instrument = PercussionCatalog.byId(id);
      if (!instrument) continue;          // skip unknown instruments
      if (g.has(id)) continue;            // ignore duplicate rows
      const cells = rowStr.substring(eq + 1).split(",");
      if (cells.length !== meter.totalSlots) return null;
      const row: (number | null)[] = [];
      for (const c of cells) {
        if (c === "-") { row.push(null); continue; }
        const n = parseInt(c, 10);
        if (Number.isNaN(n) || n < 0 || n >= instrument.voices.length) return null;
        row.push(n);
      }
      instruments.push(instrument);
      g.set(id, row);
    }
    return new PercussionPattern(instruments, g, meter);
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
  "agogo=-,0,-,1,-,-,0,-,0,-,0,-,1,-,-,0",
);

export const TELECOTECO_2 = builtin(
  "M:2,2,4,16;" +
  "surdo=1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
  "tamborim=0,1,0,1,0,1,2,0,1,0,1,0,1,2,0,1" + "|" +
  "pandeiro=0,3,2,0,0,3,2,0,0,3,2,0,0,3,2,0" + "|" +
  "agogo=0,-,0,-,1,-,-,0,-,0,-,1,-,-,0,-",
);

/** Grooves offered in the Drum-machine Load… menu (before the user's saved beats). */
export const BUILTIN_PATTERNS: { name: string; pattern: PercussionPattern }[] = [
  { name: "teleco-teco 1", pattern: TELECOTECO_1 },
  { name: "teleco-teco 2", pattern: TELECOTECO_2 },
];

// ---- Timing ----

/** Milliseconds of one [division]-note slot at [bpm] (a quarter-note = 4 sixteenths,
 *  so a 1/[division] note = quarter × 4 / division). Floored to mirror Kotlin's
 *  integer arithmetic. */
export function slotMs(bpm: number, division = 16): number {
  return Math.floor((60000 / Math.max(bpm, 20)) * 4 / division);
}
export function loopMs(bpm: number): number {
  return slotMs(bpm) * PERCUSSION_SLOTS;
}

/**
 * Wait (ms) after [slot] before the next slot, applying a Brazilian 16th-note swing.
 *
 * Swing only operates when a quarter-note beat is split into exactly four 16th notes
 * (beatUnit === 4 and division === 16); any other meter plays straight. Within each
 * beat the four 16ths sit at 0, ¼, ½, ¾ of the beat. As `swingPercent` rises 0→100
 * the 1st and 3rd stay anchored, the 2nd is delayed toward ⅓ of the beat (+1/12 at
 * 100 %), and the 4th is advanced (made early) toward ⅔ (−1/12 at 100 %). Onsets are
 * rounded independently so the anchors stay on-grid and the loop length is preserved.
 */
export function swungSlotMs(slot: number, bpm: number, swingPercent: number, meter: PercussionMeter): number {
  const base = slotMs(bpm, meter.division);
  if (meter.beatUnit !== 4 || meter.division !== 16) return Math.max(base, 1);
  const sw = Math.min(Math.max(swingPercent, 0), 100) / 100;
  const onsetMs = (k: number): number => {
    const pos = k % 4;
    const offsetSlots = pos === 0 ? 0 : pos === 1 ? 1 + sw / 3 : pos === 2 ? 2 : 3 - sw / 3;
    return Math.round((Math.floor(k / 4) * 4 + offsetSlots) * base);
  };
  return Math.max(onsetMs(slot + 1) - onsetMs(slot), 1);
}

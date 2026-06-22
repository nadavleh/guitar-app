// Samba percussion model, ported from theory/.../{Percussion,PercussionPattern}.kt.

export enum PercussionInstrument { Surdo = "Surdo", Tamborim = "Tamborim", Pandeiro = "Pandeiro", Agogo = "Agogo" }

/** Enum order (matches Kotlin entries order) — used for grid rows + serialization. */
export const PERCUSSION_INSTRUMENTS: PercussionInstrument[] = [
  PercussionInstrument.Surdo, PercussionInstrument.Tamborim, PercussionInstrument.Pandeiro, PercussionInstrument.Agogo,
];

export const PercussionInstrumentName: Record<PercussionInstrument, string> = {
  [PercussionInstrument.Surdo]: "Surdo",
  [PercussionInstrument.Tamborim]: "Tamborim",
  [PercussionInstrument.Pandeiro]: "Pandeiro",
  [PercussionInstrument.Agogo]: "Agogô",
};

export interface PercussionVoice {
  instrument: PercussionInstrument;
  index: number;
  displayName: string;
  glyph: string;
}

const VOICE_TABLE: Record<PercussionInstrument, PercussionVoice[]> = {
  [PercussionInstrument.Surdo]: [
    { instrument: PercussionInstrument.Surdo, index: 0, displayName: "open (ring)", glyph: "●" },
    { instrument: PercussionInstrument.Surdo, index: 1, displayName: "muted bass", glyph: "◐" },
    { instrument: PercussionInstrument.Surdo, index: 2, displayName: "tap", glyph: "·" },
  ],
  [PercussionInstrument.Tamborim]: [
    { instrument: PercussionInstrument.Tamborim, index: 0, displayName: "clack", glyph: "●" },
    { instrument: PercussionInstrument.Tamborim, index: 1, displayName: "muted clack", glyph: "◐" },
    { instrument: PercussionInstrument.Tamborim, index: 2, displayName: "tap", glyph: "·" },
  ],
  [PercussionInstrument.Pandeiro]: [
    { instrument: PercussionInstrument.Pandeiro, index: 0, displayName: "bass (open)", glyph: "●" },
    { instrument: PercussionInstrument.Pandeiro, index: 1, displayName: "bass (muted)", glyph: "◐" },
    { instrument: PercussionInstrument.Pandeiro, index: 2, displayName: "slap", glyph: "✦" },
    { instrument: PercussionInstrument.Pandeiro, index: 3, displayName: "jingle", glyph: "○" },
    { instrument: PercussionInstrument.Pandeiro, index: 4, displayName: "jingle hi", glyph: "◌" },
  ],
  [PercussionInstrument.Agogo]: [
    { instrument: PercussionInstrument.Agogo, index: 0, displayName: "low bell", glyph: "▼" },
    { instrument: PercussionInstrument.Agogo, index: 1, displayName: "high bell", glyph: "▲" },
  ],
};

export function voicesFor(instrument: PercussionInstrument): PercussionVoice[] { return VOICE_TABLE[instrument]; }
export function voiceCount(instrument: PercussionInstrument): number { return VOICE_TABLE[instrument].length; }
export function voiceOf(instrument: PercussionInstrument, index: number): PercussionVoice { return VOICE_TABLE[instrument][index]; }

/** Slot count of the default meter (2 bars of 2/4 in sixteenths = 16). Kept as a
 *  named constant for the stock samba / empty patterns. */
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

/** Immutable percussion loop grid. Every mutation returns a new pattern. */
export class PercussionPattern {
  constructor(
    readonly grid: ReadonlyMap<PercussionInstrument, ReadonlyArray<number | null>>,
    readonly meter: PercussionMeter = PercussionMeter.DEFAULT,
  ) {}

  /** Number of slots in this pattern (= meter.totalSlots). */
  get slots(): number { return this.meter.totalSlots; }

  voiceAt(instrument: PercussionInstrument, slot: number): number | null {
    return this.grid.get(instrument)![slot];
  }

  cycled(instrument: PercussionInstrument, slot: number): PercussionPattern {
    const count = voiceCount(instrument);
    const cur = this.grid.get(instrument)![slot];
    const next = cur === null ? 0 : cur >= count - 1 ? null : cur + 1;
    return this.withCell(instrument, slot, next);
  }

  withCell(instrument: PercussionInstrument, slot: number, voice: number | null): PercussionPattern {
    const row = this.grid.get(instrument)!.slice();
    row[slot] = voice;
    const g = new Map(this.grid);
    g.set(instrument, row);
    return new PercussionPattern(g, this.meter);
  }

  clearedRow(instrument: PercussionInstrument): PercussionPattern {
    const g = new Map(this.grid);
    g.set(instrument, Array<number | null>(this.slots).fill(null));
    return new PercussionPattern(g, this.meter);
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
    const g = new Map<PercussionInstrument, (number | null)[]>();
    for (const [inst, row] of this.grid) {
      const out: (number | null)[] = new Array(slots);
      for (let i = 0; i < slots; i++) out[i] = row[((i - shift) % slots + slots) % slots];
      g.set(inst, out);
    }
    return new PercussionPattern(g, this.meter);
  }

  /**
   * Re-fit this pattern onto [newMeter], copying cells by slot index (cells past
   * the new slot count are dropped; new slots are silent).
   */
  withMeter(newMeter: PercussionMeter): PercussionPattern {
    if (newMeter.equals(this.meter)) return this;
    const n = newMeter.totalSlots;
    const g = new Map<PercussionInstrument, (number | null)[]>();
    for (const inst of PERCUSSION_INSTRUMENTS) {
      const old = this.grid.get(inst)!;
      const out: (number | null)[] = new Array(n);
      for (let i = 0; i < n; i++) out[i] = i < old.length ? old[i] : null;
      g.set(inst, out);
    }
    return new PercussionPattern(g, newMeter);
  }

  /**
   * Serialize for persistence. New format carries the meter:
   *   "M:bars,beatsPerBar,beatUnit,division;row|row|…"
   * Each row is its cells comma-separated, silent = "-".
   */
  encode(): string {
    const m = `M:${this.meter.bars},${this.meter.beatsPerBar},${this.meter.beatUnit},${this.meter.division};`;
    const body = PERCUSSION_INSTRUMENTS.map((inst) => this.grid.get(inst)!.map((v) => (v === null ? "-" : String(v))).join(",")).join("|");
    return m + body;
  }

  static empty(meter: PercussionMeter = PercussionMeter.DEFAULT): PercussionPattern {
    const g = new Map<PercussionInstrument, (number | null)[]>();
    for (const inst of PERCUSSION_INSTRUMENTS) g.set(inst, Array<number | null>(meter.totalSlots).fill(null));
    return new PercussionPattern(g, meter);
  }

  /** Parse a string produced by [encode]; null if malformed or out of range.
   *  Accepts the new "M:…;rows" form and the legacy meter-less 16-cell form. */
  static decode(s: string): PercussionPattern | null {
    let meter = PercussionMeter.DEFAULT;
    let body = s;
    if (s.startsWith("M:")) {
      const sep = s.indexOf(";");
      if (sep < 0) return null;
      const parts = s.substring(2, sep).split(",");
      if (parts.length !== 4) return null;
      const ints = parts.map((p) => parseInt(p, 10));
      if (ints.some((n) => Number.isNaN(n))) return null;
      meter = new PercussionMeter(ints[0], ints[1], ints[2], ints[3]);
      if (!meter.isValid()) return null;
      body = s.substring(sep + 1);
    }
    const rows = body.split("|");
    if (rows.length !== PERCUSSION_INSTRUMENTS.length) return null;
    const g = new Map<PercussionInstrument, (number | null)[]>();
    for (let idx = 0; idx < PERCUSSION_INSTRUMENTS.length; idx++) {
      const inst = PERCUSSION_INSTRUMENTS[idx];
      const cells = rows[idx].split(",");
      if (cells.length !== meter.totalSlots) return null;
      const row: (number | null)[] = [];
      for (const c of cells) {
        if (c === "-") { row.push(null); continue; }
        const n = parseInt(c, 10);
        if (Number.isNaN(n) || n < 0 || n >= voiceCount(inst)) return null;
        row.push(n);
      }
      g.set(inst, row);
    }
    return new PercussionPattern(g, meter);
  }
}

function rowWith(values: Record<number, number>): (number | null)[] {
  const row = Array<number | null>(PERCUSSION_SLOTS).fill(null);
  for (const [slot, v] of Object.entries(values)) row[parseInt(slot, 10)] = v;
  return row;
}

/** The built-in "stock samba" groove. */
export const SAMBA: PercussionPattern = (() => {
  const surdo = rowWith({ 0: 1, 4: 0, 8: 1, 12: 0 });
  const tamborim = rowWith({ 0: 0, 3: 0, 4: 1, 6: 0, 8: 0, 11: 0, 12: 1, 14: 0 });
  const pandeiro = Array.from({ length: PERCUSSION_SLOTS }, (_, i) => {
    switch (i % 4) { case 0: return 0; case 1: return 3; case 2: return 2; default: return 4; }
  });
  const agogo = rowWith({ 0: 0, 2: 1, 3: 1, 6: 0, 8: 0, 10: 1, 11: 1, 14: 0 });
  return new PercussionPattern(new Map<PercussionInstrument, (number | null)[]>([
    [PercussionInstrument.Surdo, surdo],
    [PercussionInstrument.Tamborim, tamborim],
    [PercussionInstrument.Pandeiro, pandeiro],
    [PercussionInstrument.Agogo, agogo],
  ]));
})();

// Built-in loadable grooves, transcribed from the app's own step grid. Defined via the
// encode() string form (rows = Surdo|Tamborim|Pandeiro|Agogô; "-" = silent) so they're
// compact and self-validating through decode().
function builtin(encoded: string): PercussionPattern {
  const p = PercussionPattern.decode(encoded);
  if (!p) throw new Error(`invalid built-in pattern: ${encoded}`);
  return p;
}

// Teleco-teco — the two classic phrasings. Surdo + pandeiro are shared; the tamborim
// and agogô are phase-shifted between the two.
export const TELECOTECO_1 = builtin(
  "1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
  "1,0,1,0,1,2,0,1,0,1,0,1,0,1,2,0" + "|" +
  "0,3,2,4,0,3,2,4,0,3,2,4,0,3,2,4" + "|" +
  "-,0,-,1,-,-,0,-,0,-,0,-,1,-,-,0",
);

export const TELECOTECO_2 = builtin(
  "1,-,-,2,0,-,-,2,1,-,-,2,0,-,-,2" + "|" +
  "0,1,0,1,0,1,2,0,1,0,1,0,1,2,0,1" + "|" +
  "0,3,2,4,0,3,2,4,0,3,2,4,0,3,2,4" + "|" +
  "0,-,0,-,1,-,-,0,-,0,-,1,-,-,0,-",
);

/** Grooves offered in the Drum-machine Load… menu (before the user's saved beats). */
export const BUILTIN_PATTERNS: { name: string; pattern: PercussionPattern }[] = [
  { name: "stock samba", pattern: SAMBA },
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

/** Wait (ms) after [slot] before the next, applying Brazilian subdivision swing. */
export function swungSlotMs(slot: number, bpm: number, swingPercent: number, division = 16): number {
  const base = slotMs(bpm, division);
  const frac = (Math.min(Math.max(swingPercent, 0), 100) / 100) * 0.5;
  const delayOf = (s: number) => (s % 2 === 1 ? base * frac : 0);
  return Math.max(Math.trunc(base + delayOf(slot + 1) - delayOf(slot)), 1);
}

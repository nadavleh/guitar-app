// Blocks: phrase-sequencer state + scheduler for the drum machine's Blocks view.
// Mirror of app/.../BlocksState.kt; design in
// docs/superpowers/specs/2026-07-22-drum-blocks-design.md.
//
// Playback loops the block column by column. Each column is 16 STRAIGHT slots
// long (2 bars of 2/4 in 16ths) for every track; within the column each track's
// phrase plays with ITS OWN swing micro-timing (its own clock). Because swing
// only moves onsets inside a beat and keeps the beat anchors exact, all tracks
// re-align at every beat — a swung chamada over a straight teleco-teco never
// drifts, and a swung phrase followed by a straight one snaps back naturally.

import {
  DrumBlock, materializedTemplate, PresetTrack, presetByLabel, basePercussionId,
  encodePresetTrack, decodePresetTrack, mergedPresets,
  encodeBlockFile, decodeBlockFile, BUILTIN_BLOCKS,
  PercussionInstrument, PercussionCatalog, slotMs, swungSlotMs, PercussionMeter,
  PERCUSSION_ACCENT, PERCUSSION_DYN, PERCUSSION_DYN_FACTORS, voiceCount,
} from "../theory";
import { WebAudioEngine, PercussionSynth } from "../audio";
import { synthClick, ACCENT_CLICK_HZ, BEAT_CLICK_HZ } from "./woodClick";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface BlocksDeps {
  audio: WebAudioEngine;
  onChange: () => void;
  getSaved: () => Map<string, string>;       // name → DrumBlock.encode()
  save: (name: string, encoded: string) => void;
  del: (name: string) => void;
  /** USER-DEFINED phrases (custom track presets), label → encodePresetTrack(). */
  getTrackPresets: () => Map<string, string>;
  saveTrackPreset: (label: string, encoded: string) => void;
  delTrackPreset: (label: string) => void;
  /** Load a bundled one-shot sample for (instrument, voice), or null → synth fallback. */
  loadSample: (inst: PercussionInstrument, voice: number) => Promise<Float32Array | null>;
}

/** Phrases are all on the default 16-slot meter (2 bars of 2/4 in 16ths). */
const PHRASE_METER = PercussionMeter.DEFAULT;
const PHRASE_SLOTS = 16;

export class BlocksState {
  block: DrumBlock = DrumBlock.empty();
  bpm = 80;
  isPlaying = false;
  /** Column currently sounding (0-based), or -1 when stopped. */
  currentCol = -1;
  /** 16th-slot inside the current column (0-15, straight clock), or -1 when
   *  stopped — drives the playhead inside the mini phrase grids. */
  currentSlot = -1;
  /** True while the block's very FIRST column plays (openings sound instead of
   *  each track's first phrase); false once the loop wraps. */
  openingPass = false;

  /** Overlay a wood-click metronome (higher click on each bar's "1"). */
  metronomeOn = false;
  private readonly mClick = synthClick(BEAT_CLICK_HZ, 45);
  private readonly mAccent = synthClick(ACCENT_CLICK_HZ, 45);
  toggleMetronome() { this.metronomeOn = !this.metronomeOn; this.notify(); }

  private token = 0;
  private synth = new PercussionSynth();
  private synthCache = new Map<string, Float32Array>();
  private loadedSamples = new Map<string, Float32Array>();
  private requestedSampleKits = new Set<string>();

  constructor(private deps: BlocksDeps) {
    // Open the Blocks view on the built-in Tamborim Block by default.
    const first = this.builtinBlocks()[0];
    if (first) this.block = first.block;
  }

  private notify() { this.deps.onChange(); }

  // ---- editing ----

  rename(name: string) { this.block = this.block.withName(name); this.notify(); }
  addTrack(inst: PercussionInstrument) { this.block = this.block.withTrack(inst); this.ensureSamplesFor(inst); this.notify(); }
  removeTrack(index: number) { this.block = this.block.withoutTrack(index); this.notify(); }
  /** col === -1 targets the track's OPENING cell (plays once, pass 1). */
  setCell(track: number, col: number, phrase: PresetTrack | null) {
    this.block = col === -1 ? this.block.withOpeningCell(track, phrase) : this.block.withCell(track, col, phrase);
    this.notify();
  }

  /** Override one cell's swing (0–100): the phrase keeps its own clock, so a
   *  swung cell over straight tracks stays bar-aligned. Saved with the block.
   *  col === -1 targets the opening cell. */
  setCellSwing(track: number, col: number, swing: number) {
    const t = this.block.tracks[track];
    const phrase = col === -1 ? t?.opening : t?.cells[col];
    if (!phrase) return;
    this.setCell(track, col, { ...phrase, swing: Math.min(Math.max(Math.round(swing), 0), 100) });
  }
  setPhraseCount(n: number) { this.block = this.block.withPhraseCount(n); this.notify(); }
  clear() { this.block = DrumBlock.empty(this.block.name, this.block.phraseCount); this.notify(); }

  // ---- the phrase library: built-ins + user-defined phrases ----

  /** Library lookup: a user phrase with a built-in's label REPLACES it. */
  private resolvePreset = (label: string): PresetTrack | undefined => {
    const enc = this.deps.getTrackPresets().get(label);
    const custom = enc ? decodePresetTrack(enc) : null;
    return custom ?? presetByLabel(label);
  };

  /** All phrases (built-ins overridden/extended by the user's). */
  allPresets(): PresetTrack[] {
    const customs: PresetTrack[] = [];
    for (const enc of this.deps.getTrackPresets().values()) {
      const p = decodePresetTrack(enc);
      if (p) customs.push(p);
    }
    return mergedPresets(customs);
  }

  /** Labels of the user-defined phrases (deletable / marked in lists). */
  customLabels(): Set<string> { return new Set(this.deps.getTrackPresets().keys()); }

  /** Store a complete phrase in the library as-is (its own swing included) —
   *  used by phrase-file import. Returns false on an empty/reserved-char label. */
  savePhrase(p: PresetTrack): boolean {
    const clean = p.label.trim();
    if (!clean || [...'=:,|@~^', "\n"].some((ch) => clean.includes(ch))) return false;
    this.deps.saveTrackPreset(clean, encodePresetTrack({ ...p, label: clean }));
    this.notify();
    return true;
  }

  /** Save a Beat-editor track as a named phrase (custom track preset). The row's
   *  accents + dynamics ride along in the raw values; clones save as their base
   *  instrument. Returns false when the label is empty/has reserved chars. */
  saveTrackAsPreset(inst: PercussionInstrument, row: (number | null)[], label: string): boolean {
    const base = PercussionCatalog.byId(basePercussionId(inst.id)) ?? inst;
    const template = Array.from({ length: 16 }, (_, i) => row[i] ?? null);
    return this.savePhrase({ label, instrument: base, template, swing: 0 });
  }

  deleteTrackPreset(label: string) { this.deps.delTrackPreset(label); this.notify(); }

  /** Phrases available for a track's instrument (block cells are per-instrument). */
  phrasesFor(inst: PercussionInstrument): PresetTrack[] {
    return this.allPresets().filter((p) => basePercussionId(p.instrument.id) === basePercussionId(inst.id));
  }

  /** BUILT-IN blocks, decoded against the current phrase library (custom
   *  phrases with matching labels substitute into them too). */
  builtinBlocks(): { name: string; block: DrumBlock }[] {
    const out: { name: string; block: DrumBlock }[] = [];
    for (const enc of BUILTIN_BLOCKS) {
      const b = DrumBlock.decode(enc, this.resolvePreset);
      if (b) out.push({ name: b.name, block: b });
    }
    return out;
  }

  /** Merge candidates: built-in + saved blocks with the same phrase count. */
  mergeCandidates(): { name: string; block: DrumBlock }[] {
    const out: { name: string; block: DrumBlock }[] = [];
    for (const { name, block } of this.builtinBlocks()) {
      if (block.phraseCount === this.block.phraseCount && name !== this.block.name) out.push({ name, block });
    }
    for (const [name, enc] of this.deps.getSaved()) {
      const b = DrumBlock.decode(enc, this.resolvePreset);
      if (b && b.phraseCount === this.block.phraseCount && name !== this.block.name) out.push({ name, block: b });
    }
    return out;
  }

  mergeWith(other: DrumBlock) {
    const merged = this.block.mergedWith(other);
    if (merged) { this.block = merged; this.notify(); }
  }

  // ---- save / load ----

  savedBlocks(): Map<string, DrumBlock> {
    const out = new Map<string, DrumBlock>();
    for (const [name, enc] of this.deps.getSaved()) {
      const b = DrumBlock.decode(enc, this.resolvePreset);
      if (b) out.set(name, b);
    }
    return out;
  }
  saveCurrent() { this.deps.save(this.block.name, this.block.encode(this.resolvePreset)); this.notify(); }

  // ---- block files (export / import) ----

  /** Block file: the encoded block plus every USER-DEFINED phrase it references
   *  (cells + openings), so the block is portable to another device. */
  exportBlockFile(): string {
    const customs = this.deps.getTrackPresets();
    const used = new Map<string, PresetTrack>();
    for (const t of this.block.tracks) {
      for (const cell of [...t.cells, t.opening ?? null]) {
        if (!cell || used.has(cell.label)) continue;
        const enc = customs.get(cell.label);
        const p = enc ? decodePresetTrack(enc) : null;
        if (p) used.set(cell.label, p);
      }
    }
    return encodeBlockFile(this.block.encode(this.resolvePreset), [...used.values()]);
  }

  /** Import a block file: restore its embedded phrases into the library, then
   *  decode the block (preferring the embedded phrases) and load it. */
  importBlockFile(text: string): boolean {
    const parsed = decodeBlockFile(text);
    if (!parsed) return false;
    const byLabel = new Map(parsed.phrases.map((p) => [p.label, p] as const));
    for (const p of parsed.phrases) this.savePhrase(p);
    const b = DrumBlock.decode(parsed.block, (lbl) => byLabel.get(lbl) ?? this.resolvePreset(lbl));
    if (!b) return false;
    this.loadBlock(b);
    return true;
  }
  loadBlock(b: DrumBlock) {
    this.block = b;
    for (const t of b.tracks) this.ensureSamplesFor(t.instrument);
    this.notify();
  }
  deleteSaved(name: string) { this.deps.del(name); this.notify(); }

  // ---- samples (same prefer-sample-else-synth policy as the beat looper) ----

  private key(inst: PercussionInstrument, voice: number) { return `${inst.id}:${voice}`; }

  private buffer(inst: PercussionInstrument, voice: number): Float32Array {
    const k = this.key(inst, voice);
    const loaded = this.loadedSamples.get(k);
    if (loaded) return loaded;
    let buf = this.synthCache.get(k);
    if (!buf) { buf = this.synth.synthesize(inst, voice); this.synthCache.set(k, buf); }
    return buf;
  }

  private ensureSamplesFor(inst: PercussionInstrument): void {
    if (this.requestedSampleKits.has(inst.id)) return;
    this.requestedSampleKits.add(inst.id);
    for (let v = 0; v < voiceCount(inst); v++) {
      void this.deps.loadSample(inst, v).then((buf) => {
        if (buf) { this.loadedSamples.set(this.key(inst, v), buf); this.notify(); }
      });
    }
  }

  // ---- playback ----

  /** Absolute onset (seconds after column start) of `slot` under `swing` — the
   *  telescoped sum of swungSlotMs durations, so anchors match the beat looper. */
  private onsetSec(slot: number, swing: number): number {
    let ms = 0;
    for (let k = 0; k < slot; k++) ms += swungSlotMs(k, this.bpm, swing, PHRASE_METER);
    return ms / 1000;
  }

  toggle() { if (this.isPlaying) this.stop(); else this.start(); }

  start() {
    if (this.isPlaying || this.block.isEmpty()) return;
    for (const t of this.block.tracks) this.ensureSamplesFor(t.instrument);
    this.isPlaying = true;
    this.token++;
    const token = this.token;
    this.notify();
    void (async () => {
      let colStart = this.deps.audio.now() + 0.06;
      let colIndex = 0;
      while (this.isPlaying && token === this.token) {
        const snapshot = this.block;             // re-read each column so edits apply next column
        if (snapshot.tracks.length === 0) { this.stop(); return; }
        const cols = snapshot.phraseCount;
        const c = colIndex % cols;
        this.currentCol = c;
        this.notify();
        this.openingPass = colIndex === 0;
        // Schedule the whole column for every track: each phrase with ITS swing.
        // What a track plays at absolute column ci: its OPENING at ci 0 (if set),
        // its cells afterwards — so `prev` (the return rule's input) tracks what
        // actually sounded, and no return rule fires before anything played.
        for (const [ti, t] of snapshot.tracks.entries()) {
          const playedAt = (ci: number): PresetTrack | null => {
            if (ci < 0) return null;
            if (ci === 0 && t.opening) return t.opening;
            return t.cells[ci % cols];
          };
          const phrase = playedAt(colIndex);
          const prev = playedAt(colIndex - 1);
          const tmpl = materializedTemplate(phrase, prev);
          if (!tmpl || !phrase) continue;
          const swing = phrase.swing ?? 0;
          for (let slot = 0; slot < PHRASE_SLOTS; slot++) {
            const raw = tmpl[slot];
            if (raw === null || raw === undefined) continue;
            const voice = raw % PERCUSSION_ACCENT;
            const accented = Math.floor(raw / PERCUSSION_ACCENT) % 10 === 1;
            const dyn = Math.floor(raw / PERCUSSION_DYN);
            const gain = (accented ? 1.4 : 1) * PERCUSSION_DYN_FACTORS[dyn];
            // Self-choke per TRACK (blocks may repeat an instrument — two
            // pandeiro players don't damp each other).
            const chokeKey = t.instrument.selfChoke ? `${t.instrument.id}@${ti}` : undefined;
            this.deps.audio.playSamples(this.buffer(t.instrument, voice), gain, colStart + this.onsetSec(slot, swing), chokeKey);
          }
        }
        // Metronome click track: one click per beat on the straight clock,
        // higher click on each bar's "1" (bars are 8 slots in 2/4 · 1/16).
        if (this.metronomeOn) {
          const baseSec = slotMs(this.bpm, PHRASE_METER.division) / 1000;
          for (let slot = 0; slot < PHRASE_SLOTS; slot += PHRASE_METER.slotsPerBeat) {
            const barDownbeat = slot % PHRASE_METER.slotsPerBar === 0;
            this.deps.audio.playSamples(barDownbeat ? this.mAccent : this.mClick, barDownbeat ? 0.9 : 0.6, colStart + slot * baseSec);
          }
        }
        // Columns advance on the STRAIGHT clock (16 × base slot) for all tracks.
        // Walk the 16 slots for the UI playhead (audio is already queued);
        // the last sleep ends ~30 ms early so the next column schedules in time.
        const slotSec = slotMs(this.bpm, PHRASE_METER.division) / 1000;
        for (let sl = 0; sl < PHRASE_SLOTS && this.isPlaying && token === this.token; sl++) {
          this.currentSlot = sl;
          this.notify();
          const target = colStart + (sl + 1) * slotSec;
          await sleep(Math.max((target - this.deps.audio.now()) * 1000 - (sl === PHRASE_SLOTS - 1 ? 30 : 0), 0));
        }
        colStart += PHRASE_SLOTS * slotSec;
        colIndex++;
      }
    })();
  }

  stop() {
    this.isPlaying = false;
    this.token++;
    this.currentCol = -1;
    this.currentSlot = -1;
    this.openingPass = false;
    this.deps.audio.stop();
    this.notify();
  }

  release() { this.stop(); }

  setBpm(v: number) { this.bpm = Math.min(Math.max(Math.round(v), 10), 300); this.notify(); }

  /** Instruments offered by "+ Track ▾" (catalog order; repeats allowed). */
  instrumentsToAdd(): PercussionInstrument[] { return PercussionCatalog.ALL; }
}

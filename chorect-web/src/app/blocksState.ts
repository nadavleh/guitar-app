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
  DrumBlock, materializedTemplate, PresetTrack, PRESET_TRACKS,
  PercussionInstrument, PercussionCatalog, slotMs, swungSlotMs, PercussionMeter,
  PERCUSSION_ACCENT, PERCUSSION_DYN, PERCUSSION_DYN_FACTORS, voiceCount,
} from "../theory";
import { WebAudioEngine, PercussionSynth } from "../audio";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface BlocksDeps {
  audio: WebAudioEngine;
  onChange: () => void;
  getSaved: () => Map<string, string>;       // name → DrumBlock.encode()
  save: (name: string, encoded: string) => void;
  del: (name: string) => void;
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

  private token = 0;
  private synth = new PercussionSynth();
  private synthCache = new Map<string, Float32Array>();
  private loadedSamples = new Map<string, Float32Array>();
  private requestedSampleKits = new Set<string>();

  constructor(private deps: BlocksDeps) {}

  private notify() { this.deps.onChange(); }

  // ---- editing ----

  rename(name: string) { this.block = this.block.withName(name); this.notify(); }
  addTrack(inst: PercussionInstrument) { this.block = this.block.withTrack(inst); this.ensureSamplesFor(inst); this.notify(); }
  removeTrack(index: number) { this.block = this.block.withoutTrack(index); this.notify(); }
  setCell(track: number, col: number, phrase: PresetTrack | null) { this.block = this.block.withCell(track, col, phrase); this.notify(); }

  /** Override one cell's swing (0–100): the phrase keeps its own clock, so a
   *  swung cell over straight tracks stays bar-aligned. Saved with the block. */
  setCellSwing(track: number, col: number, swing: number) {
    const phrase = this.block.tracks[track]?.cells[col];
    if (!phrase) return;
    this.setCell(track, col, { ...phrase, swing: Math.min(Math.max(Math.round(swing), 0), 100) });
  }
  setPhraseCount(n: number) { this.block = this.block.withPhraseCount(n); this.notify(); }
  clear() { this.block = DrumBlock.empty(this.block.name, this.block.phraseCount); this.notify(); }

  /** Phrases available for a track's instrument (block cells are per-instrument). */
  phrasesFor(inst: PercussionInstrument): PresetTrack[] {
    const base = (id: string) => id.split("#")[0];
    return PRESET_TRACKS.filter((p) => base(p.instrument.id) === base(inst.id));
  }

  /** Merge candidates: saved blocks with the same phrase count. */
  mergeCandidates(): { name: string; block: DrumBlock }[] {
    const out: { name: string; block: DrumBlock }[] = [];
    for (const [name, enc] of this.deps.getSaved()) {
      const b = DrumBlock.decode(enc);
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
      const b = DrumBlock.decode(enc);
      if (b) out.set(name, b);
    }
    return out;
  }
  saveCurrent() { this.deps.save(this.block.name, this.block.encode()); this.notify(); }
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
        // Schedule the whole column for every track: each phrase with ITS swing.
        for (const t of snapshot.tracks) {
          const phrase = t.cells[c];
          const prev = t.cells[(c - 1 + cols) % cols];
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
            this.deps.audio.playSamples(this.buffer(t.instrument, voice), gain, colStart + this.onsetSec(slot, swing));
          }
        }
        // Columns advance on the STRAIGHT clock (16 × base slot) for all tracks.
        const colDurSec = (slotMs(this.bpm, PHRASE_METER.division) * PHRASE_SLOTS) / 1000;
        colStart += colDurSec;
        colIndex++;
        await sleep(Math.max((colStart - this.deps.audio.now()) * 1000 - 30, 0));
      }
    })();
  }

  stop() {
    this.isPlaying = false;
    this.token++;
    this.currentCol = -1;
    this.deps.audio.stop();
    this.notify();
  }

  release() { this.stop(); }

  setBpm(v: number) { this.bpm = Math.min(Math.max(Math.round(v), 10), 300); this.notify(); }

  /** Instruments offered by "+ Track ▾" (catalog order; repeats allowed). */
  instrumentsToAdd(): PercussionInstrument[] { return PercussionCatalog.ALL; }
}

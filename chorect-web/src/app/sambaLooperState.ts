// State + scheduler for the samba percussion looper, ported from
// app/.../SambaLooperState.kt. The Kotlin coroutine loop becomes a token-guarded
// async loop; voices are synthesized once and cached, then replayed each tick.

import {
  PercussionInstrument, PercussionCatalog,
  PercussionMeter, PercussionPattern, swungSlotMs, voiceCount,
  BEAT_UNITS, DIVISIONS,
} from "../theory";
import { WebAudioEngine, PercussionSynth } from "../audio";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface SambaDeps {
  audio: WebAudioEngine;
  onChange: () => void;
  getSaved: () => Map<string, string>;       // name → encoded pattern
  save: (name: string, encoded: string) => void;
  del: (name: string) => void;
  /** Load a bundled one-shot sample for (instrument, voice), or null → synth fallback. */
  loadSample: (inst: PercussionInstrument, voice: number) => Promise<Float32Array | null>;
}

export class SambaLooperState {
  pattern: PercussionPattern = PercussionPattern.empty();
  bpm = 140;
  swing = 0;
  isPlaying = false;
  currentSlot = -1;

  // Keyed by instrument id (string) so add/remove can't trip object-identity.
  muted = new Set<string>();
  soloed = new Set<string>();
  volumes = new Map<string, number>();

  private token = 0;
  private synth = new PercussionSynth();
  private synthCache = new Map<string, Float32Array>();
  private loadedSamples = new Map<string, Float32Array>();
  private requestedSampleKits = new Set<string>();

  constructor(private deps: SambaDeps) {}

  private notify() { this.deps.onChange(); }

  private key(instrument: PercussionInstrument, voiceIndex: number): string {
    return `${instrument.id}:${voiceIndex}`;
  }

  /** Prefer a loaded one-shot sample; otherwise the synthesized voice (cached). */
  private buffer(instrument: PercussionInstrument, voiceIndex: number): Float32Array {
    const k = this.key(instrument, voiceIndex);
    const loaded = this.loadedSamples.get(k);
    if (loaded) return loaded;
    let buf = this.synthCache.get(k);
    if (!buf) { buf = this.synth.synthesize(instrument, voiceIndex); this.synthCache.set(k, buf); }
    return buf;
  }

  /** True once a real sample file has been loaded for this voice (else it's the synth). */
  usesSample(instrument: PercussionInstrument, voiceIndex: number): boolean {
    return this.loadedSamples.has(this.key(instrument, voiceIndex));
  }

  /** Kick off a one-time async load of any available WAV samples for the current
   *  kit (synth meanwhile). Per-instrument so newly added instruments load too. */
  private ensureSamplesLoaded(): void {
    for (const inst of this.pattern.instruments) this.loadSamplesFor(inst);
  }

  private loadSamplesFor(inst: PercussionInstrument): void {
    if (this.requestedSampleKits.has(inst.id)) return;
    this.requestedSampleKits.add(inst.id);
    for (let v = 0; v < voiceCount(inst); v++) {
      void this.deps.loadSample(inst, v).then((buf) => {
        if (buf) { this.loadedSamples.set(this.key(inst, v), buf); this.notify(); }
      });
    }
  }

  toggleMute(inst: PercussionInstrument) {
    if (this.muted.has(inst.id)) this.muted.delete(inst.id); else this.muted.add(inst.id);
    this.notify();
  }
  toggleSolo(inst: PercussionInstrument) {
    if (this.soloed.has(inst.id)) this.soloed.delete(inst.id); else this.soloed.add(inst.id);
    this.notify();
  }
  isAudible(inst: PercussionInstrument): boolean {
    return !this.muted.has(inst.id) && (this.soloed.size === 0 || this.soloed.has(inst.id));
  }

  volumeOf(inst: PercussionInstrument): number { return this.volumes.get(inst.id) ?? 1; }
  setVolume(inst: PercussionInstrument, value: number) {
    this.volumes.set(inst.id, Math.min(Math.max(value, 0), 1));
    this.notify();
  }

  toggleSlot(instrument: PercussionInstrument, slot: number) {
    this.ensureSamplesLoaded();
    this.pattern = this.pattern.cycled(instrument, slot);
    const v = this.pattern.voiceAt(instrument, slot);
    if (v !== null && !this.isPlaying) this.deps.audio.playSamples(this.buffer(instrument, v), this.volumeOf(instrument));
    this.notify();
  }

  preview(instrument: PercussionInstrument, voiceIndex: number) {
    this.ensureSamplesLoaded();
    this.deps.audio.playSamples(this.buffer(instrument, voiceIndex), this.volumeOf(instrument));
  }

  clearCell(instrument: PercussionInstrument, slot: number) { this.pattern = this.pattern.withCell(instrument, slot, null); this.notify(); }
  clearRow(instrument: PercussionInstrument) { this.pattern = this.pattern.clearedRow(instrument); this.notify(); }
  clearAll() { this.pattern = PercussionPattern.empty(this.pattern.instruments, this.pattern.meter); this.notify(); }

  // ---- kit: add / remove instruments ----

  /** Catalog instruments not yet in the kit, in catalog order (for the picker). */
  instrumentsToAdd(): PercussionInstrument[] {
    return PercussionCatalog.ALL.filter((i) => !this.pattern.hasInstrument(i));
  }

  /** Add `inst` to the kit (silent row), load its samples, and audition voice 0. */
  addInstrument(inst: PercussionInstrument) {
    this.pattern = this.pattern.addInstrument(inst);
    this.loadSamplesFor(inst);
    if (!this.isPlaying) this.deps.audio.playSamples(this.buffer(inst, 0), this.volumeOf(inst));
    this.notify();
  }

  /** Remove `inst` from the kit, also clearing its mute/solo state. */
  removeInstrument(inst: PercussionInstrument) {
    this.pattern = this.pattern.removeInstrument(inst);
    this.muted.delete(inst.id);
    this.soloed.delete(inst.id);
    this.notify();
  }

  // ---- meter (bars / time signature / division) ----

  get meter(): PercussionMeter { return this.pattern.meter; }

  /** Re-fit the current pattern onto [newMeter] (cells preserved by slot index). */
  setMeter(newMeter: PercussionMeter) { this.pattern = this.pattern.withMeter(newMeter); this.notify(); }

  setBars(bars: number) {
    this.setMeter(this.meter.copy({ bars: Math.min(Math.max(bars, 1), 8) }));
  }

  /** Set the time signature. If the new beat unit can't host the current division
   *  (division must be a multiple of beatUnit), bump the division up so it stays valid. */
  setTimeSignature(beatsPerBar: number, beatUnit: number) {
    const beats = Math.min(Math.max(beatsPerBar, 1), 12);
    const unit = (BEAT_UNITS as readonly number[]).includes(beatUnit) ? beatUnit : 4;
    const div = this.meter.division % unit === 0
      ? this.meter.division
      : DIVISIONS.find((d) => d % unit === 0 && d >= unit)!;
    this.setMeter(this.meter.copy({ beatsPerBar: beats, beatUnit: unit, division: div }));
  }

  setDivision(division: number) {
    if (!(DIVISIONS as readonly number[]).includes(division)) return;
    if (division % this.meter.beatUnit !== 0) return;
    this.setMeter(this.meter.copy({ division }));
  }

  /** Translate (rotate) the whole loop by [n] slots with wrap-around. */
  translate(n: number) { this.pattern = this.pattern.translated(n); this.notify(); }

  // ---- save / load ----

  /** Decoded saved beats, name → PercussionPattern. */
  savedPatterns(): Map<string, PercussionPattern> {
    const out = new Map<string, PercussionPattern>();
    for (const [name, enc] of this.deps.getSaved()) {
      const p = PercussionPattern.decode(enc);
      if (p) out.set(name, p);
    }
    return out;
  }
  saveCurrent(name: string) { this.deps.save(name, this.pattern.encode()); }
  loadPattern(p: PercussionPattern) { this.pattern = p; this.notify(); }
  deleteSaved(name: string) { this.deps.del(name); }

  start() {
    if (this.isPlaying) return;
    this.ensureSamplesLoaded();
    this.isPlaying = true;
    this.token++;
    const token = this.token;
    this.notify();
    void (async () => {
      while (this.isPlaying && token === this.token) {
        const snapshot = this.pattern;          // re-read each loop so meter edits take effect
        for (let slot = 0; slot < snapshot.slots; slot++) {
          if (!this.isPlaying || token !== this.token) break;
          this.currentSlot = slot;
          for (const inst of snapshot.instruments) {
            if (!this.isAudible(inst)) continue;
            const v = snapshot.voiceAt(inst, slot);
            if (v === null) continue;
            this.deps.audio.playSamples(this.buffer(inst, v), this.volumeOf(inst));
          }
          this.notify();
          await sleep(swungSlotMs(slot, this.bpm, this.swing, snapshot.meter));
        }
      }
    })();
  }

  stop() {
    this.isPlaying = false;
    this.token++;
    this.currentSlot = -1;
    this.deps.audio.stop();
    this.notify();
  }

  release() { this.stop(); }

  setBpm(v: number) { this.bpm = Math.round(v); this.notify(); }
  setSwing(v: number) { this.swing = Math.round(v); this.notify(); }
}

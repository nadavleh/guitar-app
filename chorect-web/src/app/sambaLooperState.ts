// State + scheduler for the samba percussion looper, ported from
// app/.../SambaLooperState.kt. The Kotlin coroutine loop becomes a token-guarded
// async loop; voices are synthesized once and cached, then replayed each tick.

import {
  PercussionInstrument, PercussionCatalog,
  PercussionMeter, PercussionPattern, swungSlotMs, voiceCount,
  BEAT_UNITS, DIVISIONS, BATIDA_CAVACO_1,
} from "../theory";
import { WebAudioEngine, PercussionSynth } from "../audio";
import { synthClick, ACCENT_CLICK_HZ, BEAT_CLICK_HZ } from "./woodClick";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface SambaDeps {
  audio: WebAudioEngine;
  onChange: () => void;
  getSaved: () => Map<string, string>;       // name → encoded pattern
  save: (name: string, encoded: string) => void;
  del: (name: string) => void;
  /** Load a bundled one-shot sample for (instrument, voice), or null → synth fallback. */
  loadSample: (inst: PercussionInstrument, voice: number) => Promise<Float32Array | null>;
  /** Persisted mixer volumes, keyed by "<instId>" or "<instId>:<voice>" → 0..1. */
  getVolumes: () => Map<string, number>;
  saveVolume: (key: string, value: number) => void;
}

export class SambaLooperState {
  // Default-load "batida do cavaco 1" (surdo + tamborim + bongo) so the machine opens
  // with a musical starting point on the default kit. Clear all / Load swaps it out.
  pattern: PercussionPattern = BATIDA_CAVACO_1;
  bpm = 70;
  swing = 0;
  isPlaying = false;
  currentSlot = -1;
  /** Name of the most recently loaded/saved beat (for the header caption); null = unnamed. */
  loadedName: string | null = "batida do cavaco 1";
  /** Overlay a wood-click metronome on the loop (higher click on each bar's "1"). */
  metronomeOn = false;
  private readonly mClick = synthClick(BEAT_CLICK_HZ, 45);
  private readonly mAccent = synthClick(ACCENT_CLICK_HZ, 45);
  toggleMetronome() { this.metronomeOn = !this.metronomeOn; this.notify(); }

  // ---- track selection + voice brush (the bottom palette) ----

  /** Id of the selected track (tap its name), or null. Selecting shows the voice
   *  palette; cells of the selected track follow [brush] instead of cycling. */
  selectedTrackId: string | null = null;
  /** What a tap paints on the selected track: "cycle" (default, classic behavior),
   *  "erase", or a voice index to place directly (tap a same-voice cell to clear). */
  brush: number | "cycle" | "erase" = "cycle";

  /** Toggle track selection; a fresh selection resets the brush to Cycle. */
  selectTrack(id: string) {
    if (this.selectedTrackId === id) { this.selectedTrackId = null; }
    else { this.selectedTrackId = id; this.brush = "cycle"; }
    this.notify();
  }
  setBrush(b: number | "cycle" | "erase") { this.brush = b; this.notify(); }

  /** Apply the current brush to a cell of the selected track. Voice brush paints
   *  that voice (tapping a cell already holding it clears — quick-clear). */
  applyBrush(instrument: PercussionInstrument, slot: number) {
    if (this.brush === "cycle") { this.toggleSlot(instrument, slot); return; }
    if (this.brush === "erase") { this.clearCell(instrument, slot); return; }
    const v = this.brush;
    const cur = this.pattern.voiceAt(instrument, slot);
    if (cur === v) { this.clearCell(instrument, slot); return; }
    this.ensureSamplesLoaded();
    this.commit(this.pattern.withCell(instrument, slot, v));
    if (!this.isPlaying) this.deps.audio.playSamples(this.buffer(instrument, v), this.effectiveGain(instrument, v));
  }

  // Undo stack of prior patterns (Ctrl-Z / Undo). Every edit pushes via commit().
  private undoStack: PercussionPattern[] = [];
  get canUndo(): boolean { return this.undoStack.length > 0; }

  /** Apply a pattern edit, recording the previous pattern for undo. */
  private commit(next: PercussionPattern) {
    if (next === this.pattern) return;
    this.undoStack.push(this.pattern);
    while (this.undoStack.length > 50) this.undoStack.shift();
    this.pattern = next;
    this.loadedName = null;   // an edit means it's no longer the named beat (load/save re-sets)
    this.notify();
  }

  /** Undo the last pattern edit. */
  undo() {
    const prev = this.undoStack.pop();
    if (!prev) return;
    this.pattern = prev;
    this.notify();
  }

  /** Reorder the kit: move the track at `from` to index `to`. */
  reorderInstrument(from: number, to: number) { this.commit(this.pattern.movedInstrument(from, to)); }

  // Tap-tempo: average the intervals of the recent taps (2 s window, last 6).
  private tapTimes: number[] = [];
  tapTempo(nowMs: number = Date.now()) {
    if (this.tapTimes.length && nowMs - this.tapTimes[this.tapTimes.length - 1] > 2000) this.tapTimes = [];
    this.tapTimes.push(nowMs);
    while (this.tapTimes.length > 6) this.tapTimes.shift();
    if (this.tapTimes.length >= 2) {
      const avg = (this.tapTimes[this.tapTimes.length - 1] - this.tapTimes[0]) / (this.tapTimes.length - 1);
      this.bpm = Math.min(Math.max(Math.round(60000 / avg), 10), 300);
    }
    this.notify();
  }
  /** Seconds until a buffer first reaches 90% of its peak (crescendos bloom late). */
  private peakOffsetCache = new Map<string, number>();
  private peakOffsetSec(inst: PercussionInstrument, voiceIndex: number, buf: Float32Array): number {
    const k = this.key(inst, voiceIndex);
    const hit = this.peakOffsetCache.get(k);
    if (hit !== undefined) return hit;
    let peak = 0;
    for (const s of buf) { const a = Math.abs(s); if (a > peak) peak = a; }
    let i = 0;
    if (peak > 0) { const th = peak * 0.9; while (i < buf.length && Math.abs(buf[i]) < th) i++; }
    const sec = i / 44100;
    this.peakOffsetCache.set(k, sec);
    return sec;
  }

  // Keyed by instrument id (string) so add/remove can't trip object-identity.
  muted = new Set<string>();
  soloed = new Set<string>();
  volumes = new Map<string, number>();

  private token = 0;
  private synth = new PercussionSynth();
  private synthCache = new Map<string, Float32Array>();
  private loadedSamples = new Map<string, Float32Array>();
  private requestedSampleKits = new Set<string>();

  constructor(private deps: SambaDeps) {
    // Load the persisted mix so it survives reloads / closing the tab.
    this.volumes = new Map(deps.getVolumes());
  }

  /** Voices that start quieter than full: the tamborim "muted clack" (1) and
   *  "tap" (2) are much softer than its open clack, so default them to 50%. */
  private static readonly SOFT_VOICE_DEFAULTS: Record<string, number> = {
    "tamborim:1": 0.5,
    "tamborim:2": 0.5,
  };
  static defaultVoiceVolume(instId: string, voiceIndex: number): number {
    return SambaLooperState.SOFT_VOICE_DEFAULTS[`${instId}:${voiceIndex}`] ?? 1;
  }
  private voiceKey(inst: PercussionInstrument, voiceIndex: number): string {
    return `${inst.id}:${voiceIndex}`;
  }

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

  /** Global level of an instrument (default full). */
  volumeOf(inst: PercussionInstrument): number { return this.volumes.get(inst.id) ?? (inst.id === "agogo" ? 0.1 : 1); } // agogô defaults quiet
  /** Level of one voice (default full, or 50% for the soft tamborim voices). */
  voiceVolumeOf(inst: PercussionInstrument, voiceIndex: number): number {
    return this.volumes.get(this.voiceKey(inst, voiceIndex)) ?? SambaLooperState.defaultVoiceVolume(inst.id, voiceIndex);
  }
  /** Combined gain a hit actually plays at: global × per-voice. */
  effectiveGain(inst: PercussionInstrument, voiceIndex: number): number {
    return this.volumeOf(inst) * this.voiceVolumeOf(inst, voiceIndex);
  }
  setVolume(inst: PercussionInstrument, value: number) {
    const v = Math.min(Math.max(value, 0), 1);
    this.volumes.set(inst.id, v);
    this.deps.saveVolume(inst.id, v);
    this.notify();
  }
  setVoiceVolume(inst: PercussionInstrument, voiceIndex: number, value: number) {
    const v = Math.min(Math.max(value, 0), 1);
    const key = this.voiceKey(inst, voiceIndex);
    this.volumes.set(key, v);
    this.deps.saveVolume(key, v);
    this.notify();
  }

  toggleSlot(instrument: PercussionInstrument, slot: number) {
    this.ensureSamplesLoaded();
    this.commit(this.pattern.cycled(instrument, slot));
    const v = this.pattern.voiceAt(instrument, slot);
    if (v !== null && !this.isPlaying) this.deps.audio.playSamples(this.buffer(instrument, v), this.effectiveGain(instrument, v));
  }

  preview(instrument: PercussionInstrument, voiceIndex: number) {
    this.ensureSamplesLoaded();
    this.deps.audio.playSamples(this.buffer(instrument, voiceIndex), this.effectiveGain(instrument, voiceIndex));
  }

  toggleAccent(instrument: PercussionInstrument, slot: number) { this.commit(this.pattern.accentToggled(instrument, slot)); }
  clearCell(instrument: PercussionInstrument, slot: number) { this.commit(this.pattern.withCell(instrument, slot, null)); }
  clearRow(instrument: PercussionInstrument) { this.commit(this.pattern.clearedRow(instrument)); }
  clearAll() { this.commit(PercussionPattern.empty(this.pattern.instruments, this.pattern.meter)); }

  // ---- kit: add / remove instruments ----

  /** Catalog instruments not yet in the kit, in catalog order (for the picker). */
  instrumentsToAdd(): PercussionInstrument[] {
    return PercussionCatalog.ALL.filter((i) => !this.pattern.hasInstrument(i));
  }

  /** Add `inst` to the kit (silent row), load its samples, and audition voice 0. */
  addInstrument(inst: PercussionInstrument) {
    this.commit(this.pattern.addInstrument(inst));
    this.loadSamplesFor(inst);
    if (!this.isPlaying) this.deps.audio.playSamples(this.buffer(inst, 0), this.effectiveGain(inst, 0));
  }

  /** Remove `inst` from the kit, also clearing its mute/solo/selection state. */
  removeInstrument(inst: PercussionInstrument) {
    this.commit(this.pattern.removeInstrument(inst));
    this.muted.delete(inst.id);
    this.soloed.delete(inst.id);
    if (this.selectedTrackId === inst.id) { this.selectedTrackId = null; this.brush = "cycle"; }
    this.notify();
  }

  // ---- meter (bars / time signature / division) ----

  get meter(): PercussionMeter { return this.pattern.meter; }

  /** Re-fit the current pattern onto [newMeter] (cells preserved by slot index). */
  setMeter(newMeter: PercussionMeter) { this.commit(this.pattern.withMeter(newMeter)); }

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
  translate(n: number) { this.commit(this.pattern.translated(n)); }

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
  saveCurrent(name: string) { this.deps.save(name, this.pattern.encode()); this.loadedName = name; this.notify(); }
  /** Load a pattern, optionally naming it (caption) and setting its tempo/swing. */
  loadPattern(p: PercussionPattern, name: string | null = null, bpm: number | null = null, swing: number | null = null) {
    this.commit(p);
    this.loadedName = name;
    if (bpm !== null) this.bpm = Math.min(Math.max(Math.round(bpm), 10), 300);
    if (swing !== null) this.swing = Math.min(Math.max(Math.round(swing), 0), 100);
    this.notify();
  }
  deleteSaved(name: string) { this.deps.del(name); }

  /**
   * Lookahead sequencer ("a tale of two clocks"): each slot's audio is scheduled
   * ONE SLOT AHEAD on the AudioContext clock via playSamples(…, when), so
   * setTimeout jitter no longer moves hits and timing never drifts. Crescendo
   * voices (peak later than ~20 ms) are started early — capped by the one-slot
   * lookahead — so their peak lands on the beat. The sleep loop only drives the
   * UI playhead.
   */
  start() {
    if (this.isPlaying) return;
    this.ensureSamplesLoaded();
    this.isPlaying = true;
    this.token++;
    const token = this.token;
    this.notify();
    const scheduleSlot = (snapshot: PercussionPattern, slot: number, when: number) => {
      // Metronome click track: one click per beat, higher click on each bar's "1".
      if (this.metronomeOn) {
        const m = snapshot.meter;
        if (slot % m.slotsPerBeat === 0) {
          const barDownbeat = slot % m.slotsPerBar === 0;
          this.deps.audio.playSamples(barDownbeat ? this.mAccent : this.mClick, barDownbeat ? 0.9 : 0.6, when);
        }
      }
      for (const inst of snapshot.instruments) {
        if (!this.isAudible(inst)) continue;
        const v = snapshot.voiceAt(inst, slot);
        if (v === null) continue;
        const buf = this.buffer(inst, v);
        const peak = this.peakOffsetSec(inst, v, buf);
        const now = this.deps.audio.now();
        const advance = peak > 0.02 ? Math.min(peak, Math.max(when - now, 0)) : 0;
        // Accented hits play ~1.4× louder (mixer clamps overall).
        const gain = this.effectiveGain(inst, v) * (snapshot.isAccented(inst, slot) ? 1.4 : 1);
        this.deps.audio.playSamples(buf, gain, when - advance);
      }
    };
    void (async () => {
      let nextOnset = this.deps.audio.now();
      let first = true;
      while (this.isPlaying && token === this.token) {
        const snapshot = this.pattern;          // re-read each loop so meter edits take effect
        for (let slot = 0; slot < snapshot.slots; slot++) {
          if (!this.isPlaying || token !== this.token) break;
          this.currentSlot = slot;
          if (first) { scheduleSlot(snapshot, slot, 0); first = false; }
          const slotSec = swungSlotMs(slot, this.bpm, this.swing, snapshot.meter) / 1000;
          nextOnset += slotSec;
          const nextSlot = (slot + 1) % snapshot.slots;
          const nextSnapshot = nextSlot === 0 ? this.pattern : snapshot;
          if (nextSlot < nextSnapshot.slots) scheduleSlot(nextSnapshot, nextSlot, nextOnset);
          this.notify();
          // Sleep till the next onset (UI playhead only — audio is already queued).
          await sleep(Math.max((nextOnset - this.deps.audio.now()) * 1000, 0));
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

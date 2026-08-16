// State + scheduler for the samba percussion looper, ported from
// app/.../SambaLooperState.kt. The Kotlin coroutine loop becomes a token-guarded
// async loop; voices are synthesized once and cached, then replayed each tick.

import {
  PercussionInstrument, PercussionCatalog, basePercussionId, PresetTrack,
  PercussionMeter, PercussionPattern, swungSlotMs, SwingModel, voiceCount, BUILTIN_PATTERNS,
  BEAT_UNITS, DIVISIONS, PERCUSSION_DYN_FACTORS, PERCUSSION_ACCENT,
  renderPercussion, encodeWavMono16, PercussionRenderResult,
} from "../theory";
import { WebAudioEngine, PercussionSynth } from "../audio";
import { synthClick, ACCENT_CLICK_HZ, BEAT_CLICK_HZ } from "./woodClick";
import { pandeiroEq } from "./pandeiroEq";

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export interface SambaDeps {
  audio: WebAudioEngine;
  onChange: () => void;
  /** Lightweight per-tick playhead update (no full DOM rebuild) — keeps scrolling
   *  smooth during playback. Falls back to onChange when absent. */
  onPlayhead?: () => void;
  getSaved: () => Map<string, string>;       // name → encoded pattern
  save: (name: string, encoded: string) => void;
  del: (name: string) => void;
  /** Load a bundled one-shot sample for (instrument, voice), or null → synth fallback. */
  loadSample: (inst: PercussionInstrument, voice: number) => Promise<Float32Array | null>;
  /** Persisted mixer volumes, keyed by "<instId>" or "<instId>:<voice>" → 0..1. */
  getVolumes: () => Map<string, number>;
  saveVolume: (key: string, value: number) => void;
}

/** Separator between the main pattern, opening pattern, and notes in a saved
 *  beat's encoded value ("main~opening~notes"; empty middle part when the beat
 *  has notes but no opening). '~' never appears in PercussionPattern.encode()
 *  and is escaped out of the notes; older app versions fail to decode the
 *  combined string and simply skip the beat rather than mis-reading it. */
export const OPENING_SEP = "~";

/** Escape free-text notes for the '~'-separated beat value (newline-safe too,
 *  since Android's saved-beats store is newline-delimited). */
function escapeNotes(s: string): string {
  return s.replace(/%/g, "%25").replace(/~/g, "%7E").replace(/\r/g, "%0D").replace(/\n/g, "%0A");
}
function unescapeNotes(s: string): string {
  return s.replace(/%0A/g, "\n").replace(/%0D/g, "\r").replace(/%7E/g, "~").replace(/%25/g, "%");
}

/** A saved beat: the loop, an optional one-shot opening, and free-text notes. */
export interface SavedBeatValue { main: PercussionPattern; opening: PercussionPattern | null; notes: string; }

/** Encode a beat for persistence: "main", "main~opening", or "main~opening~notes". */
export function encodeBeatPatterns(main: PercussionPattern, opening: PercussionPattern | null, notes = ""): string {
  let out = main.encode();
  if (opening || notes) out += OPENING_SEP + (opening ? opening.encode() : "");
  if (notes) out += OPENING_SEP + escapeNotes(notes);
  return out;
}

/** Decode a persisted beat value; null if the main pattern is unreadable. A bad
 *  opening part is dropped, not fatal. */
export function decodeBeatPatterns(s: string): SavedBeatValue | null {
  const parts = s.split(OPENING_SEP);
  const main = PercussionPattern.decode(parts[0]);
  if (!main) return null;
  const opening = parts.length > 1 && parts[1] ? PercussionPattern.decode(parts[1]) : null;
  const notes = parts.length > 2 ? unescapeNotes(parts.slice(2).join(OPENING_SEP)) : "";
  return { main, opening, notes };
}

export class SambaLooperState {
  // Web opens with a single "Pandeiro — Reta" track at 0 % TRACK swing, so the
  // Open with the FIRST built-in groove loaded (both platforms).
  pattern: PercussionPattern = BUILTIN_PATTERNS[0].pattern;
  /** Optional one-shot "opening" (entrada) played once before the loop starts. */
  opening: PercussionPattern | null = null;
  /** Which pattern the grid is editing: the loop (false) or the opening (true). */
  editingOpening = false;
  /** True while the scheduler is sounding the opening pass (drives the playhead). */
  playingOpening = false;
  bpm = BUILTIN_PATTERNS[0].bpm ?? 80;
  swing = 0;
  /** Which 16th-note swing feel the beat looper uses (a test toggle; not persisted). */
  swingModel: SwingModel = SwingModel.Default;
  isPlaying = false;
  currentSlot = -1;
  /** Name of the most recently loaded/saved beat (for the header caption); null = unnamed. */
  loadedName: string | null = BUILTIN_PATTERNS[0].name;
  /** Free-text notes attached to the current beat (saved + exported with it). */
  beatNotes = "";

  /** The pattern the grid is currently editing (loop or opening). */
  get editPattern(): PercussionPattern {
    return this.editingOpening && this.opening ? this.opening : this.pattern;
  }

  /** Create an empty opening (same kit + meter as the loop) and start editing it. */
  addOpening() {
    if (!this.opening) {
      this.opening = PercussionPattern.empty(this.pattern.instruments, this.pattern.meter);
    }
    this.editingOpening = true;
    this.notify();
  }

  /** Create an opening pre-filled with a preset track (e.g. an entrada chunk). */
  addOpeningFromPreset(p: PresetTrack) {
    this.pushUndo();
    this.opening = PercussionPattern.empty([], this.pattern.meter).withPresetTrack(p.instrument, p.template, p.swing ?? 0);
    this.editingOpening = false;
    this.notify();
  }

  /** Delete the opening and return to editing the loop. */
  removeOpening() {
    if (!this.opening) return;
    this.pushUndo();
    this.opening = null;
    this.editingOpening = false;
    this.notify();
  }

  /** Switch which section (loop or opening) edits target. Both grids are always
   *  visible; interacting with a section's rows calls this first, so header
   *  tools (meter, palette, add) follow the section you touched last. Clears the
   *  track selection on a switch so the brush can't leak across sections. */
  editOpening(on: boolean) {
    if (on && !this.opening) { this.addOpening(); return; }
    const target = on && this.opening !== null;
    if (this.editingOpening === target) return;
    this.editingOpening = target;
    this.selectedTrackId = null;
    this.brush = "cycle";
    this.notify();
  }
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
    const cur = this.editPattern.voiceAt(instrument, slot);
    if (cur === v) { this.clearCell(instrument, slot); return; }
    this.ensureSamplesLoaded();
    this.commit(this.editPattern.withCell(instrument, slot, v));
    if (!this.isPlaying) this.deps.audio.playSamples(this.buffer(instrument, v), this.effectiveGain(instrument, v));
  }

  // Undo stack (Ctrl-Z / Undo). Every edit pushes a snapshot of BOTH patterns via
  // commit(), so undo restores loop + opening together.
  private undoStack: { main: PercussionPattern; opening: PercussionPattern | null }[] = [];
  get canUndo(): boolean { return this.undoStack.length > 0; }

  private pushUndo() {
    this.undoStack.push({ main: this.pattern, opening: this.opening });
    while (this.undoStack.length > 50) this.undoStack.shift();
  }

  /** Apply an edit to whichever pattern the grid is editing (loop or opening),
   *  recording the previous state for undo. */
  private commit(next: PercussionPattern) {
    if (next === this.editPattern) return;
    this.pushUndo();
    if (this.editingOpening && this.opening) this.opening = next;
    else this.pattern = next;
    this.loadedName = null;   // an edit means it's no longer the named beat (load/save re-sets)
    this.notify();
  }

  /** Undo the last edit (restores both loop and opening). */
  undo() {
    const prev = this.undoStack.pop();
    if (!prev) return;
    this.pattern = prev.main;
    this.opening = prev.opening;
    if (!this.opening) this.editingOpening = false;
    this.notify();
  }

  /** Reorder the kit: move the track at `from` to index `to`. */
  reorderInstrument(from: number, to: number) { this.commit(this.editPattern.movedInstrument(from, to)); }

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
  /** Voice keys whose WAV is still decoding — played as silence, not the synth. */
  private pendingSamples = new Set<string>();
  /** Empty buffer = silence (playSamples no-ops on length 0). */
  private static readonly SILENCE = new Float32Array(0);

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
  /** Per-tick playhead refresh: a lightweight class-only DOM update when the host
   *  provides [onPlayhead]; otherwise a full render. Keeps scrolling smooth. */
  private playTick() { if (this.deps.onPlayhead) this.deps.onPlayhead(); else this.notify(); }

  private key(instrument: PercussionInstrument, voiceIndex: number): string {
    return `${instrument.id}:${voiceIndex}`;
  }

  /** raw pandeiro buffer -> EQ'd version (high-pass + high-shelf), cached by identity. */
  private pandeiroEqCache = new Map<Float32Array, Float32Array>();

  /** Prefer a loaded one-shot sample; otherwise the synthesized voice (cached).
   *  Pandeiro voices are high-passed + brightened so they clear the surdo's low
   *  band (see pandeiroEq). */
  private buffer(instrument: PercussionInstrument, voiceIndex: number): Float32Array {
    const k = this.key(instrument, voiceIndex);
    let raw = this.loadedSamples.get(k);
    // A sampled voice whose WAV is still decoding: play silence, not the synth
    // (adding a track mid-loop used to play the synth as "weird clicks" until stop/play).
    if (!raw && this.pendingSamples.has(k)) return SambaLooperState.SILENCE;
    if (!raw) {
      raw = this.synthCache.get(k);
      if (!raw) { raw = this.synth.synthesize(instrument, voiceIndex); this.synthCache.set(k, raw); }
    }
    if (basePercussionId(instrument.id) === "pandeiro") {
      let eq = this.pandeiroEqCache.get(raw);
      if (!eq) { eq = pandeiroEq(raw, this.deps.audio.sampleRate); this.pandeiroEqCache.set(raw, eq); }
      return eq;
    }
    return raw;
  }

  /** True once a real sample file has been loaded for this voice (else it's the synth). */
  usesSample(instrument: PercussionInstrument, voiceIndex: number): boolean {
    return this.loadedSamples.has(this.key(instrument, voiceIndex));
  }

  /** Kick off a one-time async load of any available WAV samples for the current
   *  kits (loop + opening; synth meanwhile). Per-instrument so newly added
   *  instruments load too. */
  private ensureSamplesLoaded(): void {
    for (const inst of this.pattern.instruments) this.loadSamplesFor(inst);
    if (this.opening) for (const inst of this.opening.instruments) this.loadSamplesFor(inst);
  }

  private loadSamplesFor(inst: PercussionInstrument): void {
    if (this.requestedSampleKits.has(inst.id)) return;
    this.requestedSampleKits.add(inst.id);
    for (let v = 0; v < voiceCount(inst); v++) {
      const k = this.key(inst, v);
      this.pendingSamples.add(k);   // silent (not the clicky synth) until its WAV decodes
      const p = this.deps.loadSample(inst, v).then((buf) => {
        this.pendingSamples.delete(k);
        if (buf) this.loadedSamples.set(k, buf);
        this.notify();
      });
      this.samplePromises.push(p);
      void p;
    }
  }

  /** In-flight sample fetches, so [awaitSamplesLoaded] can wait for them. */
  private samplePromises: Promise<unknown>[] = [];

  /** Load samples and resolve once every requested voice has settled. An EXPORT must
   *  not race the fetches: playback tolerates a still-decoding voice by staying silent
   *  for one pass, but a rendered file would bake that silence in permanently. */
  private async awaitSamplesLoaded(): Promise<void> {
    this.ensureSamplesLoaded();
    while (this.samplePromises.length > 0) {
      const batch = this.samplePromises;
      this.samplePromises = [];
      await Promise.allSettled(batch);   // a load kicked off meanwhile lands in the next round
    }
  }

  // ---- WAV export ----

  /** How many times the loop repeats in an exported file. */
  exportCycles = 4;
  /** true = the file is exactly [exportCycles] cycles and loops seamlessly (a hit's
   *  ring-out wraps onto the start); false = the last ring-out is appended instead. */
  exportLoopExact = true;

  /**
   * Render the current LOOP offline to a 16-bit WAV.
   *
   * `onlyTrack` null exports the full kit exactly as you hear it (mute/solo, track and
   * voice volumes, accents and per-slot dynamics all applied); an instrument id exports
   * that track alone as a stem — deliberately ignoring mute/solo, since you named it.
   *
   * This is a RENDER, not a recording (see renderPercussion), so the file is identical
   * every time and unaffected by audio glitches or the tab losing focus.
   */
  async exportWav(onlyTrack: string | null = null): Promise<{
    fileName: string; bytes: Uint8Array; result: PercussionRenderResult;
  }> {
    await this.awaitSamplesLoaded();
    const result = renderPercussion({
      pattern: this.pattern,
      bpm: this.bpm,
      swing: this.swing,
      swingModel: this.swingModel,
      onlyTrack,
      includeTrack: (id) => {
        const inst = this.pattern.instruments.find((i) => i.id === id);
        return inst ? this.isAudible(inst) : true;
      },
      cycles: this.exportCycles,
      loopExact: this.exportLoopExact,
      sampleRate: this.deps.audio.sampleRate,
      voiceGain: (id, voice) => {
        const inst = this.pattern.instruments.find((i) => i.id === id);
        return inst ? this.voiceVolumeOf(inst, voice) : 1;
      },
    }, (id, voice) => {
      const inst = this.pattern.instruments.find((i) => i.id === id);
      if (!inst) return null;
      const buf = this.buffer(inst, voice);
      return buf.length > 0 ? buf : null;
    });
    return {
      fileName: this.exportFileName(onlyTrack),
      bytes: encodeWavMono16(result.samples, result.sampleRate),
      result,
    };
  }

  /** e.g. "Partido-Alto-Groove_surdo_90bpm_4x.wav" — safe on every filesystem. */
  private exportFileName(onlyTrack: string | null): string {
    const beat = (this.loadedName ?? "beat").replace(/[^A-Za-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 40);
    const track = onlyTrack?.replace("#", "-") ?? "full-mix";
    return `${beat || "beat"}_${track}_${this.bpm}bpm_${this.exportCycles}x.wav`;
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

  /** TRACK level (0..1): lives on the pattern (PercussionPattern.trackVolume),
   *  so balance travels WITH the beat — saved, exported, undone. */
  volumeOf(inst: PercussionInstrument): number { return this.editPattern.trackVolumeOf(inst.id) / 100; }
  /** Level of one voice (default full, or 50% for the soft tamborim voices);
   *  per-voice trims stay per-device (persisted). */
  voiceVolumeOf(inst: PercussionInstrument, voiceIndex: number): number {
    return this.volumes.get(this.voiceKey(inst, voiceIndex)) ?? SambaLooperState.defaultVoiceVolume(basePercussionId(inst.id), voiceIndex);
  }
  /** Combined gain a hit actually plays at: track (from `pat`) × per-voice. */
  effectiveGain(inst: PercussionInstrument, voiceIndex: number, pat: PercussionPattern = this.editPattern): number {
    return (pat.trackVolumeOf(inst.id) / 100) * this.voiceVolumeOf(inst, voiceIndex);
  }
  setVolume(inst: PercussionInstrument, value: number) {
    this.commit(this.editPattern.withTrackVolume(inst.id, Math.round(Math.min(Math.max(value, 0), 1) * 100)));
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
    this.commit(this.editPattern.cycled(instrument, slot));
    const v = this.editPattern.voiceAt(instrument, slot);
    if (v !== null && !this.isPlaying) this.deps.audio.playSamples(this.buffer(instrument, v), this.effectiveGain(instrument, v));
  }

  preview(instrument: PercussionInstrument, voiceIndex: number) {
    this.ensureSamplesLoaded();
    this.deps.audio.playSamples(this.buffer(instrument, voiceIndex), this.effectiveGain(instrument, voiceIndex),
      0, instrument.selfChoke ? instrument.id : undefined);
  }

  toggleAccent(instrument: PercussionInstrument, slot: number) { this.commit(this.editPattern.accentToggled(instrument, slot)); }
  /** Cycle a hit's per-slot volume 100 → 75 → 50 → 25 % (Dyn tool). */
  dynCycle(instrument: PercussionInstrument, slot: number) { this.commit(this.editPattern.dynCycled(instrument, slot)); }

  /** Paste a copied cell region (rows × slots of raw values) anchored at
   *  (trackIndex, slot) of the edited grid — spreading right/down, clipped at
   *  the edges. Cells whose voice doesn't exist on the target row are skipped. */
  pasteCells(trackIndex: number, slot: number, cells: (number | null)[][]) {
    let pat = this.editPattern;
    for (let r = 0; r < cells.length; r++) {
      const inst = pat.instruments[trackIndex + r];
      if (!inst) break;
      for (let c = 0; c < cells[r].length; c++) {
        const s = slot + c;
        if (s >= pat.slots) break;
        const v = cells[r][c];
        if (v !== null && (v % PERCUSSION_ACCENT) >= inst.voices.length) continue;
        pat = pat.withCell(inst, s, v);
      }
    }
    this.commit(pat);
  }
  /** Blank every cell of the rectangle [t0..t1] × [s0..s1] (used by Cut). */
  clearRegion(inOpening: boolean, t0: number, t1: number, s0: number, s1: number) {
    this.editOpening(inOpening);
    let pat = this.editPattern;
    for (let ti = t0; ti <= Math.min(t1, pat.instruments.length - 1); ti++) {
      const inst = pat.instruments[ti];
      for (let s = s0; s <= Math.min(s1, pat.slots - 1); s++) pat = pat.withCell(inst, s, null);
    }
    this.commit(pat);
  }

  /** Pure helper: `base` with the sub-rectangle [t0..t1]×[s0..s1] shifted by
   *  `delta` slots — vacated source cells cleared, results clipped at the edges.
   *  (Patterns are immutable, so `base` itself is untouched.) */
  private movedPattern(base: PercussionPattern, rect: { t0: number; t1: number; s0: number; s1: number }, delta: number): PercussionPattern {
    let pat = base;
    const slots = base.slots;
    for (let ti = rect.t0; ti <= Math.min(rect.t1, base.instruments.length - 1); ti++) {
      const inst = base.instruments[ti];
      const orig = base.grid.get(inst.id)!;
      for (let s = rect.s0; s <= Math.min(rect.s1, slots - 1); s++) pat = pat.withCell(inst, s, null);
      for (let s = rect.s0; s <= Math.min(rect.s1, slots - 1); s++) {
        const ns = s + delta;
        if (ns < 0 || ns >= slots) continue;
        pat = pat.withCell(inst, ns, orig[s] ?? null);
      }
    }
    return pat;
  }

  /** Live (un-committed) preview of the interactive selection drag: replace the
   *  edited grid with `base` shifted by `delta`. No undo entry is pushed — call
   *  [commitSelectionMove] once, on release, to record a single undo step. */
  previewSelectionMove(inOpening: boolean, base: PercussionPattern, rect: { t0: number; t1: number; s0: number; s1: number }, delta: number) {
    this.editOpening(inOpening);
    const next = this.movedPattern(base, rect, delta);
    if (this.editingOpening && this.opening) this.opening = next; else this.pattern = next;
    this.loadedName = null;
    this.notify();
  }

  /** Finalize the drag-move with ONE undo entry restoring the pre-drag grid. */
  commitSelectionMove(inOpening: boolean, base: PercussionPattern, rect: { t0: number; t1: number; s0: number; s1: number }, delta: number) {
    this.editOpening(inOpening);
    // Reset to base so commit() records the pre-move state, then apply the move.
    if (this.editingOpening && this.opening) this.opening = base; else this.pattern = base;
    this.commit(this.movedPattern(base, rect, delta));
  }

  clearCell(instrument: PercussionInstrument, slot: number) { this.commit(this.editPattern.withCell(instrument, slot, null)); }
  clearRow(instrument: PercussionInstrument) { this.commit(this.editPattern.clearedRow(instrument)); }
  clearAll() { this.commit(PercussionPattern.empty(this.editPattern.instruments, this.editPattern.meter)); }

  // ---- kit: add / remove instruments ----

  /** Catalog instruments not yet in the kit, in catalog order (for the picker). */
  instrumentsToAdd(): PercussionInstrument[] {
    return PercussionCatalog.ALL.filter((i) => !this.editPattern.hasInstrument(i));
  }

  /** Add `inst` to the kit (silent row), load its samples, and audition voice 0. */
  addInstrument(inst: PercussionInstrument) {
    this.commit(this.editPattern.addInstrument(inst));
    this.loadSamplesFor(inst);
    if (!this.isPlaying) this.deps.audio.playSamples(this.buffer(inst, 0), this.effectiveGain(inst, 0));
  }

  /** Duplicate `inst`'s track — same sound + a copy of its row, no re-picking the
   *  instrument or re-painting (the new track is a clone, e.g. "Surdo 2"). */
  duplicateTrack(inst: PercussionInstrument) {
    this.commit(this.editPattern.duplicatedTrack(inst));
  }

  /** One-press preset track (marcação surdo / teleco-teco tamborim): adds the
   *  instrument (cloned if present) with its row pre-filled, and auditions it. */
  addPresetTrack(p: PresetTrack) {
    // The phrase's own swing lands on the new TRACK (audible while global is 0).
    this.commit(this.editPattern.withPresetTrack(p.instrument, p.template, p.swing ?? 0));
    this.loadSamplesFor(p.instrument);
    if (!this.isPlaying) this.deps.audio.playSamples(this.buffer(p.instrument, 0), this.effectiveGain(p.instrument, 0));
  }

  /** Replace the whole beat with JUST this phrase as one looping track — the
   *  quickest way to loop a presaved phrase on its own. The other tracks, the
   *  opening and the notes go away (Undo restores them); the beat takes the
   *  phrase's name and swing. */
  loadPresetAsBeat(p: PresetTrack) {
    // The phrase's swing rides on the TRACK (global swing reset to 0), so adding
    // more tracks later doesn't inherit it.
    const solo = PercussionPattern.empty([], this.pattern.meter).withPresetTrack(p.instrument, p.template, p.swing ?? 0);
    this.loadPattern(solo, p.label, null, 0, null, "");
    this.loadSamplesFor(p.instrument);
  }

  /** Remove EVERY track from the edited section (clean slate; the meter and
   *  the opening/loop split stay). Undo restores them. */
  removeAllTracks() {
    this.commit(PercussionPattern.empty([], this.editPattern.meter));
    this.muted.clear();
    this.soloed.clear();
    this.selectedTrackId = null;
    this.brush = "cycle";
    this.notify();
  }

  /** Remove `inst` from the kit, also clearing its mute/solo/selection state. */
  removeInstrument(inst: PercussionInstrument) {
    this.commit(this.editPattern.removeInstrument(inst));
    this.muted.delete(inst.id);
    this.soloed.delete(inst.id);
    if (this.selectedTrackId === inst.id) { this.selectedTrackId = null; this.brush = "cycle"; }
    this.notify();
  }

  // ---- meter (bars / time signature / division) ----

  /** Meter of the pattern the grid is editing (opening can differ from the loop). */
  get meter(): PercussionMeter { return this.editPattern.meter; }

  /** Re-fit the edited pattern onto [newMeter] (cells preserved by slot index). */
  setMeter(newMeter: PercussionMeter) { this.commit(this.editPattern.withMeter(newMeter)); }

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

  /** Translate (rotate) the edited pattern by [n] slots with wrap-around. */
  translate(n: number) { this.commit(this.editPattern.translated(n)); }

  // ---- save / load ----

  /** Decoded saved beats, name → { main, opening, notes }. */
  savedPatterns(): Map<string, SavedBeatValue> {
    const out = new Map<string, SavedBeatValue>();
    for (const [name, enc] of this.deps.getSaved()) {
      const p = decodeBeatPatterns(enc);
      if (p) out.set(name, p);
    }
    return out;
  }
  saveCurrent(name: string) {
    this.deps.save(name, encodeBeatPatterns(this.pattern, this.opening, this.beatNotes));
    this.loadedName = name;
    this.notify();
  }
  /** Load a beat (loop + optional opening + notes), optionally naming it
   *  (caption) and setting its tempo/swing. Editing returns to the loop grid. */
  loadPattern(
    p: PercussionPattern, name: string | null = null,
    bpm: number | null = null, swing: number | null = null,
    opening: PercussionPattern | null = null, notes = "",
  ) {
    this.pushUndo();
    this.pattern = p;
    this.opening = opening;
    this.editingOpening = false;
    this.loadedName = name;
    this.beatNotes = notes;
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
        // Per-TRACK swing: the master clock walks the GLOBAL swing grid; a track
        // with its own swing (only honored while global is 0) shifts each hit by
        // its clock's onset difference. Anchors coincide, so the shift is ≤ 0.4
        // slot of ANTICIPATION — safely inside the one-slot scheduling lookahead.
        const trackWhen = when + this.trackOnsetDeltaSec(snapshot, inst.id, slot);
        const buf = this.buffer(inst, v);
        const peak = this.peakOffsetSec(inst, v, buf);
        const now = this.deps.audio.now();
        const advance = peak > 0.02 ? Math.min(peak, Math.max(trackWhen - now, 0)) : 0;
        // Accented hits play ~1.4× louder (mixer clamps overall); per-slot
        // dynamics scale the hit down (100/75/50/25 %).
        const gain = this.effectiveGain(inst, v, snapshot)
          * (snapshot.isAccented(inst, slot) ? 1.4 : 1)
          * PERCUSSION_DYN_FACTORS[snapshot.dynLevelAt(inst, slot)];
        // Self-choking instruments (pandeiro): each hit damps the track's
        // previous one at its own onset (kit ids are unique per track).
        this.deps.audio.playSamples(buf, gain, trackWhen - advance, inst.selfChoke ? inst.id : undefined);
      }
    };
    void (async () => {
      let nextOnset = this.deps.audio.now();
      let first = true;

      // ---- Opening: one pass of the (non-empty) opening pattern, then the loop.
      const op = this.opening;
      if (op && !op.isEmpty()) {
        this.playingOpening = true;
        for (let slot = 0; slot < op.slots; slot++) {
          if (!this.isPlaying || token !== this.token) break;
          this.currentSlot = slot;
          if (first) { scheduleSlot(op, slot, 0); first = false; }
          const slotSec = swungSlotMs(slot, this.bpm, this.swing, op.meter, this.swingModel) / 1000;
          nextOnset += slotSec;
          // Next up: the opening's next slot, or the loop's downbeat when it ends.
          if (slot + 1 < op.slots) {
            scheduleSlot(op, slot + 1, nextOnset);
            this.schedulePlayheads(op, slot + 1, nextOnset, token);
          } else {
            scheduleSlot(this.pattern, 0, nextOnset);
            this.schedulePlayheads(this.pattern, 0, nextOnset, token);
          }
          this.playTick();
          await sleep(Math.max((nextOnset - this.deps.audio.now()) * 1000, 0));
        }
        this.playingOpening = false;
      }

      while (this.isPlaying && token === this.token) {
        const snapshot = this.pattern;          // re-read each loop so meter edits take effect
        for (let slot = 0; slot < snapshot.slots; slot++) {
          if (!this.isPlaying || token !== this.token) break;
          this.currentSlot = slot;
          if (first) { scheduleSlot(snapshot, slot, 0); first = false; }
          const slotSec = swungSlotMs(slot, this.bpm, this.swing, snapshot.meter, this.swingModel) / 1000;
          nextOnset += slotSec;
          const nextSlot = (slot + 1) % snapshot.slots;
          const nextSnapshot = nextSlot === 0 ? this.pattern : snapshot;
          if (nextSlot < nextSnapshot.slots) {
            scheduleSlot(nextSnapshot, nextSlot, nextOnset);
            this.schedulePlayheads(nextSnapshot, nextSlot, nextOnset, token);
          }
          this.playTick();
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
    this.playingOpening = false;
    this.trackPlayhead.clear();
    this.deps.audio.stop();
    this.notify();
  }

  release() { this.stop(); }

  setBpm(v: number) { this.bpm = Math.round(v); this.notify(); }
  setSwing(v: number) { this.swing = Math.round(v); this.notify(); }
  setSwingModel(m: SwingModel) { this.swingModel = m; this.notify(); }

  // ---- Per-track swing (see PercussionPattern.trackSwing) ----

  /** Per-track swing MODEL (a track's own swing uses this; default V2). Runtime
   *  toggle keyed by track id, like the global [swingModel]. */
  trackSwingModel = new Map<string, SwingModel>();
  effectiveTrackSwingModel(id: string): SwingModel { return this.trackSwingModel.get(id) ?? SwingModel.Default; }
  setTrackSwingModel(id: string, m: SwingModel) { this.trackSwingModel.set(id, m); this.notify(); }

  /** The swing a track actually plays with: global overrides when nonzero. */
  effectiveTrackSwing(snapshot: PercussionPattern, id: string): number {
    return this.swing > 0 ? this.swing : snapshot.trackSwingOf(id);
  }

  /** Set one track's own swing (audible only while the global swing is 0).
   *  Part of the pattern → undo/save/export carry it. */
  setTrackSwing(inst: PercussionInstrument, v: number) {
    this.commit(this.editPattern.withTrackSwing(inst.id, v));
  }

  /** Onset shift (seconds, ≤ 0) of `id`'s slot vs the master clock's. The track
   *  term uses the TRACK's model (default V2); the master term uses the global one
   *  (and is straight whenever the global swing is 0, which is when this applies). */
  private trackOnsetDeltaSec(snapshot: PercussionPattern, id: string, slot: number): number {
    const s = this.effectiveTrackSwing(snapshot, id);
    if (s === this.swing) return 0;
    const tm = this.effectiveTrackSwingModel(id);
    let delta = 0;
    for (let k = 0; k < slot; k++) {
      delta += swungSlotMs(k, this.bpm, s, snapshot.meter, tm) - swungSlotMs(k, this.bpm, this.swing, snapshot.meter, this.swingModel);
    }
    return delta / 1000;
  }

  /** MULTIPLE PLAYHEADS: tracks with their own swing get their own playhead
   *  slot, updated by a timer at THEIR onset (which anticipates the master's).
   *  Straight tracks follow `currentSlot`. */
  private trackPlayhead = new Map<string, number>();

  /** The slot `inst`'s playhead is on right now (grid highlight). */
  playheadSlotFor(inst: PercussionInstrument): number {
    return this.trackPlayhead.get(inst.id) ?? this.currentSlot;
  }

  private schedulePlayheads(snapshot: PercussionPattern, slot: number, onset: number, token: number) {
    if (this.swing > 0) {
      if (this.trackPlayhead.size) { this.trackPlayhead.clear(); this.playTick(); }
      return;
    }
    const groups = new Map<number, string[]>();
    for (const inst of snapshot.instruments) {
      const s = snapshot.trackSwingOf(inst.id);
      if (s > 0) {
        const ids = groups.get(s) ?? [];
        ids.push(inst.id);
        groups.set(s, ids);
      }
    }
    for (const [, ids] of groups) {
      const fireIn = (onset + this.trackOnsetDeltaSec(snapshot, ids[0], slot) - this.deps.audio.now()) * 1000;
      setTimeout(() => {
        if (token !== this.token || !this.isPlaying) return;
        for (const id of ids) this.trackPlayhead.set(id, slot);
        this.playTick();
      }, Math.max(fireIn, 0));
    }
  }
}

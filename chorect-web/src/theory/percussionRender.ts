// Offline mixdown of a percussion loop — the render behind "Export WAV".
// Port of theory/.../PercussionRender.kt + WavFile.kt; keep the two in lock-step.
//
// This deliberately re-implements what the live scheduler does per slot
// (SambaLooperState.scheduleSlot + WebAudioEngine.playSamples) as a pure function of
// time, so an export is a deterministic RENDER rather than a recording of playback: no
// AudioContext, no scheduling jitter, and byte-identical output on both platforms.

import {
  PercussionPattern, PercussionMeter, SwingModel, PERCUSSION_DYN_FACTORS,
  slotMsExact, swungOnsetMsExact,
} from "./percussion";

/** Accented hits play this much louder (mirrors the live scheduler). */
export const ACCENT_GAIN = 1.4;

/** A voice whose peak lands later than this is treated as a crescendo and started
 *  early, so its PEAK — not its onset — sits on the beat (mirrors the scheduler). */
const CRESCENDO_PEAK_SEC = 0.02;

/** Self-choke fade: a new strike ramps the previous one on the same track to silence
 *  over this long, starting at the new hit's onset (engine.playSamples). */
const CHOKE_FADE_SEC = 0.012;

/** Ceiling the mix is scaled to when it would otherwise clip the 16-bit file. */
const CEILING = 0.999;

/** One mono one-shot per (instrument id, voice index), already at the render rate.
 *  Return null for "no sound available" — that voice is then skipped. */
export type VoiceBuffers = (instrumentId: string, voice: number) => Float32Array | null;

export interface PercussionRenderSpec {
  pattern: PercussionPattern;
  bpm: number;
  swing?: number;
  swingModel?: SwingModel;
  /** null/undefined renders the whole kit ("full cycle"); an instrument id renders
   *  that track ALONE (its own volume and swing still apply). */
  onlyTrack?: string | null;
  /** The live mixer's mute/solo state, applied to a FULL-kit render only. A single-track
   *  export ignores it on purpose: you asked for that stem, so a muted track still
   *  renders rather than producing a silent file. */
  includeTrack?: (id: string) => boolean;
  cycles?: number;
  /** true = the file is EXACTLY `cycles` cycles long and a hit's ring-out past the end
   *  wraps around to the start, which is precisely what you hear when the loop repeats
   *  — so it loops seamlessly in a DAW. false = the final ring-out is appended instead,
   *  giving a clean one-shot ending. */
  loopExact?: boolean;
  sampleRate?: number;
  /** Extra per-voice gain from the live mixer (voice volume sliders). */
  voiceGain?: (id: string, voice: number) => number;
}

export interface PercussionRenderResult {
  samples: Float32Array;
  sampleRate: number;
  durationSec: number;
  /** Hits actually mixed in (a voice with no buffer is skipped, not counted). */
  hits: number;
  /** Voices that had no buffer at all — surfaced instead of silently dropped. */
  missingVoices: string[];
  /** Peak BEFORE any safety scaling; > 1 means the mix was scaled down to fit. */
  peak: number;
  /** Safety gain applied to keep the file from clipping (1 = untouched). */
  safetyGain: number;
}

/** One scheduled strike, resolved to sample positions. */
interface Hit {
  start: number;
  buffer: Float32Array;
  gain: number;
  /** Sample index at which the next strike on this track chokes it (or -1). */
  chokeAt: number;
}

export function renderPercussion(spec: PercussionRenderSpec, buffers: VoiceBuffers): PercussionRenderResult {
  const { pattern, bpm } = spec;
  const swing = spec.swing ?? 0;
  const model = spec.swingModel ?? SwingModel.Default;
  const cycles = Math.max(spec.cycles ?? 1, 1);
  const loopExact = spec.loopExact ?? true;
  const sr = spec.sampleRate ?? 44100;
  const voiceGain = spec.voiceGain ?? (() => 1);
  const includeTrack = spec.includeTrack ?? (() => true);
  const meter: PercussionMeter = pattern.meter;
  const slots = pattern.slots;

  // Anchored so slot 0 sits at sample 0: the swing model gives the downbeat its own
  // offset, and the live scheduler starts the loop AT slot 0 rather than at that offset.
  // A file whose first hit was ~20 ms in would never line up on a bar line.
  const slotZeroMs = swungOnsetMsExact(0, bpm, swing, meter, model);
  const slotOnsetMs: number[] = [];
  for (let k = 0; k < slots; k++) slotOnsetMs.push(swungOnsetMsExact(k, bpm, swing, meter, model) - slotZeroMs);
  const cycleMs = slots * slotMsExact(bpm, meter.division);

  const tracks = pattern.instruments.filter((i) =>
    spec.onlyTrack != null ? i.id === spec.onlyTrack : includeTrack(i.id));
  const missing = new Set<string>();
  const perTrack: Hit[][] = [];
  let hitCount = 0;

  for (const inst of tracks) {
    const hits: Hit[] = [];
    const trackDelta = trackOnsetDeltas(pattern, inst.id, bpm, swing, model);
    const trackVol = pattern.trackVolumeOf(inst.id) / 100;
    for (let cycle = 0; cycle < cycles; cycle++) {
      for (let slot = 0; slot < slots; slot++) {
        const voice = pattern.voiceAt(inst, slot);
        if (voice === null) continue;
        const buf = buffers(inst.id, voice);
        if (!buf || buf.length === 0) { missing.add(`${inst.id}:${voice}`); continue; }
        const gain = trackVol * voiceGain(inst.id, voice) *
          (pattern.isAccented(inst, slot) ? ACCENT_GAIN : 1) *
          PERCUSSION_DYN_FACTORS[pattern.dynLevelAt(inst, slot)];
        const onsetMs = cycle * cycleMs + slotOnsetMs[slot] + trackDelta[slot];
        let start = msToSamples(onsetMs, sr);
        // Crescendo voices start early so their PEAK lands on the beat.
        const peakOffset = peakOffsetSamples(buf);
        if (peakOffset > CRESCENDO_PEAK_SEC * sr) start -= Math.min(peakOffset, start);
        hits.push({ start, buffer: buf, gain, chokeAt: -1 });
        hitCount++;
      }
    }
    hits.sort((a, b) => a.start - b.start);
    // A self-choking track (pandeiro) damps its previous stroke at each new hit.
    if (inst.selfChoke) for (let i = 0; i < hits.length - 1; i++) hits[i].chokeAt = hits[i + 1].start;
    perTrack.push(hits);
  }

  const cycleSamples = msToSamples(cycleMs * cycles, sr);
  let maxEnd = cycleSamples;
  for (const hits of perTrack) for (const h of hits) maxEnd = Math.max(maxEnd, h.start + h.buffer.length);
  const length = Math.max(loopExact ? cycleSamples : maxEnd, 1);
  const out = new Float32Array(length);

  for (const hits of perTrack) for (const h of hits) writeHit(out, h, sr, loopExact);

  let peak = 0;
  for (const v of out) peak = Math.max(peak, Math.abs(v));
  let safetyGain = 1;
  if (peak > CEILING) {
    safetyGain = CEILING / peak;
    for (let i = 0; i < out.length; i++) out[i] *= safetyGain;
  }
  return {
    samples: out, sampleRate: sr, durationSec: out.length / sr,
    hits: hitCount, missingVoices: [...missing], peak, safetyGain,
  };
}

/**
 * Per-TRACK swing offsets (ms) for every slot. A track with its own swing walks its own
 * micro-timing clock, but ONLY while the beat's global swing is 0 (a nonzero global
 * swing overrides all track values) — the same rule the live scheduler uses. Both clocks
 * are measured from their OWN slot 0, so the grids start together.
 */
function trackOnsetDeltas(
  pattern: PercussionPattern, id: string, bpm: number, swing: number, model: SwingModel,
): number[] {
  const deltas = new Array<number>(pattern.slots).fill(0);
  const trackSwing = pattern.trackSwing.get(id) ?? 0;
  if (swing !== 0 || trackSwing === 0) return deltas;
  const onset = (k: number, sw: number) =>
    swungOnsetMsExact(k, bpm, sw, pattern.meter, model) - swungOnsetMsExact(0, bpm, sw, pattern.meter, model);
  for (let k = 0; k < pattern.slots; k++) deltas[k] = onset(k, trackSwing) - onset(k, swing);
  return deltas;
}

/** Mix one strike into `out`, applying its choke fade and (optionally) wrapping any
 *  ring-out past the end back to the start so the file loops seamlessly. */
function writeHit(out: Float32Array, hit: Hit, sampleRate: number, wrap: boolean): void {
  const fade = Math.max(Math.round(CHOKE_FADE_SEC * sampleRate), 1);
  for (let i = 0; i < hit.buffer.length; i++) {
    const at = hit.start + i;
    if (!wrap && at >= out.length) break;
    let env = hit.gain;
    if (hit.chokeAt >= 0) {
      const since = at - hit.chokeAt;
      if (since >= fade) break;                        // fully damped — nothing left to write
      if (since >= 0) env *= 1 - since / fade;
    }
    const idx = wrap ? ((at % out.length) + out.length) % out.length : at;
    out[idx] += hit.buffer[i] * env;
  }
}

/** Sample offset of the buffer's first near-peak sample (crescendo detection);
 *  mirrors SambaLooperState.peakOffsetSec. */
function peakOffsetSamples(buf: Float32Array): number {
  let peak = 0;
  for (const v of buf) peak = Math.max(peak, Math.abs(v));
  if (peak <= 0) return 0;
  const threshold = peak * 0.9;
  let i = 0;
  while (i < buf.length && Math.abs(buf[i]) < threshold) i++;
  return i;
}

function msToSamples(ms: number, sampleRate: number): number {
  return Math.round((ms * sampleRate) / 1000);
}

/**
 * Encode mono `samples` (nominally in [-1, 1]) as a 16-bit PCM WAV file.
 *
 * WAV (not MP3) on purpose: a canonical RIFF header plus raw samples needs no encoder
 * dependency, it is lossless, and every DAW and OS player opens it. An MP3 export would
 * mean shipping a WASM encoder for a file that is a few seconds long.
 *
 * Values outside [-1, 1] are clamped rather than allowed to wrap, so a hot mix distorts
 * gracefully instead of producing the loud crackle of integer overflow.
 */
export function encodeWavMono16(samples: Float32Array, sampleRate: number): Uint8Array {
  const bytesPerSample = 2;
  const dataBytes = samples.length * bytesPerSample;
  const out = new Uint8Array(44 + dataBytes);
  const view = new DataView(out.buffer);
  let p = 0;
  const ascii = (s: string) => { for (const c of s) out[p++] = c.charCodeAt(0); };
  const le32 = (v: number) => { view.setUint32(p, v, true); p += 4; };
  const le16 = (v: number) => { view.setUint16(p, v, true); p += 2; };

  ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE");
  ascii("fmt "); le32(16);
  le16(1);                                   // PCM, uncompressed
  le16(1);                                   // mono
  le32(sampleRate);
  le32(sampleRate * bytesPerSample);         // byte rate
  le16(bytesPerSample);                      // block align
  le16(16);                                  // bits per sample
  ascii("data"); le32(dataBytes);

  for (const s of samples) {
    const clamped = Math.min(Math.max(s, -1), 1);
    // 32767 (not 32768) so +1.0 and -1.0 are both representable without wrapping.
    view.setInt16(p, Math.round(clamped * 32767), true);
    p += 2;
  }
  return out;
}

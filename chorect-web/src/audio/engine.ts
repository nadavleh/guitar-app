// Web Audio output engine. The analogue of audio/.../AudioTrackEngine.kt: it
// renders Karplus-Strong buffers (PluckedSynth) and plays them through the Web
// Audio API. Where the Kotlin engine runs a continuous MODE_STREAM mixer thread,
// the browser already mixes any number of concurrent AudioBufferSourceNodes for
// us, so each note/chord is just a fire-and-forget buffer source.
//
// This is an A/B facade with two independent output chains, selected at
// runtime via `useModern`/`setUseModern`:
//   - LEGACY: the original behavior — buffer -> legacyMaster(0.9) -> destination,
//     chords pre-mixed by `synth.synthesizeChord`, hard-stop on `stop()`. Kept
//     byte-for-byte identical to the pre-overhaul engine.
//   - MODERN: mirrors the Kotlin overhaul (AudioTrackEngine + VoiceMixer) —
//     one voice per note with attack declick, constant-power pan, a shared
//     reverb send/return bus, and a limiter on the master bus. `stop()` fades
//     (release) modern voices instead of hard-cutting them.

import { PluckedSynth } from "./pluckedSynth";
import { Timbre, Timbres } from "./timbre";
import { panForMidi } from "./panner";
import { buildReverbIR } from "./reverbIR";
import { SampleBank, nearestRoot, pitchRate } from "./sampleInstrument";

/** A voice tracked for `stop()`. `env` is present only for MODERN note/chord
 *  voices (which get a release ramp on stop); legacy voices and MODERN drum
 *  voices (dry, no envelope) are hard-stopped. */
interface ActiveVoice {
  src: AudioBufferSourceNode;
  env?: GainNode;
  /** Release time (ms) for the stop() ramp — set for MODERN voices only,
   *  mirrors the owning Timbre's releaseMs. */
  releaseMs?: number;
}

export class WebAudioEngine {
  private ctx: AudioContext | null = null;
  private synth: PluckedSynth | null = null;

  // LEGACY chain.
  private legacyMaster: GainNode | null = null;

  // MODERN chain: reverbBus -> reverb -> modernMaster -> modernLimiter -> destination.
  // Dry voices (and the dry side of wet voices) connect directly to modernMaster.
  private modernMaster: GainNode | null = null;
  private modernLimiter: DynamicsCompressorNode | null = null;
  private reverb: ConvolverNode | null = null;
  private reverbBus: GainNode | null = null;

  // Per-instrument runtime EQ, inserted modernMaster -> eqLow -> eqMid -> eqHigh
  // -> modernLimiter (reverb still feeds modernMaster, so it passes through the
  // EQ too — mirrors the Kotlin AudioTrackEngine chain).
  private eqLow: BiquadFilterNode | null = null;
  private eqMid: BiquadFilterNode | null = null;
  private eqHigh: BiquadFilterNode | null = null;

  private active = new Set<ActiveVoice>();
  // Default to the MODERN (overhaul) engine, matching Android (AppState.useModernAudio = true).
  private _useModern = true;

  // Currently selected sampled-guitar bank (MODERN chain only); null = synth voices.
  private currentBank: SampleBank | null = null;

  // Per-sound reverb send (0..1) for MODERN guitar voices; set per selected Sound.
  private voiceReverbSend = 0.03;

  /** Set the per-voice reverb send amount (0..1) for subsequently-played modern voices. */
  setReverbSend(amount: number): void {
    this.voiceReverbSend = Math.max(0, Math.min(1, amount));
  }

  /** Select (or clear, with null) the sampled bank used by MODERN note/chord voices. */
  setInstrument(b: SampleBank | null): void {
    this.currentBank = b;
  }

  /** Fetch + decode a sampled-guitar bank: `guitar/<inst>.json` lists recorded
   *  root MIDI notes; `guitar/<inst>_<midi>.wav` is each root's recording.
   *  Paths are prefixed with Vite's BASE_URL (mirrors `loadDrumSample`) so this
   *  resolves under the GitHub Pages subpath deploy, not just at `/`. Rejects
   *  if the manifest or any sample is missing — the caller (AppState.setSound)
   *  catches this and falls back to synth voices, which is expected until the
   *  sample assets (Task 1) ship. */
  async loadBank(inst: string): Promise<SampleBank> {
    const base = import.meta.env.BASE_URL;
    const manifestRes = await fetch(`${base}guitar/${inst}.json`);
    if (!manifestRes.ok) throw new Error(`sample bank manifest missing: ${inst}`);
    const roots: number[] = await manifestRes.json();
    if (!Array.isArray(roots) || roots.length === 0) throw new Error(`sample bank manifest empty/invalid: ${inst}`);
    const buffers = new Map<number, AudioBuffer>();
    const ctx = this.ensure();
    await Promise.all(
      roots.map(async (m) => {
        const res = await fetch(`${base}guitar/${inst}_${m}.wav`);
        if (!res.ok) throw new Error(`sample missing: ${inst}_${m}`);
        const bytes = await res.arrayBuffer();
        buffers.set(m, await ctx.decodeAudioData(bytes));
      }),
    );
    return { id: inst, roots, buffers };
  }

  /** Whether the MODERN (overhaul) output chain is currently selected. */
  get useModern(): boolean {
    return this._useModern;
  }

  /** Switch chains. Stops everything first (mirrors the Kotlin
   *  SwitchableAudioEngine) so nothing rings across the switch. */
  setUseModern(v: boolean): void {
    if (v === this._useModern) return;
    this.stop();
    this._useModern = v;
  }

  /** Lazily create + resume the AudioContext. Must be called from a user gesture
   *  the first time (browser autoplay policy), which our click/tap handlers satisfy. */
  private ensure(): AudioContext {
    if (!this.ctx) {
      const Ctor = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.ctx = new Ctor();
      this.synth = new PluckedSynth(this.ctx.sampleRate);

      this.legacyMaster = this.ctx.createGain();
      this.legacyMaster.gain.value = 0.9;
      this.legacyMaster.connect(this.ctx.destination);

      // Brickwall-ish limiter guarding the modern master bus.
      this.modernLimiter = this.ctx.createDynamicsCompressor();
      this.modernLimiter.threshold.value = -1;
      this.modernLimiter.knee.value = 0;
      this.modernLimiter.ratio.value = 20;
      this.modernLimiter.attack.value = 0.003;
      this.modernLimiter.release.value = 0.08;
      this.modernLimiter.connect(this.ctx.destination);

      this.modernMaster = this.ctx.createGain();
      this.modernMaster.gain.value = 1.0;

      this.eqLow = this.ctx.createBiquadFilter();
      this.eqLow.type = "lowshelf";
      this.eqLow.frequency.value = 120;
      this.eqMid = this.ctx.createBiquadFilter();
      this.eqMid.type = "peaking";
      this.eqMid.frequency.value = 700;
      this.eqMid.Q.value = 0.9;
      this.eqHigh = this.ctx.createBiquadFilter();
      this.eqHigh.type = "highshelf";
      this.eqHigh.frequency.value = 3500;
      this.modernMaster.connect(this.eqLow);
      this.eqLow.connect(this.eqMid);
      this.eqMid.connect(this.eqHigh);
      this.eqHigh.connect(this.modernLimiter);

      this.reverb = this.ctx.createConvolver();
      this.reverb.buffer = buildReverbIR(this.ctx);
      this.reverb.connect(this.modernMaster);

      this.reverbBus = this.ctx.createGain();
      this.reverbBus.gain.value = 1.0;
      this.reverbBus.connect(this.reverb);
    }
    if (this.ctx.state === "suspended") void this.ctx.resume();
    return this.ctx;
  }

  /** The live AudioContext (creating it if needed) — used by the tuner's mic input. */
  context(): AudioContext {
    return this.ensure();
  }

  /** Set the modern-chain EQ (dB gain, ±12 typical): bass = low-shelf @120Hz,
   *  mid = peaking @700Hz Q0.9, treble = high-shelf @3500Hz. Applies to
   *  everything on modernMaster, including reverb. Mirrors AppState.EqSettings
   *  on Android. */
  setEq(bass: number, mid: number, treble: number): void {
    if (!this.eqLow) this.ensure();
    this.eqLow!.gain.value = bass;
    this.eqMid!.gain.value = mid;
    this.eqHigh!.gain.value = treble;
  }

  /** Decode an encoded audio file (WAV/MP3/…) to a mono Float32Array at the engine's
   *  sample rate — decodeAudioData resamples for us. Used to load drum-voice samples. */
  async decodeSample(bytes: ArrayBuffer): Promise<Float32Array> {
    const ctx = this.ensure();
    const audioBuf = await ctx.decodeAudioData(bytes);
    const channels = audioBuf.numberOfChannels;
    const n = audioBuf.length;
    const out = new Float32Array(n);
    for (let c = 0; c < channels; c++) {
      const data = audioBuf.getChannelData(c);
      for (let i = 0; i < n; i++) out[i] += data[i];
    }
    if (channels > 1) for (let i = 0; i < n; i++) out[i] /= channels;
    return out;
  }

  /** LEGACY: one-shot buffer -> legacyMaster. Unchanged from the pre-overhaul engine. */
  private playLegacy(samples: Float32Array): void {
    if (samples.length === 0) return;
    const ctx = this.ensure();
    const buffer = ctx.createBuffer(1, samples.length, ctx.sampleRate);
    buffer.getChannelData(0).set(samples);
    const src = ctx.createBufferSource();
    src.buffer = buffer;
    src.connect(this.legacyMaster!);
    const entry: ActiveVoice = { src };
    src.onended = () => {
      this.active.delete(entry);
      src.disconnect();
    };
    this.active.add(entry);
    src.start();
  }

  /** MODERN: one voice = bufferSource -> envGain (attack declick) -> panner ->
   *  modernMaster (dry) AND panner -> reverbSend -> reverbBus (wet).
   *  `startAt` (AudioContext seconds) defaults to "now" — used by playChord to
   *  stagger strum voices. */
  private playModernVoice(samples: Float32Array, pan: number, reverbSend: number, level: number, releaseMs: number, startAt?: number): void {
    if (samples.length === 0) return;
    const ctx = this.ensure();
    const startT = startAt ?? ctx.currentTime;
    const buffer = ctx.createBuffer(1, samples.length, ctx.sampleRate);
    buffer.getChannelData(0).set(samples);

    const src = ctx.createBufferSource();
    src.buffer = buffer;

    const env = ctx.createGain();
    env.gain.setValueAtTime(0, startT);
    env.gain.linearRampToValueAtTime(level, startT + 0.003);

    const panner = ctx.createStereoPanner();
    panner.pan.value = pan;

    const send = ctx.createGain();
    send.gain.value = reverbSend;

    src.connect(env);
    env.connect(panner);
    panner.connect(this.modernMaster!);
    panner.connect(send);
    send.connect(this.reverbBus!);

    const entry: ActiveVoice = { src, env, releaseMs };
    src.onended = () => {
      this.active.delete(entry);
      src.disconnect();
      env.disconnect();
      panner.disconnect();
      send.disconnect();
    };
    this.active.add(entry);
    src.start(startT);
  }

  /** MODERN, sampled variant of `playModernVoice`: identical envelope/panner/
   *  dry+reverb-send graph, but the source is a decoded sample buffer re-pitched
   *  via `playbackRate` instead of a synth-rendered buffer. */
  private playModernSampleVoice(
    buffer: AudioBuffer,
    rate: number,
    pan: number,
    reverbSend: number,
    level: number,
    releaseMs: number,
    startAt?: number,
  ): void {
    const ctx = this.ensure();
    const startT = startAt ?? ctx.currentTime;

    const src = ctx.createBufferSource();
    src.buffer = buffer;
    src.playbackRate.value = rate;

    const env = ctx.createGain();
    env.gain.setValueAtTime(0, startT);
    env.gain.linearRampToValueAtTime(level, startT + 0.003);

    const panner = ctx.createStereoPanner();
    panner.pan.value = pan;

    const send = ctx.createGain();
    send.gain.value = reverbSend;

    src.connect(env);
    env.connect(panner);
    panner.connect(this.modernMaster!);
    panner.connect(send);
    send.connect(this.reverbBus!);

    const entry: ActiveVoice = { src, env, releaseMs };
    src.onended = () => {
      this.active.delete(entry);
      src.disconnect();
      env.disconnect();
      panner.disconnect();
      send.disconnect();
    };
    this.active.add(entry);
    src.start(startT);
  }

  playNote(midiNote: number, durationMillis = 1500, timbre: Timbre = Timbres.Guitar): void {
    if (midiNote < 0 || midiNote > 127) return;
    const synth = this.ensureSynth();
    if (this._useModern) {
      if (this.currentBank) {
        const root = nearestRoot(this.currentBank.roots, midiNote);
        this.playModernSampleVoice(
          this.currentBank.buffers.get(root)!,
          pitchRate(midiNote, root),
          panForMidi(midiNote),
          this.voiceReverbSend,
          // Samples ignore the synth's amplitude param, so map it to voice level
          // (0.6 = the Timbre default = unity) — keeps per-timbre level differences
          // (e.g. the ear-training root boost) audible on samples.
          timbre.amplitude / 0.6,
          timbre.releaseMs,
        );
        return;
      }
      const samples = synth.synthesize(midiNote, durationMillis / 1000, 1, timbre.damping, timbre.amplitude, 0.6);
      this.playModernVoice(samples, panForMidi(midiNote), this.voiceReverbSend, 1.0, timbre.releaseMs);
    } else {
      const samples = synth.synthesize(midiNote, durationMillis / 1000, 1, timbre.damping, timbre.amplitude);
      this.playLegacy(samples);
    }
  }

  playFrequency(freqHz: number, durationMillis = 1500, timbre: Timbre = Timbres.Guitar): void {
    if (freqHz <= 0) return;
    const synth = this.ensureSynth();
    if (this._useModern) {
      if (this.currentBank) {
        const midiNote = Math.round(69 + 12 * Math.log2(freqHz / 440));
        const root = nearestRoot(this.currentBank.roots, midiNote);
        this.playModernSampleVoice(
          this.currentBank.buffers.get(root)!,
          pitchRate(midiNote, root),
          0,
          this.voiceReverbSend,
          timbre.amplitude / 0.6, // amplitude → level (0.6 = unity); see playNote
          timbre.releaseMs,
        );
        return;
      }
      const samples = synth.synthesizeFrequency(freqHz, durationMillis / 1000, 1, timbre.damping, timbre.amplitude, 0.6);
      this.playModernVoice(samples, 0, this.voiceReverbSend, 1.0, timbre.releaseMs);
    } else {
      const samples = synth.synthesizeFrequency(freqHz, durationMillis / 1000, 1, timbre.damping, timbre.amplitude);
      this.playLegacy(samples);
    }
  }

  // bassBoost (0 = off): the lowest-pitched note is scaled by (1 + bassBoost),
  // tapering linearly to no boost at the top note, so a voicing's low strings sit
  // fuller. Applied in the modern path (the default engine).
  playChord(midiNotes: number[], strumDelayMillis = 40, sustainMillis = 2000, timbre: Timbre = Timbres.Guitar, bassBoost = 0): void {
    const synth = this.ensureSynth();
    if (this._useModern) {
      const notes = midiNotes.filter((m) => m >= 0 && m <= 127);
      if (notes.length === 0) return;
      const ctx = this.ensure();
      const strumDelaySeconds = strumDelayMillis / 1000;
      const level = 1 / Math.sqrt(notes.length);
      const minMidi = Math.min(...notes), maxMidi = Math.max(...notes);
      const span = Math.max(1, maxMidi - minMidi);
      const startNow = ctx.currentTime;
      const seedBase = 1;
      const bank = this.currentBank;
      notes.forEach((midi, i) => {
        const startAt = startNow + i * strumDelaySeconds;
        const boost = 1 + bassBoost * ((maxMidi - midi) / span);
        if (bank) {
          const root = nearestRoot(bank.roots, midi);
          this.playModernSampleVoice(
            bank.buffers.get(root)!,
            pitchRate(midi, root),
            panForMidi(midi),
            this.voiceReverbSend,
            level * boost * (timbre.amplitude / 0.6), // amplitude → level (0.6 = unity); see playNote
            timbre.releaseMs,
            startAt,
          );
          return;
        }
        const samples = synth.synthesize(midi, sustainMillis / 1000, seedBase + i, timbre.damping, timbre.amplitude, 0.6);
        this.playModernVoice(samples, panForMidi(midi), this.voiceReverbSend, level * boost, timbre.releaseMs, startAt);
      });
    } else {
      const strumDelaySamples = Math.round((strumDelayMillis / 1000) * synth.sampleRate);
      const samples = synth.synthesizeChord(midiNotes, sustainMillis / 1000, strumDelaySamples, 1, timbre.damping, timbre.amplitude);
      this.playLegacy(samples);
    }
  }

  /** The AudioContext clock (seconds) — the timebase for [playSamples]' `when`. */
  now(): number {
    return this.ensure().currentTime;
  }

  /** Play a pre-rendered one-shot buffer (e.g. a percussion voice), scaled by [gain].
   *  [when] (AudioContext seconds, from [now]) schedules the start sample-accurately;
   *  omitted/past values start immediately. Always dry (no panner/reverb send) in
   *  both chains — keeps drums punchy — but in MODERN mode still passes through the
   *  limiter via modernMaster. */
  playSamples(samples: Float32Array, gain = 1, when = 0): void {
    if (samples.length === 0) return;
    const ctx = this.ensure();
    const buffer = ctx.createBuffer(1, samples.length, ctx.sampleRate);
    buffer.getChannelData(0).set(samples);
    const src = ctx.createBufferSource();
    src.buffer = buffer;
    const g = ctx.createGain();
    g.gain.value = gain;
    src.connect(g);
    g.connect(this._useModern ? this.modernMaster! : this.legacyMaster!);
    const entry: ActiveVoice = { src };
    src.onended = () => {
      this.active.delete(entry);
      src.disconnect();
      g.disconnect();
    };
    this.active.add(entry);
    src.start(when > ctx.currentTime ? when : 0);
  }

  /** Legacy voices (and MODERN drum voices) are hard-stopped. MODERN note/chord
   *  voices (which carry an envelope) instead get a short release ramp so the
   *  cutoff doesn't click — mirrors AudioTrackEngine.stop() -> VoiceMixer.releaseAll(). */
  stop(): void {
    const now = this.ctx ? this.ctx.currentTime : 0;
    for (const v of this.active) {
      if (v.env) {
        const rel = (v.releaseMs ?? 20) / 1000;
        try {
          const g = v.env.gain;
          const current = g.value;
          g.cancelScheduledValues(now);
          g.setValueAtTime(current, now);
          g.linearRampToValueAtTime(0, now + rel);
        } catch {
          /* already stopped */
        }
        try {
          v.src.stop(now + rel + 0.005);
        } catch {
          /* already stopped */
        }
      } else {
        try {
          v.src.stop();
        } catch {
          /* already stopped */
        }
        v.src.disconnect();
      }
    }
    this.active.clear();
  }

  private ensureSynth(): PluckedSynth {
    this.ensure();
    return this.synth!;
  }
}

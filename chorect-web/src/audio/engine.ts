// Web Audio output engine. The analogue of audio/.../AudioTrackEngine.kt: it
// renders Karplus-Strong buffers (PluckedSynth) and plays them through the Web
// Audio API. Where the Kotlin engine runs a continuous MODE_STREAM mixer thread,
// the browser already mixes any number of concurrent AudioBufferSourceNodes for
// us, so each note/chord is just a fire-and-forget buffer source.

import { PluckedSynth } from "./pluckedSynth";
import { Timbre, Timbres } from "./timbre";

export class WebAudioEngine {
  private ctx: AudioContext | null = null;
  private synth: PluckedSynth | null = null;
  private master: GainNode | null = null;
  private active = new Set<AudioBufferSourceNode>();

  /** Lazily create + resume the AudioContext. Must be called from a user gesture
   *  the first time (browser autoplay policy), which our click/tap handlers satisfy. */
  private ensure(): AudioContext {
    if (!this.ctx) {
      const Ctor = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      this.ctx = new Ctor();
      this.synth = new PluckedSynth(this.ctx.sampleRate);
      this.master = this.ctx.createGain();
      this.master.gain.value = 0.9;
      this.master.connect(this.ctx.destination);
    }
    if (this.ctx.state === "suspended") void this.ctx.resume();
    return this.ctx;
  }

  /** The live AudioContext (creating it if needed) — used by the tuner's mic input. */
  context(): AudioContext {
    return this.ensure();
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

  private play(samples: Float32Array): void {
    if (samples.length === 0) return;
    const ctx = this.ensure();
    const buffer = ctx.createBuffer(1, samples.length, ctx.sampleRate);
    buffer.getChannelData(0).set(samples);
    const src = ctx.createBufferSource();
    src.buffer = buffer;
    src.connect(this.master!);
    src.onended = () => {
      this.active.delete(src);
      src.disconnect();
    };
    this.active.add(src);
    src.start();
  }

  playNote(midiNote: number, durationMillis = 1500, timbre: Timbre = Timbres.Guitar): void {
    if (midiNote < 0 || midiNote > 127) return;
    const samples = this.ensureSynth().synthesize(midiNote, durationMillis / 1000, 1, timbre.damping, timbre.amplitude);
    this.play(samples);
  }

  playFrequency(freqHz: number, durationMillis = 1500, timbre: Timbre = Timbres.Guitar): void {
    if (freqHz <= 0) return;
    const samples = this.ensureSynth().synthesizeFrequency(freqHz, durationMillis / 1000, 1, timbre.damping, timbre.amplitude);
    this.play(samples);
  }

  playChord(midiNotes: number[], strumDelayMillis = 40, sustainMillis = 2000, timbre: Timbre = Timbres.Guitar): void {
    const synth = this.ensureSynth();
    const strumDelaySamples = Math.round((strumDelayMillis / 1000) * synth.sampleRate);
    const samples = synth.synthesizeChord(midiNotes, sustainMillis / 1000, strumDelaySamples, 1, timbre.damping, timbre.amplitude);
    this.play(samples);
  }

  /** Play a pre-rendered one-shot buffer (e.g. a percussion voice), scaled by [gain]. */
  playSamples(samples: Float32Array, gain = 1): void {
    if (samples.length === 0) return;
    const ctx = this.ensure();
    const buffer = ctx.createBuffer(1, samples.length, ctx.sampleRate);
    buffer.getChannelData(0).set(samples);
    const src = ctx.createBufferSource();
    src.buffer = buffer;
    const g = ctx.createGain();
    g.gain.value = gain;
    src.connect(g);
    g.connect(this.master!);
    src.onended = () => {
      this.active.delete(src);
      src.disconnect();
      g.disconnect();
    };
    this.active.add(src);
    src.start();
  }

  stop(): void {
    for (const src of this.active) {
      try {
        src.stop();
      } catch {
        /* already stopped */
      }
      src.disconnect();
    }
    this.active.clear();
  }

  private ensureSynth(): PluckedSynth {
    this.ensure();
    return this.synth!;
  }
}

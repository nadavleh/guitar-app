// Optional drum-voice sample loading. Mirrors the Kotlin app's asset loader: for each
// (instrument, voice) it tries `/drums/<instrument>_<voice>.wav`; a missing or
// undecodable file just returns null, so the caller falls back to the synth.
//
// Drop your own recorded or properly-licensed royalty-free WAVs into `public/drums/`
// — e.g. `pandeiro_3.wav` / `pandeiro_4.wav` for the platinela jingles.

import { PercussionInstrument } from "../theory";
import { WebAudioEngine } from "../audio";

export async function loadDrumSample(engine: WebAudioEngine, inst: PercussionInstrument, voice: number): Promise<Float32Array | null> {
  const name = `${inst.toLowerCase()}_${voice}.wav`;
  const url = `${import.meta.env.BASE_URL}drums/${name}`;
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    const bytes = await res.arrayBuffer();
    return await engine.decodeSample(bytes);
  } catch {
    return null;
  }
}

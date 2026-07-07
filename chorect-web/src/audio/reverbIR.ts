// Synthetic reverb impulse response — a lightweight stand-in for the Freeverb
// algorithmic reverb ported to the Kotlin audio engine (see Freeverb.kt). Instead
// of porting the full Freeverb comb/allpass network to WebAudio, we build a
// synthetic exponentially-decaying stereo noise IR and feed it through a
// ConvolverNode, which gets a similarly subtle, diffuse "room" character without
// an extra asset to bundle or a hand-rolled comb-filter network in JS.

/** Synthetic exponentially-decaying stereo IR for a subtle room; mirrors the Freeverb sound without an asset. */
export function buildReverbIR(ctx: BaseAudioContext, seconds = 1.3): AudioBuffer {
  const len = Math.max(Math.floor(ctx.sampleRate * seconds), 1);
  const buffer = ctx.createBuffer(2, len, ctx.sampleRate);
  for (let ch = 0; ch < 2; ch++) {
    const d = buffer.getChannelData(ch);
    for (let i = 0; i < len; i++) {
      d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 2.5);
    }
  }
  return buffer;
}

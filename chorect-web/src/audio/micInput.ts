// Microphone capture for the tuner. The analogue of audio/.../MicInput.kt
// (AudioRecord). Uses getUserMedia + a ScriptProcessorNode to deliver fixed-size
// mono sample windows to a callback, which the tuner feeds to the YIN detector.

export class MicInput {
  private stream: MediaStream | null = null;
  private source: MediaStreamAudioSourceNode | null = null;
  private processor: ScriptProcessorNode | null = null;
  private sink: GainNode | null = null;

  constructor(private readonly ctx: AudioContext, private readonly bufferSize = 2048) {}

  /** Begin capture, invoking [onSamples] with each mono window. Resolves false on
   *  permission denial or any error. */
  async start(onSamples: (samples: Float32Array) => void): Promise<boolean> {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false },
      });
      if (this.ctx.state === "suspended") await this.ctx.resume();
      this.source = this.ctx.createMediaStreamSource(this.stream);
      this.processor = this.ctx.createScriptProcessor(this.bufferSize, 1, 1);
      this.processor.onaudioprocess = (e) => {
        // Copy: the underlying buffer is reused by the audio thread.
        onSamples(new Float32Array(e.inputBuffer.getChannelData(0)));
      };
      // A muted sink keeps the processor pulling without echoing the mic to output.
      this.sink = this.ctx.createGain();
      this.sink.gain.value = 0;
      this.source.connect(this.processor);
      this.processor.connect(this.sink);
      this.sink.connect(this.ctx.destination);
      return true;
    } catch {
      this.stop();
      return false;
    }
  }

  stop(): void {
    if (this.processor) {
      this.processor.onaudioprocess = null;
      this.processor.disconnect();
      this.processor = null;
    }
    if (this.source) {
      this.source.disconnect();
      this.source = null;
    }
    if (this.sink) {
      this.sink.disconnect();
      this.sink = null;
    }
    if (this.stream) {
      for (const t of this.stream.getTracks()) t.stop();
      this.stream = null;
    }
  }
}

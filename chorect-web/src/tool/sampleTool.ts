// In-browser drum sample tool (dev utility, served at /tool.html).
//
// Load an audio file, scrub its waveform, audition any region (one-shot or looped),
// and export the selection as a trimmed mono 16-bit WAV named for a drum voice —
// then drop the file into public/drums/ and the app picks it up.

import "../style.css";

const VOICE_FILES = [
  "pandeiro_3.wav", "pandeiro_4.wav", "pandeiro_2.wav", "pandeiro_0.wav", "pandeiro_1.wav",
  "surdo_0.wav", "surdo_1.wav", "surdo_2.wav",
  "tamborim_0.wav", "tamborim_1.wav", "tamborim_2.wav",
  "agogo_0.wav", "agogo_1.wav",
];

let ctx: AudioContext | null = null;
let buffer: AudioBuffer | null = null;
let mono: Float32Array | null = null;
let sel: [number, number] | null = null; // sample indices
let source: AudioBufferSourceNode | null = null;

const root = document.getElementById("tool")!;
root.innerHTML = `
  <div style="max-width:980px;margin:0 auto;padding:20px;color:var(--text-primary);font-family:system-ui,sans-serif">
    <h1 style="color:var(--primary);font-size:22px;margin-bottom:4px">Chorect — drum sample tool</h1>
    <p style="color:var(--text-secondary);font-size:13px;line-height:1.5;margin-bottom:14px">
      Load an audio file, drag across the waveform to select a hit, press <b>Play ▶</b> (or loop it),
      then <b>Download</b> the selection as a one-shot WAV. Save it into <code>public/drums/</code> under the
      chosen name and reload the app. Keep selections short and tight on a single hit.
    </p>
    <input id="file" type="file" accept="audio/*" class="btn" />
    <div id="info" style="font-size:12px;color:var(--text-secondary);margin:10px 0"></div>
    <canvas id="wave" style="width:100%;height:170px;background:var(--surface);border:1px solid var(--divider);border-radius:8px;display:none;touch-action:none"></canvas>
    <div id="controls" style="display:none;margin-top:12px">
      <div class="row" style="gap:8px;flex-wrap:wrap;align-items:center">
        <button id="play" class="btn primary">Play ▶</button>
        <button id="playAll" class="btn">Play whole</button>
        <button id="stop" class="btn">Stop ⏹</button>
        <label style="display:flex;align-items:center;gap:6px;font-size:13px"><input id="loop" type="checkbox" /> loop</label>
        <span style="flex:1"></span>
        <button id="nudgeS" class="btn">◂ start</button>
        <button id="nudgeS2" class="btn">start ▸</button>
        <button id="nudgeE" class="btn">◂ end</button>
        <button id="nudgeE2" class="btn">end ▸</button>
      </div>
      <div id="seltext" style="font-size:13px;margin:10px 0;font-family:ui-monospace,monospace"></div>
      <div class="row" style="gap:8px;align-items:center;flex-wrap:wrap">
        <span style="font-size:13px">Save as</span>
        <select id="fname" class="et-select" style="width:auto"></select>
        <button id="dl" class="btn primary">Download WAV</button>
      </div>
    </div>
  </div>
`;

const fileInput = root.querySelector<HTMLInputElement>("#file")!;
const info = root.querySelector<HTMLDivElement>("#info")!;
const canvas = root.querySelector<HTMLCanvasElement>("#wave")!;
const controls = root.querySelector<HTMLDivElement>("#controls")!;
const selText = root.querySelector<HTMLDivElement>("#seltext")!;
const loopCb = root.querySelector<HTMLInputElement>("#loop")!;
const fnameSel = root.querySelector<HTMLSelectElement>("#fname")!;
for (const f of VOICE_FILES) { const o = document.createElement("option"); o.value = f; o.textContent = f; fnameSel.appendChild(o); }

fileInput.addEventListener("change", async () => {
  const file = fileInput.files?.[0];
  if (!file) return;
  ctx = ctx || new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)();
  await ctx.resume();
  const bytes = await file.arrayBuffer();
  buffer = await ctx.decodeAudioData(bytes);
  mono = toMono(buffer);
  sel = [0, Math.min(Math.floor(buffer.sampleRate * 0.2), mono.length)];
  info.textContent = `${file.name} · ${buffer.duration.toFixed(2)}s · ${buffer.sampleRate} Hz · ${buffer.numberOfChannels}ch`;
  canvas.style.display = "block";
  controls.style.display = "block";
  drawWave();
  updateSelText();
});

function toMono(ab: AudioBuffer): Float32Array {
  const n = ab.length, ch = ab.numberOfChannels, out = new Float32Array(n);
  for (let c = 0; c < ch; c++) { const d = ab.getChannelData(c); for (let i = 0; i < n; i++) out[i] += d[i]; }
  if (ch > 1) for (let i = 0; i < n; i++) out[i] /= ch;
  return out;
}

function drawWave(): void {
  if (!mono) return;
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth, h = canvas.clientHeight;
  canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
  const g = canvas.getContext("2d")!;
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.clearRect(0, 0, w, h);
  // selection band
  if (sel && buffer) {
    const x0 = (sel[0] / mono.length) * w, x1 = (sel[1] / mono.length) * w;
    g.fillStyle = "rgba(242,169,59,0.22)";
    g.fillRect(Math.min(x0, x1), 0, Math.abs(x1 - x0), h);
  }
  // envelope
  g.strokeStyle = "#9098A6";
  g.beginPath();
  const mid = h / 2, step = mono.length / w;
  for (let x = 0; x < w; x++) {
    let min = 1, max = -1;
    const s = Math.floor(x * step), e = Math.floor((x + 1) * step);
    for (let i = s; i < e; i++) { if (mono[i] < min) min = mono[i]; if (mono[i] > max) max = mono[i]; }
    g.moveTo(x, mid - max * mid); g.lineTo(x, mid - min * mid);
  }
  g.stroke();
}

// selection by drag
let dragging = false;
const xToSample = (clientX: number) => {
  const r = canvas.getBoundingClientRect();
  return Math.max(0, Math.min(mono!.length, Math.round(((clientX - r.left) / r.width) * mono!.length)));
};
canvas.addEventListener("pointerdown", (e) => { if (!mono) return; dragging = true; const s = xToSample(e.clientX); sel = [s, s]; canvas.setPointerCapture(e.pointerId); });
canvas.addEventListener("pointermove", (e) => { if (!dragging || !sel) return; sel = [sel[0], xToSample(e.clientX)]; drawWave(); updateSelText(); });
canvas.addEventListener("pointerup", () => { dragging = false; if (sel) sel = [Math.min(sel[0], sel[1]), Math.max(sel[0], sel[1])]; drawWave(); updateSelText(); });
window.addEventListener("resize", drawWave);

function selRange(): [number, number] { return sel ? [Math.min(sel[0], sel[1]), Math.max(sel[0], sel[1])] : [0, mono!.length]; }

function updateSelText(): void {
  if (!buffer) return;
  const [a, b] = selRange();
  const sr = buffer.sampleRate;
  selText.textContent = `selection: ${(a / sr).toFixed(3)}s → ${(b / sr).toFixed(3)}s   (${((b - a) / sr * 1000).toFixed(0)} ms)`;
}

function stop(): void { if (source) { try { source.stop(); } catch { /* */ } source.disconnect(); source = null; } }

function play(whole: boolean): void {
  if (!ctx || !buffer) return;
  stop();
  const [a, b] = whole ? [0, buffer.length] : selRange();
  source = ctx.createBufferSource();
  source.buffer = buffer;
  source.loop = loopCb.checked && !whole;
  const sr = buffer.sampleRate;
  if (source.loop) { source.loopStart = a / sr; source.loopEnd = b / sr; }
  source.connect(ctx.destination);
  source.start(0, a / sr, source.loop ? undefined : (b - a) / sr);
}

root.querySelector("#play")!.addEventListener("click", () => play(false));
root.querySelector("#playAll")!.addEventListener("click", () => play(true));
root.querySelector("#stop")!.addEventListener("click", stop);

const nudge = (which: 0 | 1, deltaMs: number) => {
  if (!sel || !buffer) return;
  const d = Math.round((deltaMs / 1000) * buffer.sampleRate);
  const r = selRange();
  r[which] = Math.max(0, Math.min(mono!.length, r[which] + d));
  sel = [Math.min(r[0], r[1]), Math.max(r[0], r[1])];
  drawWave(); updateSelText();
};
root.querySelector("#nudgeS")!.addEventListener("click", () => nudge(0, -5));
root.querySelector("#nudgeS2")!.addEventListener("click", () => nudge(0, 5));
root.querySelector("#nudgeE")!.addEventListener("click", () => nudge(1, -5));
root.querySelector("#nudgeE2")!.addEventListener("click", () => nudge(1, 5));

root.querySelector("#dl")!.addEventListener("click", () => {
  if (!mono || !buffer) return;
  const [a, b] = selRange();
  if (b - a < 2) return;
  const blob = exportRegion(mono, buffer.sampleRate, a, b);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url; link.download = fnameSel.value; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});

function exportRegion(src: Float32Array, sr: number, start: number, end: number): Blob {
  const slice = src.slice(start, end);
  const fi = Math.min(Math.floor(sr * 0.003), slice.length);
  const fo = Math.min(Math.floor(sr * 0.006), slice.length);
  for (let i = 0; i < fi; i++) slice[i] *= i / fi;
  for (let i = 0; i < fo; i++) slice[slice.length - 1 - i] *= i / fo;
  return encodeWav(slice, sr);
}

function encodeWav(samples: Float32Array, sampleRate: number): Blob {
  const n = samples.length;
  const buf = new ArrayBuffer(44 + n * 2);
  const dv = new DataView(buf);
  const wr = (o: number, s: string) => { for (let i = 0; i < s.length; i++) dv.setUint8(o + i, s.charCodeAt(i)); };
  wr(0, "RIFF"); dv.setUint32(4, 36 + n * 2, true); wr(8, "WAVE"); wr(12, "fmt ");
  dv.setUint32(16, 16, true); dv.setUint16(20, 1, true); dv.setUint16(22, 1, true);
  dv.setUint32(24, sampleRate, true); dv.setUint32(28, sampleRate * 2, true);
  dv.setUint16(32, 2, true); dv.setUint16(34, 16, true); wr(36, "data"); dv.setUint32(40, n * 2, true);
  let o = 44;
  for (let i = 0; i < n; i++) { const v = Math.max(-1, Math.min(1, samples[i])); dv.setInt16(o, v * 32767, true); o += 2; }
  return new Blob([buf], { type: "audio/wav" });
}

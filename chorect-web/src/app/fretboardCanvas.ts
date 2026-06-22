// Canvas fretboard renderer, ported from app/.../FretboardView.kt.
//
// The neck is drawn at a FIXED long-horizontal aspect ratio, letterboxed and
// centered inside the canvas viewport, then transformed by a pinch/drag zoom-pan.
// Tap hit-testing inverts that transform back into neck-local coordinates, exactly
// like the Compose version maps pointer coords back through its graphicsLayer.

import { Colors, withAlpha } from "./theme";
import { FretMark, MarkKind } from "./marks";
import { Tuning, FretPosition, fp, midiPitchClass, spellPc, stringCount } from "../theory";

const OPEN_COL_FRAC = 0.08;
const OPEN_MARK_FRAC = 0.7;
const NUT_FRAC = 0.022;
const STRING_DP = 42;
const FRET_NUMBER_DP = 18;
const TAP_SLOP = 6; // px of movement before a press becomes a drag, not a tap

export interface FretboardData {
  tuning: Tuning;
  marks: Map<string, FretMark>;
  selectedPosition: FretPosition | null;
  leftHanded: boolean;
  numFrets: number;
  playOnTouchDown: boolean;
  mutedStrings: Set<number>;
  onTap: (pos: FretPosition) => void;
}

export class FretboardCanvas {
  private ctx: CanvasRenderingContext2D;
  private data: FretboardData | null = null;

  // viewport (CSS px)
  private boxW = 0;
  private boxH = 0;
  // neck base size at scale 1 (CSS px)
  private neckW0 = 0;
  private neckH0 = 0;

  private scale = 1;
  private offsetX = 0;
  private offsetY = 0;
  private lastPortrait: boolean | null = null;

  // pointer tracking
  private pointers = new Map<number, { x: number; y: number }>();
  private pressStart: { x: number; y: number } | null = null;
  private dragged = false;
  private pinchDist = 0;
  private pinchCentroid = { x: 0, y: 0 };

  constructor(private canvas: HTMLCanvasElement) {
    this.ctx = canvas.getContext("2d")!;
    const ro = new ResizeObserver(() => this.resize());
    ro.observe(canvas);
    this.bindPointer();
    canvas.addEventListener("wheel", (e) => this.onWheel(e), { passive: false });
  }

  setData(data: FretboardData): void {
    const tuningChanged = !this.data || stringCount(this.data.tuning) !== stringCount(data.tuning);
    this.data = data;
    if (tuningChanged) this.fit(true);
    this.draw();
  }

  // ---------- sizing / framing ----------

  private resize(): void {
    const dpr = window.devicePixelRatio || 1;
    const w = this.canvas.clientWidth;
    const h = this.canvas.clientHeight;
    if (w === 0 || h === 0) return;
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    this.boxW = w;
    this.boxH = h;
    this.fit(false);
    this.draw();
  }

  private get maxScale(): number {
    if (!this.data) return 1.5;
    return Math.max(stringCount(this.data.tuning) / 2, 1.5);
  }
  private readonly minScale = 0.5;

  private neckAspect(): number {
    const d = this.data!;
    return (d.numFrets * 72 + 100) / (stringCount(d.tuning) * STRING_DP + FRET_NUMBER_DP);
  }

  /** Recompute the letterboxed neck base size and (optionally) reset the framing. */
  private fit(forceReset: boolean): void {
    if (!this.data || this.boxW === 0 || this.boxH === 0) return;
    const aspect = this.neckAspect();
    if (this.boxW / this.boxH > aspect) {
      this.neckH0 = this.boxH;
      this.neckW0 = this.boxH * aspect;
    } else {
      this.neckW0 = this.boxW;
      this.neckH0 = this.boxW / aspect;
    }
    const portrait = this.boxH > this.boxW;
    if (forceReset || this.lastPortrait === null || this.lastPortrait !== portrait) {
      const initialScale = portrait ? Math.min(this.maxScale, 2.2) : 1;
      this.scale = initialScale;
      this.offsetX = portrait ? (this.neckW0 * (initialScale - 1)) / 2 : 0;
      this.offsetY = 0;
      this.lastPortrait = portrait;
    }
    this.clampOffsets();
  }

  private clampOffsets(): void {
    const maxX = Math.max(0, (this.neckW0 * (this.scale - 1)) / 2);
    const maxY = Math.max(0, (this.neckH0 * (this.scale - 1)) / 2);
    this.offsetX = Math.min(Math.max(this.offsetX, -maxX), maxX);
    this.offsetY = Math.min(Math.max(this.offsetY, -maxY), maxY);
  }

  // ---------- transform between screen (CSS px) and neck-local coords ----------

  private neckOrigin(): { x0: number; y0: number } {
    return { x0: (this.boxW - this.neckW0) / 2, y0: (this.boxH - this.neckH0) / 2 };
  }

  private screenToNeck(px: number, py: number): { u: number; v: number } {
    const cx = this.boxW / 2;
    const cy = this.boxH / 2;
    const worldX = (px - (cx + this.offsetX)) / this.scale + cx;
    const worldY = (py - (cy + this.offsetY)) / this.scale + cy;
    const { x0, y0 } = this.neckOrigin();
    return { u: worldX - x0, v: worldY - y0 };
  }

  // ---------- pointer handling ----------

  private bindPointer(): void {
    const c = this.canvas;
    c.addEventListener("pointerdown", (e) => this.onPointerDown(e));
    c.addEventListener("pointermove", (e) => this.onPointerMove(e));
    c.addEventListener("pointerup", (e) => this.onPointerUp(e));
    c.addEventListener("pointercancel", (e) => this.onPointerUp(e));
  }

  private localXY(e: MouseEvent): { x: number; y: number } {
    const rect = this.canvas.getBoundingClientRect();
    return { x: e.clientX - rect.left, y: e.clientY - rect.top };
  }

  private onPointerDown(e: PointerEvent): void {
    this.canvas.setPointerCapture(e.pointerId);
    const p = this.localXY(e);
    this.pointers.set(e.pointerId, p);
    if (this.pointers.size === 1) {
      this.pressStart = p;
      this.dragged = false;
      if (this.data?.playOnTouchDown) {
        const pos = this.hit(p.x, p.y);
        if (pos) this.data.onTap(pos);
      }
    } else if (this.pointers.size === 2) {
      this.beginPinch();
    }
  }

  private onPointerMove(e: PointerEvent): void {
    if (!this.pointers.has(e.pointerId)) return;
    const p = this.localXY(e);
    const prev = this.pointers.get(e.pointerId)!;
    this.pointers.set(e.pointerId, p);

    if (this.pointers.size >= 2) {
      this.updatePinch();
      return;
    }
    // single pointer: pan once moved past the tap slop
    if (this.pressStart) {
      const dx0 = p.x - this.pressStart.x;
      const dy0 = p.y - this.pressStart.y;
      if (!this.dragged && Math.hypot(dx0, dy0) > TAP_SLOP) this.dragged = true;
      if (this.dragged) {
        this.offsetX += p.x - prev.x;
        this.offsetY += p.y - prev.y;
        this.clampOffsets();
        this.draw();
      }
    }
  }

  private onPointerUp(e: PointerEvent): void {
    const p = this.pointers.get(e.pointerId);
    this.pointers.delete(e.pointerId);
    try { this.canvas.releasePointerCapture(e.pointerId); } catch { /* ignore */ }

    if (this.pointers.size === 1) {
      // dropped from pinch to one finger — restart pan baseline, no tap
      this.beginPinch();
      this.pressStart = null;
      this.dragged = true;
      return;
    }
    if (this.pointers.size === 0) {
      const wasTap = !this.dragged && this.pressStart && p && !this.data?.playOnTouchDown;
      if (wasTap && this.data) {
        const pos = this.hit(p!.x, p!.y);
        if (pos) this.data.onTap(pos);
      }
      this.pressStart = null;
      this.dragged = false;
    }
  }

  private centroidOf(): { x: number; y: number } {
    let sx = 0, sy = 0;
    for (const p of this.pointers.values()) { sx += p.x; sy += p.y; }
    const n = this.pointers.size;
    return { x: sx / n, y: sy / n };
  }

  private distOf(): number {
    const pts = [...this.pointers.values()];
    if (pts.length < 2) return 0;
    return Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
  }

  private beginPinch(): void {
    this.pinchDist = this.distOf();
    this.pinchCentroid = this.centroidOf();
  }

  private updatePinch(): void {
    const dist = this.distOf();
    const centroid = this.centroidOf();
    const zoom = this.pinchDist > 0 ? dist / this.pinchDist : 1;
    const pan = { x: centroid.x - this.pinchCentroid.x, y: centroid.y - this.pinchCentroid.y };
    this.applyTransform(zoom, pan, centroid);
    this.pinchDist = dist;
    this.pinchCentroid = centroid;
  }

  private onWheel(e: WheelEvent): void {
    e.preventDefault();
    const p = this.localXY(e);
    const zoom = Math.exp(-e.deltaY * 0.0015);
    this.applyTransform(zoom, { x: 0, y: 0 }, p);
  }

  /** Shared zoom-around-focal-point math, matching FretboardView's detectTransformGestures. */
  private applyTransform(zoom: number, pan: { x: number; y: number }, centroid: { x: number; y: number }): void {
    const cx = this.boxW / 2;
    const cy = this.boxH / 2;
    const oldScale = this.scale;
    const newScale = Math.min(Math.max(oldScale * zoom, this.minScale), this.maxScale);
    this.scale = newScale;
    const maxX = Math.max(0, (this.neckW0 * (newScale - 1)) / 2);
    const maxY = Math.max(0, (this.neckH0 * (newScale - 1)) / 2);
    this.offsetX = Math.min(Math.max(this.offsetX + pan.x + (centroid.x - cx) * (oldScale - newScale), -maxX), maxX);
    this.offsetY = Math.min(Math.max(this.offsetY + pan.y + (centroid.y - cy) * (oldScale - newScale), -maxY), maxY);
    this.draw();
  }

  // ---------- neck-local geometry ----------

  private positionToPixel(pos: FretPosition, w: number, h: number, sc: number, numFrets: number, leftHanded: boolean): [number, number] {
    const openWidth = w * OPEN_COL_FRAC;
    const nutWidth = w * NUT_FRAC;
    const fretSpacing = (w - openWidth - nutWidth) / numFrets;
    const stringSpacing = h / sc;
    const firstStringY = stringSpacing / 2;
    const cxRight = pos.fret === 0 ? openWidth * OPEN_MARK_FRAC : openWidth + nutWidth + (pos.fret - 0.5) * fretSpacing;
    const cx = leftHanded ? w - cxRight : cxRight;
    const cy = firstStringY + (sc - 1 - pos.stringIndex) * stringSpacing;
    return [cx, cy];
  }

  private hit(px: number, py: number): FretPosition | null {
    if (!this.data) return null;
    const { u, v } = this.screenToNeck(px, py);
    const d = this.data;
    const sc = stringCount(d.tuning);
    const totalH = this.neckH0;
    const w = this.neckW0;
    const numberStripH = totalH * (FRET_NUMBER_DP / (sc * STRING_DP + FRET_NUMBER_DP));
    const h = totalH - numberStripH;
    if (v < 0 || v > h || u < 0 || u > w) return null;
    const openWidth = w * OPEN_COL_FRAC;
    const nutWidth = w * NUT_FRAC;
    const fretSpacing = (w - openWidth - nutWidth) / d.numFrets;
    const stringSpacing = h / sc;
    const rowFromTop = Math.min(Math.max(Math.floor(v / stringSpacing), 0), sc - 1);
    const s = sc - 1 - rowFromTop;
    const px2 = d.leftHanded ? w - u : u;
    let f: number;
    if (px2 < openWidth) {
      f = 0;
    } else if (px2 < openWidth + nutWidth) {
      return null;
    } else {
      const n = Math.floor((px2 - openWidth - nutWidth) / fretSpacing) + 1;
      if (n < 1 || n > d.numFrets) return null;
      f = n;
    }
    return fp(s, f);
  }

  // ---------- drawing ----------

  draw(): void {
    if (!this.data || this.boxW === 0) return;
    const ctx = this.ctx;
    const dpr = window.devicePixelRatio || 1;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, this.boxW, this.boxH);
    ctx.fillStyle = Colors.background;
    ctx.fillRect(0, 0, this.boxW, this.boxH);

    const cx = this.boxW / 2;
    const cy = this.boxH / 2;
    const { x0, y0 } = this.neckOrigin();
    ctx.save();
    ctx.translate(cx + this.offsetX, cy + this.offsetY);
    ctx.scale(this.scale, this.scale);
    ctx.translate(-cx, -cy);
    ctx.translate(x0, y0);
    this.drawNeck(ctx);
    ctx.restore();
  }

  private drawNeck(ctx: CanvasRenderingContext2D): void {
    const d = this.data!;
    const sc = stringCount(d.tuning);
    const numFrets = d.numFrets;
    const w = this.neckW0;
    const totalH = this.neckH0;
    const numberStripH = totalH * (FRET_NUMBER_DP / (sc * STRING_DP + FRET_NUMBER_DP));
    const h = totalH - numberStripH;

    const openWidth = w * OPEN_COL_FRAC;
    const nutWidth = w * NUT_FRAC;
    const fretSpacing = (w - openWidth - nutWidth) / numFrets;
    const stringSpacing = h / sc;
    const firstStringY = stringSpacing / 2;
    const unit = Math.min(stringSpacing, fretSpacing);
    const mx = (x: number) => (d.leftHanded ? w - x : x);

    // wood + grain
    ctx.fillStyle = Colors.wood;
    ctx.fillRect(0, 0, w, h);
    const grain: [number, number][] = [
      [0.07, 0.10], [0.18, 0.06], [0.27, 0.08], [0.38, 0.05], [0.49, 0.09],
      [0.61, 0.06], [0.73, 0.08], [0.84, 0.05], [0.92, 0.07],
    ];
    ctx.lineWidth = 1.2;
    for (const [yFrac, alpha] of grain) {
      ctx.strokeStyle = withAlpha(Colors.woodGrain, alpha);
      ctx.beginPath();
      ctx.moveTo(0, h * yFrac);
      ctx.lineTo(w, h * yFrac);
      ctx.stroke();
    }

    // open-string separator
    line(ctx, mx(openWidth), 0, mx(openWidth), h, withAlpha(Colors.fretWire, 0.5), 1);

    // nut
    const nutLeft = mx(d.leftHanded ? openWidth + nutWidth : openWidth);
    ctx.fillStyle = Colors.nut;
    ctx.fillRect(nutLeft, 0, nutWidth, h);

    // fret wires
    for (let f = 1; f <= numFrets; f++) {
      const x = mx(openWidth + nutWidth + f * fretSpacing);
      line(ctx, x, 0, x, h, Colors.fretWire, 2.2);
    }

    // inlays
    const singleDots = [3, 5, 7, 9, 15, 17, 19, 21];
    const doubleDots = [12, 24];
    const inlayR = Math.max(3, unit * 0.12);
    ctx.fillStyle = withAlpha(Colors.inlay, 0.6);
    for (const f of singleDots) {
      if (f > numFrets) continue;
      const x = mx(openWidth + nutWidth + (f - 0.5) * fretSpacing);
      circle(ctx, x, h / 2, inlayR);
    }
    for (const f of doubleDots) {
      if (f > numFrets) continue;
      const x = mx(openWidth + nutWidth + (f - 0.5) * fretSpacing);
      circle(ctx, x, h * 0.32, inlayR);
      circle(ctx, x, h * 0.68, inlayR);
    }

    // strings (index 0 = lowest pitch = bottom)
    const woundCutoff = sc === 4 ? 0 : Math.floor((sc + 1) / 2);
    for (let s = 0; s < sc; s++) {
      const y = firstStringY + (sc - 1 - s) * stringSpacing;
      const isWound = s < woundCutoff;
      if (isWound) {
        const thickness = 4.0 - s * 0.5;
        line(ctx, 0, y, w, y, Colors.stringWound, thickness);
        ctx.setLineDash([2.5, 1.5]);
        line(ctx, 0, y, w, y, withAlpha("#996F40", 0.8), thickness * 0.85);
        ctx.setLineDash([]);
        line(ctx, 0, y - thickness * 0.35, w, y - thickness * 0.35, withAlpha("#E9D6A3", 0.55), 0.7);
      } else {
        const plainIdx = s - woundCutoff;
        const thickness = 2.1 - plainIdx * 0.3;
        line(ctx, 0, y, w, y, Colors.stringPlain, thickness);
        line(ctx, 0, y - thickness * 0.3, w, y - thickness * 0.3, "#F3E9CC", 0.6);
      }
    }

    // marks
    const dotR = unit * 0.40;
    const labelPx = unit * 0.42;
    for (const [key, mark] of d.marks) {
      const pos = parseKey(key);
      if (pos.fret > numFrets || pos.stringIndex >= sc) continue;
      const [mxp, myp] = this.positionToPixel(pos, w, h, sc, numFrets, d.leftHanded);
      if (mark.kind === MarkKind.Pick) {
        ctx.strokeStyle = Colors.pickSelect;
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.arc(mxp, myp, dotR, 0, Math.PI * 2);
        ctx.stroke();
      } else {
        const fill = mark.isRoot ? Colors.rootTone : mark.kind === MarkKind.Scale ? Colors.scaleTone : Colors.chordTone;
        ctx.fillStyle = fill;
        circle(ctx, mxp, myp, dotR);
        if (mark.isRoot) {
          ctx.strokeStyle = Colors.inlay;
          ctx.lineWidth = 1.5;
          ctx.beginPath();
          ctx.arc(mxp, myp, dotR * 0.78, 0, Math.PI * 2);
          ctx.stroke();
        }
        if (mark.label) {
          ctx.fillStyle = Colors.textPrimary;
          ctx.font = `bold ${labelPx}px system-ui, sans-serif`;
          ctx.textAlign = "center";
          ctx.textBaseline = "middle";
          ctx.fillText(mark.label, mxp, myp);
        }
      }
    }

    // open-string labels (left of nut)
    ctx.fillStyle = Colors.primary;
    ctx.font = `600 ${stringSpacing * 0.32}px system-ui, sans-serif`;
    ctx.textBaseline = "middle";
    for (let s = 0; s < sc; s++) {
      const y = firstStringY + (sc - 1 - s) * stringSpacing;
      const letter = spellPc(midiPitchClass(d.tuning.openStrings[s].midi));
      const isHighest = s === sc - 1;
      const label = isHighest ? letter.toLowerCase() : letter;
      ctx.textAlign = d.leftHanded ? "right" : "left";
      ctx.fillText(label, d.leftHanded ? w - 4 : 4, y);
    }

    // muted-string X
    if (d.mutedStrings.size > 0) {
      ctx.fillStyle = Colors.rootTone;
      ctx.font = `bold ${stringSpacing * 0.5}px system-ui, sans-serif`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      const xCol = mx(openWidth * OPEN_MARK_FRAC);
      for (const s of d.mutedStrings) {
        if (s < 0 || s >= sc) continue;
        const y = firstStringY + (sc - 1 - s) * stringSpacing;
        ctx.fillText("✕", xCol, y);
      }
    }

    // selected ring
    if (d.selectedPosition) {
      const [mxp, myp] = this.positionToPixel(d.selectedPosition, w, h, sc, numFrets, d.leftHanded);
      ctx.strokeStyle = Colors.primary;
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(mxp, myp, unit * 0.48, 0, Math.PI * 2);
      ctx.stroke();
    }

    // fret-number row
    ctx.fillStyle = Colors.background;
    ctx.fillRect(0, h, w, numberStripH);
    ctx.fillStyle = Colors.textSecondary;
    ctx.font = `500 ${numberStripH * 0.55}px system-ui, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    for (let f = 1; f <= numFrets; f++) {
      const x = mx(openWidth + nutWidth + (f - 0.5) * fretSpacing);
      ctx.fillText(String(f), x, h + numberStripH / 2);
    }
    ctx.fillText("0", mx(openWidth * OPEN_MARK_FRAC), h + numberStripH / 2);
  }
}

function line(ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number, color: string, width: number): void {
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
}

function circle(ctx: CanvasRenderingContext2D, x: number, y: number, r: number): void {
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fill();
}

function parseKey(key: string): FretPosition {
  const [s, f] = key.split(",");
  return fp(parseInt(s, 10), parseInt(f, 10));
}

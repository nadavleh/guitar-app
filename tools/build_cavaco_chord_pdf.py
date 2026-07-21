"""Render the cavaquinho G-chord-shapes strip PDF ("All_G_chord_shapes").

Reproducible pipeline (keep both steps committed):
  1) .\\gradlew :theory:emitCavacoShapes      -> tools/cavaco_g_shapes.json
     (shapes come from the app's own cavaquinho voicing engine)
  2) python tools/build_cavaco_chord_pdf.py  [out.pdf]
     -> tools/out/All_G_chord_shapes_by_inversion.pdf (default)

Layout mirrors the original hand-built sheet (one wide strip page per chord
quality, 7.21 cm tall, horizontal 4-string diagrams, teal interval dots with a
coral ringed root), but the shapes in each row are ORDERED BY INVERSION:
root position, 1st, 2nd, (3rd for 7ths) — then the leftovers as Extra 1, 2, …
(per Nadav's request; the original was ordered by neck position).

Requires: pip install reportlab
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from reportlab.lib.colors import Color
from reportlab.pdfgen import canvas

ROOT = Path(__file__).resolve().parent
SHAPES_JSON = ROOT / "cavaco_g_shapes.json"

# ---- palette (sampled from the original sheet) ----
CREAM = Color(0.957, 0.933, 0.882)   # page background
BOARD = Color(0.910, 0.808, 0.643)   # fretboard wood
GRID = Color(0.35, 0.33, 0.30)       # fret/string lines
NUT = Color(0.10, 0.09, 0.08)
INLAY = Color(0.55, 0.42, 0.28)
TEAL = Color(0.207, 0.769, 0.706)    # interval dots
CORAL = Color(0.788, 0.290, 0.243)   # header + root ring
CAPTION = Color(0.25, 0.25, 0.28)
FRETNUM = Color(0.35, 0.35, 0.40)

# ---- geometry (pt); page height matches the original 204.5 pt = 7.21 cm ----
PAGE_H = 204.5
MARGIN = 16.0
HEADER_H = 30.0
COL_W = 40.0          # width of one fret column
STRING_GAP = 22.0     # vertical gap between strings
BOARD_PAD_Y = 11.0    # board space above top / below bottom string
DIAGRAM_GAP = 26.0
CAPTION_H = 30.0      # fret numbers + caption line below the board
DOT_R = 8.6

INTERVAL_LABEL = {0: "1", 1: "b2", 2: "9", 3: "b3", 4: "3", 5: "11",
                  6: "b5", 7: "5", 8: "b6", 9: "6", 10: "b7", 11: "7"}
INLAY_FRETS = {3, 5, 7, 9, 12, 15}


def inversion_order(shapes: list[dict], has_seventh: bool) -> list[tuple[dict, str]]:
    """Order a quality's shapes root/1st/2nd/(3rd) inversion, then extras.

    Category comes from the bass interval (lowest played string): 0 = root
    position; 3rd (3/4) = 1st inversion; 5th (6/7/8) = 2nd; 7th (9/10/11) = 3rd.
    The lowest-position shape of each category is its representative; everything
    else becomes "Extra n" (kept in position order)."""
    def category(s: dict) -> int:
        b = s["bass"] % 12
        if b == 0: return 0
        if b in (3, 4): return 1
        if b in (6, 7, 8): return 2
        if b in (9, 10, 11): return 3
        return 4
    names = {0: "Root position", 1: "1st inversion", 2: "2nd inversion",
             3: "3rd inversion" if has_seventh else "7th in bass"}
    by_cat: dict[int, list[dict]] = {}
    for s in sorted(shapes, key=lambda s: (s["position"], max(f for f in s["frets"] if f is not None))):
        by_cat.setdefault(category(s), []).append(s)
    ordered: list[tuple[dict, str]] = []
    extras: list[dict] = []
    for cat in (0, 1, 2, 3):
        group = by_cat.get(cat, [])
        if group:
            ordered.append((group[0], names[cat]))
            extras.extend(group[1:])
    extras.extend(by_cat.get(4, []))
    extras.sort(key=lambda s: s["position"])
    for i, s in enumerate(extras, 1):
        ordered.append((s, f"Extra {i}"))
    return ordered


def fret_window(shape: dict) -> tuple[int, int]:
    """Fret columns to draw: include fret 0 when the shape uses open strings
    (nut drawn after the open column, like the original); at least 3 columns."""
    played = [f for f in shape["frets"] if f is not None]
    fretted = [f for f in played if f > 0]
    if not fretted:
        return (0, 4)
    lo, hi = min(fretted), max(fretted)
    if 0 in played:
        return (0, max(hi, 4))
    if hi - lo < 2:
        hi = lo + 2
    return (lo, hi)


def draw_diagram(c: canvas.Canvas, x: float, shape: dict, caption: str, dim7: bool) -> float:
    """Draw one chord diagram with its left edge at x; returns its width."""
    lo, hi = fret_window(shape)
    ncols = hi - lo + 1
    width = ncols * COL_W
    n_str = len(shape["frets"])
    board_h = (n_str - 1) * STRING_GAP + 2 * BOARD_PAD_Y
    y0 = PAGE_H - HEADER_H - 10 - board_h          # board top area below header
    # board
    c.setFillColor(BOARD)
    c.rect(x, y0, width, board_h, stroke=0, fill=1)
    # fret lines (column boundaries)
    c.setStrokeColor(GRID)
    c.setLineWidth(0.9)
    for i in range(ncols + 1):
        c.line(x + i * COL_W, y0, x + i * COL_W, y0 + board_h)
    # nut: thick bar after the open (fret 0) column
    if lo == 0:
        c.setStrokeColor(NUT)
        c.setLineWidth(4.5)
        c.line(x + COL_W, y0, x + COL_W, y0 + board_h)
    # strings: string 0 = low D drawn at the BOTTOM (top line = high d).
    # PDF y grows upward, so s * STRING_GAP puts s=0 lowest.
    string_y = {s: y0 + BOARD_PAD_Y + s * STRING_GAP for s in range(n_str)}
    c.setStrokeColor(GRID)
    c.setLineWidth(1.1)
    for s in range(n_str):
        c.line(x, string_y[s], x + width, string_y[s])
    # inlay dots (mid-board, at the marker frets inside the window)
    c.setFillColor(INLAY)
    for f in range(max(lo, 1), hi + 1):
        if f in INLAY_FRETS:
            cx = x + (f - lo) * COL_W + COL_W / 2
            c.circle(cx, y0 + board_h / 2, 2.6, stroke=0, fill=1)
    # note dots
    for s, (f, iv) in enumerate(zip(shape["frets"], shape["intervals"])):
        if f is None or iv is None:
            continue
        cx = x + (f - lo) * COL_W + COL_W / 2
        cy = string_y[s]
        label = INTERVAL_LABEL[iv % 12]
        if dim7 and iv % 12 == 9:
            label = "bb7"
        if iv % 12 == 0:   # root: cream fill + coral ring + coral label
            c.setFillColor(CREAM)
            c.setStrokeColor(CORAL)
            c.setLineWidth(2.2)
            c.circle(cx, cy, DOT_R, stroke=1, fill=1)
            c.setFillColor(CORAL)
        else:
            c.setFillColor(TEAL)
            c.circle(cx, cy, DOT_R, stroke=0, fill=1)
            c.setFillColor(Color(1, 1, 1))
        c.setFont("Helvetica-Bold", 9 if len(label) <= 2 else 7)
        c.drawCentredString(cx, cy - 3.1, label)
    # fret numbers
    c.setFillColor(FRETNUM)
    c.setFont("Helvetica", 9)
    for i in range(ncols):
        c.drawCentredString(x + i * COL_W + COL_W / 2, y0 - 12, str(lo + i))
    # caption
    c.setFillColor(CAPTION)
    c.setFont("Helvetica", 10.5)
    c.drawCentredString(x + width / 2, y0 - 26, caption)
    return width


def main() -> None:
    data = json.loads(SHAPES_JSON.read_text(encoding="utf-8"))
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "out" / "All_G_chord_shapes_by_inversion.pdf"
    out.parent.mkdir(parents=True, exist_ok=True)

    c = canvas.Canvas(str(out), pagesize=(1000, PAGE_H))
    c.setTitle("All_G_chord_shapes (by inversion)")
    for page in data["pages"]:
        has7 = len([s for s in page["shapes"][0]["intervals"] if s is not None]) >= 4 and \
               any((s["bass"] % 12) in (9, 10, 11) for s in page["shapes"]) or page["symbol"] in ("7", "m7", "maj7", "dim7", "m7b5")
        ordered = inversion_order(page["shapes"], has_seventh=page["symbol"] in ("7", "m7", "maj7", "dim7", "m7b5"))
        # measure total width first (pages are auto-sized like the original)
        widths = [(fret_window(s)[1] - fret_window(s)[0] + 1) * COL_W for s, _ in ordered]
        page_w = MARGIN * 2 + sum(widths) + DIAGRAM_GAP * (len(ordered) - 1)
        c.setPageSize((page_w, PAGE_H))
        # background + header
        c.setFillColor(CREAM)
        c.rect(0, 0, page_w, PAGE_H, stroke=0, fill=1)
        c.setFillColor(CORAL)
        c.setFont("Helvetica-Bold", 19)
        c.drawString(MARGIN, PAGE_H - 26, page["header"])
        # diagrams, each captioned with its inversion + fret position
        x = MARGIN
        for (shape, inv_name) in ordered:
            pos = shape["position"]
            caption = f"{inv_name} · {'open' if pos == 0 else f'fret {pos}'}"
            w = draw_diagram(c, x, shape, caption, dim7=page["symbol"] == "dim7")
            x += w + DIAGRAM_GAP
        c.showPage()
    c.save()
    print(f"wrote {out}")


if __name__ == "__main__":
    main()

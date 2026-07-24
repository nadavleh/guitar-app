"""Render the cavaquinho G-chord-shapes strip PDF ("All_G_chord_shapes").

Reproducible pipeline (keep both steps committed):
  1) .\\gradlew :theory:emitCavacoShapes      -> tools/cavaco_g_shapes.json
     (shapes come from the app's own theory engine / voicing enumerator)
  2) python tools/build_cavaco_chord_pdf.py  [out.pdf]
     -> tools/out/All_G_chord_shapes_by_inversion.pdf (default)

Each chord quality is one wide strip page. Within a page the diagrams are
grouped into three labelled sections, in order:

  * COMPLETE VOICINGS  — every chord tone present, all four strings. Because
    all four strings sound, the low-D string is always the bass, so a voicing's
    INVERSION is simply which chord tone sits on that lowest string:
        root -> Root position · 3rd -> 1st inv · 5th -> 2nd inv · 7th -> 3rd inv.
    Two voicings are shown per inversion (lowest neck position first).
  * ROOTLESS           — the root dropped, leaving an upper-structure triad
    built on the 3rd (dominant 7 -> diminished, e.g. G7 -> Bdim; maj7 -> minor;
    m7 -> major). Each is captioned with that triad ("= Bdim").
  * NO-5TH SHELLS      — the 5th dropped (root + 3rd + 7th), e.g. G7 = 5-4-6-5.

Teal interval dots; the root gets a cream fill + coral ring (so rootless
shapes, having no root, are visibly all-teal). Diagrams are compact.

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
CAPTION = Color(0.22, 0.22, 0.25)
SUBCAP = Color(0.45, 0.45, 0.50)
SECTION = Color(0.40, 0.36, 0.32)
DIVIDER = Color(0.62, 0.55, 0.46)
FRETNUM = Color(0.35, 0.35, 0.40)

# ---- geometry (pt); diagrams are noticeably smaller than the original ----
MARGIN = 14.0
COL_W = 23.0          # width of one fret column
STRING_GAP = 15.0     # vertical gap between strings
BOARD_PAD_Y = 8.0     # board space above top / below bottom string
DIAGRAM_GAP = 15.0
GROUP_GAP = 26.0      # extra space between the three sections
DOT_R = 6.0

# vertical anchors, measured from the top of the page
CHORD_NAME_DY = 20.0  # baseline of the big chord name
SECTION_DY = 36.0     # baseline of the small section header
BOARD_TOP_DY = 43.0   # top edge of the fretboard
FRETNUM_GAP = 11.0    # fret numbers below the board
CAP1_GAP = 22.0       # caption line 1 below the board
CAP2_GAP = 32.0       # caption line 2 below the board
BOTTOM_MARGIN = 8.0

N_STRINGS = 4
BOARD_H = (N_STRINGS - 1) * STRING_GAP + 2 * BOARD_PAD_Y
PAGE_H = BOARD_TOP_DY + BOARD_H + CAP2_GAP + BOTTOM_MARGIN

INTERVAL_LABEL = {0: "1", 1: "b2", 2: "9", 3: "b3", 4: "3", 5: "11",
                  6: "b5", 7: "5", 8: "b6", 9: "6", 10: "b7", 11: "7"}
INLAY_FRETS = {3, 5, 7, 9, 12, 15}

INV_NAME = {0: "Root position", 1: "1st inversion",
            2: "2nd inversion", 3: "3rd inversion"}

SECTION_TITLE = {
    "complete": "COMPLETE VOICINGS  (all chord tones)",
    "rootless": "ROOTLESS  (upper-structure triad on the 3rd — no root)",
    "shell": "NO-5TH SHELLS",
}


def bass_word(shape: dict, symbol: str) -> str:
    """Interval name of the bass note, with dim7's bb7 spelled correctly."""
    b = shape["bass"] % 12
    if symbol == "dim7" and b == 9:
        return "bb7"
    return INTERVAL_LABEL[b]


def caption_lines(shape: dict, symbol: str) -> tuple[str, str]:
    kind = shape["kind"]
    pos = shape["position"]
    where = "open" if pos == 0 else f"fret {pos}"
    if kind == "complete":
        # 6/9 (R 3 6 9) has no meaningful inversion numbering — label by bass tone.
        if symbol == "69":
            return f"{bass_word(shape, symbol)} in bass", where
        return INV_NAME.get(shape["inversion"], ""), f"{bass_word(shape, symbol)} in bass · {where}"
    if kind == "rootless":
        return "Rootless", f"= {shape['label']}"
    # shell
    return "No-5th shell", f"{bass_word(shape, symbol)} in bass · {where}"


def fret_window(shape: dict) -> tuple[int, int]:
    """Fret columns to draw: include fret 0 when the shape uses open strings
    (nut drawn after the open column); at least 3 columns."""
    played = [f for f in shape["frets"] if f is not None]
    fretted = [f for f in played if f > 0]
    if not fretted:
        return (0, 3)
    lo, hi = min(fretted), max(fretted)
    if 0 in played:
        return (0, max(hi, 3))
    if hi - lo < 2:
        hi = lo + 2
    return (lo, hi)


def diagram_width(shape: dict) -> float:
    lo, hi = fret_window(shape)
    return (hi - lo + 1) * COL_W


def draw_diagram(c: canvas.Canvas, x: float, shape: dict, symbol: str) -> float:
    """Draw one chord diagram with its left edge at x; returns its width."""
    lo, hi = fret_window(shape)
    ncols = hi - lo + 1
    width = ncols * COL_W
    y0 = PAGE_H - BOARD_TOP_DY - BOARD_H          # board bottom
    # board
    c.setFillColor(BOARD)
    c.rect(x, y0, width, BOARD_H, stroke=0, fill=1)
    # fret lines (column boundaries)
    c.setStrokeColor(GRID)
    c.setLineWidth(0.8)
    for i in range(ncols + 1):
        c.line(x + i * COL_W, y0, x + i * COL_W, y0 + BOARD_H)
    # nut: thick bar after the open (fret 0) column
    if lo == 0:
        c.setStrokeColor(NUT)
        c.setLineWidth(4.0)
        c.line(x + COL_W, y0, x + COL_W, y0 + BOARD_H)
    # strings: string 0 = low D drawn at the BOTTOM (top line = high d).
    string_y = {s: y0 + BOARD_PAD_Y + s * STRING_GAP for s in range(N_STRINGS)}
    c.setStrokeColor(GRID)
    c.setLineWidth(1.0)
    for s in range(N_STRINGS):
        c.line(x, string_y[s], x + width, string_y[s])
    # inlay dots (mid-board, at the marker frets inside the window)
    c.setFillColor(INLAY)
    for f in range(max(lo, 1), hi + 1):
        if f in INLAY_FRETS:
            cx = x + (f - lo) * COL_W + COL_W / 2
            c.circle(cx, y0 + BOARD_H / 2, 2.2, stroke=0, fill=1)
    # note dots
    for s, (f, iv) in enumerate(zip(shape["frets"], shape["intervals"])):
        if f is None or iv is None:
            continue
        cx = x + (f - lo) * COL_W + COL_W / 2
        cy = string_y[s]
        label = INTERVAL_LABEL[iv % 12]
        if symbol == "dim7" and iv % 12 == 9:
            label = "bb7"
        if iv % 12 == 0:   # root: cream fill + coral ring + coral label
            c.setFillColor(CREAM)
            c.setStrokeColor(CORAL)
            c.setLineWidth(1.8)
            c.circle(cx, cy, DOT_R, stroke=1, fill=1)
            c.setFillColor(CORAL)
        else:
            c.setFillColor(TEAL)
            c.circle(cx, cy, DOT_R, stroke=0, fill=1)
            c.setFillColor(Color(1, 1, 1))
        c.setFont("Helvetica-Bold", 7 if len(label) <= 2 else 5.5)
        c.drawCentredString(cx, cy - 2.4, label)
    # fret numbers
    c.setFillColor(FRETNUM)
    c.setFont("Helvetica", 7.5)
    for i in range(ncols):
        c.drawCentredString(x + i * COL_W + COL_W / 2, y0 - FRETNUM_GAP, str(lo + i))
    # caption (two lines)
    line1, line2 = caption_lines(shape, symbol)
    c.setFillColor(CAPTION)
    c.setFont("Helvetica-Bold", 8)
    c.drawCentredString(x + width / 2, y0 - CAP1_GAP, line1)
    c.setFillColor(SUBCAP)
    c.setFont("Helvetica", 7.5)
    c.drawCentredString(x + width / 2, y0 - CAP2_GAP, line2)
    return width


def main() -> None:
    data = json.loads(SHAPES_JSON.read_text(encoding="utf-8"))
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "out" / "All_G_chord_shapes_by_inversion.pdf"
    out.parent.mkdir(parents=True, exist_ok=True)

    c = canvas.Canvas(str(out), pagesize=(1000, PAGE_H))
    c.setTitle("All_G_chord_shapes (by inversion, complete + rootless + shells)")

    board_bottom = PAGE_H - BOARD_TOP_DY - BOARD_H
    board_top = PAGE_H - BOARD_TOP_DY

    for page in data["pages"]:
        symbol = page["symbol"]
        shapes = page["shapes"]
        # Measure total width: sum of diagram widths + gaps, with a wider gap
        # (and a divider) each time the section kind changes.
        widths = [diagram_width(s) for s in shapes]
        page_w = MARGIN * 2 + sum(widths)
        for i in range(1, len(shapes)):
            page_w += GROUP_GAP if shapes[i]["kind"] != shapes[i - 1]["kind"] else DIAGRAM_GAP
        c.setPageSize((page_w, PAGE_H))

        # background + big chord name
        c.setFillColor(CREAM)
        c.rect(0, 0, page_w, PAGE_H, stroke=0, fill=1)
        c.setFillColor(CORAL)
        c.setFont("Helvetica-Bold", 18)
        c.drawString(MARGIN, PAGE_H - CHORD_NAME_DY, page["header"])

        # diagrams, grouped by kind with section headers + dividers
        x = MARGIN
        prev_kind = None
        for shape, w in zip(shapes, widths):
            kind = shape["kind"]
            if prev_kind is not None:
                if kind != prev_kind:
                    # divider line + wider gap between sections
                    div_x = x + GROUP_GAP / 2
                    c.setStrokeColor(DIVIDER)
                    c.setLineWidth(0.8)
                    c.line(div_x, board_bottom, div_x, board_top)
                    x += GROUP_GAP
                else:
                    x += DIAGRAM_GAP
            if kind != prev_kind:
                c.setFillColor(SECTION)
                c.setFont("Helvetica-Bold", 8)
                c.drawString(x, PAGE_H - SECTION_DY, SECTION_TITLE.get(kind, kind.upper()))
            draw_diagram(c, x, shape, symbol)
            x += w
            prev_kind = kind

        c.showPage()
    c.save()
    print(f"wrote {out}")


if __name__ == "__main__":
    main()

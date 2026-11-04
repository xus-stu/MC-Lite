from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_figures import (  # noqa: E402
    Canvas, NAVY, NAVY_SOFT, GOLD, TERRACOTTA, GRID, LIGHT, MID, WHITE,
    FONT_REG, FONT_BOLD, FONT_ITAL, pt_px,
)
from PIL import Image, ImageDraw, ImageFont  # noqa: E402


def shade_poly(c: Canvas, pts, fill):
    overlay = Image.new("RGBA", c.img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.polygon([c.P(p) for p in pts], fill=fill)
    c.img = Image.alpha_composite(c.img, overlay)
    c.d = ImageDraw.Draw(c.img)


def sol19():
    c = Canvas(5.6, 5.2, (-1.0, 12.4), (-1.4, 10.8))
    B, C, A = (0, 0), (4, 0), (0, 3)
    D = (4 + 12 * 3 / 5, 12 * 4 / 5)
    shade_poly(c, [A, B, C, D], (31, 58, 95, 18))
    c.poly([A, B, C, D], outline=NAVY, fill=None, width_pt=1.8)
    c.line(A, C, color=GOLD, width_pt=1.3, dash=(4, 3))
    c.right_angle(B, A, C, size_pt=8)
    c.right_angle(C, A, D, size_pt=8, color=GOLD)
    for p, label, off in [
        (A, "A", (-0.55, 0.55)), (B, "B", (-0.58, -0.55)),
        (C, "C", (0.55, -0.55)), (D, "D", (0.55, 0.55)),
    ]:
        c.dot(p, color=NAVY, r_pt=2.3)
        c.text((p[0] + off[0], p[1] + off[1]), label, size=11, font="ital")
    c.text((-0.55, 1.5), "3", size=10.5, anchor="rm")
    c.text((2.0, -0.55), "4", size=10.5, anchor="mm")
    c.text((8.2, 5.3), "12", size=10.5, anchor="lm")
    c.text((5.3, 7.0), "13", size=10.5, anchor="rm")
    c.text((1.55, 2.0), "5", size=10.5, color=GOLD, anchor="mm")
    c.save("sol19_quadrilateral.png")


def sol20():
    c = Canvas(5.2, 6.0, (-1.2, 9.4), (-1.4, 11.3))
    A, B, C, D = (0, 10), (8, 10), (8, 0), (0, 0)
    E, F = (5, 0), (8, 4)
    c.poly([A, B, C, D], outline=NAVY, fill=None, width_pt=1.8)
    c.line(A, E, color=GOLD, width_pt=1.5, dash=(5, 3))
    c.line(A, F, color=NAVY_SOFT, width_pt=1.1, dash=(3, 3))
    c.line(E, F, color=NAVY_SOFT, width_pt=1.1, dash=(3, 3))
    c.right_angle(C, B, D, size_pt=7)
    for p, label, off in [
        (A, "A", (-0.55, 0.55)), (B, "B", (0.55, 0.55)),
        (C, "C", (0.55, -0.55)), (D, "D", (-0.55, -0.55)),
        (E, "E", (0.0, -0.80)), (F, "F", (0.55, 0.0)),
    ]:
        c.dot(p, color=NAVY, r_pt=2.2)
        c.text((p[0] + off[0], p[1] + off[1]), label, size=10.5, font="ital")
    c.text((4.0, 10.55), "8", size=9.5, anchor="mm")
    c.text((8.55, 7.0), "6", size=9.5, anchor="lm")
    c.text((8.55, 2.0), "4", size=9.5, anchor="lm")
    c.text((2.5, -0.85), "8-x", size=9.2, color=GOLD, anchor="mm")
    c.text((6.45, -0.85), "x", size=9.2, color=GOLD, anchor="mm")
    c.text((6.55, 2.15), "8-x", size=9.2, color=GOLD, anchor="lm")
    c.text((3.0, 7.2), "10", size=9.2, anchor="rm")
    c.save("sol20_folding.png")


def panel(c: Canvas, ox, oy, w, h, num, diag_label):
    A = (ox, oy)
    B = (ox + w, oy)
    C = (ox + w, oy + h)
    D = (ox, oy + h)
    shade_poly(c, [A, B, C, D], (31, 58, 95, 12))
    c.poly([A, B, C, D], outline=NAVY, fill=None, width_pt=1.3)
    c.line(A, C, color=GOLD, width_pt=1.15, dash=(4, 2))
    c.text((ox + w / 2, oy - 0.34), str(w), size=9.0, anchor="mm")
    c.text((ox + w + 0.26, oy + h / 2), str(h), size=9.0, anchor="lm")
    c.text((ox + w * 0.58, oy + h * 0.55 + 0.32), diag_label, size=10.0, color=GOLD, anchor="lm")
    # numbered badge
    cx, cy = c.P((ox + 0.45, oy + h - 0.45))
    r = pt_px(8.0)
    c.d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=GOLD)
    f = ImageFont.truetype(FONT_BOLD, pt_px(8.0))
    c.d.text((cx, cy), str(num), font=f, fill=WHITE, anchor="mm")


def sol21():
    c = Canvas(13.2, 3.7, (-0.2, 27.2), (-0.8, 6.2))
    panel(c, 0, 0, 9, 3, 1, "√90")
    panel(c, 10, 0, 8, 4, 2, "√80")
    panel(c, 19, 0, 7, 5, 3, "√74")
    c.save("sol21_nets.png")


if __name__ == "__main__":
    sol19()
    sol20()
    sol21()

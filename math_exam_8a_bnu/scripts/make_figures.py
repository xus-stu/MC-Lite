from __future__ import annotations

import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "figures"
OUT.mkdir(parents=True, exist_ok=True)

NAVY = (31, 58, 95, 255)
NAVY_SOFT = (74, 101, 134, 255)
GOLD = (176, 141, 87, 255)
TERRACOTTA = (166, 92, 70, 255)
GRID = (219, 229, 238, 255)
GRID_STRONG = (191, 207, 221, 255)
LIGHT = (234, 241, 247, 255)
MID = (143, 166, 184, 255)
WHITE = (255, 255, 255, 255)

FONT_DIR = Path("/System/Library/Fonts/Supplemental")
FONT_REG = str(FONT_DIR / "Times New Roman.ttf")
FONT_BOLD = str(FONT_DIR / "Times New Roman Bold.ttf")
FONT_ITAL = str(FONT_DIR / "Times New Roman Italic.ttf")
FONT_CN = "/System/Library/Fonts/Supplemental/Songti.ttc"

DPI = 400
S = 4


def pt_px(pt: float) -> int:
    return max(1, int(round(pt * DPI / 72.0 * S)))


class Canvas:
    def __init__(self, w_cm: float, h_cm: float, xlim, ylim, margin_cm=0.18):
        self.w = int(round(w_cm / 2.54 * DPI))
        self.h = int(round(h_cm / 2.54 * DPI))
        self.iw, self.ih = self.w * S, self.h * S
        self.img = Image.new("RGBA", (self.iw, self.ih), WHITE)
        self.d = ImageDraw.Draw(self.img)
        self.xlim = xlim
        self.ylim = ylim
        xr = xlim[1] - xlim[0]
        yr = ylim[1] - ylim[0]
        m = margin_cm / 2.54 * DPI * S
        self.scale = min((self.iw - 2 * m) / xr, (self.ih - 2 * m) / yr)
        self.ox = (self.iw - xr * self.scale) / 2
        self.oy = (self.ih - yr * self.scale) / 2

    def X(self, x):
        return self.ox + (x - self.xlim[0]) * self.scale

    def Y(self, y):
        return self.ih - (self.oy + (y - self.ylim[0]) * self.scale)

    def P(self, p):
        return (self.X(p[0]), self.Y(p[1]))

    def line(self, p1, p2, color=NAVY, width_pt=1.7, dash=None):
        a, b = self.P(p1), self.P(p2)
        w = pt_px(width_pt)
        if not dash:
            self.d.line([a, b], fill=color, width=w)
            return
        dash_px, gap_px = [pt_px(v) for v in dash]
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy)
        if L == 0:
            return
        ux, uy = dx / L, dy / L
        t = 0.0
        while t < L:
            t2 = min(t + dash_px, L)
            self.d.line(
                [(a[0] + ux * t, a[1] + uy * t), (a[0] + ux * t2, a[1] + uy * t2)],
                fill=color,
                width=w,
            )
            t = t2 + gap_px

    def poly(self, pts, outline=NAVY, fill=None, width_pt=1.7):
        pp = [self.P(p) for p in pts]
        if fill is not None:
            self.d.polygon(pp, fill=fill)
        self.d.line(pp + [pp[0]], fill=outline, width=pt_px(width_pt), joint="curve")

    def dot(self, p, color=NAVY, r_pt=2.6):
        x, y = self.P(p)
        r = pt_px(r_pt)
        self.d.ellipse([x - r, y - r, x + r, y + r], fill=color)

    def text(self, p, s, size=11, color=NAVY, anchor="mm", font="reg", halo=True, italic=False):
        x, y = self.P(p)
        path = FONT_ITAL if (italic or font == "ital") else (FONT_BOLD if font == "bold" else FONT_REG)
        f = ImageFont.truetype(path, pt_px(size))
        kw = dict(font=f, fill=color, anchor=anchor)
        if halo:
            kw["stroke_width"] = max(2, pt_px(0.55))
            kw["stroke_fill"] = WHITE
        self.d.text((x, y), s, **kw)

    def raw_text(self, xy, s, size=10, color=NAVY, anchor="mm", font="reg"):
        path = FONT_BOLD if font == "bold" else FONT_REG
        f = ImageFont.truetype(path, pt_px(size))
        self.d.text(xy, s, font=f, fill=color, anchor=anchor, stroke_width=max(2, pt_px(0.5)), stroke_fill=WHITE)

    def right_angle(self, vertex, p1, p2, size_pt=9, color=NAVY):
        v = self.P(vertex)
        a = self.P(p1)
        b = self.P(p2)
        ux, uy = a[0] - v[0], a[1] - v[1]
        vx, vy = b[0] - v[0], b[1] - v[1]
        lu, lv = math.hypot(ux, uy), math.hypot(vx, vy)
        if lu == 0 or lv == 0:
            return
        s = pt_px(size_pt)
        ux, uy = ux / lu, uy / lu
        vx, vy = vx / lv, vy / lv
        q1 = (v[0] + ux * s, v[1] + uy * s)
        q2 = (v[0] + ux * s + vx * s, v[1] + uy * s + vy * s)
        q3 = (v[0] + vx * s, v[1] + vy * s)
        self.d.line([q1, q2, q3], fill=color, width=pt_px(1.2), joint="curve")

    def arrowhead(self, tip, direction, size_pt=8, color=NAVY):
        x, y = tip
        dx, dy = direction
        L = math.hypot(dx, dy)
        ux, uy = dx / L, dy / L
        px, py = -uy, ux
        s = pt_px(size_pt)
        base = (x - ux * s * 1.4, y - uy * s * 1.4)
        p1 = (base[0] + px * s * 0.55, base[1] + py * s * 0.55)
        p2 = (base[0] - px * s * 0.55, base[1] - py * s * 0.55)
        self.d.polygon([tip, p1, p2], fill=color)

    def sub_label(self, p, base, sub, size=10.5, color=NAVY):
        x, y = self.P(p)
        f1 = ImageFont.truetype(FONT_ITAL, pt_px(size))
        f2 = ImageFont.truetype(FONT_REG, pt_px(size * 0.62))
        self.d.text((x, y), base, font=f1, fill=color, anchor="mm", stroke_width=max(2, pt_px(0.5)), stroke_fill=WHITE)
        bbox = self.d.textbbox((x, y), base, font=f1, anchor="mm")
        self.d.text((bbox[2] + pt_px(0.1), y + pt_px(1.4)), sub, font=f2, fill=color, anchor="lm", stroke_width=max(1, pt_px(0.35)), stroke_fill=WHITE)

    def save(self, name):
        img = self.img.convert("RGB").resize((self.w, self.h), Image.LANCZOS)
        path = OUT / name
        img.save(path, "PNG", dpi=(DPI, DPI))
        print(f"saved {path}  {self.w}x{self.h}")


def fig15():
    c = Canvas(6.2, 5.7, (-0.8, 14.8), (-1.2, 13.2))
    B, C, D, A = (0, 0), (14, 0), (5, 0), (5, 12)
    c.poly([A, B, C], outline=NAVY, fill=None, width_pt=1.8)
    c.line(A, D, color=GOLD, width_pt=1.4, dash=(5, 3))
    c.right_angle(D, B, A, size_pt=8)
    for p, label, off, size in [
        (A, "A", (0.0, 0.95), 11.5),
        (B, "B", (-0.85, -0.55), 11.5),
        (C, "C", (0.85, -0.55), 11.5),
        (D, "D", (0.0, -0.95), 11.5),
    ]:
        c.text((p[0] + off[0], p[1] + off[1]), label, size=size, font="ital")
    c.text((2.1, 6.8), "13", size=11, anchor="rm")
    c.text((9.9, 7.0), "15", size=11, anchor="lm")
    c.text((9.3, -0.9), "14", size=11, anchor="mm")
    c.save("fig15_triangle.png")


def axes(c: Canvas, x_ticks=(-5, 5), y_ticks=(-4, 4), xlabel="x", ylabel="y", label_size=8.0):
    x0, x1 = c.xlim
    y0, y1 = c.ylim
    xa, xb = x_ticks
    ya, yb = y_ticks
    # grid only in the labelled data area
    for x in range(xa, xb + 1):
        c.line((x, ya), (x, yb), color=GRID, width_pt=0.55)
    for y in range(ya, yb + 1):
        c.line((xa, y), (xb, y), color=GRID, width_pt=0.55)
    # axes
    c.line((x0, 0), (x1, 0), color=NAVY, width_pt=1.35)
    c.line((0, y0), (0, y1), color=NAVY, width_pt=1.35)
    c.arrowhead(c.P((x1, 0)), (1, 0), size_pt=7)
    c.arrowhead(c.P((0, y1)), (0, 1), size_pt=7)
    c.text((xb + 0.55, 0.45), xlabel, size=10, anchor="mm", font="ital")
    c.text((0.55, yb + 0.55), ylabel, size=10, anchor="mm", font="ital")
    c.text((0.30, 0.30), "O", size=7.8, anchor="lm")
    # ticks/labels
    for x in range(xa, xb + 1):
        if x != 0:
            c.line((x, -0.08), (x, 0.08), color=NAVY, width_pt=0.9)
            c.raw_text((c.X(x), c.Y(0) + pt_px(11)), str(x), size=label_size, anchor="mt")
    for y in range(ya, yb + 1):
        if y != 0:
            c.line((-0.08, y), (0.08, y), color=NAVY, width_pt=0.9)
            if y != -1:
                c.raw_text((c.X(0) - pt_px(9), c.Y(y)), str(y), size=label_size, anchor="rm")


def ticks_only(c: Canvas, x_ticks=(-5, 5), y_ticks=(-4, 4), label_size=7.6):
    xa, xb = x_ticks
    ya, yb = y_ticks
    for x in range(xa, xb + 1):
        if x != 0:
            c.raw_text((c.X(x), c.Y(0) + pt_px(11)), str(x), size=label_size, anchor="mt")
    for y in range(ya, yb + 1):
        if y != 0:
            c.raw_text((c.X(0) - pt_px(9), c.Y(y)), str(y), size=label_size, anchor="rm")


def fig16():
    c = Canvas(6.6, 5.5, (-5.8, 5.9), (-4.5, 4.9))
    axes(c, x_ticks=(-5, 5), y_ticks=(-4, 4), label_size=7.6)
    A, B, C = (-5, 1), (-2, -3), (-1, 1)
    # translucent fill
    overlay = Image.new("RGBA", c.img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.polygon([c.P(A), c.P(B), c.P(C)], fill=(31, 58, 95, 26))
    c.img = Image.alpha_composite(c.img, overlay)
    c.d = ImageDraw.Draw(c.img)
    c.poly([A, B, C], outline=NAVY, fill=None, width_pt=1.9)
    for p, label, off in [
        (A, "A", (-0.58, 0.45)),
        (B, "B", (-0.55, -0.50)),
        (C, "C", (0.45, 0.50)),
    ]:
        c.dot(p, color=NAVY, r_pt=2.3)
        c.text((p[0] + off[0], p[1] + off[1]), label, size=11, font="ital")
    ticks_only(c, x_ticks=(-5, 5), y_ticks=(-4, 4), label_size=7.6)
    c.save("fig16_coordinate.png")


def fig17():
    c = Canvas(6.0, 5.6, (-1.0, 12.4), (-1.4, 10.8))
    B, C, A = (0, 0), (4, 0), (0, 3)
    D = (4 + 12 * 3 / 5, 12 * 4 / 5)  # (11.2, 9.6)
    # translucent fill
    overlay = Image.new("RGBA", c.img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.polygon([c.P(A), c.P(B), c.P(C), c.P(D)], fill=(31, 58, 95, 22))
    c.img = Image.alpha_composite(c.img, overlay)
    c.d = ImageDraw.Draw(c.img)
    c.poly([A, B, C, D], outline=NAVY, fill=None, width_pt=1.9)
    c.right_angle(B, A, C, size_pt=9)
    for p, label, off in [
        (A, "A", (-0.55, 0.55)),
        (B, "B", (-0.58, -0.55)),
        (C, "C", (0.55, -0.55)),
        (D, "D", (0.55, 0.55)),
    ]:
        c.dot(p, color=NAVY, r_pt=2.4)
        c.text((p[0] + off[0], p[1] + off[1]), label, size=11.5, font="ital")
    c.text((-0.55, 1.55), "3", size=11, anchor="rm")
    c.text((2.0, -0.55), "4", size=11, anchor="mm")
    c.text((8.25, 5.3), "12", size=11, anchor="lm")
    c.text((5.2, 7.1), "13", size=11, anchor="rm")
    c.save("fig17_quadrilateral.png")


def fig19():
    c = Canvas(5.0, 5.9, (-1.2, 9.2), (-1.2, 11.2))
    A, B, C, D = (0, 10), (8, 10), (8, 0), (0, 0)
    E, F = (5, 0), (8, 4)
    c.poly([A, B, C, D], outline=NAVY, fill=None, width_pt=1.9)
    c.line(A, E, color=GOLD, width_pt=1.6, dash=(6, 3))
    c.line(A, F, color=NAVY_SOFT, width_pt=1.2, dash=(3, 3))
    c.line(E, F, color=NAVY_SOFT, width_pt=1.2, dash=(3, 3))
    for p, label, off in [
        (A, "A", (-0.55, 0.55)),
        (B, "B", (0.55, 0.55)),
        (C, "C", (0.55, -0.55)),
        (D, "D", (-0.55, -0.55)),
        (E, "E", (0.0, -0.80)),
        (F, "F", (0.60, 0.0)),
    ]:
        c.dot(p, color=NAVY, r_pt=2.3)
        c.text((p[0] + off[0], p[1] + off[1]), label, size=11, font="ital")
    c.text((4.0, 10.55), "8", size=11, anchor="mm")
    c.text((8.55, 5.2), "10", size=11, anchor="lm")
    c.save("fig19_folding.png")


def fig21():
    c = Canvas(6.1, 5.0, (-0.5, 8.3), (-0.7, 5.6))
    # projection: x right, y depth up-right, z up
    def pr(x, y, z):
        return (x + 0.55 * y, z + 0.35 * y)

    A = (0, 0, 0)
    F = (5, 0, 0)
    G = (5, 0, 3)
    H = (0, 0, 3)
    B = (5, 4, 3)
    Cback = (5, 4, 0)
    Dback = (0, 4, 0)
    Eback = (0, 4, 3)

    # hidden edges first
    for p, q in [(A, Dback), (Dback, Cback), (Dback, Eback), (Cback, B)]:
        c.line(pr(*p), pr(*q), color=MID, width_pt=1.0, dash=(3, 3))
    # faces
    front = [A, F, G, H]
    top = [H, G, B, Eback]
    side = [F, Cback, B, G]
    overlay = Image.new("RGBA", c.img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.polygon([c.P(pr(*p)) for p in front], fill=(255, 255, 255, 255))
    od.polygon([c.P(pr(*p)) for p in top], fill=(234, 241, 247, 255))
    od.polygon([c.P(pr(*p)) for p in side], fill=(218, 230, 240, 255))
    c.img = Image.alpha_composite(c.img, overlay)
    c.d = ImageDraw.Draw(c.img)
    for face in (front, top, side):
        c.poly([pr(*p) for p in face], outline=NAVY, fill=None, width_pt=1.7)
    # visible edges that may be covered
    for p, q in [(A, F), (F, G), (G, H), (H, A), (H, Eback), (Eback, B), (B, G), (F, Cback), (Cback, B)]:
        c.line(pr(*p), pr(*q), color=NAVY, width_pt=1.7)
    c.dot(pr(*A), color=GOLD, r_pt=3.0)
    c.dot(pr(*B), color=TERRACOTTA, r_pt=3.0)
    c.text((pr(*A)[0] - 0.55, pr(*A)[1] - 0.55), "A", size=11.5, font="ital")
    c.text((pr(*B)[0] + 0.45, pr(*B)[1] + 0.40), "B", size=11.5, font="ital")
    c.text((2.5, -0.55), "5 cm", size=9.5, anchor="mm")
    c.text((6.6, 0.55), "4 cm", size=9.5, anchor="lm")
    c.text((5.55, 1.55), "3 cm", size=9.5, anchor="lm")
    c.save("fig21_cuboid.png")


def fig22():
    c = Canvas(7.1, 6.6, (-4.5, 8.0), (-1.6, 9.8))
    axes(c, x_ticks=(-4, 7), y_ticks=(-1, 9), label_size=7.4)
    # l1: y=x+2
    c.line((-4.0, -2.0), (7.0, 9.0), color=NAVY, width_pt=1.9)
    # l2: y=-2x+8, clip to y range -1..9 => x from 4.5 to -0.5
    c.line((-0.45, 8.9), (4.45, -0.9), color=TERRACOTTA, width_pt=1.9)
    pts = [("A", (-2, 0), (-0.55, 0.55)), ("B", (0, 2), (-0.55, 0.50)), ("C", (4, 0), (0.55, 0.55)), ("D", (0, 8), (0.45, 0.45)), ("P", (2, 4), (0.35, 0.85))]
    for name, p, off in pts:
        c.dot(p, color=NAVY, r_pt=2.6)
        c.text((p[0] + off[0], p[1] + off[1]), name, size=11, font="ital")
    c.sub_label((7.25, 9.25), "l", "1", size=10.5, color=NAVY)
    c.sub_label((3.70, 1.45), "l", "2", size=10.5, color=TERRACOTTA)
    c.save("fig22_lines.png")


if __name__ == "__main__":
    fig15()
    fig16()
    fig17()
    fig19()
    fig21()
    fig22()

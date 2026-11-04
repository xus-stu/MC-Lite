#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""diskbench.py — 磁盘 / U 盘实测读写速度，测完自动生成图表。

零第三方依赖：测速与绘图都用标准库，最后用系统自带工具把 SVG 转成 PNG
（macOS: qlmanage + sips；Linux: rsvg-convert，若已安装）。

示例：
    python3 diskbench.py "/Volumes/SANDISK USB"            # 每项 60 秒
    python3 diskbench.py /Volumes/XXX --duration 120       # 每项 120 秒
    python3 diskbench.py /Volumes/XXX --duration 1000      # 长测
    python3 diskbench.py . --duration 10 --max-write-gib 2 # 快速冒烟

说明：读测试只读一遍刚写入的数据（不重复读），避免系统把数据缓存到内存后
把速度算高；macOS 上用 fcntl(F_NOCACHE) 关闭文件缓存。
"""

import argparse
import fcntl
import html
import json
import math
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time

MiB = 1024 * 1024
GiB = 1024 * MiB
F_NOCACHE = 48                      # macOS: 关闭该文件的数据缓存
WRITE_BLOCK = 1 * MiB
READ_BLOCK = 8 * MiB
RANDOM_BLOCK = 4096

FORBIDDEN = {"/", "/System", "/Library", "/Applications", "/usr", "/bin",
             "/sbin", "/etc", "/var", "/private", "/dev"}


# --------------------------------------------------------------------------
# 基础工具
# --------------------------------------------------------------------------
def now():
    return time.perf_counter()


def mbps(nbytes, seconds):
    return nbytes / max(seconds, 1e-9) / 1e6


def gb(nbytes):
    return nbytes / 1e9


def open_direct(path, flags):
    fd = os.open(path, flags, 0o600)
    if sys.platform == "darwin":
        try:
            fcntl.fcntl(fd, F_NOCACHE, 1)
        except OSError:
            pass
    elif sys.platform.startswith("linux"):
        try:
            os.posix_fadvise(fd, 0, 0, os.POSIX_FADV_DONTNEED)
        except (AttributeError, OSError):
            pass
    return fd


def fstype_of(path):
    """从 mount 表里找出该路径所属的文件系统类型。"""
    try:
        out = subprocess.run(["mount"], capture_output=True, text=True,
                             check=False).stdout
    except OSError:
        return ""
    real = os.path.realpath(path)
    best, best_len = "", -1
    for line in out.splitlines():
        if " on " not in line:
            continue
        try:
            mp = line.split(" on ", 1)[1].split(" (")[0].strip()
            tail = line.rsplit("(", 1)[1]
            fstype = tail.split(",")[0].strip()
            if " type " in tail:                      # Linux: "/dev/x on /mnt type vfat (...)"
                fstype = tail.split(" type ", 1)[1].split(",")[0].strip()
        except IndexError:
            continue
        if real == mp or real.startswith(mp.rstrip("/") + "/"):
            if len(mp) > best_len:
                best, best_len = fstype, len(mp)
    return best


def is_fat(fstype):
    return fstype.lower() in ("msdos", "vfat", "fat32", "fat")


def human_rate(v):
    return f"{v:7.1f} MB/s"


# --------------------------------------------------------------------------
# 采样器 / 进度输出
# --------------------------------------------------------------------------
class Recorder:
    def __init__(self, phase, on_tick=None):
        self.phase = phase
        self.samples = []
        self.on_tick = on_tick
        self.t0 = self.last = now()
        self.n = self.last_n = 0

    def add(self, n):
        self.n += n
        t = now()
        if t - self.last >= 1.0:
            v = mbps(self.n - self.last_n, t - self.last)
            self.samples.append((round(t - self.t0, 1), round(v, 1)))
            self.last, self.last_n = t, self.n
            if self.on_tick:
                self.on_tick(self.phase, v, t - self.t0)

    @property
    def elapsed(self):
        return now() - self.t0


# --------------------------------------------------------------------------
# 测速
# --------------------------------------------------------------------------
class DiskBench:
    def __init__(self, target, duration, rand_duration, seg_bytes, max_write,
                 on_tick=None):
        self.dir = target
        self.duration = duration
        self.rand_duration = rand_duration
        self.seg_bytes = seg_bytes
        self.max_write = max_write
        self.on_tick = on_tick
        self.should_stop = lambda: False
        self.prefix = f".diskbench-{os.getpid()}"
        self.segments = []
        self.fd = None
        self.seg_size = 0

    # -- 分段文件：避开 FAT32 单文件 4 GiB 限制 ----------------------------
    def _new_segment(self):
        path = os.path.join(self.dir, f"{self.prefix}-{len(self.segments):03d}.bin")
        self.fd = open_direct(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC)
        self.segments.append(path)
        self.seg_size = 0

    def _close_segment(self):
        if self.fd is not None:
            try:
                os.fsync(self.fd)
            finally:
                os.close(self.fd)
                self.fd = None

    def cleanup(self):
        try:
            self._close_segment()
        except OSError:
            pass
        for p in list(self.segments):
            for f in (p, os.path.join(self.dir, "._" + os.path.basename(p))):
                try:
                    os.remove(f)
                except OSError:
                    pass
        self.segments = []

    # -- 顺序写入 ---------------------------------------------------------
    def write_phase(self):
        buf = os.urandom(WRITE_BLOCK)
        rec = Recorder("顺序写入", self.on_tick)
        total = 0
        self._new_segment()
        while True:
            if self.should_stop() or rec.elapsed >= self.duration or total >= self.max_write:
                break
            if self.seg_size + WRITE_BLOCK > self.seg_bytes:
                self._close_segment()
                self._new_segment()
            sent, view = 0, memoryview(buf)
            while sent < len(buf):
                sent += os.write(self.fd, view[sent:])
            total += len(buf)
            self.seg_size += len(buf)
            rec.add(len(buf))
        self._close_segment()
        dt = max(rec.elapsed, 1e-9)
        return {"seconds": round(dt, 2), "bytes": total,
                "MBps": round(mbps(total, dt), 1), "samples": rec.samples}

    # -- 顺序读取（每个字节只读一次，避免缓存干扰）-------------------------
    def read_phase(self):
        rec = Recorder("顺序读取", self.on_tick)
        total = 0
        for path in list(self.segments):
            if rec.elapsed >= self.duration:
                break
            fd = open_direct(path, os.O_RDONLY)
            try:
                while rec.elapsed < self.duration and not self.should_stop():
                    b = os.read(fd, READ_BLOCK)
                    if not b:
                        break
                    total += len(b)
                    rec.add(len(b))
            finally:
                os.close(fd)
        dt = max(rec.elapsed, 1e-9)
        return {"seconds": round(dt, 2), "bytes": total,
                "MBps": round(mbps(total, dt), 1), "samples": rec.samples}

    # -- 4K 随机读写（在最后一段未读过的数据上做）-------------------------
    def random_phase(self, write):
        if not self.segments:
            return {"IOPS": 0, "MBps": 0.0, "ops": 0}
        seg = self.segments[-1]
        size = os.path.getsize(seg)
        if size < RANDOM_BLOCK * 64:
            return {"IOPS": 0, "MBps": 0.0, "ops": 0}
        max_slot = (size - RANDOM_BLOCK) // RANDOM_BLOCK
        buf = os.urandom(RANDOM_BLOCK)
        ops, t0 = 0, now()
        fd = open_direct(seg, os.O_RDWR if write else os.O_RDONLY)
        try:
            while now() - t0 < self.rand_duration and not self.should_stop():
                off = random.randrange(max_slot) * RANDOM_BLOCK
                if write:
                    os.pwrite(fd, buf, off)
                    os.fsync(fd)          # 4K 随机写必须等真正落盘，否则测的是缓存
                else:
                    os.pread(fd, RANDOM_BLOCK, off)
                ops += 1
        finally:
            os.close(fd)
        dt = now() - t0
        rate = mbps(ops * RANDOM_BLOCK, dt)
        return {"IOPS": round(ops / dt), "MBps": round(rate, 2), "ops": ops}


# --------------------------------------------------------------------------
# 图表：纯标准库生成 SVG
# --------------------------------------------------------------------------
W, H = 1440, 880
FONT = ("'PingFang SC','Hiragino Sans GB','Heiti SC','Microsoft YaHei',"
        "'Noto Sans CJK SC',sans-serif")
BG, CARD, INK, MUT, GRID = "#FFFFFF", "#F6F7F9", "#15181D", "#69707A", "#E3E6EA"
BLUE, ORANGE, PURPLE, BROWN, GREEN = "#2F7DD1", "#E8683A", "#7C5CD6", "#C2410C", "#1F9D74"


class Svg:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.parts = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}">',
            f'<rect width="{w}" height="{h}" fill="{BG}"/>']

    def rect(self, x, y, w, h, fill, stroke=None, sw=1, r=0):
        s = (f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}"'
             + (f' rx="{r}"' if r else "") + f' fill="{fill}"')
        if stroke:
            s += f' stroke="{stroke}" stroke-width="{sw}"'
        self.parts.append(s + "/>")

    def line(self, x1, y1, x2, y2, color, sw=1, dash=None):
        s = (f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
             f'stroke="{color}" stroke-width="{sw}"')
        if dash:
            s += f' stroke-dasharray="{dash}"'
        self.parts.append(s + "/>")

    def polyline(self, pts, color, sw=2.5):
        d = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
        self.parts.append(f'<polyline points="{d}" fill="none" stroke="{color}" '
                          f'stroke-width="{sw}" stroke-linejoin="round" '
                          f'stroke-linecap="round"/>')

    def polygon(self, pts, color, opacity=0.12):
        d = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts)
        self.parts.append(f'<polygon points="{d}" fill="{color}" '
                          f'fill-opacity="{opacity}"/>')

    def text(self, x, y, s, size, color=INK, bold=False, anchor="start"):
        weight = ' font-weight="600"' if bold else ""
        self.parts.append(
            f'<text x="{x:.1f}" y="{y:.1f}" font-family="{FONT}" font-size="{size}" '
            f'fill="{color}" text-anchor="{anchor}"{weight}>{html.escape(s)}</text>')

    def render(self):
        return "\n".join(self.parts) + "\n</svg>\n"


def nice_step(maxv, target=4):
    if maxv <= 0:
        return 10
    raw = maxv / target
    mag = 10 ** math.floor(math.log10(raw))
    for m in (1, 2, 2.5, 5, 10):
        if raw <= m * mag:
            return m * mag
    return 10 * mag


def est_text_width(s, size):
    """粗略估算文字宽度，用于把单位排在大数字右边（SVG 无字体度量）。"""
    w = 0.0
    for ch in s:
        if ch in "1":
            w += 0.46
        elif ch.isdigit():
            w += 0.56
        elif ch in ".,":
            w += 0.28
        elif ch == " ":
            w += 0.26
        else:
            w += 0.55
    return w * size


def build_chart(d):
    pw, pr = d["phases"]["write"], d["phases"]["read"]
    rw, rr = d["phases"]["rand_write"], d["phases"]["rand_read"]
    ws = pw["samples"] or [(0.0, round(pw["MBps"], 1))]
    vals = [v for _t, v in ws]
    steady = (sum(vals[1:]) / len(vals[1:])) if len(vals) > 2 else (sum(vals) / max(len(vals), 1))

    s = Svg(W, H)
    s.text(56, 76, f'{d["device"]["label"]}  实测速度', 38, INK, bold=True)
    s.text(56, 120, f'{d["device"]["path"]}  ·  {d["device"]["fstype"]}  ·  '
                    f'可用 {d["device"]["free_gb"]:.0f} GB  ·  '
                    f'关闭文件缓存（F_NOCACHE）', 19, MUT)
    s.text(W - 56, 66, d["meta"]["start"], 17, MUT, anchor="end")

    cards = [
        ("顺序读取", f"{pr['MBps']:.0f}", "MB/s", BLUE),
        ("顺序写入 · 平均", f"{pw['MBps']:.0f}", "MB/s", ORANGE),
        ("顺序写入 · 稳态", f"{steady:.0f}", "MB/s", GREEN),
        ("4K 随机读", f"{rr['IOPS']}", "IOPS", PURPLE),
        ("4K 随机写", f"{rw['IOPS']}", "IOPS", BROWN),
    ]
    gap, top, ch = 14, 156, 118
    cw = (W - 112 - gap * (len(cards) - 1)) / len(cards)
    for i, (lab, val, unit, color) in enumerate(cards):
        x = 56 + i * (cw + gap)
        s.rect(x, top, cw, ch, CARD, r=14)
        s.rect(x, top, 5, ch, color, r=2)
        s.text(x + 26, top + 40, lab, 16, MUT)
        s.text(x + 24, top + 96, val, 40, color, bold=True)
        s.text(x + 24 + est_text_width(val, 40) + 7, top + 96, unit, 16, MUT)

    ptop = 320
    s.rect(56, ptop, W - 112, H - 56 - ptop, "#FFFFFF", stroke=GRID, sw=1, r=16)
    s.text(88, ptop + 52, f"持续写入 {gb(pw['bytes']):.1f} GB 的实时速率（每秒采样）",
           24, INK, bold=True)
    s.text(88, ptop + 82, "全程平稳说明这就是真实持续速度；明显下滑通常意味着缓存耗尽",
           16, MUT)

    x0, x1 = 130, W - 150
    y0, y1 = ptop + 150, H - 130
    ymax = max(vals + [1])
    step = nice_step(ymax)
    ymax = math.ceil(ymax / step) * step

    v = 0
    while v <= ymax:
        y = y1 - (y1 - y0) * v / ymax
        s.line(x0, y, x1, y, GRID, 1)
        s.text(x0 - 14, y + 5, f"{v:g}", 15, MUT, anchor="end")
        v += step
    s.text(x0 - 14, y0 - 24, "MB/s", 15, MUT, anchor="end")

    tmax = max(ws[-1][0], 1.0)
    def px(t):
        return x0 + (x1 - x0) * t / tmax
    def py(val):
        return y1 - (y1 - y0) * min(val, ymax) / ymax

    for k in range(1, 9):
        t = tmax * k / 8
        s.text(px(t), y1 + 26, f"{t:.0f}s", 14, MUT, anchor="middle")

    pts = [(px(t), py(val)) for t, val in ws]
    n = len(pts)
    if pts:
        s.polygon([(pts[0][0], y1)] + pts + [(pts[-1][0], y1)], ORANGE, 0.10)
        s.polyline(pts, ORANGE, 2.8)
        if n <= 80:
            for p in pts:
                s.circle(p[0], p[1], 3.2, ORANGE)

    sy = py(steady)
    s.line(x0, sy, x1, sy, GREEN, 2, dash="7 6")
    s.text(x0 + 6, sy - 10, f"稳态均值 {steady:.0f} MB/s", 16, GREEN, bold=True)

    s.text(x0, y1 + 52, "时间 →（第 1 秒含写缓冲，已剔除）", 14, MUT)
    return s.render()


# --------------------------------------------------------------------------
# Pillow 精确渲染（与原图同一套版式）；不可用时退回 SVG
# --------------------------------------------------------------------------
CJK_FONTS = ["/System/Library/Fonts/Hiragino Sans GB.ttc",
             "/System/Library/Fonts/PingFang.ttc",
             "/System/Library/Fonts/STHeiti Light.ttc",
             "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"]


def _font(size, bold=False):
    from PIL import ImageFont
    for p in CJK_FONTS:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size, index=2 if bold else 0)
            except OSError:
                continue
    return ImageFont.load_default(size=size)


def build_chart_pillow(d):
    from PIL import Image, ImageDraw
    pw, pr = d["phases"]["write"], d["phases"]["read"]
    rw, rr = d["phases"]["rand_write"], d["phases"]["rand_read"]
    ws = pw["samples"] or [(0.0, round(pw["MBps"], 1))]
    vals = [v for _t, v in ws]
    steady = (sum(vals[1:]) / len(vals[1:])) if len(vals) > 2 else pw["MBps"]

    img = Image.new("RGB", (W, H), BG)
    dr = ImageDraw.Draw(img)

    def txt(xy, s, size, color=INK, bold=False, anchor="la"):
        dr.text(xy, s, font=_font(size, bold), fill=color, anchor=anchor)

    txt((56, 44), f'{d["device"]["label"]}  实测速度', 38, INK, True)
    txt((56, 100), f'{d["device"]["path"]}  ·  {d["device"]["fstype"]}  ·  '
                   f'可用 {d["device"]["free_gb"]:.0f} GB  ·  '
                   f'关闭文件缓存（F_NOCACHE）', 20, MUT)
    txt((W - 56, 52), d["meta"]["start"], 18, MUT, anchor="ra")

    cards = [
        ("顺序读取", f"{pr['MBps']:.0f}", "MB/s", BLUE),
        ("顺序写入 · 平均", f"{pw['MBps']:.0f}", "MB/s", ORANGE),
        ("顺序写入 · 稳态", f"{steady:.0f}", "MB/s", GREEN),
        ("4K 随机读", f"{rr['IOPS']}", "IOPS", PURPLE),
        ("4K 随机写", f"{rw['IOPS']}", "IOPS", BROWN),
    ]
    gap, top, ch = 14, 156, 118
    cw = (W - 112 - gap * (len(cards) - 1)) / len(cards)
    for i, (lab, val, unit, color) in enumerate(cards):
        x = 56 + i * (cw + gap)
        dr.rounded_rectangle((x, top, x + cw, top + ch), radius=14, fill=CARD)
        dr.rectangle((x, top, x + 5, top + ch), fill=color)
        txt((x + 26, top + 20), lab, 17, MUT)
        txt((x + 24, top + 46), val, 40, color, True)
        wv = dr.textlength(val, font=_font(40, True))
        txt((x + 26 + wv + 7, top + 68), unit, 16, MUT)

    ptop = 320
    dr.rounded_rectangle((56, ptop, W - 56, H - 56), radius=16,
                         fill="#FFFFFF", outline=GRID, width=1)
    txt((88, ptop + 26), f"持续写入 {gb(pw['bytes']):.1f} GB 的实时速率（每秒采样）",
        24, INK, True)
    txt((88, ptop + 62), "全程平稳说明这就是真实持续速度；明显下滑通常意味着缓存耗尽",
        17, MUT)

    px0, px1 = 130, W - 150
    py0, py1 = ptop + 120, H - 130
    ymax = max(max(vals), 1)
    step = nice_step(ymax)
    ymax = math.ceil(ymax / step) * step

    v = 0
    while v <= ymax:
        y = py1 - (py1 - py0) * v / ymax
        dr.line((px0, y, px1, y), fill=GRID, width=1)
        txt((px0 - 14, y), f"{v:g}", 16, MUT, anchor="rm")
        v += step
    txt((px0 - 14, py0 - 22), "MB/s", 16, MUT, anchor="rm")

    tmax = max(ws[-1][0], 1.0)
    def px(t):
        return px0 + (px1 - px0) * t / tmax
    def py(val):
        return py1 - (py1 - py0) * min(val, ymax) / ymax

    for k in range(1, 9):
        t = tmax * k / 8
        txt((px(t), py1 + 12), f"{t:.0f}s", 15, MUT, anchor="mt")

    pts = [(px(t), py(val)) for t, val in ws]
    n = len(pts)
    dr.polygon([(pts[0][0], py1)] + pts + [(pts[-1][0], py1)], fill="#FDEBE4")
    dr.line(pts, fill=ORANGE, width=3, joint="curve")
    if n <= 80:
        for p in pts:
            dr.ellipse((p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3), fill=ORANGE)

    sy = py(steady)
    for segx in range(int(px0), int(px1), 12):
        dr.line((segx, sy, segx + 6, sy), fill=GREEN, width=2)
    txt((px0 + 8, sy - 8), f"稳态均值 {steady:.0f} MB/s", 17, GREEN, True, anchor="lb")

    txt((px0, py1 + 44), "时间 →（第 1 秒含写缓冲，已剔除）", 16, MUT)
    return img


def render_png(d, png_path):
    try:
        img = build_chart_pillow(d)
    except Exception:
        return False
    img.save(png_path, "PNG")
    return True

def _png_size(path):
    """读 PNG 头拿宽高（避免依赖图像库）。"""
    with open(path, "rb") as f:
        head = f.read(33)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    w = int.from_bytes(head[16:20], "big")
    h = int.from_bytes(head[20:24], "big")
    return w, h


def rasterize(svg_path, png_path, w=W, h=H):
    """SVG -> PNG。macOS 用系统自带 qlmanage + sips，无需安装任何东西。"""
    try:
        if sys.platform == "darwin" and shutil.which("qlmanage") and shutil.which("sips"):
            with tempfile.TemporaryDirectory() as td:
                subprocess.run(["qlmanage", "-t", "-s", str(w), "-o", td, svg_path],
                               check=True, stdout=subprocess.DEVNULL,
                               stderr=subprocess.DEVNULL)
                produced = os.path.join(td, os.path.basename(svg_path) + ".png")
                size = _png_size(produced) if os.path.exists(produced) else None
                if not size:
                    return False
                scale = size[0] / w                      # qlmanage 可能按 2x 渲染
                subprocess.run(["sips", "-c", str(int(h * scale)), str(int(w * scale)),
                                "--cropOffset", "0", "0", produced, "--out", png_path],
                               check=True, stdout=subprocess.DEVNULL,
                               stderr=subprocess.DEVNULL)
                if scale != 1:
                    subprocess.run(["sips", "-z", str(h), str(w), png_path],
                                   check=True, stdout=subprocess.DEVNULL,
                                   stderr=subprocess.DEVNULL)
                return os.path.exists(png_path)
        rsvg = shutil.which("rsvg-convert")
        if rsvg:
            subprocess.run([rsvg, "-w", str(w), "-h", str(h), "-o", png_path, svg_path],
                           check=True, stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL)
            return os.path.exists(png_path)
    except (subprocess.CalledProcessError, OSError):
        return False
    return False


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------
def shlex_quote(s):
    return f'"{s}"' if " " in s else s


def parse_args(argv):
    ap = argparse.ArgumentParser(
        description="磁盘实测读写速度，并生成图表（默认每项 60 秒）",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    ap.add_argument("target", nargs="?",
                    help="要测试的目录，如 \"/Volumes/SANDISK USB\"；留空则列出可用卷")
    ap.add_argument("--duration", type=int, default=60,
                    help="顺序写/读每项持续秒数（5~3600，长测可用 120~1000）")
    ap.add_argument("--random-duration", type=int, default=10,
                    help="4K 随机写/读每项持续秒数")
    ap.add_argument("--max-write-gib", type=float, default=0,
                    help="本次最多写入多少 GiB；0 = 自动（min(50, 可用空间 12%%)）")
    ap.add_argument("--outdir", default=None, help="图表与 JSON 输出目录（默认当前目录）")
    ap.add_argument("--label", default=None, help="图表标题里的设备名")
    ap.add_argument("--keep", action="store_true", help="保留临时测试文件（默认删除）")
    args = ap.parse_args(argv)
    if not 5 <= args.duration <= 3600:
        ap.error("--duration 需在 5~3600 秒之间")
    if not 1 <= args.random_duration <= 600:
        ap.error("--random-duration 需在 1~600 秒之间")
    return args


def list_volumes():
    vols = []
    for base in ("/Volumes", "/mnt", "/media"):
        if os.path.isdir(base):
            for name in sorted(os.listdir(base)):
                p = os.path.join(base, name)
                if os.path.isdir(p) and os.access(p, os.W_OK):
                    vols.append(p)
    return vols


def volume_rows():
    """列出可写的卷：/Volumes 下的挂载点 + 主目录（内置磁盘）。"""
    rows, seen = [], set()
    candidates = list_volumes()
    home = os.path.expanduser("~")
    if os.path.isdir(home):
        candidates.append(home)
    for v in candidates:
        try:
            real = os.path.realpath(v)
            if real in seen or real in FORBIDDEN or real.startswith("/System/"):
                continue
            if not os.access(v, os.W_OK):
                continue
            usage = shutil.disk_usage(v)
        except OSError:
            continue
        seen.add(real)
        rows.append((v, fstype_of(v) or "?", usage.total, usage.free,
                     v == home))
    return rows


def pick_volume():
    rows = volume_rows()
    if not rows:
        print("没有找到可写的磁盘")
        return None
    print("检测到以下可写磁盘：\n")
    for i, (v, ft, tot, free, is_home) in enumerate(rows, 1):
        name = os.path.basename(v.rstrip("/")) or v
        tag = "内置磁盘" if is_home else ""
        print(f"  {i}) {name:<22} {ft:<7} 共 {tot / GiB:7.1f} GiB  "
              f"可用 {free / GiB:7.1f} GiB  {tag}")
    print("  0) 退出")
    if not sys.stdin.isatty():
        print("\n非交互环境，请直接用路径参数指定，例如：")
        print(f'  python3 diskbench.py "{rows[0][0]}"')
        return None
    while True:
        try:
            choice = input("\n请选择要测试的编号: ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return None
        if choice in ("0", "q", ""):
            return None
        if choice.isdigit() and 1 <= int(choice) <= len(rows):
            return rows[int(choice) - 1][0]
        print("输入无效，请输入列表中的编号。")

def main(argv=None):
    args = parse_args(argv)
    if not args.target:
        args.target = pick_volume()
        if not args.target:
            return 1

    target = os.path.abspath(os.path.expanduser(args.target))
    if not os.path.isdir(target):
        print(f"错误：{target} 不是目录")
        return 1
    real = os.path.realpath(target)
    if real in FORBIDDEN or real.startswith("/System/"):
        print(f"错误：拒绝在系统目录 {real} 上测试")
        return 1
    if not os.access(target, os.W_OK):
        print(f"错误：{target} 不可写")
        return 1

    free = shutil.disk_usage(target).free
    fstype = fstype_of(target) or "unknown"
    fat = is_fat(fstype)
    seg_bytes = 2 * GiB if fat else 4 * GiB
    max_write = (args.max_write_gib * GiB if args.max_write_gib > 0
                 else min(50 * GiB, max(free * 0.12, 512 * MiB)))
    if free < seg_bytes + 512 * MiB:
        seg_bytes = max(64 * MiB, free // 4)
    if free < 256 * MiB:
        print(f"错误：{target} 剩余空间不足（{free / GiB:.1f} GiB）")
        return 1

    label = args.label or os.path.basename(target.rstrip("/")) or target
    outdir = os.path.abspath(os.path.expanduser(args.outdir or os.getcwd()))
    os.makedirs(outdir, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    slug = ("".join(c if c.isalnum() or c in "-_" else "-" for c in label)[:32]
            .strip("-") or "disk")

    print(f"目标    : {target}")
    print(f"文件系统: {fstype}    可用: {free / GiB:.1f} GiB")
    print(f"时长    : 顺序写/读各 {args.duration} 秒，4K 随机各 {args.random_duration} 秒")
    print(f"写入上限: {max_write / GiB:.1f} GiB（达到即提前结束）")
    print()

    def on_tick(phase, v, elapsed):
        print(f"\r  {phase}  {v:8.1f} MB/s   {elapsed:5.0f}s", end="", flush=True)

    bench = DiskBench(target, args.duration, args.random_duration, seg_bytes,
                      max_write, on_tick)
    data = {"device": {"label": label, "path": target, "fstype": fstype,
                       "free_gb": round(free / 1e9, 1)},
            "config": {"duration": args.duration,
                       "random_duration": args.random_duration,
                       "seg_bytes": seg_bytes, "max_write": max_write},
            "phases": {}, "meta": {"start": time.strftime("%Y-%m-%d %H:%M:%S")}}

    try:
        w = bench.write_phase()
        data["phases"]["write"] = w
        print(f"\r  顺序写入  {w['MBps']:8.1f} MB/s   {gb(w['bytes']):5.1f} GB / {w['seconds']:.0f}s")

        rr = bench.random_phase(False)          # 随机读：数据还没被顺序读过
        data["phases"]["rand_read"] = rr
        print(f"\r  4K 随机读 {rr['IOPS']:8d} IOPS   {rr['MBps']:5.1f} MB/s")

        rw = bench.random_phase(True)
        data["phases"]["rand_write"] = rw
        print(f"\r  4K 随机写 {rw['IOPS']:8d} IOPS   {rw['MBps']:5.1f} MB/s")

        r = bench.read_phase()
        data["phases"]["read"] = r
        print(f"\r  顺序读取  {r['MBps']:8.1f} MB/s   {gb(r['bytes']):5.1f} GB / {r['seconds']:.0f}s")
    finally:
        if args.keep:
            print(f"\n临时文件保留在：{bench.dir}")
        else:
            bench.cleanup()

    data["meta"]["end"] = time.strftime("%Y-%m-%d %H:%M:%S")
    base = os.path.join(outdir, f"diskbench-{slug}-{stamp}")
    with open(base + ".json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    png_ok = render_png(data, base + ".png")
    if not png_ok:
        with open(base + ".svg", "w", encoding="utf-8") as f:
            f.write(build_chart(data))
        png_ok = rasterize(base + ".svg", base + ".png")

    print()
    print(f"图表：{base}.png" if png_ok else f"图表：{base}.svg（未找到 SVG→PNG 工具）")
    print(f"数据：{base}.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())

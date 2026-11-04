#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""diskbench_gui.py — 图形界面版磁盘测速（macOS 风格，全圆角）。

用法：
    python3 diskbench_gui.py                       # 打开窗口
    python3 diskbench_gui.py --shot /tmp/ui.png    # 截图后自动退出（调试）
"""

import argparse
import json
import math
import os
import queue
import shutil
import subprocess
import sys
import threading
import time
import tkinter as tk
import tkinter.font as tkfont
from tkinter import ttk, messagebox

from PIL import Image, ImageTk

import diskbench as core

# ---- macOS 配色 ----
BG = "#F5F5F7"
CARD = "#FFFFFF"
BORDER = "#E6E6EB"
SHADOW = "#E9E9EE"
INK = "#1D1D1F"
MUT = "#86868B"
BLUE = "#007AFF"
BLUE_DARK = "#0062CC"
GRAY = "#F0F0F3"
GRAY_DARK = "#E3E3E8"
ORANGE = "#E8683A"
GREEN = "#1F9D74"


def results_dir():
    """打包成 .app 后写到 Application Support，从源码运行时写到脚本目录。"""
    if getattr(sys, "frozen", False):
        base = os.path.expanduser("~/Library/Application Support/DiskBench")
    else:
        base = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(base, "results")
    os.makedirs(path, exist_ok=True)
    return path


def squircle_points(x1, y1, x2, y2, r):
    return [x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r, x2, y2 - r, x2, y2,
            x2 - r, y2, x1 + r, y2, x1, y2, x1, y2 - r, x1, y1 + r, x1, y1]


def draw_round_rect(cv, x1, y1, x2, y2, r, **kw):
    r = min(r, (x2 - x1) / 2, (y2 - y1) / 2)
    return cv.create_polygon(squircle_points(x1, y1, x2, y2, r), smooth=True,
                             splinesteps=28, **kw)


def draw_pill(cv, x1, y1, x2, y2, fill, outline=""):
    r = (y2 - y1) / 2
    cv.create_oval(x1, y1, x1 + 2 * r, y2, fill=fill, outline=outline)
    cv.create_oval(x2 - 2 * r, y1, x2, y2, fill=fill, outline=outline)
    cv.create_rectangle(x1 + r, y1, x2 - r, y2, fill=fill, outline=fill)


class RoundedCard(tk.Canvas):
    """带阴影的白色圆角卡片。内容放进 .inner。"""

    def __init__(self, master, radius=18, fill=CARD, outer=BG, border=BORDER,
                 shadow=SHADOW):
        super().__init__(master, bg=outer, highlightthickness=0, bd=0)
        self.r, self.fill, self.border, self.shadow = radius, fill, border, shadow
        self.inner = tk.Frame(self, bg=fill)
        self._win = self.create_window(1, 1, window=self.inner, anchor="nw")
        self.bind("<Configure>", self._redraw)

    def _redraw(self, _evt=None):
        w, h = self.winfo_width(), self.winfo_height()
        if w < 8 or h < 8:
            return
        self.delete("bg")
        self.delete("mask")
        x1, y1, x2, y2, r = 1, 1, w - 1, h - 1, self.r
        # 卡片底色（内部 Frame 是矩形，会盖住圆角，所以最后再用遮罩补回）
        draw_round_rect(self, x1, y1, x2, y2, r, fill=self.fill, outline="", tags="bg")
        self.tag_lower("bg")
        self.itemconfigure(self._win, width=max(1, w - 2), height=max(1, h - 2))
        # 四角遮罩
        for cx, cy, a0 in ((x1 + r, y1 + r, 180), (x2 - r, y1 + r, 270),
                           (x2 - r, y2 - r, 0), (x1 + r, y2 - r, 90)):
            corner = {180: (x1, y1), 270: (x2, y1), 0: (x2, y2), 90: (x1, y2)}[a0]
            pts = [corner]
            for i in range(11):
                a = math.radians(a0 + 90 * i / 10)
                pts += [cx + r * math.cos(a), cy + r * math.sin(a)]
            self.create_polygon(pts, fill=self.cget("bg"), outline="", tags="mask")
        # 精确圆弧描边
        for bx1, by1, st in ((x1, y1, 90), (x2 - 2 * r, y1, 0),
                             (x2 - 2 * r, y2 - 2 * r, 270), (x1, y2 - 2 * r, 180)):
            self.create_arc(bx1, by1, bx1 + 2 * r, by1 + 2 * r, start=st, extent=90,
                            style=tk.ARC, outline=self.border, tags="mask")
        for coords in ((x1 + r, y1, x2 - r, y1), (x2, y1 + r, x2, y2 - r),
                       (x1 + r, y2, x2 - r, y2), (x1, y1 + r, x1, y2 - r)):
            self.create_line(*coords, fill=self.border, tags="mask")


class RoundedButton(tk.Canvas):
    def __init__(self, master, text, command, fill=BLUE, hover=BLUE_DARK, fg="white",
                 font=None, height=44, radius=10, bg=CARD):
        super().__init__(master, height=height, bg=bg, highlightthickness=0, bd=0,
                         cursor="hand2")
        self.text, self.command = text, command
        self.fill0, self.hover, self.fg = fill, hover, fg
        self.font, self.radius = font, radius
        self.hovering = False
        self.bind("<Configure>", lambda e: self._draw())
        self.bind("<Button-1>", self._press)
        self.bind("<ButtonRelease-1>", self._release)
        self.bind("<Enter>", self._enter)
        self.bind("<Leave>", self._leave)

    def _draw(self):
        self.delete("all")
        w, h = self.winfo_width(), self.winfo_height()
        if w < 4 or h < 4:
            return
        fill = self.hover if self.hovering else self.fill0
        draw_round_rect(self, 0.5, 0.5, w - 0.5, h - 0.5, self.radius, fill=fill,
                        outline="")
        self.create_text(w / 2, h / 2 - 1, text=self.text, fill=self.fg, font=self.font)

    def _press(self, _e):
        self.hovering = True
        self._draw()

    def _release(self, _e):
        self.hovering = False
        self._draw()
        if self.command:
            self.command()

    def _enter(self, _e):
        self.hovering = True
        self._draw()

    def _leave(self, _e):
        self.hovering = False
        self._draw()

    def set_style(self, text, fill=None, hover=None, fg=None):
        self.text = text
        if fill:
            self.fill0 = fill
        if hover:
            self.hover = hover
        if fg:
            self.fg = fg
        self._draw()


class Segmented(tk.Canvas):
    """macOS 风格分段选择器。"""

    def __init__(self, master, options, value, command, font=None, height=30,
                 bg=CARD):
        super().__init__(master, height=height, bg=bg, highlightthickness=0, bd=0,
                         cursor="hand2")
        self.options = list(options)
        self.value = value
        self.command = command
        self.font = font
        self.bind("<Configure>", lambda e: self._draw())
        self.bind("<Button-1>", self._click)

    def _draw(self):
        self.delete("all")
        w, h = self.winfo_width(), self.winfo_height()
        if w < 8 or h < 8:
            return
        draw_round_rect(self, 0.5, 0.5, w - 0.5, h - 0.5, h / 2 - 1, fill="#EDEDF0",
                        outline="")
        n = len(self.options)
        seg = (w - 4) / n
        try:
            idx = self.options.index(self.value)
        except ValueError:
            idx = 0
        x = 2 + idx * seg
        draw_round_rect(self, x, 2, x + seg, h - 2, (h - 4) / 2, fill="#FFFFFF",
                        outline="#DCDCE1", width=1)
        for k, opt in enumerate(self.options):
            cx = 2 + k * seg + seg / 2
            self.create_text(cx, h / 2, text=str(opt),
                             font=(self.font[0], 11, "bold" if k == idx else "normal"),
                             fill=INK if k == idx else MUT)

    def _click(self, evt):
        w = self.winfo_width()
        n = len(self.options)
        idx = int((evt.x - 2) / max((w - 4) / n, 1))
        idx = max(0, min(n - 1, idx))
        self.value = self.options[idx]
        self._draw()
        if self.command:
            self.command(self.value)

    def set_enabled(self, on):
        self.configure(cursor="hand2" if on else "arrow")
        self._enabled = on


class RoundedProgress(tk.Canvas):
    def __init__(self, master, height=6, bg=CARD, track="#E9E9ED", fill=BLUE):
        super().__init__(master, height=height, bg=bg, highlightthickness=0, bd=0)
        self.track, self.fillc = track, fill
        self.pct = 0.0
        self.bind("<Configure>", lambda e: self._draw())

    def set(self, pct):
        self.pct = max(0.0, min(100.0, pct))
        self._draw()

    def _draw(self):
        self.delete("all")
        w, h = self.winfo_width(), self.winfo_height()
        if w < 8 or h < 4:
            return
        draw_pill(self, 0, 0, w, h, self.track)
        fw = w * self.pct / 100
        if fw > h:
            draw_pill(self, 0, 0, fw, h, self.fillc)


class InfoBox(tk.Canvas):
    """圆角浅灰信息盒，填充左侧卡片空白。"""

    def __init__(self, master, lines, font=None, bg=CARD, fill="#F7F7FA"):
        super().__init__(master, bg=bg, highlightthickness=0)
        self.lines, self.font, self.fill = lines, font, fill
        self.bind("<Configure>", lambda e: self._draw())

    def _draw(self):
        self.delete("all")
        w, h = self.winfo_width(), self.winfo_height()
        if w < 8 or h < 8:
            return
        draw_round_rect(self, 0.5, 0.5, w - 0.5, h - 0.5, 12, fill=self.fill, outline="")
        y = 16
        for text, bold in self.lines:
            self.create_text(16, y, text=text, anchor="nw",
                             fill=INK if bold else MUT,
                             font=(self.font[0], 10, "bold" if bold else "normal"))
            y += 23


class App:
    def __init__(self, root, shot=None):
        self.root = root
        self.shot = shot
        self.q = queue.Queue()
        self.stop_event = threading.Event()
        self.worker = None
        self.cur_disk = None
        self.disks = []
        self.samples = []
        self.duration = 60
        self.result_png = None
        self.running = False

        fams = set(tkfont.families())
        self.font = ("PingFang SC" if "PingFang SC" in fams
                     else "Helvetica Neue" if "Helvetica Neue" in fams else "Helvetica")

        root.title("磁盘测速")
        root.after(200, self._to_front)
        root.geometry("1100x690")
        root.minsize(1040, 660)
        root.configure(bg=BG)

        self._style()
        self._build()
        self.refresh_disks()
        self.root.after(80, self._poll)
        if shot:
            self.root.after(2200, self._take_shot)

    def _to_front(self):
        self.root.lift()
        self.root.attributes("-topmost", True)
        self.root.after(300, lambda: self.root.attributes("-topmost", False))
        self.root.focus_force()

    # ---------------------------------------------------------------- 样式
    def _style(self):
        st = ttk.Style(self.root)
        try:
            st.theme_use("clam")
        except tk.TclError:
            pass
        st.configure("Treeview", rowheight=30, font=(self.font, 12), background=CARD,
                     fieldbackground=CARD, borderwidth=0, relief="flat")
        st.configure("Treeview.Heading", font=(self.font, 10), background=CARD,
                     foreground=MUT, relief="flat", borderwidth=0)
        st.map("Treeview", background=[("selected", "#E8F1FF")],
               foreground=[("selected", INK)])
        st.layout("Treeview", [("Treeview.treearea", {"sticky": "nswe"})])

    # ---------------------------------------------------------------- 布局
    def _build(self):
        head = tk.Frame(self.root, bg=BG)
        head.pack(fill="x", padx=30, pady=(20, 6))
        tk.Label(head, text="磁盘测速", bg=BG, fg=INK,
                 font=(self.font, 26, "bold")).pack(anchor="w")
        tk.Label(head, text="选一块磁盘，设置每项时长，测完自动生成图表",
                 bg=BG, fg=MUT, font=(self.font, 12)).pack(anchor="w", pady=(3, 0))

        body = tk.Frame(self.root, bg=BG)
        body.pack(fill="both", expand=True, padx=28, pady=(8, 24))
        body.columnconfigure(0, minsize=350, weight=0)
        body.columnconfigure(1, weight=1)
        body.rowconfigure(0, weight=1)

        # ---------------- 左卡片 ----------------
        left = RoundedCard(body)
        left.grid(row=0, column=0, sticky="nsew", padx=(0, 16))
        lf = tk.Frame(left.inner, bg=CARD)
        lf.pack(fill="both", expand=True, padx=20, pady=18)
        lf.columnconfigure(0, weight=1)

        top = tk.Frame(lf, bg=CARD)
        top.grid(row=0, column=0, sticky="ew")
        top.columnconfigure(0, weight=1)
        tk.Label(top, text="选择磁盘", bg=CARD, fg=INK,
                 font=(self.font, 13, "bold")).grid(row=0, column=0, sticky="w")
        self.refresh_btn = RoundedButton(top, "刷新", self.refresh_disks, fill=GRAY,
                                         hover=GRAY_DARK, fg=INK,
                                         font=(self.font, 10), height=26, radius=8)
        self.refresh_btn.grid(row=0, column=1, sticky="e")
        self.refresh_btn.configure(width=56)

        cols = ("name", "fs", "free")
        self.tree = ttk.Treeview(lf, columns=cols, show="headings", height=4,
                                 selectmode="browse")
        for c, t, w, stretch in (("name", "磁盘", 130, True), ("fs", "格式", 54, False),
                                 ("free", "可用 / 总容量", 106, False)):
            self.tree.heading(c, text=t, anchor="w")
            self.tree.column(c, width=w, anchor="w", stretch=stretch)
        self.tree.grid(row=1, column=0, sticky="ew", pady=(10, 0))
        self.tree.bind("<<TreeviewSelect>>", self._on_select)

        tk.Label(lf, text="每项时长", bg=CARD, fg=INK,
                 font=(self.font, 13, "bold")).grid(row=2, column=0, sticky="w",
                                                    pady=(20, 0))
        self.dur_var = tk.StringVar(value="60")
        self.seg = Segmented(lf, ("10", "60", "120", "300", "1000"), "60",
                             self._on_duration, font=(self.font, 11))
        self.seg.grid(row=3, column=0, sticky="ew", pady=(8, 0))
        self.hint = tk.Label(lf, text="", bg=CARD, fg=MUT, font=(self.font, 10))
        self.hint.grid(row=4, column=0, sticky="w", pady=(8, 0))

        self.info = InfoBox(lf, [("本次测试", True),
                                 ("顺序写入 / 顺序读取", False),
                                 ("4K 随机读写", False),
                                 ("自动绕过系统缓存", False)], font=self.font)
        self.info.grid(row=5, column=0, sticky="nsew", pady=(16, 0))
        lf.rowconfigure(5, weight=1)

        self.start_btn = RoundedButton(lf, "开始测试", self.toggle,
                                       font=(self.font, 14, "bold"), height=46,
                                       radius=10)
        self.start_btn.grid(row=6, column=0, sticky="ew", pady=(16, 0))

        self.status = tk.Label(lf, text="就绪", bg=CARD, fg=MUT, font=(self.font, 10),
                               anchor="w", justify="left", wraplength=300)
        self.status.grid(row=7, column=0, sticky="ew", pady=(10, 0))

        tk.Label(lf, text="测试期间请不要往这块盘拷文件，否则速度和曲线都会被拉低。",
                 bg=CARD, fg=MUT, font=(self.font, 10), anchor="w", justify="left",
                 wraplength=300).grid(row=8, column=0, sticky="ew", pady=(8, 0))

        # ---------------- 右卡片 ----------------
        right = RoundedCard(body)
        right.grid(row=0, column=1, sticky="nsew")
        rf = tk.Frame(right.inner, bg=CARD)
        rf.pack(fill="both", expand=True, padx=22, pady=18)
        rf.columnconfigure(0, weight=1)
        rf.rowconfigure(2, weight=1)

        info = tk.Frame(rf, bg=CARD)
        info.grid(row=0, column=0, sticky="ew")
        info.columnconfigure(1, weight=1)
        self.phase_lbl = tk.Label(info, text="等待开始", bg=CARD, fg=INK,
                                  font=(self.font, 14, "bold"))
        self.phase_lbl.grid(row=0, column=0, sticky="w")
        self.sub_lbl = tk.Label(info, text="", bg=CARD, fg=MUT, font=(self.font, 11))
        self.sub_lbl.grid(row=0, column=1, sticky="e")

        speed = tk.Frame(rf, bg=CARD)
        speed.grid(row=1, column=0, sticky="ew", pady=(4, 0))
        self.speed_lbl = tk.Label(speed, text="0.0", bg=CARD, fg=ORANGE,
                                  font=(self.font, 48, "bold"))
        self.speed_lbl.pack(side="left")
        tk.Label(speed, text="MB/s", bg=CARD, fg=MUT,
                 font=(self.font, 13)).pack(side="left", anchor="s",
                                            pady=(0, 14), padx=(6, 0))

        self.canvas = tk.Canvas(rf, bg=CARD, highlightthickness=0, height=248, width=600)
        self.canvas.grid(row=2, column=0, sticky="nsew", pady=(10, 0))
        self.canvas.bind("<Configure>", lambda e: self._draw_curve())

        self.progress = RoundedProgress(rf, height=6)
        self.progress.grid(row=3, column=0, sticky="ew", pady=(14, 0))

    # ---------------------------------------------------------------- 数据
    def _on_duration(self, value):
        self.dur_var.set(value)
        self._update_hint()

    def _update_hint(self):
        try:
            d = int(self.dur_var.get())
        except ValueError:
            return
        self.hint.config(text=f"预计约 {d * 2 + 20} 秒（含 4K 随机 20 秒）")

    def refresh_disks(self):
        self.disks = core.volume_rows()
        self.tree.delete(*self.tree.get_children())
        for i, (path, fs, total, free, is_home) in enumerate(self.disks):
            name = "内置磁盘（主目录）" if is_home else (os.path.basename(path.rstrip("/")) or path)
            self.tree.insert("", "end", iid=str(i),
                             values=(name, fs, f"{free / core.GiB:.1f} / {total / core.GiB:.0f} GiB"))
        if self.disks:
            self.tree.selection_set("0")
            self.tree.focus("0")
            self.cur_disk = self.disks[0][0]
            self.status.config(text=f"已选择：{self.cur_disk}")
        self._update_hint()

    def _on_select(self, _evt=None):
        sel = self.tree.selection()
        if sel:
            self.cur_disk = self.disks[int(sel[0])][0]
            self.status.config(text=f"已选择：{self.cur_disk}")

    # ---------------------------------------------------------------- 运行
    def toggle(self):
        if self.running:
            self.stop_event.set()
            self.start_btn.set_style("正在停止 …", fill="#C7C7CC", hover="#B8B8BE")
            return
        if not self.cur_disk:
            messagebox.showwarning("没有选择磁盘", "请先在列表中选择要测试的磁盘。")
            return
        try:
            self.duration = max(5, int(self.dur_var.get()))
        except ValueError:
            self.duration = 60

        self.running = True
        self.stop_event.clear()
        self.samples = []
        self.result_png = None
        self.start_btn.set_style("停止测试", fill="#8E8E93", hover="#7C7C82")
        self.tree.config(selectmode="none")
        self.progress.set(0)
        self.speed_lbl.config(text="0.0")
        self.phase_lbl.config(text="准备中 …")
        self.sub_lbl.config(text="")
        self.status.config(text="测试中，请勿操作这块磁盘 …")
        self._draw_curve()

        self.worker = threading.Thread(target=self._run,
                                       args=(self.cur_disk, self.duration), daemon=True)
        self.worker.start()

    def _run(self, target, duration):
        try:
            free = shutil.disk_usage(target).free
            fs = core.fstype_of(target) or "unknown"
            seg = 2 * core.GiB if core.is_fat(fs) else 4 * core.GiB
            max_write = min(50 * core.GiB, max(free * 0.12, 512 * core.MiB))
            if free < seg + 512 * core.MiB:
                seg = max(64 * core.MiB, free // 4)
            label = ("内置磁盘" if target == os.path.expanduser("~")
                     else (os.path.basename(target.rstrip("/")) or target))
            data = {"device": {"label": label, "path": target, "fstype": fs,
                               "free_gb": round(free / 1e9, 1)},
                    "config": {"duration": duration, "random_duration": 10,
                               "seg_bytes": seg, "max_write": max_write},
                    "phases": {}, "meta": {"start": time.strftime("%Y-%m-%d %H:%M:%S")}}

            def on_tick(phase, v, elapsed):
                self.q.put(("tick", phase, v, elapsed, duration))

            bench = core.DiskBench(target, duration, 10, seg, max_write, on_tick)
            bench.should_stop = self.stop_event.is_set
            try:
                self.q.put(("phase", "顺序写入"))
                data["phases"]["write"] = bench.write_phase()
                if not self.stop_event.is_set():
                    self.q.put(("phase", "4K 随机读"))
                    data["phases"]["rand_read"] = bench.random_phase(False)
                if not self.stop_event.is_set():
                    self.q.put(("phase", "4K 随机写"))
                    data["phases"]["rand_write"] = bench.random_phase(True)
                if not self.stop_event.is_set():
                    self.q.put(("phase", "顺序读取"))
                    data["phases"]["read"] = bench.read_phase()
            finally:
                bench.cleanup()

            empty = {"IOPS": 0, "MBps": 0, "ops": 0, "samples": [], "bytes": 0, "seconds": 0}
            for key in ("write", "read", "rand_read", "rand_write"):
                data["phases"].setdefault(key, dict(empty))
            data["meta"]["end"] = time.strftime("%Y-%m-%d %H:%M:%S")
            self.q.put(("done", data))
        except Exception:
            import traceback
            self.q.put(("error", traceback.format_exc()))

    def _poll(self):
        try:
            while True:
                msg = self.q.get_nowait()
                kind = msg[0]
                if kind == "tick":
                    _, phase, v, elapsed, dur = msg
                    self.phase_lbl.config(text=phase)
                    self.speed_lbl.config(text=f"{v:.1f}")
                    self.sub_lbl.config(text=f"{int(elapsed)} 秒 / {dur} 秒")
                    self.progress.set(elapsed / max(dur, 1) * 100)
                    self.samples.append((elapsed, v))
                    self._draw_curve()
                elif kind == "phase":
                    self.phase_lbl.config(text=msg[1] + " …")
                    self.samples = []
                    self.speed_lbl.config(text="0.0")
                    self.sub_lbl.config(text="")
                    self.progress.set(0)
                    self._draw_curve()
                elif kind == "done":
                    self._finish(msg[1])
                elif kind == "error":
                    self._reset()
                    messagebox.showerror("测试出错", msg[1][-1600:])
        except queue.Empty:
            pass
        self.root.after(80, self._poll)

    def _reset(self):
        self.running = False
        self.start_btn.set_style("开始测试", fill=BLUE, hover=BLUE_DARK)
        self.tree.config(selectmode="browse")
        self.progress.set(0)
        self.status.config(text="就绪")

    # ---------------------------------------------------------------- 结果
    def _finish(self, data):
        self._reset()
        outdir = results_dir()
        os.makedirs(outdir, exist_ok=True)
        slug = ("".join(c if c.isalnum() or c in "-_" else "-"
                        for c in data["device"]["label"])[:32].strip("-") or "disk")
        base = os.path.join(outdir, f"diskbench-{slug}-{time.strftime('%Y%m%d-%H%M%S')}")
        with open(base + ".json", "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        if not core.render_png(data, base + ".png"):
            messagebox.showerror("出图失败", "未能生成图片，请确认已安装 Pillow。")
            return
        self.result_png = base + ".png"
        w, r_ = data["phases"]["write"], data["phases"]["read"]
        self.phase_lbl.config(text="测试完成")
        self.speed_lbl.config(text=f"{w['MBps']:.0f}")
        self.sub_lbl.config(text=f"读取 {r_['MBps']:.0f} MB/s · 写入 {w['MBps']:.0f} MB/s")
        self.status.config(text=f"图表已保存：{os.path.basename(self.result_png)}")
        self.show_result()

    def show_result(self):
        if not self.result_png or not os.path.exists(self.result_png):
            return
        win = tk.Toplevel(self.root)
        win.title("测试结果")
        win.configure(bg=CARD)
        img = Image.open(self.result_png)
        sw = min(1000, win.winfo_screenwidth() - 140)
        scale = min(1.0, sw / img.width)
        img = img.resize((int(img.width * scale), int(img.height * scale)), Image.LANCZOS)
        photo = ImageTk.PhotoImage(img)
        lbl = tk.Label(win, image=photo, bg=CARD)
        lbl.image = photo
        lbl.pack(padx=16, pady=16)

        bar = tk.Frame(win, bg=CARD)
        bar.pack(fill="x", padx=16, pady=(0, 16))
        RoundedButton(bar, "打开图片", lambda: subprocess.run(["open", self.result_png]),
                      fill=GRAY, hover=GRAY_DARK, fg=INK, font=(self.font, 11),
                      height=32, radius=8).pack(side="left")
        RoundedButton(bar, "另存为 …", self._save_as, fill=GRAY, hover=GRAY_DARK,
                      fg=INK, font=(self.font, 11), height=32,
                      radius=8).pack(side="left", padx=8)
        RoundedButton(bar, "关闭", win.destroy, fill=GRAY, hover=GRAY_DARK, fg=INK,
                      font=(self.font, 11), height=32, radius=8).pack(side="right")

    def _save_as(self):
        if not self.result_png:
            return
        from tkinter import filedialog
        path = filedialog.asksaveasfilename(defaultextension=".png",
                                            initialfile=os.path.basename(self.result_png),
                                            filetypes=[("PNG 图片", "*.png")])
        if path:
            shutil.copyfile(self.result_png, path)
            self.status.config(text=f"已另存为：{path}")

    # ---------------------------------------------------------------- 曲线
    def _draw_curve(self):
        c = self.canvas
        c.delete("all")
        w = max(c.winfo_width(), 240)
        h = max(c.winfo_height(), 160)
        L, R, T, B = 56, 18, 26, 30
        x1, y1 = w - R, h - B
        dur = max(self.duration, 1)
        vmax = max([x[1] for x in self.samples] + [10])
        step = core.nice_step(vmax, 4)
        vmax = math.ceil(vmax / step) * step

        v = 0
        while v <= vmax:
            y = y1 - (y1 - T) * v / vmax
            c.create_line(L, y, x1, y, fill="#F0F0F3")
            c.create_text(L - 10, y, text=f"{v:g}", anchor="e", fill=MUT,
                          font=(self.font, 9))
            v += step
        c.create_text(L - 10, T - 20, text="MB/s", anchor="w", fill=MUT,
                      font=(self.font, 9))
        c.create_text(x1, h - 6, text="时间（秒）", anchor="se", fill=MUT,
                      font=(self.font, 9))

        if len(self.samples) > 1:
            pts = []
            for t, val in self.samples:
                x = L + (x1 - L) * t / dur
                y = y1 - (y1 - T) * min(val, vmax) / vmax
                pts.extend([x, y])
            c.create_line(*pts, fill=ORANGE, width=2.5, joinstyle="round")
            for i in range(0, len(pts), 2):
                c.create_oval(pts[i] - 2.4, pts[i + 1] - 2.4, pts[i] + 2.4,
                              pts[i + 1] + 2.4, fill=ORANGE, outline="")

    # ---------------------------------------------------------------- 截图
    def _take_shot(self):
        self.root.lift()
        self.root.attributes("-topmost", True)
        self.root.focus_force()
        self.root.update()
        time.sleep(0.9)
        self.root.update_idletasks()
        x, y = self.root.winfo_rootx(), self.root.winfo_rooty()
        w, h = self.root.winfo_width(), self.root.winfo_height()
        subprocess.run(["screencapture", "-x", "-R", f"{x},{y},{w},{h}", self.shot],
                       check=False)
        self.root.after(300, self.root.destroy)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shot", default=None, help="截图保存路径（截完自动退出）")
    ap.add_argument("--selftest", default=None, help="自检：启动后写结果文件并退出")
    args = ap.parse_args()
    root = tk.Tk()
    app = App(root, args.shot)
    if args.selftest:
        def _done():
            with open(args.selftest, "w", encoding="utf-8") as f:
                f.write(f"ok {root.winfo_width()}x{root.winfo_height()} "
                        f"disks={len(app.disks)} frozen={getattr(sys, 'frozen', False)}\n")
            root.destroy()
        root.after(1800, _done)
    root.mainloop()


if __name__ == "__main__":
    main()

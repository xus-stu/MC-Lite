# -*- coding: utf-8 -*-
"""生成《运动会加油稿300则》DOCX。"""
import re
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

from data_cheers import CATEGORY_ORDER, CHEERS

OUT = "/Users/xiaoxu/PycharmProjects/Java/运动会加油稿300则.docx"

NAVY = RGBColor(0x1F, 0x38, 0x64)
INK = RGBColor(0x26, 0x26, 0x26)
GRAY = RGBColor(0x6B, 0x72, 0x80)
TAG_GRAY = RGBColor(0x7F, 0x8F, 0xA6)

CN_NUM = {"200米": "一", "1000米": "二", "跳绳": "三"}


def set_run_font(run, name, size=None, bold=None, color=None):
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.insert(0, rfonts)
    for attr in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(attr), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color


def shade_paragraph(p, fill):
    ppr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    ppr.append(shd)


def add_left_border(p, color="1F3864", size=24, space=6):
    ppr = p._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    left = OxmlElement("w:left")
    left.set(qn("w:val"), "single")
    left.set(qn("w:sz"), str(size))
    left.set(qn("w:space"), str(space))
    left.set(qn("w:color"), color)
    pbdr.append(left)
    ppr.append(pbdr)


def add_bottom_border(p, color="BFC9D9", size=6, space=2):
    ppr = p._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), str(size))
    bottom.set(qn("w:space"), str(space))
    bottom.set(qn("w:color"), color)
    pbdr.append(bottom)
    ppr.append(pbdr)


def disable_snap_to_grid(p):
    ppr = p._p.get_or_add_pPr()
    snap = OxmlElement("w:snapToGrid")
    snap.set(qn("w:val"), "0")
    ppr.append(snap)


def add_page_field(paragraph):
    run_begin = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    run_begin._r.append(fld_begin)

    run_instr = paragraph.add_run()
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE "
    run_instr._r.append(instr)

    run_sep = paragraph.add_run()
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    run_sep._r.append(fld_sep)

    run_result = paragraph.add_run("1")

    run_end = paragraph.add_run()
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run_end._r.append(fld_end)

    return [run_begin, run_instr, run_sep, run_result, run_end]


doc = Document()

# 页面：A4，适中页边距
section = doc.sections[0]
section.page_width = Cm(21.0)
section.page_height = Cm(29.7)
section.top_margin = Cm(2.2)
section.bottom_margin = Cm(2.1)
section.left_margin = Cm(2.4)
section.right_margin = Cm(2.4)
section.header_distance = Cm(1.0)
section.footer_distance = Cm(0.9)

normal = doc.styles["Normal"]
normal.font.name = "宋体"
normal.font.size = Pt(12)
normal.font.color.rgb = INK
normal.element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
normal.paragraph_format.space_before = Pt(0)
normal.paragraph_format.space_after = Pt(0)
normal.paragraph_format.line_spacing = 1.28

# 页眉
header = section.header
hp = header.paragraphs[0]
add_bottom_border(hp)
hp.paragraph_format.space_after = Pt(2)
r = hp.add_run("运动会加油稿")
set_run_font(r, "黑体", 8.5, False, GRAY)
r = hp.add_run("　｜　200米 · 1000米 · 跳绳")
set_run_font(r, "宋体", 8.5, False, GRAY)

# 页脚
footer = section.footer
fp = footer.paragraphs[0]
fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = fp.add_run("— ")
set_run_font(r, "宋体", 9, False, GRAY)
for r in add_page_field(fp):
    set_run_font(r, "宋体", 9, False, GRAY)
r = fp.add_run(" —")
set_run_font(r, "宋体", 9, False, GRAY)

# 标题
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(10)
p.paragraph_format.space_after = Pt(4)
r = p.add_run("运动会加油稿")
set_run_font(r, "黑体", 26, True, NAVY)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(10)
r = p.add_run("200米 · 1000米 · 跳绳　｜　共 300 则")
set_run_font(r, "宋体", 11, False, GRAY)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(6)
r = p.add_run("把呐喊送给赛场，把荣光留给青春。")
set_run_font(r, "楷体", 13, False, RGBColor(0x44, 0x4B, 0x57))

p = doc.add_paragraph()
add_bottom_border(p, color="1F3864", size=8, space=1)
p.paragraph_format.space_after = Pt(2)

# 正文
start = 1
for cat in CATEGORY_ORDER:
    items = CHEERS[cat]
    end = start + len(items) - 1

    hp = doc.add_paragraph()
    hp.paragraph_format.page_break_before = (cat != CATEGORY_ORDER[0])
    hp.paragraph_format.keep_with_next = True
    add_left_border(hp)
    shade_paragraph(hp, "EEF3FA")
    hp.paragraph_format.space_before = Pt(14)
    hp.paragraph_format.space_after = Pt(9)
    hp.paragraph_format.line_spacing = 1.0
    hp.paragraph_format.tab_stops.add_tab_stop(Cm(16.2), WD_TAB_ALIGNMENT.RIGHT)
    r = hp.add_run(f"{CN_NUM[cat]}、{cat}")
    set_run_font(r, "黑体", 16, True, NAVY)
    r = hp.add_run(f"\t第 {start}—{end} 则")
    set_run_font(r, "宋体", 10, False, GRAY)

    for offset, text in enumerate(items, start=start):
        p = doc.add_paragraph()
        p.paragraph_format.keep_together = True
        disable_snap_to_grid(p)
        p.paragraph_format.left_indent = Cm(3.4)
        p.paragraph_format.first_line_indent = Cm(-2.95)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.line_spacing = Pt(18)

        r = p.add_run(f"{offset:03d}")
        set_run_font(r, "黑体", 10, True, NAVY)
        r = p.add_run("　")
        set_run_font(r, "宋体", 10)

        r = p.add_run(f"【{cat}】")
        set_run_font(r, "楷体", 9.5, True, TAG_GRAY)
        r = p.add_run("　")
        set_run_font(r, "宋体", 10)

        r = p.add_run(text)
        set_run_font(r, "宋体", 12, False, INK)

    start = end + 1

# 文档属性
doc.core_properties.title = "运动会加油稿300则"
doc.core_properties.subject = "200米、1000米、跳绳加油稿"
doc.core_properties.author = ""

doc.save(OUT)
print("saved:", OUT)

# 即时自检
doc2 = Document(OUT)
texts = [p.text for p in doc2.paragraphs]
entries = [t for t in texts if re.match(r"^\d{3}\u3000【", t)]
print("paragraphs:", len(texts), "entries:", len(entries))
assert len(entries) == 300
for i, t in enumerate(entries, 1):
    assert t.startswith(f"{i:03d}\u3000"), (i, t)
print("numbering ok, first:", entries[0][:30], "last:", entries[-1][:30])

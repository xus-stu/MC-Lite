# -*- coding: utf-8 -*-
"""生成《奔跑吧，少年》初中运动会加油稿 200 则 DOCX。"""

import re
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_ALIGN_VERTICAL, WD_ROW_HEIGHT_RULE, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

from cheers import SECTIONS

# ---------- 色彩与字体 ----------
GREEN = "1F4D3A"
GREEN_SOFT = "446B59"
ORANGE = "BC6430"
GOLD = "C7B18A"
GOLD_LIGHT = "E7DCC7"
INK = "2A2A2A"
GRAY = "737373"
GRAY_LIGHT = "9B9B9B"
CREAM = "F7F2E8"
RULE = "DED3C1"
WHITE = "FFFFFF"

FONT_CN = "宋体"
FONT_EN = "Times New Roman"

OUT = Path(__file__).with_name("初中运动会加油稿200则.docx")


# ---------- 基础工具 ----------
def rgb(hex_color):
    return RGBColor.from_string(hex_color)


def style_run(run, size=11, color=INK, bold=False, italic=False,
              east=FONT_CN, ascii_font=FONT_EN):
    run.font.name = ascii_font
    run.font.size = Pt(size)
    run.bold = bold
    run.italic = italic
    run.font.color.rgb = rgb(color)
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.get_or_add_rFonts()
    rfonts.set(qn("w:ascii"), ascii_font)
    rfonts.set(qn("w:hAnsi"), ascii_font)
    rfonts.set(qn("w:eastAsia"), east)
    rfonts.set(qn("w:cs"), ascii_font)
    return run


def add_text(p, text, **kwargs):
    run = p.add_run(text)
    style_run(run, **kwargs)
    return run


def set_para_spacing(p, before=0, after=0, line=None, before_cm=None, after_cm=None):
    pf = p.paragraph_format
    if before_cm is not None:
        pf.space_before = Cm(before_cm)
    else:
        pf.space_before = Pt(before)
    if after_cm is not None:
        pf.space_after = Cm(after_cm)
    else:
        pf.space_after = Pt(after)
    if line is not None:
        pf.line_spacing = line


def add_bottom_border(p, color=GOLD, size=6, space=4):
    ppr = p._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), str(size))
    bottom.set(qn("w:space"), str(space))
    bottom.set(qn("w:color"), color)
    pbdr.append(bottom)
    ppr.insert_element_before(
        pbdr,
        "w:shd", "w:tabs", "w:suppressAutoHyphens", "w:kinsoku",
        "w:wordWrap", "w:overflowPunct", "w:topLinePunct", "w:autoSpaceDE",
        "w:autoSpaceDN", "w:bidi", "w:adjustRightInd", "w:snapToGrid",
        "w:spacing", "w:ind", "w:contextualSpacing", "w:mirrorIndents",
        "w:suppressOverlap", "w:jc", "w:textDirection", "w:textAlignment",
        "w:textboxTightWrap", "w:outlineLvl", "w:divId", "w:cnfStyle",
        "w:rPr", "w:sectPr", "w:pPrChange",
    )


def add_top_border(p, color=RULE, size=4, space=4):
    ppr = p._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    top = OxmlElement("w:top")
    top.set(qn("w:val"), "single")
    top.set(qn("w:sz"), str(size))
    top.set(qn("w:space"), str(space))
    top.set(qn("w:color"), color)
    pbdr.append(top)
    ppr.insert_element_before(
        pbdr,
        "w:shd", "w:tabs", "w:suppressAutoHyphens", "w:kinsoku",
        "w:wordWrap", "w:overflowPunct", "w:topLinePunct", "w:autoSpaceDE",
        "w:autoSpaceDN", "w:bidi", "w:adjustRightInd", "w:snapToGrid",
        "w:spacing", "w:ind", "w:contextualSpacing", "w:mirrorIndents",
        "w:suppressOverlap", "w:jc", "w:textDirection", "w:textAlignment",
        "w:textboxTightWrap", "w:outlineLvl", "w:divId", "w:cnfStyle",
        "w:rPr", "w:sectPr", "w:pPrChange",
    )


def set_cell_shading(cell, fill):
    tcpr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    tcpr.insert_element_before(
        shd, "w:noWrap", "w:tcMar", "w:textDirection",
        "w:tcFitText", "w:vAlign", "w:hideMark",
    )


def remove_table_borders(table):
    tblpr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:val"), "nil")
        el.set(qn("w:sz"), "0")
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), "auto")
        borders.append(el)
    tblpr.insert_element_before(
        borders, "w:shd", "w:tblLayout", "w:tblCellMar", "w:tblLook",
        "w:tblCaption", "w:tblDescription",
    )


def set_table_width(table, width_cm):
    tblpr = table._tbl.tblPr
    tblw = tblpr.find(qn("w:tblW"))
    if tblw is None:
        tblw = OxmlElement("w:tblW")
        tblpr.append(tblw)
    tblw.set(qn("w:type"), "dxa")
    tblw.set(qn("w:w"), str(int(Cm(width_cm).twips)))


def add_page_field(p, size=8.5, color=ORANGE):
    p.add_run("  ")
    begin_run = p.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    begin_run._r.append(begin)
    style_run(begin_run, size=size, color=color, bold=True)

    instr_run = p.add_run()
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE \\* ARABIC "
    instr_run._r.append(instr)
    style_run(instr_run, size=size, color=color, bold=True)

    sep_run = p.add_run()
    sep = OxmlElement("w:fldChar")
    sep.set(qn("w:fldCharType"), "separate")
    sep_run._r.append(sep)
    style_run(sep_run, size=size, color=color, bold=True)

    result_run = p.add_run("2")
    style_run(result_run, size=size, color=color, bold=True)

    end_run = p.add_run()
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    end_run._r.append(end)
    style_run(end_run, size=size, color=color, bold=True)


def add_page_break(doc):
    p = doc.add_paragraph()
    set_para_spacing(p, 0, 0, 1.0)
    p.add_run().add_break(WD_BREAK.PAGE)


def start_new_page(doc):
    """在下一页顶端放一个几乎零高度的段落，避免分页符本身溢出成空白页。"""
    p = doc.add_paragraph()
    p.paragraph_format.page_break_before = True
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = Pt(1)
    add_text(p, "", size=1)


def set_normal_style(doc):
    style = doc.styles["Normal"]
    style.font.name = FONT_EN
    style.font.size = Pt(11)
    style.font.color.rgb = rgb(INK)
    style.paragraph_format.space_before = Pt(0)
    style.paragraph_format.space_after = Pt(0)
    style.paragraph_format.line_spacing = 1.4
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.get_or_add_rFonts()
    rfonts.set(qn("w:ascii"), FONT_EN)
    rfonts.set(qn("w:hAnsi"), FONT_EN)
    rfonts.set(qn("w:eastAsia"), FONT_CN)
    rfonts.set(qn("w:cs"), FONT_EN)


# ---------- 封面 ----------
def build_cover(doc):
    p = doc.add_paragraph()
    set_para_spacing(p, before_cm=2.85, after=0, line=1.0)
    add_text(p, "SPORTS MEETING  /  AUTUMN 2026", size=8, color=GRAY,
             ascii_font="Times New Roman")

    p = doc.add_paragraph()
    set_para_spacing(p, before=10, after=18, line=1.0)
    add_bottom_border(p, color=ORANGE, size=12, space=2)

    p = doc.add_paragraph()
    set_para_spacing(p, before=8, after=0, line=1.0)
    add_text(p, "奔跑吧，", size=40, color=GREEN, bold=True)

    p = doc.add_paragraph()
    set_para_spacing(p, before=0, after=14, line=1.0)
    add_text(p, "少年", size=64, color=GREEN, bold=True)
    add_text(p, "。", size=64, color=ORANGE, bold=True)

    p = doc.add_paragraph()
    set_para_spacing(p, before=2, after=0, line=1.2)
    add_text(p, "初中运动会加油稿 · 200 则", size=15.5, color=GREEN_SOFT)

    p = doc.add_paragraph()
    set_para_spacing(p, before=14, after=0, line=1.4)
    add_text(p, "风起时，正好出发；年少时，正好奔跑。", size=11, color=GRAY)

    p = doc.add_paragraph()
    set_para_spacing(p, before=0, after=0, line=1.0)
    add_text(p, "", size=1)
    p.paragraph_format.space_before = Cm(6.4)

    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    remove_table_borders(table)
    set_table_width(table, 16.6)
    table.columns[0].width = Cm(16.6)
    row = table.rows[0]
    row.height = Cm(2.75)
    row.height_rule = WD_ROW_HEIGHT_RULE.EXACTLY
    cell = table.cell(0, 0)
    cell.width = Cm(16.6)
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    set_cell_shading(cell, GREEN)
    p = cell.paragraphs[0]
    set_para_spacing(p, before=0, after=3, line=1.2)
    p.paragraph_format.left_indent = Cm(0.65)
    add_text(p, "青春正好 · 我们正好", size=13.5, color=WHITE, bold=True)
    p = cell.add_paragraph()
    set_para_spacing(p, before=0, after=0, line=1.2)
    p.paragraph_format.left_indent = Cm(0.65)
    add_text(p, "200 CHEERS FOR EVERY RUNNING BOY AND GIRL",
             size=6.8, color=GOLD_LIGHT, ascii_font="Times New Roman")

    p = doc.add_paragraph()
    set_para_spacing(p, before=14, after=0, line=1.0)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_text(p, "2 0 2 6   ·   秋", size=8, color=GRAY_LIGHT)


# ---------- 卷首语与目录 ----------
def build_preface(doc):
    start_new_page(doc)
    p = doc.add_paragraph()
    set_para_spacing(p, before=0, after=4, line=1.0)
    add_text(p, "PREFACE  /  卷 首 语", size=7.8, color=ORANGE)

    p = doc.add_paragraph()
    set_para_spacing(p, before=2, after=4, line=1.05)
    add_text(p, "写在前面", size=24, color=GREEN, bold=True)

    p = doc.add_paragraph()
    set_para_spacing(p, before=0, after=14, line=1.0)
    p.paragraph_format.right_indent = Cm(12.8)
    add_bottom_border(p, color=ORANGE, size=14, space=2)

    for text in [
        "秋天把天空让给跑道，跑道把风让给少年。这里收录了 200 则初中运动会加油稿，按比赛项目与现场场景分为十辑，每一则十五至二十五字，短而有力，适合广播、板报、加油牌与看台呐喊。",
        "愿这些句子替你把掌声送出去，也替少年把勇气留下来。",
    ]:
        p = doc.add_paragraph()
        set_para_spacing(p, before=0, after=8, line=1.65)
        add_text(p, text, size=10.8, color=INK)

    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    remove_table_borders(table)
    set_table_width(table, 16.6)
    table.columns[0].width = Cm(16.6)
    row = table.rows[0]
    row.height = Cm(2.0)
    row.height_rule = WD_ROW_HEIGHT_RULE.EXACTLY
    cell = table.cell(0, 0)
    cell.width = Cm(16.6)
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    set_cell_shading(cell, CREAM)
    p = cell.paragraphs[0]
    set_para_spacing(p, before=0, after=0, line=1.45)
    p.paragraph_format.left_indent = Cm(0.65)
    p.paragraph_format.right_indent = Cm(0.65)
    add_text(p, "你负责奔跑，我们负责把整个秋天的掌声举高。",
             size=12.2, color=GREEN, bold=False)

    p = doc.add_paragraph()
    set_para_spacing(p, before=18, after=5, line=1.0)
    add_text(p, "使用说明", size=11.5, color=GREEN, bold=True)

    tips = [
        ("按项目检索", "十个篇章对应不同项目，可按现场需要快速取用。"),
        ("灵活组合", "同一篇章的句子可以连读，组成一段完整的加油词。"),
        ("替换细节", "把“班级”“少年”换成具体班级与姓名，会更贴近现场。"),
    ]
    for idx, (head, body) in enumerate(tips, 1):
        p = doc.add_paragraph()
        set_para_spacing(p, before=0, after=3, line=1.45)
        p.paragraph_format.left_indent = Cm(0.9)
        p.paragraph_format.first_line_indent = Cm(-0.9)
        p.paragraph_format.tab_stops.add_tab_stop(Cm(0.9))
        add_text(p, f"{idx:02d}", size=8, color=ORANGE, bold=True)
        p.add_run("\t")
        add_text(p, head + "  ", size=10, color=GREEN, bold=True)
        add_text(p, body, size=9.8, color=GRAY)

    p = doc.add_paragraph()
    set_para_spacing(p, before=16, after=6, line=1.0)
    add_text(p, "CONTENTS  /  目 录", size=7.8, color=ORANGE)

    table = doc.add_table(rows=5, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    remove_table_borders(table)
    set_table_width(table, 16.6)
    table.columns[0].width = Cm(8.3)
    table.columns[1].width = Cm(8.3)
    for r in range(5):
        table.rows[r].height = Cm(1.18)
        for c in range(2):
            cell = table.cell(r, c)
            cell.width = Cm(8.3)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            p = cell.paragraphs[0]
            set_para_spacing(p, before=0, after=0, line=1.15)
            idx = r * 2 + c
            section = SECTIONS[idx]
            add_text(p, section["number"], size=9, color=ORANGE, bold=True)
            add_text(p, "   " + section["title"], size=10.5, color=GREEN, bold=True)
            p2 = cell.add_paragraph()
            set_para_spacing(p2, before=1, after=0, line=1.0)
            start = idx * 20 + 1
            add_text(p2, f"{start:02d}—{start + 19:02d}", size=7.3, color=GRAY_LIGHT)


# ---------- 篇章页 ----------
def build_section(doc, section, index):
    start_new_page(doc)
    # 页眉区：标题在左，大号编号在右
    table = doc.add_table(rows=1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    remove_table_borders(table)
    set_table_width(table, 16.6)
    table.columns[0].width = Cm(13.2)
    table.columns[1].width = Cm(3.4)
    row = table.rows[0]
    row.height = Cm(2.0)
    row.height_rule = WD_ROW_HEIGHT_RULE.EXACTLY

    left = table.cell(0, 0)
    left.width = Cm(13.2)
    left.vertical_alignment = WD_ALIGN_VERTICAL.BOTTOM
    p = left.paragraphs[0]
    set_para_spacing(p, before=0, after=3, line=1.0)
    add_text(p, f"CHAPTER {section['number']}  /  {section['en']}",
             size=7.4, color=ORANGE)
    p = left.add_paragraph()
    set_para_spacing(p, before=0, after=0, line=1.0)
    add_text(p, section["title"], size=25, color=GREEN, bold=True)

    right = table.cell(0, 1)
    right.width = Cm(3.4)
    right.vertical_alignment = WD_ALIGN_VERTICAL.BOTTOM
    p = right.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    set_para_spacing(p, before=0, after=0, line=0.9)
    add_text(p, section["number"], size=52, color=GOLD_LIGHT, bold=True,
             ascii_font="Times New Roman")

    p = doc.add_paragraph()
    set_para_spacing(p, before=10, after=3, line=1.4)
    add_text(p, section["intro"], size=10.2, color=GRAY)

    p = doc.add_paragraph()
    set_para_spacing(p, before=0, after=9, line=1.0)
    add_bottom_border(p, color=RULE, size=4, space=2)

    start = index * 20 + 1
    for offset, line in enumerate(section["lines"]):
        p = doc.add_paragraph()
        pf = p.paragraph_format
        pf.left_indent = Cm(1.05)
        pf.first_line_indent = Cm(-1.05)
        pf.tab_stops.add_tab_stop(Cm(1.05), WD_TAB_ALIGNMENT.LEFT)
        set_para_spacing(p, before=2.6, after=2.6, line=1.42)
        add_text(p, f"{start + offset:02d}", size=8.6, color=ORANGE, bold=True)
        p.add_run("\t")
        add_text(p, line, size=11, color=INK)


# ---------- 尾声 ----------
def build_closing(doc):
    start_new_page(doc)

    p = doc.add_paragraph()
    set_para_spacing(p, before_cm=3.2, after=0, line=1.0)
    add_text(p, "TO THE HORIZON  /  尾 声", size=7.8, color=ORANGE)

    p = doc.add_paragraph()
    set_para_spacing(p, before=4, after=8, line=1.05)
    add_text(p, "奔跑，是青春最好的注脚", size=24, color=GREEN, bold=True)

    p = doc.add_paragraph()
    set_para_spacing(p, before=0, after=18, line=1.0)
    p.paragraph_format.right_indent = Cm(10.5)
    add_bottom_border(p, color=ORANGE, size=14, space=2)

    paragraphs = [
        "运动会终会散场，跑道会安静下来，旗帜也会收回。",
        "但你们跑过的风、喊哑的嗓子、递出去的那瓶水，会在很久以后依然发亮。",
        "愿每一个少年，把今天的速度变成明天的底气，把今天的勇敢带向更远的地方。",
    ]
    for text in paragraphs:
        p = doc.add_paragraph()
        set_para_spacing(p, before=0, after=12, line=1.75)
        add_text(p, text, size=11.2, color=INK)

    p = doc.add_paragraph()
    set_para_spacing(p, before=14, after=0, line=1.4)
    add_text(p, "愿你永远奔跑，永远向着光的方向。", size=15, color=GREEN, bold=True)

    p = doc.add_paragraph()
    set_para_spacing(p, before=8, after=0, line=1.4)
    add_text(p, "—— 献给每一个在操场上发光的少年", size=9.5, color=GRAY)

    p = doc.add_paragraph()
    set_para_spacing(p, before=0, after=0, line=1.0)
    add_text(p, "", size=1)
    p.paragraph_format.space_before = Cm(3.6)

    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    remove_table_borders(table)
    set_table_width(table, 16.6)
    table.columns[0].width = Cm(16.6)
    row = table.rows[0]
    row.height = Cm(2.1)
    row.height_rule = WD_ROW_HEIGHT_RULE.EXACTLY
    cell = table.cell(0, 0)
    cell.width = Cm(16.6)
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    set_cell_shading(cell, GREEN)
    p = cell.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_para_spacing(p, before=0, after=0, line=1.1)
    add_text(p, "青春不散场  ·  我们继续奔跑", size=12.5, color=WHITE, bold=True)

    p = doc.add_paragraph()
    set_para_spacing(p, before=12, after=0, line=1.0)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_text(p, "2 0 0   C H E E R S   ·   T H E   E N D", size=6.8, color=GRAY_LIGHT)


# ---------- 页脚 ----------
def build_footer(doc):
    section = doc.sections[0]
    section.different_first_page_header_footer = True
    footer = section.footer
    footer.is_linked_to_previous = False
    p = footer.paragraphs[0]
    p.paragraph_format.tab_stops.add_tab_stop(Cm(16.6), WD_TAB_ALIGNMENT.RIGHT)
    set_para_spacing(p, before=6, after=0, line=1.0)
    add_top_border(p, color=RULE, size=4, space=5)
    add_text(p, "青春正好  ·  初中运动会加油稿 200 则", size=7.6, color=GRAY)
    p.add_run("\t")
    add_text(p, "P.", size=7.6, color=GRAY)
    add_page_field(p, size=8.2, color=ORANGE)


def set_page_layout(doc):
    section = doc.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(2.2)
    section.bottom_margin = Cm(2.0)
    section.left_margin = Cm(2.2)
    section.right_margin = Cm(2.2)
    section.header_distance = Cm(1.2)
    section.footer_distance = Cm(1.1)
    section.start_type = WD_SECTION.NEW_PAGE


def main():
    doc = Document()
    set_page_layout(doc)
    set_normal_style(doc)

    props = doc.core_properties
    props.title = "奔跑吧，少年——初中运动会加油稿200则"
    props.subject = "初中运动会加油稿"
    props.author = "运动会宣传组"
    props.keywords = "运动会, 加油稿, 初中, 青春, 广播稿"
    props.comments = "200则初中运动会加油稿，按十个篇章编排，每则15—25字。"

    build_cover(doc)
    build_preface(doc)
    for i, section in enumerate(SECTIONS):
        build_section(doc, section, i)
    build_closing(doc)
    build_footer(doc)

    doc.save(OUT)
    print("saved:", OUT)

    # 基本自检
    check = Document(OUT)
    all_text = "\n".join(p.text for p in check.paragraphs)
    for table in check.tables:
        for row in table.rows:
            for cell in row.cells:
                all_text += "\n" + cell.text
    nums = re.findall(r"^\s*(\d{2})\t", all_text, flags=re.M)
    print("paragraphs:", len(check.paragraphs), "tables:", len(check.tables))
    print("numbered entries found:", len(nums), nums[:3], nums[-3:])
    print("chars:", len(all_text))


if __name__ == "__main__":
    main()

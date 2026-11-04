from __future__ import annotations

import re
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = Path(__file__).resolve().parents[1]
FIG = ROOT / "figures"
OUT = ROOT / "output"
OUT.mkdir(parents=True, exist_ok=True)

NAVY = "1F3A5F"
NAVY_DARK = "14283F"
GOLD = "B08D57"
INK = "222A33"
GRAY = "5E6E7E"
MUTED = "8797A7"
LIGHT_BLUE = "EAF1F7"
PALE = "F5F8FB"
BORDER = "D8E2EA"
WHITE = "FFFFFF"

BODY_FONT = "Times New Roman"
CN_FONT = "Songti SC"
MATH_FONT = "STIX Two Math"
BODY_PT = 10.5
CONTENT_W = 17.4  # cm


def rgb(hex_str: str) -> RGBColor:
    return RGBColor.from_string(hex_str)


def set_run(run, *, size=BODY_PT, color=INK, bold=False, italic=False, font=BODY_FONT):
    run.font.name = font
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = rgb(color)
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.rFonts
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.insert(0, rFonts)
    rFonts.set(qn("w:ascii"), font)
    rFonts.set(qn("w:hAnsi"), font)
    rFonts.set(qn("w:eastAsia"), CN_FONT if font == BODY_FONT else font)
    rFonts.set(qn("w:cs"), BODY_FONT)


def add_run(p, text, **kwargs):
    r = p.add_run(text)
    set_run(r, **kwargs)
    return r


TOKEN_RE = re.compile(r"(\^\{[^{}]*\}|_\{[^{}]*\})")
MATH_SYMBOLS = set("√∛△∠⊥≌")


def add_rich(p, text: str, *, size=BODY_PT, color=INK, bold=False, italic=False):
    """Add text with ^{...} superscripts, _{...} subscripts, and math font fallbacks."""
    pos = 0
    for m in TOKEN_RE.finditer(text):
        if m.start() > pos:
            _add_plain(p, text[pos:m.start()], size=size, color=color, bold=bold, italic=italic)
        token = m.group(0)
        is_sup = token.startswith("^")
        content = token[2:-1]
        _add_plain(
            p,
            content,
            size=size,
            color=color,
            bold=bold,
            italic=italic,
            superscript=is_sup,
            subscript=not is_sup,
        )
        pos = m.end()
    if pos < len(text):
        _add_plain(p, text[pos:], size=size, color=color, bold=bold, italic=italic)


def _add_plain(p, text, *, size, color, bold, italic, superscript=False, subscript=False):
    if not text:
        return
    parts = re.split(r"([√∛△∠⊥≌])", text)
    for part in parts:
        if not part:
            continue
        is_math = len(part) == 1 and part in MATH_SYMBOLS
        run = p.add_run(part)
        set_run(run, size=size, color=color, bold=bold, italic=italic, font=MATH_FONT if is_math else BODY_FONT)
        if superscript:
            run.font.superscript = True
        if subscript:
            run.font.subscript = True


def set_cell_shading(cell, fill: str):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    tcPr.append(shd)


def set_cell_margins(cell, top=0.08, start=0.12, bottom=0.08, end=0.12):
    tcPr = cell._tc.get_or_add_tcPr()
    mar = OxmlElement("w:tcMar")
    for tag, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        el = OxmlElement(f"w:{tag}")
        el.set(qn("w:w"), str(int(Cm(value).twips)))
        el.set(qn("w:type"), "dxa")
        mar.append(el)
    tcPr.append(mar)


def set_cell_border(cell, *, top=None, bottom=None, left=None, right=None):
    tcPr = cell._tc.get_or_add_tcPr()
    borders = tcPr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tcPr.append(borders)
    for side, spec in (("top", top), ("left", left), ("bottom", bottom), ("right", right)):
        if spec is None:
            continue
        el = borders.find(qn(f"w:{side}"))
        if el is None:
            el = OxmlElement(f"w:{side}")
            borders.append(el)
        color, sz = spec
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), str(sz))
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), color)


def set_table_borders(table, color=BORDER, sz=4, inside=True):
    tblPr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    edges = ["top", "left", "bottom", "right"]
    if inside:
        edges += ["insideH", "insideV"]
    for edge in edges:
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), str(sz))
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), color)
        borders.append(el)
    tblPr.append(borders)


def clear_table_borders(table):
    tblPr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ["top", "left", "bottom", "right", "insideH", "insideV"]:
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:val"), "nil")
        borders.append(el)
    tblPr.append(borders)


def set_table_width(table, width_cm):
    tblPr = table._tbl.tblPr
    tblW = OxmlElement("w:tblW")
    tblW.set(qn("w:w"), str(int(Cm(width_cm).twips)))
    tblW.set(qn("w:type"), "dxa")
    tblPr.append(tblW)
    table.autofit = False
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tblPr.append(layout)


def set_col_widths(table, widths_cm):
    for row in table.rows:
        for idx, w in enumerate(widths_cm):
            row.cells[idx].width = Cm(w)
    for idx, w in enumerate(widths_cm):
        table.columns[idx].width = Cm(w)


def set_row_height(row, cm, rule="atLeast"):
    trPr = row._tr.get_or_add_trPr()
    trHeight = OxmlElement("w:trHeight")
    trHeight.set(qn("w:val"), str(int(Cm(cm).twips)))
    trHeight.set(qn("w:hRule"), rule)
    trPr.append(trHeight)


def cant_split(row):
    trPr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:cantSplit")
    trPr.append(el)


def keep_next(p):
    p.paragraph_format.keep_with_next = True


def add_page_field(p, instr, size=8.2, color=GRAY):
    fld = OxmlElement("w:fldSimple")
    fld.set(qn("w:instr"), instr)
    r = OxmlElement("w:r")
    rPr = OxmlElement("w:rPr")
    rFonts = OxmlElement("w:rFonts")
    rFonts.set(qn("w:ascii"), BODY_FONT)
    rFonts.set(qn("w:hAnsi"), BODY_FONT)
    rFonts.set(qn("w:eastAsia"), CN_FONT)
    rPr.append(rFonts)
    sz = OxmlElement("w:sz")
    sz.set(qn("w:val"), str(int(size * 2)))
    rPr.append(sz)
    col = OxmlElement("w:color")
    col.set(qn("w:val"), color)
    rPr.append(col)
    r.append(rPr)
    t = OxmlElement("w:t")
    t.text = "1"
    r.append(t)
    fld.append(r)
    p._p.append(fld)


def para_border_bottom(p, color=BORDER, sz=4, space=4):
    pPr = p._p.get_or_add_pPr()
    pBdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), str(sz))
    bottom.set(qn("w:space"), str(space))
    bottom.set(qn("w:color"), color)
    pBdr.append(bottom)
    pPr.append(pBdr)


def setup_page(section):
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(1.35)
    section.bottom_margin = Cm(1.25)
    section.left_margin = Cm(1.8)
    section.right_margin = Cm(1.8)
    section.header_distance = Cm(0.65)
    section.footer_distance = Cm(0.65)


def add_header(section, left_text: str):
    section.header.is_linked_to_previous = False
    header = section.header
    p = header.paragraphs[0]
    p.text = ""
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.tab_stops.add_tab_stop(Cm(CONTENT_W), WD_TAB_ALIGNMENT.RIGHT)
    add_rich(p, left_text, size=8.2, color=GRAY)
    add_run(p, "\t\t", size=8.2, color=GRAY)
    add_rich(p, "第 ", size=8.2, color=GRAY)
    add_page_field(p, " PAGE ", size=8.2, color=GRAY)
    add_rich(p, " 页 / 共 ", size=8.2, color=GRAY)
    add_page_field(p, " NUMPAGES ", size=8.2, color=GRAY)
    add_rich(p, " 页", size=8.2, color=GRAY)
    para_border_bottom(p, color=BORDER, sz=4, space=3)


def add_footer(section):
    section.footer.is_linked_to_previous = False
    p = section.footer.paragraphs[0]
    p.text = ""
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_rich(p, "八年级数学 · 第1—4章", size=8.0, color=MUTED)


def add_exam_footer(section):
    section.footer.is_linked_to_previous = False
    p = section.footer.paragraphs[0]
    p.text = ""
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_rich(p, "第 ", size=8.0, color=MUTED)
    add_page_field(p, " PAGE ", size=8.0, color=MUTED)
    add_rich(p, " 页 / 共 ", size=8.0, color=MUTED)
    add_page_field(p, " NUMPAGES ", size=8.0, color=MUTED)
    add_rich(p, " 页", size=8.0, color=MUTED)


def add_header_block(doc):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    clear_table_borders(table)
    cell = table.cell(0, 0)
    cell.width = Cm(CONTENT_W)
    set_cell_shading(cell, NAVY)
    set_cell_margins(cell, top=0.30, bottom=0.30, start=0.45, end=0.45)
    set_cell_border(cell, bottom=(GOLD, 24))
    p = cell.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, "2026—2027 学年第一学期", size=8.3, color="C9D8E6")

    p = cell.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(1)
    add_rich(p, "八年级数学阶段检测", size=21.5, color=WHITE, bold=True)

    p = cell.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(1)
    add_rich(p, "北师大版 · 八年级上册 第1—4章", size=9.8, color="E8D7B8")

    p = cell.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, "勾股定理　·　实数　·　位置与坐标　·　一次函数", size=8.2, color="AFC5D8")


def add_info_strip(doc):
    table = doc.add_table(rows=1, cols=3)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    clear_table_borders(table)
    widths = [5.8, 5.8, 5.8]
    data = [
        ("考试时间", "120 分钟"),
        ("满分", "120 分"),
        ("命题范围", "第 1—4 章"),
    ]
    for idx, (label, value) in enumerate(data):
        cell = table.cell(0, idx)
        cell.width = Cm(widths[idx])
        set_cell_shading(cell, PALE)
        set_cell_margins(cell, top=0.18, bottom=0.18, start=0.2, end=0.2)
        if idx < 2:
            set_cell_border(cell, right=(BORDER, 4))
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(0)
        add_rich(p, label, size=7.4, color=MUTED)
        p2 = cell.add_paragraph()
        p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p2.paragraph_format.space_after = Pt(0)
        add_rich(p2, value, size=10.3, color=NAVY, bold=True)
    set_row_height(table.rows[0], 0.95)


def add_name_row(doc):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(3)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_rich(p, "班级：____________　　姓名：____________　　学号：____________", size=9.2, color=GRAY)


def add_score_table(doc):
    labels = ["题号", "一", "二", "三", "四", "五", "六", "总分"]
    table = doc.add_table(rows=2, cols=len(labels))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    set_col_widths(table, [2.3] + [2.1] * 6 + [2.5])
    set_table_borders(table, color=BORDER, sz=4, inside=True)
    for i, label in enumerate(labels):
        cell = table.cell(0, i)
        set_cell_shading(cell, LIGHT_BLUE)
        set_cell_margins(cell, top=0.08, bottom=0.08, start=0.08, end=0.08)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(0)
        add_rich(p, label, size=8.4, color=NAVY, bold=True)
        cell2 = table.cell(1, i)
        set_cell_margins(cell2, top=0.08, bottom=0.08, start=0.08, end=0.08)
        cell2.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        p2 = cell2.paragraphs[0]
        p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p2.paragraph_format.space_after = Pt(0)
        if i == 0:
            add_rich(p2, "得分", size=8.2, color=GRAY)
    set_row_height(table.rows[0], 0.48, "atLeast")
    set_row_height(table.rows[1], 0.60, "atLeast")
    for row in table.rows:
        cant_split(row)


def add_notice(doc):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    clear_table_borders(table)
    cell = table.cell(0, 0)
    cell.width = Cm(CONTENT_W)
    set_cell_shading(cell, PALE)
    set_cell_margins(cell, top=0.18, bottom=0.18, start=0.38, end=0.38)
    set_cell_border(cell, left=(GOLD, 26))
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(2)
    add_rich(p, "答题须知", size=9.4, color=NAVY, bold=True)
    add_rich(p, "　NOTICE", size=7.2, color=GOLD, bold=True)
    lines = [
        ("1", "全卷共 23 题，满分 120 分，考试时间 120 分钟。"),
        ("2", "选择题、填空题的答案填写在题目指定位置；解答题须写出必要的文字说明、证明过程或演算步骤。"),
        ("3", "请用黑色字迹签字笔作答，保持卷面整洁；不得使用计算器。"),
    ]
    for num, text in lines:
        pp = cell.add_paragraph()
        pp.paragraph_format.left_indent = Cm(0.42)
        pp.paragraph_format.first_line_indent = Cm(-0.42)
        pp.paragraph_format.space_after = Pt(1)
        pp.paragraph_format.line_spacing = 1.12
        add_rich(pp, f"{num}.", size=8.4, color=GOLD, bold=True)
        add_run(pp, " ", size=8.4, color=GRAY)
        add_rich(pp, text, size=8.4, color=GRAY)
    cant_split(table.rows[0])


def add_section_heading(doc, cn_num: str, title: str, detail: str, *, page_break=False, before=8):
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(0)
    spacer.paragraph_format.line_spacing = Pt(before if before else 1)
    if page_break:
        spacer.paragraph_format.page_break_before = True
    table = doc.add_table(rows=1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    clear_table_borders(table)
    set_col_widths(table, [0.95, 16.45])
    left = table.cell(0, 0)
    right = table.cell(0, 1)
    for cell in (left, right):
        set_cell_margins(cell, top=0.10, bottom=0.10, start=0.15, end=0.15)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    set_cell_shading(left, NAVY)
    set_cell_shading(right, LIGHT_BLUE)
    p = left.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, cn_num, size=10.5, color=WHITE, bold=True)
    p = right.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, title, size=11.2, color=NAVY, bold=True)
    add_rich(p, "　" + detail, size=8.3, color=GRAY)
    set_row_height(table.rows[0], 0.58)
    cant_split(table.rows[0])
    # keep the row with the first question
    for cell in (left, right):
        for pp in cell.paragraphs:
            keep_next(pp)


def add_question(doc, num, text, *, points=None, after=3, keep=False, size=BODY_PT):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(1.0)
    p.paragraph_format.first_line_indent = Cm(-1.0)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(1.0))
    p.paragraph_format.line_spacing = 1.22
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(after)
    if keep:
        keep_next(p)
    add_rich(p, f"{num}．", size=size, color=NAVY, bold=True)
    add_run(p, "\t", size=size, color=INK)
    add_rich(p, text, size=size, color=INK)
    return p


def add_options(doc, options, *, after=3, size=10.3):
    for row in (options[:2], options[2:]):
        p = doc.add_paragraph()
        p.paragraph_format.left_indent = Cm(1.0)
        p.paragraph_format.line_spacing = 1.10
        p.paragraph_format.space_after = Pt(0.5)
        p.paragraph_format.tab_stops.add_tab_stop(Cm(8.55), WD_TAB_ALIGNMENT.LEFT)
        for idx, (letter, text) in enumerate(row):
            add_rich(p, f"{letter}．", size=size, color=GOLD, bold=True)
            add_rich(p, text, size=size, color=INK)
            if idx == 0:
                add_run(p, "\t", size=size, color=INK)
    # Keep the two option rows together, but do not chain to the next question.
    paras = doc.paragraphs[-2:]
    keep_next(paras[0])
    paras[1].paragraph_format.space_after = Pt(after)


def add_subitem(doc, label, text, *, after=1, keep=False, size=BODY_PT):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(1.05)
    p.paragraph_format.line_spacing = 1.28
    p.paragraph_format.space_after = Pt(after)
    if keep:
        keep_next(p)
    add_rich(p, label, size=size, color=NAVY)
    add_rich(p, text, size=size, color=INK)
    return p


def set_para_bottom_border(p, color="D8E2EA", sz=4, space=1):
    pPr = p._p.get_or_add_pPr()
    pBdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), str(sz))
    bottom.set(qn("w:space"), str(space))
    bottom.set(qn("w:color"), color)
    pBdr.append(bottom)
    pPr.append(pBdr)


def add_answer_space(doc, lines=8, *, label="解："):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(1.0)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, label, size=9.4, color=GRAY)
    keep_next(p)
    for i in range(lines):
        blank = doc.add_paragraph()
        blank.paragraph_format.left_indent = Cm(1.0)
        blank.paragraph_format.space_before = Pt(0)
        blank.paragraph_format.space_after = Pt(0)
        blank.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
        blank.paragraph_format.line_spacing = Cm(0.72)
        if i == lines - 1:
            blank.paragraph_format.space_after = Pt(5)


def add_figure_question(doc, num, text, filename, width_cm, caption, *, left_w=10.6, right_w=6.8):
    table = doc.add_table(rows=1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    clear_table_borders(table)
    set_col_widths(table, [left_w, right_w])
    left, right = table.cell(0, 0), table.cell(0, 1)
    for cell in (left, right):
        set_cell_margins(cell, top=0.0, bottom=0.0, start=0.0, end=0.0)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.TOP
    p = left.paragraphs[0]
    p.paragraph_format.left_indent = Cm(0.95)
    p.paragraph_format.first_line_indent = Cm(-0.95)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(0.95))
    p.paragraph_format.line_spacing = 1.22
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, f"{num}．", size=BODY_PT, color=NAVY, bold=True)
    add_run(p, "\t", size=BODY_PT, color=INK)
    add_rich(p, text, size=BODY_PT, color=INK)

    ip = right.paragraphs[0]
    ip.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    ip.paragraph_format.space_before = Pt(0)
    ip.paragraph_format.space_after = Pt(0)
    ip.add_run().add_picture(str(FIG / filename), width=Cm(width_cm))
    cp = right.add_paragraph()
    cp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    cp.paragraph_format.space_before = Pt(0)
    cp.paragraph_format.space_after = Pt(0)
    add_rich(cp, caption, size=8.2, color=MUTED)
    cant_split(table.rows[0])


def add_figure(doc, filename, width_cm, caption):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(0)
    keep_next(p)
    run = p.add_run()
    run.add_picture(str(FIG / filename), width=Cm(width_cm))
    cap = doc.add_paragraph()
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.space_before = Pt(0)
    cap.paragraph_format.space_after = Pt(3)
    add_rich(cap, caption, size=8.2, color=MUTED)
    return cap


def add_solution_space(doc, lines=6, *, label="解：", keep_label=True):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(1.0)
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, label, size=9.6, color=GRAY)
    if keep_label:
        keep_next(p)
    for i in range(lines):
        blank = doc.add_paragraph()
        blank.paragraph_format.left_indent = Cm(1.0)
        blank.paragraph_format.space_before = Pt(0)
        blank.paragraph_format.space_after = Pt(0)
        blank.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
        blank.paragraph_format.line_spacing = Cm(0.78)
        if i == lines - 1:
            blank.paragraph_format.space_after = Pt(4)


def add_answer_title(doc):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(2)
    add_rich(p, "参考答案与评分标准", size=20, color=NAVY, bold=True)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(5)
    add_rich(p, "八年级数学（北师大版 · 第1—4章）", size=10, color=GRAY)

    # gold rule
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(7)
    add_run(p, "━━━━━━━━━━━━━━━━━━━━", size=8.5, color=GOLD)

    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    clear_table_borders(table)
    cell = table.cell(0, 0)
    set_cell_shading(cell, PALE)
    set_cell_margins(cell, top=0.18, bottom=0.18, start=0.35, end=0.35)
    set_cell_border(cell, left=(GOLD, 22))
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.18
    add_rich(p, "评分说明：", size=8.6, color=NAVY, bold=True)
    add_rich(
        p,
        "解答题如采用其他合理方法，推理正确、结果无误，请参照本标准相应步骤给分；过程分不重复扣分。",
        size=8.6,
        color=GRAY,
    )
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_after = Pt(0)
    spacer.paragraph_format.line_spacing = Pt(6)


def add_choice_key(doc):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(3)
    add_rich(p, "一、选择题", size=10.8, color=NAVY, bold=True)
    add_rich(p, "　每小题3分，共18分", size=8.3, color=GRAY)
    table = doc.add_table(rows=2, cols=7)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    set_col_widths(table, [2.3] + [2.42] * 6)
    set_table_borders(table, color=BORDER, sz=4, inside=True)
    data = [["题号", "1", "2", "3", "4", "5", "6"], ["答案", "C", "C", "A", "C", "A", "B"]]
    for r, row in enumerate(data):
        for c, val in enumerate(row):
            cell = table.cell(r, c)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell, top=0.08, bottom=0.08, start=0.08, end=0.08)
            if r == 0:
                set_cell_shading(cell, LIGHT_BLUE)
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_after = Pt(0)
            add_rich(p, val, size=9.2, color=NAVY if r == 0 else INK, bold=(r == 0))
    for row in table.rows:
        set_row_height(row, 0.55)
        cant_split(row)


def add_fill_key(doc):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(3)
    add_rich(p, "二、填空题", size=10.8, color=NAVY, bold=True)
    add_rich(p, "　每小题3分，共18分；第12题3个空，每空1分", size=8.3, color=GRAY)
    table = doc.add_table(rows=7, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, CONTENT_W)
    set_col_widths(table, [2.4, 15.0])
    set_table_borders(table, color=BORDER, sz=4, inside=True)
    header = table.cell(0, 0).merge(table.cell(0, 1))
    p = header.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(0)
    set_cell_shading(header, LIGHT_BLUE)
    add_rich(p, "题号 — 答案", size=8.8, color=NAVY, bold=True)
    rows = [
        ("7", "2"),
        ("8", "-5"),
        ("9", "m<2"),
        ("10", "5 或 √7"),
        ("11", "4√2"),
        ("12", "（1）x≥2　（2）5　（3）（2，0）"),
    ]
    for r, (num, ans) in enumerate(rows, start=1):
        c0 = table.cell(r, 0)
        c1 = table.cell(r, 1)
        for cell in (c0, c1):
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell, top=0.08, bottom=0.08, start=0.12, end=0.12)
        p0 = c0.paragraphs[0]
        p0.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p0.paragraph_format.space_after = Pt(0)
        add_rich(p0, num, size=9.2, color=NAVY, bold=True)
        p1 = c1.paragraphs[0]
        p1.paragraph_format.space_after = Pt(0)
        add_rich(p1, ans, size=9.4, color=INK)
    for row in table.rows:
        set_row_height(row, 0.52)
        cant_split(row)


def add_solution_heading(doc):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(4)
    add_rich(p, "三、解答题", size=10.8, color=NAVY, bold=True)
    add_rich(p, "　共84分", size=8.3, color=GRAY)
    p2 = doc.add_paragraph()
    p2.paragraph_format.space_after = Pt(2)
    add_rich(p2, "以下解答仅供参考，其他合理解法同样给分。", size=8.5, color=MUTED)


def add_solution(doc, num, text, *, points=None, after=4, size=9.9):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.95)
    p.paragraph_format.first_line_indent = Cm(-0.95)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(0.95))
    p.paragraph_format.line_spacing = 1.26
    p.paragraph_format.space_after = Pt(after)
    add_rich(p, f"{num}．", size=size, color=NAVY, bold=True)
    add_run(p, "\t", size=size, color=INK)
    add_rich(p, text, size=size, color=INK)
    if points:
        add_rich(p, f"　（{points}分）", size=8.4, color=MUTED)
    return p


def add_solution_line(doc, text, *, indent=0.95, after=2, size=9.9, bold=False, color=INK):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(indent)
    p.paragraph_format.line_spacing = 1.24
    p.paragraph_format.space_after = Pt(after)
    add_rich(p, text, size=size, color=color, bold=bold)
    return p


def add_eq(doc, text, *, indent=1.8, after=1, size=9.9):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(indent)
    p.paragraph_format.line_spacing = 1.22
    p.paragraph_format.space_after = Pt(after)
    add_rich(p, text, size=size, color=INK)
    return p


def build():
    doc = Document()
    setup_page(doc.sections[0])
    doc.sections[0].different_first_page_header_footer = True

    # base style
    normal = doc.styles["Normal"]
    normal.font.name = BODY_FONT
    normal.font.size = Pt(BODY_PT)
    normal.font.color.rgb = rgb(INK)
    normal.paragraph_format.line_spacing = 1.28
    normal.paragraph_format.space_after = Pt(0)
    normal._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), CN_FONT)

    # update fields on open
    settings = doc.settings.element
    update = OxmlElement("w:updateFields")
    update.set(qn("w:val"), "true")
    settings.append(update)

    # 纯题目版：不设页眉，只在页脚保留页码
    for hdr in (doc.sections[0].header, doc.sections[0].first_page_header):
        hdr.is_linked_to_previous = False
        for p in hdr.paragraphs:
            p.text = ""
    add_exam_footer(doc.sections[0])
    first_footer = doc.sections[0].first_page_footer
    first_footer.is_linked_to_previous = False
    p = first_footer.paragraphs[0]
    p.text = ""
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_rich(p, "第 ", size=8.0, color=MUTED)
    add_page_field(p, " PAGE ", size=8.0, color=MUTED)
    add_rich(p, " 页 / 共 ", size=8.0, color=MUTED)
    add_page_field(p, " NUMPAGES ", size=8.0, color=MUTED)
    add_rich(p, " 页", size=8.0, color=MUTED)

    # 直接从题目开始，不设大标题、考试时间等信息栏

    # ---------------- section I ----------------
    add_section_heading(doc, "一", "选择题", "本大题共6小题，每小题3分，共18分。在每小题给出的四个选项中，只有一项是符合题目要求的。", before=5)
    add_question(doc, 1, "下列各数中，是无理数的是（　　）", after=2, keep=True)
    add_options(doc, [("A", "0.333…"), ("B", "√9"), ("C", "π/2"), ("D", "22/7")], after=4)
    add_question(doc, 2, "下列各组数中，能作为直角三角形三边长的是（　　）", after=2, keep=True)
    add_options(doc, [("A", "1，2，3"), ("B", "2，3，4"), ("C", "3，4，5"), ("D", "4，5，6")], after=4)
    add_question(doc, 3, "若点 P(a-2, 3a+6) 在 y 轴上，则点 P 的坐标是（　　）", after=2, keep=True)
    add_options(doc, [("A", "（0，12）"), ("B", "（12，0）"), ("C", "（0，-12）"), ("D", "（-12，0）")], after=4)
    add_question(doc, 4, "一次函数 y=-2x+4 的图象不经过（　　）", after=2, keep=True)
    add_options(doc, [("A", "第一象限"), ("B", "第二象限"), ("C", "第三象限"), ("D", "第四象限")], after=4)
    add_question(doc, 5, "直角三角形的两条直角边长分别为6和8，则斜边上的高为（　　）", after=2, keep=True)
    add_options(doc, [("A", "4.8"), ("B", "5"), ("C", "6"), ("D", "8")], after=4)
    add_question(doc, 6, "直线 y=kx+3 与 x 轴、y 轴围成的三角形的面积为6，则 k 的值为（　　）", after=2, keep=True)
    add_options(doc, [("A", "3/4"), ("B", "-3/4"), ("C", "±3/4"), ("D", "±4/3")], after=2)

    # ---------------- section II ----------------
    add_section_heading(doc, "二", "填空题", "本大题共6小题，每小题3分，共18分。第12题有3个答案，答对1个得1分。", before=4)
    add_question(doc, 7, "√16 的算术平方根是________．", after=7)
    add_question(doc, 8, "已知一次函数 y=(m-2)x+3，且 y 随 x 的增大而减小，则 m 的取值范围是________．", after=7)
    add_question(doc, 9, "在平面直角坐标系中，点 A(1,3)，B(5,-1)，点 P 在 x 轴上，且 PA=PB，则点 P 的坐标为________．", after=7)
    add_question(doc, 10, "已知直角三角形的两边长分别为3和4，则第三边长为________．", after=7)
    add_question(doc, 11, "在平面直角坐标系中，A(1,3)，B(4,1)，点 P 在 x 轴上，则 PA+PB 的最小值为________．", after=7)
    add_question(doc, 12, "若 x^{2}=16，y^{2}=9，且 x>y，则 x-y 的所有可能值为________．", after=2)

    # ---------------- section III ----------------
    add_section_heading(doc, "三", "解答题（一）", "第13—17题，每题6分，共30分。解答应写出文字说明、证明过程或演算步骤。", page_break=True, before=0)
    add_question(doc, 13, "解方程：", points=6, after=2, keep=True)
    add_subitem(doc, "（1）", "4(x-1)^{2}=25；", after=1, keep=True)
    add_subitem(doc, "（2）", "27(x+2)^{3}=-64．", after=1)
    add_answer_space(doc, 7)

    add_question(doc, 14, "计算：", points=6, after=2, keep=True)
    add_subitem(doc, "（1）", "|-2|+2026^{0}-√9+∛(-8)；", after=1, keep=True)
    add_subitem(doc, "（2）", "√16-|√2-2|+(1/2)^{-1}．", after=1)
    add_answer_space(doc, 7)

    add_question(doc, 15, "已知 2a-1 的平方根是 ±3，a+b 的立方根是 2，求 a^{2}+b^{2} 的平方根．", points=6, after=1)
    add_answer_space(doc, 6)

    add_figure_question(
        doc, 16,
        "如图，在△ABC 中，AB=13，AC=15，BC=14，AD⊥BC 于点 D，求 AD 的长及△ABC 的面积．",
        "fig15_triangle.png", 5.2, "图1　第16题图",
        left_w=10.7, right_w=6.7,
    )
    add_answer_space(doc, 8)

    add_figure_question(
        doc, 17,
        "如图，在平面直角坐标系中，△ABC 的三个顶点分别为 A(-5,1)，B(-2,-3)，C(-1,1)．",
        "fig16_coordinate.png", 5.3, "图2　第17题图",
        left_w=10.4, right_w=7.0,
    )
    add_subitem(doc, "（1）", "直接写出点 A，B，C 关于 y 轴的对称点 A_{1}，B_{1}，C_{1} 的坐标；", after=1, keep=True)
    add_subitem(doc, "（2）", "求△ABC 的面积．", after=1)
    add_answer_space(doc, 8)

    # ---------------- section IV ----------------
    add_section_heading(doc, "四", "解答题（二）", "第18—20题，每题8分，共24分。", before=8)
    add_question(doc, 18, "已知一次函数 y=kx+b 的图象经过点 A(-2,0) 和 B(0,4)．", points=8, after=2, keep=True)
    add_subitem(doc, "（1）", "求这个一次函数的表达式；", after=1, keep=True)
    add_subitem(doc, "（2）", "将直线 AB 向左平移2个单位长度，再向上平移3个单位长度，得到直线 l，直线 l 与 x 轴、y 轴分别交于点 C，D，求 CD 的长及△OCD 的面积．", after=1)
    add_answer_space(doc, 8)

    add_figure_question(
        doc, 19,
        "如图，在四边形 ABCD 中，AB=3，BC=4，∠B=90°，CD=12，AD=13．求四边形 ABCD 的面积．",
        "fig17_quadrilateral.png", 5.2, "图3　第19题图",
        left_w=10.6, right_w=6.8,
    )
    add_answer_space(doc, 8)

    add_figure_question(
        doc, 20,
        "如图，在矩形 ABCD 中，AB=8，BC=10，点 E 在边 CD 上，将△ADE 沿 AE 折叠，点 D 恰好落在边 BC 上的点 F 处，求 CE 的长．",
        "fig19_folding.png", 4.4, "图4　第20题图",
        left_w=10.9, right_w=6.5,
    )
    add_answer_space(doc, 9)

    # ---------------- section V ----------------
    add_section_heading(doc, "五", "解答题（三）", "第21—22题，每题9分，共18分。", before=8)
    add_figure_question(
        doc, 21,
        "如图，长方体盒子的长、宽、高分别为5 cm、4 cm、3 cm，一只蚂蚁从盒子一角的顶点 A 处出发，沿盒子表面爬到与 A 相对的另一顶点 B 处，求蚂蚁爬行的最短路程．",
        "fig21_cuboid.png", 5.0, "图5　第21题图",
        left_w=11.1, right_w=6.3,
    )
    add_answer_space(doc, 7)

    add_question(doc, 22, "观察下列勾股数：", points=9, after=3, keep=True)
    keep_next(add_solution_line(doc, "第1组：3，4，5；", indent=1.2, after=1, size=10.1))
    keep_next(add_solution_line(doc, "第2组：5，12，13；", indent=1.2, after=1, size=10.1))
    keep_next(add_solution_line(doc, "第3组：7，24，25；", indent=1.2, after=2, size=10.1))
    keep_next(add_solution_line(doc, "……", indent=1.2, after=2, size=10.1))
    add_subitem(doc, "（1）", "第4组勾股数是________；", after=1, keep=True)
    add_subitem(doc, "（2）", "用含 n 的代数式表示第 n 组勾股数，并证明这组数能作为直角三角形的三边长；", after=1)
    add_subitem(doc, "（3）", "若某组勾股数的第一个数是2025，求这组勾股数中另外两个数．", after=1)
    add_answer_space(doc, 6)

    # ---------------- section VI ----------------
    add_section_heading(doc, "六", "解答题（四）", "第23题，12分。", before=6)
    add_figure_question(
        doc, 23,
        "如图，在平面直角坐标系中，直线 l_{1}: y=x+2 与 x 轴交于点 A，与 y 轴交于点 B；直线 l_{2}: y=-2x+8 与 x 轴交于点 C，与 y 轴交于点 D．两直线交于点 P．",
        "fig22_lines.png", 4.8, "图6　第23题图",
        left_w=10.3, right_w=6.9,
    )
    add_subitem(doc, "（1）", "求点 A，B，C，D 的坐标；", after=1)
    add_subitem(doc, "（2）", "求△PBC 的面积，并求线段 BC 的垂直平分线与 x 轴的交点坐标；", after=1)
    add_subitem(doc, "（3）", "在 x 轴上是否存在点 Q，使△QBC 是等腰三角形？若存在，求出所有满足条件的点 Q 的坐标；若不存在，请说明理由．", after=1)
    add_answer_space(doc, 6)

    # save
    out_docx = OUT / "八年级上册数学阶段检测_北师大版_第1-4章.docx"
    doc.save(out_docx)
    print(out_docx)
    return out_docx


if __name__ == "__main__":
    build()

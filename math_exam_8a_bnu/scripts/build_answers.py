from __future__ import annotations

from pathlib import Path

from docx import Document
from docx.enum.table import WD_ALIGN_VERTICAL, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

import build_exam as bx
from build_exam import (
    add_eq,
    add_footer,
    add_header,
    add_rich,
    add_run,
    add_solution,
    add_solution_line,
    cant_split,
    clear_table_borders,
    keep_next,
    set_cell_border,
    set_cell_margins,
    set_cell_shading,
    set_col_widths,
    set_row_height,
    set_table_borders,
    set_table_width,
    setup_page,
    BORDER,
    GRAY,
    GOLD,
    INK,
    LIGHT_BLUE,
    MUTED,
    NAVY,
    PALE,
    WHITE,
)

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output"
OUT.mkdir(parents=True, exist_ok=True)


def page_setup(section):
    setup_page(section)
    section.top_margin = Cm(1.55)
    section.bottom_margin = Cm(1.45)
    section.different_first_page_header_footer = False


def add_heading(doc, title, detail=""):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, bx.CONTENT_W)
    clear_table_borders(table)
    cell = table.cell(0, 0)
    cell.width = Cm(bx.CONTENT_W)
    set_cell_shading(cell, PALE)
    set_cell_margins(cell, top=0.10, bottom=0.10, start=0.30, end=0.25)
    set_cell_border(cell, left=(GOLD, 24))
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, title, size=11.2, color=NAVY, bold=True)
    if detail:
        add_rich(p, "　" + detail, size=8.2, color=GRAY)
    set_row_height(table.rows[0], 0.56)
    cant_split(table.rows[0])
    for pp in cell.paragraphs:
        keep_next(pp)
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(0)
    spacer.paragraph_format.line_spacing = Pt(3)


def add_tip(doc, title, text, kind="tip"):
    if kind == "warn":
        fill = "FBF4F0"
        bar = "A65C46"
        title_color = "8A4A36"
    else:
        fill = "F1F7F5"
        bar = "2F6F73"
        title_color = "24595C"
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, bx.CONTENT_W - 0.4)
    clear_table_borders(table)
    cell = table.cell(0, 0)
    set_cell_shading(cell, fill)
    set_cell_margins(cell, top=0.10, bottom=0.10, start=0.30, end=0.30)
    set_cell_border(cell, left=(bar, 22))
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.18
    add_rich(p, title + "　", size=8.7, color=title_color, bold=True)
    add_rich(p, text, size=8.7, color=GRAY)
    cant_split(table.rows[0])
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(0)
    spacer.paragraph_format.line_spacing = Pt(4)


def add_quick_table(doc, title, headers, values, widths):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(2)
    add_rich(p, title, size=9.8, color=NAVY, bold=True)
    table = doc.add_table(rows=2, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, bx.CONTENT_W)
    set_col_widths(table, widths)
    set_table_borders(table, color=BORDER, sz=4, inside=True)
    for r, row in enumerate([headers, values]):
        for c, val in enumerate(row):
            cell = table.cell(r, c)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell, top=0.07, bottom=0.07, start=0.06, end=0.06)
            if r == 0:
                set_cell_shading(cell, LIGHT_BLUE)
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_after = Pt(0)
            add_rich(p, val, size=8.8, color=NAVY if r == 0 else INK, bold=(r == 0))
    for row in table.rows:
        set_row_height(row, 0.48)
        cant_split(row)


def add_overview(doc):
    add_quick_table(
        doc,
        "选择题答案",
        ["题号", "1", "2", "3", "4", "5", "6"],
        ["答案", "C", "C", "A", "C", "A", "C"],
        [2.4, 2.5, 2.5, 2.5, 2.5, 2.5, 2.5],
    )
    add_quick_table(
        doc,
        "填空题答案",
        ["题号", "7", "8", "9", "10", "11", "12"],
        ["答案", "2", "m<2", "(2,0)", "5 或 √7", "5", "1，7，-1"],
        [2.2, 1.8, 2.0, 2.0, 3.4, 2.4, 3.6],
    )


def add_structure(doc):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(2)
    add_rich(p, "试卷结构", size=9.8, color=NAVY, bold=True)
    table = doc.add_table(rows=2, cols=7)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, bx.CONTENT_W)
    set_col_widths(table, [2.7, 2.7, 2.7, 2.7, 2.7, 2.2, 1.7])
    set_table_borders(table, color=BORDER, sz=4, inside=True)
    rows = [
        ["题号", "1—6", "7—12", "13—17", "18—20", "21—22", "23"],
        ["分值", "18", "18", "30", "24", "18", "12"],
    ]
    for r, row in enumerate(rows):
        for c, val in enumerate(row):
            cell = table.cell(r, c)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell, top=0.06, bottom=0.06, start=0.05, end=0.05)
            if r == 0:
                set_cell_shading(cell, LIGHT_BLUE)
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_after = Pt(0)
            add_rich(p, val, size=8.4, color=NAVY if r == 0 else INK, bold=(r == 0))
    for row in table.rows:
        set_row_height(row, 0.44)
        cant_split(row)


def add_chapter_table(doc):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(5)
    p.paragraph_format.space_after = Pt(2)
    add_rich(p, "第1—4章考点分布", size=9.8, color=NAVY, bold=True)
    table = doc.add_table(rows=5, cols=3)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, bx.CONTENT_W)
    set_col_widths(table, [3.2, 6.1, 8.1])
    set_table_borders(table, color=BORDER, sz=4, inside=True)
    rows = [
        ["章节", "对应题号", "核心考点"],
        ["第1章 勾股定理", "2，5，10，16，19，20，21，22", "勾股定理、逆定理、折叠、展开、最短路径"],
        ["第2章 实数", "1，7，12，13，14，15，22", "无理数、平方根与立方根、实数运算、规律"],
        ["第3章 位置与坐标", "3，9，11，17，23", "坐标特征、对称、距离、面积"],
        ["第4章 一次函数", "4，6，8，18，23", "图象与性质、待定系数法、平移、交点"],
    ]
    for r, row in enumerate(rows):
        for c, val in enumerate(row):
            cell = table.cell(r, c)
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell, top=0.07, bottom=0.07, start=0.09, end=0.09)
            if r == 0:
                set_cell_shading(cell, LIGHT_BLUE)
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.15
            add_rich(p, val, size=8.0, color=NAVY if r == 0 else INK, bold=(r == 0))
    for row in table.rows:
        cant_split(row)


def add_explain_item(doc, num, answer, text, *, after=3):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.95)
    p.paragraph_format.first_line_indent = Cm(-0.95)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(0.95))
    p.paragraph_format.line_spacing = 1.23
    p.paragraph_format.space_after = Pt(after)
    add_rich(p, f"{num}．", size=9.5, color=NAVY, bold=True)
    add_run(p, "\t", size=9.5, color=INK)
    add_rich(p, answer, size=9.5, color=GOLD, bold=True)
    add_rich(p, "　解析：", size=9.5, color=NAVY, bold=True)
    add_rich(p, text, size=9.5, color=INK)
    return p


def add_caption(doc, text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(3)
    add_rich(p, text, size=8.2, color=MUTED)


def add_exam_figure(doc, filename, width_cm, caption):
    return bx.add_figure(doc, filename, width_cm, caption)


def add_solution_block(doc, num, topic, first_text, *, points=None):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.95)
    p.paragraph_format.first_line_indent = Cm(-0.95)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(0.95))
    p.paragraph_format.space_before = Pt(5)
    p.paragraph_format.space_after = Pt(0)
    add_rich(p, f"{num}．", size=10.2, color=NAVY, bold=True)
    add_run(p, "\t", size=10.2, color=INK)
    add_rich(p, first_text, size=9.9, color=INK)
    tp = doc.add_paragraph()
    tp.paragraph_format.left_indent = Cm(0.95)
    tp.paragraph_format.space_after = Pt(2)
    add_rich(tp, "考点：", size=8.3, color=GOLD, bold=True)
    add_rich(tp, topic, size=8.3, color=GRAY)
    if points:
        add_rich(tp, f"　（本题{points}分）", size=8.3, color=MUTED)
    return p


def build():
    doc = Document()
    page_setup(doc.sections[0])
    normal = doc.styles["Normal"]
    normal.font.name = bx.BODY_FONT
    normal.font.size = Pt(9.9)
    normal.font.color.rgb = bx.rgb(INK)
    normal.paragraph_format.line_spacing = 1.25
    normal.paragraph_format.space_after = Pt(0)
    normal._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), bx.CN_FONT)

    settings = doc.settings.element
    update = OxmlElement("w:updateFields")
    update.set(qn("w:val"), "true")
    settings.append(update)

    add_header(doc.sections[0], "参考答案与详解 · 八年级数学（北师大版）")
    add_footer(doc.sections[0])

    # title
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(1)
    add_rich(p, "教师用 · 全解全析", size=8.5, color=GOLD, bold=True)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(1)
    add_rich(p, "参考答案与详解", size=22, color=NAVY, bold=True)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(4)
    add_rich(p, "八年级数学（北师大版 · 第1—4章）", size=10, color=GRAY)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(7)
    add_rich(p, "━━━━━━━━━━━━━━━━━━━━", size=8.5, color=GOLD)

    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_width(table, bx.CONTENT_W)
    clear_table_borders(table)
    cell = table.cell(0, 0)
    set_cell_shading(cell, PALE)
    set_cell_margins(cell, top=0.16, bottom=0.16, start=0.32, end=0.32)
    set_cell_border(cell, left=(GOLD, 22))
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.18
    add_rich(p, "使用建议：", size=8.6, color=NAVY, bold=True)
    add_rich(p, "先独立完成试卷，再对照答案；解答题重点看思路与得分点，不必拘泥于文字表述。", size=8.6, color=GRAY)
    spacer = doc.add_paragraph()
    spacer.paragraph_format.space_before = Pt(0)
    spacer.paragraph_format.space_after = Pt(0)
    spacer.paragraph_format.line_spacing = Pt(5)

    add_overview(doc)
    add_structure(doc)
    add_chapter_table(doc)

    add_heading(doc, "一、选择题解析", "每小题3分，共18分")
    add_explain_item(doc, 1, "C", "√9=3，0.333…与22/7都是有理数；π/2是无理数．")
    add_explain_item(doc, 2, "C", "因为3²+4²=5²，所以3，4，5能作为直角三角形三边长；其余各组均不满足勾股定理的逆定理．")
    add_explain_item(doc, 3, "A", "点在y轴上，则横坐标为0，所以a-2=0，a=2，纵坐标3a+6=12，故P(0,12)．")
    add_explain_item(doc, 4, "C", "k=-2<0，b=4>0，图象经过第一、二、四象限，不经过第三象限．")
    add_explain_item(doc, 5, "A", "斜边长为√(6²+8²)=10．由面积法，斜边上的高h满足6×8=10h，所以h=4.8．")
    add_explain_item(doc, 6, "C", "直线与y轴交于(0,3)，与x轴交于(-3/k,0)．由面积1/2×3×|−3/k|=6，得|k|=3/4，所以k=±3/4．", after=5)

    add_heading(doc, "二、填空题解析", "每小题3分，共18分")
    add_explain_item(doc, 7, "2", "√16=4，4的算术平方根是2．注意“算术平方根”只取非负值．")
    add_explain_item(doc, 8, "m<2", "一次函数y随x的增大而减小，说明一次项系数小于0，即m-2<0，所以m<2．")
    add_explain_item(doc, 9, "(2,0)", "设P(p,0)．由PA=PB，得(p-1)²+3²=(p-5)²+(-1)²，解得p=2，所以P(2,0)．也可由AB的垂直平分线与x轴的交点求得．")
    add_explain_item(doc, 10, "5 或 √7", "题中只说明“两边长”，没有说明4一定是直角边．若3，4都是直角边，则第三边为5；若4是斜边，则第三边为√(4²-3²)=√7．")
    add_explain_item(doc, 11, "5", "作A(1,3)关于x轴的对称点A′(1,-3)，则PA+PB=PA′+PB≥A′B=√[(4-1)²+(1+3)²]=5．当P为A′B与x轴的交点时取等号．")
    add_explain_item(doc, 12, "1，7，-1", "由x²=16得x=±4，由y²=9得y=±3．结合x>y分类：x=4，y=3时差为1；x=4，y=-3时差为7；x=-4，y=-3时差为-1；x=-4，y=3不满足x>y．", after=2)
    add_tip(
        doc,
        "易错提醒",
        "第10题不要默认4是直角边；第12题要先用平方根求出所有可能值，再用x>y筛选，不能漏掉x=-4的情况．",
        kind="warn",
    )

    add_heading(doc, "三、解答题详解", "第13—23题，共84分")
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(2)
    add_rich(p, "以下解答给出主要步骤与得分点，其他合理解法同样给分。", size=8.5, color=MUTED)

    # 13
    add_solution_block(doc, 13, "平方根、立方根与解方程", "解方程：", points=6)
    add_solution_line(doc, "（1）由4(x-1)²=25，得(x-1)²=25/4，所以x-1=±5/2．", indent=0.95, after=1)
    add_solution_line(doc, "解得x=7/2或x=-3/2．……（3分）", indent=0.95, after=2)
    add_solution_line(doc, "（2）由27(x+2)³=-64，得(x+2)³=-64/27，所以x+2=-4/3．", indent=0.95, after=1)
    add_solution_line(doc, "解得x=-10/3．……（3分）", indent=0.95, after=5)

    # 14
    add_solution_block(doc, 14, "绝对值、零指数幂、负整数指数幂、二次根式与立方根", "计算：", points=6)
    add_solution_line(doc, "（1）原式=2+1-3-2=-2．……（3分）", indent=0.95, after=2)
    add_solution_line(doc, "（2）原式=4-(2-√2)+2=4+√2．……（3分）", indent=0.95, after=5)

    # 15
    add_solution_block(doc, 15, "平方根、立方根的定义", "解：由平方根、立方根的定义求a，b，再求平方根．", points=6)
    add_solution_line(doc, "因为2a-1的平方根是±3，所以2a-1=9，a=5．……（2分）", indent=0.95, after=1)
    add_solution_line(doc, "因为a+b的立方根是2，所以a+b=8，即5+b=8，b=3．……（2分）", indent=0.95, after=1)
    add_solution_line(doc, "所以a²+b²=5²+3²=34，其平方根为±√34．……（2分）", indent=0.95, after=2)
    add_tip(doc, "易错提醒", "一个正数的平方根有两个，别把“平方根”误写成“算术平方根”而漏掉负值．", kind="warn")

    # 16
    add_solution_block(doc, 16, "勾股定理、方程思想", "解：设BD=x，把两条斜边上的勾股关系转化为方程．", points=6)
    add_solution_line(doc, "设BD=x，则CD=14-x．在Rt△ABD和Rt△ACD中，AD²=AB²-BD²=AC²-CD²，所以", indent=0.95, after=1)
    add_eq(doc, "13²-x²=15²-(14-x)²．")
    add_solution_line(doc, "解得x=5．所以AD=√(13²-5²)=12．", indent=0.95, after=1)
    add_solution_line(doc, "S△ABC=1/2×14×12=84．……（6分）", indent=0.95, after=2)
    add_tip(doc, "思路点拨", "本题的关键是抓住两个直角三角形共高AD，用BD表示CD，列方程求BD；也可以设AD=h，用勾股定理列方程组．")

    # 17
    add_solution_block(doc, 17, "关于y轴对称的坐标变化、坐标与面积", "解：先由关于y轴对称的坐标规律写出对称点，再用水平底求面积．", points=6)
    add_solution_line(doc, "（1）A(-5,1)，B(-2,-3)，C(-1,1)关于y轴的对称点分别为", indent=0.95, after=1)
    add_solution_line(doc, "A₁(5,1)，B₁(2,-3)，C₁(1,1)．……（4分）", indent=0.95, after=2)
    add_solution_line(doc, "（2）AC=4，点B到AC的距离为4，所以S△ABC=1/2×4×4=8．……（2分）", indent=0.95, after=2)
    add_tip(doc, "思路点拨", "关于y轴对称，纵坐标不变、横坐标互为相反数；求面积时以AC为水平底，高就是B到AC的竖直距离．")

    # 18
    add_solution_block(doc, 18, "待定系数法、一次函数图象平移、勾股定理", "解：先求解析式，再求平移后的交点，最后用勾股定理求CD．", points=8)
    add_solution_line(doc, "（1）把A(-2,0)，B(0,4)代入y=kx+b，得b=4，-2k+b=0，解得k=2．", indent=0.95, after=1)
    add_solution_line(doc, "所以一次函数的表达式为y=2x+4．……（4分）", indent=0.95, after=2)
    add_solution_line(doc, "（2）将直线AB向左平移2个单位，得y=2(x+2)+4=2x+8；", indent=0.95, after=1)
    add_solution_line(doc, "再向上平移3个单位，得l：y=2x+11．", indent=0.95, after=1)
    add_solution_line(doc, "令y=0，得x=-11/2；令x=0，得y=11．所以C(-11/2,0)，D(0,11)．", indent=0.95, after=1)
    add_solution_line(doc, "CD=√[(11/2)²+11²]=√(605/4)=11√5/2，S△OCD=1/2×11/2×11=121/4．……（4分）", indent=0.95, after=2)
    add_tip(doc, "思路点拨", "平移不改变一次项系数k；求CD时，把C，D的坐标差看作直角三角形的两条直角边，直接用勾股定理．")

    # 19
    add_solution_block(doc, 19, "勾股定理及其逆定理、割补法", "解：连接AC，把四边形分成两个三角形．", points=8)
    add_solution_line(doc, "连接AC．在Rt△ABC中，AC=√(3²+4²)=5．……（2分）", indent=0.95, after=1)
    add_solution_line(doc, "在△ACD中，AC²+CD²=5²+12²=169=13²=AD²，所以△ACD是直角三角形，∠ACD=90°．……（3分）", indent=0.95, after=1)
    add_solution_line(doc, "所以S四边形ABCD=S△ABC+S△ACD=1/2×3×4+1/2×5×12=6+30=36．……（3分）", indent=0.95, after=2)
    add_exam_figure(doc, "sol19_quadrilateral.png", 4.8, "解析图1　连接AC，构造两个直角三角形")
    add_tip(doc, "思路点拨", "先由Rt△ABC求出AC=5，再看5，12，13恰好满足勾股定理的逆定理，从而把四边形面积转化为两个直角三角形面积之和．")

    # 20
    add_solution_block(doc, 20, "折叠的性质、勾股定理、方程思想", "解：折叠产生全等，把已知长度集中到Rt△ECF中．", points=8)
    add_solution_line(doc, "由折叠可知AF=AD=10，EF=ED．", indent=0.95, after=1)
    add_solution_line(doc, "在Rt△ABF中，BF=√(AF²-AB²)=√(10²-8²)=6，所以FC=10-6=4．……（4分）", indent=0.95, after=1)
    add_solution_line(doc, "设CE=x，则DE=8-x，EF=8-x．在Rt△ECF中，由勾股定理得", indent=0.95, after=1)
    add_eq(doc, "x²+4²=(8-x)²，解得x=3．")
    add_solution_line(doc, "所以CE=3．……（4分）", indent=0.95, after=2)
    add_exam_figure(doc, "sol20_folding.png", 4.2, "解析图2　设CE=x，用x表示EF")
    add_tip(doc, "思路点拨", "折叠前后的对应线段相等：AF=AD=10，EF=ED；先求BF，再求FC，最后在Rt△ECF中列方程．")

    # 21
    add_solution_block(doc, 21, "长方体表面最短路径、展开思想、分类比较", "解：把相邻两个面展开，比较三种矩形对角线．", points=9)
    add_solution_line(doc, "将长方体相邻的两个面展开，从A到B的最短路线可转化为三种矩形对角线：", indent=0.95, after=1)
    add_eq(doc, "① √((5+4)²+3²)=√90=3√10；")
    add_eq(doc, "② √((5+3)²+4²)=√80=4√5；")
    add_eq(doc, "③ √((4+3)²+5²)=√74．")
    add_solution_line(doc, "因为√74<√80<√90，所以蚂蚁爬行的最短路程为√74 cm．……（9分）", indent=0.95, after=2)
    add_exam_figure(doc, "sol21_nets.png", 12.8, "解析图3　三种展开方式下的矩形对角线")
    add_tip(doc, "思路点拨", "求长方体表面最短路径，不能只展开一种情况；应把经过相邻两面的三种可能都画出来，分别用勾股定理计算，再取最小值．")

    # 22
    p22 = add_solution_block(doc, 22, "勾股数规律、代数恒等式、勾股定理的逆定理", "解：先观察第一数与序号的关系，再验证平方关系．", points=9)
    p22.paragraph_format.page_break_before = True
    add_solution_line(doc, "（1）第4组勾股数是9，40，41．……（2分）", indent=0.95, after=1)
    add_solution_line(doc, "（2）第n组勾股数为2n+1，2n²+2n，2n²+2n+1．……（2分）", indent=0.95, after=1)
    add_solution_line(doc, "证明：因为", indent=0.95, after=1)
    add_eq(doc, "(2n+1)²+(2n²+2n)²")
    add_eq(doc, "=4n²+4n+1+4n⁴+8n³+4n²")
    add_eq(doc, "=4n⁴+8n³+8n²+4n+1")
    add_eq(doc, "=(2n²+2n+1)²，")
    add_solution_line(doc, "所以以这三个数为边长的三角形是直角三角形．……（2分）", indent=0.95, after=1)
    add_solution_line(doc, "（3）由2n+1=2025，得n=1012．", indent=0.95, after=1)
    add_solution_line(doc, "所以另外两个数为2×1012²+2×1012=2050312，2050313．……（3分）", indent=0.95, after=2)
    add_tip(doc, "思路点拨", "第一数3，5，7，…是2n+1；后两数相差1，且中间数等于(2n+1)²-1的一半，即2n²+2n．证明时把三边平方，验证前两边的平方和等于第三边的平方．")

    # 23
    add_solution_block(doc, 23, "一次函数与坐标轴交点、两直线交点、面积、线段垂直平分线、等腰三角形分类讨论", "解：先求坐标与交点，再按等腰三角形的三种情况分类．", points=12)
    add_solution_line(doc, "（1）令y=0，得x=-2，所以A(-2,0)；令x=0，得y=2，所以B(0,2)．", indent=0.95, after=1)
    add_solution_line(doc, "令y=0，得x=4，所以C(4,0)；令x=0，得y=8，所以D(0,8)．……（4分）", indent=0.95, after=2)
    add_solution_line(doc, "（2）联立y=x+2与y=-2x+8，得x=2，y=4，所以P(2,4)．", indent=0.95, after=1)
    add_solution_line(doc, "过P作y轴的平行线交BC于点E．直线BC的表达式为y=-1/2x+2，故E(2,1)，PE=3．", indent=0.95, after=1)
    add_solution_line(doc, "所以S△PBC=1/2×3×2+1/2×3×2=6．", indent=0.95, after=1)
    add_solution_line(doc, "设BC的垂直平分线交x轴于Q₀(q,0)．由Q₀B=Q₀C，得q²+2²=(q-4)²，解得q=3/2，所以Q₀(3/2,0)．……（4分）", indent=0.95, after=2)
    add_solution_line(doc, "（3）设Q(q,0)．因为B(0,2)，C(4,0)，所以BC=√[(4-0)²+(0-2)²]=2√5．", indent=0.95, after=1)
    add_solution_line(doc, "① 当BQ=BC时，q²+2²=20，解得q=±4．q=4时Q与C重合，不能构成三角形，舍去，故q=-4．", indent=0.95, after=1)
    add_solution_line(doc, "② 当CQ=BC时，|q-4|=2√5，解得q=4±2√5．", indent=0.95, after=1)
    add_solution_line(doc, "③ 当BQ=CQ时，q²+4=(q-4)²，解得q=3/2．", indent=0.95, after=1)
    add_solution_line(doc, "综上，满足条件的点Q有四个：(-4,0)，(4+2√5,0)，(4-2√5,0)，(3/2,0)．……（4分）", indent=0.95, after=2)
    add_tip(doc, "易错提醒", "等腰三角形要按“哪两条边相等”分类，不能只看图形；q=4时Q与C重合，是增根，必须舍去．", kind="warn")

    out = OUT / "八年级上册数学阶段检测_参考答案与详解_北师大版.docx"
    doc.save(out)
    print(out)
    return out


if __name__ == "__main__":
    build()

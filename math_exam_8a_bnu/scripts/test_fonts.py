from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.oxml.ns import qn

doc = Document()
p = doc.add_paragraph()
def run(text, font='Times New Roman', size=12, sup=False, sub=False):
    r = p.add_run(text)
    r.font.name = font
    r._element.get_or_add_rPr().rFonts.set(qn('w:eastAsia'), 'Songti SC')
    r.font.size = Pt(size)
    r.font.superscript = sup
    r.font.subscript = sub
    return r
run('汉字测试：')
for ch in ['√9', '∛(-8)', '△ABC', '∠B=90°', 'AD⊥BC', 'π/2', '±3', '4√2', '≌', '′']:
    run(ch, font='STIX Two Math')
    run('  ')
run('x'); run('2', sup=True); run('，A'); run('1', sub=True); run('，l'); run('1', sub=True)
doc.save('math_exam_8a_bnu/test/font_test.docx')

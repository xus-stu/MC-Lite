# -*- coding: utf-8 -*-
"""对最终 DOCX 做内容、编号、字数和结构自检。"""

import re
from pathlib import Path

from docx import Document
from docx.oxml.ns import qn

from cheers import SECTIONS

DOCX = Path(__file__).with_name("初中运动会加油稿200则.docx")
doc = Document(DOCX)

entries = []
for p in doc.paragraphs:
    m = re.match(r"^(\d{2,3})\t(.+)$", p.text)
    if not m:
        continue
    text = m.group(2).strip()
    if text in {
        "按项目检索  十个篇章对应不同项目，可按现场需要快速取用。",
        "灵活组合  同一篇章的句子可以连读，组成一段完整的加油词。",
        "替换细节  把“班级”“少年”换成具体班级与姓名，会更贴近现场。",
    }:
        continue
    entries.append((m.group(1), text))

expected = []
num = 1
for section in SECTIONS:
    for line in section["lines"]:
        expected.append((f"{num:02d}", line))
        num += 1

print("提取条数:", len(entries))
print("预期条数:", len(expected))
print("编号顺序一致:", [n for n, _ in entries] == [n for n, _ in expected])
print("正文逐条一致:", [t for _, t in entries] == [t for _, t in expected])

mismatch = [(i + 1, a, b) for i, (a, b) in enumerate(zip(entries, expected)) if a != b]
print("不一致:", len(mismatch))
for row in mismatch[:5]:
    print(row)

lengths = [len(re.findall(r"[\u4e00-\u9fff]", t)) for _, t in entries]
print("汉字数范围:", min(lengths), "-", max(lengths))
print("15—25字合规:", all(15 <= n <= 25 for n in lengths))
print("完全重复:", len(entries) - len({t for _, t in entries}))

# 分段计数
counts = []
pos = 0
for s in SECTIONS:
    counts.append(len(s["lines"]))
print("每章条数:", counts, "合计:", sum(counts))

# 文档结构
all_text = "\n".join(p.text for p in doc.paragraphs)
for table in doc.tables:
    for row in table.rows:
        for cell in row.cells:
            all_text += "\n" + cell.text
for needle in ["奔跑吧，", "写在前面", "青春开场", "跳绳与趣味项目", "终点与远方", "奔跑，是青春最好的注脚"]:
    print(f"包含“{needle}”:", needle in all_text)

# 页码字段
footer_xml = doc.sections[0].footer._element.xml
print("页脚 PAGE 字段:", "PAGE" in footer_xml)
print("首页独立页脚:", doc.sections[0].different_first_page_header_footer)

print("DOCX 大小:", DOCX.stat().st_size, "bytes")

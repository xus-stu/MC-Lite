# -*- coding: utf-8 -*-
"""校验加油稿的字数、重复度与相似度。"""

import re
from collections import Counter
from itertools import combinations

from cheers import SECTIONS


def cjk_count(text):
    return len(re.findall(r"[\u4e00-\u9fff]", text))


def grams(text, n=4):
    chars = re.sub(r"[^\u4e00-\u9fff]", "", text)
    return {chars[i:i + n] for i in range(max(0, len(chars) - n + 1))}


def lcs_len(a, b):
    a = re.sub(r"[^\u4e00-\u9fff]", "", a)
    b = re.sub(r"[^\u4e00-\u9fff]", "", b)
    prev = [0] * (len(b) + 1)
    best = 0
    for ca in a:
        cur = [0]
        for j, cb in enumerate(b, 1):
            v = prev[j - 1] + 1 if ca == cb else 0
            cur.append(v)
            best = max(best, v)
        prev = cur
    return best


all_lines = []
for section in SECTIONS:
    for line in section["lines"]:
        all_lines.append((section["title"], line))

print("总条数:", len(all_lines))

lengths = [cjk_count(line) for _, line in all_lines]
print("汉字数范围:", min(lengths), "-", max(lengths))
bad = [(i + 1, n, line) for i, ((_, line), n) in enumerate(zip(all_lines, lengths)) if not 15 <= n <= 25]
print("字数不合规:", len(bad))
for row in bad:
    print(row)

visible = [len(re.sub(r"\s", "", line)) for _, line in all_lines]
print("含标点长度范围:", min(visible), "-", max(visible))

dups = [line for line, count in Counter(line for _, line in all_lines).items() if count > 1]
print("完全重复:", len(dups), dups)

pairs = []
for (sa, a), (sb, b) in combinations(all_lines, 2):
    ga, gb = grams(a), grams(b)
    jac = len(ga & gb) / len(ga | gb) if ga | gb else 0
    pairs.append((jac, lcs_len(a, b), sa, a, sb, b))
pairs.sort(reverse=True)

jacs = [p[0] for p in pairs]
print("四字片段平均 Jaccard:", round(sum(jacs) / len(jacs), 4))
print("四字片段最大 Jaccard:", round(max(jacs), 4))
print("Jaccard > 0.35 的对数:", sum(1 for j in jacs if j > 0.35))
print("Jaccard > 0.50 的对数:", sum(1 for j in jacs if j > 0.50))
print("\n最相似的 15 对：")
for jac, lcs, sa, a, sb, b in pairs[:15]:
    print(f"{jac:.3f} LCS={lcs:2d} [{sa}] {a} || [{sb}] {b}")

print("\n最长公共子串 >= 8 的对数:", sum(1 for p in pairs if p[1] >= 8))

# 高频四字片段，观察是否套话过多
counter = Counter()
for _, line in all_lines:
    counter.update(grams(line))
print("\n最常见的 25 个四字片段：")
for g, c in counter.most_common(25):
    print(g, c)

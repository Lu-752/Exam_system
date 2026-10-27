# -*- coding: utf-8 -*-
"""从操作手册的排版脚本派生一份部署文档专用脚本（只改路径/封面/页眉等常量）。"""
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

SRC_DIR = r"C:\Users\lxq\Desktop\exam-sys\output\20261026-manual\working"
DST_DIR = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\working"
os.makedirs(DST_DIR, exist_ok=True)

# ---------- md2html.py ----------
s = open(os.path.join(SRC_DIR, "md2html.py"), encoding="utf-8-sig").read()

s = s.replace(
    r'SRC = r"C:\Users\lxq\Desktop\exam-sys\output\20261026-manual\stage1\final_draft.md"',
    r'SRC = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\stage1\deploy.md"',
)
s = s.replace(
    r'DST = r"C:\Users\lxq\Desktop\exam-sys\output\20261026-manual\stage2\formatted-考试系统操作手册.html"',
    r'DST = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\stage2\formatted-考试系统部署文档.html"',
)
s = s.replace("考试系统操作手册　V0.1.0", "考试系统部署文档　V0.1.0")
s = s.replace("<title>考试系统 操作手册</title>", "<title>考试系统 部署文档</title>")

NEW_COVER = '''COVER = """
<section role="cover">
  <div class="cover">
    <p class="cv-top">文档编号 EXAM-DP-001　　密级 内部</p>
    <p class="cv-title">考试系统 部署文档</p>
    <p class="cv-sub">环境准备 · 四种部署方式 · 验收 · 运维</p>
    <p class="cv-meta">版本　V0.1.0<br/>适用对象　实施工程师 / 运维人员<br/>发布日期　2026 年 10 月</p>
  </div>
</section>
"""'''
s, n = re.subn(r'COVER = """[\s\S]*?"""', NEW_COVER, s, count=1)
assert n == 1, "COVER 未替换"
assert "20261027-deploy" in s, "SRC/DST 未替换"
open(os.path.join(DST_DIR, "md2html.py"), "w", encoding="utf-8", newline="\n").write(s)
print("md2html.py ->", len(s), "chars")

# ---------- postprocess_toc.py ----------
p = open(os.path.join(SRC_DIR, "postprocess_toc.py"), encoding="utf-8-sig").read()
p = p.replace(
    'BASE = "C:/Users/lxq/Desktop/exam-sys/output/20261026-manual/"',
    'BASE = "C:/Users/lxq/Desktop/exam-sys/output/20261027-deploy/"',
)
p = p.replace(
    'PDF = "C:/Users/lxq/Desktop/exam-sys/shots/manual-v3.pdf"',
    'PDF = "C:/Users/lxq/Desktop/exam-sys/shots/deploy-v1.pdf"',
)
# 文档名：考试系统部署文档（用 lambda 避免 re 解析 \u 转义）
p = re.sub(r'NAME = "(?:\\u[0-9a-f]{4})+"',
           lambda m: 'NAME = "\\u8003\\u8bd5\\u7cfb\\u7edf\\u90e8\\u7f72\\u6587\\u6863"', p, count=1)
# 正文起点标记：本文解决一件事
p = re.sub(r'BODY_MARK = "(?:\\u[0-9a-f]{4})+"',
           lambda m: 'BODY_MARK = "\\u672c\\u6587\\u89e3\\u51b3\\u4e00\\u4ef6\\u4e8b"', p, count=1)
assert "20261027-deploy" in p and "deploy-v1.pdf" in p, "路径未替换"
open(os.path.join(DST_DIR, "postprocess_toc.py"), "w", encoding="utf-8", newline="\n").write(p)
print("postprocess_toc.py ->", len(p), "chars")
for line in p.splitlines():
    if line.startswith(("BASE", "NAME", "SRC", "DST", "PDF", "BODY_MARK")):
        print("   ", line)

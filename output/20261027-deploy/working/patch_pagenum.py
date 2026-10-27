# -*- coding: utf-8 -*-
"""补两处：①封面计入第 1 页，正文页码从 2 起，消除「共22页但末页显示21」；
②目录页码随之 +1，与页脚一致。"""
import io

P = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\working\postprocess_toc.py"
s = io.open(P, encoding="utf-8").read()

FUNC = '''def fix_pagenum(doc, start=2):
    """第 1 节是封面（无页脚），第 2 节正文若从 1 起编，末页会比 NUMPAGES 少 1。
    把各节起始页号设为 2，让正文首页显示「第 2 页 / 共 N 页」。"""
    from docx.oxml.ns import qn

    for sec in doc.sections:
        el = sec._sectPr.find(qn("w:pgNumType"))
        if el is None:
            continue
        el.set(qn("w:start"), str(start))


'''

s = s.replace("def main():", FUNC + "def main():", 1)
s = s.replace("    apply_breaks(doc)\n    style_toc(doc)\n",
              "    apply_breaks(doc)\n    fix_pagenum(doc)\n    style_toc(doc)\n", 1)
s = s.replace('    pages, total = heading_pages(PDF, titles)\n',
              '    pages, total = heading_pages(PDF, titles)\n'
              '    pages = {k: v + 1 for k, v in pages.items()}  # 页序 0 基 -> 页码，封面占 1\n', 1)

io.open(P, "w", encoding="utf-8", newline="").write(s)
print("patched", len(s))

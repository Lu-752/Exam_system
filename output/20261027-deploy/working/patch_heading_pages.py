# -*- coding: utf-8 -*-
"""heading_pages：用 BODY_MARK 定位正文起始页，再按整行精确匹配标题。"""
import io

P = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\working\postprocess_toc.py"
s = io.open(P, encoding="utf-8").read()

NEW = '''def heading_pages(pdf_path, titles):
    import pymupdf

    doc = pymupdf.open(pdf_path)

    def norm(x):
        return x.replace(" ", "").replace("\\u3000", "").replace("\\t", "").strip()

    pages_lines = []
    for i in range(doc.page_count):
        pages_lines.append([norm(l) for l in doc[i].get_text("text").splitlines() if norm(l)])

    # 正文起点：第一处出现 BODY_MARK 的页；它之前是封面与目录，必须排除，
    # 否则目录行里的标题会把自己命中（中间版 PDF 的目录还没有点线引导符）。
    body_start = 0
    for i, lines in enumerate(pages_lines):
        if any(BODY_MARK in l for l in lines):
            body_start = i
            break

    found = {}
    for title in titles:
        key = norm(title)
        for i in range(body_start, len(pages_lines)):
            if key in pages_lines[i]:
                found[title] = i
                break
    return found, doc.page_count
'''

start = s.index("def heading_pages(")
end = s.index("def main():")
s = s[:start] + NEW + "\n\n" + s[end:]
io.open(P, "w", encoding="utf-8", newline="").write(s)
print("patched", len(s))

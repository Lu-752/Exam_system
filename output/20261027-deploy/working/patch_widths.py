# -*- coding: utf-8 -*-
"""三线表列宽按首列内容长度自适应：首列放长参数名时给它更多宽度。"""
import io

P = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\working\md2html.py"
s = io.open(P, encoding="utf-8-sig").read()

OLD = '            widths = {2: [30, 70], 3: [20, 40, 40], 4: [16, 20, 22, 42]}.get(len(header))'

NEW = '''            n = len(header)
            if n == 2:
                widths = [30, 70]
            elif n == 3:
                longest = max(len(r[0]) for r in ([header] + rows) if r)
                # 首列是 --spring.profiles.active 这类长参数名时，20% 会折行
                widths = [30, 34, 36] if longest > 12 else [20, 40, 40]
            elif n == 4:
                widths = [16, 20, 22, 42]
            else:
                widths = None'''

assert OLD in s
s = s.replace(OLD, NEW, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("widths adaptive")

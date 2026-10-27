# -*- coding: utf-8 -*-
"""编号列表：Word 忽略 <ol start>，改为对"非从 1 起"的列表直接写入序号（悬挂缩进）。"""
import io

P = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\working\md2html.py"
s = io.open(P, encoding="utf-8-sig").read()

OLD = '''            # 中间插了代码块/图片时，源文件里仍写着 2. 3. 4.，不能重新从 1 起编
            start_attr = ' start="%d"' % first_no if first_no != 1 else ""
            out.append("<ol%s>" % start_attr
                       + "".join("<li>%s</li>" % inline(x) for x in items) + "</ol>")
            continue'''

NEW = '''            # 中间插了代码块/图片时，源文件里仍写着 2. 3. 4.，不能重新从 1 起编。
            # Word 不认 <ol start>，这类列表直接把序号写进正文（悬挂缩进对齐）。
            if first_no != 1:
                seq = []
                for k, x in enumerate(items):
                    seq.append('<p class="stepno">%d.\\u3000%s</p>' % (first_no + k, inline(x)))
                out.append("".join(seq))
            else:
                out.append("<ol>" + "".join("<li>%s</li>" % inline(x) for x in items) + "</ol>")
            continue'''

assert OLD in s
s = s.replace(OLD, NEW, 1)

CSS_OLD = 'li { margin-bottom: 3pt; line-height: 1.75; }'
CSS_NEW = ('li { margin-bottom: 3pt; line-height: 1.75; }\n'
           'p.stepno { margin: 0 0 3pt 20pt; text-indent: -20pt; line-height: 1.75; }')
assert CSS_OLD in s
s = s.replace(CSS_OLD, CSS_NEW, 1)

io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("md2html patched for stepno")

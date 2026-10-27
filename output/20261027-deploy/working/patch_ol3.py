# -*- coding: utf-8 -*-
"""编号列表：代码块夹在中间时并进上一条，保持同一个 <ol>，编号不重来。"""
import io

P = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\working\md2html.py"
s = io.open(P, encoding="utf-8-sig").read()

start = s.index("        # 列表\n        if re.match")
end = s.index('        if stripped.startswith("- "):')

NEW = '''        # 列表（编号列表若被代码块打断，把代码块并进上一条，保持同一个 <ol>）
        if re.match(r"^\\d+\\.\\s+", stripped):
            first_no = int(re.match(r"^(\\d+)\\.", stripped).group(1))
            items = []

            def take_items(pos, first_no, items):
                while pos < len(lines) and re.match(r"^\\d+\\.\\s+", lines[pos].strip()):
                    items.append([re.sub(r"^\\d+\\.\\s+", "", lines[pos].strip()), []])
                    pos += 1
                return pos

            i = take_items(i, first_no, items)
            while True:
                k = i
                while k < len(lines) and not lines[k].strip():
                    k += 1
                if k >= len(lines) or not lines[k].strip().startswith("```"):
                    break
                k += 1
                buf = []
                while k < len(lines) and not lines[k].strip().startswith("```"):
                    buf.append(lines[k])
                    k += 1
                k += 1
                m2 = k
                while m2 < len(lines) and not lines[m2].strip():
                    m2 += 1
                nxt = re.match(r"^(\\d+)\\.\\s+", lines[m2].strip()) if m2 < len(lines) else None
                if not nxt or int(nxt.group(1)) != first_no + len(items):
                    break
                items[-1][1].append(
                    '<table class="codebox"><tr><td><pre>%s</pre></td></tr></table>'
                    % html.escape("\\n".join(buf)))
                i = take_items(m2, first_no, items)

            out.append("<ol>" + "".join(
                "<li>%s%s</li>" % (inline(t), "".join(ex)) for t, ex in items) + "</ol>")
            continue

'''

s = s[:start] + NEW + s[end:]
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("list branch rewritten")

# -*- coding: utf-8 -*-
"""修 md2html：编号列表保留源文件里写的起始序号（代码块打断后不再从 1 重来）。"""
import io

P = r"C:\Users\lxq\Desktop\exam-sys\output\20261027-deploy\working\md2html.py"
s = io.open(P, encoding="utf-8-sig").read()

OLD = '''        if re.match(r"^\\d+\\.\\s+", stripped):
            items = []
            while i < len(lines) and re.match(r"^\\d+\\.\\s+", lines[i].strip()):
                items.append(re.sub(r"^\\d+\\.\\s+", "", lines[i].strip()))
                i += 1
            out.append("<ol>" + "".join("<li>%s</li>" % inline(x) for x in items) + "</ol>")
            continue'''

NEW = '''        if re.match(r"^\\d+\\.\\s+", stripped):
            items = []
            first_no = int(re.match(r"^(\\d+)\\.", stripped).group(1))
            while i < len(lines) and re.match(r"^\\d+\\.\\s+", lines[i].strip()):
                items.append(re.sub(r"^\\d+\\.\\s+", "", lines[i].strip()))
                i += 1
            # 中间插了代码块/图片时，源文件里仍写着 2. 3. 4.，不能重新从 1 起编
            start_attr = ' start="%d"' % first_no if first_no != 1 else ""
            out.append("<ol%s>" % start_attr
                       + "".join("<li>%s</li>" % inline(x) for x in items) + "</ol>")
            continue'''

assert OLD in s, "list branch not found"
s = s.replace(OLD, NEW, 1)
io.open(P, "w", encoding="utf-8", newline="\n").write(s)
print("md2html patched")

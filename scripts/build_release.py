# -*- coding: utf-8 -*-
"""打开发行包：jar + 自带 Java 运行时 + 启停/校验脚本 + 文档 + 校验清单。

流程：
1) 构建前端 (vite) -> apps/admin-web/dist
2) 将 dist 拷入 server/src/main/resources/static，使 Spring Boot 直接托管前端
3) mvn package 生成可执行 jar（已含前端）
4) jlink 从本机 JDK 剪出专用运行时 release/runtime（Windows x64，约 50MB）
5) 组装 release/：jar + runtime + bin 启停脚本 + README/BUILD-INFO + 说明文档
6) 生成 SHA256SUMS.txt（逐文件校验清单）
7) 打包后自检（必需文件、jar 内 static、指纹一致、无演示库）
8) 压缩为 release.zip（先写 .new 再原子替换）

用法：
    python scripts/build_release.py                  # 全量
    python scripts/build_release.py --rebuild-runtime # 强制重做 jlink 运行时
    python scripts/build_release.py --skip-frontend   # 前端已构建过，跳过 vite
"""
import datetime
import hashlib
import os
import platform
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = r"C:\Users\lxq\Desktop\exam-sys"
JAVA_HOME = r"C:\Users\lxq\.workbuddy\binaries\jdk\21"
MVN = r"C:\Users\lxq\.workbuddy\binaries\maven\3.9.16\bin\mvn.cmd"
NODE = r"C:\Users\lxq\.workbuddy\binaries\node\versions\22.22.2-3\node.exe"
NPM = r"C:\Users\lxq\.workbuddy\binaries\node\versions\22.22.2-3\node_modules\npm\bin\npm-cli.js"
GIT = r"C:\Users\lxq\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe"

VERSION = "0.1.0"
JAR_NAME = "exam-server-%s.jar" % VERSION
ARCH = "x64"

# Spring Boot / Tomcat / Jackson / MyBatis-Plus / H2 / PostgreSQL 驱动所需模块
JLINK_MODULES = ",".join([
    "java.base", "java.compiler", "java.desktop", "java.instrument", "java.logging",
    "java.management", "java.naming", "java.net.http", "java.prefs", "java.rmi",
    "java.scripting", "java.security.jgss", "java.security.sasl", "java.sql",
    "java.transaction.xa", "java.xml", "java.xml.crypto", "jdk.crypto.cryptoki",
    "jdk.crypto.ec", "jdk.management", "jdk.naming.dns", "jdk.unsupported", "jdk.zipfs",
])

# 包内脚本/文档：源路径 -> 包内路径
PACK_FILES = [
    (r"bin\start.bat", "bin/start.bat"),
    (r"bin\stop.bat", "bin/stop.bat"),
    (r"bin\install-postgres.bat", "bin/install-postgres.bat"),
    (r"bin\run.sh", "bin/run.sh"),
    (r"bin\stop.sh", "bin/stop.sh"),
    (r"bin\verify.bat", "bin/verify.bat"),
    (r"bin\verify.sh", "bin/verify.sh"),
    (r"安装与操作说明.md", "安装与操作说明.md"),
    (r"packaging\README.txt", "README.txt"),
]
# zip 内需保留可执行位的文件（Linux 解压后直接可跑）
EXEC_SUFFIX = (".sh",)


def run(cmd, cwd=None, env=None):
    print(">>", " ".join(cmd) if isinstance(cmd, list) else cmd)
    r = subprocess.run(cmd, cwd=cwd, env=env, capture_output=True)
    out = (r.stdout or b"").decode("utf-8", "ignore")
    err = (r.stderr or b"").decode("gbk", "ignore")
    if out.strip():
        print(out[-2500:])
    if err.strip():
        print(err[-3500:])
    return r.returncode


def walk_files(release_dir):
    """包内应纳入校验与打包的文件（不含 data/ 用户数据与 logs/ 运行日志）。"""
    out = []
    for dp, dns, fns in os.walk(release_dir):
        dns[:] = [d for d in dns if d not in ("data", "logs")]
        for f in fns:
            out.append(os.path.relpath(os.path.join(dp, f), release_dir).replace("\\", "/"))
    return sorted(out)


def sha256(path, n=16):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()[:n] if n else h.hexdigest()


def dir_size(path):
    total = 0
    for dp, _dn, fn in os.walk(path):
        for f in fn:
            try:
                total += os.path.getsize(os.path.join(dp, f))
            except OSError:
                pass
    return total


def mb(n):
    return round(n / 1048576, 1)


def git_rev():
    if not os.path.exists(GIT):
        return "-"
    try:
        r = subprocess.run([GIT, "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True)
        rev = (r.stdout or b"").decode("utf-8", "ignore").strip() or "-"
        r2 = subprocess.run([GIT, "log", "-1", "--pretty=%s"], cwd=ROOT, capture_output=True)
        subj = (r2.stdout or b"").decode("utf-8", "ignore").strip()
        return "%s  %s" % (rev, subj) if subj else rev
    except Exception:
        return "-"


def java_version(runtime_dir):
    """读 runtime/release 文件里的 JAVA_VERSION，取不到再跑一次 java -version。"""
    rel = os.path.join(runtime_dir, "release")
    if os.path.exists(rel):
        try:
            txt = open(rel, encoding="utf-8", errors="ignore").read()
            m = re.search(r'JAVA_VERSION="([^"]+)"', txt)
            if m:
                return m.group(1)
        except Exception:
            pass
    try:
        r = subprocess.run([os.path.join(runtime_dir, "bin", "java.exe"), "-version"],
                           capture_output=True)
        return (r.stderr or b"").decode("utf-8", "ignore").splitlines()[0].strip()
    except Exception:
        return "-"


def build_runtime(release_dir):
    """用 jlink 剪裁专用运行时；已存在且未强制重建则复用。"""
    target = os.path.join(release_dir, "runtime")
    if os.path.exists(os.path.join(target, "bin", "java.exe")) and "--rebuild-runtime" not in sys.argv:
        print("复用现有 runtime：", target, mb(dir_size(target)), "MB")
        return
    tmp = os.path.join(release_dir, "_runtime_tmp")
    if os.path.isdir(tmp):
        shutil.rmtree(tmp)
    jlink = os.path.join(JAVA_HOME, "bin", "jlink.exe")
    rc = run([jlink, "--add-modules", JLINK_MODULES,
              "--strip-debug", "--no-header-files", "--no-man-pages",
              "--compress=zip-6", "--output", tmp])
    if rc != 0 or not os.path.exists(os.path.join(tmp, "bin", "java.exe")):
        sys.exit("jlink 生成运行时失败")
    if os.path.isdir(target):
        shutil.rmtree(target)
    shutil.move(tmp, target)
    print("runtime ->", target, mb(dir_size(target)), "MB")


def pad(s, width):
    """按显示宽度补齐（中文按 2 列计），让 BUILD-INFO 里的指纹列对齐。"""
    w = sum(2 if ord(ch) > 0x2E80 else 1 for ch in s)
    return s + " " * max(0, width - w)


def write_build_info(release_dir, jar_path):
    runtime = os.path.join(release_dir, "runtime")
    jar_mb = mb(os.path.getsize(jar_path))
    rt_mb = mb(dir_size(runtime))
    total_mb = mb(dir_size(release_dir))
    files = len(walk_files(release_dir)) + 1  # + SHA256SUMS.txt 自身
    now = datetime.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %z")

    fps = [("exam-server-%s.jar" % VERSION, jar_path)]
    for rel in [p[1] for p in PACK_FILES]:
        fp = os.path.join(release_dir, rel.replace("/", os.sep))
        if os.path.exists(fp):
            fps.append((rel, fp))
    fp_lines = "\n".join("  %s%s" % (pad(name, 32), sha256(path)) for name, path in fps)

    text = """考试系统（Exam System）发行包
============================================================
版本         %(ver)s
构建时间     %(now)s
构建主机     %(host)s %(osver)s (%(arch)s)
目标平台     Windows %(arch)s（包内自带 Java 运行时，目标机无需安装 JDK）
运行时       jlink 裁剪，Java %(jv)s
Git          %(git)s
包大小       %(total)s MB（jar %(jar)s MB + runtime %(rt)s MB）
文件数       %(files)d

启动入口     bin\\start.bat           前台启动，三种安装方式
             bin\\start.bat -d        后台启动（日志 logs\\app.log）
             bin\\stop.bat            停止
             bin\\verify.bat          完整性校验
默认端口     8080（可用 --port=9090 修改）
数据目录     data\\（首次启动自动生成 exam.mv.db）

源码指纹（SHA256 前 16 位，用于核对包内文件与源码一致）
%(fps)s
============================================================
构建方式     python scripts\\build_release.py
""" % dict(ver=VERSION, now=now, host=platform.node(), osver=platform.version(),
           arch=ARCH, jv=java_version(runtime), git=git_rev(), total=total_mb,
           jar=jar_mb, rt=rt_mb, files=files, fps=fp_lines)

    with open(os.path.join(release_dir, "BUILD-INFO.txt"), "w", encoding="utf-8-sig", newline="\r\n") as fh:
        fh.write(text)
    print("BUILD-INFO.txt ->", VERSION, total_mb, "MB,", files, "files")
    return total_mb


def write_checksums(release_dir):
    """为包内文件生成 SHA256SUMS.txt（data/ 与 logs/ 除外）。"""
    rels = [r for r in walk_files(release_dir) if r != "SHA256SUMS.txt"]
    lines = []
    for rel in rels:
        full = os.path.join(release_dir, rel.replace("/", os.sep))
        lines.append("%s  %s" % (sha256(full, 0), rel))
    lines.sort(key=lambda x: x.split("  ", 1)[1])
    with open(os.path.join(release_dir, "SHA256SUMS.txt"), "w", encoding="utf-8", newline="\r\n") as fh:
        fh.write("\n".join(lines) + "\n")
    print("SHA256SUMS.txt ->", len(lines), "个文件")
    return len(lines), rels


def self_check(release_dir, jar_path, sum_count, sum_rels):
    """打包后自检：缺件、jar 内静态资源、校验清单与实际文件一致、演示库是否混入。"""
    print("==== 自检 ====")
    problems = []
    need = ["exam-server-%s.jar" % VERSION, "runtime/bin/java.exe", "runtime/bin/javaw.exe",
            "bin/start.bat", "bin/stop.bat", "bin/run.sh", "bin/stop.sh",
            "bin/verify.bat", "bin/verify.sh", "README.txt", "BUILD-INFO.txt",
            "SHA256SUMS.txt", "安装与操作说明.md"]
    for rel in need:
        if not os.path.exists(os.path.join(release_dir, rel.replace("/", os.sep))):
            problems.append("缺少 " + rel)
    try:
        with zipfile.ZipFile(jar_path) as z:
            if "BOOT-INF/classes/static/index.html" not in z.namelist():
                problems.append("jar 内缺少前端静态资源 static/index.html")
    except Exception as e:
        problems.append("jar 无法读取：%s" % e)

    actual = [r for r in walk_files(release_dir) if r != "SHA256SUMS.txt"]
    if sum_count != len(actual) or sorted(sum_rels) != sorted(actual):
        problems.append("校验清单 %d 条与实际文件 %d 个不一致" % (sum_count, len(actual)))
    for r in actual:
        if r.startswith("data/") or r.startswith("logs/"):
            problems.append("用户数据混入包内：" + r)

    for db in ("exam.mv.db", "exam.trace.db"):
        if os.path.exists(os.path.join(release_dir, "data", db)):
            print("  [提示] data/%s 是本地试跑残留，已在打包时排除，不会进 zip" % db)

    problems.extend(check_script_newlines(release_dir))

    if problems:
        for p in problems:
            print("  [FAIL]", p)
        sys.exit("自检未通过，未生成 zip")
    print("  必需文件 %d 项齐全，jar 内含前端，校验清单 %d 条与目录一致 —— 通过"
          % (len(need), sum_count))


def check_script_newlines(release_dir):
    """脚本编码/换行符自检，防止“双击闪退”类发布事故。

    .bat：必须 GBK 编码 + 全 CRLF + 无 BOM + 不含 chcp 65001。
          UTF-8 无 BOM 的 bat 在中文 Windows 上首屏中文会被按 936 拆错，
          cmd 连带吞掉后续关键字，表现为闪退或“xx 不是内部或外部命令”。
          裸 LF 则会让 cmd 逐句错位解析，同样闪退。
    .sh ：必须全 LF，否则 bash 报 $'\\r': command not found。
    """
    problems = []
    for rel in ("bin/start.bat", "bin/stop.bat", "bin/install-postgres.bat", "bin/verify.bat"):
        p = os.path.join(release_dir, rel.replace("/", os.sep))
        raw = open(p, "rb").read()
        bare_lf = raw.count(b"\n") - raw.count(b"\r\n")
        if bare_lf:
            problems.append("%s 有 %d 处裸 LF 换行（.bat 必须 CRLF，否则 cmd 解析错位闪退）" % (rel, bare_lf))
        if raw.startswith(b"\xef\xbb\xbf"):
            problems.append("%s 带 UTF-8 BOM（cmd 会把它当命令执行）" % rel)
        try:
            raw.decode("gbk")
        except UnicodeDecodeError:
            problems.append("%s 不是 GBK 编码（中文 Windows 下会显示乱码/解析失败）" % rel)
        if b"chcp 65001" in raw:
            problems.append("%s 含 chcp 65001（GBK 编码的 bat 不能切 UTF-8 代码页）" % rel)
    for rel in ("bin/run.sh", "bin/stop.sh", "bin/verify.sh"):
        p = os.path.join(release_dir, rel.replace("/", os.sep))
        if b"\r\n" in open(p, "rb").read():
            problems.append("%s 含 CRLF（bash 会报 $'\\r': command not found）" % rel)
    return problems


def write_zip(release_dir, zip_path):
    """先写临时文件再原子替换，避免半成品覆盖旧包；保留 .sh 可执行位。"""
    tmp = zip_path + ".new"
    if os.path.exists(tmp):
        os.remove(tmp)
    count = 0
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for dp, dns, fns in os.walk(release_dir):
            dns[:] = [d for d in dns if d not in ("data", "logs")]
            for f in sorted(fns):
                fp = os.path.join(dp, f)
                arc = os.path.relpath(fp, ROOT).replace("\\", "/")
                info = zipfile.ZipInfo.from_file(fp, arc)
                info.compress_type = zipfile.ZIP_DEFLATED
                if f.endswith(EXEC_SUFFIX):
                    info.external_attr = (0o100755 << 16) | 0o600
                    info.create_system = 3  # unix
                else:
                    info.create_system = 3
                    info.external_attr = (0o100644 << 16) | 0o600
                with open(fp, "rb") as src, z.open(info, "w") as dst:
                    shutil.copyfileobj(src, dst, 1024 * 1024)
                count += 1
    os.replace(tmp, zip_path)
    return count


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    skip_frontend = "--skip-frontend" in sys.argv

    if not skip_frontend:
        print("==== [1/8] 构建前端 ====")
        if run([NODE, NPM, "run", "build", "-w", "apps/admin-web"], cwd=ROOT) != 0:
            sys.exit("前端构建失败")
    else:
        print("==== [1/8] 跳过前端构建 ====")

    print("==== [2/8] 拷贝前端到后端 static ====")
    static = os.path.join(ROOT, "server/src/main/resources/static")
    if os.path.isdir(static):
        shutil.rmtree(static)
    shutil.copytree(os.path.join(ROOT, "apps/admin-web/dist"), static)

    print("==== [3/8] mvn package ====")
    subprocess.run(["taskkill", "/F", "/IM", "java.exe"], capture_output=True)  # 释放被占用的 jar
    env = dict(os.environ)
    env["JAVA_HOME"] = JAVA_HOME
    env["PATH"] = JAVA_HOME + r"\bin" + os.pathsep + env.get("PATH", "")
    if run([MVN, "-q", "-DskipTests", "package"], cwd=os.path.join(ROOT, "server"), env=env) != 0:
        sys.exit("后端打包失败")

    jar_src = os.path.join(ROOT, "server/target", JAR_NAME)
    if not os.path.exists(jar_src):
        sys.exit("未找到构建产物 " + jar_src)

    print("==== [4/8] 组装 release/ ====")
    rel = os.path.join(ROOT, "release")
    os.makedirs(rel, exist_ok=True)
    # 清理上一版遗留的打包产物（data/ 与 logs/ 保留，属于用户数据）
    for name in ("README.md",):
        p = os.path.join(rel, name)
        if os.path.exists(p):
            os.remove(p)
    shutil.copy(jar_src, os.path.join(rel, JAR_NAME))

    for src_rel, dst_rel in PACK_FILES:
        src = os.path.join(ROOT, src_rel.replace("\\", os.sep))
        if not os.path.exists(src):
            sys.exit("缺少打包源文件：" + src_rel)
        dst = os.path.join(rel, dst_rel.replace("/", os.sep))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if dst_rel.endswith(".bat"):
            # .bat 必须 GBK 编码 + CRLF + 无 BOM，并且不能依赖 chcp 65001。
            # 原因：cmd 解析首行时还在系统代码页（中文 Windows = 936），
            # 若文件是 UTF-8 无 BOM，首屏中文会被按双字节拆错、连带吞掉后续关键字，
            # 表现为双击后闪退或 “xx 不是内部或外部命令”。
            # 源文件允许 UTF-8 或 GBK，这里统一读进来再以 GBK 写出。
            raw_src = open(src, "rb").read()
            if raw_src.startswith(b"\xef\xbb\xbf"):
                raw_src = raw_src[3:]
            for enc in ("utf-8", "gbk"):
                try:
                    text = raw_src.decode(enc)
                    break
                except UnicodeDecodeError:
                    continue
            else:
                sys.exit("%s 既不是 UTF-8 也不是 GBK，请检查编码" % dst_rel)
            if "chcp 65001" in text:
                sys.exit("%s 含 chcp 65001，请移除后再打包（GBK 编码的 bat 不能切到 65001）" % dst_rel)
            raw = text.replace("\r\n", "\n").replace("\n", "\r\n").encode("gbk")
            with open(dst, "wb") as fh:
                fh.write(raw)
            print("copy", dst_rel, "(gbk)")
            continue
        if dst_rel.endswith(".sh"):
            # .sh 必须是 LF：CRLF 会让 bash 报 $'\r': command not found
            with open(src, "rb") as fh:
                raw = fh.read().replace(b"\r\n", b"\n")
            with open(dst, "wb") as fh:
                fh.write(raw)
        elif dst_rel.endswith(".txt"):
            # 纯文本加 BOM，Windows 记事本直接双击不乱码
            with open(src, encoding="utf-8") as fh:
                content = fh.read()
            with open(dst, "w", encoding="utf-8-sig", newline="\r\n") as fh:
                fh.write(content)
        else:
            shutil.copy(src, dst)
        print("copy", dst_rel)

    print("==== [5/8] 生成 Java 运行时（jlink）====")
    build_runtime(rel)

    print("==== [6/8] 生成 BUILD-INFO.txt ====")
    jar_path = os.path.join(rel, JAR_NAME)
    total_mb = write_build_info(rel, jar_path)

    print("==== [7/8] 生成 SHA256 校验清单 ====")
    sum_count, sum_rels = write_checksums(rel)

    self_check(rel, jar_path, sum_count, sum_rels)

    print("==== [8/8] 压缩 release.zip ====")
    zip_path = os.path.join(ROOT, "release.zip")
    count = write_zip(rel, zip_path)
    print("release.zip ->", mb(os.path.getsize(zip_path)), "MB,", count, "个文件")
    print("release/ 总计 ->", total_mb, "MB")
    print("\n打包完成。目标机解压后双击 release\\bin\\start.bat（无需安装 Java）。")


if __name__ == "__main__":
    main()

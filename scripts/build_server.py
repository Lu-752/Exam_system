import os
import subprocess
import sys

ROOT = r"C:\Users\lxq\Desktop\exam-sys"
JAVA_HOME = r"C:\Users\lxq\.workbuddy\binaries\jdk\21"
MVN = r"C:\Users\lxq\.workbuddy\binaries\maven\3.9.16\bin\mvn.cmd"

# 先杀掉占用 jar 的进程，避免 repackage 重命名失败
subprocess.run(["taskkill", "/F", "/IM", "java.exe"], capture_output=True)

env = dict(os.environ)
env["JAVA_HOME"] = JAVA_HOME
env["PATH"] = JAVA_HOME + r"\bin" + os.pathsep + env.get("PATH", "")

p = subprocess.run([MVN, "-q", "-DskipTests", "package"], cwd=os.path.join(ROOT, "server"),
                   env=env, capture_output=True)
out = p.stdout.decode("utf-8", "ignore")
err = p.stderr.decode("gbk", "ignore")
print("RC:", p.returncode)
print(out[-4000:])
print(err[-6000:])
sys.exit(p.returncode)

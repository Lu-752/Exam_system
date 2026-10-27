#!/usr/bin/env bash
# 考试系统 启动脚本（Linux / macOS）
# 用法：./run.sh [-d] [--port=9090]
#   -d          后台运行，日志 logs/app.log，PID 写 logs/app.pid
#   --port=NNNN 指定端口，默认 8080
set -e
cd "$(dirname "$0")/.."

PORT=8080
BG=0
for a in "$@"; do
  case "$a" in
    -d|--daemon) BG=1 ;;
    --port=*) PORT="${a#--port=}" ;;
  esac
done

JAR=$(ls exam-server-*.jar server/target/exam-server-*.jar 2>/dev/null | head -1 || true)
if [ -z "$JAR" ]; then
  echo "未找到 exam-server-*.jar，请先执行 scripts/build_release.py 打包"
  exit 1
fi

# 优先使用包内自带运行时（Windows 包内为 windows 版，Linux 需系统 Java）
JAVA="java"
if [ -x "runtime/bin/java" ]; then
  JAVA="runtime/bin/java"
elif [ -x "runtime/bin/java.exe" ]; then
  echo "[提示] 包内运行时为 Windows 版，当前系统将使用已安装的 Java。"
fi
if ! command -v "$JAVA" >/dev/null 2>&1 && [ ! -x "$JAVA" ]; then
  echo "未检测到可用的 Java：包内运行时不可用，请安装 JDK 21+ 后重试。"
  exit 1
fi

echo "============================================================"
echo "         考试系统（Exam System）启动器"
echo "============================================================"
echo "  1) 内置演示库（开箱即用，零安装）"
echo "  2) 连接已有 PostgreSQL（表自动创建）"
echo "  3) 内置 PostgreSQL（自动安装并建库）"
echo "------------------------------------------------------------"
echo "  端口 $PORT　后台：$BG（1=后台）"
echo "============================================================"
read -r -p "请选择安装方式 [1/2/3，回车默认1]: " MODE
MODE=${MODE:-1}

ARGS=()
case "$MODE" in
  1)
    ARGS=(--server.port="$PORT" --spring.profiles.active=dev)
    echo "[模式1] 内置演示库，数据写入 ./data/exam.mv.db"
    ;;
  2)
    read -r -p "JDBC URL [jdbc:postgresql://127.0.0.1:5432/exam_sys]: " DB_URL
    read -r -p "用户名 [exam]: " DB_USER
    read -r -p "密码 [exam]: " DB_PASS
    DB_URL=${DB_URL:-jdbc:postgresql://127.0.0.1:5432/exam_sys}
    DB_USER=${DB_USER:-exam}; DB_PASS=${DB_PASS:-exam}
    ARGS=(--server.port="$PORT" --spring.profiles.active=pg --DB_URL="$DB_URL" --DB_USER="$DB_USER" --DB_PASSWORD="$DB_PASS")
    echo "[模式2] 连接 $DB_URL ..."
    ;;
  3)
    echo "[模式3] 安装 PostgreSQL ..."
    if command -v apt-get >/dev/null; then sudo apt-get update && sudo apt-get install -y postgresql; fi
    if command -v dnf >/dev/null; then sudo dnf install -y postgresql-server postgresql-contrib; sudo postgresql-setup --initdb || true; sudo systemctl enable --now postgresql; fi
    if command -v yum >/dev/null; then sudo yum install -y postgresql-server; sudo postgresql-setup initdb; sudo systemctl enable --now postgresql; fi
    sudo -u postgres psql -c "CREATE ROLE exam LOGIN PASSWORD 'exam';" 2>/dev/null || true
    sudo -u postgres psql -c "CREATE DATABASE exam_sys OWNER exam;" 2>/dev/null || true
    ARGS=(--server.port="$PORT" --spring.profiles.active=pg --DB_URL=jdbc:postgresql://127.0.0.1:5432/exam_sys --DB_USER=exam --DB_PASSWORD=exam)
    echo "[模式3] 启动（exam_sys / exam / exam）..."
    ;;
  *)
    echo "无效选择，使用模式1"
    ARGS=(--server.port="$PORT" --spring.profiles.active=dev)
    ;;
esac

mkdir -p logs
echo "$PORT" > logs/app.port

if [ "$BG" = "1" ]; then
  nohup "$JAVA" -jar "$JAR" "${ARGS[@]}" --logging.file.name=logs/app.log >/dev/null 2>&1 &
  echo $! > logs/app.pid
  echo "已在后台启动，PID $(cat logs/app.pid)，日志 logs/app.log"
  echo "约 10~20 秒后可访问：http://localhost:$PORT"
  echo "停止：bin/stop.sh"
else
  echo "启动后浏览器打开 http://localhost:$PORT  （Ctrl+C 停止）"
  exec "$JAVA" -jar "$JAR" "${ARGS[@]}"
fi

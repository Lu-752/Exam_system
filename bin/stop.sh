#!/usr/bin/env bash
# 考试系统 停止脚本（Linux / macOS）
# 用法：./stop.sh [端口]
set -e
cd "$(dirname "$0")/.."

PORT="${1:-$(cat logs/app.port 2>/dev/null || echo 8080)}"
echo "============================================================"
echo "  停止端口 $PORT 上的考试系统"
echo "============================================================"

stopped=0

if [ -f logs/app.pid ]; then
  PID=$(cat logs/app.pid 2>/dev/null || true)
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    echo "  结束进程 PID=$PID"
    kill "$PID" 2>/dev/null || true
    for _ in $(seq 1 15); do
      kill -0 "$PID" 2>/dev/null || break
      sleep 1
    done
    kill -9 "$PID" 2>/dev/null || true
    stopped=1
  fi
fi

if [ "$stopped" = "0" ]; then
  # 退回按端口找 Java 进程
  PID=$( (ss -ltnp 2>/dev/null || netstat -ltnp 2>/dev/null) \
          | grep ":$PORT " | grep -o 'pid=[0-9]*' | head -1 | cut -d= -f2 || true)
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    echo "  结束占用端口 $PORT 的进程 PID=$PID"
    kill "$PID" 2>/dev/null || true
    sleep 3
    kill -9 "$PID" 2>/dev/null || true
    stopped=1
  fi
fi

if [ "$stopped" = "0" ]; then
  echo "  未发现在 $PORT 端口监听的进程（可能已停止）。"
else
  echo "  已停止。"
fi

rm -f logs/app.pid logs/app.port

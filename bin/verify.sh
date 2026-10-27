#!/usr/bin/env bash
# 考试系统 发行包完整性校验（Linux / macOS）
set -u
cd "$(dirname "$0")/.."

if [ ! -f SHA256SUMS.txt ]; then
  echo "[错误] 未找到 SHA256SUMS.txt，请确认使用完整的发行包。"
  exit 1
fi

echo "============================================================"
echo "            考试系统 发行包完整性校验"
echo "============================================================"

if command -v sha256sum >/dev/null 2>&1; then
  if sha256sum -c --quiet SHA256SUMS.txt; then
    echo "校验通过：所有文件一致。"
    exit 0
  else
    echo "校验失败：存在缺失或损坏的文件，请重新解压发行包。"
    exit 1
  fi
elif command -v shasum >/dev/null 2>&1; then
  if shasum -a 256 -c SHA256SUMS.txt --quiet; then
    echo "校验通过：所有文件一致。"
    exit 0
  else
    echo "校验失败：存在缺失或损坏的文件，请重新解压发行包。"
    exit 1
  fi
else
  echo "[提示] 未找到 sha256sum / shasum，可跳过校验。"
  exit 0
fi

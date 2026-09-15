#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "用法: $0 '你的卡密'"
  exit 1
fi

# 仅用于需要哈希时的调试；正常管理卡密不需要使用本脚本。
printf '%s' "$1" | sha256sum | awk '{print $1}'

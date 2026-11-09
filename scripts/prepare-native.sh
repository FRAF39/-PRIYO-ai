#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/llama.cpp"
if [ ! -d "$DEST/.git" ]; then
  git clone --depth 1 https://github.com/ggml-org/llama.cpp "$DEST"
else
  git -C "$DEST" pull --ff-only
fi
echo "llama.cpp source prepared at $DEST"

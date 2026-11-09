#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/models/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf"
URL="https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf"
mkdir -p "$(dirname "$DEST")"
if command -v curl >/dev/null 2>&1; then curl -L --fail --retry 3 "$URL" -o "$DEST"
else wget -O "$DEST" "$URL"; fi
echo "Downloaded model to $DEST"

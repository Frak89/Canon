#!/usr/bin/env bash
set -euo pipefail
# Pin known working multimodal llama.cpp native source.
COMMIT="56381e407c0ccfb3a6f71e668a27a901001d22ce"
EXPECTED="7fd19c03e7d7bb02c07e64c47e0539aeabe54f366a59be822f550b5f012a8751"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/third_party/llama.cpp"
if [[ -f "$TARGET/CMakeLists.txt" ]]; then
    echo "Pinned llama.cpp source already present"
    exit 0
fi
mkdir -p "$ROOT/third_party"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
curl --fail --location --retry 3 --max-time 180 \
    "https://github.com/ggml-org/llama.cpp/archive/${COMMIT}.tar.gz" \
    -o "$TEMP/source.tgz"
echo "$EXPECTED  $TEMP/source.tgz" | sha256sum -c -
mkdir -p "$TEMP/source"
tar -xzf "$TEMP/source.tgz" -C "$TEMP/source" --strip-components=1
mv "$TEMP/source" "$TARGET"
echo "Pinned llama.cpp at $COMMIT"

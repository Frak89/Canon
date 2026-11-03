#!/data/data/com.termux/files/usr/bin/bash
# AmpyEOS on-device vision setup (Pixel 7 Pro / Termux).
# Installs/builds llama.cpp locally, downloads the OFFICIAL Qwen3-VL 2B
# Q4_K_M language model and Q8_0 vision projector, then starts an
# OpenAI-compatible HTTP server bound only to 127.0.0.1.
# This is a setup helper, not a runtime built into AmpyEOS itself.
set -euo pipefail
echo "AmpyEOS: Install Qwen3-VL 2B multimodal (about 1.6 GiB download)."
echo "Keep several additional GiB free for build and memory. 4G/5G may incur charges."
echo "The local server only accepts connections from this device."
if ! command -v pkg >/dev/null 2>&1; then
  echo "This script requires the Termux package manager on Android." >&2
  exit 1
fi
pkg update -y
pkg install -y clang cmake ninja git curl
ROOT="$HOME/ampyeos-qwen"
mkdir -p "$ROOT/models"
cd "$ROOT"
if [ ! -d llama.cpp/.git ]; then
  git clone --depth 1 https://github.com/ggml-org/llama.cpp.git
fi
cmake -S llama.cpp -B llama.cpp/build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_OPENMP=OFF -DGGML_VULKAN=OFF \
  -DLLAMA_BUILD_SERVER=ON
cmake --build llama.cpp/build --target llama-server -j 3

BASE="https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF/resolve/main"
download_if_needed() {
  local name="$1" min_bytes="$2"
  if [ -f "$ROOT/models/$name" ] && \
       [ "$(wc -c < "$ROOT/models/$name")" -ge "$min_bytes" ]; then
    echo "Using existing $name"
    return
  fi
  # -C - lets an interrupted 4G/5G download resume.
  curl --fail --location --retry 3 --retry-delay 3 -C - \
    "$BASE/$name" -o "$ROOT/models/$name"
  if [ "$(wc -c < "$ROOT/models/$name")" -lt "$min_bytes" ]; then
    echo "The downloaded model appears truncated: $name" >&2
    exit 1
  fi
}
download_if_needed "Qwen3VL-2B-Instruct-Q4_K_M.gguf" 950000000
download_if_needed "mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf" 400000000
echo "Qwen3-VL ready. Starting local llama-server on 127.0.0.1:8080"
echo "Leave this Termux session running while using QWEN-VL in AmpyEOS."
exec "$ROOT/llama.cpp/build/bin/llama-server" \
  -m "$ROOT/models/Qwen3VL-2B-Instruct-Q4_K_M.gguf" \
  --mmproj "$ROOT/models/mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf" \
  --host 127.0.0.1 --port 8080 --ctx-size 4096 \
  --threads 4 --no-mmproj-offload

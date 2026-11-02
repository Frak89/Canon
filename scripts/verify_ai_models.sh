#!/usr/bin/env bash
set -euo pipefail

# Check the exact Google-hosted assets used by ExtraRecognition.java.
# The official packages must retain these hashes; if they change, do not
# silently trust new model bytes.
MODEL_URL="https://storage.googleapis.com/download.tensorflow.org/models/tflite_11_05_08/mobilenet_v2_1.0_224.tgz"
LABELS_URL="https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt"
MODEL_HASH="9f3bc29e38e90842a852bfed957dbf5e36f2d97a91dd17736b1e5c0aca8d3303"
LABELS_HASH="536feacc519de3d418de26b2effb4d75694a8c4c0063e36499a46fa8061e2da9"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT

echo "Checking official TensorFlow assets (models only, no personal images)..."
curl --fail --location --silent --show-error --retry 2 --connect-timeout 15 --max-time 150 \
  "$MODEL_URL" -o "$TEMP/model.tgz"
curl --fail --location --silent --show-error --retry 2 --connect-timeout 15 --max-time 60 \
  "$LABELS_URL" -o "$TEMP/labels.txt"

NAME="$(tar -tzf "$TEMP/model.tgz" | grep -E '(^|/)mobilenet_v2_1.0_224\.tflite$' | head -n 1)"
if [ -z "$NAME" ]; then
  echo "Missing expected TFLite entry in archive" >&2
  exit 1
fi
tar -xOzf "$TEMP/model.tgz" "$NAME" > "$TEMP/model.tflite"
# Compile the production Android archive parser as pure Java and run it
# against the real official package. This prevents false-positive APK builds
# that compile but fail when extracting the model on a Pixel.
mkdir -p "$TEMP/classes"
javac -d "$TEMP/classes" \
  app/src/main/java/com/ampyeos/canon/ModelArchive.java \
  scripts/CheckModelArchive.java
java -cp "$TEMP/classes" CheckModelArchive "$TEMP/model.tgz" "$TEMP/model-java.tflite"
cmp "$TEMP/model.tflite" "$TEMP/model-java.tflite"
echo "Android ModelArchive: extracted bytes identical to GNU tar."
ARCHIVE_BYTES="$(stat -c '%s' "$TEMP/model.tgz")"
MODEL_BYTES="$(stat -c '%s' "$TEMP/model.tflite")"
LABEL_BYTES="$(stat -c '%s' "$TEMP/labels.txt")"
echo "Official package sizes: archive=${ARCHIVE_BYTES} bytes model=${MODEL_BYTES} bytes labels=${LABEL_BYTES} bytes."
# Match the Android app's new safety bounds; don't regress to a too-small cap.
test "$ARCHIVE_BYTES" -le $((512*1024*1024))
test "$MODEL_BYTES" -le $((256*1024*1024))
test "$LABEL_BYTES" -le $((2*1024*1024))
echo "$MODEL_HASH  $TEMP/model.tflite" | sha256sum -c -
echo "$LABELS_HASH  $TEMP/labels.txt" | sha256sum -c -
COUNT="$(awk 'NF { count++ } END { print count+0 }' "$TEMP/labels.txt")"
if [ "$COUNT" -ne 1001 ]; then
  echo "Wrong ImageNet label count: $COUNT, expected 1001" >&2
  exit 1
fi
echo "Verified MobileNetV2 model, 1001 labels and pinned SHA-256 digests."

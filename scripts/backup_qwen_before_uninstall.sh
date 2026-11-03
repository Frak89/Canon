#!/usr/bin/env bash
set -euo pipefail

# Rescue GGUF files from an OLDER debug APK's app-private files
# BEFORE uninstalling it for an APK signed with a different debug key.
# No root, no network, no photo access; uses Android debug build's run-as.
# Requires an already-connected USB/Wi-Fi ADB device.
#
# On Android, redirect runs as the ADB shell account (not as the app UID),
# enabling writing to /sdcard/Download while run-as reads the private model.
PACKAGE=com.ampyeos.canon
SOURCE=files/ampyeos_qwen_vision
DEST=/sdcard/Download/AmpyEOS/Qwen3-VL
MODEL=Qwen3VL-2B-Instruct-Q4_K_M.gguf
PROJECTOR=mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf
M_SHA=089d75c52f4b7ffc56ba998ffc50aae89fcafc755f9e7208aacca281dca6c2ae
P_SHA=f9a68fabba69c3b81e153367b2c7521030b0fa8bb0de400c9599c8e6725f9c82

command -v adb >/dev/null || { echo "adb non trovato. Installa Android platform-tools."; exit 1; }
adb get-state >/dev/null || { echo "Collega il Pixel e autorizza il debug ADB."; exit 1; }

echo "Verifico che la vecchia AmpyEOS DEBUG sia ancora installata..."
adb shell "run-as $PACKAGE ls $SOURCE/" || {
  echo "Impossibile leggere la vecchia cache. NON disinstallare se i GGUF devono essere salvati."
  echo "Questo script funziona con APK debug che supportano run-as."
  exit 1
}
echo "Spazio libero nella memoria condivisa:"
adb shell df -h /sdcard
adb shell "mkdir -p '$DEST'"

rescue() {
  local name="$1" expected="$2"
  echo "Salvo $name in Download (nessun download Internet)..."
  # The redirect must be performed by the Android shell, not by run-as.
  adb shell "run-as '$PACKAGE' cat '$SOURCE/$name' > '$DEST/$name'" || {
    echo "Copia fallita: $name. Non disinstallare la vecchia app."
    exit 1
  }
  local actual
  actual="$(adb shell "sha256sum '$DEST/$name'" | tr -d '\r' | awk '{print $1}')"
  if [[ "$actual" != "$expected" ]]; then
    echo "Hash non valido: $name. Copia incompleta. NON disinstallare."
    exit 1
  fi
  echo "SHA-256 OK: $name"
}

rescue "$MODEL" "$M_SHA"
rescue "$PROJECTOR" "$P_SHA"
echo
echo "Backup Qwen completo e verificato in $DEST."
echo "Ora puoi installare AmpyEOS v0.8.1; se Android ti obbliga a disinstallare"
echo "la vecchia APK, i due file Download resteranno intatti."
echo "Dopo la reinstallazione, usa IMPORTA GGUF DA DOWNLOAD se necessario."

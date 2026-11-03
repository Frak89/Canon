# AmpyEOS GGUF rescue for Windows PowerShell (ADB / Android SDK Platform Tools).
# Run BEFORE uninstalling the OLD debug APK.
# Streams files entirely on the Android device: no Internet, no PC copy, no root.
$ErrorActionPreference = "Stop"
$package = "com.ampyeos.canon"
$source = "files/ampyeos_qwen_vision"
$destination = "/sdcard/Download/AmpyEOS/Qwen3-VL"

$models = @(
    @{Name = "Qwen3VL-2B-Instruct-Q4_K_M.gguf"; Hash = "089d75c52f4b7ffc56ba998ffc50aae89fcafc755f9e7208aacca281dca6c2ae"},
    @{Name = "mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf"; Hash = "f9a68fabba69c3b81e153367b2c7521030b0fa8bb0de400c9599c8e6725f9c82"}
)
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb non trovato. Installa Android SDK Platform Tools e aggiungi adb a PATH."
}
& adb get-state | Out-Host
if ($LASTEXITCODE -ne 0) { throw "Collega il Pixel e autorizza il debug USB/Wi-Fi." }
& adb shell "run-as $package ls '$source/'" | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "La vecchia cache non è leggibile: NON disinstallare l'APK prima di salvarla."
}
& adb shell "df -h /sdcard" | Out-Host
& adb shell "mkdir -p '$destination'"
if ($LASTEXITCODE -ne 0) { throw "Non posso creare la cartella Download." }

foreach ($item in $models) {
    $name = $item.Name
    Write-Host "Salvo $name nella cartella Download (zero download Internet)..."
    $shellCommand = "run-as $package cat '$source/$name' > '$destination/$name'"
    & adb shell $shellCommand | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Copia fallita di $name. NON disinstallare l'app." }
    $line = & adb shell "sha256sum '$destination/$name'"
    if ($LASTEXITCODE -ne 0) { throw "sha256sum non disponibile sul Pixel." }
    $actual = (($line | Out-String).Trim() -split '\s+')[0].ToLowerInvariant()
    if ($actual -ne $item.Hash) {
        throw "SHA-256 non valido per $name. NON disinstallare: il file non è integro."
    }
    Write-Host "Verifica SHA-256 OK: $name"
}
Write-Host ""
Write-Host "Qwen salvato correttamente in $destination."
Write-Host "Dopo l'installazione di AmpyEOS 0.8.1 puoi ricollegare i file con IMPORTA GGUF DA DOWNLOAD."

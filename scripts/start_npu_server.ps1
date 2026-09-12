# Starts the Snapdragon Hexagon NPU daemon on the connected Android phone via ADB

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb"
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Starting Snapdragon Hexagon NPU Server  " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# Check device
$device = & $adb devices | Select-String "device$"
if (-not $device) {
    Write-Host "Error: No Android device detected via ADB." -ForegroundColor Red
    exit 1
}

# Kill any previous instance
& $adb shell "pkill -9 -f llama-server"
Start-Sleep -Milliseconds 500

# Start background llama-server with Hexagon NPU HTP offload
Write-Host "Launching llama-server with HTP0 offload (ngl=99)..." -ForegroundColor Yellow
& $adb shell "export LD_LIBRARY_PATH=/data/local/tmp/llama.cpp/lib; export ADSP_LIBRARY_PATH=/data/local/tmp/llama.cpp/lib; nohup /data/local/tmp/llama.cpp/bin/llama-server -m /data/local/tmp/gguf/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf --host 127.0.0.1 --port 8080 -ngl 99 --device HTP0 </dev/null >/data/local/tmp/llama-server.log 2>&1 &"

# Model load (HTP device init + FastRPC setup) takes longer than a fixed sleep would predict —
# poll instead of a single check so a live demo doesn't see a false failure.
$healthy = $false
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 1
    $health = & $adb shell "curl -s -m 2 http://127.0.0.1:8080/health"
    if ($health -match "ok") { $healthy = $true; break }
}

if ($healthy) {
    Write-Host "SUCCESS: Snapdragon Hexagon NPU Server is LIVE on http://127.0.0.1:8080" -ForegroundColor Green
    Write-Host "Open iQForge on the phone - it will now generate on the NPU at ~15-18 tokens/sec!" -ForegroundColor Green
} else {
    Write-Host "Check log:" -ForegroundColor Yellow
    & $adb shell "tail -n 15 /data/local/tmp/llama-server.log"
}

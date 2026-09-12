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
& $adb shell "pkill -f llama-server 2>/dev/null"
Start-Sleep -Milliseconds 500

# Start background llama-server with Hexagon NPU HTP offload
Write-Host "Launching llama-server with HTP0 offload (ngl=99)..." -ForegroundColor Yellow
& $adb shell "cd /data/local/tmp/llama.cpp && LD_LIBRARY_PATH=./lib ADSP_LIBRARY_PATH=./lib nohup ./bin/llama-server -m /data/local/tmp/gguf/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf --host 127.0.0.1 --port 8080 -ngl 99 --device HTP0 > /data/local/tmp/llama-server.log 2>&1 &"

Start-Sleep -Seconds 2

# Verify health
$health = & $adb shell "curl -s http://127.0.0.1:8080/health"
if ($health -match "ok") {
    Write-Host "SUCCESS: Snapdragon Hexagon NPU Server is LIVE on http://127.0.0.1:8080" -ForegroundColor Green
    Write-Host "Open iQForge on the phone — it will now generate on the NPU at ~23+ tokens/sec!" -ForegroundColor Green
} else {
    Write-Host "Check log:" -ForegroundColor Yellow
    & $adb shell "cat /data/local/tmp/llama-server.log | tail -n 15"
}

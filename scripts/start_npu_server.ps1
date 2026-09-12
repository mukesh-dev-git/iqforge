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

# Model loading normally takes longer than two seconds. Poll the shell-owned server rather
# than reporting a false failure while Hexagon is still loading the GGUF.
$health = ""
for ($attempt = 1; $attempt -le 40; $attempt++) {
    $health = & $adb shell "curl -s -m 2 http://127.0.0.1:8080/health"
    if ($health -match "ok") { break }
    Start-Sleep -Milliseconds 750
}

# Verify health
if ($health -match "ok") {
    Write-Host "SUCCESS: Snapdragon Hexagon NPU Server is LIVE on http://127.0.0.1:8080" -ForegroundColor Green
    Write-Host "Open iQForge on the phone - it will now generate on the NPU at ~15-18 tokens/sec." -ForegroundColor Green
} else {
    Write-Host "NPU server did not become healthy within 30 seconds. Check log:" -ForegroundColor Yellow
    & $adb shell "tail -n 15 /data/local/tmp/llama-server.log"
}

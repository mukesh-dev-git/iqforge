# Hexagon NPU Setup & Validation — Full Instructions

Goal: get `llama.cpp`'s official Hexagon backend built, pushed to the iQOO 15, and running a
real model on the NPU — producing a measured tokens/sec number, same style as the pre-event
AMD NPU benchmark. This is a **separate standalone CLI validation**, not the chat app itself —
it proves the architecture, it doesn't (yet) run inside `iQForge`.

Do these in order. Steps 1–2 require a reboot; everything after is straightforward.

---

## 1. Enable WSL2 (required for Docker Desktop)

Open PowerShell **as Administrator**:

```powershell
wsl --install
```

**This will ask you to reboot. Do it.** Nothing else in this guide works until WSL2 is active.

## 2. After reboot — verify WSL2

```powershell
wsl --status
```

Should report a version, not an error. If it errors, run `wsl --update` then check again.

## 3. Install Docker Desktop

- Download: https://www.docker.com/products/docker-desktop/
- Run the installer, accept defaults
- On first launch, go to **Settings → General** and confirm **"Use the WSL 2 based engine"** is checked
- It may ask you to sign in with a free Docker ID — a free account is fine, no payment needed
- Wait until the whale icon in the system tray shows Docker is running

Verify from a **normal (non-admin)** PowerShell window:

```powershell
docker --version
docker run hello-world
```

The second command should print a "Hello from Docker!" message. If it does, Docker is working.

## 4. Confirm Python and adb are available

```powershell
python --version
adb devices
```

`adb devices` must show the iQOO 15 (already connected via USB/Office Kit from earlier). If
`python` isn't found: `winget install Python.Python.3.12`, then open a fresh terminal.

## 5. Kick off the toolchain image pull now (large — start early)

```powershell
docker pull ghcr.io/snapdragon-toolchain/arm64-android:v0.7
```

This can take a while even on fast internet (it's a full cross-compilation toolchain — NDK +
Hexagon SDK + OpenCL SDK bundled in). Let it run to completion before the next step; the build
script re-pulls it anyway if you skip this, so this step is just to see the progress directly.

## 6. Build and push llama.cpp to the phone

```powershell
cd D:\Mukesh\Hackathons\IQOO-Hack26\iqforge\mobile\app\src\main\cpp\llama.cpp
python scripts\snapdragon\build.py --target adb --push
```

This automatically launches the Docker container, cross-compiles the Hexagon backend, and
pushes the built binaries + libraries to `/data/local/tmp/` on the connected phone. Let it
finish completely — it's doing a full from-source build, expect several minutes.

If it errors that Docker isn't running, go back to step 3 and make sure the whale icon is active.

## 7. Download the model (on this laptop, not the phone)

```powershell
mkdir C:\hackathon-models -Force
cd C:\hackathon-models
curl.exe -L -o qwen2.5-coder-1.5b-instruct-q4_k_m.gguf "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
```

~1.1GB — fine on your connection. Wait for it to finish (check the file is ~1.1GB, not 0 or a
tiny error page — if `curl` got redirected to an HTML error page instead of the real file, the
size will be under 1MB; re-run if so).

## 8. Push the model to the phone

```powershell
adb shell mkdir -p /data/local/tmp/gguf
adb push qwen2.5-coder-1.5b-instruct-q4_k_m.gguf /data/local/tmp/gguf/
```

## 9. Run it on the Hexagon NPU

```powershell
cd D:\Mukesh\Hackathons\IQOO-Hack26\iqforge\mobile\app\src\main\cpp\llama.cpp
python scripts\snapdragon\run.py --target adb --devices HTP0 -- llama-cli -m /data/local/tmp/gguf/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf -p "Explain what a null pointer exception is in one sentence." -ngl 99 -n 64
```

**Look for these lines in the output — this is the proof:**

```
ggml-hex: Hexagon Arch version v79 (or similar)
ggml-hex: allocating new session: HTP0
load_tensors: offloaded XX/XX layers to GPU        <- confirms NPU offload, not CPU
llama_perf_context_print:  eval time = ... ms / ... runs ( ... ms per token, ... tokens per second)
```

That final **tokens per second** number on the `eval time` line is the real, measured Hexagon
NPU inference speed.

## 10. Get a clean benchmark number (NPU vs CPU, side by side)

```powershell
# Hexagon NPU
python scripts\snapdragon\run.py --target adb --devices HTP0 -- llama-bench -m /data/local/tmp/gguf/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf -p 128 -n 64 -ngl 99

# CPU baseline for comparison
python scripts\snapdragon\run.py --target adb -- llama-bench -m /data/local/tmp/gguf/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf -p 128 -n 64 -ngl 0
```

Both print a table with a `t/s` (tokens/sec) column — that's your NPU-vs-CPU comparison,
exactly the same format as the pre-event AMD benchmark. **Screenshot or copy both tables.**

---

## Report back with

1. Whether step 6 (the build+push) completed without errors
2. The full output of the step 9 `llama-cli` run
3. Both `llama-bench` tables from step 10

That's everything needed to update the pitch with a real, measured Hexagon NPU number instead
of a projected one — and to decide whether integrating this into the actual app (not just the
CLI) is worth attempting with whatever time is left.

# Salvaged from the old repo

Proven, tested code carried over from `iqoo-hack26` (the Phase 1 repo, built around the
retired diff-review idea). The logic is sound — it just needs to land inside `mobile/` once
that Android Studio project exists. Delete this folder once everything below is moved.

Package names are already rebranded from `com.limitless.codereview.*` to `com.iqforge.*`.

| File here | Goes to (once `mobile/` exists) | Source |
|---|---|---|
| `camera/CameraScanner.kt` | `mobile/app/src/main/java/com/iqforge/camera/` | Real CameraX + ML Kit OCR — point phone at a screen, get text back. Works as-is. |
| `sensors/SensorManager.kt` | `mobile/app/src/main/java/com/iqforge/sensors/` | Shake, tilt, proximity face-down — callback-based, wired the same way it was before. |
| `native/CMakeLists.txt` | `mobile/app/src/main/cpp/CMakeLists.txt` | llama.cpp JNI build config. Still expects `llama.cpp/` vendored alongside it (gitignored — clone `ggml-org/llama.cpp` there before building). |
| `native/LlamaEngine.cpp` | `mobile/app/src/main/cpp/LlamaEngine.cpp` | The JNI bridge itself. JNI export is pre-bound to `com.iqforge.engine.NativeEngine` per `CONTRACT.md` — keep that class name and package when you write the Kotlin side, or rename both together. Has an inline comment at the NPU decision point (`n_gpu_layers`) — read it before touching. |

**Not ported, and not needed:** the old diff-centric `MainActivity.kt`, the GitHub
diff-fetch client (superseded by `RepoManager` / JGit — full clone, not single-diff fetch),
the JWT/REST filler screens, the zero-cloud/Air-Gap UI. All built around the retired idea —
rewrite clean against the current `CONTRACT.md` instead of adapting these.

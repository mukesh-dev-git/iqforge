cmake_minimum_required(VERSION 3.22.1)

project("llama-android")

set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# NPU Integration Pipeline (Issue #5) — CORRECTED, see finals-30hr/NPU_SETUP_INSTRUCTIONS.md
#
# The vendored llama.cpp does NOT have a GGML_QNN option (that name never existed in this
# snapshot's ggml/CMakeLists.txt — it was a stale assumption from an older QNN-patch era).
# Its real, current backend is GGML_HEXAGON, and — critically — it only builds via the
# Snapdragon Docker cross-compile toolchain (ghcr.io/snapdragon-toolchain/arm64-android),
# because it needs the Hexagon DSP compiler to build the HTP skel libraries. Android
# Studio's normal NDK toolchain (what add_subdirectory(llama.cpp) below uses) cannot build
# GGML_HEXAGON=ON — setting it here would just fail the build, not enable the NPU.
#
# So this Gradle/CMake build stays CPU-only from-source, on purpose. The real integration
# path once teammate B's Docker build succeeds:
#   1. `cmake --install build-snapdragon --prefix pkg-android/llama.cpp` (per llama.cpp's
#      own docs) produces libggml.so, libggml-cpu.so, libggml-hexagon.so, and per-arch
#      libggml-htp-vNN.so, plus headers.
#   2. Copy those prebuilt .so files into app/src/main/jniLibs/arm64-v8a/ instead of
#      compiling ggml from source here.
#   3. Point LlamaEngine.cpp's build at the prebuilt headers (target_include_directories)
#      and link against the prebuilt libs (target_link_libraries) instead of the
#      add_subdirectory(llama.cpp) below.
#   4. At runtime, ggml_backend_load_all() (or explicit ggml_backend_hexagon_init) needs to
#      pick up libggml-hexagon.so from the app's native lib dir — verify the app's
#      System.loadLibrary calls cover it.
# This is real, separate work from "the Docker build succeeded" — budget for it as its own
# step, not something that falls out for free once teammate B's CLI test passes.

add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)

add_library(
        llama-android
        SHARED
        LlamaEngine.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        llama-android
        llama
        ${log-lib})

target_include_directories(llama-android PRIVATE
        llama.cpp/include
        llama.cpp/ggml/include)

# Intentionally no GGML_QNN/GGML_HEXAGON branch here — see the comment above add_subdirectory.
# This build stays CPU-only (LlamaEngine.cpp's USE_QNN_BACKEND branch is never defined here,
# so it always takes the CPU path, n_gpu_layers=0 — the correct, safe default). Real Hexagon
# integration replaces the add_subdirectory(llama.cpp) approach entirely with prebuilt
# jniLibs from teammate B's Docker build, not a flag flipped on this from-source path.

# iQForge

**Clone. Code. Review. Ship. From your phone.**
*The model is offline. The phone is not.*

Team Limitless — iQOO Hackathon 2026, Chennai City Battle, Developer Tools track.

## What this is

A real development environment that runs on the iQOO 15. Clone a repo, edit files in a
real editor, and let a code model on the Hexagon NPU (or its CPU fallback) write, review,
and debug alongside you. Commit and push straight back to GitHub. Anything that needs a
real toolchain — `npm install`, a test suite, a build — escalates over Office Kit to a
paired laptop and streams back.

Full pitch: [`../phase1-submission/PHASE1_IDEA_SUBMISSION.md`](../phase1-submission/PHASE1_IDEA_SUBMISSION.md)
Build/event plan: [`../finals-30hr/EVENT_PLAN.md`](../finals-30hr/EVENT_PLAN.md)

## Layout

```
iqforge/
├── mobile/       the phone app — Android Studio project (Kotlin, Compose)
├── bridge/       the laptop-side server (FastAPI) — escalation + toolchain execution
└── salvaged/     proven code carried over from the old repo — see salvaged/README.md
```

## Quickstart

1. Read `CONTRACT.md` first — it's short, and it's what lets three people build in parallel.
2. Open `mobile/` in Android Studio. It uses Java 17, compile/target SDK 34, and min SDK 26.
   Build a debug APK with `gradlew.bat assembleDebug` on Windows.
3. `bridge/` is a standalone FastAPI server:
   ```bash
   cd bridge && pip install -r requirements.txt && uvicorn server:app --host 0.0.0.0 --port 8000
   ```
4. Tasks: GitHub Issues on this repo — not a separate file, so it doesn't drift.

## Status

Core loop is real: clone/edit (JGit + file workspace), review/write/debug/explain (on-device
CPU inference via `llama.cpp` JNI, auto-selects from `ModelCatalog`), escalate + toolchain
exec + model discovery + connector probing (`bridge/server.py`, 5 endpoints). Chat history,
camera/voice attachments, sensor feedback (haptics, tilt-scroll) are wired into the app.
`salvaged/` has been removed — everything in it was superseded or moved in; see git history
if you need the originals.

**Open:** the on-device model file itself isn't bundled yet (`mobile/app/src/main/assets/`
only has `MODEL_INFO.md`) — CPU inference has nothing to load until that lands. Hexagon NPU
backend is CPU-only by design for now; real NPU support needs teammate B's Docker toolchain
build (see `finals-30hr/NPU_SETUP_INSTRUCTIONS.md`) plus a separate jniLibs integration step
(see the comment in `mobile/app/src/main/cpp/CMakeLists.txt`) — it does not fall out for free
once the Docker build succeeds.

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

Android scaffolding is complete and `assembleDebug` succeeds. The current app is a minimal
Compose shell; the core clone/edit/review/escalate/commit/push loop is not implemented yet.
`bridge/` contains the ported `/review` server, but contract-compatible `/escalate` and
toolchain execution via `/exec` are not built yet. `salvaged/` holds the camera, sensor,
and native llama.cpp integration files waiting to be moved into `mobile/`.

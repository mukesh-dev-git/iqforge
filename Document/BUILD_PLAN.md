# iQForge — Build Plan

Consolidates the technical brainstorm. Pairs with [`EVENT_PLAN.md`](EVENT_PLAN.md)
(logistics, NPU decision, hour-by-hour schedule) and
[`../iqforge/CONTRACT.md`](../iqforge/CONTRACT.md) (the actual interfaces).

## Model

| Tier | Model | Where |
|---|---|---|
| Fast (inline) | Qwen2.5-Coder-1.5B-Instruct, GGUF Q4_K_M | Phone — CPU today, Hexagon NPU if Option A lands |
| Deep (escalation) | Qwen2.5-Coder-7B | Laptop, via Ollama — already set up, already tested |
| Stretch, cut-first | Qwen2.5-Coder-3B | Phone, optional third tier — only if core loop + NPU decision are done early. Bundle in APK, never a live download mid-event |

## Space

- Model file: ~1.0–1.1 GB (verify exact size on download)
- APK itself: ~40–60 MB
- Total on-device storage: ~1.1 GB
- **Bundle the model in APK assets** — don't depend on venue wifi for a download step during the demo
- RAM at runtime: ~1.5–2 GB peak (weights + KV cache at 2–4K context)

## Speed & efficiency

- CPU-only (current code, `n_gpu_layers=0`): ~5–15 tok/s → a ~200-token response takes ~15–40s
- If Hexagon NPU swap lands: ~15–30+ tok/s target — **measure on-site, don't assume**
- NPU matters for heat/battery over a 30-hour demo cycle, not just latency

## Working logic — the loop

1. **Get** — clone repo (JGit) → file tree on phone
2. **Write** — model generates/completes code in a file
3. **Review** — model flags issues in a diff, with severity
4. **Debug** — stack trace in → root cause + fix out
5. **Escalate** — anything needing a real OS (`npm install`, tests, builds) → Office Kit → laptop → streamed back
6. **Ship** — model drafts commit message → commit → push → live on GitHub

**The rule:** NPU handles reasoning over code. Laptop handles anything needing an operating system.

## Real capability limits — be honest about these in the pitch, don't hide them

- **Write:** ~20–30 lines per generation call (one function, one fix — not a file)
- **Review — two different ceilings:**
  - Technical (context window): ~350–400 lines could physically fit
  - Trustworthy (1.5B model quality): findings stay reliable to **~40–60 lines** — this is the real escalation threshold, not the context limit
- **Why that's fine, not a weakness:** median real-world PR size is well under 100 lines; human review quality also drops past ~200–400 lines (Google's own eng practices say so); the pitch's own use case — a quick fix on a commute — is naturally inside this range
- Pitch it as "instant on small stuff, escalates seamlessly on big stuff," never as "reviews any diff"

## Features — core vs. extras (don't let this list grow again)

**Core, must work for the demo:**
- Clone / file tree / open file
- AI write, review, debug, explain
- Office Kit escalation (model + toolchain exec)
- Commit + push

**Phone-native extras — already salvaged and ported, wire in after core works:**
- Camera OCR (`salvaged/camera/CameraScanner.kt`)
- Haptic bug alerts
- Shake / tilt-scroll / face-down-lock (`salvaged/sensors/SensorManager.kt`)

## UI — chat-agent pattern, not a dashboard of tool cards

One main screen: a chat feed (`LazyColumn`), same mental model as Claude Code / Codex —
each turn is a card (tool call, diff, escalation status, result), bottom bar has text + mic
+ camera. File tree is a slide-over drawer, not a split pane. Commit is a bottom sheet.

- **Cheap, build properly:** the chat feed itself, diff cards (colored +/- lines)
- **Keep deliberately simple:** the file editor — plain monospace `TextField`, no real
  syntax highlighting. A full code editor is exactly the kind of feature that eats
  disproportionate time for marginal demo value
- **Reuse, don't rebuild:** mic + camera buttons are the salvaged voice trigger and
  `CameraScanner.kt` wired into the input bar, not a separate screen

## Still open — decide before or right at the start of the 30 hours

- **NPU Option A vs B** — attempt the real Hexagon/QNN swap on-site, or ship CPU and
  disclose it plainly (see `EVENT_PLAN.md`)
- **Reply "confirmed" to the event email** — check this is actually done

## First thing to work on

**Create `iqforge/mobile/` in Android Studio and get it building clean with the salvaged
files dropped in.** Nothing else has anywhere to go until this exists — the chat feed, the
engine, the git operations, the bridge client all live inside this module.

Concretely, in order:
1. Android Studio → New Project → Empty Activity (Compose) → package `com.iqforge`, min SDK
   26, save to `iqforge/mobile/` (exact settings already written up — see chat history or ask again)
2. Add the dependencies `salvaged/README.md` implies (CameraX, ML Kit, OkHttp, serialization)
3. Move `salvaged/camera/`, `salvaged/sensors/` in, confirm they compile
4. Confirm the app runs empty on a personal phone before touching the model or native build

**Can run in parallel once `mobile/` exists (3 people, 3 tracks):**
- One person: vendor `llama.cpp` at the gitignored path, get `salvaged/native/` building,
  download the Qwen2.5-Coder-1.5B GGUF
- One person: build `RepoManager` (JGit clone/commit/push) — brand new, nothing to port
- One person: add the `/exec` endpoint to `bridge/server.py`

All three feed into the same chat-feed UI once each piece works standalone — that's the
whole point of `CONTRACT.md` existing.

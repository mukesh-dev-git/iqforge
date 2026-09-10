# Phase 1 Idea Submission — iQForge

*Canonical pitch. Supersedes `DEVPULSE_IDEA_FINAL.md` and
`codebase/docs/SUBMISSION_IDEA_FRAMING.md`.*

---

## Idea Title

**iQForge**

*Clone. Code. Review. Ship. From your phone.*

---

## The reframe (read this first — it changes the previous drafts)

The old pitch was "zero cloud, air-gapped, 0 bytes leave the device." That was never
actually true of what we built — `GitHubClient.kt` fetches diffs over HTTPS, and it should.
The correct line, which the team's own earliest draft already had right:

> **The model is offline. The phone is not.**

Network is normal and expected — GitHub for code, connectors for the team. What never
happens is source code going to a **third-party AI service**. Inference is the thing that
stays on the NPU.

That unlocks the real product: not a review tool, but **a development environment that runs
on the phone**, where the laptop is a build server you call rather than the place work
happens.

---

## Description

A developer away from their desk today can *read* code but not *work* on it. GitHub's mobile
app shows you a pull request but won't let you fix it. Every AI coding assistant is a cloud
service — it needs signal, costs per token, adds a round-trip, and still can't run your code.
Meanwhile the phone in your pocket has a 45-TOPS NPU sitting completely idle while you wait
to get back to a laptop to change three lines.

iQForge turns the iQOO 15 into an actual development environment. Clone a repository, or
scaffold a new React/Node project with a real file tree on the device. Open files in a real
editor. The on-device model — running on the Hexagon NPU — writes, explains, reviews, and
debugs alongside you: generate a function, explain a stack trace, fix the bug, review the
resulting diff. Then commit with an AI-written message and push straight back to GitHub. The
AI never leaves the phone: no API key, no per-token cost, no network round-trip for
inference, and it keeps working when your signal doesn't.

The phone doesn't pretend it can do everything. `npm install`, a dev server, a full test
suite, a heavy build — those need a real toolchain. iQForge escalates exactly those over
**Office Kit** to a paired laptop and streams the output back to the phone. The phone stays
the control surface; the laptop is a build server it calls when it needs one.

Built for developers who want to close the loop — fix, commit, ship — in the fifteen minutes
they have on a commute, in a meeting, or away from the desk, instead of writing themselves a
note to do it later.

---

## The dev loop — what runs where

| Stage | On the phone (Hexagon NPU) | Escalated to laptop (Office Kit) |
|---|---|---|
| **Get** | Clone repo, pull branch, open a PR, scaffold a new project | — |
| **Write** | AI generates and completes functions; real editor + file tree | Large multi-file refactors |
| **Review** | Diff review, risk and security flags | Whole-repo context passes |
| **Debug** | Stack trace → root cause → fix applied to the file | Errors that need a live run to reproduce |
| **Test** | Lint, static checks, run pure JS/Python snippets on-device | `npm test`, full suites, integration tests |
| **Ship** | AI commit message → commit → push → open PR | `npm install`, builds, dev server, running the app |

The split is the architecture: **the NPU handles anything that is reasoning over code; the
laptop handles anything that needs an operating system.**

### The Offline Escalation Queue
If the laptop bridge is offline, developers are never blocked. Instead of failing, heavy toolchain requests (like `npm test`) are gracefully intercepted and saved to a local **Pending Task Database** on the phone. A background worker (Android WorkManager) silently monitors the network. The moment the developer reconnects to their laptop via Wi-Fi or Office Kit, the phone instantly flushes the queue, streams the queued tasks to the laptop bridge, and triggers a haptic buzz when the build logs or test outputs are ready. This asynchronous queue proves the phone is an intelligent system that respects the developer's time, perfectly solving the "coding on a commute" problem.


---

## Why iQOO

| Hardware | What it makes possible |
|---|---|
| **Hexagon NPU (45 TOPS)** | Local code model — free, instant, no API key, works on bad signal. This is what makes a phone-based dev loop viable at all. |
| **16 GB RAM / UFS 4.0** | A 1.5B code model + KV cache resident alongside a real editor and file tree |
| **50MP camera** | OCR a diff or snippet straight off a monitor or whiteboard into a file |
| **Haptic engine** | A failed build or a BUG finding is felt, not missed |
| **Sensor suite** | Gyroscope scrolls long diffs hands-free; proximity locks the session face-down |
| **Office Kit** | The build-server bridge — screen mirror, clipboard, file transfer to the laptop tier |

## Why the NPU specifically (not a cloud API)

- **Free** — no per-token cost on a loop a developer runs dozens of times a day
- **Instant** — no round-trip; the model is resident in memory
- **Resilient** — the assistant keeps working on a train, on a plane, in a basement
- **Private where it counts** — code goes to GitHub, where it already lives, and never to a third-party AI vendor

---

## What this means for the code we already have

**Keep and extend:**
- `GitHubClient.kt` — extend from diff-fetch to full clone/commit/push (JGit on Android)
- `LlamaCppReviewEngine` — extend prompts beyond review into generate / explain / fix
- `laptop-bridge/server.py` — **add a command-execution endpoint** (run `npm test`, stream stdout back). Its HTTP scaffolding already exists; this is simpler than the model bridge it already does.
- Camera OCR, sensors, haptics, voice trigger — all still fit

**Must change:**
- Kill the **"0 bytes to cloud" badge** and **Air-Gap Mode** — they contradict the GitHub integration and are now off-message
- Reword `PrivacyScreen` to "AI inference stays on this device"

**Deprioritize:** the standalone utilities (JWT decoder, REST client) — they were filler under the old framing and don't serve the dev-loop story. Keep them in a side drawer; don't pitch them.

---

## 30-hour demo path (the one thing that must work end to end)

1. Clone a real GitHub repo on the phone
2. Browse the file tree, open a file
3. Ask the NPU model to fix a bug → change applied to the file
4. Review the resulting diff on-device
5. Escalate `npm test` to the laptop over Office Kit → output streams back to the phone
6. AI-written commit message → commit → push → **the commit is live on GitHub**

Ending on a real commit visible in a browser is a far stronger close than "paste a diff, see
findings."

**Cut if time runs short:** Slack connector, on-device Python execution, multiple scaffold
templates, editor syntax-highlighting polish.

---

## Team

- **Mukeshkumar M** — Team Lead; app shell, voice trigger, escalation logic
- **Delfi A** — On-device inference (llama.cpp → Hexagon NPU)
- **Nambert Jones L** — Laptop bridge, GitHub integration, camera and sensor tools

## Prototype URL

`https://github.com/mukesh-dev-git/iqoo-hack26`

---

## Scoring alignment (internal notes)

| Criteria | Weight | How this earns it |
|---|---|---|
| End product quality | 30% (jury) | A complete dev loop ending in a real commit — not a demo of one feature |
| Novelty & impact | 20% (jury) | "The phone is the workstation" is a genuinely new claim; mobile git+AI editing barely exists |
| Creative phone use | 15% (HackTracker) | The entire product *is* phone use — plus camera OCR, haptics, sensors |
| Technical depth | 15% (jury) | On-device model + JGit + a real two-tier compute split with a command-execution bridge |
| Office Kit usage | 10% (HackTracker) | Office Kit is load-bearing: every build/test/install goes through it |
| Demo & presentation | 10% (jury) | Ends on a live GitHub commit made from a phone |

Note: this framing scores *better* on phone-first execution than the old one, because the
product now requires heavy phone use by design rather than as a showcase.

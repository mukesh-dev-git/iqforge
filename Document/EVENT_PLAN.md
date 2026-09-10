# iQForge — Build & Event Plan

## Logistics (from the official confirmation email, Sep 9)

- **Venue:** The Hive - Flexible Workspaces, OMR Chennai (Pre-toll), SRP Stratford,
  Rajiv Gandhi Salai, PTK nagar, Thiruvanmiyur, Chennai, TN 600041
- **Report by:** Saturday 12 September, 9:00 AM
- **30 hours on site, overnight** — runs through roughly 3:00 PM Sunday 13 September
- **On-the-day contacts:** Rahul +91 93533 17113, Sam +91 73385 44449
- **Bring:** whole team in person with government photo ID; laptop + charger + any devices,
  cables, extension board the build needs
- **Join the Chennai WhatsApp group** — everyone attending, not just one person

### ⚠️ Open action item
Reply to the confirmation email with **"confirmed"** and the names of everyone attending.
The email is explicit: *"If you cannot make it, tell us now instead of going quiet. We will
pass the seat to the next deserving team."* Check whether this has been done.

## The actual format

Straight from the email: *"You are building the idea you submitted. Start sharpening it
now, not on the morning."*

This is **not** build-from-zero-in-30-hours. It's:
1. Build the real thing now, on your own devices
2. Install it on the iQOO 15 they provide on-site — hardware none of you have had access to
3. Spend the 30 hours validating and refining against *real* Hexagon NPU silicon, fixing
   device-specific issues, and rehearsing the demo

Everything that can be built and tested on your own Android phones, build **now**.

## The one decision that changes everything: NPU offload isn't wired yet

`LlamaEngine.cpp` (on `Nambert-2`) sets `model_params.n_gpu_layers = 0` — zero layers
offloaded to any accelerator. `CMakeLists.txt` links plain `llama.cpp` only, no QNN/Hexagon/
GenieX backend. As written today, this is a correctly-built **CPU-only** llama.cpp path.
"Runs on the Hexagon NPU" is the destination, not the current state — and that's fine, but
it needs to be a *decided* plan, not a gap nobody notices until a judge asks.

**Option A — attempt the real NPU swap on-site.** Get the CPU build verified on the
provided phone first (safety net, hour 0–2), then wire the QNN backend or GenieX SDK now
that you have real hardware and whatever on-site SDK access the event provides. Highest
risk, highest payoff — budget it early so there's runway to fall back.

**Option B — ship CPU-verified, disclose honestly.** Keep CPU inference as the demoed
engine and say so: "on-device, CPU inference today; Hexagon NPU backend is the next
integration, same interface." The team's original Aug 24 draft did exactly this and it read
as credible, not weak.

**DECISION LOG (Issue #5) — Sep 10:**
We are committing to **Option A**. The NPU pipeline is wired into `mobile/app/src/main/cpp/CMakeLists.txt` via the `GGML_QNN` flag, but explicitly set to `OFF` today.
- **Hour 0:** We will flash the `GGML_QNN=OFF` (CPU) build to establish the safety net.
- **Hour 2:** We will switch `GGML_QNN=ON` and link the Qualcomm Neural Network (QNN) SDK provided on the iQOO 15 to map the layers to the Hexagon tensor cores (`n_gpu_layers = 99`).

This keeps the CPU safety net intact while fully preparing the architecture for the NPU offload.

## Pre-event build checklist (now → Sep 11 night, on your own Android devices)

- [ ] Verify `Nambert-2` actually compiles — vendor `llama.cpp` at the gitignored path,
      confirm `gradlew assembleDebug` succeeds
- [ ] Merge `Nambert-2` into `main` once confirmed (fast-forwards cleanly, no conflicts)
- [ ] Decide NPU Option A vs B above; if A, start QNN/GenieX research now
- [ ] Build the "Get" + "Write" surface: repo clone + file tree + basic in-app file editor
      on the phone — doesn't exist yet, and it's the core of the current pitch
- [ ] Add toolchain execution to `laptop-bridge/server.py` — run a command (start with
      `npm test`), stream stdout back to the phone
- [ ] Remove/reword the "0 bytes to cloud" / Air-Gap Mode UI — contradicts the current pitch
- [ ] Confirm the full loop end to end on a personal phone: clone → fix → review → escalate
      test to laptop → commit → push → visible on GitHub
- [ ] Rehearse the install process itself — signed APK or a tested `adb install` flow —
      so installing on an unfamiliar iQOO 15 Saturday morning is not the first time it's tried

## On-site plan (Sep 12, 9:00 AM → Sep 13, ~3:00 PM)

- **Hour 0–2:** Install on the provided iQOO 15. Confirm the CPU-verified build runs — the
  non-negotiable safety net before touching anything risky.
- **Hour 2–8 (Red Light):** NPU swap attempt if Option A; otherwise device-specific tuning —
  haptics feel different per device, camera OCR needs tuning to the venue's actual lighting.
- **Green Light opens (~hour 16, per the 55/45 split):** Office Kit round-trip tested for
  real, laptop present, escalation exercised live.
- **Final hours:** demo rehearsal on the actual device, in the actual room.

## Notes
_(fill in as you go)_

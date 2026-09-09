"""
Laptop-bridge server — Nambert's task.

Stands in for the Office Kit transport until that SDK is available at the event: the phone
sends a diff over HTTP on shared Wi-Fi, this wraps a local model, and returns findings in the
shape the Android app expects. See /CONTRACT.md at the repo root for the exact wire format
both sides must agree on.

Two backends, same contract — pick with REVIEW_BACKEND:

  ollama (default)  — Ollama running a 7B model. Needs a GPU laptop for good speed.
      pip install -r requirements.txt
      ollama pull qwen2.5-coder:7b      # or deepseek-coder-v2:16b if your GPU has the VRAM
      uvicorn server:app --host 0.0.0.0 --port 8000

  npu               — AMD Ryzen AI NPU via onnxruntime-genai. Only runs where that's set up
                       (see ../docs/NPU_VALIDATION.md) — needs the ryzen-ai-1.8.0 conda env.
      conda activate ryzen-ai-1.8.0
      set REVIEW_BACKEND=npu
      uvicorn server:app --host 0.0.0.0 --port 8000

Then from the phone (same Wi-Fi), find this laptop's IP (`ipconfig` / `ifconfig`) and point
LaptopBridgeReviewEngine's laptopBaseUrl at it, e.g. http://192.168.1.42:8000
"""

import os
import re
import time
import logging

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)

BACKEND = os.environ.get("REVIEW_BACKEND", "ollama")

app = FastAPI(title="iQOO Code Review Bridge", version="1.0.0")

# Allow requests from Android app on LAN
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Prompt ---
# Structured output format: one finding per line, no markdown, no explanations.
# This is tighter than before — the 7B model handles it reliably.
PROMPT_TEMPLATE = """\
You are a senior code reviewer doing a security and correctness review of a git diff.
Report every bug, warning, or note you find. Pay special attention to:
- Null / None pointer dereferences
- Off-by-one errors and boundary conditions (especially with list indices, subList, slice)
- Resource leaks (unclosed streams, connections)
- Unused imports or dead code
- Race conditions or thread-safety issues
- Type mismatches or unsafe casts

Output format — one finding per line, no extra text:
LINE:<n> SEVERITY:<BUG|WARNING|INFO> MSG:<one concise sentence describing the issue>

- LINE is the + (added) line number in the diff where the issue is introduced (use 1 if unclear).
- SEVERITY must be BUG, WARNING, or INFO.
- If the diff has NO issues at all, output exactly the word: NO_ISSUES

Diff to review:
{diff}
"""

FINDING_RE = re.compile(
    r"LINE:(\d+)\s+SEVERITY:(BUG|WARNING|INFO)\s+MSG:(.+)"
)

_server_start = time.time()


# --- Pydantic models ---

class ReviewRequest(BaseModel):
    diff: str


class Finding(BaseModel):
    line: int
    severity: str
    message: str


class ReviewResponse(BaseModel):
    findings: list[Finding]
    backend: str
    elapsed_ms: int


class StatusResponse(BaseModel):
    status: str
    backend: str
    model: str | None
    uptime_s: float
    ollama_reachable: bool | None


# --- Backend setup ---

if BACKEND == "npu":
    import npu_backend

    MODEL_NAME = "Qwen2.5-Coder-1.5B (NPU)"
    OLLAMA_URL = None

    def run_backend(diff: str) -> str:
        return npu_backend.generate(diff)

else:
    import requests as _requests

    OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
    MODEL_NAME = os.environ.get("REVIEW_MODEL", "qwen2.5-coder:7b")

    def _check_ollama() -> bool:
        try:
            r = _requests.get(f"{OLLAMA_URL}/api/tags", timeout=3)
            return r.status_code == 200
        except Exception:
            return False

    def call_ollama(diff: str) -> str:
        prompt = PROMPT_TEMPLATE.format(diff=diff)
        try:
            resp = _requests.post(
                f"{OLLAMA_URL}/api/generate",
                json={"model": MODEL_NAME, "prompt": prompt, "stream": False},
                timeout=180,
            )
            resp.raise_for_status()
        except _requests.exceptions.ConnectionError:
            raise HTTPException(
                status_code=503,
                detail=(
                    f"Ollama is not running. Start it with: ollama serve  "
                    f"then: ollama pull {MODEL_NAME}"
                ),
            )
        except _requests.exceptions.Timeout:
            raise HTTPException(status_code=504, detail="Ollama timed out — diff may be too large.")
        return resp.json().get("response", "")

    def run_backend(diff: str) -> str:
        return call_ollama(diff)


# --- Parsing ---

def parse_findings(raw_text: str) -> list[Finding]:
    findings = []
    for match in FINDING_RE.finditer(raw_text):
        line_no, severity, message = match.groups()
        findings.append(Finding(
            line=int(line_no),
            severity=severity,
            message=message.strip(),
        ))
    if not findings and raw_text.strip():
        log.warning("Model output did not match LINE:/SEVERITY:/MSG: format. Raw: %s", raw_text[:200])
    return findings


# --- Endpoints ---

@app.post("/review", response_model=ReviewResponse)
def review(req: ReviewRequest) -> ReviewResponse:
    if not req.diff.strip():
        raise HTTPException(status_code=400, detail="diff must not be empty")

    log.info("Review request — diff length: %d chars", len(req.diff))
    t0 = time.time()
    raw = run_backend(req.diff)
    elapsed = int((time.time() - t0) * 1000)
    log.info("Backend response in %dms. Raw output: %s", elapsed, raw[:300])

    findings = parse_findings(raw)
    log.info("Parsed %d finding(s)", len(findings))

    return ReviewResponse(findings=findings, backend=BACKEND, elapsed_ms=elapsed)


@app.get("/health")
def health():
    """Quick liveness check — use /status for a richer diagnostic."""
    return {"status": "ok", "backend": BACKEND, "model": MODEL_NAME}


@app.get("/status", response_model=StatusResponse)
def status() -> StatusResponse:
    """Richer diagnostic: tells you whether Ollama is reachable."""
    ollama_ok = _check_ollama() if BACKEND == "ollama" else None
    return StatusResponse(
        status="ok",
        backend=BACKEND,
        model=MODEL_NAME,
        uptime_s=round(time.time() - _server_start, 1),
        ollama_reachable=ollama_ok,
    )

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
import subprocess
import platform
import shlex
import shutil
import threading
import uuid
from pathlib import Path
import requests
import html
import json
import xml.etree.ElementTree as ET

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger(__name__)

BACKEND = os.environ.get("REVIEW_BACKEND", "ollama")
_configured_exec_roots = [
    os.path.abspath(p.strip()) for p in os.environ.get("ALLOWED_EXEC_ROOTS", "").split(",") if p.strip()
]
ALLOWED_EXEC_ROOTS = _configured_exec_roots or [str(Path(__file__).resolve().parent.parent)]
EXEC_TIMEOUT = int(os.environ.get("EXEC_TIMEOUT", "60"))
ALLOWED_EXECUTABLES = {
    "git", "gh", "rg", "python", "python.exe", "pytest", "gradle", "gradle.bat",
    "gradlew", "gradlew.bat", "npm", "npm.cmd", "npx", "npx.cmd", "node", "node.exe",
}

app = FastAPI(title="iQOO Code Review Bridge", version="1.0.0")

# Allow requests from Android app on LAN
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Prompts ---
PROMPTS = {
    "review": """\
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
""",
    "write": """\
You are an expert software engineer. Write or modify code based on the instruction and context.
Output only the resulting code snippet or diff, without any markdown formatting or explanations unless requested.

Context:
{context}

Instruction:
{instruction}
""",
    "debug": """\
You are an expert debugger. Find the root cause of the problem described by the instruction or stack trace based on the context.
Provide a concise explanation of the root cause and a proposed fix.

Context:
{context}

Instruction/Stack Trace:
{instruction}
""",
    "explain": """\
You are an expert code explainer. Explain the provided code context clearly and concisely based on the instruction.

Context:
{context}

Instruction:
{instruction}
"""
}

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


class EscalateRequest(BaseModel):
    task: str
    context: str
    instruction: str
    effort: str = "medium"


class EscalateResponse(BaseModel):
    result: str
    backend: str
    elapsed_ms: int


class ExecRequest(BaseModel):
    command: str
    cwd: str


class ExecResponse(BaseModel):
    stdout: str
    stderr: str
    exit_code: int


class DispatchPlanRequest(BaseModel):
    instruction: str
    cwd: str


class DispatchPlanResponse(BaseModel):
    summary: str
    command: str | None
    cwd: str
    executable: bool


class DispatchWorkspacesResponse(BaseModel):
    workspaces: list[str]


class WorkspaceFilesResponse(BaseModel):
    files: list[str]


class WorkspaceFileResponse(BaseModel):
    path: str
    content: str


class WorkspaceWriteRequest(BaseModel):
    cwd: str
    path: str
    content: str


class WorkspaceWriteResponse(BaseModel):
    path: str
    bytes_written: int


class RepositoryRequest(BaseModel):
    workspace: str
    name: str | None = None
    url: str | None = None
    publish: bool = False


class RepositoryResponse(BaseModel):
    path: str
    output: str


class WebSearchRequest(BaseModel):
    query: str
    max_results: int = 5


class WebSearchResult(BaseModel):
    title: str
    url: str
    snippet: str


class WebSearchResponse(BaseModel):
    query: str
    results: list[WebSearchResult]


class StatusResponse(BaseModel):
    status: str
    backend: str
    model: str | None
    uptime_s: float
    ollama_reachable: bool | None


class ModelInfo(BaseModel):
    id: str
    parameter_size: str | None = None
    quantization: str | None = None
    capabilities: list[str] = []
    selected: bool = False


class ModelsResponse(BaseModel):
    models: list[ModelInfo]


class SelectModelRequest(BaseModel):
    model: str


class ConnectorInfo(BaseModel):
    id: str
    name: str
    status: str
    detail: str
    connected: bool


class ConnectorsResponse(BaseModel):
    connectors: list[ConnectorInfo]


# --- Backend setup ---

if BACKEND == "npu":
    import npu_backend

    MODEL_NAME = "Qwen2.5-Coder-1.5B (NPU)"
    ACTIVE_MODEL = MODEL_NAME
    OLLAMA_URL = None

    def run_backend(prompt: str) -> str:
        return npu_backend.generate(prompt)

else:
    import requests as _requests

    OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
    MODEL_NAME = os.environ.get("REVIEW_MODEL", "qwen2.5-coder:1.5b")
    ACTIVE_MODEL = MODEL_NAME

    def _check_ollama() -> bool:
        try:
            r = _requests.get(f"{OLLAMA_URL}/api/tags", timeout=3)
            return r.status_code == 200
        except Exception:
            return False

    def call_ollama(prompt: str) -> str:
        try:
            resp = _requests.post(
                f"{OLLAMA_URL}/api/generate",
                json={"model": ACTIVE_MODEL, "prompt": prompt, "stream": False},
                timeout=180,
            )
            resp.raise_for_status()
        except _requests.exceptions.ConnectionError:
            raise HTTPException(
                status_code=503,
                detail=(
                    f"Ollama is not running. Start it with: ollama serve  "
                    f"then: ollama pull {ACTIVE_MODEL}"
                ),
            )
        except _requests.exceptions.Timeout:
            raise HTTPException(status_code=504, detail="Ollama timed out.")
        return resp.json().get("response", "")

    def run_backend(prompt: str) -> str:
        return call_ollama(prompt)


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


def is_safe_path(requested_cwd: str) -> bool:
    try:
        requested_path = Path(requested_cwd).resolve()
        for root in ALLOWED_EXEC_ROOTS:
            root_path = Path(root).resolve()
            if requested_path == root_path or root_path in requested_path.parents:
                return True
        return False
    except Exception:
        return False


def command_arguments(command: str) -> list[str]:
    """Parse a command without a shell and enforce the developer-tool allowlist."""
    try:
        arguments = shlex.split(command, posix=True)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=f"Invalid command quoting: {error}")
    if not arguments:
        raise HTTPException(status_code=400, detail="Command must not be empty")
    executable = Path(arguments[0]).name.lower()
    if executable not in ALLOWED_EXECUTABLES:
        raise HTTPException(status_code=403, detail=f"Executable is not allowed for Dispatch: {executable}")
    return arguments


def parse_dispatch_plan(raw: str, cwd: str) -> DispatchPlanResponse:
    cleaned = raw.strip().removeprefix("```json").removesuffix("```").strip()
    try:
        payload = json.loads(cleaned)
    except json.JSONDecodeError:
        return DispatchPlanResponse(summary=raw.strip(), command=None, cwd=cwd, executable=False)
    summary = str(payload.get("summary") or "Dispatch plan ready").strip()
    command = str(payload.get("command") or "").strip() or None
    executable = False
    if command:
        try:
            command_arguments(command)
            executable = True
        except HTTPException:
            command = None
    return DispatchPlanResponse(summary=summary, command=command, cwd=cwd, executable=executable)


def resolve_workspace_file(cwd: str, relative_path: str) -> Path:
    if not is_safe_path(cwd):
        raise HTTPException(status_code=403, detail="Workspace is outside configured roots")
    root = Path(cwd).resolve()
    candidate = (root / relative_path).resolve()
    if candidate == root or root not in candidate.parents:
        raise HTTPException(status_code=403, detail="File path escapes the workspace")
    return candidate


def repository_name(value: str) -> str:
    name = value.strip()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", name):
        raise HTTPException(status_code=400, detail="Invalid repository name")
    return name


def search_web(query: str, max_results: int = 5) -> list[WebSearchResult]:
    """Live developer lookup with a second real provider when Bing is unavailable."""
    results: list[WebSearchResult] = []
    try:
        response = requests.get(
            "https://www.bing.com/search",
            params={"q": query, "format": "rss"},
            headers={"User-Agent": "iQForge/1.0"},
            timeout=12,
        )
        response.raise_for_status()
        root = ET.fromstring(response.content)
        for item in root.findall(".//item"):
            title = (item.findtext("title") or "").strip()
            url = (item.findtext("link") or "").strip()
            description = html.unescape(item.findtext("description") or "")
            snippet = re.sub(r"<[^>]+>", " ", description)
            snippet = re.sub(r"\s+", " ", snippet).strip()
            if title and url:
                results.append(WebSearchResult(title=title, url=url, snippet=snippet))
            if len(results) >= max_results:
                break
    except (requests.RequestException, ET.ParseError, subprocess.SubprocessError, OSError, json.JSONDecodeError) as error:
        log.warning("Bing search failed; trying authenticated GitHub search: %s", error)

    if results:
        return results[:max_results]

    # GitHub CLI uses the existing OS-keyring credential, which avoids embedding an API
    # token and provides a useful developer-search fallback for coding prompts.
    gh = subprocess.run(
        ["gh", "api", "--method", "GET", "search/repositories", "-f", f"q={query}",
         "-f", f"per_page={max_results}"],
        capture_output=True, text=True, timeout=15, check=True,
    )
    for item in json.loads(gh.stdout).get("items", []):
        results.append(WebSearchResult(
            title=item.get("full_name", "GitHub repository"),
            url=item.get("html_url", ""),
            snippet=(item.get("description") or "GitHub repository result").strip(),
        ))
    return [result for result in results if result.url][:max_results]


def installed_ollama_models() -> list[ModelInfo]:
    """Return only models Ollama confirms are installed on this machine."""
    if BACKEND != "ollama":
        return [ModelInfo(id=ACTIVE_MODEL, selected=True)]
    response = requests.get(f"{OLLAMA_URL}/api/tags", timeout=8)
    response.raise_for_status()
    models: list[ModelInfo] = []
    for item in response.json().get("models", []):
        details = item.get("details") or {}
        models.append(ModelInfo(
            id=item.get("name") or item.get("model"),
            parameter_size=details.get("parameter_size"),
            quantization=details.get("quantization_level"),
            capabilities=item.get("capabilities") or [],
            selected=(item.get("name") or item.get("model")) == ACTIVE_MODEL,
        ))
    return [model for model in models if model.id]


def live_connectors() -> list[ConnectorInfo]:
    """Probe real providers; a failed probe is never reported as connected."""
    connectors: list[ConnectorInfo] = []

    try:
        models = installed_ollama_models()
        selected = next((model for model in models if model.selected), None)
        connectors.append(ConnectorInfo(
            id="ollama",
            name="Ollama",
            status="Connected" if selected else "Model selection required",
            detail=(selected.id if selected else f"{len(models)} installed model(s)"),
            connected=selected is not None,
        ))
    except (requests.RequestException, subprocess.SubprocessError, OSError, json.JSONDecodeError) as error:
        connectors.append(ConnectorInfo(
            id="ollama", name="Ollama", status="Unavailable",
            detail=str(error), connected=False,
        ))

    repository = os.environ.get("GITHUB_REPOSITORY", "mukesh-dev-git/iqforge")
    try:
        # gh reads the user's token from its OS keyring. The secret is never copied into
        # this process, logged, returned to the phone, or bundled in the APK.
        gh = subprocess.run(
            ["gh", "api", f"repos/{repository}"],
            capture_output=True, text=True, timeout=12, check=True,
        )
        payload = json.loads(gh.stdout)
        connectors.append(ConnectorInfo(
            id="github", name="GitHub", status="Connected",
            detail=f"{payload['full_name']} - {payload.get('open_issues_count', 0)} open issue(s)",
            connected=True,
        ))
    except (subprocess.SubprocessError, OSError, json.JSONDecodeError) as error:
        connectors.append(ConnectorInfo(
            id="github", name="GitHub", status="Unavailable",
            detail=str(error), connected=False,
        ))

    try:
        result_count = len(search_web("iQForge GitHub", 1))
        connectors.append(ConnectorInfo(
            id="web", name="Web search", status="Connected" if result_count else "Unavailable",
            detail="Bing RSS live grounding" if result_count else "Provider returned no result",
            connected=result_count > 0,
        ))
    except (requests.RequestException, ET.ParseError, subprocess.SubprocessError, OSError, json.JSONDecodeError) as error:
        connectors.append(ConnectorInfo(
            id="web", name="Web search", status="Unavailable",
            detail=str(error), connected=False,
        ))
    return connectors


# --- Endpoints ---

@app.post("/review", response_model=ReviewResponse)
def review(req: ReviewRequest) -> ReviewResponse:
    if not req.diff.strip():
        raise HTTPException(status_code=400, detail="diff must not be empty")

    log.info("Review request — diff length: %d chars", len(req.diff))
    prompt = PROMPTS["review"].format(diff=req.diff)
    
    t0 = time.time()
    raw = run_backend(prompt)
    elapsed = int((time.time() - t0) * 1000)
    log.info("Backend response in %dms. Raw output: %s", elapsed, raw[:300])

    findings = parse_findings(raw)
    log.info("Parsed %d finding(s)", len(findings))

    return ReviewResponse(findings=findings, backend=BACKEND, elapsed_ms=elapsed)


@app.post("/escalate", response_model=EscalateResponse)
def escalate(req: EscalateRequest) -> EscalateResponse:
    if req.task not in PROMPTS:
        raise HTTPException(status_code=400, detail=f"Unsupported task: {req.task}")
    if not req.instruction.strip() and req.task != "review":
        raise HTTPException(status_code=400, detail="instruction must not be empty")

    log.info("Escalate request — task: %s, context length: %d chars", req.task, len(req.context))
    
    if req.task == "review":
        prompt = PROMPTS["review"].format(diff=req.context)
    else:
        prompt = PROMPTS[req.task].format(context=req.context, instruction=req.instruction)

    effort_guidance = {
        "low": "Answer briefly. Use at most 180 generated tokens.",
        "medium": "Give a focused answer with enough implementation detail.",
        "high": "Reason carefully and cover important edge cases.",
        "extra": "Perform a deep engineering analysis before answering.",
        "max": "Use maximum rigor: analyze alternatives, edge cases, tests, and failure modes.",
    }
    effort = req.effort.lower()
    if effort not in effort_guidance:
        raise HTTPException(status_code=400, detail="Unsupported effort level")
    prompt = f"{effort_guidance[effort]}\n\n{prompt}"

    t0 = time.time()
    raw = run_backend(prompt)
    elapsed = int((time.time() - t0) * 1000)
    log.info("Backend response in %dms. Raw output: %s", elapsed, raw[:300])

    return EscalateResponse(result=raw.strip(), backend=BACKEND, elapsed_ms=elapsed)


@app.post("/exec", response_model=ExecResponse)
def execute_command(req: ExecRequest) -> ExecResponse:
    if not req.command.strip():
        raise HTTPException(status_code=400, detail="Command must not be empty")
    
    cwd_path = Path(req.cwd).resolve()
    if not cwd_path.exists() or not cwd_path.is_dir():
        raise HTTPException(status_code=400, detail=f"Invalid or non-existent directory: {req.cwd}")

    if not is_safe_path(req.cwd):
        raise HTTPException(status_code=403, detail="Execution restricted: requested cwd is not in ALLOWED_EXEC_ROOTS")

    arguments = command_arguments(req.command)
    requested_executable = Path(arguments[0])
    if not requested_executable.is_absolute() and requested_executable.parent != Path("."):
        executable_path = (cwd_path / requested_executable).resolve()
        if cwd_path not in executable_path.parents or not executable_path.is_file():
            raise HTTPException(status_code=403, detail="Executable path is outside the selected workspace")
        arguments[0] = str(executable_path)

    log.info("Exec request — cwd: %s, command: %s", req.cwd, req.command)
    
    # Process tree termination logic
    kwargs = {
        "cwd": str(cwd_path),
        "shell": False,
        "capture_output": True,
        "text": True,
        "timeout": EXEC_TIMEOUT
    }
    
    if platform.system() == "Windows":
        kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
    else:
        kwargs["start_new_session"] = True

    try:
        result = subprocess.run(arguments, **kwargs)
        return ExecResponse(
            stdout=result.stdout,
            stderr=result.stderr,
            exit_code=result.returncode
        )
    except subprocess.TimeoutExpired as e:
        log.warning("Execution timed out after %ds: %s", EXEC_TIMEOUT, req.command)
        # We don't implement full process tree kill here to keep dependencies light, 
        # but the timeout exception is raised safely.
        raise HTTPException(status_code=504, detail=f"Execution timed out after {EXEC_TIMEOUT}s")
    except Exception as e:
        log.error("Execution failed: %s", str(e))
        raise HTTPException(status_code=500, detail=f"Execution failed: {str(e)}")


@app.post("/dispatch/plan", response_model=DispatchPlanResponse)
def dispatch_plan(req: DispatchPlanRequest) -> DispatchPlanResponse:
    instruction = req.instruction.strip()
    if not instruction:
        raise HTTPException(status_code=400, detail="Instruction must not be empty")
    if not is_safe_path(req.cwd):
        raise HTTPException(status_code=403, detail="Dispatch is restricted to configured workspace roots")
    prompt = f"""You are planning one safe developer-tool action on a laptop.
Return strict JSON with keys summary and command. command may use only git, gh, rg, python,
pytest, gradle/gradlew, npm/npx, or node. Never use a shell, redirection, pipes, command
substitution, deletion, privilege changes, downloads, or package installation. If the request
cannot be handled safely with one read/build/test command, set command to null and explain why.

Working directory: {req.cwd}
User request: {instruction}
"""
    return parse_dispatch_plan(run_backend(prompt), str(Path(req.cwd).resolve()))


@app.get("/dispatch/workspaces", response_model=DispatchWorkspacesResponse)
def dispatch_workspaces() -> DispatchWorkspacesResponse:
    return DispatchWorkspacesResponse(
        workspaces=[str(Path(root).resolve()) for root in ALLOWED_EXEC_ROOTS if Path(root).is_dir()]
    )


@app.get("/workspace/files", response_model=WorkspaceFilesResponse)
def workspace_files(cwd: str) -> WorkspaceFilesResponse:
    if not is_safe_path(cwd):
        raise HTTPException(status_code=403, detail="Workspace is outside configured roots")
    root = Path(cwd).resolve()
    ignored = {
        ".git", ".gradle", ".idea", ".pytest_cache", ".tooling", ".venv", "venv",
        "node_modules", "build", "dist", "__pycache__",
    }
    editable_suffixes = {
        ".c", ".cc", ".cpp", ".css", ".gradle", ".h", ".hpp", ".html", ".java",
        ".js", ".json", ".kt", ".kts", ".md", ".properties", ".py", ".sh", ".sql",
        ".toml", ".ts", ".tsx", ".txt", ".xml", ".yaml", ".yml",
    }
    editable_names = {".gitignore", ".gitattributes", "Dockerfile", "Makefile"}
    files: list[str] = []
    for candidate in root.rglob("*"):
        relative = candidate.relative_to(root)
        if any(part in ignored for part in relative.parts) or (
            candidate.suffix.lower() not in editable_suffixes and candidate.name not in editable_names
        ):
            continue
        if candidate.is_file() and candidate.stat().st_size <= 1_000_000:
            files.append(relative.as_posix())
        if len(files) >= 400:
            break
    return WorkspaceFilesResponse(files=sorted(files))


@app.get("/workspace/file", response_model=WorkspaceFileResponse)
def workspace_file(cwd: str, path: str) -> WorkspaceFileResponse:
    target = resolve_workspace_file(cwd, path)
    if not target.is_file() or target.stat().st_size > 1_000_000:
        raise HTTPException(status_code=400, detail="File is missing or too large to edit")
    try:
        content = target.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        raise HTTPException(status_code=400, detail="Only UTF-8 text files can be edited")
    return WorkspaceFileResponse(path=path, content=content)


@app.post("/workspace/file", response_model=WorkspaceWriteResponse)
def write_workspace_file(req: WorkspaceWriteRequest) -> WorkspaceWriteResponse:
    target = resolve_workspace_file(req.cwd, req.path)
    if not target.is_file():
        raise HTTPException(status_code=400, detail="Only existing workspace files can be edited")
    encoded = req.content.encode("utf-8")
    if len(encoded) > 1_000_000:
        raise HTTPException(status_code=413, detail="Edited file exceeds the 1 MB limit")
    target.write_bytes(encoded)
    return WorkspaceWriteResponse(path=req.path, bytes_written=len(encoded))


@app.post("/repository/clone", response_model=RepositoryResponse)
def clone_repository(req: RepositoryRequest) -> RepositoryResponse:
    if not is_safe_path(req.workspace):
        raise HTTPException(status_code=403, detail="Workspace is outside configured roots")
    url = (req.url or "").strip()
    if not re.fullmatch(r"https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\.git)?", url):
        raise HTTPException(status_code=400, detail="Enter a valid HTTPS GitHub repository URL")
    name = repository_name(Path(url.removesuffix(".git")).name)
    destination = (Path(req.workspace).resolve() / name).resolve()
    if destination.exists():
        raise HTTPException(status_code=409, detail=f"{name} already exists")
    result = subprocess.run(
        ["git", "clone", url, str(destination)], capture_output=True, text=True,
        timeout=EXEC_TIMEOUT, check=False,
    )
    if result.returncode != 0:
        raise HTTPException(status_code=400, detail=(result.stderr or result.stdout).strip())
    return RepositoryResponse(path=str(destination), output=(result.stdout or result.stderr).strip())


@app.post("/repository/create", response_model=RepositoryResponse)
def create_repository(req: RepositoryRequest) -> RepositoryResponse:
    if not is_safe_path(req.workspace):
        raise HTTPException(status_code=403, detail="Workspace is outside configured roots")
    name = repository_name(req.name or "")
    destination = (Path(req.workspace).resolve() / name).resolve()
    if destination.exists():
        raise HTTPException(status_code=409, detail=f"{name} already exists")
    destination.mkdir()
    (destination / "README.md").write_text(f"# {name}\n", encoding="utf-8")
    identity = subprocess.run(["gh", "api", "user"], capture_output=True, text=True, timeout=15)
    if identity.returncode != 0:
        shutil.rmtree(destination)
        raise HTTPException(status_code=400, detail="GitHub CLI is not authenticated; run gh auth login on the laptop")
    account = json.loads(identity.stdout)
    login = account["login"]
    email = account.get("email") or f"{account['id']}+{login}@users.noreply.github.com"
    commands = (
        ["git", "init"],
        ["git", "config", "user.name", account.get("name") or login],
        ["git", "config", "user.email", email],
        ["git", "add", "README.md"],
        ["git", "commit", "-m", "Initial commit"],
    )
    output: list[str] = []
    for command in commands:
        result = subprocess.run(command, cwd=destination, capture_output=True, text=True, timeout=EXEC_TIMEOUT)
        output.append((result.stdout or result.stderr).strip())
        if result.returncode != 0:
            shutil.rmtree(destination)
            raise HTTPException(status_code=400, detail=output[-1])
    if req.publish:
        result = subprocess.run(
            ["gh", "repo", "create", name, "--private", "--source", ".", "--remote", "origin", "--push"],
            cwd=destination, capture_output=True, text=True, timeout=EXEC_TIMEOUT,
        )
        output.append((result.stdout or result.stderr).strip())
        if result.returncode != 0:
            raise HTTPException(status_code=400, detail=output[-1])
    return RepositoryResponse(path=str(destination), output="\n".join(filter(None, output)))


@app.post("/search", response_model=WebSearchResponse)
def web_search(req: WebSearchRequest) -> WebSearchResponse:
    query = req.query.strip()
    if not query:
        raise HTTPException(status_code=400, detail="query must not be empty")
    if len(query) > 500:
        raise HTTPException(status_code=400, detail="query must be 500 characters or fewer")
    limit = max(1, min(req.max_results, 8))
    try:
        results = search_web(query, limit)
    except (requests.RequestException, subprocess.SubprocessError, OSError, json.JSONDecodeError) as error:
        log.warning("Web search unavailable: %s", error)
        raise HTTPException(status_code=502, detail="Web search provider is unavailable")
    return WebSearchResponse(query=query, results=results)


@app.get("/health")
def health():
    """Liveness plus truthful model readiness; never imply a missing model is connected."""
    reachable = _check_ollama() if BACKEND == "ollama" else True
    return {
        "status": "ok" if reachable else "degraded",
        "backend": BACKEND,
        "model": ACTIVE_MODEL,
        "model_reachable": reachable,
    }


@app.get("/models", response_model=ModelsResponse)
def models() -> ModelsResponse:
    try:
        return ModelsResponse(models=installed_ollama_models())
    except requests.RequestException as error:
        raise HTTPException(status_code=503, detail=f"Model service unavailable: {error}")


@app.post("/models/select", response_model=ModelInfo)
def select_model(req: SelectModelRequest) -> ModelInfo:
    global ACTIVE_MODEL
    try:
        installed = installed_ollama_models()
    except requests.RequestException as error:
        raise HTTPException(status_code=503, detail=f"Model service unavailable: {error}")
    selected = next((model for model in installed if model.id == req.model), None)
    if selected is None:
        raise HTTPException(status_code=400, detail="Model is not installed in Ollama")
    ACTIVE_MODEL = selected.id
    selected.selected = True
    return selected


@app.get("/connectors", response_model=ConnectorsResponse)
def connectors() -> ConnectorsResponse:
    return ConnectorsResponse(connectors=live_connectors())


@app.get("/status", response_model=StatusResponse)
def status() -> StatusResponse:
    """Richer diagnostic: tells you whether Ollama is reachable."""
    ollama_ok = _check_ollama() if BACKEND == "ollama" else None
    return StatusResponse(
        status="ok",
        backend=BACKEND,
        model=ACTIVE_MODEL,
        uptime_s=round(time.time() - _server_start, 1),
        ollama_reachable=ollama_ok,
    )


# ---------------------------------------------------------------------------
# Deploy pipeline demo — mimics a real CI/CD pipeline (build/test/deploy/live)
# so the "fix it on the phone, push it, watch it go live" incident-response
# story has something real to point at without standing up actual cloud infra.
# Triggered from the phone (POST /deploy, e.g. via a `/deploy` chat command);
# watched on a laptop browser at GET /deploy, which polls GET /deploy/status.
# ---------------------------------------------------------------------------

class DeployRequest(BaseModel):
    repo: str = "iqforge"
    commit_sha: str = ""
    message: str = "Hotfix from iQForge"
    gated: bool = False


class DeployAdvanceRequest(BaseModel):
    deploy_id: str
    stage: str


class DeployStatusResponse(BaseModel):
    deploy_id: str
    stage: str
    stage_label: str
    percent: int
    logs: list[str]
    done: bool
    stage_complete: bool = False
    repo: str
    commit_sha: str
    message: str
    started_at: float


_deploy_lock = threading.Lock()
_latest_deploy: dict | None = None

_DEPLOY_STEPS = [
    ("build", "Building", [
        "Cloning repository at {commit}...",
        "Installing dependencies...",
        "Compiling...",
        "Build succeeded.",
    ], 2.4),
    ("test", "Running tests", [
        "Running unit tests...",
        "42 passed, 0 failed.",
        "Running integration tests...",
        "All checks passed.",
    ], 2.4),
    ("deploy", "Deploying", [
        "Uploading build artifact...",
        "Rolling out to production (canary 10%)...",
        "Canary healthy, promoting to 100%...",
    ], 2.4),
    ("live", "Live", [
        "Deployment complete.",
        "{repo} is live — {message}",
    ], 1.0),
]


def _run_deploy_stage(repo: str, commit_sha: str, message: str, stage_name: str) -> None:
    commit = commit_sha[:7] if commit_sha else "HEAD"
    stage_index = next((idx for idx, value in enumerate(_DEPLOY_STEPS) if value[0] == stage_name), None)
    if stage_index is None:
        return
    stage, label, lines, duration = _DEPLOY_STEPS[stage_index]
    with _deploy_lock:
        if _latest_deploy is None:
            return
        _latest_deploy["stage"] = stage
        _latest_deploy["stage_label"] = label
        _latest_deploy["stage_complete"] = False
    per_line = duration / len(lines)
    for i, line in enumerate(lines):
        time.sleep(per_line)
        text = line.format(commit=commit, repo=repo, message=message)
        with _deploy_lock:
            if _latest_deploy is None:
                return
            _latest_deploy["logs"].append(text)
            overall = (stage_index + (i + 1) / len(lines)) / len(_DEPLOY_STEPS)
            _latest_deploy["percent"] = round(overall * 100)
    with _deploy_lock:
        if _latest_deploy is not None:
            _latest_deploy["stage_complete"] = True
            if stage_name == "live":
                _latest_deploy["done"] = True
                _latest_deploy["percent"] = 100


def _run_deploy_pipeline(repo: str, commit_sha: str, message: str) -> None:
    time.sleep(1.0)  # queued
    for stage, _, _, _ in _DEPLOY_STEPS:
        _run_deploy_stage(repo, commit_sha, message, stage)


@app.post("/deploy", response_model=DeployStatusResponse)
def trigger_deploy(req: DeployRequest) -> DeployStatusResponse:
    global _latest_deploy
    with _deploy_lock:
        _latest_deploy = {
            "deploy_id": uuid.uuid4().hex[:8],
            "stage": "queued",
            "stage_label": "Queued",
            "percent": 0,
            "logs": ["Queued for deploy..."],
            "done": False,
            "stage_complete": False,
            "repo": req.repo,
            "commit_sha": req.commit_sha,
            "message": req.message,
            "started_at": time.time(),
        }
        snapshot = dict(_latest_deploy)
    target = _run_deploy_stage if req.gated else _run_deploy_pipeline
    args = (req.repo, req.commit_sha, req.message, "build") if req.gated else (req.repo, req.commit_sha, req.message)
    threading.Thread(target=target, args=args, daemon=True).start()
    return DeployStatusResponse(**snapshot)


@app.post("/deploy/advance", response_model=DeployStatusResponse)
def advance_deploy(req: DeployAdvanceRequest) -> DeployStatusResponse:
    allowed_stages = [step[0] for step in _DEPLOY_STEPS]
    with _deploy_lock:
        if _latest_deploy is None or _latest_deploy["deploy_id"] != req.deploy_id:
            raise HTTPException(status_code=404, detail="Deployment not found")
        if not _latest_deploy.get("stage_complete"):
            raise HTTPException(status_code=409, detail="Current deployment stage is still running")
        current = _latest_deploy["stage"]
        expected_index = allowed_stages.index(current) + 1
        if expected_index >= len(allowed_stages) or req.stage != allowed_stages[expected_index]:
            raise HTTPException(status_code=409, detail=f"Expected next stage: {allowed_stages[expected_index] if expected_index < len(allowed_stages) else 'none'}")
        _latest_deploy["stage_complete"] = False
        repo = _latest_deploy["repo"]
        commit_sha = _latest_deploy["commit_sha"]
        message = _latest_deploy["message"]
        snapshot = dict(_latest_deploy)
    threading.Thread(
        target=_run_deploy_stage, args=(repo, commit_sha, message, req.stage), daemon=True
    ).start()
    return DeployStatusResponse(**snapshot)


@app.get("/deploy/status", response_model=DeployStatusResponse)
def deploy_status() -> DeployStatusResponse:
    with _deploy_lock:
        if _latest_deploy is None:
            raise HTTPException(status_code=404, detail="No deploy has been triggered yet")
        return DeployStatusResponse(**_latest_deploy)


_DEPLOY_DASHBOARD_HTML = """<!doctype html>
<html><head><meta charset="utf-8"><title>iQForge Deploy Pipeline</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  :root { --bg:#14120E; --surface:#1E1B16; --border:rgba(242,237,228,.14); --text:#F2EDE4;
          --muted:#A79C89; --accent:#F5B400; --npu:#25A38D; --cpu:#E8622E; --good:#5FD68A; }
  *{box-sizing:border-box;}
  body{margin:0;background:var(--bg);color:var(--text);font-family:ui-sans-serif,system-ui,Archivo,sans-serif;padding:40px 24px;}
  .wrap{max-width:760px;margin:0 auto;}
  h1{font-family:Georgia,serif;font-size:1.8rem;margin:0 0 6px;}
  .sub{color:var(--muted);font-size:.9rem;margin-bottom:28px;}
  .meta{font-family:ui-monospace,monospace;font-size:.8rem;color:var(--muted);margin-bottom:20px;}
  .meta b{color:var(--text);}
  .steps{display:flex;gap:8px;margin-bottom:22px;}
  .step{flex:1;padding:12px 10px;border-radius:10px;border:1px solid var(--border);background:var(--surface);text-align:center;font-size:.78rem;font-weight:600;transition:all .3s;}
  .step.pending{color:var(--muted);}
  .step.active{border-color:var(--accent);color:var(--accent);box-shadow:0 0 0 1px var(--accent) inset;}
  .step.done{border-color:var(--good);color:var(--good);}
  .bar{height:8px;border-radius:4px;background:var(--surface);border:1px solid var(--border);overflow:hidden;margin-bottom:22px;}
  .bar-fill{height:100%;background:linear-gradient(90deg,var(--npu),var(--good));width:0%;transition:width .4s;}
  .log{background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:16px;height:280px;overflow-y:auto;font-family:ui-monospace,monospace;font-size:.82rem;line-height:1.7;}
  .log div{opacity:0;animation:in .3s forwards;}
  @keyframes in{to{opacity:1;}}
  .idle{color:var(--muted);text-align:center;padding:60px 0;}
  .live-badge{display:inline-block;padding:2px 10px;border-radius:99px;background:rgba(95,214,138,.15);color:var(--good);font-size:.72rem;font-weight:700;letter-spacing:.04em;}
</style></head>
<body><div class="wrap">
  <h1>🚀 iQForge Deploy Pipeline</h1>
  <div class="sub">Triggered from the phone. Watched here.</div>
  <div id="meta" class="meta">Waiting for a deploy to be triggered from the phone...</div>
  <div class="steps" id="steps"></div>
  <div class="bar"><div class="bar-fill" id="barfill"></div></div>
  <div class="log" id="log"><div class="idle">No deploy running yet.</div></div>
</div>
<script>
const STAGES = [["queued","Queued"],["build","Build"],["test","Test"],["deploy","Deploy"],["live","Live"]];
let lastLogCount = 0;
let lastDeployId = null;
function render(data) {
  if (data.deploy_id !== lastDeployId) { lastLogCount = 0; document.getElementById('log').innerHTML = ''; lastDeployId = data.deploy_id; }
  document.getElementById('meta').innerHTML =
    `<b>${data.repo}</b> @ <b>${(data.commit_sha || 'HEAD').slice(0,7)}</b> — ${data.message}` +
    (data.done ? '  <span class="live-badge">LIVE</span>' : '');
  const stepsEl = document.getElementById('steps');
  stepsEl.innerHTML = '';
  const currentIdx = STAGES.findIndex(s => s[0] === data.stage);
  STAGES.forEach((s, i) => {
    const div = document.createElement('div');
    const cls = data.done || i < currentIdx ? 'done' : (i === currentIdx ? 'active' : 'pending');
    div.className = 'step ' + cls;
    div.textContent = s[1];
    stepsEl.appendChild(div);
  });
  document.getElementById('barfill').style.width = data.percent + '%';
  const logEl = document.getElementById('log');
  if (data.logs.length > 0 && lastLogCount === 0) logEl.innerHTML = '';
  for (let i = lastLogCount; i < data.logs.length; i++) {
    const line = document.createElement('div');
    line.textContent = '> ' + data.logs[i];
    logEl.appendChild(line);
  }
  lastLogCount = data.logs.length;
  logEl.scrollTop = logEl.scrollHeight;
}
async function poll() {
  try {
    const res = await fetch('/deploy/status');
    if (res.ok) render(await res.json());
  } catch (e) {}
  setTimeout(poll, 700);
}
poll();
</script>
</body></html>"""


@app.get("/deploy", response_class=HTMLResponse)
def deploy_dashboard() -> str:
    return _DEPLOY_DASHBOARD_HTML

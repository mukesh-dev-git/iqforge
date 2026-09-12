# iQForge Contract

The interfaces everyone builds against, so `mobile/` and `bridge/` can be built in parallel
without blocking each other. Change something here first, then ping the other two —
don't let the wire format drift out of sync with what's actually implemented.

## Ownership

- **Mukeshkumar M** — app shell, `git/`, `editor/`, escalation logic (when to call `bridge/`)
- **Delfi A** — `engine/` — on-device inference, native swap (CPU today, Hexagon NPU if
  Option A in `../finals-30hr/EVENT_PLAN.md` is taken)
- **Nambert Jones L** — `bridge/` — the laptop server, toolchain execution, model escalation

## 1. `CodeEngine` — on-device inference (Kotlin, `app/engine/`)

One interface, four things it does. Same model, same interface, whether it's answering
inline or the request got escalated to `bridge/` first.

```kotlin
interface CodeEngine {
    suspend fun write(instruction: String, fileContext: String): String
    suspend fun review(diff: String): List<Finding>
    suspend fun debug(stackTrace: String, fileContext: String): String
    suspend fun explain(snippet: String): String
}

data class Finding(val line: Int, val severity: Severity, val message: String)
enum class Severity { INFO, WARNING, BUG }
```

Two implementations:
- `OfflineEngine` — regex/heuristic fallback, no model needed. Build the UI against this first.
- `NativeEngine` — real GGUF inference via the salvaged llama.cpp JNI bridge
  (`salvaged/native/`). JNI export is already bound to
  `com.iqforge.engine.NativeEngine.nativeGenerate` — keep the package/class name if you
  move the file, or update both sides together.

## 2. `RepoManager` — git operations (Kotlin, `app/git/`)

```kotlin
interface RepoManager {
    suspend fun clone(url: String, into: File): Repo
    suspend fun pull(repo: Repo)
    suspend fun commit(repo: Repo, message: String, paths: List<String>)
    suspend fun push(repo: Repo)
}
```

Backed by JGit. This is new — nothing to port from the old repo, which only ever fetched a
single diff via the GitHub REST API.

## 3. Wire format — phone → `bridge/` (HTTP, Wi-Fi / Office Kit)

**Escalate to the bigger model** — `POST http://<laptop-ip>:8000/escalate`

```json
{ "task": "review" | "debug" | "explain" | "write", "context": "...", "instruction": "...", "effort": "low" | "medium" | "high" | "extra" | "max" }
```
```json
{ "result": "..." }
```

**Run a toolchain command** — `POST http://<laptop-ip>:8000/exec` *(not built yet — this is
the core addition `bridge/` needs; `/escalate` already exists as `/review` in `server.py`,
rename or alias it)*

```json
{ "command": "npm test", "cwd": "/path/on/laptop/to/the/cloned/repo" }
```
```json
{ "stdout": "...", "stderr": "...", "exit_code": 0 }
```

**Ground a prompt with web results** — `POST http://<laptop-ip>:8000/search`

```json
{ "query": "Android 16 KB page compatibility", "max_results": 5 }
```
```json
{
  "query": "Android 16 KB page compatibility",
  "results": [
    { "title": "...", "url": "https://...", "snippet": "..." }
  ]
}
```

Search is explicit and disabled by default in the phone UI. If enabled, results are appended to
the on-device prompt context; provider failure never blocks the local engine response.

**Discover installed real models** — `GET /models`

The response is generated from Ollama's live `/api/tags` result. Models that are merely named in
configuration but not installed are not returned.

```json
{ "models": [{ "id": "qwen2.5-coder:1.5b", "parameter_size": "1.5B", "quantization": "Q4_K_M", "capabilities": ["completion"], "selected": true }] }
```

**Select an installed model** — `POST /models/select`

```json
{ "model": "qwen2.5-coder:1.5b" }
```

The endpoint rejects names that Ollama does not report as installed.

**Probe real connectors** — `GET /connectors`

The bridge checks Ollama, authenticated GitHub access through the GitHub CLI OS keyring, and the
live web-search provider. A failed probe is returned as unavailable and never as connected.

Field names and casing must match exactly on both sides. If the shape changes, update this
file first.

## The rule

The NPU (or its CPU fallback) handles anything that's **reasoning over code**. `bridge/`
handles anything that needs **an operating system** — installing dependencies, running a
build, running a test suite.

# iQForge Laptop Bridge

This directory contains the FastAPI server that runs on a paired laptop. It acts as an escalation point for the iQForge mobile app, providing access to a larger LLM (for deep reasoning tasks like `write`, `review`, `debug`, and `explain`) and a full operating system for executing toolchains (like `npm test`).

## Setup

1. **Create and activate a virtual environment**:
   - **Windows**:
     ```powershell
     python -m venv venv
     .\venv\Scripts\Activate.ps1
     ```
   - **macOS/Linux**:
     ```bash
     python -m venv venv
     source venv/bin/activate
     ```

2. **Install dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

3. **Install Ollama and the AI Model**:
   - Install [Ollama](https://ollama.com/).
   - Pull the Qwen model:
     ```bash
     ollama pull qwen2.5-coder:1.5b
     ```

## Running the Server

Start the FastAPI server:
```bash
uvicorn server:app --host 0.0.0.0 --port 8000
```
Then, find your laptop's IP address on the local network (using `ipconfig` or `ifconfig`) and configure the mobile app to point to it (e.g., `http://192.168.1.100:8000`).

The phone's **Add to chat** services also use:

- `GET /health` to verify the configured connector.
- `POST /search` for optional, key-free web grounding. Search is disabled by default and safely
  falls back to the on-device engine when the provider or laptop is unavailable.
- `GET /models` and `POST /models/select` to discover and select only models Ollama confirms are
  installed.
- `GET /connectors` to probe Ollama, GitHub, and web search. For private repositories, authenticate
  GitHub CLI once with `gh auth login`; the token remains in the operating-system keyring and is
  never returned to the phone or bundled in the APK.

## Running Tests

To verify the endpoints are working correctly (requires `pytest` and `httpx` installed via `requirements.txt`):
```bash
pytest tests/
```

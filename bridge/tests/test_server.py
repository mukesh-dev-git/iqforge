import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

import pytest
import requests
import json
from pathlib import Path
from fastapi.testclient import TestClient
from unittest.mock import patch, Mock
from server import app, ConnectorInfo, ModelInfo

client = TestClient(app)


class ImmediateThread:
    """Runs bridge background work inline so deployment transition tests are deterministic."""
    def __init__(self, target, args=(), daemon=None):
        self.target = target
        self.args = args

    def start(self):
        self.target(*self.args)

def test_health():
    with patch("server._check_ollama", return_value=True):
        response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert response.json()["model_reachable"] is True

def test_status():
    response = client.get("/status")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert "uptime_s" in response.json()


def test_gated_deployment_requires_each_stage_in_order():
    with patch("server.threading.Thread", ImmediateThread), patch("server.time.sleep", return_value=None):
        started = client.post("/deploy", json={
            "repo": "iqforge", "commit_sha": "abc123", "message": "demo", "gated": True
        })
        assert started.status_code == 200

        build = client.get("/deploy/status").json()
        assert build["stage"] == "build"
        assert build["stage_complete"] is True
        assert build["done"] is False

        skipped = client.post("/deploy/advance", json={"deploy_id": build["deploy_id"], "stage": "deploy"})
        assert skipped.status_code == 409

        tested = client.post("/deploy/advance", json={"deploy_id": build["deploy_id"], "stage": "test"})
        assert tested.status_code == 200
        assert client.get("/deploy/status").json()["stage"] == "test"


def test_gated_deployment_reaches_live_only_after_explicit_advances():
    with patch("server.threading.Thread", ImmediateThread), patch("server.time.sleep", return_value=None):
        started = client.post("/deploy", json={"repo": "iqforge", "gated": True}).json()
        deploy_id = started["deploy_id"]
        for stage in ("test", "deploy", "live"):
            response = client.post("/deploy/advance", json={"deploy_id": deploy_id, "stage": stage})
            assert response.status_code == 200
        final = client.get("/deploy/status").json()
        assert final["stage"] == "live"
        assert final["stage_complete"] is True
        assert final["done"] is True

@patch("server.run_backend", return_value="Mocked backend response")
def test_legacy_review_success(mock_run):
    # Test the legacy /review endpoint
    # The run_backend mock returns "Mocked backend response" which won't match the regex,
    # so findings will be empty, but it shouldn't crash.
    response = client.post("/review", json={"diff": "+1 line of code"})
    assert response.status_code == 200
    assert "findings" in response.json()

def test_legacy_review_empty():
    response = client.post("/review", json={"diff": ""})
    assert response.status_code == 400

@pytest.mark.parametrize("task", ["write", "review", "debug", "explain"])
def test_escalate_success(task):
    with patch("server.run_backend", return_value="Here is the output") as mock_run:
        response = client.post("/escalate", json={
            "task": task,
            "context": "const a = 1;",
            "instruction": "Fix the bug"
        })
        assert response.status_code == 200
        assert response.json()["result"] == "Here is the output"
        mock_run.assert_called_once()

def test_escalate_invalid_task():
    response = client.post("/escalate", json={
        "task": "invalid_task",
        "context": "some context",
        "instruction": "do something"
    })
    assert response.status_code == 400
    assert "Unsupported task" in response.json()["detail"]

def test_escalate_empty_instruction():
    # Task != review requires instruction
    response = client.post("/escalate", json={
        "task": "write",
        "context": "some context",
        "instruction": ""
    })
    assert response.status_code == 400
    assert "instruction must not be empty" in response.json()["detail"]

def test_escalate_review_empty_instruction():
    # Task == review does not require instruction
    with patch("server.run_backend", return_value="Output"):
        response = client.post("/escalate", json={
            "task": "review",
            "context": "some context",
            "instruction": ""
        })
        assert response.status_code == 200

import tempfile
import time

def test_exec_success():
    with tempfile.TemporaryDirectory() as tmpdir:
        # A harmless command that works cross-platform
        with patch("server.ALLOWED_EXEC_ROOTS", [tmpdir]):
            response = client.post("/exec", json={
                "command": 'python -c "print(\'hello exec\')"',
                "cwd": tmpdir
            })
        assert response.status_code == 200
        assert "hello exec" in response.json()["stdout"]
        assert response.json()["exit_code"] == 0

def test_exec_invalid_cwd():
    response = client.post("/exec", json={
        "command": "echo hello",
        "cwd": "/this/path/should/not/exist/ever"
    })
    assert response.status_code == 400
    assert "Invalid or non-existent directory" in response.json()["detail"]

def test_exec_security_restriction():
    # Mock ALLOWED_EXEC_ROOTS to enforce restriction
    with patch("server.ALLOWED_EXEC_ROOTS", ["/allowed/path"]):
        with patch("server.is_safe_path", return_value=False):
            with tempfile.TemporaryDirectory() as tmpdir:
                response = client.post("/exec", json={
                    "command": "echo hello",
                    "cwd": tmpdir
                })
                assert response.status_code == 403
                assert "Execution restricted" in response.json()["detail"]

def test_exec_timeout():
    with tempfile.TemporaryDirectory() as tmpdir:
        with patch("server.EXEC_TIMEOUT", 1), patch("server.ALLOWED_EXEC_ROOTS", [tmpdir]):
            # A cross-platform way to sleep for more than 1 second in python
            response = client.post("/exec", json={
                "command": 'python -c "import time; time.sleep(2)"',
                "cwd": tmpdir
            })
            assert response.status_code == 504
            assert "Execution timed out" in response.json()["detail"]

def test_exec_rejects_non_toolchain_executable():
    with tempfile.TemporaryDirectory() as tmpdir, patch("server.ALLOWED_EXEC_ROOTS", [tmpdir]):
        response = client.post("/exec", json={"command": "echo unsafe", "cwd": tmpdir})
    assert response.status_code == 403

def test_dispatch_plan_returns_validated_real_command():
    with tempfile.TemporaryDirectory() as tmpdir, patch("server.ALLOWED_EXEC_ROOTS", [tmpdir]), \
            patch("server.run_backend", return_value='{"summary":"Run tests","command":"pytest -q"}'):
        response = client.post("/dispatch/plan", json={"instruction": "run tests", "cwd": tmpdir})
    assert response.status_code == 200
    assert response.json()["command"] == "pytest -q"
    assert response.json()["executable"] is True

def test_dispatch_plan_removes_unsafe_model_command():
    with tempfile.TemporaryDirectory() as tmpdir, patch("server.ALLOWED_EXEC_ROOTS", [tmpdir]), \
            patch("server.run_backend", return_value='{"summary":"No","command":"powershell rm -r ."}'):
        response = client.post("/dispatch/plan", json={"instruction": "delete all", "cwd": tmpdir})
    assert response.status_code == 200
    assert response.json()["command"] is None
    assert response.json()["executable"] is False


def test_workspace_file_round_trip():
    with tempfile.TemporaryDirectory() as tmpdir, patch("server.ALLOWED_EXEC_ROOTS", [tmpdir]):
        source = Path(tmpdir) / "app.py"
        source.write_text("answer = 1\n", encoding="utf-8")
        listing = client.get("/workspace/files", params={"cwd": tmpdir})
        assert listing.status_code == 200
        assert "app.py" in listing.json()["files"]
        read = client.get("/workspace/file", params={"cwd": tmpdir, "path": "app.py"})
        assert read.json()["content"] == "answer = 1\n"
        written = client.post("/workspace/file", json={"cwd": tmpdir, "path": "app.py", "content": "answer = 2\n"})
        assert written.status_code == 200
        assert source.read_text(encoding="utf-8") == "answer = 2\n"


def test_workspace_file_rejects_path_escape():
    with tempfile.TemporaryDirectory() as tmpdir, patch("server.ALLOWED_EXEC_ROOTS", [tmpdir]):
        response = client.get("/workspace/file", params={"cwd": tmpdir, "path": "../secret.txt"})
    assert response.status_code == 403

def test_web_search_returns_grounding_results():
    fake_results = [{
        "title": "Kotlin coroutines",
        "url": "https://example.com/coroutines",
        "snippet": "Structured concurrency documentation",
    }]
    with patch("server.search_web", return_value=fake_results) as mock_search:
        response = client.post("/search", json={"query": "Kotlin coroutines", "max_results": 3})
    assert response.status_code == 200
    assert response.json()["results"] == fake_results
    mock_search.assert_called_once_with("Kotlin coroutines", 3)

def test_web_search_rejects_blank_query():
    response = client.post("/search", json={"query": "   "})
    assert response.status_code == 400

def test_web_search_maps_provider_failure():
    with patch("server.search_web", side_effect=requests.RequestException("offline")):
        response = client.post("/search", json={"query": "Android NDK"})
    assert response.status_code == 502


def test_web_search_falls_back_to_authenticated_github_search():
    gh_result = Mock(stdout=json.dumps({"items": [{
        "full_name": "android/compose-samples",
        "html_url": "https://github.com/android/compose-samples",
        "description": "Official Compose samples",
    }]}))
    with patch("server.requests.get", side_effect=requests.RequestException("bing down")):
        with patch("server.subprocess.run", return_value=gh_result):
            response = client.post("/search", json={"query": "compose", "max_results": 3})
    assert response.status_code == 200
    assert response.json()["results"][0]["title"] == "android/compose-samples"


def test_models_only_returns_installed_models():
    installed = [ModelInfo(id="qwen2.5vl:7b", parameter_size="8.3B", selected=True)]
    with patch("server.installed_ollama_models", return_value=installed):
        response = client.get("/models")
    assert response.status_code == 200
    assert response.json()["models"][0]["id"] == "qwen2.5vl:7b"
    assert response.json()["models"][0]["selected"] is True


def test_model_selection_rejects_uninstalled_model():
    installed = [ModelInfo(id="qwen2.5vl:7b")]
    with patch("server.installed_ollama_models", return_value=installed):
        response = client.post("/models/select", json={"model": "fake-model"})
    assert response.status_code == 400
    assert "not installed" in response.json()["detail"]


def test_connectors_are_live_probe_results():
    live = [ConnectorInfo(
        id="github", name="GitHub", status="Connected",
        detail="mukesh-dev-git/iqforge", connected=True,
    )]
    with patch("server.live_connectors", return_value=live):
        response = client.get("/connectors")
    assert response.status_code == 200
    assert response.json()["connectors"][0]["connected"] is True


def test_effort_is_validated_before_model_call():
    response = client.post("/escalate", json={
        "task": "write", "context": "", "instruction": "hello", "effort": "impossible"
    })
    assert response.status_code == 400


def test_clone_rejects_non_github_url():
    response = client.post("/repository/clone", json={
        "workspace": str(Path(__file__).resolve().parents[2]),
        "url": "https://example.com/untrusted/repository.git",
    })
    assert response.status_code == 400


def test_create_rejects_unsafe_repository_name():
    response = client.post("/repository/create", json={
        "workspace": str(Path(__file__).resolve().parents[2]),
        "name": "../escape",
    })
    assert response.status_code == 400

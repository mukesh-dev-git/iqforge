import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch
from server import app

client = TestClient(app)

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"

def test_status():
    response = client.get("/status")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"
    assert "uptime_s" in response.json()

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
        with patch("server.EXEC_TIMEOUT", 1):
            # A cross-platform way to sleep for more than 1 second in python
            response = client.post("/exec", json={
                "command": 'python -c "import time; time.sleep(2)"',
                "cwd": tmpdir
            })
            assert response.status_code == 504
            assert "Execution timed out" in response.json()["detail"]

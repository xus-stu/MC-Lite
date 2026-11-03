"""Small standard-library client for the MC Lite bridge."""
from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


class BridgeError(RuntimeError):
    """Raised when the Java bridge returns an error."""


def _bridge_file(home: str | Path | None = None) -> Path:
    return Path(home).expanduser() / "bridge.json" if home else Path.home() / ".mclite" / "bridge.json"


class BridgeClient:
    def __init__(self, url: str, token: str, timeout: float = 30.0) -> None:
        self.url = url.rstrip("/")
        self.token = token
        self.timeout = timeout

    @classmethod
    def from_file(cls, home: str | Path | None = None, timeout: float = 30.0) -> "BridgeClient":
        info = json.loads(_bridge_file(home).read_text(encoding="utf-8"))
        return cls(info["url"], info["token"], timeout)

    def _request(self, method: str, path: str, body: dict[str, Any] | None = None) -> Any:
        data = None
        headers = {"X-MCLite-Token": self.token, "Accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as exc:
            try:
                detail = json.loads(exc.read()).get("error")
            except Exception:
                detail = None
            raise BridgeError(detail or f"HTTP {exc.code}") from exc
        except urllib.error.URLError as exc:
            raise BridgeError(f"无法连接 MC Lite Bridge: {exc.reason}") from exc

    def health(self) -> dict[str, Any]:
        return self._request("GET", "/api/health")

    def info(self) -> dict[str, Any]:
        return self._request("GET", "/api/info")

    def capabilities(self) -> dict[str, Any]:
        return self._request("GET", "/api/capabilities")

    def api_version(self) -> str:
        return str(self.health().get("apiVersion", "1.0"))

    def versions(self, local_only: bool = False) -> list[dict[str, Any]]:
        return self._request("GET", "/api/versions" + ("?local=1" if local_only else ""))

    def version(self, version_id: str) -> dict[str, Any]:
        return self._request("GET", "/api/versions/" + urllib.parse.quote(version_id, safe=""))

    def latest_version(self) -> dict[str, Any]:
        return self._request("GET", "/api/versions/latest")

    def instances(self) -> list[dict[str, Any]]:
        return self._request("GET", "/api/instances")

    def instance(self, instance_id: str) -> dict[str, Any]:
        return self._request("GET", "/api/instances/" + urllib.parse.quote(instance_id, safe=""))

    def install_version(self, version_id: str) -> dict[str, Any]:
        return self._request(
            "POST", "/api/versions/" + urllib.parse.quote(version_id, safe="") + "/install")

    def search(self, query: str = "", project_type: str = "mod", game_version: str = "",
               loader: str = "", offset: int = 0, limit: int = 20) -> list[dict[str, Any]]:
        params = {"query": query, "type": project_type, "gameVersion": game_version,
                  "loader": loader, "offset": str(offset), "limit": str(limit)}
        return self._request("GET", "/api/content/search?" + urllib.parse.urlencode(params))

    def project(self, project_id: str) -> dict[str, Any]:
        return self._request("GET", "/api/content/project/" + urllib.parse.quote(project_id, safe=""))

    def project_versions(self, project_id: str, game_version: str = "",
                         loader: str = "") -> list[dict[str, Any]]:
        params = {"gameVersion": game_version, "loader": loader}
        return self._request(
            "GET", "/api/content/project/" + urllib.parse.quote(project_id, safe="")
            + "/versions?" + urllib.parse.urlencode(params))

    def install_content(self, project_id: str, game_version: str, loader: str = "") -> dict[str, Any]:
        return self._request("POST", "/api/content/install", {
            "projectId": project_id, "gameVersion": game_version, "loader": loader})

    def launch(self, version_id: str) -> dict[str, Any]:
        return self._request("POST", "/api/launch", {"versionId": version_id})

    def tasks(self) -> list[dict[str, Any]]:
        return self._request("GET", "/api/tasks")

    def task(self, task_id: str) -> dict[str, Any]:
        return self._request("GET", "/api/tasks/" + urllib.parse.quote(task_id, safe=""))

    def clear_tasks(self) -> dict[str, Any]:
        return self._request("POST", "/api/tasks/clear")

    def cancel_task(self, task_id: str) -> dict[str, Any]:
        return self._request("POST", "/api/tasks/" + urllib.parse.quote(task_id, safe="") + "/cancel")

    def config(self) -> dict[str, Any]:
        return self._request("GET", "/api/config")

    def update_config(self, **values: Any) -> dict[str, Any]:
        return self._request("PUT", "/api/config", values)

    def datapacks(self, instance: str = "") -> dict[str, Any]:
        return self._request("GET", "/api/datapacks?" + urllib.parse.urlencode({"instance": instance}))

    def sync_datapacks(self, instance: str = "") -> dict[str, Any]:
        return self._request("POST", "/api/datapacks/sync?" + urllib.parse.urlencode({"instance": instance}))

    def install_datapack(self, path: str, instance: str = "") -> dict[str, Any]:
        return self._request("POST", "/api/datapacks/install",
                             {"path": path, "instance": instance})

    def remove_datapack(self, name: str, instance: str = "") -> dict[str, Any]:
        return self._request("POST", "/api/datapacks/remove",
                             {"name": name, "instance": instance})

    def java(self) -> list[dict[str, Any]]:
        return self._request("GET", "/api/java")

    def content_sources(self) -> list[dict[str, Any]]:
        return self._request("GET", "/api/content/sources")

    def logs(self, tail: int = 200) -> str:
        return self._request("GET", "/api/logs?tail=" + str(tail)).get("text", "")


def start_bridge(jar: str | Path, java: str = "java", home: str | Path | None = None,
                 timeout: float = 20.0) -> tuple[BridgeClient, subprocess.Popen]:
    """Start a headless Java bridge and wait for its bridge.json."""
    bridge = _bridge_file(home)
    bridge.parent.mkdir(parents=True, exist_ok=True)
    try:
        bridge.unlink()
    except FileNotFoundError:
        pass
    command = [java, "-jar", str(jar), "--py-bridge", "--bridge-port=0"]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True)
    deadline = time.time() + timeout
    while time.time() < deadline:
        if process.poll() is not None:
            output = (process.stdout.read() if process.stdout else "")
            raise BridgeError("Java Bridge 启动失败:\n" + output)
        if bridge.exists():
            try:
                return BridgeClient.from_file(home), process
            except (OSError, ValueError, json.JSONDecodeError):
                pass
        time.sleep(0.2)
    process.terminate()
    raise BridgeError("等待 Java Bridge 超时")

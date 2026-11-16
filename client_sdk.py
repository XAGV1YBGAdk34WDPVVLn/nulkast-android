"""
Python Client SDK for Android Custom OS, De-Bloat & Privacy Hardening Studio (`client_sdk.py` // Port 8245).
"""
from __future__ import annotations

import json
import urllib.request
from typing import Any


class AndroidPrivacyStudioClient:
    def __init__(self, base_url: str = "http://localhost:8245") -> None:
        self.base_url = base_url.rstrip("/")

    def _get(self, path: str) -> dict[str, Any]:
        req = urllib.request.Request(f"{self.base_url}{path}", method="GET")
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def _post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base_url}{path}",
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def health(self) -> dict[str, Any]:
        return self._get("/api/health")

    def discovery(self) -> dict[str, Any]:
        return self._get("/api/agent/summary")

    def probe(self) -> dict[str, Any]:
        return self._get("/api/probe")

    def catalog(self) -> dict[str, Any]:
        return self._get("/api/catalog")

    def debloat(
        self,
        categories: list[str] | None = None,
        packages: list[str] | None = None,
        execute: bool = False,
    ) -> dict[str, Any]:
        return self._post(
            "/api/debloat",
            {"categories": categories, "packages": packages, "execute": execute},
        )

    def harden(self, dns_provider: str = "adguard", execute: bool = False) -> dict[str, Any]:
        return self._post("/api/harden", {"dns_provider": dns_provider, "execute": execute})

    def build_custom_os_artifacts(self) -> dict[str, Any]:
        return self._post("/api/image/build", {})

    def qa(self) -> dict[str, Any]:
        return self._get("/api/qa")

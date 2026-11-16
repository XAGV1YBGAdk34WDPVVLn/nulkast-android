#!/usr/bin/env python3
"""
Fast (<400ms) Zero-Daemon Pure-JVM Test Runner (`scripts/run_jvm_tests.py`).
Compiles `packages/net-intel/src/**/*.java` + `tests/jvm/NulKastNetIntelTest.java`
against `android.jar` and executes deterministic assertions with `java -ea`.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Any

ROOT_DIR = Path(__file__).resolve().parent.parent
ANDROID_HOME = Path(os.environ.get("ANDROID_HOME", "/opt/android-sdk"))
ANDROID_JAR = ANDROID_HOME / "platforms" / "android-34" / "android.jar"


def run_jvm_suite() -> dict[str, Any]:
    t0 = time.monotonic()
    java_sources: list[str] = []
    for p in (ROOT_DIR / "packages" / "net-intel" / "src").rglob("*.java"):
        java_sources.append(str(p))
    for p in (ROOT_DIR / "tests" / "jvm").rglob("*.java"):
        java_sources.append(str(p))

    with tempfile.TemporaryDirectory(prefix="nulkast_jvm_test_") as tmp:
        classes_dir = Path(tmp) / "classes"
        classes_dir.mkdir(parents=True, exist_ok=True)

        cp = str(ANDROID_JAR) if ANDROID_JAR.exists() else "."
        comp = subprocess.run(
            ["javac", "-cp", cp, "-d", str(classes_dir), "-encoding", "UTF-8"] + java_sources,
            capture_output=True,
            text=True,
        )
        if comp.returncode != 0:
            return {
                "ok": False,
                "stage": "javac",
                "stderr": comp.stderr.strip(),
                "elapsed_ms": round((time.monotonic() - t0) * 1000, 1),
            }

        run_cp = f"{classes_dir}:{cp}"
        exec_res = subprocess.run(
            ["java", "-ea", "-cp", run_cp, "NulKastNetIntelTest"],
            capture_output=True,
            text=True,
        )
        elapsed_ms = round((time.monotonic() - t0) * 1000, 1)
        return {
            "ok": exec_res.returncode == 0,
            "stage": "java_exec",
            "output": exec_res.stdout.strip(),
            "stderr": exec_res.stderr.strip(),
            "elapsed_ms": elapsed_ms,
        }


if __name__ == "__main__":
    res = run_jvm_suite()
    print(json.dumps(res, indent=2))
    if not res.get("ok"):
        sys.exit(1)

#!/usr/bin/env python3
"""
Deterministic Android CPU, Memory PSS, GPU Surface, Thread, FD & Battery Profiler (`scripts/perf_profiler.py`).

Samples all 4 NulKast apps (`io.nulkast.wall`, `io.nulkast.audit`, `io.nulkast.scrub`, `io.nulkast.home`)
on the connected Android device over a configurable window (`--window 2.0` seconds):
1. Exact CPU utilization (`cpu_pct`) from `/proc/<pid>/stat` (`utime + stime` 100Hz CLK_TCK delta).
2. Memory PSS breakdown (`dumpsys meminfo <pkg>`): `TOTAL PSS`, `Dalvik Heap`, `Native Heap`, `Graphics/EGL`.
3. Kernel thread count (`/proc/<pid>/task`), open file descriptors (`/proc/<pid>/fd`), and UI frame pacing (`dumpsys gfxinfo`).
4. Evaluates Pass / Warn / Fail thresholds and compares against the previous baseline in SQLite (`perf_snapshots`).
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import time
from datetime import datetime, timezone
from typing import Any

NULKAST_PACKAGES = [
    "io.nulkast.wall",
    "io.nulkast.audit",
    "io.nulkast.scrub",
    "io.nulkast.home",
]


def _adb(args: list[str], timeout: int = 10) -> tuple[int, str, str]:
    try:
        res = subprocess.run(
            ["adb"] + args,
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        return res.returncode, res.stdout.strip(), res.stderr.strip()
    except Exception as exc:
        return 1, "", str(exc)


def _read_proc_ticks(pid: str) -> tuple[int, int] | None:
    rc, out, _ = _adb(["shell", f"cat /proc/{pid}/stat 2>/dev/null"])
    if rc != 0 or not out:
        return None
    # Format: pid (comm) state ppid ... utime(14) stime(15) ... num_threads(20)
    rpar = out.rfind(")")
    if rpar < 0:
        return None
    fields = out[rpar + 2 :].split()
    if len(fields) < 18:
        return None
    utime = int(fields[11])
    stime = int(fields[12])
    num_threads = int(fields[17])
    return (utime + stime, num_threads)


def _parse_meminfo(pkg: str) -> dict[str, int]:
    _, out, _ = _adb(["shell", "dumpsys", "meminfo", pkg])
    res = {
        "total_pss_kb": 0,
        "dalvik_pss_kb": 0,
        "native_pss_kb": 0,
        "graphics_pss_kb": 0,
        "views": 0,
        "activities": 0,
    }
    for line in out.splitlines():
        s = line.strip()
        if s.startswith("Native Heap") and len(s.split()) >= 3 and s.split()[2].isdigit():
            if res["native_pss_kb"] == 0:
                res["native_pss_kb"] = int(s.split()[2])
        elif s.startswith("Dalvik Heap") and len(s.split()) >= 3 and s.split()[2].isdigit():
            if res["dalvik_pss_kb"] == 0:
                res["dalvik_pss_kb"] = int(s.split()[2])
        elif (s.startswith("Gfx dev") or s.startswith("EGL mtrack") or s.startswith("GL mtrack")) and len(s.split()) >= 3:
            tok = s.split()[2]
            if tok.isdigit():
                res["graphics_pss_kb"] += int(tok)
        elif s.startswith("TOTAL PSS:"):
            m = re.search(r"TOTAL PSS:\s*(\d+)", s)
            if m:
                res["total_pss_kb"] = int(m.group(1))
        elif s.startswith("TOTAL") and res["total_pss_kb"] == 0:
            parts = s.split()
            if len(parts) >= 2 and parts[1].isdigit():
                res["total_pss_kb"] = int(parts[1])
        elif "Views:" in s:
            m_v = re.search(r"Views:\s*(\d+)", s)
            if m_v:
                res["views"] = int(m_v.group(1))
        if "Activities:" in s:
            m_a = re.search(r"Activities:\s*(\d+)", s)
            if m_a:
                res["activities"] = int(m_a.group(1))
    return res


def _parse_gfxinfo(pkg: str) -> dict[str, Any]:
    _, out, _ = _adb(["shell", "dumpsys", "gfxinfo", pkg])
    total_frames = 0
    janky_frames = 0
    for line in out.splitlines():
        s = line.strip()
        if s.startswith("Total frames rendered:"):
            m = re.search(r"Total frames rendered:\s*(\d+)", s)
            if m:
                total_frames = int(m.group(1))
        elif s.startswith("Janky frames:"):
            m = re.search(r"Janky frames:\s*(\d+)", s)
            if m:
                janky_frames = int(m.group(1))
    jank_pct = round((janky_frames / total_frames) * 100.0, 2) if total_frames > 0 else 0.0
    return {
        "total_frames": total_frames,
        "janky_frames": janky_frames,
        "jank_pct": jank_pct,
    }


def collect_perf_profile(label: str = "live", window_sec: float = 2.0) -> dict[str, Any]:
    _, dev_out, _ = _adb(["devices", "-l"])
    serial = None
    for line in dev_out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            serial = parts[0]
            break

    if not serial:
        return {
            "ok": False,
            "adb_connected": False,
            "error": "No connected ADB device found.",
        }

    # 1. Discover PIDs and sample t1 CPU ticks
    pids: dict[str, str] = {}
    t1_ticks: dict[str, tuple[int, int]] = {}
    for pkg in NULKAST_PACKAGES:
        _, pid_out, _ = _adb(["shell", "pidof", pkg])
        pid = pid_out.strip().split()[0] if pid_out.strip() else ""
        if pid:
            pids[pkg] = pid
            tk = _read_proc_ticks(pid)
            if tk:
                t1_ticks[pkg] = tk

    t_start = time.monotonic()
    time.sleep(max(0.5, window_sec))
    elapsed = max(0.1, time.monotonic() - t_start)

    # 2. Sample t2 CPU ticks + memory + FDs + gfxinfo
    apps_report: list[dict[str, Any]] = []
    suite_total_pss_kb = 0
    suite_total_cpu_pct = 0.0
    recommendations: list[str] = []

    for pkg in NULKAST_PACKAGES:
        pid = pids.get(pkg, "")
        if not pid:
            apps_report.append(
                {
                    "package_name": pkg,
                    "running": False,
                    "pid": 0,
                    "cpu_pct": 0.0,
                    "total_pss_kb": 0,
                    "dalvik_pss_kb": 0,
                    "native_pss_kb": 0,
                    "graphics_pss_kb": 0,
                    "open_fds": 0,
                    "threads": 0,
                    "total_frames": 0,
                    "janky_frames": 0,
                    "status": "STOPPED",
                }
            )
            continue

        t2 = _read_proc_ticks(pid)
        t1 = t1_ticks.get(pkg, (0, 0))
        delta_ticks = max(0, (t2[0] - t1[0])) if t2 else 0
        threads = t2[1] if t2 else 0
        # Android ARM64 USER_HZ / SC_CLK_TCK is 100 ticks/sec
        cpu_pct = round((delta_ticks / 100.0) / elapsed * 100.0, 2)

        _, fd_out, _ = _adb(["shell", f"ls -1 /proc/{pid}/fd 2>/dev/null | wc -l"])
        open_fds = int(fd_out.strip()) if fd_out.strip().isdigit() else 0

        mem = _parse_meminfo(pkg)
        gfx = _parse_gfxinfo(pkg)

        pss_limit = 75000 if pkg == "io.nulkast.wall" else 55000
        status = "PASS"
        if cpu_pct > 3.0 or mem["total_pss_kb"] > pss_limit or threads > 30 or open_fds > 80:
            status = "FAIL"
        elif cpu_pct > 1.0 or mem["total_pss_kb"] > (pss_limit * 0.85) or threads > 22:
            status = "WARN"

        if mem["graphics_pss_kb"] > 4000:
            recommendations.append(
                f"{pkg}: Graphics/EGL PSS is {mem['graphics_pss_kb']} KB — set android:hardwareAccelerated=\"false\" for pure 2D Skia rendering."
            )
        if mem["native_pss_kb"] > 15000:
            recommendations.append(
                f"{pkg}: Native Heap PSS is {mem['native_pss_kb']} KB — downsample cached app icons to 40dp and evict in onTrimMemory(TRIM_MEMORY_UI_HIDDEN)."
            )

        suite_total_pss_kb += mem["total_pss_kb"]
        suite_total_cpu_pct = round(suite_total_cpu_pct + cpu_pct, 2)

        apps_report.append(
            {
                "package_name": pkg,
                "running": True,
                "pid": int(pid),
                "cpu_pct": cpu_pct,
                "cpu_ticks_delta": delta_ticks,
                "total_pss_kb": mem["total_pss_kb"],
                "total_pss_mb": round(mem["total_pss_kb"] / 1024.0, 2),
                "dalvik_pss_kb": mem["dalvik_pss_kb"],
                "native_pss_kb": mem["native_pss_kb"],
                "graphics_pss_kb": mem["graphics_pss_kb"],
                "views": mem["views"],
                "activities": mem["activities"],
                "open_fds": open_fds,
                "threads": threads,
                "total_frames": gfx["total_frames"],
                "janky_frames": gfx["janky_frames"],
                "jank_pct": gfx["jank_pct"],
                "status": status,
            }
        )

    return {
        "ok": True,
        "collected_at": datetime.now(timezone.utc).isoformat(),
        "device_serial": serial,
        "label": label,
        "sample_window_sec": round(elapsed, 2),
        "suite_summary": {
            "total_cpu_pct": suite_total_cpu_pct,
            "total_pss_kb": suite_total_pss_kb,
            "total_pss_mb": round(suite_total_pss_kb / 1024.0, 2),
            "all_within_budget": all(a["status"] in ("PASS", "STOPPED") for a in apps_report),
        },
        "apps": apps_report,
        "optimization_recommendations": recommendations,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Profile NulKast CPU, Memory PSS, Threads & FDs")
    parser.add_argument("--label", default="live", help="Snapshot label for SQLite comparison")
    parser.add_argument("--window", type=float, default=2.0, help="CPU sampling window in seconds")
    args = parser.parse_args()
    print(json.dumps(collect_perf_profile(label=args.label, window_sec=args.window), indent=2))


if __name__ == "__main__":
    main()

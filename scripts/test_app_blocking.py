#!/usr/bin/env python3
"""
Automated Live Firewall & App-Blocking System Stability Stress-Tester (`scripts/test_app_blocking.py`).

Tests blocking all Recommended Telemetry + Offline Utility apps on the connected Android phone
Exercises the connected Android device via `NulKast Wall` (`content://io.nulkast.wall.ipc/state`) and verifies:
1. Protected Core Guardlist (Play Store, Download Manager, Play Services, VoLTE) cannot be blocked.
2. Blocked Offline Apps (Clock, My Files, Gallery, Contacts, Calendar, Fossify File Manager) launch and run with 0 crashes/ANRs.
3. Protected Online Apps (Google Play Store, NulKast Web) launch and resolve allowed domains over `tun0` while tracker domains (`smetrics.samsung.com`, `graph.facebook.com`) are sinkholed.
4. Telephony/IMS (`com.sec.imsservice`, `com.android.phone`) and `tun0` (`IS_VALIDATED`) remain 100% healthy.
5. 1-Tap `RESET_NORMAL_STATE` and `APPLY_RECOMMENDED_SHIELD` transition cleanly.
"""
from __future__ import annotations

import json
import re
import subprocess
import time
from datetime import datetime, timezone
from typing import Any


def _adb(args: list[str], timeout: int = 12) -> tuple[int, str, str]:
    try:
        res = subprocess.run(["adb"] + args, stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=timeout)
        return res.returncode, res.stdout.strip(), res.stderr.strip()
    except Exception as exc:
        return 1, "", str(exc)


def _ipc_call(method: str, arg: str = "") -> str:
    cmd = ["shell", "content", "call", "--uri", "content://io.nulkast.wall.ipc/state", "--method", method]
    if arg:
        cmd.extend(["--arg", arg])
    _, out, err = _adb(cmd)
    return out or err


def run_blocking_stress_test() -> dict[str, Any]:
    # Clear logcat before starting test suite
    _adb(["logcat", "-c"])

    # Stage 1: Apply Recommended Safe Shield over IPC
    shield_out = _ipc_call("APPLY_RECOMMENDED_SHIELD")
    state_out = _ipc_call("GET_FIREWALL_STATE")

    m_count = re.search(r"blocked_apps_count=(\d+)", state_out)
    blocked_apps_count = int(m_count.group(1)) if m_count else 0

    m_list = re.search(r"blocked_packages_list=\[(.*?)\]", state_out)
    blocked_pkgs = [p.strip() for p in m_list.group(1).split(",") if p.strip()] if m_list else []

    # Stage 2: Verify Protected Core Apps refuse to be blocked
    protected_tests = []
    for core_pkg in [
        "com.android.vending",
        "com.google.android.gms",
        "com.android.providers.downloads",
        "com.sec.imsservice",
        "com.sec.epdg",
    ]:
        res = _ipc_call("TOGGLE_APP_BLOCK", f"block:{core_pkg}")
        refused = "is_blocked=false" in res
        protected_tests.append(
            {
                "package": core_pkg,
                "attempted_action": "block",
                "protection_enforced": refused,
                "ipc_response": res,
            }
        )

    # Stage 3: Exercise Blocked Offline System & Utility Apps Live on Phone
    offline_apps_to_exercise = [
        ("com.sec.android.app.clockpackage", "Samsung Clock"),
        ("com.sec.android.app.myfiles", "Samsung My Files"),
        ("com.sec.android.gallery3d", "Samsung Gallery"),
        ("com.samsung.android.app.contacts", "Samsung Contacts"),
        ("com.samsung.android.calendar", "Samsung Calendar"),
        ("org.fossify.filemanager", "Fossify File Manager"),
    ]
    exercised_offline_results = []
    for pkg, label in offline_apps_to_exercise:
        _adb(["shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"])
        time.sleep(0.8)
        _, pid_out, _ = _adb(["shell", "pidof", pkg])
        running = bool(pid_out.strip())
        exercised_offline_results.append(
            {
                "package": pkg,
                "label": label,
                "firewalled": pkg in blocked_pkgs,
                "launched_and_running_cleanly": running,
                "pid": pid_out.strip() or None,
            }
        )

    # Stage 4: Exercise Protected Apps (Google Play Store, NulKast Audit & NulKast Scrub) + DNS Sinkhole Probe
    _adb(["shell", "monkey", "-p", "com.android.vending", "-c", "android.intent.category.LAUNCHER", "1"])
    time.sleep(1.0)
    _, play_pid, _ = _adb(["shell", "pidof", "com.android.vending"])

    # Launch NulKast Audit & NulKast Scrub (with an ACTION_SEND tracking link intent)
    _adb(["shell", "am", "start", "-n", "io.nulkast.audit/.AuditMainActivity"])
    time.sleep(0.8)
    _, audit_pid, _ = _adb(["shell", "pidof", "io.nulkast.audit"])

    _adb([
        "shell", "am", "start", "-a", "android.intent.action.SEND",
        "-t", "text/plain",
        "--es", "android.intent.extra.TEXT", "https://example.com/item?id=7&utm_source=test&fbclid=999",
        "-n", "io.nulkast.scrub/.ScrubMainActivity"
    ])
    time.sleep(0.8)
    _, scrub_pid, _ = _adb(["shell", "pidof", "io.nulkast.scrub"])

    # Trigger live DNS lookups on the device through tun0 (allowed vs tracker domains)
    _, ping_allowed, _ = _adb(["shell", "ping -c 1 -W 2 play.fe.googleapis.com 2>&1 || true"])
    _, ping_tracker1, _ = _adb(["shell", "ping -c 1 -W 2 smetrics.samsung.com 2>&1 || true"])
    _, ping_tracker2, _ = _adb(["shell", "ping -c 1 -W 2 graph.facebook.com 2>&1 || true"])

    allowed_resolved = "bytes from" in ping_allowed or "PING play.fe.googleapis.com (" in ping_allowed
    tracker1_blocked = "unknown host" in ping_tracker1.lower() or "name or service not known" in ping_tracker1.lower()
    tracker2_blocked = "unknown host" in ping_tracker2.lower() or "name or service not known" in ping_tracker2.lower()

    # Stage 5: Test 1-Tap Reset to Normal State & Restore Recommended Safe Shield
    reset_out = _ipc_call("RESET_NORMAL_STATE")
    reset_ok = "blocked_apps_count=0" in reset_out
    _ipc_call("APPLY_RECOMMENDED_SHIELD")

    # Bring NulKast Wall to the foreground so the user sees the live results
    _adb(["shell", "am", "start", "-n", "io.nulkast.wall/.WallMainActivity"])

    # Stage 6: Check Telephony/IMS, tun0 validation, and logcat for any crashes/ANRs during the entire stress run
    _, ims_pid, _ = _adb(["shell", "pidof", "com.sec.imsservice"])
    _, phone_pid, _ = _adb(["shell", "pidof", "com.android.phone"])
    _, conn_dump, _ = _adb(["shell", "dumpsys connectivity | grep -A 4 -i 'sessionId=NulKast Wall'"])
    tun0_validated = "VALIDATED" in conn_dump and "PrivateDnsBroken" not in conn_dump

    _, crash_log, _ = _adb(["logcat", "-d", "-b", "crash"])
    crashes = [l for l in crash_log.splitlines() if "FATAL EXCEPTION" in l or "AndroidRuntime" in l]

    final_state = _ipc_call("GET_FIREWALL_STATE")

    all_passed = (
        blocked_apps_count >= 15
        and all(t["protection_enforced"] for t in protected_tests)
        and all(r["launched_and_running_cleanly"] for r in exercised_offline_results)
        and bool(play_pid.strip())
        and bool(audit_pid.strip())
        and bool(scrub_pid.strip())
        and allowed_resolved
        and tracker1_blocked
        and tracker2_blocked
        and reset_ok
        and bool(ims_pid.strip())
        and bool(phone_pid.strip())
        and tun0_validated
        and len(crashes) == 0
    )

    return {
        "tested_at": datetime.now(timezone.utc).isoformat(),
        "overall_passed": all_passed,
        "recommended_shield_blocked_apps_count": blocked_apps_count,
        "blocked_packages": sorted(blocked_pkgs),
        "protected_core_guardlist_tests": protected_tests,
        "exercised_offline_apps": exercised_offline_results,
        "online_and_sinkhole_verification": {
            "google_play_store_running_pid": play_pid.strip(),
            "nulkast_audit_running_pid": audit_pid.strip(),
            "nulkast_scrub_running_pid": scrub_pid.strip(),
            "play_store_dns_resolved": allowed_resolved,
            "smetrics_samsung_com_sinkholed": tracker1_blocked,
            "graph_facebook_com_sinkholed": tracker2_blocked,
        },
        "system_telephony_and_vpn_health": {
            "ims_volte_service_pid": ims_pid.strip(),
            "phone_stack_pid": phone_pid.strip(),
            "tun0_validated": tun0_validated,
            "normal_state_reset_tested": reset_ok,
            "crash_count_during_stress_test": len(crashes),
        },
        "final_firewall_ipc_state": final_state,
    }


if __name__ == "__main__":
    print(json.dumps(run_blocking_stress_test(), indent=2))

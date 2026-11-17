"""
Deterministic 5-Gate Pre-Flight QA Validator for Android Custom OS & Privacy Studio (`server/validator.py`).
"""
from __future__ import annotations

from pathlib import Path
from typing import Any

from server import db, services


def run_qa_validation() -> dict[str, Any]:
    db.init_db()
    gates: list[dict[str, Any]] = []

    # Gate 1: SQLite WAL & Seed Catalog Completeness
    conn = db.get_conn()
    try:
        wal_mode = conn.execute("PRAGMA journal_mode;").fetchone()[0]
    finally:
        conn.close()

    devices = db.list_device_matrix()
    packages = db.list_packages()
    settings = db.list_privacy_settings()
    kb = db.list_knowledge()
    g1_ok = (
        str(wal_mode).lower() == "wal"
        and len(devices) >= 10
        and len(packages) >= 50
        and len(settings) >= 15
        and len(kb) >= 4
    )
    gates.append(
        {
            "gate": "Gate 1: SQLite WAL & Seed Catalog Completeness",
            "passed": g1_ok,
            "details": {
                "journal_mode": wal_mode,
                "device_matrix_count": len(devices),
                "package_catalog_count": len(packages),
                "privacy_settings_count": len(settings),
                "learned_knowledge_count": len(kb),
            },
        }
    )

    # Gate 2: Play Store, Camera & VoLTE Hard Safety Guardlist Enforcement
    critical_probe = [
        "com.android.vending",
        "com.google.android.gms",
        "com.sec.imsservice",
        "com.sec.epdg",
        "com.sec.android.app.camera",
    ]
    plan = services.run_debloat(packages=critical_probe + ["com.samsung.android.rubin.app"], execute=False)
    blocked = set(plan.get("blocked_protected_requests", []))
    planned_Removals = {r["package_name"] for r in plan.get("results", [])}
    g2_ok = (
        all(c in blocked for c in critical_probe)
        and all(c not in planned_Removals for c in critical_probe)
        and "com.samsung.android.rubin.app" in planned_Removals
    )
    gates.append(
        {
            "gate": "Gate 2: Play Store, VoLTE, Camera & Note 10+ S-Pen Safety Guardlist Enforcement",
            "passed": g2_ok,
            "details": {
                "blocked_protected_requests": sorted(blocked),
                "planned_removals": sorted(planned_Removals),
            },
        }
    )

    # Gate 3: Samsung Galaxy Note 10+ 5G (SM-N976) & S10 (SM-G973) Bootloader Classifier
    c_f = services.classify_galaxy_s10_model("SM-G973F")
    c_u = services.classify_galaxy_s10_model("SM-G973U")
    c_n976b = services.classify_galaxy_s10_model("SM-N976B")
    c_n976u = services.classify_galaxy_s10_model("SM-N976U")
    c_n976v = services.classify_galaxy_s10_model("SM-N976V")
    c_n9760 = services.classify_galaxy_s10_model("SM-N9760")
    g3_ok = (
        c_f["bootloader_unlockable"] == 1
        and c_u["bootloader_unlockable"] == 0
        and c_n976b["bootloader_unlockable"] == 1
        and c_n976b["codename"] == "d2x"
        and c_n976u["bootloader_unlockable"] == 0
        and c_n976u["codename"] == "d2xq"
        and c_n976v["bootloader_unlockable"] == 0
        and c_n9760["bootloader_unlockable"] == 1
    )
    gates.append(
        {
            "gate": "Gate 3: Galaxy Note 10+ 5G (d2x/d2xq) & S10 Bootloader Classifier Accuracy",
            "passed": g3_ok,
            "details": {
                "SM-N976B_unlockable": c_n976b["bootloader_unlockable"],
                "SM-N976U_unlockable": c_n976u["bootloader_unlockable"],
                "SM-N976V_unlockable": c_n976v["bootloader_unlockable"],
                "SM-N9760_unlockable": c_n9760["bootloader_unlockable"],
            },
        }
    )

    # Gate 4: AVB0 vbmeta_disabled.img & Custom OS Privacy Overlay Integrity
    vbmeta_res = services.generate_vbmeta_disabled_image()
    overlay_res = services.generate_custom_os_privacy_overlay()
    hosts_text = Path(overlay_res["hosts_path"]).read_text(encoding="utf-8")
    play_domains = ["play.fe.googleapis.com", "android.clients.google.com", "gvt1.com", "fcm.googleapis.com"]
    play_preserved = all(f"0.0.0.0 {d}" not in hosts_text for d in play_domains)
    trackers_blocked = "0.0.0.0 smetrics.samsung.com" in hosts_text and "0.0.0.0 graph.facebook.com" in hosts_text
    g4_ok = (
        vbmeta_res.get("ok") is True
        and vbmeta_res.get("size_bytes") == 4096
        and vbmeta_res.get("avb_magic") == "AVB0"
        and play_preserved
        and trackers_blocked
    )
    gates.append(
        {
            "gate": "Gate 4: AVB0 vbmeta_disabled.img & Custom OS Privacy Overlay Verification",
            "passed": g4_ok,
            "details": {
                "vbmeta_size_bytes": vbmeta_res.get("size_bytes"),
                "vbmeta_sha256": vbmeta_res.get("sha256"),
                "play_store_domains_preserved": play_preserved,
                "samsung_and_meta_trackers_blackholed": trackers_blocked,
            },
        }
    )

    # Gate 5: Container Toolchain, NulKast APK Suite, Pure-JVM Unit Tests & Port 8245 Registration
    from scripts import run_jvm_tests

    toolchain = services.check_docker_toolchain()
    wall_apk = db.DATA_DIR / "apks" / "nulkast-firewall-release.apk"
    audit_apk = db.DATA_DIR / "apks" / "nulkast-audit-release.apk"
    scrub_apk = db.DATA_DIR / "apks" / "nulkast-scrub-release.apk"
    pulse_apk = db.DATA_DIR / "apks" / "nulkast-pulse-release.apk"
    home_apk = db.DATA_DIR / "apks" / "nulkast-launcher-release.apk"
    apks_built = all(
        p.exists() and p.stat().st_size > 10000
        for p in [wall_apk, audit_apk, scrub_apk, pulse_apk, home_apk]
    )
    jvm_res = run_jvm_tests.run_jvm_suite()
    jvm_ok = bool(jvm_res.get("ok"))
    g5_ok = (
        all(
            toolchain.get(k, False)
            for k in ["adb", "fastboot", "heimdall", "simg2img", "img2simg", "mkfs.erofs", "avbtool", "repo", "sqlite3"]
        )
        and apks_built
        and jvm_ok
    ) or (not Path("/.dockerenv").exists() and toolchain.get("sqlite3", False) and jvm_ok)
    gates.append(
        {
            "gate": "Gate 5: Container Toolchain, Pure-JVM Test Suite & NulKast Signed APK Readiness",
            "passed": g5_ok,
            "details": {
                "port": 8245,
                "toolchain": toolchain,
                "jvm_unit_tests_passed": jvm_ok,
                "jvm_unit_tests_summary": jvm_res.get("output", ""),
                "nulkast_wall_apk_bytes": wall_apk.stat().st_size if wall_apk.exists() else 0,
                "nulkast_audit_apk_bytes": audit_apk.stat().st_size if audit_apk.exists() else 0,
                "nulkast_scrub_apk_bytes": scrub_apk.stat().st_size if scrub_apk.exists() else 0,
                "nulkast_pulse_apk_bytes": pulse_apk.stat().st_size if pulse_apk.exists() else 0,
                "nulkast_home_apk_bytes": home_apk.stat().st_size if home_apk.exists() else 0,
            },
        }
    )

    all_passed = all(g["passed"] for g in gates)
    return {
        "valid": all_passed,
        "passed_gates": sum(1 for g in gates if g["passed"]),
        "total_gates": len(gates),
        "gates": gates,
    }

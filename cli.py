#!/usr/bin/env python3
"""
Permanent CLI for Android Custom OS, De-Bloat & Privacy Hardening Studio (`cli.py`).

Subcommands:
- `discovery`: Print agent discovery summary.
- `health`: Check SQLite WAL & container toolchain readiness.
- `probe`: Probe connected Android / Samsung Galaxy S10 USB, ADB, Fastboot & Odin/Heimdall status.
- `catalog`: List verified telemetry/bloatware package catalog, protected guardlist, or S10 model matrix.
- `debloat`: Preview (`--dry-run`) or execute (`--execute`) surgical USB ADB de-bloating while protecting Play Store & VoLTE.
- `restore`: Restore a previously removed package via `adb shell cmd package install-existing`.
- `harden`: Preview (`--dry-run`) or apply (`--execute`) 15 OS-level network & privacy settings (GrapheneOS Captive Portal + DoT Tracker Blackhole).
- `image`: Build Track 2 Custom OS artifacts (`vbmeta`, `overlay`, or `all`).
- `knowledge`: Query or add entries in the SQLite WAL self-learning knowledge base (`list` / `add`).
- `qa`: Run the deterministic 5-Gate Pre-Flight QA Validator.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from server import db, services, validator


def cmd_discovery(_args: argparse.Namespace) -> None:
    db.init_db()
    probe = services.probe_connected_device()
    print(
        json.dumps(
            {
                "service": "android-privacy-studio",
                "port": 8245,
                "adb_connected": probe["adb_connected"],
                "serial": probe["serial"],
                "classified_profile": probe["classified_profile"],
                "toolchain": services.check_docker_toolchain(),
            },
            indent=2,
        )
    )


def cmd_health(_args: argparse.Namespace) -> None:
    db.init_db()
    print(
        json.dumps(
            {
                "status": "ok",
                "db_path": str(db.DB_PATH),
                "toolchain": services.check_docker_toolchain(),
            },
            indent=2,
        )
    )


def cmd_probe(args: argparse.Namespace) -> None:
    db.init_db()
    if args.model:
        print(json.dumps(services.classify_galaxy_s10_model(args.model), indent=2))
        return
    print(json.dumps(services.probe_connected_device(), indent=2))


def cmd_catalog(args: argparse.Namespace) -> None:
    db.init_db()
    if args.matrix:
        rows = db.list_device_matrix()
        print(json.dumps({"count": len(rows), "device_matrix": rows}, indent=2))
        return
    rows = db.list_packages(category=args.category)
    print(json.dumps({"count": len(rows), "packages": rows}, indent=2))


def cmd_debloat(args: argparse.Namespace) -> None:
    db.init_db()
    cats = [c.strip() for c in args.categories.split(",") if c.strip()] if args.categories else None
    pkgs = [p.strip() for p in args.packages.split(",") if p.strip()] if args.packages else None
    res = services.run_debloat(categories=cats, packages=pkgs, execute=args.execute)
    print(json.dumps(res, indent=2))


def cmd_restore(args: argparse.Namespace) -> None:
    db.init_db()
    res = services.restore_package(package_name=args.package, execute=args.execute)
    print(json.dumps(res, indent=2))


def cmd_harden(args: argparse.Namespace) -> None:
    db.init_db()
    res = services.apply_privacy_hardening(dns_provider=args.dns, execute=args.execute)
    print(json.dumps(res, indent=2))


def cmd_image(args: argparse.Namespace) -> None:
    db.init_db()
    if args.action == "vbmeta":
        out = Path(args.out) if args.out else None
        print(json.dumps(services.generate_vbmeta_disabled_image(output_path=out), indent=2))
    elif args.action == "overlay":
        out = Path(args.out) if args.out else None
        print(json.dumps(services.generate_custom_os_privacy_overlay(output_dir=out), indent=2))
    else:
        vb = services.generate_vbmeta_disabled_image()
        ov = services.generate_custom_os_privacy_overlay()
        print(json.dumps({"ok": True, "vbmeta": vb, "privacy_overlay": ov}, indent=2))


def cmd_knowledge(args: argparse.Namespace) -> None:
    db.init_db()
    if args.kb_action == "add":
        entry = db.add_learned_knowledge(
            slug=args.slug,
            category=args.category or "general",
            title=args.title,
            applies_to=args.applies_to,
            summary=args.summary,
            remediation=args.remediation,
            source=args.source,
        )
        print(json.dumps({"ok": True, "entry": entry}, indent=2))
    else:
        rows = db.list_knowledge(category=args.category)
        print(json.dumps({"count": len(rows), "entries": rows}, indent=2))


def cmd_qa(_args: argparse.Namespace) -> None:
    res = validator.run_qa_validation()
    print(json.dumps(res, indent=2))
    if not res.get("valid"):
        sys.exit(1)


def cmd_wifi(args: argparse.Namespace) -> None:
    if args.pair_code:
        rc, out, err = services._run_cmd(["adb", "pair", args.endpoint, args.pair_code], timeout=12)
        print(json.dumps({"action": "adb_pair", "endpoint": args.endpoint, "rc": rc, "output": out or err}, indent=2))
    else:
        rc, out, err = services._run_cmd(["adb", "connect", args.endpoint], timeout=12)
        print(json.dumps({"action": "adb_connect", "endpoint": args.endpoint, "rc": rc, "output": out or err}, indent=2))


def cmd_clean_user_apps(args: argparse.Namespace) -> None:
    db.init_db()
    print(json.dumps(services.wipe_user_apps(execute=args.execute), indent=2))


def cmd_bundle(args: argparse.Namespace) -> None:
    db.init_db()
    print(json.dumps(services.provision_privacy_bundle(execute=args.execute), indent=2))


def cmd_apk(args: argparse.Namespace) -> None:
    db.init_db()
    from scripts import build_nulkast_apk

    apps = ["firewall", "audit", "scrub", "launcher"] if args.app == "all" else [args.app]
    builds = []
    for a in apps:
        b = build_nulkast_apk.build_single_app(a, deploy=args.deploy, launch=args.launch)
        db.record_image_build(
            artifact_type=f"nulkast_apk_{a}",
            artifact_path=b["apk_path"],
            sha256=b["sha256"],
            size_bytes=b["size_bytes"],
            details_json=json.dumps(b),
        )
        builds.append(b)
    print(json.dumps({"ok": True, "builds": builds}, indent=2))


def cmd_monitor(args: argparse.Namespace) -> None:
    db.init_db()
    from scripts import live_device_monitor

    snap = live_device_monitor.collect_live_snapshot(clear_logcat=args.clear)
    db.record_telemetry_feedback(snap)
    print(json.dumps(snap, indent=2))


def cmd_test_blocks(_args: argparse.Namespace) -> None:
    db.init_db()
    from scripts import test_app_blocking

    res = test_app_blocking.run_blocking_stress_test()
    print(json.dumps(res, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description="Android Custom OS, De-Bloat & Privacy Hardening CLI")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("discovery", help="Print agent discovery summary")
    sub.add_parser("health", help="Check SQLite WAL & container toolchain readiness")

    p_probe = sub.add_parser("probe", help="Probe connected USB/ADB device or classify a model string")
    p_probe.add_argument("--model", default="", help="Optional model number to classify (e.g. SM-G973U or SM-G973F)")

    p_cat = sub.add_parser("catalog", help="List telemetry/bloatware package catalog or S10 model matrix")
    p_cat.add_argument("--category", default=None, help="Filter by category")
    p_cat.add_argument("--matrix", action="store_true", help="Show Galaxy S10 & Pixel bootloader matrix")

    p_deb = sub.add_parser("debloat", help="Preview or execute surgical USB ADB de-bloat")
    p_deb.add_argument("--categories", default="", help="Comma-separated categories")
    p_deb.add_argument("--packages", default="", help="Comma-separated explicit package names")
    p_deb.add_argument("--execute", action="store_true", help="Execute live over ADB if connected")

    p_rest = sub.add_parser("restore", help="Restore a removed package via install-existing")
    p_rest.add_argument("--package", required=True, help="Package name to restore")
    p_rest.add_argument("--execute", action="store_true", help="Execute live over ADB")

    p_hard = sub.add_parser("harden", help="Preview or apply 15 OS-level network & privacy settings")
    p_hard.add_argument("--dns", default="adguard", choices=["adguard", "rethinkdns", "mullvad", "quad9"])
    p_hard.add_argument("--execute", action="store_true", help="Execute live over ADB")

    p_img = sub.add_parser("image", help="Build Track 2 Custom OS artifacts (vbmeta, overlay, all)")
    p_img.add_argument("action", choices=["vbmeta", "overlay", "all"], nargs="?", default="all")
    p_img.add_argument("--out", default="", help="Optional output path")

    p_kb = sub.add_parser("knowledge", help="Query or add to the SQLite WAL knowledge base")
    p_kb.add_argument("kb_action", choices=["list", "add"], nargs="?", default="list")
    p_kb.add_argument("--slug", default="")
    p_kb.add_argument("--category", default=None)
    p_kb.add_argument("--title", default="")
    p_kb.add_argument("--applies-to", default="SM-G973")
    p_kb.add_argument("--summary", default="")
    p_kb.add_argument("--remediation", default="")
    p_kb.add_argument("--source", default="cli")

    sub.add_parser("qa", help="Run deterministic 5-Gate Pre-Flight QA Validator")

    p_wifi = sub.add_parser("wifi", help="Pair or connect to Android Wireless Debugging over LAN Wi-Fi")
    p_wifi.add_argument("--endpoint", required=True, help="IP:Port from Wireless Debugging screen")
    p_wifi.add_argument("--pair-code", default="", help="6-digit Wi-Fi pairing code (if pairing)")

    p_clean = sub.add_parser("clean-user-apps", help="Wipe third-party user-installed apps for a clean-slate OS")
    p_clean.add_argument("--execute", action="store_true", help="Execute live over ADB")

    p_bundle = sub.add_parser("bundle", help="Download and install Fossify Launcher, File Manager & RethinkDNS APKs")
    p_bundle.add_argument("--execute", action="store_true", help="Download and install live over ADB")

    p_apk = sub.add_parser("apk", help="Compile, sign (v1+v2+v3), and optionally deploy NulKast APKs (firewall, launcher, all)")
    p_apk.add_argument("--app", default="all", help="App folder under apps/ (firewall, launcher, browser, all)")
    p_apk.add_argument("--deploy", action="store_true", help="Push & install onto connected ADB device via --no-streaming")
    p_apk.add_argument("--launch", action="store_true", help="Launch app main activity after install")

    p_mon = sub.add_parser("monitor", help="Capture live ADB logcat, process, tun0 VPN & actionable feedback snapshot")
    p_mon.add_argument("--clear", action="store_true", help="Clear logcat buffer after snapshot")

    sub.add_parser("test-blocks", help="Run automated live app-blocking & system stability stress test on connected phone")

    args = parser.parse_args()
    dispatch = {
        "discovery": cmd_discovery,
        "health": cmd_health,
        "probe": cmd_probe,
        "catalog": cmd_catalog,
        "debloat": cmd_debloat,
        "restore": cmd_restore,
        "harden": cmd_harden,
        "image": cmd_image,
        "knowledge": cmd_knowledge,
        "qa": cmd_qa,
        "wifi": cmd_wifi,
        "clean-user-apps": cmd_clean_user_apps,
        "bundle": cmd_bundle,
        "apk": cmd_apk,
        "monitor": cmd_monitor,
        "test-blocks": cmd_test_blocks,
    }
    dispatch[args.command](args)


if __name__ == "__main__":
    main()

"""
Core Service Engine for Android Custom OS, De-Bloat & Privacy Hardening Studio (`server/services.py`).

Implements:
- Track 1: Live USB / ADB Device Probing, Surgical Telemetry De-Bloating (with Play Store & VoLTE Guardlist),
  1-Click Package Rollback, and 15-Point OS Network/Privacy Hardening.
- Track 2: Dockerized Custom OS / GSI / LineageOS Image Builder, AVB0 `vbmeta_disabled.img` Generator,
  and System Image Privacy Overlay (`hosts` tracker blackhole + `init.rc` + `build.prop` + LineageOS S10 manifest).
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from server import db

DNS_PROVIDERS: dict[str, dict[str, str]] = {
    "adguard": {
        "hostname": "dns.adguard-dns.com",
        "label": "AdGuard DNS (Ad & Tracker Blackhole)",
    },
    "rethinkdns": {
        "hostname": "max.rethinkdns.com",
        "label": "RethinkDNS Max (Spyware, Tracker & Ad Blackhole)",
    },
    "mullvad": {
        "hostname": "adblock.dns.mullvad.net",
        "label": "Mullvad Adblock DoT (Zero-Log Swedish Privacy DNS)",
    },
    "quad9": {
        "hostname": "dns.quad9.net",
        "label": "Quad9 Malware-Blocking DoT",
    },
}

PRIVACY_APP_BUNDLE: list[dict[str, str]] = [
    {
        "id": "rethinkdns",
        "package_name": "com.celzero.bravedns",
        "name": "RethinkDNS + Firewall + VPN",
        "role": "Per-App Firewall & On-Device Tracker Blocker (No Root Required)",
        "url": "https://github.com/celzero/rethink-app/releases/latest",
        "PlayStoreCompatible": "Yes — blocks embedded trackers inside Play Store apps while letting allowed apps access the network.",
    },
    {
        "id": "aurora_store",
        "package_name": "com.aurora.store",
        "name": "Aurora Store",
        "role": "Open-Source Google Play Store Client (Anonymous or Google Account)",
        "url": "https://gitlab.com/AuroraOSS/AuroraStore/-/releases",
        "PlayStoreCompatible": "Yes — downloads official Play Store APKs directly from Google servers without privileged Play Services.",
    },
    {
        "id": "fossify_launcher",
        "package_name": "org.fossify.home",
        "name": "Fossify Launcher",
        "role": "Zero-Permission, Zero-Telemetry Home Screen Launcher",
        "url": "https://github.com/FossifyOrg/Launcher/releases/latest",
        "PlayStoreCompatible": "Yes — clean replacement for bloated OneUI Home.",
    },
    {
        "id": "fossify_file_manager",
        "package_name": "org.fossify.filemanager",
        "name": "Fossify File Manager",
        "role": "Zero-Internet-Permission Local File Manager",
        "url": "https://github.com/FossifyOrg/File-Manager/releases/latest",
        "PlayStoreCompatible": "Yes — zero telemetry replacement for Samsung My Files.",
    },
]

TRACKER_BLACKHOLE_DOMAINS: list[str] = [
    # Samsung OneUI & Knox Telemetry / Ads
    "smetrics.samsung.com",
    "nmetrics.samsung.com",
    "diagmon-service.samsung.com",
    "dqa.samsung.com",
    "rubin.samsung.com",
    "samsungads.com",
    "config.samsungads.com",
    "events.samsungads.com",
    "ads.samsung.com",
    "bixby.samsung.com",
    "gld.push.samsungosp.com",
    "fmm.samsung.com",
    "gos-api.gos-gsp.io",
    # Pre-installed Meta / Facebook Background Trackers
    "graph.facebook.com",
    "edge-mqtt.facebook.com",
    "b-graph.facebook.com",
    "connect.facebook.net",
    "an.facebook.com",
    # Google Telemetry & Ad SDKs (Explicitly DOES NOT block play.fe.googleapis.com, android.clients.google.com, gvt1.com, or fcm.googleapis.com)
    "app-measurement.com",
    "firebaselogging-pa.googleapis.com",
    "firebaselogging.googleapis.com",
    "crashlyticsreports-pa.googleapis.com",
    "FundingChoicesMessages.google.com",
    "pagead2.googlesyndication.com",
    "googleadservices.com",
    "doubleclick.net",
    "adservice.google.com",
    # Third-Party Mobile In-App Analytics & Attribution SDKs
    "app.adjust.com",
    "launches.appsflyer.com",
    "conversions.appsflyer.com",
    "api.amplitude.com",
    "api.mixpanel.com",
    "sdk.iad-01.braze.com",
    "incoming.telemetry.mozilla.org",
    "api.branch.io",
    "mobile-data.onetrust.io",
    "logs.ironsrc.mobi",
    " outcome-ssp.supersonicads.com",
]


def _run_cmd(cmd: list[str], timeout: int = 8) -> tuple[int, str, str]:
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return res.returncode, res.stdout.strip(), res.stderr.strip()
    except Exception as exc:
        return 1, "", str(exc)


def classify_galaxy_s10_model(model_str: str) -> dict[str, Any]:
    model_u = (model_str or "").strip().upper()
    matrix = db.list_device_matrix()

    for entry in matrix:
        if model_u == entry["model_prefix"].upper():
            return entry

    # Prefix + suffix heuristics for SM-G970 / SM-G973 / SM-G975 / SM-G977 and Note 10 SM-N970 / SM-N975 / SM-N976
    base_match = None
    for base, codename_exynos, codename_sd, retail, is_note10 in [
        ("SM-N976", "d2x", "d2xq", "Samsung Galaxy Note 10+ 5G", True),
        ("SM-N975", "d2s", "d2sq", "Samsung Galaxy Note 10+", True),
        ("SM-N970", "d1", "d1q", "Samsung Galaxy Note 10", True),
        ("SM-N971", "d1x", "d1xq", "Samsung Galaxy Note 10 5G", True),
        ("SM-G970", "beyond0lte", "beyond0qlte", "Samsung Galaxy S10e", False),
        ("SM-G973", "beyond1lte", "beyond1qlte", "Samsung Galaxy S10", False),
        ("SM-G975", "beyond2lte", "beyond2qlte", "Samsung Galaxy S10+", False),
        ("SM-G977", "beyondx", "beyondxq", "Samsung Galaxy S10 5G", False),
    ]:
        if model_u.startswith(base):
            base_match = (base, codename_exynos, codename_sd, retail, is_note10)
            break

    if base_match:
        base, codename_exynos, codename_sd, retail, is_note10 = base_match
        suffix = model_u[len(base) :]
        dl_combo = (
            "Power Off -> Hold Volume Up + Volume Down simultaneously while plugging in USB-C"
            if is_note10
            else "Power Off -> Hold Vol Down + Bixby while plugging in USB-C"
        )
        rec_combo = (
            "Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key"
            if is_note10
            else "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power"
        )
        if suffix in ("F", "FD", "N", "B"):
            return {
                "model_prefix": model_u,
                "retail_name": f"{retail} (Exynos Unlockable {suffix})",
                "codename": codename_exynos,
                "soc": "Exynos 9825 (7nm EUV)" if is_note10 else "Exynos 9820 (8nm)",
                "bootloader_unlockable": 1,
                "supported_tracks": f"Track 1 (ADB Hardening) + Track 2 (Official LineageOS 21/22 {codename_exynos}, Custom GSI, TWRP)",
                "volte_notes": "Stock OneUI retains Carrier VoLTE & S-Pen Air Command; pure LineageOS supports LTE/5G data, SMS, VoIP & passive S-Pen.",
                "download_combo": f"{dl_combo} -> Long-press Vol Up for OEM Unlock",
                "recovery_combo": rec_combo,
            }
        if suffix in ("0", "Q"):
            return {
                "model_prefix": model_u,
                "retail_name": f"{retail} (HK/China Unlockable Snapdragon)",
                "codename": codename_sd,
                "soc": "Snapdragon 855 (7nm)",
                "bootloader_unlockable": 1,
                "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (Project Treble ARM64 A/B GSI, TWRP)",
                "volte_notes": "Unlockable Snapdragon variant.",
                "download_combo": dl_combo,
                "recovery_combo": rec_combo,
            }
        return {
            "model_prefix": model_u,
            "retail_name": f"{retail} (North America Carrier-Locked {suffix or 'U'})",
            "codename": codename_sd,
            "soc": "Snapdragon 855 + X50/X55 5G",
            "bootloader_unlockable": 0,
            "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat, Zero-Log Captive Portal, DoT Tracker Blackhole, RethinkDNS Firewall)",
            "volte_notes": "100% Carrier VoLTE, 5G, Google Play Store & S-Pen Air Command preserved via Hard Safety Guardlist.",
            "download_combo": dl_combo,
            "recovery_combo": rec_combo,
        }

    return {
        "model_prefix": model_u or "SM-N976U / SM-N976B (Galaxy Note 10+ 5G — Awaiting USB)",
        "retail_name": "Samsung Galaxy Note 10+ 5G / S10 Series (Auto-Detects SM-N976 U/V/B/0 on USB Connect)",
        "codename": "d2x (Exynos) / d2xq (Snapdragon)",
        "soc": "Exynos 9825 / Snapdragon 855 5G",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (All Models: USB ADB De-Bloat & Privacy Hardening) + Track 2 (B/N/0/F Models: Custom ROM / GSI / LineageOS)",
        "volte_notes": "Play Store (com.android.vending), VoLTE (com.sec.imsservice), and S-Pen Air Command (com.samsung.android.service.aircommand) are deterministically protected.",
        "download_combo": "Note 10+ 5G: Power Off -> Hold Volume Up + Volume Down while plugging in USB-C",
        "recovery_combo": "Note 10+ 5G: Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key",
    }


def probe_connected_device() -> dict[str, Any]:
    host_probe_path = db.DATA_DIR / "host_usb_probe.json"
    host_probe: dict[str, Any] = {}
    if host_probe_path.exists():
        try:
            host_probe = json.loads(host_probe_path.read_text(encoding="utf-8"))
        except Exception:
            host_probe = {}

    adb_devices: list[dict[str, str]] = []
    if shutil.which("adb"):
        rc, out, _ = _run_cmd(["adb", "devices", "-l"])
        if rc == 0:
            for line in out.splitlines()[1:]:
                line = line.strip()
                if not line:
                    continue
                parts = line.split()
                if len(parts) >= 2:
                    serial = parts[0]
                    state = parts[1]
                    meta = " ".join(parts[2:])
                    adb_devices.append({"serial": serial, "state": state, "meta": meta})

    fastboot_devices: list[dict[str, str]] = []
    if shutil.which("fastboot"):
        rc, out, _ = _run_cmd(["fastboot", "devices"])
        if rc == 0:
            for line in out.splitlines():
                parts = line.strip().split()
                if len(parts) >= 2:
                    fastboot_devices.append({"serial": parts[0], "mode": parts[1]})

    heimdall_detected = False
    if shutil.which("heimdall"):
        rc, _, _ = _run_cmd(["heimdall", "detect"], timeout=4)
        heimdall_detected = rc == 0

    props: dict[str, str] = {}
    active_serial = ""
    adb_state = "disconnected"
    if adb_devices:
        active_serial = adb_devices[0]["serial"]
        adb_state = adb_devices[0]["state"]
        if adb_state == "device":
            for prop in [
                "ro.product.model",
                "ro.product.device",
                "ro.product.manufacturer",
                "ro.boot.hardware",
                "ro.build.version.release",
                "ro.build.version.security_patch",
                "ro.boot.flash.locked",
                "sys.oem_unlock_allowed",
                "gsm.version.baseband",
            ]:
                rc, val, _ = _run_cmd(["adb", "-s", active_serial, "shell", "getprop", prop], timeout=4)
                if rc == 0 and val:
                    props[prop] = val.strip()

    model_str = props.get("ro.product.model") or host_probe.get("wpd_model") or ""
    profile = classify_galaxy_s10_model(model_str)

    win_devs = host_probe.get("windows_usbipd_devices", [])
    usbipd_hint = None
    if win_devs and not adb_devices and not heimdall_detected:
        busid = win_devs[0].get("busid", "3-1")
        usbipd_hint = (
            f"usbipd bind --busid {busid} && usbipd attach --wsl --busid {busid} --auto-attach"
        )

    # Extract USB hardware serial from Windows PnP if not yet attached via usbipd
    win_usb_serial = None
    for pnp in host_probe.get("windows_pnp_devices", []):
        inst = pnp.get("InstanceId", "")
        if "VID_04E8&PID_6860\\" in inst:
            win_usb_serial = inst.split("VID_04E8&PID_6860\\", 1)[1]
            break

    return {
        "probed_at": datetime.now(timezone.utc).isoformat(),
        "adb_connected": adb_state == "device",
        "adb_state": adb_state,
        "serial": active_serial or win_usb_serial or host_probe.get("wpd_serial_number"),
        "wpd_model": host_probe.get("wpd_model"),
        "wpd_firmware_version": host_probe.get("wpd_firmware_version"),
        "adb_devices": adb_devices,
        "fastboot_devices": fastboot_devices,
        "heimdall_download_mode_detected": heimdall_detected,
        "linux_usb_devices": host_probe.get("linux_usb_devices", []),
        "windows_usbipd_devices": win_devs,
        "windows_usbipd_attach_command": usbipd_hint,
        "device_properties": props,
        "classified_profile": profile,
        "onboarding_steps": [
            "1. On the Galaxy S10, open Settings -> About Phone -> Software Information and tap 'Build number' 7 times.",
            "2. Open Settings -> Developer Options and enable 'USB debugging' (and 'OEM unlocking' if visible on F/N/0 models).",
            "3. Plug in USB-C cable (if on Windows WSL2, attach via usbipd) and tap 'Allow USB debugging' on the phone screen.",
            "4. Run `make probe` to verify your exact S10 model, then `make debloat` and `make harden`.",
        ],
    }


def run_debloat(
    categories: list[str] | None = None,
    packages: list[str] | None = None,
    execute: bool = False,
) -> dict[str, Any]:
    all_pkgs = db.list_packages()
    pkg_map = {p["package_name"]: p for p in all_pkgs}
    protected_set = {p["package_name"] for p in all_pkgs if p["is_protected"] == 1}

    blocked_protected: list[str] = []
    target_rows: list[dict[str, Any]] = []

    if packages:
        for pkg_name in packages:
            if pkg_name in protected_set:
                blocked_protected.append(pkg_name)
                continue
            row = pkg_map.get(
                pkg_name,
                {
                    "package_name": pkg_name,
                    "label": pkg_name,
                    "category": "custom",
                    "risk_level": "MEDIUM",
                    "is_protected": 0,
                    "recommended_action": "remove",
                    "description": "User-specified package",
                },
            )
            target_rows.append(row)
    else:
        target_cats = set(
            categories
            or ["telemetry_spyware", "facebook_preinstalled", "samsung_bloat", "carrier_bloat"]
        )
        for row in all_pkgs:
            if row["is_protected"] == 1:
                if row["category"] in target_cats:
                    blocked_protected.append(row["package_name"])
                continue
            if row["category"] in target_cats and row["recommended_action"] == "remove":
                target_rows.append(row)

    probe = probe_connected_device()
    adb_ready = probe["adb_connected"]
    serial = probe.get("serial") or "dry-run-device"

    results: list[dict[str, Any]] = []
    for row in target_rows:
        pkg = row["package_name"]
        cmds = [
            f"adb shell cmd appops set {pkg} RUN_IN_BACKGROUND ignore",
            f"adb shell pm disable-user --user 0 {pkg}",
            f"adb shell pm uninstall -k --user 0 {pkg}",
        ]
        if not execute or not adb_ready:
            results.append(
                {
                    "package_name": pkg,
                    "label": row["label"],
                    "category": row["category"],
                    "status": "dry_run_planned",
                    "commands": cmds,
                }
            )
            continue

        # Execute live over ADB
        _run_cmd(["adb", "-s", serial, "shell", "cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", "ignore"])
        _run_cmd(["adb", "-s", serial, "shell", "pm", "disable-user", "--user 0", pkg])
        rc, out, err = _run_cmd(["adb", "-s", serial, "shell", "pm", "uninstall", "-k", "--user", "0", pkg])
        combined = (out or err or "Success").strip()
        status = "removed" if "Success" in combined else ("not_installed" if "not installed" in combined else "processed")
        db.record_debloat_action(
            device_serial=serial,
            package_name=pkg,
            action="uninstall_user_0",
            status=status,
            output=combined,
        )
        results.append(
            {
                "package_name": pkg,
                "label": row["label"],
                "category": row["category"],
                "status": status,
                "adb_output": combined,
            }
        )

    return {
        "mode": "executed" if (execute and adb_ready) else "dry_run",
        "adb_connected": adb_ready,
        "device_serial": serial,
        "target_count": len(results),
        "protected_guardlist_enforced": True,
        "protected_packages_preserved": sorted(protected_set),
        "blocked_protected_requests": blocked_protected,
        "results": results,
    }


def restore_package(package_name: str, execute: bool = False) -> dict[str, Any]:
    probe = probe_connected_device()
    adb_ready = probe["adb_connected"]
    serial = probe.get("serial") or "dry-run-device"
    cmds = [
        f"adb shell cmd package install-existing {package_name}",
        f"adb shell pm enable --user 0 {package_name}",
        f"adb shell cmd appops set {package_name} RUN_IN_BACKGROUND allow",
    ]
    if not execute or not adb_ready:
        return {
            "mode": "dry_run",
            "adb_connected": adb_ready,
            "package_name": package_name,
            "commands": cmds,
        }

    rc1, out1, err1 = _run_cmd(["adb", "-s", serial, "shell", "cmd", "package", "install-existing", package_name])
    _run_cmd(["adb", "-s", serial, "shell", "pm", "enable", "--user", "0", package_name])
    _run_cmd(["adb", "-s", serial, "shell", "cmd", "appops", "set", package_name, "RUN_IN_BACKGROUND", "allow"])
    combined = (out1 or err1 or "Restored").strip()
    status = "restored" if rc1 == 0 else "error"
    db.record_debloat_action(
        device_serial=serial,
        package_name=package_name,
        action="restore_package",
        status=status,
        output=combined,
    )
    return {
        "mode": "executed",
        "adb_connected": True,
        "package_name": package_name,
        "status": status,
        "adb_output": combined,
    }


def apply_privacy_hardening(
    dns_provider: str = "adguard", execute: bool = False
) -> dict[str, Any]:
    settings_list = db.list_privacy_settings()
    dns_info = DNS_PROVIDERS.get(dns_provider, DNS_PROVIDERS["adguard"])
    dns_hostname = dns_info["hostname"]

    probe = probe_connected_device()
    adb_ready = probe["adb_connected"]
    serial = probe.get("serial") or "dry-run-device"

    applied: list[dict[str, Any]] = []
    for item in settings_list:
        ns = item["namespace"]
        key = item["setting_key"]
        val = dns_hostname if key == "private_dns_specifier" else item["hardened_value"]
        cmd_str = f"adb shell settings put {ns} {key} {val}"

        if not execute or not adb_ready:
            applied.append(
                {
                    "namespace": ns,
                    "setting_key": key,
                    "hardened_value": val,
                    "title": item["title"],
                    "status": "dry_run_planned",
                    "command": cmd_str,
                }
            )
            continue

        rc, out, err = _run_cmd(["adb", "-s", serial, "shell", "settings", "put", ns, key, val])
        applied.append(
            {
                "namespace": ns,
                "setting_key": key,
                "hardened_value": val,
                "title": item["title"],
                "status": "applied" if rc == 0 else "error",
                "adb_output": (out or err or "ok").strip(),
            }
        )

    return {
        "mode": "executed" if (execute and adb_ready) else "dry_run",
        "adb_connected": adb_ready,
        "device_serial": serial,
        "dns_provider": dns_provider,
        "dns_hostname": dns_hostname,
        "settings_count": len(applied),
        "directives": applied,
    }


def generate_vbmeta_disabled_image(output_path: Path | None = None) -> dict[str, Any]:
    out = output_path or (db.DATA_DIR / "images" / "vbmeta_disabled.img")
    out.parent.mkdir(parents=True, exist_ok=True)

    avbtool_bin = shutil.which("avbtool")
    if not avbtool_bin:
        # Run local fallback installer or directly invoke script
        installer = Path(__file__).resolve().parent.parent / "scripts" / "install_avbtool.py"
        if installer.exists() and os.access("/usr/local/bin", os.W_OK):
            subprocess.run(["python3", str(installer)], capture_output=True)
            avbtool_bin = shutil.which("avbtool")

    if avbtool_bin:
        rc, _, err = _run_cmd(
            [
                avbtool_bin,
                "make_vbmeta_image",
                "--flags",
                "3",
                "--padding_size",
                "4096",
                "--output",
                str(out),
            ]
        )
        if rc != 0:
            raise RuntimeError(f"avbtool make_vbmeta_image failed: {err}")
        _, info_out, _ = _run_cmd([avbtool_bin, "info_image", "--image", str(out)])
    else:
        # Direct binary-exact AVB0 generation if running outside container without root /usr/local/bin
        import struct

        release_padded = b"avbtool 1.2.0 android-privacy-studio\x00".ljust(48, b"\x00")[:48]
        header = struct.pack(
            ">4sIIQQI11QII48s80s",
            b"AVB0",
            1,
            2,
            0,
            0,
            0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            3,  # flags=3 (disable verity + disable verification)
            0,
            release_padded,
            b"\x00" * 80,
        )
        out.write_bytes(header + (b"\x00" * (4096 - len(header))))
        info_out = "Minimum libavb version: 1.2 | Flags: 3 (dm-verity=DISABLED)"

    raw_bytes = out.read_bytes()
    sha256 = hashlib.sha256(raw_bytes).hexdigest()
    details = {
        "avb_magic": raw_bytes[:4].decode("ascii", errors="replace"),
        "flags": 3,
        "dm_verity_disabled": True,
        "verification_disabled": True,
        "info": info_out,
        "flash_commands": {
            "fastboot": f"fastboot --disable-verity --disable-verification flash vbmeta {out}",
            "heimdall_s10": f"heimdall flash --VBMETA {out}",
        },
    }
    db.record_image_build(
        artifact_type="vbmeta_disabled",
        artifact_path=str(out),
        sha256=sha256,
        size_bytes=len(raw_bytes),
        details_json=json.dumps(details),
    )
    return {
        "ok": True,
        "artifact_path": str(out),
        "size_bytes": len(raw_bytes),
        "sha256": sha256,
        **details,
    }


def generate_custom_os_privacy_overlay(output_dir: Path | None = None) -> dict[str, Any]:
    overlay_dir = output_dir or (db.DATA_DIR / "images" / "privacy_overlay")
    etc_dir = overlay_dir / "system" / "etc"
    init_dir = etc_dir / "init"
    init_dir.mkdir(parents=True, exist_ok=True)

    # 1. Generate /system/etc/hosts tracker blackhole (preserving Google Play Store domains)
    hosts_lines = [
        "# ======================================================================",
        "# Android Custom OS Privacy Studio - System Hosts Tracker Blackhole",
        "# Blocks Samsung, Meta, Carrier IQ, and in-app analytics SDKs.",
        "# Explicitly preserves Google Play Store (play.fe.googleapis.com, gvt1.com, fcm.googleapis.com).",
        "# ======================================================================",
        "127.0.0.1       localhost",
        "::1             ip6-localhost",
        "",
    ]
    for dom in TRACKER_BLACKHOLE_DOMAINS:
        clean_dom = dom.strip()
        if clean_dom:
            hosts_lines.append(f"0.0.0.0 {clean_dom}")
            hosts_lines.append(f"::0 {clean_dom}")

    hosts_path = etc_dir / "hosts"
    hosts_path.write_text("\n".join(hosts_lines) + "\n", encoding="utf-8")

    # 2. Generate /system/etc/init/99-privacy-hardening.rc
    init_rc_content = """# Android Custom OS Privacy Hardening Init RC
on property:sys.boot_completed=1
    exec - system system -- /system/bin/settings put global captive_portal_mode 1
    exec - system system -- /system/bin/settings put global captive_portal_http_url http://connectivitycheck.grapheneos.network/generate_204
    exec - system system -- /system/bin/settings put global captive_portal_https_url https://connectivitycheck.grapheneos.network/generate_204
    exec - system system -- /system/bin/settings put global captive_portal_fallback_url http://captiveportal.kuketz.de
    exec - system system -- /system/bin/settings put global wifi_scan_always_enabled 0
    exec - system system -- /system/bin/settings put global ble_scan_always_enabled 0
    exec - system system -- /system/bin/settings put global wifi_wakeup_enabled 0
    exec - system system -- /system/bin/settings put global network_recommendations_enabled 0
    exec - system system -- /system/bin/settings put secure send_action_app_error 0
"""
    init_rc_path = init_dir / "99-privacy-hardening.rc"
    init_rc_path.write_text(init_rc_content, encoding="utf-8")

    # 3. Generate build.prop privacy overlay
    build_prop_content = """# Custom OS Privacy Studio - build.prop Hardening Overlay
ro.config.nocheckin=1
ro.SetupWizard.enterprise_mode=1
ro.com.google.networklocation=0
 persist.sys.usb.config=mtp,adb
"""
    build_prop_path = overlay_dir / "system" / "build.prop.overlay"
    build_prop_path.write_text(build_prop_content, encoding="utf-8")

    # 4. Generate LineageOS 21/22 Galaxy Note 10+ 5G (d2x/d2s/d1) & S10 (beyond0lte/beyond1lte/beyond2lte) roomservice.xml
    lineage_manifest = """<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <!-- Samsung Galaxy Note 10 / Note 10+ / Note 10+ 5G (Exynos 9825: d1 / d2s / d2x) & S10 (Exynos 9820: beyond*) Official LineageOS Trees -->
  <project name="LineageOS/android_device_samsung_d2x" path="device/samsung/d2x" remote="github" revision="lineage-21.0" />
  <project name="LineageOS/android_device_samsung_d2s" path="device/samsung/d2s" remote="github" revision="lineage-21.0" />
  <project name="LineageOS/android_device_samsung_d1" path="device/samsung/d1" remote="github" revision="lineage-21.0" />
  <project name="LineageOS/android_device_samsung_beyond0lte" path="device/samsung/beyond0lte" remote="github" revision="lineage-21.0" />
  <project name="LineageOS/android_device_samsung_beyond1lte" path="device/samsung/beyond1lte" remote="github" revision="lineage-21.0" />
  <project name="LineageOS/android_device_samsung_beyond2lte" path="device/samsung/beyond2lte" remote="github" revision="lineage-21.0" />
  <project name="LineageOS/android_device_samsung_exynos9820-common" path="device/samsung/exynos9820-common" remote="github" revision="lineage-21.0" />
  <project name="LineageOS/android_kernel_samsung_exynos9820" path="kernel/samsung/exynos9820" remote="github" revision="lineage-21.0" />
  <project name="LineageOS/android_hardware_samsung" path="hardware/samsung" remote="github" revision="lineage-21.0" />
  <!-- Proprietary Vendor Blobs (Camera, GPU Mali-G76, RIL, Wi-Fi/BT Firmware) -->
  <project name="TheMuppets/proprietary_vendor_samsung_d2x" path="vendor/samsung/d2x" remote="github" revision="lineage-21.0" />
  <project name="TheMuppets/proprietary_vendor_samsung_d2s" path="vendor/samsung/d2s" remote="github" revision="lineage-21.0" />
  <project name="TheMuppets/proprietary_vendor_samsung_d1" path="vendor/samsung/d1" remote="github" revision="lineage-21.0" />
  <project name="TheMuppets/proprietary_vendor_samsung_beyond0lte" path="vendor/samsung/beyond0lte" remote="github" revision="lineage-21.0" />
  <project name="TheMuppets/proprietary_vendor_samsung_beyond1lte" path="vendor/samsung/beyond1lte" remote="github" revision="lineage-21.0" />
  <project name="TheMuppets/proprietary_vendor_samsung_beyond2lte" path="vendor/samsung/beyond2lte" remote="github" revision="lineage-21.0" />
  <project name="TheMuppets/proprietary_vendor_samsung_exynos9820-common" path="vendor/samsung/exynos9820-common" remote="github" revision="lineage-21.0" />
</manifest>
"""
    manifest_path = overlay_dir / "lineageos_s10_roomservice.xml"
    manifest_path.write_text(lineage_manifest, encoding="utf-8")

    # 5. Generate executable GSI / System Image Patcher Script
    patch_script = """#!/usr/bin/env bash
set -euo pipefail
# Dockerized Android GSI / System Image De-Bloater & Privacy Overlay Injector
# Usage: ./patch_gsi_image.sh <input-system.img> <output-system-hardened.img>

INPUT_IMG="${1:-}"
OUTPUT_IMG="${2:-system-privacy-hardened.img}"
OVERLAY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "$INPUT_IMG" || ! -f "$INPUT_IMG" ]]; then
    echo "Usage: $0 <input-system.img> [output-system-hardened.img]"
    exit 1
fi

WORK_RAW="/tmp/system_raw_$$.img"
MNT_DIR="/tmp/system_mnt_$$"
mkdir -p "$MNT_DIR"

FILE_TYPE="$(file -b "$INPUT_IMG")"
if [[ "$FILE_TYPE" == *"Android sparse image"* ]]; then
    echo "[gsi-patcher] Converting sparse image to raw ext4..."
    simg2img "$INPUT_IMG" "$WORK_RAW"
else
    cp "$INPUT_IMG" "$WORK_RAW"
fi

echo "[gsi-patcher] Expanding image headroom (+64M) & mounting..."
e2fsck -fy "$WORK_RAW" || true
resize2fs "$WORK_RAW" +64M
mount -o loop,rw "$WORK_RAW" "$MNT_DIR"

SYS_ROOT="$MNT_DIR/system"
[[ ! -d "$SYS_ROOT" ]] && SYS_ROOT="$MNT_DIR"

echo "[gsi-patcher] Injecting zero-telemetry /etc/hosts & init.rc privacy overlay..."
cp "$OVERLAY_DIR/system/etc/hosts" "$SYS_ROOT/etc/hosts"
chmod 0644 "$SYS_ROOT/etc/hosts"
mkdir -p "$SYS_ROOT/etc/init"
cp "$OVERLAY_DIR/system/etc/init/99-privacy-hardening.rc" "$SYS_ROOT/etc/init/99-privacy-hardening.rc"
chmod 0644 "$SYS_ROOT/etc/init/99-privacy-hardening.rc"

umount "$MNT_DIR"
rmdir "$MNT_DIR"
e2fsck -fy "$WORK_RAW" || true
img2simg "$WORK_RAW" "$OUTPUT_IMG"
rm -f "$WORK_RAW"
echo "[gsi-patcher] Successfully built hardened sparse GSI: $OUTPUT_IMG"
"""
    patch_script_path = overlay_dir / "patch_gsi_image.sh"
    patch_script_path.write_text(patch_script, encoding="utf-8")
    patch_script_path.chmod(0o755)

    hosts_sha256 = hashlib.sha256(hosts_path.read_bytes()).hexdigest()
    details = {
        "hosts_path": str(hosts_path),
        "blocked_tracker_domains": len(TRACKER_BLACKHOLE_DOMAINS),
        "init_rc_path": str(init_rc_path),
        "build_prop_path": str(build_prop_path),
        "lineage_manifest_path": str(manifest_path),
        "gsi_patch_script": str(patch_script_path),
        "play_store_whitelisted": True,
    }
    db.record_image_build(
        artifact_type="privacy_overlay_bundle",
        artifact_path=str(overlay_dir),
        sha256=hosts_sha256,
        size_bytes=hosts_path.stat().st_size,
        details_json=json.dumps(details),
    )
    return {
        "ok": True,
        "overlay_dir": str(overlay_dir),
        "sha256": hosts_sha256,
        **details,
    }


def check_docker_toolchain() -> dict[str, bool]:
    tools = [
        "adb",
        "fastboot",
        "heimdall",
        "simg2img",
        "img2simg",
        "mkfs.erofs",
        "avbtool",
        "repo",
        "sqlite3",
    ]
    return {t: bool(shutil.which(t)) for t in tools}


def wipe_user_apps(execute: bool = False) -> dict[str, Any]:
    """
    Lists and removes all third-party user-installed packages (`adb shell pm list packages -3 --user 0`),
    except protected packages or newly provisioned open-source privacy bundle apps.
    """
    probe = probe_connected_device()
    adb_ready = probe["adb_connected"]
    serial = probe.get("serial") or "dry-run-device"

    keep_pkgs = {
        p["package_name"] for p in db.list_packages() if p["is_protected"] == 1
    } | {b["package_name"] for b in PRIVACY_APP_BUNDLE}

    user_pkgs: list[str] = []
    if adb_ready:
        rc, out, _ = _run_cmd(["adb", "-s", serial, "shell", "pm", "list", "packages", "-3", "--user", "0"], timeout=10)
        if rc == 0:
            for line in out.splitlines():
                line = line.strip()
                if line.startswith("package:"):
                    pkg = line.split("package:", 1)[1].strip()
                    if pkg and pkg not in keep_pkgs:
                        user_pkgs.append(pkg)

    results: list[dict[str, Any]] = []
    for pkg in sorted(user_pkgs):
        if not execute or not adb_ready:
            results.append({"package_name": pkg, "status": "dry_run_planned"})
            continue

        rc, out, err = _run_cmd(["adb", "-s", serial, "shell", "pm", "uninstall", pkg], timeout=10)
        combined = (out or err or "").strip()
        if "Success" not in combined:
            rc, out, err = _run_cmd(
                ["adb", "-s", serial, "shell", "pm", "uninstall", "-k", "--user", "0", pkg],
                timeout=10,
            )
            combined = (out or err or "").strip()
        status = "removed" if "Success" in combined else "processed"
        db.record_debloat_action(
            device_serial=serial,
            package_name=pkg,
            action="wipe_user_app",
            status=status,
            output=combined,
        )
        results.append({"package_name": pkg, "status": status, "adb_output": combined})

    return {
        "mode": "executed" if (execute and adb_ready) else "dry_run",
        "adb_connected": adb_ready,
        "device_serial": serial,
        "user_app_count": len(results),
        "results": results,
    }


def provision_privacy_bundle(execute: bool = False) -> dict[str, Any]:
    """
    Downloads and installs open-source zero-telemetry privacy essentials from official GitHub Releases:
    - Fossify Launcher (`org.fossify.home` from `FossifyOrg/Launcher`)
    - Fossify File Manager (`org.fossify.filemanager` from `FossifyOrg/File-Manager`)
    - RethinkDNS + Firewall (`com.celzero.bravedns` from `celzero/rethink-app`)
    """
    import urllib.request

    probe = probe_connected_device()
    adb_ready = probe["adb_connected"]
    serial = probe.get("serial") or "dry-run-device"

    apk_dir = db.DATA_DIR / "apks"
    apk_dir.mkdir(parents=True, exist_ok=True)

    targets = [
        {
            "id": "fossify_launcher",
            "package_name": "org.fossify.home",
            "repo": "FossifyOrg/Launcher",
            "asset_filter": ".apk",
        },
        {
            "id": "fossify_file_manager",
            "package_name": "org.fossify.filemanager",
            "repo": "FossifyOrg/File-Manager",
            "asset_filter": ".apk",
        },
        {
            "id": "rethinkdns",
            "package_name": "com.celzero.bravedns",
            "repo": "celzero/rethink-app",
            "asset_filter": ".apk",
        },
    ]

    provisioned: list[dict[str, Any]] = []
    for t in targets:
        api_url = f"https://api.github.com/repos/{t['repo']}/releases/latest"
        if not execute:
            provisioned.append(
                {
                    "id": t["id"],
                    "package_name": t["package_name"],
                    "release_api": api_url,
                    "status": "dry_run_planned",
                }
            )
            continue

        try:
            req = urllib.request.Request(api_url, headers={"User-Agent": "android-privacy-studio/1.0"})
            with urllib.request.urlopen(req, timeout=15) as resp:
                rel = json.loads(resp.read().decode("utf-8"))
            assets = rel.get("assets", [])
            chosen = None
            for a in assets:
                name = a.get("name", "")
                if name.endswith(".apk") and ("fdroid" in name.lower() or "release" in name.lower() or "universal" in name.lower() or "arm64" in name.lower()):
                    chosen = a
                    break
            if not chosen and assets:
                for a in assets:
                    if a.get("name", "").endswith(".apk"):
                        chosen = a
                        break
            if not chosen:
                provisioned.append({"id": t["id"], "status": "no_apk_asset_found"})
                continue

            dl_url = chosen["browser_download_url"]
            apk_path = apk_dir / chosen["name"]
            if not apk_path.exists() or apk_path.stat().st_size < 10000:
                subprocess.run(["curl", "-fsSL", "-L", dl_url, "-o", str(apk_path)], check=True, timeout=60)

            sha256 = hashlib.sha256(apk_path.read_bytes()).hexdigest()
            install_status = "downloaded_only"
            install_out = ""
            if adb_ready:
                _run_cmd(
                    ["adb", "-s", serial, "shell", "settings", "put", "global", "verifier_verify_adb_installs", "0"],
                    timeout=5,
                )
                rc, out, err = _run_cmd(
                    ["adb", "-s", serial, "install", "-r", "--no-streaming", str(apk_path)],
                    timeout=90,
                )
                install_out = (out or err or "").strip()
                install_status = "installed" if rc == 0 and "Success" in install_out else "install_failed"

            provisioned.append(
                {
                    "id": t["id"],
                    "package_name": t["package_name"],
                    "apk_name": chosen["name"],
                    "apk_path": str(apk_path),
                    "sha256": sha256,
                    "status": install_status,
                    "adb_output": install_out,
                }
            )
        except Exception as exc:
            provisioned.append({"id": t["id"], "package_name": t["package_name"], "status": "error", "error": str(exc)})

    return {
        "mode": "executed" if execute else "dry_run",
        "adb_connected": adb_ready,
        "device_serial": serial,
        "apps": provisioned,
    }

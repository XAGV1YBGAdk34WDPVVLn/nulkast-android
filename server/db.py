"""
SQLite WAL Database & Seed Catalog for Android Custom OS & Privacy Studio (`server/db.py`).
"""
from __future__ import annotations

import json
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

DATA_DIR = Path(os.environ.get("DATA_DIR", Path(__file__).resolve().parent.parent / "data"))
DB_PATH = DATA_DIR / "android_studio.db"


def get_conn() -> sqlite3.Connection:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_PATH), timeout=15.0)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA synchronous=NORMAL;")
    conn.execute("PRAGMA foreign_keys=ON;")
    return conn


SEED_DEVICE_MATRIX: list[dict[str, Any]] = [
    {
        "model_prefix": "SM-G970F",
        "retail_name": "Samsung Galaxy S10e (Global / Exynos)",
        "codename": "beyond0lte",
        "soc": "Exynos 9820 (8nm)",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (LineageOS 21/22, Custom GSI, TWRP)",
        "volte_notes": "Stock OneUI retains Carrier VoLTE; pure AOSP/LineageOS supports LTE data + SMS + VoIP (Samsung IMS is proprietary).",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C -> Vol Up (short=Download, 7s long=OEM Unlock)",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power until Samsung logo",
    },
    {
        "model_prefix": "SM-G970U",
        "retail_name": "Samsung Galaxy S10e (US / North America)",
        "codename": "beyond0qlte",
        "soc": "Snapdragon 855 (7nm)",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat, Zero-Log Captive Portal, DoT Tracker Blackhole, RethinkDNS Firewall)",
        "volte_notes": "100% Carrier VoLTE & Wi-Fi Calling preserved via Critical Guardlist (com.sec.imsservice + com.sec.epdg).",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power",
    },
    {
        "model_prefix": "SM-G973F",
        "retail_name": "Samsung Galaxy S10 (Global / Exynos)",
        "codename": "beyond1lte",
        "soc": "Exynos 9820 (8nm)",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (Official LineageOS 21/22 beyond1lte, Custom GSI, TWRP)",
        "volte_notes": "Stock OneUI retains Carrier VoLTE; pure AOSP/LineageOS supports LTE data + SMS + VoIP.",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C -> Vol Up (short=Download, 7s long=OEM Unlock)",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power until Samsung logo",
    },
    {
        "model_prefix": "SM-G973U",
        "retail_name": "Samsung Galaxy S10 (US / North America)",
        "codename": "beyond1qlte",
        "soc": "Snapdragon 855 (7nm)",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat, Zero-Log Captive Portal, DoT Tracker Blackhole, RethinkDNS Firewall)",
        "volte_notes": "100% Carrier VoLTE & Wi-Fi Calling preserved via Critical Guardlist (com.sec.imsservice + com.sec.epdg).",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power",
    },
    {
        "model_prefix": "SM-G973U1",
        "retail_name": "Samsung Galaxy S10 (US Factory Unlocked)",
        "codename": "beyond1qlte",
        "soc": "Snapdragon 855 (7nm)",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat, Zero-Log Captive Portal, DoT Tracker Blackhole, RethinkDNS Firewall)",
        "volte_notes": "100% Carrier VoLTE & Wi-Fi Calling preserved without carrier bloatware.",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power",
    },
    {
        "model_prefix": "SM-G9730",
        "retail_name": "Samsung Galaxy S10 (HK / LatAm Snapdragon Unlockable)",
        "codename": "beyond1qlte",
        "soc": "Snapdragon 855 (7nm)",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (Project Treble GSI, TWRP)",
        "volte_notes": "Unlockable Snapdragon variant (TGY/CHC).",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C -> Long-press Vol Up for OEM Unlock",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power",
    },
    {
        "model_prefix": "SM-G975F",
        "retail_name": "Samsung Galaxy S10+ (Global / Exynos)",
        "codename": "beyond2lte",
        "soc": "Exynos 9820 (8nm)",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (Official LineageOS 21/22 beyond2lte, Custom GSI, TWRP)",
        "volte_notes": "Stock OneUI retains Carrier VoLTE; pure AOSP/LineageOS supports LTE data + SMS + VoIP.",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C -> Vol Up (short=Download, 7s long=OEM Unlock)",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power until Samsung logo",
    },
    {
        "model_prefix": "SM-G975U",
        "retail_name": "Samsung Galaxy S10+ (US / North America)",
        "codename": "beyond2qlte",
        "soc": "Snapdragon 855 (7nm)",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat, Zero-Log Captive Portal, DoT Tracker Blackhole, RethinkDNS Firewall)",
        "volte_notes": "100% Carrier VoLTE & Wi-Fi Calling preserved via Critical Guardlist.",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power",
    },
    {
        "model_prefix": "SM-G977B",
        "retail_name": "Samsung Galaxy S10 5G (Global / Exynos)",
        "codename": "beyondx",
        "soc": "Exynos 9820 (8nm)",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (Official LineageOS beyondx, Custom GSI)",
        "volte_notes": "Unlockable 5G Exynos variant.",
        "download_combo": "Power Off -> Hold Vol Down + Bixby while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Vol Up + Bixby + Power",
    },
    {
        "model_prefix": "SM-N976B",
        "retail_name": "Samsung Galaxy Note 10+ 5G (Global / EU / UK Exynos)",
        "codename": "d2x",
        "soc": "Exynos 9825 (7nm EUV) + Exynos Modem 5100",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (Official LineageOS 21/22 d2x, Custom GSI, TWRP)",
        "volte_notes": "Stock OneUI retains Carrier VoLTE & S-Pen Air Command; pure LineageOS d2x supports LTE/5G data, SMS, VoIP & passive S-Pen stylus.",
        "download_combo": "Power Off -> Hold Volume Up + Volume Down simultaneously while plugging in USB-C -> Vol Up (short=Download, 7s long=OEM Unlock)",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key until Note 10+ logo",
    },
    {
        "model_prefix": "SM-N976U",
        "retail_name": "Samsung Galaxy Note 10+ 5G (US AT&T / T-Mobile Carrier)",
        "codename": "d2xq",
        "soc": "Snapdragon 855 + Qualcomm X50/X55 5G",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat, Zero-Log Captive Portal, DoT Tracker Blackhole, RethinkDNS Firewall)",
        "volte_notes": "100% Carrier VoLTE, 5G, Google Play Store & S-Pen Air Command preserved via Hard Safety Guardlist.",
        "download_combo": "Power Off -> Hold Volume Up + Volume Down simultaneously while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key",
    },
    {
        "model_prefix": "SM-N976U1",
        "retail_name": "Samsung Galaxy Note 10+ 5G (US Factory Unlocked)",
        "codename": "d2xque",
        "soc": "Snapdragon 855 + Qualcomm X50/X55 5G",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat, Zero-Log Captive Portal, DoT Tracker Blackhole, RethinkDNS Firewall)",
        "volte_notes": "100% Carrier VoLTE, 5G, Google Play Store & S-Pen Air Command preserved via Hard Safety Guardlist.",
        "download_combo": "Power Off -> Hold Volume Up + Volume Down simultaneously while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key",
    },
    {
        "model_prefix": "SM-N976V",
        "retail_name": "Samsung Galaxy Note 10+ 5G (US Verizon)",
        "codename": "d2xq",
        "soc": "Snapdragon 855 + Qualcomm X50 5G",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat, Zero-Log Captive Portal, DoT Tracker Blackhole, RethinkDNS Firewall)",
        "volte_notes": "100% Verizon VoLTE, 5G, Google Play Store & S-Pen Air Command preserved while stripping DT Ignite/MyVerizon bloat.",
        "download_combo": "Power Off -> Hold Volume Up + Volume Down simultaneously while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key",
    },
    {
        "model_prefix": "SM-N9760",
        "retail_name": "Samsung Galaxy Note 10+ 5G (HK / China Unlockable Snapdragon)",
        "codename": "d2xq",
        "soc": "Snapdragon 855 + Qualcomm X50/X55 5G",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (Project Treble ARM64 A/B GSI + TWRP)",
        "volte_notes": "Unlockable Snapdragon Note 10+ 5G variant.",
        "download_combo": "Power Off -> Hold Volume Up + Volume Down while plugging in USB-C -> Long-press Vol Up for OEM Unlock",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key",
    },
    {
        "model_prefix": "SM-N975F",
        "retail_name": "Samsung Galaxy Note 10+ 4G (Global / Exynos)",
        "codename": "d2s",
        "soc": "Exynos 9825 (7nm EUV)",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (ADB Hardening) + Track 2 (Official LineageOS 21/22 d2s, Custom GSI)",
        "volte_notes": "Unlockable Global Exynos Note 10+.",
        "download_combo": "Power Off -> Hold Volume Up + Volume Down while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key",
    },
    {
        "model_prefix": "SM-N975U",
        "retail_name": "Samsung Galaxy Note 10+ 4G (US Carrier / Unlocked)",
        "codename": "d2sq",
        "soc": "Snapdragon 855 (7nm)",
        "bootloader_unlockable": 0,
        "supported_tracks": "Track 1 (Surgical USB ADB De-Bloat & OS Privacy Hardening)",
        "volte_notes": "100% VoLTE, Play Store & S-Pen Air Command preserved.",
        "download_combo": "Power Off -> Hold Volume Up + Volume Down while plugging in USB-C",
        "recovery_combo": "Power Off -> Plug in USB-C -> Hold Volume Up + Side/Power Key",
    },
    {
        "model_prefix": "PIXEL-GENERIC",
        "retail_name": "Google Pixel (6a / 7 / 7a / 8 / 8a / 9 — Factory Unlocked)",
        "codename": "generic_pixel",
        "soc": "Google Tensor G1–G4",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 + Track 2 (Full Custom AOSP / GrapheneOS / LineageOS + Custom AVB Key Re-Locking + Full Carrier VoLTE)",
        "volte_notes": "100% Carrier VoLTE, 5G, and Sandboxed Google Play supported on custom OS builds.",
        "download_combo": "Power Off -> Hold Vol Down + Power (Fastboot Mode)",
        "recovery_combo": "From Fastboot -> Select Recovery Mode with Volume keys -> Press Power",
    },
    {
        "model_prefix": "PIXEL-FOLD",
        "retail_name": "Google Pixel Fold / 9 Pro Fold (felix / comet)",
        "codename": "felix",
        "soc": "Google Tensor G2 / G4 + Titan M2",
        "bootloader_unlockable": 1,
        "supported_tracks": "Track 1 (Aegis Apps w/ Dual-Pane Foldable UI) + Track 2 (Full Custom AOSP / GrapheneOS / LineageOS + Custom AVB Key Re-Lock + Full VoLTE/5G)",
        "volte_notes": "Gold-standard custom OS hardware: supports custom AVB key bootloader re-locking, Sandboxed Google Play, 100% Carrier VoLTE/5G, and dual-screen posture APIs.",
        "download_combo": "Power Off -> Hold Volume Down + Power until Fastboot Mode appears",
        "recovery_combo": "From Fastboot Mode -> Use Volume keys to select Recovery Mode -> Press Power",
    },
]


SEED_PACKAGES: list[dict[str, Any]] = [
    # --- Category 1: telemetry_spyware ---
    {
        "package_name": "com.samsung.android.rubin.app",
        "label": "Samsung Customization Service (Rubin)",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Continuously profiles location history, app usage habits, and behavioral context for Samsung targeted advertising.",
    },
    {
        "package_name": "com.sec.android.diagmonagent",
        "label": "Samsung Diagnostic Monitoring Agent",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Collects device diagnostics, crash dumps, and usage metrics and uploads them to Samsung telemetry endpoints.",
    },
    {
        "package_name": "com.samsung.android.dqagent",
        "label": "Samsung Device Quality Agent",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Background hardware and software quality telemetry reporter.",
    },
    {
        "package_name": "com.samsung.android.securitylogagent",
        "label": "Samsung Security Log Agent",
        "category": "telemetry_spyware",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Knox security audit logger and notification agent.",
    },
    {
        "package_name": "com.samsung.android.app.omcagent",
        "label": "Open Market Customization (OMC) Agent",
        "category": "telemetry_spyware",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Regional/carrier remote configuration and silent app push agent.",
    },
    {
        "package_name": "com.samsung.android.sdm.config",
        "label": "Samsung Device Management Config",
        "category": "telemetry_spyware",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Remote configuration and telemetry polling daemon.",
    },
    {
        "package_name": "com.samsung.android.svcagent",
        "label": "Samsung SVC Telemetry Agent",
        "category": "telemetry_spyware",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Background service telemetry agent.",
    },
    {
        "package_name": "com.sec.android.soagent",
        "label": "Samsung Software Update Telemetry Agent",
        "category": "telemetry_spyware",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Polls Samsung servers with IMEI and serial number metadata.",
    },
    {
        "package_name": "com.hiya.star",
        "label": "Hiya Smart Call Cloud Lookup",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Transmits incoming and outgoing phone call metadata to third-party Hiya servers.",
    },
    {
        "package_name": "com.google.android.as",
        "label": "Android System Intelligence (Device Personalization)",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Google background behavioral profiling, smart reply harvesting, and contextual usage tracker.",
    },
    {
        "package_name": "com.google.android.feedback",
        "label": "Google Market Feedback Agent",
        "category": "telemetry_spyware",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Google crash and diagnostic feedback uploader.",
    },
    {
        "package_name": "com.samsung.android.forest",
        "label": "Digital Wellbeing Usage Logger",
        "category": "telemetry_spyware",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Logs every screen unlock and per-app foreground timestamp continuously.",
    },
    {
        "package_name": "com.samsung.carrier.logcollector",
        "label": "Samsung Carrier Log Collector",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Collects and uploads cellular/carrier radio and usage logs in the background.",
    },
    {
        "package_name": "com.samsung.android.knox.analytics.uploader",
        "label": "Samsung Knox Analytics Telemetry Uploader",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Uploads Knox analytics and behavioral usage telemetry to Samsung servers.",
    },
    {
        "package_name": "com.samsung.android.ipsgeofence",
        "label": "Samsung Indoor Positioning Geofence Tracker",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Background indoor location and geofence tracking service.",
    },
    {
        "package_name": "com.samsung.android.beaconmanager",
        "label": "Samsung BLE Beacon Location Tracker",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Continuously scans for nearby Bluetooth beacons in the background.",
    },
    {
        "package_name": "com.google.android.as.oss",
        "label": "Android System Intelligence Network Bridge (PCS)",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Network communication bridge for Android System Intelligence telemetry and models.",
    },
    {
        "package_name": "com.google.mainline.telemetry",
        "label": "Google Mainline Telemetry Module",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Google Project Mainline background telemetry reporter.",
    },
    {
        "package_name": "com.google.android.gms.location.history",
        "label": "Google Location History Tracker Module",
        "category": "telemetry_spyware",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Background timeline location history logger.",
    },

    # --- Category 2: facebook_preinstalled ---
    {
        "package_name": "com.facebook.services",
        "label": "Meta / Facebook Background Services",
        "category": "facebook_preinstalled",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed privileged Meta background daemon baked into Samsung /system firmware.",
    },
    {
        "package_name": "com.facebook.system",
        "label": "Meta App Installer (Privileged System Stub)",
        "category": "facebook_preinstalled",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Privileged Meta installer capable of silent background updates without Play Store.",
    },
    {
        "package_name": "com.facebook.appmanager",
        "label": "Meta App Manager",
        "category": "facebook_preinstalled",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Background Meta telemetry and package updater.",
    },
    {
        "package_name": "com.facebook.katana",
        "label": "Facebook Preload Stub",
        "category": "facebook_preinstalled",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-loaded Facebook application stub.",
    },
    {
        "package_name": "com.facebook.orca",
        "label": "Meta Messenger Preload",
        "category": "facebook_preinstalled",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Meta Messenger app and background listener.",
    },

    # --- Category 3: samsung_bloat ---
    {
        "package_name": "com.samsung.android.bixby.agent",
        "label": "Bixby Voice Assistant",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Samsung Bixby voice assistant and cloud voice processor.",
    },
    {
        "package_name": "com.samsung.android.bixby.agent.dummy",
        "label": "Bixby Voice Stub",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Bixby voice stub package.",
    },
    {
        "package_name": "com.samsung.android.bixby.service",
        "label": "Bixby Background Service",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Background context service for Bixby.",
    },
    {
        "package_name": "com.samsung.android.bixby.wakeup",
        "label": "Bixby Voice Wakeup Listener",
        "category": "samsung_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Always-on microphone hotword listener for Bixby.",
    },
    {
        "package_name": "com.samsung.android.visionintelligence",
        "label": "Bixby Vision",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Cloud image recognition and shopping integration.",
    },
    {
        "package_name": "com.samsung.systemui.bixby2",
        "label": "Bixby SystemUI Hook",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Bixby SystemUI overlay integration.",
    },
    {
        "package_name": "com.samsung.android.app.spage",
        "label": "Samsung Free / Samsung Daily (Bixby Home)",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Ad-supported news and game feed on the launcher home screen.",
    },
    {
        "package_name": "com.sec.spp.push",
        "label": "Samsung Push Service",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Delivers Samsung marketing notifications and Galaxy Store promos (Play Store uses FCM, not SPP).",
    },
    {
        "package_name": "com.samsung.android.game.gos",
        "label": "Samsung Game Optimizing Service (GOS)",
        "category": "samsung_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Profiles all installed apps, phones home to Samsung policy servers, and throttles CPU/GPU performance.",
    },
    {
        "package_name": "com.samsung.android.game.gamehome",
        "label": "Samsung Game Launcher",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Ad-heavy Samsung Game Hub.",
    },
    {
        "package_name": "com.samsung.android.game.gametools",
        "label": "Samsung Game Booster",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Overlay telemetry tool for games.",
    },
    {
        "package_name": "com.samsung.android.scloud",
        "label": "Samsung Cloud",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Background Samsung Cloud sync daemon.",
    },
    {
        "package_name": "com.samsung.android.arzone",
        "label": "Samsung AR Zone",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "3D AR camera sticker bloatware.",
    },
    {
        "package_name": "com.samsung.android.aremoji",
        "label": "Samsung AR Emoji",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "AR Avatar generator.",
    },
    {
        "package_name": "com.samsung.android.aremojieditor",
        "label": "Samsung AR Emoji Editor",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "AR Emoji customization bloatware.",
    },
    {
        "package_name": "com.sec.android.mimage.avatarstickers",
        "label": "AR Avatar Stickers",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed avatar sticker pack.",
    },
    {
        "package_name": "com.samsung.android.livestickers",
        "label": "Samsung DECO PIC",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Live camera sticker bloatware.",
    },
    {
        "package_name": "com.samsung.android.spay",
        "label": "Samsung Pay",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Samsung Wallet/Pay app.",
    },
    {
        "package_name": "com.samsung.android.spayfw",
        "label": "Samsung Pay Background Framework",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Always-running payment framework daemon.",
    },
    {
        "package_name": "com.samsung.android.samsungpass",
        "label": "Samsung Pass",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Samsung cloud password manager.",
    },
    {
        "package_name": "com.samsung.android.samsungpassautofill",
        "label": "Samsung Pass Autofill",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Autofill companion for Samsung Pass.",
    },
    {
        "package_name": "com.samsung.android.oneconnect",
        "label": "Samsung SmartThings",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Scans nearby BLE/Wi-Fi IoT devices in the background.",
    },
    {
        "package_name": "com.samsung.android.mdx",
        "label": "Samsung Link to Windows Service",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Microsoft Phone Link background bridge.",
    },
    {
        "package_name": "com.microsoft.appmanager",
        "label": "Microsoft Link to Windows Companion",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Microsoft Phone Link daemon.",
    },
    {
        "package_name": "com.samsung.android.app.pen.up",
        "label": "Samsung PENUP Social Drawing Network",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Note 10+ social coloring/art community app with account tracking and telemetry (zero impact on S-Pen hardware or Air Command).",
    },
    {
        "package_name": "com.sec.penup",
        "label": "Samsung PENUP Preload Package (SM-N976V)",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon Note 10+ 5G PENUP social drawing preload package.",
    },
    {
        "package_name": "com.samsung.android.ardrawing",
        "label": "Samsung AR Doodle",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "AR camera doodle bloatware.",
    },
    {
        "package_name": "com.samsung.android.app.settings.bixby",
        "label": "Bixby Settings Hook",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Bixby settings integration stub.",
    },
    {
        "package_name": "com.amazon.avod.thirdpartyclient",
        "label": "Amazon Prime Video System Stub",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Amazon Prime Video system stub.",
    },
    {
        "package_name": "com.amazon.mShop.android.shopping",
        "label": "Amazon Shopping Preload",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Amazon Shopping app.",
    },
    {
        "package_name": "com.amazon.kindle",
        "label": "Amazon Kindle Preload",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Kindle app.",
    },
    {
        "package_name": "com.audible.application",
        "label": "Audible Preload",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Audible app.",
    },
    {
        "package_name": "com.netflix.mediaclient",
        "label": "Netflix System Preload",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Netflix app.",
    },
    {
        "package_name": "com.netflix.partner.activation",
        "label": "Netflix Partner Activation Stub",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Netflix partner background stub.",
    },
    {
        "package_name": "com.disney.disneyplus",
        "label": "Disney+ Preload",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Disney+ app.",
    },
    {
        "package_name": "com.spotify.music",
        "label": "Spotify Preload",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Spotify stub.",
    },
    {
        "package_name": "com.microsoft.skydrive",
        "label": "Microsoft OneDrive System Preload",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Microsoft OneDrive cloud sync daemon.",
    },
    {
        "package_name": "com.microsoft.office.officehubrow",
        "label": "Microsoft 365 / Office Preload",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Microsoft Office hub.",
    },
    {
        "package_name": "com.microsoft.office.outlook",
        "label": "Microsoft Outlook Preload",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Outlook app.",
    },
    {
        "package_name": "com.microsoft.bing",
        "label": "Microsoft Bing Search Preload",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Microsoft Bing app.",
    },

    # --- Category 4: carrier_bloat ---
    {
        "package_name": "com.att.iqi",
        "label": "AT&T Carrier IQ (IQI) Telemetry Agent",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Deep carrier diagnostic & network telemetry agent on AT&T firmware.",
    },
    {
        "package_name": "com.tmobile.pr.adapt",
        "label": "T-Mobile Diagnostics & Telemetry Collector",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Background T-Mobile device usage and network telemetry daemon.",
    },
    {
        "package_name": "com.LogiaGroup.LogiaDeck",
        "label": "Verizon DT Ignite / AppFlash Silent Installer",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Silently installs sponsored bloatware games after system updates.",
    },
    {
        "package_name": "com.dti.folderlauncher",
        "label": "Carrier DT Ignite Folder Launcher",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Sponsored app launcher stub.",
    },
    {
        "package_name": "com.dti.att",
        "label": "AT&T AppSelect Silent Bloat Installer",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Silently pushes third-party carrier bloatware.",
    },
    {
        "package_name": "com.ironsource.appcloud.oobe",
        "label": "ironSource AppCloud / Aura Carrier Bloat Agent",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed adware/bloatware provisioning service.",
    },
    {
        "package_name": "com.vzw.hss.myverizon",
        "label": "My Verizon Carrier App",
        "category": "carrier_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon account & telemetry app.",
    },
    {
        "package_name": "com.customermobile.preload.vzw",
        "label": "Verizon Preload Silent Bloat Agent",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Silently provisions third-party promotional apps on Verizon devices.",
    },
    {
        "package_name": "com.securityandprivacy.android.verizon.vms",
        "label": "Verizon Digital Secure / VMS Telemetry",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon subscription security/VPN bloatware with background scanning.",
    },
    {
        "package_name": "com.verizon.llkagent",
        "label": "Verizon Live Look Retail Agent",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon retail demo & telemetry background agent.",
    },
    {
        "package_name": "com.verizon.mips.services",
        "label": "Verizon MIPS Background Telemetry Service",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon carrier billing and background network telemetry daemon.",
    },
    {
        "package_name": "com.verizon.obdm",
        "label": "Verizon On-Board Device Management (OBDM)",
        "category": "carrier_bloat",
        "risk_level": "HIGH",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon remote diagnostic and telemetry management daemon.",
    },
    {
        "package_name": "com.samsung.obdmpermission",
        "label": "Verizon OBDM Permission Stub",
        "category": "carrier_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Grants elevated telemetry permissions to Verizon OBDM.",
    },
    {
        "package_name": "com.verizon.familybase.parent",
        "label": "Verizon Smart Family Bloatware",
        "category": "carrier_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Verizon Smart Family parental control bloatware.",
    },
    {
        "package_name": "com.verizon.fivegcamera",
        "label": "Verizon 5G Camera Promo App",
        "category": "carrier_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon promotional AR/5G camera bloatware.",
    },
    {
        "package_name": "com.verizon.onetalk.dialer",
        "label": "Verizon OneTalk Enterprise Dialer Bloat",
        "category": "carrier_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Pre-installed Verizon enterprise OneTalk app.",
    },
    {
        "package_name": "com.vcast.mediamanager",
        "label": "Verizon Cloud (VCast Media Manager)",
        "category": "carrier_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon paid cloud backup bloatware.",
    },
    {
        "package_name": "com.vzw.ecid",
        "label": "Verizon Call Filter (ECID)",
        "category": "carrier_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Verizon caller ID upsell & call-log telemetry app.",
    },
    {
        "package_name": "com.att.myWireless",
        "label": "myAT&T Carrier App",
        "category": "carrier_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "AT&T account & telemetry app.",
    },
    {
        "package_name": "com.tmobile.pr.mytmobile",
        "label": "T-Life / T-Mobile App",
        "category": "carrier_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "T-Mobile carrier app with background location/telemetry hooks.",
    },

    # --- Category 5: critical_do_not_touch (Hard Safety Guardlist) ---
    {
        "package_name": "com.android.vending",
        "label": "Google Play Store",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Official Google Play Store client required for downloading, updating, and verifying apps.",
    },
    {
        "package_name": "com.google.android.gms",
        "label": "Google Play Services",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Core Play Services runtime required by Play Store and third-party apps (push notifications, Maps SDK).",
    },
    {
        "package_name": "com.google.android.gsf",
        "label": "Google Services Framework",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Required account/checkin bridge for Google Play Store.",
    },
    {
        "package_name": "com.google.android.webview",
        "label": "Android System WebView",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Embedded browser engine required by banking, OAuth login, and Play Store apps.",
    },
    {
        "package_name": "com.sec.imsservice",
        "label": "Samsung IMS Service (VoLTE & SMS)",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Critical cellular IP Multimedia Subsystem required for 4G/5G VoLTE voice calls and SMS.",
    },
    {
        "package_name": "com.sec.epdg",
        "label": "Samsung ePDG (Wi-Fi Calling Handoff)",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Required for Wi-Fi Calling and seamless LTE <-> Wi-Fi voice handoff.",
    },
    {
        "package_name": "com.samsung.advp.imssettings",
        "label": "Samsung Carrier IMS Settings",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Carrier VoLTE profile configuration provider.",
    },
    {
        "package_name": "com.sec.android.app.camera",
        "label": "Samsung Camera App",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Hardware-tuned camera app required for S10 dual-aperture (f/1.5-f/2.4) and telephoto/ultrawide lenses.",
    },
    {
        "package_name": "com.samsung.android.MtpApplication",
        "label": "Samsung USB MTP File Transfer",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Required for USB file transfer and ADB composite negotiation on OneUI.",
    },
    {
        "package_name": "com.samsung.android.lool",
        "label": "Samsung Device Care (Battery & Storage UI)",
        "category": "critical_do_not_touch",
        "risk_level": "PROTECTED",
        "is_protected": 1,
        "recommended_action": "keep",
        "description": "PROTECTED: Removing breaks the Settings -> Battery & Storage screens in OneUI.",
    },
    {
        "package_name": "com.samsung.android.service.aircommand",
        "label": "Note 10+ S-Pen Air Command Core Service",
        "category": "samsung_bloat",
        "risk_level": "MEDIUM",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Note 10+ S-Pen background daemon (removed when S-Pen is unused to save RAM/battery).",
    },
    {
        "package_name": "com.samsung.android.aircommandmanager",
        "label": "Note 10+ S-Pen Air Command Manager",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "S-Pen hover shortcuts daemon.",
    },
    {
        "package_name": "com.samsung.android.sdk.handwriting",
        "label": "Note 10+ S-Pen Handwriting Recognition SDK",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "S-Pen handwriting recognition service.",
    },
    {
        "package_name": "com.samsung.android.service.airviewdictionary",
        "label": "Note 10+ S-Pen Air View Dictionary & Hover",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "S-Pen hover dictionary overlay service.",
    },
    {
        "package_name": "com.samsung.android.app.notes",
        "label": "Samsung Notes",
        "category": "samsung_bloat",
        "risk_level": "LOW",
        "is_protected": 0,
        "recommended_action": "remove",
        "description": "Samsung Notes app and cloud sync daemon.",
    },
]


SEED_PRIVACY_SETTINGS: list[dict[str, Any]] = [
    {
        "namespace": "global",
        "setting_key": "captive_portal_mode",
        "hardened_value": "1",
        "default_value": "1",
        "title": "Keep Captive Portal Detection Active (Redirected Away from Google)",
        "rationale": "Preserves hotel/airport Wi-Fi login detection while redirecting probes away from connectivitycheck.gstatic.com.",
    },
    {
        "namespace": "global",
        "setting_key": "captive_portal_http_url",
        "hardened_value": "http://connectivitycheck.grapheneos.network/generate_204",
        "default_value": "http://connectivitycheck.gstatic.com/generate_204",
        "title": "Zero-Log HTTP Captive Portal Endpoint (GrapheneOS)",
        "rationale": "Replaces Google's HTTP connectivity check with GrapheneOS zero-log 204 endpoint.",
    },
    {
        "namespace": "global",
        "setting_key": "captive_portal_https_url",
        "hardened_value": "https://connectivitycheck.grapheneos.network/generate_204",
        "default_value": "https://www.google.com/generate_204",
        "title": "Zero-Log HTTPS Captive Portal Endpoint (GrapheneOS)",
        "rationale": "Replaces Google's HTTPS connectivity check so Google cannot log every Wi-Fi/cell IP transition.",
    },
    {
        "namespace": "global",
        "setting_key": "captive_portal_fallback_url",
        "hardened_value": "http://captiveportal.kuketz.de",
        "default_value": "http://www.google.com/gen_204",
        "title": "Zero-Log Fallback Captive Portal Endpoint (Kuketz Security)",
        "rationale": "Prevents fallback to Google if the primary captive portal check times out.",
    },
    {
        "namespace": "global",
        "setting_key": "captive_portal_other_fallback_urls",
        "hardened_value": "http://captiveportal.kuketz.de",
        "default_value": "http://connectivitycheck.gstatic.com/generate_204",
        "title": "Zero-Log Secondary Fallback Captive Portal URLs",
        "rationale": "Eliminates secondary fallback pings to Google.",
    },
    {
        "namespace": "global",
        "setting_key": "private_dns_mode",
        "hardened_value": "off",
        "default_value": "opportunistic",
        "title": "Route All DNS Through NulKast Wall VpnService (Prevent PrivateDnsBroken DoT Collision)",
        "rationale": "When NulKast Wall VpnService (10.215.173.2/32) is active, setting private_dns_mode=hostname causes Android netd to probe TCP/853 on 10.215.173.2 and flag PrivateDnsBroken. Setting 'off' routes 100% of port-53 DNS into NulKast Wall's local sinkhole, which forwards allowed queries to AdGuard DNS (94.140.14.14).",
    },
    {
        "namespace": "global",
        "setting_key": "private_dns_specifier",
        "hardened_value": "dns.adguard-dns.com",
        "default_value": "",
        "title": "System-Wide Tracker & Ad-Blocking DoT Resolver",
        "rationale": "Blackholes Samsung telemetry (smetrics.samsung.com, samsungads.com), Facebook Graph trackers, and Firebase/Adjust in-app ad SDKs while keeping Google Play Store 100% functional.",
    },
    {
        "namespace": "global",
        "setting_key": "wifi_scan_always_enabled",
        "hardened_value": "0",
        "default_value": "1",
        "title": "Disable Hidden Background Wi-Fi Location Scanning",
        "rationale": "Stops Android and Play Services from scanning nearby Wi-Fi BSSIDs for location triangulation when Wi-Fi is off.",
    },
    {
        "namespace": "global",
        "setting_key": "ble_scan_always_enabled",
        "hardened_value": "0",
        "default_value": "1",
        "title": "Disable Hidden Background Bluetooth Beacon Scanning",
        "rationale": "Stops background BLE beacon scanning when Bluetooth is turned off.",
    },
    {
        "namespace": "global",
        "setting_key": "wifi_wakeup_enabled",
        "hardened_value": "0",
        "default_value": "1",
        "title": "Disable Location-Triggered Wi-Fi Auto-Wakeup",
        "rationale": "Prevents continuous background geofence polling for saved Wi-Fi networks.",
    },
    {
        "namespace": "global",
        "setting_key": "network_recommendations_enabled",
        "hardened_value": "0",
        "default_value": "1",
        "title": "Disable Google Open Wi-Fi Network Rating Queries",
        "rationale": "Stops the OS from sending nearby open Wi-Fi MAC addresses to Google.",
    },
    {
        "namespace": "secure",
        "setting_key": "send_action_app_error",
        "hardened_value": "0",
        "default_value": "1",
        "title": "Disable Automatic App Crash Telemetry Uploads",
        "rationale": "Prevents stack traces and memory dumps from being uploaded automatically.",
    },
    {
        "namespace": "global",
        "setting_key": "SendSecurityReports",
        "hardened_value": "0",
        "default_value": "1",
        "title": "Disable Samsung Security Telemetry Uploads",
        "rationale": "Stops Samsung Knox/Security agents from transmitting periodic telemetry reports.",
    },
    {
        "namespace": "system",
        "setting_key": "samsung_errorlog_agree",
        "hardened_value": "0",
        "default_value": "1",
        "title": "Revoke Samsung Diagnostic Error Log Consent",
        "rationale": "Opts out of Samsung system-level diagnostic error collection.",
    },
    {
        "namespace": "system",
        "setting_key": "marketing_info_agree",
        "hardened_value": "0",
        "default_value": "1",
        "title": "Revoke Samsung Marketing & Promotional Consent",
        "rationale": "Opts out of Samsung system-level marketing notifications and profiling.",
    },
    {
        "namespace": "secure",
        "setting_key": "limit_ad_tracking",
        "hardened_value": "1",
        "default_value": "0",
        "title": "Enforce OS Limit Ad Tracking (Opt-Out of Ads Personalization)",
        "rationale": "Instructs Android and Play Services to deny personalized ad profiling.",
    },
    {
        "namespace": "global",
        "setting_key": "limit_ad_tracking",
        "hardened_value": "1",
        "default_value": "0",
        "title": "Enforce Global Limit Ad Tracking Flag",
        "rationale": "Mirrors Limit Ad Tracking across the global settings namespace.",
    },
    {
        "namespace": "secure",
        "setting_key": "advertising_id",
        "hardened_value": "00000000-0000-0000-0000-000000000000",
        "default_value": "",
        "title": "Zero-Out Secure Advertising ID (All-Zeros UUID)",
        "rationale": "Overwrites the Advertising ID setting with the null UUID (00000000-0000-0000-0000-000000000000).",
    },
    {
        "namespace": "global",
        "setting_key": "ad_id",
        "hardened_value": "00000000-0000-0000-0000-000000000000",
        "default_value": "",
        "title": "Zero-Out Global Ad ID & Disable AdServices",
        "rationale": "Overwrites global ad_id with the null UUID so apps cannot correlate cross-app behavior.",
    },
]


SEED_KNOWLEDGE: list[dict[str, str]] = [
    {
        "slug": "s10-bootloader-exynos-vs-snapdragon",
        "category": "bootloader",
        "title": "Samsung Galaxy S10 Exynos (F/N) vs North American Snapdragon (U/U1/W) Bootloader Architecture",
        "applies_to": "SM-G970 / SM-G973 / SM-G975 / SM-G977",
        "summary": "International Exynos 9820 models (F/FD/N) and HK/LatAm Snapdragon models (SM-G9730) provide OEM Unlocking in Developer Options and support full LineageOS 21/22 & GSI flashing. North American Snapdragon models (U/U1/W) have fused bootloaders without OEM Unlocking and must use Track 1 Surgical USB ADB De-Bloating & OS Network Hardening.",
        "remediation": "Run `make probe` (`cli.py probe`) with USB Debugging enabled to auto-detect `ro.product.model` and `ro.boot.flash.locked` and select Track 1 or Track 2 automatically.",
        "source": "research-verified",
    },
    {
        "slug": "samsung-volte-ims-preservation",
        "category": "telephony",
        "title": "Preserving Carrier VoLTE, Wi-Fi Calling & SMS During Aggressive De-Bloating",
        "applies_to": "Samsung OneUI & AOSP",
        "summary": "Since 2G/3G networks are retired, voice calls require VoLTE (`com.sec.imsservice`) and Wi-Fi Calling requires ePDG (`com.sec.epdg`). Furthermore, Samsung's proprietary IMS stack is not open-sourced for AOSP, meaning pure LineageOS on Samsung Exynos has working LTE data + SMS + VoIP, while stock OneUI (hardened via Track 1) retains native carrier VoLTE.",
        "remediation": "Enforce the Hard Safety Guardlist (`is_protected=1`) blocking removal of `com.sec.imsservice`, `com.sec.epdg`, and `com.samsung.advp.imssettings`.",
        "source": "research-verified",
    },
    {
        "slug": "play-store-without-tracking",
        "category": "privacy",
        "title": "Running Official Google Play Store While Blocking Telemetry & In-App Trackers",
        "applies_to": "All Android 9–15 Devices",
        "summary": "Keeping `com.android.vending` (Play Store), `com.google.android.gms` (Play Services), `com.google.android.gsf`, and `com.google.android.webview` while removing `com.google.android.as` (Android System Intelligence), `com.google.android.feedback`, and Samsung/Meta spyware (`com.samsung.android.rubin.app`, `com.facebook.*`), combined with GrapheneOS Captive Portal URLs + DNS-over-TLS tracker blackholing + RethinkDNS per-app firewall, preserves 100% Play Store app compatibility with zero background telemetry leaks.",
        "remediation": "Execute `make debloat` + `make harden` and optionally provision RethinkDNS (`com.celzero.bravedns`).",
        "source": "research-verified",
    },
    {
        "slug": "gsi-vbmeta-dm-verity-disable",
        "category": "custom_os",
        "title": "Disabling Android Verified Boot dm-verity (vbmeta --flags 2) for Custom GSI Flashing",
        "applies_to": "Project Treble Unlocked Devices (SM-G973F / SM-G9730 / Pixel)",
        "summary": "When flashing a modified Generic System Image (GSI) on a Project Treble device with an unlocked bootloader, Android Verified Boot (AVB 2.0) will halt boot unless `vbmeta.img` has header flags bit 0/1 set (`--flags 2` or `--flags 3`) to disable verification and hashtree dm-verity checks.",
        "remediation": "Run `make vbmeta` (`python3 cli.py image vbmeta`) to generate a 4096-byte aligned AVB0 `vbmeta_disabled.img` verified by `avbtool info_image`.",
        "source": "research-verified",
    },
    {
        "slug": "ubuntu-2404-adb-fastboot-recommends",
        "category": "docker_toolchain",
        "title": "Ubuntu 24.04 (noble) android-sdk-platform-tools Recommends vs Explicit adb/fastboot Packages",
        "applies_to": "Dockerfile (ubuntu:24.04)",
        "summary": "In Ubuntu 24.04, `android-sdk-platform-tools` declares `adb` and `fastboot` under `Recommends:` rather than `Depends:`. Installing with `apt-get install --no-install-recommends` omits both binaries unless `adb` and `fastboot` are explicitly listed.",
        "remediation": "Always specify `adb` and `fastboot` explicitly in `Dockerfile` alongside `android-sdk-platform-tools` and enforce all 9 binaries in Gate 5 of `server/validator.py`.",
        "source": "qa-self-learned",
    },
    {
        "slug": "wsl2-usbipd-adb-install-no-streaming",
        "category": "usb_adb",
        "title": "WSL2 usbipd-win ADB APK Installation Requires --no-streaming and verifier_verify_adb_installs=0",
        "applies_to": "All Android devices attached over WSL2 usbipd-win",
        "summary": "Modern `adb install` defaults to `--streaming` mode, which streams raw blocks over USB/IP (`usbipd-win` -> `vhci_hcd`) while Play Protect (`verifier_verify_adb_installs=1`) blocks waiting for user confirmation or stalls USB bulk endpoints.",
        "remediation": "Always set `adb shell settings put global verifier_verify_adb_installs 0` and pass `--no-streaming` (`adb install -r --no-streaming <apk>`) so `adb` performs a fast Push Install (`424 MB/s`).",
        "source": "qa-self-learned",
    },
    {
        "slug": "nulkast-vpn-split-dns-and-tcp-rst",
        "category": "nulkast_firewall",
        "title": "NulKast Wall VpnService Split /32 DNS Sinkhole & Instant 40-Byte TCP RST+ACK (0x14) Engine",
        "applies_to": "io.nulkast.wall (Android 10-15 / API 29-35)",
        "summary": "Configuring VpnService.Builder with `addAddress('10.215.173.1', 30)`, `addDnsServer('10.215.173.2')`, and `addRoute('10.215.173.2', 32)` intercepts all port-53 DNS traffic without routing bulky video/download streams through userspace. Blocked queries receive a synthesized RFC 1035 NXDOMAIN reply in <0.1ms, and blocked TCP sockets receive a 40-byte RFC 793 TCP RST+ACK (`0x14`) so connect() fails immediately with ECONNREFUSED instead of retrying for 63s.",
        "remediation": "Use `packages/net-intel/src/io/nulkast/netintel/PacketEngine.java` and `NulKastVpnService.java` with `foregroundServiceType='specialUse'`.",
        "source": "research-verified",
    },
    {
        "slug": "nulkast-sub-2s-aapt2-d8-apk-pipeline",
        "category": "apk_toolchain",
        "title": "Sub-2-Second Dockerized Multi-Package APK Build via aapt2, javac --system none, d8, and apksigner v1/v2/v3",
        "applies_to": "scripts/build_nulkast_apk.py (OpenJDK 17 + Android SDK 34)",
        "summary": "On OpenJDK 17, `-bootclasspath` is disallowed for `-source 17`; passing `--system none -source 17 -target 17 -classpath android.jar` compiles multi-package sources (`packages/*` + `apps/*`) cleanly. Packaging `.class` files into `classes.jar` before `d8` prevents Bash `$1` inner-class expansion bugs, and `zipalign -f -p 4` must precede `apksigner sign`.",
        "remediation": "Build and deploy all NulKast apps via `make build-apps`, `make deploy-wall`, or `make deploy-home`.",
        "source": "research-verified",
    },
    {
        "slug": "nulkast-signature-ipc-and-foldable-posture",
        "category": "nulkast_launcher",
        "title": "Zero-Internet NulKast Home Launcher with Signature ContentProvider IPC, S-Pen Barrel Detection & Pixel Fold sw600dp Dual-Pane",
        "applies_to": "io.nulkast.home (SM-N976V Note 10+ 5G & Google Pixel Fold felix)",
        "summary": "Signing both `io.nulkast.wall` and `io.nulkast.home` with `keystore/nulkast-release.p12` enables `io.nulkast.permission.IPC_BUS` (`protectionLevel='signature'`) RPC via `ContentResolver.call()`, allowing `NulKast Home` to display live firewall stats and toggle Stealth Lockdown while having zero `INTERNET` permission. `smallestScreenWidthDp >= 600` automatically switches between 4-column phone mode (`SM-N976V`, 411dp) and 6-column unfolded mode (`Pixel Fold felix`, 673dp).",
        "remediation": "Use `NulKastTheme.isUnfoldedDualPane()` and `NulKastTheme.attachSPenListener()` across all NulKast apps.",
        "source": "research-verified",
    },
    {
        "slug": "android-vpn-privatednsbroken-dot-collision",
        "category": "nulkast_firewall",
        "title": "Preventing Android ConnectivityService 'PrivateDnsBroken' When Running Local VpnService DNS Sinkhole",
        "applies_to": "io.nulkast.wall (Android 10-15 VpnService)",
        "summary": "When OS `private_dns_mode=hostname` (Strict DoT) is active alongside a local UDP/53 VpnService DNS server (`10.215.173.2`), Android `netd` attempts a TCP/853 TLS handshake to `10.215.173.2`, receives TCP RST, and marks `tun0` as `PrivateDnsBroken`. Setting `private_dns_mode=off` allows `tun0` to achieve `IS_VALIDATED` (`everValidated lastValidated`) so 100% of device DNS queries route through `NulKast Wall`.",
        "remediation": "Enforce `adb shell settings put global private_dns_mode off` when `NulKast Wall` is the active DNS sinkhole.",
        "source": "live-monitor-discovered",
    },
    {
        "slug": "samsung-semwifitransportlayer-playstore-scrape-leak",
        "category": "telemetry_discovery",
        "title": "Samsung OneUI SemWifiTransportLayerUtils Secretly Scrapes play.google.com/store/apps/details?id=<pkg> on App Launch",
        "applies_to": "Samsung OneUI (SM-N976V / SM-G973)",
        "summary": "Live logcat monitoring (`make monitor`) revealed that Samsung's `system_server` (`SemWifiTransportLayerUtils.getApplicationCategory`) queries `https://play.google.com/store/apps/details?id=<package_name>&hl=en` whenever a new app is launched to classify its Wi-Fi QoS category, leaking installed/sideloaded package names to Google.",
        "remediation": "Detect `SemWifiTransportLayerUtils` in `scripts/live_device_monitor.py` and route system traffic through `NulKast Wall`.",
        "source": "live-monitor-discovered",
    },
]


def init_db() -> None:
    conn = get_conn()
    try:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS device_matrix (
                model_prefix TEXT PRIMARY KEY,
                retail_name TEXT NOT NULL,
                codename TEXT NOT NULL,
                soc TEXT NOT NULL,
                bootloader_unlockable INTEGER NOT NULL DEFAULT 0,
                supported_tracks TEXT NOT NULL,
                volte_notes TEXT NOT NULL,
                download_combo TEXT NOT NULL,
                recovery_combo TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS package_catalog (
                package_name TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                category TEXT NOT NULL,
                risk_level TEXT NOT NULL,
                is_protected INTEGER NOT NULL DEFAULT 0,
                recommended_action TEXT NOT NULL,
                description TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS privacy_settings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                namespace TEXT NOT NULL,
                setting_key TEXT NOT NULL,
                hardened_value TEXT NOT NULL,
                default_value TEXT NOT NULL,
                title TEXT NOT NULL,
                rationale TEXT NOT NULL,
                UNIQUE(namespace, setting_key)
            );

            CREATE TABLE IF NOT EXISTS debloat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_serial TEXT NOT NULL,
                package_name TEXT NOT NULL,
                action TEXT NOT NULL,
                status TEXT NOT NULL,
                output TEXT NOT NULL,
                executed_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS image_builds (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                artifact_type TEXT NOT NULL,
                artifact_path TEXT NOT NULL,
                sha256 TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                details_json TEXT NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS learned_knowledge (
                slug TEXT PRIMARY KEY,
                category TEXT NOT NULL,
                title TEXT NOT NULL,
                applies_to TEXT NOT NULL,
                summary TEXT NOT NULL,
                remediation TEXT NOT NULL,
                source TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS telemetry_feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_serial TEXT NOT NULL,
                foreground_focus TEXT NOT NULL,
                vpn_tun0_active INTEGER NOT NULL,
                crash_count INTEGER NOT NULL,
                error_count INTEGER NOT NULL,
                feedback_json TEXT NOT NULL,
                collected_at TEXT NOT NULL
            );
            """
        )

        for d in SEED_DEVICE_MATRIX:
            conn.execute(
                """
                INSERT OR REPLACE INTO device_matrix
                (model_prefix, retail_name, codename, soc, bootloader_unlockable, supported_tracks, volte_notes, download_combo, recovery_combo)
                VALUES (:model_prefix, :retail_name, :codename, :soc, :bootloader_unlockable, :supported_tracks, :volte_notes, :download_combo, :recovery_combo)
                """,
                d,
            )

        for p in SEED_PACKAGES:
            conn.execute(
                """
                INSERT OR REPLACE INTO package_catalog
                (package_name, label, category, risk_level, is_protected, recommended_action, description)
                VALUES (:package_name, :label, :category, :risk_level, :is_protected, :recommended_action, :description)
                """,
                p,
            )

        for s in SEED_PRIVACY_SETTINGS:
            conn.execute(
                """
                INSERT OR REPLACE INTO privacy_settings
                (namespace, setting_key, hardened_value, default_value, title, rationale)
                VALUES (:namespace, :setting_key, :hardened_value, :default_value, :title, :rationale)
                """,
                s,
            )

        now = datetime.now(timezone.utc).isoformat()
        for k in SEED_KNOWLEDGE:
            conn.execute(
                """
                INSERT OR IGNORE INTO learned_knowledge
                (slug, category, title, applies_to, summary, remediation, source, updated_at)
                VALUES (:slug, :category, :title, :applies_to, :summary, :remediation, :source, :updated_at)
                """,
                {**k, "updated_at": now},
            )

        conn.commit()
    finally:
        conn.close()


def list_device_matrix() -> list[dict[str, Any]]:
    conn = get_conn()
    try:
        rows = conn.execute("SELECT * FROM device_matrix ORDER BY model_prefix").fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def list_packages(category: str | None = None) -> list[dict[str, Any]]:
    conn = get_conn()
    try:
        if category:
            rows = conn.execute(
                "SELECT * FROM package_catalog WHERE category = ? ORDER BY is_protected DESC, package_name",
                (category,),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM package_catalog ORDER BY is_protected DESC, category, package_name"
            ).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def list_privacy_settings() -> list[dict[str, Any]]:
    conn = get_conn()
    try:
        rows = conn.execute("SELECT * FROM privacy_settings ORDER BY id").fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def record_debloat_action(
    device_serial: str, package_name: str, action: str, status: str, output: str
) -> None:
    conn = get_conn()
    try:
        conn.execute(
            """
            INSERT INTO debloat_history (device_serial, package_name, action, status, output, executed_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (device_serial, package_name, action, status, output, datetime.now(timezone.utc).isoformat()),
        )
        conn.commit()
    finally:
        conn.close()


def list_debloat_history(limit: int = 100) -> list[dict[str, Any]]:
    conn = get_conn()
    try:
        rows = conn.execute(
            "SELECT * FROM debloat_history ORDER BY id DESC LIMIT ?", (limit,)
        ).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def record_image_build(
    artifact_type: str, artifact_path: str, sha256: str, size_bytes: int, details_json: str
) -> dict[str, Any]:
    now = datetime.now(timezone.utc).isoformat()
    conn = get_conn()
    try:
        conn.execute(
            """
            INSERT INTO image_builds (artifact_type, artifact_path, sha256, size_bytes, details_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (artifact_type, artifact_path, sha256, size_bytes, details_json, now),
        )
        conn.commit()
        return {
            "artifact_type": artifact_type,
            "artifact_path": artifact_path,
            "sha256": sha256,
            "size_bytes": size_bytes,
            "created_at": now,
        }
    finally:
        conn.close()


def list_image_builds(limit: int = 50) -> list[dict[str, Any]]:
    conn = get_conn()
    try:
        rows = conn.execute("SELECT * FROM image_builds ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def list_knowledge(category: str | None = None) -> list[dict[str, Any]]:
    conn = get_conn()
    try:
        if category:
            rows = conn.execute(
                "SELECT * FROM learned_knowledge WHERE category = ? ORDER BY slug", (category,)
            ).fetchall()
        else:
            rows = conn.execute("SELECT * FROM learned_knowledge ORDER BY slug").fetchall()
        return [dict(r) for r in rows]
    finally:
        conn.close()


def add_learned_knowledge(
    slug: str,
    category: str,
    title: str,
    applies_to: str,
    summary: str,
    remediation: str,
    source: str = "cli",
) -> dict[str, Any]:
    now = datetime.now(timezone.utc).isoformat()
    conn = get_conn()
    try:
        conn.execute(
            """
            INSERT OR REPLACE INTO learned_knowledge
            (slug, category, title, applies_to, summary, remediation, source, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (slug, category, title, applies_to, summary, remediation, source, now),
        )
        conn.commit()
        return {
            "slug": slug,
            "category": category,
            "title": title,
            "applies_to": applies_to,
            "summary": summary,
            "remediation": remediation,
            "source": source,
            "updated_at": now,
        }
    finally:
        conn.close()


def record_telemetry_feedback(snapshot: dict[str, Any]) -> None:
    conn = get_conn()
    try:
        conn.execute(
            """
            INSERT INTO telemetry_feedback
            (device_serial, foreground_focus, vpn_tun0_active, crash_count, error_count, feedback_json, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                str(snapshot.get("device_serial") or "unknown"),
                str(snapshot.get("foreground_focus") or "unknown"),
                1 if snapshot.get("vpn_tun0_active") else 0,
                int(snapshot.get("crash_count", 0)),
                int(snapshot.get("error_count", 0)),
                json.dumps(snapshot.get("actionable_feedback", [])),
                str(snapshot.get("collected_at") or datetime.now(timezone.utc).isoformat()),
            ),
        )
        conn.commit()
    finally:
        conn.close()

#!/usr/bin/env python3
"""
Deterministic Python + JVM Unit & Integration Test Suite (`tests/test_studio.py`).
Covers SQLite WAL persistence, hardware/bootloader classification, Play Store & VoLTE
guardlist invariants, AVB0 vbmeta binary generation, APK signature verification,
and the 47-assertion OpenJDK pure-JVM test suite (`scripts/run_jvm_tests.py`).
"""
from __future__ import annotations

import struct
import sys
import unittest
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from scripts import run_jvm_tests
from server import db, services, validator


class AndroidPrivacyStudioTestSuite(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        db.init_db()

    def test_01_sqlite_wal_and_seed_catalogs(self) -> None:
        with db.get_conn() as conn:
            mode = conn.execute("PRAGMA journal_mode").fetchone()[0].lower()
        self.assertEqual(mode, "wal")
        self.assertGreaterEqual(len(db.list_device_matrix()), 16)
        self.assertGreaterEqual(len(db.list_packages()), 95)
        self.assertGreaterEqual(len(db.list_privacy_settings()), 15)
        self.assertGreaterEqual(len(db.list_knowledge()), 10)

    def test_02_galaxy_note10_and_s10_bootloader_classifier(self) -> None:
        n976v = services.classify_galaxy_s10_model("SM-N976V")
        self.assertEqual(n976v["bootloader_unlockable"], 0)
        self.assertIn("d2xq", n976v["codename"])

        n976b = services.classify_galaxy_s10_model("SM-N976B")
        self.assertEqual(n976b["bootloader_unlockable"], 1)
        self.assertIn("d2x", n976b["codename"])

        g973f = services.classify_galaxy_s10_model("SM-G973F")
        self.assertEqual(g973f["bootloader_unlockable"], 1)

        g973u = services.classify_galaxy_s10_model("SM-G973U")
        self.assertEqual(g973u["bootloader_unlockable"], 0)

    def test_03_play_store_and_volte_safety_guardlist(self) -> None:
        plan = services.run_debloat(
            packages=[
                "com.android.vending",
                "com.google.android.gms",
                "com.sec.imsservice",
                "com.sec.epdg",
                "com.samsung.android.rubin.app",
                "com.sec.android.diagmonagent",
            ],
            execute=False,
        )
        blocked_guards = set(plan["blocked_protected_requests"])
        planned_pkgs = {r["package_name"] for r in plan["results"]}
        for protected in ("com.android.vending", "com.google.android.gms", "com.sec.imsservice", "com.sec.epdg"):
            self.assertIn(protected, blocked_guards)
            self.assertNotIn(protected, planned_pkgs)
        self.assertIn("com.samsung.android.rubin.app", planned_pkgs)
        self.assertIn("com.sec.android.diagmonagent", planned_pkgs)

    def test_04_avb0_vbmeta_and_hosts_overlay_generation(self) -> None:
        vb = services.generate_vbmeta_disabled_image()
        self.assertEqual(vb["size_bytes"], 4096)
        raw = Path(vb["artifact_path"]).read_bytes()
        self.assertEqual(raw[:4], b"AVB0")
        flags = struct.unpack(">I", raw[120:124])[0]
        self.assertEqual(flags, 3)

        ov = services.generate_custom_os_privacy_overlay()
        hosts_text = Path(ov["hosts_path"]).read_text(encoding="utf-8")
        self.assertIn("0.0.0.0 smetrics.samsung.com", hosts_text)
        self.assertIn("0.0.0.0 graph.facebook.com", hosts_text)
        self.assertNotIn("0.0.0.0 play.fe.googleapis.com", hosts_text)

    def test_05_nulkast_apk_suite_built_and_present(self) -> None:
        for app_slug in ("firewall", "audit", "scrub", "launcher"):
            apk = db.DATA_DIR / "apks" / f"nulkast-{app_slug}-release.apk"
            self.assertTrue(apk.exists(), f"Missing APK: {apk}")
            self.assertGreater(apk.stat().st_size, 15000)

    def test_06_pure_jvm_packet_exif_url_and_intel_suite(self) -> None:
        jvm_res = run_jvm_tests.run_jvm_suite()
        self.assertTrue(jvm_res.get("ok"), f"JVM test suite failed: {jvm_res}")
        self.assertIn("PASS: NulKastNetIntelTest", jvm_res.get("output", ""))

    def test_07_five_gate_preflight_qa_validator(self) -> None:
        qa_res = validator.run_qa_validation()
        self.assertTrue(qa_res["valid"], f"5-Gate QA failed: {qa_res}")
        self.assertEqual(qa_res["passed_gates"], 5)


if __name__ == "__main__":
    unittest.main(verbosity=2)

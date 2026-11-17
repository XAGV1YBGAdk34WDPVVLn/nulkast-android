"""
Tornado WebUI & REST API Server for Android Custom OS, De-Bloat & Privacy Studio (`server/app.py` // Port 8245).
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any

import tornado.ioloop
import tornado.web

from server import db, services, validator

TEMPLATE_DIR = Path(__file__).resolve().parent / "templates"


class BaseJsonHandler(tornado.web.RequestHandler):
    def set_default_headers(self) -> None:
        self.set_header("Access-Control-Allow-Origin", "*")
        self.set_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.set_header("Access-Control-Allow-Headers", "Content-Type")

    def options(self, *_args: Any, **_kwargs: Any) -> None:
        self.set_status(204)
        self.finish()

    def write_json(self, payload: Any, status: int = 200) -> None:
        self.set_status(status)
        self.set_header("Content-Type", "application/json; charset=utf-8")
        self.finish(json.dumps(payload, indent=2))

    def read_json_body(self) -> dict[str, Any]:
        if not self.request.body:
            return {}
        try:
            return json.loads(self.request.body.decode("utf-8"))
        except Exception:
            return {}


class IndexHandler(tornado.web.RequestHandler):
    def get(self) -> None:
        html_path = TEMPLATE_DIR / "index.html"
        self.set_header("Content-Type", "text/html; charset=utf-8")
        self.finish(html_path.read_text(encoding="utf-8"))


class HealthHandler(BaseJsonHandler):
    def get(self) -> None:
        db.init_db()
        self.write_json(
            {
                "status": "ok",
                "service": "android-privacy-studio",
                "port": int(os.environ.get("PORT", "8245")),
                "db_path": str(db.DB_PATH),
                "toolchain": services.check_docker_toolchain(),
            }
        )


class AgentSummaryHandler(BaseJsonHandler):
    def get(self) -> None:
        db.init_db()
        probe = services.probe_connected_device()
        self.write_json(
            {
                "service": "android-privacy-studio",
                "port": int(os.environ.get("PORT", "8245")),
                "capabilities": [
                    "track1_usb_adb_surgical_debloat",
                    "track1_play_store_and_volte_safety_guardlist",
                    "track1_os_privacy_and_dot_hardening",
                    "track2_avb0_vbmeta_disabled_generator",
                    "track2_gsi_system_image_privacy_patcher",
                    "track2_lineageos_s10_beyond_manifest",
                ],
                "connected_device": {
                    "adb_connected": probe["adb_connected"],
                    "serial": probe["serial"],
                    "classified_profile": probe["classified_profile"],
                },
                "counts": {
                    "device_matrix": len(db.list_device_matrix()),
                    "package_catalog": len(db.list_packages()),
                    "privacy_settings": len(db.list_privacy_settings()),
                    "learned_knowledge": len(db.list_knowledge()),
                },
            }
        )


class ProbeHandler(BaseJsonHandler):
    def get(self) -> None:
        db.init_db()
        self.write_json(services.probe_connected_device())


class CatalogHandler(BaseJsonHandler):
    def get(self) -> None:
        db.init_db()
        category = self.get_argument("category", None)
        self.write_json(
            {
                "device_matrix": db.list_device_matrix(),
                "packages": db.list_packages(category=category),
                "privacy_settings": db.list_privacy_settings(),
                "dns_providers": services.DNS_PROVIDERS,
                "privacy_app_bundle": services.PRIVACY_APP_BUNDLE,
                "debloat_history": db.list_debloat_history(limit=50),
                "image_builds": db.list_image_builds(limit=25),
            }
        )


class DebloatHandler(BaseJsonHandler):
    def post(self) -> None:
        db.init_db()
        body = self.read_json_body()
        categories = body.get("categories")
        packages = body.get("packages")
        execute = bool(body.get("execute", False))
        res = services.run_debloat(categories=categories, packages=packages, execute=execute)
        self.write_json(res)


class RestoreHandler(BaseJsonHandler):
    def post(self) -> None:
        db.init_db()
        body = self.read_json_body()
        pkg = body.get("package_name", "").strip()
        if not pkg:
            self.write_json({"ok": False, "error": "package_name is required"}, status=400)
            return
        execute = bool(body.get("execute", False))
        self.write_json(services.restore_package(package_name=pkg, execute=execute))


class HardenHandler(BaseJsonHandler):
    def post(self) -> None:
        db.init_db()
        body = self.read_json_body()
        dns_provider = body.get("dns_provider", "adguard")
        execute = bool(body.get("execute", False))
        self.write_json(services.apply_privacy_hardening(dns_provider=dns_provider, execute=execute))


class ImageBuildHandler(BaseJsonHandler):
    def post(self) -> None:
        db.init_db()
        vbmeta = services.generate_vbmeta_disabled_image()
        overlay = services.generate_custom_os_privacy_overlay()
        self.write_json({"ok": True, "vbmeta": vbmeta, "privacy_overlay": overlay})


class KnowledgeHandler(BaseJsonHandler):
    def get(self) -> None:
        db.init_db()
        cat = self.get_argument("category", None)
        rows = db.list_knowledge(category=cat)
        self.write_json({"count": len(rows), "entries": rows})

    def post(self) -> None:
        db.init_db()
        body = self.read_json_body()
        entry = db.add_learned_knowledge(
            slug=body.get("slug", ""),
            category=body.get("category", "general"),
            title=body.get("title", ""),
            applies_to=body.get("applies_to", "Android"),
            summary=body.get("summary", ""),
            remediation=body.get("remediation", ""),
            source=body.get("source", "api"),
        )
        self.write_json({"ok": True, "entry": entry})


class QAHandler(BaseJsonHandler):
    def get(self) -> None:
        self.write_json(validator.run_qa_validation())


class RulepacksHandler(BaseJsonHandler):
    def get(self) -> None:
        rp_dir = db.DATA_DIR / "rulepacks"
        rp_dir.mkdir(parents=True, exist_ok=True)
        packs = []
        for p in sorted(rp_dir.glob("*.json")):
            try:
                packs.append(json.loads(p.read_text(encoding="utf-8")))
            except Exception:
                pass
        self.write_json({"count": len(packs), "rulepacks": packs})


class FleetAnalyticsHandler(BaseJsonHandler):
    def get(self) -> None:
        db.init_db()
        perf_rows = db.list_perf_snapshots(limit=20)
        latest_android = perf_rows[0] if perf_rows else {}
        telemetry_rows = db.list_telemetry_feedback(limit=10)
        self.write_json(
            {
                "ok": True,
                "devices": [
                    {
                        "id": "sm-n976v",
                        "name": "Samsung Galaxy Note 10+ 5G (SM-N976V)",
                        "os": "Android 12 (OneUI 4.1 Hardened // NulKast Suite v2.1)",
                        "soc": "Qualcomm Snapdragon 855 (8 Cores) • 12GB RAM",
                        "suite_apps": ["io.nulkast.wall", "io.nulkast.audit", "io.nulkast.scrub", "io.nulkast.pulse", "io.nulkast.home"],
                        "latest_perf": latest_android,
                        "perf_history": perf_rows,
                        "debloated_packages": 89,
                    },
                    {
                        "id": "nocturne",
                        "name": "Google Pixel Slate (nocturne)",
                        "os": "NulKast OS 150.0 (Nocturne Turbo Edition + Android 13 ARCVM)",
                        "soc": "Intel Core i7-8500Y (2C/4T up to 4.20 GHz) • 16GB RAM • 256GB NVMe",
                        "kernel_flags": "mitigations=off kvm-intel.vmentry_l1d_flush=never",
                        "extensions": ["NulKast-Block MV3", "NulKast Pulse ChromeOS Studio"],
                        "thermal_guard": "3-Tier Fanless Loop (COOL_TURBO <55C, WARM_STEPDOWN 55-64C, HOT_CLAMP >=65C)",
                        "debloated_daemons": 10,
                        "debloated_arcvm_packages": 20,
                        "sinkholed_domains": 28,
                    },
                ],
                "telemetry_feedback": telemetry_rows,
            }
        )


def make_app() -> tornado.web.Application:
    db.init_db()
    return tornado.web.Application(
        [
            (r"/", IndexHandler),
            (r"/api/health", HealthHandler),
            (r"/api/agent/summary", AgentSummaryHandler),
            (r"/api/v1/discovery", AgentSummaryHandler),
            (r"/api/probe", ProbeHandler),
            (r"/api/catalog", CatalogHandler),
            (r"/api/debloat", DebloatHandler),
            (r"/api/restore", RestoreHandler),
            (r"/api/harden", HardenHandler),
            (r"/api/image/build", ImageBuildHandler),
            (r"/api/knowledge", KnowledgeHandler),
            (r"/api/rulepacks", RulepacksHandler),
            (r"/api/fleet", FleetAnalyticsHandler),
            (r"/api/qa", QAHandler),
        ]
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Android Custom OS & Privacy Studio Server")
    parser.add_argument("--port", type=int, default=int(os.environ.get("PORT", "8245")))
    args = parser.parse_args()

    app = make_app()
    app.listen(args.port, address="0.0.0.0")
    print(f"[android-privacy-studio] Listening on http://localhost:{args.port}")
    tornado.ioloop.IOLoop.current().start()


if __name__ == "__main__":
    main()

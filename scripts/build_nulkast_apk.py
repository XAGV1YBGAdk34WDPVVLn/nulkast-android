#!/usr/bin/env python3
"""
Fast (<2s) Zero-Daemon Multi-Package Android APK Compiler & Signer (`scripts/build_nulkast_apk.py`).
Compiles shared monorepo packages (`packages/ui-theme`, `packages/ipc-bus`, `packages/net-intel`)
and standalone apps (`apps/firewall`, `apps/launcher`, etc.) using Android SDK 34's
`aapt2` -> `javac --system none` -> `jar` -> `d8` -> `zipalign` -> `apksigner` (v1+v2+v3).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

ROOT_DIR = Path(__file__).resolve().parent.parent
ANDROID_HOME = Path(os.environ.get("ANDROID_HOME", "/opt/android-sdk"))
BUILD_TOOLS = ANDROID_HOME / "build-tools" / "34.0.0"
ANDROID_JAR = ANDROID_HOME / "platforms" / "android-34" / "android.jar"

KEYSTORE_PATH = ROOT_DIR / "keystore" / "nulkast-release.p12"
KEYSTORE_PASS = "nulkast-suite-2026"
KEYSTORE_ALIAS = "nulkast-suite"

APP_MAIN_ACTIVITIES = {
    "firewall": "io.nulkast.wall/.WallMainActivity",
    "audit": "io.nulkast.audit/.AuditMainActivity",
    "scrub": "io.nulkast.scrub/.ScrubMainActivity",
    "pulse": "io.nulkast.pulse/.PulseMainActivity",
    "launcher": "io.nulkast.home/.HomeMainActivity",
}


def ensure_shared_keystore() -> Path:
    KEYSTORE_PATH.parent.mkdir(parents=True, exist_ok=True)
    if KEYSTORE_PATH.exists() and KEYSTORE_PATH.stat().st_size > 100:
        return KEYSTORE_PATH
    subprocess.run(
        [
            "keytool",
            "-genkeypair",
            "-v",
            "-keystore",
            str(KEYSTORE_PATH),
            "-storetype",
            "PKCS12",
            "-alias",
            KEYSTORE_ALIAS,
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-sigalg",
            "SHA256withRSA",
            "-validity",
            "10000",
            "-storepass",
            KEYSTORE_PASS,
            "-keypass",
            KEYSTORE_PASS,
            "-dname",
            "CN=NulKast Suite, OU=Mobile OS Engineering, O=NulKast, C=US",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return KEYSTORE_PATH


def build_single_app(app_name: str, deploy: bool = False, launch: bool = False) -> dict[str, Any]:
    app_dir = ROOT_DIR / "apps" / app_name
    manifest_path = app_dir / "AndroidManifest.xml"
    if not manifest_path.exists():
        raise FileNotFoundError(f"Manifest not found: {manifest_path}")

    ensure_shared_keystore()
    out_dir = ROOT_DIR / "data" / "apks"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_apk = out_dir / f"nulkast-{app_name}-release.apk"

    with tempfile.TemporaryDirectory(prefix=f"nulkast_build_{app_name}_") as tmp:
        work = Path(tmp)
        res_zip = work / "res.zip"
        gen_dir = work / "gen"
        classes_dir = work / "classes"
        dex_dir = work / "dex"
        classes_jar = work / "classes.jar"
        unaligned_apk = work / "unaligned.apk"
        aligned_apk = work / "aligned.apk"

        gen_dir.mkdir(parents=True, exist_ok=True)
        classes_dir.mkdir(parents=True, exist_ok=True)
        dex_dir.mkdir(parents=True, exist_ok=True)

        # 1. aapt2 compile
        res_dir = app_dir / "res"
        subprocess.run(
            [str(BUILD_TOOLS / "aapt2"), "compile", "--dir", str(res_dir), "-o", str(res_zip)],
            check=True,
            capture_output=True,
            text=True,
        )

        # 2. aapt2 link
        subprocess.run(
            [
                str(BUILD_TOOLS / "aapt2"),
                "link",
                "-I",
                str(ANDROID_JAR),
                "--manifest",
                str(manifest_path),
                "-R",
                str(res_zip),
                "--auto-add-overlay",
                "--min-sdk-version",
                "29",
                "--target-sdk-version",
                "34",
                "--version-code",
                "1",
                "--version-name",
                "1.0.0",
                "--java",
                str(gen_dir),
                "-o",
                str(unaligned_apk),
            ],
            check=True,
            capture_output=True,
            text=True,
        )

        # 3. Collect all Java sources (generated R.java + shared packages/* + apps/<app>/src)
        java_files: list[str] = []
        for src_root in [
            gen_dir,
            ROOT_DIR / "packages" / "ui-theme" / "src",
            ROOT_DIR / "packages" / "ipc-bus" / "src",
            ROOT_DIR / "packages" / "net-intel" / "src",
            app_dir / "src",
        ]:
            if src_root.exists():
                for p in src_root.rglob("*.java"):
                    java_files.append(str(p))

        sources_list = work / "sources.txt"
        sources_list.write_text("\n".join(java_files) + "\n", encoding="utf-8")

        # 4. javac -source 8 -target 8 -bootclasspath android.jar
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-bootclasspath",
                str(ANDROID_JAR),
                "-classpath",
                str(ANDROID_JAR),
                "-d",
                str(classes_dir),
                "-encoding",
                "UTF-8",
                f"@{sources_list}",
            ],
            check=True,
            capture_output=True,
            text=True,
        )

        # 5. Package compiled .class files into classes.jar for d8
        subprocess.run(
            ["jar", "cf", str(classes_jar), "-C", str(classes_dir), "."],
            check=True,
            capture_output=True,
            text=True,
        )

        # 6. d8 bytecode-to-DEX compilation
        subprocess.run(
            [
                str(BUILD_TOOLS / "d8"),
                "--lib",
                str(ANDROID_JAR),
                "--release",
                "--min-api",
                "29",
                "--output",
                str(dex_dir),
                str(classes_jar),
            ],
            check=True,
            capture_output=True,
            text=True,
        )

        # 7. Add classes.dex into unaligned.apk
        dex_files = [str(p) for p in dex_dir.glob("classes*.dex")]
        subprocess.run(
            ["zip", "-qj", str(unaligned_apk)] + dex_files,
            check=True,
            capture_output=True,
            text=True,
        )

        # 8. zipalign -f -p 4
        subprocess.run(
            [str(BUILD_TOOLS / "zipalign"), "-f", "-p", "4", str(unaligned_apk), str(aligned_apk)],
            check=True,
            capture_output=True,
            text=True,
        )

        # 9. apksigner sign (v1 + v2 + v3)
        subprocess.run(
            [
                str(BUILD_TOOLS / "apksigner"),
                "sign",
                "--ks",
                str(KEYSTORE_PATH),
                "--ks-type",
                "PKCS12",
                "--ks-pass",
                f"pass:{KEYSTORE_PASS}",
                "--key-pass",
                f"pass:{KEYSTORE_PASS}",
                "--ks-key-alias",
                KEYSTORE_ALIAS,
                "--v1-signing-enabled",
                "true",
                "--v2-signing-enabled",
                "true",
                "--v3-signing-enabled",
                "true",
                "--out",
                str(out_apk),
                str(aligned_apk),
            ],
            check=True,
            capture_output=True,
            text=True,
        )

        # 10. apksigner verify --print-certs
        verify_res = subprocess.run(
            [str(BUILD_TOOLS / "apksigner"), "verify", "--verbose", "--print-certs", str(out_apk)],
            check=True,
            capture_output=True,
            text=True,
        )

    apk_bytes = out_apk.read_bytes()
    sha256 = hashlib.sha256(apk_bytes).hexdigest()
    cert_line = ""
    for line in verify_res.stdout.splitlines():
        if "Signer #1 certificate SHA-256 digest:" in line:
            cert_line = line.split(":", 1)[1].strip()
            break

    result: dict[str, Any] = {
        "ok": True,
        "app": app_name,
        "apk_path": str(out_apk),
        "size_bytes": len(apk_bytes),
        "sha256": sha256,
        "signer_cert_sha256": cert_line,
        "signature_schemes": ["v1", "v2", "v3"],
    }

    if deploy:
        subprocess.run(
            ["adb", "shell", "settings", "put", "global", "verifier_verify_adb_installs", "0"],
            capture_output=True,
            timeout=5,
        )
        inst = subprocess.run(
            ["adb", "install", "-r", "--no-streaming", str(out_apk)],
            capture_output=True,
            text=True,
            timeout=30,
        )
        result["deployed"] = inst.returncode == 0 and "Success" in inst.stdout
        result["adb_install_output"] = (inst.stdout or inst.stderr).strip()
        if result["deployed"] and app_name == "scrub":
            for cmd in [
                ["adb", "shell", "appops", "set", "io.nulkast.scrub", "MANAGE_EXTERNAL_STORAGE", "allow"],
                ["adb", "shell", "pm", "grant", "io.nulkast.scrub", "android.permission.READ_EXTERNAL_STORAGE"],
                ["adb", "shell", "pm", "grant", "io.nulkast.scrub", "android.permission.WRITE_EXTERNAL_STORAGE"],
            ]:
                subprocess.run(cmd, capture_output=True, timeout=5)
        if launch and result["deployed"] and app_name in APP_MAIN_ACTIVITIES:
            subprocess.run(
                ["adb", "shell", "am", "start", "-n", APP_MAIN_ACTIVITIES[app_name]],
                capture_output=True,
                text=True,
                timeout=8,
            )
            result["launched_activity"] = APP_MAIN_ACTIVITIES[app_name]

    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Build & deploy NulKast Android APKs")
    parser.add_argument("--app", default="all", help="App name under apps/ (firewall, launcher, or all)")
    parser.add_argument("--deploy", action="store_true", help="Install onto connected ADB device via --no-streaming")
    parser.add_argument("--launch", action="store_true", help="Launch main activity on phone after install")
    args = parser.parse_args()

    apps = ["firewall", "audit", "scrub", "pulse", "launcher"] if args.app == "all" else [args.app]
    results = []
    for a in apps:
        results.append(build_single_app(a, deploy=args.deploy, launch=args.launch))
    print(json.dumps({"ok": True, "builds": results}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        print(
            json.dumps(
                {
                    "ok": False,
                    "cmd": exc.cmd,
                    "stdout": exc.stdout,
                    "stderr": exc.stderr,
                },
                indent=2,
            )
        )
        sys.exit(1)

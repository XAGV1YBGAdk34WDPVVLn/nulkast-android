# 🛡️ NulKast Android Privacy Suite & Custom OS Hardening Studio

> [!IMPORTANT]
> **Hobby / Vibe-Coded Project — Provided As-Is With No Support**
> This repository is a personal, vibe-coded hobby experiment for Android privacy hardening, on-device packet/DNS filtering, and binary metadata scrubbing. **No support, warranty, issue tracking, or maintenance is provided.** Use or fork entirely at your own risk.

---

## Overview

**NulKast** (`io.nulkast.*`) is a Dockerized Android privacy hardening studio and zero-telemetry native Android app monorepo. Every app in the suite is compiled from scratch in **under 2 seconds** using the Android SDK 34 toolchain (`aapt2` $\rightarrow$ `javac` $\rightarrow$ `d8` $\rightarrow$ `zipalign` $\rightarrow$ `apksigner`) with **zero third-party dependencies** (`30 KB – 53 KB` per APK) and a shared Crisp Studio OLED (`#070A12`) 3-Zone Pinned Viewport UI.

---

## 1. The `NulKast` Single-Purpose App Suite (`apps/`)

Only `NulKast Wall` requests network/VPN access (`android.permission.INTERNET` + `BIND_VPN_SERVICE`). All companion tools are **100% offline by OS guarantee** (zero `INTERNET` permission in their manifests) and communicate with `NulKast Wall` over a cryptographic signature-protected IPC bus (`io.nulkast.permission.IPC_BUS`, `content://io.nulkast.wall.ipc/state`).

| App | Package ID & Size | Network Access | Core Capabilities |
| :--- | :--- | :--- | :--- |
| **🛡️ NulKast Wall** | `io.nulkast.wall` (`~53 KB`) | Owns Android's single `VpnService` (`tun0`) | • **Split `/32` DNS Sinkhole (`10.215.173.2/32`):** Intercepts port-53 DNS traffic across the OS and returns `< 0.1 ms` synthetic `NXDOMAIN` replies for 100+ tracker domains.<br>• **RFC 7858 DNS-over-TLS (`DoT` Port `853`):** Encrypts allowed upstream DNS queries over TLS (`SSLSocket` via `VpnService.protect()`).<br>• **DNS `CNAME` Cloaking Unmasker:** Inspects upstream DNS answer chains (`TYPE=5` `CNAME`) to block first-party subdomains aliasing to third-party trackers (`omtrdc.net`, `2o7.net`, `pardot.com`, `eulerian.net`).<br>• **Instant 40-Byte TCP `RST+ACK` (`0x14`) Blocker:** Immediately terminates blocked app socket connections with `ECONNREFUSED` (`< 1 ms`, zero 63-second TCP SYN retry battery drain).<br>• **3-Bucket App Safety Advisor + 1-Tap Normal Reset:** Categorizes every installed app into `🟢 Safe to Block`, `🟡 Normal Apps`, or `🔒 Protected Core` (preserving Google Play Store, Download Manager, and Carrier VoLTE/Wi-Fi Calling). |
| **🕵️ NulKast Audit** | `io.nulkast.audit` (`~37 KB`) | **ZERO** (`Offline`) | • **Static APK Exodus Tracker SDK X-Ray:** Scans `Activities`, `Services`, `Receivers`, and `Providers` across all installed packages against 16 tracker SDK families (`Firebase Analytics`, `Crashlytics`, `AdMob`, `Facebook Graph/Ads`, `AppsFlyer`, `Adjust`, `Braze`, `Branch`, `OneSignal`, `Mixpanel`, `Amplitude`, `Unity Ads`, `AppLovin`, `IronSource`, `Pangle`, `AppCenter`) and 9 sensitive permissions.<br>• **Deterministic Privacy Grades (`A+` to `F`):** Provides 1-tap **`[🛑 Block in NulKast Wall]`** over the signature IPC bus and 1-tap **`[⚙️ Permissions]`** shortcut. |
| **🧹 NulKast Scrub** | `io.nulkast.scrub` (`~49 KB`) | **ZERO** (`Offline`) | • **⚡ 1-Tap Device-Wide Photo & Video Scrubber (`O(1)` 32 KB RAM):** Scans and scrubs `DCIM`, `Pictures`, `Movies`, and `Download` with **0% pixel or video re-encoding**:<br>  - **Photos (`JPEG`):** `< 0.5 ms` header quick-skip (`0xFFD8..0xFFDA`) + `32 KB` constant-RAM streaming removal of `APP1..APP15` (`EXIF`, `GPS`, `XMP`) and `COM` segments.<br>  - **Videos (`MP4`/`MOV`/`M4V`/`3GP`):** `RandomAccessFile` seeks past multi-GB `mdat` streams in `< 0.01 ms` and zeroes `©xyz` / `loci` GPS and `smta` / `meta` device atoms **in-place** as ISO/IEC 14496-12 `'free'` (`0x66726565`) padding boxes (`stco`/`co64` chunk offsets untouched).<br>• **🔗 Tracking Link Sanitizer:** Strips 35+ click-tracking query params (`utm_*`, `fbclid`, `gclid`, `msclkid`, `igshid`, `si`, `_gl`, `gad_source`) via paste or Android **Share Sheet**.<br>• **📋 Foreground Clipboard Inspector & 1-Tap RAM Shredder:** Inspects and wipes the Android system clipboard (`clearPrimaryClip()`). |
| **🏠 NulKast Home** *(Optional)* | `io.nulkast.home` (`~33 KB`) | **ZERO** (`Offline`) | • Optional app hub (does not hijack the system Home button) featuring a live `NulKast Wall` telemetry HUD and adaptive foldable dual-pane (`sw600dp`) grid. |
| **🌐 NulKast-Block** *(Browser Companion)* | [NulKast-Block](https://github.com/XAGV1YBGAdk34WDPVVLn/NulKast-Block) (`MV3`) | **ZERO** (`connect-src 'self'`) | • Companion Manifest V3 Edge & Chrome AdBlocker with native C++ `declarativeNetRequest` blocking, 53-parameter URL tracker scrubbing, local C++ surrogate shims, and Shadow-DOM Element Zapper (`Alt+Shift+Z`). |

---

## 2. Repository Structure

```text
├── apps/
│   ├── firewall/        # 🛡️ NulKast Wall (io.nulkast.wall) — VpnService, DoT, CNAME Unmasker & App Firewall
│   ├── audit/           # 🕵️ NulKast Audit (io.nulkast.audit) — Static APK Tracker SDK & Permission X-Ray
│   ├── scrub/           # 🧹 NulKast Scrub (io.nulkast.scrub) — O(1) JPEG/MP4 Batch Scrubber, URL & Clipboard Cleaner
│   └── launcher/        # 🏠 NulKast Home (io.nulkast.home) — Optional Live HUD & App Hub
├── packages/
│   ├── net-intel/       # Pure-Java RFC 791/793/1035 PacketEngine, AppIntelligence, JpegExifScrubber, Mp4AtomScrubber, BatchMediaScrubber, UrlSanitizer
│   ├── ipc-bus/         # Signature-protected IPC Contract & NulKastHudClient (io.nulkast.permission.IPC_BUS)
│   └── ui-theme/        # Crisp Studio OLED 4-Tier Luminance Theme & sw600dp Foldable Posture Kit
├── server/              # Tornado Web Control Plane (:8245), SQLite WAL Catalog & 5-Gate Pre-Flight QA Engine
├── scripts/             # Sub-2s APK Builder (build_nulkast_apk.py), Pure-JVM Test Runner & Live ADB Monitor
├── tests/               # 56-Assertion Pure-JVM Test Suite (tests/jvm/) + 7-Suite Python Integration Tests
└── devices/             # Hardware profiles & community rulepack schemas
```

---

## 3. Quick Start & Makefile Commands

### Prerequisites
- Docker & Docker Compose
- An Android 10–15 (`API 29–35`) phone with **USB Debugging** enabled

### Build, Deploy & Test

```bash
# 1. Start the Dockerized Android Privacy Studio container (http://localhost:8245)
make up

# 2. Compile, sign (v1+v2+v3), and deploy all 4 NulKast APKs to a connected Android device
make deploy-apps

# 3. Run the 56-assertion Pure-JVM Unit & Adversarial Binary Vector Test Suite (<800ms)
make test-jvm

# 4. Run the full Python + SQLite WAL + AVB0 + Pure-JVM Integration Test Suite
make test

# 5. Run the deterministic 5-Gate Pre-Flight QA Validator
make qa

# 6. Preview or execute surgical USB ADB bloatware/telemetry removal & 19-point OS hardening
make debloat-dry-run
make harden-dry-run
```

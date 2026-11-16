#!/usr/bin/env python3
"""
Host-to-Container USB & WPD Hardware Telemetry Bridge (`scripts/windows_usb_bridge.py`).
Queries both Linux USB (`lsusb` / container `lsusb`) and the Windows Host via non-interactive
PowerShell (`stdin=subprocess.DEVNULL`, `Get-PnpDevice`, `usbipd list`, and `IPortableDevice` WPD COM)
for Samsung (`04e8:*`), Google/AOSP (`18d1:*`), and other Android devices.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

POWERSHELL_ANDROID_PROBE = r"""
$ErrorActionPreference = 'SilentlyContinue'
$result = @{
    probed_at = (Get-Date).ToUniversalTime().ToString("o")
    windows_pnp_devices = @()
    usbipd_devices = @()
    wpd_model = $null
    wpd_manufacturer = $null
    wpd_serial_number = $null
    wpd_firmware_version = $null
}

$pnp = Get-PnpDevice -PresentOnly | Where-Object {
    $_.InstanceId -match '04E8|18D1|05C6|22B8|2A70|2717|0E8D' -or
    $_.FriendlyName -match 'Samsung|Galaxy|Note|Android|ADB|MTP'
}
foreach ($d in $pnp) {
    $result.windows_pnp_devices += @{
        Status = [string]$d.Status
        Class = [string]$d.Class
        FriendlyName = [string]$d.FriendlyName
        InstanceId = [string]$d.InstanceId
    }
}

$usbipdLines = usbipd list 2>$null
foreach ($line in $usbipdLines) {
    if ($line -match '^(\d+-\d+)\s+([0-9a-fA-F]{4}:[0-9a-fA-F]{4})\s+(.+?)\s+(Not shared|Shared|Attached)\s*$') {
        $busid = $Matches[1]
        $vidpid = $Matches[2].ToLower()
        $desc = $Matches[3].Trim()
        $ustate = $Matches[4]
        if ($vidpid -match '^(04e8|18d1|05c6|22b8|2a70|2717|0e8d):' -or $desc -match 'Samsung|Galaxy|Note|Android|ADB|MTP') {
            $result.usbipd_devices += @{
                busid = $busid
                usb_id = $vidpid
                description = $desc
                state = $ustate
                attached_to_wsl = ($ustate -eq 'Attached')
            }
        }
    }
}

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public class AndroidWpdProbe {
    [StructLayout(LayoutKind.Sequential, Pack=4)]
    public struct PROPERTYKEY { public Guid fmtid; public uint pid; }
    [StructLayout(LayoutKind.Sequential)]
    public struct PROPVARIANT { public ushort vt; public ushort r1; public ushort r2; public ushort r3; public IntPtr p; public int p2; }
    [ComImport, Guid("a1567595-4c2f-4574-a6fa-ecef917b9a40"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IPortableDeviceManager {
        int GetDevices([In, Out, MarshalAs(UnmanagedType.LPArray, ArraySubType=UnmanagedType.LPWStr)] string[] pPnPDeviceIDs, ref uint pcPnPDeviceIDs);
    }
    [ComImport, Guid("6848F6F2-3155-4F86-B6F5-263EEEAB3143"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IPortableDeviceValues {
        int GetCount(ref uint pcelt);
        int GetAt(uint index, ref PROPERTYKEY pKey, ref PROPVARIANT pValue);
    }
    [ComImport, Guid("6A96ED84-7C73-4480-9938-BF5AF477D426"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IPortableDeviceContent {
        int EnumObjects(uint dwFlags, [MarshalAs(UnmanagedType.LPWStr)] string pszParentObjectID, IntPtr pFilter, IntPtr ppenum);
        int Properties([MarshalAs(UnmanagedType.Interface)] out IPortableDeviceProperties ppProperties);
    }
    [ComImport, Guid("7F6D695C-03DF-4439-A809-59266BEEE3A6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IPortableDeviceProperties {
        int GetSupportedProperties([MarshalAs(UnmanagedType.LPWStr)] string pszObjectID, IntPtr ppKeys);
        int GetPropertyAttributes([MarshalAs(UnmanagedType.LPWStr)] string pszObjectID, ref PROPERTYKEY Key, IntPtr ppAttributes);
        int GetValues([MarshalAs(UnmanagedType.LPWStr)] string pszObjectID, IntPtr pKeys, [MarshalAs(UnmanagedType.Interface)] out IPortableDeviceValues ppValues);
    }
    [ComImport, Guid("625E2DF8-6392-4CF0-9AD1-3CFA5F17775C"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface IPortableDevice {
        int Open([MarshalAs(UnmanagedType.LPWStr)] string pszPnPDeviceID, [MarshalAs(UnmanagedType.Interface)] IPortableDeviceValues pClientInfo);
        int SendCommand(uint dwFlags, [MarshalAs(UnmanagedType.Interface)] IPortableDeviceValues pParameters, [MarshalAs(UnmanagedType.Interface)] out IPortableDeviceValues ppResults);
        int Content([MarshalAs(UnmanagedType.Interface)] out IPortableDeviceContent ppContent);
    }
    public static string QueryAndroidWpd() {
        try {
            Type tMgr = Type.GetTypeFromCLSID(new Guid("0af10cec-2ecd-4b92-9581-34f6ae0637f3"));
            IPortableDeviceManager mgr = (IPortableDeviceManager)Activator.CreateInstance(tMgr);
            uint count = 0;
            mgr.GetDevices(null, ref count);
            if (count == 0) return "";
            string[] ids = new string[count];
            mgr.GetDevices(ids, ref count);
            string targetId = null;
            foreach (string id in ids) {
                if (id != null && (id.ToLower().Contains("vid_04e8") || id.ToLower().Contains("vid_18d1"))) {
                    targetId = id;
                    break;
                }
            }
            if (targetId == null) return "";
            Type tDev = Type.GetTypeFromCLSID(new Guid("728a21c5-3d9e-48d7-9810-864848f0f404"));
            Type tVal = Type.GetTypeFromCLSID(new Guid("0c15d503-d017-47ce-9016-7b3f978721cc"));
            IPortableDevice dev = (IPortableDevice)Activator.CreateInstance(tDev);
            IPortableDeviceValues clientInfo = (IPortableDeviceValues)Activator.CreateInstance(tVal);
            dev.Open(targetId, clientInfo);
            IPortableDeviceContent content;
            dev.Content(out content);
            IPortableDeviceProperties props;
            content.Properties(out props);
            IPortableDeviceValues vals;
            props.GetValues("DEVICE", IntPtr.Zero, out vals);
            uint pCount = 0;
            vals.GetCount(ref pCount);
            Guid gDev = new Guid("26d4979a-e643-4626-9e2b-736dc0c92fdc");
            string fw = "", sn = "", mfg = "", mdl = "";
            for (uint i = 0; i < pCount; i++) {
                PROPERTYKEY pk = new PROPERTYKEY();
                PROPVARIANT pv = new PROPVARIANT();
                vals.GetAt(i, ref pk, ref pv);
                if (pk.fmtid == gDev && pv.vt == 31 && pv.p != IntPtr.Zero) {
                    string s = Marshal.PtrToStringUni(pv.p);
                    if (pk.pid == 3) fw = s;
                    else if (pk.pid == 7) mfg = s;
                    else if (pk.pid == 8) mdl = s;
                    else if (pk.pid == 9) sn = s;
                }
            }
            return fw + "|" + sn + "|" + mfg + "|" + mdl;
        } catch {
            return "";
        }
    }
}
"@

try {
    $wpdRaw = [AndroidWpdProbe]::QueryAndroidWpd()
    if ($wpdRaw -and $wpdRaw.Contains("|")) {
        $parts = $wpdRaw.Split("|")
        $result.wpd_firmware_version = $parts[0]
        $result.wpd_serial_number = $parts[1]
        $result.wpd_manufacturer = $parts[2]
        $result.wpd_model = $parts[3]
    }
} catch {}

$result | ConvertTo-Json -Depth 5
"""


def scan_linux_lsusb() -> list[dict[str, Any]]:
    devices: list[dict[str, Any]] = []
    cmd = ["lsusb"] if shutil.which("lsusb") else ["docker", "exec", "android-privacy-studio", "lsusb"]
    try:
        res = subprocess.run(cmd, stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=5)
        for line in res.stdout.splitlines():
            m = re.search(r"Bus\s+(\d+)\s+Device\s+(\d+):\s+ID\s+([0-9a-fA-F]{4}):([0-9a-fA-F]{4})\s+(.*)", line)
            if not m:
                continue
            bus, dev, vid, pid, desc = m.groups()
            vid_l, pid_l = vid.lower(), pid.lower()
            if vid_l in ("04e8", "18d1", "05c6", "22b8", "2a70", "2717", "0e8d"):
                devices.append(
                    {
                        "bus": bus,
                        "device": dev,
                        "vid": vid_l,
                        "pid": pid_l,
                        "usb_id": f"{vid_l}:{pid_l}",
                        "description": desc.strip(),
                        "attached_to_wsl": True,
                    }
                )
    except Exception:
        pass
    return devices


def scan_windows_host() -> dict[str, Any]:
    ps_exe = "/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe"
    if not Path(ps_exe).exists():
        return {}
    try:
        res = subprocess.run(
            [ps_exe, "-NoProfile", "-NonInteractive", "-Command", POWERSHELL_ANDROID_PROBE],
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            timeout=12,
        )
        raw = res.stdout.strip()
        if raw.startswith("{"):
            return json.loads(raw)
    except Exception:
        pass
    return {}


def main() -> None:
    parser = argparse.ArgumentParser(description="Probe Linux & Windows USB/WPD for Android / Samsung devices")
    parser.add_argument("--out", default="data/host_usb_probe.json", help="Output JSON path")
    args = parser.parse_args()

    linux_devs = scan_linux_lsusb()
    win_data = scan_windows_host()
    win_usbipd = win_data.get("usbipd_devices", [])
    win_pnp = win_data.get("windows_pnp_devices", [])

    report = {
        "scanned_at": datetime.now(timezone.utc).isoformat(),
        "linux_usb_devices": linux_devs,
        "windows_usbipd_devices": win_usbipd,
        "windows_pnp_devices": win_pnp,
        "wpd_model": win_data.get("wpd_model"),
        "wpd_manufacturer": win_data.get("wpd_manufacturer"),
        "wpd_serial_number": win_data.get("wpd_serial_number"),
        "wpd_firmware_version": win_data.get("wpd_firmware_version"),
        "any_android_attached_linux": len(linux_devs) > 0,
        "any_android_detected_windows": len(win_usbipd) > 0 or len(win_pnp) > 0,
    }
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

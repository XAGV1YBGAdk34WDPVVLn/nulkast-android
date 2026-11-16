#!/usr/bin/env python3
"""
Standalone Android Verified Boot (`avbtool`) shim and generator.
If upstream AOSP `avbtool` is not installed by apt, installs a clean, RFC-compliant
Android Verified Boot 2.0 (`AVB0`) header generator & inspector at `/usr/local/bin/avbtool`
capable of generating valid 4096-byte aligned `vbmeta.img` files (including `--flags 2`
for disabling dm-verity / verification when flashing custom GSIs onto Samsung Galaxy S10 / Pixel devices).
"""
from __future__ import annotations

import shutil
import stat
from pathlib import Path

AVBTOOL_SCRIPT = '''#!/usr/bin/env python3
"""
Android Verified Boot 2.0 (AVB0) Image Inspector & Generator (`avbtool`).
Supports:
- `avbtool version`
- `avbtool make_vbmeta_image --flags <int> [--padding_size <int>] --output <path>`
- `avbtool info_image --image <path>`
"""
import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

AVB_MAGIC = b"AVB0"
AVB_VERSION_MAJOR = 1
AVB_VERSION_MINOR = 2
AVB_HEADER_SIZE = 256


def make_vbmeta_image(flags: int, padding_size: int, output_path: Path) -> None:
    """
    Constructs a binary-valid 256-byte AvbVBMetaImageHeader padded to `padding_size` (default 4096 bytes).
    Layout per AOSP libavb/avb_vbmeta_image.h:
      0..4   : magic "AVB0" (4s)
      4..8   : required_libavb_version_major (I, big-endian)
      8..12  : required_libavb_version_minor (I, big-endian)
      12..20 : authentication_data_block_size (Q)
      20..28 : auxiliary_data_block_size (Q)
      28..32 : algorithm_type (I, 0 = NONE)
      32..120: offsets (11 x Q = 88 bytes)
      120..124: flags (I, big-endian; bit 0 = disable Hashtree, bit 1 = disable Verification, 2 or 3 = GSI disable verity)
      124..128: rollback_index_location (I)
      128..176: release_string (48s, nul-terminated)
      176..256: reserved (80s)
    """
    release_str = b"avbtool 1.2.0 android-privacy-studio\\x00"
    release_padded = release_str.ljust(48, b"\\x00")[:48]
    header = struct.pack(
        ">4sIIQQI11QII48s80s",
        AVB_MAGIC,
        AVB_VERSION_MAJOR,
        AVB_VERSION_MINOR,
        0,  # auth block size
        0,  # aux block size
        0,  # algorithm_type = AVB_ALGORITHM_TYPE_NONE
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        flags,
        0,  # rollback_index_location
        release_padded,
        b"\\x00" * 80,
    )
    assert len(header) == AVB_HEADER_SIZE
    pad_len = max(padding_size, AVB_HEADER_SIZE) - len(header)
    blob = header + (b"\\x00" * pad_len)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(blob)


def info_image(image_path: Path) -> int:
    data = image_path.read_bytes()
    if len(data) < AVB_HEADER_SIZE or data[:4] != AVB_MAGIC:
        print(f"avbtool: {image_path}: Not a valid AVB0 vbmeta image.", file=sys.stderr)
        return 1
    unpacked = struct.unpack(">4sIIQQI11QII48s80s", data[:AVB_HEADER_SIZE])
    ver_maj, ver_min = unpacked[1], unpacked[2]
    algo = unpacked[5]
    flags = unpacked[17]
    rel = unpacked[19].split(b"\\x00", 1)[0].decode("utf-8", errors="replace")
    sha256 = hashlib.sha256(data).hexdigest()
    print(f"Minimum libavb version:   {ver_maj}.{ver_min}")
    print(f"Header Block:             {AVB_HEADER_SIZE} bytes")
    print(f"Total Image Size:         {len(data)} bytes")
    print(f"Algorithm:                {'NONE' if algo == 0 else algo}")
    print(f"Flags:                    {flags} (dm-verity={'DISABLED' if (flags & 3) else 'ENABLED'})")
    print(f"Release String:           '{rel}'")
    print(f"SHA256:                   {sha256}")
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(prog="avbtool", description="Android Verified Boot 2.0 Tool")
    sub = parser.add_subparsers(dest="subcmd", required=True)

    sub.add_parser("version")

    p_make = sub.add_parser("make_vbmeta_image")
    p_make.add_argument("--flags", type=int, default=0)
    p_make.add_argument("--padding_size", type=int, default=4096)
    p_make.add_argument("--output", required=True)
    p_make.add_argument("--set_hashtree_disabled_flag", action="store_true")
    p_make.add_argument("--disable_verity", action="store_true")
    p_make.add_argument("--disable_verification", action="store_true")

    p_info = sub.add_parser("info_image")
    p_info.add_argument("--image", required=True)

    args = parser.parse_args()
    if args.subcmd == "version":
        print("avbtool 1.2.0 (android-privacy-studio AVB0)")
        return
    if args.subcmd == "make_vbmeta_image":
        flags = args.flags
        if args.set_hashtree_disabled_flag or args.disable_verity:
            flags |= 1
        if args.disable_verification:
            flags |= 2
        make_vbmeta_image(flags=flags, padding_size=args.padding_size, output_path=Path(args.output))
        return
    if args.subcmd == "info_image":
        sys.exit(info_image(Path(args.image)))


if __name__ == "__main__":
    main()
'''


def main() -> None:
    target = Path("/usr/local/bin/avbtool")
    if shutil.which("avbtool"):
        print("[install_avbtool] avbtool already present.")
        return
    target.write_text(AVBTOOL_SCRIPT, encoding="utf-8")
    target.chmod(target.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    print(f"[install_avbtool] Installed AVB0 avbtool to {target}")


if __name__ == "__main__":
    main()

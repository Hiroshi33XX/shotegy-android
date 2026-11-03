# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Hiroshi33XX and contributors.
"""Inspect only Git-indexed public files; never walk local keys or caches."""
import re, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
files=subprocess.check_output(["git","ls-files","-z"],cwd=root).decode("utf8").split("\0")
forbidden={".signing",".private",".ios-reference",".android-sdk",".android-user",".android-avd",".gradle",".kotlin","build","__pycache__"}
patterns=[re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}"),re.compile(r"github_pat_[A-Za-z0-9_]{30,}"),re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),re.compile(r"wxid_[A-Za-z0-9_]+"),re.compile(r"[A-Z]:[\\/](?:Users|WeChatFile|Shotegy4Android)[\\/]",re.I)]
errors=[];total=0
for name in filter(None,files):
    p=Path(name)
    if set(p.parts)&forbidden or p.name in {"local.properties","signing.properties"} or p.suffix.lower() in {".jks",".p12",".pfx",".keystore",".apk",".aab",".pyc"}:
        errors.append(name+": excluded/private path");continue
    data=(root/p).read_bytes();total+=len(data)
    if p.suffix in {".png",".ttf",".jar"}:continue
    try:text=data.decode("utf8")
    except UnicodeDecodeError:errors.append(name+": unexpected binary");continue
    if any(pattern.search(text) for pattern in patterns):errors.append(name+": possible credential or local/private data")
for item in ["LICENSE","COPYRIGHT","TRADEMARKS.md","THIRD_PARTY_NOTICES.md","README.md","gradlew","gradle/wrapper/gradle-wrapper.jar"]:
    if item not in files:errors.append(item+": missing required file")
if errors:raise SystemExit("Public source audit FAILED:\n"+"\n".join(errors))
print(f"Public source audit passed: {len(list(filter(None,files)))} files, {total/1048576:.1f} MiB; no excluded paths or recognized private data.")

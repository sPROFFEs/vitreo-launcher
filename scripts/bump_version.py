#!/usr/bin/env python3
from pathlib import Path
import argparse
import re
from release_common import ROOT, read_version

def main() -> None:
    parser = argparse.ArgumentParser(description="Increment release version without overwriting an existing release.")
    parser.add_argument("version", help="Example: 0.1.1")
    args = parser.parse_args()
    old, old_code = read_version()
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)", args.version):
        parser.error("Expected major.minor.patch; minor/patch must be <= 99.")
    major, minor, patch = map(int, args.version.split("."))
    code = major * 10_000 + minor * 100 + patch
    if not old_code < code <= 2_100_000_000:
        parser.error("New versionCode must be greater than the old versionCode and within Android limits.")
    (ROOT / "version.properties").write_text(f"VERSION_NAME={args.version}\nVERSION_CODE={code}\n")
    print(f"{old} ({old_code}) -> {args.version} ({code}). Commit and push main to publish the new version.")
if __name__ == "__main__": main()

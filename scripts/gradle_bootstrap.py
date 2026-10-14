#!/usr/bin/env python3
"""Resolve exact Gradle, with official checksum verification before ZIP extraction."""
from __future__ import annotations
import hashlib
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "8.11.1"
SHA256 = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
URL = f"https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip"

def validate_archive(archive: Path, destination: Path) -> None:
    with archive.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest() if hasattr(hashlib, "file_digest") else hashlib.sha256(source.read()).hexdigest()
    if digest != SHA256:
        raise RuntimeError(f"Gradle checksum mismatch: {digest}; refusing to execute.")
    with zipfile.ZipFile(archive) as z:
        for item in z.infolist():
            target = (destination / item.filename).resolve()
            if not target.is_relative_to(destination.resolve()) or stat.S_ISLNK(item.external_attr >> 16):
                raise RuntimeError("Unsafe Gradle archive entry.")
        z.extractall(destination)

def resolve_gradle() -> str:
    existing = shutil.which("gradle")
    if existing:
        check = subprocess.run([existing, "--version"], text=True, capture_output=True, timeout=90)
        if check.returncode == 0 and f"Gradle {VERSION}\n" in check.stdout.replace("\r\n", "\n"):
            return existing
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))) / "vitreo-bootstrap"
    executable = cache / f"gradle-{VERSION}" / "bin" / ("gradle.bat" if os.name == "nt" else "gradle")
    if not executable.is_file():
        cache.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="download-", dir=cache) as temporary:
            temp = Path(temporary)
            archive = temp / "gradle.zip"
            print(f"Downloading Gradle {VERSION}; verifying official SHA-256.", flush=True)
            request = urllib.request.Request(URL, headers={"User-Agent": "Vitreo-build-bootstrap/0.1"})
            with urllib.request.urlopen(request, timeout=60) as source, archive.open("wb") as target:
                shutil.copyfileobj(source, target)
            validate_archive(archive, temp)
            extracted = temp / f"gradle-{VERSION}"
            (extracted / "bin" / "gradle").chmod(0o755)
            try:
                extracted.rename(cache / extracted.name)
            except OSError:
                if not executable.is_file():
                    raise
    return str(executable)

def main() -> int:
    os.chdir(ROOT)
    try:
        return subprocess.call([resolve_gradle(), *sys.argv[1:]])
    except (OSError, RuntimeError, subprocess.SubprocessError) as exc:
        print(f"Gradle bootstrap failed: {exc}", file=sys.stderr)
        return 1
if __name__ == "__main__":
    sys.exit(main())

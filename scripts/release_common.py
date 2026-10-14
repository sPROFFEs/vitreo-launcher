"""Pure release configuration helpers; shared by scripts and tests."""
from __future__ import annotations
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SIGNING_SECRETS = ("ANDROID_KEYSTORE_BASE64", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")

def read_version(path: Path | None = None) -> tuple[str, int]:
    values = {}
    for line in (path or ROOT / "version.properties").read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    name = values["VERSION_NAME"]
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)", name):
        raise ValueError("VERSION_NAME must be numeric major.minor.patch; minor/patch <= 99.")
    major, minor, patch = map(int, name.split("."))
    code = int(values["VERSION_CODE"])
    if code != major * 10_000 + minor * 100 + patch or not 1 <= code <= 2_100_000_000:
        raise ValueError("VERSION_CODE must equal major*10000 + minor*100 + patch and be within Android limits.")
    return name, code

def signing_mode(environment: dict[str, str]) -> str:
    supplied = [bool(environment.get(name, "")) for name in SIGNING_SECRETS]
    if any(supplied) and not all(supplied):
        raise ValueError("Incomplete signing secrets: set all four ANDROID_* signing secrets, or remove all for Preview.")
    return "release" if all(supplied) else "preview"

def validate_repository(value: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}", value):
        raise ValueError("Repository must be OWNER/REPOSITORY on github.com.")
    return value

def validate_ref(ref: str, version: str) -> None:
    if ref not in ("refs/heads/main", f"refs/tags/v{version}"):
        raise ValueError(f"Release requires main or a tag matching v{version}; got {ref!r}.")

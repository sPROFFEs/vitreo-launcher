#!/usr/bin/env python3
"""Check local documentation links and the screenshot provenance manifest offline."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


def prose(path: Path) -> str:
    return re.sub(r"(?ms)^(```|~~~).*?^\1[^\n]*$", "", path.read_text())


def anchors(path: Path) -> set[str]:
    result: set[str] = set()
    counts: dict[str, int] = {}
    for title in re.findall(r"(?m)^#{1,6}\s+(.+?)\s*#*\s*$", prose(path)):
        slug = re.sub(r"[^\w\- ]", "", title.lower()).replace(" ", "-")
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        result.add(slug + (f"-{count}" if count else ""))
    result.update(re.findall(r'<(?:a|h[1-6])\b[^>]*\bid="([^"]+)"', path.read_text()))
    return result


def main() -> None:
    documents = sorted([*ROOT.glob("*.md"), *(ROOT / "docs").rglob("*.md"),
                        *(ROOT / "third_party").rglob("*.md")])
    errors: list[str] = []
    references = 0
    for document in documents:
        text = prose(document)
        links = re.findall(r"\[[^\]\n]*\]\(([^\s)]+)(?:\s+[^)]*)?\)", text)
        links += re.findall(r'<(?:img|a)\b[^>]*\b(?:src|href)="([^"]+)"', text)
        for link in links:
            parsed = urlsplit(link.strip("<>"))
            if parsed.scheme or parsed.netloc:
                continue
            references += 1
            relative = unquote(parsed.path)
            target = ((ROOT / relative.lstrip("/")) if relative.startswith("/")
                      else document.parent / relative).resolve() if relative else document
            label = f"{document.relative_to(ROOT)}: {link}"
            if not target.exists():
                errors.append("Missing target: " + label)
            elif parsed.fragment and target.suffix.lower() == ".md" and unquote(parsed.fragment) not in anchors(target):
                errors.append("Missing heading: " + label)

    gallery = ROOT / "docs/screenshots"
    manifest = json.loads((gallery / "captures.json").read_text())
    records = manifest["captures"]
    if manifest.get("schema") != 1 or not records:
        errors.append("Screenshot manifest must contain schema 1 and real captures")
    seen: set[str] = set()
    identities = set()
    for entry in records:
        if entry["file"] in seen:
            errors.append("Duplicate screenshot: " + entry["file"])
        seen.add(entry["file"])
        identities.add((entry["package"], entry["versionName"], entry["versionCode"], entry["apkSha256"]))
        for filename, digest_key in [(entry["file"], "sha256"), (entry["thumbnail"], "thumbnailSha256")]:
            file = (gallery / filename).resolve()
            if not file.is_relative_to(gallery.resolve()) or not file.is_file():
                errors.append("Missing/invalid screenshot path: " + filename)
            elif hashlib.sha256(file.read_bytes()).hexdigest() != entry[digest_key]:
                errors.append("Screenshot digest mismatch: " + filename)
        if entry["width"] <= 0 or entry["height"] <= 0 or not re.fullmatch(r"[a-f0-9]{64}", entry["sourcePngSha256"]):
            errors.append("Invalid capture dimensions/source digest: " + entry["file"])
    if len(identities) != 1:
        errors.append("Screenshot gallery must use one verified APK identity")
    if seen != {p.name for p in gallery.glob("*.webp")}:
        errors.append("Screenshot manifest and full-size WebP inventory differ")
    for profile in (gallery / "profiles").glob("*.json"):
        json.loads(profile.read_text())
    if errors:
        raise RuntimeError("\n".join(errors))
    print(f"DOCS CHECKS PASSED: {len(documents)} Markdown files, {references} local links, {len(records)} verified screenshots.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)

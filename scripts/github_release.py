#!/usr/bin/env python3
"""Publish immutable release assets using GITHUB_TOKEN; never upload signing material."""
from __future__ import annotations
import hashlib
import json
import mimetypes
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from release_common import ROOT, read_version, validate_ref, validate_repository
from release_validation import validate_assets, REQUIRED_APIS

class ApiError(RuntimeError):
    def __init__(self, status: int, message: str):
        self.status = status
        super().__init__(f"GitHub HTTP {status}: {message[:500]}")

class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        parsed = urllib.parse.urlparse(newurl)
        if parsed.scheme != "https" or parsed.netloc not in ("api.github.com", "uploads.github.com"):
            raise ValueError("Refusing an off-domain authenticated redirect.")
        return super().redirect_request(req, fp, code, msg, headers, newurl)

class GitHub:
    def __init__(self, token: str):
        if not token: raise ValueError("GITHUB_TOKEN is required.")
        self.token = token
        self.opener = urllib.request.build_opener(SafeRedirect())
    def request(self, method: str, path: str, body: dict | bytes | None = None,
                content_type: str = "application/json") -> dict | list:
        url = path if path.startswith("https://") else "https://api.github.com" + path
        parsed = urllib.parse.urlparse(url)
        if parsed.scheme != "https" or parsed.netloc not in ("api.github.com", "uploads.github.com"):
            raise ValueError("Refusing to send a GitHub token to an untrusted endpoint.")
        data = json.dumps(body).encode() if isinstance(body, dict) else body
        req = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28", "Content-Type": content_type, "User-Agent": "Vitreo-release/0.1"})
        try:
            with self.opener.open(req, timeout=120) as response:
                raw = response.read()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as exc:
            raise ApiError(exc.code, exc.read(2000).decode("utf-8", errors="replace")) from exc
    def optional(self, path: str) -> dict | list | None:
        try: return self.request("GET", path)
        except ApiError as exc:
            if exc.status == 404: return None
            raise

def release_body(info: dict) -> str:
    preview = info["buildType"] == "preview"
    signing = ("**PREVIEW: signed with an ephemeral CI test key, not a production signing identity. "
        "A later Preview build can require export, uninstall and reinstall. Use private signing secrets for stable updates.**"
        if preview else "Signed with your repository's private release key. Keep that key backed up for future updates.")
    return f"""# Vitreo {info['versionName']} - experimental

{signing}

- Install `{info['apk']}`. This is a launcher APK, not a GrapheneOS system image.
- Android 12+ (API 31). Glass refraction uses AGSL on Android 13+; older/reduced mode uses a cached frosted wallpaper.
- App ID: `{info['applicationId']}`; versionCode: `{info['versionCode']}`.
- Commit: `{info['commit']}`.
- SHA-256: `{info['apkSha256']}`.
- Signer SHA-256: `{info['signingCertificateSha256']}`.

## Scope and verification
Four-column home, four-slot dock, folders, local search, locally chosen wallpaper and layout backups.
Glass samples this launcher's wallpaper only. No native widgets, icon packs, work/private profiles, replacement Recents or lock screen.
JVM tests, Android lint, build and APK signature/identity/permission checks ran in this workflow.
Publication is gated on Android API 31 and 35 instrumentation plus optimized Preview launch tests at this commit.
The signed release APK has been verified statically, not run on a device. No physical GrapheneOS testing is claimed.
Keep the stock launcher available. Export diagnostics from Settings after a problem; review reports before sharing.

Release assets are immutable. Bump version.properties for the next release; never reuse a published tag.
"""

def publish(api: GitHub, repo: str, version: str, commit: str, dist: Path) -> str:
    tag = f"v{version}"
    prefix = f"/repos/{repo}"
    validate_repository(repo)
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Invalid source commit.")
    info = validate_assets(dist, version, commit)
    if info["buildType"] != "release":
        raise ValueError("Versioned releases require private signing; Preview belongs in CI artifacts.")
    evidence = info.get("deviceTests", [])
    if len(evidence) != 2 or {e.get("api") for e in evidence} != REQUIRED_APIS:
        raise ValueError("Release requires passing API 31 and 35 device evidence.")
    if any(e.get("status") != "passed" or e.get("commit") != commit or
           e.get("debugTestsPassed", 0) < 1 or e.get("optimizedPreviewStarted") is not True for e in evidence):
        raise ValueError("Invalid or incomplete device verification evidence.")
    existing = api.optional(f"{prefix}/releases/tags/{tag}")
    if existing and existing.get("draft"):
        raise RuntimeError(f"Draft {tag} already exists. Inspect the failed draft before rerunning; it will not be overwritten.")
    # Always resolve the tag, including an already published release.
    ref = api.optional(f"{prefix}/git/ref/tags/{tag}")
    if ref:
        obj = ref["object"]
        for _ in range(5):
            if obj["type"] != "tag": break
            obj = api.request("GET", f"{prefix}/git/tags/{obj['sha']}")["object"]
        if obj["type"] != "commit" or obj["sha"] != commit:
            raise RuntimeError(f"Tag {tag} points to a different commit. Bump version.properties; never recycle release tags.")
    if existing:
        if ref is None:
            raise RuntimeError("An existing release has no verifiable tag; refusing to report success.")
        print(f"Release {tag} already exists at this exact commit; no assets were replaced.")
        return "unchanged"
    release = api.request("POST", f"{prefix}/releases", {"tag_name": tag, "target_commitish": commit,
        "name": f"Vitreo {version} ({info['buildType']})", "body": release_body(info),
        "draft": True, "prerelease": True, "make_latest": "false"})
    upload = release["upload_url"].split("{", 1)[0]
    # Publish only after every asset has uploaded successfully. Failure leaves a private draft for inspection.
    for file in sorted(dist.iterdir()):
        if not file.is_file(): continue
        url = upload + "?" + urllib.parse.urlencode({"name": file.name})
        asset = api.request("POST", url, file.read_bytes(), mimetypes.guess_type(file.name)[0] or "application/octet-stream")
        if asset.get("state") != "uploaded" or asset.get("size") != file.stat().st_size:
            raise RuntimeError(f"Incomplete upload: {file.name}. The release remains a draft.")
        digest = asset.get("digest")
        if digest != "sha256:" + hashlib.sha256(file.read_bytes()).hexdigest():
            raise RuntimeError(f"Server digest mismatch/missing for {file.name}. The release remains a draft.")
    result = api.request("PATCH", f"{prefix}/releases/{release['id']}", {"draft": False, "prerelease": True, "make_latest": "false"})
    print(f"Published {tag}: {result.get('html_url', '')}")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as file:
            file.write(f"## Vitreo {version}\n\nPublished **{info['buildType']}**: {result.get('html_url', '')}\n\nSigner: `{info['signingCertificateSha256']}`\n")
    return "published"

def main() -> None:
    version, _ = read_version()
    validate_ref(os.environ["GITHUB_REF"], version)
    repo = validate_repository(os.environ["GITHUB_REPOSITORY"])
    commit = os.environ["GITHUB_SHA"]
    if not re.fullmatch(r"[0-9a-f]{40}", commit): raise ValueError("Invalid commit SHA.")
    publish(GitHub(os.environ["GITHUB_TOKEN"]), repo, version, commit, ROOT / "dist")

if __name__ == "__main__":
    try: main()
    except Exception as exc:
        print(f"Release not published: {exc}", file=sys.stderr); sys.exit(1)

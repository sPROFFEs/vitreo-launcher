#!/usr/bin/env python3
"""Create a NEW repository, provision stable private signing, then push main.
Never accepts an existing repository, never uploads private keys as repository files.
"""
from __future__ import annotations
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
from release_common import ROOT, validate_repository

def run(args: list[str], *, text_input: str | None = None, capture: bool = False, env: dict | None = None) -> str:
    completed = subprocess.run(args, cwd=ROOT, input=text_input, text=True, check=True,
        stdout=subprocess.PIPE if capture else None, env=env)
    return completed.stdout.strip() if capture else ""

def generate_key(repo: str) -> tuple[Path, dict]:
    data_root = Path(os.environ.get("XDG_DATA_HOME", str(Path.home() / ".local" / "share")))
    directory = data_root / "vitreo" / "signing" / repo.replace("/", "__")
    if directory.resolve().is_relative_to(ROOT.resolve()):
        raise RuntimeError("Signing directory must be outside the repository.")
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.chmod(0o700)
    key = directory / "release.p12"
    metadata = directory / "secrets.json"
    if key.exists() or metadata.exists():
        if not key.is_file() or not metadata.is_file():
            raise RuntimeError(f"Incomplete existing signing material at {directory}; refusing to regenerate.")
        data = json.loads(metadata.read_text())
        for field in ("password", "alias"):
            if not data.get(field): raise RuntimeError("Invalid existing key metadata.")
        return key, data
    password = secrets.token_urlsafe(40)
    data = {"repository": repo, "alias": "vitreo", "password": password,
        "warning": "PRIVATE signing credentials. Never commit or share this directory."}
    env = dict(os.environ, VITREO_KEYGEN_PASSWORD=password)
    run(["keytool", "-genkeypair", "-keystore", str(key), "-storetype", "PKCS12",
        "-storepass:env", "VITREO_KEYGEN_PASSWORD", "-keypass:env", "VITREO_KEYGEN_PASSWORD",
        "-alias", "vitreo", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
        "-dname", "CN=Vitreo release signing"], env=env)
    key.chmod(0o600)
    descriptor = os.open(metadata, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as file: json.dump(data, file, indent=2)
    return key, data

def main() -> None:
    parser = argparse.ArgumentParser(description="Create a NEW GitHub repository and bootstrap private signed v0.1.0.")
    parser.add_argument("repository", help="OWNER/REPOSITORY")
    parser.add_argument("--public", action="store_true", help="Create publicly visible repository (default: private).")
    args = parser.parse_args()
    repo = validate_repository(args.repository)
    for tool in ("gh", "git", "keytool", "java"):
        if not shutil.which(tool): raise RuntimeError(f"Missing tool: {tool}. See BUILDING.md.")
    if (ROOT / ".git").exists():
        raise RuntimeError("This helper is only for a fresh extracted ZIP. For an existing repository, use normal git and docs/SIGNING.md.")
    # Also refuse a surrounding repository, so an extracted ZIP never alters its parent.
    surrounding = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=ROOT, capture_output=True, text=True)
    if surrounding.returncode == 0:
        raise RuntimeError("Extract outside any existing Git repository before running this helper.")
    run(["gh", "auth", "status", "--hostname", "github.com"])
    identity = json.loads(run(["gh", "api", "user"], capture=True))
    run([sys.executable, "scripts/check_project.py"])
    # gh repo create fails for existing repositories. No existing repository is changed.
    run(["gh", "repo", "create", repo, "--public" if args.public else "--private",
        "--description", "Vitreo: experimental FOSS Android launcher with original glass rendering"])
    key, credentials = generate_key(repo)
    settings = {"ANDROID_KEYSTORE_BASE64": base64.b64encode(key.read_bytes()).decode(),
        "ANDROID_KEYSTORE_PASSWORD": credentials["password"], "ANDROID_KEY_ALIAS": credentials["alias"],
        "ANDROID_KEY_PASSWORD": credentials["password"]}
    for name, value in settings.items():
        run(["gh", "secret", "set", name, "--repo", repo], text_input=value)
    run(["git", "init", "-b", "main"])
    run(["git", "add", "--all"])
    files = run(["git", "diff", "--cached", "--name-only"], capture=True).splitlines()
    if any(Path(file).suffix.lower() in (".p12", ".jks", ".keystore", ".key", ".pem") for file in files):
        raise RuntimeError("Refusing to commit private key files.")
    login = identity["login"]
    email = f"{identity['id']}+{login}@users.noreply.github.com"
    run(["git", "-c", f"user.name={login}", "-c", f"user.email={email}", "commit", "-m", "Initial Vitreo 0.1.0 source"])
    run(["git", "remote", "add", "origin", f"https://github.com/{repo}.git"])
    run(["git", "-c", "credential.https://github.com.helper=", "-c", "credential.https://github.com.helper=!gh auth git-credential",
        "push", "-u", "origin", "main"])
    print(f"\nSource pushed to https://github.com/{repo}")
    print(f"Follow Actions -> Release APK; a successful build publishes Releases/v0.1.0.")
    print(f"BACK UP PRIVATE SIGNING DIRECTORY OFFLINE: {key.parent}")
    print("The key and its password are stored together there with owner-only permissions; protect and encrypt your backup.")

if __name__ == "__main__":
    try: main()
    except Exception as exc:
        print(f"Bootstrap stopped: {exc}\nNo existing release was overwritten. Inspect any newly created empty repository before retrying.", file=sys.stderr)
        sys.exit(1)

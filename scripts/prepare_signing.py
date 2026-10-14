#!/usr/bin/env python3
"""Materialize a private key only in RUNNER_TEMP, or explicitly select Preview."""
from __future__ import annotations
import base64
import binascii
import os
from pathlib import Path
import sys
import tempfile
from release_common import SIGNING_SECRETS, signing_mode

def append_environment(path: str, values: dict[str, str]) -> None:
    with open(path, "a", encoding="utf-8") as out:
        for key, value in values.items():
            if "\n" in value or "\r" in value:
                raise ValueError(f"Newlines not supported in {key}.")
            out.write(f"{key}={value}\n")

def clean() -> None:
    name = os.environ.get("VITREO_STORE_FILE")
    if name:
        path = Path(name).resolve()
        runner = Path(os.environ["RUNNER_TEMP"]).resolve()
        if path.parent == runner and path.name.startswith("vitreo-signing-"):
            path.unlink(missing_ok=True)

def main() -> None:
    if sys.argv[1:] == ["--clean"]:
        clean(); return
    mode = signing_mode(dict(os.environ))
    if "--require-private" in sys.argv[1:] and mode != "release":
        raise ValueError("A release requires all four signing secrets: " + ", ".join(SIGNING_SECRETS) +
            ". Configure them in Settings > Secrets and variables > Actions. "
            "Preview builds are CI artifacts, not versioned releases.")
    if "--check-only" in sys.argv[1:]:
        print(f"Signing configuration present for {mode}; no key material written.")
        return
    if mode == "release":
        try:
            contents = base64.b64decode("".join(os.environ["ANDROID_KEYSTORE_BASE64"].split()), validate=True)
        except binascii.Error as exc:
            raise ValueError("ANDROID_KEYSTORE_BASE64 is not valid base64.") from exc
        if not 64 <= len(contents) <= 1_048_576:
            raise ValueError("Unexpected keystore size.")
        values = {
            "VITREO_STORE_PASSWORD": os.environ["ANDROID_KEYSTORE_PASSWORD"],
            "VITREO_KEY_ALIAS": os.environ["ANDROID_KEY_ALIAS"],
            "VITREO_KEY_PASSWORD": os.environ["ANDROID_KEY_PASSWORD"],
        }
        if any("\n" in value or "\r" in value for value in values.values()):
            raise ValueError("Signing secrets must be single-line strings.")
        with tempfile.NamedTemporaryFile(prefix="vitreo-signing-", suffix=".p12", dir=os.environ["RUNNER_TEMP"], delete=False) as key:
            key.write(contents)
        os.chmod(key.name, 0o600)
        values["VITREO_STORE_FILE"] = key.name
        append_environment(os.environ["GITHUB_ENV"], values)
    else:
        print("::warning::No private signing secrets. Building separate Vitreo Preview with an ephemeral CI key. Subsequent Preview builds may require reinstalling.")
    append_environment(os.environ["GITHUB_OUTPUT"], {"mode": mode, "variant": mode.capitalize()})
    print(f"Selected build type: {mode}; no key material will be uploaded.")

if __name__ == "__main__":
    try: main()
    except Exception as exc:
        print(f"Signing preparation failed: {exc}", file=sys.stderr); sys.exit(1)

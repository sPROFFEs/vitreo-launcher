#!/usr/bin/env sh
# Text-only bootstrap. No prebuilt wrapper JAR is bundled; see BUILDING.md.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec python3 "$ROOT/scripts/gradle_bootstrap.py" "$@"

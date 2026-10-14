#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
kotlinc "$ROOT"/app/src/main/java/dev/vitreo/launcher/core/*.kt   "$ROOT/app/src/test/java/dev/vitreo/launcher/core/CoreScenarios.kt"   -include-runtime -d "$TEMP/core-tests.jar"
java -jar "$TEMP/core-tests.jar"

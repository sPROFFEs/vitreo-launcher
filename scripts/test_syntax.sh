#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
KOTLIN_ROOT=${KOTLIN_HOME:-}
if [[ -z "$KOTLIN_ROOT" ]]; then
  echo 'Set KOTLIN_HOME to a Kotlin command-line compiler installation.' >&2; exit 1
fi
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
"$KOTLIN_ROOT/bin/kotlinc" "$ROOT/tools/KotlinSyntaxCheck.kt" \
  -cp "$KOTLIN_ROOT/lib/kotlin-compiler.jar" -d "$TEMP/parser.jar"
java -cp "$TEMP/parser.jar:$KOTLIN_ROOT/lib/*" KotlinSyntaxCheckKt "$ROOT"

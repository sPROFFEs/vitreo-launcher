#!/usr/bin/env bash
set -euo pipefail
SDK_ROOT=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z "$SDK_ROOT" ]]; then
  echo 'ANDROID_HOME/ANDROID_SDK_ROOT is not configured.' >&2; exit 1
fi
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then SDKMANAGER=$(command -v sdkmanager); fi
# Accept the Android SDK licenses in the CI build environment. Do not mask sdkmanager failures.
set +o pipefail
yes | "$SDKMANAGER" --licenses >/dev/null
set -o pipefail
"$SDKMANAGER" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'

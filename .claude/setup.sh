#!/usr/bin/env bash
set -euo pipefail

CMDLINE_TOOLS_REVISION="13114758"
BUILD_TOOLS_VERSION="36.0.0"
COMPILE_SDK_FILE="app/build.gradle.kts"

if [ -n "${CLAUDE_PROJECT_DIR:-}" ] && [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"

sdk_dir_in_local_properties=""
if [ -f local.properties ]; then
  sdk_dir_in_local_properties="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' local.properties | tail -n 1)"
fi
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${sdk_dir_in_local_properties:-${HOME}/android-sdk}}}"
SDKMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

compile_sdk_major="$(sed -n -E \
  -e 's/^[[:space:]]*(androidCompileSdk|compileSdk)[[:space:]]*=[[:space:]]*"?([0-9]+)"?[[:space:]]*$/\2/p' \
  -e 's/.*compileSdkVersion\("android-([0-9]+)\.[0-9]+"\).*/\1/p' \
  "${COMPILE_SDK_FILE}" | head -n 1)"
compile_sdk_minor="$(sed -n -E \
  -e 's/^[[:space:]]*(androidCompileSdkMinor|compileSdkMinor)[[:space:]]*=[[:space:]]*"?([0-9]+)"?[[:space:]]*$/\2/p' \
  -e 's/.*compileSdkVersion\("android-[0-9]+\.([0-9]+)"\).*/\1/p' \
  "${COMPILE_SDK_FILE}" | head -n 1)"
if [ -z "${compile_sdk_major}" ]; then
  echo "${COMPILE_SDK_FILE} から compileSdk を読めなかった" >&2
  exit 1
fi
compile_sdk_minor="${compile_sdk_minor:-0}"
if [ "${compile_sdk_major}" -le 36 ] && [ "${compile_sdk_minor}" = 0 ]; then
  platform="android-${compile_sdk_major}"
else
  platform="android-${compile_sdk_major}.${compile_sdk_minor}"
fi

if [ ! -f "${ANDROID_SDK_ROOT}/platforms/${platform}/android.jar" ] ||
  [ ! -d "${ANDROID_SDK_ROOT}/build-tools/${BUILD_TOOLS_VERSION}" ] ||
  [ ! -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]; then
  if [ ! -x "${SDKMANAGER}" ]; then
    echo "[setup] cmdline-tools を導入する"
    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
    tmp="$(mktemp -d)"
    trap 'rm -rf "${tmp}"' EXIT
    for attempt in 1 2 3 4; do
      if curl -fsSL -o "${tmp}/cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_REVISION}_latest.zip" &&
        unzip -tq "${tmp}/cmdline-tools.zip" > /dev/null; then
        break
      fi
      if [ "${attempt}" = 4 ]; then
        echo "cmdline-tools を取得できなかった" >&2
        exit 1
      fi
    done
    unzip -q "${tmp}/cmdline-tools.zip" -d "${tmp}"
    rm -rf "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
    mv "${tmp}/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  fi

  echo "[setup] Android SDK パッケージを導入する (${platform})"
  { for _ in $(seq 1 200); do printf 'y\n'; done; } | "${SDKMANAGER}" --sdk_root="${ANDROID_SDK_ROOT}" --licenses > /dev/null
  "${SDKMANAGER}" --sdk_root="${ANDROID_SDK_ROOT}" --install \
    "platform-tools" \
    "platforms;${platform}" \
    "build-tools;${BUILD_TOOLS_VERSION}" > /dev/null
fi

tmp_local_properties="$(mktemp ./.local.properties.XXXXXX)"
if [ -f local.properties ]; then
  grep -v -E '^[[:space:]]*sdk\.dir[[:space:]]*=' local.properties > "${tmp_local_properties}" || true
fi
echo "sdk.dir=${ANDROID_SDK_ROOT}" >> "${tmp_local_properties}"
mv "${tmp_local_properties}" local.properties

android_user_home="${ANDROID_USER_HOME:-${HOME}/.android}"
if [ ! -f "${android_user_home}/debug.keystore" ]; then
  echo "[setup] debug.keystore を作る"
  mkdir -p "${android_user_home}"
  keytool -genkeypair -keystore "${android_user_home}/debug.keystore" \
    -storepass android -alias androiddebugkey -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" > /dev/null
fi

echo "[setup] 完了"

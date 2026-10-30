#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT=/opt/android-sdk
CMDLINE_URL=https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
CMDLINE_SHA=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/android" ]]; then
  tmp=$(mktemp -d)
  curl -fL --retry 3 -o "$tmp/cmdtools.zip" "$CMDLINE_URL"
  echo "$CMDLINE_SHA  $tmp/cmdtools.zip" | sha256sum -c -
  unzip -q "$tmp/cmdtools.zip" -d "$tmp"
  sudo mkdir -p "$SDK_ROOT/cmdline-tools/latest"
  sudo mv "$tmp/cmdline-tools/"* "$SDK_ROOT/cmdline-tools/latest/"
  sudo chown -R ubuntu:ubuntu "$SDK_ROOT"
  rm -rf "$tmp"
fi

sudo tee /etc/profile.d/android-sdk.sh >/dev/null << 'EOF'
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF

write_env() {
  local key="$1"
  local value="$2"
  if sudo grep -q "^${key}=" /etc/environment; then
    sudo sed -i "s#^${key}=.*#${key}=${value}#" /etc/environment
  else
    echo "${key}=${value}" | sudo tee -a /etc/environment >/dev/null
  fi
}

write_env ANDROID_HOME "$SDK_ROOT"
write_env ANDROID_SDK_ROOT "$SDK_ROOT"
if sudo grep -q '^JAVA_HOME=' /etc/environment; then
  sudo sed -i '/^JAVA_HOME=/d' /etc/environment
fi

unset JAVA_HOME
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

"$SDK_ROOT/cmdline-tools/latest/bin/android" --sdk="$SDK_ROOT" sdk install --canary \
  "platforms/android-37.0" \
  "build-tools/36.0.0" \
  "build-tools/37.0.0" \
  "platform-tools"

printf 'sdk.dir=%s\n' "$SDK_ROOT" > local.properties

./gradlew assembleDebug ktlintCheck detekt --no-daemon

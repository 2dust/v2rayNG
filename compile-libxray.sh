#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset

__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

read_prop_value() {
  local file="$1"
  local key="$2"
  [[ -f "$file" ]] || return 1
  awk -v key="$key" '
    /^[[:space:]]*#/ { next }
    {
      line = $0
      sub(/^[[:space:]]+/, "", line)
      eq = index(line, "=")
      if (eq == 0) next
      k = substr(line, 1, eq - 1)
      sub(/[[:space:]]+$/, "", k)
      if (k != key) next
      val = substr(line, eq + 1)
      sub(/^[[:space:]]+/, "", val)
      sub(/\r$/, "", val)
      print val
      exit
    }
  ' "$file"
}

if [[ -z "${ANDROID_HOME:-}" ]]; then
  ANDROID_HOME="$(read_prop_value "$__dir/V2rayNG/local.properties" "sdk.dir" || true)"
fi
if [[ -z "${ANDROID_HOME:-}" || ! -d "$ANDROID_HOME" ]]; then
  echo "Android SDK: set ANDROID_HOME or sdk.dir in V2rayNG/local.properties"
  exit 1
fi
export ANDROID_HOME

NDK_VER="$(read_prop_value "$__dir/V2rayNG/gradle.properties" "v2rayN.ndkVersion" || true)"
NDK_VER="${NDK_VER:-28.2.13676358}"

if [[ -n "${NDK_HOME:-}" && -d "$NDK_HOME" ]]; then
  export ANDROID_NDK_HOME="$NDK_HOME"
elif [[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME" ]]; then
  :
else
  export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VER"
fi

if [[ ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "Android NDK: not found at $ANDROID_NDK_HOME. Install NDK $NDK_VER, or set NDK_HOME / ANDROID_NDK_HOME."
  exit 1
fi

command -v go >/dev/null 2>&1 || { echo "Go is required but not in PATH."; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "jq is required for gen_assets.sh."; exit 1; }

export PATH="$(go env GOPATH)/bin:$PATH"
command -v gomobile >/dev/null 2>&1 || go install golang.org/x/mobile/cmd/gomobile@latest
command -v gobind >/dev/null 2>&1 || go install golang.org/x/mobile/cmd/gobind@latest

pushd "$__dir/AndroidLibXrayLite" >/dev/null
mkdir -p assets data
bash gen_assets.sh download
cp -f data/*.dat assets/
gomobile init
go mod tidy
gomobile bind -v -androidapi 24 -trimpath -ldflags='-s -w -buildid=' ./
popd >/dev/null

mkdir -p "$__dir/V2rayNG/app/libs"
cp -f "$__dir/AndroidLibXrayLite/libv2ray.aar" "$__dir/V2rayNG/app/libs/"

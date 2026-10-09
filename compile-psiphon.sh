#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Builds the Psiphon client the Aether core runs for a profile with Psiphon. The core does not
# contain Psiphon: it starts the psiphon-tunnel-core console client as a program of its own, and
# the app tells it where that program is (AETHER_PSIPHON_BIN). aether's own psiphon-build.sh does
# the building, from the fork and commit it pins, so the client matches the core.
#
# The client is a static GOOS=linux binary, the way aether's Android release builds it: a
# GOOS=android build needs cgo and the NDK, while a CGO_ENABLED=0 linux ELF runs as it is on
# Android. It is staged as libpsiphon-tunnel-core.so beside libaether.so, so that the installer
# extracts it next to the core with execute permission.

if ! command -v go >/dev/null 2>&1; then
  echo "Go: go not found. install the Go toolchain"
  exit 1
fi

BUILD_SCRIPT="$__dir/aether/psiphon-build.sh"
if [[ ! -f "$BUILD_SCRIPT" ]]; then
  echo "Aether sources not found at $__dir/aether"
  echo "run: git submodule update --init --recursive"
  exit 1
fi

# The same ABIs the core is built for; see compile-aether.sh.
ABIS="armeabi-v7a arm64-v8a x86_64"

goarch_for () {
  case "$1" in
    armeabi-v7a) echo "arm" ;;
    arm64-v8a)   echo "arm64" ;;
    x86_64)      echo "amd64" ;;
    *) echo "unsupported abi: $1" >&2; exit 1 ;;
  esac
}

goarm_for () {
  case "$1" in
    armeabi-v7a) echo "7" ;;
    *)           echo "" ;;
  esac
}

OUT_DIR="$__dir/libs-psiphon"
mkdir -p "$OUT_DIR"

for abi in $ABIS; do
  goarch="$(goarch_for "$abi")"
  goarm="$(goarm_for "$abi")"
  stage="$(mktemp -d)"

  echo "[psiphon] building the client for $abi (linux/$goarch${goarm:+ v$goarm})"
  bash "$BUILD_SCRIPT" linux "$goarch" "$stage" "$goarm"

  produced="$stage/psiphon-tunnel-core"
  if [[ ! -f "$produced" ]]; then
    echo "psiphon client missing for $abi: $produced"
    exit 1
  fi

  mkdir -p "$OUT_DIR/$abi"
  cp "$produced" "$OUT_DIR/$abi/libpsiphon-tunnel-core.so"
  rm -rf "$stage"
done

echo "[psiphon] staged:"
for abi in $ABIS; do
  ls -la "$OUT_DIR/$abi/libpsiphon-tunnel-core.so"
done

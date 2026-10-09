#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename ${__file} .sh)"
if [[ ! -d $NDK_HOME ]]; then
  echo "Android NDK: NDK_HOME not found. please set env \$NDK_HOME"
  exit 1
fi
if ! command -v cargo >/dev/null 2>&1; then
  echo "Rust: cargo not found. install the Rust toolchain"
  exit 1
fi

CORE_DIR="$__dir/aether/aether"
if [[ ! -f "$CORE_DIR/Cargo.toml" ]]; then
  echo "Aether core sources not found at $CORE_DIR"
  echo "run: git submodule update --init --recursive"
  exit 1
fi

# 32-bit x86 is left out on purpose: aether's own release builds do not cover
# i686-linux-android, so that target is unverified. The app hides the Aether
# feature on an ABI that ships without the binary.
ABIS="armeabi-v7a arm64-v8a x86_64"
API_LEVEL=29

triple_for () {
  case "$1" in
    armeabi-v7a) echo "armv7-linux-androideabi" ;;
    arm64-v8a)   echo "aarch64-linux-android" ;;
    x86)         echo "i686-linux-android" ;;
    x86_64)      echo "x86_64-linux-android" ;;
    *) echo "unsupported abi: $1" >&2; exit 1 ;;
  esac
}

clang_for () {
  case "$1" in
    armeabi-v7a) echo "armv7a-linux-androideabi$API_LEVEL" ;;
    *)           echo "$(triple_for "$1")$API_LEVEL" ;;
  esac
}

case "$(uname -s)" in
  Darwin) HOST_TAG_CANDIDATES="darwin-arm64 darwin-x86_64" ;;
  *)      HOST_TAG_CANDIDATES="linux-x86_64 linux-arm64" ;;
esac

TOOLCHAIN=""
for candidate in $HOST_TAG_CANDIDATES; do
  if [[ -d "$NDK_HOME/toolchains/llvm/prebuilt/$candidate/bin" ]]; then
    TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/$candidate/bin"
    break
  fi
done
if [[ -z "$TOOLCHAIN" ]]; then
  echo "no llvm toolchain under $NDK_HOME/toolchains/llvm/prebuilt"
  exit 1
fi
SYSROOT="$(cd "$TOOLCHAIN/../sysroot" && pwd)"

OUT_DIR="$__dir/libs-aether"
mkdir -p "$OUT_DIR"

for abi in $ABIS; do
  triple="$(triple_for "$abi")"
  clang_target="$(clang_for "$abi")"
  env_triple="$(echo "$triple" | tr 'a-z-' 'A-Z_')"
  under_triple="$(echo "$triple" | tr '-' '_')"

  if [[ ! -x "$TOOLCHAIN/$clang_target-clang" ]]; then
    echo "NDK clang not found: $TOOLCHAIN/$clang_target-clang"
    exit 1
  fi

  rustup target add "$triple" >/dev/null 2>&1 || true

  release_dir="$CORE_DIR/target/$triple/release"
  stale=0
  for candidate in "$release_dir"/build/boring-sys-*; do
    [[ -d "$candidate" ]] || continue
    out="$candidate/out/build"
    [[ -d "$out" ]] || continue
    if [[ ! -f "$out/libssl.a" || ! -f "$out/libcrypto.a" || ! -f "$out/CMakeCache.txt" ]]; then
      stale=1
      break
    fi
    recorded="$(sed -n 's/^CMAKE_C_COMPILER:[^=]*=//p' "$out/CMakeCache.txt" | head -1)"
    if [[ -n "$recorded" && "$recorded" != "$TOOLCHAIN/clang" ]]; then
      stale=1
      break
    fi
  done
  if [[ "$stale" == "1" ]]; then
    echo "[aether] the boringssl cache for $abi is stale, rebuilding it"
    rm -rf "$release_dir"/build/boring-sys-* "$release_dir"/.fingerprint/boring-sys-*
  fi

  # The tor feature embeds arti, which is what lets a profile put Tor inside or around the tunnel;
  # aether's own Android release is built with it as well.
  #
  # The C++ runtime BoringSSL needs is linked statically. boring-sys 5 links it as "c++" on Android, which the
  # NDK resolves to libc++_shared.so, a library Android does not ship: the core, run as a program from the
  # app's library folder, then cannot start ("library "libc++_shared.so" not found"). As a static library,
  # "c++" is the NDK's libc++.a, which takes in libc++_static and libc++abi. Drop the setting once boring-sys
  # links the runtime statically on Android itself, or once the app ships libc++_shared.so where the core
  # finds it.
  echo "[aether] building the core for $abi ($triple)"
  env \
    ANDROID_NDK_HOME="$NDK_HOME" \
    ANDROID_NDK_ROOT="$NDK_HOME" \
    "CARGO_TARGET_${env_triple}_LINKER=$TOOLCHAIN/$clang_target-clang" \
    "CARGO_TARGET_${env_triple}_RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384" \
    "CC_${under_triple}=$TOOLCHAIN/clang" \
    "CXX_${under_triple}=$TOOLCHAIN/clang++" \
    "CFLAGS_${under_triple}=--target=$clang_target" \
    "CXXFLAGS_${under_triple}=--target=$clang_target" \
    "AR_${under_triple}=$TOOLCHAIN/llvm-ar" \
    "BINDGEN_EXTRA_CLANG_ARGS_${under_triple}=--target=$clang_target --sysroot=$SYSROOT" \
    "BORING_BSSL_RUST_CPPLIB_${under_triple}=static:-bundle=c++" \
    cargo build --release --locked --features tor --manifest-path "$CORE_DIR/Cargo.toml" --target "$triple" --bin aether

  produced="$CORE_DIR/target/$triple/release/aether"
  if [[ ! -f "$produced" ]]; then
    echo "aether binary missing for $abi: $produced"
    exit 1
  fi

  # Only Android's own libraries are found when the core starts; the NDK's C++ runtime is not one of them.
  needed="$("$TOOLCHAIN/llvm-readelf" --dynamic-table "$produced" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p')"
  echo "[aether] the core for $abi needs:" $needed
  if grep -qx 'libc++_shared.so' <<< "$needed"; then
    echo "the core for $abi needs libc++_shared.so, which Android does not ship"
    exit 1
  fi

  mkdir -p "$OUT_DIR/$abi"
  cp "$produced" "$OUT_DIR/$abi/libaether.so"
done

echo "[aether] staged:"
for abi in $ABIS; do
  ls -la "$OUT_DIR/$abi/libaether.so"
done

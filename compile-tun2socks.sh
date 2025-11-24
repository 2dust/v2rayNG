#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset

# Set magic variables for current file & dir
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename ${__file} .sh)"

if [[ ! -d $NDK_HOME ]]; then
    echo "Android NDK: NDK_HOME not found. please set env \$NDK_HOME"
    exit 1
fi

# Create output directory
mkdir -p "$__dir/libs"

# macOS detection and optimization
if [[ "$(uname)" == "Darwin" ]]; then
    echo "Detected macOS, applying optimizations..."
    export DISABLE_prelink=1
    export MACOSX_DEPLOYMENT_TARGET=10.15
    export MAKEFLAGS="-j1"
    export NINJAFLAGS="-j1"
    JOB_COUNT=1
else
    JOB_COUNT=4
fi

# Memory monitoring function
check_memory() {
    if [[ "$(uname)" == "Darwin" ]] && command -v vm_stat >/dev/null 2>&1; then
        echo "=== Memory Status ==="
        vm_stat | grep -E "(Pages free|Pages active|Pages wired)"
        echo "===================="
    fi
}

# Cleanup function
cleanup_temp() {
    local temp_dir="$1"
    if [[ -d "$temp_dir" ]]; then
        rm -rf "$temp_dir"
    fi
    
    # macOS memory cleanup
    if [[ "$(uname)" == "Darwin" ]] && command -v purge >/dev/null 2>&1; then
        sudo purge 2>/dev/null || true
    fi
}

# Build tun2socks
build_tun2socks() {
    echo "=== Building tun2socks ==="
    check_memory
    
    local TMPDIR=$(mktemp -d)
    trap 'cleanup_temp "$TMPDIR"' EXIT
    
    install -m644 "$__dir/tun2socks.mk" "$TMPDIR/"
    pushd "$TMPDIR" >/dev/null
    ln -s "$__dir/badvpn" badvpn
    ln -s "$__dir/libancillary" libancillary
    
    "$NDK_HOME/ndk-build" \
        NDK_PROJECT_PATH=. \
        APP_BUILD_SCRIPT=./tun2socks.mk \
        APP_ABI=all \
        APP_PLATFORM=android-21 \
        NDK_LIBS_OUT="$TMPDIR/libs" \
        NDK_OUT="$TMPDIR/tmp" \
        APP_SHORT_COMMANDS=false \
        LOCAL_SHORT_COMMANDS=false \
        -B -j$JOB_COUNT
    
    cp -r "$TMPDIR/libs" "$__dir/"
    popd >/dev/null
    
    cleanup_temp "$TMPDIR"
    trap - EXIT
    sleep 2
}

# Build hev-socks5-tunnel
build_hev_socks5_tunnel() {
    echo "=== Building hev-socks5-tunnel ==="
    check_memory
    
    local HEVTUN_TMP=$(mktemp -d)
    trap 'cleanup_temp "$HEVTUN_TMP"' EXIT
    
    mkdir -p "$HEVTUN_TMP/jni"
    pushd "$HEVTUN_TMP" >/dev/null
    
    echo 'include $(call all-subdir-makefiles)' > jni/Android.mk
    ln -s "$__dir/hev-socks5-tunnel" jni/hev-socks5-tunnel
    
    local abis=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")
    
    for abi in "${abis[@]}"; do
        echo "Building ABI: $abi"
        
        "$NDK_HOME/ndk-build" \
            NDK_PROJECT_PATH=. \
            APP_BUILD_SCRIPT=jni/Android.mk \
            "APP_ABI=$abi" \
            APP_PLATFORM=android-21 \
            NDK_LIBS_OUT="$HEVTUN_TMP/libs" \
            NDK_OUT="$HEVTUN_TMP/obj" \
            "APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service" \
            "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu" \
            -j$JOB_COUNT
        
        sleep 1
    done
    
    cp -r "$HEVTUN_TMP/libs/"* "$__dir/libs/"
    popd >/dev/null
    
    cleanup_temp "$HEVTUN_TMP"
    trap - EXIT
}

main() {
    echo "Starting build process..."
    
    build_tun2socks
    build_hev_socks5_tunnel
    
    echo "All builds completed successfully!"
    echo "Output directory: $__dir/libs/"
}

# Run main
main "$@"

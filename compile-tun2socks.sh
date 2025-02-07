#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset

# Set magic variables
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename ${__file} .sh)"

# Constants
NDK_HOME_DIR="$NDK_HOME"
APP_PLATFORM_VERSION="android-19"
SOURCE_DIR="/home/runner/work/v2rayNG/v2rayNG/libs"
DEST_DIR="/home/runner/work/v2rayNG/v2rayNG/V2rayNG/app"

# Check if NDK_HOME is set
if [[ -z "$NDK_HOME_DIR" ]]; then
    echo "Android NDK: NDK_HOME not found. Please set env \$NDK_HOME"
    exit 1
fi

# Check if ndk-build is available
if ! command -v ndk-build &> /dev/null; then
    echo "ndk-build command not found. Please ensure NDK is properly configured."
    exit 1
fi

# Create temporary directory
TMPDIR=$(mktemp -d -t tun2socks_build.XXXXXXXXXX)

# Cleanup function
clear_tmp() {
    rm -rf "$TMPDIR"
}
trap 'echo -e "Aborted, error $? in command: $BASH_COMMAND"; clear_tmp; exit 1' ERR INT

# Copy necessary files
install -m644 "$__dir/tun2socks.mk" "$TMPDIR/"
pushd "$TMPDIR"
ln -s "$__dir/badvpn" badvpn
ln -s "$__dir/libancillary" libancillary

# NDK Build arguments
NDK_BUILD_ARGS=(
    NDK_PROJECT_PATH=.
    APP_BUILD_SCRIPT=./tun2socks.mk
    APP_ABI=all
    APP_PLATFORM="$APP_PLATFORM_VERSION"
    NDK_LIBS_OUT=$TMPDIR/libs
    NDK_OUT=$TMPDIR/tmp
    APP_SHORT_COMMANDS=false
    LOCAL_SHORT_COMMANDS=false
    -B
    -j$(nproc)
    LOCAL_LDFLAGS=-Wl,--build-id=none
)

# Run NDK build
echo "Starting NDK build..."
"$NDK_HOME_DIR"/ndk-build "${NDK_BUILD_ARGS[@]}"

# Remove unnecessary files
echo "Cleaning up temporary files..."
find "$TMPDIR" -name "*.o" -delete
find "$TMPDIR" -name "*.d" -delete

# Compress the library for each ABI
echo "Compressing libraries..."
for ABI in armeabi-v7a arm64-v8a x86 x86_64; do
    if [[ -f "$TMPDIR/libs/$ABI/libtun2socks.so" ]]; then
        pushd "$TMPDIR/libs/$ABI"
        gzip libtun2socks.so
        mv libtun2socks.so.gz "$__dir/libtun2socks_$ABI.so.gz"
        popd
    fi
done

# Create a tarball containing all ABI libraries
echo "Creating tarball..."
tar -czvf "$__dir/libtun2socks.so.tgz" -C "$TMPDIR/libs" .

# Clean up
echo "Cleaning up temporary directory..."
popd
clear_tmp

# Check and create directories
if [ ! -d "$SOURCE_DIR" ]; then
    echo "Source directory does not exist. Creating..."
    mkdir -p "$SOURCE_DIR"
else
    echo "Source directory exists."
fi

if [ ! -d "$DEST_DIR" ]; then
    echo "Destination directory does not exist. Creating..."
    mkdir -p "$DEST_DIR"
else
    echo "Destination directory exists."
fi

# Copy the directory
echo "Copying libraries..."
cp -r "$SOURCE_DIR" "$DEST_DIR"

echo "Build completed. Libraries are in $__dir and copied to $DEST_DIR"

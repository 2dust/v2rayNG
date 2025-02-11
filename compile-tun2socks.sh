#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset

# Set magic variables for current file & dir
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename "${__file}" .sh)"

# Check if NDK_HOME is set
if [[ ! -d "${NDK_HOME}" ]]; then
    echo "Android NDK: NDK_HOME not found. please set env \$NDK_HOME"
    exit 1
fi

# Create a temporary directory
TMPDIR=$(mktemp -d)
clear_tmp () {
    rm -rf "${TMPDIR}"
}
trap 'echo -e "Aborted, error $? in command: $BASH_COMMAND"; clear_tmp; exit 1' ERR INT

# Copy tun2socks.mk to the temporary directory
install -m644 "${__dir}/tun2socks.mk" "${TMPDIR}/"
pushd "${TMPDIR}"

# Create symbolic links
ln -s "${__dir}/badvpn" badvpn
ln -s "${__dir}/libancillary" libancillary

# Build with ndk-build
"${NDK_HOME}/ndk-build" \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=./tun2socks.mk \
    APP_ABI=all \
    APP_PLATFORM=android-19 \
    NDK_LIBS_OUT="${TMPDIR}/libs" \
    NDK_OUT="${TMPDIR}/tmp" \
    APP_SHORT_COMMANDS=false LOCAL_SHORT_COMMANDS=false -B -j4 \
    LOCAL_LDFLAGS=-Wl,--build-id=none

# Create a tarball of the built libraries
tar cvfz "${__dir}/libtun2socks.so.tgz" libs
popd

# Clean up temporary directory
clear_tmp

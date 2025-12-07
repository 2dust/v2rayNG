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
TMPDIR=$(mktemp -d)
clear_tmp () {
  rm -rf $TMPDIR
}
trap 'echo -e "Aborted, error $? in command: $BASH_COMMAND"; trap ERR; clear_tmp; exit 1' ERR INT

#build hev-socks5-tunnel
mkdir -p "$TMPDIR/jni"
pushd "$TMPDIR"

echo 'include $(call all-subdir-makefiles)' > jni/Android.mk

ln -s "$__dir/hev-socks5-tunnel" jni/hev-socks5-tunnel

"$NDK_HOME/ndk-build" \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=jni/Android.mk \
	"APP_ABI=armeabi-v7a arm64-v8a x86 x86_64" \
	APP_PLATFORM=android-21 \
    NDK_LIBS_OUT="$TMPDIR/libs" \
    NDK_OUT="$TMPDIR/obj" \
    "APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service" \
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu" \

cp -r "$TMPDIR/libs/"* "$__dir/libs/"

popd
rm -rf $TMPDIR

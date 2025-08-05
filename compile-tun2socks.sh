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
install -m644 $__dir/tun2socks.mk $TMPDIR/
pushd $TMPDIR
ln -s $__dir/badvpn badvpn
ln -s $__dir/libancillary libancillary
$NDK_HOME/ndk-build \
	NDK_PROJECT_PATH=. \
	APP_BUILD_SCRIPT=./tun2socks.mk \
	APP_ABI=all \
	APP_PLATFORM=android-21 \
	NDK_LIBS_OUT=$TMPDIR/libs \
	NDK_OUT=$TMPDIR/tmp \
	APP_SHORT_COMMANDS=false LOCAL_SHORT_COMMANDS=false -B -j4
cp -r $TMPDIR/libs $__dir/
popd
rm -rf $TMPDIR

#build hev-socks5-tunnel
HEVTUN_TMP=$(mktemp -d)
trap 'rm -rf "$HEVTUN_TMP"' EXIT

mkdir -p "$HEVTUN_TMP/jni"
pushd "$HEVTUN_TMP"

echo 'include $(call all-subdir-makefiles)' > jni/Android.mk

ln -s "$__dir/hev-socks5-tunnel" jni/hev-socks5-tunnel

"$NDK_HOME/ndk-build" \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=jni/Android.mk \
	"APP_ABI=armeabi-v7a arm64-v8a x86 x86_64" \
	APP_PLATFORM=android-21 \
    NDK_LIBS_OUT="$HEVTUN_TMP/libs" \
    NDK_OUT="$HEVTUN_TMP/obj" \
    "APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service" \
    "APP_LDFLAGS=-WI,--build-id=none -WI,--hash-style=gnu" \

cp -r "$HEVTUN_TMP/libs/"* "$__dir/libs/"
popd

rm -rf "$HEVTUN_TMP"

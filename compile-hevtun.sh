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

ABIS="armeabi-v7a arm64-v8a x86 x86_64"

mkdir -p "$TMPDIR/jni"
pushd "$TMPDIR"

ln -s "$__dir/hev-socks5-tunnel" jni/hev-socks5-tunnel

# 1) JNI shared library (libhev-socks5-tunnel.so) — loaded in-process by
#    com.v2ray.ang.service.TProxyService for the VpnService hev tun mode.
echo 'include $(call all-subdir-makefiles)' > jni/Android.mk

"$NDK_HOME/ndk-build" \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=jni/Android.mk \
    "APP_ABI=$ABIS" \
    APP_PLATFORM=android-24 \
    NDK_LIBS_OUT="$TMPDIR/libs" \
    NDK_OUT="$TMPDIR/obj" \
    "APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service" \
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu" \

# 2) Standalone executable (libhevsockstun.so) — run as a separate root
#    process by com.v2ray.ang.core.root for the Root run mode. Same hev source,
#    no -DENABLE_LIBRARY so hev-main.c's main() is built, and BUILD_EXECUTABLE
#    instead of a shared library. It creates its own tun and reads a YAML config.
cat > jni/exec.mk <<'EXECMK'
TOP_PATH := $(call my-dir)/hev-socks5-tunnel

ifeq ($(filter $(modules-get-list),yaml),)
    include $(TOP_PATH)/third-part/yaml/Android.mk
endif
ifeq ($(filter $(modules-get-list),lwip),)
    include $(TOP_PATH)/third-part/lwip/Android.mk
endif
ifeq ($(filter $(modules-get-list),hev-task-system),)
    include $(TOP_PATH)/third-part/hev-task-system/Android.mk
endif

LOCAL_PATH := $(TOP_PATH)
SRCDIR := $(LOCAL_PATH)/src

include $(CLEAR_VARS)
include $(LOCAL_PATH)/build.mk
LOCAL_MODULE    := hevsockstun
LOCAL_SRC_FILES := $(patsubst $(SRCDIR)/%,src/%,$(SRCFILES))
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/src \
	$(LOCAL_PATH)/src/misc \
	$(LOCAL_PATH)/src/core/include \
	$(LOCAL_PATH)/third-part/yaml/include \
	$(LOCAL_PATH)/third-part/lwip/src/include \
	$(LOCAL_PATH)/third-part/lwip/src/ports/include \
	$(LOCAL_PATH)/third-part/hev-task-system/include
LOCAL_CFLAGS += -DFD_SET_DEFINED -DSOCKLEN_T_DEFINED
LOCAL_CFLAGS += $(VERSION_CFLAGS)
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS += -mfpu=neon
endif
LOCAL_STATIC_LIBRARIES := yaml lwip hev-task-system
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_LDFLAGS += -Wl,-z,common-page-size=16384
include $(BUILD_EXECUTABLE)
EXECMK

"$NDK_HOME/ndk-build" \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=jni/exec.mk \
    "APP_ABI=$ABIS" \
    APP_PLATFORM=android-24 \
    NDK_LIBS_OUT="$TMPDIR/libs-exec" \
    NDK_OUT="$TMPDIR/obj-exec" \
    "APP_CFLAGS=-O3" \
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu" \

# Stage both artifacts under libs/<abi>/. The executable is renamed to
# lib*.so so the APK installer extracts it into nativeLibraryDir as an
# executable file (filename distinct from the JNI library above).
mkdir -p "$__dir/libs"
cp -r "$TMPDIR/libs/"* "$__dir/libs/"
for abi in $ABIS; do
  cp "$TMPDIR/libs-exec/$abi/hevsockstun" "$__dir/libs/$abi/libhevsockstun.so"
done

popd
rm -rf $TMPDIR

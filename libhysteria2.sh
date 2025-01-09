#!/bin/bash

targets=(
  "aarch64-linux-android21 arm64 arm64-v8a"
  "armv7a-linux-androideabi21 arm armeabi-v7a"
  "x86_64-linux-android21 amd64 x86_64"
  "i686-linux-android21 386 x86"
)

cd "hysteria" || exit

for target in "${targets[@]}"; do
  IFS=' ' read -r ndk_target goarch abi <<< "$target"

  echo "Building for ${abi} with ${ndk_target} (${goarch})"

  CC="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/${ndk_target}-clang" CGO_ENABLED=1 CGO_LDFLAGS="-Wl,-z,max-page-size=16384" GOOS=android GOARCH=$goarch go build -o libs/$abi/libhysteria2.so -trimpath -ldflags "-s -w -buildid=" -buildvcs=false ./app

  echo "Built libhysteria2.so for ${abi}"
done

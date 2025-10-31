#!/bin/bash

targets=(
  "aarch64-linux-android21 arm64 arm64-v8a"
)

cd "hysteria" || exit

for target in "${targets[@]}"; do
  IFS=' ' read -r ndk_target goarch abi <<< "$target"

  echo "Building for ${abi} with ${ndk_target} (${goarch})"

  CC="${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/${ndk_target}-clang" CGO_ENABLED=1 GOOS=android GOARCH=$goarch go build -o libs/$abi/libhysteria2.so -trimpath -ldflags "-s -w -buildid=" -buildvcs=false ./app

  echo "Built libhysteria2.so for ${abi}"
done

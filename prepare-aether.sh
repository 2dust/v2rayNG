#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
lock_file="$repo_dir/aether-core.lock.json"
version="$(jq -r '.version' "$lock_file")"
source_revision="$(jq -r '.sourceRevision' "$lock_file")"
min_sdk="$(jq -r '.minSdk' "$lock_file")"
repository="$(jq -r '.repository' "$lock_file")"
work_dir="$repo_dir/.aether-native-downloads"
mkdir -p "$work_dir" "$repo_dir/app/src/main/assets/third_party"
while IFS=$'\t' read -r abi target archive checksum; do
    curl --fail --location --retry 3 "https://github.com/$repository/releases/download/$version/$archive" -o "$work_dir/$archive"
    printf '%s  %s\n' "$checksum" "$work_dir/$archive" | sha256sum --check -
    mkdir -p "$work_dir/$abi" "$repo_dir/app/libs/$abi"
    tar -xzf "$work_dir/$archive" -C "$work_dir/$abi" aether
    cp "$work_dir/$abi/aether" "$repo_dir/app/libs/$abi/libaether-core.so"
    "$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/${target}${min_sdk}-clang" \
        -O2 -fPIE -pie -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 \
        "$repo_dir/native/aether-launcher.c" -o "$repo_dir/app/libs/$abi/libaether-launcher.so"
done < <(jq -r '.assets[] | [.abi,.target,.archive,.sha256] | @tsv' "$lock_file")
curl --fail --location --retry 3 "https://raw.githubusercontent.com/$repository/$source_revision/LICENSE" \
    -o "$repo_dir/app/src/main/assets/third_party/AETHER_LICENSE.txt"

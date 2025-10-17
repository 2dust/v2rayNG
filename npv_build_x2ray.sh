#!/bin/bash

set -e

# 如果是 zsh，就加载 ~/.zshrc；如果是 bash，就加载 ~/.bashrc
if [ -n "$ZSH_VERSION" ] && [ -f "$HOME/.zshrc" ]; then
    source "$HOME/.zshrc"
elif [ -n "$BASH_VERSION" ] && [ -f "$HOME/.bashrc" ]; then
    source "$HOME/.bashrc"
fi

# ------------------------
# 检测 go 是否存在
# ------------------------
if ! command -v go &> /dev/null; then
    echo "错误：未找到 Go (go command)。请先手工安装 Go："
    echo "macOS: brew install go 或者从 https://go.dev/dl/ 下载 pkg 安装"
    echo "Linux: sudo apt install golang-go 或者从 https://go.dev/dl/ 下载 tar.gz 安装"
    exit 1
fi

echo "Go 已安装: $(go version)"

# ------------------------
# 检测 gomobile 是否存在
# ------------------------
if ! command -v gomobile &> /dev/null; then
    echo "gomobile 未找到，正在安装..."
    # 安装 gomobile
    go install golang.org/x/mobile/cmd/gomobile@latest
    # 确保 GOPATH/bin 在 PATH 中
    export PATH=$PATH:$(go env GOPATH)/bin
    echo "gomobile 安装完成"
fi

git submodule update --init

export ANDROID_HOME=~/Library/Android/sdk
export ANDROID_SDK_ROOT=~/Library/Android/sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865
export NDK_HOME=$ANDROID_NDK_HOME
export PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$PATH
export PATH=$PATH:$(go env GOPATH)/bin

# build libv2ray.aar
cd AndroidLibXrayLite
gomobile init
go mod tidy -v
gomobile bind -v -androidapi 21 -ldflags='-s -w' ./

mkdir -p ../V2rayNG/app/libs
cp -f libv2ray.aar ../V2rayNG/app/libs/

# go back to the root directory
cd ..

# build libtun2socks.so
chmod +x ./compile-tun2socks.sh
./compile-tun2socks.sh
cp -rf ./libs/* ./V2rayNG/app/libs/

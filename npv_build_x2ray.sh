#!/bin/bash

source ~/.bashrc

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

#!/bin/bash

# Thanks to https://gist.github.com/wenzhixin/43cf3ce909c24948c6e7
# Execute this script in your home directory. Lines 17 and 21 will prompt you for a y/n

# Install Oracle JDK 8
apt-get update
apt-get install -y openjdk-8-jdk
apt-get install -y unzip make expect # NDK stuff

# Get SDK tools (link from https://developer.android.com/studio/index.html#downloads)
wget -q https://dl.google.com/android/repository/sdk-tools-linux-3859397.zip
mkdir android-sdk-linux
unzip sdk*.zip -d android-sdk-linux

# Get NDK (https://developer.android.com/ndk/downloads/index.html)
# wget -q https://dl.google.com/android/repository/android-ndk-r15c-linux-x86_64.zip
# unzip android-ndk*.zip >> /dev/null

ACCEPT_LICENSES_URL=https://gist.githubusercontent.com/xiaokangwang/1489fd223d26581bfec92adb3cb0088e/raw/328eb6925099df5aae3e76790f8232f0fc378f8b/accept-licenses

ACCEPT_LICENSES_ITEM="android-sdk-license-bcbbd656|intel-android-sysimage-license-1ea702d1|android-sdk-license-2742d1c5"

# Let it update itself and install some stuff
cd android-sdk-linux/tools

curl -L -o accept-licenses $ACCEPT_LICENSES_URL

chmod +x accept-licenses

./accept-licenses "./android update sdk --use-sdk-wrapper --all --no-ui" $ACCEPT_LICENSES_ITEM  >/dev/null

# Download every build-tools version that has ever existed
# This will save you time! Thank me later for this

#./accept-licenses "./android update sdk --use-sdk-wrapper --all --no-ui --filter 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27" $ACCEPT_LICENSES_ITEM

PACKAGE_PARSE_URL=https://gist.githubusercontent.com/xiaokangwang/06268fb23034ed94bc301880e862da09/raw/afd95cbbe2f8c1d9e7b0277b7c5ef39af756a6ee/parse.awk

reduceout=https://gist.githubusercontent.com/xiaokangwang/4684bdb5c3415b943f52aa4803386480/raw/b46dab1cc60f02c0d87f88f01e27157034218faa/out.awk

cd bin

curl -L -o parse.awk $PACKAGE_PARSE_URL

curl -L -o reduce.awk $reduceout

sudo apt-get install gawk

./sdkmanager --verbose --list |awk -f parse.awk > ~/package_to_install

readarray -t filenames < $HOME/package_to_install

cat $HOME/package_to_install

yes|./sdkmanager --verbose "${filenames[@]}" |awk -f reduce.awk

# If you need additional packages for your app, check available packages with:
# ./android list sdk --all

# install certain packages with:
# ./android update sdk --no-ui --all --filter 1,2,3,<...>,N
# where N is the number of the package in the list (see previous command)

./sdkmanager "ndk-bundle"

# Add the directory containing executables in PATH so that they can be found
echo 'export ANDROID_HOME=$HOME/android-sdk-linux' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools' >> ~/.bashrc
# echo 'export NDK_HOME=$HOME/android-ndk-r15c' >> ~/.bashrc
# echo 'export ANDROID_NDK_HOME=$NDK_HOME' >> ~/.bashrc


source ~/.bashrc

# Make sure you can execute 32 bit executables if this is 64 bit machine, otherwise skip this
dpkg --add-architecture i386
apt-get update
apt-get install -y libc6:i386 libstdc++6:i386 zlib1g:i386

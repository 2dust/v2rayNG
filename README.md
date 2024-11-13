# v2rayNG

A V2Ray client for Android that supports [Xray core](https://github.com/XTLS/Xray-core) and [v2fly core](https://github.com/v2fly/v2ray-core).

[![API](https://img.shields.io/badge/API-21%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/2dust/v2rayNG)](https://github.com/2dust/v2rayNG/commits/master)
[![CodeFactor](https://www.codefactor.io/repository/github/2dust/v2rayng/badge)](https://www.codefactor.io/repository/github/2dust/v2rayng)
[![GitHub Releases](https://img.shields.io/github/downloads/2dust/v2rayNG/latest/total?logo=github)](https://github.com/2dust/v2rayNG/releases)
[![Chat on Telegram](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/v2rayn)

<a href="https://play.google.com/store/apps/details?id=com.v2ray.ang">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" width="165" height="64" />
</a>

## Telegram Channel
Join our channel: [github_2dust](https://t.me/github_2dust)

## Usage

### Geoip and Geosite
- The `geoip.dat` and `geosite.dat` files are located in `Android/data/com.v2ray.ang/files/assets` (the path may differ on some Android devices).
- The download feature will retrieve an enhanced version from this [repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (requires a working proxy).
- You can manually import the latest official [domain list](https://github.com/v2fly/domain-list-community) and [IP list](https://github.com/v2fly/geoip).
- It is also possible to use third-party `.dat` files in the same folder, such as those from [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6).

### More Information
For additional details, visit our [wiki](https://github.com/2dust/v2rayNG/wiki).

## Development Guide

The Android project located in the V2rayNG folder can be compiled directly in Android Studio or using the Gradle wrapper. Note that the V2Ray core inside the AAR may be outdated.  
You can compile the AAR from the Golang projects [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite).

For a quick start, refer to guides for [Go Mobile](https://github.com/golang/go/wiki/Mobile) and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/).

v2rayNG can run on Android Emulators. For Windows Subsystem for Android (WSA), VPN permission needs to be granted via:
```bash
appops set [package name] ACTIVATE_VPN allow

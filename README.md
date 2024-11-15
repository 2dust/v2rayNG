### v2rayNG

V2rayNG is a V2Ray client for Android that supports both the [Xray core](https://github.com/XTLS/Xray-core) and the [v2fly core](https://github.com/v2fly/v2ray-core).

[![API](https://img.shields.io/badge/API-21%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/2dust/v2rayNG)](https://github.com/2dust/v2rayNG/commits/master)
[![CodeFactor](https://www.codefactor.io/repository/github/2dust/v2rayng/badge)](https://www.codefactor.io/repository/github/2dust/v2rayng)
[![GitHub Releases](https://img.shields.io/github/downloads/2dust/v2rayNG/latest/total?logo=github)](https://github.com/2dust/v2rayNG/releases)
[![Chat on Telegram](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/v2rayn)

Certainly! Here’s a reformatted version of your document with improved structure and clarity:

### Download
<a href="https://play.google.com/store/apps/details?id=com.v2ray.ang">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" width="165" height="64" />
</a>

### Community
Join our Telegram channel: [github_2dust](https://t.me/github_2dust)

---

## Usage

### Geoip and Geosite Files
- The `geoip.dat` and `geosite.dat` files are located in `Android/data/com.v2ray.ang/files/assets` (the path may vary on some Android devices).
- The download feature will retrieve an enhanced version from this [repository](https://github.com/Loyalsoldier/v2ray-rules-dat) (note: a working proxy is required).
- You can manually import the latest official [domain list](https://github.com/v2fly/domain-list-community) and [IP list](https://github.com/v2fly/geoip).
- Third-party data files can be used in the same folder, such as those from [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6).

### Additional Resources
For more information, visit our [wiki](https://github.com/2dust/v2rayNG/wiki).

---

## Development Guide

To compile the Android project located in the V2rayNG folder, you can use either Android Studio or the Gradle wrapper. Please note that the V2Ray core inside the AAR file may be outdated.

You can compile the AAR from the Golang projects:
- [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite)
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)

For a quick start, refer to:
- [Go Mobile guide](https://github.com/golang/go/wiki/Mobile)
- [Makefiles for Go Developers tutorial](https://tutorialedge.net/golang/makefiles-for-go-developers/)

### Running on Emulators
V2rayNG can run on Android emulators. For Windows Subsystem for Android (WSA), grant VPN permission using the following command:
```bash
appops set [package name] ACTIVATE_VPN allow

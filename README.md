# v2rayNG

A V2Ray client for Android, support [Xray core](https://github.com/XTLS/Xray-core) and [v2fly core](https://github.com/v2fly/v2ray-core)

[![API](https://img.shields.io/badge/API-17%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/jelly-bean#android-4.2)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.4.10-blue.svg)](https://kotlinlang.org)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/2dust/v2rayNG)](https://github.com/2dust/v2rayNG/commits/master)
[![CodeFactor](https://www.codefactor.io/repository/github/2dust/v2rayng/badge)](https://www.codefactor.io/repository/github/2dust/v2rayng)
[![GitHub Releases](https://img.shields.io/github/downloads/2dust/v2rayNG/latest/total?logo=github)](https://github.com/2dust/v2rayNG/releases)
[![Chat on Telegram](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/v2rayn)

<a href="https://play.google.com/store/apps/details?id=com.v2ray.ang">
<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" width="165" height="64" />
</a>

### Usage

#### Geoip and Geosite
v2rayNG release already embedded domain file `geoip.dat` and `geosite.dat`. However it is (probably) not the latest and not the most complete list.
For power user, the embedded files can be easily replaced with the following steps:
1. Launch v2rayNG (v1.4.9+)
2. Find existing geoip.dat and geosite.dat in `Android/data/com.v2ray.ang/files/assets` (path may differ on some Android device)
3. Replace them with the latest [domain list](https://github.com/v2fly/domain-list-community) and [ip list](https://github.com/v2fly/geoip)
4. Enhanced version can be found in this [repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (recommend to use `geosite:geolocation-!cn` for proxy dns and routing)
5. It is also possible to use third party dat file in the same folder, like [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

#### See more in our [wiki](https://github.com/2dust/v2rayNG/wiki)

### Development guide

Android project under V2rayNG folder can be compiled directly in Android Studio, or using Gradle wrapper. But the v2ray core inside the aar is (probably) outdated.  
The aar can be compiled from the Golang project under AndroidLibV2rayLite folder. For a quick start, read guide for [Go Mobile](https://github.com/golang/go/wiki/Mobile)
and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/)

v2rayNG can run on Android Emulators, with minimum Android 5.0

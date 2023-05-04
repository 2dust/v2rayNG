# HiddifyNG

A Hiddify client for Android, support [Xray core](https://github.com/XTLS/Xray-core) <!-- and [v2fly core](https://github.com/v2fly/v2ray-core)-->

<a href="https://play.google.com/store/apps/details?id=ang.hiddify.com">
<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" width="165" height="64" />
</a>

<br>
<center>
<a href=""><img width="35%" src="https://user-images.githubusercontent.com/114227601/236047341-47a744e2-b552-4734-b919-2ee8f9416998.png" /></a>
<a href=""><img width="35%" src="https://user-images.githubusercontent.com/114227601/236047343-85026615-c891-40d7-bd22-ec44b846d727.png" /></a>
<a href=""><img width="35%" src="https://user-images.githubusercontent.com/114227601/236047350-99b4cd08-1cd7-49b5-b5e5-efff0163101b.png" /></a>
<a href=""><img width="35%" src="https://user-images.githubusercontent.com/114227601/236047353-5007bd75-fb00-4462-a535-b523b547f6f9.png" /></a>
</center>


[![API](https://img.shields.io/badge/API-21%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-1.6.21-blue.svg)](https://kotlinlang.org)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/hiddify/HiddifyAndroidNG)](https://github.com/hiddify/HiddifyAndroidNG/commits/master)
[![CodeFactor](https://www.codefactor.io/repository/github/hiddify/HiddifyAndroidNG/badge)](https://www.codefactor.io/repository/github/hiddify/HiddifyAndroidNG)
[![GitHub Releases](https://img.shields.io/github/downloads/hiddify/HiddifyAndroidNG/total?logo=github)](https://github.com/hiddify/HiddifyAndroidNG/releases)
[![Chat on Telegram](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/hiddify)




### Telegram Channel
[hiddify](https://t.me/hiddify)

### Usage

#### Geoip and Geosite
- geoip.dat and geosite.dat files are in `Android/data/com.v2ray.ang/files/assets` (path may differ on some Android device)
- download feature will get enhanced version in this [repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (Note it need a working proxy)
- latest official [domain list](https://github.com/v2fly/domain-list-community) and [ip list](https://github.com/v2fly/geoip) can be imported manually
- possible to use third party dat file in the same folder, like [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

### More in our [wiki](https://github.com/2dust/v2rayNG/wiki)

### Development guide

Android project under V2rayNG folder can be compiled directly in Android Studio, or using Gradle wrapper. But the v2ray core inside the aar is (probably) outdated.  
The aar can be compiled from the Golang project [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite).
For a quick start, read guide for [Go Mobile](https://github.com/golang/go/wiki/Mobile) and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/)

v2rayNG can run on Android Emulators. For WSA, VPN permission need to be granted via
`appops set [package name] ACTIVATE_VPN allow`

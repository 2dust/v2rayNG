## RX Client v1.0.0

An Android client for the **Xray core** with native **tun2socks** support, built on the open-source
[v2rayNG](https://github.com/2dust/v2rayNG) project (GPL-3.0). RX Client ships **no** VPN service and
**no** ready-made server: you add your own config or subscription. It contains no advertising,
analytics, or crash-reporting SDK.

| APK | Devices |
| --- | --- |
| `*_arm64-v8a.apk` | Almost all modern phones (recommended) |
| `*_armeabi-v7a.apk` | Older 32-bit phones |
| `*_x86_64.apk`, `*_x86.apk` | Emulators and Intel tablets |
| `*_universal.apk` | One APK for every device |

Requires Android 7.0 (API 24) or newer.

### Install

1. Download the APK that matches your phone (or the `universal` one) from the
   [Releases page](https://github.com/kairomax1/RX_client/releases).
2. Allow "install unknown apps" if Android asks.
3. Open **RX Client**, add your config with `+` (scan QR, paste link, import file or subscription),
   select it, then press the round button to connect.

### Features

- Import by QR code, clipboard, file, or subscription link
- Protocols supported by the bundled Xray core: VLESS, VMess, Trojan, Shadowsocks, SOCKS, WireGuard,
  TUIC, Hysteria2
- VPN mode with native tun2socks, plus proxy-only and root modes
- App-based routing: all apps, selected apps only, or all apps except selected ones; a routing change
  is applied on the next VPN connection
- Subscription groups, delay/real-ping tests, backup and restore

Connection quality and availability depend on your server, your operator, and your network. No
"works on every operator" guarantee is made, and a successful build is not a substitute for a test
on a real device.

## فارسی

نسخه‌ی `v1.0.0` آر ایکس کلاینت: کلاینت اندرویدی هسته‌ی **Xray** با پشتیبانی بومی از **tun2socks**،
بر پایه‌ی پروژهٔ متن‌باز [v2rayNG](https://github.com/2dust/v2rayNG) (مجوز GPL-3.0). این برنامه
**سرویس VPN یا سرور آماده ارائه نمی‌دهد**؛ کانفیگ یا لینک اشتراک خودتان را اضافه می‌کنید. هیچ SDK
تبلیغات، آنالیتیکس یا گزارش خطا در آن نیست.

| فایل | مناسب برای |
| --- | --- |
| `*_arm64-v8a.apk` | تقریباً همه‌ی گوشی‌های امروزی (پیشنهادی) |
| `*_armeabi-v7a.apk` | گوشی‌های قدیمی ۳۲ بیتی |
| `*_x86_64.apk` / `*_x86.apk` | شبیه‌سازها و تبلت‌های اینتل |
| `*_universal.apk` | یک فایل برای همه‌ی دستگاه‌ها |

اندروید ۷.۰ (API 24) به بالا.

### نصب

۱. فایل APK مناسب گوشی‌تان (یا نسخه‌ی `universal`) را از صفحه‌ی
   [Releases](https://github.com/kairomax1/RX_client/releases) دانلود کنید.
۲. اگر اندروید اجازه خواست، «نصب برنامه‌های ناشناس» را فعال کنید.
۳. برنامه‌ی **RX Client** را باز کنید، با دکمه‌ی `+` کانفیگ خود را اضافه کنید (اسکن QR، لینک، فایل
   یا لینک اشتراک)، سرور را انتخاب کنید و دکمه‌ی گرد را بزنید تا وصل شوید.

کیفیت و امکان اتصال به سرور و شبکه‌ی شما بستگی دارد؛ هیچ تضمین «اتصال روی همه‌ی اپراتورها» داده
نمی‌شود.

## Development

The Android project lives under `V2rayNG/` and uses Kotlin, Gradle Kotlin DSL, Compose and Material 3.
Tool versions are read from `V2rayNG/gradle/libs.versions.toml` and
[`.github/workflows/build.yml`](.github/workflows/build.yml).

```bash
git submodule update --init --recursive
# install the NDK used by the build workflow and set NDK_HOME
bash compile-hevtun.sh
mkdir -p V2rayNG/app/libs
cp -r libs/* V2rayNG/app/libs/
# provide libv2ray.aar matching the AndroidLibXrayLite revision
cd V2rayNG
./gradlew :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin
./gradlew :app:assemblePlaystoreRelease
```

The build workflow publishes APKs to GitHub Releases on pushes to `master`, or when it is dispatched
manually with a `release_tag`. Release builds are signed with the repository keystore when the
`APP_KEYSTORE_BASE64` secret is set; otherwise a throwaway CI key is used, which is not suitable for
trusted updates.

## Credits and license

RX Client is derived from [v2rayNG](https://github.com/2dust/v2rayNG) by 2dust and friends, and from
the [Xray-core](https://github.com/XTLS/Xray-core) and
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) projects. Translation credits are
listed inside the app.

This project is distributed under **GPL-3.0**; see [LICENSE](LICENSE). The privacy policy is in
[CR.md](CR.md).

# RX VPN — Privacy Policy / سیاست حریم خصوصی

Last updated: 2026-11-11 · آخرین به‌روزرسانی: ۲۰ آبان ۱۴۰۵ (2026-11-11)

RX VPN is an open-source client for the V2Ray/Xray cores, derived from
[v2rayNG](https://github.com/2dust/v2rayNG). It does not provide a VPN service, a server, or a
subscription. You add your own server configuration.

RX VPN یک کلاینت متن‌باز برای هسته‌های V2Ray/Xray است که از پروژهٔ
[v2rayNG](https://github.com/2dust/v2rayNG) گرفته شده. این برنامه سرویس VPN، سرور یا اشتراک ارائه
نمی‌دهد؛ کانفیگ سرور خودتان را اضافه می‌کنید.

---

## English

### 1. What we collect

**Nothing is sent to the developer.** RX VPN contains no advertising SDK, no analytics SDK, and no
crash-reporting SDK. There is no account, no sign-in, and no telemetry endpoint.

### 2. Data that stays on your device

The app stores the following locally, inside its own private storage:

- the server profiles or subscription links you add, including their credentials
- app settings (routing mode, per-app selections, DNS, ports, theme, language)
- logs you choose to view in the in-app log screen
- an optional backup file if you create one

This data is not uploaded anywhere by the app. Removing the app or clearing its data deletes it.
Backups are written only where you choose, and WebDAV backups go only to the server address you enter.

### 3. Network connections the app makes

The app connects only to the destinations below. Everything else is traffic you generated yourself
through your own server.

| Destination | When | Purpose | Can it be disabled? |
| --- | --- | --- | --- |
| Your server / subscription URL | when you connect or refresh | deliver your VPN traffic and fetch your subscription | it is your own configuration |
| `api.github.com` (this repository's releases) | when the update check runs | compare your installed version with the latest release | the check is user-initiated and can be ignored |
| `raw.githubusercontent.com` (this repository) | when you open the privacy policy or geo files | display this document, download geo data | it only opens when you tap |
| `gstatic.com` / `google.com` (`generate_204`) | when a delay test runs | measure latency to a server | URL is configurable in settings |
| `api.ip.sb` or the IP API you set | when you check your IP | show your current IP and location | URL is configurable in settings |
| DNS resolvers you configured | while connected | resolve domain names | it is your own configuration |

### 4. Permissions and why they are needed

- **INTERNET / ACCESS_NETWORK_STATE / CHANGE_NETWORK_STATE** — establish and monitor the tunnel.
- **FOREGROUND_SERVICE (+ SPECIAL_USE)** — keep the VPN service running with a notification.
- **POST_NOTIFICATIONS** — show the ongoing connection notification.
- **CAMERA** — read QR codes, only while you are on the scanner screen.
- **RECEIVE_BOOT_COMPLETED** — reconnect after a reboot, only if you enabled start-on-boot and a
  server is selected.
- **QUERY_ALL_PACKAGES** — list installed apps so you can choose which apps use the tunnel. Only app
  names and icons for that picker are read; the list never leaves the device.
- **VPN service permission (system dialog)** — create the tunnel.

Camera, notifications, and the app list are never read in the background.

### 5. Sharing

We do not sell, share, or transfer any data. There is no third party to share it with: the app has no
third-party advertising or analytics component.

### 6. Your control

You can revoke permissions in Android settings, delete profiles inside the app, clear app data, or
uninstall. Because nothing is stored remotely, deletion is immediate and complete.

### 7. Children

The app is not directed at children under 13 and collects no personal information from anyone.

### 8. Changes

Changes to this policy are published in this repository, so the version shown inside the app is always
the current one.

### 9. Contact

Open an issue at <https://github.com/kairomax1/RX_client/issues>. Do not include credentials, private
keys, or full configuration links in an issue.

---

## فارسی

### ۱. چه اطلاعاتی جمع می‌کنیم

**هیچ اطلاعاتی برای توسعه‌دهنده ارسال نمی‌شود.** در RX VPN هیچ SDK تبلیغات، آنالیتیکس یا گزارش
خطا وجود ندارد. حساب کاربری، ورود، و سرور تلمتری هم ندارد.

### ۲. داده‌ای که روی دستگاه خودتان می‌ماند

برنامه این موارد را فقط در حافظهٔ خصوصی خودش ذخیره می‌کند:

- پروفایل‌های سرور یا لینک‌های اشتراکی که اضافه می‌کنید، همراه با اطلاعات ورودشان
- تنظیمات برنامه (حالت مسیردهی، انتخاب برنامه‌ها، DNS، پورت‌ها، پوسته، زبان)
- لاگ‌هایی که در صفحهٔ لاگ برنامه می‌بینید
- فایل پشتیبان، اگر خودتان بسازید

این داده‌ها توسط برنامه به جایی آپلود نمی‌شوند. با حذف برنامه یا پاک‌کردن داده‌های آن، پاک می‌شوند.
پشتیبان فقط جایی نوشته می‌شود که خودتان انتخاب کنید و پشتیبان WebDAV فقط به آدرسی می‌رود که خودتان
وارد کرده‌اید.

### ۳. اتصال‌های شبکه‌ای برنامه

برنامه فقط به مقصدهای زیر وصل می‌شود. هر چیز دیگری ترافیک خود شماست که از سرور خودتان می‌گذرد.

| مقصد | چه زمانی | برای چه | قابل غیرفعال‌سازی است؟ |
| --- | --- | --- | --- |
| سرور / لینک اشتراک شما | هنگام اتصال یا به‌روزرسانی اشتراک | عبور ترافیک VPN و دریافت اشتراک | همان کانفیگ خودتان است |
| `api.github.com` (ریلیزهای همین مخزن) | هنگام بررسی به‌روزرسانی | مقایسهٔ نسخهٔ نصب‌شده با آخرین انتشار | با انتخاب شما اجرا می‌شود و می‌توانید نادیده بگیرید |
| `raw.githubusercontent.com` (همین مخزن) | هنگام باز کردن این سند یا فایل‌های geo | نمایش این سند و دریافت داده‌های geo | فقط با لمس شما باز می‌شود |
| `gstatic.com` / `google.com` (`generate_204`) | هنگام تست تأخیر | اندازه‌گیری پینگ سرور | آدرس در تنظیمات قابل تغییر است |
| `api.ip.sb` یا IP API انتخابی شما | هنگام بررسی IP | نمایش IP و موقعیت فعلی | آدرس در تنظیمات قابل تغییر است |
| DNS انتخابی شما | در حال اتصال | تبدیل نام دامنه | همان کانفیگ خودتان است |

### ۴. دسترسی‌ها و دلیل‌شان

- **INTERNET / ACCESS_NETWORK_STATE / CHANGE_NETWORK_STATE** — ایجاد و پایش تونل.
- **FOREGROUND_SERVICE (+ SPECIAL_USE)** — زنده نگه‌داشتن سرویس VPN همراه با اعلان.
- **POST_NOTIFICATIONS** — نمایش اعلان وضعیت اتصال.
- **CAMERA** — خواندن QR، فقط وقتی در صفحهٔ اسکنر هستید.
- **RECEIVE_BOOT_COMPLETED** — اتصال خودکار پس از روشن‌شدن گوشی، فقط اگر «شروع خودکار» را روشن
  کرده باشید و سروری انتخاب شده باشد.
- **QUERY_ALL_PACKAGES** — فهرست برنامه‌های نصب‌شده برای انتخاب برنامه‌هایی که از تونل عبور کنند.
  فقط نام و آیکون برنامه‌ها برای همین انتخابگر خوانده می‌شود و این فهرست هرگز از دستگاه خارج نمی‌شود.
- **مجوز VPN (دیالوگ سیستم)** — ساخت تونل.

دوربین، اعلان‌ها و فهرست برنامه‌ها هرگز در پس‌زمینه خوانده نمی‌شوند.

### ۵. اشتراک‌گذاری

هیچ داده‌ای فروخته، به اشتراک گذاشته یا منتقل نمی‌شود. طرف سومی هم وجود ندارد؛ برنامه هیچ مؤلفهٔ
تبلیغاتی یا آنالیتیکسِ شخص ثالث ندارد.

### ۶. کنترل شما

می‌توانید دسترسی‌ها را از تنظیمات اندروید پس بگیرید، پروفایل‌ها را در برنامه حذف کنید، داده‌های
برنامه را پاک کنید یا برنامه را حذف کنید. چون چیزی روی سرور ما ذخیره نمی‌شود، حذف فوری و کامل است.

### ۷. کودکان

این برنامه برای کودکان زیر ۱۳ سال ساخته نشده و از هیچ‌کس اطلاعات شخصی جمع نمی‌کند.

### ۸. تغییرات

هر تغییر در این سیاست در همین مخزن منتشر می‌شود؛ پس نسخه‌ای که داخل برنامه نمایش داده می‌شود همیشه
به‌روز است.

### ۹. تماس

در <https://github.com/kairomax1/RX_client/issues> یک issue باز کنید. اطلاعات ورود، کلید خصوصی یا لینک
کامل کانفیگ را در issue قرار ندهید.

---

## Credits / اعتبارها

This policy text was written for RX VPN. RX VPN is derived from
[v2rayNG](https://github.com/2dust/v2rayNG) (GPL-3.0) and bundles the
[Xray-core](https://github.com/XTLS/Xray-core) and
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) projects. Portions of the app's UI
and routing behaviour follow the upstream project. See [LICENSE](LICENSE).

این متن برای RX VPN نوشته شده است. RX VPN بر پایهٔ پروژهٔ
[v2rayNG](https://github.com/2dust/v2rayNG) (مجوز GPL-3.0) است و پروژه‌های
[Xray-core](https://github.com/XTLS/Xray-core) و
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) را همراه دارد. بخشی از رابط کاربری و
رفتار مسیردهی از پروژهٔ بالادست گرفته شده است. [LICENSE](LICENSE) را ببینید.

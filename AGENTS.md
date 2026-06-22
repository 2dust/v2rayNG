# v2rayNG — Agent guide

## Build

```sh
cd V2rayNG
./gradlew assembleFdroidDebug    # or assemblePlaystoreDebug
```

Kotlin 2.4.0, AGP 9.2.1, Gradle Kotlin DSL. No lint/format/typecheck tasks configured.

## Test

```sh
cd V2rayNG && ./gradlew test
```

JUnit 4 + Mockito. Unit tests only under `app/src/test/java/`. No instrumented tests beyond defaults.

## Project structure

```
V2rayNG/
  app/
    src/main/java/com/v2ray/ang/
      AngApplication.kt          # extends MultiDexApplication, inits MMKV + WorkManager
      AppConfig.kt                # all constants, pref keys, URLs, ports, tags
      core/                       # v2ray core integration
        CoreServiceManager.kt      # start/stop core, traffic stats, delay measurement
        CoreConfigManager.kt       # generates JSON config for v2ray core
        CoreNativeManager.kt       # JNI bridge to libv2ray AAR
        CoreOutboundBuilder.kt     # outbound config construction
        CoreConfigContextBuilder.kt
      service/                    # Android foreground services
        CoreVpnService.kt          # VPN mode (VpnService)
        CoreProxyOnlyService.kt    # proxy-only mode (no VPN)
        CoreTestService.kt         # delay test
        TProxyService.kt
        DialerNativeService.kt / DialerWebviewService.kt   # browser dialer
        RealPingWorkerService.kt   # WorkManager-based real ping
        QSTileService.kt           # quick settings tile
        ProcessService.kt
      handler/                    # business logic
        MmkvManager.kt             # all MMKV CRUD (servers, subs, settings, routing)
        SettingsManager.kt         # preference defaults, config generation (633 lines)
        AngConfigManager.kt        # server config operations
        NotificationManager.kt
        SpeedtestManager.kt
        SubscriptionUpdater.kt
        WebDavManager.kt
        UpdateCheckerManager.kt
        CertificateFingerprintManager.kt
        SettingsChangeManager.kt
      ui/                         # activities, adapters, fragments
        MainActivity.kt            # main screen with drawer + tabs
        ServerActivity.kt          # edit server config
        ServerCustomConfigActivity.kt / ServerGroupActivity.kt / ServerProxyChainActivity.kt
        SettingsActivity.kt / PerAppProxyActivity.kt / AppPickerActivity.kt
        ScannerActivity.kt / LogcatActivity.kt
        RoutingSettingActivity.kt / RoutingEditActivity.kt
        SubSettingActivity.kt / SubEditActivity.kt
        UserAssetActivity.kt / UserAssetUrlActivity.kt
        TaskerActivity.kt / UrlSchemeActivity.kt
        BackupActivity.kt / CheckUpdateActivity.kt / AboutActivity.kt
      fmt/                        # protocol URL parsers (VMESS, VLESS, TROJAN, SS, SOCKS, etc.)
      dto/                        # data classes + entities/
      enums/                      # EConfigType, Language, RoutingType, etc.
      extension/_Ext.kt           # extension functions (toast, traffic string, etc.)
      util/                       # Utils, JsonUtil, HttpUtil, LogUtil, etc.
      receiver/                   # BootReceiver, TaskerReceiver, WidgetProvider
      contracts/                  # interfaces (ServiceControl, Tun2SocksControl)
      helper/                     # QRCodeScannerHelper, PermissionHelper, FileChooserHelper etc.
```

## Key facts

- **Storage**: MMKV exclusively — never SharedPreferences. `MmkvManager` is the data layer.
- **Core**: Native AAR (`libv2ray`) from [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite). Prebuilt `.aar` files go in `app/libs/`.
- **Services** run in dedicated process `:RunSoLibV2RayDaemon`. `CoreServiceManager` controls start/stop lifecycle.
- **Two modes**: VPN (`CoreVpnService`, uses `VpnService.Builder`) or proxy-only (`CoreProxyOnlyService`, local SOCKS/HTTP).
- **Flavors**: `fdroid` (suffix `.fdroid`) and `playstore` (no suffix). ABI version codes differ per flavor.
- **hev-socks5-tunnel**: Optional tun2socks binary. Build with `./compile-hevtun.sh` (requires `NDK_HOME`).
- **ViewBinding** enabled, no DataBinding.
- **No CI**, no pre-commit hooks, no lint/format enforcement.

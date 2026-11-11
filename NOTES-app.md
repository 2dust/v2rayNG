# RX VPN — notes that must survive

Working branch: `arena/d739235f-rx-client` (App repo: `kairomax1/RX_client`).

## App identity
- **Name:** RX VPN — not "RX Client". `app_name` = `RX VPN` in `values/strings.xml`,
  `dev`/`fdroid` flavour strings, and all 10 locale files.
- **Package:** `com.mobix.vpn` (must stay — installed app and signing key depend on it).
- **Icon:** dark rounded square (#1E1E1E-ish) with "R" in white and "X" in amber (#F5B050-ish).
  Source of truth: `branding/rx-vpn-app-icon.png` (the 1254 px file the user supplied).
  Generated from it: `branding/rx-vpn-playstore-512.png`,
  `fastlane/metadata/android/en-US/images/icon.png`, and every
  `mipmap-*/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`.
- **Palette:** graphite dark theme with an **amber** accent (the RX VPN theme), not the
  violet/cyan "RX Client" theme. `Theme.kt` holds it.
- **UI:** graphite glass panels/buttons (`RxGlassPanel`, `RxGlassButton` in
  `ui/compose/RxComponents.kt`), app routing (`ui/perappproxy/`), bottom bar in
  `ui/main/MainBottomBar.kt`.

## Structure of the bottom bar (as of this change)
- Status line: dot + status text. When connected it is tappable and re-checks the ping;
  a spinner replaces the ping chip while a check runs.
- The ping chip (`48 ms`) appears on the status line **and** as a second line under the
  "Disconnect" label. No separate lightning button any more.
- Big accent button: Connect / Disconnect (`rx_connect` / `rx_disconnect`).
- Square button next to it opens App routing (`MainDestination.PerAppProxy`).

## Things to remove (the user's standing instruction)
- Anything advertising-like: promotion rows, promo URLs, ad/analytics/crash SDKs,
  upstream Telegram channel links, dead wiki/help entries, upstream branding.
- These were already stripped once (`80fa4a92`) and stayed stripped after the RX VPN merge.
  Verify after any upstream merge with:
  `grep -rIn "APP_PROMOTION_URL\|APP_WIKI_MODE\|9\.234456\|github_2dust" --exclude-dir=.git .`

## Things to add / keep in mind
- Ping visible to the user without a separate button (done in the bottom bar).
- Privacy policy lives in `CR.md` and is referenced by `AppConfig.APP_PRIVACY_POLICY`.
- Store metadata in `fastlane/metadata/android/en-US/` (title, short/full description, icon).
- Release notes template: `.github/release-notes.md` with `@TAG@` / `@SHA@` placeholders.

## Signing (so updates install over the top)
- Only one key signs RX VPN: `V2rayNG/signing/rxclient.p12` (alias `mobix`, password
  `mobixvpn`, PKCS#12). Cert SHA-256:
  `52:86:4C:67:AF:63:07:4A:B2:A6:13:64:3A:4B:09:43:4E:3D:32:0B:87:DC:9C:1D:6B:EF:51:4D:88:1A:5C:83`.
- CI used to generate a throwaway key per run, so every APK had a new signature and Android
  refused to update in place ("App not installed") until the old app was uninstalled.
  `build.yml` now signs with the checked-in key (`APP_KEYSTORE_BASE64`, if ever set, must
  hold that same key). Never regenerate the key — that would force every user to uninstall.
- Playstore APKs all carry `1000000*4 + versionCode`, so any playstore APK updates any other;
  fdroid builds use a different offset, so switching between the two needs an uninstall.

## Publishing notes
- Live releases: `v1.0.0` (RX Client, has the old promotion build) and `v1.0.0-rxvpn`
  (pre-release). The next release should be a normal release, e.g. `v1.0.1`, so
  `releases/latest` points at the new build.
- Workflow `Build RX VPN APK` builds APKs and runs validation; `Publish RX VPN Release`
  uploads the artifact of a successful run with
  `-f release_tag=<tag> -f artifact_run_id=<run id>`.
- No Android SDK/JDK in the sandbox — GitHub Actions is the only compile gate.

## Design system (1.0.2 pass)
- `ui/compose/Theme.kt` owns `AppShapes` (8/12/18/24/30 dp) and is passed to `MaterialTheme`,
  so chips, cards, dialogs and menus share one corner language.
- Semantic latency tone: `rxPingColor(ms)` / `rxPingContainer(ms)` — tertiary <= 400 ms,
  primary <= 900 ms, error otherwise or for -1. Do not hardcode `colorPing`/`colorPingRed`
  in new screens (they stay for the older routing screen).
- `RxPill(text, contentColor, containerColor)` in `ui/compose/RxComponents.kt` is the one
  small label used for protocol, latency and subscription badge.
- Server rows: tinted selected background + 4 dp accent rail, name row, meta row with badge /
  statistics / done tick, third row with type pill (plus latency pill in the two column grid).
  Share, edit and delete live behind a single 40 dp overflow button that opens
  `ShareMethodDialog(more = true)`.
- Splash: brand mark, name, then `splash_tagline` («هواتو داره» / "Got your back"); the whole
  overlay is skipped when `ServiceRuntimeState.isRunning()` is true, i.e. the tunnel is up.
- `ServiceRuntimeState` mirrors the core state into MMKV (`pref_service_running`) from the
  `:daemon` process and verifies the daemon process is alive before trusting it.

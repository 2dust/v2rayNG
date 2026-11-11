# Release signing

`rxclient.p12` is the key that signs every RX VPN APK.

- **SHA-256:** `52:86:4C:67:AF:63:07:4A:B2:A6:13:64:3A:4B:09:43:4E:3D:32:0B:87:DC:9C:1D:6B:EF:51:4D:88:1A:5C:83`
- **Alias:** `mobix` — **store/key password:** `mobixvpn`
- **Type:** PKCS#12, RSA 2048, self-signed, valid for ~30 years (to 2056)

## Why it is checked in

Android only installs an APK over an existing app when both carry the *same* signing
certificate. Otherwise the installer fails with "App not installed" and the old app has to
be uninstalled first — which also wipes its settings and servers.

`build.yml` used to generate a throwaway key on every CI run, so every build had a new
signature and every update required an uninstall. It now copies this file (or
`APP_KEYSTORE_BASE64` if that secret is set — it must contain *this same* key).

## Rules

- **Never replace this file.** A new key means every installed copy must be uninstalled.
- The repository is public, so the key is public too. That is acceptable for sideloaded
  builds; do not ship a store listing signed with it unless the repo is made private.
- Moving the key to a secret later is fine: `base64 -w0 rxclient.p12` →
  `APP_KEYSTORE_BASE64`, `APP_KEYSTORE_PASSWORD`/`APP_KEY_PASSWORD` = `mobixvpn`,
  `APP_KEYSTORE_ALIAS` = `mobix`.

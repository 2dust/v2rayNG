# Building the F-Droid flavor from source

This document is the build contract for the `fdroid` product flavor: what goes
into the APK, where each native artifact comes from, and the exact toolchain a
third party needs in order to rebuild a published APK and compare it against
ours.

It exists because the historical objection to this app was not that it bundled
binary blobs, but that published APKs could not be derived from the published
source. An independently repeatable build is the answer to that, and it is also
a hard prerequisite for both the IzzyOnDroid reproducible-builds programme and
an `fdroiddata` recipe.

> Status: the build is *defined* here and automated in
> `.github/workflows/fdroid-source-build.yml`. Whether two independent runs
> produce byte-identical APKs has **not** been verified yet — see
> [Open questions](#open-questions).

## What is built from source

| Artifact | Source | Pinned by | Built by |
| --- | --- | --- | --- |
| `libv2ray.aar` (Xray core + JNI bindings) | [`2dust/AndroidLibXrayLite`](https://github.com/2dust/AndroidLibXrayLite) | git submodule | `gomobile bind` in CI |
| `libhev-socks5-tunnel.so` (in-process tun2socks for `VpnService`) | [`heiher/hev-socks5-tunnel`](https://github.com/heiher/hev-socks5-tunnel) | git submodule | `compile-hevtun.sh` (`ndk-build`) |
| `libhevsockstun.so` (standalone binary for root mode) | same submodule | git submodule | `compile-hevtun.sh` (`ndk-build`) |
| APK | this repository | git | Gradle / AGP |

Nothing under `V2rayNG/app/libs/` is committed to this repository; it is
ignored in `.gitignore` and produced entirely by the steps above.

The difference from `.github/workflows/build.yml` ("Build APK") is one step:
that workflow downloads `libv2ray.aar` from the `AndroidLibXrayLite` release
assets instead of compiling it, which makes its output impossible to verify from
source. `fdroid-source-build.yml` compiles it.

## What is downloaded

Three geo databases are fetched by `AndroidLibXrayLite/gen_assets.sh` and
embedded in the aar's `assets/`:

- `geoip.dat`, `geosite.dat` — from [`Loyalsoldier/v2ray-rules-dat`](https://github.com/Loyalsoldier/v2ray-rules-dat) releases
- `geoip-only-cn-private.dat` — from [`Loyalsoldier/geoip`](https://github.com/Loyalsoldier/geoip)

These are routing **data**, not executable code, and the app can also fetch them
at runtime (`SettingsManager.initAssets` only copies them out of the APK when
they are present). They are listed explicitly in every build manifest. If a
distributor objects to shipping them, omitting the `cp -v data/*.dat assets/`
line yields an APK that downloads them on first use instead.

## Pinned toolchain

These versions are part of the output. A rebuilder that changes any of them will
get different bytes — the Go version in particular is embedded in the compiled
`.so` files. They live in the `env:` block of
`.github/workflows/fdroid-source-build.yml` and in
`V2rayNG/app/build.gradle.kts`:

| Component | Version | Where it is pinned |
| --- | --- | --- |
| Go | 1.27.1 | workflow `GO_VERSION` |
| `gomobile` | the `golang.org/x/mobile` revision in `AndroidLibXrayLite/go.mod` | resolved at build time via `go list -m` |
| Android NDK | 29.0.14206865 | workflow `NDK_VERSION` **and** `android.ndkVersion` |
| Android cmdline-tools | 14742923 | workflow `CMDLINE_TOOLS_VERSION` |
| Android platform / build-tools | android-37.0 / 37.0.0 | workflow `SDK_PACKAGES` |
| JDK | Temurin 21 | workflow `JAVA_VERSION` |
| AGP / Kotlin / dependencies | see `V2rayNG/gradle/libs.versions.toml` | Gradle |
| `gomobile -androidapi` | 24 | workflow `GOMOBILE_ANDROID_API`, must equal `minSdk` |

Two notes on the Go side:

- `gomobile` is installed at the exact `golang.org/x/mobile` version the module
  already depends on, not `@latest` as the upstream `AndroidLibXrayLite`
  workflow does. `@latest` would let the binding generator change underneath an
  otherwise unchanged source tree.
- `go mod tidy` **is** run before `gomobile bind`, as upstream does, because
  `gomobile bind` invokes `gobind` out of the module and the module graph has to
  cover it. This does not hurt reproducibility — `tidy` resolves minimum
  versions and never upgrades — but the workflow warns if it actually modifies
  `go.mod`/`go.sum`, since that would mean the committed files were incomplete.
- `GOTOOLCHAIN=local` is set so Go refuses to silently fetch a toolchain other
  than the pinned one. The Go version string is embedded in the compiled `.so`
  files, so an automatic upgrade would change the output without any signal.

## Reproducing a build by hand

On a Linux host with the pinned JDK, Android SDK and NDK installed, and
`NDK_HOME` / `ANDROID_NDK_HOME` pointing at NDK 29.0.14206865:

```bash
git clone --recurse-submodules https://github.com/AcideFluorhydrique/v2rayNG.git
cd v2rayNG
git checkout <the tag or commit the APK was built from>
git submodule update --init --recursive

# 1. libv2ray.aar
cd AndroidLibXrayLite
mkdir -p assets data
bash gen_assets.sh download
cp -v data/*.dat assets/
go install "golang.org/x/mobile/cmd/gomobile@$(go list -m -f "{{.Version}}" golang.org/x/mobile)"
export PATH="$PATH:$(go env GOPATH)/bin"
gomobile init
export GOTOOLCHAIN=local
go mod tidy
go mod download
gomobile bind -v \
  -target=android/arm,android/arm64,android/386,android/amd64 \
  -androidapi 24 -trimpath \
  -ldflags='-s -w -buildid= -checklinkname=0' \
  -o libv2ray.aar ./
cd ..

# 2. hev-socks5-tunnel
bash compile-hevtun.sh

# 3. stage both into the Gradle project
mkdir -p V2rayNG/app/libs
cp -r libs/. V2rayNG/app/libs/
cp AndroidLibXrayLite/libv2ray.aar V2rayNG/app/libs/

# 4. APKs
cd V2rayNG
echo "sdk.dir=${ANDROID_HOME}" > local.properties
./gradlew licenseFdroidReleaseReport
./gradlew assembleFdroidRelease
```

Output lands in `V2rayNG/app/build/outputs/apk/fdroid/release/` as one APK per
ABI plus a universal one.

Every CI run uploads a `build-manifest` artifact recording the submodule
revisions, the toolchain versions, the runner image, and SHA-256 sums of all
native artifacts and APKs. Attach it when asking a rebuilder to compare.

## Open questions

These are unresolved and should be treated as work items, not as settled:

- **Byte-for-byte determinism is unverified.** Nobody has yet run the build
  twice and diffed the APKs. Likely suspects if they differ: zip entry
  timestamps written by AGP, `gomobile`'s aar packaging, and the HTML report
  produced by `licenseFdroidReleaseReport`.
- **In-app updater.** `UpdateCheckerManager` fetches APK download URLs from
  GitHub releases. IzzyOnDroid tolerates this (with an anti-feature flag);
  F-Droid normally requires it gated off for their build. Not addressed here.
- **`gradle-wrapper.jar`** is the one binary still committed to the repository.
  F-Droid's build server uses its own Gradle, so it is unused there, but their
  scanner may still flag it.
- **Go version availability.** `AndroidLibXrayLite/go.mod` tracks Xray-core
  closely and currently requires Go 1.27. F-Droid's build server is Debian-based
  and its packaged Go may lag behind, which is a real risk for an `fdroiddata`
  recipe even though it is a non-issue on GitHub Actions.

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

> Status: the from-source build works and reproduces byte for byte. Three
> GitHub-hosted runs, across two VMs of the same image and a later one after
> GitHub rolled the image, produced identical checksums for all 13 native
> artifacts and all 5 fdroid release APKs — see [Evidence](#evidence). The whole
> toolchain is pinned; the runner image is the one input that is not, and it
> has been exercised for the APK stage only — see
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

The build manifest also lists a `hev-socks5-tunnel-bin` per ABI, byte-identical
to `libhevsockstun.so`. hev-socks5-tunnel's own `Android.mk` defines that
executable alongside its shared library, so the first `ndk-build` pass in
`compile-hevtun.sh` builds it, and the second pass builds the same program again
under the `lib*.so` name the APK installer needs. It is not packaged into the
APK: AGP's native-library merge only takes files ending in `.so`, plus the
literal names `gdbserver` and `gdb.setup` (`MergeNativeLibsTask` in the Android
Gradle Plugin source).

The difference from `.github/workflows/build.yml` ("Build APK") is one step:
that workflow downloads `libv2ray.aar` from the `AndroidLibXrayLite` release
assets instead of compiling it, which makes its output impossible to verify from
source. `fdroid-source-build.yml` compiles it.

## What is downloaded

Three geo databases are embedded in the aar's `assets/`, and from there in the
APK:

- `geoip.dat`, `geosite.dat` — from [`Loyalsoldier/v2ray-rules-dat`](https://github.com/Loyalsoldier/v2ray-rules-dat) releases
- `geoip-only-cn-private.dat` — from [`Loyalsoldier/geoip`](https://github.com/Loyalsoldier/geoip) releases

These are routing **data**, not executable code, which is why they are acceptable
in a from-source build at all. They are still downloaded rather than generated,
so they get their own section in every build manifest.

`AndroidLibXrayLite/gen_assets.sh` fetches them from `releases/latest`, which
floats — two builds of the same commit on different days would embed different
data and could never be verified against each other. The workflow therefore does
**not** call that script. It reads [`geo-assets.lock`](../geo-assets.lock)
instead, which pins an exact dated release of each project together with the
SHA-256 of each file, and fails the build if a download does not match.

Updating the pin is a deliberate act: bump the tags and checksums in
`geo-assets.lock` as its own commit, and expect every APK checksum to change with
it. The lock file is part of the `libv2ray.aar` cache key, so a changed pin
forces a rebuild rather than silently reusing an aar built with the old data.

If a distributor objects to shipping the databases at all, dropping the three
`fetch_dat` lines yields an APK that downloads them on first use instead —
`SettingsManager.initAssets` only copies them out of the APK when present. That
changes first-run behaviour, so it is not the default here.

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
| JDK | Temurin 21.0.12.1+1 (Adoptium semver `21.0.12+101.0.LTS`) | workflow `JAVA_VERSION` |
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
mkdir -p assets

# Pinned geo data. Do NOT run gen_assets.sh here: it fetches releases/latest,
# which will not match what the published APK was built with.
. ../geo-assets.lock
RULES="https://github.com/Loyalsoldier/v2ray-rules-dat/releases/download/${RULES_DAT_TAG}"
GEOIP="https://github.com/Loyalsoldier/geoip/releases/download/${GEOIP_REPO_TAG}"
curl -fsSL -o assets/geoip.dat "${RULES}/geoip.dat"
curl -fsSL -o assets/geosite.dat "${RULES}/geosite.dat"
curl -fsSL -o assets/geoip-only-cn-private.dat "${GEOIP}/geoip-only-cn-private.dat"
printf '%s  assets/geoip.dat\n%s  assets/geosite.dat\n%s  assets/geoip-only-cn-private.dat\n' \
  "$GEOIP_DAT_SHA256" "$GEOSITE_DAT_SHA256" "$GEOIP_ONLY_CN_PRIVATE_DAT_SHA256" | sha256sum -c -

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

Every CI run records the submodule revisions, the toolchain versions, the runner
image, and SHA-256 sums of all native artifacts and APKs. It is published both as
a `build-manifest` artifact and on the run's summary page, so a rebuilder can
read the checksums without downloading anything.

## Verifying determinism

The caches are keyed on the submodule revisions and the geo pin, so an ordinary
second run reuses the previously built `libv2ray.aar` instead of rebuilding it —
which would make a comparison meaningless. Run the workflow manually with
**`bypass_cache` checked** to force a cold rebuild of every native artifact; that
run also declines to write its results back to the cache, so it does not disturb
the existing entry.

The JDK is pinned to an exact Temurin build for the same reason. Only the
full Adoptium semver form in `JAVA_VERSION` achieves that: `setup-java` checks
the runner's preinstalled JDKs first, so `21` or `21.0.12` quietly take whatever
the image carries, and the four-part `21.0.12.1` resolves to the preinstalled
copy only by an accident of its folder name, then fails to match anything on
Adoptium once the image moves on. The full form never matches a preinstalled
JDK, so `setup-java` always downloads that exact build and verifies its SHA-256.
Updating it is manual: copy the `semver` field for the new release from the
Adoptium API URL given in the workflow.

`bypass_cache` additionally runs `go clean -cache`. `actions/setup-go` restores
Go's build cache, which is content addressed and replays a previous compile's
output rather than redoing the work — normally a pure speedup, but in a
reproducibility check it would hide compiler-level nondeterminism, and an outside
rebuilder starts with no such cache. The module cache is deliberately kept, since
`go.sum` already fixes its contents.

Compare its manifest against an earlier cold run's, on the same commit. The
native artifact checksums and the APK checksums should match line for line. If
they do not, the manifest's toolchain section is the first place to look for
what differed.

Compare the checksums *in the manifest*, not the digests GitHub shows next to
each artifact. Those are digests of the zip container `upload-artifact` builds
around the files, which records per-file metadata and differs on every upload
even when the contents are identical.

Note that a push to the default branch cannot see caches created on feature
branches, so the first run after merging to `master` is cold without
`bypass_cache` — which is how the comparison below came about.

### Evidence

Three runs, all producing the same checksums for all 13 native artifacts and
all 5 fdroid release APKs:

| Run | Commit | Branch | `libv2ray.aar` | hev libraries | JDK | Runner image |
| --- | --- | --- | --- | --- | --- | --- |
| [34460850995](https://github.com/AcideFluorhydrique/v2rayNG/actions/runs/34460850995) | `02a6cc79` | `fdroid-source-build` | compiled (195 s) | cache from a VM ~6 h earlier | 21.0.12.1, runner's preinstalled copy | ubuntu24 20260831.293.1 |
| [34466248610](https://github.com/AcideFluorhydrique/v2rayNG/actions/runs/34466248610) | `02a6cc79` | `master` | compiled (192 s), no Go build cache | compiled (86 s) | same | same |
| [34475374598](https://github.com/AcideFluorhydrique/v2rayNG/actions/runs/34475374598) | `8a0a0e66` | `fdroid-source-build` | cache from the first run | cache | 21.0.12.1, **downloaded from Adoptium** via the exact pin | **ubuntu24 20260907.300.1** |

The first two runs rule out the suspects this document originally listed: AGP
zip entry timestamps, `gomobile`'s aar packaging, and the
`licenseFdroidReleaseReport` HTML all came out identical.

The third run changes no app source (its commit only touches the workflow and
this document), so its APKs had to match if pinning the JDK changed nothing but
where the JDK comes from — and they did. GitHub also rolled the runner image
between the second and third runs, so the third additionally shows the Gradle
and APK stage surviving an image update. It restored its native artifacts from
cache, though, so the Go and NDK compile stages were not re-run on the new
image.

## Open questions

These are unresolved and should be treated as work items, not as settled:

- **The runner image still floats.** `runs-on: ubuntu-latest` moves between
  image builds and, eventually, Ubuntu releases. Every tool that shapes the
  output (Go, NDK, SDK, JDK, Gradle through its wrapper) is pinned and installed
  by the workflow rather than taken from the image, so the image should not
  matter. That is now shown for the APK stage, which reproduced across an image
  update (see [Evidence](#evidence)); the Go and NDK compile stages have not yet
  been run on two different images. A `bypass_cache` run on a newer image would
  close that. The manifest records the image either way.
- **Geo data goes stale.** Pinning trades reproducibility for freshness: the
  routing databases stay at whatever dated release the lock file names until
  someone bumps it. Review it when cutting a release, and note that users can
  update the databases in-app regardless of what shipped in the APK.
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

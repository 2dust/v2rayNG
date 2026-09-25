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

> Status: the from-source build works and reproduces byte for byte. Four
> GitHub-hosted runs, across two VMs of one runner image and later ones after
> GitHub rolled the image, produced identical checksums for all 13 native
> artifacts and all 5 fdroid release APKs — see [Evidence](#evidence). The
> fourth was the first with the JDK that compiles the aar's Java bindings
> pinned, and its aar matched the earlier ones. The runner image itself is
> still not pinned — see [Open questions](#open-questions).

## What is built from source

| Artifact | Source | Pinned by | Built by |
| --- | --- | --- | --- |
| `libv2ray.aar` (Xray core + JNI bindings) | [`2dust/AndroidLibXrayLite`](https://github.com/2dust/AndroidLibXrayLite) | git submodule | `gomobile bind` in CI |
| `libhev-socks5-tunnel.so` (in-process tun2socks for `VpnService`) | [`heiher/hev-socks5-tunnel`](https://github.com/heiher/hev-socks5-tunnel) | git submodule | `compile-hevtun.sh` (`ndk-build`) |
| `libhevsockstun.so` (standalone binary for root mode) | same submodule | git submodule | `compile-hevtun.sh` (`ndk-build`) |
| `geoip.dat`, `geosite.dat`, `geoip-only-cn-private.dat` (routing databases, inside the aar) | [`AcideFluorhydrique/forkray-geodata`](https://github.com/AcideFluorhydrique/forkray-geodata) | git submodule | its `scripts/build.sh` |
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

## Geo data

Three routing databases are embedded in the aar's `assets/`, and from there in
the APK: `geoip.dat`, `geosite.dat` and `geoip-only-cn-private.dat`.

Upstream downloads them prebuilt: `AndroidLibXrayLite/gen_assets.sh` fetches
Loyalsoldier's releases from `releases/latest`, and that repository also commits
two `.dat` files. Neither is used here. The workflow deletes the committed ones
and builds all three from the [`forkray-geodata`](https://github.com/AcideFluorhydrique/forkray-geodata)
submodule instead, which:

- stores every input as plain text (domain lists, CIDR lists), taken only from
  freely licensed sources: v2fly/domain-list-community (MIT), gfwlist
  (LGPL-2.1), gaoyifan/china-operator-ip (MIT), DB-IP Lite (CC BY 4.0) and the
  address ranges operators publish. No MaxMind data, which needs an account and
  a licence key;
- records the exact upstream revision and checksum of each in
  `sources.lock.json`;
- builds the `.dat` files with generators pinned in its `tools/go.mod`, and its
  CI checks that two builds of one commit are byte-identical;
- fails the build if a category the routing presets refer to is missing, since
  Xray refuses to start on a rule naming a category its database lacks.

So the data is pinned by the submodule commit like the other sources, and is
built, not downloaded. The build manifest lists the submodule revision and the
SHA-256 of each `.dat`, read back from the aar.

The sources require attribution (DB-IP) and their licence notices to go with the
data. The workflow appends them, rendered by `forkray-geodata/scripts/notice.py`,
to the licences page the About screen shows, right after
`licenseFdroidReleaseReport` regenerates it.

Updating the data is a submodule bump in its own commit; expect every APK
checksum to change with it. forkray-geodata proposes source updates weekly as
pull requests in its own repository; the pin report here says when the
submodule has fallen behind.

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
| JDK for Gradle | Temurin 21.0.12.1+1 (Adoptium semver `21.0.12+101.0.LTS`) | workflow `JAVA_VERSION` |
| JDK for `gomobile` | Temurin 17.0.20.1+1 (Adoptium semver `17.0.20+101`) | workflow `GOMOBILE_JAVA_VERSION` |
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

# Geo data, built from the forkray-geodata submodule (needs Python 3). Do NOT
# run gen_assets.sh: it downloads prebuilt files from releases/latest.
bash ../forkray-geodata/scripts/build.sh
rm -f assets/*.dat
cp ../forkray-geodata/out/*.dat assets/

# gomobile's javac must be Temurin 17.0.20.1+1 (put it first on PATH here)
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

# 4. APKs, with Temurin 21.0.12.1+1 as the active JDK
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

The caches are keyed on the submodule revisions, so an ordinary
second run reuses the previously built `libv2ray.aar` instead of rebuilding it —
which would make a comparison meaningless. Run the workflow manually with
**`bypass_cache` checked** to force a cold rebuild of every native artifact; that
run also declines to write its results back to the cache, so it does not disturb
the existing entry.

There are two JDKs. `gomobile bind` compiles the aar's generated Java bindings
into `classes.jar` with `javac`, so the JDK on the path at that point is part of
the aar. Until this was noticed the workflow set Java up only later, for Gradle,
leaving `gomobile` on the runner image's default JDK. It is now pinned to that
same default, Temurin 17, and is part of the `libv2ray.aar` cache key.

The JDKs are pinned to exact Temurin builds for the same reason as Go. Only the
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

Four runs, all producing the same checksums for all 13 native artifacts and
all 5 fdroid release APKs:

| Run | Commit | Branch | `libv2ray.aar` | hev libraries | JDK | Runner image |
| --- | --- | --- | --- | --- | --- | --- |
| [34460850995](https://github.com/AcideFluorhydrique/v2rayNG/actions/runs/34460850995) | `02a6cc79` | `fdroid-source-build` | compiled (195 s) | cache from a VM ~6 h earlier | 21.0.12.1, runner's preinstalled copy | ubuntu24 20260831.293.1 |
| [34466248610](https://github.com/AcideFluorhydrique/v2rayNG/actions/runs/34466248610) | `02a6cc79` | `master` | compiled (192 s), no Go build cache | compiled (86 s) | same | same |
| [34475374598](https://github.com/AcideFluorhydrique/v2rayNG/actions/runs/34475374598) | `8a0a0e66` | `fdroid-source-build` | cache from the first run | cache | 21.0.12.1, **downloaded from Adoptium** via the exact pin | **ubuntu24 20260907.300.1** |
| [34494546647](https://github.com/AcideFluorhydrique/v2rayNG/actions/runs/34494546647) | `995b8a3e` | `fdroid-source-build` | recompiled: `javac` fresh with the **pinned Temurin 17**, Go packages from `setup-go`'s build cache | cache | 21.0.12.1 via the pin | ubuntu24 20260907.300.1 |

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

The fourth run is the first to build the aar with the gomobile JDK pinned. The
new cache key forced the aar to be rebuilt, and it came out identical to the one
the first two runs built with the runner's preinstalled JDK 17, so the pin
changed nothing but where that JDK comes from. Its Go packages were replayed from
`setup-go`'s build cache, so it still does not show the Go compile reproducing on
a different image.

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
  routing databases stay at the `forkray-geodata` commit the submodule names
  until someone bumps it. Review it when cutting a release, and note that users
  can update the databases in-app regardless of what shipped in the APK.
- **Geo data coverage.** Only freely licensed sources are used, so `geosite:cn`
  is v2fly's list without Loyalsoldier's additions (which come partly from a
  list whose licence is not stated), and `geosite.dat` is about a fifth of the
  size. Whether mainland sites still route directly well enough needs testing
  from inside mainland China.
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

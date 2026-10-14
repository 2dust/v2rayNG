# Build behradNG 1.5.0 locally on Windows

Open a normal PowerShell window in this folder and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-local.ps1
```

The first run needs Internet access and substantial disk space for the Android
SDK, two NDKs, Gradle, Go tools, and dependencies. Review the Android SDK licenses
when prompted. Java 21, Go, Git, and curl must be installed. Java defaults to
`C:\Program Files\Java\jdk-21`; use `-JavaPath` to specify another installation.

The script builds in `.local-apk-build`, copies the full V2rayNG project and then
the behradNG app overrides, builds the pinned Xray and HEV sources for all four
ABIs, runs unit tests and Kotlin compilation, and assembles a signed release
APK. It uses the existing private signing files in
`tmp/behradNG-release-signing`; keep those files private and backed up.

After a successful build, it verifies the signature, package name, version, and
all native libraries. The output is:

`local-apks/behradNG-1.5.0-local.apk`

Package: `com.v2ray.ang.behradng`. Version code: `753`.
It installs alongside v2rayNG. Updating an existing behradNG installation also
requires that installation to use the same signing key and a compatible version
code. No GitHub publishing, commit, release, or workflow is performed.

To check prerequisites without downloading or building:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-local.ps1 -CheckOnly
```

## Current validation

PowerShell syntax and prerequisite checks passed. The build was attempted, but
the restricted agent session cannot connect to `dl.google.com`, so downloading
the SDK tools failed with curl exit code 7. No APK was produced.

**Not run:** `:app:testDebugUnitTest`, `:app:compileDebugKotlin`, and
`:app:assembleRelease` in the prepared fork project; downloads failed before
project preparation. APK signature/package/native-library checks and physical
phone performance checks also remain unrun. The native Windows build steps have
not been exercised end to end.

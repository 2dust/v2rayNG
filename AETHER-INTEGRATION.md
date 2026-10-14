# Aether integration

The local source adds **aether** to the add-protocol menu and opens a dedicated settings
editor when an Aether profile is edited. Editable settings include MASQUE/WireGuard,
HTTP carrier, scan mode, IP family, noise, peer endpoint, DNS, fragmentation and local
SOCKS port. Profiles use a separate persisted protocol ID; existing IDs remain unchanged.
Shared `aether://` links contain settings and a name, not the native WARP identity.

Protocol, MASQUE transport, scan mode, IP family and noise use selection lists.
WireGuard disables the MASQUE transport and HTTP/2 fragmentation controls while retaining
their saved values for switching back. HTTP/3 disables fragmentation; turning fragmentation
off disables its size and delay fields. Endpoint, DNS, numeric ranges, port and name remain
editable text because they accept custom values.

## Native source and packaging

Pinned release: [CluvexStudio/Aether v2.3.0](https://github.com/CluvexStudio/Aether/releases/tag/v2.3.0).
Source revision: `6175b67df370ab856bcee07fe85b524031903956`.
Aether is licensed under **AGPL-3.0-only**. The preparation scripts download the upstream
license into the APK assets alongside the core. The hashes and download URLs are pinned
in `aether-core.lock.json`; `prepare-aether.ps1` and `prepare-aether.sh` verify each archive
before packaging. A small native launcher ties the child process lifetime to the daemon.
Both local and Actions build scripts include preparation for future builds.

## Current limits

Integration is restricted to Android 8 or later, arm64-v8a, armeabi-v7a and x86_64,
and devices with 4 KiB memory pages. There is no upstream Android x86 release binary.
Support for 16 KiB devices has not been established and is explicitly disabled.

Aether runs as a standalone profile through a local SOCKS listener and Xray.
VPN and proxy-only modes are implemented; root mode, routing/group/chain membership
and Android always-on recovery are unsupported. Settings changes take effect after
reconnecting. These restrictions are surfaced through application resources.

## Validation status

Passed locally: the actual settings validator, CLI argument builder, Aether URI formatter,
profile model and lifecycle command helper compiled and executed with cached Kotlin
2.2.20 and Gson 2.13.1. Checks covered Unicode URI round-trips, credential exclusion,
preceding profile format compatibility, repeat commands, failed restart dispatch and
Stop cancelling a pending restart. Extracted editor callbacks also compiled and applied
their respective field changes. These standalone checks use small stubs for unrelated
app constants and address formatting; they are not an Android project build.
PowerShell/Bash syntax, manifest/resource XML and pinned metadata checks passed locally.
Standalone checks of the editor state's protocol-dependent controls passed: loading/saving,
WireGuard selection, switching back without losing values, HTTP/3, and fragmentation off.
All selection arrays in both resource trees match the supported core option values.

The first manually dispatched Actions build reached Kotlin compilation and reported
four errors: root `app/` overrides replaced the new restart contract and notification
channel, and the batch writer lacked its protocol enum import. The affected override
and canonical files are now synchronized and both batch writers import `EConfigType`.
A standalone compiler check of the effective override contract and notification enum
passed with minimal Android type stubs. This does not establish a successful APK build.

**Not run:** the new AetherSettingsTest, AetherLifecycleTest, AetherFmtTest, AetherProfileRollbackTest,
AetherEditorStateTest and
AetherEditorViewModelTest JUnit classes; `:app:testDebugUnitTest`,
`:app:compileDebugKotlin`, `:app:assembleRelease` and `:app:lintDebug` on the staged
fork project. The canonical project equivalents are `:app:testPlaystoreDebugUnitTest`,
`:app:compilePlaystoreDebugKotlin`, `:app:assemblePlaystoreDebug` and
`:app:lintPlaystoreDebug`. This archive checkout lacks the wrapper/native build inputs;
network downloads are blocked and the installed Android SDK is inaccessible to the agent.

**Not run:** native packaging and startup for every supported ABI; physical-device
VPN/proxy cold start and equivalent consecutive starts, failure after resource acquisition,
normal/repeated stop and stop during setup, restart and stop racing restart,
daemon death, permission denial and scan cancellation;
unsupported API/ABI/page-size checks; editor recreation, accessibility and IME checks.
No connected test device or runnable APK is available. Source integration and static
checks do not establish that the native connection works on a phone.

Publishing these source changes does not produce an APK. The commit uses `[skip ci]`
and no workflow is dispatched. No local Git pull or merge is performed. Instruction
files, signing files, generated binaries and temporary test artifacts are excluded.

# Shizuku tethering: implementation and reviewer guide

This document explains the rootless tethering implementation from its Android prerequisites to its failure paths. It is a reading guide and review checklist, not a claim that every scenario below has passed on every device. Read it alongside the source in the same revision; test results belong to a separately recorded build, device, and configuration.

The feature's central idea is simple: **give Android tethering a TUN-backed upstream, then let v2rayNG consume the packets from that TUN.** Most of the lifecycle code exists because creating that upstream, starting a hotspot, changing a core configuration, and terminating a process are not one atomic Android operation.

## Contents

1. [Foundations: Shizuku, tethering, and testtun](#1-foundations-shizuku-tethering-and-testtun)
2. [Scope and platform boundary](#2-scope-and-platform-boundary)
3. [Code map and suggested reading order](#3-code-map-and-suggested-reading-order)
4. [Processes, resources, and identities](#4-processes-resources-and-identities)
5. [Routing state versus Android tethering state](#5-routing-state-versus-android-tethering-state)
6. [Startup and shutdown walkthrough](#6-startup-and-shutdown-walkthrough)
7. [Profile changes, process death, and recovery](#7-profile-changes-process-death-and-recovery)
8. [Upstream protection and its limits](#8-upstream-protection-and-its-limits)
9. [Configuration, DNS, IPv6, and routing semantics](#9-configuration-dns-ipv6-and-routing-semantics)
10. [Concurrency, IPC, and bounded waits](#10-concurrency-ipc-and-bounded-waits)
11. [UI and status interpretation](#11-ui-and-status-interpretation)
12. [Compatibility adapters and removal conditions](#12-compatibility-adapters-and-removal-conditions)
13. [Security, conflicts, and operational quirks](#13-security-conflicts-and-operational-quirks)
14. [Methodical review and validation](#14-methodical-review-and-validation)
15. [Maintaining this feature](#15-maintaining-this-feature)

## 1. Foundations: Shizuku, tethering, and testtun

### What Shizuku contributes

An ordinary Android application runs under its own restricted UID. Declaring a permission in its manifest does not grant privileged control over Android networking.

[Shizuku][shizuku-api] lets a user-authorized application execute code in a separate privileged process. On a non-rooted device it is started through ADB and uses the shell identity, UID 2000. Its UserService mechanism loads application-supplied Binder code into that separate process. The application talks to it through IPC; the application's own UID does not become shell. Non-root Shizuku normally needs restarting after a device reboot.

This implementation is designed around that shell process. `ShizukuTetheringService` is an AIDL implementation hosted by Shizuku, **not** another ordinary Android foreground `Service`. The binding is daemon-style, so closing the Tethering screen does not stop it. Shizuku itself, the UserService it hosts, and the main v2rayNG core are different lifetime participants.

The UserService calls Android framework APIs directly. It does not run a terminal script, install firewall rules, or parse `dumpsys` to perform normal operations. `ShellContextCompat` gives those framework calls package attribution matching `com.android.shell`; possessing the shell UID alone does not make an app-attributed context suitable for these calls.

### Why the existing VPN is not enough

The main v2rayNG VPN obtains a virtual interface through Android's [VpnService][vpn-api]. Android routes selected device-originated traffic into that interface. Android tethering, however, has its own forwarding and upstream-selection machinery. A connected laptop's packets do not automatically become traffic originating from an Android application protected by that VPN.

Two terms recur throughout the code:

- **Downstream:** the interface serving clients, such as the Wi-Fi hotspot or USB tethering interface.
- **Upstream:** the Android `Network` chosen to carry forwarded client traffic toward its destination.

Android still manages downstream creation, hotspot configuration, DHCP, forwarding, IPv4 NAT, IPv6 advertisement, and DNS forwarding. v2rayNG changes the upstream available to that machinery; it does not reimplement those services.

### What a test TUN actually is

A TUN is a virtual IP interface backed by a file descriptor. The kernel delivers outgoing IP packets to a userspace reader; the reader's implementation determines what happens next. A TUN is not a remote VPN server and does not provide internet access by itself. A TAP, by contrast, carries Ethernet frames; this implementation does not create a TAP.

Android's privileged [TestNetworkManager][test-network-api] creates the interface and publishes an Android test `Network` for it. The framework assigns an interface name, typically `testtunN`. The code retrieves that name; it does not assume `testtun0`, rename the interface, or depend on the numeric suffix. The published network has `LinkProperties` describing the interface, addresses, and DNS servers.

The hidden `TetheringManager.setPreferTestNetworks(true)` call allows Android's tethering upstream selection to use test networks. It is a system-wide preference, **not a firewall rule or an exclusive reservation of our particular TUN**. The service must observe which upstream Android actually chose.

The resulting packet paths are:

```text
Wi-Fi or USB client
    -> Android downstream and tethering forwarding
    -> the owned testtun interface
    -> one of:
       HEV in the shell UserService -> main core's local SOCKS inbound -> main core outbounds
       Xray in the shell UserService -> secondary core outbounds
    -> underlying network, as directed by the selected routing configuration
```

Responses return through the corresponding engine, TUN, and Android downstream. The main VPN's interface and the tethering test TUN are distinct resources. Stopping the tethering engine must not stop the main core or close its VPN interface.

## 2. Scope and platform boundary

The feature is enabled on Android 13 / API 33 and newer. This is a **feature gate**, not an increase in the application's overall minimum Android version. The same `shizuku_tethering_enabled` resource is false in [default resources][bools] and true in [values-v33][bools33]. The [manifest][manifest] and [drawer][drawer] use it to disable the feature's activity, synchronization receiver, Shizuku provider, and navigation entry on older versions. Service entry points also guard the relevant API boundary.

Shizuku availability on an older Android release does not imply this tethering design is available there. The design requires the test-network preference and compatible upstream-selection behavior, not merely the ability to allocate a TUN. There is no API 30–32 alternative path in this implementation.

Other prerequisites are:

- Shizuku running, permitted for v2rayNG, and accepted by the ViewModel's version check.
- A running main v2rayNG connection in VPN mode, with a valid launch snapshot and core lease.
- A functioning Android test-network service and the shell privileges needed by the platform calls.
- Android/carrier/device-policy permission to start the requested tethering type.
- For HEV, a usable SOCKS inbound in the main core, including UDP support where required.

The page directly controls Wi-Fi hotspot and displays USB status. USB is enabled through Android's own UI. Protection, stop, restore, and retry operate on generic tethering-type masks rather than Wi-Fi-specific interface names. That shared logic is not evidence that Bluetooth, Ethernet, or every OEM-specific downstream has been tested or has a dedicated UI here.

Read current SDK, distribution, dependency, and ABI declarations from [app/build.gradle.kts][app-build], [libs.versions.toml][versions], and [the build workflow][workflow], rather than treating a version list in this guide as authoritative.

## 3. Code map and suggested reading order

Follow this order to establish contracts before reading the long service implementation. Paths link to the actual files; names in the right column are useful search anchors.

| Read | File or group | Responsibility and review anchors |
| --- | --- | --- |
| 1 | [HotspotRoutingSnapshot][snapshot], [HotspotRoutingSync][sync-dto] | Small cross-process launch metadata and lifecycle events; distinguish `launchId` from `token`. |
| 2 | [AIDL contracts][aidl] and [TetheringStatusSnapshot][status] | What crosses Binder, which calls are synchronous, and which are one-way. |
| 3 | [HotspotRoutingConfig][config] | Convert a running launch into HEV YAML or a TUN-only native configuration. |
| 4 | [TetheringCoreSync][core-sync] | Main-core hooks, immutable `CoreTetheringLease.Launch`, descriptor ownership, and Shizuku recovery decisions. |
| 5 | [ShizukuRoutingSyncReceiver][sync-receiver] | Ordered forwarding from the app's broadcast channel to the UserService. |
| 6 | [ShizukuTetheringService][service] | Resource/state declarations, `startRouting`, `shutdownRoutingLocked`, `applyRoutingConfigLocked`, and `stopUnprotectedDownstreams`. Then read platform/network/engine helpers. |
| 7 | [TetheringPlatformCompat][compat] and [TetheringApi36][api36] | Legacy versus typed operations; the shared `TetheringUpstreamMonitor`; observed upstream identity. |
| 8 | [ShellContextCompat][shell-context] | Why the privileged process needs shell package attribution. |
| 9 | [ShizukuViewModel][viewmodel] | Permission/binding, fresh snapshot requests, token bookkeeping, operation ordering, and event-driven status refresh. |
| 10 | [ShizukuActivity][activity] and [TetheringScreen][screen] | Presentation only; UI state/action helpers, switches, diagnostics, and accessibility semantics. |
| 11 | [CoreServiceManager][core-manager] | `TetheringCoreSync` hooks around start/stop/reload, `TetheringMessageHandler`, and shared teardown ownership. |
| 12 | [AngApplication][application] and [ShizukuForegroundRecovery][foreground] | Multiprocess Shizuku setup and the API 34+ foreground recovery request. |
| 13 | [TProxyService][tproxy] and [HevTunnelConfig][hev-config] | Process-local HEV JNI control and shared configuration generation; normal VPN behavior must remain intact. |
| 14 | [AppConfig][app-config], manifest, resources, drawer, and tests in [section 14](#14-methodical-review-and-validation) | Constants, component gates, strings, integration surfaces, and executable assertions. |

The service deliberately keeps routing orchestration in one file. Small adapters isolate incompatible Android API signatures; they are not separate owners of routing policy. Likewise, UI control helpers model presentation, not an independent authoritative networking state machine.

## 4. Processes, resources, and identities

### Ownership is more important than the status label

| Owner | Owns | Must not be mistaken for |
| --- | --- | --- |
| Normal app/UI process | Activity/ViewModel, permission UI, status listener, synchronization dispatcher, persisted session token access | The main core or the protected TUN's sole owner |
| Main core process (`:RunSoLibV2RayDaemon`) | Primary VPN/core, authoritative running snapshot, `CoreTetheringLease`, duplicate test-TUN descriptor and network request | A ViewModel singleton; stopping the core is not necessarily killing this process |
| Shizuku UserService | Secondary native Xray or HEV worker, `RoutingSession`, `TestNetworkHandle`, upstream observer, lifecycle queue | Android's own tethering service or the main app's VPN service |
| Android networking services | Published test `Network`, interface/route setup, selected upstream, downstream interfaces | State that the app can infer merely from a successful start callback |

Three distinct mechanisms keep the protected route alive:

1. The UserService retains `TestNetworkInterface` and its TUN descriptor in `TestNetworkHandle`.
2. The main core receives a duplicate descriptor through `holdTestNetwork()` and registers another test-network request. Ordinary core stop clears the launch configuration, **not this protective lease**.
3. `setupTestNetwork()` receives the connectivity-service Binder as its lifetime token. Its lifetime is anchored outside either app-owned process, rather than disappearing just because the publishing UserService dies.

The third item alone is insufficient: the interface descriptors and network requests must also remain alive. Conversely, keeping a descriptor alone is not equivalent to keeping a published, selectable Android network. Review these resources together.

Both network requests use `TRANSPORT_TEST` with the default `NOT_VPN` and `TRUSTED` requirements removed. They are not reservations against other applications creating test networks. `TestNetworkHandle` identifies its own published network by interface name; the tethering observer separately validates Android's chosen upstream.

### Two identities with different purposes

| Identity | Created/held by | Changes when | Prevents |
| --- | --- | --- | --- |
| Tethering session token | ViewModel creates it; MMKV and `RoutingSession.token` retain it | User establishes a new protected-routing session | Old lifecycle events controlling a different or explicitly stopped session |
| Main-core launch ID | Main core creates it in `createSnapshot()`; immutable lease launch retains it | Main core starts/reloads with a new configuration | Combining metadata from one launch with another launch's engine configuration |

The session can survive a profile switch; the launch ID cannot. `openEngineConfig(launchId)` captures one matching immutable launch before parsing or file I/O. `synchronizeRouting()` also checks whether the supplied launch is still current. These are stale-work safeguards, not an atomic transaction spanning every subsequent native operation.

`freshUserService` distinguishes recovery after the privileged process was replaced from a late update arriving after an explicit Stop in the existing process. Only a fresh process may turn a missing session into a recovered session. Duplicate `startRouting()` is rejected before it reads or releases either lease.

## 5. Routing state versus Android tethering state

Read the declarations at the start of [ShizukuTetheringService][service] before following any transition.

| Routing state | Meaning | Resources / UI consequence |
| --- | --- | --- |
| `DISABLED` | Protected-routing shutdown has completed | No owned route; hotspot status is still independently derived from Android |
| `STARTING` | Network or engine setup/switch is underway | Partial resources may exist; not proof that clients are routed |
| `ACTIVE_HEV` / `ACTIVE_NATIVE` | The selected secondary engine was started | Readiness/health checks inspect the worker; downstreams can still be absent or a restore can fail |
| `WAITING` | Main core stopped, died, or could not be synchronized | Secondary engine stopped; protected TUN retained where available, intentionally providing no working datapath |
| `STOPPING` | Confirmed downstream shutdown is followed by resource release | A transient lifecycle state |
| `ERROR` | An operation or engine health check failed | **Does not mean resources were released.** `hasRoutingSession` keeps Stop available for retained resources |

The UI's operation (`CHECKING`, `STARTING_HOTSPOT`, etc.) is a separate notion: it describes an in-flight screen request, not kernel forwarding state.

Three downstream masks are also deliberately separate:

- The monitor's **active interfaces** are Android's latest callback snapshot.
- `requestedTetheringTypes` remembers starts that have crossed into Android but may not yet appear in that snapshot. Cleanup must not call those "already stopped".
- `RoutingSession.desiredTetheringTypes` records what to restore after a core interruption. It is not a substitute for observing current interfaces.

Type numbers become bits through `tetheringTypeBit()`. Only bits 0–30 are representable; `-1` is the unknown-state sentinel, not "all active" or "nothing active". Normal start refuses an unreadable initial downstream state. Shutdown uses remembered types as a fallback and refuses unsafe release when it cannot establish enough state.

## 6. Startup and shutdown walkthrough

### Enable protected routing

Trace `ShizukuViewModel.startRouting()` into the service:

1. Request a fresh snapshot and core lease from the daemon through `MessageHelper`. A UI-selected profile or a settings read is not proof of what is running.
2. Validate that the snapshot describes a running VPN and has a launch identity.
3. Within the process-wide remote-operation lock, save the previous MMKV token and write a new random session token. Restore the previous token if the start does not succeed.
4. The UserService rejects an empty token or duplicate session before changing owned resources, reads Android's active downstreams, and obtains engine content for the requested launch.
5. Install a Binder death watch on the core lease.
6. For a new test network, stop pre-existing downstreams **before** releasing/replacing any old protection. A hotspot already enabled in Android Settings must not be assumed to migrate safely by itself.
7. Set the test-network preference, create the TUN, share its descriptor with the core, register the UserService's network request, and publish `LinkProperties` using the system-owned lifetime token.
8. Wait for publication of the network whose interface matches the newly created TUN. Start the chosen engine and begin health observation.
9. Restore previously active tethering types through the same validated start path used by the page's hotspot switch.
10. Commit the new `RoutingSession` when routing setup succeeds.

`startRoutingLocked()` can return success while one or more downstream restores failed: those failures are logged, and actual downstream status/warnings remain authoritative. Success establishing the route must not be read as success restoring every client connection.

If engine/network construction fails, its partial resources are cleaned up. Pre-existing downstreams were stopped before replacement; if they could not be stopped, replacement must not proceed. Recovery and rebuild paths depend on this ordering.

### Enable the Wi-Fi hotspot

`toggleHotspot()` first establishes protected routing if it is not active. It then calls `setWifiHotspotEnabled(true)`. If hotspot startup fails, the ViewModel stops routing only when this operation created that route; it does not unconditionally dismantle a pre-existing session.

The service treats Android's `onTetheringStarted()` as an acknowledgement, not the final safety condition. It requires a ready engine, a TUN, an active downstream, and an observed upstream equal to the owned interface. The rejection counter must also remain unchanged across the attempt. An existing but incorrectly routed downstream is stopped and restarted through this path.

### Disable hotspot versus disable protected routing

- **Hotspot off:** stop and confirm the Wi-Fi downstream, then remove that type from the desired mask. The protected route and any other downstreams can remain.
- **Protected routing off:** stop all active, requested, and remembered downstream types; confirm their absence; stop the secondary engine; release the core's duplicate network hold; tear down the published network and local descriptor; reset the test-network preference; clear the lease watch and session.
- **Main connection off:** pause the secondary engine but retain the protected route. This is not the same command as disabling protected tethering.

If Android cannot confirm downstream shutdown, `shutdownRoutingLocked()` reports failure and retains protection. `destroy()` refuses to exit on that failure. The UI must still permit a subsequent Stop. Successful protected-routing stop clears the persisted session token in the same IO operation as its Binder result, even if the screen has been closed meanwhile.

## 7. Profile changes, process death, and recovery

### Ordinary main-core stop and restart

[CoreServiceManager][core-manager] calls `TetheringCoreSync` around initial start, reload, stop, and failure. The lifecycle event carries the persisted session token. A successful start supplies launch metadata and a lease; large engine configuration is not included in the broadcast.

[ShizukuRoutingSyncReceiver][sync-receiver] forwards events in order, checking that their token still matches MMKV. It finishes its broadcast work after handing off a one-way Binder call. The UserService's own queue performs the potentially long transition.

On stop, `pauseForCoreRestartLocked()` remembers downstreams, stops the secondary engine, marks `coreRestartPending`, and enters `WAITING` when a TUN remains. Clients may stay associated with Wi-Fi or USB while traffic stops. The phone itself can use its ordinary internet connection after its VPN stops; retained tethering clients must not inherit that direct fallback merely because the main core stopped.

On restart, `applyRoutingConfigLocked()` prefers reusing the same TUN if DNS addresses and IPv6 enablement have not changed and the retained state is usable. It replaces the engine, then restores/validates downstreams. If reuse fails or network properties changed, it stops downstreams first, rebuilds the protected network, and restores them afterward. Connections may be interrupted; this does not migrate existing TCP sessions between independent engines.

On synchronization failure, the secondary engine is stopped. A retained TUN yields `WAITING`; without it, the service attempts downstream shutdown and reports error. Tethering hook failures are isolated by `runCoreSyncHook()` so that this optional feature does not throw into the primary core lifecycle.

### The main core process dies without sending Stop

`watchCoreLifetimeLocked()` links a death recipient to the core lease. If that Binder dies, the surviving UserService pauses its engine and keeps the test TUN/network resources. This covers a crash or force-stop that bypasses normal lifecycle broadcasts. Closing just the activity is different and should not stop either engine.

The death handler uses the lifecycle monitor, and normal Stop is asynchronous. Neither promises an instantaneous secondary-engine stop at the exact CPU instruction where the main core exits. Review eventual shutdown and the retained protected route independently; do not confuse this callback with the lock-independent unsafe-upstream emergency path.

When the user starts the main connection again, a new lease and launch can resume the retained session. Recovery does not silently start a main connection the user chose to stop.

### Shizuku disappears while the main core survives

The core's duplicate TUN descriptor and network request retain the protected upstream even if the shell engine disappears. The network's lifetime token is not owned by the dying UserService. Traffic is intentionally stranded on that route until recovery.

`TetheringCoreSync` observes Shizuku Binder death and return. If a core is still running, a replacement Binder triggers synchronization from its current snapshot and the persisted session token. A fresh UserService stops active downstreams before releasing the old core-held network and creating a replacement. Only after the new route is ready does it restore downstreams.

On API 34+, replacement-Binder delivery can be deferred while the app is cached. `ShizukuForegroundRecovery` forwards activity-resume events to the core, which requests the Binder for its non-provider process. `TetheringRecoveryState` coalesces these requests and clears pending recovery when the core stops. This is a fallback when delivery is deferred, not a claim that every API 34+ device always needs foregrounding.

An isolated UserService failure while the Shizuku server remains alive is not identical to server Binder death: the server-death recovery listener does not itself prove automatic recovery for that case. A subsequent bind/core synchronization can reach a fresh service. Include isolated failure in device review rather than assuming all process losses take the same notification path.

### Both failure domains disappear

There is no surviving app-owned descriptor/request holder if both the core process and UserService are terminated. The system-owned lifetime token does not turn this into an OS-enforced lockdown policy. Simultaneous loss of both holders, reboot, system networking failure, or hostile privileged interference is outside the single-process-loss protection guarantee.

## 8. Upstream protection and its limits

### Observe the actual route, not the requested route

One long-lived `TetheringUpstreamMonitor` records Android's upstream and downstream callbacks. The selected `Network` is resolved through `ConnectivityManager.getLinkProperties()` to its interface name. Only exact equality with the owned TUN name is accepted; a string containing that name among other text is not accepted.

On API 33–35, downstream identities come from the callback's supplied `Set<TetheringInterface>`. Reading the manager's separate cached interface getter at that moment could observe an older state. Both adapters publish the same small `ActiveTetheringInterface(type, name)` model.

`eth0` was a useful emulator reproducer, not the name of the bug. Any unexpected physical or other application's test interface is rejected. A real device may expose cellular, Wi-Fi, or Ethernet under different names. An allocated `testtunN` alone proves neither Android's upstream choice nor working proxy traffic.

### Initial handover

`startTetheringTypeAttemptLocked()` records the pending type before calling Android. It waits for downstream activation and then checks the upstream:

- An empty interface means selection/properties are not yet available; the bounded check can wait.
- The exact owned interface is acceptable.
- Any other non-empty interface fails immediately. Waiting for a physical path to become protected later would leave clients exposed during the wait.

Failed attempts explicitly stop the possibly in-flight downstream. If cleanup fails, its error takes precedence over the original start error. A wrong-upstream result gets one delayed retry after cleanup; other failures do not automatically receive that retry. The delay and retry are shared across tethering types, not special-cased for Wi-Fi.

### Later unexpected upstream changes

`stopUnprotectedDownstreams()` deliberately does **not** take the routing lifecycle monitor. It reads the observed interface and known downstream masks, increments `upstreamRejections`, records warning bits, and requests every affected downstream stop without waiting for each acknowledgement. An in-flight start checks that counter before claiming success.

The ordinary lifecycle path still verifies final shutdown before releasing a TUN. Emergency stop dispatch is independent of that monitor, but remains dependent on Android processing Binder requests and actually stopping tethering. It is not a kernel packet filter. The implementation does not continually retry a failed emergency stop on a timer; subsequent callbacks and explicit lifecycle operations are separate opportunities to act.

Warnings are tracked by type. A successful retry clears only its own bit. `getStatus()` consumes accumulated warning bits once, and the UI presents the localized wrong-upstream message through `toastInfo`. Reading status for diagnostics can therefore consume a warning before the screen reads it.

### What "fail-closed" means here

The implementation is designed to retain a dead protected route when an engine stops and to stop downstreams when Android reports an unsafe upstream. It does not intentionally remove the protected route while known clients remain active.

It **cannot prove that zero packets escape before Android delivers an upstream-change callback or completes a stop request**. Protection of a hotspot already active before enabling this feature begins during the transition; traffic sent before that transition was not protected retroactively. Missing callbacks, OEM forwarding/offload behavior, and both-process loss require separate scrutiny. A successful status assertion is not a packet capture.

Also, a routing rule intentionally selecting Xray's direct outbound is not an Android upstream escape: the packet still traversed the chosen engine and obeyed its configuration. Leak testing must use a profile/rule whose expected destination is unambiguously proxied or blocked.

## 9. Configuration, DNS, IPv6, and routing semantics

### Use the running launch, not newly edited settings

`TetheringCoreSync.onStarted()` pairs the exact generated main-core JSON with launch metadata. Changing preferences without restarting the main core must not make tethering build a different configuration from that still-running core. The lease captures metadata/configuration together; the shell service obtains engine content through a read-only descriptor.

The descriptor refers to a temporary file created in app-private cache. Its pathname is unlinked after opening, and the open descriptor keeps its contents available for the recipient. This avoids large Binder payloads and a normal shared pathname containing the full configuration. It is not a pipe, and it does not mean configuration contains no credentials.

### HEV and native Xray are not interchangeable internals

| Aspect | HEV | Native Xray TUN |
| --- | --- | --- |
| TUN consumer | Another process-local HEV worker in the UserService | Another Xray controller in the UserService |
| Routing decisions | Main core, reached through its local SOCKS inbound | Secondary core, using the copied running configuration |
| Configuration | `HevTunnelConfig` with captured MTU, timeouts, log level, SOCKS port/authentication, and synthetic TUN addresses | `nativeTunOnlyConfig()` retains all TUN inbounds and removes other inbounds |
| Local SOCKS requirement | Must be enabled and usable | Not required by the tethering backend itself |
| Runtime state | Shares main core's routing/balancer state through SOCKS | Has independent connections, DNS caches, counters, and balancer/observatory runtime state |

Native configuration retains outbounds, routing, DNS, balancers, and other non-inbound sections at the JSON-model level, not byte-for-byte text. Removing SOCKS/HTTP inbounds avoids listener-port collisions. It is not a general repair of arbitrary custom configurations: inbound-tag-specific rules and other listener-producing sections must be reviewed in their actual configuration.

Equal policy-group/balancer configuration does not guarantee that two independent native controllers select the same member at every instant. Profile/configuration synchronization is the contract; identical mutable runtime state is not.

HEV's JNI state is process-global. `startExternalTunnel()` is safe as a separate tethering instance because it runs in a separate process. Moving it into the main core process would destroy that isolation. `isExternalTunnelRunning()` checks native worker state; the existence of the YAML file is not a health check. A running worker also does not prove its SOCKS endpoint or the remote server is reachable.

### DNS follows existing app behavior

`SettingsManager.getVpnDnsServers()` supplies the IP-address list advertised by the main VPN; the snapshot carries the same list to the test network. The tethering feature does not introduce a separate public resolver preference.

When Local DNS is enabled, the normal configuration builder installs port-53 routing to `dns-out` for the relevant inbound (`socks` for HEV, `tun` for native). HEV traffic reaches those rules in the main core; native mode preserves them in its TUN-only configuration. With Local DNS disabled, configured resolver addresses are ordinary routed destinations. Android still handles the downstream-facing DNS service. Custom configurations can have different rules; inspect the actual generated configuration.

Do not confuse the VPN DNS address list, the core's internal DNS server settings, Android's DNS proxy, and a client's own encrypted DNS. A client choosing its own resolver exercises routing of that traffic, not necessarily the app's port-53 handling.

### IPv6 is requested separately from being observed

The captured Enable IPv6 setting determines whether the test network has an IPv6 address and whether HEV receives an IPv6 tunnel address. Native mode uses the running core's TUN configuration. The synthetic addresses live in `AppConfig.SHIZUKU_TUN_ADDR_V4` and `SHIZUKU_TUN_ADDR_V6`; they are not real public allocations or proxy endpoints.

The IPv6 prefix is a documentation-range /64 chosen to satisfy Android's upstream-prefix handling. A ULA-shaped DNS hint (`SHIZUKU_TUN_DNS_HINT_V6`) is appended only when IPv6 is enabled and the configured DNS list has no IPv6 address. Configured servers stay first. The hint is not an independently running resolver; successful resolution must come from the configured servers or applicable core DNS interception. Review DNS behavior with Local DNS both on and off rather than assuming this synthetic hint can resolve names by itself.

Android decides whether to advertise the prefix downstream. `hasDelegatedIpv6Prefix()` inspects the real downstream interface for an address inside the configured /64; the UI reports per-type IPv4-only, dual-stack, or unknown from that observation. This is a **prefix-setup diagnostic**, not proof that a particular client acquired an address, that its DNS works, or that the remote proxy has IPv6 egress. A newly enabled setting is not proof the currently running launch has already applied it; status display enablement reads the current preference while routing follows its launch snapshot.

Prefer IPv6 is not a second downstream-enablement switch. Its effects in the normal configuration/resolution path are inherited through the running core or copied native configuration. It cannot force Android to delegate IPv6, force a client to choose IPv6, or provide IPv6 connectivity that the selected outbound lacks.

Per-app/process routing cannot identify the original application on a tethered laptop or phone. Domain/IP/port/protocol rules still apply as expressed by the effective core configuration. The screen's disclaimer communicates that distinction.

## 10. Concurrency, IPC, and bounded waits

Read the [AIDL][aidl] before interpreting a returned method call as completion:

- UI mutations (`startRouting`, `stopRouting`, `setWifiHotspotEnabled`) and `getStatus` are synchronous Binder calls, performed off the UI thread.
- `notifyCoreStopping`, `synchronizeRouting`, and `notifyCoreStartFailed` are one-way. Their return means handoff, not a completed transition.
- Status notifications and listener registration are one-way. The notification carries no state; the ViewModel fetches current state.
- Lease calls are synchronous and can involve configuration generation, file I/O, or resource acquisition.

| Ordering mechanism | Purpose | Deliberately not covered |
| --- | --- | --- |
| UserService monitor (`@Synchronized` / `synchronized(this)`) | Prevent normal start/stop/configuration transitions interleaving | Emergency downstream stop dispatch and listener replacement |
| `coreUpdates` single-thread scheduler | Order core lifecycle events and run engine health checks | Acknowledgement to the sender; unsafe-upstream emergency processing |
| Monitor change executor | Process upstream/downstream changes after publishing callback snapshots | Waiting for ordinary lifecycle work to finish |
| UI process-wide `serviceOperationLock` on IO | Keep remote mutations and MMKV bookkeeping ordered across closing/reopening the screen | Android cancelling a Binder transaction already submitted |
| ViewModel operation generation | Discard obsolete asynchronous UI results | Undoing an already executed remote mutation |
| Sync dispatcher queue | Order forwarded broadcasts, bound service connection wait, retry one stale Binder proxy | Completing the actual routing transition within a BroadcastReceiver lifetime |

Callbacks publish atomic references before scheduling change handling. Active interfaces and upstream identity arrive independently; `getStatus()` is one Binder response, not a transactionally simultaneous kernel snapshot of every field. Fresh callbacks and postcondition checks remain important.

The source currently bounds individual waits as follows; these are diagnostic expectations, not a maximum duration for the whole operation:

| Wait | Current bound |
| --- | --- |
| UI core snapshot / UserService binding; dispatcher binding | 5 seconds each |
| Android start/stop acknowledgement; initial interface snapshot | 10 seconds each |
| Test network publication | 15 seconds |
| Downstream active/stopped postcondition | 10 seconds |
| Upstream selection while no interface is reported | 5 seconds |
| Delay before the one wrong-upstream retry | 8 seconds |
| Postcondition polling interval | 200 ms |
| Secondary-engine health observation | 2-second scheduled delay |

Multiple downstreams and retry phases can add these waits. Native calls and synchronous Binder work do not acquire a universal deadline from this table. Health observation also shares the lifecycle queue/monitor and can be delayed by ongoing work. Review responsiveness with blocked operations; do not infer it solely from the presence of timeout constants.

## 11. UI and status interpretation

[ShizukuActivity][activity] collects immutable ViewModel state with lifecycle awareness and passes actions to [TetheringScreen][screen]. The screen does not create tunnels or mutate preferences. Its routing switch means "a protected session/resources are retained", not always "packets are flowing"; therefore it can remain checked in `WAITING` or a retained-resource error.

Permission status, shell-service connectivity, main-core state, routing state, hotspot state, and IPv6 observation are distinct. In particular:

- Shizuku permission granted does not prove a UserService can bind or a platform API works.
- Main-core running status is queried from the daemon, not reconstructed from a UI singleton.
- Closing the page unregisters listeners and unbinds **without destroying** the daemon UserService.
- Losing the service connection makes the screen unavailable; its cleared local fields do not prove the OS released the retained route.
- A hotspot can exist without protected routing, including one started outside the app. The screen has a separate direct/unprotected presentation for that case.
- The diagnostic detail uses the **observed upstream interface** and profile, not the requested interface name. It can be empty while Android is still resolving an upstream.
- Status refresh is callback-driven. A refresh arriving during a check is coalesced into another read; a toggle performs a final refresh. There is no continuous UI polling loop.
- `getStatus()` includes backend health and can consume warnings. Do not use repeated reads as a passive packet-safety proof.

The row is the switch's action/semantic target; the visual `Switch` does not add a second action. Icons are decorative. Review actual TalkBack, keyboard, D-pad, long translations, and large-font behavior; a screenshot only establishes appearance. Resource keys retain the `shizuku_` prefix although the page is called Tethering.

## 12. Compatibility adapters and removal conditions

"Typed API on API 36+" does not mean "no hidden API on API 36+". The following dependencies are intentional review boundaries, not universally stable contracts:

| Dependency | Location / why | Exact migration or removal condition |
| --- | --- | --- |
| API 33–35 tethering lookup, request, start/stop, callback and interface reflection | `TetheringPlatformCompat` plus service lookup | Remove legacy branches when the feature stops supporting APIs below 36; retain shared helpers still used by the typed path. |
| Typed tethering requests and callbacks | `TetheringApi36`, reached through `usesPublicTetheringApi()` | Keep above-minimum types isolated so older supported devices never load an incompatible path. |
| `onUpstreamChanged(Network?)` on the typed callback | Hidden callback member with `@Keep`, matching the framework binary signature; not a Kotlin `override` against the current SDK | Replace with a normal `override` when the compile SDK exposes it. Remove only if upstream identity ceases to be part of protection. Validate shrinker behavior when changing it. |
| Test-network create/setup/teardown | `ShizukuTetheringService` reflection | Public replacements must provide equivalent descriptor and lifetime-token semantics on every supported feature API. |
| `setPreferTestNetworks(boolean)` | Service reflection on every supported API | A public equivalent must provide the same upstream opt-in everywhere supported, or the design must stop using test networks. |
| `TRANSPORT_TEST` numeric value in the request | Shared request builder and narrow `WrongConstant` suppression | Use a public constant when exposed, or delete the request when no longer using test networks. |
| CIDR `LinkAddress` construction | `createLinkAddress()` reflection | A public equivalent must exist on every supported feature API. |
| Shell-attributed `ContextImpl` construction | `ShellContextCompat` | Android/Shizuku supplies a public equivalent whose operation package is shell across supported versions. |
| Foreground Binder request on API 34+ | `ShizukuForegroundRecovery` and core sync | Minimum supported Shizuku reliably delivers replacement Binders to cached non-provider processes across supported Android versions. |

API presence is not permission or OEM-behavior equivalence. A device can pass the resource/API gate and still lack a usable service, method, permission, upstream behavior, or hotspot entitlement. Check actual firmware before extending compatibility claims. Root-started Shizuku/Sui identity variants are not established by testing the non-root shell path.

## 13. Security, conflicts, and operational quirks

### Trust and sensitive material

Granting Shizuku permission allows this feature's privileged code to act outside ordinary app privileges. The UserService receives the running native configuration or SOCKS credentials, so the user must trust both Shizuku and the installed app.

The manifest's synchronization receiver/activity are non-exported. UI snapshot reception is non-exported. Shizuku's exported provider is protected by `INTERACT_ACROSS_USERS_FULL`. Internal intents are package-scoped/explicit as appropriate, and the sync dispatcher checks the random session token before forwarding. The lease is a sensitive Binder capability: its holder can request engine content and assets. Tokens and launch IDs complement component/IPC boundaries; they are not encryption or a defense against another actor with equivalent privileged access.

Native geodata and user assets are app-private, so `stageNativeAssets()` streams them through the core lease into `/data/local/tmp/v2rayng-tethering-assets`. Names are restricted to basenames; the cache fingerprint uses names, sizes, and modification times, not a content hash. Files are staged via temporary files, stale names are removed on restaging, and unchanged files are reused. This directory is not cleared by ordinary routing shutdown. Review its shell-level visibility and lifetime separately from app-private configuration.

HEV YAML is written to a PID-specific file in `/data/local/tmp` and removed by normal engine cleanup. Abrupt process death can bypass that cleanup, and the YAML can include SOCKS authentication. Do not describe shell temporary storage as app-private or guaranteed erased. Avoid putting real credentials, full configurations, or unredacted user assets in review logs or test artifacts.

### Shared global state

Do not combine this feature casually with another test-network owner, root VPN-hotspot tool, firewall/tethering controller, or v2rayNG root-mode forwarding. `setPreferTestNetworks` and Android tethering state are global; cleanup resets the preference to false rather than restoring another controller's prior state. Emergency and explicit shutdown may stop downstreams originally enabled outside v2rayNG.

The implementation uses Android's existing SSID/password/band settings and does not manage client allowlists or bypass carrier/device-policy checks. It does not silently disconnect station Wi-Fi or enable mobile data. Some devices support [simultaneous station Wi-Fi and hotspot][wifi-concurrency]; others cannot satisfy the requested combination.

Positive Android error `5` is [TETHER_ERROR_INTERNAL_ERROR][tethering-api], not a Wi-Fi-specific failure. It is also different from this service's negative `RESULT_ALREADY_ACTIVE` (`-5`) and routing state `ERROR` (`5`). Current hotspot error presentation can expose the platform number; contextual Wi-Fi guidance/automatic switching is not implemented here.

### Resource and naming surprises

- A rising `testtun` suffix is not itself proof of a leaked interface. Inspect live interfaces and retained descriptors/requests. The API chooses the name; review must not rely on suffix reuse.
- A retained TUN with no worker is often the intended safety state. Deleting it to make an error screen look clean can expose clients.
- A successful engine start is not a remote connectivity probe, and a successful route start is not proof all downstream restores succeeded.
- Native profile switches replace an independent engine; balancer choices and established connections can differ from the main engine even with equivalent configuration.
- `USER_SERVICE_VERSION` in `createUserServiceArgs()` must change when implementation/AIDL compatibility changes, because an old daemon can survive an APK update. An ordinary app versionCode alone is insufficient for local rebuilds. Documentation-only changes do not require a bump.

## 14. Methodical review and validation

### A. Static review, in order

Use the code map above and mark each item only after following both the happy and failure paths.

1. **Boundary:** check resource gate, manifest components, drawer entry, permission flow, API guards, and shell context attribution. Confirm older app-supported APIs do not enter the feature.
2. **IPC:** read every AIDL method, snapshot field, event, and receiver. Identify the process executing it and whether return means handoff or completion.
3. **Launch consistency:** trace main-core config capture to `openEngineConfig`; check stale/nullable launch handling and distinguish session authorization from launch freshness.
4. **Resource ownership:** follow TUN creation, publication token, both descriptor/request holders, Binder death, normal release, partial failure, and refused shutdown.
5. **Start/restore:** begin with no downstream, then pre-existing downstreams, then an accepted-but-not-yet-published start. Check every early return's cleanup.
6. **Stop:** distinguish hotspot off, protected-routing off, main-core stop, UI close, and process death. Verify primary-core teardown is not reached by the tethering Stop action.
7. **Concurrency:** draw an execution timeline for blocked configuration read plus bad-upstream callback. Verify emergency dispatch and listener removal do not require the lifecycle monitor. Check rejection-counter and stale-event behavior.
8. **Configuration:** compare native JSON before/after inbound filtering; review HEV credentials/timeouts/MTU; check policy groups, custom inbound rules, geodata staging, and DNS with Local DNS on/off.
9. **IPv6:** inspect requested addresses and DNS hint separately from actual downstream prefix detection. Check unknown versus disabled versus IPv4-only UI states.
10. **Recovery:** trace core death, Shizuku server death, isolated UserService death, foreground recovery, explicit Stop followed by a late event, and both-holder loss. Do not turn missing evidence into a guarantee.
11. **Presentation:** map every state/result to enabled controls, switch state, strings, warning consumption, and accessibility semantics. Verify translated strings across the declared locale set.
12. **Maintenance/performance:** inspect per-process threads, callback registrations, health scheduling, cancellation, asset copying, native resources, and compatibility removal comments. Confirm the feature does not add per-packet Kotlin/Binder work.

### B. Automated tests: what they actually cover

| Test source | Assertions to inspect | Does not establish |
| --- | --- | --- |
| [HotspotRoutingConfigTest][test-config] | Missing launch rejection, TUN-only filtering, retained sections, HEV settings/quoting/IPv6 output | All custom configs, DNS reachability, or packet routing |
| [TetheringCoreSyncTest][test-sync] | Hook failure isolation; recovery only with a running core; foreground API boundary/coalescing | Actual Android process death or Binder delivery timing |
| [TetheringPlatformCompatTest][test-compat] | API dispatch boundary, valid bit masks, exact upstream comparison | Runtime hidden-method availability on an OEM |
| [TetheringUpstreamMonitorTest][test-monitor] | Latest callback snapshot, initial missing state, invalid state, close | Framework callback reliability or ordering |
| [TetheringUiStateTest][test-ui] | Control/state mapping, Stop for retained errors, per-type IPv6/unknown behavior | TalkBack speech, D-pad interaction, or actual forwarding |
| [HevTunnelConfigTest][test-hev] and [CoreTeardownDecisionTest][test-teardown] | Shared YAML generation and teardown scheduling decisions | JNI worker lifetime, descriptor ownership, or complete service teardown |
| [TetheringLifecycleTest][test-device] | Duplicate start leaves leases untouched; stale update cannot undo Stop; listener removal while startup blocks; stale launch read rejection; HEV failure status; retained-TUN pause/resume; emergency shutdown during blocked configuration read | The complete physical-client, OEM, process-death, and IPv6 matrix below |

Important details of the device tests:

- They require a usable selected profile, VPN/Shizuku authorization, and a compatible test device.
- The `backend` argument changes VPN/backend preferences and enables local SOCKS for HEV. They are not settings-preserving tests. Record settings before/after and agree on the desired final configuration.
- Setup/teardown stops protected routing and can affect tethering clients. Use a dedicated device/network, not a production hotspot.
- Client Wi-Fi is disabled for the isolated lifecycle tests so switching into hotspot mode does not independently reload the main VPN. That isolates a race; it does **not** test the connected-Wi-Fi user scenario.
- `coreStopKeepsHotspotOnDeadTunAndRestartResumesIt` sends lifecycle calls to the UserService with a live lease; it is not a real crash/force-stop test.
- The unsafe-upstream test deliberately changes the global preference and restarts a downstream from outside the service while a lease read is blocked. It uses shell permission adoption and hidden-API access for fault injection. It must not be run with sensitive client traffic.
- That test uses an independent Android observer because `getStatus()` legitimately waits behind the blocked lifecycle monitor. It asserts downstream shutdown, not absence of every escaped packet.

For a prepared checkout, run the repository's required tests from `V2rayNG/`; Windows uses `gradlew.bat` and other systems use `./gradlew`. Typical Play Store tasks are:

```text
./gradlew :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin
./gradlew :app:assemblePlaystoreDebug :app:assemblePlaystoreDebugAndroidTest
```

For targeted instrumentation, after installing the matching app/test APKs and preparing the device:

```text
adb -s <serial> shell am instrument -w --no-hidden-api-checks -e class com.v2ray.ang.shizuku.TetheringLifecycleTest -e backend native com.v2ray.ang.test/androidx.test.runner.AndroidJUnitRunner
adb -s <serial> shell am instrument -w --no-hidden-api-checks -e class com.v2ray.ang.shizuku.TetheringLifecycleTest -e backend hev com.v2ray.ang.test/androidx.test.runner.AndroidJUnitRunner
```

These package names are for the Play Store variant; verify the installed instrumentation component with `adb -s <serial> shell pm list instrumentation`. Hidden-API relaxation is for the isolated test invocation, not a production setup requirement. Do not leave device-wide testing-policy changes behind. Follow repository build instructions for the pinned AndroidLibXrayLite AAR and HEV libraries; successful compilation with stale native inputs is not validation. Inspect packaged `libgojni.so`, `libhev-socks5-tunnel.so`, and `libhevsockstun.so` for the tested ABI.

### C. Device and real-client review matrix

Run both backends on at least one API 33–35 device and one API 36+ device. Check an older API where the feature is hidden. Add a physical phone/client pair: emulator upstream/interface observations cannot substitute for USB or Wi-Fi client traffic. Unsupported hardware scenarios should be explicitly marked Not run, with the reason.

| Scenario | Procedure | Evidence / expected outcome |
| --- | --- | --- |
| Permission and absence | Test missing/stopped Shizuku, grant, deny, and reconnect | Truthful status; no false connected/routed state; normal app still works |
| Fresh hotspot | Main VPN running; enable hotspot through the page | Route ready before validated success; actual upstream matches owned TUN; client uses expected outbound |
| Pre-existing hotspot | Start hotspot in Android Settings, then enable protected routing | Old downstream is stopped/re-established and validated; do not count earlier direct traffic as protected |
| USB and mixed downstreams | Repeat with USB already active, USB enabled later, and Wi-Fi plus USB | Observe actual types/upstream and traffic from each client; independent per-type diagnostics |
| Start race | Start/change main profile, immediately enable hotspot; repeat | No success on wrong upstream; bounded cancellation/retry or valid protected start |
| Rapid toggles | Enable hotspot then request protected-routing Stop as soon as UI permits; close/reopen during operation | No orphan pending hotspot; UI remains usable; primary VPN remains running |
| Wi-Fi handover | Repeat while attached to station Wi-Fi, including supported/unsupported concurrency | Record OEM result and core reload; error 5 alone is not a diagnosis; no assumed automatic mobile-data switch |
| Profile/group switch | Use two distinguishable exits/rules; switch profile and policy group | New client flows obey replacement config; retained TUN where reusable; native balancer state need not match main runtime exactly |
| Main-core normal Stop | Keep client probes running, stop main connection, then restart | Dead protected TUN while paused; no unintended physical upstream; restored routing follows new launch |
| Main-core abrupt death | Kill the identified main-core process in an isolated session, not just the activity | Secondary engine stops after death handling; surviving UserService retains protection; new launch can recover |
| Shizuku death | From launcher/background, stop Shizuku while main core survives; restart it | Core-held route remains; recovery when replacement Binder arrives, including foreground fallback where needed |
| Isolated UserService death | Terminate only the identified UserService while server/core survive | Record retained route and actual recovery trigger; do not infer server-death notification occurred |
| Failed main restart | Use an intentionally unusable replacement configuration | Secondary engine does not keep serving the old profile; retained route or downstream shutdown with error |
| Bad upstream after success | On a dedicated device, provoke an upstream change; also test blocked lifecycle work | All affected downstream stops requested without waiting on lifecycle monitor; warning/status update; capture traffic during transition |
| Failed cleanup | Fault-inject stop failure or unavailable state without sensitive clients | Error keeps retained route stoppable; resource release must not be treated as success without downstream absence |
| DNS combinations | Local DNS on/off; custom VPN DNS; IPv4-only and IPv6 DNS settings; client DNS probes | Effective routing and DNS match intended config; no reliance on synthetic DNS hint as a real server |
| IPv6 combinations | IPv6 off/on with Wi-Fi and USB; obtain client addresses/routes and exercise IPv4 and IPv6 separately | Prefix diagnostic matches downstream observation; real client connectivity recorded separately; unknown is not silently IPv4-only |
| Native assets / HEV prerequisites | Use geodata/custom assets; change an asset; HEV with valid then unavailable SOCKS | Current assets used; useful failure evidence; running worker not confused with reachable upstream |
| UI lifecycle/accessibility | Leave/reopen, recreate activity, foreground after Shizuku restart; touch/keyboard/D-pad/TalkBack | No networking teardown from page closure; correct final state; one switch action per row; readable localized diagnostics |
| Packaging/update | Debug and release/shrunk build; update while daemon exists | Current service version/contract used; typed hidden callback preserved; correct native libraries packaged |

For packet-level review, collect both new-connection and established-flow behavior with a controlled destination. Compare the client's egress with the intended proxy/direct policy; record IPv4, IPv6, TCP, UDP, and DNS separately. Where a zero-leak claim matters, capture traffic at the controlled upstream/client during fault injection. Interface names, successful HTTP requests, and a green UI label alone cannot establish that claim.

Useful read-only device observations include:

```text
adb -s <serial> shell dumpsys tethering
adb -s <serial> shell dumpsys connectivity
adb -s <serial> shell ip addr
adb -s <serial> logcat -d -s ShizukuTethering ShizukuSyncReceiver TetheringPlatformCompat TetheringLifecycleTest
```

`dumpsys` is a diagnostic tool here, not a production dependency. Also capture relevant app/framework logs when these tags are insufficient, but redact secrets and client identifiers before publication. Record process identities before fault injection so a test does not accidentally kill both protective holders.

For each result, record source SHA, build variant, native-library revisions, ABI, Android/API and firmware, Shizuku mode/version, backend, relevant settings, downstream types, observed interface, client-side result, and final cleanup state. Separate static reasoning, automated assertions, emulator results, and physical traffic evidence. This guide's checklists are not a completed test report.

## 15. Maintaining this feature

When reviewing an upstream merge or a future simplification, preserve these invariants:

- The main daemon remains the authority for the running configuration; normal VPN/proxy/root lifecycle behavior must not be replaced by UI assumptions.
- The protected route is not removed before downstream shutdown is established.
- An engine can stop while its TUN intentionally remains alive.
- Pending starts count during cleanup even before Android reports active interfaces.
- Session identity, launch identity, resource ownership, and observed forwarding state remain distinct.
- Unsafe-upstream stop dispatch must not queue behind the normal lifecycle monitor.
- Closing a page must not destroy the routing daemon, and a completed remote mutation must retain its bookkeeping.
- Older supported APIs still use their compatible adapter; newer typed APIs do not remove unrelated hidden dependencies.
- Error/status text and tests must describe observed behavior, not promise universal OEM compatibility or an atomic firewall guarantee.

Update this guide when one of those contracts changes, especially when adding/removing IPC fields, changing process ownership, changing the network-lifetime mechanism, or replacing hidden APIs. Link new regression tests to the relevant invariant. Prefer comments explaining why the unusual ordering exists over restating the next line of code.

[snapshot]: V2rayNG/app/src/main/java/com/v2ray/ang/dto/HotspotRoutingSnapshot.kt
[sync-dto]: V2rayNG/app/src/main/java/com/v2ray/ang/dto/HotspotRoutingSync.kt
[aidl]: V2rayNG/app/src/main/aidl/com/v2ray/ang/shizuku
[status]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/TetheringStatusSnapshot.kt
[config]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/HotspotRoutingConfig.kt
[core-sync]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/TetheringCoreSync.kt
[sync-receiver]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/ShizukuRoutingSyncReceiver.kt
[service]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/ShizukuTetheringService.kt
[compat]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/TetheringPlatformCompat.kt
[api36]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/TetheringApi36.kt
[shell-context]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/ShellContextCompat.kt
[viewmodel]: V2rayNG/app/src/main/java/com/v2ray/ang/ui/ShizukuViewModel.kt
[activity]: V2rayNG/app/src/main/java/com/v2ray/ang/ui/ShizukuActivity.kt
[screen]: V2rayNG/app/src/main/java/com/v2ray/ang/ui/TetheringScreen.kt
[core-manager]: V2rayNG/app/src/main/java/com/v2ray/ang/core/CoreServiceManager.kt
[application]: V2rayNG/app/src/main/java/com/v2ray/ang/AngApplication.kt
[foreground]: V2rayNG/app/src/main/java/com/v2ray/ang/shizuku/ShizukuForegroundRecovery.kt
[tproxy]: V2rayNG/app/src/main/java/com/v2ray/ang/service/TProxyService.kt
[hev-config]: V2rayNG/app/src/main/java/com/v2ray/ang/service/HevTunnelConfig.kt
[app-config]: V2rayNG/app/src/main/java/com/v2ray/ang/AppConfig.kt
[manifest]: V2rayNG/app/src/main/AndroidManifest.xml
[drawer]: V2rayNG/app/src/main/java/com/v2ray/ang/ui/main/MainDrawer.kt
[bools]: V2rayNG/app/src/main/res/values/bools.xml
[bools33]: V2rayNG/app/src/main/res/values-v33/bools.xml
[app-build]: V2rayNG/app/build.gradle.kts
[versions]: V2rayNG/gradle/libs.versions.toml
[workflow]: .github/workflows/build.yml
[test-config]: V2rayNG/app/src/test/java/com/v2ray/ang/shizuku/HotspotRoutingConfigTest.kt
[test-sync]: V2rayNG/app/src/test/java/com/v2ray/ang/shizuku/TetheringCoreSyncTest.kt
[test-compat]: V2rayNG/app/src/test/java/com/v2ray/ang/shizuku/TetheringPlatformCompatTest.kt
[test-monitor]: V2rayNG/app/src/test/java/com/v2ray/ang/shizuku/TetheringUpstreamMonitorTest.kt
[test-ui]: V2rayNG/app/src/test/java/com/v2ray/ang/ui/TetheringUiStateTest.kt
[test-hev]: V2rayNG/app/src/test/java/com/v2ray/ang/service/HevTunnelConfigTest.kt
[test-teardown]: V2rayNG/app/src/test/java/com/v2ray/ang/service/CoreTeardownDecisionTest.kt
[test-device]: V2rayNG/app/src/androidTest/java/com/v2ray/ang/shizuku/TetheringLifecycleTest.kt
[shizuku-api]: https://github.com/RikkaApps/Shizuku-API
[vpn-api]: https://developer.android.com/reference/android/net/VpnService
[test-network-api]: https://android.googlesource.com/platform/packages/modules/Connectivity/+/311feaff8b4a66b0c8a7bc5ed72f916d666c683e/framework/src/android/net/TestNetworkManager.java
[tethering-api]: https://developer.android.com/reference/android/net/TetheringManager#TETHER_ERROR_INTERNAL_ERROR
[wifi-concurrency]: https://source.android.com/docs/core/connect/wifi-sta-ap-concurrency

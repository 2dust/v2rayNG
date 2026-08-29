# Service code agent guide

These rules apply to the service and service-adjacent paths listed in the repository-root
`AGENTS.md`. Follow both guides; apply the precedence rule defined by the root guide.

## Process and lifecycle invariants

- The daemon process `:RunSoLibV2RayDaemon` is the only authority for whether the native
  core is running. Do not use a UI-process singleton, cached Boolean, bound-service
  assumption, or successful broadcast send as proof of daemon state.
- Route every app-initiated VPN, proxy-only, and root start through `LauncherManager`.
  Route service commands and their results through `MessageHelper`. Put lifecycle state
  shared by two or more run modes in `CoreServiceManager` or `ServiceControl`. Do not
  load or query the native core from the UI process to decide start, stop, or restart.
- Keep Android-owned `VpnService` entry paths intact. `CoreVpnService` must accept a null
  restart intent and the `VpnService.SERVICE_INTERFACE` action without requiring the
  app-initiated `LauncherManager` path.
- A start path is idempotent only when a second equivalent command leaves exactly one
  native core, tunnel, foreground notification update loop, worker set, and root rule
  set. Every edit to start dispatch or `onStartCommand` must preserve that result.
- In each `onStartCommand`, enter foreground before reading configuration, opening a
  file or socket, starting a coroutine that performs setup, invoking native code, or
  executing a root command. `CoreVpnService`, `CoreProxyOnlyService`, and
  `CoreRootService` must call `NotificationManager.ensureForeground()`;
  `CoreTestService` and `SubscriptionUpdateService` must call
  `NotificationHelper.startForeground()`.
- On setup failure, cancel the setup job, release every resource created by that attempt,
  remove partial root or VPN state, and stop the failed service instance. Do not return a
  sticky restart mode unless `CoreServiceManager.isRunning()` is true, the service owns
  every tunnel or root resource required by its run mode, and the Android restart path
  reconstructs configuration, native core, notification, and mode-owned resources
  without UI-process state.
- Preserve these ownership boundaries: `CoreVpnService` owns the VPN interface and
  socket protection; `CoreProxyOnlyService` owns local-proxy mode; `CoreRootService`
  owns root routing. Code used by two or more of these services belongs in their common
  lifecycle layer. Code used by one mode remains in that mode's service.

## Cleanup, concurrency, networking, and IPC

- Teardown must prevent new work before releasing dependencies. Mark the service as
  stopping, reject or invalidate pending start/reload work, cancel and join setup jobs,
  then release owned resources. Remove root routing before stopping its core listener.
  Close each VPN descriptor and tun2socks resource once on setup failure, revoke, stop,
  and destroy; make repeated teardown calls no-ops after the first close.
- Every new coroutine launched by a service must be a child of a job or scope stored by
  that service or the shared lifecycle owner. Cancel that owner during stop and
  `onDestroy`. Do not use `GlobalScope`, an anonymous standalone `CoroutineScope`, or a
  job whose parent outlives the service and later recreates routes, notifications,
  files, or native state.
- Do not add root commands, file I/O, network probes, or native calls directly to
  `onCreate`, `onStartCommand`, `onRevoke`, or `onDestroy` on the main thread. Perform
  the operation in the service-owned scope. If traffic-leak prevention requires the
  callback to await completion, the call site must contain an explicit timeout and a
  comment naming the leak-prevention ordering; an unbounded wait is prohibited.
- A handover belongs to the `NetworkMonitor` instance that scheduled it.
  `NetworkMonitor.unregister()` must cancel pending handover work and prevent its
  callback from entering after `unregister()` returns. Immediately before reload, the
  handover handler must verify that its monitor instance is still the instance owned by
  `CoreServiceManager` and that the core is running. Stop and destroy must unregister
  the monitor before clearing it or stopping the core.
- Every app-internal broadcast intent must set its package to `AppConfig.ANG_PACKAGE`;
  every app-internal service intent must use an explicit `ComponentName`. Do not add an
  implicit broadcast or service intent. Each payload must implement the serialization
  contract used by its receiver. When a caller must distinguish `handled by daemon`
  from `daemon absent`, return an acknowledgement/result from the daemon. Broadcast
  delivery, bind success, and command enqueue success are not acknowledgements.

## Native resources, logging, and validation

- `TProxyService` loads the JNI library `libhev-socks5-tunnel.so`; root mode executes
  the separately packaged `libhevsockstun.so`. A native or packaging diff must inspect
  the produced APK as a ZIP and confirm both HEV files and `libgojni.so` for every ABI
  selected by `ABI_FILTERS` or, when that property is absent, every ABI in the app's
  default `splits.abi` block.
- A recoverable start, stop, reload, handover, or cleanup failure log must include the
  run mode, lifecycle phase, stable non-secret profile/group identifier when one exists,
  failed operation, and exception. Apply the root guide's secret-redaction rule.
- Move deterministic lifecycle decisions into pure helpers and add a JVM regression test
  for each changed decision. Apply this scenario mapping to service changes:
  - Start dispatch, `onStartCommand`, or command deduplication: cold start and two
    equivalent consecutive start commands.
  - Foreground setup or setup-error handling: successful setup and failure after at
    least one resource has been acquired.
  - Stop or teardown: normal stop, repeated stop, and stop while setup is in flight.
  - Restart or network handover: successful restart/handover and a stop racing the
    pending restart/handover.
  - Shared lifecycle code: run the mapped scenarios in VPN, proxy-only, and root modes.
    Mode-owned code: run them in the owning mode.
- Every service lifecycle scenario above requires an emulator or physical device; a JVM
  test or assembled APK is not a substitute. Record each scenario that did not run under
  `Not run`, with the device requirement or blocker. Do not claim the service change is
  runtime-verified when any mapped scenario is unrun.

# Multi-core Engine RFC

## Summary

This proposal introduces a runtime abstraction layer for `v2rayNG` so the app can evolve from a single embedded Xray runtime into a multi-core client that can host both `Xray` and `sing-box`.

The current branch is no longer architecture-only:

- native `VMESS` / `VLESS` / `TROJAN` profiles continue to run on `Xray`;
- explicitly marked `[sing-box]` `CUSTOM` profiles run on `SingBoxEngine`;
- Android VPN startup, foreground-service sync, line testing, and AnyTLS raw runtime bootstrap have been exercised on device.

## Background

`v2rayNG` currently couples its Android service lifecycle, delay testing, stats access, and process lookup directly to `libv2ray.CoreController`.

That design works well for an embedded Xray runtime, but it becomes a blocker for:

- supporting multiple cores side by side;
- selecting a core per profile or per subscription item;
- routing mixed subscriptions to the correct runtime automatically;
- introducing a `sing-box` implementation without threading new engine-specific branches through the entire app.

## Goals

- Introduce a stable app-level `CoreEngine` interface.
- Isolate Xray-specific bindings behind `XrayCoreEngine`.
- Add a single selection hook for future per-profile core resolution.
- Keep runtime selection centralized and reviewable.
- Add a minimal but usable `sing-box` path for Android `CUSTOM` profiles.
- Preserve existing `Xray` behavior for native profile types.

## Non-goals

- Full feature parity between `Xray` and `sing-box`.
- Automatic per-subscription routing for every protocol family.
- Replacing native `Xray` handling for `VMESS` / `VLESS` / `TROJAN`.
- Final UI or storage model for long-term multi-core metadata.

## Proposed Architecture

### 1. App-facing engine contract

Introduce:

- `CoreType`
- `CoreCapability`
- `CoreEventHandler`
- `AppProcessFinder`
- `CoreEngine`
- `CoreEngineFactory`

These types define what the Android app needs from a runtime without exposing `libv2ray` classes to the rest of the service layer.

### 2. Xray adapter

Add `XrayCoreEngine` as the current implementation of `CoreEngine`.

Responsibilities:

- initialize the embedded Xray environment;
- create and own the `libv2ray.CoreController`;
- adapt app callbacks to `CoreCallbackHandler`;
- adapt app process lookup to `libv2ray.ProcessFinder`;
- provide stats and delay APIs through the common contract.

### 3. Core selection hook

Add `CoreSelector.resolve(profile)` as a central decision point.

Current behavior:

- profiles marked as `[sing-box]` and stored as `CUSTOM` resolve to `CoreType.SING_BOX`;
- all other native profile types currently resolve to `CoreType.XRAY`.

Future behavior:

- resolve per-profile, per-subscription, or per-protocol core choice;
- support automatic fallback and compatibility checks;
- support mixed subscriptions that require different runtimes.

## Why this shape

This structure keeps the existing runtime stable while creating extension seams in the right places:

- `CoreServiceManager` depends on `CoreEngine`, not `CoreController`.
- Xray-specific code stays inside the Xray adapter.
- Future `SingBoxEngine` can be added without rewriting service orchestration again.

## sing-box integration plan

The recommended Android path is:

- keep `Xray` embedded as today;
- integrate `sing-box` as a dedicated runtime implementation;
- prefer process or executable isolation for `sing-box` if JNI or Go runtime conflicts appear.

### Current scaffold status

The follow-up skeleton adds:

- `SingBoxEngine` as a non-selected runtime implementation;
- `SingBoxRuntimeLayout` to define stable locations for the future binary, config, and log files;
- a process bootstrap path that writes config, installs the binary from apk assets when available, and prepares `sing-box run -c ...`.

Expected apk asset layout for future runtime packaging:

- `app/src/main/assets/sing-box/arm64-v8a/sing-box`
- `app/src/main/assets/sing-box/armeabi-v7a/sing-box`
- `app/src/main/assets/sing-box/x86_64/sing-box`
- `app/src/main/assets/sing-box/x86/sing-box`

Current local test entry:

- only `CUSTOM` profiles are eligible;
- a profile remark prefixed with `[sing-box]` is resolved to `SingBoxEngine`;
- the runtime uses the raw custom JSON from MMKV instead of the Xray config builder.

Current Android runtime status:

- `HEV tun2socks` JNI loading is gated so capability probing does not crash the VPN process;
- `CoreVpnService` foreground startup has been tightened to avoid the prior stuck-on-start path;
- `sing-box 1.12` DNS bootstrap is generated with `hosts_dns`, `direct_dns`, `remote_dns`, `dns.rules`, and `route.default_domain_resolver`;
- old `SERVER_RAW` entries are normalized at runtime so existing imported nodes do not require manual re-import after every DNS-template fix;
- `SingBoxEngine.measureDelay()` now performs a real local-proxy HTTP delay check instead of returning a placeholder failure;
- foreground UI state is refreshed against the actual service/runtime state when returning to the app.

Current parser bridge status:

- `Clash YAML` documents with a top-level `proxies:` list can be converted into `CUSTOM` profiles backed by raw `sing-box` JSON;
- direct `anytls://` import can be converted into `CUSTOM` profiles backed by raw `sing-box` JSON;
- base64 subscriptions that decode into mixed URI lines can now keep native Xray-supported links on the legacy path while routing `anytls://` lines to `SingBoxEngine`;
- subscription metadata lines such as remaining-traffic or expiry notices should be filtered before profile creation.

Follow-up phases:

1. Add `SingBoxEngine` bootstrap and runtime lifecycle.
2. Extend `CoreSelector` with protocol and capability-based resolution.
3. Add per-profile core preference and automatic selection metadata.
4. Implement mixed-subscription routing and fallback rules.
5. Add UI and settings for engine visibility, priority, and failure handling.

## Feature targets for follow-up phases

Expected `sing-box` support areas:

- TUN
- per-app proxy routing
- rule sets
- proxy chains
- real delay testing
- DNS module management

These targets should be implemented behind the common engine contract instead of leaking engine-specific branching into UI and service code.

## Validation status

Device-side regression checks completed on the current branch:

- `sing-box` path:
  - scanned/imported `[sing-box]` `CUSTOM` profiles start through `CoreVpnService`;
  - runtime config is written under `no_backup/core-sing-box/runtime/config.json`;
  - line test succeeds with a real delay result;
  - `Google` is reachable in Chrome after the VPN is enabled.
- `xray` path:
  - imported native `VMESS` and `VLESS` profiles remain visible as native types in the server list;
  - starting a `VMESS` node logs `Xray 26.5.3 started`;
  - line test succeeds with a real delay result;
  - `Google` is reachable in Chrome after the VPN is enabled.

Current evidence supports the intended split:

- `[sing-box]` `CUSTOM` -> `SingBoxEngine`
- native `VMESS` / `VLESS` / `TROJAN` -> `XrayCoreEngine`

## Review strategy

This PR is still structured to keep the architecture understandable, but it now includes a constrained functional slice:

- common engine abstractions and centralized selection;
- a minimal Android-usable `sing-box` runtime path for explicit `CUSTOM` profiles;
- regression coverage to verify that enabling `sing-box` does not break the existing `Xray` path.

## Open questions

- How should profile metadata persist the selected or resolved core?
- Should `sing-box` use an embedded library, a dedicated process, or both?
- Which subscription formats should trigger automatic core resolution in phase two?

# v2rayNG agent guide

## Scope and precedence

This file applies to the entire repository. Read the applicable scoped guide before editing any
listed path, including when the agent starts at the repository root:

- Read `V2rayNG/app/src/main/java/com/v2ray/ang/service/AGENTS.md` for
  `V2rayNG/app/src/main/java/com/v2ray/ang/service/`,
  `V2rayNG/app/src/test/java/com/v2ray/ang/service/`, and these paths under
  `V2rayNG/app/src/main/java/com/v2ray/ang/`: `core/CoreServiceManager.kt`,
  `core/LauncherManager.kt`, `root/`, `contracts/ServiceControl.kt`,
  `contracts/Tun2SocksControl.kt`, `handler/NotificationManager.kt`,
  `helper/MessageHelper.kt`, and `helper/NotificationHelper.kt`.
- Read `V2rayNG/app/src/main/java/com/v2ray/ang/ui/AGENTS.md` for
  `V2rayNG/app/src/main/java/com/v2ray/ang/ui/` and
  `V2rayNG/app/src/test/java/com/v2ray/ang/ui/`.

Follow both guides in scoped paths. A scoped rule overrides a root rule only when they require
incompatible actions; every other root rule remains active.

## Requirement language

- `must`, `must not`, `only`, and `never` are mandatory. Only the current user request or linked
  acceptance criteria can state an exception; agent inference cannot.
- `Task scope` is the named files and behavior plus dependency, test, or build edits that a
  compiler or required validation proves necessary. Every other change is outside scope.
- `Changed behavior` includes direct and downstream build-time or runtime effects through callers,
  consumers, storage, IPC, and resources.
- `Supported` versions, ABIs, distributions, and locales are exactly those declared by
  `V2rayNG/app/build.gradle.kts` and `.github/workflows/build.yml` inputs.
- A behavior is `verified` only when its named check passed on branch HEAD. Otherwise list the
  exact unrun check and blocker, and do not call the behavior verified.

## Existing code is not a compliance baseline

- These guides define the target state; they do not claim that this checkout, an older commit, or
  another worktree complies. Existing code shows current behavior and is not permission to repeat
  a pattern.
- A `touched declaration` is any function, property, class, composable, resource entry, manifest
  element, or Gradle block containing an added or removed line. Inspect it and every caller or
  consumer whose contract changes against all applicable rules before editing.
- Every new declaration and changed behavior must comply. Inspect a helper or pattern before
  reusing it. Tests prove only their assertions; names, green builds, and prior approval prove
  nothing else.
- Do not repair unrelated violations. Fix a violation left on a task-changed execution path in the
  same branch; if that would change behavior outside scope, request approval first. Report any
  remaining violation that blocks required validation.

## Agent-guide synchronization and PR boundary

- `AGENTS.md` files are the only rule source. `CLAUDE.md`, `GEMINI.md`, and
  `.github/copilot-instructions.md` are routing shims and must only load or point to applicable
  `AGENTS.md` files.
- Feature, fix, and release branches use instruction files from fetched `upstream/master` as their
  baseline. If `upstream` is absent, run
  `git remote add upstream https://github.com/2dust/v2rayNG.git`; otherwise require
  `git remote get-url upstream` to print exactly that URL. Successfully run
  `git fetch upstream master` before comparison. An agent-instruction branch uses its HEAD and
  working diff as the proposal.
- At task start and after each upstream merge or rebase, synchronize canonical instructions into
  the worktree without deleting local-only text. Compare them with:

  ```sh
  git diff upstream/master -- ':(glob)**/AGENTS.md' ':(glob)**/CLAUDE.md' ':(glob)**/GEMINI.md' .github/copilot-instructions.md
  ```

  After synchronization, every displayed difference must be inside a local-only block. If a branch
  predates the files, keep copied versions untracked or unstaged; acquire tracked copies only by
  merging or rebasing their upstream commit.
- User-, machine-, worktree-, and temporary text is local-only. Put it in `AGENTS.md` between
  visible lines `LOCAL INSTRUCTIONS - START` and `LOCAL INSTRUCTIONS - END`.
  Unless the task targets agent instructions, treat every pre-existing instruction-file difference
  as local-only.
- Keep local-only text unstaged and uncommitted. Never use `git add .`, `git add -A`, or
  `git commit -a` in that worktree. Stage task paths explicitly; never delete, restore, or overwrite
  local instructions to clean the worktree.
- Before each commit, push, or PR update outside agent-instruction scope, run both commands. Both
  must print no paths; otherwise stop and remove the instruction change from the staged or branch
  diff without altering its working copy.

  ```sh
  git diff --cached --name-only -- ':(glob)**/AGENTS.md' ':(glob)**/CLAUDE.md' ':(glob)**/GEMINI.md' .github/copilot-instructions.md
  git diff --name-only upstream/master...HEAD -- ':(glob)**/AGENTS.md' ':(glob)**/CLAUDE.md' ':(glob)**/GEMINI.md' .github/copilot-instructions.md
  ```

- Commit instruction files only for a task explicitly limited to agent instructions. The branch
  and PR may contain only `AGENTS.md` and necessary routing-shim changes; name agent instructions
  in the PR title and state that exclusive scope in the body.

## Project boundaries

- The Android project is under `V2rayNG/` and uses Kotlin, Gradle Kotlin DSL, Compose, Material 3,
  coroutines, and AndroidX lifecycle. Read dependency versions from
  `V2rayNG/gradle/libs.versions.toml`, SDK levels from `V2rayNG/app/build.gradle.kts`, and CI tools
  from `.github/workflows/build.yml`; never copy those numbers into a guide.
- `AngApplication` initializes MMKV, app locales, WorkManager, defaults, and theme state. Persist
  application data through `MmkvManager` or `SettingsManager`; do not create `SharedPreferences`
  or another path for data they own.
- `core/` owns native configuration and lifecycle, `handler/` data and application operations,
  `service/` Android services, and `ui/` Compose activities, components, and ViewModels. Put new
  code in its owner unless a scoped guide names a narrower owner.
- Shared lifecycle and configuration code must retain VPN, proxy-only, and root-mode branches.
  Change one mode only when task scope names it; gate the change and preserve the other modes.
- `AndroidLibXrayLite` is native-core source. `V2rayNG/app/libs/libv2ray.aar` and the HEV libraries
  are generated inputs; do not modify or commit them unless task scope upgrades a native dependency.
  Such a PR must identify the source revision and verify every app-declared ABI.

## Build and validation

Before building a clean checkout, initialize submodules recursively and reproduce the HEV and AAR
steps in `.github/workflows/build.yml`. The AAR revision must match the `AndroidLibXrayLite` gitlink
from `git ls-tree HEAD AndroidLibXrayLite`. Take build-tool versions from that workflow and
`V2rayNG/gradle/libs.versions.toml`.

Run Gradle from `V2rayNG/`, using `gradlew.bat` in Windows PowerShell or `./gradlew` on POSIX. Use
the Play Store debug variant unless task scope names another distribution.

Apply every matching validation rule:

- Documentation only: run `git diff --check`; no Gradle task is required.
- Kotlin/Java production code: run the test class that asserts each changed behavior, then
  `:app:testPlaystoreDebugUnitTest` and `:app:compilePlaystoreDebugKotlin`.
- Resources, manifest, Gradle, dependencies, or native packaging: run
  `:app:assemblePlaystoreDebug`.
- F-Droid only: replace `Playstore` with `Fdroid`. Run both variants when shared code branches on a
  flavor, `BuildConfig.DISTRIBUTION`, or a flavor-specific resource or dependency.
- Android service lifecycle, framework callback, native interaction, permission, accessibility,
  focus, or state restoration: also run an emulator or physical-device check.

Add or update JUnit tests under `V2rayNG/app/src/test/java/` for changed deterministic logic. Report
an unavailable required check under the exact label `Not run`, with its command or scenario and
reason. A compile or assembled APK does not verify runtime behavior.

There is no enforced Kotlin formatter. Preserve local indentation and import order. Do not change
whitespace outside touched declarations or reorder imports except as the diff requires.

## Repository-wide coding rules

- Keep the diff inside task scope; exclude unrelated refactors, upgrades, generated files,
  translation cleanup, renames, and formatting-only changes.
- Use server GUIDs and group IDs for persistence, asynchronous work, and UI state. Never use a
  list index, adapter position, or paging position as server or group identity.
- Run disk, network, package-manager, native, bitmap, and CPU-intensive work off the main thread.
  Own each asynchronous operation with a named lifecycle or ViewModel scope and cancel it when the
  owner ends or newer work supersedes it.
- Put all visible and accessibility text in Android resources. Update every locale in
  `androidResources.localeFilters` for each changed key, preserving placeholders, plurals, and
  formatting tags.
- Never rename or delete a persisted, serialized, routing, import, or export field without a
  backward-compatible migration and a regression test reading the preceding released format.
  Multi-record writes must complete together or restore/remove partial writes on every failure.
- Log recoverable failures through `LogUtil`, including operation, mode/component, non-secret
  stable ID when available, and exception. Never log credentials, full proxy URLs, private keys,
  or exported configurations.
- Before adding a constant, helper, manager, repository, or state holder, search its owner. Extend
  an owner that controls the same data or lifecycle. Create a new abstraction only for different
  ownership or lifetime, and state that distinction in code or the PR.
- A platform workaround needs a comment naming its Android/API or vendor boundary, prevented
  failure, and exact removal condition.

## Deprecated, experimental, and version-gated APIs

- Introduce a deprecated API only for a supported-version fallback lacking the replacement, a
  required deprecated callback/interface member, or a documented platform/vendor defect that
  blocks acceptance criteria. The defect requires a test or linked upstream issue.
- Isolate each permitted deprecated call in one compatibility function or adapter. Comment the
  reason and an exact removal trigger. Use the stable API wherever available.
- Scope `@Suppress("DEPRECATION")` or `@SuppressLint("<IssueId>")` to the containing expression or function.
  Class scope is allowed only when every member implements the same required deprecated interface;
  file, package, and module scope is forbidden. Never suppress permission, background-execution,
  lifecycle, or security requirements.
- Guard every API above `minSdk` by SDK or extension version in the same function, or use
  `@RequiresApi` only when every entry point is guarded. Mark reusable guards
  `@ChecksSdkIntAtLeast`. Lower versions must use an
  existing API, return explicit unsupported status, or hide/disable the feature; never crash or
  report false success.
- Add or expand an experimental API only when no stable API meets named acceptance criteria, its
  dependency is pinned in `libs.versions.toml`, a project-owned stable interface contains it and
  excludes it from persisted data, IPC payloads, and public shared contracts, a regression test
  covers it, and a comment names the opt-in marker and exact reevaluation condition.
- Put `@OptIn` on the direct function/property, or on a class only when multiple members need it.
  File and module opt-ins are forbidden. Existing experimental use outside scope does not permit a
  new call site.
- A preview/canary SDK or alpha dependency is allowed in production only when the request or linked
  issue requires it. Keep a stable path for supported non-preview devices, gate by runtime API or
  feature availability, and test both paths. After an API guard, opt-in, or lint suppression, run
  `:app:lintPlaystoreDebug` and also `:app:lintFdroidDebug` when behavior differs by distribution.

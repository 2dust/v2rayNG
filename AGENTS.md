# v2rayNG agent guide

## Scope and instruction precedence

This file applies to the entire repository. Read the following scoped guide before
editing any path in its scope, even when the agent starts at the repository root:

- Read `V2rayNG/app/src/main/java/com/v2ray/ang/service/AGENTS.md` before editing
  `V2rayNG/app/src/main/java/com/v2ray/ang/service/`,
  `V2rayNG/app/src/test/java/com/v2ray/ang/service/`, or any of these paths under
  `V2rayNG/app/src/main/java/com/v2ray/ang/`: `core/CoreServiceManager.kt`,
  `core/LauncherManager.kt`, `root/`, `contracts/ServiceControl.kt`,
  `contracts/Tun2SocksControl.kt`, `handler/NotificationManager.kt`,
  `helper/MessageHelper.kt`, and `helper/NotificationHelper.kt`.
- Read `V2rayNG/app/src/main/java/com/v2ray/ang/ui/AGENTS.md` before editing
  `V2rayNG/app/src/main/java/com/v2ray/ang/ui/` or
  `V2rayNG/app/src/test/java/com/v2ray/ang/ui/`.

For a path covered by a scoped guide, follow both files. A scoped rule overrides a root
rule only when the two rules require incompatible actions; all other rules remain in
force.

## Requirement language

- `must`, `must not`, `only`, and `never` are mandatory. An exception is allowed only
  when the current user request or the acceptance criteria in its linked issue state
  that exception. Agent inference does not create an exception.
- `Task scope` means the files and behavior named by the current user request or linked
  acceptance criteria, plus a dependency, test, or build-file edit that compilation or
  a mapped validation check proves is required. Every other cleanup or behavior change
  is outside task scope.
- `Changed behavior` means any build-time or runtime behavior altered by the branch diff
  directly or through a caller, consumer, stored value, IPC contract, or resource.
- `Supported` Android versions, ABIs, distributions, and locales are exactly those
  declared by `V2rayNG/app/build.gradle.kts` and the build inputs used by
  `.github/workflows/build.yml`. Do not use a hard-coded list from this guide.
- A behavior is `verified` only when the named check ran and passed on the branch HEAD.
  If a mandatory check cannot run, list the exact unrun check and the blocking reason;
  do not describe that behavior as verified.

## Existing code is not a compliance baseline

- These guides define the required target state. They do not assert that the current
  checkout, an older commit, or a neighboring worktree follows the rules. Existing code
  demonstrates current behavior only; it is not permission to repeat a pattern.
- A `touched declaration` is the function, property, class, composable, resource entry,
  manifest element, or Gradle block that contains an added or removed line. Before
  editing, inspect every touched declaration and each caller or consumer whose contract
  the diff changes against all applicable guide rules.
- Every new declaration and every changed behavior must comply with these guides. Reuse
  an existing helper or pattern only after inspecting it and confirming that the reused
  behavior complies. A passing existing test is evidence only for the assertions in
  that test; test names, green builds, and prior PR approval do not verify unasserted
  behavior.
- Do not expand task scope to repair an unrelated pre-existing violation. If the planned
  edit leaves a violation on an execution path changed by the task, fix that violation
  in the same branch. If that correction changes behavior outside task scope, stop and
  request scope approval before editing it. Report any uncorrected violation that blocks
  a mandatory validation check.

## AGENTS.md synchronization and PR boundary

- For every feature, fix, or release branch, the canonical guide baseline is every
  `AGENTS.md` tracked by the fetched `upstream/master` ref. Before using that ref, verify
  that `git remote get-url upstream` prints exactly
  `https://github.com/2dust/v2rayNG.git`, then run `git fetch upstream master` and require
  a zero exit status. A branch whose task scope is agent instructions uses its own HEAD
  plus its working guide diff as the proposed guide version.
- At the start of a task and after every merge or rebase from upstream, compare every
  canonical guide with `upstream/master`. Apply canonical
  additions and corrections to the local working copies without deleting local-only
  text. On a branch that tracks the guides, use
  `git diff upstream/master -- AGENTS.md ':(glob)**/AGENTS.md'`; after synchronization,
  every displayed difference must be inside a local-only block.
- Text added for one user, machine, worktree, or temporary workflow is local-only text.
  Put new local-only text between `<!-- LOCAL ONLY: DO NOT COMMIT -->` and
  `<!-- END LOCAL ONLY -->`. Treat every pre-existing guide difference as local-only
  unless the current task explicitly targets agent instructions.
- Local-only text must remain unstaged and uncommitted. Do not use `git add .`,
  `git add -A`, or `git commit -a` in a worktree containing local guide differences.
  Stage each product or test path explicitly. Do not delete, restore, or overwrite local
  guide differences to make `git status` clean.
- If a branch predates the canonical guides, copy the canonical files into that worktree
  for agent use but leave them untracked or unstaged. Incorporate the tracked versions
  by rebasing or merging the upstream commit that introduced them; do not add a
  guide-only commit to a product branch.
- Before every commit and again before every push or PR update on a branch outside agent
  instruction scope, run both commands below with the fetched `upstream/master`. Both
  commands must produce no paths:

  ```sh
  git diff --cached --name-only -- AGENTS.md ':(glob)**/AGENTS.md'
  git diff --name-only upstream/master...HEAD -- AGENTS.md ':(glob)**/AGENTS.md'
  ```

  If either command prints a path, stop. Preserve the local working copy, remove the
  guide change from the staged or branch diff, and rerun both commands. Do not push or
  update the PR until both outputs are empty.
- Commit guide changes only when the current task explicitly requests agent-instruction
  changes. The PR title must name `AGENTS.md` or agent instructions, and the PR body must
  state that agent instructions are the complete scope. Such a branch must contain no
  product-code, dependency, resource, generated-file, or translation changes.

## Project boundaries

- The Android project is under `V2rayNG/` and uses Kotlin, Gradle Kotlin DSL, Jetpack
  Compose, Material 3, coroutines, and AndroidX lifecycle components. Read dependency
  versions from `V2rayNG/gradle/libs.versions.toml`, SDK levels from
  `V2rayNG/app/build.gradle.kts`, and CI tool versions from
  `.github/workflows/build.yml`; do not duplicate those numbers in an `AGENTS.md`.
- `AngApplication` initializes MMKV, app locales, WorkManager, settings defaults, and
  theme state. Store persistent application data through `MmkvManager` or
  `SettingsManager`. Do not create a `SharedPreferences` store or a second persistence
  path for data already owned by either manager.
- `core/` owns native-core configuration and lifecycle, `handler/` owns data and
  application operations, `service/` owns Android services, and `ui/` owns Compose
  activities, components, and ViewModels. Put new code in the owner named here unless
  a scoped guide names a narrower owner.
- Shared lifecycle and configuration code must retain VPN, proxy-only, and root-mode
  branches. Changing only one mode is allowed only when task scope names that mode;
  gate the new behavior to that mode and leave the other two branches unchanged.
- `AndroidLibXrayLite` is the native-core source. `V2rayNG/app/libs/libv2ray.aar` and the
  HEV libraries are generated build inputs. Do not modify or commit those binaries
  unless the task scope explicitly upgrades a native dependency. A native-dependency
  PR must identify the source revision and verify every ABI declared by the app build.

## Build and validation

Before building a clean checkout, initialize all submodules recursively. Reproduce the
HEV and `libv2ray.aar` preparation steps in `.github/workflows/build.yml`; the AAR source
revision must equal the `AndroidLibXrayLite` gitlink shown by
`git ls-tree HEAD AndroidLibXrayLite`. Obtain the JDK, SDK, NDK, and other build-tool
versions from that workflow and `V2rayNG/gradle/libs.versions.toml`.

Run Gradle from `V2rayNG/`. Use `gradlew.bat` in Windows PowerShell and `./gradlew` on a
POSIX shell. If the task does not name a distribution, validate the Play Store debug
variant.

Use this validation mapping:

- Documentation-only diff: run `git diff --check`; no Gradle task is required.
- Kotlin or Java production-code diff: run the test class that asserts each changed
  behavior, then run `:app:testPlaystoreDebugUnitTest` and
  `:app:compilePlaystoreDebugKotlin`.
- Resource, manifest, Gradle, dependency, or native-packaging diff: run
  `:app:assemblePlaystoreDebug`.
- F-Droid-only diff: replace `Playstore` with `Fdroid` in every mapped task. Run both
  variants when shared code branches on a product flavor, `BuildConfig.DISTRIBUTION`,
  or a flavor-specific resource or dependency.
- A changed Android service lifecycle, Android framework callback, native interaction,
  permission flow, accessibility semantic, focus transition, or state-restoration path
  requires an emulator or physical-device check in addition to Gradle tasks.

Add or update JUnit tests under `V2rayNG/app/src/test/java/` for changed deterministic
logic. If a required test or device check cannot run, report it under the exact label
`Not run` with the command or scenario and reason. A successful compile or APK assembly
does not verify runtime behavior.

The repository has no enforced Kotlin formatter. Preserve the indentation and import
ordering used by the edited file. Do not change whitespace outside the edited
declaration and do not reorder imports except to add or remove imports required by the
diff.

## Repository-wide coding rules

- Keep every diff inside task scope. Do not include a refactor, dependency upgrade,
  generated file, translation cleanup, rename, or formatting-only edit unless it is
  required to implement the named behavior.
- Use server GUIDs and group IDs across persistence, asynchronous work, and UI state.
  Do not store or dispatch a list index, adapter position, or paging position as the
  identity of a server or group.
- Execute disk, network, package-manager, native, bitmap-decoding, and CPU-intensive
  operations outside the main thread. Each asynchronous operation must be owned by a
  named lifecycle or ViewModel scope and cancelled when that owner is destroyed or the
  operation is superseded.
- Put every visible or accessibility string in Android resources. For each added or
  changed string key, update every locale in `androidResources.localeFilters` in
  `V2rayNG/app/build.gradle.kts`. Keep the same placeholder names, placeholder types,
  plurals, and formatting tags in every catalog.
- Do not rename or delete a persisted key, serialized field, routing field, import field,
  or export field without a backward-compatible migration and a regression test that
  reads data written by the preceding released format. Multi-record storage updates
  must either complete all related writes or restore/remove partial writes on every
  failure path.
- Log recoverable failures through `LogUtil`. Include the failed operation, run mode or
  component, a non-secret stable identifier when one exists, and the exception. Never
  log credentials, complete proxy URLs, private keys, or exported configurations.
- Before adding a constant, helper, manager, repository, or state holder, search its
  owning package for an existing implementation. Extend the existing owner when it
  already controls the same data or lifecycle. Create a new abstraction only when its
  ownership or lifetime differs, and state that difference in the code comment or PR.
- A platform workaround must contain a comment naming the Android/API or vendor boundary,
  the failure it prevents, and the exact removal condition.

## Deprecated, experimental, and version-gated APIs

- Do not introduce a deprecated API unless at least one condition below is true:
  1. The call is confined to a fallback for supported Android versions on which the
     replacement API does not exist.
  2. Android or a dependency requires implementation of a deprecated callback or
     interface member.
  3. A platform or vendor defect prevents the stable API from meeting a task acceptance
     criterion, and a test or linked upstream issue records that defect.
- Put each permitted deprecated call in one compatibility function or adapter. Add a
  comment with the applicable condition above and an exact removal trigger, such as
  `minSdk >= N`, removal of a named interface, or release of a linked upstream fix.
  Use the stable API on every version where it is available.
- Apply `@Suppress("DEPRECATION")` or `@SuppressLint("<IssueId>")` to the expression or
  function containing the permitted call. Class-level suppression is allowed only when
  every member implements the same required deprecated interface. File-, package-, and
  module-level suppression is prohibited. A suppression must not hide permission,
  background-execution, lifecycle, or security requirements.
- Every call to an API above `minSdk` must be dominated by an SDK or extension-version
  check in the same function, or be inside a function annotated with `@RequiresApi`
  whose every entry point is guarded. Annotate a reused guard with
  `@ChecksSdkIntAtLeast`. The lower-version branch must execute an existing API, return
  an explicit unsupported result, or hide/disable the feature; it must not crash or
  silently report success.
- Do not add or expand an experimental API call or opt-in unless all conditions below
  are met:
  1. No stable API satisfies a named task acceptance criterion.
  2. The experimental dependency version is pinned in `libs.versions.toml`.
  3. The experimental type is contained behind a project-owned stable interface and is
     absent from persisted data, IPC payloads, and public shared contracts.
  4. A regression test covers the behavior supplied by the experimental API.
  5. A comment names the opt-in marker and the exact condition for reevaluation, such as
     a stable release of the providing API.
- Put `@OptIn` on the function or property that directly contains the experimental call.
  Put it on a class only when the marker is required by more than one member of that
  class. File- and module-level opt-ins are prohibited. Do not modify existing
  experimental use outside task scope, and do not use it to justify another call site.
- A preview/canary SDK or alpha dependency is permitted in a production source set only when
  the current user request or linked issue explicitly requires it. Provide a stable path
  for all supported non-preview devices, gate the preview path by runtime API or feature
  availability, and test both paths. Run `:app:lintPlaystoreDebug` after adding an API
  guard, opt-in, or lint suppression; also run `:app:lintFdroidDebug` when the changed
  code or dependency differs by distribution.

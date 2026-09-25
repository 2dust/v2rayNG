# Maintaining this fork

This fork follows upstream [2dust/v2rayNG](https://github.com/2dust/v2rayNG)
and publishes its F-Droid flavor as **Forkray**, under its own application ID
`io.github.acidefluorhydrique.v2rayng`, built from source and signed with this
fork's key. Almost everything routine is automated on free GitHub Actions; this
page covers the one-time setup, the routine, and what still needs a person.

## How it fits together

```
upstream tags x.y.z ──▶ upstream-sync.yml (every 6 h)
                          │ merges cleanly            │ conflicts
                          ▼                           ▼
                 PR "Merge upstream release x.y.z"   issue with the files
                          │ checks: fdroid-source-build.yml
                          │ (build from source, unit tests, both flavors)
                          ▼
                 you review and merge ──▶ git tag vx.y.z && push
                                             │
                                             ▼
                          release.yml: signed build ─▶ GitHub release
                                             └──────▶ F-Droid repo on GitHub Pages

pin-report.yml (weekly) ──▶ issue when Go, a JDK, the NDK or geo data falls behind
```

| Workflow | Runs | Does |
| --- | --- | --- |
| `fdroid-source-build.yml` | pushes and PRs to `master`; manually | Builds the F-Droid flavor from source, runs the unit tests for both flavors, publishes the build manifest. Unsigned. |
| `upstream-sync.yml` | every 6 hours; manually | Proposes the newest upstream release as a PR, or an issue if it conflicts. Never merges. |
| `release.yml` | a pushed tag `v*` | Signed build, GitHub release, F-Droid repository. |
| `pin-report.yml` | weekly; manually | Keeps one issue listing pins that have fallen behind. |
| `build.yml` ("Build APK") | upstream's | Needs upstream's signing key; **disable it** (below). It stays in the tree because deleting it would conflict with every upstream change to it. |

## One-time setup

All of this is under the repository's **Settings**.

1. **Disable upstream's workflow.** Actions → *Build APK* → `···` →
   *Disable workflow*. It fails on every push without upstream's keystore.

2. **Pull requests: allow merge commits only.** General → Pull Requests:
   keep *Allow merge commits*, untick *squash* and *rebase*. A squashed or
   rebased sync PR loses the link to upstream's history, so the next sync sees
   the release as unmerged and conflicts with it.

3. **App signing key.** Generate once, on your own machine, and back the file
   and passwords up somewhere safe. If this key is lost, installed copies can
   never be updated again; users would have to uninstall first.

   ```bash
   keytool -genkeypair -v -keystore app-release.jks -alias app -keyalg RSA -keysize 4096 -validity 10000
   ```

   Secrets and variables → Actions → *New repository secret*, one each:

   | Secret | Value |
   | --- | --- |
   | `APP_KEYSTORE_BASE64` | output of `base64 -i app-release.jks` |
   | `APP_KEYSTORE_PASSWORD` | the keystore password |
   | `APP_KEYSTORE_ALIAS` | `app` |
   | `APP_KEY_PASSWORD` | the key password |

   Then, on the *Variables* tab, add `APP_CERT_SHA256`: the certificate's
   SHA-256 in lowercase hex without colons (the first release prints it in its
   log if unset). Releases signed with any other key are then refused.

4. **F-Droid repository key.** A second, separate key, which signs the
   repository index rather than the APKs:

   ```bash
   keytool -genkeypair -v -keystore fdroid-repo.jks -alias fdroidrepo -keyalg RSA -keysize 4096 -validity 10000
   ```

   Secrets `FDROID_REPO_KEYSTORE_BASE64`, `FDROID_REPO_KEYSTORE_PASSWORD`,
   `FDROID_REPO_KEY_ALIAS` (`fdroidrepo`) and `FDROID_REPO_KEY_PASSWORD`, as
   above. Users verify the repository by this key's fingerprint, so it must
   never change either.

5. **Sync token.** The default `GITHUB_TOKEN` cannot push a branch that
   changes `.github/workflows/` (upstream does that regularly), and pull
   requests it opens do not run checks. Create a fine-grained personal access
   token at <https://github.com/settings/personal-access-tokens/new>:
   *Only select repositories* → this fork; permissions *Contents*, *Pull
   requests*, *Issues* and *Workflows*: **Read and write**. Save it as the
   secret `SYNC_TOKEN`. Note its expiry date: when it expires, sync falls back
   to opening issues until it is replaced.

6. **GitHub Pages**, after the first release has created the `gh-pages`
   branch: Pages → *Deploy from a branch* → `gh-pages`, `/ (root)`. The
   repository is then at <https://acidefluorhydrique.github.io/v2rayNG/repo>.

7. **Keep scheduled workflows alive.** GitHub disables scheduled workflows in
   a public repository after 60 days without activity. Regular syncs count as
   activity; if they stop, re-enable them under Actions.

## Routine: an upstream release

1. A PR *Merge upstream release x.y.z* appears. Its description says whether
   it **updates the Xray core**. Those are security-relevant: users run the old
   core until you release, so do not let them wait.
2. Wait for its checks. If they fail, the failing step says which pin or
   change is at fault; fix it on the PR branch.
3. Merge with *Create a merge commit*.
4. Tag the merge on `master` and push the tag:

   ```bash
   git fetch origin
   git tag v2.3.9 origin/master
   git push origin v2.3.9
   ```

   The tag must be `v` followed by the `versionName` in
   `V2rayNG/app/build.gradle.kts`, on a commit that is on `master`;
   `release.yml` refuses anything else.

Not every upstream release has to be published. Ones that only touch the UI
can be batched; ones that update the core should not be.

## What still needs a person

| Task | How often | Notes |
| --- | --- | --- |
| Merge conflicts | rarely, about once a year so far | Arrive as an issue with the conflicted files and the commands to resolve them. |
| Pin bumps | when the pin report says so | Go, JDKs, NDK, the `forkray-geodata` submodule. One commit each; the release checksums change with every bump. The comment next to each pin says where its next value comes from. |
| Go minor upgrades | about twice a year | When AndroidLibXrayLite's `go.mod` moves past `GO_VERSION`, builds fail on purpose (`GOTOOLCHAIN=local`) and the pin report says so. |
| Reproducibility baseline | after upstream upgrades AGP, Gradle or dependencies | The APK bytes change legitimately; compare two runs again as in docs/reproducible-build.md. |
| A fork-only fix between upstream releases | as needed | Version codes come from upstream's `versionCode`, and a release tag must match `versionName`. Releasing the same upstream version twice therefore needs both raised first (for example `versionName = "2.3.9-1"`), and those lines will conflict with the next upstream sync. Prefer waiting for the next upstream release when you can. |
| Renewing `SYNC_TOKEN` | at its expiry | Sync keeps working in issue-only mode meanwhile. |

## Where Forkray differs from upstream, and why it merges cleanly

Everything that makes the F-Droid build Forkray lives in files upstream does
not touch, or has not touched since creating them, so upstream releases merge
without conflicts:

| What | Where |
| --- | --- |
| Application ID, and the `XRAY_CORE`, `UPDATE_CHECK_ENABLED` and `PROMOTION_ENABLED` flags | the `fdroid` flavor and `defaultConfig` in `V2rayNG/app/build.gradle.kts` |
| Name, in every locale | `V2rayNG/app/src/fdroid/res/values*/strings.xml`: every string whose upstream text names v2rayNG, with the name replaced. If upstream adds a string that names v2rayNG, add its override here. |
| Icons | `V2rayNG/app/src/fdroid/res/mipmap-*`, `drawable-*/ic_stat_name.png`, generated by `branding/make_forkray_icons.py` from upstream's own icons. Rerun it if upstream redraws its icon. |
| Store listing | `fastlane/metadata/android/`, replacing upstream's text and icon. fdroidserver only looks for listings at the repository root, under the recipe's `subdir`, or in a root-level `src/<flavor>/`, so a listing inside `V2rayNG/app/src/fdroid/` would be ignored. Upstream has changed this directory once, when creating it. |
| Shortcuts | `V2rayNG/app/src/fdroid/res/xml/shortcuts.xml` |

The HTTP User-Agent stays `v2rayNG/<version>` on purpose: some subscription
services pick their response format by it.

## If maintenance stops

Say so at the top of the README and in a final release note, and point users
back to upstream v2rayNG. Because this build has its own application ID,
switching means installing upstream's app next to it and moving the
configuration over with *Backup & Restore*.

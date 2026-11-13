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
| `release.yml` | a pushed tag `v*`; manually | Signed build, GitHub release, F-Droid repository; refuses a version already published. Run manually, only rebuilds the F-Droid repository. |
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

   The same workflow keeps the core mirror,
   `AcideFluorhydrique/AndroidLibXrayLite`, up to upstream: its `main` branch
   and every tag. Create a second token, *Only select repositories* → that
   mirror, permission *Contents*: **Read and write**, and save it as the
   secret `MIRROR_GITHUB_ANDROIDLIBXRAYLITE_TOKEN`. Without it the mirror has
   to be updated by hand as described under the routine below.

6. **GitHub Pages**, after the first release has created the `gh-pages`
   branch: Pages → *Deploy from a branch* → `gh-pages`, `/ (root)`. The
   repository is then at <https://acidefluorhydrique.github.io/v2rayNG/repo>.

7. **Mirrors of the F-Droid repository.** Everything above lives on GitHub,
   so the repository is also published to other hosts. Clients find them
   through the signed index and need no new address. Each is an empty public
   repository that `release.yml` force-pushes one commit to, on a branch
   named `pages` (`main` on GitHub):

   | Host | Repository | Secret | Served at |
   | --- | --- | --- | --- |
   | Codeberg | `lanticy/forkray-fdroid` | `CODEBERG_TOKEN`: an access token with *repository: read and write* | <https://lanticy.codeberg.page/forkray-fdroid/fdroid/repo>, and <https://ray.arielx.is-a.dev/fdroid/repo> |
   | GitLab | `HydrofluoricAcid/forkray-fdroid` | `GITLAB_TOKEN`: a project access token, role *Maintainer*, scope `write_repository` | <https://hydrofluoricacid.gitlab.io/forkray-fdroid/fdroid/repo> |
   | 0xacab (Riseup's GitLab) | `lanticy/forkray-fdroid` | `OXACAB_TOKEN`: as for GitLab | <https://lanticy.itcouldbewor.se/forkray-fdroid/fdroid/repo> |
   | GitHub | `AcideFluorhydrique/forkray-fdroid` | `MIRROR_GITHUB_FORKRAY_FDROID_TOKEN`: a fine-grained personal access token for that repository only, *Contents: Read and write* | <https://forkray.is-my.app/fdroid/repo>, the domain in the `FORKRAY_FDROID_DOMAIN` variable, claimed through <https://github.com/jn-aman/is-my.app> |

   On GitLab, Deploy → Pages → untick *Use unique domain*, or the site gets a
   random address instead of the one above; set Settings → General →
   Visibility → *Pages* to *Everyone*, or it answers only to signed-in
   project members; the account must be able to run pipelines, since
   GitLab serves Pages only from the CI job in
   `fdroid-repo/mirror/gitlab-ci.yml`; and under Settings → Repository →
   Protected branches, turn on *Allowed to force push* for `pages`. GitLab
   protects the default branch against force pushes, and every release
   replaces the branch with a single new commit, so without it the first
   release publishes and every later one is refused with only a warning on
   the run. The GitLab branch is listed a second
   time by its raw address,
   <https://gitlab.com/HydrofluoricAcid/forkray-fdroid/-/raw/pages/fdroid/repo>,
   which keeps working when only GitLab Pages is down. The Codeberg site is
   also served under its own domain, `ray.arielx.is-a.dev`, which Codeberg's
   Pages webhook redeploys on every push, so it needs no step of its own in
   `release.yml`; it is listed in `mirrors:` separately. 0xacab runs GitLab
   too and needs the same settings; it serves Pages under `itcouldbewor.se`,
   and its branch is also listed by its raw address.

   The GitHub mirror is a repository of its own so that it can have a custom
   domain: a custom domain on this repository's site would redirect
   <https://acidefluorhydrique.github.io/v2rayNG/> there. Set its Settings →
   Pages → Source to *Deploy from a branch*, `main`, `/ (root)`, once the
   first release has pushed that branch. Unlike the other mirrors it is
   published to `main`, the repository's default branch, since nothing else
   lives there. For the domain, point a DNS `CNAME`
   record for it at `acidefluorhydrique.github.io`, put it in the repository
   variable `FORKRAY_FDROID_DOMAIN` here, enter it under Pages → Custom domain
   there and tick *Enforce HTTPS* once GitHub offers it. A branch-deployed
   site keeps its domain in a `CNAME` file on the branch, which the release
   step writes from the variable on every push; without the variable each
   release would remove the domain again. Its address in `mirrors:` is the
   custom domain, since the github.io one then only redirects there.

   To add a mirror, add it
   to `mirrors:` in `fdroid-repo/config.yml` and give it a step in
   `release.yml`; its address must end in `fdroid`. Give it a `countryCode`
   for where its servers are, which clients show as a flag, unless it is
   behind an anycast CDN. A mirror that fails does not fail the
   release: the run shows a warning, and a manual run of *Release* retries.

   The page shown at each of these addresses is written by
   `fdroid-repo/make-landing-page.py` right after `fdroid update`, from the
   signed index: it lists the main address, every mirror, the signing key
   fingerprint and the files on offer, so a new mirror appears on it without
   further work. Give a host a readable name in its `host_label` if needed.
   The page shows English, with a button to switch to Simplified Chinese;
   write every new text on it in both, through `t()` or `para()`.

8. **Keep scheduled workflows alive.** GitHub disables scheduled workflows in
   a public repository after 60 days without activity. Regular syncs count as
   activity; if they stop, re-enable them under Actions.

## Routine: an upstream release

1. A PR *Merge upstream release x.y.z* appears. Its description says whether
   it **updates the Xray core**. Those are security-relevant: users run the old
   core until you release, so do not let them wait.

   The core submodule is fetched from this fork's own mirror,
   `AcideFluorhydrique/AndroidLibXrayLite`, so that a pinned commit survives
   upstream rewriting or removing its repository. The mirror carries no
   commits of its own: `upstream-sync.yml` pushes upstream's `main` and tags
   to it before proposing a release, so the PR's checks can fetch the new
   commit and the build manifest can name its tag. It never forces a push, so
   if upstream rewrites `main` or moves a tag the job fails instead of
   dropping a commit this fork has pinned; look at what upstream did before
   doing anything about it. Without the token, or to fix things by hand,
   press *Sync fork* on the mirror's `main` and push the tags, which *Sync
   fork* does not copy, from a clone that has both remotes, then re-run the
   PR's checks:

   ```bash
   git fetch https://github.com/2dust/AndroidLibXrayLite.git --tags
   git push https://github.com/AcideFluorhydrique/AndroidLibXrayLite.git --tags
   ```
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

A version is released once. When its APKs are on its GitHub release,
`release.yml` refuses to build that tag again, even if the tag is moved to
another commit: people have already downloaded those APKs and checked them
against a build manifest naming their commit, and one version number with two
different sources is what makes a release unverifiable. If a release run
fails:

- **before the APKs were uploaded** (the build, or the signature check): fix
  the cause on `master`, then move the tag there and push it again
  (`git tag -f vX.Y.Z origin/master && git push -f origin refs/tags/vX.Y.Z`).
  The release has no APKs yet, so this is allowed.
- **while publishing the F-Droid repository**: the release itself is done.
  Fix the configuration on `master`, then run *Release* manually from the
  Actions tab on `master`. A manual run only rebuilds the repository from the
  published releases, with that configuration.

To retry a run without changes (a network error, say), use *Re-run failed
jobs*. *Re-run all jobs* repeats the tag check, which refuses the version once
its APKs are out.

Not every upstream release has to be published. Ones that only touch the UI
can be batched; ones that update the core should not be.

## What still needs a person

| Task | How often | Notes |
| --- | --- | --- |
| Merge conflicts | rarely, about once a year so far | Arrive as an issue with the conflicted files and the commands to resolve them. |
| Pin bumps | when the pin report says so | Go, JDKs, NDK, the `forkray-geodata` submodule. One commit each; the release checksums change with every bump. The comment next to each pin says where its next value comes from. |
| Go minor upgrades | about twice a year | When AndroidLibXrayLite's `go.mod` moves past `GO_VERSION`, builds fail on purpose (`GOTOOLCHAIN=local`) and the pin report says so. |
| Refreshing the gomobile patch | when an Xray core update moves `golang.org/x/mobile` and the build stops at `git apply` | `patches/gomobile-build-in-module.patch` changes `buildAndroidSO` in gomobile's `cmd/gomobile/bind_androidapp.go`. Redo the same change on the new version and regenerate the patch; `build-libv2ray.sh` fails if a library contains its build directory, so a patch that applies but no longer works is caught too. |
| Reproducibility baseline | after upstream upgrades AGP, Gradle or dependencies | The APK bytes change legitimately; compare two runs again as in docs/reproducible-build.md. |
| A fork-only fix between upstream releases | as needed | Version codes come from upstream's `versionCode`, and a release tag must match `versionName`. Releasing the same upstream version twice therefore needs both raised first (for example `versionName = "2.3.9-1"`), and those lines will conflict with the next upstream sync. Prefer waiting for the next upstream release when you can. |
| Renewing `SYNC_TOKEN` | at its expiry | Sync keeps working in issue-only mode meanwhile. |
| Renewing `MIRROR_GITHUB_ANDROIDLIBXRAYLITE_TOKEN` | at its expiry | Until then the core mirror is not updated, and a release that updates the core fails its checks at checkout. |

## Where Forkray differs from upstream, and why it merges cleanly

Almost everything that makes the F-Droid build Forkray lives in files upstream
does not touch, or has not touched since creating them, so upstream releases
merge without conflicts. The exception is the no-silent-direct-connection
change, which has to edit the upstream code that makes those requests:

| What | Where |
| --- | --- |
| Application ID, and the `XRAY_CORE`, `UPDATE_CHECK_ENABLED`, `PROMOTION_ENABLED` and `STRICT_PROXY_FETCH` flags | the `fdroid` flavor and `defaultConfig` in `V2rayNG/app/build.gradle.kts` |
| Name, in every locale | `V2rayNG/app/src/fdroid/res/values*/strings.xml`: every string whose upstream text names v2rayNG, with the name replaced. If upstream adds a string that names v2rayNG, add its override here; if it adds a locale (as 2.3.11 did with `az`), add that locale's file here and its `strings_forkray.xml`. |
| Icons | `V2rayNG/app/src/fdroid/res/mipmap-*`, `drawable-*/ic_stat_name.png`, generated by `branding/make_forkray_icons.py` from upstream's own icons. Rerun it if upstream redraws its icon. |
| Store listing | `fastlane/metadata/android/`, replacing upstream's text and icon. fdroidserver only looks for listings at the repository root, under the recipe's `subdir`, or in a root-level `src/<flavor>/`, so a listing inside `V2rayNG/app/src/fdroid/` would be ignored. Upstream has changed this directory once, when creating it. |
| No silent direct connections ([docs/network.md](network.md)) | `handler/FetchRoutePolicy.kt` and `ui/compose/DirectRetryDialog.kt` are fork-only. The callers (subscription update, geo files, per-app list) are upstream files edited in a few places, so upstream changes to them can conflict: keep each fetch going through `FetchRoutePolicy.fetch`, with upstream's own order as its `legacyOrder`. |
| Strings for fork-only features, in every locale | `V2rayNG/app/src/main/res/values*/strings_forkray.xml`, never `strings.xml`. `values-bqi-rIR` holds Persian text until there is a Bakhtiari translation. |
| Shortcuts | `V2rayNG/app/src/fdroid/res/xml/shortcuts.xml` |
| R8 for release builds, which F-Droid asked for | `isMinifyEnabled` in `V2rayNG/app/build.gradle.kts` and the rules in `V2rayNG/app/proguard-rules.pro`, both upstream files. If upstream adds a class that Gson maps outside `com.v2ray.ang.dto`, or a new JNI entry point, add a rule for it, or that feature breaks only in release builds. |
| README | `.github/README.md`, which GitHub shows in place of upstream's `README.md`; that one stays unchanged. |

The HTTP User-Agent stays `v2rayNG/<version>` on purpose: some subscription
services pick their response format by it.

## If maintenance stops

Say so at the top of `.github/README.md` and in a final release note, and point users
back to upstream v2rayNG. Because this build has its own application ID,
switching means installing upstream's app next to it and moving the
configuration over with *Backup & Restore*.

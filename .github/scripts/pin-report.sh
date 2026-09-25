#!/bin/bash
# Reports reproducible-build pins that have fallen behind, as a single GitHub
# issue that is created, updated or closed to match what it finds.
#
# The pins are deliberate (docs/reproducible-build.md), so nothing is bumped
# automatically: every bump changes the release bytes and should be its own
# reviewed commit. This only makes sure they do not go stale unnoticed.
#
# With DRY_RUN=true it prints the report instead of touching issues.

set -o errexit
set -o pipefail
set -o nounset

DRY_RUN="${DRY_RUN:-false}"
GEO_MAX_AGE_DAYS="${GEO_MAX_AGE_DAYS:-30}"
TITLE="Reproducible-build pins to review"
GEODATA_REPO=AcideFluorhydrique/forkray-geodata
GEODATA_URL="https://github.com/${GEODATA_REPO}"
WORKFLOW=.github/workflows/fdroid-source-build.yml

pin() { sed -nE "s/^  $1: '([^']+)'.*/\1/p" "$WORKFLOW"; }
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }

findings=()

# Go: AndroidLibXrayLite's go.mod decides the minimum; GOTOOLCHAIN=local makes
# the build fail outright once it exceeds the pin.
go_pin=$(pin GO_VERSION)
core_rev=$(git rev-parse HEAD:AndroidLibXrayLite)
# Downloads are read into variables before parsing: a parser that stops at the
# first match would otherwise close the pipe on curl, and pipefail would turn
# the resulting SIGPIPE into a failure.
core_gomod=$(curl -fsSL "https://raw.githubusercontent.com/2dust/AndroidLibXrayLite/${core_rev}/go.mod")
go_required=$(awk '/^go /{print $2; exit}' <<<"$core_gomod")
go_minor="${go_pin%.*}"
go_latest_patch=$(curl -fsSL 'https://go.dev/dl/?mode=json&include=all' \
    | json "max((v['version'][2:] for v in d if v['stable'] and v['version'].startswith('go${go_minor}.')), key=lambda s: [int(x) for x in s.split('.')])")
if [[ "$(printf '%s\n' "$go_required" "$go_minor" | sort -V | tail -n 1)" != "$go_minor" ]]; then
    findings+=("**Go**: AndroidLibXrayLite now requires go ${go_required}, above the pinned ${go_pin}. The build fails until \`GO_VERSION\` is raised.")
elif [[ "$go_latest_patch" != "$go_pin" ]]; then
    findings+=("**Go**: ${go_latest_patch} is out; \`GO_VERSION\` is ${go_pin}. Patch releases often carry security fixes.")
fi

# JDKs: the newest Temurin GA build of each pinned major version.
for var in JAVA_VERSION GOMOBILE_JAVA_VERSION; do
    jdk_pin=$(pin "$var")
    major="${jdk_pin%%.*}"
    jdk_latest=$(curl -fsSL "https://api.adoptium.net/v3/info/release_versions?release_type=ga&vendor=eclipse&page_size=1&sort_order=DESC&version=%5B${major},$((major + 1))%29" \
        | json "d['versions'][0]['semver']")
    if [[ "$jdk_latest" != "$jdk_pin" ]]; then
        findings+=("**JDK ${major}**: Temurin ${jdk_latest} is out; \`${var}\` is ${jdk_pin}.")
    fi
done

# NDK: follow upstream, whose build.yml installs the NDK its releases use.
ndk_pin=$(pin NDK_VERSION)
upstream_build_yml=$(curl -fsSL https://raw.githubusercontent.com/2dust/v2rayNG/master/.github/workflows/build.yml)
ndk_upstream=$(grep -m 1 -oE '"ndk;[0-9.]+"' <<<"$upstream_build_yml" | tr -d '"' | cut -d';' -f2 || true)
if [[ -n "$ndk_upstream" && "$ndk_upstream" != "$ndk_pin" ]]; then
    findings+=("**NDK**: upstream builds with ${ndk_upstream}; \`NDK_VERSION\` and \`android.ndkVersion\` are ${ndk_pin}.")
fi

# Geo data: routing databases go stale. forkray-geodata refreshes its sources
# weekly; this reports when the submodule pin has fallen behind its main
# branch by more than GEO_MAX_AGE_DAYS.
geo_pin=$(git rev-parse HEAD:forkray-geodata)
geo_head=$(git ls-remote "${GEODATA_URL}" refs/heads/main | cut -f1)
if [[ -n "$geo_head" && "$geo_head" != "$geo_pin" ]]; then
    geo_pin_date=$(curl -fsSL "https://api.github.com/repos/${GEODATA_REPO}/commits/${geo_pin}" \
        | json "d['commit']['committer']['date']")
    age=$(( ($(date -u +%s) - $(date -u -d "$geo_pin_date" +%s)) / 86400 ))
    if (( age > GEO_MAX_AGE_DAYS )); then
        findings+=("**Geo data**: the \`forkray-geodata\` submodule is at ${geo_pin:0:7}, ${age} days old; its main branch is at ${geo_head:0:7}. Update with \`git -C forkray-geodata fetch && git -C forkray-geodata checkout origin/main\`.")
    fi
fi

if (( ${#findings[@]} == 0 )); then
    body=""
else
    body="These pins in \`${WORKFLOW}\`, \`V2rayNG/app/build.gradle.kts\` and the \`forkray-geodata\` submodule have fallen behind:

$(printf -- '- %s\n' "${findings[@]}")

Bump each in its own commit and expect the release checksums to change. The comments next to each pin say where its next value comes from; docs/maintenance.md describes the process. This issue is updated weekly and closes itself once nothing is left."
fi

if [[ "$DRY_RUN" == "true" ]]; then
    echo "${body:-All pins are current.}"
    exit 0
fi

existing=$(gh issue list --state open --search "in:title \"${TITLE}\"" --json number --jq '.[0].number // empty')
if [[ -z "$body" ]]; then
    if [[ -n "$existing" ]]; then
        gh issue close "$existing" --comment "All pins are current."
    fi
elif [[ -n "$existing" ]]; then
    gh issue edit "$existing" --body "$body"
else
    gh issue create --title "$TITLE" --body "$body"
fi

#!/bin/bash
# Proposes merging the newest upstream v2rayNG release into this fork.
#
# Upstream tags every release (x.y.z) on its "up x.y.z" commit, so the fork
# follows those tags rather than upstream's master. When the newest one is not
# yet merged into BASE_BRANCH:
#   - if it merges cleanly, push it to a branch upstream-sync/<tag> and open a
#     pull request, whose checks (fdroid-source-build.yml) then build and test it
#   - if it conflicts, open an issue listing the conflicted files instead
# Nothing is ever merged into BASE_BRANCH here; a person reviews and merges.
#
# Needs the git remote "origin" to be pushable, and the gh CLI authenticated
# through GH_TOKEN. With OPEN_PR=false it only opens issues, for when no token
# able to push branches and trigger workflows is configured.

set -o errexit
set -o pipefail
set -o nounset

UPSTREAM_URL="${UPSTREAM_URL:-https://github.com/2dust/v2rayNG.git}"
UPSTREAM_WEB="${UPSTREAM_WEB:-https://github.com/2dust/v2rayNG}"
BASE_BRANCH="${BASE_BRANCH:-master}"
OPEN_PR="${OPEN_PR:-true}"

INSTRUCTION_FILES=(':(glob)**/AGENTS.md' ':(glob)**/CLAUDE.md' ':(glob)**/GEMINI.md' .github/copilot-instructions.md)

if git remote get-url upstream >/dev/null 2>&1; then
    git remote set-url upstream "$UPSTREAM_URL"
else
    git remote add upstream "$UPSTREAM_URL"
fi
# Upstream's tags go under refs/upstream-tags, not refs/tags, so they can never
# be pushed to the fork by accident.
git fetch --quiet --no-tags upstream '+refs/tags/*:refs/upstream-tags/*'
git fetch --quiet origin "$BASE_BRANCH"

latest=$(git for-each-ref --format='%(refname:strip=2)' refs/upstream-tags \
    | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)
if [[ -z "$latest" ]]; then
    echo "No upstream release tags found."
    exit 0
fi
commit=$(git rev-parse "refs/upstream-tags/${latest}^{commit}")

if git merge-base --is-ancestor "$commit" "origin/$BASE_BRANCH"; then
    echo "$BASE_BRANCH already contains upstream $latest."
    exit 0
fi

branch="upstream-sync/$latest"
title="Merge upstream release $latest"
conflict_title="Upstream release $latest does not merge cleanly"

open_issue_once() {
    local issue_title="$1" body="$2"
    if [[ -n "$(gh issue list --state open --search "in:title \"$issue_title\"" --json number --jq '.[].number')" ]]; then
        echo "Issue \"$issue_title\" is already open."
        return
    fi
    gh issue create --title "$issue_title" --body "$body"
}

if git ls-remote --exit-code --heads origin "$branch" >/dev/null 2>&1; then
    echo "Branch $branch already exists; its pull request is pending review."
    exit 0
fi

previous=$(git for-each-ref --format='%(refname:strip=2)' refs/upstream-tags \
    | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | sort -V \
    | while read -r t; do
        git merge-base --is-ancestor "refs/upstream-tags/$t" "origin/$BASE_BRANCH" && echo "$t"
      done | tail -n 1 || true)
compare="$UPSTREAM_WEB/compare/${previous:-$BASE_BRANCH}...$latest"

git switch --quiet -c "$branch" "origin/$BASE_BRANCH"
if ! git merge --quiet --no-ff --no-edit -m "$title" "$commit"; then
    conflicted=$(git diff --name-only --diff-filter=U | sed 's/^/- `/; s/$/`/')
    git merge --abort
    body=$(cat <<EOF
Upstream released [$latest]($UPSTREAM_WEB/releases/tag/$latest) ([changes]($compare)), and it conflicts with this fork in:

$conflicted

Resolve it by hand:

\`\`\`bash
git fetch upstream tag $latest --no-tags
git switch -c $branch origin/$BASE_BRANCH
git merge $latest
# resolve, commit, push, open a pull request into $BASE_BRANCH
\`\`\`
EOF
)
    open_issue_once "$conflict_title" "$body"
    exit 0
fi

core_before=$(git rev-parse "origin/$BASE_BRANCH:AndroidLibXrayLite")
core_after=$(git rev-parse "HEAD:AndroidLibXrayLite")
if [[ "$core_before" != "$core_after" ]]; then
    core_note="**Updates the Xray core** (AndroidLibXrayLite \`${core_before:0:8}\` → \`${core_after:0:8}\`). Release it promptly: users stay on the old core until then."
else
    core_note="Does not change the Xray core."
fi

if [[ -n "$(git diff --name-only "$commit" HEAD -- "${INSTRUCTION_FILES[@]}")" ]]; then
    instruction_note="⚠️ This fork's agent instruction files differ from upstream's after the merge. AGENTS.md requires them to match upstream; fix that before merging."
else
    instruction_note="Agent instruction files match upstream."
fi

# --max-count, not `| head`: with pipefail, head closing the pipe early would
# kill git log with SIGPIPE and fail the script on any release over the limit.
commits=$(git log --no-merges --max-count=60 --format='- %s (%an)' "origin/$BASE_BRANCH..$commit")

body=$(cat <<EOF
Merges upstream release [$latest]($UPSTREAM_WEB/releases/tag/$latest) ([changes]($compare)).

- $core_note
- $instruction_note

The checks on this pull request build the F-Droid flavor from source and run the unit tests for both flavors. After merging, publish with \`git tag v$latest && git push origin v$latest\` (see docs/maintenance.md).

<details><summary>Upstream commits</summary>

$commits

</details>
EOF
)

if [[ "$OPEN_PR" != "true" ]]; then
    open_issue_once "$title" "$body

No SYNC_TOKEN secret is configured, so no branch or pull request was created. Merge it by hand as described in docs/maintenance.md."
    exit 0
fi

git push --quiet origin "$branch"
gh pr create --base "$BASE_BRANCH" --head "$branch" --title "$title" --body "$body"

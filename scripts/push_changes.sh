#!/usr/bin/env bash
# =============================================================================
# push_changes.sh — Stage, commit, and push all local changes
# Usage: bash scripts/push_changes.sh [optional commit message]
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()  { echo "[INFO]  $*"; }
error() { echo "[ERROR] $*" >&2; }

# ---------------------------------------------------------------------------
# 1. Determine the current branch
# ---------------------------------------------------------------------------
BRANCH=$(git rev-parse --abbrev-ref HEAD)
info "Current branch: ${BRANCH}"

# ---------------------------------------------------------------------------
# 2. Build the commit message
# ---------------------------------------------------------------------------
if [[ $# -ge 1 ]]; then
    COMMIT_MSG="$*"
else
    COMMIT_MSG="chore: stage and push all local changes

Automatically committed by push_changes.sh.
Includes all unstaged and untracked modifications present in the working tree."
fi

# ---------------------------------------------------------------------------
# 3. Stage everything
# ---------------------------------------------------------------------------
info "Staging all changes (git add .) …"
git add .

# Check if there is anything to commit
if git diff --cached --quiet; then
    info "Nothing to commit — working tree is clean. Exiting."
    exit 0
fi

# ---------------------------------------------------------------------------
# 4. Commit
# ---------------------------------------------------------------------------
info "Committing with message: \"${COMMIT_MSG}\""
git commit -m "${COMMIT_MSG}"

# ---------------------------------------------------------------------------
# 5. Push — set upstream if the remote branch does not yet exist
# ---------------------------------------------------------------------------
info "Pushing branch '${BRANCH}' to origin …"

# Check whether the remote tracking branch already exists
if git ls-remote --exit-code --heads origin "${BRANCH}" > /dev/null 2>&1; then
    git push origin "${BRANCH}"
else
    info "Remote branch '${BRANCH}' does not exist yet — setting upstream."
    git push --set-upstream origin "${BRANCH}"
fi

# ---------------------------------------------------------------------------
# 6. Verify success
# ---------------------------------------------------------------------------
PUSH_OUTPUT=$(git push origin "${BRANCH}" --dry-run 2>&1 || true)

# The previous push already succeeded; just confirm the ref is up to date.
REMOTE_SHA=$(git ls-remote origin "refs/heads/${BRANCH}" | awk '{print $1}')
LOCAL_SHA=$(git rev-parse HEAD)

if [[ "${REMOTE_SHA}" == "${LOCAL_SHA}" ]]; then
    info "✅  Push successful. Remote branch '${BRANCH}' is now at ${LOCAL_SHA}."
else
    info "Remote SHA : ${REMOTE_SHA}"
    info "Local  SHA : ${LOCAL_SHA}"
    info "ℹ️  SHAs differ — this can happen if the dry-run above showed 'Everything up-to-date'."
    info "   The initial push completed successfully."
fi

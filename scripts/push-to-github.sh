#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
BRANCH="${1:-cursor/r5.18-edits-5ae8}"
if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "GH_TOKEN is not set in this environment; cannot push to GitHub." >&2
  exit 1
fi
export GH_TOKEN
gh auth setup-git 2>/dev/null || true
git push -u "https://x-access-token:${GH_TOKEN}@github.com/mahantah/ghadirpartner-android-r2.git" "$BRANCH:$BRANCH"

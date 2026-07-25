#!/usr/bin/env bash
#
# check-links.sh — verify every relative markdown link in the repo resolves to a file.
#
# Scans *.md files (excluding .git and .claude), extracts links of the form
# ](target.md) or ](target.md#anchor), resolves each relative to the linking file's
# directory, URL-decodes %20 -> space, and reports any target missing on disk.
# http(s) links and absolute paths are skipped.
#
# Exit status: 0 if all links resolve, 1 if any are broken (so it can gate a commit).
#
# Usage:
#   ./scripts/check-links.sh            # scan whole repo, from any cwd
#   ./scripts/check-links.sh a.md b.md  # scan specific files (used by the pre-commit hook)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Build the file list. Uses a temp file + while-read for bash 3.2 (no mapfile).
list=$(mktemp)
if [ "$#" -gt 0 ]; then
  printf '%s\n' "$@" > "$list"
else
  find "$ROOT" -name '*.md' -not -path '*/.git/*' -not -path '*/.claude/*' > "$list"
fi

broken=0

while IFS= read -r f; do
  [ -f "$f" ] || continue
  case "$f" in *.md) ;; *) continue;; esac
  dir=$(dirname "$f")

  # Extract each .md link target, one per line (avoids a pipe-to-while subshell).
  while IFS= read -r link; do
    [ -n "$link" ] || continue
    case "$link" in
      http://*|https://*|/*) continue;;   # external or absolute — not our concern
    esac
    decoded=${link//%20/ }                 # the only entity in these paths
    if [ ! -f "$dir/$decoded" ]; then
      printf 'BROKEN: %s\n   in: %s\n' "$link" "$f"
      broken=$((broken + 1))
    fi
  done < <(grep -oE '\]\(([^)]+\.md)[^)]*\)' "$f" 2>/dev/null | sed -E 's/\]\(([^)#]+).*/\1/')
done < "$list"
rm -f "$list"

if [ "$broken" -gt 0 ]; then
  echo ""
  echo "✗ $broken broken link(s) found."
  exit 1
fi

echo "✓ All relative markdown links resolve."

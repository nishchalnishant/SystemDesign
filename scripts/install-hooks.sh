#!/usr/bin/env bash
#
# install-hooks.sh — wire the repo's git hooks into this clone.
#
# Git does not version .git/hooks, so each clone must install them once.
# Run from anywhere: ./scripts/install-hooks.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ln -sf ../../scripts/pre-commit "$ROOT/.git/hooks/pre-commit"
chmod +x "$ROOT/scripts/pre-commit" "$ROOT/scripts/check-links.sh"

echo "✓ pre-commit hook installed (runs scripts/check-links.sh on staged .md files)."

#!/usr/bin/env sh
# bsm-svc pre-commit checks — called by root .husky/pre-commit when bsm-svc files are staged.

set -e

ROOT="$(git rev-parse --show-toplevel)"
SVC_DIR="$ROOT/apps/bsm-svc"
STAGED="$(git diff --cached --name-only)"

# ── compile (only when .java files are staged) ────────────────────────────────
if echo "$STAGED" | grep -q '\.java$'; then
  echo "[bsm-svc] compiling..."
  (cd "$ROOT" && ./gradlew :apps:bsm-svc:compileJava -x test --quiet)
  echo "[bsm-svc] compile ✓"
fi

echo "[bsm-svc] ✓ all checks passed"

#!/usr/bin/env sh
# auth-svc pre-commit checks — called by root .husky/pre-commit when auth-svc files are staged.

set -e

ROOT="$(git rev-parse --show-toplevel)"
SVC_DIR="$ROOT/apps/auth-svc"
STAGED="$(git diff --cached --name-only)"

# ── compile (only when .java files are staged) ────────────────────────────────
if echo "$STAGED" | grep -q '\.java$'; then
  echo "[auth-svc] compiling..."
  (cd "$ROOT" && ./gradlew :apps:auth-svc:compileJava -x test --quiet)
  echo "[auth-svc] compile ✓"
fi

echo "[auth-svc] ✓ all checks passed"

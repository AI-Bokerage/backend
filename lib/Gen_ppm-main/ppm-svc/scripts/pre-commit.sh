#!/usr/bin/env sh
# ppm-svc pre-commit checks — called by root .husky/pre-commit when ppm-svc files are staged.

set -e

ROOT="$(git rev-parse --show-toplevel)"
SVC_DIR="$ROOT/apps/ppm-svc"
STAGED="$(git diff --cached --name-only)"

# ── compile (only when .java files are staged) ────────────────────────────────
if echo "$STAGED" | grep -q '\.java$'; then
  echo "[ppm-svc] compiling..."
  (cd "$ROOT" && ./gradlew :apps:ppm-svc:compileJava -x test --quiet)
  echo "[ppm-svc] compile ✓"
fi

echo "[ppm-svc] ✓ all checks passed"

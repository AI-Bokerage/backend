#!/usr/bin/env bash
# Builds the in-repo library the backend loads at runtime (@gen-ms/gen-sup-starter, Super Admin
# features). Its dist/ is git-ignored, so a fresh checkout (CI, image builds) must build it
# before `pnpm install` / `docker build`.
set -euo pipefail
cd "$(dirname "$0")/../lib/Gen_Sup-main"
npm install --no-audit --no-fund --workspace=@gen-ms/gen-sup-starter
npm run build --workspace=@gen-ms/gen-sup-starter
test -f packages/gen-sup-starter/dist/index.js
echo "built gen-sup-starter"

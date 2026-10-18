# modules/platform loads @gen-ms/gen-sup-starter (lib/, a `file:` dependency) at runtime. Its dist/
# is git-ignored, so build it here: a checkout (CI, Railway) has only its source.
FROM node:20-alpine AS libs
WORKDIR /lib
COPY lib/Gen_Sup-main/package.json ./
COPY lib/Gen_Sup-main/packages/gen-sup-starter packages/gen-sup-starter
RUN npm install --no-audit --no-fund --workspace=@gen-ms/gen-sup-starter \
 && npm run build --workspace=@gen-ms/gen-sup-starter

FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
# pnpm needs the `file:` dependency's directory (with its fresh dist/) at install time.
COPY --from=libs /lib/packages/gen-sup-starter lib/Gen_Sup-main/packages/gen-sup-starter
COPY lib/Gen_Usg-main/packages/gen-usg-starter lib/Gen_Usg-main/packages/gen-usg-starter
# @prisma/client's postinstall runs `prisma generate` against this schema —
# without it here, `pnpm install` silently generates an empty client (no
# models), which only surfaces later as confusing type errors in `pnpm build`.
COPY prisma ./prisma
RUN corepack enable && corepack prepare pnpm@10.0.0 --activate
# --frozen-lockfile (not --no-frozen-lockfile): resolves every version from
# the committed pnpm-lock.yaml instead of re-resolving live against the
# registry on every build — confirmed necessary by an actual build break
# when a floating @typescript-eslint range briefly caught an upstream
# release still propagating its own sub-dependencies.
RUN pnpm install --frozen-lockfile

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN corepack enable && corepack prepare pnpm@10.0.0 --activate
RUN pnpm build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY package.json ./
COPY --from=deps /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist
# Migrations: the deploy runs `prisma migrate deploy` from this image before starting it.
COPY prisma ./prisma
# swagger-jsdoc reads these from disk at request time (config/swagger.ts's `apis`
# glob), not from the compiled dist/ bundle — without them, /docs renders an empty
# spec (confirmed: the UI shell loads fine either way, silently hiding the gap).
# modules/platform and modules/crm are source .ts (not dist/'s compiled .js) so
# any future inline @openapi JSDoc comment there still gets picked up in
# production, matching dev's behavior. Not all of modules/ — modules/auth and
# modules/tenant are separate Java/Gradle projects (their own Dockerfiles),
# irrelevant to this image.
COPY docs ./docs
COPY modules/platform ./modules/platform
COPY modules/crm ./modules/crm
COPY .env.example ./.env
EXPOSE 4000
CMD ["node", "dist/server.js"]

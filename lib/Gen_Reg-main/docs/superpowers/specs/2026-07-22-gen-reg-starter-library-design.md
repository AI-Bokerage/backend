# Gen_REG Phase 2, Sub-Project 1 — Starter/Demo Library Split

## Goal

Restructure Gen_REG from a single deployable Node app into a configurable library, mirroring the org's established Gen_Auth/Gen_TNT pattern: a `-starter` package (the reusable library, embeddable by any Node/Express consumer) plus a `-demo` package (a thin reference app that runs the starter standalone over HTTP). This is a foundation project — later Phase 2 features (payment, captcha, abandoned-signup recovery, wizard read-model, idempotency middleware, RabbitMQ event bus) land as configurable modules on top of this split, not as ad-hoc additions to a monolith.

## Why

Gen_Auth and Gen_TNT are both two-module Gradle builds: a Spring Boot autoconfigure `-starter` (published to GitHub Packages, no main class, `@ConfigurationProperties`-driven feature toggles, `@ConditionalOnProperty` module inclusion) and a `-demo` app that proves the starter works standalone and gives non-JVM consumers an HTTP surface. Gen_REG's Phase 1 was built as a single Node/Express/Prisma app with no equivalent split. This project brings Gen_REG to parity in spirit — embeddable, configurable, published as an artifact — using Node's own idiom (a config-object factory function) rather than porting Spring's DI/autoconfigure machinery, which has no direct Node equivalent and isn't justified here.

## Scope

**In scope:**
- Split the existing Gen_REG repo into an npm workspace with two packages: `gen-reg-starter` (library) and `gen-reg-demo` (thin app).
- A `createGenReg(config)` factory as the starter's single entry point, with every major dependency (repo, email sender, TNT client, Auth client) and module (signup routes, verify-email routes, worker) individually swappable/toggleable, defaulting to Phase 1's existing built-in implementations.
- Extract `TntClient`/`AuthClient` behind new `ITntClient`/`IAuthClient` interfaces (today they're concrete classes) so they're swappable like `ISignupSessionRepo`/`EmailSender` already are.
- Move env-var validation from eager (import-time, all vars required) to lazy (validated only when the specific default adapter needing it is actually constructed).
- Publish `gen-reg-starter` to GitHub Packages' npm registry, matching the Java starters' Maven-to-GitHub-Packages publishing story.
- Migrate all existing Phase 1 tests to their new package location, unmodified in logic, plus new tests for the factory itself (default-config boot, override-path proof, module-toggle behavior, lazy-env behavior).

**Explicitly out of scope (future Phase 2 sub-projects, land as modules once this split exists):**
- Payment/plan selection (Stripe/Razorpay, plan data source TBD — hardcode vs. minimal Gen_PPM).
- Turnstile captcha.
- Abandoned-signup recovery emails.
- Wizard read-model.
- Idempotency-key middleware.
- RabbitMQ event bus.

Whether/where RabbitMQ and Valkey get introduced for those later sub-projects is an open, separate decision (discussed but not finalized) — this spec does not add either.

## Architecture

```
Gen_REG/
  packages/
    gen-reg-starter/     — the library
    gen-reg-demo/         — thin reference app
```

`gen-reg-demo` depends on `gen-reg-starter` via npm workspace protocol locally (`"gen-reg-starter": "workspace:*"`); an external consumer would instead depend on the version published to GitHub Packages.

### The factory

```typescript
export function createGenReg(config: GenRegConfig): { app: Express; worker?: GenRegWorker } {
  // ...
}

interface GenRegConfig {
  repo?: ISignupSessionRepo;       // default: PrismaSignupSessionRepo
  emailSender?: EmailSender;       // default: ConsoleEmailSender
  tntClient?: ITntClient;          // default: HttpTntClient
  authClient?: IAuthClient;        // default: HttpAuthClient
  modules?: {
    signup?: boolean;              // default: true
    verifyEmail?: boolean;         // default: true
    worker?: boolean;              // default: true
  };
}

interface GenRegWorker {
  start(): void;
  stop(): void;
}
```

Every field left unset falls back to Phase 1's existing built-in, driven by the same env vars already established in `.env.example`. `createGenReg({})` with the full env set reproduces exactly what Gen_REG does today — this is the correctness bar for the migration.

## Components

**Moves into `gen-reg-starter`, logic unchanged:**
`domain/` (enums, ports), `common/` (errors, token, slug, disposable-domains, email-sender interface), `infra/persistence` (Prisma client + repo), `modules/signup/v1`, `modules/verify-email/v1`, `middleware/error-handler.ts`, `prisma/schema.prisma` + migrations, `worker.ts`'s `sweepProvisioning`/`sweepAbandoned` functions, `config/env.ts` (reworked, see below).

**New: interface extraction for the two HTTP clients**

```typescript
// domain/ports/tnt-client.port.ts
export interface ITntClient {
  isSlugTaken(slug: string): Promise<boolean>;
  createTenant(input: CreateTenantInput): Promise<TenantResponse>;
  getTenant(id: string): Promise<TenantResponse>;
  getJob(jobId: string): Promise<ProvisioningJobResponse>;
}
// HttpTntClient implements ITntClient — today's TntClient, renamed, now behind the interface.

// domain/ports/auth-client.port.ts
export interface IAuthClient {
  register(email: string, password: string): Promise<RegisterResult>;
}
// HttpAuthClient implements IAuthClient — today's AuthClient, renamed, behind the interface.
```

`SignupService`/`VerifyEmailService` constructor parameter types widen from the concrete classes to these interfaces — no behavior change; `HttpTntClient`/`HttpAuthClient` satisfy them structurally, so existing tests pass unmodified.

**New: `gen-reg-starter/src/create-gen-reg.ts`** — the factory implementation. Resolves each config field to a default if unset, constructs `SignupService`/`VerifyEmailService`, builds the Express `app` (conditionally mounting signup/verify-email routes and the error handler per `modules` flags), and conditionally returns a `worker` object wrapping the two sweep `setInterval`s if `modules.worker` is true.

**`gen-reg-demo` becomes:** a single `src/index.ts` — reads env, calls `createGenReg({})`, calls `app.listen(...)` and `worker?.start()`. Today's `server.ts`/`worker.ts` entry-point boilerplate (including the Windows `pathToFileURL` entry-point-detection fix from Phase 1) collapses into this one file.

## Config resolution & env handling

Today, `config/env.ts` validates every var eagerly at import time (`EnvSchema.parse(process.env)`), all required, no defaults — `GEN_TNT_BASE_URL`, `GEN_TNT_INTERNAL_SECRET`, `GEN_AUTH_BASE_URL` included. This is incompatible with "everything pluggable": a consumer supplying their own `ITntClient` shouldn't be forced to set `GEN_TNT_BASE_URL` to satisfy a check for a code path they never use.

**Fix:** the schema becomes fully optional. Validation moves from eager/import-time to lazy/construction-time — each default-resolver function validates only the specific env vars it actually needs, and only runs at all if its config field wasn't overridden and its owning module is enabled:

```typescript
function resolveTntClient(override: ITntClient | undefined): ITntClient {
  if (override) return override;
  const baseUrl = requireEnv("GEN_TNT_BASE_URL");
  const secret = requireEnv("GEN_TNT_INTERNAL_SECRET");
  return new HttpTntClient(baseUrl, secret);
}
```

Same pattern for `authClient` (→ `GEN_AUTH_BASE_URL`) and `repo` (→ `DATABASE_URL`). `emailSender`'s default (`ConsoleEmailSender`) needs no env. If `modules.signup` is `false`, `resolveTntClient`/`resolveAuthClient` are never called, so their env vars are never required.

`requireEnv()` throws a `GenRegConfigError` (new error class, boot-time, distinct from request-time `AppError`s) with a clear message naming the missing var and which default adapter needed it.

## Error handling

`errorHandler` middleware moves into `gen-reg-starter`, exported directly for consumers building their own Express app around individual pieces. `createGenReg()`'s built-in `app` wires it automatically. `AppError`/`ZodError` → 4xx mapping is unchanged from Phase 1.

`GenRegConfigError` is new: thrown synchronously by `createGenReg()` at boot time when a module is enabled, its adapter wasn't overridden, and a required env var for the default is missing. Fail-fast at startup, not a confusing failure on the first request.

## Testing

Everything existing (service unit tests, repo Testcontainers tests, worker tests) moves to its new package location with no logic changes — `ISignupSessionRepo`/`ITntClient`/`IAuthClient` are structurally identical to what the current test fakes already satisfy.

New tests for the factory:
1. **Default-config test** — `createGenReg({})` with full env set produces a working app (reuses Phase 1's `server.test.ts` assertions).
2. **Override-path test** — construct `createGenReg()` with fake `repo`/`emailSender`/`tntClient`/`authClient`; hit a route; assert the fakes were actually invoked. This is the test that proves pluggability works at runtime, not just at the type level — mirrors how Gen_TNT/Gen_Auth's demo app proves their autoconfiguration genuinely wires up.
3. **Module-toggle tests** — `modules.worker: false` → no `worker` object returned; `modules.signup: false` → signup routes genuinely return 404 (not mounted, not just untested); same for `modules.verifyEmail`.
4. **Lazy-env tests** — module disabled + its default's env var unset → no throw. Module enabled + its default's env var unset (and no override supplied) → throws `GenRegConfigError`.

`gen-reg-demo` gets one thin boot-smoke test: import `gen-reg-starter`, call `createGenReg({})`, confirm no throw. The existing `scripts/smoke-signup.sh` continues to cover the real signup → verify → provision path against the running demo app.

## Migration correctness bar

The migration is complete when: (1) `gen-reg-demo` calling `createGenReg({})` with today's `.env` reproduces Gen_REG's exact current behavior (all existing integration/smoke tests pass unmodified in outcome), and (2) the override-path test proves every one of `repo`/`emailSender`/`tntClient`/`authClient` can be swapped and is actually used instead of the default.

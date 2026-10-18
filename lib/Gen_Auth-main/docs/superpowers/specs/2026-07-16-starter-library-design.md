# Design: Gen_AUTH as a Spring Boot Starter Library

Converts Gen_AUTH from a standalone-only deployable service into a reusable Spring Boot starter library, so other internal Java projects (client engagements where the whole codebase, including auth, gets IP-transferred to the client) can embed a fully working, self-contained auth module into their own application — one deployment per client, no shared central service, no dependency back on us after IP transfer.

## Why not a shared central service or a client SDK

Both were considered and rejected for this specific use case: client projects need their own isolated user data and a clean IP handoff. A central service run by us means the client's live auth still depends on our infrastructure after "transfer" — not a clean transfer. A client SDK implies calling a remotely-hosted service, which doesn't exist in this model. A library embedded into each client's own app, each with its own DB/keys/deployment, is the only option that's both reusable (one maintained codebase) and cleanly transferable (compiles into the client's own build, nothing calls home).

## Scope (v1 — core auth only)

| In scope | Out of scope (deferred, restorable from git history later) |
|---|---|
| Register, login, refresh (rotation + replay detection), logout, logout-all, change-password, session | Magic-link password reset |
| JWKS serving + runtime multi-key rotation (`/internal/auth/keys/**`) | Super-admin login / impersonation |
| Internal user provisioning (`/internal/auth/users`) | RabbitMQ event publishing |
| Argon2id hashing, lockout, JWT sign/verify (local PEM + AWS KMS) | Email sending (SMTP/SES) |
| Core entities: users, sessions, refresh tokens, user-roles, audit logs, login attempts, JWT signing keys | `platform_super_admin` table, `DevDataSeeder`, `BootstrapSuperAdminInitializer` |

## Module layout

Split the single Gradle module into two:

- **`:gen-auth-starter`** — the reusable library. `java-library` plugin (no `org.springframework.boot` plugin, no bootJar, no main class). Contains everything in the "in scope" column above.
- **`:gen-auth-demo`** — a thin Spring Boot application (`org.springframework.boot` plugin, its own `application.yaml` with real DB/Redis/JWT config) that depends on `:gen-auth-starter`. Exists to (a) prove the starter actually auto-configures correctly end-to-end, using the same real-Postgres/Redis manual-verification convention already established for the Flyway and JWKS-rotation work, and (b) serve as living documentation of what a consuming app's build file and config need to look like. This also keeps Gen_AUTH runnable standalone, same as today.

`AuthSvcApplication` (the current `@SpringBootApplication` main class) moves into `:gen-auth-demo`; the starter module has no application entry point of its own.

## Auto-configuration design

Every existing `@Service`, `@RestController`, `@Repository`-via-Spring-Data annotation stays **unchanged** — no per-class rewrite. A consuming app's own component scan won't reach into the starter's package tree by default, so a small set of new `@AutoConfiguration` glue classes (registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, the same modularized mechanism behind this session's Flyway fix) explicitly point Spring at the starter's packages:

```java
@AutoConfiguration
@ComponentScan("com.example.authsvc")
@EntityScan("com.example.authsvc.infrastructure.persistence.entity")
@EnableJpaRepositories("com.example.authsvc.infrastructure.persistence.repository")
public class GenAuthAutoConfiguration {}
```

This is the single highest-leverage decision in this design: it turns "convert to a library" from a rewrite of ~130 in-scope classes into a build/packaging change plus a handful of new glue classes.

## Migration strategy

The starter runs its **own independent Flyway instance** at startup — a programmatic `Flyway.configure()...load().migrate()` call inside an auto-configured `ApplicationRunner`, using the DataSource the host app already configured, migration files bundled on the starter's own classpath, and the existing dedicated history table `flyway_schema_history_auth` (already distinct from Flyway's default table name today, specifically so it doesn't collide with a host app's own migration history). Completely isolated from however the host app manages its own schema (Flyway, Liquibase, or nothing) — no manual wiring required by the consuming project.

## Configuration

Every existing `@ConfigurationProperties` class carries over unchanged: `jwt.*`, `auth.*`, `cookie.*` (bound at `app.cookie.*` — currently orphaned, see Known Gaps below; fixed as part of this work), `app.cors.*`, `aws.*`. A consuming app configures the starter the same way `application.yaml` configures Gen_AUTH today, with the same required env vars — `AUTH_DB_URL`/`AUTH_DB_USERNAME`/`AUTH_DB_PASSWORD`, `INTERNAL_SERVICE_SECRET`, `JWT_KEY_ENCRYPTION_SECRET` — and the same fail-fast behavior on missing/invalid values. `SUPER_ADMIN_EMAIL`/`SUPER_ADMIN_PASSWORD` are no longer required, since super-admin bootstrap is deferred out of v1.

Endpoint base paths (`/api/v1/auth/**`, `/internal/auth/**`, `/.well-known/jwks.json`) stay fixed for v1 — no configurable prefix yet (YAGNI; add only if a real path collision with a client's existing routes comes up).

## Distribution & testing

- Published to **GitHub Packages** as a private Maven artifact (`com.example:gen-auth-starter`), semantically versioned. Free to make breaking changes across major versions — every consumer is internal and known.
- The existing unit test suite moves into `:gen-auth-starter`'s own test source set unchanged; tests for deferred features (magic-link, super-admin, impersonation) are removed along with that code.
- `:gen-auth-demo` is the integration/manual-verification harness — same real-Postgres/Redis manual pass convention used for the Flyway fix and JWKS rotation slices, run against the starter as an actual dependency (not the in-tree source), to catch any auto-configuration issue a unit test can't see (analogous to how the `@Lob`/`BYTEA` schema bug was only caught by a real-Postgres run, not mocks).

## Known gaps this touches

- `CookieProperties`/`CorsProperties` currently bind to `app.cookie.*`/`app.cors.*`, which don't exist anywhere in `application.yaml` (both silently run on in-code defaults today — a pre-existing bug documented in the codebase reference artifact). Since a consuming client app will need to actually configure these, this conversion is the natural point to fix the orphaned property prefixes so they bind to real YAML keys.
- The dead `LockoutStore` port and unused `domain/model/AuthSession` class are dropped during the move (not carried into the starter) rather than fixed — out of scope for this conversion, no reason to carry known-dead code into a fresh library module.

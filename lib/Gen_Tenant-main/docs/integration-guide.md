# Gen_TNT Integration Guide

Two ways to use Gen_TNT. Pick one:

- **Embed** `gen-tnt-starter` directly in your Spring Boot app (same JVM, same datasource, no network hop). Recommended if your app is Java/Spring.
- **Call it over HTTP** as a standalone service (`gen-tnt-demo`, or your own thin wrapper). Recommended for non-Java consumers.

Either way, you also need to implement the **step targets** your provisioning pipeline calls out to — Gen_TNT drives the saga, it doesn't do the actual provisioning work itself.

---

## 1. Embedding `gen-tnt-starter` in your app

### 1.1 Add the dependency

Not yet published to a real package registry (`gen-tnt-starter/build.gradle`'s `publishing` block still has a placeholder `YOUR_GITHUB_ORG` URL). Until that's wired up, publish to your local Maven cache from this repo:

```bash
cd Gen_TNT
./gradlew.bat :gen-tnt-starter:publishToMavenLocal
```

Then in your app's `build.gradle`:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'com.example:gen-tnt-starter:<version>'   // matches project.version in Gen_TNT
}
```

Once GitHub Packages is configured for real, swap `mavenLocal()` for the `GitHubPackages` repository declared in `gen-tnt-starter/build.gradle`.

### 1.2 Nothing to `@Import` — autoconfiguration is automatic

`GenTntAutoConfiguration` is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, so it activates the moment the jar is on your classpath — component scan, JPA repositories, entity scan, and config-properties binding all wire themselves in. You don't add any `@Import` or `@ComponentScan` yourself.

### 1.3 Required configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/yourdb
    username: ...
    password: ...
  data:
    redis:
      host: localhost
      port: 6379

gentnt:
  internal-secret: ${INTERNAL_SERVICE_SECRET}   # gates every /api/v1/** and /internal/** call
  provisioning:
    max-retries: 3
    timeout-minutes: 10
    retry-scheduler-interval-ms: 120000
    timeout-scheduler-interval-ms: 60000
    steps:
      - name: SCHEMA_BOOTSTRAP
        url: http://your-service/provisioning/schema
        mode: SYNC
        retryable: true
      - name: AUTH_BOOTSTRAP
        url: http://your-service/provisioning/auth
        mode: ASYNC
        retryable: true
        compensate-url: http://your-service/provisioning/auth/compensate
```

Notes:
- **Postgres and Redis are hard requirements**, not optional — the saga's locking (Redis) and job/step state (Postgres) both depend on them. No in-memory fallback exists.
- **`gentnt.provisioning.steps` has no default** — an empty list means `createTenant` marks the job complete with zero steps run. Define your real pipeline here.
- **Flyway migration**: the starter's migration lives on the classpath at `db/migration/gentnt/V1__tenant_and_provisioning.sql`. Spring Boot's default `spring.flyway.locations` (`classpath:db/migration`) scans subdirectories, so it's picked up automatically — **unless your app already overrides `spring.flyway.locations`**, in which case add `classpath:db/migration/gentnt` to that list yourself, or your own migrations and Gen_TNT's will collide/be skipped.
- If your app's own `ddl-auto` is `validate` (recommended) or `none`, Flyway is the only thing creating the `tenant`, `provisioning_job`, and `provisioning_step` tables — don't hand-write those tables yourself.

### 1.4 Endpoints you get for free

Once configured, these routes exist in your app (see [§3](#3-http-api-reference) for full request/response shapes):

```
POST   /api/v1/tenants
GET    /api/v1/tenants/{id}
PATCH  /api/v1/tenants/{id}/suspend
PATCH  /api/v1/tenants/{id}/reactivate
PATCH  /api/v1/tenants/{id}/cancel
PATCH  /api/v1/tenants/{id}/purge
GET    /api/v1/provisioning/jobs/{id}
GET    /api/v1/provisioning/jobs/{id}/steps
POST   /api/v1/provisioning/jobs/{id}/retry
GET    /api/v1/provisioning/failed
POST   /internal/provisioning/jobs/{jobId}/steps/{stepName}/callback
```

Every one of them requires the `X-Internal-Secret` header (matching `gentnt.internal-secret`) — including calls from your own app's code. There is no cookie/JWT auth layer; if you need to expose tenant management to end users, put your own authenticated controller in front and call Gen_TNT's `TenantService`/`ProvisioningService` beans directly instead of going back out over HTTP.

### 1.5 Swagger / OpenAPI

Once `springdoc-openapi-starter-webmvc-ui` is on the classpath (it ships as a transitive dependency of `gen-tnt-starter` — nothing to add), your app automatically exposes:

- `/v3/api-docs` — raw OpenAPI 3 JSON
- `/swagger-ui.html` — interactive UI

Click "Authorize" in the UI and paste your `gentnt.internal-secret` value to call gated endpoints from the browser.

---

## 2. Calling it over HTTP as a standalone service

Use `gen-tnt-demo` as-is (see the root `README.md` for `bootRun` instructions), or deploy your own thin wrapper module the same way — same pattern as `gen-auth-demo`.

```bash
docker compose up -d   # Postgres :5435, Redis :6381
INTERNAL_SERVICE_SECRET=dev-secret \
TNT_DB_URL=jdbc:postgresql://localhost:5435/gentnt \
TNT_DB_USERNAME=postgres TNT_DB_PASSWORD=postgres \
TNT_REDIS_HOST=localhost TNT_REDIS_PORT=6381 \
./gradlew.bat :gen-tnt-demo:bootRun
```

Then call it like any REST API, from any language, with `X-Internal-Secret: dev-secret` on every request. Swagger UI at `http://localhost:8201/swagger-ui.html`.

---

## 3. HTTP API reference

### Create a tenant

```
POST /api/v1/tenants
X-Internal-Secret: <secret>
Content-Type: application/json

{
  "name": "Acme Corp",
  "slug": "acme",
  "region": "us-east-1",
  "primaryOwnerUserId": "11111111-1111-1111-1111-111111111111",
  "idempotencyKey": "optional-client-generated-key"
}
```

→ `202 Accepted`, body is the tenant (status `PROVISIONING`, or already `ACTIVE` if every configured step happened to be `SYNC` and all succeeded before the response was built). `409` if `slug` is already taken.

`idempotencyKey` is optional but recommended for any caller that might retry the create request — pair it with your own dedup logic if you need exactly-once creates (Gen_TNT stores the key but the uniqueness enforcement is on `slug`, not on the key itself).

### Read / transition a tenant

```
GET   /api/v1/tenants/{id}
PATCH /api/v1/tenants/{id}/suspend
PATCH /api/v1/tenants/{id}/reactivate
PATCH /api/v1/tenants/{id}/cancel
PATCH /api/v1/tenants/{id}/purge
```

All return the updated `TenantResponse`. `409` if the transition isn't legal from the tenant's current status (e.g. `purge` on anything but `CANCELLED`).

### Inspect provisioning

```
GET /api/v1/provisioning/jobs/{id}          → job status, retryCount, lastError, timestamps
GET /api/v1/provisioning/jobs/{id}/steps    → each step's status in pipeline order
GET /api/v1/provisioning/failed             → all FAILED jobs (operator dashboard feed)
POST /api/v1/provisioning/jobs/{id}/retry   → 202, only legal if job is FAILED (409 otherwise)
```

---

## 4. Implementing your own step targets

Each entry in `gentnt.provisioning.steps` needs a real HTTP endpoint on your side (or on whatever service owns that piece of provisioning). Gen_TNT calls it, in order, one step at a time.

### SYNC steps

Gen_TNT `POST`s to your `url` and blocks until you respond:

```json
// Request body Gen_TNT sends you
{ "tenantId": "...", "jobId": "...", "stepName": "SCHEMA_BOOTSTRAP", ...anyPriorStepContext }
```

- Any **2xx** response = success. The saga advances to the next step immediately.
- Response body (JSON object, if any) is merged into the job's context and forwarded as extra fields in every subsequent step's payload.
- Any non-2xx status, timeout (10s connect / 10s read), or connection error = failure. The job goes to `FAILED` and waits for retry (automatic via the retry scheduler, or manual via the retry endpoint).

### ASYNC steps

Gen_TNT `POST`s the same payload, plus a `callbackToken`:

```json
{ "tenantId": "...", "jobId": "...", "stepName": "AUTH_BOOTSTRAP", "callbackToken": "...", ...context }
```

- A **2xx** response only means "accepted" — it does **not** mark the step complete. Return immediately, do the real work in the background.
- When done (success or failure), call back:

```
POST /internal/provisioning/jobs/{jobId}/steps/{stepName}/callback
X-Internal-Secret: <secret>
Content-Type: application/json

{ "token": "<the callbackToken you were given>", "success": true, "context": { "adminUserId": "..." }, "error": null }
```

- `token` must match exactly — mismatches are silently rejected (logged as a warning), not surfaced as an error to you, since a wrong token usually means a stale/duplicate delivery.
- If the callback loses a race for the tenant's provisioning lock, you'll get `409` — retry the callback (at-least-once delivery is assumed).
- A late callback for a job that's no longer `IN_PROGRESS` (already `DEAD`/`COMPLETED`) is a no-op — safe to fire-and-forget retries on your side without double-processing risk.

### Compensation (best-effort, timeout-triggered only)

If a job never completes within `gentnt.provisioning.timeout-minutes`, the timeout scheduler marks it `DEAD` and calls each **already-completed** step's `compensate-url` (if configured), in reverse order, with the same payload shape. Compensation is fire-and-forget from Gen_TNT's side — no response is checked, failures are logged and swallowed. Compensation is **not** triggered by an explicit step failure (those go through retry instead) — only by the job timing out entirely.

### Reference implementation

`gen-tnt-demo/scripts/mock_step_server.py` is a working (if minimal) example of both a SYNC and an ASYNC step target, including the callback — read it before writing your first real one.

---

## 5. Local end-to-end smoke test

```bash
./gen-tnt-demo/scripts/smoke-provisioning.sh
```

Creates a tenant, polls until `ACTIVE`, exits 0/1. Good template for a CI health check against a real deployment.

# PPM-SVC Integration Guide

**Audience:** engineers integrating another service (checkout, billing, provisioning, signup) with the PPM (Plan & Pricing Management) service.

**Scope of this document:** `ppm-svc` — the deployable Spring Boot application. It exposes the Plan/Module/Add-On/Entitlement catalog, the Pricing Resolver, and the Promotion Engine (Coupons, Referrals, Campaigns) over REST. This document does not cover `ppm-core` (the internal business-logic library `ppm-svc` is built on) as a standalone dependency — `ppm-core` is not published outside this repository, so the only integration surface available to another service is the HTTP API documented here.

> **Note**
>
> This guide reflects the code as it exists in this repository today. Where a section cannot be derived from the implementation, that is stated explicitly rather than filled with a plausible-sounding guess.

---

## 1. Introduction

**What PPM-SVC is.** A platform-wide catalog and pricing service. It owns the definition of subscription plans, the platform capability modules and add-ons attached to them, entitlement limits, region/currency/cycle-specific pricing, and the full promotion/discount engine (coupons, referrals, campaigns, and the pricing pipeline that resolves a final price for a plan given an optional discount code).

**What PPM-SVC owns:**

| Domain | Description |
|---|---|
| Plan catalog | Plans, plan versions, visibility, trial length |
| Module & Add-On catalog | Platform capabilities and purchasable add-ons |
| Entitlement catalog | Quota/flag/text entitlement definitions and plan assignments |
| Pricing | Region/currency/cycle-specific `PlanPrice`/`AddOnPrice` rows and the Pricing Resolver that picks the applicable one |
| Promotions | `Promotion` (percentage / flat / fixed-price discount, or free-period / free-module / free-add-on entitlement grant), conditions (plan restriction, eligibility, per-user usage cap) |
| Coupons | Redemption codes pointing at a `Promotion` |
| Referrals | Referral programs, referrer codes, conversion tracking, referrer reward issuance |
| Campaigns | Organizational grouping of promotions for marketing/reporting |
| Legacy promo codes | The original `PromoCode` catalog + validation engine (`/api/v1/ppm/promo-codes`) — coexists with the newer Promotion/Coupon model; **not** deprecated in code, but new integrations should prefer Promotions/Coupons |

**What PPM-SVC deliberately does NOT own:**

- **Billing / invoicing** — PPM tells you the price and any discount; it never charges a card or creates an invoice.
- **Checkout orchestration** — PPM has no concept of a cart, an order, or a subscription lifecycle state machine.
- **Customer / tenant identity** — PPM does not store customer records. Where a customer identity is needed (condition evaluation, referral attribution), the caller supplies it as an opaque string (`customerId`) — PPM does not validate that it corresponds to a real account.
- **Entitlement fulfilment / provisioning** — when a promotion grants a free module or add-on, PPM computes and returns *what* was granted (`grantedEntitlement`). PPM does not call any provisioning API to actually turn the feature on. The caller (checkout/provisioning service) is responsible for acting on that grant.
- **Usage-limit enforcement at runtime** — PPM resolves *entitlement definitions*; enforcing them during day-to-day product usage is `usg-svc`'s job (per `ppm-svc`'s own README).
- **Redemption bookkeeping side effects** — PPM's pricing pipeline (`POST /quotes`) is read-only. Recording that a coupon/referral was actually redeemed (so per-user caps take effect) is a separate, explicit call the caller must make (see §13).

---

## 2. Integration Models

Only one integration model exists in this codebase.

### Standalone (REST over HTTP)

`ppm-svc` is a standalone Spring Boot service. Other services (BSM, REG-SVC, USG-SVC, and by convention any checkout/billing service) call it over HTTP, authenticated with a JWT issued by AUTH-SVC.

- **Transport:** HTTP/JSON, base path `/api/v1/ppm`.
- **Authentication:** OAuth2 JWT bearer token (see §12).
- **Deployment:** independent process, own PostgreSQL schema, own Redis usage (permission cache + JTI revocation checks).

**Advantages:** language-agnostic caller, independent deployability/scaling, single source of truth for pricing/promotion logic shared by every consumer.

**Limitations:** network hop and its latency/availability characteristics; no shared-transaction integration with the caller (see §16 — there is no distributed transaction between "resolve a quote" and "charge the customer").

**Recommended use cases:** any service that needs the current price for a plan, needs to validate/apply a coupon or referral code, or needs to manage the plan/promotion/campaign catalog.

> **Note**
>
> An **embedded/library** integration model (depending on `ppm-core` directly inside another JVM process) is *architecturally possible* — `ppm-core` is a framework-agnostic Java library with no Spring Boot/MVC/JPA dependency (see `ppm-core/README.md`) — but it is **not published** as a versioned artifact outside this repository (confirmed: `group = 'com.company'`, `version = '0.0.1-SNAPSHOT'` in `ppm-core/build.gradle`, no Maven repository publishing configured, no `ppm-spring-boot-starter` module exists in this repository's `settings.gradle`). Do not plan an integration around embedding `ppm-core`; it is not currently available for external consumption.

---

## 3. Installation

There is nothing to "install" as a dependency — `ppm-svc` is a service you deploy and call over HTTP.

### Running it yourself (for local integration testing)

```bash
git clone <this-repository>
cd Gen_PPM
./gradlew :ppm-svc:bootRun
```

This starts the service on port `8106` by default (see `application.yaml`, `server.port`).

### Version / platform requirements

| Requirement | Value | Source |
|---|---|---|
| Java | 21 (Gradle toolchain-pinned) | `ppm-svc/build.gradle` |
| Spring Boot | 4.0.6 | `ppm-svc/build.gradle` plugin version |
| PostgreSQL | any version compatible with the `org.postgresql:postgresql` JDBC driver and the Flyway migrations listed in §6 | — |
| Redis / Valkey | any version compatible with Spring Data Redis (`StringRedisTemplate`, Set operations) | — |
| RabbitMQ | connection configured but **no publishers or listeners are currently implemented** (see `ppm-svc/README.md`: *"No publishers are implemented yet"*) | `ppm-svc/build.gradle`, `application.yaml` |

### Build artifacts

`./gradlew :ppm-svc:bootJar` produces a runnable fat JAR. `ppm-svc/Dockerfile` builds a container image (`eclipse-temurin:21-jre-alpine` runtime, exposes port `8106`, has a `HEALTHCHECK` against `/actuator/health`).

> **Warning**
>
> `ppm-svc/Dockerfile` and `ppm-svc/README.md` reference monorepo-era paths (`apps/ppm-svc`, `libs/java-common`, `./gradlew :apps:ppm-svc:bootJar`) that do not match this repository's actual layout (`Gen_PPM/ppm-svc`, no `libs/java-common` dependency — it was vendored into `ppm-svc`'s own security package during extraction). These files are stale. Use `./gradlew :ppm-svc:bootJar` / `./gradlew :ppm-svc:bootRun` from the actual repository root instead.

### Transitive dependencies of note

`spring-boot-starter-webmvc`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `spring-boot-starter-amqp`, `spring-boot-starter-flyway`, `mapstruct` 1.6.3, `springdoc-openapi-starter-webmvc-ui` 3.0.3, `shedlock-spring`/`shedlock-provider-jdbc-template` 5.16.0, `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `micrometer-registry-prometheus` (runtime). Full list in `ppm-svc/build.gradle`.

---

## 4. Auto Configuration

`ppm-svc` is a normal `@SpringBootApplication` — there is no separate auto-configuration mechanism to opt into and no `@Enable...` annotation a consumer needs to add, because **you do not embed this code** — you run the service and call its HTTP API.

What happens inside the service at startup (for your understanding, not something you configure):

- Component scanning covers `com.company.ppmsvc` — every `@Service`/`@Component`/`@RestController` (all controllers, application services, persistence adapters, security filters) is picked up automatically.
- JPA entity scanning covers the same base package — every `@Entity` class under `infrastructure.persistence.entity`.
- Flyway runs automatically on startup (`spring.flyway.enabled: true`) against `classpath:db/migration`, tracked in a dedicated history table `flyway_schema_history_ppm` (not the default `flyway_schema_history` — this lets multiple services share one database without colliding).
- `spring.jpa.hibernate.ddl-auto: validate` — Hibernate never creates or alters schema; Flyway is the only schema-mutation mechanism.

---

## 5. Required Configuration

All configuration is via environment variables with defaults baked into `application.yaml`. **Nothing is strictly required to start the service in a local/dev context** — every property has a default — but every default points at `localhost` and must be overridden for any non-local environment.

| Property (env var) | Required | Default | Description |
|---|---|---|---|
| `SERVER_PORT` | No | `8106` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | No | `local` | Active Spring profile |
| `PPM_DB_URL` | No¹ | `jdbc:postgresql://localhost:5432/ppmdb` | PostgreSQL JDBC URL |
| `PPM_DB_USERNAME` | No¹ | `ppm_owner` | DB username |
| `PPM_DB_PASSWORD` | No¹ | `changeme` | DB password |
| `PPM_DB_MAX_POOL_SIZE` | No | `20` | HikariCP max pool size |
| `PPM_DB_MIN_IDLE` | No | `5` | HikariCP min idle connections |
| `AUTH_SVC_JWKS_URI` | No¹ | `http://localhost:8101/.well-known/jwks.json` | JWKS endpoint used to verify inbound JWTs |
| `AUTH_SVC_ISSUER` | No¹ | `https://auth.cpms.io` | Expected JWT `iss` claim |
| `PPM_REDIS_HOST` | No¹ | `localhost` | Redis/Valkey host (permission cache + JTI revocation) |
| `PPM_REDIS_PORT` | No | `6379` | Redis/Valkey port |
| `RABBITMQ_URL` | No | `amqp://guest:guest@localhost:5672` | AMQP broker address (connection is opened; no publishers/consumers currently registered) |
| `OTLP_TRACING_ENDPOINT` | No | `http://localhost:4318/v1/traces` | OTLP trace exporter endpoint |
| `TRACING_SAMPLING_PROBABILITY` | No | `1.0` | Trace sampling rate |
| `PPM_SCHEDULING_ENABLED` | No | `true` | Enables/disables scheduled jobs (see §16 — no scheduled job is currently implemented in the read code paths covered by this guide) |

¹ Has a working default for local development, but **must** be overridden in any shared/staging/production environment — the defaults point at localhost services and a placeholder password.

> **Warning**
>
> `changeme` is a real default password baked into `application.yaml`. Do not deploy with the default `PPM_DB_PASSWORD`.

### Complete example `.env` / environment block

```yaml
# Non-local deployment example
SERVER_PORT: "8106"
SPRING_PROFILES_ACTIVE: "staging"

PPM_DB_URL: "jdbc:postgresql://ppm-db.internal:5432/ppmdb"
PPM_DB_USERNAME: "ppm_owner"
PPM_DB_PASSWORD: "${SECRET_PPM_DB_PASSWORD}"
PPM_DB_MAX_POOL_SIZE: "20"
PPM_DB_MIN_IDLE: "5"

AUTH_SVC_JWKS_URI: "https://auth.internal.example.com/.well-known/jwks.json"
AUTH_SVC_ISSUER: "https://auth.internal.example.com"

PPM_REDIS_HOST: "ppm-redis.internal"
PPM_REDIS_PORT: "6379"

RABBITMQ_URL: "amqp://ppm:changeme@rabbitmq.internal:5672"

OTLP_TRACING_ENDPOINT: "http://otel-collector.internal:4318/v1/traces"
TRACING_SAMPLING_PROBABILITY: "0.1"
```

---

## 6. Database Integration

- **Required database:** PostgreSQL (JDBC driver `org.postgresql:postgresql`; `hibernate.dialect` auto-detected).
- **Migration tool:** Flyway, `classpath:db/migration`, history table `flyway_schema_history_ppm`, `baseline-on-migrate: true`, `baseline-version: 0`, `validate-on-migrate: true`.
- **`ddl-auto`:** hard-set to `validate`. Hibernate will refuse to start if the mapped entities don't match the actual schema — Flyway must always run first (it does, automatically).

### Tables created (in migration order)

| Migration | Tables | Purpose |
|---|---|---|
| V001 | baseline | — |
| V002 | `ppm_modules` | Module catalog |
| V003 | `ppm_plans` | Plan catalog |
| V004 | (sequence) | Plan slug generation |
| V005 | `ppm_plan_modules` | Plan↔Module join |
| V006 | `ppm_entitlements` | Entitlement catalog |
| V007 | `ppm_plan_prices` | Plan pricing rows |
| V008 | `ppm_plan_versions` | Plan version snapshots |
| V009 | `ppm_promo_codes`, `ppm_promo_code_plans` | Legacy promo code catalog + plan restrictions |
| V010 | `ppm_add_ons` | Add-on catalog |
| V011 | (plan version limits) | — |
| V012 | (plan tier column) | — |
| V013 | `ppm_promotions`, `ppm_coupons` | Promotion + Coupon aggregates |
| V014 | `ppm_promotion_redemptions` + `conditions_payload`/`usage_cap_per_user` columns on `ppm_promotions` | Per-user redemption ledger, conditions |
| V015 | `ppm_referral_programs`, `ppm_referral_codes`, `ppm_referral_events` + `source` column on `ppm_promotions` | Referral bounded context, `PromotionSource` tag |
| V016 | `ppm_campaigns` + `campaign_id` column on `ppm_promotions` | Campaign grouping |

### Key schema facts an integrator should know

- Every table uses a `UUID` primary key generated by `gen_random_uuid()` (requires the PostgreSQL `pgcrypto` extension or PG13+ built-in `gen_random_uuid()` — confirm your PostgreSQL version supports it).
- Every table has soft-delete via a nullable `deleted_at` column; unique indexes are **partial** (`WHERE deleted_at IS NULL`), so a deleted row's unique key (e.g. a coupon code) can be reused by a new row.
- `ppm_promotions.campaign_id` and `ppm_promotions.source` are plain columns with **no foreign key constraint** on `campaign_id` (deliberate — see V016's own comment: soft-deleting a campaign must not cascade or be blocked by its promotions).
- `ppm_referral_events.created_by`/`updated_by` are **nullable** (unlike every other audit table) because referral conversions are system-triggered with no authenticated actor; the code issues coupons on behalf of the system using a reserved nil UUID (`00000000-0000-0000-0000-000000000000`) for `ppm_coupons.created_by`/`updated_by` in that one flow.
- `action_payload` (on `ppm_promotions`) and the equivalent on redemption/reward records are `JSONB` columns storing the polymorphic `PromotionAction` (six concrete shapes: `percentage`, `flat`, `fixed_price`, `free_period`, `free_module`, `free_addon`) — do not write to this table directly; always go through the API.

### Existing-database compatibility

Not applicable — PPM owns its own schema (`ppm_*` tables) and does not attempt to integrate with a pre-existing schema. If you already run a `flyway_schema_history` table for another service in the same physical database, PPM's dedicated `flyway_schema_history_ppm` table avoids collision.

### Common mistakes

- Running the service against a database that has **not** had Flyway migrations applied and expecting Hibernate to create tables — it will not (`ddl-auto: validate`); the service will fail to start.
- Manually editing `ppm_promotions.action_payload`/`conditions_payload` — the exact JSON shape (discriminator field `"type"`) is load-bearing; malformed JSON will surface as a 500 at read time.

---

## 7. External Dependencies

| Dependency | Why required | If unavailable | Optional? | Startup behaviour | Failure behaviour |
|---|---|---|---|---|---|
| **PostgreSQL** | System of record for every catalog/promotion/coupon/referral/campaign row | Service fails to start (Flyway/Hikari cannot connect) | No | Startup blocks on Flyway migration + Hikari pool init | Runtime queries fail; requests error |
| **Redis / Valkey** | (a) JTI revocation check, (b) role→permission set lookup for authorization | Both checks are implemented **fail-open/fail-closed differently** — see below | No (but degrades gracefully per-check) | No startup dependency — connection is lazy | See below |
| **AUTH-SVC (JWKS endpoint)** | JWT signature verification | JWT validation fails; all authenticated requests return 401 | No, for any authenticated endpoint | `JwtDecoder` bean fetches the JWKS lazily on first token validation, not at startup | Requests fail with 401 until the JWKS endpoint is reachable |
| **RabbitMQ** | Connection is configured (`spring-boot-starter-amqp`) | Connection retry per Spring AMQP defaults | Effectively yes for current functionality — **no publishers or consumers are registered** (confirmed in `ppm-svc/README.md`) | Attempts connection at startup per Spring Boot AMQP auto-configuration | Not exercised by any current code path in the promotion/pricing engine |

**Redis failure-mode detail (this is deliberately asymmetric — verified in the filter source):**

- `JtiRevocationFilter`: **fail-open**. If Redis is unreachable, the revocation check is skipped and the request proceeds. Rationale documented in code: short JWT TTL (15 min) limits the exposure window.
- `PpmAccessAuthorizationFilter` (via `RedisRolePermissionResolver`): **fail-closed** for tenant users. If Redis is unreachable, `resolveAll(...)` catches the exception, logs a warning, and returns an empty permission set — the caller will not have `ppm.read` and gets `403`. SUPER_ADMIN callers are unaffected (they bypass the Redis lookup entirely).

> **Best Practice**
>
> If you are integrating a service-to-service caller that must always succeed regardless of Redis health, that caller should authenticate as a SUPER_ADMIN-typed principal (if your platform's AUTH-SVC supports issuing such tokens for internal callers) — or call one of the endpoints PPM has marked public (see §11/§12), which skip the permission check entirely.

---

## 8. Application Startup

In order, on `./gradlew :ppm-svc:bootRun` (or running the built JAR):

1. Spring context bootstraps; `@SpringBootApplication` component/entity scan over `com.company.ppmsvc`.
2. Configuration properties bind (environment variables → `application.yaml` placeholders); `CpmsSecurityProperties` (`@EnableConfigurationProperties`) binds `cpms.security.jwks-uri` / `cpms.security.issuer`.
3. HikariCP connection pool initializes against `PPM_DB_URL`.
4. Flyway runs all pending migrations from `classpath:db/migration` against `flyway_schema_history_ppm`. **This is the step most likely to fail startup** — a missing database, wrong credentials, or a schema drift causes `validate-on-migrate` to abort startup with a clear Flyway error in the logs.
5. Hibernate validates every `@Entity` mapping against the now-migrated schema (`ddl-auto: validate`) — a mismatch here (e.g. a manually-altered table) also fails startup.
6. Spring Security's filter chain is built (`SecurityConfig.filterChain`): JWT resource-server config, then `JtiRevocationFilter` → `PpmAccessAuthorizationFilter` → `PpmAdminAuthorizationFilter` are inserted into the chain in that order.
7. Springdoc OpenAPI scans all `@RestController`s to build the OpenAPI document served at `/v1/docs/openapi.json`.
8. Actuator endpoints (`health`, `info`, `metrics`, `prometheus`) become available.
9. Embedded Tomcat starts listening on `SERVER_PORT` (default `8106`).

No custom `ApplicationRunner`/`CommandLineRunner` seed logic is present in this codebase — the service starts with whatever data already exists in the database.

---

## 9. First Integration

Complete, runnable example: a hypothetical **checkout service** resolving a price quote and then, on successful subscription, recording a referral conversion. This uses only `curl` — no PPM code is imported by the caller (there is nothing to import; see §2).

### Step 1 — obtain a bearer token

Not covered by this repository (issued by AUTH-SVC). For this walkthrough, assume you have a valid JWT in `$TOKEN` for a SUPER_ADMIN-typed principal (see §12 for why this matters for the endpoints below).

### Step 2 — resolve a price quote for a plan with a coupon

```bash
curl -s -X POST http://localhost:8106/api/v1/ppm/quotes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "planId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "region": "india",
    "currency": "inr",
    "cycle": "monthly",
    "couponCode": "DIWALI20"
  }'
```

Example response (`200 OK`):

```json
{
  "success": true,
  "message": "Quote resolved.",
  "data": {
    "baseAmount": 2000.0000,
    "currency": "inr",
    "discountAmount": 200.0000,
    "finalAmount": 1800.0000,
    "reason": "valid",
    "promotionId": "9c858901-8a57-4791-81fe-4c455b099bc9",
    "appliedAction": {
      "type": "percentage",
      "percentage": 20,
      "maxDiscountValue": 200,
      "minDiscountValue": null
    },
    "conditionsSkipped": true
  }
}
```

### Step 3 — on successful checkout, record the referral conversion (if the customer used a referral code, not a coupon)

```bash
curl -s -X POST http://localhost:8106/api/v1/ppm/referrals/convert \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "referralCode": "REFER-NAMAN",
    "referredCustomerId": "cust_9f1c2b"
  }'
```

Example response (`200 OK`):

```json
{
  "success": true,
  "message": "Referral converted.",
  "data": {
    "promotionId": "1e2d3c4b-5a69-4788-9a12-abcdef123456",
    "customerId": "referrer-1",
    "couponCode": "REFRR-REFERRER-A1B2C3D4"
  }
}
```

The checkout service is now responsible for delivering `couponCode` to the referrer (e.g. email) — PPM does not do this.

> **Note**
>
> There is no SDK. Every "client" in this platform is a plain HTTP client (`RestTemplate`, `WebClient`, `curl`, or any language's HTTP library) calling these JSON endpoints directly.

---

## 10. Request Flow

### 10.1 Coupon-based price quote (`POST /api/v1/ppm/quotes`)

```mermaid
sequenceDiagram
    participant Caller as Caller (e.g. Checkout)
    participant PPM as ppm-svc (PriceQuoteController)
    participant Pricing as PromotionPricingServiceImpl
    participant DB as PostgreSQL

    Caller->>PPM: POST /api/v1/ppm/quotes {planId, region, currency, cycle, couponCode}
    PPM->>Pricing: quote(...) or quoteWithCustomer(...)
    Pricing->>DB: resolve PlanPrice (PricingResolver)
    DB-->>Pricing: PlanPrice or not-found
    alt plan or price not found
        Pricing-->>PPM: throws ResourceNotFoundException
        PPM-->>Caller: 404
    else resolved
        Pricing->>DB: find Coupon by code
        Pricing->>DB: find Promotion by coupon.promotionId
        Pricing->>Pricing: evaluate status/validity window
        Pricing->>Pricing: evaluate conditions (if customerContext supplied)
        Pricing->>Pricing: apply PriceAction (discount) or EntitlementAction (grant)
        Pricing-->>PPM: PriceQuote
        PPM-->>Caller: 200 {reason, discountAmount|grantedEntitlement, finalAmount}
    end
```

### 10.2 Referral conversion (`POST /api/v1/ppm/referrals/convert`)

```mermaid
sequenceDiagram
    participant Caller as Caller (e.g. Checkout, on successful subscription)
    participant PPM as ppm-svc (ReferralConversionController)
    participant Svc as ReferralConversionServiceImpl
    participant DB as PostgreSQL

    Caller->>PPM: POST /api/v1/ppm/referrals/convert {referralCode, referredCustomerId}
    PPM->>Svc: onConversion(referralCode, referredCustomerId)
    Svc->>DB: find ReferralCode by code
    Svc->>DB: find ReferralProgram by code.referralProgramId
    Svc->>DB: check existing ReferralEvent for (code, referredCustomerId)
    alt already converted
        Svc-->>PPM: throws BusinessException(REFERRAL_ALREADY_CONVERTED)
        PPM-->>Caller: 409
    else cap reached
        Svc-->>PPM: throws BusinessException(REFERRAL_CAP_REACHED)
        PPM-->>Caller: 409
    else eligible
        Svc->>DB: create ReferralEvent (status=CONVERTED, convertedAt)
        Svc->>DB: update ReferralEvent (rewardGrantedAt)
        Svc->>DB: create Coupon pointing at program.referrerRewardPromotionId
        Svc-->>PPM: ReferralReward{promotionId, customerId, couponCode}
        PPM-->>Caller: 200 {promotionId, customerId, couponCode}
    end
```

### 10.3 Authorization filter chain (every request)

```mermaid
sequenceDiagram
    participant Caller
    participant JWT as OAuth2 Resource Server (JWT validation)
    participant Jti as JtiRevocationFilter
    participant Access as PpmAccessAuthorizationFilter
    participant Admin as PpmAdminAuthorizationFilter
    participant Ctrl as Controller

    Caller->>JWT: Bearer token
    alt invalid signature/issuer/expired
        JWT-->>Caller: 401
    else valid
        JWT->>Jti: authenticated principal
        Jti->>Jti: check auth:revoked:{jti} in Redis (fail-open)
        alt revoked
            Jti-->>Caller: 401
        else not revoked
            Jti->>Access: continue
            Access->>Access: public path? SUPER_ADMIN? else check ppm.read in Redis (fail-closed)
            alt forbidden
                Access-->>Caller: 403
            else allowed
                Access->>Admin: continue
                Admin->>Admin: write method (POST/PUT/PATCH/DELETE) on non-public, non-/quotes path? delegate to PpmAuthorizationService.authorizeAdminWrite()
                alt forbidden
                    Admin-->>Caller: 403
                else allowed
                    Admin->>Ctrl: request proceeds
                    Ctrl-->>Caller: response
                end
            end
        end
    end
```

---

## 11. REST API

Base path for every endpoint below: `/api/v1/ppm`. All responses are wrapped in the standard envelope:

```json
{ "success": true, "message": "...", "data": { } }
```

or, on error:

```json
{ "success": false, "message": "..." }
```

This section documents the **Promotion Engine** surface in full (the primary subject of this guide) and summarizes the supporting catalog APIs it depends on. For the full DTO shapes of every field, the authoritative source is the OpenAPI document served live by the running service at `GET /v1/docs/openapi.json` (Swagger UI at `/v1/docs`) — this guide's tables are accurate as of this codebase but the OpenAPI document is generated directly from the code and will never drift.

### 11.1 Price Quotes

#### `POST /api/v1/ppm/quotes`

Resolves a plan's price and applies a coupon's promotion, if any. **Always returns HTTP 200** for coupon/promotion outcomes; only a missing plan or price returns 404.

| | |
|---|---|
| **Auth** | Bearer JWT + `ppm.read`, **or** SUPER_ADMIN (this is a `POST`, and it is **not** in `PpmAdminAuthorizationFilter`'s public-POST exemption list — see §12 Warning) |
| **Request body** | `{ planId (UUID, required), region (string, required), currency (string, required), cycle ("monthly"\|"annual", required), couponCode (string, optional), customerContext ({customerId, isNewCustomer}, optional) }` |
| **Response** | `PriceQuoteResponse` — see field table below |

**Response fields:**

| Field | Type | Present when |
|---|---|---|
| `baseAmount` | decimal | always |
| `currency` | string | always |
| `discountAmount` | decimal | `reason=valid` **and** the promotion's action is a price-reducing action |
| `finalAmount` | decimal | always (equals `baseAmount` unless a discount applied) |
| `reason` | enum | always — see reason table below |
| `promotionId` | UUID | only when `reason=valid` |
| `appliedAction` | polymorphic object | only when `reason=valid` — one of `percentage`/`flat`/`fixed_price`/`free_period`/`free_module`/`free_addon` (discriminated by `"type"`) |
| `conditionsSkipped` | boolean | always — `true` if `customerContext` was omitted |
| `grantedEntitlement` | object `{type, targetId, durationMonths}` | only when `reason=valid` **and** the promotion's action is an entitlement grant |

**`reason` values:**

| Reason | Meaning |
|---|---|
| `valid` | Coupon applied successfully |
| `no_coupon` | No `couponCode` supplied |
| `coupon_not_found` | No active coupon with that code |
| `coupon_inactive` | Coupon exists but is deactivated |
| `promotion_inactive` | Promotion is inactive, draft, or missing |
| `promotion_not_started` | Today is before the promotion's `validFrom` |
| `promotion_expired` | Today is after the promotion's `validUntil` |
| `plan_not_eligible` | Promotion's plan-restriction condition excludes this plan (requires `customerContext`) |
| `eligibility_violation` | Customer doesn't match the eligibility condition (requires `customerContext`) |
| `usage_limit_per_user` | Customer already reached the promotion's per-user cap (requires `customerContext`) |

> **Note**
>
> Supplying `customerContext` switches the engine from `quote(...)` to `quoteWithCustomer(...)` internally, which additionally evaluates plan-restriction, eligibility, and per-user usage-cap conditions. Omitting it evaluates only status + validity window (`conditionsSkipped=true`).

**cURL:**

```bash
curl -s -X POST http://localhost:8106/api/v1/ppm/quotes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "planId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "region": "india",
    "currency": "inr",
    "cycle": "monthly",
    "couponCode": "DIWALI20",
    "customerContext": { "customerId": "cust_9f1c2b", "isNewCustomer": true }
  }'
```

#### `POST /api/v1/ppm/referrals/quote`

Same response shape as above, resolved via a referral code instead of a coupon code. `customerContext` is **required** here (unlike the coupon path) — referral pricing always evaluates conditions.

| | |
|---|---|
| **Auth** | Bearer JWT + `ppm.read`, or SUPER_ADMIN |
| **Request body** | `{ planId, region, currency, cycle, referralCode (required), customerContext (required) }` |
| **Additional `reason` values** | `referral_code_not_found`, `referral_code_inactive`, `referral_program_inactive` |

#### `POST /api/v1/ppm/referrals/convert`

Records a referral conversion and grants the referrer's reward (issues a one-time coupon). **Not idempotent by retry** in the sense of returning the same result twice — see §14/§15.

| | |
|---|---|
| **Auth** | Bearer JWT + `ppm.read`, or SUPER_ADMIN |
| **Request body** | `{ referralCode (required), referredCustomerId (required) }` |
| **Response `200`** | `{ promotionId, customerId, couponCode }` — `couponCode` is the newly-issued coupon for the **referrer**, pointing at the referrer's reward promotion |
| **Response `404`** | Referral code or its program not found |
| **Response `409`** | Already converted for this exact `(referralCode, referredCustomerId)` pair, or the referrer's program cap (`maxReferralsPerReferrer`) reached |

### 11.2 Promotion Catalog

Base path `/api/v1/ppm/promotions`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/` | Create a promotion |
| `PATCH` | `/{id}` | Partially update (see PATCH semantics warning below) |
| `GET` | `/{id}` | Get by ID |
| `GET` | `/?status=` | List, optional status filter (`draft`\|`active`\|`inactive`) |
| `DELETE` | `/{id}` | Soft-delete |

**Create/update request body:**

```json
{
  "name": "Diwali Sale",
  "description": "20% off, capped at ₹200",
  "action": { "type": "percentage", "percentage": 20, "maxDiscountValue": 200, "minDiscountValue": null },
  "validFrom": "2026-10-01",
  "validUntil": "2026-11-15",
  "status": "active",
  "source": "normal",
  "campaignId": null,
  "conditions": [
    { "type": "eligibility", "eligibilityType": "new_customer" }
  ],
  "usageCapPerUser": 1
}
```

**`action.type` values and their required fields:**

| `type` | Fields | Category |
|---|---|---|
| `percentage` | `percentage` (0–100), `maxDiscountValue` (nullable), `minDiscountValue` (nullable) | Price reduction |
| `flat` | `amount` (>0) | Price reduction |
| `fixed_price` | `price` (>0) | Price reduction — sets the final price directly |
| `free_period` | `durationMonths` (1–12) | Entitlement grant — no target |
| `free_module` | `moduleId` (must reference an existing Module), `durationMonths` (1–12) | Entitlement grant |
| `free_addon` | `addOnId` (must reference an existing AddOn), `durationMonths` (1–12) | Entitlement grant |

**`conditions[].type` values:**

| `type` | Fields | Effect |
|---|---|---|
| `plan_restriction` | `planIds` (set, empty = unrestricted) | Restricts the promotion to specific plans |
| `eligibility` | `eligibilityType` (`new_customer`\|`existing_customer`) | Restricts by customer new/existing status |
| `usage_limit` | `usageCapPerUser` | Informational only — the actual cap is enforced from the promotion's own `usageCapPerUser` field regardless of whether this condition is present |

> **Warning — PATCH semantics are not uniform.** For every field on `PATCH /promotions/{id}` **except `campaignId`**, `null` means "leave unchanged." For `campaignId` specifically, `null` means **"remove this promotion from its campaign"** — it is always applied, not skipped. A non-null `campaignId` is verified to exist and always replaces the current value. This is a deliberate, documented exception in the code (`PromotionApplicationService` Javadoc) — if you omit `campaignId` from a PATCH body expecting it to be left alone, **it will be cleared**.

> **Best Practice**
>
> Always send the current `campaignId` back explicitly on every `PATCH /promotions/{id}` call unless you intend to remove the promotion from its campaign.

### 11.3 Coupon Catalog

Base path `/api/v1/ppm/coupons`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/` | Create — `{ code, promotionId, active }`. `code` is normalised to uppercase; `promotionId` must resolve. |
| `PATCH` | `/{id}` | Update `active` only — `code` is immutable after creation |
| `GET` | `/{id}` | Get by ID |
| `GET` | `/code/{code}` | Get by code string (404 if not found/inactive) |
| `GET` | `/?active=` | List, optional active filter |
| `DELETE` | `/{id}` | Soft-delete |

### 11.4 Referral Programs & Codes

Base path `/api/v1/ppm/referral-programs`:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/` | `{ name, description, referrerRewardPromotionId, referredRewardPromotionId, status, maxReferralsPerReferrer }` — both reward promotion IDs must already exist |
| `PATCH` | `/{id}` | Update |
| `GET` | `/{id}` | Get |
| `GET` | `/` | List |
| `DELETE` | `/{id}` | Soft-delete |

Base path `/api/v1/ppm/referral-codes`:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/` | `{ code, referralProgramId, referrerCustomerId, status }` — one code per (program, referrer) pair enforced |
| `PATCH` | `/{id}` | Update `status` only — code immutable |
| `GET` | `/{id}`, `/code/{code}` | Get |
| `GET` | `/` | List |
| `DELETE` | `/{id}` | Soft-delete |

### 11.5 Campaigns

Base path `/api/v1/ppm/campaigns`:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/` | `{ name, description, validFrom, validUntil, status }` — `status` defaults to `draft` (campaigns default differently from promotions, which default to `active`) |
| `PATCH` | `/{id}` | Update |
| `GET` | `/{id}` | Get |
| `GET` | `/?status=` | List, optional filter (`draft`\|`active`\|`completed`\|`archived`) |
| `DELETE` | `/{id}` | Soft-delete — **does not cascade** to promotions; their `campaignId` is left dangling and they continue to function normally |

`GET /api/v1/ppm/campaigns/{campaignId}/promotions` — lists every promotion currently assigned to a campaign (`404` if the campaign doesn't exist). There is deliberately no `POST`/`DELETE` on this sub-resource — assignment happens via `campaignId` on the Promotion CRUD itself.

> **Note — Campaign is organizational, not enforced at quote time.** A promotion belonging to an archived or expired campaign is still fully evaluable by `/quotes` — the campaign's own status/validity never gates pricing. If you need campaign-level enforcement, it must be modeled as a condition on the promotion itself; the pipeline does not check campaign state.

### 11.6 Supporting Catalog APIs

The Promotion Engine sits on top of the Plan/Pricing catalog. These are pre-existing, stable APIs and several are **public (no auth required)** — see §12.

| Base path | Purpose | Notable public endpoints |
|---|---|---|
| `/api/v1/ppm/plans` | Plan CRUD | `GET /plans`, `GET /plans/{id}`, `GET /plans/code/{code}`, `GET /plans/slug/{slug}` |
| `/api/v1/ppm/modules` | Module CRUD | `GET /plans/{id}/modules` |
| `/api/v1/ppm/add-ons` | Add-on CRUD | `GET /add-ons/{id}/prices/active` |
| `/api/v1/ppm/entitlements`, `/plan-entitlements` | Entitlement CRUD + resolved entitlements | `GET /plans/{id}/entitlements/resolved` |
| `/api/v1/ppm/prices` | Plan price resolution | `POST /prices/resolve` |
| `/api/v1/ppm/promo-codes` | Legacy promo code catalog | `POST /promo-codes/validate` |
| `/api/v1/ppm/plan-versions` | Plan version snapshots | `GET /plan-versions/{id}/meta`, `GET /plan-versions/{id}/limits`, `GET /plans/{id}/versions/latest` |

### 11.7 Error response format

```json
{ "success": false, "message": "Promotion not found: 9c858901-8a57-4791-81fe-4c455b099bc9" }
```

Validation errors (`422`) carry additional structure:

```json
{
  "success": false,
  "message": "Percentage must be greater than 0 and at most 100.",
  "data": {
    "timestamp": "2026-07-23T12:00:00Z",
    "code": "PPM-0002",
    "message": "Percentage must be greater than 0 and at most 100.",
    "path": "/api/v1/ppm/promotions",
    "violations": []
  }
}
```

---

## 12. Authentication

**Mechanism:** OAuth2 JWT Bearer tokens, RS256-signed, verified against a JWKS endpoint (`AUTH_SVC_JWKS_URI`) with issuer validation (`AUTH_SVC_ISSUER`). This is implemented with Spring Security's OAuth2 Resource Server support (`NimbusJwtDecoder`) — there is no API-key or internal-shared-secret mechanism in this codebase.

**Authorization is layered on top of authentication in three sequential filters** (see the sequence diagram in §10.3):

| Filter | Rule |
|---|---|
| `JtiRevocationFilter` | Rejects tokens whose `jti` claim is in `auth:revoked:{jti}` in Redis. Fail-open on Redis outage. |
| `PpmAccessAuthorizationFilter` | Public paths pass through unauthenticated. SUPER_ADMIN-typed principals bypass everything else. All other authenticated callers must have `ppm.read` in their resolved permission set (looked up in Redis by `role:{roleId}`, populated by an external ADM-SVC process — PPM only reads this keyspace, never writes it). Fail-closed (403) on Redis outage for non-SUPER_ADMIN callers. |
| `PpmAdminAuthorizationFilter` | For any `POST`/`PUT`/`PATCH`/`DELETE` on a non-public path (except `/quotes`, which authorizes itself — see below), delegates to `PpmAuthorizationService.authorizeAdminWrite()`. This platform's wiring of that call still requires SUPER_ADMIN — there is no permission code that grants write access to a tenant role — but a different deployment of this library can supply its own rule (see "Authorization SPI" below). |

### Authorization SPI

`ppm-core` does not hardcode a role model. It exposes a small, framework-independent interface, `PpmAuthorizationService` (`com.company.ppmsvc.security`), with one method per authorization decision the engine needs to make:

```java
public interface PpmAuthorizationService {
    void authorizePromotionRead();
    void authorizePromotionWrite();
    void authorizeCouponRead();
    void authorizeCouponWrite();
    void authorizeCampaignRead();
    void authorizeCampaignWrite();
    void authorizeReferralRead();
    void authorizeReferralWrite();
    void authorizeQuote();
    void authorizeAdminWrite();
}
```

Each method should return normally to permit the operation, or throw `AccessDeniedException` (the domain-level, framework-independent 403) to deny it. No Spring Security, Servlet, or JWT type appears in this interface — the library defines *what* needs authorizing, the integrating application decides *how*.

- **Default:** an application that defines no `PpmAuthorizationService` bean of its own gets `PermitAllPpmAuthorizationService` — every method is a no-op — via `@ConditionalOnMissingBean`.
- **This platform's wiring:** `PpmAuthorizationConfig` explicitly wires `PlatformPpmAuthorizationService`, which reproduces this platform's existing rules unchanged — reads open to any authenticated caller, writes and `authorizeQuote()` requiring SUPER_ADMIN.
- **Overriding it:** define your own bean of the same type; it replaces this platform's default entirely.

```java
@Bean
public PpmAuthorizationService authorizationService() {
    return new MyAuthorizationService();
}
```

`POST /api/v1/ppm/quotes` calls `authorizeQuote()` directly from `PriceQuoteController` rather than going through the blanket `PpmAdminAuthorizationFilter` write gate — this is the one endpoint this hardening pass moved onto its own hook, since a quote lookup being gated identically to a catalog mutation made little sense for a reusable library. Every other write endpoint (Promotions, Coupons, Campaigns, Referrals, and the legacy catalog) still runs through `authorizeAdminWrite()`.

**Public endpoints** (no token required at all) — exactly these, verified against `SecurityConfig`:

```
GET  /api/v1/ppm/plans
GET  /api/v1/ppm/plans/{id}
GET  /api/v1/ppm/plans/slug/{slug}
GET  /api/v1/ppm/plans/code/{code}
GET  /api/v1/ppm/plans/{id}/versions/latest
GET  /api/v1/ppm/add-ons/{id}/prices/active
GET  /api/v1/ppm/plans/{id}/entitlements/resolved
GET  /api/v1/ppm/plans/{id}/modules
GET  /api/v1/ppm/plan-versions/{id}/meta
GET  /api/v1/ppm/plan-versions/{id}/limits
POST /api/v1/ppm/prices/resolve
POST /api/v1/ppm/promo-codes/validate
```

> **Warning — on this platform's current wiring, every Promotion/Coupon/Referral/Campaign/Quote endpoint still requires SUPER_ADMIN.**
>
> `POST /quotes`, `POST /referrals/quote`, `POST /referrals/convert`, and every write operation on Promotions, Coupons, Referral Programs/Codes, and Campaigns are `POST`/`PATCH`/`DELETE` requests. As of this hardening pass, that rule is no longer hardcoded — it comes from `PlatformPpmAuthorizationService`, this deployment's chosen implementation of `PpmAuthorizationService` (see "Authorization SPI" above). This repository's own integration tests authenticate as `CpmsUserType.SUPER_ADMIN` for every one of these endpoints, confirming this remains the actual current behavior; it just now lives behind a customization point instead of an inline role check.
>
> If your integrating service is not able to authenticate as SUPER_ADMIN, these endpoints will return `403` under this platform's current wiring. If you control the deployment, define your own `PpmAuthorizationService` bean (or use the library default, `PermitAllPpmAuthorizationService`, if you don't need this gate at all) — no fork or code change to `ppm-core`/`ppm-svc` required. Otherwise, coordinate with the PPM team to change this platform's wiring before depending on these endpoints from a lower-privileged service identity.

---

## 13. Integration with Other Services

| Consumer | What it should call | Library responsibility | Consumer responsibility |
|---|---|---|---|
| **Checkout** | `POST /quotes` or `POST /referrals/quote` before charging; `POST /referrals/convert` after a successful subscription | Compute the correct price/discount or entitlement grant for a given plan + coupon/referral code + customer facts | Actually charge the customer for `finalAmount`; decide whether to call the coupon path or referral path; call `/referrals/convert` exactly once per successful conversion |
| **Billing** | `POST /prices/resolve` (base price), `POST /quotes` (with discount) | Resolve the applicable price row for a plan/region/currency/cycle | Invoice generation, recurring billing, proration — none of this exists in PPM |
| **Provisioning** | Reads `grantedEntitlement` from a quote response (or wherever checkout forwards it) | Compute *what* was granted (`type`, `targetId`, `durationMonths`) | Actually enable the free module/add-on/period on the customer's account — PPM makes no provisioning call itself |
| **Signup / REG-SVC** | Public plan/module/entitlement GET endpoints (§11.6, §12) | Serve the public catalog for a pricing page | Render the UI, handle plan selection |
| **Usage / USG-SVC** | `GET /plans/{id}/entitlements/resolved` (public) | Serve resolved entitlement limits for a plan | Enforce those limits at runtime — PPM does not meter usage |
| **Marketing / Ops tooling** | Campaign, Promotion, Coupon, Referral Program/Code CRUD (all SUPER_ADMIN-gated — see §12) | Persist and validate the catalog objects | Build whatever admin UI/workflow creates campaigns and promotions |

> **Note — recording a redemption is a separate, explicit step your service owns.**
>
> `POST /quotes` and `POST /referrals/quote` are **read-only**. Neither call increments any usage counter. If a promotion has a per-user cap (`usageCapPerUser`), that cap is enforced by counting rows in the `ppm_promotion_redemptions` ledger — but this codebase's HTTP API exposes **no endpoint to write to that ledger for the coupon path**. The referral path's `POST /referrals/convert` is the only implemented write path that affects future quote evaluations (it creates the underlying `ReferralEvent`, which the referral pricing pipeline counts). For coupon-based per-user caps, there is currently no documented mechanism in this codebase's public surface to record a redemption — this is a real gap in the implementation as it stands, not an omission from this guide.

---

## 14. Error Handling

| HTTP | `ErrorCode` (examples) | Meaning | Caller responsibility |
|---|---|---|---|
| `400` | `ILLEGAL_ARGUMENT`, and any `BusinessException` not in the 409/422 categories | Generic business-rule violation | Fix the request; not retryable as-is |
| `401` | — | Missing/invalid/expired/revoked JWT | Refresh or re-obtain the token |
| `403` | — | Missing `ppm.read`, or a write call from a non-SUPER_ADMIN principal (see §12) | Use a sufficiently privileged identity; not retryable with the same token |
| `404` | `PLAN_NOT_FOUND`, `PROMOTION_NOT_FOUND`, `COUPON_NOT_FOUND`, `CAMPAIGN_NOT_FOUND`, `REFERRAL_PROGRAM_NOT_FOUND`, `REFERRAL_CODE_NOT_FOUND`, `MODULE_NOT_FOUND`, `ADD_ON_NOT_FOUND`, etc. | Referenced resource does not exist or is soft-deleted | Verify the ID/code; not retryable without changing the request |
| `409` | `COUPON_CODE_ALREADY_EXISTS`, `REFERRAL_CODE_ALREADY_EXISTS`, `REFERRAL_ALREADY_CONVERTED`, `REFERRAL_CAP_REACHED`, `PLAN_PRICE_ALREADY_EXISTS`, `PLAN_VERSION_DATE_CONFLICT`, etc. | Uniqueness or state conflict | For `REFERRAL_ALREADY_CONVERTED`, this is the **expected, idempotent-safe** response to a duplicate conversion attempt — see §15 |
| `422` | `VALIDATION_ERROR`, `CONDITION_VALIDATION_ERROR` | Request body failed field-level or business validation | Fix the request payload |
| `500` | `INTERNAL_SERVER_ERROR` | Unhandled exception | Retry only if you suspect a transient issue (e.g. DB blip); otherwise escalate |

`POST /quotes` and `POST /referrals/quote` are a deliberate exception to this table: everything short of a missing plan/price is reported as `200 OK` with a `reason` field (§11.1) rather than an HTTP error — this is by design, not a bug, so a caller should branch on `reason`, not on HTTP status, for coupon/promotion/referral outcomes.

---

## 15. Retry Behaviour

- **No automatic retries are implemented anywhere in this codebase.** Neither the controllers nor the application services wrap calls in a retry mechanism (no Resilience4j, no Spring Retry annotations found in this module).
- **Idempotency:**
  - `POST /referrals/convert` is idempotent **in effect, not in response**: calling it twice with the same `(referralCode, referredCustomerId)` pair after the first call succeeded returns `409 REFERRAL_ALREADY_CONVERTED` on the second call — no duplicate coupon is issued, no duplicate `ReferralEvent` is created. Treat `409` on this endpoint as "already done" and move on; do not treat it as a failure requiring escalation.
  - `POST /quotes` and `POST /referrals/quote` are naturally idempotent — they are read-only.
  - `POST /promotions`, `POST /coupons`, `POST /referral-programs`, `POST /referral-codes`, `POST /campaigns` are **not** idempotent — calling any of them twice with the same logical intent creates two distinct records (or, for coupon/referral codes, the second call fails with a `409` duplicate-code error if you reuse the same code string).
- **Timeouts:** no explicit HTTP client-side timeout is configured by this service for outbound calls (it makes none relevant to the promotion engine); no explicit request-processing timeout is configured server-side beyond Tomcat/Spring Boot defaults.
- **Dead-letter behaviour:** not applicable — no messaging consumer exists in this codebase (RabbitMQ connection is configured but unused, per §7).

> **Best Practice**
>
> For `POST /referrals/convert`, implement your caller so a `409 REFERRAL_ALREADY_CONVERTED` response is treated as success (the conversion already happened) — this makes at-least-once delivery from your side safe without needing a separate idempotency-key mechanism, because the uniqueness check is already keyed on `(referralCode, referredCustomerId)` server-side.

---

## 16. Operational Behaviour

- **Transactions:** every write use case is annotated `@Transactional` at the method level (Spring's declarative transaction management via `spring-tx`); reads are `@Transactional(readOnly = true)`. Each HTTP request runs in its own transaction — there is no cross-request or distributed transaction.
- **Locking:** optimistic locking via a `version` column (JPA `@Version`) on every entity. A concurrent update conflict surfaces as `OPTIMISTIC_LOCK_CONFLICT` (mapped to `409`).
- **Thread safety:** all use-case implementations are stateless Spring singletons (`@Service`, constructor-injected final fields, no mutable instance state) — safe under concurrent request handling. `DiscountCalculatorImpl` and `ConditionEvaluatorImpl` are pure functions with no side effects.
- **Consistency model:** strongly consistent — every read goes directly to PostgreSQL inside a transaction. There is no caching layer in front of catalog/promotion data (Redis is used **only** for permission lookups and JTI revocation, never for domain data).
- **Scheduler behaviour:** `ppm.scheduling.enabled` exists as a configuration property and `shedlock-spring`/`shedlock-provider-jdbc-template` are on the classpath (implying some scheduled job exists somewhere in the broader `ppm-svc` codebase), but **no scheduled job is present in the promotion/pricing/campaign/referral code paths covered by this guide.** If your integration depends on a background job related to promotions, campaigns, or referrals, none currently exists — do not assume one.
- **Caching:** none, for anything documented in this guide.

---

## 17. Production Recommendations

- **Connection pool:** HikariCP is pre-configured with sane defaults (`maximum-pool-size: 20`, `minimum-idle: 5`, `leak-detection-threshold: 60000`ms) — tune `PPM_DB_MAX_POOL_SIZE`/`PPM_DB_MIN_IDLE` to your expected concurrency, not the defaults, in production.
- **Flyway:** run migrations as part of your deployment pipeline gate, not silently on every pod start in a multi-replica rollout, if you want to control exactly when schema changes apply. As configured (`baseline-on-migrate: true`), the first replica to start against a fresh database will run all pending migrations; subsequent replicas will find nothing pending. This is safe for rolling deploys of a single schema version at a time, but coordinate carefully around schema-breaking releases.
- **Health checks:** `GET /actuator/health` is exposed and used by the provided `Dockerfile`'s `HEALTHCHECK`. `show-details: always` is configured — **do not expose this endpoint publicly** without review, since detailed health output can reveal internal topology (DB status, disk space, etc.).
- **Metrics:** Prometheus scraping is enabled at the standard Actuator Prometheus endpoint (`management.endpoints.web.exposure.include: metrics, prometheus`).
- **Tracing:** OTLP trace export is enabled by default at `TRACING_SAMPLING_PROBABILITY: 1.0` (100% sampling) — reduce this in production (e.g. `0.1`) to control tracing backend cost/volume.
- **Logging:** `org.springframework.security` is set to `TRACE` in the shipped `application.yaml` — this is extremely verbose and will log security-filter internals on every request. **Override this to `INFO` or `WARN` in production** to avoid log volume and potential sensitive-data exposure in trace-level security logs.
- **Horizontal scaling:** the service is stateless (see §16) and should scale horizontally behind a load balancer with no special session affinity requirements — Spring Security is configured `STATELESS` (`SessionCreationPolicy.STATELESS`).
- **Virtual threads:** enabled (`spring.threads.virtual.enabled: true`) — be aware of this if you profile latency/CPU under load, as thread-pool-based assumptions from traditional Spring MVC deployments don't directly apply.

---

## 18. Security Considerations

- **Required secrets:** database password (`PPM_DB_PASSWORD`), and whatever secret backs your AUTH-SVC's JWT signing key (not managed by PPM — PPM only verifies against the public JWKS).
- **Network security:** this service should sit behind whatever network boundary the rest of your platform uses for internal services; nothing in this codebase implements network-level allowlisting itself.
- **TLS:** not configured in this codebase (no `server.ssl.*` properties present) — TLS termination is expected to happen at a load balancer/ingress in front of this service, consistent with typical Kubernetes deployment patterns.
- **Header validation:** `server.forward-headers-strategy: framework` is set — the service trusts `X-Forwarded-*` headers from whatever sits in front of it. **Ensure only your trusted ingress/load balancer can reach this service directly**, since a malicious direct caller could spoof forwarded headers otherwise.
- **Sensitive fields:** JWT claims (`userId`, `tenantId`, `roleId`, session/jti identifiers) flow through `CpmsAuthenticatedPrincipal` and appear in `DEBUG`/`WARN` log lines throughout the security filters (e.g. `ppm.access.granted userId=...`). Treat application logs at `DEBUG` as containing PII-adjacent identifiers.
- **Secret rotation:** JWKS-based verification supports key rotation on AUTH-SVC's side transparently (no PPM-side change needed) as long as the JWKS endpoint publishes the new key before old tokens signed with the previous key expire.
- **Least privilege:** as documented in §12, every mutation on this service currently requires the platform's highest privilege level (SUPER_ADMIN). There is no finer-grained write permission model implemented today — do not architect an integration assuming one exists.

---

## 19. Troubleshooting

| Symptom | Likely cause | Resolution |
|---|---|---|
| Service fails to start with a Flyway error | Database unreachable, wrong credentials, or `flyway_schema_history_ppm` reports a checksum mismatch against a migration file that changed after being applied | Verify `PPM_DB_URL`/`PPM_DB_USERNAME`/`PPM_DB_PASSWORD`; never hand-edit an already-applied migration file — add a new one instead |
| Service fails to start with a Hibernate schema-validation error | `ddl-auto: validate` found a mismatch between `@Entity` mappings and actual DB schema | Confirm all Flyway migrations ran; check for manual schema drift |
| Every request returns `401` | JWKS endpoint unreachable, wrong `AUTH_SVC_JWKS_URI`/`AUTH_SVC_ISSUER`, or token actually expired/revoked | Confirm `AUTH_SVC_JWKS_URI` resolves from this service's network; check the JWT's `iss`/`exp` claims |
| `403` on a GET you expected to be public | Path doesn't exactly match one of the Ant patterns in §12 (e.g. `/plans/{id}/versions/latest` matches, but a sub-path one level deeper does not) | Re-check the exact public-path list in §12; `/*` matches exactly one path segment |
| `403` on any `POST`/`PATCH`/`DELETE` to Promotion/Coupon/Referral/Campaign/Quote endpoints | Caller is not SUPER_ADMIN | See the §12 Warning — this is current, documented behavior, not a misconfiguration |
| `403` intermittently, only for tenant-scoped (non-SUPER_ADMIN) callers | Redis unreachable — `PpmAccessAuthorizationFilter` fails closed | Check Redis connectivity (`PPM_REDIS_HOST`/`PPM_REDIS_PORT`); this filter fails closed by design |
| `POST /quotes` returns `404` unexpectedly | The `planId` doesn't exist, or no `PlanPrice` row matches the given `region`+`currency`+`cycle` combination exactly (no fuzzy/fallback matching is implemented) | Confirm the plan exists and a price row exists for the exact region/currency/cycle triple you're requesting |
| `POST /quotes` returns `200` with `reason: coupon_not_found` even though you just created the coupon | Coupon codes are normalised to **uppercase** on creation; if you're calling `/quotes` with a lowercase code, the coupon lookup on the quote path does **not** re-normalise the input the same way creation does — pass the code exactly as it was created (uppercase) | Always send coupon/referral codes in the same case they were created with |
| Duplicate coupon/referral-code creation fails with `409` | Partial unique index only excludes soft-deleted rows — the active code already exists | Check for an existing active row before retrying with the same code, or soft-delete the old one first |
| `PATCH /promotions/{id}` unexpectedly removed a promotion from its campaign | `campaignId: null` in the PATCH body was interpreted as "remove from campaign," not "leave unchanged" (§11.2 Warning) | Always echo back the existing `campaignId` on unrelated PATCH calls |
| Free module/add-on grant doesn't seem to "do anything" | This is expected — PPM only computes and returns `grantedEntitlement`; it never calls a provisioning API (§1, §13) | Your service must read `grantedEntitlement` from the quote response and act on it |

---

## 20. FAQ

**Is there a Java/Kotlin client SDK?**
No. Integrate via plain HTTP calls to the documented REST endpoints.

**Can I embed `ppm-core` directly in my JVM instead of calling `ppm-svc` over HTTP?**
Not currently — `ppm-core` is not published as a versioned Maven/Gradle artifact outside this repository (see §2 Note).

**Does PPM charge the customer's card?**
No. PPM only computes prices and discounts. Billing/payment is entirely out of scope (§1).

**Does PPM enforce that a customer only gets a promotion once?**
Only if the promotion has `usageCapPerUser` set **and** you supply `customerContext` on the quote call, **and** for the referral path specifically, only after you call `/referrals/convert` to record the conversion. For the coupon path, there is no documented endpoint that writes to the per-user redemption ledger — see the §13 Note.

**What happens if I call `/referrals/convert` twice by accident (e.g. a retry)?**
The second call returns `409 REFERRAL_ALREADY_CONVERTED`. No duplicate coupon is issued. Treat this as success (§15).

**Why do all my write requests get `403` even though my token is valid and has `ppm.read`?**
Because `ppm.read` only grants read access. All mutations require SUPER_ADMIN (§12). This applies to every Promotion/Coupon/Referral/Campaign endpoint, not just legacy catalog writes.

**Can I create a promotion that both discounts the price AND grants a free module?**
No. `PromotionAction` is a single polymorphic field — one promotion has exactly one action, which is either a price reduction (`percentage`/`flat`/`fixed_price`) or an entitlement grant (`free_period`/`free_module`/`free_addon`), never both. If you need both effects, create two promotions.

**Can a coupon point at more than one promotion, or a promotion have more than one coupon?**
A `Coupon` points at exactly one `Promotion` (foreign key). A `Promotion` can be the target of many coupons — this is documented as the intended M:1 shape in the code's own comments.

**Can the same promotion belong to more than one campaign?**
No — `Promotion.campaignId` is a single nullable field (M:1 toward Campaign), by explicit design (`M:N (join table) is explicitly rejected` per the implementation's own architecture notes). If you need the same offer in two campaigns, create two promotions.

**What happens to a promotion's coupons/quotes if I delete its campaign?**
Nothing changes for them. Campaign deletion does not cascade, and `campaign_id` has no foreign-key constraint (§6, §11.5) — the promotion keeps functioning with a dangling reference.

**What currencies/regions are supported?**
Whatever free-form strings you've created `PlanPrice`/`AddOnPrice` rows for. There is no enumerated allowlist in the code — `region` and `currency` are plain strings, normalised to uppercase internally by the pricing resolver.

**Is there rate limiting?**
Not implemented in this codebase.

**Is there a sandbox/test mode?**
Not implemented in this codebase. There is no concept of test-mode data segregation.

---

## 21. Best Practices

- Always branch on the `reason` field for `/quotes` and `/referrals/quote` — do not treat HTTP `200` alone as "discount applied."
- Treat `409 REFERRAL_ALREADY_CONVERTED` from `/referrals/convert` as an idempotent success signal, not an error to surface to the end user.
- Echo the full current state of a Promotion (including `campaignId`) on every `PATCH` unless you specifically intend to change that field, given the non-uniform null semantics (§11.2).
- Normalise coupon/referral code casing on your side to uppercase before sending, to match what the catalog stores and avoid case-sensitivity surprises on lookup paths that don't re-normalise.
- Resolve prices via `POST /quotes` (which already calls the underlying pricing resolver) rather than calling `/prices/resolve` and then separately trying to reimplement discount math yourself.
- Keep a single owning service responsible for calling `/referrals/convert` exactly once per successful checkout — don't let multiple services race to record the same conversion, even though the API tolerates the retry case safely.

---

## 22. Anti-Patterns

- **Assuming a coupon/referral quote call also records usage.** It does not (§13, §15) for the coupon path — you must separately account for per-user usage if your business rules require it, since no such endpoint exists today.
- **Building admin tooling that assumes a `ppm.<something>` write permission exists for tenant roles.** It does not — writes are SUPER_ADMIN-only, full stop (§12).
- **Treating `PATCH /promotions/{id}` like a typical partial-update PATCH for every field, including `campaignId`.** It is not uniform — see §11.2.
- **Polling or retrying `POST /promotions`/`POST /coupons`/etc. on timeout without an idempotency strategy.** These are not idempotent; a retried create will produce a second record (or fail with a code-collision `409`, which is not the same as "the first one succeeded").
- **Hardcoding assumptions about which action types exist.** New action types are additive (documented in the code as the intended extension point) — always branch on `action.type`/`grantedEntitlement.type` by string comparison, not by assuming an exhaustive fixed list will never grow.
- **Calling this service without TLS termination in front of it in any networked environment.** The service itself implements no TLS (§18).

---

## 23. Upgrade Guide

- **Versioning:** `ppm-svc` and `ppm-core` are both at `0.0.1-SNAPSHOT` (`group = 'com.company'`) — there is no published semantic-versioning policy for `ppm-svc` as a deployed service; it is deployed directly from this repository, not consumed as a versioned dependency.
- **Migration/backward compatibility for the Promotion Engine:** the `PromotionAction` sealed hierarchy was deliberately restructured mid-project (introducing an intermediate `PriceAction`/`EntitlementAction` split) **without any data migration**, because the JSON discriminator (`"type"`) values and shapes of existing action types (`percentage`, `flat`) never changed — only new sibling types were added. This is the intended pattern for any *future* action type as well: add a new record, add it to the relevant `permits` clause, register it with Jackson — never change an existing `"type"` value's shape.
- **Breaking changes:** none of the REST endpoints documented in §11 have been removed or had a breaking field-shape change within the scope of this codebase's history — every phase of the Promotion Engine's development (coupons → conditions/referrals → campaigns → entitlement actions) added new optional fields and new endpoints without altering the meaning of previously-shipped fields, with the single documented exception of the `campaignId` PATCH-null semantics (§11.2), which was introduced as new behavior alongside the field itself, not as a change to pre-existing behavior.
- **Deprecations:** none. The legacy `PromoCode`/`ppm_promo_codes` system (§1, §11.6) coexists with the newer Promotion/Coupon system and is not marked deprecated in code.

---

## 24. Complete Working Example

End-to-end: create a promotion + coupon, then resolve a discounted quote for a customer.

### Prerequisites

- `ppm-svc` running locally (`./gradlew :ppm-svc:bootRun`), listening on `8106`.
- A valid SUPER_ADMIN-typed bearer token in `$TOKEN` (see §12 — required for every write below).
- An existing `Plan` with `id=$PLAN_ID` and a `PlanPrice` row for region `india`, currency `inr`, cycle `monthly`.

### Step 1 — create a promotion

```bash
curl -s -X POST http://localhost:8106/api/v1/ppm/promotions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "New Year Flat 500 Off",
    "description": "Flat ₹500 off for the first month",
    "action": { "type": "flat", "amount": 500 },
    "validFrom": "2026-01-01",
    "validUntil": "2026-01-31",
    "status": "active"
  }'
```

Response `201 Created`:

```json
{
  "success": true,
  "message": "Promotion created.",
  "data": {
    "id": "9c858901-8a57-4791-81fe-4c455b099bc9",
    "name": "New Year Flat 500 Off",
    "action": { "type": "flat", "amount": 500 },
    "status": "active",
    "source": "normal",
    "campaignId": null,
    "conditions": [],
    "usageCapPerUser": null
  }
}
```

### Step 2 — create a coupon pointing at it

```bash
curl -s -X POST http://localhost:8106/api/v1/ppm/coupons \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "NEWYEAR500",
    "promotionId": "9c858901-8a57-4791-81fe-4c455b099bc9"
  }'
```

Response `201 Created`:

```json
{
  "success": true,
  "message": "Coupon created.",
  "data": { "id": "...", "code": "NEWYEAR500", "promotionId": "9c858901-8a57-4791-81fe-4c455b099bc9", "active": true }
}
```

### Step 3 — resolve a quote using the coupon

```bash
curl -s -X POST http://localhost:8106/api/v1/ppm/quotes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"planId\": \"$PLAN_ID\",
    \"region\": \"india\",
    \"currency\": \"inr\",
    \"cycle\": \"monthly\",
    \"couponCode\": \"NEWYEAR500\"
  }"
```

Expected output, assuming the plan's base price is ₹2000/month:

```json
{
  "success": true,
  "message": "Quote resolved.",
  "data": {
    "baseAmount": 2000.0000,
    "currency": "inr",
    "discountAmount": 500.0000,
    "finalAmount": 1500.0000,
    "reason": "valid",
    "promotionId": "9c858901-8a57-4791-81fe-4c455b099bc9",
    "appliedAction": { "type": "flat", "amount": 500 },
    "conditionsSkipped": true
  }
}
```

Your checkout service now charges the customer **₹1500**, not ₹2000. This response, byte for byte in shape, is what any integrating service should expect back from this endpoint today.

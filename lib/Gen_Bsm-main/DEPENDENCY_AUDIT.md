# Repository & Dependency Audit

## Part A — Repository / Module Responsibility Audit

Six modules, each with one clear responsibility, no overlap found:

| Module | Responsibility | Depends on (project) |
|---|---|---|
| `bsm-core` | Framework-agnostic BSM domain + application services + ports. The library. | none |
| `bsm-spring-boot-starter` | Auto-configuration wiring bsm-core services/ports into a Spring Boot context. | `bsm-core` |
| `bsm-svc` | Host application: real JPA/Redis/RabbitMQ/Stripe/Razorpay/S3 adapters, REST controllers, migrations. | `bsm-core`, `libs:java-common` |
| `bsm-demo` | Second-consumer validation app: in-memory adapters, proves bsm-core/starter portability. | `bsm-spring-boot-starter`, `libs:security-spi` |
| `libs:security-spi` | Pure-Java security interface contracts (`AuthenticatedPrincipal`, `TenantScopePort`-style SPIs), zero framework deps. | none |
| `libs:java-common` | CPMS-platform-shared cross-cutting concerns (auth, messaging, security infrastructure) used only by the host, not by the library or its ports. | `libs:security-spi` |

No accidental overlap: `bsm-core` never depends on `libs:java-common` or
`bsm-svc` (enforced by `ArchitectureTest`); `bsm-demo` deliberately excludes
`libs:java-common` to keep the portability proof strong (Phase 5 design
decision, documented in `SECOND_CONSUMER_VALIDATION.md`); `libs:security-spi`
has no dependents overlap — both `bsm-demo` and `libs:java-common` depend on
it independently, as intended (a shared SPI contract, not shared
implementation).

## Part B — Dependency Audit

Scope: every declared dependency in all 6 modules, classified
required / optional / transitive-support / removable. Verified against actual
source usage (grep), not assumed from declaration alone. No dependency was
removed unless verified as genuinely unused — no speculative trimming.

## Legend

- **Required** — directly imported/used in module's own source, or a hard
  runtime necessity (JDBC driver, bytecode processor).
- **Transitive-support** — not directly imported by application code, but
  backs a feature that IS used (e.g. connection pooling behind an
  auto-configured client, tracing exporter behind a bridge). Removing it
  would silently degrade or break a used feature at runtime.
- **Removable** — no source usage found. Candidate for deletion.

---

## 1. bsm-core

Framework-agnostic domain + application + port library. Must stay free of
any host/framework-specific dependency (enforced by `ArchitectureTest`).

| Dependency | Scope | Classification | Notes |
|---|---|---|---|
| `spring-context` | api | Required | 27 files use `org.springframework.stereotype`/`context` (`@Service`, `@Component` on application service impls). |
| `spring-tx` | api | Required | 18 files use `@Transactional` on application service methods. |
| `slf4j-api` | api | Transitive-support | No direct `org.slf4j` import, but 10 files use Lombok's `@Slf4j` (`lombok.extern.slf4j.Slf4j`), which generates a field typed `org.slf4j.Logger` — removing this dependency breaks compilation. Verified by trial removal + rebuild. |
| `jackson-annotations` | api | **Removable** | Zero usages found (`grep -rl com.fasterxml.jackson.annotation` → 0 hits). No domain model carries Jackson annotations. Verified by trial removal + successful rebuild. |
| `lombok` | compileOnly + annotationProcessor | Required | 43 files use Lombok annotations (`@Builder`, `@Getter`, etc. — domain models like `DunningAttempt`, `Subscription`). |
| `junit-bom` / `junit-jupiter` | test | Required | Test framework. |
| `assertj-core` | test | Required | Assertion library, used throughout test suite. |
| `mockito-core` / `mockito-junit-jupiter` | test | Required | Mocking framework, used throughout test suite. |
| `archunit-junit5` | test | Required | Powers `ArchitectureTest`, the boundary-enforcement suite. |

**Action taken:** removed `jackson-annotations` from `bsm-core/build.gradle`
— verified zero-usage by trial removal, `bsm-core` still compiles clean.
`slf4j-api` was also trial-removed but caused 17 compile errors (Lombok's
`@Slf4j` needs `org.slf4j.Logger` on the classpath even though no source
file imports `org.slf4j` directly) — reverted, kept as required.

---

## 2. bsm-spring-boot-starter

Auto-configuration glue layer. Depends on bsm-core (api) plus
Spring Boot's autoconfigure machinery.

| Dependency | Scope | Classification | Notes |
|---|---|---|---|
| `bsm-core` (project) | api | Required | The starter's entire purpose is wiring bsm-core services/ports. |
| `spring-boot-autoconfigure` | implementation | Required | All 9 `@AutoConfiguration` classes depend on it (`@ConditionalOnBean`, `@ConditionalOnProperty`, `@AutoConfigureAfter`). |
| `spring-boot-autoconfigure-processor` | annotationProcessor | Required | Generates `META-INF/spring-autoconfigure-metadata.properties`, needed for conditional ordering/deferral. |
| `spring-boot-configuration-processor` | annotationProcessor | Required | Generates metadata for `@ConfigurationProperties` classes in the starter's `properties` package. |
| `lombok` | compileOnly + annotationProcessor | **Removable** | Zero usages found in `bsm-spring-boot-starter/src/main/java`. Declared but unused. |
| `spring-boot-starter-test` | test | Required | Backs `ApplicationContextRunner` used across all 15 `BsmAutoConfigurationTest` scenarios. |

**Action taken:** removed `lombok`/`annotationProcessor` from
`bsm-spring-boot-starter/build.gradle` — verified zero-usage by trial
removal, module still compiles clean (including test sources).

---

## 3. bsm-demo

Second-consumer validation application. Depends only on the starter and
security-spi, deliberately excluding `libs:java-common` (see Phase 5 design
note in `SECOND_CONSUMER_VALIDATION.md`) to keep the portability proof strong.

| Dependency | Scope | Classification | Notes |
|---|---|---|---|
| `bsm-spring-boot-starter` (project) | implementation | Required | The library under test — brings bsm-core transitively. |
| `libs:security-spi` (project) | implementation | Required | `TenantScopePort`/`AuthenticatedPrincipal`-style SPI consumed by in-memory adapters. |
| `spring-boot-starter` | implementation | Required | Base Spring Boot application bootstrap (`@SpringBootApplication`, embedded runtime). |
| `lombok` | compileOnly + annotationProcessor | Required | Used in demo's in-memory adapter classes. |
| `spring-boot-starter-test` | test | Required | Backs the demo's end-to-end integration test. |
| `junit-platform-launcher` | testRuntimeOnly | Required | Needed to run JUnit 5 tests under the Gradle test task. |

No removable dependencies in bsm-demo — every declaration is exercised.

---

## 4. libs/security-spi

Pure Java interface module, zero production dependencies (by design).

| Dependency | Scope | Classification | Notes |
|---|---|---|---|
| `junit-bom` / `junit-jupiter` | test | Required | Test framework for any interface-contract tests. |

No production dependencies exist — nothing to audit for removal.

---

## 5. libs/java-common

CPMS platform-shared library (auth, messaging, security). Used by `bsm-svc`
(host), not by `bsm-core` or `bsm-demo`.

| Dependency | Scope | Classification | Notes |
|---|---|---|---|
| `security-spi` (project) | api | Required | `java-common`'s security package implements/extends security-spi contracts. |
| `spring-boot-starter-web` | api | Required | REST/validation annotations used in `errors` package (`@ControllerAdvice`, exception handlers). |
| `spring-boot-starter-security` | api | Required | Backs `CpmsSecurityAutoConfiguration`. |
| `spring-boot-starter-validation` | api | Required | Bean validation annotations used across shared DTOs. |
| `aspectjweaver` | api | Required | Backs `PermissionCheckAspect`. |
| `jjwt-api` | api | Required | `CpmsJwtAuthConverter` uses JWT parsing/building. |
| `jjwt-impl` / `jjwt-jackson` | runtimeOnly | Required | JJWT implementation providers, needed at runtime alongside `jjwt-api`. |
| `spring-boot-starter-amqp` | api | Required | Backs `AuditMessagingTopology`. |
| `spring-boot-starter-oauth2-resource-server` | api | Required | Backs `CpmsSecurityAutoConfiguration`'s JWKS resource-server beans. |
| `spring-boot-starter-data-redis` | api | Required | Backs `CpmsSecurityAutoConfiguration`'s Redis-backed RBAC cache beans. |
| `lombok` | compileOnly + annotationProcessor | Required | Used throughout java-common's model/config classes. |
| `spring-boot-starter-test` | test | Required | Test framework for java-common's own test suite. |

No removable dependencies — all previously verified in Phase 2.5/2.6 as
genuinely used. Re-confirmed here, no change.

---

## 6. bsm-svc

Host Spring Boot application. Real JPA/Redis/RabbitMQ/Stripe/Razorpay/S3
adapters implementing bsm-core's ports.

| Dependency | Scope | Classification | Notes |
|---|---|---|---|
| `bsm-core` (project) | implementation | Required | The library being hosted. |
| `libs:java-common` (project) | implementation | Required | Shared platform auth/messaging/security. |
| `spring-boot-starter-webmvc` | implementation | Required | REST controllers. |
| `spring-boot-starter-validation` | implementation | Required | Request validation on controllers. |
| `spring-boot-starter-actuator` | implementation | Required | Health/metrics endpoints. |
| `micrometer-registry-prometheus` | runtimeOnly | Transitive-support | No direct source import expected (auto-registered via actuator) — backs the Prometheus scrape endpoint enabled by actuator. Used indirectly. |
| `spring-boot-starter-data-jpa` | implementation | Required | All repository adapters (`*RepositoryAdapter` implementing bsm-core ports). |
| `postgresql` (driver) | runtimeOnly | Required | JDBC driver for the JPA datasource — never imported directly by design (loaded via `DriverManager`/Hikari), required at runtime. |
| `spring-boot-starter-data-redis` | implementation | Required | Caching/session adapters. |
| `commons-pool2` | implementation | Transitive-support | No direct source import (0 hits) — required at runtime by Lettuce/Jedis connection pooling behind `spring-boot-starter-data-redis` when pooled connections are configured. Do not remove: Redis pooling silently falls back to non-pooled or fails depending on config if absent. |
| `spring-boot-starter-amqp` | implementation | Required | RabbitMQ event publishing/consuming adapters. |
| `micrometer-tracing-bridge-otel` | implementation | Transitive-support | No direct source import (0 hits) — bridges Micrometer's tracing API to OpenTelemetry; required for the OTLP exporter below to actually receive spans. |
| `opentelemetry-exporter-otlp` | implementation | Transitive-support | No direct source import (0 hits) — auto-configured exporter consumed by the tracing bridge above. Together these two form one feature (distributed tracing export); neither is removable independently. |
| `mapstruct` | implementation | Required | 17 files use MapStruct-generated mappers (DTO↔domain mapping in `bsm-svc` controllers/mappers). |
| `springdoc-openapi-starter-webmvc-ui` | implementation | Required | OpenAPI/Swagger UI generation for the host's REST API. |
| `spring-boot-starter-flyway` | implementation | Required | Migration bootstrap integration. |
| `flyway-core` / `flyway-database-postgresql` | implementation | Required | Schema migrations (`src/main/resources/db/migration`). |
| `openpdf` | implementation | Required | 1 file uses it (invoice PDF generation). |
| `software.amazon.awssdk:s3` / `auth` (v2) | implementation | Required | 2 files use AWS SDK v2 S3 client (storage adapter). |
| `com.amazonaws:aws-java-sdk-s3` (v1) | implementation | Required | 2 files use AWS SDK v1 S3 client. **Overlap flagged below.** |
| `resilience4j-spring-boot4` | implementation | Required | 5 files use resilience4j annotations/APIs (circuit breaker/retry around payment gateway calls). |
| `jackson-datatype-jsr310` | implementation | Required | Java 8 time module registration for JSON serialization of `Instant`/`LocalDate` fields on REST DTOs. |
| `stripe-java` | implementation | Required | Stripe payment gateway adapter. |
| `razorpay-java` | implementation | Required | Razorpay payment gateway adapter. |
| `lombok` | compileOnly + annotationProcessor | Required | Used throughout bsm-svc. |
| `mapstruct-processor` | annotationProcessor | Required | Generates MapStruct mapper implementations. |
| `spring-boot-configuration-processor` | annotationProcessor | Required | Generates metadata for `@ConfigurationProperties` classes (dunning/reconciliation/trial policies, etc.). |
| `spring-boot-starter-test` / `-webmvc-test` | test | Required | Test framework + MockMvc. |
| `spring-rabbit-test` | test | Required | RabbitMQ test harness. |
| `testcontainers` / `postgresql` / `junit-jupiter` (testcontainers) | test | Required | Integration tests against real Postgres/Rabbit containers. |
| `junit-platform-launcher` | testRuntimeOnly | Required | Runs JUnit 5 tests under Gradle. |

### Flagged overlap: two competing AWS S3 SDKs

`bsm-svc` declares **both** `software.amazon.awssdk:s3` (AWS SDK v2) and
`com.amazonaws:aws-java-sdk-s3` (AWS SDK v1), each with 2 real source
usages. This is a genuine dependency-hygiene finding — two SDK
major-versions for the same capability increases classpath size and
maintenance surface. **Not removed in this pass**: the user's Phase 7
instructions explicitly forbid moving business logic or making
architecture changes beyond what blocks publication, and consolidating two
S3 client implementations touches adapter source code (a design decision
about *which* SDK the storage adapter should standardize on), not a
dependency-declaration change. Recorded as a finding in
`TECHNICAL_DEBT_REGISTER.md` for future resolution, not silently fixed here.

---

## Summary of removals applied

| Module | Dependency | Reason | Verified |
|---|---|---|---|
| bsm-core | `jackson-annotations` | 0 source usages | Trial removal + rebuild: `BUILD SUCCESSFUL` |
| bsm-spring-boot-starter | `lombok` (+ annotationProcessor) | 0 source usages | Trial removal + rebuild (main + test): `BUILD SUCCESSFUL` |

`slf4j-api` in bsm-core was investigated as a candidate but is **required**:
no file imports `org.slf4j` directly, but 10 files use Lombok's `@Slf4j`,
which generates a `Logger` field of type `org.slf4j.Logger` — removing the
dependency broke compilation with 17 errors. Kept as-is.

No other module has removable dependencies. The AWS SDK v1/v2 overlap in
`bsm-svc` is flagged as technical debt, not removed, since both SDKs are
actively used and consolidating them is an adapter-code decision outside
this audit's scope.

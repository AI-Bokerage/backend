# Changelog

All notable changes to `bsm-core` and `bsm-spring-boot-starter` are
documented here, following [Keep a Changelog](https://keepachangelog.com/)
conventions and [Semantic Versioning](VERSIONING.md).

> **Note on version numbering**: this changelog documents the `1.0.0`
> milestone as the culmination of the extraction work described below.
> The Gradle `version` fields in `bsm-core`/`bsm-spring-boot-starter` remain
> `0.0.1-SNAPSHOT` until the full `RELEASE_PROCESS.md` checklist — including
> the outside-monorepo external-consumer verification — has actually run
> and passed. Treat this entry as the target release notes for that first
> real release, not as confirmation that `1.0.0` has been tagged yet.

## [1.0.0] — Library extraction complete

The culmination of a seven-phase effort to extract a Billing & Subscription
Management system out of a monolithic host application (`bsm-svc`) into a
standalone, framework-agnostic, independently publishable Java library
(`bsm-core`) with an optional Spring Boot starter.

### Added

- **`bsm-core`**: framework-agnostic domain models, application services,
  and 40 port interfaces (plus `PaymentGatewayResolver`) forming the
  library's entire public surface. Zero dependency on Spring MVC, Spring
  Security, JPA/Hibernate, AMQP/Kafka clients, Resilience4j, or any
  payment-provider SDK — enforced at build time by `ArchitectureTest`
  (ArchUnit).
- **`bsm-spring-boot-starter`**: ten `@AutoConfiguration` classes wiring
  bsm-core's application services to consumer-supplied port beans, using an
  anchor-port strategy per aggregate (tenant-billing, payment, invoice,
  subscription, invoice-renewal, dunning, tenant-onboarding). A custom
  `BsmPortAvailabilityValidator` fails startup fast with an actionable
  message when a consumer supplies some but not all of an aggregate's
  required ports — a gap `@ConditionalOnBean` alone cannot express.
- **`bsm-demo`**: an independent second-consumer Spring Boot application
  with 24 in-memory port adapters, proving `bsm-core`/
  `bsm-spring-boot-starter` are genuinely reusable outside the original
  host — validated both via Gradle project dependencies and via published
  `publishToMavenLocal` artifacts.
- **`libs/security-spi`**: a pure-Java security interface module with zero
  production dependencies, extracted so both the library ecosystem and the
  original host's shared platform code (`libs/java-common`) can depend on
  the same contracts without either depending on the other's
  implementation.
- Domain command/result types (`InitiateCheckoutCommand`/`CheckoutResult`,
  `PurchaseAddOnCommand`/`AddOnPurchaseResult`,
  `PreviewPlanChangeCommand`/`ApplyPlanChangeCommand`, and others) replacing
  what had been direct REST-DTO parameters on application service
  interfaces.
- `ConcurrentUpdateException`, a domain-level exception that lets
  `bsm-core` express optimistic-locking conflicts without importing
  `jakarta.persistence` — JPA's `OptimisticLockingFailureException`/
  `OptimisticLockException` are translated into it at the adapter boundary.
- Policy value objects (`DunningPolicy`, `TrialPolicy`,
  `ReconciliationPolicy`) bridging host `@ConfigurationProperties` into
  plain records `bsm-core` can depend on without any Spring
  `@ConfigurationProperties`/`@Value` machinery inside the library itself.
- Full documentation set: `GETTING_STARTED.md`, `STARTER_GUIDE.md`,
  `API_STABILITY.md`, `INTEGRATION_GUIDE.md`, `ARCHITECTURE_CERTIFICATION.md`,
  `SECOND_CONSUMER_VALIDATION.md`, ten worked examples under
  `docs/examples/`, plus this Phase 7 governance set (`PUBLIC_API.md`,
  `PORT_REFERENCE.md`, `CONFIGURATION_REFERENCE.md`, `DEPENDENCY_AUDIT.md`,
  `DEVELOPER_GUIDE.md`, `COMPATIBILITY.md`, `VERSIONING.md`,
  `RELEASE_PROCESS.md`, `TECHNICAL_DEBT_REGISTER.md`, `ROADMAP.md`,
  `SECURITY.md`, `CONTRIBUTING.md`, `LICENSE`).

### Changed

- `bsm-demo` migrated from manual `@Bean` wiring (a hand-written
  `BsmCoreConfig`) to consuming `bsm-spring-boot-starter`'s
  auto-configuration exclusively — zero `@Bean` definitions remain in
  `bsm-demo`.
- `TenantCreatedConsumer`'s business logic (previously living directly in a
  `bsm-svc` message consumer) extracted into `TenantOnboardingService` in
  `bsm-core`, matching the established port/adapter pattern.

### Removed

- Two zero-usage dependencies: `jackson-annotations` from `bsm-core`, and
  `lombok`/`lombok-annotationProcessor` from `bsm-spring-boot-starter` — see
  `DEPENDENCY_AUDIT.md` for verification method (trial removal + full
  rebuild).
- CPMS-specific coupling (`CpmsUserType`, `CpmsMessagingConstants`) replaced
  with capability-based and generic messaging abstractions during an
  interim Phase 2.7 cleanup.

### Known limitations at 1.0.0

See `TECHNICAL_DEBT_REGISTER.md` for full detail. Summary: `BillingDashboardServiceImpl`
uses raw `JdbcTemplate` instead of a proper port (lives in `bsm-svc`, not
`bsm-core`); `CommercialEngineServiceImpl` depends on
`io.cpms.common.plan.PpmTierRegistry` and also lives in `bsm-svc`;
`SubscriptionAddOnService`/`SubscriptionChangeService` are not yet
auto-configured by the starter (manual wiring required, documented in
`docs/examples/`); `BsmAuthorizationPort` has no implementation anywhere in
the codebase yet; `bsm-svc` carries both AWS SDK v1 and v2 for S3 access.

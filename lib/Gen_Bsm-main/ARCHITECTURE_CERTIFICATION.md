# Gen_BSM — Architecture Certification

Certifies the module boundary established across Phases 1–4. This is the contract future
contributors must follow to keep `bsm-core` a portable Billing & Subscription library and
`bsm-svc` a thin, replaceable host.

## Module dependency graph

```
                 bsm-svc  (host, Spring Boot application)
                    │
        ┌───────────┼───────────────┐
        │           │               │
   bsm-core   libs:java-common      │
        │           │               │
        │      libs:security-spi ───┘
        │           │
        └───────────┘  (bsm-core has zero dependency on java-common or security-spi)
```

- `bsm-core` — zero project dependencies. Compiles standalone.
- `libs:security-spi` — zero dependencies. Pure Java interfaces.
- `libs:java-common` — depends on `libs:security-spi`.
- `bsm-svc` — depends on `bsm-core` and `libs:java-common`.

No cycles. `bsm-core` cannot see `bsm-svc`, `java-common`, or `security-spi` at compile time.

## Module ownership

| Module | Owns | Never contains |
|---|---|---|
| `bsm-core` | Billing/subscription domain models, application services (use cases), ports, domain events, exceptions, value objects/policies | Spring MVC, Spring Security, JPA/Hibernate, AMQP/Kafka, Resilience4j, Stripe/Razorpay SDKs, `io.cpms.common.*`, REST/DTO/config/scheduler/adapter packages |
| `bsm-svc` | REST controllers/DTOs/mappers, JPA entities/repositories, RabbitMQ config/consumers/publishers, Stripe/Razorpay/PPM/USG/ADM/S3 adapters, security filters, schedulers, Spring configuration | Billing business rules, use-case orchestration (beyond thin delegation), domain state-transition logic |
| `libs:java-common` | Generic platform primitives: exception scaffolding, JWT/RBAC security infra, two genuinely cross-service messaging contracts (`AuditMessagingTopology`, `CpmsMessagingConstants`), `PpmTierRegistry` (shared ppm-svc/bsm-svc tier ranking) | Billing logic, audit implementation, single-service event catalogs |
| `libs:security-spi` | `AuthenticatedPrincipal` — the one platform-neutral authentication contract | Any framework or platform-specific type |

## Ports and their adapters (40 ports, 37 adapter classes)

Repository ports (persistence adapters, `bsm-svc/infrastructure/persistence/adapter/`): `SubscriptionRepositoryPort`, `SubscriptionHistoryRepositoryPort`, `SubscriptionEventRepositoryPort`, `SubscriptionScheduleRepositoryPort`, `SubscriptionLimitSnapshotRepositoryPort`, `SubscriptionAddOnRepositoryPort`, `PaymentRepositoryPort`, `PaymentMethodRepositoryPort`, `RefundRequestRepositoryPort`, `PlatformInvoiceRepositoryPort`, `InvoiceLineItemRepositoryPort`, `CreditNoteRepositoryPort`, `BillingLedgerRepositoryPort`, `DunningAttemptRepositoryPort`, `MigrationPlanRepositoryPort`, `PpmChangeSnapshotRepositoryPort`, `ProrationPreviewRepositoryPort`, `TenantBillingProfileRepositoryPort`, `TenantTrialRecordRepositoryPort`, `WebhookEventRepositoryPort` — each implemented by exactly one `*RepositoryAdapter`, JPA-backed, translating persistence exceptions (`ConcurrentUpdateException` for optimistic-lock conflicts).

External capability ports: `PaymentGatewayPort` → `StripePaymentAdapter` / `RazorpayPaymentAdapter`; `PpmPricingService`/`PpmPromoService`/`PpmVersionService`/`PpmAddOnPricingService`/`PlanVersionMetaPort`/`PlanLimitsPort`/`DefaultTrialPlanPort` → PPM HTTP clients (`infrastructure/client/ppm/*`); `UserUsagePort`/`ProjectUsagePort`/`StorageUsagePort` → ADM client or stub adapters; `FeatureEntitlementPort` → `PlanVersionFeatureEntitlementAdapter`; `UsageLimitsSeedingPort` → `UsgLimitsSeedingService`.

Security/messaging ports: `TenantScopePort`/`BsmAuthorizationPort` → `BsmTenantScopeEnforcer`; `EventPublisherPort` → `BsmAuditEventPublisher`; `SubscriptionEventPublisherPort` → `BsmSubscriptionEventPublisher`; `InvoiceEventPublisher`/`DunningEventPublisher` → `RabbitInvoiceEventPublisher`/`RabbitDunningEventPublisher`; `AddOnEventPublisherPort` → `BsmAddOnEventPublisher`.

## Rules for future contributors

1. A new billing rule, calculation, or state transition goes in `bsm-core`. If it needs an external capability (HTTP call, DB, broker), define a port in `bsm-core/domain/port` first.
2. A new integration (payment provider, catalog service, storage backend) goes in `bsm-svc/infrastructure` as an adapter implementing an existing or new port — never inline in a controller, consumer, or scheduler.
3. Consumers and schedulers stay thin: deserialize/trigger, delegate to a `bsm-core` application service, log. If a consumer method grows past a few lines of orchestration, that orchestration belongs in a service.
4. Never let a `bsm-core` interface take or return an `api.dto.*` type. If a REST payload and a use case need different shapes, that's what mapping in the controller/mapper is for.
5. Run `./gradlew :bsm-core:test` after any change — the `ArchitectureTest` tripwire fails the build the moment a forbidden import (Spring MVC/Security/JPA/Hibernate/AMQP/Kafka/Resilience4j/Stripe/Razorpay, or a host-layer package name) lands in `bsm-core`.
6. Don't add a dependency to `libs:java-common` or `libs:security-spi` without checking it's genuinely used by more than one bounded context — see Phase 2.5/2.6's removal of `audit-core` and 23 cross-service messaging classes for what that looks like in practice.

## Second consumer validation (Phase 5)

`bsm-core`'s portability claim is no longer theoretical. `bsm-demo` — an independent Spring
Boot application in package `com.example.bsmdemo` — consumes `bsm-core` and
`libs:security-spi` only, with zero dependency on `bsm-svc`, wires 23 of 40 ports with simple
in-memory adapters, and runs the full tenant-onboarding → subscription → invoice → payment →
renewal → dunning → recovery lifecycle through `bsm-core`'s real application services. Validated
twice: once via Gradle `project(...)` dependencies, once via artifacts published to
`mavenLocal()` and consumed by Maven coordinates. Full details, the port coverage table, and the
one real API-usability finding (payment intents require a gateway-customer record registered via
`PaymentMethodService` first) are in `SECOND_CONSUMER_VALIDATION.md`.

## Spring Boot starter (Phase 6)

`bsm-spring-boot-starter` depends on `bsm-core` and adds one Gradle dependency for any Spring
Boot consumer. Nine `@AutoConfiguration` classes, one per aggregate: `BsmPolicyAutoConfiguration`,
`BsmSupportAutoConfiguration`, `TenantBillingProfileAutoConfiguration`, `PaymentAutoConfiguration`,
`InvoiceAutoConfiguration`, `SubscriptionAutoConfiguration`, `InvoiceRenewalAutoConfiguration`,
`DunningAutoConfiguration`, `TenantOnboardingAutoConfiguration`, plus
`BsmPortAvailabilityValidatorAutoConfiguration`.

Each aggregate registers its services only when every required port bean is present
(`@ConditionalOnBean`), never partially. A `SmartInitializingSingleton`-based validator
(`BsmPortAvailabilityValidator`) catches the one case `@ConditionalOnBean` can't express — an
anchor port present but the rest of the aggregate's ports missing — and fails startup with an
actionable, aggregate-scoped error message rather than silently half-wiring anything. Full detail,
property reference, and the port-per-aggregate table: `STARTER_GUIDE.md`.

`bsm-svc` was verified to build completely unmodified after the starter's introduction —
`./gradlew clean build` across all 6 modules (`bsm-core`, `bsm-svc`, `libs:java-common`,
`libs:security-spi`, `bsm-demo`, `bsm-spring-boot-starter`), 549 tests, 0 failures.

## Known, documented deviations (not debt to silently "fix")

- `CommercialEngineServiceImpl` stays in `bsm-svc` — depends on `io.cpms.common.plan.PpmTierRegistry`, a real ppm-svc/bsm-svc shared contract, not billing-owned.
- `WebhookProcessingServiceImpl`, `PaymentGatewayResolverImpl`, `Ppm{Pricing,AddOnPricing,Promo,Version}ServiceImpl` stay in `bsm-svc` — Stripe SDK / Resilience4j-wrapped clients, forbidden in `bsm-core` by the ArchUnit rule.
- `BillingDashboardServiceImpl` stays in `bsm-svc` — queries via raw `JdbcTemplate` SQL rather than a repository port. Needs a `BillingSummaryQueryPort` design before it can move; not attempted (redesign, not migration).

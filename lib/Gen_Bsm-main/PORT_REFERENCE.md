# Port Reference

One entry per `bsm-core` extension point: all 40 interfaces in
`bsm-core/src/main/java/com/company/bsmsvc/domain/port/` plus `PaymentGatewayResolver`
(`bsm-core/src/main/java/com/company/bsmsvc/application/service/PaymentGatewayResolver.java`) —
a genuine strategy-lookup extension point that happens to live in `application/service` rather
than `domain/port`, called out explicitly in `API_STABILITY.md`'s SPI tier. **41 ports total.**

Every port is `Public API` tier / `SPI` per `API_STABILITY.md` — consumers implement these,
never extend a concrete class. Adding a method to any of these interfaces is a breaking change
for every existing implementer.

**Default thread-safety expectation** (stated once here, not repeated per port unless a port says
more): `bsm-core`'s application services are plain Java objects with no internal locking of their
own. If your host framework allows concurrent requests (any real Spring Boot app), your port
implementation **must be thread-safe or stateless** — no unguarded mutable instance fields,
consistent with idiomatic Spring singleton-bean design (`@Repository`/`@Component`/`@Service`
beans backed by a thread-safe client or connection pool).

**Default lifecycle** (also stated once): a singleton Spring bean, constructed once at
application startup and injected into the aggregate's application service constructor. None of
these ports are meant to be instantiated per-request or per-call.

**Required vs optional** is per the aggregate wiring table in `STARTER_GUIDE.md` — an aggregate's
services are only auto-configured when **every** port in its required set is present as a bean;
supplying only some of them fails startup fast (`BsmPortAvailabilityValidator`). A port that
doesn't appear in any aggregate's required set is "optional" in the sense that no auto-configured
service depends on it — it still needs an implementation if *your own* code (or a
not-yet-starter-wired service like `SubscriptionAddOnService`) calls it.

---

## tenant-billing aggregate

Anchor: `TenantBillingProfileRepositoryPort`. Registers `TenantBillingProfileService`.

### `TenantBillingProfileRepositoryPort`
- **Purpose**: Persistence/query port for a tenant's billing profile (provider, currency, external customer id). No javadoc on the interface itself — inferred from method set.
- **Methods**: `TenantBillingProfile save(TenantBillingProfile profile)`; `Optional<TenantBillingProfile> findByTenantId(UUID tenantId)`
- **Required by**: tenant-billing (anchor), payment, invoice, subscription, invoice-renewal, dunning, tenant-onboarding (all depend on `TenantBillingProfileService`, which itself depends on this port)
- **bsm-svc adapter**: `TenantBillingProfileRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/TenantBillingProfileRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemoryTenantBillingProfileRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryTenantBillingProfileRepository.java`
- **Thread safety**: default (stateless/thread-safe repository semantics)
- **Lifecycle**: default (singleton bean)

### `TenantScopePort`
- **Purpose**: "Tenant isolation guard for use-case methods that receive a claimed tenantId (request body, query parameter, or DTO field) rather than relying on a filter-level convention. No framework/security dependency — the host wires this to its actual authentication mechanism (JWT claims, header-based, etc.)."
- **Methods**: `void assertTenantAccess(UUID requestedTenantId)` — throws (unchecked) when the current caller is not permitted to act on the tenant; `UUID resolveEffectiveTenantId(UUID requestedTenantId)` — scopes non-privileged callers to their own tenant for list/search filters even when the filter is null.
- **Required by**: tenant-billing, payment, invoice, subscription, dunning
- **bsm-svc adapter**: `BsmTenantScopeEnforcer` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/security/BsmTenantScopeEnforcer.java`
- **bsm-demo adapter (example)**: `DemoTenantScopeAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/DemoTenantScopeAdapter.java`
- **Thread safety**: default
- **Lifecycle**: default

---

## payment aggregate

Anchor: `PaymentGatewayResolver`. Registers `PaymentMethodService`, `PaymentService`.

### `PaymentGatewayResolver`
- **Purpose**: (inferred, no javadoc) Resolves the correct `PaymentGatewayPort` implementation for a given `PaymentProvider` — the strategy lookup that lets `bsm-core` stay agnostic to how many gateways a host wires up.
- **Package**: `com.company.bsmsvc.application.service` (not `domain.port` — flagged explicitly per task scope, but functionally a port).
- **Methods**: `PaymentGatewayPort resolve(PaymentProvider provider)`
- **Required by**: payment (anchor), subscription, dunning
- **bsm-svc adapter**: `PaymentGatewayResolverImpl` — `bsm-svc/src/main/java/com/company/bsmsvc/application/impl/PaymentGatewayResolverImpl.java` (resolves to `StripePaymentAdapter` or `RazorpayPaymentAdapter` by provider)
- **bsm-demo adapter (example)**: `DemoPaymentGatewayResolver` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/DemoPaymentGatewayResolver.java` (resolves unconditionally to the single in-memory gateway — see `INTEGRATION_GUIDE.md`)
- **Thread safety**: default
- **Lifecycle**: default

### `PaymentGatewayPort`
- **Purpose**: (inferred, no javadoc) Abstraction over an external payment gateway — customer/payment-method management, provider-side subscription mirroring, checkout sessions, payment intents, refunds.
- **Methods**: `CustomerResult createCustomer(CreateCustomerCommand)`; `void attachPaymentMethod(AttachPaymentMethodCommand)`; `void detachPaymentMethod(DetachPaymentMethodCommand)`; `SubscriptionResult createSubscription(CreateSubscriptionCommand)`; `SubscriptionResult updateSubscription(UpdateSubscriptionCommand)`; `void cancelSubscription(CancelSubscriptionCommand)`; `CheckoutSessionResult createCheckoutSession(CreateCheckoutSessionCommand)`; `ListPaymentMethodsResult listPaymentMethods(ListPaymentMethodsCommand)`; `PaymentIntentResult createPaymentIntent(CreatePaymentIntentCommand)`; `PaymentIntentResult retryPayment(RetryPaymentCommand)` — off-session retry for dunning, confirms immediately with a specific payment method; `PaymentStatusResult retrievePaymentStatus(String externalId)` — used by reconciliation to refresh stale `PENDING` payments; `RefundResult refund(RefundCommand)`
- **Required by**: not directly required by any aggregate's port set (resolved indirectly through `PaymentGatewayResolver`, which *is* required by payment/subscription/dunning)
- **bsm-svc adapters**: `StripePaymentAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/gateway/StripePaymentAdapter.java`; `RazorpayPaymentAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/gateway/RazorpayPaymentAdapter.java`
- **bsm-demo adapter (example)**: `DemoPaymentGatewayAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/DemoPaymentGatewayAdapter.java`
- **Thread safety**: default (implementations typically wrap a provider SDK client, which must itself be safe for concurrent use)
- **Lifecycle**: default

### `PaymentMethodRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for saved payment methods.
- **Methods**: `PaymentMethod save(PaymentMethod)`; `Optional<PaymentMethod> findById(UUID)`; `List<PaymentMethod> findByTenantId(UUID)`; `Optional<PaymentMethod> findDefaultByTenantId(UUID)`; `boolean existsByTenantIdAndExternalPaymentMethodId(UUID, String)`; `void unsetDefaultForTenant(UUID)`
- **Required by**: payment (anchor set), subscription, dunning
- **bsm-svc adapter**: `PaymentMethodRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/PaymentMethodRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemoryPaymentMethodRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryPaymentMethodRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

### `PaymentRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for payment records.
- **Methods**: `Payment save(Payment)`; `Optional<Payment> findById(UUID)`; `Optional<Payment> findByExternalPaymentId(String)`; `Optional<Payment> findByExternalChargeId(String)`; `List<Payment> findByInvoiceId(UUID)`; `List<Payment> findPendingOlderThan(Instant threshold)`
- **Required by**: payment (anchor set), dunning
- **bsm-svc adapter**: `PaymentRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/PaymentRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemoryPaymentRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryPaymentRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

### `EventPublisherPort`
- **Purpose**: "Publishes a billing business event that occurred as part of a use case (invoice paid, refund completed, dunning started, etc.). The library only knows that the event happened — it never knows what happens after publication (outbox, RabbitMQ, Kafka, audit consumption, monitoring all remain host decisions)."
- **Methods**: `void publish(String eventType, UUID tenantId, String aggregateType, UUID aggregateId, UUID actorId, Map<String, Object> data)`
- **Required by**: payment (anchor set), invoice, dunning
- **bsm-svc adapter**: `BsmAuditEventPublisher` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/outbox/BsmAuditEventPublisher.java`
- **bsm-demo adapter (example)**: `InMemoryEventPublisherAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryEventPublisherAdapter.java`
- **Thread safety**: default
- **Lifecycle**: default

---

## invoice aggregate

Anchor: `PlatformInvoiceRepositoryPort`. Registers `InvoiceGenerationService`, `InvoiceService`.

### `PlatformInvoiceRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for platform invoices, including duplicate-period guards used by renewal.
- **Methods**: `PlatformInvoice save(PlatformInvoice)`; `Optional<PlatformInvoice> findById(UUID)`; `Optional<PlatformInvoice> findByInvoiceNumber(String)`; `boolean existsBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID, Instant, Instant)`; `boolean existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID, Instant, Instant)` — true only for `RECURRING` invoices with source `MANUAL` or `SUBSCRIPTION_RENEWAL`, non-recurring sources excluded; `List<PlatformInvoice> findByTenantId(UUID)`; `List<PlatformInvoice> findBySubscriptionId(UUID)`; `PageResult<PlatformInvoice> findInvoices(InvoiceFilter, int page, int size, String sortBy, String sortDirection)`
- **Required by**: invoice (anchor), invoice-renewal, dunning
- **bsm-svc adapter**: `PlatformInvoiceRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/PlatformInvoiceRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemoryInvoiceRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryInvoiceRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

### `InvoiceEventPublisher`
- **Purpose**: (inferred, no javadoc) Publishes invoice-created events (specifically to trigger async PDF generation downstream).
- **Methods**: `void publishInvoiceCreated(InvoiceCreatedMessage message)`
- **Required by**: invoice (anchor set)
- **bsm-svc adapter**: `RabbitInvoiceEventPublisher` — `bsm-svc/src/main/java/com/company/bsmsvc/messaging/RabbitInvoiceEventPublisher.java`
- **bsm-demo adapter (example)**: `InMemoryInvoiceEventPublisherAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryInvoiceEventPublisherAdapter.java`
- **Thread safety**: default
- **Lifecycle**: default

### `InvoiceLineItemRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for individual invoice line items.
- **Methods**: `InvoiceLineItem save(InvoiceLineItem)`; `Optional<InvoiceLineItem> findById(UUID)`; `List<InvoiceLineItem> findByInvoiceId(UUID)`; `void deleteById(UUID)`
- **Required by**: not listed in any `STARTER_GUIDE.md` aggregate's required set — optional
- **bsm-svc adapter**: `InvoiceLineItemRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/InvoiceLineItemRepositoryAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented per `SECOND_CONSUMER_VALIDATION.md`
- **Thread safety**: default
- **Lifecycle**: default

(`SubscriptionRepositoryPort`, `SubscriptionAddOnRepositoryPort`, `TenantScopePort` are also required for `InvoiceService` specifically per `STARTER_GUIDE.md` — documented under the subscription and add-on sections below to avoid duplication.)

---

## subscription aggregate

Anchor: `SubscriptionRepositoryPort`. Registers `SubscriptionSynchronizationService`, `SubscriptionService`.

### `SubscriptionRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for subscriptions, including provider-sync recovery queries.
- **Methods**: `Subscription save(Subscription)`; `Optional<Subscription> findCurrentByTenantId(UUID)`; `Optional<Subscription> findById(UUID)`; `Optional<Subscription> findCurrentBySubscriptionId(UUID, Collection<SubscriptionStatus>)`; `List<Subscription> findDueForRenewal(Instant asOf)` — `ACTIVE` subscriptions whose `currentPeriodEnd` is on/before `asOf`; `Optional<Subscription> findByExternalSubscriptionId(String)` — correlates Stripe/Razorpay webhook events back to BSM subscriptions; `List<Subscription> findExpiredTrials(Instant asOf)` — `TRIALING` subscriptions whose `trialEndsAt` is on/before `asOf`; `List<Subscription> findPendingProviderSync()` — live-status subscriptions with no provider subscription id yet
- **Required by**: subscription (anchor), invoice (for `InvoiceService`), invoice-renewal (derived), dunning, tenant-onboarding — a hard dependency of **both** `invoice` and `subscription`; see `STARTER_GUIDE.md`'s architecture note on why supplying it alone fails both aggregates' validation if the rest of the subscription port set is missing
- **bsm-svc adapter**: `SubscriptionRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/SubscriptionRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemorySubscriptionRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemorySubscriptionRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

### `SubscriptionHistoryRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for subscription lifecycle history entries.
- **Methods**: `SubscriptionHistory save(SubscriptionHistory)`; `PageResult<SubscriptionHistory> findHistory(SubscriptionHistoryFilter, int page, int size, String sortBy, String sortDirection)`
- **Required by**: subscription (anchor set), dunning
- **bsm-svc adapter**: `SubscriptionHistoryRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/SubscriptionHistoryRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemorySubscriptionHistoryRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemorySubscriptionHistoryRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

### `SubscriptionEventRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for subscription-scoped domain events (queryable read model, distinct from `SubscriptionEventPublisherPort`'s fire-and-forget publication).
- **Methods**: `SubscriptionEvent save(SubscriptionEvent)`; `PageResult<SubscriptionEvent> findEvents(SubscriptionEventFilter, int page, int size, String sortBy, String sortDirection)`
- **Required by**: subscription (anchor set)
- **bsm-svc adapter**: `SubscriptionEventRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/SubscriptionEventRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemorySubscriptionEventRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemorySubscriptionEventRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

### `SubscriptionScheduleRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for scheduled future subscription actions (e.g. scheduled downgrades).
- **Methods**: `SubscriptionSchedule save(SubscriptionSchedule)`; `Optional<SubscriptionSchedule> findPendingBySubscriptionIdAndActionType(UUID, SubscriptionScheduleActionType)`; `List<SubscriptionSchedule> findDueSchedules(Instant asOf)` — `PENDING` schedules whose `effectiveAt` is on/before `asOf`; `PageResult<SubscriptionSchedule> findSchedules(SubscriptionScheduleFilter, int page, int size, String sortBy, String sortDirection)`
- **Required by**: subscription (anchor set)
- **bsm-svc adapter**: `SubscriptionScheduleRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/SubscriptionScheduleRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemorySubscriptionScheduleRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemorySubscriptionScheduleRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

### `TenantTrialRecordRepositoryPort`
- **Purpose**: (inferred, no javadoc) Tracks whether a tenant has already consumed its one-time lifetime trial.
- **Methods**: `boolean existsByTenantId(UUID tenantId)` — "Called inside the same transaction as subscription creation so the check is isolated at the READ COMMITTED level."; `void markTrialConsumed(UUID tenantId, UUID subscriptionId, Instant consumedAt)` — "Called immediately after a TRIALING subscription row is saved, within the same transaction. The PRIMARY KEY on tenant_id in the underlying table ensures concurrent inserts from two nodes result in exactly one winner — the loser's transaction rolls back on the constraint violation."
- **Required by**: subscription (anchor set)
- **bsm-svc adapter**: `TenantTrialRecordRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/TenantTrialRecordRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemoryTenantTrialRecordRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryTenantTrialRecordRepository.java`
- **Thread safety**: **stronger than default** — the javadoc requires a `tenant_id` uniqueness constraint at the storage layer so concurrent trial-consumption attempts from different nodes resolve to exactly one winner (loser rolls back on constraint violation), plus READ COMMITTED isolation for the existence check within the same transaction as subscription creation. A naive in-memory implementation (like `bsm-demo`'s) must reproduce this with an atomic map operation, not a check-then-insert.
- **Lifecycle**: default

### `UsageLimitsSeedingPort`
- **Purpose**: "Best-effort synchronous seeding of usage quota limits right after a subscription is created."
- **Methods**: `void seedLimits(UUID tenantId, UUID ppmPlanId)`
- **Required by**: subscription (anchor set)
- **bsm-svc adapter**: `UsgLimitsSeedingService` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/client/usg/UsgLimitsSeedingService.java`
- **bsm-demo adapter (example)**: `NoOpUsageLimitsSeedingAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/NoOpUsageLimitsSeedingAdapter.java` (wired, deliberately a no-op)
- **Thread safety**: default
- **Lifecycle**: default

### `UserUsagePort`
- **Purpose**: "Outbound port: queries current user counts from ADM-SVC."
- **Methods**: `UserUsageCounts getUserUsageCounts(UUID tenantId)` — "Returns both active user counts in a single fetch. Preferred over the individual methods — avoids two HTTP round-trips per operation."; `default int getActiveInternalUserCount(UUID tenantId)` — delegates to `getUserUsageCounts`; `default int getActiveClientUserCount(UUID tenantId)` — delegates to `getUserUsageCounts`
- **Required by**: subscription (anchor set)
- **bsm-svc adapters**: `AdmUserUsageAdapter` (real) — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/external/adm/AdmUserUsageAdapter.java`; `StubUserUsageAdapter` (Phase-3 stub, superseded) — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/external/stub/StubUserUsageAdapter.java`
- **bsm-demo adapter (example)**: `InMemoryUserUsageAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryUserUsageAdapter.java`
- **Thread safety**: default
- **Lifecycle**: default

### `SubscriptionEventPublisherPort`
- **Purpose**: "Publishes subscription lifecycle business events. The host decides how these are delivered downstream (outbox, message broker, etc.) — the library only signals that the lifecycle transition occurred."
- **Methods**: `void publishCreated(Subscription)`; `void publishChanged(Subscription, String oldPlanCode, String reason)`; `void publishCanceled(Subscription)`; `void publishExpired(Subscription)`; `void publishRenewed(Subscription)`; `void publishUpgraded(Subscription, UUID fromPlanVersionId)`
- **Required by**: subscription (anchor set), invoice-renewal, dunning
- **bsm-svc adapter**: `BsmSubscriptionEventPublisher` — `bsm-svc/src/main/java/com/company/bsmsvc/messaging/BsmSubscriptionEventPublisher.java`
- **bsm-demo adapter (example)**: `InMemorySubscriptionEventPublisherAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemorySubscriptionEventPublisherAdapter.java`
- **Thread safety**: default
- **Lifecycle**: default

### `SubscriptionLimitSnapshotRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for point-in-time snapshots of a subscription's usage/entitlement limits.
- **Methods**: `SubscriptionLimitSnapshot save(SubscriptionLimitSnapshot)`; `PageResult<SubscriptionLimitSnapshot> findSnapshots(LimitSnapshotFilter, int page, int size, String sortBy, String sortDirection)`
- **Required by**: not listed in any `STARTER_GUIDE.md` aggregate's required set — optional
- **bsm-svc adapter**: `SubscriptionLimitSnapshotRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/SubscriptionLimitSnapshotRepositoryAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented per `SECOND_CONSUMER_VALIDATION.md`
- **Thread safety**: default
- **Lifecycle**: default

---

## invoice-renewal aggregate

Derived: needs `InvoiceService` + `SubscriptionRepositoryPort` already registered. Registers `InvoiceRenewalService`. Additional required ports beyond ones already covered above: `PlatformInvoiceRepositoryPort` (see invoice section), `SubscriptionEventPublisherPort` (see subscription section) — `TenantBillingProfileService` is a service dependency, not a port.

---

## dunning aggregate

Anchor: `DunningAttemptRepositoryPort`. Registers `DunningService`.

### `DunningAttemptRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for individual dunning (failed-payment retry) attempts.
- **Methods**: `DunningAttempt save(DunningAttempt)`; `Optional<DunningAttempt> findById(UUID)`; `List<DunningAttempt> findBySubscriptionId(UUID)`; `Optional<DunningAttempt> findLatestBySubscriptionId(UUID)`; `List<DunningAttempt> findDuePending(Instant now)`
- **Required by**: dunning (anchor)
- **bsm-svc adapter**: `DunningAttemptRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/DunningAttemptRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemoryDunningAttemptRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryDunningAttemptRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

### `DunningEventPublisher`
- **Purpose**: (inferred, no javadoc) Publishes dunning lifecycle events (started, retry, recovered, suspended, cancelled).
- **Methods**: `void publishStarted(UUID subscriptionId, UUID tenantId, UUID invoiceId, int attemptNumber)`; `void publishRetry(UUID subscriptionId, UUID tenantId, int attemptNumber, DunningStatus newStatus)`; `void publishRecovered(UUID subscriptionId, UUID tenantId, UUID invoiceId)`; `void publishSuspended(UUID subscriptionId, UUID tenantId)`; `void publishCancelled(UUID subscriptionId, UUID tenantId)`
- **Required by**: dunning (anchor set)
- **bsm-svc adapter**: `RabbitDunningEventPublisher` — `bsm-svc/src/main/java/com/company/bsmsvc/messaging/RabbitDunningEventPublisher.java`
- **bsm-demo adapter (example)**: `InMemoryDunningEventPublisherAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryDunningEventPublisherAdapter.java`
- **Thread safety**: default
- **Lifecycle**: default

### `BillingLedgerRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence port for billing ledger entries (append-only accounting trail).
- **Methods**: `BillingLedgerEntry save(BillingLedgerEntry entry)`
- **Required by**: dunning (anchor set)
- **bsm-svc adapter**: `BillingLedgerRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/BillingLedgerRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemoryBillingLedgerRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemoryBillingLedgerRepository.java`
- **Thread safety**: default
- **Lifecycle**: default

(`SubscriptionRepositoryPort`, `SubscriptionHistoryRepositoryPort`, `PaymentMethodRepositoryPort`, `PaymentRepositoryPort`, `PaymentGatewayResolver`, `TenantScopePort`, `SubscriptionEventPublisherPort`, `EventPublisherPort` are also required for dunning per `STARTER_GUIDE.md` — documented in their respective sections above to avoid duplication.)

---

## tenant-onboarding aggregate

Anchors: `DefaultTrialPlanPort` + `PlanVersionMetaPort` + `PpmPricingService`. Registers `TenantOnboardingService`.

### `DefaultTrialPlanPort`
- **Purpose**: "Resolves the platform's default free/trial plan for tenants with no pre-selected plan."
- **Methods**: `PpmDefaultTrialResult getDefaultTrialPlan()`
- **Required by**: tenant-onboarding (anchor)
- **bsm-svc adapter**: `PpmDefaultTrialClient` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/client/ppm/PpmDefaultTrialClient.java`
- **bsm-demo adapter (example)**: `DemoDefaultTrialPlanAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/DemoDefaultTrialPlanAdapter.java` (wired via config; the pre-resolved-plan onboarding path in `EndToEndWorkflowTest` doesn't exercise it — see `SECOND_CONSUMER_VALIDATION.md`)
- **Thread safety**: default
- **Lifecycle**: default

### `PlanVersionMetaPort`
- **Purpose**: "Resolves plan version metadata (plan code, tier, active flags) from the plan catalog."
- **Methods**: `PpmVersionMetaResult getVersionMeta(UUID versionId)`
- **Required by**: tenant-onboarding (anchor)
- **bsm-svc adapter**: `PpmVersionMetaClient` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/client/ppm/PpmVersionMetaClient.java`
- **bsm-demo adapter (example)**: `DemoPlanVersionMetaAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/DemoPlanVersionMetaAdapter.java`
- **Thread safety**: default
- **Lifecycle**: default

### `PpmPricingService`
- **Purpose**: "Service abstraction over PPM-SVC's Pricing Resolver endpoint. Implementations apply a circuit breaker so that a degraded PPM-SVC fast-fails checkout requests rather than hanging for the full request timeout."
- **Methods**: `PpmResolvePriceResult resolvePrice(UUID ppmPlanId, String region, String currency, String cycle)` — never returns null; throws `PpmIntegrationException` if PPM is unavailable or the circuit is open
- **Required by**: tenant-onboarding (anchor)
- **bsm-svc adapter**: `PpmPricingServiceImpl` — `bsm-svc/src/main/java/com/company/bsmsvc/application/impl/PpmPricingServiceImpl.java` (Resilience4j circuit breaker wrapping an HTTP client to PPM-SVC — forbidden in `bsm-core` by the `ArchitectureTest` ArchUnit rule, per `ARCHITECTURE_CERTIFICATION.md`)
- **bsm-demo adapter (example)**: `DemoPricingAdapter` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/DemoPricingAdapter.java`
- **Thread safety**: default; resilience note — fail-fast on circuit-open is a deliberate design characteristic, not a thread-safety concern
- **Lifecycle**: default

(`SubscriptionRepositoryPort` is also required for tenant-onboarding per `STARTER_GUIDE.md` — see the subscription section above.)

---

## Ports not gated by any starter aggregate (optional — no auto-configured service requires them)

These back capabilities outside the nine starter aggregates' scope: credit notes, refunds, migration plans, proration preview, webhooks, add-ons beyond the base subscription flow, feature entitlement, project/storage usage, PPM promo/add-on-pricing/version services, and plan limits. Consumers who want `CreditNoteService`, `RefundService`, `MigrationPlanService`, `SubscriptionAddOnService`, `SubscriptionChangeService`, or `WebhookProcessingService` must wire these manually — see `API_STABILITY.md`'s "not yet covered by the starter" list.

### credit-note

#### `CreditNoteRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for credit notes.
- **Methods**: `CreditNote save(CreditNote)`; `Optional<CreditNote> findById(UUID)`; `PageResult<CreditNote> findCreditNotes(CreditNoteFilter, int page, int size, String sortBy, String sortDirection)`
- **bsm-svc adapter**: `CreditNoteRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/CreditNoteRepositoryAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

### refund

#### `RefundRequestRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for refund requests, with idempotency and over-refund-guard queries.
- **Methods**: `RefundRequest save(RefundRequest)`; `Optional<RefundRequest> findById(UUID)`; `Optional<RefundRequest> findActiveByInvoiceAndPaymentAndAmount(UUID invoiceId, UUID paymentId, long amountMinor)` — "Idempotency lookup: find an active (non-FAILED) request for the same invoice + payment + amount. Used to prevent duplicate provider calls."; `List<RefundRequest> findByStatus(RefundStatus status)` — used by the recovery scheduler; `List<RefundRequest> findByStatusOlderThan(RefundStatus status, Instant threshold)`; `long sumProviderCommittedAmountByInvoiceId(UUID invoiceId)` — "Sum of requestedAmountMinor for requests that are committed at the provider but whose credit note may not exist yet. Used to close the over-refund gap during concurrent requests."
- **bsm-svc adapter**: `RefundRequestRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/RefundRequestRepositoryAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety**: **stronger than default** — the idempotency lookup and committed-amount sum are explicitly designed to prevent duplicate provider refund calls and over-refunds when multiple refund requests race concurrently; an implementation must make these queries consistent under concurrent writes (e.g. `SELECT ... FOR UPDATE` or an equivalent transactional guard), not just individually thread-safe.
- **Lifecycle**: default

### migration-plan

#### `MigrationPlanRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for tenant resource-migration plans and their line items.
- **Methods**: `MigrationPlan savePlan(MigrationPlan)`; `MigrationPlanItem saveItem(MigrationPlanItem)`; `Optional<MigrationPlan> findById(UUID)`; `List<MigrationPlanItem> findItemsByMigrationPlanId(UUID)`; `PageResult<MigrationPlan> findPlans(MigrationPlanFilter, int page, int size, String sortBy, String sortDirection)`
- **bsm-svc adapter**: `MigrationPlanRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/MigrationPlanRepositoryAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

### ppm / pricing extras

#### `PpmChangeSnapshotRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for point-in-time snapshots of a plan-change calculation (audit trail for upgrades/downgrades).
- **Methods**: `PpmChangeSnapshot save(PpmChangeSnapshot)`; `List<PpmChangeSnapshot> findBySubscriptionId(UUID)`
- **bsm-svc adapter**: `PpmChangeSnapshotRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/PpmChangeSnapshotRepositoryAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

#### `ProrationPreviewRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for generated proration previews (shown to a user before they confirm a plan change).
- **Methods**: `ProrationPreview save(ProrationPreview)`; `Optional<ProrationPreview> findById(UUID)`; `PageResult<ProrationPreview> findPreviews(ProrationPreviewFilter, int page, int size, String sortBy, String sortDirection)`
- **bsm-svc adapter**: `ProrationPreviewRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/ProrationPreviewRepositoryAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

#### `PpmAddOnPricingService`
- **Purpose**: "Application service for PPM add-on price resolution. Wraps `PpmAddOnPricingClient` with a Resilience4j circuit breaker so failures degrade gracefully (circuit opens → fast-fail with `PpmIntegrationException`)."
- **Methods**: `PpmResolvedAddOnPriceResult resolveActivePrice(UUID addOnId, String region, String currency, BillingCycle cycle)`
- **bsm-svc adapter**: `PpmAddOnPricingServiceImpl` — `bsm-svc/src/main/java/com/company/bsmsvc/application/impl/PpmAddOnPricingServiceImpl.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default; same fail-fast circuit-breaker note as `PpmPricingService`

#### `PpmPromoService`
- **Purpose**: "Service abstraction over PPM-SVC's Promo Validation endpoint. PPM is the sole authority for promo code validity. BSM never replicates or re-evaluates promo rules (date ranges, usage caps, plan eligibility). Implementations apply a circuit breaker — failure always means fail-closed (promo cannot be applied), never silent approval."
- **Methods**: `PpmValidatePromoResult validatePromo(String code, UUID ppmPlanId)` — `valid=false` is a normal business outcome; throws `PpmIntegrationException` if unavailable/circuit open
- **bsm-svc adapter**: `PpmPromoServiceImpl` — `bsm-svc/src/main/java/com/company/bsmsvc/application/impl/PpmPromoServiceImpl.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default; fail-closed circuit-breaker semantics

#### `PpmVersionService`
- **Purpose**: "Service abstraction over PPM-SVC's plan version lookup. Implementations apply a circuit breaker (fail-closed) so that a degraded PPM-SVC aborts checkout rather than storing a null version."
- **Methods**: `PpmPlanVersionResult getLatestVersion(UUID ppmPlanId)` — never null; throws `PpmIntegrationException` if unavailable/circuit open
- **bsm-svc adapter**: `PpmVersionServiceImpl` — `bsm-svc/src/main/java/com/company/bsmsvc/application/impl/PpmVersionServiceImpl.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default; fail-closed circuit-breaker semantics

#### `PlanLimitsPort`
- **Purpose**: "Resolves plan version entitlement limits from the plan catalog."
- **Methods**: `PpmPlanLimitsResult getLimits(UUID ppmPlanVersionId)`
- **bsm-svc adapter**: `PpmPlanLimitsClient` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/client/ppm/PpmPlanLimitsClient.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

### add-on

#### `SubscriptionAddOnRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/query port for purchased subscription add-ons.
- **Methods**: `SubscriptionAddOn save(SubscriptionAddOn)`; `List<SubscriptionAddOn> findBySubscriptionId(UUID)`; `List<SubscriptionAddOn> findActiveBySubscriptionId(UUID)`; `Optional<SubscriptionAddOn> findActiveBySubscriptionIdAndPpmAddOnId(UUID, UUID)`; `boolean existsActive(UUID subscriptionId, UUID ppmAddOnId)`
- **Required by**: invoice aggregate's `InvoiceService` per `STARTER_GUIDE.md` (listed here rather than duplicated above, since its primary home is the add-on capability)
- **bsm-svc adapter**: `SubscriptionAddOnRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/SubscriptionAddOnRepositoryAdapter.java`
- **bsm-demo adapter (example)**: `InMemorySubscriptionAddOnRepository` — `bsm-demo/src/main/java/com/example/bsmdemo/adapter/InMemorySubscriptionAddOnRepository.java` (wired, not exercised by the demo's end-to-end flow per `SECOND_CONSUMER_VALIDATION.md`)
- **Thread safety / Lifecycle**: default

#### `AddOnEventPublisherPort`
- **Purpose**: "Publishes add-on lifecycle business events."
- **Methods**: `void publishActivated(SubscriptionAddOn addOn)`; `void publishDeactivated(SubscriptionAddOn addOn)`
- **bsm-svc adapter**: `BsmAddOnEventPublisher` — `bsm-svc/src/main/java/com/company/bsmsvc/messaging/BsmAddOnEventPublisher.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

### usage / limits / entitlement

#### `FeatureEntitlementPort`
- **Purpose**: "Outbound port: queries active feature entitlements from FMM-SVC. Stub adapter active in Phase 3; replaced by real integration in Phase 4."
- **Methods**: `List<String> getActiveFeatureCodes(UUID tenantId)`
- **bsm-svc adapters**: `PlanVersionFeatureEntitlementAdapter` (real, current) — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/internal/PlanVersionFeatureEntitlementAdapter.java`; `StubFeatureEntitlementAdapter` (Phase-3 stub, superseded) — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/external/stub/StubFeatureEntitlementAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

#### `ProjectUsagePort`
- **Purpose**: "Outbound port: queries active project counts from ADM-SVC. Stub adapter active in Phase 3; replaced by real integration in Phase 4."
- **Methods**: `int getActiveProjectCount(UUID tenantId)`
- **bsm-svc adapter**: `StubProjectUsageAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/external/stub/StubProjectUsageAdapter.java` (still a stub — no "Phase 4" real client was found alongside it, unlike `UserUsagePort`/`FeatureEntitlementPort`; worth confirming with the owning team before relying on it in production)
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

#### `StorageUsagePort`
- **Purpose**: "Outbound port: queries current storage consumption from USG-SVC. Stub adapter active in Phase 3; replaced by real integration in Phase 4."
- **Methods**: `long getUsedStorageBytes(UUID tenantId)`
- **bsm-svc adapter**: `StubStorageUsageAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/external/stub/StubStorageUsageAdapter.java` (same caveat as `ProjectUsagePort` — still a stub in the current codebase)
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety / Lifecycle**: default

### webhook

#### `WebhookEventRepositoryPort`
- **Purpose**: (inferred, no javadoc) Persistence/dedup lookup port for inbound provider webhook events (Stripe/Razorpay), keyed by provider + external event id to guard against duplicate processing.
- **Methods**: `WebhookEvent save(WebhookEvent)`; `Optional<WebhookEvent> findByProviderAndExternalEventId(PaymentProvider provider, String externalEventId)`
- **bsm-svc adapter**: `WebhookEventRepositoryAdapter` — `bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/persistence/adapter/WebhookEventRepositoryAdapter.java`
- **bsm-demo adapter**: none — intentionally unimplemented
- **Thread safety**: implicit — the provider+external-event-id lookup exists specifically to dedupe concurrent/retried webhook deliveries; treat the save+dedup-check as needing a uniqueness constraint at the storage layer, similar in spirit to `TenantTrialRecordRepositoryPort`
- **Lifecycle**: default

### authorization

#### `BsmAuthorizationPort`
- **Purpose**: "Authorization abstraction for BSM-SVC. Implementations will wire in the actual auth mechanism (JWT claims, header-based, etc.). No Spring Security dependency — the domain layer stays framework-agnostic."
- **Methods**: `boolean hasRole(UUID principalId, UUID tenantId, BsmRole required)` — "Returns true if the given principal has at least the required role for the given tenant."
- **bsm-svc adapter**: **none found.** `grep -rl "implements BsmAuthorizationPort"` across the entire repo returns zero matches. `ARCHITECTURE_CERTIFICATION.md`'s "Security/messaging ports" line lists `TenantScopePort`/`BsmAuthorizationPort` → `BsmTenantScopeEnforcer`, but `BsmTenantScopeEnforcer` (`bsm-svc/src/main/java/com/company/bsmsvc/infrastructure/security/BsmTenantScopeEnforcer.java`) implements only `TenantScopePort` — **this is a discrepancy against `ARCHITECTURE_CERTIFICATION.md`**, not a documentation choice made here. Either the port is genuinely unimplemented (dead SPI surface) or an implementer was removed without updating the certification doc; worth a follow-up before calling the SPI surface fully certified.
- **bsm-demo adapter**: none
- **Thread safety / Lifecycle**: default (n/a until implemented)

---

## Summary

- 41 extension points documented (40 `domain.port` interfaces + `PaymentGatewayResolver`).
- 23 have a bsm-demo in-memory reference adapter (matches `SECOND_CONSUMER_VALIDATION.md`'s 23/40 count for the 40 domain ports, plus `PaymentGatewayResolver` itself also has a demo adapter — `DemoPaymentGatewayResolver`).
- 40 of 41 have at least one bsm-svc production adapter; **`BsmAuthorizationPort` has zero implementations anywhere in the repo** — flagged above as a discrepancy against `ARCHITECTURE_CERTIFICATION.md`.
- `ProjectUsagePort` and `StorageUsagePort` still resolve only to their Phase-3 stub adapters in bsm-svc (no confirmed Phase-4 real client alongside them, unlike `UserUsagePort` and `FeatureEntitlementPort` which do have both a stub and a real adapter) — worth confirming intentional before treating them as production-ready.

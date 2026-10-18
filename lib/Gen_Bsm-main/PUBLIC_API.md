# Public API Reference

Exhaustive classification of every public class/interface/enum in `bsm-core`'s and
`bsm-spring-boot-starter`'s main source sets, into the four tiers already established by
`API_STABILITY.md`: **Public Library API**, **Extension SPI**, **Internal**, **Experimental**.
Tier names and definitions are used verbatim from that file — this document expands it into a
full, aggregate-organized class listing rather than representative examples.

`bsm-core` has zero framework dependencies (see `ARCHITECTURE_CERTIFICATION.md`); everything
below in `bsm-core` compiles standalone. `bsm-spring-boot-starter` depends on `bsm-core` plus
Spring Boot autoconfigure machinery.

---

## `bsm-core` — `com.company.bsmsvc.application.service` (Public API tier)

Every class here is a `public interface` — the primary surface consumers call. Per
`API_STABILITY.md`, ten of these are auto-configured by the starter today; the rest exist,
are fully functional, and must be wired manually.

| Interface | Purpose | Starter-wired? |
|---|---|---|
| `TenantOnboardingService` | Onboards a new tenant into its first BSM subscription (resolves trial/plan, creates subscription, generates first invoice). | Yes |
| `TenantBillingProfileService` | Manages a tenant's provider/currency/external-customer billing profile. | Yes |
| `PaymentMethodService` | Manages a tenant's provider customer record and saved payment methods. | Yes |
| `PaymentService` | Collects payment against an open invoice (checkout session / payment intent / lookup). | Yes |
| `InvoiceGenerationService` | Low-level invoice creation primitive shared by multiple flows (onboarding, renewal). | Yes |
| `InvoiceService` | Invoice lifecycle: create (thin wrapper), lookup/search, payment application, void, refund-mark. | Yes |
| `SubscriptionSynchronizationService` | Syncs subscription create/update/cancel to the payment provider. | Yes |
| `SubscriptionService` | Subscription lifecycle: create, cancel, pause/resume, scheduled downgrades, history/event/schedule reads. | Yes |
| `InvoiceRenewalService` | Generates renewal invoices for subscriptions due for renewal. | Yes |
| `DunningService` | Failed-payment recovery lifecycle (start/retry/recover dunning). | Yes |
| `SubscriptionAddOnService` | Purchase/list/remove subscription add-ons. | No — manual wiring, see `docs/examples/add-on-purchase.md` |
| `SubscriptionChangeService` | Preview and apply PPM-backed plan changes (upgrade/downgrade). | No — manual wiring, see `docs/examples/plan-upgrade.md` |
| `CreditNoteService` | Create/list/get/apply/void credit notes. | No — not expected without a separate design decision |
| `MigrationPlanService` | Persists/retrieves/lists tenant migration plans (bulk resource migration). | No |
| `PpmSnapshotIntegrityService` | Read-only diagnostics on a subscription's PPM catalog-reference consistency. | No |
| `RefundService` | Creates refunds (as credit notes) against invoices. | No |
| `RefundRecoveryService` | Retries/recovers stuck refund requests. | No |
| `PaymentReconciliationService` | Reconciles stale `PENDING` payments against the gateway. | No |
| `PaymentGatewayResolver` | Resolves the `PaymentGatewayPort` implementation for a given `PaymentProvider` — also documented as an SPI/extension point in `PORT_REFERENCE.md`. | Yes (payment/subscription/dunning anchor) |
| `InvoiceNumberGenerator` | Generates human-readable invoice numbers. | Yes — default impl auto-supplied by `BsmSupportAutoConfiguration` unless overridden |
| `CreditNoteNumberGenerator` | Generates human-readable credit note numbers. | No |
| `WebhookProcessingService` | Processes Stripe/Razorpay inbound webhooks. | No |
| `PpmCheckoutService` | Orchestrates PPM-catalog-backed checkout (price resolution → subscription → invoice → payment session). | No |
| `CommercialEngineService` | Plan upgrade/downgrade orchestration, proration preview generation, downgrade preflight impact analysis. **Lives in `bsm-svc`, not `bsm-core`** — its impl depends on `io.cpms.common.plan.PpmTierRegistry`. Interface only, listed here because the `application.service` package glob picked it up; see Experimental tier below. | No |
| `BillingDashboardService` | Aggregates a tenant's billing summary for dashboards. **Impl lives in `bsm-svc`**, queries via raw `JdbcTemplate` SQL. See Experimental tier below. | No |

---

## `bsm-core` — `com.company.bsmsvc.application.impl` (Internal tier)

Sole concrete implementations of the interfaces above, constructed only via the starter's
`@AutoConfiguration` `@Bean` methods (never `@Component`-scanned themselves). Not part of the
stable contract — only the interface type they're registered under is guaranteed stable.

`CreditNoteNumberGeneratorImpl`, `CreditNoteServiceImpl`, `InvoiceGenerationServiceImpl`,
`InvoiceNumberGeneratorImpl`, `InvoiceRenewalServiceImpl`, `InvoiceServiceImpl`,
`MigrationPlanServiceImpl`, `PaymentMethodServiceImpl`, `PaymentReconciliationServiceImpl`,
`PaymentServiceImpl`, `PpmSnapshotIntegrityServiceImpl`, `RefundRecoveryServiceImpl`,
`RefundServiceImpl`, `SubscriptionScheduleExecutorServiceImpl`, `SubscriptionServiceImpl`,
`SubscriptionSynchronizationServiceImpl`, `TenantBillingProfileServiceImpl`,
`DunningServiceImpl`, `PpmCheckoutServiceImpl`, `SubscriptionAddOnServiceImpl`,
`SubscriptionChangeServiceImpl`, `TenantOnboardingServiceImpl`, `TrialExpiryServiceImpl`.

## `bsm-core` — `com.company.bsmsvc.application.util` (Internal tier)

- `PaginationUtils` — static helpers clamping page/size query params to safe bounds.

---

## `bsm-core` — `com.company.bsmsvc.domain.model` (Public API tier — subscription/invoice/payment/dunning/tenant-billing/plan-change aggregates)

Mostly `record`/`@Builder` value types: entities, paged-query filters, commands, results.

**Entities/aggregates**: `Subscription` (subscription), `PlatformInvoice` (invoice),
`Payment` (payment), `PaymentMethod` (payment), `CreditNote` (invoice/credit-note),
`MigrationPlan` / `MigrationPlanItem` (migration-plan), `TenantBillingProfile` (tenant-billing),
`TenantTrialRecord` (tenant-onboarding), `WebhookEvent` (webhook), `BillingLedgerEntry`
(dunning/ledger), `InvoiceLineItem` (invoice), `DunningAttempt` (dunning), `RefundRequest`
(refund), `SubscriptionAddOn` (add-on), `SubscriptionEvent` (subscription),
`SubscriptionHistory` (subscription), `SubscriptionSchedule` (subscription),
`SubscriptionLimitSnapshot` (subscription/usage), `PpmChangeSnapshot` (plan-change),
`UserUsageCounts` (usage).

**Paged-query filters**: `CreditNoteFilter`, `InvoiceFilter`, `LimitSnapshotFilter`,
`MigrationPlanFilter`, `ProrationPreviewFilter`, `SubscriptionEventFilter`,
`SubscriptionHistoryFilter`, `SubscriptionScheduleFilter`.

**Generic paging wrapper**: `PageResult<T>` (content/page/size/totalElements/totalPages/hasNext).

**Commands/results — commercial-engine & PPM flows (plan-change aggregate)**:
`DowngradeImpact`, `DowngradeImpactDetails`, `DowngradeWarning`, `ProrationPreview`,
`PpmPlanLimitsResult`, `PpmPlanVersionResult`, `PpmProrationResult`, `PpmResolvePriceResult`,
`PpmResolvedAddOnPriceResult`, `PpmSnapshotDiagnostics`, `PpmValidatePromoResult`,
`PpmVersionMetaResult`, `PpmDefaultTrialResult`, `InitiateCheckoutCommand`, `CheckoutResult`,
`PurchaseAddOnCommand`, `AddOnPurchaseResult`, `PreviewPlanChangeCommand`,
`ApplyPlanChangeCommand`, `PlanChangePreviewResult`, `PlanChangeApplyResult`,
`OnboardTenantCommand`.

**Messaging**: `InvoiceCreatedMessage` (async PDF-generation trigger payload, paired with
`InvoiceEventPublisher`).

**Dashboard**: `BillingSummary` (used only by the Experimental `BillingDashboardService`).

**Host-configured policy value objects** (plain records, bridged from Spring
`@ConfigurationProperties` by the starter — see `CONFIGURATION_REFERENCE.md`): `DunningPolicy`,
`TrialPolicy`, `ReconciliationPolicy`.

## `bsm-core` — `com.company.bsmsvc.domain.model.payment` (Public API tier — payment aggregate)

Gateway-agnostic commands/results used by `PaymentGatewayPort` and `PaymentGatewayResolver`:
`AttachPaymentMethodCommand`, `CancelSubscriptionCommand`, `CheckoutSessionResult`,
`CreateCheckoutSessionCommand`, `CreateCustomerCommand`, `CreatePaymentIntentCommand`,
`CreateSubscriptionCommand`, `CustomerResult`, `DetachPaymentMethodCommand`,
`ListPaymentMethodsCommand`, `ListPaymentMethodsResult`, `PaymentIntentResult`,
`PaymentMethodDetails`, `PaymentStatusResult`, `RefundCommand`, `RefundResult`,
`RetryPaymentCommand`, `SubscriptionResult`, `UpdateSubscriptionCommand`.

---

## `bsm-core` — `com.company.bsmsvc.domain.enums` (Public API tier)

Plain `public enum`s with no methods beyond values, organized by aggregate:

- **subscription**: `SubscriptionStatus` (TRIALING/ACTIVE/PAUSED/PAST_DUE/SUSPENDED_PENDING_PURGE/CANCELLED), `SubscriptionEventType`, `SubscriptionHistoryAction`, `SubscriptionScheduleActionType`, `SubscriptionScheduleStatus`, `BillingCycle` (MONTHLY/YEARLY)
- **invoice**: `InvoiceStatus`, `InvoiceSource`, `InvoiceLineItemType`, `InvoicePdfStatus`
- **payment**: `PaymentStatus`, `PaymentMethodStatus`, `PaymentMethodType`, `PaymentProvider`
- **dunning**: `DunningStatus`, `DunningAttemptStatus`
- **credit-note / refund**: `CreditNoteStatus`, `RefundStatus`
- **tenant-onboarding / plan-change**: `PpmPlanChangeType`, `ProrationMode`, `PpmSnapshotStatus`
- **migration-plan**: `MigrationAction`, `MigrationPlanStatus`, `MigrationResourceType`
- **webhook**: `WebhookEventStatus`
- **ledger**: `LedgerEntryType`
- **authorization**: `BsmRole`, `ActorType`

## `bsm-core` — `com.company.bsmsvc.domain.event` (Public API tier)

40 immutable `public record`s of shape `(...ids..., Instant occurredAt)` (pattern confirmed via
`SubscriptionCreatedEvent(UUID subscriptionId, UUID tenantId, Instant occurredAt)` and
`PaymentFailedEvent(UUID paymentId, UUID invoiceId, UUID tenantId, String reason, Instant
occurredAt)`), fired by aggregate lifecycle transitions for outbox/audit purposes:

`CreditNoteAppliedEvent`, `CreditNoteCreatedEvent`, `CreditNoteVoidedEvent`,
`CustomerCreatedEvent`, `DowngradePreflightExecutedEvent`, `DunningCancelledEvent`,
`DunningRecoveredEvent`, `DunningRetryEvent`, `DunningStartedEvent`, `DunningSuspendedEvent`,
`ExternalSubscriptionCancelledEvent`, `ExternalSubscriptionCreatedEvent`,
`ExternalSubscriptionUpdatedEvent`, `InvoiceCreatedEvent`, `InvoiceMarkedPaidEvent`,
`InvoicePdfGeneratedEvent`, `InvoicePdfGenerationFailedEvent`, `InvoiceRenewedEvent`,
`InvoiceVoidedEvent`, `LedgerEntryCreatedEvent`, `LineItemAddedEvent`,
`MigrationPlanCreatedEvent`, `PaymentFailedEvent`, `PaymentInitiatedEvent`,
`PaymentMethodAddedEvent`, `PaymentMethodDefaultChangedEvent`, `PaymentMethodRemovedEvent`,
`PaymentSucceededEvent`, `ProrationPreviewGeneratedEvent`, `RefundCreatedEvent`,
`SubscriptionCancelledEvent`, `SubscriptionCreatedEvent`, `SubscriptionDowngradeCancelledEvent`,
`SubscriptionDowngradeScheduledEvent`, `SubscriptionPausedEvent`, `SubscriptionResumedEvent`,
`SubscriptionScheduledForCancellationEvent`, `SubscriptionUpgradedEvent`,
`TenantBillingCurrencyChangedEvent`, `TenantBillingProfileCreatedEvent`,
`TenantBillingProviderChangedEvent`.

## `bsm-core` — `com.company.bsmsvc.domain.exception` (Public API tier)

`public class X extends RuntimeException`, with `(String message)` and often
`(String message, Throwable cause)` constructors — consumers catch these directly.

- `BusinessRuleViolationException` — generic invariant/precondition violation (most-used).
- `ConcurrentUpdateException` — optimistic-concurrency conflict signal for repository adapters to throw (see `INTEGRATION_GUIDE.md`'s `JpaSubscriptionRepository` example).
- **Not-found lookups per aggregate**: `AddOnAlreadyPurchasedException`, `CreditNoteNotFoundException`, `InvoiceNotFoundException`, `MigrationPlanNotFoundException`, `PaymentMethodNotFoundException`, `PaymentNotFoundException`, `PlanNotFoundException`, `ProrationPreviewNotFoundException`, `SubscriptionAddOnNotFoundException`, `SubscriptionNotFoundException`, `TenantBillingProfileNotFoundException`.
- `PaymentGatewayException` — wraps underlying gateway (Stripe/Razorpay) call failures.
- `PpmIntegrationException` — wraps PPM catalog service integration failures (thrown by the three circuit-breaker-wrapped PPM ports).
- `TrialAlreadyConsumedException` — tenant already used its one-time trial.
- `UsageDataUnavailableException` — usage/limits data could not be resolved.
- `WebhookVerificationException` — inbound webhook signature verification failed.

---

## `bsm-core` — `com.company.bsmsvc.domain.port` (Extension SPI tier)

All 38 files in this package plus `PaymentGatewayResolver` (in `application.service`) are the
40+1 SPI ports — full detail (purpose, method signatures, adapters, required/optional,
thread-safety, lifecycle) is in the dedicated `PORT_REFERENCE.md`, not repeated here. Name list
for completeness: `AddOnEventPublisherPort`, `BillingLedgerRepositoryPort`,
`BsmAuthorizationPort`, `CreditNoteRepositoryPort`, `DefaultTrialPlanPort`,
`DunningAttemptRepositoryPort`, `DunningEventPublisher`, `EventPublisherPort`,
`FeatureEntitlementPort`, `InvoiceEventPublisher`, `InvoiceLineItemRepositoryPort`,
`MigrationPlanRepositoryPort`, `PaymentGatewayPort`, `PaymentMethodRepositoryPort`,
`PaymentRepositoryPort`, `PlanLimitsPort`, `PlanVersionMetaPort`,
`PlatformInvoiceRepositoryPort`, `PpmAddOnPricingService`, `PpmChangeSnapshotRepositoryPort`,
`PpmPricingService`, `PpmPromoService`, `PpmVersionService`, `ProjectUsagePort`,
`ProrationPreviewRepositoryPort`, `RefundRequestRepositoryPort`, `StorageUsagePort`,
`SubscriptionAddOnRepositoryPort`, `SubscriptionEventPublisherPort`,
`SubscriptionEventRepositoryPort`, `SubscriptionHistoryRepositoryPort`,
`SubscriptionLimitSnapshotRepositoryPort`, `SubscriptionRepositoryPort`,
`SubscriptionScheduleRepositoryPort`, `TenantBillingProfileRepositoryPort`, `TenantScopePort`,
`TenantTrialRecordRepositoryPort`, `UsageLimitsSeedingPort`, `UserUsagePort`,
`WebhookEventRepositoryPort` — plus `PaymentGatewayResolver`.

Note: `PpmAddOnPricingService`, `PpmPricingService`, `PpmPromoService`, `PpmVersionService` are
named `*Service` but live in `domain.port` and are genuine SPI, not application services — don't
confuse them with the `application.service` package's `*Service` interfaces (Public Library API
tier) above.

---

## `bsm-core` — `com.company.bsmsvc.domain.service` (Internal tier)

`@Component`-annotated, stateless domain helper classes — not application-facing, no consumer
should construct or extend these directly.

- `PpmProrationEngine` — BigDecimal-based proration calculator for PPM plan changes (credit/charge/net + change-type classification). (plan-change)
- `PpmReferenceValidator` — validates a subscription's four PPM reference fields are all-null or all-populated. (plan-change / subscription)
- `SubscriptionLifecycleMapper` — builds new `Subscription`/`SubscriptionSchedule`/`SubscriptionHistory`/`SubscriptionEvent` instances for lifecycle transitions. (subscription) — also registered as a bean by `BsmSupportAutoConfiguration`.
- `TenantOwnershipValidator` — throws `BusinessRuleViolationException` if a subscription doesn't belong to the requesting tenant. (tenant-billing / subscription) — also registered as a bean by `BsmSupportAutoConfiguration`.

---

## `bsm-spring-boot-starter` — `com.company.bsmsvc.starter.config` (Internal tier)

Per `API_STABILITY.md`'s Internal tier definition ("consumers should never construct or extend
these directly — only rely on the beans they produce").

| Class | Purpose |
|---|---|
| `BsmProperties` | `@ConfigurationProperties(prefix = "bsm")` root: `enabled` master switch + nested `Dunning`/`Trial`/`Reconciliation` sections. Full detail in `CONFIGURATION_REFERENCE.md`. |
| `BsmPolicyAutoConfiguration` | Bridges `BsmProperties` into `bsm-core`'s plain `DunningPolicy`/`TrialPolicy`/`ReconciliationPolicy` beans. Also does `@EnableConfigurationProperties(BsmProperties.class)`. |
| `BsmSupportAutoConfiguration` | Registers stateless helper beans (`SubscriptionLifecycleMapper`, `TenantOwnershipValidator`, default `InvoiceNumberGenerator`). |
| `TenantBillingProfileAutoConfiguration` | Wires `TenantBillingProfileService`, anchored on `TenantBillingProfileRepositoryPort`. |
| `PaymentAutoConfiguration` | Wires `PaymentMethodService`/`PaymentService`, anchored on `PaymentGatewayResolver`. |
| `SubscriptionAutoConfiguration` | Wires `SubscriptionSynchronizationService`/`SubscriptionService`, anchored on `SubscriptionRepositoryPort`. |
| `InvoiceRenewalAutoConfiguration` | Wires `InvoiceRenewalService`, anchored on `InvoiceService` + `SubscriptionRepositoryPort`. |
| `DunningAutoConfiguration` | Wires `DunningService`, anchored on `DunningAttemptRepositoryPort`. |
| `TenantOnboardingAutoConfiguration` | Wires `TenantOnboardingService`, anchored on `DefaultTrialPlanPort` + `PlanVersionMetaPort` + `PpmPricingService`. |
| `InvoiceAutoConfiguration` | Wires `InvoiceGenerationService`/`InvoiceService`, anchored on `PlatformInvoiceRepositoryPort`. |

## `bsm-spring-boot-starter` — `com.company.bsmsvc.starter.config.validation` (Internal tier)

- `BsmPortAvailabilityValidatorAutoConfiguration` — registers the `BsmPortAvailabilityValidator` `SmartInitializingSingleton` bean.

## `bsm-spring-boot-starter` — `com.company.bsmsvc.starter.validation` (Internal tier)

Note this is a **separate, sibling package** to `starter.config.validation` above (both exist,
easy to conflate).

- `BsmPortAvailabilityValidator` — post-startup check that fails fast with an actionable message when a consumer has wired some-but-not-all ports of an aggregate — the case `@ConditionalOnBean` can't express on its own. See `STARTER_GUIDE.md`'s "Fail-fast validation contract" for the exact error text.
- `BsmAggregateSpec` — package-private `record` describing one aggregate's anchor/required ports + expected service beans; used only by the validator above.

There is no separate `starter.properties` package — `BsmProperties` lives directly under
`starter.config`.

---

## Experimental tier

Per `API_STABILITY.md`, verbatim:

- **`CommercialEngineService`** and **`BillingDashboardService`** — live in `bsm-svc`, not
  `bsm-core`. `CommercialEngineServiceImpl` depends on a real cross-service tier registry
  (`io.cpms.common.plan.PpmTierRegistry`, shared with ppm-svc); `BillingDashboardServiceImpl`
  queries via raw `JdbcTemplate` SQL rather than a repository port. Neither is portable in its
  current form. Not part of the library surface at all right now — would need a real design
  (a `BillingSummaryQueryPort`, at minimum) before becoming Public API.
- **`SubscriptionAddOnService` / `SubscriptionChangeService`** — Public API in the sense that
  they're stable, tested interfaces in `bsm-core`, but Experimental in that the starter doesn't
  auto-configure them yet — "wire it yourself" rather than "add the dependency and go." Listed
  under Public Library API above per their true home package, cross-referenced here for
  completeness per `API_STABILITY.md`'s own dual-listing of them.
- **`libs/java-common`'s `io.cpms.common.messaging.*`** cross-service event catalog and
  **`PpmTierRegistry`** — real, load-bearing, but explicitly platform-specific (CPMS's own shared
  contracts), not part of the portable Billing library surface at all. Not in `bsm-core` or the
  starter — mentioned here only because `CommercialEngineServiceImpl` (`bsm-svc`) depends on
  `PpmTierRegistry`.

---

## Tier summary

| Tier | Location | Count (approx.) |
|---|---|---|
| Public Library API | `bsm-core/application/service` (24 interfaces, 2 of which — `CommercialEngineService`, `BillingDashboardService` — are Experimental per above), `domain/model` (~61 types + 18 in `domain/model/payment`), `domain/enums` (27), `domain/event` (40), `domain/exception` (17) | ~185 |
| Extension SPI | `bsm-core/domain/port` (38) + `PaymentGatewayResolver` (1) | 41 (see `PORT_REFERENCE.md`) |
| Internal | `bsm-core/application/impl` (22), `application/util` (1), `domain/service` (4); `bsm-spring-boot-starter/starter/config` (10), `starter/config/validation` (1), `starter/validation` (2) | 40 |
| Experimental | `CommercialEngineService`, `BillingDashboardService` (bsm-svc-only, not part of the library surface); `SubscriptionAddOnService`/`SubscriptionChangeService` (dual-listed, stable-but-unwired) | 2 fully-experimental + 2 dual-listed |

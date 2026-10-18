# Starter Guide

## Dependency

```kotlin
implementation("com.company:bsm-spring-boot-starter:0.0.1-SNAPSHOT")
```

## `bsm.*` property reference

All fields are optional; every value shown is the default.

| Property | Default | Purpose |
|---|---|---|
| `bsm.enabled` | `true` | Master switch — `false` disables every auto-configuration below |
| `bsm.dunning.day1-retry-after-hours` | `24` | Hours after first payment failure before retry 1 |
| `bsm.dunning.day3-retry-after-hours` | `72` | Hours before retry 2 |
| `bsm.dunning.day7-retry-after-hours` | `168` | Hours before retry 3 |
| `bsm.dunning.suspend-after-days` | `14` | Days in dunning before subscription is suspended |
| `bsm.dunning.cancel-after-days` | `30` | Days in dunning before subscription is cancelled |
| `bsm.trial.default-days` | `14` | Trial length granted by `TenantOnboardingService` when the resolved plan is the trial plan |
| `bsm.reconciliation.threshold-seconds` | `300` | Age (seconds) at which a `PENDING` payment is considered stale for reconciliation |

No "commercial module" / "dashboard module" toggles — see `API_STABILITY.md` / `ARCHITECTURE_CERTIFICATION.md` for why those services aren't in `bsm-core` at all.

## Aggregates and their ports

Each aggregate auto-configures **only if every required port is present**; if none are present,
the aggregate silently doesn't activate; if some but not all are present, startup fails (see
below).

| Aggregate | Anchor port(s) | Full required port set | Services registered |
|---|---|---|---|
| tenant-billing | `TenantBillingProfileRepositoryPort` | + `TenantScopePort` | `TenantBillingProfileService` |
| payment | `PaymentGatewayResolver` | + `PaymentMethodRepositoryPort`, `PaymentRepositoryPort`, `PlatformInvoiceRepositoryPort`, `TenantScopePort`, `EventPublisherPort`, `TenantBillingProfileService` | `PaymentMethodService`, `PaymentService` |
| invoice | `PlatformInvoiceRepositoryPort` | + `InvoiceNumberGenerator`* , `InvoiceEventPublisher`, `EventPublisherPort`, (for `InvoiceService` only) `SubscriptionRepositoryPort`, `SubscriptionAddOnRepositoryPort`, `TenantBillingProfileService`, `TenantScopePort` | `InvoiceGenerationService`, `InvoiceService` |
| subscription | `SubscriptionRepositoryPort` | + `SubscriptionHistoryRepositoryPort`, `SubscriptionEventRepositoryPort`, `SubscriptionScheduleRepositoryPort`, `TenantTrialRecordRepositoryPort`, `UsageLimitsSeedingPort`, `UserUsagePort`, `TenantScopePort`, `SubscriptionEventPublisherPort`, `PaymentMethodRepositoryPort`, `PaymentGatewayResolver`, `TenantBillingProfileService` | `SubscriptionSynchronizationService`, `SubscriptionService` |
| invoice-renewal | (derived — needs `InvoiceService` + `SubscriptionRepositoryPort` already registered) | + `PlatformInvoiceRepositoryPort`, `TenantBillingProfileService`, `SubscriptionEventPublisherPort` | `InvoiceRenewalService` |
| dunning | `DunningAttemptRepositoryPort` | + `DunningEventPublisher`, `SubscriptionRepositoryPort`, `SubscriptionHistoryRepositoryPort`, `PaymentMethodRepositoryPort`, `PaymentRepositoryPort`, `BillingLedgerRepositoryPort`, `TenantBillingProfileService`, `PaymentGatewayResolver`, `InvoiceService`, `TenantScopePort`, `SubscriptionEventPublisherPort`, `EventPublisherPort` | `DunningService` |
| tenant-onboarding | `DefaultTrialPlanPort` + `PlanVersionMetaPort` + `PpmPricingService` | + `SubscriptionRepositoryPort`, `SubscriptionService`, `InvoiceGenerationService`, `InvoiceService`, `TenantBillingProfileService` | `TenantOnboardingService` |

\* `InvoiceNumberGenerator` is auto-supplied by `BsmSupportAutoConfiguration` (a pure, stateless
default implementation) unless you register your own bean of that type.

## Overriding a default bean

Every service bean is `@ConditionalOnMissingBean` — register your own bean of the same type and
it wins, no configuration flag needed:

```java
@Bean
public TenantBillingProfileService tenantBillingProfileService(/* your own deps */) {
    return new MyCustomTenantBillingProfileService(...);
}
```

Proven by `BsmAutoConfigurationTest.customTenantBillingProfileServiceBean_takesPrecedenceOverAutoConfigured`
and the equivalent test for policy beans (`customDunningPolicyBean_takesPrecedenceOverPropertiesBinding`).

## Disabling the library

```yaml
bsm:
  enabled: false
```

No aggregate registers anything, regardless of which ports you've supplied.

## Fail-fast validation contract

`BsmPortAvailabilityValidator` runs once, after all other beans are instantiated. It does **not**
duplicate what `@ConditionalOnBean` already handles (an aggregate with zero of its ports present
is simply skipped, no error). It exists specifically for the case `@ConditionalOnBean` can't
express: an aggregate's anchor port is present (signalling you want it) but the resulting service
never got created, meaning some *other* required port is missing. Example, from actually running
the test suite (`partialTenantBillingPorts_failsFastWithActionableMessage`):

```
IllegalStateException: BSM library startup validation failed:
  - Aggregate 'tenant-billing' looks partially configured: at least one anchor port
    (TenantBillingProfileRepositoryPort) is present, but the following required port bean(s)
    are missing, so its application service could not be created: [TenantScopePort]. Either
    supply all of them, or remove all of them if you don't need this aggregate.
```

A second example (`partialSubscriptionPorts_failsFastWithActionableMessage`, supplying only the
anchor `SubscriptionRepositoryPort`):

```
IllegalStateException: BSM library startup validation failed:
  - Aggregate 'subscription' looks partially configured: at least one anchor port
    (SubscriptionRepositoryPort) is present, but the following required port bean(s)
    are missing, so its application service could not be created: [SubscriptionHistoryRepositoryPort,
    SubscriptionEventRepositoryPort, SubscriptionScheduleRepositoryPort, TenantTrialRecordRepositoryPort,
    UsageLimitsSeedingPort, UserUsagePort, SubscriptionEventPublisherPort, PaymentMethodRepositoryPort,
    PaymentGatewayResolver]. Either supply all of them, or remove all of them if you don't need this aggregate.
```

**Architecture note**: `SubscriptionRepositoryPort` is a hard dependency of both the `invoice` and
`subscription` aggregates. Supplying it only to satisfy `InvoiceService`, without the rest of the
subscription port set, correctly triggers this same failure for the `subscription` aggregate —
this is intended behavior (`bsm-spring-boot-starter/.../BsmAutoConfigurationTest`'s
`invoicePortsWithBareSubscriptionRepositoryPort_failsFast...` test), not a bug to work around.

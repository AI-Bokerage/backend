# Getting Started with Gen_BSM

Target: a working subscription in 10–15 minutes.

## 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.company:bsm-spring-boot-starter:0.0.1-SNAPSHOT")
}
```

This brings in `bsm-core` transitively. Nothing else is required to compile.

## 2. Configuration

Every `bsm.*` property has a default (see `STARTER_GUIDE.md` for the full table). You can start
with zero configuration and add overrides only when you need different values:

```yaml
bsm:
  enabled: true          # default; set false to disable the library entirely
  dunning:
    day1-retry-after-hours: 24
  trial:
    default-days: 14
```

## 3. Implement the ports you need

The starter auto-configures each aggregate (tenant-billing, payment, invoice, subscription,
invoice-renewal, dunning, tenant-onboarding) **only if you've supplied every port that aggregate
requires**. Supply nothing for an aggregate and it silently doesn't activate — no error, no bean.
Supply *some but not all* of an aggregate's required ports and startup **fails fast** with an
actionable message (see Troubleshooting below).

Minimum viable set to get a subscription working end-to-end — implement these as `@Component`
beans in your application:

```java
@Component class MySubscriptionRepository implements SubscriptionRepositoryPort { /* ... */ }
@Component class MySubscriptionHistoryRepository implements SubscriptionHistoryRepositoryPort { /* ... */ }
@Component class MySubscriptionEventRepository implements SubscriptionEventRepositoryPort { /* ... */ }
@Component class MySubscriptionScheduleRepository implements SubscriptionScheduleRepositoryPort { /* ... */ }
@Component class MyTenantTrialRecordRepository implements TenantTrialRecordRepositoryPort { /* ... */ }
@Component class MyUsageLimitsSeedingAdapter implements UsageLimitsSeedingPort { /* no-op is fine */ }
@Component class MyUserUsageAdapter implements UserUsagePort { /* stub is fine */ }
@Component class MyTenantScopeAdapter implements TenantScopePort { /* ties into your auth */ }
@Component class MySubscriptionEventPublisher implements SubscriptionEventPublisherPort { /* log or publish */ }
@Component class MyPaymentMethodRepository implements PaymentMethodRepositoryPort { /* ... */ }
@Component class MyPaymentGatewayResolver implements PaymentGatewayResolver { /* returns your gateway adapter */ }
```

See `docs/examples/` for full working code, and `bsm-demo/src/main/java/com/example/bsmdemo/adapter/`
in this repo for a complete, compiling reference implementation of every port (in-memory, not
production-grade, but structurally correct).

## 4. Implement `AuthenticatedPrincipal`

`libs/security-spi`'s `AuthenticatedPrincipal` is the one contract every port implementation that
needs caller identity depends on. Minimal implementation:

```java
public record MyAuthenticatedPrincipal(UUID userId, UUID tenantId) implements AuthenticatedPrincipal {
    @Override
    public boolean bypassesTenantScoping() {
        return false; // true only for an unconditional cross-tenant operator identity
    }
}
```

Populate `SecurityContextHolder` with this (or your own equivalent) however your app already does
authentication — `bsm-core` never touches `SecurityContextHolder` directly; only your own
`TenantScopePort` implementation does.

## 5. First subscription

```java
@Autowired TenantOnboardingService tenantOnboardingService;
@Autowired SubscriptionService subscriptionService;

tenantOnboardingService.onboard(new OnboardTenantCommand(
    tenantId, "MONTHLY", ppmPlanId, ppmPlanVersionId, "US", "SELF_SIGNUP"));

Subscription subscription = subscriptionService.getCurrentSubscription(tenantId);
```

`ppmPlanId`/`ppmPlanVersionId` may both be `null` — onboarding then resolves the platform's
default trial plan via `DefaultTrialPlanPort` instead. See `docs/examples/create-subscription.md`.

## Troubleshooting

**Startup fails with `IllegalStateException: BSM library startup validation failed`** — you've
supplied some but not all of an aggregate's required ports. The message names the aggregate and
lists exactly which port beans are missing, e.g.:

```
Aggregate 'tenant-billing' looks partially configured: at least one anchor port
(TenantBillingProfileRepositoryPort) is present, but the following required port bean(s)
are missing, so its application service could not be created: [TenantScopePort]. Either
supply all of them, or remove all of them if you don't need this aggregate.
```

Either implement the missing port(s) or remove the ones you've already added — there's no
partial-credit mode.

**`PaymentService.createPaymentIntent` throws `BusinessRuleViolationException: No provider
customer for tenant`** — you must call `PaymentMethodService.createCustomerIfRequired(...)` and
`addPaymentMethod(...)` for the tenant before creating a payment intent. This precondition lives
in a sibling service and isn't enforced by `PaymentService`'s own type signature — see
`docs/examples/payment.md`.

**A service you expected isn't registered as a bean** — check `API_STABILITY.md`'s Experimental
section; a few `bsm-core` services (subscription add-ons, plan changes) aren't auto-configured by
the starter yet. You can still wire them manually with `new XyzServiceImpl(...)`.

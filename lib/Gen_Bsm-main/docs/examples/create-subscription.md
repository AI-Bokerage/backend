# Create a subscription

Two paths: through tenant onboarding (recommended — handles trial resolution, first invoice,
idempotency), or directly via `SubscriptionService` if you're building your own onboarding flow.

## Via `TenantOnboardingService` (recommended)

```java
@Autowired TenantOnboardingService tenantOnboardingService;

// ppmPlanId/ppmPlanVersionId null -> resolves the platform's default trial plan
tenantOnboardingService.onboard(new OnboardTenantCommand(
    tenantId,
    "MONTHLY",
    null,            // ppmPlanId
    null,            // ppmPlanVersionId
    "US",            // region
    "SELF_SIGNUP"    // sourceChannel — controls whether the first invoice is auto-marked PAID
));
```

Idempotent: calling this again for a tenant that already has a subscription is a silent no-op.

## Directly via `SubscriptionService`

```java
@Autowired SubscriptionService subscriptionService;

Subscription draft = Subscription.builder()
    .id(UUID.randomUUID())
    .tenantId(tenantId)
    .billingCycle(BillingCycle.MONTHLY)
    .ppmPlanId(ppmPlanId)
    .ppmPriceId(ppmPriceId)
    .ppmResolvedPriceMinor(299900L)
    .build();

// trialDays > 0 -> TRIALING; null or <=0 -> ACTIVE immediately
Subscription created = subscriptionService.createSubscription(draft, 14, "signup", "SYSTEM");
```

`createSubscription` does **not** require a `TenantBillingProfile` to already exist — that's
only needed later, for invoicing and payment.

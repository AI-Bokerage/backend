# Cancel a subscription

Two variants exist — by tenant's *current* subscription, or by a specific subscription id:

```java
@Autowired SubscriptionService subscriptionService;

// Cancels the tenant's current subscription
Subscription cancelled = subscriptionService.cancelSubscription(
    tenantId, "customer requested cancellation", "user-123", /* cancelImmediately */ false);

// Cancels a specific subscription by id (e.g. admin action on a historical record)
Subscription cancelledById = subscriptionService.cancelSubscriptionById(
    subscriptionId, tenantId, "chargeback", "admin-456", /* cancelAtPeriodEnd */ true);
```

`cancelImmediately=false` / `cancelAtPeriodEnd=true` means the subscription stays `ACTIVE` until
the current billing period ends, then transitions to `CANCELLED` — the exact transition timing is
`bsm-core`'s responsibility, not something the caller drives with a second call.

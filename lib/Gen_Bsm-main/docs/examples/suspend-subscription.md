# Suspend / resume a subscription

`SubscriptionService` calls this "pause"/"resume", not "suspend" — use the real method names:

```java
@Autowired SubscriptionService subscriptionService;

Subscription paused = subscriptionService.pauseSubscription(
    subscriptionId, tenantId, "customer requested pause", "user-123");

// later
Subscription resumed = subscriptionService.resumeSubscription(
    subscriptionId, tenantId, "customer requested resume", "user-123");
```

Both take the subscription id, the tenant id (for tenant-scope validation), a free-text reason
(recorded in `SubscriptionHistory`), and `performedBy`.

Separately, `DunningService` can also suspend a subscription automatically as part of the dunning
policy (`SUSPENDED_PENDING_PURGE` status) — that's a different code path, not this one.

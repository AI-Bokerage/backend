# Activate a subscription

There is no separate "activate" method — `SubscriptionService` has no `activateSubscription()`.
Activation is implicit in `createSubscription`: pass `trialDays` as `null` or `<= 0` and the
subscription is created `ACTIVE` immediately; pass a positive `trialDays` and it's created
`TRIALING`, moving to `ACTIVE` when the trial period ends (via the scheduler that drives trial
expiry in the host application — `bsm-core` doesn't run its own scheduler).

```java
@Autowired SubscriptionService subscriptionService;

// Immediately active — no trial
Subscription active = subscriptionService.createSubscription(draft, null, "signup", "SYSTEM");
assert active.getStatus() == SubscriptionStatus.ACTIVE;
```

If you need to resume a previously `PAUSED` subscription, that's `resumeSubscription`, not
"activate" — see the paired suspend/resume example.

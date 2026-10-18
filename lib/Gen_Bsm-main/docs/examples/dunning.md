# Dunning

```java
@Autowired DunningService dunningService;

// Start dunning on an unpaid invoice — no-op if the subscription is PAUSED/CANCELLED/already
// in dunning
dunningService.startDunning(subscription.getId(), renewalInvoice.getId());

// Driven by a host-owned scheduler on an interval — retries due attempts per DunningPolicy
// (bsm.dunning.* properties)
dunningService.processDueAttempts();

// Manual retry, e.g. triggered from a support tool
dunningService.manualRetry(subscription.getId());

// Payment recovered through an external channel (webhook) while still in dunning
dunningService.recoveryPaymentReceived(subscription.getId(), renewalInvoice.getId());
```

`startDunning` requires a payment method to exist for the retry attempts to actually charge
anything — see `docs/examples/payment.md`. If none exists, dunning still starts (the attempt is
recorded) but each retry fails with "no default payment method available" until one is added.

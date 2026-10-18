# Plan upgrade / downgrade preview and apply

**Known gap: `SubscriptionChangeService` is not auto-configured by `bsm-spring-boot-starter`**,
same situation as `SubscriptionAddOnService` — see `add-on-purchase.md` and
`API_STABILITY.md`. Wire it manually:

```java
@Bean
public SubscriptionChangeService subscriptionChangeService(
        SubscriptionRepositoryPort subscriptionRepositoryPort,
        SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort,
        SubscriptionEventRepositoryPort subscriptionEventRepositoryPort,
        PpmChangeSnapshotRepositoryPort ppmChangeSnapshotRepositoryPort,
        TenantBillingProfileService tenantBillingProfileService,
        TenantOwnershipValidator tenantOwnershipValidator,
        PpmPricingService ppmPricingService,
        PlanVersionMetaPort planVersionMetaPort,
        PpmPromoService ppmPromoService,
        PpmProrationEngine prorationEngine,
        InvoiceGenerationService invoiceGenerationService,
        PaymentService paymentService) {
    return new SubscriptionChangeServiceImpl(/* same order as fields */);
}
```

Usage:

```java
PlanChangePreviewResult preview = subscriptionChangeService.previewChange(
    subscriptionId,
    new PreviewPlanChangeCommand(tenantId, targetPpmPlanId, "US", BillingCycle.MONTHLY, null));
// preview.prorationNetMinor(), preview.changeType() (UPGRADE/DOWNGRADE) — pure read, no writes

PlanChangeApplyResult applied = subscriptionChangeService.applyChange(
    subscriptionId,
    new ApplyPlanChangeCommand(tenantId, targetPpmPlanId, "US", BillingCycle.MONTHLY,
        "customer requested upgrade", "user-123",
        "https://your-app/success", "https://your-app/cancel", null));
```

Upgrades (`net > 0`) generate a proration invoice and return a checkout URL; downgrades take
effect at the next renewal with no immediate invoice.

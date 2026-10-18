# Purchase a subscription add-on

**Known gap: `SubscriptionAddOnService` is not auto-configured by `bsm-spring-boot-starter`.**
It exists in `bsm-core` (`application.service.SubscriptionAddOnService`,
`application.impl.SubscriptionAddOnServiceImpl`) and is fully functional, but none of the
starter's 9 `@AutoConfiguration` classes registers it — see `API_STABILITY.md`'s Experimental
section. Until that's added, wire it yourself:

```java
@Bean
public SubscriptionAddOnService subscriptionAddOnService(
        SubscriptionRepositoryPort subscriptionRepositoryPort,
        SubscriptionAddOnRepositoryPort subscriptionAddOnRepositoryPort,
        SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort,
        TenantBillingProfileService tenantBillingProfileService,
        TenantOwnershipValidator tenantOwnershipValidator,
        PpmAddOnPricingService ppmAddOnPricingService,
        InvoiceGenerationService invoiceGenerationService,
        PaymentService paymentService,
        AddOnEventPublisherPort addOnEventPublisher) {
    return new SubscriptionAddOnServiceImpl(
        subscriptionRepositoryPort, subscriptionAddOnRepositoryPort, subscriptionHistoryRepositoryPort,
        tenantBillingProfileService, tenantOwnershipValidator, ppmAddOnPricingService,
        invoiceGenerationService, paymentService, addOnEventPublisher);
}
```

Then use it:

```java
AddOnPurchaseResult result = subscriptionAddOnService.purchaseAddOn(
    subscriptionId,
    new PurchaseAddOnCommand(tenantId, ppmAddOnId, "US", BillingCycle.MONTHLY,
        "https://your-app/success", "https://your-app/cancel", "user-123"));

List<SubscriptionAddOn> addOns = subscriptionAddOnService.listAddOns(subscriptionId, tenantId);

subscriptionAddOnService.removeAddOn(subscriptionId, ppmAddOnId, tenantId, "user-123");
```

You'll also need `PpmAddOnPricingService` (a `domain.port` interface — implement it against
whatever plan/pricing catalog you use) and `AddOnEventPublisherPort`.

# Create an invoice

`InvoiceGenerationService` is the low-level primitive (enforces one invoice per
subscription/period, builds the `DRAFT → OPEN` invoice, publishes the creation event).
`InvoiceService.createInvoice` is a thin wrapper that derives period/line-items from the
subscription automatically — prefer `InvoiceGenerationService` when you already know the exact
line items (e.g. onboarding, renewal, add-on purchase).

```java
@Autowired InvoiceGenerationService invoiceGenerationService;

InvoiceLineItem subscriptionLine = InvoiceLineItem.builder()
    .id(UUID.randomUUID())
    .itemType(InvoiceLineItemType.SUBSCRIPTION)
    .description("Subscription — monthly")
    .quantity(1)
    .unitAmountMinor(299900L)
    .amountMinor(299900L)
    .createdAt(Instant.now())
    .build();

PlatformInvoice invoice = invoiceGenerationService.generateInvoice(
    tenantId,
    subscription.getId(),
    "INR",
    subscription.getCurrentPeriodStart(),
    subscription.getCurrentPeriodEnd(),
    LocalDate.now().plusDays(7),   // dueDate
    List.of(subscriptionLine),
    InvoiceSource.MANUAL);
```

Amounts are always minor units (paise/cents) — see `docs/examples/payment.md` for the
conversion helper pattern used throughout `bsm-core`.

# Renew a subscription

```java
@Autowired InvoiceRenewalService invoiceRenewalService;

// Batch: everything due right now (typically called from a host-owned scheduler)
invoiceRenewalService.generateDueRenewalInvoices();

// Single subscription — idempotent, skips if a renewal invoice already exists for the period
PlatformInvoice renewalInvoice = invoiceRenewalService.generateRenewalInvoice(subscription);
```

`bsm-core` never schedules this itself (per `ARCHITECTURE_CERTIFICATION.md`: no schedulers in the
library) — your host application decides when `generateDueRenewalInvoices()` runs, e.g. a daily
`@Scheduled` job.
